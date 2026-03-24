/* ProjectExplorerFalseRootNode
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

import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.std.mouse.MouseTools;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.CollectionUtil;

public class ProjectExplorerLibraryNode
  extends ProjectExplorerModel.Node<Library> implements LibraryListener {

  private static final long serialVersionUID = 1L;
  private LogisimFile file;

  ProjectExplorerLibraryNode(ProjectExplorerModel model, Library lib, ProjectExplorerModel.Node<?> parent) {
    super(model, lib, parent);
    if (lib instanceof LogisimFile) {
      file = (LogisimFile)lib;
      file.addLibraryWeakListener(null, this);
    }
    for (Tool item : lib.getTools()) {
      children.add(new ProjectExplorerToolNode(model, item, this));
    }
    for (Library item : lib.getLibraries()) {
      if (item instanceof MouseTools)
        continue;
      if (item instanceof Library && ancestorContainsLibrary((Library)item))
        continue; // hide libraries already shown in an ancestor
      children.add(new ProjectExplorerLibraryNode(model, item, this));
    }
  }

  private boolean ancestorContainsLibrary(Library lib) {
    ProjectExplorerModel.Node<?> p = parent;
    while (p != null) {
      Object v = p.value;
      if (v instanceof Library && ((Library)v).getLibraries().contains(lib))
        return true;
      p = p.parent;
    }
    return false;
  }

  @Override
  public void reload() {
    removeAll();
    rebuildAfterAdditionsOrRemovals();
    super.reload();
  }

  private void rebuildAfterLibraryAdditionOrRemovals() {
    rebuildAfterAdditionsOrRemovals();
    for (ProjectExplorerModel.Node<?> child : children)
      if (child instanceof ProjectExplorerLibraryNode)
        ((ProjectExplorerLibraryNode)child).rebuildAfterLibraryAdditionOrRemovals();
  }

  // This is used when children have changed only by:
  //  - possibly adding one or more children
  //  - possibly removing one or more children
  // Note: the order of existing children should not have changed.
  private void rebuildAfterAdditionsOrRemovals() {

    // build updated list of child values
    ArrayList<Object> v = new ArrayList<>();
    v.addAll(value.getTools());
    for (Library item : value.getLibraries()) {
      if (item instanceof MouseTools)
        continue;
      if (item instanceof Library && ancestorContainsLibrary((Library)item))
        continue; // hide libraries already shown in an ancestor
      v.add(item);
    }

    ArrayList<Integer> indices = new ArrayList<>();
    ArrayList<ProjectExplorerModel.Node<?>> affected = new ArrayList<>();

    // remove and decommission existing children as necessary, and notify of removal
    for (int i = children.size()-1; i >= 0; i--) {
      ProjectExplorerModel.Node<?> child = children.get(i);
      if (v.contains(child.value))
        continue;
      children.remove(i);
      child.decommission();
      affected.add(child);
      indices.add(i);
    }
    if (!affected.isEmpty()) {
      fireChildrenRemoved(CollectionUtil.toArray(indices),
          affected.toArray(new ProjectExplorerModel.Node<?>[affected.size()]));
      indices.clear();
      affected.clear();
    }

    // sanity check: all remaining children should be in proper order already
    int previdx = -1;
    boolean bad = false;
    for (ProjectExplorerModel.Node<?> child : children) {
      int idx = v.indexOf(child.value);
      if (idx <= previdx) {
        bad = true;
        break;
      }
      previdx = idx;
    }
    if (bad) {
      System.err.println("Project explorer failure: nodes not in proper order");
      removeAll(); // remove all nodes, start over from scratch
    }

    // create and add new children as necessary, and notify of addition
    // note: existing children are in proper order already
    for (int i = 0; i < v.size(); i++) {
      Object value = v.get(i);
      if (i < children.size() && children.get(i).value == value)
        continue;
      ProjectExplorerModel.Node<?> child;
      if (value instanceof Tool)
        child = new ProjectExplorerToolNode(model, (Tool)value, this);
      else if (value instanceof Library)
        child = new ProjectExplorerLibraryNode(model, (Library)value, this);
      else
        continue; // ?
      children.add(i, child);
      indices.add(i);
      affected.add(child);
    }
    if (!affected.isEmpty()) {
      fireChildrenInserted(CollectionUtil.toArray(indices),
          affected.toArray(new ProjectExplorerModel.Node<?>[affected.size()]));
      indices.clear();
      affected.clear();
    }

  }

  private int getNewIndexOfValue(Object v) {
    int i = -1;
    for (Tool item : value.getTools()) {
      i++;
      if (item == v)
        return i;
    }
    for (Library item : value.getLibraries()) {
      if (item instanceof MouseTools)
        continue;
      if (item instanceof Library && ancestorContainsLibrary((Library)item))
        continue; // hide libraries already shown in an ancestor
      i++;
      if (item == v)
        return i;
    }
    return -1;
  }

  // This is used when children have changed only by:
  //  - moving at most ONE existing child to a different position
  // Note: no children should have been added or removed.
  private void rebuildAfterSingleMove(Object v) {
    int oldIdx = getIndexOfValue(v);
    int newIdx = getNewIndexOfValue(v);
    // sanity check:
    if (oldIdx == -1 || newIdx == -1) {
      System.err.println("Project explorer failure: nodes missing, rebuilding");
      removeAll();
      rebuildAfterAdditionsOrRemovals();
      return;
    }
    if (oldIdx == newIdx)
      return;
    ProjectExplorerModel.Node<?> child = children.remove(oldIdx);
    fireChildrenRemoved(new int[] { oldIdx }, new ProjectExplorerModel.Node<?>[] { child });
    children.add(newIdx, child);
    fireChildrenInserted(new int[] { newIdx }, new ProjectExplorerModel.Node<?>[] { child });
  }

  private void removeAll() { // only used in desperation
    int n = children.size();
    int[] indices = new int[n];
    ProjectExplorerModel.Node<?>[] affected = new ProjectExplorerModel.Node<?>[n];
    for (int i = 0; i < n; i++) {
      ProjectExplorerModel.Node<?> child = children.get(i);
      indices[i] = i;
      affected[i] = child;
      child.decommission();
    }
    children.clear();
    fireChildrenRemoved(indices, affected);
  }


  @Override
  protected void decommission() {
    super.decommission();
    if (file != null)
      file.removeLibraryWeakListener(null, this);
  }

  @Override
  public void libraryChanged(LibraryEvent event) {
    switch (event.getAction()) {
    case LibraryEvent.DIRTY_STATE:
    case LibraryEvent.SET_NAME:
      fireAppearanceChanged();
      break;
    case LibraryEvent.SET_MAIN:
      break;
    case LibraryEvent.ADD_TOOL:
    case LibraryEvent.REMOVE_TOOL:
      rebuildAfterAdditionsOrRemovals();
      break;
    case LibraryEvent.MOVE_TOOL:
    case LibraryEvent.MOVE_LIBRARY:
      rebuildAfterSingleMove(event.getData());
      break;
    case LibraryEvent.ADD_LIBRARY:
    case LibraryEvent.REMOVE_LIBRARY:
      rebuildAfterLibraryAdditionOrRemovals();
      break;
    }
  }

}
