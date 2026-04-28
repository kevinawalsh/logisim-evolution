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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.cburch.logisim.Main;

/**
 * Store for automatically-captured UI state (window geometry, zoom levels, etc.).
 *
 * Unlike SettingsStore, there is no layering, no user/default distinction, and
 * no "unset" concept. The app owns these values and writes them on every change.
 *
 * State file location:
 *   macOS:   ~/Library/Application Support/logisim-hc/state.xml
 *   Windows: %LOCALAPPDATA%\logisim-hc\state.xml   (non-roaming: machine-specific)
 *   Linux:   ${XDG_STATE_HOME:-~/.local/state}/logisim-hc/state.xml
 *   other:   ~/.logisim-hc/state.xml
 */
public class StateStore {

  // Window geometry
  private static int windowState = 0; // JFrame.NORMAL
  private static int windowWidth = 640;
  private static int windowHeight = 480;
  private static int windowX = 0;
  private static int windowY = 0;
  private static double mainSplit = 0.25;
  private static double leftSplit = 0.5;

  // Layout (circuit editor)
  private static double layoutZoom = 1.0;
  private static boolean layoutGrid = true;

  // Appearance (appearance editor)
  private static double appearanceZoom = 1.0;
  private static boolean appearanceGrid = true;

  // Simulation
  private static double tickFrequency = 1.0;

  // File chooser dialog
  private static String dialogDirectory = "";

  // Recent projects (most-recent-first, max 10)
  private static final List<String> recentProjects = new ArrayList<>();
  private static final int MAX_RECENT = 10;

  // File location
  private static File stateFile;

  // Debounced write state
  private static volatile boolean dirty = false;
  private static Timer writeTimer;
  private static TimerTask writeTask;
  private static final long WRITE_DELAY_MS = 500;

  // =========================================================================
  // Initialization
  // =========================================================================

  public static void initialize() {
    stateFile = getDefaultStateFile();
    if (stateFile.exists()) {
      loadFile(stateFile);
    }
    Runtime.getRuntime().addShutdownHook(
        new Thread(StateStore::flushIfDirty, "logisim-state-flush"));
  }

  public static File getStateFile() { return stateFile; }

  static File getDefaultStateFile() {
    return new File(getDefaultStateDir(), "state.xml");
  }

