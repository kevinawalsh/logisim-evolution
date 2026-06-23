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

package com.bfh.logisim.hdlgenerator;

import com.bfh.logisim.netlist.NetlistComponent;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.std.bfh.DynamicClock;

public class TickHDLGenerator extends HDLGenerator {

  public static final String FPGA_CLK_NET = "FPGA_CLK";
  public static final String FPGA_CLKp_NET = "FPGA_CLKp";
  public static final String FPGA_CLKn_NET = "FPGA_CLKn";
  public static final String FPGA_TICK_NET = "FPGA_Tick";
  public static final String FPGA_DYNCLK_PERIOD_NET = "s_LOGISIM_DYNAMIC_CLOCK";

  // Note: The discussion below should be checked against recommended best
  // practices. For example, see:
  // https://www.intel.cn/content/dam/altera-www/global/zh_CN/pdfs/literature/hb/qts/qts_qii51006.pdf
  // (starting on page 8, "Internally Generated Clocks")
  //
  // Generates periodic ticks meant to be used as a clock enable by other
  // components.
  // * In full-speed mode (period=0), the tick is high all the time, and a
  //   special "raw" mode is enabled.
  // * When period>0, downstream Clock components use only the rising edges to
  //   count high and low periods, so even with 1:1 high:low shape, the fastest
  //   case, things downstream from them run at half speed. In this component,
  //   we can slow things down further by a factor of 1/N, where period=N. So
  //   the overall slowdown is by a factor of 1/(2N).
  // * So when period=1, we get a slowdown of 1/2: the tick is high all the time
  //   (just as in raw mode), but the lack of "raw" mode means the downstream
  //   Clock components (assuming 1:1 shape) use only rising edges leading to
  //   half the transitions being ignored.
  // * When period=2, the slowdown is 1/4, with tick high every other period.
  // * When period=3, the slowdown is 1/6, with tick high every third period.
  //
  // Dynamic-speed mode works basically the same, but the period is adjustable
  // via a "Dynamic Clock Speed" component within the top-level circuit. The
  // tick signal transitions on the rising edge of the raw clock, driven by a
  // register to reduce noise.
  //
  // The outputs of this component are:
  //   FPGA_CLKp - the raw fpga clock
  //   FPGA_CLKn - the raw fpga clock, inverted
  //   FPGA_Tick - high always or during some FPGA_CLKp rise-to-rise periods
  //
  // Logisim Clock components (ClockHDLGenerator) uses FPGA_CLKp, FPGA_CLKn, and
  // FPGA_Tick to derive clock buses (containing 6 signals) for driving clocked
  // components. 
  //
  // In "Raw=1" mode, Logisim Clock is basically a no-op, passing the underlying
  // raw fpga clock through to downstream components and allowing them to run at
  // full speed. In thise case the clock bus signals are:
  //   CLK_USR     -- Same as FPGA_CLKp (or FPGA_CLKn if phase=+1)
  //   CLK_INV     -- Same as FPGA_CLKn (or FPGA_CLKp if phase=+1)
  //   POS_EDGE    -- High always
  //   NEG_EDGE    -- High always
  //   CLK_RAW     -- Same as FPGA_CLKp
  //   CLK_INVRAW  -- Same as FPGA_CLKn
  //
  // In "Raw=0" mode (reduced-speed or dynamic-speed) modes, the clock bus
  // signals are:
  //   CLK_USR     -- Oscillates at user-chosen rate and shape
  //   CLK_INV     -- Inverse of CLK_USR
  //   POS_EDGE    -- High for one CLK_RAW rise-to-rise cycle when CLK_USR rises
  //   NEG_EDGE    -- High for one CLK_RAW rise-to-rise cycle when CLK_USR falls
  //   CLK_RAW     -- Same as FPGA_CLKp
  //   CLK_INVRAW  -- Same as FPGA_CLKn
  // These only transition at the rising edge of FPGA_CLKp, so we lose half the
  // raw clock speed in these modes no matter what. 
  //
  // Downstream components select from the six clock bus signals depending on
  // the clock discipline used for each component.
  // For TRIG_RISING:
  //    clk_signal = CLK_RAW
  //    clk_enable = POS_EDGE
  //    --> HDL coded to transition on enabled rising edges
  // For TRIG_FALLING [non-raw mode]:
  //    clk_signal = CLK_RAW
  //    clk_enable = NEG_EDGE
  //    --> HDL coded to transition on enabled rising edges
  // For TRIG_HIGH:
  //    clk_signal = CLK_USR
  //    clk_enable = 1
  //    --> HDL coded to transition on enabled high clock signal
  // For TRIG_LOW:
  //    clk_signal = CLK_INV
  //    clk_enable = 1
  //    --> HDL coded to transition on enabled high clock signal
  //
  // Note: In raw mode, the above strategies mostly work, except for two
  // problems:
  //  - When phase=1, we don't want to use CLK_RAW, but CLK_USR instead, so that
  //    it is inverted. When phase=0, either of CLK_RAW or CLK_USR works fine,
  //    as they are the same. This only matters for the edge detect cases.
  //  - For falling edge detect, we need to use CLK_INV, since both POS_EDGE and
  //    NEG_EDGE will just be constant 1 in raw mode.
  //
  // So for raw mode, the strategies are:
  // For TRIG_RISING [special case for raw mode]:
  //    clk_signal = CLK_USR = FPGA_CLKp (or FPGA_CLKn when phase=+1)
  //    clk_enable = 1
  //    --> HDL coded to transition on enabled (i.e. all) rising edges
  // For TRIG_FALLING [special case for raw mode]:
  //    clk_signal = CLK_INV = FPGA_CLKn (or FPGA_CLKp when phase=+1)
  //    clk_enable = 1
  //    --> HDL coded to transition on enabled (i.e. all) rising edges
  // For TRIG_HIGH:
  //    clk_signal = CLK_USR = FPGA_CLKp (or FPGA_CLKn when phase=+1)
  //    clk_enable = 1
  //    --> HDL coded to transition on enabled (i.e. all) high clock signal
  // For TRIG_LOW:
  //    clk_signal = CLK_INV = FPGA_CLKn (or FPGA_CLKp when phase=+1)
  //    clk_enable = 1
  //    --> HDL coded to transition on enabled (i.e. all) high clock signal
  // Here, TRIG_HIGH/TRIG_LOW will fire whenever the underlying raw clock is
  // high/low as desired. And both TRIG_RISING/TRIG_FALLING will fire on every
  // rising/falling edge of the undelrying raw clock.
  //
  // Note: the HDL for downstream components in the raw case and non-raw cases
  // are identical, and boils down to two cases:
  //    --> HDL coded to transition on enabled (i.e. all) rising edges
  //    --> HDL coded to transition on enabled (i.e. all) high clock signal
  //
  //
  // Ticker, period=3:   0     1     2     3     4     5     6     7     8
  // FPGA_CLKp           :~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@
  // FPGA_Tick           :__2_____1__/~~0~~\__2_____1__/~~0~~\__2_____1__/~~0~~\
  // enabled rising edges:                 |                 |                 |
  // ClockHDLGenerator, assuming 1:1 hi:lo shape, phase=0
  // CLK_USR             :~~~~~~~~~~~~~~~~~\_________________/~~~~~~~~~~~~~~~~~\
  // CLK_INV             :_________________/~~~~~~~~~~~~~~~~~\_________________/
  // POS_EDGE            :_____________________________/~~~~~\__________________
  // NEG_EDGE            :___________/~~~~~\_____________________________/~~~~~\
  // CLK_RAW             :~~\__/~~\__/~~\__@~~\__/~~\__/~~\__@~~\__/~~\__/~~\__@
  // TRIG_RISING events  :                                   |                  
  // TRIG_FALLING events :                 |                                   |
  //
  // Ticker, period=1:   0     1     2     3     4     5     6     7     8
  // FPGA_CLKp           :~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@
  // FPGA_Tick           :~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // enabled rising edges:     |     |     |     |     |     |     |     |     |
  // ClockHDLGenerator, assuming 1:1 hi:lo shape, phase=0
  // CLK_USR             :~~~~~\_____/~~~~~\_____/~~~~~\_____/~~~~~\_____/~~~~~\
  // CLK_INV             :_____/~~~~~\_____/~~~~~\_____/~~~~~\_____/~~~~~\_____/
  // POS_EDGE            :_____/~~~~~\_____/~~~~~\_____/~~~~~\_____/~~~~~\_____/
  // NEG_EDGE            :~~~~~\_____/~~~~~\_____/~~~~~\_____/~~~~~\_____/~~~~~\
  // CLK_RAW             :~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@
  // TRIG_RISING events  :           |           |           |           |
  // TRIG_FALLING events :     |           |           |           |           |
  //
  // Raw Ticker,period=0 0     1     2     3     4     5     6     7     8
  // FPGA_CLKp           :~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@
  // FPGA_Tick           :~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // enabled rising edges:     |     |     |     |     |     |     |     |     |
  // ClockHDLGenerator, requires 1:1 hi:lo shape, and assuming phase=0
  // CLK_USR             :~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@
  // CLK_INV             :__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\
  // POS_EDGE            :~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // NEG_EDGE            :~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // CLK_RAW             :~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@~~\__@
  // TRIG_RISING events  :     |     |     |     |     |     |     |     |     |
  // TRIG_FALLING events :  |     |     |     |     |     |     |     |     |
  //

