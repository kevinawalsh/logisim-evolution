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

package com.cburch.logisim.std.wiring;
import static com.cburch.logisim.std.Strings.S;

import java.awt.FontMetrics;
import java.awt.Graphics2D;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class BitExtender extends InstanceFactory {
  private static final Attribute<BitWidth> ATTR_IN_WIDTH = Attributes
      .forBitWidth("in_width", S.getter("extenderInAttr"));
  private static final Attribute<BitWidth> ATTR_OUT_WIDTH = Attributes
      .forBitWidth("out_width", S.getter("extenderOutAttr"));
  static final Attribute<AttributeOption> ATTR_TYPE = Attributes.forOption(
      "type",
      S.getter("extenderTypeAttr"),
      new AttributeOption[] {
        new AttributeOption("zero", "zero", S.getter("extenderZeroType")),
        new AttributeOption("one", "one", S.getter("extenderOneType")),
        new AttributeOption("sign", "sign", S.getter("extenderSignType")),
        new AttributeOption("input", "input", S.getter("extenderInputType")), });

  public static final BitExtender FACTORY = new BitExtender();

  public BitExtender() {
    super("Bit Extender", S.getter("extenderComponent"));
    setIconName("extender.gif");
    setAttributes(new Attribute[] { StdAttr.FACING,
      ATTR_IN_WIDTH, ATTR_OUT_WIDTH, ATTR_TYPE },
      new Object[] { Direction.EAST, BitWidth.create(8), BitWidth.create(16),
        ATTR_TYPE.parse("sign") });
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(JoinedConfigurator.create(new BitWidthConfigurator(
            ATTR_OUT_WIDTH), new BitWidthConfigurator(ATTR_IN_WIDTH, 1,
              Value.MAX_WIDTH, 0)));
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    Bounds base = Bounds.create(-40, -20, 40, 40);
    return base.rotate(Direction.EAST, facing, 0, 0);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    instance.addAttributeListener();
  }

  private void configurePorts(Instance instance) {
    Port p0, p1, p2;
    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    boolean isInput = getType(instance.getAttributeSet()).equals("input");
    p0 = new Port(0, 0, Port.OUTPUT, ATTR_OUT_WIDTH);
    if (facing == Direction.WEST) {
      p1 = new Port(40, 0, Port.INPUT, ATTR_IN_WIDTH);
      p2 = isInput ? new Port(20, 20, Port.INPUT, 1) : null;
    } else if (facing == Direction.NORTH) {
      p1 = new Port(0, 40, Port.INPUT, ATTR_IN_WIDTH);
      p2 = isInput ? new Port(-20, 20, Port.INPUT, 1) : null;
    } else if (facing == Direction.SOUTH) {
      p1 = new Port(0,-40, Port.INPUT, ATTR_IN_WIDTH);
      p2 = isInput ? new Port(20, -20, Port.INPUT, 1) : null;
    } else { // EAST
      p1 = new Port(-40, 0, Port.INPUT, ATTR_IN_WIDTH);
      p2 = isInput ? new Port(-20, -20, Port.INPUT, 1) : null;
    }
    if (p2 != null)
      instance.setPorts(new Port[] { p0, p1, p2 });
    else
      instance.setPorts(new Port[] { p0, p1 });
  }

  private String getType(AttributeSet attrs) {
    AttributeOption topt = attrs.getValue(ATTR_TYPE);
    return (String) topt.getValue();
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new BitExtenderHDLGenerator(ctx);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_TYPE) {
      configurePorts(instance);
    } else if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      configurePorts(instance);
    }
    instance.fireInvalidated();
  }

  private void paintBorder(InstancePainter painter) {
    Graphics2D g = (Graphics2D)painter.getGraphics();

    Direction facing = painter.getAttributeValue(StdAttr.FACING);
    int degrees = Direction.EAST.toDegrees() - facing.toDegrees();
    double radians = Math.toRadians((degrees + 360) % 360);

    Location loc = painter.getLocation();
    g.translate(loc.getX(), loc.getY());
    g.rotate(radians);

    int x = -40, y = -20, w = 40, h = 40;

    BitWidth w0 = painter.getAttributeValue(ATTR_OUT_WIDTH);
    BitWidth w1 = painter.getAttributeValue(ATTR_IN_WIDTH);

    GraphicsUtil.switchToWidth(g, 2);
    int xp[], yp[];
    if (w0.getWidth() < w1.getWidth()) {
      // truncate
      xp = new int[] { x, x+w/2-2, x+w/2+2, x+w, x+w,   x };
      yp = new int[] { y,       y,     y+4, y+4, y+h, y+h };
    } else if (w0.getWidth() == w1.getWidth()) {
      // nop
      xp = new int[] { x, x+w/3-2, x+w/3+2, x+2*w/3-2, x+2*w/3+2, x+w, x+w,   x };
      yp = new int[] { y,       y,     y+4,       y+4,         y,   y, y+h, y+h };
    } else {
      // extend
      xp = new int[] {   x, x+w/2-2, x+w/2+2, x+w, x+w,   x };
      yp = new int[] { y+4,     y+4,       y,   y, y+h, y+h };
    }
    g.drawPolygon(xp, yp, xp.length);

    g.rotate(-radians);
    g.translate(-loc.getX(), -loc.getY());
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    paintBorder(painter);
  }
 
  @Override
  public void paintInstance(InstancePainter painter) {

    paintBorder(painter);

    Graphics2D g = (Graphics2D)painter.getGraphics();

    Direction facing = painter.getAttributeValue(StdAttr.FACING);
    int degrees = Direction.EAST.toDegrees() - facing.toDegrees();
    double radians = Math.toRadians((degrees + 360) % 360);

    Location loc = painter.getLocation();
    g.translate(loc.getX(), loc.getY());
    g.rotate(radians);

    FontMetrics fm = g.getFontMetrics();
    int asc = fm.getAscent();

    BitWidth w0 = painter.getAttributeValue(ATTR_OUT_WIDTH);
    BitWidth w1 = painter.getAttributeValue(ATTR_IN_WIDTH);

    String s0;
    String type = getType(painter.getAttributeSet());
    if (w0.getWidth() <= w1.getWidth())
      s0 = ""; // truncate or nop
    else if (type.equals("zero"))
      s0 = S.get("extenderZeroLabel");
    else if (type.equals("one"))
      s0 = S.get("extenderOneLabel");
    else if (type.equals("sign"))
      s0 = S.get("extenderSignLabel");
    else if (type.equals("input"))
      s0 = S.get("extenderInputLabel");
    else
      s0 = "???"; // should never happen
    String s1;
    if (w0.getWidth() < w1.getWidth())
      s1 = S.get("extenderTruncateLabel");
    else if (w0.getWidth() <= w1.getWidth())
      s1 = S.get("extenderNopLabel");
    else 
      s1 = S.get("extenderMainLabel");
    
    int x = -40, y = -20, w = 40, h = 40;

    int cx = x + w/2;
    int cy = y + h/2;
    int y0 = y + (h / 2 + asc - 4) / 2;
    int y1 = y + (3 * h / 2 + asc) / 2;
    GraphicsUtil.drawText(g, s0, cx, y0, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
    GraphicsUtil.drawText(g, s1, cx, y1, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);

    GraphicsUtil.drawText(g, "" + w0.getWidth(), cx+18, cy, GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_OVERALL);
    GraphicsUtil.drawText(g, "" + w1.getWidth(), cx-18, cy, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_OVERALL);
    
    g.rotate(-radians);
    g.translate(-loc.getX(), -loc.getY());

    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Value in = state.getPortValue(1);
    BitWidth wout = state.getAttributeValue(ATTR_OUT_WIDTH);
    String type = getType(state.getAttributeSet());
    Value extend;
    if (type.equals("one")) {
      extend = Value.TRUE;
    } else if (type.equals("sign")) {
      int win = in.getWidth();
      extend = win > 0 ? in.get(win - 1) : Value.ERROR;
    } else if (type.equals("input")) {
      extend = state.getPortValue(2);
      if (extend.getWidth() != 1)
        extend = Value.ERROR;
    } else {
      extend = Value.FALSE;
    }

    Value out = in.extendWidth(wout.getWidth(), extend);
    state.setPort(0, out, 1);
  }

}
