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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.fpga.PullBehavior;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.prefs.AppPreferences;

public class Altera {
  
  public static final String ALTERA_QUARTUS_SH = "quartus_sh" + Toolchain.dotexe;
  public static final String ALTERA_QUARTUS_PGM = "quartus_pgm" + Toolchain.dotexe;
  public static final String ALTERA_QUARTUS_MAP = "quartus_map" + Toolchain.dotexe;
  public static final String ALTERA_QUARTUS_CPF = "quartus_cpf" + Toolchain.dotexe;
  public static final String[] ALTERA_PROGRAMS = {
    ALTERA_QUARTUS_SH, ALTERA_QUARTUS_PGM, ALTERA_QUARTUS_MAP, ALTERA_QUARTUS_CPF,
  };

  private static final Toolchain MY_TOOLCHAIN = new Toolchain("Altera Quartus", "Quartus", true, true) {
    @Override
    public boolean hasAlternateName(String altname) {
      return altname.toLowerCase().startsWith("quartus")
        || altname.toLowerCase().startsWith("altera quartus")
        || altname.toLowerCase().startsWith("intel quartus");
    }
    @Override
    public boolean supports(Board b) {
      // TODO: probably need to use fpga part to somehow determine if it is
      // supported.
      return b.name.toLowerCase().contains("altera")
        || b.codename.toLowerCase().contains("altera")
        || b.name.toLowerCase().contains("intel")
        || b.codename.toLowerCase().contains("intel");
    }
    @Override
    public List<String[]> defaultParams(/*Board board*/) {
      return Collections.emptyList();
    }
    @Override
    public List<String> getLanguages(Board board) {
      return List.of(VERILOG, VHDL);
    }
    @Override
    public FPGADownload newDownloader() {
      if (isRemote())
        return new AlteraDownloadRemote();
      else if (isScript())
        return new AlteraDownloadScript();
      else
        return new AlteraDownloadLocal();
    }
    @Override
    public FPGAProgrammer newProgrammer() {
      return new AlteraProgrammer();
    }
  };

  public static void register() { Toolchain.register(MY_TOOLCHAIN); }


  static boolean isRemote() {
    String tool = AppPreferences.ALTERA_PATH.get();
    if (tool == null || tool.isEmpty()) return false;
    tool = tool.toLowerCase();
    return tool.startsWith("http://") || tool.startsWith("https://");
  }

  static boolean isScript() {
    String tool = AppPreferences.ALTERA_PATH.get();
    if (tool == null || tool.isEmpty()) return false;
    File script = new File(tool);
    return script.exists() && !script.isDirectory() && script.canExecute();
  }



  abstract static class AlteraDownload extends FPGADownload {

    protected AlteraDownload() { super("Quartus"); }

    @Override
    public boolean readyForDownload() {
      return new File(sandboxPath + TOP_HDL + ".sof").exists()
        || new File(sandboxPath + TOP_HDL + ".pof").exists();
    }

    public boolean toolchainIsInstalled(FPGAReport err) {
      String helpmsg = "It should be set to the directory where " + ALTERA_QUARTUS_SH
        + " and related programs are installed, or set to a file"
        + " containing astand-alone executable script, or set to a"
        + " *trusted* URL to invoke for remote synthesis.";
      String tool = AppPreferences.ALTERA_PATH.get();
      if (tool == null || tool.isEmpty()) {
        err.AddFatalError("Altera Quartus toolchain path not configured. " + helpmsg);
        return false;
      }
      if (isRemote() || isScript())
        return true;
      File prog = new File(tool + File.separator + ALTERA_QUARTUS_SH);
      if (prog.exists() && !prog.isDirectory() && prog.canExecute())
        return true;
      err.AddFatalError("Altera Quartus toolchain path is set to " + tool + ","
          + " but this appears to be incorrect. " + helpmsg);
      return false;
    }

