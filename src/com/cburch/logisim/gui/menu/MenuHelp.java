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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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

  private boolean running;
  private HttpServer srv;
  private void loadBroker() {
    if (running)
      return;
    try {
      srv = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10);
      srv.createContext("/", (req) -> handle(req));
      srv.start();
      running = true;
    } catch (IOException e) {
      disableHelp();
      Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"), e);
    }
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
  
  static void send_err(HttpExchange req, int rcode, String msg) {
    try {
      byte[] body = msg.getBytes(StandardCharsets.UTF_8);
      req.sendResponseHeaders(rcode, body.length);
      OutputStream out = req.getResponseBody();
      out.write(body, 0, body.length);
      out.close();
    } catch (Exception e) {
      internal_err(req, e);
    }
  }

  static void handle(HttpExchange req) {
    // The req path will look like "/en/html/guide/about/index.html"
    // This maps to jar resource "/doc/en/html/guide/about/index.html"
    
    if (!req.getRequestMethod().equals("GET")) {
      send_err(req, 405, "Sorry, that method is not allowed.");
      return;
    }
    URI uri = req.getRequestURI();

    String urlpath = uri.getPath();
    if (!urlpath.startsWith("/")) {
      send_err(req, 404, "Missing leading slash");
      return;
    }
    String rsrc = "/doc" + urlpath;
    // If path ends in "/", add "index.html"
    // Otherwise, try adding "/index.html" but fall back on failure.
    InputStream is;
    if (rsrc.endsWith("/")) {
      is = MenuHelp.class.getResourceAsStream(rsrc + "index.html");
    } else {
      is = MenuHelp.class.getResourceAsStream(rsrc + "/index.html");
      if (is == null)
        is = MenuHelp.class.getResourceAsStream(rsrc);
    }
    if (is == null) {
      send_err(req, 404, "Not found");
      return;
    }

    byte[] body;
    try {
      body = is.readAllBytes();
    } catch (IOException e) {
      send_err(req, 500, "Failed to read help resource");
      return;
    }

    try {
      req.sendResponseHeaders(200, body.length);
      OutputStream out = req.getResponseBody();
      out.write(body, 0, body.length);
      out.close();
    } catch (Exception e) {
      internal_err(req, e);
    }
  }

  static boolean warned = false;
  static void internal_err(HttpExchange req, Exception e) {
    if (warned)
      return;
    warned = true;
    SwingUtilities.invokeLater(() ->
        Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"), e));
  }

  private void showHelp(String target) {
    loadBroker();
    String lang = Locale.getDefault().getLanguage();
    if (MenuHelp.class.getResource("/doc/"+lang+"/html/guide/index.html") == null)
      lang = "en";
    String url = "http://" + srv.getAddress() + "/" + lang + "/html/" + target;
    DesktopIntegration.openBrowser(url);
  }
}
