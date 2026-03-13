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

package com.cburch.logisim.std.decor;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;

// Parses a CSS-like string into styles for Text.
// Example:
//   margin: 3px;                // set top/right/bottom/left margins
//   margin: 1em 2.5pt;          // set top/bottom and left/right margins
//   margin: 3 0 5;              // set top, right/left, and bottom margins, default is px
//   margin: 3 0 2 5;            // set top, right, bottom, and left margins
//   p-margin: 0.75em;           // paragraph margin
//   h1 {
//     color: #ff0012;           // set header1 foreground color
//     background-color: #00c;   // set header1 background color
//     accent: underline 3 #0f0; // set 3 pixel underline accent in green
//   }
//   h2 {
//     accent: block 8 #000;     // set 8 pixel block accent in black
//   }
public class TextStyling {

  private static final int DEFAULT_BODY_MARGIN = 4; // only applied if requested (e.g. for callout,
                                                    // or if there is a background)

  public final String str; // original user-specified string
  public final AttributeOption format;
  public final HashMap<String, String> map;

  public final Font font;
  public final Color color;
  public final Color background_color;
  public final Size margin[];

  public final Size paragraph_margin[];
  
  public final HeaderStyle header_style[];

  public final LinkStyle link_style;


  public enum SizeUnit { PIXELS, POINTS, EMS }

  public static final class Size {
    public final float v;
    public final SizeUnit u;
    public Size(float vv, SizeUnit uu) {
      v = vv;
      u = uu;
    }
    public float px(Font font) {
      return px(font.getSize2D() * 4f/3f);
    }
    public float px(float fontsize) {
      if (u == SizeUnit.PIXELS) return v;
      else if (u == SizeUnit.POINTS) return v*4f/3f;
      else return v * fontsize;
    }
    public int ipx(Font font) {
      return (int)Math.round(px(font));
    }
    public int ipx(float fontsize) {
      return (int)Math.round(px(fontsize));
    }
  }

  public static final Size ZERO_PX = new Size(0, SizeUnit.PIXELS);
  public static final Size ZERO_MARGIN[] = new Size[] { ZERO_PX, ZERO_PX, ZERO_PX, ZERO_PX };

  public enum AccentType { UNDERLINE, LEADERBLOCK }
  public static final class Accent {
    public final AccentType type;
    public final Color color;
    public final Size size;
    public Accent(AccentType t, Color c, Size sz) {
      type = t;
      color = c == null ? Color.MAGENTA : c;
      if (sz != null)
        size = sz;
      else if (type == AccentType.UNDERLINE)
        size = new Size(0.25f, SizeUnit.EMS);
      else // LEADERBLOCK
        size = new Size(0.75f, SizeUnit.EMS);
    }
  }

  public static final class LinkStyle {
    public final Color color;
    public final Color background_color;
    public final boolean bold, underline, italic;
    public LinkStyle(Color fg, Color bg, boolean b, boolean u, boolean i) {
      color = fg;
      background_color = bg;
      bold = b;
      underline = u;
      italic = i;
    }
  }

  public static final class HeaderStyle {
    public final Size margin[];
    public /*final*/ Size font_size;
    public final Color color;
    public final Color background_color;
    public final Accent accent;

    public HeaderStyle(Size m[], Size fs, Color fg, Color bg, Accent a) {
      margin = m;
      font_size = fs;
      color = fg;
      background_color = bg;
      accent = a;
    }
  }

