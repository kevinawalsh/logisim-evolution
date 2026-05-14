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

import java.awt.Graphics2D;
import java.awt.Color;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.draw.shapes.DrawAttr;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.UnmodifiableList;

public class LedBarShape extends DynamicElement {

  public LedBarShape(int x, int y, DynamicElement.Path p) {
    super(p, getBounds(x, y, p.leaf().getAttributeSet()));
  }

  static Bounds getBounds(int x, int y, AttributeSet attrs) {
    Bounds o = LedBar.getLedBarOffsetBounds(attrs);
    return Bounds.create(x, y, o.width, o.height);
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return UnmodifiableList.create(new Attribute<?>[] {
      ATTR_LABEL, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR, DrawAttr.DYNAMIC_CONDITION});
  }

  @Override
  public void paintDynamic(Graphics2D g, CircuitState state) {
    AttributeSet attrs = path.leaf().getAttributeSet();

    if (state == null) {
      LedBar.paintLedBar(g, null, bounds, attrs, true, true, 1);
    } else {
      Value data = (Value)getData(state);
      LedBar.paintLedBar(g, data, bounds, attrs, true, true, 1);
    }

    drawLabel(g);
  }

  @Override
  public Element toSvgElement(Document doc) {
    return toSvgElement(doc.createElement("visible-ledbar"));
  }

  @Override
  public String getDisplayName() {
    return S.get("ledBarComponent");
  }

  @Override
  public String toString() {
    return "LedBar:" + getBounds();
  }
}
