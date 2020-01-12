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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.JOptionPane;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.InputEventUtil;

public class XmlProjectReader extends XmlReader {

  class ReadProjectContext extends ReadContext {

    HashMap<String, Library> libs = new HashMap<>();
    HashMap<Circuit, ArrayList<HashMap<String, AttributeSet>>> simulations = new HashMap<>();

    ReadProjectContext(LogisimFile f, String path) { super(f, path); }

    Library findLibrary(String libName) throws XmlReaderException { // may be null
      if (libName == null || libName.equals(""))
        return file;
      if (!libs.containsKey(libName))
        throw new XmlReaderException(S.fmt("libMissingError", libName));
      return libs.get(libName); // may be null
    }

    private void initMouseMappings(Element elt) {
      MouseMappings map = file.getOptions().getMouseMappings();
      for (Element sub_elt : XmlIterator.forChildElements(elt, "tool")) {
        Tool tool;
        try {
          tool = toTool(sub_elt);
          if (tool == null)
            continue; // skip mappings for tools from skipped libs
        } catch (XmlReaderException e) {
          addErrors(e, "mapping");
          continue;
        }

        String mods_str = sub_elt.getAttribute("map");
        if (mods_str == null || mods_str.equals("")) {
          addError(S.get("mappingMissingError"), "mouse mapping");
          continue;
        }
        int mods;
        try {
          mods = InputEventUtil.fromString(mods_str);
        } catch (NumberFormatException e) {
          addError(S.fmt("mappingBadError", mods_str), "mouse mapping");
          continue;
        }

        tool = tool.cloneTool();
        try {
          initAttributeSet(sub_elt, tool.getAttributeSet(), tool);
        } catch (XmlReaderException e) {
          addErrors(e, "mapping." + tool.getName());
        }

        map.setToolFor(mods, tool);
      }
    }

    private void initToolbarData(Element elt) {
      ToolbarData toolbar = file.getOptions().getToolbarData();
      for (Element sub_elt : XmlIterator.forChildElements(elt)) {
        if (sub_elt.getTagName().equals("sep")) {
          toolbar.addSeparator();
        } else if (sub_elt.getTagName().equals("tool")) {
          Tool tool;
          try {
            tool = toTool(sub_elt);
            if (tool == null)
              continue; // skip toolbar items for tools from skipped libs
          } catch (XmlReaderException e) {
            addErrors(e, "toolbar");
            continue;
          }
          if (tool != null) {
            tool = tool.cloneTool();
            try {
              initAttributeSet(sub_elt, tool.getAttributeSet(), tool);
            } catch (XmlReaderException e) {
              addErrors(e, "toolbar." + tool.getName());
            }
            toolbar.addTool(tool);
          }
        }
      }
    }

    private Library toLibrary(Element elt) throws LoadCanceledByUser {
      Library lib = parseLibrary(loader, elt);
      libs.put(elt.getAttribute("name"), lib); // will be null if skipping lib
      return lib;
    }

