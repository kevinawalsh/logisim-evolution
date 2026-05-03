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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;

import com.cburch.logisim.file.XmlIterator;

public class FPGABoardlistPref implements SettingsStore.Item {

  // "external-boards" subsection of "fpga" section of settings.xml
 
  private final String section; //  "fpga"
  private final String subsection; // "external-boards"

  private ArrayList<String> paths = new ArrayList<>();

  public FPGABoardlistPref(String section, String subsection) {
    this.section = section;
    this.subsection = subsection;

    // Register so we appear in settings.xml
    SettingsStore.registerSubsection(section, subsection, this);

    // React to changes pushed by SettingsStore (e.g. from load, reload, or clear)
    SettingsStore.addReloadListener(() -> setFromStore());
  }

  public List<String> get() { return Collections.unmodifiableList(paths); }

  public void add(String path) {
    if (paths.contains(path))
      return;
    paths.add(0, path);
    SettingsStore.put(section, subsection, encode());
    AppPreferences.fireFPGAChangeEvent();
  }

  private void setFromStore() {
    String s = SettingsStore.getEffective(section, subsection);
    paths.clear();
    paths.addAll(List.of(s.split("\\|")));
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

  @Override
  public String parse(Element elt) {
    // we are assuming file names do not contain '|', which seems reasoanble
    String s = "";
    for (Element file : XmlIterator.forChildElements(elt, "file")) {
      String v = file.getAttribute("value");
      if (v != null && !v.isEmpty())
        s = s.isEmpty() ? v : s + "|" + v;
    }
    return s;
  }

  private String encode() {
    // we are assuming file names do not contain '|', which seems reasoanble
    String s = "";
    for (String v : paths) {
      if (v != null && !v.isEmpty())
        s = s.isEmpty() ? v : s + "|" + v;
    }
    return s;
  }

  @Override
  public void writeTo(StringBuilder sb, String indent, String userVal, String effDefault) {
    if (userVal == null || userVal.isEmpty()) {
      sb.append(indent + "<"+subsection+"/>\n");
      return;
    }
    String userPaths[] = userVal.split(":");
    sb.append(indent + "<"+subsection+">\n");
    for (String path : userPaths)
      sb.append(indent + "  <file value=\"" + BackingStore.xmlEscapeAttr(path) + "\"/>\n");
    sb.append(indent + "</"+subsection+">\n");
  }

}
