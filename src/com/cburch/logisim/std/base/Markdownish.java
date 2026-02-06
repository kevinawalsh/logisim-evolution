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

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.ArrayList;


public class Markdownish {

  public static final float HEADER1_FONT_SIZE = 1.5f;
        
  public final String src;
  public final Font baseFont, monoFont;
  public final ArrayList<Block> blocks;

  public Markdownish(String src, Font baseFont, Font monoFont) {
    this.src = src;
    this.baseFont = baseFont;
    this.monoFont = monoFont;
    blocks = new ArrayList<>();
    parseBlocks(src);
  }

  enum BlockType { HEADER, PARAGRAPH }
  public final class Block {
    final BlockType type;
    final int start;     // start index in src (inclusive)
    final int end;       // end index in src (exclusive), includes trailing newline

    // For headers
    final int headerLevel;      // 1..N
    final int contentStart;     // start of header text (after #'s and optional whitespace)
    final int contentEnd;       // end of header text (end of line)

    // For paragraphs
    final ArrayList<Span> spans;

    private Block(BlockType t, int s, int e, int lvl, int cs, int ce, ArrayList<Span> sp) {
      type = t; start = s; end = e;
      headerLevel = lvl; contentStart = cs; contentEnd = ce;
      spans = sp;
    }

    public AttributedString buildAttributedString() {
      // FIXME: eliminate RenderedText wrapper?
      RenderedText rt = buildRenderedText();
      AttributedString as = new AttributedString(rt.text);
      for (AttrRun r : rt.runs)
        as.addAttribute(TextAttribute.FONT, r.font, r.start, r.end);
      return as;
    }

    RenderedText buildRenderedText() {
      StringBuilder sb = new StringBuilder();
      ArrayList<AttrRun> runs = new ArrayList<>();

      if (type == BlockType.HEADER) {

        sb.append(src, contentStart, contentEnd);
        Font f = fontForStyle(InlineStyle.H1);
        runs.add(new AttrRun(0, sb.length(), f));
      
      } else { // PARAGRAPH
        AttrRun prev = null;
        for (Span span : spans) {
          int outStart = sb.length();
          if (span.style == InlineStyle.SPACE) {
            sb.append(' ');
          } else { // LINESEP, TEXT
            sb.append(src, span.start, span.end);
          }
          int outEnd = sb.length();
          if (outEnd == outStart) continue;

          Font f = fontForStyle(span.style);

          // Merge with previous run if same font and adjacent
          if (prev != null && prev.end == outStart && prev.font.equals(f)) {
            prev.end = outEnd;
          } else {
            AttrRun run = new AttrRun(outStart, outEnd, f);
            runs.add(run);
            prev = run;
          }
        }

      }
      return new RenderedText(sb.toString(), runs);
    }

    Font fontForStyle(InlineStyle style) {
      switch (style) {
        case H1:
          return baseFont.deriveFont(baseFont.getStyle() | Font.BOLD).deriveFont(baseFont.getSize() * HEADER1_FONT_SIZE);
        case CODE:
          // if you want bold/italic code later, you can derive from monoFont similarly
          return monoFont;
        case BOLD:
          return baseFont.deriveFont(baseFont.getStyle() | Font.BOLD);
        case ITALIC:
          return baseFont.deriveFont(baseFont.getStyle() | Font.ITALIC);
        case BOLD_ITALIC:
          return baseFont.deriveFont(baseFont.getStyle() | Font.BOLD | Font.ITALIC);
        case SPACE:
        case PLAIN:
        default:
          return baseFont;
      }
    }


  }

  private void header(int start, int end, int level, int cs, int ce) {
    blocks.add(new Block(BlockType.HEADER, start, end, level, cs, ce, null));
  }

  private void paragraph(int start, int end, ArrayList<Span> spans) {
    blocks.add(new Block(BlockType.PARAGRAPH, start, end, 0, 0, 0, spans));
  }

  enum InlineStyle { PLAIN, BOLD, ITALIC, BOLD_ITALIC, CODE, SPACE, H1 }
  enum SpanType { TEXT, SPACE, LINESEP }
  static final class Span {
    final SpanType type;
    final int start;   // in original text (inclusive)
    final int end;     // in original text (exclusive)
    final InlineStyle style;

    Span(SpanType t, int s, int e, InlineStyle y) {
      type = t;
      style = y;
      start = s;
      end = e;
    }

    static Span text(int s, int e, InlineStyle y) { // render substring of src
      return new Span(SpanType.TEXT, s, e, y);
    }
    static Span space(int s, int e) { // render as single ' '
      return new Span(SpanType.SPACE, s, e, InlineStyle.PLAIN);
    }
    static Span linesep(int s, int e) { // render as U+2028
      return new Span(SpanType.LINESEP, s, e, InlineStyle.PLAIN);
    }
  }

  static final class RenderedText {
    final String text;
    final ArrayList<AttrRun> runs;
    RenderedText(String text, ArrayList<AttrRun> runs) {
      this.text = text;
      this.runs = runs;
    }
  }

  static final class AttrRun {
    int start;   // in rendered string (inclusive)
    int end;     // in rendered string (exclusive)
    Font font;
    AttrRun(int s, int e, Font f) { start = s; end = e; font = f; }
  }

