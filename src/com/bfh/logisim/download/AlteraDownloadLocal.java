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

import java.io.File;
import java.util.ArrayList;

import com.bfh.logisim.gui.Commander;
import com.cburch.logisim.prefs.AppPreferences;

public class AlteraDownloadLocal extends Altera.AlteraDownload {

  public AlteraDownloadLocal() { }

  private ArrayList<String> cmd(String prog, String ...args) {
    ArrayList<String> command = new ArrayList<>();
    command.add(AppPreferences.ALTERA_PATH.get() + File.separator + prog);
    if (AppPreferences.ALTERA_64BIT.get())
      command.add("--64bit");
    for (String arg: args)
      command.add(arg);
    return command;
  }

  @Override
  public ArrayList<Stage> initiateDownload(Commander cmdr) {
    ArrayList<Stage> stages = new ArrayList<>();

    if (!readyForDownload()) {
      String script = scriptPath.replace(projectPath, ".." + File.separator) + "AlteraDownload.tcl";
      stages.add(new ProcessStage(
            "init", "Creating Quartus Project",
            cmd(Altera.ALTERA_QUARTUS_SH, "-t", script),
            "Failed to create Quartus project, cannot download"));
      stages.add(new ProcessStage(
            "optimize", "Optimizing for Minimal Area",
            cmd(Altera.ALTERA_QUARTUS_MAP, TOP_HDL, "--optimize=area"),
            "Failed to optimize design, cannot download"));
      stages.add(new ProcessStage(
            "synthesize", "Synthesizing (may take a while)",
            cmd(Altera.ALTERA_QUARTUS_SH, "--flow", "compile", TOP_HDL),
            "Failed to synthesize design, cannot download"));
      if (board.fpga.FlashDefined) { // do this even if flash wasn't requested, it's quick
        stages.add(new ProcessStage(
              "convert", "Convert JTAG bitstream (.sof) to Flash file (.pof) format",
              cmd(Altera.ALTERA_QUARTUS_CPF, "-c", "-d", board.fpga.FlashName,
                sandboxPath + TOP_HDL + ".sof",
                sandboxPath + TOP_HDL + ".pof"),
              "Failed to convert bitstream, cannot download"));
      }
    }

    if (programmer != null && !(programmer instanceof Altera.AlteraProgrammer)) {
      err.AddFatalError("Altera toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Altera programmer.");
      return stages;
    }

    stages.add(new ProcessStage(
          "scan", "Searching for FPGA Devices",
          cmd(Altera.ALTERA_QUARTUS_PGM, "--list"),
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
        System.out.println("post scan: " + ok);
        cancelled = scanWasCancelled;
        return ok;
      }
    });

    stages.add(new ProcessStage(
          "download", "Downloading to FPGA", null /* will be assigned in prep() */,
          "Failed to download design; did you connect the board?") {
      @Override
      protected boolean prep() {
        if (writeToFlash)
          cmd = cmd(Altera.ALTERA_QUARTUS_PGM, "-c", cablename, "-m", "as", "-o", "P;"+flashfile);
        else
          cmd = cmd(Altera.ALTERA_QUARTUS_PGM, "-c", cablename, "-m", "jtag", "-o", "P;"+bitfile);
        return true;
      }
    });
    return stages;
  }

}
