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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;

import com.cburch.logisim.file.LoadCanceledByUser;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.Template;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.PathSettingUI;
import com.cburch.logisim.util.TableLayout;

class TemplateOptions extends SettingsPanel {

  private class MyListener implements ActionListener, PropertyChangeListener {

    public void actionPerformed(ActionEvent event) {
      int value = AppPreferences.TEMPLATE_UNKNOWN;
      if (plain.isSelected())
        value = AppPreferences.TEMPLATE_PLAIN;
      else if (empty.isSelected())
        value = AppPreferences.TEMPLATE_EMPTY;
      else if (custom.isSelected())
        value = AppPreferences.TEMPLATE_CUSTOM;
      AppPreferences.setTemplateType(value);
      computeEnabled();
    }

    private void templatePathChanged(File file) {
      FileInputStream reader = null;
      InputStream reader2 = null;
      try {
        Loader loader = new Loader(getSettingsFrame());
        reader = new FileInputStream(file);
        Template template = Template.create(reader);
        reader2 = template.createStream();
        LogisimFile.load(file, reader2, loader); // to see if OK
        AppPreferences.setTemplateFile(file, template); // todo: relativize ?
        AppPreferences.setTemplateType(AppPreferences.TEMPLATE_CUSTOM);
      } catch (LoadCanceledByUser ex) {
        JOptionPane.showMessageDialog(
            getSettingsFrame(),
            S.fmt("templateErrorMessage", ex.toString()),
            S.get("templateErrorTitle"),
            JOptionPane.ERROR_MESSAGE);
      } catch (IOException ex) {
        JOptionPane.showMessageDialog(
            getSettingsFrame(),
            S.fmt("templateErrorMessage", ex.toString()),
            S.get("templateErrorTitle"),
            JOptionPane.ERROR_MESSAGE);
      } finally {
        try { if (reader != null) reader.close(); }
        catch (IOException ex) { }
        try { if (reader != null) reader2.close(); }
        catch (IOException ex) { }
      }
    }

    private void computeEnabled() {
      custom.setEnabled(!path.field.getText().equals(""));
      path.field.setEnabled(custom.isSelected());
    }

    public void propertyChange(PropertyChangeEvent event) {
      String prop = event.getPropertyName();
      if (prop.equals(AppPreferences.TEMPLATE_TYPE)) {
        int value = AppPreferences.getTemplateType();
        plain.setSelected(value == AppPreferences.TEMPLATE_PLAIN);
        empty.setSelected(value == AppPreferences.TEMPLATE_EMPTY);
        custom.setSelected(value == AppPreferences.TEMPLATE_CUSTOM);
      } else if (prop.equals(AppPreferences.TEMPLATE_FILE)) {
        path.set((File) event.getNewValue());
      }
    }

  }

  private static final long serialVersionUID = 1L;

  private MyListener myListener = new MyListener();

  private JLabel templateLabel = new JLabel();
  private JRadioButton plain = new JRadioButton();
  private JRadioButton empty = new JRadioButton();
  private JRadioButton custom = new JRadioButton();
  private PathSettingUI path;

  public TemplateOptions(SettingsFrame window) {
    super(window);

    path = new PathSettingUI(window,
        AppPreferences.getTemplateFile(),
        S.getter("templateSelectButton"),
        S.getter("selectDialogTitle"),
        S.getter("selectDialogButton"),
        (f) -> myListener.templatePathChanged(f));
    path.setLeftMargin(30);

    ButtonGroup bgroup = new ButtonGroup();
    bgroup.add(plain);
    bgroup.add(empty);
    bgroup.add(custom);

    plain.addActionListener(myListener);
    empty.addActionListener(myListener);
    custom.addActionListener(myListener);
    myListener.computeEnabled();

    setLayout(new TableLayout(1));
    add(templateLabel);
    add(plain);
    add(empty);
    add(custom);
    add(path);

    AppPreferences.propertyChangeProducer.addPropertyChangeWeakListener(AppPreferences.TEMPLATE_TYPE,
        myListener);
    AppPreferences.propertyChangeProducer.addPropertyChangeWeakListener(AppPreferences.TEMPLATE_FILE,
        myListener);
    switch (AppPreferences.getTemplateType()) {
    case AppPreferences.TEMPLATE_PLAIN:
      plain.setSelected(true);
      break;
    case AppPreferences.TEMPLATE_EMPTY:
      empty.setSelected(true);
      break;
    case AppPreferences.TEMPLATE_CUSTOM:
      custom.setSelected(true);
      break;
    }
  }

  @Override
  public String getHelpText() {
    return S.get("templateHelp");
  }

  @Override
  public String getTitle() {
    return S.get("templateTitle");
  }

  @Override
  public void localeChanged() {
    templateLabel.setText(S.get("templateLabel"));
    plain.setText(S.get("templatePlainOption"));
    empty.setText(S.get("templateEmptyOption"));
    custom.setText(S.get("templateCustomOption"));
    path.localeChanged();
  }
}
