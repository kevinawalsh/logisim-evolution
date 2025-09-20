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

package com.cburch.logisim.gui.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.tree.TreeNode;

import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.instance.StdAttr;

class SimulationTreeCircuitNode extends SimulationTreeNode
  implements CircuitListener, AttributeListener, Comparator<Component> {
  private static class CompareByName implements Comparator<Object> {
    public int compare(Object a, Object b) {
      return a.toString().compareToIgnoreCase(b.toString());
    }
  }

  private CircuitState circuitState;
  private Component subcircComp;

  public SimulationTreeCircuitNode(SimulationTreeModel model,
      SimulationTreeNode parent, CircuitState circuitState,
      Component subcircComp) {
    super(model, parent);
    this.circuitState = circuitState;
    this.subcircComp = subcircComp;
    circuitState.getCircuit().addCircuitWeakListener(null, this);
    if (subcircComp != null) {
      subcircComp.getAttributeSet().addAttributeWeakListener(null, this);
    } else {
      circuitState.getCircuit().getStaticAttributes().addAttributeWeakListener(null, this);
    }
    computeChildren();
  }

  public void attributeListChanged(AttributeEvent e) {
  }

  public void attributeValueChanged(AttributeEvent e) {
    Object attr = e.getAttribute();
    if (attr == CircuitAttributes.CIRCUIT_LABEL_ATTR || attr == StdAttr.LABEL)
      fireAppearanceChanged();
  }

  public void circuitChanged(CircuitEvent event) {
    int action = event.getAction();
    if (action == CircuitEvent.ACTION_SET_NAME)
      fireAppearanceChanged();
    else if (action != CircuitEvent.ACTION_INVALIDATE && computeChildren())
      fireStructureChanged(); // fixme: use add/remove instead to preserve expand state
  }

  public int compare(Component a, Component b) {
    if (a != b) {
      String aName = a.getFactory().getDisplayName();
      String bName = b.getFactory().getDisplayName();
      int ret = aName.compareToIgnoreCase(bName);
      if (ret != 0)
        return ret;
    }
    return a.getLocation().toString().compareTo(b.getLocation().toString());
  }

  // returns true if changed
  private boolean computeChildren() {
    ArrayList<TreeNode> newChildren = new ArrayList<TreeNode>();
    ArrayList<Component> subcircs = new ArrayList<Component>();
    for (Component comp : circuitState.getCircuit().getNonWires()) {
      if (comp.getFactory() instanceof SubcircuitFactory) {
        subcircs.add(comp);
      } else {
        TreeNode toAdd = model.mapComponentToNode(comp);
        if (toAdd != null) {
          newChildren.add(toAdd);
        }
      }
    }
    Collections.sort(newChildren, new CompareByName());
    Collections.sort(subcircs, this);
    for (Component comp : subcircs) {
      CircuitState state = circuitState.getCircuitSubstateFor(comp);
      SimulationTreeCircuitNode toAdd = null;
      for (TreeNode o : children) {
        if (o instanceof SimulationTreeCircuitNode) {
          SimulationTreeCircuitNode n = (SimulationTreeCircuitNode) o;
          if (n.circuitState == state) {
            toAdd = n;
            break;
          }
        }
      }
      if (toAdd == null) {
        toAdd = new SimulationTreeCircuitNode(model, this, state, comp);
      }
      newChildren.add(toAdd);
    }

    if (!children.equals(newChildren)) {
      children = newChildren;
      return true;
    } else {
      return false;
    }
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  @Override
  public ComponentFactory getComponentFactory() {
    return circuitState.getCircuit().getSubcircuitFactory();
  }

  @Override
  public boolean isCurrentView(SimulationTreeModel model) {
    return model.getCurrentView() == circuitState;
  }

  @Override
  public boolean onCurrentViewPath(SimulationTreeModel model) {
    CircuitState bottom = model.getBottomView();
    if (bottom == null)
      return false;
    else
      return bottom == circuitState || bottom.hasAncestorState(circuitState);
  }

  @Override
  public String toString() {
    if (subcircComp != null) {
      String label = subcircComp.getAttributeSet()
          .getValue(StdAttr.LABEL);
      if (label != null && !label.equals(""))
        return label;
    }
    String ret = circuitState.getCircuit().getName();
    if (subcircComp != null)
      ret += subcircComp.getLocation();
    if (!circuitState.isSubstate())
      ret += " [simulation "+circuitState.getId()+"]";
    return ret;
  }
}
