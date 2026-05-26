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
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class AlteraSynthesizeRemote extends Altera.AlteraSynthesize {

  protected AlteraSynthesizeRemote(FPGAReport err) {
    super(err);
    supportsRemoteJTAG = true;
  }

  @Override
  public boolean readyForDownload() {
    if (remoteJTAG)
      return super.readyForDownload();
    String fmt = AppPreferences.ALTERA_FORMAT.get();
    return new File(sandboxPath + TOP_HDL + "." + fmt).exists();
  }

  String url, urlbase, urldir, zipname, bitstreamzip, use64bit;

  @Override
  public boolean createSynthesisPlan(ArrayList<Stage> stages) {

    //  example: http://some.server.org/home/quartus/synthesize.php
    url = AppPreferences.ALTERA_PATH.get();
    //  example: http://some.server.org/
    urlbase = url.substring(0, url.indexOf('/', 8)+1);
    //  example: http://some.server.org/home/quartus/
    urldir = url.substring(0, url.lastIndexOf('/')+1);

    //  example: /tmp/project_fpga_workspace/foo/bar.zip
    zipname = projectPath.substring(0, projectPath.length()-1) + ".zip";
    //  example: /tmp/project_fpga_workspace/foo/bar_bitstream.zip
    bitstreamzip = projectPath.substring(0, projectPath.length()-1) + "_bitstream.zip";

    use64bit = AppPreferences.ALTERA_64BIT.get() ? "1" : "0";

    stages.add(new RunnableStage(
          "synthesis", "Synthesizing (may take a while)",
          "Failed to synthesize design, cannot download") {
      @Override
      protected boolean run() {
        if (writeToFlash && !remoteJTAG) {
          console.printf(console.ERROR, "SPI Flash not yet supported with openFPGAloader.");
          console.printf(console.ERROR, "Uncheck the 'Use Flash?' option or the 'remote JTAG' option.");
          return false;
        }
        console.printf("Compressing project before upload to " + url);

        if (!Zip.compress(console, zipname, projectPath)) {
          console.printf(console.ERROR, "Failed to compress project.");
          return false;
        }

        // just in case of non-remote JTAG via openFPGAloader
        String openFPGAloaderFormat = AppPreferences.ALTERA_FORMAT.get();

        if (!HTTP.post(console, url, "operation", "synthesize",
              "use64bit", use64bit,
              "flashname", board.fpga.FlashName,
              "format", openFPGAloaderFormat,
              "zipfile", new File(zipname)))
          return false;

        String resulturl;
        String lastline = console.getText().get(console.getText().size()-1);
        if (lastline.startsWith("RESULT: /")) {
          resulturl = urlbase + lastline.substring(9);
        } else if (lastline.startsWith("RESULT: ")) {
          resulturl = urldir + lastline.substring(8);
        } else {
          console.printf(console.ERROR, "Failed.");
          return false;
        }

        if (!HTTP.get(console, resulturl, bitstreamzip))
          return false;

        if (!Zip.uncompress(console, bitstreamzip, projectPath))
          return false;

        return true;
      }
    });

    return true;
  }

  @Override
  public boolean createProgrammingPlan(ArrayList<Stage> stages) {

    if (programmer != null && !(programmer instanceof Altera.AlteraProgrammer)) {
      err.AddFatalError("Altera toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Altera programmer.");
      return false;
    }

    if (remoteJTAG) {
      stages.add(new RunnableStage(
            "scan", "Searching for FPGA Devices",
            "Could not find any FPGA devices. Did you connect the FPGA board?") {
        @Override
        protected boolean prep() {
          boolean ok = prepForScan(cmdr, console);
          cancelled = scanWasCancelled;
          return ok;
        }
        @Override
        protected boolean run() {
          console.printf("Listing cables from " + url);
          return HTTP.post(console, url, "operation", "list-cables",
              "use64bit", use64bit);
        }
        @Override
        protected boolean post() {
          boolean ok = postScanDetectCable(cmdr, console);
          cancelled = scanWasCancelled;
          return ok;
        }
      });
      stages.add(new RunnableStage(
            "remote download", "Downloading to Remote FPGA", 
            "Failed to download design; did you connect the board?") {
        @Override
        protected boolean run() {
          if (writeToFlash)
            return HTTP.post(console, url, "operation", "program", 
                "use64bit", use64bit,
                "mode", "as", "cable", cablename, "bitfile", new File(sandboxPath+flashfile));
          else
            return HTTP.post(console, url, "operation", "program",
                "use64bit", use64bit,
                "mode", "jtag", "cable", cablename, "bitfile", new File(sandboxPath+bitfile));
        }
        @Override
        protected boolean post() {
          String lastline = console.getText().get(console.getText().size()-1);
          return lastline.startsWith("success");
        }
      });
    } else {
      return false;
      // // FIXME: also try usb tmc?
      // if (!OpenFPGALoader.supports(board)) {
      //   err.AddFatalError("Board does not support openFPGAloader yet.");
      //   return new ArrayList<>();
      // }
      // final String openFPGAloader = OpenFPGALoader.findExecutable(err);
      // if (openFPGAloader == null) {
      //   return new ArrayList<>();
      // }
      // stages.add(new ProcessStage(
      //       "download", "Downloading to Local FPGA",
      //       null /* will be assigned in prep() */,
      //       "Failed to download design; did you connect the board?") {
      //   protected boolean prep() {
      //     boolean ok = prepForScan(cmdr, console);
      //     cancelled = scanWasCancelled;
      //     cmd = new ArrayList<>();
      //     cmd.add(openFPGAloader);
      //     cmd.add("-b");
      //     cmd.add(OpenFPGALoader.boardNameFor(board));
      //     cmd.add(bitfile);
      //     return ok;
      //   }
      //   int retrycount = 0;
      //   protected boolean retry(int exitval) { return retrycount++ < 2; }
      // });
    }
    return true;
  }

  protected boolean prepForScan(Commander cmdr, Console console) {
    if (remoteJTAG)
      return super.prepForScan(cmdr, console);

    // local JTAG via openFPGAloader
    String fmt = AppPreferences.ALTERA_FORMAT.get();
    if (new File(sandboxPath + TOP_HDL + "." + fmt).exists()) {
      bitfile = TOP_HDL + "." + fmt;
    }
    if (bitfile == null) {
      console.printf(console.ERROR, "Error: Design must be synthesized before download.");
      return false;
    }
    if (!cmdr.confirmDownload()) {
      scanWasCancelled = true;
      return false;
    }
    return true;
  }
}
