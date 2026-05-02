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
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.logisim.Main;
import com.cburch.logisim.file.XmlIterator;
import com.cburch.logisim.util.Debug;

// Backing store for user-configurable preferences.
//
// Settings are organized into sections and subsections with
// key-value pairs. 
//
// FIXME: The FPGA section is managed externally by FpgaSettings and
// contributes a raw XML fragment.
//
// Sources (highest to lowest priority):
// 1. User settings file (--config FILE, or settings.xml file in platform-specific location)
// 2. Defaults file      (--defaults FILE, or <jar-dir>/logisim-hc-defaults.xml)
// 3. Hardcoded defaults (registered via PrefMonitor constructors)
//
// If a key-value pair is not found at one level, or if the value is missing (or
// null), the next level is queried. This always succeeds, as the "Hardocded
// Java defaults" level has a non-null default value for every key.
//
// Logisim never modifies the defaults file.
//
// When writing the user settings file, all known sections and keys are
// included. Those not explicitly set by the user will omit the value. 
// - "known" means the hardcoded ones, plus "passthrough" content (any
//   unrecognized section, or unrecognized element within a recognized section,
//   seen while parsing the user settings.xml).
// - "explicitly set" means either: newly set within the preferences UI or
//   similar; or, had with a value in the existing user settings file.
//
// User settings file location (if not overridden by --config FILE option):
//   macOS:   ~/Library/Application Support/logisim-hc/settings.xml
//   Windows: ~/AppData/Roamingl/logisim-hc/settings.xml (%APPDATA% can override this)
//   Linux:   ~/.config/logisim-hc/settings.xml ($XDG_CONFIG_HOME can override this)
//   other:   ~/.logisim-hc/settings.xml
public class SettingsStore {

  static interface Item {
    String getHardcodedDefault();
    String resolve(String userVal, String effDefault);
    String parse(Element setting);
    void writeTo(StringBuilder sb, String indent, String userVal, String effDefault);
  }

  private static class KeyValueItem implements Item {
    // xml: <setting key="k" value="v"> <!-- default: x --> <!-- options: v, w, x -->
    final String key;
    final String dflt; // hardcoded default value, or emptystring; non-null
    final String[] opts; // only for comment; if null, comment is omitted
   
    KeyValueItem(String key, String dflt, String opts[]) {
      this.key = key;
      this.dflt = dflt;
      this.opts = opts;
    }

    @Override
    public String getHardcodedDefault() { return dflt; }

    @Override
    public String resolve(String userVal, String effDefault) {
      return (userVal != null) ? userVal : effDefault;
    }

    @Override
    public String parse(Element setting) {
      if (setting.hasAttribute("value"))
        return setting.getAttribute("value");
      else
        return null;
    }

    @Override
    public void writeTo(StringBuilder sb, String indent, String userVal, String effDefault) {
      sb.append(indent + "<setting key=\"").append(BackingStore.xmlEscapeAttr(key)).append("\"");
      if (userVal != null) {
        sb.append(" value=\"").append(BackingStore.xmlEscapeAttr(userVal)).append("\"/>");
      } else {
        sb.append("/>");
        String comment = (effDefault != null) ? effDefault : "";
        if (comment.isEmpty() || comment.contains(" "))
          comment = "\"" + comment.replace("\"", "\\\"") + "\""; // not precise escaping, but good enough
        sb.append("  <!-- default: ").append(BackingStore.xmlEscapeComment(comment)).append(" -->");
      }
      if (opts != null && opts.length >= 1) {
        sb.append("  <!-- options: ").append(BackingStore.xmlEscapeComment(opts[0]));
        for (int i = 1; i < opts.length; i++)
          sb.append(", " + BackingStore.xmlEscapeComment(opts[i]));
        sb.append(" -->");
      }
      sb.append("\n");
    }

  }

  // Schema: Defines canonical write order, and default and (optional) set of valid values.
  //   section -> key -> value... [hardcoded default, optional list of options, all non-null]
  //   section -> subsection -> null [caller will parse xml dom and write xml fragments]
  private static final LinkedHashMap<String, LinkedHashMap<String, Item>> schema =
      new LinkedHashMap<>();

  // User-set values: keys with a (non-null) value xml attribute in user settings file
  private static final LinkedHashMap<String, LinkedHashMap<String, String>> userValues =
      new LinkedHashMap<>();

  // Values from defaults file (keys with value= attribute)
  private static final LinkedHashMap<String, LinkedHashMap<String, String>> defaultsValues =
      new LinkedHashMap<>();

  // Reload listeners: list of Runnables
  private static final List<Runnable> reloadListeners = new ArrayList<>();

  // FPGA section: managed externally by FpgaSettings FIXME
  private static String customFpgaXml = null;
  static Element fpgaUserElement = null;
  static Element fpgaDefaultsElement = null;

