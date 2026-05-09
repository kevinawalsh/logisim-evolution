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

package com.bfh.logisim.fpga;

public class PinOrdering {
  public final int gang;
  public final String order, desc;
  private PinOrdering(int g, String o) { gang = g; order = o; desc = g + "x " + o; }

	public static final PinOrdering ORDER_2_LRTB = new PinOrdering(2, "Left-Right, Top-Bottom");
	public static final PinOrdering ORDER_2_RLBT = new PinOrdering(2, "Right-Left, Bottom-Top");
	public static final PinOrdering ORDER_2_TBRL = new PinOrdering(2, "Top-Bottom, Right-Left");
	public static final PinOrdering ORDER_2_BTLR = new PinOrdering(2, "Bottom-Top, Left-Right");
	public static final PinOrdering ORDER_1_LR = new PinOrdering(1, "Left-Right");
	public static final PinOrdering ORDER_1_RL = new PinOrdering(1, "Right-Left");
	public static final PinOrdering ORDER_1_TB = new PinOrdering(1, "Top-Bottom");
	public static final PinOrdering ORDER_1_BT = new PinOrdering(1, "Bottom-Top");
	public static final PinOrdering ORDER_3_LRTB = new PinOrdering(3, "Left-Right, Top-Bottom");
	public static final PinOrdering ORDER_3_RLBT = new PinOrdering(3, "Right-Left, Bottom-Top");
	public static final PinOrdering ORDER_3_TBRL = new PinOrdering(3, "Top-Bottom, Right-Left");
	public static final PinOrdering ORDER_3_BTLR = new PinOrdering(3, "Bottom-Top, Left-Right");

  public static final PinOrdering[] OPTIONS = { 
    ORDER_2_LRTB, ORDER_2_RLBT, ORDER_2_TBRL, ORDER_2_BTLR,
    ORDER_1_LR, ORDER_1_RL, ORDER_1_TB, ORDER_1_BT,
    ORDER_3_LRTB, ORDER_3_RLBT, ORDER_3_TBRL, ORDER_3_BTLR,
  };

  public static PinOrdering get(String desc) {
    for (PinOrdering p : OPTIONS)
      if (p.desc.equalsIgnoreCase(desc))
        return p;
    return null;
  }

  @Override
  public String toString() { return desc; }
}
