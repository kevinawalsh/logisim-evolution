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
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
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
import com.cburch.logisim.util.StringGetter;

public class Slider extends InstanceFactory {

  public static class Logger extends InstanceLogger {
    
    @Override
    public String getLogName(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.LABEL);
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.WIDTH);
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      State data = (State) state.getDataAsCustom();
      if (data == null)
        return Value.createKnown(getBitWidth(state, option), 0);
      else
        return data.getValue(state.getAttributeSet());
    }

    @Override
    public boolean isInput(InstanceState state, Object option) {
      return true;
    }

  }

  public static class SliderPoker extends InstancePoker {

    @Override
    public void mouseDragged(InstanceState state, MouseEvent e) {
      Location inputLoc = state.getInstance().getLocation();
      AttributeSet attrs = state.getAttributeSet();
      Direction facing = attrs.getValue(StdAttr.FACING);
      int dx = e.getX() - inputLoc.getX();
      int dy = e.getY() - inputLoc.getY();
      double aC = HEIGHT/2.0;
      int aS = HEIGHT - 2*MARGIN;
      double dcs; // distance in pixels from center of slider
      if (facing == Direction.SOUTH) // inputLoc at top
        dcs = (-dy + aC)/(1.0 + Math.abs(dx*1.0/HEIGHT));
      else if (facing == Direction.NORTH) // inputLoc at bottom
        dcs = (-dy - aC)/(1.0 + Math.abs(dx*1.0/HEIGHT));
      else if (facing == Direction.EAST) // inputLoc at left
        dcs = (dx - aC)/(1.0 + Math.abs(dy*1.0/HEIGHT));
      else // inputLoc at right
        dcs = (dx + aC)/(1.0 + Math.abs(dy*1.0/HEIGHT));
      double t = (aC + dcs) / aS;
      State data = (State) state.getDataAsCustom();
      if (data == null) {
        data = new State(attrs);
        data.setPosition(t, attrs);
        state.setData(data);
      } else {
        data.setPosition(t, attrs);
      }
      state.queueForPropagation();
    }

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      mouseDragged(state, e);
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      AttributeSet attrs = state.getAttributeSet();
      State data = (State) state.getDataAsCustom();
      if (data == null) {
        return;
      } else if (state.getAttributeValue(RETURN_TO_ZERO)) {
        data.returnToZero(attrs);
      } else {
        data.snapToValidPosition(attrs);
      }
      state.queueForPropagation();
    }

    @Override
    public void paint(InstancePainter painter) {
      paintSlider(painter, true, true);
    }

  }

  private static final int MARGIN = 4;
  private static final int HEIGHT = 70;
  
  public static final Attribute<Boolean> RETURN_TO_ZERO
      = Attributes.forBoolean("returnToZero", S.getter("returnToZero"));

  protected static final Color DEFAULT_BACKGROUND_COLOR = new Color(226, 214, 182);
  protected static final Color HANDLE_COLORS[] = {
    new Color(0x66,0xA9,0xA9), // metalic teal
    new Color(0x44,0x44,0xAF), // blue
    new Color(0x86,0x56,0x56), // red-gray
    new Color(0xFF,0xFF,0xCC), // light yellow
  };
 
  /* used by Dial */
  protected Slider(String name, StringGetter displayName) {
    super(name, displayName);
  }

  public Slider() {
    super("Slider", S.getter("sliderComponent"));
    setAttributes(new Attribute[] {
      StdAttr.FACING, Io.ATTR_COLOR, StdAttr.WIDTH, StdAttr.MODE, RETURN_TO_ZERO,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] {
        Direction.SOUTH, DEFAULT_BACKGROUND_COLOR,
        BitWidth.create(8), StdAttr.UNSIGNED_OPTION, false,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIconName("slider.png");
    setInstanceLogger(Logger.class);
    setInstancePoker(SliderPoker.class);
    setPorts(new Port[] { new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH) });
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.computeLabelTextField(Instance.AVOID_RIGHT);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    return Bounds.create(-10, 0, 20, HEIGHT).rotate(Direction.SOUTH, facing, 0, 0);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      instance.computeLabelTextField(Instance.AVOID_RIGHT);
    } else if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_RIGHT);
    } else { // if (attr == StdAttr.MODE || attr == StdAttr.WIDTH || attr == RETURN_TO_ZERO) {
      instance.fireInvalidated();
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds().expand(-1);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(bds.x, bds.y, bds.width, bds.height);
  }

  private static void drawTick(Graphics g, int x, int y, double t, int a, int b, Color color, boolean upright) {
    // x, y is bottom left corner of bounds
    g.setColor(color);
    int a0 = MARGIN;
    int aS = HEIGHT - 2*MARGIN;
    double v = a0 + t*aS;
    if (upright)
      ((Graphics2D)g).draw(new Line2D.Double(x+a, y-v, x+b, y-v));
    else
      ((Graphics2D)g).draw(new Line2D.Double(x+v, y-a, x+v, y-b));
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    boolean colorized = painter.shouldDrawColor();
    boolean showState = painter.getShowState();
    paintSlider(painter, colorized, showState);
  }

  static void paintSlider(InstancePainter painter, boolean colorized, boolean showState) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();

    AttributeSet attrs = painter.getAttributeSet();
    BitWidth bw = attrs.getValue(StdAttr.WIDTH);
    AttributeOption mode = attrs.getValue(StdAttr.MODE);
    Direction facing = attrs.getValue(StdAttr.FACING);
    Color slideColor = attrs.getValue(Io.ATTR_COLOR);

    int x = bds.getX();
    int y = bds.getY();
    int w = bds.getWidth();
    int h = bds.getHeight();

    Color tickColor, pinColor;
    if (!colorized) {
      slideColor = Color.WHITE;
      tickColor = Color.DARK_GRAY;
      pinColor = Color.BLACK;
    } else {
      pinColor = Meter.pickContrasting(slideColor, HANDLE_COLORS);
      tickColor = Meter.pickTicks(slideColor, Color.DARK_GRAY);
    }

    boolean upright = (facing == Direction.NORTH || facing == Direction.SOUTH);

    g.setColor(slideColor);
    g.fillRect(x+1, y+1, w-2, h-2);

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x+1, y+1, w-2, h-2);
    GraphicsUtil.switchToWidth(g, 1);

    int ctr = (upright ? w/2 : h/2);
    int a0 = 3;
    int aR = (upright ? h-6 : w-6);
    
    GraphicsUtil.switchToWidth(g, 1);
    Meter.RangedValue r = new Meter.RangedValue(null, bw, mode);
    if (bw.getWidth() <= 3) {
      for (long v = r.min; v <= r.max; v++) {
        double tt = (v - r.min) * 1.0 / (r.max - r.min);
        if (v == 0 && mode == StdAttr.SIGNED_OPTION)
          drawTick(g, x, y+h, tt, ctr-4, ctr+4, tickColor, upright);
        else
          drawTick(g, x, y+h, tt, ctr-2, ctr+2, tickColor, upright);
      }
      if (bw.getWidth() == 1) {
        // draw a fake middle tick
        drawTick(g, x, y+h, 0.5, ctr-2, ctr+2, tickColor, upright);
      }
    } else {
      for (int i = 0; i < 8; i++) {
        double tt = i / 7.0;
        drawTick(g, x, y+h, tt, ctr-2, ctr+2, tickColor, upright);
      }
      if (mode == StdAttr.SIGNED_OPTION) {
        // draw a zero tick, in different style
        double tt = (0 - r.min) * 1.0 / (r.max - r.min);
        drawTick(g, x, y+h, tt, ctr-4, ctr+4, tickColor, upright);
      }
    }

    if (showState) {
      State data = (State) painter.getDataAsCustom();
      if (data == null) {
        data = new State(attrs);
        painter.setData(data);
      }
      double t = data.getPosition();
      GraphicsUtil.switchToWidth(g, 3);
      drawTick(g, x, y+h, t, ctr-5, ctr+5, pinColor.darker(), upright);
      GraphicsUtil.switchToWidth(g, 1);
      drawTick(g, x, y+h, t, ctr-4, ctr+4, pinColor.brighter(), upright);
    }

    painter.drawLabel();
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    AttributeSet attrs = state.getAttributeSet();
    State data = (State) state.getDataAsCustom();
    Value val;
    if (data == null) {
      data = new State(attrs);
      state.setData(data);
    }
    val = data.getValue(attrs);
    state.setPort(0, val, 1);
  }

  /* also used by Dial */
  static class State implements ComponentData {
    private double pos; // [ 0 ... 1.0 ]
    private Value val;
    
    // These 3 are saved only for re-validation during propagation
    private BitWidth bw;
    private AttributeOption mode;
    private boolean centering;

    public State(AttributeSet attrs) {
      returnToZero(attrs);
    }

    State(State other) {
      pos = other.pos;
      val = other.val;
      bw = other.bw;
      mode = other.mode;
      centering = other.centering;
    }
    
    private void save(AttributeSet attrs) {
      bw = attrs.getValue(StdAttr.WIDTH);
      mode = attrs.getValue(StdAttr.MODE);
      centering = attrs.getValue(RETURN_TO_ZERO);
    }

    public synchronized Value getValue(AttributeSet attrs) {
      BitWidth bw0 = attrs.getValue(StdAttr.WIDTH);
      AttributeOption mode0 = attrs.getValue(StdAttr.MODE);
      boolean centering0 = attrs.getValue(RETURN_TO_ZERO);
      if ((bw != bw0) || (mode != mode0) || (centering != centering0)) {
        if (centering0) {
          returnToZero(attrs);
        } else {
          recomputeValue(attrs); 
          snapToValidPosition(attrs);
        }
      }
      return val;
    }

    public double getPosition() { return pos; }

    // set val to zero, and recompute pos
    synchronized void returnToZero(AttributeSet attrs) {
      save(attrs);
      val = Value.createKnown(bw, 0);
      snapToValidPosition(attrs);
    }
    
    // use current val to recompute pos
    synchronized void snapToValidPosition(AttributeSet attrs) {
      save(attrs);
      Meter.RangedValue pt = new Meter.RangedValue(val, bw, mode);
      double t = (pt.val - pt.min)*1.0/(pt.max - pt.min);
      pos = Math.max(0.0, Math.min(t, 1.0));
    }

    // set pos, and use pos to recompute val
    synchronized void setPosition(double t, AttributeSet attrs) {
      save(attrs);
      pos = Math.max(0.0, Math.min(t, 1.0));
      recomputeValue(attrs);
    }

    // use pos to recompute val
    synchronized void recomputeValue(AttributeSet attrs) {
      save(attrs);
      int b = bw.getWidth();
      if (mode == StdAttr.SIGNED_OPTION) {
        long min = -(1L << (b - 1));     // -2^(b-1)
        long span = (1L << b) - 1;       //  2^b - 1  == max - min
        long q = Math.round(pos * span); // 0..span
        int v = (int)(min + q);
        val = Value.createKnown(bw, v);
      } else {
        long max = (1L << b) - 1;        // 0 .. 2^b - 1
        long q = Math.round(pos * max);
        val = Value.createKnown(bw, (int)q);
      }
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }
  }

}
