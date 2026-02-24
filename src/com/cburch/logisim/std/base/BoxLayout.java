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
import java.awt.font.TextAttribute;
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

// BoxLayout handles text rendering for Text and Callout, optionally providing auto-wrap.
// Foreground and background colors must be applied by caller.
// Styles applied here:
//  - margin [for overall outer margins]
//  - paragraph-margin [e.g. top/bottom for inter-paragraph spacing]
public class BoxLayout implements Text.LayoutEngine {
  
  private final int halign, valign;
  private final Font font;
  private final boolean autoWrap;
  private final String text;
  private final TextStyling styling;

  private final int textWidth; // only valid if autowrap; includes margins
  private Bounds bounds;  // accurate once layout is complete, includes body and para margins
  ArrayList<VisualLine> lines;

  // visual lines are in in top-to-bottom draw order (wrapped, if needed),
  // with all text accounted for EXCEPT newlines:
  //   lines[0].start == 0
  //   lines[i].end == lines[i+1].start (for soft breaks)
  //   lines[i].end+1 == lines[i+1].start (if line[i] has a hard break after)
  //   lines[n-1].end == len-1

  public BoxLayout(String t, int tw, int h, int v, TextStyling sty) {
    text = t;
    textWidth = tw;
    autoWrap = (tw > 0);
    styling = sty;
    font = styling.font;
    halign = h;
    valign = v;

    lines = new ArrayList<>();
    bounds = null;

    layoutMultiline();
  }

  public Bounds getBounds(Location loc) {
    return bounds.translate(loc);
  }

  public void drawText(Graphics2D g, Location loc) {
    g.setFont(font);
    for (VisualLine line : lines)
      line.layout.draw(g, loc.x + line.x, loc.y + line.baselineY);
  }

  float dy = 0f; // cumulative height so far
  float dx = 0f; // left margin
  float mlw = 0f; // max line width so far
  
  private void append(TextLayout layout, int start, int end, boolean paraBreakAfter, boolean lineBreakAfter) {

    // If last layout had a paraBreak after, apply para margin top and bottom, collapsed
    if (!lines.isEmpty()) {
      VisualLine prev = lines.get(lines.size() - 1);
      if (prev.paraBreakAfter) {
        float gapBelowPrev = styling.paragraph_margin[2].px(font);
        float gapAboveThis = styling.paragraph_margin[0].px(font);
        dy += Math.max(gapBelowPrev, gapAboveThis);
      }
    }

    dy += layout.getAscent();
    lines.add(new VisualLine(lines.size(), text, start, end, paraBreakAfter, lineBreakAfter,
          layout, dx, dy));
    dy += layout.getDescent() + layout.getLeading();
    float lineWidth = autoWrap ? layout.getVisibleAdvance() : layout.getAdvance();
    mlw = Math.max(mlw, lineWidth);
  }

