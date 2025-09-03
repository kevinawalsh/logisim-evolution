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
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
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
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.tools.key.DirectionConfigurator;

public class Button extends InstanceFactory {
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
      State data = (State) state.getData();
      return data == null  || data.value == null ? Value.FALSE : data.value;
    }

    @Override
    public boolean isInput(InstanceState state, Object option) {
      return true;
    }
  }

  public static class Poker extends InstancePoker {
    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      State data = getState(state);
      synchronized (data) {
        data.pressed = true;
        data.pressedRecently = true;
      }
      state.getInstance().fireInvalidated();
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      State data = getState(state);
      synchronized (data) {
        data.pressed = false;
      }
      state.getInstance().fireInvalidated();
    }

    private State getState(InstanceState state) {
      State data = (State) state.getData();
      if (data == null) {
        data = new State();
        state.setData(data);
      }
      return data;
    }
  }

  private static final int DEPTH = 3;

  // Momentary normally open, asynchronous (default)
  //  - output is 1 when mouse is pressing, 0 otherwise
  // Momentary normally closed, asynchronous
  //  - output is 0 when mouse is pressing, 1 otherwise
  // One-Shot normally open, asynchronous
  //  - output becomes 1 when mouse is pressed, and reverts
  //    to 0 after a delay of 30 simulation units.
  // One-Shot normally closed, asynchronous
  //  - output becomes 0 when mouse is pressed, and reverts
  //    to 1 after a delay of 30 simulation units.
  // Latching, asynchronous
  //  - output toggles upon mouse press
  //
  // Momentary NC (or NO), synchronous
  //  - output updates according to mouse position on each clock edge
  // One-Shot NC (or NO), synchronous
  //  - output becomes 1 (or 0 for NC) on the clock edge following mouse press,
  //    but reverts to 0 (or 1 for NC) on the next clock edge (unless mouse is
  //    pressed afresh before that clock edge).
  // Latching, synchronous
  //  - output changes on the clock edge following mouse press

  static final AttributeOption BEHAVIOR_MOMENTARY_NO = new AttributeOption("momentary-no",
      S.getter("ioButtonMomentaryNormallyOpen"));
  static final AttributeOption BEHAVIOR_MOMENTARY_NC = new AttributeOption("momentary-nc",
      S.getter("ioButtonMomentaryNormallyClosed"));
  static final AttributeOption BEHAVIOR_ONESHOT_NO = new AttributeOption("oneshot-no",
      S.getter("ioButtonOneShotNormallyOpen"));
  static final AttributeOption BEHAVIOR_ONESHOT_NC = new AttributeOption("oneshot-nc",
      S.getter("ioButtonOneShotNormallyClosed"));
  static final AttributeOption BEHAVIOR_LATCHING = new AttributeOption("latching",
      S.getter("ioButtonLatching"));
  static final Attribute<AttributeOption> ATTR_BEHAVIOR = Attributes
      .forOption("behavior", S.getter("ioButtonBehavior"),
          new AttributeOption[] { BEHAVIOR_MOMENTARY_NO, BEHAVIOR_MOMENTARY_NC, 
            BEHAVIOR_ONESHOT_NO, BEHAVIOR_ONESHOT_NC, BEHAVIOR_LATCHING });

  static final AttributeOption CLOCKING_ASYNCHRONOUS = new AttributeOption("asynchronous",
      S.getter("ioButtonAsynchronous"));
  static final AttributeOption CLOCKING_SYNCHRONOUS = new AttributeOption("synchronous",
      S.getter("ioButtonSynchronous"));
  static final Attribute<AttributeOption> ATTR_CLOCKING = Attributes
      .forOption("clocking", S.getter("ioButtonClocking"),
          new AttributeOption[] { CLOCKING_ASYNCHRONOUS, CLOCKING_SYNCHRONOUS });

  public Button() {
    super("Button", S.getter("buttonComponent"));
    setAttributes(new Attribute[] { StdAttr.FACING,
      ATTR_BEHAVIOR, ATTR_CLOCKING, StdAttr.EDGE_TRIGGER, 
      Io.ATTR_COLOR,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT,
      StdAttr.LABEL_COLOR }, new Object[] { Direction.EAST,
        BEHAVIOR_MOMENTARY_NO, CLOCKING_ASYNCHRONOUS, StdAttr.TRIG_RISING,
        Color.WHITE, "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT,
        Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setIconName("button.gif");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    setInstancePoker(Poker.class);
    setInstanceLogger(Logger.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    recomputeLabelTextFieldPosition(instance);
  }

  private void recomputeLabelTextFieldPosition(Instance instance) {
    AttributeOption clocking = instance.getAttributeValue(ATTR_CLOCKING);
    if (clocking == CLOCKING_SYNCHRONOUS)
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_LEFT | Instance.AVOID_BOTTOM);
    else
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_LEFT);
  }

  private void updatePorts(Instance instance) {
    AttributeOption clocking = instance.getAttributeValue(ATTR_CLOCKING);
    int n = (clocking == CLOCKING_SYNCHRONOUS ? 2 : 1);
    Port[] ps = new Port[n];
    ps[0] = new Port(0, 0, Port.OUTPUT, 1);
    ps[0].setToolTip(S.getter("ioButtonOutputTip"));
    if (n == 2) {
      Direction facing = instance.getAttributeValue(StdAttr.FACING);
      int cx, cy;
      if (facing == Direction.EAST) {
        cx = -10; cy = 10;
      } else if (facing == Direction.SOUTH) {
        cx = -10; cy = -10;
      } else if (facing == Direction.WEST) {
        cx = 10; cy = -10;
      } else { // NORTH
        cx = 10; cy = 10;
      }
      ps[1] = new Port(cx, cy, Port.INPUT, 1);
      ps[1].setToolTip(S.getter("ioButtonClockTip"));
    }
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    return Bounds.create(-20, -10, 20, 20).rotate(Direction.EAST, facing,
        0, 0);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return ButtonHDLGenerator.forButton(ctx);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "Button"; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      updatePorts(instance);
      recomputeLabelTextFieldPosition(instance);
    } else if (attr == StdAttr.LABEL_LOC) {
      recomputeLabelTextFieldPosition(instance);
    } else if (attr == ATTR_CLOCKING) {
      instance.recomputeBounds();
      updatePorts(instance);
      recomputeLabelTextFieldPosition(instance);
      instance.fireInvalidated(); // recompute using clock signal
    } else if (attr == ATTR_BEHAVIOR) {
      instance.fireInvalidated(); // resting value might have changed
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Bounds bds = painter.getNominalBounds();
    int x = bds.getX();
    int y = bds.getY();
    int w = bds.getWidth();
    int h = bds.getHeight();
    
    AttributeOption behavior = painter.getAttributeValue(ATTR_BEHAVIOR);
    AttributeOption clocking = painter.getAttributeValue(ATTR_CLOCKING);
    Value resting, active;
    if (behavior == BEHAVIOR_MOMENTARY_NC || behavior == BEHAVIOR_ONESHOT_NC ) {
      resting = Value.TRUE;
      active = Value.FALSE;
    } else {
      resting = Value.FALSE;
      active = Value.TRUE;
    }

    Value val;
    boolean pressed;
    if (painter.getShowState()) {
      State data = (State) painter.getData();
      if (data == null) {
        pressed = false;
        val = resting;
      } else {
        synchronized (data) {
          val = data.value == null ? resting : data.value;
          if (behavior == BEHAVIOR_MOMENTARY_NC || behavior == BEHAVIOR_MOMENTARY_NO)
            pressed = data.pressed || val == active;
          else if (behavior == BEHAVIOR_ONESHOT_NC || behavior == BEHAVIOR_ONESHOT_NO)
            pressed = data.pressedRecently || val == active;
          else
            pressed = data.pressedRecently;
        }
      }
    } else {
      val = resting;
      pressed = false;
    }

    Color color = painter.getAttributeValue(Io.ATTR_COLOR);
    if (!painter.shouldDrawColor()) {
      int hue = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
      color = new Color(hue, hue, hue);
    } else {
      if (val == active && active == Value.TRUE)
        color = color.brighter();
      else if (val == active && active == Value.FALSE)
        color = color.darker();
    }

    Graphics2D g = (Graphics2D)painter.getGraphics();
    
    // centered labels shift when button is pressed or latched
    double labelOffset = 0;
    if (pressed || (behavior == BEHAVIOR_LATCHING && val == active)) {
      Object labelLoc = painter.getAttributeValue(StdAttr.LABEL_LOC);
      if (labelLoc == StdAttr.LABEL_CENTER)
        labelOffset = DEPTH;
    }

    if (pressed) {
      x += DEPTH;
      y += DEPTH;

      // Draw exposed north/west wire stub when pressed
      Object facing = painter.getAttributeValue(StdAttr.FACING);
      if (facing == Direction.NORTH || facing == Direction.WEST) {
        Location p = painter.getLocation();
        int px = p.getX();
        int py = p.getY();
        GraphicsUtil.switchToWidth(g, Wire.WIDTH);
        g.setColor(val == Value.FALSE ? Value.FALSE_COLOR : Value.TRUE_COLOR);
        if (facing == Direction.NORTH)
          g.drawLine(px, py, px, py + 10);
        else // WEST
          g.drawLine(px, py, px + 10, py);
        GraphicsUtil.switchToWidth(g, 1);
      }

      if (behavior == BEHAVIOR_LATCHING) {
        // circle
        g.setColor(color);
        g.fillOval(x, y, w - DEPTH, h - DEPTH);
        g.setColor(Color.BLACK);
        g.drawOval(x, y, w - DEPTH, h - DEPTH);
      } else if (behavior == BEHAVIOR_ONESHOT_NC || behavior == BEHAVIOR_ONESHOT_NO) {
        // hexagon
        double hw = w - DEPTH;
        double hh = h - DEPTH;
        double xx[] = { hw/4+x,  3*hw/4+x,     hw+x,   3*hw/4+x,   hw/4+x,     0+x };
        double yy[] = {    0+y,       0+y,   hh/2+y,       hh+y,     hh+y,  hh/2+y };
        Path2D.Double hexagon = new Path2D.Double();
        hexagon.moveTo(xx[0], yy[0]);
        for (int i = 1; i < 6; i++) hexagon.lineTo(xx[i], yy[i]);
        hexagon.closePath();
        g.setColor(color);
        g.fill(hexagon);
        g.setColor(Color.BLACK);
        g.draw(hexagon);
      } else { // MOMENTARY
        // square
        g.setColor(color);
        g.fillRect(x, y, w - DEPTH, h - DEPTH);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, w - DEPTH, h - DEPTH);
      }
    } else {
      if (behavior == BEHAVIOR_LATCHING) {
        // raised circle (if on, then only slightly raised)
        double p = 0; // pressed/toggled offset
        if (val == active) {
          p = DEPTH/3;
          if (labelOffset != 0)
            labelOffset = p;
        }
        double tx = x + p; // top circle position x, y
        double ty = y + p;
        double bx = x + DEPTH; // bottom circle position x, y
        double by = y + DEPTH;
        double cw = (w - DEPTH); // button circle width, height
        double ch = (h - DEPTH);
        double rw = cw/2.0; // circle radius in width, height direction
        double rh = ch/2.0;
        double rw2 = rw/Math.sqrt(2);
        double rh2 = rh/Math.sqrt(2);

        Path2D.Double sides = new Path2D.Double();
        sides.moveTo(tx + rw + rw2, ty + rh - rh2); // top arc, top right
        sides.lineTo(x + w - rw + rw2, y + h - rh - rh2); // bottom arc, top right
        sides.lineTo(tx + rw - rw2, ty + rh + rh2); // top arc, bottom left
        sides.lineTo(x + w - rw - rw2, y + h - rh + rh2); // bottom arc, bottom left
        sides.closePath();
        Arc2D bottomWedge = new Arc2D.Double(bx, by, cw, ch, 45, -180, Arc2D.CHORD);
        Arc2D bottomArc = new Arc2D.Double(bx, by, cw, ch, 45, -180, Arc2D.OPEN);

        g.setColor(color.darker());
        g.fill(sides);
        g.fill(bottomWedge);

        g.setColor(color);
        g.fill(new Ellipse2D.Double(tx, ty, cw, ch));

        g.setColor(Color.BLACK);
        g.draw(bottomArc);
        g.draw(new Ellipse2D.Double(tx, ty, cw, ch));
        g.draw(new Line2D.Double(tx + rw + rw2, ty + rh - rh2,
              x + w - rw + rw2, y + h - rh - rh2));
        g.draw(new Line2D.Double(tx + rw - rw2, ty + rh + rh2,
              x + w - rw - rw2, y + h - rh + rh2));
      } else if (behavior == BEHAVIOR_ONESHOT_NC || behavior == BEHAVIOR_ONESHOT_NO) {
        // raised hexagon
        double hw = w - DEPTH;
        double hh = h - DEPTH;
        double xx[] = { hw/4+x,  3*hw/4+x,     hw+x,   3*hw/4+x,   hw/4+x,     0+x };
        double yy[] = {    0+y,       0+y,   hh/2+y,       hh+y,     hh+y,  hh/2+y };
        Path2D.Double upper = new Path2D.Double();
        upper.moveTo(xx[0], yy[0]);
        for (int i = 1; i < 6; i++) upper.lineTo(xx[i], yy[i]);
        upper.closePath();
        Path2D.Double lower = new Path2D.Double();
        lower.moveTo(xx[1], yy[1]);
        for (int i = 1; i <= 4; i++) lower.lineTo(xx[i] + DEPTH, yy[i] + DEPTH);
        lower.lineTo(xx[4], yy[4]);
        lower.closePath();

        g.setColor(color.darker());
        g.fill(lower);

        g.setColor(color.BLACK);
        g.draw(lower);

        g.setColor(color);
        g.fill(upper);
        g.setColor(color.BLACK);
        g.draw(upper);
        g.draw(new Line2D.Double(xx[2], yy[2], xx[2]+DEPTH, yy[2]+DEPTH));
        g.draw(new Line2D.Double(xx[3], yy[3], xx[3]+DEPTH, yy[3]+DEPTH));
      } else { // MOMENTARY
        // raised square
        int[] xp = new int[] { x, x + w - DEPTH, x + w, x + w, x + DEPTH, x };
        int[] yp = new int[] { y, y, y + DEPTH, y + h, y + h, y + h - DEPTH };
        g.setColor(color.darker());
        g.fillPolygon(xp, yp, xp.length);
        g.setColor(color);
        g.fillRect(x, y, w - DEPTH, h - DEPTH);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, w - DEPTH, h - DEPTH);
        g.drawLine(x + w - DEPTH, y + h - DEPTH, x + w, y + h);
        g.drawPolygon(xp, yp, xp.length);
      }
    }

    // draw label, possibly shifted
    g.translate(labelOffset, labelOffset-DEPTH);
    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();
    g.translate(-labelOffset, -labelOffset+DEPTH);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState circState) {
    State state = (State)circState.getData();
    AttributeOption behavior = circState.getAttributeValue(ATTR_BEHAVIOR);
    AttributeOption clocking = circState.getAttributeValue(ATTR_CLOCKING);
    Value resting, active;
    if (behavior == BEHAVIOR_MOMENTARY_NC || behavior == BEHAVIOR_ONESHOT_NC ) {
      resting = Value.TRUE;
      active = Value.FALSE;
    } else {
      resting = Value.FALSE;
      active = Value.TRUE;
    }
    if (state == null) {
      circState.setPort(0, resting, 1);
      return;
    }

    Value newValue;
    if (clocking == CLOCKING_SYNCHRONOUS) {
      AttributeOption trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
      Value clock = circState.getPortValue(1);
      synchronized (state) {
        if (state.value == null)
          state.value = resting; // may be overwritten below
        Value lastClock = state.setLastClock(clock);
        boolean go;
        if (trigger == StdAttr.TRIG_FALLING) {
          go = lastClock == Value.TRUE && clock == Value.FALSE;
        } else {
          go = lastClock == Value.FALSE && clock == Value.TRUE;
        }
        if (go) {
          if (behavior == BEHAVIOR_LATCHING) {
            // Latching, synchronous
            if (state.pressedRecently) {
              state.pressedRecently = false; // reset
              state.value = (state.value == Value.TRUE ? Value.FALSE : Value.TRUE);
            }
          } else if (behavior == BEHAVIOR_ONESHOT_NC || behavior == BEHAVIOR_ONESHOT_NO) {
            if (state.pressedRecently) {
              state.pressedRecently = false; // reset
              state.value = active;
            } else {
              state.value = resting;
            }
          } else { // BEHAVIOR_MOMENTARY_NC || BEHAVIOR_MOMENTARY_NO
            state.pressedRecently = false; // reset, not used
            state.value = state.pressed ? active : resting;
          }
        } // end clock trigger
        newValue = state.value;
      } // end synchronized
    } else { // CLOCKING_ASYNCHRONOUS
      synchronized (state) { 
        if (state.value == null)
          state.value = resting; // may be overwritten below
        if (behavior == BEHAVIOR_LATCHING) {
          if (state.pressedRecently) {
              state.pressedRecently = false; // reset
              state.value = (state.value == Value.TRUE ? Value.FALSE : Value.TRUE);
          }
        } else if (behavior == BEHAVIOR_ONESHOT_NC || behavior == BEHAVIOR_ONESHOT_NO) {
          // This is an unusual case: no other component in logisim
          // causes spontanous changes to outputs like this.
          if (state.pressedRecently) {
            state.pressedRecently = false; // reset
            state.value = active; // not used
            circState.setPort(0, active, 1);
            state.value = resting; // not used
            circState.setPort(0, resting, 30); // hopefully redraws?
            return; // do not call setPort() below
          }
        } else { // BEHAVIOR_MOMENTARY_NC || BEHAVIOR_MOMENTARY_NO
          state.pressedRecently = false; // reset, not used
          state.value = state.pressed ? active : resting;
        }
        newValue = state.value;
      } // end synchronized
    }
    circState.setPort(0, newValue, 1);
  }
  
  private static class State implements InstanceData, Cloneable {

    Value lastClock = Value.UNKNOWN;
    Value value; // current value
    boolean pressed;
    boolean pressedRecently;

    public State() { }

    public Value setLastClock(Value newClock) {
      Value ret = lastClock;
      lastClock = newClock;
      return ret;
    }

    @Override
    public Object clone() {
      try {
        return super.clone();
      } catch (CloneNotSupportedException e) {
        return null;
      }
    }

  }

}
