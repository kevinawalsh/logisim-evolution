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
import java.util.Enumeration;

import javax.swing.tree.TreeNode;

import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.instance.StdAttr;

public class SimulationTreeNode implements TreeNode,
  CircuitListener, AttributeListener, Comparator<Component> {

  protected SimulationTreeModel model;
  protected SimulationTreeNode parent;
  protected ArrayList<TreeNode> children; // only contains SimulationTreeNode objects

  private CircuitState circuitState;
  private Component subcircComp;

  public SimulationTreeNode(SimulationTreeModel model, SimulationTreeNode parent,
      CircuitState circuitState, Component subcircComp) {
    this.model = model;
    this.parent = parent;
    this.children = new ArrayList<TreeNode>();
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

  public Enumeration<TreeNode> children() {
    return Collections.enumeration(children);
  }

  public boolean getAllowsChildren() {
    return true;
  }

  public TreeNode getChildAt(int index) {
    return children.get(index);
  }

  public int getChildCount() {
    return children.size();
  }

  public int getIndex(TreeNode node) {
    return children.indexOf(node);
  }

  public TreeNode getParent() {
    return parent;
  }

  public boolean isCurrentView(SimulationTreeModel model) {
    return false;
  }
  
  public boolean onCurrentViewPath(SimulationTreeModel model) {
    return false;
  }

  public boolean isLeaf() {
    return false;
  }

  // Subclasses can call this when this node's appearance (icon, label,
  // etc.) has changed. It will fire the appropriate TreeModelEvent.
  public void fireAppearanceChanged() {
    if (parent == null) {
      model.fire(model.getPath(this), new int[0], null,
          (l,e) -> l.treeNodesChanged(e));
    } else {
      int[] indices = new int[] { parent.getIndex(this) };
      SimulationTreeNode[] nodes = new SimulationTreeNode[] { this };
      model.fire(model.getPath(parent), indices, nodes,
          (l,e) -> l.treeNodesChanged(e));
    }
  }

  public void fireStructureChanged() {
    model.fire(model.getPath(this), new int[0], null,
        (l,e) -> l.treeStructureChanged(e));
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
  
  private static class CompareByName implements Comparator<Object> {
    public int compare(Object a, Object b) {
      return a.toString().compareToIgnoreCase(b.toString());
    }
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

  // FIXME: compute this only on-demand, caching results when circuit has not changed.
  // returns true if changed
  private boolean computeChildren() {
    ArrayList<TreeNode> newChildren = new ArrayList<TreeNode>();

    int mismatches = 0;
    for (Component childComp : circuitState.getCircuit().getNonWires()) {
      if (!(childComp.getFactory() instanceof SubcircuitFactory))
        continue;
      CircuitState childState = circuitState.getCircuitSubstateFor(childComp);
      TreeNode childNode = getChildFor(childState);
      if (childNode == null) {
        childNode = new SimulationTreeNode(model, this, childState, childComp);
        mismatches++;
      }
      newChildren.add(childNode);
    }

    if (mismatches == 0 && newChildren.size() == children.size()) {
      return false; // no changes
    } else {
      children = newChildren;
      return true; // changed
    }
  }

  public SimulationTreeNode getChildFor(CircuitState childState) {
    for (TreeNode o : children) {
      SimulationTreeNode n = (SimulationTreeNode) o;
      if (n.circuitState == childState)
        return n;
    }
    return null;
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  public ComponentFactory getComponentFactory() {
    return circuitState.getCircuit().getSubcircuitFactory();
  }

  @Override
  public String toString() {
    if (subcircComp != null) {
      String label = subcircComp.getAttributeSet().getValue(StdAttr.LABEL);
      if (label != null && !label.equals(""))
        return label;
    }
    String ret = circuitState.getCircuit().getName();
    if (subcircComp != null)
      ret += " @ " + subcircComp.getLocation();
    return ret;
  }
}
