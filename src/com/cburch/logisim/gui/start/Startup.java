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

package com.cburch.logisim.gui.start;
import static com.cburch.logisim.gui.start.Strings.S;

import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import com.cburch.logisim.Main;
import com.cburch.logisim.access.Inventory;
import com.cburch.logisim.access.Connectivity;
import com.cburch.logisim.access.PinoutExporter;
import com.cburch.logisim.file.LoadCanceledByUser;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Print;
import com.cburch.logisim.gui.menu.HelpBroker;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.WindowManagers;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.StateStore;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.DesktopIntegration;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.LocaleManager;

public class Startup {

  private static final int ONEPARAM = 1;
  private static final int TWOPARAM = 2;
  private static final int HEADLESS = 4;
  private static final int NEEDFILES = 8;
  private static final int NEED1FILE = 16;

  private static final int NUMPARAMS = 3; // mask to extract ONEPARAM|TWOPARAM as int
  
  private static HashMap<String, Integer> options = new HashMap<>();
  static {
    options.put("--geom", ONEPARAM);
    options.put("--empty", 0);
    options.put("--plain", 0);
    options.put("--template", ONEPARAM);
    options.put("--gates", ONEPARAM);
    options.put("--locale", ONEPARAM);
    options.put("--accents", ONEPARAM);
    options.put("--nosplash", 0);
    options.put("--clearprefs", 0);
    options.put("--config", ONEPARAM);
    options.put("--defaults", ONEPARAM);
    options.put("--questa", ONEPARAM);
    options.put("--sub", TWOPARAM);
    options.put("--test", TWOPARAM); // is this a tty option? what is this?

    options.put("--version", HEADLESS);
    options.put("--help", HEADLESS);
    options.put("--list", HEADLESS | NEED1FILE);
    options.put("--pretty", 0);
    options.put("--png", HEADLESS | ONEPARAM | NEED1FILE);
    options.put("--tty", HEADLESS | ONEPARAM | NEED1FILE);
    options.put("--circuit", HEADLESS | ONEPARAM);
    options.put("--load", HEADLESS | ONEPARAM);

    options.put("--inventory", HEADLESS);
    options.put("--netlist", HEADLESS | NEED1FILE);
    options.put("--pinout", HEADLESS | ONEPARAM);

    options.put("--verbose", 0);
    options.put("-v", 0);
    options.put("-vv", 0);
    options.put("-vvv", 0);
    options.put("-vvvv", 0);
    options.put("-vvvvv", 0);

    options.put("-?", HEADLESS); // undocumented synonym for -help
    options.put("--clearprops", 0); // obsolete synonym for -clearprefs
    options.put("--noupdate", 0); // obsolte like auto-updates
    options.put("--analyze", 0); // obsolete option to enable analysis menu
    options.put("--debug", 0); // undocumented, enables debug console
 
    // Backwards compatibility: also accept -singledash options.
    for (Map.Entry<String, Integer> e : new HashSet<>(options.entrySet())) {
      if (e.getKey().startsWith("--"))
        options.put(e.getKey().substring(1), e.getValue());
    }
  }

