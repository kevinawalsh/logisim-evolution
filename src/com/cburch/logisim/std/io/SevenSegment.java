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
import java.util.Arrays;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.tools.key.IntegerConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;

public class SevenSegment extends InstanceFactory implements DynamicElementProvider {

  public static final int MAX_DIGITS = 8; // more than 8 digits not available in real components?

  static class State implements ComponentData {
    private boolean segOn[] = new boolean[MAX_DIGITS*8];
    private long persistTo[] = new long[MAX_DIGITS*8];

    public State(long curClock) {
      Arrays.fill(persistTo, curClock - 1);
    }

    State(State other) {
      segOn = other.segOn.clone();
      persistTo = other.persistTo.clone();
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }

    void setDigit(int digit, boolean on[], boolean enabled, long persist) {
      for (int i = 0; i < 8; i++) {
        int idx = 8*digit + i;
        if (segOn[idx])
          persistTo[idx] = persist - 1;
        boolean val = enabled &&  on[i];
        segOn[idx] = val;
        if (val)
          persistTo[idx] = persist;
        else if (enabled)
          persistTo[idx] = 0;
      }
    }

    // returns 1.0 for "on", 0.0 for "off for more than the presist time",
    // and interpolates between for "off for a short time"
    float get(int digit, int seg, long curTick, int persistDuration) {
      int idx = 8*digit + seg;
      boolean ret = segOn[idx];
      if (ret) return 1.0f;
      if (persistDuration > 0 && persistTo[idx] - curTick >= 0)
        return Math.max(0.0f, Math.min(1.0f, (float)(persistTo[idx] - curTick) / (float)persistDuration));
      return 0.0f;
    }

  }
  
  static void drawBase(InstancePainter painter) {
    State data = getState(painter);
    long ticks = painter.getTickCount();

    // int summ = painter.getDataOrDefault(0);

    Bounds bds = painter.getNominalBounds();
    int x = bds.getX() + 5;
    int y = bds.getY();

    Graphics g = painter.getGraphics();
    SevenSegmentAttributes attrs = (SevenSegmentAttributes)painter.getAttributeSet();
    // Color onColor = painter.getAttributeValue(Io.ATTR_ON_COLOR);
    // Color offColor = painter.getAttributeValue(Io.ATTR_OFF_COLOR);
    Color bgColor = painter.getAttributeValue(Io.ATTR_BACKGROUND);
    if (painter.shouldDrawColor() && bgColor.getAlpha() != 0) {
      g.setColor(bgColor);
      g.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
      g.setColor(Color.BLACK);
    }
    painter.drawBounds();
    g.setColor(Color.DARK_GRAY);

    int digits = painter.getAttributeValue(ATTR_DIGITS).intValue();
    int persistDuration = digits == 1 ? 0 : painter.getAttributeValue(ATTR_PERSIST).intValue();

    for (int digit = 0; digit < digits; digit++) {
      for (int i = 0; i <= 7; i++) {
        if (painter.getShowState()) {
          g.setColor(attrs.getColor(data.get(digit, i, ticks, persistDuration)));
        }
        if (i < 7) {
          Bounds seg = SEGMENTS[i];
          g.fillRect(x + seg.getX(), y + seg.getY(), seg.getWidth(), seg.getHeight());
        } else {
          g.fillOval(x + 28, y + 48, 5, 5); // draw decimal point
        }
      }
      x += 40;
    }
    painter.drawLabel();
    painter.drawPorts();
  }

  public static final int Segment_A = 0;
  public static final int Segment_B = 1;
  public static final int Segment_C = 2;
  public static final int Segment_D = 3;
  public static final int Segment_E = 4;
  public static final int Segment_F = 5;
  public static final int Segment_G = 6;
  public static final int DP = 7;

  public static String[] pinLabels(int numPins) {
    String[] basePins = new String[] {
      "Segment_A", "Segment_B",
      "Segment_C", "Segment_D",
      "Segment_E", "Segment_F",
      "Segment_G", "Segment_DP" };
    if (numPins == 8)
      return basePins;
    int numDigits = numPins - 8;
    String[] labels = new String[numPins];
    for (int i = 0; i < 8; i++)
      labels[i] = basePins[i];
    for (int i = 1; i <= numDigits; i++)
      labels[8+i-1] = "Digit_"+i+"_Enable";
    return labels;
  }

