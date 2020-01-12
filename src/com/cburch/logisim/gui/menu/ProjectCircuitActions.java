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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.cburch.hdl.HdlModel;
import com.cburch.logisim.analyze.gui.Analyzer;
import com.cburch.logisim.analyze.gui.AnalyzerManager;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.circuit.Analyze;
import com.cburch.logisim.circuit.AnalyzeException;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;

public class ProjectCircuitActions {
  private static void analyzeError(Project proj, String message) {
    JOptionPane.showMessageDialog(proj.getFrame(), message,
        S.get("analyzeErrorTitle"), JOptionPane.ERROR_MESSAGE);
    return;
  }

  private static void configureAnalyzer(Project proj, Circuit circuit,
      Analyzer analyzer, Map<Instance, String> pinNames,
      ArrayList<Var> inputVars, ArrayList<Var> outputVars) {
    analyzer.getModel().setVariables(inputVars, outputVars);

    // If there are no inputs or outputs, we stop with that tab selected
    if (inputVars.size() == 0 || outputVars.size() == 0) {
      analyzer.setSelectedTab(Analyzer.IO_TAB);
      return;
    }

    // Attempt to show the corresponding expression
    try {
      Analyze.computeExpression(analyzer.getModel(), circuit, pinNames);
      analyzer.setSelectedTab(Analyzer.EXPRESSION_TAB);
      return;
    } catch (AnalyzeException ex) {
      JOptionPane.showMessageDialog(proj.getFrame(), ex.getMessage(),
          S.get("analyzeNoExpressionTitle"),
          JOptionPane.INFORMATION_MESSAGE);
    }

    // As a backup measure, we compute a truth table.
    Analyze.computeTable(analyzer.getModel(), proj, circuit, pinNames);
    analyzer.setSelectedTab(Analyzer.TABLE_TAB);
  }

  public static void doAddCircuit(Project proj) {
    String name = promptForCircuitName(proj.getFrame(), proj.getLogisimFile(), "");
    if (name != null) {
      Circuit circuit = new Circuit(name, proj.getLogisimFile());
      proj.doAction(LogisimFileActions.addCircuit(circuit));
      proj.setCurrentCircuit(circuit);
    }
  }

  public static void doAddVhdl(Project proj) {
    String name = promptForVhdlName(proj.getFrame(), proj.getLogisimFile(), "");
    if (name != null) {
      VhdlContent content = VhdlContent.create(name, proj.getLogisimFile());
      if (content == null)
        return;
      proj.doAction(LogisimFileActions.addVhdl(content));
      proj.setCurrentHdlModel(content);
    }
  }

  public static void doAddTool(Project proj) {
    LogisimFile file = proj.getLogisimFile();
    NameResult r = promptForNewName(proj.getFrame(), file, "", ASK_TOOL_NAME);
    String name = r.name;
    if (name == null)
      return;
    if (r.type == ASK_VHDL_NAME) {
      if (VhdlContent.labelVHDLInvalidNotify(name, file))
        return;
      VhdlContent content = VhdlContent.create(name, file);
      if (content == null)
        return;
      proj.doAction(LogisimFileActions.addVhdl(content));
      proj.setCurrentHdlModel(content);
    } else {
      Circuit circuit = new Circuit(name, file);
      proj.doAction(LogisimFileActions.addCircuit(circuit));
      proj.setCurrentCircuit(circuit);
    }
  }

  public static void doImportVhdl(Project proj) {
    String vhdl = proj.getLogisimFile().getLoader().vhdlImportChooser(proj.getFrame());
    if (vhdl == null)
      return;
    VhdlContent content = VhdlContent.parse(null, vhdl, proj.getLogisimFile());
    if (content == null)
      return;
    if (VhdlContent.labelVHDLInvalidNotify(content.getName(), proj.getLogisimFile())) {
      return;
    }
    proj.doAction(LogisimFileActions.addVhdl(content));
    proj.setCurrentHdlModel(content);
  }

  public static boolean doRename(Project proj, Circuit circuit) {
    String newName = promptForCircuitName(proj.getFrame(), proj.getLogisimFile(),
        circuit.getName());
    if (newName == null || newName.equals(circuit.getName()))
      return false;
    circuit.setCircuitName(newName);
    return true;
  }

  public static boolean doRename(Project proj, VhdlContent vhdl) {
    String newName = promptForVhdlName(proj.getFrame(), proj.getLogisimFile(),
        vhdl.getName());
    if (newName == null || newName.equals(vhdl.getName()))
      return false;
    vhdl.setName(newName);
    return true;
  }

