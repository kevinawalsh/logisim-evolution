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

import com.cburch.logisim.prefs.AppPreferences;

public class WorkspaceOptions extends FPGAToolchainPanel {
 
  public WorkspaceOptions(SettingsFrame window) {
    super(window, "Workspace");

    addExplanation(
      "FPGA synthesis, place-and-route, and downloading tools use a workspace "
      + "directory to hold scripts, design files, the synthesized bitstream, etc. "
      + "Logisim-HC can automatically create a temporary workspace directory "
      + "alongside each project, named '${projectname}_workspace'. Or, you "
      + "can specify a directory here to use as the workspace. "
      + "Workspace files can be large. It is safe to delete the workspace, "
      + "as it can be regenerated from your project, but synthesis takes time.");
    
    addDirOption(AppPreferences.FPGA_WORKSPACE_PATH,
        "FPGA toolchain workspace directory",
        (path) -> true);
  }

}
