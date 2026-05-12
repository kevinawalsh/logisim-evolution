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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import javax.swing.SwingUtilities;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.bfh.logisim.hdlgenerator.TickHDLGenerator;
import com.bfh.logisim.hdlgenerator.ToplevelHDLGenerator;
import com.bfh.logisim.netlist.Netlist;
import com.cburch.logisim.Main;

public abstract class FPGADownload {

  // FIXME: these need a home, duplicated in several places
  static final String VHDL = "VHDL";
  static final String VERILOG = "Verilog";

  static final String TOP_HDL = ToplevelHDLGenerator.HDL_NAME;
  static final String CLK_PORT = TickHDLGenerator.FPGA_CLK_NET;

  public final String name;

  // Parameters set by Commander, used by downloader
  public FPGAReport err;
  public String lang;
  public Board board;
  public String projectPath;
  public String circuitPath;
  public String scriptPath;
  public String sandboxPath;
  public String ucfPath;
  public boolean writeToFlash;
  public boolean remoteJTAG;
  public FPGAProgrammer programmer; // user-selected programmer, or null to auto-select

  // Capability flag set by downloader, used by Commander
  public boolean supportsRemoteJTAG = false;

  protected FPGADownload(String name) {
    this.name = name;
  }

  // FIXME: this should be part of toolchain... and show status in AppPreferences too?
  public abstract boolean toolchainIsInstalled(FPGAReport err);

  public boolean generateScripts(PinBindings ioResources) {
    ArrayList<String> hdlFiles = new ArrayList<>();
    enumerateHDLFiles(circuitPath, hdlFiles);
    return generateScripts(ioResources, hdlFiles);
  }

