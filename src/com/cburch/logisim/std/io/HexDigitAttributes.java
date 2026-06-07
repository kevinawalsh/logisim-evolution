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

package com.cburch.logisim.std.io;

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;

class HexDigitAttributes extends SevenSegmentAttributes {

  // WARNING: ATTR_DIGITS must come early, before ATTR_ENABLES_ACTIVE and
  // ATTR_PERSIST. The presence of those two depend on ATTR_DIGITS, so during
  // xml file loading ATTR_DIGITS must be set before them.
  
  static Attribute<?>[] HEX_ATTRS_1 = {
    SevenSegment.ATTR_DIGITS, /* no enable pin, so no enables-active or perist attribute */
    Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
    Io.ATTR_BACKGROUND, StdAttr.LABEL,
    StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR };
  static List<Attribute<?>> HEX_LIST_ATTRS_1 = Arrays.asList(ATTRS_1);

  static Attribute<?>[] HEX_ATTRS_N = {
    SevenSegment.ATTR_DIGITS, SevenSegment.ATTR_ENABLES_ACTIVE, SevenSegment.ATTR_PERSIST,
    Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
    Io.ATTR_BACKGROUND, StdAttr.LABEL,
    StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR };
  static List<Attribute<?>> HEX_LIST_ATTRS_N = Arrays.asList(ATTRS_N);

  public HexDigitAttributes() {
    super();
    setAttr(StdAttr.LABEL_LOC, Direction.NORTH); // hex defaults to north label, 7seg to east... why??
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    if (getValue(SevenSegment.ATTR_DIGITS).intValue() == 1)
      return HEX_LIST_ATTRS_1;
    else
      return HEX_LIST_ATTRS_N;
  }

}
