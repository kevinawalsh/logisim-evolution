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

public class DriveStrength {
  public final String desc, xml, ma;
  private DriveStrength(String d, String m) { desc = xml = d; ma = m; };
  private DriveStrength(String d, String x, String m) { desc = d; xml = x; ma = m; };

	public static final DriveStrength DEFAULT  = new DriveStrength("Default", "default", "");
	public static final DriveStrength DRIVE_2  = new DriveStrength("2 mA", "2");
	public static final DriveStrength DRIVE_4  = new DriveStrength("4 mA", "4");
	public static final DriveStrength DRIVE_8  = new DriveStrength("8 mA", "8");
	public static final DriveStrength DRIVE_16 = new DriveStrength("16 mA", "16");
	public static final DriveStrength DRIVE_24 = new DriveStrength("24 mA", "24");
	public static final DriveStrength UNKNOWN  = new DriveStrength("Unknown", "unknown", "");
  public static final DriveStrength[] OPTIONS = { DEFAULT,
    DRIVE_2, DRIVE_4, DRIVE_8, DRIVE_16, DRIVE_24 };

  public static DriveStrength get(String desc) {
    if (desc == null || desc.isEmpty())
      return DEFAULT;
    for (DriveStrength p : OPTIONS)
      if (p.desc.equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "").equalsIgnoreCase(desc)
          || p.ma.equals(desc))
        return p;
    return UNKNOWN;
  }

  @Override
  public String toString() { return desc; }
}
