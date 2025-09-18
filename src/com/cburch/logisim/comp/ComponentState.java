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

/* Design Notes on CircuitState.setData()/getData() (1 of 2)
 *
 * A ComponentState object can be created by a component within a circuit being
 * simulated, and stashed inside the CircuitState associated with that
 * component. For example, a std/memory/Register will need an object with an
 * int, to keep track of the simulated stored value of the register.
 *
 * Duplication/cloning...
 * Sometimes simulation state needs to be duplicated, e.g. when the user wants
 * to clone a subtree of a simulation. For example, the UI gives a popup dialog
 * about this if you explore into a subtree of a simulation, then try to change
 * the state of the subcomponent pin (the dialog mentions the pin state being
 * "tied" to the parent circuit state). For this reason, ComponentState objects
 * should be able make a copy of themselves: clone() is used for this.
 *
 * - clone() implementations can return any data type, that's allowed by java's
 *   type system.
 *
 * - clone() implementations can leverage java's Cloneable/Object.clone()
 *   system. But this isn't required.
 *
 * - clone() implementations can call a constructor, instead of using java's
 *   Cloneable/Object.clone() system. This is fine. The idea is the returned
 *   object should hold equivalent, but independent, data, so the user sees the
 *   "same" state initially in the duplicated simulation.
 *
 * - clone() implementations can return null, in which case the component will
 *   see null when it later calls CircuitState.getData() in the duplicated
 *   simulation. Some components are okay with this, they just treat it as a new
 *   simulation and create a new state object on demand.
 *
 * - clone() implementations can return any Object whatsoever, CircuitState
 *   doesn't care, so as long as the component knows what to do with that result
 *   when it later calls CircuitState.getObject() in the duplicated simulation.
 *   There are no known components using this feature, though it's conceivable a
 *   component might want to use some kind of sentinel value, or return some
 *   kind of copy-on-demand stub object to avoid duplicating a large object that
 *   might not change much. Rom (and maybe Ram?) comes to mind as a potential
 *   case for this, since it has large but rarely-mutating data.
 *
 * - clone() implementations may `return this`, so long as the component is okay
 *   with having two simulations share the same state object. For example, a
 *   component could use fully immutable state objects, replacing them whenever
 *   the state changes, rather than mutating them. Here, clone() returns the
 *   same object, and the later duplicated simulation will replace the object
 *   when needed with a new, different immutable object. But in this case, it
 *   would make more sense to just *not* implement ComponentState or clone() at
 *   all, and instead pass immutable objects directly to CircuitState.setData().
 *   CircuitState.setData()/getData() fall back to re-using state objects if
 *   they don't implement ComponentState. There are no known components using
 *   either of these features, though there are some clone() implementations
 *   that fall back to `return this` in case of `CloneNotSupportedException`,
 *   but those seem like they might be (a) impossible to reach at runtime, only
 *   needed to satisfy java checked-exception rules, or (b) bugs.
 *
 * See also:
 *   void CircuitState.setData(Component comp, Object data)
 *   Object CircuitState.getData(Component comp)
 *   InstanceData
 *   InstanceDataSingleton
 *
 * note: CircuitState.setData()/getData() can accept any Object, not just
 * ComponentState objects. See design notes in CircuitState.
 * But:
 *  - The data for those calls should *never* be a CircuitState. That would only
 *    be appropriate for a subcircuit component, but that uses a different,
 *    dedicated code path.
 *  - The data for those objects doesn't need to be a CircuitState. Any object
 *    is accepted. And it would even make sense to use an immutable object in
 *    some cases, as noted above. 
 */
public interface ComponentState {
  public Object clone(); // FIXME: time to move away from clone()
}