  public static Startup parseArgs(String[] args) {

    String osname = System.getProperty("os.name", "generic").toLowerCase();
    Main.MacOS = osname.startsWith("mac") || osname.startsWith("darwin");
    Main.MSWindows = osname.contains("win");
    Main.Linux = osname.contains("nux") || osname.contains("nix") || osname.contains("aix");

    // first pass: check for headless, process locale, and make note of high priority items
    boolean doClearPreferences = false;
    File configOverride = null;
    File defaultsOverride = null;
    int i;
    for (i = 0; i < args.length && args[i].startsWith("-"); i++) {
      String arg = args[i];
      if (arg.equals("--")) {
        i++;
        break;
      }
      Integer o = options.get(arg);
      if (o == null)
        fail(S.fmt("argUnrecognized", arg));
      // headless
      Main.headless |= (o & HEADLESS) != 0;
      // params
      int n = o & NUMPARAMS;
      if (i + n >= args.length)
        fail(S.fmt(n == 1 ? "argMissingParam" : "argMissingParams", arg));
      i += n;
      if (arg.startsWith("--"))
        arg = arg.substring(1);
      // special cases
      doClearPreferences |= (arg.equals("-clearprefs") || arg.equals("-clearprops"));
      if (arg.equals("-config"))   configOverride  = new File(args[i]);
      if (arg.equals("-defaults")) defaultsOverride = new File(args[i]);
      if (arg.equals("-help") || arg.equals("-?"))
        printUsage();
      if (arg.equals("-locale"))
        setLocale(args[i]);
      if (arg.equals("-debug"))
        Debug.enable();
      if (arg.equals("-verbose") || arg.equals("-v"))
        Debug.verbose++;
      if (arg.equals("-vv"))
        Debug.verbose += 2;
      if (arg.equals("-vvv"))
        Debug.verbose += 3;
      if (arg.equals("-vvvv"))
        Debug.verbose += 4;
      if (arg.equals("-vvvvv"))
        Debug.verbose += 5;
    }
    
    Debug.init();

    // If headless requested, force awt to throw if touched
    if (Main.headless)
      System.setProperty("java.awt.headless", "true");

    if (GraphicsEnvironment.isHeadless() && !Main.headless)
      fail(S.get("argHeadlessError"));

    // Initialize settings and state stores before any AppPreferences access
    StateStore.initialize();
    AppPreferences.initialize(configOverride, defaultsOverride);

    if (doClearPreferences)
      AppPreferences.clear();

    if (!Main.headless) {
      // we're using the GUI: Set up the Look&Feel to match the platform
      System.setProperty(
          "com.apple.mrj.application.apple.menu.about.name",
          "Logisim-evolution");
      System.setProperty(
          "apple.awt.application.name",
          "Logisim-evolution");
      System.setProperty("apple.laf.useScreenMenuBar", "true");

      // Initialize graphics acceleration if appropriate
      AppPreferences.handleGraphicsAcceleration();

      // Make sure tooltips have a reasonable delay
      ToolTipManager tipManager = ToolTipManager.sharedInstance();
      int delay = tipManager.getDismissDelay();
      tipManager.setDismissDelay(Math.max(delay, 20_000));

      // On macOS, AWT delivers mouse events globally (across Spaces), so tooltips
      // from a Logisim window on one Space can appear over other apps on another Space.
      // Fix: suppress tooltips whenever no Logisim window is active (activeWindow == null
      // means focus is held by a non-Logisim app).
      KeyboardFocusManager.getCurrentKeyboardFocusManager()
          .addPropertyChangeListener("activeWindow",
              e -> tipManager.setEnabled(e.getNewValue() != null));
    }

    Startup ret = new Startup();
    if (!Main.headless)
      DesktopIntegration.init(ret);

    for ( ; i < args.length; i++)
      ret.filesToOpen.add(new File(args[i]));

    // second pass: parse arguments
    for (i = 0; i < args.length && args[i].startsWith("-") && !args[i].equals("--"); i++) {
      String arg = args[i];
      Integer o = options.get(arg);
      int n = o & NUMPARAMS;
      String param0 = n >= 1 ? args[i+1] : null;
      String param1 = n >= 2 ? args[i+2] : null;
      i += n;
      if ((o & NEED1FILE) != 0 && ret.filesToOpen.size() != 1)
        fail(S.fmt("argMissingOneFile", arg));
      if ((o & NEEDFILES) != 0 && ret.filesToOpen.isEmpty())
        fail(S.fmt("argMissingFiles", arg));
      if (arg.startsWith("--"))
        arg = arg.substring(1);
      if (arg.equals("-tty")) {
        ret.headlessTty = true;
        ret.doTty = true;
        String[] fmts = param0.split(",");
        if (fmts.length == 0)
          fail(S.get("ttyFormatError"));
        for (int j = 0; j < fmts.length; j++) {
          String fmt = fmts[j].trim();
          if (fmt.equals("pretty")) {
            ret.ttyFormat |= TtyInterface.FORMAT_PRETTY;
            ret.headlessPretty = true;
          }
          else if (fmt.equals("png")) {
            ret.ttyFormat |= TtyInterface.FORMAT_INCLUDE_PNG;
          }
          else if (fmt.equals("table"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE;
          else if (fmt.startsWith("rows:"))
            TtyInterface.turingMaxSteps = Integer.parseInt(fmt.substring(5));
          else if (fmt.startsWith("turing:")) {
            ret.ttyFormat |= TtyInterface.FORMAT_TURING;
            TtyInterface.turingInitialTape = fmt.substring(7);
          }
          else if (fmt.equals("speed"))
            ret.ttyFormat |= TtyInterface.FORMAT_SPEED;
          else if (fmt.equals("tty"))
            ret.ttyFormat |= TtyInterface.FORMAT_TTY;
          else if (fmt.equals("halt"))
            ret.ttyFormat |= TtyInterface.FORMAT_HALT;
          else if (fmt.equals("stats"))
            ret.ttyFormat |= TtyInterface.FORMAT_STATISTICS;
          else if (fmt.equals("binary"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE_BIN;
          else if (fmt.equals("hex"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE_HEX;
          else if (fmt.equals("dec") || fmt.equals("decimal") || fmt.equals("signed"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE_SDEC;
          else if (fmt.equals("udec") || fmt.equals("udecimal") || fmt.equals("unsigned"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE_UDEC;
          else if (fmt.equals("csv"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE_CSV;
          else if (fmt.equals("tabs"))
            ret.ttyFormat |= TtyInterface.FORMAT_TABLE_TABBED;
          else if (fmt.startsWith("choose:")) {
            ret.ttyFormat |= TtyInterface.FORMAT_RANDOMIZE;
            String[] p = fmt.split(":");
            try {
              if (p.length == 2) {
                ret.ttyRandomHead = ret.ttyRandomTail = 0;
                ret.ttyRandomBody = Integer.parseInt(p[1]);
              } else if (p.length == 3) {
                ret.ttyRandomHead = ret.ttyRandomTail = Integer.parseInt(p[1]);
                ret.ttyRandomBody = Integer.parseInt(p[2]);
              } else if (p.length == 4) {
                ret.ttyRandomHead = Integer.parseInt(p[1]);
                ret.ttyRandomBody = Integer.parseInt(p[2]);
                ret.ttyRandomTail = Integer.parseInt(p[3]);
              } else {
                fail("can't parse args for tty args choose:A[:B[:C]]");
              }
            } catch (NumberFormatException e) {
              fail("can't parse args for tty choose: " + fmt + " - " + e.getMessage());
            }
          } else {
            fail("unrecognized tty args: " + fmt);
          }
        }
      } else if (arg.equals("-png")) {
        ret.headlessPng = true;
        ret.doTty = true;
        String[] circuits = param0.split(",");
        if (circuits.length == 0)
          fail(S.get("pngArgError"));
        ret.headlessPngCircuits = circuits;
      } else if (arg.equals("-list")) {
        ret.headlessList = true;
        ret.doTty = true;
      } else if (arg.equals("-pretty")) {
        ret.headlessPretty = true;
      } else if (arg.equals("-sub")) {
        if (ret.substitutions.containsKey(param0))
          fail(S.get("argDuplicateSubstitutionError"));
        ret.substitutions.put(param0, param1);
      } else if (arg.equals("-load")) {
        if (ret.loadFile != null)
          fail(S.get("loadMultipleError"));
        ret.loadFile = new File(param0);
      } else if (arg.equals("-empty")) {
        if (ret.templFile != null || ret.templEmpty || ret.templPlain)
          fail(S.get("argOneTemplateError"));
        ret.templEmpty = true;
      } else if (arg.equals("-plain")) {
        if (ret.templFile != null || ret.templEmpty || ret.templPlain)
          fail(S.get("argOneTemplateError"));
        ret.templPlain = true;
      } else if (arg.equals("-template")) {
        if (ret.templFile != null || ret.templEmpty || ret.templPlain)
          fail(S.get("argOneTemplateError"));
        ret.templFile = new File(param0);
        if (!ret.templFile.exists())
          fail(S.fmt("templateMissingError", param0));
        if (!ret.templFile.canRead())
          fail(S.fmt("templateCannotReadError", param0));
      } else if (arg.equals("-version")) {
        System.out.println(Main.VERSION_NAME); // OK
        System.exit(0);
      } else if (arg.equals("-gates")) {
        if (param0.equals("shaped") || param0.equals("ansi"))
          AppPreferences.GATE_SHAPE.set(AppPreferences.SHAPE_SHAPED);
        else if (param0.equals("rectangular") || param0.equals("iec"))
          AppPreferences.GATE_SHAPE.set(AppPreferences.SHAPE_RECTANGULAR);
        else if (param0.equals("german") || param0.equals("din"))
          AppPreferences.GATE_SHAPE.set(AppPreferences.SHAPE_DIN40700);
        else
          fail(S.get("argGatesOptionError"));
      } else if (arg.equals("-geom")) {
        String wxh[] = param0.split("[xX]");
        if (wxh.length != 2 || wxh[0].length() < 1 || wxh[1].length() < 1)
          fail(S.get("argGeometryError"));
        int p = wxh[1].indexOf('+', 1);
        String loc = null;
        int x = 0, y = 0;
        if (p >= 0) {
          loc = wxh[1].substring(p+1);
          wxh[1] = wxh[1].substring(0, p);
          String xy[] = loc.split("\\+");
          if (xy.length != 2 || xy[0].length() < 1 || xy[0].length() < 1)
            fail(S.get("argGeometryError"));
          try {
            x = Integer.parseInt(xy[0]);
            y = Integer.parseInt(xy[1]);
          } catch (NumberFormatException e) {
            fail(S.get("argGeometryError"));
          }
        }
        int w = 0, h = 0;
        try {
          w = Integer.parseInt(wxh[0]);
          h = Integer.parseInt(wxh[1]);
        } catch (NumberFormatException e) {
          fail(S.get("argGeometryError"));
        }
        if (w <= 0 || h <= 0)
          fail(S.get("argGeometryError"));
        StateStore.WINDOW_WIDTH.set(w);
        StateStore.WINDOW_HEIGHT.set(h);
        if (loc != null) {
          StateStore.WINDOW_X.set(x);
          StateStore.WINDOW_Y.set(y);
        }
      } else if (arg.equals("-locale")) {
        // already handled above
      } else if (arg.equals("-accents")) {
        if (param0.equals("yes"))
          AppPreferences.ACCENTS_REPLACE.set(false);
        else if (param0.equals("no"))
          AppPreferences.ACCENTS_REPLACE.set(true);
        else
          fail(S.get("argAccentsOptionError"));
      } else if (arg.equals("-nosplash")) {
        ret.showSplash = false;
      } else if (arg.equals("-test")) {
        ret.circuitToTest = param0;
        ret.testVector = param1;
        ret.showSplash = false;
        ret.exitAfterStartup = true;
      } else if (arg.equals("-circuit")) {
        ret.circuitToTest = param0;
      } else if (arg.equals("-clearprefs") || arg.equals("-clearprops")) {
        // already handled above
      } else if (arg.equals("-config") || arg.equals("-defaults")) {
        // already handled above
      } else if (arg.equals("-analyze")) {
        // ignore
      } else if (arg.equals("-noupdates")) {
        // ignore
      } else if (arg.equals("-questa")) {
        if (param0.equals("yes"))
          AppPreferences.QUESTA_VALIDATION.set(true);
        else if (param0.equals("no"))
          AppPreferences.QUESTA_VALIDATION.set(false);
        else
          fail(S.get("argQuestaOptionError"));
      } else if (arg.equals("-inventory")) {
        ret.doInventory = true;
        ret.generateOutfile = "-"; // param0;
        if (ret.filesToOpen.size() > 1)
          fail(S.fmt("argMultipleFiles", arg));
      } else if (arg.equals("-generate-netlist")) {
        ret.doNetlist = true;
        ret.generateOutfile = "-"; // param0;
      } else if (arg.equals("-pinout")) {
        ret.doPinout = true;
        ret.pinoutSpec = param0;
        if (ret.filesToOpen.size() > 1)
          fail(S.fmt("argMultipleFiles", arg));
      } else if (arg.equals("-help") || arg.equals("-?")) {
        // already handled above
      }
    }

    // third pass: check remaining errors
    int n = 0;
    if (ret.doInventory) n++;
    if (ret.doNetlist) n++;
    if (ret.doPinout) n++;
    if (ret.doTty) n++;
    if (n > 1)
      fail(S.get("tooManyHeadless"));

    return ret;
  }

  private static void printUsage() {
    System.out.println(S.fmt("argUsage", Startup.class.getName()));
    for (int i = 1; ; i++) {
      String key = "argUsage" + i;
      String msg = S.get(key);
      if (msg.equals(key))
        break;
      if (!msg.startsWith("-") && !msg.startsWith(" ")) {
        // header
        System.out.println();
        System.out.println(msg);
      } else {
        if (msg.startsWith(" "))
          System.out.print(" "); // leading slash kills one space
        System.out.println("  " + msg);
      }
    }
    System.exit(0);
  }

  private static void setLocale(String lang) {
    Locale[] opts = S.getLocaleOptions();
    for (int i = 0; i < opts.length; i++) {
      if (lang.equals(opts[i].toString())) {
        LocaleManager.setLocale(opts[i]);
        return;
      }
    }
    System.out.println(S.get("invalidLocaleError"));
    System.out.println(S.get("invalidLocaleOptionsHeader"));
    for (int i = 0; i < opts.length; i++)
      System.out.println("  " + opts[i]);
    System.exit(1);
  }

  // based on command line
  private boolean doTty = false;
  boolean headlessTty, headlessPng, headlessList, headlessPretty;
  String headlessPngCircuits[];
  private File templFile = null;
  private boolean templEmpty = false;
  private boolean templPlain = false;
  private ArrayList<File> filesToOpen = new ArrayList<>();
  private String testVector = null;
  private String circuitToTest = null;
  private boolean exitAfterStartup = false;
  private boolean showSplash;
  private File loadFile;
  private HashMap<String, String> substitutions = new HashMap<>();
  private int ttyFormat = 0;
  private int ttyRandomHead, ttyRandomBody, ttyRandomTail;
  // from other sources
  private boolean initialized = false;
  private SplashScreen monitor = null;
  private boolean doInventory = false;
  private boolean doNetlist = false;
  private String generateOutfile;
  private boolean doPinout = false;
  private String pinoutSpec;

  private ArrayList<File> filesToPrint = new ArrayList<>();

  private Startup() {
    this.showSplash = !Main.headless;
  }

  // used by util/DesktopIntegration
  public void doOpenFile(File file) {
    if (initialized) {
      ProjectActions.doOpen(null, null, file);
    } else {
      filesToOpen.add(file);
    }
  }

  // used by util/DesktopIntegration
  public void doPrintFile(File file) {
    if (initialized) {
      Project toPrint = ProjectActions.doOpen(null, null, file);
      Print.doPrint(toPrint);
      toPrint.getFrame().dispose();
    } else {
      filesToPrint.add(file);
    }
  }

  public List<File> getFilesToOpen() {
    return filesToOpen;
  }

  File getLoadFile() {
    return loadFile;
  }

  String getCircuitToTest() {
    return circuitToTest;
  }

  Map<String, String> getSubstitutions() {
    return Collections.unmodifiableMap(substitutions);
  }

  int getTtyFormat() { return ttyFormat; }
  int getTtyRandomHead() { return ttyRandomHead; }
  int getTtyRandomBody() { return ttyRandomBody; }
  int getTtyRandomTail() { return ttyRandomTail; }

  private void loadTemplate() {
    if (templFile != null) {
      AppPreferences.TEMPLATE.setCustom(templFile, null);
    } else if (templEmpty) {
      AppPreferences.TEMPLATE.setEmpty();
    } else if (templPlain) {
      AppPreferences.TEMPLATE.setPlain();
    }
  }

  public void run() {
    if (Main.headless) {
      try {
        if (doInventory)
          Inventory.exportTo(generateOutfile, this);
        else if (doPinout)
          PinoutExporter.exportTo(pinoutSpec, this);
        else if (doNetlist)
          Connectivity.exportTo(generateOutfile,
              headlessOpen(filesToOpen.get(0)),
              circuitToTest);
        else // headlessTty, headlessPng, etc.
          TtyInterface.run(this);
        System.exit(0);
      } catch (Exception t) {
        t.printStackTrace();
        System.exit(1);
      }
    }

    // kick off the progress monitor
    // (The values used for progress values are based on a single run where
    // I loaded a large file.)
    if (showSplash) {
      try {
        monitor = SplashScreen.begin();
      } catch (Exception t) {
        monitor = null;
        showSplash = false;
      }
    }

    // pre-load the two basic component libraries, just so that the time
    // taken is shown separately in the progress bar.
    if (showSplash) {
      monitor.setProgress(SplashScreen.LIBRARIES);
    }
    Loader preLoader = new Loader(monitor);
    int count;
    count = preLoader.getBuiltin().getLibrary("Base").getTools().size();
    count += preLoader.getBuiltin().getLibrary("Gates").getTools().size();
    if (count < 0) {
      // this will never happen, but the optimizer doesn't know that...
      System.out.println("FATAL ERROR - no components"); // OK
      System.exit(1);
    }

    // load in template
    if (showSplash)
      monitor.setProgress(SplashScreen.TEMPLATE_OPEN);
    loadTemplate();

    // now that the splash screen is almost gone, we do some last-minute
    // interface initialization
    if (showSplash)
      monitor.setProgress(SplashScreen.GUI_INIT);
    WindowManagers.initialize();

    LogisimMenuBar menubar = new LogisimMenuBar(null, null, null, null);
    DesktopIntegration.installMenubar(menubar);
    // most of the time occupied here will be in loading menus, which
    // will occur eventually anyway; we might as well do it when the
    // monitor says we are

    // Make ENTER and SPACE have the same effect for focused buttons.
    UIManager.getDefaults().put("Button.focusInputMap",
        new UIDefaults.LazyInputMap(new Object[] {
          "ENTER", "pressed",
          "released ENTER", "released",
          "SPACE","pressed",
          "released SPACE","released"
        }));

    // if user has double-clicked a file to open, we'll
    // use that as the file to open now.
    initialized = true;

    // check for stray auto-backup files
    if (!Main.headless) {
      try {
        SwingUtilities.invokeAndWait(() -> Loader.checkForAutoBackups());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // load file
    if (filesToOpen.isEmpty()) {
      Project proj = ProjectActions.doNew(monitor);
      proj.setStartupScreen(true);
      if (showSplash)
        monitor.close();
      monitor = null;
    } else {
      int numOpened = 0;
      boolean first = true;
      for (File fileToOpen : filesToOpen) {
        String failmsg = null;
        if (!fileToOpen.exists()) {
          failmsg = S.fmt("startupMissingFileError", fileToOpen.toString());
        } else if (fileToOpen.isDirectory()) {
          failmsg = S.fmt("startupIsDirectoryError", fileToOpen.toString());
        } else if (!fileToOpen.canRead()) {
          failmsg = S.fmt("startupPermissionsError", fileToOpen.toString());
        } else {
          try {
            if (testVector != null) {
              Project proj = ProjectActions.doOpenNoWindow(monitor,
                  fileToOpen, substitutions);
              proj.doTestVector(testVector, circuitToTest);
            } else {
              ProjectActions.doOpen(monitor, fileToOpen, substitutions);
            }
            numOpened++;
          } catch (LoadCanceledByUser ex) {
            // eat exception
          } catch (LoadFailedException ex) {
            Errors.title(S.get("startupFailTitle")).show(
                S.fmt("startupCantOpenError", fileToOpen.getName()), ex);
          }
        }
        if (first) {
          first = false;
          if (showSplash)
            monitor.close();
          monitor = null;
        }
        if (failmsg != null) {
          String ttl = S.get("startupFailTitle");
          JOptionPane.showMessageDialog(null, failmsg, ttl, JOptionPane.ERROR_MESSAGE);
        }
      }
      if (numOpened == 0 && filesToPrint.isEmpty()) {
        if (exitAfterStartup)
          System.exit(1);
        else
          ProjectActions.doNew((SplashScreen)null);
      }
    }

    for (File fileToPrint : filesToPrint)
      doPrintFile(fileToPrint);

    if (exitAfterStartup)
      System.exit(0);

    // preload help broker in case old web pages are still open in browser
    HelpBroker.start();
  }

  private static void fail(String msg) {
    System.out.println(msg);
    if (!GraphicsEnvironment.isHeadless() && !Main.headless) {
      try { Errors.title(S.get("startupFailTitle")).show(msg); }
      catch (Throwable t) { }
    }
    System.exit(1);
  }

  public LogisimFile.FileWithSimulations headlessOpen(File fileToOpen) {
    Loader loader = new Loader(null);
    LogisimFile.FileWithSimulations file = null;
    try {
      file = loader.openLogisimFile(fileToOpen, getSubstitutions());
    } catch (LoadCanceledByUser e) {
      System.out.println(S.fmt("headlessLoadCancel", fileToOpen.getName()));
      System.exit(-1);
    } catch (LoadFailedException e) {
      System.out.println(S.fmt("headlessLoadError", fileToOpen.getName()));
      System.exit(-1);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(-1);
    }
    return file;
  }
}
