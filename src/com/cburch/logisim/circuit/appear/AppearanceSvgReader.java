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

package com.cburch.logisim.circuit.appear;

import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.SvgReader;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.XmlReader;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.std.io.HexDigitShape;
import com.cburch.logisim.std.io.LedShape;
import com.cburch.logisim.std.io.MeterShape;
import com.cburch.logisim.std.io.RGBLedShape;
import com.cburch.logisim.std.io.SevenSegmentShape;
import com.cburch.logisim.std.memory.CounterShape;
import com.cburch.logisim.std.memory.RegisterShape;
import com.cburch.logisim.std.wiring.ProbeShape;
import com.cburch.logisim.util.Debug;

public class AppearanceSvgReader {

  public static AbstractCanvasObject createShape(Element elt,
      Map<Location, Instance> pins, Circuit circuit, XmlReader.ReadContext ctx) {
    String name = elt.getTagName();
    if (name.equals("circ-anchor") || name.equals("circ-origin")) {
      Location loc = getLocation(elt);
      AbstractCanvasObject ret = new AppearanceAnchor(loc);
      if (elt.hasAttribute("facing")) {
        Direction facing = Direction.parse(elt.getAttribute("facing"));
        ret.setAttr(AppearanceAnchor.FACING, facing);
      }
      return ret;
    } else if (name.equals("circ-port")) {
      Location loc = getLocation(elt);
      String[] pinStr = elt.getAttribute("pin").split(",");
      Location pinLoc = Location.create(
          Integer.parseInt(pinStr[0].trim()),
          Integer.parseInt(pinStr[1].trim()));
      Instance pin = pins.get(pinLoc);
      if (pin == null)
        return null;
      return new AppearancePort(loc, pin);
    } 
		AbstractCanvasObject ret;
    if (name.startsWith("visible-")) {
      String pathstr = elt.getAttribute("path");
      if (pathstr == null || pathstr.length() == 0)
        return null;
      DynamicElement.Path path;
      try {
        path = DynamicElement.Path.fromSvgString(pathstr, circuit);
      } catch (IllegalArgumentException e) {
        System.out.println(e.getMessage());
        return null;
      }
      if (path == null)
        return null;
      int x = (int)Double.parseDouble(elt.getAttribute("x").trim());
      int y = (int)Double.parseDouble(elt.getAttribute("y").trim());
      DynamicElement shape;
      if (name.equals("visible-led")) {
        shape = new LedShape(x, y, path);
      } else if (name.equals("visible-rgbled")) {
        shape = new RGBLedShape(x, y, path);
      } else if (name.equals("visible-hexdigit")) {
        shape = new HexDigitShape(x, y, path);
      } else if (name.equals("visible-sevensegment")) {
        shape = new SevenSegmentShape(x, y, path);
      } else if (name.equals("visible-register")) {
        shape = new RegisterShape(x, y, path);
      } else if (name.equals("visible-counter")) {
        shape = new CounterShape(x, y, path);
      } else if (name.equals("visible-meter")) {
        shape = new MeterShape(x, y, path);
      } else if (name.equals("visible-probe")) {
        shape = new ProbeShape(x, y, path);
      } else {
        return null;
      }
      try {
        shape.parseSvgElement(elt);
      } catch (Exception e) {
        Debug.error(e);
        e.printStackTrace();
        throw e;
      }
      ret = shape;
    } else {
      ret = SvgReader.createShape(elt);
    }
    if (ret == null)
      return null;
		List<Attribute<?>> attrs = ret.getAttributes();
		if (attrs.contains(DrawAttr.DYNAMIC_CONDITION)) {
			String dyn = elt.getAttribute("visibility");
			if (dyn.equals("")) {
        ret.setAttr(DrawAttr.DYNAMIC_CONDITION, null);
      } else {
        try {
          DynamicCondition visibility = DynamicCondition.fromSvgString(dyn, circuit);
          ret.setAttr(DrawAttr.DYNAMIC_CONDITION, visibility);
        } catch (Throwable t) {
          ctx.addError("Error parsing dynamic visibility condition", t.getMessage());
        }
      }
    }
    return ret;
  }

  private static Location getLocation(Element elt) {
    double x = Double.parseDouble(elt.getAttribute("x"));
    double y = Double.parseDouble(elt.getAttribute("y"));
    double w = Double.parseDouble(elt.getAttribute("width"));
    double h = Double.parseDouble(elt.getAttribute("height"));
    int px = (int) Math.round(x + w / 2);
    int py = (int) Math.round(y + h / 2);
    return Location.create(px, py);
  }
}
