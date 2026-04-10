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
import static com.cburch.logisim.circuit.Strings.S;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.std.hdl.VhdlContent;

// This is a general purpose CircuitMutation that is used by a variety of
// clients to modify circuits or vhdl. Clients include: wiring tool, edit tool,
// selection tools, text tool, the "analysis" circuit builder, and many others.
//
// Typical client usage:
//   CircuitMutation xn = new CircuitMutation.forCircuit(someCircuit);
//   xn.add(componentA);
//   xn.remove(componentB);
//   xn.remove(componentC);
//   xn.replace(componentOld, componentNew);
//   xn.set(componentA, StdAttr.LABEL, "Hello World");
//   xn.execute()
//
// Note: Class is misnamed...
//   CircuitMutation - used for any set of changes to circuit(s) and vhdl(s)
//   CircuitMutation.forCircuit - convenience subclass for a single circuit
//   CircuitMutation.forVhdl - convenience subclass for a single vhdl
//
// A list of *planned* changes is maintained. Each helper method like xn.add(),
// xn.remove(), and xn.set(), does not modify the circuit but instead appends a
// new CircuitChange to the plan.
//
// During xn.execute(), implemented by CircuitTransaction, the list is used for:
//  - getAccessedCircuits(): to determine the full set of affected circuits
//  - run(): to carry out each change in the plan, in sequential order
//
// Notes:
//  - The plan here is not the full set of changes performed during the
//    transaction... a transaction also does wire repairs, and might do some
//    other cleanup for appearance, etc. The plan here is not directly used for
//    undo.
//
//  - CircuitMutatorImpl, used in CircuitTransaction.execute(), keeps a change log, a
//    second list of CircuitChange objects, built as each change is applied.
//    That list is comprehensive, and includes changes made via wire repairs,
//    and changes made via mutator methods like like mutator.add(circ, comp).
//    That list is authoritative, used for undo, and is built even for other
//    subclasses of CircuitTransaction besides this one.

public class CircuitMutation extends CircuitTransaction {
  protected ArrayList<CircuitChange> plan = new ArrayList<>();

  public void addToPlan(CircuitChange change) {
    if (change != null)
      plan.add(change);
  }

  public boolean isEmpty() {
    return plan.isEmpty();
  }

  @Override
  protected Set<Circuit> getAccessedCircuits() {
    HashSet<Circuit> access = new HashSet<>();
    HashSet<Object> supercircsDone = new HashSet<>();
    // HashSet<VhdlEntity> vhdlDone = new HashSet<>(); // no vhdl locks yet
    for (CircuitChange change : plan) {
      Circuit circ = change.circuit;
      VhdlContent vhdl = change.vhdl;
      // note: if circ is null, change concerns vhdl, which doesn't have a lock yet.
      if (circ != null)
        access.add(circ);
      if (vhdl != null && change.concernsSupercircuit() && supercircsDone.add(vhdl))
          access.addAll(vhdl.getEntityFactory().getCircuitsUsingThis());
      if (circ != null && change.concernsSupercircuit() && supercircsDone.add(circ))
        access.addAll(circ.getCircuitsUsingThis());
    }
    return access;
  }

  @Override
  protected void run(CircuitMutator mutator) {
    for (CircuitChange change : plan)
      mutator.applyChange(change);
  }
  
  public Action toAction(StringGetter name) {
    if (name == null)
      name = S.getter("unknownChangeAction");
    return new CircuitAction(name, this);
  }

  // Lots of callers expect CircuitMutation to have all of the convenience
  // methods. So we include them here, all throwing errors, and the two
  // subclasses below override them.

  // convenience methods for Circuit
  public void add(Component comp) {
    throw new UnsupportedOperationException();
  }
  public void addAll(Collection<? extends Component> comps) {
    throw new UnsupportedOperationException();
  }
  public void remove(Component comp) {
    throw new UnsupportedOperationException();
  }
  public void removeAll(Collection<? extends Component> comps) {
    throw new UnsupportedOperationException();
  }
  public void replacePairs(Map<Component, Component> pairs) {
    throw new UnsupportedOperationException();
  }
  public void replacePairs(List<Component> oldComps, List<Component> newComps) {
    throw new UnsupportedOperationException();
  }
  public void repairWires(Collection<Wire> oldWires, Collection<Wire> newWires) {
    throw new UnsupportedOperationException();
  }
  public void set(Component comp, Attribute<?> attr, Object value) {
    throw new UnsupportedOperationException();
  }
  public void setForCircuit(Attribute<?> attr, Object value) {
    throw new UnsupportedOperationException();
  }
  
