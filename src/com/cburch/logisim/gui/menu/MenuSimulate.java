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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.proj.Project;
// import com.cburch.logisim.std.hdl.VhdlSimulator;

@SuppressWarnings("serial")
public class MenuSimulate extends Menu {

  private class CircuitStateMenuItem extends JMenuItem
    implements CircuitListener, ActionListener {

    private CircuitState circuitState;

    public CircuitStateMenuItem(CircuitState circuitState) {
      this.circuitState = circuitState;

      Circuit circuit = circuitState.getCircuit();
      circuit.addCircuitWeakListener(null, this);
      this.setText(circuit.getName());
      addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      menubar.fireStateChanged(currentSim, circuitState);
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      if (event.getAction() == CircuitEvent.ACTION_SET_NAME) {
        this.setText(circuitState.getCircuit().getName());
      }
    }

    void unregister() {
      Circuit circuit = circuitState.getCircuit();
      circuit.removeCircuitWeakListener(null, this);
    }
  }

  private class MyListener
    implements ActionListener, Simulator.StatusListener, ChangeListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      Project proj = menubar.getSimulationProject();
      if (proj == null)
        return;
      // VhdlSimulator vhdl = proj.getVhdlSimulator();
      // if (vhdl != null && (src == simulate_vhdl_enable
      //     || src == LogisimMenuBar.SIMULATE_VHDL_ENABLE)) {
      //   vhdl.setEnabled(!vhdl.isEnabled());
      // } else if (vhdl != null && (src == vhdl_sim_files
      //     || src == LogisimMenuBar.GENERATE_VHDL_SIM_FILES)) {
      //   vhdl.restart();
      // } else
      if (src == log) {
        proj.getLogFrame().setVisible(true);
      } else if (src == test) {
        proj.getTestFrame().setVisible(true);
      }

