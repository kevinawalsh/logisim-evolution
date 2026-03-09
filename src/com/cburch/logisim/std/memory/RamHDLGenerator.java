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

import java.io.File;

import com.bfh.logisim.hdlgenerator.FileWriter;
import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;

public class RamHDLGenerator extends HDLGenerator {

  static boolean supports(String lang, AttributeSet attrs, char vendor) {
    Object dbus = attrs.getValue(RamAttributes.ATTR_DBUS);
    boolean separate = dbus == RamAttributes.BUS_SEP;
    Object trigger = attrs.getValue(StdAttr.TRIGGER);
    boolean synch = trigger == StdAttr.TRIG_RISING || trigger == StdAttr.TRIG_FALLING;
    return lang.equals("VHDL") && separate && synch;
  }

  public RamHDLGenerator(ComponentContext ctx) {
    super(ctx, "memory", deriveHDLName(ctx.attrs), "i_RAM");
    // Address, DataOut0, then... DataOutX, DataInX, CLK, WE, LEX
    inPorts.add("Address", addrWidth(), Mem.ADDR, false);
    outPorts.add("DataOut0", dataWidth(), Mem.DATA, null);
    int n = Mem.lineSize(_attrs);
    int portnr = Mem.MEM_INPUTS;
    for (int i = 1; i < n; i++)
      outPorts.add("DataOut"+i, dataWidth(), portnr++, null);
    for (int i = 0; i < n; i++)
      inPorts.add("DataIn"+i, dataWidth(), portnr++, false);
    clockPort = new ClockPortInfo("GlobalClock", "ClockEnable", portnr++);
    inPorts.add("WE", 1, portnr++, false);
    for (int i = 0; i < n && n > 1; i++)
      inPorts.add("LE"+i, 1, portnr++, true);

    // For NVRAM, the values for initial data are encoded directly into the VHDL
    // file as hex bit patterns, so the entire HDL depends on the specific
    // component instance (the full path to the instance within the overall
    // design, not just a unique name within a single circuit or subcircuit.
    // Ideally we'd use generic parameters, but we don't yet have a way to pass
    // parameters down through multiple nested levels of of HDL. So instead for
    // now, we required NVRAM only appears at the top-level circuit (this is
    // checked during DRC), then we just use a UID for this component.
  }

  private static String deriveHDLName(AttributeSet attrs) {
    if (attrs.getValue(RamAttributes.ATTR_TYPE) == RamAttributes.NONVOLATILE)
      return "NVRAM_${UID}";
    int wd = attrs.getValue(Mem.DATA_ATTR).getWidth();
    int wa = attrs.getValue(Mem.ADDR_ATTR).getWidth();
    int n = Mem.lineSize(attrs);
    return String.format("RAM_%dx%dx%d", wd, n, 1<<wa); // could probably be generic
  }

  @Override
	protected Hdl getArchitecture() {
    return getArchitecture(null);
  }

