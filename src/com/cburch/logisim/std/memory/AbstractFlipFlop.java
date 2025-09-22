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

package com.cburch.logisim.std.memory;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;

abstract class AbstractFlipFlop extends InstanceFactory {
  public static class Logger extends InstanceLogger {
    @Override
    public String getLogName(InstanceState state, Object option) {
      String ret = state.getAttributeValue(StdAttr.LABEL);
      return ret != null && !ret.equals("") ? ret : null;
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return BitWidth.ONE;
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      StateData s = (StateData) state.getDataAsCustom();
      return s == null ? Value.FALSE : s.curValue;
    }
  }

  public static class Poker extends InstancePoker {
    boolean isPressed = true;

    @Override
    public boolean capturesTextInput() { return true; }

    private boolean isInside(InstanceState state, MouseEvent e) {
      Location loc = state.getInstance().getLocation();
      int dx, dy;
      if (state.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
        dx = e.getX() - (loc.getX() - 20);
        dy = e.getY() - (loc.getY() + 10);
      } else {
        dx = e.getX() - (loc.getX() + 20);
        dy = e.getY() - (loc.getY() + 30);
      }
      int d2 = dx * dx + dy * dy;
      return d2 < 8 * 8;
    }

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      isPressed = isInside(state, e);
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      if (isPressed && isInside(state, e)) {
        StateData myState = (StateData) state.getDataAsCustom();
        if (myState == null)
          return;

        myState.curValue = myState.curValue.not();
        state.queueForPropagation();
      }
      isPressed = false;
    }

    @Override
    public void keyTyped(InstanceState state, KeyEvent e) {
      int val = Character.digit(e.getKeyChar(), 2);
      if (val < 0)
        return;
      StateData myState = (StateData) state.getDataAsCustom();
      if (myState == null)
        return;
      e.consume();
      if (val == 0 && myState.curValue != Value.FALSE) {
        myState.curValue = Value.FALSE;
        state.queueForPropagation();
      } else if (val == 1 && myState.curValue != Value.TRUE) {
        myState.curValue = Value.TRUE;
        state.queueForPropagation();
      }
    }

    @Override
    public void keyPressed(InstanceState state, KeyEvent e) {
      StateData myState = (StateData) state.getDataAsCustom();
      if (myState == null)
        return;
      if ((e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_LEFT) && myState.curValue != Value.FALSE) {
        e.consume();
        myState.curValue = Value.FALSE;
        state.queueForPropagation();
      } else if ((e.getKeyCode() == KeyEvent.VK_UP ||e.getKeyCode() == KeyEvent.VK_RIGHT) && myState.curValue != Value.TRUE) {
        e.consume();
        myState.curValue = Value.TRUE;
        state.queueForPropagation();
      }
    }

