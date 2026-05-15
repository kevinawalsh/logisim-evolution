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

public enum UnmentionedPinsBehavior {

  // unspecified/other/unknown, i.e. let toolchain decide
  UNSPECIFIED("unspecified", "unspecified"),

  // if possible, configure unused pins as inputs (i.e. setting the output
  // driver to "tri-state"), either with or without pull resistors
  INPUT_PULL_UP("input with pull-up", "pull-up"),
  INPUT_PULL_DOWN("input with pull-down", "pull-down"),
  INPUT_NO_PULL("input without pull resistors", "no-pull"),

  // if possible, configure unused pins as outputs, driving either high or low
  DRIVE_LOW("drive low", "drive-low"),
  DRIVE_HIGH("drive high", "drive-high");

  public final String desc, xml;

  public static final UnmentionedPinsBehavior[] OPTIONS = {
    UNSPECIFIED, INPUT_PULL_UP, INPUT_PULL_DOWN, INPUT_NO_PULL, DRIVE_LOW, DRIVE_HIGH
  };

  UnmentionedPinsBehavior(String d, String x) { desc = d; xml = x; }

  public static UnmentionedPinsBehavior get(String desc) {
    if (desc == null || desc.isEmpty())
      return UNSPECIFIED;
    for (UnmentionedPinsBehavior p : OPTIONS) {
      if (p.desc.equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "-").equalsIgnoreCase(desc))
        return p;
      if (p.xml.equalsIgnoreCase(desc)
          || p.xml.replaceAll("-", " ").equalsIgnoreCase(desc)
          || p.xml.replaceAll("-", "").equalsIgnoreCase(desc))
        return p;
    }
    if (desc.equalsIgnoreCase("up")) return INPUT_PULL_UP;
    if (desc.equalsIgnoreCase("down")) return INPUT_PULL_DOWN;
    if (desc.equalsIgnoreCase("float")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("none")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("pullnone")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("pull-none")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("tristate")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("tristated")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("tri-state")) return INPUT_NO_PULL;
    if (desc.equalsIgnoreCase("tri-stated")) return INPUT_NO_PULL;
    return UNSPECIFIED;
  }

  @Override
  public String toString() { return desc; }
}