    private void parseProject(Element elt) throws LoadCanceledByUser {
      // determine the version producing this file
      String versionString = elt.getAttribute("source");
      if (versionString.equals("")) {
        sourceVersion = Main.VERSION;
      } else {
        sourceVersion = LogisimVersion.parse(versionString);
      }

      // If we are opening a pre-logisim-evolution file, there might be some
      // components (such as the RAM or the counters), that have changed their
      // shape and other details. We have therefore to warn the user that things
      // might be a little strange in their circuits...
      if (sourceVersion.compareTo(LogisimVersion.get(2, 7, 2)) < 0) {
        String msg = 
            "You are opening a file created with original Logisim code.\n"
            + "You might encounter some problems in the execution, since many components\n"
            + "have evolved since then. Some components and labels will be adjusted.";
        if (Main.headless)
          System.err.println("WARNING:\n" + msg);
        else
          JOptionPane.showMessageDialog(null, msg, "Warning: Legacy Circuit", JOptionPane.WARNING_MESSAGE);
      }

      // first, load the sublibraries
      for (Element o : XmlIterator.forChildElements(elt, "lib")) {
        Library lib = toLibrary(o);
        if (lib != null)
          file.addLibrary(lib);
      }

      // second, create the circuits - empty for now - and the vhdl entities
      List<CircuitData> circuitsData = new ArrayList<>();
      for (Element subElt : XmlIterator.forChildElements(elt)) {
        switch (subElt.getTagName()) {
        case "vhdl":
          VhdlContent contents = parseVhdl(subElt);
          if (contents != null) {
            file.addVhdlContent(contents);
          }
          break;
        case "circuit":
          CircuitData circData = parseCircuit(subElt);
          if (circData != null) {
            file.addCircuit(circData.circuit);
            circuitsData.add(circData);
          }
        default:
          // do nothing
        }
      }

      // third, process the other child elements
      for (Element sub_elt : XmlIterator.forChildElements(elt)) {
        String name = sub_elt.getTagName();

        switch (name) {
        case "circuit":
        case "vhdl":
        case "lib":
          // Nothing to do: Done earlier.
          break;
        case "options":
          try {
            initAttributeSet(sub_elt, file.getOptions().getAttributeSet(), null);
          } catch (XmlReaderException e) {
            addErrors(e, "options");
          }
          break;
        case "mappings":
          initMouseMappings(sub_elt);
          break;
        case "toolbar":
          initToolbarData(sub_elt);
          break;
        case "main":
          String main = sub_elt.getAttribute("name");
          Circuit circ = file.getCircuit(main);
          if (circ != null) {
            file.setMainCircuit(circ);
          }
          break;
        case "message":
          file.addMessage(sub_elt.getAttribute("value"));
          break;
        default:
          throw new IllegalArgumentException(
              "Invalid node in logisim file: " + name);
        }
      }

      // fourth, execute a transaction that initializes all the circuits
      XmlCircuitReader builder = new XmlCircuitReader(this, circuitsData);
      builder.execute();

      // fifth, load any saved simulations
      for (CircuitData cd : circuitsData) {
        ArrayList<HashMap<String, AttributeSet>> simData = new ArrayList<>();
        for (HashMap<String, Element> sim : cd.simulations) {
          HashMap<String, AttributeSet> simState = new HashMap<>();
          sim.forEach((path, simElt) -> {
            ArrayList<Component> cpath = findComponent(cd.circuit, path);
            if (cpath == null) {
              addError(S.fmt("simulationPathInvalidError", path), "non-volatile simulation state");
              return; // continue forEach
            }
            Component comp = cpath.get(cpath.size()-1);
            AttributeSet attrs = comp.getFactory().getNonVolatileSimulationState(comp, null);
            if (attrs == null) {
              addError(S.fmt("simulationPathVolatileError", path), "non-volatile simulation state");
              return; // continue forEach
            }
            try {
              initAttributeSet(simElt, attrs, null);
              for (com.cburch.logisim.data.Attribute a : attrs.getAttributes())
              simState.put(path, attrs);
            } catch (XmlReaderException ex) {
              addErrors(ex, "non-volatile simulation state");
            }
          });
          if (simState.size() > 0)
            simData.add(simState);
        }
        if (simData.size() > 0)
          simulations.put(cd.circuit, simData);
      }

      // last, configure AddTool attributes for circuits within the project
      for (CircuitData cd : circuitsData) {
        AddTool tool = file.findToolFor(cd.circuit);
        if (tool == null)
          continue;
        try {
          initAttributeSet(cd.circuitElement, tool.getAttributeSet(), tool);
        } catch (XmlReaderException e) {
            addErrors(e, "circuit tool attributes");
        }
      }
    }

    Tool toTool(Element elt) throws XmlReaderException { // may be null
      Library lib = findLibrary(elt.getAttribute("lib"));
      if (lib == null)
        return null; // skip tools from skipped libs
      String name = elt.getAttribute("name");
      if (name == null || name.equals(""))
        throw new XmlReaderException(S.get("toolNameMissing"));
      Tool tool = lib.getTool(name);
      if (tool == null)
        throw new XmlReaderException(S.fmt("xmlToolNotFound", name));
      return tool;
    }
  }


