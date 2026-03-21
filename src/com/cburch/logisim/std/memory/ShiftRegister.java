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

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.access.InventoryFeature;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.IntegerConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;

public class ShiftRegister extends InstanceFactory {
  static final Attribute<Integer> ATTR_LENGTH = Attributes.forIntegerRange(
      "length", S.getter("shiftRegLengthAttr"), 1, 32);
  static final Attribute<Boolean> ATTR_LOAD = Attributes.forBoolean(
      "parallel", S.getter("shiftRegParallelAttr"));

  static final int IN = 0;
  static final int SH = 1;
  static final int CK = 2;
  static final int CLR = 3;
  static final int OUT = 4;
  static final int LD = 5;
  static final int SymbolWidth = 100;

  public ShiftRegister() {
    super("Shift Register", S.getter("shiftRegisterComponent"));
    setAttributes(new Attribute[] { StdAttr.WIDTH, ATTR_LENGTH, ATTR_LOAD,
      StdAttr.EDGE_TRIGGER, StdAttr.LABEL, StdAttr.LABEL_FONT,
      Register.ATTR_SHOW_IN_TAB, StdAttr.APPEARANCE },
      new Object[] { BitWidth.ONE, Integer.valueOf(8), Boolean.TRUE,
        StdAttr.TRIG_RISING, "", StdAttr.DEFAULT_LABEL_FONT, 
        true, StdAttr.APPEAR_CLASSIC });
    setKeyConfigurator(JoinedConfigurator
        .create(new IntegerConfigurator(ATTR_LENGTH, 1, 32, 0),
          new BitWidthConfigurator(StdAttr.WIDTH)));

    setIconName("shiftreg.gif");
    setInstanceLogger(ShiftRegisterLogger.class);
    setInstancePoker(ShiftRegisterPoker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    instance.addAttributeListener();
  }

  private void configurePorts(Instance instance) {
    BitWidth widthObj = instance.getAttributeValue(StdAttr.WIDTH);
    int width = widthObj.getWidth();
    Boolean parallelObj = instance.getAttributeValue(ATTR_LOAD);
    Bounds bds = instance.getNominalBounds();
    Port[] ps;
    Integer lenObj = instance.getAttributeValue(ATTR_LENGTH);
    int len = lenObj == null ? 8 : lenObj.intValue();
    if (instance.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      if (parallelObj == null || parallelObj.booleanValue()) {
        ps = new Port[6 + 2 * len];
        ps[LD] = new Port(10, -20, Port.INPUT, 1);
        ps[LD].setToolTip(S.getter("shiftRegLoadTip"));
        for (int i = 0; i < len; i++) {
          ps[6 + 2 * i]     = new Port(20 + 10 * i, -20, Port.INPUT, width);
          ps[6 + 2 * i + 1] = new Port(20 + 10 * i,  20, Port.OUTPUT, width);
          ps[6 + 2 * i].setToolTip(S.getter("shiftRegParallelInputTip", ""+(len-i-1)));
          ps[6 + 2 * i + 1].setToolTip(S.getter("shiftRegParallelOutputTip", ""+(len-i-1)));
        }
      } else {
        ps = new Port[5];
      }
      ps[OUT] = new Port(bds.getWidth(), 0, Port.OUTPUT, width);
      ps[SH]  = new Port( 0, -10, Port.INPUT, 1);
      ps[IN]  = new Port( 0,   0, Port.INPUT, width);
      ps[CK]  = new Port( 0,  10, Port.INPUT, 1);
      ps[CLR] = new Port(10,  20, Port.INPUT, 1);
    } else {
      if (parallelObj == null || parallelObj.booleanValue()) {
        ps = new Port[6 + 2 * len - 1];
        ps[LD] = new Port(0, 30, Port.INPUT, 1);
        for (int i = 0; i < len; i++) {
          ps[6 + 2 * i] = new Port(0, 90 + i * 20, Port.INPUT, width);
          ps[6 + 2 * i].setToolTip(S.getter("shiftRegParallelInputTip", ""+(len-i-1)));
          if (i < (len - 1)) {
            ps[6 + 2 * i + 1] = new Port(SymbolWidth + 20, 90 + i * 20,
                Port.OUTPUT, width);
            ps[6 + 2 * i + 1].setToolTip(S.getter("shiftRegParallelOutputTip", ""+(len-i-1)));
          }
        }
        ps[LD].setToolTip(S.getter("shiftRegLoadTip"));
      } else {
        ps = new Port[5];
      }

      ps[OUT] = new Port(SymbolWidth + 20, 70 + len * 20, Port.OUTPUT, width);
      ps[IN] = new Port(0, 80, Port.INPUT, width);
      ps[SH] = new Port(0, 40, Port.INPUT, 1);
      ps[CK] = new Port(0, 50, Port.INPUT, 1);
      ps[CLR] = new Port(0, 20, Port.INPUT, 1);
    }

    ps[OUT].setToolTip(S.getter("shiftRegOutTip"));
    ps[SH].setToolTip(S.getter("shiftRegShiftTip"));
    ps[IN].setToolTip(S.getter("shiftRegInTip"));
    ps[CK].setToolTip(S.getter("shiftRegClockTip"));
    ps[CLR].setToolTip(S.getter("shiftRegClearTip"));
    instance.setPorts(ps);
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, bds.getX()
        + bds.getWidth() / 2, bds.getY() + bds.getHeight() / 2 - 3, GraphicsUtil.H_CENTER,
        GraphicsUtil.V_BASELINE);
  }

