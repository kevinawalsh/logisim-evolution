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

import com.bfh.logisim.fpga.BoardIO;
import com.bfh.logisim.fpga.PinActivity;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.fpga.PullBehavior;
import com.bfh.logisim.library.DynamicClock;
import com.bfh.logisim.netlist.ClockBus;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.std.wiring.ClockHDLGenerator;
import com.cburch.logisim.std.wiring.Pin;

public class ToplevelHDLGenerator extends HDLGenerator {

  // Name of the top-level HDL module.
  public static final String HDL_NAME = "LogisimToplevelShell";

	private Circuit circUnderTest;
	private PinBindings ioResources;
  private boolean useTristates;
  private Netlist _circNets; // Netlist of the circUnderTest.

  private TickHDLGenerator ticker;
  private ArrayList<ClockHDLGenerator.CounterPart> clkgens = new ArrayList<>();
  private CircuitHDLGenerator circgen;

  // There is no parent netlist for TopLevel, because it is not embedded inside
  // anything. There are no attributes either.
  // There is no parent netlist for circgen or ticker, because TopLevel doesn't
  // create a netlist for itself. Neither of those components uses attributes,
  // so we can leave them empty. So both can use a single context with null nets
  // and empty attributes.
	
  public ToplevelHDLGenerator(Netlist.Context ctx, PinBindings ioResources) {
    this(ctx, ioResources, true);
  }

  public ToplevelHDLGenerator(Netlist.Context ctx, PinBindings ioResources, boolean useTristates) {
    super(new ComponentContext(ctx, null, null), "toplevel", HDL_NAME, "i_Toplevel");

		this.circUnderTest = ctx.circUnderTest;
		this.ioResources = ioResources;
    this.useTristates = useTristates;

    _circNets = ctx.getNetlist(circUnderTest);
    int numclk = ctx.clockbus.shapes().size();

    // raw oscillator input
    ioResources.requiresOscillator = numclk > 0;
    if (numclk > 0)
      inPorts.add(TickHDLGenerator.FPGA_CLK_NET, 1, -1, null);

    // io resources
    Netlist.Int3 ioPinCount = ioResources.countFPGAPhysicalIOPins();
		for (int i = 0; i < ioPinCount.in; i++)
      inPorts.add("FPGA_INPUT_PIN_"+i, 1, -1, null);
		for (int i = 0; i < ioPinCount.inout; i++)
      inOutPorts.add("FPGA_BIDIR_PIN_"+i, 1, -1, null);
		for (int i = 0; i < ioPinCount.out; i++)
      outPorts.add("FPGA_OUTPUT_PIN_"+i, 1, -1, null);

    // internal networks for unconnected bidirectional ports
    Netlist.Int3 openCount = ioResources.countFPGAUnconnectedIOMappings();
    for (int i = 0; i < openCount.inout; i++)
      wires.add("FPGA_OPEN_"+i, 1);

    // internal clock networks
		if (numclk > 0) {
      wires.add(TickHDLGenerator.FPGA_CLKp_NET, 1);
      wires.add(TickHDLGenerator.FPGA_CLKn_NET, 1);
      wires.add(TickHDLGenerator.FPGA_TICK_NET, 1);
			for (int i = 0; i < numclk; i++)
				wires.add(ClockHDLGenerator.CLK_TREE_NET+i,
              ClockHDLGenerator.CLK_TREE_WIDTH);
		}

    // wires for hidden ports for circuit design under test
    Netlist.Int3 hidden = _circNets.numHiddenBits();
    wires.addVector("s_LOGISIM_HIDDEN_FPGA_INPUT", hidden.in);
    wires.addVector("s_LOGISIM_HIDDEN_FPGA_OUTPUT", hidden.out);
    wires.addVector("in_s_LOGISIM_HIDDEN_FPGA_BIDIR", hidden.inout);
    wires.addVector("out_s_LOGISIM_HIDDEN_FPGA_BIDIR", hidden.inout);
    wires.addVector("en_s_LOGISIM_HIDDEN_FPGA_BIDIR", hidden.inout);

    // wires for normal ports for circuit design under test
    for (NetlistComponent shadow : _circNets.inpins) {
      int w = shadow.original.getEnd(0).getWidth().getWidth();
      wires.add("s_"+shadow.pathName(), w);
    }
    for (NetlistComponent shadow : _circNets.outpins) {
      int w = shadow.original.getEnd(0).getWidth().getWidth();
      wires.add("s_"+shadow.pathName(), w);
    }

    // wires for dynamic clock
    NetlistComponent dynClock = _circNets.dynamicClock();
    if (dynClock != null) {
      int w = dynClock.original.getAttributeSet().getValue(DynamicClock.WIDTH_ATTR).getWidth();
      wires.add("s_LOGISIM_DYNAMIC_CLOCK", w);
    }

    ComponentContext subctx = new ComponentContext(ctx, null, null);
		if (numclk > 0) {
			ticker = new TickHDLGenerator(subctx, dynClock);
			long id = 0;
      for (ClockBus.Shape shape : ctx.clockbus.shapes())
        clkgens.add(new ClockHDLGenerator.CounterPart(subctx, shape, id++));
    }

		circgen = new CircuitHDLGenerator(subctx, circUnderTest);
	}