    @Override
    public void paint(InstancePainter painter) {
      Location loc = painter.getLocation();
      int x = loc.getX();
      int y = loc.getY();
      Graphics g = painter.getGraphics();
      g.setColor(Color.RED); // red to indicate text capture
      g.drawOval(x - 27, y + 3, 15, 15);
      g.setColor(Color.BLACK);
    }
  }

  private static class StateData extends ClockState implements ComponentData {
    Value curValue = Value.FALSE;

    StateData() { }

    StateData(StateData other) {
      super(other);
      curValue = other.curValue;
    }

    @Override
    public StateData duplicateForNewSimulation() {
      return new StateData(this);
    }
  }

  private static final int STD_PORTS = 5; // or 6, with enable

  private Attribute<AttributeOption> triggerAttribute;

  public static final Attribute<Boolean> ATTR_ENABLE = Attributes.forBoolean(
      "enable", S.getter("flipFlopEnableAttr"));

  private int numInputs;

  protected AbstractFlipFlop(String name, String iconName, StringGetter desc,
      int numInputs, boolean allowLevelTriggers) {
    super(name, desc);
    this.numInputs = numInputs;
    setIconName(iconName);
    triggerAttribute = allowLevelTriggers ? StdAttr.TRIGGER
        : StdAttr.EDGE_TRIGGER;
    setAttributes(new Attribute[] { triggerAttribute, StdAttr.LABEL,
      StdAttr.LABEL_FONT, ATTR_ENABLE, Register.ATTR_SHOW_IN_TAB, StdAttr.APPEARANCE },
      new Object[] { StdAttr.TRIG_RISING, "",
        StdAttr.DEFAULT_LABEL_FONT, Boolean.FALSE, true, StdAttr.APPEAR_CLASSIC });
    setInstancePoker(Poker.class);
    setInstanceLogger(Logger.class);
  }


  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      return Bounds.create(-40, -10, 40, 40);
    } else {
      return Bounds.create(-10, 0, 60, 60);
    }
  }

  private void updatePorts(Instance instance) {
    int enable = 0;
    if (instance.getAttributeValue(ATTR_ENABLE) == Boolean.TRUE) {
      enable = 1;
    }
    Port[] ps = new Port[numInputs + STD_PORTS + enable];
    if (instance.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      if (numInputs == 1) {
        ps[0] = new Port(-40, 20, Port.INPUT, 1);
        ps[1] = new Port(-40,  0, Port.INPUT, 1);
      } else if (numInputs == 2) {
        ps[0] = new Port(-40,  0, Port.INPUT, 1);
        ps[1] = new Port(-40, 20, Port.INPUT, 1);
        ps[2] = new Port(-40, 10, Port.INPUT, 1);
      } else {
        throw new RuntimeException("flip-flop input > 2");
      }
      ps[numInputs + 1] = new Port(  0,  0, Port.OUTPUT, 1);
      ps[numInputs + 2] = new Port(  0, 20, Port.OUTPUT, 1);
      ps[numInputs + 3] = new Port(-10, 30, Port.INPUT,  1);
      ps[numInputs + 4] = new Port(-30, 30, Port.INPUT,  1);
      if (enable > 0) {
        ps[numInputs + 5] = new Port(-20, 30, Port.INPUT,  1);
      }
    } else {
      if (numInputs == 1) {
        ps[0] = new Port(-10, 10, Port.INPUT, 1);
        ps[1] = new Port(-10, 50, Port.INPUT, 1);
      } else if (numInputs == 2) {
        ps[0] = new Port(-10, 10, Port.INPUT, 1);
        ps[1] = new Port(-10, 30, Port.INPUT, 1);
        ps[2] = new Port(-10, 50, Port.INPUT, 1);
      } else {
        throw new RuntimeException("flip-flop input > 2");
      }
      ps[numInputs + 1] = new Port(50, 10, Port.OUTPUT, 1);
      ps[numInputs + 2] = new Port(50, 50, Port.OUTPUT, 1);
      ps[numInputs + 3] = new Port(20, 60, Port.INPUT, 1);
      ps[numInputs + 4] = new Port(20, 0, Port.INPUT, 1);
      if (enable > 0) {
        ps[numInputs + 5] = new Port(30, 60, Port.INPUT,  1);
      }
    }
    ps[numInputs].setToolTip(S.getter("flipFlopClockTip"));
    ps[numInputs + 1].setToolTip(S.getter("flipFlopQTip"));
    ps[numInputs + 2].setToolTip(S.getter("flipFlopNotQTip"));
    ps[numInputs + 3].setToolTip(S.getter("flipFlopResetTip"));
    ps[numInputs + 4].setToolTip(S.getter("flipFlopPresetTip"));
    if (enable > 0) {
      ps[numInputs + 5].setToolTip(S.getter("flipFlopEnableTip"));
    }
    instance.setPorts(ps);
  }
  protected abstract Value computeValue(Value[] inputs, Value curValue);

  //
  // concrete methods not intended to be overridden
  //
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    Bounds bds = instance.getNominalBounds();
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, bds.getX()
        + bds.getWidth() / 2, bds.getY() - 3, GraphicsUtil.H_CENTER,
        GraphicsUtil.V_BASELINE);
  }

  protected abstract FlipFlopHDLGenerator getHDLGenerator(HDLSupport.ComponentContext ctx);

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    if (ctx.attrs.getValue(ATTR_ENABLE))
      return null; // flip-flops with enable pin not yet supported
    return getHDLGenerator(ctx);
  }

  //
  // abstract methods intended to be implemented in subclasses
  //
  protected abstract String getInputName(int index);

  @Override
  public void paintInstance(InstancePainter painter) {
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      paintInstanceClassic(painter);
    } else {
      paintInstanceEvolution(painter);
    }
  }

  private void paintInstanceClassic(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    painter.drawBounds();
    painter.drawLabel();
    if (painter.getShowState()) {
      Location loc = painter.getLocation();
      StateData myState = (StateData) painter.getDataAsCustom();
      if (myState != null) {
        int x = loc.getX();
        int y = loc.getY();
        g.setColor(myState.curValue.getColor());
        g.fillOval(x - 26, y + 4, 13, 13);
        g.setColor(Color.WHITE);
        GraphicsUtil.drawCenteredText(g, MemState.FONT,
            myState.curValue.toDisplayString(), x - 20, y + 9);
        g.setColor(Color.BLACK);
      }
    }

    int enable = 0;
    if (painter.getAttributeValue(ATTR_ENABLE) == Boolean.TRUE) {
      enable = 1;
    }
    int n = numInputs;
    g.setColor(Color.GRAY);
    painter.drawPort(n + 3, "0", Direction.SOUTH);
    painter.drawPort(n + 4, "1", Direction.SOUTH);
    if (enable > 0) {
      painter.drawPort(n + 5, S.get("memEnableLabel"), Direction.SOUTH);
    }
    g.setColor(Color.BLACK);
    for (int i = 0; i < n; i++) {
      painter.drawPort(i, getInputName(i), Direction.EAST);
    }
    painter.drawClock(n, Direction.EAST);
    painter.drawPort(n + 1, "Q", Direction.WEST);
    painter.drawPort(n + 2);
  }

  private void paintInstanceEvolution(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    painter.drawLabel();
    Location loc = painter.getLocation();
    int x = loc.getX();
    int y = loc.getY();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x, y, 40, 60);
    if (painter.getShowState()) {
      StateData myState = (StateData) painter.getDataAsCustom();
      if (myState != null) {
        g.setColor(myState.curValue.getColor());
        g.fillOval(x + 13, y + 23, 14, 14);
        g.setColor(Color.WHITE);
        GraphicsUtil.drawCenteredText(g, MemState.FONT,
            myState.curValue.toDisplayString(), x + 19, y + 29);
      }
    }

    int enable = 0;
    if (painter.getAttributeValue(ATTR_ENABLE) == Boolean.TRUE) {
      enable = 1;
    }
    int n = numInputs;
    g.setColor(Color.GRAY);
    painter.drawPort(n + 3, "R", Direction.SOUTH);
    painter.drawPort(n + 4, "S", Direction.NORTH);
    if (enable > 0) {
      painter.drawPort(n + 5, "E", Direction.SOUTH);
    }
    g.setColor(Color.BLACK);
    for (int i = 0; i < n; i++) {
      g.fillRect(x - 10, y + 9 + i * 20, 10, 3);
      painter.drawPort(i);
      GraphicsUtil.drawCenteredText(g, MemState.FONT, getInputName(i), x + 8, y + 10 + i * 20);
    }
    Object Trigger = painter.getAttributeValue(triggerAttribute);
    if (Trigger.equals(StdAttr.TRIG_RISING)
        || Trigger.equals(StdAttr.TRIG_FALLING)) {
      painter.drawClockSymbol(x, y + 50);
    } else {
      GraphicsUtil.drawCenteredText(g, StdAttr.DEFAULT_LABEL_FONT, "E", x + 8, y + 50);
    }
    if (Trigger.equals(StdAttr.TRIG_RISING)
        || Trigger.equals(StdAttr.TRIG_HIGH)) {
      g.fillRect(x - 10, y + 49, 10, 3);
    } else {
      GraphicsUtil.switchToWidth(g, 2);
      g.drawOval(x - 10, y + 45, 10, 10);
      GraphicsUtil.switchToWidth(g, 1);
    }
    painter.drawPort(n);

    g.fillRect(x + 40, y + 9, 10, 3);
    GraphicsUtil.drawCenteredText(g, StdAttr.DEFAULT_LABEL_FONT, "Q", x + 31, y + 10);
    painter.drawPort(n + 1);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawOval(x + 40, y + 45, 10, 10);
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPort(n + 2);
  }

  @Override
  public void propagate(InstanceState state) {
    // boolean changed = false;
    StateData data = (StateData) state.getDataAsCustom();
    if (data == null) {
      // changed = true;
      data = new StateData();
      state.setData(data);
    }

    int enable = 0;
    if (state.getAttributeValue(ATTR_ENABLE) == Boolean.TRUE) {
      enable = 1;
    }
    int n = numInputs;
    Object triggerType = state.getAttributeValue(triggerAttribute);
    boolean triggered = data
        .updateClock(state.getPortValue(n), triggerType);

    if (state.getPortValue(n + 3) == Value.TRUE) { // clear requested
      // changed |= data.curValue != Value.FALSE;
      data.curValue = Value.FALSE;
    } else if (state.getPortValue(n + 4) == Value.TRUE) { // preset requested
      // changed |= data.curValue != Value.TRUE;
      data.curValue = Value.TRUE;
    } else if (triggered && (enable == 0 || state.getPortValue(n + 5) != Value.FALSE)) {
      // Clock has triggered and flip-flop is enabled: Update the state
      Value[] inputs = new Value[n];
      for (int i = 0; i < n; i++) {
        inputs[i] = state.getPortValue(i);
      }

      Value newVal = computeValue(inputs, data.curValue);
      if (newVal == Value.TRUE || newVal == Value.FALSE) {
        // changed |= data.curValue != newVal;
        data.curValue = newVal;
      }
    }

    state.setPort(n + 1, data.curValue, Memory.DELAY);
    state.setPort(n + 2, data.curValue.not(), Memory.DELAY);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.APPEARANCE) {
      instance.recomputeBounds();
      updatePorts(instance);
    } else if (attr == ATTR_ENABLE) {
      updatePorts(instance);
    }
  }
}
