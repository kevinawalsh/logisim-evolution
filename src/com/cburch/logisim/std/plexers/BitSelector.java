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

package com.cburch.logisim.std.plexers;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics2D;

import com.bfh.logisim.hdlgenerator.HDLSupport;
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
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class BitSelector extends InstanceFactory {
  public static final Attribute<BitWidth> GROUP_ATTR = Attributes
      .forBitWidth("group", S.getter("bitSelectorGroupAttr"));

  public BitSelector() {
    super("BitSelector", S.getter("bitSelectorComponent"));
    setAttributes(new Attribute[] { StdAttr.FACING, StdAttr.WIDTH,
      GROUP_ATTR }, new Object[] { Direction.EAST,
        BitWidth.create(8), BitWidth.ONE });
    setKeyConfigurator(JoinedConfigurator.create(new BitWidthConfigurator(
            GROUP_ATTR, 1, Value.MAX_WIDTH, 0), new BitWidthConfigurator(
              StdAttr.WIDTH)));

    setIconName("bitSelector.gif");
    setFacingAttribute(StdAttr.FACING);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    Bounds base = Bounds.create(-30, -15, 30, 30);
    return base.rotate(Direction.EAST, facing, 0, 0);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new BitSelectorHDLGenerator(ctx);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      updatePorts(instance);
    } else if (attr == StdAttr.WIDTH || attr == GROUP_ATTR) {
      updatePorts(instance);
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Plexers.drawTrapezoid(painter.getGraphics(), painter.getNominalBounds(),
        painter.getAttributeValue(StdAttr.FACING), 9);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    Direction facing = painter.getAttributeValue(StdAttr.FACING);

    Plexers.drawTrapezoid(g, painter.getNominalBounds(), facing, 9);
    Bounds bds = painter.getNominalBounds();
    g.setColor(Color.BLACK);
    GraphicsUtil.drawCenteredText(g, "Sel",
        bds.getX() + bds.getWidth() / 2,
        bds.getY() + bds.getHeight() / 2);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Value data = state.getPortValue(1);
    Value select = state.getPortValue(2);
    BitWidth groupBits = state.getAttributeValue(GROUP_ATTR);
    Value group;
    if (!select.isFullyDefined()) {
      group = Value.createUnknown(groupBits);
    } else {
      int shift = select.toIntValue() * groupBits.getWidth();
      if (shift >= data.getWidth()) {
        group = Value.createKnown(groupBits, 0);
      } else if (groupBits.getWidth() == 1) {
        group = data.get(shift);
      } else {
        Value[] bits = new Value[groupBits.getWidth()];
        for (int i = 0; i < bits.length; i++) {
          if (shift + i >= data.getWidth()) {
            bits[i] = Value.FALSE;
          } else {
            bits[i] = data.get(shift + i);
          }
        }
        group = Value.create(bits);
      }
    }
    state.setPort(0, group, Plexers.DELAY);
  }

  private void updatePorts(Instance instance) {
    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    BitWidth data = instance.getAttributeValue(StdAttr.WIDTH);
    BitWidth group = instance.getAttributeValue(GROUP_ATTR);
    int groups = (data.getWidth() + group.getWidth() - 1)
        / group.getWidth() - 1;
    int selectBits = 1;
    if (groups > 0) {
      while (groups != 1) {
        groups >>= 1;
        selectBits++;
      }
    }
    BitWidth select = BitWidth.create(selectBits);

    Location inPt;
    Location selPt;
    if (facing == Direction.WEST) {
      inPt = Location.create(30, 0);
      selPt = Location.create(10, 10);
    } else if (facing == Direction.NORTH) {
      inPt = Location.create(0, 30);
      selPt = Location.create(-10, 10);
    } else if (facing == Direction.SOUTH) {
      inPt = Location.create(0, -30);
      selPt = Location.create(-10, -10);
    } else {
      inPt = Location.create(-30, 0);
      selPt = Location.create(-10, 10);
    }

    Port[] ps = new Port[3];
    ps[OUT] = new Port(0, 0, Port.OUTPUT, group.getWidth());
    ps[IN] = new Port(inPt.getX(), inPt.getY(), Port.INPUT, data.getWidth());
    ps[SEL] = new Port(selPt.getX(), selPt.getY(), Port.INPUT,
        select.getWidth());
    ps[OUT].setToolTip(S.getter("bitSelectorOutputTip"));
    ps[IN].setToolTip(S.getter("bitSelectorDataTip"));
    ps[SEL].setToolTip(S.getter("bitSelectorSelectTip"));
    instance.setPorts(ps);
  }

  static final int OUT = 0;
  static final int IN = 1;
  static final int SEL = 2;
}
