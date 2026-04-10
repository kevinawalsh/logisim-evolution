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

// When a CircuitTransaction is finished (CircuitEvent.TRANSACTION_DONE),
// certain changes to the circuit will be summarized in a ReplacementLog, built
// up during the transaction's execution, detailing:
//  1. removals: non-wire components removed outright, without being replaced
//  2. additions: non-wire components added outright, without replacing anything
//  3. replacements: a pair of like components, one removed, the other added to replace it
//  4. wire changes: indicating how wire selections should change after the transaction
//
// ReplacementLog reflects the actual total delta of changes to a circuit before
// and after a transaction. In all cases, info is added to a ReplacementLog only
// after some change actually succeeds in being carried out on the cicuit. For
// example, if a CircuitChange tries to add a wire w, but fails because w
// already exists in the circuit, then no change is noted in the ReplacementLog.
//
// All changes to a ReplacementLog are cumulative with an "append" or "compose"
// style semantics. So a sequence like add(x), remove(x) get turned into a
// no-op.
//
// ReplacementLog is never reversed, and plays no role in reverse transactions.
// For changes to the circuit, the CircuitChange log is used for reversing the
// effects of a transaction. And for the UI selection, a snapshot is taken
// before each transaction, then restored during reverse transactions.
//
// For #1, #2, and #3, the Component objects are all unique under reference
// equality and Object.equals(), because Circuit disallows duplicate components.
// So any given component can be at most one of:
//   - removed outright (and not replaced),
//   - added outright (and not replacing),
//   - replaced by some other Component
//   - replacing some other Component
// We check these invariants, and warn if they fail. But ReplacementLog doesn't
// rely on any of these invariants, and it isn't known if any clients rely on
// these invariants. A stronger invariant, which we don't check, states that the
// same properties hold over time for non-wires, e.g. that a non-wire component,
// once marked for removal within a ReplacementLog, should never again appear in
// any later operation of that same ReplacementLog (and probably not in any
// ReplacementLog, for this or any other circuit).
//
// For #3, replacements (e.g. Component a replaced by Component b), we also
// check (and warn for) two additional invariants, that a and b are not the same
// under Object.equals(), and that a and b have the same Factory. The only code
// creating a --> b replacement pairs is Selection, which is triggered when the
// user moves some selected components through a dx, dy translation. Selection
// will create b in that case by calling a.getFactory().createComponent(...). So
// far as I know, all existing factories implement that call in a way that
// returns a component with the same factory object. Downstream clients expect
// replacements to be like the originals (some clients check for this
// explicitly, just in case).
//
// For #4, wire changes, we only care about how the selection changes. We don't
// enforce any equality invariants here, in case the wire repair code wants to
// replace a wire by itself, for example. It's okay if this is best-effort or
// imprecise in some cases, since the only result The wire changes are
// represented as mappings:
//   Wire --> Set<Wire>,
// where w0 --> {w1, w2, ...} means that if w0
// was in the selection before the transaction, then after the transaction it
// should be removed then w1, w2, ..., should be added to the selection instead.
//
// Q: Does it mean w1, w2, ... were just added to the circuit?
// A: No, some may have already existed in the circuit. For example, the user
//    might have moved a wire w0 so it lands on top of (and completely covered
//    by) an existing wire w1, which condenses down to a transaction that just
//    removes w0, leaves w1 alone. But the selection moves from w0 to w1.
//
// Q: Does it mean w0 should be removed from the selection?
// A: Yes, unless w0 appears in the set on the right side (which is allowed).
//    It's hard to think of an example of how the UI could ever cause this to
//    happen, but it seems conceivable. And allowing this means wire repair code
//    doesn't need extra check to filter out this case.
//
// Q: Should w0 be removed from the circuit? Should w1, w2, ... be added to it?
// A: No. This map indicates changes to the selection, not to the circuit.
//    
// Q: Does it guarantee that w1, w2, ... will be in the circuit?
// A: Yes. Though it wouldn't hurt for a client to double-check.
//
// Q: Does it mean w0 got removed from the circuit?
// A: No. For example, If the user moves w0 and w1, such that w0 lands where w1 was
//    previously, this condenses down to a transaction with w0 removed, w1 left
//    alone, and a new wire w2 added. But the selection moves from w0 to w1, and
//    from w1 to w2.

public class ReplacementLog {
 
  // #1: non-wire Component a removed outright: removed[a] = null
  // #2: non-wire Component b added outright: added[b] = null
  // #3: non-wire Component a replaced by b: removed[a] = b and added[b] = a
  // Invariant: a reference appears at most once across both of these maps.
  // Invariant: non-null pairs in each map are "like" components.
  private IdentityHashMap<Component, Component> removed = new IdentityHashMap<>();
  private IdentityHashMap<Component, Component> added = new IdentityHashMap<>();
  
