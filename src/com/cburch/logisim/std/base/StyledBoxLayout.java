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

package com.cburch.logisim.std.base;

import java.util.ArrayList;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.Locale;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.GraphicsUtil;

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

// StyledBoxLayout handles text rendering for Markdownish.
public class StyledBoxLayout implements Text.LayoutEngine {

  final Font font;
  final boolean autoWrap;
  final String text;

  int textWidth;
  Bounds bounds;  // accurate once layout is complete
  ArrayList<VisualLine> lines;

  public StyledBoxLayout(String t, int tw, Font f) {
    text = t;
    textWidth = tw; // must be positive
    autoWrap = true;
    font = f;

    lines = new ArrayList<>();
    bounds = null;

    layoutMarkdownish();
  }

  public Bounds getBounds(Location loc) {
    return bounds.translate(loc);
  }

  public void drawText(Graphics2D g, Location loc) {
    g.setFont(font);
    for (VisualLine line : lines)
      line.layout.draw(g, loc.x + line.x, loc.y + line.baselineY);
  }

  private void layoutMarkdownish() {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;

    int x = 0;
    int y = 0;
    float dy = 0f;
    float dx = 0f;

    // FIXME: add a monospace font option?
    Font baseFont = font; 
    Font monoFont = new Font("Monospaced", Font.PLAIN, font.getSize());
  
    Markdownish md = new Markdownish(text, baseFont, monoFont);
    for (Markdownish.Block block : md.blocks) {

      dy += md.gapAbove(block);

      AttributedString astr = block.buildAttributedString();
      AttributedCharacterIterator it = astr.getIterator();

      if (!block.wrapped()) {
        // e.g. FENCED_CODE block
        int left = it.getBeginIndex();
        int right = it.getEndIndex();
        TextLayout layout = new TextLayout(it, frc);
        
        dy += layout.getAscent();
        lines.add(new VisualLine(layout, astr, left, right, x + dx, y + dy));
        dy += layout.getDescent() + layout.getLeading();
        continue;
      }

      LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
      measurer.setPosition(it.getBeginIndex());
          
      int left = measurer.getPosition();
      while (left < it.getEndIndex()) {
        TextLayout layout = measurer.nextLayout(textWidth);
        int right = measurer.getPosition();
        boolean last = (right >= it.getEndIndex());
        
        dy += layout.getAscent();
        lines.add(new VisualLine(layout, astr, left, right, x + dx, y + dy));
        dy += layout.getDescent() + layout.getLeading();

        left = right;
      }

    }

    bounds = Bounds.create(x, y, textWidth, (int) Math.ceil(dy));
  }

  static class VisualLine {
    final TextLayout layout;
    final AttributedString astr;
    final int start, end; // index range within astr for this line
    final float x;     // draw origin x, where layout.draw() is called
    final float baselineY; // baseline y, where layout.draw() is called

    VisualLine(TextLayout layout, AttributedString astr, int s, int e, float x, float baselineY) {
      this.layout = layout; // does not include newline
      this.astr = astr;
      this.start = s;
      this.end = e;
      this.x = x;
      this.baselineY = baselineY;
    }

    float topY() { return baselineY - layout.getAscent(); }
    float bottomY() { return baselineY + layout.getDescent() + layout.getLeading(); }
    float height() { return layout.getAscent() + layout.getDescent() + layout.getLeading(); }

    public int sourcePositionForX(float px) { // snaps to some nearby grapheme boundary
      float relX = px - x, relY = 0; // 0 means baseline
      TextHitInfo hit = layout.hitTestChar(relX, 0);
      int pos = start + hit.getInsertionIndex();
      pos = clamp(pos, start, end);

      AttributedCharacterIterator it = astr.getIterator();
      int begin = it.getBeginIndex();
      int end = it.getEndIndex();
      if (end == begin)
        return 0; // empty text, should not be possible
      pos = clamp(pos, begin, end); // should be in range already, but just in case
      it.setIndex(pos == end ? end - 1 : pos);
      Markdownish.Span span = (Markdownish.Span)it.getAttribute(Markdownish.SPAN_ID);
      if (span == null)
        return 0; // unmarked character, huh?
      int runStart = it.getRunStart(Markdownish.SPAN_ID);
      int runLimit = it.getRunLimit(Markdownish.SPAN_ID);
      int offset = clamp(pos - runStart, 0, runLimit - runStart + 1); // clamp just in case
      int srcIdx = clamp(span.start + offset, span.start, span.end+1); // clamp just in case
      return srcIdx;
    }

    public boolean getBiasForX(float px) {
      return true; // reverse bias, good enough
    }

    private static int clamp(int v, int lo, int hi) {
      return (v < lo) ? lo : (v > hi ? hi : v);
    }

  }

  public Text.CaretPosition caretPositionForPoint(int px, int py) {
    StyledBoxLayout.VisualLine vl = lineForY(py);
    int cursor = snapToGraphemeBoundary(vl.sourcePositionForX(px));
    boolean revBias = vl.getBiasForX(px);
    return new Text.CaretPosition(bounds, cursor, revBias);
  }

  public VisualLine lineForY(int py) {
    for (VisualLine vl : lines) {
      if (py < vl.bottomY()) {
        return vl;
      }
    }
    return lines.get(lines.size() - 1);
  }
  
  public int snapToGraphemeBoundary(int pos) {
    if (text.isEmpty())
      return 0;
    BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
    bi.setText(text);
    if (bi.isBoundary(pos)) {
      return pos;
    } else { // reverse bias
      int prev = bi.preceding(pos);
      return (prev == BreakIterator.DONE || prev < 0) ? 0 : prev;
    }
  }

}