  LogisimFile.FileWithSimulations parseProjectWithSimulations(InputStream is)
      throws IOException, SAXException, LoadCanceledByUser {
    Document doc = loadXmlFrom(is);
    Element elt = doc.getDocumentElement();

    considerRepairs(doc, elt);
    LogisimFile file = new LogisimFile(loader);
    ReadProjectContext context = new ReadProjectContext(file,
        srcFile == null ? null : srcFile.getAbsolutePath());

    context.parseProject(elt);

    if (file.getCircuits().size() == 0)
      file.addCircuit(new Circuit("main", file));
    
    if (context.messages.size() > 0) {
      StringBuilder all = new StringBuilder();
      all.append("Error were encountered loading the project:\n");
      for (String msg : context.messages) {
        all.append(msg);
        all.append("\n");
      }
      Errors.title("XML Error").show(all.substring(0, all.length() - 1));
    }
    LogisimFile.FileWithSimulations ret = new LogisimFile.FileWithSimulations(file);
    ret.simulations.putAll(context.simulations);
    return ret;
  }

  private Loader loader;
  private File srcFile; // used for de-relativizing paths in xml

  XmlProjectReader(Loader loader, File srcFile) {
    this.loader = loader;
    this.srcFile = srcFile;
  }

  // --------------------------------------------------------------------------------
  // Everything below this point is for repairing and validating old and potentially
  // misformed xml data, e.g. from old or broken versions of Logisim.
  // --------------------------------------------------------------------------------

  private static void findLibraryUses(ArrayList<Element> dest, String label,
      Iterable<Element> candidates) {
    for (Element elt : candidates) {
      String lib = elt.getAttribute("lib");
      if (lib.equals(label)) {
        dest.add(elt);
      }
    }
  }

  private void addToLabelMap(HashMap<String, String> labelMap,
      String srcLabel, String dstLabel, String toolNames) {
    if (srcLabel != null && dstLabel != null) {
      for (String tool : toolNames.split(";")) {
        labelMap.put(srcLabel + ":" + tool, dstLabel);
      }
    }
  }

