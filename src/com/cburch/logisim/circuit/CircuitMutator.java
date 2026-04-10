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
import java.util.List;
import java.util.Map;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.std.hdl.VhdlContent;

// CircuitTransactions shouldn't generally call methods to modify a circuit
// directly. Instead, all mutations should be done through these helpers.
// Note: CircuitMutatorImpl is the only implementation of this interface.
public interface CircuitMutator {

  public void applyChange(CircuitChange change);

  // convenience methods: same as applyChange(new CircuitChange...)
  public void add(Circuit circuit, Component comp) {
    applyChange(new CircuitChange.ADD(circuit, comp));
  }
  public void addAll(Circuit circuit, Collection<Component> comps) {
    applyChange(new CircuitChange.ADD_ALL(circuit, comps));
  }
  public void remove(Circuit circuit, Component comp) {
    applyChange(new CircuitChange.REMOVE(circuit, comp));
  }
  public void removeAll(Circuit circuit, Collection<Component> comps) {
    applyChange(new CircuitChange.REMOVE_ALL(circuit, comps));
  }
  public void replacePairs(Circuit circuit, Map<Component, Component> pairs) {
    applyChange(new CircuitChange.REPLACE_PAIRS(circuit, pairs));
  }
  public void replacePairs(Circuit circuit, List<Component> oldComps, List<Component> newComps) {
    applyChange(new CircuitChange.REPLACE_PAIRS(circuit, oldComps, newComps));
  }
  public void repairWires(Circuit circuit, Collection<Wire> oldWires, Collection<Wire> newWires) {
    applyChange(new CircuitChange.REPAIR_WIRES(circuit, oldWires, newWires));
  }
  public void set(Circuit circuit, Component comp, Attribute<?> attr, Object value) {
    applyChange(new CircuitChange.SET_COMP_ATTR(circuit, comp, attr, value));
  }
  public void setForCircuit(Circuit circuit, Attribute<?> attr, Object value) {
    applyChange(new CircuitChange.SET_CIRC_ATTR(circuit, attr, value));
  }
  public void setForVhdl(VhdlContent vhdl, Attribute<?> attr, Object value) {
    applyChange(new CircuitChange.SET_VHDL_ATTR(vhdl, attr, value));
  }
}
