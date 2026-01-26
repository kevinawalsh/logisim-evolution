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

import java.util.List;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.DragDrop;
import com.cburch.logisim.util.Icons;

public class Toolbar extends JPanel {

  static final Color DROP_CURSOR_COLOR =
      UIManager.getDefaults().getColor("Table.dropLineColor");

  private class JPanelWithCursor extends JPanel {
    int cursorPos = -1; // 1 or higher is valid (ignores box and glue at ends)

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
      int end = getComponentCount() - 2; // ignore box and glue at ends
      if (p != null && end >= 0) {
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
      return cursorPos - 1; // minus one for filler at leading edge
    }

  }
	
  private class OverflowDropTargetListener implements DropTargetListener {

    void checkDrag(DropTargetDragEvent e) {
      // todo: be careful about drag and drop between projects
      try {
        if (overflowWindow != null && model.supportsDragDrop()
            && (checkIntraToolbarMove(e) || checkIntraProjectAddition(e))) {
          overflowPanel.setDropCursor(e.getLocation());
          return;
        }
      } catch (Throwable t) {
        Debug.error("drag-and-drop failure", t);
      }
      overflowPanel.setDropCursor(null);
      mainPanel.setDropCursor(null);
      e.rejectDrag();
    }

    @Override
    public void dragEnter(DropTargetDragEvent e) {
      overflowWindowCloseTimer.stop();
      checkDrag(e);
    }

    @Override
    public void dragOver(DropTargetDragEvent e) {
      overflowWindowCloseTimer.stop();
      checkDrag(e);
    }

    @Override
    public void dragExit(DropTargetEvent e)  {
      overflowPanel.setDropCursor(null);
      overflowWindowCloseTimer.restart();
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent e) {
      checkDrag(e);
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      if (overflowWindow != null) {
        int pos = overflowPanel.setDropCursor(e.getLocation());
        try {
          if (pos >= 0 && model.supportsDragDrop() && e.isLocalTransfer()
              && (tryIntraToolbarMove(e, pos) || tryIntraProjectAddition(e, pos))) {
            e.dropComplete(true);
            migrateOverflowWindowToPopup();
            return;
          }
        } catch (Throwable t) {
          Debug.error("drag-and-drop failure", t);
        }
      }
      e.dropComplete(false);
      e.rejectDrop();
      hideOverflowWindow();
    }

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


  private class MainPanelListener implements MouseListener, ToolbarModelListener, DropTargetListener {
    @Override
		public void toolbarAppearanceChanged(ToolbarModelEvent event) {
			repaint();
		}

    @Override
		public void toolbarContentsChanged(ToolbarModelEvent event) {
			recomputeContents();
		}

    void checkDrag(DropTargetDragEvent e) {
      // todo: be careful about drag and drop between projects
      try {
        if (model.supportsDragDrop()
            && (checkIntraToolbarMove(e) || checkIntraProjectAddition(e))) {
          mainPanel.setDropCursor(e.getLocation());
          return;
        }
      } catch (Throwable t) {
        Debug.error("drag-and-drop failure", t);
      }
      mainPanel.setDropCursor(null);
      e.rejectDrag();
    }

    @Override
    public void dragEnter(DropTargetDragEvent e) {
      overflowWindowCloseTimer.stop();
      if (numVisible != buttons.length && overflowWindow == null) {
        overflowButton.setSelected(true); // note: popup may already be open (e.g. if dragging from popup)
        showOverflowWindow();
      }
      checkDrag(e);
    }

    @Override
    public void dragOver(DropTargetDragEvent e) {
      overflowWindowCloseTimer.stop();
      checkDrag(e);
    }

    @Override
    public void dragExit(DropTargetEvent e) {
      mainPanel.setDropCursor(null);
      overflowWindowCloseTimer.restart();
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent e) {
      checkDrag(e);
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      int pos = mainPanel.setDropCursor(e.getLocation());
      mainPanel.setDropCursor(null);
      try {
        if (pos >= 0 && model.supportsDragDrop() && e.isLocalTransfer()
            && (tryIntraToolbarMove(e, pos) || tryIntraProjectAddition(e, pos))) {
          e.dropComplete(true);
          // migrateOverflowWindowToPopup();
          SwingUtilities.invokeLater(() -> {
            hideOverflowWindow();
            hideOverflowPopup();
          });
          return;
        }
      } catch (Throwable t) {
        Debug.error("drag-and-drop failure", t);
      }
      e.dropComplete(false);
      e.rejectDrop();
    }

