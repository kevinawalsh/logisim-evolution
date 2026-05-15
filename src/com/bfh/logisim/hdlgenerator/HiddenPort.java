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

package com.bfh.logisim.hdlgenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.bfh.logisim.fpga.BoardIO;
import static com.bfh.logisim.netlist.Netlist.Int3;

// HiddenPort represents a FPGA I/O-related HDL port for a (shadow) logisim
// component, used to connect the (shadow) component to FPGA I/O pins. This port
// is separate from those corresponding to the user-visible component pins
// within the logisim circuit layout. For example, the shadow NetlistComponent
// for a logisim Keyboard component has HDL ports for data, status, etc., each
// matching one of the user-visible logisim Keyboard ports, but it also has one
// HiddenPort used to connect to the FPGA I/O pins leading to a phyiscal
// keyboard connector.
//
// Note: So far, each NetlistComponent has at most one HiddenPort, and all the
// bits in each HiddenPort are either all input, all output, or all inout. There
// are no mixed-direction hidden ports.
public class HiddenPort {

  // Note: Type constants are here merely for convenience in generator classes.

  // Input types
  public static final BoardIO.Type Button = BoardIO.Type.Button; // single-bit
  public static final BoardIO.Type DIPSwitch = BoardIO.Type.DIPSwitch; // multi-bit

  // Input, Output, or Bidirectional
  public static final BoardIO.Type Pin = BoardIO.Type.Pin; // single-bit
  public static final BoardIO.Type Ribbon = BoardIO.Type.Ribbon; // multi-bit

  // Output types
  public static final BoardIO.Type LED = BoardIO.Type.LED; // single-bit
  public static final BoardIO.Type RGBLED = BoardIO.Type.RGBLED; // multi-bit
  public static final BoardIO.Type SevenSegment = BoardIO.Type.SevenSegment; // multi-bit
  public static final BoardIO.Type LEDBar = BoardIO.Type.LEDBar; // multi-bit
  
  public final boolean in, out, inout;
  public final List<String> labels;
  public final List<String> inports, inoutports, outports; // labels by category, for convenience
  private final Int3 width;
  private final boolean customLabels;

  public final BoardIO.Type mainType;
  public final List<BoardIO.Type> altTypes;
  public final List<BoardIO.Type> types; // main + alternates

  // Constructors with custom bit labels
  public static HiddenPort makeInport(String[] labels, BoardIO.Type mainType, BoardIO.Type ...altTypes)    { return new HiddenPort(IN, true, labels, mainType, altTypes); }
  public static HiddenPort makeInOutport(String[] labels, BoardIO.Type mainType, BoardIO.Type ...altTypes) { return new HiddenPort(BI, true, labels, mainType, altTypes); }
  public static HiddenPort makeOutport(String[] labels, BoardIO.Type mainType, BoardIO.Type ...altTypes)   { return new HiddenPort(OUT, true, labels, mainType, altTypes); }

  // Constructors with generic bit labels
  public static HiddenPort makeInport(int width, BoardIO.Type mainType, BoardIO.Type ...altTypes)    { return new HiddenPort(IN, false, generateLabels(IN, width), mainType, altTypes); }
  public static HiddenPort makeInOutport(int width, BoardIO.Type mainType, BoardIO.Type ...altTypes) { return new HiddenPort(BI, false, generateLabels(BI, width), mainType, altTypes); }
  public static HiddenPort makeOutport(int width, BoardIO.Type mainType, BoardIO.Type ...altTypes)   { return new HiddenPort(OUT, false, generateLabels(OUT, width), mainType, altTypes); }

  // // Constructor for Pin components
  // public static HiddenPort forPin(Component pin) {
  //   int w = pin.getEnd(0).getWidth().getWidth();
  //   boolean i = pin.getEnd(0).isOutput(); // output to circuit means input from fpga
  //   boolean o = pin.getEnd(0).isInput(); // input from circuit means output to fpga
  //   int dir = (i?IN:0) + (o?OUT:0);
  //   if (w == 1)
  //     return new HiddenPort(dir, generateLabels(dir, 1), Pin);
  //   else
  //     return new HiddenPort(dir, generateLabels(dir, w), Ribbon, Pin);
  // }

  private static final int IN = 1;
  private static final int OUT = 2;
  private static final int BI = IN+OUT;

  private static List<String> EMPTY = Collections.unmodifiableList(new ArrayList<String>());

	private HiddenPort(int dir, boolean custom, String[] ports, BoardIO.Type main, BoardIO.Type ...alts) {
    labels = Collections.unmodifiableList(Arrays.asList(ports));
    customLabels = custom;
    in = (dir == IN);
    inout = (dir == BI);
    out = (dir == OUT);
    inports = in ? labels : EMPTY;
    inoutports = inout ? labels : EMPTY;
    outports = out ? labels : EMPTY;
		mainType = main;
    altTypes = Collections.unmodifiableList(Arrays.asList(alts));
    ArrayList<BoardIO.Type> all = new ArrayList<>();
    all.add(mainType);
    all.addAll(altTypes);
    types = Collections.unmodifiableList(all);
    int w = ports.length;
    width = new Int3();
    width.in = in ? w : 0;
    width.inout = inout ? w : 0;
    width.out = out ? w : 0;
	}

  private static String[] generateLabels(int dir, int w) {
    String prefix = (dir == IN ? "In_" : dir == OUT ? "Out_" : "Bidir_");
    String[] labels = new String[w];
    for (int i = 1; i <= w; i++)
      labels[i-1] = prefix + i;
    return labels;
  }

	public int numPorts() {
    return width.size();
  }

  public Int3 width() {
    return width.copy();
  }

  @Override
  public String toString() {
    return String.format("HiddenPort{dir=%s, width=%s maintype=%s}",
        in?"in":inout?"bidir":"out", width, mainType);
  }

}
