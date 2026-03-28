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
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.bfh.logisim.fpga.PinBindings;
import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.appear.AppearanceSvgReader;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class XmlReader {

  public static class CircuitData {

    Element circuitElement;
    Circuit circuit;
    Map<Element, Component> knownComponents = new HashMap<>();
    List<AbstractCanvasObject> appearance = new ArrayList<>();
    List<HashMap<String, Element>> simulations = new ArrayList<>();

    public CircuitData(ReadContext ctx, Element elt, Circuit circ) {
      circuitElement = elt;
      circuit = circ;
      // load known components
      for (Element e : XmlIterator.forChildElements(elt, "comp")) try {
          Component comp = XmlCircuitReader.getComponent(e, ctx);
          if (comp != null)
            knownComponents.put(e, comp);
      } catch (XmlReaderException ex) {
        ctx.addErrors(ex, "parsing component from xml");
      }
      // load appearance
      for (Element e : XmlIterator.forChildElements(elt, "appear"))
        loadAppearance(ctx, e, circ.getName() + ".appear");
      // load fpga configs
      for (Element e : XmlIterator.forChildElements(elt, "fpgaconfig"))
        loadFPGAConfig(ctx, e, circ.getName() + ".fpgaconfig");
      // load non-volatile simulation state
      for (Element e : XmlIterator.forChildElements(elt, "simulation"))
        loadSimulation(ctx, e, circ.getName() + ".simulation");
    }

    private void loadAppearance(ReadContext ctx, Element elt, String context) {
      // Dynamic shapes are skipped here. They are resolved later in
      // XmlCircuitReader once the full Circuit tree has been built.
      // Static shapes (e.g. pins and anchors) need to be done here.
      Map<Location, Instance> pins = new HashMap<>();
      for (Component comp : knownComponents.values()) {
        if (comp != null && comp.getFactory() == Pin.FACTORY) {
          Instance instance = Instance.getInstanceFor(comp);
          pins.put(comp.getLocation(), instance);
        }
      }
      for (Element sub : XmlIterator.forChildElements(elt)) {
        if (XmlCircuitReader.hasDynamicAppearance(sub))
          continue; // skip dynamic shapes
        String tag = sub.getTagName();
        try {
          AbstractCanvasObject shape = AppearanceSvgReader.createShape(sub, pins, null, ctx);
          if (shape == null)
            ctx.addError(S.fmt("fileAppearanceNotFound", tag), context + "." + tag);
          else
            appearance.add(shape);
        } catch (RuntimeException e) {
          ctx.addError(S.fmt("fileAppearanceError", tag), context + "." + tag);
        }
      }
    }

    private void loadFPGAConfig(ReadContext ctx, Element elt, String context) {
      try {
        circuit.saveFPGAConfig(PinBindings.parseConfig(elt));
      } catch (Exception e) {
        ctx.addError(S.fmt("fileFPGAConfigError", e.getMessage()), context);
      }
    }

    private void loadSimulation(ReadContext ctx, Element elt, String context) {
      try {
        HashMap<String, Element> sim = new HashMap<>();
        for (Element e : XmlIterator.forChildElements(elt, "state")) {
          String path = e.getAttribute("path");
          if (path == null || path.equals("")) {
            ctx.addError(S.get("simulationPathMissingError"), context);
            continue;
          }
          // AttributeSet attrs = parseAttributeSet(ctx, e, context + "." + path);
          // AttributeSet old = sim.put(path, attrs);
          Element old = sim.put(path, e);
          if (old != null)
            ctx.addError(S.fmt("simulationPathDuplicateError", path), context);
        }
        if (sim.size() > 0)
          simulations.add(sim);
      } catch (Exception e) {
        ctx.addError(S.fmt("fileLoadSimulationError", e.getMessage()), context);
      }
    }

  }

  public static abstract class ReadContext {

    LogisimFile file;
    String srcDirPath = ""; // used for de-relativizing path names
    LogisimVersion sourceVersion;
    ArrayList<String> messages = new ArrayList<>();

    ReadContext(LogisimFile f, String srcFilePath) {
      file = f;
      if (srcFilePath != null)
        srcDirPath = srcFilePath.substring(0, srcFilePath.lastIndexOf(File.separator));
    }

    public void addError(String message, String context) {
      messages.add(message + " [" + context + "]");
    }

    void addErrors(XmlReaderException exception, String context) {
      for (String msg : exception.getMessages())
        messages.add(msg + " [" + context + "]");
    }

    abstract Library findLibrary(String libName) throws XmlReaderException;

    CircuitData parseCircuit(Element elt) {
      String name = elt.getAttribute("name");
      if (name == null || name.equals("")) {
        addError(S.get("circNameMissingError"), "C??");
        return null;
      }
      Circuit circ = new Circuit(name, file);
      return new CircuitData(this, elt, circ);
    }

    VhdlContent parseVhdl(Element elt) {
      String name = elt.getAttribute("name");
      if (name == null || name.equals("")) {
        addError(S.get("circNameMissingError"), "C??");
        return null;
      }
      String vhdl = elt.getTextContent();
      VhdlContent ret = VhdlContent.parse(name, vhdl, file);
      String a = elt.getAttribute("appearance");
      if (a != null && a.equalsIgnoreCase("classic"))
        ret.getStaticAttributes().setAttr(StdAttr.APPEARANCE, StdAttr.APPEAR_CLASSIC);
      return ret;
    }

    Library parseLibrary(Loader loader, Element elt) throws LoadCanceledByUser {
      if (!elt.hasAttribute("name")) {
        addError(S.get("libNameMissingError"), "loading library");
        return null;
      }
      if (!elt.hasAttribute("desc")) {
        addError(S.get("libDescMissingError"), "loading library");
        return null;
      }
      String name = elt.getAttribute("name");
      String desc = elt.getAttribute("desc");
      Library lib = loader.loadLibrary(desc); // will be null if skipping lib
      if (lib == null)
        return null;
      for (Element e : XmlIterator.forChildElements(elt, "tool")) {
        if (!e.hasAttribute("name")) {
          addError(S.get("toolNameMissingError"), "loading library tool");
        } else {
          String toolName = e.getAttribute("name");
          Tool tool = lib.getTool(toolName);
          if (tool != null) try {
            initAttributeSet(e, tool.getAttributeSet(), tool);
          } catch (XmlReaderException ex) {
            addErrors(ex, "lib." + name + "." + toolName);
          }
        }
      }
      return lib;
    }

    void initAttributeSet(Element parentElt, AttributeSet attrs,
        AttributeDefaultProvider defaults) throws XmlReaderException {
      ArrayList<String> messages = null;

      HashMap<String, String> attrsDefined = new HashMap<>();
      for (Element attrElt : XmlIterator.forChildElements(parentElt, "a")) {
        if (!attrElt.hasAttribute("name")) {
          if (messages == null)
            messages = new ArrayList<String>();
          messages.add(S.get("attrNameMissingError"));
        } else {
          String attrName = attrElt.getAttribute("name");
          String attrVal;
          if (attrElt.hasAttribute("val"))
            attrVal = attrElt.getAttribute("val");
          else
            attrVal = attrElt.getTextContent();
          attrsDefined.put(attrName, attrVal);
        }
      }

      if (attrs == null)
        return;

      boolean setDefaults = defaults != null
          && !defaults.shouldOmitAllAttributesFromXml(attrs, sourceVersion);
      // We need to process this in order, and we have to refetch the
      // attribute list each time because it may change as we iterate
      // (as it will for a splitter).
      // WARNING: This is fragile and error-prone, likely leading to subtle bugs. The ordering of
      // attributes must be carefully arranged to avoid bugs.
      // - For a component where the list of attributes can change depending on some attribute A, we
      //   must ensure that the prefix before A is constant, and any parts of the list that may
      //   change come after A.
      // - For a component where the value of one attribute A constrains the possible
      //   values for another attribute B, then A must come before B in the list.
      // - For a component where setting the value of an attribute A can alter the value of other
      //   attributes, or where the effect of setting A depends on other attributes, then care must
      //   be taken so that effects occur in the intended order.
      // FIXME: Ideally we'd have some better way to initialize an attribute set "all at once", or
      // to somehow determine a correct ordering automatically, or make it order-invariant.
      for (int i = 0; true; i++) {
        List<Attribute<?>> attrList = attrs.getAttributes();
        if (i >= attrList.size())
          break;
        @SuppressWarnings("unchecked")
        Attribute<Object> attr = (Attribute<Object>) attrList.get(i);
        if (!attrs.isToSave(attr))
          continue; // ignore attributes that should never have been saved (like circuit name)
        String attrName = attr.getName();
        String attrVal = attrsDefined.get(attrName);
        if (attrVal == null) {
          if (setDefaults) {
            Object val = defaults.getDefaultAttributeValue(attr, sourceVersion);
            if (val != null)
              attrs.setAttr(attr, val);
          }
        } else {
          try {
            Object val = attr.parseFromFilesystem(new File(srcDirPath), attrVal);
            attrs.setAttr(attr, val);
          } catch (NumberFormatException e) {
            if (messages == null)
              messages = new ArrayList<String>();
            messages.add(S.fmt("attrValueInvalidError", attrVal, attrName));
          }
        }
      }
      if (messages != null) {
        throw new XmlReaderException(messages);
      }
    }

  }

  static Document loadXmlFrom(InputStream is) throws SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      return factory.newDocumentBuilder().parse(is);
    } catch (ParserConfigurationException e) {
      throw new IOException("XML parse configuration error: " + e.getMessage(), e);
    }
  }

}
