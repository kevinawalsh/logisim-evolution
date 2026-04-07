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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;

import com.cburch.logisim.comp.Component;

// When a circuit change is finished (CircuitEvent.TRANSACTION_DONE), the effects on
// the circuit will be summarized in a ReplacementMap, detailing:
//  1. each non-wire component removed from circuit outright, without being replaced
//  2. each non-wire component added to the circuit outright, without replacing anything
//  3. each non-wire component replaced in the circuit by a different component
//  4. a set of wires removed
//  5. a set of wires added
//  6. a map indicating how wire selections should change after the transaction
//
// For 1, 2, and 3, the Component objects are all unique, under reference
// equality at least, and likely under Object.equals() as well since no known
// non-wire Components override Object.equals(). To be specific, any non-wire
// Component c will be at most one of:
//   - removed outright (and not replaced),
//   - added outright (and not replacing),
//   - replaced by some other Component
//   - replacing some other Component
//
// For case 3, replacements (e.g. Component a replaced by Component b), we also
// check (and warn if it fails) an additional invariant, that a and b have the
// same Factory. The only code creating a --> b replacement pairs is Selection,
// which is triggered the user moves some selected components through a dx, dy
// translation. Selection will create b in that case by calling
// a.getFactory().createComponent(...). So far as I know, all existing factories
// implement that call in a way that returns a component with the same factory
// object.
//
// For 4, 5, and 6, Wire objects are NOT unique. Wire overrides Object.equals(),
// and Wire.create() caches objects and may return the same reference multiple
// times. So a single Wire object may appear in multiple circuits, or be removed
// from a circuit then later added, etc. And the wire-repair and wire connection
// code doesn't seem particularly careful about deduplicating wires during their
// manipulations, sometimes causing a Wire object to be removed from a circuit
// then added again during the same transaction, or sometimes the same Wire
// object might be added multiple times, or multiple Wire objects that are
// .equals() might all be added, etc.
//
// For case 6, we maintain only a forward-transaction mapping describing how
// wire selection state changes, since the selection code keeps a
// pre-transaction snapshot that it can restore in the reverse direction. The
// mapping is: Wire -> Set<Wire>, where w0 -> {w1, w2, ...} means simply that
// if w0 was in the selection before the transaction, then w1, w2, ..., should
// be added to the selection after the transaction.
// FIXME:
//  - Does it mean w1, w2, ... were just added?
//    No, some may have already existed, I think.
//  - Does it mean w0 should be removed from the selection?
//    I'm not sure.
//  - Does it guarantee that w1, w2, ... will be in the circuit?
//    Probably yes?
//  - Does it mean w0 has been removed from the circuit?
//    Maybe?
//
// 

public class ReplacementMap {

  private boolean frozen; // prevents further changes to mappings
 
  // 1: non-wire Component a removed outright: removed[a] = null
  // 2: non-wire Component b added outright: added[b] = null
  // 3: non-wire Component a replaced by b: removed[a] = b and added[b] = a
  private IdentityHashMap<Component, Component> removed = new IdentityHashMap<>();
  private IdentityHashMap<Component, Component> added = new IdentityHashMap<>();
  
  // 4: Wires removed
  // 5: Wires added
  private HashSet<Wire> removedWires = new HashSet<>();
  private HashSet<Wire> addedWires = new HashSet<>();
  
  // 6: Wire selection changes
  private HashMap<Wire, HashSet<Wire>> wireSelectionChanges = new HashMap<>();

  // Create an empty ReplacementMap
  public ReplacementMap() { }

  // Create a ReplacementMap saying a component c was removed outright from a circuit
  public static ReplacementMap forRemoval(Component c) {
    ReplacementMap r = new ReplacementMap();
    if (c instanceof Wire)
      r.removedWires.add((Wire)c);
    else
      r.removed.put(c, null);
    return r;
  }

  // Create a ReplacementMap saying a component c was added outright to a circuit
  public static ReplacementMap forAddition(Component c) {
    ReplacementMap r = new ReplacementMap();
    if (c instanceof Wire)
      r.addedWires.add((Wire)c);
    else
      r.added.put(c, null);
    return r;
  }

  // Create a ReplacementMap saying component a was replaced by like component b in a circuit
  public static ReplacementMap forReplacement(Component a, Component b) {
    if (a.getFactory() != b.getFactory())
      throw new IllegalArgumentException("should have same factory: " + a.getFactory() + " != " + b.getFactory());
    ReplacementMap r = new ReplacementMap();
    if (a instanceof Wire) {
      r.replaceWire((Wire)a, (Wire)b);
    } else {
      if (a == b)
        throw new IllegalArgumentException("replacing component with itself");
      r.removed.put(a, b);
      r.added.put(b, a);
    }
    return r;
  }

