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
import java.util.WeakHashMap;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;

public class Ram extends Mem {

  // kwalsh: Notes on RAM behavior.
  //
  // Logisim simulation never used registered/clocked address and
  // enable lines for writing -- when the clock rises, the addressed memory
  // location changes immediately (if WE is high). For reading, Logisim did
  // use a registered/clocked address, enable, and data output, however, sort
  // of. When the clock rises, the value of the addressed memory location
  // would be read and latched, and, if OE is high, also output immediately.
  // But if OE was low, it would not be output immediately. But if OE
  // subsequently went high, even absent a rising clock edge, then the output
  // would change to be the latched value, then stay that way. This is,
  // needless to say, unusual.
  //
  // Old FPGA synthesis behaved differently. The address, data in, and both
  // enables are latched on rising clock edges. Writes happen soon after that:
  // when running in fast "raw" clock mode, writes happen at the next rising
  // clock edge; in slow "fake" clock mode, writes happen at the next rising
  // edge of the internal fast clock, so they will appear to happen just
  // following the slow clock edge. Reads would happen at the same time as
  // writes, but the data output was latched into a register (only if OE is
  // high) that gets updated on the next rising edge of the (fast) clock.
  // So data output for reads would appear during the next cycle for slow fake
  // clock mode and 2 full cycles later for fast raw clock mode.
  // Fake slow clock writing exampe:
  //   slow clock rises, start of cycle 0:
  //       OE is 1
  //       Address is aaa
  //   slow clock rises, start of cycle 1
  //       internal latched OEReg is now 1, AddressReg is now aaa
  //       (soon after)
  //       data is written to memory (if WE was 1)
  //       data is read from memory (pre-update old data, if WE was 1)
  //       (soon after)
  //       data is latched into output register (since OE was 1)
  // Raw fast clock writing exampe:
  //   fast clock rises, start of cycle 0:
  //       OE is 1
  //       Address is aaa
  //   fast clock rises, start of cycle 1
  //       internal latched OEReg is now 1, AddressReg is now aaa
  //   fast clock rises, start of cycle 2
  //       data is written to memory (if WE was 1)
  //       data is read from memory (pre-update old data, if WE was 1)
  //    fast clock rises, start of cycle 3:
  //       data is latched into output register (since OE was 1)
  //
  // New behavior:
  //
  // For Logisim simulation, no internal registers. If you want registers, add
  // them yourself outside the RAM component. If one bidirectional data bus is
  // used, then output enable controls only the tristate for the data bus.
  // Otherwise, there is no output or read enable or any sort. Data output
  // always reflects the current addressed location. When the address changes,
  // the addressed location and the output data changes immediately (or soon
  // after, asynchronously). If you want a registered output, or a tristate
  // output on a two-bus RAM, add one yourself outside the RAM component. If
  // you want registered addresses, add a register yourself outside the RAM
  // component. The clock is used only for writing: when the clock rises, if
  // WE is high, then the addressed location (and data output) is updated
  // immediately or soon after. This behavior is consistent with how registers
  // behave.
  //
  // For FPGA synthesis, it isn't clear that the Cyclone III FPGA, or most
  // other FPGAs, support fully unclocked, asynchronous reads. But we'll try
  // it anyway.

  // static final int DIN = MEM_INPUTS + 0; // only if separate
  // static final int OE = MEM_INPUTS + 0;  // only if not separate
  // static final int WE = MEM_INPUTS + 1;  // always
  // static final int CLK = MEM_INPUTS + 2; // always
  // static final int BE = MEM_INPUTS + 3;  // as many as needed

  public static class Logger extends InstanceLogger {

    @Override
    public String getLogName(InstanceState state, Object option) {
      String Label = state.getAttributeValue(StdAttr.LABEL);
      if (Label.equals("")) {
        Label = null;
      }
      if (option instanceof Integer) {
        String disp = S.get("ramComponent");
        Location loc = state.getInstance().getLocation();
        return (Label == null) ? disp + loc + "[" + option + "]"
            : Label + "[" + option + "]";
      } else {
        return Label;
      }
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      int dataBits = state.getAttributeValue(DATA_ATTR).getWidth();
      int dataLines = lineSize(state.getAttributeSet());
      return BitWidth.create(dataBits * dataLines);
    }

