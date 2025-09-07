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

class TextAttributes extends AbstractAttributeSet {
  private static final List<Attribute<?>> ATTRIBUTES =
      Arrays.asList(new Attribute<?>[] { Text.ATTR_TEXT, Text.ATTR_FONT,
        Text.ATTR_HALIGN, Text.ATTR_VALIGN, Text.FG_COLOR, Text.BG_COLOR });

  private String text;
  private Font font;
  private AttributeOption halign;
  private AttributeOption valign;
  private Color fg;
  private Color bg;

  private static final Color CLEAR = new Color(255, 255, 255, 0);

  public TextAttributes() {
    text = "text";
    font = StdAttr.DEFAULT_LABEL_FONT;
    halign = Text.ATTR_HALIGN.parse("center");
    valign = Text.ATTR_VALIGN.parse("base");
    fg = Color.BLACK;
    bg = CLEAR;
  }

  @Override
  protected void copyInto(AbstractAttributeSet destObj) {
    ; // nothing to do
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return ATTRIBUTES;
  }

  Font getFont() {
    return font;
  }

  int getHorizontalAlign() {
    return ((Integer) halign.getValue()).intValue();
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
    return null;
  }

  int getVerticalAlign() {
    return ((Integer) valign.getValue()).intValue();
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == Text.ATTR_TEXT) {
      text = (String) value;
      if (text == null || text.length() == 0)
        text = "text";
    }
    else if (attr == Text.ATTR_FONT)
      font = (Font) value;
    else if (attr == Text.ATTR_HALIGN)
      halign = (AttributeOption) value;
    else if (attr == Text.ATTR_VALIGN)
      valign = (AttributeOption) value;
    else if (attr == Text.FG_COLOR)
      fg = (Color) value;
    else if (attr == Text.BG_COLOR)
      bg = (Color) value;
  }

}
