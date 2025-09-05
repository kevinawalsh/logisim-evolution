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
package com.cburch.logisim.std.arith;

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;

public class ComparatorHDLGenerator extends HDLGenerator {

  public ComparatorHDLGenerator(ComponentContext ctx) {
    super(ctx, "arithmetic", "${BUS}Comparator", "i_Cmp");
    int w = stdWidth();
    if (w > 1) {
      // Generic n-bit version
      parameters.add("TwosComplement", uMode() ? 0 : 1);
      parameters.add("BitWidth", w);
      inPorts.add("DataA", "BitWidth", Comparator.IN0, false);
      inPorts.add("DataB", "BitWidth", Comparator.IN1, false);
    } else {
      // 1-bit version
      parameters.add("TwosComplement", uMode() ? 0 : 1);
      inPorts.add("DataA", 1, Comparator.IN0, false);
      inPorts.add("DataB", 1, Comparator.IN1, false);
    }
    outPorts.add("A_GT_B", 1, Comparator.GT, null);
    outPorts.add("A_EQ_B", 1, Comparator.EQ, null);
    outPorts.add("A_LT_B", 1, Comparator.LT, null);
    if (isBus()) {
      wires.add("s_slt", 1);
      wires.add("s_ult", 1);
      wires.add("s_sgt", 1);
      wires.add("s_ugt", 1);
    }
  }

  @Override
  protected void generateBehavior(Hdl out) {
    if (out.isVhdl && !isBus()) {
      out.stmt("A_EQ_B <= DataA XNOR DataB;");
      out.stmt("A_LT_B <= DataA AND NOT(DataB) WHEN TwosComplement = 1 ELSE");
      out.stmt("          NOT(DataA) AND DataB;");
      out.stmt("A_GT_B <= NOT(DataA) AND DataB WHEN TwosComplement = 1 ELSE");
      out.stmt("          DataA AND NOT(DataB);");
    } else if (out.isVhdl) {
      out.stmt("s_slt <= '1' WHEN signed(DataA) < signed(DataB) ELSE '0';");
      out.stmt("s_ult <= '1' WHEN unsigned(DataA) < unsigned(DataB) ELSE '0';");
      out.stmt("s_sgt <= '1' WHEN signed(DataA) > signed(DataB) ELSE '0';");
      out.stmt("s_ugt <= '1' WHEN unsigned(DataA) > unsigned(DataB) ELSE '0';");
      out.stmt("");
      out.stmt("A_EQ_B <= '1' WHEN DataA = DataB ELSE '0';");
      out.stmt("A_GT_B <= s_sgt WHEN TwosComplement = 1 ELSE s_ugt;");
      out.stmt("A_LT_B <= s_slt WHEN TwosComplement = 1 ELSE s_ult;");
    } else if (out.isVerilog && !isBus()) {
      out.stmt("assign A_EQ_B = (DataA == DataB);");
      out.stmt("assign A_LT_B = (DataA < DataB);");
      out.stmt("assign A_GT_B = (DataA > DataB);");
    } else if (out.isVerilog) {
      out.stmt("assign s_slt = ($signed(DataA) < $signed(DataB));");
      out.stmt("assign s_ult = (DataA < DataB);");
      out.stmt("assign s_sgt = ($signed(DataA) > $signed(DataB));");
      out.stmt("assign s_ugt = (DataA > DataB);");
      out.stmt("");
      out.stmt("assign A_EQ_B = (DataA == DataB);");
      out.stmt("assign A_GT_B = (TwosComplement == 1) ? s_sgt : s_ugt;");
      out.stmt("assign A_LT_B = (TwosComplement == 1) ? s_slt : s_ult;");
    }
  }

  protected boolean uMode() {
    return _attrs.getValue(StdAttr.MODE) == StdAttr.UNSIGNED_OPTION;
  }

}
