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

package com.cburch.logisim.file;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.EventScheduler;
import com.cburch.logisim.util.QNode;

public class ProjectsDirty {

  private static class AutoBackup extends QNode {

    WeakReference<Project> projRef;
    boolean cancelled, fired;

    AutoBackup(Project proj) {
      super(autoBackupTime());
      projRef = new WeakReference<>(proj);
    }

    static long autoBackupTime() {
      long minutes = AppPreferences.AUTO_BACKUP_FREQ.get();
      if (minutes < 1) minutes = 10;
      if (minutes > 120) minutes = 120;
      long now = System.currentTimeMillis();
      return now + minutes*60*1000;
    }

  }

  private static class AutoBackupScheduler extends EventScheduler<AutoBackup> {
    private Object lock = new Object();
    WeakHashMap<Project, AutoBackup> scheduled = new WeakHashMap<>();

    AutoBackupScheduler() { super("Auto-Backup"); }
  
    @Override
    public void fire(AutoBackup event) {
      Project proj = event.projRef.get();
      if (proj == null)
        return;
      synchronized(lock) {
        if (event.cancelled || event.fired)
          return;
        event.fired = true;
        scheduled.remove(proj);
      }
      ProjectActions.doAutoBackup(proj);
    }
    
    public void schedule(Project proj) {
      if (proj == null)
        return;
      AutoBackup event = new AutoBackup(proj);
      synchronized(lock) {
        AutoBackup pending = scheduled.get(proj);
        if (pending != null && !pending.cancelled
            && !pending.fired && pending.key <= event.key)
          return;
        if (pending != null)
          pending.cancelled = true;
        scheduled.put(proj, event); // replaces pending in hashmap
      }
      // System.out.println("auto-backup scheduled");
      super.schedule(event);
    }
  };
  
  private static AutoBackupScheduler autobackup = new AutoBackupScheduler();


  private static class DirtyListener implements LibraryListener {
    Project proj;

    DirtyListener(Project proj) {
      this.proj = proj;
    }

    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.DIRTY_STATE) {
        LogisimFile lib = proj.getLogisimFile();
        File file = lib.getLoader().getMainFile();
        LibraryManager.instance.setDirty(file, lib.isDirty());
      }
    }
  }

  public static void needsBackup(Project proj) {
    if (AppPreferences.AUTO_BACKUP.get())
      autobackup.schedule(proj);
  }

  private static Object listLock = new Object();
  private static void projectsListChanged() {
    synchronized (listLock) {
      for (DirtyListener l : listeners) {
        l.proj.removeLibraryWeakListener(null, l);
      }
      listeners.clear();
      for (Project proj : Projects.getOpenProjects()) {
        DirtyListener l = new DirtyListener(proj);
        proj.addLibraryWeakListener(null, l);
        listeners.add(l);

        LogisimFile lib = proj.getLogisimFile();
        LibraryManager.instance.setDirty(lib.getLoader().getMainFile(),
            lib.isDirty());
      }
    }
  }

  private static final Object PERMANENT = new Object();
  public static void initialize() {
    Projects.addListChangeWeakListener(PERMANENT, () ->  projectsListChanged());
    autobackup.start();
  }

  private static ArrayList<DirtyListener> listeners = new ArrayList<DirtyListener>();

  private ProjectsDirty() {
  }
}
