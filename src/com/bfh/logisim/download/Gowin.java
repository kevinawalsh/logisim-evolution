package com.bfh.logisim.download;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.prefs.AppPreferences;

public class Gowin {

  private static final Toolchain MY_TOOLCHAIN = new Toolchain("Gowin EDA", "Gowin", true, true) {
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
      // TODO: gowin may have board definition files under IDE/data/device/
      // or similar, which we could search for the given board fpga part.
      return b.name.toLowerCase().contains("gowin")
        || b.codename.toLowerCase().contains("gowin");
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
    public FPGADownload newDownloader() { return new GowinDownload(); }
    @Override
    public FPGAProgrammer newProgrammer() { return new GowinProgrammer(); }
  };

  public static void register() { Toolchain.register(MY_TOOLCHAIN); }

  // public static final String[] GOWIN_PROGRAMS = {"gw_sh" + dotexe};
  public static final String GOWIN_SH = "gw_sh" + Toolchain.dotexe;
  public static final String GOWIN_PROG = "programmer_cli" + Toolchain.dotexe;

  private static String resolve(String path, String progname) {
    if (path == null || path.isEmpty())
      return null;
    File prog = new File(path);
    // backwards compatibility: maybe it is a directory?
    if (prog.exists() && prog.isDirectory()) {
      path += File.separator + progname;
      prog = new File(path);
    }
    if (prog.exists() && !prog.isDirectory() && prog.canExecute())
      return path;
    return null;
  }

  private static String getGowinProgrammerPath() {
    return resolve(AppPreferences.GOWIN_PROGRAMMER_PATH.get(), GOWIN_PROG);
  }

  private static String getGowinShellPath() {
    return resolve(AppPreferences.GOWIN_SHELL_PATH.get(), GOWIN_SH);
  }

  private static class GowinDownload extends FPGADownload {

    private String cableIndex;
    private GowinDownload() {
      super("Gowin");
    }

    @Override
    public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {
      Hdl cst = new Hdl(lang, err);
      ioResources.forEachPhysicalPin((pin, net, io, label) -> {
        cst.stmt("IO_LOC \"%s\" %s //%s;", net, pin, label);
        if ("Pull Up".equals(io.pull.desc)) {
          cst.stmt("IO_PORT \"%s\" PULL_MODE=UP", net);
        }
        if ("Pull Down".equals(io.pull.desc)) {
          cst.stmt("IO_PORT \"%s\" PULL_MODE=DOWN", net);
        }
      });
      if (ioResources.requiresOscillator)
        cst.stmt("IO_LOC \"%s\" %s //%s;", CLK_PORT, board.fpga.ClockPinLocation, CLK_PORT);
      File fcst = FileWriter.GetFilePointer(scriptPath, "io_map.cst", err);
      if (fcst == null || !FileWriter.WriteContents(fcst, cst, err))
        return false;
      Hdl sh = new Hdl(lang, err);
      sh.stmt("set_device %s", board.fpga.Part);
      for (String hf : hdlFiles)
        sh.stmt("add_file \"%s\"", hf);
      sh.stmt("add_file \"%s/%s\"", scriptPath.replace(File.separatorChar, '/'), "io_map.cst");
      sh.stmt("set_option -top_module \"%s\"", TOP_HDL);
      sh.stmt("set_option -output_base_name \"%s\"", TOP_HDL);
      sh.stmt("set_option -use_sspi_as_gpio 1");
      sh.stmt("run all");
      File fsh = FileWriter.GetFilePointer(scriptPath, "gw_download.tcl", err);
      if (fsh == null || !FileWriter.WriteContents(fsh, sh, err))
        return false;
      return true;
    }

    @Override
    public boolean readyForDownload() {
      return new File(sandboxPath + "impl" + File.separator + "pnr" + File.separator + TOP_HDL + ".fs").exists();
    }

    private ArrayList<String> cmd(String prog, String... args) {
      ArrayList<String> command = new ArrayList<>();
      command.add(prog);
      for (String arg : args)
        command.add(arg);
      return command;
    }

