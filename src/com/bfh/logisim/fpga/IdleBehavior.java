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

public enum IdleBehavior {

  // the unmapped output (or bidir) pin should be driven to its inactive or active level
  DRIVE_INACTIVE("drive to inactive level", "drive-inactive"), // the default
  DRIVE_ACTIVE("drive to active level", "drive-active"),
  // the unmapped output (or bidir) pin should be driven to a low or high level
  DRIVE_LOW("drive low", "drive-low"),
  DRIVE_HIGH("drive high", "drive-high"),
  // the unmapped output (or bidir) pin should be pulled by resistor to an inactive or active level
  PULL_INACTIVE("pull to inactive level", "pull-inactive"),
  PULL_ACTIVE("pull to active level", "pull-active"),
  // the unmapped output (or bidir) pin should be pulled up with a resistor
  PULL_DOWN("pull down", "pull-down"),
  PULL_UP("pull up", "pull-up"),
  // the unmapped output (or bidir) pin should not be driven or pulled at all
  DO_NOT_DRIVE("do not drive (floating)", "floating");

  public final String desc, xml;

  public static final IdleBehavior[] OPTIONS = {
    DRIVE_INACTIVE /*DEFAULT*/, DRIVE_ACTIVE, DRIVE_LOW, DRIVE_HIGH, PULL_INACTIVE, PULL_ACTIVE, PULL_DOWN, PULL_UP, DO_NOT_DRIVE
  };

  public static final IdleBehavior DEFAULT = DRIVE_INACTIVE;

  IdleBehavior(String d, String x) { desc = d; xml = x; }

  public static IdleBehavior get(String desc) {
    if (desc == null || desc.isEmpty())
      return DEFAULT;
    for (IdleBehavior p : OPTIONS) {
      if (p.desc.equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "-").equalsIgnoreCase(desc))
        return p;
      if (p.xml.equalsIgnoreCase(desc)
          || p.xml.replaceAll("-", " ").equalsIgnoreCase(desc)
          || p.xml.replaceAll("-", "").equalsIgnoreCase(desc))
        return p;
    }
    if (desc.equalsIgnoreCase("inactive")) return DRIVE_INACTIVE;
    if (desc.equalsIgnoreCase("active")) return DRIVE_ACTIVE;
    if (desc.equalsIgnoreCase("low")) return DRIVE_LOW;
    if (desc.equalsIgnoreCase("high")) return DRIVE_HIGH;
    if (desc.equalsIgnoreCase("down")) return PULL_DOWN;
    if (desc.equalsIgnoreCase("up")) return PULL_UP;
    if (desc.equalsIgnoreCase("float")) return DO_NOT_DRIVE;
    if (desc.equalsIgnoreCase("none")) return DO_NOT_DRIVE;
    if (desc.equalsIgnoreCase("no-pull")) return DO_NOT_DRIVE;
    if (desc.equalsIgnoreCase("tristate")) return DO_NOT_DRIVE;
    if (desc.equalsIgnoreCase("tristated")) return DO_NOT_DRIVE;
    if (desc.equalsIgnoreCase("tri-state")) return DO_NOT_DRIVE;
    if (desc.equalsIgnoreCase("tri-stated")) return DO_NOT_DRIVE;
    return DEFAULT;
  }

  @Override
  public String toString() { return desc; }
}
