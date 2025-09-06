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
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceDataSingleton;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class RGBLed extends InstanceFactory implements DynamicElementProvider {

  public static class Logger extends InstanceLogger {
    static final BitWidth bitwidth = BitWidth.create(3);
    @Override
    public String getLogName(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.LABEL);
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return bitwidth;
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
      int rgb = 0;
      if (data == null)
        return Value.createUnknown(bitwidth);
      else
        return Value.createKnown(bitwidth, ((Integer)data.getValue()).intValue());
    }
  }

  public static final int RED = 0;
  public static final int GREEN = 1;
  public static final int BLUE = 2;

  public static String[] pinLabels() {
    return new String[] { "RED", "GREEN", "BLUE" };
  }

  public RGBLed() {
    super("RGBLED", S.getter("RGBledComponent"));
    setAttributes(new Attribute[] { StdAttr.FACING, Io.ATTR_ACTIVE, StdAttr.LABEL,
      StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] { Direction.WEST, Boolean.TRUE, "", StdAttr.LABEL_CENTER,
        StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setIconName("rgbled.gif");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    setInstanceLogger(Logger.class);
  }

  private void updatePorts(Instance instance) {
    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    Port[] ps = new Port[3];
    int cx = 0, cy = 0, dx = 0, dy = 0;
    if (facing == Direction.NORTH) {
      cy = 10; dx = 10;
    } else if (facing == Direction.EAST) {
      cx = -10; dy = 10;
    } else if (facing == Direction.SOUTH) {
      cy = -10; dx = -10;
    } else {
      cx = 10; dy = -10;
    }
    ps[RED] = new Port(0, 0, Port.INPUT, 1);
    ps[GREEN] = new Port(cx+dx, cy+dy, Port.INPUT, 1);
    ps[BLUE] = new Port(cx-dx, cy-dy, Port.INPUT, 1);
    ps[RED].setToolTip(S.getter("RED"));
    ps[GREEN].setToolTip(S.getter("GREEN"));
    ps[BLUE].setToolTip(S.getter("BLUE"));
    instance.setPorts(ps);
  }

  @Override
  public boolean ActiveOnHigh(AttributeSet attrs) {
    return attrs.getValue(Io.ATTR_ACTIVE);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    return Bounds.create(0, -10, 20, 20).rotate(Direction.WEST, facing, 0, 0);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return LightsHDLGenerator.forRGBLed(ctx);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "RGBLED"; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      updatePorts(instance);
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawOval(bds.getX() + 1, bds.getY() + 1, bds.getWidth() - 2,
        bds.getHeight() - 2);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    InstanceDataSingleton data = (InstanceDataSingleton) painter.getData();
    int summ = (data == null ? 0 : ((Integer) data.getValue()).intValue());
    Bounds bds = painter.getNominalBounds().expand(-1);

    Graphics g = painter.getGraphics();
    if (painter.getShowState()) {
      Boolean activ = painter.getAttributeValue(Io.ATTR_ACTIVE);
      int mask = activ.booleanValue() ? 0 : 7;
      summ ^= mask;
      int red = ((summ >> RED) & 1) * 0xFF;
      int green = ((summ >> GREEN) & 1) * 0xFF;
      int blue = ((summ >> BLUE) & 1) * 0xFF;
      Color LedColor = new Color(red, green, blue);
      g.setColor(LedColor);
      g.fillOval(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    }
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawOval(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    int summary = 0;
    for (int i = 0; i < 3; i++) {
      Value val = state.getPortValue(i);
      if (val == Value.TRUE)
        summary |= 1 << i;
    }
    Object value = Integer.valueOf(summary);
    InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
    if (data == null) {
      state.setData(new InstanceDataSingleton(value));
    } else {
      data.setValue(value);
    }
  }

  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new RGBLedShape(x, y, path);
  }
}
