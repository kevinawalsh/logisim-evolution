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

package com.cburch.logisim.circuit;

import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;

// The subcircuit factory for a circuit uses SubcircuitAttributes, which holds
// parameters related to a specific instance, like FACING and LABEL.
// By contrast, a circuit has one CircuitAttributes to hold parameters related
// to the circuit itself, like CIRCUIT_NAME and CIRCUIT_APPEARANCE.
public class SubcircuitAttributes extends AttributeSets.ArrayBacked {

  private static Attribute<?>[] INSTANCE_ATTRS = {
    StdAttr.FACING, StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT,
  };

  static final Object[] INSTANCE_DEFAULTS = {
    Direction.EAST, /* overwritten below */
    "", Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT
  };

  private Instance[] pinInstances;

  public SubcircuitAttributes(Circuit source) {
    super(INSTANCE_ATTRS, INSTANCE_DEFAULTS);
    updateAttr(StdAttr.FACING, source.getAppearance().getFacing());
    pinInstances = new Instance[0];
  }

  void setPinInstances(Instance[] value) { pinInstances = value; }
  public Instance[] getPinInstances() { return pinInstances; }

}
