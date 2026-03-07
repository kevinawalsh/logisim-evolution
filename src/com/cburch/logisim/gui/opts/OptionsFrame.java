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

package com.cburch.logisim.gui.opts;

import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.proj.Project;

// Thin delegate — all UI is now in SettingsFrame.
// Kept so that Project.getOptionsFrame() and callers (MenuProject, Popups)
// continue to work without changes.
public class OptionsFrame extends LFrame.Dialog {

  private static final long serialVersionUID = 1L;

  public OptionsFrame(Project project) {
    super(project);
  }

  public Options getOptions() {
    return getProject().getLogisimFile().getOptions();
  }

  @Override
  public void setVisible(boolean value) {
    if (value)
      SettingsFrame.showProjectSettings(getProject());
    // Never actually show this window.
  }

  public void showToolbarPanel() {
    SettingsFrame.showProjectPanel(getProject(), 1); // index 1 = ToolbarOptions
  }
}
