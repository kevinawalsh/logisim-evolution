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
package com.cburch.logisim.std.bfh;

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.cburch.logisim.hdl.Hdl;

public class hex2sevensegHDLGenerator extends HDLGenerator {

  public hex2sevensegHDLGenerator(ComponentContext ctx) {
    super(ctx,"bfh", "Hex_to_7_Segment_Decoder", "i_HexTo7");
    inPorts.add("Hexin", 4, hex2sevenseg.Hexin, false);
    outPorts.add("Segment_A", 1, hex2sevenseg.Segment_A, null);
    outPorts.add("Segment_B", 1, hex2sevenseg.Segment_B, null);
    outPorts.add("Segment_C", 1, hex2sevenseg.Segment_C, null);
    outPorts.add("Segment_D", 1, hex2sevenseg.Segment_D, null);
    outPorts.add("Segment_E", 1, hex2sevenseg.Segment_E, null);
    outPorts.add("Segment_F", 1, hex2sevenseg.Segment_F, null);
    outPorts.add("Segment_G", 1, hex2sevenseg.Segment_G, null);
    wires.add("s_output_value", 7);
  }
	
	@Override
  protected void generateBehavior(Hdl out) {
  // todo: verilog support
		out.stmt("Segment_A <= s_output_value(0);");
		out.stmt("Segment_B <= s_output_value(1);");
		out.stmt("Segment_C <= s_output_value(2);");
		out.stmt("Segment_D <= s_output_value(3);");
		out.stmt("Segment_E <= s_output_value(4);");
		out.stmt("Segment_F <= s_output_value(5);");
		out.stmt("Segment_G <= s_output_value(6);");
		out.stmt("");
		out.stmt("MakeSegs : PROCESS( HexIn )");
		out.stmt("BEGIN");
		out.stmt("   CASE (HexIn) IS");
		out.stmt("      WHEN \"0000\" => s_output_value <= \"0111111\";");
		out.stmt("      WHEN \"0001\" => s_output_value <= \"0000110\";");
		out.stmt("      WHEN \"0010\" => s_output_value <= \"1011011\";");
		out.stmt("      WHEN \"0011\" => s_output_value <= \"1001111\";");
		out.stmt("      WHEN \"0100\" => s_output_value <= \"1100110\";");
		out.stmt("      WHEN \"0101\" => s_output_value <= \"1101101\";");
		out.stmt("      WHEN \"0110\" => s_output_value <= \"1111101\";");
		out.stmt("      WHEN \"0111\" => s_output_value <= \"0000111\";");
		out.stmt("      WHEN \"1000\" => s_output_value <= \"1111111\";");
		out.stmt("      WHEN \"1001\" => s_output_value <= \"1101111\";");
		out.stmt("      WHEN \"1010\" => s_output_value <= \"1110111\";");
		out.stmt("      WHEN \"1011\" => s_output_value <= \"1111100\";");
		out.stmt("      WHEN \"1100\" => s_output_value <= \"0111001\";");
		out.stmt("      WHEN \"1101\" => s_output_value <= \"1011110\";");
		out.stmt("      WHEN \"1110\" => s_output_value <= \"1111001\";");
		out.stmt("      WHEN \"1111\" => s_output_value <= \"1110001\";");
		// out.stmt("      WHEN OTHERS => s_output_value <= \"-------\";");
		out.stmt("   END CASE;");
		out.stmt("END PROCESS MakeSegs;");
	}

}
