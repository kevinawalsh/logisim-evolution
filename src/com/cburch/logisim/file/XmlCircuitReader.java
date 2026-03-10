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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.w3c.dom.Element;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutator;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.appear.AppearanceSvgReader;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.std.decor.Text;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Debug;

import javax.swing.JOptionPane;

public class XmlCircuitReader extends CircuitTransaction {

  static boolean trackercomp_warned = false;

  /**
   * Get a circuit's component from a read XML file. 
   *
   * @param elt
   *            XML element to parse
   * @param reader
   *            XML file reader
   * @return the component built from its XML description
   * @throws XmlReaderException
   */
  static Component getComponent(Element elt, XmlReader.ReadContext reader)
      throws XmlReaderException {

    // Someone (REDS-HEIG?) apparently has files containing this secret
    // value and only shows these components in special "tracker" versions
    // of logisim. 
    if (!trackercomp_warned && elt.getAttribute("trackercomp") != "") {
      trackercomp_warned = true;
      String msg =
          "WARNING: This file contains mysterious \"tracked\" components and may not\n"
          + "work properly in this version of Logisim-Evolution. The file will be opened\n"
          + "anyway, in the hope it might work. You may want to instead edit the \".circ\"\n"
          + "file by hand, in a text editor, to remove the offending components with the\n"
          + "\"trackercomp\" XML attrbute. Contact me at <kwalsh@holycross.edu> as\n"
          + "well, since I'm curious what this is all about.";
      if (Main.headless) {
        System.err.println(msg);
      } else {
        JOptionPane.showMessageDialog(null, msg);
      }
    }

    // Determine the factory that creates this element
    String name = elt.getAttribute("name");
    if (name == null || name.equals("")) {
      throw new XmlReaderException(S.get("compNameMissingError"));
    }

    String libName = elt.getAttribute("lib");
    Library lib = reader.findLibrary(libName);
    if (lib == null) // skip components from missing libs
      return null; // throw new XmlReaderException(S.fmt("compUnknownError", name + " (missing library)"));

    Tool tool = lib.getTool(name);
    if (tool == null || !(tool instanceof AddTool)) {
      if (libName == null || libName.equals("")) {
        return null; // throw new XmlReaderException(S.fmt("compUnknownError", name));
      } else {
        throw new XmlReaderException(S.fmt("compAbsentError", name, libName));
      }
    }
    ComponentFactory source = ((AddTool) tool).getFactory();

    // Determine attributes
    AttributeSet attrs = source.createAttributeSet();
    reader.initAttributeSet(elt, attrs, source);

    // Create component if location known
    Location loc = parseComponentLoc(elt, source.getName()); // name
    Component comp = source.createComponent(loc, attrs);
    if (comp == null)
      throw new XmlReaderException(String.format("Error instantiating component `%s' from library `%s'.", name, libName));
    return comp;
  }

  private XmlReader.ReadContext reader;

  private List<XmlReader.CircuitData> circuitsData;

  public XmlCircuitReader(XmlReader.ReadContext reader,
      List<XmlReader.CircuitData> circDatas) {
    this.reader = reader;
    this.circuitsData = circDatas;
  }

