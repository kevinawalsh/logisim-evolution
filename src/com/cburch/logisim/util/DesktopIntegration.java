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

package com.cburch.logisim.util;
import static com.cburch.logisim.util.Strings.S;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.function.BooleanSupplier;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.desktop.QuitStrategy;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import com.cburch.logisim.Main;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.gui.start.About;
import com.cburch.logisim.gui.start.Startup;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.util.Debug;

public class DesktopIntegration {
 
  public static boolean SupportsDesktop = false;

  public static boolean SupportsSuddenTerminationHandling = false;
  public static boolean AboutMenuAutomaticallyPresent = false;
  public static boolean PreferencesMenuAutomaticallyPresent = false;
  public static boolean QuitMenuAutomaticallyPresent = false;
  public static boolean AlwaysUseScrollbars = false;
  public static boolean HasWindowlessMenubar = false;
  public static boolean CanRequestForeground = false;

  private static Desktop desktop;

  // Tracks browser popups that are pending (within delay window) or shown,
  // keyed by the URL/URI string, so browserWasOpened() can cancel them.
  private static final HashMap<String, FallbackPopup> activeBrowserPopups = new HashMap<>();
  private static final int BROWSER_OPEN_DELAY_MS = 3000;

  // Called once, during startup, before splash screen.
  public static void init(Startup startup) {

    AlwaysUseScrollbars = Main.MacOS;

    try {
      if (!Desktop.isDesktopSupported()) {
        Debug.println(1, "Note [0]: no desktop support");
        return;
      }
      desktop = Desktop.getDesktop();
      if (desktop == null) {
        Debug.println(1, "Note [0]: no desktop support, despite claims otherwise");
        return;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      Debug.println(1, "Note [0]: no desktop support, it fails");
      return;
    }

    SupportsDesktop = true; // apparently...

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_SUDDEN_TERMINATION)) {
        desktop.enableSuddenTermination();
        SupportsSuddenTerminationHandling = true;
        return true;
      } else return false;
    }, "Note [1]: no support to prevent sudden termination");

    if (SupportsSuddenTerminationHandling)
        Debug.parkCanary();

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_QUIT_STRATEGY)
          && desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        QuitMenuAutomaticallyPresent = true;
        // desktop.setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
        desktop.setQuitStrategy(QuitStrategy.NORMAL_EXIT);
        desktop.setQuitHandler((e, response) ->
            {
              boolean ok = ProjectActions.doQuit();
              if (ok) response.performQuit(); // never reached: doQuit calls System.exit() on success.
              else response.cancelQuit();
            });
        return true;
      } else return false;
    }, "Note [2]: no support to control quit strategy and handler");

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
        desktop.setOpenFileHandler(e -> { 
          for (File file : e.getFiles())
            startup.doOpenFile(file);
        });
        return true;
      } else return false;
    }, "Note [3]: no support for desktop file opening");

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_PRINT_FILE)) {
        desktop.setPrintFileHandler(e -> { 
          for (File file : e.getFiles())
            startup.doPrintFile(file);
        });
        return true;
      } else return false;
    }, "Note [4]: no support for desktop file printing");

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
        desktop.setPreferencesHandler(e -> SettingsFrame.showAppSettings());
        PreferencesMenuAutomaticallyPresent = true;
        return true;
      } else return false;
    }, "Note [5]: no support for desktop preferences");

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
        desktop.setAboutHandler(e -> About.showAboutDialog(null));
        AboutMenuAutomaticallyPresent = true;
        return true;
      } else return false;
    }, "Note [6]: no support for desktop about screen");

    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
        CanRequestForeground = true;
        return true;
      } else return false;
    }, "Note [7]: no support for requesting foreground focus");

  }
  
  // Called once, during startup, after splash screen.
  public static void installMenubar(LogisimMenuBar menubar) {
    if (desktop == null)
      return;
    tryOrPrint(() -> {
      if (desktop.isSupported(Desktop.Action.APP_MENU_BAR)) {
        desktop.setDefaultMenuBar(menubar);
        HasWindowlessMenubar = true;
        return true;
      } else return false;
    }, "Note [7]: no desktop menubar support");
  }

  private static boolean tryOrPrint(BooleanSupplier func, String failmsg) {
    try {
      if (func.getAsBoolean())
        return true;
    } catch (Exception ex) {
      ex.printStackTrace();
      System.err.println(failmsg + ", it failed");
      return false;
    }
    Debug.println(1, failmsg);
    return false;
  }

  private static boolean TerminationAllowed = true;
  private static Object TerminationLock = new Object();
  
  // Returns true if desired state seems to be achieved,
  // false on failure. Doesn't even attempt it if Desktop
  // doesn't appear to support this. 
  public static boolean setSuddenTerminationAllowed(boolean allow) {
    if (!SupportsSuddenTerminationHandling)
      return false;
    synchronized (TerminationLock) {
      if (TerminationAllowed == allow)
        return true;
      try {
        Desktop desktop = Desktop.getDesktop();
        if (allow && desktop != null) {
          Debug.parkCanary();
          desktop.enableSuddenTermination();
        } else if (desktop != null) {
          desktop.disableSuddenTermination();
          Debug.unparkCanary();
        }
        TerminationAllowed = allow;
        return true;
      } catch (Exception ex) {
        ex.printStackTrace();
        return false;
      }
    }
  }

  // Returns true if browser seems to have been opened,
  // false on failure. Doesn't even attempt it if Desktop
  // doesn't appear to support this. 
  // The link can be "http[s]://..." or "mailto://..." link.
  private static boolean tryOpenBrowser(Object link) {
    try {
      URI uri;
      if (link instanceof URI)
        uri = (URI)link;
      else if (link instanceof URL)
        uri = ((URL)link).toURI();
      else
        uri = new URI(link.toString());
      if (uri == null)
        return false;
      Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
      if (desktop == null)
        return false;
      if ("mailto".equalsIgnoreCase(uri.getScheme())
          && desktop.isSupported(Desktop.Action.MAIL)) {
        // System.out.println("Opening with desktop mail: " + uri);
        desktop.mail(uri);
        return true; // hope for the best
      } else if (desktop.isSupported(Desktop.Action.BROWSE)) {
        // System.out.println("Opening with desktop browser: " + uri);
        desktop.browse(uri);
        return true; // hope for the best
      } else {
        return false;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public static void browserWasOpened(URI uri) {
    cancelBrowserPopup(uri.toString());
  }

  public static void browserWasOpened(URL url) {
    cancelBrowserPopup(url.toString());
  }

  // Strips "http://host:port" or "https://host:port" prefix from a URL string,
  // leaving just the path (and query/fragment). Returns the original string if
  // no such prefix is present.
  private static String stripUrlPrefix(String key) {
    if (key == null) return key;
    int slashSlash = key.indexOf("://");
    if (slashSlash < 0) return key;
    int pathStart = key.indexOf('/', slashSlash + 3);
    if (pathStart < 0) return key;
    return key.substring(pathStart);
  }

  // Cancels a pending or shown browser fallback popup for the given URL key.
  // Safe to call from any thread.
  private static void cancelBrowserPopup(String key) {
    final String normKey = stripUrlPrefix(key);
    Runnable task = () -> {
      FallbackPopup popup = activeBrowserPopups.remove(normKey);
      if (popup != null) popup.cancel();
    };
    if (SwingUtilities.isEventDispatchThread())
      task.run();
    else
      SwingUtilities.invokeLater(task);
  }

  public static void openBrowser(URI uri) {
    if (uri == null)
      return;
    boolean ok = tryOpenBrowser(uri);
    showBrowserFallback(ok, uri.toString(),
        S.get("desktopFallbackOpenBrowserTitle"),
        S.get("desktopFallbackOpenBrowserMessage"),
        S.get("desktopFallbackOpenBrowserFailed"),
        uri.toString());
  }

  public static void openBrowser(URL url) {
    if (url == null)
      return;
    boolean ok = tryOpenBrowser(url);
    showBrowserFallback(ok, url.toString(),
        S.get("desktopFallbackOpenBrowserTitle"),
        S.get("desktopFallbackOpenBrowserMessage"),
        S.get("desktopFallbackOpenBrowserFailed"),
        url.toString());
  }

  public static void openBrowser(String link) {
    if (link == null || link.isBlank())
      return;
    boolean ok = tryOpenBrowser(link);
    showBrowserFallback(ok, link,
        S.get("desktopFallbackOpenBrowserTitle"),
        S.get("desktopFallbackOpenBrowserMessage"),
        S.get("desktopFallbackOpenBrowserFailed"),
        link);
  }



  // Given email like "Foo Bar <foo@example.com>" or "foo@example.com",
  // returns just the email part, "foo@example.com"
  public static String extractEmail(String email) {
    int i = email.indexOf('<');
    int j = email.lastIndexOf('>');
    if (0 <= i && i < j)
      email = email.substring(i+1, j);
    return email.trim();
  }

  // Given email like "Foo Bar <foo@example.com>" or "foo@example.com",
  // returns "mailto:foo@example.com?subject=Bar%20Baz".
  // This nly escapes spaces in the subject.
  public static String formatMailto(String email, String subject) {
    email = extractEmail(email);
    if (subject == null || subject.isBlank())
      return "mailto:"+email;
    else
      return "mailto:"+email+"?subject=" + subject.replace(" ", "%20");
  }

  // Returns true if mail seems to have been opened,
  // false on failure. Doesn't even attempt it if Desktop
  // doesn't appear to support this. 
  // The email can be:
  //    Foo Bar <foo@example.com>
  //    foo@example.com
  private static boolean tryOpenMail(String email, String subject) {
    return tryOpenBrowser(formatMailto(email, subject));
  }

  public static void openMail(String email, String subject) {
    if (email == null || email.isBlank())
      return;
    boolean ok = tryOpenMail(email, subject);
    showFallback(ok, 
        S.get("desktopFallbackOpenMailTitle"),
        S.get("desktopFallbackOpenMailMessage"),
        S.get("desktopFallbackOpenMailFailed"),
        email.toString());
  }

  // Returns true if folder seems to have been opened,
  // false on failure. Doesn't even attempt it if Desktop
  // doesn't appear to support this. 
  private static boolean tryOpenFolder(Path path) {
    try {
      Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
      if (desktop == null)
        return false;
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        // System.out.println("Opening folder with desktop: " + path);
        desktop.open(path.toFile());
        return true;
      } else {
        return false;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public static void openFolder(Path path) {
    if (path == null)
      return;
    boolean ok = tryOpenFolder(path);
    showFallback(ok, 
        S.get("desktopFallbackOpenFolderTitle"),
        S.get("desktopFallbackOpenFolderMessage"),
        S.get("desktopFallbackOpenFolderFailed"),
        path.toString());
  }

  // Returns true if text file seems to have been opened,
  // false on failure. Doesn't even attempt it if Desktop
  // doesn't appear to support this. 
  private static boolean tryOpenTextfile(Path path) {
    try {
      Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
      if (desktop == null)
        return false;
      if (desktop.isSupported(Desktop.Action.EDIT)) {
        // System.out.println("Opening text file with desktop editor: " + path);
        desktop.edit(path.toFile());
        return true;
      } else if (desktop.isSupported(Desktop.Action.OPEN)) {
        // System.out.println("Opening text file with desktop: " + path);
        desktop.open(path.toFile());
        return true;
      } else {
        return false;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public static void openTextfile(Path path) {
    if (path == null)
      return;
    boolean ok = tryOpenTextfile(path);
    showFallback(ok, 
        S.get("desktopFallbackOpenFileTitle"),
        S.get("desktopFallbackOpenFileMessage"),
        S.get("desktopFallbackOpenFileFailed"),
        path.toString());
  }

  // Like showFallback, but when ok=true the popup is delayed by BROWSER_OPEN_DELAY_MS.
  // During that window (and while the popup is visible), a browserWasOpened() callback
  // can cancel/close it via the activeBrowserPopups map.
  private static void showBrowserFallback(boolean ok, String key, String title,
      String okText, String failText, String payload) {
    final String normKey = stripUrlPrefix(key);
    Runnable task = () -> {
      if (ok) {
        FallbackPopup popup = new FallbackPopup(title, okText, payload);
        activeBrowserPopups.put(normKey, popup);
        popup.addWindowListener(new WindowAdapter() {
          @Override public void windowClosed(WindowEvent e) {
            activeBrowserPopups.remove(normKey, popup);
          }
        });
        Timer delay = new Timer(BROWSER_OPEN_DELAY_MS, e -> popup.showBriefly());
        delay.setRepeats(false);
        delay.start();
      } else {
        FallbackPopup popup = new FallbackPopup(title, failText, payload);
        popup.showForever();
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      try { SwingUtilities.invokeLater(task); }
      catch (Exception ignored) { }
    } else {
      task.run();
    }
  }

  private static void showFallback(boolean ok, String title,
      String okText, String failText, String payload) {
    Runnable task = () -> {
      if (ok) {
        FallbackPopup fallback = new FallbackPopup(title, okText, payload);
        fallback.showBriefly();
      } else {
        FallbackPopup fallback = new FallbackPopup(title, failText, payload);
        fallback.showForever();
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      try { SwingUtilities.invokeLater(task); }
      catch (Exception ignored) { }
    } else {
      task.run();
    }
  }

  static class FallbackPopup extends JDialog {

    int timeout;
    JButton dismiss;
    Window parent;
    boolean cancelled = false;

    void cancel() {
      cancelled = true;
      dispose();
    }

    // title is like "Opening Browser..." or "Opening Mail..."
    // text is like "If your browser doesn't open, please use this link instead:"
    // or "If your email app doesn't open, please use this email instead:"
    // payload is the link, or email address, etc.
    public FallbackPopup(String title, String text, String payload) {

      super(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(),
          title, Dialog.ModalityType.MODELESS);

      parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setAlwaysOnTop(true);

      JLabel textLabel = new JLabel("<html>"+text+"</html>");

      JTextField payloadField = new JTextField(payload);
      payloadField.setEditable(false);
      payloadField.setCaretPosition(0);

      JButton copy = new JButton(S.get("desktopFallbackCopy"));
      copy.addActionListener(e -> {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(payload), null);
        payloadField.requestFocusInWindow();
        payloadField.selectAll();
      });

      dismiss = new JButton(S.get("desktopFallbackDismiss"));
      dismiss.addActionListener(e -> dispose());

      JPanel center = new JPanel(new BorderLayout(8, 0));
      center.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      center.add(payloadField, BorderLayout.CENTER);
      center.add(copy, BorderLayout.EAST);

      JPanel content = new JPanel(new BorderLayout(10,10));
      content.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
      content.add(textLabel, BorderLayout.NORTH);
      content.add(center, BorderLayout.CENTER);

      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
      buttons.add(dismiss);
      content.add(buttons, BorderLayout.SOUTH);

      setContentPane(content);
    }

    @Override
    public void pack() {
      super.pack();
      while (parent != null && !parent.isShowing())
        parent = parent.getOwner();
      setLocationRelativeTo(parent);
    }

    void showForever() {
      if (cancelled) return;
      timeout = -1; // not used
      pack();
      setVisible(true);
    }

    AWTEventListener guard;
    void showBriefly() {
      if (cancelled) return;
      timeout = 10;

      final String base = S.get("desktopFallbackDismiss");
      dismiss.setText(base + " (" + timeout + " s)");
      Timer autoClose = new Timer(1000, e -> {
        timeout--;
        if (timeout <= 0) {
          ((Timer)e.getSource()).stop();
          FallbackPopup.this.dispose();
        } else {
          dismiss.setText(base + " (" + timeout + " s)");
        }
      });
      autoClose.setInitialDelay(1000);
      autoClose.start();

      Runnable stopCountdown = () -> {
        if (autoClose.isRunning()) {
          autoClose.stop();
          dismiss.setText(base);
        }
      };

      final long armAt = System.nanoTime() + 300_000_000L; // grace
      guard = ev -> {
        if (System.nanoTime() < armAt || !autoClose.isRunning())
          return;
        Object src = ev.getSource();
        if (!(src instanceof Component
              && SwingUtilities.isDescendingFrom((Component)src, this)))
          return;
        int id = ev.getID();
        boolean userMouse = (ev instanceof MouseEvent)
            && (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_CLICKED
                || id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_WHEEL);
        boolean userKey = (ev instanceof KeyEvent) && (id == KeyEvent.KEY_PRESSED);
        boolean userMoveOrResize = (ev instanceof ComponentEvent) &&
            (src == this) &&
            (id == ComponentEvent.COMPONENT_MOVED || id == ComponentEvent.COMPONENT_RESIZED);

        if ((userMouse || userKey || userMoveOrResize)) {
          stopCountdown.run();
          Toolkit.getDefaultToolkit().removeAWTEventListener(guard);
        }
      };

      long mask = AWTEvent.MOUSE_EVENT_MASK
          | AWTEvent.MOUSE_WHEEL_EVENT_MASK
          | AWTEvent.KEY_EVENT_MASK
          | AWTEvent.COMPONENT_EVENT_MASK;
      Toolkit.getDefaultToolkit().addAWTEventListener(guard, mask);
      addWindowListener(new WindowAdapter() {
        @Override public void windowClosed(WindowEvent e) {
          autoClose.stop();
          Toolkit.getDefaultToolkit().removeAWTEventListener(guard);
        }
      });

      pack();
      setVisible(true);
    }
      /*
      Box buttons = Box.createHorizontalBox();
      buttons.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      buttons.add(Box.createHorizontalGlue());
      buttons.add(ok);
      if (withCancel) {
        buttons.add(Box.createHorizontalStrut(10));
        buttons.add(cancel);
      }
      buttons.add(Box.createHorizontalGlue());

      Container pane = super.getContentPane();
      pane.add(contents, BorderLayout.CENTER);
      pane.add(buttons, BorderLayout.SOUTH);

      getRootPane().registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) { setVisible(false); cancelClicked(); dispose(); }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

      addWindowListener(new WindowAdapter() {
        public void windowOpened(WindowEvent e) {
          ok.requestFocus();
          e.getWindow().removeWindowListener(this);
        }
      });
      */
  }

  public static void requestForeground(int count) {
    if (CanRequestForeground)
      desktop.requestForeground(count > 1); // false = bounce once, true = bounce until clicked
  }

}

