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

package com.cburch.logisim.std.audio;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.SplitterAttributes;
import com.cburch.logisim.comp.ComponentListingFeature;
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
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.plexers.Plexers;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class LineSelect extends InstanceFactory {
  
  static final Attribute<Integer> ATTR_SPACING = SplitterAttributes.ATTR_SPACING;
  
  static final Attribute<Integer> ATTR_INPUTS =
      Attributes.forIntegerRange("inputs", S.getter("audioLineSelectInputs"), 2, 20);

  static final AttributeOption SELECT_1 = new AttributeOption("one", S.getter("audioLineSelect1"));
  static final AttributeOption SELECT_N = new AttributeOption("many", S.getter("audioLineSelectN"));

  static final Attribute<AttributeOption> ATTR_BEHAVIOR = Attributes.forOption(
      "behavior", S.getter("audioLineSelectBehavior"), new AttributeOption[] {
        SELECT_1, SELECT_N });

  static final AttributeOption DEFAULT_SMIN = new AttributeOption("signedmin", S.getter("audioLineSelectDefaultSMin"));
  static final AttributeOption DEFAULT_ZERO = new AttributeOption("zero", S.getter("audioLineSelectDefaultZero"));
  static final AttributeOption DEFAULT_UMID = new AttributeOption("midpoint", S.getter("audioLineSelectDefaultUMid"));
  static final AttributeOption DEFAULT_SMAX = new AttributeOption("signedmax", S.getter("audioLineSelectDefaultSMax"));
  static final AttributeOption DEFAULT_UMAX = new AttributeOption("unsignedmax", S.getter("audioLineSelectDefaultUMax"));
  static final AttributeOption DEFAULT_ERR = new AttributeOption("error", S.getter("audioLineSelectDefaultErr"));
  static final AttributeOption DEFAULT_UND = new AttributeOption("undefined", S.getter("audioLineSelectDefaultUnd"));

  static final Attribute<AttributeOption> ATTR_DEFAULTVAL = Attributes.forOption(
      "default", S.getter("audioLineSelectDefaultValue"), new AttributeOption[] {
        DEFAULT_SMIN, DEFAULT_ZERO, DEFAULT_UMID,
            DEFAULT_SMAX, DEFAULT_UMAX, DEFAULT_ERR, DEFAULT_UND, });

  public LineSelect() {
    super("LineSelect", S.getter("audioLineSelectComponent"));
    setAttributes(new Attribute[] {
      StdAttr.FACING, ATTR_SPACING, Plexers.ATTR_SIZE, ATTR_INPUTS, ATTR_BEHAVIOR, StdAttr.WIDTH, StdAttr.MODE, ATTR_DEFAULTVAL },
      new Object[] { Direction.EAST, Integer.valueOf(1), Plexers.SIZE_NARROW, Integer.valueOf(5), SELECT_N, BitWidth.EIGHT, StdAttr.UNSIGNED_OPTION, DEFAULT_ZERO });
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIconName("lineselect.png");
    setFacingAttribute(StdAttr.FACING);
    setInstancePoker(Poker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    boolean wide = attrs.getValue(Plexers.ATTR_SIZE) == Plexers.SIZE_WIDE;
    int inputs = attrs.getValue(ATTR_INPUTS).intValue();
    Direction dir = attrs.getValue(StdAttr.FACING);
    int spacing = attrs.getValue(ATTR_SPACING).intValue();

    int w = (wide ? 30 : 20);
    int h = Math.max(40, 20+10*spacing*(inputs-1));
    int a = ((h % 20) == 0) ? 0 : 5; // asymmetrical adjustemtn

    return Bounds.create(-w, -h/2+a, w, h).rotate(Direction.EAST, dir, 0, 0);
  }

  private void updatePorts(Instance instance) {
    boolean wide = instance.getAttributeValue(Plexers.ATTR_SIZE) == Plexers.SIZE_WIDE;
    int inputs = instance.getAttributeValue(ATTR_INPUTS).intValue();
    Direction dir = instance.getAttributeValue(StdAttr.FACING);
    int spacing = instance.getAttributeValue(ATTR_SPACING).intValue();
      
    int w = (wide ? 30 : 20);
    int h = Math.max(40, 20+10*spacing*(inputs-1));
    int a = ((h % 20) == 0) ? 0 : 5;

    Port[] ps = new Port[inputs + 1];
    ps[0] = new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH);

    int x, y, dx, dy;
    if (dir == Direction.EAST) {
      x = -w;
      y = -h/2+a + 10;
      dx = 0;
      dy = 10*spacing;
    } else if (dir == Direction.WEST) {
      x = w;
      y = -(-h/2+a + 10);
      dx = 0;
      dy = -10*spacing;
    } else if (dir == Direction.NORTH) {
      y = w;
      x = -h/2+a + 10;
      dx = 10*spacing;
      dy = 0;
    } else { // SOUTH
      y = -w;
      x = -(-h/2+a + 10);
      dx = -10*spacing;
      dy = 0;
    }

    for (int i = 0; i < inputs; i++) {
      int j = (inputs == 2 && i == 1 && spacing == 1) ? i+1 : i;
      ps[1+i] = new Port(x+j*dx, y+j*dy, Port.INPUT, StdAttr.WIDTH);
      ps[1+i].setToolTip(S.getter("multiplexerInTip", "" + i));
    }

    instance.setPorts(ps);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING || attr == Plexers.ATTR_SIZE || attr == ATTR_INPUTS || attr == ATTR_SPACING) {
      instance.recomputeBounds();
      updatePorts(instance);
    } else if (attr == StdAttr.WIDTH || attr == ATTR_SPACING) {
      updatePorts(instance);
    } else if (attr == ATTR_DEFAULTVAL || attr == StdAttr.MODE || attr == ATTR_BEHAVIOR) {
      instance.fireInvalidated();
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Bounds bds = painter.getNominalBounds();
    boolean wide = painter.getAttributeValue(Plexers.ATTR_SIZE) == Plexers.SIZE_WIDE;
    Direction dir = painter.getAttributeValue(StdAttr.FACING);
    drawTrapezoid(painter.getGraphics(), bds, dir, wide);
  }

  static void drawTrapezoid(Graphics2D g, Bounds bds, Direction dir, boolean wide) {
    int lean = (wide ? 7 : 4);
    Plexers.drawTrapezoid(g, bds, dir, lean);
  }

  static final Color RED = new Color(255, 0, 102);

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    boolean wide = painter.getAttributeValue(Plexers.ATTR_SIZE) == Plexers.SIZE_WIDE;
    Direction dir = painter.getAttributeValue(StdAttr.FACING);
    int inputs = painter.getAttributeValue(ATTR_INPUTS).intValue();
    AttributeOption behavior = painter.getAttributeValue(ATTR_BEHAVIOR);
    int spacing = painter.getAttributeValue(ATTR_SPACING).intValue();

    drawTrapezoid(g, bds, dir, wide);
    GraphicsUtil.switchToWidth(g, 1);

    int xo = 0, yo = 0;
    if (dir == Direction.EAST) xo = 10;
    else if (dir == Direction.WEST) xo = -10;
    else if (dir == Direction.NORTH) yo = -10;
    else yo = 10;

    int n = 0;
    int mask = painter.getShowState() ? painter.getDataOrDefault(0) : 0;
    for (int i = 0; i < inputs; i++) {
      Location pt = painter.getComponent().getEnd(i+1).getLocation();
      int x = pt.x + xo, y = pt.y + yo;
      if (behavior == SELECT_N) {
        g.setColor((mask & (1<<i)) != 0 ? RED : Color.LIGHT_GRAY);
        if (yo == 0) {
          g.fillRect(x - 6, y - 3, 12, 6);
          g.setColor(Color.DARK_GRAY);
          g.drawRect(x - 6, y - 3, 12, 6);
        } else {
          g.fillRect(x - 3, y - 6, 6, 12);
          g.setColor(Color.DARK_GRAY);
          g.drawRect(x - 3, y - 6, 6, 12);
        }
      } else { // SELECT_1
        boolean on = ((mask & (1 << i)) != 0) && (n == 0);
        if (on) n++;
        g.setColor(on ? RED : Color.LIGHT_GRAY);
        if (yo == 0) {
          g.fill(new Ellipse2D.Double(x - 6, y - 3, 12, 6));
          g.setColor(Color.DARK_GRAY);
          g.draw(new Ellipse2D.Double(x - 6, y - 3, 12, 6));
        } else {
          g.fill(new Ellipse2D.Double(x - 3, y - 6, 6, 12));
          g.setColor(Color.DARK_GRAY);
          g.draw(new Ellipse2D.Double(x - 3, y - 6, 6, 12));
        }
      }
    }

    g.setColor(Color.BLACK);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {

    BitWidth bw = state.getAttributeValue(StdAttr.WIDTH);
    int w = bw.getWidth();
    int inputs = state.getAttributeValue(ATTR_INPUTS).intValue();
    boolean signed = state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;

    int mask = state.getDataOrDefault(0);
    int n = 0;
    long sum = 0;
    Value out = null;
    for (int i = 0; i < inputs; i++) {
      if ((mask & (1<<i)) != 0) {
        out = state.getPortValue(1+i);
        if (out.isFullyDefined()) {
          sum += out.extendAsLong(signed);
          n++;
        }
      }
    }

    if (n == 1) {
      // out was set above
    } else if (n > 1) {
      // multiple selection, compute average
      out = Value.createKnown(bw, (int)(sum/n)); 
    } else {
      // no valid selection
      AttributeOption def = state.getAttributeValue(ATTR_DEFAULTVAL);
      if (def == DEFAULT_SMIN)
        out = Value.createKnown(bw, 1 << (w-1));
      else if (def == DEFAULT_ZERO)
        out = Value.createKnown(bw, 0);
      else if (def == DEFAULT_UMID)
        out = Value.createKnown(bw, 1 << (w-1));
      else if (def == DEFAULT_SMAX)
        out = Value.createKnown(bw, 0x7fffffff >> (32-w));
      else if (def == DEFAULT_UMAX)
        out = Value.createKnown(bw, 0xffffffff);
      else if (def == DEFAULT_ERR)
        out = Value.createError(bw);
      else
        out = Value.createUnknown(bw);
    }
    state.setPort(0, out, 1);
  }

  public static class Poker extends InstancePoker {

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      int ex = e.getX(), ey = e.getY();
      int inputs = state.getAttributeValue(ATTR_INPUTS).intValue();
      Direction dir = state.getAttributeValue(StdAttr.FACING);
      int spacing = state.getAttributeValue(ATTR_SPACING).intValue();
      AttributeOption behavior = state.getAttributeValue(ATTR_BEHAVIOR);

      int xo = 0, yo = 0;
      if (dir == Direction.EAST) xo = 10;
      else if (dir == Direction.WEST) xo = -10;
      else if (dir == Direction.NORTH) yo = -10;
      else yo = 10;
    
      int sel = -1;
      for (int i = 0; sel < 0 && i < inputs; i++) {
        Location pt = state.getInstance().getComponent().getEnd(i+1).getLocation();
        int x = pt.x + xo, y = pt.y + yo;

        int hitboxWidth = (xo != 0 || spacing > 1) ? 18 : 10;
        int hitboxHeight = (yo != 0 || spacing > 1) ? 18 : 10;

        if ((Math.abs(ex - x) <= hitboxWidth/2)
            && (Math.abs(ey - y) <= hitboxHeight/2)) {
          sel = i;
        }
      }
      if (sel == -1)
        return;
      int bit = (1<<sel);
      int mask = state.getDataOrDefault(0);
      if (behavior == SELECT_1)
        mask = mask & bit;
      if ((mask & bit) != 0) {
        state.setData(mask & ~bit);
      } else {
        state.setData(mask | bit);
      }
      state.queueForPropagation();
    }
  }

  @Override
  public Object getFeature(Object key, AttributeSet attrs) {
    if (key == ComponentListingFeature.class)
      return new MyComponentListingFeature();
    return super.getFeature(key, attrs);
  }

  private static class MyComponentListingFeature implements ComponentListingFeature {

    @Override
    public List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs) {
      return List.of("spacing", "inputs");
    }

    @Override
    public List<List<Map.Entry<String, Object>>> getCustomPortLayout(AttributeSet attrs) {
      Object appear = attrs.getValue(Plexers.ATTR_SIZE);
      Object facing = attrs.getValue(StdAttr.FACING);

      // [
      //   {
      //     "name": "OUT",
      //     "type": "output",
      //     "dx": 0,
      //     "dy": 0
      //   },
      //   {
      //     "name": "Input_",
      //     "type": "input",
      //     "count": "inputs",
      //     "first_index": 1,
      //     "first_dx": ...,
      //     "first_dy": ...,
      //     "step_dx": ...,
      //     "step_dy": ...
      //   }
      // ]
      ArrayList<Map.Entry<String, Object>> bus = new ArrayList<>();
      bus.add(new AbstractMap.SimpleEntry<>("name", "OUT"));
      bus.add(new AbstractMap.SimpleEntry<>("type", "output"));
      bus.add(new AbstractMap.SimpleEntry<>("dx", 0));
      bus.add(new AbstractMap.SimpleEntry<>("dy", 0));

      ArrayList<Map.Entry<String, Object>> in0 = new ArrayList<>();
      in0.add(new AbstractMap.SimpleEntry<>("name", "Input_0"));
      in0.add(new AbstractMap.SimpleEntry<>("type", "input"));
      if (facing == Direction.EAST) {
        in0.add(new AbstractMap.SimpleEntry<>("dx", appear == Plexers.SIZE_WIDE ? -30 : -20));
        in0.add(new AbstractMap.SimpleEntry<>("dy", "-10*min(1, floor(spacing*(inputs-1)/2))"));
      }

      ArrayList<Map.Entry<String, Object>> inI = new ArrayList<>();
      inI.add(new AbstractMap.SimpleEntry<>("name", "Input_"));
      inI.add(new AbstractMap.SimpleEntry<>("type", "input"));
      inI.add(new AbstractMap.SimpleEntry<>("count", "inputs-1"));
      inI.add(new AbstractMap.SimpleEntry<>("first_index", 1));
      if (facing == Direction.EAST) {
        inI.add(new AbstractMap.SimpleEntry<>("first_dx", appear == Plexers.SIZE_WIDE ? -30 : -20));
        inI.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
        inI.add(new AbstractMap.SimpleEntry<>("first_dy", "10*min(1, ceil(spacing*(inputs-1)/2))-10*(inputs-2)*spacing"));
        inI.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
      } else if (facing == Direction.WEST) {
        inI.add(new AbstractMap.SimpleEntry<>("first_dx", appear == Plexers.SIZE_WIDE ? 30 : 20));
        inI.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
        inI.add(new AbstractMap.SimpleEntry<>("first_dy", "-10*min(1, ceil(spacing*(inputs-1)/2))+10*(inputs-2)*spacing"));
        inI.add(new AbstractMap.SimpleEntry<>("step_dy", "-10*spacing"));
      } else if (facing == Direction.NORTH) {
        inI.add(new AbstractMap.SimpleEntry<>("first_dx", "10*min(1, ceil(spacing*(inputs-1)/2))-10*(inputs-2)*spacing"));
        inI.add(new AbstractMap.SimpleEntry<>("step_dx", "10*spacing"));
        inI.add(new AbstractMap.SimpleEntry<>("first_dy", appear == Plexers.SIZE_WIDE ? 30 : 20));
        inI.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
      } else if (facing == Direction.SOUTH) {
        inI.add(new AbstractMap.SimpleEntry<>("first_dx", "-10*min(1, ceil(spacing*(inputs-1)/2))+10*(inputs-2)*spacing"));
        inI.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
        inI.add(new AbstractMap.SimpleEntry<>("first_dy", appear == Plexers.SIZE_WIDE ? -30 : -20));
        inI.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
      }
      return List.of(bus, in0, inI);
    }
  }

}