  private void DrawControl(InstancePainter painter, int xpos, int ypos,
      int nr_of_stages, int nr_of_bits, boolean has_load,
      boolean active_low_clock) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    int blockwidth = SymbolWidth;
    g.drawLine(xpos + 10, ypos, xpos + blockwidth + 10, ypos);
    g.drawLine(xpos + 10, ypos, xpos + 10, ypos + 60);
    g.drawLine(xpos + blockwidth + 10, ypos, xpos + blockwidth + 10,
        ypos + 60);
    g.drawLine(xpos + 10, ypos + 60, xpos + 20, ypos + 60);
    g.drawLine(xpos + blockwidth, ypos + 60, xpos + blockwidth + 10,
        ypos + 60);
    g.drawLine(xpos + 20, ypos + 60, xpos + 20, ypos + 70);
    g.drawLine(xpos + blockwidth, ypos + 60, xpos + blockwidth, ypos + 70);
    if (nr_of_bits > 1) {
      g.drawLine(xpos + blockwidth + 10, ypos + 5,
          xpos + blockwidth + 15, ypos + 5);
      g.drawLine(xpos + blockwidth + 15, ypos + 5,
          xpos + blockwidth + 15, ypos + 65);
      g.drawLine(xpos + blockwidth + 5, ypos + 65,
          xpos + blockwidth + 15, ypos + 65);
      g.drawLine(xpos + blockwidth + 5, ypos + 65, xpos + blockwidth + 5,
          ypos + 70);
      if (nr_of_bits > 2) {
        g.drawLine(xpos + blockwidth + 15, ypos + 10, xpos + blockwidth
            + 20, ypos + 10);
        g.drawLine(xpos + blockwidth + 20, ypos + 10, xpos + blockwidth
            + 20, ypos + 70);
        g.drawLine(xpos + blockwidth + 10, ypos + 70, xpos + blockwidth
            + 20, ypos + 70);
      }
    }
    String Identifier = "SRG" + Integer.toString(nr_of_stages);
    GraphicsUtil.drawCenteredText(g, Identifier, xpos + (SymbolWidth / 2)
        + 10, ypos + 5);
    /* Draw the clock input */
    painter.drawClockSymbol(xpos + 10, ypos + 50);
    GraphicsUtil.switchToWidth(g, 2);
    if (active_low_clock)
      g.drawOval(xpos, ypos + 45, 10, 10);
    else
      g.drawLine(xpos, ypos + 50, xpos + 10, ypos + 50);
    painter.drawPort(CK);
    String Cntrl = "1\u2192/C3";
    GraphicsUtil.drawText(g, Cntrl, xpos + 20, ypos + 50,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    /* draw shift input */
    g.drawLine(xpos, ypos + 40, xpos + 10, ypos + 40);
    GraphicsUtil.drawText(g, "M1 [shift]", xpos + 20, ypos + 40,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    painter.drawPort(SH);
    /* draw load input */
    if (has_load) {
      g.drawLine(xpos, ypos + 30, xpos + 10, ypos + 30);
      GraphicsUtil.drawText(g, "M2 [load]", xpos + 20, ypos + 30,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
      painter.drawPort(LD);
    }
    /* draw reset */
    g.drawLine(xpos, ypos + 20, xpos + 10, ypos + 20);
    GraphicsUtil.drawText(g, "R", xpos + 20, ypos + 20,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    painter.drawPort(CLR);
    GraphicsUtil.switchToWidth(g, 1);
  }

  private void DrawDataBlock(InstancePainter painter, int xpos, int ypos,
      int nr_of_stages, int nr_of_bits, int current_stage,
      Integer data_value, boolean has_load) {
    int real_ypos = ypos + 70 + current_stage * 20;
    if (current_stage > 0)
      real_ypos += 10;
    int real_xpos = xpos + 10;
    int DataWidth = (nr_of_bits == 1) ? 3 : 5;
    int height = (current_stage == 0) ? 30 : 20;
    boolean LastBlock = (current_stage == (nr_of_stages - 1));
    int blockwidth = SymbolWidth;
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(real_xpos, real_ypos, blockwidth, height);
    if (nr_of_bits > 1) {
      g.drawLine(real_xpos + blockwidth, real_ypos + 5, real_xpos
          + blockwidth + 5, real_ypos + 5);
      g.drawLine(real_xpos + blockwidth + 5, real_ypos + 5, real_xpos
          + blockwidth + 5, real_ypos + height + 5);
      if (LastBlock) {
        g.setColor(Color.RED);
        g.drawLine(real_xpos + 5, real_ypos + height + 5, real_xpos
            + blockwidth + 5, real_ypos + height + 5);
        g.drawLine(real_xpos + 5, real_ypos + height, real_xpos + 5,
            real_ypos + height + 5);
        g.setColor(Color.BLACK);
      }
      if (nr_of_bits > 2) {
        g.drawLine(real_xpos + blockwidth + 5, real_ypos + 10,
            real_xpos + blockwidth + 10, real_ypos + 10);
        g.drawLine(real_xpos + blockwidth + 10, real_ypos + 10,
            real_xpos + blockwidth + 10, real_ypos + height + 10);
        if (LastBlock) {
        g.setColor(Color.CYAN);
          g.drawLine(real_xpos + 10, real_ypos + height + 10,
              real_xpos + blockwidth + 10, real_ypos + height + 10);
          g.drawLine(real_xpos + 10, real_ypos + height + 5,
              real_xpos + 10, real_ypos + height + 10);
        g.setColor(Color.BLACK);
        }
      }
    }
    /* Draw the Inputs */
    if (current_stage == 0 || has_load) {
      GraphicsUtil.switchToWidth(g, DataWidth);
      g.drawLine(real_xpos - 10, real_ypos + 10, real_xpos - 1,
          real_ypos + 10);
      if (has_load)
        painter.drawPort(6 + 2 * current_stage);
      if (current_stage == 0) {
        painter.drawPort(IN);
        GraphicsUtil.drawText(g, "1,3D", real_xpos + 1, real_ypos + 10,
            GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
        if (has_load) {
          g.drawLine(real_xpos - 10, real_ypos + 20, real_xpos - 1,
              real_ypos + 20);
          GraphicsUtil.drawText(g, "2,3D", real_xpos + 1,
              real_ypos + 20, GraphicsUtil.H_LEFT,
              GraphicsUtil.V_CENTER_FIRST);
        }
      } else {
        GraphicsUtil.drawText(g, "2,3D", real_xpos + 1, real_ypos + 10,
            GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
      }
      GraphicsUtil.switchToWidth(g, 1);
    }
    GraphicsUtil.switchToWidth(g, 1);
    /* Draw the outputs */
    if (LastBlock) {
      GraphicsUtil.switchToWidth(g, DataWidth);
      g.drawLine(real_xpos + blockwidth, real_ypos + 10, real_xpos
          + blockwidth + 10, real_ypos + 10);
      painter.drawPort(OUT);
      GraphicsUtil.switchToWidth(g, 1);
    }
    if (has_load && !LastBlock) {
      GraphicsUtil.switchToWidth(g, DataWidth);
      if (current_stage == 0)
        g.drawLine(real_xpos + blockwidth, real_ypos + 20, real_xpos
            + blockwidth + 10, real_ypos + 20);
      else
        g.drawLine(real_xpos + blockwidth, real_ypos + 10, real_xpos
            + blockwidth + 10, real_ypos + 10);
      painter.drawPort(6 + 2 * current_stage + 1);
      GraphicsUtil.switchToWidth(g, 1);
    }
    /* Draw stage value */
    if (painter.getShowState()) {
      g.setColor(Color.LIGHT_GRAY);
      int yoff = (current_stage == 0) ? 10 : 0;
      int len = (nr_of_bits + 3) / 4;
      int boxXpos = ((blockwidth - 30) / 2 + 30) - (len * 4);
      g.fillRect(real_xpos + boxXpos, real_ypos + yoff + 2, 2 + len * 8,
          16);
      if (data_value != null) {
        g.setColor(Color.DARK_GRAY);
        String Value = StringUtil.toHexString(nr_of_bits, data_value.intValue());
        GraphicsUtil.drawText(g, MemState.FONT, Value, real_xpos + boxXpos + 1, real_ypos
            + yoff + 10, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
        g.setColor(Color.BLACK);
      }
    }
  }

  private ShiftRegisterData getData(InstanceState state) {
    BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
    Integer lenObj = state.getAttributeValue(ATTR_LENGTH);
    int length = lenObj == null ? 8 : lenObj.intValue();
    ShiftRegisterData data = (ShiftRegisterData) state.getDataAsCustom();
    if (data == null) {
      data = new ShiftRegisterData(width, length);
      state.setData(data);
    } else {
      data.setDimensions(width, length);
    }
    return data;
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      Object parallel = attrs.getValue(ATTR_LOAD);
      if (parallel == null || ((Boolean) parallel).booleanValue()) {
        int len = attrs.getValue(ATTR_LENGTH).intValue();
        return Bounds.create(0, -20, 20 + 10 * len, 40);
      } else {
        return Bounds.create(0, -20, 30, 40);
      }
    } else {
      int len = attrs.getValue(ATTR_LENGTH).intValue();
      return Bounds.create(0, 0, SymbolWidth + 20, 80 + 20 * len);
    }
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new ShiftRegisterHDLGenerator(ctx);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_LOAD || attr == ATTR_LENGTH || attr == StdAttr.WIDTH || attr == StdAttr.APPEARANCE) {
      instance.recomputeBounds();
      configurePorts(instance);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      paintInstanceClassic(painter);
    } else {
      paintInstanceEvolution(painter);
    }
  }

  private void paintInstanceEvolution(InstancePainter painter) {
    // draw boundary, label
    painter.drawLabel();
    int xpos = painter.getLocation().getX();
    int ypos = painter.getLocation().getY();
    BitWidth widObj = painter.getAttributeValue(StdAttr.WIDTH);
    int wid = widObj.getWidth();
    Integer lenObj = painter.getAttributeValue(ATTR_LENGTH);
    int len = lenObj == null ? 8 : lenObj.intValue();
    Boolean parallelObj = painter.getAttributeValue(ATTR_LOAD);
    Boolean Negedge = painter.getAttributeValue(StdAttr.EDGE_TRIGGER)
        .equals(StdAttr.TRIG_FALLING);
    DrawControl(painter, xpos, ypos, len, wid, parallelObj, Negedge);
    ShiftRegisterData data = (ShiftRegisterData) painter.getDataAsCustom();
    for (int stage = 0; stage < len; stage++) {
      Integer val = null;
      if (data != null && data.get(len - stage - 1) != null)
        val = Integer.valueOf(data.get(len - stage - 1).toIntValue());
      DrawDataBlock(painter, xpos, ypos, len, wid, stage,
          val, parallelObj);
    }
  }

  @Override
  public void propagate(InstanceState state) {
    Object triggerType = state.getAttributeValue(StdAttr.EDGE_TRIGGER);
    boolean parallel = state.getAttributeValue(ATTR_LOAD).booleanValue();
    ShiftRegisterData data = getData(state);
    int len = data.getLength();

    boolean triggered = data.updateClock(state.getPortValue(CK),
        triggerType);
    if (state.getPortValue(CLR) == Value.TRUE) {
      data.clear();
    } else if (triggered) {
      if (parallel && state.getPortValue(LD) == Value.TRUE) {
        data.clear();
        for (int i = len - 1; i >= 0; i--) {
          data.push(state.getPortValue(6 + 2 * i));
        }
      } else if (state.getPortValue(SH) != Value.FALSE) {
        data.push(state.getPortValue(IN));
      }
    }

    state.setPort(OUT, data.get(0), 4);
    if (parallel) {
      for (int i = 0; i < len - 1; i++) {
        state.setPort(6 + 2 * i + 1, data.get(len - 1 - i), 4);
      }
      if (state.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
        state.setPort(6 + 2 * (len-1) + 1, data.get(0), 4);
      }
    }
  }

  private void paintInstanceClassic(InstancePainter painter) {
    // draw boundary, label
    painter.drawBounds();
    painter.drawLabel();

    // draw state
    boolean parallel = painter.getAttributeValue(ATTR_LOAD).booleanValue();
    if (parallel) {
      BitWidth widObj = painter.getAttributeValue(StdAttr.WIDTH);
      int wid = widObj.getWidth();
      Integer lenObj = painter.getAttributeValue(ATTR_LENGTH);
      int len = lenObj == null ? 8 : lenObj.intValue();
      if (painter.getShowState()) {
        if (wid <= 4) {
          ShiftRegisterData data = getData(painter);
          Bounds bds = painter.getNominalBounds();
          int x = bds.getX() + 20;
          int y = bds.getY();
          Object label = painter.getAttributeValue(StdAttr.LABEL);
          if (label == null || label.equals("")) {
            y += bds.getHeight() / 2;
          } else {
            y += 3 * bds.getHeight() / 4;
          }
          Graphics2D g = painter.getGraphics();
          for (int i = 0; i < len; i++) {
            if (data != null && data.get(len - 1 - i) != null) {
              String s = data.get(len - 1 - i).toHexString();
              GraphicsUtil.drawCenteredText(g, MemState.FONT, s, x, y);
            }
            x += 10;
          }
        }
      } else {
        Bounds bds = painter.getNominalBounds();
        int x = bds.getX() + bds.getWidth() / 2;
        int y = bds.getY();
        int h = bds.getHeight();
        Graphics2D g = painter.getGraphics();
        Object label = painter.getAttributeValue(StdAttr.LABEL);
        if (label == null || label.equals("")) {
          String a = S.get("shiftRegisterLabel1");
          GraphicsUtil.drawCenteredText(g, a, x, y + h / 4);
        }
        String b = S.fmt("shiftRegisterLabel2", "" + len, "" + wid);
        GraphicsUtil.drawCenteredText(g, b, x, y + 3 * h / 4);
      }
    }

    // draw input and output ports
    int ports = painter.getInstance().getPorts().size();
    for (int i = 0; i < ports; i++) {
      if (i != CK) painter.drawPort(i);
    }
    painter.drawClock(CK, Direction.EAST);
  }

  @Override
  public Object getFeature(Object key, AttributeSet attrs) {
    if (key == InventoryFeature.class)
      return new MyInventoryFeature();
    return super.getFeature(key, attrs);
  }

  private static class MyInventoryFeature implements InventoryFeature {
    
    @Override
    public Map<String, String> getAttributeNotes(AttributeSet attrs) {
      HashMap<String, String> notes = new HashMap<>();
      String msg;
      msg = "determines whether Load port and parallel Input and Output ports are present";
      notes.put("parallel", msg);
      msg = "when parallel=true, determines the count of Input and Output ports";
      notes.put("length", msg);
      return notes;
    }
    // @Override
    // public List<String> getLayoutAnalysisIncludedAttributes(AttributeSet attrs) {
    // }

    @Override
    public List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs) {
      return List.of("length");
    }

    @Override
    public List<InventoryFeature.PortPosition> getCustomPortLayout(AttributeSet attrs) {
      Object appear = attrs.getValue(StdAttr.APPEARANCE);
      boolean parallel = attrs.getValue(ATTR_LOAD).booleanValue();
        
      InventoryFeature.PortPosition shf, inp, clk, clr, out, lde, ldi, ldo;
      if (appear == StdAttr.APPEAR_CLASSIC) {
        shf = portAt("Shift", "input", 0, -10);
        inp = portAt("Input", "input", 0, 0);
        clk = portAt("Clock", "input", 0, 10);
        clr = portAt("Clear", "input", 10, 20);
        if (!parallel) {
          out = portAt("Output", "output", 30, 0);
          return List.of(inp, out, clk, shf, clr);
        }
        out = portAt("Output", "output", "20 + 10*length", 0);
        lde = portAt("Load", "input", 10, -20);
        ldi = portsAt("Input", "input", 0, "length", 20, -20, 10, 0);
        ldo = portsAt("Output", "output", 0, "length", 20, 20, 10, 0);
        return List.of(inp, out, clk, shf, clr, lde, ldi, ldo);
      } else { // ANSI
        clr = portAt("Clear", "input", 0, 20);
        shf = portAt("Shift", "input", 0, 40);
        clk = portAt("Clock", "input", 0, 50);
        inp = portAt("Input", "input", 0, 80);
        if (!parallel) {
          out = portAt("Output", "output", SymbolWidth+20, 90);
          return List.of(inp, out, clk, shf, clr);
        }
        lde = portAt("Load", "input", 0, 30);
        ldi = portsAt("Input", "input", 0, "length", 0, 90, 0, 20);
        ldo = portsAt("Output", "output", 0, "length", SymbolWidth+20, 90, 0, 20);
        return List.of(inp, clk, shf, clr, lde, ldi, ldo);
      }
    }
  }

}
