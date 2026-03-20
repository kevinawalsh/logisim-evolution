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

package com.cburch.logisim.access;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Splitter;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Probe;
import com.cburch.logisim.std.wiring.PullResistor;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.tools.Library;

// Connectivity is a utility for exporting a simple netlist-like description
// of the connectivity with a circuit. For example:
//
// net n1 [1-bit]:
//   Pin "A" @(150,120) .OUT [out]
//   Tunnel "sig_a" @(200,150) [bidir]
//   AND Gate @(300,170) .IN1 [in]
//   AND Gate @(300,250) .IN2 [in]
//   wire (150,120) to (200,150)
//   wire (200,150) to (300,170)
//   wire (200,150) to (300,250)
// 
// net n2 [ERROR: width conflict - all net connections must have same width]:
//   Pin "D" @(150,180) .OUT [out]
//   Adder @(300,200) .IN1 [in]
//   wire (150,180) to (300,200)
// 
// net n3 [no connections]:
//   wire (200,330) to (200,360)
//   wire (200,360) to (240,360)
//
// This code is adapted from com.bfh.logisim.netlist.Netlist, which generates a
// similar netlist-like connectivity data structure. That code, however, is
// designed for HDL synthesis. It is more complex, and it both enforces and
// relies on HDL-specific design rules checks, which we don't want to enforce or
// assume here. For example, we build a datastructure even if there are width
// incompatibilities, rather than failing.

public class Connectivity {
  
  private LogisimFile.FileWithSimulations circFile;
  private Circuit circ;
  private ArrayList<Net> nets = new ArrayList<>();
  private HashMap<Location, Net> netAt = new HashMap<>();

  public static void exportTo(String outputFile, LogisimFile.FileWithSimulations circFile, String circName) {
    if (outputFile.equals("-"))
      outputFile = null;
    PrintWriter out = null;
    try {

      Circuit circ;
      if (circName != null) {
          circ = circFile.file.getCircuit(circName);
        if (circ == null) {
          System.err.printf("Circuit not found: '%s'\n", circName);
          System.exit(-1);
        }
      } else {
        circ = circFile.file.getMainCircuit();
      }

      Connectivity c = new Connectivity(circFile, circ);

      if (outputFile == null)
        out = new PrintWriter(System.out);
      else
        out = new PrintWriter(new FileWriter(outputFile));
      c.write(out);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (out != null) {
        out.flush();
        if (outputFile != null)
          out.close();
      }
    }
  }

  Connectivity(LogisimFile.FileWithSimulations circFile, Circuit circ) {
    this.circFile = circFile;
    this.circ = circ;
    buildNets();
  }

  void write(PrintWriter out) {
    int i = 0;
    for (Net net : nets)
      if (!net.connections.isEmpty())
        net.write(++i, out);
    for (Net net : nets)
      if (net.connections.isEmpty())
        net.write(++i, out);
  }

  // Net holds info about a single contiguous network (1-bit signal or w-bit bus)
  // within a circuit.
  // - Each net has a uniform width throughout.
  // - Every output or bidirectional port is part of a Net.
  // - Every splitter end is part of a Net.
  // - The two ends of a wire are part of the same Net.
  // - Tunnels with the same name are part of the same Net.
  // - Input-only ports that are touching one of the above are part of that Net.
  // - Other input ports, which are touching nothing, or touching only other input ports,
  //   are not part of any Net, and are considered unconnected.
  private class Net {
    // Bit width of this network.
    int width = 0; // 0 means not yet computed, or not well defined
    boolean incompatibleWidths = false;
    // Points within this network.
    CopperTrace copper;
    // Component ends (ports) connected to this network, including splitters,
    // tunnels, and probes.
    ArrayList<ComponentEnd> connections = new ArrayList<>();
    // Wires that form part of this network.
    ArrayList<Wire> wires = new ArrayList<>();
    // Statistics for "normal components", i.e. excluding splitters, tunnels,
    // and probes. We skip splitters, tunnels, and probes because they are fully
    // passive, and *never* drive values, even though they are implemented like
    // bidirectional ports. For other components, we try to classify into ports
    // that always drive values, and ports that only sometimes drive values.
    int numAlwaysActiveOutputDrivers, numSometimesActiveOutputDrivers;

    Net(CopperTrace c) { copper = c; }

    void write(int id, PrintWriter out) {
      if (id != 0)
        out.println();
      String msg;
      if (incompatibleWidths)
        msg = "ERROR: width conflict - all net connections must have same width";
      else if (connections.isEmpty())
        msg = "no connections";
      else if (width == 0)
        msg = "width indeterminate";
      else
        msg = "["+width+"-bit]";
      out.printf("net n%d [%s]\n", id, msg);
      for (ComponentEnd c : connections)
        out.printf("  %s\n", c);
      for (Wire w : wires)
        out.printf("  Wire %s to %s\n", w.getEndLocation(0), w.getEndLocation(1));
    }
  }

  // ComponentEnd idetifies one of the ports of a Component.
  private class ComponentEnd {
    Component comp;
    int endIndex;
    ComponentEnd(Component c, int i) { comp = c; endIndex = i; }