  public TextStyling(String styles, Font bodyFont, Color fgColor, Color bgColor, AttributeOption fmt, boolean defaultHasMargin) {
    str = styles;
    font = bodyFont;
    color = fgColor;
    background_color = bgColor;
    format = fmt;

    map = parseStyleString(styles);

    header_style = new HeaderStyle[6];
    for (int i = 0; i < 6; i++) {

      Color fg = null, bg = null, ac = null;
      AccentType at = null;
      Size as = null;

      try { fg = Attributes.parseColor(map.get("h"+i+"-color")); }
      catch (Exception e) { }

      try { bg = Attributes.parseColor(map.get("h"+i+"-background-color")); }
      catch (Exception e) { }

      String accent = map.getOrDefault("h"+i+"-accent", "");
      String parts[] = accent.split("\\s+");
      for (int j = 0; j < parts.length; j++) {
        String part = parts[j];
        if (part.equalsIgnoreCase("underline")) {
          at = AccentType.UNDERLINE;
        } else if (part.equalsIgnoreCase("block")) {
          at = AccentType.LEADERBLOCK;
        } else if (part.equalsIgnoreCase("none")) {
          at = null;
        } else if (part.startsWith("#")) {
          try { ac = Attributes.parseColor(part); }
          catch (Exception e) { }
        } else {
          if (!part.toLowerCase().endsWith("em") &&
              !part.toLowerCase().endsWith("pt") &&
              !part.toLowerCase().endsWith("px") &&
              j + 1 < parts.length && 
              (parts[j+1].equalsIgnoreCase("em") ||
               parts[j+1].equalsIgnoreCase("pt") ||
               parts[j+1].equalsIgnoreCase("px"))) {
            part += " " + parts[j+1];
            j++;
          }
          try { as = parseSize(part); }
          catch (NumberFormatException e) { }
        }
      }
      Accent a = (at == null) ? null : new Accent(at, ac, as);

      Size[] m = getAndParseMargin("h"+i+"-margin");
      Size fs = parseSize(map.get("h"+i+"-font-size"));

      header_style[i] = new HeaderStyle(m, fs, fg, bg, a);
    }
    
    paragraph_margin = getAndParseMargin("p-margin");

    margin = getAndParseMargin("margin");
    
    if (format == Text.TEXT_FORMAT_MARKDOWNISH) {
      // Set default paragraph margin
      float defParaMargin[] = { 0.5f, 0f, 0.5f, 0f };
      for (int p = 0; p < 4; p++)
        if (paragraph_margin[p] == null)
          paragraph_margin[p] = new Size(defParaMargin[p], SizeUnit.EMS);
      // Set default header margins
      float defHdrMargin[] = { 0.5f, 0f, 0.5f, 0f };
      for (int i = 0; i < 6; i++)
        for (int p = 0; p < 4; p++)
          if (header_style[i].margin[p] == null)
            header_style[i].margin[p] = new Size(defHdrMargin[p], SizeUnit.EMS);
      // Set default header font sizes
      float defHdrSz[] = { 1.6f, 1.4f, 1.2f, 1.1f, 1.1f, 1.1f };
      for (int i = 0; i < 6; i++)
        if (header_style[i].font_size == null)
          header_style[i].font_size = new Size(defHdrSz[i], SizeUnit.EMS);
    } else if (format == Text.TEXT_FORMAT_WRAPPED) {
      // Set default paragraph margin
      float defParaMargin[] = { 0.6f, 0f, 0.6f, 0f };
      for (int p = 0; p < 4; p++)
        if (paragraph_margin[p] == null)
          paragraph_margin[p] = new Size(defParaMargin[p], SizeUnit.EMS);
    } else { // TEXT_FORMAT_PLAIN
      // Set default paragraph margin
      for (int p = 0; p < 4; p++)
        if (paragraph_margin[p] == null)
          paragraph_margin[p] = ZERO_PX;
    }

    // Set default body margin: 4px if requested, 0px otherwiise
    float defBodyMargin = defaultHasMargin ? DEFAULT_BODY_MARGIN : 0;
    for (int i = 0; i < 4; i++)
      if (margin[i] == null)
        margin[i] = new Size(defBodyMargin, SizeUnit.PIXELS);

    Color a_fg, a_bg;
    try { a_fg = Attributes.parseColor(map.get("a-color")); }
    catch (Exception e) { a_fg = Color.BLUE; }
    try { a_bg = Attributes.parseColor(map.get("a-background-color")); }
    catch (Exception e) { a_bg = new Color(255, 255, 255, 0); } // transparent
    boolean a_bold, a_line, a_ital;
    a_bold = map.getOrDefault("a-font-weight", "normal").trim().equalsIgnoreCase("bold");
    a_line = map.getOrDefault("a-text-decoration", "none").trim().equalsIgnoreCase("underline");
    a_ital = map.getOrDefault("a-font-style", "normal").trim().equalsIgnoreCase("italic");
    link_style = new LinkStyle(a_fg, a_bg, a_bold, a_line, a_ital);
  }

  // Used when editing markdownish-rendered Text, this replaces the font
  // with an editing-specific font, and eliminates paragraph margins.
  public TextStyling withReplacedFontAndSpacing(Font altFont) {
    TextStyling repl = new TextStyling(str, altFont, color, background_color, format, false);
    for (int p = 0; p < 4; p++)
      repl.paragraph_margin[p] = ZERO_PX;
    return repl;
  }

  // Tries, for example, in order:
  //   ul[bullet='*']-margin
  //   ul-margin
  //   {0, left, 0, right} where left/right are taken from paragraph margin
  public Size[] getListMargin(String bullet) {
    String type = (bullet.equals(".") || bullet.equals(")")) ? "ol" : "ul";
    Size[] m = getAndParseMargin(type+"['"+bullet+"']-margin");
    if (m[0] == null)
      m = getAndParseMargin(type+"-margin");
    if (m[0] == null)
      m = new Size[] {
        ZERO_PX, paragraph_margin[1],
        ZERO_PX, paragraph_margin[3]
      };
    return m;
  }