  static Bounds[] SEGMENTS;
  static {
      SEGMENTS = new Bounds[] { Bounds.create(3, 8, 19, 4),
        Bounds.create(23, 10, 4, 19), Bounds.create(23, 30, 4, 19),
        Bounds.create(3, 47, 19, 4), Bounds.create(-2, 30, 4, 19),
        Bounds.create(-2, 10, 4, 19), Bounds.create(3, 28, 19, 4) };
  }

  static Color DEFAULT_OFF = new Color(220, 220, 220);

  static final Attribute<Integer> ATTR_DIGITS = Attributes
      .forIntegerRange("digits", S.getter("sevenSegmentDigits"), 1, MAX_DIGITS);

  static final Attribute<Boolean> ATTR_ENABLES_ACTIVE = Attributes.forBoolean(
      "activeEnables", S.getter("ioActiveEnablesAttr"));

  static final Attribute<Integer> ATTR_PERSIST = DotMatrix.ATTR_PERSIST;

  public SevenSegment() {
    super("7-Segment Display", S.getter("sevenSegmentComponent"));
    setIconName("7seg.gif");
    setKeyConfigurator(JoinedConfigurator.create(
        new IntegerConfigurator(ATTR_DIGITS, 1, MAX_DIGITS, 0),
        new DirectionConfigurator(StdAttr.LABEL_LOC)));
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new SevenSegmentAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int digits = attrs.getValue(ATTR_DIGITS).intValue();
    int w = 40*digits;
    return Bounds.create(15 - w/2, 0, w, 60);
  }

  private void updatePorts(Instance instance) {
    int digits = instance.getAttributeValue(ATTR_DIGITS).intValue();
    Port[] ps = new Port[8 + (digits == 1 ? 0 : digits)];
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
    if (digits > 1) {
      int w = digits*40;
      for (int i = 0; i < digits; i++) {
        ps[DP+1+i] = new Port(30+(1+i)*10, 60, Port.INPUT, 1);
        ps[DP+1+i].setToolTip(S.getter("Enable_Digit_" + (i+1)));
      }
    }
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
    } else if (attr == ATTR_DIGITS) {
      instance.recomputeBounds();
      updatePorts(instance);
      instance.computeLabelTextField(0);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    drawBase(painter);
  }

  @Override
  public void propagate(InstanceState state) {
    State data = getState(state);
    int digits = state.getAttributeValue(ATTR_DIGITS).intValue();
    long clock = state.getTickCount();
    long persist = clock + (digits == 1 ? -1 : state.getAttributeValue(ATTR_PERSIST).intValue());

    Boolean active = state.getAttributeValue(Io.ATTR_ACTIVE);
    Value desired = active == null || active.booleanValue() ? Value.TRUE : Value.FALSE;

    Boolean activeEnables = state.getAttributeValue(ATTR_ENABLES_ACTIVE);
    Value desiredEnables = digits == 1 || activeEnables == null || activeEnables.booleanValue() ? Value.TRUE : Value.FALSE;

    boolean on[] = new boolean[8];
    for (int i = 0; i < 8; i++) {
      Value val = state.getPortValue(i);
      on[i] = (val == desired);
    }
    if (digits == 1) {
      data.setDigit(0, on, true, persist);
    } else {
      for (int digit = 0; digit < digits; digit++) {
        Value en = state.getPortValue(8+digit);
        if (en == desiredEnables)
          data.setDigit(digit, on, true, persist);
        else
          data.setDigit(digit, null, false, persist);
      }
    }
  }

  private static State getState(InstanceState state) {
    long clock = state.getTickCount();

    State data = (State) state.getDataAsCustom();
    if (data == null) {
      data = new State(clock);
      state.setData(data);
    } else {
      // data.update(clock);
    }
    return data;
  }

  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new SevenSegmentShape(x, y, path);
  }
}
