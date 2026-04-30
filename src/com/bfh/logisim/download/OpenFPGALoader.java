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

package com.bfh.logisim.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class OpenFPGALoader {

  private OpenFPGALoader() { }

  public static ArrayList<String> commandFor(Board board, String bin) {
    // FIXME: if openFPGALoader_name is unset, fall back to... name?
    ArrayList<String> cmd = new ArrayList<>();
    cmd.add(bin);
    cmd.add("--verify");
    cmd.add("-b");
    cmd.add(board.openFPGALoader_name);
    cmd.add("hardware.bin");
    return cmd;
  }
  
  private static final String helpmsg =
    "Either install openFPGALoader to a system directory, or set "
    + "the toolchain path to point to the 'openFPGALoader' executable or a "
    + "directory (e.g. a python virtualenv) containing it.";

  private static String getVersion(String cmd) {
    try {
      Process process = Runtime.getRuntime().exec(cmd + " --Version");
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      String line = reader.readLine();
      if (line.toLowerCase().startsWith("openfpgaloader "))
        return line.substring("openfpgaloader ".length());
    } catch (Exception e) {
    }
    return null;
  }

  public static String findExecutable(FPGAReport err) {
    String tool = AppPreferences.OPENFPGALOADER_PATH.get();
    // user wants system openFPGALoader
    if (tool == null || tool.isEmpty()) {
      String version = getVersion("openFPGALoader");
      if (version != null) {
        err.AddInfo("Using system installed openFPGALoader, " + version);
        return "openFPGALoader";
      }
      err.AddSevereWarning("openFPGALoader toolchain path is not configured, and openFPGALoader"
          + " does not appear to be installed in a system directory. " + helpmsg);
      return null;
    }
    // user wants custom openFPGALoader
    String prog = findExecutable(tool);
    if (prog != null && !prog.isEmpty()) {
      String version = getVersion(prog);
      if (version != null) {
        err.AddInfo("Using " + prog + ", " + version);
        return prog;
      }
      err.AddSevereWarning("openFPGALoader path is set to '" + tool + "', but "
          + " `openFPGALoader --Version` still failed. " + helpmsg);
      return null;
    }
    return null;
  }

  public static String findExecutable(String p) {
    if (p != null && !p.isEmpty()) {
      File script = new File(p);
      if (script.exists() && !script.isDirectory() && script.canExecute())
        return p;
      if (script.exists() && script.isDirectory()) {
        String pp = p + "/openFPGALoader";
        script = new File(pp);
        if (script.exists() && !script.isDirectory() && script.canExecute())
          return pp;
      }
      return null;
    }
    // Try just using "openFPGALoader", hope it is found on system path?
    return "openFPGALoader";
  }

}
