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
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cburch.logisim.Main;

/**
 * Central store for user-configurable application preferences.
 *
 * Settings are organized into sections (e.g. "display", "simulation") and
 * stored as string key-value pairs. The FPGA section is managed externally by
 * FpgaSettings and contributes a raw XML fragment.
 *
 * Layered loading (highest to lowest priority):
 *   1. User settings file  (--config FILE, or platform default)
 *   2. Defaults file       (--defaults FILE, or <jar-dir>/logisim-hc-defaults.xml)
 *   3. Hardcoded Java defaults (registered via PrefMonitor constructors)
 *
 * Only user-set keys (those with a "value" attribute in the XML) are stored
 * in userValues. Keys absent from userValues fall through to defaults.
 */
public class SettingsStore {

  // Schema: section -> key -> hardcoded default. Defines canonical write order.
  private static final LinkedHashMap<String, LinkedHashMap<String, String>> schema =
      new LinkedHashMap<>();

  // User-set values (keys with value= attribute in user settings file)
  private static final LinkedHashMap<String, LinkedHashMap<String, String>> userValues =
      new LinkedHashMap<>();

  // Values from defaults file (keys with value= attribute)
  private static final LinkedHashMap<String, LinkedHashMap<String, String>> defaultsValues =
      new LinkedHashMap<>();

  // Change listeners: "section|key" -> list of Runnables
  private static final Map<String, List<Runnable>> changeListeners = new LinkedHashMap<>();

  // FPGA section: managed externally by FpgaSettings
  private static String customFpgaXml = null;
  static Element fpgaUserElement = null;
  static Element fpgaDefaultsElement = null;

  // Unknown top-level sections not managed by SettingsStore or FpgaSettings
  private static final List<String> passthroughXml = new ArrayList<>();

  // File paths
  private static File userFile;
  private static File defaultsFile;

  // Debounced write state
  private static volatile boolean dirty = false;
  private static Timer writeTimer;
  private static TimerTask writeTask;
  private static final long WRITE_DELAY_MS = 500;

  // =========================================================================
  // Initialization
  // =========================================================================

  public static void initialize(File configOverride, File defaultsOverride) {
    userFile = (configOverride != null) ? configOverride : getDefaultUserFile();
    defaultsFile = (defaultsOverride != null) ? defaultsOverride : getDefaultDefaultsFile();

    // Load defaults file first (lower priority)
    if (defaultsFile != null && defaultsFile.exists()) {
      loadFile(defaultsFile, defaultsValues, false);
    }

    // Load user file; if absent and no explicit --config, run migration
    if (userFile.exists()) {
      loadFile(userFile, userValues, true);
    } else if (configOverride == null) {
      SettingsMigrator.migrate();
    }

    // Flush any pending writes on JVM exit
    Runtime.getRuntime().addShutdownHook(new Thread(SettingsStore::flushIfDirty, "logisim-settings-flush"));
  }

  // =========================================================================
  // File location
  // =========================================================================

  public static File getUserSettingsFile() { return userFile; }
  public static File getDefaultsFile() { return defaultsFile; }

  static File getDefaultUserFile() {
    return new File(getDefaultConfigDir(), "settings.xml");
  }

  static File getDefaultDefaultsFile() {
    String jarDir = getJarDir();
    return (jarDir != null) ? new File(jarDir, "logisim-hc-defaults.xml") : null;
  }

  static File getDefaultConfigDir() {
    String home = System.getProperty("user.home");
    if (Main.MacOS) {
      return new File(home, "Library/Application Support/logisim-hc");
    } else if (Main.MSWindows) {
      String appdata = System.getenv("APPDATA");
      if (appdata != null && !appdata.isBlank())
        return new File(appdata, "logisim-hc");
      return new File(home, "AppData/Roaming/logisim-hc");
    } else if (Main.Linux) {
      String xdgConfig = System.getenv("XDG_CONFIG_HOME");
      if (xdgConfig != null && !xdgConfig.isBlank())
        return new File(xdgConfig, "logisim-hc");
      return new File(home, ".config/logisim-hc");
    } else {
      return new File(home, ".logisim-hc");
    }
  }

  static String getJarDir() {
    try {
      String path = SettingsStore.class.getProtectionDomain()
          .getCodeSource().getLocation().getPath();
      String decoded = URLDecoder.decode(path, "UTF-8");
      File parent = new File(decoded).getParentFile();
      return (parent != null) ? parent.getAbsolutePath() : null;
    } catch (UnsupportedEncodingException | SecurityException e) {
      return null;
    }
  }

  // =========================================================================
  // Key registration (called by PrefMonitor constructor)
  // =========================================================================

  /** Registers a key with its hardcoded default. Defines canonical write order. */
  public static void registerKey(String section, String key, String hardcodedDefault) {
    schema.computeIfAbsent(section, k -> new LinkedHashMap<>())
          .putIfAbsent(key, hardcodedDefault);
  }

  // =========================================================================
  // Value access
  // =========================================================================

