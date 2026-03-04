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

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

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
// (Coming soon)
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
// 1. List item
// 2. Another item
// Notes:
//  - Multi-level lists, and bulleted lists, should mostly work.
//  - Nested blocks within list items should work, at least in basic cases.
//
// (Coming soon)
// | table | with | headers   |
// | :---- | :--: | --------: |
// | and   | row  | alignment |
// Notes:
// - at most 3 spaces, then header row
// - inline formatting within cells is fine
// - if table width doesn't fit within text width, columns are allocated space proportionally to how
//   many dashes they have in the delimiter row
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

  private final String src;
  private final TextStyling styling;
  private final Font baseFont, monoFont;

  public final Block body;

  public Markdownish(String src, TextStyling sty) {
    this.src = src;
    this.styling = sty;

    baseFont = styling.font;
    monoFont = new Font("Monospaced", Font.PLAIN, baseFont.getSize()); // FIXME: allow styling

    body = new Block(BlockType.BODY, styling.margin);
    parseBlocks(0, src.length());
  }

  public static final AttributedCharacterIterator.Attribute SPAN_ID =
    new AttributedCharacterIterator.Attribute("logisim.markdown.span") {
      private static final long serialVersionUID = 1L;
    };
 
  enum BlockType  {
    HEADER,        // one phrase; headerLevel is defined
    PARAGRAPH,     // 1+ phrases are hardbreak-separated pieces of paragraph or table cell
    FENCED_CODE,   // 1+ phrases are lines of a code block
    TABLE,         // 1+ ROW blocks; first is header, rest are body
    ROW,           // 1+ PARAGRAPH blocks are the cells
    LIST,          // 1+ BODY blocks; bullet is '+', '-', or '*', indent is defined, or
                   // 1+ BODY blocks; bullet is '.' or ')', indent and startnum are defined
    BODY,          // 1+ blocks, e.g. paragraphs, headers, fenced_code, etc. 
  }

  private Block new_Header(int headerLevel) { return new Block(headerLevel); }
  private Block new_Paragraph(Font font, int cellAlign, int cellWidth) {
    return new Block(BlockType.PARAGRAPH, font, styling.paragraph_margin, cellAlign, cellWidth);
  }
  private Block new_FencedCode() {
    return new Block(BlockType.FENCED_CODE, monoFont, styling.getCodeMargin(), 0, 0);
  }
  private Block new_List(String bullet, int startnum) {
    return new Block(BlockType.LIST, bullet, startnum);
  }
  private Block new_ListItem(String bullet) {
    return new Block(BlockType.BODY, styling.getListItemMargin(bullet));
  }
  private Block new_Table() {
    return new Block(BlockType.TABLE, baseFont, styling.getTableMargin(), 0, 0);
  }
  private Block new_TableRow() {
    return new Block(BlockType.ROW, baseFont, styling.getTableCellPadding(), 0, 0);
  }
  public final class Block {
    final BlockType type;                // all blocks
    /*final*/ TextStyling.Size margin[]; // all blocks
    final Font font;                     // all blocks
    final ArrayList<Phrase> phrases;     // for non-lists; not empty
    final ArrayList<Block> blocks;       // for lists and body; not empty
    final int headerLevel;               // for headers
    final String bullet;                 // for lists
    final int startnum;                  // for numbered lists
    final int cellAlign;                 // for table cells
    final int cellWidth;                 // for table cells
  
    // HEADER
    private Block(int headerLevel) {
      this.type = BlockType.HEADER;
      this.phrases = new ArrayList<>();
      this.headerLevel = headerLevel;
      float px = styling.header_style[headerLevel].font_size.px(baseFont);
      float pt = px*3f/4f;
      this.font = baseFont.deriveFont(baseFont.getStyle() | Font.BOLD, pt);
      this.margin = styling.header_style[headerLevel].margin;

      this.blocks = null;
      this.bullet = null;
      this.startnum = 0;
      this.cellAlign = this.cellWidth = 0;
    }

    // PARAGRAPH, FENCED_CODE, TABLE, ROW
    private Block(BlockType type, Font font, TextStyling.Size margin[], int cellAlign, int cellWidth) {
      this.type = type;
      if (type == BlockType.TABLE || type == BlockType.ROW) {
        this.phrases = null;
        this.blocks = new ArrayList<>();
      } else {
        this.phrases = new ArrayList<>();
        this.blocks = null;
      }
      this.font = font;
      this.margin = margin;
      this.cellAlign = cellAlign;
      this.cellWidth = cellWidth;

      this.bullet = null;
      this.startnum = 0;
      this.headerLevel = 0;
    }

    // BODY
    private Block(BlockType type, TextStyling.Size margin[]) {
      this.type = type;
      this.blocks = new ArrayList<>();
      this.font = baseFont;
      this.margin = margin;

      this.phrases = null;
      this.bullet = null;
      this.startnum = 0;
      this.headerLevel = 0;
      this.cellAlign = this.cellWidth = 0;
    }

    // LIST
    private Block(BlockType type, String bullet, int startnum) {
      this.type = type;
      this.blocks = new ArrayList<>();
      this.bullet = bullet;
      this.startnum = startnum;
      this.font = baseFont;
      this.margin = styling.getListMargin(bullet);

      this.phrases = null;
      this.headerLevel = 0;
      this.cellAlign = this.cellWidth = 0;
    }

    private Block addPhrase(ArrayList<Span> spans) {
      phrases.add(new Phrase(font, spans));
      return this;
    }

    private Block addSubBlock(Block child) {
      blocks.add(child);
      return this;
    }

    public boolean isLeaf() {
      return phrases != null;
    }

    public boolean wrapped() {
      return type != BlockType.FENCED_CODE;
    }

    public TextStyling.Accent getAccent() {
      if (type == BlockType.HEADER)
        return styling.header_style[headerLevel].accent;
      else
        return null;
    }
 
  }

  public final class Phrase {
    final Font font;
    final ArrayList<Span> spans; // not empty; otherwise, we can't map block to src text index

    private Phrase(Font font, ArrayList<Span> spans) {
      this.font = font;
      this.spans = spans;
    }

    public AttributedString buildAttributedString() {
      StringBuilder sb = new StringBuilder();
      ArrayList<AttrRun> runs = new ArrayList<>();

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

  }

  private class OpenBlock {
    final OpenBlock prev;
    final Block block;
    final int WplusN; // cumulative for all open blocks
    OpenBlock(OpenBlock p, Block b, int wn) {
      prev = p; block = b; WplusN = wn;
    }
  }

  private OpenBlock openBlock = null;

  private void pushOpen(Block b, int wn) {
    openBlock = new OpenBlock(openBlock, b, openBlock != null ? openBlock.WplusN + wn : wn);
  }

  private void popClose() {
    openBlock = openBlock.prev;
  }

  private void emit(Block nextBlock) {
    if (openBlock != null)
      openBlock.block.addSubBlock(nextBlock);
    else
      body.addSubBlock(nextBlock);
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
  
  private void parseBlocks(int start, int end) {
    int ls = start;
    while (ls < end) {
      ls = skipWplusN(ls, end);
      int le = lineEnd(ls, end); // line is [ls, le), and le is EOL or EOF

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
        ls = parseFencedCode(ls, le, end);
      }

      // parse list
      else if (isListLine(ls, le, false)) {
        ls = parseList(ls, end);
      }

      // parse truthtable
      else if (isOpeningTable(ls, le, end)) {
        ls = parseTable(ls, le, end);
      }

      // anything else must be a paragraph
      else {
        ls = parseParagraph(ls, end);
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
        } else if (ch == '<' && pos + 3 < end && src.substring(pos, pos+4).equalsIgnoreCase("<br>")) {
          markAsOther();
          addSpaceIfEmptySection(); // ensure no hardbreak at start, no consecutive hardbreak
          spans.add(Span_hardbreak(pos, pos+3));
          pos += 3;
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
          pos = skipWplusN(pos+1, end) - 1;
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
        } else if (ch == ' ' || ch == '\t') {
          // (3) sequence of spaces and tabs --> SPACE
          markAsSpace();
        } else if (ch == '\n') {
          // (8) newline --> SPACE
          markAsSpace();
          pos = skipWplusN(pos+1, end) - 1;
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
  private int lineEnd(int s, int eof) {
    while (s < eof && src.charAt(s) != '\n')
      s++;
    return s; // exclusive, points at '\n', or EOF
  }

  // whitespace within a line: only plain spaces and tabs
  private boolean isSpaceOrTab(char ch) {
    return ch == ' ' || ch == '\t';
  }

  // plain ascii digits
  private boolean isAsciiDigit(char ch) {
    return '0' <= ch && ch <= '9';
  }
  
  private int countLineIndent(int ls, int le) {
    int n = 0;
    for (int i = ls; i < le; i++) {
      char ch = src.charAt(i);
      if (ch == ' ') n++;
      else if (ch == '\t') n += 4;
      else break;
    }
    return n;
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

  private boolean containsBlankLine(int pos, int end) {
    while (pos < end) {
      int ls = pos;
      int le = lineEnd(ls, end);
      if (isBlankLine(ls, le))
        return true;
      pos = le + 1;
    }
    return false;
  }

  private int ignore3LeadingSpaces(int ls, int eof) {
    int s = ls;
    if (s < eof && src.charAt(s) == ' ') s++;
    if (s < eof && src.charAt(s) == ' ') s++;
    if (s < eof && src.charAt(s) == ' ') s++;
    return s;
  }

  // Note: handling of tabs here is broken when there are nested lists with a
  // mixture of tabs and spaces... an outer indent can strip a tab when it
  // should have stripped fewer columns, leaving the inner indent with fewer
  // spaces than it should. Similarly, when calculating W for a new list item,
  // an outer tab may have been stripped when it should have stripped fewer
  // columns, so W will undercount.
  private int skipWplusN(int ls, int eof) {
    int s = ls;
    int n = openBlock == null ? 0 : openBlock.WplusN;
    while (n > 0 && s < eof) {
      if (src.charAt(s) == ' ') { s++; n--; }
      else if (src.charAt(s) == '\t') { s++; n-=4; }
      else return ls;
    }
    return s;
  }

  private int ignoreNLeadingSpaces(int ls, int eof, int n) {
    int s = ls;
    while (n-- > 0 && s < eof && src.charAt(s) == ' ') s++;
    return s;
  }

  private boolean isOpeningCodeFence(int ls, int le) {
    int s = ignore3LeadingSpaces(ls, le);
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

  private boolean isClosingCodeFence(int ls, int eof, char delim, int delimLen) {
    int s = ignore3LeadingSpaces(ls, eof);
    if (s == eof)
      return false;
    // count or more backtick or tilde chars
    int count;
    for (count = 0; s < eof && src.charAt(s) == delim; s++)
      count++;
    if (count < delimLen)
      return false;
    // rest of line must be only spaces and tabs
    while (s < eof && isSpaceOrTab(src.charAt(s))) s++;
    return s == eof || src.charAt(s) == '\n';
  }

  private int parseFencedCode(int ls, int le, int eof) {
    if (le == eof) {
      // special case: empty fenced code black at end of file
      ArrayList<Span> spans = new ArrayList<>();
      spans.add(Span_space(le-1, le));
      emit(new_FencedCode().addPhrase(spans));
      return le; 
    }
    int s = ignore3LeadingSpaces(ls, eof);
    int indentLen = s - ls;
    char delim = src.charAt(ls);
    int delimLen;
    for (delimLen = 0; s < le && src.charAt(s) == delim; s++)
      delimLen++;
    // rest of initial line is the "info text", ignore it
    ls = le + 1;
    ls = skipWplusN(ls, eof);
    Block block = new_FencedCode();
    while (ls < eof && !isClosingCodeFence(ls, eof, delim, delimLen)) {
      le = lineEnd(ls, eof);
      // line is [ls, le)
      // strip only a few leading spaces
      // (non-standard: ignore tabs, do not treat as 4 spaces)
      s = ignoreNLeadingSpaces(ls, eof, indentLen);
      ArrayList<Span> spans = new ArrayList<>();
      if (s == le) // blank line, replace by SPACE
        spans.add(Span_space(le-1, le));
      else
        spans.add(Span_text(s, le));
      block.addPhrase(spans);
      ls = le + 1;
      ls = skipWplusN(ls, eof);
    }
    emit(block);
    return lineEnd(ls, eof); // swallow closing fence
  }

  private boolean isHeaderLine(int ls, int le) {
    int s = ignore3LeadingSpaces(ls, le);
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
    int s = ignore3LeadingSpaces(ls, le);
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
    emit(new_Header(lvl).addPhrase(spans));
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
 
  private int parseParagraph(int paraStart, int eof) {
    // find paragraph end
    int paraEnd = lineEnd(paraStart, eof);
    while (paraEnd + 1 < eof) {
      int ls = paraEnd + 1;
      ls = skipWplusN(ls, eof);
      int le = lineEnd(ls, eof); // next line is [ls, le), and le is EOL or eof

      if (isBlankLine(ls, le) || isHeaderLine(ls, le) || isOpeningCodeFence(ls, le) || isListLine(ls, le, true) || isOpeningTable(ls, le, eof))
        break;
      paraEnd = le;
    }
    ArrayList<Span> spans = parseInlineSpans(paraStart, paraEnd);
    applyInlineStyles(spans);
    emit(buildParagraph(baseFont, spans, 0, 0));
    return paraEnd + 1;
  }

  private Block buildParagraph(Font font, ArrayList<Span> spans, int cellAlign, int cellWidth) {
    // split by hardbreaks, make a Phrase for each, package into a PARAGRAPH
    ArrayList<Span> section = new ArrayList<>();
    Block block = new_Paragraph(font, cellAlign, cellWidth);
    for (Span span : spans) {
      if (span.type == SpanType.HARDBREAK) {
        // Emit current section (skips if empty)
        if (!section.isEmpty())
            block.addPhrase(section);
        section = new ArrayList<>();
      } else {
        section.add(span);
      }
    }
    // Emit last section (skips if empty)
    if (!section.isEmpty())
        block.addPhrase(section);
    return block;
  }

  private boolean isBulletListMarker(char ch) {
    return (ch == '-' || ch == '*' || ch == '+');
  }

  private class BulletInfo {
    boolean bulleted;
    final String bullet;
    final int number;
    final int W, N;
    final int end; // index in string after the W+N prefix
    BulletInfo(char b, int num, int w, int pos, int le) {
      bulleted = isBulletListMarker(b);
      bullet = ""+b;
      number = num;
      W = w;
      int n = 0;
      while (pos < le) {
        char ch = src.charAt(pos);
        if (ch == ' ') { n++; pos++; }
        else if (ch == '\t') { n += 4; pos++; }
        else break;
      }
      N = n;
      end = pos;
    }
  }

  private BulletInfo getBulletInfo(int ls, int le, boolean interruptingPara) {
    int s = ignore3LeadingSpaces(ls, le);
    // "* A..." etc.
    if (s+2 < le && isBulletListMarker(src.charAt(s)) && isSpaceOrTab(src.charAt(s+1))) {
      int W = s+1 - ls;
      return new BulletInfo(src.charAt(s), 0, W, s+1, le);
    }
    // "1) A..." etc.
    if (s+3 < le && src.charAt(s) == '1' && (src.charAt(s+1) == ')' || src.charAt(s+1) == '.') && isSpaceOrTab(src.charAt(s+2))) {
      int W = s+2 - ls;
      return new BulletInfo(src.charAt(s+1), 1, W, s+2, le);
    }
    if (interruptingPara || !(s+3 < le))
      return null;
    int n, num = 0;
    for (n = 0; n < 9 && s < le && isAsciiDigit(src.charAt(s)); s++) {
      n++;
      num = 10*num + (src.charAt(s) - '0');
    }
    if ((1 <= n && n <= 9) && s+2 < le && (src.charAt(s) == ')' || src.charAt(s) == '.') && isSpaceOrTab(src.charAt(s+1))) {
      int W = s+1 - ls;
      return new BulletInfo(src.charAt(s), num, W, s+1, le);
    }
    return null;
  }

  private boolean isListLine(int ls, int le, boolean interruptingPara) {
    return getBulletInfo(ls, le, interruptingPara) != null;
  }

  // Rule 1a:
  //   If we aren't within a potential paragraph-continuation setting, or
  //   already within a list, and we encounter a line that looks like the start
  //   of a list item, that's the start of a list.
  // Rule 1b:
  //   If we are within a potential paragraph-continuation setting, 1a also
  //   applies, except that numbered items other than "1." or "1)" don't count.
  // Rule 2:
  //   Once within a list item, we keep taking lines so long as:
  //   (a) the line is blank
  //   (b) the line is indented sufficiently (here we strip W+N leading spaces/tabs)
  //   (c) the line is non-blank and insufficiently indented, but doesn't follow
  //       a blank line and doesn't look like something else (a header, start of
  //       a list item, fenced code block, etc.)
  // Rule 3a:
  //   If the line following a list item looks like the start of a list item,
  //   and it has the same bullet type, then it is the next item in this same
  //   list.
  // Rule 3a:
  //   But if it has a different bullet type, it is the first item in a new
  //   list.

  private int parseList(int listStart, int eof) {

    int itemStart = listStart;
    int le = lineEnd(itemStart, eof);
    
    // determine bullet and prefix width, advance past them
    BulletInfo bi = getBulletInfo(itemStart, le, false);
    String bullet = bi.bullet;

    Block list = new_List(bullet, bi.number);
    boolean loose = false;

    int itemEnd = listItemEnd(bi.end+1, eof, bi.W+bi.N);
    {
      Block item = new_ListItem(bullet);
      pushOpen(item, bi.W + bi.N);
      parseBlocks(itemStart + bi.W, itemEnd);
      popClose();
      list.addSubBlock(item);
      loose |= item.blocks.size() != 1 || containsBlankLine(itemStart, itemEnd);
    }

    // consume additional items
    while (itemEnd + 1 < eof) {
      int ls = itemEnd + 1;
      ls = skipWplusN(ls, eof);
      le = lineEnd(ls, eof); // next line is [ls, le), and le is EOL or eof
      if (!isListLine(ls, le, false))
        break;
      itemStart = ls;
      bi = getBulletInfo(itemStart, le, false);
      if (!bi.bullet.equals(bullet))
        break;
      itemEnd = listItemEnd(bi.end+1, eof, bi.W+bi.N);
      Block item = new_ListItem(bullet);
      pushOpen(item, bi.W + bi.N);
      parseBlocks(itemStart + bi.W, itemEnd);
      popClose();
      list.addSubBlock(item);
      loose |= item.blocks.size() != 1 || containsBlankLine(itemStart, itemEnd);
    }

    if (!loose) {
      // remove margins from list item blocks
      for (Block item : list.blocks) {
        // item is a BODY, and should be exactly one block within it
        Block itemContents = item.blocks.get(0);
        TextStyling.Size[] oldMargin = itemContents.margin;
        itemContents.margin = new TextStyling.Size[] {
          TextStyling.ZERO_PX, oldMargin[1],
          TextStyling.ZERO_PX, oldMargin[3]
        };
      }
    }

    emit(list);

    return itemEnd + 1;
  }

  int listItemEnd(int pos, int eof, int WplusN) {
    int itemEnd = pos;
    boolean prevWasBlank = false;
    while (itemEnd + 1 < eof) {
      int ls = itemEnd + 1;
      ls = skipWplusN(ls, eof);
      int le = lineEnd(ls, eof); // next line is [ls, le), and le is EOL or eof
      boolean thisIsBlank = isBlankLine(ls, le); 
      if (!thisIsBlank
          && countLineIndent(ls, le) < WplusN
          && (prevWasBlank || isHeaderLine(ls, le) || isOpeningCodeFence(ls, le) || isListLine(ls, le, false) || isOpeningTable(ls, le, eof)))
        break;
      itemEnd = le;
      prevWasBlank = thisIsBlank;
    }
    return itemEnd;
  }

  // if strict, there must be pipes...
  //   " |foo|" or even " | foo " is a cell,
  //   but " foo " alone isn't a cell
  // if not strict, then any non-blank not-too-indented line has cells
  //   unless it has just a single pipe
  private ArrayList<String> splitTableCells(int ls, int le, boolean strict, Block cells[], int cellAlign[], int cellWidth[], Font font) {
    int s = skipWplusN(ls, le);
    s = ignore3LeadingSpaces(s, le);
    if (s == le || (strict && isSpaceOrTab(src.charAt(s))))
      return null; // blank, or too indented
    // strip leading pipe
    boolean leadingPipe = (src.charAt(s) == '|');
    if (leadingPipe) s++;
    if (s == le) return null;
    // strip trailing whitespace and pipe
    int e = le;
    while (s < e && isSpaceOrTab(src.charAt(e-1))) e--;
    boolean trailingPipe = (s < e && src.charAt(e-1) == '|');
    if (trailingPipe) e--;
    int numCells;
    ArrayList<String> ret = new ArrayList<>();
    if (s == e && leadingPipe && trailingPipe) {
      // "   ||   "      -- one empty header cell
      ret.add("");
    } else if (s == e) {
      // "   |    "      -- a lone pipe, or entirely blank, no cells
      // note: GFM treats a lone pipe as no cells even for non-strict (body) rows
      return null;
    } else {
      // count remaining un-escaped pipes
      int cellStart = s;
      for (int pos = s; pos < e; pos++) {
        char ch = src.charAt(pos);
        if (ch == '\\' && pos + 1 < e && isAsciiPunct(src.charAt(pos+1))) {
          pos++;
        } else if (ch == '|') {
          int i = ret.size();
          ret.add(src.substring(cellStart, pos).trim());
          if (cells != null && i < cells.length) {
            ArrayList<Span> spans = parseInlineSpans(cellStart, pos);
            applyInlineStyles(spans);
            cells[i] = buildParagraph(font, spans, cellAlign[i], cellWidth[i]);
          }
          cellStart = pos+1;
        }
      }
      if (ret.size() == 0 && strict && !leadingPipe && !trailingPipe)
        return null;
      int i = ret.size();
      ret.add(src.substring(cellStart, e).trim());
      if (cells != null && i < cells.length) {
        ArrayList<Span> spans = parseInlineSpans(cellStart, e);
        applyInlineStyles(spans);
        cells[i] = buildParagraph(font, spans, cellAlign[i], cellWidth[i]);
      }
    }
    if (cells != null) {
      for (int i = 0; i < cells.length; i++) {
        if (cells[i] == null) {
          ArrayList<Span> spans = new ArrayList<>();
          spans.add(Span_space(e-1, e));
          cells[i] = buildParagraph(font, spans, cellAlign[i], cellWidth[i]);
        }
      }
    }
    return ret;
  }

  private boolean isOpeningTable(int ls, int le, int end) {
    ArrayList<String> hdr = splitTableCells(ls, le, true, null, null, null, null);
    if (hdr == null)
      return false;

    ls = le + 1;
    le = lineEnd(ls, end);
    if (ls == le) return false;

    ArrayList<String> dlm = splitTableCells(ls, le, true, null, null, null, null); // in GFM, this is non-strict (sort of)
    if (dlm == null || dlm.size() != hdr.size())
      return false;

    for (String d: dlm) {
      // if d contains anything other than chars from "-:~", then it's not a delim row
      for (int i = 0; i < d.length(); i++) {
        char ch = d.charAt(i);
        if (ch != '-' && ch != ':' && ch != '~')
          return false;
      }
    }
    return true;
  }

  private int parseTable(int ls, int le, int end) {
    int tableStart = ls;
    int headerEnd = le;

    Font hdrFont = baseFont.deriveFont(baseFont.getStyle() | Font.BOLD);

    // get delimiter row
    ls = le + 1;
    le = lineEnd(ls, end);
    ArrayList<String> dlm = splitTableCells(ls, le, true, null, null, null, null);
    int cols = dlm.size();

    // get cell alignments and widths
    int[] alignments = new int[cols];
    int[] widths = new int[cols];
    for (int i = 0; i < cols; i++) {
      String d = dlm.get(i);
      if (d.startsWith(":") && d.endsWith(":")) {
        alignments[i] = ALIGN.H_CENTER;
      } else if (d.startsWith(":")) {
        alignments[i] = ALIGN.H_LEFT;
      } else if (d.endsWith(":")) {
        alignments[i] = ALIGN.H_RIGHT;
      } else {
        alignments[i] = ALIGN.H_CENTER;
      }
      widths[i] = 0;
      for (int j = 0; j < d.length(); j++) {
        char ch = d.charAt(j);
        if (ch == '-' || ch == '~') widths[i]++;
      }
    }

    Block hdrcells[] = new Block[cols];
    splitTableCells(tableStart, headerEnd, true, hdrcells, alignments, widths, hdrFont);
    
    Block table = new_Table();
    Block headerRow = new_TableRow();
    for (Block cell : hdrcells)
      headerRow.addSubBlock(cell);
    table.addSubBlock(headerRow);

    ArrayList<ArrayList<String>> rows = new ArrayList<>();
    int tableEnd = le;
    while (tableEnd + 1 < end) {
      ls = tableEnd + 1;
      le = lineEnd(ls, end);
      Block rowcells[] = new Block[cols];
      ArrayList<String> row = splitTableCells(ls, le, false, rowcells, alignments, widths, baseFont);
      if (row == null)
        break;
      Block bodyRow = new_TableRow();
      for (Block cell : rowcells)
        bodyRow.addSubBlock(cell);
      table.addSubBlock(bodyRow);
      tableEnd = le;
    }

    emit(table);

    return tableEnd;
  }

}
