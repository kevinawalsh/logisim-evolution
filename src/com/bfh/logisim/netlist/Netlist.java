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

package com.bfh.logisim.netlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.CircuitHDLGenerator;
import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.bfh.logisim.library.DynamicClock;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Splitter;
import com.cburch.logisim.circuit.SplitterFactory;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Tunnel;

// Netlist holds info about the connectivity within a circuit based on the
// layout of wires, tunnels, splitters, and any component ports that touch each
// other. Netlist also holds other info about the circuit, for convenience:
// - inputs and outputs (based on Pin components within the circuit)
// - "normal" components (muxes, adders, vhdl entities, etc.)
// - subcircuit components
// - clock components within the circuit
// - dynamic clock control (if that component is present)
// - hidden nets (needed for Button, Tty, Keyboard, subcircuit, and other I/O components)
public class Netlist {

  // Global parameters used for one complete generate-synthesis-download effort.
  public static class Context {
    public final String lang;
    public final FPGAReport err;
    // public final char vendor;
    public final Circuit circUnderTest;
    public final long oscFreq;
    public final int clkPeriod; // -1 means dynamic, 0 means raw, >0 means reduced speed
    private final HashMap<Circuit, Netlist> circNets;
    public final ClockBus clockbus;
    public final Hdl hdl;
    private final int[] prevSeqno;
    public final HashMap<Object, String> uniqueHDLNames;
    public final HashSet<String> forbiddenHDLNames;

    public Context(String lang, FPGAReport err, /* char vendor,*/ Circuit root,
        long oscFreq, int clkPeriod) {
      this.lang = lang;
      this.err = err;
      // this.vendor = vendor;
      this.circUnderTest = root;
      this.oscFreq = oscFreq;
      this.clkPeriod = clkPeriod;
      this.circNets = new HashMap<>();
      this.clockbus = new ClockBus(oscFreq, clkPeriod);
      this.hdl = new Hdl(lang, err);
      this.prevSeqno = new int[1];
      this.uniqueHDLNames = new HashMap<>();
      this.forbiddenHDLNames = new HashSet<>();
    }

    protected Context(Context ctx) {
      lang = ctx.lang;
      err = ctx.err;
      // vendor = ctx.vendor;
      circUnderTest = ctx.circUnderTest;
      oscFreq = ctx.oscFreq;
      clkPeriod = ctx.clkPeriod;
      circNets = ctx.circNets;
      clockbus = ctx.clockbus;
      hdl = ctx.hdl;
      prevSeqno = ctx.prevSeqno;
      uniqueHDLNames = ctx.uniqueHDLNames;
      forbiddenHDLNames = ctx.forbiddenHDLNames;
    }

    public Netlist getNetlist(Circuit circ) {
      Netlist n = circNets.get(circ);
      if (n == null) {
        n = new Netlist(circ, this);
        circNets.put(circ, n);
      }
      return n;
    }

    public Netlist getNetlist(Component comp) {
      if (!(comp.getFactory() instanceof SubcircuitFactory))
        throw new IllegalArgumentException("expecting subcircuit shadow, got " + comp.getFactory());
      SubcircuitFactory sub = (SubcircuitFactory)comp.getFactory();
      return getNetlist(sub.getSubcircuit());
    }

    public Netlist getNetlist(NetlistComponent subcircShadow) {
      return getNetlist(subcircShadow.original);
    }

    public int seqno() {
      return ++prevSeqno[0];
    }

    public String sanitizeCircuitName(Circuit circ) {
      // A circuit named "Foo" will be put into an HDL module "Circuit_Foo" in a
      // file like "some/path/Circuit_Foo.vhd". For circuits that have a name
      // containing HDL-illegal characters, sanitize the name by replacing any
      // illegal character sequence with a single underscore. If that turns out
      // to not be unique, just add a unique prefix until it is.
      return sanitizeName(circ, "Circuit", circ.getName());
    }

    public String sanitizeName(Object obj, String prefix, String name) {
      String result = uniqueHDLNames.get(obj);
      if (result != null)
        return result;
      // VHDL names must:
      //  - use only valid characters
      //  - not contain two consecutive underscores
      //  - not end with an underscore
      //  - start with a letter (we assume prefix starts with a letter)
      //  - be unique (names are not case sensitive)
      String suffix = name.replaceAll("[^a-zA-Z0-9]{1,}", "_");
      suffix = suffix.replaceAll("^_", ""); // pfx will end in underscore
      suffix = suffix.replaceAll("_$", "");
      String pfx = prefix + "_";
      int i = 0;
      while (forbiddenHDLNames.contains((pfx + suffix).toUpperCase()))
        pfx = prefix + (++i) + "_";
      result = prefix + "_" + suffix;
      uniqueHDLNames.put(obj, result);
      forbiddenHDLNames.add(result.toUpperCase());
      return result;
    }

  }

