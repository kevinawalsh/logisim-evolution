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

package com.cburch.draw.model;

import com.cburch.draw.shapes.DrawAttr;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.util.WeakList;

public abstract class AbstractDrawingAttributeSet implements Cloneable, AttributeSet {

  private WeakList<AttributeListener> listeners = new WeakList<>();

	public AbstractDrawingAttributeSet() { }

	public void addAttributeWeakListener(Object owner, AttributeListener l) { listeners.add(owner, l); }
	public void removeAttributeWeakListener(Object owner, AttributeListener l) { listeners.remove(owner, l); }

	@Override
	public Object clone() {
		try {
			AbstractDrawingAttributeSet ret;
      ret = (AbstractDrawingAttributeSet) super.clone();
			ret.listeners = new WeakList<>();
			return ret;
		} catch (CloneNotSupportedException e) {
			throw new UnsupportedOperationException("AbstractDrawingAttributeSet.clone");
		}
	}

	protected void fireAttributeListChanged() {
		AttributeEvent e = new AttributeEvent(this);
		for (AttributeListener listener : listeners)
			listener.attributeListChanged(e);
	}

  protected <V> void fireAttributeValueChanged(Attribute<? super V> attr, V value) {
    AttributeEvent e = new AttributeEvent(this, attr, value);
    for (AttributeListener listener : listeners)
      listener.attributeValueChanged(e);
  }

  public <V> void changeAttr(Attribute<V> attr, V value) {
    updateAttr(attr, value);
    fireAttributeValueChanged(attr, value);
    if (attr == DrawAttr.PAINT_TYPE)
			fireAttributeListChanged();
  }

	protected abstract <V> void updateAttr(Attribute<V> attr, V value);
}
