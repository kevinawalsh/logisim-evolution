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

package com.bfh.logisim.menu;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.bfh.logisim.fpga.BoardEditor;
import com.bfh.logisim.gui.Commander;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.proj.Project;

public class MenuFPGA extends JMenu implements ActionListener {
	private Project proj;
	private JMenuItem editorMenu = new JMenuItem();
	private JMenuItem commanderMenu = new JMenuItem();
	private JMenuItem settingsMenu = new JMenuItem();
	private BoardEditor editor = null;
	private Commander commander = null;
  private JFrame parent;

	public MenuFPGA(JFrame parent, LogisimMenuBar menubar, Project proj) {
    this.parent = parent;
		this.proj = proj;

		editorMenu.addActionListener(this);
		commanderMenu.addActionListener(this);
		settingsMenu.addActionListener(this);

    int menuMask = getToolkit().getMenuShortcutKeyMaskEx();
    commanderMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, menuMask));

		add(editorMenu);
		add(commanderMenu);
		add(settingsMenu);
    boolean hasProject = parent instanceof Frame;
    commanderMenu.setEnabled(hasProject);
	}

	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == editorMenu) {
			if (editor == null)
				editor = new BoardEditor();
			else
        editor.reactivate();
		} else if (src == commanderMenu) {
			if (commander == null)
				commander = new Commander(proj);
      else
        commander.reactivate();
		} else if (src == settingsMenu) {
      SettingsFrame.showFPGASettings();
    }
	}

	public void localeChanged() {
		this.setText(Strings.get("FPGAMenu"));
		editorMenu.setText(Strings.get("FPGABoardEditor"));
		commanderMenu.setText(Strings.get("FPGACommander"));
		settingsMenu.setText(Strings.get("FPGASettings"));
	}
}