  // Tries, for example, in order:
  //   ul[bullet='*']-li-margin
  //   ul-li-margin
  //   li-margin
  //   {0, 0, 0, 0}
  public Size[] getListItemMargin(String bullet) {
    String type = (bullet.equals(".") || bullet.equals(")")) ? "ol" : "ul";
    Size[] m = getAndParseMargin(type+"['"+bullet+"']-li-margin");
    if (m[0] == null)
      m = getAndParseMargin(type+"-li-margin");
    if (m[0] == null)
      m = getAndParseMargin("li-margin");
    if (m[0] == null)
      m = ZERO_MARGIN;
    return m;
  }

  // Tries, in order:
  //   code-margin
  //   paragraph margin
  public Size[] getCodeMargin() {
    Size[] m = getAndParseMargin("code-margin");
    if (m[0] == null)
      m = paragraph_margin;
    return m;
  }

  // For space around outside of table, tries, in order:
  //   table-margin
  //   paragraph margin
  public Size[] getTableMargin() {
    Size[] m = getAndParseMargin("table-margin");
    if (m[0] == null)
      m = paragraph_margin;
    return m;
  }

  // For padding between cell border and cell contents, tries, in order:
  //   table-tr-td-padding
  //   table-td-padding
  //   td-padding
  //   0.15em 0.3em;
  public Size[] getTableCellPadding() {
    Size[] m = getAndParseMargin("table-tr-td-padding");
    if (m[0] == null)
      m = getAndParseMargin("table-td-padding");
    if (m[0] == null)
      m = getAndParseMargin("td-padding");
    if (m[0] == null)
      m = parseMargin("0.15em 0.3em");
    return m;
  }

  /**
   * Parse a simplified CSS-like style string into flattened key/value pairs.
   *
   * Grammar:
   *  - assignment: key ':' value ';'
   *  - block: prefix '{' (assignment | block)* '}'
   *
   * Notes:
   *  - Extra semicolons are ignored.
   *  - Missing final semicolon or closing braces is fine.
   *  - Values are raw strings (trimmed) and may include spaces, but not '{', '}', ';'.
   */
  private static HashMap<String, String> parseStyleString(String input) {
    HashMap<String, String> out = new HashMap<>();
    if (input == null || input.isBlank()) return out;

    Parser p = new Parser(input);
    p.parseTopLevel(out);
    return out;
  }

  // ---------------- internals ----------------

  private static final class Parser {
    private final String s;
    private final int n;
    private int i = 0;

    // Stack of prefixes, each already normalized (trimmed).
    private final Deque<String> prefixStack = new ArrayDeque<>();

    Parser(String s) {
      this.s = s;
      this.n = s.length();
    }

    void parseTopLevel(HashMap<String, String> out) {
      parseStatementsUntil(out, /*stopOnCloseBrace=*/false);
      // If there are unmatched '{', we just leave them; you can choose to error instead.
    }

    private void parseStatementsUntil(HashMap<String, String> out, boolean stopOnCloseBrace) {
      while (true) {
        skipWhitespaceAndSemicolons();

        if (eof()) return;

        char c = peek();
        if (c == '}') {
          if (stopOnCloseBrace) {
            i++; // consume '}'
            return;
          } else {
            // stray close-brace at top-level: ignore it
            i++;
            continue;
          }
        }

        // Read "head" token up to one of ':', '{', '}', ';'
        String head = readUntilSpecial().trim();
        if (head.isEmpty()) {
          // Could be a special char; consume something and continue to avoid infinite loop.
          if (!eof()) i++;
          continue;
        }

        skipWhitespace();

        if (eof()) {
          // "head" at end with no ':' or '{' => nothing to do
          return;
        }

        char next = peek();

        if (next == '{') {
          // Block: head { ... }
          i++; // consume '{'
          prefixStack.addLast(head);
          parseStatementsUntil(out, /*stopOnCloseBrace=*/true);
          // Pop prefix even if '}' was missing (parseStatementsUntil hits EOF)
          if (!prefixStack.isEmpty()) prefixStack.removeLast();

          // Optional trailing semicolons / whitespace after block
          skipWhitespaceAndSemicolons();
          continue;
        }

        if (next == ':') {
          // Assignment: head : value
          i++; // consume ':'
          String value = readValueToken().trim();
          if (!value.isEmpty()) {
            String fullKey = qualifyKey(head);
            out.put(fullKey, value);
          }
          // Optional semicolon (or end-of-input or before '}')
          if (!eof() && peek() == ';') i++;
          continue;
        }

        // Any other char after a head token means malformed under our grammar.
        // To be forgiving, skip forward to next ';' or '}' and continue.
        skipToStatementBoundary();
      }
    }

