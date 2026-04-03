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

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.hdl.Hdl;

public class bin2bcdHDLGenerator extends HDLGenerator {

  public bin2bcdHDLGenerator(ComponentContext ctx) {
    super(ctx, "bfh", deriveHDLName(ctx.attrs), "i_BinToBcd");
    int w = bitWidth(_attrs);
    int n = numPorts(_attrs);
    parameters.add("BitWidth", w);
    inPorts.add("BinValue", "BitWidth", 0, false);
		for (int i = 0 ; i < n; i++)
			outPorts.add("BCD"+(int)Math.pow(10, i), 4, 1+i, null);
    switch (n) {
    case 2: 
      for (int i = 0; i <= 3; i++)
        wires.add("s_level_"+i, 7);
      break;
    case 3:
      for (int i = 0; i <= 6; i++)
        wires.add("s_level_"+i, 11);
      break;
    case 4:
      for (int i = 0; i <= 10; i++)
        wires.add("s_level_"+i, 16);
      break;
    }
  }
  
  private static String deriveHDLName(AttributeSet attrs) {
    return "Binary_to_BCD_converter_" + numPorts(attrs) + "_bcd_ports";
	}
	
	@Override
  protected void generateBehavior(Hdl out) {
    if (out.isVhdl) {
      switch (numPorts(_attrs)) {
      case 2 : out.stmt("s_level_0(6 DOWNTO BitWidth) <= (OTHERS => '0');");
               out.stmt("s_level_0(BitWidth-1 DOWNTO 0) <= BinValue;");
               out.stmt("s_level_1(2 DOWNTO 0) <= s_level_0(2 DOWNTO 0);");
               out.stmt("s_level_2(1 DOWNTO 0) <= s_level_1(1 DOWNTO 0);");
               out.stmt("s_level_2(6)          <= s_level_1(6);");
               out.stmt("s_level_3(6 DOWNTO 5) <= s_level_2(6 DOWNTO 5);");
               out.stmt("s_level_3(0)          <= s_level_2(0);");
               out.stmt();
               out.stmt("BCD1  <= s_level_3(3 DOWNTO 0);");
               out.stmt("BCD10 <= \"0\"&s_level_3(6 DOWNTO 4);" );
               generateAdd3Block(out, "s_level_0", 6, "s_level_1", 6, "C1");
               generateAdd3Block(out, "s_level_1", 5, "s_level_2", 5, "C2");
               generateAdd3Block(out, "s_level_2", 4, "s_level_3", 4, "C3");
               break;
      case 3 : out.stmt("s_level_0(10 DOWNTO BitWidth) <= (OTHERS => '0');");
               out.stmt("s_level_0(BitWidth-1 DOWNTO 0) <= BinValue;");
               out.stmt("s_level_1(10)          <= s_level_0(10);");
               out.stmt("s_level_1( 5 DOWNTO 0) <= s_level_0( 5 DOWNTO 0);");
               out.stmt("s_level_2(10 DOWNTO 9) <= s_level_1(10 DOWNTO 9);");
               out.stmt("s_level_2( 4 DOWNTO 0) <= s_level_1( 4 DOWNTO 0);");
               out.stmt("s_level_3(10 DOWNTO 8) <= s_level_2(10 DOWNTO 8);");
               out.stmt("s_level_3( 3 DOWNTO 0) <= s_level_2( 3 DOWNTO 0);");
               out.stmt("s_level_4( 2 DOWNTO 0) <= s_level_3( 2 DOWNTO 0);");
               out.stmt("s_level_5(10)          <= s_level_4(10);");
               out.stmt("s_level_5( 1 DOWNTO 0) <= s_level_4( 1 DOWNTO 0);");
               out.stmt("s_level_6(10 DOWNTO 9) <= s_level_5(10 DOWNTO 9);");
               out.stmt("s_level_6(0)           <= s_level_5(0);");
               out.stmt();
               out.stmt("BCD1   <= s_level_6( 3 DOWNTO 0 );");
               out.stmt("BCD10  <= s_level_6( 7 DOWNTO 4 );");
               out.stmt("BCD100 <= \"0\"&s_level_6(10 DOWNTO 8);");
               generateAdd3Block(out, "s_level_0" ,9,  "s_level_1", 9,  "C0");
               generateAdd3Block(out, "s_level_1" ,8,  "s_level_2", 8,  "C1");
               generateAdd3Block(out, "s_level_2" ,7,  "s_level_3", 7,  "C2");
               generateAdd3Block(out, "s_level_3" ,6,  "s_level_4", 6,  "C3");
               generateAdd3Block(out, "s_level_4" ,5,  "s_level_5", 5,  "C4");
               generateAdd3Block(out, "s_level_5" ,4,  "s_level_6", 4,  "C5");
               generateAdd3Block(out, "s_level_3" ,10, "s_level_4", 10, "C6");
               generateAdd3Block(out, "s_level_4" ,9,  "s_level_5", 9,  "C7");
               generateAdd3Block(out, "s_level_5" ,8,  "s_level_6", 8,  "C8");
               break;
      case 4 : out.stmt("s_level_0(15 DOWNTO BitWidth) <= (OTHERS => '0');");
               out.stmt("s_level_0(BitWidth-1 DOWNTO 0) <= BinValue;");
               out.stmt("s_level_1(15 DOWNTO 14)  <= s_level_0(15 DOWNTO 14);");
               out.stmt("s_level_1( 9 DOWNTO  0)  <= s_level_0( 9 DOWNTO  0);");
               out.stmt("s_level_2(15 DOWNTO 13)  <= s_level_1(15 DOWNTO 13);");
               out.stmt("s_level_2( 8 DOWNTO  0)  <= s_level_1( 8 DOWNTO  0);");
               out.stmt("s_level_3(15 DOWNTO 12)  <= s_level_2(15 DOWNTO 12);");
               out.stmt("s_level_3( 7 DOWNTO  0)  <= s_level_2( 7 DOWNTO  0);");
               out.stmt("s_level_4(15)            <= s_level_3(15);");
               out.stmt("s_level_4( 6 DOWNTO  0)  <= s_level_3( 6 DOWNTO  0);");
               out.stmt("s_level_5(15 DOWNTO 14)  <= s_level_4(15 DOWNTO 14);");
               out.stmt("s_level_5( 5 DOWNTO  0)  <= s_level_4( 5 DOWNTO  0);");
               out.stmt("s_level_6(15 DOWNTO 13)  <= s_level_5(15 DOWNTO 13);");
               out.stmt("s_level_6( 4 DOWNTO  0)  <= s_level_5( 4 DOWNTO  0);");
               out.stmt("s_level_7( 3 DOWNTO  0)  <= s_level_6( 3 DOWNTO  0);");
               out.stmt("s_level_8(15)            <= s_level_7(15);");
               out.stmt("s_level_8( 2 DOWNTO  0)  <= s_level_7( 2 DOWNTO  0);");
               out.stmt("s_level_9(15 DOWNTO 14)  <= s_level_8(15 DOWNTO 14);");
               out.stmt("s_level_9( 1 DOWNTO  0)  <= s_level_8( 1 DOWNTO  0);");
               out.stmt("s_level_10(15 DOWNTO 13) <= s_level_9(15 DOWNTO 13);");
               out.stmt("s_level_10(0)            <= s_level_9(0);");
               out.stmt();
               out.stmt("BCD1    <= s_level_10( 3 DOWNTO  0 );");
               out.stmt("BCD10   <= s_level_10( 7 DOWNTO  4 );");
               out.stmt("BCD100  <= s_level_10(11 DOWNTO  8);");
               out.stmt("BCD1000 <= s_level_10(15 DOWNTO 12);");
               generateAdd3Block(out, "s_level_0", 13, "s_level_1" , 13, "C0");
               generateAdd3Block(out, "s_level_1", 12, "s_level_2" , 12, "C1");
               generateAdd3Block(out, "s_level_2", 11, "s_level_3" , 11, "C2");
               generateAdd3Block(out, "s_level_3", 10, "s_level_4" , 10, "C3");
               generateAdd3Block(out, "s_level_4",  9, "s_level_5" ,  9, "C4");
               generateAdd3Block(out, "s_level_5",  8, "s_level_6" ,  8, "C5");
               generateAdd3Block(out, "s_level_6",  7, "s_level_7" ,  7, "C6");
               generateAdd3Block(out, "s_level_7",  6, "s_level_8" ,  6, "C7");
               generateAdd3Block(out, "s_level_8",  5, "s_level_9" ,  5, "C8");
               generateAdd3Block(out, "s_level_9",  4, "s_level_10",  4, "C9");
               generateAdd3Block(out, "s_level_3", 14, "s_level_4" , 14, "C10");
               generateAdd3Block(out, "s_level_4", 13, "s_level_5" , 13, "C11");
               generateAdd3Block(out, "s_level_5", 12, "s_level_6" , 12, "C12");
               generateAdd3Block(out, "s_level_6", 11, "s_level_7" , 11, "C13");
               generateAdd3Block(out, "s_level_7", 10, "s_level_8" , 10, "C14");
               generateAdd3Block(out, "s_level_8",  9, "s_level_9" ,  9, "C15");
               generateAdd3Block(out, "s_level_9",  8, "s_level_10",  8, "C16");
               generateAdd3Block(out, "s_level_6", 15, "s_level_7" , 15, "C17");
               generateAdd3Block(out, "s_level_7", 14, "s_level_8" , 14, "C18");
               generateAdd3Block(out, "s_level_8", 13, "s_level_9" , 13, "C19");
               generateAdd3Block(out, "s_level_9", 12, "s_level_10", 12, "C20");
               break;
      }
    } else {
      // todo: verilog support
		}
	}
	
