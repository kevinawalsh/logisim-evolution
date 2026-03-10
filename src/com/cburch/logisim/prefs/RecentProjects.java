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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

import com.cburch.logisim.util.WeakList;

public class RecentProjects {

  private static final String RECENT_KEY_PREFIX = "recent"; // recent0, recent1, ...
  private static final int NUM_RECENT = 10;

  private File[] recentFiles;
  private long[] recentTimes;
  private Preferences backingStore;

  RecentProjects() {
    recentFiles = new File[NUM_RECENT];
    recentTimes = new long[NUM_RECENT];
    Arrays.fill(recentTimes, System.currentTimeMillis());

    backingStore = AppPreferences.getPrefs();
    backingStore.addPreferenceChangeListener(e -> backingStoreChanged(e.getKey()));

    for (int index = 0; index < NUM_RECENT; index++)
      setFromBackingStore(index);
  }

  private final WeakList<AppPreferences.Listener<File>> listeners = new WeakList<>();
  public void addPrefChangeWeakListener(Object owner, AppPreferences.Listener<File> l) { listeners.add(owner, l); }
  public void removePrefChangeWeakListener(Object owner, AppPreferences.Listener<File> l) { listeners.remove(owner, l); }
  private void firePrefChangeEvent(AppPreferences.ChangeEvent<File> evt) { for (AppPreferences.Listener<File> l : listeners) l.prefChanged(evt); }

  public List<File> get() {
    long now = System.currentTimeMillis();
    long[] ages = new long[NUM_RECENT];
    long[] toSort = new long[NUM_RECENT];
    for (int i = 0; i < NUM_RECENT; i++) {
      if (recentFiles[i] == null) {
        ages[i] = -1;
      } else {
        ages[i] = now - recentTimes[i];
      }
      toSort[i] = ages[i];
    }
    Arrays.sort(toSort);

    List<File> ret = new ArrayList<File>();
    for (long age : toSort) {
      if (age >= 0) {
        int index = -1;
        for (int i = 0; i < NUM_RECENT; i++) {
          if (ages[i] == age) {
            index = i;
            ages[i] = -1;
            break;
          }
        }
        if (index >= 0) {
          ret.add(recentFiles[index]);
        }
      }
    }
    return ret;
  }

  public void update(File file) {
    File fileToSave = file;
    try { fileToSave = file.getCanonicalFile(); }
    catch (IOException e) { }
    long now = System.currentTimeMillis();
    int index = getReplacementIndex(now, fileToSave);
    if (setAndFire(index, now, fileToSave))
      setIntoBackingStore(index);
  }

  public void backingStoreChanged(String key) {
    if (!key.startsWith(RECENT_KEY_PREFIX))
      return;
    int index = -1;
    try { index = Integer.parseInt(key.substring(RECENT_KEY_PREFIX.length())); }
    catch (NumberFormatException e) { }
    if (index < 0 || index >= NUM_RECENT)
      return;
    File oldFile = recentFiles[index];
    long oldTime = recentTimes[index];
    setFromBackingStore(index);
    File newFile = recentFiles[index];
    long newTime = recentTimes[index];
    if (!isSame(oldFile, newFile) || oldTime != newTime)
      SwingUtilities.invokeLater(() ->
          firePrefChangeEvent(new AppPreferences.ChangeEvent<File>(this, oldFile, newFile)));
  }

  private boolean setAndFire(int index, long newTime, File newFile) {
    File oldFile = recentFiles[index];
    long oldTime = recentTimes[index];
    if (!isSame(oldFile, newFile) || oldTime != newTime) {
      recentFiles[index] = newFile;
      recentTimes[index] = newTime;
      SwingUtilities.invokeLater(() ->
          firePrefChangeEvent(new AppPreferences.ChangeEvent<File>(this, oldFile, newFile)));
      return true;
    }
    return false;
  }

  private void setIntoBackingStore(int index) {
    try {
      File file = recentFiles[index];
      long time = recentTimes[index];
      String encoding = "" + time + ";" + file.getCanonicalPath();
      backingStore.put(RECENT_KEY_PREFIX + index, encoding);
    } catch (IOException e) {
    }
  }

  private void setFromBackingStore(int index) { // does not fire
    String encoding = backingStore.get(RECENT_KEY_PREFIX + index, null);
    if (encoding == null)
      return;
    int semi = encoding.indexOf(';');
    if (semi < 0)
      return;
    try {
      long time = Long.parseLong(encoding.substring(0, semi));
      File file = new File(encoding.substring(semi + 1));
      recentTimes[index] = time;
      recentFiles[index] = file;
    } catch (NumberFormatException e) {
      return;
    }
  }

  private int getReplacementIndex(long now, File f) {
    long oldestAge = -1;
    int oldestIndex = 0;
    int nullIndex = -1;
    for (int i = 0; i < NUM_RECENT; i++) {
      if (f.equals(recentFiles[i])) {
        return i;
      }
      if (recentFiles[i] == null) {
        nullIndex = i;
      }
      long age = now - recentTimes[i];
      if (age > oldestAge) {
        oldestIndex = i;
        oldestAge = age;
      }
    }
    if (nullIndex != -1) {
      return nullIndex;
    } else {
      return oldestIndex;
    }
  }

  private static boolean isSame(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }

}
