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

import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.netlist.NetlistComponent;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.std.wiring.Pin;

// InputBias are relevant only for FPGA pins used as inputs.
//
// FPGA board xml files can specify the resistor configuration (from among any
// of the choices below) for input-type I/O components such as Button,
// DIPSwitch, Pin (relevant only when used as input), and Ribbon (relevant only
// when used as input). 
//
//   XML bias value      Java enum        Meaning
//   --------------      ---------        -------
//   pull-up             PULL_UP          Pull up resistor required.
//   pull-down           PULL_DOWN        Pull down resistor required.
//   none                PULL_NONE        Explicitly requesting no resistors or bus-hold.
//   bus-hold            BUS_HOLD         bus-hold/keeper required.
//   pull-required       PULL_REQUIRED    Pull up or down required, direct is flexible,
//                                        defers to logisim design if possible,
//                                        otherwise pull-up as fallback.
//   any                 ANY              Defer entirely to logisim design to
//                                        select among pull-up, pull-down, or none.
//   toolchain-default   DO_NOT_SPECIFY   Emit no constraint, allowing backend
//                                        toolchain to decide.
//   (absent)                             Same as pull-up.
//
//
// The std/wiring/Pin components in the top-level logisim
// circuit can, when configured as an input, also specify a "pull" direction
// (but only PULL_UP, PULL_DOWN, and PULL_NONE are possible).
//
// Both are used to determine the actual resistors configured, resolved as
// follows:
//
//   board xml       logisim Pin         FPGA resistor configuration 
//   ---------       -----------         ---------------------------
//
//   PULL_REQUIRED   PULL_UP             PULL_UP
//                   PULL_DOWN           PULL_DOWN
//                   PULL_NONE           PULL_UP (default, with info message)
//
//   ANY             PULL_UP             PULL_UP
//                   PULL_DOWN           PULL_DOWN
//                   PULL_NONE           PULL_NONE (with a warning that this could be unsafe)
//
//   PULL_UP         PULL_UP             PULL_UP
//                   PULL_DOWN           PULL_UP (with a warning about conflict)
//                   PULL_NONE           PULL_UP
//
//   PULL_DOWN       PULL_UP             PULL_DOWN (with a warning about conflict)
//                   PULL_DOWN           PULL_DOWN
//                   PULL_NONE           PULL_DOWN
//
//   PULL_NONE       PULL_UP             PULL_NONE (with a warning about conflict)
//                   PULL_DOWN           PULL_NONE (with a warning about conflict)
//                   PULL_NONE           PULL_NONE
//
//   BUS_HOLD        PULL_UP             BUS_HOLD (with a warning about conflict)
//                   PULL_DOWN           BUS_HOLD (with a warning about conflict)
//                   PULL_NONE           BUS_HOLD
//
//   DO_NOT_SPECIFY  PULL_UP             DO_NOT_SPECIFY (with a warning about conflict)
//                   PULL_DOWN           DO_NOT_SPECIFY (with a warning about conflict)
//                   PULL_NONE           DO_NOT_SPECIFY
//
//  Of all of these, the only combinations that result in no resistors at all
//  are: DO_NOT_SPECIFY, or ANY+PULL_NONE. All other combinations result in the
//  FPGA pin being configured with bus-hold or pull resistors of some kind.
//
//  The general principles here are:
//   - For logisim pins, PULL_NONE is the default and expected configuration,
//     and is taken to mean "do whatever board xml thinks is best."
//   - For board xml, PULL_UP, PULL_DOWN, PULL_NONE, BUS_HOLD, and
//     DO_NOT_SPECIFY, all are taken to mean "the board hardware is such that
//     this is the only resistor configuration that makes sense electrically, so
//     it must be used." Any contrary resistor specification from the logisim
//     pin is rejected, with a warning.
//   - For board xml, PULL_REQUIRED is taken to mean "this pin (if used as an
//     input) is sometimes undriven by the board hardware, so it really should
//     have pull resistor of some kind, and both PULL_UP or PULL_DOWN are
//     sensible electrically", so here, logisim's pin decides or, if logisim's
//     pin doesn't specify a direction, we fall back to PULL_UP as the plausibly
//     most safe default. The anticipated use case is for FPGA boards where an
//     FPGA pin is routed to an expansion header without any external pull
//     resistor. To avoid the undesirable case of floating inputs (e.g. if
//     nothing is connected to the expansion header), a pull resistor in the
//     fpga is desirable. And depending on what gets plugged in, a logisim pin
//     can specify either PULL_UP or PULL_DOWN as appropriate for that expansion
//     hardware.
//   - For board xml, ANY is taken to mean "defer to logisim", even if that
//     results in there being no pull resistors at all. This could make sense
//     for expansion headers where the hardware to be connected may require
//     either the FPGA to have a pull up or pull down resistor or, for some
//     reason I can't image what, somehow can't tolerate any FPGA pull resistor.
//
// Logisim toolchains backends and HDL generator emit the corresponding
// constraints for each known input-type FPGA pin, regardless of whether the I/O
// component is actually used within the particular design.

