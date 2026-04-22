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
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.gui.generic;
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Consumer;

import java.awt.font.TextAttribute;
import java.text.AttributedString;
import javax.swing.JPanel;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * A floating popup panel added directly to the Canvas (like Callout) that
 * displays a scrollable list of AddTool completions matching a typed prefix.
 * The visible window size is computed from available canvas space. When
 * matches exceed the window, triangle scroll indicators appear at the edges
 * of the item list. Navigation is driven externally via the public API
 * (keyboard events stay on the canvas).
 */
public class ComponentSearchPopup extends JPanel {

  // Maximum rows to show when space is plentiful
  private static final int MAX_VISIBLE = 15;
  // Threshold to switch to reversed orientation
  private static final int OK_VISIBLE = 6;
  // Minimum rows to show even when space is very tight
  private static final int MIN_VISIBLE = 3;
  // Height (px) of the triangle scroll-indicator rows
  private static final int INDICATOR_H  = 7;

  // Colours
  private static final Color BG_COLOR        = new Color(255, 255, 200);
  private static final Color BORDER_COLOR    = Color.DARK_GRAY;
  private static final Color HEADER_FG       = new Color(40, 40, 180);
  private static final Color HEADER_BG       = new Color(224, 255, 249);
  private static final Color SELECT_BG       = new Color(100, 160, 255);
  private static final Color SELECT_FG       = Color.WHITE;
  private static final Color ITEM_FG         = Color.BLACK;
  private static final Color NOMATCH_FG      = new Color(140, 140, 140);
  private static final Color TRI_ACTIVE_FG   = new Color(100, 100, 100);
  private static final Color TRI_INACTIVE_FG = Color.GRAY;

  // Layout constants
  private static final int PADDING_X    = 8;
  private static final int PADDING_Y    = 4;
  private static final int HEADER_EXTRA = 0; // extra gap below header divider

  // Cursors
  private static final Cursor HAND_CURSOR    = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  private static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

  // Special return values from rowAtY()
  private static final int ROW_NONE     = -1;
  private static final int ROW_UP_IND   = -2;
  private static final int ROW_DOWN_IND = -3;

  // Search state
  private String searchText = "";
  private ArrayList<AddTool> allTools = new ArrayList<>();
  private ArrayList<Match> matches = new ArrayList<>();
  private HashMap<AddTool, String> displayNames = new HashMap<>();
  private int selectedIndex = 0;
  private int scrollOffset  = 0;
  private int windowSize    = MIN_VISIBLE; // max visible rows; set in showAt()

  // Position / orientation
  private final Canvas canvas;
  private boolean flipped = false; // true when popup is above the cursor
  private int anchorY;             // canvas-local pixel y at showAt()

  // Cached font metrics (set in initMetrics() and refreshed in paintComponent())
  private int fontAscent;
  private int fontHeight;
  private int headerH;  // height of the header row
  private int rowH;     // height of each item row

  private static class Match {
    final AddTool tool;
    final int start; // index within displayNames.get(tool) where match was made
    Match(AddTool t, int s) {
      tool = t; start = s;
    }
  }


  // -----------------------------------------------------------------------

