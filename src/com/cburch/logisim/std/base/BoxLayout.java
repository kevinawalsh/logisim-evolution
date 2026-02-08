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
import java.awt.Graphics;
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

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

// BoxLayout handles text rendering for Text and Callout, optionally providing auto-wrap.
public class BoxLayout {
  
  static final float DEFAULT_INTER_PARAGRAPH_SPACE = 0.7f; // 0.7 x FontHeight, used with auto-wrap mode

  final Location loc;
  final int halign, valign;
  final Font font;
  final boolean autoWrap;
  final String text;
  final float interParaSpace;

  int textWidth; // accurate, even for manual-wrap mode, once layout is complete
  Bounds bounds;  // accurate once layout is complete
  ArrayList<VisualLine> lines;

  // visual lines are in in top-to-bottom draw order (wrapped, if needed),
  // with all text accounted for EXCEPT newlines:
  //   lines[0].start == 0
  //   lines[i].end == lines[i+1].start (for soft breaks)
  //   lines[i].end+1 == lines[i+1].start (if line[i] has a hard break after)
  //   lines[n-1].end == len-1

  public BoxLayout(Graphics g, String t, Location l, int tw, Font f, int h, int v, boolean spacing) {
    g2 = (Graphics2D)g;
    text = t;
    loc = l;
    textWidth = tw;
    autoWrap = (tw > 0);
    interParaSpace = (spacing ? DEFAULT_INTER_PARAGRAPH_SPACE : 0);
    font = f;
    halign = h;
    valign = v;

    lines = new ArrayList<>();
    bounds = null;

    layoutMultiline();
    g2 = null;
  }

  public void drawText(Graphics g) {
    Graphics2D g2 = (Graphics2D) g;
    g2.setFont(font);
    for (VisualLine line : lines)
      line.layout.draw(g2, line.x, line.baselineY);
  }

  // used during layout
  float dy = 0f;
  float dx = 0f;
  Graphics2D g2;

  private void append(TextLayout layout, int start, int end, boolean paraBreakAfter, boolean lineBreakAfter) {

    // If auto-wrapping, and last layout had a paraBreak after, add inter-para space.
    if (autoWrap && !lines.isEmpty()) {
      VisualLine prev = lines.get(lines.size() - 1);
      if (prev.paraBreakAfter) {
        float gap = prev.height() * interParaSpace;
        dy += gap;
      }
    }

    dy += layout.getAscent();
    lines.add(new VisualLine(lines.size(), text, start, end, paraBreakAfter, lineBreakAfter, layout, dx, dy));
    dy += layout.getDescent() + layout.getLeading();
    if (!autoWrap) {
      // note: with manual-wrap, trailing spaces make the bounds wider
      textWidth = Math.max(textWidth, (int)Math.ceil(layout.getAdvance()));
    }

  }


  private void layoutMultiline() {

    g2.setFont(font);
    FontRenderContext frc = g2.getFontRenderContext();
    
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
          AttributedCharacterIterator it = astr.getIterator();
          LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
          measurer.setPosition(it.getBeginIndex());
          
          int left = measurer.getPosition();
          while (left < it.getEndIndex()) {
            TextLayout layout = measurer.nextLayout(textWidth);
            int right = measurer.getPosition();
            boolean last = (right == para.length());
            append(layout, start + left, start + right, paraBreakAfter && last, lineBreakAfter && last);
            left = right;
          }

        }

        start = end + 1; // add one, for newline between paragraphs, or lineseparator between subparagraphs
      }
    }

    int x = (int)Math.round(loc.x - halignAdjust(textWidth, halign));
    int y = (int)Math.round(loc.y - valignAdjust(lines.get(0).layout, valign)); // valign relative to first line

    for (VisualLine line : lines) {
      float lineWidth = autoWrap ? line.layout.getVisibleAdvance() : line.layout.getAdvance();
      line.x += x + halignAdjustLine(textWidth, lineWidth, halign);
      line.baselineY += y;
    }

    bounds = Bounds.create(x, y, textWidth, (int) Math.ceil(dy));
    g2 = null;
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

  public VisualLine lineForY(int py) {
    for (VisualLine vl : lines) {
      if (py < vl.bottomY()) {
        return vl;
      }
    }
    return lines.get(lines.size() - 1);
  }

  private static float valignAdjust(TextLayout layout, int valign) {
    float h = layout.getAscent() + layout.getDescent() + layout.getLeading();
    if (valign == ALIGN.V_BASELINE)
      return layout.getAscent();
    else if (valign == ALIGN.V_BOTTOM)
      return h;
    else if (valign == ALIGN.V_CENTER)
      return h / 2f;
    else // V_TOP
      return 0;
  }
  
  private static float halignAdjust(int textWidth, int halign) {
    if (halign == ALIGN.H_RIGHT)
      return textWidth;
    else if (halign == ALIGN.H_CENTER)
      return textWidth / 2;
    else // H_LEFT
      return 0;
  }

  private static float halignAdjustLine(int textWidth, float lineWidth, int halign) {
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
    float height() { return layout.getAscent() + layout.getDescent() + layout.getLeading(); }

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

  // This is used by Text.get*Bounds()
  static Bounds getBounds(Graphics g, String text, Location loc, int textWidth, Font font, int halign, int valign) {
    BoxLayout box = new BoxLayout(g, text, loc, textWidth, font, halign, valign, textWidth > 0);
    return box.bounds;
  }

  // This is used by Text.paint()
  static void drawMultilineText(Graphics g, String text, Location loc, int textWidth, Font font, int halign, int valign) {
    BoxLayout box = new BoxLayout(g, text, loc, textWidth, font, halign, valign, textWidth > 0);
    box.drawText(g);
  }

}

