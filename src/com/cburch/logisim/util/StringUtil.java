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

package com.cburch.logisim.util;

import java.awt.Font;
import java.awt.FontMetrics;

import com.cburch.logisim.data.Bounds;

public class StringUtil {
  public static <T> StringGetter constantGetter(final T value) {
    return new StringGetter() {
      public String toString() {
        return value.toString();
      }
    };
  }

  // public static String resizeString(String value, FontMetrics metrics,
  //     int maxWidth) {
  //   int width = metrics.stringWidth(value);

  //   if (width < maxWidth)
  //     return value;
  //   if (value.length() < 4)
  //     return value;
  //   return resizeString(
  //       new StringBuilder(value.substring(0, value.length() - 3) + ".."),
  //       metrics, maxWidth);
  // }

  // private static String resizeString(StringBuilder value,
  //     FontMetrics metrics, int maxWidth) {
  //   int width = metrics.stringWidth(value.toString());

  //   if (width < maxWidth)
  //     return value.toString();
  //   if (value.length() < 4)
  //     return value.toString();
  //   return resizeString(
  //       value.delete(value.length() - 3, value.length() - 2), metrics,
  //       maxWidth);
  // }

  public static String toHexString(int bits, int value) {
    if (bits < 32)
      value &= (1 << bits) - 1;
    String ret = Integer.toHexString(value);
    int len = (bits + 3) / 4;
    while (ret.length() < len)
      ret = "0" + ret;
    if (ret.length() > len)
      ret = ret.substring(ret.length() - len);
    return ret;
  }

  public static Bounds estimateBounds(int numChars, Font font) {
    // TODO - you can imagine being more clever here, but this is used only in a
    // very few places.
    if (numChars <= 0)
      numChars = 1;
    int lines = 1;
    float size = font.getSize2D();
    // A typical 12 pt height monospace font might be
    //   8.5 pt ascent (baseline to top of most chars)
    //   + 2.5 pt descent (baseline to bottom of most chars)
    //   + 1 pt leading (inter-line space)
    //   8 pt width (approx 2/3 aspect ratio)
    // FIXME: this is probably way off, it seems like this is describing a font
    // that is 12 px, not 12 pt.
    float h = size;
    float w = size * numChars * 2.0f / 3.0f; // assume approx monospace 12x8 aspect ratio
    return Bounds.create(0, 0, (int)Math.round(w), (int)Math.round(h));
  }

}