  private void parseBlocks(String s) {
    int n = s.length();
    int i = 0;

    while (i < n) {
      int ls = i;
      int le = lineEnd(s, i);          // [ls, le)
      int next = (le < n) ? le + 1 : n; // consume '\n' if present

      // Skip blank lines
      if (isLineBlankNo2028(s, ls, le)) {
        i = next;
        continue;
      }

      // Header block
      if (isHeaderLine(s, ls, le)) {
        int lvl = headerLevel(s, ls, le);
        int cs = headerContentStart(s, ls, le);
        int ce = le;
        header(ls, next, lvl, cs, ce);
        i = next;
        continue;
      }

      // Paragraph block: consume consecutive nonblank, nonheader lines
      int paraStart = ls;
      int paraEnd = next;
      i = next;

      while (i < n) {
        int ls2 = i;
        int le2 = lineEnd(s, i);
        int next2 = (le2 < n) ? le2 + 1 : n;

        if (isLineBlankNo2028(s, ls2, le2)) break;
        if (isHeaderLine(s, ls2, le2)) break;

        paraEnd = next2;
        i = next2;
      }

      paragraph(paraStart, paraEnd, parseParagraphSpans(s, paraStart, paraEnd));
    }

  }

  private class Buf {
    ArrayList<Span> spans = new ArrayList<>();
    int textRunStart = -1;
    int wsRunStart = -1;
    
    void flushTextUpTo(int pos) {
      if (textRunStart >= 0 && textRunStart < pos) {
        spans.add(Span.text(textRunStart, pos, InlineStyle.PLAIN));
      }
      textRunStart = -1;
    };

    // Helper to flush a pending whitespace run into a single SPACE span
    void flushWsUpTo(int pos) {
      if (wsRunStart >= 0 && wsRunStart < pos) {
        // Avoid leading SPACE in paragraph (markdown-ish)
        if (!spans.isEmpty() && spans.get(spans.size() - 1).type != SpanType.LINESEP) {
          // collapse multiple WS runs into a single SPACE span
          if (spans.get(spans.size() - 1).type != SpanType.SPACE) {
            spans.add(Span.space(wsRunStart, pos));
          } else {
            // already have a SPACE, just extend its source range if you want:
            // (optional) spans.set(last, new Span(SPACE, spans[last].start, pos));
          }
        } else {
          // paragraph-leading whitespace: drop it (common markdown behavior)
        }
      }
      wsRunStart = -1;
    };

  }

  private ArrayList<Span> parseParagraphSpans(String s, int a, int b) {
    Buf c = new Buf();
    int i = a;

    while (i < b) {
      char ch = s.charAt(i);

      // Preserve explicit LINE SEPARATOR as a LINESEP span
      if (ch == '\u2028') {
        c.flushTextUpTo(i);
        c.flushWsUpTo(i);
        c.spans.add(Span.linesep(i, i + 1));
        i++;
        continue;
      }

      // Newline inside a paragraph is "non-special whitespace" (collapsed),
      // except when preceded by 2+ spaces => LINESEP.
      if (ch == '\n') {
        // Determine if immediately preceded by 2+ spaces in the source
        int j = i - 1;
        int spaceCount = 0;
        while (j >= a && s.charAt(j) == ' ') { spaceCount++; j--; }

        if (spaceCount >= 2) {
          // The whitespace run may include earlier whitespace; split:
          int tailStart = i - spaceCount;

          c.flushTextUpTo(tailStart);
          c.flushWsUpTo(tailStart);

          c.spans.add(Span.linesep(tailStart, i + 1)); // source is "  ...  \n"
          i++; // consume newline
          continue;
        } else {
          // treat as normal whitespace
          c.flushTextUpTo(i);
          if (c.wsRunStart < 0)
            c.wsRunStart = i;
          i++;
          continue;
        }
      }

      // Other whitespace (excluding U+2028) collapses to SPACE
      if (Character.isWhitespace(ch)) {
        c.flushTextUpTo(i);
        if (c.wsRunStart < 0)
          c.wsRunStart = i;
        i++;
        continue;
      }

      // Visible text char
      c.flushWsUpTo(i);
      if (c.textRunStart < 0)
        c.textRunStart = i;
      i++;
    }

    // Flush trailing runs; drop trailing whitespace (markdown-ish)
    c.flushTextUpTo(b);
    // do NOT flushWsUpTo(b): trailing WS becomes nothing

    return c.spans;
  }

  private static boolean isLineBlankNo2028(String s, int a, int b) {
    // blank line: only whitespace (space/tab/etc) but NOT U+2028
    for (int i = a; i < b; i++) {
      char ch = s.charAt(i);
      if (ch == '\u2028') return false;
      if (!Character.isWhitespace(ch)) return false;
    }
    return true;
  }

  private static int lineEnd(String s, int i) {
    int n = s.length();
    while (i < n && s.charAt(i) != '\n') i++;
    return i; // exclusive, points at '\n' or n
  }

  private static boolean isHeaderLine(String s, int ls, int le) {
    int i = ls;
    int count = 0;
    while (i < le && s.charAt(i) == '#') { count++; i++; }
    if (count == 0) return false;
    // optional whitespace after #'s
    while (i < le && Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '\u2028') i++;
    // allow empty headers? markdown does; you can decide. Here: allow.
    return true;
  }

  private static int headerLevel(String s, int ls, int le) {
    int i = ls, count = 0;
    while (i < le && s.charAt(i) == '#') { count++; i++; }
    return count;
  }

  private static int headerContentStart(String s, int ls, int le) {
    int i = ls;
    while (i < le && s.charAt(i) == '#') i++;
    while (i < le && Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '\u2028') i++;
    return i;
  }

}
