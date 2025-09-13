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

package com.cburch.logisim.gui.generic;
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.circuit.SplitterAttributes;

public abstract class AttributeSetTableModel
  implements AttrTableModel, AttributeListener {

  public <V> Component getCellEditor(Attribute<V> attr, Window parent, V value) {
    return attr.getCellEditor(parent, value);
  }

  private class AttrRow<V> implements AttrTableModelRow {
    private Attribute<V> attr;

    AttrRow(Attribute<V> attr) {
      this.attr = attr;
    }

    public String toString() { 
      return "AttrRow(" + attr + ")";
    }

    public Attribute<?> getAttribute() {
      return attr;
    }

    public Component getEditor(Window parent) {
      V value = attrs.getValue(attr);
      return getCellEditor(attr, parent, value);
    }

    public String getLabel() {
      return attr.getDisplayName();
    }

    public Object getValue() {
      return attrs.getValue(attr);
    }

    public Object getDisplayObject() {
      V value = attrs.getValue(attr);
      if (value == null) {
        try {
          return attr.toDisplayObject(value);
        } catch (NullPointerException e) {
          return "";
        }
      } else {
        try {
          return attr.toDisplayObject(value);
        } catch (Exception e) {
          return "???";
        }
      }
    }

    public String getDisplayString() {
      V value = attrs.getValue(attr);
      if (value == null) {
        try {
          return attr.toDisplayString(value);
        } catch (NullPointerException e) {
          return "";
        }
      } else {
        try {
          return attr.toDisplayString(value);
        } catch (Exception e) {
          return "???";
        }
      }
    }

    public boolean multiEditCompatible(AttrTableModelRow other) {
      if (other == null || !(other instanceof AttrRow<?>))
        return false;
      AttrRow<?> o = (AttrRow<?>)other;
      if (!(((Object)attr) instanceof SplitterAttributes.BitOutAttribute))
        return false;
      if (!(((Object)o.attr) instanceof SplitterAttributes.BitOutAttribute))
        return false;
      SplitterAttributes.BitOutAttribute a = (SplitterAttributes.BitOutAttribute)(Object)attr;
      SplitterAttributes.BitOutAttribute b = (SplitterAttributes.BitOutAttribute)(Object)o.attr;
      return a.sameOptions(b);
    }

    public void setValue(Window parent, Object valueOrStr) throws AttrTableSetException {
      // Attribute<Object> attr = this.attr;
      if (attr == null || valueOrStr == null)
        return;

      try {
        V value;
        if (valueOrStr instanceof String)
          value = attr.parseFromUser(parent, (String) valueOrStr);
        else
          value = (V)valueOrStr;
        setValueRequested(attr, value);
      } catch (ClassCastException e) {
        String msg = S.get("attributeChangeInvalidError") + ": " + e;
        throw new AttrTableSetException(msg);
      } catch (NumberFormatException e) {
        String msg = S.get("attributeChangeInvalidError");
        String emsg = e.getMessage();
        if (emsg != null && emsg.length() > 0)
          msg += ": " + emsg;
        msg += ".";
        throw new AttrTableSetException(msg);
      }
    }
  }

  private ArrayList<AttrTableModelListener> listeners = new ArrayList<>();
  private AttributeSet attrs;
  private HashMap<Attribute<?>, AttrRow<?>> rowMap = new HashMap<>();
  private ArrayList<AttrRow<?>> rows = new ArrayList<>();

  public AttributeSetTableModel(AttributeSet attrs) {
    this.attrs = attrs;
    if (attrs != null) {
      for (Attribute<?> attr : attrs.getAttributes()) {
        AttrRow<?> row = new AttrRow(attr);
        rowMap.put(attr, row);
        rows.add(row);
      }
      attrs.addAttributeWeakListener(null, this);
    }
  }

  public void addAttrTableModelListener(AttrTableModelListener listener) {
    listeners.add(listener);
  }

  public void attributeListChanged(AttributeEvent e) {
    // if nothing has changed, don't do anything
    int index = 0;
    boolean match = true;
    int rowsSize = rows.size();
    for (Attribute<?> attr : attrs.getAttributes()) {
      if (index >= rowsSize || rows.get(index).attr != attr) {
        match = false;
        break;
      }
      index++;
    }
    if (match && index == rows.size())
      return;

    // compute the new list of rows, possible adding into hash map
    ArrayList<AttrRow<?>> newRows = new ArrayList<AttrRow<?>>();
    HashSet<Attribute<?>> missing = new HashSet<Attribute<?>>(rowMap.keySet());
    for (Attribute<?> attr : attrs.getAttributes()) {
      AttrRow<?> row = rowMap.get(attr);
      if (row == null) {
        row = new AttrRow(attr);
        rowMap.put(attr, row);
      } else {
        missing.remove(attr);
      }
      newRows.add(row);
    }
    rows = newRows;
    for (Attribute<?> attr : missing) {
      rowMap.remove(attr);
    }
    fireStructureChanged();
  }

  public void attributeValueChanged(AttributeEvent e) {
    Attribute<?> attr = e.getAttribute();
    AttrTableModelRow row = rowMap.get(attr);
    if (row != null) {
      int index = rows.indexOf(row);
      if (index >= 0) {
        fireValueChanged(index);
      }
    }
  }

  protected void fireStructureChanged() {
    AttrTableModelEvent event = new AttrTableModelEvent(this);
    for (AttrTableModelListener l : listeners)
      l.attrStructureChanged(event);
  }

  protected void fireTitleChanged() {
    AttrTableModelEvent event = new AttrTableModelEvent(this);
    for (AttrTableModelListener l : listeners)
      l.attrTitleChanged(event);
  }

  protected void fireValueChanged(int index) {
    AttrTableModelEvent event = new AttrTableModelEvent(this, index);
    for (AttrTableModelListener l : listeners)
      l.attrValueChanged(event);
  }

  public AttributeSet getAttributeSet() {
    return attrs;
  }

  public AttrTableModelRow getRow(int rowIndex) {
    return rows.get(rowIndex);
  }

  public int getRowCount() {
    return rows.size();
  }

  public abstract String getTitle();

  public void removeAttrTableModelListener(AttrTableModelListener listener) {
    listeners.remove(listener);
  }

  public void setAttributeSet(AttributeSet value) {
    if (attrs != value) {
      attrs.removeAttributeWeakListener(null, this);
      attrs = value;
      attrs.addAttributeWeakListener(null, this);
      attributeListChanged(null);
    }
  }

  public boolean isRowValueEditable(int rowIndex) {
    return !attrs.isReadOnly(rows.get(rowIndex).attr);
  }

  protected abstract <V> void setValueRequested(Attribute<V> attr,
      V value) throws AttrTableSetException;

}
