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

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;

class VideoAttributes extends AbstractAttributeSet {
  private final /*public*/ static VideoAttributes instance = new VideoAttributes(); // why?

  // WARNING: The prefix of these lists before BLANK must be identical. The list of possible
  // attributes depends on BLANK, so during xml file loading BLANK must be set before the
  // remaining attributes.
  private static final List<Attribute<?>> BLANK_FIXED_ATTRIBUTES =
      Arrays.asList(new Attribute<?>[]
        { Video.BLINK_OPTION, Video.RESET_OPTION,
          Video.BLANK_OPTION, Video.FIXED_OPTION,
          Video.MODEL_OPTION, Video.WIDTH_OPTION, Video.HEIGHT_OPTION, Video.SCALE_OPTION });

  private static final List<Attribute<?>> BLANK_INPUT_ATTRIBUTES =
      Arrays.asList(new Attribute<?>[]
        { Video.BLINK_OPTION, Video.RESET_OPTION,
          Video.BLANK_OPTION,
          Video.MODEL_OPTION, Video.WIDTH_OPTION, Video.HEIGHT_OPTION, Video.SCALE_OPTION });


  String blink = Video.BLINK_OPTIONS[0];
  String reset = Video.RESET_OPTIONS[0];
  String blank = Video.BLANK_OPTIONS[0];
  Video.ColorModelColor fixed = new Video.ColorModelColor(Video.MODEL_RGB, 0 /*black*/);
  String model = Video.MODEL_RGB;
  int width = 128;
  int height = 128;
  int scale = 2;

  public VideoAttributes() { }

  @Override
  protected void copyInto(AbstractAttributeSet destObj) {
    ; // nothing to do
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    if (blank == Video.BLANK_FIXED)
        return BLANK_FIXED_ATTRIBUTES;
    else
        return BLANK_INPUT_ATTRIBUTES;
  }

  @Override
  public <E> E getValue(Attribute<E> attr) {
    if (attr == Video.BLINK_OPTION)
      return (E) blink;
    if (attr == Video.RESET_OPTION)
      return (E) reset;
    if (attr == Video.BLANK_OPTION)
      return (E) blank;
    if (attr == Video.FIXED_OPTION)
      return (E) fixed;
    if (attr == Video.MODEL_OPTION)
      return (E) model;
    if (attr == Video.WIDTH_OPTION)
      return (E) Integer.valueOf(width);
    if (attr == Video.HEIGHT_OPTION)
      return (E) Integer.valueOf(height);
    if (attr == Video.SCALE_OPTION)
      return (E) Integer.valueOf(scale);
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == Video.BLINK_OPTION)
      blink = (String) value;
    if (attr == Video.RESET_OPTION)
      reset = (String) value;
    if (attr == Video.BLANK_OPTION) {
      if (value != blank) {
        fireAttributeListChanged(); // why before changing value
        blank = (String) value;
      }
    }
    if (attr == Video.FIXED_OPTION)
      fixed = (Video.ColorModelColor) value;
    if (attr == Video.MODEL_OPTION)
      model = (String) value;
    if (attr == Video.WIDTH_OPTION)
      width = (Integer) value;
    if (attr == Video.HEIGHT_OPTION)
      height = (Integer) value;
    if (attr == Video.SCALE_OPTION)
      scale = (Integer) value;
  }

}