  private void considerRepairs(Document doc, Element root) {
    LogisimVersion version = LogisimVersion.parse(root.getAttribute("source"));

    if (version.compareTo(LogisimVersion.get(2, 3, 0)) < 0) {
      // This file was saved before an Edit tool existed. Most likely
      // we should replace the Select and Wiring tools in the toolbar
      // with the Edit tool instead.
      for (Element toolbar : XmlIterator.forChildElements(root, "toolbar")) {
        Element wiring = null;
        Element select = null;
        Element edit = null;
        for (Element elt : XmlIterator.forChildElements(toolbar, "tool")) {
          String eltName = elt.getAttribute("name");
          if (eltName != null && !eltName.equals("")) {
            if (eltName.equals("Select Tool"))
              select = elt;
            if (eltName.equals("Wiring Tool"))
              wiring = elt;
            if (eltName.equals("Edit Tool"))
              edit = elt;
          }
        }
        if (select != null && wiring != null && edit == null) {
          select.setAttribute("name", "Edit Tool");
          toolbar.removeChild(wiring);
        }
      }
    }

    if (version.compareTo(LogisimVersion.get(2, 6, 3)) < 0) {
      for (Element circElt : XmlIterator.forChildElements(root, "circuit")) {
        for (Element attrElt : XmlIterator.forChildElements(circElt, "a")) {
          String name = attrElt.getAttribute("name");
          if (name != null && name.startsWith("label")) {
            attrElt.setAttribute("name", "c" + name);
          }
        }
      }

      repairForWiringLibrary(doc, root);
      repairByEradicatingLibrary(doc, root, "Legacy", "#Legacy");
    }

    if (version.compareTo(LogisimVersion.get(2, 7, 2)) < 0) {
      addBuiltinLibrariesIfMissing(doc, root);
      // pre logisim-evolution, we didn't have "Appearance" labels
      // on many components. Add StdAttr.APPEAR_CLASSIC on each subcircuit
      // and instances of FlipFlops, Registers, Counters, RAM, ROM, and
      // Shift Registers.
      String memLibName = findLibNameByDesc(root, "#Memory");
      for (Element circElt : XmlIterator.forChildElements(root, "circuit")) {
        setDefaultAttribute(doc, circElt, "appearance", "classic");
        if (memLibName != null) {
          for (Element compElt : XmlIterator.forChildElements(circElt, "comp")) {
            String lib = compElt.getAttribute("lib");
            String name = compElt.getAttribute("name");
            if (lib == null || name == null || !lib.equals(memLibName))
              continue;
            if (name.equals("J-K Flip-Flop") || name.equals("S-R Flip-Flop")
                || name.equals("T Flip-Flop") || name.equals("D Flip-Flop")
                || name.equals("RAM") || name.equals("ROM")
                || name.equals("Register") || name.equals("Shift Register")
                || name.equals("Counter") || name.equals("Random")) {
              setDefaultAttribute(doc, compElt, "appearance", "classic");
            }
            if (name.equals("J-K Flip-Flop") || name.equals("S-R Flip-Flop")
                || name.equals("T Flip-Flop") || name.equals("D Flip-Flop")) {
              setDefaultAttribute(doc, compElt, "enable", "true");
            }
          }
        }
      }
    }

    // As of version 4.0.0-HC, the #TCL library is gone.
    repairByEradicatingLibrary(doc, root, "TCL", "#TCL");

    // Before version 4.0.0-HC, Pin components had attributes:
    //   output=true|false
    //   tristate=true|false
    //   pull=up|down (or missing)
    // These are now consolodated into two attributes:
    //   type=input|output
    //   behavior=simple|tristate|pullup|pulldown
    String wiringLibName = findLibNameByDesc(root, "#Wiring");
    for (Element compElt : XmlIterator.forDescendantElements(root, "comp"))
      convertObsoletePinAttributes(doc, compElt, wiringLibName);
    for (Element toolElt : XmlIterator.forDescendantElements(root, "tool"))
      convertObsoletePinAttributes(doc, toolElt, wiringLibName);

    // As of version 4.0.2, quad-input wide primitive gates have reasonable layout.
    if (version.compareTo(LogisimVersion.get(4, 0, 2)) < 0) {
      // Version 4.0.2 fixed the spacing of input pins for certain primitive
      // gates like AND and OR when they were configured with size=Wide and
      // inputs=4. Warn about this backwards-incompatible change.
      String gatesLib = findLibNameByDesc(root, "#Gates");
      if (gatesLib != null) {
        wideQuadCheck:
        for (Element circElt : XmlIterator.forChildElements(root, "circuit")) {
          for (Element compElt : XmlIterator.forChildElements(circElt, "comp")) {
            String lib = compElt.getAttribute("lib");
            String name = compElt.getAttribute("name");
            if (lib == null || name == null || !lib.equals(gatesLib))
              continue;
            if (!(name.equals("AND Gate") || name.equals("OR Gate")
                  || name.equals("NAND Gate") || name.equals("NOR Gate")
                  || name.equals("XOR Gate") || name.equals("XNOR Gate")
                  || name.equals("Odd Parity") || name.equals("Even Parity")))
              continue;
            boolean wide = false, quad = false;
            for (Element attrElt : XmlIterator.forChildElements(compElt, "a")) {
              String aname = attrElt.getAttribute("name");
              String aval = attrElt.getAttribute("val");
              if (aname == null || aval == null)
                continue;
              if (aname.equals("inputs")) {
                if (aval.trim().equals("4"))
                  quad = true;
                else
                  break;
              }
              else if (aname.equals("size"))
                if (aval.trim().equals("70"))
                  wide = true;
                else
                  break;
            }
            if (wide && quad) {
              String msg =
                    "Warning: The shapes of certain quad-input primitive gates used in\n"
                  + "this file have changed in recent versions of Logisim-Evolution.\n"
                  + "This include quad-input AND, OR, NAND, NOR, XOR, XNOR, Even-Parity,\n"
                  + "and Odd-Parity gates that are configured with 'Wide' appearance.\n"
                  + "Please reconnect input wires as needed for these gates.";
              if (Main.headless)
                System.err.println("WARNING:\n" + msg);
              else
                JOptionPane.showMessageDialog(null, msg, "Warning: Legacy Circuit", JOptionPane.WARNING_MESSAGE);
              break wideQuadCheck;
            }
          }
        }
      }
    }

    if (version.compareTo(LogisimVersion.get(4, 0, 2)) < 0) {
      // This file was saved before the Wire Cutter tool existed. Let's
      // add it to the toolbar next to wiring (or edit (or select)).
      for (Element toolbar : XmlIterator.forChildElements(root, "toolbar")) {
        Element wiring = null;
        Element select = null;
        Element edit = null;
        Element cutter = null;
        for (Element elt : XmlIterator.forChildElements(toolbar, "tool")) {
          String eltName = elt.getAttribute("name");
          if (eltName != null && !eltName.equals("")) {
            if (eltName.equals("Select Tool"))
              select = elt;
            if (eltName.equals("Wiring Tool"))
              wiring = elt;
            if (eltName.equals("Edit Tool"))
              edit = elt;
            if (eltName.equals("Cutter Tool"))
              edit = elt;
          }
        }
        if (cutter == null) {
          String baselib = findLibNameByDesc(root, "#Base");
          if (baselib != null) {
            cutter  = doc.createElement("tool");
            cutter.setAttribute("lib", baselib);
            cutter.setAttribute("name", "Cutter Tool");
            if (wiring != null)
              toolbar.insertBefore(cutter, wiring.getNextSibling());
            else if (edit != null)
              toolbar.insertBefore(cutter, edit.getNextSibling());
            else if (select != null)
              toolbar.insertBefore(cutter, select.getNextSibling());
            else
              toolbar.insertBefore(cutter, toolbar.getFirstChild());
          }
        }
      }
    }
  }

