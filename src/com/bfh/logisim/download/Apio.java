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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.DriveStrength;
import com.bfh.logisim.fpga.InputBias;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.fpga.UnmentionedPinsBehavior;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.bfh.logisim.hdlgenerator.ToplevelHDLGenerator;
import com.bfh.logisim.netlist.Netlist;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.FileUtil;

public class Apio {

  private static final Toolchain MY_TOOLCHAIN = new Toolchain("Apio CLI", "Apio", true, true) {
    @Override
    public boolean hasAlternateName(String altname) {
      return 
        altname.equalsIgnoreCase("Apio CLI")
        || altname.equalsIgnoreCase("Apio IDE")
        || altname.equalsIgnoreCase("FPGAwars/apio");
    }
    @Override
    public boolean supports(Board b) {
      String codename = normalizeBoardName(b.codename);
      ArrayList<String> names = getApioBoardList();
      for (String name : names) {
        if (normalizeBoardName(name).equalsIgnoreCase(codename))
          return true;
      }
      return false;
    }
    @Override
    public List<String[]> defaultParams(/*Board board*/) {
      return List.<String[]>of(new String[] { "board", "passed to backend, defaults to board codename" });
    }
    @Override
    public List<String> getLanguages(Board board) {
      return List.of(VERILOG);
    }
    @Override
    public FPGADownload newDownloader() { return new ApioDownload(); }
    @Override
    public FPGAProgrammer newProgrammer() { return new ApioProgrammer(); }
  };

  public static void register() { Toolchain.register(MY_TOOLCHAIN); }

  // Apio board names tend to follow alhpanum-kebab-case conventions.
  private static String normalizeBoardName(String name) {
    name = name.replaceAll("[^a-zA-Z0-9]+", "-");
    if (name.startsWith("-")) name = name.substring(1);
    if (name.endsWith("-")) name = name.substring(0, name.length()-1);
    return name;
  }