  // convenience methods for VhdlContent
  public void setForVhdl(Attribute<?> attr, Object value) {
    throw new UnsupportedOperationException();
  }


  // convenience subclass for modifying a single circuit
  public static CircuitMutation forCircuit(Circuit circuit) {
    return new CircuitMutationForCircuit(circuit);
  }
  static class CircuitMutationForCircuit extends CircuitMutation {
    private Circuit primaryCircuit;
    public CircuitMutationForCircuit(Circuit circuit) {
      primaryCircuit = circuit;
    }

    // convenience methods: same as addToPlan(new CircuitChange.FOO(circuit, ...)
    @Override
    public void add(Component comp) {
      plan.add(new CircuitChange.ADD(primaryCircuit, comp));
    }
    @Override
    public void addAll(Collection<? extends Component> comps) {
      plan.add(new CircuitChange.ADD_ALL(primaryCircuit, comps));
    }
    @Override
    public void remove(Component comp) {
      plan.add(new CircuitChange.REMOVE(primaryCircuit, comp));
    }
    @Override
    public void removeAll(Collection<? extends Component> comps) {
      plan.add(new CircuitChange.REMOVE_ALL(primaryCircuit, comps));
    }
    @Override
    public void replacePairs(Map<Component, Component> pairs) {
      plan.add(new CircuitChange.REPLACE_PAIRS(primaryCircuit, pairs));
    }
    @Override
    public void replacePairs(List<Component> oldComps, List<Component> newComps) {
      plan.add(new CircuitChange.REPLACE_PAIRS(primaryCircuit, oldComps, newComps));
    }
    @Override
    public void repairWires(Collection<Wire> oldWires, Collection<Wire> newWires) {
      plan.add(new CircuitChange.REPAIR_WIRES(primaryCircuit, oldWires, newWires));
    }
    @Override
    public void set(Component comp, Attribute<?> attr, Object value) {
      plan.add(new CircuitChange.SET_COMP_ATTR(primaryCircuit, comp, attr, value));
    }
    @Override
    public void setForCircuit(Attribute<?> attr, Object value) {
      plan.add(new CircuitChange.SET_CIRC_ATTR(primaryCircuit, attr, value));
    }

    @Override
    public void dump() {
      super.dump();
      int n = plan.size();
      System.out.println(" xn plan of " + n + " changes to circuit " + primaryCircuit.getName());
      for (int i = 0; i < n; i++)
        System.out.println("   planned_change["+i+"]: " + plan.get(i));
    }
  }
  
  // convenience subclass for modifying a single vhdl
  public static CircuitMutation forVhdl(VhdlContent vhdl) {
    return new CircuitMutationForVhdl(vhdl);
  }
  static class CircuitMutationForVhdl extends CircuitMutation {
    private VhdlContent primaryVhdl;
    public CircuitMutationForVhdl(VhdlContent vhdl) {
      primaryVhdl = vhdl;
    }

    // convenience methods: same as addToPlan(new CircuitChange.FOO(vhdl, ...)
    @Override
    public void setForVhdl(Attribute<?> attr, Object value) {
      plan.add(new CircuitChange.SET_VHDL_ATTR(primaryVhdl, attr, value));
    }

    @Override
    public void dump() {
      super.dump();
      int n = plan.size();
      System.out.println(" xn plan of " + n + " changes to vhdl " + primaryVhdl.getName());
      for (int i = 0; i < n; i++)
        System.out.println("   planned_change["+i+"]: " + plan.get(i));
    }
  }

  @Override
  public void dump() {
    super.dump();
    int n = plan.size();
    System.out.println(" xn plan of " + n + " changes");
    for (int i = 0; i < n; i++)
      System.out.println("   planned_change["+i+"]: " + plan.get(i));
  }
}
