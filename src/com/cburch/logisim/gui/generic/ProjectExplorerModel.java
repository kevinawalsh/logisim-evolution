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

package com.cburch.logisim.gui.generic;

/**
 * Code taken from Cornell's version of Logisim:
 * http://www.cs.cornell.edu/courses/cs3410/2015sp/
 */

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.Debug;

class ProjectExplorerModel implements TreeModel, ProjectListener {

  static abstract class Node<T> {
    protected ProjectExplorerModel model;
    protected Node<?> parent;
    protected T value;
    protected ArrayList<Node<?>> children; // null if children disallowed

    Node(ProjectExplorerModel model, T value, Node<?> parent) {
      this.model = model;
      this.parent = parent;
      this.value = value;
      if (getAllowsChildren())
        children = new ArrayList<Node<?>>();
    }
    
    public boolean getAllowsChildren() { return true; }

    public Node<?> getChildAt(int childIndex) {
      return children.get(childIndex);
    }

    public List<Node<?>> getChildren() {
      return children == null ? new ArrayList<Node<?>>() : children;
    }

    public T getValue() {
      return value;
    }

    public int getChildCount() {
      return children == null ? 0 : children.size();
    }

    public int getIndex(Node<?> node) {
      return children == null ? -1 : children.indexOf(node);
    }

    public int getIndexOfValue(Object value) {
      if (children == null)
        return -1;
      for (int i = 0; i < children.size(); i++)
        if (children.get(i).value == value)
          return i;
      return -1;
    }

    public Node<?> getChildWithValue(Object value) {
      if (children == null)
        return null;
      for (int i = 0; i < children.size(); i++)
        if (children.get(i).value == value)
          return children.get(i);
      return null;
    }

    public boolean isLeaf() {
      return children == null || children.isEmpty();
    }

    // This is called once during init after extensive un-notified changes to
    // underlying libraries. Subclasses should override this to cleanup and
    // refresh all children.
    public void reload() {
      if (children != null)
        for (Node<?> child : children)
          child.reload();
    }

    // This is called when a node is permanently removed from the tree.
    // Subclasses should override this to clean up any listeners, state, etc.
    protected void decommission() {
      if (children != null)
        for (Node<?> node : children)
          node.decommission();
    }

    // Subclasses should call this when this node's appearance (icon, label,
    // etc.) has changed. It will fire the appropriate TreeModelEvent.
    public void fireAppearanceChanged() {
      if (parent == null) {
        model.fire(model.getPath(this), new int[0], null,
            (l,e) -> l.treeNodesChanged(e));
      } else {
        int[] indices = new int[] { parent.getIndex(this) };
        Node<?>[] nodes = new Node<?>[] { this };
        model.fire(model.getPath(parent), indices, nodes,
            (l,e) -> l.treeNodesChanged(e));
      }
    }

    // Subclasses should call this when this when the children array has gained
    // elements. It will fire the appropriate TreeModelEvent. The indices are
    // the new positions, in ascending order, where the nodes appear in the
    // children array. The nodes are the Node<T> objects (not T objects) that
    // represent each of the new children.
    void fireChildrenInserted(int[] indices, Node<?>[] nodes) {
      model.fire(model.getPath(this), indices, nodes,
          (l,e) -> l.treeNodesInserted(e));
    } 

    // Subclasses should call this when this when the children array has lost
    // elements. It will fire the appropriate TreeModelEvent. The indices are
    // the old positions, in ascending order, where the nodes were in the
    // children array. The nodes are the Node<T> objects (not T object) that
    // represent each of the removed children.
    void fireChildrenRemoved(int[] indices, Node<?>[] nodes) {
      model.fire(model.getPath(this), indices, nodes,
          (l,e) -> l.treeNodesRemoved(e));
    } 

  }

  private EventListenerList listeners = new EventListenerList();
  private Project proj;
  private boolean showAll;
  Node<?> root;

  ProjectExplorerModel(Project proj, boolean showAll) {
    this.proj = proj;
    this.showAll = showAll;
    if (showAll)
      root = new ProjectExplorerRootNode(this, proj.getLogisimFile());
    else
      root = new ProjectExplorerLibraryNode(this, proj.getLogisimFile(), null);
    proj.addProjectWeakListener(null, this);
  }

  public void addTreeModelListener(TreeModelListener l) {
    listeners.add(TreeModelListener.class, l);
  }

  public void removeTreeModelListener(TreeModelListener l) {
    listeners.remove(TreeModelListener.class, l);
  }

  public Object getChild(Object parent, int index) {
    return ((Node<?>)parent).getChildAt(index);
  }

  public int getChildCount(Object parent) {
    return ((Node<?>)parent).getChildCount();
  }

