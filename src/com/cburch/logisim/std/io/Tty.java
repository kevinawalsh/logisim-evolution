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
import java.awt.FontMetrics;
import java.awt.Graphics;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Tty extends InstanceFactory {
  private static int getColumnCount(Object val) {
    if (val instanceof Integer)
      return ((Integer) val).intValue();
    else
      return 16;
  }

  private static int getRowCount(Object val) {
    if (val instanceof Integer)
      return ((Integer) val).intValue();
    else
      return 4;
  }

  static final int CLR = 0;
  static final int CK = 1;
  static final int WE = 2;
  static final int IN = 3;

  private static final int BORDER = 5;
  private static final int ROW_HEIGHT = 15;

  private static final int COL_WIDTH = 7;

  private static final Color DEFAULT_BACKGROUND = new Color(0, 0, 0, 64);
  private static final Font DEFAULT_FONT = new Font("monospaced", Font.PLAIN, 12);

  private static final Attribute<Integer> ATTR_COLUMNS = Attributes
      .forIntegerRange("cols", S.getter("ttyColsAttr"), 1, 120);

  private static final Attribute<Integer> ATTR_ROWS = Attributes
      .forIntegerRange("rows", S.getter("ttyRowsAttr"), 1, 48);

  public static final Attribute<Integer> ATTR_WIDTH = Attributes
      .forIntegerRange("asciiwidth", S.getter("asciiWidthAttr"),
          7, 8);

  public Tty() {
    super("TTY", S.getter("ttyComponent"));
    setAttributes(new Attribute[] {
      ATTR_ROWS, ATTR_COLUMNS, ATTR_WIDTH,
          StdAttr.EDGE_TRIGGER, Io.ATTR_COLOR, Io.ATTR_BACKGROUND,
          StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT},
          new Object[] {
            Integer.valueOf(8), Integer.valueOf(32), Integer.valueOf(7),
            StdAttr.TRIG_RISING, Color.BLACK, DEFAULT_BACKGROUND,
            "", Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT});
    setIconName("tty.gif");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
  }

  public static int getWidth(Object widthAttr) {
    if (widthAttr instanceof Integer)
      return ((Integer) widthAttr).intValue();
    else
      return 7;
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    instance.computeLabelTextField(0);
  }

  private void updatePorts(Instance instance) {
    int asciiWidth = getWidth(instance.getAttributeValue(ATTR_WIDTH));
    Port[] ps = new Port[4];
    ps[CLR] = new Port(20, 10, Port.INPUT, 1);
    ps[CK] = new Port(0, 0, Port.INPUT, 1);
    ps[WE] = new Port(10, 10, Port.INPUT, 1);
    ps[IN] = new Port(0, -10, Port.INPUT, asciiWidth);
    ps[CLR].setToolTip(S.getter("ttyClearTip"));
    ps[CK].setToolTip(S.getter("ttyClockTip"));
    ps[WE].setToolTip(S.getter("ttyEnableTip"));
    ps[IN].setToolTip(S.getter("ttyInputTip"));
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int rows = getRowCount(attrs.getValue(ATTR_ROWS));
    int cols = getColumnCount(attrs.getValue(ATTR_COLUMNS));
    int width = 2 * BORDER + cols * COL_WIDTH;
    int height = 2 * BORDER + rows * ROW_HEIGHT;
    if (width < 30)
      width = 30;
    if (height < 30)
      height = 30;
    return Bounds.create(0, 10 - height, width, height);
  }

  private TtyState getTtyState(InstanceState state) {
    int rows = getRowCount(state.getAttributeValue(ATTR_ROWS));
    int cols = getColumnCount(state.getAttributeValue(ATTR_COLUMNS));
    TtyState ret = (TtyState) state.getDataAsCustom();
    if (ret == null) {
      ret = new TtyState(rows, cols);
      state.setData(ret);
    } else {
      ret.updateSize(rows, cols);
    }
    return ret;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_ROWS || attr == ATTR_COLUMNS) {
      instance.recomputeBounds();
    } else if (attr == ATTR_WIDTH) {
      updatePorts(instance);
    } else if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(0);
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    Bounds bds = painter.getNominalBounds();
    g.drawRoundRect(bds.getX(), bds.getY(), bds.getWidth(),
        bds.getHeight(), 10, 10);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    boolean showState = painter.getShowState();
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    painter.drawClock(CK, Direction.EAST);
    if (painter.shouldDrawColor()) {
      g.setColor(painter.getAttributeValue(Io.ATTR_BACKGROUND));
      g.fillRoundRect(bds.getX(), bds.getY(), bds.getWidth(),
          bds.getHeight(), 10, 10);
    }
    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.BLACK);
    g.drawRoundRect(bds.getX(), bds.getY(), bds.getWidth(),
        bds.getHeight(), 2 * BORDER, 2 * BORDER);
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPort(CLR);
    painter.drawPort(WE);
    painter.drawPort(IN);
    painter.drawLabel();

    int rows = getRowCount(painter.getAttributeValue(ATTR_ROWS));
    int cols = getColumnCount(painter.getAttributeValue(ATTR_COLUMNS));

    if (showState) {
      String[] rowData = new String[rows];
      int curRow;
      int curCol;
      TtyState state = getTtyState(painter);
      synchronized (state) {
        for (int i = 0; i < rows; i++) {
          rowData[i] = state.getRowString(i);
        }
        curRow = state.getCursorRow();
        curCol = state.getCursorColumn();
      }

      g.setFont(DEFAULT_FONT);
      g.setColor(painter.getAttributeValue(Io.ATTR_COLOR));
      FontMetrics fm = g.getFontMetrics();
      int x = bds.getX() + BORDER;
      int y = bds.getY() + BORDER + (ROW_HEIGHT + fm.getAscent()) / 2;
      for (int i = 0; i < rows; i++) {
        g.drawString(rowData[i], x, y);
        if (i == curRow) {
          int x0 = x
              + fm.stringWidth(rowData[i].substring(0, curCol));
          g.drawLine(x0, y - fm.getAscent(), x0, y);
        }
        y += ROW_HEIGHT;
      }
    } else {
      String str = S.fmt("ttyDesc", "" + rows, "" + cols);
      FontMetrics fm = g.getFontMetrics();
      int strWidth = fm.stringWidth(str);
      if (strWidth + BORDER > bds.getWidth()) {
        str = S.get("ttyDescShort");
        strWidth = fm.stringWidth(str);
      }
      int x = bds.getX() + (bds.getWidth() - strWidth) / 2;
      int y = bds.getY() + (bds.getHeight() + fm.getAscent()) / 2;
      g.drawString(str, x, y);
    }
  }

  @Override
  public void propagate(InstanceState circState) {
    Object trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
    TtyState state = getTtyState(circState);
    Value clear = circState.getPortValue(CLR);
    Value clock = circState.getPortValue(CK);
    Value enable = circState.getPortValue(WE);
    Value in = circState.getPortValue(IN);

    synchronized (state) {
      Value lastClock = state.setLastClock(clock);
      if (clear == Value.TRUE) {
        state.clear();
      } else if (enable != Value.FALSE) {
        boolean go;
        if (trigger == StdAttr.TRIG_FALLING) {
          go = lastClock == Value.TRUE && clock == Value.FALSE;
        } else {
          go = lastClock == Value.FALSE && clock == Value.TRUE;
        }
        if (go)
          state.add(in.isFullyDefined() ? (char) in.toIntValue() : '?');
      }
    }
  }

  public void sendToStdout(InstanceState state) {
    TtyState tty = getTtyState(state);
    tty.setSendStdout(true);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "TTY"; }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    if (ctx.lang.equals("VHDL"))
      return new TtyHDLGenerator(ctx);
    else
      return null;
  }

}
