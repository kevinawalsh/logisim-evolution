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

package com.cburch.logisim.gui.opts;
import static com.cburch.logisim.gui.opts.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;

import com.cburch.logisim.file.MouseMappings;
import com.cburch.logisim.gui.generic.AttrTable;
import com.cburch.logisim.gui.generic.PillLabel;
import com.cburch.logisim.gui.generic.ProjectExplorer;
import com.cburch.logisim.gui.generic.ProjectExplorerToolNode;
import com.cburch.logisim.gui.main.AttrTableToolModel;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.gui.prefs.SettingsPanel;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.MenuTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.InputEventUtil;
import com.cburch.logisim.util.TableLayout;

public class MouseOptions extends SettingsPanel {

  private class MappingsListener implements MouseMappings.MouseMappingsListener {
    @Override
    public void mouseMappingsChanged() {
      refreshMappings();
    }
  }

  private class MappingDialog extends JDialog implements ProjectExplorer.Listener {
    private static final long serialVersionUID = 1L;
    private final Integer editingKey;
    private Tool curTool = null;
    private JRadioButton btn1, btn2, btn3;
    private JCheckBox ctrlCheck, altCheck, shiftCheck, metaCheck;
    private AttrTable attrTable;

    MappingDialog(Integer editingKey) {
      super(getSettingsFrame(),
          editingKey == null ? S.get("mouseDialogAddTitle") : S.get("mouseDialogEditTitle"),
          true /* modal */);
      this.editingKey = editingKey;

      // Left: tool explorer
      ProjectExplorer explorer = new ProjectExplorer(getProject(), true);
      explorer.setListener(this);
      JScrollPane explorerPane = new JScrollPane(explorer,
          ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      explorerPane.setPreferredSize(new Dimension(200, 300));

      // Right top: mouse button radios
      btn1 = new JRadioButton(InputEventUtil.toDisplayString(InputEvent.BUTTON1_DOWN_MASK));
      btn2 = new JRadioButton(S.fmt("mouseUnavailableLabel", InputEventUtil.toDisplayString(InputEvent.BUTTON2_DOWN_MASK)));
      btn3 = new JRadioButton(InputEventUtil.toDisplayString(InputEvent.BUTTON3_DOWN_MASK));
      ButtonGroup btnGroup = new ButtonGroup();
      btnGroup.add(btn1); btnGroup.add(btn2); btnGroup.add(btn3);
      btn1.setSelected(true);

      JPanel btnPanel = new JPanel();
      btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.PAGE_AXIS));
      btnPanel.setBorder(BorderFactory.createTitledBorder(S.get("mouseButtonLabel")));
      btnPanel.add(btn1); btnPanel.add(btn2); btnPanel.add(btn3);

      // Right top: modifier checkboxes
      ctrlCheck = new JCheckBox(InputEventUtil.toDisplayString(InputEvent.CTRL_DOWN_MASK));
      altCheck = new JCheckBox(InputEventUtil.toDisplayString(InputEvent.ALT_DOWN_MASK));
      shiftCheck = new JCheckBox(InputEventUtil.toDisplayString(InputEvent.SHIFT_DOWN_MASK));
      metaCheck = new JCheckBox(InputEventUtil.toDisplayString(InputEvent.META_DOWN_MASK));

      JPanel modPanel = new JPanel();
      modPanel.setLayout(new BoxLayout(modPanel, BoxLayout.PAGE_AXIS));
      modPanel.setBorder(BorderFactory.createTitledBorder(S.get("mouseModLabel")));
      modPanel.add(ctrlCheck); modPanel.add(altCheck); modPanel.add(shiftCheck); modPanel.add(metaCheck);

      // Right bottom: tool attributes
      attrTable = new AttrTable(getSettingsFrame());
      JPanel attrWrapper = new JPanel(new BorderLayout());
      attrWrapper.setBorder(BorderFactory.createTitledBorder(S.get("mouseToolAttrsLabel")));
      attrWrapper.add(attrTable, BorderLayout.CENTER);

      JPanel optionsTop = new JPanel();
      optionsTop.setLayout(new BoxLayout(optionsTop, BoxLayout.PAGE_AXIS));
      optionsTop.add(btnPanel);
      optionsTop.add(modPanel);

      JPanel rightPanel = new JPanel(new BorderLayout());
      rightPanel.add(optionsTop, BorderLayout.NORTH);
      rightPanel.add(attrWrapper, BorderLayout.CENTER);

      JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPane, rightPanel);
      split.setDividerLocation(200);
      split.setResizeWeight(0.0);

      // Populate fields when editing an existing mapping
      if (editingKey != null) {
        int key = editingKey;
        if ((key & InputEvent.BUTTON2_DOWN_MASK) != 0) btn2.setSelected(true);
        else if ((key & InputEvent.BUTTON3_DOWN_MASK) != 0) btn3.setSelected(true);
        else btn1.setSelected(true);
        ctrlCheck.setSelected((key & InputEvent.CTRL_DOWN_MASK) != 0);
        altCheck.setSelected((key & InputEvent.ALT_DOWN_MASK) != 0);
        shiftCheck.setSelected((key & InputEvent.SHIFT_DOWN_MASK) != 0);
        curTool = getOptions().getMouseMappings().getToolFor(editingKey);
        if (curTool != null)
          explorer.setSelectedTool(curTool);
        updateAttrTable();
      }

      // Dialog buttons
      JButton okButton = new JButton(
          editingKey == null ? S.get("mouseDialogAdd") : S.get("mouseDialogSave"));
      JButton cancelButton = new JButton(S.get("mouseDialogCancel"));
      okButton.addActionListener(ae -> doOk());
      cancelButton.addActionListener(ae -> dispose());

      JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      buttonRow.add(cancelButton);
      buttonRow.add(okButton);

