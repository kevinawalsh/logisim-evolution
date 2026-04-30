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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.util.StringGetter;

public class PathSettingUI extends JPanel {

  private static final long serialVersionUID = 1L;

  private final JTextField field = new JTextField(1);
  private final JButton button = new JButton();
  private final JLabel label;

  private final JFrame parent;
  private final ChangeListener listener;
  private final StringGetter desc, btnText, dialogTitle, dialogBtnText;
  private boolean dirOnly;

  private Predicate<String> validator = null;
  private String lastCommittedPath = "";
  private Color normalBg;

  public PathSettingUI(JFrame parent, File f, StringGetter desc,
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {
    this(parent, desc, btnText, dialogTitle, dialogBtnText, listener);
    set(f);
  }

  public PathSettingUI(JFrame parent, String f, StringGetter desc,
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {
    this(parent, desc, btnText, dialogTitle, dialogBtnText, listener);
    set(f);
  }

  public PathSettingUI(JFrame parent, PrefMonitor<String> pref, StringGetter desc,
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {
    this(parent, desc, btnText, dialogTitle, dialogBtnText, listener);
    set(pref.get());
    pref.addPrefChangeWeakListener(this, e -> set(pref.get()));
  }

  public PathSettingUI(JFrame parent, StringGetter desc,
      StringGetter btnText, StringGetter dialogTitle, StringGetter dialogBtnText,
      ChangeListener listener) {

    this.parent = parent;
    this.listener = listener;
    this.desc = desc;
    this.btnText = btnText;
    this.dialogTitle = dialogTitle;
    this.dialogBtnText = dialogBtnText;

    if (desc == null)
      label = null;
    else
      label = new JLabel(desc.toString());

    button.setText(btnText.toString());

    normalBg = field.getBackground();
    field.setEditable(false);
    button.addActionListener(e -> buttonClicked());
    field.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) { textChanged(); }
      public void removeUpdate(DocumentEvent e) { textChanged(); }
      public void changedUpdate(DocumentEvent e) { textChanged(); }
    });

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
    if (label != null) {
      gridbag.setConstraints(label, gbc);
      add(label);
      gbc.gridx = GridBagConstraints.RELATIVE;
      gbc.gridy = 1;
    }
    gridbag.setConstraints(field, gbc);
    add(field);
    gbc.weightx = 0.0;
    gridbag.setConstraints(button, gbc);
    add(button);

    setPreferredSize(new Dimension(450, getMinimumSize().height));
  }

  public void setValidator(Predicate<String> v) {
    this.validator = v;
    field.setEditable(true);
    textChanged();
  }

  private void textChanged() {
    if (validator == null) return;
    String path = normalizePath(field.getText());
    boolean valid = validator.test(path);
    field.setBackground(valid ? normalBg : new Color(255, 200, 200));
    if (valid && !path.equals(lastCommittedPath)) {
      lastCommittedPath = path;
      listener.settingChanged(path);
    }
  }

  public void set(File f) {
    String path = normalizePath(f);
    if (path.equals(lastCommittedPath) && path.equals(field.getText()))
      return;
    lastCommittedPath = path; // suppress firing settingChanged
    field.setText(path);
  }

  public void set(String path) {
    path = normalizePath(path);
    if (path.equals(lastCommittedPath) && path.equals(field.getText()))
      return;
    lastCommittedPath = path; // suppress firing settingChanged
    field.setText(path);
  }

  public File getFile() {
    String path = normalizePath(field.getText());
    if (path == null || path.equals(""))
      return null;
    else
      return new File(path);
  }

  public String getPath() {
    return normalizePath(field.getText());
  }

  public void setLeftMargin(int amt) {
    setBorder(BorderFactory.createEmptyBorder(0, amt, 0, 0));
  }

  public void setDirOnly(boolean t) {
    dirOnly = t;
  }

  public void localeChanged() {
    button.setText(btnText.toString());
    if (label != null)
      label.setText(desc.toString());
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
      String path = normalizePath(file);
      if (validator != null) {
        field.setText(path);
        // textChanged() fires, validates, calls settingChanged if valid
      } else {
        listener.settingChanged(path);
      }
    }
  }

  @FunctionalInterface
  public interface ChangeListener {
    public void settingChanged(String path);
  }

  // If given a File, we normalize using getCanonicalPath(), or getPath() as a
  // fallback, and ensure the result doesn't have a trailing slash.
  public static String normalizePath(File f) {
    if (f == null)
      return "";
    String path;
    try {
      path = f.getCanonicalPath();
    } catch (IOException ex) {
      path = f.getPath();
    }
    if (path.length() > 1 && path.endsWith(File.separator))
      path = path.substring(0, path.length() - 1);
    return path;
  }

  // If given a path, we only ensure it doesn't have a trailing slash.
  public static String normalizePath(String path) {
    if (path == null)
      return "";
    if (path.length() > 1 && path.endsWith(File.separator))
      path = path.substring(0, path.length() - 1);
    return path;
  }
}
