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

package com.cburch.logisim.gui.prefs;
import static com.cburch.logisim.gui.prefs.Strings.S;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Softwares;

public class SoftwaresOptions extends SettingsPanel {

  private class MyListener
    implements ActionListener, PreferenceChangeListener {

    @Override
    public void actionPerformed(ActionEvent ae) {
      Object source = ae.getSource();

      if (source == questaPathButton) {
        Softwares.setQuestaPath(getSettingsFrame());
      }
      if (source == questaValidationCheckBox) {
        AppPreferences.QUESTA_VALIDATION.set(questaValidationCheckBox.isSelected());
      }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent pce) {
      String property = pce.getKey();

      if (property.equals(AppPreferences.QUESTA_PATH.getIdentifier())) {
        questaPathField.setText(AppPreferences.QUESTA_PATH.get());
      }
      if (property.equals(AppPreferences.QUESTA_VALIDATION
            .getIdentifier())) {
        questaValidationCheckBox
            .setSelected(AppPreferences.QUESTA_VALIDATION.get());
      }
    }

  }

  private static final long serialVersionUID = 1L;

  private MyListener myListener = new MyListener();

  private JCheckBox questaValidationCheckBox = new JCheckBox();
  private JLabel questaPathLabel = new JLabel();
  private JTextField questaPathField = new JTextField(1);
  private JButton questaPathButton = new JButton();

  public SoftwaresOptions(SettingsFrame window) {
    super(window);

    questaValidationCheckBox.addActionListener(myListener);
    questaPathButton.addActionListener(myListener);
    AppPreferences.getPrefs().addPreferenceChangeListener(myListener);

    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    setLayout(layout);

    c.insets = new Insets(2, 4, 4, 2);
    c.anchor = GridBagConstraints.BASELINE_LEADING;

    c.fill = GridBagConstraints.HORIZONTAL;

    c.gridwidth = 3;

    c.gridx = 0; c.gridy = 0;
    add(questaValidationCheckBox, c);

    c.gridx = 0; c.gridy = 1;
    add(questaPathLabel, c);

    c.gridwidth = 1;

    c.gridx = 0; c.gridy = 3; c.weightx = 0.0;
    JPanel strut = new JPanel();
    strut.setMinimumSize(new Dimension(50, 1));
    strut.setPreferredSize(new Dimension(50, 1));
    add(strut, c);

    c.gridx = 1; c.gridy = 3; c.weightx = 1.0;
    add(questaPathField, c);

    c.gridx = 2; c.gridy = 3; c.weightx = 0.0;
    add(questaPathButton, c);

    questaValidationCheckBox.setSelected(AppPreferences.QUESTA_VALIDATION.get());
    questaPathField.setText(AppPreferences.QUESTA_PATH.get());
    questaPathField.setEditable(false);
  }

  @Override
  public String getHelpText() {
    return S.get("softwaresHelp");
  }

  @Override
  public String getTitle() {
    return S.get("softwaresTitle");
  }

  @Override
  public void localeChanged() {
    questaValidationCheckBox.setText(S.get("softwaresQuestaValidationLabel"));
    questaPathButton.setText(S.get("softwaresQuestaPathButton"));
    questaPathLabel.setText(S.get("softwaresQuestaPathLabel"));
  }

}
