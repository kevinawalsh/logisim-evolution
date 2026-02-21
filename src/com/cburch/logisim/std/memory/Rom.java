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

package com.cburch.logisim.std.memory;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import javax.swing.JLabel;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;

public class Rom extends Mem {
  static class ContentsAttribute extends Attribute<MemContents> {
    public ContentsAttribute() {
      super("contents", S.getter("romContentsAttr"));
    }

    @Override
    public java.awt.Component getCellEditor(Window source, MemContents value) {
      if (source instanceof Frame) {
        Project proj = ((Frame) source).getProject();
        RomAttributes.register(value, proj);
      }
      ContentsCell ret = new ContentsCell(source, value);
      ret.mouseClicked(null);
      return ret;
    }

    @Override
    public MemContents parse(String value) {
      int lineBreak = value.indexOf('\n');
      String first = lineBreak < 0 ? value : value.substring(0, lineBreak);
      String rest = lineBreak < 0 ? "" : value.substring(lineBreak + 1);
      StringTokenizer toks = new StringTokenizer(first);
      try {
        String header = toks.nextToken();
        if (!header.equals("addr/data:"))
          return null;
        int addr = Integer.parseInt(toks.nextToken());
        int data = Integer.parseInt(toks.nextToken());
        return HexFile.parseFromCircFile(rest, addr, data);
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      } catch (NumberFormatException e) {
        e.printStackTrace();
        return null;
      } catch (NoSuchElementException e) {
        e.printStackTrace();
        return null;
      }
    }

    @Override
    public String toDisplayString(MemContents value) {
      return S.get("romContentsValue");
    }

    @Override
    public String toStandardString(MemContents state) {
      int addr = state.getLogLength();
      int data = state.getWidth();
      String contents = HexFile.saveToStringForCircFile(state);
      return "addr/data: " + addr + " " + data + "\n" + contents;
    }
  }

  @SuppressWarnings("serial")
  private static class ContentsCell extends JLabel implements MouseListener {
    Window source;
    MemContents contents;

    ContentsCell(Window source, MemContents contents) {
      super(S.get("romContentsValue"));
      this.source = source;
      this.contents = contents;
      addMouseListener(this);
    }

    public void mouseClicked(MouseEvent e) {
      if (contents == null)
        return;
      Project proj = source instanceof Frame ? ((Frame) source)
          .getProject() : null;
      HexFrame frame = RomAttributes.getHexFrame(contents, proj, null);
      frame.setVisible(true);
      frame.toFront();
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }
  }
  
  static final AttributeOption RECT = new AttributeOption("rect", S.getter("romProportionsRect"));
  static final AttributeOption WIDE = new AttributeOption("wide", S.getter("romProportionsWide"));
  static final AttributeOption TALL = new AttributeOption("tall", S.getter("romProportionsTall"));
  static final Attribute<AttributeOption> ATTR_PROPORTIONS = Attributes.forOption(
      "proportions", S.getter("romProportions"), new AttributeOption[] { RECT, WIDE, TALL });

  public static Attribute<MemContents> CONTENTS_ATTR = new ContentsAttribute();

  public Rom() {
    super("ROM", S.getter("romComponent"), 0);
    setIconName("rom.gif");
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    super.configureNewInstance(instance);
    MemContents newContents = getMemContents(instance);
    MemListener listener = new MemListener(instance);
    newContents.addHexModelWeakListener(instance, listener);
    instance.addAttributeListener();
  }

  @Override
  void configurePorts(Instance instance) {
    int dataLines = Mem.lineSize(instance.getAttributeSet());
    Port[] ps = new Port[MEM_INPUTS + dataLines-1];
    ps[ADDR] = new Port(0, 10, Port.INPUT, ADDR_ATTR);
    ps[ADDR].setToolTip(S.getter("memAddrTip"));
    int symwidth = SymbolWidth;
    int ypos = getControlHeight(instance.getAttributeSet());
    if (instance.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      AttributeOption p = instance.getAttributeValue(ATTR_PROPORTIONS);
      AttributeOption lines = instance.getAttributeValue(Mem.LINE_ATTR);
      if (p == TALL)
        symwidth /= 2;
      else if (p == WIDE)
        ypos = (lines == QUAD) ? 0 : 10;
      else if (instance.getAttributeValue(Mem.DATA_ATTR).getWidth() == 1)
        ypos += 10;
    } else {
      if (instance.getAttributeValue(Mem.DATA_ATTR).getWidth() == 1)
        ypos += 10;
    }
    ps[DATA] = new Port(symwidth + 40, ypos, Port.OUTPUT, DATA_ATTR);
    ps[DATA].setToolTip(S.getter("memDataTip"));
    for (int i = 1; i < dataLines; i++) {
      ps[MEM_INPUTS+i-1] = new Port(symwidth + 40, ypos+i*10, Port.OUTPUT, DATA_ATTR);
      ps[MEM_INPUTS+i-1].setToolTip(S.getter("memDataTip"+i));
    }
    instance.setPorts(ps);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new RomAttributes();
  }

