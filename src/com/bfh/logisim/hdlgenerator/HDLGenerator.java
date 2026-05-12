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

import java.io.File;
import java.util.ArrayList;

import com.bfh.logisim.netlist.Net;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.ClockHDLGenerator;

public class HDLGenerator extends HDLSupport {

  // Details of input, output, and inout ports.
  protected static class PortInfo {
    public final String name; // signal name
    public final String width; // generic param (but not an expression) or constant integer
    public final int index; // port/end number within logisim Component and HDL NetlistComponent
    public final static int UNCONNECTED = -1; // When used as index, we just use default value.
    public final Boolean defaultValue; // only for inputs; null means port is required
    public PortInfo(String n, String w, int i, Boolean v) {
      name = n;
      width = w;
      index = i;
      defaultValue = v;
    }
  }

  public static class PortList extends ArrayList<PortInfo> {
    public void add(String name, String width, int index, Boolean defaultValue) {
      add(new PortInfo(name, width, index, defaultValue));
    }
    public void add(String name, int width, int index, Boolean defaultValue) {
      add(new PortInfo(name, ""+width, index, defaultValue));
    }
    public void addVector(String name, int width, int index, Boolean defaultValue) {
      // Note: the parens around "(1)" here are a hack to force vector creation.
      // See: Hdl.typeForWidth() and its use below.
      if (width > 0)
        add(new PortInfo(name, "("+width+")", index, defaultValue));
    }
  }

  // Details of wire signals.
  protected static class WireInfo {
    final String name; // signal name
    final String width; // generic param, or param expression, or constant integer
    WireInfo(String n, String w) {
      name = n;
      width = w;
    }
  }

  public static class WireList extends ArrayList<WireInfo> {
    public void add(String name, String width) {
      add(new WireInfo(name, width));
    }
    public void add(String name, int width) {
      add(new WireInfo(name, ""+width));
    }
    public void addVector(String name, int width) {
      // Note: the parens around "(1)" here are a hack to force vector creation.
      // See: Hdl.typeForWidth() and its use below.
      if (width > 0)
        add(new WireInfo(name, "("+width+")"));
    }
  }

  // Details of register signals.
  protected static class RegisterInfo extends WireInfo {
    final String initialValue; // null for no initial value
    RegisterInfo(String n, String w, String i) {
      super(n, w);
      initialValue = i;
    }
  }

  public static class RegisterList extends ArrayList<RegisterInfo> {
    public void add(String name, String width, String initialValue) {
      add(new RegisterInfo(name, width, initialValue));
    }
    public void add(String name, int width, String initialValue) {
      add(new RegisterInfo(name, ""+width, initialValue));
    }
  }

  // Details of generic parameters
  protected static class ParameterInfo {
    final String name; // param name
    final String type; // "integer", "natural", "positive", or "string" (only used for VHDL)
    final Object value; // Integer or String value for a given instance
    final Object defaultValue; // default Integer or String value that appears in declarations, or null
    public ParameterInfo(String n, String t, int v, int d) {
      name = n;
      type = t;
      value = v;
      defaultValue = d;
    }
    public ParameterInfo(String n, String t, String v, String d) {
      name = n;
      type = t;
      value = v;
      defaultValue = d;
    }
  }
  public static class Generics extends ArrayList<ParameterInfo> {
    public ParameterInfo get(String name) {
      for (ParameterInfo p : this)
        if (p.name.equals(name))
          return p;
      return null;
    }
    // public Integer getValue(String name) {
    //   for (ParameterInfo p : this)
    //     if (p.name.equals(name))
    //       return p.value;
    //   return null;
    // }
    public void add(String name, int val) {
      add(new ParameterInfo(name, "integer", val, 0));
    }
  }

  protected static class ClockPortInfo {
    String ckPortName, enPortName; // Port names to use for HDL
    final int index; // port/end number within logisim Component and HDL NetlistComponent
    public ClockPortInfo(String cn, String en, int i) {
      ckPortName = cn;
      enPortName = en;
      index = i;
    }
  }

