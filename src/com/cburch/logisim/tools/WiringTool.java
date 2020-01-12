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
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.generic.Callout;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;

public final class WiringTool extends Tool {
  private static Cursor cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
  private static final Icon toolIcon = Icons.getIcon("wiring.gif");

  private static final int AIMLESS = 0;
  private static final int HORIZONTAL = 1;
  private static final int VERTICAL = 2;

  private static final int REPAIR_RADIUS = 2;

  private boolean pending = false; // wire draing in progress (i.e. mouse is down)
  private boolean inCanvas = false; // mouse is within canvas bounds

  private Set<Component> toRemove = new HashSet<>();
  private LinkedList<Wire> wires = new LinkedList<>();
  private Wire extraWireFromSplitting = null;
  private List<Wire> candidates = new LinkedList<>();
  private boolean splitCandidate = false;
  private Location origin = Location.ORIGIN;
  private Location anchor = Location.ORIGIN;
  private Location cur = Location.ORIGIN;
  private boolean repairOrigin = false;
  private Location unrepairableEnd = null;

  private Wire shortening0 = null, shortening1 = null;
  private int direction = AIMLESS;

  public WiringTool() { }

  @Override
  public boolean isBuiltin() { return true; }

  @Override
  public Tool cloneTool() {
    return new WiringTool();
  }

  // All instances considered equal, so it is unique per toolbar, etc.
  @Override
  public boolean equals(Object other) {
    return other instanceof WiringTool;
  }

  @Override
  public int hashCode() {
    return WiringTool.class.hashCode();
  }

  // Wire w goes from end0 (top or left) to end1 (bottom or right), and end is one of those two points.
  private Wire checkForRepairs(Canvas canvas, Wire w, Location end) {
    if (!canvas.getCircuit().getNonWires(end).isEmpty())
      return w; // don't repair if the wire landed on a pin or some other connection

    // Vertical:
    //    end0
    //     |  <-- candidate if end0 is being repaired
    //     |
    //     |  <-- candidate if end1 is being repaired
    //    end1
    // Horizontal:
    //    end0 - - - end1
    //         ^   ^
    //         |    \ candidate if end1 is being repaired
    //          \ candidate if end0 is being repaired
    //
    int delta = (end.equals(w.getEnd0()) ? 10 : -10);
    Location cand;
    if (w.isVertical())
      cand = Location.create(end.x, end.y + delta);
    else
      cand = Location.create(end.x + delta, end.y);

    for (Component comp : canvas.getCircuit().getNonWires(cand)) {
      if (comp.getBounds().contains(end, REPAIR_RADIUS)) {
        WireRepair repair = (WireRepair) comp.getFeature(WireRepair.class);
        if (repair != null
            && repair.shouldRepairWire(new WireRepairData(w, cand))) {
          w = Wire.create(w.getOtherEnd(end), cand);
          canvas.repaint(end.x - 13, end.y - 13, 26, 26);
          return w;
        }
      }
    }
    return w;
  }