  private void convertObsoletePinAttributes(Document doc, Element elt, String wiringLibName) {
    String lib = elt.getAttribute("lib");
    String name = elt.getAttribute("name");
    if (name == null || lib == null || !name.equals("Pin") || !lib.equals(wiringLibName))
      return;
    String output = null, tristate = null, pull = null, type = null, behavior = null;
    ArrayList<Element> bad = new ArrayList<>();
    for (Element attrElt : XmlIterator.forChildElements(elt, "a")) {
      String aname = attrElt.getAttribute("name");
      String aval = attrElt.getAttribute("val");
      if ("output".equalsIgnoreCase(aname)) {
        output = aval;
        bad.add(attrElt);
      } else if ("tristate".equalsIgnoreCase(aname)) {
        tristate = aval;
        bad.add(attrElt);
      } else if ("pull".equalsIgnoreCase(aname)) {
        pull = aval;
        bad.add(attrElt);
      } else if ("type".equalsIgnoreCase(aname)) {
        type = aval;
      } else if ("behavior".equalsIgnoreCase(aname)) {
        behavior = aval;
      }
    }
    for(Element b : bad)
      elt.removeChild(b);
    if (type == null && output != null)
      appendChildAttribute(doc, elt, "type", output.equalsIgnoreCase("true") ? "output" : "inputk");
    if (behavior == null) {
      if ("up".equalsIgnoreCase(pull))
        appendChildAttribute(doc, elt, "behavior", "pullup");
      else if ("down".equalsIgnoreCase(pull))
        appendChildAttribute(doc, elt, "behavior", "pulldown");
      else if ("true".equalsIgnoreCase(tristate))
        appendChildAttribute(doc, elt, "behavior", "tristate");
    }
  }

  private static void appendChildAttribute(Document doc, Element elt, String name, String val) {
    Element a = doc.createElement("a");
    a.setAttribute("name", name);
    a.setAttribute("val", val);
    elt.appendChild(a);
  }

  private void addBuiltinLibrariesIfMissing(Document doc, Element root) {
    HashSet<String> found = new HashSet<>();
    Node end = root.getFirstChild();
    int maxLib = 0;
    for (Element libElt : XmlIterator.forChildElements(root, "lib")) {
      String desc = libElt.getAttribute("desc");
      String name = libElt.getAttribute("name");
      if (desc != null)
        found.add(desc);
      if (name != null) {
        int thisLabel = Integer.parseInt(name);
        if (thisLabel > maxLib)
          maxLib = thisLabel;
      }
      end = libElt.getNextSibling();
    }
    for (Library lib : loader.getBuiltin().getLibraries()) {
      String desc = LibraryManager.instance.getShortDescriptor(lib);
      if (found.contains(desc))
        continue;
      Element libElt = doc.createElement("lib");
      libElt.setAttribute("name", "" + (maxLib + 1));
      libElt.setAttribute("desc", desc);
      for (Tool t : lib.getTools()) {
        AttributeSet attrs = t.getAttributeSet();
        if (attrs != null) {
          Element toAdd = doc.createElement("tool");
          toAdd.setAttribute("name", t.getName());
          XmlAttributesUtil.addAttributeSetContent(doc, null, toAdd, attrs, t);
          if (toAdd.getChildNodes().getLength() > 0) {
            libElt.appendChild(toAdd);
          }
        }
      }
      root.insertBefore(libElt, end);
      found.add(desc);
      maxLib++;
    }
  }