  // Generic parameters and ports.
  protected final Generics parameters = new Generics();
  protected final PortList inPorts = new PortList();
  protected final PortList inOutPorts = new PortList();
  protected final PortList outPorts = new PortList();

  // Wires and registers, which appear before the "begin" statement in VHDL.
  protected final WireList wires = new WireList();
  protected final RegisterList registers = new RegisterList();

  // Components that have a clock input (like Keyboard, Tty, Register, etc.)
  // don't put the clock input into inPorts because the clock needs special
  // handling. The clockPort object, if not null, holds info about the clock
  // port allowing us to properly configure clock-related inputs for the
  // component. Generally, this means two inputs: one clock signal, and one
  // clock enable. Depending on user options (to set the global speed), Clock
  // component options (to set the shape for individual clock nets), and wiring
  // (to route various clock signals to the component clock ports), the two
  // clock inputs will either be the raw fpga clock, a derived clock signal, or
  // an inverted clock, and the enable will either be a constant 1 or will pulse
  // high for one period at appropriate times. HDL should be coded to use rising
  // edges (for edge-triggered components) or high level (for level-senstive
  // components). Below, we do the adaptions to handle falling edge or
  // active-low clocks for components so configured.
  protected ClockPortInfo clockPort = null;

  // Some components need direct access to the raw FPGA clock signal and the
  // ClockTick signal coming from the TickHDLGenerator (which gets added
  // automatically to the top-level circuit, and propagated into subcircuits as
  // needed (or always?)). Instead of using inPorts, these components put two
  // port names into tickerPort, which get mapped to the clock signal and clock
  // enable from the top-level Ticker.
  // ClockPortInfo tickerPort = null;

  // A suitable subdirectory for storing HDL files.
  protected final String subdir;

  // Name for HDL module (i.e. base name of HDL module for generated components,
  // like "Comparator". Must be unique, across the entire HDL project, for each
  // variation of generated HDL. This name becomes the name of the vhdl/verilog
  // file (with ".vhd" or ".verilog" extension added), and it becomes the name
  // of the vhdl/verilog entity for this component. 
  //
  // In most cases, all instances of some component type share the same HDL and
  // the same module name (e.g. "Counter"), or only share some small fixed set
  // of HDL variations with fixed module names (e.g. "BitAdder" for a 1-bit HDL
  // version using std_logic, versus "BusAdder" for a parameterized N-bit
  // version using std_logic_vector). In a few cases, like PLA, Rom, and
  // (eventually) non-volatile Ram, every instance will essentially have its own
  // unique HDL implementation generated based on the user-chosen contents of
  // the instance. In these cases, the module name will include the circuit name
  // and a unique ID (containing the user-chosen label, the location on the
  // sheet, and a sequence number if needed).
  public final String hdlModuleName;
  protected String getHDLModuleName() { return hdlModuleName; }

  // Prefix for generating pretty instance names. No need to be unique, as it
  // will get a unique ID suffix: "i_Add" will become "i_Add_1", "i_Add_2", etc.
  protected final String hdlInstanceNamePrefix;

  protected HDLGenerator(ComponentContext ctx, String subdir,
      String hdlModuleNameTemplate, String hdlInstanceNamePrefix) {
    super(ctx, false);
    this.subdir = subdir;
    this.hdlModuleName = deriveHDLModuleName(hdlModuleNameTemplate);
    this.hdlInstanceNamePrefix = hdlInstanceNamePrefix;
    this.vhdlLibraries.add(IEEE_LOGIC);
    this.vhdlLibraries.add(IEEE_NUMERIC);
  }

  // Generate and write all necessary HDL files for this component, using the
  // given root directory. Depeding on the specific subclass involved, this may
  // include entity, architecture, and several memory init files (for NV Ram).
  public boolean writeHDLFiles(String rootDir) {
    return writeEntity(rootDir) && writeArchitecture(rootDir);
  }

