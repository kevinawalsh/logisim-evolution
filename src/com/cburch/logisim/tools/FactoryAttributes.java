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

package com.cburch.logisim.tools;

import java.util.List;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.util.WeakList;

public class FactoryAttributes implements AttributeSet, AttributeListener, Cloneable {
  private Class<? extends Library> libraryClass;
  private FactoryDescription desc;
  private ComponentFactory factory;
  private AttributeSet baseAttrs;
  private WeakList<AttributeListener> listeners = new WeakList<>();

  public FactoryAttributes(Class<? extends Library> libClass,
      FactoryDescription desc) {
    this.libraryClass = libClass;
    this.desc = desc;
    this.factory = null;
    this.baseAttrs = null;
  }

  public FactoryAttributes(ComponentFactory factory) {
    this.libraryClass = null; // not needed b/c factory already loaded
    this.desc = null;
    this.factory = factory;
    this.baseAttrs = null;
  }

  public FactoryAttributes(FactoryAttributes other) {
    this.libraryClass = other.libraryClass;
    this.desc = other.desc;
    this.factory = other.factory;
    this.baseAttrs = null;
  }

  public void addAttributeWeakListener(Object owner, AttributeListener l) {
    listeners.add(owner, l);
  }

  public void attributeListChanged(AttributeEvent baseEvent) {
    AttributeEvent e = null;
    for (AttributeListener l : listeners) {
      if (e == null) {
        e = new AttributeEvent(this, baseEvent.getAttribute(),
            baseEvent.getValue());
      }
      l.attributeListChanged(e);
    }
  }

  public void attributeValueChanged(AttributeEvent baseEvent) {
    AttributeEvent e = null;
    for (AttributeListener l : listeners) {
      if (e == null) {
        e = new AttributeEvent(this, baseEvent.getAttribute(),
            baseEvent.getValue());
      }
      l.attributeValueChanged(e);
    }
  }

  @Override
  public AttributeSet clone() {
    return (AttributeSet) getBase().clone();
  }

  public boolean containsAttribute(Attribute<?> attr) {
    return getBase().containsAttribute(attr);
  }

  public Attribute<?> getAttribute(String name) {
    return getBase().getAttribute(name);
  }

  public List<Attribute<?>> getAttributes() {
    return getBase().getAttributes();
  }

  public ComponentFactory getFactory() {
    return factory != null ? factory : desc.getFactoryFromLibrary(libraryClass);
  }

  public AttributeSet getBase() {
    AttributeSet ret = baseAttrs;
    if (ret == null) {
      ComponentFactory fact = factory;
      if (fact == null) {
        fact = desc.getFactoryFromLibrary(libraryClass);
        factory = fact;
      }
      if (fact == null) {
        ret = AttributeSets.EMPTY;
      } else {
        ret = fact.createAttributeSet();
        ret.addAttributeWeakListener(null, this);
      }
      baseAttrs = ret;
    }
    return ret;
  }

  public <V> V getValue(Attribute<V> attr) {
    return getBase().getValue(attr);
  }

  boolean isFactoryInstantiated() {
    return baseAttrs != null;
  }

  public boolean isReadOnly(Attribute<?> attr) {
    return getBase().isReadOnly(attr);
  }

  public boolean isToSave(Attribute<?> attr) {
    return getBase().isToSave(attr);
  }

  public void removeAttributeWeakListener(Object owner, AttributeListener l) {
    listeners.remove(owner, l);
  }

  public void setReadOnly(Attribute<?> attr, boolean value) {
    getBase().setReadOnly(attr, value);
  }

  public <V> void setAttr(Attribute<V> attr, V value) {
    getBase().setAttr(attr, value);
  }

  public <V> void changeAttr(Attribute<V> attr, V value) {
    throw new UnsupportedOperationException("FactoryAttributes.changeAttr");
  }
}
