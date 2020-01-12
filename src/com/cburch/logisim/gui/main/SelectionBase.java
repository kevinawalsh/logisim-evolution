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

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.CollectionUtil;

class SelectionBase {

  private static Bounds computeBounds(Collection<Component> components) {
    if (components.isEmpty()) {
      return Bounds.EMPTY_BOUNDS;
    } else {
      Iterator<Component> it = components.iterator();
      Bounds ret = it.next().getBounds();
      while (it.hasNext()) {
        Component comp = it.next();
        Bounds bds = comp.getBounds();
        ret = ret.add(bds);
      }
      return ret;
    }
  }

  private static boolean shouldSnapComponent(Component comp) {
    Boolean shouldSnapValue = (Boolean) comp.getFactory().getFeature(
        ComponentFactory.SHOULD_SNAP, comp.getAttributeSet());
    return shouldSnapValue == null ? true : shouldSnapValue.booleanValue();
  }

  static final Set<Component> NO_COMPONENTS = Collections.emptySet();

  Project proj;
  private ArrayList<Selection.Listener> listeners = new ArrayList<Selection.Listener>();
  final HashSet<Component> selected = new HashSet<Component>(); // of selected Components in circuit
  final HashSet<Component> lifted = new HashSet<Component>(); // of selected Components removed

  final HashSet<Component> suppressHandles = new HashSet<Component>(); // of Components
  final Set<Component> unionSet = CollectionUtil.createUnmodifiableSetUnion(
      selected, lifted);

  private Bounds bounds = Bounds.EMPTY_BOUNDS;

  private boolean shouldSnap = false;

  public SelectionBase(Project proj) {
    this.proj = proj;
  }

  public Project getProject() {
    return proj;
  }

  public void add(Component comp) {
    if (selected.add(comp))
      fireSelectionChanged();
  }

  public void addAll(Collection<? extends Component> comps) {
    if (selected.addAll(comps))
      fireSelectionChanged();
  }

  public void addListener(Selection.Listener l) {
    listeners.add(l);
  }

  void clear(CircuitMutation xn) {
    clear(xn, true);
  }

  // removes all from selection - NOT from circuit
  void clear(CircuitMutation xn, boolean dropLifted) {
    if (selected.isEmpty() && lifted.isEmpty())
      return;

    if (dropLifted && !lifted.isEmpty())
      xn.addAll(lifted);

    selected.clear();
    lifted.clear();
    shouldSnap = false;
    bounds = Bounds.EMPTY_BOUNDS;

    fireSelectionChanged();
  }

  //
  // private methods
  //
  private void computeShouldSnap() {
    shouldSnap = false;
    for (Component comp : unionSet) {
      if (shouldSnapComponent(comp)) {
        shouldSnap = true;
        return;
      }
    }
  }

  public static HashMap<Component, Component> copyComponents(Circuit circuit,
      Collection<Component> components) {
    // determine translation offset where we can legally place the clipboard
    int dx, dy;
    Bounds bds = computeBounds(components);
    for (int index = 0;; index++) {
      // compute offset to try: We try points along successively larger
      // squares radiating outward from 0,0
      if (index == 0) {
        dx = 0;
        dy = 0;
      } else {
        int side = 1;
        while (side * side <= index)
          side += 2;
        int offs = index - (side - 2) * (side - 2);
        dx = side / 2;
        dy = side / 2;
        if (offs < side - 1) { // top edge of square
          dx -= offs;
        } else if (offs < 2 * (side - 1)) { // left edge
          offs -= side - 1;
          dx = -dx;
          dy -= offs;
        } else if (offs < 3 * (side - 1)) { // right edge
          offs -= 2 * (side - 1);
          dx = -dx + offs;
          dy = -dy;
        } else {
          offs -= 3 * (side - 1);
          dy = -dy + offs;
        }
        dx *= 10;
        dy *= 10;
      }

      if (bds.getX() + dx >= 0 && bds.getY() + dy >= 0
          && !hasConflictTranslated(circuit, components, dx, dy, true)
          && !hasOverlapTranslated(circuit, components, dx, dy, true)) {
        return copyComponents(components, dx, dy);
      }
    }
  }

  private static HashMap<Component, Component> copyComponents(
      Collection<Component> components, int dx, int dy) {
    HashMap<Component, Component> ret = new HashMap<Component, Component>();
    for (Component comp : components) {
      Location oldLoc = comp.getLocation();
      AttributeSet attrs = (AttributeSet) comp.getAttributeSet().clone();
      int newX = oldLoc.getX() + dx;
      int newY = oldLoc.getY() + dy;
      Object snap = comp.getFactory().getFeature(
          ComponentFactory.SHOULD_SNAP, attrs);
      if (snap == null || ((Boolean) snap).booleanValue()) {
        newX = Canvas.snapXToGrid(newX);
        newY = Canvas.snapYToGrid(newY);
      }
      Location newLoc = Location.create(newX, newY);

      Component copy = comp.getFactory().createComponent(newLoc, attrs);
      ret.put(comp, copy);
    }
    return ret;
  }

  void deleteAllHelper(CircuitMutation xn) {
    for (Component comp : selected)
      xn.remove(comp);
    selected.clear();
    lifted.clear();
    fireSelectionChanged();
  }

