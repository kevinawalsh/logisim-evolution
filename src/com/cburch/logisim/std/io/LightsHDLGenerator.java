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
package com.cburch.logisim.std.io;

import com.bfh.logisim.netlist.Net;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.hdlgenerator.HDLInliner;
import com.bfh.logisim.hdlgenerator.HiddenPort;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.comp.EndData;

public class LightsHDLGenerator extends HDLInliner {

  public LightsHDLGenerator(ComponentContext ctx) {
    super(ctx);
  }

  public static LightsHDLGenerator forLed(ComponentContext ctx) {
    LightsHDLGenerator g = new LightsHDLGenerator(ctx);
    g.hiddenPort = HiddenPort.makeOutport(1, HiddenPort.LED, HiddenPort.OutPin);
    return g;
  }

  public static LightsHDLGenerator forRGBLed(ComponentContext ctx) {
    LightsHDLGenerator g = new LightsHDLGenerator(ctx);
    g.hiddenPort = HiddenPort.makeOutport(RGBLed.pinLabels(), HiddenPort.RGBLED, HiddenPort.LED, HiddenPort.OutRibbon, HiddenPort.OutPin);
    return g;
  }

  public static LightsHDLGenerator forSevenSegment(ComponentContext ctx) {
    int digits = ctx.attrs.getValue(SevenSegment.ATTR_DIGITS).intValue();
    LightsHDLGenerator g = new LightsHDLGenerator(ctx);
    if (digits == 1)
      g.hiddenPort = HiddenPort.makeOutport(SevenSegment.pinLabels(8),
          HiddenPort.SevenSegment, HiddenPort.OutRibbon, HiddenPort.LED, HiddenPort.OutPin);
    else
      g.hiddenPort = HiddenPort.makeOutport(SevenSegment.pinLabels(8+digits),
          HiddenPort.SevenSegmentGang, HiddenPort.OutRibbon, HiddenPort.LED, HiddenPort.OutPin);
    return g;
  }

  public static LightsHDLGenerator forLedBar(ComponentContext ctx) {
    LightsHDLGenerator g = new LightsHDLGenerator(ctx);
    int n = ctx.attrs.getValue(LedBar.ATTR_SEGMENTS).intValue();
    g.hiddenPort = HiddenPort.makeOutport(n, HiddenPort.LEDBar, HiddenPort.OutRibbon, HiddenPort.OutPin);
    return g;
  }

  @Override
	protected void generateInlinedCode(Hdl out, NetlistComponent comp) {
    int b = comp.getLocalHiddenPortIndices().start.out;
    int e = comp.getLocalHiddenPortIndices().end.out;
    if (comp.original.getEnds().size() != comp.portConnections.size()) {
      out.err.AddError("LightsHDLGenerator: Number of ports (%s) does not match number of connected netlists (%s).",
          comp.original.getEnds().size(),
          comp.portConnections.size());
    }
    int o = b;
    for (int i = 0; i < comp.original.getEnds().size(); i++) {
      EndData end = comp.original.getEnd(i);
      int n = end.getWidth().getWidth();
      Net net = comp.getConnection(i);
      if (net != null) {
        int nn = net.bitWidth();
        if (n != nn)
          out.err.AddError("LightsHDLGenerator: Port %d width (%d bits) does does match netlist width (%d bits).", i, n, nn);
        out.assign("LOGISIM_HIDDEN_FPGA_OUTPUT", o + n - 1, o, net.name);
      } else {
        out.err.AddWarning("LightsHDLGenerator: Port %d has no netlist connection.", i);
      }
      o += n;
    }
    if (o-1 != e)
      out.err.AddWarning("LightsHDLGenerator: Mismatch in total width, ended on %d instead of %d.", o-1, e);
  }

}
