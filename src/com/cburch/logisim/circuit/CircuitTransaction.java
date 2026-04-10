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
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.cburch.logisim.circuit.appear.CircuitPins;

// CircuitTransaction is the base class for the transaction mechanism, which
// attempts to ensure concurrency-safe multi-threaded modification of circuits,
// and are used as part of the broader UI undo/redo mechanism.
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
// execute() - carries out the transaction, in phases:
//   1. Creates a mutator, which will capture changes.
//   2. Locks all accessed circuits, in a common serial order.
//   3. Calls run(mutator) to make changes.
//   4. Updates appearance of each modified circuit.
//   5. Repairs wires in each affected circuit.
//   6. Creates a result object summarizing changes.
//      - list of modified circuits, and ReplacementLog for each one
//      - ability to create the reverse transaction
//   7. Fires TRANSACTION_DONE events
//      - Selection updates the UI selection state
//      - Circuit transfers simulation state to replacement components
//      - gui.log updates tracked signals
//      - etc.
//   8. Unlocks all circuits.
//   9. Returns the result object.
public abstract class CircuitTransaction {

  private static final ThreadLocal<CircuitMutatorImpl>
    activeMutatorForThisThread = new ThreadLocal<>();

  public final CircuitTransactionResult execute() {

    // xn phase 0 - setup and sanity check for nested transaction
    CircuitTransactionResult result;
    CircuitMutatorImpl mutator = activeMutatorForThisThread.get();
    if (mutator != null) {
      diagnostics(mutator);
      throw new IllegalStateException("attempt to execute nested transactions");
    }
    Map<Circuit, Lock> locks = null;
    try {

      // xn phase 1
      mutator = new CircuitMutatorImpl(this);
      activeMutatorForThisThread.set(mutator);

      // xn phase 2
      locks = CircuitLocker.acquireLocks(this, mutator);

      try {
        // xn phase 3
        this.run(mutator);
      } catch (CircuitLocker.LockException e) {
        diagnostics(e, locks, mutator);
        throw e;
      }

      // TODO: remove stale appearance dynamic elements here instead
      // of in Circuit.mutatorRemove() ?

      // Sanity check: any circuit modified by us should have been locked by us.
      Collection<Circuit> modified = mutator.getModifiedCircuits();
      for (Circuit circuit : modified) {
        CircuitMutatorImpl circMutator = circuit.getLocker().getMutator();
        if (circMutator != mutator) {
          diagnostics(new CircuitLocker.LockException("illegal modification", circuit.getLocker()),
              locks, mutator);
        }
      }

      // xn phase 4
      // Let the port locations of each subcircuit's appearance be
      // updated to reflect the changes - this needs to happen before
      // wires are repaired because it could lead to some wires being
      // split
      for (Circuit circuit : modified) {
        ReplacementLog repl = mutator.getReplacementLog(circuit);
        if (repl != null) {
          CircuitPins pins = circuit.getAppearance().getCircuitPins();
          pins.transactionCompleted(repl);
        }
      }

      // xn phase 5
      // Now go through each affected circuit and repair its wires
      for (Circuit circuit : modified) {
        RepairWireHelper.repairWires(circuit, mutator);
      }

      // xn phase 6
      result = new CircuitTransactionResult(mutator);

      // xn phase 7
      for (Circuit circuit : result.getModifiedCircuits()) {
        circuit.fireEvent(CircuitEvent.TRANSACTION_DONE, result);
      }

    } finally {
      // xn phase 8
      if (locks != null)
        CircuitLocker.releaseLocks(locks);
      activeMutatorForThisThread.remove();
    }
    // xn phase 9
    return result;
  }
  
  private void diagnostics(CircuitMutatorImpl mutator) {
    System.err.println("*** Circuit Lock Bug Diagnostics ***");
    System.err.println("This thread: " + Thread.currentThread());
    System.err.println("  executing transaction:" + mutator.owner);
    System.err.println("  with mutator: " + mutator);
    System.err.println("attempted to illegally execute");
    System.err.println("  nested transaction:" + this);
  }

  private void diagnostics(CircuitLocker.LockException e, Map<Circuit, Lock> locks, CircuitMutator mutator) {
    System.err.println("*** Circuit Lock Bug Diagnostics ***");
    System.err.println("This thread: " + Thread.currentThread());
    System.err.println("owns " + locks.size() + " locks, as follows:");
    for (Map.Entry<Circuit, Lock> entry : locks.entrySet()) {
      Circuit circuit = entry.getKey();
      Lock lock = entry.getValue();
      System.err.printf("  circuit \"%s\" [lock serial: %d] with lock %s\n",
          circuit.getName(), circuit.getLocker().getSerialNumber(), lock);
    }
    System.err.println("attempted to access without a lock:");
    System.err.printf("  circuit \"%s\" [lock serial: %d/%d]\n",
        e.getCircuit().getName(), e.getSerialNumber(),
        e.getCircuit().getLocker().getSerialNumber());
    System.err.println("  owned by thread: " + e.getMutatingThread());
    System.err.println("  with mutator: " + e.getCircuitMutator());
  }

  protected abstract Set<Circuit> getAccessedCircuits();

  protected abstract void run(CircuitMutator mutator);

  public void dump() {
    System.out.println(" xn accesses circuits: " + getAccessedCircuits());
  }
}
