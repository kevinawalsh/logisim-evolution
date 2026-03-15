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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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

  // If search has n <= MAX_ITEMS, all n of them will be displayed in popop.
  private static final int MAX_ITEMS = 20;
  // Otherwise... something else.
  // - maybe the first TRUNCATE_ITEMS of them will be displayed, with
  //   a message saying "(k more results)" with k = n - TRUNCATE_ITEMS
  // - or show a small arrow at bottom, and allow up/down arrows to scroll the list?
  // - or just a "..."
  private static final int TRUNCATE_ITEMS = 10;

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

  private static final Cursor HAND_CURSOR =
      Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  private static final Cursor DEFAULT_CURSOR =
      Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

  private String searchText = "";
  private List<AddTool> matches = Collections.emptyList();
  private int selectedIndex = 0;
  private final Canvas canvas;
  private boolean flipped = false; // true when popup is above the cursor
  private int anchorX, anchorY;   // canvas-local cursor position at showAt()

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
      @Override
      public void mouseExited(MouseEvent e) {
        setCursor(DEFAULT_CURSOR);
      }
    });
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = rowAtY(e.getY());
        if (row >= 0 && row < matches.size()) {
          setCursor(HAND_CURSOR);
          if (row != selectedIndex) {
            selectedIndex = row;
            repaint();
          }
        } else {
          setCursor(DEFAULT_CURSOR);
        }
      }
    });
  }

  // ----- public API -----

  /** Show the popup at the given canvas-local pixel position. */
  public void showAt(int canvasX, int canvasY) {
    anchorX = canvasX;
    anchorY = canvasY;
    updateSize();
    int w = getPreferredSize().width;
    int h = getPreferredSize().height;

    Rectangle view = canvas.getVisibleRect();

    // Flip horizontally if near right edge
    int x = canvasX + 8;
    if (x + w > view.x + view.width)
      x = canvasX - w - 4;

    // Flip vertically if near bottom edge; header follows the cursor
    flipped = (canvasY + 8 + h > view.y + view.height);
    int y = flipped ? canvasY - h - 4 : canvasY + 8;

    setBounds(x, y, w, h);
    canvas.add(this);
    canvas.repaint(x - 1, y - 1, w + 2, h + 2);
  }

  /** Update the search text and matching results; reset selection if needed. */
  public void updateSearch(String text, List<AddTool> newMatches) {
    this.searchText = text;
    if (newMatches.size() <= MAX_ITEMS)
      this.matches = newMatches;
    else
      this.matches = newMatches.subList(0, MAX_ITEMS); // FIXME
    if (selectedIndex >= matches.size())
      selectedIndex = matches.size() - 1;
    updateSize();
    if (flipped) {
      // Re-anchor: keep the bottom of the popup close to the cursor
      int h = getPreferredSize().height;
      setLocation(getX(), anchorY - h - 4);
    }
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

    int headerTop = flipped ? h - headerH : 0;
    int itemsTop  = flipped ? 0           : headerH;
    int dividerY  = flipped ? h - headerH : headerH - HEADER_EXTRA - 1;

    // Header: typed text with simulated cursor
    g.setFont(g.getFont().deriveFont(Font.BOLD));
    g.setColor(HEADER_FG);
    String header = "\u00bb " + searchText + "_";
    g.drawString(header, PADDING_X, headerTop + PADDING_Y + fontAscent);

    // Divider line
    g.setFont(g.getFont().deriveFont(Font.PLAIN));
    g.setColor(BORDER_COLOR);
    g.drawLine(1, dividerY, w - 2, dividerY);

    // Rows
    if (matches.isEmpty()) {
      g.setColor(NOMATCH_FG);
      g.drawString("(no matches)", PADDING_X, itemsTop + PADDING_Y + fontAscent);
    } else {
      for (int i = 0; i < matches.size(); i++) {
        int rowY = itemsTop + i * rowH;
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
    if (!flipped) {
      if (pixelY < headerH) return -1;
      return (pixelY - headerH) / rowH;
    } else {
      int row = pixelY / rowH;
      if (row >= matches.size()) return -1;
      return row;
    }
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
