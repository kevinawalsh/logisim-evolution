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

import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import javax.swing.SwingUtilities;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.DesktopIntegration;
import com.cburch.logisim.util.Errors;

public class HelpBroker {

  // A few ports to try; if these fall, we will fallback to 0 (any open port)
  private static final int HELP_HTTP_PORT_NUMBERS[] = { 7400, 7408, 7432, 7486, };

  private static boolean running;
  private static HttpServer srv;
  private static Object lock = new Object(); // protects running and srv

  public static IOException start() {
    synchronized(lock) {
      if (running)
        return null;
      try {
        // try fixed port numbers
        for (int port : HELP_HTTP_PORT_NUMBERS) {
          try {
            srv = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 10);
            break;
          } catch (IOException e) { }
        }
        // fallback to any open port, throws on failure
        if (srv == null)
          srv = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10);

        srv.createContext("/live", (req) -> handleLive(req));
        srv.createContext("/", (req) -> handle(req));
        srv.start();
        running = true;
        return null;
      } catch (IOException e) {
        return e;
      }
    }
  }

  private static void send_err(HttpExchange req, int rcode, String msg) {
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

  private static void handle(HttpExchange req) {
    // The req path will look like "/en/html/guide/about/index.html"
    // This maps to jar resource "/doc/en/html/guide/about/index.html"
    
    if (!req.getRequestMethod().equals("GET")) {
      send_err(req, 405, "Sorry, that method is not allowed.");
      return;
    }
    URI uri = req.getRequestURI();
    DesktopIntegration.browserWasOpened(uri);

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

  private static void handleLive(HttpExchange req) {
    req.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

    if (!req.getRequestMethod().equals("GET")) {
      send_err(req, 405, "Method not allowed.");
      return;
    }

    String raw = req.getRequestURI().getRawQuery();
    if (raw == null || raw.isBlank()) {
      send_err(req, 400, "Missing query");
      return;
    }

    // Parse "file=...&circ=..." from raw query string
    String filename = null, circuitName = null;
    for (String param : raw.split("&")) {
      int eq = param.indexOf('=');
      if (eq < 1) continue;
      String key = param.substring(0, eq);
      String val = java.net.URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);
      if (key.equals("file")) filename = val;
      else if (key.equals("circuit")) circuitName = val;
    }

    if (filename == null) {
      send_err(req, 400, "Expected: ?file=help.circ&circ=Circuit Name");
      return;
    }

    if (filename.contains("..") || filename.startsWith("/")) {
      send_err(req, 400, "Invalid filename");
      return;
    }

    if (!openLive(filename, circuitName)) {
      send_err(req, 404, "Error opening: " + filename + " with circuit '" + (circuitName == null ? "" : circuitName) + "'");
      return;
    }

    send_err(req, 200, "OK");
    DesktopIntegration.requestForeground(1);

  }

  private static HashMap<String, Frame> helpFrames = new HashMap<>();;
  private static HelpProjectWindowListener listener = new HelpProjectWindowListener();

  private static class HelpProjectWindowListener extends WindowAdapter {
    @Override
    public void windowClosed(WindowEvent event) {
      Frame frame = (Frame) event.getSource();
      // Project proj = frame.getProject();
      helpFrames.values().remove(frame);
    }
  }

  public static boolean openLive(String filename, String circuitName) {
    File srcfile = new File(filename);
    String rsrc = "/live/" + filename;
    InputStream is = MenuHelp.class.getResourceAsStream(rsrc);
    if (is == null)
      return false;

    SwingUtilities.invokeLater(() -> {
      try {
        Frame frame = helpFrames.get(filename);
        if (frame == null) {
          Loader loader = new Loader(null);
          LogisimFile.FileWithSimulations file = loader.openLogisimFile(srcfile, is);
          frame = new Frame(new Project(file));
          frame.getProject().setExternalLinksApproved(true); // live example circuits always trusted
          frame.setVisible(true);
          helpFrames.put(filename, frame);
        }
        frame.toFront();
        frame.getCanvas().requestFocus();
        Project proj = frame.getProject();
        proj.getLogisimFile().getLoader().setParent(frame);
        Circuit circ = (circuitName != null && !circuitName.isEmpty())
          ? proj.getLogisimFile().getCircuit(circuitName)
          : proj.getLogisimFile().getMainCircuit();
        if (circ != null)
          proj.setCurrentCircuit(circ);
        else
          Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"));
      } catch (Exception ex) {
        Debug.error("Opening live help: " + filename + " " + circuitName, ex);
        Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"));
      } finally {
        try { is.close(); }
        catch (IOException e) { }
      }
    });
    
    return true;
  }

  private static boolean warned = false; // unprotected, meh
  private static void internal_err(HttpExchange req, Exception e) {
    if (warned)
      return;
    warned = true;
    SwingUtilities.invokeLater(() ->
        Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"), e));
  }

  // Follow a hyperlink from a canvas text component.
  //
  // Supported URL schemes:
  //   "#CircuitName"               — switch to a named circuit in the same project (always trusted)
  //   "logisim:///live/f.circ"     — open a built-in live example circuit (always trusted)
  //   "logisim:///live/f.circ#C"   — same, navigate to circuit C inside
  //                                  (1, 2, or 3 slashes after "logisim:" are all accepted)
  //   "/libs/wiring/pin.html"      — relative path into built-in help docs (always trusted)
  //   "http://..."  "https://..."  — open in browser; requires user confirmation unless
  //   "mailto:..."                   project has already been marked as trusted
  //
  // TODO: future: "logisim://hostname/path#circuit" could download and open a remote project
  // TODO: future: "logisim:///abs/path/to/file.circ#circuit" could open a local .circ file
  //
  // proj may be null (e.g. when called from a built-in help circuit context).
  public static void followLink(String url, Project proj) {
    if (url == null || url.isBlank()) return;

    // "#CircuitName" — switch to a named circuit in the same project.
    if (url.startsWith("#")) {
      if (proj == null) return;
      String circName = url.substring(1).trim();
      if (circName.isEmpty()) return;
      Circuit circ = proj.getLogisimFile().getCircuit(circName);
      if (circ != null)
        SwingUtilities.invokeLater(() -> proj.setCurrentCircuit(circ));
      return;
    }

    // "logisim:" scheme — built-in live circuits (always trusted, no confirmation).
    if (url.startsWith("logisim:")) {
      // Strip scheme and any number of leading slashes to get a plain path.
      // Accepts logisim:/live/..., logisim://live/..., logisim:///live/...
      String rest = url.substring("logisim:".length());
      while (rest.startsWith("/")) rest = rest.substring(1);
      // rest is now like "live/file.circ" or "live/file.circ#CircuitName"
      int hashIdx = rest.indexOf('#');
      String circuitName = hashIdx >= 0 ? rest.substring(hashIdx + 1).trim() : null;
      String path = hashIdx >= 0 ? rest.substring(0, hashIdx) : rest;
      if (circuitName != null && circuitName.isBlank()) circuitName = null;
      if (path.startsWith("live/")) {
        openLive(path.substring("live/".length()), circuitName);
      }
      // Other logisim: paths are silently ignored pending future extension.
      return;
    }

    // Relative path — link to a built-in help doc page (always trusted, no confirmation).
    // e.g. "/libs/wiring/pin.html" opens the corresponding page from the built-in docs.
    if (url.startsWith("/") && !url.startsWith("//")) {
      if (url.startsWith("//")) url = url.substring(2);
      else if (url.startsWith("/")) url = url.substring(1);
      IOException e = showHelp(url);
      if (e != null)
        Errors.title(S.get("helpNotFoundTitle")).show(S.get("helpNotFoundError"), e);
      return;
    }

    // External links — require user confirmation unless project is already trusted.
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
      if (proj != null && !proj.isExternalLinksApproved()) {
        Window parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JCheckBox trustBox = new JCheckBox(S.get("helpOptionAlwaysTrustExternalLinks"));
        boolean isMail = url.startsWith("mailto:");
        Object[] content = {
          isMail ? S.get("helpConfirmOpenEmail") : S.get("helpConfirmOpenUrl"),
          url,
          trustBox,
        };
        int result = JOptionPane.showOptionDialog(
            parent, content,
            isMail ? S.get("helpConfirmOpenEmailTitle") : S.get("helpConfirmOpenUrlTitle"),
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            new Object[] { S.get("helpConfirmOpen"), S.get("helpConfirmCancel") },
            S.get("helpConfirmCancel"));
        if (result != 0) return; // user cancelled
        if (trustBox.isSelected())
          proj.setExternalLinksApproved(true);
      }
      DesktopIntegration.openBrowser(url);
      return;
    }

    // Unknown scheme — silently ignored.
  }

  public static IOException showHelp(String target) {
    IOException e = start();
    if (e != null)
      return e;
    String lang = Locale.getDefault().getLanguage();
    if (MenuHelp.class.getResource("/doc/"+lang+"/html/guide/index.html") == null)
      lang = "en";
    String host = srv.getAddress().getAddress().getHostAddress() + ":" + srv.getAddress().getPort();
    String url = "http://" + host + "/" + lang + "/html/" + target;
    DesktopIntegration.openBrowser(url);
    return null;
  }

}
