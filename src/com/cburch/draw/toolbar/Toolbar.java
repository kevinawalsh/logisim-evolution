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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.cburch.logisim.util.DragDrop;

public class Toolbar extends JPanel {

  static final Color DROP_CURSOR_COLOR =
      UIManager.getDefaults().getColor("Table.dropLineColor");

  private class JPanelWithCursor extends JPanel {
    int cursorPos = -1; // 1 or higher is valid (ignores filler and glue at ends)

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (cursorPos >= 1) {
        Component c = getComponent(cursorPos);
        g.setColor(DROP_CURSOR_COLOR);
        Point p = c.getLocation();
        if (orientation == HORIZONTAL)
          g.fillRect(p.x-1, 2, 3, getHeight()-4);
        else
          g.fillRect(2, p.y-1, getWidth()-4, 3);
      }
    }

    int setDropCursor(Point p) {
      int newPos = -1;
      if (p != null && getComponentCount() > 1) {
        Component[] children = getComponents();
        int end = children.length - 2; // ignore filler and glue at ends
        for (newPos = 1; newPos < end; newPos++) {
          Component c = getComponent(newPos);
          Rectangle r = c.getBounds();
          if (orientation == HORIZONTAL && p.x < r.x + r.width/2)
            break;
          if (orientation == VERTICAL && p.y < r.y + r.height/2)
            break;
        }
      }
      if (newPos != cursorPos) {
        cursorPos = newPos;
        repaint();
      }
      return cursorPos - 1; // minus one for filler at end
    }

  }

	private class MyListener implements MouseListener, ToolbarModelListener, DropTargetListener {
    @Override
		public void toolbarAppearanceChanged(ToolbarModelEvent event) {
			repaint();
		}

    @Override
		public void toolbarContentsChanged(ToolbarModelEvent event) {
			computeContents();
		}

    boolean checkIntraToolbarMove(DropTargetDragEvent e) throws Exception {
      DataFlavor flavor1 = ToolbarButton.dnd.dataFlavor;
      DataFlavor flavor2 = ToolbarButton.dnd.dataFlavors[1]; // UUID_FLAVOR
      int action = DnDConstants.ACTION_MOVE;
      if ((e.getSourceActions() & action) == 0 || !e.isDataFlavorSupported(flavor1) || !e.isDataFlavorSupported(flavor2))
        return false;
      e.acceptDrag(action);
      String token = (String)e.getTransferable().getTransferData(flavor2);
      return (token != null && token.equals(TOOLBAR_TOKEN));
    }

    boolean checkIntraProjectAddition(DropTargetDragEvent e) throws Exception {
      DataFlavor flavor = model.getAcceptedDataFlavor();
      int action = DnDConstants.ACTION_LINK;
      if ((e.getSourceActions() & action) == 0 || !e.isDataFlavorSupported(flavor)) {
        return false;
      }
      DataFlavor preFlavors[] = model.getPrecheckDataFlavors();
      for (DataFlavor f : preFlavors) {
        if (!e.isDataFlavorSupported(f)) {
          return false;
        }
      }
      e.acceptDrag(action);
      int n = preFlavors.length;
      String[] tokens = new String[n];
      Transferable t = e.getTransferable();
      for (int i = 0; i < n; i++) {
        tokens[i] = (String)t.getTransferData(preFlavors[i]);
      }
      return model.dragPrecheck(tokens);
    }

    void checkDrag(DropTargetDragEvent e) {
      // todo: be careful about drag and drop between projects
      try {
        if (model.supportsDragDrop()
            && (checkIntraToolbarMove(e) || checkIntraProjectAddition(e))) {
          subpanel.setDropCursor(e.getLocation());
          return;
        }
      } catch (Throwable t) { t.printStackTrace(); }
      subpanel.setDropCursor(null);
      e.rejectDrag();
    }

    @Override
    public void dragEnter(DropTargetDragEvent e) { checkDrag(e); }
    @Override
    public void dragOver(DropTargetDragEvent e) { checkDrag(e); }
    @Override
    public void dropActionChanged(DropTargetDragEvent e) { checkDrag(e); }
    @Override
    public void dragExit(DropTargetEvent e)  { subpanel.setDropCursor(null); }

    boolean tryIntraToolbarMove(DropTargetDropEvent e, int pos) throws Exception {
      DataFlavor flavor = ToolbarButton.dnd.dataFlavor;
      int action = DnDConstants.ACTION_MOVE;
      if ((e.getSourceActions() & action) == 0 || !e.isDataFlavorSupported(flavor))
        return false;
      e.acceptDrop(action);
      ToolbarButton incoming;
      incoming = (ToolbarButton)e.getTransferable().getTransferData(flavor);
      if (!(incoming != null && incoming.getToolbar() == Toolbar.this))
        return false;
      int oldPos = indexOf(incoming);
      if (oldPos < 0)
        return false;
      int newPos = (oldPos < pos) ? pos - 1 : pos;
      return (newPos == oldPos || model.handleDragDrop(oldPos, newPos));
    }

    boolean tryIntraProjectAddition(DropTargetDropEvent e, int pos) throws Exception {
      DataFlavor flavor = model.getAcceptedDataFlavor();
      int action = DnDConstants.ACTION_LINK;
      if ((e.getSourceActions() & action) == 0 || !e.isDataFlavorSupported(flavor) || !e.isDataFlavorSupported(flavor))
        return false;
      e.acceptDrop(action);
      Object incoming = e.getTransferable().getTransferData(flavor);
      return model.handleDrop(incoming, pos);
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      int pos = subpanel.setDropCursor(e.getLocation());
      subpanel.setDropCursor(null);
      try {
        if (pos >= 0 && model.supportsDragDrop() && e.isLocalTransfer()
            && (tryIntraToolbarMove(e, pos) || tryIntraProjectAddition(e, pos))) {
          e.dropComplete(true);
          return;
        }
      } catch (Throwable t) { t.printStackTrace(); }
      e.dropComplete(false);
      e.rejectDrop();
    }

    public void mouseClicked(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
    public void mouseReleased(MouseEvent e) { checkForPopup(e); }
    public void mousePressed(MouseEvent e) { checkForPopup(e); }

    void checkForPopup(MouseEvent e) {
      if (!e.isPopupTrigger())
        return;
      JPopupMenu menu = model.getPopupMenu();
      if (menu != null)
        menu.show(Toolbar.this, e.getX(), e.getY());
    }

  }

	private static final long serialVersionUID = 1L;
	public static final Object VERTICAL = new Object();

	public static final Object HORIZONTAL = new Object();

	private ToolbarModel model;
	private JPanelWithCursor subpanel;
	private Object orientation;
	private MyListener myListener;
	private ToolbarButton curPressed;
  private DropTarget dropTarget;

	public Toolbar(ToolbarModel model) {
		super(new BorderLayout());
		this.subpanel = new JPanelWithCursor();
		this.model = model;
		this.orientation = HORIZONTAL;
		this.myListener = new MyListener();
		this.curPressed = null;
    // this.flavorMap = new FlavorMap();
    this.dropTarget = new DropTarget(this, DnDConstants.ACTION_LINK, myListener, true /* , flavorMap */);

		// this.add(new JPanel(), BorderLayout.CENTER);
		setOrientation(HORIZONTAL);

		subpanel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        Component src = (Component)e.getSource();
        Component parent = src.getParent();
        parent.dispatchEvent(SwingUtilities.convertMouseEvent(src, e, parent));
      }
    });
		addMouseListener(myListener);

		computeContents();
		if (model != null)
			model.addToolbarModelListener(myListener);
	}

	private void computeContents() {
		subpanel.removeAll();
		ToolbarModel m = model;
		if (m != null) {
      subpanel.add(Box.createRigidArea(new Dimension(2, 2))); // for drop cursor
			for (ToolbarItem item : m.getItems()) {
				subpanel.add(new ToolbarButton(this, item));
			}
      subpanel.add(Box.createRigidArea(new Dimension(2, 2))); // for drop cursor
			subpanel.add(Box.createGlue());
		}
		revalidate();
	}

	Object getOrientation() {
		return orientation;
	}

	ToolbarButton getPressed() {
		return curPressed;
	}

	public ToolbarModel getToolbarModel() {
		return model;
	}

	public void setOrientation(Object value) {
		int axis;
		String position;
		if (value.equals(HORIZONTAL)) {
			axis = BoxLayout.X_AXIS;
			position = BorderLayout.LINE_START;
		} else if (value.equals(VERTICAL)) {
			axis = BoxLayout.Y_AXIS;
			position = BorderLayout.NORTH;
		} else {
			throw new IllegalArgumentException();
		}
		this.remove(subpanel);
		subpanel.setLayout(new BoxLayout(subpanel, axis));
		this.add(subpanel, position);
		this.orientation = value;
	}

	void setPressed(ToolbarButton value) {
		ToolbarButton oldValue = curPressed;
		if (oldValue != value) {
			curPressed = value;
			if (oldValue != null)
				oldValue.repaint();
			if (value != null)
				value.repaint();
		}
	}

	public void setToolbarModel(ToolbarModel value) {
		ToolbarModel oldValue = model;
		if (value != oldValue) {
			if (oldValue != null)
				oldValue.removeToolbarModelListener(myListener);
			if (value != null)
				value.addToolbarModelListener(myListener);
			model = value;
			computeContents();
		}
	}

  int indexOf(ToolbarButton b) {
    Component[] children = subpanel.getComponents();
    for (int i = 1; i < children.length - 2; i++) {
      if (subpanel.getComponent(i) == b) {
        return i - 1; // ignore glue at ends
      }
    }
    return -1;
  }
  
  // Extra DragDrop code is needed to check for intra-toolbar drags, while also
  // avoiding calling getTransferable() before the drop. Apparently, during a
  // transfer but before he drop, is e.isLocalTransfer() can wrongly return
  // false, and the JVM may fail while trying to serialize the dragged objects.
  static final Object UUID_FLAVOR = DragDrop.uuidTokenFlavor("toolbar");
  public final String TOOLBAR_TOKEN = DragDrop.uuidToken("toolbar");

}