    @Override
    public Object[] getLogOptions(InstanceState state) {
      int addrBits = state.getAttributeValue(ADDR_ATTR).getWidth();
      if (addrBits >= logOptions.length) {
        addrBits = logOptions.length - 1;
      }
      synchronized (logOptions) {
        Object[] ret = logOptions[addrBits];
        if (ret == null) {
          ret = new Object[1 << addrBits];
          logOptions[addrBits] = ret;
          for (int i = 0; i < ret.length; i++) {
            ret[i] = Integer.valueOf(i);
          }
        }
        return ret;
      }
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      if (option instanceof Integer) {
        MemState s = (MemState) state.getDataAsCustom();
        int addr = ((Integer) option).intValue();
        return Value.createKnown(BitWidth.create(s.getDataBits()),
            s.getContents().get(addr));
      } else {
        return Value.NIL;
      }
    }
  }

  public static int numWriteEnables(AttributeSet attrs) {
    int dataLines = lineSize(attrs);
    if (dataLines == 1) return 0;
    else return dataLines;
  }

  static final int AByEnBiDir = MEM_INPUTS + 2;

  static final int AByEnSep = MEM_INPUTS + 3;

  static final int SByEnBiDir = MEM_INPUTS + 3;

  static final int SByEnSep = MEM_INPUTS + 4;

  private static Object[][] logOptions = new Object[9][];

