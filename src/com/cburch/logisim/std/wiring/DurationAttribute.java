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

package com.cburch.logisim.std.wiring;
import static com.cburch.logisim.std.Strings.S;

import javax.swing.JTextField;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.util.StringGetter;

public class DurationAttribute extends Attribute<Integer> {
  private int min;
  private int max;

  public DurationAttribute(String name, StringGetter disp, int min, int max) {
    super(name, disp);
    this.min = min;
    this.max = max;
  }

  @Override
  public java.awt.Component getCellEditor(Integer value) {
    JTextField field = new JTextField();
    field.setText(value.toString());
    return field;
  }

  @Override
  public Integer parse(String value) {
    try {
      Integer ret = Integer.valueOf(value);
      if (ret.intValue() < min) {
        throw new NumberFormatException(S.fmt("durationSmallMessage", "" + min));
      } else if (ret.intValue() > max) {
        throw new NumberFormatException(S.fmt("durationLargeMessage", "" + max));
      }
      return ret;
    } catch (NumberFormatException e) {
      throw new NumberFormatException(S.get("freqInvalidMessage"));
    }
  }

  @Override
  public String toDisplayString(Integer value) {
    if (value.equals(Integer.valueOf(1))) {
      return S.get("clockDurationOneValue");
    } else {
      return S.fmt("clockDurationValue", value.toString());
    }
  }

  @Override
  public Domain getDomain() {
    return Domain.ofIntRange(min, max);
  }

}
