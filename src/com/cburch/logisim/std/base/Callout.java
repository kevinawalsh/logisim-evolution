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

package com.cburch.logisim.std.base;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Collections;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Reshapable;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.util.GraphicsUtil;

public class Callout extends Text implements Reshapable {

  public static final Attribute<Integer> ATTR_DX =
      Attributes.forInteger("dx", S.getter("calloutDeltaX"));
  public static final Attribute<Integer> ATTR_DY =
      Attributes.forInteger("dy", S.getter("calloutDeltaY"));

  public static final Callout FACTORY = new Callout();

  private Callout() {
    super("Callout", S.getter("calloutComponent"));
    setIconName("callout.png");
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new CalloutAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) { // nominal
    CalloutAttributes attrs = (CalloutAttributes) attrsBase;
    return super.getOffsetBounds(attrsBase).add(attrs.getDx(), attrs.getDy());
  }

  @Override
  public Bounds getVisibleOffsetBounds(AttributeSet attrsBase, Graphics g) { // visible
    CalloutAttributes attrs = (CalloutAttributes) attrsBase;
    return super.getVisibleOffsetBounds(attrsBase, g).add(attrs.getDx(), attrs.getDy());
  }

  @Override
  public void paint(InstancePainter painter, boolean border) {
    CalloutAttributes attrs = (CalloutAttributes) painter.getAttributeSet();
    int dx = attrs.getDx();
    int dy = attrs.getDy();
    Location loc = painter.getLocation();
    Location focus = loc.translate(dx, dy);
    paint(painter, border, focus);
  }

  @Override
  public void drawReshaping(InstancePainter painter, Location handle, int rdx, int rdy) {
    CalloutAttributes attrs = (CalloutAttributes) painter.getAttributeSet();
    int dx = attrs.getDx();
    int dy = attrs.getDy();
    Location loc = painter.getLocation();
    Location focus = loc.translate(dx + rdx, dy + rdy);
    paint(painter, false, focus);
  }

  void paint(InstancePainter painter, boolean border, Location focus) {
    super.paint(painter, border);
    CalloutAttributes attrs = (CalloutAttributes) painter.getAttributeSet();
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    Graphics g = painter.getGraphics();
    Location loc = painter.getLocation();
    Bounds tbds = getTextOnlyVisibleBounds(loc, attrs, g);

    g.setColor(attrs.getFGColor());
   
    // Allowed pivot positions, 1 to 8:
    //      1---------2---------3  V_TOP
    //      |         |         |
    //      |         |         |
    //      8---------o---------4  V_CENTER
    //      |         |         |
    //      o---------X---------o  V_BASELINE
    //      |         |         |
    //      7---------6---------5  V_BOTTOM
    //   H_LEFT   H_CENTER   H_RIGHT

    int wid = tbds.getWidth();
    int hgt = tbds.getHeight();
    int left = tbds.getX();
    int right = left + wid;
    int top = tbds.getY();
    int bot = top + hgt;
    Location p1 = Location.create(left, top);
    Location p2 = Location.create(left+wid/2, top);
    Location p3 = Location.create(right, top);
    Location p4 = Location.create(right, top+hgt/2);
    Location p5 = Location.create(right, bot);
    Location p6 = Location.create(left+wid/2, bot);
    Location p7 = Location.create(left, bot);
    Location p8 = Location.create(left, top+hgt/2);
    Location p = p1;
    if (valign == GraphicsUtil.V_TOP && halign == GraphicsUtil.H_LEFT) p = p1;
    else if (valign == GraphicsUtil.V_TOP && halign == GraphicsUtil.H_CENTER) p = p2;
    else if (valign == GraphicsUtil.V_TOP && halign == GraphicsUtil.H_RIGHT) p = p3;
    else if (valign == GraphicsUtil.V_CENTER && halign == GraphicsUtil.H_RIGHT) p = p4;
    else if (valign == GraphicsUtil.V_BOTTOM && halign == GraphicsUtil.H_RIGHT) p = p5;
    else if (valign == GraphicsUtil.V_BOTTOM && halign == GraphicsUtil.H_CENTER) p = p6;
    else if (valign == GraphicsUtil.V_BOTTOM && halign == GraphicsUtil.H_LEFT) p = p7;
    else if (valign == GraphicsUtil.V_CENTER && halign == GraphicsUtil.H_LEFT) p = p8;
    else if (valign == GraphicsUtil.V_BASELINE && halign == GraphicsUtil.H_LEFT) {
      // left bar, auto pivot
      if (focus.getX() < left && top - focus.getY() < left - focus.getX() && focus.getY() - bot < left - focus.getX())
        p = p8;
      else if (focus.getY() <= top + hgt/2)
        p = p1;
      else
        p = p7;
    } else if (valign == GraphicsUtil.V_BASELINE && halign == GraphicsUtil.H_RIGHT) {
      // right bar, auto pivot
      if (focus.getX() > right && top - focus.getY() < focus.getX() - right && focus.getY() - bot < focus.getX() - right)
        p = p4;
      else if (focus.getY() <= top + hgt/2)
        p = p3;
      else
        p = p5;
    } else {
      // auto bar, auto pivot
      if (focus.getX() < left && top - focus.getY() < left - focus.getX() && focus.getY() - bot < left - focus.getX())
        p = p8;
      else if (focus.getX() > right && top - focus.getY() < focus.getX() - right && focus.getY() - bot < focus.getX() - right)
        p = p4;
      else if (focus.getY() <= top + hgt/2)
        p = p2;
      else 
        p = p6;
    }

    g.drawLine(p.getX(), p.getY(), focus.getX(), focus.getY());
    if (valign == GraphicsUtil.V_TOP || p == p2) g.drawLine(left, top, right, top);
    else if (valign == GraphicsUtil.V_BOTTOM || p == p6) g.drawLine(left, bot, right, bot);
    else if (halign == GraphicsUtil.H_RIGHT || p == p4) g.drawLine(right, top, right, bot);
    else g.drawLine(left, top, left, bot);
  }

  @Override
  public Collection<Location> getReshapeHandles(Component comp) {
    CalloutAttributes attrs = (CalloutAttributes)comp.getAttributeSet();
    Location focus = comp.getLocation().translate(attrs.getDx(), attrs.getDy());
    return Collections.singletonList(focus);
  }

  @Override
  public void doReshapeAction(Project proj, Circuit circ, Component comp,
      Location handle, int rdx, int rdy) {
    CalloutAttributes attrs = (CalloutAttributes)comp.getAttributeSet();
    SetAttributeAction act = new SetAttributeAction(circ, S.getter("calloutReshape"));
    act.set(comp, ATTR_DX, attrs.getDx() + rdx);
    act.set(comp, ATTR_DY, attrs.getDy() + rdy);
    proj.doAction(act);
  }

  @Override
  public Object getInstanceFeature(Instance instance, Object key) {
    if (key == Reshapable.class)
      return this;
    else
      return super.getInstanceFeature(instance, key);
  }
}
