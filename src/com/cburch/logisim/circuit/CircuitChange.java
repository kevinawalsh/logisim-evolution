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

package com.cburch.logisim.circuit;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;

// Capturing Changes:
// CircuitChange is used by CircuitMutatorImpl so it can record a log of all
// changes done to a circuit during a CircuitTrasnsaction's execute (and run)
// methods. This log is used to make the reverse transaction for undo
// operations.
//
// Planning:
// CircuitChange is used by CircuitMutation (the general-purpose, generic
// CircuitTransaction subclass) as a way to record a set of planned changes to a
// circuit, before actually carrying them out during transaction execution.
abstract class CircuitChange {

  public final Circuit circuit;
  public final VhdlContent vhdl;

  private CircuitChange(Circuit circuit) {
    this.circuit = circuit;
    this.vhdl = null;
  }

  private CircuitChange(VhdlContent vhdl) {
    this.circuit = null;
    this.vhdl = vhdl;
  }

  // API:
  public abstract void apply(ReplacementLog repl);
  public abstract CircuitChange inverse();
  public abstract boolean concernsSupercircuit();

  // NOTE: Within apply, for reverse transactions, recording accurate wire
  // selection changes in repl isn't important, because the selection code has a
  // snapshot of the selection for that case.

  public final static class ADD extends CircuitChange {
    private final Component comp;
    private boolean added;

    public ADD(Circuit circuit, Component comp) {
      super(circuit);
      this.comp = comp;
    }

    @Override
    public String toString() { return "ADD " + comp + " TO " + circuit.getName(); }

    @Override
    public void apply(ReplacementLog repl) {
      added = circuit.mutatorAdd(comp);
      if (added)
        repl.logAddition(comp);
    }

    @Override
    CircuitChange inverse() {
      return added ? new REMOVE(circuit, comp) : null;
    }

    @Override
    boolean concernsSupercircuit() {
      return comp.getFactory() instanceof Pin;
    }
  }

  public final static class REMOVE extends CircuitChange {
    private final Component comp;
    private boolean removed;

    public REMOVE(Circuit circuit, Component comp) {
      super(circuit);
      this.comp = comp;
    }

    @Override
    public String toString() { return "REMOVE " + comp + " FROM " + circuit.getName(); }

    @Override
    public void apply(ReplacementLog repl) {
      removed = circuit.mutatorRemove(comp);
      if (removed)
        repl.logRemoval(comp);
    }

    @Override
    CircuitChange inverse() {
      return removed ? new ADD(circuit, comp) : null;
    }

    @Override
    boolean concernsSupercircuit() {
      return comp.getFactory() instanceof Pin;
    }
  }

  public final static class ADD_ALL extends CircuitChange {
    private final Component comps[];
    private boolean added[];

    public ADD_ALL(Circuit circuit, Collection<Component> comps) {
      super(circuit);
      this.comps = comps.toArray(new Component[0]);
    }
    private ADD_ALL(Circuit circuit, int n) {
      super(circuit);
      this.comps = new Component[n];
    }

    @Override
    public String toString() { return "ADDx" + comps.length + " components TO " + circuit.getName(); }

    @Override
    public void apply(ReplacementLog repl) {
      added = new boolean[comps.length];
      for (int i = 0; i < comps.length; i++) {
        added[i] = comps[i] != null && circuit.mutatorAdd(comps[i]);
        if (added[i])
          repl.logAddition(comps[i]);
      }
    }

    @Override
    CircuitChange inverse() {
      if (added == null) return null;
      int n = comps.length;
      REMOVE_ALL inv = new REMOVE_ALL(circuit, n);
      for (int i = 0; i < n; i++)
        inv.comps[n-i-1] = added[i] ? comps[i] : null;
      return inv;
    }

    @Override
    boolean concernsSupercircuit() {
      for (Component comp : comps)
        if (comp != null && comp.getFactory() instanceof Pin)
          return true;
      return false;
    }
  }

  public final static class REMOVE_ALL extends CircuitChange {
    private final Component comps[];
    private boolean removed[];

