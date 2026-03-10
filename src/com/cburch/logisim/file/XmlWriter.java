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
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;

import com.bfh.logisim.fpga.PinBindings;
import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.appear.DrawingsClip;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.InputEventUtil;

public class XmlWriter {

  /* We sort some parts of the xml tree, to help with reproducibility and to
   * ease testing (e.g. diff a circuit file). Attribute name=value pairs seem
   * to be sorted already, so we don't worry about those. The code below sorts
   * the nodes, but only in best-effort fashion (some nodes are identical
   * except for their child contents, which seems overkill to bother sorting).
   * Parts of the tree where node order matters (top-level "project", the
   * libraries, and the toolbar, for example) are not sorted.
   */

  static String attrToString(Attr a) {
    String n = a.getName();
    String v = a.getValue().replaceAll("&", "&amp;").replaceAll("\"", "&quot;");
    return n + "=\"" + v + "\"";
  }

  static String attrsToString(NamedNodeMap a) {
    int n = a.getLength();
    if (n == 0)
      return "";
    else if (n == 1)
      return attrToString((Attr)a.item(0));
    ArrayList<String> lst = new ArrayList<String>();
    for (int i = 0; i < n; i++) {
      lst.add(attrToString((Attr)a.item(i)));
    }
    Collections.sort(lst);
    String s = lst.get(0);
    for (int i = 1; i < n; i++)
      s = s + " " + lst.get(i);
    return s;
  }

  static int stringCompare(String a, String b) {
    if (a == b) return 0;
    else if (a == null) return -1;
    else if (b == null) return 1;
    else return a.compareTo(b);
  }

  static Comparator<Node> nodeComparator = new Comparator<Node>() {
    public int compare(Node a, Node b) {
      String na = a.getNodeName();
      String nb = b.getNodeName();
      int c = stringCompare(na, nb);
      if (c != 0) return c;
      String ma = attrsToString(a.getAttributes());
      String mb = attrsToString(b.getAttributes());
      c = stringCompare(ma, mb);
      if (c != 0) return c;
      String va = a.getNodeValue();
      String vb = b.getNodeValue();
      c = stringCompare(va, vb);
      if (c != 0) return c;
      // This can happen in some cases, e.g. two text components
      // on top of each other. But it seems rare enough to not
      // worry about, since our normalization here is just for
      // ease of comparing circ files during testing.
      // System.out.printf("sorts equal:\n");
      // System.out.printf(" a: <%s %s>%s\n", na, ma, va);
      // System.out.printf(" b: <%s %s>%s\n", nb, mb, vb);
      return 0;
    }
  };

  static void sort(Node top) {
    NodeList children = top.getChildNodes();
    int n = children.getLength();
    String name = top.getNodeName();
    // see: comments about sort() below
    // Do not sort: top level, project, toolbar, lib, appear
    if (n > 1 && !name.equals("project") && !name.equals("lib") && !name.equals("toolbar") && !name.equals("appear")) {
      Node[] a = new Node[n];
      for (int i = 0; i < n; i++)
        a[i] = children.item(i);
      Arrays.sort(a, nodeComparator);
      for (int i = 0; i < n; i++)
        top.insertBefore(a[i], null); // moves a[i] to end
    }
    for (int i = 0; i < n; i++) {
      sort(children.item(i));
    }
  }