  private void setDefaultAttribute(Document doc, Element elt, String attrib, String val) {
    Node end = elt.getFirstChild();
    for (Element attrElt : XmlIterator.forChildElements(elt, "a")) {
      String name = attrElt.getAttribute("name");
      if (name != null && name.equals(attrib)) {
        return;
      }
      end = attrElt.getNextSibling();
    }
    Element a = doc.createElement("a");
    a.setAttribute("name", attrib);
    a.setAttribute("val", val);
    elt.insertBefore(a, end);
  }

  private void relocateTools(Element src, Element dest,
      HashMap<String, String> labelMap) {
    if (src == null || src == dest)
      return;
    String srcLabel = src.getAttribute("name");
    if (srcLabel == null)
      return;

    ArrayList<Element> toRemove = new ArrayList<>();
    for (Element elt : XmlIterator.forChildElements(src, "tool")) {
      String name = elt.getAttribute("name");
      if (name != null && labelMap.containsKey(srcLabel + ":" + name)) {
        toRemove.add(elt);
      }
    }
    for (Element elt : toRemove) {
      src.removeChild(elt);
      if (dest != null) {
        dest.appendChild(elt);
      }
    }
  }

  private void repairByEradicatingLibrary(Document doc, Element root,
      String libDisplayName, String libTag) {
    Element lib = null;
    String libId = null;
    for (Element libElt : XmlIterator.forChildElements(root, "lib")) {
      String desc = libElt.getAttribute("desc");
      String id = libElt.getAttribute("name");
      if (desc != null && desc.equals(libTag)) {
        lib = libElt;
        libId = id;
        break;
      }
    }
    if (lib == null)
      return;

    root.removeChild(lib);

    ArrayList<Element> toRemove = new ArrayList<>();
    findLibraryUses(toRemove, libId,
        XmlIterator.forDescendantElements(root, "comp"));
    boolean componentsRemoved = !toRemove.isEmpty();
    findLibraryUses(toRemove, libId,
        XmlIterator.forDescendantElements(root, "tool"));

    for (Element elt : toRemove)
      elt.getParentNode().removeChild(elt);

    if (componentsRemoved) {
      String error = S.fmt("libNoLongerSupported", libDisplayName);
      Element elt = doc.createElement("message");
      elt.setAttribute("value", error);
      root.appendChild(elt);
    }
  }

  // Before version 2.6.3, #Base contained a lot of stuff, including a mix of
  // component-like things (Pin, splitters, transistors, constants, etc.), and
  // the standard tools (edit, poke, wiring-tool text, menu-tool, etc.). At
  // version 2.6.3, this was split into two libraries, one called #Wiring for
  // all the component-like things, and another called #Base with the standard
  // tools. After 3.1.1-HC (maybe 4.0.0-HC?), #Base is hidden, except in the
  // toolbar and the toolbar options explorer. It does not appear in the project
  // explorer at in the left panel. The file format remains the same.
  private void repairForWiringLibrary(Document doc, Element root) {
    Element oldBaseElt = null;
    String oldBaseLabel = null;
    Element gatesElt = null;
    String gatesLabel = null;
    int maxLabel = -1;
    Element firstLibElt = null;
    Element lastLibElt = null;
    for (Element libElt : XmlIterator.forChildElements(root, "lib")) {
      String desc = libElt.getAttribute("desc");
      String label = libElt.getAttribute("name");
      if (desc == null) {
        // skip these tests
      } else if (desc.equals("#Base")) {
        oldBaseElt = libElt;
        oldBaseLabel = label;
      } else if (desc.equals("#Wiring")) {
        // Wiring library already in file. This shouldn't happen, but if
        // somehow it does, we don't want to add it again.
        return;
      } else if (desc.equals("#Gates")) {
        gatesElt = libElt;
        gatesLabel = label;
      }

      if (firstLibElt == null)
        firstLibElt = libElt;
      lastLibElt = libElt;
      try {
        if (label != null) {
          int thisLabel = Integer.parseInt(label);
          if (thisLabel > maxLabel)
            maxLabel = thisLabel;
        }
      } catch (NumberFormatException e) {
        // ignore, will likely fail later anyway
      }
    }

    Element wiringElt;
    String wiringLabel;
    Element newBaseElt;
    String newBaseLabel;
    if (oldBaseElt != null) {
      wiringLabel = oldBaseLabel;
      wiringElt = oldBaseElt;
      wiringElt.setAttribute("desc", "#Wiring");

      newBaseLabel = "" + (maxLabel + 1);
      newBaseElt = doc.createElement("lib");
      newBaseElt.setAttribute("desc", "#Base");
      newBaseElt.setAttribute("name", newBaseLabel);
      root.insertBefore(newBaseElt, lastLibElt.getNextSibling());
    } else {
      wiringLabel = "" + (maxLabel + 1);
      wiringElt = doc.createElement("lib");
      wiringElt.setAttribute("desc", "#Wiring");
      wiringElt.setAttribute("name", wiringLabel);
      root.insertBefore(wiringElt, lastLibElt.getNextSibling());

      newBaseLabel = null;
      newBaseElt = null;
    }

    HashMap<String, String> labelMap = new HashMap<>();
    addToLabelMap(labelMap, oldBaseLabel, newBaseLabel, "Poke Tool;"
        + "Edit Tool;Select Tool;Wiring Tool;Text Tool;Menu Tool;Text");
    addToLabelMap(labelMap, oldBaseLabel, wiringLabel, "Splitter;Pin;"
        + "Probe;Tunnel;Clock;Pull Resistor;Bit Extender");
    addToLabelMap(labelMap, gatesLabel, wiringLabel, "Constant");
    relocateTools(oldBaseElt, newBaseElt, labelMap);
    relocateTools(oldBaseElt, wiringElt, labelMap);
    relocateTools(gatesElt, wiringElt, labelMap);
    updateFromLabelMap(XmlIterator.forDescendantElements(root, "comp"),
        labelMap);
    updateFromLabelMap(XmlIterator.forDescendantElements(root, "tool"),
        labelMap);
  }

