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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.Main;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Debug;

public abstract class Toolchain {

  public static final String VHDL = "VHDL";
  public static final String VERILOG = "Verilog";
  public static final String CLK_PORT = FPGASynthesizer.CLK_PORT; // convenience


  // A unique, canonical name for the toolchain. This is displayed in some UI
  // selectors, may be saved in settings, mentioned in board xml files, etc.
  public final String toolchainName; // e.g. "Altera Quartus II"
 
  // A short name used for printing status messages (when the full name might be
  // cumbersome, and where uniqueness isn't important). Also used to match
  // toolchain settings in board xml files (if canonical name wasn't used).
  public final String shortName; // e.g. "Quartus"

  // Alternate name matching, used for matching toolchain settings in board xml
  // files (if canonical name or short name wasn't used).
  public abstract boolean hasAlternateName(String altname); // e.g. "quartus 2"

  public boolean approximateNameMatch(String fuzzyname) {
    return toolchainName.equalsIgnoreCase(fuzzyname)
        || shortName.equalsIgnoreCase(fuzzyname)
        || hasAlternateName(fuzzyname);
  }

  // Capabilities of this toolchain. These are definitive: e.g. if a toolchain
  // says it can't synthesize, the Commander gui will not allow it to be
  // selected for syntehsis.
  public final boolean canSynthesize;
  public final boolean canProgram;
  
  // Check whether the toolchain thinks it can support this board. This is used
  // in the Commander gui to add a "(supported)" tag to toolchain names, but the
  // user can ignore it and select a non-supported toolchain if they think they
  // know better.
  public abstract boolean supports(Board b);

  // Get list of default [key, value-or-description] parameter pairs for a given
  // board (independent of any board customizations, app preferences, etc.). For
  // example:
  //   "board", "passed to backend, defaults to board codename"
  //   "64bit", "slower but more memory, defaults to true"
  //   "apikey", "api key string from website, not used if empty"
  //   "optimize", "passed to backed, either 'memory' (default) or 'speed'"
  // This is used by BoardEditor, only as a hint for the user.
  public List<String[]> defaultParams(/* Board board*/) {
    return splitKeyValueLines(defaultParamsAsString());
  }

  public abstract String defaultParamsAsString(/* Board board */);

  // Get a list of languages supported for a given board, e.g. VERLOG and/or VHDL.
  public abstract List<String> getLanguages(Board board);

  public record InstallStatus(boolean installed, String cmd, String detail) {
    // installed: toolchain appears to be installed and working
    // cmd: verified command to run, including path if needed
    // detail: version info, or error message
    
    static InstallStatus fromSuccess(String cmd, String versionFormatString, Object ...args) {
      return new InstallStatus(true, cmd, String.format(versionFormatString, args));
    }

    static InstallStatus fromError(String errorFormatString, Object ...args) {
      return new InstallStatus(false, null, String.format(errorFormatString, args));
    }
  }

  // Checks status of toolchain, prints the version and returns true on success,
  // or prints fatal error and returns false on failure.
  public boolean toolchainIsInstalled(FPGAReport err) {
    InstallStatus status = toolchainInstallStatus();
    if (status.installed()) {
      err.AddInfo(status.detail());
      return true;
    } else {
      err.AddFatalError(status.detail());
      return false;
    }
  }

  // Checks status of toolchain, discards the version info and returns the
  // command on success, or prints fatal error (if err is non null) and returns
  // null on failure.
  protected String getInstalledCommand(FPGAReport err) {
    InstallStatus status = toolchainInstallStatus();
    if (status.installed()) return status.cmd();
    // if (err != null) err.AddFatalError(status.detail());
    if (err != null) err.AddFatalError(toolchainName + " toolchain isn't installed or configured properly. Install the necessary software, fix the settings, or try a different toolchain.");
    return null;
  }

  // Check status of toolchain installation, returns either
  // "YES: <version info>" or "NO: <error message>"
  public abstract InstallStatus toolchainInstallStatus();

  // Create a new synthesizer, to be configured and used imminently.
  public abstract FPGASynthesizer newSynthesizer(FPGAReport err);

  // Create a new programmer, to be configured and used imminently.
  public abstract FPGAProgrammer newProgrammer(FPGAReport err);


  protected Toolchain(String canonicalName, String shortName, boolean synth, boolean pgm) {
    this.toolchainName = canonicalName;
    this.shortName = shortName;
    this.canSynthesize = synth;
    this.canProgram = pgm;
  }

  private static final HashMap<String, Toolchain> byName = new HashMap<>(); // lowercase
  private static final ArrayList<Toolchain> tools = new ArrayList<>();
  private static final ArrayList<Toolchain> sTools = new ArrayList<>();
  private static final ArrayList<Toolchain> pTools = new ArrayList<>();
  public static void register(Toolchain t) {
    if (byName.containsKey(t.toolchainName.toLowerCase())) {
      Debug.error("ignoring duplicate toolchain: " + t.toolchainName);
      return;
    }
    byName.put(t.toolchainName.toLowerCase(), t);
    tools.add(t);
    if (t.canSynthesize)
      sTools.add(t);
    if (t.canProgram)
      pTools.add(t);
  }