  // private double freq;
  private int period;
  private NetlistComponent dynClock;

  public TickHDLGenerator(ComponentContext ctx, NetlistComponent dynClock) {
    super(ctx, "base", deriveHdlName(ctx.clkPeriod), "i_TickGenerator");
    this.dynClock = dynClock;
    // freq = ctx.fpgaFreq;
    period = ctx.clkPeriod; // negative means dynamically adjustable period

    inPorts.add("FPGA_CLK", 1, -1, null); // see getPortMappings below
    outPorts.add("FPGA_CLKp", 1, -1, null); // see getPortMappings below
    outPorts.add("FPGA_CLKn", 1, -1, null); // see getPortMappings below
    outPorts.add("FPGA_TICK", 1, -1, null); // see getPortMappings below

    if (period == 0) {
      // Full speed: no params.
    } else if (period == 1) {
      // Static, but no counter needed, always tick.
    } else if (period < 0) {
      // Dynamic adjustable period: We don't know the initial count value, so
      // start without a tick, and with count=0. On the next tick, load counter
      // with ReloadValue, and from then on, tick upon detecting counter=1, and
      // reload upon detecting count=0 or count=1. This also makes it so that
      // ReloadValue=0 disables the clock tick.
      int w = getDynamicCounterWidth();
      String initialCount = (ctx.hdl.isVhdl ?
          "std_logic_vector(to_unsigned(0, CtrWidth))" : "0");
      parameters.add("CtrWidth", w);
      inPorts.add("ReloadValue", "CtrWidth", -1, null); // see getPortMappings below
      registers.add("s_tick_reg", 1, ctx.hdl.zero);
      registers.add("s_count_reg", "CtrWidth", initialCount);
      wires.add("s_tick_next", 1);
      wires.add("s_count_next", "CtrWidth");
    } else {
      // Static fixed period > 1: Start counter at period-1, and with no tick.
      int w = getStaticCounterWidth();
      String initialCount = ctx.hdl.isVhdl
          ? "std_logic_vector(to_unsigned(ReloadValue-1, CtrWidth))"
          : "ReloadValue-1";
      parameters.add("CtrWidth", w);
      parameters.add("ReloadValue", period);
      registers.add("s_tick_reg", 1, ctx.hdl.zero);
      registers.add("s_count_reg", "CtrWidth", initialCount);
      wires.add("s_tick_next", 1);
      wires.add("s_count_next", "CtrWidth");
    }
  }

