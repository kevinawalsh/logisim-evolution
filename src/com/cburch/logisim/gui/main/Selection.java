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

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.CustomHandles;
import com.cburch.logisim.tools.Reshapable;
import com.cburch.logisim.util.CollectionUtil;

public class Selection {

  public static class Event {
    Object source;

    Event(Object source) {
      this.source = source;
    }

    public Object getSource() {
      return source;
    }
  }

  public static interface Listener {
    public void selectionChanged(Selection.Event event);
  }

  private class MyListener implements ProjectListener, CircuitListener {
    private WeakHashMap<Action, SelectionSave> savedSelections;

    MyListener() {
      savedSelections = new WeakHashMap<Action, SelectionSave>();
    }

    public void circuitChanged(CircuitEvent event) {
      if (event.getAction() == CircuitEvent.TRANSACTION_DONE) {
        Circuit circuit = event.getCircuit();
        ReplacementMap repl = event.getResult().getReplacementMap(circuit);
        boolean change = false;

        ArrayList<Component> oldAnchored;
        oldAnchored = new ArrayList<Component>(getComponents());
        for (Component comp : oldAnchored) {
          Collection<Component> replacedBy = repl.getReplacementsFor(comp);
          if (replacedBy != null) {
            change = true;
            selected.remove(comp);
            lifted.remove(comp);
            for (Component add : replacedBy) {
              if (circuit.contains(add)) {
                selected.add(add);
              } else {
                lifted.add(add);
              }
            }
          }
        }

        if (change) {
          fireSelectionChanged();
        } else {
          computeCanReshape();
        }
      }
    }

    public void projectChanged(ProjectEvent event) {
      int type = event.getAction();
      if (type == ProjectEvent.ACTION_START) {
        SelectionSave save = SelectionSave.create(Selection.this);
        savedSelections.put((Action) event.getData(), save);
      } else if (type == ProjectEvent.ACTION_COMPLETE) {
        SelectionSave save = savedSelections.get(event.getData());
        if (save != null && save.isSame(Selection.this)) {
          savedSelections.remove(event.getData());
        }
      } else if (type == ProjectEvent.ACTION_MERGE) {
        SelectionSave save = savedSelections.get(event.getOldData());
        savedSelections.put((Action) event.getData(), save);
      } else if (type == ProjectEvent.UNDO_COMPLETE) {
        Action act = (Action) event.getData();
        restore(savedSelections.get(act));
      } else if (type == ProjectEvent.REDO_START) {
        Action act = (Action) event.getData();
        restore(savedSelections.get(act));
      }
    }
  }

  private void restore(SelectionSave save) {
    if (save == null)
      return;
    Circuit circ = proj.getCurrentCircuit();
    lifted.clear();
    selected.clear();
    Component cs[][] = {
        save.getFloatingComponents(),
        save.getAnchoredComponents()
    };
    for (int i = 0; i < 2; i++) {
      if (cs[i] != null) {
        for (Component c : cs[i]) {
          if (circ.contains(c))
            selected.add(c);
          else
            lifted.add(c);
        }
      }
    }
    fireSelectionChanged();
  }

  private MyListener myListener;
  private SelectionAttributes attrs;

  static final Set<Component> NO_COMPONENTS = Collections.emptySet();

  Project proj;
  private ArrayList<Selection.Listener> listeners = new ArrayList<>();
  final HashSet<Component> selected = new HashSet<>(); // of selected Components in circuit
  final HashSet<Component> lifted = new HashSet<>(); // of selected Components removed

  final HashSet<Component> suppressHandles = new HashSet<>(); // of wires being edited
  final Set<Component> unionSet = CollectionUtil.createUnmodifiableSetUnion(selected, lifted);
  
  ArrayList<Location> reshapeHandles = new ArrayList<>();
  Reshapable reshapeHandler = null;

  private Location topLeftCorner = null; // corner of unionSet nominal bounding box

  private boolean shouldSnap = false;


  public Selection(Project proj, Canvas canvas) {
    this.proj = proj;
    myListener = new MyListener();
    attrs = new SelectionAttributes(canvas, this);
    proj.addProjectWeakListener(null, myListener);
    proj.addCircuitWeakListener(/*null,*/myListener);
  }