  public int getIndexOfChild(Object parent, Object child) {
    if (parent == null || child == null)
      return -1;
    return ((Node<?>)parent).getIndex((Node<?>)child);
  }

  public Object getRoot() {
    return root;
  }

  public Node<?> getRootNode() {
    return root;
  }

  public boolean isLeaf(Object node) {
    return ((Node<?>)node).isLeaf();
  }

  public void valueForPathChanged(TreePath path, Object newValue) {
    try { throw new Exception("not implemented"); }
    catch (Exception e) { Debug.error(e); }
  }

  // void fireStructureChanged() {
  //   final ProjectExplorerModel model = this;
  //   final Node<?> root = (Node<?>) getRoot();
  //   SwingUtilities.invokeLater(new Runnable() {
  //     @Override
  //     public void run() {
  //       if (root != null) {
  //         model.fireTreeNodesChanged(model, root.getUserObjectPath(), null, null);
  //         model.fireTreeStructureChanged(model, root.getUserObjectPath(), null, null);
  //       } else {
  //         model.fireTreeNodesChanged(model, null, null, null);
  //         model.fireTreeStructureChanged(model, null, null, null);
  //       }
  //     }
  //   });
  // }
  
  public void updateStructure() { // happens once during init, and during locale changes
    if (root != null) {
      root.reload();
      fire(new TreePath(root), new int[0], null,
          (l,e) -> l.treeStructureChanged(e));
    }
  }

  @Override
  public void projectChanged(ProjectEvent event) {
    int act = event.getAction();
    if (act == ProjectEvent.ACTION_SET_FILE) {
      setLogisimFile(proj.getLogisimFile());
    } else if (act == ProjectEvent.ACTION_SET_CURRENT || act == ProjectEvent.ACTION_SET_TOOL) {
      Node<?> node;
      node = findObject(root, event.getOldData()); // always a Tool
      if (node != null)
        node.fireAppearanceChanged();
      node = findObject(root, event.getData()); // always a Tool
      if (node != null)
        node.fireAppearanceChanged();
    }
  }

  private static Node<?> findObject(Node<?> node, Object value) {
    if (node == null)
      return null;
    if (node.value == value)
      return node;
    if (node.children == null)
      return null;
    int i = node.getIndexOfValue(value);
    if (i >= 0)
      return node.children.get(i);
    for (Node<?> child : node.children) {
      Node<?> match = findObject(child, value);
      if (match != null)
        return match;
    }
    return null;
  }

  public Node<?> findObject(List<?> candidatePath, Object obj) {
    for (Object o : candidatePath)
    if (root == null || root.value == obj)
      return root;
    Node<?> node = root;
    Node<?> found = node.getChildWithValue(obj);
    if (found != null)
      return found;
    for (Object o : candidatePath) {
      if (node.value == o)
        continue;
      node = node.getChildWithValue(o);
      if (node == null)
        return null;
      found = node.getChildWithValue(obj);
      if (found != null)
        return found;
    }
    return null;
  }

  private void setLogisimFile(LogisimFile file) {
    if (root != null)
      root.decommission();

    if (file == null)
      root = null;
    else if (showAll)
      root = new ProjectExplorerRootNode(this, proj.getLogisimFile());
    else
      root = new ProjectExplorerLibraryNode(this, proj.getLogisimFile(), null);

    fire(root == null ? null : new TreePath(root), new int[0], null,
        (l,e) -> l.treeStructureChanged(e));
  }

  public void setProject(Project value) {
    if (proj == value)
      return;

    if (proj != null)
      proj.removeProjectWeakListener(null, this);

    proj = value;

    if (proj != null)
      proj.addProjectWeakListener(null, this);

    setLogisimFile(proj == null ? null : proj.getLogisimFile());
  }

  public TreePath getPath(Node<?> node) {
    Node<?>[] path = getPath(node, 0);
    return path == null ? null : new TreePath(path);
  }

  private Node<?>[] getPath(Node<?> node, int depth) {
    if (node == null && depth == 0)
      return null;
    if (node == null)
      return new Node<?>[depth];
    Node<?>[] path = getPath(node == root ? null : node.parent, depth+1);
    path[path.length - depth - 1] = node;
    return path;
  }

  // public TreePath getPath(Node<?> node) {
  //   if (node == null)
  //     return null;
  //   else if (node == root || node.parent == null)
  //     return new TreePath(node);
  //   else
  //     return new TreePath(getPath(node.parent), node);
  // }

  private interface EventDispatcher {
    void dispatch(TreeModelListener listener, TreeModelEvent e);
  }

  private void fire(TreePath path, int[] indices, Node<?>[] nodes, EventDispatcher call) {
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