  private static boolean registered = false;
  private static synchronized void ensureRegistered() {
    if (registered) return;
    registered = true;
    register(Apio.TOOLCHAIN);
    Altera.register();
    register(Xilinx.TOOLCHAIN);
    register(Lattice.TOOLCHAIN);
    register(Gowin.SYNTH_TOOLCHAIN);
    register(Gowin.PROG_TOOLCHAIN);
    register(OpenFPGALoader.TOOLCHAIN);
  }

  public static List<Toolchain> getAllToolchains() {
    ensureRegistered();
    return Collections.unmodifiableList(tools);
  }

  public static List<Toolchain> getSynthesisToolchains() {
    ensureRegistered();
    return Collections.unmodifiableList(sTools);
  }

  public static List<Toolchain> getProgrammingToolchains() {
    ensureRegistered();
    return Collections.unmodifiableList(pTools);
  }

  private static Toolchain findByApproximateName(List<Toolchain> list, String name) {
    if (name == null || name.isEmpty())
      return null;
    // Look for exact match first, e.g. "Altera Quartus II" or "Quartus"
    for (Toolchain t : list)
      if (t.toolchainName.equalsIgnoreCase(name) || t.shortName.equalsIgnoreCase(name))
        return t;
    // Then look for alternate names, e.g. "quartus ii" or "quartus 2"
    for (Toolchain t : list)
      if (t.hasAlternateName(name))
        return t;
    // No matching toolchain
    return null;
  }

  public static Toolchain findToolchainByApproximateName(String name) {
    ensureRegistered();
    return findByApproximateName(tools, name);
  }

  public record ToolchainParameterPair(Toolchain tool, String params) {}

  public static ToolchainParameterPair autoSelectSynthesisToolchain(Board board) {
    ensureRegistered();
    if (board == null)
      return new ToolchainParameterPair(sTools.get(0), null); // no board selected, so any toolchain is fine, whatever
    
    // First priority: user preference for the given board
    String pref = AppPreferences.FPGA_BOARDPREFS.getBoardPreferredSynthesisToolchain(board.name);
    Toolchain t = findByApproximateName(sTools, pref);
    if (t != null) {
      String p = AppPreferences.FPGA_BOARDPREFS.getBoardPreferredSynthesisParams(board.name);
      return new ToolchainParameterPair(t, p);
    }
    
    // Fallback 1: default toolchain listed in board xml
    pref = board.getDefaultSynthesisTool();
    t = findByApproximateName(sTools, pref);
    if (t != null) return new ToolchainParameterPair(t, null);

    // Fallback 2: other toolchains listed in board xml
    for (String p : board.getListedToolchains()) {
      if (!board.synthesisEnabled(p)) continue;
      t = findByApproximateName(sTools, p);
      if (t != null) return new ToolchainParameterPair(t, null);
    }

    // Fallback 3: any toolchain supporting this board
    for (Toolchain tt : sTools)
      if (tt.supports(board))
        return new ToolchainParameterPair(tt, null);

    // No known toolchain supports this board, just return anything, whatever
    return new ToolchainParameterPair(sTools.get(0), null);
  }

  public static ToolchainParameterPair autoSelectProgrammingToolchain(Board board) {
    ensureRegistered();
    if (board == null)
      return null; // no board selected, so use null for "auto-select by synthesis tool"
    
    // First priority: user preference for the given board
    String pref = AppPreferences.FPGA_BOARDPREFS.getBoardPreferredProgrammingToolchain(board.name);
    Toolchain t = findByApproximateName(pTools, pref);
    if (t != null) {
      String p = AppPreferences.FPGA_BOARDPREFS.getBoardPreferredProgrammingParams(board.name);
      return new ToolchainParameterPair(t, p);
    }
    
    // Fallback 1: default toolchain listed in board xml
    pref = board.getDefaultProgrammingTool();
    t = findByApproximateName(pTools, pref);
    if (t != null) return new ToolchainParameterPair(t, null);

    // Fallback 2: other toolchains listed in board xml
    for (String p : board.getListedToolchains()) {
      if (!board.programmingEnabled(p)) continue;
      t = findByApproximateName(pTools, p);
      if (t != null) return new ToolchainParameterPair(t, null);
    }

    // Fallback 3: any toolchain supporting this board
    for (Toolchain tt : pTools)
      if (tt.supports(board))
        return new ToolchainParameterPair(tt, null);

    // No known toolchain supports this board, so use null for "auto-select by synthesis tool"
    return null;
  }

