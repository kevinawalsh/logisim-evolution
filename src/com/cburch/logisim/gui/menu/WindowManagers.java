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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JFrame;

import com.cburch.logisim.analyze.gui.AnalyzerManager;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.find.FindManager;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.WindowMenuItemManager;

public class WindowManagers {
  private static class MyListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent event) {
      computeListeners();
    }
  }

  private static class ProjectManager extends WindowMenuItemManager
    implements ProjectListener, LibraryListener {
    private Project proj;

    ProjectManager(Project proj) {
      super(proj.getLogisimFile().getName(), false);
      this.proj = proj;
      proj.addProjectWeakListener(null, this);
      proj.addLibraryWeakListener(/*null,*/ this);
      frameOpened(proj.getFrame());
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return proj.getFrame();
    }

    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.SET_NAME) {
        setText((String) event.getData());
      }
    }

    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_FILE) {
        setText(proj.getLogisimFile().getName());
      }
    }
  }

  private static void computeListeners() {
    List<Project> nowOpen = Projects.getOpenProjects();

    HashSet<Project> closed = new HashSet<Project>(projectMap.keySet());
    closed.removeAll(nowOpen);
    for (Project proj : closed) {
      ProjectManager manager = projectMap.get(proj);
      manager.frameClosed(manager.getJFrame(false, null));
      projectMap.remove(proj);
    }

    HashSet<Project> opened = new LinkedHashSet<Project>(nowOpen);
    opened.removeAll(projectMap.keySet());
    for (Project proj : opened) {
      ProjectManager manager = new ProjectManager(proj);
      projectMap.put(proj, manager);
    }
  }

  public static void initialize() {
    if (!initialized) {
      initialized = true;
      AnalyzerManager.initialize();
      FindManager.initialize();
      SettingsFrame.initializeManager();
      Projects.propertyChangeProducer.addPropertyChangeListener(Projects.projectListProperty, myListener);
      computeListeners();
    }
  }

  private static boolean initialized = false;

  private static MyListener myListener = new MyListener();

  private static HashMap<Project, ProjectManager> projectMap = new LinkedHashMap<Project, ProjectManager>();

  private WindowManagers() {
  }
}
