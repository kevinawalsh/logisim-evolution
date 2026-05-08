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

import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;

public abstract class HDLSupport {

  // Parameters used for one specific component or (sub)circuit within a
  // generate-synthesis-download effort.
  public static class ComponentContext extends Netlist.Context {
    public final Netlist nets;
    public final NetlistComponent comp; // null for some top-level things
    public final AttributeSet attrs;
    public ComponentContext(Netlist.Context ctx /* for entire effort */,
        Netlist nets /* for circuit containing this component, if any */,
        NetlistComponent comp) {
      super(ctx);
      this.nets = nets;
      this.comp = comp;
      AttributeSet attrs = comp == null ? null : comp.original.getAttributeSet();
      this.attrs = attrs != null ? attrs : AttributeSets.EMPTY;
    }
  }

  public final ComponentContext ctx;
  public final String _projectName; // context - fixme
 
  // A user-friendly name, unique across the circuit in which this component
  // appears. For I/O related components, for top-level Pins, and for
  // subcircuits, the GUI displays this name as part of a Path, so the user can
  // bind the component (which may be nested inside a subcircuit) to an FPGA I/O
  // resource.
  public final String hdlPathName;

  public final String _lang; // context - fixme
  public final FPGAReport _err; // context - fixme
  public final Netlist _nets; // context - fixme // signals of the circuit in
  // which this component is embeded. For CircuitHDLGeneratorComponent, this is
  // the signals for the *parent* circuit (or null, if it is the top-level
  // circuit), not the signals within this subcircuit.
  public final AttributeSet _attrs; // context - fixme
  // public final char _vendor; // context - fixme
  public final boolean inlined;
  public final Hdl _hdl;

  protected HDLSupport(ComponentContext ctx, boolean inlined) {
    this.ctx = ctx;
    this._projectName = ctx.err.getProjectName();
    this._lang = ctx.lang;
    this._err = ctx.err;
    this._nets = ctx.nets; // sometimes null, i.e. for top level circuit and also for quick checks
    this._attrs = ctx.attrs; // empty for Circuit, Ticker, maybe others?
    // this._vendor = ctx.vendor;
    this.inlined = inlined;
    this.hdlPathName = ctx.comp == null ? null : deriveHdlPathName(ctx.comp.original);
    this._hdl = new Hdl(_lang, _err);
  }

  // For non-inlined HDLGenerator classes.
  public boolean writeHDLFiles(String rootDir) { return true; }
	protected void generateComponentDeclaration(Hdl out) { }
	protected void generateComponentInstance(Hdl out, long id, NetlistComponent comp) { }
	protected String getInstanceNamePrefix() { return null; }
  protected String getHDLModuleName() { return null; }
  public boolean hdlDependsOnCircuitState() { return false; } // for NVRAM
  public boolean writeAllHDLThatDependsOn(CircuitState cs, NetlistComponent comp,
      Path path, String rootDir) { return true; } // for NVRAM

  // For HDLInliner classes.
	protected void generateInlinedCode(Hdl out, NetlistComponent comp) { }


  // Helpers for subclasses...

  protected int stdWidth() {
    return _attrs.getValueOrElse(StdAttr.WIDTH, BitWidth.ONE).getWidth();
  }

  protected boolean isBus() {
    return stdWidth() > 1;
  }

  protected boolean edgeTriggered() {
    return _attrs.containsAttribute(StdAttr.EDGE_TRIGGER)
        || (_attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_FALLING)
        || (_attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_RISING);
  }

  // Return a suitable HDL path for a component, e.g. "Button A" or 
  // "Unnamed Button at (220, 100)". This needs to be unique
  // across the containing circuit (but not across the entire project). It
  // should be user-friendly, because it is used in the GUI for binding I/O
  // related components to FPGA I/O resources. And because we store I/O binding
  // information in the logisim file, the names used here need to be stable
  // across file save-close-open. 
  //
  // Note: Here we just use the label or location to make things unique.
  // Elsewhere (during DRC and/or annotate) we check if this is sufficiently
  // unique and, if not, either fail or re-label the components.
  // FIXME: we actually don't check anywhere. We should do it here using
  // Netlist.Context instead.
  //
  // Note: Only subcircuits and components with I/O bindings need path names.
  // Other components will never appear as part of a path.
  public static String deriveHdlPathName(Component comp) {
    String prefix = comp.getFactory().getHDLNamePrefix(comp);
    if (prefix == null)
      return null; // component does not need a path name
    // Special case: Pin components path names are used within the HDL itself
    // for the HDL port names. So the path name must be unique even after
    // sanitizing (i.e. removing illegal characaters).
    // FIXME: We don't actually ensure this, but just hope for the best here.
    boolean sanitize = comp.getFactory() instanceof Pin;
    String suffix = comp.getAttributeSet().getValueOrElse(StdAttr.LABEL, "");
    if (!suffix.isEmpty())
      suffix = suffix.replaceAll("/", "_"); // path elements may not contain slashes
    else
      suffix = "at " + comp.getLocation();
    if (sanitize) {
      String s = prefix + "_" + suffix;
      s = s.replaceAll("[^a-zA-Z0-9]{1,}", "_");
      s = s.replaceAll("^_", "");
      s = s.replaceAll("_$", "");
      return s;
    } else {
      return prefix + " " + suffix;
    }
  }

  // Some components can have hidden connections to FPGA board resource, e.g. an
  // LED component has a regular logisim input, but it also has a hidden FPGA
  // output that needs to routed up to the top-level HDL circuit and eventually
  // be connected to an FPGA "LED" or "Pin" resource. Similarly, a Button
  // component has a regular logisim output, but also a separate hidden FPGA
  // input that gets routed up to the top level and can be connected to an FPGA
  // "Button", "DipSwitch", "Pin", or other compatable FPGA resource. The
  // hiddenPorts object, if not null, holds info about what type of FPGA
  // resource is most suitable, alternate resource types, how many in/out/inout
  // pins are involved, names for the signals, etc.
  protected HiddenPort hiddenPort = null;
  public HiddenPort hiddenPort() { return hiddenPort; }

}
