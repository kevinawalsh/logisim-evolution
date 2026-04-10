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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

class RepairWireHelper {

  private static class MergeSets {
    private final HashMap<Wire, ArrayList<Wire>> map = new HashMap<>();

    Collection<ArrayList<Wire>> getMergeSets() {
      IdentityHashMap<ArrayList<Wire>, Boolean> lists;
      lists = new IdentityHashMap<>();
      for (ArrayList<Wire> list : map.values()) {
        lists.put(list, Boolean.TRUE);
      }
      return lists.keySet();
    }

    void merge(Wire a, Wire b) {
      ArrayList<Wire> set0 = map.get(a);
      ArrayList<Wire> set1 = map.get(b);
      if (set0 == null && set1 == null) {
        set0 = new ArrayList<>(2);
        set0.add(a);
        set0.add(b);
        map.put(a, set0);
        map.put(b, set0);
      } else if (set0 == null && set1 != null) {
        set1.add(a);
        map.put(a, set1);
      } else if (set0 != null && set1 == null) {
        set0.add(b);
        map.put(b, set0);
      } else if (set0 != set1) { // neither is null, and they are different
        if (set0.size() > set1.size()) { // ensure set1 is the larger
          ArrayList<Wire> temp = set0;
          set0 = set1;
          set1 = temp;
        }
        set1.addAll(set0);
        for (Wire w : set0) {
          map.put(w, set1);
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
    MergeSets sets = new MergeSets();
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
            sets.merge(w0, w1);
          }
        }
      }
    }

