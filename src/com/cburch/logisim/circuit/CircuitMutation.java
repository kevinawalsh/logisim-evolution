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
import java.util.Set;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.std.hdl.VhdlContent;

// This is a general purpose CircuitMutation that is used by a variety of
// clients to modify circuits. Clients include: wiring tool, edit tool,
// selection tools, text tool, the "analysis" circuit builder, and many others.
//
// Typical client usage:
//   CircuitMutation xn = new CircuitMutation(someCircuit);
//   xn.add(componentA);
//   xn.remove(componentB);
//   xn.remove(componentC);
//   xn.replace(componentOld, componentNew);
//   xn.set(componentA, StdAttr.LABEL, "Hello World");
//   xn.execute()
//
// A list of changes is maintained. Each helper method like xn.add(),
// xn.remove(), and xn.set(), does not modify the circuit but insteaad appends a
// new CircuitChange to the list.
//
// During xn.execute(), implemented by CircuitTransaction, the list is used for:
//  - getAccessedCircuits(): to determine the full set of affected circuits
//  - run(): to carry out each change, in sequential order
//
// Notes:
//
//  - We keep a CircuitChange list. But so does CircuitMutatorImpl. As we
//    execute each change in our list, a corresponding entry is made in the
//    mutator's change list. This seems strangely redundant. On the other hand
//    (a) the mutator's change list captures old attribute values when changing
//    component or circuit attributes so it can operate in reverse, whereas we
//    don't capture the old attribute values and only operate as a forward
//    transaction, and (b) is used by other CircuitTransaction subclasses that
//    don't necessarily use CircuitChange like we do here.
//
//  - run() seems to be able to handle CircuitChange objects that modify
//    diffferent circuits. But all helper except .change() create CircuitChange
//    objects that modify the same primary circuit. And .change() is called only
//    when creating a reverse transaction, using its own CircuitChange list
//    which should closely mirror our own CircuitChange list. However, it could
//    be that this multi-circuit capability is used for the reverse transaction
//    for other CircuitTransaction subclassess, if those touch multiple
//    circuits.
//
//  - Many CircuitChange objects are meant to add, remove, or replace components
//    and wires, and these effects are not done immediately but instead
//    accumulated into a ReplacementMap, before applying them to a circuit in a
//    batch. Why? This batching is interrupted, i.e. flushed, via
//    mutator.applyReplacements(), whenever we change the target circuit (but
//    see multi-circuit note above), or whenever one of the CircuitChanges is a
//    SET (to change a component attribute) or SET_FOR_CIRCUIT (to change a
//    circuit attribute) type. And, when flushed to the underlying mutator, our
//    replacement map containing the next batch of changes, is both applied to
//    the circuit, then also composed with another per-circuit replacement
//    within the underlying mutator. The reason for our batching, as a second
//    layer, isn't clear. Was it some kind of optimization?
//
public final class CircuitMutation extends CircuitTransaction {
  private Circuit primaryCircuit;
  private VhdlContent primaryVhdl;
  private ArrayList<CircuitChange> changes = new ArrayList<>();

  CircuitMutation() { }

  public CircuitMutation(Circuit circuit) {
    primaryCircuit = circuit;
  }

  public CircuitMutation(VhdlContent vhdl) {
    primaryVhdl = vhdl;
  }

  public void add(Component comp) {
    changes.add(CircuitChange.add(primaryCircuit, comp));
  }

  public void addAll(Collection<? extends Component> comps) {
    changes.add(CircuitChange.addAll(primaryCircuit, new ArrayList<Component>(comps)));
  }

  void change(CircuitChange change) {
    changes.add(change);
  }

  @Override
  protected Set<Circuit> getAccessedCircuits() {
    HashSet<Circuit> access = new HashSet<>();
    HashSet<Object> supercircsDone = new HashSet<>();
    // HashSet<VhdlEntity> vhdlDone = new HashSet<>();
    // HashSet<ComponentFactory> siblingsDone = new HashSet<>();
    for (CircuitChange change : changes) {
      Circuit circ = change.getCircuit();
      VhdlContent vhdl = change.getVhdl();
      // note: if circ is null, change concerns vhdl, which doesn't have a lock yet.
      if (circ != null)
        access.add(circ);

      if (vhdl != null && change.concernsSupercircuit() && supercircsDone.add(vhdl))
          access.addAll(vhdl.getEntityFactory().getCircuitsUsingThis());
      if (circ != null && change.concernsSupercircuit() && supercircsDone.add(circ))
        access.addAll(circ.getCircuitsUsingThis());

      // if (change.concernsSiblingComponents()) {
      //   System.out.println("processing change that concerns siblings.. nvm");
      //   ComponentFactory factory = change.getComponent().getFactory();
      //   boolean isFirstForSibling = siblingsDone.add(factory);
      //   if (isFirstForSibling) {
      //     if (factory instanceof SubcircuitFactory) {
      //       Circuit sibling = ((SubcircuitFactory)factory).getSubcircuit();
      //       boolean isFirstForCirc = supercircsDone.add(sibling);
      //       if (isFirstForCirc) {
      //         access.addAll(sibling.getCircuitsUsingThis());
      //       }
      //     } else if (factory instanceof VhdlEntity) {
      //       VhdlEntity sibling = (VhdlEntity)factory;
      //       boolean isFirstForVhdl = vhdlDone.add(sibling);
      //       if (isFirstForVhdl) {
      //          access.addAll(sibling.getCircuitsUsingThis());
      //       }
      //     }
      //   }
      // }
    }
    return access;
  }

  public boolean isEmpty() {
    return changes.isEmpty();
  }

  public void remove(Component comp) {
    changes.add(CircuitChange.remove(primaryCircuit, comp));
  }

  public void removeAll(Collection<? extends Component> comps) {
    changes.add(CircuitChange.removeAll(primaryCircuit, new ArrayList<Component>(comps)));
  }

  public void replace(Component oldComp, Component newComp) {
    ReplacementMap repl = ReplacementMap.forReplacement(oldComp, newComp);
    changes.add(CircuitChange.replace(primaryCircuit, repl));
  }

  public void replace(ReplacementMap replacements) {
    if (!replacements.isEmpty()) {
      replacements.freeze();
      changes.add(CircuitChange.replace(primaryCircuit, replacements));
    }
  }

  @Override
  protected void run(CircuitMutator mutator) {
    Circuit curCircuit = null;
    ReplacementMap curReplacements = null;
    for (CircuitChange change : changes) {
      Circuit circ = change.getCircuit();
      if (circ != curCircuit) {
        if (curCircuit != null) {
          mutator.applyReplacements(curCircuit, curReplacements);
        }
        curCircuit = circ;
        curReplacements = new ReplacementMap();
      }
      change.execute(mutator, curReplacements);
    }
    if (curCircuit != null) {
      mutator.applyReplacements(curCircuit, curReplacements);
    }
  }

  public void set(Component comp, Attribute<?> attr, Object value) {
    changes.add(CircuitChange.set(primaryCircuit, comp, attr, value));
  }

  public void setForCircuit(Attribute<?> attr, Object value) {
    changes.add(CircuitChange.setForCircuit(primaryCircuit, attr, value));
  }

  public void setForVhdl(Attribute<?> attr, Object value) {
    changes.add(CircuitChange.setForVhdl(primaryVhdl, attr, value));
  }

  public Action toAction(StringGetter name) {
    if (name == null)
      name = S.getter("unknownChangeAction");
    return new CircuitAction(name, this);
  }
}
