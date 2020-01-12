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

package com.cburch.logisim.circuit;
import static com.cburch.logisim.circuit.Strings.S;

import java.awt.Color;
import java.awt.Graphics;

import com.cburch.logisim.comp.AbstractComponentFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;

public class WireFactory extends AbstractComponentFactory {
  public static final WireFactory instance = new WireFactory();

  private WireFactory() {
  }

  @Override
  public AttributeSet createAttributeSet() {
    return Wire.create(Location.create(0, 0), Location.create(100, 0));
  }

  @Override
  public Component createComponent(Location loc, AttributeSet attrs) {
    Object dir = attrs.getValue(Wire.dir_attr);
    int len = attrs.getValue(Wire.len_attr).intValue();

    if (dir == Wire.VALUE_HORZ) {
      return Wire.create(loc, loc.translate(len, 0));
    } else {
      return Wire.create(loc, loc.translate(0, len));
    }
  }

  @Override
  public void drawGhost(ComponentDrawContext context, Color color, int x,
      int y, AttributeSet attrs) {
    Graphics g = context.getGraphics();
    Object dir = attrs.getValue(Wire.dir_attr);
    int len = attrs.getValue(Wire.len_attr).intValue();

    g.setColor(color);
    GraphicsUtil.switchToWidth(g, 3);
    if (dir == Wire.VALUE_HORZ)
      g.drawLine(x, y, x + len, y);
    else
      g.drawLine(x, y, x, y + len);
  }

  public void drawPartialGhost(ComponentDrawContext context, Color color, int x,
      int y, AttributeSet attrs, Bounds bds) {
    Graphics g = context.getGraphics();
    Object dir = attrs.getValue(Wire.dir_attr);
    int len = attrs.getValue(Wire.len_attr).intValue();

    int x0 = x, y0 = y, x1 = x, y1 = y;
    if (dir == Wire.VALUE_HORZ)
      x1 += len;
    else
      y1 += len;
    int nx0 = Math.max(x0, bds.x);
    int ny0 = Math.max(y0, bds.y);
    int nx1 = Math.min(x1, bds.x+bds.width);
    int ny1 = Math.min(y1, bds.y+bds.height);
    if (nx0 == nx1 && ny0 == ny1)
      return; // partial portion is empty, no partial ghost
    g.setColor(color);
    GraphicsUtil.switchToWidth(g, 3);
    g.drawLine(nx0, ny0, nx1, ny1);
    GraphicsUtil.switchToWidth(g, 1);
    if (nx0 != x0 || ny0 != y0)
      context.drawHandle(nx0, ny0);
    if (nx1 != x1 || ny1 != y1)
      context.drawHandle(nx1, ny1);
  }

  public static Wire[] splitWire(Wire w, Bounds bds) {
    Location p0 = w.getEnd0();
    Location p1 = w.getEnd1();
    int nx0 = Math.max(p0.x, bds.x);
    int ny0 = Math.max(p0.y, bds.y);
    int nx1 = Math.min(p1.x, bds.x+bds.width);
    int ny1 = Math.min(p1.y, bds.y+bds.height);
    Location n0 = Location.create(nx0, ny0);
    Location n1 = Location.create(nx1, ny1);
    if (n0.equals(n1))
      return null; // partial portion is empty, no splitting
    // if (p0.equals(n0) && p1.equals(n1))
    //   return null; // both ends contained, no splitting
    // one or both ends are not contained,
    // split into up to three pieces
    // p0    n0    n1     p1
    // o------x-----x------o
    Wire[] splits = new Wire[3];
    splits[0] = p0.equals(n0)?null:Wire.create(p0, n0);
    splits[1] = Wire.create(n0, n1);
    splits[2] = n1.equals(p1)?null:Wire.create(n1, p1);
    return splits;
  }

  @Override
  public StringGetter getDisplayGetter() {
    return S.getter("wireComponent");
  }

  @Override
  public String getName() {
    return "Wire";
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Object dir = attrs.getValue(Wire.dir_attr);
    int len = attrs.getValue(Wire.len_attr).intValue();

    if (dir == Wire.VALUE_HORZ) {
      return Bounds.create(0, -2, len, 5);
    } else {
      return Bounds.create(-2, 0, 5, len);
    }
  }
}
