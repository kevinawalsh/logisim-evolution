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
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;

// Markdownish can render something approaching a small subset of commonmark or github-flavored
// markdown, with a few logisim-specific variations.
//
// Note: Before invoking this code, source string must be normalized slightly, at minimum by
// removing all carriage returns or replacing them with newlines as appropriate.
//
// Features from markdown that mostly work as expected, if used in simple ways:
//   Headers, paragraphs, (soon) bold, (soon) italics, (soon) inline-code, etc.
//
// See, github-flavored markdown spec for context: https://github.github.com/gfm/
//
// # Header 1
// ## Header 2
// ### Header 3 - up to header 6, though these may be visually nearly indistinguishable.
// Notes:
//  - Starts with 1-6 "#", and a space, tab, or newline after the leading "#" sequence.
//  - Empty header is legal, and will render as a blank vertical gap.
//  - Headers can break a paragraph, no need for blank lines before or after.
//  - Leading #-space sequence, and trailing spaces and optional trailing "#", are all stripped.
//  - Within header contents, spaces and tabs are collapsed to single spaces.
//  - Trailing backslash or spaces have no special meaning.
//
// Paragraphs - Consecutive non-blank, non-header lines make up a paragraph.
// Notes:
//  - Within paragraphs, spaces, tabs, and newlines are mostly collapsed to single spaces.
//  - Content is rendered with auto-wrapping to fit width of text box.
//  - A trailing "\", or 2 or more spaces, directly before a newline, acts like an html <br>,
//    introducing a hard break into the auto-wrapping layout.
//  - Such hard-breaks do not leave a gap.
//  - Such hard-breaks aren't possible on the last line of a paragraph. That is, a hard break
//    is inserted iff the backslash-newline or space-space-newline sequence is followed directly by
//    a line that isn't blank (i.e. it contains somethign other than spaces and tabs) and isn't a
//    header (i.e. it doesn't start with a #-space or #-newline sequence, etc.)
// 
// Notes:
// - Headers and paragraphs are each rendered with some space below (and above) them, unless there
//   is no other content below (or above) them. Here, "content" means a header or paragraph.
// - Blank lines are not considered "content". They help define the boundaries of paragraphs, but
//   are not part of the paragraphs. "Blank line" here means a line with only spaces and tabs.
// - Gaps before/after headers and paragraphs are not additive, they "overlap". There is no gap at
//   the start or end of the markdown, only between the headers and paragraphs.
//
// Index Mapping: We keep a mapping such that for each part of rendered text, we can determine the
// corresponding index in the original source text. But note:
//  - a single rendered character (e.g. a space) may have originated from multiple characters
//    in the source text (e.g. a sequence of spaces and tabs).
//  - some characters in the source text (e.g. delimiters like "#") are not rendered and are outside
//    the range of this mapping.

public class Markdownish {

  // Styling rules
  static final float[] HEADER_FONT_SIZE = { 1.6f, 1.4f, 1.2f, 1.1f, 1.1f, 1.1f }; // relative to base font size
  static final float HEADER_ABOVE_GAP = 0.6f; // relative to header font size
  static final float HEADER_BELOW_GAP = 0.4f; // relative to header font size
  static final float PARAGRAPH_ABOVE_GAP = 0.5f; // relative to base font size
  static final float PARAGRAPH_BELOW_GAP = 0.5f; // relative to base font size

  public final String src;
  public final Font baseFont, monoFont;
  public final ArrayList<Block> blocks;
  public Block lastBlock;

  public Markdownish(String src, Font baseFont, Font monoFont) {
    this.src = src;
    this.baseFont = baseFont;
    this.monoFont = monoFont;
    blocks = new ArrayList<>();
    lastBlock = null;
    parseBlocks();
  }

  public static final AttributedCharacterIterator.Attribute SPAN_ID =
    new AttributedCharacterIterator.Attribute("logisim.markdown.span") {
      private static final long serialVersionUID = 1L;
    };

  enum BlockType { HEADER, PARAGRAPH }
  public final class Block {
    final BlockType type;
    final int blockno;
    final ArrayList<Span> spans;
    final int headerLevel; // 1..N for headers, unused for other block types
    final boolean paraContinuation; // this para continues previous one, after a hard-break

