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

import java.awt.Window;
import java.io.File;

import javax.swing.JTextField;

import com.cburch.logisim.util.StringGetter;

public abstract class Attribute<V> {
  private String name;
  private StringGetter disp;

  public Attribute(String name, StringGetter disp) {
    this.name = name;
    this.disp = disp;
  }

  // getCellEditor() may return:
  //  - JTextField
  //  - JComboBox
  //  - JInputDialog<?> [AttrTable will wrap in PopupEditor]
  //  - JInputComponent<?> [AttrTable will wrap in MyDialog+PopupEditor]
  //    (for the latter two, the java.awt.Component type isn't important at
  //     all... most of these are already a swing or awt component, and for
  //     those that are entirely custom, or wrappers around some UI, just extend
  //     java.awt.Component so the type checker is satisfied)
  //  - PopupEditor [private for now, but could be made public?]
  protected java.awt.Component getCellEditor(V value) {
    return new JTextField(toDisplayString(value));
  }

  public java.awt.Component getCellEditor(Window source, V value) {
    return getCellEditor(value);
  }

  public String getDisplayName() {
    return disp.toString();
  }

  public String getName() {
    return name;
  }

  public V parseFromUser(Window source, String value) {
    return parse(value);
  }

  public V parseFromFilesystem(File directory, String value) throws Exception {
    return parse(value);
  }

  public abstract V parse(String value);

  public String toDisplayString(V value) {
    return value == null ? "" : value.toString();
  }

  public Object toDisplayObject(V value) {
    return toDisplayString(value);
  }

  public String toStandardString(V value) {
    return value.toString();
  }

  public String toStandardStringRelative(V value, String outFilepath) {
    return toStandardString(value);
  }

  @Override
  public String toString() {
    return name;
  }

  public abstract Domain getDomain();

  // Attribute.Domain describes the domain of XML values an attribute can take on.
  public static class Domain {
    public enum Kind {
      LIST,          // finite, quickly enumerable - options[] holds the list of values
      INT_RANGE,     // integer range [min, max] inclusive
      DOUBLE_RANGE,  // floating-point range [min, max] inclusive
      TYPE,          // any value of a given type — freeform hint names it ("font", "color", etc.)
      DESCRIPTION,   // free-form prose for opaque or complicated types
      UNKNOWN        // no information available
    }

    public final Kind kind;
    public final String[] options;   // LIST only: the values that can appear in the XML attribute
    public final int imin, imax;     // INT_RANGE only
    public final double dmin, dmax;  // DOUBLE_RANGE only
    public final String hint;        // TYPE and DESCRIPTION: the text; null otherwise

    public static final Domain UNKNOWN
      = new Domain(Kind.UNKNOWN, null, 0, 0, 0, 0, "unknown");

    public static Domain ofList(Object[] options) {
      String vals[] = new String[options.length];
      for (int i = 0; i < options.length; i++)
        vals[i] = options[i].toString();
      return new Domain(Kind.LIST, vals, 0, 0, 0, 0, String.join(", ", vals));
    }
    public static Domain ofIntRange(int min, int max) {
      return new Domain(Kind.INT_RANGE, null, min, max, 0, 0, String.format("%d - %d, inclusive, integer", min, max));
    }
    public static Domain ofDoubleRange(double min, double max) {
      return new Domain(Kind.DOUBLE_RANGE, null, 0, 0, min, max, String.format("%f - %f, inclusive, floating point", min, max));
    }
    public static Domain ofType(String type) {
      return new Domain(Kind.TYPE, null, 0, 0, 0, 0, "any " + type);
    }
    public static Domain ofDescription(String desc) {
      return new Domain(Kind.DESCRIPTION, null, 0, 0, 0, 0, desc);
    }

    private Domain(Kind k, String[] o, int im, int ix, double dm, double dx, String h) {
      kind = k; options = o; imin = im; imax = ix; dmin = dm; dmax = dx; hint = h;
    }

  }

}
