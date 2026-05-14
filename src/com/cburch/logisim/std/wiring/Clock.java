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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import javax.swing.Icon;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
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
import com.cburch.logisim.util.Icons;
import com.cburch.logisim.tools.key.DirectionConfigurator;

public class Clock extends InstanceFactory {
  public static class ClockLogger extends InstanceLogger {
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
      ClockState s = getState(state);
      return s.sending;
    }

    @Override
    public boolean isInput(InstanceState state, Object option) {
      return true;
    }
  }

  public static class ClockPoker extends InstancePoker {
    boolean isPressed = true;

    private boolean isInside(InstanceState state, MouseEvent e) {
      Bounds bds = state.getInstance().getNominalBounds();
      return bds.contains(e.getX(), e.getY());
    }

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      isPressed = isInside(state, e);
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      // special case: for synthetic "by label" mouse release, mousePressed is
      // on different caret, so isPressed will be uninitialized
      if ((isPressed && isInside(state, e)) || (e.getX() == -1 && e.getY() == -1))
        state.getProject().getSimulator().tick(1); // all clocks tick together
      isPressed = false;
    }
  }

  private static class ClockState implements ComponentData {
    Value sending = Value.UNKNOWN;
    int currentTick;

    ClockState(int curTick, AttributeSet attrs) {
      updateTick(curTick, attrs);
    }

    ClockState(ClockState other) {
      sending = other.sending;
      currentTick = other.currentTick;
    }

    boolean updateTick(int ticks, AttributeSet attrs) {
      int durationHigh = attrs.getValue(ATTR_HIGH).intValue();
      int durationLow = attrs.getValue(ATTR_LOW).intValue();
      int cycle = durationHigh + durationLow;
      int phase = ((attrs.getValue(ATTR_PHASE).intValue() % cycle) + cycle) % cycle;
      boolean isLow = ((ticks + phase) % cycle) < durationLow;
      Value desired = (isLow ? Value.FALSE : Value.TRUE);
      if (sending.equals(desired))
        return false;
      sending = desired;
      return true;
    }

    @Override
    public ClockState duplicateForNewSimulation() {
      return new ClockState(this);
    }
  }

  private static ClockState getState(InstanceState state) {
    ClockState ret = (ClockState) state.getDataAsCustom();
    if (ret == null) {
      ret = new ClockState(state.getTickCount(), state.getAttributeSet());
      state.setData(ret);
    }
    return ret;
  }

  public static boolean tick(CircuitState circState, int ticks, Component comp) {
    AttributeSet attrs = comp.getAttributeSet();
    ClockState state = (ClockState) circState.getDataAsCustom(comp);
    boolean dirty = false;
    if (state == null) {
      state = new ClockState(ticks, attrs);
      circState.setData(comp, state);
      dirty = true;
    } else {
      dirty = state.updateTick(ticks, attrs);
    }
    // if (dirty)
    //   Instance.getInstanceFor(comp).fireInvalidated();
    return dirty;
  }

  public static final Attribute<Integer> ATTR_HIGH = new DurationAttribute(
      "highDuration", S.getter("clockHighAttr"), 1,
      Integer.MAX_VALUE);

  public static final Attribute<Integer> ATTR_LOW = new DurationAttribute(
      "lowDuration", S.getter("clockLowAttr"), 1, Integer.MAX_VALUE);

  public static final Attribute<Integer> ATTR_PHASE = new DurationAttribute(
      "phaseOffset", S.getter("clockPhaseAttr"), 0, Integer.MAX_VALUE);

  public static final Clock FACTORY = new Clock();

  private static final Icon toolIcon = Icons.getIcon("clock.gif");

  public Clock() {
    super("Clock", S.getter("clockComponent"));
    setAttributes(
        new Attribute[] { StdAttr.FACING, ATTR_HIGH, ATTR_LOW, ATTR_PHASE, 
          StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
          new Object[] { Direction.EAST, Integer.valueOf(1),
            Integer.valueOf(1), Integer.valueOf(0), "", Direction.WEST,
            StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setInstanceLogger(ClockLogger.class);
    setInstancePoker(ClockPoker.class);
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.setPorts(new Port[] { new Port(0, 0, Port.OUTPUT, BitWidth.ONE) });
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    return Probe.getOffsetBounds(attrs.getValue(StdAttr.FACING),
        BitWidth.ONE, RadixOption.RADIX_2);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    // This returns support for CircuitHDLGenerator, etc.
    // Elsewhere, TopLevelHDLGenerator uses ClockHDLGenerator.CounterPart().
    return new ClockHDLGenerator.StubPart(ctx);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    }
  }

  //
  // graphics methods
  //
  @Override
  public void paintIcon(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(painter.getDestination(), g, 2, 2);
    } else {
      g.drawRect(4, 4, 13, 13);
      g.setColor(Value.FALSE.getColor());
      g.drawPolyline(new int[] { 6, 6, 10, 10, 14, 14 }, new int[] { 10,
        6, 6, 14, 14, 10 }, 6);
    }

    Direction dir = painter.getAttributeValue(StdAttr.FACING);
    int pinx = 15;
    int piny = 8;
    if (dir == Direction.EAST) { // keep defaults
    } else if (dir == Direction.WEST) {
      pinx = 3;
    } else if (dir == Direction.NORTH) {
      pinx = 8;
      piny = 3;
    } else if (dir == Direction.SOUTH) {
      pinx = 8;
      piny = 15;
    }
    g.setColor(Value.TRUE.getColor());
    g.fillOval(pinx, piny, 3, 3);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getInstance().getNominalBounds(); // intentionally with no
    // graphics object - we don't want label included
    int x = bds.x;
    int y = bds.y;
    int w = bds.width;
    int h = bds.height;
    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.BLACK);
    g.drawRect(x, y, w, h);

    painter.drawLabel();

    int durationHigh = painter.getAttributeValue(ATTR_HIGH).intValue();
    int durationLow = painter.getAttributeValue(ATTR_LOW).intValue();
    if (durationHigh != 1 || durationLow != 1) {
      int cycle = durationHigh + durationLow;
      int phase = ((painter.getAttributeValue(ATTR_PHASE).intValue() % cycle) + cycle) % cycle;
      String txt1 = String.format("%d:%d", durationHigh, durationLow);
      String txt2 = String.format("+%d", phase);
      String label = painter.getAttributeValue(StdAttr.LABEL);
      Object ldir = (label != null && !label.trim().isEmpty()) ?
          painter.getAttributeValue(StdAttr.LABEL_LOC) : null;
      Direction pdir = painter.getAttributeValue(StdAttr.FACING);
      Direction dir = pdir == Direction.NORTH || 
          pdir == Direction.SOUTH ? Direction.EAST : Direction.SOUTH;
      if (dir == ldir)
        dir = dir.reverse();
      if (dir == Direction.NORTH) {
        GraphicsUtil.drawText(g, PARAM_FONT, txt1,
            x+w/2, y-10, GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM_FIRST);
        GraphicsUtil.drawText(g, PARAM_FONT, txt2,
            x+w/2, y-14, GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
      } else if (dir == Direction.EAST) {
        GraphicsUtil.drawText(g, PARAM_FONT, txt1,
            x+w+2, y+h/2+1, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM_FIRST);
        GraphicsUtil.drawText(g, PARAM_FONT, txt2,
            x+w+2, y+h/2-1, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
      } else if (dir == Direction.SOUTH) {
        GraphicsUtil.drawText(g, PARAM_FONT, txt1,
            x+w/2, y+h+14, GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM_FIRST);
        GraphicsUtil.drawText(g, PARAM_FONT, txt2,
            x+w/2, y+h+10, GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
      } else {
        GraphicsUtil.drawText(g, PARAM_FONT, txt1,
            x-2, y+h/2+1, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BOTTOM_FIRST);
        GraphicsUtil.drawText(g, PARAM_FONT, txt2,
            x-2, y+h/2-1, GraphicsUtil.H_RIGHT, GraphicsUtil.V_TOP);
      }
    }

    boolean drawUp;
    if (painter.getShowState()) {
      ClockState state = getState(painter);
      g.setColor(state.sending.getColor());
      drawUp = state.sending == Value.TRUE;
    } else {
      g.setColor(Color.BLACK);
      drawUp = true;
    }
    x += 10;
    y += 10;
    int[] xs = { x - 6, x - 6, x, x, x + 6, x + 6 };
    int[] ys;
    if (drawUp) {
      ys = new int[] { y, y - 4, y - 4, y + 4, y + 4, y };
    } else {
      ys = new int[] { y, y + 4, y + 4, y - 4, y - 4, y };
    }
    g.drawPolyline(xs, ys, xs.length);

    painter.drawPorts();
  }

  private static final Font PARAM_FONT = new Font("SansSerif", Font.BOLD, 10);

  @Override
  public void propagate(InstanceState state) {
    Value val = state.getPortValue(0);
    ClockState q = getState(state);
    if (!val.equals(q.sending)) { // ignore if no change
      state.setPort(0, q.sending, 1);
    }
  }
}
