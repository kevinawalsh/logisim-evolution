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

package com.cburch.logisim.prefs;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.bfh.logisim.download.FPGADownload;
import com.cburch.logisim.file.XmlIterator;
// import com.cburch.logisim.util.WeakList;

public class FPGABoardPrefs implements SettingsStore.Item {

  // "board-preferences" subsection of "fpga" section of settings.xml
 
  private final String section; //  "fpga"
  private final String subsection; // "board-preferences"

  // boardname -> key -> value
  private LinkedHashMap<String, LinkedHashMap<String, String>> prefs = new LinkedHashMap<>();

  public FPGABoardPrefs(String section, String subsection) {
    this.section = section;
    this.subsection = subsection;

    // Register so we appear in settings.xml
    SettingsStore.registerSubsection(section, subsection, this);

    // React to changes pushed by SettingsStore (e.g. from load, reload, or clear)
    SettingsStore.addReloadListener(() -> setFromStore());
  }

  public Map<String, String> getBoardPrefs(String board) {
    LinkedHashMap<String, String> p = prefs.get(board);
    if (p == null)
      return Collections.emptyMap();
    else
      return Collections.unmodifiableMap(p);
  }

  public String getBoardPref(String board, String key) {
    return getBoardPrefs(board).get(key);
  }
 
  // FIXME: these need a home
  public static final String VHDL    = "VHDL";
  public static final String VERILOG = "Verilog";

  public String getBoardPreferredHdl(String board) {
    String hdl = getBoardPref(board, "hdl");
    if (VHDL.equalsIgnoreCase(hdl)) return VHDL;
    else if (VERILOG.equalsIgnoreCase(hdl)) return VERILOG;
    else return null;
  }

  public String getBoardPreferredToolchain(String board) {
    return getBoardPref(board, "toolchain");
  }

  public void setBoardPreferredHdl(String board, String hdl) {
    if (VHDL.equalsIgnoreCase(hdl)) hdl = VHDL;
    else if (VERILOG.equalsIgnoreCase(hdl)) hdl = VERILOG;
    else return;
    setBoardPref(board, "hdl", hdl);
  }

  public void setBoardPreferredToolchain(String board, String toolchain) {
    setBoardPref(board, "toolchain", toolchain);
  }

  private void setBoardPref(String board, String key, String val) {
    LinkedHashMap<String, String> p = prefs.computeIfAbsent(board, k -> new LinkedHashMap<>());
    String oldVal = p.get(key);
    if (val.equals(oldVal))
        return;
    p.put(key, val);
    SettingsStore.put(section, subsection, encode(prefs));
    AppPreferences.fireFPGAChangeEvent();
  }

  private void setFromStore() {
    String s = SettingsStore.getEffective(section, subsection);
    prefs.clear();
    // s == "board:key=val:key=val:...|board:key=val:key=val:..."
    if (!s.isEmpty()) { 
      for (String boardSection : s.split("\\|", -1)) {
        String[] parts = boardSection.split(":", -1);
        String board = unescape(parts[0]);
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
          int eq = parts[i].indexOf('=');
          String key = unescape(parts[i].substring(0, eq));
          String val = unescape(parts[i].substring(eq + 1));
          p.put(key, val);
        }
        prefs.put(board, p);
      }
    }
    AppPreferences.fireFPGAChangeEvent();
  }

  @Override
  public String getHardcodedDefault() { return ""; }

  @Override
  public String resolve(String userVal, String effDefault) {
    if (userVal == null || userVal.isEmpty())
      return effDefault;
    if (effDefault.isEmpty())
      return userVal;
    return userVal + "|" + effDefault;
  }

  // remove "|", "=", ":", and newline (just in case they appear in board names, keys, or values)
  private static String escape(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  // add back "|", "=", ":", and newline (if they appeared in board names, keys, or values)
  private static String unescape(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }

  static String encode(LinkedHashMap<String, LinkedHashMap<String, String>> prefs) {
    // returns "board:key=val:key=val:...|board:key=val:key=val:..."
    String s = "";
    for (Map.Entry<String, LinkedHashMap<String, String>> e : prefs.entrySet()) {
      String board = e.getKey();
      LinkedHashMap<String, String> p = e.getValue();
      if (!s.isEmpty())
        s += "|";
      s += escape(board);
      for (Map.Entry<String, String> kv : p.entrySet())
        s += ":" + escape(kv.getKey()) + "=" + escape(kv.getValue());
    }
    return s;
  }

  @Override
  public String parse(Element elt) {
    String s = "";
    for (Element boardElt : XmlIterator.forChildElements(elt, "board")) {
      String board = boardElt.getAttribute("name");
      if (board == null || board.isEmpty())
        continue;
      if (!s.isEmpty())
        s += "|";
      s += escape(board);
      NamedNodeMap attrs = boardElt.getAttributes();
      for (int i = 0; i < attrs.getLength(); i++) {
        Node a = attrs.item(i);
        if (a.getNodeName().equalsIgnoreCase("name"))
          continue;
        s += ":" + escape(a.getNodeName()) + "=" + escape(a.getNodeValue());
      }
    }
    return s;
  }

  @Override
  public void writeTo(StringBuilder sb, String indent, String userVal, String effDefault) {
    if (userVal == null || userVal.isEmpty()) {
      sb.append(indent + "<"+subsection+"/>\n");
      return;
    }
    sb.append(indent + "<"+subsection+">\n");
    for (Map.Entry<String, LinkedHashMap<String, String>> e : prefs.entrySet()) {
      String board = e.getKey();
      LinkedHashMap<String, String> p = e.getValue();
      sb.append(indent + "  <board name=\"" + BackingStore.xmlEscapeAttr(board) +"\"");
      for (Map.Entry<String, String> kv : p.entrySet())
        sb.append(" " + kv.getKey() + "=\"" + BackingStore.xmlEscapeAttr(kv.getValue()) + "\"");
      sb.append("/>\n");
    }
    sb.append(indent + "</"+subsection+">\n");
  }

}
