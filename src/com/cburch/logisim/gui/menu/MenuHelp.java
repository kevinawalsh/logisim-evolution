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
package com.cburch.logisim.gui.menu;
import static com.cburch.logisim.gui.menu.Strings.S;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import com.cburch.logisim.Main;
import com.cburch.logisim.gui.start.About;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.DesktopIntegration;
import com.cburch.logisim.util.Errors;

public class MenuHelp extends JMenu implements ActionListener {

  private static final long serialVersionUID = 1L;

  private LogisimMenuBar menubar;
  private JMenuItem tutorial = new JMenuItem();
  private JMenuItem guide = new JMenuItem();
  private JMenuItem library = new JMenuItem();
  private JMenuItem report = new JMenuItem();
  private JMenuItem email = new JMenuItem();
  private JMenuItem crashlog = new JMenuItem();
  private JMenuItem logs = new JMenuItem();
  private JMenuItem about = new JMenuItem();

  public MenuHelp(LogisimMenuBar menubar) {
    this.menubar = menubar;

    tutorial.addActionListener(this);
    guide.addActionListener(this);
    library.addActionListener(this);
    report.addActionListener(this);
    email.addActionListener(this);
    crashlog.addActionListener(this);
    logs.addActionListener(this);
    about.addActionListener(this);

    add(tutorial);
    add(guide);
    add(library);
    addSeparator();
    add(report);
    add(email);
    add(crashlog);
    add(logs);
    if (!DesktopIntegration.AboutMenuAutomaticallyPresent) {
      addSeparator();
      add(about);
    }
    report.setEnabled(!Main.CRASH_CONTACT_LINK.isBlank());
    email.setEnabled(!Main.CRASH_CONTACT_EMAIL.isBlank());
    crashlog.setEnabled(Debug.crashLogPath != null);
    logs.setEnabled(Debug.PERSIST_DIR != null);
  }

  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    if (src == guide) {
      showHelp("guide/");
    } else if (src == tutorial) {
      showHelp("guide/tutorial/");
    } else if (src == library) {
      showHelp("libs/");
    } else if (src == report) {
      DesktopIntegration.openBrowser(Main.CRASH_CONTACT_LINK);
    } else if (src == email) {
      DesktopIntegration.openMail(Main.CRASH_CONTACT_EMAIL, "Logisim feedback");
    } else if (src == crashlog) {
      Debug.openCrashLog();
    } else if (src == logs) {
      Debug.openCrashLogFolder();
    } else if (src == about) {
      About.showAboutDialog(menubar.getParentFrame());
    }
  }

  private void disableHelp() {
    guide.setEnabled(false);
    tutorial.setEnabled(false);
    library.setEnabled(false);
  }

  public void localeChanged() {
    this.setText(S.get("helpMenu"));
    tutorial.setText(S.get("helpTutorialItem"));
    guide.setText(S.get("helpGuideItem"));
    library.setText(S.get("helpLibraryItem"));
    report.setText(S.get("helpReportItem"));
    email.setText(S.get("helpEmailItem"));
    crashlog.setText(S.get("helpCrashlogItem"));
    logs.setText(S.get("helpLogsItem"));
    about.setText(S.get("helpAboutItem"));
  }
  
  private void showHelp(String target) {
    IOException e = HelpBroker.showHelp(target);
    if (e != null) {
      disableHelp();
      Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"), e);
    }
  }
}