  // #4: Wire changes: Selection moves from w0 to wireChanged[w0] = {w1, w2, ...}
  private HashMap<Wire, HashSet<Wire>> wireChanged = new HashMap<>();

  // Create an empty ReplacementLog
  public ReplacementLog() { }

  // Update: b was just added outright.
  public void logAddition(Component b) {
    if (b instanceof Wire) {
      // wire b was just added outright, selection does not change (selection
      // could not have contained b). Maybe mark b as dead in wireChange, so
      // further changes involving b don't pollute the map?
    } else {
      if (added.containsKey(b)) {
        System.err.printf("Invariant violated: b was already added: b=%s\n", b);
        return;
      }
      if (removed.containsKey(b)) {
        // won't happen in practice: currently, all non-wire additions are fresh objects
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

  // Update: a was just removed outright.
  public void logRemoval(Component a) {
    if (a instanceof Wire) {
      // wire a was just removed outright, any selection of a is now dead.
      moveWireSelection((Wire)a, Collections.emptySet());
    } else {
      if (added.containsKey(a)) {
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

  // Update: a was just replaced by like component b
  public void logNonWireReplacement(Component a, Component b) {
    if (a.getFactory() != b.getFactory())
      throw new IllegalArgumentException("should have same factory: " + a.getFactory() + " != " + b.getFactory());
    if (a instanceof Wire || b instanceof Wire)
      throw new IllegalArgumentException("must be non-wire");
    if (a.equals(b))
      return; // harmless, no-op
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
        logAddition(b);
      } else {
        System.err.printf("Invariant violated: a already removed outright, now a replaced by b: a=%s b=%s\n", a, b);
        // a --> null was present, but now we wanted a --> b, which isn't possible.
        // I guess just add b outright?
        logAddition(b);
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

  // Update: selection of w should move to wsNew={w1, w2, ...}
  // If wsNew is empty, it means the selection of w0 is dead.
  // Note: w0 may appear within wsNew.
  // Special case: wsNew can contain duplicates, or nulls. They will be removed.
  public void moveWireSelection(Wire w, Collection<Wire> wsNew) {
    for (Map.Entry<Wire, HashSet<Wire>> e : wireChanged.entrySet()) {
      Wire w0 = e.getKey();
      HashSet<Wire> ws = e.getValue();
      if (ws.contains(w)) {
        // We already have w0 --> ws={ ... w ... } meaning:
        // if w0 is selected before xn, then after xn, { ... w ... } is selected instead.
        // Now, w got replaced by wsNew, so selection would move to { ... wsNew ...}.
        ws.remove(w);
        ws.addAll(wsNew);
        ws.remove(null);
      }
    }
    HashSet<Wire> ws = wireChanged.get(w);
    if (ws != null) {
      // We already have w --> ws={...}, meaning:
      // if w ws selected before xn, then after xn, drop it and select {...} instead.
      // Now, replacing w with wNew doesn't affect the selection at all, unless
      // w appears within ws (but that case was already handled in above loop).
    } else {
      // We don't have anything for w yet, but w just got replaced by wsNew, so
      // selection should move with that change.
      ws = new HashSet<>();
      ws.addAll(wsNew);
      ws.remove(null);
      wireChanged.put(a, ws);
    }
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

  // Checks whether non-wire component a was removed (outright, or replaced)
  public boolean wasNonWireRemoved(Component a) {
    if (a instanceof Wire)
      throw new IllegalArgumentException("requires non-wire");
    return removed.containsKey(a);
  }

  // Returns items to be added to the selection if a was previously selected.
  public Collection<Component> getSelectionUpdatesFor(Component a) {
    if (a instanceof Wire) {
      HashSet<Wire> set = wireChanged.get((Wire)a);
      return set == null ? null : Collections.unmodifiableSet(set);
    } else {
      Component b = removed.get(a);
      return b == null ? null : Collections.singleton(b);
    }
  }
  
  public void print(PrintStream out) {
    out.printf("  removed %d non-wires:\n", removed.size());
    for (Map.Entry<Component, Component> e : removed.entrySet()) {
      out.println("    " + e.getKey());
      if (e.getValue() != null)
        out.println("      ... replaced by " + e.getValue());
    }
    out.printf("  added %d non-wires:\n", added.size());
    for (Map.Entry<Component, Component> e : added.entrySet()) {
      out.println("    " + e.getKey());
      if (e.getValue() != null)
        out.println("      ... replaces " + e.getValue());
    }
    out.printf("  updating %d wire selections:\n");
    for (Map.Entry<Wire, HashSet<Wire>> e : wireChanged.entrySet()) {
      Wire w0 = e.getKey();
      HashSet<Wire> ws = e.getValue();
      out.println("    " + w + " selection moves to " + ws);
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
