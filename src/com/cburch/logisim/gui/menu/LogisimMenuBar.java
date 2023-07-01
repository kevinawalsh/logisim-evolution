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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JMenuBar;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.bfh.logisim.menu.MenuFPGA;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenu;

@SuppressWarnings("serial")
public class LogisimMenuBar extends JMenuBar {
  private class MyListener implements LocaleListener {
    public void localeChanged() {
      file.localeChanged();
      edit.localeChanged();
      project.localeChanged();
      fpga.localeChanged();
      simulate.localeChanged();
      help.localeChanged();
    }
  }

  public static final LogisimMenuItem PRINT = new LogisimMenuItem("Print");
  public static final LogisimMenuItem EXPORT_IMAGE = new LogisimMenuItem( "ExportImage");
 
  public static final LogisimMenuItem CUT = new LogisimMenuItem("Cut");
  public static final LogisimMenuItem COPY = new LogisimMenuItem("Copy");
  public static final LogisimMenuItem PASTE = new LogisimMenuItem("Paste");
  public static final LogisimMenuItem DELETE = new LogisimMenuItem("Delete");
  public static final LogisimMenuItem DUPLICATE = new LogisimMenuItem("Duplicate");
  public static final LogisimMenuItem SELECT_ALL = new LogisimMenuItem("SelectAll");
  public static final LogisimMenuItem SEARCH = new LogisimMenuItem("Search");
  public static final LogisimMenuItem RAISE = new LogisimMenuItem("Raise");
  public static final LogisimMenuItem LOWER = new LogisimMenuItem("Lower");
  public static final LogisimMenuItem RAISE_TOP = new LogisimMenuItem("RaiseTop");
  public static final LogisimMenuItem LOWER_BOTTOM = new LogisimMenuItem("LowerBottom");
  public static final LogisimMenuItem ADD_CONTROL = new LogisimMenuItem("AddControl");
  public static final LogisimMenuItem REMOVE_CONTROL = new LogisimMenuItem("RemoveControl");
  public static final LogisimMenuItem[] EDIT_ITEMS = {
    // UNDO, REDO,
    CUT, COPY, PASTE,
    DELETE, DUPLICATE, SELECT_ALL, SEARCH,
    RAISE, LOWER, RAISE_TOP, LOWER_BOTTOM,
    ADD_CONTROL, REMOVE_CONTROL,
  };

  public static final LogisimMenuItem ADD_VHDL = new LogisimMenuItem("AddVhdl");
  public static final LogisimMenuItem IMPORT_VHDL = new LogisimMenuItem("ImportVhdl");
  public static final LogisimMenuItem ADD_CIRCUIT = new LogisimMenuItem("AddCircuit");
  public static final LogisimMenuItem ADD_TOOL = new LogisimMenuItem("AddTool");
  public static final LogisimMenuItem MOVE_TOOL_UP = new LogisimMenuItem("MoveToolUp");
  public static final LogisimMenuItem MOVE_TOOL_DOWN = new LogisimMenuItem("MoveToolDown");
  public static final LogisimMenuItem SET_MAIN_CIRCUIT = new LogisimMenuItem("SetMainCircuit");
  public static final LogisimMenuItem REMOVE_TOOL = new LogisimMenuItem("RemoveTool");
  public static final LogisimMenuItem EDIT_LAYOUT = new LogisimMenuItem("EditLayout");
  public static final LogisimMenuItem EDIT_APPEARANCE = new LogisimMenuItem("EditAppearance");
  public static final LogisimMenuItem TOGGLE_APPEARANCE = new LogisimMenuItem("ToggleEditLayoutAppearance");
  public static final LogisimMenuItem REVERT_APPEARANCE = new LogisimMenuItem("RevertAppearance");
  public static final LogisimMenuItem ANALYZE_CIRCUIT = new LogisimMenuItem("AnalyzeCircuit");

