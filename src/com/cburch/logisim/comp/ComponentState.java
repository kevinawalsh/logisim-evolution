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

package com.cburch.logisim.comp;

/* Design Notes on CircuitState.setData()/getData() (2 of 5)
 *
 * ComponentState is here only for backwards compatibility. It has been replaced
 * by ComponentData. Wherever possible, don't use this, but instead implement
 * ComponentData, or use the whitelisted immutable data types allowed by
 * CircuitState.setData()/getData().
 *
 * It provides an implementation of ComponentData.duplicateForNewSimulation()
 * that calls the (legacy) clone(), which is partially entangled with java's
 * confusing Object.clone()/Cloneable system.
 *
 * ComponentState implementations must provide a clone() method to help copy the
 * component's simulation state when a simulation is duplicated.
 *
 * - clone() implementations can return any data type, that's allowed by java's
 *   type system.
 *
 * - clone() implementations can leverage java's Cloneable/Object.clone()
 *   system. But this isn't required, and is probably best avoided.
 *
 * - clone() implementations can a constructor, instead of using java's
 *   Cloneable/Object.clone() system. This is probably best. The idea is the
 *   returned object should hold equivalent, but independent, data, so the user
 *   sees the "same" state initially in the duplicated simulation.
 *
 * - clone() implementations can return null, in which case the component will
 *   see null when it later calls CircuitState.getData() in the duplicated
 *   simulation. Some components are okay with this, they just treat it as a new
 *   simulation and create a new state object on demand.
 *
 * - clone() implementations that return an object must return a ComponentData
 *   object. This ensures the result can be stored within the simulation using
 *   CircuitState.setData()/getData(). (Previously, any Object was allowed, and
 *   currently, it would be possible to extend this code to accept the
 *   whitelisted immutable classes allowed CircuitState.setData()/getData(), but
 *   why bother, nobody should use this class anyway.)
 *
 * - clone() implementations may `return this`, so long as the component is okay
 *   with having two simulations share the same state object. This could make
 *   sense for immutable data, but here it would be simpler to just avoid
 *   ComponentState entirely, and just implement ComoponentData directly.
 *
 */
public interface ComponentState extends ComponentData {
  
    // WARNING: clone() must return a ComponentData object, or null.
    // No other value is allowed. The return type of `Object` here is
    // only for backwards-compatiability. All known uses of 
    public Object clone();

  default ComponentData duplicateForNewSimulation() {
      Object dup = clone();
      if (dup instanceof ComponentData)
          return (ComponentData)dup;
      else
          throw new UnsupportedOperationException("ComponentState.clone() implementation is broken");
  }
}