      Simulator sim = proj.getSimulator();
      if (sim == null) {
        return;
      } else if (src == LogisimMenuBar.SIMULATE_STOP) {
        sim.setAutoPropagation(false);
        proj.repaintCanvas();
      } else if (src == LogisimMenuBar.SIMULATE_RUN) {
        sim.setAutoPropagation(true);
        proj.repaintCanvas();
      } else if (src == runToggle || src == LogisimMenuBar.SIMULATE_RUN_TOGGLE) {
        sim.setAutoPropagation(!sim.isAutoPropagating());
        proj.repaintCanvas();
      } else if (src == reset) {
        /* Restart VHDL simulation (in QuestaSim) */
        // if (vhdl != null && vhdl.isRunning()) {
        //   vhdl.reset();
        //   // Wait until the restart finishes, otherwise the signal reset will be
        //   // sent to the VHDL simulator before the sim is loaded and errors will
        //   // occur. Wait time (0.5 sec) is arbitrary.
        //   // FIXME: Find a better way to do blocking reset.
        //   try { Thread.sleep(500); }
        //   catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        // }
        sim.reset();
        proj.repaintCanvas();
      } else if (src == step || src == LogisimMenuBar.SIMULATE_STEP) {
        sim.setAutoPropagation(false);
        sim.step();
      } else if (src == tickHalf || src == LogisimMenuBar.TICK_HALF) {
        sim.tick(1);
      } else if (src == tickFull || src == LogisimMenuBar.TICK_FULL) {
        sim.tick(2);
      } else if (src == ticksEnabled || src == LogisimMenuBar.TICK_ENABLE) {
        sim.setAutoTicking(!sim.isAutoTicking());
      } else if (src == addSim || src == LogisimMenuBar.SIMULATE_ADD_STATE) {
        CircuitState state = proj.getCircuitState();
        if (state != null)
          proj.setCircuitState(state.cloneAsNewRootState()); // for now, duplicate b/c easy
      } else if (src == delSim || src == LogisimMenuBar.SIMULATE_DELETE_STATE) {
        CircuitState state = proj.getCircuitState();
        if (state != null)
          proj.removeCircuitStateAncestor(state);
      }
    }

    @Override
    public void simulatorReset(Simulator.Event e) {
      updateSimulator(e);
    }

    @Override
    public void simulatorStateChanged(Simulator.Event e) {
      updateSimulator(e);
    }

    void updateSimulator(Simulator.Event e) {
      Simulator sim = e.getSource();
      if (sim != currentSim) {
        return;
      }
      computeEnabled();
      runToggle.setSelected(sim.isAutoPropagating());
      ticksEnabled.setSelected(sim.isAutoTicking());
      int index = closestTickFreqIndex(sim.getTickFrequency());
      for (int i = 0; i < tickFreqs.length; i++) {
        tickFreqs[i].setSelected(i == index);
      }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
    }

  }

  private static int closestTickFreqIndex(double freq) {
    int index = 0;
    double delta = Math.abs(SupportedTickFrequencies[0] - freq);
    for (int i = 1; i < SupportedTickFrequencies.length; i++) {
      double d = Math.abs(SupportedTickFrequencies[i] - freq);
      if (d <= delta) {
        delta = d;
        index = i;
      }
    }
    return index;
  }

  public static void decreaseTickFrequency(Simulator sim) {
    double freq = sim.getTickFrequency();
    int i = closestTickFreqIndex(freq);
    if (freq > SupportedTickFrequencies[i])
      sim.setTickFrequency(SupportedTickFrequencies[i]);
    else if (i+1 < SupportedTickFrequencies.length)
      sim.setTickFrequency(SupportedTickFrequencies[i+1]);
  }

  public static void increaseTickFrequency(Simulator sim) {
    double freq = sim.getTickFrequency();
    int i = closestTickFreqIndex(freq);
    if (freq < SupportedTickFrequencies[i])
      sim.setTickFrequency(SupportedTickFrequencies[i]);
    else if (i-1 >= 0)
      sim.setTickFrequency(SupportedTickFrequencies[i-1]);
  }

  private class TickFrequencyChoice extends JRadioButtonMenuItem
    implements ActionListener {

    private double freq;

    public TickFrequencyChoice(double value) {
      freq = value;
      addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (currentSim != null) {
        currentSim.setTickFrequency(freq);
      }
    }

    public void localeChanged() {
      double f = freq;
      if (f < 1000) {
        String hzStr;
        if (Math.abs(f - Math.round(f)) < 0.0001) {
          hzStr = "" + (int) Math.round(f);
        } else {
          hzStr = "" + f;
        }
        setText(S.fmt("simulateTickFreqItem", hzStr));
      } else if (f < 1000000) {
        String kHzStr;
        double kf = Math.round(f / 100) / 10.0;
        if (kf == Math.round(kf)) {
          kHzStr = "" + (int) kf;
        } else {
          kHzStr = "" + kf;
        }
        setText(S.fmt("simulateTickKFreqItem", kHzStr));
      } else {
        String mHzStr;
        double mf = Math.round(f / 100000) / 10.0;
        if (mf == Math.round(mf)) {
          mHzStr = "" + (int) mf;
        } else {
          mHzStr = "" + mf;
        }
        setText(S.fmt("simulateTickMFreqItem", mHzStr));
      }
    }
  }

  public static ArrayList<String> getTickFrequencyStrings() {
    ArrayList<String> result = new ArrayList<String>();
    for (int i = 0; i < SupportedTickFrequencies.length; i++) {
      if (SupportedTickFrequencies[i] < 1000) {
        String hzStr;
        if (Math.abs(SupportedTickFrequencies[i]
              - Math.round(SupportedTickFrequencies[i])) < 0.0001) {
          hzStr = "" + (int) Math.round(SupportedTickFrequencies[i]);
        } else {
          hzStr = "" + SupportedTickFrequencies[i];
        }
        result.add(S.fmt("simulateTickFreqItem", hzStr));
      } else if (SupportedTickFrequencies[i] < 1000000) {
        String kHzStr;
        double kf = Math.round(SupportedTickFrequencies[i] / 100) / 10.0;
        if (kf == Math.round(kf)) {
          kHzStr = "" + (int) kf;
        } else {
          kHzStr = "" + kf;
        }
        result.add(S.fmt("simulateTickKFreqItem", kHzStr));
      } else {
        String mHzStr;
        double mf = Math.round(SupportedTickFrequencies[i] / 100000) / 10.0;
        if (mf == Math.round(mf)) {
          mHzStr = "" + (int) mf;
        } else {
          mHzStr = "" + mf;
        }
        result.add(S.fmt("simulateTickMFreqItem", mHzStr));
      }

    }
    return result;
  }

  public static final Double[] SupportedTickFrequencies = {
    8000000.0, 4000000.0, 2000000.0, 1000000.0, // up to 8MHz
    500000.0, 250000.0, 125000.0, 64000.0, 32000.0, 16000.0, 8000.0, 4000.0, 2000.0, 1000.0,
    500.0, 250.0, 125.0, 64.0, 32.0, 16.0, 8.0, 4.0, 2.0, 1.0, 0.5, 0.25
  };
  private LogisimMenuBar menubar;
  private MyListener myListener = new MyListener();
  private CircuitState currentState = null;
  private CircuitState bottomState = null;
  private Simulator currentSim = null;
  private MenuItemCheckImpl runToggle;
  private JMenuItem reset = new JMenuItem();
  private MenuItemImpl step;
  // private MenuItemImpl vhdl_sim_files;
  // private MenuItemCheckImpl simulate_vhdl_enable;
  private MenuItemCheckImpl ticksEnabled;
  private MenuItemImpl tickHalf;
  private MenuItemImpl tickFull;
  private MenuItemImpl addSim, delSim;
  private JMenu tickFreq = new JMenu();
  private TickFrequencyChoice[] tickFreqs = new TickFrequencyChoice[SupportedTickFrequencies.length];
  private JMenu downStateMenu = new JMenu();
  private ArrayList<CircuitStateMenuItem> downStateItems = new ArrayList<CircuitStateMenuItem>();
  private JMenu upStateMenu = new JMenu();
  private ArrayList<CircuitStateMenuItem> upStateItems = new ArrayList<CircuitStateMenuItem>();
  private JMenuItem log = new JMenuItem();
  private JMenuItem test = new JMenuItem();

  public MenuSimulate(LogisimMenuBar menubar) {
    this.menubar = menubar;

    runToggle = new MenuItemCheckImpl(this, LogisimMenuBar.SIMULATE_RUN_TOGGLE);
    step = new MenuItemImpl(this, LogisimMenuBar.SIMULATE_STEP);
    // simulate_vhdl_enable = new MenuItemCheckImpl(this,
    //     LogisimMenuBar.SIMULATE_VHDL_ENABLE);
    // vhdl_sim_files = new MenuItemImpl(this,
    //     LogisimMenuBar.GENERATE_VHDL_SIM_FILES);
    ticksEnabled = new MenuItemCheckImpl(this, LogisimMenuBar.TICK_ENABLE);
    tickHalf = new MenuItemImpl(this, LogisimMenuBar.TICK_HALF);
    tickFull = new MenuItemImpl(this, LogisimMenuBar.TICK_FULL);
    addSim = new MenuItemImpl(this, LogisimMenuBar.SIMULATE_ADD_STATE);
    delSim = new MenuItemImpl(this, LogisimMenuBar.SIMULATE_DELETE_STATE);

    menubar.registerItem(LogisimMenuBar.SIMULATE_RUN_TOGGLE, runToggle);
    menubar.registerItem(LogisimMenuBar.SIMULATE_STEP, step);
    // menubar.registerItem(LogisimMenuBar.SIMULATE_VHDL_ENABLE,
    //     simulate_vhdl_enable);
    // menubar.registerItem(LogisimMenuBar.GENERATE_VHDL_SIM_FILES,
    //     vhdl_sim_files);
    menubar.registerItem(LogisimMenuBar.TICK_ENABLE, ticksEnabled);
    menubar.registerItem(LogisimMenuBar.TICK_HALF, tickHalf);
    menubar.registerItem(LogisimMenuBar.TICK_FULL, tickFull);
    menubar.registerItem(LogisimMenuBar.SIMULATE_ADD_STATE, addSim);
    menubar.registerItem(LogisimMenuBar.SIMULATE_DELETE_STATE, delSim);

    int menuMask = getToolkit().getMenuShortcutKeyMaskEx();
    runToggle.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask));
    reset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, menuMask));
    step.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, menuMask));
    tickHalf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, menuMask));
    tickFull.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0)); // like "compile/run" in many IDEs
    ticksEnabled.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, menuMask));

    ButtonGroup bgroup = new ButtonGroup();
    for (int i = 0; i < SupportedTickFrequencies.length; i++) {
      tickFreqs[i] = new TickFrequencyChoice(SupportedTickFrequencies[i]);
      bgroup.add(tickFreqs[i]);
      tickFreq.add(tickFreqs[i]);
    }

    add(runToggle);
    add(step);
    add(reset);
    // add(simulate_vhdl_enable);
    // add(vhdl_sim_files);
    addSeparator();
    add(upStateMenu);
    add(downStateMenu);
    addSeparator();
    add(tickHalf);
    add(tickFull);
    add(ticksEnabled);
    add(tickFreq);
    addSeparator();
    add(log);
    add(test);

    setEnabled(false);
    runToggle.setEnabled(false);
    reset.setEnabled(false);
    step.setEnabled(false);
    // simulate_vhdl_enable.setEnabled(false);
    // vhdl_sim_files.setEnabled(false);
    upStateMenu.setEnabled(false);
    downStateMenu.setEnabled(false);
    tickHalf.setEnabled(false);
    tickFull.setEnabled(false);
    ticksEnabled.setEnabled(false);
    tickFreq.setEnabled(false);

    runToggle.addChangeListener(myListener);
    menubar.addActionListener(LogisimMenuBar.SIMULATE_RUN_TOGGLE, myListener);
    menubar.addActionListener(LogisimMenuBar.SIMULATE_STEP, myListener);
    // menubar.addActionListener(LogisimMenuBar.SIMULATE_VHDL_ENABLE, myListener);
    // menubar.addActionListener(LogisimMenuBar.GENERATE_VHDL_SIM_FILES, myListener);
    menubar.addActionListener(LogisimMenuBar.TICK_ENABLE, myListener);
    menubar.addActionListener(LogisimMenuBar.TICK_HALF, myListener);
    menubar.addActionListener(LogisimMenuBar.TICK_FULL, myListener);
    menubar.addActionListener(LogisimMenuBar.SIMULATE_ADD_STATE, myListener);
    menubar.addActionListener(LogisimMenuBar.SIMULATE_DELETE_STATE, myListener);
    // runToggle.addActionListener(myListener);
    reset.addActionListener(myListener);
    // step.addActionListener(myListener);
    // tickHalf.addActionListener(myListener);
    // tickFull.addActionListener(myListener);
    // ticksEnabled.addActionListener(myListener);
    log.addActionListener(myListener);
    test.addActionListener(myListener);

    computeEnabled();
  }

  private void clearItems(ArrayList<CircuitStateMenuItem> items) {
    for (CircuitStateMenuItem item : items) {
      item.unregister();
    }
    items.clear();
  }

  @Override
  void computeEnabled() {
    boolean present = currentState != null;
    Simulator sim = this.currentSim;
    boolean simRunning = sim != null && sim.isAutoPropagating();
    setEnabled(present);
    runToggle.setEnabled(present);
    reset.setEnabled(present);
    step.setEnabled(present);
    // simulate_vhdl_enable.setEnabled(present);
    // vhdl_sim_files.setEnabled(present);
    upStateMenu.setEnabled(present);
    downStateMenu.setEnabled(present);
    tickHalf.setEnabled(present);
    tickFull.setEnabled(present);
    addSim.setEnabled(present);
    delSim.setEnabled(present);
    ticksEnabled.setEnabled(present);
    tickFreq.setEnabled(present);
    menubar.fireEnableChanged();
  }

  public void localeChanged() {
    this.setText(S.get("simulateMenu"));
    runToggle.setText(S.get("simulateRunItem"));
    reset.setText(S.get("simulateResetItem"));
    step.setText(S.get("simulateStepItem"));
    // simulate_vhdl_enable.setText(S.get("simulateVhdlEnableItem"));
    // vhdl_sim_files.setText(S.get("simulateGenVhdlFilesItem"));
    tickHalf.setText(S.get("simulateTickHalfItem"));
    tickFull.setText(S.get("simulateTickFullItem"));
    addSim.setText(S.get("simulateAddState"));
    delSim.setText(S.get("simulateDeleteState"));
    ticksEnabled.setText(S.get("simulateTickItem"));
    tickFreq.setText(S.get("simulateTickFreqMenu"));
    for (int i = 0; i < tickFreqs.length; i++) {
      tickFreqs[i].localeChanged();
    }
    downStateMenu.setText(S.get("simulateDownStateMenu"));
    upStateMenu.setText(S.get("simulateUpStateMenu"));
    log.setText(S.get("simulateLogItem"));
    test.setText(S.get("simulateTestItem"));
  }

  private void recreateStateMenu(JMenu menu,
      ArrayList<CircuitStateMenuItem> items, int code) {
    menu.removeAll();
    menu.setEnabled(items.size() > 0);
    boolean first = true;
    int mask = getToolkit().getMenuShortcutKeyMaskEx();
    for (int i = items.size() - 1; i >= 0; i--) {
      JMenuItem item = items.get(i);
      menu.add(item);
      if (first) {
        item.setAccelerator(KeyStroke.getKeyStroke(code, mask));
        first = false;
      } else {
        item.setAccelerator(null);
      }
    }
  }

  private void recreateStateMenus() {
    // recreateStateMenu(downStateMenu, downStateItems, KeyEvent.VK_RIGHT);
    // recreateStateMenu(upStateMenu, upStateItems, KeyEvent.VK_LEFT);
    recreateStateMenu(downStateMenu, downStateItems, KeyEvent.VK_DOWN);
    recreateStateMenu(upStateMenu, upStateItems, KeyEvent.VK_UP);
  }

  public void setCurrentState(Simulator sim, CircuitState value) {
    if (currentState == value) {
      return;
    }
    Simulator oldSim = currentSim;
    CircuitState oldState = currentState;
    currentSim = sim;
    currentState = value;
    if (bottomState == null) {
      bottomState = currentState;
    } else if (currentState == null) {
      bottomState = null;
    } else {
      CircuitState cur = bottomState;
      while (cur != null && cur != currentState) {
        cur = cur.getParentState();
      }
      if (cur == null) {
        bottomState = currentState;
      }
    }

    boolean oldPresent = oldState != null;
    boolean present = currentState != null;
    if (oldPresent != present) {
      computeEnabled();
    }

    if (currentSim != oldSim) {
      double freq = currentSim == null ? 1.0 : currentSim.getTickFrequency();
      int index = closestTickFreqIndex(freq);
      for (int i = 0; i < tickFreqs.length; i++) {
        tickFreqs[i].setSelected(i == index);
      }

      if (oldSim != null) {
        oldSim.removeSimulatorListener(myListener);
      }
      if (currentSim != null) {
        currentSim.addSimulatorListener(myListener);
      }
      myListener.simulatorStateChanged(new Simulator.Event(sim, false, false, false));
    }

    clearItems(downStateItems);
    CircuitState cur = bottomState;
    while (cur != null && cur != currentState) {
      downStateItems.add(new CircuitStateMenuItem(cur));
      cur = cur.getParentState();
    }
    if (cur != null) {
      cur = cur.getParentState();
    }
    clearItems(upStateItems);
    while (cur != null) {
      upStateItems.add(0, new CircuitStateMenuItem(cur));
      cur = cur.getParentState();
    }
    recreateStateMenus();
  }
}
