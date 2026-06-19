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
package com.cburch.logisim.std.plexers;

import java.util.ArrayList;

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.hdl.Hdl;

public class PriorityEncoderHDLGenerator extends HDLGenerator {

  public PriorityEncoderHDLGenerator(ComponentContext ctx) {
    super(ctx, "plexers", deriveHDLName(ctx.attrs), "i_PriEnc");
    int ws = selWidth();
    int n = (1 << ws);
    for (int i = n - 1; i >= 0; i--)
      inPorts.add("In"+i, 1, i, false);
    inPorts.add("Enable", 1, n + PriorityEncoder.EN_IN, true);
    outPorts.add("Sel", 1, n + PriorityEncoder.GS, null);
    outPorts.add("EnableOut", 1, n + PriorityEncoder.EN_OUT, null);
    outPorts.add("Address", ws, n + PriorityEncoder.OUT, null);
    wires.add("In", n);
    wires.add("s_in_is_zero", 1);
    wires.add("s_addr", 5);
    wires.add("v_sel_1", 33); // extra bit prevents hdl syntax error in "33:InWidth" slice (if InWidth=n=32)
    wires.add("v_sel_2", 16);
    wires.add("v_sel_3", 8);
    wires.add("v_sel_4", 4);
  }

  private static String deriveHDLName(AttributeSet attrs) {
    int w = 1 << attrs.getValue(Plexers.ATTR_SELECT).getWidth();
    return "PriorityEncoder_" + w + "_Way";
  }

  @Override
  protected void generateVhdlTypes(Hdl out) {
    // slight abuse, but this puts the VHDL constant in the right place
    if (out.isVhdl)
      out.stmt("constant InWidth : integer := %d;", 1<<selWidth());
  }

  @Override
  protected void generateBehavior(Hdl out) {
    int ws = selWidth();
    int n = (1 << ws);
    if (out.isVhdl) {
      for (int i = n-1; i >= 0; i--)
        out.stmt("In(%d) <= In%d;", i, i);
      out.stmt("Sel       <= Enable AND NOT(s_in_is_zero);");
      out.stmt("EnableOut <= Enable AND s_in_is_zero;");
      out.stmt("Address   <= (others => '0') WHEN Enable = '0' ELSE");
      out.stmt("             s_addr(%d DOWNTO 0);", ws-1);
      out.stmt("");
      out.stmt("s_in_is_zero <= '1' WHEN In = std_logic(to_unsigned(0, InWidth)) ELSE '0';");
      out.stmt("");
      out.stmt("make_addr : PROCESS( In, v_sel_1, v_sel_2, v_sel_3, v_sel_4 )");
      out.stmt("BEGIN");
      out.stmt("  v_sel_1(32 DOWNTO InWidth)  <= (others => '0');");
      out.stmt("  v_sel_1(InWidth-1 DOWNTO 0) <= In;");
      out.stmt("  IF (v_sel_1(31 DOWNTO 16) = X\"0000\") THEN");
      out.stmt("    s_addr(4) <= '0';");
      out.stmt("    v_sel_2   <= v_sel_1(15 DOWNTO 0);");
      out.stmt("  ELSE");
      out.stmt("    s_addr(4) <= '1';");
      out.stmt("    v_sel_2   <= v_sel_1(31 DOWNTO 16);");
      out.stmt("  END IF;");
      out.stmt("  IF (v_sel_2(15 DOWNTO 8) = X\"00\") THEN");
      out.stmt("    s_addr(3) <= '0';");
      out.stmt("    v_sel_3   <= v_sel_2(7 DOWNTO 0);");
      out.stmt("  ELSE");
      out.stmt("    s_addr(3) <= '1';");
      out.stmt("    v_sel_3   <= v_sel_2(15 DOWNTO 8);");
      out.stmt("  END IF;");
      out.stmt("  IF (v_sel_3(7 DOWNTO 4) = X\"0\") THEN");
      out.stmt("    s_addr(2) <= '0';");
      out.stmt("    v_sel_4   <= v_sel_3(3 DOWNTO 0);");
      out.stmt("  ELSE");
      out.stmt("    s_addr(2) <= '1';");
      out.stmt("    v_sel_4   <= v_sel_3(7 DOWNTO 4);");
      out.stmt("  END IF;");
      out.stmt("  IF (v_sel_4(3 DOWNTO 2) = \"00\") THEN");
      out.stmt("     s_addr(1) <= '0';");
      out.stmt("     s_addr(0) <= v_sel_4(1);");
      out.stmt("  ELSE");
      out.stmt("     s_addr(1) <= '1';");
      out.stmt("     s_addr(0) <= v_sel_4(3);");
      out.stmt("  END IF;");
      out.stmt("END PROCESS make_addr;");
    } else {
      ArrayList<String> inputs = new ArrayList<>();
      for (int i = n-1; i >= 0; i--)
        inputs.add("In"+i);

      out.stmt("localparam InWidth = %d;", 1<<selWidth());

      out.stmt("assign In        = {%s};", String.join(", ", inputs));
      out.stmt("assign Sel       = Enable & ~s_in_is_zero;");
      out.stmt("assign EnableOut = Enable & s_in_is_zero;");
      out.stmt("assign Address   = (~Enable) ? 0 : s_addr[%d:0];", ws-1);
      out.stmt("assign s_in_is_zero = (In == 0) ? 1'b1 : 1'b0;");
      out.stmt("");
      out.stmt("assign v_sel_1[32:InWidth] = 0;");
      out.stmt("assign v_sel_1[InWidth-1:0] = In;");
      out.stmt("assign s_addr[4] = (v_sel_1[31:16] == 0) ? 1'b0 : 1'b1;");
      out.stmt("assign v_sel_2 = (v_sel_1[31:16] == 0) ? v_sel_1[15:0] : v_sel_1[31:16];");
      out.stmt("assign s_addr[3] = (v_sel_2[15:8] == 0) ? 1'b0 : 1'b1;");
      out.stmt("assign v_sel_3 = (v_sel_2[15:8] == 0) ? v_sel_2[7:0] : v_sel_2[15:8];");
      out.stmt("assign s_addr[2] = (v_sel_3[7:4] == 0) ? 1'b0 : 1'b1;");
      out.stmt("assign v_sel_4 = (v_sel_3[7:4] == 0) ? v_sel_3[3:0] : v_sel_3[7:4];");
      out.stmt("assign s_addr[1] = (v_sel_4[3:2] == 0) ? 1'b0 : 1'b1;");
      out.stmt("assign s_addr[0] = (v_sel_4[3:2] == 0) ? v_sel_4[1] : v_sel_4[3];");
    }
  }

  protected int selWidth() {
    return _attrs.getValue(Plexers.ATTR_SELECT).getWidth();
  }
}
