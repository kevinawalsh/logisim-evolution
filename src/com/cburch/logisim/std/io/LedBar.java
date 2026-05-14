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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.access.InventoryFeature;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class LedBar extends InstanceFactory {

  private static class State implements ComponentData {
    private int cols;
    private Value[] grid;
    private long[] persistTo;

    public State(int cols, long curClock) {
      this.cols = -1;
      updateSize(cols, curClock);
    }

    State(State other) {
      cols = other.cols;
      grid = other.grid.clone();
      persistTo = other.persistTo.clone();
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }

    private Value get(int col, long curTick) {
      Value ret = grid[col];
      if (ret == Value.FALSE && persistTo[col] - curTick >= 0)
        ret = Value.TRUE;
      return ret;
    }

    private void setColumn(int col, Value val, long persist, Value on, Value off) {
      int stride = -cols;
      if (grid[col] == Value.TRUE)
        persistTo[col] = persist - 1;
      grid[col] = (val == on ? Value.TRUE : val == off ? Value.FALSE : val);
      if (val == on)
        persistTo[col] = persist;
    }

    private void updateSize(int cols, long curClock) {
      if (this.cols != cols) {
        this.cols = cols;
        grid = new Value[cols];
        persistTo = new long[cols];
        Arrays.fill(grid, Value.UNKNOWN);
        Arrays.fill(persistTo, curClock - 1);
      }
    }
  }

  static final AttributeOption INPUT_AS_WIRES = new AttributeOption("separated",
      S.getter("ioLedBarSeparated"));
  static final AttributeOption INPUT_AS_BUS = new AttributeOption("bus",
      S.getter("ioLedBarBus"));
  static final Attribute<AttributeOption> ATTR_INPUT_TYPE = Attributes
      .forOption("inputtype", S.getter("ioLedBarInput"),
          new AttributeOption[] { INPUT_AS_WIRES, INPUT_AS_BUS });
  
  static final Attribute<Integer> ATTR_SEGMENTS = Attributes
      .forIntegerRange("segments", S.getter("ioLedBarSegments"), 1, Value.MAX_WIDTH);

  static final AttributeOption SHAPE_SQUARE = DotMatrix.SHAPE_SQUARE;
  static final AttributeOption SHAPE_CIRCLE = DotMatrix.SHAPE_CIRCLE;
  static final Attribute<AttributeOption> ATTR_DOT_SHAPE = DotMatrix.ATTR_DOT_SHAPE;
  static final Attribute<Integer> ATTR_PERSIST = DotMatrix.ATTR_PERSIST;

  static final Color DEFAULT_ON_COLOR = new Color(0, 0xff, 0xcc);
  static final Color DEFAULT_OFF_COLOR = Color.GRAY;

  public LedBar() {
    super("LEDBar", S.getter("ledBarComponent"));
    setAttributes(new Attribute<?>[] { StdAttr.FACING,
      ATTR_INPUT_TYPE, ATTR_SEGMENTS,
      Io.ATTR_ACTIVE, Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
      ATTR_PERSIST, ATTR_DOT_SHAPE,
      StdAttr.LABEL, StdAttr.LABEL_EDGE_LOC,
      StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR
    }, new Object[] { Direction.EAST,
      INPUT_AS_WIRES, Integer.valueOf(8),
        true, DEFAULT_ON_COLOR, DEFAULT_OFF_COLOR,
        Integer.valueOf(0), SHAPE_SQUARE,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK
    });
    setFacingAttribute(StdAttr.FACING);
    setIconName("ledbar.png");
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_EDGE_LOC));
  }

  @Override
  public boolean ActiveOnHigh(AttributeSet attrs) {
    return attrs.getValue(Io.ATTR_ACTIVE);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    positionLabel(instance);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Object input = attrs.getValue(ATTR_INPUT_TYPE);
    int n = attrs.getValue(ATTR_SEGMENTS).intValue();
    boolean drawSquare = attrs.getValue(ATTR_DOT_SHAPE) == SHAPE_SQUARE;
    int w = drawSquare ? 10 : 20;
    int h = drawSquare ? 30 : 20;
    Direction facing = attrs.getValue(StdAttr.FACING);
    if (input == INPUT_AS_WIRES)
      return Bounds.create(-w/2, -h, w * n, h).rotate(Direction.EAST, facing, 0, 0);
    else // INPUT_AS_BUS
      return Bounds.create(0, -h/2, w * n, h).rotate(Direction.EAST, facing, 0, 0);
  }

  private State getState(InstanceState state) {
    int cols = state.getAttributeValue(ATTR_SEGMENTS).intValue();
    long clock = state.getTickCount();

    State data = (State) state.getDataAsCustom();
    if (data == null) {
      data = new State(cols, clock);
      state.setData(data);
    } else {
      data.updateSize(cols, clock);
    }
    return data;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_SEGMENTS || attr == ATTR_INPUT_TYPE
        || attr == ATTR_DOT_SHAPE || attr == StdAttr.FACING) {
      instance.recomputeBounds();
      updatePorts(instance);
      positionLabel(instance);
    } else if (attr == StdAttr.LABEL_EDGE_LOC) {
      positionLabel(instance);
    }
  }

  private void positionLabel(Instance instance) {
    if (instance.getAttributeValue(ATTR_INPUT_TYPE) == INPUT_AS_WIRES)
      instance.computeLabelTextField(Instance.AVOID_BOTTOM);
    else
      instance.computeLabelTextField(Instance.AVOID_RIGHT);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Color onColor = painter.getAttributeValue(Io.ATTR_ON_COLOR);
    Color offColor = painter.getAttributeValue(Io.ATTR_OFF_COLOR);
    boolean drawSquare = painter.getAttributeValue(ATTR_DOT_SHAPE) == SHAPE_SQUARE;
    int w = drawSquare ? 10 : 20;
    int h = drawSquare ? 30 : 20;
    int x0, y0;
    if (painter.getAttributeValue(ATTR_INPUT_TYPE) == INPUT_AS_WIRES) {
      x0 = 0; y0 = -h/2;
    } else {
      x0 = w/2; y0 = 0;
    }

    State data = getState(painter);
    long ticks = painter.getTickCount();
    Bounds bds = painter.getNominalBounds();
    boolean showState = painter.getShowState();

    Graphics2D g = painter.getGraphics();

    Direction facing = painter.getAttributeValue(StdAttr.FACING);
    Location loc = painter.getLocation();
    
    double radians = facing.toRadians();
    g.translate(loc.getX(), loc.getY());
    g.rotate(-radians);

    int cols = data.cols;
    for (int i = 0; i < cols; i++) {
      int x = x0 + w * i;
      int y = y0;
      if (showState) {
        Value val = data.get(i, ticks);
        if (val == Value.TRUE)
          g.setColor(onColor);
        else if (val == Value.FALSE)
          g.setColor(offColor);
        else
          g.setColor(Value.ERROR_COLOR);
      } else {
        g.setColor(Color.GRAY);
      }
      if (drawSquare)
        g.fillRect(x - 3, y - 8, 6, 16);
      else
        g.fillOval(x - 8, y - 8, 16, 16);
    }

    g.rotate(radians);
    g.translate(-loc.getX(), -loc.getY());

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Object type = state.getAttributeValue(ATTR_INPUT_TYPE);
    int cols = state.getAttributeValue(ATTR_SEGMENTS).intValue();
    long clock = state.getTickCount();
    long persist = clock + state.getAttributeValue(ATTR_PERSIST).intValue();
    boolean activeHigh = state.getAttributeValue(Io.ATTR_ACTIVE).booleanValue();
    Value on = activeHigh ? Value.TRUE : Value.FALSE;
    Value off = activeHigh ? Value.FALSE : Value.TRUE;

    State data = getState(state);
    if (type == INPUT_AS_WIRES) {
      for (int i = 0; i < cols; i++)
        data.setColumn(i, state.getPortValue(i), persist, on, off);
    } else { // INPUT_AS_BUS
      Value[] vals = state.getPortValue(0).getAll();
      for (int i = 0; i < cols; i++)
        data.setColumn(cols - i - 1, i < vals.length ? vals[i] : Value.UNKNOWN, persist, on, off);
    }
  }

  private void updatePorts(Instance instance) {
    Object input = instance.getAttributeValue(ATTR_INPUT_TYPE);
    int cols = instance.getAttributeValue(ATTR_SEGMENTS).intValue();
    boolean drawSquare = instance.getAttributeValue(ATTR_DOT_SHAPE) == SHAPE_SQUARE;
    Port[] ps;
    if (input == INPUT_AS_WIRES) {
      Direction facing = instance.getAttributeValue(StdAttr.FACING);
      int w = drawSquare ? 10 : 20;
      Location dxy = Location.create(w, 0).rotate(Direction.EAST, facing, 0, 0);
      ps = new Port[cols];
      for (int i = 0; i < cols; i++) {
        ps[i] = new Port(dxy.x * i, dxy.y * i, Port.INPUT, 1);
        ps[i].setToolTip(S.getter("ioLedBarWireInput", ""+i));
      }
    } else { // INPUT_AS_BUS
      ps = new Port[1];
      ps[0] = new Port(0, 0, Port.INPUT, cols);
      ps[0].setToolTip(S.getter("ioLedBarBusInput"));
    }
    instance.setPorts(ps);
  }

  @Override
  public Object getFeature(Object key, AttributeSet attrs) {
    if (key == InventoryFeature.class)
      return new MyInventoryFeature();
    return super.getFeature(key, attrs);
  }

  private class MyInventoryFeature implements InventoryFeature {
    
    @Override
    public Map<String, String> getAttributeNotes(AttributeSet attrs) {
      HashMap<String, String> notes = new HashMap<>();
      notes.put("inputtype", "when inputtype='separated' uses one input per LED");
      notes.put("segments", "determines the count of LEDs, and when inputtype='separated' also the number of input ports");
      return notes;
    }

    @Override
    public List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs) {
      return List.of("segments");
    }

    @Override
    public List<InventoryFeature.PortPosition> getCustomPortLayout(AttributeSet attrs) {
      Object itype = attrs.getValue(ATTR_INPUT_TYPE);
      if (itype == INPUT_AS_WIRES) {
        boolean drawSquare = attrs.getValue(ATTR_DOT_SHAPE) == SHAPE_SQUARE;
        int w = drawSquare ? 10 : 20;
        InventoryFeature.PortPosition cols;
        cols = portsAt("LED_", "input", 0, "cols", 0, 0, w, 0);
        return List.of(cols);
      } else { // INPUT_AS_BUS
        InventoryFeature.PortPosition cs;
        cs = portAt("LEDs", "input", 0, 0);
        return List.of(cs);
      }
    }
  }
}
