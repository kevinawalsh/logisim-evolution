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

package com.cburch.logisim.gui.generic;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JPanel;

import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.tools.AddTool;

/**
 * A floating popup panel added directly to the Canvas (like Callout) that
 * displays a list of AddTool completions matching a typed prefix. Navigation
 * is driven externally by EditTool (keyboard events stay on the canvas).
 */
public class ComponentSearchPopup extends JPanel {

  private static final Color BG_COLOR    = new Color(255, 255, 200);
  private static final Color BORDER_COLOR = Color.DARK_GRAY;
  private static final Color HEADER_FG   = new Color(40, 40, 180);
  private static final Color SELECT_BG   = new Color(100, 160, 255);
  private static final Color SELECT_FG   = Color.WHITE;
  private static final Color ITEM_FG     = Color.BLACK;
  private static final Color NOMATCH_FG  = new Color(140, 140, 140);

  private static final int PADDING_X    = 8;
  private static final int PADDING_Y    = 4;
  private static final int HEADER_EXTRA = 2; // extra space below header divider

  private String searchText = "";
  private List<AddTool> matches = Collections.emptyList();
  private int selectedIndex = 0;
  private final Canvas canvas;

  // Cached metrics
  private int fontAscent;
  private int fontHeight;
  private int headerH;
  private int rowH;

  public ComponentSearchPopup(Canvas canvas, Consumer<AddTool> onSelect) {
    this.canvas = canvas;
    setOpaque(false);
    setLayout(null);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        int row = rowAtY(e.getY());
        if (row >= 0 && row < matches.size()) {
          onSelect.accept(matches.get(row));
        }
      }
    });
  }

  // ----- public API -----

  /** Show the popup at the given canvas-local pixel position. */
  public void showAt(int canvasX, int canvasY) {
    updateSize();
    int w = getPreferredSize().width;
    int h = getPreferredSize().height;

    // Flip horizontally if near right edge
    Rectangle view = canvas.getVisibleRect();
    int x = canvasX + 8;
    if (x + w > view.x + view.width)
      x = canvasX - w - 4;

    // Flip vertically if near bottom edge
    int y = canvasY + 8;
    if (y + h > view.y + view.height)
      y = canvasY - h - 4;

    setBounds(x, y, w, h);
    canvas.add(this);
    canvas.repaint(x - 1, y - 1, w + 2, h + 2);
  }

  /** Update the search text and matching results; reset selection if needed. */
  public void updateSearch(String text, List<AddTool> newMatches) {
    this.searchText = text;
    this.matches = newMatches;
    if (selectedIndex >= matches.size())
      selectedIndex = 0;
    updateSize();
    repaint();
  }

  /** Move the highlighted row up (-1) or down (+1). Wraps around. */
  public void moveSelection(int delta) {
    if (matches.isEmpty()) return;
    selectedIndex = Math.floorMod(selectedIndex + delta, matches.size());
    repaint();
  }

  /** Return the currently highlighted AddTool, or null if none. */
  public AddTool getSelectedTool() {
    if (selectedIndex < matches.size())
      return matches.get(selectedIndex);
    return null;
  }

  /** Remove from canvas and trigger repaint. */
  public void dismiss() {
    Container parent = getParent();
    if (parent != null) {
      Rectangle r = getBounds();
      parent.remove(this);
      parent.repaint(r.x - 1, r.y - 1, r.width + 2, r.height + 2);
    }
  }

  // ----- painting -----

  @Override
  public void paintComponent(Graphics g) {
    FontMetrics fm = g.getFontMetrics();
    fontAscent = fm.getAscent();
    fontHeight = fm.getHeight();
    headerH = fontHeight + PADDING_Y * 2 + HEADER_EXTRA;
    rowH = fontHeight + PADDING_Y * 2;

    int w = getWidth();
    int h = getHeight();

    // Background
    g.setColor(BG_COLOR);
    g.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
    g.setColor(BORDER_COLOR);
    g.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

    // Header: typed text with simulated cursor
    g.setFont(g.getFont().deriveFont(Font.BOLD));
    g.setColor(HEADER_FG);
    String header = "\u00bb " + searchText + "_";
    g.drawString(header, PADDING_X, PADDING_Y + fontAscent);

    // Divider line
    g.setFont(g.getFont().deriveFont(Font.PLAIN));
    g.setColor(BORDER_COLOR);
    g.drawLine(1, headerH - HEADER_EXTRA - 1, w - 2, headerH - HEADER_EXTRA - 1);

    // Rows
    if (matches.isEmpty()) {
      g.setColor(NOMATCH_FG);
      g.drawString("(no matches)", PADDING_X, headerH + PADDING_Y + fontAscent);
    } else {
      for (int i = 0; i < matches.size(); i++) {
        int rowY = headerH + i * rowH;
        if (i == selectedIndex) {
          g.setColor(SELECT_BG);
          g.fillRect(1, rowY, w - 2, rowH);
          g.setColor(SELECT_FG);
        } else {
          g.setColor(ITEM_FG);
        }
        String name = matches.get(i).getDisplayName();
        g.drawString(name, PADDING_X, rowY + PADDING_Y + fontAscent);
      }
    }
  }

  // ----- private helpers -----

  private int rowAtY(int pixelY) {
    if (pixelY < headerH) return -1;
    return (pixelY - headerH) / rowH;
  }

  private void updateSize() {
    // Use a temporary Graphics to measure, or fall back to screen metrics.
    // We use a simple heuristic: average char width * maxLen + padding.
    // Actual painting uses real FontMetrics; this is just for pre-layout.
    Graphics g = canvas.getGraphics();
    if (g == null) {
      setPreferredSize(new Dimension(200, 80));
      return;
    }
    FontMetrics fm = g.getFontMetrics();
    g.dispose();

    fontAscent = fm.getAscent();
    fontHeight = fm.getHeight();
    headerH = fontHeight + PADDING_Y * 2 + HEADER_EXTRA;
    rowH = fontHeight + PADDING_Y * 2;

    // Width: max of header text and all item names
    int maxW = fm.stringWidth("\u00bb " + searchText + "_") + PADDING_X * 2 + 4;
    if (matches.isEmpty()) {
      maxW = Math.max(maxW, fm.stringWidth("(no matches)") + PADDING_X * 2 + 4);
    } else {
      for (AddTool tool : matches) {
        int tw = fm.stringWidth(tool.getDisplayName()) + PADDING_X * 2 + 4;
        if (tw > maxW) maxW = tw;
      }
    }
    maxW = Math.max(maxW, 140);

    int numRows = matches.isEmpty() ? 1 : matches.size();
    int totalH = headerH + numRows * rowH + 2;

    setPreferredSize(new Dimension(maxW, totalH));
    setSize(maxW, totalH);
  }
}