  // Circuit and context for which we store connectivity info.
  public final Context ctx;	
  public final Circuit circ;

  // Signal nets, each defining a collection of connected points.
	public final ArrayList<Net> nets = new ArrayList<>();
  public final HashMap<Location, Net> netAt = new HashMap<>();

  // Shadow data for components within this circuit, arranged by factory type.
  public final ArrayList<NetlistComponent> components = new ArrayList<>(); // all but Splitter, Tunnel, and ignored
	public final ArrayList<NetlistComponent> subcircuits = new ArrayList<>(); // SubCircuitFactory
	public final ArrayList<NetlistComponent> clocks = new ArrayList<>(); // Clock
	public final ArrayList<NetlistComponent> inpins = new ArrayList<>(); // Pin (input)
	public final ArrayList<NetlistComponent> outpins = new ArrayList<>(); // Pin (output)
  private NetlistComponent dynClock; // DynamicClock

  // Info about hidden networks, needed by certain I/O-related components.
	private Int3 numHiddenBits = null; // initialized later
  
  // Path to one of the instantiations of this circuit. For a circuit that gets
  // instantiated several times, there is one Netlist for the circuit, and the
  // currentPath will rotate between the different instances.
	public Path currentPath; // set by CircuitHDLGenerator

  // Info about design rule checks.
	public static final int DRC_ERROR = -1;
	public static final int DRC_REQUIRED = 0;
	public static final int DRC_PASSED = 1;
	private int status = DRC_REQUIRED;

	public Netlist(Circuit circ, Context ctx) {
		this.circ = circ;
    this.ctx = ctx;
	}
  
  // Primary entry point for performing design-rule-check (DRC) validation on
  // the Netlist for the top-level circuit.
  public boolean validate() {

    // Recursively validate this Netlist and those of all subcircuits.
    if (!recursiveValidate(new ArrayList<>(), true)) {
      return false;
    }

    Path root = new Path(circ);

    // Recursively trace all clock nets to build the global clock bus.
    HashMap<Path, Netlist> netlists = new HashMap<>();
    recursiveEnumerateNetlists(root, netlists);
    recursiveTraceClockNets(root, netlists); // also adds hidden clock in ports

    // Recursively build other hidden nets.
    HashSet<Netlist> visited = new HashSet<>();
    recursiveAssignLocalHiddenNets(visited); // also adds hidden in/inout/out ports
    recursiveAssignGlobalHiddenNets(root, new Int3());

    // Sanity check for effectively-blank designs at the top level.
    if (inpins.size() + outpins.size() + numHiddenBits.size() == 0) {
      ctx.err.AddFatalError("Top-level circuit '%s' has no input pins, output pins, "
          + "or I/O devices, so would produce no visible behavior.", circ.getName());
      return false;
    }

    return true;
  }

  private boolean recursiveValidate(ArrayList<String> sheets, boolean isTop) {

    // Avoid re-validating when there are multiple instances of this circuit.
    if (status == DRC_PASSED)
      return true;
    else if (status == DRC_ERROR)
      return false;

    // Sanity check for identically named circuits or infinite recursion.
    String name = circ.getName();
    if (sheets.contains(name)) {
			ctx.err.AddFatalError("Multiple circuits in your design are named '%s'."
          + " Each circuit must have a unique name.", name);
      status = DRC_ERROR;
      return false;
    }
    sheets.add(name);

    // Perform the actual DRC and cache the result.
    if (!drc(sheets, isTop)) {
      status = DRC_ERROR;
      return false;
    }

    status = DRC_PASSED;
    return true;
  }

