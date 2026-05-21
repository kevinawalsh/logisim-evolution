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

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import com.cburch.logisim.Main;
import com.cburch.logisim.prefs.StateStore;

public class JFileChoosers {
  /*
   * A user reported that JFileChooser's constructor sometimes resulted in
   * IOExceptions when Logisim is installed under a system administrator
   * account and then is attempted to run as a regular user. This class is an
   * attempt to be a bit more robust about which directory the JFileChooser
   * opens up under. (23 Feb 2010)
   */
  private static class LogisimFileChooser extends JFileChooser {
    private static final long serialVersionUID = 1L;

    LogisimFileChooser() {
      super();
    }

    LogisimFileChooser(File initSelected) {
      super(initSelected);
    }

    @Override
    public File getSelectedFile() {
      File dir = getCurrentDirectory();
      if (dir != null) {
        JFileChoosers.currentDirectory = dir.toString();
      }
      return super.getSelectedFile();
    }
  }

  // On macOS, use java.awt.FileDialog instead of JFileChooser so we get the
  // native NSOpenPanel/NSSavePanel. This fixes keyboard navigation (letter
  // prefix jumping, Enter to open directories, arrow keys past filtered files)
  // and gives the dialog a modern macOS appearance. The tradeoff is that there
  // is no filter-type dropdown; the active filter still restricts visible files
  // but the user cannot switch filter types mid-dialog.
  // showDialog(Component, String) falls back to Swing except when in
  // DIRECTORIES_ONLY mode, where the native picker is cleaner. The three
  // non-directory showDialog call sites (missing-library picker, export image,
  // print export) keep the Swing fallback.
  // Additionally, showOpenDialog/showSaveDialog fall back to Swing when the
  // chooser has multiple non-accept-all filters (i.e. accept-all was explicitly
  // disabled). In that pattern the filter selection carries semantic meaning
  // beyond mere extension filtering (e.g. HexFile uses it to choose a save
  // format), and the native dialog cannot preserve the user's format choice.
  private static class MacOSNativeFileChooser extends JFileChooser {
    private static final long serialVersionUID = 1L;

    MacOSNativeFileChooser() {
      super();
    }

    MacOSNativeFileChooser(File initSelected) {
      super(initSelected);
    }

    private static Frame findParentFrame(Component parent) {
      for (Component c = parent; c != null; c = c.getParent()) {
        if (c instanceof Frame) return (Frame) c;
      }
      return null;
    }

    private int showNativeDialog(Component parent, int mode) {
      Frame frame = findParentFrame(parent);
      String title = getDialogTitle();
      FileDialog fd = new FileDialog(frame, title != null ? title : "", mode);

      File currentDir = getCurrentDirectory();
      if (currentDir != null)
        fd.setDirectory(currentDir.getAbsolutePath());

      File selectedFile = super.getSelectedFile();
      if (selectedFile != null && !selectedFile.isDirectory())
        fd.setFile(selectedFile.getName());

      FileFilter[] choosable = getChoosableFileFilters();
      FileFilter acceptAll = getAcceptAllFileFilter();
      boolean hasAcceptAll = false;
      for (FileFilter ff : choosable)
        if (ff == acceptAll) { hasAcceptAll = true; break; }
      // If accept-all is absent and there are multiple filters, the filters are
      // acting as format selectors (not just extension hints). Fall back to Swing
      // so the user can actually pick a format.
      if (!hasAcceptAll && choosable.length > 1)
        return mode == FileDialog.LOAD ? super.showOpenDialog(parent) : super.showSaveDialog(parent);
      // Otherwise build a union filter: a file is shown if any choosable filter
      // accepts it. When accept-all is present the union is effectively accept-all.
      if (choosable.length > 0)
        fd.setFilenameFilter((dir, name) -> {
          File f = new File(dir, name);
          for (FileFilter ff : choosable)
            if (ff.accept(f)) return true;
          return false;
        });

      boolean dirMode = (getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY);
      if (dirMode)
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
      try {
        fd.setVisible(true);
      } finally {
        if (dirMode)
          System.clearProperty("apple.awt.fileDialogForDirectories");
      }

      String resultDir = fd.getDirectory();
      String resultFile = fd.getFile();
      if (resultFile == null)
        return CANCEL_OPTION;

      File result = new File(resultDir, resultFile);
      setCurrentDirectory(result.getParentFile());
      JFileChoosers.currentDirectory = resultDir;
      setSelectedFile(result);
      return APPROVE_OPTION;
    }

    @Override
    public int showOpenDialog(Component parent) {
      return showNativeDialog(parent, FileDialog.LOAD);
    }

    @Override
    public int showSaveDialog(Component parent) {
      return showNativeDialog(parent, FileDialog.SAVE);
    }

    @Override
    public int showDialog(Component parent, String approveButtonText) {
      if (getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY)
        return showNativeDialog(parent, FileDialog.LOAD);
      return super.showDialog(parent, approveButtonText);
    }
  }

  private static JFileChooser newChooser() {
    return Main.MacOS ? new MacOSNativeFileChooser() : new LogisimFileChooser();
  }

  private static JFileChooser newChooser(File initSelected) {
    return Main.MacOS ? new MacOSNativeFileChooser(initSelected) : new LogisimFileChooser(initSelected);
  }

  public static JFileChooser create() {
    RuntimeException first = null;
    for (int i = 0; i < PROP_NAMES.length; i++) {
      String prop = PROP_NAMES[i];
      try {
        String dirname;
        if (prop == null) {
          dirname = currentDirectory;
          if (dirname.equals("")) {
            dirname = StateStore.DIALOG_DIRECTORY.get();
          }
        } else {
          dirname = System.getProperty(prop);
        }
        if (dirname.equals("")) {
          return newChooser();
        } else {
          File dir = new File(dirname);
          if (dir.canRead()) {
            return newChooser(dir);
          }
        }
      } catch (RuntimeException t) {
        if (first == null)
          first = t;
        Throwable u = t.getCause();
        if (!(u instanceof IOException))
          throw t;
      }
    }
    throw first;
  }

  public static JFileChooser createAt(File openDirectory) {
    if (openDirectory == null) {
      return create();
    } else {
      try {
        return newChooser(openDirectory);
      } catch (RuntimeException t) {
        if (t.getCause() instanceof IOException) {
          try {
            return create();
          } catch (RuntimeException u) {
          }
        }
        throw t;
      }
    }
  }

  public static JFileChooser createSelected(File selected) {
    if (selected == null) {
      return create();
    } else if (selected.isDirectory()) {
      return createAt(selected);
    } else {
      JFileChooser ret = createAt(selected.getParentFile());
      ret.setSelectedFile(selected);
      return ret;
    }
  }

  public static String getCurrentDirectory() {
    return currentDirectory;
  }

  private static final String[] PROP_NAMES = { null, "user.home", "user.dir",
    "java.home", "java.io.tmpdir" };

  private static String currentDirectory = "";

  private JFileChoosers() {
  }
}
