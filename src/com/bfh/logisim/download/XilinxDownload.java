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

import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.DriveStrength;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.fpga.PullBehavior;
import com.bfh.logisim.gui.Commander;
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.prefs.AppPreferences;

public class XilinxDownload extends FPGADownload {

  public XilinxDownload() { super("Xilinx"); }

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
          PullBehavior dir = board.fpga.UnusedPinsBehavior;
          if (dir == PullBehavior.PULL_UP || dir == PullBehavior.PULL_DOWN) {
            stages.add(new ProcessStage(
                  "generate", "Generating Bitfile",
                  cmd(XILINX_BITGEN, "-w", "-g", "UnusedPin:"+dir.xilinx.toUpperCase(),
                    "-g", "StartupClk:CCLK", "logisim_par", TOP_HDL + ".bit"),
                  "Failed to place & route design, cannot download"));
          } else {
            stages.add(new ProcessStage(
                  "generate", "Generating Bitfile",
                  cmd(XILINX_BITGEN, "-w", "-g", "StartupClk:CCLK", "logisim_par", TOP_HDL + ".bit"),
                  "Failed to generate bitfile, cannot download"));
          }
        } else {
          String part = board.fpga.Part.toUpperCase() + "-"
            + board.fpga.SpeedGrade + "-"
            + board.fpga.Package.toUpperCase();
          stages.add(new ProcessStage(
                "CPLD fit", "Fit CPLD Design (may take a while)",
                cmd(XILINX_CPLDFIT, "-p", part, "-intstyle", "ise",
                  "-terminate", board.fpga.UnusedPinsBehavior.xilinx, // TODO: do correct termination type
                  "-loc", "on", "-log", "logisim_cpldfit.log", "logisim.ngd"),
                "Failed to fit CPLD design, cannot download"));
          stages.add(new ProcessStage(
                "generate", "Generating Bitfile",
                cmd(XILINX_HPREP6, "-i", "logisim.vm6"),
                "Failed to generate bitfile, cannot download"));
        }
      }
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
    Hdl out = new Hdl(lang, err);
    String kind = lang.toUpperCase();
		for (String f : hdlFiles)
			out.stmt("%s work \"%s\"", kind, f);
		File f = FileWriter.GetFilePointer(scriptPath, vhdl_list_file, err);
		return f != null && FileWriter.WriteContents(f, out, err);
  }

  private boolean generateRunScript() {
    Chipset chip = board.fpga;
    String dev = String.format("%s-%s-%s", chip.Part, chip.Package, chip.SpeedGrade);
    String vhdlListPath = scriptPath.replace(projectPath, "../") + vhdl_list_file;
    Hdl out = new Hdl(lang, err);
		out.stmt("run -top %s -ofn logisim.ngc -ofmt NGC -ifn %s -ifmt mixed -p %s",
        TOP_HDL, vhdlListPath, dev);
		File f = FileWriter.GetFilePointer(scriptPath, script_file, err);
		return f != null && !FileWriter.WriteContents(f, out, err);
  }

  private boolean generateDownloadScript() {
		boolean isCPLD = isCPLD(board.fpga);
		String bitFileExt = isCPLD ? ".jed" : ".bit";
    int jtagPos = board.fpga.JTAGPos;
    Hdl out = new Hdl(lang, err);
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
		File f = FileWriter.GetFilePointer(scriptPath, download_file, err);
		return f != null && FileWriter.WriteContents(f, out, err);
  }

  private boolean generateUcfFile(PinBindings ioResources) {
    Hdl out = new Hdl(lang, err);
		if (ioResources.requiresOscillator) {
			out.stmt("NET \"%s\" %s ;", CLK_PORT, xilinxClockSpec(board.fpga));
			out.stmt("NET \"%s\" TNM_NET = \"%s\" ;", CLK_PORT, CLK_PORT);
			out.stmt("TIMESPEC \"TS_%s\" = PERIOD \"%s\" %s HIGH 50 % ;",
          CLK_PORT, CLK_PORT, board.fpga.Speed);
			out.stmt();
		}
    ioResources.forEachPhysicalPin((pin, net, io, label) -> {
      String spec = String.format("LOC = \"%s\"", pin);
      if (io.pull == PullBehavior.PULL_UP || io.pull == PullBehavior.PULL_DOWN)
        spec += " | " + io.pull.xilinx;
      if (io.strength != DriveStrength.UNKNOWN && io.strength != DriveStrength.DEFAULT)
        spec += " | DRIVE = " + io.strength.ma;
      if (io.standard != IoStandard.UNKNOWN && io.standard != IoStandard.DEFAULT)
        spec += " | IOSTANDARD = " + io.standard;
      out.stmt("NET \"%s\" %s  ;# %s", net, spec, label);
    });
		File f = FileWriter.GetFilePointer(ucfPath, ucf_file, err);
		return FileWriter.WriteContents(f, out, err);
  }

  @Override
  public boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles) {
    return generateVhdlListFile(hdlFiles)
        && generateRunScript()
        && generateDownloadScript()
        && generateUcfFile(ioResources);
	}

	private static String xilinxClockSpec(Chipset chip) {
    String spec = String.format("LOC = \"%s\"", chip.ClockPinLocation);
    PullBehavior pull = chip.ClockPullBehavior;
		if (pull == PullBehavior.PULL_UP || pull == PullBehavior.PULL_DOWN)
			spec += " | " + pull.xilinx;
    IoStandard std = chip.ClockIOStandard;
		if (std != IoStandard.DEFAULT && std != IoStandard.UNKNOWN)
			spec += " | IOSTANDARD = " + std;
		return spec;
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
