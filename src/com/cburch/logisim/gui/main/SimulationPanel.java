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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.gui.menu.Popups;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Icons;

import static com.cburch.logisim.gui.menu.Strings.S;

class SimulationPanel extends JPanel implements MouseListener {

  private final CircuitState root;
  private final SimulationTreeModel model;
  private final JTree tree;

  public SimulationPanel(CircuitState root) {
    super(new BorderLayout());
    this.root = root;
    this.model = new SimulationTreeModel(root);
    this.tree = new JTree(model);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setScrollsOnExpand(false); // helps keep width sane
    tree.setCellRenderer(new SimulationTreeRenderer());
    // Use single-click to select (i.e. view circuit simulation) in the simulation epxlorer pane.
    // Disable toggle-via-double-click, as it interfers with click-to-select. Toggle can be done
    // with the arrow icon instead.
    tree.setToggleClickCount(0);
    tree.addMouseListener(this);

    ToolTipManager.sharedInstance().registerComponent(tree);

    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
   
    tree.addTreeSelectionListener(e -> {
      Object last = tree.getLastSelectedPathComponent();
      if (last instanceof SimulationTreeNode) {
        CircuitState cs = ((SimulationTreeNode)last).getCircuitState();
        if (cs != null)
          cs.getProject().setCircuitState(cs);
      }
    });

    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(1, 6, 6, 1));
    // setBorder(BorderFactory.createLineBorder(Color.RED, 1));

    String title = "Simulation " + root.getId();
    add(buildHeader(title), BorderLayout.NORTH);
    add(buildTreeArea(), BorderLayout.CENTER);

    setAlignmentX(Component.LEFT_ALIGNMENT); // also gives full width

    tree.expandRow(0);

