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
import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.instance.StdAttr;

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

class TextAttributes extends AbstractAttributeSet {

  // WARNING: The prefix of these lists before FORMAT must be identical. The list of possible
  // attributes depends on FORMAT, so during xml file loading FORMAT must be set before the
  // remaining attributes.
  private static final List<Attribute<?>> ATTRIBUTES_AUTO_WRAPPING =
      Arrays.asList(new Attribute<?>[] { Text.ATTR_TEXT, Text.ATTR_FONT, Text.ATTR_FORMAT, 
        Text.FG_COLOR, Text.BG_COLOR, Text.ATTR_HALIGN, Text.ATTR_VALIGN,
        Text.ATTR_STYLE, Text.TEXT_WIDTH });

  private static final List<Attribute<?>> ATTRIBUTES_MANUAL_WRAPPING =
      Arrays.asList(new Attribute<?>[] { Text.ATTR_TEXT, Text.ATTR_FONT, Text.ATTR_FORMAT,
        Text.FG_COLOR, Text.BG_COLOR, Text.ATTR_HALIGN, Text.ATTR_VALIGN, 
        Text.ATTR_STYLE });

  private static final List<Attribute<?>> ATTRIBUTES_MARKDOWNISH =
      Arrays.asList(new Attribute<?>[] { Text.ATTR_TEXT, Text.ATTR_FONT, Text.ATTR_FORMAT,
        Text.FG_COLOR, Text.BG_COLOR, Text.ATTR_STYLE });

  private String text; // note: never contains CRLF or CR, only LF
  private Font font;
  private AttributeOption halign;
  private AttributeOption valign;
  private Color fg;
  private Color bg;
  protected AttributeOption format;
  protected int width;
  protected TextStyling styling;

  private Text.LayoutEngine layout;

  private static final Color CLEAR = new Color(255, 255, 255, 0);

  public TextAttributes() {
    text = "text";
    font = StdAttr.DEFAULT_LABEL_FONT;
    halign = Text.ATTR_HALIGN.parse("center");
    valign = Text.ATTR_VALIGN.parse("base");
    fg = Color.BLACK;
    bg = CLEAR;
    format = Text.TEXT_FORMAT_PLAIN;
    width = 300;
    computeLayout("");
  }

  protected void computeLayout(String style) {
    styling = new TextStyling(style, font, fg, bg, format);
    if (format == Text.TEXT_FORMAT_MARKDOWNISH)
      layout = new StyledBoxLayout(text, width, styling);
    else if (format == Text.TEXT_FORMAT_WRAPPED)
      layout = new BoxLayout(text, width, getHorizontalAlign(), getVerticalAlign(), true, styling);
    else
      layout = new BoxLayout(text, -1, getHorizontalAlign(), getVerticalAlign(), false, styling);
  }

  @Override
  protected void copyInto(AbstractAttributeSet destObj) {
    ; // nothing to do
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return isMarkdownish() ? ATTRIBUTES_MARKDOWNISH :
      isWrapping() ? ATTRIBUTES_AUTO_WRAPPING : ATTRIBUTES_MANUAL_WRAPPING;
  }

  Text.LayoutEngine getLayout() {
    return layout;
  }

  TextStyling getStyling() {
    return styling;
  }

  Text.LayoutEngine getLayout(Integer altTextWidth) { // used during resizing
    if (altTextWidth == null || format == Text.TEXT_FORMAT_PLAIN || altTextWidth == width)
      return layout;
    else if (format == Text.TEXT_FORMAT_MARKDOWNISH)
      return new StyledBoxLayout(text, altTextWidth, styling);
    else
      return new BoxLayout(text, altTextWidth, getHorizontalAlign(), getVerticalAlign(), true, styling);
  }

  Font getFont() {
    return font;
  }

  int getHorizontalAlign() {
    if (isMarkdownish()) return ALIGN.H_LEFT;
    else return ((Integer) halign.getValue()).intValue();
  }

  int getVerticalAlign() {
    if (isMarkdownish()) return ALIGN.V_TOP;
    else return ((Integer) valign.getValue()).intValue();
  }

  String getText() {
    return text;
  }

  Color getFGColor() {
    return fg;
  }

  Color getBGColor() {
    return bg;
  }

  AttributeOption getFormat() {
    return format;
  }

