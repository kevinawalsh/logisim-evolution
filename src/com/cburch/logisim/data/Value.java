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
import static com.cburch.logisim.data.Strings.S;

import java.awt.Color;
import java.util.Arrays;

import com.cburch.logisim.util.Cache;
import com.cburch.logisim.circuit.CircuitWires.BusConnection;

public final class Value {

  private static Value create(int width, int error, int unknown, int value) {
    if (width == 0) {
      return Value.NIL;
    } else if (width == 1) {
      if ((error & 1) != 0)
        return Value.ERROR;
      else if ((unknown & 1) != 0)
        return Value.UNKNOWN;
      else if ((value & 1) != 0)
        return Value.TRUE;
      else
        return Value.FALSE;
    } else {
      int mask = (width == 32 ? -1 : ~(-1 << width));
      error = error & mask;
      unknown = unknown & mask & ~error;
      value = value & mask & ~unknown & ~error;

      int hashCode = 31 * (31 * (31 * width + error) + unknown) + value;
      Value val = cache.get(hashCode);
      if (val != null && val.value == value && val.width == width
            && val.error == error && val.unknown == unknown)
          return val;
      Value ret = new Value(width, error, unknown, value);
      cache.put(hashCode, ret);
      return ret;
    }
  }

  public static Value create_unsafe(int width, int error, int unknown, int value) {
    int hashCode = 31 * (31 * (31 * width + error) + unknown) + value;
    Value val = cache.get(hashCode);
    if (val != null && val.value == value && val.width == width
        && val.error == error && val.unknown == unknown)
      return val;
    Value ret = new Value(width, error, unknown, value);
    cache.put(hashCode, ret);
    return ret;
  }

  public static Value create(Value[] values) {
    if (values.length == 0)
      return NIL;
    if (values.length == 1)
      return values[0];
    if (values.length > MAX_WIDTH)
      throw new RuntimeException("Cannot have more than " + MAX_WIDTH
          + " bits in a value");

    int width = values.length;
    int value = 0;
    int unknown = 0;
    int error = 0;
    for (int i = 0; i < values.length; i++) {
      int mask = 1 << i;
      if (values[i] == TRUE)
        value |= mask;
      else if (values[i] == FALSE) /* do nothing */
        ;
      else if (values[i] == UNKNOWN)
        unknown |= mask;
      else if (values[i] == ERROR)
        error |= mask;
      else {
        throw new RuntimeException("unrecognized value " + values[i]);
      }
    }
    return Value.create(width, error, unknown, value);
  }

  public static Value createError(BitWidth bits) {
    return Value.create(bits.getWidth(), -1, 0, 0);
  }

  public static Value createKnown(BitWidth bits, int value) {
    return Value.create(bits.getWidth(), 0, 0, value);
  }

  public static Value createUnknown(BitWidth bits) {
    return Value.create(bits.getWidth(), 0, -1, 0);
  }

