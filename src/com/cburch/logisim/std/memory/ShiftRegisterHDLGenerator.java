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

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.hdl.Hdl;

public class ShiftRegisterHDLGenerator extends HDLGenerator {

  public ShiftRegisterHDLGenerator(ComponentContext ctx) {
    super(ctx, "memory", deriveHDLName(ctx.attrs), "i_Shft");
    int w = stdWidth();
    int n = stages();
    boolean parallel = parallel();

    parameters.add("BitWidth", w);
    if (!parallel)
      parameters.add("Stages", n);

    clockPort = new ClockPortInfo("GlobalClock", "ClockEnable", ShiftRegister.CK);

    inPorts.add("Reset", 1, ShiftRegister.CLR, false);
    inPorts.add("ShiftEnable", 1, ShiftRegister.SH, true);
    inPorts.add("ShiftIn", "BitWidth", ShiftRegister.IN, false);
    outPorts.add("ShiftOut", "BitWidth", ShiftRegister.OUT, null);
    if (parallel) {
      int portnr = ShiftRegister.LD;
      inPorts.add("ParLoad", 1, portnr++, false);
      for (int i = n-1; i >= 0; i--) {
        inPorts.add("D"+i, "BitWidth", portnr++, false);
        outPorts.add("Q"+i, "BitWidth", portnr++, null);
      }
    }
  }

  private static String deriveHDLName(AttributeSet attrs) {
    if (!attrs.getValue(ShiftRegister.ATTR_LOAD)) {
      return "ShiftRegister"; // variation with clock, 4 control ports, no D/Q ports, generic in BitWidth and Stages
    } else {
      int n = attrs.getValue(ShiftRegister.ATTR_LENGTH);
      return "ShiftRegister_"+n+"_stages"; // variation with clock, 5 control ports, 2*N D/Q ports, generic in BitWidth
    }
  }

  @Override
  protected void generateVhdlTypes(Hdl out) {
    // slight abuse, but this puts the VHDL constant in the right place
    if (out.isVhdl) {
      if (parallel()) {
        out.stmt("constant Stages : integer := %d;", stages());
        out.stmt("   signal s_all_inputs : std_logic_vector((BitWidth*Stages)-1 downto 0);");
      }
      out.stmt("   signal s_state_reg  : std_logic_vector((BitWidth*Stages)-1 downto 0);");
      out.stmt("   signal s_state_next : std_logic_vector((BitWidth*Stages)-1 downto 0);");
    }
  }

  @Override
  protected void generateBehavior(Hdl out) {
    boolean parallel = parallel();
    int n = stages();
    if (out.isVhdl) {
      out.stmt("   ShiftOut <= s_state_reg(BitWidth-1 downto 0);");
      if (parallel) {
        String all = "D0";
        for (int i = 0; i < n; i++) {
          out.stmt("   Q"+i+" <= s_state_reg(BitWidth*"+(i+1)+"-1 downto BitWidth*"+(i)+");");
          if (i != 0) 
            all = "D"+i + " & " + all;
        }
        out.stmt("   s_all_inputs <= "+all+";");
        out.stmt("   s_state_next <= s_all_inputs WHEN ParLoad = '1' ELSE ShiftIn & s_state_reg((BitWidth*Stages)-1 downto BitWidth);");
      } else {
        out.stmt("   s_state_next <= ShiftIn & s_state_reg((BitWidth*Stages)-1 downto BitWidth);");
      }
      if (parallel)
        out.stmt("   make_state : process(GlobalClock, ShiftEnable, ParLoad, ClockEnable, Reset, s_state_next)");
      else
        out.stmt("   make_state : process(GlobalClock, ShiftEnable, ClockEnable, Reset, s_state_next)");
      out.stmt("   begin");
      out.stmt("      if (Reset = '1') then s_state_reg <= (others => '0');");
      out.stmt("      elsif (GlobalClock'event and (GlobalClock = '1')) then");
      if (parallel)
        out.stmt("         if (((ShiftEnable = '1') or (ParLoad = '1')) and (ClockEnable = '1')) then");
      else
        out.stmt("         if ((ShiftEnable = '1') and (ClockEnable = '1')) then");
      out.stmt("            s_state_reg <= s_state_next;");
      out.stmt("         end if;");
      out.stmt("      end if;");
      out.stmt("   end process make_state;");
    } else {
      if (parallel) {
        out.stmt("   localparam Stages = %d;", stages());
        out.stmt("   wire[(Stages*BitWidth)-1:0] s_all_inputs;");
      }
      out.stmt("   wire[(Stages*BitWidth)-1:0] s_state_next;");
      out.stmt("   reg[(Stages*BitWidth)-1:0] s_state_reg;");

      if (parallel) {
        String all = "D0";
        for (int i = 0; i < n; i++) {
          out.stmt("   assign Q"+i+" = s_state_reg[BitWidth*"+(i+1)+"-1 : BitWidth*"+(i)+"];");
          if (i != 0) 
            all = "D"+i + "," + all;
        }
        out.stmt("   assign s_all_inputs = {"+all+"};");
        out.stmt("   assign s_state_next = (ParLoad) ? s_all_inputs : {ShiftIn, s_state_reg[(BitWidth*Stages)-1 : BitWidth]};");
      } else {
        out.stmt("   assign s_state_next == {ShiftIn, s_state_reg[(BitWidth*Stages)-1 : BitWidth]};");
      }
      out.stmt("   always @(posedge GlobalClock or posedge Reset)");
      out.stmt("   begin");
      out.stmt("      if (Reset) s_state_reg <= 0;");
      if (parallel)
        out.stmt("         else if ((ShiftEnable|ParLoad)&ClockEnable) s_state_reg <= s_state_next;");
      else
        out.stmt("         else if (ShiftEnable&ClockEnable) s_state_reg <= s_state_next;");
      out.stmt("   end;");
      out.stmt();
    }
  }

  protected int stages() {
    return _attrs.getValue(ShiftRegister.ATTR_LENGTH);
  }

  protected boolean parallel() {
    return _attrs.getValue(ShiftRegister.ATTR_LOAD);
  }

}
