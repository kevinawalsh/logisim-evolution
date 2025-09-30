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

package com.cburch.logisim.util;
import static com.cburch.logisim.file.Strings.S;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.cburch.logisim.Main;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.util.JDialogOk;

public class Errors {

  private String title, projName;
  // private String description;
  // private Throwable[] errs;

  private Errors(String title, String projName) {
    this.title = title;
    this.projName = projName;
  }

  public static Errors title(String title) {
    return new Errors(title, null);
  }

  public static Errors project(String name) {
    return new Errors(null, name);
  }

  public static Errors project(File circFile) {
    return new Errors(null, circFile == null ? null : LogisimFile.toProjectName(circFile));
  }

  public void show(String description, Throwable ...errs) {
    show(true, description, errs);
  }

  public void warn(String description, Throwable ...errs) {
    show(false, description, errs);
  }

  private void show(boolean isErr, String description, Throwable ...errs) {
    boolean multiline = description.indexOf('\n') >= 0;
    String text = description;
    if (projName != null)
      text = projName + (multiline ? ":\n" : ": ") + text;

    String title = this.title;
    if (title == null)
      title = isErr ? "Error" : "Notice";

    if (Main.headless) {
      System.err.println(title + ": " + text);
      if (errs.length > 1)
        System.err.println(errs.length + " associated errors:");
      for (Throwable t: errs) {
        //   t.printStackTrace();
        Debug.error(title, t);
      }
      return;
    }
    Icon icon = isErr
        ? UIManager.getIcon("OptionPane.errorIcon")
        : UIManager.getIcon("OptionPane.informationIcon");
    JComponent msg;
    if (multiline || text.length() > 60) {
      int lines = 1;
      for (int pos = text.indexOf('\n'); pos >= 0; pos = text.indexOf('\n', pos + 1))
        lines++;
      lines = Math.max(4, Math.min(lines, 7));

      JTextArea textArea = new JTextArea(lines, 60);
      textArea.setEditable(false);
      textArea.setText(text);
      textArea.setCaretPosition(0);

      JScrollPane scrollPane = new JScrollPane(textArea);
      Box box = Box.createHorizontalBox();
      box.add(new JLabel(icon));
      box.add(scrollPane);
      box.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

      msg = box;
    } else {
      msg = new JLabel(text, icon, SwingConstants.LEFT);
      msg.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    }
    if (errs.length > 0) {

      StringWriter errors = new StringWriter();
      PrintWriter out = new PrintWriter(errors);
      if (errs.length > 1)
        out.println(errs.length + " associated errors:");
      for (Throwable t: errs) {
        t.printStackTrace(out);
        Debug.error(title, t);
      }

      String errstr = text + "\n" + errors.toString();

      JButton copybtn = new JButton(S.get("fileErrorCopyDetail"));

      msg.setAlignmentX(0);

      JTextArea textArea = new JTextArea(errstr);
      textArea.setEditable(false);
      textArea.setCaretPosition(0);
      JScrollPane errPane = new JScrollPane(textArea);
      errPane.setAlignmentX(0);

      JButton button = new JButton(S.get("fileErrorShowDetail"));
      button.setContentAreaFilled(false);
      button.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
      button.setAlignmentX(0);

      Icon iconClosed = UIManager.getIcon("Tree.collapsedIcon");
      if (iconClosed == null) UIManager.getIcon("Tree.closedIcon");
      Icon iconOpen = UIManager.getIcon("Tree.expandedIcon");
      if (iconOpen == null) UIManager.getIcon("Tree.openIcon");
      button.setIcon(iconClosed);

      JPanel details = new JPanel();
      details.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
      GridBagLayout gb = new GridBagLayout();
      GridBagConstraints gc = new GridBagConstraints();
      details.setLayout(gb);
      gc.anchor = GridBagConstraints.NORTHWEST;
      gc.weightx = 1;
      gc.gridwidth = 2;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.gridx = gc.gridy = 0;
      gb.setConstraints(msg, gc);
      details.add(msg);

      gc.fill = GridBagConstraints.NONE;
      gc.gridwidth = 1;
      gc.weightx = 0;
      gc.gridy = 2;
      gb.setConstraints(button, gc);
      details.add(button);

      gc.gridx = 1;
      gb.setConstraints(copybtn, gc);
      details.add(copybtn);

      copybtn.addActionListener(e -> {
        StringSelection sel = new StringSelection(errstr);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
      });

      button.addActionListener(e -> {
        if (errPane.isShowing()) {
          button.setIcon(iconClosed);
          details.remove(errPane);
        } else {
          button.setIcon(iconOpen);
          gc.fill = GridBagConstraints.BOTH;
          gc.weightx = gc.weighty = 1;
          gc.gridwidth = 2;
          gc.gridx = 0;
          gc.gridy = 3;
          gb.setConstraints(errPane, gc);
          details.add(errPane);
        }
        details.revalidate();
        JDialog topFrame = (JDialog)SwingUtilities.getWindowAncestor(details);
        topFrame.pack();
        details.repaint();
      }
      );
      msg = details;
    }
    JDialog dialog = new JDialogOk(title, false) {
      public void okClicked() { }
    };
    dialog.getContentPane().add(msg);
    dialog.pack();
    dialog.setVisible(true);
  }

}
