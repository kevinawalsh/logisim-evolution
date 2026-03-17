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

package com.cburch.logisim.data;

import com.cburch.logisim.gui.generic.ComboBox;
import com.cburch.logisim.util.StringGetter;

public final class BitWidth implements Comparable<BitWidth> {

  static class Attribute extends com.cburch.logisim.data.Attribute<BitWidth> {
    private BitWidth[] choices;

    public Attribute(String name, StringGetter disp) {
      super(name, disp);
      choices = prefab; // 1 to 32
    }

    public Attribute(String name, StringGetter disp, int min, int max) {
      super(name, disp);
      choices = new BitWidth[max - min + 1];
      for (int i = 0; i < choices.length; i++) {
        choices[i] = BitWidth.create(min + i);
      }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public java.awt.Component getCellEditor(BitWidth value) {
      ComboBox combo = new ComboBox<>(choices);
      if (value != null) {
        boolean found = false;
        for (BitWidth b : choices) {
          if (b.equals(value)) {
              found = true;
              break;
          }
        }
        if (!found)
          combo.addItem(value);
        combo.setSelectedItem(value);
      }
      return combo;
    }

    @Override
    public BitWidth parse(String value) {
      return BitWidth.parse(value);
    }

    @Override
    public Domain getDomain() {
      return Domain.ofIntRange(choices[0].getWidth(), choices[choices.length-1].getWidth());
    }
  }

  public static BitWidth create(int width) {
    if (width <= 0) {
      if (width == 0) {
        return UNKNOWN;
      } else {
        throw new IllegalArgumentException("width " + width
            + " must be positive");
      }
    } else if (width - 1 < prefab.length) {
      return prefab[width - 1];
    } else {
      // FIXME: should never happen?
      System.out.println("WARNING: width " + width + " exceeds max 32 supported");
      return new BitWidth(width);
    }
  }

  public static BitWidth parse(String str) {
    if (str == null || str.length() == 0) {
      throw new NumberFormatException("Width string cannot be null");
    }
    if (str.charAt(0) == '/')
      str = str.substring(1);
    return create(Integer.parseInt(str));
  }

  public static final BitWidth UNKNOWN = new BitWidth(0);
  public static final BitWidth ONE = new BitWidth(1);
  public static final BitWidth TWO = new BitWidth(2);
  public static final BitWidth THREE = new BitWidth(3);
  public static final BitWidth FOUR = new BitWidth(4);
  public static final BitWidth FIVE = new BitWidth(5);
  public static final BitWidth SIX = new BitWidth(6);
  public static final BitWidth SEVEN = new BitWidth(7);
  public static final BitWidth EIGHT = new BitWidth(8);

  private static final BitWidth[] prefab = new BitWidth[] {
    ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT,
   new BitWidth(9), new BitWidth(10), new BitWidth(11), new BitWidth(12),
   new BitWidth(13), new BitWidth(14), new BitWidth(15), new BitWidth(16),
   new BitWidth(17), new BitWidth(18), new BitWidth(19), new BitWidth(20),
   new BitWidth(21), new BitWidth(22), new BitWidth(23), new BitWidth(24),
   new BitWidth(25), new BitWidth(26), new BitWidth(27), new BitWidth(28),
   new BitWidth(29), new BitWidth(30), new BitWidth(31), new BitWidth(32) };

  final int width;

  // This method only supports widths from 1 to 32
  public static BitWidth of(int width) {
    if (width <= 0 || width > 32)
      throw new IllegalArgumentException("width " + width + " must be within 0 to 32");
    else
      return prefab[width-1];
  }

  private BitWidth(int width) {
    this.width = width;
  }

  public int compareTo(BitWidth other) {
    return this.width - other.width;
  }

  @Override
  public boolean equals(Object other_obj) {
    if (!(other_obj instanceof BitWidth))
      return false;
    BitWidth other = (BitWidth) other_obj;
    return this.width == other.width;
  }

  // does not work if width > 32
  public int getMask() {
    if (width == 0)
      return 0;
    else if (width == 32)
      return -1;
    else
      return (1 << width) - 1;
  }

  public int getWidth() {
    return width;
  }

  @Override
  public int hashCode() {
    return width;
  }

  @Override
  public String toString() {
    return "" + width;
  }
}