    public boolean toolchainIsInstalled(FPGAReport err) {
      String helpmsg = "It should be set to the path of " + GOWIN_SH
        + " or of a compatible stand-alone executable script.";
      String shPath = getGowinShellPath();
      if (shPath == null) {
        err.AddFatalError("Gowin shell tool path not configured, or configured incorrectly. " + helpmsg);
        return false;
      }
      if (getGowinProgrammerPath() == null) {
        if (OpenFPGALoader.findExecutable(err) == null) {
          err.AddFatalError("Either Gowin " + GOWIN_PROG + " path must be specified in settings, or " 
              + "openFPGALoader must be installed and configured.");
          return false;
        }
      }
      return true;
    }
    @Override
    public ArrayList<Stage> initiateDownload(Commander cmdr) {
      ArrayList<Stage> stages = new ArrayList<>();
      if (!readyForDownload()) {
        String script = scriptPath.replace(projectPath, ".." + File.separator) + "gw_download.tcl";
        stages.add(new ProcessStage(
              "compile", "Executing Gowin syn & pnr",
              cmd(getGowinShellPath(), script),
              "Failed to to execute gowin syn & pnr"));
      }

      if (programmer != null && !(programmer instanceof GowinProgrammer) && !(programmer instanceof OpenFPGALoader)) {
        err.AddFatalError("Gowin toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Gowin programmer or openFPGALoader.");
        return stages;
      }
      
      String gwprog = getGowinProgrammerPath();

      boolean useOFL;
      if (programmer instanceof OpenFPGALoader) {
        useOFL = true;
      } else if (programmer instanceof GowinProgrammer) {
        useOFL = false;
        if (gwprog == null) {
          err.AddFatalError("Gowin toolchain internal programmer isn't available or isn't configured properly. Fix the settings, or try openFPGALoader instead.");
          return stages;
        }
      } else {
        // auto-select: only use OFL if internal programmer isn't available
        useOFL = (gwprog == null);
      }

      if (useOFL) { // try openFPGALoader
        String ofl = OpenFPGALoader.findExecutable(err);
        stages.add(new ProcessStage("scan", "Scaning for FPGA Devices",
              cmd(ofl, "--detect"),
              "Could not find any FPGA devices.") {
          @Override
          protected boolean prep() {
            if (!readyForDownload()) {
              console.printf(Console.ERROR, "Error: Design must be synthesized before download.");
              return false;
            }
            if (!cmdr.confirmDownload()) {
              cancelled = true;
              return false;
            }
            return true;
          }

          @Override
          protected boolean post() {
            ArrayList<String> dev = new ArrayList<>();
            StringBuilder curdev = null;

            for (String line : console.getText()) {
              if (line.trim().matches("^index \\d+:")) {
                if (curdev != null)
                  dev.add(curdev.toString());
                curdev = new StringBuilder(line.trim());
              }
              if (line.trim().matches("^idcode\\s+0x[0-9a-f]+")) {
                curdev.append(" " + line.trim().split("\\s+")[1]);
              }
              if (line.trim().matches("^model\\s+.*")) {
                curdev.append(" " + line.trim().split("\\s+")[1]);
              }
            }
            if (curdev != null)
              dev.add(curdev.toString());


            String devsel = dev.size() > 1 ? cmdr.chooseDevice(dev) : dev.get(0);
            cableIndex = devsel.split(":")[0].split("\\s+")[1];
            return super.post();
          }
        });
        stages.add(new ProcessStage("download", "Download to selected FPGA", null, "Failed to download design") {
          @Override
          protected boolean prep() {
            cmd = cmd(ofl,
                sandboxPath + "impl" + File.separator + "pnr" + File.separator + TOP_HDL + ".fs",
                "--cable-index", cableIndex);
            return true;
          }
        });
      } else {
        stages.add(new ProcessStage("download", "Download to selected FPGA", null, "Failed to download design") {
          @Override
          protected boolean prep() {
            cmd = cmd(gwprog,
                "--fsFile", sandboxPath + "impl" + File.separator + "pnr" + File.separator + TOP_HDL + ".fs",
                "-r", "2",
                "--device", board.fpga.Technology);
            return true;
          }
        });
      }
      return stages;
    }

  }

  protected static class GowinProgrammer extends FPGAProgrammer {
    // TODO: reorganize stages above, e.g. allowing for
    // openFPGALoader, separating out usb-tmc, etc.
    GowinProgrammer() { super("Gowin"); }
    @Override
    public boolean toolchainIsInstalled(FPGAReport err) {
      String gwprog = getGowinProgrammerPath();
      if (gwprog == null) {
        err.AddFatalError("Gowin toolchain internal programmer isn't available or isn't configured properly. Fix the settings, or try openFPGALoader instead.");
        return false;
      }
      return true;
    }
  }

}
