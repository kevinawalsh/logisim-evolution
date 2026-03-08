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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.PathSettingUI;
import com.cburch.logisim.util.Softwares;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.TableLayout;

public class SoftwaresOptions extends SettingsPanel {
  
  private static final long serialVersionUID = 1L;
  
  private PrefBoolean questaEnabled = new PrefBoolean(
      AppPreferences.QUESTA_VALIDATION,
      S.getter("softwaresQuestaValidationLabel"));
  private PathSettingUI questaPath;

  private static final StringGetter dlgTitle = 
    com.cburch.logisim.util.Strings.S.getter("questaDialogTitle");

  private static final StringGetter dlgBtnText = 
    com.cburch.logisim.util.Strings.S.getter("questaDialogButton");

  private PropertyChangeListener prefListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      questaPath.set(AppPreferences.QUESTA_PATH.get());
    }
  };

  public SoftwaresOptions(SettingsFrame window) {
    super(window);

    questaPath = new PathSettingUI(window,
        AppPreferences.QUESTA_PATH.get(),
        S.getter("softwaresQuestaPathButton"),
        dlgTitle, dlgBtnText,
        (f) -> Softwares.setQuestaPath(f));
    questaPath.setLeftMargin(30);

    AppPreferences.QUESTA_PATH.addPropertyChangeWeakListener(prefListener);

    setLayout(new TableLayout(1));
    add(questaEnabled);
    add(questaPath);
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
    questaPath.localeChanged();
  }

}
