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

  public static String resizeString(String value, FontMetrics metrics,
      int maxWidth) {
    int width = metrics.stringWidth(value);

    if (width < maxWidth)
      return value;
    if (value.length() < 4)
      return value;
    return resizeString(
        new StringBuilder(value.substring(0, value.length() - 3) + ".."),
        metrics, maxWidth);
  }

  private static String resizeString(StringBuilder value,
      FontMetrics metrics, int maxWidth) {
    int width = metrics.stringWidth(value.toString());

    if (width < maxWidth)
      return value.toString();
    if (value.length() < 4)
      return value.toString();
    return resizeString(
        value.delete(value.length() - 3, value.length() - 2), metrics,
        maxWidth);
  }

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

  public static Bounds estimateBounds(String text, Font font) {
    return estimateAlignedBounds(text, font, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
  }

  // Note: For legacy reasons, vAlign is relative only to the first line of text.
  // See: std.base.Text for details.
  // Also, std.base.Text and std.wiring.Tunnel, the only callers outside this
  // file, now always use the fixt text string "ABC".
  public static Bounds estimateAlignedBounds(String text, Font font, int hAlign, int vAlign) {
    // TODO - you can imagine being more clever here
    if (text == null || text.length() == 0)
      text = "X"; // return Bounds.EMPTY_BOUNDS;
    int n = 0;
    int c = 0;
    int lines = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        n = (c > n ? c : n);
        c = 0;
        lines++;
      } else if (text.charAt(i) == '\t') {
        c += 4;
      } else {
        c++;
      }
    }
    if (text.charAt(text.length()-1) != '\n') {
      n = (c > n ? c : n);
      lines++;
    }
    float size = font.getSize2D();
    // A typical 12 pt height monospace font might be
    //   8.5 pt ascent (baseline to top of most chars)
    //   + 2.5 pt descent (baseline to bottom of most chars)
    //   + 1 pt leading (inter-line space)
    //   8 pt width (approx 2/3 aspect ratio)
    float h = size * lines;
    float w = size * n * 2.0f / 3.0f; // assume approx monospace 12x8 aspect ratio
    float a  = size * 8.5f / 12.0f;
    float x;
    float y;
    if (hAlign == GraphicsUtil.H_LEFT) {
      x = 0;
    } else if (hAlign == GraphicsUtil.H_RIGHT) {
      x = -w;
    } else {
      x = -w / 2;
    }
    if (vAlign == GraphicsUtil.V_TOP) {
      y = 0;
    } else if (vAlign == GraphicsUtil.V_CENTER) {
      y = -a / 2; // center of first line ascent
    } else if (vAlign == GraphicsUtil.V_CENTER_OVERALL) {
      y = -h / 2; // center of all lines of text
    } else if (vAlign == GraphicsUtil.V_BASELINE) {
      y = -a; // ascent of first line of text
    } else { // GraphicsUtil.V_BOTTOM
      // y = -h; // bottom of all lines of text
      y = -size; // bottom of first line of text
    }
    return Bounds.create((int)Math.round(x), (int)Math.round(y),
        (int)Math.round(w), (int)Math.round(h));
  }

}
