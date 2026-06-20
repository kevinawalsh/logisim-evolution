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

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.bfh.logisim.netlist.NetlistComponent;
import com.cburch.logisim.comp.ComponentData;
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

public class TimedPulse extends InstanceFactory {

  // This component periodically (at a rate chosen by the user, specified in
  // real time units like seconds, milliseconds, Hz, kHz, etc.) sends a high
  // pulse for one "cycle", where "cycle" can mean:
  //  * from one rising edge of the connected clock, to the next rising edge
  //  * from one falling edge of the connected clock, to the next falling edge
  //  * for the duration of the high phase of the connected clock
  //  * for the duration of the low phase of the connected clock
  //
  // In all cases, there is a slight delay, which would normally ensure that
  // before the timer pulses arrive at other components, clock signals will
  // arrive at those components. This allows a timer pulse to be used reliably
  // as a register's clock enable, for example, even when the timer pulse
  // changes at the same clock edge as the register uses for it's trigger.
  // Without any delay, we might worry that the timer pulse's 0->1 transition
  // arrives a little too early (causing the register to be enabled one cycle
  // earlier than expected), or the timer pulse's 1->0 transition arrives a
  // little too early (causing the register to be disabled on the cycle it was
  // intended to be enabled).

  static final int DELAY = 3; // Most primitive gates use DELAY=1. Hopefully 3 is plenty but not too
                              // much. In theory, any delay should work, since clocks should be
                              // processed, and propagate, before any TimedPulse or other component.

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
    setAttributes(new Attribute[] { ATTR_INTERVAL, ATTR_UNIT, StdAttr.TRIGGER, 
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] { 10.0, HZ, StdAttr.TRIG_RISING,
        "", Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setIconName("timedpulse.png");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
	}

  static final int OUT = 0;
  static final int CLK = 1;
	
	@Override
	protected void configureNewInstance(Instance instance) {
    instance.setPorts(new Port[] {
      new Port(0, 0, Port.OUTPUT, BitWidth.ONE),  // OUT
      new Port(-10, 10, Port.INPUT, BitWidth.ONE) // CLK
    });
    instance.computeLabelTextField(Instance.AVOID_LEFT | Instance.AVOID_TOP);
		instance.addAttributeListener();
	}
	
  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_LEFT | Instance.AVOID_TOP);
    }
  }

	@Override
	public Bounds getOffsetBounds(AttributeSet attrs) {
    return Bounds.create(-20, -10, 20, 20);
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
		painter.drawPort(OUT);
		painter.drawClock(CLK, Direction.NORTH);
	}

  private static class State implements ComponentData {
    long lastPulseStartNanos;
    boolean active;
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
    long now = System.nanoTime();

    Value clk = state.getPortValue(CLK);
    if (clk != Value.TRUE && clk != Value.FALSE) {
      state.setPort(OUT, Value.ERROR, DELAY);
      return;
    }

    double interval = state.getAttributeValue(ATTR_INTERVAL);
    AttributeOption unit = state.getAttributeValue(ATTR_UNIT);
    AttributeOption trigger = state.getAttributeValue(StdAttr.TRIGGER);
    long delta = delta(interval, unit);

    State s = (State)state.getDataAsCustom();
    if (s == null) {
      s = new State();
      // initialize as if a pulse occurred a little while ago
      s.active = false;
      s.lastPulseStartNanos = now - delta/2;
      state.setData(s);
    }

    Value lastClock = s.setLastClock(clk);
    boolean ticked = (lastClock != clk);
    // Normally, ticked=true, unless we got a spurrious propagate invoation (in
    // which case we should aim to be idempotent).

    if (s.active) {
      // pulse is active
      if (trigger == StdAttr.TRIG_HIGH)
        s.active = (!ticked && clk == Value.TRUE); // either condition alone would work here
      else if (trigger == StdAttr.TRIG_LOW)
        s.active = (!ticked && clk == Value.FALSE); // either condition alone would work here
      else if (trigger == StdAttr.TRIG_FALLING)
        s.active = !(ticked && clk == Value.FALSE); // both conditions needed here
      else
        s.active = !(ticked && clk == Value.TRUE); // both conditions needed here
    }

    if (!s.active) {
      // pulse was already inactive, or just became inactive
      boolean go;
      if (trigger == StdAttr.TRIG_FALLING || trigger == StdAttr.TRIG_LOW)
        go = lastClock == Value.TRUE && clk == Value.FALSE;
      else
        go = lastClock == Value.FALSE && clk == Value.TRUE;
      // if clock is a go, and enough time has elapsed, begin a pulse
      if (go && now - s.lastPulseStartNanos >= delta) {
        s.active = true;
        s.lastPulseStartNanos += delta;
        if (now - s.lastPulseStartNanos > delta)
          s.lastPulseStartNanos = now - delta;
      }
    }

    state.setPort(OUT, s.active ? Value.TRUE : Value.FALSE, DELAY);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new TimerPulseHDLGenerator(ctx);
  }

  private static class TimerPulseHDLGenerator extends HDLGenerator {

    public TimerPulseHDLGenerator(ComponentContext ctx) {
      super(ctx, "bfh", "TimedPulse", "i_Timer");

      clockPort = new ClockPortInfo("GlobalClock", "ClockEnable", CLK);
      outPorts.add("Pulse", 1, OUT, null);

      // TODO: Ideally, we could determine the rate of the clock connected to
      // this TimedPulse component. This is not necessarily the same as the
      // underlying fpga oscillator, because (a) the user may have chosen an
      // option other than "max frequency" in the fpga options window, and (b)
      // within the user's circuit the clock component connected to this
      // TimedPulse component may have custom high:low:phase parameters, making
      // it run slower than the other clocks components with the default 1:1:0
      // parameters. If either or both of those occur, this TimedPulse
      // component's behavior should be based on the connected clock's behavior.
      //
      // Special case 1: within the user's circuit, the signal connected to the
      // clock input might no be directly from a Clock component. That is, the
      // user has "gated the clock signal", against Logisim recommendations, or
      // is using some ill-advised logic or inputs to drive the timer's clock
      // input. In this case, there may not be a fixed frequency, or even a
      // well-defined notion of frequency at all, for the timer's incoming
      // clock.
      //
      // Special case 2: if the user's circuit has a "dynamic clock control"
      // component, then the frequency of the timer's clock input might change
      // dynamically. So again in this case, there is no fixed frequency for the
      // timer's incoming clock.
      //
      // Workaround: For now, let's drive our counter entirely by the underlying
      // fpga oscillator, which has a fixed, known frequency, and from which we
      // can determine the necessary counter bit-width and target counter value
      // to get the desired pulse-to-pulse timing.
      //
      // Possible sketch of HDL:
      //  * a register, of the necessary bit width, counting from 0 upwards to
      //    target (or target-1?) then resetting back to 0. Maybe we count to
      //    target-1, and reset based on the "go" register, to avoid having the
      //    addition and comparison take place in the same critical path?
      //  * three other registers, 1-bit each:
      //    - "go", driven by the raw fpga clock. A 1 in "go" acts as a token
      //      that indicates that it is time for another pulse.
      //    - "pending", driven by the raw fpga clock. A 1 in "pending" also
      //      acts as a token to indicates that a pulse will be generated during
      //      the next user-clock cycle
      //    - "active", driven by the connected user-clock input signal (i.e. by
      //      GlobalClock and ClockEnable), which indicates that this user-clock
      //      cycle is one in which a pulse occurs.
      //  * Rationale: depending on the relative speeds of the user-clock and
      //    the target pulse rate, we don't need "go" tokens piling up faster
      //    than pulses can be generated. On the other hand, since a pulse might
      //    be only slightly slower than "go" tokens get generated, we don't
      //    want to miss a "go" token simply because an earlier pulse was still
      //    in progress. So "go" is basically a queue of waiting pulses, but we
      //    cap the queue length at 1 token.
      //  * "go" gets set to 1 whenever the counter value equals target (or
      //     target-1?), and it is stiky, keeping that 1 until it can be moved into
      //     "pending".
      //  * Whenever "pending" is 0, or about to become 0, it grabs a new token,
      //    if available, from "go" (clearing "go" in the process, unless "go"
      //    is about to be set to 1 again because of the counter value reaching
      //    the target).
      //  * "active" only changes when triggered by the user-clock, on either
      //    rising or on falling edges (as appropriate, depending on which edge
      //    the user wants the pulses to be on). It always just grabs a token
      //    from "pending" and clears the pending register (unless pending is
      //    getting set to 1 again because of the state of "go").
      //  * The final output is either:
      //    - the value of "active" (for edge-triggered pulses)
      //    - the value of "active" AND'ed with the user clock or its inverse
      //      (for level-sensitive pulses).
      //
      // Special case: if the target count is determined to be 0, that means the
      // user wants pulses all the time, and we don't need a counter at all. The
      // output can just be either: 1 (for edge-triggered cases), or the input
      // clock signal (for TRIG_HIGH cases), or the inverted input clock signal
      // (for TRIG_LOW) cases.
      //
      // Special case: if the user-clock is equivalent to the raw fpga clock
      // (i.e. the user selected "max frequency" in the fpga options window, and
      // isn't using unusual parameters for the connected Clock component), we
      // can probably simplify some of this, perhaps collapsing "active" and
      // "pending" into a single register. But it probably doesn't really
      // matter?
    }

    @Override
    protected void generateGenerator(Hdl out, NetlistComponent comp) {
      // TODO: Generate HDL code.
      //
      // * See std/mem/Register's HDL generation code (or std/mem/Counter's)
      //   for inspiration.
      //
      // * Also see std/io/Keyboard or std/io/Tty for details about crossing
      //   clock domains, since that is relevant here.
    }

  }

}
