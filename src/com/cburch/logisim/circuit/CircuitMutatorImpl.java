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

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.std.hdl.VhdlContent;

// CircuitTransactions shouldn't generally call methods to modify a circuit
// directly. Instead, all mutations should be done through these helpers.
// Note: this is the only implementation of the CircuitMutator interface.
//
// This class:
//  - Keeps a list of modified circuits.
//  - Keeps a CircuitChange list for each circuit, an ordered log edits, so we
//    can build a reverse transaction for undo operations.
//  - Builds a ReplacementMap for each circuit, used upon transaction completion
//    to: (a) update UI selections after a transaction, (b) transfer circuit
//    simulation state to replacement components, (c) update gui.log, and
//    possibly other things.
class CircuitMutatorImpl implements CircuitMutator {

  final CircuitTransaction owner;
  private ArrayList<CircuitChange> log = new ArrayList<>();
  private HashMap<Circuit, ReplacementMap> replacements = new HashMap<>();
  private HashSet<Circuit> modified = new HashSet<>();

  CircuitMutatorImpl(CircuitTransaction xn) {
    owner = xn;
  }

  public void add(Circuit circuit, Component comp) {
    markModified(circuit);
    log.add(CircuitChange.add(circuit, comp));

    getMap(circuit).appendAddition(comp);

    circuit.mutatorAdd(comp);
  }

  private ReplacementMap getMap(Circuit circuit) {
    ReplacementMap ret = replacements.get(circuit);
    if (ret == null) {
      ret = new ReplacementMap();
      replacements.put(circuit, ret);
    }
    return ret;
  }

  Collection<Circuit> getModifiedCircuits() {
    return Collections.unmodifiableSet(modified);
  }

  ReplacementMap getReplacementMap(Circuit circuit) {
    return replacements.get(circuit);
  }

  Collection<CircuitChange> getChangeLogFor(Circuit circuit) {
    ArrayList<CircuitChange> l = new ArrayList<>();
    for (CircuitChange cc : log) {
      if (cc.getCircuit() == circuit)
        l.add(cc);
    }
    return l;
  }

  CircuitTransaction getReverseTransaction() {
    CircuitMutation ret = new CircuitMutation();
    ArrayList<CircuitChange> log = this.log;
    for (int i = log.size() - 1; i >= 0; i--) {
      ret.change(log.get(i).getReverseChange());
    }
    return ret;
  }

  void markModified(Circuit circuit) {
    modified.add(circuit);
    // Sanity check: circuit should have been locked by us
    CircuitMutatorImpl circMutator = circuit.getLocker().getMutator();
    if (circMutator != this) {
      System.out.println("*** Circuit Lock Bug Diagnostics ***");
      System.out.println("This thread: " + Thread.currentThread());
      System.out.println("  executing transaction:" + owner);
      System.out.println("  with mutator: " + this);
      System.out.println("attempted to illegally modify");
      System.out.println("  non-locked circuit: " + circuit.getName());
      System.out.println("  with mutator: " + circMutator);
      Thread.dumpStack();
      // FIXME: perhaps throw (before adding to modified set)?
    }
  }

  public void remove(Circuit circuit, Component comp) {
    if (circuit.contains(comp)) {
      markModified(circuit);
      log.add(CircuitChange.remove(circuit, comp));

      getMap(circuit).appendRemoval(comp);

      circuit.mutatorRemove(comp);
    }
  }

  public void replace(Circuit circuit, Component prev, Component next) {
    applyReplacements(circuit, ReplacementMap.forReplacement(prev, next));
  }

  public void applyReplacements(Circuit circuit, ReplacementMap repl) {
    ArrayList<Component> added = new ArrayList<>();
    if (!repl.isEmpty()) {
      markModified(circuit);
      repl.freeze();
      log.add(CircuitChange.replaceMultiple(circuit, repl));
      getMap(circuit).appendMultiple(repl);

      for (Component c : repl.getAllRemovals()) {
        // case 1: c is a wire... call mutatorRemove(c); next loop handles any replacement(s)
        // case 2: non-wire c replaced by r... call mutatorReplace(c, r)
        //         and skip mutatorAdd(r) in the loop below
        // case 3: non-wire c removed outright... call mutatorRemove(c)
        // note: a non-wire c will always have zero or one replacement
        if (c instanceof Wire) { // case 1
          circuit.mutatorRemove(c);
        } else {
          Component r = repl.getNonWireReplacementFor(c);
          if (r != null) { // case 2
            circuit.mutatorReplaceNonWire(c, r);
            added.add(r); // 
          } else { // case 3
            circuit.mutatorRemove(c);
          }
        }
      }
      for (Component c : repl.getAllAdditions()) {
        if (!added.contains(c))
          circuit.mutatorAdd(c);
      }
    }
  }

  public void set(Circuit circuit, Component comp, Attribute<?> attr, Object newValue) {
    if (circuit.contains(comp)) {
      markModified(circuit);
      @SuppressWarnings("unchecked")
      Attribute<Object> a = (Attribute<Object>) attr;
      AttributeSet attrs = comp.getAttributeSet();
      Object oldValue = attrs.getValue(a);
      log.add(CircuitChange.set(circuit, comp, attr, oldValue, newValue));
      attrs.setAttr(a, newValue);
    }
  }

  public void setForCircuit(Circuit circuit, Attribute<?> attr, Object newValue) {
    @SuppressWarnings("unchecked")
    Attribute<Object> a = (Attribute<Object>) attr;
    AttributeSet attrs = circuit.getStaticAttributes();
    Object oldValue = attrs.getValue(a);
    log.add(CircuitChange.setForCircuit(circuit, attr, oldValue, newValue));
    attrs.setAttr(a, newValue);
  }

  public void setForVhdl(VhdlContent vhdl, Attribute<?> attr, Object newValue) {
    @SuppressWarnings("unchecked")
    Attribute<Object> a = (Attribute<Object>) attr;
    AttributeSet attrs = vhdl.getStaticAttributes();
    Object oldValue = attrs.getValue(a);
    log.add(CircuitChange.setForVhdl(vhdl, attr, oldValue, newValue));
    attrs.setAttr(a, newValue);
  }
}
