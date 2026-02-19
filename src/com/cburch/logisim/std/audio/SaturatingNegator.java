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

package com.cburch.logisim.std.audio;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class SaturatingNegator extends InstanceFactory {

  public SaturatingNegator() {
    super("SaturatingNegator", S.getter("audioSaturatingNegatorComponent"));
    setAttributes(
        new Attribute[] { StdAttr.WIDTH, StdAttr.MODE },
          new Object[] { BitWidth.create(8), StdAttr.UNSIGNED_OPTION });
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setOffsetBounds(Bounds.create(-30, -15, 30, 30));
    setIconName("saturatingnegator.png");
    setPorts(new Port[] {
      new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH),
      new Port(-30, 0, Port.INPUT, StdAttr.WIDTH)
    });
  }

  protected void paintDecoration(Graphics2D g, double cx, double cy) {
    g.setColor(Color.GRAY);
    g.fill(new Ellipse2D.Double(cx-12, cy-12, 24, 24));
    g.setColor(Color.WHITE);
    GraphicsUtil.switchToWidth(g, 2);
    g.draw(new Line2D.Double(cx-8, cy, cx-3, cy));
    g.draw(new Line2D.Double(cx+2, cy-8, cx+2, cy+8));
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    painter.drawBounds();
    Bounds bds = painter.getNominalBounds();
    double cx = bds.x + bds.width/2.0;
    double cy = bds.y + bds.height/2.0;
    
    paintDecoration(g, cx, cy);

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    int w = state.getAttributeValue(StdAttr.WIDTH).getWidth();
    boolean signed = state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;

    long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
    long min = signed ? -((1L << (w-1))) : 0L;
    long x;
    Value v = state.getPortValue(1);
    if (v.isFullyDefined()) {
      x = v.extendAsLong(signed);
      x = -x;
    } else if (v.isErrorValue()) {
      state.setPort(0, Value.createError(BitWidth.create(w)), 1);
      return;
    } else {
      x = signed ? 0 : (max/2);
    }
    x = Math.max(min, Math.min(max, x));
    state.setPort(0, Value.createKnown(BitWidth.create(w), (int)x), 1);
  }

}
