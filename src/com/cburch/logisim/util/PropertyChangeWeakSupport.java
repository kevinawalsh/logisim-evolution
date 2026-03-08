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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PropertyChangeWeakSupport {

  // Typical usage:
  // class Foo ... implements PropertyChangeWeakSupport.Producer {
  //   ...
  //   PropertyChangeWeakSupport propListeners = new PropertyChangeWeakSupport(this);
  //   public PropertyChangeWeakSupport getPropertyListeners() { return propListeners; }
  // }
  
  private static final String ALL_PROPERTIES = "ALL PROPERTIES";

  public interface Producer {

    public PropertyChangeWeakSupport getPropertyChangeListeners();

    default public void addPropertyChangeWeakListener(
        PropertyChangeListener listener) {
      addPropertyChangeWeakListener(ALL_PROPERTIES, listener);
    }

    default public void addPropertyChangeWeakListener(String property,
        PropertyChangeListener listener) {
      PropertyChangeWeakSupport s = getPropertyChangeListeners();
      s.listeners.add(new ListenerData(property, listener));
    }

    default public void firePropertyChange(String property,
        boolean oldValue, boolean newValue) {
      firePropertyChange(property, Boolean.valueOf(oldValue), Boolean.valueOf(newValue));
    }

    default public void firePropertyChange(String property,
        int oldValue, int newValue) {
      firePropertyChange(property, Integer.valueOf(oldValue), Integer.valueOf(newValue));
    }

    default public void firePropertyChange(String property,
        Object oldValue, Object newValue) {
      PropertyChangeWeakSupport s = getPropertyChangeListeners();
      PropertyChangeEvent e = null;
      for (Iterator<ListenerData> it = s.listeners.iterator(); it.hasNext();) {
        ListenerData data = it.next();
        PropertyChangeListener l = data.listener.get();
        if (l == null) {
          it.remove();
        } else if (data.property == ALL_PROPERTIES
            || data.property.equals(property)) {
          if (e == null) {
            e = new PropertyChangeEvent(s.source, property, oldValue,
                newValue);
          }
          l.propertyChange(e);
        }
      }
    }

    default public void removePropertyChangeListener(PropertyChangeListener listener) {
      removePropertyChangeListener(ALL_PROPERTIES, listener);
    }

    default public void removePropertyChangeListener(String property,
        PropertyChangeListener listener) {
      PropertyChangeWeakSupport s = getPropertyChangeListeners();
      for (Iterator<ListenerData> it = s.listeners.iterator(); it.hasNext();) {
        ListenerData data = it.next();
        PropertyChangeListener l = data.listener.get();
        if (l == null) {
          it.remove();
        } else if (data.property.equals(property) && l == listener) {
          it.remove();
        }
      }
    }

  }

  private static class ListenerData {
    String property;
    WeakReference<PropertyChangeListener> listener;
    ListenerData(String p, PropertyChangeListener l) {
      property = p;
      listener = new WeakReference<PropertyChangeListener>(l);
    }
  }

  private Object source;
  private ConcurrentLinkedQueue<ListenerData> listeners = new ConcurrentLinkedQueue<>();

  public PropertyChangeWeakSupport(Object src) { source = src; }

}
