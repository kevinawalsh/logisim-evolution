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

package com.bfh.logisim.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import javax.swing.JFrame;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.bfh.logisim.download.FPGADownload;

// import com.bfh.logisim.download.LatticeDownload;
import com.bfh.logisim.gui.FPGASettingsDialog;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.SettingsStore;

/**
 * FPGA-related settings, backed by the {@code <legacy_fpga>} section of
 * {@code settings.xml} managed by {@link SettingsStore}.
 *
 * All workspace values are held in-memory. Changes are flushed back to
 * SettingsStore (and from there to disk) via {@link #updateSettingsFile()}.
 * The getter/setter API is identical to the old Settings.java so that the
 * ~40 FPGA callsites need no changes.
 */
public class Settings {

  public static final String VHDL    = "VHDL";
  public static final String VERILOG = "Verilog";

  // =========================================================================
  // In-memory workspace settings
  // =========================================================================

  // private String  workspacePath = "";
  private String  hdlType       = VHDL;
  private String  selectedBoard = "";
  // private boolean useRBF        = false;
  // private String  xilinxPath    = "";
  // private String  alteraPath    = "";
  // private boolean altera64bit   = true;
  // private String  gowinShPath   = "";
  // private String  gowinProgPath = "";
  // private String  latticePath   = "";
  // private String  apioPath      = "";
  // private String  openFpgaPath  = "";

  // Per-board preferences: boardName -> {attrName -> value}
  private final LinkedHashMap<String, LinkedHashMap<String, String>> boardPrefs =
      new LinkedHashMap<>();

  // External board file paths (in registration order)
  private final ArrayList<String> externalBoards = new ArrayList<>();

  // Board catalog (built-in + external)
  private final BoardList knownBoards = new BoardList();

  private final ArrayList<Listener> listeners = new ArrayList<>();
  private boolean dirty = false;

  // =========================================================================
  // Singleton
  // =========================================================================

  private static Settings singleton;

  public static Settings getSettings() {
    if (singleton == null)
      singleton = new Settings();
    return singleton;
  }

  private Settings() {
    loadFromStore(SettingsStore.getFpgaDefaultsElement(), false);
    loadFromStore(SettingsStore.getFpgaUserElement(), true);

    // Validate selectedBoard; fall back to first known board if invalid
    if (selectedBoard.isEmpty() || !knownBoards.BoardInCollection(selectedBoard)) {
      Collection<String> names = knownBoards.GetBoardNames();
      if (!names.isEmpty())
        selectedBoard = names.iterator().next();
    }
  }

  // =========================================================================
  // Public listener / dialog API
  // =========================================================================

  public interface Listener {
    void fpgaSettingsChanged();
  }

  public void addSettingsListener(Listener l)    { listeners.add(l); }
  public void removeSettingsListener(Listener l) { listeners.remove(l); }
  public void notifyListeners() {
    for (Listener l : listeners) l.fpgaSettingsChanged();
  }

  private static FPGASettingsDialog dialog;
  public static void doSettingsDialog(JFrame parentFrame) {
    if (dialog != null) {
      dialog.toFront();
    } else {
      Settings s = getSettings();
      dialog = new FPGASettingsDialog(parentFrame, s);
      dialog.doDialog();
      dialog = null;
    }
  }

  // =========================================================================
  // Tool-path getters / setters / validators
  // =========================================================================

  // public String GetAlteraToolPath()  { return alteraPath; }
  // public String GetXilinxToolPath()  { return xilinxPath; }
  // public String GetGowinShPath()     { return gowinShPath; }
  // public String GetGowinProgPath()   { return gowinProgPath; }
  // public String GetLatticeToolPath() { return latticePath; }
  // public String GetApioToolPath()    { return apioPath; }
  // public String GetOpenFPGALoaderPath() { return openFpgaPath; }

  // public boolean SetAlteraToolPath(String path) {
  //   path = normalizePath(path);
  //   if (!validAlteraToolPath(path)) return false;
  //   alteraPath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean validAlteraToolPath(String path) {
  //   path = normalizePath(path);
  //   return path == null
  //       || path.toLowerCase().startsWith("http://")
  //       || path.toLowerCase().startsWith("https://")
  //       || allToolsPresent(path, FPGADownload.ALTERA_PROGRAMS)
  //       || isExecutableScript(path);
  // }

