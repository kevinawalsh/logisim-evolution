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
import java.util.Arrays;
import java.util.List;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.analyze.model.Expressions;
import com.cburch.logisim.circuit.ExpressionComputer;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
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
import com.cburch.logisim.util.StringGetter;

public class Constant extends InstanceFactory {
  private static class ConstantAttributes extends AbstractAttributeSet {
    private Direction facing = Direction.EAST;;
    private BitWidth width = BitWidth.ONE;
    private Value value = Value.TRUE;
    private RadixOption radix = RadixOption.RADIX_16;

    @Override
    protected void copyInto(AbstractAttributeSet destObj) {
      ConstantAttributes dest = (ConstantAttributes) destObj;
      dest.facing = this.facing;
      dest.width = this.width;
      dest.value = this.value;
      dest.radix = this.radix;
    }

    @Override
    public List<Attribute<?>> getAttributes() {
      return ATTRIBUTES;
    }

    @Override
    public <V> V getValue(Attribute<V> attr) {
      if (attr == StdAttr.FACING)
        return (V) facing;
      if (attr == StdAttr.WIDTH)
        return (V) width;
      if (attr == ATTR_VALUE)
        return (V) new ValueWithRadix(value, radix);
      if (attr == RadixOption.ATTRIBUTE)
        return (V) radix;
      return null;
    }

    @Override
    public <V> void updateAttr(Attribute<V> attr, V value) {
      if (attr == StdAttr.FACING) {
        facing = (Direction) value;
      } else if (attr == StdAttr.WIDTH) {
        width = (BitWidth) value;
        this.value = this.value.extendWidth(width.getWidth(),
            this.value.get(this.value.getWidth() - 1));
      } else if (attr == ATTR_VALUE) {
        this.value = ((ValueWithRadix) value).value;
        this.value = this.value.extendWidth(width.getWidth(),
            this.value.get(this.value.getWidth() - 1));
        // this.radix = ((ValueWithRadix) value).radix;
      } else if (attr == RadixOption.ATTRIBUTE) {
        this.radix = (RadixOption) value;
      }
    }
  }

  private static class ConstantExpression implements ExpressionComputer {
    private Instance instance;

    public ConstantExpression(Instance instance) {
      this.instance = instance;
    }

    public void computeExpression(ExpressionComputer.Map expressionMap) {
      AttributeSet attrs = instance.getAttributeSet();
      int width = attrs.getValue(StdAttr.WIDTH).getWidth();
      Value v = attrs.getValue(ATTR_VALUE).value;
      for (int b = 0; b < width; b++) {
        expressionMap.put(instance.getLocation(), b,
            Expressions.constant(v.get(b).toIntValue()));
      }
    }
  }

  public static final Attribute<ValueWithRadix> ATTR_VALUE = 
    new ValueWithRadixAttribute("value", S.getter("constantValueAttr"));

  public static InstanceFactory FACTORY = new Constant();

  private static final Color BACKGROUND_COLOR = new Color(230, 230, 230);

  public static final Font DEFAULT_FONT = new Font("monospaced", Font.PLAIN, 12);

