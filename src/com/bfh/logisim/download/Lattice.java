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
 *   
 * This file for supporting the Lattice toolchain in LogSim was written by: 
 *   + Carsten Noeske (noe@ghse.de, https://www.ghse.de)  
 */

package com.bfh.logisim.download;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.DriveStrength;
import com.bfh.logisim.fpga.InputBias;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class Lattice extends Toolchain {

  public static final String LATTICE_DIAMOND_WIN = "pnmainc" + Toolchain.dotexe;
  public static final String LATTICE_DIAMOND_UNIX = "diamondc";
  public static final String LATTICE_ISPLEVER_WIN = "projnav" + Toolchain.dotexe;
  public static final String[] LATTICE_PROGRAMS = {
    LATTICE_DIAMOND_WIN, LATTICE_DIAMOND_UNIX , LATTICE_ISPLEVER_WIN
  };

  public static final Lattice TOOLCHAIN = new Lattice();

  private Lattice() {
    super("Lattice Diamond/ispLEVER", "Lattice", true, true);
  }

  @Override
  public boolean hasAlternateName(String altname) {
    return 
      altname.equalsIgnoreCase("Lattice Diamond")
      || altname.equalsIgnoreCase("Lattice ispLEVER")
      || altname.equalsIgnoreCase("Diamond")
      || altname.equalsIgnoreCase("ispLEVER")
      || altname.equalsIgnoreCase("Diamond/ispLEVER")
      || altname.equalsIgnoreCase("Lattice Diamond/ispLEVER");
  }

  @Override
  public boolean supports(Board b) {
    // TODO: probably need to check which variant of toolchain is installed
    // (Diamond vs ispLEVER), then use fpga part to somehow determine if it
    // is supported.
    return b.name.toLowerCase().contains("lattice")
      || b.codename.toLowerCase().contains("lattice");
  }

  @Override
  public String defaultParamsAsString(/*Board board*/) {
    return "";
  }

  @Override
  public List<String> getLanguages(Board board) {
    return List.of(VHDL, VERILOG);
  }

  @Override
  public FPGASynthesizer newSynthesizer(FPGAReport err) {
    String cmd = getInstalledCommand(err);
    return cmd == null ? null : new LatticeSynthesizer(err, cmd);
  }

  @Override
  public FPGAProgrammer newProgrammer(FPGAReport err) {
    String cmd = getInstalledCommand(err);
    return cmd == null ? null : new LatticeProgrammer(err, cmd);
  }

  private static final String helpmsg =
    "Set the Lattice toolchain path to the directory where pnmainc.exe, "
    + "diamondc, or projnav.exe is installed.";

  @Override
  public InstallStatus toolchainInstallStatus() {
    String path = AppPreferences.LATTICE_PATH.get();
    if (path == null || path.isEmpty())
      return InstallStatus.fromError("Lattice Diamond/ispLEVER toolchain path not configured. " + helpmsg);
    String cmd = getToolPath(path);
    if (cmd == null)
      return InstallStatus.fromError("Lattice Diamond/ispLEVER toolchain path is set to '%s', "
          + "but this appears to be incorrect. %s", path, helpmsg);
    SYSTEM toolChainType = getToolChainTypeFromToolPath(cmd);
    if (toolChainType == SYSTEM.DIAMOND_WIN)
      return InstallStatus.fromSuccess(cmd, "Diamond (Windows)");
    else if (toolChainType == SYSTEM.DIAMOND_UNIX)
      return InstallStatus.fromSuccess(cmd, "Diamond (Unix)");
    else if (toolChainType == SYSTEM.ISP_LEVER_WIN)
      return InstallStatus.fromSuccess(cmd, "ispLEVER (Windows)");
    else if (toolChainType == SYSTEM.ISP_LEVER_UNIX)
      return InstallStatus.fromSuccess(cmd, "ispLEVER (Unix)");
    else
      return InstallStatus.fromSuccess(cmd, "Unknown system"); // should not happen
  }

  public enum SYSTEM { DIAMOND_WIN, DIAMOND_UNIX, ISP_LEVER_WIN, ISP_LEVER_UNIX, UNKNOWN };

  private static Map<String, SYSTEM> toolMap;
  static {
    toolMap = new HashMap<>();
    toolMap.put("pnmainc.exe", SYSTEM.DIAMOND_WIN);
    toolMap.put("diamondc", SYSTEM.DIAMOND_UNIX);
    toolMap.put("projnav.exe", SYSTEM.ISP_LEVER_WIN);
  }

  private static String getToolPath() {
    return getToolPath(AppPreferences.LATTICE_PATH.get());
  }

  private static String getToolPath(String toolPathBinDirectory) {
    if (toolPathBinDirectory == null || toolPathBinDirectory.isEmpty())
      return null;
    Path toolPathDir = Paths.get(toolPathBinDirectory);
    for (String tool : toolMap.keySet()) {
      Path toolPath = toolPathDir.resolve(tool);
      if (Files.exists(toolPath)) {
        return toolPath.toString();
      }
    }
    return null;
  }

  public static SYSTEM getToolChainTypeFromToolPath(String toolPath) {
    return toolPath == null ? SYSTEM.UNKNOWN : toolMap.get(new File(toolPath).getName());
  }

  public static SYSTEM getToolChainType() {
    return getToolChainTypeFromToolPath(getToolPath());
  }

  public static SYSTEM getToolChainType(String toolPathBinDirectory) {
    return getToolChainTypeFromToolPath(getToolPath(toolPathBinDirectory));
  }

  private static String getAbsolutePath(String pathRoot, String... pathes) {
    return getAbsolutePathAsPath(pathRoot, pathes).toString();
  }

  private static Path getAbsolutePathAsPath(String pathRoot, String... pathes) {
    return Paths.get(pathRoot, pathes).toAbsolutePath();
  }

  private static String toWinPath(Path path) {
    return path.toString().replace("/", "\\");
  }

  private static String toTclPath(Path path) {
    return path.toString().replace("\\", "/");
  }

  private static String toUnixPath(Path path) {
    String s = toTclPath(path);
    if (s.indexOf(':') == 1) {
      s = "/"+Character.toLowerCase(s.charAt(0))+s.substring(2);
    }
    return s;
  }

  private static class LatticeSynthesizer extends FPGASynthesizer {

    private LatticeSynthesizer(FPGAReport err, String cmd) {
      super(TOOLCHAIN, "Lattice", err);
    }

    private Path getRelativePathAsPath(String pathRoot, String... pathes) {
      return Paths.get("..").resolve(Paths.get(projectPath).relativize(Paths.get(pathRoot, pathes)));
    }

    private String getRelativePath(String pathRoot, String... pathes) {
      return getRelativePathAsPath(pathRoot, pathes).toString();
    }

    @Override
    public boolean readyForDownload() {
      String bitFileExt = ".jed";
      //return new File(sandboxPath + TOP_HDL + bitFileExt).exists();
      //return Files.exists(Paths.get(sandboxPath).resolve("impl1").resolve(PROJECT_NAME+"_impl1"+bitFileExt));
      return Files.exists(getAbsolutePathAsPath(sandboxPath, "impl1", PROJECT_NAME+"_impl1"+bitFileExt));
    }

    private ArrayList<String> cmd(String prog, String... args) {
      ArrayList<String> command = new ArrayList<>();
      //command.add(Paths.get(settings.GetLatticeToolPath()).resolve(prog).toString());
      if (prog.endsWith(".cmd") || prog.endsWith(".bat")) {
        command.add("cmd");
        command.add("/c");
      }
      command.add(prog);
      for (String arg: args)
        command.add(arg);
      return command;
    }

    @Override
    public boolean createSynthesisPlan(ArrayList<Stage> stages) {

      SYSTEM toolChainType = getToolChainType();

      String synthFile;
      switch (toolChainType) {
        case DIAMOND_WIN: synthFile = PROJECT_RUN_FILE; break;
        case DIAMOND_UNIX: synthFile = PROJECT_RUN_FILE_UNIX; break;
        case ISP_LEVER_WIN: synthFile = PROJECT_RUN_FILE_ISPLEVER; break;
        case ISP_LEVER_UNIX: 
        case UNKNOWN:
        default: return false;
      }

      String script = getRelativePath(scriptPath, synthFile);
      //String log = script.subSequence(0, script.lastIndexOf('.'))+".log"; 
      stages.add(new ProcessStage(
            "synthesize", "Synthesizing (may take a while)",
            cmd(script),
            "Failed to synthesize Lattice project, cannot download"));

      return true;
    }

    @Override
    public boolean createProgrammingPlan(ArrayList<Stage> stages) {

      SYSTEM toolChainType = getToolChainType();

      if (programmer != null && !(programmer instanceof LatticeProgrammer)) {
        err.AddFatalError("Lattice toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Lattice programmer.");
        return false;
      }

      String downloadFile;
      switch (toolChainType) {
        case DIAMOND_WIN: downloadFile = PROJECT_DOWNLOAD_FILE; break;
        case DIAMOND_UNIX: downloadFile = PROJECT_DOWNLOAD_FILE_UNIX; break;
        case ISP_LEVER_WIN: downloadFile = PROJECT_DOWNLOAD_FILE_ISPLEVER; break;
        case ISP_LEVER_UNIX: 
        case UNKNOWN:
        default: return false;
      }
      downloadFile = getRelativePath(scriptPath, downloadFile);

      stages.add(new ProcessStage("download", "Downloading to FPGA",
            cmd(downloadFile),
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

    private boolean generateProjectFile(ArrayList<String> hdlFiles) {

      // --- ispLEVER project file ---  
      AuxFile out = new AuxFile(sandboxPath, PROJECT_FILE_ISPLEVER, err);

      Chipset chip = board.fpga;
      String device = String.format("%s%s%s", chip.Part, chip.SpeedGrade, chip.Package);

      out.stmt("JDF B");
      out.stmt("// Created by Version 6.1");
      out.stmt("DESIGN %s Normal", name);
      out.stmt("DEVKIT %s", device);
      String hdl_type = lang.toUpperCase();
      out.stmt("ENTRY Schematic/%s", hdl_type);
      // File flpf = FileWriter.GetFilePointer(ucfPath, lpf_file, err);
      // out.stmt("DOCUMENT %s", flpf.getAbsolutePath().replace("\\", "/"));
      for (String fname : hdlFiles) {
        out.stmt("MODULE %s", getRelativePath(fname));

        String moduleName = fname;
        if (moduleName.endsWith(".vhdl")) moduleName = moduleName.substring(0, moduleName.length()-1);
        if (!moduleName.contains("_entity.vhd")) {
          //moduleName = fname.replace("_entity.vhd", "");
          moduleName = moduleName.replace("_behavior.vhd", "");
        }
        int idx = moduleName.lastIndexOf('\\');
        if (idx <= 0) idx = moduleName.lastIndexOf('/');
        if (idx >= 0) moduleName = moduleName.substring(idx+1);
        out.stmt("MODSTYLE %s Normal", moduleName);
      }
      out.stmt("SYNTHESIS_TOOL Synplify");
      out.stmt("TOPMODULE LogisimToplevelShell");

      boolean success = out.save();

      // ---- lattice diamond scripts

      // tcl-project
      out = new AuxFile(scriptPath, PROJECT_CREATION_TCL_FILE, err);
      final String synth_engine = "lse";
      out.stmt("prj_project new -name \"%s\" -lpf \"%s.lpf\" -impl \"impl1\" -dev %s -synthesis \"%s\"", PROJECT_NAME, PROJECT_NAME, device, synth_engine);

      for (String fname : hdlFiles) {
        //String path = fname.replace(projectPath, "../");
        out.stmt("prj_src add \"%s\"", toTclPath(getRelativePathAsPath(fname)));
      }
      out.stmt("prj_project save");

      success &= out.save();

      return success;
    }

    private boolean generateLpfFile(PinBindings ioResources) {
      // ispLEVER needs the constraints file next to the project file
      AuxFile lpf = new AuxFile(sandboxPath, PROJECT_CONSTRAINT_FILE, err);
      if (!writeLatticeConstraintLPF(lpf, board, ioResources))
        return false;

      // Also write to ucfPath... for diamond?
      return lpf.saveAs(sandboxPath, PROJECT_CONSTRAINT_FILE);
    }

    private boolean generateRunScript() {

      //String vhdlListPath = scriptPath.replace(projectPath, "../") + vhdl_list_file;
      AuxFile out = new AuxFile(scriptPath, PROJECT_RUN_TCL_FILE, err);

      out.stmt("prj_project open \"%s\"", PROJECT_FILE);
      out.stmt("");
      out.stmt("prj_run Synthesis -impl impl1");
      out.stmt("prj_run Translate -impl impl1");
      out.stmt("prj_run Map -impl impl1");
      out.stmt("prj_run PAR -impl impl1");
      out.stmt("prj_run PAR -impl impl1 -task PARTrace");
      out.stmt("prj_run Export -impl impl1 -task Bitgen");
      out.stmt("prj_project close");

      boolean success = out.save();

      // --- windows synthesis script ---

      Path latticeToolPath = Paths.get(AppPreferences.LATTICE_PATH.get());
      String binDirectory = latticeToolPath.getFileName().toString();	// either "nt" or "nt64"

      // detect directory of tcl-library by going upwards above the bin-directory and downwards to the tcl-lib
      Path toolPath = latticeToolPath;
      while (((toolPath != null) && Files.notExists(toolPath.resolve("bin")))) {
        toolPath = toolPath.getParent();
      }
      Path tclLibPath = toolPath.resolve("tcltk").resolve("lib");
      try {
        Path tcllib = Paths.get("");
        List<Path> list = Files.list(tclLibPath).filter(p -> (p.getFileName().toString().startsWith("tcl") && Files.isDirectory(p))).collect(Collectors.toList()); 
        for (Path ptcl : list) {
          // take the directory with the longest name
          if (tcllib.toString().length() < ptcl.toString().length()) {
            tcllib = ptcl;
          }
        }
        tclLibPath = tcllib;
      } catch (IOException e) {
      }

      Path tcl_prj_creation_file = getRelativePathAsPath(scriptPath, PROJECT_CREATION_TCL_FILE);
      Path tcl_prj_synth_file = getRelativePathAsPath(scriptPath, PROJECT_RUN_TCL_FILE);

      String fpgaToolPath = getToolPath();
      SYSTEM toolChainTpye = getToolChainTypeFromToolPath(fpgaToolPath);
      Path latticeTool = Paths.get(fpgaToolPath); // latticeToolPath.resolve(fpgaTool);

      if (toolChainTpye == SYSTEM.DIAMOND_WIN) {
        out = new AuxFile(scriptPath, PROJECT_RUN_FILE, err);

        out.stmt("@echo off", toWinPath(toolPath));
        out.stmt("set LCD_DIAMOND_PATH=%s", toWinPath(toolPath));
        out.stmt("");
        out.stmt("set LSC_INI_PATH=");
        out.stmt("set LSC_DIAMOND=true");
        out.stmt("set TCL_LIBRARY=%s", toWinPath(tclLibPath));
        out.stmt("set FOUNDRY=%s\\ispfpga", toWinPath(toolPath));
        out.stmt("set PATH=%%FOUNDRY%%\\bin\\%s;%%PATH%%", binDirectory);
        out.stmt("\"%s\" %s", toWinPath(latticeTool), toWinPath(tcl_prj_creation_file));
        out.stmt("\"%s\" %s", toWinPath(latticeTool), toWinPath(tcl_prj_synth_file));
        out.stmt("EXIT /B %%ERRORLEVEL%%");

        success &= out.save();
      }

      // ---- Unix and Cygwin synthesis script ----
      if (toolChainTpye != SYSTEM.ISP_LEVER_WIN) {
        out = new AuxFile(scriptPath, PROJECT_RUN_FILE_UNIX, err);
        out.stmt("export TEMP=/tmp");
        out.stmt("export LSC_INI_PATH=\"\"");
        out.stmt("export LSC_DIAMOND=true");
        out.stmt("export TCL_LIBRARY=%s", toUnixPath(tclLibPath));
        out.stmt("export FOUNDRY=%s/ispFPGA", toUnixPath(toolPath));
        out.stmt("export PATH=$FOUNDRY/bin/%s:$PATH", binDirectory);
        out.stmt("%s %s", toUnixPath(latticeTool), toUnixPath(tcl_prj_creation_file));
        out.stmt("%s %s", toUnixPath(latticeTool), toUnixPath(tcl_prj_synth_file));
        success &= out.save();
      } else {
        // --- ispLEVER synthesis file => open project navigator for the user
        String prj_isplever_file = getRelativePath(sandboxPath, PROJECT_FILE_ISPLEVER);
        String projnav = toWinPath(latticeToolPath.resolve("projnav.exe"));
        out = new AuxFile(scriptPath, PROJECT_RUN_FILE_ISPLEVER, err);
        out.stmt("@echo off");
        out.stmt("rem start project navigator and wait until it is finished. The user has to perfrom the synthesis!");
        out.stmt("start /B /wait \"%s\" \"%s\"", projnav, prj_isplever_file);
        success &= out.save();
      }

      return success;
    }

    private boolean generateDownloadScript() {

      // --- download tcl file --- 
      String srcXcfFile = toTclPath(getRelativePathAsPath(sandboxPath, PROJECT_NAME+".xcf"));
      String cpyXcfFile = toTclPath(getRelativePathAsPath(sandboxPath, PROJECT_NAME+"_latest.xcf"));
      AuxFile out = new AuxFile(scriptPath, PROJECT_DOWNLOAD_TCL_FILE, err);
      out.stmt("pgr_project open \"%s\"", srcXcfFile);
      out.stmt("pgr_program set -cable %s -portaddress %s", "usb2", "FTUSB-0");
      out.stmt("pgr_project save \"%s\"", cpyXcfFile);
      out.stmt("pgr_project close");
      out.stmt("");
      out.stmt("pgr_project open \"%s\"", cpyXcfFile);
      out.stmt("if {[catch {");
      out.stmt("	pgr_program run");
      out.stmt("} result]} {");
      out.stmt("    # in case of failure, try with FTUSB-1");
      out.stmt("	pgr_program set -portaddress FTUSB-1");
      out.stmt("	pgr_program run");
      out.stmt("}");
      out.stmt("pgr_project close");
      boolean success = out.save();

      // --- download xcf-filefor Lattice Diamond ----
      Chipset chip = board.fpga;
      String chip_family = chip.Technology;

      out = new AuxFile(sandboxPath, PROJECT_DOWNLOAD_CONFIG_FILE, err);
      out.stmt("<?xml version='1.0' encoding='utf-8' ?>");
      out.stmt("<!DOCTYPE  ispXCF SYSTEM \"IspXCF.dtd\" >");
      out.stmt("<ispXCF version=\"3.12\">");
      out.stmt("  <Comment></Comment>");
      out.stmt("  <Chain>");
      out.stmt("    <Comm>JTAG</Comm>");
      out.stmt("    <Device>");
      out.stmt("      <Pos>%d</Pos>", chip.JTAGPos);
      out.stmt("      <Vendor>%s</Vendor>", chip.Vendor);
      out.stmt("      <Family>%s</Family>", chip_family);
      out.stmt("      <Name>%s</Name>", chip.Part);
      out.stmt("      <Package>All</Package>");
      out.stmt("      <PON>%s</PON>", chip.Part);

      out.stmt("      <Bypass>");
      out.stmt("        <InstrLen>8</InstrLen>");
      out.stmt("        <InstrVal>11111111</InstrVal>");
      out.stmt("        <BScanLen>1</BScanLen>");
      out.stmt("        <BScanVal>0</BScanVal>");
      out.stmt("      </Bypass>");

      out.stmt("      <File>%simpl1/%s_impl1.jed</File>", "../sandbox/", PROJECT_NAME);
      out.stmt("      <Operation>FLASH Erase,Program,Verify</Operation>");

      out.stmt("      <Option>");
      out.stmt("        <SVFVendor>JTAG STANDARD</SVFVendor>");
      out.stmt("        <IOState>HighZ</IOState>");
      out.stmt("        <IOVectorData>0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF</IOVectorData>");
      out.stmt("        <OverideUES value=\"TRUE\"/>");
      out.stmt("        <TCKFrequency>1.000000 MHz</TCKFrequency>");
      out.stmt("        <SVFProcessor>ispVM</SVFProcessor>");
      out.stmt("      </Option>");

      out.stmt("    </Device>");
      out.stmt("  </Chain>");

      out.stmt("  <ProjectOptions>");
      out.stmt("    <Program>SEQUENTIAL</Program>");
      out.stmt("    <Process>ENTIRED CHAIN</Process>");
      out.stmt("    <OperationOverride>No Override</OperationOverride>");
      out.stmt("    <StartTAP>TLR</StartTAP>");
      out.stmt("    <EndTAP>TLR</EndTAP>");
      out.stmt("    <DeGlitch value=\"TRUE\"/>");
      out.stmt("    <VerifyUsercode value=\"TRUE\"/>");
      out.stmt("    <TCKDelay>1</TCKDelay>");
      out.stmt("  </ProjectOptions>");

      out.stmt("</ispXCF>");

      success &= out.save();

      // --- download run scripts
      String fpgaToolPath = getToolPath();
      SYSTEM toolChainType = getToolChainTypeFromToolPath(fpgaToolPath);
      if (fpgaToolPath == null || toolChainType == null) {
        return false;
      }
      Path latticeToolPath = Paths.get(AppPreferences.LATTICE_PATH.get());
      Path latticeTool = Paths.get(fpgaToolPath); // latticeToolPath.resolve(fpgaTool);

      // --- Windows ----
      if (toolChainType == SYSTEM.DIAMOND_WIN) {
        out = new AuxFile(scriptPath, PROJECT_DOWNLOAD_FILE, err);
        out.stmt("\"%s\" \"%s\"", toWinPath(latticeTool), toWinPath(getRelativePathAsPath(scriptPath, PROJECT_DOWNLOAD_TCL_FILE)));
        success &= out.save();
      }

      // --- Unix and Cygwin ---
      if (toolChainType != SYSTEM.ISP_LEVER_WIN) {
        out = new AuxFile(scriptPath, PROJECT_DOWNLOAD_FILE_UNIX, err);
        out.stmt("%s \"%s\"", toUnixPath(latticeTool), toUnixPath(getRelativePathAsPath(scriptPath, PROJECT_DOWNLOAD_TCL_FILE)));
        success &= out.save();
      } else {
        // --- ispLEVER / ispVM ---
        out = new AuxFile(scriptPath, PROJECT_DOWNLOAD_FILE_ISPLEVER, err);
        out.stmt("@echo off");
        out.stmt("rem start ispVM and wait until it is finished. The user has to perfrom the download!");
        out.stmt("cd \"%s\"", toWinPath(getRelativePathAsPath(sandboxPath)));
        out.stmt("if not exist impl1\\ md impl1");
        out.stmt("copy /Y %s.jed impl1\\%s_impl1.jed", PROJECT_NAME, PROJECT_NAME);
        out.stmt("start /B /wait %s/../../ispvmsystem/ispVM.exe -infile %s -cabletype %s -portaddress %s -logfile %s -o", toWinPath(latticeToolPath), PROJECT_DOWNLOAD_CONFIG_FILE, "usb2", "ftusb-0", "output_download.txt");
        // -o opens a windows with information
        out.stmt("if %%ERRORLEVEL%% == 0 goto finish");
        out.stmt("start /B /wait %s/../../ispvmsystem/ispVM.exe -infile %s -cabletype %s -portaddress %s -logfile %s -o", toWinPath(latticeToolPath), PROJECT_DOWNLOAD_CONFIG_FILE, "usb2", "ftusb-1", "output_download.txt");
        out.stmt(":finish");
        out.stmt("EXIT /B %%ERRORLEVEL%%");
        success &= out.save();
      }

      return success;
    }

    @Override
    public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {
      return generateProjectFile(hdlFiles)
        && generateLpfFile(ioResources)
        && generateRunScript()
        && generateDownloadScript();
    }

    private final static String PROJECT_NAME = "LatticeProject";
    private final static String PROJECT_FILE = PROJECT_NAME+".ldf";
    private final static String PROJECT_FILE_ISPLEVER = PROJECT_NAME+".syn";
    private final static String PROJECT_CONSTRAINT_FILE = PROJECT_NAME+".lpf";
    private final static String PROJECT_CREATION_TCL_FILE = PROJECT_NAME+"_create.tcl";
    private final static String PROJECT_RUN_TCL_FILE = PROJECT_NAME+"_run.tcl";
    private final static String PROJECT_DOWNLOAD_TCL_FILE = PROJECT_NAME+"_download.tcl";
    private final static String PROJECT_RUN_FILE = PROJECT_NAME+"_run.cmd";
    private final static String PROJECT_RUN_FILE_UNIX = PROJECT_NAME+"_run.sh";
    private final static String PROJECT_RUN_FILE_ISPLEVER = PROJECT_NAME+"_run_ispLEVER.cmd";
    private final static String PROJECT_DOWNLOAD_CONFIG_FILE = PROJECT_NAME+".xcf";
    private final static String PROJECT_DOWNLOAD_FILE = PROJECT_NAME+"_download.cmd";
    private final static String PROJECT_DOWNLOAD_FILE_ISPLEVER = PROJECT_NAME+"_download_ispLEVER.cmd";
    private final static String PROJECT_DOWNLOAD_FILE_UNIX = PROJECT_NAME+"_download.sh";

  }

  protected static class LatticeProgrammer extends FPGAProgrammer {
    // TODO: reorganize stages above, e.g. allowing for
    // openFPGALoader, maybe LPT download, etc.
    LatticeProgrammer(FPGAReport err, String cmd) { super(TOOLCHAIN, "Lattice", err); }
    // The work for lattice upload is done above, in createProgrammingPlan().
  }

  static void writeIoSpec(AuxFile lpf, String net, InputBias bias, IoStandard standard, DriveStrength strength) {
    String iospec = "";

    if (bias == InputBias.PULL_UP) iospec += " PULLMODE=UP";
    else if (bias == InputBias.PULL_DOWN) iospec += " PULLMODE=DOWN";
    else if (bias == InputBias.BUS_HOLD) iospec += " PULLMODE=KEEPER";
    else if (bias == InputBias.PULL_NONE) iospec += " PULLMODE=NONE";

    if (standard != IoStandard.DEFAULT)
      iospec += " IO_TYPE=" + standard;

    if (strength != DriveStrength.DEFAULT)
      iospec += " DRIVE=" + (strength.ma != null ? strength.ma : strength.desc);

    if (!iospec.isEmpty())
      lpf.stmt("IOBUF PORT \"%s\"%s;", net, iospec);
  }

  // Create a Lattice ".lpf" constraint file
  static boolean writeLatticeConstraintLPF(AuxFile lpf, Board board, PinBindings ioResources) {
    System.err.println("not yet tested");
    // syntax:
    //   LOCATE COMP "net" SITE "pin";
    //   IOBUF PORT "net" PULLMODE=UP/DOWN/KEEPER/NONE
    if (ioResources.requiresOscillator) {
      lpf.stmt("FREQUENCY PORT \"%s\" %s;", CLK_PORT, Altera.formatFreqForFMAX(board.fpga.ClockFrequency).toUpperCase());
      lpf.stmt("LOCATE COMP \"%s\" SITE \"%s\";", CLK_PORT, board.fpga.ClockPinLocation);
      writeIoSpec(lpf, CLK_PORT, InputBias.PULL_NONE, board.fpga.ClockIOStandard, DriveStrength.DEFAULT);
    }
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      lpf.stmt("LOCATE COMP \"%s\" SITE \"%s\";", net, pin);
      if (net.startsWith("FPGA_INPUT_PIN_")) {
        InputBias bias = ioResources.getInputBias(net);
        writeIoSpec(lpf, net, bias, io.standard, io.strength);
      } else if (net.startsWith("FPGA_BIDIR_PIN_")) {
        // FIXME: use TRELLIS_IO block, and apply bias there instead of here?
        InputBias bias = ioResources.getInputBias(net);
        writeIoSpec(lpf, net, bias, io.standard, io.strength);
      } else if (net.startsWith("FPGA_OUTPUT_PIN_")) {
        // output pins do not have bias
        writeIoSpec(lpf, net, null, io.standard, io.strength);
      }
    });
    // FIXME: handle all unmapped pins
    return lpf.save();
  }



}
