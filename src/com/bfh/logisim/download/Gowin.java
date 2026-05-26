package com.bfh.logisim.download;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.DriveStrength;
import com.bfh.logisim.fpga.InputBias;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class Gowin {

  public static final Toolchain SYNTH_TOOLCHAIN = new GowinSynthToolchain();
  public static final Toolchain PROG_TOOLCHAIN = new GowinProgToolchain();

  public static final String GOWIN_SH = "gw_sh" + Toolchain.dotexe;
  public static final String GOWIN_PROG = "programmer_cli" + Toolchain.dotexe;

  private static class GowinSynthToolchain extends Toolchain {

    private GowinSynthToolchain() {
      super("Gowin EDA", "Gowin", true, false);
    }

    @Override
    public boolean hasAlternateName(String altname) {
      return 
        altname.equalsIgnoreCase("Gowin EDA")
        || altname.equalsIgnoreCase("GowinFPGA")
        || altname.equalsIgnoreCase("GowinFPGA EDA")
        || altname.equalsIgnoreCase("Gowin FPGA")
        || altname.equalsIgnoreCase("Gowin FPGA EDA");
    }

    @Override
    public boolean supports(Board b) {
      // TODO: gowin may have board definition files under IDE/data/device/ or
      // similar, which we could mabye search for the given board fpga part.
      return b.name.toLowerCase().contains("gowin")
        || b.codename.toLowerCase().contains("gowin");
    }

    @Override
    public List<String> getLanguages(Board board) {
      return List.of(VERILOG, VHDL);
    }

    @Override
    public String defaultParamsAsString(/*Board board*/) {
      // TODO: Does gowin shell have any useful options?
      return "";
    }

    @Override
    public FPGASynthesizer newSynthesizer(FPGAReport err) {
      String gowin_sh = getInstalledCommand(err);
      return gowin_sh == null ? null : new GowinSynthesizer(err, gowin_sh);
    }

    @Override
    public FPGAProgrammer newProgrammer(FPGAReport err) {
      return null;
    }

    // FIXME: on MacOS, we probably need to set up proper zsh env,
    // and should take path to .app directory to do that properly.
    private static final String helpmsg =
      "Either install " + GOWIN_SH + " to a system directory, "
      + "or set the toolchain path to point to a directory containing, "
      + "or to point to a stand-alone executable script.";

    @Override
    public InstallStatus toolchainInstallStatus() {
      return simpleInstallStatusHelper(
          AppPreferences.GOWIN_SHELL_PATH.get(), "SENTINEL_INVALID_FILE_ARGUMENT",
          helpmsg, GOWIN_SH);
    }

    @Override
    protected String simpleParseVersionHelper(String prog, String line) {
      // Typical output: "*** GOWIN Tcl Command Line Console  ***"
      if (line.toLowerCase().contains("gowin"))
        return "OK";
      else
        return null;
      // FIXME: we might be able to get version info from the directory name?
    }

  }

  private static class GowinProgToolchain extends Toolchain {

    private GowinProgToolchain() {
      super("Gowin Programmer", "Gowin", false, true);
    }

    @Override
    public boolean hasAlternateName(String altname) {
      return 
        altname.equalsIgnoreCase("Gowin Programmer")
        || altname.equalsIgnoreCase("Gowin programmer_cli");
    }

    @Override
    public boolean supports(Board b) {
      // TODO: gowin may have board definition files under IDE/data/device/ or
      // similar, which we could mabye search for the given board fpga part.
      return b.name.toLowerCase().contains("gowin")
        || b.codename.toLowerCase().contains("gowin");
    }

    @Override
    public List<String> getLanguages(Board board) {
      return List.of(VERILOG, VHDL);
    }

    @Override
    public String defaultParamsAsString(/*Board board*/) {
      // TODO: Does gowin programmer have any useful options?
      return "";
    }

    @Override
    public FPGASynthesizer newSynthesizer(FPGAReport err) {
      return null;
    }

    @Override
    public FPGAProgrammer newProgrammer(FPGAReport err) {
      String programmer_cli = getInstalledCommand(err);
      return programmer_cli == null ? null : new GowinProgrammer(err, programmer_cli);
    }

    // FIXME: on MacOS, we probably need to set up proper zsh env,
    // and should take path to .app directory to do that properly.
    private static final String helpmsg =
      "Either install " + GOWIN_PROG + " to a system directory, "
      + "or set the toolchain path to point to a directory containing it.";

    @Override
    public InstallStatus toolchainInstallStatus() {
      return simpleInstallStatusHelper(
          AppPreferences.GOWIN_PROGRAMMER_PATH.get(), "--help",
          helpmsg, GOWIN_PROG);
    }

    @Override
    protected String simpleParseVersionHelper(String prog, String line) {
      // Typical output: "Gowin FPGA Programmer command-line interface. Version V1.9.11.03 Education (64-bit) build(2536);"
      if (line.toLowerCase().contains("gowin")
          && line.toLowerCase().contains("programmer")
          && line.toLowerCase().contains("version")) {
        int i = line.toLowerCase().indexOf("version");
        String version = line.substring(i);
        if (version.startsWith(" "))
          version = version.substring(1);
        if (version.endsWith(";"))
          version = version.substring(0, version.length() - 1);
        if (!version.isEmpty())
          return "OK";
      }
      return null;
    }

  }


  private static class GowinSynthesizer extends FPGASynthesizer {
    
    private String gw_sh; // verified gw_sh command, inluding full path if needed

    private GowinSynthesizer(FPGAReport err, String gw_sh) {
      super(SYNTH_TOOLCHAIN, "Gowin", err);
      this.gw_sh = gw_sh;
    }

    @Override
    public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {
      System.err.println("not yet tested");

      AuxFile cst = new AuxFile(scriptPath, "io_map.cst", err);
      if (!writeGowinConstraintCST(cst, board, ioResources))
        return false;

      AuxFile sh = new AuxFile(scriptPath, "gw_download.tcl", err);
      sh.stmt("set_device %s", board.fpga.Part);
      for (String hf : hdlFiles)
        sh.stmt("add_file \"%s\"", hf);
      sh.stmt("add_file \"%s/%s\"", scriptPath.replace(File.separatorChar, '/'), "io_map.cst");
      sh.stmt("set_option -top_module \"%s\"", TOP_HDL);
      sh.stmt("set_option -output_base_name \"%s\"", TOP_HDL);
      sh.stmt("set_option -use_sspi_as_gpio 1");
      sh.stmt("run all");
      return sh.save();
    }

    private String bitstream() { return sandboxPath + "impl/pnr/"+TOP_HDL+".fs"; }

    @Override
    public boolean readyForDownload() {
      return new File(bitstream()).exists();
    }

    @Override
    public boolean createSynthesisPlan(ArrayList<Stage> stages) {
      if (!readyForDownload()) {
        String script = scriptPath.replace(projectPath, ".." + File.separator) + "gw_download.tcl";
        stages.add(new ProcessStage(
              "compile", "Executing Gowin syn & pnr",
              join(gw_sh, script),
              "Failed to to execute gowin syn & pnr"));
      }

      return createProgrammingPlan(stages);
    }

    @Override
    public boolean createProgrammingPlan(ArrayList<Stage> stages) {

      String bitstream = sandboxPath + "impl/pnr/"+TOP_HDL+".fs";
      
      // auto-select toolchain: gowin if installed, else openFPGALoader
      if (programmer == null) {
        Toolchain.InstallStatus status = PROG_TOOLCHAIN.toolchainInstallStatus();
        if (status.installed())
          programmer = new GowinProgrammer(err, status.cmd());
        else if (OpenFPGALoader.TOOLCHAIN.toolchainInstallStatus().installed())
          programmer = OpenFPGALoader.TOOLCHAIN.newProgrammer(err);
        else {
          err.AddFatalError("Gowin programmer and openFPGALoader aren't available or aren't configured properly. Install them, fix the settings, or try a different programmer.");
          return false;
        }
        cmdr.configure(programmer);
      }

      // If user (or board xml) specified OpenFPGALoader, then use it.
      if (programmer instanceof OpenFPGALoader) {
        OpenFPGALoader ofl = (OpenFPGALoader)programmer;
        return ofl.createProgrammingPlan(stages, bitstream);
      }

      if (programmer instanceof GowinProgrammer) {
        GowinProgrammer gwp = (GowinProgrammer)programmer;
        return gwp.createProgrammingPlan(stages, bitstream);
      }

      err.AddFatalError("Gowin toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Gowin programmer or openFPGALoader.");
      return false;
    }

  }


  private static class GowinProgrammer extends FPGAProgrammer {

    private String programmer_cli; // verified programmer_cli command, inluding full path if needed
    private String cableIndex;

    private GowinProgrammer(FPGAReport err, String programmer_cli) {
      super(PROG_TOOLCHAIN, "Gowin", err);
      this.programmer_cli = programmer_cli;
    }

    private String bitstream() { return sandboxPath + "impl/pnr/"+FPGASynthesizer.TOP_HDL+".fs"; }

    public boolean createProgrammingPlan(ArrayList<Stage> stages, String bitstream) {
      stages.add(new ProcessStage("download", "Download to selected FPGA", 
            join(programmer_cli, bitstream(), "--fsFile", bitstream(), "-r", "2", "--device", board.fpga.Technology),
            "Failed to download design") {
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

  }


  static void writeIoSpec(AuxFile cst, String net, InputBias bias, IoStandard standard, DriveStrength strength) {
    String iospec = "";

    if (bias == InputBias.PULL_UP) iospec += " PULL_MODE=UP";
    else if (bias == InputBias.PULL_DOWN) iospec += " PULL_MODE=DOWN";
    else if (bias == InputBias.BUS_HOLD) iospec += " PULL_MODE=KEEPER";
    else if (bias == InputBias.PULL_NONE) iospec += " PULL_MODE=NONE";

    if (standard != IoStandard.DEFAULT)
      iospec += " IO_TYPE=" + standard;

    if (strength != DriveStrength.DEFAULT)
      iospec += " DRIVE=" + (strength.ma != null ? strength.ma : strength.desc);

    if (!iospec.isEmpty())
      cst.stmt("IO_PORT \"%s\"%s;", net, iospec);
  }

  // Create a gowin-compatible ".cst" constraint file
  static boolean writeGowinConstraintCST(AuxFile cst, Board board, PinBindings ioResources) {
    System.err.println("not yet tested");
    if (ioResources.requiresOscillator) {
      cst.stmt("IO_LOC \"%s\" %s //%s;", FPGASynthesizer.CLK_PORT, board.fpga.ClockPinLocation, FPGASynthesizer.CLK_PORT);
      writeIoSpec(cst, FPGASynthesizer.CLK_PORT, InputBias.PULL_NONE, board.fpga.ClockIOStandard, DriveStrength.DEFAULT);
    }
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      cst.stmt("IO_LOC \"%s\" %s //%s;", net, pin, label);
      if (net.startsWith("FPGA_INPUT_PIN_")) {
        InputBias bias = ioResources.getInputBias(net);
        writeIoSpec(cst, net, bias, io.standard, io.strength);
      } else if (net.startsWith("FPGA_BIDIR_PIN_")) {
        // FIXME: use IOBUF block, and apply bias there instead of here?
        InputBias bias = ioResources.getInputBias(net);
        writeIoSpec(cst, net, bias, io.standard, io.strength);
      } else if (net.startsWith("FPGA_OUTPUT_PIN_")) {
        // output pins do not have bias
        writeIoSpec(cst, net, null, io.standard, io.strength);
      }
    });
    // FIXME: handle all unmapped pins
    return cst.save();
  }

}
