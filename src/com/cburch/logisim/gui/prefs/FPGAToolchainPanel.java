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

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.PathSettingUI;
import com.cburch.logisim.util.TableLayout;

public class FPGAToolchainPanel extends SettingsPanel {
  private static final long serialVersionUID = 1L;
  
  final String title;

  public FPGAToolchainPanel(SettingsFrame window, String title) {
    super(window);
    this.title = title;
    setLayout(new TableLayout(1));
  }

  // protected void addPathOption(PrefMonitor<String> pref,
  //     String heading, String[] expectedPrograms, boolean requireAll) {
  //   PathSettingUI ui = new PathSettingUI(getSettingsFrame(),
  //       pref,
  //       S.unlocalized(heading),
  //       S.unlocalized("Browse..."),
  //       S.unlocalized("Select Toolchain Directory"),
  //       S.unlocalized("Select"),
  //       (f) -> setIfPresent(f, pref, expectedPrograms, requireAll));
  //   ui.setLeftMargin(30);
  //   ui.setDirOnly(true);
  //   add(ui);
  // }
  
  protected void addDirOption(PrefMonitor<String> pref, String heading, Predicate<String> validator) {
    PathSettingUI ui = new PathSettingUI(getSettingsFrame(),
        pref,
        S.unlocalized(heading),
        S.unlocalized("Browse..."),
        S.unlocalized("Select " + title + " Directory"),
        S.unlocalized("Select"),
        (path) -> pref.set(path));
    ui.setLeftMargin(30);
    ui.setDirOnly(true);
    ui.setValidator(validator);
    add(ui);
  }

  protected void addFileOption(PrefMonitor<String> pref, String heading, Predicate<String> validator) {
    PathSettingUI ui = new PathSettingUI(getSettingsFrame(),
        pref,
        S.unlocalized(heading),
        S.unlocalized("Browse..."),
        S.unlocalized("Select " + title + " Executable"),
        S.unlocalized("Select"),
        (path) -> pref.set(path));
    ui.setLeftMargin(30);
    ui.setDirOnly(false);
    ui.setValidator(validator);
    add(ui);
  }

  protected void addExplanation(String text) {
    JTextArea explanation = new JTextArea();
    explanation.setEditable(false);
    explanation.setOpaque(false);
    explanation.setLineWrap(true);
    explanation.setWrapStyleWord(true);
    explanation.setFont(UIManager.getFont("Label.font"));
    explanation.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    explanation.setText(text);
    add(explanation);
  }

  // private static void setIfPresent(File f, PrefMonitor<String> pref, String expectedFiles[], boolean requireAll) {
  //   try {
  //     String path = normalizePath(f);
  //     if (path.isEmpty()) {
  //       pref.set(path); // allow user to clear the path
  //     } else if (requireAll && allToolsPresent(path, expectedFiles)) {
  //       pref.set(path);
  //     } else if (!requireAll && someToolsPresent(path, expectedFiles)) {
  //       pref.set(path);
  //     } else {
  //       Errors.title("Invalid Selection").show("Invalid selection.\n" +
  //           "Please select a directory containing " + pretty(expectedFiles, requireAll ? "and" : "or") + ".");
  //     }
  //   } catch (IOException e) {
  //       Errors.title("Invalid Selection").show("Error accessing directory.\n" +
  //           "Please select a directory containing " + pretty(expectedFiles, requireAll ? "and" : "or") + ".", e);
  //   }
  // }


  @Override
  public String getHelpText() { return title + " settings"; }

  @Override
  public String getTitle() { return title; }

  @Override
  public void localeChanged() { /* path.localeChanged(); */ }

	protected static String pretty(String[] names, String conjunction) {
		String s = names[0];
		for (int i = 1; i < names.length; i++) {
			s += (i == names.length - 1 ? " "+conjunction+" " : ", "); 
			s += names[i];
		}
		return s;
	}

  protected static boolean allToolsPresent(String path, String[] progNames) {
    for (String prog : progNames) {
      if (!new File(path + File.separator + prog).exists())
        return false;
    }
    return true;
  }

  protected static boolean someToolsPresent(String path, String[] progNames) {
    for (String prog : progNames) {
      if (new File(path + File.separator + prog).exists())
        return true;
    }
    return false;
  }

  protected static boolean isExecutableScript(String path) {
    File f = new File(path);
    return f.exists() && !f.isDirectory() && f.canExecute();
  }
}