    public void mouseClicked(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
    public void mouseReleased(MouseEvent e) { checkForPopup(e); }
    public void mousePressed(MouseEvent e) { checkForPopup(e); }

  }

  boolean checkForPopup(MouseEvent e) {
    if (!e.isPopupTrigger())
      return false;
    JPopupMenu menu = model.getPopupMenu();
    if (menu != null)
      menu.show(Toolbar.this, e.getX(), e.getY());
    return true;
  }

	private static final long serialVersionUID = 1L;

	public static final Object VERTICAL = new Object();
	public static final Object HORIZONTAL = new Object();

	private ToolbarModel model;
	private JPanelWithCursor mainPanel;
	private Object orientation, position;
	private MainPanelListener mainPanelListener;
	private OverflowDropTargetListener overflowDropListener;
	private ToolbarButton curPressed;
  private DropTarget dropTarget, overflowDropTarget;

  private ToolbarButton buttons[]; // placed in mainPanel, or overflowPanel
  private int preferredWidth, preferredHeight;
  private int numVisible = -1;

  private OverflowPanel overflowPanel; // placed in either overflowPopup or overflowWindow
  private OverflowPopup overflowPopup; // used during normal popup scenarios
  private OverflowWindow overflowWindow; // used only during drag-and-drop
  private Timer overflowWindowCloseTimer;
  private OverflowButton overflowButton;
  private static final Icon overflowIcon = Icons.getIcon("overflow.png");
  private static final float OVERFLOW_PANEL_ASPECT_RATIO = 2.5f;

	public Toolbar(ToolbarModel model) {
		super(new BorderLayout());
		this.mainPanel = new JPanelWithCursor();
		this.model = model;
		this.orientation = HORIZONTAL;
    this.position = Direction.NORTH;
		this.mainPanelListener = new MainPanelListener();
		this.overflowDropListener = new OverflowDropTargetListener();
		this.curPressed = null;
    // this.flavorMap = new FlavorMap();
    this.dropTarget = new DropTarget(this, DnDConstants.ACTION_LINK, mainPanelListener, true /* , flavorMap */);

		setPosition(Direction.NORTH); // default, will be overwritten with user prefs

		mainPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        Component src = (Component)e.getSource();
        Component parent = src.getParent();
        parent.dispatchEvent(SwingUtilities.convertMouseEvent(src, e, parent));
      }
    });
		addMouseListener(mainPanelListener);