	private void generateAdd3Block(Hdl out, String srcName, int srcIdx,
			                               String dstName, int dstIdx,
			                               String processName) {
		out.stmt();
		out.stmt("ADD3_%s : PROCESS( %s )", processName, srcName);
		out.stmt("BEGIN");
		out.stmt("   CASE ( %s(%d DOWNTO %d) ) IS", srcName, srcIdx, srcIdx-3);
		out.stmt("      WHEN \"0000\" => %s(%d DOWNTO %d) <= \"0000\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0001\" => %s(%d DOWNTO %d) <= \"0001\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0010\" => %s(%d DOWNTO %d) <= \"0010\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0011\" => %s(%d DOWNTO %d) <= \"0011\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0100\" => %s(%d DOWNTO %d) <= \"0100\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0101\" => %s(%d DOWNTO %d) <= \"1000\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0110\" => %s(%d DOWNTO %d) <= \"1001\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"0111\" => %s(%d DOWNTO %d) <= \"1010\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"1000\" => %s(%d DOWNTO %d) <= \"1011\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN \"1001\" => %s(%d DOWNTO %d) <= \"1100\";", dstName, dstIdx, dstIdx-3);
		out.stmt("      WHEN OTHERS =>   %s(%d DOWNTO %d) <= \"----\";", dstName, dstIdx, dstIdx-3);
		out.stmt("   END CASE;");
		out.stmt("END PROCESS ADD3_%s;", processName);
	}

  private static int bitWidth(AttributeSet attrs) {
		return attrs.getValue(bin2bcd.ATTR_BinBits).getWidth();
  }

  private static int numPorts(AttributeSet attrs) {
    return (int)(Math.log10(1 << bitWidth(attrs)) + 1.0);
  }
}
