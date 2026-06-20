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

package com.cburch.logisim.std.bfh;

import com.bfh.logisim.hdlgenerator.HDLInliner;
import com.bfh.logisim.netlist.Net;
import com.bfh.logisim.netlist.NetlistComponent;
import com.cburch.logisim.hdl.Hdl;

public class DynamicClockHDLGenerator extends HDLInliner {

  public DynamicClockHDLGenerator(ComponentContext ctx) {
    super(ctx);
  }

  @Override
	protected void generateInlinedCode(Hdl out, NetlistComponent comp) {
    Net net = comp.getConnection(0);
    if (net == null) {
      out.err.AddWarning("Dynamic Clock Control component input is not connected.");
      out.err.AddWarning("Clock speed will be set to the maximum possible.");
      int w = _attrs.getValue(DynamicClock.WIDTH_ATTR).getWidth();
      out.assign("LOGISIM_DYNAMIC_CLOCK_OUT", out.ones(w));
    } else {
      out.assign("LOGISIM_DYNAMIC_CLOCK_OUT", net.name);
    }
  }

}
