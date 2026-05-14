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

import java.util.List;

import java.awt.Color;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;

class CounterAttributes extends AttributeSets.ArrayBacked {

  public CounterAttributes() {
    // WARNING: WIDTH must come early, before INIT or MAX. The values of INIT and MAX are
    // constrained by WIDTH, so during xml file loading WiDTH must be set before them.
    super(new Attribute<?>[] { StdAttr.WIDTH, Counter.ATTR_INIT, 
      Counter.ATTR_MAX, Counter.ATTR_ON_GOAL, StdAttr.EDGE_TRIGGER,
      StdAttr.LABEL, StdAttr.LABEL_EDGE_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR,
      Register.ATTR_SHOW_IN_TAB, StdAttr.APPEARANCE },
      new Object[] { BitWidth.create(8), 0, Integer.valueOf(0xFF),
        Counter.ON_GOAL_WRAP, StdAttr.TRIG_RISING, "",
        Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK,
        true, StdAttr.APPEAR_CLASSIC});
  }
  
  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.WIDTH) {
      BitWidth oldWidth = getValue(StdAttr.WIDTH);
      super.updateAttr(attr, value);
      // if width changes, update initial and max accordingly
      BitWidth newWidth = (BitWidth) value;
      Value oldInitial = Value.createKnown(oldWidth, getValue(Counter.ATTR_INIT));
      Value newInitial = oldInitial.extendWidth(newWidth.getWidth(), oldInitial.get(oldInitial.getWidth() - 1));
      setAttr(Counter.ATTR_INIT, newInitial.toIntValue());
      int oldMax = getValue(Counter.ATTR_MAX);
      int newMax;
      if (newWidth.getWidth() > oldWidth.getWidth())
        newMax = Integer.valueOf(newWidth.getMask());
      else
        newMax = oldMax & newWidth.getMask();
      setAttr(Counter.ATTR_MAX, newMax);
    } else if (attr == Counter.ATTR_MAX) {
      // if max changes, ensure it fits within existing width
      BitWidth width = getValue(StdAttr.WIDTH);
      int newVal = ((Integer) value).intValue() & width.getMask();
      super.updateAttr(Counter.ATTR_MAX, newVal);
    } else if (attr == Counter.ATTR_INIT) {
      // if initial changes, ensure it fits within existing width
      BitWidth width = getValue(StdAttr.WIDTH);
      int newVal = ((Integer) value).intValue() & width.getMask();
      super.updateAttr(Counter.ATTR_INIT, newVal);
    } else {
      super.updateAttr(attr, value);
    }
  }

  @Override
  public <V> List<Attribute<?>> getAttributesForUndo(Attribute<V> attr, V newValue) {
    if (attr == StdAttr.WIDTH)
      return List.of(Counter.ATTR_INIT, Counter.ATTR_MAX, StdAttr.WIDTH);
    else
      return null;
  }
}
