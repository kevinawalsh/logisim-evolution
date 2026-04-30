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

package com.cburch.logisim.gui.menu;
import static com.cburch.logisim.gui.menu.Strings.S;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import com.cburch.logisim.prefs.StateStore;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;

class OpenRecent extends JMenu {
  private class RecentItem extends JMenuItem implements ActionListener {
    private static final long serialVersionUID = 1L;
    private File file;

    RecentItem(File file) {
      super(getFileText(file));
      this.file = file;
      setEnabled(file != null);
      addActionListener(this);
    }

    public void actionPerformed(ActionEvent event) {
      Project proj = menubar.getSaveProject();
      Project baseProj = menubar.getBaseProject();
      Component parent  = baseProj != null ? baseProj.getFrame().getCanvas() : menubar.getParentFrame();
      Project newProj = ProjectActions.doOpen(parent, baseProj, file);
      // If the current project hasn't been touched and has no file associated
      // with it (i.e. is entirely blank), and the new file was opened
      // successfully, then go ahead and close the old blank window.
      // todo: and has no subwindows or dialogs open?
      if (newProj != null && proj != null
          && !proj.isFileDirty()
          && proj.getLogisimFile().getLoader().getMainFile() == null) {
        proj.getFrame().dispose();
      }
    }
  }

  private static String getFileText(File file) {
    if (file == null) {
      return S.get("fileOpenRecentNoChoices");
    } else {
      String ret;
      try {
        ret = file.getCanonicalPath();
      } catch (IOException e) {
        ret = file.toString();
      }
      if (ret.length() <= MAX_ITEM_LENGTH) {
        return ret;
      } else {
        ret = ret.substring(ret.length() - MAX_ITEM_LENGTH + 3);
        int splitLoc = ret.indexOf(File.separatorChar);
        if (splitLoc >= 0) {
          ret = ret.substring(splitLoc);
        }
        return "..." + ret;
      }
    }
  }

  private static final long serialVersionUID = 1L;

  private static final int MAX_ITEM_LENGTH = 50;
  private LogisimMenuBar menubar;

  private List<RecentItem> recentItems;

  OpenRecent(LogisimMenuBar menubar) {
    this.menubar = menubar;
    this.recentItems = new ArrayList<RecentItem>();
    StateStore.RECENT_PROJECTS.addPrefChangeWeakListener(this, e -> renewItems());
    renewItems();
  }

  void localeChanged() {
    setText(S.get("fileOpenRecentItem"));
    for (RecentItem item : recentItems) {
      if (item.file == null) {
        item.setText(S.get("fileOpenRecentNoChoices"));
      }
    }
  }

  private void renewItems() {
    for (int index = recentItems.size() - 1; index >= 0; index--) {
      RecentItem item = recentItems.get(index);
      remove(item);
    }
    recentItems.clear();

    List<File> files = StateStore.RECENT_PROJECTS.get();
    if (files.isEmpty()) {
      recentItems.add(new RecentItem(null));
    } else {
      for (File file : files) {
        recentItems.add(new RecentItem(file));
      }
    }

    for (RecentItem item : recentItems) {
      add(item);
    }
  }
}