  private void DrawControlBlock(InstancePainter painter, int xpos, int ypos) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    AttributeSet attrs = painter.getAttributeSet();
    g.drawLine(xpos + 20, ypos, xpos + 20 + SymbolWidth, ypos);
    g.drawLine(xpos + 20, ypos, xpos + 20, ypos + getControlHeight(attrs)
        - 10);
    g.drawLine(xpos + 20 + SymbolWidth, ypos, xpos + 20 + SymbolWidth, ypos
        + getControlHeight(attrs) - 10);
    g.drawLine(xpos + 20, ypos + getControlHeight(attrs) - 10, xpos + 30,
        ypos + getControlHeight(attrs) - 10);
    g.drawLine(xpos + 20 + SymbolWidth - 10, ypos + getControlHeight(attrs)
        - 10, xpos + 20 + SymbolWidth, ypos + getControlHeight(attrs)
        - 10);
    g.drawLine(xpos + 30, ypos + getControlHeight(attrs) - 10, xpos + 30,
        ypos + getControlHeight(attrs));
    g.drawLine(xpos + 20 + SymbolWidth - 10, ypos + getControlHeight(attrs)
        - 10, xpos + 20 + SymbolWidth - 10, ypos
        + getControlHeight(attrs));
    GraphicsUtil.drawCenteredText(g,
        "ROM " + GetSizeLabel(painter.getAttributeValue(Mem.ADDR_ATTR) .getWidth())
        + " x "
        + painter.getAttributeValue(Mem.DATA_ATTR).getWidth(),
        xpos + (SymbolWidth / 2) + 20, ypos + 6);
    GraphicsUtil.switchToWidth(g, 1);
    DrawAddress(painter, xpos, ypos + 10,
        painter.getAttributeValue(Mem.ADDR_ATTR).getWidth());
  }

  private void DrawDataBlock(InstancePainter painter, int xpos, int ypos,
      int bit, int NrOfBits) {
    int realypos = ypos + getControlHeight(painter.getAttributeSet()) + bit
        * 20;
    int realxpos = xpos + 20;
    boolean FirstBlock = bit == 0;
    boolean LastBlock = bit == (NrOfBits - 1);
    Graphics2D g = painter.getGraphics();
    Font font = g.getFont();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(realxpos, realypos, SymbolWidth, 20);
    GraphicsUtil.drawText(g, "A", realxpos + SymbolWidth - 3,
        realypos + 10, GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_FIRST);
    painter.drawPort(DATA);
    int lineSize = Mem.lineSize(painter.getAttributeSet());
    for (int i = 1; i < lineSize; i++)
      painter.drawPort(MEM_INPUTS+i-1);
    if (FirstBlock && LastBlock) {
      GraphicsUtil.switchToWidth(g, 3);
      g.drawLine(realxpos + SymbolWidth + 1, realypos + 10, realxpos
          + SymbolWidth + 20, realypos + 10);
      return;
    }
    g.drawLine(realxpos + SymbolWidth, realypos + 10, realxpos
        + SymbolWidth + 10, realypos + 10);
    g.drawLine(realxpos + SymbolWidth + 10, realypos + 10, realxpos
        + SymbolWidth + 15, realypos + 5);
    g.setFont(font.deriveFont(7.0f));
    GraphicsUtil
        .drawText(g, Integer.toString(bit), realxpos + SymbolWidth + 3,
            realypos + 7, GraphicsUtil.H_LEFT,
            GraphicsUtil.V_BASELINE);
    g.setFont(font);
    GraphicsUtil.switchToWidth(g, 5);
    if (FirstBlock) {
      g.drawLine(realxpos + SymbolWidth + 15, realypos + 5, realxpos
          + SymbolWidth + 15, realypos + 20);
      g.drawLine(realxpos + SymbolWidth + 15, realypos + 5, realxpos
          + SymbolWidth + 20, realypos);
    } else if (LastBlock) {
      g.drawLine(realxpos + SymbolWidth + 15, realypos, realxpos
          + SymbolWidth + 15, realypos + 10);
    } else
      g.drawLine(realxpos + SymbolWidth + 15, realypos, realxpos
          + SymbolWidth + 15, realypos + 20);
    GraphicsUtil.switchToWidth(g, 1);
  }

  public int getControlHeight(AttributeSet attrs) {
    return 60;
  }

  @Override
  HexFrame getHexFrame(Project proj, Instance instance, CircuitState state) {
    return RomAttributes.getHexFrame(getMemContents(instance), proj, instance);
  }

  public static MemContents getMemContents(Instance instance) {
    return instance.getAttributeValue(CONTENTS_ATTR);
  }

  public static void closeHexFrame(Component c) {
    if (!(c instanceof InstanceComponent))
      return;
    Instance instance = ((InstanceComponent)c).getInstance();
    RomAttributes.closeHexFrame(getMemContents(instance));
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int len = attrs.getValue(Mem.DATA_ATTR).getWidth();
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      AttributeOption p = attrs.getValue(ATTR_PROPORTIONS);
      if (p == WIDE)
        return Bounds.create(0, 0, SymbolWidth + 40, 40);
      else if (p == TALL)
        return Bounds.create(0, 0, SymbolWidth/2 + 40, 140);
      else
        return Bounds.create(0, 0, SymbolWidth + 40, 140);
    } else {
      return Bounds.create(0, 0, SymbolWidth + 40,
          getControlHeight(attrs) + 20 * len);
    }
  }

  @Override
  MemState getState(Instance instance, CircuitState state) {
    MemState ret = (MemState) instance.getDataAsCustom(state);
    if (ret == null) {
      MemContents contents = getMemContents(instance);
      ret = new MemState(contents);
      instance.setData(state, ret);
    }
    return ret;
  }

  @Override
  MemState getState(InstanceState state) {
    MemState ret = (MemState) state.getDataAsCustom();
    if (ret == null) {
      MemContents contents = getMemContents(state.getInstance());
      ret = new MemState(contents);
      state.setData(ret);
    }
    return ret;
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    if (RomHDLGenerator.supports(ctx.lang, ctx.attrs, ctx.vendor)) // fixme
      return new RomHDLGenerator(ctx);
    else
      return null;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    // if (attr == StdAttr.APPEARANCE) {
    //   boolean classic = StdAttr.APPEAR_CLASSIC.equals(instance.getAttributeValue(StdAttr.APPEARANCE));
    //   int lineSize = Mem.lineSize(instance);
    //   if (!classic) {
    //     if (lineSize > 1) {
    //       instance.getAttributeSet().setValue(Mem.LINE_SIZE, Mem.SINGLE);
    //       super.instanceAttributeChanged(instance, Mem.LINE_SIZE);
    //     }
    //     instance.setAttributeReadOnly(Mem.LINE_SIZE, true);
    //     super.instanceAttributeChanged(instance, Mem.LINE_SIZE);
    //   } else {
    //     if (instance.getAttributeSet().isReadOnly(Mem.LINE_SIZE)) {
    //       instance.setAttributeReadOnly(Mem.LINE_SIZE, false);
    //       super.instanceAttributeChanged(instance, Mem.LINE_SIZE);
    //     }
    //   }
    // }
    if (attr == Mem.DATA_ATTR || attr == StdAttr.APPEARANCE || attr == ATTR_PROPORTIONS) {
      instance.recomputeBounds();
      configurePorts(instance);
    } else if (attr == Mem.LINE_ATTR) {
      configurePorts(instance);
    }
  }

  public void DrawRomClassic(InstancePainter painter) {
    DrawMemClassic(painter);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      DrawRomClassic(painter);
    } else {
      Graphics2D g = painter.getGraphics();
      Bounds bds = painter.getNominalBounds();

      painter.drawLabel();

      int xpos = bds.getX();
      int ypos = bds.getY();
      int NrOfBits = painter.getAttributeValue(Mem.DATA_ATTR).getWidth();
      /* draw control */
      DrawControlBlock(painter, xpos, ypos);
      /* draw body */
      for (int i = 0; i < NrOfBits; i++) {
        DrawDataBlock(painter, xpos, ypos, i, NrOfBits);
      }
      /* Draw contents */
      if (painter.getShowState()) {
        int dataLines = Mem.lineSize(painter.getAttributeSet());
        MemState state = getState(painter);
        state.paint(painter.getGraphics(), bds.getX(), bds.getY(),
            25, getControlHeight(painter.getAttributeSet()) + 5,
            Mem.SymbolWidth - 20, 20 * NrOfBits - 10, false, dataLines);
      }
    }
  }

  @Override
  public void propagate(InstanceState state) {
    MemState myState = getState(state);
    BitWidth dataBits = state.getAttributeValue(DATA_ATTR);
    int dataLines = Mem.lineSize(state.getAttributeSet());

    Value addrValue = state.getPortValue(ADDR);

    int addr = addrValue.toIntValue();
    if (addrValue.isErrorValue() || (addrValue.isFullyDefined() && addr < 0)) {
      state.setPort(DATA, Value.createError(dataBits), DELAY);
      for (int i = 1; i < dataLines; i++)
        state.setPort(MEM_INPUTS+i-1, Value.createError(dataBits), DELAY);
      return;
    }
    if (!addrValue.isFullyDefined()) {
      state.setPort(DATA, Value.createUnknown(dataBits), DELAY);
      for (int i = 1; i < dataLines; i++)
        state.setPort(MEM_INPUTS+i-1, Value.createUnknown(dataBits), DELAY);
      return;
    }
    if (addr != myState.getCurrent()) {
      myState.setCurrent(addr);
      myState.scrollToShow(addr);
    }
    if (addr % dataLines != 0) { // misaligned access
      state.setPort(DATA, Value.createError(dataBits), DELAY);
      for (int i = 1; i < dataLines; i++)
        state.setPort(MEM_INPUTS+i-1, Value.createError(dataBits), DELAY);
      return;
    }
    int val = myState.getContents().get(addr);
    state.setPort(DATA, Value.createKnown(dataBits, val), DELAY);
    for (int i = 1; i < dataLines; i++) {
      val = myState.getContents().get(addr+i);
      state.setPort(MEM_INPUTS+i-1, Value.createKnown(dataBits, val), DELAY);
    }
  }

}
