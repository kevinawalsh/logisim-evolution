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
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

import com.cburch.logisim.util.WeakList;

/**
 * Recent-project list. Backed by StateStore; order is most-recent-first.
 * No timestamps: document order IS the order.
 */
public class RecentProjects {

  private final WeakList<AppPreferences.Listener<File>> listeners = new WeakList<>();
  public void addPrefChangeWeakListener(Object owner, AppPreferences.Listener<File> l) { listeners.add(owner, l); }
  public void removePrefChangeWeakListener(Object owner, AppPreferences.Listener<File> l) { listeners.remove(owner, l); }
  private void firePrefChangeEvent(AppPreferences.ChangeEvent<File> evt) { for (AppPreferences.Listener<File> l : listeners) l.prefChanged(evt); }

  RecentProjects() {
    // Nothing to initialize — StateStore already loaded its data from state.xml.
  }

  /** Returns the list of recent projects, most-recent-first. */
  public List<File> get() {
    List<String> paths = StateStore.getRecentProjects();
    List<File> files = new ArrayList<>(paths.size());
    for (String path : paths)
      files.add(new File(path));
    return files;
  }

  /**
   * Records that the given file was just opened/saved. Updates StateStore and
   * fires a change event so the UI menu refreshes.
   */
  public void update(File file) {
    List<File> oldList = get();
    StateStore.updateRecentProject(file);
    List<File> newList = get();

    // Fire with old=first-old, new=first-new so listeners know the list changed
    File oldFirst = oldList.isEmpty() ? null : oldList.get(0);
    File newFirst = newList.isEmpty() ? null : newList.get(0);
    SwingUtilities.invokeLater(() ->
        firePrefChangeEvent(new AppPreferences.ChangeEvent<File>(this, oldFirst, newFirst)));
  }
}