  public Ram() {
    super("RAM", S.getter("ramComponent"), 3);
    setIconName("ram.gif");
    setInstanceLogger(Logger.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    super.configureNewInstance(instance);
    instance.addAttributeListener();
  }

  @Override
  void configurePorts(Instance instance) {
    boolean classic = instance.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC;
    boolean separate = isSeparate(instance.getAttributeSet());
    int enables = numWriteEnables(instance.getAttributeSet());
    int dataLines = lineSize(instance.getAttributeSet());
    // (addr, data0) + (clk+we)
    //    + (dataout0, if separate) + (oe, if not separate)
    //    + (write enables, if multiple data lines)
    //    + (extra dataout lines) + (extra datain lines, if separate)
    int portCount = MEM_INPUTS + 2 + 1 + enables + (separate?2:1)*(dataLines-1);
    Port[] ps = new Port[portCount];
    int DATA1 = MEM_INPUTS; // (dataLines-1) of them
    int DIN0 = DATA1 + (dataLines-1); // (dataLines) of them, only if separate
    int OE = DIN0; // 1, only if not separate
    int CLK = separate ? (DIN0 + dataLines) : (OE + 1); // 1 always
    int WE = CLK+1; // 1, always
    int LE = WE+1; // (datalines) of them, only if multiple data lines
    ps[ADDR] = new Port(0, 10, Port.INPUT, ADDR_ATTR);
    ps[ADDR].setToolTip(S.getter("memAddrTip"));
    ps[CLK] = new Port(0, (classic ? 40 : 70) + enables * 10, Port.INPUT, 1);
    ps[CLK].setToolTip(S.getter("ramClkTip"));
    ps[WE] = new Port(0, (classic ? 30 : 50), Port.INPUT, 1);
    ps[WE].setToolTip(S.getter("ramWETip"));
    int ypos = getControlHeight(instance.getAttributeSet());
    if (!classic && instance.getAttributeValue(Mem.DATA_ATTR).getWidth() == 1)
      ypos += 10;
    if (separate) {
      for (int i = 0; i < dataLines; i++) {
        ps[DIN0+i] = new Port(0, ypos, Port.INPUT, DATA_ATTR);
        ps[DIN0+i].setToolTip(S.getter("ramInTip"+i));
        ps[i==0?DATA:(DATA1+i-1)] = new Port(SymbolWidth + 40, ypos, Port.OUTPUT, DATA_ATTR);
        ps[i==0?DATA:(DATA1+i-1)].setToolTip(S.getter("memDataTip"+i));
        ypos += 10;
      }
    } else {
      ps[OE] = new Port(0, (classic ? 20 : 60), Port.INPUT, 1);
      ps[OE].setToolTip(S.getter("ramOETip"));
      for (int i = 0; i < dataLines; i++) {
        ps[i==0?DATA:(DATA1+i-1)] = new Port(SymbolWidth + 50, ypos, Port.INOUT, DATA_ATTR);
        ps[i==0?DATA:(DATA1+i-1)].setToolTip(S.getter("memDataTip"+i));
        ypos += 10;
      }
    }
    for (int i = 0; i < enables; i++) {
      ypos = (classic ? 40 : 70);
      ps[LE + i] = new Port(0, ypos + i * 10, Port.INPUT, 1);
      ps[LE + i].setToolTip(S.getter("ramLETip"+i));
    }
    instance.setPorts(ps);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new RamAttributes();
  }

  private void DrawConnections(Graphics2D g, int xpos, int ypos,
      boolean singleBit, boolean separate,
      boolean ByteEnabled, int bit) {
    Font font = g.getFont();
    GraphicsUtil.switchToWidth(g, 2);
    if (separate) {
      if (singleBit) {
        g.drawLine(xpos, ypos + 10, xpos + 20, ypos + 10);
        g.drawLine(xpos + 20 + SymbolWidth, ypos + 10, xpos + 40
            + SymbolWidth, ypos + 10);
      } else {
        g.drawLine(xpos + 5, ypos + 5, xpos + 10, ypos + 10);
        g.drawLine(xpos + 10, ypos + 10, xpos + 20, ypos + 10);
        g.drawLine(xpos + 20 + SymbolWidth, ypos + 10, xpos + 30
            + SymbolWidth, ypos + 10);
        g.drawLine(xpos + 30 + SymbolWidth, ypos + 10, xpos + 35
            + SymbolWidth, ypos + 5);
        g.setFont(font.deriveFont(7.0f));
        GraphicsUtil
            .drawText(g, Integer.toString(bit), xpos + 17,
                ypos + 7, GraphicsUtil.H_RIGHT,
                GraphicsUtil.V_BASELINE);
        GraphicsUtil.drawText(g, Integer.toString(bit), xpos + 23
            + SymbolWidth, ypos + 7, GraphicsUtil.H_LEFT,
            GraphicsUtil.V_BASELINE);
        g.setFont(font);
      }
      String ByteIndex = "";
      if (ByteEnabled) {
        int Index = bit / 8;
        ByteIndex = "," + Integer.toString(Index + 4);
      }
      String DLabel = "A,1,3" + ByteIndex + "D";
      String QLabel = "A";
      g.setFont(font.deriveFont(9.0f));
      GraphicsUtil.drawText(g, DLabel, xpos + 23, ypos + 10,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
      GraphicsUtil.drawText(g, QLabel, xpos + 17 + SymbolWidth,
          ypos + 10, GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_FIRST);
      g.setFont(font);
    } else {
      g.drawLine(xpos + 24 + SymbolWidth, ypos + 2, xpos + 28
          + SymbolWidth, ypos + 5);
      g.drawLine(xpos + 24 + SymbolWidth, ypos + 8, xpos + 28
          + SymbolWidth, ypos + 5);
      g.drawLine(xpos + 20 + SymbolWidth, ypos + 5, xpos + 30
          + SymbolWidth, ypos + 5);
      g.drawLine(xpos + 22 + SymbolWidth, ypos + 15, xpos + 26
          + SymbolWidth, ypos + 12);
      g.drawLine(xpos + 22 + SymbolWidth, ypos + 15, xpos + 26
          + SymbolWidth, ypos + 18);
      g.drawLine(xpos + 20 + SymbolWidth, ypos + 15, xpos + 30
          + SymbolWidth, ypos + 15);
      g.drawLine(xpos + 30 + SymbolWidth, ypos + 5, xpos + 30
          + SymbolWidth, ypos + 15);
      g.drawLine(xpos + 30 + SymbolWidth, ypos + 10, xpos + 40
          + SymbolWidth, ypos + 10);
      if (singleBit) {
        g.drawLine(xpos + 40 + SymbolWidth, ypos + 10, xpos + 50
            + SymbolWidth, ypos + 10);
      } else {
        g.drawLine(xpos + 40 + SymbolWidth, ypos + 10, xpos + 45
            + SymbolWidth, ypos + 5);
      }
      g.setFont(font.deriveFont(7.0f));
      GraphicsUtil.drawText(g, Integer.toString(bit), xpos + 33
          + SymbolWidth, ypos + 7, GraphicsUtil.H_LEFT,
          GraphicsUtil.V_BASELINE);
      String ByteIndex = "";
      if (ByteEnabled) {
        int Index = bit / 8;
        ByteIndex = "," + Integer.toString(Index + 4);
      }
      String DLabel = "A,1,3" + ByteIndex + "D";
      String QLabel = "A,2" + "  ";
      g.setFont(font.deriveFont(9.0f));
      GraphicsUtil.drawText(g, DLabel, xpos + 17 + SymbolWidth,
          ypos + 13, GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_FIRST);
      GraphicsUtil.drawText(g, QLabel, xpos + 17 + SymbolWidth, ypos + 5,
          GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_FIRST);
      g.setFont(font);
      GraphicsUtil.switchToWidth(g, 1);
      g.drawLine(xpos + 11 + SymbolWidth, ypos + 4, xpos + 19
          + SymbolWidth, ypos + 4);
      g.drawLine(xpos + 11 + SymbolWidth, ypos + 4, xpos + 15
          + SymbolWidth, ypos + 8);
      g.drawLine(xpos + 15 + SymbolWidth, ypos + 8, xpos + 19
          + SymbolWidth, ypos + 4);
    }
    GraphicsUtil.switchToWidth(g, 1);
  }

  private void DrawControlBlock(InstancePainter painter, int xpos, int ypos) {
    boolean separate = isSeparate(painter.getAttributeSet());
    int dataLines = Mem.lineSize(painter.getAttributeSet());
    int DATA1 = MEM_INPUTS; // (dataLines-1) of them
    int DATAOUT[] = { DATA, DATA1, DATA1+1, DATA1+2 };
    int DIN0 = DATA1+(dataLines-1); // (dataLines) of them, only if separate
    int DATAIN[] = { (separate?DIN0:DATA), (separate?DIN0+1:DATA1), (separate?DIN0+2:DATA1+1), (separate?DIN0+3:DATA1+2) };
    int OE = DIN0; // 1, only if not separate
    int CLK = separate ? (DIN0 + dataLines) : (OE + 1); // 1 always
    int WE = CLK+1; // 1, always
    int LE = WE+1; // (datalines) of them, only if multiple data lines

    Object trigger = painter.getAttributeValue(StdAttr.TRIGGER);
    boolean asynch = trigger.equals(StdAttr.TRIG_HIGH)
        || trigger.equals(StdAttr.TRIG_LOW);
    boolean inverted = trigger.equals(StdAttr.TRIG_FALLING)
        || trigger.equals(StdAttr.TRIG_LOW);
    int enables = numWriteEnables(painter.getAttributeSet());
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
        "RAM " + GetSizeLabel(painter.getAttributeValue(Mem.ADDR_ATTR).getWidth())
        + " x "
        + painter.getAttributeValue(Mem.DATA_ATTR).getWidth(),
        xpos + (SymbolWidth / 2) + 20, ypos + 5);
    g.drawLine(xpos, ypos + 50, xpos + 20, ypos + 50);
    GraphicsUtil.drawText(g, "M1 [Write Enable]", xpos + 33, ypos + 50,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    painter.drawPort(WE);
    g.drawLine(xpos, ypos + 60, xpos + 20, ypos + 60);
    if (!separate) {
      GraphicsUtil.drawText(g, "M2 [Output Enable]", xpos + 33, ypos + 60,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
      painter.drawPort(OE);
    }
    int yoffset = 70 + enables * 10;
    if (inverted) {
      g.drawLine(xpos, ypos + yoffset, xpos + 12, ypos + yoffset);
      g.drawOval(xpos + 12, ypos + yoffset - 4, 8, 8);
    } else {
      g.drawLine(xpos, ypos + yoffset, xpos + 20, ypos + yoffset);
    }
    if (asynch) {
      GraphicsUtil.drawText(g, "E3", xpos + 33, ypos + yoffset,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    } else {
      GraphicsUtil.drawText(g, "C3", xpos + 33, ypos + yoffset,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
      painter.drawClockSymbol(xpos + 20, ypos + yoffset);
    }
    painter.drawPort(CLK);

    GraphicsUtil.switchToWidth(g, 2);
    for (int i = 0; i < enables; i++) {
      g.drawLine(xpos, ypos + 70 + i * 10, xpos + 20, ypos + 70 + i * 10);
      painter.drawPort(LE + i);
      String Label = "M" + (4+i) + " [LineEnable " + i + "]";
      GraphicsUtil.drawText(g, Label, xpos + 33, ypos + 70 + i * 10,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    }

    GraphicsUtil.switchToWidth(g, 1);
    DrawAddress(painter, xpos, ypos + 10,
        painter.getAttributeValue(Mem.ADDR_ATTR).getWidth());
  }

  private void DrawDataBlock(InstancePainter painter, int xpos, int ypos, int bit, int NrOfBits) {
    boolean separate = isSeparate(painter.getAttributeSet());
    int dataLines = Mem.lineSize(painter.getAttributeSet());
    int DATA1 = MEM_INPUTS; // (dataLines-1) of them
    int DATAOUT[] = { DATA, DATA1, DATA1+1, DATA1+2 };
    int DIN0 = DATA1+(dataLines-1); // (dataLines) of them, only if separate
    int DATAIN[] = { (separate?DIN0:DATA), (separate?DIN0+1:DATA1), (separate?DIN0+2:DATA1+1), (separate?DIN0+3:DATA1+2) };
    int OE = DIN0; // 1, only if not separate
    int CLK = separate ? (DIN0 + dataLines) : (OE + 1); // 1 always
    int WE = CLK+1; // 1, always
    int LE = WE+1; // (datalines) of them, only if multiple data lines

    int realypos = ypos + getControlHeight(painter.getAttributeSet()) + bit * 20;
    int realxpos = xpos + 20;
    boolean FirstBlock = bit == 0;
    boolean LastBlock = bit == (NrOfBits - 1);
    Graphics2D g = painter.getGraphics();
    boolean byteEnables = numWriteEnables(painter.getAttributeSet()) > 0;
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(realxpos, realypos, SymbolWidth, 20);
    DrawConnections(g, xpos, realypos, FirstBlock & LastBlock, separate, byteEnables, bit);
    if (FirstBlock) {
      for (int i = 0; i < dataLines; i++)
        painter.drawPort(DATAOUT[i]);
      if (separate)
        for (int i = 0; i < dataLines; i++)
          painter.drawPort(DATAIN[i]);
      if (!LastBlock) {
        GraphicsUtil.switchToWidth(g, 5);
        if (separate) {
          g.drawLine(xpos, realypos, xpos + 5, realypos + 5);
          g.drawLine(xpos + 5, realypos + 5, xpos + 5, realypos + 20);
          g.drawLine(xpos + 40 + SymbolWidth, realypos, xpos + 35
              + SymbolWidth, realypos + 5);
          g.drawLine(xpos + 35 + SymbolWidth, realypos + 5, xpos + 35
              + SymbolWidth, realypos + 20);
        } else {
          g.drawLine(xpos + 50 + SymbolWidth, realypos, xpos + 45
              + SymbolWidth, realypos + 5);
          g.drawLine(xpos + 45 + SymbolWidth, realypos + 5, xpos + 45
              + SymbolWidth, realypos + 20);
        }
      }
    } else {
      GraphicsUtil.switchToWidth(g, 5);
      if (LastBlock) {
        if (separate) {
          g.drawLine(xpos + 5, realypos, xpos + 5, realypos + 5);
          g.drawLine(xpos + 35 + SymbolWidth, realypos, xpos + 35
              + SymbolWidth, realypos + 5);
        } else {
          g.drawLine(xpos + 45 + SymbolWidth, realypos, xpos + 45
              + SymbolWidth, realypos + 5);
        }
      } else {
        if (separate) {
          g.drawLine(xpos + 5, realypos, xpos + 5, realypos + 20);
          g.drawLine(xpos + 35 + SymbolWidth, realypos, xpos + 35
              + SymbolWidth, realypos + 20);
        } else {
          g.drawLine(xpos + 45 + SymbolWidth, realypos, xpos + 45
              + SymbolWidth, realypos + 20);
        }
      }
    }
    GraphicsUtil.switchToWidth(g, 1);
  }

  public int getControlHeight(AttributeSet attrs) {
    int enables = numWriteEnables(attrs);
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC)
      return 60 + enables * 10;
    else
      return 90 + enables * 10;
  }

  private static WeakHashMap<MemContents, HexFrame> windowRegistry = new WeakHashMap<MemContents, HexFrame>();
  static HexFrame getHexFrame(MemContents value, Project proj, Instance instance) {
    synchronized (windowRegistry) {
      HexFrame ret = windowRegistry.get(value);
      if (ret == null) {
        ret = new HexFrame(proj, instance, value);
        windowRegistry.put(value, ret);
      }
      return ret;
    }
  }

  public static void closeHexFrame(RamState state) {
    MemContents contents = state.getContents();
    HexFrame ret;
    synchronized (windowRegistry) {
      ret = windowRegistry.remove(contents);
    }
    if (ret == null)
      return;
    ret.closeAndDispose();
  }

  @Override
  HexFrame getHexFrame(Project proj, Instance instance, CircuitState circState) {
    return getHexFrame(getState(instance, circState).getContents(), proj, instance);
  }

//  public AttributeSet getNonVolatileSimulationAttributes(Component comp) {
//    // return Collections.singletonList(NV_CONTENTS_ATTR);
//    AttributeOption type = comp.getAttributeSet().getValue(RamAttributes.ATTR_TYPE);
//    if (type != RamAttributes.NONVOLATILE)
//      return null;
//    int addrBits = state.getAttributeValue(ADDR_ATTR).getWidth();
//    int dataBits = state.getAttributeValue(DATA_ATTR).getWidth();
//    MemContents contents = MemContents.create(addrBits, dataBits);
//    return AttributeSets.fixedSet(new Attribute<?>[] { NV_CONTENTS_ATTR },
//        new Object[] { contents });
//  }
//
  @Override
  public AttributeSet getNonVolatileSimulationState(Component comp, CircuitState state) {
    AttributeOption type = comp.getAttributeSet().getValue(RamAttributes.ATTR_TYPE);
    if (type != RamAttributes.NONVOLATILE)
      return null;
    MemContents contents;
    if (state == null) {
      // FIXME: should return null here???? contents is clear...
      int addrBits = comp.getAttributeSet().getValue(ADDR_ATTR).getWidth();
      int dataBits = comp.getAttributeSet().getValue(DATA_ATTR).getWidth();
      contents = MemContents.create(addrBits, dataBits);
    } else {
      RamState ret = (RamState)state.getDataAsCustom(comp);
      if (ret == null)
        return null;
      contents = ret.getContents();
      if (contents.isClear())
        return null;
    }
    return AttributeSets.fixedSet(new Attribute<?>[] { NV_CONTENTS_ATTR },
        new Object[] { contents });
  }

  @Override
  public void setNonVolatileSimulationState(Component comp, CircuitState state, AttributeSet attrs) {
    if (!(comp instanceof InstanceComponent))
      throw new IllegalStateException("Component %s has unexpected type");
    AttributeOption type = comp.getAttributeSet().getValue(RamAttributes.ATTR_TYPE);
    if (type != RamAttributes.NONVOLATILE)
      throw new IllegalStateException("Component is not non-volatile");
    MemContents src = attrs.getValue(NV_CONTENTS_ATTR);
    if (src == null)
      throw new IllegalStateException("Component is missing simulation state");
    Instance instance = ((InstanceComponent)comp).getInstance();
    InstanceState istate = new InstanceStateImpl(state, comp);
    MemContents contents = getContents(istate);
    contents.copyFrom(0, src, 0, (int)(src.getLastOffset()+1));
  }
  
  public static Attribute<MemContents> NV_CONTENTS_ATTR = Rom.CONTENTS_ATTR;

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    boolean separate = isSeparate(attrs);
    int xoffset = (separate) ? 40 : 50;
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      return Bounds.create(0, 0, SymbolWidth + xoffset, 140);
    } else {
      int len = attrs.getValue(Mem.DATA_ATTR).getWidth();
      return Bounds.create(0, 0, SymbolWidth + xoffset,
          getControlHeight(attrs) + 20 * len);
    }
  }

  public MemContents getContents(InstanceState ramState) {
    return (MemContents)getState(ramState).getContents();
  }

  @Override
  MemState getState(Instance instance, CircuitState state) {
    Component comp = instance.getComponent();
    RamState ret = (RamState)state.getDataAsCustom(comp);
    if (ret == null) {
      AttributeOption type = instance.getAttributeValue(RamAttributes.ATTR_TYPE);
      int addrBits = instance.getAttributeValue(ADDR_ATTR).getWidth();
      int dataBits = instance.getAttributeValue(DATA_ATTR).getWidth();
      MemContents contents = MemContents.create(addrBits, dataBits);
      ret = new RamState(instance, contents, new MemListener(instance));
      state.setData(comp, ret);
    } else {
      ret.setRam(instance);
    }
    return ret;
  }

  @Override
  MemState getState(InstanceState state) {
    return getState(state.getInstance(), state.getCircuitState());
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    if (RamHDLGenerator.supports(ctx.lang, ctx.attrs, ctx.vendor)) // fixme
      return new RamHDLGenerator(ctx);
    else
      return null;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    super.instanceAttributeChanged(instance, attr);
    if (attr == Mem.DATA_ATTR || attr == Mem.LINE_ATTR || attr == StdAttr.APPEARANCE || attr == RamAttributes.ATTR_DBUS) {
      instance.recomputeBounds();
      configurePorts(instance);
    }
  }

  public void DrawRamClassic(InstancePainter painter) {
    DrawMemClassic(painter);

    boolean separate = isSeparate(painter.getAttributeSet());
    int dataLines = Mem.lineSize(painter.getAttributeSet());
    int DATA1 = MEM_INPUTS; // (dataLines-1) of them
    int DATAOUT[] = { DATA, DATA1, DATA1+1, DATA1+2 };
    int DIN0 = DATA1+(dataLines-1); // (dataLines) of them, only if separate
    int DATAIN[] = { (separate?DIN0:DATA), (separate?DIN0+1:DATA1), (separate?DIN0+2:DATA1+1), (separate?DIN0+3:DATA1+2) };
    int OE = DIN0; // 1, only if not separate
    int CLK = separate ? (DIN0 + dataLines) : (OE + 1); // 1 always
    int WE = CLK+1; // 1, always
    int LE = WE+1; // (datalines) of them, only if multiple data lines

    painter.drawPort(WE, S.get("ramWELabel"), Direction.EAST);
    painter.drawClock(CLK, Direction.EAST);

    if (separate)
      painter.drawPort(DATAIN[0], S.get("ramDataLabel"), Direction.EAST);
    else
      painter.drawPort(OE, S.get("ramOELabel"), Direction.EAST);
    for (int i = 1; i < dataLines; i++)
      painter.drawPort(DATAIN[i], ""+i, Direction.EAST);

    int enables = numWriteEnables(painter.getAttributeSet());
    for (int i = 0; i < enables; i++) {
      painter.drawPort(LE + i, S.get("ramWELabel")+i, Direction.EAST);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      DrawRamClassic(painter);
    } else {
      Graphics2D g = painter.getGraphics();
      Bounds bds = painter.getNominalBounds();
      int NrOfBits = painter.getAttributeValue(Mem.DATA_ATTR).getWidth();

      painter.drawLabel();

      int xpos = bds.getX();
      int ypos = bds.getY();

      DrawControlBlock(painter, xpos, ypos);
      for (int i = 0; i < NrOfBits; i++) {
        DrawDataBlock(painter, xpos, ypos, i, NrOfBits);
      }
      /* Draw contents */
      if (painter.getShowState()) {
        int dataLines = Mem.lineSize(painter.getAttributeSet());
        RamState state = (RamState) getState(painter);
        state.paint(painter.getGraphics(), bds.getX(), bds.getY(),
            60, getControlHeight(painter.getAttributeSet()) + 5,
            Mem.SymbolWidth - 75, 20 * NrOfBits - 10, false, dataLines);
      }
    }
  }

  @Override
  public void propagate(InstanceState state) {
    AttributeSet attrs = state.getAttributeSet();
    RamState myState = (RamState) getState(state);
    boolean separate = isSeparate(attrs);

    // get address
    Value addrValue = state.getPortValue(ADDR);
    int addr = addrValue.toIntValue();
    boolean goodAddr = (addrValue.isFullyDefined() && addr >= 0);
    if (goodAddr && addr != myState.getCurrent()) {
      myState.setCurrent(addr);
      myState.scrollToShow(addr);
    }

    int dataLines = Mem.lineSize(attrs);
    int DATA1 = MEM_INPUTS; // (dataLines-1) of them
    int DATAOUT[] = { DATA, DATA1, DATA1+1, DATA1+2 };
    int DIN = DATA1+(dataLines-1); // (dataLines) of them, only if separate
    int DATAIN[] = { (separate?DIN:DATA), (separate?DIN+1:DATA1), (separate?DIN+2:DATA1+1), (separate?DIN+3:DATA1+2) };
    int OE = DIN; // 1, only if not separate
    int CLK = separate ? (DIN + dataLines) : (OE + 1); // 1 always
    int WE = CLK+1; // 1, always
    int LE = WE+1; // (datalines) of them, only if multiple data lines

    // perform writes
    Object trigger = state.getAttributeValue(StdAttr.TRIGGER);
    boolean triggered = myState.setClock(state.getPortValue(CLK), trigger);
    boolean writeEnabled = triggered && (state.getPortValue(WE) == Value.TRUE);
    if (writeEnabled && goodAddr && (addr % dataLines == 0)) {
      for (int i = 0; i < dataLines; i++) {
        if (dataLines > 1) {
          Value le = state.getPortValue(LE+i);
          if (le != null && le.equals(Value.FALSE))
            continue;
        }
        int dataValue = state.getPortValue(DATAIN[i]).toIntValue();
        myState.getContents().set(addr+i, dataValue);
      }
    }

    // perform reads
    BitWidth width = state.getAttributeValue(DATA_ATTR);
    boolean outputEnabled = separate || !state.getPortValue(OE).equals(Value.FALSE);
    if (outputEnabled && goodAddr && (addr % dataLines == 0)) {
      for (int i = 0; i < dataLines; i++) {
        int val = myState.getContents().get(addr+i);
        state.setPort(DATAOUT[i], Value.createKnown(width, val), DELAY);
      }
    } else if (outputEnabled && (addrValue.isErrorValue() || (goodAddr && (addr % dataLines != 0)))) {
      for (int i = 0; i < dataLines; i++)
        state.setPort(DATAOUT[i], Value.createError(width), DELAY);
    } else {
      for (int i = 0; i < dataLines; i++)
        state.setPort(DATAOUT[i], Value.createUnknown(width), DELAY);
    }
  }

  private boolean isSeparate(AttributeSet attrs) {
    Object bus = attrs.getValue(RamAttributes.ATTR_DBUS);
    return bus == null || bus.equals(RamAttributes.BUS_SEP);
  }

}
