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

public class PinActivity {
  public final String desc, xml;

  public static final PinActivity ACTIVE_LOW = new PinActivity("Active low", "active-low");
  public static final PinActivity ACTIVE_HIGH = new PinActivity("Active high", "active-high");
  public static final PinActivity UNKNOWN = new PinActivity("Unknown", "unknown");
  public static final PinActivity[] OPTIONS = { ACTIVE_LOW, ACTIVE_HIGH };

  private PinActivity(String d, String x) { desc = d; xml = x; }

  public static PinActivity get(String desc) {
    if (desc == null || desc.isEmpty())
      return ACTIVE_HIGH;
    for (PinActivity p : OPTIONS)
      if (p.desc.equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "-").equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "").equalsIgnoreCase(desc))
        return p;
    if (desc.equalsIgnoreCase("high")) return ACTIVE_HIGH;
    if (desc.equalsIgnoreCase("low")) return ACTIVE_LOW;
    if (desc.equalsIgnoreCase("positive")) return ACTIVE_HIGH;
    if (desc.equalsIgnoreCase("negative")) return ACTIVE_LOW;
    if (desc.equalsIgnoreCase("pos")) return ACTIVE_HIGH;
    if (desc.equalsIgnoreCase("neg")) return ACTIVE_LOW;
    return UNKNOWN;
  }
  
  @Override
  public String toString() { return desc; }
}