  private static final List<Attribute<?>> ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { StdAttr.FACING, RadixOption.ATTRIBUTE,
        StdAttr.WIDTH, ATTR_VALUE });

  public Constant() {
    super("Constant", S.getter("constantComponent"));
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(JoinedConfigurator.create(
          new ConstantConfigurator(), new BitWidthConfigurator(
            StdAttr.WIDTH)));
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new ConstantAttributes();
  }

  @Override
  protected Object getInstanceFeature(Instance instance, Object key) {
    if (key == ExpressionComputer.class)
      return new ConstantExpression(instance);
    return super.getInstanceFeature(instance, key);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    // BitWidth width = attrs.getValue(StdAttr.WIDTH);
    ValueWithRadix value = attrs.getValue(ATTR_VALUE);
    int chars = value.toUnadornedString().length();
    int w = 7 + 7*chars;
    if (facing == Direction.EAST)
      return Bounds.create(-w, -8, w, 16);
    else if (facing == Direction.WEST)
      return Bounds.create(0, -8, w, 16);
    else if (facing == Direction.SOUTH)
      return Bounds.create(-w/2, -16, w, 16);
    else if (facing == Direction.NORTH)
      return Bounds.create(-w/2, 0, w, 16);
    else
      throw new IllegalArgumentException("unrecognized direction " + facing);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new ConstantHDLGenerator(ctx, ctx.attrs.getValue(Constant.ATTR_VALUE).value.toIntValue());
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.WIDTH) {
      instance.recomputeBounds();
      updatePorts(instance);
    } else if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
    } else if (attr == RadixOption.ATTRIBUTE) {
      ValueWithRadix v = instance.getAttributeValue(ATTR_VALUE);
      RadixOption r = instance.getAttributeValue(RadixOption.ATTRIBUTE);
      if (v.radix != r)
        instance.getAttributeSet().setAttr(ATTR_VALUE, new ValueWithRadix(v.value, r));
      instance.recomputeBounds();
      instance.fireInvalidated();
    } else if (attr == ATTR_VALUE) {
      ValueWithRadix v = instance.getAttributeValue(ATTR_VALUE);
      RadixOption r = instance.getAttributeValue(RadixOption.ATTRIBUTE);
      if (r != v.radix)
        instance.getAttributeSet().setAttr(RadixOption.ATTRIBUTE, v.radix);
      instance.fireInvalidated();
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    String vStr = painter.getAttributeValue(ATTR_VALUE).toUnadornedString();
    Bounds bds = getOffsetBounds(painter.getAttributeSet());

    Graphics g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.fillOval(-2, -2, 4, 4);
    g.setFont(DEFAULT_FONT);
    GraphicsUtil.drawCenteredText(g, vStr, bds.getX() + bds.getWidth() / 2,
        bds.getY() + bds.getHeight() / 2 - 2);
  }

  //
  // painting methods
  //
  @Override
  public void paintIcon(InstancePainter painter) {
    int w = painter.getAttributeValue(StdAttr.WIDTH).getWidth();
    int pinx = 16;
    int piny = 9;
    Direction dir = painter.getAttributeValue(StdAttr.FACING);
    if (dir == Direction.EAST) {
    } // keep defaults
    else if (dir == Direction.WEST) {
      pinx = 4;
    } else if (dir == Direction.NORTH) {
      pinx = 9;
      piny = 4;
    } else if (dir == Direction.SOUTH) {
      pinx = 9;
      piny = 16;
    }

    Graphics g = painter.getGraphics();
    if (w == 1) {
      int v = painter.getAttributeValue(ATTR_VALUE).value.toIntValue();
      Value val = v == 1 ? Value.TRUE : Value.FALSE;
      g.setColor(val.getColor());
      GraphicsUtil.drawCenteredText(g, "" + v, 10, 9);
    } else {
      g.setFont(g.getFont().deriveFont(9.0f));
      GraphicsUtil.drawCenteredText(g, "x" + w, 10, 9);
    }
    g.fillOval(pinx, piny, 3, 3);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Bounds bds = painter.getComponent().getVisibleBounds(painter.getGraphics());
    BitWidth width = painter.getAttributeValue(StdAttr.WIDTH);
    ValueWithRadix v = painter.getAttributeValue(ATTR_VALUE);

    Graphics g = painter.getGraphics();
    if (painter.shouldDrawColor()) {
      g.setColor(BACKGROUND_COLOR);
      g.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    }
    if (v.value.getWidth() <= 1 && painter.shouldDrawColor())
      g.setColor(v.value.getColor());
    else
      g.setColor(Color.BLACK);
    g.setFont(DEFAULT_FONT);
    RadixOption radix = painter.getAttributeValue(RadixOption.ATTRIBUTE);
    GraphicsUtil.drawCenteredText(g, v.toUnadornedString(),
        bds.getX() + bds.getWidth() / 2,
        bds.getY() + bds.getHeight() / 2 - 2);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Value value = state.getAttributeValue(ATTR_VALUE).value;
    state.setPort(0, value, 1);
  }

  private void updatePorts(Instance instance) {
    Port[] ps = { new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH) };
    instance.setPorts(ps);
  }

  public static class ValueWithRadix {
    public final Value value;
    public final RadixOption radix;
    public ValueWithRadix(Value v, RadixOption r) {
      value = v; radix = r;
    }
    public static ValueWithRadix fromValue(Value v) {
      return new ValueWithRadix(v, RadixOption.RADIX_16);
    }
    public static ValueWithRadix fromInteger(int v) {
      return fromValue(Value.createKnown(BitWidth.of(32), v));
    }
    public static ValueWithRadix parse(String value) {
      return new ValueWithRadix(value);
    }
    private ValueWithRadix(String s) {
      s = s.toLowerCase();
      if (s.startsWith("0x")) {
        s = s.substring(2);
        value = Value.createKnown(BitWidth.of(32), (int) Long.parseLong(s, 16));
        radix = RadixOption.RADIX_16;
      } else if (s.startsWith("0b")) {
        s = s.substring(2);
        value = Value.createKnown(BitWidth.of(32), (int) Long.parseLong(s, 2));
        radix = RadixOption.RADIX_2;
      } else if (s.startsWith("0o")) {
        s = s.substring(2);
        value = Value.createKnown(BitWidth.of(32), (int) Long.parseLong(s, 8));
        radix = RadixOption.RADIX_8;
      } else if (s.startsWith("+") || s.startsWith("-")) {
        value = Value.createKnown(BitWidth.of(32), (int) Long.parseLong(s, 10));
        radix = RadixOption.RADIX_10_SIGNED;
      } else  {
        value = Value.createKnown(BitWidth.of(32), (int) Long.parseLong(s, 10));
        radix = RadixOption.RADIX_10_UNSIGNED;
      }
    }
    public String toDisplayString() { // includes prefix, fixed width
      if (radix == RadixOption.RADIX_2)
        return "0b" + value.toBinaryString();
      else if (radix == RadixOption.RADIX_8)
        return "0o" + value.toOctalString();
      else if (radix == RadixOption.RADIX_10_UNSIGNED)
        return Integer.toUnsignedString(value.toIntValue()); // variable width
      else if (radix == RadixOption.RADIX_10_SIGNED) {
        int val = this.value.extendWidth(32,
            value.get(value.getWidth() - 1)).toIntValue();
        return (val >= 0 ? "+" : "") + Integer.toString(val); // variable width
      }
      else 
        return "0x" + value.toHexString();
    }
    public String toUnadornedString() { // without prefix, fixed width
      if (radix == RadixOption.RADIX_2)
        return value.toBinaryString();
      else if (radix == RadixOption.RADIX_8)
        return value.toOctalString();
      else if (radix == RadixOption.RADIX_10_UNSIGNED)
        return Integer.toUnsignedString(value.toIntValue()); // variable width
      else if (radix == RadixOption.RADIX_10_SIGNED) {
        int val = this.value.extendWidth(32,
            value.get(value.getWidth() - 1)).toIntValue();
        return (val >= 0 ? "+" : "") + Integer.toString(val); // variable width
      }
      else 
        return value.toHexString();
    }
    public String toStandardString() { // always hex, variable width
     return "0x" + value.toHexString();
    }
  }

  private static class ValueWithRadixAttribute extends Attribute<ValueWithRadix> {
    private ValueWithRadixAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public ValueWithRadix parse(String value) {
      return ValueWithRadix.parse(value);
    }

    @Override
    public String toDisplayString(ValueWithRadix value) {
      return value.toDisplayString();
    }

    @Override
    public String toStandardString(ValueWithRadix value) {
      return value.toStandardString();
    }
  }

  // TODO: Allow editing of value via text tool/attribute table
}