  private static void xform(Document doc, OutputStream out) {
    try {
      TransformerFactory tfFactory = TransformerFactory.newInstance();
      try { tfFactory.setAttribute("indent-number", Integer.valueOf(2)); }
      catch (IllegalArgumentException e) { } // non-fatal
        Transformer tf = tfFactory.newTransformer();
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
      try { tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); }
      catch (IllegalArgumentException e) { } // non-fatal

      doc.normalize();
      sort(doc);
      Source src = new DOMSource(doc);
      Result dest = new StreamResult(out);
      tf.transform(src, dest);
    } catch (Exception e) { } // non-fatal
  }

  static void write(LogisimFile file, Project proj, OutputStream out, File destFile)
      throws ParserConfigurationException, TransformerConfigurationException, TransformerException {

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.newDocument();

    XmlWriter context = new XmlWriter(file, proj, doc, destFile);
    context.fromLogisimFile();
    xform(doc, out);
  }

  public static String encodeSelection(LogisimFile file, Project proj, Object sel) {
    try {
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
      Document doc = docBuilder.newDocument();

      XmlWriter context = new XmlWriter(file, proj, doc, null);

      context.fromSelection(sel);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      xform(doc, out);
      String xml = new String(out.toByteArray(), "UTF-8");
      return xml;
    } catch (Exception e) {
      Errors.title("Clipboard Error").show("Error serializing data to clipboard.", e);
      return null;
    }
  }

  private LogisimFile file;
  private Project proj;
  private Document doc;
  private File destFile; // file being written, used to relativize library paths
  private String destDir; // dir path of circ file begin written, used to relativize paths of components
  private HashMap<Library, String> libIDs = new HashMap<>();

  private XmlWriter(LogisimFile file, Project proj, Document doc, File destFile) {
    this.file = file;
    this.proj = proj;
    this.doc = doc;
    this.destFile = destFile;
    if (destFile != null)
      this.destDir = destFile.getAbsoluteFile().getParent().toString();
  }

  Library findLibrary(ComponentFactory source) {
    if (file.contains(source)) {
      return file;
    }
    for (Library lib : file.getLibraries()) {
      if (lib.contains(source))
        return lib;
    }
    return null;
  }

  Library findLibrary(Tool tool) {
    if (libraryContains(file, tool)) {
      return file;
    }
    for (Library lib : file.getLibraries()) {
      if (libraryContains(lib, tool))
        return lib;
    }
    return null;
  }

  void addAttributeSetContent(Element elt,
      AttributeSet attrs, AttributeDefaultProvider source) {
    XmlAttributesUtil.addAttributeSetContent(doc, destDir, elt, attrs, source);
  }

  private static String subcircPathName(String circName, Location loc) {
    return String.format("subcirc(%s)@%s", circName, loc);
  }

  private static String componentPathName(Component comp) {
    return String.format("%s@%s", comp.getFactory().getName(), comp.getLocation());
  }

  private void enumerateNonVolatileState(String path, CircuitState circState,
      HashMap<String, AttributeSet> nvs) {
    Circuit circuit = circState.getCircuit();
    for (Component comp : circuit.getNonWires()) {
      ComponentFactory factory = comp.getFactory();
      if (factory instanceof SubcircuitFactory) {
        Circuit subcirc = ((SubcircuitFactory)factory).getSubcircuit();
        CircuitState subState = circState.getDataForSubcircuit(comp);
        if (subState == null)
          continue;
        String subpath = path + subcircPathName(subcirc.getName(), comp.getLocation()) + "/";
        enumerateNonVolatileState(subpath, subState, nvs);
      } else {
        AttributeSet attrs = factory.getNonVolatileSimulationState(comp, circState);
        if (attrs == null)
          continue;
        String nodepath = path + componentPathName(comp);
        AttributeSet old = nvs.put(nodepath, attrs);
        if (old != null)
          showError(S.fmt("nonVolatilePathConflictError", nodepath));
      }
    }
  }

  Element fromCircuit(Circuit circuit, AddTool tool) {
    Element ret = doc.createElement("circuit");
    ret.setAttribute("name", circuit.getName());
    addAttributeSetContent(ret, circuit.getStaticAttributes(), circuit);
    if (tool != null)
      addAttributeSetContent(ret, tool.getAttributeSet(), tool);
    for (PinBindings.Config fpgaconfig : circuit.getFPGAConfigs()) {
      ret.appendChild(fpgaconfig.toXml(doc));
    }
    // If there are top-level simulations of this circuit, save any non-volatile
    // state associated with them.
    for (CircuitState circState : proj.getRootCircuitStates()) {
      if (!circState.getCircuit().equals(circuit))
        continue;
      HashMap<String, AttributeSet> nvs = new HashMap<>();
      enumerateNonVolatileState("/", circState, nvs);
      if (nvs.size() == 0)
        continue;
      Element sim = doc.createElement("simulation");
      nvs.forEach((path, attrs) -> {
        Element elt = doc.createElement("state");
        elt.setAttribute("path", path);
        addAttributeSetContent(elt, attrs, null);
        sim.appendChild(elt);
      });
      ret.appendChild(sim);
    }
    if (!circuit.getAppearance().isDefaultAppearance()) {
      Element appear = doc.createElement("appear");
      for (Object o : circuit.getAppearance().getObjectsFromBottom()) {
        if (o instanceof AbstractCanvasObject) {
          AbstractCanvasObject shape = (AbstractCanvasObject)o;
          Element elt = shape.toDynamicSvgElement(doc);
          if (elt != null)
            appear.appendChild(elt);
        }
      }
      ret.appendChild(appear);
    }
    for (Wire w : circuit.getWires()) {
      ret.appendChild(fromWire(w));
    }
    for (Component comp : circuit.getNonWires()) {
      Element elt = fromComponent(comp);
      if (elt != null)
        ret.appendChild(elt);
    }
    return ret;
  }

  Element fromVhdl(VhdlContent vhdl) {
    vhdl.aboutToSave();
    Element ret = doc.createElement("vhdl");
    ret.setAttribute("name", vhdl.getName());
    if (vhdl.getAppearance() == StdAttr.APPEAR_CLASSIC)
      ret.setAttribute("appearance", "classic");
    ret.setTextContent(vhdl.getContent());
    return ret;
  }

  private void showError(String description, Throwable ...errs) {
    String name = destFile != null
        ? destFile.getName()
        : file.getName();
    Errors.project(name).show(description, errs);
  }

  Element fromComponent(Component comp) {
    ComponentFactory source = comp.getFactory();
    // Special case (hack): eliminate any Text nodes having no children, i.e. no
    // text contents. This is a workaround for a long-standing issue where empty
    // text nodes can be made inadvertently, causing warnings when opening files
    // and littering the canvas with invisible empty Text components.
    if (source instanceof Text) {
      String text = comp.getAttributeSet().getValue(Text.ATTR_TEXT);
      if (text == null || text.length() == 0) {
        return null;
      }
    }
    Library lib = findLibrary(source);
    String lib_id;
    if (lib == null) {
      showError(S.fmt("componentLibraryMissingError", source.getName()));
      return null;
    } else if (lib == file) {
      lib_id = null;
    } else {
      lib_id = libIDs.get(lib);
      if (lib_id == null) {
        showError(S.fmt("componentLibraryUnregisteredError", lib.getName(), source.getName()));
        return null;
      }
    }

    Element ret = doc.createElement("comp");
    if (lib_id != null)
      ret.setAttribute("lib", lib_id);
    ret.setAttribute("name", source.getName());
    ret.setAttribute("loc", comp.getLocation().toString());
    addAttributeSetContent(ret, comp.getAttributeSet(), comp.getFactory());
    return ret;
  }

  Element fromLibrary(Library lib) {
    Element ret = doc.createElement("lib");
    if (libIDs.containsKey(lib))
      return null;
    String lib_id = "" + libIDs.size();
    String desc = destFile != null
        ? LibraryManager.instance.getRelativeDescriptor(destFile, lib)
        : LibraryManager.instance.getAbsoluteDescriptor(lib);
    if (desc == null) { // should never happen for a loaded file?
      showError("internal error: missing library: " + lib.getName());
      return null;
    }
    libIDs.put(lib, lib_id);
    ret.setAttribute("name", lib_id);
    ret.setAttribute("desc", desc);
    for (Tool t : lib.getTools()) {
      AttributeSet attrs = t.getAttributeSet();
      if (attrs != null) {
        Element toAdd = doc.createElement("tool");
        toAdd.setAttribute("name", t.getName());
        addAttributeSetContent(toAdd, attrs, t);
        if (toAdd.getChildNodes().getLength() > 0) {
          ret.appendChild(toAdd);
        }
      }
    }
    return ret;
  }

  Element fromDrawings(DrawingsClip drawings) {
    Element ret = doc.createElement("drawings");
    Direction af = drawings.getAnchorFacing();
    Location al = drawings.getAnchorLocation();
    if (af != null || al != null) {
      Element anchor = doc.createElement("anchor-info");
      if (af != null)
        anchor.setAttribute("facing", af.toString());
      if (al != null)
        anchor.setAttribute("location", al.toString());
      ret.appendChild(anchor);
    }
    for (Object o : drawings.getElements()) {
      if (o instanceof AbstractCanvasObject) {
        AbstractCanvasObject shape = (AbstractCanvasObject)o;
        Element elt = shape.toDynamicSvgElement(doc);
        if (elt != null)
          ret.appendChild(elt);
      }
    }
    return ret;
  }

  // see: sort() above
  // project (contains ordered elements, do not sort)
  // - main
  // - toolbar (contains ordered elements, do not sort)
  //   - tool*
  //     - a*
  // - lib* (contains ordered elements, do not sort)
  //   - tool*
  //     - a*
  // - options
  //   - a*
  // - mappings
  //   - tool*
  //     - a*
  // - circuit*
  //   - simulation*
  //   - a*
  //   - appear (contains ordered elements, do not sort)
  //     - circ-anchor
  //     - circ-port*
  //     - path*
  //     - rect*
  //     - ellipse*
  //     - ...
  //   - comp*
  //   - wire*
  // - vhdl*
  Element fromLogisimFile() {
    Element ret = doc.createElement("project");
    doc.appendChild(ret);
    ret.appendChild(doc
        .createTextNode("\nThis file is intended to be "
          + "loaded by Logisim-evolution (https://github.com/kevinawalsh/logisim-evolution).\n"));
    ret.setAttribute("version", "1.0");
    ret.setAttribute("source", Main.VERSION_NAME);

    for (Library lib : file.getLibraries()) {
      Element elt = fromLibrary(lib);
      if (elt != null)
        ret.appendChild(elt);
    }

    if (file.getMainCircuit() != null) {
      Element mainElt = doc.createElement("main");
      mainElt.setAttribute("name", file.getMainCircuit().getName());
      ret.appendChild(mainElt);
    }

    ret.appendChild(fromOptions());
    ret.appendChild(fromMouseMappings());
    ret.appendChild(fromToolbarData());

    for (Circuit circ : file.getCircuits()) {
      ret.appendChild(fromCircuit(circ, file.findToolFor(circ)));
    }
    for (VhdlContent vhdl : file.getVhdlContents()) {
      ret.appendChild(fromVhdl(vhdl));
    }
    return ret;
  }

  private void scanSelection(Collection<Component> sel,
    HashSet<Library> usedLibs, HashSet<Circuit> usedCircs, HashSet<VhdlContent> usedVhdl) {
    for (Component c : sel) {
      if (c instanceof Wire)
        continue;
      ComponentFactory f = c.getFactory();
      Library lib = findLibrary(f); 
      if (lib == null)
        throw new IllegalStateException("missing library for " + f.getDisplayName());
      usedLibs.add(lib);
      if (f instanceof SubcircuitFactory) {
        Circuit circ = ((SubcircuitFactory)f).getSubcircuit();
        if (usedCircs.add(circ))
          scanSelection(circ.getNonWires(), usedLibs, usedCircs, usedVhdl);
      } else if (f instanceof VhdlEntity) {
        VhdlEntity vhdl = (VhdlEntity)f;
        usedVhdl.add(vhdl.getContent());
          // scanSelection(vhdl....?); // todo: no current way to track vhdl dependencies
      }
    }
  }

  // see: sort() above
  // clipdata (contains ordered elements, do not sort)
  // - selection
  //   - circuit; or vhdl; or comp* and wire*; or lib
  // - circuit*
  //   - simulation*
  //   - a*
  //   - comp*
  //   - wire*
  // - vhdl*
  // - lib* (contains ordered elements, do not sort)
  //   - tool*
  //     - a*
  private Element fromSelection(Object sel) {
    Element ret = doc.createElement("clipdata");
    doc.appendChild(ret);
    ret.appendChild(doc
        .createTextNode("\nThis clipboard data is intended to be "
          + " used by Logisim-evolution (https://github.com/kevinawalsh/logisim-evolution).\n"));
    ret.setAttribute("version", "1.0");
    ret.setAttribute("source", Main.VERSION_NAME);

    HashSet<Library> usedLibs = new HashSet<>();
    HashSet<Circuit> usedCircs = new HashSet<>();
    HashSet<VhdlContent> usedVhdl = new HashSet<>();

    if (sel instanceof Circuit)
      scanSelection(((Circuit)sel).getNonWires(), usedLibs, usedCircs, usedVhdl);
    else if (sel instanceof Collection<?>)
      scanSelection((Collection<Component>)sel, usedLibs, usedCircs, usedVhdl);
    // else: todo: no current way to track vhdl dependencies
    // also, we don't currently handle direct copy-paste of cross-process dynamic appearance elements

    for (Library lib : usedLibs) {
      if (lib == file)
        continue;
      Element elt = fromLibrary(lib);
      if (elt != null)
        ret.appendChild(elt);
    }

    Element e = doc.createElement("selection");
    if (sel instanceof Circuit) {
      e.setAttribute("type", "circuit"); // not used by parser
      e.appendChild(fromCircuit((Circuit)sel, file.findToolFor((Circuit)sel)));
    } else if (sel instanceof VhdlContent) {
      e.setAttribute("type", "vhdl"); // not used by parser
      e.appendChild(fromVhdl((VhdlContent)sel));
    } else if (sel instanceof Collection<?>) {
      e.setAttribute("type", "components"); // not used by parser
      Collection<Component> csel = (Collection<Component>)sel;
      for (Component c : csel) {
        if (c instanceof Wire)
          e.appendChild(fromWire((Wire)c));
      }
      for (Component c : csel) {
        if (!(c instanceof Wire)) {
          Element e2 = fromComponent(c);
          if (e2 != null)
            e.appendChild(e2);
        }
      }
    } else if (sel instanceof Library) {
      e.setAttribute("type", "lib"); // not used by parser
      e.appendChild(fromLibrary((Library)sel));
    } else if (sel instanceof DrawingsClip) {
      e.setAttribute("type", "drawings"); // not used by parser
      e.appendChild(fromDrawings((DrawingsClip)sel));
    } else {
      throw new IllegalArgumentException("clipboard type not supported: " + sel);
    }
    ret.appendChild(e);

    for (Circuit circ : usedCircs)
      ret.appendChild(fromCircuit(circ, file.findToolFor(circ)));
    for (VhdlContent vhdl : usedVhdl)
      ret.appendChild(fromVhdl(vhdl));
    return ret;
  }

  Element fromMouseMappings() {
    Element elt = doc.createElement("mappings");
    MouseMappings map = file.getOptions().getMouseMappings();
    for (Map.Entry<Integer, Tool> entry : map.getMappings().entrySet()) {
      Integer mods = entry.getKey();
      Tool tool = entry.getValue();
      Element toolElt = fromTool(tool);
      String mapValue = InputEventUtil.toXMLString(mods.intValue());
      toolElt.setAttribute("map", mapValue);
      elt.appendChild(toolElt);
    }
    return elt;
  }

  Element fromOptions() {
    Element elt = doc.createElement("options");
    addAttributeSetContent(elt, file.getOptions().getAttributeSet(), null);
    return elt;
  }

  Element fromTool(Tool tool) {
    Library lib = findLibrary(tool);
    String lib_id;
    if (lib == null) {
      showError(S.fmt("toolLibraryMissingError", tool.getDisplayName()));
      return null;
    } else if (lib == file) {
      lib_id = null;
    } else {
      lib_id = libIDs.get(lib);
      if (lib_id == null) {
        showError(S.fmt("toolLibraryUnregisteredError", lib.getName(), tool.getDisplayName()));
        return null;
      }
    }

    Element elt = doc.createElement("tool");
    if (lib_id != null)
      elt.setAttribute("lib", lib_id);
    elt.setAttribute("name", tool.getName());
    addAttributeSetContent(elt, tool.getAttributeSet(), tool);
    return elt;
  }

  Element fromToolbarData() {
    Element elt = doc.createElement("toolbar");
    ToolbarData toolbar = file.getOptions().getToolbarData();
    for (Tool tool : toolbar.getContents()) {
      if (tool == null) {
        elt.appendChild(doc.createElement("sep"));
      } else {
        elt.appendChild(fromTool(tool));
      }
    }
    return elt;
  }

  Element fromWire(Wire w) {
    Element ret = doc.createElement("wire");
    ret.setAttribute("from", w.getEnd0().toString());
    ret.setAttribute("to", w.getEnd1().toString());
    return ret;
  }

  boolean libraryContains(Library lib, Tool query) {
    for (Tool tool : lib.getTools()) {
      if (tool.sharesSource(query))
        return true;
    }
    return false;
  }
}
