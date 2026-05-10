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

// FIXME: this should be PullResistors, not PullBehavior
// Also include an explicit keeper/latching option
public class PullBehavior {
  public final String desc, xml, altera, xilinx, lattice;

  public static final PullBehavior NONE = new PullBehavior("Without Pull", "none", null, null, null);
  public static final PullBehavior FLOAT = new PullBehavior("Float", "float", "TRI-STATED", "FLOAT", "KEEPER");
  public static final PullBehavior PULL_UP = new PullBehavior("Pull Up", "up", "PULLUP", "PULLUP", "UP");
  public static final PullBehavior PULL_DOWN = new PullBehavior("Pull Down", "down", "PULLDOWN", "PULLDOWN", "DOWN");
  public static final PullBehavior UNKNOWN = new PullBehavior("Unknown", "unknown", "", "FLOAT", "NONE");
  public static final PullBehavior[] OPTIONS = { FLOAT, PULL_UP, PULL_DOWN };

  private PullBehavior(String d, String m, String a, String x, String l) { desc = d; xml = m; altera = a; xilinx = x; lattice = l;}

  public static PullBehavior get(String desc) {
    if (desc == null || desc.isEmpty() || desc.equalsIgnoreCase("none") || desc.equalsIgnoreCase("without pull"))
      return NONE;
    for (PullBehavior p : OPTIONS)
      if (p.desc.equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "-").equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "").equalsIgnoreCase(desc))
        return p;
    if (desc.equalsIgnoreCase("up")) return PULL_UP;
    if (desc.equalsIgnoreCase("down")) return PULL_DOWN;
    if (desc.equalsIgnoreCase("float")) return FLOAT;
    if (desc.equalsIgnoreCase("tristate")) return FLOAT;
    if (desc.equalsIgnoreCase("tri-state")) return FLOAT;
    if (desc.equalsIgnoreCase("none")) return NONE;
    if (desc.equalsIgnoreCase("without")) return NONE;
    if (desc.equalsIgnoreCase("without-pull")) return NONE;
    if (desc.equalsIgnoreCase("without pull")) return NONE;
    return UNKNOWN;
  }

  @Override
  public String toString() { return desc; }
}
