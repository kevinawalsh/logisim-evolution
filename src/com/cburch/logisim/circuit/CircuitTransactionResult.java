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

// CircuitTransactionResult is a read-only summary of the results of a completed CircuitTransaction.
// It is implemented as a thing wrapper around the CircuitMutator/CircuitMutatorImpl which was used
// to execute the transaction, since that object has all the relevant info.
public class CircuitTransactionResult {
  private final CircuitMutatorImpl mutator;

  CircuitTransactionResult(CircuitMutatorImpl mutator) {
    this.mutator = mutator;
  }

  public Collection<Circuit> getModifiedCircuits() {
    return mutator.getModifiedCircuits();
  }

  public ReplacementMap getReplacementMap(Circuit circuit) {
    ReplacementMap ret = mutator.getReplacementMap(circuit);
    return ret == null ? new ReplacementMap() : ret;
  }

  public CircuitTransaction getReverseTransaction() {
    return mutator.getReverseTransaction();
  }

  public String toString() {
    String s = "CircuitTransactionResult affecting...";
    for (Circuit c : getModifiedCircuits()) {
      s += "\n    - circuit " + c + " with replacements...";
      s += "\n" + getReplacementMap(c);
      s += "\n      and changes...";
      for (CircuitChange cc : mutator.getChangeLogFor(c))
        s += "\n" + cc;
    }
    return s;
  }
}
