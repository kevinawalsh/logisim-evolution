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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import com.cburch.logisim.circuit.appear.CircuitPins;

// CircuitTransaction is the base class for the transaction mechanism, which attempts to ensure
// concurrency-safe multi-threaded access and modifications to circuits.
//
// Primary (general purpose) subclass:
//  - CircuitMutation: used for most edits to a circuit from user and other triggers
// Other known (more specialized) subclasses:
//  - XmlCircuitReader: used after xml parsing loading
//  - VhdlUpdatedTransaction: unknown
//  - RevertAppearanceAction.ActionTransaction: reverts circuit appearance upon menu trigger
//  - CanvasActionAdapter.ActionTransaction: updates circuit appearance upon drawing updates
//  - Circuit.EndsChangedTransaction: unclear
//
// Each subclass provides:
//   getAccessedCircuits() - Specifies which circuits the xn accesses.
//   run(mutator) - Makes changes using the provided CircuitMutator object.
//
// And this class provides:
// execute() - carries out the transaction..
//   1. Creates a mutator.
//   2. Locks all accessed circuits, in a stable serial order.
//   3. Calls run(mutator) to make changes.
//   4. Updates appearance of each modified circuit.
//   5. Repairs wires in each affected circuit.
//   6. Creates a result object summarizing all mutations.
//      - list of modified circuits, and ReplacementMap for each one
//      - ability to create the reverse transaction
//   7. Fires TRANSACTION_DONE events
//      - Selection updates the UI selection state
//      - Circuit transfers simulation state to replacement components
//      - gui.log updates tracked signals
//      - etc.
//   8. Unlocks all circuits.
//   9. Returns the result object.
public abstract class CircuitTransaction {
  // public static final Integer READ_ONLY = 1; // never used
  public static final Integer READ_WRITE = 2;

  public final CircuitTransactionResult execute() {
    CircuitMutatorImpl mutator = new CircuitMutatorImpl();
    Map<Circuit, Lock> locks = CircuitLocker.acquireLocks(this, mutator);
    try {
      try {
        this.run(mutator);
      } catch (CircuitLocker.LockException e) {
        System.out.println("*** Circuit Lock Bug Diagnostics ***");
        System.out.println("This thread: " + Thread.currentThread());
        System.out.println("owns " + locks.size() + " locks, as follows:");
        for (Map.Entry<Circuit, Lock> entry : locks.entrySet()) {
          Circuit circuit = entry.getKey();
          Lock lock = entry.getValue();
          System.out.printf("  circuit \"%s\" [lock serial: %d] with lock %s\n",
              circuit.getName(), circuit.getLocker().getSerialNumber(), lock);
        }
        System.out.println("attempted to access without a lock:");
        System.out.printf("  circuit \"%s\" [lock serial: %d/%d]\n",
            e.getCircuit().getName(), e.getSerialNumber(),
            e.getCircuit().getLocker().getSerialNumber());
        System.out.println("  owned by thread: " + e.getMutatingThread());
        System.out.println("  with mutator: " + e.getCircuitMutator());
        throw e;
      }

      // TODO: remove stale appearance dynamic elements here instead
      // of in Circuit.mutatorRemove() ?

      // Let the port locations of each subcircuit's appearance be
      // updated to reflect the changes - this needs to happen before
      // wires are repaired because it could lead to some wires being
      // split
      Collection<Circuit> modified = mutator.getModifiedCircuits();
      for (Circuit circuit : modified) {
        CircuitMutatorImpl circMutator = circuit.getLocker().getMutator();
        if (circMutator == mutator) { // FIXME: is this ever false? when?
          ReplacementMap repl = mutator.getReplacementMap(circuit);
          if (repl != null) {
            CircuitPins pins = circuit.getAppearance().getCircuitPins();
            pins.transactionCompleted(repl);
          }
        }
      }

      // Now go through each affected circuit and repair its wires
      for (Circuit circuit : modified) {
        CircuitMutatorImpl circMutator = circuit.getLocker().getMutator();
        if (circMutator == mutator) { // FIXME: is this ever false? when?
          RepairWireHelper.repairWires(circuit, mutator);
        } else {
          System.err.println("HUH?");
          // this is a transaction executed within a transaction -
          // wait to repair wires until overall transaction is done
          // FIXME: where does that happen?
          // FIXME: ??? why are there transactions within transactions... seems unsafe, no?
          circMutator.markModified(circuit);
        }
      }

      CircuitTransactionResult result;
      result = new CircuitTransactionResult(mutator);
      for (Circuit circuit : result.getModifiedCircuits()) {
        circuit.fireEvent(CircuitEvent.TRANSACTION_DONE, result);
      }
      return result;
    } finally {
      CircuitLocker.releaseLocks(locks);
    }
  }

  protected abstract Map<Circuit, Integer> getAccessedCircuits();

  protected abstract void run(CircuitMutator mutator);

}
