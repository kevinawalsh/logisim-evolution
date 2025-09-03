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
import com.cburch.logisim.circuit.appear.DynamicValueProvider;
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

public class Led extends InstanceFactory implements DynamicElementProvider, DynamicValueProvider {

  public static class Logger extends InstanceLogger {
    @Override
    public String getLogName(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.LABEL);
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return BitWidth.ONE;
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      InstanceDataSingleton data = (InstanceDataSingleton) state
          .getData();
      if (data == null)
        return Value.FALSE;
      return data.getValue() == Value.TRUE ? Value.TRUE : Value.FALSE;
    }
  }

  public Led() {
    super("LED", S.getter("ledComponent"));
    setAttributes(new Attribute[] { StdAttr.FACING, Io.ATTR_ON_COLOR,
      Io.ATTR_OFF_COLOR, Io.ATTR_ACTIVE, StdAttr.LABEL,
      StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] { Direction.WEST, new Color(240, 0, 0),
        Color.LIGHT_GRAY, Boolean.TRUE, "", StdAttr.LABEL_CENTER,
        StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setIconName("led.gif");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    setPorts(new Port[] { new Port(0, 0, Port.INPUT, 1) });
    setInstanceLogger(Logger.class);
  }

  @Override
  public boolean ActiveOnHigh(AttributeSet attrs) {
    return attrs.getValue(Io.ATTR_ACTIVE);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    return Bounds.create(0, -10, 20, 20).rotate(Direction.WEST, facing, 0, 0);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return LightsHDLGenerator.forLed(ctx);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "LED"; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
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
    Value val = data == null ? Value.FALSE : (Value) data.getValue();
    Bounds bds = painter.getNominalBounds().expand(-1);

    Graphics g = painter.getGraphics();
    if (painter.getShowState()) {
      Color onColor = painter.getAttributeValue(Io.ATTR_ON_COLOR);
      Color offColor = painter.getAttributeValue(Io.ATTR_OFF_COLOR);
      Boolean activ = painter.getAttributeValue(Io.ATTR_ACTIVE);
      Object desired = activ.booleanValue() ? Value.TRUE : Value.FALSE;
      g.setColor(val == desired ? onColor : offColor);
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
  public Value getDynamicValue(Instance instance, Object instanceStateData) {
    InstanceDataSingleton data = (InstanceDataSingleton) instanceStateData;
    return data == null ? Value.NIL : (Value) data.getValue();
  }

  @Override
  public void propagate(InstanceState state) {
    Value val = state.getPortValue(0);
    InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
    if (data == null) {
      state.setData(new InstanceDataSingleton(val));
    } else {
      data.setValue(val);
    }
  }

  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new LedShape(x, y, path);
  }
}
