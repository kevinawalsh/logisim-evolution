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

package com.cburch.logisim.gui.prefs;
import static com.cburch.logisim.gui.prefs.Strings.S;

import java.util.Map;

import com.bfh.logisim.download.Altera;
import com.cburch.logisim.prefs.AppPreferences;

public class AlteraOptions extends FPGAToolchainPanel {

  private PrefRadioList<Boolean> altera64bit;
  private PrefRadioList<String> alteraFormat;
 
  public AlteraOptions(SettingsFrame window) {
    super(window, "Altera");

    addExplanation(
      "Altera Quartus II is a legacy toolchain for older Altera FPGAs, such as "
      + "Spartan-3/6, Virtex-4/5/6, and CPLDs. Altera/Intel Quartus Prime is the "
      + "current toolchain for more recent devices. Logisim-HC's integration was "
      + "designed for Quartus II, the last known version of which is 14.7 "
      + "released in 2013, available only for Windows and Linux, though some "
      + "devices require an even older version 13.0sp1.  Logisim-HC might also "
      + "work with the Quartus Prime Lite (free) or other Prime editions.");

    addDirOption(AppPreferences.ALTERA_PATH,
        "Altera Quartus II tool path (directory, url, or script):",
        (path) -> validateAlteraPath(path));

    altera64bit = new PrefRadioList<>(Boolean.class, AppPreferences.ALTERA_64BIT, Map.of(
            Boolean.FALSE, S.unlocalized("32-bit (faster, less memory, small projects)"),
            Boolean.TRUE, S.unlocalized("64-bit (slower, more memory, large projects)")));
    add(altera64bit.createJPanel());

    alteraFormat =new PrefRadioList<>(String.class, AppPreferences.ALTERA_FORMAT, Map.of(
            "svf", S.unlocalized("svf (Serial Vector Format)"),
            "rbf", S.unlocalized("rbf (Raw Binary File)")));
    add(alteraFormat.createJPanel());

    addExplanation("Use 'Browse...' to select the directory where "
        + pretty(Altera.ALTERA_PROGRAMS, "and") + " are installed. "
        + "Or, edit the text directly to specify either that directory, a "
        + "trusted URL, or the path to a single-file script that will be called on "
        + "to do each stage of the synthesis and downloading.");
  }

  boolean validateAlteraPath(String path) {
    return path.isEmpty()
      || allToolsPresent(path, Altera.ALTERA_PROGRAMS)
      || isExecutableScript(path)
      || path.toLowerCase().startsWith("https://")
      || path.toLowerCase().startsWith("http://");
  }

}
