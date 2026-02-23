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
import java.awt.geom.Rectangle2D;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.Locale;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.GraphicsUtil;

// StyledBoxLayout handles text rendering for Markdownish.
public class StyledBoxLayout implements Text.LayoutEngine {

  final String text;
  final TextStyling styling;
  final int textWidth; // excludes margins
  
  Bounds bounds;  // accurate once layout is complete, includes margin
  ArrayList<VisualLine> lines;

  public StyledBoxLayout(String t, int tw, TextStyling sty) {
    text = t;
    textWidth = tw; // must be positive, includes margins
    styling = sty;

    lines = new ArrayList<>();

    bounds = layoutMarkdown(md);
  }

  public Bounds getBounds(Location loc) {
    return bounds.translate(loc);
  }

  public void drawText(Graphics2D g, Location loc) {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
    g.setFont(styling.font);
    g.setColor(styling.color);
    for (VisualLine line : lines) {
      if (line.accent == null) {
        line.layout.draw(g, loc.x + line.x, loc.y + line.baselineY);
      } else if (line.accent.type == TextStyling.AccentType.UNDERLINE) {
        float px = line.accent.size.px(line.font);
        g.setColor(line.accent.color);
        g.fill(new Rectangle2D.Float(loc.x + line.x,
              loc.y + line.baselineY + line.layout.getDescent(),
              line.layout.getVisibleAdvance(), px));
        g.setColor(styling.color);
        line.layout.draw(g, loc.x + line.x, loc.y + line.baselineY);
      } else if (line.accent.type == TextStyling.AccentType.LEADERBLOCK) {
        float px = line.accent.size.px(line.font);
        float px2 = (float)line.font.getStringBounds(" ", frc).getWidth();
        g.setColor(line.accent.color);
        g.fill(new Rectangle2D.Float(loc.x + line.x, loc.y + line.topY(),
              px, line.textSize()));
        g.setColor(styling.color);
        line.layout.draw(g, loc.x + line.x + px + px2, loc.y + line.baselineY);
      }
    }
  }
  
  private Bounds layoutMarkdown(Markdownish md) {
    float mt = styling.margin[0].px(styling.font); // body margin top
    float mr = styling.margin[1].px(styling.font); // body margin right
    float mb = styling.margin[2].px(styling.font); // body margin bottom
    float ml = styling.margin[3].px(styling.font); // body margin left

    float by = mt;
    float bx = ml;
    Box box = layoutBody(md, bx, by, mt, textWidth - ml - mr);

    float w = ml + box.width + mr; // includes possible overflow
    float h = mr + box.height + Math.max(0, mb - box.bottomClear);
    return Bounds.create(0, 0, (int)Math.ceil(w), (int)Math.ceil(h));
  }

  // Place blocks (including their margins) starting at y, but topmost block's
  // margin can collapse with topClear above us. The margins around the body are
  // handled by caller.
  private Box layoutBody(Markdownish md, float x, float y, float topClear, float bodyWidth) {

    if (md.blocks.isEmpty()) {
      // Likely never happens: no blocks, so no height, no margins
      return Box(x, y, bodyWidth, 0, 0, 0);
    }

    float actualWidth = bodyWidth;
    float dy = 0;
    float clearedAbove = topClear;

    Markdownish.Block first = md.blocks.get(0);
    Markdownish.Block last = md.blocks.get(md.blocks.size() - 1);
    for (Markdownish.Block block : md.blocks) {
      Font font = block.getFont();
      float mt = Math.max(0, block.margin[0].px(font) - clearedAbove);
      float mr = block.margin[1].px(font);
      float mb = block.margin[2].px(font);
      float ml = block.margin[3].px(font);

      clearedAbove += mt;
      float bx = x + ml;
      float by = y + dy + mt;
      float bw = bodyWidth - ml - mr;

      if (block.isLeaf()) {
        Box box = layoutLeafBlock(block, bx, by, clearedAbove, bw);
        actualWidth = Math.max(actualWidth, ml + box.width + mr);
        clearedAbove = box.bottomClear;
        dy += mt + box.height;
      } else {
        // todo
      }

    }

    return Bounds.create(0, 0, (int)Math.ceil(dx), (int)Math.ceil(dy));
  }