    private Block(BlockType type, ArrayList<Span> spans, int headerLevel, boolean paraContinuation) {
      this.type = type;
      this.blockno = blocks.size();
      this.spans = spans;
      this.headerLevel = headerLevel;
      this.paraContinuation = paraContinuation;
    }

    public AttributedString buildAttributedString() {
      StringBuilder sb = new StringBuilder();
      ArrayList<AttrRun> runs = new ArrayList<>();

      Font font;
      if (type == BlockType.HEADER) {
        font = baseFont.deriveFont(
            baseFont.getStyle() | Font.BOLD,
            baseFont.getSize2D() * HEADER_FONT_SIZE[headerLevel]);
      } else {
        font = baseFont;
      }
      
      AttrRun prev = null;
      for (Span span : spans) {
        int outStart = sb.length();
        if (span.type == SpanType.SPACE) {
          sb.append(' ');
        } else { // TEXT
          sb.append(src, span.start, span.end);
        }
        int outEnd = sb.length();
        if (outEnd == outStart) continue;
          
        runs.add(new AttrRun(outStart, outEnd, SPAN_ID, span));

        Font f = adjustForStyle(font, span.style);

        // Merge with previous run if same font and adjacent
        if (prev != null && prev.end == outStart && prev.value.equals(f)) {
          prev.end = outEnd;
        } else {
          AttrRun run = new AttrRun(outStart, outEnd, TextAttribute.FONT, f);
          runs.add(run);
          prev = run;
        }

      }
      AttributedString as = new AttributedString(sb.toString());
      for (AttrRun r : runs)
        as.addAttribute(r.key, r.value, r.start, r.end);
      return as;
    }

    private float gapAbove() {
      float sz = baseFont.getSize2D();
      if (type == BlockType.PARAGRAPH)
        return sz * PARAGRAPH_ABOVE_GAP;
      else
        return sz * HEADER_ABOVE_GAP;
    }

    private float gapBelow() {
      float sz = baseFont.getSize2D();
      if (type == BlockType.PARAGRAPH)
        return sz * PARAGRAPH_BELOW_GAP;
      else
        return sz * HEADER_BELOW_GAP;
    }
  }

  private void add(Block nextBlock) {
    blocks.add(nextBlock);
    lastBlock = nextBlock;
  }

  private void emitHeader(ArrayList<Span> spans, int level) {
    add(new Block(BlockType.HEADER, spans, level, false));
  }

  private void emitParagraph(ArrayList<Span> spans, boolean paraContinuation) {
    if (spans.isEmpty()) // note: probably always non-empty here, but check anyway
      return;
    add(new Block(BlockType.PARAGRAPH, spans, 0, paraContinuation));
  }

  public float gapAbove(Block next) {
    if (next.blockno == 0)
      return 0f;
    Block prev = blocks.get(next.blockno - 1);
    if (prev.type == BlockType.PARAGRAPH && next.type == BlockType.PARAGRAPH && next.paraContinuation)
      return 0f;
    return Math.max(prev.gapBelow(), next.gapAbove());
  }

  enum InlineStyle { PLAIN, BOLD, ITALIC, BOLD_ITALIC, CODE }
  Font adjustForStyle(Font font, InlineStyle style) {
    switch (style) {
      case CODE:
        return monoFont.deriveFont(font.getStyle(), font.getSize2D());
      case BOLD:
        return font.deriveFont(font.getStyle() | Font.BOLD);
      case ITALIC:
        return font.deriveFont(font.getStyle() | Font.ITALIC);
      case BOLD_ITALIC:
        return font.deriveFont(font.getStyle() | Font.BOLD | Font.ITALIC);
      case PLAIN:
      default:
        return font;
    }
  }

  enum SpanType { TEXT, SPACE }
  static final class Span {
    final SpanType type;
    final int start;   // index in src text (inclusive)
    final int end;     // index in src text (exclusive)
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
  }

  static final class AttrRun {
    int start;   // in rendered string (inclusive)
    int end;     // in rendered string (exclusive)
    AttributedCharacterIterator.Attribute key;
    Object value;
    AttrRun(int s, int e, AttributedCharacterIterator.Attribute k, Object v) {
      start = s;
      end = e;
      key = k;
      value = v;
    }
  }