  // Generate and write an "architecture" file for this component, using the
  // given root directory.
  protected boolean writeArchitecture(String rootDir) {
    Hdl hdl = getArchitecture();
		if (hdl == null || hdl.isEmpty()) {
			_err.AddFatalError("INTERNAL ERROR: Generated empty architecture for HDL `%s'.", hdlModuleName);
			return false;
		}
		File f = openFile(rootDir, false, false);
		if (f == null)
			return false;
		return FileWriter.WriteContents(f, hdl, _err);
	}

  // Generate the full HDL code for the "architecture" file.
	protected Hdl getArchitecture() {
    return getArchitecture(false);
  }

	protected Hdl getArchitecture(boolean useTristates) {
    Hdl out = new Hdl(_lang, _err);
    generateFileHeader(out);

		if (out.isVhdl) {

			out.stmt("architecture logisim_generated of " + hdlModuleName + " is ");
      out.indent();

      generateVhdlTypes(out);
      declareNeededComponents(out);

      if (!wires.isEmpty() || !registers.isEmpty()) {
        for (WireInfo s : wires)
          out.stmt("signal %s : %s;", s.name, out.typeForWidth(s.width));
        for (RegisterInfo s : registers)
          if (s.initialValue == null)
            out.stmt("signal %s : %s;", s.name, out.typeForWidth(s.width));
          else
            out.stmt("signal %s : %s := %s;", s.name, out.typeForWidth(s.width), s.initialValue);
        out.stmt();
			}
      out.dedent();

			out.stmt("begin");

      out.indent();
			out.stmt();
      generateBehavior(out);
			out.stmt();
      out.dedent();

			out.stmt("end logisim_generated;");

    } else {

      ArrayList<String> portNames = new ArrayList<>();
      inPorts.forEach(p -> portNames.add(p.name));
      inOutPorts.forEach(p -> {
        if (useTristates) {
          portNames.add(p.name);
        } else {
          portNames.add(p.name+"_IN");
          portNames.add(p.name+"_OUT");
          portNames.add(p.name+"_EN");
        }
      });
      outPorts.forEach(p -> portNames.add(p.name));
      if (clockPort != null) {
        portNames.add(clockPort.ckPortName);
        portNames.add(clockPort.enPortName);
      }
      // if (tickerPort != null) {
      //   portNames.add(tickerPort.ckPortName);
      //   portNames.add(tickerPort.enPortName);
      // }
      if (hiddenPort != null) {
        for (String name : hiddenPort.inports)
          portNames.add(name);
        for (String name : hiddenPort.inoutports) {
          portNames.add(name+"_in");
          portNames.add(name+"_out");
          portNames.add(name+"_en");
        }
        for (String name : hiddenPort.outports)
          portNames.add(name);
      }
      out.stmt("module %s(\t %s );", hdlModuleName, String.join(",\n\t ", portNames));

      out.indent();
      for (ParameterInfo p : parameters)
        if (p.defaultValue != null)
          out.stmt("parameter %s = %s;", p.name, p.defaultValue); // note: verilog does not include type
        else
          out.stmt("parameter %s;", p.name); // note: verilog does not include type
			for (PortInfo p : inPorts)
				out.stmt("input %s%s;", out.typeForWidth(p.width), p.name);
			for (PortInfo p : inOutPorts) {
        if (useTristates) {
          out.stmt("inout %s%s;", out.typeForWidth(p.width), p.name);
        } else {
          out.stmt("input %s%s;", out.typeForWidth(p.width), p.name+"_IN");
          out.stmt("output %s%s;", out.typeForWidth(p.width), p.name+"_OUT");
          out.stmt("output %s%s;", out.typeForWidth(p.width), p.name+"_EN");
        }
      }
			for (PortInfo p : outPorts)
				out.stmt("output %s%s;", out.typeForWidth(p.width), p.name);
      if (clockPort != null) {
				out.stmt("input %s; // special clock signal", clockPort.ckPortName);
				out.stmt("input %s; // special clock enable", clockPort.enPortName);
      }
      // if (tickerPort != null) {
			// 	out.stmt("input %s%s; // special clock signal", clockPort.ckPortName);
      if (hiddenPort != null) {
        for (String name : hiddenPort.inports)
          out.stmt("input %s; // special hidden port", name);
        for (String name : hiddenPort.inoutports) {
          out.stmt("input %s_in; // special hidden bidir port", name);
          out.stmt("output %s_out; // special hidden bidir port", name);
          out.stmt("output %s_en; // special hidden bidir port", name);
        }
        for (String name : hiddenPort.outports)
          out.stmt("output %s; // special hidden port", name);
      }
      out.stmt();

      for (WireInfo s : wires)
        out.stmt("wire %s%s;", out.typeForWidth(s.width), s.name);
      if (!wires.isEmpty())
        out.stmt();

      for (RegisterInfo s : registers)
        if (s.initialValue == null)
          out.stmt("reg %s%s;", out.typeForWidth(s.width), s.name);
        else
          out.stmt("reg %s%s = %s;", out.typeForWidth(s.width), s.name, s.initialValue);
      if (!registers.isEmpty())
        out.stmt();

			out.stmt();
      generateBehavior(out);
			out.stmt();

      out.dedent();
			out.stmt("endmodule");

		}
		return out;
	}

