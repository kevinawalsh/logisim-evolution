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

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import com.cburch.logisim.circuit.CircuitState;

public class SimulationTreeModel implements TreeModel {
  private EventListenerList listeners = new EventListenerList();
  private SimulationTreeNode root;
  // private CircuitState currentView, bottomView;

  public SimulationTreeModel(CircuitState cs) {
    this.root = new SimulationTreeNode(this, null, cs, null);
    // this.currentView = null;
    // this.bottomView = null;
  }

  public void addTreeModelListener(TreeModelListener l) {
    listeners.add(TreeModelListener.class, l);
  }

  public TreePath getPath(Object node) {
    ArrayList<Object> path = new ArrayList<Object>();
    Object current = node;
    while (current instanceof TreeNode) {
      path.add(0, current);
      current = ((TreeNode) current).getParent();
    }
    if (current != null)
      path.add(0, current);
    return new TreePath(path.toArray());
  }

  public Object getChild(Object parent, int index) {
    if (parent instanceof TreeNode)
      return ((TreeNode) parent).getChildAt(index);
    return null;
  }

  public int getChildCount(Object parent) {
    if (parent instanceof TreeNode)
      return ((TreeNode) parent).getChildCount();
    return 0;
  }

  // public CircuitState getCurrentView() {
  //   return currentView;
  // }

  // public CircuitState getBottomView() {
  //   return bottomView;
  // }

  public int getIndexOfChild(Object parent, Object child) {
    if (parent instanceof TreeNode && child instanceof TreeNode)
      return ((TreeNode) parent).getIndex((TreeNode) child);
    return -1;
  }

  public Object getRoot() {
    return root;
  }

  public boolean isLeaf(Object node) {
    if (node instanceof TreeNode)
      return ((TreeNode) node).getChildCount() == 0;
    return true;
  }

  // protected SimulationTreeNode mapComponentToNode(Component comp) {
  //   return null;
  // }

  // private SimulationTreeCircuitNode mapToNode(CircuitState state) {
  //   TreePath path = mapToPath(state);
  //   if (path == null) {
  //     return null;
  //   } else {
  //     return (SimulationTreeCircuitNode) path.getLastPathComponent();
  //   }
  // }

  public TreePath mapToPath(CircuitState state) {
    if (state == null)
      return null;
    ArrayList<CircuitState> path = new ArrayList<CircuitState>();
    CircuitState current = state;
    CircuitState parent = current.getParentState();
    while (parent != null && parent != state) {
      path.add(current);
      current = parent;
      parent = current.getParentState();
    }
    // We should have reached root
    if (current != ((SimulationTreeNode)root).getCircuitState())
      return null;

    Object[] pathNodes = new Object[path.size() + 1];
    pathNodes[0] = root;
    SimulationTreeNode prevNode = root;
    int pathPos = 1;
    for (int i = path.size() - 1; i >= 0; i--) {
      current = path.get(i);
      SimulationTreeNode nextNode = prevNode.getChildFor(path.get(i));
      if (nextNode == null)
        return null;
      pathNodes[pathPos] = nextNode;
      pathPos++;
      prevNode = nextNode;
    }
    return new TreePath(pathNodes);
  }

  public void removeTreeModelListener(TreeModelListener l) {
    listeners.remove(TreeModelListener.class, l);
  }

  // public void setCurrentView(CircuitState value) {
  //   CircuitState oldView = currentView;
  //   CircuitState oldBottomView = bottomView;
  //   if (value == null) {
  //     currentView = bottomView = null;
  //   } else if (oldView != value) {
  //     currentView = value;
  //     if (bottomView == null) {
  //       bottomView = currentView;
  //     } else if (currentView != bottomView) {
  //       if (!bottomView.hasAncestorState(currentView))
  //         bottomView = currentView;
  //     }

  //     // we could udpate only up to a common ancestor, but full path is simpler
  //     SimulationTreeCircuitNode node;
  //     node = mapToNode(oldBottomView);
  //     while (node != null) {
  //       node.fireAppearanceChanged();
  //       TreeNode parent = node.getParent();
  //       if (parent instanceof SimulationTreeCircuitNode)
  //         node = (SimulationTreeCircuitNode) parent;
  //       else
  //         node = null;
  //     }
  //     node = mapToNode(bottomView);
  //     while (node != null) {
  //       node.fireAppearanceChanged();
  //       TreeNode parent = node.getParent();
  //       if (parent instanceof SimulationTreeCircuitNode)
  //         node = (SimulationTreeCircuitNode) parent;
  //       else
  //         node = null;
  //     }
  //   }
  // }

  public void valueForPathChanged(TreePath path, Object newValue) {
    throw new UnsupportedOperationException();
  }
  
  public interface EventDispatcher {
    void dispatch(TreeModelListener listener, TreeModelEvent e);
  }

  public void fire(TreePath path, int[] indices,
      SimulationTreeNode[] nodes, EventDispatcher call) {
    Object[] list = listeners.getListenerList();
    TreeModelEvent e = null;
    for (int i = list.length - 2; i >= 0; i -= 2) {
      if (list[i] == TreeModelListener.class) {
        if (e == null)
          e = new TreeModelEvent(this, path, indices, nodes);
        call.dispatch((TreeModelListener)list[i+1], e);
      }
    }
  }
}
