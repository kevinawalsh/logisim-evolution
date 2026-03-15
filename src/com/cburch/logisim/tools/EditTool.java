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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.swing.Icon;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.Selection.Event;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;

public final class EditTool extends Tool {
  private class Listener implements CircuitListener, Selection.Listener {
    public void circuitChanged(CircuitEvent event) {
      if (event.getAction() != CircuitEvent.ACTION_INVALIDATE) {
        lastX = -1;
        cache.clear();
        updateLocation(lastCanvas, lastRawX, lastRawY, lastMods);
      }
    }

    public void selectionChanged(Event event) {
      lastX = -1;
      cache.clear();
      updateLocation(lastCanvas, lastRawX, lastRawY, lastMods);
    }
  }

  private static final int CACHE_MAX_SIZE = 32;

  private static final Location NULL_LOCATION = Location.create(
      Integer.MIN_VALUE, Integer.MIN_VALUE);

  private Listener listener;
  private SelectTool select;
  private WiringTool wiring;
  private ComponentSearch search;
  private Tool current;
  private LinkedHashMap<Location, Boolean> cache;
  private Canvas lastCanvas;
  private int lastRawX;
  private int lastRawY;
  private int lastX; // last coordinates where wiring was computed
  private int lastY;
  private int lastMods; // last modifiers for mouse event
  private Location wireLoc; // coordinates where to draw wiring indicator, if
  private int pressX; // last coordinate where mouse was pressed
  private int pressY; // (used to determine when a short wire has been clicked)

  public EditTool(SelectTool select, WiringTool wiring) {
    this.listener = new Listener();
    this.select = select;
    this.wiring = wiring;
    this.search = new ComponentSearch();
    this.current = select;
    this.cache = new LinkedHashMap<Location, Boolean>();
    this.lastX = -1;
    this.wireLoc = NULL_LOCATION;
    this.pressX = -1;
  }

  @Override
  public Tool cloneTool() {
    return new EditTool((SelectTool)select.cloneTool(), (WiringTool)wiring.cloneTool());
  }

  // All instances considered equal, so it is unique per toolbar, etc.
  @Override
  public boolean equals(Object other) {
    return other instanceof EditTool;
  }

  @Override
  public int hashCode() {
    return EditTool.class.hashCode();
  }

