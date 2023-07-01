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

/**
 * Code taken from Cornell's version of Logisim:
 * http://www.cs.cornell.edu/courses/cs3410/2015sp/
 */

package com.cburch.logisim.gui.test;
import static com.cburch.logisim.gui.test.Strings.S;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.data.TestException;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.FileChooser;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;

public class TestFrame extends LFrame.SubWindowWithSimulation {

  private class MyListener implements ActionListener, ProjectListener,
          Simulator.Listener, LocaleListener, ModelListener {

    public void actionPerformed(ActionEvent event) {
      Object src = event.getSource();
      if (src == close) {
        requestClose();
      } else if (src == load) {
        FileChooser chooser = FileChooser.create(TestFrame.this);
        chooser.addFilenameFilter(TestVector.FILE_FILTER);
        chooser.addFilenameFilter(FileChooser.ACCEPT_ALL);

        if (!chooser.showOpenDialog())
          return;
        File file = chooser.getSelectedFile();
        if (!file.exists() || !file.canRead() || file.isDirectory()) {
          JOptionPane.showMessageDialog(
              TestFrame.this,
              S.fmt("fileCannotReadMessage", file.getName()),
              S.get("fileCannotReadTitle"),
              JOptionPane.OK_OPTION);
          return;
        }
        try {
          TestVector vec = new TestVector(file);
          finished = 0;
          count = vec.data.size();
          getModel().setVector(vec);
          curFile = file;
          getModel().setPaused(true);
          getModel().start();
        } catch (IOException e) {
          JOptionPane.showMessageDialog(
              TestFrame.this,
              S.fmt("fileCannotParseMessage", file.getName(), e.getMessage()),
              S.get("fileCannotReadTitle"),
              JOptionPane.OK_OPTION);
        } catch (TestException e) {
          JOptionPane.showMessageDialog(
              TestFrame.this,
              S.fmt("fileWrongPinsMessage", file.getName(), e.getMessage()),
              S.get("fileWrongPinsTitle"),
              JOptionPane.OK_OPTION);
        }
      } else if (src == run) {
        try {
          getModel().start();
        } catch (TestException e) {
          JOptionPane.showMessageDialog(TestFrame.this,
              S.fmt("fileWrongPinsMessage", curFile.getName(), e.getMessage()),
              S.get("fileWrongPinsTitle"),
              JOptionPane.OK_OPTION);
        }
      } else if (src == stop) {
        getModel().setPaused(true);
      } else if (src == reset) {
        getModel().clearResults();
        testingChanged();
      }
    }

    public void localeChanged() {
      setTitle(computeTitle(curModel, project));
      panel.localeChanged();
      load.setText(S.get("loadButton"));
      run.setText(S.get("runButton"));
      stop.setText(S.get("stopButton"));
      reset.setText(S.get("resetButton"));
      close.setText(S.get("closeButton"));
      myListener.testResultsChanged(getModel().getPass(), getModel()
          .getFail());
      windowManager.localeChanged();
    }

    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_STATE) {
        setSimulator(event.getProject().getSimulator(),
            event.getProject().getCircuitState().getCircuit());
      } else if (action == ProjectEvent.ACTION_SET_FILE) {
        setTitle(computeTitle(curModel, project));
      }
    }
    
    @Override
    public void simulatorReset(Simulator.Event e) {
      // ? curModel.propagationCompleted();
    }

    @Override
    public void propagationCompleted(Simulator.Event e) {
      // curModel.propagationCompleted();
    }

    @Override
    public void simulatorStateChanged(Simulator.Event e) {
    }

    public void testingChanged() {
      if (getModel().isRunning() && !getModel().isPaused()) {
        run.setEnabled(false);
        stop.setEnabled(true);
      } else if (getModel().getVector() != null && finished != count) {
        run.setEnabled(true);
        stop.setEnabled(false);
      } else {
        run.setEnabled(false);
        stop.setEnabled(false);
      }
      reset.setEnabled(getModel().getVector() != null && finished > 0);
    }

    public void testResultsChanged(int numPass, int numFail) {
      pass.setText(S.fmt("passMessage", Integer.toString(numPass)));
      fail.setText(S.fmt("failMessage", Integer.toString(numFail)));
      finished = numPass + numFail;
    }

    public void vectorChanged() {
    }

  }

  private class WindowMenuManager extends WindowMenuItemManager
    implements LocaleListener, ProjectListener {

    WindowMenuManager() {
      super(S.get("logFrameMenuItem"), false);
      project.addProjectWeakListener(null, this);
    }

    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return TestFrame.this;
    }

    public void localeChanged() {
      String title = project.getLogisimFile().getDisplayName();
      setText(S.fmt("testFrameMenuItem", title));
    }

    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_FILE) {
        localeChanged();
      }
    }

  }

  private static String computeTitle(Model data, Project proj) {
    String name = data == null ? "???" : data.getCircuit().getName();
    return S.fmt("testFrameTitle", name, proj
        .getLogisimFile().getDisplayName());
  }

  private static final long serialVersionUID = 1L;
  private Simulator curSimulator = null;
  private Model curModel;
  private Map<Circuit, Model> modelMap = new HashMap<Circuit, Model>();
  private MyListener myListener = new MyListener();
  private WindowMenuManager windowManager;
  private int finished, count;

  private File curFile;
  private TestPanel panel;
  private JButton load = new JButton();
  private JButton run = new JButton();
  private JButton stop = new JButton();
  private JButton reset = new JButton();
  private JButton close = new JButton();
  private JLabel pass = new JLabel();

  private JLabel fail = new JLabel();

  public TestFrame(Project project) {
    super(project);
    this.windowManager = new WindowMenuManager();
    project.addProjectWeakListener(null, myListener);
    setSimulator(project.getSimulator(), project.getCircuitState().getCircuit());

    panel = new TestPanel(this);

    JPanel statusPanel = new JPanel();
    statusPanel.add(pass);
    statusPanel.add(fail);

    JPanel buttonPanel = new JPanel();
    buttonPanel.add(load);
    buttonPanel.add(run);
    buttonPanel.add(stop);
    buttonPanel.add(reset);
    buttonPanel.add(close);
    load.addActionListener(myListener);
    run.addActionListener(myListener);
    stop.addActionListener(myListener);
    reset.addActionListener(myListener);
    close.addActionListener(myListener);

    run.setEnabled(false);
    stop.setEnabled(false);
    reset.setEnabled(false);

    Container contents = getContentPane();
    panel.setPreferredSize(new Dimension(450, 300));
    contents.add(statusPanel, BorderLayout.NORTH);
    contents.add(panel, BorderLayout.CENTER);
    contents.add(buttonPanel, BorderLayout.SOUTH);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();
  }

  Model getModel() {
    return curModel;
  }

  private void setSimulator(Simulator value, Circuit circuit) {
    if ((value == null) == (curModel == null)) {
      if (value == null
          || value.getCircuitState().getCircuit() == curModel.getCircuit())
        return;
    }

    menubar.setCircuitState(value, value.getCircuitState());

    if (curSimulator != null)
      curSimulator.removeSimulatorListener(myListener);
    if (curModel != null)
      curModel.setSelected(false);
    if (curModel != null)
      curModel.removeModelWeakListener(null, myListener);

    Model oldModel = curModel;
    Model data = null;
    if (value != null) {
      data = modelMap.get(value.getCircuitState().getCircuit());
      if (data == null) {
        data = new Model(project, value.getCircuitState().getCircuit());
        modelMap.put(data.getCircuit(), data);
      }
    }
    curSimulator = value;
    curModel = data;

    if (curSimulator != null)
      curSimulator.addSimulatorListener(myListener);
    if (curModel != null)
      curModel.setSelected(true);
    if (curModel != null)
      curModel.addModelWeakListener(null, myListener);
    setTitle(computeTitle(curModel, project));
    if (panel != null)
      panel.modelChanged(oldModel, curModel);
  }

  public void setVisible(boolean value) {
    if (value)
      windowManager.frameOpened(this);
    super.setVisible(value);
  }

}
