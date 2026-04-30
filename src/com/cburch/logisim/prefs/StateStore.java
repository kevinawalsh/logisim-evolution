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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.logisim.Main;
import com.cburch.logisim.file.XmlIterator;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.WeakList;

// Store for UI state that is saved across sessions, but not exposed as
// user-facing preferences, for example: recent projects list, window geometry,
// zoom level, etc.
//
// Most state is accessed via:
//   val = StateStore.SOME_ENTRY.get()
//   StateStore.SOME_ENTRY.set(val)
//
// State file location:
//   macOS:   ~/Library/Application Support/logisim-hc/state.xml
//   Windows: ~/AppData/Local/logisim-hc/state.xml (%LOCALAPPDATA% can override this)
//   Linux:   ~/.local/state/logisim-hc/state.xml ($XDG_STATE_HOME can override this)
//   other:   ~/.logisim-hc/state.xml
public class StateStore {
 
  public static class Entry<V> {
    private final LinkedHashMap<String, Object> map;
    private final String name;

    private Entry(LinkedHashMap<String, Object> map, String name, V defaultValue) {
      this.map = map;
      this.name = name;
      map.put(name, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public V get() { return (V)map.get(name); }

    public void set(V v) { map.put(name, v); store.markDirty(); }
  }

  // Window geometry
  private static final LinkedHashMap<String, Object> window = new LinkedHashMap<>();
  public static final Entry<Integer> WINDOW_STATE = new Entry<>(window, "state", 0); // JFrame.NORMAL
  public static final Entry<Integer> WINDOW_WIDTH = new Entry<>(window, "width", 800);
  public static final Entry<Integer> WINDOW_HEIGHT = new Entry<>(window, "height", 600);
  public static final Entry<Integer> WINDOW_X = new Entry<>(window, "x", 0);
  public static final Entry<Integer> WINDOW_Y = new Entry<>(window, "y", 0);
  public static final Entry<Double> WINDOW_MAIN_SPLIT = new Entry<>(window, "main-split", 0.25);
  public static final Entry<Double> WINDOW_LEFT_SPLIT = new Entry<>(window, "left-split", 0.5);

  // Layout (circuit editor)
  private static final LinkedHashMap<String, Object> layout = new LinkedHashMap<>();
  public static final Entry<Double> LAYOUT_ZOOM = new Entry<>(layout, "zoom", 1.0);
  public static final Entry<Boolean> LAYOUT_GRID = new Entry<>(layout, "grid", true);

  // Appearance (appearance editor)
  private static final LinkedHashMap<String, Object> appearance = new LinkedHashMap<>();
  public static final Entry<Double> APPEARANCE_ZOOM = new Entry<>(appearance, "zoom", 1.0);
  public static final Entry<Boolean> APPEARANCE_GRID = new Entry<>(appearance, "grid", true);

  // Simulation
  private static final LinkedHashMap<String, Object> simulation = new LinkedHashMap<>();
  public static final Entry<Double> TICK_FREQ = new Entry<>(simulation, "tick-frequency", 1.0);

  // Tips and Hints Activity
  // private static final LinkedHashMap<String, Object> activity = new LinkedHashMap<>();
  // public static final Entry<Integer> WIRING_TOOL_ACTIVITY_COUNTER = new Entry<>(activity, "wiring", 0);
  // public static final Entry<Integer> CUTTER_TOOL_ACTIVITY_COUNTER = new Entry<>(activity, "cutter", 0);

  // Most recent directory for file-chooser dialogs
  private static final LinkedHashMap<String, Object> dialog = new LinkedHashMap<>();
  public static final Entry<String> DIALOG_DIRECTORY = new Entry<>(dialog, "directory", "");

  // Recent projects (most-recent-first, max 10)
  private static final List<String> recentProjects = new ArrayList<>();
  private static final int MAX_RECENT = 10;
  public static final RecentProjects RECENT_PROJECTS = new RecentProjects();

  static void updateRecentProject(File file) {
    synchronized (store.lock) {
      String path;
      try { path = file.getCanonicalPath(); }
      catch (IOException e) { path = file.getAbsolutePath(); }
      recentProjects.remove(path);
      recentProjects.add(0, path);
      while (recentProjects.size() > MAX_RECENT)
        recentProjects.remove(recentProjects.size() - 1);
    }
    store.markDirty();
  }

  private static List<File> getRecentProjects() {
    synchronized (store.lock) {
      ArrayList<File> ret = new ArrayList<>();
      for (String path : recentProjects)
        ret.add(new File(path));
      return ret;
    }
  }

  private static BackingStore store;
  private static Object lock = new Object();
  private static File stateFile;

  public static void initialize() {
    stateFile = new File(getDefaultStateDir(), "state.xml");
    if (stateFile.exists())
      loadFile(stateFile);
    store = new BackingStore("UI-state", stateFile, StateStore::buildXml);
  }

  private static File getDefaultStateDir() {
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

  public static void save() { store.writeNow(); }

  private static void loadElement(LinkedHashMap<String, Object> map, Element elt) {
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String s = elt.getAttribute(e.getKey());
      if (s == null || s.isEmpty())
        continue;
      Object defaultValue = e.getValue();
      try {
        if (defaultValue instanceof Integer) e.setValue(Integer.parseInt(s));
        else if (defaultValue instanceof Double) e.setValue(Double.parseDouble(s));
        else if (defaultValue instanceof Boolean) e.setValue("true".equalsIgnoreCase(s));
        else if (defaultValue instanceof String) e.setValue(s);
      } catch (NumberFormatException ex) {
        continue;
      }
    }
  }

  private static void loadFile(File file) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder parser = factory.newDocumentBuilder();
      parser.setErrorHandler(null); // suppress SAX stderr output
      Document doc = parser.parse(file);
      Element root = doc.getDocumentElement();
      if (!"logisim-hc-state".equals(root.getTagName())) {
        Debug.error("Warning: Unexpected xml root element in state file: " + file);
        return;
      }
      try {
        int v = Integer.parseInt(root.getAttribute("schema-version"));
        if (v > 1)
          Debug.error("Warning: State file written by newer and incompatible Logisim-HC"
              + " (schema-version=" + v + "): " + file);
      } catch (NumberFormatException ignored) { }

      for (Element elt : XmlIterator.forChildElements(root)) {
        switch (elt.getTagName()) {
          case "window": loadElement(window, elt); break;
          case "layout": loadElement(layout, elt); break;
          case "appearance": loadElement(appearance, elt); break;
          case "simulation": loadElement(simulation, elt); break;
          case "dialog": loadElement(dialog, elt); break;
          // case "activity": loadElement(activity, elt); break;
          case "recent-projects":
            recentProjects.clear();
            for (Element proj : XmlIterator.forChildElements(elt)) {
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
      Debug.error("Error reading saved UI state", e);
    }
  }

  private static String toNode(LinkedHashMap<String, Object> map, String name) {
    StringBuilder sb = new StringBuilder();
    sb.append("<" + name);
    for (Map.Entry<String, Object> e : map.entrySet())
      sb.append(" " + e.getKey() + "=\"" + store.xmlEscapeAttr(e.getValue().toString()) + "\"");
    sb.append("/>\n");
    return sb.toString();
  }

  private static String buildXml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<!-- Logisim-HC saved UI state. Overwritten by Logisim-HC, any manual edits will be lost. -->\n");
    sb.append("<logisim-hc-state schema-version=\"1\" written-by=\"");
    sb.append(store.xmlEscapeAttr(Main.VERSION_NAME));
    sb.append("\">\n\n");

    sb.append("  " + toNode(window, "window"));
    sb.append("  " + toNode(layout, "layout"));
    sb.append("  " + toNode(appearance, "appearance"));
    sb.append("  " + toNode(simulation, "simulation"));
    sb.append("  " + toNode(dialog, "dialog"));
    // sb.append("  " + toNode(activity, "activity"));

    if (!recentProjects.isEmpty()) {
      sb.append("  <recent-projects>\n");
      for (String path : recentProjects)
        sb.append("    <project path=\"").append(store.xmlEscapeAttr(path)).append("\"/>\n");
      sb.append("  </recent-projects>\n");
    }

    sb.append("\n</logisim-hc-state>\n");
    return sb.toString();
  }

  public static class RecentProjects {

    private final WeakList<AppPreferences.Listener<List<File>>> listeners = new WeakList<>();
    public void addPrefChangeWeakListener(Object owner, AppPreferences.Listener<List<File>> l) { listeners.add(owner, l); }
    public void removePrefChangeWeakListener(Object owner, AppPreferences.Listener<List<File>> l) { listeners.remove(owner, l); }
    private void firePrefChangeEvent(AppPreferences.ChangeEvent<List<File>> evt) { for (AppPreferences.Listener<List<File>> l : listeners) l.prefChanged(evt); }

    RecentProjects() { }

    // Returns list of recent projects, most-recent-first.
    public List<File> get() {
      return getRecentProjects();
    }

    // Updates list to indicate the given file was just opened/saved.
    // Updates StateStore and fires a change event so the UI menu refreshes.
    public void update(File file) {
      List<File> oldList = get();
      updateRecentProject(file);
      List<File> newList = get();
      SwingUtilities.invokeLater(() ->
          firePrefChangeEvent(new AppPreferences.ChangeEvent<List<File>>(this, oldList, newList)));
    }

  }

}