  static File getDefaultStateDir() {
    String home = System.getProperty("user.home");
    if (Main.MacOS) {
      // Same directory as settings.xml on macOS
      return new File(home, "Library/Application Support/logisim-hc");
    } else if (Main.MSWindows) {
      // %LOCALAPPDATA% (non-roaming) since window positions are machine-specific
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank())
        return new File(localAppData, "logisim-hc");
      return new File(home, "AppData/Local/logisim-hc");
    } else if (Main.Linux) {
      String xdgState = System.getenv("XDG_STATE_HOME");
      if (xdgState != null && !xdgState.isBlank())
        return new File(xdgState, "logisim-hc");
      return new File(home, ".local/state/logisim-hc");
    } else {
      return new File(home, ".logisim-hc");
    }
  }

  // =========================================================================
  // Getters / setters — window geometry
  // =========================================================================

  public static int getWindowState() { return windowState; }
  public static void setWindowState(int v) { windowState = v; markDirty(); }

  public static int getWindowWidth() { return windowWidth; }
  public static void setWindowWidth(int v) { windowWidth = v; markDirty(); }

  public static int getWindowHeight() { return windowHeight; }
  public static void setWindowHeight(int v) { windowHeight = v; markDirty(); }

  public static int getWindowX() { return windowX; }
  public static void setWindowX(int v) { windowX = v; markDirty(); }

  public static int getWindowY() { return windowY; }
  public static void setWindowY(int v) { windowY = v; markDirty(); }

  public static double getMainSplit() { return mainSplit; }
  public static void setMainSplit(double v) { mainSplit = v; markDirty(); }

  public static double getLeftSplit() { return leftSplit; }
  public static void setLeftSplit(double v) { leftSplit = v; markDirty(); }

  // =========================================================================
  // Getters / setters — canvas state
  // =========================================================================

  public static double getLayoutZoom() { return layoutZoom; }
  public static void setLayoutZoom(double v) { layoutZoom = v; markDirty(); }

  public static boolean getLayoutGrid() { return layoutGrid; }
  public static void setLayoutGrid(boolean v) { layoutGrid = v; markDirty(); }

  public static double getAppearanceZoom() { return appearanceZoom; }
  public static void setAppearanceZoom(double v) { appearanceZoom = v; markDirty(); }

  public static boolean getAppearanceGrid() { return appearanceGrid; }
  public static void setAppearanceGrid(boolean v) { appearanceGrid = v; markDirty(); }

  // =========================================================================
  // Getters / setters — simulation and dialog
  // =========================================================================

  public static double getTickFrequency() { return tickFrequency; }
  public static void setTickFrequency(double v) { tickFrequency = v; markDirty(); }

  public static String getDialogDirectory() { return dialogDirectory; }
  public static void setDialogDirectory(String v) {
    dialogDirectory = (v != null) ? v : "";
    markDirty();
  }

  // =========================================================================
  // Recent projects
  // =========================================================================

  /** Returns an unmodifiable view of the recent project paths, most-recent-first. */
  public static List<String> getRecentProjects() {
    return Collections.unmodifiableList(recentProjects);
  }

  /**
   * Records that the given file was just opened/saved. Prepends it to the list,
   * removes any earlier occurrence of the same path, and trims to MAX_RECENT.
   */
  public static void updateRecentProject(File file) {
    String path;
    try { path = file.getCanonicalPath(); }
    catch (IOException e) { path = file.getAbsolutePath(); }
    recentProjects.remove(path);
    recentProjects.add(0, path);
    while (recentProjects.size() > MAX_RECENT)
      recentProjects.remove(recentProjects.size() - 1);
    markDirty();
  }

  // =========================================================================
  // Write scheduling
  // =========================================================================

  private static void markDirty() {
    dirty = true;
    scheduleWrite();
  }

  private static synchronized void scheduleWrite() {
    if (writeTask != null) writeTask.cancel();
    if (writeTimer == null)
      writeTimer = new Timer("logisim-state-write", true); // daemon timer
    writeTask = new TimerTask() {
      @Override public void run() { writeNow(); }
    };
    writeTimer.schedule(writeTask, WRITE_DELAY_MS);
  }

  static synchronized void flushIfDirty() {
    if (dirty) writeNow();
  }

  /** Flush immediately; called from Frame.savePreferences() on quit. */
  public static void save() {
    dirty = true;
    writeNow();
  }

  static synchronized void writeNow() {
    if (writeTask != null) { writeTask.cancel(); writeTask = null; }
    try {
      File dir = stateFile.getParentFile();
      if (dir != null) dir.mkdirs();
      File tmp = new File(dir != null ? dir : new File("."), stateFile.getName() + ".tmp");
      Files.writeString(tmp.toPath(), buildXml(), StandardCharsets.UTF_8);
      Files.move(tmp.toPath(), stateFile.toPath(),
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      dirty = false;
    } catch (IOException e) {
      System.err.println("Warning: Could not write state file: " + stateFile
          + ": " + e.getMessage());
    }
  }

  // =========================================================================
  // XML reading
  // =========================================================================

  private static void loadFile(File file) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder parser = factory.newDocumentBuilder();
      parser.setErrorHandler(null); // suppress SAX stderr output
      Document doc = parser.parse(file);
      Element root = doc.getDocumentElement();
      if (!"logisim-hc-state".equals(root.getTagName())) {
        System.err.println("Warning: Unexpected root element in state file: " + file);
        return;
      }
      try {
        int v = Integer.parseInt(root.getAttribute("schema-version"));
        if (v > 1)
          System.err.println("Warning: State file written by newer Logisim-HC"
              + " (schema-version=" + v + "): " + file);
      } catch (NumberFormatException ignored) { }

      NodeList children = root.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        if (!(children.item(i) instanceof Element)) continue;
        Element el = (Element) children.item(i);
        switch (el.getTagName()) {
          case "window":
            windowState  = parseInt(el.getAttribute("state"),      windowState);
            windowWidth  = parseInt(el.getAttribute("width"),       windowWidth);
            windowHeight = parseInt(el.getAttribute("height"),      windowHeight);
            windowX      = parseInt(el.getAttribute("x"),           windowX);
            windowY      = parseInt(el.getAttribute("y"),           windowY);
            mainSplit    = parseDouble(el.getAttribute("main-split"), mainSplit);
            leftSplit    = parseDouble(el.getAttribute("left-split"), leftSplit);
            break;
          case "layout":
            layoutZoom = parseDouble(el.getAttribute("zoom"), layoutZoom);
            layoutGrid = parseBoolean(el.getAttribute("grid"), layoutGrid);
            break;
          case "appearance":
            appearanceZoom = parseDouble(el.getAttribute("zoom"), appearanceZoom);
            appearanceGrid = parseBoolean(el.getAttribute("grid"), appearanceGrid);
            break;
          case "simulation":
            tickFrequency = parseDouble(el.getAttribute("tick-frequency"), tickFrequency);
            break;
          case "file-dialog":
            String d = el.getAttribute("directory");
            if (d != null) dialogDirectory = d;
            break;
          case "recent-projects":
            recentProjects.clear();
            NodeList projects = el.getChildNodes();
            for (int j = 0; j < projects.getLength(); j++) {
              if (!(projects.item(j) instanceof Element)) continue;
              Element proj = (Element) projects.item(j);
              if (!"project".equals(proj.getTagName())) continue;
              String path = proj.getAttribute("path");
              if (path != null && !path.isEmpty())
                recentProjects.add(path);
            }
            break;
          // unknown elements silently ignored (forwards compatibility)
        }
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not read state file: " + file
          + ": " + e.getMessage());
    }
  }

  // =========================================================================
  // XML writing
  // =========================================================================

  static String buildXml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<!-- Logisim-HC state file."
        + " Written automatically on exit. Hand edits will be overwritten. -->\n");
    sb.append("<logisim-hc-state schema-version=\"1\">\n\n");

    sb.append("  <window state=\"").append(windowState)
      .append("\" width=\"").append(windowWidth)
      .append("\" height=\"").append(windowHeight)
      .append("\" x=\"").append(windowX)
      .append("\" y=\"").append(windowY)
      .append("\" main-split=\"").append(mainSplit)
      .append("\" left-split=\"").append(leftSplit)
      .append("\"/>\n");

    sb.append("  <layout zoom=\"").append(layoutZoom)
      .append("\" grid=\"").append(layoutGrid).append("\"/>\n");

    sb.append("  <appearance zoom=\"").append(appearanceZoom)
      .append("\" grid=\"").append(appearanceGrid).append("\"/>\n");

    sb.append("  <simulation tick-frequency=\"").append(tickFrequency).append("\"/>\n");

    sb.append("  <file-dialog directory=\"").append(xmlAttr(dialogDirectory))
      .append("\"/>\n\n");

    if (!recentProjects.isEmpty()) {
      sb.append("  <!-- Most-recently-used files, most recent first. -->\n");
      sb.append("  <recent-projects>\n");
      for (String path : recentProjects)
        sb.append("    <project path=\"").append(xmlAttr(path)).append("\"/>\n");
      sb.append("  </recent-projects>\n\n");
    }

    sb.append("</logisim-hc-state>\n");
    return sb.toString();
  }

  // =========================================================================
  // Parse helpers
  // =========================================================================

  static String xmlAttr(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
  }

  private static int parseInt(String s, int dflt) {
    if (s == null || s.isEmpty()) return dflt;
    try { return Integer.parseInt(s); }
    catch (NumberFormatException e) { return dflt; }
  }

  private static double parseDouble(String s, double dflt) {
    if (s == null || s.isEmpty()) return dflt;
    try { return Double.parseDouble(s); }
    catch (NumberFormatException e) { return dflt; }
  }

  private static boolean parseBoolean(String s, boolean dflt) {
    if (s == null || s.isEmpty()) return dflt;
    return "true".equalsIgnoreCase(s);
  }
}
