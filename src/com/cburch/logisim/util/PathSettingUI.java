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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.cburch.logisim.util.StringGetter;

public class PathSettingUI extends JPanel {

  private static final long serialVersionUID = 1L;

  public final JTextField field = new JTextField(1);
  public final JButton button = new JButton();

  private final JFrame parent;
  private final ChangeListener listener;
  private final StringGetter btnText, dialogTitle, dialogBtnText;
  private boolean dirOnly;

  public PathSettingUI(JFrame parent, File f, 
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {
    this(parent, btnText, dialogTitle, dialogBtnText, listener);
    set(f);
  }

  public PathSettingUI(JFrame parent, String f, 
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {
    this(parent, btnText, dialogTitle, dialogBtnText, listener);
    set(f);
  }

  public PathSettingUI(JFrame parent, 
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {

    this.parent = parent;
    this.listener = listener;
    this.btnText = btnText;
    this.dialogTitle = dialogTitle;
    this.dialogBtnText = dialogBtnText;
    
    button.setText(btnText.toString());

    field.setEditable(false);
    field.addActionListener(e -> buttonClicked());

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    setLayout(gridbag);

    gbc.gridy = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 2;
    gbc.gridx = GridBagConstraints.RELATIVE;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gridbag.setConstraints(field, gbc);
    add(field);
    gbc.weightx = 0.0;
    gridbag.setConstraints(button, gbc);
    add(button);

    setPreferredSize(new Dimension(450, getMinimumSize().height));
  }

  public void set(File f) {
    try {
      field.setText(f == null ? "" : f.getCanonicalPath());
    } catch (IOException e) {
      field.setText(f.getName());
    }
  }

  public void set(String f) {
    field.setText(f);
  }

  public void setLeftMargin(int amt) {
    setBorder(BorderFactory.createEmptyBorder(0, amt, 0, 0));
  }

  public void setDirOnly(boolean t) {
    dirOnly = t;
  }

  public void localeChanged() {
    button.setText(btnText.toString());
  }

  public void buttonClicked() {
    JFileChooser chooser = JFileChoosers.create();
    if (dirOnly)
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle(dialogTitle.toString());
    chooser.setApproveButtonText(dialogBtnText.toString());
    int action = chooser.showOpenDialog(parent);
    if (action == JFileChooser.APPROVE_OPTION) {
      File file = chooser.getSelectedFile();
      listener.settingChanged(file);
    }
  }

  @FunctionalInterface
  public interface ChangeListener {
    public void settingChanged(File newFile);
  }
}
