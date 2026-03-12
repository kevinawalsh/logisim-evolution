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

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.instance.StdAttr;

public class AttributeSets {

  public static class ArrayBacked extends AbstractAttributeSet {
    private List<Attribute<?>> attrs;
    private Object[] values;
    private int readOnly = 0, dontSave = 0;

    public ArrayBacked(Attribute<?>[] attrs, Object[] initValues) {
      if (attrs.length != initValues.length) {
        throw new IllegalArgumentException(
            "attribute and value arrays must have same length");
      }
      if (attrs.length > 32) {
        throw new IllegalArgumentException(
            "cannot handle more than 32 attributes");
      }
      this.attrs = Arrays.asList(attrs);
      this.values = initValues.clone();
    }

    @Override
    protected void copyInto(AbstractAttributeSet destSet) {
      ArrayBacked dest = (ArrayBacked) destSet;
      dest.attrs = this.attrs;
      dest.values = this.values.clone();
      dest.readOnly = this.readOnly;
      dest.dontSave = this.dontSave;
    }

    @Override
    public List<Attribute<?>> getAttributes() {
      return attrs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getValue(Attribute<V> attr) {
      int index = attrs.indexOf(attr);
      return index < 0 ? null : (V)values[index];
    }

    @Override
    public boolean isReadOnly(Attribute<?> attr) {
      int index = attrs.indexOf(attr);
      return index < 0 || isReadOnly(index);
    }

    private boolean isReadOnly(int index) {
      return ((readOnly >> index) & 1) == 1;
    }

    @Override
    public void setReadOnly(Attribute<?> attr, boolean value) {
      int index = attrs.indexOf(attr);
      if (index < 0)
        throw new IllegalArgumentException("no such attribute: " + attr);
      if (value)
        readOnly |= (1 << index);
      else
        readOnly &= ~(1 << index);
    }

    @Override
    public boolean isToSave(Attribute<?> attr) {
      int index = attrs.indexOf(attr);
      return index < 0 || isToSave(index);
    }

    private boolean isToSave(int index) {
      return ((dontSave >> index) & 1) == 0;
    }

    @Override
    public void setToSave(Attribute<?> attr, boolean value) {
      int index = attrs.indexOf(attr);
      if (index < 0)
        throw new IllegalArgumentException("no such attribute: " + attr);
      if (!value)
        dontSave |= (1 << index);
      else
        dontSave &= ~(1 << index);
    }

    @Override
    public <V> void updateAttr(Attribute<V> attr, V value) {
      int index = attrs.indexOf(attr);
      if (index < 0)
        throw new IllegalArgumentException("no such attribute: " + attr);
      values[index] = value;
    }
  }

  public static AttributeSet fixedSet(Attribute<?>[] attrs, Object[] initValues) {
    if (attrs.length == 0)
      return EMPTY;
    else
      return new ArrayBacked(attrs, initValues);
  }


  public static void copy(AttributeSet src, AttributeSet dst) {
    if (src == null || src.getAttributes() == null)
      return;
    for (Attribute<?> attr : src.getAttributes()) {
      @SuppressWarnings("unchecked")
      Attribute<Object> attrObj = (Attribute<Object>) attr;
      Object value = src.getValue(attr);
      dst.setAttr(attrObj, value);
    }
  }

  public static <V> boolean isAttrLabel(Attribute<V> attr) {
    return (attr.equals(StdAttr.LABEL))
        || (attr.equals(CircuitAttributes.CIRCUIT_LABEL_ATTR))
        || (attr.equals(CircuitAttributes.NAME_ATTR));
  }

  public static final AttributeSet EMPTY
      = new ArrayBacked(new Attribute<?>[0], new Object[0]);

  private AttributeSets() { }
}
