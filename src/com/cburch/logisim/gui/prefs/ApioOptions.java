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

import com.bfh.logisim.download.Apio;
import com.cburch.logisim.prefs.AppPreferences;

public class ApioOptions extends FPGAToolchainPanel {
 
  public ApioOptions(SettingsFrame window) {
    super(window, "Apio toolchain");

    addExplanation(
      "Apio is a free, open-source toolchain supporting (as of April 2026) "
      + "80+ FPGA boards using the ICE40, ECP5, and GOWIN FPGA architectures. "
      + "Apio provides both FPGA synthesis and FPGA downloading tools for these boards. "
      + "Or, you can use Apio synthesis together with openFPGAloader for downloading. "
      + "To use Apio with Logisim, install the Apio CLI either directly, or in a python "
      + "virtual environment (venv). For more info, see: "
      + "https://fpgawars.github.io/apio/docs/installing-apio-cli/");
    
    // FIXME: apio has multiple install methods, and venv isn't listed as an official one.
    // FIXME: backend can accept path to 'apio' itself, path to directory of 'apio',
    // or path to directory of 'bin/apio' (e.g. path to venv). But the 'Browse...' button
    // can only handle *either* directories *or* files, but not both.

    addFileOption(AppPreferences.APIO_PATH,
        "Apio tool path (program or directory):",
        (path) -> validateApioPath(path));

    addExplanation("If the 'apio' command is installed globally in a system directory, "
        + "leave the tool path empty. Otherwise, you must specify a path here. "
        + "Use 'Browse...' to select the 'apio' program executable. "
        + "Or, edit the text directly to specify either the path to the 'apio' executable, "
        + "the directory where 'apio' program is located, or the "
        + "python virtual environment (venv) where 'bin/apio' can be found.");
  }

  boolean validateApioPath(String path) {
    // TODO: show version info or error message in dialog
    return path.isEmpty() || Apio.TOOLCHAIN.toolchainInstallStatus(path).installed();
  }

}
