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

package com.cburch.logisim.comp;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.GraphicsUtil;

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

public final class ComponentDrawContext {
  private static final int PIN_OFFS = 2;
  private static final int PIN_RAD = 4;

  private final java.awt.Component dest;
  private final Circuit circuit;
  private final CircuitState circuitState;
  private final Graphics2D base; // untransformed, to be used only for measuring text, obtaining font metrics, etc.
  private /*final*/ Graphics2D g; // potentially transformed, to be used for all drawing
  private /*final*/ boolean showState;
  private /*final*/ boolean showColor;
  private final boolean printView;
  private /*final*/ WireSet highlightedWires;
  private final InstancePainter instancePainter;

  // FIXME:
  // - Base should perhaps be a FontRenderContext, not a Graphics.
  // - Use Graphics2D everywhere, not Graphics, to indicate these are not
  //   awt/swing methods, and to signal that anti-aliasing/fractional-metrics
  //   hinting should have already been applied to both.
  // - Eliminate setGraphics(), provide some kind of replace-with-fresh-graphics
  //   feature instead.
  public ComponentDrawContext(java.awt.Component dest, Circuit circuit,
      CircuitState circuitState, Graphics base, Graphics g) {
    this(dest, circuit, circuitState, base, g, false);
  }

  public ComponentDrawContext(java.awt.Component dest, Circuit circuit,
      CircuitState circuitState, Graphics base, Graphics g,
      boolean printView) {
    this.dest = dest;
    this.circuit = circuit;
    this.circuitState = circuitState;
    this.base = (Graphics2D)base;
    this.g = (Graphics2D)g;
    this.showState = true;
    this.showColor = true;
    this.printView = printView;
    this.highlightedWires = WireSet.EMPTY;
    this.instancePainter = new InstancePainter(this, null);
  }

  public void drawBounds(Component comp) {
    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.BLACK);
    Bounds bds = comp.getNominalBounds(); // nominal, so as not to include label
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawClock(Component comp, int i, Direction dir) {
    Color curColor = g.getColor();
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);

    EndData e = comp.getEnd(i);
    Location pt = e.getLocation();
    int x = pt.getX();
    int y = pt.getY();
    final int CLK_SZ = 4;
    final int CLK_SZD = CLK_SZ - 1;
    if (dir == Direction.NORTH) {
      g.drawLine(x - CLK_SZD, y - 1, x, y - CLK_SZ);
      g.drawLine(x + CLK_SZD, y - 1, x, y - CLK_SZ);
    } else if (dir == Direction.SOUTH) {
      g.drawLine(x - CLK_SZD, y + 1, x, y + CLK_SZ);
      g.drawLine(x + CLK_SZD, y + 1, x, y + CLK_SZ);
    } else if (dir == Direction.EAST) {
      g.drawLine(x + 1, y - CLK_SZD, x + CLK_SZ, y);
      g.drawLine(x + 1, y + CLK_SZD, x + CLK_SZ, y);
    } else if (dir == Direction.WEST) {
      g.drawLine(x - 1, y - CLK_SZD, x - CLK_SZ, y);
      g.drawLine(x - 1, y + CLK_SZD, x - CLK_SZ, y);
    }

