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

package com.cburch.logisim.data;
import static com.cburch.logisim.data.Strings.S;

import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileFilter;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.JInputDialog;
import com.cburch.logisim.util.StringGetter;

// LinkedOrEmbedded<Thing> can hold:
//  - file path in circ file, which is read and parsed into a Thing
//  - raw bytes in circ file, which is parsed into a Thing
//  - empty
public class LinkedOrEmbedded<Thing> {

  @FunctionalInterface
  public interface Parser<Thing> {
    Thing parse(byte[] data) throws IOException;
  }

  // For linked content: format and absolute are valid, content and timestamp used as cache
  // For embedded content: format and data are valid, content is valid if possible
  // For empty content: both absolute and data are null, format is "empty"
  private final String format; // "empty", or some custom tag like "text", "PNG", "JPG", etc.
  private final File absolute, relative; // non-null iff linked content
  private final Parser<Thing> parser; // non-null iff linked content
  private final byte[] data; // non-null iff embedded content

  private Thing content; // parsed from data or file, or null if empty
  private long timestamp; // only for linked: file timestamp when data was last parsed
  
  // Constructor for empty content
  public LinkedOrEmbedded() {
    format = "empty";
    absolute = relative = null;
    parser = null;
    data = null;
  }

  // Constructor for linked content; later exceptions from parser are suppresed
  public LinkedOrEmbedded(String fmt, File a, File r, Parser<Thing> p) {
    format = fmt;
    absolute = a;
    relative = r;
    parser = p;
    data = null;
  }

  // Constructor for embedded content; may have exception now, no later exceptions
  public LinkedOrEmbedded(String fmt, byte[] d, Thing c) {
    format = fmt;
    absolute = relative = null;
    parser = null;
    data = d;
    content = c;
  }

  public LinkedOrEmbedded(LinkedOrEmbedded<Thing> other) {
    this.format = other.format;
    this.relative = other.relative;
    this.absolute = other.absolute;
    this.parser = other.parser;
    this.data = other.data;
    this.content = other.content;
    this.timestamp = other.timestamp;
  }

  public boolean isEmpty() {
    return absolute == null && data == null;
  }

  public Thing getContent() {
    if (absolute == null)
      return content; // embedded or empty
    long ts = absolute.lastModified();
    if (ts == timestamp)
      return content; // cached
    timestamp = ts;
    try {
      byte[] fileData = Files.readAllBytes(absolute.toPath());
      content = parser.parse(fileData);
    } catch (IOException e) {
      Debug.error("Failed to parse " + absolute, e);
    }
    return content;
  }

  public static File relativize(File abs, Window source) {
    if (abs == null || !abs.isAbsolute() || !(source instanceof Frame)) {
      return abs;
    }
    Project proj = ((Frame)source).getProject();
    LogisimFile lf = (proj == null ? null : proj.getLogisimFile());
    Loader ld = (lf == null ? null : lf.getLoader());
    File parent = (ld == null ? null : ld.getCurrentDirectory());
    if (parent == null)
      return abs;
    File rel;
    try {
      rel = parent.toPath().toRealPath().relativize(abs.toPath().toRealPath()).toFile();
    } catch (IOException ex) {
      rel = parent.toPath().relativize(abs.toPath()).toFile();
    }
    return rel;
  }

  public static File resolve(File rel, Window source) {
    if (rel == null || rel.isAbsolute() || !(source instanceof Frame)) {
      return rel;
    }
    Project proj = ((Frame)source).getProject();
    LogisimFile lf = (proj == null ? null : proj.getLogisimFile());
    Loader ld = (lf == null ? null : lf.getLoader());
    File parent = (ld == null ? null : ld.getCurrentDirectory());
    if (parent == null)
      return rel;
    File abs = parent.toPath().resolve(rel.toPath()).toFile();
    return abs;
  }


  public static abstract class Attr<Thing> extends Attribute<LinkedOrEmbedded<Thing>> {

    public final LinkedOrEmbedded<Thing> EMPTY = new LinkedOrEmbedded<>();

