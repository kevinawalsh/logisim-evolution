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

import java.awt.Color;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;

class SevenSegmentAttributes extends AttributeSets.ArrayBacked {

  // WARNING: ATTR_DIGITS must come early, before ATTR_ENABLES_ACTIVE and
  // ATTR_PERSIST. The presence of those two depend on ATTR_DIGITS, so during
  // xml file loading ATTR_DIGITS must be set before them.
  
  static Attribute<?>[] ATTRS_1 = {
    SevenSegment.ATTR_DIGITS, /* no enable pin, so no enables-active or perist attribute */
    Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
    Io.ATTR_BACKGROUND, Io.ATTR_ACTIVE, StdAttr.LABEL,
    StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR };
  static List<Attribute<?>> LIST_ATTRS_1 = Arrays.asList(ATTRS_1);

  static Attribute<?>[] ATTRS_N = {
    SevenSegment.ATTR_DIGITS, SevenSegment.ATTR_ENABLES_ACTIVE, SevenSegment.ATTR_PERSIST,
    Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
    Io.ATTR_BACKGROUND, Io.ATTR_ACTIVE, StdAttr.LABEL,
    StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR };
  static List<Attribute<?>> LIST_ATTRS_N = Arrays.asList(ATTRS_N);

  public SevenSegmentAttributes() {
    super(ATTRS_N, new Object[] { 1, true, 10,
        new Color(240, 0, 0), SevenSegment.DEFAULT_OFF, Io.DEFAULT_BACKGROUND,
        Boolean.TRUE, "", Direction.EAST, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    updateGradient();
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    if (getValue(SevenSegment.ATTR_DIGITS).intValue() == 1)
      return LIST_ATTRS_1;
    else
      return LIST_ATTRS_N;
  }
  
  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    super.updateAttr(attr, value);
    if (attr == SevenSegment.ATTR_DIGITS)
      fireAttributeListChanged();
    if (attr == SevenSegment.ATTR_DIGITS || attr == SevenSegment.ATTR_PERSIST
        || attr == Io.ATTR_ON_COLOR || attr == Io.ATTR_OFF_COLOR)
      updateGradient();
  }

  @Override
  public <V> List<Attribute<?>> getAttributesForUndo(Attribute<V> attr, V newValue) {
    if (attr == SevenSegment.ATTR_DIGITS)
      return List.of(SevenSegment.ATTR_DIGITS, SevenSegment.ATTR_ENABLES_ACTIVE, SevenSegment.ATTR_PERSIST);
    else
      return null;
  }

  private Color[] gradient; // 2 or more colors for persistence

  private void updateGradient() {
    Color onColor = getValue(Io.ATTR_ON_COLOR);
    Color offColor = getValue(Io.ATTR_OFF_COLOR);
    int digits = getValue(SevenSegment.ATTR_DIGITS).intValue();
    int persist = getValue(SevenSegment.ATTR_PERSIST).intValue();
    if (digits == 1 || persist == 0) {
      gradient = new Color[2];
      gradient[0] = onColor;
      gradient[1] = offColor;
    } else {
      int n = 2 + Math.min(persist, 24);
      gradient = new Color[n];
      gradient[0] = onColor;
      gradient[n-1] = offColor;
      for (int i = 1; i < n-1; i++) {
        float t = (i - 1.0f) / (n - 1.0f);
        gradient[i] = blend(onColor, offColor, t); // (float) Math.pow(t, 0.35f));
      }
    }
  }

  private static Color blend(Color x, Color y, float t) {
    int r = Math.round(x.getRed()   + t * (y.getRed()   - x.getRed()));
    int g = Math.round(x.getGreen() + t * (y.getGreen() - x.getGreen()));
    int b = Math.round(x.getBlue() + t * (y.getBlue()  - x.getBlue()));
    int a = Math.round(x.getAlpha() + t * (y.getAlpha() - x.getAlpha()));
    return new Color(r, g, b, a);
  }

  public Color getColor(float amtOn) {
    int n = gradient.length;
    int i = Math.min(n-1, Math.max(0, (int)Math.floor((1.0f-amtOn)*n)));
    return gradient[i];
  }

}
