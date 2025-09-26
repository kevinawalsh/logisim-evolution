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

package com.cburch.logisim;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.swing.JOptionPane;

import com.cburch.logisim.gui.start.Startup;


public class Main {
  public static void main(String[] args) throws Exception {
    Startup startup = Startup.parseArgs(args);
    if (startup == null)
      System.exit(0);
    try {
      startup.run();
    } catch (Throwable e) {
      if (headless) {
        System.err.println(e);
        e.printStackTrace(System.err);
      } else {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(new PrintWriter(result));
        JOptionPane.showMessageDialog(null, result.toString());
      }
      System.exit(-1);
    }
  }

  public static boolean headless = false;
  public static boolean MacOS = false;
  public static boolean MSWindows = false;
  public static boolean Linux = false;

  private static String getFromFile(String filename, String defaultValue) {
    try {
      InputStream in = Main.class.getResourceAsStream(filename);
      if (in != null)
        return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
    }
    return defaultValue;
  }

  private static String getFromFile(String filename, int linenum, String defaultValue) {
    String s = getFromFile(filename, null);
    if (s == null)
      return defaultValue;
    String lines[] = s.split("\\r?\\n");
    return (lines == null || lines.length < linenum) ? defaultValue : lines[linenum].trim();
  }

  private static String getCurrentVersion() {
    String verFromJar = Main.class.getPackage().getImplementationVersion();
    if (verFromJar != null)
      return verFromJar;
    String ver = getFromFile("/version.txt", "unknown");
    String git = getFromFile("/git-version-hash.txt", "");
    String verFromFile = !git.isBlank() ? ver + " ("+git+")" : ver;
    return verFromFile;
  }

  private static int getCurrentCopyrightYear() {
    String s = getFromFile("/copyright-year.txt", "2025");
    try {
      return Math.max(2025, Integer.parseInt(s));
    } catch (Throwable t) {
      return 2025;
    }
  }

  public static final LogisimVersion VERSION = LogisimVersion.parse(getCurrentVersion());
  public static final String VERSION_NAME = VERSION.toString();
  public static final int COPYRIGHT_YEAR = getCurrentCopyrightYear();;
  public static final String CRASH_CONTACT_LINK = getFromFile("/crash-contact.txt", 0, "");
  public static final String CRASH_CONTACT_EMAIL = getFromFile("/crash-contact.txt", 1, "");

}