  private void updateFromLabelMap(Iterable<Element> elts,
      HashMap<String, String> labelMap) {
    for (Element elt : elts) {
      String oldLib = elt.getAttribute("lib");
      String name = elt.getAttribute("name");
      if (oldLib != null && name != null) {
        String newLib = labelMap.get(oldLib + ":" + name);
        if (newLib != null) {
          elt.setAttribute("lib", newLib);
        }
      }
    }
  }

  private static String findLibNameByDesc(Element root, String libdesc) {
    for (Element libElt : XmlIterator.forChildElements(root, "lib")) {
      String desc = libElt.getAttribute("desc");
      String name = libElt.getAttribute("name");
      if (name != null && desc != null && desc.equals(libdesc))
        return name;
    }
    return null;
  }

  public static ArrayList<Component> findComponent(Circuit circuit, String path) {
    if (!path.startsWith("/"))
      return null;
    String[] elts = path.substring(1).split("/", -1);
    ArrayList<Component> cpath = new ArrayList<>();
    // subcirc(main)@(x,y)/subcirc(foo)@(x,y)/RAM@(x,y)
    for (int i = 0; i < elts.length; i++) {
      int idx = elts[i].lastIndexOf('@');
      if (idx <= 0)
        return null;
      String name = elts[i].substring(0, idx);
      Location loc;
      try {
        loc = Location.parse(elts[i].substring(idx+1));
      } catch (NumberFormatException e) {
        return null;
      }
      if (i < elts.length-1) {
        if (!name.startsWith("subcirc(") || !name.endsWith(")"))
          return null;
        String subcircName = name.substring(8, name.length()-1);
        Circuit subcirc = null;
        for (Component sub : circuit.getComponents(loc)) {
          if (!(sub.getFactory() instanceof SubcircuitFactory))
              continue;
          SubcircuitFactory f = (SubcircuitFactory)sub.getFactory();
          if (f.getSubcircuit().getName().equals(subcircName)) {
            subcirc = f.getSubcircuit();
            cpath.add(sub);
            break;
          }
        }
        if (subcirc == null)
          return null;
        circuit = subcirc;
      } else {
        // for (Component sub : circuit.getComponents(loc))
        for (Component sub : circuit.getNonWires()) {
          if (!sub.getLocation().equals(loc))
            continue;
          if (!(sub.getFactory().getName().equals(name)))
            continue;
          cpath.add(sub);
          return cpath;
        }
      }
    }
    return null;
  }

}