  public static Location parseComponentLoc(Element elt, String name)
      throws XmlReaderException {
    String str = elt.getAttribute("loc");
    if (str == null || str.equals(""))
      throw new XmlReaderException(S.fmt("compLocMissingError", name));
    try {
      return Location.parse(str);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.fmt("compLocInvalidError", name, str));
    }
  }

  public static Location parseWireEnd(Element elt, String end) throws XmlReaderException {
    try {
      String str = elt.getAttribute(end);
      if (str == null || str.equals(""))
        throw new XmlReaderException(S.get("wireEndMissingError"));
      return Location.parse(str);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.get("wireEndInvalidError"));
    }
  }

  public static Wire parseWire(Element elt) throws XmlReaderException {
    Location pt0 = parseWireEnd(elt, "from");
    Location pt1 = parseWireEnd(elt, "to");
    if (pt0.equals(pt1))
      throw new XmlReaderException(S.get("wireLengthZeroError"));
    return Wire.create(pt0, pt1);
  }

  void addWire(Circuit dest, CircuitMutator mutator, Element elt)
      throws XmlReaderException {
    mutator.add(dest, parseWire(elt));
  }

  private void buildCircuit(XmlReader.CircuitData circData, CircuitMutator mutator) {
    Element elt = circData.circuitElement;
    Circuit dest = circData.circuit;
    Map<Element, Component> knownComponents = circData.knownComponents;
    if (knownComponents == null)
      knownComponents = Collections.emptyMap();
    try {
      reader.initAttributeSet(circData.circuitElement,
          dest.getStaticAttributes(), null);
    } catch (XmlReaderException e) {
      reader.addErrors(e, circData.circuit.getName() + ".static");
    }

    HashMap<Bounds, Component> componentsAt = new HashMap<>();
    ArrayList<Component> overlapComponents = new ArrayList<>();
    for (Element sub_elt : XmlIterator.forChildElements(elt)) {
      String sub_elt_name = sub_elt.getTagName();
      if (sub_elt_name.equals("comp")) {
        try {
          Component comp;
          if (knownComponents.containsKey(sub_elt)) {
            comp = knownComponents.get(sub_elt);
            if (comp == null)
              continue; // deliberately skipped
          } else {
            comp = getComponent(sub_elt, reader);
            if (comp == null)
              throw new XmlReaderException(S.fmt("compUnknownError", sub_elt.getAttribute("name")));
          }
          Bounds bds = comp.getNominalBounds();
          if (bds.isEmpty()) {
            // For text components, don't even print a warning, since empty text
            // components have plagued many files, since there are still many
            // ways to inadvertently create empty text components.
            // Also note: All text components now have the same nominal bounds,
            // even empty text components.
            if (!(comp.getFactory() instanceof Text))
              Debug.println(1, "Note: Within " + circData.circuit.getName() +", ignoring component with empty bounds: " + comp);
          } else {
            Component conflict = componentsAt.get(bds);
            if (conflict != null) {
              // oops... overlapping components...
              reader.addError(S.fmt("fileComponentOverlapError",
                    conflict.getFactory().getName()+conflict.getLocation(),
                    comp.getFactory().getName()+conflict.getLocation()),
                    circData.circuit.getName());
              overlapComponents.add(comp);
            } else {
              componentsAt.put(bds, comp);
              mutator.add(dest, comp);
            }
          }
        } catch (XmlReaderException e) {
          reader.addErrors(e, circData.circuit.getName() + "."
              + toComponentString(sub_elt));
        }
      } else if (sub_elt_name.equals("wire")) {
        try {
          addWire(dest, mutator, sub_elt);
        } catch (XmlReaderException e) {
          reader.addErrors(e, circData.circuit.getName() + "."
              + toWireString(sub_elt));
        }
      }
    }
    for (Component comp : overlapComponents) {
      Bounds bds = comp.getNominalBounds();
      int d = 0;
      do {
        d += 10;
      } while (componentsAt.get(bds.translate(d, d)) != null);
      Location loc = comp.getLocation().translate(d, d);
      AttributeSet attrs = (AttributeSet) comp.getAttributeSet().clone();
      comp = comp.getFactory().createComponent(loc, attrs);
      componentsAt.put(comp.getNominalBounds(), comp);
      mutator.add(dest, comp);
    }
  }

  static boolean hasDynamicAppearance(Element elt) {
    return elt.getTagName().startsWith("visible-")
        || !elt.getAttribute("visibility").equals("");
  }

  private void buildDynamicAppearance(XmlReader.CircuitData circData, CircuitMutator mutator) {
    Circuit dest = circData.circuit;
    List<AbstractCanvasObject> shapes = new ArrayList<>();
    List<Integer> layers = new ArrayList<>();
    int layer = -1;
    for (Element appearElt : XmlIterator.forChildElements(circData.circuitElement, "appear")) {
      for (Element sub : XmlIterator.forChildElements(appearElt)) {
        layer++;
        // Dynamic shapes are handled here. Static shapes are already done.
        if (!hasDynamicAppearance(sub))
          continue;
        try {
          AbstractCanvasObject m = AppearanceSvgReader.createShape(sub, null, dest, reader);
          if (m == null) {
            reader.addError(S.fmt("fileAppearanceNotFound", sub.getTagName()),
                circData.circuit.getName() + "." + sub.getTagName());
          } else {
            shapes.add(m);
            layers.add(layer);
          }
        } catch (RuntimeException e) {
          reader.addError(S.fmt("fileAppearanceError", sub.getTagName()),
              circData.circuit.getName() + "." + sub.getTagName());
        }
      }
    }
    if (!shapes.isEmpty()) {
      if (circData.appearance == null) {
        circData.appearance = shapes;
      } else {
        for (int i = 0; i < shapes.size(); i++)
          circData.appearance.add(layers.get(i), shapes.get(i));
      }
    }
    if (circData.appearance != null && !circData.appearance.isEmpty()) {
      dest.getAppearance().setObjectsForce(circData.appearance);
      dest.getAppearance().setDefaultAppearance(false);
    }
  }

  @Override
  protected Map<Circuit, Integer> getAccessedCircuits() {
    HashMap<Circuit, Integer> access = new HashMap<Circuit, Integer>();
    for (XmlReader.CircuitData data : circuitsData) {
      access.put(data.circuit, READ_WRITE);
    }
    return access;
  }

  @Override
  protected void run(CircuitMutator mutator) {
    for (XmlReader.CircuitData circuitData : circuitsData) {
      buildCircuit(circuitData, mutator);
    }
    for (XmlReader.CircuitData circuitData : circuitsData) {
      buildDynamicAppearance(circuitData, mutator);
    }
  }

  private String toComponentString(Element elt) {
    String name = elt.getAttribute("name");
    String loc = elt.getAttribute("loc");
    return name + "(" + loc + ")";
  }

  private String toWireString(Element elt) {
    String from = elt.getAttribute("from");
    String to = elt.getAttribute("to");
    return "w" + from + "-" + to;
  }
}
