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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

import com.cburch.logisim.Main;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;

public class Debug {

  // New debugging and crash support for 5.0.5hc:
  // - Still no metrics, no telemetry, no PII collection, no network activity.
  //   - no activity logging, no keyboard or event logging
  //   - no detailed machine info, no statistics
  // - *Always* tee stdin/stdout to a local crash file, not just with -debug flag.
  //   - Name these by date
  //   - Delete older crash files on startup
  //   - Check for crash file size growth
  // - Expose crash reports in UI.
  //   - menu to open directory of crash files
  //   - menu to clear all crash files
  //   - on startup, check for last run crash, show dialog w/ "copy report" option
  //     and contact info [github? email?]
  // - Install a default thread uncaught-exception handler.
  // - Hook AWT/Swing dispatch queue to handle uncaught UI exceptions.
 
  // PERSIST_DIR holds a few of the most recent crash logs.
  // Mac:
  //   {user.home}/Library/Logs/Logisim/           ~/Library/Logs/Logisim/
  // Windows:
  //   %LOCALAPPDATA%\Logisim\Logs\                C:\Users\%USER%\AppData\Local\Logisim\Logs\
  //   %APPDATA%\Logisim\Logs\                     C:\Users\%USER%\AppData\Logisim\Logs\
  //   {user.home}\AppData\Local\Logisim\Logs      C:\Users\%USER%\AppData\Logisim\Logs\
  // Linux:
  //   ${XDG_STATE_HOME}/logisim/logs/             ~/.local/state/logisim/logs/
  //   {user.home}/.local/state/logisim/logs/      ~/.local/state/logisim/logs/
  // Other:
  //   {user.home}/.logisim-logs/                  ~/.logisim-logs/
  //
  public static Path PERSIST_DIR;
  public static int PERSIST_MAX_FILES = 7;
  
  // TEMP_DIR holds a copy of a new, current crash log as it is generated.
  // Mac:
  //   {java.io.tmpdir}/logisim                    /var/folders/...random-per-user-per-boot.../Logisim
  // Windows:
  //   {java.io.tmpdir}/logisim                    C:\Users\%USER%\AppData\Local\Temp\Logisim
  // Linux:
  //   ${XDG_RUNTIME_DIR}/logisim/                 /run/user/${UID}/logisim
  //   {java.io.tmpdir}/logisim-{user.name}        /tmp/logisim-${USER}
  // Other:
  //   {java.io.tmpdir}/logisim-{user.name}        /tmp/logisim-${USER}
  //
  private static Path TEMP_DIR;