  // public boolean SetXilinxToolPath(String path) {
  //   path = normalizePath(path);
  //   if (!validXilinxToolPath(path)) return false;
  //   xilinxPath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean validXilinxToolPath(String path) {
  //   path = normalizePath(path);
  //   return path == null
  //       || allToolsPresent(path, FPGADownload.XILINX_PROGRAMS)
  //       || isExecutableScript(path);
  // }

  // public boolean SetGowinShPath(String path) {
  //   path = normalizePath(path);
  //   gowinShPath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean SetGowinProgPath(String path) {
  //   path = normalizePath(path);
  //   gowinProgPath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean validGowinToolPath(String path) {
  //   path = normalizePath(path);
  //   return path == null
  //       || isExecutableScript(path + File.separator + FPGADownload.GOWIN_SH);
  // }

  // public boolean SetLatticeToolPath(String path) {
  //   path = normalizePath(path);
  //   if (!validLatticeToolPath(path)) return false;
  //   latticePath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean validLatticeToolPath(String path) {
  //   path = normalizePath(path);
  //   return path == null
  //       || LatticeDownload.getToolChainType(path) != LatticeDownload.TOOLCHAIN.UNKNOWN;
  // }

  // public boolean SetApioToolPath(String path) {
  //   path = normalizePath(path);
  //   if (!validApioToolPath(path)) return false;
  //   apioPath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean validApioToolPath(String path) {
  //   path = normalizePath(path);
  //   return path == null
  //       || allToolsPresent(path, FPGADownload.APIO_PROGRAMS);
  // }

  // public boolean SetOpenFPGAloaderPath(String path) {
  //   path = normalizePath(path);
  //   if (!validOpenFPGAloaderPath(path)) return false;
  //   openFpgaPath = nvl(path);
  //   markDirty(); return true;
  // }

  // public boolean validOpenFPGAloaderPath(String path) {
  //   path = normalizePath(path);
  //   return path == null || isExecutableScript(path);
  // }

  // =========================================================================
  // Other workspace settings
  // =========================================================================

  // public boolean GetUseRBF()          { return useRBF; }
  // public void    SetUseRBF(boolean v) { useRBF = v; markDirty(); }

  // public boolean GetAltera64Bit()          { return altera64bit; }
  // public void    SetAltera64Bit(boolean v) { altera64bit = v; markDirty(); }

  public String GetHDLType() { return hdlType; }
  public void SetHDLType(String lang) {
    if (VHDL.equalsIgnoreCase(lang))    { hdlType = VHDL;    markDirty(); }
    else if (VERILOG.equalsIgnoreCase(lang)) { hdlType = VERILOG; markDirty(); }
  }

  // =========================================================================
  // Board selection
  // =========================================================================

  public Collection<String> GetBoardNames() { return knownBoards.GetBoardNames(); }

  public String GetSelectedBoard() { return selectedBoard; }

  public boolean SetSelectedBoard(String name) {
    if (!knownBoards.BoardInCollection(name)) return false;
    selectedBoard = name;
    markDirty();
    return true;
  }

  public String GetSelectedBoardFileName() {
    return knownBoards.GetBoardFilePath(selectedBoard);
  }

  // =========================================================================
  // Board-specific preferences
  // =========================================================================

  public String GetPreferredToolchain(String board) {
    String pref = getBoardPref(board, "Toolchain");
    String tc = FPGADownload.normalizeToolchain(pref);
    if (pref != null && tc == null && !warnedBadToolchain) {
      warnedBadToolchain = true;
      javax.swing.JOptionPane.showMessageDialog(null,
          "Error: Unrecognized toolchain '" + pref + "' in settings.xml legacy_fpga section");
    }
    return tc;
  }

  public String GetPreferredHDLType(String board) {
    String pref = getBoardPref(board, "HDLTypeToGenerate");
    if (VHDL.equalsIgnoreCase(pref))    return VHDL;
    if (VERILOG.equalsIgnoreCase(pref)) return VERILOG;
    if (pref != null && !warnedBadHDLType) {
      warnedBadHDLType = true;
      javax.swing.JOptionPane.showMessageDialog(null,
          "Error: Unrecognized HDL type '" + pref + "' in settings.xml legacy_fpga section");
    }
    return null;
  }

  public void SetPreferredToolchain(String board, String toolchain) {
    setBoardPref(board, "Toolchain", toolchain);
  }

  public void SetPreferredHDLType(String board, String hdlType) {
    setBoardPref(board, "HDLTypeToGenerate", hdlType);
  }

