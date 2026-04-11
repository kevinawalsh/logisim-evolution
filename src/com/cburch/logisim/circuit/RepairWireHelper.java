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

package com.cburch.logisim.circuit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

class RepairWireHelper {

  // A clump is a set of two or more parallel and internally-connected wires,
  // e.g. connected end-to-end in a chain (which might double back on itself),
  // and/or overlapping (partially or fully).
  private static class Clump {
    final HashSet<Wire> wires = new HashSet<>();
    Location e0, e1;

    Clump(Wire w0, Wire w1) {
      e0 = w0.getEnd0();
      e1 = w1.getEnd1();
      add(w0);
      add(w1);
    }

    int size() {
      return wires.size();
    }

    void add(Wire w) {
      wires.add(w);
      if (w.e0.compareTo(e0) < 0) e0 = w.e0;
      if (w.e1.compareTo(e1) > 0) e1 = w.e1;
    }

    void addAll(Clump other) {
      wires.addAll(other.wires);
      if (other.e0.compareTo(e0) < 0) e0 = other.e0;
      if (other.e1.compareTo(e1) > 0) e1 = other.e1;
    }
  }

  private static class Clumpifier {
    final ArrayList<Clump> clumps = new ArrayList<>();
    final HashMap<Wire, Clump> map = new HashMap<>();

    void merge(Wire a, Wire b) {
      Clump cA = map.get(a);
      Clump cB = map.get(b);
      if (cA == null && cB == null) {
        Clump cAB = new Clump(a, b);
        clumps.add(cAB);
        map.put(a, cAB);
        map.put(b, cAB);
      } else if (cA == null && cB != null) {
        cB.add(a);
        map.put(a, cB);
      } else if (cA != null && cB == null) {
        cA.add(b);
        map.put(b, cA);
      } else if (cA != cB) { // neither is null, and they are different
        if (cA.size() < cB.size()) {
          // merge A (smaller) into B (larger)
          cB.addAll(cA);
          for (Wire w : cA.wires)
            map.put(w, cB);
        } else {
          // merge B (smaller) into A (larger)
          cA.addAll(cB);
          for (Wire w : cB.wires)
            map.put(w, cA);
        }
      }
    }
  }

  // Within current circuit, find chains of parallel, sequentially-connected
  // wires, and merge them into a single equivalent wire. The circuit is updated
  // with the changes. For example:
  //
  //   o---w1---o------w2------o--w3--o------------w4------------o
  //
  // gets merged into:
  //
  //   o--------------------------wnew---------------------------o
  //
  // Note: Intermediate points where wires connect must have no other component
  // connections. For example, this is not a single chain:
  //
  //   o---w1---o------w2------o--w3--o------------w4------------o
  //                           |
  //                           o
  //
  // The left and right halves would be each merged independently:
  //
  //   o---------wnew1---------o--------------wnew2--------------o
  //                           |
  //                           o
  //
  // Also note: A chain may double back on itself. It is still merged, using the
  // two most distant points for the ends of the new wire.
  private static void doMerges(Circuit circuit, CircuitMutator mutator) {
    Clumpifier repair = new Clumpifier();
    // Within current circuit, find all points where:
    //   - some wire ends
    //   - exactly two components have ends there
    //   - both are wires
    //   - and both are parallel to each other
    // and mark those as part of the same chain.
    for (Location loc : circuit.wires.points.getAllLocations()) {
      Collection<?> at = circuit.wires.points.getComponents(loc);
      if (at.size() == 2) {
        Iterator<?> atit = at.iterator();
        Object at0 = atit.next();
        Object at1 = atit.next();
        if (at0 instanceof Wire && at1 instanceof Wire) {
          Wire w0 = (Wire) at0;
          Wire w1 = (Wire) at1;
          if (w0.isParallel(w1)) {
            repair.merge(w0, w1);
          }
        }
      }
    }

    // For each chain, replace all the wires in the chain with one new wire.
    for (Clump clump : repair.clumps) {
      Wire wnew = Wire.create(clump.e0, clump.e1);
      // N>=1 wires are removed, replaced with a single (possibly existing)
      // wire. Note: none of these wires are .equal() to each other (because
      // they came from circuit, which doesn't have duplicates), and none are
      // .equal() to other wires in other clumps, which are either old (from
      // circuit) or new (and necessarily different, coming from a different
      // clump).
      // If wnew existed previously, or even if not, the code here is:
      //  - removing all the wires (possibly including wnew)
      //  - adding the new wire repeatedly (only the first addition matters)
      //  - and moving any old wire selection to wnew instead
      mutator.repairWires(circuit, clump.wires, Collections.singletonList(wnew));
    }
  }