    public REMOVE_ALL(Circuit circuit, Collection<Component> comps) {
      super(circuit);
      this.comps = comps.toArray(new Component[0]);
    }
    private REMOVE_ALL(Circuit circuit, int n) {
      super(circuit);
      this.comps = new Component[n];
    }

    @Override
    public String toString() { return "REMOVEx" + comps.length + " components FROM " + circuit.getName(); }

    @Override
    public void apply(ReplacementLog repl) {
      removed = new boolean[comps.length];
      for (int i = 0; i < comps.length; i++) {
        removed[i] = comps[i] != null && circuit.mutatorRemove(comps[i]);
        if (removed[i])
          repl.logRemoval(comps[i]);
      }
    }

    @Override
    CircuitChange inverse() {
      if (removed == null) return null;
      int n = comps.length;
      ADD_ALL inv = new ADD_ALL(circuit, n);
      for (int i = 0; i < n; i++)
        inv.comps[n-i-1] = removed[i] ? comps[i] : null;
      return inv;
    }

    @Override
    boolean concernsSupercircuit() {
      for (Component comp : comps)
        if (comp != null && comp.getFactory() instanceof Pin)
          return true;
      return false;
    }
  }

  public final static class REPLACE_PAIRS extends CircuitChange {
    private final Component oldComps[], newComps[];
    private int status[]; // 0 = none, 1 = removed, 2 = added, 3 = replaced
    
    public REPLACE_PAIRS(Circuit circuit, Map<Component, Component> pairs) {
      super(circuit);
      int n = pairs.size();
      this.oldComps = new Component[n];
      this.newComps = new Component[n];
      int i = 0;
      for (Map.Entry<Component, Component> e : pairs.entrySet()) {
        oldComps[i] = e.getKey();
        newComps[i] = e.getValue();
        i++;
      }
    }
    public REPLACE_PAIRS(Circuit circuit, List<Component> oldComps, List<Component> newComps) {
      super(circuit);
      this.oldComps = oldComps.toArray(new Component[0]);
      this.newComps = newComps.toArray(new Component[0]);
    }
    private REPLACE_PAIRS(Circuit circuit, int n) {
      super(circuit);
      this.oldComps = new Component[n];
      this.newComps = new Component[n];
    }

    @Override
    public String toString() { return "REPLACEx" + oldComps.length + " components IN " + circuit.getName(); }

    @Override
    public void apply(ReplacementLog repl) {
      status = new int[oldComps.length];
      for (int i = 0; i < oldComps.length; i++) {
        status[i] = 0;
        Component c = oldComps[i];
        Component r = newComps[i];
        // Skip no-op replacements, so ReplacementLog doesn't have to deal with them.
        if (c == r || (c != null && r != null && c.equals(r)))
          continue;
        if (c == null || r == null || c instanceof Wire || r instanceof Wire) {
          if (c != null && circuit.mutatorRemove(c)) status[i] |= 1;
          if (r != null && circuit.mutatorAdd(r)) status[i] |= 2;
          if (c instanceof Wire && r instanceof Wire) {
            // if both are wires, then selection follows the replaceemnt,
            // regardless of whether the operation succeeded or failed
            repl.moveWireSelection((Wire)c, Collections.singleton((Wire)r));
          } else {
            // All other cases, treat as separate operations.
            //  - one of them is null, so this is a plain add or remove
            //    (never happens in forward xn, so selection doesn't matter)
            //  - one is a wire, other is a non-wire
            //    (never happens, selection doesn't matter)
            if ((status[i] & 1) != 0) repl.logRemoval(c);
            if ((status[i] & 2) != 0) repl.logAddition(r);
          }
        } else {
          if (circuit.mutatorReplaceNonWire(c, r)) {
            status[i] = 3;
            repl.logNonWireReplacement(c, r);
          }
        }
      }
    }

