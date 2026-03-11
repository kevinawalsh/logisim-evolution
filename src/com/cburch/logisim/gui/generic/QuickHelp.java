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

package com.cburch.logisim.gui.generic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Loads quick-help HTML fragments from classpath resources under doc/{lang}/quickhelp/.
 *
 * Resource files mirror the Java package structure with "com.cburch.logisim." stripped:
 *   com.cburch.logisim.std.wiring.Pin  →  doc/{lang}/quickhelp/std/wiring/Pin.html
 *   com.cburch.logisim.tools.WiringTool → doc/{lang}/quickhelp/tools/WiringTool.html
 *
 * The current locale language is tried first; if the resource doesn't exist for that
 * language, "en" is used as the fallback — the same pattern used by HelpBroker.showHelp().
 *
 * Each resource file contains a body-only HTML fragment (no html/head/body tags).
 * Tools or factories that need dynamic content can override getQuickHelp() /
 * getFeature(QUICK_HELP, attrs) in Java and ignore these resource files.
 */
public class QuickHelp {

  private static final String PREFIX = "com.cburch.logisim.";

  /** Load the HTML fragment for the given class, or null if not found. */
  public static String load(Class<?> cls) {
    return load(cls, "");
  }

  /** Load the HTML fragment for the given class with a suffix, or null if not found.
   *  E.g. load(Text.class, "-plain") → doc/{lang}/quickhelp/std/decor/Text-plain.html */
  public static String load(Class<?> cls, String suffix) {
    String pkg = cls.getPackage().getName();
    String sub = pkg.startsWith(PREFIX) ? pkg.substring(PREFIX.length()) : pkg;
    String rel = sub.replace('.', '/') + "/" + cls.getSimpleName() + suffix + ".html";
    String lang = Locale.getDefault().getLanguage();
    String content = loadPath("/doc/" + lang + "/quickhelp/" + rel);
    if (content == null && !"en".equals(lang))
      content = loadPath("/doc/en/quickhelp/" + rel);
    return content;
  }

  private static String loadPath(String path) {
    try (InputStream is = QuickHelp.class.getResourceAsStream(path)) {
      if (is == null) return null;
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

}