  // Generate and write an "entity" file (for VHDL) for this component, using
  // the given root directory.
  protected boolean writeEntity(String rootDir) {
    if (!_lang.equals("VHDL"))
      return true;
    Hdl hdl = getVhdlEntity();
		if (hdl == null || hdl.isEmpty()) {
			_err.AddFatalError("INTERNAL ERROR: Generated empty entity for VHDL `%s'.", hdlModuleName);
			return false;
		}
		File f = openFile(rootDir, false, true);
		if (f == null)
			return false;
		return FileWriter.WriteContents(f, hdl, _err);
	}

	protected Hdl getVhdlEntity() {
    Hdl out = new Hdl(_lang, _err);
    generateFileHeader(out);
    generateVhdlLibraries(out);
    generateVhdlBlackBox(out, true);
    return out;
	}

	protected void generateComponentDeclaration(Hdl out) {
		if (_lang.equals("VHDL"))
      generateVhdlBlackBox(out, false);
	}

	protected void generateVhdlBlackBox(Hdl out, boolean isEntity) {
    generateVhdlBlackBox(out, isEntity, false);
  }

	protected void generateVhdlBlackBox(Hdl out, boolean isEntity, boolean useTristates) {
    if (isEntity)
      out.stmt("entity %s is", hdlModuleName);
    else
      out.stmt("component %s", hdlModuleName);

		if (!parameters.isEmpty()) {
      ArrayList<String> generics = new ArrayList<>();
      for (ParameterInfo p : parameters)
        if (p.defaultValue != null)
          generics.add(p.name + " : " + p.type + " := " + p.defaultValue);
        else
          generics.add(p.name + " : " + p.type);
      out.stmt("generic(\t %s );", String.join(";\n\t ", generics));
    }

		if (!inPorts.isEmpty() || !outPorts.isEmpty() || !inOutPorts.isEmpty()
        || clockPort != null
        || (hiddenPort != null && hiddenPort.numPorts() > 0)) {
      ArrayList<String> ports = new ArrayList<>();
      for (PortInfo s: inPorts)
        ports.add(s.name + " : in " + out.typeForWidth(s.width));
      for (PortInfo s: inOutPorts) {
        if (useTristates) {
          ports.add(s.name + " : inout " + out.typeForWidth(s.width));
        } else {
          ports.add(s.name + "_IN : in " + out.typeForWidth(s.width));
          ports.add(s.name + "_OUT : out " + out.typeForWidth(s.width));
          ports.add(s.name + "_EN : out " + out.typeForWidth(s.width));
        }
      }
      for (PortInfo s: outPorts)
        ports.add(s.name + " : out " + out.typeForWidth(s.width));
      if (clockPort != null) {
				ports.add(clockPort.ckPortName + " : in " + out.typeForWidth(1));
				ports.add(clockPort.enPortName + " : in " + out.typeForWidth(1));
      }
      if (hiddenPort != null) {
        for (String name : hiddenPort.inports)
          ports.add(name + " : in " + out.typeForWidth(1));
        for (String name : hiddenPort.inoutports) {
          if (useTristates) {
            ports.add(name + " : inout " + out.typeForWidth(1));
          } else {
            ports.add(name + "_in : in " + out.typeForWidth(1));
            ports.add(name + "_out : out " + out.typeForWidth(1));
            ports.add(name + "_en : out " + out.typeForWidth(1));
          }
        }
        for (String name : hiddenPort.outports)
          ports.add(name + " : out " + out.typeForWidth(1));
      }
      out.stmt("port(\t %s );", String.join(";\n\t ", ports));
		}

    if (isEntity)
      out.stmt("end %s;", hdlModuleName);
    else
      out.stmt("end component;");
		out.stmt();
	}

