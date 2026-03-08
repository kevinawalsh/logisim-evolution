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

import java.awt.Component;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.TableLayout;

class ExperimentalOptions extends SettingsPanel {
  private static final long serialVersionUID = 1L;
  private JLabel accelRestart = new JLabel();
  private PrefBoolean autobackup;
  private PrefInteger autobackupFreq;
  private PrefOptionList accel;
  private PrefOptionList dualScreen;

  public ExperimentalOptions(SettingsFrame window) {
    super(window);

    autobackup = new PrefBoolean(AppPreferences.AUTO_BACKUP, S.getter("autobackupLabel"));
    autobackupFreq = new PrefInteger(AppPreferences.AUTO_BACKUP_FREQ, 1, 60);
    autobackupFreq.setEnabled(autobackup.isSelected());
    autobackup.addActionListener(ae -> autobackupFreq.setEnabled(autobackup.isSelected()));

    accel = new PrefOptionList(AppPreferences.GRAPHICS_ACCELERATION,
        S.getter("accelLabel"), new PrefOption[] {
          new PrefOption(AppPreferences.ACCEL_DEFAULT, S.getter("accelDefault")),
          new PrefOption(AppPreferences.ACCEL_NONE,    S.getter("accelNone")),
          new PrefOption(AppPreferences.ACCEL_METAL,   S.getter("accelMetal")),
          new PrefOption(AppPreferences.ACCEL_OPENGL,  S.getter("accelOpenGL")),
          new PrefOption(AppPreferences.ACCEL_D3D,     S.getter("accelD3D")), });
    accelRestart.setFont(accelRestart.getFont().deriveFont(Font.ITALIC));
    accelRestart.setVisible(false);
    accel.getJComboBox().addActionListener(ae -> accelRestart.setVisible(true));

    dualScreen = new PrefOptionList(AppPreferences.DUALSCREEN,
        S.getter("dualScreenLabel"), new PrefOption[] {
          new PrefOption(AppPreferences.DUALSCREEN_NONE, S.getter("dualScreenNone")),
          new PrefOption(AppPreferences.DUALSCREEN_FIX,  S.getter("dualScreenFix")),
          new PrefOption(AppPreferences.DUALSCREEN_MORE, S.getter("dualScreenMore")),
          new PrefOption(AppPreferences.DUALSCREEN_MOST, S.getter("dualScreenMost")), });

    // Row: [checkbox "Auto-save ... frequency (min):"] [spinner]
    JPanel autobackupRow = new JPanel();
    autobackupRow.setLayout(new BoxLayout(autobackupRow, BoxLayout.LINE_AXIS));
    autobackupRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    autobackupRow.add(autobackup);
    autobackupRow.add(Box.createHorizontalStrut(4));
    autobackupRow.add(autobackupFreq);
    autobackupRow.add(Box.createHorizontalGlue());

    // Row: [label] [combo]
    JPanel accelRow = new JPanel();
    accelRow.setLayout(new BoxLayout(accelRow, BoxLayout.LINE_AXIS));
    accelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    accelRow.add(accel.getJLabel());
    accelRow.add(Box.createHorizontalStrut(8));
    accelRow.add(accel.getJComboBox());
    accelRow.add(Box.createHorizontalGlue());

    accelRestart.setAlignmentX(Component.LEFT_ALIGNMENT);

    // Row: [label] [combo]
    JPanel dualRow = new JPanel();
    dualRow.setLayout(new BoxLayout(dualRow, BoxLayout.LINE_AXIS));
    dualRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    dualRow.add(dualScreen.getJLabel());
    dualRow.add(Box.createHorizontalStrut(8));
    dualRow.add(dualScreen.getJComboBox());
    dualRow.add(Box.createHorizontalGlue());

    setLayout(new TableLayout(1));
    add(autobackupRow);
    add(accelRow);
    add(dualRow);
    add(accelRestart);
  }

  @Override
  public String getHelpText() {
    return S.get("experimentHelp");
  }

  @Override
  public String getTitle() {
    return S.get("experimentTitle");
  }

  @Override
  public void localeChanged() {
    accel.localeChanged();
    accelRestart.setText(S.get("accelRestartLabel"));
    dualScreen.localeChanged();
  }
}
