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
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;

public class Random extends InstanceFactory {
  public static class Logger extends InstanceLogger {
    @Override
    public String getLogName(InstanceState state, Object option) {
      String ret = state.getAttributeValue(StdAttr.LABEL);
      return ret != null && !ret.equals("") ? ret : null;
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.WIDTH);
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
      if (dataWidth == null)
        dataWidth = BitWidth.create(0);
      StateData data = (StateData) state.getDataAsCustom();
      if (data == null)
        return Value.createKnown(dataWidth, 0);
      return Value.createKnown(dataWidth, data.value);
    }

    @Override
    public boolean isInput(InstanceState state, Object option) {
      return true;
    }
  }

  public static class Poker extends InstancePoker {
    boolean isPressed = true;

    private boolean isInside(InstanceState state, MouseEvent e) {
      BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
      int width = (dataWidth == null ? 8 : dataWidth.getWidth());
      Location loc = state.getInstance().getLocation();
      int x = loc.getX();
      int y = loc.getY();
      if (state.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
        int side = width <= 16 ? 2 + 20 - 5*((width+3)/4) : 2 ;
        int top = 1;
        int bot = width <= 16 ? 20 : 5;
        return x+side <= e.getX() && e.getX() <= x+40-side
            && y+top <= e.getY() && e.getY() <= y+40-bot;
      } else {
        int side = 2 + 80 - 10*((width+3)/4);
        int top = 70;
        int bot = 1;
        return x+side <= e.getX() && e.getX() <= x+80-side
            && y+top <= e.getY() && e.getY() <= y+90-bot;
      }
    }

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      isPressed = isInside(state, e);
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      if (isPressed && isInside(state, e)) {
        StateData data = (StateData) state.getDataAsCustom();
        if (data == null)
          return;
        data.step();
        state.queueForPropagation();
      }
      isPressed = false;
    }

    // @Override
    // public void keyTyped(InstanceState state, KeyEvent e) { }

    @Override
    public void keyPressed(InstanceState state, KeyEvent e) {
      StateData data = (StateData) state.getDataAsCustom();
      if (data == null)
        return;
      if (e.getKeyCode() == KeyEvent.VK_DELETE ||
          e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
        e.consume();
        if (data.reset(state.getAttributeValue(ATTR_SEED)))
          state.queueForPropagation();
      }
    }
  }

  private static class StateData extends ClockState implements ComponentData {
    private final static long multiplier = 0x5DEECE66DL;
    private final static long addend = 0xBL;
    private final static long mask = (1L << 48) - 1;

    private long initSeed;
    private long curSeed;
    private int value;
    private boolean isResetting;

    public StateData(Object seed) {
      reset(seed);
    }

    StateData(StateData other) {
      super(other);
      initSeed = other.initSeed;
      curSeed = other.curSeed;
      value = other.value;
      isResetting = other.isResetting;
    }

    @Override
    public StateData duplicateForNewSimulation() {
      return new StateData(this);
    }

    boolean reset(Object seed) {
      if (isResetting)
        return false;
      long start = seed instanceof Integer ? ((Integer) seed).intValue() : 0;
      if (start == 0) {
        // Prior to 2.7.0, this would reset to the seed at the time of
        // the StateData's creation. It seems more likely that what
        // would be intended was starting a new sequence entirely...
        start = (System.currentTimeMillis() ^ multiplier) & mask;
        if (start == initSeed) {
          start = (start + multiplier) & mask;
        }
      }
      this.initSeed = start;
      this.curSeed = start;
      this.value = (int) start;
      this.isResetting = true;
      return true;
    }

    void doneReset() {
      this.isResetting = false;
    }

    void step() {
      long v = curSeed;
      v = (v * multiplier + addend) & mask;
      curSeed = v;
      value = (int) (v >> 12);
    }
  }

  static final Attribute<Integer> ATTR_SEED = Attributes.forInteger("seed",
      S.getter("randomSeedAttr"));

  static final int OUT = 0;
  static final int CK = 1;
  static final int NXT = 2;
  static final int RST = 3;

  public Random() {
    super("Random", S.getter("randomComponent"));
    setAttributes(new Attribute[] { StdAttr.WIDTH, ATTR_SEED,
      StdAttr.EDGE_TRIGGER, StdAttr.LABEL, StdAttr.LABEL_FONT,
      StdAttr.APPEARANCE, },
      new Object[] { BitWidth.create(8), Integer.valueOf(0),
        StdAttr.TRIG_RISING, "", StdAttr.DEFAULT_LABEL_FONT,
        StdAttr.APPEAR_CLASSIC,});
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));

    setIconName("random.gif");
    setInstancePoker(Poker.class);
    setInstanceLogger(Logger.class);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      return Bounds.create(0, 0, 40, 40);
    } else {
      return Bounds.create(0, 0, 80, 90);
    }
  }


  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    Bounds bds = instance.getNominalBounds();
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, bds.getX()
        + bds.getWidth() / 2, bds.getY() - 3, GraphicsUtil.H_CENTER,
        GraphicsUtil.V_BASELINE);
  }

  private void DrawControl(InstancePainter painter, int xpos, int ypos,
      int NrOfBits) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawLine(xpos + 10, ypos, xpos + 70, ypos);
    g.drawLine(xpos + 10, ypos, xpos + 10, ypos + 60);
    g.drawLine(xpos + 70, ypos, xpos + 70, ypos + 60);
    g.drawLine(xpos + 10, ypos + 60, xpos + 20, ypos + 60);
    g.drawLine(xpos + 60, ypos + 60, xpos + 70, ypos + 60);
    g.drawLine(xpos + 20, ypos + 60, xpos + 20, ypos + 70);
    g.drawLine(xpos + 60, ypos + 60, xpos + 60, ypos + 70);
    String Name = "RNG" + Integer.toString(NrOfBits);
    GraphicsUtil.drawText(g, Name, xpos + 40, ypos + 8,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_FIRST);
    g.drawLine(xpos, ypos + 30, xpos + 10, ypos + 30);
    GraphicsUtil.drawText(g, "R", xpos + 20, ypos + 30,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    painter.drawPort(RST);
    g.drawLine(xpos, ypos + 40, xpos + 10, ypos + 40);
    GraphicsUtil.drawText(g, "EN", xpos + 20, ypos + 40,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_FIRST);
    painter.drawPort(NXT);
    painter.drawClockSymbol(xpos + 10, ypos + 50);
    GraphicsUtil.switchToWidth(g, 2);
    if (painter.getAttributeValue(StdAttr.EDGE_TRIGGER).equals(
          StdAttr.TRIG_FALLING)) {
      g.drawOval(xpos, ypos + 45, 10, 10);
    } else {
      g.drawLine(xpos, ypos + 50, xpos + 10, ypos + 50);
    }
    painter.drawPort(CK);
    GraphicsUtil.switchToWidth(g, 1);
  }

  private void DrawData(InstancePainter painter, int xpos, int ypos,
      int NrOfBits, int Value) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(xpos, ypos, 80, 20);
    if (painter.getShowState()) {
      String str = StringUtil.toHexString(NrOfBits, Value);
      GraphicsUtil.drawCenteredText(g, str, xpos + 40, ypos + 10);
    }
    painter.drawPort(OUT);
    GraphicsUtil.switchToWidth(g, 1);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new RandomHDLGenerator(ctx);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC)
      paintInstanceClassic(painter);
    else
      paintInstanceEvolution(painter);
  }

  public void paintInstanceClassic(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    StateData state = (StateData) painter.getDataAsCustom();
    BitWidth widthVal = painter.getAttributeValue(StdAttr.WIDTH);
    int width = widthVal == null ? 8 : widthVal.getWidth();

    // determine text to draw in label
    String a;
    String b = null;
    if (painter.getShowState()) {
      int val = state == null ? 0 : state.value;
      String str = StringUtil.toHexString(width, val);
      if (str.length() <= 4) {
        a = str;
      } else {
        int split = str.length() - 4;
        a = str.substring(0, split);
        b = str.substring(split);
      }
    } else {
      a = S.get("randomLabel");
      b = S.fmt("randomWidthLabel", "" + widthVal.getWidth());
    }

    // draw boundary, label
    painter.drawBounds();
    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();

    // draw input and output ports
    if (b == null)
      painter.drawPort(OUT, "Q", Direction.WEST);
    else
      painter.drawPort(OUT);
    g.setColor(Color.GRAY);
    painter.drawPort(RST, "0", Direction.SOUTH);
    painter.drawPort(NXT, S.get("memEnableLabel"), Direction.EAST);
    g.setColor(Color.BLACK);
    painter.drawClock(CK, Direction.NORTH);

    // draw contents
    if (b == null) {
      GraphicsUtil.drawText(g, MemState.FONT, a, bds.getX() + 20, bds.getY() + 4,
          GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
    } else {
      GraphicsUtil.drawText(g, MemState.FONT, a, bds.getX() + 20, bds.getY() + 3,
          GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
      GraphicsUtil.drawText(g, MemState.FONT, b, bds.getX() + 20, bds.getY() + 15,
          GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
    }
  }

  public void paintInstanceEvolution(InstancePainter painter) {
    Bounds bds = painter.getNominalBounds();
    int x = bds.getX();
    int y = bds.getY();
    StateData state = (StateData) painter.getDataAsCustom();
    int val = state == null ? 0 : state.value;
    BitWidth widthVal = painter.getAttributeValue(StdAttr.WIDTH);
    int width = widthVal == null ? 8 : widthVal.getWidth();

    painter.drawLabel();
    DrawControl(painter, x, y, width);
    DrawData(painter, x, y + 70, width, val);

  }

  @Override
  public void propagate(InstanceState state) {
    StateData data = (StateData) state.getDataAsCustom();
    if (data == null) {
      data = new StateData(state.getAttributeValue(ATTR_SEED));
      state.setData(data);
    }

    BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
    Object triggerType = state.getAttributeValue(StdAttr.EDGE_TRIGGER);
    boolean triggered = data.updateClock(state.getPortValue(CK), triggerType);

    if (state.getPortValue(RST) == Value.TRUE) {
      if (!data.reset(state.getAttributeValue(ATTR_SEED)))
        return; // avoid oscillation while resetting
    } else {
      data.doneReset();
      if (triggered && state.getPortValue(NXT) != Value.FALSE)
        data.step();
    }

    state.setPort(OUT, Value.createKnown(dataWidth, data.value), 4);
  }

  private void updatePorts(Instance instance) {
    Port[] ps = new Port[4];
    if (instance.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      ps[OUT] = new Port(40, 20, Port.OUTPUT, StdAttr.WIDTH);
      ps[CK] = new Port(10, 40, Port.INPUT, 1);
      ps[NXT] = new Port(0, 30, Port.INPUT, 1);
      ps[RST] = new Port(30, 40, Port.INPUT, 1);
    } else {
      ps[OUT] = new Port(80, 80, Port.OUTPUT, StdAttr.WIDTH);
      ps[CK] = new Port(0, 50, Port.INPUT, 1);
      ps[NXT] = new Port(0, 40, Port.INPUT, 1);
      ps[RST] = new Port(0, 30, Port.INPUT, 1);
    }
    ps[OUT].setToolTip(S.getter("randomQTip"));
    ps[CK].setToolTip(S.getter("randomClockTip"));
    ps[NXT].setToolTip(S.getter("randomNextTip"));
    ps[RST].setToolTip(S.getter("randomResetTip"));
    instance.setPorts(ps);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.APPEARANCE) {
      instance.recomputeBounds();
      updatePorts(instance);
    }
  }
}
