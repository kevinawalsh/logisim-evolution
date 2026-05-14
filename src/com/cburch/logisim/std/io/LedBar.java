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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.access.InventoryFeature;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
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
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.tools.key.IntegerConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

// Future improvements:
// - support multi-colored bars?

public class LedBar extends InstanceFactory implements DynamicElementProvider {

  public static class Logger extends InstanceLogger {
    
    @Override
    public String getLogName(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.LABEL);
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return BitWidth.create(state.getAttributeValue(ATTR_SEGMENTS));
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      Value data = state.getDataAsValue();
      if (data == null)
        return Value.createUnknown(getBitWidth(state, option));
      else
        return data;
    }
  }

  static final AttributeOption INPUT_AS_WIRES = new AttributeOption("separated",
      S.getter("ioLedBarSeparated"));
  static final AttributeOption INPUT_AS_BUS = new AttributeOption("bus",
      S.getter("ioLedBarBus"));
  static final Attribute<AttributeOption> ATTR_INPUT_TYPE = Attributes
      .forOption("inputtype", S.getter("ioLedBarInput"),
          new AttributeOption[] { INPUT_AS_WIRES, INPUT_AS_BUS });

  static final AttributeOption SHAPE_RECT = new AttributeOption("rectangular",
      S.getter("ioLedBarShapeRectangle"));
  static final AttributeOption SHAPE_ROUND = new AttributeOption("round",
      S.getter("ioLedBarShapeRound"));
  static final Attribute<AttributeOption> ATTR_LED_SHAPE = Attributes
      .forOption("shape", S.getter("ioLedBarShape"),
          new AttributeOption[] { SHAPE_RECT, SHAPE_ROUND });

  static final Attribute<Integer> ATTR_SEGMENTS = Attributes
      .forIntegerRange("segments", S.getter("ioLedBarSegments"), 1, Value.MAX_WIDTH);

  static final Color DEFAULT_ON_COLOR = new Color(0, 0xff, 0xcc);
  static final Color DEFAULT_OFF_COLOR = Color.GRAY;

  public LedBar() {
    super("LEDBar", S.getter("ledBarComponent"));
    setAttributes(new Attribute<?>[] { StdAttr.FACING,
      ATTR_INPUT_TYPE, ATTR_SEGMENTS,
      Io.ATTR_ACTIVE, Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR, ATTR_LED_SHAPE,
      StdAttr.LABEL, StdAttr.LABEL_EDGE_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR
    }, new Object[] { Direction.EAST,
      INPUT_AS_WIRES, Integer.valueOf(8),
        true, DEFAULT_ON_COLOR, DEFAULT_OFF_COLOR, SHAPE_RECT,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK
    });
    setFacingAttribute(StdAttr.FACING);
    setIconName("ledbar.png");
    setInstanceLogger(Logger.class);
    setKeyConfigurator(JoinedConfigurator.create(
          new IntegerConfigurator(ATTR_SEGMENTS, 1, Value.MAX_WIDTH, 0),
          new DirectionConfigurator(StdAttr.LABEL_EDGE_LOC)));
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
    return getLedBarOffsetBounds(attrs);
  }

  static Bounds getLedBarOffsetBounds(AttributeSet attrs) {
    Object input = attrs.getValue(ATTR_INPUT_TYPE);
    int n = attrs.getValue(ATTR_SEGMENTS).intValue();
    boolean drawSquare = attrs.getValue(ATTR_LED_SHAPE) == SHAPE_RECT;
    int w = drawSquare ? 10 : 20;
    int h = drawSquare ? 30 : 20;
    Direction facing = attrs.getValue(StdAttr.FACING);
    if (input == INPUT_AS_WIRES)
      return Bounds.create(-w/2, -h, w * n, h).rotate(Direction.EAST, facing, 0, 0);
    else // INPUT_AS_BUS
      return Bounds.create(0, -h/2, w * n, h).rotate(Direction.EAST, facing, 0, 0);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_SEGMENTS || attr == ATTR_INPUT_TYPE
        || attr == ATTR_LED_SHAPE || attr == StdAttr.FACING) {
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
    Graphics2D g = painter.getGraphics();
    Value data = painter.getDataAsValue();
    Bounds bds = painter.getNominalBounds();
    boolean colorized = painter.shouldDrawColor();
    boolean showState = painter.getShowState();

    paintLedBar(g, data, bds, painter.getAttributeSet(), true, showState, 2);
    
    painter.drawLabel();
    painter.drawPorts();
  }

  static void paintLedBar(Graphics2D g, Value data, Bounds bds,
      AttributeSet attrs, boolean colorized, boolean showState, int borderWidth) {

    int cols = attrs.getValue(ATTR_SEGMENTS);
    boolean activeHigh = attrs.getValue(Io.ATTR_ACTIVE).booleanValue();
    Value on = activeHigh ? Value.TRUE : Value.FALSE;
    Value off = activeHigh ? Value.FALSE : Value.TRUE;
    Color onColor = colorized ? attrs.getValue(Io.ATTR_ON_COLOR) : Color.DARK_GRAY;
    Color offColor = colorized ? attrs.getValue(Io.ATTR_OFF_COLOR) : Color.WHITE;
    Color errColor = colorized ? Value.ERROR_COLOR : Color.LIGHT_GRAY;
    boolean drawSquare = attrs.getValue(ATTR_LED_SHAPE) == SHAPE_RECT;
    int w = drawSquare ? 10 : 20;
    int h = drawSquare ? 30 : 20;
    Direction facing = attrs.getValue(StdAttr.FACING);
    boolean upright = (facing == Direction.NORTH || facing == Direction.SOUTH);

    int ww, hh;
    if (drawSquare && upright) { ww = h; hh = w; }
    else { ww = w; hh = h; }

    int x0, y0, dx, dy;
    if (facing == Direction.EAST) {
      x0 = bds.x + ww/2; y0 = bds.y + hh/2;
      dx = ww; dy = 0;
    } else if (facing == Direction.WEST) {
      x0 = bds.x + bds.width - ww/2; y0 = bds.y + hh/2;
      dx = -ww; dy = 0;
    } else if (facing == Direction.NORTH) {
      x0 = bds.x + ww/2; y0 = bds.y + bds.height - hh/2;
      dx = 0; dy = -hh;
    } else {
      x0 = bds.x + ww/2; y0 = bds.y + hh/2;
      dx = 0; dy = hh;
    }

    if (data == null)
      data = Value.UNKNOWN;
    Value[] vals = data.getAll();

    for (int i = 0; i < cols; i++) {
      int x = x0 + dx * (cols - i - 1);
      int y = y0 + dy * (cols - i - 1);
      if (showState) {
        Value val = (i < vals.length) ? vals[i] : Value.UNKNOWN;
        if (val == on)
          g.setColor(onColor);
        else if (val == off || val == Value.UNKNOWN)
          g.setColor(offColor);
        else
          g.setColor(errColor);
      } else {
        g.setColor(Color.GRAY);
      }
      if (drawSquare)
        g.fillRect(x - (ww/2+1)/2, y - (hh/2+1)/2, (ww/2+1), (hh/2+1));
      else
        g.fillOval(x - 8, y - 8, 16, 16);
    }

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, borderWidth);
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
  }
  
  @Override
  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new LedBarShape(x, y, path);
  }

  @Override
  public void propagate(InstanceState state) {
    Object type = state.getAttributeValue(ATTR_INPUT_TYPE);
    if (type == INPUT_AS_WIRES) {
      int cols = state.getAttributeValue(ATTR_SEGMENTS).intValue();
      Value[] vals = new Value[cols];
      for (int i = 0; i < cols; i++) {
        vals[i] = state.getPortValue(cols - i - 1);
        if (vals[i] == Value.NIL)
          vals[i] = Value.UNKNOWN;
      }
      state.setData(Value.create(vals));
    } else { // INPUT_AS_BUS
      Value vals = state.getPortValue(0);
      state.setData(vals);
    }
  }

  private void updatePorts(Instance instance) {
    Object input = instance.getAttributeValue(ATTR_INPUT_TYPE);
    int cols = instance.getAttributeValue(ATTR_SEGMENTS).intValue();
    boolean drawSquare = instance.getAttributeValue(ATTR_LED_SHAPE) == SHAPE_RECT;
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
        boolean drawSquare = attrs.getValue(ATTR_LED_SHAPE) == SHAPE_RECT;
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