  private String getBoardPref(String board, String attr) {
    LinkedHashMap<String, String> m = boardPrefs.get(board);
    if (m == null) return null;
    String v = m.get(attr);
    return (v == null || v.trim().isEmpty()) ? null : v;
  }

  private void setBoardPref(String board, String attr, String val) {
    boardPrefs.computeIfAbsent(board, k -> new LinkedHashMap<>()).put(attr, val);
    markDirty();
  }

  private static boolean warnedBadToolchain = false;
  private static boolean warnedBadHDLType   = false;

  // =========================================================================
  // Workspace path
  // =========================================================================

  // public String GetStaticWorkspacePath() {
  //   return workspacePath.isEmpty() ? null : workspacePath;
  // }

  // public void SetStaticWorkspacePath(String path) {
  //   path = normalizePath(path);
  //   workspacePath = nvl(path);
  //   markDirty();
  // }

  public String GetWorkspacePath(File projectFile) {
    String p = AppPreferences.FPGA_WORKSPACE_PATH.get();
    if (p != null && !p.isEmpty())
      return p; // FIXME: create subdirectory per project?
    String home = System.getProperty("user.home");
    if (projectFile != null) {
      String dir  = projectFile.getAbsoluteFile().getParentFile().getAbsolutePath();
      String name = projectFile.getName()
          .replaceAll(".circ.xml$", "").replaceAll(".circ$", "") + "_fpga_workspace";
      return dir + File.separator + name;
    }
    return home + File.separator + "logisim_fpga_workspace" + File.separator;
  }

  // =========================================================================
  // External boards
  // =========================================================================

  public void AddExternalBoard(String filename) {
    if (!externalBoards.contains(filename)) {
      externalBoards.add(filename);
      knownBoards.AddExternalBoard(filename);
      markDirty();
    }
  }

  // =========================================================================
  // Persistence
  // =========================================================================

  /** Flush any pending changes to SettingsStore (and from there to disk). */
  public boolean UpdateSettingsFile() {
    return updateSettingsFile();
  }

  /** Same as {@link #UpdateSettingsFile()} — lower-case form for internal callers. */
  public boolean updateSettingsFile() {
    if (!dirty) return true;
    SettingsStore.setFpgaXml(buildFpgaXml());
    SettingsStore.save();
    dirty = false;
    return true;
  }

  private void markDirty() { dirty = true; }

  // =========================================================================
  // Load from SettingsStore DOM elements
  // =========================================================================

