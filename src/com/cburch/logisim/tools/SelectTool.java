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

package com.cburch.logisim.tools;
import static com.cburch.logisim.tools.Strings.S;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection.Event;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.key.KeyConfigurationEvent;
import com.cburch.logisim.tools.key.KeyConfigurationResult;
import com.cburch.logisim.tools.key.KeyConfigurator;
import com.cburch.logisim.tools.move.MoveGesture;
import com.cburch.logisim.tools.move.MoveRequestListener;
import com.cburch.logisim.tools.move.MoveResult;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;
import com.cburch.logisim.util.StringGetter;

public final class SelectTool extends Tool {
  private static class ComputingMessage implements StringGetter {
    private int dx;
    private int dy;

    public ComputingMessage(int dx, int dy) {
      this.dx = dx;
      this.dy = dy;
    }

    public String toString() {
      return S.get("moveWorkingMsg");
    }
  }

  private class Listener implements Selection.Listener {
    public void selectionChanged(Event event) {
      keyHandlers = null;
    }
  }

  private static class MoveRequestHandler implements MoveRequestListener {
    private Canvas canvas;

    MoveRequestHandler(Canvas canvas) {
      this.canvas = canvas;
    }

    public void requestSatisfied(MoveGesture gesture, int dx, int dy) {
      clearCanvasMessage(canvas, dx, dy);
    }
  }

  private static void clearCanvasMessage(Canvas canvas, int dx, int dy) {
    Object getter = canvas.getErrorMessage();
    if (getter instanceof ComputingMessage) {
      ComputingMessage msg = (ComputingMessage) getter;
      if (msg.dx == dx && msg.dy == dy) {
        canvas.setErrorMessage(null);
        canvas.repaint();
      }
    }
  }

