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

package com.bfh.logisim.settings;

import java.io.File;

import com.cburch.logisim.prefs.AppPreferences;

public class Workspace {

  private Workspace() { }

  public static String pathFor(File projectFile) {
    String p = AppPreferences.FPGA_WORKSPACE_PATH.get();
    if (p != null && !p.isEmpty())
      return p; // FIXME: create subdirectory per project?
    String home = System.getProperty("user.home");
    if (projectFile != null) {
      String dir  = projectFile.getAbsoluteFile().getParentFile().getAbsolutePath();
      String name = projectFile.getName()
          .replaceAll(".circ.xml$", "").replaceAll(".circ$", "") + "_fpga_workspace";
      return dir + File.separator + name;
    }
    return home + File.separator + "logisim_fpga_workspace" + File.separator;
  }

}