    g.setColor(curColor);
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawClockSymbol(Component comp, int xpos, int ypos) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawLine(xpos, ypos - 4, xpos + 8, ypos);
    g.drawLine(xpos, ypos + 4, xpos + 8, ypos);
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawDongle(int x, int y) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawOval(x - 4, y - 4, 9, 9);
  }

  public void drawReshapeHandle(Location loc) {
    int x = loc.getX();
    int y = loc.getY();
    g.setColor(Color.magenta);
    g.fillRect(x - 3, y - 3, 7, 7);
    g.setColor(Color.black);
    g.drawRect(x - 3, y - 3, 7, 7);
  }

  public void drawHandle(int x, int y) {
    g.setColor(Color.white);
    g.fillRect(x - 3, y - 3, 7, 7);
    g.setColor(Color.black);
    g.drawRect(x - 3, y - 3, 7, 7);
  }

  public void drawHandle(Location loc) {
    drawHandle(loc.getX(), loc.getY());
  }

  public void drawHandles(Component comp) {
    Bounds b = comp.getVisibleBounds();
    int left = b.getX();
    int right = left + b.getWidth();
    int top = b.getY();
    int bot = top + b.getHeight();
    drawHandle(right, top);
    drawHandle(left, bot);
    drawHandle(right, bot);
    drawHandle(left, top);
  }

  public void drawPin(Component comp, int i) {
    EndData e = comp.getEnd(i);
    Location pt = e.getLocation();
    Color curColor = g.getColor();
    if (getShowState()) {
      CircuitState state = getCircuitState();
      g.setColor(state.getValue(pt).getColor());
    } else {
      g.setColor(Color.BLACK);
    }
    g.fillOval(pt.getX() - PIN_OFFS, pt.getY() - PIN_OFFS, PIN_RAD, PIN_RAD);
    g.setColor(curColor);
  }

  public void drawPin(Component comp, int i, String label, Direction dir) {
    Color curColor = g.getColor();
    if (i < 0 || i >= comp.getEnds().size())
      return;
    EndData e = comp.getEnd(i);
    Location pt = e.getLocation();
    int x = pt.getX();
    int y = pt.getY();
    if (getShowState()) {
      CircuitState state = getCircuitState();
      g.setColor(state.getValue(pt).getColor());
    } else {
      g.setColor(Color.BLACK);
    }
    g.fillOval(x - PIN_OFFS, y - PIN_OFFS, PIN_RAD, PIN_RAD);
    g.setColor(curColor);
    if (dir == Direction.EAST) {
      GraphicsUtil.drawText(g, label, x + 3, y, ALIGN.H_LEFT, ALIGN.V_CENTER_FIRST); // CENTER_OVERALL may look nicer?
    } else if (dir == Direction.WEST) {
      GraphicsUtil.drawText(g, label, x - 3, y, ALIGN.H_RIGHT, ALIGN.V_CENTER_FIRST); // CENTER_OVERALL may look nicer?
    } else if (dir == Direction.SOUTH) {
      GraphicsUtil.drawText(g, label, x, y - 3, ALIGN.H_CENTER, ALIGN.V_BASELINE);
    } else if (dir == Direction.NORTH) {
      GraphicsUtil.drawText(g, label, x, y + 3, ALIGN.H_CENTER, ALIGN.V_TOP);
    }
  }

  public void drawPins(Component comp) {
    Color curColor = g.getColor();
    for (EndData e : comp.getEnds()) {
      Location pt = e.getLocation();
      if (getShowState()) {
        CircuitState state = getCircuitState();
        g.setColor(state.getValue(pt).getColor());
      } else {
        g.setColor(Color.BLACK);
      }
      g.fillOval(pt.getX() - PIN_OFFS, pt.getY() - PIN_OFFS, PIN_RAD,
          PIN_RAD);
    }
    g.setColor(curColor);
  }

  // never used
  // public void drawRectangle(Component comp) {
  //   drawRectangle(comp, "");
  // }

  // never used
  // public void drawRectangle(Component comp, String label) {
  //   Bounds bds = comp.getBounds(g);
  //   drawRectangle(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight(),
  //       label);
  // }

  // never used
  // public void drawRectangle(ComponentFactory source, int x, int y,
  //     AttributeSet attrs, String label) {
  //   Bounds bds = source.getOffsetBounds(attrs);
  //   drawRectangle(source, x + bds.getX(), y + bds.getY(), bds.getWidth(),
  //       bds.getHeight(), label);
  // }

  // never used
  // public void drawRectangle(ComponentFactory source, int x, int y, int width,
  //     int height, String label) {
  //   GraphicsUtil.switchToWidth(g, 2);
  //   g.drawRect(x + 1, y + 1, width - 1, height - 1);
  //   if (label != null && !label.equals("")) {
  //     FontMetrics fm = base.getFontMetrics(g.getFont());
  //     int lwid = fm.stringWidth(label);
  //     if (height > 20) { // centered at top edge
  //       g.drawString(label, x + (width - lwid) / 2,
  //           y + 2 + fm.getAscent());
  //     } else { // centered overall
  //       g.drawString(label, x + (width - lwid) / 2,
  //           y + (height + fm.getAscent()) / 2 - 1);
  //     }
  //   }
  // }

  public void drawRectangle(int x, int y, int width, int height, String label) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x, y, width, height);
    if (label != null && !label.equals("")) {
      FontMetrics fm = base.getFontMetrics(g.getFont());
      int lwid = fm.stringWidth(label);
      if (height > 20) { // centered at top edge
        g.drawString(label, x + (width - lwid) / 2,
            y + 2 + fm.getAscent());
      } else { // centered overall
        g.drawString(label, x + (width - lwid) / 2,
            y + (height + fm.getAscent()) / 2 - 1);
      }
    }
  }

  public Circuit getCircuit() {
    return circuit;
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  public java.awt.Component getDestination() {
    return dest;
  }

  public Object getGateShape() {
    return AppPreferences.GATE_SHAPE.get();
  }

  // Unlike swing/awt Component.getGraphics(), this does *not* create a new
  // Graphics object, but only borrows the existing Graphics object. The caller
  // should not dispose it.
  public Graphics2D getGraphics() {
    return g;
  }

  public WireSet getHighlightedWires() {
    return highlightedWires;
  }

  public InstancePainter getInstancePainter() {
    return instancePainter;
  }

  public boolean getShowState() {
    return !printView && showState;
  }

  public boolean isPrintView() {
    return printView;
  }

  public void setGraphics(Graphics g) {
    this.g = (Graphics2D)g;
  }

  public void setHighlightedWires(WireSet value) {
    this.highlightedWires = value == null ? WireSet.EMPTY : value;
  }

  public void setShowColor(boolean value) {
    showColor = value;
  }

  public void setShowState(boolean value) {
    showState = value;
  }

  public boolean shouldDrawColor() {
    return !printView && showColor;
  }

}
