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
package com.cburch.logisim.std.memory;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceState;
// import com.cburch.logisim.std.memory.Mem.MemListener;

public class RamState extends MemState
  implements ComponentData.WithLifetimeTracking, AttributeListener {

  private Instance parent;
  // private MemListener listener;
  private ClockState clockState;
  private int CurrentData = 0;

  RamState(Instance parent, MemContents contents /*, MemListener listener*/) {
    super(contents);
    this.parent = parent;
    // this.listener = listener;
    this.clockState = new ClockState();
    if (parent != null) {
      parent.getAttributeSet().addAttributeWeakListener(null, this);
    }
    // contents.addHexModelWeakListener(null, listener);
  }

  RamState(RamState other) {
    super(other);
    parent = null;
    clockState = new ClockState(other.clockState);
    // listener = other.listener;
    CurrentData = other.CurrentData;
    // getContents().addHexModelWeakListener(null, listener);
  }

  @Override
  void clearContents(InstanceState state) {
    System.out.println("ram clearContents direct");
    contents.clear();
    state.queueForPropagation();
  }

  @Override
  void setContentBytes(InstanceState state, long start, int[] data) {
    System.out.println("ram setContentBytes direct");
    contents.set(start, data);
    state.queueForPropagation();
  }

  @Override
  public void attributeListChanged(AttributeEvent e) { }

  @Override
  public void attributeValueChanged(AttributeEvent e) {
    AttributeSet attrs = e.getSource();
    BitWidth addrBits = attrs.getValue(Mem.ADDR_ATTR);
    BitWidth dataBits = attrs.getValue(Mem.DATA_ATTR);
    getContents().setDimensions(addrBits.getWidth(), dataBits.getWidth());
  }

  @Override
  public RamState duplicateForNewSimulation() {
    return new RamState(this);
  }

  int GetCurrentData() {
    return CurrentData;
  }

  public boolean setClock(Value newClock, Object trigger) {
    return clockState.updateClock(newClock, trigger);
  }

  void SetCurrentData(int data) {
    CurrentData = data;
  }

  void setRam(Instance value) {
    if (parent == value) {
      return;
    }
    if (parent != null) {
      parent.getAttributeSet().removeAttributeWeakListener(null, this);
    }
    parent = value;
    if (value != null) {
      value.getAttributeSet().addAttributeWeakListener(null, this);
    }
  }
  
  @Override
  public boolean simulationReset(CircuitState cs, Component comp) {
    AttributeOption type = comp.getAttributeSet().getValue(RamAttributes.ATTR_TYPE);
    if (type == RamAttributes.VOLATILE) {
      MemContents contents = getContents();
      contents.clear();
      return true; // okay to delete this RamState
    } else {
      return false; // do not delete this RamState
    }
  }

  @Override
  public void simulationCleanup(CircuitState cs, Component comp) {
    Ram.closeHexFrame(this);
  }

}
