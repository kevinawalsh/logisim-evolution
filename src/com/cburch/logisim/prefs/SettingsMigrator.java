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
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.prefs;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * One-shot, silent migration from old storage formats to the new XML files.
 *
 * Called by SettingsStore.initialize() when no user settings file exists and
 * --config was not given. Reads:
 *   - Old java.util.prefs.Preferences node (com/cburch/logisim)
 *   - Old ~/.LogisimFPGASettings.xml and <jar-dir>/LogisimFPGASettings.xml
 *
 * True preferences go to SettingsStore; automatically-captured state goes to
 * StateStore. Old stores are left untouched (safe rollback).
 */
class SettingsMigrator {

  // Old Java Preferences node path
  private static final String OLD_PREFS_NODE = "com/cburch/logisim";

  // Old FPGA settings file names
  private static final String OLD_FPGA_USER    = ".LogisimFPGASettings.xml";
  private static final String OLD_FPGA_SHARED  = "LogisimFPGASettings.xml";

  // Keys that belong in StateStore (not settings)
  private static final java.util.Set<String> STATE_KEYS = new java.util.HashSet<>(
      java.util.Arrays.asList(
        "tickFrequency",
        "layoutGrid",
        "layoutZoom",
        "appearanceGrid",
        "appearanceZoom",
        "windowState",
        "windowWidth",
        "windowHeight",
        "windowLocation",
        "windowMainSplit",
        "windowLeftSplit",
        "windowRightSplit",
        "dialogDirectory"
      ));

  // Mapping from old pref key → settings section (for non-state keys)
  private static final java.util.Map<String, String> SECTION = new java.util.HashMap<>();
  static {
    // display section
    for (String k : new String[]{ "gateShape", "locale", "accentsReplace",
        "showTickRate", "showCoordinates", "toolbarPlacement", "printerView",
        "attributeHalo", "componentTips", "keepConnected", "showGhosts",
        "afterAdd", "pokeRadix1", "pokeRadix2" })
      SECTION.put(k, "display");

    // simulation section
    for (String k : new String[]{ "autobackup", "autobackupFreq",
        "questaPath", "questaValidation" })
      SECTION.put(k, "simulation");

    // graphics section
    for (String k : new String[]{ "graphicsAcceleration", "dualScreenFixes" })
      SECTION.put(k, "graphics");

    // misc section
    for (String k : new String[]{ "wiringToolTip", "cutterToolTip",
        "templateType", "templateFile" })
      SECTION.put(k, "misc");
  }

  static void migrate() {
    migrateJavaPrefs();
    migrateFpgaSettings();
  }

  // =========================================================================
  // Migrate java.util.prefs.Preferences
  // =========================================================================

  private static void migrateJavaPrefs() {
    Preferences node;
    try {
      if (!Preferences.userRoot().nodeExists(OLD_PREFS_NODE))
        return;
      node = Preferences.userRoot().node(OLD_PREFS_NODE);
    } catch (BackingStoreException e) {
      return; // no old prefs, nothing to migrate
    }

    // Migrate settings keys
    try {
      for (String key : node.keys()) {
        String value = node.get(key, null);
        if (value == null) continue;

        if (STATE_KEYS.contains(key)) {
          migrateStateKey(key, value);
        } else if (SECTION.containsKey(key)) {
          SettingsStore.put(SECTION.get(key), key, value);
        } else if (key.startsWith("recent")) {
          // handled separately below
        }
        // unknown keys silently skipped
      }
    } catch (BackingStoreException e) {
      // partial migration is acceptable
    }

    // Migrate recent projects: "recent0".."recent9", each "timestamp;path"
    List<long[]> recent = new ArrayList<>(); // [timestamp, index] pairs
    for (int i = 0; i < 10; i++) {
      String encoded = node.get("recent" + i, null);
      if (encoded == null) continue;
      int semi = encoded.indexOf(';');
      if (semi < 0) continue;
      try {
        long ts = Long.parseLong(encoded.substring(0, semi));
        String path = encoded.substring(semi + 1);
        if (!path.isEmpty())
          recent.add(new long[]{ ts, i, path.hashCode() });
        // store path alongside: use index to re-fetch
      } catch (NumberFormatException ignored) { }
    }

    // Re-do in sorted order: oldest first so that prepend ends up most-recent-first
    // Build list of (timestamp, path) sorted oldest→newest
    List<long[]> withPaths = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String encoded = node.get("recent" + i, null);
      if (encoded == null) continue;
      int semi = encoded.indexOf(';');
      if (semi < 0) continue;
      try {
        long ts = Long.parseLong(encoded.substring(0, semi));
        String path = encoded.substring(semi + 1);
        if (!path.isEmpty())
          withPaths.add(new long[]{ ts, i }); // store index for path retrieval
      } catch (NumberFormatException ignored) { }
    }

