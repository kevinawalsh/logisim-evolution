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

import java.awt.LayoutManager;

import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.proj.Project;

// Extends prefs.OptionsPanel so that both app and project panels share a
// common supertype, allowing SettingsFrame to hold both in one array.
abstract class OptionsPanel extends com.cburch.logisim.gui.prefs.OptionsPanel {
  private static final long serialVersionUID = 1L;

  public OptionsPanel(SettingsFrame frame) {
    super(frame);
  }

  public OptionsPanel(SettingsFrame frame, LayoutManager manager) {
    super(frame, manager);
  }

  Options getOptions() {
    return getSettingsFrame().getOptions();
  }

  // Alias kept for MouseOptions (passes frame as AttrTable parent window)
  SettingsFrame getOptionsFrame() {
    return getSettingsFrame();
  }

  Project getProject() {
    return getSettingsFrame().getProject();
  }
}