    // encode raw data (e.g. from file, or from decoded circ) so it can appear after "fmt:" in circ file
    protected abstract String encodeData(byte[] data);
    // decode the string after "fmt:" in circ file into raw data
    protected abstract byte[] decodeData(String fmt, String encoded);
    // parse raw data (e.g from file, or from decoded circ) into content object
    protected abstract Thing parseData(byte[] data) throws IOException;
    // determine format tag for a file path
    protected abstract String formatForPath(File f);

    protected StringGetter dialogTitle;
    protected FileFilter[] filters;

    public Attr(String name, StringGetter desc, StringGetter dialogTitle, FileFilter... filters) {
      super(name, desc);
      this.dialogTitle = dialogTitle;
      this.filters = filters;
    }

    @Override
    public java.awt.Component getCellEditor(Window source, LinkedOrEmbedded<Thing> s) {
      return new Chooser<>(this, (Frame)source, s);
    }

    @Override
    public String toDisplayString(LinkedOrEmbedded<Thing> value) {
      if (value == null || value.isEmpty())
        return S.get("stdEmbedClickToLoad");
      else if (value.absolute != null)
        return value.relative + " [" + value.absolute + "]";
      else
        return String.format(S.get("stdEmbedContentInfo"), value.format, value.data.length);
    }

    @Override
    public String toStandardString(LinkedOrEmbedded<Thing> value) {
      return toStandardStringRelative(value, null);
    }

    @Override
    public String toStandardStringRelative(LinkedOrEmbedded<Thing> value, String outFilename) {
      if (value == null || value.isEmpty()) {
        return "";
      }
      if (value.data != null) {
        // embedded, encode existing data
        return value.format+":"+encodeData(value.data);
      } else if (outFilename == null || outFilename.equals("")) {
        // linked, but use absolute path
        return "file:" + value.absolute.toString();
      } else {
        // linked, use relative path
        try {
          return "file:" + new File(outFilename).toPath().toRealPath().relativize(value.absolute.toPath().toRealPath()).toString();
        } catch (IOException e) {
          return "file:" + new File(outFilename).toPath().relativize(value.absolute.toPath()).toString();
        }
      }
    }

    @Override
    public LinkedOrEmbedded<Thing> parse(String str) {
      throw new UnsupportedOperationException("can't parse embedded content without context");
    }

    @Override
    public LinkedOrEmbedded<Thing> parseFromUser(Window source, String value) {
      throw new UnsupportedOperationException("can't parse embedded content without context");
    }

    @Override
    public LinkedOrEmbedded<Thing> parseFromFilesystem(File directory, String value) {
      if (value == null || value.equals("")) {
        return EMPTY;
      }
      if (value.length() >= 5 && value.substring(0, 5).equalsIgnoreCase("file:")) {
        String path = value.substring(5);
        return parsePathFromFilesystem(directory, path);
      }
      int idx = value.indexOf(':');
      if (idx < 0) {
        return EMPTY;
      }
      String fmt = value.substring(0, idx);
      String encoded = value.substring(idx+1);
      byte[] data = decodeData(fmt, encoded);
      Thing content = null;
      try {
        content = parseData(data);
      } catch (IOException e) {
        Errors.title(S.get("stdEmbedParseErrorTitle")).show(S.get("stdEmbedParseErrorMessage"), e);
      }
      return new LinkedOrEmbedded<>(fmt, data, content);
    }

    private LinkedOrEmbedded<Thing> parsePathFromFilesystem(File directory, String path) {
      if (path == null || path.equals(""))
        return null;
      if (directory == null || directory.toString().equals("")) {
        // happens when copy-pasting
        File abs = new File(path);
        File rel = abs;
        String fmt = formatForPath(abs);
        return new LinkedOrEmbedded<>(fmt, abs, rel, this::parseData);
      } else {
        // happens when loading from Xml
        Path parent = directory.toPath();
        Path abs = parent.resolve(new File(path).toPath());
        Path rel;
        try {
          rel = parent.toRealPath().relativize(abs.toRealPath());
        } catch (IOException ex) {
          rel = parent.relativize(abs);
        }
        String fmt = formatForPath(abs.toFile());
        return new LinkedOrEmbedded<>(fmt, abs.toFile(), rel.toFile(), this::parseData);
      }
    }

    @Override
    public Domain getDomain() {
      return Domain.ofDescription("file:path or fmt:encoded_contents");
    }

  }