    @Override
    public String toString() {
      Library lib = circFile.file.findLibraryFor(comp.getFactory());
      EndData end = comp.getEnds().get(endIndex);
      String libName = lib.getName();
      String compName = comp.getFactory().getName();
      String portName = ComponentListingExporter.getNameFromToolTipOrDefault(comp, endIndex);
      Location loc = comp.getLocation();
      Location portLoc = end.getLocation().translate(-loc.x, -loc.y);
      String portDir = end.isInputOnly() ? "in" : end.isOutputOnly() ? "out" : "bidir";
      return String.format("'%s:%s'@%s .%s@%s [%s]", libName, compName, loc, portName, portLoc, portDir);
    }
  }

  // CopperTrace is a set of Location points that are part of the same connected
  // net, i.e. a subset of the Location points in a Net. Unlike a Net, a trace
  // doesn't yet have a name, we don't track bit widths of the points, etc.
  // To create Nets, we:
  // 1. Create small CoppperTraces, one for each each component port and wire
  //    segment.
  // 2. Merge CopperTraces that are touching, repeatedly.
  // 3. Each remaining COpperTrace becomes a Net.
  private static class CopperTrace extends HashSet<Location> {
    // Create a CopperTrace for a component port
    CopperTrace(EndData e) {
      add(e.getLocation());
    }

    // Create a CopperTrace for a wire segment 
    CopperTrace(Wire w) {
      add(w.getEndLocation(0));
      add(w.getEndLocation(1));
    }

    // Check whether this touches the other.
    boolean touches(CopperTrace other) {
      return !Collections.disjoint(this, other);
    }

    @Override
    public String toString() {
      String s = "["+size()+" points]{";
      for (Location loc : this) {
        s += loc + ",";
      }
      s += "}";
      return s;
    }
  }

  private void buildNets() {
    LinkedList<CopperTrace> traces = new LinkedList<>();

    // Make a CopperTrace for every wire.
    for (Wire w : circ.getWires()) {
      traces.add(new CopperTrace(w));
    }

    // Make a CopperTrace for every group of tunnels with the same name.
    HashMap<String, CopperTrace> tunnels = new HashMap<>();
    for (Component comp : circ.getNonWires()) {
      if (!(comp.getFactory() instanceof Tunnel))
        continue;
      String name = comp.getAttributeSet().getValue(StdAttr.LABEL);
      EndData e = comp.getEnd(0);
      CopperTrace c = tunnels.get(name);
      if (c == null) {
        c = new CopperTrace(e);
        traces.add(c);
        tunnels.put(name, c);
      } else {
        c.add(e.getLocation());
      }
    }

    // Make a CopperTrace for every other component output or bidirectional port.
    for (Component comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof Tunnel)
        continue;
      for (EndData end : comp.getEnds()) {
        // For a splitter, a zero-width end is entirely ignored
        if (end.getWidth().getWidth() == 0 && comp instanceof Splitter)
          continue;
        if (end.canOutput())
          traces.add(new CopperTrace(end));
      }
    }

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
            net.copper.addAll(c2);
            it.remove();
          }
        }
      }
      nets.add(net);
    }

    // Create index mapping Location to Net containing that location.
    for (Net net : nets) {
      for (Location pt : net.copper)
        netAt.put(pt, net);
    }

    // Add each Wire to the Net that contains it.
    for (Wire w : circ.getWires()) {
      Net net = netAt.get(w.getEndLocation(0));
      net.wires.add(w);
    }

    // Add ComponentEnds to each Net, and determine Net bit width.
    for (Component comp : circ.getNonWires()) {
      ComponentFactory factory = comp.getFactory();
      List<EndData> ends = comp.getEnds();
      for (int i = 0; i < ends.size(); i++) {
        EndData end = ends.get(i);
        int w = end.getWidth().getWidth();
        if (w == 0) {
          // splitters can have zero-width ends... ignore them.
          if (comp instanceof Splitter)
            continue;
          // Probe and PullResistor both have zero-width ends, and they
          // internally adapt to whatever they touch. Keep them. But warn if we
          // find others zero-width ends.
          if (!(factory instanceof Probe) && !(factory instanceof PullResistor))
            System.err.printf("WARNING: Unknown width for %s port[%d].\n", comp, i);
        }
        Net net = netAt.get(end.getLocation());
        if (net == null) {
          if (!end.isInputOnly())
            System.err.printf("ERROR: Can't find net for %s port[%d]\n", comp, i);
          continue; // input-only ports can be unconnected, we ignore them.
        }
        if (w == 0) {
          // auto-adapting ports don't tell us anything
        } if (net.width == 0) {
          net.width = w;
        } else if (net.width != w) {
          net.incompatibleWidths = true;
        }
        net.connections.add(new ComponentEnd(comp, i));
        if (end.canOutput() && !(factory instanceof Tunnel) && !(comp instanceof Splitter)) {
          if (end.isBidir() || factory.HasThreeStateDrivers(comp.getAttributeSet()))
            net.numSometimesActiveOutputDrivers++;
          else
            net.numAlwaysActiveOutputDrivers++;
        }
      }
    }

    for (Net net : nets)
      if (net.connections.isEmpty() && net.wires.isEmpty())
        System.out.println("huh? : " + net.copper);
    // Cull Nets with only one connection and no wires ... these come from
    // output ports that aren't actually connected to anything.
    nets.removeIf(net -> net.connections.size() <= 1 && net.wires.isEmpty());

  }

}