  // Top-level entry point: write all HDL files for the project.
  public boolean writeAllHDLFiles(String rootDir) {
    if (!circgen.writeAllHDLFiles(rootDir)) {
      _err.AddInfo("Circuit HDL files could not be generated.");
      return false;
    }
    if (ticker != null && !ticker.writeHDLFiles(rootDir)) {
      _err.AddInfo("Clock ticker HDL files could not be generated.");
      return false;
    }
    if (!clkgens.isEmpty() && !clkgens.get(0).writeHDLFiles(rootDir)) {
      _err.AddInfo("Clock HDL files could not be generated.");
      return false;
    }
    if (!writeHDLFiles(rootDir)) {
      _err.AddInfo("Top level HDL module could not be generated.");
      return false;
    }
    return true;
  }
  
  @Override
	protected void generateVhdlBlackBox(Hdl out, boolean isEntity) {
    generateVhdlBlackBox(out, isEntity, true);
  }

  @Override
  public boolean hdlDependsOnCircuitState() { // for NVRAM
    return circgen.hdlDependsOnCircuitState();
  }

  @Override
  public boolean writeAllHDLThatDependsOn(CircuitState cs,
      NetlistComponent ignored1, Path ignored2, String rootDir) { // for NVRAM
    return circgen.writeAllHDLThatDependsOn(cs, null,
        new Path(circUnderTest), rootDir);
  }

	@Override
	protected void declareNeededComponents(Hdl out) {
		if (ticker != null) {
      ticker.generateComponentDeclaration(out);
      // Declare clock gen module. All are identical, so only one declaration needed.
      clkgens.get(0).generateComponentDeclaration(out);
		}
    circgen.generateComponentDeclaration(out);
	}

	@Override
  protected void generateBehavior(Hdl out) {

    out.comment("signal adaptions for I/O related components and top-level pins");
    ioResources.components.forEach((path, shadow) -> {
      generateInlinedCodeSignal(out, path, shadow);
		});
    out.stmt();

    out.comment("signal adaptions for bidirectional top-level pins");
    ioResources.components.forEach((path, shadow) -> {
      generateInlinedCodeBidirSignal(out, path, shadow);
		});
    out.stmt();

		if (ticker != null) {
      out.comment("clock signal distribution");
      ticker.generateComponentInstance(out, 0L /*id*/, null /*comp*/ /*, null path*/);

			long id = 0;
			for (ClockHDLGenerator.CounterPart clkgen : clkgens)
        clkgen.generateComponentInstance(out, clkgen.id, null /*comp*/ /*, null path*/);
      out.stmt();
		}

    out.comment("connections for circuit design under test");
    circgen.generateTopComponentInstance(out, this);
	}

  private void pinVectorAssign(Hdl out, String pinName, String portName, int seqno, int n) {
    if (n == 1)
      out.assign(pinName, portName, seqno);
    else if (n > 1)
      out.assign(pinName, portName, seqno+n-1, seqno);
  }

