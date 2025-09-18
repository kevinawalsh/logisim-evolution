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

package com.cburch.logisim.instance;

/* Design Notes on CircuitState.setData()/getData() (4 of 4)
 *
 * InstanceDataSingleton is a convenience helper class intended to make it
 * easier to create mutable state for instance-flavored components that have
 * only simple state. But it seems mostly pointless.
 *
 * Some instance-flavored components have simple state, such as std/io/Led which
 * holds just a single Value, or std/io/RGBLed which holds just an Integer. In
 * both cases, the data objects (Value, or Integer) are immutable, and are
 * replaced rather than being mutated. This data can be wrapped in a
 * InstanceDataSingleton, to make it mutable, using a pattern like:
 *
 *  // Get existing component state data from simulation, if any, or fallback ...
 *  InstanceDataSingleton data = (InstanceDataSingleton)instancestate.getData();
 *  Integer value = data == null ? Integer.valueOf(0) : (Integer)data.getValue();
 *
 *  // Mutate component state data within simulation, if any, or fallback ...
 *  Integer value = ...
 *  InstanceDataSingleton data = (InstanceDataSingleton)instancestate.getData();
 *  if (data == null) {
 *    state.setData(new InstanceDataSingleton(value));
 *  } else {
 *    data.setValue(value);
 *  }
 *
 * InstanceDataSingleton provides a clone() implementation, as needed by
 * ComponentData, but it makes only a shallow copy, which only makes sense if
 * the value being wrapped is immutable or can otherwise be safely shared
 * between multiple simulations. All known uses of InstanceDataSingleton use
 * either Value or Integer.
 *
 * It seems that all of this is pointless. The above code using
 * InstanceDataSingleton could be replaced by simpler code directly using the
 * underlying CircuitState, like this:
 *
 *  // Get existing component state data from simulation, if any, or fallback ...
 *  Integer value = (Integer)circuitstate.getData(comp);
 *  if (value == null) value = Integer.valueOf(0);
 *
 *  // Overwrite component state data within simulation...
 *  Integer value = ...
 *  circuitstate.setData(comp, value);
 *
 * Note: The variables needed here, the Component and the CircuitState, can be
 * obtained from InstanceState, since InstanceState is just a thin wrapper
 * around those variables. And if the (pointless) restriction in
 * Instance.setData()/getData() to use InstanceData were eliminated, then the
 * InstanceState wrappers could be used directly:
 *
 *  // Get existing component state data from simulation, if any, or fallback ...
 *  Integer value = (Integer)instancestate.getData();
 *  if (value == null) value = Integer.valueOf(0);
 *
 *  // Overwrite component state data within simulation...
 *  Integer value = ...
 *  instancestate.setData(value);
 *
 * TODO: make this final, see if everything still compiles, to ensure nobody
 * extends this.
 * TODO: change Object to just accept Integer or Value, see if it compiles, to
 * confirm above notes.
 * TODO: make this generic? Or don't bother, just eliminate it, it's pointless.
 */
public class InstanceDataSingleton implements InstanceData, Cloneable {
  private Object value;

  public InstanceDataSingleton(Object value) {
    this.value = value;
  }

  @Override
  public InstanceDataSingleton clone() {
    try {
      return (InstanceDataSingleton) super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }
}
