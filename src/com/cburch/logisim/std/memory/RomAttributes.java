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

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.StdAttr;

class RomAttributes extends AbstractAttributeSet {

  // WARNING: The prefix of these lists before APPEARANCE must be identical. The
  // list of possible attributes depends on APPEARANCE, so during xml file
  // loading APPEARANCE must be set before the remaining attributes.
  private static List<Attribute<?>> ATTRIBUTES_CLASSIC = Arrays
      .asList(new Attribute<?>[] { Mem.ADDR_ATTR, Mem.DATA_ATTR, Mem.LINE_ATTR,
        Rom.CONTENTS_ATTR, StdAttr.LABEL, StdAttr.LABEL_FONT,
        StdAttr.APPEARANCE, Rom.ATTR_PROPORTIONS });

  private static List<Attribute<?>> ATTRIBUTES_ANSI = Arrays
      .asList(new Attribute<?>[] { Mem.ADDR_ATTR, Mem.DATA_ATTR, Mem.LINE_ATTR,
        Rom.CONTENTS_ATTR, StdAttr.LABEL, StdAttr.LABEL_FONT,
        StdAttr.APPEARANCE });

  private BitWidth addrBits = BitWidth.create(8);
  private BitWidth dataBits = BitWidth.create(8);
  private final RomContents contents = new RomContents(8, 8); // all changes are in-place
  private AttributeOption lineSize = Mem.SINGLE;
  private String Label = "";
  private Font LabelFont = StdAttr.DEFAULT_LABEL_FONT;
  private AttributeOption Appearance = StdAttr.APPEAR_CLASSIC;
  private AttributeOption Proportions = Rom.RECT;

  RomAttributes() { }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    RomAttributes d = (RomAttributes) dest;
    d.addrBits = addrBits;
    d.dataBits = dataBits;
    // d.contents = contents.duplicate();
    // modify in-place, using setDmensions+copyInto, so contents reference never changes
    d.contents.setDimensions(addrBits.getWidth(), dataBits.getWidth());
    d.contents.copyFrom(contents);
    d.lineSize = lineSize;
    d.LabelFont = LabelFont;
    d.Appearance = Appearance;
    d.Proportions = Proportions;
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return Appearance == StdAttr.APPEAR_CLASSIC ? ATTRIBUTES_CLASSIC : ATTRIBUTES_ANSI;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    if (attr == Mem.ADDR_ATTR)
      return (V) addrBits;
    if (attr == Mem.DATA_ATTR)
      return (V) dataBits;
    if (attr == Rom.CONTENTS_ATTR)
      return (V) contents;
    if (attr == Rom.CONTENTS_ATTR_DUPLICATE)
      return (V) contents.duplicate();
    if (attr == Mem.LINE_ATTR)
      return (V) lineSize;
    if (attr == StdAttr.LABEL)
      return (V) Label;
    if (attr == StdAttr.LABEL_FONT)
      return (V) LabelFont;
    if (attr == StdAttr.APPEARANCE)
      return (V) Appearance;
    if (attr == Rom.ATTR_PROPORTIONS)
      return (V) Proportions;
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == Mem.ADDR_ATTR) {
      addrBits = (BitWidth) value;
      contents.setDimensions(addrBits.getWidth(), dataBits.getWidth());
    } else if (attr == Mem.DATA_ATTR) {
      dataBits = (BitWidth) value;
      contents.setDimensions(addrBits.getWidth(), dataBits.getWidth());
    }
    else if (attr == Mem.LINE_ATTR)
      lineSize = (AttributeOption) value;
    else if (attr == Rom.CONTENTS_ATTR || attr == Rom.CONTENTS_ATTR_DUPLICATE) {
      // Occurs during xml reading, and when rom is moved on the canvas
      // CONTENTS_ATTR_DUPLICATE occurs during some undo/redo actions
      RomContents newContents = (RomContents) value;
      contents.setDimensions(newContents.getLogLength(), newContents.getValueWidth());
      contents.copyFrom(newContents);
      addrBits = BitWidth.create(contents.getLogLength());
      dataBits = BitWidth.create(contents.getValueWidth());
      // RomState for all simulations has a reference to the RomContents, so we
      // modify contents in-place rather than changing the reference.
    }
    else if (attr == StdAttr.LABEL)
      Label = (String) value;
    else if (attr == StdAttr.LABEL_FONT)
      LabelFont = (Font) value;
    else if (attr == StdAttr.APPEARANCE) {
      Appearance = (AttributeOption) value;
      fireAttributeListChanged();
    }
    else if (attr == Rom.ATTR_PROPORTIONS)
      Proportions = (AttributeOption) value;
  }

  @Override
  public <V> List<Attribute<?>> getAttributesForUndo(Attribute<V> attr, V newValue) {
    // Rom modifies the contents in-place, using a custom attribute editor, so
    // saving the value of CONTENTS_ATTR during undo/redo actions is not
    // effective. We instead use a special, unlisted CONTENTS_ATTR_DUPLICATE
    // attribute to capture a copy of the contents during destructive attribute
    // changes.
    if (attr == Mem.ADDR_ATTR && ((BitWidth)newValue).getWidth() < addrBits.getWidth())
      return List.of(Rom.CONTENTS_ATTR_DUPLICATE, Mem.ADDR_ATTR);
    else if (attr == Mem.DATA_ATTR && ((BitWidth)newValue).getWidth() < dataBits.getWidth())
      return List.of(Rom.CONTENTS_ATTR_DUPLICATE, Mem.DATA_ATTR);
    else if (attr == Rom.CONTENTS_ATTR) // should not occur during most Actions
      return List.of(Rom.CONTENTS_ATTR_DUPLICATE);
    else
      return null;
  }
}
