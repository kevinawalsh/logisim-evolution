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
package com.bfh.logisim.library;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;

import com.bfh.logisim.hdlgenerator.HDLInliner;
import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.bfh.logisim.netlist.NetlistComponent;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class TimedPulse extends InstanceFactory implements Circuit.TickSubscriber {

  static final AttributeOption MHZ = new AttributeOption("MHz", S.unlocalized("MHz"));
  static final AttributeOption KHZ = new AttributeOption("kHz", S.unlocalized("kHz"));
  static final AttributeOption HZ = new AttributeOption("Hz", S.unlocalized("Hz"));
  static final AttributeOption NSEC = new AttributeOption("ns", S.unlocalized("nanoseconds (ns)"));
  static final AttributeOption USEC = new AttributeOption("us", S.unlocalized("microseconds (us)"));
  static final AttributeOption MSEC = new AttributeOption("ms", S.unlocalized("milliseconds (ms)"));
  static final AttributeOption SEC = new AttributeOption("s", S.unlocalized("seconds"));
  static final Attribute<AttributeOption> ATTR_UNIT = Attributes.forOption(
      "unit", S.getter("timedPulseUnit"), new AttributeOption[] { SEC, MSEC, USEC, NSEC, HZ, KHZ, MHZ });

  static final Attribute<Double> ATTR_INTERVAL = Attributes.forDoubleRange(
      "interval", S.getter("timedPulseInterval"), Double.MIN_VALUE, 10.0, Double.MAX_VALUE);

	public TimedPulse() {
		super("TimedPulse", S.getter("timedPulseComponent"));
    // We pulse high for one "cycle", where "cycle" can mean:
    //  * from one rising edge of the default 1:1:0 clock, to the next rising edge
    //  * from one falling edge of the default 1:1:0 clock, to the next falling edge
    //  * for the duration of the high phase of the default 1:1:0 clock
    //  * for the duration of the low phase of the default 1:1:0 clock
    // FIXME: maybe we also need to allow customizable high:low:phase parameters?
    setAttributes(new Attribute[] { StdAttr.FACING, ATTR_INTERVAL, ATTR_UNIT, StdAttr.TRIGGER, 
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] { Direction.EAST, 10.0, HZ, StdAttr.TRIG_RISING,
        "", Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setIconName("timedpulse.png");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
	}
	
	@Override
	protected void configureNewInstance(Instance instance) {
    instance.setPorts(new Port[] { new Port(0, 0, Port.OUTPUT, BitWidth.ONE) });
    instance.computeLabelTextField(Instance.AVOID_LEFT);
		instance.addAttributeListener();
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

	@Override
	public Bounds getOffsetBounds(AttributeSet attrs) {
    int w = 20, h = 20;
    Direction dir = attrs.getValue(StdAttr.FACING);
    if (dir == Direction.WEST)
      return Bounds.create(0, -h/2, w, h);
    else if (dir == Direction.SOUTH)
      return Bounds.create(-w/2, -h, w, h);
    else if (dir == Direction.NORTH)
      return Bounds.create(-w/2, 0, w, h);
    else
      return Bounds.create(-w, -h/2, w, h);
	}

  @Override
	public void paintInstance(InstancePainter painter) {
		Graphics g = painter.getGraphics();
		// BitWidth nrofbits=painter.getAttributeValue(TimedPulse.ATTR_BinBits);
		// int NrOfPorts = (int)(Math.log10(Math.pow(2.0,nrofbits.getWidth()))+1.0);
    Bounds bds = painter.getNominalBounds();
    int x = bds.x, y = bds.y, w = bds.width, h = bds.height;

    GraphicsUtil.switchToWidth(g, 1.5f);
		g.setColor(Color.BLACK);
    g.drawPolyline(
        new int[] { x+4,  x+8,  x+8,  x+12, x+12, x+16 },
        new int[] { y+10, y+10, y+3,  y+3,  y+10, y+10 }, 6);

    painter.drawLabel();
		painter.drawBounds();
		painter.drawPorts();
	}

  private static class State implements ComponentData {
    long lastPulseStartNanos;
    boolean active;
    int tickCountAtActivation;
    Value lastClock = Value.UNKNOWN;

    public Value setLastClock(Value newClock) {
      Value ret = lastClock;
      lastClock = newClock;
      return ret;
    }

    @Override
    public State duplicateForNewSimulation() {
      State dup = new State();
      dup.lastClock = this.lastClock;
      dup.lastPulseStartNanos = this.lastPulseStartNanos;
      dup.active = this.active;
      dup.tickCountAtActivation = this.tickCountAtActivation;
      return dup;
    }
  }

  static long delta(double interval, AttributeOption unit) {
    if (unit == SEC)  return (long)(interval * 1_000_000_000.0);
    if (unit == MSEC) return (long)(interval * 1_000_000.0);
    if (unit == USEC) return (long)(interval * 1_000.0);
    if (unit == NSEC) return (long)(interval);
    if (unit == HZ)   return (long)(1_000_000_000.0 / interval);
    if (unit == KHZ)  return (long)(1_000_000.0 / interval);
    /* MHZ */         return (long)(1_000.0 / interval);
  }

	@Override
	public void propagate(InstanceState state) {
    State s = (State)state.getDataAsCustom();
    state.setPort(0, s != null && s.active ? Value.TRUE : Value.FALSE, 1);
	}

  @Override
  public boolean tick(CircuitState cs, int tickCount, Component comp) {
    boolean dirty = false;
    long now = System.nanoTime();

    AttributeSet attrs = comp.getAttributeSet();
    double interval = attrs.getValue(ATTR_INTERVAL);
    AttributeOption unit = attrs.getValue(ATTR_UNIT);
    AttributeOption trigger = attrs.getValue(StdAttr.TRIGGER);
    long delta = delta(interval, unit);

    State s = (State)cs.getDataAsCustom(comp);
    if (s == null) {
      s = new State();
      // initialize as if a pulse occurred a little while ago
      s.active = false;
      s.lastPulseStartNanos = now - delta/2;
      cs.setData(comp, s);
      dirty = true;
    }

    int clockLevel = (tickCount % 2); // FIXME: move this calculation to Clock, to ensure it
                                         // stays in sync with the calculations there.
    Value clock = clockLevel == 1 ? Value.TRUE : Value.FALSE;
    Value lastClock = s.setLastClock(clock);

    if (s.active) {
      // pulse is active
      boolean a;
      if (trigger == StdAttr.TRIG_HIGH)
        a = (tickCount == s.tickCountAtActivation && clockLevel == 1);
      else if (trigger == StdAttr.TRIG_LOW)
        a = (tickCount == s.tickCountAtActivation && clockLevel == 0);
      else if (trigger == StdAttr.TRIG_FALLING)
        a = (tickCount == s.tickCountAtActivation && clockLevel == 0) ||
          (tickCount == s.tickCountAtActivation+1 && clockLevel == 1);
      else
        a = (tickCount == s.tickCountAtActivation && clockLevel == 1) ||
          (tickCount == s.tickCountAtActivation+1 && clockLevel == 0);
      if (s.active != a) {
        s.active = a;
        dirty = true;
      }
    }

    if (!s.active) {
      // pulse was already inactive, or just became inactive
      boolean go;
      if (trigger == StdAttr.TRIG_FALLING || trigger == StdAttr.TRIG_LOW)
        go = lastClock == Value.TRUE && clock == Value.FALSE;
      else
        go = lastClock == Value.FALSE && clock == Value.TRUE;
      // if clock is a go, and enough time has elapsed, begin a pulse
      if (go && now - s.lastPulseStartNanos >= delta) {
        s.active = true;
        s.tickCountAtActivation = tickCount;
        s.lastPulseStartNanos += delta;
        if (now - s.lastPulseStartNanos > delta)
          s.lastPulseStartNanos = now - delta;
        dirty = true;
      }
    }

    return dirty;
  }

  @Override
  public Object getFeature(Object key, AttributeSet attrs) {
    if (key == ComponentFactory.TICK_SUBSCRIPTION)
      return (Circuit.TickSubscriber)this;
    return super.getFeature(key, attrs);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    // return new TimerPulseHDLInliner(ctx);
    return null;
  }

  private static class TimerPulseHDLInliner extends HDLInliner {

    public TimerPulseHDLInliner(ComponentContext ctx) {
      super(ctx);
    }

    @Override
    protected void generateInlinedCode(Hdl out, NetlistComponent comp) {
      // TODO
      // Net outNet = comp.getConnection(0);
      // if (net != null) {
      //   int clkid = _nets.getClockId(net);
      //   out.assign(net.name, CLK_TREE_NET + clkid, CLK_USR);
      // }
    }

  }

}
