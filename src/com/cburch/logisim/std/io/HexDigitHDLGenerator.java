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

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.bfh.logisim.hdlgenerator.HiddenPort;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.hdl.Hdl;

public class HexDigitHDLGenerator extends HDLGenerator {
  
  public HexDigitHDLGenerator(ComponentContext ctx) {
    super(ctx, "io", deriveHDLName(ctx.attrs), "i_Hex");

    inPorts.add("Hex", 4, HexDigit.HEX, false);
    inPorts.add("DecimalPoint", 1, HexDigit.DP, false);
    int digits = ctx.attrs.getValue(HexDigit.ATTR_DIGITS).intValue();
    if (digits > 1) {
      for (int i = 1; i <= digits; i++)
        inPorts.add("Hex_Digit_Enable_" + i, 1, HexDigit.DP+i, false);
      hiddenPort = HiddenPort.makeOutport(SevenSegment.pinLabels(8 + digits),
          HiddenPort.SevenSegmentGang, HiddenPort.OutRibbon, HiddenPort.LED, HiddenPort.OutPin);
    } else {
      hiddenPort = HiddenPort.makeOutport(SevenSegment.pinLabels(8),
          HiddenPort.SevenSegment, HiddenPort.OutRibbon, HiddenPort.LED, HiddenPort.OutPin);
    }
    wires.add("s_pattern", 7);
  }

  private static String deriveHDLName(AttributeSet attrs) {
    int digits = attrs.getValue(HexDigit.ATTR_DIGITS).intValue();
    return "HexDigit_" + digits + "_Gang";
  }

  @Override
  protected void generateBehavior(Hdl out) {
    int digits = _attrs.getValue(HexDigit.ATTR_DIGITS).intValue();
    if (out.isVhdl) {
      out.stmt("Segment_A <= s_pattern(0);");
      out.stmt("Segment_B <= s_pattern(1);");
      out.stmt("Segment_C <= s_pattern(2);");
      out.stmt("Segment_D <= s_pattern(3);");
      out.stmt("Segment_E <= s_pattern(4);");
      out.stmt("Segment_F <= s_pattern(5);");
      out.stmt("Segment_G <= s_pattern(6);");
      out.stmt("Segment_DP <= DecimalPoint;");
      out.stmt();
      if (digits > 1) {
        for (int i = 1; i <= digits; i++)
          out.stmt("Digit_"+i+"_Enable <= Hex_Digit_Enable_" + i + ";");
        out.stmt();
      }
      out.stmt("MakeSegments : process( Hex )");
      out.stmt("begin");
      out.stmt("   case (Hex) is");
      out.stmt("      when \"0000\" => s_pattern <= \"0111111\";");
      out.stmt("      when \"0001\" => s_pattern <= \"0000110\";");
      out.stmt("      when \"0010\" => s_pattern <= \"1011011\";");
      out.stmt("      when \"0011\" => s_pattern <= \"1001111\";");
      out.stmt("      when \"0100\" => s_pattern <= \"1100110\";");
      out.stmt("      when \"0101\" => s_pattern <= \"1101101\";");
      out.stmt("      when \"0110\" => s_pattern <= \"1111101\";");
      out.stmt("      when \"0111\" => s_pattern <= \"0000111\";");
      out.stmt("      when \"1000\" => s_pattern <= \"1111111\";");
      out.stmt("      when \"1001\" => s_pattern <= \"1101111\";");
      out.stmt("      when \"1010\" => s_pattern <= \"1110111\";");
      out.stmt("      when \"1011\" => s_pattern <= \"1111100\";");
      out.stmt("      when \"1100\" => s_pattern <= \"0111001\";");
      out.stmt("      when \"1101\" => s_pattern <= \"1011110\";");
      out.stmt("      when \"1110\" => s_pattern <= \"1111001\";");
      out.stmt("      when \"1111\" => s_pattern <= \"1110001\";");
      out.stmt("      when others   => s_pattern <= \"1001001\"; -- err");
      out.stmt("   end case;");
      out.stmt("end process MakeSegments;");
    } else {
      out.stmt("assign Segment_A = s_pattern[0];");
      out.stmt("assign Segment_B = s_pattern[1];");
      out.stmt("assign Segment_C = s_pattern[2];");
      out.stmt("assign Segment_D = s_pattern[3];");
      out.stmt("assign Segment_E = s_pattern[4];");
      out.stmt("assign Segment_F = s_pattern[5];");
      out.stmt("assign Segment_G = s_pattern[6];");
      out.stmt("assign Segment_DP = DecimalPoint;");
      out.stmt();
      if (digits > 1) {
        for (int i = 1; i <= digits; i++)
          out.stmt("assign Digit_"+i+"_Enable = Hex_Digit_Enable_" + i + ";");
        out.stmt();
      }
      out.stmt("assign s_pattern =");
      out.stmt("    (Hex == 4'b0000) ? 7'b0111111 :");
      out.stmt("    (Hex == 4'b0001) ? 7'b0000110 :");
      out.stmt("    (Hex == 4'b0010) ? 7'b1011011 :");
      out.stmt("    (Hex == 4'b0011) ? 7'b1001111 :");
      out.stmt("    (Hex == 4'b0100) ? 7'b1100110 :");
      out.stmt("    (Hex == 4'b0101) ? 7'b1101101 :");
      out.stmt("    (Hex == 4'b0110) ? 7'b1111101 :");
      out.stmt("    (Hex == 4'b0111) ? 7'b0000111 :");
      out.stmt("    (Hex == 4'b1000) ? 7'b1111111 :");
      out.stmt("    (Hex == 4'b1001) ? 7'b1101111 :");
      out.stmt("    (Hex == 4'b1010) ? 7'b1110111 :");
      out.stmt("    (Hex == 4'b1011) ? 7'b1111100 :");
      out.stmt("    (Hex == 4'b1100) ? 7'b0111001 :");
      out.stmt("    (Hex == 4'b1101) ? 7'b1011110 :");
      out.stmt("    (Hex == 4'b1110) ? 7'b1111001 :");
      out.stmt("    (Hex == 4'b1111) ? 7'b1110001 :");
      out.stmt("    8'b1001001; // err");
    }
  }
}
