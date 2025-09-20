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

package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.JInputDialog;

public class FileViewer extends InstanceFactory {

  public static final Font FONT = new Font("monospaced", Font.PLAIN, 12);
    
  private static class State implements ComponentData {
    int lines, cols;
    List<String> contents = null;
    HashMap<Integer, Integer> map = null;
    // int offset = 0;
    int firstline = 0, selectedline = -1;

    State(int lines, int cols) {
      this.lines = lines;
      this.cols = cols;
    }

    State(int lines, int cols, List<String> contents) {
      this(lines, cols);
      updateContents(contents);
    }

    State(State other) {
      lines = other.lines;
      cols = other.cols;
      contents = new ArrayList<>(other.contents);
      map = new HashMap<>(other.map);
      firstline = other.firstline;
      selectedline = other.selectedline;
    }

    void updateSize(int lines, int cols) {
      this.lines = lines;
      this.cols = cols;
    }

    void updateContents(List<String> contents) {
      if (this.contents == contents)
        return;
      this.contents = contents;
      selectedline = -1; // why?
      if (contents == null) {
        map = null;
        return;
      }
      map = new HashMap<Integer, Integer>();
      int lineno = 0;
      for (String line : contents) {
        int s = 0, n = line.length();
        while (s < n && Character.isWhitespace(line.charAt(s)))
          s++;
        int e = s;
        int addr = 0, digit;
        while (e < n && (digit = Character.digit(line.charAt(e), 16)) >= 0) {
          addr = 16*addr + digit;
          e++;
        }
        if (e > s) {
          while (e < n && Character.isWhitespace(line.charAt(e)))
            e++;
          if (e < n && line.charAt(e) == ':')
            map.put(addr, lineno);
        }
        lineno++;
      }
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }

    void deselect() {
      selectedline = -1;
    }

    void selectLine(int lineno) {
      if (contents == null) {
        selectedline = -1;
        return;
      }
      int n = contents.size();
      if (lineno < 0) {
        selectedline = -1;
        firstline = 0;
      } else if (lineno >= n) {
        selectedline = -1;
        firstline = Math.max(0, n - lines);
      } else {
        selectedline = lineno;
        firstline = Math.max(0, Math.min(n-lines, lineno - lines/2));
      }
    }

    void selectAddr(int addr) {
      if (map != null && map.containsKey(addr)) {
        selectLine(map.get(addr));
      } else {
        selectedline = -1;
      }
    }

    // void selectOffset(int offset) { }
  }

  private static class LinkedFileState extends State {
    long timestamp = 0;
    Attributes.LinkedFile source = null;

    LinkedFileState(int lines, int cols, Attributes.LinkedFile src) {
      super(lines, cols);
      updateSource(src);
    }

    void updateSource(Attributes.LinkedFile src) {
      if (((this.source == null && src == null)
           || this.source != null && this.source.equals(src))) {
        return;
      }
      source = src;
      if (source == null) {
        timestamp = 0;
        updateContents(null);
        return;
      }
      timestamp = source.absolute.lastModified();
      try {
        List<String> data;
        data = Files.readAllLines(source.absolute.toPath(), StandardCharsets.UTF_8);
        updateContents(data);
      } catch (IOException e) {
        updateContents(null);
        // maybe don't warn?
        System.out.println("file access error: " + source.relative);
        // JOptionPane.showMessageDialog(null, e.getMessage(),
        //     S.get("fileViewerLoadErrorTitle"),
        //     JOptionPane.ERROR_MESSAGE);
      }
    }

    void reloadFile() {
      if (source == null)
        return;
      long ts = source.absolute.lastModified();
      if (ts == timestamp)
        return;
      timestamp = ts;
      try {
        List<String> data;
        data = Files.readAllLines(source.absolute.toPath(), StandardCharsets.UTF_8);
        updateContents(data);
      } catch (IOException e) {
        updateContents(null);
        // maybe don't warn?
        System.out.println("file access error: " + source.relative);
      }
    }

