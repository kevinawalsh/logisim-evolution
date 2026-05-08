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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.access.InventoryFeature;
import com.cburch.logisim.comp.Component;
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
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class DipSwitch extends InstanceFactory {

  public static class Poker extends InstancePoker {

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      State val = (State) state.getDataAsCustom();
      Location loc = state.getInstance().getLocation();
      Direction facing = state.getInstance().getAttributeValue(StdAttr.FACING);
      int n = state.getInstance().getAttributeValue(ATTR_SIZE).getWidth();
      int i;
      if (facing == Direction.SOUTH) {
        i = n + (e.getX() - loc.getX() - 5) / 10;
      }  else if (facing == Direction.EAST) {
        i = (e.getY() - loc.getY() - 5) / 10;
      }  else if (facing == Direction.WEST) {
        i = (loc.getY() - e.getY() - 5) / 10;
      }  else {
        i = (e.getX() - loc.getX() - 5) / 10;
      }
      val.ToggleBit(i);
      state.queueForPropagation();
    }
  }

  private static class State implements ComponentData {

    private int Value;
    private int size;

    public State(int value, int size) {
      Value = value;
      this.size = size;
    }

    public boolean BitSet(int bitindex) {
      if (bitindex >= size) {
        return false;
      }
      int mask = 1 << bitindex;
      return (Value & mask) != 0;
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(Value, size);
    }

    public void ToggleBit(int bitindex) {
      if ((bitindex < 0) || (bitindex >= size)) {
        return;
      }
      int mask = 1 << bitindex;
      Value ^= mask;
    }
  }

  // FIXME: this should never have been a BitWidth. It should be Integer.
  public static final int MAX_SWITCH = 32;
  public static final int DEF_SWITCH = 8;
  public static final int MIN_SWITCH = 2;
  public static final Attribute<BitWidth> ATTR_SIZE = Attributes
      .forBitWidth("number", S.getter("nrOfSwitch"), MIN_SWITCH, MAX_SWITCH);

  public DipSwitch() {
    super("DipSwitch", S.getter("dipswitchComponent"));
    setAttributes(new Attribute[] { StdAttr.FACING, StdAttr.LABEL, StdAttr.LABEL_LOC,
      StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR, ATTR_SIZE },
      new Object[] {Direction.NORTH, "", Direction.EAST, StdAttr.DEFAULT_LABEL_FONT,
        Color.BLACK, BitWidth.create(DEF_SWITCH) });
    setFacingAttribute(StdAttr.FACING);
    setIconName("dipswitch.gif");
    setKeyConfigurator(JoinedConfigurator.create(
          new BitWidthConfigurator(ATTR_SIZE), // should be like common gate number of input, not bitwidth
          new DirectionConfigurator(StdAttr.LABEL_LOC)));
    setInstancePoker(Poker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  private void updatePorts(Instance instance) {
    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    int n = instance.getAttributeValue(ATTR_SIZE).getWidth();
    int cx = 0, cy = 0, dx = 0, dy = 0;
    if (facing == Direction.WEST) {
      // cy = -10*(n+1); dy = 10;
      dy = -10;
    } else if (facing == Direction.EAST) {
      // cy = 10*(n+1); dy = -10;
      dy = 10;
    } else if (facing == Direction.SOUTH) {
      cx = -10*(n+1); dx = 10;
    } else {
      dx = 10;
    }
    Port[] ps = new Port[n];
    for (int i = 0; i < ps.length; i++) {
      ps[i] = new Port(cx+(i+1)*dx, cy+(i+1)*dy, Port.OUTPUT, 1);
      ps[i].setToolTip(S.getter("DIP"+(i+1)));
    }
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    int n = attrs.getValue(ATTR_SIZE).getWidth();
    return Bounds.create(0, 0, (n+1)*10, 40).rotate(Direction.NORTH, facing, 0, 0);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return ButtonHDLGenerator.forDipSwitch(ctx);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "DipSwitch"; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == ATTR_SIZE) {
      instance.recomputeBounds();
      updatePorts(instance);
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      updatePorts(instance);
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    State state = (State) painter.getDataAsCustom();
    if (state == null || state.size != painter.getAttributeValue(ATTR_SIZE).getWidth()) {
      int val = (state == null) ? 0 : state.Value;
      state = new State(val, painter.getAttributeValue(ATTR_SIZE).getWidth());
      painter.setData(state);
    }
    int n = painter.getAttributeValue(ATTR_SIZE).getWidth();

    Direction facing = painter.getAttributeValue(StdAttr.FACING);
    Location loc = painter.getLocation();
    int x = loc.getX();
    int y = loc.getY();
    if (facing == Direction.SOUTH) {
      x -= 10*(n+1);
      y -= 40;
    } else if (facing == Direction.EAST) {
    } else if (facing == Direction.WEST) {
    }
    Graphics2D g = painter.getGraphics();
    g.translate(x, y);
    double rotate = 0.0;
    if (facing != Direction.NORTH && facing != Direction.SOUTH) {
      rotate = -facing.getRight().toRadians();
      g.rotate(rotate);
    }

    Bounds bds = painter.getNominalBounds().expand(-1);
    g.setColor(Color.darkGray);
    g.fillRect(1, 1, (n+1)*10-2, 40-2);

    g.setColor(Color.white);
    g.setFont(StdAttr.DEFAULT_LABEL_FONT);
    if (n > 9) {
      g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() * 0.6f));
    }
    for (int i = 0; i < n; i++) {
      g.fillRect(7+(i*10), 16, 6, 20);
      String s = Integer.toString(i + 1);
      GraphicsUtil.drawCenteredText(g, s, 9+(i*10), 8);
    }
    g.setColor(Color.GRAY);
    for (int i = 0; i < n; i++) {
      int ypos = (state.BitSet(i)) ? 17 : 26;
      g.fillRect(8+(i*10), ypos, 4, 9);
    }

    if (rotate != 0.0) {
      g.rotate(-rotate);
    }
    g.translate(-x, -y);

    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    State pins = (State) state.getDataAsCustom();
    if (pins == null || pins.size != state.getAttributeValue(ATTR_SIZE).getWidth()) {
      int val = (pins == null) ? 0 : pins.Value;
      pins = new State(val, state.getAttributeValue(ATTR_SIZE).getWidth());
      state.setData(pins);
    }
    for (int i = 0; i < pins.size; i++) {
      Value pinstate = (pins.BitSet(i)) ? Value.TRUE : Value.FALSE;
      state.setPort(i, pinstate, 1);
    }
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
      notes.put("number", "determines the count of output ports, and their layout when facing=south.");
      return notes;
    }

    @Override
    public List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs) {
      return List.of("number");
    }

    @Override
    public List<InventoryFeature.PortPosition> getCustomPortLayout(AttributeSet attrs) {
      Direction facing = attrs.getValue(StdAttr.FACING);
      
      InventoryFeature.PortPosition out;

      if (facing == Direction.EAST) {
        out = portsAt("DIP", "output", 1, "number", 0, 10, 0, 10);
      } else if (facing == Direction.WEST) {
        out = portsAt("DIP", "output", 1, "number", 0, -10, 0, -10);
      } else if (facing == Direction.NORTH) {
        out = portsAt("DIP", "output", 1, "number", 10, 0, 10, 0);
      } else { // SOUTH
        out = portsAt("DIP", "output", 1, "number", "-10*number", 0, 10, 0);
      }

      return List.of(out);
    }
  }
}
