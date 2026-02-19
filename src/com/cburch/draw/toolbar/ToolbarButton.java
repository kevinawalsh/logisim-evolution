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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.DragDrop;
import com.cburch.logisim.util.GraphicsUtil;

class ToolbarButton extends JComponent implements MouseListener, DragDrop.Support, DragDrop.Ghost {
	private static final long serialVersionUID = 1L;

	public static final int BORDER = 2;

	protected Toolbar toolbar;
  protected int position;
	protected ToolbarItem item;
	
	ToolbarButton(Toolbar toolbar, int position, ToolbarItem item) {
		this.toolbar = toolbar;
    this.position = position;
		this.item = item;
		addMouseListener(this);
		setFocusable(true);
		setToolTipText("");
    // DragDrop.enable(this, DragDrop.MOVE);
    this.addHierarchyListener(new HierarchyListener() {
      DragHandler h;
      DragGestureRecognizer r;
      public void hierarchyChanged(HierarchyEvent e) {
        if (h == null && ToolbarButton.this.isShowing()) {
          h = new DragHandler(ToolbarButton.this);
          r = DragDrop.source.createDefaultDragGestureRecognizer(ToolbarButton.this, DragDrop.MOVE, h);
        } else if (h != null && !ToolbarButton.this.isShowing()) {
          r.removeDragGestureListener(h);
          h = null;
          r = null;
        }
      }
    });
  }

  private boolean isWithinPopupMenu() {
    return toolbar.isOverflowButton(position) && SwingUtilities.getAncestorOfClass(JPopupMenu.class, (Component)this) != null;
  }

  private static class DragHandler extends DragSourceAdapter implements DragGestureListener {
    ToolbarButton t;
    boolean migratedPopup;

    public DragHandler(ToolbarButton t) { this.t = t; }

    @Override
    public void dragGestureRecognized(DragGestureEvent e) {
      if (t.isWithinPopupMenu()) {
        if (SwingUtilities.isEventDispatchThread()) {
          t.toolbar.migrateOverflowPopupToWindow();
        } else {
          try {
            SwingUtilities.invokeAndWait(() -> t.toolbar.migrateOverflowPopupToWindow());
          } catch (Exception ex) {
            Debug.error("failed to convert popup to window", ex);
            return;
          }
        }
        migratedPopup = true;
      }
      Cursor cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      e.getDragSource().startDrag(e, cursor, t, this);
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent e) {
      if (migratedPopup)
        t.toolbar.migrateOverflowWindowToPopup();
    }
  }

	public ToolbarItem getItem() {
		return item;
	}

  public Toolbar getToolbar() {
    return toolbar;
  }
	
  @Override
	public Dimension getMaximumSize() {
		Dimension dim = getMinimumSize();
    if (toolbar.getOrientation(position) == Toolbar.HORIZONTAL)
      dim.height = Math.max(dim.height, 50);
    else
      dim.width = Math.max(dim.width, 50);
		return dim;
	}

	@Override
	public Dimension getMinimumSize() {
		Dimension dim = item.getDimension(this, toolbar.getOrientation(position));
		dim.width += 2 * BORDER;
		dim.height += 2 * BORDER;
		return dim;
	}

	public Dimension getPreferredSize(Object orientation) {
		Dimension dim = item.getDimension(this, orientation);
		dim.width += 2 * BORDER;
		dim.height += 2 * BORDER;
		return dim;
	}

	@Override
	public Dimension getPreferredSize() {
		return getMinimumSize();
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
    if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()) {
      Component src = (Component)e.getSource();
      Component parent = src.getParent();
      parent.dispatchEvent(SwingUtilities.convertMouseEvent(src, e, parent));
    } else if (item != null && (item.isSelectable() || (item instanceof ToolbarClickableItem))) {
			toolbar.setPressed(this);
		}
	}

	public void mouseReleased(MouseEvent e) {
		if (toolbar.completeButtonPress(e, this)) {
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
				Graphics2D gt = (Graphics2D)g.create(); // UI, no custom rendering hints
        try {
          gt.translate(BORDER, BORDER);
          ((ToolbarClickableItem)item).paintPressedIcon(ToolbarButton.this, gt);
        } finally {
          gt.dispose();
        }
				return;
			}
			Dimension dim = item.getDimension(this, toolbar.getOrientation(position)); 
			Color defaultColor = g.getColor();
			GraphicsUtil.switchToWidth(g, 2);
			g.setColor(Color.GRAY);
			g.fillRect(BORDER, BORDER, dim.width, dim.height);
			GraphicsUtil.switchToWidth(g, 1);
			g.setColor(defaultColor);
		}

		Graphics2D gt = (Graphics2D)g.create(); // UI, no custom rendering hints
    try {
      gt.translate(BORDER, BORDER);
      item.paintIcon(ToolbarButton.this, gt);
    } finally {
      gt.dispose();
    }

		// draw selection indicator
		if (toolbar.getToolbarModel().isSelected(item)) {
			Dimension dim = item.getDimension(this, toolbar.getOrientation(position));
			GraphicsUtil.switchToWidth(g, 2);
			g.setColor(Color.BLACK);
			g.drawRect(BORDER, BORDER, dim.width, dim.height);
			GraphicsUtil.switchToWidth(g, 1);
		}
	
  }

  public static final DragDrop dnd = new DragDrop(ToolbarButton.class, Toolbar.UUID_FLAVOR);
  public DragDrop getDragDrop() { return dnd; }

  public void paintDragImage(JComponent dest, Graphics g, Dimension dim) {
		Graphics2D g2 = (Graphics2D)g.create(); // UI, no custom rendering hints
    try {
      g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.75f));
      g2.setColor(Color.WHITE);
      g2.fillRect(0, 0, dim.width, dim.height);
      g2.setColor(Color.BLACK);
      g2.drawRect(0, 0, dim.width-1, dim.height-1);
      g2.translate(BORDER, BORDER);
      item.paintIcon(ToolbarButton.this, g2);
    } finally {
      g2.dispose();
    }
	}

  @Override
  public Object convertToFlavor(int idx, Object dataFlavor) {
    if (dataFlavor == Toolbar.UUID_FLAVOR)
      return toolbar.TOOLBAR_TOKEN;
    return null;
  }
}