  // Generate an instantiation of this component.
  // VHDL Example:
  //  MyAdder_4 : BusAdder
  //  generic map( BitWidth => 8,
  //               OtherParam => 5 )
  //  port map( A => foo,
  //            B => bar,
  //            C => baz );
  //
  // Verilog Example:
  // BusAdder #( .BitWidth(8),
  //             .OtherParam(5) ) MyAdder_4 (
  //             .A(foo),
  //             .B(bar),
  //             .C(baz) );
	protected void generateComponentInstance(Hdl out, long id, NetlistComponent comp /*, Path path*/) {

		String instName = getInstanceName(id);
    Hdl.Map values = getPortMappings(comp);
    ArrayList<String> generics = new ArrayList<>();

		if (out.isVhdl) {
        
      for (ParameterInfo s : parameters)
          generics.add(s.name + " => " +  s.value);
      String g = String.join(",\n\t ", generics);
      String v = String.join(",\n\t ", values);

      if (parameters.isEmpty() && values.isEmpty())
        out.stmt("%s : %s;", instName, hdlModuleName);
      else if (parameters.isEmpty())
        out.stmt("%s : %s port map(\t %s );", instName, hdlModuleName, v);
      else if (values.isEmpty())
        out.stmt("%s : %s generic map(\t %s );", instName, hdlModuleName, g);
      else
        out.stmt("%s : %s generic map(\t %s ) port map (\t %s );",
            instName, hdlModuleName, g, v);

		} else {

      for (ParameterInfo s : parameters)
        generics.add(String.format(".%s(%s)", s.name, s.value));
      String g = String.join(",\n\t ", generics);
      String v = String.join(",\n\t ", values);

			if (generics.isEmpty() && values.isEmpty())
        out.stmt("%s %s;", hdlModuleName, instName);
      else if (generics.isEmpty())
        out.stmt("%s %s (\t %s );", hdlModuleName, instName, v);
      else if (values.isEmpty())
        out.stmt("%s #(\t %s ) %s;", hdlModuleName, g, instName);
      else
        out.stmt("%s #(\t %s ) %s ( %s );", hdlModuleName, g, instName, v);

		}
    // out.stmt();
	}

	protected void generateFileHeader(Hdl out) {
    out.comment("Logisim-Evolution Holy Cross Edition auto-generated %s code", _lang);
    out.comment("Project: %s", _projectName);
    out.comment("Module: %s", hdlModuleName);
    out.stmt();
	}

  // Generate external component declarations for any external components needed
  // by this component. These will appear within the VHDL architecture
  // declarations.
	protected void declareNeededComponents(Hdl out) { }

  // Generate any custom type declarations that should appear within the VHDL
  // declarations.
	protected void generateVhdlTypes(Hdl out) { }

  // Generate HDL code for the component, i.e. the actual behavioral RTL code.
  protected void generateBehavior(Hdl out) { }