  // initialChar should not be whitespace, but anything else is okay
  public ComponentSearchPopup(Canvas canvas, char initialChar, Consumer<AddTool> onSelect) {
    this.canvas = canvas;
    setOpaque(false);
    setLayout(null);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        int hit = rowAtY(e.getY());
        if (hit == ROW_UP_IND) {
          scroll(-1);
        } else if (hit == ROW_DOWN_IND) {
          scroll(1);
        } else if (hit >= 0 && hit < matches.size()) {
          onSelect.accept(matches.get(hit).tool);
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
        int hit = rowAtY(e.getY());
        if (hit == ROW_UP_IND || hit == ROW_DOWN_IND) {
          setCursor(HAND_CURSOR);
        } else if (hit >= 0 && hit < matches.size()) {
          setCursor(HAND_CURSOR);
          if (hit != selectedIndex) {
            selectedIndex = hit;
            repaint();
          }
        } else {
          setCursor(DEFAULT_CURSOR);
        }
      }
    });

    addMouseWheelListener(e -> scroll(e.getWheelRotation() > 0 ? 1 : -1));

    // find all relevant tools
    String prefix = String.valueOf(initialChar);
    LogisimFile file = canvas.getProject().getLogisimFile();
    HashMap<AddTool, Match> allMatches = new HashMap<>();
    for (Tool tool : file.getToolsAndSublibraryTools()) {
      if (tool instanceof AddTool) {
        AddTool addTool = (AddTool) tool;
        // prefix is one char, we only check tool name here, not lib name suffix
        Match m = keywordMatch(addTool, prefix);
        if (m != null) {
          allTools.add(addTool);
          allMatches.put(addTool, m);
        }
      }
    }

    // If any of the tools have the same name, add suffix to disambiguate
    HashMap<String, AddTool> rev = new HashMap<>();
    for (AddTool tool : allTools) {
      String name = tool.getDisplayName();
      if (!rev.containsKey(name)) {
        rev.put(name, tool);
        displayNames.put(tool, name);
      } else {
        // name clash, append suffix
        Library lib = canvas.getProject().getLogisimFile().findLibraryFor(tool.getFactory());
        String newName = name + " in " + lib.getDisplayName();
        rev.put(newName, tool);
        displayNames.put(tool, newName);
        // fixup the previous tool, if not already done
        tool = rev.get(name);
        rev.put(name, null);
        if (tool != null) {
          lib = canvas.getProject().getLogisimFile().findLibraryFor(tool.getFactory());
          newName = tool.getDisplayName() + " in " + lib.getDisplayName();
          rev.put(newName, tool);
          displayNames.put(tool, newName);
        }
      }
    }

    Collections.sort(allTools,
        (a, b) -> displayNames.get(a).compareToIgnoreCase(displayNames.get(b)));

    searchText = prefix;
    for (AddTool tool : allTools)
      matches.add(allMatches.get(tool));
    matchesUpdated();
  }

  // ----- public API -----

  /** Show the popup anchored to the given canvas model coordinate position. */
  public void showAt(int canvasX, int canvasY) {
    double zoom = canvas.getZoomFactor();
    int screenX  = (int) Math.round(canvasX * zoom);
    int screenY  = (int) Math.round(canvasY * zoom);
    anchorY     = screenY;
    scrollOffset = 0;
    initMetrics();
    computeWindowSize();  // sets flipped and windowSize
    refreshLayout();      // sets preferred/actual size

    Rectangle view = canvas.getVisibleRect();
    int w = getPreferredSize().width;
    int h = getPreferredSize().height;

    int x = screenX + 8;
    if (x + w > view.x + view.width)
      x = screenX - w - 4;

    int y = flipped ? screenY - h - 4 : screenY + 8;

    setBounds(x, y, w, h);
    canvas.add(this);
    canvas.repaint(x - 1, y - 1, w + 2, h + 2);
  }

  /** Tab completion. */
  public String tabComplete() {
    if (matches.isEmpty())
      return searchText;
    // Longest common prefix of all match display names (case-insensitive,
    // but stored in the case of the first match)
    Match m = matches.get(0);
    String lcp = displayNames.get(m.tool).substring(m.start);
    int len = lcp.length();
    for (int i = 1; i < matches.size(); i++) {
      m = matches.get(i);
      String hit = displayNames.get(m.tool).substring(m.start);
      int j = 0;
      while (j < len && j < hit.length()
          && Character.toLowerCase(lcp.charAt(j)) == Character.toLowerCase(hit.charAt(j)))
        j++;
      len = j;
    }
    String extended = lcp.substring(0, len);
    if (extended.length() > searchText.length()) {
      searchText = extended;
      matchesUpdated();
    }
    return searchText;
  }

  /** Replace the current matches list and search text; update display. */
  public void updateSearch(String text) {
    searchText = text;
    matches.clear();
    for (AddTool tool : allTools) {
      Match m = keywordMatch(tool, searchText);
      if (m != null)
        matches.add(m);
    }
    matchesUpdated();
  }
  
  private void matchesUpdated() {
    if (matches.isEmpty()) {
      selectedIndex = 0;
      scrollOffset  = 0;
    } else {
      if (selectedIndex >= matches.size())
        selectedIndex = matches.size() - 1;
      ensureVisible();
    }
    refreshLayout();
    repaint();
  }

  /** Move the keyboard selection up (-1) or down (+1), scrolling if needed. */
  public void moveSelection(int delta) {
    if (matches.isEmpty()) return;
    selectedIndex = Math.floorMod(selectedIndex + delta, matches.size());
    ensureVisible();
    refreshLayout();
    repaint();
  }

  /** Return the currently highlighted AddTool, or null if none. */
  public AddTool getSelectedTool() {
    if (selectedIndex >= 0 && selectedIndex < matches.size())
      return matches.get(selectedIndex).tool;
    return null;
  }

  /** Remove from the canvas and trigger a repaint. */
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
  public void paintComponent(Graphics gg) {
    Graphics2D g = (Graphics2D)gg;
    // Refresh cached metrics from the actual painting Graphics
    FontMetrics fm = g.getFontMetrics();
    fontAscent = fm.getAscent();
    fontHeight = fm.getHeight();
    headerH = fontHeight + PADDING_Y * 2 + HEADER_EXTRA;
    rowH    = fontHeight + PADDING_Y * 2;

    int w = getWidth();
    int h = getHeight();

    // Header position depends on orientation
    int headerTop = flipped ? h - headerH : 0;
    int itemsTop  = flipped ? 0           : headerH;
    int dividerY  = flipped ? h - headerH : headerH - HEADER_EXTRA - 1;

    int visibleCount = Math.min(matches.size(), windowSize);
    boolean hasUp   = scrollOffset > 0;
    boolean hasDown = scrollOffset + visibleCount < matches.size();

    // Background and border
    g.setColor(BG_COLOR);
    g.fillRoundRect(1, 1, w - 2, h - 2, 6, 6);
    Shape savedClip = g.getClip();
    g.setClip(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 6, 6));
    g.setColor(HEADER_BG);
    g.fillRect(1, headerTop+1, w - 2, headerH - HEADER_EXTRA - 2);
    g.setClip(savedClip);
    if (scrollOffset <= selectedIndex && selectedIndex < scrollOffset + visibleCount) {
      g.setColor(SELECT_BG);
      g.fillRect(1, itemsTop + INDICATOR_H + rowH * (selectedIndex - scrollOffset), w - 2, rowH-1);
    }
    g.setColor(BORDER_COLOR);
    g.drawRoundRect(1, 1, w - 2, h - 2, 6, 6);

    // Header: bold typed text with simulated cursor
    g.setFont(g.getFont().deriveFont(Font.BOLD));
    g.setColor(HEADER_FG);
    g.drawString("\u00bb " + searchText + "_", PADDING_X, headerTop + PADDING_Y + fontAscent);
    g.setFont(g.getFont().deriveFont(Font.PLAIN));
    g.setColor(BORDER_COLOR);
    g.drawLine(1, dividerY, w - 2, dividerY);

    // No-matches placeholder
    if (matches.isEmpty()) {
      g.setColor(NOMATCH_FG);
      g.drawString(S.get("componentSearchNoMatch"), PADDING_X, itemsTop + PADDING_Y + fontAscent);
      return;
    }

    int y = itemsTop;

    // Up scroll indicator
    drawTriangle(g, w, y, INDICATOR_H, true, hasUp);
    y += INDICATOR_H;

    // Item rows
    int prefixLen = searchText.length();
    for (int i = 0; i < visibleCount; i++) {
      int idx = scrollOffset + i;
      g.setColor(idx == selectedIndex ? SELECT_FG : ITEM_FG);
      Match m = matches.get(idx);
      AttributedString as = new AttributedString(displayNames.get(m.tool));
      as.addAttribute(TextAttribute.FONT, g.getFont());
      as.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, m.start, m.start + prefixLen);
      g.drawString(as.getIterator(), PADDING_X, y + PADDING_Y + fontAscent);
      y += rowH;
    }

    // Down scroll indicator
    drawTriangle(g, w, y, INDICATOR_H, false, hasDown);
  }

  // ----- private helpers -----

  /** Draw a centred filled triangle pointing up or down within a zone. */
  private void drawTriangle(Graphics g, int w, int zoneY, int zoneH, boolean up, boolean active) {
    if (!active)
      return;
    int cx = w / 2;
    int cy = zoneY + zoneH / 2;
    int hw = 6, hh = 3; // half-width and half-height of triangle
    if (up) {
      g.setColor(active ? TRI_ACTIVE_FG : TRI_INACTIVE_FG);
      g.fillPolygon(
          new int[]{ cx,      cx - hw, cx + hw },
          new int[]{ cy - hh, cy + hh, cy + hh },
          3);
    } else {
      g.setColor(active ? TRI_ACTIVE_FG : TRI_INACTIVE_FG);
      g.fillPolygon(
          new int[]{ cx,      cx - hw, cx + hw },
          new int[]{ cy + hh, cy - hh, cy - hh },
          3);
    }
  }

  /**
   * Return the logical hit-zone for a mouse event at pixel y.
   * Returns ROW_UP_IND, ROW_DOWN_IND, a valid item index, or ROW_NONE.
   */
  private int rowAtY(int pixelY) {
    if (matches.isEmpty()) return ROW_NONE;

    int visibleCount = Math.min(matches.size(), windowSize);
    boolean hasUp   = scrollOffset > 0;
    boolean hasDown = scrollOffset + visibleCount < matches.size();

    // Items area starts after header (normal) or at top (flipped)
    int y = pixelY - (flipped ? 0 : headerH);
    if (y < 0) return ROW_NONE; // in header region

    // if (hasUp) {
      if (y < INDICATOR_H) return ROW_UP_IND;
      y -= INDICATOR_H;
    // }
    // y is now relative to the start of item rows
    int itemsH = visibleCount * rowH;
    if (y < itemsH) {
      int idx = scrollOffset + y / rowH;
      return idx < matches.size() ? idx : ROW_NONE;
    }
    y -= itemsH;
    if (/*hasDown &&*/ y < INDICATOR_H) return ROW_DOWN_IND;
    return ROW_NONE;
  }

  /**
   * Scroll the viewport by delta rows, clamping to valid range.
   * Clamps selection to remain within the new visible window.
   */
  private void scroll(int delta) {
    if (matches.isEmpty()) return;
    int visibleCount = Math.min(matches.size(), windowSize);
    int maxOffset = Math.max(0, matches.size() - visibleCount);
    scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
    // Keep selection visible
    if (selectedIndex < scrollOffset)
      selectedIndex = scrollOffset;
    else if (selectedIndex >= scrollOffset + visibleCount)
      selectedIndex = scrollOffset + visibleCount - 1;
    refreshLayout();
    repaint();
  }

  /** Scroll viewport so that selectedIndex is within the visible window. */
  private void ensureVisible() {
    if (matches.isEmpty()) return;
    if (selectedIndex < scrollOffset) {
      scrollOffset = selectedIndex;
    } else if (selectedIndex >= scrollOffset + windowSize) {
      scrollOffset = selectedIndex - windowSize + 1;
    }
    scrollOffset = Math.max(0, scrollOffset);
  }

  /**
   * Initialise font metrics from a temporary canvas Graphics.
   * Must be called before computeWindowSize() and refreshLayout().
   */
  private void initMetrics() {
    Graphics g = canvas.getGraphics();
    if (g != null) {
      FontMetrics fm = g.getFontMetrics();
      g.dispose();
      fontAscent = fm.getAscent();
      fontHeight = fm.getHeight();
    } else {
      fontAscent = 12;
      fontHeight = 16;
    }
    headerH = fontHeight + PADDING_Y * 2 + HEADER_EXTRA;
    rowH = fontHeight + PADDING_Y * 2;
  }

  /**
   * Choose orientation (flipped) and compute windowSize from available canvas
   * space. Conservatively subtracts space for both indicator rows and the
   * header so the popup is guaranteed to fit.
   */
  private void computeWindowSize() {
    Rectangle view = canvas.getVisibleRect();
    // Overhead = header + worst-case two indicator rows + 2px bottom margin
    int overhead = headerH + 2 * INDICATOR_H + 2;
    int availableBelow = (view.y + view.height) - (anchorY + 8) - overhead;
    int availableAbove = (anchorY - 4) - view.y - overhead;
    int wsBelow = Math.max(0, availableBelow / rowH);
    int wsAbove = Math.max(0, availableAbove / rowH);

    // Prefer below, only flip when
    //  - the full list doesn't fit below
    //  - even a reasonable subset can't fit below
    //  - there is more space above, so flipping would help
    if (wsBelow < matches.size() && wsBelow < OK_VISIBLE && wsAbove > wsBelow) {
      flipped = true;
      windowSize = wsAbove;
    } else {
      flipped = false;
      windowSize = wsBelow;
    }
    windowSize = Math.max(MIN_VISIBLE, windowSize);
  }

  /**
   * Recompute the popup's preferred/actual size based on current state, and
   * re-anchor the top edge if the popup is flipped (so the bottom stays near
   * the cursor even as the height changes with scroll state).
   */
  private void refreshLayout() {
    int visibleCount = matches.isEmpty() ? 1 : Math.min(matches.size(), windowSize);
    boolean hasUp   = !matches.isEmpty() && scrollOffset > 0;
    boolean hasDown = !matches.isEmpty() && scrollOffset + visibleCount < matches.size();

    // Width: wide enough for any match name (all, not just visible, to avoid
    // resizing as the user scrolls), plus an underscore so there is room to
    // fully type the longest item without widening.
    Graphics g = canvas.getGraphics();
    int maxW = 140;
    if (g != null) {
      FontMetrics fm = g.getFontMetrics();
      g.dispose();
      maxW = Math.max(maxW, fm.stringWidth("\u00bb " + searchText + "_") + PADDING_X * 2 + 4);
      if (matches.isEmpty()) {
        maxW = Math.max(maxW, fm.stringWidth(S.get("componentSearchNoMatch")) + PADDING_X * 2 + 4);
      } else {
        for (Match m : matches) {
          int tw = fm.stringWidth("\u00bb " + displayNames.get(m.tool) + "_") + PADDING_X * 2 + 4;
          if (tw > maxW) maxW = tw;
        }
      }
    }

    int totalH = headerH
        + INDICATOR_H
        + visibleCount * rowH
        + INDICATOR_H
        + 2;

    setPreferredSize(new Dimension(maxW, totalH));
    setSize(maxW, totalH);

    // If flipped, keep the bottom edge anchored near the cursor
    if (flipped && getParent() != null)
      setLocation(getX(), anchorY - totalH - 4);
  }

  static boolean isLetter(char c) { return ('a' <= c && c <= 'z'); }
  static boolean isDigit(char c) { return ('0' <= c && c <= '9'); }

  /**
   * Check if prefix matches at any position in the tool's displayname that
   * isn't inside the middle of a word. A "word" here is a sequence of [0-9], or
   * a sequence of [A-Za-z]. The match must start within the tool name,
   * excluding any "in lib-x" suffix, since the presence of those suffixes is
   * not consistent across entries. So you can't start typing "audio" or
   * "wiring" for * example to get all the audio or wiring components. But you
   * can type "bar in lib-x" to match "foo bar" component with an "in lib-x"
   * suffix.
   */
  private Match keywordMatch(AddTool tool, String prefix) {
    prefix = prefix.toLowerCase();
    String toolName = tool.getDisplayName();
    String fullName = displayNames.get(tool);
    String corpus;
    if (fullName == null) // only occurs during beginSearch, when prefix is initialChar
      corpus = toolName.toLowerCase();
    else
      corpus = fullName.toLowerCase();
    // toolName = "Saturating Adder"
    // fullName = "Saturating Adder in Audio"
    // fullName = "Saturating Adder in MyProject-With-Unlucky-Named-Circuit"
    // prefix = "adder in M"
    // 1. position i shouldn't go past fullName.length-prefix.length, it can't possibly
    //    match after that point.
    // 2. otherwise, position i can go up to end of toolName, with match flowing into suffix, but
    //    don't allow i above toolName, since it would then just be matching the library name.
    //    (or isn't that ok?)
    int i = 0;
    int n = Math.min(toolName.length(), corpus.length() - prefix.length() + 1);
    // int n = fullName.length() - prefix.length() + 1;
    while (i < n) {
      if (corpus.startsWith(prefix, i)) {
        return new Match(tool, i);
      }
      char c = corpus.charAt(i);
      if (isLetter(c)) {
        do { i++; }
        while (i < n && isLetter(corpus.charAt(i)));
      } else if (isDigit(c)) {
        do { i++; }
        while (i < n && isDigit(corpus.charAt(i)));
      } else {
        i++;
      }
    }
    return null;
  }
}