  private static Location computeNominalTopLeftCorner(Collection<Component> components) {
    if (components.isEmpty())
      return Location.create(0, 0);
    Iterator<Component> it = components.iterator();
    Bounds bds = it.next().getNominalBounds();
    int x = bds.getX();
    int y = bds.getY();
    while (it.hasNext()) {
      Component comp = it.next();
      bds = comp.getNominalBounds();
      x = Math.min(x, bds.getX());
      y = Math.min(y, bds.getY());
    }
    return Location.create(x, y);
  }

  private static boolean shouldSnapComponent(Component comp) {
    Boolean shouldSnapValue = (Boolean) comp.getFactory().getFeature(
        ComponentFactory.SHOULD_SNAP, comp.getAttributeSet());
    return shouldSnapValue == null ? true : shouldSnapValue.booleanValue();
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
    topLeftCorner = null;

    fireSelectionChanged();
  }

  private void computeShouldSnap() {
    shouldSnap = false;
    for (Component comp : unionSet) {
      if (shouldSnapComponent(comp)) {
        shouldSnap = true;
        return;
      }
    }
  }

  public Collection<Location> getReshapeHandles() {
    return reshapeHandles;
  }

  public Reshapable getReshapeHandler() {
    return reshapeHandler;
  }

  void computeCanReshape() {
    reshapeHandles.clear();
    reshapeHandler = null;
    // Note: only reshape when selection is a single non-floating component.
    if (selected.size() != 1 || lifted.size() !=  0)
      return;
    Component comp = selected.iterator().next();
    reshapeHandler = (Reshapable)comp.getFeature(Reshapable.class);
    if (reshapeHandler != null)
      reshapeHandles.addAll(reshapeHandler.getReshapeHandles(comp));
  }
  
  public static HashMap<Component, Component> copyComponents(Project proj,
      Circuit circuit, Collection<Component> components) {
    Selection sel = proj.getSelection();
    HashSet<Component> oldLifted = new HashSet<Component>(sel.lifted);
    return copyComponents(circuit, oldLifted, components);
  }