  protected static final String IEEE_LOGIC = "std_logic_1164"; // standard + extended + extended2
  protected static final String IEEE_UNSIGNED = "std_logic_unsigned"; //                extended2
  protected static final String IEEE_NUMERIC = "numeric_std"; //             extended + extended2
  protected ArrayList<String> vhdlLibraries = new ArrayList<>();
	protected void generateVhdlLibraries(Hdl out) {
    if (vhdlLibraries.size() == 0)
      return;
    out.stmt("library ieee;");
    for (String lib : vhdlLibraries)
      out.stmt("use ieee.%s.all;", lib);
    out.stmt();
  }

  // Returns a file opened for writing.
	protected File openFile(String rootDir, boolean isMif, boolean isEntity) {
    return openFile(rootDir, isMif, isEntity, -1);
  }

	protected File openFile(String rootDir, boolean isMif, boolean isEntity, int idx) {
    if (!rootDir.endsWith(File.separator))
      rootDir += File.separator;
    String path = rootDir + _lang.toLowerCase() + File.separator + subdir + File.separator;
    return FileWriter.GetFilePointer(path, hdlModuleName, isMif, isEntity, idx, _err, _lang);
	}

  // Return a suitable stem for naming instances of this component within a
  // circuit, so we can form names like "i_Add_1", "i_Add_2", etc., for
  // instances of BusAdder and BitAdder. These need not be unique: the
  // CircuitHDLGeneratorFactory will add suffixes to ensure all the instance
  // names within a circuit are unique.
  @Override
	protected final String getInstanceNamePrefix() { return hdlInstanceNamePrefix; }

  // Return an instance name by combining the prefix with a unique ID.
	public final String getInstanceName(long id) { return hdlInstanceNamePrefix + "_" + id; }


  // Returns mappings of all input, inout, and output ports to signal names
  // or expressions for the "port map" of the given component instantiation.
  protected Hdl.Map getPortMappings(NetlistComponent comp) {
    Hdl.Map map = new Hdl.Map(_lang, _err);
    for (PortInfo port : inPorts)
      getPortMappings(map, comp, port);
    for (PortInfo port : inOutPorts)
      getPortMappings(map, comp, port);
    for (PortInfo port : outPorts)
      getPortMappings(map, comp, port);
    if (clockPort != null)
      getClockPortMappings(map, comp);
    if (hiddenPort != null)
      getHiddenPortMappings(map, comp);
    return map;
  }

  protected void getClockPortMappings(Hdl.Map map, NetlistComponent comp) {
    if (clockPort.index < 0 || clockPort.index >= comp.portConnections.size()) {
      _err.AddFatalError("INTERNAL ERROR: Clock port %d of '%s' is missing.",
          clockPort.index, hdlModuleName);
      map.add(clockPort.ckPortName, _hdl.zero);
      map.add(clockPort.enPortName, _hdl.zero);
      return;
    }
    Net net = comp.getConnection(clockPort.index);
    if (net == null) {
      _err.AddSevereWarning("ERROR: Clock port of '%s' in circuit '%s' is not connected.",
          hdlModuleName, _nets.circName());
      _err.AddSevereWarning("  The component will likely malfunction without a clock.");
      map.add(clockPort.ckPortName, _hdl.zero);
      map.add(clockPort.enPortName, _hdl.zero);
      return;
    }
    if (net.bitWidth() != 1) {
      _err.AddFatalError("INTERNAL ERROR: Clock port of '%s' in '%s' is connected to a bus.",
          hdlModuleName, _nets.circName());
      map.add(clockPort.ckPortName, _hdl.zero);
      map.add(clockPort.enPortName, _hdl.zero);
      return;
    }
    int clkid = _nets.getClockId(net);
    if (clkid < 0) {
      // The port is connected to a (potentially noisy) logic signal from
      // something other than a Clock. In this case, we use the noisy signal (or
      // it's inverse, for TRIG_LOW or TRIG_FALLING) as the clock, and set the
      // enable signal to one. Note: currently only Ram, Register, and some
      // FlipFlops can be level-sensitive in Logisim, but even here, only only
      // Register and the FlipFlops support it in HDL). All other cases are edge
      // triggered.
      if (edgeTriggered()) {
        _err.AddSevereWarning("WARNING: Non-clock signal (or \"gated clock\") connected to clock port for edge-triggered '%s' in circuit '%s'",
            hdlModuleName, _nets.circName());
        _err.AddSevereWarning("         Expect functional differences between Logisim simulation and FPGA behavior.");
      }
      if (edgeTriggered() && clockPort.ckPortName.equals("FPGAClock")) {
        _err.AddSevereWarning("         Actually, this component is almost certain to malfunction "
            + "unless it's clock input is connected to a proper clock signal, "
            + "due to explicit timing requirements in the synthesized HDL and "
            + "the need for signals to traverse multiple clock domains.");
      }
      String clkSignal = net.name;
      if (_attrs.getValue(StdAttr.EDGE_TRIGGER) == StdAttr.TRIG_FALLING 
          || _attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_FALLING
          || _attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_LOW) {
        clkSignal = _hdl.not + " " + clkSignal;
      }
      map.add(clockPort.ckPortName, clkSignal);
      map.add(clockPort.enPortName, _hdl.one);
    } else {
      // Here we have a proper connection to one of the clock buses that come
      // from a Clock component. We just need to select the right bits from it.
      String[] clkSignals = ClockHDLGenerator.clkSignalFor(this, clkid);
      map.add(clockPort.ckPortName, clkSignals[0]);
      map.add(clockPort.enPortName, clkSignals[1]);
    }
  }

