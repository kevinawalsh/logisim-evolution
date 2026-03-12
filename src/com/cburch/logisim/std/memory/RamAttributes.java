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
package com.cburch.logisim.std.memory;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.StdAttr;

public class RamAttributes extends AbstractAttributeSet {

  /* here the rest is defined */
  static final AttributeOption VOLATILE = new AttributeOption("volatile",
      S.getter("ramTypeVolatile"));
  static final AttributeOption NONVOLATILE = new AttributeOption("nonvolatile",
      S.getter("ramTypeNonVolatile"));
  static final Attribute<AttributeOption> ATTR_TYPE = Attributes.forOption(
      "type", S.getter("ramTypeAttr"), new AttributeOption[] {
        VOLATILE, NONVOLATILE });
  static final AttributeOption BUS_BIDIR = new AttributeOption("bidir",
      S.getter("ramBidirDataBus"));
  static final AttributeOption BUS_SEP = new AttributeOption("bibus",
      S.getter("ramSeparateDataBus"));
  static final Attribute<AttributeOption> ATTR_DBUS = Attributes.forOption(
      "databus", S.getter("ramDataAttr"), new AttributeOption[] {
        BUS_BIDIR, BUS_SEP });

  private static List<Attribute<?>> ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { Mem.ADDR_ATTR, Mem.DATA_ATTR, Mem.LINE_ATTR,
        StdAttr.TRIGGER, ATTR_TYPE, ATTR_DBUS,
        StdAttr.LABEL, StdAttr.LABEL_FONT,
        StdAttr.APPEARANCE});

  private BitWidth addrBits = BitWidth.create(8);
  private BitWidth dataBits = BitWidth.create(8);
  private AttributeOption lineSize = Mem.SINGLE;
  private String Label = "";
  private AttributeOption Trigger = StdAttr.TRIG_RISING;
  private AttributeOption Type = VOLATILE; // NONVOLATILE;
  private AttributeOption BusStyle = BUS_SEP; // BUS_BIDIR;
  private Font LabelFont = StdAttr.DEFAULT_LABEL_FONT;
  private AttributeOption Appearance = StdAttr.APPEAR_CLASSIC;

  RamAttributes() { }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    RamAttributes d = (RamAttributes) dest;
    d.addrBits = addrBits;
    d.dataBits = dataBits;
    d.Trigger = Trigger;
    d.BusStyle = BusStyle;
    d.LabelFont = LabelFont;
    d.Appearance = Appearance;
    d.lineSize = lineSize;
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return ATTRIBUTES;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    if (attr == Mem.ADDR_ATTR)
      return (V) addrBits;
    if (attr == Mem.DATA_ATTR)
      return (V) dataBits;
    if (attr == Mem.LINE_ATTR)
      return (V) lineSize;
    if (attr == StdAttr.LABEL)
      return (V) Label;
    if (attr == StdAttr.TRIGGER)
      return (V) Trigger;
    if (attr == ATTR_TYPE)
      return (V) Type;
    if (attr == ATTR_DBUS)
      return (V) BusStyle;
    if (attr == StdAttr.LABEL_FONT)
      return (V) LabelFont;
    if (attr == StdAttr.APPEARANCE)
      return (V) Appearance;
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == Mem.ADDR_ATTR)
      addrBits = (BitWidth) value;
    else if (attr == Mem.DATA_ATTR)
      dataBits = (BitWidth) value;
    else if (attr == Mem.LINE_ATTR)
      lineSize = (AttributeOption) value;
    else if (attr == StdAttr.LABEL)
      Label = (String) value;
    else if (attr == StdAttr.TRIGGER)
      Trigger = (AttributeOption) value;
    else if (attr == ATTR_TYPE)
      Type = (AttributeOption) value;
    else if (attr == ATTR_DBUS)
      BusStyle = (AttributeOption) value;
    else if (attr == StdAttr.LABEL_FONT)
      LabelFont = (Font) value;
    else if (attr == StdAttr.APPEARANCE)
      Appearance = (AttributeOption) value;
  }
}