  private boolean drc(ArrayList<String> sheets, boolean isTop) {

    String circName = circ.getName();

    // DRC Step 1: Check for presence of tri-state devices or bidirectional
    // ports, neither of which are typically supported for HDL synthesis.
		ctx.err.AddInfo("Checking for tri-state drivers or bidirectional ports in circuit '%s'.", circName);
		for (Component comp : circ.getNonWires()) {
      // Note: Splitters and Tunnels can be bidirectional, as those cases are
      // handled as special cases in buildNets().
      if (comp.getFactory() instanceof SplitterFactory)
        continue;
      if (comp.getFactory() instanceof Tunnel) 
        continue;
      if (comp.getFactory().HDLIgnore())
        continue;
      if (comp.getFactory() instanceof Pin) {
        // For purposes of HDL, Pin never generates floating values, regardless of
        // attributes. So we skip the check below.
        //
        // Output pins always pass whatever is received from the connected bus up to
        // the parent. If that bus has a driver, the value will presumably be
        // non-floating. If the connected bus has no driver, that will be detected
        // by the HDL synthesis and flagged as an error. If there is no connected
        // bus, the Pin HDL generator will detect and raise an error.
        //
        // Input pins always pass whatever is received from the parent down into the
        // circuit. If the parent has a connected bus with a driver, the value will
        // presumably be non-floating. If the parent has an undriven connected bus,
        // that will be detected by the HDL synthesis and flagged as an error. If
        // the parent has no connected bus, the Pin HDL generator will either detect
        // and raise an error (for simple or tristate mode), or substitute a pull
        // value (for pullup or pulldown mode).
        //
        // FIXME: Perhaps Netlist should detect buses with no drivers, omit them,
        // and treat the port as if it were unconnected. In some cases this would
        // simply allow for earlier/better error reporting (output pins, input pins
        // in simple or tristate mode). In other cases, this would allow for
        // substituting the pull value. Currently, pull values are used by HDL when
        // the port is entirely disconnected, but in logisim syntehsis, they are
        // also used when the port is connected to an undriven bus.
      } else if (comp.getFactory().HasThreeStateDrivers(comp.getAttributeSet())) {
        return drcFail(comp, "component has tri-state output drivers or is configured "
            + "to allow floating outputs, features typically not supported for FPGA synthesis.");
      }
      for (EndData end : comp.getEnds())
        if (end.getWidth().getWidth() > 0 && end.isBidir())
          return drcFail(comp, "component has a bidirectional port, a feature not yet supported.");
    }

    // We can now skip this step: all names are made HDL-safe and sufficiently unique automatically.
    // DRC Step 2: Validate names and labels for a few basic component types.
    // if (!CorrectLabel.IsCorrectLabel(circName, ctx.lang, "Circuit has illegal name.", ctx.err))
    //   return false;
    // HashSet<String> pinNames = new HashSet<>();
		// for (Component comp : circ.getNonWires()) {
    //   if (comp.getFactory().HDLIgnore())
    //     continue; // Text, Probe, and other similarly irrelevant components
    //   if (comp.getFactory() instanceof Pin) {
		// 		String label = HDLSupport.deriveHdlPathName(comp);
    //     if (label == null || label.isEmpty())
    //       return drcFail(comp, "for HDL synthesis, each pin is required to have a unique label. "
    //           + "Add a label, or use the \"Annotate\" button above.");
		// 		if (!CorrectLabel.IsCorrectLabel(label,
		// 				ctx.lang, "Bad label for pin '"+nameOf(comp)+"' in circuit '"+circName+"'", ctx.err))
		// 			return false;
    //     if (pinNames.contains(label))
    //       return drcFail(comp, "pin has the same label as another pin in same circuit.");
    //     pinNames.add(label);
    //   } else if (comp.getFactory() instanceof SubcircuitFactory
    //       || comp.getFactory() instanceof VhdlEntity) {
    //     String compName = comp.getFactory().getName();
		// 		if (!CorrectLabel.IsCorrectLabel(compName,
		// 				ctx.lang, "Bad name for component '"+compName+"' in circuit '"+circName+"'", ctx.err))
		// 			return false;
		// 	}
    // }

    // DRC Step 3: Check connectivity (e.g. splitters, tunnels, wires) and build nets.
		ctx.err.AddInfo("Checking wire, tunnel, and splitter connectivity in circuit '%s'.", circName);
		if (!buildNets())
			return false;
    printNetlistStats();

    // DRC Step 4: Create NetlistComponent shadow objects for each Component,
    // and perform sanity checks.
		for (Component comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof SplitterFactory)
        continue;
      if (comp.getFactory() instanceof Tunnel) 
        continue;
      if (comp.getFactory().HDLIgnore())
        continue;
      NetlistComponent shadow = new NetlistComponent(comp, this);
      if (shadow.hdlSupport == null && !(comp.getFactory() instanceof Pin))
        return drcFail(comp, "component does not support HDL generation.");
      components.add(shadow);
      if (comp.getFactory() instanceof SubcircuitFactory) 
        subcircuits.add(shadow);
      else if (!isTop && shadow.hdlSupport != null && shadow.hdlSupport.hdlDependsOnCircuitState())
        return drcFail(comp, "component depends on circuit state, so may appear only in top level circuit.");
      else if (comp.getFactory() instanceof Clock) 
        clocks.add(shadow);
      else if (comp.getFactory() instanceof Pin && comp.getEnd(0).isOutputOnly())
        inpins.add(shadow);
      else if (comp.getFactory() instanceof Pin && comp.getEnd(0).isInputOnly())
        outpins.add(shadow);
      else if (comp.getFactory() instanceof DynamicClock) {
        if (isTop && dynClock == null)
          dynClock = shadow;
        else if (isTop)
          return drcFail(comp, "multiple dynamic clock controls found, but at most one allowed.");
        else
          return drcFail(comp, "dynamic clock control found in sub-circuit, but this is allowed only in main top-level circuit.");
      }
    }

