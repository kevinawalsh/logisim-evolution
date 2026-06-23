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

package com.bfh.logisim.netlist;

import java.util.ArrayList;
import java.util.HashMap;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.std.wiring.Clock;

// A list of all Clock timing/shape parameters used in a design, based on a
// complete enumeration of all Clock components in all nested subcircuits. Each
// shape is assigned a globally unique ID. For each ID, we also store a complete
// trace of Path:Net:bit points associated with that ID.
//
// In logisim, users commonly seem to place Clocks willy-nilly, relying on the
// logisim simulator to tick them all simultaneously (or if they have different
// timing/shape parameters, to globally align all the tick in an ideal but
// simplistic way). In an FPGA, we can't should not expect multiple clock
// signals to be perfectly aligned, even if they were generated using the same
// timing/shape parameters. An FPGA might also have limited clock net resources.
// So we want to reduce the number of clock distribution nets in the FPGA
// design. This is done by separating the "tick generator" functionality of a
// Clock from the Clock component, lifting all these tick generators up out of
// their circuits into the TopLevelHDLGenerator. The tick generators are
// de-duplicated according to their timing/shape parameters, assigned globally
// unique IDs, and all of their outputs formed into a single combined "hidden
// clock bus" that is then re-distributed to all circuits and subcircuits. The
// portion of the Clock components left behind in the circuits then just serve
// to select the correct bit out of the hidden clock bus to feed into their
// output port. But note than typicaly, components with a "clock port" that are
// fed by that clock signal won't typically use the raw signal from that Clock
// output port. Instead, they would take the corresponding rising/falling
// enables and the base clock signal directly from the hidden clock bus. The
// only time the raw Clock output port signal gets used is when it is fed into
// combinational logic or some port that isn't expecting a clock signal.
public class ClockBus {

  public static class Shape {
    public final int hi, lo, ph;
    public final String desc;
    Shape(Component clk) {
      hi = clk.getAttributeSet().getValue(Clock.ATTR_HIGH);
      lo = clk.getAttributeSet().getValue(Clock.ATTR_LOW);
      ph = clk.getAttributeSet().getValue(Clock.ATTR_PHASE);
      desc = hi+":"+lo+"+"+ph;
    }
    @Override
    public String toString() { return desc; }
    @Override
    public boolean equals(Object o) { return (o instanceof Shape) && desc.equals(((Shape)o).desc); }
    @Override
    public int hashCode() { return desc.hashCode(); }
  }

  static class Point {
    final Path path;
    final Net net;
    final int bit;
    Point(Path p, Net n, int b) { path = p; net = n; bit = b; }
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Point))
        return false;
      Point other = (Point)o;
      return path.equals(other.path) && net == other.net && bit == other.bit;
    }
    @Override
    public int hashCode() { return path.hashCode() + 31*net.hashCode() + 31*31*bit; }
  }

	public final boolean RawFPGAClock;
  public final double RawFPGAClockFreq_;
  public final int TickerPeriod; // 0:"use fpga clock", -1:"dynamic", >0:"divided clock"
	private ArrayList<Shape> shapes = new ArrayList<>();
  private HashMap<Point, Integer> points = new HashMap<>();

	public ClockBus(double fpgaFreq, int clkPeriod) {
    RawFPGAClockFreq_ = fpgaFreq;
    TickerPeriod = clkPeriod;
    RawFPGAClock = (clkPeriod == 0);
  }

	public void clear() {
		shapes.clear();
    points.clear();
	}

	public int id(Component clk) {
		if (!(clk.getFactory() instanceof Clock))
			return -1;
    Shape s = new Shape(clk);
    int idx = shapes.indexOf(s);
    if (idx >= 0)
      return idx;
    idx = shapes.size();
    shapes.add(s);
    return idx;
  }

  public int id(Path path, Net net, int bit) {
    Point p = new Point(path, net, bit);
    return points.getOrDefault(p, -1);
  }

  public boolean associate(int id, Path path, Net net, int bit) {
    Point p = new Point(path, net, bit);
    // Sanity check
    Integer old = points.put(p, id);
    if (old != null && old != id)
      System.err.printf("INTERNAL ERROR: Conflicting clocks for a net in '%s': %d vs %d.",
          path, old, id);
    return old == null;
  }

	public ArrayList<Shape> shapes() {
		return shapes;
	}

}