  private boolean needTopLevelInversion(Component comp, BoardIO io) {
    boolean boardIsActiveHigh = io.activity != PinActivity.ACTIVE_LOW;
    boolean compIsActiveHigh = comp.getFactory().ActiveOnHigh(comp.getAttributeSet());
    return boardIsActiveHigh ^ compIsActiveHigh;
  }

  private static PullBehavior pullDirection(PullBehavior behavior) {
    if (behavior == PullBehavior.PULL_UP || behavior == PullBehavior.PULL_DOWN)
      return behavior;
    else
      return PullBehavior.NONE;
  }
  private PullBehavior pullDirection(NetlistComponent shadow) {
    AttributeOption behavior = shadow.original.getAttributeSet().getValue(Pin.ATTR_BEHAVIOR);
    if (behavior == Pin.PULL_UP) return PullBehavior.PULL_UP;
    if (behavior == Pin.PULL_DOWN) return PullBehavior.PULL_DOWN;
    if (behavior != Pin.SIMPLE)
        _err.AddFatalError(shadow.pathName() + " is configured as " + behavior + ", which can't be synthesized.");
      return PullBehavior.NONE;
  }

  // precondition: dest is a physical I/O device, src is a Pin requesting given pull
  private void recordInputPullDirection(Hdl out, PullBehavior inputPinPullDir,
      NetlistComponent shadow, PinBindings.Source src,  PinBindings.Dest dest) {
    if (inputPinPullDir == null || inputPinPullDir == PullBehavior.NONE)
      return;
    PullBehavior fpgaPullDir = pullDirection(dest.io.pull);
    if (fpgaPullDir == inputPinPullDir) {
      out.err.AddInfo(shadow.pathName() + " " + inputPinPullDir + " satisfied by default configuration of " + dest.io);
      return;
    }
    if (fpgaPullDir != PullBehavior.NONE) {
      out.err.AddSevereWarning(shadow.pathName() + " " + inputPinPullDir + " conflicts with "
          + dest.io + " " + fpgaPullDir +", latter will take precedence.");
      return;
    }
    out.err.AddInfo(shadow.pathName() + " requires " + inputPinPullDir + " on " + dest.io);
    Netlist.Int3 seqno = dest.seqno();
    for (int i = 0; i < src.width.in; i++) { // TODO: verify src.width.in instead of destwidth.in
      ioResources.addPull("FPGA_INPUT_PIN_"+(seqno.in+i), inputPinPullDir);
    }
  }

