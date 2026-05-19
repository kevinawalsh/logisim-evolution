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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.DriveStrength;
import com.bfh.logisim.fpga.InputBias;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class Xilinx {

  public static final String XILINX_XST = "xst" + Toolchain.dotexe;
  public static final String XILINX_NGDBUILD = "ngdbuild" + Toolchain.dotexe;
  public static final String XILINX_MAP = "map" + Toolchain.dotexe;
  public static final String XILINX_PAR = "par" + Toolchain.dotexe;
  public static final String XILINX_BITGEN = "bitgen" + Toolchain.dotexe;
  public static final String XILINX_IMPACT = "impact" + Toolchain.dotexe;
  public static final String XILINX_CPLDFIT = "cpldfit" + Toolchain.dotexe;
  public static final String XILINX_HPREP6 = "hprep6" + Toolchain.dotexe;
  public static final String[] XILINX_PROGRAMS = {
    XILINX_XST, XILINX_NGDBUILD, XILINX_MAP, XILINX_PAR,
    XILINX_BITGEN, XILINX_IMPACT, XILINX_CPLDFIT, XILINX_HPREP6,
  };

  private static final Toolchain MY_TOOLCHAIN = new Toolchain("Xilinx ISE", "Xilinx", true, true) {
    @Override
    public boolean hasAlternateName(String altname) {
      return 
        altname.equalsIgnoreCase("Xilinx ISE")
        || altname.equalsIgnoreCase("Xilinx ISE Design Suite")
        || altname.equalsIgnoreCase("Xilinx Design Suite");
    }
    @Override
    public boolean supports(Board b) {
      // TODO: probably need to use fpga part to somehow determine if it is
      // supported.
      return b.name.toLowerCase().contains("xilinx")
        || b.codename.toLowerCase().contains("xilinx")
        || b.name.toLowerCase().contains("amd")
        || b.codename.toLowerCase().contains("amd");
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
    public FPGADownload newDownloader() { return new XilinxDownload(); }
    @Override
    public FPGAProgrammer newProgrammer() { return new XilinxProgrammer(); }
  };

  public static void register() { Toolchain.register(MY_TOOLCHAIN); }


  private static class XilinxDownload extends FPGADownload {

    private XilinxDownload() { super("Xilinx"); }

    @Override
    public boolean readyForDownload() {
      // return new File(scriptPath + script_file).exists()
      //     && new File(scriptPath + vhdl_list_file).exists()
      //     && new File(ucfPath + ucf_file).exists()
      //     && new File(scriptPath + download_file).exists();
      String bitFileExt = isCPLD(board.fpga) ? ".jed" : ".bit";
      return new File(sandboxPath + TOP_HDL + bitFileExt).exists();
    }

    private ArrayList<String> externalSynthesisScript() {
      // If XilinxToolPath is an executable file, rather than a directory, then
      // use that as a single-file script to do the entire synthesis rather than
      // using the multi-step synthesis using xst.exe, etc.
      String tool = AppPreferences.XILINX_PATH.get();
      if (tool == null || tool.isEmpty())
        return null;
      File script = new File(tool);
      if (!script.exists() || script.isDirectory() || !script.canExecute()
          || script.getName().equalsIgnoreCase("xst")
          || script.getName().equalsIgnoreCase("xst.exe"))
        return null;
      ArrayList<String> command = new ArrayList<>();
      command.add(tool);
      return command;
    }

    private ArrayList<String> cmd(String prog, String ...args) {
      ArrayList<String> command = new ArrayList<>();
      String x = AppPreferences.XILINX_PATH.get();
      if (x == null || x.isEmpty()) {
        command.add(prog);
      } else {
        // strip off "xst" or "xst.exe", 
        // or for standalone script (which might still be used with impact.exe)
        // strip off the script name
        File xdir = new File(x);
        if (!xdir.isDirectory())
          xdir = xdir.getParentFile();
        command.add(xdir + File.separator + prog);
      }
      for (String arg: args)
        command.add(arg);
      return command;
    }

    public boolean toolchainIsInstalled(FPGAReport err) {
      String helpmsg = "It should be set to the directory where " + XILINX_XST
        + " and related programs are installed, or set to a file"
        + " containing a stand-alone executable script.";
      String tool = AppPreferences.XILINX_PATH.get();
      if (tool == null || tool.isEmpty()) {
        err.AddFatalError("Xilinx ISE toolchain path not configured. " + helpmsg);
        return false;
      }
      if (externalSynthesisScript() != null)
        return true;
      File prog = new File(tool + File.separator + XILINX_XST);
      if (prog.exists() && !prog.isDirectory() && prog.canExecute())
        return true;
      err.AddFatalError("Xilinx ISE toolchain path is set to " + tool + ","
          + " but this appears to be incorrect. " + helpmsg);
      return false;
    }

    @Override
    public ArrayList<Stage> initiateDownload(Commander cmdr) {
      ArrayList<Stage> stages = new ArrayList<>();

      if (!readyForDownload()) {
        ArrayList<String> tool = externalSynthesisScript();
        if (tool != null) {
          tool.add(projectPath);
          stages.add(new ProcessStage(
                "synthesis", "Synthesizing (may take a while)",
                tool, "Failed to synthesize design, cannot download"));
        } else {
          String script = scriptPath.replace(projectPath, "../") + script_file;
          stages.add(new ProcessStage(
                "synthesize", "Synthesizing (may take a while)",
                cmd(XILINX_XST, "-ifn", script, "-ofn", "logisim.log"),
                "Failed to synthesize Xilinx project, cannot download"));
          String ucf = ucfPath.replace(projectPath, "../") + ucf_file;
          stages.add(new ProcessStage(
                "constrain", "Adding Constraints",
                cmd(XILINX_NGDBUILD, "-intstyle", "ise", "-uc", ucf, "logisim.ngc", "logisim.ngd"),
                "Failed to add Xilinx constraints, cannot download"));
          if (!isCPLD(board.fpga)) {
            stages.add(new ProcessStage(
                  "mapping", "Mapping Design (may take a while)",
                  cmd(XILINX_MAP, "-intstyle", "ise", "-o", "logisim_map", "logisim.ngd"),
                  "Failed to map design, cannot download"));
            stages.add(new ProcessStage(
                  "place & route", "Place & Route Design (may take a while)",
                  cmd(XILINX_PAR, "-w", "-intstyle", "ise", "-ol", "high", "logisim_map", "logisim_par", "logisim_map.pcf"),
                  "Failed to place & route design, cannot download"));
            String unusedPinFlag = getXilinxFPGAUnusedPinFlag(board);
            if (unusedPinFlag != null)
              stages.add(new ProcessStage(
                    "generate", "Generating Bitfile",
                    cmd(XILINX_BITGEN, "-w", "-g", unusedPinFlag,
                      "-g", "StartupClk:CCLK", "logisim_par", TOP_HDL + ".bit"),
                    "Failed to place & route design, cannot download"));
            else
              stages.add(new ProcessStage(
                    "generate", "Generating Bitfile",
                    cmd(XILINX_BITGEN, "-w",
                      "-g", "StartupClk:CCLK", "logisim_par", TOP_HDL + ".bit"),
                    "Failed to place & route design, cannot download"));
          } else {
            String part = board.fpga.Part.toUpperCase() + "-"
              + board.fpga.SpeedGrade + "-"
              + board.fpga.Package.toUpperCase();
            String unusedPinFlag = getXilinxCPLDUnusedPinFlag(board);
            if (unusedPinFlag != null)
              stages.add(new ProcessStage(
                    "CPLD fit", "Fit CPLD Design (may take a while)",
                    cmd(XILINX_CPLDFIT, "-p", part, "-intstyle", "ise",
                      "-unused", unusedPinFlag,
                      // "-terminate", board.fpga.UnusedPinsBehavior.xilinx, // TODO: do correct termination type
                      "-loc", "on", "-log", "logisim_cpldfit.log", "logisim.ngd"),
                    "Failed to fit CPLD design, cannot download"));
            else
              stages.add(new ProcessStage(
                    "CPLD fit", "Fit CPLD Design (may take a while)",
                    cmd(XILINX_CPLDFIT, "-p", part, "-intstyle", "ise",
                      // "-terminate", board.fpga.UnusedPinsBehavior.xilinx, // TODO: do correct termination type
                      "-loc", "on", "-log", "logisim_cpldfit.log", "logisim.ngd"),
                    "Failed to fit CPLD design, cannot download"));
            stages.add(new ProcessStage(
                  "generate", "Generating Bitfile",
                  cmd(XILINX_HPREP6, "-i", "logisim.vm6"),
                  "Failed to generate bitfile, cannot download"));
          }
        }
      }

      if (programmer != null && !(programmer instanceof XilinxProgrammer)) {
        err.AddFatalError("Xilinx ISE toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Xilinx programmer.");
        return stages;
      }

      if (!board.fpga.USBTMCAvailable) {
        String download = scriptPath.replace(projectPath, "../") + download_file;
        stages.add(new ProcessStage(
              "download", "Downloading to FPGA",
              cmd(XILINX_IMPACT, "-batch", download),
              "Failed to download design; did you connect the board?") {
          @Override
          protected boolean prep() {
            if (!cmdr.confirmDownload()) {
              cancelled = true;
              return false;
            }
            return true;
          }
        });
      } else {
        stages.add(new RunnableStage(
              "download", "Downloading to FPGA", 
              "Failed to download design; did you connect the board?") {
          File usbtmc;
          @Override
          protected boolean prep() {
            if (!cmdr.confirmDownload()) {
              failed = true;
              cancelled = true;
              return false;
            }
            File usbtmc = new File("/dev/usbtmc0");
            if (!usbtmc.exists()) {
              console.printf(console.ERROR, "Could not find usbtmc device: /dev/usbtmc0 not found.");
              failed = true;
              return false;
            }
            return true;
          }
          @Override
          protected boolean run() {
            String bitFileExt = isCPLD(board.fpga) ? ".jed" : ".bit";
            File bitfile = new File(sandboxPath + TOP_HDL + bitFileExt);
            return copyFile(console, usbtmc, bitfile);
          }
        });
      }
      return stages;
    }

    private boolean copyFile(Console console, File destfile, File srcfile) { 
      console.printf("%s <= %s\n", destfile, srcfile);
      byte[] buf = new byte[BUFFER_SIZE];
      try {
        BufferedInputStream src = new BufferedInputStream(new FileInputStream(srcfile));
        BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(destfile));
        dest.write("FPGA ".getBytes());
        int n = src.read(buf, 0, BUFFER_SIZE);
        while (n > 0) {
          dest.write(buf, 0, n);
          n = src.read(buf, 0, BUFFER_SIZE);
        }
        dest.close();
        src.close();
      } catch (IOException e) {
        console.printf(console.ERROR, "Error: " + e.getMessage());
        return false;
      }
      return true;
    }

    private boolean generateVhdlListFile(ArrayList<String> hdlFiles) {
      AuxFile out = new AuxFile(scriptPath, vhdl_list_file, err);
      String kind = lang.toUpperCase();
      for (String f : hdlFiles)
        out.stmt("%s work \"%s\"", kind, f);
      return out.save();
    }

    private boolean generateRunScript() {
      Chipset chip = board.fpga;
      String dev = String.format("%s-%s-%s", chip.Part, chip.Package, chip.SpeedGrade);
      String vhdlListPath = scriptPath.replace(projectPath, "../") + vhdl_list_file;
      AuxFile out = new AuxFile(scriptPath, script_file, err);
      out.stmt("run -top %s -ofn logisim.ngc -ofmt NGC -ifn %s -ifmt mixed -p %s",
          TOP_HDL, vhdlListPath, dev);
      return out.save();
    }

    private boolean generateDownloadScript() {
      boolean isCPLD = isCPLD(board.fpga);
      String bitFileExt = isCPLD ? ".jed" : ".bit";
      int jtagPos = board.fpga.JTAGPos;
      AuxFile out = new AuxFile(scriptPath, download_file, err);
      out.stmt("setmode -bscan");
      if (writeToFlash && board.fpga.FlashDefined) {
        String mcsFile = scriptPath + mcs_file;
        out.stmt("setmode -pff");
        out.stmt("setSubMode -pffserial");
        out.stmt("addPromDevice -p %s -size 0 -name %s", jtagPos, board.fpga.FlashName);
        out.stmt("addDesign -version 0 -name \"0\"");
        out.stmt("addDeviceChain -index 0");
        out.stmt("addDevice -p %s -file %s", jtagPos, TOP_HDL + bitFileExt);
        out.stmt("generate -format mcs -fillvalue FF -output %s", mcsFile);
        out.stmt("setMode -bs");
        out.stmt("setCable -port auto");
        out.stmt("identify");
        out.stmt("assignFile -p %s -file %s", board.fpga.FlashPos, mcsFile);
        out.stmt("program -p %s -e -v", board.fpga.FlashPos);
      } else if (isCPLD) {
        out.stmt("setcable -p auto");
        out.stmt("identify");
        out.stmt("assignFile -p %s -file %s", jtagPos, "logisim"+ bitFileExt);
        out.stmt("program -p %s -e", jtagPos);
      } else {
        out.stmt("setcable -p auto");
        out.stmt("identify");
        out.stmt("assignFile -p %s -file %s", jtagPos, TOP_HDL + bitFileExt);
        out.stmt("program -p %s -onlyFpga", jtagPos);
      }
      out.stmt("quit");
      return out.save();
    }


    @Override
    public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {
      return generateVhdlListFile(hdlFiles)
        && generateRunScript()
        && generateDownloadScript()
        && writeXilinuxConstraintUCF(new AuxFile(ucfPath, ucf_file, err), board, ioResources);
    }

    private static boolean isCPLD(Chipset chip) {
      String part = chip.Part.toUpperCase();
      return part.startsWith("XC2C")
        || part.startsWith("XA2C")
        || part.startsWith("XCR3")
        || part.startsWith("XC9500")
        || part.startsWith("XA9500");
    }

    private final static String vhdl_list_file = "XilinxVHDLList.prj";
    private final static String script_file = "XilinxScript.cmd";
    private final static String ucf_file = "XilinxConstraints.ucf";
    private final static String download_file = "XilinxDownload";
    private final static String mcs_file = "XilinxProm.mcs";
    private final static Integer BUFFER_SIZE = 16 * 1024;
  }

  protected static class XilinxProgrammer extends FPGAProgrammer {
    // TODO: reorganize stages above, e.g. allowing for
    // openFPGALoader, separating out usb-tmc, etc.
    XilinxProgrammer() { super("Xilinx"); }
    @Override
    public boolean toolchainIsInstalled(FPGAReport err) { return true; } // only relevant if XilinxDownload reported okay
  }

  private static String getXilinxFPGAUnusedPinFlag(Board board) {
    // first priority: use xilinx-specific param from board.xml
    String pref = board.paramFor(MY_TOOLCHAIN, "UnusedPin");
    if (pref != null && !pref.isEmpty())
      return "UnusedPin:" + pref;
    if (pref.isEmpty())
      return null; // explicitly unset, let toolchain decide
    // otherwise: use generic param from board.xml
    switch (board.fpga.UnmentionedPinsBehaviorHint) {
      case DRIVE_HIGH:
      case INPUT_PULL_UP:
        return "UnusedPin:PULLUP";
      case DRIVE_LOW:
      case INPUT_PULL_DOWN:
        return "UnusedPin:PULLDOWN";
      case INPUT_NO_PULL:
        return "UnusedPin:PULLNONE"; // aka "FLOAT" maybe? docs are inconsistent...
      case UNSPECIFIED:
      default:
        return "UnusedPin:PULLDOWN"; // seems to be a widely used "safe" default?
        // return null; // let toolchain decide
    }
  }

  private static String getXilinxCPLDUnusedPinFlag(Board board) {
    // first priority: use xilinx-specific param from board.xml
    String pref = board.paramFor(MY_TOOLCHAIN, "UnusedPin");
    if (pref != null && !pref.isEmpty())
      return pref;
    if (pref.isEmpty())
      return null; // explicitly unset, let toolchain decide
    // otherwise: use generic param from board.xml
    switch (board.fpga.UnmentionedPinsBehaviorHint) {
      case DRIVE_HIGH:
      case INPUT_PULL_UP:
        return "pullup";
      case DRIVE_LOW:
        return "ground";
      case INPUT_PULL_DOWN:
        return "pulldown";
      case INPUT_NO_PULL:
        return "float";
      case UNSPECIFIED:
      default:
        return null; // let cpldfit decide
    }
  }

  static void writeIoSpec(AuxFile lpf, String net, String pin, InputBias bias, IoStandard standard, DriveStrength strength, String label) {
    String iospec = "LOC = \""+pin+"\"";

    if (bias == InputBias.PULL_UP) iospec += " | PULLUP";
    else if (bias == InputBias.PULL_DOWN) iospec += " | PULLDOWN";
    else if (bias == InputBias.BUS_HOLD) iospec += " | KEEPER";
    // else if (bias == InputBias.PULL_NONE) ; /* nop... UCF has no explicit NONE, we simply omit the pull */

    if (standard != IoStandard.DEFAULT)
      iospec += " | IOSTANDARD = " + standard;

    if (strength != DriveStrength.DEFAULT)
      iospec += " | DRIVE = " + (strength.ma != null ? strength.ma : strength.desc);

    lpf.stmt("NET \"%s\" %s;# %s", net, iospec, label);
  }

  static boolean writeXilinuxConstraintUCF(AuxFile ucf, Board board, PinBindings ioResources) {
    if (ioResources.requiresOscillator) {
      writeIoSpec(ucf, FPGADownload.CLK_PORT, board.fpga.ClockPinLocation,
          InputBias.PULL_NONE, board.fpga.ClockIOStandard, DriveStrength.DEFAULT, "primary clock");
      ucf.stmt("NET \"%s\" TNM_NET = \"%s\" ;", FPGADownload.CLK_PORT, FPGADownload.CLK_PORT);
      ucf.stmt("TIMESPEC \"TS_%s\" = PERIOD \"%s\" %s HIGH 50 % ;",
          FPGADownload.CLK_PORT, FPGADownload.CLK_PORT, board.fpga.Speed);
      ucf.stmt();
    }
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      if (net.startsWith("FPGA_INPUT_PIN_")) {
        InputBias bias = ioResources.getInputBias(net);
        writeIoSpec(ucf, net, pin, bias, io.standard, io.strength, label);
      } else if (net.startsWith("FPGA_BIDIR_PIN_")) {
        // FIXME: use TRELLIS_IO block, and apply bias there instead of here?
        InputBias bias = ioResources.getInputBias(net);
        writeIoSpec(ucf, net, pin, bias, io.standard, io.strength, label);
      } else if (net.startsWith("FPGA_OUTPUT_PIN_")) {
        // output pins do not have bias
        writeIoSpec(ucf, net, pin, null, io.standard, io.strength, label);
      }
    });
    // FIXME: handle all unmapped pins
    return ucf.save();
  }
}