  private void updateDirection() {
    if ((direction == HORIZONTAL && cur.x == anchor.x)
        || (direction == VERTICAL && cur.y == anchor.y))
      direction = AIMLESS;

    if (direction == AIMLESS && cur.x != anchor.x)
        direction = HORIZONTAL;
    else if (direction == AIMLESS && cur.y != anchor.y)
        direction = VERTICAL;
  }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    Graphics g = context.getGraphics();
    if (pending) {
      Location e0 = anchor;
      Location e1 = cur;
      int x0 = e0.x;
      int y0 = e0.y;
      int x1 = e1.x;
      int y1 = e1.y;

      g.setColor(Color.BLACK);
      GraphicsUtil.switchToWidth(g, 3);
      if (extraWireFromSplitting != null) {
        Location a = extraWireFromSplitting.getEnd0();
        Location b = extraWireFromSplitting.getEnd1();
        g.drawLine(a.x, a.y, b.x, b.y);
      }
      for (Wire w : wires) {
        Location a = w.getEnd0();
        Location b = w.getEnd1();
        g.drawLine(a.x, a.y, b.x, b.y);
        if (!a.equals(anchor))
          g.fillOval(a.x-2, a.y-2, 5, 5);
      }
      g.fillOval(anchor.x-2, anchor.y-2, 5, 5);
      if (direction == HORIZONTAL) {
        if (x0 != x1)
          g.drawLine(x0, y0, x1, y0);
        if (y0 != y1)
          g.drawLine(x1, y0, x1, y1);
      } else if (direction == VERTICAL) {
        if (y0 != y1)
          g.drawLine(x0, y0, x0, y1);
        if (x0 != x1)
          g.drawLine(x0, y1, x1, y1);
      }
    } else if (AppPreferences.ADD_SHOW_GHOSTS.get() && inCanvas) {
      g.setColor(Color.GRAY);
      g.fillOval(cur.x - 2, cur.y - 2, 5, 5);
    }
  }

  @Override
  public Cursor getCursor() {
    return cursor;
  }

  @Override
  public String getDescription() {
    return S.get("wiringToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("wiringTool");
  }

  @Override
  public Set<Component> getHiddenComponents(Canvas canvas) {
    return toRemove;
  }

  @Override
  public String getName() {
    return "Wiring Tool";
  }

  private void backup(Canvas canvas) {
    Wire w = wires.removeLast();
    Location m = w.getOtherEnd(anchor);
    Rectangle dirty = new Rectangle();
    dirty.add(m.x, m.y);
    dirty.add(anchor.x, anchor.y);
    dirty.add(cur.x, cur.y);
    dirty.grow(5, 5);
    if (m.x == anchor.x) {
      direction = VERTICAL;
    } else if (m.y == anchor.y) {
      direction = HORIZONTAL;
    } else {
      direction = AIMLESS;
    }
    anchor = m;
    updateDirection();
    canvas.repaint(dirty);
  }

  @Override
  public void keyPressed(Canvas canvas, KeyEvent event) {
    switch (event.getKeyCode()) {
    case KeyEvent.VK_SPACE:
      if (!pending || direction == AIMLESS)
        return;
      hintWorked();
      candidates.clear();
      extraWireFromSplitting = null;
      if (anchor.x == cur.x || anchor.y == cur.y) {
        wires.addLast(Wire.create(anchor, cur));
        anchor = cur;
        direction = 2-direction;
      } else {
        Location m = cornerPoint();
        wires.addLast(Wire.create(anchor, m));
        anchor = m;
        updateDirection();
      }
      break;
    case KeyEvent.VK_DELETE:
    case KeyEvent.VK_BACK_SPACE:
      if (!pending || wires.isEmpty())
        return;
      hintWorked();
      backup(canvas);
      break;
    case KeyEvent.VK_ESCAPE:
      if (pending) {
        reset();
        canvas.getProject().repaintCanvas();
      }
      break;
    }
  }

  @Override
  public void mouseEntered(Canvas canvas, Graphics g, MouseEvent e) {
    inCanvas = true;
    canvas.getProject().repaintCanvas();
  }

  @Override
  public void mouseExited(Canvas canvas, Graphics g, MouseEvent e) {
    inCanvas = false;
    canvas.getProject().repaintCanvas();
  }

  @Override
  public void mouseMoved(Canvas canvas, Graphics g, MouseEvent e) {
    inCanvas = true;
    if (pending) {
      mouseDragged(canvas, g, e);
      return;
    } 
    Canvas.snapToGrid(e);
    int newX = e.getX();
    int newY = e.getY();
    if (!cur.equals(newX, newY)) {
      canvas.repaint(cur.x - 5, cur.y - 5, 10, 10);
      cur = Location.create(newX, newY);
      canvas.repaint(cur.x - 5, cur.y - 5, 10, 10);
    }
  }

  @Override
  public void mousePressed(Canvas canvas, Graphics g, MouseEvent e) {
    if (!canvas.getProject().getLogisimFile().contains(canvas.getCircuit())) {
      pending = false;
      canvas.setErrorMessage(S.getter("cannotModifyError"));
      return;
    }

    if (pending) { // Maybe happens if we missed a mouseReleased event?
      mouseDragged(canvas, g, e);
      return;
    }

    Canvas.snapToGrid(e);
    Location p = Location.create(e.getX(), e.getY());

    wires.clear();
    toRemove.clear();
    candidates.clear();
    extraWireFromSplitting = null;

    List<Wire> touching = canvas.getCircuit().getWiresTouching(p);
    if (touching.isEmpty()) {
      // Case 0: No wires touching this location at all.
      origin = p;
      anchor = p;
      cur = p;
      direction = AIMLESS;
      repairOrigin = true;
      unrepairableEnd = null;
    } else if (touching.get(0).endsAt(p)) {
      // Case 1: One or more wires end at this location.
      if (touching.size() == 1) {
        // Case 1a: One sequence of wires leading to this location.
        List<Wire> seq = canvas.getCircuit().getWireSequenceEndingAt(touching.get(0), p);
        toRemove.addAll(seq);
        int n = seq.size();
        if (n == 1) {
          Wire w = seq.get(0);
          origin = w.getOtherEnd(p);
          anchor = origin;
          cur = p;
          direction = w.isVertical() ? VERTICAL : HORIZONTAL;
          repairOrigin = false;
          unrepairableEnd = repel(w.getOtherEnd(p), p);
        } else {
          Wire w0 = seq.get(0);
          Wire w1 = seq.get(1);
          origin = w0.getOtherEnd(w1);
          Wire w = seq.get(n-1);
          anchor = w.getOtherEnd(p);
          cur = p;
          direction = w.isVertical() ? VERTICAL : HORIZONTAL;
          wires.addAll(seq);
          wires.removeLast();
          repairOrigin = false;
          unrepairableEnd = repel(w.getOtherEnd(p), p);
        }
      } else {
        // Case 1b: Two or more sequences of wires leading to this location.
        candidates.addAll(touching);
        splitCandidate = false;
        origin = p;
        anchor = p;
        cur = p;
        direction = AIMLESS;
        repairOrigin = false;
        unrepairableEnd = null;
      }
    } else {
      // Case 2: One or two wires pass through this location.
      candidates.addAll(touching);
      splitCandidate = true;
      origin = p;
      anchor = p;
      cur = p;
      direction = AIMLESS;
      repairOrigin = false;
      unrepairableEnd = null;
    }

    pending = true;
    canvas.getProject().repaintCanvas();
  }

  private Location repel(Location fixed, Location p) {
    if (fixed.x < p.x)
      return Location.create(p.x+10, p.y);
    else if (fixed.x > p.x)
      return Location.create(p.x-10, p.y);
    else if (fixed.y < p.y)
      return Location.create(p.x, p.y+10);
    else
      return Location.create(p.x, p.y-10);
  }

  @Override
  public void mouseDragged(Canvas canvas, Graphics g, MouseEvent e) {
    if (!pending)
      return;

    Canvas.snapToGrid(e);
    int newX = e.getX();
    int newY = e.getY();
    if (cur.equals(newX, newY))
      return;

    Rectangle dirty = new Rectangle();
    dirty.add(anchor.x, anchor.y);
    dirty.add(cur.x, cur.y);
    dirty.add(newX, newY);
    dirty.grow(3, 3);
    
    cur = Location.create(newX, newY);
    updateDirection();

    if (Math.abs(cur.x - anchor.x) > 40 && Math.abs(cur.y - anchor.y) > 40
        && hintReady())
      showHint(canvas);

    if (wires.isEmpty() && !candidates.isEmpty() && !cur.equals(origin)) {
      for (Wire w : candidates) {
        if (!w.contains(cur))
          continue;
        extraWireFromSplitting = null;
        if (!splitCandidate) {
          // Case 1b, cont: One chosen sequence (out of many) of wires leading to this location.
          List<Wire> seq = canvas.getCircuit().getWireSequenceEndingAt(w, origin);
          toRemove.clear();
          toRemove.addAll(seq);
          int n = seq.size();
          if (n == 1) {
            origin = w.getOtherEnd(origin);
            anchor = origin;
            direction = w.isVertical() ? VERTICAL : HORIZONTAL;
            repairOrigin = false;
            unrepairableEnd = repel(w.getOtherEnd(origin), origin);
          } else {
            Wire w0 = seq.get(0);
            Wire w1 = seq.get(1);
            anchor = w.getOtherEnd(origin);
            unrepairableEnd = repel(anchor, origin);
            origin = w0.getOtherEnd(w1);
            direction = w.isVertical() ? VERTICAL : HORIZONTAL;
            wires.addAll(seq);
            wires.removeLast();
            repairOrigin = false;
          }
        } else {
          // Case 2, cont: Split a wire passing through this location.
          anchor = (cur.x < origin.x || cur.y < origin.y) ? w.getEnd0() : w.getEnd1();
          Location other = w.getOtherEnd(anchor);
          Location splitpt = candidates.size() == 1 ? origin : repel(anchor, origin);
          if (!other.equals(splitpt))
            extraWireFromSplitting = Wire.create(splitpt, other);
          List<Wire> seq = canvas.getCircuit().getWireSequenceEndingAt(w, other);
          toRemove.clear();
          toRemove.addAll(seq);
          int n = seq.size();
          if (n == 1) {
            unrepairableEnd = origin;
            origin = anchor;
            direction = w.isVertical() ? VERTICAL : HORIZONTAL;
            repairOrigin = false;
          } else {
            Wire w0 = seq.get(0);
            Wire w1 = seq.get(1);
            unrepairableEnd = origin;
            origin = w0.getOtherEnd(w1);
            direction = w.isVertical() ? VERTICAL : HORIZONTAL;
            wires.addAll(seq);
            wires.removeLast();
            repairOrigin = false;
          }
        }
        updateDirection();
        candidates.clear();
        canvas.getProject().repaintCanvas();
        break;
      }
    }
    
    if (anchor.equals(cur) && !wires.isEmpty())
      backup(canvas);

    canvas.repaint(dirty);
  }

  @Override
  public void mouseReleased(Canvas canvas, Graphics g, MouseEvent e) {
    hideHint();
    if (!pending)
      return;
    pending = false;

    Canvas.snapToGrid(e);
    int newX = e.getX();
    int newY = e.getY();
    if (!cur.equals(newX, newY)) {
      cur = Location.create(newX, newY);
      updateDirection();
    }

    if (direction == AIMLESS) {
      // no new wire 
    } else if (cur.x == anchor.x || cur.y == anchor.y) {
      Wire w = Wire.create(cur, anchor);
      wires.addLast(w);
    } else {
      Location m = cornerPoint();
      wires.addLast(Wire.create(anchor, m));
      wires.addLast(Wire.create(m, cur));
    }

    repairEnds(canvas);

    ArrayList<Component> removals = new ArrayList<>(toRemove);
    removals.removeAll(wires);
    wires.removeAll(toRemove);

    if (extraWireFromSplitting != null)
      wires.add(extraWireFromSplitting);

    if (wires.isEmpty() && removals.isEmpty())
      return;

    CircuitMutation mutation = new CircuitMutation(canvas.getCircuit());
    if (!wires.isEmpty())
      mutation.addAll(wires);
    if (!removals.isEmpty())
      mutation.removeAll(removals);
    Action act = mutation.toAction(S.getter("wiringAction"));
    canvas.getProject().doAction(act);
  }

  private void repairEnds(Canvas canvas) {
    if (wires.isEmpty()) {
      // no repairs
    } else if (wires.size() == 1) {
      Wire w = wires.removeFirst();
      if (!w.endsAt(origin) || !w.endsAt(cur)) {
        System.err.printf("wire repair error: w=%s origin=%s cur=%s\n", w, origin, cur);
        return;
      }
      if (repairOrigin)
        w = checkForRepairs(canvas, w, origin);
      if (unrepairableEnd == null || !cur.equals(unrepairableEnd))
        w = checkForRepairs(canvas, w, cur);
      if (w.getLength() > 0)
        wires.addFirst(w);
    } else {
      Wire w0 = wires.removeFirst();
      Wire wN = wires.removeLast();
      if (!w0.endsAt(origin) || !wN.endsAt(cur)) {
        System.err.printf("wire repair error: w0=%s wN=%s origin=%s cur=%s\n", w0, wN, origin, cur);
        return;
      }
      if (repairOrigin)
        w0 = checkForRepairs(canvas, w0, origin);
      if (w0.getLength() > 0)
        wires.addFirst(w0);
      if (unrepairableEnd == null || !cur.equals(unrepairableEnd))
        wN = checkForRepairs(canvas, wN, cur);
      if (wN.getLength() > 0)
        wires.addLast(wN);
    }
  }

  private Location cornerPoint() {
    if (direction == HORIZONTAL)
      return Location.create(cur.x, anchor.y);
    else
      return Location.create(anchor.x, cur.y);
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    Graphics g = c.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(c.getDestination(), g, x + 2, y + 2);
    } else {
      g.setColor(java.awt.Color.black);
      g.drawLine(x + 3, y + 13, x + 17, y + 7);
      g.fillOval(x + 1, y + 11, 5, 5);
      g.fillOval(x + 15, y + 5, 5, 5);
    }
  }

  private void reset() {
    pending = false;
    inCanvas = false;
    wires.clear();
    toRemove.clear();
    candidates.clear();
    extraWireFromSplitting = null;
    origin = Location.ORIGIN;
    anchor = Location.ORIGIN;
    cur = Location.ORIGIN;
    direction = AIMLESS;
  }

  void resetClick() {
    pending = false;
  }

  @Override
  public void select(Canvas canvas) {
    reset();
  }

  // Limit hints to once per wiring sequence
  private boolean doneTip = false;

  protected void hideHint() {
    doneTip = false;
    super.hideHint();
  }

  protected boolean hintReady() {
    if (doneTip) // once per wiring sequence
      return false;
    doneTip = true;
    return super.hintReady(AppPreferences.WIRING_TOOL_TIP);
  }

  protected void showHint(Canvas canvas) {
    Location p = cornerPoint();
    tip = new Callout(S.get("wiringTip"));
    if (p.x >= Math.max(cur.x, anchor.x) && p.y >= Math.max(cur.y, anchor.y))
      tip.show(canvas, p.x, p.y, Callout.SE, Callout.DURATION);
    else if (p.x <= Math.min(cur.x, anchor.x) && p.y <= Math.min(cur.y, anchor.y))
      tip.show(canvas, p.x, p.y, Callout.NW, Callout.DURATION);
    else if (p.x >= Math.max(cur.x, anchor.x) && p.y <= Math.min(cur.y, anchor.y))
      tip.show(canvas, p.x, p.y, Callout.NE, Callout.DURATION);
    else if (p.x <= Math.min(cur.x, anchor.x) && p.y >= Math.max(cur.y, anchor.y))
      tip.show(canvas, p.x, p.y, Callout.SW, Callout.DURATION);
    else
      tip = null;
  }


}
