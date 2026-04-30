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

import com.bfh.logisim.download.FPGADownload;
import com.cburch.logisim.prefs.AppPreferences;

public class GowinOptions extends FPGAToolchainPanel {
 
  public GowinOptions(SettingsFrame window) {
    super(window, "Gowin EDA");

    addExplanation(
      "Gowin EDA is a toolchain for Gowin FPGAs, including GW1N and GW2A chips"
      + "used on the low-cost Tang Nano and Tang Nano 9K boards. It is available "
      + "for Windows and Linux (as of April 2026). It includes synthesis and "
      + "place-and-route tools, and includes a programmer tool for downloading "
      + "synthesized bitstreams to the FPGA. Alternatively, downloading can be "
      + "done using openFPGALoader for some boards. Registration is required to "
      + "obtain a free software license for Gowin EDA.");

    addFileOption(AppPreferences.GOWIN_SHELL_PATH,
        "Gowin shell tool path (required):",
        (path) -> validateExecutable(path));

    addFileOption(AppPreferences.GOWIN_PROGRAMMER_PATH,
        "Gowin programmer tool path (optional):",
        (path) -> validateExecutable(path));

    addExplanation(
        "For the shell tool, select the path to the 'gw_sh' or 'gw_sh.exe' program, "
        + "or to a custom script or program that will do full synthesis and "
        + "place-and-route."
        + "For the programmer, select the path to the 'programmer_cli', 'programmer.exe', "
        + "or similar program for the downloading step. If left blank, openFPGALoader "
        + "will be tried instead.");
  }

  boolean validateExecutable(String path) {
    return path.isEmpty() || isExecutableScript(path);
  }

}