  private void generateInlinedCodeSignal(Hdl out, Path path, NetlistComponent shadow) {
    // Note: Any logisim component that is not active-high will get an inversion
    // here. Also, any FPGA I/O device that is not active-high will get an
    // inversion. In cases where there would be two inversions, we leave them
    // both off.
    // Note: The signal being mapped might be an entire signal, e.g. s_SomePin,
    // or it might be a slice of some hidden net, e.g. s_LOGISIM_HIDDEN_OUTPUT[7 downto 3].
    // And that signal might get mapped together to a single I/O device, or each
    // bit might be individually mapped to different I/O devices.
    String signal; // e.g.: s_SomePin or s_LOGISIM_HIDDEN_OUTPUT[7 downto 3].
    String bit;    // e.g.: s_SomePin or s_LOGISIM_HIDDEN_OUTPUT
    int offset;    // e.g.: 0 or 3
    boolean isInput = false, isOutput = false;
    int srcwidth = -1;
    // System.out.println("Generating inline code signal for " + path);
    PullBehavior inputPinPullDir = PullBehavior.NONE;
    if (shadow.original.getFactory() instanceof Pin) {
      signal = "s_" + shadow.pathName();
      bit = signal;
      offset = 0;
      srcwidth = shadow.original.getEnd(0).getWidth().getWidth();
      // note: these next two are reversed intentionally, b/c OutputPin has an
      // EndData configured as an input src w.r.t. logisim circuit, and vice versa
      isInput = shadow.original.getEnd(0).canOutput();
      isOutput = shadow.original.getEnd(0).canInput();
      if (isInput)
        inputPinPullDir = pullDirection(shadow);
    } else {
      NetlistComponent.Range3 indices = shadow.getGlobalHiddenPortIndices(path);
      if (indices == null) {
        out.err.AddFatalError("INTERNAL ERROR: Missing index data for I/O component %s", path);
        return;
      }
      if (indices.end.in == indices.start.in) {
        // foo[5] is the only bit
        offset = indices.start.in;
        bit = "s_LOGISIM_HIDDEN_FPGA_INPUT";
        signal = String.format(bit+out.idx, offset);
        srcwidth = 1;
        isInput = true;
      } else if (indices.end.in > indices.start.in) {
        // foo[8:3]
        offset = indices.start.in;
        bit = "s_LOGISIM_HIDDEN_FPGA_INPUT";
        signal = String.format(bit+out.range, indices.end.in, offset);
        srcwidth = indices.end.in - indices.start.in;
        isInput = true;
      } else if (indices.end.out == indices.start.out) {
        // foo[5] is the only bit
        offset = indices.start.out;
        bit = "s_LOGISIM_HIDDEN_FPGA_OUTPUT";
        signal = String.format(bit+out.idx, offset);
        srcwidth = 1;
        isOutput = true;
      } else if (indices.end.out > indices.start.out) {
        // foo[8:3]
        offset = indices.start.out;
        bit = "s_LOGISIM_HIDDEN_FPGA_OUTPUT";
        signal = String.format(bit+out.range, indices.end.out, offset);
        srcwidth = indices.end.out - indices.start.out + 1;
        isOutput = true;
      } else {
        // This is an inout signal, handled elsewhere (no inversions possible)
        return;
      }
    }

    // Sanity check: one of isInput or isOutput must be set, because
    // bidirectional components are handled elsewhere.
    if (isInput == isOutput)
      out.err.AddSevereWarning("Ambiguous direction for " + signal + " isInput="+isInput+" isOutput="+isOutput);
    // Sanity check: srcwidth should be positive.
    if (srcwidth <= 0)
      out.err.AddSevereWarning("Unexpected width for " + signal + ", expected srcwidth>0: srcwidth="+srcwidth);

    // Notes for both cases below (binding entire port, or individual pins):
    // - srcwidth, isInput, isOutput all come from the Logisim Pin's EndData
    // or shadow's hidden ports.
    // - src.width comes from the PinBinding dialog.
    // - dest.io is the underlying synthetic input or physical fpga resource
    // from the PinBinding dialog.

    PinBindings.Source src = ioResources.sourceFor(path);
    PinBindings.Dest dest = ioResources.mappings.get(src);
    // System.out.println("src="+src);
    // System.out.println("dest="+dest);
    if (dest != null) { // Entire port is mapped to one BoardIO resource.

      // Sanity check: src.width.inout should be zero, because bidirectional
      // components are handled elsewhere.
      if (src.width.inout != 0)
        out.err.AddSevereWarning("Unexpected width for " + signal + ", expected inout=0: src.width="+src.width+" srcwidth="+srcwidth);

      // Sanity check: srcwidth should match one of src.width.in or
      // src.width.out, and the other should be zero, depending on isInput or
      // isOutput.
      if (isInput && (srcwidth != src.width.in || src.width.out != 0))
        out.err.AddSevereWarning("Unexpected width for " + signal + ", expected srcwidth=src.width.in: src.width="+src.width+" srcwidth="+srcwidth);
      if (isOutput && (srcwidth != src.width.out || src.width.in != 0))
        out.err.AddSevereWarning("Unexpected width for " + signal + ", expected srcwidth=src.width.out: src.width="+src.width+" srcwidth="+srcwidth);

      Netlist.Int3 destwidth = dest.io.getPinCounts();
      // Sanity check: destwidth should have one non-zero component.
      if (destwidth.isMixedDirection())
        out.err.AddSevereWarning("Unexpected mixed direction for " + dest + ": destwidth="+destwidth);
      // Sanity check: destwidth should be compatible with src.width.
      if (isInput && src.width.in > destwidth.in + destwidth.inout)
        out.err.AddSevereWarning("Unexpected mismatched widths for " + signal + " and " + dest + ": src.width="+src.width+" destwidth="+destwidth);
      if (isOutput && src.width.out > destwidth.out + destwidth.inout)
        out.err.AddSevereWarning("Unexpected mismatched widths for " + signal + " and " + dest + ": src.width="+src.width+" destwidth="+destwidth);
      // Note: In many cases, src.width and destwidth are equal, e.g. when src
      // is a Logisim HexDisplay, and dest is an FPGA SevenSegment, or when src
      // is a Logisim 1-bit Logisim Output Pin and dest is an FPGA LED. But in
      // other cases, src.width and dest.io.PinCounts() differ, such as when src
      // is a 1-bit Logisim Output Pin and dest is a single bit of a 32-bit
      // Ribbon, or when src is a Logisim LED and dest is a single bit of a
      // SevenSegment. But even when mismatched, the destwidth must be no
      // smaller than the src.width.

      boolean invert = needTopLevelInversion(shadow.original, dest.io);
      String maybeNot = (invert ? out.not + " " : "");
      if (dest.io.type == BoardIO.Type.Unconnected) {
        // If user assigned type "unconnected", do nothing. Synthesis will warn,
        // but optimize away the signal.
      } else if (!BoardIO.PhysicalTypes.contains(dest.io.type)) {
        // Handle synthetic input types.
        // Sanity check: only inputs can be synthetic.
        if (!isInput)
          out.err.AddSevereWarning("Conflicting synthetic input direction for " + signal);
        int constval = dest.io.syntheticValue;
        out.assign(signal, maybeNot+out.literal(constval, src.width.in));
      } else {
        // Handle physical I/O device types.
        Netlist.Int3 seqno = dest.seqno();
        // Inputs
        if (isInput) {
          recordInputPullDirection(out, inputPinPullDir, shadow, src, dest);
          if (src.width.in == 1) {
            out.assign(signal, maybeNot+"FPGA_INPUT_PIN_"+seqno.in);
          } else for (int i = 0; i < src.width.in; i++) { // TODO: verify src.width.in instead of destwidth.in
            out.assign(bit, offset+i, maybeNot+"FPGA_INPUT_PIN_"+(seqno.in+i));
          }
        }
        // Outputs
        else {
          if (src.width.out == 1)
            out.assign("FPGA_OUTPUT_PIN_"+seqno.out, maybeNot+signal);
          else for (int i = 0; i < src.width.out; i++) // TODO: verify src.width.out instead of destwidth.out
            out.assign("FPGA_OUTPUT_PIN_"+(seqno.out+i), maybeNot+bit, offset+i);
        }
      }
    } else { // Each bit of pin is assigned to a different BoardIO resource.
      ArrayList<PinBindings.Source> srcs = ioResources.bitSourcesFor(path);
      for (int i = 0; i < srcs.size(); i++)  {
        src = srcs.get(i);
        dest = ioResources.mappings.get(src);
        // System.out.println("src["+i+"]="+src);
        // System.out.println("dest["+i+"]="+dest);

        // Individual pins are handled almost identically to the code above,
        // with only slight changes. All the sanity checks in the above case
        // apply as well, except src.width.in (or out) will be 1, and srcwidth
        // is no longer relevant.

        // Sanity check: src.width.inout should be zero, because bidirectional
        // components are handled elsewhere.
        if (src.width.inout != 0)
          out.err.AddSevereWarning("Unexpected width for " + signal + " bit " + i +", expected inout=0: src.width="+src.width+" srcwidth="+srcwidth);

        // Sanity check: srcwidth should match one of src.width.in or
        // src.width.out, and the other should be zero, depending on isInput or
        // isOutput.
        if (isInput && (1 != src.width.in || src.width.out != 0))
          out.err.AddSevereWarning("Unexpected width for " + signal + " bit " + i +", expected srcwidth=src.width.in: src.width="+src.width+" srcwidth="+srcwidth);
        if (isOutput && (1 != src.width.out || src.width.in != 0))
          out.err.AddSevereWarning("Unexpected width for " + signal + " bit " + i +", expected srcwidth=src.width.out: src.width="+src.width+" srcwidth="+srcwidth);

        Netlist.Int3 destwidth = dest.io.getPinCounts();
        // Sanity check: destwidth should have one non-zero component.
        if (destwidth.isMixedDirection())
          out.err.AddSevereWarning("Unexpected mixed direction for " + dest + ": destwidth="+destwidth);
        // Sanity check: destwidth should be compatible with src.width.
        if (isInput && src.width.in > destwidth.in + destwidth.inout)
          out.err.AddSevereWarning("Unexpected mismatched widths for " + signal + " and " + dest + ": src.width="+src.width+" destwidth="+destwidth);
        if (isOutput && src.width.out > destwidth.out + destwidth.inout)
          out.err.AddSevereWarning("Unexpected mismatched widths for " + signal + " and " + dest + ": src.width="+src.width+" destwidth="+destwidth);
        
        boolean invert = needTopLevelInversion(shadow.original, dest.io);
        String maybeNot = (invert ? out.not + " " : "");
        if (dest.io.type == BoardIO.Type.Unconnected) {
          // If user assigned type "unconnected", do nothing. Synthesis will warn,
          // but optimize away the signal.
          continue;
        } else if (!BoardIO.PhysicalTypes.contains(dest.io.type)) {
          // Handle synthetic input types.
          // Sanity check: only inputs can be synthetic.
          if (!isInput)
            out.err.AddSevereWarning("Conflicting synthetic input direction for " + signal + " bit " + i);
          int constval = dest.io.syntheticValue;
          out.assign(bit, offset+i, maybeNot+out.literal(constval, 1));
        } else {
          // Handle physical I/O device types.
          Netlist.Int3 seqno = dest.seqno();
          // Inputs
          if (isInput) {
            recordInputPullDirection(out, inputPinPullDir, shadow, src, dest);
            out.assign(bit, offset+i, maybeNot+"FPGA_INPUT_PIN_"+seqno.in);
          }
          // Outputs
          else {
            out.assign("FPGA_OUTPUT_PIN_"+seqno.out, maybeNot+bit, offset+i);
          }
        }
      }
    }
  }

