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

// CircuitTransactions shouldn't generally call methods to modify a circuit
// directly. Instead, all mutations should be done through the mutator for the
// transaction, either directly by calling the methods below, or indirectly by
// creating CircuitChange objects get get passed to this mutator.
//
// Note: this is the only implementation of the CircuitMutator interface.
//
// This class:
//  - Keeps a list of modified circuits.
//  - Keeps a CircuitChange log, built as each change is made, so we can build a
//    reverse transaction for undo operations.
//  - Builds a ReplacementLog for each circuit, used upon transaction completion
//    to: (a) update UI selections after a transaction, (b) transfer circuit
//    simulation state to replacement components, (c) update gui.log, and
//    possibly other things.
class CircuitMutatorImpl implements CircuitMutator {

  final CircuitTransaction owner;
  private ArrayList<CircuitChange> log = new ArrayList<>();
  private HashMap<Circuit, ReplacementLog> replacements = new HashMap<>();
  private HashSet<Circuit> modified = new HashSet<>();

  CircuitMutatorImpl(CircuitTransaction xn) {
    owner = xn;
  }

  Collection<Circuit> getModifiedCircuits() {
    return Collections.unmodifiableSet(modified);
  }

  Collection<CircuitChange> getChangeLogFor(Circuit circuit) {
    ArrayList<CircuitChange> l = new ArrayList<>();
    for (CircuitChange change : log) {
      if (change.circuit == circuit)
        l.add(change);
    }
    return l;
  }

  ReplacementLog getReplacementLog(Circuit circuit) {
    return replacements.get(circuit);
  }

  public void applyChange(CircuitChange change) {
    ReplacementLog repl = null;
    if (change.circuit != null) {
      markModified(change.circuit);
      repl = replacements.get(change.circuit);
      if (repl == null) {
        repl = new ReplacementLog();
        replacements.put(change.circuit, repl);
      }
    }
    // if (change.vhdl != null) // no vhdl locks yet
    //   ...;
    log.add(change);
    change.apply(repl);
  }

  CircuitTransaction getReverseTransaction() {
    CircuitMutation ret = new CircuitMutation();
    for (int i = log.size() - 1; i >= 0; i--)
      ret.addToPlan(log.get(i).inverse());
    return ret;
  }

  void markModified(Circuit circuit) {
    modified.add(circuit);
    // Sanity check: circuit should have been locked by us
    CircuitMutatorImpl circMutator = circuit.getLocker().getMutator();
    if (circMutator != this) {
      System.err.println("*** Circuit Lock Bug Diagnostics ***");
      System.err.println("This thread: " + Thread.currentThread());
      System.err.println("  executing transaction:" + owner);
      System.err.println("  with mutator: " + this);
      System.err.println("  accessing circuits: " + owner.getAccessedCircuits());
      System.err.println("attempted to illegally modify");
      System.err.println("  non-locked circuit: " + circuit.getName());
      System.err.println("  with mutator: " + circMutator);
      Thread.dumpStack();
      // FIXME: perhaps throw (before adding to modified set)?
    }
  }

}
