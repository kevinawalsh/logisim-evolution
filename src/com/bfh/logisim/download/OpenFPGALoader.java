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
import com.bfh.logisim.gui.Console;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.prefs.AppPreferences;

public class OpenFPGALoader extends FPGAProgrammer {

  public static OFLToolchain TOOLCHAIN = new OFLToolchain();

  public static class OFLToolchain extends Toolchain {

    OFLToolchain() {
      super("openFPGALoader", "openFPGALoader", false, true /* programmer only */);
    }

    @Override
    public boolean hasAlternateName(String altname) {
      return altname.equalsIgnoreCase("trabucayre/openFPGALoader");
    }

    @Override
    public boolean supports(Board board) {
      // if the board xml explicitly lists openFPGALoader, then who are we to disagree?
      if (board.mentions(this) || board.paramFor(this, "board") != null)
        return true;
      // otherwise, check if we know the name for this board
      String openFPGALoader = getInstalledCommand(null);
      return boardNameFromBackendList(openFPGALoader, board) != null;
    }

    @Override
    public String defaultParamsAsString(/*Board board*/) {
      return
        "board: passed to backend, defaults to board codename\n" +
        "verify: whether to verify after upload (default: true)\n" +
        "reset: whether to reset after upload (default: false)\n" +
        "verbose: verbosity level (default: 0 or false)\n";
      // FIXME: Maybe also support...
      // --write-flash vs --write-sram
      // --detect
      // --freq N to change the jtag programming speed
      // --verbose
      // --index-chain
      // --offset
      // etc.
    }

    @Override
    public List<String> getLanguages(Board board) {
      return List.of(VERILOG, VHDL);
    }

    private static final String helpmsg =
      "Either install openFPGALoader to a system directory, or set "
      + "the toolchain path to point to the 'openFPGALoader' executable or a "
      + "directory (e.g. a python virtualenv) containing it.";

   
    @Override
    public InstallStatus toolchainInstallStatus() {
      return toolchainInstallStatus(AppPreferences.APIO_PATH.get());
    }

    public InstallStatus toolchainInstallStatus(String prefPath) {
      return simpleInstallStatusHelper(
          prefPath, "--Version",
          helpmsg, "openFPGALoader", "bin/openFPGALoader");
    }


    @Override
    public FPGASynthesizer newSynthesizer(FPGAReport err) { return null; }

    @Override
    public FPGAProgrammer newProgrammer(FPGAReport err) {
      String openFPGALoader = getInstalledCommand(err);
      return openFPGALoader == null ? null : new OpenFPGALoader(err, openFPGALoader);
    }

  };

  private String openFPGALoader; // verified openFPGALoader command, inluding full path if needed
  private String bitstream; // set by createProgrammingPlan()
  private String cableIndex; // set by ScanStage.post()
  private boolean confirmed;

  private OpenFPGALoader(FPGAReport err, String openFPGALoader) {
    super(TOOLCHAIN, "openFPGALoader", err);
    this.openFPGALoader = openFPGALoader;
  }

  // openFPGALoader board names tend to follow alphanumplus_snake_case or,
  // sometimes, alhpanumplus-kebab-case conventions. We normalize to snake case.
  private static String normalizeBoardName(String name) {
    name = name.replaceAll("[^a-zA-Z0-9+]+", "_");
    if (name.startsWith("_")) name = name.substring(1);
    if (name.endsWith("_")) name = name.substring(0, name.length()-1);
    return name;
  }

  private class ScanStage extends ProcessStage {

    ScanStage() {
      super("scan", "Scaning for FPGA Devices",
          join(openFPGALoader, "--detect"),
          "Could not find any FPGA devices.");
    }

    @Override
    protected boolean prep() {
      if (!new File(bitstream).exists()) {
        console.printf(Console.ERROR, "Error: Design must be synthesized before download.");
        return false;
      }
      if (!confirmed && !cmdr.confirmDownload()) {
        cancelled = true;
        return false;
      }
      confirmed = true;
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

  }

  private ArrayList<String> uploadCommand() {
    ArrayList<String> cmd = new ArrayList<>();

    cmd.add(openFPGALoader);
    
    String verbosePref = param("verbose");
    if (verbosePref != null && !verbosePref.equalsIgnoreCase("false")) {
      if (verbosePref.equalsIgnoreCase("true")) {
        cmd.add("--verbose");
      } else {
        try {
          int level = Integer.parseInt(verbosePref);
          cmd.add("--verbose-level");
          cmd.add(""+level);
        } catch (NumberFormatException e) {
        }
      }
    }

    String verifyPref = param("verify");
    if (verifyPref == null || !verifyPref.equalsIgnoreCase("false"))
      cmd.add("--verify");

    String resetPref = param("reset");
    if (resetPref != null && resetPref.equalsIgnoreCase("true"))
      cmd.add("--reset");

    String boardname = boardNameFor(board);
    if (boardname != null) {
      cmd.add("-b");
      cmd.add(boardname);
    }

    if (cableIndex != null) {
      cmd.add("--cable-index");
      cmd.add(cableIndex);
    }

    cmd.add(bitstream);

    return cmd;
  }


  public boolean createProgrammingPlan(ArrayList<Stage> stages, String bitstream) {

    this.bitstream = bitstream; // e.g. hardware.bin

    stages.add(new ScanStage());

    stages.add(new ProcessStage(
          "upload", "Uploading to FPGA via openFPGALoader", uploadCommand(),
          "Failed to upload design; did you connect the board?") {
      @Override
      protected boolean prep() {
        if (!confirmed && !cmdr.confirmDownload()) {
          cancelled = true;
          return false;
        }
        confirmed = true;
        return true;
      }
    });

    return true;;
  }

  private String boardNameFor(Board board) {
    // if custom params, or board, listed a name, then who are we to disagree?
    String pref = param("board");
    if (pref != null)
      return pref;
    // next, see if codename appears in board list (ignore case, but otherwise exact match)
    return boardNameFromBackendList(openFPGALoader, board);
  }

  private static String boardNameFromBackendList(String openFPGALoader, Board board) {
    // see if codename appears in board list (ignore case, but otherwise exact match)
    ArrayList<String> names = getOpenFPGALoaderBoardList(openFPGALoader);
    for (String name : names)
      if (name.equalsIgnoreCase(board.codename))
        return name;
    // try approximate matches against board list
    String codename = normalizeBoardName(board.codename);
    for (String name : names)
      if (name.equalsIgnoreCase(codename))
        return name;
    return null;
  }


  private static ArrayList<String> getOpenFPGALoaderBoardList(String openFPGALoader) {
    ArrayList<String> ret = new ArrayList<>();
    for (String line : FPGATool.stdoutFor(openFPGALoader, "--list-boards")) {
      String parts[] = line.split(" ", 2);
      if (parts.length > 0 && !parts[0].equalsIgnoreCase("empty")
          && !parts[0].equalsIgnoreCase("board"))
        ret.add(parts[0]);
    }
    return ret;
  }

}