  public static final LogisimMenuItem CIRCUIT_STATS = new LogisimMenuItem("GetCircuitStatistics");
  public static final LogisimMenuItem SIMULATE_STOP = new LogisimMenuItem("SimulateStop");
  public static final LogisimMenuItem SIMULATE_RUN = new LogisimMenuItem("SimulateRun");
  public static final LogisimMenuItem SIMULATE_RUN_TOGGLE = new LogisimMenuItem("SimulateRun");
  public static final LogisimMenuItem SIMULATE_STEP = new LogisimMenuItem("SimulateStep");
  public static final LogisimMenuItem SIMULATE_VHDL_ENABLE = new LogisimMenuItem("SimulateVhdlEnable");
  public static final LogisimMenuItem GENERATE_VHDL_SIM_FILES = new LogisimMenuItem("GenerateVhdlSimFiles");
  public static final LogisimMenuItem TICK_ENABLE = new LogisimMenuItem("TickEnable");
  public static final LogisimMenuItem TICK_HALF = new LogisimMenuItem("TickHalf");
  public static final LogisimMenuItem TICK_FULL = new LogisimMenuItem("TickFull");
  public static final LogisimMenuItem SIMULATE_ADD_STATE = new LogisimMenuItem("AddCircuitState");
  public static final LogisimMenuItem SIMULATE_DELETE_STATE = new LogisimMenuItem("DeleteCircuitState");

  private LFrame parent;
  private MyListener listener;
  private Project saveProj, baseProj, simProj;
  private SimulateListener simulateListener = null;
  private HashMap<LogisimMenuItem, MenuItem> menuItems = new HashMap<LogisimMenuItem, MenuItem>();
  private ArrayList<ChangeListener> enableListeners;

  public final MenuFile file;
  public final MenuEdit edit;
  public final MenuProject project;
  public final MenuSimulate simulate;
  public final MenuHelp help;
  public final MenuFPGA fpga;

  public LogisimMenuBar(LFrame parent, Project saveProj, Project baseProj, Project simProj) {
    this.parent = parent;
    this.listener = new MyListener();
    this.saveProj = saveProj;
    this.baseProj = baseProj;
    this.simProj = simProj;
    this.enableListeners = new ArrayList<ChangeListener>();
    add(file = new MenuFile(this));
    add(edit = new MenuEdit(this));
    add(project = new MenuProject(this));
    add(simulate = new MenuSimulate(this));
    add(fpga = new MenuFPGA(parent, this, saveProj));
    add(new WindowMenu(parent));
    add(help = new MenuHelp(this));

    LocaleManager.addLocaleListener(listener);
    listener.localeChanged();
  }

  public void addActionListener(LogisimMenuItem which, ActionListener l) {
    MenuItem item = menuItems.get(which);
    if (item != null)
      item.addActionListener(l);
  }

  public void addEnableListener(ChangeListener l) {
    enableListeners.add(l);
  }

  public void doAction(LogisimMenuItem which) {
    MenuItem item = menuItems.get(which);
    item.actionPerformed(new ActionEvent(item,
          ActionEvent.ACTION_PERFORMED, which.toString()));
  }

  public KeyStroke getAccelerator(LogisimMenuItem which) {
    MenuItem item = menuItems.get(which);
    return item == null ? null : item.getAccelerator();
  }

  void fireEnableChanged() {
    ChangeEvent e = new ChangeEvent(this);
    for (ChangeListener listener : enableListeners) {
      listener.stateChanged(e);
    }
  }

  void fireStateChanged(Simulator sim, CircuitState state) {
    if (simulateListener != null) {
      simulateListener.stateChangeRequested(sim, state);
    }
  }

  public LFrame getParentFrame() {
    return parent;
  }

  public Project getSaveProject() {
    return saveProj;
  }

  public Project getBaseProject() {
    return baseProj;
  }

  public Project getSimulationProject() {
    return simProj;
  }

  public boolean isEnabled(LogisimMenuItem item) {
    MenuItem menuItem = menuItems.get(item);
    return menuItem != null && menuItem.isEnabled();
  }

  void registerItem(LogisimMenuItem which, MenuItem item) {
    menuItems.put(which, item);
  }

  public void removeActionListener(LogisimMenuItem which, ActionListener l) {
    MenuItem item = menuItems.get(which);
    if (item != null)
      item.removeActionListener(l);
  }

  public void removeEnableListener(ChangeListener l) {
    enableListeners.remove(l);
  }

  public void setCircuitState(Simulator sim, CircuitState state) {
    simulate.setCurrentState(sim, state);
  }

  public void setEnabled(LogisimMenuItem which, boolean value) {
    MenuItem item = menuItems.get(which);
    if (item != null)
      item.setEnabled(value);
  }

  public void setSimulateListener(SimulateListener l) {
    simulateListener = l;
  }
}