  private void layoutMultiline() {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
    
    float bml = styling.margin[3].px(font); // body left margin
    float pml = styling.paragraph_margin[3].px(font); // para left margin
    float ml = bml + pml; // total left margin
    float bmr = styling.margin[1].px(font); // body right margin
    float pmr = styling.paragraph_margin[1].px(font); // para right margin
    float mr = bmr + pmr; // total right margin
    
    // collapse top margin into first paragraph
    float bmt = styling.margin[0].px(font); // body margin top
    float pmt = styling.paragraph_margin[0].px(font); // para margin top
    float mt = Math.max(bmt, pmt);

    dy = mt; // overall top margins applied here
    dx = ml; // all left margin applied here
    float paraWidth = Math.max(Text.TEXT_MIN_WIDTH, autoWrap ? textWidth - ml - mr : 0);
    mlw = paraWidth; // max line width so far
    
    String[] paragraphs = text.split("\n", -1);

    int start = 0;

    for (int p = 0; p < paragraphs.length; p++) {
      String paraWithBreaks = paragraphs[p];

      String[] subparagraphs = paraWithBreaks.split("\u2028", -1);
      for (int s = 0; s < subparagraphs.length; s++) {
        String para = subparagraphs[s];

        int end = start + para.length();
        boolean empty = para.isEmpty();
        boolean lineBreakAfter = (s < subparagraphs.length - 1);
        boolean paraBreakAfter = !lineBreakAfter && (p < paragraphs.length - 1);

        if (empty || !autoWrap) { // empty or manual-wrap: one visual line for entire paragraph
                                  // empty paragraph uses a space, to get sensible line height
          TextLayout layout = new TextLayout(empty ? " " : para, font, frc);
          append(layout, start, end, paraBreakAfter, lineBreakAfter);

        } else { // Auto-wrap: LineBreakMeasurer produces multiple visual lines per paragraph
          AttributedString astr = new AttributedString(para);
          astr.addAttribute(TextAttribute.FONT, font);
          AttributedCharacterIterator it = astr.getIterator();
          LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
          measurer.setPosition(it.getBeginIndex());
          
          int left = measurer.getPosition();
          while (left < it.getEndIndex()) {
            TextLayout layout = measurer.nextLayout(paraWidth);
            int right = measurer.getPosition();
            boolean last = (right == para.length());
            append(layout, start + left, start + right, paraBreakAfter && last, lineBreakAfter && last);
            left = right;
          }
        }

        start = end + 1; // add one, for newline between paragraphs, or lineseparator between subparagraphs
      }

    }

    // apply last para margin bottom and body margin bottom, collapsed
    float bmb = styling.margin[2].px(font); // body margin bottom
    float pmb = styling.paragraph_margin[2].px(font); // para margin bottom
    float mb = Math.max(bmb, pmb);
    dy += mb;

    float bodyWidth = ml + mlw + mr;
    float bodyHeight = dy;

    // so far: all content, with margins, is bounded by (0, 0, bodyWidth, bodyHeight)

    // pick a new origin
    int x = (int)Math.round(0 - halignAdjust(bodyWidth, halign));
    int y = (int)Math.round(0 - valignAdjust(bodyHeight, lines.get(0).layout, valign));

    // translate all lines, and apply horizontal justification
    for (VisualLine line : lines) {
      float lineWidth = autoWrap ? line.layout.getVisibleAdvance() : line.layout.getAdvance();
      line.x += x + halignAdjustLine(mlw, lineWidth, halign);
      line.baselineY += y;
    }

    bounds = Bounds.create(x, y, (int)Math.ceil(bodyWidth), (int)Math.ceil(bodyHeight));
  }


  VisualLine firstLineOfParagraphContaining(VisualLine vl) {
    while (vl.lineno > 0) {
      VisualLine prev = lines.get(vl.lineno - 1);
      if (prev.paraBreakAfter)
        break;
      vl = prev;
    }
    return vl;
  }

  VisualLine lastLineOfParagraphContaining(VisualLine vl) {
    while (vl.lineno < lines.size() - 1 && !vl.paraBreakAfter)
      vl = lines.get(vl.lineno + 1);
    return vl;
  }

  VisualLine lineForPosition(int pos, boolean reverseBias) {
    for (VisualLine vl : lines) {
      int end = vl.softBreakAfter ? (vl.end) : (vl.end + 1);
      if (pos < end || (pos == end && reverseBias))
        return vl;
    }
    return lines.get(lines.size() - 1);
  }