  // Unrecognized top-level sections from the existing user settings.xml.
  private static final ArrayList<String> unrecognizedSections = new ArrayList<>();
  // Unrecognized keys within known sections from the existing user settings.xml.
  private static final HashMap<String, ArrayList<String>> unrecognizedKeys = new HashMap<>();

  private static BackingStore store;
  private static File userFile;
  private static File defaultsFile;

  static void initialize(File configOverride, File defaultsOverride) {
    userFile = (configOverride != null) ? configOverride : new File(getDefaultConfigDir(), "settings.xml");
    defaultsFile = (defaultsOverride != null) ? defaultsOverride : getDefaultDefaultsFile();

    // Load defaults file first (lower priority)
    if (defaultsFile != null && defaultsFile.exists())
      loadFile(defaultsFile, defaultsValues, false);

    // Load user file; if absent and no explicit --config, run migration
    boolean loaded = false;
    if (userFile.exists()) {
      loadFile(userFile, userValues, true);
      loaded = true;
    }

    store = new BackingStore("settings", userFile, SettingsStore::buildXml);
    
    if (!loaded && configOverride == null)
      SettingsMigrator.migrate();

    // Notify all listeners so they initialize to loaded values
    for (Runnable r : reloadListeners)
      r.run();
  }

  static File getUserSettingsFile() { return userFile; }
  static File getDefaultSettingsFile() { return defaultsFile; }

  private static File getDefaultDefaultsFile() {
    String jarDir = getJarDir();
    return (jarDir != null) ? new File(jarDir, "logisim-hc-defaults.xml") : null;
  }

