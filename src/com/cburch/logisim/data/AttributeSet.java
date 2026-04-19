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

import java.util.List;

public interface AttributeSet {

  public void addAttributeWeakListener(Object owner, AttributeListener l);
  public void removeAttributeWeakListener(Object owner, AttributeListener l);

  public Object clone();

  public List<Attribute<?>> getAttributes();

  public default Attribute<?> getAttribute(String name) {
    for (Attribute<?> attr : getAttributes())
      if (attr.getName().equals(name))
        return attr;
    return null;
  }

  public default boolean containsAttribute(Attribute<?> attr) {
    return getAttributes().contains(attr);
  }

  public default boolean isReadOnly(Attribute<?> attr) { return false; }

  public default boolean isToSave(Attribute<?> attr) { return true; }

  public default void setReadOnly(Attribute<?> attr, boolean value) {
    throw new UnsupportedOperationException("Attribute.setReadOnly");
  }

  public default void setToSave(Attribute<?> attr, boolean value) {
    // optional, so no error
  }

  // For most attributes, x0 = getValue(a0); setAttr(a0, y0);
  // can be undone by calling setAttr(a0, x0);
  // In those cases, getAttributesForUndo() should return null.
  // But for a few components, modifying the value of a0 can, sometimes, affect
  // the value of a1, a2, ...
  // For those cases, getAttributesForUndo() returns a list of attributes, in
  // order, and including a0, that should be captured, and which can, if
  // re-applied in the opposite order, undo the change.
  // With Constant for example:
  //   getAttributesForUndo(StdAttr.WIDTH) --> [ Constant.VALUE_ATTR, StdAttr.WIDTH ]
  // So a CircuitChange or other action that changes StdAttr.WIDTH for Constant
  // would essentially do:
  //   x0 = getValue(Constant.VALUE_ATTR)
  //   x1 = getValue(StdAttr.WIDTH_ATTR)
  //   setValue(StdAttr.WIDTH_ATTR, y0)
  // And the undo operation for the action would do:
  //   setValue(StdAttr.WIDTH_ATTR, x1)
  //   setValue(Constant.VALUE_ATTR, x0)
  public default <V> List<Attribute<?>> getAttributesForUndo(Attribute<V> attr, V newValue) {
    return null;
  }

  // getValue() returns null if attr was not found
  public <V> V getValue(Attribute<V> attr);

  public default <V> V getValueOrElse(Attribute<V> attr, V valueIfUnset) {
    V v = getValue(attr);
    return v == null ? valueIfUnset : v;
  }
  
  // Note: changeAttr() and setAttr() must not fail by putting up a dialog. Any
  // validation must be done before these are called. Worst case, these can
  // ignore the new value, or coerce it into soemthing valid. But they should
  // not cause the keyboard or mouse focus to change.
  public <V> void changeAttr(Attribute<V> attr, V value);

  // Normally, callers should use setAttr(), which ignores re-setting the same
  // value that is already set.
  public default <V> void setAttr(Attribute<V> attr, V value) {
    if (isReadOnly(attr)) {
      System.err.printf("attempt change readonly attribute %s from %s to %s\n", attr, getValue(attr), value);
      return;
    }
    V oldValue = getValue(attr);
    if (oldValue == null && value == null)
      return;
    if (oldValue != null && value != null && oldValue.equals(value))
      return;
    changeAttr(attr, value);
  }
  
  // public static boolean indistinguishable(AttributeSet a, AttributeSet b) {
  //   if (a == b)
  //     return true;
  //   if (a == null || b == null)
  //     return false;
  //   if (a.getClass() != b.getClass()) // TODO: verify if this can be violated by moving/replacing a component with itself
  //     return false;
  //   List<Attribute<?>> attrsA = a.getAttributes();
  //   List<Attribute<?>> attrsB = b.getAttributes();
  //   if (attrsA.size() != attrsB.size())
  //     return false;
  //   for (Attribute<?> attr : attrsA) {
  //     // There is a possibility that A could have a null value
  //     // explicitly set for some attribute that B doesn't have?
  //     // Seems unlikely. Let's ignore it.
  //     Object valA = a.getValue(attr);
  //     Object valB = b.getValue(attr);
  //     if (!Objects.equals(valA, valB))
  //       return false;
  //     // These seem excessive for purposes of CircuitState replacement
  //     // if (a.isReadOnly(attr) != b.isReadOnly(attr))
  //     //   return false;
  //     // if (a.isToSave(attr) != b.isToSave(attr))
  //     //   return false;
  //   }
  //   return true;
  // }

  public default String dump() {
    List<Attribute<?>> attrs = getAttributes();
    if (attrs.size() == 0)
      return getClass() + " with 0 attrs";
    StringBuilder out = new StringBuilder();
    out.append(getClass() + " with " + attrs.size() + " attrs:");
    for (Attribute<?> attr : attrs) {
      Object val = getValue(attr);
      out.append("\n  attr="+attr+" val="+val);
      if (isReadOnly(attr))
        out.append(" (readonly)");
      if (!isToSave(attr))
        out.append(" (no-save)");
    }
    return out.toString();
  }
}
