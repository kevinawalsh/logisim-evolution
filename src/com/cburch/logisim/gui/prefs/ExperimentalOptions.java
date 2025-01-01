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

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.cburch.logisim.prefs.AppPreferences;

class ExperimentalOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  // private JLabel autobackupRestart = new JLabel();
  private JLabel accelRestart = new JLabel();
  private PrefBoolean autobackup;
  private PrefInteger autobackupFreq;
  private PrefOptionList accel;
  // private JLabel dualScreenRestart = new JLabel();
  private PrefOptionList dualScreen;

  public ExperimentalOptions(PreferencesFrame window) {
    super(window);

    autobackup = new PrefBoolean(AppPreferences.AUTO_BACKUP, S.getter("autobackupLabel"));
    autobackupFreq = new PrefInteger(AppPreferences.AUTO_BACKUP_FREQ, 1, 60);

    JPanel autobackupSubpanel1 = new JPanel();
    autobackupSubpanel1.setLayout(new BoxLayout(autobackupSubpanel1, BoxLayout.LINE_AXIS));
    autobackupSubpanel1.add(autobackup);
    autobackupSubpanel1.add(Box.createGlue());

    JPanel autobackupSubpanel2 = new JPanel();
    autobackupSubpanel2.setLayout(new BoxLayout(autobackupSubpanel2, BoxLayout.LINE_AXIS));
    autobackupSubpanel2.add(new JLabel(S.get("autobackupFreqLabel")));
    autobackupSubpanel2.add(Box.createGlue());
    autobackupSubpanel2.add(autobackupFreq);
    autobackupFreq.setEnabled(autobackup.isSelected());
    autobackupSubpanel2.add(Box.createGlue());

    JPanel autobackupPanel3 = new JPanel();
    autobackupPanel3.setLayout(new BoxLayout(autobackupPanel3, BoxLayout.PAGE_AXIS));
    autobackupPanel3.add(autobackupSubpanel1);
    autobackupPanel3.add(autobackupSubpanel2);
    
    JPanel autobackupPanel = new JPanel();
    autobackupPanel.setLayout(new BoxLayout(autobackupPanel, BoxLayout.PAGE_AXIS));
    autobackupPanel.add(autobackupPanel3);
    // autobackupPanel.add(autobackupRestart);
    // autobackupRestart.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
    // autobackupRestart.setFont(autobackupRestart.getFont().deriveFont(Font.ITALIC));
    // autobackupRestart.setVisible(false);
    autobackup.addActionListener((ae) -> {
      // autobackupRestart.setVisible(true);
      autobackupFreq.setEnabled(autobackup.isSelected());
    });
    JPanel autobackupPanel2 = new JPanel();
    autobackupPanel2.add(autobackupPanel);

    accel = new PrefOptionList(AppPreferences.GRAPHICS_ACCELERATION,
        S.getter("accelLabel"), new PrefOption[] {
          new PrefOption(AppPreferences.ACCEL_DEFAULT,
              S.getter("accelDefault")),
          new PrefOption(AppPreferences.ACCEL_NONE,
              S.getter("accelNone")),
          new PrefOption(AppPreferences.ACCEL_METAL,
              S.getter("accelMetal")),
          new PrefOption(AppPreferences.ACCEL_OPENGL,
              S.getter("accelOpenGL")),
          new PrefOption(AppPreferences.ACCEL_D3D,
              S.getter("accelD3D")), });

    JPanel accelPanel = new JPanel(new BorderLayout());
    accelPanel.add(accel.getJLabel(), BorderLayout.LINE_START);
    accelPanel.add(accel.getJComboBox(), BorderLayout.CENTER);
    accelPanel.add(accelRestart, BorderLayout.PAGE_END);
    accelRestart.setFont(accelRestart.getFont().deriveFont(Font.ITALIC));
    accelRestart.setVisible(false);
    accel.getJComboBox().addActionListener((ae) -> accelRestart.setVisible(true));
    JPanel accelPanel2 = new JPanel();
    accelPanel2.add(accelPanel);

    dualScreen = new PrefOptionList(AppPreferences.DUALSCREEN,
        S.getter("dualScreenLabel"), new PrefOption[] {
          new PrefOption(AppPreferences.DUALSCREEN_NONE,
              S.getter("dualScreenNone")),
          new PrefOption(AppPreferences.DUALSCREEN_FIX,
              S.getter("dualScreenFix")),
          new PrefOption(AppPreferences.DUALSCREEN_MORE,
              S.getter("dualScreenMore")),
          new PrefOption(AppPreferences.DUALSCREEN_MOST,
              S.getter("dualScreenMost")), });

    JPanel dualScreenPanel = new JPanel(new BorderLayout());
    dualScreenPanel.add(dualScreen.getJLabel(), BorderLayout.LINE_START);
    dualScreenPanel.add(dualScreen.getJComboBox(), BorderLayout.CENTER);
    // dualScreenPanel.add(dualScreenRestart, BorderLayout.PAGE_END);
    // dualScreenRestart.setFont(dualScreenRestart.getFont().deriveFont(Font.ITALIC));
    // dualScreenRestart.setVisible(false);
    // dualScreen.getJComboBox().addActionListener(new ActionListener() {
    //   public void actionPerformed(ActionEvent ae) {
    //     dualScreenRestart.setVisible(true);
    //   }
    // });
    JPanel dualScreenPanel2 = new JPanel();
    dualScreenPanel2.add(dualScreenPanel);

    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    add(Box.createGlue());
    add(autobackupPanel2);
    add(accelPanel2);
    add(dualScreenPanel2);
    add(Box.createGlue());
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
    // autobackupRestart.setText(S.get("autobackupRestartLabel"));
    accelRestart.setText(S.get("accelRestartLabel"));
    dualScreen.localeChanged();
    // dualScreenRestart.setText(S.get("dualScreenRestartLabel"));
  }
}