  private static ArrayList<String> getApioBoardList() {
    ArrayList<String> ret = new ArrayList<>();
    String prog = findApioExecutable(AppPreferences.APIO_PATH.get());
    if (prog == null || prog.isEmpty())
      return ret;
    try {
      Process process = new ProcessBuilder(prog, "boards").start();
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      String line = reader.readLine().trim();
      String name = null;
      if (line.startsWith("| ") && line.endsWith(" |")) {
        name = line.substring(2).split("\\|", 2)[0].trim();
      } else if (line.startsWith("\u2502 ") && line.endsWith(" \u2502")) {
        name = line.substring(2).split("\u2502", 2)[0].trim();
      }
      if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("BOARD-ID"))
        ret.add(name);
    } catch (Exception e) {
      Debug.error("Executing `"+prog+" boards`", e);
    }
    return ret;
  }

  public static String findApioExecutable(String p) {
    if (p != null && !p.isEmpty()) {
      File script = new File(p);
      if (script.exists() && !script.isDirectory() && script.canExecute())
        return p;
      if (script.exists() && script.isDirectory()) {
        String pp = p + "/apio";
        script = new File(pp);
        if (script.exists() && !script.isDirectory() && script.canExecute())
          return pp;
        pp = p + "/bin/apio";
        script = new File(pp);
        if (script.exists() && !script.isDirectory() && script.canExecute())
          return pp;
      }
      return null;
    }
    // Try just using "apio", hope it is found on system path?
    return "apio";
  }

  public static class ApioDownload extends FPGADownload {

    private ApioDownload() { super("Apio"); }

    @Override
    public boolean readyForDownload() {
      return new File(sandboxPath + "_build/default/hardware.bin").exists();
    }

    String bin_apio;
    private ArrayList<String> apio(String ...args) {
      ArrayList<String> command = new ArrayList<>();
      command.add(bin_apio);
      for (String arg: args)
        command.add(arg);
      return command;
    }

    private String getApioVersion(String cmd) {
      try {
        Process process = new ProcessBuilder(cmd, "--version").start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        if (line.toLowerCase().startsWith("apio "))
          return line.substring("apio ".length());
      } catch (Exception e) {
        Debug.error("Executing `"+cmd+" --version`", e);
      }
      return null;
    }

    private static final String helpmsg =
      "Either install apio to a system directory, or set "
      + "the toolchain path to point to the apio executable or a "
      + "directory (e.g. a python virtualenv) containing bin/apio.";

    public boolean toolchainIsInstalled(FPGAReport err) {
      String tool = AppPreferences.APIO_PATH.get();
      // user wants system apio
      if (tool == null || tool.isEmpty()) {
        String version = getApioVersion("apio");
        if (version != null) {
          err.AddInfo("Using system installed apio, version " + version);
          return true;
        }
        err.AddFatalError("Apio toolchain path is not configured, and apio"
            + " does not appear to be installed in a system directory. " + helpmsg);
        return false;
      }
      // user wants custom apio
      String prog = Apio.findApioExecutable(tool);
      if (prog != null && !prog.isEmpty()) {
        String version = getApioVersion(prog);
        if (version != null) {
          err.AddInfo("Using " + prog + ", version " + version);
          return true;
        }
        err.AddFatalError("Apio toolchain path is set to '" + tool + "', but "
            + " `apio --version` still failed. " + helpmsg);
        return false;
      }
      return false;
    }

    private String findApioExecutable() {
      String p = AppPreferences.APIO_PATH.get();
      String script = Apio.findApioExecutable(p);
      if (script == null) {
        err.AddFatalError("Apio toolchain path is set to '" + p + "' but"
            + " the apio command was still not found. " + helpmsg);
      }
      return script;
    }

    @Override
    public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {

      bin_apio = findApioExecutable();
      if (bin_apio == null)
        return false;

      String board_name = board.paramFor(MY_TOOLCHAIN, "board");
      if (board_name == null)
        board_name = board.codename;

      // Apio does not have a global unused pins flag. The behavior is probably
      // weak pull-up for ice40 and similar boards, but we have no definitive
      // info for any particular fpga. So just warn here.
      if (board.fpga.UnmentionedPinsBehaviorHint == UnmentionedPinsBehavior.INPUT_PULL_UP) {
        err.AddWarning("FPGA board specifies " + board.fpga.UnmentionedPinsBehaviorHint
            + " for any remaining pins. This is the default for many FPGA configurations, but"
            + " Apio toolchain can't enforce or verify this, and some FPGAs may differ."
            + " Where critical, the board xml must explicitly reserve pins individually.");
      } else if (board.fpga.UnmentionedPinsBehaviorHint != UnmentionedPinsBehavior.UNSPECIFIED) {
        err.AddWarning("FPGA board specifies " + board.fpga.UnmentionedPinsBehaviorHint
            + " for any remaining pins, but apio toolchain can't enforce this. A default behavior"
            + " will be used instead, which may differ. Where critical, the board xml must explicitly"
            + " reserve pins individually.");
      }

      // Generate apio.ini
      AuxFile ini = new AuxFile(sandboxPath, "apio.ini", err);
      // FIXME: to support apio v0.9.5 and earlier, should use "[env]" here,
      // but version 1.0.0 and later expect "[env:default]".
      // Also, the earlier versions put bitstream in sandbox/hardware.bin,
      // but current versions put bistream in sandbox/_build/default/hardware.bin.
      ini.stmt("[env:default]");
      ini.stmt("board = " + board_name);
      ini.stmt("top-module = LogisimToplevelApioShell");
      ini.stmt("nextpnr-extra-options =");
      ini.stmt("    --freq %f", board.fpga.ClockFrequency/1000000.0);
      if (!ini.save())
        return false;

      boolean iCE40 = board.fpga.Vendor.equalsIgnoreCase("Lattice") 
        && board.fpga.Technology.equalsIgnoreCase("iCE40");
      boolean ECP5 = board.fpga.Vendor.equalsIgnoreCase("Lattice") 
        && board.fpga.Technology.equalsIgnoreCase("ECP5");
      boolean GOWIN = board.fpga.Vendor.equalsIgnoreCase("Gowin");

      // FIXME: 
      // Lattice iCE40UP/UL family has SB_HFOSC; HX/LP family requires an external clock pin.
      boolean hasHFOSC = iCE40 &&
        (board.fpga.Part.toUpperCase().contains("UP") ||
         board.fpga.Part.toUpperCase().contains("UL"));

      if (iCE40) {
        // For iCE40, generate fpga.pcf
        AuxFile pcf = new AuxFile(sandboxPath, "fpga.pcf", err);
        if (!writeiCE40ConstraintPCF(pcf, board, ioResources, hasHFOSC))
          return false;
      } else if (ECP5) {
        // For ecp5, generate fpga.lpf
        AuxFile lpf = new AuxFile(sandboxPath, "fpga.lpf", err);
        if (!Lattice.writeLatticeConstraintLPF(lpf, board, ioResources))
          return false;
      } else if (GOWIN) {
        // For gowin, generate fpga.cst
        AuxFile cst = new AuxFile(scriptPath, "fpga.cst", err);
        if (!Gowin.writeGowinConstraintCST(cst, board, ioResources))
          return false;
      } else {
        err.AddFatalError("Apio support for FPGA vendor '%s' family '%s' not yet implemented.",
            board.fpga.Vendor, board.fpga.Technology);
      }

      // Copy all HDL files to sandbox, renaming to avoid conflicts.
      HashSet<String> names = new HashSet<>();
      names.add("LogisimToplevelApioShell.v"); 
      for (String src : hdlFiles) {
        String relPath = Paths.get(circuitPath).relativize(Paths.get(src)).toString();
        String name = relPath.replaceAll("[^a-zA-Z0-9.]", "_").replaceAll("_+", "_").replaceAll("^_+", "");
        String prefix = "";
        int i = 0;
        while (names.contains(prefix+name)) {
          i++;
          prefix = "dup"+i+"_";
        }
        name = prefix + name;
        names.add(name);
        String dst = sandboxPath + name;
        try { FileUtil.copyFile(dst, src); }
        catch (IOException e) {
          err.AddFatalError(e.getMessage());
          return false;
        }
      }

      // Create LogisimToplevelApioShell.v
      Hdl out = new Hdl(lang, err);
      if (out.isVhdl) {
        err.AddSevereWarning("VHDL was chosen, but apio currently only supports Verilog.");
        err.AddSevereWarning("Design will almost certainly fail to compile.");
      }

      Netlist.Int3 ioPinCount = ioResources.countFPGAPhysicalIOPins();
      int n = ioPinCount.size();
      if (ioResources.requiresOscillator && !hasHFOSC) n++;  // FPGA_CLK as input port
      out.stmt("module LogisimToplevelApioShell(%s", (n == 0 ? " );" : ""));
      if (ioResources.requiresOscillator && !hasHFOSC)
        out.stmt("              FPGA_CLK%s", (--n == 0 ? " );" : ","));
      for (int i = 0; i < ioPinCount.in; i++)
        out.stmt("              FPGA_INPUT_PIN_%d%s", i, (--n == 0 ? " );" : ","));
      for (int i = 0; i < ioPinCount.inout; i++)
        out.stmt("              FPGA_BIDIR_PIN_%d%s", i, (--n == 0 ? " );" : ","));
      for (int i = 0; i < ioPinCount.out; i++)
        out.stmt("              FPGA_OUTPUT_PIN_%d%s", i, (--n == 0 ? " );" : ","));
      if (ioResources.requiresOscillator && !hasHFOSC)
        out.stmt("  input FPGA_CLK;");
      for (int i = 0; i < ioPinCount.in; i++)
        out.stmt("  input FPGA_INPUT_PIN_%d;", i);
      for (int i = 0; i < ioPinCount.inout; i++)
        out.stmt("  inout FPGA_BIDIR_PIN_%d;", i);
      for (int i = 0; i < ioPinCount.out; i++)
        out.stmt("  output FPGA_OUTPUT_PIN_%d;", i);
      out.stmt();

      n = ioPinCount.size();
      if (ioResources.requiresOscillator) {
        if (hasHFOSC) {
          out.stmt("  wire FPGA_CLK;");
          // For iCE40UP/UL fpga, use the high-speed oscillator (48 MHz).
          // FIXME: the SB_HFOSC block can divide by 1, 2, 4, or 8. We should
          // probably use that feature when the design clock settings call for clock
          // division. Or, use the SB_LFOSC block instead, which runs at 10 kHz.
          out.stmt("  SB_HFOSC internal_oscillator(.CLKHFPU(1'b1), .CLKHFEN(1'b1), .CLKHF(FPGA_CLK));");
          out.stmt();
        }
        // For iCE40HX/LP, FPGA_CLK is an input port wired to the board's external
        // crystal via PCF constraint; no internal oscillator primitive is needed.
        n++;
      }
      // ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      //   // todo: also handle bidirectional using SB_IO
      //   InputBias bias = ioResources.getInputBias(net);
      //   int pullup = 0;
      //   if (bias == InputBias.PULL_UP) {
      //     pullup = 1;
      //   } else if (bias == InputBias.PULL_DOWN) {
      //     err.AddSevereWarning("FPGA pin %s pull behavior specified as %s, but apio only supports pull-up.", pull);
      //   } else if (bias == InputBias.BUS_HOLD) {
      //     todo;
      //   } else if (bias == InputBias.NONE) {
      //     return; // handled above (elsewhere? where??? FIXME)
      //   } else {
      //     // do not specify
      //     return;
      //   }
      //   out.stmt("  wire %s;", "PULLED_"+net);
      //   // PIN_TYPE = 6 bits = xxxx_yy, xxxx=1010 is tri-state output, yy=01 is
      //   // simple non-clocked input, etc.
      //   out.stmt("  SB_IO #(.PIN_TYPE(6'b 0000_01), .PULLUP(1'b %d))", pullup);
      //   out.stmt("        sb_pin_%s (.PACKAGE_PIN(%s), .D_IN_0(%s));", pin, net, "PULLED_"+net);
      //   out.stmt();
      // });
     
      // For each FPGA bidir pin, emit an SB_IO block to bring together the
      // input, output, and enable nets.
      for (int i = 0; i < ioPinCount.inout; i++) {
        if (iCE40) {
          String net = "FPGA_BIDIR_PIN_" + i;
          String pullup;
          InputBias bias = ioResources.getInputBias(net);
          if (bias == InputBias.PULL_UP) {
            pullup = ", .PULLUP(1'b 1)";
          } else if (bias == InputBias.PULL_DOWN) {
            err.AddSevereWarning("FPGA pin %s pull-down is not possible for iCE40 FPGA. Using pull-none instead.", net);
            pullup = ", .PULLUP(1'b 0)";
          } else if (bias == InputBias.BUS_HOLD) {
            err.AddSevereWarning("FPGA pin %s bus-hold is not possible for iCE40 FPGA. Using pull-none instead.", net);
            pullup = ", .PULLUP(1'b 0)";
          } else if (bias == InputBias.PULL_NONE) {
            pullup = ", .PULLUP(1'b 0)";
          } else { // DO_NOT_SPECIFY
            pullup = "";
          }
          out.stmt("  wire %s;", net+"_IN");
          out.stmt("  wire %s;", net+"_OUT");
          out.stmt("  wire %s;", net+"_EN");
          out.stmt("  SB_IO #(.PIN_TYPE(6'b 1010_01)%s)", pullup);
          out.stmt("        sb_bidir_%d (.PACKAGE_PIN(%s),", i, net);
          out.stmt("                    .OUTPUT_ENABLE(%s),", net+"_EN");
          out.stmt("                    .D_OUT_0(%s),", net+"_OUT");
          out.stmt("                    .D_IN_0(%s));", net+"_IN");
          out.stmt();
        } else if (ECP5) {
          err.AddFatalError("Apio support for FPGA vendor '%s' family '%s' not yet implemented.",
              board.fpga.Vendor, board.fpga.Technology);
          // FIXME: emit appropriate verilog for ECP5, e.g.:
          // TRELLIS_IO #(.DIR("BIDIR"), .PULLMODE("UP/DOWN/KEEPER/NONE"))
          //    ecp5_bidir_%d (.B(some_net),
          //                   .T(~some_net_en),  // note: active-low tristate enable on ECP5
          //                   .O(some_net_in),
          //                   .I(some_net_out));
        } else if (GOWIN) {
          err.AddFatalError("Apio support for FPGA vendor '%s' family '%s' not yet implemented.",
              board.fpga.Vendor, board.fpga.Technology);
          // FIXME: emit appropriate verilog for Gowin, e.g.:
          // IOBUF #(.PULL_MODE("UP/DOWN/KEEPER/NONE"))
          //   gowin_bidir_%d (.IO(some_net),
          //                   .OEN(~some_net_en),  // also active-low
          //                   .O(some_net_in),
          //                   .I(some_net_out));
        } else {
          err.AddFatalError("Apio support for FPGA vendor '%s' family '%s' not yet implemented.",
              board.fpga.Vendor, board.fpga.Technology);
        }
      }

      out.stmt("  LogisimToplevelShell wrappedShell( %s", (n == 0 ? " );" : ""));
      if (ioResources.requiresOscillator)
        out.stmt("              .FPGA_CLK(FPGA_CLK)%s", (--n == 0 ? " );" : ","));
      for (int i = 0; i < ioPinCount.in; i++) {
        String net = "FPGA_INPUT_PIN_"+i;
        // if (ioResources.getInputPinPull(net) != PullBehavior.NONE)
        //   net = "PULLED_"+net;
        out.stmt("              .FPGA_INPUT_PIN_%d(%s)%s", i, net, (--n == 0 ? " );" : ","));
      }
      for (int i = 0; i < ioPinCount.inout; i++) {
        out.stmt("              .FPGA_BIDIR_PIN_%d_IN(FPGA_BIDIR_PIN_%d_IN),", i, i);
        out.stmt("              .FPGA_BIDIR_PIN_%d_OUT(FPGA_BIDIR_PIN_%d_OUT),", i, i);
        out.stmt("              .FPGA_BIDIR_PIN_%d_EN(FPGA_BIDIR_PIN_%d_EN)%s", i, i, (--n == 0 ? " );" : ","));
      }
      for (int i = 0; i < ioPinCount.out; i++)
        out.stmt("              .FPGA_OUTPUT_PIN_%d(FPGA_OUTPUT_PIN_%d)%s", i, i, (--n == 0 ? " );" : ","));
      out.stmt();

      out.stmt("endmodule");
      File f = FileWriter.GetFilePointer(sandboxPath, "LogisimToplevelApioShell.v", err);
      if (f == null || !FileWriter.WriteContents(f, out, err))
        return false;

      return true;
    }

    @Override
    public ArrayList<Stage> initiateDownload(Commander cmdr) {

      ArrayList<Stage> stages = new ArrayList<>();
      bin_apio = findApioExecutable();
      if (bin_apio == null)
        return stages;

      // synthesize
      if (!readyForDownload()) {
        stages.add(new ProcessStage(
              "synthesis", "Synthesizing (may take a while)",
              apio("build"),
              "Failed to synthesize design, cannot download"));
      }

      if (programmer != null && !(programmer instanceof ApioProgrammer) && !(programmer instanceof OpenFPGALoader)) {
        err.AddFatalError("Apio toolchain isn't yet enabled to work with " + programmer.name + " programmer, only the built-in Apio programmer or openFPGALoader.");
        return stages;
      }

      boolean useOFL;
      if (programmer instanceof OpenFPGALoader) {
        useOFL = true;
      } else if (programmer instanceof ApioProgrammer) {
        useOFL = false;
      } else {
        // auto-select: use openFPGALoader when the board explicitly specifies an
        // openfpgaloader board name (indicative of boards whose flash chip is not
        // supported by apio/iceprog), otherwise use apio upload.
        // useOFL = bin_ofl != null && OpenFPGALoader.supports(board);
        // Or: 
        useOFL = false; // if board really needs openFPGA, board xml should specify it
                        // as default and user should follow that recommendation.
      }

      // upload: use openFPGALoader when the board specifies it (e.g. boards whose
      // flash chip is not supported by apio/iceprog), otherwise use apio upload.
      if (useOFL) { 
        String bin_ofl = OpenFPGALoader.findExecutable(err);
        if (bin_ofl == null) {
          err.AddFatalError("openFPGALoader toolchain isn't installed or isn't configured properly. Fix the settings, or try the Apio programmer instead.");
          return stages;
        }
        stages.add(new ProcessStage(
              "upload", "Uploading to FPGA via openFPGALoader",
              OpenFPGALoader.commandFor(board, bin_ofl, sandboxPath + "_build/default/hardware.bin"),
              "Failed to upload design; did you connect the board?") {
          @Override
          protected boolean prep() {
            if (!cmdr.confirmDownload()) {
              cancelled = true;
              return false;
            }
            return true;
          }
        });
        return stages;
      }

      stages.add(new ProcessStage(
            "upload", "Uploading to FPGA",
            apio("upload"),
            "Failed to upload design; did you connect the board?") {
        @Override
        protected boolean prep() {
          if (!cmdr.confirmDownload()) {
            cancelled = true;
            return false;
          }
          return true;
        }
      });

      return stages;
    }

    public ToplevelHDLGenerator toplevelHDLGenerator(Netlist.Context ctx, PinBindings pinBindings) {
      return new ToplevelHDLGenerator(ctx, pinBindings, false);
    }

  }

  protected static class ApioProgrammer extends FPGAProgrammer {
    // TODO: reorganize stages above, e.g. allowing for
    // openFPGALoader, separating out usb-tmc, etc.
    ApioProgrammer() { super("Apio"); }
    @Override
    public boolean toolchainIsInstalled(FPGAReport err) { return true; } // only relevant if ApioDownload reported okay
  }
  
  // Create an iCE40-compatible ".pcf" constraint file
  static boolean writeiCE40ConstraintPCF(AuxFile pcf, Board board, PinBindings ioResources, boolean hasHFOSC) {
    if (ioResources.requiresOscillator && !hasHFOSC)
      pcf.stmt("set_io --warn-no-port %s %s", FPGADownload.CLK_PORT, board.fpga.ClockPinLocation);
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      if (io.standard != IoStandard.DEFAULT)
        pcf.err.AddSevereWarning("FPGA pin %s specifies ioStandard '%s' but this is not configurable for iCE40 FPGA in Apio. Using DEFAULT instead.", pin);
      if (io.strength != null && io.strength != DriveStrength.DEFAULT)
        pcf.err.AddSevereWarning("FPGA pin %s specifies drive strength '%s' but this is not configurable for iCE40 FPGA in Apio. Using DEFAULT instead.", pin);
      if (net.startsWith("FPGA_INPUT_PIN_")) {
        // FPGA input pins may need pull-up, pull-down, bus-hold, etc. Those
        // are done here, in the constraints file. Or, we could emit an SB_IO
        // block or equivalent, below. Either should work, maybe?
        InputBias bias = ioResources.getInputBias(net);
        if (bias == InputBias.PULL_UP) {
          pcf.stmt("set_io --warn-no-port --pullup yes %s %s", net, pin);
        } else if (bias == InputBias.PULL_DOWN) {
          pcf.err.AddSevereWarning("FPGA pin %s pull-down is not possible for iCE40 FPGA. Using pull-none instead.", pin);
          pcf.stmt("set_io --warn-no-port --pullup no %s %s", net, pin);
        } else if (bias == InputBias.BUS_HOLD) {
          pcf.err.AddSevereWarning("FPGA pin %s bus-hold is not possible for iCE40 FPGA. Using pull-none instead.", pin);
          pcf.stmt("set_io --warn-no-port --pullup no %s %s", net, pin);
        } else if (bias == InputBias.PULL_NONE) {
          pcf.stmt("set_io --warn-no-port --pullup no %s %s", net, pin);
        } else { // DO_NOT_SPECIFY
          pcf.stmt("set_io --warn-no-port %s %s", net, pin);
        }
      } else if (net.startsWith("FPGA_BIDIR_PIN_")) {
        // FPGA bidir pins require an SB_IO block or equivalent, emitted below, so
        // we specify any pull-up, pull-down, or bus-hold there.
        pcf.stmt("set_io --warn-no-port %s %s", net, pin);
      } else if (net.startsWith("FPGA_OUTPUT_PIN_")) {
        // FPGA output pins have no bias.
        pcf.stmt("set_io --warn-no-port %s %s", net, pin);
      }
    });
    // FIXME: handle all unmapped pins
    return pcf.save();
  }
  

}
