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

import java.awt.Color;
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
//   Headers, paragraphs, fenced code, bold, italics, inline code, etc.
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
//  - All of this is pretty much exactly in line with GFM conventions.
//
// Paragraphs - Consecutive non-blank, non-header lines make up a paragraph.
// Notes:
//  - Within paragraphs, spaces, tabs, and newlines are mostly collapsed to single spaces.
//  - Content is rendered with auto-wrapping to fit width of text box.
//  - A trailing "\", or 2 or more spaces, directly before a newline, acts like an html <br>,
//    introducing a hard break into the auto-wrapping layout.
//  - Such hard-breaks do not leave a inter-paragraph gap.
//  - Such hard-breaks aren't possible on the last line of a paragraph. That is, a hard break
//    is inserted iff the backslash-newline or space-space-newline sequence is followed directly by
//    a line that isn't blank (i.e. it contains somethign other than spaces and tabs) and isn't a
//    header (i.e. it doesn't start with a #-space or #-newline sequence, etc.) or fenced code
//    block.
//  - On the other hands, paragraphs can begin with hard breaks, as many as you like!
//
// Fenced code blocks - start/end with 3 or more backticks, can interrupt a paragraph
// Notes:
//  - Tabs at the beginning don't function as 4-spaces, as they do in GFM.
//  - Tabs probably don't render at all, actually. FIXME?
//
// Inline *italics*, **bold**, ***bolditalics***, and `code` - keep it simple please.
// Notes:
//  - Underscores work too, but not in the middle of words, as typical.
//  - Works in headers and paragraphs.
//  - More than 3 delimiters is the same as 3.
//  - Nesting is fine, but not partial matches. For example, "***" must match with "***" exactly, it
//    can't match with a "*" to get italics, then later with "**" to get bold.
//
// [link text](http://whatever) - Basic links
// Notes:
//  - No titles, just the link text and url, unlike GFM.
//  - Link text can't have unescaped square brackets, unlike GFM.
//  - Link square brackets have the same precedence as backticks, unlike GFM.
//    So `this[is`](code://followed+by+garbled+stuff)
//    But [this`is](http://a.strange.link/followed)by`backtick
//  - The url can't have unescpaed parens, unlike GFM.
//  - The url part can't be surrounded by <angles>, unlike GFM.
//  - The url can't have newlines, spaces, or tabs. Use %20 and other url escapes instead.
//  - Spaces around the url are discarded.
//
// | table | with | headers |
// | :---- | :--: | ------: |
// | or    | without | them |
// Notes:
// - at most 3 spaces, then starts with unescaped pipe, ends with unescaped pipe
// - row with only dashes (and spaces) makes a horizontal separator between rows
// - row with only 
// - inline formatting within cells is fine
// - if table width doesn't fit within text width, columns are allocated space proportionally to how
// many dashes they have in the 
//
//
// Other Notes:
// - Header, paragraph, and fenced code blocks are each rendered with some space below (and above)
//   them, unless there are no other blocks below (or above) them. 
// - Gaps before/after blocks are not additive, they "overlap". There is no gap at the start or end
//   of the markdown, only between the blocks.
// - Blank lines are mostly not considered "content". They only help define the boundaries of
//   paragraphs, but are not part of the paragraphs. "Blank line" here means a line with only spaces
//   and tabs.
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
  public final int EOF;
  public final Font baseFont, monoFont;
  public final ArrayList<Block> blocks;
  public Block lastBlock;

  public Markdownish(String src, Font baseFont, Font monoFont) {
    this.src = src;
    this.EOF = src.length();
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

  enum BlockType { HEADER, PARAGRAPH, FENCED_CODE }
  public final class Block {
    final BlockType type;
    final int blockno;
    final ArrayList<Span> spans; // not empty; otherwise, we can't map block to src text index
    final int headerLevel; // 1..N for headers, unused for other block types
    final boolean continuation; // this para or code continues previous one, after a hard-break

    private Block(BlockType type, ArrayList<Span> spans, int headerLevel, boolean continuation) {
      this.type = type;
      this.blockno = blocks.size();
      this.spans = spans;
      this.headerLevel = headerLevel;
      this.continuation = continuation;
    }

    public AttributedString buildAttributedString() {
      StringBuilder sb = new StringBuilder();
      ArrayList<AttrRun> runs = new ArrayList<>();

      Font font;
      if (type == BlockType.HEADER) {
        font = baseFont.deriveFont(
            baseFont.getStyle() | Font.BOLD,
            baseFont.getSize2D() * HEADER_FONT_SIZE[headerLevel]);
      } else if (type == BlockType.FENCED_CODE) {
        font = monoFont;
      } else {
        font = baseFont;
      }

      AttrRun prevFont = null;
      AttrRun prevUnderline = null;
      AttrRun prevColor = null;
      for (Span span : spans) {
        int outStart = sb.length();
        span.renderTo(sb);
        int outEnd = sb.length();
        if (outEnd == outStart) continue;

        runs.add(new AttrRun(outStart, outEnd, SPAN_ID, span));

        // Apply font, merging with previous run if same value and adjacent
        Font f = adjustForStyle(font, span.style);
        if (prevFont != null && prevFont.end == outStart && prevFont.value.equals(f)) {
          prevFont.end = outEnd;
        } else {
          AttrRun run = new AttrRun(outStart, outEnd, TextAttribute.FONT, f);
          runs.add(run);
          prevFont = run;
        }

        // Apply underlining, merging with previous run if same value and adjacent
        Object u = (span.style & CLICKABLE) != 0 ? TextAttribute.UNDERLINE_ON : null;
        if (prevUnderline != null && prevUnderline.end == outStart && prevUnderline.value.equals(u)) {
          prevUnderline.end = outEnd;
        } else if (u != null) {
          AttrRun run = new AttrRun(outStart, outEnd, TextAttribute.UNDERLINE, u);
          runs.add(run);
          prevUnderline = run;
        }

        // Apply color, merging with previous run if same value and adjacent
        Color c = (span.style & CLICKABLE) != 0 ? new Color(155, 155, 0, 128) : null;
        if (prevColor != null && prevColor.end == outStart && prevColor.value.equals(c)) {
          prevColor.end = outEnd;
        } else if (c != null) {
          AttrRun run = new AttrRun(outStart, outEnd, TextAttribute.BACKGROUND, c);
          runs.add(run);
          prevColor = run;
        }

      }
      AttributedString as = new AttributedString(sb.toString());
      for (AttrRun r : runs)
        as.addAttribute(r.key, r.value, r.start, r.end);
      return as;
    }

    private float gapAbove() {
      float sz = baseFont.getSize2D();
      if (type == BlockType.PARAGRAPH || type == BlockType.FENCED_CODE)
        return sz * PARAGRAPH_ABOVE_GAP;
      else
        return sz * HEADER_ABOVE_GAP;
    }

    private float gapBelow() {
      float sz = baseFont.getSize2D();
      if (type == BlockType.PARAGRAPH || type == BlockType.FENCED_CODE)
        return sz * PARAGRAPH_BELOW_GAP;
      else
        return sz * HEADER_BELOW_GAP;
    }

    public boolean wrapped() {
      return type != BlockType.FENCED_CODE;
    }
  }

  private void emit(Block nextBlock) {
    blocks.add(nextBlock);
    lastBlock = nextBlock;
  }

  public float gapAbove(Block next) {
    if (next.blockno == 0)
      return 0f;
    Block prev = blocks.get(next.blockno - 1);
    if (prev.type == next.type && next.continuation)
      return 0f;
    return Math.max(prev.gapBelow(), next.gapAbove());
  }

  static final int PLAIN = 0;
  static final int MONOSPACE = 1;
  static final int BOLD = 2;
  static final int ITALIC = 4;
  static final int BOLD_ITALIC = BOLD|ITALIC;
  static final int CLICKABLE = 8;
  
  Font adjustForStyle(Font font, int style) {
    if ((style & MONOSPACE) != 0)
      font = monoFont.deriveFont(font.getStyle(), font.getSize2D());
    switch (style & (BOLD | ITALIC)) {
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
    CODE_SPAN, // renders from src indices [start, end) but replaces newlines with spaces
    // LINK_BEGIN,// LINK_BEGIN and LINK_END render as U+200B (zero width space), and change the
    // LINK_END,  // styling and mouse/click handling of any span between them.
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
  private Span Span_code(int s, int e) { return new Span(SpanType.CODE_SPAN, s, e); }
  private Span Span_hardbreak(int s, int e) { return new Span(SpanType.HARDBREAK, s, e); }
  private Span Span_space(int s, int e) { return new Span(SpanType.SPACE, s, e); }
  private Span Span_delim(int s, int e) { return new Span(SpanType.DELIM, s, e); }

  final class Span {

    final SpanType type;
    final int start;     // index in src text (inclusive)
    final int end;       // index in src text (exclusive)
    int style;           // adjusted during second stage of parsing
    boolean pairedLeft;  // DELIM is the left part of a matching pair
    boolean pairedRight; // DELIM is the right part of a matching pair
    String url;          // for spans that are part of a link

    Span(SpanType t, int s, int e) {
      type = t;
      start = s;
      end = e;
      style = (type == SpanType.CODE_SPAN ? MONOSPACE : PLAIN);
    }

    void renderTo(StringBuilder sb) {
      switch (type) {
        case TEXT:
          sb.append(src, start, end);
          break;
        case CODE_SPAN:
          sb.append(src.substring(start, end).replace('\n', ' '));
          break;
        case SPACE:
          sb.append(' ');
          break;
        case HARDBREAK:
          // does not render
          break;
        case DELIM:
          // render iff unpaired
          if (!pairedLeft && !pairedRight)
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
      // for underscores, must have whitespace on left side, or start of src text
      if (src.charAt(start) == '_' && !(start == 0 || Character.isWhitespace(src.charAt(start-1))))
        return false;
      return true;
    }

    boolean canPairRight() { // this DELIM can be the right part of a pair
      if (pairedLeft || pairedRight)
        return false;
      // left side can't be whitespace (or start of line, or start of src text)
      if (start == 0 || Character.isWhitespace(src.charAt(start-1)))
        return false;
      // for underscores, must have whitespace on right side, or end of src text
      if (src.charAt(start) == '_' && !(end == src.length() - 1 || Character.isWhitespace(src.charAt(end))))
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
  
  private void parseBlocks() {
    int ls = 0;
    while (ls < EOF) {
      int le = lineEnd(ls); // line is [ls, le), and le is EOL or EOF

      // skip blank lines
      if (isBlankLine(ls, le)) {
        ls = le + 1; // skip EOL or EOF
      }

      // parse header block
      else if (isHeaderLine(ls, le)) {
        parseHeaderLine(ls, le);
        ls = le + 1; // skip EOL or EOF
      }

      // parse fenced code block
      else if (isOpeningCodeFence(ls, le)) {
        ls = parseFencedCode(ls, le);
      }

      // anything else must be a paragraph
      else {
        ls = parseParagraph(ls);
      }
    }
  }

  // Parse src text [ps, pe) as inline spans. 
  // Precondition:
  //  There are no blank lines within [ps, pe)
  // Approximately, this handles...
  //  (0) leading and trailing spaces, tabs, and newlines --> ignored
  //  (1) most regular text and punctuation --> TEXT
  //  (2) sequence of 2 or more spaces before newline --> HARDBREAK
  //  (3) sequence of spaces and tabs elsewhere --> SPACE
  //  (4) escaped punctuation --> TEXT
  //  (5) unescaped backslash before newline or pe --> HARDBREAK
  //  (6) unescaped backslash elsewhere --> TEXT
  //  (7) left or right flanking delimiter run of * or _ chars --> DELIM
  //  (8) newline --> SPACE
  //  (9) backticks around text --> CODE
  //  (10) unmatched backticks --> TEXT
  //  (11) [inline](url) --> LINK_BEGIN, ..., LINK_END
  // Postconditions for resulting list:
  //  (a) List is not empty
  //      (we insert a SPACE, if needed)
  //  (b) Before, after, and between each HARDBREAK is some other span
  //      (we insert a SPACE, if needed)
  //  (c) SPACE does not appear as first or last span, or directly before or after HARDBREAK,
  //      unless it is the only span in that section
  //      (we omit leading and trailing SPACE, where possible)
  private ArrayList<Span> parseInlineSpans(int ps, int pe) {
    
    // (0) strip trailing spaces, tabs, and newlines
    while (ps < pe && isSpaceOrTabOrNewline(src.charAt(pe-1))) pe--;
    
    // (0) strip leading spaces, tabs, and newlines
    while (ps < pe && isSpaceOrTabOrNewline(src.charAt(ps))) ps++;

    // Process remaining text
    return new InlineSpanParser(ps, pe).parse();
  }

  private final class InlineSpanParser {
    ArrayList<Span> spans = new ArrayList<>();
    int spaceRunStart = -1; // spaces, tabs, newlines
    int textRunStart = -1; // anything else
    int pos, end;

    InlineSpanParser(int ps, int pe) { pos = ps; end = pe; }
    ArrayList<Span> parse() {
      for (; pos < end; pos++) {
        char ch = src.charAt(pos);

        if (ch == '`') {
          // possibly the start of an inline code span
          parseInlineCode();
        } else if (ch == '[' && parseLink()) {
          //  (11) [inline](url) --> LINK_BEGIN, ..., LINK_END
        } else if (ch == '\\' && (pos + 1 == end || src.charAt(pos+1) == '\n')) {
          // (5) unescaped backslash before newline or end --> HARDBREAK
          markAsOther();
          addSpaceIfEmptySection(); // ensure no hardbreak at start, no consecutive hardbreak
          spans.add(Span_hardbreak(pos, pos+1)); // just the backslash
        } else if (ch == '\n' && haveTwoSpacesBefore()) {
          // (2) sequence of 2 or more spaces before newline --> HARDBREAK
          int hb = pos;
          pos = spaceRunStart; // rewind to start of recent spaces
          spaceRunStart = -1; // cancel recent spaces
          addSpaceIfEmptySection(); // ensure no hardbreak at start, no consecutive hardbreak
          spans.add(Span_hardbreak(pos, hb)); // just the whitespace
          pos = hb; //  fast-forward back to where we started
        } else if (ch == '\\' && pos + 1 < end && isAsciiPunct(src.charAt(pos+1))) {
          // (4) escaped punctuation --> TEXT
          markAsOther();
          removeLeadingSpace();
          pos++; // swallow escape
          spans.add(Span_text(pos, pos+1)); // just the punctuation
        } else if (ch == '*' || ch == '_') {
          // (7) unescaped delimiters --> DELIM
          markAsOther();
          removeLeadingSpace();
          int count = 0;
          while (src.charAt(pos+count) == ch)
            count++;
          spans.add(Span_delim(pos, pos+count));
          pos += count-1; // fast-forward
        } else if (ch == ' ' || ch == '\t' || ch == '\n') {
          // (3) sequence of spaces and tabs --> SPACE
          // (8) newline --> SPACE
          markAsSpace();
        } else {
          // (1) regular text and punctuation --> TEXT
          // (6) unescaped backslash --> TEXT
          markAsText();
        }
      }
      pos = end; // just in case we overshot
      markAsOther();
      addSpaceIfEmptySection();

      return spans;
    }

    void parseInlineCode() {
      // possibly the start of an inline code span
      int delimLen = 1;
      while (pos+delimLen < end && src.charAt(pos+delimLen) == '`')
        delimLen++;
      int cs = pos+delimLen;
      int ce = cs+1;
      int ticks = 0;
      boolean found = false;
      boolean allBlank = true;
      while (!found && ce < end+1) {
        char ch = ce == end ? '\0' : src.charAt(ce);
        if (ch == '`') {
          ticks++;
          ce++;
        } else if (ticks == delimLen) {
          found = true;
          ce -= ticks;
        } else {
          if (ticks > 0 || !isSpaceOrTabOrNewline(ch))
            allBlank = false;
          ticks = 0;
          ce++;
        }
      }
      if (!found) {
        // (10) unmatched backticks --> TEXT
        markAsText();
        pos = cs - 1; // fast-forward to end of delimiter run
        markAsText();
      } else {
        // (9) backticks around text --> CODE
        // [cs, ce) is the code, it will be non-empty
        markAsOther();
        pos += ce + delimLen - 1; // consume
        if (!allBlank && src.charAt(cs) == ' ' && src.charAt(ce-1) == ' ') {
          cs++;
          ce--;
        }
        removeLeadingSpace();
        spans.add(Span_code(cs, ce));
      }
    }

    boolean parseLink() {
      // ensure open brace
      if (pos == end || src.charAt(pos) != '[')
        return false;
      int openBrace = pos;

      // ensure close brace
      int closeBrace = -1;
      boolean escaped = false;
      for (int p = openBrace + 1; closeBrace < 0 && p < end; p++) {
        char ch = src.charAt(p);
        if (!escaped && ch == ']') closeBrace = p;
        else if (ch  == '\\') escaped = !escaped;
        else escaped = false;
      }
      if (closeBrace < 0)
        return false;
   
      // ensure open paren
      if (closeBrace + 1 == end || src.charAt(closeBrace + 1) != '(')
        return false;
      int openParen = closeBrace + 1;

      // ensure close paren
      int closeParen = -1;
      escaped = false;
      for (int p = openParen + 1; closeParen < 0 && p < end; p++) {
        char ch = src.charAt(p);
        if (!escaped && ch == ')') closeParen = p;
        else if (ch == '\\') escaped = !escaped;
        else if (ch == '\t' || ch == '\n') return false; // disallow tabs and newlines in url
        else escaped = false;
      }
      if (closeParen < 0)
        return false;
    
      // trim leading and trailing spaces around url
      int urlStart = openParen + 1, urlEnd = closeParen;
      while (urlStart < urlEnd && src.charAt(urlStart) == ' ') urlStart++;
      while (urlStart < urlEnd && src.charAt(urlEnd - 1) == ' ') urlEnd--;

      // we never bothered to check for spaces in url, oh well
      String url = src.substring(urlStart, urlEnd);

      markAsOther();
      pos = closeParen; // fast-forward
     
      // // LINK_BEGIN
      // Span linkBegin = new Span(SpanType.LINK_BEGIN, openBrace, openBrace+1);
      // linkBegin.url = url;
      // spans.add(linkBegin);

      // link text
      ArrayList<Span> linkText = parseInlineSpans(openBrace+1, closeBrace);
      for (Span span : linkText) {
        span.style |= CLICKABLE;
        span.url = url; // good enough? we'll see
      }
      spans.addAll(linkText);

      // // LINK_END
      // Span linkEnd = new Span(SpanType.LINK_END, openBrace, openBrace+1);
      // linkEnd.url = url;
      // spans.add(linkEnd);

      return true;
    }

    void markAsText() {
      endOfSpace();
      removeLeadingSpace();
      if (textRunStart < 0)
        textRunStart = pos;
    }

    void endOfText() {
      if (textRunStart >= 0 && textRunStart < pos)
        spans.add(Span_text(textRunStart, pos));
      textRunStart = -1;
    };

    void markAsSpace() {
      endOfText();
      if (spaceRunStart < 0)
        spaceRunStart = pos;
    }

    void endOfSpace() {
      if (spaceRunStart >= 0 && spaceRunStart < pos)
        spans.add(Span_space(spaceRunStart, pos));
      spaceRunStart = -1;
    };

    boolean haveTwoSpacesBefore() {
      if (spaceRunStart < 0)
        return false;
      if (pos - spaceRunStart < 2)
        return false;
      return src.charAt(pos-1) == ' ' && src.charAt(pos-2) == ' ';
    }

    void markAsOther() {
      endOfText();
      endOfSpace();
    }
    
    void removeLeadingSpace() {
      int n = spans.size();
      if (n == 1 && spans.get(0).type == SpanType.SPACE)
        spans.remove(0);
      else if (n >= 2
          && spans.get(n-2).type == SpanType.HARDBREAK
          && spans.get(n-1).type == SpanType.SPACE)
        spans.remove(n-1);
    }

    void addSpaceIfEmptySection() {
      if (spans.isEmpty() || spans.get(spans.size() - 1).type == SpanType.HARDBREAK) {
        if (pos > 0 && isSpaceOrTabOrNewline(src.charAt(pos-1)))
          spans.add(Span_space(pos-1, pos));
        else
          spans.add(Span_space(pos, pos+1)); // src is not actually a space, but oh well
      }
    }
  }


  // Match DELIM spans, and apply corresponding style to the spans they encompass
  private void applyInlineStyles(ArrayList<Span> spans) {
    int n = spans.size();

    // identify DELIM pairings
    for (int i = 0; i < n; i++) {
      Span span = spans.get(i);
      if (span.type == SpanType.DELIM && span.canPairLeft()) {
        // search rightward, first match wins
        for (int j = i+1; j < n; j++) {
          Span right = spans.get(j);
          if (right.canPairRight() && span.matchingDelim(right)) {
            span.pairedLeft = true;
            right.pairedRight = true;
            break;
          }
        }
      }
    }

    // apply styles
    int bold = 0, italic = 0;
    for (int i = 0; i < n; i++) {
      Span span = spans.get(i);
      
      if (span.type == SpanType.DELIM && span.pairedLeft) {
        // begin styled range
        int delimLen = span.end - span.start;
        if (delimLen == 1) { italic++; }
        else if (delimLen == 2) { bold++; }
        else if (delimLen >= 3) { italic++; bold++; } // note: non-standard
      }

      // apply current styles
      if (bold > 0) span.style |= BOLD;
      if (italic > 0) span.style |= ITALIC;

      if (span.type == SpanType.DELIM && span.pairedRight) {
        // end styled range
        int delimLen = span.end - span.start;
        if (delimLen == 1) { italic--; }
        else if (delimLen == 2) { bold--; }
        else if (delimLen >= 3) { italic--; bold--; } // note: non-standard
      }
    }

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
  private int lineEnd(int s) {
    while (s < src.length() && src.charAt(s) != '\n')
      s++;
    return s; // exclusive, points at '\n', or EOF
  }

  // whitespace within a line: only plain spaces and tabs
  private boolean isSpaceOrTab(char ch) {
    return ch == ' ' || ch == '\t';
  }

  private boolean isSpaceOrTabOrNewline(char ch) {
    return ch == ' ' || ch == '\t' || ch == '\n';
  }

  // blank line: only plain spaces and tabs
  private boolean isBlankLine(int ls, int le) {
    for (int i = ls; i < le; i++) {
      if (!isSpaceOrTab(src.charAt(i)))
        return false;
    }
    return true;
  }

  private int ignore3LeadingSpaces(int ls) {
    int s = ls;
    if (s < EOF && src.charAt(s) == ' ') s++;
    if (s < EOF && src.charAt(s) == ' ') s++;
    if (s < EOF && src.charAt(s) == ' ') s++;
    return s;
  }

  private int ignoreNLeadingSpaces(int ls, int n) {
    int s = ls;
    while (n-- > 0 && s < EOF && src.charAt(s) == ' ') s++;
    return s;
  }

  private boolean isOpeningCodeFence(int ls, int le) {
    int s = ignore3LeadingSpaces(ls);
    if (s == le)
      return false;
    // 3 or more backtick or tilde chars
    char delim = src.charAt(ls);
    if (delim != '`' && delim != '~')
      return false;
    int count;
    for (count = 0; s < le && src.charAt(s) == delim; s++)
      count++;
    if (count < 3)
      return false;
    return true;
  }

  private boolean isClosingCodeFence(int ls, char delim, int delimLen) {
    int s = ignore3LeadingSpaces(ls);
    if (s == EOF)
      return false;
    // count or more backtick or tilde chars
    int count;
    for (count = 0; s < EOF && src.charAt(s) == delim; s++)
      count++;
    if (count < delimLen)
      return false;
    // rest of line must be only spaces and tabs
    while (s < EOF && isSpaceOrTab(src.charAt(s))) s++;
    return s == EOF || src.charAt(s) == '\n';
  }

  private int parseFencedCode(int ls, int le) {
    if (le == EOF) {
      // special case: empty fenced code black at end of file
      ArrayList<Span> spans = new ArrayList<>();
      spans.add(Span_space(le-1, le));
      emit(new Block(BlockType.FENCED_CODE, spans, 0, false));
      return le; 
    }
    int s = ignore3LeadingSpaces(ls);
    int indentLen = s - ls;
    char delim = src.charAt(ls);
    int delimLen;
    for (delimLen = 0; s < le && src.charAt(s) == delim; s++)
      delimLen++;
    // rest of initial line is the "info text", ignore it
    ls = le + 1;
    boolean continuation = false;
    while (ls < EOF && !isClosingCodeFence(ls, delim, delimLen)) {
      le = lineEnd(ls);
      // line is [ls, le)
      // strip only a few leading spaces
      // (non-standard: ignore tabs, do not treat as 4 spaces)
      s = ignoreNLeadingSpaces(ls, indentLen);
      ArrayList<Span> spans = new ArrayList<>();
      if (s == le) // blank line, replace by SPACE
        spans.add(Span_space(le-1, le));
      else
        spans.add(Span_text(s, le));
      emit(new Block(BlockType.FENCED_CODE, spans, 0, continuation));
      continuation = true;
      ls = le + 1;
    }
    return lineEnd(ls);
  }

  private boolean isHeaderLine(int ls, int le) {
    int s = ignore3LeadingSpaces(ls);
    // 1-6 "#" chars
    int lvl;
    for (lvl = 0; lvl < 6 && s < le && src.charAt(s) == '#'; s++)
      lvl++;
    if (lvl < 1 || lvl > 6)
      return false;
    // followed by EOL, space, or tab
    if (s != le && !isSpaceOrTab(src.charAt(s)))
      return false;
    return true;
  }

  private void parseHeaderLine(int ls, int le) {
    int e = le;
    int s = ignore3LeadingSpaces(ls);
    // count "#" chars
    int lvl;
    for (lvl = 0; lvl < 6 && s < le && src.charAt(s) == '#'; s++)
      lvl++;
    // strip trailing spaces and tabs
    while (s < e && isSpaceOrTab(src.charAt(e-1))) e--;
    // strip optional trailing space-or-tab-then-### sequence
    int e2 = e;
    while (s < e2 && src.charAt(e2-1) == '#') e2--;
    if (s < e2 && isSpaceOrTab(src.charAt(e2-1))) {
      e = e2;
    }
    ArrayList<Span> spans = parseInlineSpans(s, e);
    applyInlineStyles(spans);
    emit(new Block(BlockType.HEADER, spans, lvl, false));
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
 
  private int parseParagraph(int paraStart) {
    // find paragraph end
    int paraEnd = lineEnd(paraStart);
    while (paraEnd + 1 < EOF) {
      int ls = paraEnd + 1;
      int le = lineEnd(ls); // next line is [ls, le), and le is EOL or EOF

      if (isBlankLine(ls, le) || isHeaderLine(ls, le) || isOpeningCodeFence(ls, le))
        break;
      paraEnd = le;
    }
    ArrayList<Span> spans = parseInlineSpans(paraStart, paraEnd);
    applyInlineStyles(spans);
    emitParagraphs(spans);
    return paraEnd + 1;
  }

  private void emitParagraphs(ArrayList<Span> spans) {
    // split by hardbreaks, emit each one as a paragraph
    ArrayList<Span> section = new ArrayList<>();
    boolean cont = false;
    for (Span span : spans) {
      if (span.type == SpanType.HARDBREAK) {
        // Emit current section (skips if empty)
        emitParagraph(section, cont);
        section = new ArrayList<>();
        cont = true;
      } else {
        section.add(span);
      }
    }
    // Emit last section (skips if empty)
    emitParagraph(section, cont);
  }

  private void emitParagraph(ArrayList<Span> spans, boolean continuation) {
    if (spans.isEmpty())
      return;
    emit(new Block(BlockType.PARAGRAPH, spans, 0, continuation));
  }

}

// TODO:
//  ~ links (web, eventually to built-in help pages)
//  - bullets
//  - tables (for properties)
//  - truthtables
