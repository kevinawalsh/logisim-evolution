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
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;

public class SimulationTreeRenderer extends DefaultTreeCellRenderer {

  private static final int ICON_SIZE = 20;

  private static class RendererIcon implements Icon {
    private ComponentFactory factory;

    RendererIcon(ComponentFactory factory) { this.factory = factory; }

    public int getIconHeight() { return ICON_SIZE; }
    public int getIconWidth() { return ICON_SIZE; }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      ComponentDrawContext context = new ComponentDrawContext(c, null, null, g, g);
      factory.paintIcon(context, x, y, factory.createAttributeSet());
    }
  }

  private static final long serialVersionUID = 1L;

  private Font plainFont, boldFont;
  private int availableWidth, indentPerLevel;

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object value,
      boolean selected, boolean expanded, boolean leaf, int row,
      boolean hasFocus) {

    super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus); // returns this

    if (plainFont == null) {
      plainFont = getFont();
      boldFont = new Font(plainFont.getFontName(), Font.BOLD, plainFont.getSize());
    }

    setFont(plainFont);
    setToolTipText(null);
    setOpaque(false);

    if ((value instanceof SimulationTreeNode)) {
      SimulationTreeNode node = (SimulationTreeNode) value;

      ComponentFactory factory = node.getComponentFactory();
      if (factory != null) {
        setIcon(factory != null ? new RendererIcon(factory) : null);
        setToolTipText(S.fmt("simulationToolTip", factory.getDisplayName()));
      }
    }

    if (selected)
      setFont(boldFont);

    Container p = tree.getParent();
    availableWidth = (p instanceof JViewport) ? ((JViewport)p).getWidth() : tree.getVisibleRect().width;

    if (availableWidth <= 0)
      availableWidth = Integer.MAX_VALUE;

    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      if (indentPerLevel == 0)
        indentPerLevel = indentPerLevel(tree);
      int depth = path.getPathCount();
      availableWidth -= depth * indentPerLevel(tree);
    }

    return this;
  }

  @Override
  protected void paintComponent(Graphics g) {

    String text = getText();

    // Paint everything but text: icon, background, etc.
    setText("");
    super.paintComponent(g);
    setText(text);

    Insets in = getInsets();
    int x = in.left;
    Icon icon = getIcon();
    if (icon != null)
      x += icon.getIconWidth() + getIconTextGap();

    java.awt.FontMetrics fm = g.getFontMetrics(getFont());
    int y = in.top + fm.getAscent();

    int padding = 3;
    int availableTextWidth = availableWidth - x - padding;
    if (availableTextWidth >= 0)
      text = ellipsize(fm, text, availableTextWidth);

    g.setFont(getFont());
    g.setColor(getForeground());
    g.drawString(text, x, y);
  }

  private static int indentPerLevel(JTree tree) {
    TreeUI ui = tree.getUI();
    if (ui instanceof BasicTreeUI)
      return ((BasicTreeUI)ui).getLeftChildIndent() + ((BasicTreeUI)ui).getRightChildIndent();
    else
      return 20;
  }

  private static String ellipsize(FontMetrics fm, String s, int maxWidth) {
    if (fm.stringWidth(s) <= maxWidth)
      return s;

    final String ellipsis = "\u2026"; // ellipsis
    int ellW = fm.stringWidth(ellipsis);
    if (ellW > maxWidth)
      return ellipsis;

    int lo = 0, hi = s.length();
    // Find max prefix length that fits with ellipsis
    while (lo < hi) {
      int mid = (lo + hi + 1) >>> 1;
      String candidate = s.substring(0, mid) + ellipsis;
      if (fm.stringWidth(candidate) <= maxWidth) lo = mid;
      else hi = mid - 1;
    }
    return s.substring(0, lo) + ellipsis;
  }

}
