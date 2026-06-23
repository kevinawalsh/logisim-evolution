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
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.DriveStrength;
import com.bfh.logisim.fpga.InputBias;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class Xilinx extends Toolchain {

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

  public static final Toolchain TOOLCHAIN = new Xilinx();

  private Xilinx() {
    super("Xilinx ISE", "Xilinx", true, true);
  }

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
  public String defaultParamsAsString(/*Board board*/) {
    return "UnusedPin: passed to bitgen or cpldfit - PULLUP|PULLDOWN|PULLNONE\n";
  }

  @Override
  public List<String> getLanguages(Board board) {
    return List.of(VERILOG, VHDL);
  }

  @Override
  public FPGASynthesizer newSynthesizer(FPGAReport err) {
    String xst_or_script = getInstalledCommand(err);
    return xst_or_script == null ? null : new XilinxSynthesizer(err, xst_or_script);
  }

  @Override
  public FPGAProgrammer newProgrammer(FPGAReport err) {
    String xst_or_script = getInstalledCommand(err);
    return xst_or_script == null ? null : new XilinxProgrammer(err, xst_or_script);
  }

  private static final String helpmsg =
    "Set the Xilinx toolchain path to the directory where " + XILINX_XST
    + " and related programs are installed, or set it to a file"
    + " containing a stand-alone executable script.";

  @Override
  public InstallStatus toolchainInstallStatus() {
    String path = AppPreferences.XILINX_PATH.get();
    if (path == null || path.isEmpty())
      return InstallStatus.fromError("Xilinx ISE toolchain path not configured. %s", helpmsg);

    // Check if path is a directory containing xst
    File prog = new File(path, XILINX_XST);
    if (prog.exists() && !prog.isDirectory() && prog.canExecute())
      return InstallStatus.fromSuccess(prog.toString(), "xst: undetermined version");

    // Check if path points to xst itself, or maybe an external script
    prog = new File(path);
    if (prog.exists() && !prog.isDirectory() && prog.canExecute()) {
      boolean is_xst = prog.getName().equalsIgnoreCase("xst") ||
        prog.getName().equalsIgnoreCase("xst.exe");
      if (is_xst)
        return InstallStatus.fromSuccess(prog.toString(), "xst: undetermined version");
      else
        return InstallStatus.fromSuccess(prog.toString(), "standalone-script");
    }

    return InstallStatus.fromError(
        "Xilinx ISE toolchain path set to '%s' but no suitable program found there. %s",
        toolchainName, path, helpmsg);
  }


  private static class XilinxSynthesizer extends FPGASynthesizer {

    private String xdir; // verified path to dir containing xst and other tools
    private String standaloneScript; // stand-alone script, full path, if xst is null

    private XilinxSynthesizer(FPGAReport err, String xst_or_script) {
      super(TOOLCHAIN, "Xilinx", err);
      File prog = new File(xst_or_script);
      boolean is_xst = prog.getName().equalsIgnoreCase("xst") ||
        prog.getName().equalsIgnoreCase("xst.exe");
      if (is_xst)
        xdir = new File(xst_or_script).getParentFile().toString() + File.separator;
      else
        standaloneScript = xst_or_script;
    }

    @Override
    public boolean readyForDownload() {
      // return new File(scriptPath + script_file).exists()
      //     && new File(scriptPath + vhdl_list_file).exists()
      //     && new File(ucfPath + ucf_file).exists()
      //     && new File(scriptPath + download_file).exists();
      String bitFileExt = isCPLD(board.fpga) ? ".jed" : ".bit";
      return new File(sandboxPath + TOP_HDL + bitFileExt).exists();
    }

    private String getXilinxFPGAUnusedPinFlag(Board board) {
      // first priority: use xilinx-specific param from board.xml
      String pref = param("UnusedPin");
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

    private String getXilinxCPLDUnusedPinFlag(Board board) {
      // first priority: use xilinx-specific param from board.xml
      String pref = param("UnusedPin");
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


    @Override
    public boolean createSynthesisPlan(ArrayList<Stage> stages) {

      // If the toolchain path is unusual (not to xst or xst.exe), we assume it is a
      // standalone script that will do all synthesis steps in one go. We pass
      // two parameters: "synthesize" and the project path.
      if (standaloneScript != null) {
        stages.add(new ProcessStage(
              "synthesis", "Synthesizing (may take a while)",
              join(standaloneScript, "synthesize", projectPath),
              "Failed to synthesize design, cannot download"));
        return true;
      }

      // Otherwise, we use the standard (old) xilinx toolchain programs.

      String script = scriptPath.replace(projectPath, "../") + script_file;
      stages.add(new ProcessStage(
            "synthesize", "Synthesizing (may take a while)",
            join(xdir + XILINX_XST, "-ifn", script, "-ofn", "logisim.log"),
            "Failed to synthesize Xilinx project, cannot download"));

      String ucf = ucfPath.replace(projectPath, "../") + ucf_file;
      stages.add(new ProcessStage(
            "constrain", "Adding Constraints",
            join(xdir + XILINX_NGDBUILD, "-intstyle", "ise", "-uc", ucf, "logisim.ngc", "logisim.ngd"),
            "Failed to add Xilinx constraints, cannot download"));

      if (!isCPLD(board.fpga)) {
        stages.add(new ProcessStage(
              "mapping", "Mapping Design (may take a while)",
              join(xdir + XILINX_MAP, "-intstyle", "ise", "-o", "logisim_map", "logisim.ngd"),
              "Failed to map design, cannot download"));
        stages.add(new ProcessStage(
              "place & route", "Place & Route Design (may take a while)",
              join(xdir + XILINX_PAR, "-w", "-intstyle", "ise", "-ol", "high", "logisim_map", "logisim_par", "logisim_map.pcf"),
              "Failed to place & route design, cannot download"));
        String unusedPinFlag = getXilinxFPGAUnusedPinFlag(board);
        stages.add(new ProcessStage(
              "generate", "Generating Bitfile",
              join(xdir + XILINX_BITGEN, "-w", opt("-g", unusedPinFlag),
                "-g", "StartupClk:CCLK", "logisim_par", TOP_HDL + ".bit"),
              "Failed to place & route design, cannot download"));
      } else {
        String part = nameForCPLD(board.fpga);
        String unusedPinFlag = getXilinxCPLDUnusedPinFlag(board);
        stages.add(new ProcessStage(
              "CPLD fit", "Fit CPLD Design (may take a while)",
              join(xdir + XILINX_CPLDFIT, "-p", part, "-intstyle", "ise",
                opt("-unused", unusedPinFlag),
                // "-terminate", board.fpga.UnusedPinsBehavior.xilinx, // TODO: do correct termination type
                "-loc", "on", "-log", "logisim_cpldfit.log", "logisim.ngd"),
              "Failed to fit CPLD design, cannot download"));
        stages.add(new ProcessStage(
              "generate", "Generating Bitfile",
              join(xdir + XILINX_HPREP6, "-i", "logisim.vm6"),
              "Failed to generate bitfile, cannot download"));
      }

      return true;
    }

    @Override
    public boolean createProgrammingPlan(ArrayList<Stage> stages) {

      if (programmer != null && !(programmer instanceof XilinxProgrammer)) {
        err.AddFatalError("Xilinx ISE toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Xilinx programmer.");
        return false;
      }

      // Support for "USBTMC" download has been removed. So far as I can tell,
      // this was used by exactly one board: the "GECKO4LED" board using Xilinx
      // Spartan-3 XC3S5000. Apparently this board used a xilinx usb cable that
      // presented as /dev/usbtmc0 (on Linux, presumably), and was programmed by
      // writing a 4 byte prefix ("FPGA"), followed by the bitstream file,
      // directly to /dev/usbtmc0. This fpga is 20+ years old, and no public
      // information is available about the "GECKO4LED" board.

      String download = scriptPath.replace(projectPath, "../") + download_file;
      ArrayList<String> cmd;

      // If the toolchain path is unusual (not to xst or xst.exe), we assume it is a
      // standalone script that will can do the programming. We pass
      // two parameters: "program" and the download file.
      if (standaloneScript != null)
        cmd = join(standaloneScript, "program", download);
      else
        cmd = join(xdir + XILINX_IMPACT, "-batch", download);

      stages.add(new ProcessStage(
            "download", "Downloading to FPGA", cmd,
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

    private static String nameForCPLD(Chipset chip) {
      return String.format("%s-%s-%s",
          chip.Part.toUpperCase(),
          chip.SpeedGrade,
          chip.Package.toUpperCase());
    }

    private final static String vhdl_list_file = "XilinxVHDLList.prj";
    private final static String script_file = "XilinxScript.cmd";
    private final static String ucf_file = "XilinxConstraints.ucf";
    private final static String download_file = "XilinxDownload";
    private final static String mcs_file = "XilinxProm.mcs";
    private final static Integer BUFFER_SIZE = 16 * 1024;
  }

  protected static class XilinxProgrammer extends FPGAProgrammer {
    XilinxProgrammer(FPGAReport err, String xst_or_script) {
      super(TOOLCHAIN, "Xilinx", err);
    }
    // The work for xilinx upload is done above, in createProgrammingPlan().
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
      writeIoSpec(ucf, FPGASynthesizer.CLK_PORT, board.fpga.ClockPinLocation,
          InputBias.PULL_NONE, board.fpga.ClockIOStandard, DriveStrength.DEFAULT, "primary clock");
      ucf.stmt("NET \"%s\" TNM_NET = \"%s\" ;", FPGASynthesizer.CLK_PORT, FPGASynthesizer.CLK_PORT);
      ucf.stmt("TIMESPEC \"TS_%s\" = PERIOD \"%s\" %s HIGH 50 % ;",
          FPGASynthesizer.CLK_PORT, FPGASynthesizer.CLK_PORT, Altera.formatFreqForFMAX(board.fpga.ClockFrequency));
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
