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

import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.fpga.PullBehavior;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.bfh.logisim.hdlgenerator.ToplevelHDLGenerator;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.settings.Settings;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.FileUtil;

public class ApioDownload extends FPGADownload {

  public ApioDownload() { super("Apio"); }

  @Override
  public boolean readyForDownload() {
    return new File(sandboxPath + "hardware.bin").exists();
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
      Process process = Runtime.getRuntime().exec(cmd + " --version");
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      String line = reader.readLine();
      if (line.toLowerCase().startsWith("apio "))
        return line.substring("apio ".length());
    } catch (Exception e) {
    }
    return null;
  }

  private static final String helpmsg =
    "Either install apio to a system directory, or set "
    + "the toolchain path to point to the apio executable or a "
    + "directory (e.g. a python virtualenv) containing bin/apio.";

  public boolean toolchainIsInstalled(Settings settings, FPGAReport err) {
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
    String prog = findApioExecutable(tool);
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
    String script = findApioExecutable(p);
    if (script == null) {
      err.AddFatalError("Apio toolchain path is set to '" + p + "' but"
          + " the apio command was still not found. " + helpmsg);
    }
    return script;
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

  @Override
  public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {

    bin_apio = findApioExecutable();
    if (bin_apio == null)
      return false;

    String board_name = board.name;
    if (board.apio_name != null && !board.apio_name.equals(""))
      board_name = board.apio_name;

    if (board.fpga.UnusedPinsBehavior != PullBehavior.UNKNOWN &&
        board.fpga.UnusedPinsBehavior != PullBehavior.PULL_UP) {
      err.AddSevereWarning("Design specifies " + board.fpga.UnusedPinsBehavior +
          " for unused pins, but apio toolchain maybe only supports pull-up.");
      err.AddSevereWarning("Unused pins will maybe be pulled high.");
    }

    File f;

    // Generate apio.ini
    Hdl out = new Hdl(lang, err);
    // FIXME: to support apio v0.9.5 and earlier, should use "[env]" here,
    // but version 1.0.0 and later expect "[env:default]"
    out.stmt("[env:default]");
    out.stmt("board = " + board_name);
    out.stmt("top-module = LogisimToplevelApioShell");
    f = FileWriter.GetFilePointer(sandboxPath, "apio.ini", err);
    if (f == null || !FileWriter.WriteContents(f, out, err))
      return false;

    if (out.isVhdl) {
      err.AddSevereWarning("VHDL was chosen, but apio toolchain maybe only supports Verilog.");
      err.AddSevereWarning("Design will probably fail to compile.");
    }

    // iCE40UP/UL family has SB_HFOSC; HX/LP family requires an external clock pin.
    boolean hasHFOSC = board.fpga.Part.toUpperCase().contains("UP") ||
        board.fpga.Part.toUpperCase().contains("UL");

    // Generate fpga.pcf
    Hdl out2 = new Hdl(lang, err);
    out2.stmt();
    if (ioResources.requiresOscillator && !hasHFOSC)
      out2.stmt("set_io --warn-no-port FPGA_CLK %s", board.fpga.ClockPinLocation);
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      out2.stmt("set_io --warn-no-port %s %s", net, pin);
    });
    f = FileWriter.GetFilePointer(sandboxPath, "fpga.pcf", err);
    if (f == null || !FileWriter.WriteContents(f, out2, err))
      return false;

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
    // todo: SB_IO for pullups, tristates, etc.
    Hdl out3 = new Hdl(lang, err);
    Netlist.Int3 ioPinCount = ioResources.countFPGAPhysicalIOPins();
    int n = ioPinCount.size();
    if (ioResources.requiresOscillator && !hasHFOSC) n++;  // FPGA_CLK as input port
    out3.stmt("module LogisimToplevelApioShell(%s", (n == 0 ? " );" : ""));
    if (ioResources.requiresOscillator && !hasHFOSC)
      out3.stmt("              FPGA_CLK%s", (--n == 0 ? " );" : ","));
		for (int i = 0; i < ioPinCount.in; i++)
      out3.stmt("              FPGA_INPUT_PIN_%d%s", i, (--n == 0 ? " );" : ","));
		for (int i = 0; i < ioPinCount.inout; i++)
      out3.stmt("              FPGA_BIDIR_PIN_%d%s", i, (--n == 0 ? " );" : ","));
		for (int i = 0; i < ioPinCount.out; i++)
      out3.stmt("              FPGA_OUTPUT_PIN_%d%s", i, (--n == 0 ? " );" : ","));
    if (ioResources.requiresOscillator && !hasHFOSC)
      out3.stmt("  input FPGA_CLK;");
		for (int i = 0; i < ioPinCount.in; i++)
      out3.stmt("  input FPGA_INPUT_PIN_%d;", i);
		for (int i = 0; i < ioPinCount.inout; i++)
      out3.stmt("  inout FPGA_BIDIR_PIN_%d;", i);
		for (int i = 0; i < ioPinCount.out; i++)
      out3.stmt("  output FPGA_OUTPUT_PIN_%d;", i);
    out3.stmt();

    n = ioPinCount.size();
    if (ioResources.requiresOscillator) {
      if (hasHFOSC) {
        out3.stmt("  wire FPGA_CLK;");
        // For iCE40UP/UL fpga, use the high-speed oscillator (48 MHz).
        // FIXME: the SB_HFOSC block can divide by 1, 2, 4, or 8. We should
        // probably use that feature when the design clock settings call for clock
        // division. Or, use the SB_LFOSC block instead, which runs at 10 kHz.
        out3.stmt("  SB_HFOSC internal_oscillator(.CLKHFPU(1'b1), .CLKHFEN(1'b1), .CLKHF(FPGA_CLK));");
        out3.stmt();
      }
      // For iCE40HX/LP, FPGA_CLK is an input port wired to the board's external
      // crystal via PCF constraint; no internal oscillator primitive is needed.
      n++;
    }
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      // todo: also handle bidirectional using SB_IO
      PullBehavior pull = ioResources.getInputPinPull(net);
      int pullup = 0;
      if (pull == PullBehavior.PULL_UP) {
        pullup = 1;
      } else if (pull == PullBehavior.NONE) {
        return; // handled above
      } else {
        err.AddSevereWarning("FPGA pin %s pull behavior specified as %s, but apio only supports pull-up.", pull);
      }
      out3.stmt("  wire %s;", "PULLED_"+net);
      // PIN_TYPE = 6 bits = xxxx_yy, xxxx=1010 is tri-state output, yy=01 is
      // simple non-clocked input, etc.
      out3.stmt("  SB_IO #(.PIN_TYPE(6'b 0000_01), .PULLUP(1'b %d))", pullup);
      out3.stmt("        sb_pin_%s (.PACKAGE_PIN(%s), .D_IN_0(%s));", pin, net, "PULLED_"+net);
      out3.stmt();
    });
		for (int i = 0; i < ioPinCount.inout; i++) {
      String net = "FPGA_BIDIR_PIN_" + i;
      out3.stmt("  wire %s;", net+"_IN");
      out3.stmt("  wire %s;", net+"_OUT");
      out3.stmt("  wire %s;", net+"_EN");
      out3.stmt("  SB_IO #(.PIN_TYPE(6'b 1010_01), .PULLUP(1'b 0))");
      out3.stmt("        sb_bidir_%d (.PACKAGE_PIN(%s),", i, net);
      out3.stmt("                    .OUTPUT_ENABLE(%s),", net+"_EN");
      out3.stmt("                    .D_OUT_0(%s),", net+"_OUT");
      out3.stmt("                    .D_IN_0(%s));", net+"_IN");
      out3.stmt();
    }

    out3.stmt("  LogisimToplevelShell wrappedShell( %s", (n == 0 ? " );" : ""));
    if (ioResources.requiresOscillator)
      out3.stmt("              .FPGA_CLK(FPGA_CLK)%s", (--n == 0 ? " );" : ","));
		for (int i = 0; i < ioPinCount.in; i++) {
      String net = "FPGA_INPUT_PIN_"+i;
      if (ioResources.getInputPinPull(net) != PullBehavior.NONE)
        net = "PULLED_"+net;
      out3.stmt("              .FPGA_INPUT_PIN_%d(%s)%s", i, net, (--n == 0 ? " );" : ","));
    }
		for (int i = 0; i < ioPinCount.inout; i++) {
      out3.stmt("              .FPGA_BIDIR_PIN_%d_IN(FPGA_BIDIR_PIN_%d_IN),", i, i);
      out3.stmt("              .FPGA_BIDIR_PIN_%d_OUT(FPGA_BIDIR_PIN_%d_OUT),", i, i);
      out3.stmt("              .FPGA_BIDIR_PIN_%d_EN(FPGA_BIDIR_PIN_%d_EN)%s", i, i, (--n == 0 ? " );" : ","));
    }
		for (int i = 0; i < ioPinCount.out; i++)
      out3.stmt("              .FPGA_OUTPUT_PIN_%d(FPGA_OUTPUT_PIN_%d)%s", i, i, (--n == 0 ? " );" : ","));
    out3.stmt();

    out3.stmt("endmodule");
    f = FileWriter.GetFilePointer(sandboxPath, "LogisimToplevelApioShell.v", err);
    if (f == null || !FileWriter.WriteContents(f, out3, err))
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

    // upload: use openFPGALoader when the board specifies it (e.g. boards whose
    // flash chip is not supported by apio/iceprog), otherwise use apio upload.
    if (board.openFPGALoader_name != null) {
      String bin_ofl = OpenFPGALoader.findExecutable(err);
      if (bin_ofl != null) {
        stages.add(new ProcessStage(
              "upload", "Uploading to FPGA via openFPGALoader",
              OpenFPGALoader.commandFor(board, bin_ofl),
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
      } else {
        // err message

      }
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
