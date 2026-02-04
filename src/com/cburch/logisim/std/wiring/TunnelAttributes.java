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

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

class TunnelAttributes extends AbstractAttributeSet {
  private static final List<Attribute<?>> ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { StdAttr.FACING, StdAttr.WIDTH,
        StdAttr.LABEL, StdAttr.LABEL_FONT });

  private Direction facing;
  private BitWidth width;
  private String label;
  private Font labelFont;
  private int labelAnchorXOffset;
  private int labelAnchorYOffset;
  private int labelHAlign;
  private int labelVAlign;

  public TunnelAttributes() {
    facing = Direction.WEST;
    width = BitWidth.ONE;
    label = "";
    labelFont = StdAttr.DEFAULT_LABEL_FONT;
    configureLabel();
  }

  private void configureLabel() {
    Direction facing = this.facing;
    int x;
    int y;
    int halign;
    int valign;
    int margin = Tunnel.ARROW_MARGIN;
    if (facing == Direction.NORTH) {
      x = 0;
      y = margin;
      halign = ALIGN.H_CENTER;
      valign = ALIGN.V_TOP;
    } else if (facing == Direction.SOUTH) {
      x = 0;
      y = -margin;
      halign = ALIGN.H_CENTER;
      valign = ALIGN.V_BOTTOM;
    } else if (facing == Direction.EAST) {
      x = -margin;
      y = 0;
      halign = ALIGN.H_RIGHT;
      valign = ALIGN.V_CENTER_OVERALL;
    } else {
      x = margin;
      y = 0;
      halign = ALIGN.H_LEFT;
      valign = ALIGN.V_CENTER_OVERALL;
    }
    labelAnchorXOffset = x;
    labelAnchorYOffset = y;
    labelHAlign = halign;
    labelVAlign = valign;
  }

  @Override
  protected void copyInto(AbstractAttributeSet destObj) {
    ; // nothing to do
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return ATTRIBUTES;
  }

  Direction getFacing() {
    return facing;
  }

  Font getFont() {
    return labelFont;
  }

  String getLabel() {
    return label;
  }


  // label alignment and anchor relative to tunnel location origin
  int getLabelHAlign() { return labelHAlign; }
  int getLabelVAlign() { return labelVAlign; }
  int getLabelAnchorXOffset() { return labelAnchorXOffset; }
  int getLabelAnchorYOffset() { return labelAnchorYOffset; }

  @Override
  public <V> V getValue(Attribute<V> attr) {
    if (attr == StdAttr.FACING)
      return (V) facing;
    if (attr == StdAttr.WIDTH)
      return (V) width;
    if (attr == StdAttr.LABEL)
      return (V) label;
    if (attr == StdAttr.LABEL_FONT)
      return (V) labelFont;
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.FACING) {
      facing = (Direction) value;
      configureLabel();
    } else if (attr == StdAttr.WIDTH) {
      width = (BitWidth) value;
    } else if (attr == StdAttr.LABEL) {
      label = (String) value;
    } else if (attr == StdAttr.LABEL_FONT) {
      labelFont = (Font) value;
    }
  }
}
