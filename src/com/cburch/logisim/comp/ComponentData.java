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

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;

/* Design Notes on CircuitState.setData()/getData() (1 of 5)
 *
 * ComponentData replaces the earlier ComponentState, which had issues.
 * 
 * A ComponentData object can be created by a component within a circuit being
 * simulated, and stashed inside the CircuitState associated with that
 * component. For example, std/io/Tty needs an object with a queue of characters
 * and more, to keep track of the simulated state of the Tty.
 *
 * Note: For components with simple state that can be represented by an
 * immutable object that is simply overwritten as needed, this class isn't
 * necessary. Instead, just use CircuitState.setData()/getData() directly with
 * the immutable objects.
 *
 * Duplication/cloning...
 * Sometimes simulation state needs to be duplicated, e.g. when the user wants
 * to clone a subtree of a simulation. For example, the UI gives a popup dialog
 * about this if you explore into a subtree of a simulation, then try to change
 * the state of the subcomponent pin (the dialog mentions the pin state being
 * "tied" to the parent circuit state). For this reason, ComponentData objects
 * must be able make a copy of themselves: duplicateForNewSimulation() is used
 * for this.
 *
 * Typical implementations use a copy-constructor:
 *
 *   static class MyState implements ComponentData {
 *     MyState(...) {
 *       ... initialze for a new component ...
 *     }
 *     MyState(MyState other) { 
 *       ... initialize from other copy ...
 *     }
 *     @Override
 *     public MyState duplicateForNewSimulation() {
 *       return new MyState(this);
 *     }
 *     ...
 *   }
 *
 * Notes:
 *
 * - duplicateForNewSimulation() implementations can return any data type,
 *   that's allowed by java's type system. Normally, an implementation would
 *   return the same type as itself.
 *
 * - duplicateForNewSimulation() implementations can leverage java's
 *   Cloneable/Object.clone() system. But this isn't required, and is probably
 *   best avoided.
 *
 * - duplicateForNewSimulation() implementations should normally call a
 *   constructor, instead of using java's Cloneable/Object.clone() system. The
 *   idea is the returned object should hold equivalent, but independent, data,
 *   so the user sees the "same" state initially in the duplicated simulation.
 *
 * - duplicateForNewSimulation() implementations can return null, in which case
 *   the component will see null when it later calls CircuitState.getData() in
 *   the duplicated simulation. Some components are okay with this, they just
 *   treat it as a new simulation and create a new state object on demand.
 *
 * - duplicateForNewSimulation() implementations can conceivably return some
 *   kind of sentinel value, or return some kind of copy-on-demand stub object
 *   to avoid duplicating a large object that might not change much. Rom (and
 *   maybe Ram?) comes to mind as a potential case for this, since it has large
 *   but rarely-mutating data.
 *
 * - duplicateForNewSimulation() implementations may `return this`, so long as
 *   the component is okay with having two simulations share the same state
 *   object. For example, a component could use fully immutable state objects,
 *   replacing them whenever the state changes, rather than mutating them. Here,
 *   duplicateForNewSimulation() can return the same object, and the later
 *   duplicated simulation will replace the object when needed with a new,
 *   different immutable object. However, in this case, it makes more sense to
 *   just call CircuitState.setData()/getData() directly with the immutible
 *   objects, if possible, and don't bother with ComponentData at all.
 *
 * Note: For now, we require that duplicateForNewSimulation() returns a
 * ComponentData object. It is conceivable one might want to use ComponentData
 * with an duplicateForNewSimulation() implementation that returns some
 * immutable object like an Integer, maybe as a sentinel of some sort.
 * CircuitState would probably be okay with this kind of
 * switching-types-during-duplication nonsense, but let's not complicate things. 
 *
 * Note: CircuitState doesn't move ComponentData between CircuitState objects.
 * So, for the most part, a ComponentData inserted into one CircuitState will
 * never appear in some other CircuitState. The only exceptions would be if
 * some component deliberately puts an object into multiple CircuitStates, which
 * seems like a bad idea and isn't normally done.
 *
 * See also:
 *   void CircuitState.setData(Component comp, ComponentData data)
 *   ComponentData CircuitState.getData(Component comp)
 */
public interface ComponentData {

  public ComponentData duplicateForNewSimulation();

  // Some components need help tracking the lifetime of their associated
  // simulation data. SerialIn and HttpIn, for example, need to close ports or
  // stop worker threads, and Ram may need to close hex editor windows. The data
  // for these components should implement ComponentData.WithLifetimeTracking.
  // For such objects, CircuitState will notify the object of changes to the
  // status of the simulation:
  public interface WithLifetimeTracking extends ComponentData {

    // simulationActivating() is called when a simulation transitions to active
    // status, e.g. selected within the UI and receiving clock ticks.
    public default void simulationActivating(CircuitState cs, Component comp) { };

    // simulationDeactivating() is called when a simulation transitions to inactive
    // status, e.g. no longer selected within the UI or receiving clock ticks.
    public default void simulationDeactivating(CircuitState cs, Component comp) { }

    // simulationReset() is called when a simulation is being reset, e.g. from
    // menu item action. If this returns true, the component data will be
    // removed from the CircuitState immediately after this call, so this is a
    // last chance to clean up.  If this returns false, the component data will
    // be preserved across resets. Normally both cleanup and reset would do the
    // same thing, and this will return true. But Ram is unusual: for
    // NONVOLATILE, the state persists across resets.
    public default boolean simulationReset(CircuitState cs, Component comp) {
      simulationCleanup(cs, comp);
      return true;
    };

    // simulationCleanup() is called when simulation data has become defunct,
    // e.g. the entire simulation was deleted by the user and will no longer be
    // visible in the UI, or the component (or subcircuit containing the
    // component) was deleted from a circuit. This is a last chance to clean up
    // resources associated with this ComponentData, no further notifications
    // will be provided after this is called.
    public default void simulationCleanup(CircuitState cs, Component comp) { }
  }

}
