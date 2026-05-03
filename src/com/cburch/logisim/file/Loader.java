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

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import com.cburch.hdl.HdlFile;
import com.cburch.logisim.Main;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.ZipClassLoader;

public class Loader implements LibraryLoader {

  public static FileFilter makeFileFilter(StringGetter desc, String ...extensions) {
    return new FileFilter() {
      @Override
      public boolean accept(File f) {
        for (String ext : extensions)
          if (f.getName().toLowerCase().endsWith(ext.toLowerCase()))
            return true;
        return f.isDirectory();
      }

      @Override
      public String getDescription() {
        return desc.toString();
      }
    };
  }

  public static final FileFilter LOGISIM_FILTER =
      makeFileFilter(S.getter("logisimFileFilter"),
          LogisimFile.LOGISIM_EXTENSION, LogisimFile.LOGISIM_EXTENSION_ALT);

  public static final FileFilter JAR_FILTER =
      makeFileFilter(S.getter("jarFileFilter"), ".jar");
  public static final FileFilter TXT_FILTER =
      makeFileFilter(S.getter("txtFileFilter"), ".txt");
  public static final FileFilter VHDL_FILTER =
      makeFileFilter(S.getter("vhdlFileFilter"), ".vhd", ".vhdl");
  public static final FileFilter XML_FILTER =
      makeFileFilter(S.getter("xmlFileFilter"), ".xml");

  private Component parent;
  private Builtin builtin = new Builtin();
  private File mainFile = null; // to be cleared with each new file
  private Stack<File> filesOpening = new Stack<>();
  private Map<String, String> substitutions = new HashMap<>();

  public Loader(Component parent) {
    this.parent = parent;
  }

  public JFileChooser createChooser() {
    return JFileChoosers.createAt(getCurrentDirectory());
  }

  public Builtin getBuiltin() {
    return builtin;
  }

  // Used here, in LibraryManager, and in MemMenu.
  public File getCurrentDirectory() {
    File ref = filesOpening.empty() ? mainFile : filesOpening.peek();
    if (ref == null)
      return null;
    try {
      File dir = ref.getParentFile();
      if (dir != null)
        return dir.getCanonicalFile();
    } catch (Exception e) {
      // fall through
    }
    try {
      ref = ref.getCanonicalFile();
      return ref.getParentFile();
    } catch (IOException e) {
      return null;
    }
  }

  // Used by LibraryManager.
  private File getFileFor(String requestedName, FileFilter filter) throws LoadCanceledByUser {
    String name = substitutions.getOrDefault(requestedName, requestedName);
    if (name == null)
      return null;

    // Determine the actual file name.
    File file = new File(name);
    if (!file.isAbsolute()) {
      try {
        File currentDirectory = getCurrentDirectory();
        if (currentDirectory != null)
          file = new File(currentDirectory, name);
        // file = file.getAbsoluteFile();
      } catch (Exception e) {
        Errors.title("Error finding JAR library").show(
            String.format("Could not locate JAR library: %s", name), e);
        file = null;
      }
    }
    File circFile = filesOpening.empty() ? null : filesOpening.peek();
    String circName = circFile != null ? "project '"+circFile.toString()+"'" : "this project";
    // It doesn't exist. Figure it out from the user.
    if ((file == null || !file.canRead()) && Main.headless) {
      String msg = (file != null && file.exists())
          ? S.fmt("fileLibraryUnreadableError", name, circName)
          : S.fmt("fileLibraryMissingError", name, circName);
      System.out.println(msg);
      System.exit(1);
    }
    while (file == null || !file.canRead()) {
      Object[] choices = {
        S.get("fileLibraryMissingChoiceCancel"),
        S.get("fileLibraryMissingChoiceSkip"),
        S.get("fileLibraryMissingChoiceSelect") };
      String msg = file != null && file.exists()
          ? S.fmt("fileLibraryUnreadableMessage", name, circName)
          : S.fmt("fileLibraryMissingMessage", name, circName);
      int choice = -1;
      choice = JOptionPane.showOptionDialog(parent,
          "<html><body><p style='width: 400px;'>" + msg + "</p></body></html>",
          S.fmt("logisimLoadError", name, S.get("fileLibraryMissingTitleDetail")),
          JOptionPane.DEFAULT_OPTION,
          JOptionPane.ERROR_MESSAGE,
          null /* icon */,
          choices,
          choices[2]);
      if (choice == 1) {
        substitutions.put(requestedName, null); // record, so we don't ask again
        return null;
      } else if (choice == 2) { 
        JFileChooser chooser = createChooser();
        chooser.setFileFilter(filter);
        chooser.setDialogTitle(S.get("fileLibraryMissingChoiceSelect") + ": " + name);
        int action = chooser.showDialog(parent, S.get("fileLibraryMissingChoiceOk"));
        if (action == JFileChooser.APPROVE_OPTION)
          file = chooser.getSelectedFile();
      } else {
        throw new LoadCanceledByUser();
      }
    }
    return file;
  }