public enum InputBias {

  PULL_UP("pull-up resistor", "pull-up"), // the default
  PULL_DOWN("pull-down resistor", "pull-down"),
  PULL_NONE("no pull resistor (aka 'floating')", "none"),
  BUS_HOLD("bus-hold (aka 'keeper')", "bus-hold"),
  PULL_REQUIRED("pull required, direction flexible", "pull-required"),
  ANY("any / defer to logisim circuit", "any"),
  DO_NOT_SPECIFY("use toolchain default", "toolchain-default");

  public final String desc, xml;

  public static final InputBias[] OPTIONS = {
    PULL_UP /*DEFAULT*/, PULL_DOWN, PULL_NONE, BUS_HOLD, PULL_REQUIRED, ANY, DO_NOT_SPECIFY
  };

  public static final InputBias DEFAULT = PULL_UP;

  InputBias(String d, String x) { desc = d; xml = x; }

  public static InputBias get(String desc) {
    if (desc == null || desc.isEmpty())
      return DEFAULT;
    for (InputBias p : OPTIONS) {
      if (p.desc.equalsIgnoreCase(desc)
          || p.desc.replaceAll(" ", "-").equalsIgnoreCase(desc))
        return p;
      if (p.xml.equalsIgnoreCase(desc)
          || p.xml.replaceAll("-", " ").equalsIgnoreCase(desc)
          || p.xml.replaceAll("-", "").equalsIgnoreCase(desc))
        return p;
    }
    if (desc.equalsIgnoreCase("up")) return PULL_UP;
    if (desc.equalsIgnoreCase("down")) return PULL_DOWN;
    if (desc.equalsIgnoreCase("float")) return PULL_NONE;
    if (desc.equalsIgnoreCase("floating")) return PULL_NONE;
    if (desc.equalsIgnoreCase("without-pull")) return PULL_NONE;
    if (desc.equalsIgnoreCase("pull-none")) return PULL_NONE;
    if (desc.equalsIgnoreCase("pullnone")) return PULL_NONE;
    if (desc.equalsIgnoreCase("no-pull")) return PULL_NONE;
    if (desc.equalsIgnoreCase("nopull")) return PULL_NONE;
    if (desc.equalsIgnoreCase("hold")) return BUS_HOLD;
    if (desc.equalsIgnoreCase("keeper")) return BUS_HOLD;
    return DEFAULT;
  }

  @Override
  public String toString() { return desc; }


  // Always returns one of the concrete pull directions (up, down, none,
  // bus-hold, do-not-specify), eliminating the ambiguous cases (any,
  // pull-required).
  public static InputBias resolveConflict(FPGAReport err,
      InputBias boardSpec, NetlistComponent pinShadow) {

    String name = pinShadow.pathName();

    AttributeOption logisimPinBehavior;
    if (pinShadow.original.getFactory() instanceof Pin)
      logisimPinBehavior = pinShadow.original.getAttributeSet().getValue(Pin.ATTR_BEHAVIOR);
    else
      logisimPinBehavior = Pin.SIMPLE;

    // pinSpec can only be PULL_UP, PULL_DOWN, or PULL_NONE
    InputBias pinSpec;
    if (logisimPinBehavior == Pin.PULL_UP) {
      pinSpec = PULL_UP;
    } else if (logisimPinBehavior == Pin.PULL_DOWN) {
      pinSpec = PULL_DOWN;
    } else if (logisimPinBehavior == Pin.SIMPLE) {
      pinSpec = PULL_NONE;
    } else {
      err.AddFatalError("%s is configured as %s, which can't be synthesized.", name, logisimPinBehavior);
      pinSpec = PULL_NONE;
    }

    if (boardSpec == PULL_REQUIRED) {
      if (pinSpec == PULL_NONE) {
        err.AddInfo("%s specifies no pull, but this board I/O pin requires a bias resistor; defaulting to pull-up.", name);
        return PULL_UP;
      } else {
        return pinSpec;
      }
    }

    if (boardSpec == ANY) {
      if (pinSpec == PULL_NONE)
        err.AddWarning("%s specifies no pull, which is permitted by this board I/O pin but may be electrically unsafe.", name);
      return pinSpec;
    }

    boolean warn = 
      (boardSpec == PULL_UP && pinSpec == PULL_DOWN) ||
      (boardSpec == PULL_DOWN && pinSpec == PULL_UP) ||
      (boardSpec == PULL_NONE && pinSpec != PULL_NONE) ||
      (boardSpec == BUS_HOLD && pinSpec != PULL_NONE) ||
      (boardSpec == DO_NOT_SPECIFY && pinSpec != PULL_NONE);

    if (warn)
      err.AddSevereWarning("%s specifies bias '%s', but board I/O pin requires bias '%s', so '%s' will be used.",
          name, pinSpec.xml, boardSpec.xml, boardSpec.xml);

    return boardSpec;
  }

}
