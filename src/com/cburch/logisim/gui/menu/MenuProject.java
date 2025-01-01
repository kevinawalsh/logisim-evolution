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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.cburch.logisim.proj.Project;

class MenuProject extends Menu {
  private class MyListener implements ActionListener {
    public void actionPerformed(ActionEvent event) {
      Object src = event.getSource();
      Project proj = menubar.getSaveProject();
      if (proj == null) {
        return;
      } else if (src == loadBuiltin) {
        ProjectLibraryActions.doLoadBuiltinLibrary(proj);
      } else if (src == loadLogisim) {
        ProjectLibraryActions.doLoadLogisimLibrary(proj);
      } else if (src == loadJar) {
        ProjectLibraryActions.doLoadJarLibrary(proj);
      } else if (src == unload) {
        ProjectLibraryActions.doUnloadLibraries(proj);
      } else if (src == options) {
        proj.getOptionsFrame().setVisible(true);
      }
    }
  }

  private static final long serialVersionUID = 1L;

  private LogisimMenuBar menubar;
  private MyListener myListener = new MyListener();

  private MenuItemImpl addCircuit = new MenuItemImpl(this, LogisimMenuBar.ADD_CIRCUIT);
  private MenuItemImpl addVhdl = new MenuItemImpl(this, LogisimMenuBar.ADD_VHDL);
  private MenuItemImpl addTool = new MenuItemImpl(this, LogisimMenuBar.ADD_TOOL);
  private MenuItemImpl importVhdl = new MenuItemImpl(this, LogisimMenuBar.IMPORT_VHDL);
  private JMenu loadLibrary = new JMenu();
  private JMenuItem loadBuiltin = new JMenuItem();
  private JMenuItem loadLogisim = new JMenuItem();
  private JMenuItem loadJar = new JMenuItem();
  private JMenuItem unload = new JMenuItem();
  private MenuItemImpl moveUp = new MenuItemImpl(this, LogisimMenuBar.MOVE_TOOL_UP);
  private MenuItemImpl moveDown = new MenuItemImpl(this, LogisimMenuBar.MOVE_TOOL_DOWN);
  private MenuItemImpl remove = new MenuItemImpl(this, LogisimMenuBar.REMOVE_TOOL);
  private MenuItemImpl setAsMain = new MenuItemImpl(this, LogisimMenuBar.SET_MAIN_CIRCUIT);
  private MenuItemImpl revertAppearance = new MenuItemImpl(this, LogisimMenuBar.REVERT_APPEARANCE);
  private MenuItemImpl layout = new MenuItemImpl(this, LogisimMenuBar.EDIT_LAYOUT);
  private MenuItemImpl appearance = new MenuItemImpl(this, LogisimMenuBar.EDIT_APPEARANCE);
  private MenuItemImpl toggleLayoutAppearance = new MenuItemImpl(this, LogisimMenuBar.TOGGLE_APPEARANCE);
  private MenuItemImpl analyze = new MenuItemImpl(this, LogisimMenuBar.ANALYZE_CIRCUIT);
  private MenuItemImpl stats = new MenuItemImpl(this, LogisimMenuBar.CIRCUIT_STATS);
  private JMenuItem options = new JMenuItem();

