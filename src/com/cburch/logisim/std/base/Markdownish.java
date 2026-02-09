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
        span.renderTo(sb);
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

  private void emit(Block nextBlock) {
    blocks.add(nextBlock);
    lastBlock = nextBlock;
  }

  private void emitParagraph(ArrayList<Span> spans, boolean paraContinuation) {
    if (spans.isEmpty())
      return;
    emit(new Block(BlockType.PARAGRAPH, spans, 0, paraContinuation));
  }

  public float gapAbove(Block next) {
    if (next.blockno == 0)
      return 0f;
    Block prev = blocks.get(next.blockno - 1);
    if (prev.type == BlockType.PARAGRAPH && next.type == BlockType.PARAGRAPH && next.paraContinuation)
      return 0f;
    return Math.max(prev.gapBelow(), next.gapAbove());
  }

  static final int PLAIN = 0;
  static final int CODE = 1;
  static final int BOLD = 2;
  static final int ITALIC = 4;
  static final int BOLD_ITALIC = BOLD|ITALIC;
  
  Font adjustForStyle(Font font, int style) {
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

  enum SpanType {
    TEXT,      // TEXT renders directly from src indices [start, end)
    SPACE,     // SPACE renders as " "
    HARDBREAK, // HARDBREAK is not rendered
               // Note: a misspaced hardbreak at the end of a paragraph is replaced by TEXT
               // just before applying styles, and all remaining hardbreaks are removed
               // as styles are applied just before emitting paragraphs.
               // So no hardbreaks reach the rendering stage.
    DELIM      // DELIM renders directly from src indices [start, end)
               // Note: paired delimiters are removed before rendering, only unpaired delimiters
               // will reach the rendering stage.
  }

  private Span Span_text(int s, int e) { return new Span(SpanType.TEXT, s, e); }
  private Span Span_hardbreak(int s, int e) { return new Span(SpanType.HARDBREAK, s, e); }
  private Span Span_space(int s, int e) { return new Span(SpanType.SPACE, s, e); }
  private Span Span_delim(int s, int e) { return new Span(SpanType.DELIM, s, e); }

  final class Span {

    final SpanType type;
    final int start;     // index in src text (inclusive)
    final int end;       // index in src text (exclusive)
    int style = PLAIN;   // adjusted during second stage of parsing
    boolean pairedLeft;  // DELIM is the left part of a matching pair
    boolean pairedRight; // DELIM is the right part of a matching pair

    Span(SpanType t, int s, int e) {
      type = t;
      start = s;
      end = e;
    }

    void renderTo(StringBuilder sb) {
      switch (type) {
        case TEXT:
          sb.append(src, start, end);
          break;
        case SPACE:
          sb.append(' ');
          break;
        case HARDBREAK:
          // does not render
          break;
        case DELIM:
          sb.append(src, start, end);
          break;
      }
    }

    boolean canPairLeft() { // this DELIM can be the left part of a pair
      if (pairedLeft || pairedRight)
        return false;
      // right side can't be whitespace (or end of line, or end of src text)
      if (end == src.length() - 1 || Character.isWhitespace(src.charAt(end)))
        return false;
      return true;
    }

    boolean canPairRight() { // this DELIM can be the right part of a pair
      if (pairedLeft || pairedRight)
        return false;
      // left side can't be whitespace (or start of line, or start of src text)
      if (start == 0 || Character.isWhitespace(src.charAt(start-1)))
        return false;
      return true;
    }

    boolean matchingDelim(Span other) { // this and other DELIM have same text
      return src.charAt(this.start) == src.charAt(other.start)
        && (this.end - this.start) == (other.end - other.start);
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
    // invariant: first span in list is not a SPACE and
    //            first span after any HARDBREAK is not a SPACE
    //            (we drop the initial SPACE in these cases)
    // invariant: no consecutive HARDBREAK spans
    //            (we insert a SPACE if needed)
    // invariant: no consecutive SPACE spans
    //            (we drop the second SPACE in this case)

    Span getCurrentSectionLastSpan() {
      // returns null if entire paragraph is empty, or the current section is empty,
      // i.e. if the most recent span was a hardbreak.
      if (spans.isEmpty())
        return null;
      Span last = spans.get(spans.size()-1);
      if (last.type == SpanType.HARDBREAK)
        return null;
      return last;
    }

    void extend(int ls, ArrayList<Span> more) {
      if (more.isEmpty())
        return;
      Span next = more.get(0);
      Span last = getCurrentSectionLastSpan();
      if (last == null) {
        if (next.type == SpanType.SPACE)
          more.remove(0); // remove leading SPACE in a section
      } else if (last.type == SpanType.SPACE && next.type == SpanType.SPACE) {
        more.remove(0); // remove consecutive SPACE
      } else if (last.type != SpanType.SPACE && next.type != SpanType.SPACE) {
        // insert SPACE between last and next, where newline must have been
        spans.add(Span_space(ls-1, ls));
      }
      spans.addAll(more);
    }

    void emitAndReset() { // encountered a non-paragraph line
      if (spans.isEmpty())
        return;
      applyInlineStylesAndEmitParagraphs(spans);
      spans = new ArrayList<>();
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
      Block header = parseHeaderLine(ls, le);
      if (header != null) {
        para.emitAndReset();
        emit(header);
        i = next;
        continue;
      }

      // Paragraph block (consecutive non-blank, non-header lines)
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
        spans.add(Span_text(textRunStart, pos));
      }
      textRunStart = -1;
    };

    void markWs(int pos) {
      if (wsRunStart < 0)
        wsRunStart = pos;
    }

    void flushWsUpTo(int pos) {
      if (wsRunStart >= 0 && wsRunStart < pos) {
        spans.add(Span_space(wsRunStart, pos));
      }
      wsRunStart = -1;
    };

    void flushAllUpTo(int pos) {
      flushTextUpTo(pos);
      flushWsUpTo(pos);
    }
  }

  // Parse the single line of src text [s, e) into Spans.
  // Approximately, this handles...
  //  (1) most regular text and punctuation --> TEXT
  //  (2) sequence of 2 or more spaces before EOL --> HARDBREAK
  //  (3) sequence of spaces and tabs elsewhere --> SPACE
  //  (4) escaped punctuation --> TEXT
  //  (5) unescaped backslash before EOL --> HARDBREAK
  //  (6) unescaped backslash elsewhere --> TEXT
  //  (7) left or right flanking delimiter run of * or _ chars --> DELIM
  private ArrayList<Span> parseLineSpans(int ls, int le) {
    SpanList out = new SpanList();

    // (2) or (5): find and strip trailing hardbreak, if present
    int hb = trailingHardBreak(ls, le);
    int e = hb < 0 ? le : hb;

    // Process remaining text
    for (int s = ls; s < e; s++) {
      char ch = src.charAt(s);

      if (ch == '\\' && s + 1 < e && isAsciiPunct(src.charAt(s+1))) {
        // (4) escaped punctuation --> TEXT
        out.flushAllUpTo(s);
        s++; // swallow escape
        out.spans.add(Span_text(s, s+1)); // just the punctuation
      } else if (ch == '*' || ch == '_') {
        // (7) unescaped delimiters --> DELIM
        out.flushAllUpTo(s);
        int count = 0;
        while (src.charAt(s+count) == ch)
          count++;
        out.spans.add(Span_delim(s, s+count));
        s += count-1;
      } else if (ch == ' ' || ch == '\t') {
        // (3) sequence of spaces and tabs --> SPACE
        out.flushTextUpTo(s);
        out.markWs(s);
      } else {
        // (1) regular text and punctuation, or (6) unescaped backslash --> TEXT
        out.flushWsUpTo(s);
        out.markText(s);
      }
    }
    out.flushAllUpTo(e);

    // Append hardbreak, if needed
    if (hb >= 0)
      out.spans.add(Span_hardbreak(hb, le));
    
    return out.spans;
  }

  // Scan the raw spans and apply styles.
  // Approximately, this means:
  // (1) Remove misplaced trailing HARDBREAK, or convert it to TEXT
  // (2) Remove matched DELIM spans, and apply style to the spans they encompass
  private ArrayList<Span> applyInlineStyles(ArrayList<Span> raw) {
    ArrayList<Span> out = new ArrayList<>();
    int n = raw.size();

    if (n ==  0)
      return out;

    // detect misplaced trailing hardbreak
    Span misplacedHardbreak = null;
    if (raw.get(n - 1).type == SpanType.HARDBREAK) {
      misplacedHardbreak = raw.get(n - 1);
      n--;
    }

    // identify DELIM pairings
    for (int i = 0; i < n; i++) {
      Span span = raw.get(i);
      if (span.type == SpanType.DELIM && span.canPairLeft()) {
        // search rightward, first match wins
        for (int j = i+1; j < n; j++) {
          Span right = raw.get(j);
          if (right.canPairRight() && span.matchingDelim(right)) {
            span.pairedLeft = true;
            right.pairedRight = true;
            break;
          }
        }
      }
    }

    // apply styles and remove matched DELIM spans
    int bold = 0, italic = 0;
    for (int i = 0; i < n; i++) {
      Span span = raw.get(i);
      if (span.type == SpanType.DELIM && span.pairedLeft) {
        int delimLen = span.end - span.start;
        if (delimLen == 1) { italic++; }
        else if (delimLen == 2) { bold++; }
        else if (delimLen >= 3) { italic++; bold++; } // note: non-standard
        // do not emit 
      } else if (span.type == SpanType.DELIM && span.pairedRight) {
        int delimLen = span.end - span.start;
        if (delimLen == 1) { italic--; }
        else if (delimLen == 2) { bold--; }
        else if (delimLen >= 3) { italic--; bold--; } // note: non-standard
        // do not emit 
      } else {
        if (bold > 0) span.style |= BOLD;
        if (italic > 0) span.style |= ITALIC;
        // emit
        out.add(span);
      }
    }

    // convert misplaced trailing hardbreak to TEXT if it was a backslash
    if (misplacedHardbreak != null && src.charAt(misplacedHardbreak.start) == '\\')
      out.add(Span_text(misplacedHardbreak.start, misplacedHardbreak.end));

    return out;
  }

  private int countTrailing(int ls, int le, char ch) {
    int count = 0;
    for(int e = le-1; ls <= e && src.charAt(e) == ch; e--)
      count++;
    return count;
  }

  private int trailingHardBreak(int ls, int le) {
    // 2 or more spaces before EOL is a hard-break
    int count = countTrailing(ls, le, ' ');
    if (count >= 2)
      return le - count;
    // 1 backslash before EOL is a hard-break, but only if odd number of backslashes
    count = countTrailing(ls, le, '\\');
    if (count % 2 != 0)
      return le - 1;
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

  private Block parseHeaderLine(int ls, int le) {
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
      return null;
    s += lvl;
    // followed by EOL, space, or tab
    if (s != e && !isSpaceOrTab(src.charAt(s)))
      return null;
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
      spans.add(Span_space(s, e));
      return new Block(BlockType.HEADER, spans, lvl, false);
    } else {
      ArrayList<Span> spans = parseLineSpans(s, e);
      spans = applyInlineStyles(spans);
      return new Block(BlockType.HEADER, spans, lvl, false);
    }
  }

  // GFM/CommonMark: any ASCII punctuation can be backslash-escaped.
  private static boolean isAsciiPunct(char ch) {
    // ASCII punctuation set:
    // !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~
    return (ch >= 0x21 && ch <= 0x2F)
      || (ch >= 0x3A && ch <= 0x40)
      || (ch >= 0x5B && ch <= 0x60)
      || (ch >= 0x7B && ch <= 0x7E);
  }

  private void applyInlineStylesAndEmitParagraphs(ArrayList<Span> raw) {
    // apply styles
    ArrayList<Span> cooked = applyInlineStyles(raw);
    // split into sections
    ArrayList<Span> section = new ArrayList<>();
    boolean cont = false;
    for (Span span : cooked) {
      if (span.type == SpanType.DELIM && span.start == span.end) {
        // drop paired delim
        continue;
      } else if (span.type == SpanType.HARDBREAK) {
        // Emit current section (possibly empty)
        emitParagraph(section, cont);
        section = new ArrayList<>();
        cont = true;
      } else {
        section.add(span);
      }
    }
    emitParagraph(section, cont);
  }

}
