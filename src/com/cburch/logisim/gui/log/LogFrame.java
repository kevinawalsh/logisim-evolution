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

package com.cburch.logisim.gui.log;
import static com.cburch.logisim.gui.log.Strings.S;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.chrono.ChronoPanel;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;

public class LogFrame extends LFrame.SubWindowWithSimulation {
  private LogMenuListener menuListener;

  private class MyListener implements ProjectListener,
          LibraryListener, Simulator.ProgressListener, LocaleListener {

    public void libraryChanged(LibraryEvent event) {
      int action = event.getAction();
      if (action == LibraryEvent.SET_NAME) {
        setTitle(computeTitle(curModel, project));
      }
    }

    public void localeChanged() {
      setTitle(computeTitle(curModel, project));
      for (int i = 0; i < panels.length; i++) {
        tabbedPane.setTitleAt(i, panels[i].getTitle());
        tabbedPane.setToolTipTextAt(i, panels[i].getToolTipText());
        panels[i].localeChanged();
      }
      windowManager.localeChanged();
    }

    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_STATE) {
        setSimulator(event.getProject().getSimulator(),
            event.getProject().getCircuitState());
      } else if (action == ProjectEvent.ACTION_SET_FILE) {
        setTitle(computeTitle(curModel, project));
      }
    }

    @Override
    public void simulatorReset(Simulator.Event e) {
      curModel.simulatorReset();
    }

    @Override
    public void propagationCompleted(Simulator.Event e) {
      curModel.propagationCompleted(e.didTick(), e.didSingleStep(), e.didPropagate());
    }

    @Override
    public boolean wantProgressEvents() {
      return curModel.isFine();
    }

    @Override
    public void propagationInProgress(Simulator.Event e) {
      curModel.propagationCompleted(false, true, false); // treat as a single-step
    }

    @Override
    public void simulatorStateChanged(Simulator.Event e) {
      if (setSimulator(project.getSimulator(), project.getCircuitState()))
        return;
      if (curModel != null)
        curModel.checkForClocks();
    }

  }

  // TODO should automatically repaint icons when component attr change
  // TODO ? moving a component using Select tool removes it from selection
  private class WindowMenuManager extends WindowMenuItemManager
    implements LocaleListener, ProjectListener, LibraryListener {
    Project proj;
    WindowMenuManager(Project p) {
      super(S.get("logFrameMenuItem"), false);
      proj = p;
      proj.addProjectWeakListener(null, this);
      proj.addLibraryWeakListener(/*null,*/ this);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return LogFrame.this;
    }

    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.SET_NAME) {
        localeChanged();
      }
    }

    public void localeChanged() {
      String title = proj.getLogisimFile().getDisplayName();
      setText(S.fmt("logFrameMenuItem", title));
    }

    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_FILE) {
        localeChanged();
      }
    }
  }

  private static String computeTitle(Model data, Project proj) {
    String name = data == null ? "???"
        : data.getCircuitState().getCircuit().getName();
    return S.fmt("logFrameTitle", name, proj.getLogisimFile().getDisplayName());
  }

  private static final long serialVersionUID = 1L;
  private Simulator curSimulator = null;
  private Model curModel;
  private Map<CircuitState, Model> modelMap = new HashMap<>();
  private MyListener myListener = new MyListener();
  private MyChangeListener myChangeListener = new MyChangeListener();

  private WindowMenuManager windowManager;
  private LogPanel[] panels;
  private JTabbedPane tabbedPane;

  public JButton makeSelectionButton() {
    JButton button = new JButton(S.get("addRemoveSignals"));
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        SelectionPanel.doDialog(LogFrame.this);
      }
    });
    return button;
  }

  public LogFrame(Project project) {
    super(project);
    windowManager = new WindowMenuManager(project);
    menuListener = new LogMenuListener(menubar);
    project.addProjectWeakListener(null, myListener);
    project.addLibraryWeakListener(/*null,*/ myListener);
    setSimulator(project.getSimulator(), project.getCircuitState());

    panels = new LogPanel[] {
      new ChronoPanel(this),
      // new TablePanel(this), // todo: fix table panel
      // new FilePanel(this), // todo: rethink file writer in light of signal rewriting
      new SettingsPanel(this),
    };
    tabbedPane = new JTabbedPane();
    // tabbedPane.setFont(new Font("Dialog", Font.BOLD, 9));
    for (int index = 0; index < panels.length; index++) {
      LogPanel panel = panels[index];
      tabbedPane.addTab(panel.getTitle(), null, panel,
          panel.getToolTipText());
    }
    tabbedPane.addChangeListener(myChangeListener);
    myChangeListener.stateChanged(null);


    Container contents = getContentPane();
    int w = Math.max(550, project.getFrame().getWidth());
    int h = 300;
    tabbedPane.setPreferredSize(new Dimension(w, h));
    contents.add(tabbedPane, BorderLayout.CENTER);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();
    h = getSize().height;

    // Try to place below circuit window, or at least near bottom of screen,
    // using same width as circuit window.
    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    Rectangle r = project.getFrame().getBounds();
    int x = r.x;
    int y = r.y + r.height;
    if (y + h > d.height) { // too small below circuit
      if (r.y >= h) // plenty of room above circuit
        y = r.y - h;
      else if (r.y > d.height - h) // circuit is near bottom of screen
        y = 0;
      else // circuit is near top of screen
        y = d.height - h;
    }
    setLocation(x, y);
    setMinimumSize(new Dimension(300, 200));

    // set initial focus to first panel
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        e.getWindow().removeWindowListener(this);
        myChangeListener.stateChanged(null);
      }
    });
  }

  public LogisimMenuBar getLogisimMenuBar() {
    return menubar;
  }

  public LogMenuListener getMenuListener() {
    return menuListener;
  }

  public Model getModel() {
    return curModel;
  }

  LogPanel[] getPrefPanels() {
    return panels;
  }

  private boolean setSimulator(Simulator value, CircuitState state) {
    if ((value == null) == (curModel == null)) {
      if (value == null || value.getCircuitState() == curModel.getCircuitState())
        return false;
    }
    menubar.setCircuitState(value, state);

    if (curSimulator != null)
      curSimulator.removeSimulatorListener(myListener);
    if (curModel != null)
      curModel.setSelected(false);

    Model oldModel = curModel;
    Model data = null;
    if (value != null) {
      data = modelMap.get(value.getCircuitState());
      if (data == null) {
        data = new Model(value.getCircuitState());
        modelMap.put(data.getCircuitState(), data);
      }
    }
    curSimulator = value;
    curModel = data;

    if (curSimulator != null)
      curSimulator.addSimulatorListener(myListener);
    if (curModel != null)
      curModel.setSelected(true);
    setTitle(computeTitle(curModel, project));
    if (panels != null) {
      for (int i = 0; i < panels.length; i++) {
        panels[i].modelChanged(oldModel, curModel);
      }
    }
    return true;
  }

  @Override
  public void setVisible(boolean value) {
    if (value)
      windowManager.frameOpened(this);
    super.setVisible(value);
  }

  @Override
  public void requestClose() {
    super.requestClose();
    dispose();
  }

  private class MyChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      Object selected = tabbedPane.getSelectedComponent();
      if (selected instanceof JScrollPane) {
        selected = ((JScrollPane) selected).getViewport().getView();
      }
      if (selected instanceof JPanel) {
        ((JPanel) selected).requestFocus();
      }
      if (selected instanceof LogPanel) {
        LogPanel tab = (LogPanel)selected;
        menuListener.setEditHandler(tab.getEditHandler());
        menuListener.setPrintHandler(tab.getPrintHandler());
        // menuListener.setSimulationHandler(tab.getSimulationHandler());
        tab.updateTab();
      }
    }
  }
}
