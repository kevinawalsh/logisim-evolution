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

import java.util.ArrayList;

import com.bfh.logisim.gui.Commander;
import com.cburch.logisim.prefs.AppPreferences;

// If AlteraToolPath is an executable file, rather than a directory or URL, then
// use that as a single-file script to do the entire synthesis rather than using
// the multi-step synthesis using quartus_sh, quartus_map, etc.
public class AlteraDownloadScript extends Altera.AlteraDownload {

  protected AlteraDownloadScript() { }

  private ArrayList<String> script(String ...args) {
    ArrayList<String> command = new ArrayList<>();
    command.add(AppPreferences.ALTERA_PATH.get());
    if (AppPreferences.ALTERA_64BIT.get())
      command.add("--64bit");
    for (String arg: args)
      command.add(arg);
    return command;
  }

  @Override
  public ArrayList<Stage> initiateDownload(Commander cmdr) {
    ArrayList<Stage> stages = new ArrayList<>();

    // synthesize
    if (!readyForDownload()) {
      ArrayList<String> tool;
      if (board.fpga.FlashDefined)
        tool = script("--synthesize", "--flash", board.fpga.FlashName, projectPath);
      else
        tool = script("--synthesize", projectPath);
      stages.add(new ProcessStage(
            "synthesis", "Synthesizing (may take a while)",
            tool, "Failed to synthesize design, cannot download"));
    }

    if (programmer != null && !(programmer instanceof Altera.AlteraProgrammer)) {
      err.AddFatalError("Altera toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Altera programmer.");
      return stages;
    }

    // list-cables
    ArrayList<String> scan = script("--list-cables");
    stages.add(new ProcessStage(
          "scan", "Searching for FPGA Devices",
          scan,
          "Could not find any FPGA devices. Did you connect the FPGA board?") {
      @Override
      protected boolean prep() {
        boolean ok = prepForScan(cmdr, console);
        cancelled = scanWasCancelled;
        return ok;
      }
      @Override
      protected boolean post() {
        boolean ok = postScanDetectCable(cmdr, console);
        cancelled = scanWasCancelled;
        return ok;
      }
    });

    // program
    stages.add(new ProcessStage(
          "download", "Downloading to FPGA", null /* will be assigned in prep() */,
          "Failed to download design; did you connect the board?") {
      @Override
      protected boolean prep() {
        if (writeToFlash)
          cmd = script("--program", cablename, "as", flashfile);
        else
          cmd = script("--program", cablename, "jtag", bitfile);
        return true;
      }
    });
    return stages;
  }

}