  void anchorAll(CircuitMutation xn) {
    if (!lifted.isEmpty()) {
      xn.addAll(lifted);
      selected.addAll(lifted);
      lifted.clear();
    }
  }

  void duplicateHelper(CircuitMutation xn) {
    HashSet<Component> oldSelected = new HashSet<Component>(selected);
    oldSelected.addAll(lifted);
    pasteHelper(xn, oldSelected);
  }

  public void fireSelectionChanged() {
    bounds = null;
    computeShouldSnap();
    Selection.Event e = new Selection.Event(this);
    for (Selection.Listener l : listeners) {
      l.selectionChanged(e);
    }
  }

  //
  // query methods
  //
  public Bounds getBounds() {
    if (bounds == null) {
      bounds = computeBounds(unionSet);
    }
    return bounds;
  }

  public Bounds getBounds(Graphics g) {
    Iterator<Component> it = unionSet.iterator();
    if (it.hasNext()) {
      bounds = it.next().getBounds(g);
      while (it.hasNext()) {
        Component comp = it.next();
        Bounds bds = comp.getBounds(g);
        bounds = bounds.add(bds);
      }
    } else {
      bounds = Bounds.EMPTY_BOUNDS;
    }
    return bounds;
  }

  private static boolean hasConflictTranslated(Circuit circuit,
      Collection<Component> components, int dx, int dy, boolean selfConflicts) {
    if (circuit == null)
      return false;
    for (Component comp : components) {
      if ((comp instanceof Wire))
        continue;
      for (EndData endData : comp.getEnds()) {
        if (endData != null && endData.isExclusive()) {
          Location endLoc = endData.getLocation().translate(dx, dy);
          Component conflict = circuit.getExclusive(endLoc);
          if (conflict != null && (selfConflicts || !components.contains(conflict)))
            return true;
        }
      }
    }
    return false;
  }

  private static boolean hasOverlapTranslated(Circuit circuit,
      Collection<Component> components, int dx, int dy, boolean selfConflicts) {
    if (circuit == null)
      return false;
    for (Component comp : components) {
      if ((comp instanceof Wire))
        continue;
      Location newLoc = comp.getLocation().translate(dx, dy);
      Bounds newBounds = comp.getBounds().translate(dx, dy);
      for (Component comp2 : circuit.getAllContaining(newLoc)) {
        Bounds bds = comp2.getBounds();
        if (bds.equals(newBounds) && (selfConflicts || !components.contains(comp2)))
          return true;
      }
    }
    return false;
  }

  public boolean hasConflictWhenMoved(int dx, int dy) {
    Circuit circuit = proj.getCurrentCircuit();
    return hasConflictTranslated(circuit, unionSet, dx, dy, false);
  }

  public boolean hasOverlapWhenMoved(int dx, int dy) {
    Circuit circuit = proj.getCurrentCircuit();
    return hasOverlapTranslated(circuit, unionSet, dx, dy, false);
  }

  void pasteHelper(CircuitMutation xn, Collection<Component> comps) {
    clear(xn);

    Circuit circuit = proj.getCurrentCircuit();
    Map<Component, Component> newLifted = copyComponents(circuit, comps);
    lifted.addAll(newLifted.values());
    fireSelectionChanged();
  }

  // debugging methods
  public void print() {
    System.out.printf(" shouldSnap: %s\n", shouldSnap());

    boolean hasPrinted = false;
    for (Component comp : selected) {
      if (hasPrinted)
        System.out.printf("       : %s  [%s]\n", comp, comp.hashCode());
      else
        System.out.printf(" select: %s  [%s]\n", comp, comp.hashCode());
      hasPrinted = true;
    }

    hasPrinted = false;
    for (Component comp : lifted) {
      if (hasPrinted)
        System.out.printf("       : %s  [%s]\n", comp, comp.hashCode());
      else
        System.out.printf(" lifted: %s  [%s]\n", comp, comp.hashCode());
      hasPrinted = true;
    }
  }

  // removes from selection - NOT from circuit
  void remove(CircuitMutation xn, Component comp) {
    boolean removed = selected.remove(comp);
    if (lifted.contains(comp)) {
      if (xn == null) {
        throw new IllegalStateException("cannot remove");
      } else {
        lifted.remove(comp);
        removed = true;
        xn.add(comp);
      }
    }

    if (removed) {
      if (shouldSnapComponent(comp))
        computeShouldSnap();
      fireSelectionChanged();
    }
  }

  public void removeListener(Selection.Listener l) {
    listeners.remove(l);
  }

  public void setSuppressHandles(Collection<Component> toSuppress) {
    suppressHandles.clear();
    if (toSuppress != null)
      suppressHandles.addAll(toSuppress);
  }

  public boolean shouldSnap() {
    return shouldSnap;
  }

  void translateHelper(CircuitMutation xn, int dx, int dy) {
    Map<Component, Component> selectedAfter = copyComponents(selected, dx, dy);
    for (Map.Entry<Component, Component> entry : selectedAfter.entrySet()) {
      xn.replace(entry.getKey(), entry.getValue());
    }

    Map<Component, Component> liftedAfter = copyComponents(lifted, dx, dy);
    lifted.clear();
    for (Map.Entry<Component, Component> entry : liftedAfter.entrySet()) {
      xn.add(entry.getValue());
      selected.add(entry.getValue());
    }
    fireSelectionChanged();
  }

}