      getContentPane().setLayout(new BorderLayout());
      getContentPane().add(split, BorderLayout.CENTER);
      getContentPane().add(buttonRow, BorderLayout.SOUTH);
      setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
      pack();
      setLocationRelativeTo(getSettingsFrame());
    }

    private void updateAttrTable() {
      if (curTool != null && curTool.getAttributeSet() != null) {
        attrTable.setAttrTableModel(new AttrTableToolModel(getProject(), curTool));
      } else {
        attrTable.setAttrTableModel(null);
      }
    }

    private int getSelectedKey() {
      int key = 0;
      if (btn1.isSelected()) key |= InputEvent.BUTTON1_DOWN_MASK;
      else if (btn2.isSelected()) key |= InputEvent.BUTTON2_DOWN_MASK;
      else if (btn3.isSelected()) key |= InputEvent.BUTTON3_DOWN_MASK;
      if (ctrlCheck.isSelected()) key |= InputEvent.CTRL_DOWN_MASK;
      if (altCheck.isSelected()) key |= InputEvent.ALT_DOWN_MASK;
      if (shiftCheck.isSelected()) key |= InputEvent.SHIFT_DOWN_MASK;
      if (metaCheck.isSelected()) key |= InputEvent.META_DOWN_MASK;
      return key;
    }

    private void doOk() {
      if (curTool == null) return;
      int newKey = getSelectedKey();
      MouseMappings mm = getOptions().getMouseMappings();
      if (editingKey != null && editingKey.intValue() != newKey)
        mm.setToolFor(editingKey, null);
      getProject().doAction(OptionsActions.setMapping(mm, newKey, curTool.cloneTool()));
      dispose();
    }

    @Override
    public void selectionChanged(ProjectExplorer.Event event) {
      Object target = event.getTarget();
      curTool = (target instanceof ProjectExplorerToolNode)
          ? ((ProjectExplorerToolNode) target).getValue() : null;
      updateAttrTable();
    }

    @Override
    public void doubleClicked(ProjectExplorer.Event event) { }

    @Override
    public JPopupMenu menuRequested(ProjectExplorer.Event event) { return null; }
  }

  private static final Color TRIGGER_COLOR = new Color(0xcc0099);
  private static final Color ACTION_COLOR = new Color(0x6600ff);
  private static final long serialVersionUID = 1L;

  private final JLabel instruction;
  private final JButton addButton, resetButton;
  private final JPanel mappingsPanel;
  private final MappingsListener mappingsListener = new MappingsListener();

  public MouseOptions(SettingsFrame window) {
    super(window);
    setLayout(new BorderLayout());

    instruction = new JLabel();

    addButton = new JButton();
    addButton.addActionListener(ae -> showMappingDialog(null));
    resetButton = new JButton();
    resetButton.addActionListener(ae -> doReset());

    JPanel topArea = new JPanel();
    topArea.setLayout(new BoxLayout(topArea, BoxLayout.PAGE_AXIS));
    topArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    instruction.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
    topArea.add(instruction);
    JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    buttonRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
    buttonRow.add(addButton);
    buttonRow.add(resetButton);
    topArea.add(buttonRow);

    TableLayout layout = new TableLayout(4);
    mappingsPanel = new JPanel(layout);
    mappingsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    getOptions().getMouseMappings().addMouseMappingsListener(mappingsListener);
    refreshMappings();

    add(topArea, BorderLayout.NORTH);
    add(new JScrollPane(mappingsPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
  }

  private void refreshMappings() {
    mappingsPanel.removeAll();
    TableLayout layout = new TableLayout(4);
    mappingsPanel.setLayout(layout);
    JLabel trigger = new JLabel(S.get("mouseColumnTrigger")); 
    // trigger.setPreferredSize(new Dimension(120, trigger.getPreferredSize().height));
    JLabel action = new JLabel(S.get("mouseColumnAction")); 
    // action.setPreferredSize(new Dimension(120, action.getPreferredSize().height));
    mappingsPanel.add(trigger);
    mappingsPanel.add(action);
    mappingsPanel.add(new JPanel());
    mappingsPanel.add(new JPanel());
    ArrayList<Integer> keys = new ArrayList<>(getOptions().getMouseMappings().getMappedModifiers());
    Collections.sort(keys);
    for (Integer key : keys)
      addMappingRow(key);
    layout.setRowWeight(keys.size(), 1.0);
    mappingsPanel.revalidate();
    mappingsPanel.repaint();
  }

  private void addMappingRow(Integer key) {
    Tool tool = getOptions().getMouseMappings().getToolFor(key);
    String toolName = tool == null ? "" : tool.getDisplayName();
    if (tool instanceof AddTool)
      toolName = S.fmt("mouseAddTool", tool);
    JPanel trigger = new JPanel(new FlowLayout(FlowLayout.LEFT));
    int i = 0;
    for (String s : InputEventUtil.toDisplayString(key).split("\\+")) {
      if (i++ > 0) trigger.add(new JLabel("+"));
      PillLabel pill = new PillLabel(s.trim(), TRIGGER_COLOR);
      pill.setPadding(8);
      trigger.add(pill);
    }

    JPanel action = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    PillLabel pill = new PillLabel(toolName, ACTION_COLOR);
    pill.setPadding(8);
    action.add(pill);

    JButton editBtn = new JButton(S.get("mouseEditButton"));
    JButton removeBtn = new JButton(S.get("mouseRemoveButton"));
    editBtn.addActionListener(ae -> showMappingDialog(key));
    removeBtn.addActionListener(ae -> doRemove(key));
    int sq = removeBtn.getPreferredSize().height;
    mappingsPanel.add(trigger);
    mappingsPanel.add(action);
    JPanel edit = new JPanel(new FlowLayout(FlowLayout.LEFT));
    edit.add(editBtn);
    mappingsPanel.add(edit);
    JPanel rem = new JPanel(new FlowLayout(FlowLayout.LEFT));
    rem.add(removeBtn);
    mappingsPanel.add(rem);
  }

  private void showMappingDialog(Integer editingKey) {
    new MappingDialog(editingKey).setVisible(true);
  }

  private void doRemove(Integer key) {
    if (key == null) return;
    getProject().doAction(
        OptionsActions.removeMapping(getOptions().getMouseMappings(), key));
  }

  private void doReset() {
    int confirm = JOptionPane.showConfirmDialog(getSettingsFrame(),
        S.get("mouseResetConfirm"), S.get("mouseResetTitle"),
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) return;
    MouseMappings mm = getOptions().getMouseMappings();
    for (Integer k : new ArrayList<>(mm.getMappedModifiers()))
      mm.setToolFor(k, null);
    mm.setToolFor(InputEvent.CTRL_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK, MenuTool.SINGLETON);
    mm.setToolFor(InputEvent.BUTTON2_DOWN_MASK, MenuTool.SINGLETON);
    mm.setToolFor(InputEvent.BUTTON3_DOWN_MASK, MenuTool.SINGLETON);
  }

  @Override
  public String getHelpText() {
    return S.get("mouseHelp");
  }

  @Override
  public String getTitle() {
    return S.get("mouseTitle");
  }

  @Override
  public void localeChanged() {
    instruction.setText(S.get("mouseInstruction"));
    addButton.setText(S.get("mouseAddButton"));
    resetButton.setText(S.get("mouseResetButton"));
    refreshMappings();
  }
}
