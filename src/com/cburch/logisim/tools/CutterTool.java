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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.swing.Icon;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WireFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;
import com.cburch.logisim.util.StringGetter;

public final class CutterTool extends Tool {

  private static final String CUTTER_CURSOR_PATH = "resources/logisim/icons/cutter%d%s.png";
  private static Cursor selectCursor = null; // lazily loaded
  private static final Cursor rectSelectCursor = Cursor
      .getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

  private static final int IDLE = 0;
  private static final int RECT_SELECT = 1;
  private static final Icon toolIcon = Icons.getIcon("cutter16.png");

  private static final Color COLOR_RECT_SELECT = new Color(192, 64, 0, 255);
  private static final Color BACKGROUND_RECT_SELECT = new Color(255, 128, 128, 192);

  private Location start;
  private int state = IDLE;
  private int curDx;
  private int curDy;

  public CutterTool() { }

  @Override
  public boolean isBuiltin() { return true; }

  @Override
  public Tool cloneTool() { return new CutterTool(); }

  // All instances considered equal, so it is unique per toolbar, etc.
  @Override
  public boolean equals(Object other) { return other instanceof CutterTool; }

  @Override
  public int hashCode() { return CutterTool.class.hashCode(); }

  @Override
  public void deselect(Canvas canvas) { }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    if (state != RECT_SELECT)
      return;
    Project proj = canvas.getProject();
    Circuit circ = canvas.getCircuit();
    Bounds bds = Bounds.create(start.x, start.y, curDx, curDy);
      
    Graphics gBase = context.getGraphics();
    if (bds.width > 3 && bds.height > 3) {
      gBase.setColor(BACKGROUND_RECT_SELECT);
      gBase.fillRect(bds.x + 1, bds.y + 1, bds.width - 2, bds.height - 2);
    }
        
    Bounds b = bds.snapToGrid();
    if (b != Bounds.EMPTY_BOUNDS) {
      for (Wire c : circ.getWiresIntersecting(b)) {
        Location cloc = c.getLocation();
        Graphics gDup = gBase.create();
        context.setGraphics(gDup);
        ((WireFactory)c.getFactory()).drawPartialGhost(
            context, COLOR_RECT_SELECT,
            cloc.getX(), cloc.getY(), c.getAttributeSet(), b);
        gDup.dispose();
      }
    }