    // Sort by timestamp ascending (oldest first → prepend → newest ends up at index 0)
    // Actually: we want to call updateRecentProject in oldest→newest order so that the
    // final list has most-recent at index 0.
    Collections.sort(withPaths, (a, b) -> Long.compare(a[0], b[0]));
    for (long[] entry : withPaths) {
      int idx = (int) entry[1];
      String encoded = node.get("recent" + idx, null);
      if (encoded == null) continue;
      int semi = encoded.indexOf(';');
      if (semi < 0) continue;
      String path = encoded.substring(semi + 1);
      if (!path.isEmpty())
        StateStore.updateRecentProject(new File(path));
    }
  }

  private static void migrateStateKey(String key, String value) {
    try {
      switch (key) {
        case "tickFrequency":
          StateStore.setTickFrequency(Double.parseDouble(value)); break;
        case "layoutGrid":
          StateStore.setLayoutGrid(Boolean.parseBoolean(value)); break;
        case "layoutZoom":
          StateStore.setLayoutZoom(Double.parseDouble(value)); break;
        case "appearanceGrid":
          StateStore.setAppearanceGrid(Boolean.parseBoolean(value)); break;
        case "appearanceZoom":
          StateStore.setAppearanceZoom(Double.parseDouble(value)); break;
        case "windowState":
          StateStore.setWindowState(Integer.parseInt(value)); break;
        case "windowWidth":
          StateStore.setWindowWidth(Integer.parseInt(value)); break;
        case "windowHeight":
          StateStore.setWindowHeight(Integer.parseInt(value)); break;
        case "windowLocation": {
          int comma = value.indexOf(',');
          if (comma >= 0) {
            StateStore.setWindowX(Integer.parseInt(value.substring(0, comma).trim()));
            StateStore.setWindowY(Integer.parseInt(value.substring(comma + 1).trim()));
          }
          break;
        }
        case "windowMainSplit":
          StateStore.setMainSplit(Double.parseDouble(value)); break;
        case "windowLeftSplit":
          StateStore.setLeftSplit(Double.parseDouble(value)); break;
        case "windowRightSplit":
          // kept as a default (0.75); not exposed in StateStore, silently dropped
          break;
        case "dialogDirectory":
          StateStore.setDialogDirectory(value); break;
      }
    } catch (NumberFormatException ignored) {
      // bad stored value; leave StateStore at its default
    }
  }

  // =========================================================================
  // Migrate old FPGA settings XML
  // =========================================================================

  private static void migrateFpgaSettings() {
    // Try user home file first, then shared (jar-dir) file
    String home = System.getProperty("user.home");
    File userFile = new File(home, OLD_FPGA_USER);
    File sharedFile = getSharedFpgaFile();

    Document doc = tryParseXml(userFile);
    if (doc == null && sharedFile != null)
      doc = tryParseXml(sharedFile);
    if (doc == null)
      return; // no old FPGA settings; FpgaSettings will create defaults

    try {
      Element root = doc.getDocumentElement();
      if (!"LogisimFPGASettings".equals(root.getTagName()))
        return;

      String workPath = "";
      String alteraPath = "";
      String xilinxPath = "";
      String gowinShPath = "";
      String gowinProgPath = "";
      String altera64bit = "true";
      String latticePath = "";
      String apioPath = "";
      String openFpgaPath = "";
      String rawBinary = "false";
      String hdlType = "VHDL";
      String selectedBoard = "";

      // Read WorkSpace element
      NodeList wsList = root.getElementsByTagName("WorkSpace");
      if (wsList.getLength() > 0) {
        Element ws = (Element) wsList.item(0);
        workPath    = attr(ws, "WorkPath", "");
        alteraPath  = attr(ws, "AlteraToolsPath", "");
        xilinxPath  = attr(ws, "XilinxToolsPath", "");
        gowinShPath = attr(ws, "GowinShPath", "");
        gowinProgPath = attr(ws, "GowinProgPath", "");
        altera64bit = attr(ws, "Altera64Bit", "true");
        latticePath = attr(ws, "LatticeToolsPath", "");
        apioPath    = attr(ws, "ApioToolsPath", "");
        openFpgaPath = attr(ws, "openFPGAloaderPath", "");
        rawBinary   = attr(ws, "RawBinaryFormat", "false");
        hdlType     = attr(ws, "HDLTypeToGenerate", "VHDL");
      }

      // Read FPGABoards element
      List<String> externalBoards = new ArrayList<>();
      List<String[]> boardPrefs = new ArrayList<>(); // [name, toolchain, hdlType]
      NodeList boardsList = root.getElementsByTagName("FPGABoards");
      if (boardsList.getLength() > 0) {
        Element boards = (Element) boardsList.item(0);
        selectedBoard = attr(boards, "SelectedBoard", "");

        // External board files: ExternalBoardFile_1, ExternalBoardFile_2, ...
        NamedNodeMap attrs = boards.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
          Node a = attrs.item(i);
          if (a.getNodeName().startsWith("ExternalBoardFile_")) {
            String path = a.getNodeValue();
            if (path != null && !path.isEmpty())
              externalBoards.add(path);
          }
        }

        // Board-specific preferences: <Settings Board="..." Toolchain="..." HDLTypeToGenerate="..."/>
        NodeList settings = boards.getChildNodes();
        for (int i = 0; i < settings.getLength(); i++) {
          if (!(settings.item(i) instanceof Element)) continue;
          Element s = (Element) settings.item(i);
          if (!"Settings".equals(s.getTagName())) continue;
          String bName = attr(s, "Board", "");
          String bTc   = attr(s, "Toolchain", "");
          String bHdl  = attr(s, "HDLTypeToGenerate", "");
          if (!bName.isEmpty())
            boardPrefs.add(new String[]{ bName, bTc, bHdl });
        }
      }

      // Build new-format <fpga> XML for SettingsStore
      StringBuilder sb = new StringBuilder();
      sb.append("  <fpga>\n");
      sb.append("    <workspace>\n");
      appendSetting(sb, "workspacePath",     workPath);
      appendSetting(sb, "hdlType",           hdlType);
      appendSetting(sb, "selectedBoard",     selectedBoard);
      appendSetting(sb, "useRawBinaryFormat", rawBinary);
      appendSetting(sb, "xilinxToolsPath",   xilinxPath);
      appendSetting(sb, "alteraToolsPath",   alteraPath);
      appendSetting(sb, "altera64bit",       altera64bit);
      appendSetting(sb, "gowinShPath",       gowinShPath);
      appendSetting(sb, "gowinProgPath",     gowinProgPath);
      appendSetting(sb, "latticeToolsPath",  latticePath);
      appendSetting(sb, "apioToolsPath",     apioPath);
      appendSetting(sb, "openFPGAloaderPath", openFpgaPath);
      sb.append("    </workspace>\n");

      if (!boardPrefs.isEmpty()) {
        sb.append("    <board-preferences>\n");
        for (String[] bp : boardPrefs) {
          sb.append("      <board name=\"").append(SettingsStore.xmlAttr(bp[0])).append("\"");
          if (!bp[1].isEmpty())
            sb.append(" toolchain=\"").append(SettingsStore.xmlAttr(bp[1])).append("\"");
          if (!bp[2].isEmpty())
            sb.append(" hdlType=\"").append(SettingsStore.xmlAttr(bp[2])).append("\"");
          sb.append("/>\n");
        }
        sb.append("    </board-preferences>\n");
      }

      if (!externalBoards.isEmpty()) {
        sb.append("    <external-boards>\n");
        for (String path : externalBoards) {
          sb.append("      <board path=\"").append(SettingsStore.xmlAttr(path))
            .append("\"/>\n");
        }
        sb.append("    </external-boards>\n");
      }

      sb.append("  </fpga>");
      SettingsStore.setFpgaXml(sb.toString());

    } catch (Exception e) {
      // Silent: failed FPGA migration is not fatal; user can reconfigure via dialog
    }
  }

  private static void appendSetting(StringBuilder sb, String key, String value) {
    if (value == null || value.isEmpty()) return; // omit unset (falsy) values
    sb.append("      <setting key=\"").append(key)
      .append("\" value=\"").append(SettingsStore.xmlAttr(value))
      .append("\"/>\n");
  }

  private static String attr(Element el, String name, String dflt) {
    String v = el.getAttribute(name);
    return (v == null || v.isEmpty()) ? dflt : v;
  }

  private static Document tryParseXml(File file) {
    if (file == null || !file.exists()) return null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder parser = factory.newDocumentBuilder();
      parser.setErrorHandler(null);
      return parser.parse(file);
    } catch (Exception e) {
      return null;
    }
  }

  private static File getSharedFpgaFile() {
    try {
      String path = SettingsMigrator.class.getProtectionDomain()
          .getCodeSource().getLocation().getPath();
      String decoded = URLDecoder.decode(path, "UTF-8");
      File parent = new File(decoded).getParentFile();
      return (parent != null) ? new File(parent, OLD_FPGA_SHARED) : null;
    } catch (UnsupportedEncodingException | SecurityException e) {
      return null;
    }
  }
}
