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
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class SaturatingAdder extends InstanceFactory {

  static final Attribute<Integer> ATTR_INPUTS =
      Attributes.forIntegerRange("inputs", S.getter("gateInputsAttr"), 2, 5);

  static final AttributeOption NORM_CAP = FitRange.NORM_CAP;
  static final AttributeOption NORM_FIT = FitRange.NORM_FIT;
  static final AttributeOption NORM_CENTER = FitRange.NORM_CENTER;
  static final Attribute<AttributeOption> ATTR_NORM = FitRange.ATTR_NORM;

  protected SaturatingAdder(String name, String localized) {
    super(name, S.getter(localized));
    setAttributes(
        new Attribute[] {
          StdAttr.WIDTH, ATTR_INPUTS, StdAttr.MODE, ATTR_NORM },
          new Object[] {
            BitWidth.create(8), Integer.valueOf(2), StdAttr.UNSIGNED_OPTION, NORM_CAP });
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
  }

  public SaturatingAdder() {
    this("SaturatingAdder", "audioSaturatingAdderComponent");
    setIconName("saturatingadder.png");
  }
  
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.WIDTH) {
      updatePorts(instance);
    } else if (attr == ATTR_INPUTS) {
      instance.recomputeBounds();
      updatePorts(instance);
    }
    instance.fireInvalidated(); // recompute using new mode, etc.
  }

  private static final int[][] port_y = {
    { -10, 10 },                // 2 inputs
    { -10, 0, 10 },             // 3 inputs
    { -20, -10, 10, 20 },       // 4 inputs
    { -20, -10, 0, 10, 20 },    // 5 inputs
  };

  private void updatePorts(Instance instance) {
    int n = instance.getAttributeValue(ATTR_INPUTS);
    Port[] ps = new Port[1+n];
    ps[0] = new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH);
    for (int i = 0; i < n; i++)
      ps[1+i] = new Port(-30, port_y[n-2][i], Port.INPUT, StdAttr.WIDTH);
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int n = attrs.getValue(ATTR_INPUTS);
    if (n <= 3)
      return Bounds.create(-30, -15, 30, 30);
    else
      return Bounds.create(-30, -25, 30, 50);
  }
  
  protected void paintDecoration(Graphics2D g, double cx, double cy) {
    g.setColor(Color.GRAY);
    g.fill(new Ellipse2D.Double(cx-12, cy-12, 24, 24));
    g.setColor(Color.WHITE);
    GraphicsUtil.switchToWidth(g, 2);
    g.draw(new Line2D.Double(cx, cy-8, cx, cy+8));
    g.draw(new Line2D.Double(cx-8, cy, cx+8, cy));
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = (Graphics2D)painter.getGraphics();
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
    int n = state.getAttributeValue(ATTR_INPUTS);
    boolean signed = state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    AttributeOption norm = state.getAttributeValue(ATTR_NORM);

    long signbit = 1L << (w-1);
    long moresigns = signed ? (-1L << w) : 0;
    long sum = 0;
    int m = 0;
    for (int i = 0; i < n; i++) {
      Value v = state.getPortValue(1+i);
      if (v.isFullyDefined()) {
        long x = v.toIntValue();
        if ((x & signbit) != 0)
          x |= moresigns;
        sum += x;
        m++;
      }
    }
    long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
    long min = signed ? -((1L << (w-1))) : 0L;
    if (norm == NORM_FIT) {
      // Signed:
      //   Inputs in [-A, +B], sum in [-A*m, +B*m], so scale by 1/m.
      // Unsigned:
      //   Inputs in [0, +B], sum in [0, +B*m], so scale by 1/m.
      if (m > 1)
        sum /= m;
    } else if (norm == NORM_CENTER) {
      // Signed:
      //   Inputs centered on 0, sum centered on 0, so no offset.
      // Unsigned:
      //   Inputs centered on B/2, sum centered m*B/2, so offset by -(m-1)*B/2.
      //   Note: this offset works fine even in the m=1 and m=0 cases. Having
      //   the output be centered at B/2 when there are no inputs is reasonable.
      if (!signed)
        sum = sum - (m-1)*max/2;
    }
    sum = Math.max(min, Math.min(max, sum));
    state.setPort(0, Value.createKnown(BitWidth.create(w), (int)sum), 1);
  }

}
