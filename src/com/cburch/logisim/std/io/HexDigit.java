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

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.tools.key.IntegerConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;

public class HexDigit extends InstanceFactory implements DynamicElementProvider {

  protected static final int HEX = 0;
  protected static final int DP = 1;

  static final int MAX_DIGITS = SevenSegment.MAX_DIGITS;
  static final Attribute<Integer> ATTR_DIGITS = SevenSegment.ATTR_DIGITS;
  static final Attribute<Boolean> ATTR_ENABLES_ACTIVE = SevenSegment.ATTR_ENABLES_ACTIVE;
  static final Attribute<Integer> ATTR_PERSIST = SevenSegment.ATTR_PERSIST;

  public HexDigit() {
    super("Hex Digit Display", S.getter("hexDigitComponent"));
    setIconName("hexdig.gif");
    setKeyConfigurator(JoinedConfigurator.create(
        new IntegerConfigurator(ATTR_DIGITS, 1, MAX_DIGITS, 0),
        new DirectionConfigurator(StdAttr.LABEL_LOC)));
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new HexDigitAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int digits = attrs.getValue(ATTR_DIGITS).intValue();
    int w = 40*digits;
    return Bounds.create(5 - w/2, -60, w, 60);
  }

  private void updatePorts(Instance instance) {
    int digits = instance.getAttributeValue(ATTR_DIGITS).intValue();
    Port[] ps = new Port[2 + (digits == 1 ? 0 : digits)];
    ps[HEX] = new Port(0, 0, Port.INPUT, 4);
    ps[DP] = new Port(20, 0, Port.INPUT, 1);
    ps[HEX].setToolTip(S.getter("hexDigitDataTip"));
    ps[DP].setToolTip(S.getter("hexDigitDPTip"));
    if (digits > 1) {
      int w = digits*40;
      for (int i = 0; i < digits; i++) {
        ps[DP+1+i] = new Port(20+(1+i)*10, 0, Port.INPUT, 1);
        ps[DP+1+i].setToolTip(S.getter("Enable_Digit_" + (i+1)));
      }
    }
    instance.setPorts(ps);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    instance.computeLabelTextField(0);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(0);
    } else if (attr == ATTR_DIGITS) {
      instance.recomputeBounds();
      updatePorts(instance);
      instance.computeLabelTextField(0);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    SevenSegment.drawBase(painter);
  }

  private static SevenSegment.State getState(InstanceState state) {
    long clock = state.getTickCount();

    SevenSegment.State data = (SevenSegment.State) state.getDataAsCustom();
    if (data == null) {
      data = new SevenSegment.State(clock);
      state.setData(data);
    } else {
      // data.update(clock);
    }
    return data;
  }

  @Override
  public void propagate(InstanceState state) {
    SevenSegment.State data = getState(state);
    int digits = state.getAttributeValue(ATTR_DIGITS).intValue();
    long clock = state.getTickCount();
    long persist = clock + (digits == 1 ? -1 : state.getAttributeValue(ATTR_PERSIST).intValue());

    Boolean activeEnables = state.getAttributeValue(ATTR_ENABLES_ACTIVE);
    Value desiredEnables = digits == 1 || activeEnables == null || activeEnables.booleanValue() ? Value.TRUE : Value.FALSE;

    int summary = 0;
    Value baseVal = state.getPortValue(HEX);
    if (baseVal == null)
      baseVal = Value.createUnknown(BitWidth.create(4));
    Value dpVal = state.getPortValue(DP);
    int segs; // each nibble is one segment, in top-down, left-to-right
    // order: middle three nibbles are the three horizontal segments
    switch (baseVal.toIntValue()) {
      case 0x0: segs = 0x1110111; break;
      case 0x1: segs = 0x0000011; break;
      case 0x2: segs = 0x0111110; break;
      case 0x3: segs = 0x0011111; break;
      case 0x4: segs = 0x1001011; break;
      case 0x5: segs = 0x1011101; break;
      case 0x6: segs = 0x1111101; break;
      case 0x7: segs = 0x0010011; break;
      case 0x8: segs = 0x1111111; break;
      case 0x9: segs = 0x1011011; break;
      case 0xA: segs = 0x1111011; break;
      case 0xB: segs = 0x1101101; break;
      case 0xC: segs = 0x1110100; break;
      case 0xD: segs = 0x0101111; break;
      case 0xE: segs = 0x1111100; break;
      case 0xF: segs = 0x1111000; break;
      default: segs = 0x0001000; break; // a dash '-'
    }
    boolean on[] = new boolean[8];
    on[2] = ((segs & 0x1) != 0); // vertical seg in bottom right
    on[1] = ((segs & 0x10) != 0); // vertical seg in top right
    on[3] = ((segs & 0x100) != 0); // horizontal seg at bottom
    on[6] = ((segs & 0x1000) != 0); // horizontal seg at middle
    on[0] = ((segs & 0x10000) != 0); // horizontal seg at top
    on[4] = ((segs & 0x100000) != 0); // vertical seg at bottom left
    on[5] = ((segs & 0x1000000) != 0); // vertical seg at top left
    on[7] = (dpVal != null && dpVal.toIntValue() == 1); // decimal point
    
    if (digits == 1) {
      data.setDigit(0, on, true, persist);
    } else {
      for (int digit = 0; digit < digits; digit++) {
        Value en = state.getPortValue(2+digit);
        if (en == desiredEnables)
          data.setDigit(digit, on, true, persist);
        else
          data.setDigit(digit, null, false, persist);
      }
    }

  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    return new HexDigitHDLGenerator(ctx);
  }

  @Override
  public String getHDLNamePrefix(Component comp) { return "HexDigit"; }

  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new HexDigitShape(x, y, path);
  }
}