  protected void getHiddenPortMappings(Hdl.Map map, NetlistComponent comp) {
    Netlist.Int3 id = comp.getLocalHiddenPortIndices().start;
    for (String name : hiddenPort.inports)
      map.add(name, "LOGISIM_HIDDEN_FPGA_INPUT", (id.in++));
    for (String name : hiddenPort.inoutports) {
      map.add(name + "_in", "LOGISIM_HIDDEN_FPGA_BIDIR_IN", (id.inout));
      map.add(name + "_out", "LOGISIM_HIDDEN_FPGA_BIDIR_OUT", (id.inout));
      map.add(name + "_en", "LOGISIM_HIDDEN_FPGA_BIDIR_EN", (id.inout++));
    }
    for (String name : hiddenPort.outports)
      map.add(name, "LOGISIM_HIDDEN_FPGA_OUTPUT", (id.out++));
  }

  protected void getUnconnectedPortMappings(Hdl.Map map, PortInfo p) {
    if (p.defaultValue == null)
      map.add(p.name, map.unconnected); // "name => open" or ".circName()"
    else if (p.width.equals("1"))
      map.add(p.name, map.bit(p.defaultValue)); // "name => '1'" or ".name(1'b1)"
    else 
      map.add(p.name, map.all(getWidth(p.width), p.defaultValue)); // "name => (others => '1')" or ".name(~0)"
  }

  private Integer getWidth(String width) {
    try { return Integer.parseInt(width); }
    catch (NumberFormatException e) { }
    if (width.startsWith("(") && width.endsWith(")")) {
      try { return Integer.parseInt(width.substring(1, width.length()-1)); }
      catch (NumberFormatException e) { }
    }
    ParameterInfo p = parameters.get(width);
    if (p == null || p.value == null || !(p.value instanceof Integer))
      return null;
    return (Integer)p.value;
  }

  protected void getPortMappings(Hdl.Map map, NetlistComponent comp, PortInfo p) {
    Net net = comp.getConnection(p.index);
    if (net == null) {
      // For output and inout ports, default value *should* be null, making
      // those ones be open. We do a sanity check unless p.index is out of
      // bounds (e.g. UNCONNECTED).
      if (p.index >= 0 && p.index < comp.original.getEnds().size()
          && comp.original.getEnd(p.index).canOutput() && p.defaultValue != null) {
        _err.AddSevereWarning("INTERNAL ERROR: ignoring default value for "
            + "output pin '%s' of '%s' in circuit '%s'.",
            p.name, hdlModuleName, _nets.circName());
      }
      getUnconnectedPortMappings(map, p);
    } else if (net.bitWidth() == 1 && !p.width.equals("1")) {
      // Special VHDL case: When port is anything other than width "1", it will
      // be a std_logic_vector. And if the end is 1 bit, then the port vector
      // must be of type std_logic_vector(0 downto 0), but the signal being
      // assigned is of type std_logic by necessity (since it is a single bit).
      // These can't be directly assigned using the normal
      //   "portname => signal"
      // mapping because of the mismatched types. We need to do
      //   "portname(0) => signal"
      // instead.
      // For verilog, there is no such mismatching issue.
      map.add0(p.name, net.name);
    } else {
      map.add(p.name, net.name);
    }
  }

