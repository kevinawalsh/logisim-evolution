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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.util.GraphicsUtil;

public class RGBLedShape extends LedShape {
  public RGBLedShape(int x, int y, DynamicElement.Path p) {
    super(x, y, p);
  }

  @Override
  public void paintDynamic(Graphics g, CircuitState state) {
    int x = bounds.getX() + 1;
    int y = bounds.getY() + 1;
    int w = bounds.getWidth() - 2;
    int h = bounds.getHeight() - 2;
    GraphicsUtil.switchToWidth(g, strokeWidth);
    if (state == null) {
      g.setColor(Color.lightGray);
      g.fillOval(x, y, w, h);
      g.setColor(DynamicElement.COLOR);
      g.drawOval(x, y, w, h);
    } else {
      Boolean activ = path.leaf().getAttributeSet().getValue(Io.ATTR_ACTIVE);
      Object desired = activ.booleanValue() ? Value.TRUE : Value.FALSE;
      Integer data = (Integer)getData(state);
      int summ = (data == null ? 0 : data.intValue());
      int mask = activ.booleanValue() ? 0 : 7;
      summ ^= mask;
      int red = ((summ >> RGBLed.RED) & 1) * 0xFF;
      int green = ((summ >> RGBLed.GREEN) & 1) * 0xFF;
      int blue = ((summ >> RGBLed.BLUE) & 1) * 0xFF;
      g.setColor(new Color(red, green, blue));
      g.fillOval(x, y, w, h);
      g.setColor(Color.darkGray);
      g.drawOval(x, y, w, h);
    }
    drawLabel(g);
  }

  @Override
  public Element toSvgElement(Document doc) {
    return toSvgElement(doc.createElement("visible-rgbled"));
  }

  @Override
  public String getDisplayName() {
    return S.get("RGBledComponent");
  }

  @Override
  public String toString() {
    return "RGBLed:" + getBounds();
  }
}
