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

package com.cburch.logisim.std.wiring;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;

class ProbeAttributes extends AbstractAttributeSet {
  public static ProbeAttributes instance = new ProbeAttributes();

  private static final List<Attribute<?>> ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { StdAttr.FACING, RadixOption.ATTRIBUTE,
        StdAttr.LABEL, StdAttr.LABEL_EDGE_LOC, StdAttr.LABEL_FONT, });

  Direction facing = Direction.EAST;
  String label = "";
  Direction labelloc = Direction.WEST;
  Font labelfont = StdAttr.DEFAULT_LABEL_FONT;
  RadixOption radix = RadixOption.RADIX_2;
  BitWidth width = BitWidth.ONE;

  public ProbeAttributes() {
  }

  @Override
  protected void copyInto(AbstractAttributeSet destObj) {
    ; // nothing to do
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return ATTRIBUTES;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E> E getValue(Attribute<E> attr) {
    if (attr == StdAttr.FACING)
      return (E) facing;
    if (attr == StdAttr.LABEL)
      return (E) label;
    if (attr == StdAttr.LABEL_EDGE_LOC)
      return (E) labelloc;
    if (attr == StdAttr.LABEL_FONT)
      return (E) labelfont;
    if (attr == RadixOption.ATTRIBUTE)
      return (E) radix;
    if (attr == StdAttr.WIDTH)
      return (E) width; // not part of our list, but used by SignalInfo
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.FACING)
      facing = (Direction) value;
    else if (attr == StdAttr.LABEL)
      label = (String) value;
    else if (attr == StdAttr.LABEL_EDGE_LOC)
      labelloc = (Direction) value;
    else if (attr == StdAttr.LABEL_FONT)
      labelfont = (Font) value;
    else if (attr == RadixOption.ATTRIBUTE)
      radix = (RadixOption) value;
  }
}