    void selectLine(int lineno) {
      reloadFile();
      super.selectLine(lineno);
    }

    void selectAddr(int addr) {
      reloadFile();
      super.selectAddr(addr);
    }

  }

  private static class FileChooser extends java.awt.Component implements JInputDialog<List<String>> {
    JFileChooser chooser;
    Frame parent;
    List<String> result;

    FileChooser(Frame parent, List<String> r) {
      this.parent = parent;
      this.result = r;
      chooser = JFileChoosers.create();
      chooser.setDialogTitle(S.get("ioFileViewerLoadDialogTitle"));
      chooser.setFileFilter(Loader.TXT_FILTER);
    }

    public void setValue(List<String> r) {
      result = r;
    }

    public List<String> getValue() {
      return result;
    }

    public void setVisible(boolean b) {
      if (!b)
        return;
      int choice = chooser.showOpenDialog(parent);
      if (choice == JFileChooser.APPROVE_OPTION) {
        File f = chooser.getSelectedFile();
        try {
          result = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
          JOptionPane.showMessageDialog(parent, e.getMessage(),
              S.get("fileViewerLoadErrorTitle"),
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  private static class ContentsAttribute extends Attribute<List<String>> {
    public ContentsAttribute() {
      super("contents", S.getter("ioFileViewerContents"));
    }

    @Override
    public java.awt.Component getCellEditor(Window source, List<String> s) {
      return new FileChooser((Frame)source, s);
    }

    @Override
    public String toDisplayString(List<String> value) {
      return S.get("ioFileViewerClickToLoad");
    }

    @Override
    public String toStandardString(List<String> value) {
      return String.join("\n", value);
    }

    @Override
    public List<String> parse(String str) {
      return Arrays.asList(str.split("\\R"));
    }
  }

  static final AttributeOption BY_LINE = new AttributeOption("line",
      S.getter("ioFileViewerByLine"));
  static final AttributeOption BY_ADDR = new AttributeOption("address",
      S.getter("ioFileViewerByAddress"));
  // static final AttributeOption BY_OFFSET = new AttributeOption("offset",
  //     S.getter("ioFileViewerByOffset"));

  static final Attribute<AttributeOption> ATTR_SELECT = 
      Attributes.forOption("select", S.getter("ioFileViewerSelect"),
          new AttributeOption[] { BY_LINE, BY_ADDR, /* BY_OFFSET, */ });

  static final AttributeOption FILE_EMBED = new AttributeOption("embed",
      S.getter("ioFileViewerEmbedFile"));
  static final AttributeOption FILE_LINK = new AttributeOption("link",
      S.getter("ioFileViewerLinkFile"));

  static final Attribute<AttributeOption> ATTR_STORAGE = 
      Attributes.forOption("storage", S.getter("ioFileViewerStorage"),
          new AttributeOption[] { FILE_EMBED, FILE_LINK });

  static final Attribute<Integer> ATTR_LINES =
      Attributes.forIntegerRange("lines", S.getter("ioFileViewerLines"), 1, 60);
  static final Attribute<Integer> ATTR_COLS =
      Attributes.forIntegerRange("cols", S.getter("ioFileViewerCols"), 1, 120);
  public static final Attribute<BitWidth> ATTR_WIDTH = Attributes.forBitWidth(
      "addrWidth", S.getter("ioFileViewerAddrWidth"), 1, 30);

  static final ContentsAttribute ATTR_CONTENTS = new ContentsAttribute();

  static final Attribute<Attributes.LinkedFile> ATTR_FILENAME = Attributes.forFilename("filename",
      S.getter("ioFileViewerFilename"), S.getter("ioFileViewerLoadDialogTitle"),
      Loader.TXT_FILTER);

  public FileViewer() {
    super("FileViewer", S.getter("fileViewerComponent"));
    setKeyConfigurator(new BitWidthConfigurator(ATTR_WIDTH, 1, 30));
    setIconName("fileviewer.gif");
    setPorts(new Port[] { new Port(0, 0, Port.INPUT, ATTR_WIDTH) });
    // setInstancePoker(Poker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new FileViewerAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int lines = attrs.getValue(ATTR_LINES).intValue();
    int cols = attrs.getValue(ATTR_COLS).intValue();
    int s = FONT.getSize();
    int h = (s+2) * lines;
    int w = s * cols * 2 / 3; // assume monospace 12x8 aspect ratio
    h = ((h + 25) / 20) * 20;
    w = ((w + 15) / 10) * 10;
    return Bounds.create(0, -h/2, w, h);
  }

  private State getState(InstanceState state) {
    int lines = state.getAttributeValue(ATTR_LINES).intValue();
    int cols = state.getAttributeValue(ATTR_COLS).intValue();
    if (state.getAttributeValue(ATTR_STORAGE) == FILE_EMBED) {
      List<String> contents = state.getAttributeValue(ATTR_CONTENTS);
      State data = (State) state.getDataAsCustom();
      if (data == null) {
        data = new State(lines, cols, contents);
        state.setData(data);
      } else {
        data.updateSize(lines, cols);
        data.updateContents(contents);
      }
      return data;
    } else {
      Attributes.LinkedFile source = state.getAttributeValue(ATTR_FILENAME);
      State data = (State) state.getDataAsCustom();
      if (data == null || !(data instanceof LinkedFileState)) {
        data = new LinkedFileState(lines, cols, source);
        state.setData(data);
      } else {
        LinkedFileState linkedData = (LinkedFileState) data;
        linkedData.updateSize(lines, cols);
        linkedData.updateSource(source);
      }
      return data;
    }
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_LINES || attr == ATTR_COLS)
      instance.recomputeBounds();
    // if (attr == ATTR_FILENAME)
    //   ...
    instance.fireInvalidated();
  }

  public static final Color SELECTED_LINE_COLOR = new Color(255, 255, 153);

  @Override
  public void paintInstance(InstancePainter painter) {
    State data = getState(painter);
    Bounds bds = painter.getNominalBounds();
    boolean showState = painter.getShowState();
    Graphics g = painter.getGraphics();
    int lines = data.lines;
    int cols = data.cols;
    
    int s = FONT.getSize();
    int h = (s+2) * lines;
    int w = s * cols * 2 / 3; // assume monospace 12x8 aspect ratio
    int hh = ((h + 25) / 20) * 20;
    int ww = ((w + 15) / 10) * 10;

    if (showState) {
      int x = bds.getX() + (ww-w)/2;
      int y = bds.getY() + (hh-h)/2;
      g.setColor(Color.LIGHT_GRAY);
      g.fillRect(x-3, y-3, w+6, h+6);
      Graphics gt = g.create(x-2, y-2, w+4, h+4);
      x = 2;
      y = 2;

      gt.setColor(Color.BLACK);
      gt.setFont(FONT);
      int L = GraphicsUtil.H_LEFT;
      int T = GraphicsUtil.V_TOP;
      int numlines = data.contents == null ? 0 : data.contents.size();
      for (int i = 0; i < lines; i++) {
        int lineno = data.firstline + i;
        if (lineno < 0 || lineno >= numlines)
          break;
        if (lineno == data.selectedline) {
          gt.setColor(SELECTED_LINE_COLOR);
          gt.fillRect(x-2, y, w+4, s+2);
          gt.setColor(Color.BLACK);
        }
        gt.setColor(Color.BLACK);
        GraphicsUtil.drawText(gt, data.contents.get(lineno), x, y, L, T);
        y += s+2;
      }
    }

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Object select = state.getAttributeValue(ATTR_SELECT);
    int lines = state.getAttributeValue(ATTR_LINES).intValue();
    int cols = state.getAttributeValue(ATTR_COLS).intValue();

    State data = getState(state);
    int v = state.getPortValue(0).toIntValue();
    if (v < 0)
      data.deselect();
    else if (select == BY_LINE)
      data.selectLine(v);
    else if (select == BY_ADDR)
      data.selectAddr(v);
    // else
    //   data.selectOffset(v);
  }
}