    // For each chain, replace all the wires in the chain with one new wire.
    for (ArrayList<Wire> mergeSet : sets.getMergeSets()) {
      // FIXME: if mergeSet kept track of largest and smallest point, would not
      // need to sort here. Also, code here uses ArrayList, other code uses
      // TreeSet for sorting... not sure why.
      ArrayList<Location> locs = new ArrayList<>(2 * mergeSet.size());
      for (Wire w : mergeSet) {
        locs.add(w.getEnd0());
        locs.add(w.getEnd1());
      }
      Collections.sort(locs);
      Location e0 = locs.get(0);
      Location e1 = locs.get(locs.size() - 1);
      Wire wnew = Wire.create(e0, e1);

      // N>=1 wires are removed, replaced with a single (possibly existing) wire.
      // Note: none of these wires are .equal() to each other (because they came
      // from circuit, which doesn't have duplicates), and none are .equal() to
      // other wires in repl, which are either old (from circuit) or new (and
      // necessarily different, coming from a different mergeSet).
      // If wnew existed previously, then the code here is:
      //  - removing all the other wires (skipping wnew)
      //  - adding the new wire repeatedly (which does nothing, since it already
      //    exists),
      //  - and moving any old wire selection to wnew instead
      // But if wnew did not exist previously, then the code here is :
      //  - removing all the wires,
      //  - adding the new wire repeatedly (only the first addition matters)
      //  - and moving any old wire selection to wnew instead
      // Note: if we remove wnew, we gain nothing, but could change an N-to-1
      // replacement (N>1) into to just a 1-to-1 replacement.

      // mergeSet.remove(wnew); // don't bother recording wnew --> wnew, I guess? FIXME Does it matter?
      // for (Wire wold : mergeSet)
      //  mutator.repairWires( wold, wnew);

      mutator.repairWires(circuit, mergeSet, Collections.singletonList(wnew));

      // Note: repl is using append-style semantics, but because none of the
      // wires are .equal(), this is equivalent to simultaneous replacements.
    }
  }

  // Helper: Merges a set of parallel, overlapping wires into a chain of wires
  // broken only at midpoints where some other component is touching.
  private static void doMergeAndSplit(Circuit circuit, ArrayList<Wire> mergeSet,
      CircuitMutator mutator, Set<Location> allLocs) {
    // FIXME: if mergeSet kept track of largest and smallest point, would not
    // need to sort here. Also, code here uses TreeSort, other code uses
    // ArrayList for sorting... not sure why.
    TreeSet<Location> ends = new TreeSet<>();
    for (Wire w : mergeSet) {
      ends.add(w.getEnd0());
      ends.add(w.getEnd1());
    }
    Wire whole = Wire.create(ends.first(), ends.last());

    // whole is a (possibly existing) wire spanning an entire set of overlapping
    // parallel wires. But it may pass through locations where there are other
    // components connecting too, in which case whole needs to be split at those
    // midpoints.

    TreeSet<Location> mids = new TreeSet<>();
    mids.add(whole.getEnd0());
    mids.add(whole.getEnd1());
    for (Location loc : whole) {
      // whole passes through loc, check if we need to split here
      if (allLocs.contains(loc)) {
        for (Component comp : circuit.wires.points.getComponents(loc)) {
          // whole touches loc, and comp has an end there too
          if (!mergeSet.contains(comp)) {
            // comp isn't one of the merging wires, so yes, need to split here
            mids.add(loc);
            break;
          }
        }
      }
    }

    // Create a set of wires spanning whole, split at each identified midpoint.
    ArrayList<Wire> mergeResult = new ArrayList<>();
    if (mids.size() == 2) {
      mergeResult.add(whole);
    } else {
      Location e0 = null;
      for (Location e1 : mids) {
        if (e0 != null)
          mergeResult.add(Wire.create(e0, e1));
        e0 = e1;
      }
    }

    // For each of the wires we are trying to merge...
    for (Wire w : mergeSet) {
      // Figure out which of the new wires it gets replaced by...
      HashSet<Wire> wRepl = new HashSet<>();
      for (Wire w2 : mergeResult) {
        if (w2.overlaps(w, false)) {
          wRepl.add(w2);
        }
      }
      // Replace one old wire with some subset of the new (possibly existing) wires
      // Note: we don't check for it, but this could be doing a 1-to-1
      // replacement of a wire with itself.
      mutator.repairWires(circuit, Collections.singletonList(w), wRepl);
      // Note: repl is using append-style semantics, but I don't think it
      // matters here... none of the old wires are .equals() to each other,
      // because they all came from the circuit, which does not have duplicates.
      // And the new wires may or may not be equal to some old wires, but if
      // they are, we keep the w --> w replacements, so I think the
      // append-semantics is harmless. Maybe.
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
  // been merged already by doMerges(), but I think there could be points where
  // 3 or more wires meet, and those need to be handled (but aren't yet) I think.
  // FIXME
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
    MergeSets mergeSets = new MergeSets();
    for (ArrayList<Wire> locWires : wirePoints.values()) {
      if (locWires.size() > 1) {
        // for each pair w0, w1 in such a set...
        for (int i = 0, n = locWires.size(); i < n; i++) {
          Wire w0 = locWires.get(i);
          for (int j = i + 1; j < n; j++) {
            Wire w1 = locWires.get(j);
            // ... if they are parallel and overlapping, mark as part of the same chain
            if (w0.overlaps(w1, false /*don't include ends*/)) // FIXME: why exclude ends?
              mergeSets.merge(w0, w1);
          }
        }
      }
    }

    Set<Location> allLocs = circuit.wires.points.getAllLocations();
    for (ArrayList<Wire> mergeSet : mergeSets.getMergeSets()) {
      doMergeAndSplit(circuit, mergeSet, mutator, allLocs);
    }
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
    // For each wire
    for (Wire w : circuit.getWires()) {
      Location w0 = w.getEnd0();
      Location w1 = w.getEnd1();
      // Find all split points
      ArrayList<Location> splits = null;
      for (Location loc : allLocs) {
        if (w.nominallyContains(loc) && !loc.equals(w0) && !loc.equals(w1)) {
          // something is at loc, and loc is in the middle of wire w,
          // so w needs to split
          if (splits == null)
            splits = new ArrayList<>();
          splits.add(loc);
        }
      }
      if (splits != null) {
        splits.add(w1);
        Collections.sort(splits);
        Location e0 = w0;
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
        mutator.repairWires(circuit, Collections.singletonList(w), subs);
      }
    }
  }

  public static void repairWires(Circuit circuit, CircuitMutator mutator) {
    doMerges(circuit, mutator);
    doOverlaps(circuit, mutator);
    doSplits(circuit, mutator);
  }
}
