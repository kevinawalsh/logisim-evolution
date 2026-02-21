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
// Foreground and background colors, and body margin, must be applied by caller.
// Styles applied here:
//  - paragraph-margin [top/bottom for inter-paragraph spacing]
public class BoxLayout implements Text.LayoutEngine {
  
  static final TextStyling.Size DEFAULT_INTER_PARAGRAPH_SPACE = 
    new TextStyling.Size(0.7f, TextStyling.SizeUnit.EMS); // used with auto-wrap mode
  
  static final TextStyling.Size ZERO_SPACE =
    new TextStyling.Size(0f, TextStyling.SizeUnit.PIXELS);

  private final int halign, valign;
  private final Font font;
  private final boolean autoWrap;
  private final String text;
  private final TextStyling styling;
  private final TextStyling.Size paraMargin[];

  private int textWidth; // accurate, even for manual-wrap mode, once layout is complete,
                         // does not include body margins
  private Bounds bounds;  // accurate once layout is complete, includes body margins
  ArrayList<VisualLine> lines;

  // visual lines are in in top-to-bottom draw order (wrapped, if needed),
  // with all text accounted for EXCEPT newlines:
  //   lines[0].start == 0
  //   lines[i].end == lines[i+1].start (for soft breaks)
  //   lines[i].end+1 == lines[i+1].start (if line[i] has a hard break after)
  //   lines[n-1].end == len-1

  public BoxLayout(String t, int tw, int h, int v, boolean spacing, TextStyling sty) {
    text = t;
    textWidth = tw;
    autoWrap = (tw > 0);
    styling = sty;
    paraMargin = new TextStyling.Size[4];
    for (int i = 0; i < 4; i++) {
      // Use user-specified margin, if available
      paraMargin[i] = styling.paragraph_margin[i];
      if (paraMargin[i] == null) {
        if (i == 1 || i == 3) // For left/right, default to 0
          paraMargin[i] = ZERO_SPACE;
        else if (!spacing) // For top/bottom, default to 0 if editing as plain unstyled text
          paraMargin[i] = ZERO_SPACE;
        else // for top/bottom, leave a gap by default for wrapped text
          paraMargin[i] = DEFAULT_INTER_PARAGRAPH_SPACE;;
      }
    }
    font = styling.font;
    halign = h;
    valign = v;

    lines = new ArrayList<>();
    bounds = null;

    layoutMultiline();
    // System.out.println("BoxLayout #" + (seqno++) +" created by " + Thread.currentThread().getName() + "'s " + getCaller());
  }
  static int seqno = 0;
  static String getCaller() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    if (stack.length > 3) {
        StackTraceElement caller = stack[3];
        return caller.getClassName() + "." + caller.getMethodName();
    } else {
      return "???";
    }
  }

  public Bounds getBounds(Location loc) {
    return bounds.translate(loc);
  }

  public void drawText(Graphics2D g, Location loc) {
    g.setFont(font);
    for (VisualLine line : lines)
      line.layout.draw(g, loc.x + line.x, loc.y + line.baselineY);
  }

  // used during layout
  float dy = 0f;
  float dx = 0f;

  private void append(TextLayout layout, int start, int end, boolean paraBreakAfter, boolean lineBreakAfter) {

    // If last layout had a paraBreak after, add inter-para space.
    if (!lines.isEmpty()) {
      VisualLine prev = lines.get(lines.size() - 1);
      if (prev.paraBreakAfter) {
        float gapBelowPrev = paraMargin[2].px(prev.textSize());
        float gapAboveThis = paraMargin[0].px(layout.getAscent() + layout.getDescent() + layout.getLeading());
        dy += Math.max(gapBelowPrev, gapAboveThis);
      }
    }
    // TODO: paragraph left/right margins... but kind of pointless, for now.

    dy += layout.getAscent();
    lines.add(new VisualLine(lines.size(), text, start, end, paraBreakAfter, lineBreakAfter, layout, dx, dy));
    dy += layout.getDescent() + layout.getLeading();
    if (!autoWrap) {
      // note: with manual-wrap, trailing spaces make the bounds wider
      textWidth = Math.max(textWidth, (int)Math.ceil(layout.getAdvance()));
    }

  }

  private void layoutMultiline() {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;

    dy += styling.margin[0].px(font); // top
    dx += styling.margin[3].px(font); // left
    
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
    
    dy += styling.margin[2].px(font); // bottom
    dx += textWidth + styling.margin[1].px(font); // right

    float bodyWidth = dx;
    float bodyHeight = dy;

    int x = (int)Math.round(0 - halignAdjust(bodyWidth, halign));
    int y = (int)Math.round(0 - valignAdjust(bodyHeight, lines.get(0).layout, valign));

    for (VisualLine line : lines) {
      float lineWidth = autoWrap ? line.layout.getVisibleAdvance() : line.layout.getAdvance();
      line.x += x + halignAdjustLine(textWidth, lineWidth, halign);
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
    float textSize() { return layout.getAscent() + layout.getDescent(); }
    // float height() { return layout.getAscent() + layout.getDescent() + layout.getLeading(); }

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

