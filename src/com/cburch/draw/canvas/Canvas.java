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

package com.cburch.draw.canvas;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;

import com.cburch.draw.model.CanvasModel;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.undo.Action;
import com.cburch.logisim.util.GraphicsUtil;

public class Canvas extends JComponent {
	private static final long serialVersionUID = 1L;
	public static final String TOOL_PROPERTY = "tool";
	public static final String MODEL_PROPERTY = "model";

	private CanvasModel model;
	private ActionDispatcher dispatcher;
	private CanvasListener listener;
	private Selection selection;

	public Canvas() {
		model = null;
		listener = new CanvasListener(this);
		selection = new Selection();

		addMouseListener(listener);
		addMouseMotionListener(listener);
		addKeyListener(listener);
		setPreferredSize(new Dimension(200, 200));
	}

	public void doAction(Action action) {
		dispatcher.doAction(action);
	}

	public CanvasModel getModel() {
		return model;
	}

	public Selection getSelection() {
		return selection;
	}

	public CanvasTool getTool() {
		return listener.getTool();
	}

	public double getZoomFactor() {
		return 1.0; // subclass will have to override this
	}

	protected void paintBackground(Graphics2D g) {
		g.clearRect(0, 0, getWidth(), getHeight());
	}

	@Override
	public void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    Object oldHints[] = GraphicsUtil.setRenderingHintsForCanvas(g2);
    try {
      paintBackground(g2);
      paintForeground(g2);
    } finally {
      GraphicsUtil.restoreRenderingHints(g2, oldHints);
    }
	}

	protected void paintForeground(Graphics2D g) {
		CanvasModel cModel = this.model;
		CanvasTool tool = listener.getTool();
		if (cModel != null) {
			Graphics dup = g.create();
			cModel.paint(g, selection);
			dup.dispose();
		}
		if (tool != null) {
			Graphics2D dup = (Graphics2D)g.create();
			tool.draw(this, dup);
			dup.dispose();
		}
	}

	public void repaintCanvasCoords(int x, int y, int width, int height) {
		repaint(x, y, width, height);
	}

	public void setModel(CanvasModel value, ActionDispatcher dispatcher) {
		CanvasModel oldValue = model;
		if (oldValue != value) {
			if (oldValue != null)
				oldValue.removeCanvasModelWeakListener(null, listener);
			model = value;
			this.dispatcher = dispatcher;
			if (value != null)
				value.addCanvasModelWeakListener(null, listener);
			selection.clearSelected();
			repaint();
			firePropertyChange(MODEL_PROPERTY, oldValue, value);
		}
	}

	protected void setSelection(Selection value) {
		selection = value;
		repaint();
	}

	public void setTool(CanvasTool value) {
		CanvasTool oldValue = listener.getTool();
		if (value != oldValue) {
			listener.setTool(value);
			firePropertyChange(TOOL_PROPERTY, oldValue, value);
		}
	}

	protected JPopupMenu showPopupMenu(MouseEvent e, CanvasObject clicked) {
		return null; // subclass will override if it supports popup menus
	}

	public int snapX(int x) {
		return x; // subclass will have to override this
	}

	public int snapY(int y) {
		return y; // subclass will have to override this
	}

	public void toolGestureComplete(CanvasTool tool, CanvasObject created) {
		// nothing to do - subclass may override
	}
}