  public static HashMap<Component, Component> copyComponents(Circuit circuit,
      Collection<Component> oldLifted, Collection<Component> components) {
    // determine translation offset where we can legally place the clipboard
    int dx = 0, dy = 0;
    Location topleft = computeNominalTopLeftCorner(components);
    for (int index = 0; index < 64; index++) { // give up after 64 tries
      // compute offset to try: We try points along successively larger
      // squares radiating outward from 0,0
      if (index == 0 || index == 64) {
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
      if (topleft.getX() + dx >= 0 && topleft.getY() + dy >= 0
          && !hasConflictTranslated(circuit, components, dx, dy, true)
          && !hasOverlapTranslated(circuit, components, dx, dy, true)
          && !hasOverlapTranslated(oldLifted, components, dx, dy, true)) {
        break;
      }
    }
    return copyComponents(components, dx, dy);
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

  void duplicateHelper(CircuitMutation xn) {
    HashSet<Component> oldSelected = new HashSet<Component>(selected);
    oldSelected.addAll(lifted);
    pasteHelper(xn, oldSelected);
  }

  public void fireSelectionChanged() {
    topLeftCorner = null;
    computeShouldSnap();
    computeCanReshape();
    Selection.Event e = new Selection.Event(this);
    for (Selection.Listener l : listeners) {
      l.selectionChanged(e);
    }
  }

  public Location getNominalTopLeftCorner() {
    if (topLeftCorner == null && !unionSet.isEmpty())
      topLeftCorner = computeNominalTopLeftCorner(unionSet);
    return topLeftCorner;
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
      Bounds newBounds = comp.getNominalBounds().translate(dx, dy);
      for (Component comp2 : circuit.getAllNominallyContaining(newLoc)) {
        Bounds bds = comp2.getNominalBounds();
        if (bds.equals(newBounds) && (selfConflicts || !components.contains(comp2)))
          return true;
      }
    }
    return false;
  }

  private static boolean hasOverlapTranslated(Collection<Component> existing,
      Collection<Component> components, int dx, int dy, boolean selfConflicts) {
    for (Component comp : components) {
      if ((comp instanceof Wire))
        continue;
      Bounds newBounds = comp.getNominalBounds().translate(dx, dy);
      for (Component comp2 : existing) {
        Bounds bds = comp2.getNominalBounds();
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
    HashSet<Component> oldLifted = new HashSet<Component>(lifted);
    clear(xn);

    Circuit circuit = proj.getCurrentCircuit();
    Map<Component, Component> newLifted = copyComponents(circuit, oldLifted, comps);
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

  public boolean contains(Component comp) {
    return unionSet.contains(comp);
  }

  public void draw(ComponentDrawContext context, Set<Component> hidden) {
    Graphics g = context.getGraphics();

    for (Component c : lifted) {
      if (!hidden.contains(c)) {
        Location loc = c.getLocation();

        Graphics g_new = g.create();
        context.setGraphics(g_new);
        c.getFactory().drawGhost(context, Color.GRAY, loc.getX(),
            loc.getY(), c.getAttributeSet());
        context.setGraphics(null);
        g_new.dispose();
      }
    }

    for (Component comp : unionSet) {
      if (!suppressHandles.contains(comp) && !hidden.contains(comp)) {
        Graphics g_new = g.create();
        context.setGraphics(g_new);
        context.getInstancePainter().setComponent(comp);
        CustomHandles handler = (CustomHandles) comp.getFeature(CustomHandles.class);
        if (handler != null)
          handler.drawHandles(context);
        else
          context.drawHandles(comp);
        context.setGraphics(null);
        g_new.dispose();
      }
    }
    if (!reshapeHandles.isEmpty() && hidden.isEmpty()) {
      Graphics g_new = g.create();
      context.setGraphics(g_new);
      for (Location loc: reshapeHandles) {
        context.drawReshapeHandle(loc);
      }
      context.setGraphics(null);
      g_new.dispose();
    }

    context.setGraphics(g);
  }

  public void drawGhostsShifted(ComponentDrawContext context, int dx, int dy) {
    if (shouldSnap()) {
      dx = Canvas.snapXToGrid(dx);
      dy = Canvas.snapYToGrid(dy);
    }
    Graphics g = context.getGraphics();
    for (Component comp : unionSet) {
      AttributeSet attrs = comp.getAttributeSet();
      Location loc = comp.getLocation();
      int x = loc.getX() + dx;
      int y = loc.getY() + dy;
      Graphics g_new = g.create();
      context.setGraphics(g_new);
      comp.getFactory().drawGhost(context, Color.gray, x, y, attrs);
      context.setGraphics(null);
      g_new.dispose();
    }
    context.setGraphics(g);
  }


  public int hashCode() {
    return this.selected.hashCode()
        + 31 * this.lifted.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Selection))
      return false;
    Selection otherSelection = (Selection) other;
    return this.selected.equals(otherSelection.selected)
        && this.lifted.equals(otherSelection.lifted);
  }

  public Collection<Component> getAnchoredComponents() {
    return selected;
  }

  public AttributeSet getAttributeSet() {
    return attrs;
  }

  public Set<Component> getComponents() {
    return unionSet;
  }

  // public Collection<Component> getComponentsContaining(Location query) {
  //   HashSet<Component> ret = new HashSet<Component>();
  //   for (Component comp : unionSet) {
  //     if (comp.contains(query))
  //       ret.add(comp);
  //   }
  //   return ret;
  // }

  public Collection<Component> getComponentsContaining(Location query) {
    HashSet<Component> ret = new HashSet<Component>();
    for (Component comp : unionSet) {
      // New behavior for 5.0.5-HC: A selected wire has a larger hit-box
      // than usual, because moving small wires is annoying, or
      // close to impossible depending on zoom level.
      if (comp instanceof Wire) {
        if (((Wire)comp).nominallyNearby(query))
          ret.add(comp);
      } else {
        if (comp.visiblyContains(query))
          ret.add(comp);
      }
    }
    return ret;
  }

  // public Collection<Component> getComponentsWithin(Bounds bds) {
  //   HashSet<Component> ret = new HashSet<Component>();
  //   for (Component comp : unionSet) {
  //     if (bds.contains(comp.getBounds()))
  //       ret.add(comp);
  //   }
  //   return ret;
  // }

  public Collection<Component> getComponentsVisiblyWithin(Bounds bds) {
    HashSet<Component> ret = new HashSet<Component>();
    for (Component comp : unionSet) {
      if (bds.contains(comp.getVisibleBounds()))
        ret.add(comp);
    }
    return ret;
  }

  public Collection<Component> getFloatingComponents() {
    return lifted;
  }

  public boolean isEmpty() {
    return selected.isEmpty() && lifted.isEmpty();
  }

}
