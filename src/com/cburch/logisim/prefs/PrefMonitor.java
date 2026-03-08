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

package com.cburch.logisim.prefs;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.Preferences;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

public class PrefMonitor<E> implements PreferenceChangeListener {

  protected String name;
  protected E value, oldValue, dflt;
  protected E[] opts;
  protected Preferences backingStore;

  PrefMonitor(String name, E dflt) {
    this(name, null, dflt);
  }

  PrefMonitor(String name, E[] opts, E dflt) {
    this.name = name;
    this.dflt = dflt;
    this.value = dflt;
    this.oldValue = dflt;
    this.opts = opts;
    this.backingStore = AppPreferences.getPrefs();
    backingStore.addPreferenceChangeListener(this);

    set(getFromBackingStore()); // if needed, this updates value (and fires event)
  }

  public void addPropertyChangeWeakListener(PropertyChangeListener listener) {
    AppPreferences.propertyChangeProducer.addPropertyChangeWeakListener(name, listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    AppPreferences.propertyChangeProducer.removePropertyChangeListener(name, listener);
  }

  public boolean isSource(PropertyChangeEvent event) {
    return name.equals(event.getPropertyName());
  }

  public void preferenceChange(PreferenceChangeEvent event) {
    if (!event.getKey().equals(name))
      return;
    if (identical(oldValue, value))
      return;
    E prev = oldValue;
    oldValue = value;
    AppPreferences.propertyChangeProducer.firePropertyChange(name, prev, value);
  }

  public String getIdentifier() {
    return name;
  }

  public E get() {
    return value;
  }

  public void set(E newValue) {
    if (identical(value, newValue))
      return;
    if (opts != null) {
      E chosen = dflt;
      for (E opt : opts) {
        if (identical(opt, newValue)) {
          chosen = opt;
          break;
        }
      }
      newValue = chosen;
    }
    value = newValue;
    setIntoBackingStore();
  }

  private void setIntoBackingStore() {
    if (dflt instanceof Double)
      backingStore.putDouble(name, (Double)value);
    else if (dflt instanceof Integer)
      backingStore.putInt(name, (Integer)value);
    else if (dflt instanceof Boolean)
      backingStore.putBoolean(name, (Boolean)value);
    else if (dflt instanceof String)
      backingStore.put(name, (String)value);
  }

  private E getFromBackingStore() {
    if (dflt instanceof Double)
      return (E)Double.valueOf(backingStore.getDouble(name, (Double)dflt));
    else if (dflt instanceof Integer)
      return (E)Integer.valueOf(backingStore.getInt(name, (Integer)dflt));
    else if (dflt instanceof Boolean)
      return (E)Boolean.valueOf(backingStore.getBoolean(name, (Boolean)dflt));
    else if (dflt instanceof String)
      return (E)backingStore.get(name, (String)dflt);
    else
      throw new IllegalArgumentException("illegal preference type");
  }

  protected static <E> boolean identical(E a, E b) {
    return (a == null && b == null)
        || (a != null && b != null && a.equals(b));
  }

}