  // Helper: Merges a set of parallel, overlapping wires into a chain of wires
  // broken only at midpoints where some other component is touching.
  private static void doMergeAndSplit(Circuit circuit, Clump clump,
      CircuitMutator mutator, Set<Location> allLocs) {

    // make a (possibly existing) wire spanning the entire clump of wires.
    Wire whole = Wire.create(clump.e0, clump.e1);

    // But whole may pass through locations where there are other components
    // connecting too, in which case it needs to be split at those midpoints.
    TreeSet<Location> mids = new TreeSet<>();
    mids.add(whole.e0);
    mids.add(whole.e1);
    for (Location loc : whole) {
      // whole passes through loc, check if we need to split here
      if (allLocs.contains(loc)) {
        for (Component comp : circuit.wires.points.getComponents(loc)) {
          // whole touches loc, and comp has an end there too
          if (!(comp instanceof Wire) || !clump.wires.contains((Wire)comp)) {
            // comp isn't one of the merging wires, so yes, need to split here
            mids.add(loc);
            break;
          }
        }
      }
    }

    // Create a set of wires spanning whole, split at each identified midpoint.
    ArrayList<Wire> pieces = new ArrayList<>();
    if (mids.size() == 2) {
      pieces.add(whole);
    } else {
      Location e0 = null;
      for (Location e1 : mids) {
        if (e0 != null)
          pieces.add(Wire.create(e0, e1));
        e0 = e1;
      }
    }

    // For each of the wires we are trying to merge...
    for (Wire w : clump.wires) {
      // Figure out which of the new wires it gets replaced by...
      HashSet<Wire> wRepl = new HashSet<>();
      for (Wire w2 : pieces)
        if (w2.overlaps(w, false /* exclude ends */))
          wRepl.add(w2);
      // Replace one old wire with some subset of the new (possibly existing) wires
      // Note: we don't check for it, but this could be doing a 1-to-1
      // replacement of a wire with itself.
      //
      // Note: none of the old wires are .equals() to each other, because they
      // all came from the circuit, which does not have duplicates. And the new
      // wires may or may not be equal to some old wires, but if they are, we
      // keep the w --> w replacements.
      mutator.repairWires(circuit, Collections.singletonList(w), wRepl);
    }
  }

  // Within current circuit, find sets of parallel, overlapping wires, and merge
  // them into a single equivalent wire. The circuit is updated with the
  // changes. For example:
  //
  //   o--------w1--------o
  //               o------w2------o
  //             o--w3--o
  //         o------------w4------------o
  //
  // gets merged into:
  //
  //   o--------------wnew--------------o
  //
  // Note: there won't be points where exactly 2 wires meet (they would have
  // been merged already by doMerges(), but there could be points where
  // 3 or more wires meet, and those need to be handled.
  private static void doOverlaps(Circuit circuit, CircuitMutator mutator) {
    // For each location, determine all wires ending at or passing through that location.
    HashMap<Location, ArrayList<Wire>> wirePoints = new HashMap<>();
    for (Wire w : circuit.getWires()) {
      for (Location loc : w) {
        // w ends at or passes through loc
        ArrayList<Wire> locWires = wirePoints.get(loc);
        if (locWires == null) {
          locWires = new ArrayList<>(3);
          wirePoints.put(loc, locWires);
        }
        locWires.add(w);
      }
    }

    // Find sets where 2 or more wires pass through or end at some common location...
    Clumpifier repair = new Clumpifier();
    for (ArrayList<Wire> locWires : wirePoints.values()) {
      if (locWires.size() > 1) {
        // for each pair w0, w1 in such a set...
        for (int i = 0, n = locWires.size(); i < n; i++) {
          Wire w0 = locWires.get(i);
          for (int j = i + 1; j < n; j++) {
            Wire w1 = locWires.get(j);
            // ... if they are parallel and overlapping, mark as part of the same chain
            if (w0.overlaps(w1, true /* include ends */))
              repair.merge(w0, w1);
          }
        }
      }
    }

    Set<Location> allLocs = circuit.wires.points.getAllLocations();
    for (Clump clump : repair.clumps)
      doMergeAndSplit(circuit, clump, mutator, allLocs);
  }

  // Within current, find and split wires that pass through locations where
  // other components have ends. The circuit is updated with the changes. For
  // example:
  //
  //              _|_  Buffer           _|___|_  Multiplexer
  //              \ /                   \_____/
  //   o----?------?----------w------------?-----------o
  //        |
  //        otherwire
  //
  // the long wire gets split into for wires:
  //
  //              _|_  Buffer           _|___|_  Multiplexer
  //              \ /                   \_____/
  //   o-w1-o--w2--o----------w3-----------o----w4-----o
  //        |
  //        w2
  //
  private static void doSplits(Circuit circuit, CircuitMutator mutator) {
    Set<Location> allLocs = circuit.wires.points.getAllLocations();
    HashMap<Wire, HashSet<Wire>> plan = new HashMap<>();
    // For each wire
    for (Wire w : circuit.getWires()) {
      // Find all split points
      ArrayList<Location> splits = null;
      for (Location loc : allLocs) {
        if (w.nominallyContains(loc) && !loc.equals(w.e0) && !loc.equals(w.e1)) {
          // something is at loc, and loc is in the middle of wire w,
          // so w needs to split
          if (splits == null)
            splits = new ArrayList<>();
          splits.add(loc);
        }
      }
      if (splits != null) {
        splits.add(w.e1);
        Collections.sort(splits);
        Location e0 = w.e0;
        HashSet<Wire> subs = new HashSet<>();
        for (Location e1 : splits) {
          subs.add(Wire.create(e0, e1));
          e0 = e1;
        }
        // A single wire is removed, replaced with a N>1 new wires.
        // Note: the new wires are not .equal() to each other or to the removed
        // wire, or to any other wires (because no other wire in the circuit is
        // parallel and overlapping with our w... earlier repairs would have
        // eliminated such cases).
        plan.put(w, subs);
      }
    }
    for (Map.Entry<Wire, HashSet<Wire>> e : plan.entrySet()) {
      Wire w = e.getKey();
      HashSet<Wire> subs = e.getValue();
      mutator.repairWires(circuit, Collections.singletonList(w), subs);
    }
  }

  public static void repairWires(Circuit circuit, CircuitMutator mutator) {
    doMerges(circuit, mutator);
    doOverlaps(circuit, mutator);
    doSplits(circuit, mutator);
  }
}