  private static final int IDLE = 0;
  private static final int MOVING = 1;
  private static final int RECT_SELECT = 2;
  private static final int RESHAPING = 3;
  private static final int MOVING_OR_DROPPING_ONE = 4;
  private static final Cursor cursors[] = {
    Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR),
    Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR),
    Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
    Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
    Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR),
  };

  private static final Icon toolIcon = Icons.getIcon("move.gif");

  private static final Color COLOR_UNMATCHED = new Color(192, 0, 0);

  private static final Color COLOR_COMPUTING = new Color(96, 192, 96);

  private static final Color COLOR_RECT_SELECT = new Color(0, 64, 128, 255);
  private static final Color BACKGROUND_RECT_SELECT = new Color(192, 192, 255, 192);

  private Location start;
  private int state = IDLE;
  private Location forceSnap;
  private int curDx;
  private int curDy;
  private boolean drawConnections;
  private MoveGesture moveGesture;
  private HashMap<Component, KeyConfigurator> keyHandlers;

  private HashSet<Selection> selectionsAdded = new HashSet<>();

  private Listener selListener = new Listener();

  public SelectTool() { }

  @Override
  public boolean isBuiltin() { return true; }

  @Override
  public Tool cloneTool() {
    return new SelectTool();
  }

  // All instances considered equal, so it is unique per toolbar, etc.
  @Override
  public boolean equals(Object other) {
    return other instanceof SelectTool;
  }

  @Override
  public int hashCode() {
    return SelectTool.class.hashCode();
  }

  private void computeDxDy(Project proj, MouseEvent e) {
    forceSnap = null;
    int dx = e.getX() - start.getX();
    int dy = e.getY() - start.getY();
    Location topleft = proj.getSelection().getNominalTopLeftCorner();
    if (topleft != null) {
      dx = Math.max(dx, -topleft.getX());
      dy = Math.max(dy, -topleft.getY());
    }
    Selection sel = proj.getSelection();
    if (sel.shouldSnap()) {
      dx = Canvas.snapXToGrid(dx);
      dy = Canvas.snapYToGrid(dy);
    } else if ((e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0) {
      // pick target component, and force align it to grid
      Collection<Component> in_sel = sel.getComponentsContaining(start);
      // all components within in_sel are non-snapping, so any one is okay to pick
      Component target = highestPriority(start, in_sel, null);
      if (target != null) {
        Location loc = target.getLocation();
        int snappedDestX = Canvas.snapXToGrid(loc.x + dx);
        int snappedDestY = Canvas.snapYToGrid(loc.y + dy);
        dx += snappedDestX - (loc.x + dx);
        dy += snappedDestY - (loc.y + dy);
        forceSnap = loc;
      } else {
        // not reached?
        Debug.printf(1, "dragging non-snapping components with shift, but can't determine a highest priority target");
      }
    }
    // else if (topleft != null && (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0) {
    //   // top left corner force align to grid
    //   int destx = Canvas.snapXToGrid(topleft.getX() + dx);
    //   int desty = Canvas.snapYToGrid(topleft.getY() + dy);
    //   dx += destx - (topleft.getX() + dx);
    //   dy += desty - (topleft.getY() + dy);
    //   forceSnap = topleft;
    // }
    // after snapping, again ensure top left doesn't go off canvas
    if (topleft != null) {
      while (dx < -topleft.getX()) dx += 10;
      while (dy < -topleft.getY()) dy += 10;
    }
    curDx = dx;
    curDy = dy;
  }

  @Override
  public void deselect(Canvas canvas) {
    moveGesture = null;
  }

  public boolean isMoving() {
    return state == MOVING || state == MOVING_OR_DROPPING_ONE;
  }

  public Location getForceSnapPoint() {
    if (state == MOVING && forceSnap != null)
      return forceSnap.translate(curDx, curDy);
    else
      return null;
  }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    Project proj = canvas.getProject();
    int dx = curDx;
    int dy = curDy;
    if (state == MOVING) {
      proj.getSelection().drawGhostsShifted(context, dx, dy);

      MoveGesture gesture = moveGesture;
      if (gesture != null && drawConnections && (dx != 0 || dy != 0)) {
        MoveResult result = gesture.findResult(dx, dy);
        if (result != null) {
          Collection<Wire> wiresToAdd = result.getWiresToAdd();
          Graphics2D g = context.getGraphics();
          GraphicsUtil.switchToWidth(g, 3);
          g.setColor(Color.GRAY);
          for (Wire w : wiresToAdd) {
            Location loc0 = w.getEnd0();
            Location loc1 = w.getEnd1();
            g.drawLine(loc0.getX(), loc0.getY(), loc1.getX(),
                loc1.getY());
          }
          GraphicsUtil.switchToWidth(g, 1);
          g.setColor(COLOR_UNMATCHED);
          for (Location conn : result.getUnconnectedLocations()) {
            int connX = conn.getX();
            int connY = conn.getY();
            g.fillOval(connX - 3, connY - 3, 6, 6);
            g.fillOval(connX + dx - 3, connY + dy - 3, 6, 6);
          }
        }
      }
    } else if (state == RECT_SELECT) {
      Bounds bds = Bounds.create(start.x, start.y, dx, dy);

      Graphics2D gBase = context.getGraphics();
      if (bds.width > 3 && bds.height > 3) {
        gBase.setColor(BACKGROUND_RECT_SELECT);
        gBase.fillRect(bds.x + 1, bds.y + 1, bds.width - 2, bds.height - 2);
      }

      Circuit circ = canvas.getCircuit();
      for (Component c : circ.getAllVisiblyWithin(bds)) {
        Location cloc = c.getLocation();
        Graphics2D gDup = (Graphics2D)gBase.create();
        context.setGraphics(gDup);
        c.getFactory().drawGhost(context, COLOR_RECT_SELECT,
            cloc.getX(), cloc.getY(), c.getAttributeSet());
        context.setGraphics(gBase);
        gDup.dispose();
      }

      gBase.setColor(COLOR_RECT_SELECT);
      GraphicsUtil.switchToWidth(gBase, 2);
      gBase.drawRect(bds.x, bds.y,
          Math.max(0, bds.width-1), Math.max(0, bds.height-1));
    } else if (state == RESHAPING) {
      Component c = canvas.getSelection().getComponents().iterator().next();
      Reshapable handler = canvas.getSelection().getReshapeHandler();
      Graphics2D gBase = context.getGraphics();
      Graphics2D gDup = (Graphics2D)gBase.create();
      context.setGraphics(gDup);
      context.getInstancePainter().setComponent(c);
      handler.drawReshaping(context.getInstancePainter(), start, curDx, curDy);
      context.setGraphics(gBase);
      gDup.dispose();
    }
  }

  @Override
  public AttributeSet getAttributeSet(Canvas canvas) {
    return canvas.getSelection().getAttributeSet();
  }

  @Override
  public Cursor getCursor() {
    return cursors[state];
  }

  @Override
  public String getDescription() {
    return S.get("selectToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("selectTool");
  }

  @Override
  public Set<Component> getHiddenComponents(Canvas canvas) {
    if (state == MOVING) {
      int dx = curDx;
      int dy = curDy;
      if (dx == 0 && dy == 0) {
        return null;
      }

      Set<Component> sel = canvas.getSelection().getComponents();
      MoveGesture gesture = moveGesture;
      if (gesture != null && drawConnections) {
        MoveResult result = gesture.findResult(dx, dy);
        if (result != null) {
          HashSet<Component> ret = new HashSet<Component>(sel);
          ret.addAll(result.getReplacementMap().getRemovals());
          return ret;
        }
      }
      return sel;
    } else if (state == RESHAPING) {
      return canvas.getSelection().getComponents();
    } else {
      return null;
    }
  }

  @Override
  public String getName() {
    return "Select Tool";
  }

  private void handleMoveDrag(Canvas canvas, int dx, int dy, int modsEx) {
    boolean connect = shouldConnect(canvas, modsEx);
    drawConnections = connect;
    if (connect) {
      MoveGesture gesture = moveGesture;
      if (gesture == null) {
        gesture = new MoveGesture(new MoveRequestHandler(canvas),
            canvas.getCircuit(),
            canvas.getSelection().getAnchoredComponents());
        moveGesture = gesture;
      }
      if (dx != 0 || dy != 0) {
        boolean queued = gesture.enqueueRequest(dx, dy);
        if (queued) {
          canvas.setErrorMessage(new ComputingMessage(dx, dy),
              COLOR_COMPUTING);
          // maybe CPU scheduled led the request to be satisfied
          // just before the "if(queued)" statement. In any case, it
          // doesn't hurt to check to ensure the message belongs.
          if (gesture.findResult(dx, dy) != null) {
            clearCanvasMessage(canvas, dx, dy);
          }
        }
      }
    }
    canvas.repaint();
  }

  @Override
  public boolean shouldOmitAllAttributesFromXml(AttributeSet attrs, LogisimVersion ver) {
    // SelectTool has no meaningful attributes to save to xml. All the attributes it has are
    // transient, computed from the current selection.
    return true;
  }

  @Override
  public void keyPressed(Canvas canvas, KeyEvent e) {
    if (state == MOVING && e.getKeyCode() == KeyEvent.VK_SHIFT) {
      handleMoveDrag(canvas, curDx, curDy, e.getModifiersEx());
    } else {
      Direction dir = null;
      switch (e.getKeyCode()) {
        case KeyEvent.VK_BACK_SPACE:
        case KeyEvent.VK_DELETE:
          if (!canvas.getSelection().isEmpty()) {
            Action act = SelectionActions.delete(canvas.getSelection());
            canvas.getProject().doAction(act);
            e.consume();
          }
          break;
        case KeyEvent.VK_INSERT:
          Action act = SelectionActions.duplicate(canvas.getSelection());
          canvas.getProject().doAction(act);
          e.consume();
          break;
        case KeyEvent.VK_UP:
        case KeyEvent.VK_DOWN:
        case KeyEvent.VK_LEFT:
        case KeyEvent.VK_RIGHT:
          if (!attemptReface(canvas, e))
            processKeyEvent(canvas, e, KeyConfigurationEvent.KEY_PRESSED);
          break; 
        default:
          processKeyEvent(canvas, e, KeyConfigurationEvent.KEY_PRESSED);
      }
    }
  }

  @Override
  public void keyReleased(Canvas canvas, KeyEvent e) {
    if (state == MOVING && e.getKeyCode() == KeyEvent.VK_SHIFT) {
      handleMoveDrag(canvas, curDx, curDy, e.getModifiersEx());
    } else {
      processKeyEvent(canvas, e, KeyConfigurationEvent.KEY_RELEASED);
    }
  }

  @Override
  public void keyTyped(Canvas canvas, KeyEvent e) {
    processKeyEvent(canvas, e, KeyConfigurationEvent.KEY_TYPED);
  }

  @Override
  public void mouseDragged(Canvas canvas, MouseEvent e) {
    if (state == MOVING_OR_DROPPING_ONE) {
      Project proj = canvas.getProject();
      setState(proj, MOVING);
    }
    if (state == MOVING) {
      Project proj = canvas.getProject();
      computeDxDy(proj, e);
      handleMoveDrag(canvas, curDx, curDy, e.getModifiersEx());
    } else if (state == RECT_SELECT) {
      Project proj = canvas.getProject();
      curDx = e.getX() - start.getX();
      curDy = e.getY() - start.getY();
      proj.repaintCanvas();
    } else if (state == RESHAPING) {
      // TODO : handle key modifiers, e.g. to maintain aspect ratio?
      curDx = e.getX() - start.getX();
      curDy = e.getY() - start.getY();
      canvas.getProject().repaintCanvas();
    }
  }

  private Component highestPriority(Location loc, Component c1, Component c2) {
    Bounds b1 = c1.getVisibleBounds();
    Bounds b2 = c2.getVisibleBounds();
    int a1 = b1.getWidth() * b1.getHeight();
    int a2 = b2.getWidth() * b2.getHeight();
    if (a1 < a2) return c1;
    else if (a2 < a1) return c2;
    int x1 = b1.getCenterX();
    int y1 = b1.getCenterY();
    int x2 = b2.getCenterX();
    int y2 = b2.getCenterY();
    int d1 = (loc.getX() - x1) * (loc.getX() - x1) + (loc.getY() - y1) * (loc.getY() - y1);
    int d2 = (loc.getX() - x2) * (loc.getX() - x2) + (loc.getY() - y2) * (loc.getY() - y2);
    if (d1 <= d2) return c1;
    else return c2;
  }

  // Pick a single component from comps: the smallest one, with ties broken by
  // distance from loc to center.
  private Component highestPriority(Location loc, Collection<Component> comps, Collection<Component> disqualify) {
      Component target = null;
      for (Component comp : comps) {
        if (disqualify == null || !disqualify.contains(comp)) {
          if (target == null)
            target = comp;
          else
            target = highestPriority(start, target, comp);
        }
      }
      return target;
  }

  @Override
  public void mousePressed(Canvas canvas, MouseEvent e) {
    canvas.requestFocusInWindow();
    Project proj = canvas.getProject();
    Selection sel = proj.getSelection();
    Circuit circuit = canvas.getCircuit();
    start = Location.create(e.getX(), e.getY());
    curDx = 0;
    curDy = 0;
    moveGesture = null;

    // If user click on reshaping handle within selection, start reshaping.
    // Note: this occurs only when selection is a single non-floating component.
    for (Location loc : sel.getReshapeHandles()) {
      int dx = loc.getX() - e.getX();
      int dy = loc.getY() - e.getY();
      if (dx * dx + dy * dy < 36) {
        setState(proj, RESHAPING);
        start = loc;
        Component comp = sel.getComponents().iterator().next();
        proj.repaintCanvas();
        return;
      }
    }

    // If user clicks into the selection, selection is being modified
    Collection<Component> in_sel = sel.getComponentsContaining(start);
    if (!in_sel.isEmpty()) {
      if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0) {
        // Non-shift-click to selected components begins MOVING
        setState(proj, MOVING);
        proj.repaintCanvas();
        return;
      } else {
        // FIXME: No need to compute target, it must exist but is not used here.
        Component target = highestPriority(start, in_sel, null);
        if (target != null) {
          // shift-click to a selected component might begin MOVING, or maybe DROP_ONE
          setState(proj, MOVING_OR_DROPPING_ONE);
          proj.repaintCanvas();
          return;
        } else {
          Debug.printf(1, "SelectTool: click on components within selection, without shift, but couldn't determine highest priority target?");
          // return; // never reached ?
        }
      }
    }
    // FIXME: at this point, in_sel must be empty, right?
    if (!in_sel.isEmpty())
      Debug.printf(1, "SelectTool: in_sel should have been empty at this point.");

    // if the user clicks into a component outside selection, user
    // wants to add/reset selection
    Collection<Component> clicked = circuit.getAllVisiblyContaining(start);
    if (!clicked.isEmpty()) {
      if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0) {
        if (sel.getComponentsContaining(start).isEmpty()) {
          Action act = SelectionActions.dropAll(sel);
          if (act != null) {
            proj.doAction(act);
          }
        }
      }
      // Old behavior: click on stacked components will select all of them.
      // New behavior: sort by size, take only the smallest. A z-order would be
      // nicer, but we don't maintain that.
      // FIXME: in_sel is empty at this point?
      Component target = highestPriority(start, clicked, in_sel);
      if (target != null) {
        sel.add(target);
      } else {
        Debug.printf(1, "SelectTool: click on non-selected components, without shift, but can't determine a highest priority target?");
        // not reached ?
      }
      setState(proj, MOVING);
      proj.repaintCanvas();
      return;
    }

    // The user clicked on the background. This is a rectangular
    // selection (maybe with the shift key down).
    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0) {
      Action act = SelectionActions.dropAll(sel);
      if (act != null)
        proj.doAction(act);
    }
    setState(proj, RECT_SELECT);
    proj.repaintCanvas();
  }

  @Override
  public void mouseReleased(Canvas canvas, MouseEvent e) {
    Project proj = canvas.getProject();
    if (state == MOVING) {
      computeDxDy(proj, e);
      setState(proj, IDLE);
      int dx = curDx;
      int dy = curDy;
      if (dx != 0 || dy != 0) {
        if (!proj.getLogisimFile().contains(canvas.getCircuit())) {
          canvas.setErrorMessage(S.getter("cannotModifyError"));
        } else if (proj.getSelection().hasConflictWhenMoved(dx, dy)) {
          canvas.setErrorMessage(S.getter("exclusiveError"));
        } else if (proj.getSelection().hasOverlapWhenMoved(dx, dy)) {
          canvas.setErrorMessage(S.getter("overlapError"));
        } else {
          boolean connect = shouldConnect(canvas, e.getModifiersEx());
          drawConnections = false;
          ReplacementMap repl;
          if (connect) {
            MoveGesture gesture = moveGesture;
            if (gesture == null) {
              gesture = new MoveGesture(
                  new MoveRequestHandler(canvas),
                  canvas.getCircuit(),
                  canvas.getSelection().getAnchoredComponents());
            }
            canvas.setErrorMessage(new ComputingMessage(dx, dy),
                COLOR_COMPUTING);
            MoveResult result = gesture.forceRequest(dx, dy);
            clearCanvasMessage(canvas, dx, dy);
            repl = result.getReplacementMap();
          } else {
            repl = null;
          }
          Selection sel = proj.getSelection();
          proj.doAction(SelectionActions.translate(sel, dx, dy, repl));
        }
      }
      moveGesture = null;
      proj.repaintCanvas();
    } else if (state == RECT_SELECT) {
      Bounds bds = Bounds.create(start.x, start.y, curDx, curDy);
      Circuit circuit = canvas.getCircuit();
      Selection sel = proj.getSelection();
      Collection<Component> in_sel = sel.getComponentsVisiblyWithin(bds);
      for (Component comp : circuit.getAllVisiblyWithin(bds)) {
        if (!in_sel.contains(comp))
          sel.add(comp);
      }
      Action act = SelectionActions.drop(sel, in_sel);
      if (act != null) {
        proj.doAction(act);
      }
      setState(proj, IDLE);
      proj.repaintCanvas();
    } else if (state == RESHAPING) {
      setState(proj, IDLE);
      int dx = curDx = e.getX() - start.getX();
      int dy = curDy = e.getY() - start.getY();
      if (dx != 0 || dy != 0) {
        Component c = proj.getSelection().getComponents().iterator().next();
        Reshapable handler = proj.getSelection().getReshapeHandler();
        Circuit circuit = canvas.getCircuit();
        handler.doReshapeAction(proj, circuit, c, start, dx, dy);
      }
      proj.repaintCanvas();
    } else if (state == MOVING_OR_DROPPING_ONE) {
      // drop one
      Selection sel = proj.getSelection();
      Collection<Component> in_sel = sel.getComponentsContaining(start);
      if (!in_sel.isEmpty()) {
        Component target = highestPriority(start, in_sel, null);
        if (target != null) {
          Action act = SelectionActions.drop(sel, Collections.singletonList(target));
          if (act != null)
            proj.doAction(act);
        }
      }
    }
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    Graphics2D g = c.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(c.getDestination(), g, x + 2, y + 2);
    } else {
      int[] xp = { x + 5, x + 5, x + 9, x + 12, x + 14, x + 11, x + 16 };
      int[] yp = { y, y + 17, y + 12, y + 18, y + 18, y + 12, y + 12 };
      g.setColor(java.awt.Color.black);
      g.fillPolygon(xp, yp, xp.length);
    }
  }

  private void processKeyEvent(Canvas canvas, KeyEvent e, int type) {
    HashMap<Component, KeyConfigurator> handlers = keyHandlers;
    if (handlers == null) {
      handlers = new HashMap<Component, KeyConfigurator>();
      Selection sel = canvas.getSelection();
      for (Component comp : sel.getComponents()) {
        ComponentFactory factory = comp.getFactory();
        AttributeSet attrs = comp.getAttributeSet();
        Object handler = factory.getFeature(KeyConfigurator.class,
            attrs);
        if (handler != null) {
          KeyConfigurator base = (KeyConfigurator) handler;
          handlers.put(comp, base.clone());
        }
      }
      keyHandlers = handlers;
    }

    if (!handlers.isEmpty()) {
      boolean consume = false;
      ArrayList<KeyConfigurationResult> results = new ArrayList<>();
      for (Map.Entry<Component, KeyConfigurator> entry : handlers.entrySet()) {
        Component comp = entry.getKey();
        KeyConfigurator handler = entry.getValue();
        KeyConfigurationEvent event = new KeyConfigurationEvent(type,
            comp.getAttributeSet(), e, comp);
        KeyConfigurationResult result = handler.keyEventReceived(event);
        consume |= event.isConsumed();
        if (result != null) {
          results.add(result);
        }
      }
      if (consume) {
        e.consume();
      }
      if (!results.isEmpty()) {
        SetAttributeAction act = new SetAttributeAction(
            canvas.getCircuit(),
            S.getter("changeComponentAttributesAction"));
        for (KeyConfigurationResult result : results) {
          Component comp = (Component) result.getEvent().getData();
          Map<Attribute<?>, Object> newValues = result
              .getAttributeValues();
          for (Map.Entry<Attribute<?>, Object> entry : newValues
              .entrySet()) {
            act.set(comp, entry.getKey(), entry.getValue());
          }
        }
        if (!act.isEmpty()) {
          canvas.getProject().doAction(act);
        }
      }
    }
  }

  @Override
  public void select(Canvas canvas) {
    Selection sel = canvas.getSelection();
    if (!selectionsAdded.contains(sel)) {
      sel.addListener(selListener);
    }
  }

  private void setState(Project proj, int new_state) {
    if (state == new_state)
      return; // do nothing if state not new
    forceSnap = null;
    state = new_state;
    proj.getFrame().getCanvas().setCursor(getCursor());
  }

  private boolean shouldConnect(Canvas canvas, int modsEx) {
    boolean shiftReleased = (modsEx & MouseEvent.SHIFT_DOWN_MASK) == 0;
    boolean dflt = AppPreferences.MOVE_KEEP_CONNECT.get();
    if (shiftReleased) {
      return dflt;
    } else {
      return !dflt;
    }
  }

  private Attribute<Direction> getFacingAttribute(Component comp) {
    AttributeSet attrs = comp.getAttributeSet();
    Object key = ComponentFactory.FACING_ATTRIBUTE_KEY;
    Attribute<?> a = (Attribute<?>) comp.getFactory()
        .getFeature(key, attrs);
    @SuppressWarnings("unchecked")
    Attribute<Direction> ret = (Attribute<Direction>) a;
    return ret;
  }

  private boolean attemptReface(Canvas canvas, KeyEvent e) {
    if (e.getModifiersEx() != 0)
      return false;
    final Direction facing = Direction.fromKeyCode(e.getKeyCode());
    if (facing == null)
      return false;
    final Circuit circuit = canvas.getCircuit();
    final Selection sel = canvas.getSelection();
    SetAttributeAction act = new SetAttributeAction(circuit,
        S.getter("selectionRefaceAction"));
    for (Component comp : sel.getComponents()) {
      if (!(comp instanceof Wire)) {
        Attribute<Direction> attr = getFacingAttribute(comp);
        if (attr != null) {
          act.set(comp, attr, facing);
        }
      }
    }
    if (!act.isEmpty()) {
      canvas.getProject().doAction(act);
      e.consume();
      return true;
    }
    return false;
  }

}
