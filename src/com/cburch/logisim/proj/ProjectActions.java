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

package com.cburch.logisim.proj;
import static com.cburch.logisim.proj.Strings.S;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.LoadCanceledByUser;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.start.SplashScreen;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.JFileChoosers;

public class ProjectActions {
  private static class CreateFrame implements Runnable {
    private Loader loader;
    private Project proj;
    private boolean isStartupScreen;

    public CreateFrame(Loader loader, Project proj, boolean isStartup) {
      this.loader = loader;
      this.proj = proj;
      this.isStartupScreen = isStartup;
    }

    public void run() {
      try {
        Frame frame = createFrame(null, proj);
        frame.setVisible(true);
        frame.toFront();
        frame.getCanvas().requestFocus();
        loader.setParent(frame);
        if (isStartupScreen) {
          proj.setStartupScreen(true);
        }
      } catch (Exception e) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        Debug.error("main project run", e);
        JOptionPane.showMessageDialog(null, result.toString());
        System.exit(-1);
      }
    }
  }

  // /**
  //  * Returns true if the filename contains valid characters only, that is,
  //  * alphanumeric characters and underscores.
  //  */
  // private static boolean checkValidFilename(String filename) {
  //   Pattern p = Pattern.compile("[^a-z0-9_.]", Pattern.CASE_INSENSITIVE);
  //   Matcher m = p.matcher(filename);
  //   return (!m.find());
  // }

  private static Project completeProject(SplashScreen monitor, Loader loader,
      LogisimFile file, boolean isStartup) {
    return completeProject(monitor, loader, new LogisimFile.FileWithSimulations(file), isStartup);
  }

  private static Project completeProject(SplashScreen monitor, Loader loader,
      LogisimFile.FileWithSimulations file, boolean isStartup) {
    if (monitor != null)
      monitor.setProgress(SplashScreen.PROJECT_CREATE);
    Project ret = new Project(file);

    if (monitor != null)
      monitor.setProgress(SplashScreen.FRAME_CREATE);
    SwingUtilities.invokeLater(new CreateFrame(loader, ret, isStartup));
    return ret;
  }

  private static LogisimFile createEmptyFile(Loader loader) {
    InputStream templReader = AppPreferences.getEmptyTemplate().createStream();
    LogisimFile file;
    try {
      file = loader.openLogisimFile(null, templReader).file;
    } catch (Exception t) {
      file = LogisimFile.createNew(loader);
      file.addCircuit(new Circuit("main", file));
    } finally {
      try {
        templReader.close();
      } catch (IOException e) {
      }
    }
    return file;
  }

  private static Frame createFrame(Frame sourceProjectFrame, Project newProject) {
    if (sourceProjectFrame != null)
        sourceProjectFrame.savePreferences();
    return new Frame(newProject);
    // Frame newFrame = new Frame(newProject);
    // newProject.setFrame(newFrame);
    // return newFrame;
  }

  public static LogisimFile createNewFile(Component errReportFrame) {
    Loader loader = new Loader(errReportFrame);
    InputStream templReader = AppPreferences.getTemplate().createStream();
    LogisimFile file;
    try {
      file = loader.openLogisimFile(null, templReader).file;
    } catch (IOException ex) {
      displayException(errReportFrame, ex);
      file = createEmptyFile(loader);
    } catch (LoadCanceledByUser ex) {
      displayException(errReportFrame, ex);
      file = createEmptyFile(loader);
    } finally {
      try {
        templReader.close();
      } catch (IOException e) {
      }
    }
    return file;
  }

  private static void displayException(Component parent, Exception ex) {
    String msg = S.fmt("templateOpenError", ex.toString());
    String ttl = S.get("templateOpenErrorTitle");
    JOptionPane.showMessageDialog(parent, msg, ttl,
        JOptionPane.ERROR_MESSAGE);
  }

  public static Project doNew(Frame baseProjectFrame) {
    LogisimFile file = createNewFile(baseProjectFrame);
    Project newProj = new Project(new LogisimFile.FileWithSimulations(file));
    Frame frame = createFrame(baseProjectFrame, newProj);
    frame.setVisible(true);
    frame.getCanvas().requestFocus();
    newProj.getLogisimFile().getLoader().setParent(frame);
    return newProj;
  }

  public static Project doNew(SplashScreen monitor) {
    return doNew(monitor, false);
  }

  public static Project doNew(SplashScreen monitor, boolean isStartupScreen) {
    if (monitor != null)
      monitor.setProgress(SplashScreen.FILE_CREATE);
    Loader loader = new Loader(monitor);
    InputStream templReader = AppPreferences.getTemplate().createStream();
    LogisimFile file = null;
    try {
      file = loader.openLogisimFile(null, templReader).file;
    } catch (IOException ex) {
      displayException(monitor, ex);
    } catch (LoadCanceledByUser ex) {
      displayException(monitor, ex);
    } finally {
      try {
        templReader.close();
      } catch (IOException e) {
      }
    }
    if (file == null)
      file = createEmptyFile(loader);
    return completeProject(monitor, loader, file, isStartupScreen);
  }

  public static Project doOpen(Component parent, Project baseProject) {
    JFileChooser chooser;
    if (baseProject != null) {
      Loader oldLoader = baseProject.getLogisimFile().getLoader();
      chooser = oldLoader.createChooser();
      if (oldLoader.getMainFile() != null) {
        chooser.setSelectedFile(oldLoader.getMainFile());
      }
    } else {
      chooser = JFileChoosers.create();
    }
    chooser.setFileFilter(Loader.LOGISIM_FILTER);

    int returnVal = chooser.showOpenDialog(parent);
    if (returnVal != JFileChooser.APPROVE_OPTION)
      return null;
    File selected = chooser.getSelectedFile();
    if (selected == null)
      return null;
    return doOpen(parent, baseProject, selected);
  }

  private static String formatTime(long ts, long now) {
    Date date = new Date(ts);
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String when = fmt.format(date);
    String suffix = "ago";
    long delta = now - ts;
    if (delta < 0) {
      suffix = "in the future";
      delta = -delta;
    }
    if (delta < 10000) // under 10 seconds
      return String.format("%s (%.3f seconds %s)", when, delta/1000.0, suffix);
    delta /= 1000;
    if (delta < 60) // under a minute
      return String.format("%s (%d seconds %s)", when, delta, suffix);
    else if (delta < 5*60) // under 5 mintues
      return String.format("%s (%.1f minutes %s)", when, delta/60.0, suffix);
    else if (delta < 60*60) // under an hour
      return String.format("%s (%.0f minutes %s)", when, delta/60.0, suffix);
    else if (delta < 24*60*60) // under a day
      return String.format("%s (%.1f hours %s)", when, delta/60.0/60, suffix);
    else if (delta < 7*24*60*60) // under a week
      return String.format("%s (%.1f days %s)", when, delta/60.0/60/24, suffix);
    else // a week or more
      return String.format("%s (%.0f days %s)", when, delta/60.0/60/24, suffix);
  }

  private static File checkForAutoBackups(Component parent, File src) {
    // Check for auto-backup file
    // Check timestamps
    File bak = Loader.getAutoBackupFile(src);
    if (bak == null || !bak.exists())
      return src;
    // Check timestamps, delete, confirm, and/or restore.
    try {
      long bakTime = bak.lastModified();
      long srcTime = src.lastModified();
      if (bakTime <= srcTime) {
        bak.delete();
        return src;
      }
      long now = System.currentTimeMillis();
      String bakWhen = formatTime(bakTime, now);
      String srcWhen = formatTime(srcTime, now);
      String message = S.fmt("openAutoBackupFoundMessage", src.getName(), srcWhen, bakWhen);
      String[] options = {
        S.get("openAutoBackupDiscardOption"),
        S.get("openAutoBackupReplaceOption"),
        S.get("openAutoBackupKeepOption"), };
      if (parent instanceof SplashScreen) {
        ((SplashScreen)parent).close();
        parent = null;
      }
      int result = JOptionPane.showOptionDialog(parent, message,
          S.get("openAutoBackupFoundTitle"), 0,
          JOptionPane.QUESTION_MESSAGE, null, options,
          options[1]);
      if (result == 0) { // discard backup
        bak.delete();
        return src;
      } else if (result == 1) { // replace with backup
        File restored = restore(bak);
        if (restored != null) {
          JOptionPane.showMessageDialog(
              parent,
              S.fmt("autoBackupReplacedMessage",
                restored.getName(), src.getName()),
              S.get("openAutoBackupFoundTitle"),
              JOptionPane.INFORMATION_MESSAGE);
          return restored;
        } else {
          JOptionPane.showMessageDialog(
              parent,
              S.fmt("autoBackupFailedMessage",
                bak, src.getName()),
              S.get("openAutoBackupFoundTitle"),
              JOptionPane.INFORMATION_MESSAGE);
          return src;
        }
      } else { // keep both
        File restored = restore(bak);
        if (restored != null) {
          JOptionPane.showMessageDialog(
              parent,
              S.fmt("autoBackupKeptMessage",
                restored.getName(), src.getName()),
              S.get("openAutoBackupFoundTitle"),
              JOptionPane.INFORMATION_MESSAGE);
        } else {
          JOptionPane.showMessageDialog(
              parent,
              S.fmt("autoBackupFailedMessage",
                bak, src.getName()),
              S.get("openAutoBackupFoundTitle"),
              JOptionPane.INFORMATION_MESSAGE);
        }
        return src;
      }
    } catch (Exception e) {
      // e.printStackTrace();
      Debug.error("restoring backup", e);
      if (bak != null && bak.exists()) {
        File restored = restore(bak);
        if (restored != null)
          System.out.println("auto-backup file restored to: " + restored);
        else
          System.out.println("auto-backup file could not be restored.");
      }
      return src;
    }
  }

  private static File restore(File bak) {
    String name = bak.getName();
    if (name.startsWith("."))
      name = name.substring(1);
    if (name.toLowerCase().endsWith(".autosave"))
      name = name.substring(0, name.length() - ".autosave".length());
    if (name.toLowerCase().endsWith(".xml"))
      name = name.substring(0, name.length() - ".xml".length());
    if (name.toLowerCase().endsWith(".circ"))
      name = name.substring(0, name.length() - ".circ".length());
    if (name.equals(""))
      name = "project";
    name = name + "-restored";
    for (int i = 0; i < 20; i++) {
      File restored = new File(bak.getParent(), (i > 0 ? name+i : name) + ".circ");
      try {
        if (!restored.exists()) {
          bak.renameTo(restored);
          if (!restored.exists())
            throw new Exception("Could not move " + bak + " to " + restored);
          return restored;
        }
      } catch (Exception e) {
        if (i == 0)
          e.printStackTrace();
      }
    }
    return null;
  }

  public static Project doOpen(Component parent, Project baseProject, File f) {
    Project proj = Projects.findProjectFor(f);
    Loader loader = null;
    if (proj != null) {
      proj.getFrame().toFront();
      loader = proj.getLogisimFile().getLoader();
      if (proj.isFileDirty()) {
        String message = S.fmt("openAlreadyMessage", proj.getLogisimFile().getName());
        String[] options = {
          S.get("openAlreadyLoseChangesOption"),
          S.get("openAlreadyNewWindowOption"),
          S.get("openAlreadyCancelOption"), };
        int result = JOptionPane.showOptionDialog(proj.getFrame(), message,
                S.get("openAlreadyTitle"), 0,
                JOptionPane.QUESTION_MESSAGE, null, options,
                options[2]);
        if (result == 0) {
          ; // keep proj as is, so that load happens into the window
        } else if (result == 1) {
          proj = null; // we'll create a new project
        } else {
          return proj;
        }
      }
    } else {
      // only check for auto-backups if the file wasn't already open
      f = checkForAutoBackups(parent, f);
    }

    if (proj == null && baseProject != null && baseProject.isStartupScreen()) {
      proj = baseProject;
      proj.setStartupScreen(false);
      loader = baseProject.getLogisimFile().getLoader();
    } else {
      loader = new Loader(baseProject == null ? parent : baseProject.getFrame());
    }

    try {
      LogisimFile.FileWithSimulations libWithSim = loader.openLogisimFile(f);
      AppPreferences.updateRecentFile(f);
      if (libWithSim == null)
        return null;
      if (proj == null) {
        proj = new Project(libWithSim);
      } else {
        proj.setLogisimFile(libWithSim);
      }
    } catch (LoadCanceledByUser ex) {
      // eat exception
      return null;
    } catch (LoadFailedException ex) {
      JOptionPane.showMessageDialog(
          parent,
          S.fmt("fileOpenError", ex.toString()),
          S.get("fileOpenErrorTitle"),
          JOptionPane.ERROR_MESSAGE);
      return null;
    }

    Frame frame = proj.getFrame();
    if (frame == null)
      frame = createFrame(baseProject != null ? baseProject.getFrame() : null, proj);
    frame.setVisible(true);
    frame.toFront();
    frame.getCanvas().requestFocus();
    proj.getLogisimFile().getLoader().setParent(frame);
    return proj;
  }

  public static Project doOpen(SplashScreen monitor, File source,
      Map<String, String> substitutions) throws LoadFailedException, LoadCanceledByUser {
    if (monitor != null)
      monitor.setProgress(SplashScreen.FILE_LOAD);
    source = checkForAutoBackups(monitor, source);
    Loader loader = new Loader(monitor);
    LogisimFile.FileWithSimulations file = loader.openLogisimFile(source, substitutions);
    AppPreferences.updateRecentFile(source);

    return completeProject(monitor, loader, file, false);
  }

  public static Project doOpenNoWindow(SplashScreen monitor, File source,
      Map<String, String> substitutions)
      throws LoadFailedException, LoadCanceledByUser {
    Loader loader = new Loader(monitor);
    LogisimFile file = loader.openLogisimFile(source).file;
    return new Project(new LogisimFile.FileWithSimulations(file));
  }

  public static boolean doQuit() {
    Projects.saveTopFramePreferences();
    for (Project proj : new ArrayList<Project>(Projects.getOpenProjects())) {
      if (!proj.confirmClose(S.get("confirmQuitTitle")))
        return false;
    }
    System.exit(0);
    return true; // never reached
  }

  public static boolean doSave(Project proj) {
    Loader loader = proj.getLogisimFile().getLoader();
    File f = loader.getMainFile();
    if (f == null)
      return doSaveAs(proj);
    else
      return doSave(proj, f);
  }

  private static boolean doSave(Project proj, File f) {
    Tool oldTool = proj.getTool();
    proj.setTool(null);
    File bak = proj.getLogisimFile().getLoader().getAutoBackupFile(proj);
    boolean ret = proj.getLogisimFile().save(f, proj);
    if (ret) {
      if (bak != null) {
        try {
          bak.delete();
          // System.out.println("auto-backup removed for " + bak);
        } catch (Exception e) {
        }
      }
      AppPreferences.updateRecentFile(f);
      proj.setFileAsClean();
      removeAutoBackup(proj);
    }
    proj.setTool(oldTool);
    return ret;
  }

  public static void removeAutoBackup(Project proj) {
    try {
      File bak = proj.getLogisimFile().getLoader().getAutoBackupFile(proj);
      if (bak != null) {
        bak.delete();
        // System.out.println("auto-backup removed for " + bak);
      }
    } catch (Exception e) {
    }
  }

  public static void doAutoBackup(Project proj) {
    if (!AppPreferences.AUTO_BACKUP.get()) {
      // System.out.println("auto-backup not enabled");
      return;
    }
    if (proj == null) {
      // System.out.println("auto-backup not needed, project was closed");
      return;
    }
    if (!proj.isFileDirty()) {
      // System.out.println("auto-backup not needed, project is not dirty");
      return;
    }
    Loader loader = proj.getLogisimFile().getLoader();
    File f = loader.getAutoBackupFile(proj);
    if (f == null) {
      // System.out.println("auto-backup failed");
      return;
    }
    boolean ret = proj.getLogisimFile().autoBackup(f, proj);
    if (ret) {
      // proj.setFileAsSafe();
      // System.out.println("auto-backup done for " + f);
    } else {
      // System.out.println("auto-backup failed for " + f);
    }
  }

  /**
   * Saves a Logisim project in a .circ file.
   *
   * It is the action listener for the File->Save as... menu option.
   *
   * @param proj
   *            project to be saved
   * @return true if success, false otherwise
   */
  public static boolean doSaveAs(Project proj) {
    Loader loader = proj.getLogisimFile().getLoader();
    JFileChooser chooser = loader.createChooser();
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    if (loader.getMainFile() != null) {
      chooser.setSelectedFile(loader.getMainFile());
    }

    int returnVal = chooser.showSaveDialog(proj.getFrame());
    if (returnVal != JFileChooser.APPROVE_OPTION)
      return false;

    File f = chooser.getSelectedFile();
    String circExt = LogisimFile.LOGISIM_EXTENSION;
    if (!f.getName().endsWith(circExt)) {
      String old = f.getName();
      int ext0 = old.lastIndexOf('.');
      if (ext0 < 0
          || !Pattern.matches("\\.\\p{L}{2,}[0-9]?",
            old.substring(ext0))) {
        f = new File(f.getParentFile(), old + circExt);
      } else {
        String ext = old.substring(ext0);
        String ttl = S.get("replaceExtensionTitle");
        String msg = S.fmt("replaceExtensionMessage", ext);
        Object[] options = {
          S.fmt("replaceExtensionReplaceOpt", ext),
          S.fmt("replaceExtensionAddOpt", circExt),
          S.get("replaceExtensionKeepOpt") };
        JOptionPane dlog = new JOptionPane(msg);
        dlog.setMessageType(JOptionPane.QUESTION_MESSAGE);
        dlog.setOptions(options);
        dlog.createDialog(proj.getFrame(), ttl).setVisible(true);

        Object result = dlog.getValue();
        if (result == options[0]) {
          String name = old.substring(0, ext0) + circExt;
          f = new File(f.getParentFile(), name);
        } else if (result == options[1]) {
          f = new File(f.getParentFile(), old + circExt);
        }
      }
    }

    if (f.exists()) {
      int confirm = JOptionPane.showConfirmDialog(proj.getFrame(),
          S.get("confirmOverwriteMessage"),
          S.get("confirmOverwriteTitle"),
          JOptionPane.YES_NO_OPTION);
      if (confirm != JOptionPane.YES_OPTION)
        return false;
    }
    return doSave(proj, f);
  }

  private ProjectActions() {
  }
}