  /** Returns the effective value: user > defaults file > hardcoded default. */
  public static String getEffective(String section, String key) {
    String v = getUserValue(section, key);
    if (v != null) return v;
    v = getDefaultsFileValue(section, key);
    if (v != null) return v;
    return getHardcodedDefault(section, key);
  }

  /** Returns the default value: defaults file > hardcoded default. */
  public static String getDefault(String section, String key) {
    String v = getDefaultsFileValue(section, key);
    return (v != null) ? v : getHardcodedDefault(section, key);
  }

  public static boolean isUserSet(String section, String key) {
    return getUserValue(section, key) != null;
  }

  private static String getUserValue(String section, String key) {
    LinkedHashMap<String, String> m = userValues.get(section);
    return (m != null) ? m.get(key) : null;
  }

  private static String getDefaultsFileValue(String section, String key) {
    LinkedHashMap<String, String> m = defaultsValues.get(section);
    return (m != null) ? m.get(key) : null;
  }

  private static String getHardcodedDefault(String section, String key) {
    LinkedHashMap<String, String> m = schema.get(section);
    return (m != null) ? m.get(key) : null;
  }

  // =========================================================================
  // Mutations
  // =========================================================================

  public static void put(String section, String key, String value) {
    userValues.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, value);
    dirty = true;
    scheduleWrite();
    fireListeners(section, key);
  }

  public static void unset(String section, String key) {
    LinkedHashMap<String, String> m = userValues.get(section);
    if (m != null && m.remove(key) != null) {
      dirty = true;
      scheduleWrite();
      fireListeners(section, key);
    }
  }

  public static void clear() {
    userValues.clear();
    dirty = true;
    writeNow();
    // Notify all PrefMonitors so they revert to defaults
    for (Map.Entry<String, List<Runnable>> e : changeListeners.entrySet())
      for (Runnable r : e.getValue())
        r.run();
  }

  // =========================================================================
  // FPGA section (managed externally by FpgaSettings)
  // =========================================================================

  /** Called by FpgaSettings to provide its XML content for the next file write. */
  public static void setFpgaXml(String xml) {
    customFpgaXml = xml;
    dirty = true;
    scheduleWrite();
  }

  /** Returns the raw &lt;fpga&gt; DOM element from the user file, for FpgaSettings to read. */
  public static Element getFpgaUserElement() { return fpgaUserElement; }

  /** Returns the raw &lt;fpga&gt; DOM element from the defaults file, for FpgaSettings to read. */
  public static Element getFpgaDefaultsElement() { return fpgaDefaultsElement; }

  // =========================================================================
  // Change listeners
  // =========================================================================

  public static void addChangeListener(String section, String key, Runnable listener) {
    String k = section + "|" + key;
    changeListeners.computeIfAbsent(k, x -> new CopyOnWriteArrayList<>()).add(listener);
  }

  private static void fireListeners(String section, String key) {
    List<Runnable> list = changeListeners.get(section + "|" + key);
    if (list != null)
      for (Runnable r : list) r.run();
  }

  // =========================================================================
  // Write scheduling
  // =========================================================================

  private static synchronized void scheduleWrite() {
    if (writeTask != null) writeTask.cancel();
    if (writeTimer == null)
      writeTimer = new Timer("logisim-settings-write", true); // daemon
    writeTask = new TimerTask() {
      @Override public void run() { writeNow(); }
    };
    writeTimer.schedule(writeTask, WRITE_DELAY_MS);
  }

  static synchronized void flushIfDirty() {
    if (dirty) writeNow();
  }

  /** Flush any pending changes immediately; callable from any package. */
  public static void save() {
    dirty = true;
    writeNow();
  }

  static synchronized void writeNow() {
    if (writeTask != null) { writeTask.cancel(); writeTask = null; }
    try {
      File dir = userFile.getParentFile();
      if (dir != null) dir.mkdirs();
      File tmp = new File(dir != null ? dir : new File("."), userFile.getName() + ".tmp");
      Files.writeString(tmp.toPath(), buildXml(), StandardCharsets.UTF_8);
      Files.move(tmp.toPath(), userFile.toPath(),
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      dirty = false;
    } catch (IOException e) {
      System.err.println("Warning: Could not write settings file: " + userFile + ": " + e.getMessage());
    }
  }

  // =========================================================================
  // XML reading
  // =========================================================================

  private static void loadFile(File file,
      LinkedHashMap<String, LinkedHashMap<String, String>> target,
      boolean loadPassthrough) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder parser = factory.newDocumentBuilder();
      parser.setErrorHandler(null); // suppress SAX error output
      Document doc = parser.parse(file);
      Element root = doc.getDocumentElement();
      if (!"logisim-hc-settings".equals(root.getTagName())) {
        System.err.println("Warning: Unexpected root in settings file: " + file);
        return;
      }
      try {
        int v = Integer.parseInt(root.getAttribute("schema-version"));
        if (v > 1)
          System.err.println("Warning: Settings file written by newer Logisim-HC (schema-version="
              + v + "). Some settings may be ignored: " + file);
      } catch (NumberFormatException ignored) { }

      NodeList children = root.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node n = children.item(i);
        if (!(n instanceof Element)) continue;
        Element sectionEl = (Element) n;
        String sectionName = sectionEl.getTagName();

        if ("fpga".equals(sectionName)) {
          if (target == userValues) fpgaUserElement = sectionEl;
          else fpgaDefaultsElement = sectionEl;
          continue;
        }

        // Read <setting key="..." value="..."/> elements
        NodeList settings = sectionEl.getChildNodes();
        for (int j = 0; j < settings.getLength(); j++) {
          Node sn = settings.item(j);
          if (!(sn instanceof Element)) continue;
          Element setting = (Element) sn;
          if (!"setting".equals(setting.getTagName())) continue;
          String key = setting.getAttribute("key");
          if (key == null || key.isEmpty()) continue;
          if (setting.hasAttribute("value")) {
            target.computeIfAbsent(sectionName, k -> new LinkedHashMap<>())
                  .put(key, setting.getAttribute("value"));
          }
        }

        // Preserve unknown sections as raw XML strings (for forwards compat)
        if (loadPassthrough && !schema.containsKey(sectionName) && !"fpga".equals(sectionName)) {
          passthroughXml.add(elementToString(sectionEl));
        }
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not read settings file: " + file + ": " + e.getMessage());
    }
  }

  // =========================================================================
  // XML writing
  // =========================================================================

  static String buildXml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<!--\n");
    sb.append("  Logisim-HC settings file.\n");
    sb.append("  This file is managed by Logisim-HC. You may hand-edit it while Logisim is not running.\n");
    sb.append("  Comments and element ordering are normalized each time Logisim saves.\n");
    sb.append("  Settings without a \"value\" attribute are unset and use the current default.\n");
    sb.append("  To set a value: add value=\"...\"   To unset: remove the value attribute (or delete the line).\n");
    sb.append("-->\n");
    sb.append("<logisim-hc-settings schema-version=\"1\" written-by=\"");
    sb.append(xmlAttr(Main.VERSION_NAME));
    sb.append("\">\n\n");

    // Known sections in registration order
    for (Map.Entry<String, LinkedHashMap<String, String>> sectionEntry : schema.entrySet()) {
      String section = sectionEntry.getKey();
      sb.append("  <").append(section).append(">\n");
      for (Map.Entry<String, String> keyEntry : sectionEntry.getValue().entrySet()) {
        String key = keyEntry.getKey();
        String userVal = getUserValue(section, key);
        String effDefault = getDefault(section, key);
        sb.append("    <setting key=\"").append(xmlAttr(key)).append("\"");
        if (userVal != null) {
          sb.append(" value=\"").append(xmlAttr(userVal)).append("\"/>");
        } else {
          sb.append("/>");
          String shown = (effDefault != null) ? effDefault : "";
          sb.append("  <!-- default: ").append(xmlComment(shown)).append(" -->");
        }
        sb.append("\n");
      }
      // Preserve any user-set keys not in schema (future-version compat, going backwards)
      LinkedHashMap<String, String> extraUserKeys = userValues.get(section);
      if (extraUserKeys != null) {
        for (Map.Entry<String, String> extra : extraUserKeys.entrySet()) {
          if (!sectionEntry.getValue().containsKey(extra.getKey())) {
            sb.append("    <setting key=\"").append(xmlAttr(extra.getKey()))
              .append("\" value=\"").append(xmlAttr(extra.getValue())).append("\"/>\n");
          }
        }
      }
      sb.append("  </").append(section).append(">\n\n");
    }

    // FPGA section (managed by FpgaSettings)
    if (customFpgaXml != null && !customFpgaXml.isBlank()) {
      sb.append(customFpgaXml);
      sb.append("\n\n");
    }

    // Unknown sections from a newer version (preserved verbatim)
    for (String passthrough : passthroughXml) {
      // Indent two spaces
      for (String line : passthrough.split("\n")) {
        sb.append("  ").append(line).append("\n");
      }
      sb.append("\n");
    }

    sb.append("</logisim-hc-settings>\n");
    return sb.toString();
  }

  // =========================================================================
  // XML helpers
  // =========================================================================

  static String xmlAttr(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
  }

  static String xmlComment(String s) {
    if (s == null) return "";
    // XML comments must not contain "--"
    return s.replace("--", "-\u2012"); // replace with figure dash
  }

  private static String elementToString(Element e) {
    try {
      StringWriter sw = new StringWriter();
      Transformer t = TransformerFactory.newInstance().newTransformer();
      t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      t.setOutputProperty(OutputKeys.INDENT, "yes");
      t.transform(new DOMSource(e), new StreamResult(sw));
      return sw.toString().trim();
    } catch (Exception ex) {
      return "<!-- unknown element (serialization failed) -->";
    }
  }
}