  boolean isWrapping() {
    return (format != Text.TEXT_FORMAT_PLAIN);
  }

  boolean isMarkdownish() {
    return (format == Text.TEXT_FORMAT_MARKDOWNISH);
  }

  int getTextWidth() {
    return width;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    if (attr == Text.ATTR_TEXT)
      return (V) text;
    if (attr == Text.ATTR_FONT)
      return (V) font;
    if (attr == Text.ATTR_HALIGN)
      return (V) halign;
    if (attr == Text.ATTR_VALIGN)
      return (V) valign;
    if (attr == Text.FG_COLOR)
      return (V) fg;
    if (attr == Text.BG_COLOR)
      return (V) bg;
    if (attr == Text.ATTR_FORMAT)
      return (V) format;
    if (attr == Text.TEXT_WIDTH)
      return (V) (Integer)width;
    if (attr == Text.ATTR_STYLE)
      return (V) styling.str;
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    String style = styling.str;
    if (attr == Text.ATTR_TEXT) {
      String str = (String) value;
      if (str == null || str.isEmpty())
        text = "text";
      else
        text = normalize(str);
    } else if (attr == Text.ATTR_FONT) {
      font = (Font) value;
    } else if (attr == Text.ATTR_HALIGN) {
      halign = (AttributeOption) value;
    } else if (attr == Text.ATTR_VALIGN) {
      valign = (AttributeOption) value;
    } else if (attr == Text.FG_COLOR) {
      fg = (Color) value;
    } else if (attr == Text.BG_COLOR) {
      bg = (Color) value;
    } else if (attr == Text.ATTR_FORMAT) {
      format = (AttributeOption) value;
    } else if (attr == Text.TEXT_WIDTH) {
      width = (Integer) value;
    } else if (attr == Text.ATTR_STYLE) {
      style = (String) value;
    }
    computeLayout(style);
    if (attr == Text.ATTR_FORMAT)
      fireAttributeListChanged();
  }

  // public static normalize(String str) {
  //     // eliminate "\r\n" and any stray remaining "\r" in favor of "\n"
  //     str = str.replace("\r\n", "\n").replace("\r", "\n");
  //     return str;
  // }
  
  public static boolean needsNormalization(String s) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if ((ch >= 0x0000 && ch <= 0x001F && ch != '\n' && ch != '\t')
          || (ch >= 0x007F && ch <= 0x009F)
          || ch == '\u2028'
          || ch == '\u2029'
          || Character.isSurrogate(ch))
        return true;
    }
    return false;
  }

  public static String normalize(String s) {
    if (!needsNormalization(s))
      return s;

    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);

      // Normalize CRLF/CR -> LF
      if (ch == '\r') {
        if (i + 1 < s.length() && s.charAt(i + 1) == '\n')
          i++; // consume LF in CRLF
        out.append('\n');
        continue;
      }

      // Normalize other newline-ish separators -> LF
      // except for LINE SEPARATOR U+2028, which is used as a non-paragraph line break
      if (ch == '\u000B' || ch == '\u000C' || ch == '\u0085' || ch == '\u2029') {
        out.append('\n');
        continue;
      }

      // Keep TAB and LF as-is
      if (ch == '\n' || ch == '\t') {
        out.append(ch);
        continue;
      }

      // Strip NUL (0x00) + DEL (0x7F) + odd C0 (0x00-0x1F) controls + odd C1 controls (0x80-0x9F)
      if ((ch >= 0x0000 && ch <= 0x001F) || (ch >= 0x007F && ch <= 0x009F)) {
        continue;
      }

      // Replace ill-formed surrogate pairs with U+FFFD
      if (Character.isHighSurrogate(ch)) {
        if (i + 1 < s.length()) {
          char lo = s.charAt(i + 1);
          if (Character.isLowSurrogate(lo)) {
            out.append(ch).append(lo);
            i++; // consumed low surrogate
          } else {
            out.append('\uFFFD');
          }
        } else {
          out.append('\uFFFD');
        }
        continue;
      }
      if (Character.isLowSurrogate(ch)) {
        // unpaired low surrogate
        out.append('\uFFFD');
        continue;
      }

      // Otherwise, keep as-is
      out.append(ch);
    }

    return out.toString();
  }

}
