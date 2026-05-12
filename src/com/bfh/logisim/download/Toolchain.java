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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.bfh.logisim.fpga.Board;
import com.cburch.logisim.Main;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Debug;

public abstract class Toolchain {

  public static final String VHDL = "VHDL";
  public static final String VERILOG = "Verilog";


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
  public abstract List<String[]> defaultParams(/* Board board*/);

  // Get a list of languages supported for a given board, e.g. VERLOG and/or VHDL.
  public abstract List<String> getLanguages(Board board);

  // Create a new downloader, to be configured and used imminently.
  public abstract FPGADownload newDownloader();
  
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
  static {
    ApioDownload.register();
    AlteraDownload.register();
    XilinxDownload.register();
    LatticeDownload.register();
    GowinDownload.register();
    OpenFPGALoader.register();
    // USBTMC.register(); // FIXME: TODO
  }

  public static List<Toolchain> getAllToolchains() {
    return Collections.unmodifiableList(tools);
  }
  
  public static List<Toolchain> getSynthesisToolchains() {
    return Collections.unmodifiableList(sTools);
  }


  //  ArrayList<String> ret = new ArrayList<>();
  //  for (Toolchain t: sTools)
  //    ret.add(t.toolchainName);
  //  return ret;

  // public static ArrayList<String> getSynthesisToolchainNames() {
  //   ArrayList<String> ret = new ArrayList<>();
  //   for (Toolchain t: sTools)
  //     ret.add(t.toolchainName);
  //   return ret;
  // }

  // public static ArrayList<String> getProgrammerToolchainNames() {
  //   ArrayList<String> ret = new ArrayList<>();
  //   for (Toolchain t: pTools)
  //     ret.add(t.toolchainName);
  //   return ret;
  // }

  // public static ArrayList<String> getAllToolchainNames() {
  //   ArrayList<String> ret = new ArrayList<>();
  //   for (Toolchain t: sTools)
  //       ret.add(t.toolchainName);
  //   for (Toolchain t: pTools)
  //       ret.add(t.toolchainName);
  //   return ret;
  // }
  
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

  private static Toolchain findSynthesisToolchainByApproximateName(String name) {
    return findByApproximateName(sTools, name);
  }

  public static Toolchain findToolchainByApproximateName(String name) {
    return findByApproximateName(tools, name);
  }

  public static Toolchain autoSelectSynthesisToolchain(Board board) {
    if (board == null)
      return sTools.get(0); // no board selected, so any toolchain is fine, whatever
    
    // First priority: user preference for the given board
    String pref = AppPreferences.FPGA_BOARDPREFS.getBoardPreferredToolchain(board.name);
    Toolchain t = findSynthesisToolchainByApproximateName(pref);
    if (t != null) return t;
    
    // Fallback 1: default toolchain listed in board xml
    pref = board.getDefaultSynthesisTool();
    t = findSynthesisToolchainByApproximateName(pref);
    if (t != null) return t;

    // Fallback 2: other toolchains listed in board xml
    for (String p : board.getListedToolchains()) {
      if (!board.synthesisEnabled(p)) continue;
      t = findSynthesisToolchainByApproximateName(p);
      if (t != null) return t;
    }

    // Fallback 3: any toolchain supporting this board
    for (Toolchain tt : sTools)
      if (tt.supports(board))
        return tt;

    // No known toolchain supports this board, just return anything, whatever
    return sTools.get(0);
  }

  public static String autoSelectLanguage(Board board, Toolchain t) {
    if (t == null)
      return VERILOG; // fallback, whatever is fine
    List<String> langs = t.getLanguages(board);
    if (langs.isEmpty())
      return VERILOG; // fallback, whatever is fine
    return langs.get(0); 
  }

  // public static String getLanguage(Board board, String toolchainName) {
  //   Toolchain t = findSynthesisToolchain(toolchainName);
  //   if (t == null)
  //     return VERILOG; // FIXME: fallback
  //   FPGADownload tool = t.newDownloader();
  //   tool.board = board;
  //   List<String> langs = tool.getLanguages();
  //   if (langs.isEmpty())
  //     return VERILOG; // FIXME: fallback
  //   return langs.get(0); 
  // }

  // public static String getSynthesisToolchainName(Board board) {
  //   Toolchain t = getSynthesisToolchain(board);
  //   return t == null ? "no toolchains available" : t.toolchainName;
  // }
  
  // public static FPGADownload forToolchain(String toolchainName) {
  //   Toolchain t = findSynthesisToolchain(toolchainName);
  //   return t == null ? null : t.newDownloader();
  // }

  protected static final String dotexe = Main.MSWindows ? ".exe" : ""; // convenience

}
