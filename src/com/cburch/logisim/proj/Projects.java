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
package com.cburch.logisim.proj;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.util.DesktopIntegration;
import com.cburch.logisim.util.PropertyChangeWeakSupport;

public class Projects {

  private static class MyListener extends WindowAdapter {

    @Override
    public void windowActivated(WindowEvent event) {
      mostRecentFrame = (Frame) event.getSource();
      moveToFront(mostRecentFrame);
    }

    @Override
    public void windowDeiconified(WindowEvent event) {
      mostRecentFrame = (Frame) event.getSource();
      moveToFront(mostRecentFrame);
    }

    @Override
    public void windowIconified(WindowEvent event) {
      moveToBack((Frame)event.getSource());
    }

    private int findProject(Frame frame) {
      for (int i = 0; i < openProjects.size(); i++) {
        Project proj = openProjects.get(i);
        if (proj.getFrame() == frame)
          return i;
      }
      return -1;
    }

    private void moveToFront(Frame frame) {
      int i = findProject(frame);
      if (i < 0)
        return;
      Project proj = openProjects.remove(i);
      openProjects.add(0, proj);
    }

    private void moveToBack(Frame frame) {
      int i = findProject(frame);
      if (i < 0)
        return;
      Project proj = openProjects.remove(i);
      openProjects.add(proj);
    }

    @Override
    public void windowClosed(WindowEvent event) {
      Frame frame = (Frame) event.getSource();
      Project proj = frame.getProject();

      if (frame == proj.getFrame()) {
        projectRemoved(proj, frame, this);
      }
      if (openProjects.isEmpty()) {
        if (!DesktopIntegration.HasWindowlessMenubar) {
          ProjectActions.doQuit();
        } else {
          Frame top = getTopFrame();
          if (top != null)
            top.savePreferences();
          DesktopIntegration.setSuddenTerminationAllowed(true);
        }
      }
    }

    @Override
    public void windowClosing(WindowEvent event) {
      Frame frame = (Frame) event.getSource();
      frameClosing(frame);
    }

    @Override
    public void windowOpened(WindowEvent event) {
      Frame frame = (Frame) event.getSource();
      Project proj = frame.getProject();

      if (frame == proj.getFrame() && !openProjects.contains(proj)) {
        openingLocations.remove(frame.getLocation());
        openProjects.add(proj);
        propertyChangeProducer.firePropertyChange(projectListProperty, null, null);
        if (proj.isFileDirty())
          DesktopIntegration.setSuddenTerminationAllowed(false);
      }
    }
  }

  public static Project findProjectFor(File query) {
    for (Project proj : openProjects) {
      Loader loader = proj.getLogisimFile().getLoader();
      if (loader == null) {
        continue;
      }
      File f = loader.getMainFile();
      if (query.equals(f)) {
        return proj;
      }
    }
    return null;
  }

  public static Point getCenteredLoc(int width, int height) {
    int x, y;
    x = getTopFrame().getX() + getTopFrame().getWidth() / 2;
    x -= width / 2;
    y = getTopFrame().getY() + getTopFrame().getHeight() / 2;
    y -= height / 2;
    return new Point(x, y);
  }

  public static Point getLocation(Window win) {
    Point ret = frameLocations.get(win);
    return ret == null ? null : (Point) ret.clone();
  }

  public static List<Project> getOpenProjects() {
    return Collections.unmodifiableList(openProjects);
  }

  public static Frame getTopFrame() {
    Frame ret = mostRecentFrame;
    if (ret == null) {
      Frame backup = null;
      for (Project proj : openProjects) {
        Frame frame = proj.getFrame();
        if (ret == null) {
          ret = frame;
        }
        if (ret.isVisible()
            && (ret.getExtendedState() & Frame.ICONIFIED) != 0) {
          backup = ret;
        }
      }
      if (ret == null) {
        ret = backup;
      }
    }
    return ret;
  }

  private static void projectRemoved(Project proj, Frame frame, MyListener listener) {
    frame.removeWindowListener(listener);
    openProjects.remove(proj);
    proj.getSimulator().shutDown();
    propertyChangeProducer.firePropertyChange(projectListProperty, null, null);
    projectCleaned();
  }

  public static void projectDirtied() {
    DesktopIntegration.setSuddenTerminationAllowed(false);
  }