  private final class ParaBuilder {
    ArrayList<Span> spans = new ArrayList<>();
    // invariant: first span (if any) is not a SPACE 
    // invariant: span does not contain consecutive SPACEs
    boolean isContinuation;
    int hardBreakIndex = -1;

    void extend(int ls, ArrayList<Span> more) {
      if (more.isEmpty())
        return;
      Span next = more.get(0);
      if (spans.isEmpty()) {
        if (next.type == SpanType.SPACE)
          more.remove(0);
      } else {
        Span last = spans.get(spans.size() - 1);
        if (last.type == SpanType.SPACE && next.type == SpanType.SPACE) {
          more.remove(0);
        } else if (last.type != SpanType.SPACE && next.type != SpanType.SPACE) {
          // insert space between last and next, where newline must have been
          spans.add(Span.space(ls-1, ls));
        }
      }
      spans.addAll(more);
    }

    void flushHardBreak() {
      if (hardBreakIndex >= 0) {
        if (src.charAt(hardBreakIndex) == '\\') {
          spans.add(Span.text(hardBreakIndex, hardBreakIndex+1, 
                spans.get(spans.size() - 1).style));
        }
        hardBreakIndex = -1;
      }
    }

    void emitAndReset() { // encountered a non-paragraph line
      flushHardBreak();
      if (!spans.isEmpty()) {
        if (spans.get(spans.size() - 1).type == SpanType.SPACE)
          spans.remove(spans.size() -1);
        emitParagraph(spans, isContinuation);
        spans = new ArrayList<>();
      }
      isContinuation = false;
    }

    void encounteredParagraphLine() {
      if (hardBreakIndex < 0) {
        // encountered a valid paragraph line after a previous paragraph without hard break...
        // do nothing: this line simply extends the current paragraph
        return;
      }
      // encountered a valid paragraph line after a previous paragraph with hard break...
      // prep for new continuation-paragraph: swallow the hard break, emit current paragraph, and
      if (spans.isEmpty()) {
        // The just-completed paragraph section was empty and will be rendered as a blank line. To
        // preserve the index mapping, we include a SPACE span where the hard break was
        spans.add(Span.space(hardBreakIndex, hardBreakIndex+1));
        emitParagraph(spans, isContinuation);
        spans = new ArrayList<>();
      } else {
        // non-empty section of paragraph, strip trailing space and emit it.
        if (spans.size() > 1) {
          Span last = spans.get(spans.size() - 1);
          if (last.type == SpanType.SPACE)
            spans.remove(spans.size() - 1);
        }
        emitParagraph(spans, isContinuation);
        spans = new ArrayList<>();
      }
      hardBreakIndex = -1;
      isContinuation = true;
    }
  }

  private void parseBlocks() {
    int i = 0, n = src.length();

    ParaBuilder para = new ParaBuilder();

    while (i < n) {
      int ls = i;
      int le = lineEnd(i); // line is [ls, le), followed by EOL
      int next = (le < n) ? le + 1 : n; // consume '\n' if present

      // Skip blank lines
      if (isBlankLine(ls, le)) {
        para.emitAndReset();
        i = next;
        continue;
      }

      // Header block
      if (parseHeaderLine(ls, le)) {
        para.emitAndReset();
        i = next;
        continue;
      }

      // Paragraph block (consecutive non-blank, non-header lines)
      para.encounteredParagraphLine();

      // Check for trailing hard-break
      int hb = trailingHardBreak(ls, le);
      if (hb >= 0) {
        para.hardBreakIndex = hb;
        le = hb;
      } else {
        para.hardBreakIndex = -1;
      }

      para.extend(ls, parseLineSpans(ls, le));
      i = next;
    }

    // emit any remaining accumulated paragraph
    para.emitAndReset();
  }

  private final class SpanList {
    ArrayList<Span> spans = new ArrayList<>();
    int textRunStart = -1;
    int wsRunStart = -1;

    void markText(int pos) {
      if (textRunStart < 0)
        textRunStart = pos;
    }
    