    @Override
    public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {

      Hdl out = new Hdl(lang, err);

      Chipset chip = board.fpga;
      String[] pkg = board.fpga.Package.split(" ");
      String hdltype = out.isVhdl ? "VHDL_FILE" : "VERILOG_FILE";

      out.stmt("# Quartus II Tcl Project package loading script for Logisim");
      out.stmt("package require ::quartus::project");
      out.stmt();
      out.stmt("set need_to_close_project 0");
      out.stmt("set make_assignments 1");
      out.stmt();
      out.stmt("# Check that the right project is open");
      out.stmt("if {[is_project_open]} {");
      out.stmt("    if {[string compare $quartus(project) \"%s\"]} {", TOP_HDL);
      out.stmt("        puts \"Project %s is not open\"", TOP_HDL);
      out.stmt("        set make_assignments 0");
      out.stmt("    }");
      out.stmt("} else {");
      out.stmt("    # Only open if not already open");
      out.stmt("    if {[project_exists %s]} {", TOP_HDL);
      out.stmt("        project_open -revision %s %s", TOP_HDL, TOP_HDL);
      out.stmt("    } else {");
      out.stmt("        project_new -revision %s %s", TOP_HDL, TOP_HDL);
      out.stmt("    }");
      out.stmt("    set need_to_close_project 1");
      out.stmt("}");
      out.stmt();
      out.stmt("# Make assignments");
      out.stmt("if {$make_assignments} {");
      out.stmt("    set_global_assignment -name FAMILY \"%s\"", chip.Technology);
      out.stmt("    set_global_assignment -name DEVICE %s", chip.Part);
      out.stmt("    set_global_assignment -name DEVICE_FILTER_PACKAGE %s", pkg[0]);
      out.stmt("    set_global_assignment -name DEVICE_FILTER_PIN_COUNT %s", pkg[1]);
      String unusedPinsFlag = getAlteraUnusedPinsFlag(board);
      if (unusedPinsFlag != null)
        out.stmt("    set_global_assignment -name RESERVE_ALL_UNUSED_PINS \"%s\"", unusedPinsFlag);
      out.stmt("    set_global_assignment -name FMAX_REQUIREMENT \"%s\"", chip.Speed);
      out.stmt("    set_global_assignment -name RESERVE_NCEO_AFTER_CONFIGURATION \"USE AS REGULAR IO\"");
      out.stmt("    set_global_assignment -name CYCLONEII_RESERVE_NCEO_AFTER_CONFIGURATION \"USE AS REGULAR IO\"");
      out.stmt();
      out.stmt("    # Include all entities and gates");
      out.stmt();
      for (String f : hdlFiles) {
        String relPath = Paths.get(scriptPath).relativize(Paths.get(f)).toString();
        out.stmt("    set_global_assignment -name %s \"%s\"", hdltype, relPath);
      }
      out.stmt();
      out.stmt("    # Map fpga_clk and ionets to fpga pins");
      if (ioResources.requiresOscillator)
        out.stmt("    set_location_assignment %s -to %s", board.fpga.ClockPinLocation, CLK_PORT);
      ioResources.forEachPhysicalPin((pin, net, io, label) -> {
        out.stmt("    set_location_assignment %s -to %s  ;# %s", pin, net, label);
        if (io.pull == PullBehavior.PULL_UP)
          out.stmt("    set_instance_assignment -name WEAK_PULL_UP_RESISTOR ON -to %s", net);
      });
      out.stmt("    # Commit assignments");
      out.stmt("    export_assignments");
      out.stmt();
      out.stmt("    # Close project");
      out.stmt("    if {$need_to_close_project} {");
      out.stmt("        project_close");
      out.stmt("    }");
      out.stmt("}");

      File f = FileWriter.GetFilePointer(scriptPath, "AlteraDownload.tcl", err);
      return f != null && FileWriter.WriteContents(f, out, err);
    }

    protected String bitfile, flashfile;
    protected String cablename;
    protected boolean scanWasCancelled = false;

    protected boolean prepForScan(Commander cmdr, Console console) {
      // Check for flash POF bitstream
      if (new File(sandboxPath + TOP_HDL + ".pof").exists())
        flashfile = TOP_HDL + ".pof";

      // Check for jtag SOF bitstream, or fall back to flash POF
      if (new File(sandboxPath + TOP_HDL + ".sof").exists())
        bitfile = TOP_HDL + ".sof";
      else if (flashfile != null)
        bitfile = flashfile;

      if (writeToFlash) {
        if (flashfile == null) {
          console.printf(console.ERROR, "Error: Design must be synthesized before download.");
          return false;
        }
        if (!cmdr.confirmDownload("Set board to PROG, and restart it.")) {
          scanWasCancelled = true;
          return false;
        }
      } else {
        if (bitfile == null) {
          console.printf(console.ERROR, "Error: Design must be synthesized before download.");
          return false;
        }
        if (!cmdr.confirmDownload()) {
          scanWasCancelled = true;
          return false;
        }
      }
      return true;

    }

    protected boolean postScanDetectCable(Commander cmdr, Console console) {
      ArrayList<String> dev = new ArrayList<>();
      for (String line: console.getText()) {
        int n  = dev.size() + 1;
        if (!line.matches("^" + n + "\\) .*" ))
          continue;
        line = line.replaceAll("^" + n + "\\) ", "");
        dev.add(line.trim());
      }
      if (dev.size() == 0) {
        console.printf(console.ERROR, "No USB-Blaster cable detected");
        return false;
      } else if (dev.size() == 1) {
        cablename = "usb-blaster"; // why not dev.get(0)?
      } else if (dev.size() > 1) {
        console.printf("%d FPGA devices detected:", dev.size());
        int i = 1;
        for (String d : dev)
          console.printf("   %d) %s", i++, d);
        cablename = cmdr.chooseDevice(dev);
        if (cablename == null) {
          scanWasCancelled = true;
          return false;
        }
      }
      return true;
    }

  }

  protected static class AlteraProgrammer extends FPGAProgrammer {
    // TODO: reorganize stages above, e.g. allowing for
    // openFPGALoader, separating out usb-tmc, etc.
    AlteraProgrammer() { super("Altera"); }
    @Override
    public boolean toolchainIsInstalled(FPGAReport err) { return true; } // only relevant if AlteraDownload reported okay
  }

  private static String getAlteraUnusedPinsFlag(Board board) {
    // first priority: use altera-specific param from board.xml
    String pref = board.paramFor(MY_TOOLCHAIN, "RESERVE_ALL_UNUSED_PINS");
    if (pref != null && !pref.isEmpty())
      return pref;
    if (pref.isEmpty())
      return null; // explicitly unset, let toolchain decide
    // otherwise: use generic param from board.xml
    switch (board.fpga.UnmentionedPinsBehaviorHint) {
      case DRIVE_HIGH:
      case INPUT_PULL_UP:
        return "AS INPUT TRI-STATED WITH WEAK PULL-UP";
      case DRIVE_LOW:
      case INPUT_PULL_DOWN:
        return "AS OUTPUT DRIVING GROUND";
      case INPUT_NO_PULL:
        return "AS INPUT TRI-STATED";
      case UNSPECIFIED:
      default:
        return "AS INPUT TRI-STATED WITH WEAK PULL-UP"; // seems safer than the toolchain default of DRIVING GROUND
        // return null; // let toolchain decide (default seems to be DRIVING GROUND?!?)
    }
  }

}
