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

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;

class PinAttributes extends ProbeAttributes {
  public static PinAttributes instance = new PinAttributes();

  // WARNING: The prefix of these lists before TYPE must be identical. The list of possible
  // attributes depends on TYPE, so during xml file loading TYPE must be set before the
  // remaining attributes.
  private static final List<Attribute<?>> INPIN_ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { StdAttr.FACING, Pin.ATTR_TYPE,
        StdAttr.WIDTH, Pin.ATTR_BEHAVIOR,
        StdAttr.LABEL, LABEL_LOC, StdAttr.LABEL_FONT,
        RadixOption.ATTRIBUTE });
  private static final List<Attribute<?>> OUTPIN_ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { StdAttr.FACING, Pin.ATTR_TYPE,
        StdAttr.WIDTH, /*Pin.ATTR_BEHAVIOR, */
        StdAttr.LABEL, LABEL_LOC, StdAttr.LABEL_FONT,
        RadixOption.ATTRIBUTE });

  BitWidth width = BitWidth.ONE;
  AttributeOption type = Pin.INPUT;
  AttributeOption behavior = Pin.SIMPLE;

  public PinAttributes() { }

  @Override
  public List<Attribute<?>> getAttributes() {
    if (type == Pin.INPUT)
      return INPIN_ATTRIBUTES;
    else
      return OUTPIN_ATTRIBUTES;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    if (attr == StdAttr.WIDTH)
      return (V) width;
    if (attr == Pin.ATTR_TYPE)
      return (V) type;
    if (attr == Pin.ATTR_BEHAVIOR)
      return (V) behavior;
    return super.getValue(attr);
  }

  boolean isInput() { return type == Pin.INPUT; }
  boolean isOutput() { return type == Pin.OUTPUT; }

  Value defaultBitValue() {
    if (isOutput() || behavior == Pin.TRISTATE)
      return Value.UNKNOWN;
    else if (behavior == Pin.PULL_UP)
      return Value.TRUE;
    else
      return Value.FALSE;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.WIDTH) {
      width = (BitWidth) value;
    } else if (attr == Pin.ATTR_TYPE) {
      fireAttributeListChanged();
      type = (AttributeOption) value;
    } else if (attr == Pin.ATTR_BEHAVIOR) {
      if (behavior != value) {
        behavior = (AttributeOption) value;
        fireAttributeListChanged(); // not needed?
      }
    } else {
      super.updateAttr(attr, value);
    }
  }
}