  public File getMainFile() {
    return mainFile;
  }
  
  public File getAutoBackupFile(Project project) {
    File bak = getAutoBackupFile(mainFile);
    if (bak == null) {
      try {
        Date date = new Date(project.windowCreationTime);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
        String when = fmt.format(date);
        File home = new File(System.getProperty("user.home"));
        File save = new File(home, ".logisim-backups");
        if (!save.exists())
          save.mkdir();
        bak = new File(save, "autosave-" + when + ".circ");
      } catch (Exception e) {
        return null;
      }
    }
    return bak;
  }

  public static void checkForAutoBackups() {
    File home = new File(System.getProperty("user.home"));
    File save = new File(home, ".logisim-backups");
    if (!save.exists() || !save.isDirectory())
      return;
    File[] backups = save.listFiles();
    int n = backups == null ? 0 : backups.length;
    if (n == 0)
      return;
    String message = S.fmt("autobackupFilesFoundMessage", n, save);
    String[] options = {
      S.get("autobackupFilesDiscardOption"),
      S.get("autobackupFilesRecoverOption"),
      S.get("autobackupFilesIgnoreOption"), };
    int result = JOptionPane.showOptionDialog(null, message,
        S.get("autobackupFilesFoundTitle"), 0,
        JOptionPane.QUESTION_MESSAGE, null, options,
        options[1]);
    if (result == 0) { // discard
      for (File bak : backups) {
        try { bak.delete(); }
        catch (Exception e) { }
      }
    } else if (result == 2) { // ignore
      return;
    } else { // recover
      JFileChooser chooser = JFileChoosers.create();
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setDialogTitle(S.get("autobackupFilesRecoverTitle"));
      int returnVal = chooser.showDialog(null,
          S.get("autobackupFilesRecoverButton"));
      if (returnVal != JFileChooser.APPROVE_OPTION)
        return; // cancel recovery, ignore instead
      File dest = chooser.getSelectedFile();
      int success = 0;
      String names = "";
      for (File bak : backups) {
        String name = bak.getName();
        if (name.startsWith("autosave-"))
          name = name.substring("autosave-".length());
        if (name.endsWith(".circ"))
          name = name.substring(0, name.length() - ".circ".length());
        name = "recovered-" + name;
        for (int i = 0; i < 20; i++) {
          File recovered = new File(dest, (i > 0 ? name+"-"+i : name) + ".circ");
          if (recovered.exists())
            continue;
          try {
            bak.renameTo(recovered);
            if (recovered.exists()) {
              success++;
              names += "\n ("+success+") " + recovered.getName();
              break;
            }
          } catch (Exception e) {
          }
        }
      }
      JOptionPane.showMessageDialog(null,
          S.fmt("autobackupFilesRecoverDoneMessage", success, n) + names,
          S.get("autobackupFilesRecoverDoneTitle"), JOptionPane.OK_OPTION);
    }
  }

  public static File getAutoBackupFile(File mainFile) {
    File f = mainFile;
    if (f == null)
      return null;
    return new File(f.getParent(), "." + f.getName() + ".autosave");
  }

  Library loadJarFile(File actual, String className)
      throws LoadFailedException {
    // Up until 2.1.8, this was written to use a URLClassLoader, which
    // worked pretty well, except that the class never releases its file
    // handles. For this reason, with 2.2.0, it's been switched to use
    // a custom-written class ZipClassLoader instead. The ZipClassLoader
    // is based on something downloaded off a forum, and I'm not as sure
    // that it works as well. It certainly does more file accesses.

    // Anyway, here's the line for this new version:
    ZipClassLoader loader = new ZipClassLoader(actual);

    // And here's the code that was present up until 2.1.8, and which I
    // know to work well except for the closing-files bit. If necessary, we
    // can revert by deleting the above declaration and reinstating the
    // below.
    /*
     * URL url; try { url = new URL("file", "localhost",
     * file.getCanonicalPath()); } catch (MalformedURLException e1) { throw
     * new LoadFailedException("Internal error: Malformed URL"); } catch
     * (IOException e1) { throw new
     * LoadFailedException(S.get("jarNotOpenedError")); }
     * URLClassLoader loader = new URLClassLoader(new URL[] { url });
     */

    // load library class from loader
    Class<?> retClass;
    try {
      retClass = loader.loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new LoadFailedException(S.fmt("jarClassNotFoundError", className), e);
    }
    if (!(Library.class.isAssignableFrom(retClass))) {
      throw new LoadFailedException(S.fmt("jarClassNotLibraryError", className));
    }

    // instantiate library
    Library ret;
    try {
      ret = (Library) retClass.getDeclaredConstructor().newInstance();
      // Backwards-compatibility: fix any null libraryClass references in
      // the library's AddTool objects.
      for (Tool t : ret.getTools()) {
        if (t instanceof AddTool) {
          AddTool tool = (AddTool)t;
          tool.fixupLibraryClass(ret.getClass());
        }
      }
    } catch (Throwable e) {
      throw new LoadFailedException(S.fmt("jarLibraryNotCreatedError", className), e);
    }
    return ret;
  }