  private static final String timestamp = LocalDateTime.now().format(
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
 
  private static final long MAX_CRASH_SIZE = 20*1024;
  private static final long CUT_CRASH_SIZE = 10*1024;
  private static OutputStream crashLogStream;
  public static Path crashLogPath; // this is within PERSIST_DIR
  public static Path crashLogUncutPath; // this is within TEMP_DIR
  private static long crashLogSize;
  private static long crashLogUncutSize;

  private static boolean initialized;
  
  public static void init() {
    if (initialized)
      return;
    setupCrashLogFolders();
    installFileLog();
    installUncaughtHandler();
    AWTGuard.install();
    Thread cleanup = new Thread(() -> shutdownHook(), "cleanup");
    Runtime.getRuntime().addShutdownHook(cleanup);
    String canary = readCanary();
    if (canary != null) {
      String desc = "Did logisim crash last time?\n(timestamp canary="+canary+")";
      if (Main.headless) {
        System.out.println(desc + "\n(see crash logs for details)\n");
      } else {
        surfaceError(desc, null, 0);
      }
    }
    pruneCrashLogs();
    writeCanary();
  }

  private static Path canaryLive()   { return PERSIST_DIR.resolve("current-run-timestamp.txt"); }
  private static Path canaryParked() { return PERSIST_DIR.resolve("current-run-timestamp.parked"); }

  private static void removeCanary() {
    if (PERSIST_DIR == null) return;
    try {
      Files.deleteIfExists(canaryLive());
      Files.deleteIfExists(canaryParked());
    } catch (Exception e) { }
  }

  private static void writeCanary() {
    if (PERSIST_DIR == null) return;
    try {
      String ts = Instant.now().toString() + "\n";
      Files.writeString(canaryLive(), ts, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      Files.deleteIfExists(canaryParked());
    } catch (Exception e) { }
  }

  private static String readCanary() {
    if (PERSIST_DIR == null) return null;
    try {
      return Files.readString(canaryLive(), StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      return null;
    }
  }

  public static void parkCanary() {
    if (PERSIST_DIR == null) return;
    synchronized(lock) {
      if (numLoggedErrors > numSurfacedErrors) {
        return; // do not park if the log contains unsurfaced errors
      }
    }
    try {
      if (Files.exists(canaryLive()))
        Files.move(canaryLive(), canaryParked(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
    }
  }

  public static void unparkCanary() {
    if (PERSIST_DIR == null) return;
    try {
      if (Files.exists(canaryLive())) {
        return;
      }
      else if (Files.exists(canaryParked())) {
        Files.move(canaryParked(), canaryLive(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      }
      else {
        writeCanary();
      }
    } catch (Exception e) {
    }
  }

  public static void setupCrashLogFolders() {
    PERSIST_DIR = ensureCrashDir(pickPersistDir(), "persistent");
    TEMP_DIR = ensureCrashDir(pickTempDir(), "temporary");
  }

  private static Path pickPersistDir() {
    Path home = Paths.get(System.getProperty("user.home"));
    if (Main.MacOS) {
      return home.resolve("Library/Logs/Logisim");
    } else if (Main.MSWindows) {
      String local = System.getenv("LOCALAPPDATA");
      if (local != null && !local.isBlank())
        return Paths.get(local, "Logisim", "Logs");
      String appdata = System.getenv("APPDATA");
      if (appdata != null && !appdata.isBlank())
        return Paths.get(appdata, "Logisim", "Logs");
      return home.resolve("AppData/Local/Logisim/Logs");
    } else if (Main.Linux) {
      String xdgState = System.getenv("XDG_STATE_HOME");
      if (xdgState != null && !xdgState.isBlank())
        return Paths.get(xdgState, "logisim", "logs");
      return home.resolve(".local/state/logisim/logs");
    } else { // other/unknown systems
      return home.resolve(".logisim-logs"); 
    }
  }

  private static Path pickTempDir() {
    if (Main.Linux) {
      String xdgRun = System.getenv("XDG_RUNTIME_DIR");
      if (xdgRun != null && !xdgRun.isBlank())
        return Paths.get(xdgRun).resolve("logisim");
    }
    Path temp = Paths.get(System.getProperty("java.io.tmpdir"));
    if (Main.MacOS || Main.MSWindows) {
      return temp.resolve("Logisim");
    } else { // other/unknown systems, and fallback for Linux
      String name = System.getProperty("user.name");
      return temp.resolve("logisim-"+name);
    }
  }

  private static Path ensureCrashDir(Path dir, String desc) {
    try {
      Path base = dir.toAbsolutePath().getRoot();
      boolean posix = Files.getFileStore(base).supportsFileAttributeView("posix");
      if (Files.exists(dir)) {
        if (posix)
          Files.setPosixFilePermissions(dir,
              PosixFilePermissions.fromString("rwx------"));
      } else {
        if (posix) {
          Files.createDirectories(dir,
              PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
        } else {
          Files.createDirectories(dir);
        }
      }
      Debug.printf(2, "Prepared %s crash log directory: %s\n", desc, dir);
      return dir;
    } catch (Exception e) {
      System.err.printf("Could not create %s crash-log directory: %s\n",  desc, dir.toString());
      System.err.printf("No %s logs will be created in case of crashes.\n", desc);
      return null;
    }
  }

  private static void installFileLog() {
    if (PERSIST_DIR == null)
      return;
    try {
      Path p = PERSIST_DIR.resolve("crash-" + timestamp + ".txt");
      Debug.printf(1, "Preparing crash log: %s\n", p);
      BufferedOutputStream f = new BufferedOutputStream(new FileOutputStream(p.toFile(), true));
      System.setOut(new PrintStream(new Tee(System.out, f), true, "UTF-8"));
      System.setErr(new PrintStream(new Tee(System.err, f), true, "UTF-8"));
      crashLogStream = f;
      crashLogPath = p;
      String line1 = String.format("=== Logisim-Evolution %s start %s ===\n", Main.VERSION.toString(), timestamp);
      String line2 = String.format("version: %s\n", Main.VERSION.toDetailString());
      if (verbose > 0) {
        // print banner to console and crash log
        System.out.print(line1);
        System.out.print(line2);
      } else {
        // write banner only to crash log
        try {
          f.write(line1.getBytes(StandardCharsets.UTF_8));
          f.write(line2.getBytes(StandardCharsets.UTF_8));
          f.flush();
        } catch (IOException ex) { ex.printStackTrace(); }
      }
    } catch (Exception e) {
      crashLogStream = null;
      crashLogPath = null;
      System.err.println("Failed to create crash log.");
      e.printStackTrace();
    }
  }

  private static void installUncaughtHandler() {
    Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
      nonfatalUncaughtException(ex, t.getName());
    });
  }

  private static class AWTGuard extends EventQueue {
    private static volatile boolean installed;

    public static void install() {
      if (Main.headless || installed)
        return;
      installed = true;
      EventQueue eq = Toolkit.getDefaultToolkit().getSystemEventQueue();
      if (eq instanceof AWTGuard)
        return;
      eq.push(new AWTGuard());
    }

    @Override
    protected void dispatchEvent(AWTEvent e) {
      try { super.dispatchEvent(e); }
      catch (Throwable ex) { nonfatalUncaughtException(ex, "AWT/Swing"); }
    }
  }

  static void dumpAllThreads() {
    var map = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
    System.err.println("--- BEGIN Thread dump ---");
    for (var ti : map) {
      System.err.println(ti.toString());
    }
    System.err.println("--- END Thread dump ---");
  }

  private static final Object lock = new Object();
  private static int numLoggedErrors = 0;
  private static int numSurfacedErrors = 0;

  // These are less severe and are not surfaced in GUI until shutdown.
  // All other classes should call this rather than dumping stuff to stderr.
  public static void error(String what) { error(what, null); }
  public static void error(Throwable ex) { error("unknown", ex); }
  public static void error(String what, Throwable ex) {
    synchronized(lock) { numLoggedErrors++; }
    String where = Thread.currentThread().getName();
    String desc = "Unexpected error: " + what + " in " + where + " @ " + Instant.now();
    System.err.println("\n*** " + desc);
    if (ex != null)
      ex.printStackTrace();
  }

  // These are severe and get surfaced in GUI immediately, but are not fatal.
  private static void nonfatalUncaughtException(Throwable ex, String where) {
    int recent;
    synchronized(lock) {
      recent = Math.max(0, numLoggedErrors - numSurfacedErrors);
      numLoggedErrors++;
      numSurfacedErrors = numLoggedErrors;
    }
    String desc = "Uncaught exception " + ex + " in " + where + " @ " + Instant.now();
    System.err.println("\n*** " + desc);
    ex.printStackTrace();
    dumpAllThreads();
    // cleanup(); // non-fatal, no cleanup, we keep running
    surfaceError(desc, ex, recent);
    // removeCanary(); // non-fatal, no clenaup, we keep running
  }

  // These are fatal and are surfaced immediately, then we exit.
  // All other classes should call this rather than exiting on their own.
  private static volatile boolean shuttingDown;
  public static void crashed(String desc, Throwable ex) {
    if (shuttingDown)
      return;
    shuttingDown = true;
    int recent;
    synchronized(lock) {
      recent = Math.max(0, numLoggedErrors - numSurfacedErrors);
      numLoggedErrors++;
      numSurfacedErrors = numLoggedErrors;
    }
    desc = "Crash " + desc + " @ " + Instant.now();
    System.err.println("\n*** " + desc);
    if (ex != null)
      ex.printStackTrace();
    dumpAllThreads();
    cleanup();
    surfaceError(desc, ex, recent);
    removeCanary();
    showDevContact();
    System.exit(-1);
  }

  private static void shutdownHook() {
    if (shuttingDown)
      return;
    shuttingDown = true;
    cleanup();
    int n = numLoggedErrors;
    if (n > 0) {
      showDevContact();
      int recent = Math.max(0, numLoggedErrors - numSurfacedErrors);
      if (recent > 0) {
        String desc = "Encountered " + n + " recent unexpected errors";
        surfaceError(desc, null, 0);
      }
    }
    removeCanary();
  }

  private static volatile boolean cleaned;
  private static void cleanup() {
    if (cleaned) return;
    cleaned = true;

    System.out.println("cleaning up...");
    try { System.err.flush(); } catch (Exception e) { }
    try { System.out.flush(); } catch (Exception e) { }
    
    // Flush and close debug log, but leave the file
    if (logStream != null) {
      try { logStream.flush(); } catch (IOException e) { }
      try { logStream.close(); } catch (IOException e) { }
      logStream = null;
      System.out.println("debug: stdout and stderr saved to: " + LOG_FILE);
    }
    
    // Cleanup crash log
    if (crashLogStream == null)
      return;
    try { System.err.flush(); } catch (Exception e) { }
    try { System.out.flush(); } catch (Exception e) { }
    try { crashLogStream.flush(); } catch (IOException e) { }
    try { crashLogStream.close(); } catch (IOException e) { }
    crashLogStream = null;
    if (numLoggedErrors == 0) {
      try { Files.deleteIfExists(crashLogPath); }
      catch (Exception e) { e.printStackTrace(); }
      crashLogPath = null;
      return;
    }

    // Save crash log
    try {
      crashLogSize = Files.size(crashLogPath);
      if (TEMP_DIR == null || crashLogSize <= MAX_CRASH_SIZE) {
        System.err.printf("crash: stack traces for %d errors saved to %s (%d bytes)\n",
            numLoggedErrors, crashLogPath, crashLogSize);
      } else {
        Path p = TEMP_DIR.resolve("crash-full-" + timestamp + ".txt");
        Files.copy(crashLogPath, p, StandardCopyOption.COPY_ATTRIBUTES);
        crashLogUncutPath = p;
        crashLogUncutSize = crashLogSize;
        crashLogSize = trimLog(crashLogUncutPath, crashLogPath);
        System.err.printf("crash: stack traces for %d errors saved to %s (%d bytes, full trace)\n",
            numLoggedErrors, crashLogUncutPath, crashLogUncutSize);
        System.err.printf("crash: stack traces for %d errors saved to %s (%d bytes, truncated)\n",
            numLoggedErrors, crashLogPath, crashLogSize);
      }
      System.err.printf("crash: at most %d will be kept in this directory,\n"
          + "  with older files deleted automatically.\n", PERSIST_MAX_FILES);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void showDevContact() {
    System.err.println("So sorry! Logisim encountered errors :(");
    if (!Main.CRASH_CONTACT_LINK.isBlank() && !Main.CRASH_CONTACT_EMAIL.isBlank()) {
      System.err.println(
          "If you think these errors should be fixed, please report it here:\n" +
          "    " + Main.CRASH_CONTACT_LINK + "\n" +
          "Or, please email:\n" +
          "    " + Main.CRASH_CONTACT_EMAIL + "\n");
    } else if (!Main.CRASH_CONTACT_LINK.isBlank()) {
      System.err.println(
          "If you think these errors should be fixed, please report it here:\n" +
          "    " + Main.CRASH_CONTACT_LINK + "\n");
    } else if (!Main.CRASH_CONTACT_EMAIL.isBlank()) {
      System.err.println(
          "If you think these errors should be fixed, please email:\n" +
          "    " + Main.CRASH_CONTACT_EMAIL + "\n");
    } else {
      System.err.println(
          "If you think these errors should be fixed, please contact the developer\n" +
          "who maintains this version of logisim.\n");
    }
    System.err.println(
       "Send the stack traces, along with any other information you think might be\n" +
       "relevant. Don't be shy, they want to hear from you!\n");
    System.err.println();
    System.err.println(
        "Care has been taken to ensure your prrivacy. Crash logs contain no personal\n" +
        "information, no details about your activity, no telmetry or other tracking\n" +
        "data, and no detailed information about you or your computer. We tried to\n" +
        "include only basic information about the error logisim encountered.\n" +
        "Nevertheless, before sending anything you can review the files to be sure. If\n" +
        "you like, it would help if you could give additional details: what you were\n" +
        "trying to do when the error happened; anything unsual about your computer;\n" +
        "or a circuit file that can reproduce the issue.\n");
  }

  private static long trimLog(Path src, Path dst) {
    FileChannel in = null, out = null;
    try {
      long size = Files.size(src);

      long tailsize = Math.min(size, CUT_CRASH_SIZE/2);
      long headsize = Math.min(size - tailsize, CUT_CRASH_SIZE/2);
      byte[] snip = String.format(
          "\n[... truncated %d bytes here ...]\n",
          size - (headsize - tailsize)).getBytes(StandardCharsets.UTF_8);

      in = FileChannel.open(src, StandardOpenOption.READ);
      out = FileChannel.open(dst, StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);

      long pos = 0, remain = headsize;
      while (remain > 0) {
        long n = in.transferTo(pos, remain, out);
        if (n <= 0) break;
        pos += n; remain -= n;
      }

      out.write(ByteBuffer.wrap(snip));

      pos = size - tailsize; remain = tailsize;
      while (remain > 0) {
        long n = in.transferTo(pos, remain, out);
        if (n <= 0) break;
        pos += n; remain -= n;
      }

      return headsize + snip.length + tailsize;
    } catch (Exception e) {
      return 0;
    } finally {
      try { if (in != null) in.close(); } catch (Exception e) { }
      try { if (out != null) out.close(); } catch (Exception e) { }
    }
  }

  private static void pruneCrashLogs() {
    int keep = Math.max(1, PERSIST_MAX_FILES);

    try (var s = Files.list(PERSIST_DIR)) {
      List<Path> logs = s.filter(Files::isRegularFile)
          .filter(p -> {
            String n = p.getFileName().toString();
            return n.startsWith("crash-") && n.endsWith(".txt");
          }).collect(Collectors.toList());

      if (logs.size() <= keep)
        return;

      Comparator<Path> byTime = Comparator.comparing(p -> {
        try {
          BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          FileTime t = a.creationTime();
          if (t.toMillis() > 0) return t;
        } catch (Exception ignore) { }
        try {
          return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS); }
        catch (Exception e) {
          return FileTime.fromMillis(0L);
        }
      });

      logs.sort(byTime);
      for (int i = 0; i < logs.size() - keep; i++) {
        Debug.printf(1, "Removing old crash file: %s\n", logs.get(i));
        try {
          Files.deleteIfExists(logs.get(i));
        } catch (Exception ignore) { }
      }
    } catch (Exception ignore) { }
  }

  private static void surfaceError(String desc, Throwable ex, int recent) {
    if (Main.headless)
      return;
    if (SwingUtilities.isEventDispatchThread()) {
      showCrashDialog(desc, ex, recent);
    } else if (shuttingDown) {
      try { SwingUtilities.invokeLater(() -> showCrashDialog(desc, ex, recent)); }
      catch (Exception ignored) { }
    } else {
      try { SwingUtilities.invokeAndWait(() -> showCrashDialog(desc, ex, recent)); }
      catch (Exception ignored) { }
    }
  }

  private static void showCrashDialog(String desc, Throwable ex, int recent) {
    String msg = desc + "\n";
    if (ex != null)
      msg += ex + "\n";
    if (recent > 0)
      msg += "(there were " + recent + " other recent errors too)\n";
    if (crashLogPath != null)
      msg += "(see crash log for more details)\n";

    String body = msg.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");

    String link = Main.CRASH_CONTACT_LINK;
    String email = Main.CRASH_CONTACT_EMAIL;
    String mailto = email.isBlank() ? "" : DesktopIntegration.formatMailto(email, "Logisim feedback");
    if (!link.isBlank() && !email.isBlank()) {
      body += 
          "<br>If you think these errors should be fixed, please report it here:" +
          "<br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='"+link+"'>"+ link + "</a>" +
          "<br>Or, please email:" +
          "<br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='"+mailto+"'>"+escapeEmail(email)+"</a>";
    } else if (!link.isBlank()) {
      body += 
          "<br>If you think these errors should be fixed, please report it here:" +
          "<br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='"+link+"'>"+ link + "</a>";
    } else if (!email.isBlank()) {
      body +=
          "<br>If you think these errors should be fixed, please email:" +
          "<br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='"+mailto+"'>"+ escapeEmail(email) + "</a>";
    } else {
      body +=
          "<br>If you think these errors should be fixed, please contact the developer" +
          " who maintains this version of logisim.";
    }
    body += "<br>" +
       "<br>Send the stack traces, along with any other information you think might be" +
       " relevant. Don't be shy, they want to hear from you!";

    body += "<br>" +
        "<br>Care has been taken to ensure your prrivacy. Crash logs contain no personal" +
        " information, no details about your activity, no telmetry or other tracking" +
        " data, and no detailed information about you or your computer. We tried to" +
        " include only basic information about the error logisim encountered." +
        "<br><br>Nevertheless, before sending anything you can review the files to be sure. If" +
        " you like, it would help if you could give additional details, like:<br>" +
        "<ul><li>what you were trying to do when the error happened;</li>" +
        "<li>anything unsual about your computer;</li>" +
        "<li>or a circuit file that can reproduce the issue.</li></ul>";

    JDialog d = new JDialog((Frame)null, "Logisim Error", false);

    JEditorPane linkPane = new JEditorPane("text/html",
        "<html><body style='font-family:sans-serif;'>" +
        body +
        "</body></html>");
    linkPane.setEditable(false);
    linkPane.setOpaque(false);
    linkPane.setCaretPosition(0);
    linkPane.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        DesktopIntegration.openBrowser(e.getURL());
      }
    });

    String detail0 = msg;
    try {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println(desc);
      if (ex != null)
        ex.printStackTrace(pw);
      pw.flush();
      detail0 = sw.toString();
    } catch (Exception e) {
    }
    final String detail = detail0;

    JButton copy = new JButton("Copy Error");
    copy.addActionListener(ev -> {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(detail), null);
    });

    JButton file = new JButton("Open Full Crash Log");
    if (crashLogPath != null) {
      file.addActionListener(ev -> openCrashLog());
    } else {
      file.setEnabled(false);
    }

    JButton folder = new JButton("Open Crash Log Folder");
    if (PERSIST_DIR != null) {
      folder.addActionListener(ev -> openCrashLogFolder());
    } else {
      folder.setEnabled(false);
    }

    JPanel buttons = new JPanel();
    buttons.add(copy);
    buttons.add(file);
    buttons.add(folder);
    d.getContentPane().add(new JScrollPane(linkPane), BorderLayout.CENTER);
    d.getContentPane().add(buttons, BorderLayout.SOUTH);
    d.setSize(520, 300);
    d.setLocationByPlatform(true);
    d.setVisible(true);
  }

  public static void openCrashLogFolder() {
    Path path = PERSIST_DIR;
    if (path == null)
      return;
    DesktopIntegration.openFolder(path);
  }

  public static void openCrashLog() {
    Path log = crashLogUncutPath;
    if (log == null)
      log = crashLogPath;
    if (log == null)
      return;
    DesktopIntegration.openTextfile(log);
  }

  public static String escapeEmail(String addr) {
    return addr.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","");
  }

  // Future work, if needed: We could also hs_err_pid*.log files, either
  // searching in common places, or adding jvm args to direct these
  // into PERSIST_DIR, then check for these on startup.

  ///////////////////////

  private static final String LOG_FILE = "./logisim_debug.txt";
  private static DebugThread debugThread;
  private static Scanner stdin;
  private static OutputStream logStream;

  // When "-debug" is given on command line, this enables the more dev-oriented
  // debug mode, including:
  // - Debug thread, using System.in for interactive commands
  // - More verbose printing of debug messages to System.out
  public static void enable() {
    if (debugThread != null)
      return;

    System.out.println("Debug mode enabled");

    try {
      OutputStream f = new FileOutputStream(LOG_FILE, true);
      System.setOut(new PrintStream(new Tee(System.out, f), true, "UTF-8"));
      System.setErr(new PrintStream(new Tee(System.err, f), true, "UTF-8"));
      logStream = f;
    } catch (Exception e) {
      e.printStackTrace();
      logStream = null;
    }

    System.out.printf("\n\n===== Logisim debug session: %s =====\n%s",
        new Date(),
        logStream != null ? "debug: stdout and stderr copied to: " + LOG_FILE + "\n": "");

    stdin = new Scanner(System.in);

    debugThread = new DebugThread();
    debugThread.setDaemon(true);
    debugThread.start();
  }

  public static int verbose = 0;

  public static void printf(int lvl, String fmt, Object... args) {
    if (verbose >= lvl)
      System.out.printf(Thread.currentThread().getName() + ": " + fmt, args);
  }
  
  public static void println(int lvl, String msg) {
    if (verbose >= lvl)
      System.out.println(Thread.currentThread().getName() + ": " + msg);
  }

  // public static void print(int lvl, String msg) {
  //   if (verbose >= lvl)
  //     System.out.print(Thread.currentThread().getName() + ": " + msg);
  // }

  static void doCmd(String cmd, String... args) {
    if (cmd.equals("verbose")) {
      verbose++;
      System.out.printf("verbosity is now %d\n", verbose);
    } else if (cmd.equals("quiet")) {
      verbose--;
      System.out.printf("verbosity is now %d\n", verbose);
    } else if (cmd.equals("rate")) {
      Project proj = Projects.getTopFrame().getProject();
      String name = proj.getLogisimFile().getName();
      if (args.length < 2) {
        double hz = proj.getSimulator().getTickFrequency();
        System.out.printf("tick rate for %s is %f Hz\n", name, hz);
      } else {
        double hz = Double.parseDouble(args[1]);
        proj.getSimulator().setTickFrequency(hz);
        System.out.printf("tick rate for %s is now %f Hz\n", name, hz);
      }
    } else {
      System.out.printf("unrecognized debug command: %s\n", cmd);
    }
  }

  private static class DebugThread extends UniquelyNamedThread {
    public DebugThread() {
      super("DebugThread");
    }
    @Override
    public void run() {
      try { Thread.sleep(1000); } catch (InterruptedException e) { }
      System.out.printf("$ ");
      System.out.flush();
      while (stdin.hasNextLine()) {
        try {
          String line = stdin.nextLine();
          String[] args = line.trim().split("\\s+");
          if (args.length > 0)
            doCmd(args[0], args);
        } catch (Throwable t) {
          t.printStackTrace();
        }
        System.out.printf("$ ");
        System.out.flush();
      }
    }
  }

  static class Tee extends OutputStream {
    private final OutputStream one;
    private final OutputStream two; // errors in this ignored

    public Tee(OutputStream a, OutputStream b) {
      one = a;
      two = b;
    }

    @Override
    public void write(int b) throws IOException {
      one.write(b);
      try { two.write(b); }
      catch (IOException e) { }
    }

    @Override
    public void write(byte[] b) throws IOException {
      one.write(b);
      try { two.write(b); }
      catch (IOException e) { }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      one.write(b, off, len);
      try { two.write(b, off, len); }
      catch (IOException e) { }
    }

    @Override
    public void flush() throws IOException {
      one.flush();
      try { two.flush(); }
      catch (IOException e) { }
    }

    @Override
    public void close() throws IOException {
      try {
        one.close();
      } finally {
        try { two.close(); }
        catch (IOException e) { }
      }
    }
  }

}
