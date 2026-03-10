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

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

import com.cburch.logisim.util.WeakList;

public class PrefMonitor<E> {

  protected String name;
  protected E value, dflt;
  protected E[] opts; // only enforced if not null
  protected Preferences backingStore;

  PrefMonitor(String name, E dflt) {
    this(name, null, dflt);
  }

  PrefMonitor(String name, E[] opts, E dflt) {
    this.name = name;
    this.dflt = dflt;
    this.value = dflt;
    this.opts = opts;
    this.backingStore = AppPreferences.getPrefs();
    backingStore.addPreferenceChangeListener(e -> backingStoreChanged(e));
    setFromBackingStore();
  }

  private final WeakList<AppPreferences.Listener<E>> listeners = new WeakList<>();
  public void addPrefChangeWeakListener(Object owner, AppPreferences.Listener<E> l) { listeners.add(owner, l); }
  public void removePrefChangeWeakListener(Object owner, AppPreferences.Listener<E> l) { listeners.remove(owner, l); }
  private void firePrefChangeEvent(AppPreferences.ChangeEvent<E> evt) { for (AppPreferences.Listener<E> l : listeners) l.prefChanged(evt); }

  public E get() {
    return value;
  }

  public void set(E newValue) {
    if (setAndFire(newValue))
      setIntoBackingStore();
  }

  private void backingStoreChanged(PreferenceChangeEvent event) {
    if (!event.getKey().equals(name))
      return;
    E newValue = convertFromString(event.getNewValue());
    setAndFire(newValue);
  }

  protected boolean setAndFire(E newValue) {
    if (identical(newValue, value))
      return false;
    E chosen = ensureWithinRange(newValue);
    if (identical(value, chosen))
      return false;
    E oldValue = value;
    value = chosen;
    SwingUtilities.invokeLater(() ->
        firePrefChangeEvent(new AppPreferences.ChangeEvent<E>(this, oldValue, chosen)));
    return true;
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

  private E ensureWithinRange(E v) {
    if (opts == null)
      return v;
    E chosen = dflt;
    for (E opt : opts) {
      if (identical(opt, v)) {
        return opt;
      }
    }
    return dflt;
  }

  private void setFromBackingStore() { // does not fire
    E newValue;
    if (dflt instanceof Double)
      newValue = (E)Double.valueOf(backingStore.getDouble(name, (Double)dflt));
    else if (dflt instanceof Integer)
      newValue = (E)Integer.valueOf(backingStore.getInt(name, (Integer)dflt));
    else if (dflt instanceof Boolean)
      newValue = (E)Boolean.valueOf(backingStore.getBoolean(name, (Boolean)dflt));
    else if (dflt instanceof String)
      newValue = (E)backingStore.get(name, (String)dflt);
    else
      throw new IllegalArgumentException("illegal preference type");
    if (identical(newValue, value))
      return;
    newValue = ensureWithinRange(newValue);
    if (identical(value, newValue))
      return;
    value = newValue;
  }

  private E convertFromString(String s) {
    if (s == null)
      return dflt;
    try {
      E newValue;
      if (dflt instanceof Double)
        newValue = (E)Double.valueOf(Double.parseDouble(s));
      else if (dflt instanceof Integer)
        newValue = (E)Integer.valueOf(Integer.parseInt(s));
      else if (dflt instanceof Boolean && s.equalsIgnoreCase("true"))
        newValue = (E)Boolean.TRUE;
      else if (dflt instanceof Boolean && s.equalsIgnoreCase("false"))
        newValue = (E)Boolean.FALSE;
      else if (dflt instanceof Boolean)
        return dflt;
      else if (dflt instanceof String)
        newValue = (E)s;
      else
        throw new IllegalArgumentException("illegal preference type");
      return ensureWithinRange(newValue);
    } catch (NumberFormatException e) {
      return dflt;
    }
  }

  protected static <E> boolean identical(E a, E b) {
    return (a == null && b == null)
        || (a != null && b != null && a.equals(b));
  }

}