	private Hdl getArchitecture(RamState state) {
    Hdl out = new Hdl(_lang, _err);
    generateFileHeader(out);

    int wd = dataWidth();
    int n = Mem.lineSize(_attrs);
    int rows = (1 << addrWidth()) / n;

		if (out.isVhdl) {

			out.stmt("architecture logisim_generated of " + hdlModuleName + " is ");
      out.indent();
      out.stmt("type MEMORY_ARRAY is array (%d downto 0) of %s;", rows-1, out.typeForWidth(wd));
      out.stmt();
      if (state != null) {
        int bits = rows * wd;
        while (bits % 4 != 0)
          bits++;
        out.stmt("function INIT_RAM_VEC(init_vec : std_logic_vector(0 to %d)) return MEMORY_ARRAY is", bits - 1);
        out.indent();
        out.stmt("variable ram_content : MEMORY_ARRAY;");
        out.stmt("begin");
        out.indent();
        out.stmt("for i in 0 to %d loop", rows - 1);
        out.indent();
        out.stmt("ram_content(i)(%d downto 0) := init_vec(i*%d to i*%d+%d);", wd - 1, wd, wd, wd-1);
        out.dedent();
        out.stmt("end loop;");
        out.stmt("return ram_content;");
        out.dedent();
        out.stmt("end function;");
        out.stmt();
        out.comment("memory definitions and initial values");
        for (int i = 0; i < n; i++) {
          String contents = encodeMemInitDataVHDL(state, i);
          out.stmt("signal s_mem%d_contents : MEMORY_ARRAY := INIT_RAM_VEC(%s);", i, contents);
        }
      } else {
        out.comment("memory definitions without initial values");
        for (int i = 0; i < n; i++)
          out.stmt("signal s_mem%d_contents : MEMORY_ARRAY;", i);
      }
      out.stmt();
      out.dedent();

			out.stmt("begin");
      out.indent();
			out.stmt();
      // NOTE: Current HDL for RAM differs from the Logisim simulation in two
      // important ways:
      //
      // (1) Read and clock ticks. In Logisim simulations, reads happen
      // asynchronously, i.e. the data output port always reflects the current
      // input address. In HDL, reads are latched: the data output port is
      // updated with a new value when the clock ticks. 
      //
      // (2) Read-during-write behavior. In Logisim simulations, writing has no
      // effect on reading. In HDL, during the time a write occurs (i.e. when
      // the clock ticks and write enable is turned on), the data output is not
      // updated, and the previously-read value is maintained instead.
      //
      // Note, however, that reads happen at the speed of the underlying global
      // clock, regardless of whether the clock has been divided down. So in
      // cases where the clock has been divided, HDL read behavior will be
      // closer to the Logisim read behavior.
      if (n == 1) {
        out.stmt("Mem0 : PROCESS( GlobalClock, DataIn0, Address, WE, ClockEnable )");
        out.stmt("BEGIN");
        out.stmt("   IF (GlobalClock'event AND (GlobalClock = '1')) THEN");
        out.stmt("      IF (WE = '1' and ClockEnable = '1') THEN");
        out.stmt("         s_mem0_contents(to_integer(unsigned(Address))) <= DataIn0;");
        out.stmt("      ELSE");
        out.stmt("          DataOut0 <= s_mem0_contents(to_integer(unsigned(Address)));");
        out.stmt("      END IF;");
        out.stmt("   END IF;");
        out.stmt("END PROCESS Mem0;");
      } else {
        int sa = (n == 4 ? 2 : n == 2 ? 1 : 0);
        for (int i = 0; i < n; i++) {
          out.stmt("Mem%d : PROCESS( GlobalClock, DataIn%d, Address, WE, LE%d, ClockEnable )", i, i, i);
          out.stmt("BEGIN");
          out.stmt("   IF (GlobalClock'event AND (GlobalClock = '1')) THEN");
          out.stmt("      IF (WE = '1' and LE%d = '1' and ClockEnable = '1') THEN", i);
          out.stmt("         s_mem%d_contents(to_integer(shift_right(unsigned(Address),%d))) <= DataIn%d;", i, sa, i);
          out.stmt("      ELSE");
          out.stmt("          DataOut%d <= s_mem%d_contents(to_integer(shift_right(unsigned(Address),%d)));", i, i, sa);
          out.stmt("      END IF;");
          out.stmt("   END IF;");
          out.stmt("END PROCESS Mem%d;", i);
        }
      }
			out.stmt();
      out.dedent();
			out.stmt("end logisim_generated;");
    } else {
      // todo: Verilog support
    }
		return out;
	}

  protected int addrWidth() {
    return _attrs.getValue(Mem.ADDR_ATTR).getWidth();
  }

  protected int dataWidth() {
    return _attrs.getValue(Mem.DATA_ATTR).getWidth();
  }

  @Override
  public boolean hdlDependsOnCircuitState() { // for NVRAM
    return _attrs.getValue(RamAttributes.ATTR_TYPE) == RamAttributes.NONVOLATILE;
  }

  @Override
  public boolean writeHDLFiles(String rootDir) {
    if (hdlDependsOnCircuitState())
      return true;
    else
      return super.writeHDLFiles(rootDir);
  }
      
  @Override
  public boolean writeAllHDLThatDependsOn(CircuitState cs, NetlistComponent comp,
      Path path, String rootDir) { // for NVRAM
    if (!hdlDependsOnCircuitState())
      return true;
    RamState state = cs == null ? null : (RamState)cs.getDataAsCustom(comp.original);
    if (state == null)
      _err.AddWarning("Non-volatile RAM %s initializion data not found in current "
          + "simulator state. The FPGA NVRAM will be initialized to zero instead.",
          path);

    return writeEntity(rootDir) && writeArchitecture(rootDir, state);
  }

  protected boolean writeArchitecture(String rootDir, RamState state) {
    Hdl hdl = getArchitecture(state);
		if (hdl == null || hdl.isEmpty()) {
			_err.AddFatalError("INTERNAL ERROR: Generated empty architecture for HDL `%s'.", hdlModuleName);
			return false;
		}
		File f = openFile(rootDir, false, false);
		if (f == null)
			return false;
		return FileWriter.WriteContents(f, hdl, _err);
	}

  private static final char HEX_DIGIT[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
  private String encodeMemInitDataVHDL(RamState state, int offset) {
    MemContents c = state.getContents();
    int skip = Mem.lineSize(_attrs);
    int width = dataWidth();
    int depth = (1 << addrWidth()) / skip;
    StringBuilder sb = new StringBuilder("X\"");
    long val = 0;
    int bits = 0;
    for (int a = 0; a < depth; a++) {
      long d = c.get(a * skip + offset);
      val = (val << width) | (d & ((1 << width)-1));
      bits += width;
      while (bits > 4) {
        sb.append(HEX_DIGIT[(int)(val >> (bits-4)) & 0xf]);
        bits -= 4;
      }
    }
    if (bits > 0) { // 1, 2, or 3 bits leftover
      val = val << (4-bits);
      sb.append(HEX_DIGIT[(int)val & 0xf]);
    }
    sb.append("\"");
    return sb.toString();
  }

}
