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

import java.util.HashMap;

public class Chipset {

  // public static final String[] KNOWN_VENDORS = new String[] {
  //   "Altera" /* and/or Intel */,
  //   "Gowin",
  //   "Lattice",
  //   "Xilinx"  /* and/or AMD */
  // };

  public final String Speed; // e.g. "50 MHz"
	public final long ClockFrequency; // FIXME: non-integer frequencies are possible, e.g.  Intel MAX 10 FPGA with Si570 programmable oscillator
	public final String ClockPinLocation;
	public final PullBehavior ClockPullBehavior;
	public final IoStandard ClockIOStandard;
	public final String Technology;
	public final String Part;
	public final String Package;
	public final String SpeedGrade;
	public final String VendorName;
	public final PullBehavior UnusedPinsBehavior;
	
  public final boolean USBTMCAvailable;
  // FIXME: LPT as yet another option? See comment in LatticeDownload.

  public final int JTAGPos;
	
  public final String FlashName; // optional, required for FlashDefined
	public final Integer FlashPos; // optional, required non-zero for FlashDefined
	public final boolean FlashDefined;

	public Chipset(HashMap<String, String> params) throws Exception {

    ClockFrequency = Long.parseLong(params.get("ClockInformation/Frequency"));
		ClockPinLocation = params.get("ClockInformation/FPGApin");
		ClockPullBehavior = PullBehavior.get(params.get("ClockInformation/PullBehavior"));
		ClockIOStandard = IoStandard.get(params.get("ClockInformation/IOStandard"));

		Technology = params.get("FPGAInformation/Family");
		Part = params.get("FPGAInformation/Part");
		Package = params.get("FPGAInformation/Package");
		SpeedGrade = params.get("FPGAInformation/Speedgrade");
    VendorName = params.get("FPGAInformation/Vendor");
		USBTMCAvailable = Boolean.parseBoolean(params.getOrDefault("FPGAInformation/USBTMC", "false"));
    JTAGPos = Integer.parseInt(params.getOrDefault("FPGAInformation/JTAGPos", "1"));
    FlashPos = Integer.parseInt(params.getOrDefault("FPGAInformation/FlashPos", "2"));
    FlashName = params.get("FPGAInformation/FlashName");
		FlashDefined = FlashPos != null && FlashPos != 0 && FlashName != null && !FlashName.isEmpty();

		UnusedPinsBehavior = PullBehavior.get(params.get("UnusedPins/PullBehavior"));

    if (ClockFrequency <= 0)
      throw new Exception("invalid ClockInformation/Frequency");
    if (ClockPinLocation == null)
      throw new Exception("invalid or missing ClockInformation/FPGApin");
    if (ClockPullBehavior == null)
      throw new Exception("invalid or missing ClockInformation/PullBehavior");
    if (ClockIOStandard == null)
      throw new Exception("invalid or missing ClockInformation/IOStandard");
    if (UnusedPinsBehavior == null)
      throw new Exception("invalid or missing UnusedPins/PullBehavior");
    if (Technology == null)
      throw new Exception("invalid or missing FPGAInformation/Family");
    if (Part == null)
      throw new Exception("invalid or missing FPGAInformation/Part");
    if (Package == null)
      throw new Exception("invalid or missing FPGAInformation/Package");
    if (SpeedGrade == null)
      throw new Exception("invalid or missing FPGAInformation/Speedgrade");
    if (VendorName == null)
      throw new Exception("invalid or missing FPGAInformation/Vendor");

    Speed = freqToString(ClockFrequency);
  }

	private static String freqToString(long clkfreq) {
		if (clkfreq % 1000000 == 0) {
			clkfreq /= 1000000;
			return clkfreq + " MHz";
		} else if (clkfreq % 1000 == 0) {
			clkfreq /= 1000;
			return clkfreq + " kHz";
		}
		return clkfreq + " Hz";
	}

}
