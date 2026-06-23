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

import java.util.Map;

public class Chipset {

  // public static final String[] KNOWN_VENDORS = new String[] {
  //   "Altera" /* and/or Intel */,
  //   "Gowin",
  //   "Lattice",
  //   "Xilinx"  /* and/or AMD */
  // };

  // FIXME: many of these could be optional, never used for some backends
  
  public final String ClockSpeed; // e.g. "50 MHz" or "50000.0 kHz" (from xml, may not be normalized, may be missing units)

  // FIXME: Clock should be a BoardIO... some fpga boards have multiple clocks,
  // user should be able to choose between them
  public final double ClockFrequency; // note: hz, but non-integer frequencies are possible, e.g. Intel MAX 10 FPGA with Si570 programmable oscillator
	public final String ClockPinLocation;
	// public final PullBehavior ClockPullBehavior;
	public final IoStandard ClockIOStandard;
  public final String PLLType; // optional

	public final String Vendor;
	public final String Technology; // aka Family
	public final String Part;
	public final String Package; // optional
	public final String SpeedGrade; // optional

  // For all FPGA pins mentioned in an xml file (as physical I/O components, as
  // the clock pin, or as otherwise reserved pins), logisim (as of version 5.20
  // or so) will explicitly configure them, whether or not the user has chosen
  // to map circuit elements to that FPGA pin. 
  //
  // Power and ground FPGA pins are not mentioned in xml files, as these are
  // handled directly by toolchains.
  //
  // The remaining unmentioned pins include some combination of:
  //  - FPGA pins not routed to anything on the board,
  //  - FPGA pins connected only to test pads,
  //  - and FPGA pins connnected to various perhipheral devices or components
  //    the xml author elected to ignore.
  // The UnmentioendPinsBehaviorHint parameter specifies how these should be handled.
  //
  // Because these pins are unmentioned, logisim doesn't know their names, so
  // can't configure them individually.
  //
  // Some toolchains have a global flag for specifying the behavior of any pins
  // not otherwise specified in a design. Logisim will pass options to the
  // toolchains in an attempt to follow the UnmentioendPinsBehaviorHint, unless
  // overridden by a toolchain-specific parameters in the board xml. This is
  // done on a best effort basis only, because some toolchains and/or fpgas
  // support only a limited set of options here. If no UnmentioendPinsBehaviorHint is
  // given, or if the hint can't be followed, logisim might fall back to passing
  // some other default flag, or to omitting the flag and letting the toolchain
  // decide how unused pins behave.
  //
  // Other toolchains have no such global flag. These toolchains have some
  // internal way of deciding how unused pins behave, which logisim makes no
  // atttempt to override. The UnmentioendPinsBehaviorHint is ignored in this case.
	public final UnmentionedPinsBehavior UnmentionedPinsBehaviorHint;
	
  // FIXME: LPT as yet another option? See Lattice toolchain.

  public final int JTAGPos;
	
  public final String FlashName; // optional, required for FlashDefined
	public final Integer FlashPos; // optional, required non-zero for FlashDefined
	public final boolean FlashDefined;

	public Chipset(Map<String, String> params) throws Exception {

    ClockSpeed = params.get("Clock/frequency");
    try {
      ClockFrequency = freqFromString(ClockSpeed);
    } catch (NumberFormatException ex) {
      throw new Exception("invalid Clock:frequency: " + ex.getMessage());
    }

		ClockPinLocation = params.get("Clock/pin");
		ClockIOStandard = IoStandard.get(params.get("Clock/ioStandard")); // optional; returns non-null
		PLLType = params.get("PLL/type"); // optional

    Vendor = params.get("Chip/vendor");
		Technology = params.get("Chip/family");
		Part = params.get("Chip/part");
		SpeedGrade = params.getOrDefault("Chip/speedGrade", ""); // optional
		Package = params.getOrDefault("Chip/package", ""); // optional

    JTAGPos = Integer.parseInt(params.getOrDefault("JTAG/pos", "1")); // optional
    FlashPos = Integer.parseInt(params.getOrDefault("Flash/pos", "2")); // optional
    FlashName = params.get("Flash/name"); // optional
		FlashDefined = FlashPos != null && FlashPos != 0 && FlashName != null && !FlashName.isEmpty();

		UnmentionedPinsBehaviorHint = UnmentionedPinsBehavior.get(params.get("UnmentionedPins/behavior")); // optional; returns non-null

    if (ClockPinLocation == null)
      throw new Exception("invalid or missing Clock:pin");
    if (Vendor == null)
      throw new Exception("invalid or missing Chip:vendor");
    if (Technology == null)
      throw new Exception("invalid or missing Chip:family");
    if (Part == null)
      throw new Exception("invalid or missing Chip:part");
  }

  // Convert a string like "30.5 MHz" to a frequency in Hz as a double. For all
  // integer frequencies up to several GHz, this should be exact. For some
  // fractional frequencies, like 0.1 Hz, the result may be inexact because of
  // floating point representation.
  public static double freqFromString(String rate) throws NumberFormatException {
    rate = rate.toLowerCase().trim();
    double multiplier = 1.0;
    if (rate.endsWith("khz")) {
      multiplier = 1000.0;
      rate = rate.substring(0, rate.length() - 3);
    } else if (rate.endsWith("mhz")) {
      multiplier = 1000000.0;
      rate = rate.substring(0, rate.length() - 3);
    } else if (rate.endsWith("ghz")) {
      multiplier = 10000000000.0;
      rate = rate.substring(0, rate.length() - 3);
    } else if (rate.endsWith("hz")) {
      multiplier = 1.0;
      rate = rate.substring(0, rate.length() - 2);
    }
    double freq = Double.parseDouble(rate) * multiplier; // may throw
    if (freq <= 0)
      throw new NumberFormatException("clock frequencies must be positive");
    if (!Double.isFinite(freq))
      throw new NumberFormatException("clock frequencies must be finite");
    return freq;
  }

}