    void flushTextUpTo(int pos) {
      if (textRunStart >= 0 && textRunStart < pos) {
        spans.add(Span.text(textRunStart, pos, InlineStyle.PLAIN));
      }
      textRunStart = -1;
    };

    void markWs(int pos) {
      if (wsRunStart < 0)
        wsRunStart = pos;
    }

    void flushWsUpTo(int pos) {
      if (wsRunStart >= 0 && wsRunStart < pos) {
        spans.add(Span.space(wsRunStart, pos));
      }
      wsRunStart = -1;
    };
  }

  // parse the single line of src text [s, e) into Spans
  private ArrayList<Span> parseLineSpans(int s, int e) {
   
    // do NOT strip leading spaces and tabs
    // while (s < e && isSpaceOrTab(src.charAt(s))) s++;
    
    // do NOT strip trailing spaces and tabs
    // while (s < e && isSpaceOrTab(src.charAt(e-1))) e++;
    
    SpanList c = new SpanList();
    while (s < e) {
      char ch = src.charAt(s);
      if (isSpaceOrTab(ch)) {
        c.flushTextUpTo(s);
        c.markWs(s);
      } else {
        c.flushWsUpTo(s);
        c.markText(s);
      }
      s++;
    }
    c.flushTextUpTo(s);
    c.flushWsUpTo(s);
    return c.spans;
  }

  private int trailingHardBreak(int ls, int le) {
    if (ls < le-1 && src.charAt(le-1) == ' ' && src.charAt(le-2) == ' ')
      return le-1; // double space before EOL is a hard-break
    // count backslashes
    int e = le-1, count = 0;
    while (ls < e && src.charAt(e) == '\\') {
      count++;
      e--;
    }
    if (count % 2 != 0)
      return le-1; // odd number of backslashes before EOL is a hard-break
    return -1;
  }

  // All \r should have been stripped already, so only \n or end of source can end a line
  private int lineEnd(int i) {
    while (i < src.length() && src.charAt(i) != '\n')
      i++;
    return i; // exclusive, points at '\n', or n
  }

  // whitespace within a line: only plain spaces and tabs
  private boolean isSpaceOrTab(char ch) {
    return ch == ' ' || ch == '\t';
  }

  // blank line: only plain spaces and tabs
  private boolean isBlankLine(int ls, int le) {
    for (int i = ls; i < le; i++) {
      if (!isSpaceOrTab(src.charAt(i)))
        return false;
    }
    return true;
  }

  private boolean parseHeaderLine(int ls, int le) {
    int s = ls, e = le;
    // ignore up to 3 leading spaces
    if (s < e && src.charAt(s) == ' ') s++;
    if (s < e && src.charAt(s) == ' ') s++;
    if (s < e && src.charAt(s) == ' ') s++;
    // 1-6 "#" chars
    int lvl = 0;
    for (int i = s; lvl < 6 && i < e && src.charAt(i) == '#'; i++)
      lvl++;
    if (lvl < 1 || lvl > 6)
      return false;
    s += lvl;
    // followed by EOL, space, or tab
    if (s != e && !isSpaceOrTab(src.charAt(s)))
      return false;
    // strip leading spaces and tabs
    while (s < e && isSpaceOrTab(src.charAt(s))) s++;
    // strip trailing spaces and tabs
    while (s < e && isSpaceOrTab(src.charAt(e-1))) e++;
    // strip optional trailing space-or-tab-then-### sequence
    int e2 = e;
    while (s < e2 && src.charAt(e2-1) == '#') e2--;
    if (s < e2 && isSpaceOrTab(src.charAt(e2-1))) {
      e = e2;
      // again strip trailing spaces and tabs
      while (s < e && isSpaceOrTab(src.charAt(e-1))) e++;
    }
    if (s == e) {
      // empty header: to preserve the index mapping, we include an empty SPACE span
      ArrayList<Span> spans = new ArrayList<>();
      spans.add(Span.space(s, e));
      emitHeader(spans, lvl);
    } else {
      ArrayList<Span> spans = parseLineSpans(s, e);
      emitHeader(spans, lvl);
    }
    return true;
  }

}
