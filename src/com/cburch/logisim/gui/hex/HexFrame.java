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

package com.cburch.logisim.gui.hex;
import static com.cburch.logisim.gui.hex.Strings.S;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.cburch.hex.HexEditor;
import com.cburch.hex.HexModelListener;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.std.memory.RomContents;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;

public class HexFrame extends LFrame.SubWindow {
  private class EditListener implements ActionListener, ChangeListener {
    private Clip clip = null;

    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      if (src == LogisimMenuBar.CUT) {
        getClip().copy();
        editor.delete();
      } else if (src == LogisimMenuBar.COPY) {
        getClip().copy();
      } else if (src == LogisimMenuBar.PASTE) {
        getClip().paste();
      } else if (src == LogisimMenuBar.DELETE) {
        // This only gets menu activations; keyboard delete is handled by
        // HexEditor's Caret.keyPressed() before triggering the menu
        // accelerator.
        editor.delete();
      } else if (src == LogisimMenuBar.SELECT_ALL) {
        editor.selectAll();
      } else if (src == LogisimMenuBar.UNDO) {
        Action last = project.getLastAction();
        project.undoAction();
        updateAfter(last);
      } else if (src == LogisimMenuBar.UNDO) {
        Action next = project.getLastRedoAction();
        project.redoAction();
        updateAfter(next);
      }
    }

    void updateAfter(Action act) {
      enableItems((LogisimMenuBar) getJMenuBar());
      if (act == null)
        return;
      RomContents hex = RomContents.forAction(act);
      if (hex == null) // Unrelated to any Rom
        project.getFrame().toFront();
      else if (hex != model) // Related to some other Rom
        hex.raiseHexFrameOrProject();
    }

    private void enableItems(LogisimMenuBar menubar) {
      boolean sel = editor.selectionExists();
      boolean clip = true; // TODO editor.clipboardExists();
      menubar.setEnabled(LogisimMenuBar.CUT, sel);
      menubar.setEnabled(LogisimMenuBar.COPY, sel);
      menubar.setEnabled(LogisimMenuBar.PASTE, clip);
      menubar.setEnabled(LogisimMenuBar.DELETE, sel);
      menubar.setEnabled(LogisimMenuBar.SELECT_ALL, true);

      LocaleManager mainS = com.cburch.logisim.gui.menu.Strings.S;

      Action last = project == null ? null : project.getLastAction();
      menubar.setEnabled(LogisimMenuBar.UNDO, last != null);
      if (last == null)
        menubar.setText(LogisimMenuBar.UNDO, mainS.get("editCantUndoItem"));
      else
        menubar.setText(LogisimMenuBar.UNDO, mainS.fmt("editUndoItem", last.getName()));

      Action next = project == null ? null : project.getLastRedoAction();
      menubar.setEnabled(LogisimMenuBar.REDO, next != null);
      if (next == null)
        menubar.setText(LogisimMenuBar.REDO, mainS.get("editCantRedoItem"));
      else
        menubar.setText(LogisimMenuBar.REDO, mainS.fmt("editRedoItem", next.getName()));
    }

    private Clip getClip() {
      if (clip == null)
        clip = new Clip(editor);
      return clip;
    }

    private void register(LogisimMenuBar menubar) {
      menubar.addActionListener(LogisimMenuBar.UNDO, this);
      menubar.addActionListener(LogisimMenuBar.REDO, this);
      menubar.addActionListener(LogisimMenuBar.CUT, this);
      menubar.addActionListener(LogisimMenuBar.COPY, this);
      menubar.addActionListener(LogisimMenuBar.PASTE, this);
      menubar.addActionListener(LogisimMenuBar.DELETE, this);
      menubar.addActionListener(LogisimMenuBar.SELECT_ALL, this);
      enableItems(menubar);
    }

    public void stateChanged(ChangeEvent e) {
      enableItems((LogisimMenuBar) getJMenuBar());
    }
  }

  private class MyListener implements ActionListener, LocaleListener {
    public void actionPerformed(ActionEvent event) {
      Object src = event.getSource();
      if (src == open) {
        HexFile.open(model, HexFrame.this, project, instance);
      } else if (src == save) {
        HexFile.save(model, HexFrame.this, project, instance);
      } else if (src == close) {
        WindowEvent e = new WindowEvent(HexFrame.this, WindowEvent.WINDOW_CLOSING);
        HexFrame.this.processWindowEvent(e);
      }
    }

    public void localeChanged() {
      setTitle(S.get("hexFrameTitle"));
      open.setText(S.get("openButton"));
      save.setText(S.get("saveButton"));
      close.setText(S.get("closeButton"));
    }
  }

  public void closeAndDispose() {
      WindowEvent e = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
      processWindowEvent(e);
      dispose();
  }

  private class WindowMenuManager extends WindowMenuItemManager
    implements LocaleListener {
    WindowMenuManager() {
      super(S.get("hexFrameMenuItem"), false);
      LocaleManager.addLocaleListener(this);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return HexFrame.this;
    }

    public void localeChanged() {
      setText(S.get("hexFrameMenuItem"));
    }
  }

  private static final long serialVersionUID = 1L;

  private WindowMenuManager windowManager = new WindowMenuManager();
  private EditListener editListener = new EditListener();
  private MyListener myListener = new MyListener();
  private MemContents model;
  private HexEditor editor;
  private JButton open = new JButton();
  private JButton save = new JButton();
  private JButton close = new JButton();
  private Instance instance;

  public HexFrame(Project project, Instance instance, MemContents model) {
    super(project);
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    this.model = model;
    this.editor = new HexEditor(model);
    this.instance = instance; // only for access to recent file

    JPanel buttonPanel = new JPanel();
    buttonPanel.add(open);
    buttonPanel.add(save);
    buttonPanel.add(close);
    open.addActionListener(myListener);
    save.addActionListener(myListener);
    close.addActionListener(myListener);

    Dimension pref = editor.getPreferredSize();
    JScrollPane scroll = new JScrollPane(editor,
        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    pref.height = Math.min(pref.height, pref.width * 3 / 2);
    pref.height = Math.max(pref.height, 100);
    scroll.setPreferredSize(pref);
    scroll.getViewport().setBackground(editor.getBackground());

    Container contents = getContentPane();
    contents.add(scroll, BorderLayout.CENTER);
    contents.add(buttonPanel, BorderLayout.SOUTH);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();

    Dimension size = getSize();
    Dimension screen = getToolkit().getScreenSize();
    if (size.width > screen.width || size.height > screen.height) {
      size.width = Math.min(size.width, screen.width);
      size.height = Math.min(size.height, screen.height);
      setSize(size);
    }

    editor.getCaret().addChangeListener(editListener);
    editor.getCaret().setDot(0, false);
    editListener.register(menubar);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        editListener.enableItems((LogisimMenuBar) getJMenuBar());
      }
    });

    setLocationRelativeTo(project.getFrame());
  }

  public HexModelListener getListener() {
    return editor.getListener();
  }

  @Override
  public void setVisible(boolean value) {
    if (value && !isVisible()) {
      windowManager.frameOpened(this);
    }
    super.setVisible(value);
  }

  @Override
  public void dispose() {
    model.clearHexFrameRef(this);
    super.dispose();
  }
}