    @Override
    CircuitChange inverse() {
      if (status == null) return null;
      int n = oldComps.length;
      REPLACE_PAIRS inv = new REPLACE_PAIRS(circuit, n);
      // fill out arrays in reverse, but null out things we didn't accomplish
      for (int i = 0; i < n; i++) {
        inv.oldComps[n-i-1] = ((status[i] & 2) == 0) ? null : newComps[i]; // remove if new was added
        inv.newComps[n-i-1] = ((status[i] & 1) == 0) ? null : oldComps[i]; // add if old was removed
      }
      return inv;
    }

    @Override
    boolean concernsSupercircuit() {
      for (int i = 0; i < oldComps.length; i++)
        if ((oldComps[i] != null && oldComps[i].getFactory() instanceof Pin)
            || (newComps[i] != null && newComps[i].getFactory() instanceof Pin))
          return true;
      return false;
    }
  }

  public final static class REPAIR_WIRES extends CircuitChange {
    // Handles either:
    // - N>=1 existing wires merged, replaced by 1 (new or possibly existing) wire
    // - 1 existing wire split, replaced by N>=1 (new or possibly existing) wires
    // Note: N=1 should be no-op in practice, but RepairWireHelper currently
    // does it in some cases (out of carelessness).
    private final Wire oldWires[], newWires[]; // N-to-1, 1-to-N, or 1-to-1
    private boolean removed[], added[];

    public REPAIR_WIRES(Circuit circuit, Collection<Wire> oldWires, Collection<Wire> newWires) {
      super(circuit);
      this.oldWires  = oldWires.toArray(new Wire[0]);
      this.newWires = newWires.toArray(new Wire[0]);
    }
    private REPAIR_WIRES(Circuit circuit, int nOld, int nNew) {
      super(circuit);
      this.oldWires  = new Wire[nOld];
      this.newWires = new Wire[nNew];
    }

    @Override
    public String toString() {
      return "REPAIR_WIRES " + oldWires.length + "-to-" + newWires.length
        + " IN " + circuit.getName();
    }

    @Override
    public void apply(ReplacementLog repl) {
      removed = new boolean[oldWires.length];
      added = new boolean[newWires.length];
      for (int i = 0; i < oldWires.length; i++)
        removed[i] = oldWires[i] != null && circuit.mutatorRemove(oldWires[i]);
      for (int i = 0; i < newWires.length; i++)
        added[i] = newWires[i] != null && circuit.mutatorAdd(newWires[i]);
      // move selection, even if the operations failed or partly failed.
      // note: newWires might have nulls, but repl will filter them out.
      for (int i = 0; i < oldWires.length; i++)
        if (oldWires[i] != null)
          repl.moveWireSelection(oldWires[i], Arrays.asList(newWires));
    }

    @Override
    CircuitChange inverse() {
      if (removed == null) return null;
      int nOld = oldWires.length;
      int nNew = newWires.length;
      REPAIR_WIRES inv = new REPAIR_WIRES(circuit, nNew, nOld);
      // fill out arrays in reverse, but null out things we didn't accomplish
      for (int i = 0; i < nNew; i++)
        inv.oldWires[nNew-i-1] = added[i] ? newWires[i] : null; // remove if new was added
      for (int i = 0; i < nOld; i++)
        inv.newWires[nOld-i-1] = removed[i] ? oldWires[i] : null; // add if old was removed
      return inv;
    }

    @Override
    boolean concernsSupercircuit() {
      return false;
    }
  }


  public final static class SET_COMP_ATTR extends CircuitChange {
    private final Component comp;
    private final Attribute<?> attr;
    private final Object newValue;
    private Object oldValue;
    private boolean set;

    public SET_COMP_ATTR(Circuit circuit, Component comp, Attribute<?> attr, Object newValue) {
      this(circuit, comp, attr, null, newValue);
    }

    private SET_COMP_ATTR(Circuit circuit, Component comp, Attribute<?> attr, Object oldValue, Object newValue) {
      super(circuit);
      this.comp = comp;
      this.attr = attr;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }

    @Override
    public String toString() {
      return "SET " + comp + " " + attr + " = " + newValue
        + (oldValue != null ? " (from " + oldValue + ")" : "")
        + " IN " + circuit.getName();
    }