  // HEADER, PARAGRAPH, FENCED_CODE ... these have a 1+ phrases
  private Rectangle2D.Float layoutLeafBlock(Markdownish.Block block, float x, float y, float blockWidth) {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
    float dx = 0, dy = 0;

    for (Markdownish.Phrase phrase : block.phrases) {

      AttributedString astr = phrase.buildAttributedString();
      AttributedCharacterIterator it = astr.getIterator();
      Font font = phrase.getFont();

      // underline accent is added to the last visual line of a header block
      // blockleading accent is added to all visual lines of a header block
      float availableWidth = blockWidth;
      float accentExtraY = 0f;
      TextStyling.Accent accent = block.getAccent(), uAccent = null, bAccent = null;
      if (accent != null && accent.type == TextStyling.AccentType.UNDERLINE) {
        uAccent = accent;
        accentExtraY = accent.size.px(font);
      } else if (accent != null && accent.type == TextStyling.AccentType.LEADERBLOCK) {
        bAccent = accent;
        float px = accent.size.px(font) + (float)font.getStringBounds(" ", frc).getWidth();
        // The accent leaderblock takes up some of the blockWidth, leaving less
        // room for text. We always reserve TEXT_MIN_WIDTH, overflowing if needed.
        availableWidth = Math.max(blockWidth - px, Text.TEXT_MIN_WIDTH);
      }

      if (!block.wrapped()) {
        // e.g. phrase within a FENCED_CODE block
        int left = it.getBeginIndex();
        int right = it.getEndIndex();
        TextLayout layout = new TextLayout(it, frc);

        dy += layout.getAscent();
        lines.add(new VisualLine(layout, astr, left, right, x+dx, y+dy, accent, font));
        dy += layout.getDescent() + layout.getLeading() + accentExtraY;

        continue;
      }

      LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
      measurer.setPosition(it.getBeginIndex());

      int left = measurer.getPosition();
      while (left < it.getEndIndex()) {
        TextLayout layout = measurer.nextLayout(availableWidth);
        int right = measurer.getPosition();
        boolean last = (right >= it.getEndIndex());
        
        dy += layout.getAscent();
        lines.add(new VisualLine(layout, astr, left, right, x+dx, y+dy,
              bAccent != null ? bAccent : last ? uAccent : null, font));
        dy += layout.getDescent() + layout.getLeading() + (last ? accentExtraY : 0f);

        left = right;
      }

    }

    dx += blockWidth;
    return new Rectangle2D.Float(x, y, dx, dy);
  }

  static class VisualLine {
    final TextLayout layout;
    final AttributedString astr;
    final int start, end; // index range within astr for this line
    final float x;     // draw origin x, where layout.draw() is called
    final float baselineY; // baseline y, where layout.draw() is called
    final TextStyling.Accent accent;
    final Font font; // only needed for computing accent sizing

    VisualLine(TextLayout layout, AttributedString astr, int s, int e, float x, float baselineY, TextStyling.Accent a, Font f) {
      this.layout = layout; // does not include newline
      this.astr = astr;
      this.start = s;
      this.end = e;
      this.x = x;
      this.baselineY = baselineY;
      this.accent = a;
      this.font = f;
    }

    float topY() { return baselineY - layout.getAscent(); }
    float bottomY() { return baselineY + layout.getDescent() + layout.getLeading(); }
    float textSize() { return layout.getAscent() + layout.getDescent(); }
    // float height() { return layout.getAscent() + layout.getDescent() + layout.getLeading(); }

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

  static final class Box {
    float x, y, w, h; // includes all internal margins and content
    float topClear, bottomClear; // space eligible for margin collapse
    Box(float xx, float yy, float ww, float hh, float t, float b) {
      x = xx; y = yy; w = ww; h = hh; topClear = t; bottomClear = b;
    }
  }

