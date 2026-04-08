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
import java.util.Set;

import com.cburch.logisim.comp.Component;

// When a circuit change is finished (CircuitEvent.TRANSACTION_DONE), the
// effects on the circuit will be summarized in a ReplacementMap, detailing:
//  1. removals: non-wire components removed outright, without being replaced
//  2. additions: non-wire components added outright, without replacing anything
//  3. replacements: a pair of like components, one removed, the other added to replace it
//  4. wire removals: wires removed, either outright or replaced with (an)other wire(s)
//  5. wire additions: wires added, either outright or replacing (an)other wire(s)
//  6. a best-effort map indicating how wire selections should change after the transaction
//
// For 1, 2, and 3, the Component objects are normally all unique under
// reference equality, and likely under Object.equals() as well since no known
// non-wire Components override Object.equals(). To be specific, any non-wire
// Component c will normally be at most one of:
//   - removed outright (and not replaced),
//   - added outright (and not replacing),
//   - replaced by some other Component
//   - replacing some other Component
// We check these invariants, and warn if they fail. But ReplacementMap doesn't
// rely on any of these invariants, and it isn't known if any clients rely on
// these invariants. Strong invariants (which we don't check) are that the same
// properties hold over time, e.g. that a non-wire component, once marked for
// removal within a ReplacementMap, should never again appear in any later
// operation of that same ReplacementMap.
//
// For case 3, replacements (e.g. Component a replaced by Component b), we also
// check (and warn for) an additional invariant, that a and b have the same
// Factory. The only code creating a --> b replacement pairs is Selection, which
// is triggered when the user moves some selected components through a dx, dy
// translation. Selection will create b in that case by calling
// a.getFactory().createComponent(...). So far as I know, all existing factories
// implement that call in a way that returns a component with the same factory
// object.
//
// For 4 and 5 we maintain an invariant:
//  - the added wires and removed wire sets are disjoint, under .equals.
// Generally, Wire objects are NOT unique, because Wire overrides
// Object.equals(), and Wire.create() caches objects and may return the same
// reference multiple times. This means a single Wire object may appear in
// multiple circuits, or be removed from a circuit then later added, etc. And
// the wire-repair and wire connection code may not always be careful about
// deduplicating wires during their manipulations, sometimes causing a Wire
// object to be removed from a circuit then added again during the same
// transaction, or sometimes the same Wire object might be added multiple times,
// or added even though it is already in the circuit. Or multiple Wire objects
// that are .equals() might all be added, etc. So a wire might be in the
// ReplacementMap, marked for addition, yet already exist in the circuit. We
// allow all of these, as they should be harmless: CircuitWires de-duplicates
// wires ultimately (I hope).
//
// For case 6, we maintain only a forward-transaction mapping describing how
// wire selection state changes, since the selection code keeps a
// pre-transaction snapshot that it can restore in the reverse direction. The
// mapping is: Wire --> Set<Wire>, where w0 --> {w1, w2, ...} means that if w0
// was in the selection before the transaction, then after the transaction it
// should be removed then w1, w2, ..., should be added to the selection instead.
//
//  Q: Does it mean w1, w2, ... were just added to the circuit?
//  A: No, some may have already existed in the circuit.
//
//  Q: Does it mean w0 should be removed from the selection?
//  A: Yes, unless w0 appears in the set on the right side (which is allowed).
//
//  Q: Should w0 be removed from the circuit? Should w1, w2, ... be added to it?
//  A: No, these may or may not be in the addedWires or removedWires sets.
//   (FIXME: verify this)
//    
//  Q: Does it guarantee that w1, w2, ... will be in the circuit?
//  A: Maybe? (FIXME: verify this)
//
//  Q: Does it mean w0 will not be in the circuit?
//  A: Probably not? (FIXME: verify this)
//
// After construction, all changes to a ReplacementMap are cumulative with an
// "append" or "compose" style semantics. So a sequence like add(x), remove(x)
// get composed into a simpler sequence. For a non-wire x, it becomes a no-op: x
// wasn't in the circuit before, was then added, then removed, so it isn't in
// the circuit after. For a wire, it becomes remove(x): x may or may not have
// been in the circuit before, was then added (possibly a no-op, if it was
// already in the circuit), then removed, so it needs to be removed (which may
// be a harmless no-op).

public class ReplacementMap {

  private boolean frozen; // prevents further changes to mappings
 
  // 1: non-wire Component a removed outright: removed[a] = null
  // 2: non-wire Component b added outright: added[b] = null
  // 3: non-wire Component a replaced by b: removed[a] = b and added[b] = a
  // Invariant: a reference appears at most once across both of these maps.
  // Invariant: non-null pairs in each map are "like" components.
  private IdentityHashMap<Component, Component> removed = new IdentityHashMap<>();
  private IdentityHashMap<Component, Component> added = new IdentityHashMap<>();
  