  private static File getDefaultConfigDir() {
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

  private static String getJarDir() {
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
  // Key registration (called by PrefMonitor constructor, TemplatePref, etc.)
  // =========================================================================

  public static void registerKey(String section, String key, String hardcodedDefault, String options[]) {
    schema.computeIfAbsent(section, k -> new LinkedHashMap<>()).putIfAbsent(key, new KeyValueItem(key, hardcodedDefault, options));
  }

  public static void registerSubsection(String section, String subsection, Item item) {
    schema.computeIfAbsent(section, k -> new LinkedHashMap<>()).putIfAbsent(subsection, item);
  }

  // =========================================================================
  // Value access
  // =========================================================================

  public static String getEffective(String section, String key) {
    String v = getUserValue(section, key);
    Item item = getSchemaItem(section, key);
    if (item == null)
      return v;
    String d = getDefaultsFileValue(section, key);
    if (d == null)
      d = item.getHardcodedDefault();
    return item.resolve(v, d);
  }

  public static String getDefault(String section, String key) {
    String v = getDefaultsFileValue(section, key);
    return (v != null) ? v : getHardcodedDefault(section, key);
  }

  public static boolean isUserSet(String section, String key) {
    return getUserValue(section, key) != null;
  }

  private static String getUserValue(String section, String key) {
    synchronized (store.lock) {
      LinkedHashMap<String, String> m = userValues.get(section);
      return (m != null) ? m.get(key) : null;
    }
  }

  private static String getDefaultsFileValue(String section, String key) {
    synchronized (store.lock) {
      LinkedHashMap<String, String> m = defaultsValues.get(section);
      return (m != null) ? m.get(key) : null;
    }
  }

  private static String getHardcodedDefault(String section, String key) {
    synchronized (store.lock) {
      Item item = getSchemaItem(section, key);
      return (item == null) ? null : item.getHardcodedDefault();
    }
  }

  private static Item getSchemaItem(String section, String key) {
    synchronized (store.lock) {
      LinkedHashMap<String, Item> m = schema.get(section);
      return (m == null) ? null : m.get(key);
    }
  }

  // =========================================================================
  // Mutations
  // =========================================================================

  public static void put(String section, String key, String value) {
    synchronized (store.lock) {
      userValues.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, value);
      store.markDirty();
    }
  }

  public static void unset(String section, String key) {
    synchronized (store.lock) {
      LinkedHashMap<String, String> m = userValues.get(section);
      if (m != null && m.remove(key) != null) {
        store.markDirty();
      }
    }
  }

  public static void clear() {
    synchronized (store.lock) {
      userValues.clear();
      store.writeNow();
    }
    // Notify all listeners so they revert to defaults
    for (Runnable r : reloadListeners)
      r.run();
  }

  // =========================================================================
  // FPGA section (managed externally by FpgaSettings)
  // =========================================================================

  /** Called by FpgaSettings to provide its XML content for the next file write. */
  public static void setFpgaXml(String xml) {
    customFpgaXml = xml;
    store.markDirty();
  }

  /** Returns the raw &lt;legacy_fpga&gt; DOM element from the user file, for FpgaSettings to read. */
  public static Element getFpgaUserElement() { return fpgaUserElement; }

  /** Returns the raw &lt;legacy_fpga&gt; DOM element from the defaults file, for FpgaSettings to read. */
  public static Element getFpgaDefaultsElement() { return fpgaDefaultsElement; }


  public static void addReloadListener(Runnable listener) {
    reloadListeners.add(listener);
  }

  public static void save() { store.writeNow(); }

  private static void loadFile(File file,
      LinkedHashMap<String, LinkedHashMap<String, String>> target,
      boolean trackUnregnizedXml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder parser = factory.newDocumentBuilder();
      parser.setErrorHandler(null); // suppress SAX error output
      Document doc = parser.parse(file);
      Element root = doc.getDocumentElement();
      if (!"logisim-hc-settings".equals(root.getTagName())) {
        Debug.error("Warning: Unexpected xml root element in settings file: " + file);
        return;
      }
      try {
        int v = Integer.parseInt(root.getAttribute("schema-version"));
        if (v > 1)
          Debug.error("Warning: Settings file written by newer and incompatible Logisim-HC"
              + " (schema-version=" + v + "): " + file);
      } catch (NumberFormatException ignored) { }

      for (Element sectionElt : XmlIterator.forChildElements(root)) {
        String section = sectionElt.getTagName();
        if (section == null || section.isEmpty())
          continue;

        if ("legacy_fpga".equals(section)) {
          if (target == userValues) fpgaUserElement = sectionElt;
          else fpgaDefaultsElement = sectionElt;
          continue;
        }
        LinkedHashMap<String, Item> ss = schema.get(section);
        if (ss == null) {
          // Preserve unrecognized sections as raw XML strings (for forwards compat).
          if (trackUnregnizedXml)
            unrecognizedSections.add(elementToString(sectionElt));
          continue;
        }

        // Read <setting key="..." value="..."/> items, and <key> subsection items
        for (Element itemElt : XmlIterator.forChildElements(sectionElt)) {
          String key = itemElt.getTagName();
          if ("setting".equals(key))
            key = itemElt.getAttribute("key");
          if (key == null || key.isEmpty())
            continue;
          // don't call getSchemaItem() here, we can't do locking during init
          // Item item = getSchemaItem(section, key);
          Item item = ss.get(key);
          if (item != null) {
            String val = item.parse(itemElt);
            if (val != null)
                target.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, val);
          } else if (trackUnregnizedXml) {
            // Preserve unknown keys as raw XML strings (for forwards compat).
            unrecognizedKeys.computeIfAbsent(section, k -> new ArrayList<>()).add(elementToString(itemElt));
          } 
        }
      }
    } catch (Exception e) {
      Debug.error("Could not read settings file: " + file, e);
    }
  }

  static String buildXml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<!--\n");
    sb.append("  Logisim-HC settings. You can edit this file while Logisim is not running.\n");
    sb.append("  Comments and element ordering are normalized each time Logisim saves.\n");
    sb.append("  Settings without a \"value\" attribute use a default value.\n");
    sb.append("  To set a value, add value=\"...\" and to unset, remove the value\n");
    sb.append("  attribute or delete the entire settins line.\n");
    sb.append("-->\n");
    sb.append("<logisim-hc-settings schema-version=\"1\" written-by=\"");
    sb.append(store.xmlEscapeAttr(Main.VERSION_NAME));
    sb.append("\">\n\n");

    // Known sections in registration order
    for (Map.Entry<String, LinkedHashMap<String, Item>> sectionEntry : schema.entrySet()) {
      String section = sectionEntry.getKey();
      sb.append("  <").append(section).append(">\n");
      for (Map.Entry<String, Item> keyEntry : sectionEntry.getValue().entrySet()) {
        String key = keyEntry.getKey();
        Item item = keyEntry.getValue();
        String userVal = getUserValue(section, key);
        String effDefault = getDefault(section, key);
        item.writeTo(sb, "    ", userVal, effDefault);
      }
      // Unrecognized keys within known section, perhaps from a newer version (preserved as xml strings).
      if (unrecognizedKeys.containsKey(section)) {
        for (String xml : unrecognizedKeys.get(section)) {
          for (String line : xml.split("\n"))
            if (!line.isBlank())
              sb.append("    ").append(line).append("\n");
          sb.append("\n");
        }
      }
      sb.append("  </").append(section).append(">\n\n");
    }

    // FPGA section (managed by FpgaSettings). Fall back to the DOM element
    // loaded from the file if FpgaSettings hasn't initialized yet this run.
    if (customFpgaXml != null && !customFpgaXml.isBlank()) {
      sb.append(customFpgaXml);
      sb.append("\n\n");
    } else if (fpgaUserElement != null) {
      for (String line : elementToString(fpgaUserElement).split("\n"))
        if (!line.isBlank())
          sb.append("  ").append(line).append("\n");
      sb.append("\n\n");
    }

    // Unrecognized sections, perhaps from a newer version (preserved as xml strings).
    for (String xml : unrecognizedSections) {
      for (String line : xml.split("\n"))
        if (!line.isBlank())
          sb.append("  ").append(line).append("\n");
      sb.append("\n");
    }

    sb.append("</logisim-hc-settings>\n");
    return sb.toString();
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
      return "<!-- unrecognized element (xml serialization failed) -->";
    }
  }
}