    gBase.setColor(COLOR_RECT_SELECT);
    GraphicsUtil.switchToWidth(gBase, 2);
    gBase.drawRect(bds.x, bds.y,
        Math.max(0, bds.width-1), Math.max(0, bds.height-1));
  }

  private static Cursor loadCutterCursor() {
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    // For the cursor, we have 24x24 and 32x32, in both 2-color and full color.
    int colors = toolkit.getMaximumCursorColors();
    if (colors <= 0)
      return null; // custom cursors are not supported
    String mode = (colors < 4 ? "mono" : "rgb");
    int sz = 32;
    Dimension pref = toolkit.getBestCursorSize(sz, sz);
    if (pref.width <= 28 || pref.height <= 28)
      sz = 24;
    String rsrc = String.format(CUTTER_CURSOR_PATH, sz, mode);
    URL url = CutterTool.class.getClassLoader().getResource(rsrc);
    if (url == null)
      return null;
    Image img = toolkit.createImage(url);
    if (img == null)
      return null;
    return toolkit.createCustomCursor(img,
        new Point(1,1), "Wire Cutter Cursor");
  }

  @Override
  public Cursor getCursor() {
    // On linux, seems to be no best cursor size...
    // for (int i = 0; i < 64; i++)
    //   System.out.printf("%d --> %s\n", i, Toolkit.getDefaultToolkit().getBestCursorSize(i, i));
    if (selectCursor == null) {
      try {
        selectCursor = loadCutterCursor();
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (selectCursor == null)
        selectCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    }
    return state != RECT_SELECT ? selectCursor : rectSelectCursor;
  }

  @Override
  public String getDescription() {
    return S.get("cutterToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("cutterTool");
  }

  @Override
  public Set<Component> getHiddenComponents(Canvas canvas) {
    return null;
  }

  @Override
  public String getName() {
    return "Cutter Tool";
  }

  @Override
  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver) {
    return true;
  }

  @Override
  public void mouseDragged(Canvas canvas, Graphics g, MouseEvent e) {
    Project proj = canvas.getProject();
    setState(proj, RECT_SELECT);
    curDx = e.getX() - start.getX();
    curDy = e.getY() - start.getY();
    proj.repaintCanvas();
  }

  @Override
  public void mousePressed(Canvas canvas, Graphics g, MouseEvent e) {
    canvas.requestFocusInWindow();
    Project proj = canvas.getProject();
    Circuit circuit = canvas.getCircuit();
    start = Location.create(e.getX(), e.getY());
    curDx = 0;
    curDy = 0;
  }

  static final int HITBOX_SIZE = 3;
  
  @Override
  public void mouseClicked(Canvas canvas, Graphics g, MouseEvent e) {
    Project proj = canvas.getProject();
    curDx = curDy = 0;
    
    Circuit circ = canvas.getCircuit();
    Bounds b = Bounds.create(e.getX(), e.getY(), HITBOX_SIZE, HITBOX_SIZE);
    Collection<Wire> wires = circ.getWiresIntersecting(b);

    // If the user clicks a four-way wire intersection,
    // untangle the wires so they don't connect any more.
    //
    //       |                      |
    //       |                      |
    //    ---o---    becomes     ---|---
    //       |                      |
    //       |                      |
     
    Collection<Wire> wiresToRemove = new ArrayList<>();
    Collection<Wire> wiresToAdd = new ArrayList<>();
    if (wires.size() == 4) {
      // Make sure all four end at same point.
      Location p = Location.create((e.getX()+5)/10*10,
          (e.getY()/10*10));
      // Remove the four wires, replace with two instead
      Location h0 = null, h1 = null, v0 = null, v1 = null;
      for (Wire w : wires) {
        if (!w.endsAt(p)) {
          System.out.printf("internal error: wire %s does not end at %s\n", w, p);
          continue; // should never happen
        }
        if (w.isVertical()) {
          v1 = v0;
          v0 = w.getOtherEnd(p);
        } else {
          h1 = h0;
          h0 = w.getOtherEnd(p);
        }
      }
      if (h1 == null || v1 == null) {
        System.out.printf("internal error: confusing intersection"); // should never happen
      } else {
        wiresToRemove = wires;
        wiresToAdd.add(Wire.create(h0, h1));
        wiresToAdd.add(Wire.create(v0, v1));
        doIt(proj, circ, S.getter("cutterDetangleAction"),
            wiresToAdd, wiresToRemove);
      }
    }

    // If the user clicks a wire or a pair of crossing wires,
    // without shift key held, then delete wire segment(s).
    else if (wires.size() > 0) {
      if (e.isShiftDown()) {
        hintWorked();
        // If shift key is held, then delete entire wire path(s)
        // as well...
        HashSet<Wire> moreWires = new HashSet<>();
        for (Wire w : wires)
          moreWires.addAll(circ.getWireSet(w).getWires());
        wiresToRemove.addAll(moreWires);
      } else {
        if (hintReady(AppPreferences.CUTTER_TOOL_TIP))
          showHint(canvas, start, S.get("cutterTip"));
        wiresToRemove.addAll(wires);
      }
      doIt(proj, circ, S.getter("cutterAction"),
          wiresToAdd, wiresToRemove);
    } 

    setState(proj, IDLE);
    proj.repaintCanvas();
  }

  @Override
  public void mouseReleased(Canvas canvas, Graphics g, MouseEvent e) {
    if (state != RECT_SELECT)
      return;

    // This is a rectangular selection.
    // Maybe if shift is held, we should lift these wire segments into a
    // floating selection instead of deleting them?

    Project proj = canvas.getProject();
    Bounds bds = Bounds.create(start.x, start.y, curDx, curDy);
    Bounds b = bds.snapToGrid(); // maybe expand instead of shrinking?
    if (b != Bounds.EMPTY_BOUNDS) {
      Circuit circ = canvas.getCircuit();

      // Collection<Wire> wiresToLift = new ArrayList<>();
      Collection<Wire> wiresToAdd = new ArrayList<>();
      Collection<Wire> wiresToRemove = new ArrayList<>();
      for (Wire w : circ.getWiresIntersecting(b)) {
        Wire[] parts = WireFactory.splitWire(w, b);
        if (parts == null)
          continue;
        wiresToRemove.add(w);
        // wiresToLift.add(parts[1]);
        if (parts[0] != null)
          wiresToAdd.add(parts[0]);
        if (parts[2] != null)
          wiresToAdd.add(parts[2]);
      }
      doIt(proj, circ, S.getter("cutterAction"),
          wiresToAdd, wiresToRemove);
    }
    setState(proj, IDLE);
    proj.repaintCanvas();
  }

  private void doIt(Project proj, Circuit circ, StringGetter name,
      Collection<Wire> wiresToAdd, Collection<Wire> wiresToRemove) {
    if (!wiresToAdd.isEmpty() || !wiresToRemove.isEmpty()) {
      CircuitMutation mutation = new CircuitMutation(circ);
      mutation.addAll(wiresToAdd);
      mutation.removeAll(wiresToRemove);
      Action act = mutation.toAction(name);
      proj.doAction(act);
    }
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    Graphics g = c.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(c.getDestination(), g, x + 2, y + 2);
    } else {
      int[] xp = { x + 5, x + 5, x + 9, x + 12, x + 14, x + 11, x + 16 };
      int[] yp = { y, y + 17, y + 12, y + 18, y + 18, y + 12, y + 12 };
      g.setColor(java.awt.Color.black);
      g.fillPolygon(xp, yp, xp.length);
    }
  }

  private void setState(Project proj, int new_state) {
    if (state == new_state)
      return; // do nothing if state not new

    state = new_state;
    proj.getFrame().getCanvas().setCursor(getCursor());
  }
}