  public static void projectCleaned() {
    for (Project p : openProjects) {
      if (p.isFileDirty()) {
        DesktopIntegration.setSuddenTerminationAllowed(false);
        return;
      }
    }
    Frame top = getTopFrame();
    if (top != null)
      top.savePreferences();
    DesktopIntegration.setSuddenTerminationAllowed(true);
  }

  static void windowCreated(Project proj, Frame oldFrame, Frame frame) {
    if (oldFrame != null)
      projectRemoved(proj, oldFrame, myListener);

    if (frame == null)
      return;

    // Cascade project windows. Unfortunately, frame.setLocationByPlatform(true)
    // does not work reliably on all platforms. It might also cascade
    // non-project windows (like settings, analyzer box, etc.), while we only
    // want to cascade the projects.
    //
    // Strategy: find project window nearest the top-right corner (argmax x-y),
    // with ties broken in favor of the window right-most one (argmax x).
    // Put the new window 20 pixels offset down-right from that, so long as it
    // stays away from edge of screen. If too close to edge, put it at top, just
    // to the right of that diagonal (x-y=const) line, so long as it stays away
    // from the edge. Failing that, randomize location.

    Point p = null;
    for (Point loc : openingLocations) {
      if (p == null || (loc.x - loc.y) > (p.x - p.y))
        p = loc;
    }
    for (Project p2 : openProjects) {
      Frame f = p2.getFrame();
      if (f == null)
        continue;
      Point loc = p2.getFrame().getLocation();
      if (p == null || (loc.x - loc.y) > (p.x - p.y))
        p = loc;
    }

    if (p == null) {
      p = frame.getLocation();
    } else {
      p.x += 20;
      p.y += 20;
      Dimension w = frame.getSize();
      w.width += 60;
      w.height += 60;
      Dimension screen = frame.getToolkit().getScreenSize();
      if (p.x >= screen.width - w.width ||
          p.y >= screen.height - w.height) {
        p = new Point(p.x - p.y + 60 + 40, 60);
        p.x = Math.max(p.x, 0);
        p.y = Math.max(p.y, 0);
        if (p.x >= screen.width - w.width ||
            p.y >= screen.height - w.height) {
          Random r = new Random();
          if (screen.width - w.width - 60 <= 0)
            p.x = 0;
          else
            p.x = r.nextInt(screen.width - w.width - 60) + 60;
          if (screen.height - w.height - 60 <= 0)
            p.y = 0;
          else
            p.y = r.nextInt(screen.height - w.height - 60) + 60;
        }
      }
      frame.setLocation(p);
    }

    if (frame.isVisible() && !openProjects.contains(proj)) {
      openProjects.add(proj);
      propertyChangeProducer.firePropertyChange(projectListProperty, null, null);
      if (proj.isFileDirty())
        DesktopIntegration.setSuddenTerminationAllowed(false);
    } else {
      openingLocations.add(p);
    }
    frame.addWindowListener(myListener);
  }

  public static boolean windowNamed(String name) {
    for (Project proj : openProjects) {
      if (proj.getLogisimFile().getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  public static final String projectListProperty = "projectList";

  private static final WeakHashMap<Window, Point> frameLocations = new WeakHashMap<Window, Point>();

  private static final MyListener myListener = new MyListener();

  // openProjects is maintained in order of most recent activation order:
  //  - activate brings to front
  //  - minimize sends to back
  private static ArrayList<Project> openProjects = new ArrayList<>();
  // openingLocations contains locations of project windows that are about to be
  // opened, but are not yet in openProjects list. This is needed because window
  // cascade position can sometimes need to be calculated for several windows
  // opening at once (e.g. from command line) before any of them appear on
  // screen.
  private static ArrayList<Point> openingLocations = new ArrayList<>();

  private static Frame mostRecentFrame = null;

  private Projects() {
  }

  public static void frameClosing(Frame frame) {
    if ((frame.getExtendedState() & Frame.ICONIFIED) == 0) {
      mostRecentFrame = frame;
      try {
        Point pt = frame.getLocationOnScreen();
        if (pt != null)
          frameLocations.put(frame, pt);
      } catch (Exception t) {
      }
    }
  }

  public static final PropertyChangeWeakSupport.Producer propertyChangeProducer =
      new PropertyChangeWeakSupport.Producer() {
        PropertyChangeWeakSupport propListeners = new PropertyChangeWeakSupport(Projects.class);
        public PropertyChangeWeakSupport getPropertyChangeListeners() { return propListeners; }
      };
}
