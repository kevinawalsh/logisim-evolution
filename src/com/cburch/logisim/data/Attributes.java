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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import com.bric.swing.ColorPicker;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.generic.ComboBox;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.FontUtil;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.JInputComponent;
import com.cburch.logisim.util.JInputDialog;
import com.cburch.logisim.util.StringGetter;
import com.connectina.swing.fontchooser.JFontChooser;

public class Attributes {
  private static class BooleanAttribute extends OptionAttribute<Boolean> {
    private static Boolean[] vals = { Boolean.TRUE, Boolean.FALSE };

    private BooleanAttribute(String name, StringGetter disp) {
      super(name, disp, vals);
    }

    @Override
    public Boolean parse(String value) {
      Boolean b = Boolean.valueOf(value);
      return vals[b.booleanValue() ? 0 : 1];
    }

    @Override
    public String toDisplayString(Boolean value) {
      if (value.booleanValue())
        return S.get("booleanTrueOption");
      else
        return S.get("booleanFalseOption");
    }
  }

  public static final class LinkedFile {
    public final File absolute, relative;
    public LinkedFile(File a, File r)  {
      absolute = a ;
      relative = r;
    }

    public LinkedFile(File f, Window source) { // normally f should be absolute here
      this(resolve(f, source), relativize(f, source));
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof LinkedFile))
        return false;
      LinkedFile other = (LinkedFile)o;
      return this.absolute.equals(other.absolute);
    }

    @Override
    public int hashCode() {
      return absolute.hashCode();
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
  }

  private static class FilenameAttribute extends Attribute<LinkedFile> {
    StringGetter dialogTitle;
    FileFilter[] filters;
    public FilenameAttribute(String name, StringGetter desc, StringGetter dialogTitle, FileFilter[] filters) {
      super(name, desc);
      this.dialogTitle = dialogTitle;
      this.filters = filters;
    }
    
    @Override
    public String toDisplayString(LinkedFile value) {
      return value == null ? "" : (value.relative + " [" + value.absolute) + "]";
    }

    @Override
    public String toStandardString(LinkedFile value) {
      return value == null ? "" : value.relative.toString();
    }

    @Override
    public String toStandardStringRelative(LinkedFile value, String outFilename) {
      if (value == null)
        return "";
      if (outFilename == null || outFilename.equals("")) {
        return value.absolute.toString();
      } else {
        try {
          return new File(outFilename).toPath().toRealPath().relativize(value.absolute.toPath().toRealPath()).toString();
        } catch (IOException e) {
          return new File(outFilename).toPath().relativize(value.absolute.toPath()).toString();
        }
      }
    }

    @Override
    public java.awt.Component getCellEditor(Window source, LinkedFile value) {
      return new FilenameChooser((Frame)source, value, dialogTitle, filters);
    }
    
    @Override
    public LinkedFile parse(String value) {
      throw new UnsupportedOperationException("parse filename without source");
      // if (value == null || value.equals(""))
      //   return null;
      // return new LinkedFile(new File(value), (Window)null);
    }

    @Override
    public LinkedFile parseFromUser(Window source, String value) {
      if (value == null || value.equals(""))
        return null;
      // if (!abs.exists())
      //   throw new IllegalArgumentException("File '"+abs+"' can't be found.");
      // if (abs.isDirectory())
      //   throw new IllegalArgumentException("Expected '"+abs+"' to be a file, but it's a directory.");
      return new LinkedFile(new File(value), source);
    }

    @Override
    public LinkedFile parseFromFilesystem(File directory, String value) {
      if (value == null || value.equals(""))
        return null;
      if (directory == null || directory.toString().equals("")) {
        // happens when copy-pasting
        File f = new File(value);
        return new LinkedFile(f, f);
      }
      Path parent = directory.toPath();
      Path abs = parent.resolve(new File(value).toPath());
      Path rel;
      try {
        rel = parent.toRealPath().relativize(abs.toRealPath());
      } catch (IOException ex) {
        rel = parent.relativize(abs);
      }
      return new LinkedFile(abs.toFile(), rel.toFile());
    }

    @Override
    public Domain getDomain() {
      return Domain.ofDescription("file name");
    }

  }

  private static class FilenameChooser extends java.awt.Component implements JInputDialog<LinkedFile> {

    JFileChooser chooser;
    Frame parent;
    LinkedFile result;

    FilenameChooser(Frame parent, LinkedFile file, StringGetter title, FileFilter[] filters) {
      this.parent = parent;
      chooser = JFileChoosers.create();
      chooser.setDialogTitle(title.toString());
      setValue(file);
      if (filters != null && filters.length != 0) {
        for (FileFilter ff : filters)
          chooser.addChoosableFileFilter(ff);
        chooser.setFileFilter(filters[0]);
      }
    }

    @Override
    public void setValue(LinkedFile file) {
      if (file == null) {
        chooser.setSelectedFile(null);
        return;
      } 
      if (file.absolute.isDirectory()) {
        chooser.setCurrentDirectory(file.absolute);
      } else {
        chooser.setCurrentDirectory(file.absolute.getParentFile());
        chooser.setSelectedFile(file.absolute);
      }
    }

    @Override
    public LinkedFile getValue() {
      return result;
    }

    public void setVisible(boolean b) {
      if (!b)
        return;
      int choice = chooser.showOpenDialog(parent);
      if (choice == JFileChooser.APPROVE_OPTION) {
        File f = chooser.getSelectedFile();
        LinkedFile lf = new LinkedFile(f, parent);
        try {
          // sanity check: try to read file data
          Files.readAllBytes(lf.absolute.toPath());
          result = lf;
        } catch (IOException e) {
          choice = JOptionPane.showConfirmDialog(parent,
              S.get("fileUnreadableConfirmation"),
              S.get("fileUnreadableTitle"),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.WARNING_MESSAGE);
          if (choice == JOptionPane.OK_OPTION)
            result = lf;
        }
      }
    }
  }

  public static Color parseColor(String value) throws NumberFormatException {
    if (value.startsWith("#") && value.length()-1 == 8) {
      int r = Integer.parseInt(value.substring(1, 3), 16);
      int g = Integer.parseInt(value.substring(3, 5), 16);
      int b = Integer.parseInt(value.substring(5, 7), 16);
      int a = Integer.parseInt(value.substring(7, 9), 16);
      return new Color(r, g, b, a);
    } else if (value.startsWith("#") && value.length()-1 == 6) {
      int r = Integer.parseInt(value.substring(1, 3), 16);
      int g = Integer.parseInt(value.substring(3, 5), 16);
      int b = Integer.parseInt(value.substring(5, 7), 16);
      return new Color(r, g, b);
    } else if (value.startsWith("#") && value.length()-1 == 4) {
      int r = Integer.parseInt(value.substring(1, 2), 16) * 0x11;
      int g = Integer.parseInt(value.substring(2, 3), 16) * 0x11;
      int b = Integer.parseInt(value.substring(3, 4), 16) * 0x11;
      int a = Integer.parseInt(value.substring(4, 5), 16) * 0x11;
      return new Color(r, g, b, a);
    } else if (value.startsWith("#") && value.length()-1 == 3) {
      int r = Integer.parseInt(value.substring(1, 2), 16) * 0x11;
      int g = Integer.parseInt(value.substring(2, 3), 16) * 0x11;
      int b = Integer.parseInt(value.substring(3, 4), 16) * 0x11;
      return new Color(r, g, b);
    } else {
      return Color.decode(value);
    }
  }

  private static class ColorAttribute extends Attribute<Color> {
    public ColorAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public java.awt.Component getCellEditor(Color value) {
      Color init = value == null ? Color.BLACK : value;
      return new ColorChooser(init);
    }

    private String hex(int value) {
      if (value >= 16)
        return Integer.toHexString(value);
      else
        return "0" + Integer.toHexString(value);
    }

    @Override
    public Color parse(String value) {
      return parseColor(value);
    }

    @Override
    public String toDisplayString(Color value) {
      return toStandardString(value);
    }

    @Override
    public Object toDisplayObject(Color value) {
      return value;
    }

    @Override
    public String toStandardString(Color c) {
      String ret = "#" + hex(c.getRed()) + hex(c.getGreen())
          + hex(c.getBlue());
      return c.getAlpha() == 255 ? ret : ret + hex(c.getAlpha());
    }

    @Override
    public Domain getDomain() { return Domain.ofType("color"); }
  }

  private static class ColorChooser extends ColorPicker
    implements JInputComponent<Color> {
    private static final long serialVersionUID = 1L;

    ColorChooser(Color initial) {
      if (initial != null)
        setColor(initial);
      setOpacityVisible(true);
    }

    public Color getValue() {
      return getColor();
    }

    public void setValue(Color value) {
      setColor(value);
    }
  }

  private static class DirectionAttribute extends OptionAttribute<Direction> {
    private static Direction[] vals = { Direction.NORTH, Direction.SOUTH,
      Direction.EAST, Direction.WEST, };

    public DirectionAttribute(String name, StringGetter disp) {
      super(name, disp, vals);
    }

    @Override
    public Direction parse(String value) {
      return Direction.parse(value);
    }

    @Override
    public String toDisplayString(Direction value) {
      return value == null ? "???" : value.toDisplayString();
    }
  }

  private static class DoubleAttribute extends Attribute<Double> {
    private DoubleAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public Double parse(String value) {
      return Double.valueOf(value);
    }

    @Override
    public Domain getDomain() { return Domain.ofType("double"); }
  }

  private static class DoubleRangeAttribute extends Attribute<Double> {
    double start, initial, end;

    private DoubleRangeAttribute(String name, StringGetter disp,
        double start, double initial, double end) {
      super(name, disp);
      this.start = start;
      this.initial = initial;
      this.end = end;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public java.awt.Component getCellEditor(Double value) {
      return super.getCellEditor(value == null ? initial : value);
    }

    @Override
    public Double parse(String value) {
      double v = (double) Double.parseDouble(value);
      if (v < start)
        throw new NumberFormatException("value too small, must be at least " + start);
      if (v > end)
        throw new NumberFormatException("value too large, can't exceed " + end);
      return Double.valueOf(v);
    }

    @Override
    public Domain getDomain() { return Domain.ofDoubleRange(start, end); }
  }

  private static class FontAttribute extends Attribute<Font> {
    private FontAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public java.awt.Component getCellEditor(Font value) {
      return value == null ? new FontChooser() : new FontChooser(value);
    }

    @Override
    public Font parse(String value) {
      return Font.decode(value);
    }

    @Override
    public String toDisplayString(Font f) {
      if (f == null)
        return "???";
      return f.getFamily() + " "
          + FontUtil.toStyleDisplayString(f.getStyle()) + " "
          + f.getSize();
    }

    @Override
    public String toStandardString(Font f) {
      return f.getFamily() + " "
          + FontUtil.toStyleStandardString(f.getStyle()) + " "
          + f.getSize();
    }

    @Override
    public Domain getDomain() { return Domain.ofType("font"); }
  }

  private static class FontChooser extends JFontChooser
    implements JInputComponent {
    private static final long serialVersionUID = 1L;

    FontChooser() {
      super();
    }

    FontChooser(Font initial) {
      super(initial);
    }

    public Object getValue() {
      return getSelectedFont();
    }

    public void setValue(Object value) {
      setSelectedFont((Font) value);
    }
  }

  public static int parseInteger(String value) {
    value = value.toLowerCase();
    if (value.startsWith("0x")) {
      value = value.substring(2);
      return Integer.valueOf((int) Long.parseLong(value, 16));
    } else if (value.startsWith("0b")) {
      value = value.substring(2);
      return Integer.valueOf((int) Long.parseLong(value, 2));
    } else if (value.startsWith("0") && value.length() > 1) {
      value = value.substring(1);
      return Integer.valueOf((int) Long.parseLong(value, 8));
    } else {
      return Integer.valueOf((int) Long.parseLong(value, 10));
    }
  }

  private static class HexIntegerAttribute extends Attribute<Integer> {
    private HexIntegerAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public Integer parse(String value) {
      return parseInteger(value);
    }

    @Override
    public String toDisplayString(Integer value) {
      int val = value.intValue();
      return "0x" + Integer.toHexString(val);
    }

    @Override
    public String toStandardString(Integer value) {
      return toDisplayString(value);
    }

    @Override
    public Domain getDomain() { return Domain.ofDescription("integer in hex notation"); }
  }

  private static class IntegerAttribute extends Attribute<Integer> {
    private IntegerAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public Integer parse(String value) {
      return Integer.valueOf(value);
    }

    @Override
    public Domain getDomain() { return Domain.ofType("int"); }
  }

  private static class IntegerRangeAttribute extends Attribute<Integer> {
    Integer[] options = null;
    int start;
    int end;

    private IntegerRangeAttribute(String name, StringGetter disp,
        int start, int end) {
      super(name, disp);
      this.start = start;
      this.end = end;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public java.awt.Component getCellEditor(Integer value) {
      if (end - start > 32 - 1) {
        return super.getCellEditor(value == null ? start : value);
      } else {
        if (options == null) {
          options = new Integer[end - start + 1];
          for (int i = start; i <= end; i++) {
            options[i - start] = Integer.valueOf(i);
          }
        }
        ComboBox combo = new ComboBox<>(options);
        if (value == null)
          combo.setSelectedIndex(-1);
        else
          combo.setSelectedItem(value);
        return combo;
      }
    }

    @Override
    public Integer parse(String value) {
      int v = (int) Long.parseLong(value);
      if (v < start)
        throw new NumberFormatException("integer too small");
      if (v > end)
        throw new NumberFormatException("integer too large");
      return Integer.valueOf(v);
    }

    @Override
    public Domain getDomain() { return Domain.ofIntRange(start, end); }
  }

  private static class LocationAttribute extends Attribute<Location> {
    public LocationAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public Location parse(String value) {
      return Location.parse(value);
    }

    @Override
    public Domain getDomain() { return Domain.ofDescription("integer x, y coordinates"); }
  }

  private static class OptionAttribute<V> extends Attribute<V> {
    private V[] vals;

    private OptionAttribute(String name, StringGetter disp, V[] vals) {
      super(name, disp);
      this.vals = vals;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public java.awt.Component getCellEditor(Object value) {
      ComboBox combo = new ComboBox<>(vals);
      combo.setRenderer(new OptionComboRenderer<V>(this));
      if (value == null)
        combo.setSelectedIndex(-1);
      else
        combo.setSelectedItem(value);
      return combo;
    }

    @Override
    public V parse(String value) {
      for (int i = 0; i < vals.length; i++) {
        if (value.equals(vals[i].toString())) {
          return vals[i];
        }
      }
      throw new NumberFormatException("value not among choices");
    }

    @Override
    public String toDisplayString(V value) {
      if (value instanceof AttributeOptionInterface) {
        return ((AttributeOptionInterface) value).toDisplayString();
      } else {
        return value.toString();
      }
    }

    @Override
    public Domain getDomain() {
      return Domain.ofList(vals);
    }
  }

  private static class OptionComboRenderer<V> extends BasicComboBoxRenderer {
    private static final long serialVersionUID = 1L;
    Attribute<V> attr;

    OptionComboRenderer(Attribute<V> attr) {
      this.attr = attr;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      Component ret = super.getListCellRendererComponent(list, value,
          index, isSelected, cellHasFocus);
      if (ret instanceof JLabel) {
        @SuppressWarnings("unchecked")
        V val = (V) value;
        ((JLabel) ret).setText(
          value == null ? "" : attr.toDisplayString(val));
      }
      return ret;
    }
  }

  private static class StringAttribute extends Attribute<String> {
    private StringAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public String parse(String value) {
      return value;
    }
    @Override
    public Domain getDomain() { return Domain.ofType("string"); }
  }

  public static Attribute<BitWidth> forBitWidth(String name) {
    return forBitWidth(name, getter(name));
  }

  public static Attribute<BitWidth> forBitWidth(String name, int min, int max) {
    return forBitWidth(name, getter(name), min, max);
  }

  public static Attribute<BitWidth> forBitWidth(String name, StringGetter disp) {
    return new BitWidth.Attribute(name, disp);
  }

  public static Attribute<BitWidth> forBitWidth(String name,
      StringGetter disp, int min, int max) {
    return new BitWidth.Attribute(name, disp, min, max);
  }

  public static Attribute<Boolean> forBoolean(String name) {
    return forBoolean(name, getter(name));
  }

  public static Attribute<Boolean> forBoolean(String name, StringGetter disp) {
    return new BooleanAttribute(name, disp);
  }
  
  public static Attribute<LinkedFile> forFilename(String name, StringGetter disp,
      StringGetter dialogTitle, FileFilter... filters) {
    return new FilenameAttribute(name, disp, dialogTitle, filters);
  }

  public static Attribute<Color> forColor(String name) {
    return forColor(name, getter(name));
  }

  public static Attribute<Color> forColor(String name, StringGetter disp) {
    return new ColorAttribute(name, disp);
  }

  public static Attribute<Direction> forDirection(String name) {
    return forDirection(name, getter(name));
  }

  public static Attribute<Direction> forDirection(String name,
      StringGetter disp) {
    return new DirectionAttribute(name, disp);
  }

  public static Attribute<Double> forDouble(String name) {
    return forDouble(name, getter(name));
  }

  public static Attribute<Double> forDouble(String name, StringGetter disp) {
    return new DoubleAttribute(name, disp);
  }

  public static Attribute<Double> forDoubleRange(String name,
      StringGetter disp, double start, double initial, double end) {
    return new DoubleRangeAttribute(name, disp, start, initial, end);
  }

  public static Attribute<Font> forFont(String name) {
    return forFont(name, getter(name));
  }

  public static Attribute<Font> forFont(String name, StringGetter disp) {
    return new FontAttribute(name, disp);
  }

  public static Attribute<Integer> forHexInteger(String name) {
    return forHexInteger(name, getter(name));
  }

  public static Attribute<Integer> forHexInteger(String name,
      StringGetter disp) {
    return new HexIntegerAttribute(name, disp);
  }

  public static Attribute<Integer> forInteger(String name) {
    return forInteger(name, getter(name));
  }

  public static Attribute<Integer> forInteger(String name, StringGetter disp) {
    return new IntegerAttribute(name, disp);
  }

  public static Attribute<Integer> forIntegerRange(String name, int start,
      int end) {
    return forIntegerRange(name, getter(name), start, end);
  }

  public static Attribute<Integer> forIntegerRange(String name,
      StringGetter disp, int start, int end) {
    return new IntegerRangeAttribute(name, disp, start, end);
  }

  public static Attribute<Location> forLocation(String name) {
    return forLocation(name, getter(name));
  }

  public static Attribute<Location> forLocation(String name, StringGetter disp) {
    return new LocationAttribute(name, disp);
  }

  public static Attribute<?> forOption(String name, Object[] vals) {
    return forOption(name, getter(name), vals);
  }

  public static <V> Attribute<V> forOption(String name, StringGetter disp,
      V[] vals) {
    return new OptionAttribute<V>(name, disp, vals);
  }

  //
  // methods with display name == standard name
  //
  public static Attribute<String> forString(String name) {
    return forString(name, getter(name));
  }

  //
  // methods with internationalization support
  //
  public static Attribute<String> forString(String name, StringGetter disp) {
    return new StringAttribute(name, disp);
  }

  private static StringGetter getter(String s) {
    return S.unlocalized(s);
  }

  private Attributes() {
  }
}
