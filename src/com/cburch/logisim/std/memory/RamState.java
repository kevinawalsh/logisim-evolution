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
// import com.cburch.logisim.std.memory.Mem.MemListener;

public class RamState extends MemState
  implements ComponentData.WithLifetimeTracking, AttributeListener {

  // RamState holds simulation state for Ram, including everything MemState
  // provides, plus clock state, and a reference to the instance (note we are
  // always in a circuit) so we can respond to attribute changes,

  private Instance parent; // also stored in contents
  // private MemListener listener;
  private ClockState clockState;

  RamState(Project proj, Instance inst, int addrBits, int dataBits) { // RamContents contents /*, MemListener listener*/) {
    super(contents);
    contents.setProject(proj);
    contents.setRamInstance(inst);
    this.parent = parent;
    // this.listener = listener;
    this.clockState = new ClockState();
    if (parent != null) {
      parent.getAttributeSet().addAttributeWeakListener(null, this);
    } else {
      System.err.println("ram - missing instance?");
    }
    // contents.addHexModelWeakListener(null, listener);
  }

  private RamState(RamState other) {
    // duplicate: new state does not share our RamContents
    super(other.contents.duplicate(), other);
    parent = null; // instance not known just yet...
    clockState = new ClockState(other.clockState);
    // listener = other.listener;
    // getContents().addHexModelWeakListener(null, listener);
  }

  @Override
  public RamState duplicateForNewSimulation() {
    return new RamState(this);
  }

  // @Override
  // void clearContents(InstanceState state) {
  //   System.out.println("ram clearContents direct");
  //   contents.clear();
  //   state.queueForPropagation();
  // }

  // @Override
  // void setContentBytes(InstanceState state, long start, int[] data) {
  //   System.out.println("ram setContentBytes direct");
  //   contents.set(start, data);
  //   state.queueForPropagation();
  // }

  @Override
  public void attributeListChanged(AttributeEvent e) { }

  @Override
  public void attributeValueChanged(AttributeEvent e) {
    AttributeSet attrs = e.getSource();
    BitWidth addrBits = attrs.getValue(Mem.ADDR_ATTR);
    BitWidth dataBits = attrs.getValue(Mem.DATA_ATTR);
    contents.setDimensions(addrBits.getWidth(), dataBits.getWidth());
  }

  public boolean setClock(Value newClock, Object trigger) {
    return clockState.updateClock(newClock, trigger);
  }

  void setRamInstance(Instance instance) {
    if (parent == value)
      return;
    if (parent != null)
      parent.getAttributeSet().removeAttributeWeakListener(null, this);
    parent = instance;
    contents.setRamInstance(instance);
    if (instance != null)
      instance.getAttributeSet().addAttributeWeakListener(null, this);
  }

  void setProject(Project proj) {
    contents.setProject(proj);
  }
  
  @Override
  public boolean simulationReset(CircuitState cs, Component comp) {
    AttributeOption type = comp.getAttributeSet().getValue(RamAttributes.ATTR_TYPE);
    if (type == RamAttributes.VOLATILE) {
      contents.simulatorClear();
      return true; // okay to delete this RamState
    } else {
      return false; // do not delete this RamState
    }
  }

  @Override
  public void simulationCleanup(CircuitState cs, Component comp) {
    contents.closeHexFrame();
  }

}