    installAutoHeight(tree);
    SwingUtilities.invokeLater(this::updateTreePreferredHeight);
  }

  void setCurrentView(CircuitState cs) {
    if (cs == null) {
      tree.clearSelection();
    } else {
      TreePath path = model.mapToPath(cs);
      tree.setSelectionPath(path);
    }
    SwingUtilities.invokeLater(tree::repaint);
    SwingUtilities.invokeLater(this::repaint); // in case focus-border changed
  }

  private JComponent buildHeader(String title) {
    JPanel header = new JPanel(new BorderLayout());
    header.setOpaque(false);

    JLabel label = new JLabel(title);
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Font base = label.getFont();
    label.setFont(base.deriveFont(base.getStyle() | Font.ITALIC, base.getSize2D() * 0.8f)); // small, italic

    JButton reset = new HeaderIconButton(Icons.getIcon("reset.png"));
    // reset.setFocusable(false);
    // reset.setBorderPainted(false);
    // reset.setContentAreaFilled(false);
    // reset.setOpaque(false);
    // reset.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    reset.setToolTipText(S.get("circuitStateReset"));
    reset.addActionListener((ActionEvent e) -> {
      Project proj = root.getProject();
      Simulator sim = proj.getSimulator();
      CircuitState active = proj.getCircuitState();
      if (active == root || active.hasAncestorState(root)) {
        sim.reset(); // let the propagator thread do the reset
        proj.repaintCanvas();
      } else {
        root.reset(); // no propagator, we can do the reset
      }
    });

    JButton delete = new HeaderIconButton(Icons.getIcon("close.png"));
    // delete.setFocusable(false);
    // delete.setBorderPainted(false);
    // delete.setContentAreaFilled(false);
    // delete.setOpaque(false);
    // delete.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    delete.setToolTipText(S.get("circuitStateDelete"));
    delete.addActionListener((ActionEvent e) -> root.getProject().removeCircuitStateAncestor(root));

    JPanel buttons = new JPanel(new BorderLayout(2, 2));
    buttons.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    buttons.setOpaque(false);
    buttons.add(reset, BorderLayout.WEST);
    buttons.add(delete, BorderLayout.EAST);

    header.add(label, BorderLayout.WEST);
    header.add(buttons, BorderLayout.EAST);

    return header;
  }

  private JComponent buildTreeArea() {
    JPanel wrap = new JPanel(new BorderLayout());
    wrap.setOpaque(false);
    wrap.add(tree, BorderLayout.CENTER);
    return wrap;
  }

  private void installAutoHeight(JTree tree) {
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override public void treeExpanded(TreeExpansionEvent event) { updateTreePreferredHeight(); }
      @Override public void treeCollapsed(TreeExpansionEvent event) { updateTreePreferredHeight(); }
      });

    TreeModel m = tree.getModel();
    if (m != null) {
      m.addTreeModelListener(new TreeModelListener() {
        @Override public void treeNodesChanged(TreeModelEvent e) { updateTreePreferredHeight(); }
        @Override public void treeNodesInserted(TreeModelEvent e) { updateTreePreferredHeight(); }
        @Override public void treeNodesRemoved(TreeModelEvent e) { updateTreePreferredHeight(); }
        @Override public void treeStructureChanged(TreeModelEvent e) { updateTreePreferredHeight(); }
        });
    }
  }

  @Override
  public Dimension getMaximumSize() {
    Dimension pref = getPreferredSize();
    return new Dimension(Integer.MAX_VALUE, pref.height);
  }

  private void updateTreePreferredHeight() {
    int rows = tree.getRowCount();
    int height = 0;

    if (rows > 0) {
      Rectangle last = tree.getRowBounds(rows - 1);
      if (last != null) height = last.y + last.height;
    }

    Dimension pref = tree.getPreferredSize();
    tree.setPreferredSize(new Dimension(pref != null ? pref.width : 0, height));
    tree.setMinimumSize(new Dimension(1, 0));

    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    // Rounded card background + border
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int arc = 12;
      int w = getWidth();
      int h = getHeight();

      Color fill = tree.getBackground();
      Color border = tree.getSelectionCount() == 0 ? fill.darker() : getFocusBorderColor();

      g2.setColor(fill);
      g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

      g2.setColor(border);
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
    } finally {
      g2.dispose();
    }
    super.paintComponent(g);
  }
  
  public void mouseClicked(MouseEvent e) { }
  public void mouseEntered(MouseEvent e) { }
  public void mouseExited(MouseEvent e) { }

  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger())
      doPopup(e);
  }

  public void mouseReleased(MouseEvent e) {
    if (e.isPopupTrigger()) 
      doPopup(e);
  }

  private static Color getFocusBorderColor() {
    Color c;
    c = UIManager.getColor("Focus.color");
    if (c != null) return c;
    c = UIManager.getColor("Component.focusColor");
    if (c != null) return c;
    c = UIManager.getColor("TextField.focusedBorderColor");
    if (c != null) return c;
    return new Color(0x4A90E2);
  }


  private void doPopup(MouseEvent e) {
      SwingUtilities.convertMouseEvent(this, e, tree);

      int row = tree.getRowForLocation(e.getX(), e.getY());
      if (row < 0)
        return;
      TreePath path = tree.getPathForRow(row);
      if (path == null)
        return;
      Object last = path.getLastPathComponent();
      if (!(last instanceof SimulationTreeNode))
        return;
      CircuitState cs = ((SimulationTreeNode)last).getCircuitState();
      if (cs == null)
        return;

      tree.setSelectionPath(path);
      JPopupMenu menu = Popups.forCircuitState(cs);
      if (menu != null)
        menu.show(tree, e.getX(), e.getY());
  }

  private static class HeaderIconButton extends JButton {

    HeaderIconButton(Icon icon) {
      super(icon);
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
    }

    @Override
    protected void paintComponent(Graphics g) {
      ButtonModel m = getModel();
      boolean showChrome = m.isRollover() || (m.isArmed() && m.isPressed());

      if (showChrome) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          int w = getWidth(), h = getHeight();
          int arc = Math.min(h, 10);

          boolean pressed = (m.isArmed() && m.isPressed());

          Color fill = pressed ? new Color(0, 0, 0, 35) : new Color(0, 0, 0, 20);
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

    @Override
    public Dimension getPreferredSize() {
      int s = Math.max(Math.max(getIcon().getIconWidth()+4, getIcon().getIconHeight()+4), 14);
      return new Dimension(s, s);
    }
  }

}