  // Append change saying component b is now added outright
  public void appendAddition(Component b) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    if (b instanceof Wire) {
      removedWires.remove((Wire)b); // wire is no longer removed
      addedWires.add((Wire)b); // wire is added instead
    } else {
      if (removed.containsKey(b)) {
        Component c = removed.remove(b);
        if (c != null) {
          System.err.printf("WARN: b was replaced by c, then b was re-added: b=%s c=%s\n", b, c);
          added.put(c, null); // I guess change c <-- b so it says c is added outright
        }
      }
      Component a = added.put(b, null);
      if (a != null) {
        System.err.printf("WARN: a replaced by b, then b added outright: a=%s b=%s\n", a, b);
        removed.put(a, null); // I guess a gets removed outright?
      }
    }
  }

  // Append change saying component b is removed outright
  public void appendRemoval(Component b) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    if (b instanceof Wire) {
      addedWires.remove((Wire)b); // wire is no longer added
      removedWires.add((Wire)b);
    } else {
      if (added.containsKey(b)) {
        Component a = added.remove(b);
        if (a != null) { // a was replaced by b, then b removed outright
          removed.put(a, null); // change a --> b so it says a is removed outright 
        } else { // b was added outright, then b removed outright
          // nothing more to do, code below will ensure b is removed outright
        }
      }
      if (removed.containsKey(b)) {
        Component c = removed.put(b, null);
        if (c != null) {
          System.err.printf("WARN: b replaced by c, then b removed outright: b=%s c=%s\n", b, c);
          added.put(c, null); // I guess change c <-- b  so it say c is added outright
        } else {
          System.err.printf("WARN: b removed outright, then b removed outright again: b=%s c=%s\n", b, c);
        }
      } else {
        removed.put(b, null);
      }
    }
  }
  
  // Modify ReplacementMap to say that wire a is removed.
  // Note: a can be added/removed multiple times.
  public void removeWire(Wire a) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    // addedWires.remove(a); // do NOT undo previous addition?
    removedWires.add(a);
  }

  // Modify ReplacementMap to say that wire b is added.
  // Note: a can be added/removed multiple times.
  public void addWire(Wire a) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    // removedWires.remove(a); // do NOT undo previous removal?
    addedWires.add(a);
  }

  // Modify ReplacementMap to say that wire a is removed, wire b is added, and
  // wire selections that contained a should be modified to now contain b.
  // Note: a==b is allowed, and either can be added/removed multiple times.
  public void replaceWire(Wire a, Wire b) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    // addedWires.remove(a); // do NOT undo previous addition?
    removedWires.add(a);
    // removedWires.remove(b); // do NOT undo previous removal?
    addedWires.add(b);
    HashSet<Wire> sel = wireSelectionChanges.get(a);
    if (sel == null) {
      sel = new HashSet<>();
      wireSelectionChanges.put(a, sel);
    }
    sel.add(b);
  }

  // Modify ReplacementMap to say that wire a is removed, wires bs are added, and
  // wire selections that contained a should be modified to now contain all bs.
  // Note: a in bs is allowed, and any of the wires can be added/removed multiple times.
  public void replaceWire(Wire a, Collection<Wire> bs) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    for (Wire b : bs)
      replaceWire(a, b);
  }
  
  // Clear everything
  public void reset() {
    frozen = false; // FIXME: well this seems dangerous
    added.clear();
    removed.clear();
    addedWires.clear();
    removedWires.clear();
    wireSelectionChanges.clear();
  }

  private void mapNonWire(Component a, Component b) {
    if (a != null && mentionsNonWire(a))
      System.err.println("ERR: invariant violated: a=" + a);
    if (b != null && mentionsNonWire(b))
      System.err.println("ERR: invariant violated: b=" + b);
    if (a == b)
      System.err.println("ERR: invariant violated: a=b=" + a);
    if (a != null)
      removed.put(a, b);
    if (b != null)
      added.put(b, a);
  }

  // Compose this ReplacementMap with next ReplacementMap.
  void appendReplacements(ReplacementMap next) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    // For non-wires:
    // 1. DONE a --> b then b --> c ... becomes a --> c (c should not appear in this)
    // 2. DONE a --> b then b removed ... becomes a removed
    // 3. DONE a --> b then nothing ... stays a --> b (a and b should not appear in next)
    // 4. DONE b added then b --> c ... becomes c added
    // 5. DONE b added then b removed ... becomes no-op
    // 6. DONE b added then nothing ... stays b added (b should not appear in next)
    // 7. DONE a removed then anything ... stays a removed (a should not appear in next)
    // 8. DONE nothing then b --> c ... stays b --> c (b and c should not appear in this)
    // 9. DONE nothing then c added  ... stays c added (c should not appear in this)
    // 10. DONE nothing then b removed ... stays b removed (b should not appear in this)
    ReplacementMap r = new ReplacementMap();
    for (Map.Entry<Component, Component> e : added.entrySet()) {
      Component b = e.getKey();
      Component a = e.getValue();
      Component c = next.removed.get(b);
      if (a != null && c != null) { // case 1
        if (this.mentionsNonWire(c))
          System.err.println("ERR: case 1 invariant violated: c=" + c);
        r.mapNonWire(a, c);
      } else if (a != null && next.removed.containsKey(b)) { // case 2
        r.mapNonWire(a, null);
      } else if (a != null) { // case 3
        if (next.mentionsNonWire(a))
          System.err.println("ERR: case 3 invariant violated: a=" + a);
        if (next.mentionsNonWire(b))
          System.err.println("ERR: case 3 invariant violated: b=" + b);
        r.mapNonWire(a, b);
      } else if (c != null) {  // case 4
        r.mapNonWire(null, c);
      } else if (next.removed.containsKey(b)) { // case 5
        // no-op 
      } else { // case 6
        if (next.mentionsNonWire(b))
          System.err.println("ERR: case 6 invariant violated: b=" + b);
        r.mapNonWire(null, b);
      }
    }
    for (Map.Entry<Component, Component> e : removed.entrySet()) {
      if (e.getValue() != null) continue;
      // case 7
      Component a = e.getKey();
      if (next.mentionsNonWire(a))
        System.err.println("ERR: case 7 invariant violated: a=" + a);
      r.mapNonWire(a, null);
    }
    for (Map.Entry<Component, Component> e : next.added.entrySet()) {
      Component c = e.getKey();
      Component b = e.getValue();
      if (b != null && !this.added.containsKey(b)) { // case 8
        if (this.mentionsNonWire(b))
          System.err.println("ERR: case 8 invariant violated: b=" + b);
        if (this.mentionsNonWire(c))
          System.err.println("ERR: case 8 invariant violated: c=" + c);
        r.mapNonWire(b, c);
      } else if (!this.added.containsKey(b)) { // case 9
        if (this.mentionsNonWire(c))
          System.err.println("ERR: case 9 invariant violated: c=" + c);
        r.mapNonWire(null, c);
      }
    }
    for (Map.Entry<Component, Component> e : next.removed.entrySet()) {
      if (e.getValue() != null) continue;
      Component b = e.getKey();
      if (!this.added.containsKey(b)) { // case 10
        if (this.mentionsNonWire(b))
          System.err.println("ERR: case 10 invariant violated: b=" + b);
        r.mapNonWire(b, null);
      }
    }
    // FIXME: how do we compose wires?
    // For wires:
    // - All wires added in both sets, just merge them.
    // - All wires removed in both sets, just merge them.
    // - Merge wireSelectionChanges.
    r.addedWires.addAll(this.addedWires);
    r.addedWires.addAll(next.addedWires);
    r.removedWires.addAll(this.removedWires);
    r.removedWires.addAll(next.removedWires);
    for (Map.Entry<Wire, HashSet<Wire>> e : this.wireSelectionChanges.entrySet()) {
      Wire w0 = e.getKey();
      HashSet<Wire> ws = e.getValue();
      HashSet<Wire> sel = r.wireSelectionChanges.get(w0);
      if (sel == null) {
        sel = new HashSet<>();
        wireSelectionChanges.put(w0, sel);
      }
      sel.addAll(ws);
      for (Wire w1 : ws) {
        HashSet<Wire> ws2 = next.wireSelectionChanges.get(w1);
        if (ws2 != null)
          sel.addAll(ws2);
      }
    }
    for (Map.Entry<Wire, HashSet<Wire>> e : next.wireSelectionChanges.entrySet()) {
      Wire w0 = e.getKey();
      HashSet<Wire> ws = e.getValue();
      HashSet<Wire> sel = r.wireSelectionChanges.get(w0);
      if (sel == null) {
        sel = new HashSet<>();
        wireSelectionChanges.put(w0, sel);
      }
      sel.addAll(ws);
    }
    this.removed = r.removed;
    this.added = r.added;
    this.removedWires = r.removedWires;
    this.addedWires = r.addedWires;
    this.wireSelectionChanges = r.wireSelectionChanges;
  }

  // Prevent any further changes
  void freeze() {
    frozen = true;
  }
 
  // Get an inverse map with opposite additions, removals, and replacements, but
  // an empty wireSelectionChanges.
  ReplacementMap getInverseMap() {
    if (!frozen)
      System.err.println("ERR? not frozen but getting inverse");
    frozen = true;

    // No need to copy most sets, this and inv will both be frozen
    ReplacementMap inv = new ReplacementMap();
    inv.frozen = true;
    inv.removed = this.added;
    inv.added = this.removed;
    inv.removedWires = this.addedWires;
    inv.addedWires = this.removedWires;
    // for (Map.Entry<Wire, HashSet<Wire>> e : this.wireSelectionChanges.entrySet()) {
    //   Wire a = e.getKey();
    //   HashSet<Wire> bs = e.getValue();
    //   for (Wire b : bs) {
    //     HashSet<Wire> as = inv.wireSelectionChanges.get(b);
    //     if (as == null) {
    //       as = new HashSet<>();
    //       inv.wireSelectionChanges.put(b, as);
    //     }
    //     as.add(a);
    //   }
    // }
    return inv;
  }

  // Checks if this ReplacementMap is empty
  public boolean isEmpty() {
    return added.isEmpty() && removed.isEmpty()
      && addedWires.isEmpty() && removedWires.isEmpty();
  }

  // Returns non-wire components that were added outright.
  // Used only by gui.log.Model to check for new components of interest.
  public Collection<Component> getFreshNonWireAdditions() {
    HashSet<Component> set = new HashSet<>();
    for (Map.Entry<Component, Component> e : added.entrySet()) {
      if (e.getValue() == null)
        set.add(e.getKey());
    }
    return Collections.unmodifiableSet(set);
  }

  // Returns non-wire components that were added (outright, or replacements).
  public Collection<Component> getNonWireAdditions() {
    return Collections.unmodifiableSet(added.keySet());
  }

  // Returns non-wire components that were removed (outright, or replacements).
  public Collection<Component> getNonWireRemovals() {
    return Collections.unmodifiableSet(removed.keySet());
  }

  // Returns the non-wire replacing the non-wire a, or null if a wasn't replaced.
  public Component getNonWireReplacementFor(Component a) {
    if (a instanceof Wire)
      throw new IllegalArgumentException("requires non-wire");
    return removed.get(a);
  }
  
  // Returns wires that were added
  public Collection<Wire> getWireAdditions() {
    return Collections.unmodifiableSet(addedWires);
  }


  // Returns wire and non-wire additions (outright, or replacements).
  public Collection<Component> getAllAdditions() {
    HashSet<Component> set = new HashSet<>();
    set.addAll(added.keySet());
    set.addAll(addedWires);
    return Collections.unmodifiableSet(set);
  }
  
  // Returns wire and non-wire removals (outright, or replacements).
  public Collection<Component> getAllRemovals() {
    HashSet<Component> set = new HashSet<>();
    set.addAll(removed.keySet());
    set.addAll(removedWires);
    return Collections.unmodifiableSet(set);
  }
 
  // Checks whether non-wire component a was removed (outright, or replaced)
  public boolean wasNonWireRemoved(Component a) {
    if (a instanceof Wire)
      throw new IllegalArgumentException("requires non-wire");
    return removed.containsKey(a);
  }

  private boolean mentionsNonWire(Component c) {
    return added.containsKey(c) || removed.containsKey(c);
  }

  // Returns items to be added to the selection if a was previously selected.
  public Collection<Component> getSelectionUpdatesFor(Component a) {
    if (a instanceof Wire) {
      HashSet<Wire> set = wireSelectionChanges.get((Wire)a);
      return set == null ? null : Collections.unmodifiableSet(set);
    } else {
      Component b = removed.get(a);
      return b == null ? null : Collections.singleton(b);
    }
  }
  

  public void print(PrintStream out) {
    out.printf("  removing %d wires and %d non-wires, adding %d wires and %d non-wires\n",
        addedWires.size(), added.size(), removedWires.size(), removed.size());
    System.out.println(removed.isEmpty() && removedWires.isEmpty()
        ? "  removals: none" : "  removals:");
    for (Map.Entry<Component, Component> e : removed.entrySet()) {
      out.println("    " + e.getKey());
      if (e.getValue() != null)
        out.println("      ... replaced by " + e.getValue());
    }
    for (Wire w : removedWires) {
      out.println("    " + w);
      HashSet<Wire> sel = wireSelectionChanges.get(w);
      if (sel != null)
        out.println("      ... replaced in selection by " + sel.size() + " wires");
    }
    System.out.println(added.isEmpty() && addedWires.isEmpty()
        ? "  additions: none" : "  additions:");
    for (Map.Entry<Component, Component> e : added.entrySet()) {
      out.println("    " + e.getKey());
      if (e.getValue() != null)
        out.println("      ... replaces " + e.getValue());
    }
    for (Wire w : addedWires) {
      out.println("    " + w);
    }
  }

  public String toString() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PrintStream p = new PrintStream(out, true, "UTF-8")) {
        print(p);
    } catch (Exception e) {
    }
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

}
