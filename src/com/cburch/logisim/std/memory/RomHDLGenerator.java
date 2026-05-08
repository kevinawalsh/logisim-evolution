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

public class RomHDLGenerator extends HDLGenerator {
  
  static boolean supports(String lang, AttributeSet attrs /*, char vendor*/) {
    return lang.equals("VHDL") || Mem.lineSize(attrs) == 1; // TODO: Verilog support
  }

  public RomHDLGenerator(ComponentContext ctx) {
    super(ctx, "memory", "ROM_${UID}", "i_ROM");
    inPorts.add("Address", addrWidth(), Mem.ADDR, false);
    outPorts.add("Data", dataWidth(), Mem.DATA, null);
    int n = Mem.lineSize(_attrs);
    for (int i = 1; i < n; i++)
      outPorts.add("Data"+i, dataWidth(), Mem.MEM_INPUTS+i-1, null);
  }

  @Override
  protected void generateBehavior(Hdl out) {
    MemContents rom = _attrs.getValue(Rom.CONTENTS_ATTR);
    int n = Mem.lineSize(_attrs);
    int wd = dataWidth();
    int wa = addrWidth();
    // int shift = (n == 4 ? 2 : n == 2 ? 1 : 0);
    if (out.isVhdl) {
      out.stmt("MakeRom : PROCESS( Address )");
      out.stmt("   BEGIN");
      out.stmt("      CASE (Address) IS");
      for (long addr = 0; addr < (1 << wa); addr += n) {
        if (filled(rom, addr, n)) {
          out.stmt("         WHEN %s =>\t Data <= %s;",
              out.literal(addr, wa), out.literal(rom.get(addr), wd));
          for (int i = 1; i < n; i++)
            out.cont(" \t Data%d <= %s;", i, out.literal(rom.get(addr+i), wd));
        }
      }
      if (wd == 1) {
        out.stmt("         WHEN others =>\t Data <= '0';");
        for (int i = 1; i < n; i++)
          out.cont(" \t Data%d <= '0';", i);
      } else {
        out.stmt("         WHEN others =>\t Data <= (others => '0');");
        for (int i = 1; i < n; i++)
          out.cont(" \t Data%d <= (others => '0');", i);
      }
      out.stmt("      END CASE;");
      out.stmt("   END PROCESS MakeRom;");
    } else {
      // todo: support verilog with lineSize > 1
      out.stmt("reg[%d:0] Data;", wd - 1);
      out.stmt("");
      out.stmt("always @ (Address)");
      out.stmt("begin");
      out.stmt("   case(Address)");
      for (long addr = 0; addr < (1 << wa); addr++) {
        int data = rom.get(addr);
        if (data != 0)
          out.stmt("      %d : Data = %d;", addr, data);
      }
      out.stmt("      default : Data = 0;");
      out.stmt("   endcase");
      out.stmt("end");
    }
  }

  protected int addrWidth() {
    return _attrs.getValue(Mem.ADDR_ATTR).getWidth();
  }

  protected int dataWidth() {
    return _attrs.getValue(Mem.DATA_ATTR).getWidth();
  }

  static boolean filled(MemContents rom, long addr, int n) {
    for (int i = 0; i < n; i++)
      if (rom.get(addr+i) != 0)
        return true;
    return false;
  }

}