  // Return a suitable HDL module name for this component (e.g. "BitAdder" or
  // "BusAdder") based on a template (e.g. "${BUS}Adder"). Only a few simple
  // string replacements are made here:
  //   ${WIDTH}   - replaced with StdAttr.WIDTH.
  //   ${BUS}     - replaced with "Bit" (StdAttr.WIDTH == 1) or "Bus" (otherwise).
  //   ${TRIGGER} - replaced with "LevelSensitive", "EdgeTriggered", or "Asynchronous"
  //                depending on StdAttr.TRIGGER and/or StdAttr.EDGE_TRIGGER.
  //   ${UID}     - replaced with string that is unique across the entire HDL
  //                project, containing circuit name, label, location, and a
  //                sequence number.
  //
  // Note: ${UID} is used for PLA, ROM, and anything else that needs an
  // essentially globally-unique ID. For ROM, the the generated HDL code depends
  // on the contents of the memory, which is too large of an attribute to simply
  // include in the HDL module name, as would be done for other HDL-relevant
  // attributes like ${WIDTH}. We could include a concise unique hash, perhaps.
  // But instead, we just make it entirely unique.
  private String deriveHDLModuleName(String nameTemplate) {
    String s = nameTemplate;
    if (s.contains("${WIDTH}"))
        s = s.replace("${WIDTH}", ""+stdWidth());
    if (s.contains("${BUS}"))
        s = s.replace("${BUS}", stdWidth() == 1 ? "Bit" : "Bus");
    if (s.contains("${TRIGGER}")) {
      if (edgeTriggered())
        s = s.replace("${TRIGGER}", "EdgeTriggered");
      else if (_attrs.containsAttribute(StdAttr.TRIGGER))
        s = s.replace("${TRIGGER}", "LevelSensitive");
      else
        s = s.replace("${TRIGGER}", "Asynchronous");
    }
    if (s.contains("${UID}")) {
      // Note: _nets and ctx.comp should not be null here, because none of the
      // top-level components (TopLevelHDLGenerator, the top-level
      // CircuitHDLGenerator, TickHDLGenerator, and ClockHDLGenerator) include
      // UID in their name.
      if (_nets == null || ctx.comp == null)
        _err.AddFatalError("INTERNAL ERROR: Missing netlist for component %s", nameTemplate);
      String u = _nets.circName().replaceAll("[^a-zA-Z0-9]{2,}", "_");
      // Append label
      String label = ctx.comp.original.getAttributeSet().getValueOrElse(StdAttr.LABEL, "");
      if (!label.isEmpty()) {
        label = label.replaceAll("[^a-zA-Z0-9]{1,}", "_");
        u += "_" + label;
      }
      Location loc = ctx.comp.original.getLocation();
      u += "_" + loc.x + "_" + loc.y;
      // prefix + circname + [label] + location will normally be unique, but we
      // include a seqno just in case user managed to place two components wth
      // same prefix exactly on top of each other (the GUI tries to prevent
      // this, but it is still possible if the user is careless with rotations,
      // or just due to various bugs and corner-cases the GUI does not yet
      // check).
      u += "_" + ctx.seqno();
      s = s.replace("${UID}", u);
    }
    s = s.replaceAll("[^a-zA-Z0-9]{1,}", "_");
    s = s.replaceAll("^_", "");
    s = s.replaceAll("_$", "");
    return s;
  }

}
