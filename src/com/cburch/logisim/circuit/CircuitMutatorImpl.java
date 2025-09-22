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

// fixme: this is the one and only implementation of CircuitMutator
class CircuitMutatorImpl implements CircuitMutator {
  private ArrayList<CircuitChange> log = new ArrayList<>();
  private HashMap<Circuit, ReplacementMap> replacements = new HashMap<>();
  private HashSet<Circuit> modified = new HashSet<>();

  public CircuitMutatorImpl() { }

  public void add(Circuit circuit, Component comp) {
    modified.add(circuit);
    log.add(CircuitChange.add(circuit, comp));

    ReplacementMap repl = new ReplacementMap();
    repl.add(comp);
    getMap(circuit).append(repl);

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
  }

  public void remove(Circuit circuit, Component comp) {
    if (circuit.contains(comp)) {
      modified.add(circuit);
      log.add(CircuitChange.remove(circuit, comp));

      ReplacementMap repl = new ReplacementMap();
      repl.remove(comp);
      getMap(circuit).append(repl);

      circuit.mutatorRemove(comp);
    }
  }

  public void replace(Circuit circuit, Component prev, Component next) {
    replace(circuit, new ReplacementMap(prev, next));
  }

  public void replace(Circuit circuit, ReplacementMap repl) {
    ArrayList<Component> added = new ArrayList<>();
    if (!repl.isEmpty()) {
      modified.add(circuit);
      log.add(CircuitChange.replace(circuit, repl));

      repl.freeze();
      getMap(circuit).append(repl);

      for (Component c : repl.getRemovals()) {
        Collection<Component> replacements = repl.getReplacementsFor(c);
        if (replacements != null && replacements.size() == 1) {
          Component r = replacements.iterator().next();
          circuit.mutatorReplace(c, r);
          added.add(r);
        } else {
          circuit.mutatorRemove(c);
        }
      }
      for (Component c : repl.getAdditions()) {
        if (!added.contains(c))
          circuit.mutatorAdd(c);
      }
    }
  }

  public void set(Circuit circuit, Component comp, Attribute<?> attr,
      Object newValue) {
    if (circuit.contains(comp)) {
      modified.add(circuit);
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
