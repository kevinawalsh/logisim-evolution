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

import javax.swing.JPanel;

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.TableLayout;

class WindowOptions extends SettingsPanel {
  private static final long serialVersionUID = 1L;
  private PrefBoolean[] checks;
  private PrefOptionList toolbarPlacement;

  public WindowOptions(SettingsFrame window) {
    super(window);

    checks = new PrefBoolean[] {
      // new PrefBoolean(AppPreferences.LAYOUT_SHOW_GRID, S.getter("windowShowGridForLayout")),
      // new PrefBoolean(AppPreferences.APPEARANCE_SHOW_GRID, S.getter("windowShowGridForAppearance")),
      new PrefBoolean(AppPreferences.PRINTER_VIEW, S.getter("layoutPrinterView")),
      new PrefBoolean(AppPreferences.SHOW_TICK_RATE, S.getter("windowTickRate")),
      new PrefBoolean(AppPreferences.SHOW_COORDS, S.getter("windowCoordinates")),
    };

    toolbarPlacement = new PrefOptionList(AppPreferences.TOOLBAR_PLACEMENT,
        S.getter("windowToolbarLocation"), new PrefOption[] {
          new PrefOption(Direction.NORTH.toString(),
              Direction.NORTH.getDisplayGetter()),
          new PrefOption(Direction.SOUTH.toString(),
              Direction.SOUTH.getDisplayGetter()),
          new PrefOption(Direction.EAST.toString(),
              Direction.EAST.getDisplayGetter()),
          new PrefOption(Direction.WEST.toString(),
              Direction.WEST.getDisplayGetter()),
          new PrefOption(AppPreferences.TOOLBAR_DOWN_MIDDLE,
              S.getter("windowToolbarDownMiddle")),
          new PrefOption(AppPreferences.TOOLBAR_HIDDEN,
              S.getter("windowToolbarHidden")) });

    JPanel panel = new JPanel(new TableLayout(2));
    panel.add(toolbarPlacement.getJLabel());
    panel.add(toolbarPlacement.getJComboBox());

    setLayout(new TableLayout(1));
    for (int i = 0; i < checks.length; i++)
      add(checks[i]);
    add(panel);
  }

  @Override
  public String getHelpText() {
    return S.get("windowHelp");
  }

  @Override
  public String getTitle() {
    return S.get("windowTitle");
  }

  @Override
  public void localeChanged() {
    for (int i = 0; i < checks.length; i++) {
      checks[i].localeChanged();
    }
    toolbarPlacement.localeChanged();
  }
}