  public static String autoSelectLanguage(Board board, Toolchain t) {
    // First priority: user preference for the given board
    String pref = AppPreferences.FPGA_BOARDPREFS.getBoardPreferredHdl(board.name);
    if (VERILOG.equalsIgnoreCase(pref)) return VERILOG;
    if (VHDL.equalsIgnoreCase(pref)) return VHDL;
    // Next, see what toolchain prefers (first item listed in supported languages list)
    if (t == null)
      return VERILOG; // fallback, whatever is fine
    List<String> langs = t.getLanguages(board);
    if (langs.isEmpty())
      return VERILOG; // fallback, whatever is fine
    return langs.get(0); 
  }

  protected static final String dotexe = Main.MSWindows ? ".exe" : ""; // convenience


  private List<String[]> splitKeyValueLines(String input) {
    ArrayList<String[]> result = new ArrayList<>();
    boolean lastWasBlankLine = false;
    for (String line : input.split("\n", -1)) {
      int colon = line.indexOf(':');
      if (colon >= 0) {
        String key = line.substring(0, colon).trim();
        String value = line.substring(colon + 1).trim();
        if (!key.isEmpty()) {
          result.add(new String[]{key, value});
          lastWasBlankLine = false;
          continue;
        }
      }
      result.add(new String[]{line});
      lastWasBlankLine = line.trim().isEmpty();
    }
    if (lastWasBlankLine)
      result.remove(result.size()-1);
    return result;
  }

  // Helper: Given a path (e.g. from settings) and a program name (e.g. "apio"),
  // and one or more alternative program names (e.g. "bin/apio", "Apio"), choose
  // which one should be used for execution. Priority order:
  // 1. Use path alone, if it exists and is executable
  // 2. Use path/prog, if that exists and is executable
  // 3. Use path/altname[i], if that exists and is executable
  // Returns null if none of these are available.
  // Precondition: path must be non-null and non-empty
  static String chooseExecutable(String path, String prog, String ...altprog)  {
    if (path == null || path.isEmpty())
      return null;
    File exe;
    // 1. Use path alone?
    exe = new File(path);
    if (exe.exists() && !exe.isDirectory() && exe.canExecute())
      return exe.toString();
    // 2. Use path/prog?
    exe = new File(path, prog);
    if (exe.exists() && !exe.isDirectory() && exe.canExecute())
      return exe.toString();
    // 3. Use path/altname[i]?
    for (String alt : altprog) {
      exe = new File(path, alt);
      if (exe.exists() && !exe.isDirectory() && exe.canExecute())
        return exe.toString();
    }
    // Not found.
    return null;
  }

  // Helper: Given an optional path (e.g. from settings), a version argument, a
  // program name (e.g. "apio"), and one or more alternative program names (e.g.
  // "bin/apio", "Apio"):
  // - try to find the executable (using path, if set, or trying
  //   system-installed version if unset)
  // - invoke the program with the version flag
  // - if first line of stdout starts with program name, then consider it a success
  protected InstallStatus simpleInstallStatusHelper(String path,
      String versionFlag, String helpmsg, String prog, String ...altprog) {
    if (path == null || path.isEmpty()) {
      // no pref path, user apparently wants to try system-installed executable
      String cmd = prog;
      return simpleGetVersionHelper(cmd, versionFlag, prog, true);
    } else {
      // pref path is set, use it to find executable
      String cmd = chooseExecutable(path, prog, altprog);
      if (cmd == null) {
        return InstallStatus.fromError(
            "%s toolchain path set to '%s' but no suitable program found there. %s",
            toolchainName, path, helpmsg);
      }
      return simpleGetVersionHelper(cmd, versionFlag, prog, false);
    }
  }

  protected InstallStatus simpleGetVersionHelper(String cmd, String versionFlag, String prog, boolean isFromSystem) {
    try {
      List<String> lines = FPGATool.stdoutOrFail(cmd, versionFlag);
      if (lines.isEmpty())
        return InstallStatus.fromError("Executing `%s %s`: no text found in standard output", cmd, versionFlag);
      for (String line : lines) {
        String version = simpleParseVersionHelper(prog, line);
        if (version != null)
          return InstallStatus.fromSuccess(cmd, "Using %s, version %s",
              isFromSystem ? "system-installed " + prog  : "`" + cmd + "`", version);
      }
      return InstallStatus.fromError("Executing `%s %s`: unexpected output: '%s'", cmd, versionFlag, lines.get(0));
    } catch (Exception e) {
      return InstallStatus.fromError("Executing `%s %s`: %s", cmd, versionFlag, e.getMessage());
    }
  }

  // This checks the first line of stdout, looking for "prog: versionstring" or similar.
  // Otherwise, it fails immediately.
  protected String simpleParseVersionHelper(String prog, String line) throws Exception {
    if (line.toLowerCase().startsWith(prog.toLowerCase())) {
      String version = line.substring(prog.length());
      int numPunct = 0;
      while (!version.isEmpty() && " :,;".indexOf(version.charAt(0)) >= 0) {
        numPunct++;
        version = version.substring(1);
      }
      if (numPunct > 0 && !version.isEmpty())
        return version;
    }
    throw new Exception(String.format("unexpected output: '%s'", line));
  }

}
