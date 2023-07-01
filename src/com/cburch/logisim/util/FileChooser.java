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

import java.io.File;
import java.io.IOException;

import net.tomahawk.XFileDialog;

import java.awt.Component;
import javax.swing.JFileChooser;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.StringGetter;

/*
 * This class is a wrapper around XFileDialog that tries to be a bit more robust
 * about the initial directory. If Logisim is installed under a system
 * administrator account and then is attempted to run as a regular user, this
 * can sometimes cause security exceptions (23 Feb 2021). We also want to use
 * AppPreferences as the default location in most cases.
 */
public class FileChooser extends XFileDialog {

  private static final long serialVersionUID = 1L;

  // Create a file chooser in some reasonable directory. We try a series of
  // directories and use the first one that is readable:
  //   Most recent -- the last successful directory used, if available
  //   AppPreferences.DIALOG_DIRECTORY -- saved from user's previous history
  //   user.home/Documents -- maybe works on Windows 10, MacOS X, and most Linux platforms
  //   user.home -- seems reasonable, though not the typical default
  //   user.dir -- current working directory, sensible for command-line usage
  //   empty -- a platform-dependent default
  //   java.io.tmpdir -- a last restort if none of the above work
  // Upon success, we record the user's chosen directory for use in the next
  // call. Eventually, this also gets saved into AppPreferences.DIALOG_DIRECTORY
  // for later executions.
  private static File normalizeDirectory(File dir) {
    if (dir != null && !dir.isDirectory())
      dir = dir.getParentFile();
    if ((dir == null || !dir.canRead()) && recentDirectory != null)
      dir = new File(recentDirectory);
    if (dir == null || !dir.canRead())
      dir = new File(AppPreferences.DIALOG_DIRECTORY.get());
    if (dir == null || !dir.canRead())
      dir = new File(System.getProperty("user.home"), "Documents");
    if (dir == null || !dir.canRead())
      dir = new File(System.getProperty("user.home"));
    if (dir == null || !dir.canRead())
      dir = new File(System.getProperty("user.dir"));
    if (dir == null || !dir.canRead())
      dir = new File(System.getProperty("java.home"));
    return dir;
  }
  
  public static FileChooser create() {
    return createAt(null, null);
  }
  
  public static FileChooser create(Component parent) {
    return createAt(parent, null);
  }

  public static FileChooser createAt(Component parent, File dir) {
    dir = normalizeDirectory(dir);
    FileChooser dlg = new FileChooser(parent);
    if (dir != null && dir.canRead())
      dlg.setDirectory(dir.getPath());
    return dlg;
  }

  public static FileChooser createSelected(Component parent, File selected) {
    if (selected == null)
      return createAt(parent, null);
    FileChooser dlg = new FileChooser(parent);
    dlg.setFile(selected.getPath());
    return dlg;
  }

  private boolean accepted = false;

  private FileChooser(Component parent) { super(parent); }

  public void setVisible(boolean visible) {
    if (getTitle() == null || getTitle().trim().equals("")) {
      if (getMode() == LOAD && isMultipleMode())
        setTitle("Open Files");
      else if (getMode() == LOAD)
        setTitle("Open File");
      else
        setTitle("Save File");
    }
    super.setVisible(visible);
    accepted = visible && getFile() != null;
    if (accepted)
      recentDirectory = getDirectory();
  }

  public boolean showSaveDialog() {
    setMode(SAVE);
    setVisible(true);
    return accepted;
  }

  public boolean showOpenDialog() {
    setMode(LOAD);
    setVisible(true);
    return accepted;
  }

  public File getSelectedFile() {
    return accepted ? getFiles()[0] : null; // returns full path
  }

  private static String recentDirectory = null;

  public static String getRecentDirectory() {
    return recentDirectory != null ? recentDirectory : AppPreferences.DIALOG_DIRECTORY.get();
  }

  public static final Filter ACCEPT_ALL = new Filter("All Files", "*"); // FIXME: localize

  public static class LocalizedFilter extends Filter {
    StringGetter nameGetter;
    public LocalizedFilter(StringGetter name, String... extensions) {
      super(name.toString(), extensions);
      nameGetter = name;
    }
    public String getName() {
      return nameGetter.toString();
    }
  }

  /* The code below is for the few cases that need the customizability of
   * Swing's JFileChooser.
   */
  private static class LogisimFileChooser extends JFileChooser {
    private static final long serialVersionUID = 1L;

    LogisimFileChooser() { super(); }
    LogisimFileChooser(File initSelected) { super(initSelected); }

    @Override
    public File getSelectedFile() {
      File dir = getCurrentDirectory();
      if (dir != null)
        recentDirectory = dir.toString();
      return super.getSelectedFile();
    }
  }

  public static JFileChooser createSwingChooserSelected(File selected) {
    if (selected == null) {
      return createSwingChooser();
    } else if (selected.isDirectory()) {
      return createSwingChooserAt(selected);
    } else {
      JFileChooser ret = createSwingChooserAt(selected.getParentFile());
      ret.setSelectedFile(selected);
      return ret;
    }
  }

  public static JFileChooser createSwingChooserAt(File openDirectory) {
    if (openDirectory == null) {
      return createSwingChooser();
    } else {
      try { return new LogisimFileChooser(openDirectory); }
      catch (RuntimeException t) {
        if (t.getCause() instanceof IOException) {
          try { return createSwingChooser(); }
          catch (RuntimeException u) { }
        }
        throw t;
      }
    }
   }

  public static JFileChooser createSwingChooser() {
    File dir = normalizeDirectory(null);
    if (dir != null && dir.canRead())
      return new LogisimFileChooser(dir);
    else
      return new LogisimFileChooser();
  }

}
