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

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;

class CalloutAttributes extends TextAttributes {

  // WARNING: The prefix of these lists before FORMAT must be identical. The list of possible
  // attributes depends on FORMAT, so during xml file loading FORMAT must be set before the
  // remaining attributes.
  private static final List<Attribute<?>> ATTRIBUTES_AUTO_WRAPPING =
      Arrays.asList(new Attribute<?>[] {
        Text.ATTR_TEXT, Text.ATTR_FONT, Text.ATTR_HALIGN, Text.ATTR_VALIGN,
        Text.FG_COLOR, Text.BG_COLOR, Callout.ATTR_FORMAT, Text.ATTR_STYLE, 
        Text.TEXT_WIDTH, Callout.ATTR_DX, Callout.ATTR_DY });

  private static final List<Attribute<?>> ATTRIBUTES_MANUAL_WRAPPING =
      Arrays.asList(new Attribute<?>[] {
        Text.ATTR_TEXT, Text.ATTR_FONT, Text.ATTR_HALIGN, Text.ATTR_VALIGN,
        Text.FG_COLOR, Text.BG_COLOR, Callout.ATTR_FORMAT, Text.ATTR_STYLE, 
        Callout.ATTR_DX, Callout.ATTR_DY });

  private int dx, dy;

  public CalloutAttributes() {
    super("left", "center-overall", 120);
    dx = dy = -40;
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return isWrapping() ? ATTRIBUTES_AUTO_WRAPPING : ATTRIBUTES_MANUAL_WRAPPING;
  }

  int getDx() { return dx; }
  int getDy() { return dy; }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    if (attr == Callout.ATTR_DX)
      return (V) (Integer)dx;
    else if (attr == Callout.ATTR_DY)
      return (V) (Integer)dy;
    else if (attr == Callout.ATTR_FORMAT)
      return (V) format;
    else
      return super.getValue(attr);
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == Callout.ATTR_DX) {
      dx = (Integer) value;
    } else if (attr == Callout.ATTR_DY) {
      dy = (Integer) value;
    } else if (attr == Callout.ATTR_FORMAT) {
      format = (AttributeOption) value;
      computeLayout(styling.str);
      fireAttributeListChanged();
    } else {
      super.updateAttr(attr, value);
    }
  }

}