    private String qualifyKey(String key) {
      if (prefixStack.isEmpty()) return key;
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (String p : prefixStack) {
        if (p == null || p.isBlank()) continue;
        if (!first) sb.append('-');
        sb.append(p.trim());
        first = false;
      }
      if (!first) sb.append('-');
      sb.append(key.trim());
      return sb.toString();
    }

    // Reads until one of ':', '{', '}', ';' OR EOF (does not consume the special char).
    private String readUntilSpecial() {
      int start = i;
      while (!eof()) {
        char c = peek();
        if (c == ':' || c == '{' || c == '}' || c == ';') break;
        i++;
      }
      return s.substring(start, i);
    }

    // Value runs until ';' or '}' or EOF (does not consume terminator).
    private String readValueToken() {
      skipWhitespace();
      int start = i;
      while (!eof()) {
        char c = peek();
        if (c == ';' || c == '}') break;
        // We disallow '{' in values in this PoC; treat it as boundary too.
        if (c == '{') break;
        i++;
      }
      return s.substring(start, i);
    }

    private void skipWhitespace() {
      while (!eof() && Character.isWhitespace(peek())) i++;
    }

    private void skipWhitespaceAndSemicolons() {
      while (!eof()) {
        char c = peek();
        if (Character.isWhitespace(c) || c == ';') {
          i++;
        } else {
          break;
        }
      }
    }

    private void skipToStatementBoundary() {
      while (!eof()) {
        char c = peek();
        if (c == ';' || c == '}') return;
        i++;
      }
    }

    private boolean eof() { return i >= n; }
    private char peek() { return s.charAt(i); }
  }

  private Size[] getAndParseMargin(String key) {
    return parseMargin(map.get(key));
  }

  private Size[] parseMargin(String value) {
    Size[] result = new Size[4];
    if (value == null || value.isBlank()) {
      return result;
    }
    value = value.replaceAll("(?i)\\s*pt", "pt");
    value = value.replaceAll("(?i)\\s*em", "em");
    value = value.replaceAll("(?i)\\s*px", "px");

    String[] parts = value.split("\\s+");
    int n = Math.min(4, parts.length);
    if (n < 1)
      return result;

    Size[] parsed = new Size[n];
    for (int i = 0; i < n; i++) {
      parsed[i] = parseSize(parts[i].trim());
    }

    switch (n) {
      case 1:
        result[0] = result[1] = result[2] = result[3] = parsed[0];
        break;
      case 2:
        result[0] = result[2] = parsed[0]; // top, bottom
        result[1] = result[3] = parsed[1]; // right, left
        break;
      case 3:
        result[0] = parsed[0];             // top
        result[1] = result[3] = parsed[1]; // right, left
        result[2] = parsed[2];             // bottom
        break;
      case 4:
        result[0] = parsed[0]; // top
        result[1] = parsed[1]; // right
        result[2] = parsed[2]; // bottom
        result[3] = parsed[3]; // left
        break;
    }

    return result;
  }

  private static Size parseSize(String value) {
    if (value == null || value.isBlank())
      return null;
    SizeUnit u = SizeUnit.PIXELS;
    if (value.toLowerCase().endsWith("em")) {
      u = SizeUnit.EMS;
      value = value.substring(0, value.length()-2).trim();
    } else if (value.toLowerCase().endsWith("pt")) {
      u = SizeUnit.POINTS;
      value = value.substring(0, value.length()-2).trim();
    } else if (value.toLowerCase().endsWith("px")) {
      u = SizeUnit.PIXELS;
      value = value.substring(0, value.length()-2).trim();
    }
    if (value.isBlank())
      return new Size(1, u);
    try {
      float v = Float.parseFloat(value);
      return new Size(v, u);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // quick manual test
  public static void main(String[] args) {
    String x = "margin: 5;;; header1 { color: #3f0; background-color: #f00;accent: block 7 #f0f; } header2 { color: #fff; accent: underline 2 #fff}";
    System.out.println(parseStyleString(x));
    // expected:
    //   {margin=5, header1-background-color=#f00, header1-color=#3f0, header2-color=#fff, header2-accent=underline 2 #fff, header1-accent=block 7 #f0f}
  }
}

