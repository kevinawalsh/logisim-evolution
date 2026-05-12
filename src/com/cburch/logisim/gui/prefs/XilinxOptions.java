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

import com.bfh.logisim.download.Xilinx;
import com.cburch.logisim.prefs.AppPreferences;

public class XilinxOptions extends FPGAToolchainPanel {
 
  public XilinxOptions(SettingsFrame window) {
    super(window, "Xilinx ISE");

    addExplanation(
      "Xilinx ISE Design Suite is a legacy toolchain for older Xilinx FPGAs, "
      + "such as Spartan-3/6, Virtex-4/5/6, and CPLDs. The last known version is 14.7, "
      + "released in 2013, available only for Linux and Windows."
      + "To use Xilinx ISE Design Suite with Logisim, install the \"Full\" or "
      + "\"Logic Edition\" (not \"WebPack\"). For more info, search online for "
      + "\"Xilinx ISE Design Suite\" and look for the \"ISE Archive\".");

    addFileOption(AppPreferences.XILINX_PATH,
        "Xilinx ISE tool path (program or directory):",
        (path) -> validateXilinxPath(path));

    addExplanation("If the 'xst', 'ngdbuild', 'cpldfit', and other xilinx commands are "
        + "installed globally in a system directory, "
        + "leave the tool path empty. Otherwise, specify a path here. "
        + "Use 'Browse...' to select the 'xst' program executable. "
        + "Or, edit the text directly to specify either the path to the 'xst' executable, "
        + "or the directory where 'xst', 'ngdbuild', 'cpldfit', and other xilinx programs "
        + "are located. Alternatively, specify the path to a script or program: "
        + "instead of xst and the other tools, that script will be called on to do "
        + "all synthesis steps (but if needed, 'impact' will be called for the download "
        + "step, and it must be located in the same directory as the script.");
  }

  boolean validateXilinxPath(String path) {
    return path.isEmpty()
      || allToolsPresent(path, Xilinx.XILINX_PROGRAMS)
      || isExecutableScript(path);
  }

}