  /**
   * Reads settings from a {@code <legacy_fpga>} DOM element (either user file or defaults file).
   * When {@code isUser} is true, user-specific structured data (board prefs, external
   * boards) is also loaded.
   */
  private void loadFromStore(Element fpgaEl, boolean isUser) {
    if (fpgaEl == null) return;

    NodeList children = fpgaEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (!(children.item(i) instanceof Element)) continue;
      Element el = (Element) children.item(i);

      switch (el.getTagName()) {
        case "workspace":
          readWorkspace(el);
          break;
        case "board-preferences":
          if (isUser) readBoardPrefs(el);
          break;
        case "external-boards":
          if (isUser) readExternalBoards(el);
          break;
        // unknown sub-elements silently ignored
      }
    }
  }

  private void readWorkspace(Element ws) {
    NodeList settings = ws.getChildNodes();
    for (int i = 0; i < settings.getLength(); i++) {
      if (!(settings.item(i) instanceof Element)) continue;
      Element s = (Element) settings.item(i);
      if (!"setting".equals(s.getTagName())) continue;
      if (!s.hasAttribute("value")) continue; // unset — keep current value
      String key = s.getAttribute("key");
      String val = s.getAttribute("value");
      switch (key) {
        // case "workspacePath":     workspacePath = val; break;
        case "hdlType":
          if (VERILOG.equalsIgnoreCase(val)) hdlType = VERILOG;
          else hdlType = VHDL;
          break;
        case "selectedBoard":     selectedBoard = val; break;
        // case "useRawBinaryFormat": useRBF = "true".equalsIgnoreCase(val); break;
        // case "xilinxToolsPath":   xilinxPath  = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // case "alteraToolsPath":   alteraPath  = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // case "altera64bit":       altera64bit = "true".equalsIgnoreCase(val); break;
        // case "gowinShPath":       gowinShPath  = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // case "gowinProgPath":     gowinProgPath = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // case "latticeToolsPath":  latticePath = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // case "apioToolsPath":     apioPath    = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // case "openFPGAloaderPath": openFpgaPath = normalizePath(val) != null ? normalizePath(val) : ""; break;
        // unknown keys silently ignored
      }
    }
  }

  private void readBoardPrefs(Element bpEl) {
    NodeList nodes = bpEl.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      if (!(nodes.item(i) instanceof Element)) continue;
      Element board = (Element) nodes.item(i);
      if (!"board".equals(board.getTagName())) continue;
      String name = board.getAttribute("name");
      if (name == null || name.isEmpty()) continue;
      NamedNodeMap attrs = board.getAttributes();
      for (int j = 0; j < attrs.getLength(); j++) {
        Node a = attrs.item(j);
        if (!"name".equals(a.getNodeName()))
          boardPrefs.computeIfAbsent(name, k -> new LinkedHashMap<>())
                    .put(a.getNodeName(), a.getNodeValue());
      }
    }
  }

  private void readExternalBoards(Element ebEl) {
    NodeList nodes = ebEl.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      if (!(nodes.item(i) instanceof Element)) continue;
      Element board = (Element) nodes.item(i);
      if (!"board".equals(board.getTagName())) continue;
      String path = board.getAttribute("path");
      if (path != null && !path.isEmpty() && new File(path).exists()) {
        externalBoards.add(path);
        knownBoards.AddExternalBoard(path);
      }
    }
  }

  // =========================================================================
  // Build XML for SettingsStore
  // =========================================================================

  private String buildFpgaXml() {
    StringBuilder sb = new StringBuilder();
    sb.append("  <legacy_fpga>\n");
    sb.append("    <workspace>\n");
    // appendSetting(sb, "workspacePath",      workspacePath);
    appendSetting(sb, "hdlType",            hdlType);
    appendSetting(sb, "selectedBoard",      selectedBoard);
    // appendSetting(sb, "useRawBinaryFormat", "" + useRBF);
    // appendSetting(sb, "xilinxToolsPath",    xilinxPath);
    // appendSetting(sb, "alteraToolsPath",    alteraPath);
    // appendSetting(sb, "altera64bit",        "" + altera64bit);
    // appendSetting(sb, "gowinShPath",        gowinShPath);
    // appendSetting(sb, "gowinProgPath",      gowinProgPath);
    // appendSetting(sb, "latticeToolsPath",   latticePath);
    // appendSetting(sb, "apioToolsPath",      apioPath);
    // appendSetting(sb, "openFPGAloaderPath", openFpgaPath);
    sb.append("    </workspace>\n");

    if (!boardPrefs.isEmpty()) {
      sb.append("    <board-preferences>\n");
      for (java.util.Map.Entry<String, LinkedHashMap<String, String>> e : boardPrefs.entrySet()) {
        sb.append("      <board name=\"").append(xmlAttr(e.getKey())).append("\"");
        for (java.util.Map.Entry<String, String> a : e.getValue().entrySet())
          sb.append(" ").append(a.getKey()).append("=\"").append(xmlAttr(a.getValue())).append("\"");
        sb.append("/>\n");
      }
      sb.append("    </board-preferences>\n");
    }

    if (!externalBoards.isEmpty()) {
      sb.append("    <external-boards>\n");
      for (String path : externalBoards)
        sb.append("      <board path=\"").append(xmlAttr(path)).append("\"/>\n");
      sb.append("    </external-boards>\n");
    }

    sb.append("  </legacy_fpga>");
    return sb.toString();
  }

  private static void appendSetting(StringBuilder sb, String key, String value) {
    sb.append("      <setting key=\"").append(key).append("\"");
    if (value != null && !value.isEmpty())
      sb.append(" value=\"").append(xmlAttr(value)).append("\"");
    sb.append("/>\n");
  }

  private static String xmlAttr(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
  }

  // =========================================================================
  // Path utilities
  // =========================================================================

  private static String normalizePath(String path) {
    if (path == null || path.isEmpty()) return null;
    if (path.length() > 1 && path.endsWith(File.separator))
      path = path.substring(0, path.length() - 1);
    return path;
  }

  /** Null-safe: returns empty string for null input. */
  private static String nvl(String s) { return s != null ? s : ""; }

  private static boolean allToolsPresent(String path, String[] progNames) {
    for (String prog : progNames) {
      if (!new File(path + File.separator + prog).exists())
        return false;
    }
    return true;
  }

  private static boolean isExecutableScript(String path) {
    File f = new File(path);
    return f.exists() && !f.isDirectory() && f.canExecute();
  }
}