  private static class Chooser<Thing> extends java.awt.Component implements JInputDialog<LinkedOrEmbedded<Thing>> {
    
    private final Frame parent;
    private final Attr<Thing> attr;
    private LinkedOrEmbedded<Thing> result;

    Chooser(Attr<Thing> attr, Frame parent, LinkedOrEmbedded<Thing> r) {
      this.attr = attr;
      this.parent = parent;
      this.result = r;
    }

    @Override
    public void setValue(LinkedOrEmbedded<Thing> r) { result = r; }

    @Override
    public LinkedOrEmbedded<Thing> getValue() { return result; }

    @Override
    public void setVisible(boolean b) {
      if (!b)
        return;
      if (result != null && !result.isEmpty()) {
        int[] choice = {-1}; // -1 = dismissed, 0 = choose new, 1 = remove
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem chooseItem = new JMenuItem(S.get("stdEmbedChooseNewOption"));
        chooseItem.addActionListener(e -> { choice[0] = 0; loop.exit(); });
        menu.add(chooseItem);
        JMenuItem removeItem = new JMenuItem(S.get("stdEmbedRemoveOption"));
        removeItem.addActionListener(e -> { choice[0] = 1; loop.exit(); });
        menu.add(removeItem);
        menu.addPopupMenuListener(new PopupMenuListener() {
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { loop.exit(); }
          public void popupMenuCanceled(PopupMenuEvent e) { loop.exit(); }
        });
        Point loc = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(loc, parent);
        menu.show(parent, loc.x, loc.y);
        loop.enter();
        if (choice[0] == 1) { // remove
          result = attr.EMPTY;
          return;
        } else if (choice[0] != 0) { // dismissed without selection
          return;
        }
        // choice[0] == 0: fall through to file dialog
      }

      JFileChooser chooser = JFileChoosers.create();
      chooser.setDialogTitle(attr.dialogTitle.toString());
      if (result == null || result.absolute == null) {
        chooser.setSelectedFile(null);
      } else if (result.absolute.isDirectory()) {
        chooser.setCurrentDirectory(result.absolute);
      } else {
        chooser.setCurrentDirectory(result.absolute.getParentFile());
        chooser.setSelectedFile(result.absolute);
      }
      if (attr.filters != null && attr.filters.length != 0) {
        chooser.setAcceptAllFileFilterUsed(false);
        for (FileFilter ff : attr.filters) {
          chooser.addChoosableFileFilter(ff);
        }
        chooser.setFileFilter(attr.filters[0]);
      }

      int choice = chooser.showOpenDialog(parent);
      if (choice != JFileChooser.APPROVE_OPTION)
        return;

      File f = chooser.getSelectedFile();
      File absolute = resolve(f, parent);
      long size = 0;
      byte[] data = null;
      try {
        // read all data in case of embed, also for sanity check
        data = Files.readAllBytes(absolute.toPath());
        // size is needed
        size = data.length;
      } catch (IOException e) {
        Errors.title(S.get("stdEmbedUnreadableTitle")).show(S.get("stdEmbedUnreadableMessage"), e);
        return;
      }
      Thing content = null;
      try {
        // try to parse, to surface errors, fail if can't parse
        if (data != null)
          content = attr.parseData(data);
      } catch (IOException e) {
        Errors.title(S.get("stdEmbedParseErrorTitle")).show(S.get("stdEmbedParseErrorMessage"), e);
        return;
      }

      String[] options = {
        S.get("stdEmbedEmbedOption"),
        S.get("stdEmbedLinkOption"),
        S.get("stdEmbedCancelOption") };
      choice = JOptionPane.showOptionDialog(parent,
          String.format(S.get("stdEmbedStorageDialogQuestion"), size),
          S.get("stdEmbedStorageDialogTitle"), 0,
          JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
      if (choice == 0) { // embed
        String fmt = attr.formatForPath(absolute);
        result = new LinkedOrEmbedded<>(fmt, data, content);
      } else if (choice == 1) { // link
        String fmt = attr.formatForPath(absolute);
        File relative = relativize(absolute, parent);
        result = new LinkedOrEmbedded<>(fmt, absolute, relative, attr::parseData);
      } else { // cancel
        return;
      }
    }
  }

}
