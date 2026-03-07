/**
 * This file is part of Logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Logisim-evolution.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + Haute École Spécialisée Bernoise
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 *   + REDS Institute - HEIG-VD, Yverdon-les-Bains, Switzerland
 *     http://reds.heig-vd.ch
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.file;
import static com.cburch.logisim.file.Strings.S;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.cburch.hdl.HdlModel;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.EventSourceWeakSupport;
import com.cburch.logisim.util.UniquelyNamedThread;

public class LogisimFile extends Library implements LibraryEventSource {

  private static class WritingThread extends UniquelyNamedThread {
    OutputStream out;
    LogisimFile file;
    File destFile;
    Project proj;

    WritingThread(OutputStream out, LogisimFile file, File destFile, Project proj) {
      super("WritingThread");
      this.out = out;
      this.file = file;
      this.destFile = destFile;
      this.proj = proj;
    }

    @Override
    public void run() {
      try {
        file.write(out, destFile, proj);
      } catch (IOException e) {
        Errors.project(destFile).show(S.fmt("fileDuplicateError", e.toString()), e);
      }
      try { out.close(); }
      catch (Exception e) { }
    }
  }

  public static LogisimFile createNew(Loader loader) {
    LogisimFile ret = new LogisimFile(loader);
    ret.main = new Circuit("main", ret);
    // The name will be changed in LogisimPreferences
    ret.tools.add(new AddTool(null, ret.main.getSubcircuitFactory()));
    return ret;
  }

  private static String getFirstLine(BufferedInputStream in)
      throws IOException {
    byte[] first = new byte[512];
    in.mark(first.length - 1);
    in.read(first);
    in.reset();

    int lineBreak = first.length;
    for (int i = 0; i < lineBreak; i++) {
      if (first[i] == '\n') {
        lineBreak = i;
      }
    }
    return new String(first, 0, lineBreak, "UTF-8");
  }

  public static FileWithSimulations load(File file, Loader loader) throws IOException, LoadCanceledByUser {
    InputStream in = new FileInputStream(file);
    Throwable firstExcept = null;
    try {
      return loadSub(in, loader, file);
    } catch (LoadCanceledByUser e) {
      throw e;
    } catch (Throwable t) {
      firstExcept = t;
    } finally {
      in.close();
    }

    if (firstExcept != null) {
      // We'll now try to do it using a reader. This is to work around
      // Logisim versions prior to 2.5.1, when files were not saved using
      // UTF-8 as the encoding (though the XML file reported otherwise).
      try {
        in = new ReaderInputStream(new FileReader(file), "UTF8");
        return loadSub(in, loader, file);
      } catch (Exception t) {
        Errors.project(file).show(S.fmt("xmlFormatError", firstExcept.toString()), firstExcept, t);
      } finally {
        try { in.close(); }
        catch (Exception t) { }
      }
    }

    return null;
  }

  public static FileWithSimulations load(File srcFile, InputStream in, Loader loader)
      throws IOException, LoadCanceledByUser {
    try {
      return loadSub(in, loader, null);
    } catch (SAXException e) {
      Errors.project(srcFile).show(S.fmt("xmlFormatError", e.toString()), e);
      throw new IOException("huh?", e);
    }
  }

  public static class FileWithSimulations {
    public final LogisimFile file;
    public final HashMap<Circuit, ArrayList<HashMap<String, AttributeSet>>> simulations = new HashMap<>();
    public FileWithSimulations(LogisimFile file) {
      this.file = file;
    }
  }

  private static FileWithSimulations loadSub(InputStream in, Loader loader, File srcFile)
      throws IOException, SAXException, LoadCanceledByUser {
    // fetch first line and then reset
    BufferedInputStream inBuffered = new BufferedInputStream(in);
    String firstLine = getFirstLine(inBuffered);

    if (firstLine == null) {
      throw new IOException("File is empty");
    } else if (firstLine.equals("Logisim v1.0")) {
      // if this is a 1.0 file, then set up a pipe to translate to
      // 2.0 and then interpret as a 2.0 file
      throw new IOException("Version 1.0 files no longer supported");
    }

    XmlProjectReader xmlReader = new XmlProjectReader(loader, srcFile);
    FileWithSimulations ret = xmlReader.parseProjectWithSimulations(inBuffered);
    ret.file.loader = loader;
    return ret;
  }

  private EventSourceWeakSupport<LibraryListener> listeners = new EventSourceWeakSupport<LibraryListener>();
  private Loader loader;
  private LinkedList<String> messages = new LinkedList<String>();
  private Options options = new Options();

  private LinkedList<AddTool> tools = new LinkedList<AddTool>();

  private LinkedList<Library> libraries = new LinkedList<Library>();

  private Circuit main = null;

  private String name;

  private boolean dirty = false;

  LogisimFile(Loader loader) {
    this.loader = loader;

    // Creates the default project name, adding an underscore if needed
    name = S.get("defaultProjectName");
    if (Projects.windowNamed(name)) {
      for (int i = 2; true; i++) {
        if (!Projects.windowNamed(name + "_" + i)) {
          name += "_" + i;
          break;
        }
      }
    }

  }

  public void addCircuit(Circuit circuit) {
    addCircuit(circuit, tools.size());
  }

  public void addCircuit(Circuit circuit, int index) {
    AddTool tool = new AddTool(null, circuit.getSubcircuitFactory());
    tools.add(index, tool);
    if (tools.size() == 1)
      setMainCircuit(circuit);
    fireEvent(LibraryEvent.ADD_TOOL, tool);
  }

  public void addVhdlContent(VhdlContent content) {
    addVhdlContent(content, tools.size());
  }

  public void addVhdlContent(VhdlContent content, int index) {
    AddTool tool = new AddTool(null, content.getEntityFactory());
    tools.add(index, tool);
    fireEvent(LibraryEvent.ADD_TOOL, tool);
  }

  public void addLibrary(Library lib) {
    addLibrary(lib, libraries.size());
  }

  public void addLibrary(Library lib, int index) {
    libraries.add(index, lib);
    fireEvent(LibraryEvent.ADD_LIBRARY, lib);
  }


  public void addLibraryWeakListener(Object owner, LibraryListener l) { listeners.add(owner, l); }
  public void removeLibraryWeakListener(Object owner, LibraryListener l) { listeners.remove(owner, l); }

  public void addMessage(String msg) {
    messages.addLast(msg);
  }

  @SuppressWarnings("resource")
  public LogisimFile cloneLogisimFile(File destFile, Loader newloader, Project proj) {
    PipedInputStream reader = new PipedInputStream();
    PipedOutputStream writer = new PipedOutputStream();
    try {
      reader.connect(writer);
    } catch (IOException e) {
      Errors.project(destFile).show(S.fmt("fileDuplicateError", e.toString()), e);
      return null;
    }
    new WritingThread(writer, this, destFile, proj).start();
    try {
      return LogisimFile.load(destFile, reader, newloader).file;
    } catch (IOException | LoadCanceledByUser e) {
      Errors.project(destFile).show(S.fmt("fileDuplicateError", e.toString()), e);
    } finally {
      try { reader.close(); }
      catch (IOException e1) { }
    }
    return null;
  }

  public boolean contains(Circuit circ) {
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) tool.getFactory();
        if (factory.getSubcircuit() == circ)
          return true;
      }
    }
    return false;
  }

  public boolean containsFactory(String name) {
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) tool.getFactory();
        if (factory.getContent().getName().equals(name))
          return true;
      } else if (tool.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) tool.getFactory();
        if (factory.getSubcircuit().getName().equals(name))
          return true;
      }
    }
    return false;
  }

  public boolean contains(VhdlContent content) {
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) tool.getFactory();
        if (factory.getContent() == content)
          return true;
      }
    }
    return false;
  }

  private void fireEvent(int action, Object data) {
    LibraryEvent e = new LibraryEvent(this, action, data);
    for (LibraryListener l : listeners) {
      l.libraryChanged(e);
    }
  }

  private void fireEvent(int action, Object data, Object oldData) {
    LibraryEvent e = new LibraryEvent(this, action, data, oldData);
    for (LibraryListener l : listeners) {
      l.libraryChanged(e);
    }
  }

  // fixme: only for moving circuit. Why not indexOf?
  public AddTool getAddTool(Circuit circ) {
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) tool.getFactory();
        if (factory.getSubcircuit() == circ) {
          return tool;
        }
      }
    }
    return null;
  }

  // fixme: never used?
  public AddTool getAddTool(VhdlContent content) {
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) tool.getFactory();
        if (factory.getContent() == content) {
          return tool;
        }
      }
    }
    return null;
  }

  public Circuit getCircuit(String name) {
    if (name == null)
      return null;
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) tool.getFactory();
        if (name.equals(factory.getName()))
          return factory.getSubcircuit();
      }
    }
    return null;
  }

  public VhdlContent getVhdlContent(String name) {
    if (name == null)
      return null;
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) tool.getFactory();
        if (name.equals(factory.getName()))
          return factory.getContent();
      }
    }
    return null;
  }

  public List<Circuit> getCircuits() {
    List<Circuit> ret = new ArrayList<Circuit>(tools.size());
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) tool.getFactory();
        ret.add(factory.getSubcircuit());
      }
    }
    return ret;
  }

  public int indexOfLibrary(Library lib) {
    return libraries.indexOf(lib);
  }

  public int indexOfCircuit(Circuit circ) {
    for (int i = 0; i < tools.size(); i++) {
      AddTool tool = tools.get(i);
      if (tool.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) tool.getFactory();
        if (factory.getSubcircuit() == circ) {
          return i;
        }
      }
    }
    return -1;
  }

  public AddTool findToolFor(Circuit circ) {
    return findToolFor(circ.getSubcircuitFactory());
  }

  public List<VhdlContent> getVhdlContents() {
    List<VhdlContent> ret = new ArrayList<VhdlContent>(tools.size());
    for (AddTool tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) tool.getFactory();
        ret.add(factory.getContent());
      }
    }
    return ret;
  }

  public int indexOfHdl(HdlModel hdl) {
    if (hdl instanceof VhdlContent)
      return indexOfVhdl((VhdlContent)hdl);
    return -1;
  }

  public int indexOfVhdl(VhdlContent vhdl) {
    for (int i = 0; i < tools.size(); i++) {
      AddTool tool = tools.get(i);
      if (tool.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) tool.getFactory();
        if (factory.getContent() == vhdl) {
          return i;
        }
      }
    }
    return -1;
  }

  @Override
  public List<Library> getLibraries() {
    return libraries;
  }

  public Loader getLoader() {
    return loader;
  }

  public Circuit getMainCircuit() {
    return main;
  }

  public String getMessage() {
    if (messages.size() == 0)
      return null;
    return messages.removeFirst();
  }

  //
  // access methods
  //
  @Override
  public String getName() {
    return name;
  }

  public Options getOptions() {
    return options;
  }

  @Override
  public List<AddTool> getTools() {
    return tools;
  }

  public String getUnloadLibraryMessage(Library lib) {
    List<? extends Tool> myTools = getToolsAndDirectLibraryTools();
    List<? extends Tool> libTools = lib.getToolsAndSublibraryTools();
    libTools.removeAll(myTools);

    HashSet<ComponentFactory> factories = new HashSet<>();
    for (Tool tool : libTools)
      if (tool instanceof AddTool)
        factories.add(((AddTool)tool).getFactory());

    for (Circuit circuit : getCircuits())
      for (Component comp : circuit.getNonWires())
        if (factories.contains(comp.getFactory()))
          return S.fmt("unloadUsedError", circuit.getName());

    ToolbarData tb = options.getToolbarData();
    MouseMappings mm = options.getMouseMappings();
    for (Tool t : libTools) {
      if (tb.usesToolFromSource(t))
        return S.get("unloadToolbarError");
      if (mm.usesToolFromSource(t))
        return S.get("unloadMappingError");
    }

    return null;
  }

  @Override
  public boolean isDirty() {
    return dirty;
  }

  public void moveLibrary(Library lib, int index) {
    int oldIndex = libraries.indexOf(lib);
    if (oldIndex < 0) {
      libraries.add(index, lib);
      fireEvent(LibraryEvent.ADD_LIBRARY, lib);
    } else {
      Library value = libraries.remove(oldIndex);
      libraries.add(index, value);
      fireEvent(LibraryEvent.MOVE_LIBRARY, lib);
    }
  }

  public void moveTool(AddTool tool, int index) {
    int oldIndex = tools.indexOf(tool);
    if (oldIndex < 0) {
      tools.add(index, tool);
      fireEvent(LibraryEvent.ADD_TOOL, tool);
    } else {
      AddTool value = tools.remove(oldIndex);
      tools.add(index, value);
      fireEvent(LibraryEvent.MOVE_TOOL, tool);
    }
  }

  public int removeCircuit(Circuit circuit) {
    if (getCircuits().size() <= 1)
      throw new RuntimeException("Cannot remove last circuit");
    int index = indexOfCircuit(circuit);
    if (index < 0)
      return -1;
    if (main == circuit) {
      Circuit newMain = null;
      for (AddTool tool : tools) {
        if (tool.getFactory() instanceof SubcircuitFactory) {
          SubcircuitFactory factory = (SubcircuitFactory)tool.getFactory();
          newMain = factory.getSubcircuit();
          break;
        }
      }
      if (newMain == null)
        throw new IllegalStateException("Can't find new main circuit");
      setMainCircuit(newMain);
    }

    Tool circuitTool = tools.remove(index);
    fireEvent(LibraryEvent.REMOVE_TOOL, circuitTool);
    return index;
  }

  public void removeVhdl(VhdlContent vhdl) {
    int index = indexOfVhdl(vhdl);
    if (index >= 0) {
      Tool vhdlTool = tools.remove(index);
      fireEvent(LibraryEvent.REMOVE_TOOL, vhdlTool);
    }
  }

  public int removeLibrary(Library lib) {
    int index = indexOfLibrary(lib);
    if (index >= 0) {
      libraries.remove(lib);
      fireEvent(LibraryEvent.REMOVE_LIBRARY, lib);
    }
    return index;
  }

  public void setDirty(boolean value) {
    if (dirty != value) {
      dirty = value;
      fireEvent(LibraryEvent.DIRTY_STATE, value ? Boolean.TRUE
          : Boolean.FALSE);
      if (dirty)
        Projects.projectDirtied();
      else
        Projects.projectCleaned();
    }
  }

  public void setMainCircuit(Circuit circuit) {
    if (circuit == null)
      return;
    Circuit oldMain = this.main;
    this.main = circuit;
    fireEvent(LibraryEvent.SET_MAIN, circuit, oldMain);
  }

  public void setName(String name) {
    this.name = name;
    fireEvent(LibraryEvent.SET_NAME, name);
  }

  // void write(OutputStream out) throws IOException {
  //   write(out, null);
  // }

  void write(OutputStream out, File dest, Project proj) throws IOException {
    try {
      XmlWriter.write(this, proj, out, dest);
    } catch (TransformerConfigurationException e) {
      Errors.project(dest).show("internal error configuring transformer", e);
    } catch (ParserConfigurationException e) {
      Errors.project(dest).show("internal error configuring parser", e);
    } catch (TransformerException e) {
      String msg = e.getMessage();
      String err = S.get("xmlConversionError");
      if (msg != null)
        err += ": " + msg;
      Errors.project(dest).show(err, e);
    }
  }

  // same as save(f, proj), but with aggressive deletion, no warnings, etc.
  public boolean autoBackup(File dest, Project proj) {
    Library reference = LibraryManager.instance.findReference(this, dest);
    if (reference != null)
      return false;

    FileOutputStream fwrite = null;
    try {
      fwrite = new FileOutputStream(dest);
      write(fwrite, dest, proj);
    } catch (IOException e) {
      if (dest.exists() && dest.length() == 0)
        dest.delete();
      return false;
    } finally {
      if (fwrite != null) {
        try {
          fwrite.close();
        } catch (IOException e) {
          if (dest.exists() && dest.length() == 0)
            dest.delete();
          return false;
        }
      }
    }

    if (dest.exists() && dest.length() == 0) {
      dest.delete();
      return false;
    }

    return true;
  }

  public boolean save(File dest, Project proj) {
    Library reference = LibraryManager.instance.findReference(this, dest);
    if (reference != null) {
      Errors.title(S.get("fileSaveErrorTitle")).show(
          S.fmt("fileCircularError", reference.getDisplayName()));
      return false;
    }

    File backup = determineBackupName(dest);
    boolean backupCreated = backup != null && dest.renameTo(backup);

    FileOutputStream fwrite = null;
    try {
      // Java 8+ does not appear to support this feature. Is it even needed?
      // if (Main.MacOS) {
      //   try {
      //     MRJAdapter.setFileCreatorAndType(dest, "LGSM", "circ");
      //   } catch (IOException e) {
      //   }
      // }
      fwrite = new FileOutputStream(dest);
      write(fwrite, dest, proj);
      setName(toProjectName(dest));

      File oldFile = loader.getMainFile();
      loader.setMainFile(dest);
      LibraryManager.instance.fileSaved(loader, dest, oldFile, this, proj);
    } catch (IOException e) {
      if (backupCreated)
        recoverBackup(backup, dest);
      if (dest.exists() && dest.length() == 0)
        dest.delete();
      Errors.title(S.get("fileSaveErrorTitle")).show(
          S.fmt("fileSaveError", e.toString()));
      return false;
    } finally {
      if (fwrite != null) {
        try {
          fwrite.close();
        } catch (IOException e) {
          if (backupCreated)
            recoverBackup(backup, dest);
          if (dest.exists() && dest.length() == 0)
            dest.delete();
          Errors.title(S.get("fileSaveErrorTitle")).show(
              S.fmt("fileSaveCloseError", e.toString()));
          return false;
        }
      }
    }

    if (!dest.exists() || dest.length() == 0) {
      if (backupCreated && backup != null && backup.exists())
        recoverBackup(backup, dest);
      else
        dest.delete();
      Errors.title(S.get("fileSaveErrorTitle")).show(
          S.get("fileSaveZeroError"));
      return false;
    }

    if (backupCreated && backup.exists()) {
      backup.delete();
    }
    return true;
  }

  private static void recoverBackup(File backup, File dest) {
    if (backup != null && backup.exists()) {
      if (dest.exists())
        dest.delete();
      backup.renameTo(dest);
    }
  }

  private static File determineBackupName(File base) {
    File dir = base.getParentFile();
    String name = base.getName();
    if (name.endsWith(LOGISIM_EXTENSION)) {
      name = name.substring(0, name.length() - LOGISIM_EXTENSION.length());
    } else if (name.endsWith(LOGISIM_EXTENSION_ALT)) {
      name = name.substring(0, name.length() - LOGISIM_EXTENSION_ALT.length());
    }
    for (int i = 1; i <= 20; i++) {
      String ext = i == 1 ? ".bak" : (".bak" + i);
      File candidate = new File(dir, name + ext);
      if (!candidate.exists())
        return candidate;
    }
    return null;
  }

  public static String toProjectName(File file) {
    String ret = file.getName();
    if (ret.endsWith(LOGISIM_EXTENSION)) {
      return ret.substring(0, ret.length() - LOGISIM_EXTENSION.length());
    } else if (ret.endsWith(LOGISIM_EXTENSION_ALT)) {
      return ret.substring(0, ret.length() - LOGISIM_EXTENSION_ALT.length());
    } else {
      return ret;
    }
  }

  public static final String LOGISIM_EXTENSION = ".circ";
  public static final String LOGISIM_EXTENSION_ALT = ".circ.xml";

}
