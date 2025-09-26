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
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.DesktopIntegration;

class MenuFile extends Menu implements ActionListener {
  private static final long serialVersionUID = 1L;
  private LogisimMenuBar menubar;
  private JMenuItem newi = new JMenuItem();
  private JMenuItem open = new JMenuItem();
  private OpenRecent openRecent;
  private JMenuItem close = new JMenuItem();
  private JMenuItem save = new JMenuItem();
  private JMenuItem saveAs = new JMenuItem();
  private MenuItemImpl print = new MenuItemImpl(this, LogisimMenuBar.PRINT);
  private MenuItemImpl exportImage = new MenuItemImpl(this,
      LogisimMenuBar.EXPORT_IMAGE);
  private JMenuItem prefs = new JMenuItem();
  private JMenuItem quit = new JMenuItem();

  public MenuFile(LogisimMenuBar menubar) {
    this.menubar = menubar;
    openRecent = new OpenRecent(menubar);

    int menuMask = getToolkit().getMenuShortcutKeyMaskEx();

    newi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask));
    open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
    close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask
          | InputEvent.SHIFT_DOWN_MASK));
    save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
    saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask
          | InputEvent.SHIFT_DOWN_MASK));
    print.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask));
    prefs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask));
    quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuMask));

    add(newi);
    add(open);
    add(openRecent);
    addSeparator();
    add(close);
    add(save);
    add(saveAs);
    addSeparator();
    add(exportImage);
    add(print);
    if (!DesktopIntegration.PreferencesMenuAutomaticallyPresent) {
      addSeparator();
      add(prefs);
    }
    if (!DesktopIntegration.QuitMenuAutomaticallyPresent) {
      addSeparator();
      add(quit);
    }

    Project proj = menubar.getSaveProject();
    newi.addActionListener(this);
    open.addActionListener(this);
    if (proj == null) {
      close.setEnabled(false);
      save.setEnabled(false);
      saveAs.setEnabled(false);
    } else {
      close.addActionListener(this);
      save.addActionListener(this);
      saveAs.addActionListener(this);
    }
    menubar.registerItem(LogisimMenuBar.EXPORT_IMAGE, exportImage);
    menubar.registerItem(LogisimMenuBar.PRINT, print);
    prefs.addActionListener(this);
    quit.addActionListener(this);
  }

  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    Project proj = menubar.getSaveProject();
    Project baseProj = menubar.getBaseProject();
    Frame frame  = baseProj == null ? null : baseProj.getFrame();
    if (src == newi) {
      ProjectActions.doNew(frame);
    } else if (src == open) {
      Project newProj = ProjectActions.doOpen(frame, baseProj);
      // If the current project hasn't been touched and has no file associated
      // with it (i.e. is entirely blank), and the new file was opened
      // successfully, then go ahead and close the old blank window.
      // todo: and has no subwindows or dialogs open?
      if (newProj != null && proj != null
          && !proj.isFileDirty()
          && proj.getLogisimFile().getLoader().getMainFile() == null) {
        proj.getFrame().dispose();
      }
    } else if (src == close && proj != null) {
      int result = 0;
      if (proj.isFileDirty()) {
        /* Must use hardcoded strings here, because the string management is rotten */
        String message = "What should happen to your unsaved changes to " + proj.getLogisimFile().getName();
        String[] options = { "Save", "Discard", "Cancel" };
        result = JOptionPane.showOptionDialog(JOptionPane.getFrameForComponent(this), message, "Confirm Close", 0,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (result == 0) {
          ProjectActions.doSave(proj);
        }
      }

      /* If "cancel" pressed do nothing, otherwise dispose the window, opening one if this was the last opened window */
      if (result != 2) {
        // Get the list of open projects
        List<Project> pl = Projects.getOpenProjects();
        if (pl.size() <= 1 && !DesktopIntegration.HasWindowlessMenubar) {
          // Since we have a single window open, before closing the current
          // project open a new empty one, to avoid having no remaining windows.
          // This isn't needed if (like on MacOS) there is a menubar even when
          // there are no windows.
          ProjectActions.doNew(frame);
        }

        // Close the current project
        frame.dispose();
      }
    } else if (src == save && proj != null) {
      ProjectActions.doSave(proj);
    } else if (src == saveAs && proj != null) {
      ProjectActions.doSaveAs(proj);
    } else if (src == prefs) {
      PreferencesFrame.showPreferences();
    } else if (src == quit) {
      ProjectActions.doQuit();
    }
  }

  @Override
  void computeEnabled() {
    setEnabled(true);
    menubar.fireEnableChanged();
  }

  public void localeChanged() {
    this.setText(S.get("fileMenu"));
    newi.setText(S.get("fileNewItem"));
    open.setText(S.get("fileOpenItem"));
    openRecent.localeChanged();
    close.setText(S.get("fileCloseItem"));
    save.setText(S.get("fileSaveItem"));
    saveAs.setText(S.get("fileSaveAsItem"));
    exportImage.setText(S.get("fileExportImageItem"));
    print.setText(S.get("filePrintItem"));
    prefs.setText(S.get("filePreferencesItem"));
    quit.setText(S.get("fileQuitItem"));
  }
}
