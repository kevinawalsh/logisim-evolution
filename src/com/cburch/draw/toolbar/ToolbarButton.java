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

package com.cburch.draw.toolbar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.cburch.logisim.util.DragDrop;
import com.cburch.logisim.util.GraphicsUtil;

class ToolbarButton extends JComponent implements MouseListener, DragDrop.Support, DragDrop.Ghost {
	private static final long serialVersionUID = 1L;

	public static final int BORDER = 2;

	private Toolbar toolbar;
	private ToolbarItem item;

	ToolbarButton(Toolbar toolbar, ToolbarItem item) {
		this.toolbar = toolbar;
		this.item = item;
		addMouseListener(this);
		setFocusable(true);
		setToolTipText("");
    DragDrop.enable(this, DragDrop.MOVE);
	}

	public ToolbarItem getItem() {
		return item;
	}

  public Toolbar getToolbar() {
    return toolbar;
  }

	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension dim = item.getDimension(toolbar.getOrientation());
		dim.width += 2 * BORDER;
		dim.height += 2 * BORDER;
		return dim;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		return item.getToolTip();
	}

	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }

	public void mouseExited(MouseEvent e) {
		toolbar.setPressed(null);
	}

	public void mousePressed(MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e)) {
      Component src = (Component)e.getSource();
      Component parent = src.getParent();
      parent.dispatchEvent(SwingUtilities.convertMouseEvent(src, e, parent));
    } else if (item != null && (item.isSelectable() || (item instanceof ToolbarClickableItem))) {
			toolbar.setPressed(this);
		}
	}

	public void mouseReleased(MouseEvent e) {
		if (toolbar.getPressed() == this) {
			toolbar.setPressed(null);
			if (item != null && item.isSelectable()) {
				toolbar.getToolbarModel().itemSelected(item);
			} else if (item != null && item instanceof ToolbarClickableItem) {
				((ToolbarClickableItem)item).clicked();
			}
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		if (toolbar.getPressed() == this) {
			if (item instanceof ToolbarClickableItem) {
				Graphics g2 = g.create();
				g2.translate(BORDER, BORDER);
				((ToolbarClickableItem)item).paintPressedIcon(ToolbarButton.this, g2);
				g2.dispose();
				return;
			}
			Dimension dim = item.getDimension(toolbar.getOrientation());
			Color defaultColor = g.getColor();
			GraphicsUtil.switchToWidth(g, 2);
			g.setColor(Color.GRAY);
			g.fillRect(BORDER, BORDER, dim.width, dim.height);
			GraphicsUtil.switchToWidth(g, 1);
			g.setColor(defaultColor);
		}

		Graphics g2 = g.create();
		g2.translate(BORDER, BORDER);
		item.paintIcon(ToolbarButton.this, g2);
		g2.dispose();

		// draw selection indicator
		if (toolbar.getToolbarModel().isSelected(item)) {
			Dimension dim = item.getDimension(toolbar.getOrientation());
			GraphicsUtil.switchToWidth(g, 2);
			g.setColor(Color.BLACK);
			g.drawRect(BORDER, BORDER, dim.width, dim.height);
			GraphicsUtil.switchToWidth(g, 1);
		}
	}

  public static final DragDrop dnd = new DragDrop(ToolbarButton.class, Toolbar.UUID_FLAVOR);
  public DragDrop getDragDrop() { return dnd; }

  public void paintDragImage(JComponent dest, Graphics gr, Dimension dim) {
		Graphics2D g = (Graphics2D)gr.create();
    g.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.75f));
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, dim.width, dim.height);
    g.setColor(Color.BLACK);
    g.drawRect(0, 0, dim.width-1, dim.height-1);
		g.translate(BORDER, BORDER);
		item.paintIcon(ToolbarButton.this, g);
    // ((Graphics2D)g).setComposite(java.awt.AlphaComposite.SrcOver);
		g.dispose();
	}

  @Override
  public Object convertToFlavor(int idx, Object dataFlavor) {
    if (dataFlavor == Toolbar.UUID_FLAVOR)
      return toolbar.TOOLBAR_TOKEN;
    return null;
  }
}
