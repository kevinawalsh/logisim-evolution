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

import java.awt.Font;
import java.awt.Color;
import java.awt.Graphics;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.circuit.appear.DynamicValueProvider;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Probe extends InstanceFactory implements DynamicValueProvider {
  public static class ProbeLogger extends InstanceLogger {
    public ProbeLogger() {
    }

    @Override
    public String getLogName(InstanceState state, Object option) {
      String ret = state.getAttributeValue(StdAttr.LABEL);
      return ret != null && !ret.equals("") ? ret : null;
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      StateData data = (StateData) state.getData();
      return data == null || data.curValue == null ? null : data.curValue.getBitWidth();
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      return getValue(state);
    }
  }

  private static class StateData implements InstanceData, Cloneable {
    Value curValue = Value.NIL;

    @Override
    public Object clone() {
      try {
        return super.clone();
      } catch (CloneNotSupportedException e) {
        return null;
      }
    }
  }

  /* note: also used by Clock and Pin, for convenience ?!?! */
  static Bounds getOffsetBounds(Direction dir, BitWidth width, RadixOption radix) {
    if (radix == null)
      radix = RadixOption.RADIX_2;
    int len = (radix == RadixOption.RADIX_2) ? width.getWidth() : radix.getMaxLength(width);
    int w = 90, h = 20;
    if (len <= 1)
      w = 20;
    else if (len <= 8 && radix == RadixOption.RADIX_2)
      w = 10 + 10*len;
    else if (radix != RadixOption.RADIX_2)
      w = 8 + 7*len;
    else
      h = 4 + 14*((len+7)/8);
    if (dir == Direction.EAST)
      return Bounds.create(-w, -h/2, w, h);
    else if (dir == Direction.WEST)
      return Bounds.create(0, -h/2, w, h);
    else if (dir == Direction.SOUTH)
      return Bounds.create(-w/2, -h, w, h);
    else if (dir == Direction.NORTH)
      return Bounds.create(-w/2, 0, w, h);
    else
      return Bounds.create(0, -10, 20, 20);
  }

  private static Value getValue(InstanceState state) {
    StateData data = (StateData) state.getData();
    return data == null ? Value.NIL : data.curValue;
  }

  @Override
  public Value getDynamicValue(Instance insance, Object instanceStateData) {
    StateData data = (StateData) instanceStateData;
    return data == null ? Value.NIL : data.curValue;
  }

  static void paintValue(InstancePainter painter, Value value) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds(); // label not included
    g.setFont(DEFAULT_FONT);

    RadixOption radix = painter.getAttributeValue(RadixOption.ATTRIBUTE);
    if (radix == null || radix == RadixOption.RADIX_2) {
      int x = bds.getX();
      int y = bds.getY();
      int wid = value.getWidth();
      if (wid == 0) {
        x += bds.getWidth() / 2;
        y += bds.getHeight() / 2;
        GraphicsUtil.switchToWidth(g, 2);
        g.drawLine(x - 4, y, x + 4, y);
        return;
      }
      int x0 = bds.getX() + bds.getWidth() - 5;
      int lineWidth = (wid < 8 ? wid : 8) * 10;
      if (lineWidth < bds.getWidth() - 3) {
        x0 = bds.getX() + (bds.getWidth() + lineWidth) / 2 - 5;
      }
      int cx = x0;
      int cy = bds.getY() + bds.getHeight() - 10;
      int cur = 0;
      for (int k = 0; k < wid; k++) {
        GraphicsUtil.drawCenteredText(g,
            value.get(k).toDisplayString(), cx, cy);
        ++cur;
        if (cur == 8) {
          cur = 0;
          cx = x0;
          cy -= 14;
        } else {
          cx -= 10;
        }
      }
    } else {
      String text = radix.toString(value);
      GraphicsUtil.drawCenteredText(g, text, bds.getX() + bds.getWidth()
          / 2, bds.getY() + bds.getHeight() / 2 - 2);
    }
  }

  private static final Font DEFAULT_FONT = new Font("monospaced", Font.PLAIN, 12);

  public static final Probe FACTORY = new Probe();

  public Probe() {
    super("Probe", S.getter("probeComponent"));
    setIconName("probe.gif");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    setFacingAttribute(StdAttr.FACING);
    setInstanceLogger(ProbeLogger.class);
  }

  //
  // methods for instances
  //
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.setPorts(new Port[] { new Port(0, 0, Port.INPUT, BitWidth.UNKNOWN) });
    instance.addAttributeListener();
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new ProbeAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) {
    ProbeAttributes attrs = (ProbeAttributes) attrsBase;
    return getOffsetBounds(attrs.facing, attrs.width, attrs.radix);
  }
  
  @Override
  public boolean HDLIgnore() { return true; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == StdAttr.FACING || attr == RadixOption.ATTRIBUTE) {
      instance.recomputeBounds();
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    }
  }

  //
  // graphics methods
  //
  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalOffsetBounds();
    g.drawOval(bds.getX() + 1, bds.getY() + 1, bds.getWidth() - 1,
        bds.getHeight() - 1);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Value value = getValue(painter);

    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds(); // label not included
    int x = bds.getX();
    int y = bds.getY();
    Color back = new Color(0xff, 0xf0, 0x99);
    if (value.getWidth() <= 1) {
      g.setColor(back);
      g.fillOval(x + 1, y + 1, bds.getWidth() - 2, bds.getHeight() - 2);
      g.setColor(Color.lightGray);
      g.drawOval(x + 1, y + 1, bds.getWidth() - 2, bds.getHeight() - 2);
    } else {
      g.setColor(back);
      g.fillRoundRect(x + 1, y + 1, bds.getWidth() - 2,
          bds.getHeight() - 2, 20, 20);
      g.setColor(Color.lightGray);
      g.drawRoundRect(x + 1, y + 1, bds.getWidth() - 2,
          bds.getHeight() - 2, 20, 20);
    }

    g.setColor(Color.GRAY);
    painter.drawLabel();
    g.setColor(Color.DARK_GRAY);

    if (!painter.getShowState()) {
      if (value.getWidth() > 0) {
        GraphicsUtil.drawCenteredText(g, "x" + value.getWidth(),
            bds.getX() + bds.getWidth() / 2,
            bds.getY() + bds.getHeight() / 2);
      }
    } else {
      paintValue(painter, value);
    }

    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    StateData oldData = (StateData) state.getData();
    Value oldValue = oldData == null ? Value.NIL : oldData.curValue;
    Value newValue = state.getPortValue(0);
    boolean same = oldValue == null ? newValue == null : oldValue
        .equals(newValue);
    if (!same) {
      if (oldData == null) {
        oldData = new StateData();
        oldData.curValue = newValue;
        state.setData(oldData);
      } else {
        oldData.curValue = newValue;
      }
      int oldWidth = oldValue == null ? 1 : oldValue.getBitWidth().getWidth();
      int newWidth = newValue.getBitWidth().getWidth();
      if (oldWidth != newWidth) {
        ProbeAttributes attrs = (ProbeAttributes) state.getAttributeSet();
        attrs.width = newValue.getBitWidth();
        state.getInstance().recomputeBounds();
        state.getInstance().computeLabelTextField(Instance.AVOID_LEFT);
      }
    }
  }
}
