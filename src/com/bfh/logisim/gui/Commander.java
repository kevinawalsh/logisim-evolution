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

package com.bfh.logisim.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.bfh.logisim.download.FPGADownload;
import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.BoardReader;
import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.hdlgenerator.ToplevelHDLGenerator;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.settings.Settings;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.generic.ComboBox;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.MenuSimulate;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.Projects;

public class Commander extends JFrame
  implements LibraryListener, CircuitListener, Settings.Listener {

  private static final String SLASH = File.separator;
  private static final String SANDBOX_DIR = "sandbox" + SLASH;
  private static final String SCRIPT_DIR = "scripts" + SLASH;
  private static final String UCF_DIR = "ucf" + SLASH;
  private static final String[] HDL_PATHS = { "verilog", "vhdl", "scripts", "sandbox", "ucf" };
  private static final String OTHER_BOARD = "Other";

  private final Project proj;
  private Board board;
  private String lang;
  private int boardsListSelectedIndex;
  private final FPGAReport err = new FPGAReport(this);
  public int fatals, warns, errors;
  private final Settings settings = Settings.getSettings();

  private static final String MAX_SPEED = "Maximum Speed";
  private static final String DIV_SPEED = "Reduced Speed";
  private static final String DYN_SPEED = "Dynamic Speed";

  // Three steps: (1) Generate, (2) Synthesize, (3 Downlaod.
  // User can do choose to do just first step, just last step, or all three steps.
  private static final String HDL_GEN_ONLY = "Generate HDL only";
  private static final String HDL_GEN_AND_DOWNLOAD = "Synthesize and Download";
  private static final String HDL_DOWNLOAD_ONLY = "Download only";

  private static final String ANNOTATE_SOME = "Add missing labels";
  // private static final String ANNOTATE_ALL = "Relabel all components";

  private final JLabel textCircuit = new JLabel("Circuit: ", SwingConstants.RIGHT);
  private final JLabel textLanguage = new JLabel("Language: ", SwingConstants.RIGHT);
  private final JLabel textTargetDiv = new JLabel("Divide clock by...", SwingConstants.RIGHT);

  private final BoardIcon boardIcon = new BoardIcon();
  private final JButton annotateButton = new JButton("Annotate");
  // private final JPopupMenu annotatePopup = new JPopupMenu("Options");
  // private final HashMap<String, JCheckBoxMenuItem> annotateItems = new HashMap<>();

  private final JButton actionButton = new JButton("Synthesize & Download");
  private final JPopupMenu actionPopup = new JPopupMenu("Options");
  private final HashMap<String, JCheckBoxMenuItem> actionItems = new HashMap<>();
  private final JCheckBox writeToFlash = new JCheckBox("Flash?");

  private final ComboBox<String> boardsList = new ComboBox<>();
  private final ComboBox<Circuit> circuitsList = new ComboBox<>();
  private final ComboBox<String> clockOption = new ComboBox<>();
  private final ComboBox<Object> clockDivRate = new ComboBox<>();
  private final ComboBox<Object> clockDivCount = new ComboBox<>();
  private final ComboBox<String> language = new ComboBox<>();
  private final JButton toolSettings = new JButton("Settings");

  private final JProgressBar progressBar = new JProgressBar(0, 5);
  private final JButton stop = new JButton("Stop");

  public final Console messages = new Console(this, "Messages", 0);
  private final ArrayList<Console> consoles = new ArrayList<>();
  private final JTabbedPane tabbedPane = new JTabbedPane();

  public void libraryChanged(LibraryEvent e) {
    if (e.getAction() == LibraryEvent.ADD_TOOL || e.getAction() == LibraryEvent.REMOVE_TOOL)
      updateCircuitList();
  }

  public void circuitChanged(CircuitEvent e) {
    if (e.getAction() == CircuitEvent.ACTION_SET_NAME)
      updateCircuitList();
  }

  public Commander(Project p) {
    super("FPGA Commander : " + p.getLogisimFile().getName());
    LFrame.attachIcon(this, "resources/logisim/img/fpga-icon-%d.png");
    proj = p;
    lang = settings.GetHDLType();

    board = BoardReader.read(settings.GetSelectedBoardFileName());
    boardIcon.setImage(board == null ? null : board.image);

    setResizable(true);
    setAlwaysOnTop(false);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    GridBagConstraints c = new GridBagConstraints();

    // listen for project changes
    proj.addProjectWeakListener(this, e -> {
      if (e.getAction() == ProjectEvent.ACTION_SET_CURRENT && e.getCircuit() != null)
        circuitsList.setSelectedItem(e.getCircuit());
      else if (e.getAction() == ProjectEvent.ACTION_SET_FILE)
        updateCircuitList();
    });
    proj.addLibraryWeakListener(this);
    proj.addCircuitWeakListener(this);

    // listen for simulator changes
    proj.getSimulator().addSimulatorListener(new Simulator.Listener() {
      public void simulatorReset(Simulator.Event e) { }
      public void propagationCompleted(Simulator.Event e) { }
      public void simulatorStateChanged(Simulator.Event e) { useTickSpeedFromSimulator(); }
    });

    // configure circuit list
    circuitsList.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object v, int i, boolean s, boolean f) {
        return super.getListCellRendererComponent(list, ((Circuit)v).getName(), i, s, f);
      }
    });
    updateCircuitList();
    Dimension d = circuitsList.getPreferredSize();
    d.width = Math.max(d.width, 150);
    circuitsList.setMinimumSize(d);
    circuitsList.setPreferredSize(d);
    
    // configure language options
    language.addItem(Settings.VHDL);
    language.addItem(Settings.VERILOG);
    language.setSelectedItem(lang);
    language.addActionListener(e -> setLang());

    // configure board list
    for (String boardname : settings.GetBoardNames())
      boardsList.addItem(boardname);
    boardsList.addItem(OTHER_BOARD);
    boardsList.setSelectedItem(settings.GetSelectedBoard());
    boardsListSelectedIndex = boardsList.getSelectedIndex();
    boardsList.addActionListener(e -> setBoard());

    // configure clock speed options
    clockOption.addItem(MAX_SPEED);
    clockOption.addItem(DIV_SPEED);
    clockOption.addItem(DYN_SPEED);
    clockOption.setSelectedItem(DIV_SPEED);
    clockDivRate.setEditable(true);
    clockDivCount.setEditable(true);
    clockOption.addActionListener(e -> setClockOption());
    clockDivRate.addActionListener(e -> setClockDivRate());
    clockDivCount.addActionListener(e -> setClockDivCount());
    populateClockDivOptions();
    updateClockOptions();

    // configure annotation options and button
    // for (String s : new String[] { ANNOTATE_SOME, ANNOTATE_ALL }) {
    //   JCheckBoxMenuItem m = new JCheckBoxMenuItem(s);
    //   m.addActionListener(ev -> setAnnotate(s));
    //   annotateItems.put(s, m);
    //   annotatePopup.add(m);
    // }
    // setAnnotate(ANNOTATE_SOME);
    annotateButton.addActionListener(e -> annotate());
    
    // configure action options
    for (String s : new String[] { HDL_GEN_ONLY, HDL_GEN_AND_DOWNLOAD, HDL_DOWNLOAD_ONLY }) {
      JCheckBoxMenuItem m = new JCheckBoxMenuItem(s);
      m.addActionListener(ev -> setAction(s));
      actionItems.put(s, m);
      actionPopup.add(m);
    }
    actionButton.addActionListener(e -> doDownloadPrep());

    // layout buttons
    JPanel buttons = new JPanel(); // default FlowLayout
    // buttons.add(new JDropdownButton(annotateButton, annotatePopup, getIcon("dropdown.png")));
    buttons.add(annotateButton);
    buttons.add(new JDropdownButton(actionButton, actionPopup, getIcon("dropdown.png")));
    buttons.add(writeToFlash);

    // layout board options
    JPanel boardOptions = new JPanel();
    boardOptions.setBorder(BorderFactory.createTitledBorder("FPGA Board Options"));
    boardOptions.setLayout(new GridBagLayout());
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets.top = c.insets.bottom = 5;
    c.insets.left = c.insets.right = 10;
    c.gridy = 0;
    c.gridx = 0;
    boardOptions.add(boardsList, c);
    c.insets.top = 0;
    c.fill = GridBagConstraints.BOTH;
    c.weightx = c.weighty = 1.0;
    c.gridy++;
    boardOptions.add(boardIcon.label, c);

    // layout clock options
    JPanel clockOptions = new JPanel();
    clockOptions.setBorder(BorderFactory.createTitledBorder("Clock Options"));
    clockOptions.setLayout(new GridBagLayout());
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = c.weighty = 0.0;
    c.insets.top = c.insets.bottom = 5;
    c.insets.left = c.insets.right = 5;
    c.gridy = 0;
    c.gridx = 0;
    clockOptions.add(clockOption, c);
    c.gridx++;
    clockOptions.add(clockDivRate, c);
    c.insets.top = 0;
    c.gridy++;
    c.gridx = 0;
    clockOptions.add(textTargetDiv, c);
    c.gridx++;
    clockOptions.add(clockDivCount, c);
    
    // configure settings button, and listen for settings changes
    settings.addSettingsListener(this);
    toolSettings.addActionListener(e -> Settings.doSettingsDialog(this));

    // configure console panels
    tabbedPane.add(messages); // tab index 0 is for console messages
    tabbedPane.setPreferredSize(new Dimension(700, 20 * Console.FONT_SIZE));

    // configure progress panel
    progressBar.setValue(0);
    progressBar.setString("Ready");
    progressBar.setStringPainted(true);
    progressBar.setEnabled(false);
    stop.addActionListener(e -> stopDownloading());
    stop.setEnabled(false);
    JPanel progressPanel = new JPanel();
    progressPanel.setLayout(new GridBagLayout());
    c.fill = GridBagConstraints.BOTH;
    c.insets.top = c.insets.bottom = 0;
    c.insets.left = c.insets.right = 0;
    c.gridx = c.gridy = 0;
    c.weightx = c.weighty = 1.0;
    progressPanel.add(progressBar, c);
    c.gridx++;
    c.insets.left = 5;
    c.weightx = c.weighty = 0.0;
    progressPanel.add(stop, c);

    configureActions();

    // layout rest of panel

    setLayout(new GridBagLayout());
    c.fill = GridBagConstraints.NONE;
    c.insets.top = c.insets.bottom = c.insets.left = c.insets.right = 0;
  
    c.insets.top = c.insets.bottom = 5;
    c.insets.left = c.insets.right = 5;
    c.gridy = 0;
    c.gridx = 0;
    c.weightx = 0.0;
    c.anchor = GridBagConstraints.EAST;
    add(textCircuit, c);
    c.insets.left = 0;
    c.gridx++;
    c.gridwidth = 2;
    c.weightx = 1.0;
    c.anchor = GridBagConstraints.WEST;
    add(circuitsList, c);
    c.gridwidth = 1;
    
    c.insets.top = c.insets.bottom = 5;
    c.insets.left = c.insets.right = 5;
    c.gridy++;
    c.gridx = 0;
    c.weightx = 0.0;
    c.anchor = GridBagConstraints.EAST;
    add(textLanguage, c);
    c.insets.left = 0;
    c.gridx++;
    c.anchor = GridBagConstraints.WEST;
    add(language, c);
    c.insets.left = 0;
    c.gridx++;
    c.weightx = 1.0;
    c.anchor = GridBagConstraints.WEST;
    add(toolSettings, c);

    c.insets.top = c.insets.bottom = 5;
    c.insets.left = c.insets.right = 5;
    c.anchor = GridBagConstraints.CENTER;
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.gridwidth = 3;
    c.gridy++;
    c.gridx = 0;
    add(clockOptions, c);
    c.gridy++;
    add(buttons, c);

    c.gridwidth = 4;
    c.gridy++;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets.top = 0;
    add(progressPanel, c);

    c.gridy++;
    c.weightx = c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    add(tabbedPane, c);
    c.gridwidth = 1;

    c.gridy = 0;
    c.gridx = 3;
    c.gridheight = 4;
    c.gridheight = 4;
    c.insets.top = c.insets.bottom = 5;
    c.insets.left = c.insets.right = 5;
    c.fill = GridBagConstraints.BOTH;
    c.weightx = c.weighty = 0.0;
    add(boardOptions, c);

    // setup
    pack();
    Dimension size = getSize();
    size.height -= 10 * Console.FONT_SIZE;
    setMinimumSize(size);
    setLocation(Projects.getCenteredLoc(getWidth(), getHeight()));

    // show window
    actionButton.requestFocus();
    setVisible(true);
  }

  public String getProjectName() {
    return proj.getLogisimFile().getName();
  }

  private void updateCircuitList() {
    circuitsList.removeAllItems();
    for (Circuit circ : proj.getLogisimFile().getCircuits())
      circuitsList.addItem(circ);
    Circuit circ = proj.getCurrentCircuit();
    if (circ == null)
      circ = proj.getLogisimFile().getMainCircuit();
    circuitsList.setSelectedItem(circ);
    circuitsList.invalidate();
  }

  private void updateClockOptions() {
    // Circuit root = circuitsList.getSelectedValue();
    // int nClocks = root.getNetlist().NumberOfClockTrees();
    // clockOption.setEnabled(nClocks > 0);
    // clockDivRate.setEnabled(nClocks > 0);
    // clockDivCount.setEnabled(nClocks > 0);
  }

  boolean updatingClockMenus = false;
  private void populateClockDivOptions() {
    updatingClockMenus = true;
    clockDivCount.removeAllItems();
    clockDivRate.removeAllItems();
    long base = board == null ? 50000000 : board.fpga.ClockFrequency;
    ArrayList<Integer> counts = new ArrayList<>();
    ArrayList<Double> freqs = new ArrayList<>();
    double ff = (double)base/2.0; // reduced speed baseline is half of actual base speed
    while (ff >= MenuSimulate.SupportedTickFrequencies[0]*2) {
      freqs.add(ff);
      ff /= 2;
    }
    for (double f : MenuSimulate.SupportedTickFrequencies)
      freqs.add(f);
    Circuit root = circuitsList.getSelectedValue();
    PinBindings.Config config = root.getFPGAConfig(settings.GetSelectedBoard());
    for (double f : freqs) {
      int count = countForFreq(base, f);
      if (counts.contains(count))
        continue;
      counts.add(count);
      String rate = rateForCount(base, count);
      clockDivCount.addItem(count);
      clockDivRate.addItem(new ExactRate(base, count));
      if (config == null 
          && Math.abs((proj.getSimulator().getTickFrequency() - f)/f) < 0.0001) {
        clockDivCount.setSelectedItem(count);
        clockDivRate.setSelectedItem(new ExactRate(base, count));
      }
    }
    if (config != null) {
      if (config.clkmode.equals("maximum")) {
        clockOption.setSelectedItem(MAX_SPEED);
      } else if (config.clkmode.equals("dynamic")) {
        clockOption.setSelectedItem(DYN_SPEED);
      } else {
        clockOption.setSelectedItem(DIV_SPEED);
        int count = config.clkdiv;
        if (!counts.contains(count)) {
          clockDivCount.addItem(count);
          clockDivRate.addItem(new ExactRate(base, count));
        }
        clockDivCount.setSelectedItem(count);
        clockDivRate.setSelectedItem(new ExactRate(base, count));
      }
    }
    if (clockDivCount.getSelectedValue() == null && clockDivCount.getItemCount() > 0)
      clockDivCount.setSelectedIndex(0);
    if (clockDivRate.getSelectedValue() == null && clockDivRate.getItemCount() > 0)
      clockDivRate.setSelectedIndex(0);

    updatingClockMenus = false;
    setClockDivCount();
    setClockDivRate();
  }

  private void setClockOption() {
    boolean div = clockOption.getSelectedValue().equals(DIV_SPEED);
    boolean max = clockOption.getSelectedValue().equals(MAX_SPEED);
    clockDivRate.setEnabled(div);
    clockDivCount.setEnabled(div);
    long base = board == null ? 50000000 : board.fpga.ClockFrequency;
    if (max) {
      clockDivRate.setSelectedItem(new ExactRate(base, 0));
      clockDivCount.setSelectedItem("undivided");
    } else if (div) {
      if (prevSelectedDivCount > 0 && prevSelectedDivRate != null) {
        clockDivCount.setSelectedItem(prevSelectedDivCount);
        clockDivRate.setSelectedItem(prevSelectedDivRate);
      } else {
        useTickSpeedFromSimulator();
      }
    } else {
      clockDivRate.setSelectedItem(new ExactRate(base, -1));
      clockDivCount.setSelectedItem("set in circuit");
    }
  }

  private int prevSelectedDivCount = 0;
  private Object prevSelectedDivRate = null;

  private static class ExactRate {
    long base;
    int count;
    String rate;
    public ExactRate(long base, int count) {
      this.base = base;
      this.count = count;
      if (count < 0)
        rate = "varies";
      else if (count == 0)
        rate = rateForFreq(base);
      else
        rate = rateForCount(base, count);
    }
    @Override
    public String toString() {
      return rate;
    }
    @Override
    public boolean equals(Object other) {
      if (other instanceof ExactRate) {
        ExactRate that = (ExactRate)other;
        return (base == that.base && count == that.count);
      }
      return false;
    }
    @Override
    public int hashCode() {
      return (int)(39 * (base + 27) + count);
    }
  }

  private void setClockDivRate() {
    if (updatingClockMenus)
      return;
    if (!clockOption.getSelectedValue().equals(DIV_SPEED))
      return;
    long base = board == null ? 50000000 : board.fpga.ClockFrequency;
    Object o = clockDivRate.getSelectedValue();
    Integer i;
    if (o instanceof ExactRate) {
      i = ((ExactRate)o).count;
    } else {
      // approximate
      i = countForRate(base, o.toString());
      if (i == null) {
        if (prevSelectedDivCount > 0 && prevSelectedDivRate != null) {
          clockDivCount.setSelectedItem(prevSelectedDivCount);
          clockDivRate.setSelectedItem(prevSelectedDivRate);
        } else {
          useTickSpeedFromSimulator();
        }
        return;
      }
      String rate = rateForCount(base, i);
      clockDivRate.setSelectedItem(rate); // rounds to nearest acceptable value
    }
    if (clockDivCount.getSelectedValue() == null || !clockDivCount.getSelectedValue().equals(i))
      clockDivCount.setSelectedItem(i);
    prevSelectedDivRate = clockDivRate.getSelectedValue();
    prevSelectedDivCount = (Integer)clockDivCount.getSelectedValue();
  }

  private void setClockDivCount() {
    if (updatingClockMenus)
      return;
    if (!clockOption.getSelectedValue().equals(DIV_SPEED))
      return;
    long base = board == null ? 50000000 : board.fpga.ClockFrequency;
    Object item = clockDivCount.getSelectedValue();
    String s = item == null ? "-1" : item.toString();
    int count = -1;
    try { count = Integer.parseInt(s); }
    catch (NumberFormatException e) { }
    if (count <= 0) {
      if (prevSelectedDivCount > 0 && prevSelectedDivRate != null) {
        clockDivCount.setSelectedItem(prevSelectedDivCount);
        clockDivRate.setSelectedItem(prevSelectedDivRate);
      } else {
        useTickSpeedFromSimulator();
      }
    } else {
      clockDivRate.setSelectedItem(new ExactRate(base, count));
      prevSelectedDivRate = clockDivRate.getSelectedValue();
      prevSelectedDivCount = count;
    }
  }

  private void useTickSpeedFromSimulator() {
    long base = board.fpga.ClockFrequency;
    for (double f : MenuSimulate.SupportedTickFrequencies) {
      int count = countForFreq(base, f);
      if (Math.abs((proj.getSimulator().getTickFrequency() - f)/f) < 0.0001) {
        clockDivCount.setSelectedItem(count);
        clockDivRate.setSelectedItem(new ExactRate(base, count));
      }
    }
  }

  private static Integer countForRate(long base, String rate) {
    rate = rate.toLowerCase().trim();
    int multiplier = 1;
    if (rate.endsWith("khz")) {
      multiplier = 1000;
      rate = rate.substring(0, rate.length() - 3);
    } else if (rate.endsWith("mhz")) {
      multiplier = 1000000;
      rate = rate.substring(0, rate.length() - 3);
    } else if (rate.endsWith("hz")) {
      multiplier = 1;
      rate = rate.substring(0, rate.length() - 2);
    }
    double freq;
    try {
      freq = Double.parseDouble(rate) * multiplier;
    } catch (NumberFormatException e) {
      return null;
    }
    if (freq <= 0)
      return null;
    return countForFreq(base, freq);
  }

  // base=50mhz, reduced=25mhz, count=1 --> 0 0 0 0 0 0 --> 25mhz = 25/1
  // base=50mhz, reduced=25mhz, count=2 --> 1 0 1 0 1 0 --> 12.5mhz = 25/2
  // base=50mhz, reduced=25mhz, count=3 --> 2 1 0 2 1 0 --> 8.3mhz = 25/3
  private static int countForFreq(long base, double freq) {
    double reduced = base / 2.0;
    long count = (long)(reduced / freq);
    if ((count > (long) 0x7FFFFFFF) | (count < 0))
      count = (long) 0x7FFFFFFF;
    else if (count == 0)
      count = 1;
    return (int)count;
  }

  private static String rateForCount(long base, int count) {
    double reduced = base / 2.0; // reduced speed baseline is half of actual base speed
    double f = reduced / count;
    return rateForFreq(f);
  }

  private static String rateForFreq(double f) {
    String suffix;
    if (f < 0.1) {
      return String.format("%g Hz", f);
    } else if (f < 1000) {
      suffix = "Hz";
    } else if (f < 1000000) {
      f /= 1000;
      suffix = "kHz";
    } else {
      suffix = "MHz";
      f /= 1000000;
    }
    if (Math.abs(f - Math.round(f)) < 0.1)
      return String.format("%.0f %s", f, suffix);
    else
      return String.format("%.2f %s", f, suffix);
  }

  private void repaintConsoles() {
    Rectangle rect = tabbedPane.getBounds();
    rect.x = 0;
    rect.y = 0;
    if (EventQueue.isDispatchThread())
      tabbedPane.paintImmediately(rect);
    else
      tabbedPane.repaint(rect);
  }

  private void clearConsoles() {
    synchronized(consoles) {
      consoles.clear();
      tabbedPane.setSelectedIndex(0);
      for (int i = tabbedPane.getTabCount() - 1; i > 0; i--)
        tabbedPane.removeTabAt(i);
    }
    fatals = errors = warns = 0;
    messages.clear();
    repaintConsoles();
  }

  public Console NewConsole(String title) {
    synchronized(consoles) {
      Console console = new Console(this, title, 1+consoles.size());
      consoles.add(console);
      tabbedPane.add(console);
      return console;
    }
  }

  private boolean settingBoard = false;
  private void setBoard() {
    if (settingBoard)
      return;
    settingBoard = true;
    Board old = board;
    String boardName = boardsList.getSelectedValue();
    if (boardName == OTHER_BOARD) {
      doLoadOtherBoard();
    } else {
      settings.SetSelectedBoard(boardName);
      settings.UpdateSettingsFile();
      board = BoardReader.read(settings.GetSelectedBoardFileName());
    }
    if (board == null || board == old) {
      // load failed, cancelled, or pointless... go back to previous selection
      board = old;
      boardsList.setSelectedIndex(boardsListSelectedIndex);
      settingBoard = false;
      configureActions();
      return;
    }
    boardsListSelectedIndex = boardsList.getSelectedIndex();
    settingBoard = false;
    boardIcon.setImage(board == null ? null : board.image);
    populateClockDivOptions();
    configureActions();
  }

  private void doLoadOtherBoard() {
    String filename = doBoardFileSelect();
    if (filename == null || filename.isEmpty())
      return; // cancelled
    board = BoardReader.read(filename);
    if (board == null)
      return; // failed to load
    if (settings.GetBoardNames().contains(board.name)) {
      eprintf("A board with the name \""+board.name+"\" already exists. "
          + "Either rename your board file, or edit Logisim's XML settings file by "
          + "hand to remove the existing board.");
      board = null;
      return;
    }
    settings.AddExternalBoard(filename);
    settings.SetSelectedBoard(board.name);
    settings.UpdateSettingsFile();
    boardsList.addItem(board.name);
    boardsList.setSelectedItem(board.name);
    boardsList.invalidate();
  }

  void repaintConsole(int idx) {
    tabbedPane.setSelectedIndex(idx);
    repaintConsoles();
  }

  private void iprintf(String msg, Object ...args) {
    messages.printf(Console.INFO, msg, args);
  }

  private void eprintf(String msg, Object ...args) {
    messages.printf(Console.ERROR, msg, args);
  }

  private void annotate() {
    // boolean clearExistingLabels = annotateItems.get(ANNOTATE_ALL).isSelected();
    clearAllMessages();
    Circuit root = circuitsList.getSelectedValue();
    if (root == null)
      return; // huh?
    root.autoHdlAnnotate(err);
    iprintf("Annotation done");
    // TODO: Fix this dirty hack; see Circuit.autoHdlAnnotate() also.
    proj.repaintCanvas();
    proj.getLogisimFile().setDirty(true);
  }

  private boolean cleanDirectory(String dirname) {
    try {
      File dir = new File(dirname);
      if (!dir.exists())
        return true;
      iprintf("Clearing " + dirname);
      for (File f : dir.listFiles()) {
        if (f.isDirectory()) {
          if (!cleanDirectory(f.getPath()))
            return false;
        } else {
          if (!f.delete()) {
            err.AddFatalError("Unable to remove old project file: %s", f.getPath());
            return false;
          }
        }
      }
      if (!dir.delete()) {
        err.AddFatalError("Unable to remove old project directory: %s", dirname);
        return false;
      }
      return true;
    } catch (Exception e) {
      err.AddFatalError("Error removing directory tree: %s\n   detail: %s", dirname,
          e.getMessage());
      return false;
    }
  }

  private void clearAllMessages() {
    messages.clear();
    repaintConsoles();
  }

  public void ClearConsole() {
    Console area;
    synchronized(consoles) {
      int i = consoles.size() - 1;
      if (i == -1) {
        return;
      }
      tabbedPane.setSelectedIndex(1 + i);
      area = consoles.get(i);
    }
    area.clear();
    repaintConsoles();
  }

  private boolean justDownload() {
    return actionItems.get(HDL_DOWNLOAD_ONLY).isSelected();
  }

  private void doDownloadPrep() {
    try {
      clearConsoles();
      if (board == null) {
        eprintf("No FPGA board is selected. Please select an FPGA board.");
        return;
      }
      Circuit root = circuitsList.getSelectedValue();
      if (root == null) {
        eprintf("INTERNAL ERROR: no circuit selected.");
        return;
      }
      String name = proj.getLogisimFile().getName();
      if (name.indexOf(" ") != -1) {
        eprintf("The project name '" + name + "' contains a space.");
        eprintf("Spaces are not permitted by the HDL synthesis engine. Please");
        eprintf("rename your files and directories to not have any spaces.");
        return;
      }
      String path = workspacePath();
      if (path == null || path.isEmpty()) {
        eprintf("The project workspace path is invalid.");
        eprintf("Please change the path in FPGA settings.");
        return;
      }
      if (path.indexOf(" ") != -1) {
        eprintf("The workspace directory '" + path + "' contains a space.");
        eprintf("Spaces are not permitted by the HDL synthesis engine. Please");
        eprintf("rename your files and directories to not have any spaces.");
        eprintf("Or, set a valid workspace path in FPGA settings.");
        return;
      }
      iprintf("Workspace directory for HDL synthesis: " + path);
      if (actionItems.get(HDL_DOWNLOAD_ONLY).isSelected()) {
        iprintf("*** NOTE *** Skipping both HDL generation and synthesis.");
        iprintf("*** NOTE *** Recent changes to circuits will not take effect.");
        doSynthesisAndDownload(null);
      } else {
        iprintf("Performing design rule checks (DRC)");
        long oscFreq = board.fpga.ClockFrequency;
        int clkPeriod = getClkPeriod();
        Netlist.Context ctx = new Netlist.Context(lang, err, board.fpga.Vendor,
            root, oscFreq, clkPeriod);
        if (!ctx.getNetlist(root).validate() || fatals > 0) {
          eprintf("DRC failed, synthesis can't continue.");
          return;
        }
        iprintf("Performing pin assignment");
        PinBindings pinBindings = performPinAssignments(ctx);
        if (pinBindings == null || fatals > 0) {
          eprintf("Pin assignment failed or is incomplete, synthesis can't continue.");
          return;
        }
        iprintf("Generating HDL files");
        if (!writeHDL(ctx, pinBindings) || fatals > 0) {
          eprintf("HDL file generation failed, synthesis can't continue.");
          return;
        }
        iprintf("HDL files are ready for synthesis.");
        if (!actionItems.get(HDL_GEN_ONLY).isSelected())
          doSynthesisAndDownload(pinBindings);
      }
    } catch (Exception e) {
      eprintf("Unexpected exception: " + e.getMessage());
      for (StackTraceElement m: e.getStackTrace())
        eprintf("%s", m.toString());
    }
  }

  private String workspacePath() {
    File projFile = proj.getLogisimFile().getLoader().getMainFile();
    return settings.GetWorkspacePath(projFile);
  }

  private String projectWorkspace() {
    return workspacePath() + SLASH
        + proj.getLogisimFile().getName() + SLASH;
  }

  private String circuitWorkspace() {
    String rootname = circuitsList.getSelectedValue().getName();
    // We use the root circuit name as part of the workspace path. This isn't
    // actually necessary, but is nice to have different top-level generations
    // in their own directories. The name needs to be filesystem-safe, but
    // doesn't really need to be unique in any way.
    String safename = rootname.replaceAll("[^a-zA-Z0-9]{1,}", "_");
    safename = safename.replaceAll("^_", "");
    safename = safename.replaceAll("_$", "");
    if (safename.equals(""))
      safename = "design_under_test";
    return projectWorkspace() + safename + SLASH;
  }

  private void doSynthesisAndDownload(PinBindings pinBindings) {
    if (board == null)
      return;
    String circdir = circuitWorkspace();
    String langdir = circdir + lang.toLowerCase() + SLASH;

    FPGADownload tools = FPGADownload.forVendor(board.fpga.Vendor);
    tools.err = err;
    tools.lang = lang;
    tools.board = board;
    tools.settings = settings;
    tools.projectPath = circdir;
    tools.circuitPath = langdir;
    tools.scriptPath = circdir + SCRIPT_DIR;
    tools.sandboxPath = circdir + SANDBOX_DIR;
    tools.ucfPath = circdir + UCF_DIR;
    tools.writeToFlash = writeToFlash.isSelected();

    if (pinBindings != null) {
      // sanity check pin bindings
      if (pinBindings == null || !pinBindings.allPinsAssigned()) {
        eprintf("Not all pins have been assigned, synthesis can't continue.");
        return;
      }
      // generate scripts and synthesize
      if (!tools.generateScripts(pinBindings) || fatals > 0) {
        eprintf("Can't generate Tool-specific download scripts");
        return;
      }
    } else {
      // don't generate or synthesize, just sanity-check that project is ready
      if (!tools.readyForDownload()) {
        eprintf("HDL files are not ready for download. "
            + "Try selecting \"Synthesize and Download\" instead.");
        return;
      }
    }
    // download
    String tool = tools.name;
    ArrayList<FPGADownload.Stage> stages = tools.initiateDownload(this);
    progressBar.setValue(0);
    progressBar.setString(String.format("(%d of %d) %s: Initializing", 0, stages.size(), tool));
    progressBar.setMaximum(stages.size());
    setDownloadMode(true);
    doDownload(tool, stages, 0);
  }

  private void setDownloadMode(boolean dl) {
    annotateButton.setEnabled(!dl);
    actionButton.setEnabled(!dl && board != null);
    writeToFlash.setEnabled(!dl && board != null && board.fpga.FlashDefined
        && !actionItems.get(HDL_GEN_ONLY).isSelected());
    boardsList.setEnabled(!dl);
    circuitsList.setEnabled(!dl);
    clockOption.setEnabled(!dl);
    clockDivRate.setEnabled(!dl);
    clockDivCount.setEnabled(!dl);
    if (!dl)
      setClockOption();
    language.setEnabled(!dl);
    toolSettings.setEnabled(!dl);

    progressBar.setEnabled(dl);
    stop.setEnabled(dl);
  }

  private FPGADownload.Stage downloader;

  public boolean confirmDownload() {
    Object[] options = { "Yes, download", "Cancel" };
    int ans = JOptionPane.showOptionDialog(
        this,
        "Verify that your FPGA board is connected and you are ready to download.",
        "Ready to download?", JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE, null, options, options[0]);
    return ans == JOptionPane.OK_OPTION;
  }

  public String chooseDevice(ArrayList<String> names) {
    String[] choices = names.toArray(new String[names.size()]);
    String choice = (String) JOptionPane.showInputDialog(this,
        "Multiple FPGA devices detected. Select one to be programmed...",
        "Select FPGA Device",
        JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
    return choice;
  }

  private void doDownload(String tool, ArrayList<FPGADownload.Stage> stages, int i) {
    downloader = null;
    int n = stages.size();
    FPGADownload.Stage prev = i == 0 ? null : stages.get(i-1);
    if (prev != null && prev.failed) {
      progressBar.setValue(i);
      if (prev.cancelled) {
        eprintf("%s download cancelled.\n", tool);
        progressBar.setString("Cancelled");
      } else {
        eprintf("%s %s failed (exit code %d)\n", tool, prev.title, prev.exitValue);
        eprintf("%s\n", prev.errmsg);
        progressBar.setString("Failed");
      }
      setDownloadMode(false);
    } else if (i >= n) {
      progressBar.setValue(n);
      progressBar.setString("Success");
      iprintf("Success");
      setDownloadMode(false);
    } else {
      downloader = stages.get(i);
      progressBar.setValue(i);
      progressBar.setString(String.format("(%d of %d) %s: %s", i, stages.size(), tool, downloader.msg));
      iprintf(tool + ": " + downloader.msg);
      downloader.console = NewConsole(downloader.title);
      downloader.startAndThen(() -> doDownload(tool, stages, i+1));
    }
  }

  public void stopDownloading() {
    if (downloader == null || downloader.thread == null)
      return;
    downloader.cancelled = true;
    downloader.thread.interrupt();
  }

  private boolean mkdirs(String dirname) {
    try {
      File dir = new File(dirname);
      if (dir.exists())
        return true;
      if (!dir.mkdirs()) {
        err.AddFatalError("Unable to create directory: %s", dirname);
        return false;
      }
      return true;
    } catch (Exception e) {
      err.AddFatalError("Error creating directory: %s\n  detail: %s", dirname, e.getMessage());
      return false;
    }
  }

  void configureActions() {
    clearConsoles();
    actionButton.setEnabled(board != null);
    if (board == null
        || (board.fpga.Vendor == Chipset.ALTERA && settings.GetAlteraToolPath() == null)
        || (board.fpga.Vendor == Chipset.XILINX && settings.GetXilinxToolPath() == null)) {
      if (board == null) {
        eprintf("Please select an FPGA board.");
      } else {
        String vendor = board.fpga.VendorName;
        eprintf("Tool path for " + vendor + " is not set correctly. "
            + "Synthesis and download will not be available. "
            + "Please set the " + vendor + " tool path "
            + "using the \"Settings\" button above, or choose "
            + "a board from a different vendor.");
        String[] toolset = board.fpga.Vendor == Chipset.ALTERA ?
            FPGADownload.ALTERA_PROGRAMS : FPGADownload.XILINX_PROGRAMS;
        iprintf("The tool path for " + vendor + " must contain these progams:\n"
            + "         " + String.join(", ", toolset));
      }
      actionItems.get(HDL_GEN_AND_DOWNLOAD).setSelected(false);
      actionItems.get(HDL_DOWNLOAD_ONLY).setSelected(false);
      actionItems.get(HDL_GEN_AND_DOWNLOAD).setEnabled(false);
      actionItems.get(HDL_DOWNLOAD_ONLY).setEnabled(false);
      setAction(HDL_GEN_ONLY);
    } else {
      board.printStats(err);
      actionItems.forEach((s, m) -> m.setEnabled(true));
      setAction(HDL_GEN_AND_DOWNLOAD);
    }
    writeToFlash.setEnabled(board != null && board.fpga.FlashDefined
        && !actionItems.get(HDL_GEN_ONLY).isSelected());
    if (!writeToFlash.isEnabled())
      writeToFlash.setSelected(false);
    if (writeToFlash.isEnabled())
      writeToFlash.setToolTipText("Download bitstream to FPGA board flash device?");
    else
      writeToFlash.setToolTipText("Selected FPGA board does not support downloading to flash device.");
  }

  // private void setAnnotate(String choice) {
  //   annotateItems.forEach((s, m) -> m.setSelected(s == choice));
  // }

  private void setAction(String choice) {
    actionItems.forEach((s, m) -> m.setSelected(s == choice));
    if (choice == HDL_GEN_ONLY)
      actionButton.setText("Generate HDL");
    else if (choice == HDL_GEN_AND_DOWNLOAD)
      actionButton.setText("Synthesize & Download");
    else
      actionButton.setText("Just Download");
  }

  private void setLang() {
    if (language.getSelectedIndex() == 0)
      lang = Settings.VHDL;
    else
      lang = Settings.VERILOG;
    if (lang.equals(settings.GetHDLType()))
      return;
    settings.SetHDLType(lang);
    settings.UpdateSettingsFile();
  }

  private PinBindings performPinAssignments(Netlist.Context ctx) {
    board.printStats(err);

    Circuit root = circuitsList.getSelectedValue();

    Netlist netlist = ctx.getNetlist(root);
    String boardname = settings.GetSelectedBoard();

    PinBindings.Config config = root.getFPGAConfig(boardname);
    PinBindings pinBindings = new PinBindings(err, board, netlist.getMappableComponents(), config);

    BindingsDialog dlg = new BindingsDialog(board, pinBindings, this);
    setVisible(false);
    dlg.setVisible(true);
    setVisible(true);
    // Save configuration for future use, even if not complete.
    String clkmode = clockOption.getSelectedValue().equals(MAX_SPEED) ? "maximum"
        : clockOption.getSelectedValue().equals(DIV_SPEED) ? "reduced"
        : "dynamic";
    int clkdiv = getClkPeriod();
    root.saveFPGAConfig(pinBindings.makeConfig(boardname, clkmode, clkdiv));
    proj.getLogisimFile().setDirty(true);
    if (pinBindings.allPinsAssigned()) {
      pinBindings.finalizeMappings();
      return pinBindings;
    }
    eprintf("Some I/O-related components have not been assigned to I/O "
        + "resources on the FPGA board. All components must be assigned.");
    return null;
  }

  @Override
  public void fpgaSettingsChanged() {
    configureActions();
  }

  private String doBoardFileSelect() {
    JFileChooser fc = new JFileChooser(workspacePath());
    FileNameExtensionFilter filter = new FileNameExtensionFilter("Board files", "xml", "xml");
    fc.setFileFilter(filter);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    File test = new File(workspacePath());
    if (test.exists()) {
      fc.setSelectedFile(test);
    }
    fc.setDialogTitle("Board description selection");
    int retval = fc.showOpenDialog(null);
    if (retval != JFileChooser.APPROVE_OPTION)
      return null;
    File file = fc.getSelectedFile();
    return file.getPath();
  }

  public void reactivate() {
    if (!isVisible()) {
      // clear();
      setVisible(true);
    }
    toFront();
  }

  private int getClkPeriod() {
    if (clockOption.getSelectedValue().equals(MAX_SPEED))
      return 0;
    else if (clockOption.getSelectedValue().equals(DYN_SPEED))
      return -1;
    Object item = clockDivCount.getSelectedValue();
    if (item == null)
      return 1;
    return Integer.parseInt(item.toString());
  }

  private boolean writeHDL(Netlist.Context ctx, PinBindings pinBindings) {
    String circdir = circuitWorkspace();
    if (!cleanDirectory(circdir))
      return false;
    for (String subdir : HDL_PATHS)
      if (!mkdirs(circdir + subdir))
        return false;

    // Generate HDL for top-level module and everything it contains, including
    // the root circuit (and all its subcircuits and components), the top-level
    // ticker (if needed), any clocks lifted to the top-level, etc.
    ToplevelHDLGenerator g = new ToplevelHDLGenerator(ctx, pinBindings);

    g.notifyNetlistReady();
    Circuit root = circuitsList.getSelectedValue();
    if (g.hdlDependsOnCircuitState()) { // for NVRAM
      CircuitState cs = getCircuitState(root);
      if (!g.writeAllHDLThatDependsOn(cs, null, null, circdir))
        return false;
    }
    return g.writeAllHDLFiles(circdir);
  }

  private CircuitState getCircuitState(Circuit circ) {
    ArrayList<CircuitState> list = new ArrayList<>();
    for (CircuitState cs : proj.getRootCircuitStates())
      if (cs.getCircuit() == circ)
        list.add(cs);
    if (list.isEmpty()) {
      err.AddSevereWarning("Circuit %s contains non-volatile RAM or other components that depend "
          + "on the current simulator state, but there are no running simulations with this "
          + "circuit as the root. These components will be initialized using default values.",
          circ.getName());
      return null;
    }
    if (list.size() > 1) {
      err.AddSevereWarning("Circuit %s contains non-volatile RAM or other components that depend "
          + "on the current simulator state, but there are multiple running simulations with this "
          + "circuit as the root. The first maching simulator state will be used for HDL synthesis.",
          circ.getName());
    }
    return list.get(0);
  }

  static ImageIcon getIcon(String name) {
    String path ="resources/logisim/icons/" + name;
    java.net.URL url = BindingsDialog.class.getClassLoader().getResource(path);
    return url == null ? null : new ImageIcon(url);
  }


}
