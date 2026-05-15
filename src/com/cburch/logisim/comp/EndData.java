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

package com.cburch.logisim.comp;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;

public class EndData {
  public static final int INPUT_ONLY = 1;
  public static final int OUTPUT_ONLY = 2;
  public static final int INPUT_OUTPUT = 3;

  private final Location loc;
  private final BitWidth width;
  private final int i_o;
  private final boolean exclusive;

  public EndData(Location loc, BitWidth width, int type) {
    this(loc, width, type, type == OUTPUT_ONLY);
  }

  // FIXME: is this constructor really necessary? Can we eliminate it?
  public EndData(Location loc, BitWidth width, int type, boolean exclusive) {
    this.loc = loc;
    this.width = width;
    this.i_o = type;
    this.exclusive = exclusive;
  }

  @Override
  public String toString() {
    return "EndData{loc="+loc+", width="+width+", "+
      (i_o == INPUT_ONLY ? "input-only" :
       i_o == OUTPUT_ONLY ? "output-only" :
       i_o == INPUT_OUTPUT ? "input-output" : "illegal-type")+", exclusive="+exclusive+"}";
  }

  public int hashCode() {
    return loc.hashCode() + 31 * width.hashCode()
        + 31*31*(exclusive?1:0);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof EndData))
      return false;
    if (other == this)
      return true;
    EndData o = (EndData) other;
    return o.loc.equals(this.loc) && o.width.equals(this.width)
        && o.i_o == this.i_o && o.exclusive == this.exclusive;
  }

  public Location getLocation() {
    return loc;
  }

  public int getType() {
    return i_o;
  }

  public BitWidth getWidth() {
    return width;
  }

  public boolean isExclusive() {
    return exclusive;
  }

  public boolean canInput() {
    return (i_o == INPUT_ONLY) || (i_o == INPUT_OUTPUT);
  }

  public boolean canOutput() {
    return (i_o == OUTPUT_ONLY) || (i_o == INPUT_OUTPUT);
  }

  public boolean isInputOnly() {
    return (i_o == INPUT_ONLY);
  }

  public boolean isOutputOnly() {
    return (i_o == OUTPUT_ONLY);
  }

  public boolean isBidir() {
    return (i_o == INPUT_OUTPUT);
  }
}
