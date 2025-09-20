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

/* Design Notes on CircuitState.setData()/getData() (5 of 5)
 *
 * InstanceDataSingleton was a convenience helper class intended to make it
 * easier to create mutable state for instance-flavored components that have
 * only simple state. But it was always mostly pointless, and is now deprecated.
 * It is no longer used by any known components.
 *
 * If you insist on using this data type... this is designed only for use with
 * instance-flavored components with simple state that can be respresented by a
 * single immutible object, like an Integer for example. InstanceDataSingleton
 * wraps a variable holding the immutible Integer, allowing it to be accessed
 * and mutated with setValue()/getValue(). The pattern of code, which previously
 * was used by many components in std, looked like:
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
 * WARNING: InstanceDataSingleton provides the clone() implementation needed by
 * ComponentState. It makes only a shallow copy, which only makes sense if the
 * value being wrapped is immutable or can otherwise be safely shared between
 * multiple simulations. All known previous uses of InstanceDataSingleton used
 * either Value or Integer. Using InstanceDataSingleton with a mutable object
 * seems like a bad idea, but we have no way to enforce this in the Java type
 * system.
 *
 * All of this is now pointless. Instead, use CircuitState.setData()/getData()
 * directly with any of the whitelisted immutable data types. Or, if you are
 * making your own immutable data types (which aren't whitelisted by
 * CircuitState), then implement ComponentData and provide a simple
 * duplicateForNewSimulation() implementation that just does `return this`. Or,
 * as a last restort, if you have an immutible data type that isn't whitelisted,
 * make your own simple wrapper to hold it, or add it to the whitelist in
 * CircuitState.
 *
 * Performance note: Conceivably, it could be more efficient to use a wrapper
 * class like this, so the CircuitState HashMap is accessed only once, and
 * the wrapper object mutated quickly after that, like:
 *
 *  // Get existing component state data from simulation, if any, or fallback ...
 *  InstanceDataSingleton data = (InstanceDataSingleton)instancestate.getData();
 *  if (data == null)
 *    state.setData(data = new InstanceDataSingleton(Integer.valueOf(0));
 *
 *  // ... later, modify wrapper, no need to access CircuitState hash map again
 *  data.setValue(...);   
 *
 * But there are no known examples of this, so apparently this style was not
 * considered or was not a performance win. In future a simple
 * ComponentData<Value> or ComponentData<Integer> wrapper could be made for this
 * purpose, and it's performance impact measured.
 *  
 *
 */
@Deprecated(since = "5.0.5HC", forRemoval = false)
public final class InstanceDataSingleton implements InstanceData /*, Cloneable */ {
  private Object value; // must be immutable, like Color, String, etc.

  public InstanceDataSingleton(Object value_with_an_immutable_type) {
    this.value = value_with_an_immutable_type;
  }

  @Override
  public InstanceDataSingleton clone() {
    return new InstanceDataSingleton(value);
    // try {
    //   return (InstanceDataSingleton) super.clone();
    // } catch (CloneNotSupportedException e) {
    //   return null;
    // }
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value_with_an_immutable_type) {
    this.value = value_with_an_immutable_type;
  }

}
