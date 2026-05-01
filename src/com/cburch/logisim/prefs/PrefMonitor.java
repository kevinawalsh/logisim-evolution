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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;

import com.cburch.logisim.util.WeakList;

public class PrefMonitor<E> {

  protected final String section;
  protected final String name;
  protected E value, dflt;
  protected E[] opts; // only enforced if not null

  PrefMonitor(String section, String name, E dflt) {
    this(section, name, null, dflt);
  }

  PrefMonitor(String section, String name, E[] opts, E dflt) {
    this.section = section;
    this.name = name;
    this.dflt = dflt;
    this.value = dflt;
    this.opts = opts;

    // Register this key so it appears in settings.xml (even when unset)
    if (opts == null || opts.length == 0) {
      SettingsStore.registerKey(section, name, convertToString(dflt), null);
    } else {
      String options[] = new String[opts.length];
      for (int i = 0; i < opts.length; i++)
        options[i] = convertToString(opts[i]);
      SettingsStore.registerKey(section, name, convertToString(dflt), options);
    }

    // React to changes pushed by SettingsStore (e.g. from --config reload or clear())
    SettingsStore.addChangeListener(section, name, () -> {
      E newVal = convertFromString(SettingsStore.getEffective(section, name));
      setAndFire(newVal);
    });

    // Load initial value from SettingsStore
    setFromStore();
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
      SettingsStore.put(section, name, convertToString(newValue));
  }

  /** Returns true if the user has explicitly set this preference (has value= in settings.xml). */
  public boolean isUserSet() {
    return SettingsStore.isUserSet(section, name);
  }

  /** Clears the user-set value; effective value reverts to the default. */
  public void unset() {
    SettingsStore.unset(section, name);
    // Change listener will call setAndFire() with the new effective (default) value
  }

  private void setFromStore() { // does not fire
    E newValue = convertFromString(SettingsStore.getEffective(section, name));
    newValue = ensureWithinRange(newValue);
    value = (newValue != null) ? newValue : dflt;
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

  public List<E> getEnumeratedOptions() {
    return opts == null ? null : Collections.unmodifiableList(Arrays.asList(opts));
  }

  private E ensureWithinRange(E v) {
    if (opts == null)
      return v;
    for (E opt : opts) {
      if (identical(opt, v))
        return opt;
    }
    return dflt;
  }

  private String convertToString(E v) {
    if (v == null) return null;
    return v.toString();
  }

  @SuppressWarnings("unchecked")
  private E convertFromString(String s) {
    if (s == null)
      return dflt;
    try {
      if (dflt instanceof Double)
        return (E) Double.valueOf(Double.parseDouble(s));
      else if (dflt instanceof Integer)
        return (E) Integer.valueOf(Integer.parseInt(s));
      else if (dflt instanceof Boolean && s.equalsIgnoreCase("true"))
        return (E) Boolean.TRUE;
      else if (dflt instanceof Boolean && s.equalsIgnoreCase("false"))
        return (E) Boolean.FALSE;
      else if (dflt instanceof Boolean)
        return dflt;
      else if (dflt instanceof String)
        return (E) s;
      else
        throw new IllegalArgumentException("illegal preference type: " + dflt.getClass());
    } catch (NumberFormatException e) {
      return dflt;
    }
  }

  protected static <E> boolean identical(E a, E b) {
    return (a == null && b == null)
        || (a != null && b != null && a.equals(b));
  }
}
