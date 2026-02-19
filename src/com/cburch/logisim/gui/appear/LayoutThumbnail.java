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

package com.cburch.logisim.gui.appear;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Collections;

import javax.swing.JComponent;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.io.Led;
import com.cburch.logisim.std.io.RGBLed;
import com.cburch.logisim.util.GraphicsUtil;

public class LayoutThumbnail extends JComponent {
  private static final long serialVersionUID = 1L;

  private static final int BORDER = 10;

  private CircuitState circuitState;
  private Collection<Instance> ports, elts;

  public LayoutThumbnail() {
    circuitState = null;
    ports = null;
    elts = null;
    setBackground(Color.LIGHT_GRAY);
    setPreferredSize(new Dimension(400, 300));
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (circuitState == null)
      return;
    Object oldHints[] = GraphicsUtil.setRenderingHintsForCanvas(g);
    try {
      Circuit circuit = circuitState.getCircuit();
      Bounds bds = circuit.getCircuitBounds(g);
      Dimension size = getSize();
      double scaleX = (double) (size.width - 2 * BORDER) / bds.getWidth();
      double scaleY = (double) (size.height - 2 * BORDER)
          / bds.getHeight();
      double scale = Math.min(1.0, Math.min(scaleX, scaleY));

      Graphics2D gCopy = (Graphics2D)g.create();
      int borderX = (int) ((size.width - bds.getWidth() * scale) / 2);
      int borderY = (int) ((size.height - bds.getHeight() * scale) / 2);
      gCopy.translate(borderX, borderY);
      if (scale != 1.0)
        gCopy.scale(scale, scale);
      gCopy.translate(-bds.getX(), -bds.getY());

      ComponentDrawContext context = new ComponentDrawContext(this,
          circuit, circuitState, g, gCopy);
      context.setShowState(false);
      context.setShowColor(false);
      circuit.draw(context, Collections.<Component>emptySet());
      if (ports != null && ports.size() > 0) {
        gCopy.setColor(AppearancePort.COLOR);
        int width = Math.max(4, (int) ((2 / scale) + 0.5));
        GraphicsUtil.switchToWidth(gCopy, width);
        for (Instance port : ports) {
          Bounds b = port.getNominalBounds();
          int x = b.getX();
          int y = b.getY();
          int w = b.getWidth();
          int h = b.getHeight();
          if (Pin.FACTORY.isInputPin(port)) {
            gCopy.drawRect(x, y, w, h);
          } else {
            if (b.getWidth() > 25) {
              gCopy.drawRoundRect(x, y, w, h, 4, 4);
            } else {
              gCopy.drawOval(x, y, w, h);
            }
          }
        }
      }
      if (elts != null && elts.size() > 0) {
        gCopy.setColor(DynamicElement.COLOR);
        int width = Math.max(4, (int) ((2 / scale) + 0.5));
        GraphicsUtil.switchToWidth(gCopy, width);
        for (Instance elt : elts) {
          Bounds b = elt.getNominalBounds();
          int x = b.getX();
          int y = b.getY();
          int w = b.getWidth();
          int h = b.getHeight();
          if (elt.getFactory() instanceof Led || elt.getFactory() instanceof RGBLed) {
            gCopy.drawOval(x, y, w, h);
          } else {
            gCopy.drawRect(x, y, w, h);
          }
        }
      }
      gCopy.dispose();

      g.setColor(Color.BLACK);
      GraphicsUtil.switchToWidth(g, 2);
      g.drawRect(0, 0, size.width - 2, size.height - 2);
    } finally {
      GraphicsUtil.restoreRenderingHints(g, oldHints);
    }
  }

  public void setCircuit(CircuitState circuitState,
      Collection<Instance> ports, Collection<Instance> elts) {
    this.circuitState = circuitState;
    this.ports = ports;
    this.elts = elts;
    repaint();
  }

}
