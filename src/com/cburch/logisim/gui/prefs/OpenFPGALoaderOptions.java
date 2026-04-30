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

import com.bfh.logisim.download.OpenFPGALoader;
import com.cburch.logisim.prefs.AppPreferences;

public class OpenFPGALoaderOptions extends FPGAToolchainPanel {
 
  public OpenFPGALoaderOptions(SettingsFrame window) {
    super(window, "openFPGALoader programmer");

    addExplanation(
      "OpenFPGALoader is a free, open-source utility for programming FPGAs, "
      + "\"downloading\" the synthesized bitstream to the physical device. "
      + "It supports many cables and FPGA devices, including many from "
      + "Xilinx, Altera/Intel, Lattice, Gowin, Efinix, Anlogic, Cologne Chip. It works on "
      + "Linux, Windows and macOS. In Logisim-HC, OpenFPGALoader can be used for download "
      + "after synthesizing with Apio or Gowin, e.g. in situations where those toolcahins"
      + "alone are not sufficient. "
      + "To use OpenFPGALoader with Logisim, install it directly, and ensure you have "
      + "any necessary udev rules (on Linux) or usb drivers (on Windows). "
      + "For more info, see: "
      + "https://trabucayre.github.io/openFPGALoader/guide/install.html");
    
    addFileOption(AppPreferences.OPENFPGALOADER_PATH,
        "openFPGALoader tool path (program or directory):",
        (path) -> validateOpenFPGALoaderPath(path));

    addExplanation("If the 'openFPGALoader' command is installed globally in a system directory, "
        + "leave the tool path empty. Otherwise, you must specify a path here. "
        + "Use 'Browse...' to select the 'openFPGALoader' program executable. "
        + "Or, edit the text directly to specify either the path to the 'openFPGALoader' executable, "
        + "the directory where that program can be found.");
  }

  boolean validateOpenFPGALoaderPath(String path) {
    return path.isEmpty() || OpenFPGALoader.findExecutable(path) != null;
  }

}