  public Library loadJarLibraryWithSubstitutions(String fileName, String className)
      throws LoadCanceledByUser {
      File file = getFileFor(fileName, JAR_FILTER);
      return file == null ? null : loadJarLibrary(file, className);
  }

  public Library loadJarLibrary(File file, String className) {
    return LibraryManager.instance.loadJarLibraryStage2(this, file, className);
  }

  public Library loadLibrary(String desc) throws LoadCanceledByUser {
    return LibraryManager.instance.loadLibraryStage2(this, desc);
  }

  // This is used only by Loader.loadLogisimFile(), which calls
  // LibraryManager.loadLogisimFileStage2(), which calls this.
  LogisimFile.FileWithSimulations loadLogisimLibraryStage3(File request) throws LoadFailedException, LoadCanceledByUser {
    return loadLogisimFile(request);
  }

  private LogisimFile.FileWithSimulations loadLogisimFile(File actual) throws LoadFailedException, LoadCanceledByUser {
    if (filesOpening.contains(actual))
      throw new LoadFailedException(
          S.fmt("logisimCircularError", LogisimFile.toProjectName(actual)));

    LogisimFile.FileWithSimulations ret = null;
    filesOpening.push(actual);
    try {
      ret = LogisimFile.load(actual, this);
    } catch (LoadCanceledByUser e) {
      throw e;
    } catch (Throwable e) {
      throw new LoadFailedException(
            S.fmt("logisimLoadError", LogisimFile.toProjectName(actual), e.toString()), e);
    } finally {
      filesOpening.pop();
    }
    if (ret != null)
      ret.file.setName(LogisimFile.toProjectName(actual));
    return ret;
  }

  public Library loadLogisimLibraryWithSubstitutions(String fileName)
      throws LoadCanceledByUser {
    File file = getFileFor(fileName, LOGISIM_FILTER);
    return file == null ? null : loadLogisimLibrary(file);
  }

  public Library loadLogisimLibrary(File file) throws LoadCanceledByUser {
    LoadedLibrary lib = LibraryManager.instance.loadLogisimLibraryStage2(this, file);
    if (lib != null)
      showMessages((LogisimFile) lib.getBase());
    return lib;
  }

  public LogisimFile.FileWithSimulations openLogisimFile(File file) throws LoadFailedException, LoadCanceledByUser {
      LogisimFile.FileWithSimulations ret = loadLogisimFile(file);
      if (ret == null)
        throw new LoadFailedException("File could not be opened"); // fixme i18n
      setMainFile(file);
      showMessages(ret.file);
      return ret;
  }

  public LogisimFile.FileWithSimulations openLogisimFile(File file, Map<String, String> substitutions)
      throws LoadFailedException, LoadCanceledByUser {
    this.substitutions = substitutions;
    try {
      return openLogisimFile(file);
    } finally {
      this.substitutions = Collections.emptyMap();
    }
  }

  public LogisimFile.FileWithSimulations openLogisimFile(File srcFile, InputStream reader)
      throws /* LoadFailedException, */ IOException, LoadCanceledByUser {
    LogisimFile.FileWithSimulations ret = LogisimFile.load(srcFile, reader, this);
    if (srcFile != null)
      ret.file.setName(LogisimFile.toProjectName(srcFile));
    showMessages(ret.file);
    return ret;
  }

  public void reload(LoadedLibrary lib) {
    LibraryManager.instance.reload(this, lib);
  }

  public void setMainFile(File value) {
    if (!value.isAbsolute())
      value = value.getAbsoluteFile();
    mainFile = value;
  }

  public void setParent(Component value) {
    parent = value;
  }

  private void showMessages(LogisimFile source) {
    if (source == null)
      return;
    File circFile = filesOpening.empty() ? null : filesOpening.peek();
    for (String m = source.getMessage(); m != null; m = source.getMessage())
      Errors.project(circFile).warn(m);
  }

  public String vhdlImportChooser(Component window) {
    JFileChooser chooser = createChooser();
    chooser.setFileFilter(VHDL_FILTER);
    chooser.setDialogTitle(S.get("hdlOpenDialog"));
    int returnVal = chooser.showOpenDialog(window);
    if (returnVal != JFileChooser.APPROVE_OPTION)
      return null;
    File selected = chooser.getSelectedFile();
    if (selected == null)
      return null;
    try {
      String vhdl = HdlFile.load(selected);
      return vhdl;
    } catch (IOException e) {
      Errors.title(S.get("hexOpenErrorTitle")).show(e.getMessage(), e);
      return null;
    }
  }

}
