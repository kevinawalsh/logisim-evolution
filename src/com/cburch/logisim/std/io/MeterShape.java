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

package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Graphics;
import java.awt.Color;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.draw.shapes.DrawAttr;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.UnmodifiableList;

public class MeterShape extends DynamicElement {

  public MeterShape(int x, int y, DynamicElement.Path p) {
    super(p, getBounds(x, y, p.leaf().getAttributeSet()));
  }

  static Bounds getBounds(int x, int y, AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    AttributeOption shape = attrs.getValue(Meter.ATTR_SHAPE);
    Bounds size;
    if (shape == Meter.SHAPE_DIAL)
      size = Bounds.create(0, 0, 40, 40).rotate(Direction.SOUTH, facing, 0, 0);
    else if (shape == Meter.SHAPE_BOX)
      size = Bounds.create(0, 0, 60, 40).rotate(Direction.SOUTH, facing, 0, 0);
    else // SHAPE_BAR
      size = Bounds.create(0, 0, 20, 60).rotate(Direction.SOUTH, facing, 0, 0);
    return Bounds.create(x, y, size.getWidth(), size.getHeight());
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return UnmodifiableList.create(new Attribute<?>[] {
      ATTR_LABEL, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR, DrawAttr.DYNAMIC_CONDITION});
  }

  @Override
  public void paintDynamic(Graphics g, CircuitState state) {
    AttributeSet attrs = path.leaf().getAttributeSet();
    Color dialColor = attrs.getValue(Io.ATTR_COLOR);

    if (state == null) {
      Meter.paintMeter(g, null, bounds, attrs, true, true, 1, dialColor.darker());
    } else {
      Value data = (Value)getData(state);
      Meter.paintMeter(g, data, bounds, attrs, true, true, 1, dialColor.darker());
    }

    drawLabel(g);
  }

  @Override
  public Element toSvgElement(Document doc) {
    return toSvgElement(doc.createElement("visible-meter"));
  }

  @Override
  public String getDisplayName() {
    return S.get("meterComponent");
  }

  @Override
  public String toString() {
    return "Meter:" + getBounds();
  }
}
