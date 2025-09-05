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
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;

public class MultiplierHDLGenerator extends HDLGenerator {

  public MultiplierHDLGenerator(ComponentContext ctx) {
    super(ctx, "arithmetic", uMode(ctx.attrs) ? "UnsignedMultiplier" : "SignedMultiplier", "i_Mult");
    // todo: 1-bit version?
    // todo: Verilog version
    int w = stdWidth();
    parameters.add("BitWidth", w);
    inPorts.add("DataA", "BitWidth", Multiplier.IN0, false);
    inPorts.add("DataB", "BitWidth", Multiplier.IN1, false);
    inPorts.add("CarryIn", "BitWidth", Multiplier.C_IN, false);
    outPorts.add("ProductLo", "BitWidth", Multiplier.OUT, null);
    outPorts.add("ProductHi", "BitWidth", Multiplier.C_OUT, null);
    if (_hdl.isVhdl) {
      wires.add("s_mul", "2*BitWidth");
      wires.add("s_cin", "2*BitWidth");
      wires.add("s_res", "2*BitWidth");
    } else {
      throw new IllegalArgumentException("Verilog multiplier not yet implemented");
    }
  }

  @Override
  protected void generateBehavior(Hdl out) {
    if (uMode(_attrs)) {
      out.stmt("s_cin(2*BitWidth-1 downto BitWidth) <= (others => '0');");
      out.stmt("s_cin(BitWidth-1 downto 0)          <= CarryIn;");
      out.stmt("s_mul <= std_logic_vector(unsigned(DataA) * unsigned(DataB));");
      out.stmt("s_res <= std_logic_vector(unsigned(s_mul) + unsigned(s_cin));");
      out.stmt("");
      out.stmt("ProductHi <= s_res(2*BitWidth-1 downto BitWidth);");
      out.stmt("ProductLo <= s_res(BitWidth-1 downto 0);");
    } else {
      out.stmt("s_cin(2*BitWidth-1 downto BitWidth) <= (others => '0');");
      out.stmt("s_cin(BitWidth-1 downto 0)            <= CarryIn;");
      out.stmt("s_mul <= std_logic_vector(signed(DataA) * signed(DataB));");
      out.stmt("s_res <= std_logic_vector(signed(s_mul) + signed(s_cin));");
      out.stmt("");
      out.stmt("ProductHi <= s_res(2*BitWidth-1 downto BitWidth);");
      out.stmt("ProductLo <= s_res(BitWidth-1 downto 0);");
    }
  }

  protected static boolean uMode(AttributeSet attrs) {
    return attrs.getValue(StdAttr.MODE) == StdAttr.UNSIGNED_OPTION;
  }

}