  private static String deriveHdlName(int period) {
    if (period == 0)
      return "FullSpeedTickGenerator";
    else if (period < 0)
      return "DynamicTickGenerator";
    else if (period == 1)
      return "HalfSpeedTickGenerator";
    else
      return "ReducedSpeedTickGenerator";
  }

  private int getDynamicCounterWidth() {
    if (dynClock == null) {
      _err.AddFatalError("Dynamic clock speed selected, but no dynamic clock control "
          + "component found. To use the dynamic clock speed feature, you must place a "
          + "\"dynamic clock control\" component in your main top-level circuit.");
      return 0;
    }
    return dynClock.original.getAttributeSet().getValue(DynamicClock.WIDTH_ATTR).getWidth();
  }

  private int getStaticCounterWidth() {
    int w = 0, p = period;
    while (p != 0) {
      w++;
      p /= 2;
    }
    return w;
  }

  @Override
  protected void generateBehavior(Hdl out) {
    out.assign("FPGA_CLKp", "FPGA_CLK");
    out.assign("FPGA_CLKn", out.not + " FPGA_CLK");

    if (period == 0 || period == 1) {
      out.assign("FPGA_TICK", out.one);
    } else  {
      out.assign("FPGA_TICK", "s_tick_reg");
      out.stmt();
      if (out.isVhdl) {
        out.stmt("s_tick_next   <= '1'");
        out.stmt("                 WHEN s_count_reg = std_logic_vector(to_unsigned(1, CtrWidth))");
        out.stmt("                 ELSE '0';");
        if (period < 0)
          out.stmt("s_count_next  <= ReloadValue");
        else
          out.stmt("s_count_next  <= std_logic_vector(to_unsigned(ReloadValue, CtrWidth))");
        out.stmt("                 WHEN (s_count_reg = std_logic_vector(to_unsigned(0, CtrWidth))");
        out.stmt("                    OR s_count_reg = std_logic_vector(to_unsigned(1, CtrWidth)))");
        out.stmt("                 ELSE std_logic_vector(unsigned(s_count_reg)-1);");
        out.stmt();
        out.stmt("make_tick : PROCESS( FPGA_CLK , s_tick_next )");
        out.stmt("BEGIN");
        out.stmt("   IF (FPGA_CLK'event AND (FPGA_CLK = '1')) THEN");
        out.stmt("      s_tick_reg <= s_tick_next;");
        out.stmt("      s_count_reg <= s_count_next;");
        out.stmt("   END IF;");
        out.stmt("END PROCESS make_tick;");
      } else {
        out.stmt("assign s_tick_next  = (s_count_reg == 1) ? 1'b1 : 1'b0;");
        out.stmt("assign s_count_next = (s_count_reg == 1 || s_count_reg == 0) ? ReloadValue : s_count_reg-1;");
        out.stmt();
        out.stmt("always @(posedge FPGA_CLK)");
        out.stmt("begin");
        out.stmt("    s_count_reg <= s_count_next;");
        out.stmt("    s_tick_reg  <= s_tick_next;");
        out.stmt("end");
      }
    }
  }

  @Override
  protected void getPortMappings(Hdl.Map map, NetlistComponent compUnused, PortInfo p) {
    if (p.name.equals("FPGA_CLK"))
      map.add(p.name, FPGA_CLK_NET);
    else if (p.name.equals("ReloadValue"))
      map.add(p.name, FPGA_DYNCLK_PERIOD_NET);
    else if (p.name.equals("FPGA_TICK"))
      map.add(p.name, FPGA_TICK_NET);
    else if (p.name.equals("FPGA_CLKp"))
      map.add(p.name, FPGA_CLKp_NET);
    else if (p.name.equals("FPGA_CLKn"))
      map.add(p.name, FPGA_CLKn_NET);
  }
}
