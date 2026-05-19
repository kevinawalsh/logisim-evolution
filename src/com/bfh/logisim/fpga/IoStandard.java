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

public class IoStandard {
  public final String desc, xml;
  private IoStandard(String d) { desc = xml = d; }
  private IoStandard(String d, String x) { desc = d; xml = x; }

	public static final IoStandard DEFAULT  = new IoStandard("Default", "default");
	public static final IoStandard LVCMOS12 = new IoStandard("LVCMOS12");
	public static final IoStandard LVCMOS15 = new IoStandard("LVCMOS15");
	public static final IoStandard LVCMOS18 = new IoStandard("LVCMOS18");
	public static final IoStandard LVCMOS25 = new IoStandard("LVCMOS25");
	public static final IoStandard LVCMOS33 = new IoStandard("LVCMOS33");
	public static final IoStandard LVTTL    = new IoStandard("LVTTL");
  // FIXME: Implement this... 
  // Arbitrary strings are also allowed. An aribitrary VAL is either passed
  // through to the toolchain as-is, or mapped to some toolchain-specific FLAG
  // through toolchain-specific mechanism, or by specifying "io-standard-VAL:
  // FLAG" in the board's toolchain parameters.

  public static final IoStandard[] OPTIONS = { DEFAULT,
    LVCMOS12, LVCMOS15, LVCMOS18, LVCMOS25, LVCMOS33, LVTTL };

  public static IoStandard get(String desc) {
    if (desc == null || desc.trim().isEmpty())
      return DEFAULT;
    desc = desc.trim();
    for (IoStandard p : OPTIONS)
      if (p.desc.equalsIgnoreCase(desc))
        return p;
    return new IoStandard(desc, desc);
  }

  @Override
  public String toString() { return desc; }

}