    @Override
    public void apply(ReplacementLog repl) {
      if (circuit.contains(comp)) {
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) attr;
        AttributeSet attrs = comp.getAttributeSet();
        oldValue = attrs.getValue(a);
        attrs.setAttr(a, newValue);
        set = true;
      }
    }

    @Override
    CircuitChange inverse() {
      return set ? new SET_COMP_ATTR(circuit, comp, attr, oldValue, newValue) : null;
    }

    @Override
    boolean concernsSupercircuit() {
      // NOTE: The list of attributes in appear/CircuitPins which could affect the
      // appearance ports and layout must be consistent with the list here, which
      // ensures affected circuits are locked.
      // Note: WIDTH needed b/c handling changes, even though appearance is the same
      // Note: BEHAVIOR does not affect appearance or parent
      return comp.getFactory() instanceof Pin
          && (attr == StdAttr.WIDTH
              || attr == Pin.ATTR_TYPE
              || attr == StdAttr.LABEL
              || attr == StdAttr.FACING);
    }
  }

  public final static class SET_CIRC_ATTR extends CircuitChange {
    private final Attribute<?> attr;
    private final Object newValue;
    private Object oldValue;
    private boolean set;

    public SET_CIRC_ATTR(Circuit circuit, Attribute<?> attr, Object newValue) {
      this(circuit, attr, null, newValue);
    }

    private SET_CIRC_ATTR(Circuit circuit, Attribute<?> attr, Object oldValue, Object newValue) {
      super(circuit);
      this.attr = attr;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }

    @Override
    public String toString() {
      return "SET " + attr + " = " + newValue
        + (oldValue != null ? " (from " + oldValue + ")" : "")
        + " OF " + circuit.getName();
    }

    @Override
    public void apply(ReplacementLog repl) {
      @SuppressWarnings("unchecked")
      Attribute<Object> a = (Attribute<Object>) attr;
      AttributeSet attrs = circuit.getStaticAttributes();
      oldValue = attrs.getValue(a);
      attrs.setAttr(a, newValue);
      set = true;
    }

    @Override
    CircuitChange inverse() {
      return set ? new SET_CIRC_ATTR(circuit, attr, newValue, oldValue) : null;
    }

    @Override
    boolean concernsSupercircuit() {
      return attr == CircuitAttributes.CIRCUIT_APPEARANCE
          || attr == CircuitAttributes.CIRCUIT_NAME
          || attr == CircuitAttributes.CIRCUIT_REVISION
          || attr == CircuitAttributes.CIRCUIT_REVISION_FACING_ATTR
          || attr == CircuitAttributes.CIRCUIT_REVISION_FONT_ATTR;
    }
  }

  public final static class SET_VHDL_ATTR extends CircuitChange {
    private final Attribute<?> attr;
    private final Object newValue;
    private Object oldValue;
    private boolean set;

    public SET_VHDL_ATTR(VhdlContent vhdl, Attribute<?> attr, Object newValue) {
      this(vhdl, attr, null, newValue);
    }

    private SET_VHDL_ATTR(VhdlContent vhdl, Attribute<?> attr, Object oldValue, Object newValue) {
      super(vhdl);
      this.attr = attr;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }

    @Override
    public String toString() {
      return "SET " + attr + " = " + newValue
        + (oldValue != null ? " (from " + oldValue + ")" : "")
        + " OF " + vhdl.getName();
    }

    @Override
    public void apply(ReplacementLog repl) {
      @SuppressWarnings("unchecked")
      Attribute<Object> a = (Attribute<Object>) attr;
      AttributeSet attrs = vhdl.getStaticAttributes();
      oldValue = attrs.getValue(a);
      attrs.setAttr(a, newValue);
      set = true;
    }

    @Override
    CircuitChange inverse() {
      return set ? new SET_VHDL_ATTR(vhdl, attr, newValue, oldValue) : null;
    }

    @Override
    boolean concernsSupercircuit() {
      return attr == VhdlEntity.NAME_ATTR
          || attr == StdAttr.APPEARANCE; // note: always true so far
    }
  }

}