  @Override
  public void deselect(Canvas canvas) {
    if (search.isActive())
      search.cancelSearch();
    current = select;
    canvas.getSelection().setSuppressHandles(null);
    cache.clear();
    canvas.getCircuit().removeCircuitWeakListener(null, listener);
    canvas.getSelection().removeListener(listener);
  }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    Location loc = current == select && select.isMoving() ?
      select.getForceSnapPoint() : wireLoc;
    if (loc != NULL_LOCATION && loc != null && current != wiring) {
      int x = loc.getX();
      int y = loc.getY();
      Graphics2D g = context.getGraphics();
      g.setColor(Color.MAGENTA);
      GraphicsUtil.switchToWidth(g, 2);
      g.drawOval(x - 5, y - 5, 10, 10);
      g.setColor(Color.BLACK);
      GraphicsUtil.switchToWidth(g, 1);
    }
    current.draw(canvas, context);
  }

  @Override
  public AttributeSet getAttributeSet() {
    return select.getAttributeSet();
  }

  @Override
  public AttributeSet getAttributeSet(Canvas canvas) {
    return canvas.getSelection().getAttributeSet();
  }

  @Override
  public Cursor getCursor() {
    return select.getCursor();
  }

  @Override
  public String getDescription() {
    return S.get("editToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("editTool");
  }

  @Override
  public Set<Component> getHiddenComponents(Canvas canvas) {
    return current.getHiddenComponents(canvas);
  }

  @Override
  public String getName() {
    return "Edit Tool";
  }

  @Override
  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver) {
    return true;
  }

  private boolean isClick(MouseEvent e) {
    int px = pressX;
    if (px < 0) {
      return false;
    } else {
      int dx = e.getX() - px;
      int dy = e.getY() - pressY;
      if (dx * dx + dy * dy <= 4) {
        return true;
      } else {
        pressX = -1;
        return false;
      }
    }
  }

  private boolean isWiringPoint(Canvas canvas, Location loc, int modsEx) {
    boolean WIRING = true, SELECT = false;
    // Old behavior before 5.0.5-HC:
    //   Hover without ALT --> 
    //     Hover over selected wire ... SELECT point (e.g. drag-to-move action).
    //     Hover over port (with no selected wire attached; selected or non-selected component)... WIRING point.
    //                     [so splitters are hard to move, and selecting them doesn't help]
    //                     [so for a component port with attached wire, behavior depends on
    //                     whether the wire is selected, but not whether the component is
    //                     selected... a little confusing]
    //     Hover over wire (non-selected, over a port or not)... WIRING point (i.e. extend wire).
    //     Hover over non-wire (selected or not, but not over a port)... SELECT point (e.g. drag-to-move).
    //     Hover over blank area... SELECT point (e.g. rect-select).
    //   Hover with ALT -->
    //     Hover over selected wire... WIRING point.
    //                     [doesn't seem useful... why select wire if not moving it?]
    //     Hover over port (with no selected wire attached)... SELECT point.
    //                     [useful for moving splitter? but nobody discovers this]
    //     Hover over wire (non-selected, not over a port)... SELECT point.
    //                     [doesn't seem useful.... clicking a wire without ALT can also still select it]
    //     Hover over non-wire (selected or not, but not over a port)... WIRING point.
    //                     [anti-useful.... this makes wires on top of components]
    //     Hover over blank area... WIRING point (i.e. start new wire).
    //                       [seems useful]
    // Note:
    //  - Above behavior is likely not clear in the interface at all, and likely not discoverable.
    //  - ALT means "exact opposite" here, which is logically nice, but even from this view ALT should
    //    probably mean "do the opposite when choosing between two reasonable alternatives", e.g.
    //    when clicking a blank area we could rect-select or we could start a wire.
    //
    // New behavior for 5.0.5-HC:
    //   Hover without ALT -->
    //     Hover over *anything selected* ... SELECT point (e.g. drag-to-move action).
    //     Hover over port (of non-selected component, with no selected wire attached) ...WIRING point.
    //     Hover over wire (non-selected) ... WIRING point.
    //     Hover over non-wire (non-selected) ... SELECT point (e.g. drag-to-move action).
    //     Hover over blank area... SELECT point (e.g. rect-select).
    //   Hover with ALT -->
    //     Hover over *anything selected* ... SELECT point (e.g. a new drag-to-copy action).
    //     Hover over port (of non-selected component, with no selected wire attached) ...WIRING point.
    //        FIXME... change this to SELECT
    //     Hover over wire (non-selected) ... SELECT point [??? probably drops all, starts to selection?]
    //     Hover over non-wire (non-selected) ... SELECT point [??? probably drops all, starts to selection?]
    //     Hover over blank area ... WIRING point (i.e. start new wire).
    // In other words, the only thing ALT affects is here is...
    //   - How select tool itself behaves (normal --> move existing components, alt --> create copy and move)
    //   - What happens when clicking over a wire (normal --> draw wires, alt --> select wire)
    //   - What happens when clicking blank area (normal --> rect-select, alt --> start new wire)
    boolean alt = (modsEx & MouseEvent.ALT_DOWN_MASK) != 0;

    // Remaining TODO:
    // - Change hover over non-selected port with ALT ... SELECT point
    // - In SelectTool, if press-drag-release a component, should drop it immediately,
    //   rather than keeping it selected.
    // - In SelectTool, if press-drag then release with ALT, create a copy instead of moving.

    // Hover over anything selected --> SELECT
    if (canvas != null && canvas.getSelection() != null) {
      Collection<Component> sel = canvas.getSelection().getComponents();
      if (sel != null) {
        for (Component c : sel) {
          if (c instanceof Wire) {
            Wire w = (Wire) c;
            // New behavior for 5.0.5-HC: if clicking on the end of seelcted
            // wire is considered a selection action, not a wiring action. Also,
            // extra margin is added, because moving small wires is annoying, or
            // close to impossible depending on zoom level.
            if (w.nominallyNearby(loc))
              return SELECT; // hover over selected wire --> always selection
          } else {
            if (c.nominallyContains(loc)) {
              return SELECT; // hover over selected component --> always selection
            }
          }
        }
      }
    }

    // Not over a selected component. Might be...
    //  - over a port
    //  - over a wire
    //  - over a non-wire component
    //  - over a blank area

    // Hover over a port --> WIRING
    Circuit circ = canvas.getCircuit();
    Collection<? extends Component> at = circ.getComponentsByPortLocation(loc);
    // System.out.println("  at = " + (at == null ? "null" : ""+at.size()));
    if (at != null && at.size() > 0)
      return WIRING;

    // Hover over a wire --> SELECT if alt, WIRING otherwise
    for (Wire w : circ.getWires()) {
      if (w.nominallyContains(loc)) {
        return alt ? SELECT : WIRING;
      }
    }

    // Hover over a non-wire --> SELECT
    // NOTE: slight discrepancy, we use nominal bounds here, but select
    // uses visble bounds.
    Collection<Component> clicked = circ.getAllNominallyContaining(loc);
    if (!clicked.isEmpty())
      return SELECT;

    // Over a blank area --> WIRING if alt, SELECT otherwise
    return alt ? WIRING : SELECT;
  }

  @Override
  public void keyPressed(Canvas canvas, KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ALT) {
      updateLocation(canvas, e);
      e.consume();
      return;
    }
    if (current == wiring)
      wiring.keyPressed(canvas, e);
    else if (search.isActive())
      search.keyPressed(canvas, e);
    else
      select.keyPressed(canvas, e);
  }

  @Override
  public void keyReleased(Canvas canvas, KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ALT) {
      updateLocation(canvas, e);
      e.consume();
      return;
    }
    if (current == wiring)
      wiring.keyReleased(canvas, e);
    else if (search.isActive())
      search.keyReleased(canvas, e);
    else
      select.keyReleased(canvas, e);
  }

  @Override
  public void keyTyped(Canvas canvas, KeyEvent e) {
    if (current == wiring) {
      wiring.keyTyped(canvas, e);
      return;
    }
    if (search.isActive()) {
      search.keyTyped(canvas, e);
      return;
    }
    char c = e.getKeyChar();
    // Start search on any letter when nothing is selected and not wiring
    if (current != wiring && canvas.getSelection().isEmpty()
        && Character.isLetter(c) && e.getModifiersEx() == 0) {
      search.beginSearch(canvas, String.valueOf(c), lastRawX, lastRawY);
      e.consume();
      return;
    }
    select.keyTyped(canvas, e);
  }

  @Override
  public void mouseDragged(Canvas canvas, MouseEvent e) {
    isClick(e);
    current.mouseDragged(canvas, e);
  }

  @Override
  public void mouseEntered(Canvas canvas, MouseEvent e) {
    pressX = -1;
    current.mouseEntered(canvas, e);
  }

  @Override
  public void mouseExited(Canvas canvas, MouseEvent e) {
    pressX = -1;
    current.mouseExited(canvas, e);
  }

  @Override
  public void mouseMoved(Canvas canvas, MouseEvent e) {
    updateLocation(canvas, e);
    select.mouseMoved(canvas, e);
  }

  @Override
  public void mousePressed(Canvas canvas, MouseEvent e) {
    if (search.isActive())
      search.cancelSearch();
    canvas.requestFocusInWindow();
    boolean wire = updateLocation(canvas, e);
    Location oldWireLoc = wireLoc;
    wireLoc = NULL_LOCATION;
    lastX = Integer.MIN_VALUE;
    if (wire) {
      // System.out.println("pressed wire");
      current = wiring;
      Selection sel = canvas.getSelection();
      Circuit circ = canvas.getCircuit();
      Collection<Component> selected = sel.getAnchoredComponents();
      ArrayList<Component> suppress = null;
      for (Wire w : circ.getWires()) {
        if (selected.contains(w)) {
          if (w.nominallyContains(oldWireLoc)) {
            if (suppress == null)
              suppress = new ArrayList<Component>();
            suppress.add(w);
          }
        }
      }
      sel.setSuppressHandles(suppress);
    } else {
      current = select;
    }
    pressX = e.getX();
    pressY = e.getY();
    current.mousePressed(canvas, e);
  }

  @Override
  public void mouseReleased(Canvas canvas, MouseEvent e) {
    boolean click = isClick(e) && current == wiring;
    canvas.getSelection().setSuppressHandles(null);
    current.mouseReleased(canvas, e);
    if (click) {
      wiring.resetClick();
      select.mousePressed(canvas, e);
      select.mouseReleased(canvas, e);
    }
    current = select;
    cache.clear();
    updateLocation(canvas, e);
  }
  
  private static final Icon toolIcon = Icons.getIcon("select.gif");

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

  @Override
  public void select(Canvas canvas) {
    current = select;
    lastCanvas = canvas;
    cache.clear();
    canvas.getCircuit().addCircuitWeakListener(null, listener);
    canvas.getSelection().addListener(listener);
    select.select(canvas);
  }

  @Override
  public void setAttributeSet(AttributeSet attrs) {
    select.setAttributeSet(attrs);
  }

  private boolean updateLocation(Canvas canvas, int mx, int my, int mods) {
    int snapx = Canvas.snapXToGrid(mx);
    int snapy = Canvas.snapYToGrid(my);
    int dx = mx - snapx;
    int dy = my - snapy;
    boolean isEligible = dx * dx + dy * dy < 36;
    if ((mods & MouseEvent.ALT_DOWN_MASK) != 0)
      isEligible = true;
    // System.out.println(""+isEligible + " dist " + (dx * dx + dy * dy) + " " + (dx*dx+dy*dy<36));
    if (!isEligible) {
      snapx = -1;
      snapy = -1;
    }
    boolean modsSame = lastMods == mods;
    lastCanvas = canvas;
    lastRawX = mx;
    lastRawY = my;
    lastMods = mods;
    if (lastX == snapx && lastY == snapy && modsSame) { // already computed
      // System.out.println("precomputed: " + wireLoc);
      return wireLoc != NULL_LOCATION;
    } else {
      Location snap = Location.create(snapx, snapy);
      if (modsSame) {
        Object o = cache.get(snap);
        if (o != null) {
      // System.out.println("got cache: " + o);
          lastX = snapx;
          lastY = snapy;
          Location oldWireLoc = wireLoc;
          boolean ret = ((Boolean) o).booleanValue();
          wireLoc = ret ? snap : NULL_LOCATION;
          repaintIndicators(canvas, oldWireLoc, wireLoc);
          return ret;
        }
      } else {
        cache.clear();
      }

      Location oldWireLoc = wireLoc;
      boolean ret = isEligible && isWiringPoint(canvas, snap, mods);
      wireLoc = ret ? snap : NULL_LOCATION;
      // System.out.println("ret: " + ret);
      cache.put(snap, Boolean.valueOf(ret));
      int toRemove = cache.size() - CACHE_MAX_SIZE;
      Iterator<Location> it = cache.keySet().iterator();
      while (it.hasNext() && toRemove > 0) {
        it.next();
        it.remove();
        toRemove--;
      }

      lastX = snapx;
      lastY = snapy;
      repaintIndicators(canvas, oldWireLoc, wireLoc);
      return ret;
    }
  }

  private void repaintIndicators(Canvas canvas, Location a, Location b) {
    if (a.equals(b))
      return;
    int w = 3;
    if (a != NULL_LOCATION)
      canvas.repaint(a.getX()-5-w, a.getY()-5-w, 10+2*w, 10+2*w);
    if (b != NULL_LOCATION)
      canvas.repaint(b.getX()-5-w, b.getY()-5-w, 10+2*w, 10+2*w);
  }

  private boolean updateLocation(Canvas canvas, KeyEvent e) {
    int x = lastRawX;
    if (x >= 0)
      return updateLocation(canvas, x, lastRawY, e.getModifiersEx());
    else
      return false;
  }

  private boolean updateLocation(Canvas canvas, MouseEvent e) {
    return updateLocation(canvas, e.getX(), e.getY(), e.getModifiersEx());
  }

  public boolean isBuiltin() { return true; }
}