  MenuProject(LogisimMenuBar menubar) {
    this.menubar = menubar;

    menubar.registerItem(LogisimMenuBar.ADD_CIRCUIT, addCircuit);
    menubar.registerItem(LogisimMenuBar.ADD_VHDL, addVhdl);
    menubar.registerItem(LogisimMenuBar.IMPORT_VHDL, importVhdl);
    menubar.registerItem(LogisimMenuBar.ADD_TOOL, addTool);
    loadBuiltin.addActionListener(myListener);
    loadLogisim.addActionListener(myListener);
    loadJar.addActionListener(myListener);
    unload.addActionListener(myListener);
    menubar.registerItem(LogisimMenuBar.MOVE_TOOL_UP, moveUp);
    menubar.registerItem(LogisimMenuBar.MOVE_TOOL_DOWN, moveDown);
    menubar.registerItem(LogisimMenuBar.SET_MAIN_CIRCUIT, setAsMain);
    menubar.registerItem(LogisimMenuBar.REMOVE_TOOL, remove);
    menubar.registerItem(LogisimMenuBar.REVERT_APPEARANCE, revertAppearance);
    menubar.registerItem(LogisimMenuBar.EDIT_LAYOUT, layout);
    menubar.registerItem(LogisimMenuBar.EDIT_APPEARANCE, appearance);
    menubar.registerItem(LogisimMenuBar.TOGGLE_APPEARANCE, toggleLayoutAppearance);
    menubar.registerItem(LogisimMenuBar.ANALYZE_CIRCUIT, analyze);
    menubar.registerItem(LogisimMenuBar.CIRCUIT_STATS, stats);
    options.addActionListener(myListener);

    int menuMask = getToolkit().getMenuShortcutKeyMaskEx();
    addCircuit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask
          | InputEvent.SHIFT_DOWN_MASK));

    loadLibrary.add(loadBuiltin);
    loadLibrary.add(loadLogisim);
    loadLibrary.add(loadJar);

    add(addCircuit);
    add(addVhdl);
    add(importVhdl);
    add(loadLibrary);
    add(unload);
    addSeparator();
    add(moveUp);
    add(moveDown);
    add(setAsMain);
    add(remove);
    add(revertAppearance);
    addSeparator();
    add(layout);
    add(appearance);
    addSeparator();
    add(analyze);
    add(stats);
    addSeparator();
    add(options);

    boolean known = menubar.getSaveProject() != null;
    loadLibrary.setEnabled(known);
    loadBuiltin.setEnabled(known);
    loadLogisim.setEnabled(known);
    loadJar.setEnabled(known);
    unload.setEnabled(known);
    options.setEnabled(known);
    computeEnabled();
  }

  @Override
  void computeEnabled() {
    setEnabled(menubar.getSaveProject() != null || addCircuit.hasListeners()
        || addVhdl.hasListeners() || importVhdl.hasListeners()
        || moveUp.hasListeners() || moveDown.hasListeners()
        || setAsMain.hasListeners() || remove.hasListeners()
        || layout.hasListeners() || revertAppearance.hasListeners()
        || appearance.hasListeners() || analyze.hasListeners()
        || stats.hasListeners());
    menubar.fireEnableChanged();
  }

  public void localeChanged() {
    setText(S.get("projectMenu"));
    addCircuit.setText(S.get("projectAddCircuitItem"));
    addVhdl.setText(S.get("projectAddVhdlItem"));
    importVhdl.setText(S.get("projectImportVhdlItem"));
    addTool.setText(S.get("projectAddToolItem"));
    loadLibrary.setText(S.get("projectLoadLibraryItem"));
    loadBuiltin.setText(S.get("projectLoadBuiltinItem"));
    loadLogisim.setText(S.get("projectLoadLogisimItem"));
    loadJar.setText(S.get("projectLoadJarItem"));
    unload.setText(S.get("projectUnloadLibrariesItem"));
    revertAppearance.setText(S.get("projectRevertAppearanceItem"));
    layout.setText(S.get("projectEditToolLayoutItem"));
    appearance.setText(S.get("projectEditToolAppearanceItem"));
    toggleLayoutAppearance.setText(S.get("projectToggleToolAppearanceItem"));
    analyze.setText(S.get("projectAnalyzeCircuitItem"));
    stats.setText(S.get("projectGetCircuitStatisticsItem"));
    options.setText(S.get("projectOptionsItem"));
    if (vhdlName != null) setVhdlName(vhdlName);
    else setCircuitName(circuitName);
  }

  private String circuitName, vhdlName;

  public void setCircuitName(String name) {
    circuitName = name;
    vhdlName = null;
    if (name != null) {
      moveUp.setText(S.fmt("projectMoveCircuitUpItem", name));
      moveDown.setText(S.fmt("projectMoveCircuitDownItem", name));
      setAsMain.setText(S.fmt("projectSetCircuitAsMainItem", name));
      remove.setText(S.fmt("projectRemoveNamedCircuitItem", name));
    } else {
      moveUp.setText(S.get("projectMoveToolUpItem"));
      moveDown.setText(S.get("projectMoveToolDownItem"));
      setAsMain.setText(S.get("projectSetAsMainItem"));
      remove.setText(S.get("projectRemoveToolItem"));
    }
  }

  public void setVhdlName(String name) {
    circuitName = null;
    vhdlName = name;
    if (name != null) {
      moveUp.setText(S.fmt("projectMoveVhdlUpItem", name));
      moveDown.setText(S.fmt("projectMoveVhdlDownItem", name));
      remove.setText(S.fmt("projectRemoveNamedVhdlItem", name));
    } else {
      moveUp.setText(S.get("projectMoveToolUpItem"));
      moveDown.setText(S.get("projectMoveToolDownItem"));
      remove.setText(S.get("projectRemoveToolItem"));
    }
    setAsMain.setText(S.get("projectSetAsMainItem"));
  }
}