  public abstract boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles);
  
  public abstract boolean readyForDownload();

  public abstract ArrayList<Stage> initiateDownload(Commander cmdr);
  
  private void enumerateHDLFiles(String path, ArrayList<String> files) {
    if (lang.equals(VHDL))
      enumerateHDLFiles(path, files,
          FileWriter.EntityExtension + ".vhd",
          FileWriter.ArchitectureExtension + ".vhd");
    else
      enumerateHDLFiles(path, files, ".v", null);
  }
  
  private void enumerateHDLFiles(String path, ArrayList<String> files,
    String entityEnding, String behaviorEnding) {
    File dir = new File(path);
    if (!path.endsWith(File.separator))
      path += File.separator;
    for (File f : dir.listFiles()) {
      String subpath = path + f.getName();
      if (f.isDirectory())
        enumerateHDLFiles(subpath, files, entityEnding, behaviorEnding);
      else if (f.getName().endsWith(entityEnding))
        files.add(subpath.replace("\\", "/"));
      else if (f.getName().endsWith(behaviorEnding))
        files.add(subpath.replace("\\", "/"));
    }
  }

  protected static final String dotexe = Main.MSWindows ? ".exe" : ""; // convenience

  public abstract class Stage {
    public final String title, msg, errmsg;
    public Console console;
    public Thread thread;
    public int exitValue = -1;
    public boolean failed, cancelled;

    public Stage(String title, String msg, String errmsg) {
      this.title = title;
      this.msg = msg;
      this.errmsg = errmsg;
    }

    protected boolean prep() { return true; }
    protected boolean post() { return true; }

    public abstract void startAndThen(Runnable completion);
  }

  public class ProcessStage extends Stage {
    
    ArrayList<String> cmd;

    public ProcessStage(String title, String msg, 
        ArrayList<String> cmd, String errmsg) {
      super(title, msg, errmsg);
      this.cmd = cmd;
    }

    protected boolean retry(int exitval) { return false; }

    public void startAndThen(Runnable completion) {
      if (!prep()) {
        failed = true;
        completion.run();
        return;
      }
      if (cmd == null) {
        completion.run();
        return;
      }
      console.printf(console.INFO, "Command: %s\n", shellEscape(cmd));
      ProcessBuilder builder = new ProcessBuilder(cmd);
      builder.directory(new File(sandboxPath));
      Process process;
      try {
        process = builder.start();
      } catch (IOException e) {
        console.printf(console.ERROR, e.getMessage());
        failed = true;
        completion.run();
        return;
      }
      InputStream stdout = process.getInputStream();
      InputStream stderr = process.getErrorStream();
      Thread t1 = console.copyFrom(console.INFO, stdout);
      Thread t2 = console.copyFrom(console.WARNING, stderr);
      thread = new Thread(() -> {
        boolean needRetry = false;
        try {
          process.waitFor();
          t1.join(500);
          t2.join(500);
          if (t1.isAlive()) {
            try { stdout.close(); } 
            catch (IOException e) { console.printf(console.ERROR, e.getMessage()); }
            t1.join();
          }
          if (t2.isAlive()) {
            try { stderr.close(); }
            catch (IOException e) { console.printf(console.ERROR, e.getMessage()); }
            t2.join();
          }
          exitValue = process.exitValue();
          if (exitValue != 0)
            console.printf(console.ERROR, describeExitCode(exitValue));
          if (exitValue != 0 && retry(exitValue)) {
            console.printf(console.INFO, "Command failed, retrying...");
            needRetry = true;
          } else if (exitValue != 0 || !post()) {
            failed = true;
          }
        } catch (InterruptedException ex) {
          process.destroyForcibly();
          needRetry = false;
          failed = true;
        } finally {
          if (needRetry)
            SwingUtilities.invokeLater(() -> { startAndThen(completion); });
          else
            SwingUtilities.invokeLater(completion);
        }
      });
      thread.start();
    }

    static String describeExitCode(int code) {
      if (code == 0)
        return "success (exit code 0)";

      // Unix style signal (128+N convention, or negative signum)
      int signum = -1;
      if (code < 0 && code > -64) signum = -code;
      else if (code > 128 && code <= 192) signum = code - 128;
      if (signum > 0)
        return String.format("Process failed via signal [exit code %d, %s]", code, signalName(signum));

      // Windows structured exception codes (negative in Java due to sign)
      if (Main.MSWindows && code < 0)
        return String.format("Process failed [exit code 0x%s, %s]",
            Integer.toUnsignedString(code, 16).toUpperCase(), windowsExceptionName(code));

      return String.format("Process failed [exit code %d]", code);
    }

    static String windowsExceptionName(int code) {
      switch (code) {
        case (int)0xC0000005: return "access violation";
        case (int)0xC000001D: return "illegal instruction";
        case (int)0xC0000094: return "integer divide by zero";
        case (int)0xC00000FD: return "stack overflow";
        case (int)0xC0000135: return "DLL not found";
        case (int)0xC0000138: return "DLL ordinal not found";
        case (int)0xC0000139: return "DLL entry point not found";
        case (int)0xC0000142: return "DLL initialization failed";
        default:              return "windows exception";
      }
    }

    private static String signalName(int signum) {
      switch (signum) {
        case  1: return "SIGHUP (hangup)";
        case  2: return "SIGINT (interrupt)";
        case  3: return "SIGQUIT (quit)";
        case  4: return "SIGILL (illegal instruction)";
        case  6: return "SIGABRT (abort)";
        case  7: return "SIGBUS (bus error)";
        case  8: return "SIGFPE (floating point exception)";
        case  9: return "SIGKILL (killed)";
        case 10: return "SIGUSR1";
        case 11: return "SIGSEGV (segmentation fault)";
        case 12: return "SIGUSR2";
        case 13: return "SIGPIPE (broken pipe)";
        case 14: return "SIGALRM (alarm)";
        case 15: return "SIGTERM (terminated)";
        case 24: return "SIGXCPU (CPU time limit exceeded)";
        case 25: return "SIGXFSZ (file size limit exceeded)";
        default: return String.format("signal %d", signum);
      }
    }

  }

  public abstract class RunnableStage extends Stage {

    public RunnableStage(String title, String msg, String errmsg) {
      super(title, msg, errmsg);
    }

    protected abstract boolean run();

    @Override
    public void startAndThen(Runnable completion) {
      if (!prep()) {
        failed = true;
        completion.run();
        return;
      }
      thread = new Thread(() -> {
        try {
          if (!run())
            failed = true;
          else if (!post())
            failed = true;
        } catch (Exception ex) {
          failed = true;
          try { console.printf(console.ERROR, ex.getMessage()); }
          catch (Exception ex2) { }
        } finally {
          SwingUtilities.invokeLater(completion);
        }
      });
      thread.start();
    }
  }

  private static String shellEscape(ArrayList<String> cmd) { // just for pretty-printing
    String s = "";
    for (String c : cmd) {
      if (!c.matches("[a-zA-Z0-9-+_=:,.]*")) {
        c = c.replaceAll("\\\\", "\\\\");
        c = c.replaceAll("`", "\\`");
        c = c.replaceAll("\\$", "\\$");
        c = c.replaceAll("!", "\\!");
        c = c.replaceAll("'", "'\\''");
        c = "'" + c + "'";
      }
      s += s.length() > 0 ? " " + c : c;
    }
    return s;
  }

  public ToplevelHDLGenerator toplevelHDLGenerator(Netlist.Context ctx, PinBindings pinBindings) {
    return new ToplevelHDLGenerator(ctx, pinBindings);
  }

}