    overflowPanel = new OverflowPanel();
    overflowButton = new OverflowButton();
    overflowPopup = new OverflowPopup();
    overflowWindowCloseTimer = new Timer(1500, // 1.5 seconds
        e -> hideOverflowWindow());
    overflowWindowCloseTimer.setRepeats(false);
  
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        scheduleToolbarLayout();
      }
    });

		recomputeContents();

    model.addToolbarModelListener(mainPanelListener);
	}

	private void recomputeContents() {
    List<ToolbarItem> items = model.getItems();
    buttons = new ToolbarButton[items.size()];
    for (int i = 0; i < buttons.length; i++)
      buttons[i] = new ToolbarButton(this, i, items.get(i));

    numVisible = -1;
    performToolbarLayout(); // scheduleToolbarLayout();
  }

  private boolean layoutPending = false;
  private void scheduleToolbarLayout() {
    if (layoutPending)
      return;
    layoutPending = true;
    SwingUtilities.invokeLater(() -> {
      layoutPending = false;
      performToolbarLayout();
    });
  }

  // FIXME: upon circuit name change, need to redo layout

  private void performToolbarLayout() {

    if (buttons == null)
      return;

    // Preferably, we want enough space to show all buttons
    if (orientation == HORIZONTAL) {
      preferredWidth = 2 + 2;
      preferredHeight = 0;
      for (int i = 0; i < buttons.length; i++) {
        Dimension dim = buttons[i].getPreferredSize(orientation);
        preferredWidth += dim.width;
        preferredHeight = Math.max(preferredHeight, dim.height);
      }
    } else {
      preferredWidth = 0;
      preferredHeight = 2 + 2;
      for (int i = 0; i < buttons.length; i++) {
        Dimension dim = buttons[i].getPreferredSize(orientation);
        preferredWidth = Math.max(preferredWidth, dim.width);
        preferredHeight += dim.height;
      }
    }

    int availableSize = (orientation == HORIZONTAL ? getWidth() : getHeight());
    int origAvail = availableSize;

    availableSize -= 2; // account for drop-target space at start of toolbar

    int overflowIconSize = (orientation == HORIZONTAL ? overflowIcon.getIconWidth() : overflowIcon.getIconHeight());

    boolean needOverflow = false;
    int fit = 0;
    int missingWidth = 0;
    for (int i = 0; i < buttons.length; i++) {
      Dimension dim = buttons[i].getPreferredSize(orientation);
      int buttonSize = (orientation == HORIZONTAL ? dim.width : dim.height);
      if (!needOverflow && availableSize > buttonSize + (i == buttons.length - 1 ? 2 : overflowIconSize)) {
        fit++;
        availableSize -= buttonSize;
      } else {
        needOverflow = true;
        missingWidth += buttonSize;
        // break;
      }
    }

    if (numVisible == fit)
      return; // no change to overflow

    numVisible = fit;

    mainPanel.removeAll();
    mainPanel.add(Box.createRigidArea(new Dimension(2, 2))); // for drop cursor
    for (int i = 0; i < numVisible; i++) {
      mainPanel.add(buttons[i]);
    }
    if (numVisible != buttons.length) {
      mainPanel.add(overflowButton);
      // mainPanel.add(Box.createRigidArea(new Dimension(missingWidth - overflowIcon.getIconWidth() + 2, 2)));
    } else {
      mainPanel.add(Box.createRigidArea(new Dimension(2, 2))); // for drop cursor
    }
    mainPanel.add(Box.createGlue());
    revalidate();

    overflowPanel.optimizeLayout();
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(10, 10);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(preferredWidth, preferredHeight);
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(preferredWidth, preferredHeight);
  }

  private class OverflowButton extends JToggleButton {

    OverflowButton() {
      super(overflowIcon);

      setFocusable(false);
      setOpaque(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setRolloverEnabled(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setText(null);
      setHorizontalAlignment(SwingConstants.CENTER);
      setVerticalAlignment(SwingConstants.CENTER);
      setAlignmentX(0.5f);
      setAlignmentY(0.5f);
      setToolTipText("More...");

      // Normally, once this button has been clicked and is selected/depressed, the associated popup
      // will be showing. Normally, if the user next clicks *any* button outside the popup, or any
      // swing component at all outside the popup, then Swing's event handling logic internally will
      // internally cancel the popup. Code below listens for this popup-cancellation event and
      // resets this button to unselected. That is the desired behavior in all cases *except* one:
      // suppose, while this button is selected/depressed and the popup is showing, if the user were
      // to click this button again. First, Swing's internal event handling logic detects the click
      // and logic cancels the popup. The code below would then reset this button. But then event
      // handling continues, and this button gets the button press, re-selecting the button and
      // re-showing the popup. The (undocumented, but apparently canonical) way to avoid this is to
      // set a client property on this JButton, which serves to inform Swing's internal event logic
      // that mouse clicks within this button should be exempt from the normal "cancel popups on
      // mouse events that are outside the popup window" rule.
      //
      // See:
      //  https://explodingpixels.wordpress.com/2008/11/10/prevent-popup-menu-dismissal/
      //  https://bugs.openjdk.org/browse/JDK-6350814
      
      // (Part 1 - overflow popup logic)
      // Disable Swing's built-in popup-cancel hook for events on this button.
      Object preventHide = (new JComboBox()).getClientProperty("doNotCancelPopup");
      putClientProperty("doNotCancelPopup", preventHide);

      // (Part 2 - overflow popup logic)
      // Listen for mouse clicks explicitly, hide or show popup in response.
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (!SwingUtilities.isLeftMouseButton(e))
            return;
          if (isSelected()) // overflowPopup.isShowing()
            hideOverflowPopup();
          else
            showOverflowPopup();
        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(overflowIcon.getIconWidth(), overflowIcon.getIconHeight());
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
      ButtonModel m = getModel();

      boolean showChrome = m.isRollover() || m.isPressed() || isSelected() || overflowPopup.isShowing();

      if (showChrome) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          int w = getWidth(), h = getHeight();
          int arc = Math.min(h, 10);

          boolean pressedLike = isSelected() || (m.isArmed() && m.isPressed());

          Color fill   = pressedLike ? new Color(0, 0, 0, 35) : new Color(0, 0, 0, 20);
          Color border = new Color(0, 0, 0, 55);

          g2.setColor(fill);
          g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

          g2.setColor(border);
          g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        } finally {
          g2.dispose();
        }
      }

      super.paintComponent(g);
    }
  }

  private Point popupOffset(Component anchor, Component popup) {
    Dimension popupDim = popup.getPreferredSize();
    int pw = popupDim.width;
    int ph = popupDim.height;
    int aw = anchor.getWidth();
    int ah = anchor.getHeight();
    if (position == Direction.NORTH)
      return new Point(aw - pw, ah);
    else if (position == Direction.SOUTH)
      return new Point(aw - pw, -ph);
    else if (position == Direction.EAST)
      return new Point(-pw, ah - ph);
    else // WEST or TOOLBAR_DOWN_MIDDLE
      return new Point(aw, ah - ph);
  }

  void showOverflowPopup() {
    if (overflowPopup.isVisible()) // sanity check
      return;
    if (overflowWindow != null)
       hideOverflowWindow();

    overflowPopup.removeAll();
    overflowPopup.add(overflowPanel);
    overflowPopup.pack();

    Point offset = popupOffset(overflowButton, overflowPopup);
    overflowPopup.show(overflowButton, offset.x, offset.y);
  }

  void showOverflowWindow() {
    if (overflowWindow != null) // sanity check
      return;
    if (overflowPopup.isVisible())
      hideOverflowPopup();

    overflowWindowCloseTimer.stop();
    overflowWindow = new OverflowWindow(overflowButton);
  }

  // (Part 3 - overflow popup logic)
  // When hiding popup programmatically, tell popup to not sync with button.
  void hideOverflowPopup() {
    overflowPopup.deselectButtonWhenDisappearing = false;
    overflowPopup.setVisible(false);
    overflowPopup.deselectButtonWhenDisappearing = true;
    overflowPopup.removeAll();
  }

  void hideOverflowWindow() {
    overflowWindowCloseTimer.stop();
    if (overflowWindow != null) {
      overflowWindow.setVisible(false);
      overflowWindow.removeAll();
      overflowWindow = null;
      overflowButton.setSelected(false);
      overflowPanel.setDropCursor(null);
    }
  }

  void migrateOverflowPopupToWindow() {
    hideOverflowPopup();
    showOverflowWindow();
  }

  void migrateOverflowWindowToPopup() {
    hideOverflowWindow();
    SwingUtilities.invokeLater(() -> {
      overflowButton.setSelected(true);
      showOverflowPopup();
    });
  }
  
  private class OverflowPopup extends JPopupMenu {

    boolean deselectButtonWhenDisappearing = true;

    OverflowPopup() {
      setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 80)));

      addPopupMenuListener(new PopupMenuListener() {

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
          // (Part 4 - overflow popup logic) When hiding popup due to various events, deselect the
          // button so it stays in sync with the visible state of the popup. But don't sync if the
          // hiding-deselecting is already in progress, i.e. if we are hiding the popup
          // programmatically as part of the button click handler.
          if (deselectButtonWhenDisappearing) 
            overflowButton.setSelected(false);
          overflowPopup.removeAll();
        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
          // (Part 5 - overflow popup logic) Swing's event handling is canceling this popup, so
          // we do want to sync the button state here.
          overflowButton.setSelected(false);
        }
      });
    }

  }



  private class OverflowWindow extends JWindow {

    private final DropTarget dropTarget;

    public OverflowWindow(Component anchor) {
      super(SwingUtilities.getWindowAncestor(Toolbar.this));
      setType(Window.Type.POPUP);
      setFocusableWindowState(false);
      // setAlwaysOnTop(true);

      getContentPane().setLayout(new BorderLayout());
      getContentPane().add(overflowPanel, BorderLayout.CENTER);

      pack();

      Point p = popupOffset(anchor, this);
      SwingUtilities.convertPointToScreen(p, anchor);
      setLocation(p);

      dropTarget = new DropTarget(this, DnDConstants.ACTION_LINK, overflowDropListener, true /* , flavorMap */);

      setVisible(true);
    }

  }


  private class OverflowPanel extends JPanel {
    int overflow, rows[];
    Dimension boxes[];
    int width, height;
    int cursorRow = -1;  // 0 or higher is valid
    int cursorCol = -1; // 0 or higher is valid
    
    OverflowPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
      setOpaque(true);
      setBackground(UIManager.getColor("Panel.background"));
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      if (cursorRow >= 0) {
        JPanel row = (JPanel)getComponent(cursorRow);
        Point rp = row.getLocation();
        Component btn = row.getComponent(cursorCol+1);
        Point bp = btn.getLocation();
        g.setColor(DROP_CURSOR_COLOR);
        g.fillRect(rp.x+bp.x-1, rp.y + 2, 3, row.getHeight()-4);
      }
    }

    int setDropCursor(Point p) {
      int pos = -1; // 0 or higher is valid
      int newRow = -1, newCol = -1;
      int end = getComponentCount();
      if (p != null && end > 0) {
        pos = numVisible;
        Component[] children = getComponents();
        for (newRow = 0; newRow < end-1; newRow++) {
          JPanel row = (JPanel)getComponent(newRow);
          Rectangle r = row.getBounds();
          if (p.y < r.y + r.height)
            break;
          pos += rows[newRow];
        }
        JPanel row = (JPanel)getComponent(newRow);
        int rowx = row.getBounds().x;
        end = row.getComponentCount() - 2; // ignore box and glue at end
        for (newCol = 0; newCol+1 < end; newCol++) {
          Component c = row.getComponent(newCol+1);
          Rectangle r = c.getBounds();
          if (p.x < rowx + r.x + r.width/2)
            break;
        }
        pos += newCol;
      }
      if (newRow != cursorRow || newCol != cursorCol) {
        cursorRow = newRow;
        cursorCol = newCol;
        repaint();
      }
      return pos;
    }

    void optimizeLayout() {
      removeAll();
      int n = buttons.length;
      overflow = n - numVisible;
      if (overflow <= 0) {
        width = 50;
        height = 50;
        rows = new int[0];
        return;
      }
     
      boxes = new Dimension[overflow];
      for (int i = 0; i < overflow; i++)
        boxes[i] = buttons[numVisible + i].getPreferredSize(HORIZONTAL);

      int minWidth = 0;
      for (int i = 0; i < overflow; i++)
        minWidth = Math.max(minWidth, 2 + boxes[i].width + 2); // every row has 2 px drop-targets on edges
      

      // try a single row
      rows = new int[] { overflow };
      int w = 2 + 2;
      for (int i = 0; i < overflow; i++)
        w += boxes[i].width;

      // get aspect ratio, for a single row
      int h = 1;
      for (int i = 0; i < overflow; i++)
        h = Math.max(h, boxes[i].height);
      float aspect = w / h;
        
      // The widest button, with spacing, requires minWidth, so a layout with width=minWidth is
      // achievable, given enough rows (e.g. we could just use enough rows so every button gets a
      // row).

      while (aspect > OVERFLOW_PANEL_ASPECT_RATIO && w > minWidth) {
        // Invariant: layout fits in n=rows.length rows, with longest row(s) having width=w.
        // And since w > minWidth, if we add one or more additional rows, we
        // can always to get to a smaller width.
        // First, figure out how many new rows we need to add. Often just one
        // more row will allow the longest row to be split, making the layout less wide, i.e. at
        // most width=w-1 or smaller. But sometimes there are several equally long rows that all
        // must be split, requiring multiple new rows.
        int better;
        do {
          rows = new int[rows.length + 1];
          better = squishInto(rows, w - 1); // try to achieve width=w-1 or narrower
        } while (better < 0);
        // We have now found, using more rows, a narrower layout with width=better.
        w = better;
        // Can we make it even more narrow, with the same number of rows?
        while (w > minWidth) {
          better = squishInto(rows, w - 1);
          if (better < 0) {
            // Nope, w is lowest possible width given this number or rows, so
            // revert back to the layout with width=w and stop trying.
            int fit = squishInto(rows, w);
            break;
          } else {
            // Yes, a layout with width=better was found.
            w = better;
          }
        }
        h = 0;
        int b = 0;
        for (int r = 0; r < rows.length; r++) {
          int rh = 0;
          for (int i = 0; i < rows[r]; i++)
            rh = Math.max(rh, boxes[b++].height);
          h += rh;
        }
        aspect = w / h;
      }

      height = h;
      width = w;

      int b = numVisible;
      for (int r = 0; r < rows.length; r++) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(Box.createRigidArea(new Dimension(2, 2))); // for drop cursor
        for (int i = 0; i < rows[r]; i++)
          row.add(buttons[b++]);
        row.add(Box.createRigidArea(new Dimension(2, 2))); // for drop cursor
        row.add(Box.createGlue());
        add(row);
      }

    }

    int squishInto(int rows[], int maxW) {
      int r = 0;
      rows[r] = 0;
      int rw = 4;
      int widest = 0;
      for (int i = 0; i < overflow; i++) {
        if (rw + boxes[i].width <= maxW) {
          rw += boxes[i].width;
          rows[r]++;
        } else if (r >= rows.length - 1 || 2 + boxes[i].width + 2 > maxW) {
          return -1;
        } else {
          r++;
          rows[r] = 1;
          rw = 2 + boxes[i].width + 2;
        }
        widest = Math.max(widest, rw);
      }
      return widest;
    }
  }

  boolean isOverflowButton(int btnPosition) {
    return (btnPosition >= numVisible);
  }

	Object getOrientation(int btnPosition) {
    return (btnPosition >= numVisible) ? HORIZONTAL : orientation;
	}

	ToolbarButton getPressed() {
		return curPressed;
	}

	public ToolbarModel getToolbarModel() {
		return model;
	}

	public void setPosition(Object pos) {
		int axis;
		String anchor;
    if (pos == Direction.NORTH || pos == Direction.SOUTH) {
			axis = BoxLayout.X_AXIS;
			anchor = BorderLayout.LINE_START;
      orientation = HORIZONTAL;
		} else if (pos == Direction.EAST || pos == Direction.WEST || pos == AppPreferences.TOOLBAR_DOWN_MIDDLE) {
			axis = BoxLayout.Y_AXIS;
			anchor = BorderLayout.NORTH;
      orientation = VERTICAL;
		} else {
			throw new IllegalArgumentException();
		}
    this.position = pos;
		this.remove(mainPanel);
		mainPanel.setLayout(new BoxLayout(mainPanel, axis));
		this.add(mainPanel, anchor);
    performToolbarLayout();
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

  boolean completeButtonPress(MouseEvent e, ToolbarButton value) {
    if (checkForPopup(e) || curPressed != value) {
      return false;
    } else {
			setPressed(null);
      overflowButton.setSelected(false);
      hideOverflowPopup();
      return true;
    }
  }

	public void setToolbarModel(ToolbarModel value) {
		ToolbarModel oldValue = model;
		if (value != oldValue) {
      oldValue.removeToolbarModelListener(mainPanelListener);
      value.addToolbarModelListener(mainPanelListener);
			model = value;
			recomputeContents();
		}
	}

  int indexOf(ToolbarButton b) {
    for (int i = 0; i < buttons.length; i++)
      if (buttons[i] == b)
        return i;
    return -1;
  }
  
  // Extra DragDrop code is needed to check for intra-toolbar drags, while also
  // avoiding calling getTransferable() before the drop. Apparently, during a
  // transfer but before he drop, is e.isLocalTransfer() can wrongly return
  // false, and the JVM may fail while trying to serialize the dragged objects.
  static final Object UUID_FLAVOR = DragDrop.uuidTokenFlavor("toolbar");
  public final String TOOLBAR_TOKEN = DragDrop.uuidToken("toolbar");

}