  public Text.CaretPosition caretPositionForPoint(int px, int py) {
    BoxLayout.VisualLine vl = lineForY(py);
    int cursor = vl.positionForX(px);
    boolean revBias = (cursor != vl.start && cursor == vl.end);
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

  private static float valignAdjust(float bodyHeight, TextLayout layout, int valign) {
    float firstlineHeight = layout.getAscent() + layout.getDescent() + layout.getLeading();
    if (valign == ALIGN.V_BASELINE)
      return layout.getAscent();
    else if (valign == ALIGN.V_BOTTOM_FIRST)
      return firstlineHeight;
    else if (valign == ALIGN.V_CENTER_FIRST)
      return firstlineHeight / 2f;
    else if (valign == ALIGN.V_BOTTOM_OVERALL)
      return bodyHeight;
    else if (valign == ALIGN.V_CENTER_OVERALL)
      return bodyHeight / 2f;
    else // V_TOP
      return 0;
  }
  
  private static float halignAdjust(float bodyWidth, int halign) {
    if (halign == ALIGN.H_RIGHT)
      return bodyWidth;
    else if (halign == ALIGN.H_CENTER)
      return bodyWidth / 2;
    else // H_LEFT
      return 0;
  }

  private static float halignAdjustLine(float textWidth, float lineWidth, int halign) {
    if (halign == ALIGN.H_RIGHT)
      return (textWidth - lineWidth);
    else if (halign == ALIGN.H_CENTER)
      return (textWidth - lineWidth)/2;
    else // H_LEFT
      return 0;
  }


  static class VisualLine {
    final int lineno;
    final String fullText;        // ugh: copy of text from outer class
    final int start;              // utf16 index into text (inclusive)
    final int end;                // utf16 index into text (exclusive) -- excludes '\n'
    final boolean paraBreakAfter; // whether line is followed by '\n' in text
    final boolean lineBreakAfter; // whether line is followed by '\u2028' LINE SEPARATOR in text
    final boolean softBreakAfter; // otherwise
    final TextLayout layout;
    /*final*/ float x;     // draw origin x, where layout.draw() is called
    /*final*/ float baselineY; // baseline y, where layout.draw() is called

    VisualLine(int lineno, String fullText, int start, int end, boolean paraBreakAfter, boolean lineBreakAfter, TextLayout layout, float x, float baselineY) {
      this.lineno = lineno;
      this.fullText = fullText;
      this.start = start;
      this.end = end; // does not include newline
      this.paraBreakAfter = paraBreakAfter;
      this.lineBreakAfter = lineBreakAfter;
      this.softBreakAfter = !(paraBreakAfter || lineBreakAfter);
      this.layout = layout; // does not include newline
      this.x = x;
      this.baselineY = baselineY;
    }

    float topY() { return baselineY - layout.getAscent(); }
    float bottomY() { return baselineY + layout.getDescent() + layout.getLeading(); }
    float textSize() { return layout.getAscent() + layout.getDescent(); }

    float caretXForPosition(int pos) {
      int local = clamp(pos - start, 0, end - start);
      TextHitInfo hit = TextHitInfo.leading(local);
      float[] caretInfo = layout.getCaretInfo(hit); // [x_along_baseline, inverse_slope]
      return x + caretInfo[0];
    }

    // int positionForX(float px, boolean biasReverse) {
    //   float relX = px - x, relY = 0; // 0 means baseline
    //   TextHitInfo hit = layout.hitTestChar(relX, 0);
    //   int pos = start + hit.getInsertionIndex();
    //   pos = clamp(pos, start, end);
    //   return snapToGraphemeBoundary(pos, biasReverse, start, end);
    // }

    int positionForX(float px) { // snaps to visually closest grapheme boundary
      float relX = px - x, relY = 0; // 0 means baseline
      TextHitInfo hit = layout.hitTestChar(relX, 0);
      int pos = start + hit.getInsertionIndex();
      pos = clamp(pos, start, end);
      return snapToGraphemeBoundary(pos, px, this);
    }

    private static int clamp(int v, int lo, int hi) {
      return (v < lo) ? lo : (v > hi ? hi : v);
    }

    boolean isEmpty() {
      return start == end;
    }
  }

  public static int prevGraphemeBoundary(int pos, String text) {
    if (pos <= 0)
      return 0;
    BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
    bi.setText(text);
    int b = bi.preceding(pos);
    return (b == BreakIterator.DONE) ? 0 : b;
  }

  public static int nextGraphemeBoundary(int pos, String text) {
    int n = text.length();
    if (pos >= n)
      return n;
    BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
    bi.setText(text);
    int b = bi.following(pos);
    return (b == BreakIterator.DONE) ? n : b;
  }

  // public static int snapToGraphemeBoundary(int pos, boolean reverseBias, int start, int end) {
  //   BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
  //   bi.setText(text);
  //   if (bi.isBoundary(pos)) {
  //     return pos;
  //   } else if (reverseBias) {
  //     int prev = bi.preceding(pos);
  //     return (prev == BreakIterator.DONE || prev < start) ? start : prev;
  //   } else {
  //     int next = bi.following(pos);
  //     return (next == BreakIterator.DONE || next > end) ? end : next;
  //   }
  // }

  public static int snapToGraphemeBoundary(int pos, float px, VisualLine vl) {
    BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
    bi.setText(vl.fullText);
    if (bi.isBoundary(pos))
      return pos;
    int prev = bi.preceding(pos);
    if (prev == BreakIterator.DONE || prev < vl.start)
      prev = vl.start;
    int next = bi.following(pos);
    if (next == BreakIterator.DONE || next > vl.end)
      next = vl.end;
    float prevX = vl.caretXForPosition(prev);
    float nextX = vl.caretXForPosition(next);
    return (Math.abs(px - prevX) <= Math.abs(px - nextX)) ? prev : next;
  }

}