  public static void doAnalyze(Project proj, Circuit circuit) {
    Map<Instance, String> pinNames = Analyze.getPinLabels(circuit);
    ArrayList<Var> inputVars = new ArrayList<Var>();
    ArrayList<Var> outputVars = new ArrayList<Var>();
    int numInputs = 0, numOutputs = 0;
    for (Map.Entry<Instance, String> entry : pinNames.entrySet()) {
      Instance pin = entry.getKey();
      boolean isInput = Pin.FACTORY.isInputPin(pin);
      int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      Var v = new Var(entry.getValue(), width);
      if (isInput) {
        inputVars.add(v);
        numInputs += width;
      } else {
        outputVars.add(v);
        numOutputs += width;
      }
    }
    if (numInputs > AnalyzerModel.MAX_INPUTS) {
      analyzeError(proj, S.fmt("analyzeTooManyInputsError", "" + AnalyzerModel.MAX_INPUTS,
            "" + numInputs, (numInputs <= 32 ? ""+(1L << numInputs) : "more than 4 billion")));
      return;
    }
    if (numOutputs > AnalyzerModel.MAX_OUTPUTS) {
      analyzeError(proj, S.fmt("analyzeTooManyOutputsError", "" + AnalyzerModel.MAX_OUTPUTS));
      return;
    }

    Analyzer analyzer = AnalyzerManager.getAnalyzer(proj.getFrame());
    analyzer.getModel().setCurrentCircuit(proj, circuit);
    configureAnalyzer(proj, circuit, analyzer, pinNames, inputVars, outputVars);
    if (!analyzer.isVisible())
      analyzer.setVisible(true);
    analyzer.toFront();
  }

  public static void doMoveCircuit(Project proj, Circuit circ, int delta) {
    LogisimFile file = proj.getLogisimFile();
    int oldPos = file.indexOfCircuit(circ);
    if (oldPos < 0)
      return;
    List<AddTool> tools = file.getTools();
    int newPos = oldPos + delta;
    if (newPos >= 0 && newPos < tools.size())
      proj.doAction(LogisimFileActions.moveTool(tools.get(oldPos), newPos));
  }

  public static void doMoveHdl(Project proj, HdlModel hdl, int delta) {
    if (hdl instanceof VhdlContent)
      doMoveVhdl(proj, (VhdlContent)hdl, delta);
  }

  public static void doMoveVhdl(Project proj, VhdlContent vhdl, int delta) {
    LogisimFile file = proj.getLogisimFile();
    int oldPos = file.indexOfVhdl(vhdl);
    if (oldPos < 0)
      return;
    List<AddTool> tools = file.getTools();
    int newPos = oldPos + delta;
    if (newPos >= 0 && newPos < tools.size())
      proj.doAction(LogisimFileActions.moveTool(tools.get(oldPos), newPos));
  }

  public static void doRemoveCircuit(Project proj, Circuit circuit) {
    if (proj.getLogisimFile().getCircuits().size() == 1) {
      JOptionPane.showMessageDialog(proj.getFrame(),
          S.get("circuitRemoveLastError"),
          S.get("circuitRemoveErrorTitle"),
          JOptionPane.ERROR_MESSAGE);
    } else if (!proj.getDependencies().canRemove(circuit)) {
      JOptionPane.showMessageDialog(proj.getFrame(),
          S.get("circuitRemoveUsedError"),
          S.get("circuitRemoveErrorTitle"),
          JOptionPane.ERROR_MESSAGE);
    } else {
      proj.doAction(LogisimFileActions.removeCircuit(circuit));
    }
  }

  public static void doRemoveHdl(Project proj, HdlModel hdl) {
    if (hdl instanceof VhdlContent)
      doRemoveVhdl(proj, (VhdlContent)hdl);
  }

  public static void doRemoveVhdl(Project proj, VhdlContent vhdl) {
    if (!proj.getDependencies().canRemove(vhdl)) {
      JOptionPane.showMessageDialog(proj.getFrame(),
          S.get("vhdlRemoveUsedError"),
          S.get("vhdlRemoveErrorTitle"),
          JOptionPane.ERROR_MESSAGE);
    } else {
      proj.doAction(LogisimFileActions.removeVhdl(vhdl));
    }
  }

  public static void doSetAsMainCircuit(Project proj, Circuit circuit) {
    proj.doAction(LogisimFileActions.setMainCircuit(circuit));
  }

  /**
   * Ask the user for the name of the new circuit to create. If the name is
   * valid, then it returns it, otherwise it displays an error message and
   * returns null.
   *
   * @param frame
   *            Project's frame
   * @param lib
   *            Project's logisim file
   * @param initialValue
   *            Default suggested value (can be empty if no initial value)
   */
  public static String promptForCircuitName(JFrame frame, Library lib,
      String initialValue) {
    return promptForNewName(frame, lib, initialValue, ASK_CIRCUIT_NAME).name;
  }