  private void generateInlinedCodeBidirSignal(Hdl out, Path path, NetlistComponent shadow) {
    // This implements the top-level tri-state logic needed for bidirectional
    // pins.
    // Note: Any logisim component that is not active-high will get an inversion
    // here. Also, any FPGA I/O device that is not active-high will get an
    // inversion. In cases where there would be two inversions, we leave them
    // both off.
    // Note: The signal being mapped might be an entire signal, e.g. s_SomePin,
    // or it might be a slice of some hidden net, e.g. s_LOGISIM_HIDDEN_OUTPUT[7 downto 3].
    // And that signal might get mapped together to a single I/O device, or each
    // bit might be individually mapped to different I/O devices.
    String signal; // e.g.: s_SomePin or s_LOGISIM_HIDDEN_OUTPUT[7 downto 3].
    String bit;    // e.g.: s_SomePin or s_LOGISIM_HIDDEN_OUTPUT
    int offset;    // e.g.: 0 or 3
    int srcwidth = -1;
    // System.out.println("Generating inline code signal for " + path);
    PullBehavior inputPinPullDir = PullBehavior.NONE;
    if (shadow.original.getFactory() instanceof Pin)
      return; // Pin is not bidirectional
    NetlistComponent.Range3 indices = shadow.getGlobalHiddenPortIndices(path);
    if (indices == null)
      return; // error
    if (indices.end.in >= indices.start.in)
      return; // input pin
    if (indices.end.out >= indices.start.out)
      return; // output pin

    offset = indices.start.inout;
    bit = "s_LOGISIM_HIDDEN_FPGA_BIDIR";
    srcwidth = indices.end.inout - indices.start.inout + 1;
    if (indices.end.inout == indices.start.inout) { // foo[5] is the only bit
      signal = String.format(bit+out.idx, offset);
    } else if (indices.end.inout > indices.start.inout) { // foo[8:3]
      signal = String.format(bit+out.range, indices.end.inout, offset);
    } else { // error
      return;
    }

    // Sanity check: srcwidth should be positive.
    if (srcwidth <= 0)
      out.err.AddSevereWarning("Unexpected width for " + signal + ", expected srcwidth>0: srcwidth="+srcwidth);

    // Notes for both cases below (binding entire port, or individual pins):
    // - srcwidth, isInput, isOutput all come from the shadow's hidden ports.
    // - src.width comes from the PinBinding dialog.
    // - dest.io is the underlying synthetic input or physical fpga resource
    // from the PinBinding dialog.

    PinBindings.Source src = ioResources.sourceFor(path);
    PinBindings.Dest dest = ioResources.mappings.get(src);
    // System.out.println("src="+src);
    // System.out.println("dest="+dest);
    if (dest != null) { // Entire port is mapped to one BoardIO resource.

      // Sanity check: src.width.in and src.width.out should be zero, because
      // unidirectional components are handled elsewhere.
      if (src.width.in != 0 || src.width.out != 0)
        out.err.AddSevereWarning("Unexpected width for " + signal + ", expected in=out=0: src.width="+src.width+" srcwidth="+srcwidth);

      // Sanity check: srcwidth should match src.width.inout
      if (srcwidth != src.width.inout)
        out.err.AddSevereWarning("Unexpected width for " + signal + ", expected srcwidth=src.width.inout: src.width="+src.width+" srcwidth="+srcwidth);

      Netlist.Int3 destwidth = dest.io.getPinCounts();
      // Sanity check: destwidth should have one non-zero component.
      if (destwidth.isMixedDirection())
        out.err.AddSevereWarning("Unexpected mixed direction for " + dest + ": destwidth="+destwidth);
      // Sanity check: destwidth should be compatible with src.width.
      if (src.width.inout > destwidth.inout)
        out.err.AddSevereWarning("Unexpected mismatched widths for " + signal + " and " + dest + ": src.width="+src.width+" destwidth="+destwidth);

      boolean invert = needTopLevelInversion(shadow.original, dest.io);
      String maybeNot = (invert ? out.not + " " : "");
      if (dest.io.type == BoardIO.Type.Unconnected) {
        // If user assigned type "unconnected", do nothing. Synthesis will warn,
        // but optimize away the signal.
      } else if (!BoardIO.PhysicalTypes.contains(dest.io.type)) {
        // Sanity check: only inputs can be synthetic.
        out.err.AddSevereWarning("Conflicting synthetic input direction for " + signal);
        // int constval = dest.io.syntheticValue;
        // out.assign(signal, maybeNot+out.literal(constval, src.width.inout));
      } else {
        // Handle physical I/O device types.
        Netlist.Int3 seqno = dest.seqno();
        // TODO: support for PortIO pullup resistors?
        // recordInputPullDirection(out, inputPinPullDir, shadow, src, dest);
        if (useTristates) {
          // Input half
          if (src.width.inout == 1) {
            out.assign("in_"+signal, maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout);
          } else for (int i = 0; i < src.width.inout; i++) { // TODO: verify src.width.inout instead of destwidth.inout
            out.assign("in_"+bit, offset+i, maybeNot+"FPGA_BIDIR_PIN_"+(seqno.inout+i));
          }
          // Output half
          if (src.width.inout == 1)
            out.assignTristate("FPGA_BIDIR_PIN_"+seqno.inout, maybeNot+"out_"+signal, "en_"+signal);
          else for (int i = 0; i < src.width.inout; i++) // TODO: verify src.width.inout instead of destwidth.inout
            out.assignTristate("FPGA_BIDIR_PIN_"+(seqno.inout+i), maybeNot+"out_"+bit, offset+i, "en_"+bit, offset+i);
        } else {
          if (src.width.inout == 1) {
            out.assign("in_"+signal, maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout+"_IN");
            out.assign(maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout+"_OUT", "out_"+signal);
            out.assign(maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout+"_EN", "en_"+signal);
          } else for (int i = 0; i < src.width.inout; i++) { // TODO: verify src.width.inout instead of destwidth.inout
            out.assign("in_"+bit, offset+i, maybeNot+"FPGA_BIDIR_PIN_"+(seqno.inout+i)+"_IN");
            out.assign(maybeNot+"FPGA_BIDIR_PIN_"+(seqno.inout+i)+"_OUT", "out_"+bit, offset+i);
            out.assign(maybeNot+"FPGA_BIDIR_PIN_"+(seqno.inout+i)+"_EN", "en_"+bit, offset+i);
          }
        }
      }
    } else { // Each bit of pin is assigned to a different BoardIO resource.
      ArrayList<PinBindings.Source> srcs = ioResources.bitSourcesFor(path);
      for (int i = 0; i < srcs.size(); i++)  {
        src = srcs.get(i);
        dest = ioResources.mappings.get(src);
        // System.out.println("src["+i+"]="+src);
        // System.out.println("dest["+i+"]="+dest);

        // Individual pins are handled almost identically to the code above,
        // with only slight changes. All the sanity checks in the above case
        // apply as well, except src.width.inout will be 1, and srcwidth
        // is no longer relevant.

        // Sanity check: src.width.in and src.width.out should be zero, because
        // unidirectional components are handled elsewhere.
        if (src.width.in != 0 || src.width.out != 0)
          out.err.AddSevereWarning("Unexpected width for " + signal + " bit " + i +", expected in=out=0: src.width="+src.width+" srcwidth="+srcwidth);

        // Sanity check: srcwidth should match src.width.inout.
        if (1 != src.width.inout)
          out.err.AddSevereWarning("Unexpected width for " + signal + " bit " + i +", expected srcwidth=src.width.inout: src.width="+src.width+" srcwidth="+srcwidth);

        Netlist.Int3 destwidth = dest.io.getPinCounts();
        // Sanity check: destwidth should have one non-zero component.
        if (destwidth.isMixedDirection())
          out.err.AddSevereWarning("Unexpected mixed direction for " + dest + ": destwidth="+destwidth);
        // Sanity check: destwidth should be compatible with src.width.
        if (src.width.inout > destwidth.inout)
          out.err.AddSevereWarning("Unexpected mismatched widths for " + signal + " and " + dest + ": src.width="+src.width+" destwidth="+destwidth);
        
        boolean invert = needTopLevelInversion(shadow.original, dest.io);
        String maybeNot = (invert ? out.not + " " : "");
        if (dest.io.type == BoardIO.Type.Unconnected) {
          // If user assigned type "unconnected", do nothing. Synthesis will warn,
          // but optimize away the signal.
          continue;
        } else if (!BoardIO.PhysicalTypes.contains(dest.io.type)) {
          // Sanity check: only inputs can be synthetic.
          out.err.AddSevereWarning("Conflicting synthetic input direction for " + signal + " bit " + i);
          // int constval = dest.io.syntheticValue;
          // out.assign(bit, offset+i, maybeNot+out.literal(constval, 1));
        } else {
          // Handle physical I/O device types.
          Netlist.Int3 seqno = dest.seqno();
          // todo: support for PortIO pullup resistors?
          // recordInputPullDirection(out, inputPinPullDir, shadow, src, dest);
          if (useTristates) {
            // Input half
            out.assign("in_"+bit, offset+i, maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout);
            // Output half
            out.assignTristate("FPGA_BIDIR_PIN_"+seqno.inout, maybeNot+"out_"+bit, offset+i, "en_"+bit, offset+i);
          } else {
            out.assign("in_"+bit, offset+i, maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout+"_IN");
            out.assign(maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout+"_OUT", "out_"+bit, offset+i);
            out.assign(maybeNot+"FPGA_BIDIR_PIN_"+seqno.inout+"_EN", "en_"+bit, offset+i);
          }
        }
      }
    }
  }

  public void notifyNetlistReady() {
    circgen.notifyNetlistReady();
  }

	protected Hdl getArchitecture() {
    return getArchitecture(useTristates);
  }

}
