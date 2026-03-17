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

package com.cburch.logisim.std.gates;
import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Direction;

class NegateAttribute extends Attribute<Boolean> {
  private static Attribute<Boolean> BOOLEAN_ATTR = Attributes
      .forBoolean("negateDummy");

  int index;
  private Direction side;

  public NegateAttribute(int index, Direction side) {
    super("negate" + index, null);
    this.index = index;
    this.side = side;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof NegateAttribute) {
      NegateAttribute o = (NegateAttribute) other;
      return this.index == o.index && this.side == o.side;
    } else {
      return false;
    }
  }

  @Override
  public java.awt.Component getCellEditor(Boolean value) {
    return BOOLEAN_ATTR.getCellEditor(null, value);
  }

  @Override
  public String getDisplayName() {
    String ret = S.fmt("gateNegateAttr", "" + (index + 1));
    if (side != null) {
      ret += " (" + side.toVerticalDisplayString() + ")";
    }
    return ret;
  }

  @Override
  public int hashCode() {
    return index * 31 + (side == null ? 0 : side.hashCode());
  }

  @Override
  public Boolean parse(String value) {
    return BOOLEAN_ATTR.parse(value);
  }

  @Override
  public String toDisplayString(Boolean value) {
    return BOOLEAN_ATTR.toDisplayString(value);
  }

  @Override
  public Domain getDomain() { return Domain.ofList(new String[] {"true", "false"}); }
}