    // DRC Step 5: Ensure labels are sufficiently unique. Components like LED,
    // DipSwitch, etc., need unique labels because they show up in the mapping
    // gui, or because the HDL they generate is specific to each instance.
    HashMap<String, Component> namedComponents = new HashMap<>();
    for (NetlistComponent shadow : components) {
      Component comp = shadow.original;
      if (comp.getFactory() instanceof Pin)
        continue; // handled above
      String name = shadow.pathName();
      if (name == null)
        continue;
      Component clash = namedComponents.put(name, comp);
      if (clash != null)
        return drcFail(comp, "component '%s' has same the same name '%s'. "
            + "Please change the labels, or use \"annotate\" above, to ensure "
            + "are unique within this circuit.", nameOf(clash), name);
    }

    // DRC Step 6: Recurse to validate subcircuits.
    for (NetlistComponent shadow : subcircuits) {
      Netlist subnets = ctx.getNetlist(shadow);
      if (!subnets.recursiveValidate(sheets, false))
        return false;
    }

		ctx.err.AddInfo("Circuit '%s' passed all design rule checks.", circName);
    return true;
	}

  private void printNetlistStats() {
    int n = 0, b = 0;
		for (Net net : nets) {
			if (net.isBus())
        b++;
      else
				n++;
    }
    ctx.err.AddInfo("Circuit '%s' contains %d signal nets and %d bus nets.", circ.getName(), n, b);
  }

  private String nameOf(Component comp) {
    String name = comp.getFactory().getName();
    String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
    if (label == null || label.isEmpty())
      return String.format("'%s' at %s", name, comp.getLocation().toString());
    else
      return String.format("'%s' with label '%s'", name, label);
  }
 
  private boolean drcFail(Component comp, String msg, Object ...args) {
    String prefix = String.format("Component %s in circuit '%s': ",
        nameOf(comp), circ.getName());
    ctx.err.AddSevereError(prefix + String.format(msg, args));
    return false;
  }

  public static class Int3 {
    // Note: All three counts are meant to be non-negative at all times.
    public int in, inout, out; // Note: these are deliberately mutable
    public Int3() { }
    public void clear() { in = inout = out = 0; }
    public Int3 copy() {
      Int3 o = new Int3();
      o.in = in;
      o.inout = inout;
      o.out = out;
      return o;
    }
    public void increment(Int3 amt) {
      in += amt.in;
      inout += amt.inout;
      out += amt.out;
    }
    public int size() {
      return in + inout + out;
    }
    public boolean isMixedDirection() {
      return (in > 0 && inout > 0)
          || (in > 0 && out > 0)
          || (inout > 0 && out > 0);
    }
    public Int3 forSingleBit() {
      Int3 bit = new Int3();
      bit.in = in > 0 ? 1 : 0;
      bit.inout = inout > 0 ? 1 : 0;
      bit.out = out > 0 ? 1 : 0;
      return bit;
    }
    @Override
    public String toString() {
      return String.format("{in:%d, inout:%d, out:%d}", in, inout, out);
    }
  }

  // this also assigns the numHiddenBits field
	private void recursiveAssignLocalHiddenNets(HashSet<Netlist> visited) {
    visited.add(this);

    Int3 start = new Int3();
		for (NetlistComponent shadow : subcircuits) {
      Netlist subnets = ctx.getNetlist(shadow);

      if (!visited.contains(subnets))
        subnets.recursiveAssignLocalHiddenNets(visited);

      Int3 count = subnets.numHiddenBits;
			shadow.setLocalHiddenPortIndices(start.copy(), count);
      start.increment(count);
    }

		for (NetlistComponent shadow : components) {
			if (shadow.hiddenPort == null) // matches subcircuits too
        continue;
      Int3 count = shadow.hiddenPort.width();
      shadow.setLocalHiddenPortIndices(start.copy(), count);
      start.increment(count);
    }

    numHiddenBits = start;
  }

	private void recursiveAssignGlobalHiddenNets(Path path, Int3 start) {
		for (NetlistComponent shadow : subcircuits) {
      Netlist subnets = ctx.getNetlist(shadow);
      Path subpath = path.extend(shadow);
      subnets.recursiveAssignGlobalHiddenNets(subpath, start);
    }

		for (NetlistComponent shadow : components) {
			if (shadow.hiddenPort == null) // matches subcircuits too
        continue;
      Path subpath = path.extend(shadow);
      Int3 count = shadow.hiddenPort.width();
      shadow.setGlobalHiddenPortIndices(subpath, start.copy(), count);
      start.increment(count);
    }
  }

  private static class CopperTrace extends HashSet<Location> {
    CopperTrace(Location pt) { add(pt); }
    CopperTrace(Wire w) { add(w.getEndLocation(0)); add(w.getEndLocation(1)); }
    boolean touches(CopperTrace other) {
      for (Location pt1 : this)
        for (Location pt2 : other)
          if (pt1.equals(pt2))
            return true;
      return false;
    }
  }

	private boolean buildNets() {
		LinkedList<CopperTrace> traces = new LinkedList<>();
   
    // Make a CopperTrace for every wire.
    for (Wire w : circ.getWires())
      traces.add(new CopperTrace(w));

    // Make a CopperTrace for every group of tunnels with the same name.
		HashMap<String, CopperTrace> tunnels = new HashMap<>();
    for (Component comp : circ.getNonWires()) {
      if (!(comp.getFactory() instanceof Tunnel))
        continue;
      String name = comp.getAttributeSet().getValue(StdAttr.LABEL);
      Location pt = comp.getEnd(0).getLocation();
      CopperTrace c = tunnels.get(name);
      if (c == null) {
        c = new CopperTrace(pt);
        traces.add(c);
        tunnels.put(name, c);
      } else {
        c.add(pt);
      }
    }

    // Make a CopperTrace for every component output port.
    for (Component comp : circ.getNonWires())
      for (EndData end : comp.getEnds())
        if (end.canOutput()) // same as isOutputOnly, since we don't support bidir yet
          traces.add(new CopperTrace(end.getLocation()));

    // Make a Net for each set of touching CopperTraces.
		while (traces.size() != 0) {
      CopperTrace c = traces.pop();
			Net net = new Net(c);
      LinkedList<CopperTrace> workingset = new LinkedList<>();
      workingset.add(c);
      while (!workingset.isEmpty()) {
        c = workingset.pop();
        Iterator<CopperTrace> it = traces.iterator();
        while (it.hasNext()) {
          CopperTrace c2 = it.next();
          if (c2.touches(c)) {
            workingset.add(c2);
            net.add(c2);
            it.remove();
          }
        }
      }
      nets.add(net);
		}

    // Create index mapping Location to Net containing that location.
    for (Net net : nets)
      for (Location pt : net.getPoints())
        netAt.put(pt, net);

    // Annotate each Net with appropriate bit width.
		for (Component comp : circ.getNonWires()) {
      for (EndData end : comp.getEnds()) {
        Location pt = end.getLocation();
        int w = end.getWidth().getWidth();
        if (w == 0)
          continue; // splitters can have zero-width ends, safe to ignore.
        Net net = netAt.get(pt);
        if (net == null)
          continue; // input ports can be unconnected, safe to ignore.
        int nw = net.bitWidth();
        if (nw == 0)
          net.setBitWidth(w);
        else if (nw != w)
          return drcFail(comp, "%d-bit port is connected to a %d-bit bus.", w, nw);
      }
    }

    // If a Net has zero width, it must be entirely unconnected (or perhaps
    // connected only to splitter ends with no connections), so remove it.
    for (Net net : nets)
      if (net.bitWidth() == 0)
        for (Location pt : net.getPoints())
          netAt.remove(pt);
    nets.removeIf(net -> net.bitWidth() == 0);

    // Give each Net a suitable name.
		for (Component comp : circ.getNonWires()) {
      // A Net connected to input pin "Foo" is named "p_Foo"
      if (!(comp.getFactory() instanceof Pin))
        continue;
      EndData end = comp.getEnd(0);
      if (!end.canOutput()) // same as isOutputOnly, since we don't support bidir yet
        continue;
      Net net = netAt.get(end.getLocation());
      if (net == null || net.name != null)
        continue;
      net.name = "p_" + HDLSupport.deriveHdlPathName(comp);
    }
		for (Component comp : circ.getNonWires()) {
      // A Net connected to output pin "Bar" is named "p_Bar"
      if (!(comp.getFactory() instanceof Pin))
        continue;
      EndData end = comp.getEnd(0);
      if (!end.canInput()) // same as isInputOnly, since we don't support bidir yet
        continue;
      Net net = netAt.get(end.getLocation());
      if (net == null || net.name != null)
        continue;
      net.name = "p_" + HDLSupport.deriveHdlPathName(comp);
    }
    // Other nets are named s_NET_A, s_BUS_B, ... s_NET_AAAA, etc.
    int id = 0; 
    for (Net net : nets) {
      if (net.name != null)
        continue;
      if (net.bitWidth() == 1)
        net.name = "s_NET_"+uid(id++);
      else
        net.name = "s_BUS_"+uid(id++);
    }

    // Every single-bit signal of every Net should be driven by zero or one
    // component ports and should drive zero or more component ports. First
    // identify all source and sink ports belonging to normal components like
    // Adder or AndGate (anything other than Splitter or Tunnel).
		for (Component comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof SplitterFactory)
        continue;
      if (comp.getFactory() instanceof Tunnel)
        continue;
      if (comp.getFactory().HDLIgnore())
        continue;
      int idx = -1;
      for (EndData end : comp.getEnds()) {
        idx++;
        if (end.getWidth().getWidth() == 0)
          continue;
        Location pt = end.getLocation();
        Net net = netAt.get(pt);
        if (end.isInputOnly()) { // same as canInput, since we don't support bidir yet
          if (net != null)
            net.addSink(comp, idx);
          else {
            String prefix = String.format("Component %s in circuit '%s': ",
                nameOf(comp), circ.getName());
            ctx.err.AddInfo("%s component is a sink, but no net connected at %s", prefix, pt);
          }
        } else {
          if (net == null)
            return drcFail(comp, "component is driving, but no net connected at %s", pt);
          Component c = net.getSourceComponent();
          if (c == null) {
            net.setSource(comp, idx);
          } else {
            if (c != comp)
              return drcFail(comp, "component is driving a bus that is also "
                  + "driven by component %s.", nameOf(c));
            int endidx = net.getSourceEnd();
            if (endidx != idx)
              return drcFail(comp, "port %d and port %d are both "
                  + "driving the same bus.", idx, endidx);
          }
        }
      }
    }

    // Next identify indirect sources and sinks due to splitters. End 0 of each
    // splitter is the "combined end", and will be connected to some Net with
    // matching width. Each bit of the combined end maps to one or none of the
    // other "split ends". Each split end can be the target for zero, one, or
    // multiple bits from the combined end. Example 5-bit, 4-way Splitter:
    //
    // End0{bit4,bit3,bit2,bit1,bit0}  ====\-- End1{bit4,bit0}
    //                                     |-- End2{bit1}
    //                                     |-- End3{(empty)}
    //                                     |-- End4{bit3}
    // Note: bit2 of the combined end does not map to any of the split ends, and
    // End3 is not the target of any bits from the combined end.
    //
    // If a bit is mapped to a split end, and the bit on one side (either the
    // combined or split end) already has a known source, then that source
    // becomes the source for the other side's bit (either the split or combined
    // end).
    //
    // If a bit is mapped to a split end, and the bit on *both* sides already
    // have a known source, then those sources better be identical otherwise we
    // have found a short circuit.
    //
    // If a bit is not mapped to a split end, then we can ignore the splitter
    // entirely for that bit, as it won't affect the bit.
   
    // Annotate each Net with the list of splitters the Net touches.
    for (Component comp : circ.getNonWires()) {
			if (comp.getFactory() instanceof SplitterFactory) {
        int idx = 0;
        for (EndData end : comp.getEnds()) {
          Net net = netAt.get(end.getLocation());
          net.splitters.add(new Net.SplitterPort((Splitter)comp, idx++));
        }
      }
    }

    // Propagate sources from Net, through Splitter, to other Nets.
    LinkedList<Net> workingset = new LinkedList<>();
    for (Net net : nets)
      if (net.getSourceComponent() != null)
        workingset.add(net);
    while (!workingset.isEmpty()) {
      Net net = workingset.pop();
      for (Net.SplitterPort sp : net.splitters) {
        int n = sp.splitter.getEnds().size();
        byte[] dest = sp.splitter.getEndpoints();
        if (sp.end == 0) {
          // net is the big combined end of splitter, and drives every bit of
          // small split ends (except those small split ends with width = 0,
          // but that gets ignored in the loop here, and except for bits in
          // net that aren't actually driven).
          for (int split = 1; split < n; split++) {
            Net small = netAt.get(sp.splitter.getEnd(split).getLocation());
            int s = -1;
            for (int b = 0; b < dest.length; b++) {
              if (dest[b] != split)
                continue; // net[b] does not map to this split end
              s++;
              // net[b] drives small[s]
              Net.Source src = net.getSourceForBit(b);
              if (src == null)
                continue; // actually, net[b] isn't driven, so nevermind
              Net.Source sink = small.getSourceForBit(s);
              if (sink == null) {
                small.setSourceForBit(s, net, b);
                workingset.add(small);
              } else if (!sink.equals(src)) {
                return drcFail(sp.splitter,
                    "splitter bit %d is being driven by both "
                    + " %s (port %d bit %d) and %s (port %d bit %d).",
                    b,
                    nameOf(src.comp), src.end, src.bit,
                    nameOf(sink.comp), sink.end, sink.bit);
              }
            } // for each bit in splitter
          } // for each small split end
        } else {
          // net is the small split end of splitter, and every bit of net is
          // being driven by some bit of big combined end (unless this split
          // end has width = 0, but that is filtered out above).
          Net big = netAt.get(sp.splitter.getEnd(0).getLocation());
          int s = -1;
          for (int b = 0; b < dest.length; b++) {
            if (dest[b] != sp.end)
              continue; // big[b] does not map to this small split end
            s++;
            // big[b] is driven by net[s]
            Net.Source src = net.getSourceForBit(s);
            if (src == null)
              continue; // actually, net[s] isn't driven, so nevermind
            Net.Source sink = big.getSourceForBit(b);
            if (sink == null) {
              big.setSourceForBit(b, net, s);
              workingset.add(big);
            } else if (!sink.equals(src)) {
              return drcFail(sp.splitter,
                  "splitter bit %d is being driven by both %s (port %d bit %d) "
                  + " and %s (port %d bit %d).",
                  b,
                  nameOf(src.comp), src.end, src.bit,
                  nameOf(sink.comp), sink.end, sink.bit);
            }
          } // for each bit in splitter
        }
      } // for each splitter touching this Net
    }

    // For each Net that has direct sinks, ensure that every bit of the Net is
    // driven by something, either directly (by a component on the same Net) or
    // indirectly (through one or more splitters and eventually by some
    // component on some other Net).
    for (Net net : nets) {
      if (!net.getSinks().isEmpty())
        continue; // no direct sinks, ignore
      if (net.getSourceComponent() != null)
        continue; // net has a direct source, done
      for (int b = 0; b < net.bitWidth(); b++) {
        if (net.getSourceForBit(b) == null) {
          Net.DirectSink sink = net.getSinks().get(0);
          return drcFail(sink.comp,
              "component port %d is connected to a %d-bit bus with some "
              + "undriven signals (e.g. bit %d)",
              sink.end, net.bitWidth(), b);
        }
      }
    }

    // If there are nets with all bits undriven, they could removed now. But we
    // don't bother, since these will cause synthesis warnings but not errors.
    return true;
  }

  private void recursiveEnumerateNetlists(Path path, HashMap<Path, Netlist> netlists) {
    netlists.put(path, this);
		for (NetlistComponent shadow : subcircuits) {
      Netlist subnets = ctx.getNetlist(shadow);
      Path subpath = path.extend(shadow);
      subnets.recursiveEnumerateNetlists(subpath, netlists);
    }
  }

	private void recursiveTraceClockNets(Path path, HashMap<Path, Netlist> netlists) {
    traceClockNets(path, netlists);
		for (NetlistComponent shadow : subcircuits) {
      Netlist subnets = ctx.getNetlist(shadow);
      Path subpath = path.extend(shadow);
      subnets.recursiveTraceClockNets(subpath, netlists);
    }
  }

  // Associate id for each clock source with all downstream signals.
	private void traceClockNets(Path path, HashMap<Path, Netlist> netlists) {
    for (NetlistComponent shadow : clocks) {
      Component clk = shadow.original;
      int clkid = ctx.clockbus.id(clk);
      Net clknet = netAt.get(clk.getEnd(0).getLocation());
      traceClockNet(clkid, clknet, 0, path, netlists);
    }
  }

  // Associate the signal Path:net:bit, and all downstream signals, with the
  // given clock id. We trace the signal down into subcircuits, up through
  // output pins to parent circuits, and across through tunnels (for free) and
  // splitters (below).
  private void traceClockNet(int clkid, Net net, int bit, Path path, HashMap<Path, Netlist> netlists) {

    // Start by marking Path:net:bit.
    if (!ctx.clockbus.associate(clkid, path, net, bit))
      return; // already marked this net and all downstream signals.

    // Then find and mark all downstream signals from net:bit.

    // Look for subcircuits and follow signal down into them.
    for (Net.DirectSink sink : net.getSinks()) {
      if (!(sink.comp.getFactory() instanceof SubcircuitFactory))
        continue;
      Netlist subnets = ctx.getNetlist(sink.comp);
      Path subpath = path.extend(sink.comp);
      NetlistComponent shadow = shadowForSubcirc(subpath);
      // within child, find the Pin that corresponds to this end
      CircuitHDLGenerator g = (CircuitHDLGenerator)shadow.hdlSupport;
      Net childNet = g.getInternalNetFor(shadow, sink.end);
      subnets.traceClockNet(clkid, childNet, bit, subpath, netlists);
    }

    // If in a subcircuit, look for output pins and follow them up to parent circuit.
    if (!path.isRoot()) {
      for (Net.DirectSink sink : net.getSinks()) {
        if (!(sink.comp.getFactory() instanceof Pin))
          continue;
        EndData end = sink.comp.getEnd(0);
        if (!end.isInputOnly()) // same as canInput, since we don't support bidir yet
          continue;
        Path parentpath = path.parent();
        Netlist parent = netlists.get(parentpath);
        // within parent, find the component that corresponds to this subcircuit
        NetlistComponent shadow = parent.shadowForSubcirc(path);
        // within that component, find the Net connecting to this Pin
        CircuitHDLGenerator g = (CircuitHDLGenerator)shadow.hdlSupport;
        Net parentNet = g.getPortMappingForPin(sink.comp);
        if (parentNet != null)
          parent.traceClockNet(clkid, parentNet, bit, parentpath, netlists);
      }
    }

    // Follow signal through splitters.
    for (Net.SplitterPort sp : net.splitters) {
      int n = sp.splitter.getEnds().size();
      byte[] dest = sp.splitter.getEndpoints();
      if (sp.end == 0) { // net goes into big combined end
        int end2 = dest[bit];
        if (end2 < 0)
          continue; // net:bit doesn't map to any of the small split ends
        Net net2 = netAt.get(sp.splitter.getEnd(end2).getLocation());
        int bit2 = 0;
        for (int b = 0; b < bit; b++) {
          if (dest[b] == end2)
              bit2++; // net:bit signal comes after any lower numbered net:b for same end
        }
        traceClockNet(clkid, net2, bit2, path, netlists);
      } else { // net goes into small split end
        Net net2 = netAt.get(sp.splitter.getEnd(0).getLocation());
        int bit1 = 0;
        for (int bit2 = 0; bit2 < dest.length; bit2++) {
          if (dest[bit2] == sp.end && bit1 == bit) {
            traceClockNet(clkid, net2, bit2, path, netlists);
            break;
          } else if (dest[bit2] == sp.end) {
            bit1++;
          }
        }
      }
    }

	}

  private NetlistComponent shadowForSubcirc(Path childPath) {
    String name = childPath.tail();
		for (NetlistComponent shadow : subcircuits) {
      if (name.equals(shadow.pathName()))
        return shadow;
    }
    return null;
  }

  // Return a collection of paths and components that need to be assigned
  // FPGA I/O resources.
	public HashMap<Path, NetlistComponent> getMappableComponents() {
    HashMap<Path, NetlistComponent> result = new HashMap<>();
    getMappableComponents(result, new Path(circ));
    return result;
  }

	private void getMappableComponents(HashMap<Path, NetlistComponent> result, Path path) {
    // components in subcircuits
    for (NetlistComponent shadow : subcircuits) {
      Netlist subnets = ctx.getNetlist(shadow);
      Path subpath = path.extend(shadow);
      subnets.getMappableComponents(result, subpath);
    }
    // local components
		for (NetlistComponent shadow : components) {
			if (shadow.hiddenPort == null)
        continue;
      Path subpath = path.extend(shadow);
      result.put(subpath, shadow);
    }
    // top-level pins
    if (path.isRoot()) {
      for (NetlistComponent shadow : inpins) {
        Path subpath = path.extend(shadow);
        result.put(subpath, shadow);
      }
      for (NetlistComponent shadow : outpins) {
        Path subpath = path.extend(shadow);
        result.put(subpath, shadow);
      }
    }
	}

  public NetlistComponent dynamicClock() {
    return dynClock;
  }

  public Int3 numHiddenBits() {
    return numHiddenBits.copy();
  }

  private String uid(int i) {
    String s = "";
    do {
      s = ('A' + (i % 26)) + s;
      i /= 26;
    } while (i > 0);
    return s;
  }

  public int getClockId(Net net) {
    return ctx.clockbus.id(currentPath, net, 0);
  }

  public String circName() {
    return circ.getName();
  }

}
