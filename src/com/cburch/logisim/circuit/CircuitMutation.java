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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.std.hdl.VhdlContent;

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
  protected Map<Circuit, Integer> getAccessedCircuits() {
    HashMap<Circuit, Integer> accessMap = new HashMap<>();
    HashSet<Object> supercircsDone = new HashSet<>();
    // HashSet<VhdlEntity> vhdlDone = new HashSet<>();
    HashSet<ComponentFactory> siblingsDone = new HashSet<>();
    for (CircuitChange change : changes) {
      Circuit circ = change.getCircuit();
      VhdlContent vhdl = change.getVhdl();
      // note: if circ is null, change converns VhdlContent, which doesn't have
      // a lock yet.
      if (circ != null)
        accessMap.put(circ, READ_WRITE);

      if (vhdl != null &&
          change.concernsSupercircuit() && supercircsDone.add(vhdl)) {
        for (Circuit supercirc : vhdl.getEntityFactory().getCircuitsUsingThis())
          accessMap.put(supercirc, READ_WRITE);
      }
      if (circ != null &&
          change.concernsSupercircuit() && supercircsDone.add(circ)) {
        for (Circuit supercirc : circ.getCircuitsUsingThis())
          accessMap.put(supercirc, READ_WRITE);
      }

      if (change.concernsSiblingComponents()) {
        System.out.println("processing change that concerns siblings.. nvm");
        /*ComponentFactory factory = change.getComponent().getFactory();
        boolean isFirstForSibling = siblingsDone.add(factory);
        if (isFirstForSibling) {
          if (factory instanceof SubcircuitFactory) {
            Circuit sibling = ((SubcircuitFactory)factory).getSubcircuit();
            boolean isFirstForCirc = supercircsDone.add(sibling);
            if (isFirstForCirc) {
              for (Circuit supercirc : sibling.getCircuitsUsingThis()) {
                accessMap.put(supercirc, READ_WRITE);
              }
            }
          } else if (factory instanceof VhdlEntity) {
            VhdlEntity sibling = (VhdlEntity)factory;
            boolean isFirstForVhdl = vhdlDone.add(sibling);
            if (isFirstForVhdl) {
              for (Circuit supercirc : sibling.getCircuitsUsingThis()) {
                accessMap.put(supercirc, READ_WRITE);
              }
            }
          }
        } */
      }
    }
    return accessMap;
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
    ReplacementMap repl = new ReplacementMap(oldComp, newComp);
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
          mutator.replace(curCircuit, curReplacements);
        }
        curCircuit = circ;
        curReplacements = new ReplacementMap();
      }
      change.execute(mutator, curReplacements);
    }
    if (curCircuit != null) {
      mutator.replace(curCircuit, curReplacements);
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