  public static Value createUnknown(int bits) {
    return Value.create(bits, 0, -1, 0);
  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  public static Value fromLogString(BitWidth width, String t)
      throws Exception {
    int radix = radixOfLogString(width, t);
    int offset;

    if (radix == 16 || radix == 8)
      offset = 2;
    else if (radix == 10 && t.startsWith("-"))
      offset = 1;
    else
      offset = 0;

    int n = t.length();

    if (n <= offset)
      throw new Exception("expected digits");

    int w = width.getWidth();
    long value = 0, unknown = 0;

    for (int i = offset; i < n; i++) {
      char c = t.charAt(i);
      int d;

      if ((c == 'x' || c == 'z') && radix != 10)
        d = -1;
      else if ('0' <= c && c <= '9')
        d = c - '0';
      else if ('a' <= c && c <= 'f')
        d = 0xa + (c - 'a');
      else if ('A' <= c && c <= 'F')
        d = 0xA + (c - 'A');
      else
        throw new Exception("unexpected character '"
            + t.substring(i, i + 1) + "' in \"" + t + "\"");

      if (d >= radix)
        throw new Exception("unexpected character '"
            + t.substring(i, i + 1) + "' in \"" + t + "\"");

      value *= radix;
      unknown *= radix;
      if ((value >> (radix == 10 ? 33 : w)) != 0 || (unknown >> 36) != 0)
        throw new Exception("too many bits in \"" + t + "\"");

      if (radix != 10) {
        if (d == -1)
          unknown |= (radix - 1);
        else
          value |= d;
      } else {
        if (d == -1)
          unknown += (radix - 1);
        else
          value += d;
      }

    }
    if (radix == 10 && t.charAt(0) == '-')
      value = -value;

    if (w == 32) {
      if (((value & 0x7FFFFFFF) >> (w - 1)) != 0)
        throw new Exception("too many bits in \"" + t + "\"");
    } else {
      if ((value >> w) != 0)
        throw new Exception("too many bits in \"" + t + "\"");
    }

    unknown &= ((1L << w) - 1);
    int v = (int) (value & 0x00000000ffffffff);
    int u = (int) (unknown & 0x00000000ffffffff);
    return create(w, 0, u, v);
  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  public static int radixOfLogString(BitWidth width, String t) {
    if (t.startsWith("0x"))
      return 16;
    if (t.startsWith("0o"))
      return 8;
    if (t.length() == width.getWidth())
      return 2;

    return 10;
  }

  public static Value repeat(Value base, BitWidth width) {
    return repeat(base, width.getWidth());
  }

  public static Value repeat(Value base, int bits) {
    if (base.getWidth() != 1) {
      throw new IllegalArgumentException(
          "first parameter must be one bit");
    }
    if (bits == 1) {
      return base;
    } else {
      Value[] ret = new Value[bits];
      Arrays.fill(ret, base);
      return create(ret);
    }
  }

  private static final Cache<Value> cache = new Cache<>();

  // these are not cached, instead they are checked explicitly in create()
  public static final Value FALSE = new Value(1, 0, 0, 0);
  public static final Value TRUE = new Value(1, 0, 0, 1);
  public static final Value UNKNOWN = new Value(1, 0, 1, 0);
  public static final Value ERROR = new Value(1, 1, 0, 0);
  public static final Value NIL = new Value(0, 0, 0, 0);

  public static final int MAX_WIDTH = 32;
  public static final Color NIL_COLOR = Color.GRAY;
  public static final Color FALSE_COLOR = new Color(0, 100, 0);
  public static final Color TRUE_COLOR = new Color(0, 210, 0);
  public static final Color UNKNOWN_COLOR = new Color(40, 40, 255);
  public static final Color ERROR_COLOR = new Color(192, 0, 0);

  public static final Color WIDTH_ERROR_COLOR = new Color(255, 123, 0);
  public static final Color WIDTH_ERROR_CAPTION_COLOR = new Color(85, 0, 0);
  public static final Color WIDTH_ERROR_HIGHLIGHT_COLOR = new Color(255, 255, 0);
  public static final Color WIDTH_ERROR_CAPTION_BGCOLOR = new Color(255, 230, 210);

  public static final Color MULTI_COLOR = Color.BLACK;


  private final int width;
  private final int error;
  private final int unknown;
  private final int value;

  private Value(int width, int error, int unknown, int value) {
    // To ensure that the one-bit values are unique, this should be called
    // only for the one-bit values and by the private create method
    this.width = width;
    this.error = error;
    this.unknown = unknown;
    this.value = value;
  }

  public Value and(Value other) {
    if (other == null)
      return this;
    if (this.width == 1 && other.width == 1) {
      if (this == FALSE || other == FALSE)
        return FALSE;
      if (this == TRUE && other == TRUE)
        return TRUE;
      return ERROR;
    } else {
      int false0 = ~this.value & ~this.error & ~this.unknown;
      int false1 = ~other.value & ~other.error & ~other.unknown;
      int falses = false0 | false1;
      return Value.create(Math.max(this.width, other.width), (this.error
            | other.error | this.unknown | other.unknown)
          & ~falses, 0, this.value & other.value);
    }
  }

  public Value controls(Value other) { // e.g. tristate buffer
    if (other == null)
      return null;
    if (this.width == 1) {
      if (this == FALSE)
        return Value.create(other.width, 0, -1, 0);
      if (this == TRUE || this == UNKNOWN)
        return other;
      return Value.create(other.width, -1, 0, 0);
    } else if (this.width != other.width) {
      return Value.create(other.width, -1, 0, 0);
    } else {
      int enabled = (this.value | this.unknown) & ~this.error;
      int disabled = ~this.value & ~this.unknown & ~this.error;
      return Value.create(other.width,
          (this.error | (other.error & ~disabled)),
          (disabled | other.unknown),
          (enabled & other.value));
    }
  }

  public Value combine(Value other) {
    if (other == null)
      return this;
    if (this == NIL)
      return other;
    if (other == NIL)
      return this;
    if (this.width == 1 && other.width == 1) {
      if (this == other)
        return this;
      if (this == UNKNOWN)
        return other;
      if (other == UNKNOWN)
        return this;
      return ERROR;
    } else if (this.width == other.width) {
      int disagree = (this.value ^ other.value) & ~(this.unknown | other.unknown);
      return Value.create(
          width,
          this.error | other.error | disagree,
          this.unknown & other.unknown,
          this.value | other.value);
    } else {
      int thisknown = ~this.unknown & (this.width == 32 ? -1 : ~(-1 << this.width));
      int otherknown = ~other.unknown & (other.width == 32 ? -1 : ~(-1 << other.width));
      int disagree = (this.value ^ other.value) & thisknown & otherknown;
      return Value.create(
          Math.max(this.width, other.width),
          this.error | other.error | disagree,
          ~thisknown & ~otherknown,
          this.value | other.value);
    }
  }

  // public static final Value combineLikeWidths(Value[] vals) { // all widths must match
  public static final Value combineLikeWidths(int width, BusConnection[] vals) { // all widths must match
    int n = vals.length;
    for (int i = 0; i < n; i++) {
      Value v = vals[i].drivenValue;
      if (v != null && v != NIL) {
        int error = v.error;
        int unknown = v.unknown;
        int value = v.value;
        for (int j = i+1; j < n; j++) {
          v = vals[j].drivenValue;
          if (v == null || v == NIL)
            continue;
          if (v.width != width)
            throw new IllegalArgumentException("INTERNAL ERROR: mismatched widths in Value.combine");
          int disagree = (value ^ v.value) & ~(unknown | v.unknown);
          error |= v.error | disagree;
          unknown &= v.unknown;
          value |= v.value;
        }
        return Value.create(width, error, unknown, value);
      }
    }
    return Value.createUnknown(width);
  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  public boolean compatible(Value other) {
    // where this has a value, other must have same value
    // where this has unknown, other can have unknown or any value
    // where this has error, other must have error
    return (this.width == other.width && this.error == other.error
        && this.value == (other.value & ~this.unknown) && this.unknown == (other.unknown | this.unknown));
  }

  @Override
  public boolean equals(Object other_obj) {
    if (!(other_obj instanceof Value))
      return false;
    Value other = (Value) other_obj;
    boolean ret = this.width == other.width && this.error == other.error
        && this.unknown == other.unknown && this.value == other.value;
    return ret;
  }

  public Value extendWidth(int newWidth, Value others) {
    if (width == newWidth)
      return this;
    int maskInverse = (width == 32 ? 0 : (-1 << width));
    if (others == Value.ERROR) {
      return Value.create(newWidth, error | maskInverse, unknown, value);
    } else if (others == Value.FALSE) {
      return Value.create(newWidth, error, unknown, value);
    } else if (others == Value.TRUE) {
      return Value.create(newWidth, error, unknown, value | maskInverse);
    } else {
      return Value.create(newWidth, error, unknown | maskInverse, value);
    }
  }

  public Value get(int which) {
    if (which < 0 || which >= width)
      return ERROR;
    int mask = 1 << which;
    if ((error & mask) != 0)
      return ERROR;
    else if ((unknown & mask) != 0)
      return UNKNOWN;
    else if ((value & mask) != 0)
      return TRUE;
    else
      return FALSE;
  }

  public Value extract(int from /* inclusive */ , int to /* exclusive */) {
    if (from < 0 || from >= to || to > width) {
      throw new RuntimeException("Invalid range of bits to extract");
    } else {
      int n = to - from;
      int mask = (n == 32 ? -1 : ((1 << n) - 1)) << from;
      return Value.create(n,
          (this.error & mask) >> from,
          (this.unknown & mask) >> from,
          (this.value & mask) >> from);
    }
  }

  public Value[] getAll() {
    Value[] ret = new Value[width];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = get(i);
    }
    return ret;
  }

  public BitWidth getBitWidth() {
    return BitWidth.create(width);
  }

  public Color getColor() {
    if (error != 0) {
      return ERROR_COLOR;
    } else if (width == 0) {
      return NIL_COLOR;
    } else if (width == 1) {
      if (this == UNKNOWN)
        return UNKNOWN_COLOR;
      else if (this == TRUE)
        return TRUE_COLOR;
      else
        return FALSE_COLOR;
    } else {
      return MULTI_COLOR;
    }
  }

  public int getWidth() {
    return width;
  }

  @Override
  public int hashCode() {
    int ret = width;
    ret = 31 * ret + error;
    ret = 31 * ret + unknown;
    ret = 31 * ret + value;
    return ret;
  }

  public boolean isErrorValue() {
    return error != 0;
  }

  public boolean isFullyDefined() {
    return width > 0 && error == 0 && unknown == 0;
  }

  public boolean isUnknown() {
    if (width == 32) {
      return error == 0 && unknown == -1;
    } else {
      return error == 0 && unknown == ((1 << width) - 1);
    }
  }

  public Value not() {
    if (width <= 1) {
      if (this == TRUE)
        return FALSE;
      if (this == FALSE)
        return TRUE;
      return ERROR;
    } else {
      return Value.create(this.width, this.error | this.unknown, 0,
          ~this.value);
    }
  }

  public Value or(Value other) {
    if (other == null)
      return this;
    if (this.width == 1 && other.width == 1) {
      if (this == TRUE || other == TRUE)
        return TRUE;
      if (this == FALSE && other == FALSE)
        return FALSE;
      return ERROR;
    } else {
      int true0 = this.value & ~this.error & ~this.unknown;
      int true1 = other.value & ~other.error & ~other.unknown;
      int trues = true0 | true1;
      return Value.create(Math.max(this.width, other.width), (this.error
            | other.error | this.unknown | other.unknown)
          & ~trues, 0, this.value | other.value);
    }
  }

  public Value set(int which, Value val) {
    if (val.width != 1) {
      throw new RuntimeException("Cannot set multiple values");
    } else if (which < 0 || which >= width) {
      throw new RuntimeException("Attempt to set outside value's width");
    } else if (width == 1) {
      return val;
    } else {
      int mask = ~(1 << which);
      return Value.create(this.width,
          (this.error & mask) | (val.error << which),
          (this.unknown & mask) | (val.unknown << which),
          (this.value & mask) | (val.value << which));
    }
  }

  public String toBinaryString() {
    switch (width) {
    case 0:
      return "-";
    case 1:
      if (error != 0)
        return "E";
      else if (unknown != 0)
        return "z";
      else if (value != 0)
        return "1";
      else
        return "0";
    default:
      StringBuilder ret = new StringBuilder();
      for (int i = width - 1; i >= 0; i--) {
        ret.append(get(i).toString());
      }
      return ret.toString();
    }
  }

  public String toDecimalString(boolean signed) {
    if (width == 0)
      return "-";
    if (isErrorValue())
      return S.get("valueError");
    if (!isFullyDefined())
      return S.get("valueUnknown");

    int value = toIntValue();
    if (signed) {
      if (width < 32 && (value >> (width - 1)) != 0) {
        value |= (-1) << width;
      }
      return "" + value;
    } else {
      return "" + ((long) value & 0xFFFFFFFFL);
    }
  }

  private static final int[] U_DECIMAL_WIDTH = {
    1, 1, 1, 1, // 0 bits, 1 bits, 2 bits, 3 bits
    2, 2, 2, // 4 bits, 5 bits, 6 bits
    3, 3, 3, // 7 bits, 8 bits, 9 bits
    4, 4, 4, 4, // 10 bits, 11 bits, 12 bits, 13 bits
    5, 5, 5, // 14 bits, 15 bits, 16 bits
    6, 6, 6, // 17 bits, 18 bits, 19 bits
    7, 7, 7, 7, // 20 bits, 21 bits, 22 bits, 23 bits
    8, 8, 8, // 24 bits, 25 bits, 26 bits
    9, 9, 9, // 27 bits, 28 bits, 29 bits
    10, 10, 10, // 30 bits, 31 bits, 32 bits
  };

  private static final int[] S_DECIMAL_WIDTH = {
    1, // 0 bits
    2, 2, 2, 2, // 1 bits, 2 bits, 3 bits, 4 bits
    3, 3, 3, // 5 bits, 6 bits, 7 bits
    4, 4, 4, // 8 bits, 9 bits, 10 bits
    5, 5, 5, 5, // 11 bits, 12 bits, 13 bits, 14 bits
    6, 6, 6, // 15 bits, 16 bits, 17 bits
    7, 7, 7, // 18 bits, 19 bits, 20 bits
    8, 8, 8, 8, // 21 bits, 22 bits, 23 bits, 24 bits
    9, 9, 9, // 25 bits, 26 bits, 27 bits
    10, 10, 10, // 28 bits, 29 bits, 30 bits
    11, 11, // 31 bits, 32 bits
  };

  public String toFixedWidthDecimalString(boolean signed) {
    if (width == 0)
      return "-";
    int strwidth = signed ? S_DECIMAL_WIDTH[width] : U_DECIMAL_WIDTH[width];
    if (isErrorValue()) {
      String a = S.get("valueError");
      if (a.length() > strwidth)
        a = "E";
      return widenString(strwidth, a);
    }
    if (!isFullyDefined()) {
      String a = S.get("valueUnknown");
      if (a.length() > strwidth)
        a = "z";
      return widenString(strwidth, a);
    }

    int value = toIntValue();
    if (signed) {
      if (width < 32 && (value >> (width - 1)) != 0) {
        value |= (-1) << width;
      }
      return widenString(strwidth, "" + value);
    } else {
      return widenString(strwidth, "" + ((long) value & 0xFFFFFFFFL));
    }
  }

  private static String widenString(int w, String s) {
    return String.format("%"+w+"s", s);
  }

  public String toDisplayString() {
    switch (width) {
    case 0:
      return "-";
    case 1:
      if (error != 0)
        return "E";
      else if (unknown != 0)
        return "z";
      else if (value != 0)
        return "1";
      else
        return "0";
    default:
      StringBuilder ret = new StringBuilder();
      for (int i = width - 1; i >= 0; i--) {
        ret.append(get(i).toString());
        if (i % 4 == 0 && i != 0)
          ret.append(" ");
      }
      return ret.toString();
    }
  }

  public String toDisplayString(int radix) {
    switch (radix) {
    case 2:
      return toDisplayString();
    case 8:
      return toOctalString();
    case 16:
      return toHexString();
    default:
      if (width == 0)
        return "-";
      if (isErrorValue())
        return S.get("valueError");
      if (!isFullyDefined())
        return S.get("valueUnknown");
      return Integer.toString(toIntValue(), radix);
    }
  }

  public String toHexString() {
    if (width <= 1) {
      return toString();
    } else {
      Value[] vals = getAll();
      char[] c = new char[(vals.length + 3) / 4];
      for (int i = 0; i < c.length; i++) {
        int k = c.length - 1 - i;
        int frst = 4 * k;
        int last = Math.min(vals.length, 4 * (k + 1));
        int v = 0;
        c[i] = '?';
        for (int j = last - 1; j >= frst; j--) {
          if (vals[j] == Value.ERROR) {
            c[i] = 'E';
            break;
          }
          if (vals[j] == Value.UNKNOWN) {
            c[i] = 'z';
            break;
          }
          v = 2 * v;
          if (vals[j] == Value.TRUE)
            v++;
        }
        if (c[i] == '?')
          c[i] = Character.forDigit(v, 16);
      }
      return new String(c);
    }
  }

  public int toIntValue() {
    if (error != 0)
      return -1;
    if (unknown != 0)
      return -1;
    return value;
  }

  public long extendAsLong(boolean signed) {
    if (error != 0)
      return -1;
    if (unknown != 0)
      return -1;
    if (!signed)
      return value & 0xffffffffL;
    else
      return (((long)value) << (64-width)) >> (64-width);
  }

  public String toOctalString() {
    if (width <= 1) {
      return toString();
    } else {
      Value[] vals = getAll();
      char[] c = new char[(vals.length + 2) / 3];
      for (int i = 0; i < c.length; i++) {
        int k = c.length - 1 - i;
        int frst = 3 * k;
        int last = Math.min(vals.length, 3 * (k + 1));
        int v = 0;
        c[i] = '?';
        for (int j = last - 1; j >= frst; j--) {
          if (vals[j] == Value.ERROR) {
            c[i] = 'E';
            break;
          }
          if (vals[j] == Value.UNKNOWN) {
            c[i] = 'z';
            break;
          }
          v = 2 * v;
          if (vals[j] == Value.TRUE)
            v++;
        }
        if (c[i] == '?')
          c[i] = Character.forDigit(v, 8);
      }
      return new String(c);
    }
  }

  @Override
  public String toString() {
    switch (width) {
    case 0:
      return "-";
    case 1:
      if (error != 0)
        return "E";
      else if (unknown != 0)
        return "z";
      else if (value != 0)
        return "1";
      else
        return "0";
    default:
      StringBuilder ret = new StringBuilder();
      for (int i = width - 1; i >= 0; i--) {
        ret.append(get(i).toString());
        if (i % 4 == 0 && i != 0)
          ret.append(" ");
      }
      return ret.toString();
    }
  }

  public Value xor(Value other) {
    if (other == null)
      return this;
    if (this.width <= 1 && other.width <= 1) {
      if (this == ERROR || other == ERROR)
        return ERROR;
      if (this == UNKNOWN || other == UNKNOWN)
        return ERROR;
      if (this == NIL || other == NIL)
        return ERROR;
      if ((this == TRUE) == (other == TRUE))
        return FALSE;
      return TRUE;
    } else {
      return Value.create(Math.max(this.width, other.width), this.error
          | other.error | this.unknown | other.unknown, 0, this.value
          ^ other.value);
    }
  }

  public static boolean equal(Value a, Value b) {
    if ((a == null || a == Value.NIL) &&
        (b == null || b == Value.NIL))
      return true; // both are effectively NIL
    if (a != null && b != null && a.equals(b))
      return true; // both are same non-NIL value
    return false;
  }

  public Value pullTowardsBits(Value other) {
    // wherever this is unknown, use other's value for that bit instead
    if (width <= 0 || unknown == 0 || other.width <= 0)
      return this;
    int e = error | (unknown & other.error);
    int v = value | (unknown & other.value);
    int u = unknown & (other.unknown | (other.width == 32 ? 0 : (-1 << other.width)));
    return Value.create(width, e, u, v);
  }

  public Value pullEachBitTowards(Value bit) {
    // wherever this is unknown, use bit instead
    if (width <= 0 || unknown == 0 || bit.width <= 0)
      return this;
    if (bit == ERROR)
      return Value.create(width, error | unknown, 0, value);
    else if (bit == TRUE)
      return Value.create(width, error, 0, value | unknown);
    else if (bit == FALSE)
      return Value.create(width, error, 0, value | 0);
    else if (bit == UNKNOWN)
      return this;
    else
      throw new IllegalArgumentException("pull value must be 1, 0, X, or E");
  }

}
