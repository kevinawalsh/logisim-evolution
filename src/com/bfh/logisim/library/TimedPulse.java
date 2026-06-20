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

      AttributeOption trigger = _attrs.getValue(StdAttr.TRIGGER);
      if (trigger == StdAttr.TRIG_HIGH || trigger == StdAttr.TRIG_LOW) {
        _err.AddFatalError("TimedPulse: TRIG_HIGH and TRIG_LOW are not supported for HDL "
            + "synthesis. Use TRIG_RISING or TRIG_FALLING instead.");
        return;
      }

      // The counter is driven by GlobalClock, which always oscillates at oscFreq Hz
      // for TRIG_RISING/FALLING — it is FPGA_CLKp or FPGA_CLKn depending on mode,
      // but both run at the same rate. The getClockPortMappings() adapter handles
      // the inversion so this module always uses posedge GlobalClock.
      // ClockEnable pulses once per user-clock period (POS_EDGE or NEG_EDGE, or
      // constant 1 in raw mode), synchronizing the output to the user's clock domain.
      double interval = _attrs.getValue(ATTR_INTERVAL);
      AttributeOption unit = _attrs.getValue(ATTR_UNIT);
      long intervalNanos = delta(interval, unit);
      long maxCount = intervalNanos * ctx.oscFreq / 1_000_000_000L;
      maxCount = Math.max(1, maxCount);
      if (maxCount > Integer.MAX_VALUE) {
        _err.AddSevereWarning("TimedPulse: interval is too long for HDL synthesis at "
            + "this oscillator frequency. Clamping MaxCount to 32-bit limit.");
        maxCount = Integer.MAX_VALUE;
      }

      int ctrWidth = 0;
      for (long p = maxCount; p > 0; p >>= 1) ctrWidth++;

      parameters.add("CtrWidth", ctrWidth);
      parameters.add("MaxCount", (int) maxCount);

      String ctrInit = ctx.hdl.isVhdl
          ? "std_logic_vector(to_unsigned(0, CtrWidth))" : "0";
      registers.add("s_count_reg", "CtrWidth", ctrInit);
      registers.add("s_go_reg",    1, ctx.hdl.zero);
      wires.add("s_wrap",      1);
      wires.add("s_go_taking", 1);
    }

    @Override
    protected void generateBehavior(Hdl out) {
      // GlobalClock oscillates at oscFreq Hz in all supported modes:
      //   TRIG_RISING, slow mode:  GlobalClock = FPGA_CLKp, ClockEnable = POS_EDGE
      //   TRIG_RISING, raw mode:   GlobalClock = FPGA_CLKp, ClockEnable = 1 (always)
      //   TRIG_FALLING, slow mode: GlobalClock = FPGA_CLKp, ClockEnable = NEG_EDGE
      //   TRIG_FALLING, raw mode:  GlobalClock = FPGA_CLKn, ClockEnable = 1 (always)
      // In the last case, the adapter in getClockPortMappings() inverts FPGA_CLKp
      // before passing it in, so the module always uses posedge GlobalClock without
      // needing to know which physical edge it is. The counter counts at oscFreq Hz
      // in all four cases.
      //
      // s_go is a sticky 1-token queue: set when the counter wraps, held until
      // ClockEnable fires. Pulse is driven combinationally as s_go & ClockEnable
      // so it is visible on the same cycle that ClockEnable fires. This is
      // essential: downstream components (e.g. a Counter) gate their own
      // ClockEnable with Pulse, so both must be high simultaneously.
      // If the counter wraps again while s_go is already set, the token is
      // preserved (not lost and not doubled); excess wraps while s_go=1 are
      // silently merged into the existing token (queue depth = 1).
      if (out.isVhdl) {
        out.stmt("s_wrap      <= '1' WHEN s_count_reg = std_logic_vector(to_unsigned(MaxCount-1, CtrWidth)) ELSE '0';");
        out.stmt("s_go_taking <= s_go_reg AND ClockEnable;");
        out.stmt("");
        out.stmt("make_pulse : PROCESS(GlobalClock)");
        out.stmt("BEGIN");
        out.stmt("   IF (GlobalClock'event AND GlobalClock = '1') THEN");
        out.stmt("      IF (s_wrap = '1') THEN");
        out.stmt("         s_count_reg <= (others => '0');");
        out.stmt("      ELSE");
        out.stmt("         s_count_reg <= std_logic_vector(unsigned(s_count_reg) + 1);");
        out.stmt("      END IF;");
        out.stmt("      s_go_reg     <= s_wrap OR (s_go_reg AND NOT s_go_taking);");
        out.stmt("   END IF;");
        out.stmt("END PROCESS make_pulse;");
        out.stmt("");
        out.stmt("Pulse <= s_go_taking;");
      } else {
        out.stmt("assign s_wrap      = (s_count_reg == MaxCount - 1);");
        out.stmt("assign s_go_taking = s_go_reg & ClockEnable;");
        out.stmt("");
        out.stmt("always @(posedge GlobalClock)");
        out.stmt("begin");
        out.stmt("   s_count_reg  <= s_wrap ? 0 : s_count_reg + 1;");
        out.stmt("   s_go_reg     <= s_wrap | (s_go_reg & ~s_go_taking);");
        out.stmt("end");
        out.stmt("");
        out.stmt("assign Pulse = s_go_taking;");
      }
    }

  }

}
