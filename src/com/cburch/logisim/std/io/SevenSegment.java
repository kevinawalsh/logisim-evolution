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

import java.awt.Color;
import java.awt.Graphics;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceDataSingleton;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;

public class SevenSegment extends InstanceFactory implements DynamicElementProvider {
  static void drawBase(InstancePainter painter, boolean DrawPoint) {
    ensureSegments();
    InstanceDataSingleton data = (InstanceDataSingleton) painter.getData();
    int summ = (data == null ? 0 : ((Integer) data.getValue()).intValue());
    Boolean active = painter.getAttributeValue(Io.ATTR_ACTIVE);
    int desired = active == null || active.booleanValue() ? 1 : 0;

    Bounds bds = painter.getNominalBounds();
    int x = bds.getX() + 5;
    int y = bds.getY();

    Graphics g = painter.getGraphics();
    Color onColor = painter.getAttributeValue(Io.ATTR_ON_COLOR);
    Color offColor = painter.getAttributeValue(Io.ATTR_OFF_COLOR);
    Color bgColor = painter.getAttributeValue(Io.ATTR_BACKGROUND);
    if (painter.shouldDrawColor() && bgColor.getAlpha() != 0) {
      g.setColor(bgColor);
      g.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
      g.setColor(Color.BLACK);
    }
    painter.drawBounds();
    g.setColor(Color.DARK_GRAY);
    for (int i = 0; i <= 7; i++) {
      if (painter.getShowState()) {
        g.setColor(((summ >> i) & 1) == desired ? onColor : offColor);
      }
      if (i < 7) {
        Bounds seg = SEGMENTS[i];
        g.fillRect(x + seg.getX(), y + seg.getY(), seg.getWidth(),
            seg.getHeight());
      } else {
        if (DrawPoint)
          g.fillOval(x + 28, y + 48, 5, 5); // draw decimal point
      }
    }
    g.setColor(Color.BLACK);
    painter.drawLabel();
    painter.drawPorts();
  }

  static void ensureSegments() {
    if (SEGMENTS == null) {
      SEGMENTS = new Bounds[] { Bounds.create(3, 8, 19, 4),
        Bounds.create(23, 10, 4, 19), Bounds.create(23, 30, 4, 19),
        Bounds.create(3, 47, 19, 4), Bounds.create(-2, 30, 4, 19),
        Bounds.create(-2, 10, 4, 19), Bounds.create(3, 28, 19, 4) };
    }
  }

  public static final int Segment_A = 0;
  public static final int Segment_B = 1;
  public static final int Segment_C = 2;
  public static final int Segment_D = 3;
  public static final int Segment_E = 4;
  public static final int Segment_F = 5;
  public static final int Segment_G = 6;

  public static final int DP = 7;

  public static String[] pinLabels() {
    return new String[] {
    "Segment_A", "Segment_B",
    "Segment_C", "Segment_D",
    "Segment_E", "Segment_F",
    "Segment_G", "Segment_DP" };
  }

  static Bounds[] SEGMENTS = null;

  static Color DEFAULT_OFF = new Color(220, 220, 220);

  public SevenSegment() {
    super("7-Segment Display", S.getter("sevenSegmentComponent"));
    setAttributes(new Attribute[] { Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
      Io.ATTR_BACKGROUND, Io.ATTR_ACTIVE, StdAttr.LABEL,
      StdAttr.LABEL_LOC, StdAttr.LABEL_FONT }, new Object[] {
        new Color(240, 0, 0), DEFAULT_OFF, Io.DEFAULT_BACKGROUND,
        Boolean.TRUE, "", Direction.EAST, StdAttr.DEFAULT_LABEL_FONT });
    setOffsetBounds(Bounds.create(-5, 0, 40, 60));
    setIconName("7seg.gif");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    Port[] ps = new Port[8];
    ps[Segment_A] = new Port(20, 0, Port.INPUT, 1);
    ps[Segment_B] = new Port(30, 0, Port.INPUT, 1);
    ps[Segment_C] = new Port(20, 60, Port.INPUT, 1);
    ps[Segment_D] = new Port(10, 60, Port.INPUT, 1);
    ps[Segment_E] = new Port(0, 60, Port.INPUT, 1);
    ps[Segment_F] = new Port(10, 0, Port.INPUT, 1);
    ps[Segment_G] = new Port(0, 0, Port.INPUT, 1);
    ps[DP] = new Port(30, 60, Port.INPUT, 1);
    ps[Segment_A].setToolTip(S.getter("Segment_A"));
    ps[Segment_B].setToolTip(S.getter("Segment_B"));
    ps[Segment_C].setToolTip(S.getter("Segment_C"));
    ps[Segment_D].setToolTip(S.getter("Segment_D"));
    ps[Segment_E].setToolTip(S.getter("Segment_E"));
    ps[Segment_F].setToolTip(S.getter("Segment_F"));
    ps[Segment_G].setToolTip(S.getter("Segment_G"));
    ps[DP].setToolTip(S.getter("DecimalPoint"));
    setPorts(ps);
  }

  @Override
  public boolean ActiveOnHigh(AttributeSet attrs) {
    return attrs.getValue(Io.ATTR_ACTIVE);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.computeLabelTextField(0);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return LightsHDLGenerator.forSevenSegment(ctx);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "SevenSegment"; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(0);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    drawBase(painter, true);
  }

  @Override
  public void propagate(InstanceState state) {
    int summary = 0;
    for (int i = 0; i < 8; i++) {
      Value val = state.getPortValue(i);
      if (val == Value.TRUE)
        summary |= 1 << i;
    }
    Integer value = Integer.valueOf(summary);
    InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
    if (data == null) {
      state.setData(new InstanceDataSingleton(value));
    } else {
      data.setValue(value);
    }
  }

  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new SevenSegmentShape(x, y, path);
  }
}