  public static String promptForVhdlName(JFrame frame, LogisimFile file,
      String initialValue) {
    String name = promptForNewName(frame, file, initialValue, ASK_VHDL_NAME).name;
    if (name == null || VhdlContent.labelVHDLInvalidNotify(name, file))
      return null;
    return name;
  }

  private static int ASK_CIRCUIT_NAME = 1;
  private static int ASK_VHDL_NAME = 2;
  private static int ASK_TOOL_NAME = 3;
  private static class NameResult {
    String name;
    int type;
    NameResult(int t) { type = t; }
  }

  private static NameResult promptForNewName(JFrame frame, Library lib,
      String initialValue, int type) {
    String title, prompt;
    if (type == ASK_VHDL_NAME) {
      title = S.get("vhdlNameDialogTitle");
      prompt = S.get("vhdlNamePrompt");
    } else if (type == ASK_CIRCUIT_NAME) {
      title = S.get("circuitNameDialogTitle");
      prompt = S.get("circuitNamePrompt");
    } else {
      title = S.get("toolTypeNameDialogTitle");
      prompt = S.get("circuitNamePrompt");
    }

    JLabel label = new JLabel(prompt);
    JPanel typePanel = new JPanel();
    NameResult result = new NameResult(type);
    if (type == ASK_TOOL_NAME) {
      JRadioButton circButton = new JRadioButton(S.get("toolAddDialogCircuitLabel"));
      JRadioButton vhdlButton = new JRadioButton(S.get("toolAddDialogVhdlLabel"));
      typePanel.add(circButton);
      typePanel.add(vhdlButton);
      ButtonGroup g = new ButtonGroup();
      g.add(circButton);
      g.add(vhdlButton);
      circButton.setSelected(true);
      circButton.addActionListener(e -> {
        result.type = ASK_CIRCUIT_NAME;
        label.setText(S.get("circuitNamePrompt"));
      });
      vhdlButton.addActionListener(e -> {
        result.type = ASK_VHDL_NAME;
        label.setText(S.get("vhdlNamePrompt"));
      });
    }

    final JTextField field = new JTextField(15);
    field.setText(initialValue);
    GridBagLayout gb = new GridBagLayout();
    GridBagConstraints gc = new GridBagConstraints();
    JPanel strut = new JPanel(null);
    strut.setPreferredSize(new Dimension(
          3 * field.getPreferredSize().width / 2, 0));
    JPanel panel = new JPanel(gb);
    gc.gridx = 0;
    gc.gridy = GridBagConstraints.RELATIVE;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.LINE_START;
    gb.setConstraints(typePanel, gc);
    panel.add(typePanel);
    gb.setConstraints(label, gc);
    panel.add(label);
    gb.setConstraints(field, gc);
    panel.add(field);
    gb.setConstraints(strut, gc);
    panel.add(strut);

    JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE,
        JOptionPane.OK_CANCEL_OPTION);
    pane.setInitialValue(field);
    JDialog dlog = pane.createDialog(frame, title);
    dlog.addWindowFocusListener(new WindowFocusListener() {
      public void windowGainedFocus(WindowEvent arg0) { field.requestFocus(); }
      public void windowLostFocus(WindowEvent arg0) { }
    });
    field.selectAll();

    dlog.pack();
    dlog.setVisible(true);
    field.requestFocusInWindow();
    Object action = pane.getValue();
    if (action == null || !(action instanceof Integer)
        || ((Integer) action).intValue() != JOptionPane.OK_OPTION) {
      return result;
    }

    String name = field.getText().trim();
    result.name = validateCircuitName(frame, lib, name, result.type == ASK_VHDL_NAME);
    return result;
  }

  public static String getNewNameErrors(Library lib, String name, boolean vhdl) {
    name = name.trim();
    if (name.equals(""))
      return S.get("circuitNameMissingError");
    else if (lib.getTool(name) != null)
      return S.get("circuitNameDuplicateError");
    return null;
  }

  public static String validateCircuitName(JFrame frame, Library lib, String name, boolean vhdl) {
    String error = getNewNameErrors(lib, name, vhdl);;
    if (error == null)
      return name;
    try { throw new Exception(); }
    catch (Exception e) { e.printStackTrace(); }
    String title = vhdl ? S.get("vhdlNameDialogTitle") : S.get("circuitNameDialogTitle");
    JOptionPane.showMessageDialog(frame, error, title, JOptionPane.ERROR_MESSAGE);
    return null;
  }

  private ProjectCircuitActions() { }
}
