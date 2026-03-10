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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.generic.GridPainter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.GraphicsUtil;

class CanvasPainter {
  private static final Set<Component> NO_COMPONENTS = Collections.emptySet();

  private Canvas canvas;
  private GridPainter grid;
  private Component haloedComponent = null;
  private Circuit haloedCircuit = null;
  private WireSet highlightedWires = WireSet.EMPTY;

  CanvasPainter(Canvas canvas) {
    this.canvas = canvas;
    this.grid = new GridPainter(canvas);

    AppPreferences.PRINTER_VIEW.addPrefChangeWeakListener(this, e -> canvas.repaint());
    AppPreferences.ATTRIBUTE_HALO.addPrefChangeWeakListener(this, e -> canvas.repaint());
  }

  private void drawWidthIncompatibilityData(Graphics2D base, Graphics2D g,
      Project proj) {
    Circuit circ = proj.getCurrentCircuit();
    if (circ == null)
      return;
    Set<WidthIncompatibilityData> exceptions = circ.getWidthIncompatibilityData();
    if (exceptions == null || exceptions.size() == 0)
      return;

    FontMetrics fm = base.getFontMetrics(g.getFont());
    for (WidthIncompatibilityData ex : exceptions) {
      BitWidth common = ex.getCommonBitWidth();
      for (int i = 0; i < ex.size(); i++) {
        Location p = ex.getPoint(i);
        BitWidth w = ex.getBitWidth(i);

        // ensure it hasn't already been drawn
        boolean drawn = false;
        for (int j = 0; j < i; j++) {
          if (ex.getPoint(j).equals(p)) {
            drawn = true;
            break;
          }
        }
        if (drawn)
          continue;

        // compute the caption combining all similar points
        String caption = "" + w.getWidth();
        for (int j = i + 1; j < ex.size(); j++) {
          if (ex.getPoint(j).equals(p)) {
            caption += "/" + ex.getBitWidth(j);
            break;
          }
        }
        GraphicsUtil.switchToWidth(g, 2);
        if (common != null && !w.equals(common)) {
          g.setColor(Value.WIDTH_ERROR_HIGHLIGHT_COLOR);
          g.drawOval(p.getX() - 5, p.getY() - 5, 10, 10);
        }
        g.setColor(Value.WIDTH_ERROR_COLOR);
        g.drawOval(p.getX() - 4, p.getY() - 4, 8, 8);
        GraphicsUtil.switchToWidth(g, 3);
        GraphicsUtil.outlineText(g, caption, p.getX() + 4,
            p.getY() + 1 + fm.getAscent(),
            Value.WIDTH_ERROR_CAPTION_COLOR,
            common != null && !w.equals(common)
            ? Value.WIDTH_ERROR_HIGHLIGHT_COLOR
            : Value.WIDTH_ERROR_CAPTION_BGCOLOR);
      }
    }
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 1);
  }

  private void drawWithUserState(Graphics2D base, Graphics2D g, Project proj) {
    Circuit circ = proj.getCurrentCircuit();
    if (circ == null)
      return;
    Selection sel = proj.getSelection();
    Set<Component> hidden;
    Tool dragTool = canvas.getDragTool();
    if (dragTool == null) {
      hidden = NO_COMPONENTS;
    } else {
      hidden = dragTool.getHiddenComponents(canvas);
      if (hidden == null)
        hidden = NO_COMPONENTS;
    }

    // draw halo around component whose attributes we are viewing
    boolean showHalo = AppPreferences.ATTRIBUTE_HALO.get();
    if (showHalo && haloedComponent != null && haloedCircuit == circ
        && !hidden.contains(haloedComponent)) {
      Bounds bds = haloedComponent.getVisibleBounds().expand(5);
      int x = bds.getX();
      int y = bds.getY();
      int w = bds.getWidth();
      int h = bds.getHeight();
      if (w < 15) {
        x -= (w-15)/2;
        w = 15;
      }
      if (h < 15) {
        y -= (h-15)/2;
        h = 15;
      }
      g.setColor(Canvas.HALO_COLOR);
      GraphicsUtil.switchToWidth(g, 3);
      g.drawRoundRect(x, y, w, h, 5, 5);
      GraphicsUtil.switchToWidth(g, 1);
      g.setColor(Color.BLACK);
    }

    // draw circuit and selection
    CircuitState circState = proj.getCircuitState();
    boolean printerView = AppPreferences.PRINTER_VIEW.get();
    ComponentDrawContext context = new ComponentDrawContext(canvas, circ,
        circState, base, g, printerView);
    context.setHighlightedWires(highlightedWires);
    circ.draw(context, hidden);
    sel.draw(context, hidden);

    // draw tool
    Tool tool = dragTool != null ? dragTool : proj.getTool();
    if (tool != null && !canvas.isPopupMenuUp()) {
      Graphics gCopy = g.create();
      context.setGraphics(gCopy);
      tool.draw(canvas, context);
      context.setGraphics(g);
      gCopy.dispose();
    }
  }

  private void exposeHaloedComponent() {
    Component c = haloedComponent;
    if (c == null)
      return;
    Bounds bds = c.getVisibleBounds().expand(8);
    int x = bds.getX();
    int y = bds.getY();
    int w = bds.getWidth();
    int h = bds.getHeight();
    if (w < 20) {
      x -= (w-20)/2;
      w = 20;
    }
    if (h < 20) {
      y -= (h-20)/2;
      h = 20;
    }
    canvas.repaint(x, y, w, h);
  }

  //
  // accessor methods
  //
  GridPainter getGridPainter() {
    return grid;
  }

  Component getHaloedComponent() {
    return haloedComponent;
  }

  //
  // painting methods
  //
  void paintContents(Graphics2D g, Project proj) {
    CircuitState circState = proj.getCircuitState();
    if (circState == null)
      return;
    Dimension size = canvas.getSize();
    double zoomFactor = canvas.getZoomFactor();
    // Debugging
    // Rectangle clip = g.getClipBounds();
    // if (clip == null) {
    //   clip = new Rectangle(0, 0, size.width, size.height);
    // }
    // g.setColor(Color.magenta);
    // g.fillRect(clip.x, clip.y, clip.width, clip.height);

    grid.paintGrid(g);
    g.setColor(Color.black);

    Graphics2D gScaled = (Graphics2D)g.create();
    try {
      if (zoomFactor != 1.0)
        gScaled.scale(zoomFactor, zoomFactor);
      drawWithUserState(g, gScaled, proj);
      drawWidthIncompatibilityData(g, gScaled, proj);
      Circuit circ = proj.getCurrentCircuit();

      ComponentDrawContext ptContext = new ComponentDrawContext(canvas, circ,
          circState, g, gScaled);
      ptContext.setHighlightedWires(highlightedWires);
      gScaled.setColor(Color.RED);
      circState.drawOscillatingPoints(ptContext);
      gScaled.setColor(Color.BLUE);
      proj.getSimulator().drawStepPoints(ptContext);
      gScaled.setColor(Color.MAGENTA);
      proj.getSimulator().drawPendingInputs(ptContext);
    } finally {
      gScaled.dispose();
    }
  }

  public void setHaloedComponent(Circuit circ, Component comp) {
    if (comp == haloedComponent)
      return;
    exposeHaloedComponent();
    haloedCircuit = circ;
    haloedComponent = comp;
    exposeHaloedComponent();
  }

  void setHighlightedWires(WireSet value) {
    highlightedWires = value == null ? WireSet.EMPTY : value;
  }
}