  // 4: Wires removed
  // 5: Wires added
  // Invariant: these sets are disjoint under Wire.equals()
  private HashSet<Wire> removedWires = new HashSet<>();
  private HashSet<Wire> addedWires = new HashSet<>();
  
  // 6: Wire selection changes
  // Invariant: practically none... this is best-effort only.
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
      if (a != b) {
        // a --> b means "remove a, then add b"
        r.removedWires.add((Wire)a);
        r.addedWires.add((Wire)b);
      } else {
        // b --> b means "remove b, then add b"; for wires this composes to only "add b"
        r.addedWires.add((Wire)b);
      }
      // Either way, record the effect on wire selections:
      // if wire a was selected before xn, then after xn, b should be selected instead.
      HashSet<Wire> sel = new HashSet<>();
      sel.add((Wire)b);
      r.wireSelectionChanges.put((Wire)a, sel);
    } else {
      if (a == b)
        throw new IllegalArgumentException("replacing component with itself");
      r.removed.put(a, b);
      r.added.put(b, a);
    }
    return r;
  }

  // Append change saying component b is now added outright.
  // Same effect as: appendMultiple(ReplacementMap.forAddition(b))
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
          System.err.printf("Invariant violated: b was replaced by c, then b was re-added: b=%s c=%s\n", b, c);
          added.put(c, null); // I guess change c <-- b so it says c is added outright
        }
      }
      Component a = added.put(b, null); // b added outright
      if (a != null) {
        System.err.printf("Invariant violated: a replaced by b, then b re-added: a=%s b=%s\n", a, b);
        removed.put(a, null); // I guess a gets removed outright, instead of replaced?
      }
    }
  }

  // Append change saying component a is removed outright.
  // Same effect as: appendMultiple(ReplacementMap.forRemoval(a))
  public void appendRemoval(Component a) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    if (a instanceof Wire) {
      addedWires.remove((Wire)a); // wire is no longer added
      removedWires.add((Wire)a); // wire is removed instead
      // FIXME: Maybe remove a from wireSelectionChanges? Test out what seems best for selection UI?
    } else {
      if (added.containsKey(a)) { // a was added
        Component a0 = added.remove(a); // a is no longer added
        if (a0 != null) { // a0 was replaced by a, then a removed outright
          removed.put(a0, null); // change a0 --> a so it says a0 is removed outright 
        } else { // a was added outright, then a removed outright
          // nothing more to do: we already updated so a is no longer added
          // note: we do NOT keep a record of a being removed... the
          // add(a), remove(a) sequence collapses to a no-op.
        }
      } else if (removed.containsKey(a)) {
        Component b = removed.get(a);
        if (b != null) {
          System.err.printf("Invariant violated: a replaced by b, then a removed outright: a=%s b=%s\n", a, b);
          // leave it alone, a is already removed (though replaced by b, not outright)
        } else {
          System.err.printf("Invariant violated: a removed outright, then a removed outright again: a=%s\n", a);
          // leave it alone, a is already removed outright
        }
      } else {
        removed.put(a, null); // a is removed outright
      }
    }
  }

  // Append change saying component a is replaced by like component b.
  // Same effect as: appendMultiple(ReplacementMap.forReplacement(a, b))
  public void appendReplacement(Component a, Component b) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    if (a.getFactory() != b.getFactory())
      throw new IllegalArgumentException("should have same factory: " + a.getFactory() + " != " + b.getFactory());
    if (a instanceof Wire) {
      // NOTE: this if/else isn't needed, the if-case should work in both cases due to the order of operations
      if (a != b) {
        // a --> b means "remove a, then add b"
        addedWires.remove((Wire)a);   // a is no longer added (if it was)
        removedWires.add((Wire)a);    // instead, now a is removed
        // FIXME: Maybe remove a from wireSelectionChanges? Test out what seems best for selection UI?
        removedWires.remove((Wire)b); // b is no longer removed (if it was)
        addedWires.add((Wire)b);      // instead, now b is added
      } else {
        // b --> b means "remove b, then add b"; for wires this composes to only "add b"
        removedWires.remove((Wire)b); // b is no longer removed (if it was)
        addedWires.add((Wire)b);      // instead, now b is added
      }
      // Either way, record the effect on wire selections, composing with current changes.
      for (Map.Entry<Wire, HashSet<Wire>> e : wireSelectionChanges.entrySet()) {
        Wire w0 = e.getKey();
        HashSet<Wire> ws = e.getValue();
        if (ws.contains(a)) {
          // We already have w0 --> ws={ ... a ... } meaning
          // if w0 is selected before xn, then after xn, { ... a ... } is selected instead.
          // Now a got replaced by b, so selection would move to { ... b ...} now.
          ws.remove((Wire)a);
          ws.add((Wire)b);
        }
      }
      HashSet<Wire> ws = wireSelectionChanges.get((Wire)a);
      if (ws != null) {
        // We already have a --> ws={...}, and this causes a to be dropped from
        // the selection, so now replacing a with b doesn't affect the selection
        // (unless a appears within ws, but that case was already handled in
        // above loop)
      } else {
        // We don't have anything for a yet, but now a got replaced by b, so
        // selection would move with that change.
        ws = new HashSet<>();
        ws.add((Wire)b);
        wireSelectionChanges.put((Wire)a, ws);
      }
    } else {
      if (a == b)
        throw new IllegalArgumentException("replacing component with itself");
      if (added.containsKey(a)) { // a was previously added
        Component a0 = added.remove(a); // a is no longer added
        if (a0 != null) { // a0 was replaced by a, now a replaced by b: collapses to a0 replaced by b
          removed.put(a0, b);  // update: a0's replacement changes from a to b
          Component a1 = added.put(b, a0); // b is added as replacement for a0
          if (a1 != null) {
            System.err.printf("Invariant violated: b already added, replacing a1, while collapsing chain: a=%s a0=%s b=%s a1=%s\n", a, a0, b, a1);
            // b <-- a1 was present, we just set a0 --> a --> b (collapsed to a0 --> b)
            // So now we have both a1 --> b and a0 --> b, with b appearing twice.
            // Let's change a1 to be removed outright, I guess?
            removed.put(a1, null); // replaces a1 --> b with a1 --> null
          }
        } else { // a was added outright, now replaced by b: net is b added outright
          Component a1 = added.put(b, null); // b added outright
          if (a1 != null) {
            System.err.printf("Invariant violated: b already added, replacing a1, while recording replacement: a=%s b=%s, a1=%s\n", a, b, a1);
            // b <-- a1 was present, we are handling b <-- a after a <-- null (collapsed to b <-- null)
            // So now we have a1 --> b but also b <-- null, with b appearing inconsistently
            // Let's change a1 to be removed outright, I guess?
            removed.put(a1, null); // replaces a1 --> b with a1 --> null
          }
        }
      } else if (removed.containsKey(a)) { // a was already removed: error
        Component b0 = removed.get(a);
        if (b0 != null) {
          System.err.printf("Invariant violated: a already replaced by b0, now a replaced by b: a=%s b0=%s b=%s\n", a, b0, b);
          // a --> b0 was present, but now we wanted a --> b, which isn't possible.
          // I guess just add b outright?
          appendAddition(b);
        } else {
          System.err.printf("Invariant violated: a already removed outright, now a replaced by b: a=%s b=%s\n", a, b);
          // a --> null was present, but now we wanted a --> b, which isn't possible.
          // I guess just add b outright?
          appendAddition(b);
        }
      } else { // a not previously mentioned: simple replacement
        removed.put(a, b);
        Component a1 = added.put(b, a);
        if (a1 != null) {
          System.err.printf("Invariant violated: b already added while recording replacement: a=%s b=%s a1=%s\n", a, b, a1);
          // b <-- a1 was present, we just set b <-- a
          // So now we have a1 --> b but a --> b, with b appearing twice.
          // Let's change a1 to be removed outright, I guess?
          removed.put(a1, null); // replaces a1 --> b with a1 --> null
        }
      }
    }
  }

  // Append change saying wire a is replaced by a set of wires b. This is a
  // special case multiple-replacement version of appendReplacement(a, b), which
  // is not necessarily the same as appending/composing multiple replacements in
  // sequence.
  // Note: a can appear among bs, and bs can be empty.
  public void appendReplacements(Wire a, Set<Wire> bs) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    // NOTE: this if/else isn't needed, the if-case should work in both cases due to the order of operations
    if (!bs.contains(a)) {
      // a --> bs={...} (with a not in bs) means "remove a, then add all bs"
      addedWires.remove(a);    // a is no longer added (if it was)
      removedWires.add(a);     // instead, now a is removed
      // FIXME: Maybe remove a from wireSelectionChanges? Test out what seems best for selection UI?
      removedWires.removeAll(bs); // bs are no longer removed (if any were)
      addedWires.addAll(bs);      // instead, now bs are added
    } else {
      // a --> bs={a,...} means "remove a, then add a,b1,..."; for wires this composes to only "add a,b1,..."
      removedWires.removeAll(bs); // bs are no longer removed (if any were)
      addedWires.addAll(bs);      // instead, now bs are added
    }
    // Either way, record the effect on wire selections, composing with current changes.
    for (Map.Entry<Wire, HashSet<Wire>> e : wireSelectionChanges.entrySet()) {
      Wire w0 = e.getKey();
      HashSet<Wire> ws = e.getValue();
      if (ws.contains(a)) {
        // We already have w0 --> ws={ ... a ... } meaning
        // if w0 is selected before xn, then after xn, { ... a ... } is selected instead.
        // Now a got replaced by bs, so selection would move to { ... bs ...} now.
        ws.remove(a);
        ws.addAll(bs);
      }
    }
    HashSet<Wire> ws = wireSelectionChanges.get(a);
    if (ws != null) {
      // We already have a --> ws={...}, and this causes a to be dropped from
      // the selection, so now replacing a with b doesn't affect the selection
      // (unless a appears within ws, but that case was already handled in
      // above loop)
    } else {
      // We don't have anything for a yet, but now a got replaced by bs, so
      // selection would move with that change.
      ws = new HashSet<>();
      ws.addAll(bs);
      wireSelectionChanges.put(a, ws);
    }
  }

  // Compose this ReplacementMap with next ReplacementMap.
  void appendMultiple(ReplacementMap next) {
    if (frozen)
      throw new IllegalStateException("cannot change frozen map");
    // Note: next will normally be locked here... client's do that already

    // For non-wires: call the appropriate appendX() methods, in any order.
    for (Map.Entry<Component, Component> e : next.removed.entrySet()) {
      Component a = e.getKey(), b = e.getValue();
      if (b == null) appendRemoval(a); // next: a --> null
      else appendReplacement(a, b); // next: a --> b
    }
    for (Map.Entry<Component, Component> e : next.added.entrySet()) {
      Component b = e.getKey(), a = e.getValue();
      if (a == null) appendAddition(b); // next: b <-- null
      // else: already handled in above loop // next: b <-- a
    }

    // For wires, we can bulk-merge the sets from next, which are disjoint
    addedWires.removeAll(next.removedWires); // wires are no longer added
    removedWires.addAll(next.removedWires); // wires are removed instead
    // FIXME: Maybe remove wires from wireSelectionChanges? Test out what seems best for selection UI?
    removedWires.removeAll(next.addedWires); // wires are no longer removed
    addedWires.addAll(next.addedWires); // wires are added instead
    
    // For wire selection changes, we need to compose the maps.
    for (Map.Entry<Wire, HashSet<Wire>> e : this.wireSelectionChanges.entrySet()) {
      Wire w0 = e.getKey();
      HashSet<Wire> ws = e.getValue();
      // We already have w0 --> ws={w1, ...}
      HashSet<Wire> wsUpdated = new HashSet<>();
      for (Wire w1 : ws) {
        HashSet<Wire> wsNext = next.wireSelectionChanges.get(w1);
        if (wsNext != null) {
          // next has w1 --> wsNext={...}
          wsUpdated.addAll(wsNext); // we will get w0 --> { ... } + wsNext
        } else {
          // next doesn't mention w1
         wsUpdated.add(w1); // we will keep w0 --> { ... w1 ... }
        }
      }
      e.setValue(wsUpdated);
    }
    for (Map.Entry<Wire, HashSet<Wire>> e : next.wireSelectionChanges.entrySet()) {
      Wire w1 = e.getKey();
      HashSet<Wire> wsNext = e.getValue();
      HashSet<Wire> ws = this.wireSelectionChanges.get(w1);
      if (ws != null) {
        // Next has w1 --> wsNext={...}
        // We have w1 --> ws={...}, and this causes w1 to be dropped from selection,
        // so next's data is not applicable (unless w1 appears within ws, but
        // that case was already handled in above loop).
      } else {
        // Next has w1 --> wsNext={...}
        // We don't have anything for w1 yet, so we should adopt next's data here.
        wireSelectionChanges.put(w1, new HashSet<>(wsNext));
      }
    }
  }

  // Prevent any further changes
  void freeze() {
    frozen = true;
  }
 
  // Get an inverse map with opposite additions, removals, and replacements, but
  // an empty wireSelectionChanges.
  ReplacementMap getInverseMap() {
    // if (!frozen)
    //   System.err.println("ERR? not frozen but getting inverse");
    frozen = true;

    // No need to copy most sets, this and inv are both frozen
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

  // Checks if this ReplacementMap has no effect on the circuit (ignores
  // wire selection changes).
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
        removedWires.size(), removed.size(), addedWires.size(), added.size());
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
