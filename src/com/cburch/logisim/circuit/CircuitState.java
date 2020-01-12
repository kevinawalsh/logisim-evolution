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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// import com.cburch.logisim.circuit.Propagator.DrivenValue;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentState;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.RamState;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Pin;

// CircuitState holds the simulation state of a Circuit (or Subcircuit), i.e.
// the values being carried along all wires and buses, along with the
// InstanceData for all components embedded in the circuit. Most of the
// dynamically-computed data is actually in CircuitWires. In here there is
// mostly just a few pointers to other data structures and the dirty lists
// (lists of locations or components that need to be recomputed).
//
// Note: Each CircuitState belongs to (at most) one Propagator. Some of the
// members in here more properly belong to Propagator (or, vice versa, some of
// the functionality in Propagator could equally well be in here.
public class CircuitState implements InstanceData {

  private class MyCircuitListener implements CircuitListener {
    public void circuitChanged(CircuitEvent event) {
      int action = event.getAction();

      /* Component was added */
      if (action == CircuitEvent.ACTION_ADD) {
        // Nothing to do: CircuitWires.BundleMap will be voided, causing
        // everything to be marked dirty.
        Component comp = (Component) event.getData();
        // DEBUG: System.out.println("added comp " + comp);
        // if (comp instanceof Wire) {
        //   Wire w = (Wire) comp;
        //   markPointAsDirty(w.getEnd0(), null);
        //   markPointAsDirty(w.getEnd1(), null);
        // } else {
        //   markComponentAsDirty(comp);
        // }
      }

      /* Component was removed */
      else if (action == CircuitEvent.ACTION_REMOVE) {
        Component comp = (Component) event.getData();
        if (comp == temporaryClock)
          temporaryClock = null;
        if (comp.getFactory() instanceof Clock) {
          knownClocks = false; // just in case, will be recomputed by simulator
        }

        if (comp.getFactory() instanceof SubcircuitFactory) {
          knownClocks = false; // just in case, will be recomputed by simulator
          // disconnect from tree
          CircuitState substate = (CircuitState) getData(comp);
          if (substate != null && substate.parentComp == comp) {
            synchronized (dirtyLock) {
              substates.remove(substate);
              substatesDirty = true;
            }
            substate.parentState = null;
            substate.parentComp = null;
          }
        }

        if (comp instanceof Wire) {
          // Nothing to do: CircuitWires.BundleMap will be voided, causing
          // everything to be marked dirty.
          // Wire w = (Wire) comp;
          // markPointAsDirty(w.getEnd0(), null);
          // markPointAsDirty(w.getEnd1(), null);
          // DEBUG: System.out.println("removed wire " + comp);
        } else {
          // Nothing else to do: CircuitWires.BundleMap will be voided, causing
          // everything to be marked dirty.
          // Propagator.checkComponentEnds(CircuitState.this, comp);
          // DEBUG: System.out.println("removed comp " + comp);
          synchronized (dirtyLock) {
            // dirtyComponents.remove(comp);
            while (dirtyComponents.remove(comp))
              ;
          }
        }
      }

      /* Whole circuit was cleared */
      else if (action == CircuitEvent.ACTION_CLEAR) {
        temporaryClock = null;
        knownClocks = false;
        wireData = null;
        componentData.clear();
        synchronized (valuesLock) {
          slowpath_values.clear(); // slow path
          clearFastpathGrid(); // fast path
        }
        synchronized (dirtyLock) {
          dirtyComponents.clear();
          dirtyPoints.clear();
          // dirtyPointVals.clear();
          substates.clear();
          substatesWorking = new CircuitState[0];
          substatesDirty = true;
        }
        // slowpath_drivers.clear();
      }

      /* Component ends changed */
      else if (action == CircuitEvent.ACTION_INVALIDATE) {
        Component comp = (Component) event.getData();
        markComponentAsDirty(comp);
        // If simulator is in single step mode, we want to hilight the
        // invalidated components (which are likely Pins, Buttons, or other
        // inputs), so pass this component to the simulator for display.
        proj.getSimulator().addPendingInput(CircuitState.this, comp);
      } else if (action == CircuitEvent.TRANSACTION_DONE) {
        ReplacementMap map = event.getResult().getReplacementMap(circuit);
        if (map == null)
          return;
        for (Component comp : map.getRemovals()) {
          Object compState = componentData.remove(comp);
          if (compState == null)
            continue;
          Class<?> compFactory = comp.getFactory().getClass();
          boolean found = false;
          for (Component repl : map.getReplacementsFor(comp)) {
            if (repl.getFactory().getClass() == compFactory) {
              found = true;
              replaceData(repl, compState);
              break;
            }
          }
          if (!found && compState instanceof RamState) {
            Ram.closeHexFrame((RamState)compState);
          }
          if (!found && compState instanceof CircuitState) {
            CircuitState sub = (CircuitState) compState;
            sub.parentState = null;
            synchronized (dirtyLock) {
              substates.remove(sub);
              substatesDirty = true;
            }
          }
        }
      }
    }
  }

  private MyCircuitListener myCircuitListener = new MyCircuitListener();
  private Propagator base; // inherited from base of tree of CircuitStates
  private Project proj; // project containing this circuit
  private Circuit circuit; // circuit being simulated

  private CircuitState parentState; // parent in tree of CircuitStates
  private Component parentComp; // subcircuit component containing this state

  private CircuitWires.State wireData;
  private HashMap<Component, Object> componentData = new HashMap<>();
  
  private static final int FASTPATH_GRID_WIDTH = 200;
  private static final int FASTPATH_GRID_HEIGHT = 200;

  // slowpath_values and fastpath_values store values resulting from propagation
  // *within* this circuit, i.e. the outputs of componnents in this circuit
  // together with the values carried on wires and buses in this circuit. When
  // components embedded in this circuit are called upon to re-calculate /
  // propagate, the components will call getValue() to pick out values from
  // these data structures. These are the values you would see if you stick a
  // probe at some location on the circuit sheet.
  Map<Location, Value> slowpath_values = new HashMap<>(); // protected by valuesLock
  Value[][] fastpath_values = new Value[FASTPATH_GRID_HEIGHT][FASTPATH_GRID_WIDTH]; // protected by valuesLock

  // slowpath_drivers and fastpass_drivers store {component,value} pairs for each
  // component that is currently emitting a value *into* this circuit, i.e.
  // the values that sources/drivers are putting out and that will ultimately
  // get propagated along wires and busses to other points in the circuit. These
  // are essentially the values you would see at the outputs of components if
  // you somehow froze all their inputs then removed all wires, splitters,
  // tunnels, and other connectivity within the circuit so that each component's
  // outputs could be observed in isolation.
  // HashMap<Location, DrivenValue> slowpath_drivers = new HashMap<>(); // used by Propagator, protected by valuesLock
  // DrivenValue[][] fastpath_drivers = new DrivenValue[FASTPATH_GRID_HEIGHT][FASTPATH_GRID_WIDTH]; // used by Propagator, protected by valuesLock

  Object valuesLock = new Object();

  // HashSet<Propagator.ComponentPoint> visited = new HashSet<>(); // used by Propagator
  // int visitedNonce; // used by Propagator;
  // The visited member holds the set of every [component,loc] pair (where the
  // component is among those in this circuit) that has been visited during the
  // current iteration of Propagator.stepInternal().

  // private CopyOnWriteArraySet<Component> dirtyComponents = new CopyOnWriteArraySet<>();
  // private HashSet<Component> dirtyComponents = new HashSet<>(); // protected by dirtyLock
  private ArrayList<Component> dirtyComponents = new ArrayList<>(); // protected by dirtyLock
  // private CopyOnWriteArraySet<Location> dirtyPoints = new CopyOnWriteArraySet<>();
  // private HashSet<Location> dirtyPoints = new HashSet<>();
  // private ArrayList<Location> dirtyPoints = new ArrayList<>(); // protected by dirtyLock
  // private ArrayList<Value> dirtyPointVals = new ArrayList<>(); // protected by dirtyLock
  private ArrayList<Propagator.SimulatorEvent> dirtyPoints = new ArrayList<>(); // protected by dirtyLock
  private HashSet<CircuitState> substates = new HashSet<>(); // protected by dirtyLock
  private Object dirtyLock = new Object();


  private static int lastId = 0;
  private int id = lastId++;

  private CircuitState(Project proj, Circuit circuit, Propagator prop) {
    this.proj = proj;
    this.circuit = circuit;
    this.base = prop != null ? prop : new Propagator(this);
    circuit.addCircuitWeakListener(null, myCircuitListener);
    markAllComponentsDirty();
  }

  @Override
  public CircuitState clone() {
    try { throw new Exception("*** why? ***"); }
    catch (Exception e) { e.printStackTrace(); }
    return cloneAsNewRootState();
  }

  public static CircuitState createRootState(Project proj, Circuit circuit) {
    return new CircuitState(proj, circuit, null /* make new Propagator */);
  }

  public CircuitState cloneAsNewRootState() {
    CircuitState ret = new CircuitState(proj, circuit, null /* make new Propatator */);
    ret.copyFrom(this);
    ret.parentComp = null; // detatch from old parent component and state
    ret.parentState = null;
    return ret;
  }

  private void copyFrom(CircuitState src) {
    // this.base = ... DO NOT COPY propagator, as this circuit has its own already.
		// DEBUG: System.out.printf("this %s is copying from %s\n", this, src);
    this.parentComp = src.parentComp;
    this.parentState = src.parentState;
    HashMap<CircuitState, CircuitState> substateData = new HashMap<>();
    this.substates = new HashSet<CircuitState>();
    synchronized (src.dirtyLock) {
      // note: we don't bother with our this.dirtyLock here: it isn't needed
      // (b/c no other threads have a reference to this yet), and to avoid the
      // possibility of deadlock (though that shouldn't happen either since no
      // other threads have references to this yet).
      for (CircuitState oldSub : src.substates) {
        CircuitState newSub = new CircuitState(src.proj, oldSub.circuit, this.base);
        newSub.copyFrom(oldSub);
        newSub.parentState = this;
        this.substates.add(newSub);
        this.substatesDirty = true;
        substateData.put(oldSub, newSub);
      }
    }
    for (Component key : src.componentData.keySet()) {
      Object oldValue = src.componentData.get(key);
      if (oldValue instanceof CircuitState) {
        Object newValue = substateData.get(oldValue);
        if (newValue != null)
          this.componentData.put(key, newValue);
        else
          this.componentData.remove(key);
      } else {
        Object newValue;
        if (oldValue instanceof ComponentState) {
          newValue = ((ComponentState) oldValue).clone();
        } else {
          newValue = oldValue;
        }
        this.componentData.put(key, newValue);
      }
    }
    // Propagator.copyDrivenValues(this, src);
    // note: we don't bother with our this.valuesLock here: it isn't needed
    // (b/c no other threads have a reference to this yet), and to avoid the
    // possibility of deadlock (though that shouldn't happen either since no
    // other threads have references to this yet).
    this.slowpath_values.clear(); // slow path
    synchronized (src.valuesLock) {
      this.slowpath_values.putAll(src.slowpath_values); // slow path
      for(int y = 0; y < FASTPATH_GRID_HEIGHT; y++) { // fast path
        System.arraycopy(src.fastpath_values[y], 0,
            this.fastpath_values[y], 0, FASTPATH_GRID_WIDTH);
      }
    }
    synchronized(src.dirtyLock) {
      // note: we don't bother with our this.dirtyLock here: it isn't needed
      // (b/c no other threads have a reference to this yet), and to avoid the
      // possibility of deadlock (though that shouldn't happen either since no
      // other threads have references to this yet).
      this.dirtyComponents.addAll(src.dirtyComponents);
      this.dirtyPoints.addAll(src.dirtyPoints);
      // this.dirtyPointVals.addAll(src.dirtyPointVals);
    }
    if (src.wireData != null) {
      this.wireData = circuit.wires.newState(this); // all buses will be marked as dirty
      // this.wireData = (CircuitWires.State) src.wireData.clone();
    }
  }

  public void drawOscillatingPoints(ComponentDrawContext context) {
    base.drawOscillatingPoints(context);
  }

  public Circuit getCircuit() {
    return circuit;
  }

  public Object getData(Component comp) {
    return componentData.get(comp);
  }

  private InstanceStateImpl reusableInstanceState = new InstanceStateImpl(this, null);

  // FIXME: wtf?
  public InstanceState getInstanceState(Component comp) {
    Object factory = comp.getFactory();
    if (factory instanceof InstanceFactory) {
      if (comp != ((InstanceComponent)comp).getInstance().getComponent()) 
        throw new IllegalStateException("instanceComponent.getInstance().getComponent() is wrong");
      // return ((InstanceFactory) factory).createInstanceState(this, comp);
      reusableInstanceState.repurpose(this, comp);
      return reusableInstanceState;
    } else {
      throw new RuntimeException("getInstanceState requires instance component");
    }
  }

  // FIXME: wtf?
  public InstanceState getInstanceState(Instance instance) {
    Object factory = instance.getFactory();
    if (factory instanceof InstanceFactory) {
      // return ((InstanceFactory) factory).createInstanceState(this, instance);
      reusableInstanceState.repurpose(this, instance.getComponent());
      return reusableInstanceState;
    } else {
      throw new RuntimeException("getInstanceState requires instance component");
    }
  }

  public CircuitState getParentState() {
    return parentState;
  }

  public CircuitState getAncestorState() {
    CircuitState ancestor = this;
    while (ancestor.parentState != null)
      ancestor = ancestor.parentState;
    return ancestor;
  }

  public Project getProject() {
    return proj;
  }

  public Propagator getPropagator() {
    return base;
  }

  Component getSubcircuit() {
    return parentComp;
  }

  public Set<CircuitState> getSubstates() { // returns Set of CircuitStates
    return substates;
  }

  public Value getValue(Location p) {
    Value v = null;
    if (p.x >= 0 && p.y >= 0
        && p.x % 10 == 0 && p.y % 10 == 0
        && p.x < FASTPATH_GRID_WIDTH*10
        && p.y < FASTPATH_GRID_HEIGHT*10) {
      // fast path
      int x = p.x/10;
      int y = p.y/10;
      synchronized (valuesLock) {
        v = fastpath_values[y][x];
      }
    } else {
      // slow path
      synchronized (valuesLock) {
        v = slowpath_values.get(p);
      }
    }
    if (v != null)
      return v;
    v = CircuitWires.getBusValue(this, p);
    if (v != null)
      return v;
    return Value.createUnknown(circuit.getWidth(p));
  }

  CircuitWires.State getWireData() {
    return wireData;
  }

  public boolean isSubstate() {
    return parentState != null;
  }

  private void markAllComponentsDirty() {
    synchronized (dirtyLock) {
      dirtyComponents.addAll(circuit.getNonWires());
    }
  }

  public void markComponentAsDirty(Component comp) {
    synchronized (dirtyLock) {
      dirtyComponents.add(comp);
    }
  }

  public void markComponentsDirty(Collection<Component> comps) {
    synchronized (dirtyLock) {
      dirtyComponents.addAll(comps);
    }
  }

  void markPointAsDirty(Propagator.SimulatorEvent ev) {
    synchronized(dirtyLock) {
      dirtyPoints.add(ev);
    }
  }

  // DEBUG: private void dumpDirty() {
  // DEBUG:   synchronized(dirtyLock) {
  // DEBUG:     boolean updated = false;
  // DEBUG:     if (substatesDirty) { // caution: maybe not a good idea here in debugging
  // DEBUG:       substatesDirty = false;
  // DEBUG:       substatesWorking = substates.toArray(substatesWorking);
  // DEBUG:       updated = true;
	// DEBUG: 		}
  // DEBUG:     System.out.printf("There are %d dirty components, %d working substates%s.\n",
  // DEBUG:         dirtyComponentsWorking.size(),
  // DEBUG:           substatesWorking.length,
  // DEBUG:           updated ? " (just updated list)" :"");
  // DEBUG:     for (CircuitState cs : substatesWorking)
  // DEBUG:       System.out.printf("  substate %s\n", cs);
  // DEBUG:     for (Component c : dirtyComponentsWorking)
  // DEBUG:       System.out.printf("  comp %s with state %s\n", c, getData(c));
  // DEBUG:   }
  // DEBUG: }

  ArrayList<Component> dirtyComponentsWorking = new ArrayList<>();
  // DEBUG: void processDirtyComponents() { processDirtyComponents("-="); }
  void processDirtyComponents(/* DEBUG: String tab */) {
    // DEBUG: System.out.printf(tab+" Start of processDirtyComponents(%s)\n", this);
    // DEBUG: System.out.printf(tab+" NOTE: parentState = %s\n", parentState);
    if (!dirtyComponentsWorking.isEmpty())
      throw new IllegalStateException("INTERNAL ERROR: dirtyComponentsWorking not empty");
    synchronized (dirtyLock) {
      ArrayList<Component> other = dirtyComponents;
      dirtyComponents = dirtyComponentsWorking; // dirtyComponents is now empty
      dirtyComponentsWorking = other; // working set is now ready to process
      if (substatesDirty) {
        substatesDirty = false;
        substatesWorking = substates.toArray(substatesWorking);
			}
      // DEBUG: dumpDirty();
    }

    // DEBUG: boolean finished = false, progress = false;
    try { // comp.propagate() can fail if external (or std) library is buggy
      for (Component comp : dirtyComponentsWorking) {
        // DEBUG: progress = true;
				// DEBUG: System.out.printf("Propagating (from %s) for dirty component %s\n", this, comp);
        comp.propagate(this);
        // pin values also get propagated to parent state
        if (comp.getFactory() instanceof Pin && parentState != null)
          parentComp.propagate(parentState);
      }
      // DEBUG: finished = true;
    } finally {
      // DEBUG: if (!finished)
      // DEBUG:   System.out.printf(tab+" ERROR in processDirtyComponents(%s)\n", this);
      dirtyComponentsWorking.clear();
    }

    // DEBUG: if (progress)
    // DEBUG:   dumpDirty();

    // DEBUG: boolean moreprogress = false;
    for (CircuitState substate : substatesWorking) {
      // DEBUG: moreprogress = true;
			// DEBUG: System.out.printf("Recurse down for substate %s\n", substate);
      if (substate == null)
        break;
      // DEBUG: substate.processDirtyComponents(tab+"==");
      substate.processDirtyComponents();
			// DEBUG: System.out.printf("Done recurse for substate %s\n", substate);
    }

    // DEBUG: if (moreprogress)
    // DEBUG:   dumpDirty();
    
    // DEBUG: System.out.printf(tab+" End of processDirtyComponents(%s)\n", this);
  }

  // private ArrayList<Location> dirtyPointsWorking = new ArrayList<>();
  // private ArrayList<Value> dirtyPointValsWorking = new ArrayList<>();
  private ArrayList<Propagator.SimulatorEvent> dirtyPointsWorking = new ArrayList<>();
  private CircuitState[] substatesWorking = new CircuitState[0];
  private boolean substatesDirty = true;
  void processDirtyPoints() {
    if (!dirtyPointsWorking.isEmpty())
      throw new IllegalStateException("INTERNAL ERROR: dirtyPointsWorking not empty");
    synchronized (dirtyLock) {
      ArrayList<Propagator.SimulatorEvent> other = dirtyPoints;
      // ArrayList<Value> otherVals = dirtyPointVals;
      dirtyPoints = dirtyPointsWorking; // dirtyPoints is now empty
      // dirtyPointVals = dirtyPointValsWorking; // dirtyPointVals is now empty
      dirtyPointsWorking = other; // working set is now ready to process
      // dirtyPointValsWorking = otherVals; // working set is now ready to process
      if (substatesDirty) {
        substatesDirty = false;
        substatesWorking = substates.toArray(substatesWorking);
      }
    }
    // Note: When a new wire map is created (because wires or splitters have
    // changed, for example), we need to mark all the splitter locations as
    // dirty. This used to be handled here by detecting when the map was voided,
    // and explicitly marking all the splitter locations as dirty. But We can't
    // reliably touch circuit.wires.points.getAllLocations(), because we are
    // on the simulator thread here, and the UI/AWT thread owns that data
    // structure. So the hack below just tried a few times hoping to not get a
    // run-time exception. Instead, we now put the splitter location list in
    // the wire map itself when it is created (which is done by CircuitWires
    // carefully in a thread-safe way).
    //
    // if (circuit.wires.isMapVoided()) {
    //   // Note: this is a stopgap hack until we figure out the cause of the
    //   // concurrent modification exception.
    //   for (int i = 3; i >= 0; i--) {
    //     try {
    //       dirtyPointsWorking.addAll(circuit.wires.points.getAllLocations());
    //       break;
    //     } catch (ConcurrentModificationException e) {
    //       System.out.printf("warning: concurrent exception upon voided map (tries left %d)\n", i);
    //       // try again...
    //       try {
    //         Thread.sleep(1);
    //       } catch (InterruptedException e2) {
    //         // Yes, swallow the interrupt -- if simulator thread is interrupted
    //         // while it is in here, we want to keep going. The simulator thread
    //         // uses interrupts only for cancelling its own sleep/wait calls, not
    //         // this sleep call.
    //       }
    //       if (i == 0)
    //         e.printStackTrace();
    //     }
    //   }
    // }
    // if (!dirtyPointsWorking.isEmpty()) {
      // circuit.wires.propagate(this, dirtyPointsWorking, dirtyPointValsWorking);
      circuit.wires.propagate(this, dirtyPointsWorking);
      dirtyPointsWorking.clear();
      // dirtyPointValsWorking.clear();
    // }

    for (CircuitState substate : substatesWorking) {
      if (substate == null)
        break;
      substate.processDirtyPoints();
    }
  }

  public void reset() {
    temporaryClock = null;
    wireData = null;
    for (Iterator<Component> it = componentData.keySet().iterator(); it.hasNext();) {
      Component comp = it.next();
      if (comp.getFactory() instanceof Ram) {
        Ram ram = (Ram)comp.getFactory();
        boolean remove = ram.reset(this, Instance.getInstanceFor(comp));
        if (remove)
          it.remove();
      } else if (!(comp.getFactory() instanceof SubcircuitFactory)) {
        it.remove();
      }
    }
    synchronized (valuesLock) {
      slowpath_values.clear(); // slow path
      clearFastpathGrid(); // fast path
    }
    synchronized (dirtyLock) {
      dirtyComponents.clear();
      dirtyPoints.clear();
      // dirtyPointVals.clear();
      for (CircuitState sub : substates)
        sub.reset();
    }
    // slowpath_drivers.clear();
    markAllComponentsDirty();

  }

  public CircuitState createCircuitSubstateFor(Component comp, Circuit circ) {
      CircuitState oldState = (CircuitState)componentData.get(comp);
      if (oldState != null && oldState.parentComp == comp) {
        // fixme: Does this ever happen?
        System.out.println("fixme: removed stale circuitstate... should never happen");
        System.out.printf("this = %s with parentComp %s \n", this, this.parentComp);
        System.out.printf("comp = %s for circuit %s\n", comp, circ);
        System.out.printf("oldState = %s with parentComp %s\n", oldState, oldState.parentComp);
        Thread.dumpStack();
        synchronized(dirtyLock) {
          substates.remove(oldState);
          substatesDirty = true;
        }
        oldState.parentState = null;
        oldState.parentComp = null;
      }
      CircuitState newState = new CircuitState(proj, circ, base);
      synchronized(dirtyLock) {
        substates.add(newState);
        substatesDirty = true;
      }
      newState.parentState = this;
      newState.parentComp = comp;
      componentData.put(comp, newState);
      return newState;
  }
  
	private void replaceData(Component comp, Object data) {
		// This happens when subcirc is copy/paste/moved, which causes a new
		// component to be created, and we want to transfer the now-defunct
		// component's state over to the newly-created copmonent.
		// If comp is a subcircuit, the data will be a CircuitState.
		// Otherwise data might be a RamState, or some other built-in component state.
    if (data instanceof CircuitState) {
      CircuitState sub = (CircuitState)data;
			// DEBUG: System.out.printf("comp is %s\n", comp);
			// DEBUG: System.out.printf("with old data %s\n", getData(comp));
			// DEBUG: if (getData(comp) instanceof CircuitState)
			// DEBUG:  System.out.printf("        new data parent %s\n", ((CircuitState)getData(comp)).parentComp);
			// DEBUG: System.out.printf("setting new data %s\n", data);
			// DEBUG: System.out.printf("        new data parent %s\n", sub.parentComp);
      // data was already removed from componentData[orig].
      // need to register it now under componentdata[comp], done below.
      // also need to set parentcomp
      // but don't need to add to substates, b/c it should already be there
      sub.parentComp = comp;
			CircuitState old = (CircuitState)componentData.put(comp, data);
			synchronized (dirtyLock) {
				// DEBUG: System.out.println("removing old substate " + old);
				if (old != null) {
					substates.remove(old);
          old.parentState = null;
        }
				// DEBUG: System.out.println("adding new substate " + sub);
        sub.parentState = this;
				substates.add(sub);
				substatesDirty = true;
				dirtyComponents.add(comp);
			}
    } else {
			componentData.put(comp, data);
		}
  }

  public void setData(Component comp, Object data) {
    if (data instanceof CircuitState) {
      // fixme: should never happen?
      System.out.println("fixme: setData with circuitstate... should never happen");
			Thread.dumpStack();
      ((CircuitState)data).parentComp = comp;
    }
    componentData.put(comp, data);
  }

  public void setValue(Location pt, Value val, Component cause, int delay) {
    base.setValue(this, pt, val, cause, delay);
  }

  private void clearFastpathGrid() { // precondition: valuesLock held
    for (int y = 0; y < FASTPATH_GRID_HEIGHT; y++)
      for (int x = 0; x < FASTPATH_GRID_WIDTH; x++)
        fastpath_values[y][x] = null;
  }

  // for CircuitWires - to set value at point
  void setValueByWire(Value v, Location[] points, CircuitWires.BusConnection[] connections) {
    for (Location p : points) {
      if (p.x >= 0 && p.y >= 0
          && p.x % 10 == 0 && p.y % 10 == 0
          && p.x < FASTPATH_GRID_WIDTH*10
          && p.y < FASTPATH_GRID_HEIGHT*10) {
        synchronized (valuesLock) {
          fastpath(p, v);
        }
      } else {
        synchronized (valuesLock) {
          slowpath(p, v);
        }
      }
      base.locationTouched(this, p);
    }
    for (CircuitWires.BusConnection bc : connections) {
      if (bc.isSink || (bc.isBidirectional && !Value.equal(v, bc.drivenValue)))
        markComponentAsDirty(bc.component);
    }
  }

  void clearValuesByWire() {
    synchronized (valuesLock) {
      slowpath_values.clear(); // slow path
      clearFastpathGrid(); // fast path
    }
  }

  // // for CircuitWires - to set value at point where there is no bus, just a
  // // bunch of components
  // void setValueByWire(Value v, Location p) {
  //   boolean changed;
  //   if (p.x >= 0 && p.y >= 0
  //       && p.x % 10 == 0 && p.y % 10 == 0
  //       && p.x < FASTPATH_GRID_WIDTH*10
  //       && p.y < FASTPATH_GRID_HEIGHT*10) {
  //     synchronized (valuesLock) {
  //       changed = fastpath(p, v);
  //     }
  //   } else {
  //     synchronized (valuesLock) {
  //       changed = slowpath(p, v);
  //     }
  //   }
  //   if (changed)
  //     markDirtyComponentsAt(p);
  // }

  private boolean fastpath(Location p, Value v) { // precondition: valuesLock held
    int x = p.x/10;
    int y = p.y/10;
    if (v == Value.NIL) {
      if (fastpath_values[y][x] != null) {
        fastpath_values[y][x] = null;
        return true;
      } else {
        return false;
      }
    } else {
      if (!v.equals(fastpath_values[y][x])) {
        fastpath_values[y][x] = v;
        return true;
      } else {
        return false;
      }
    }
  }
  
  private boolean slowpath(Location p, Value v) { // precondition: valuesLock held
    if (v == Value.NIL) {
      Object old = slowpath_values.remove(p);
      return (old != null && old != Value.NIL);
    } else {
      Object old = slowpath_values.put(p, v);
      return !v.equals(old);
    }
  }

  // private void markDirtyComponentsAt(Location p) {
  //   boolean found = false;
  //   for (Component comp : circuit.getComponents(p)) {
  //     if (!(comp instanceof Wire) && !(comp instanceof Splitter)) {
  //       found = true;
  //       markComponentAsDirty(comp);
  //     }
  //   }
  //   // NOTE: this will cause a double-propagation on components
  //   // whose outputs have just changed.
  //   // FIXME: huh?
  //   if (found)
  //     base.locationTouched(this, p);
  // }

  // private void markDirtyComponents(Location p, Component[] affected) {
  //   for (Component comp : affected)
  //     markComponentAsDirty(comp);
  //   // NOTE: this will cause a double-propagation on components
  //   // whose outputs have just changed.
  //   // FIXME: huh?
  //   if (affected.length > 0)
  //     base.locationTouched(this, p);
  // }

  void setWireData(CircuitWires.State data) {
    wireData = data;
  }

  boolean toggleClocks(int ticks) {
    boolean hasClocks = false;

    if (temporaryClock != null)
      hasClocks |= temporaryClockValidateOrTick(ticks);

    for (Component clock : circuit.getClocks()) {
      hasClocks = true;
      boolean dirty = Clock.tick(this, ticks, clock);
      if (dirty) {
        markComponentAsDirty(clock);
        // If simulator is in single step mode, we want to hilight the
        // invalidated components (which are likely Pins, Buttons, or other
        // inputs), so pass this component to the simulator for display.
        proj.getSimulator().addPendingInput(this, clock);
      }
    }

    synchronized (dirtyLock) {
      if (substatesDirty) {
        substatesDirty = false;
        substatesWorking = substates.toArray(substatesWorking);
      }
    }
    for (CircuitState substate : substatesWorking) {
      if (substate == null)
        break;
      hasClocks |= substate.toggleClocks(ticks);
    }

    return hasClocks;
  }

  private boolean temporaryClockValidateOrTick(int ticks) {
    // temporaryClock.getFactory() will be Pin, normally a 1 bit input
    Pin pin;
    try {
      pin = (Pin)temporaryClock.getFactory();
    } catch (ClassCastException e) {
      temporaryClock = null;
      return false;
    }
    Instance i = Instance.getInstanceFor(temporaryClock);
    if (i == null || !pin.isInputPin(i) || pin.getWidth(i).getWidth() != 1) {
      temporaryClock = null;
      return false;
    }
    if (ticks >= 0) {
      InstanceState state = getInstanceState(i);
      Value vOld = pin.getValue(state);
      Value vNew = ticks%2==0 ? Value.FALSE : Value.TRUE;
      if (!vNew.equals(vOld)) {
        pin.driveInputPin(state, vNew);
        // state.fireInvalidated();
        markComponentAsDirty(temporaryClock);
        // If simulator is in single step mode, we want to hilight the
        // invalidated components (which are likely Pins, Buttons, or other
        // inputs), so pass this component to the simulator for display.
        proj.getSimulator().addPendingInput(this, temporaryClock);
      }
    }
    return true;
  }

  private boolean knownClocks;
  private Component temporaryClock;

  public boolean hasKnownClocks() {
    return knownClocks || temporaryClock != null;
  }

  public void markKnownClocks() {
    knownClocks = true;
  }

  public boolean setTemporaryClock(Component clk) {
    temporaryClock = clk;
    return clk == null ? true : temporaryClockValidateOrTick(-1);
  }
  
  public Component getTemporaryClock() {
    return temporaryClock;
  }

  @Override
  public String toString() {
    return "State" + id + "[" + circuit.getName() + "]";
  }

  public int getId() {
    return id;
  }

	// DEBUGGING
  // DEBUG: private static Object dumpLock = new Object();
  // DEBUG: private static int dumpIndent = 0;
  // DEBUG: public synchronized void dump(String msg, Object ...fmt) {
  // DEBUG:   synchronized (dumpLock) {
  // DEBUG:     synchronized (dirtyLock) {
  // DEBUG:       synchronized (valuesLock) {
  // DEBUG:         String t = dumpIndent <= 0 ? "" : String.format("%"+dumpIndent+"s", "");
  // DEBUG:         dumpIndent += 2;
  // DEBUG:         // Thread.dumpStack();
  // DEBUG:         System.out.printf(t+"{ Dumping %s values: %s\n", this, String.format(msg, fmt));
  // DEBUG:         System.out.printf(t+"  Current values at canvas locations:\n");
  // DEBUG:         slowpath_values.forEach((loc, val) ->
  // DEBUG:             System.out.printf(t+"    at %s value = %s (from slowpath)\n", loc, val));
  // DEBUG:         for (int i = 0; i < FASTPATH_GRID_HEIGHT; i++) {
  // DEBUG:           for (int j = 0; j < FASTPATH_GRID_WIDTH; j++) {
  // DEBUG:             Value val = fastpath_values[i][j];
  // DEBUG:             if (val != null)
  // DEBUG:               System.out.printf(t+"    at (%d,%d) value = %s\n", 10*j, 10*i, val);
  // DEBUG:           }
  // DEBUG:         }
  // DEBUG:         if (wireData != null) {
  // DEBUG:           System.out.printf(t+"  Wire data:\n");
  // DEBUG:           circuit.wires.dump(t+"  ", wireData);
  // DEBUG:         } else {
  // DEBUG:           System.out.printf(t+"  No wire data.\n");
  // DEBUG:         }
  // DEBUG:         if (dirtyPoints.isEmpty())
  // DEBUG:           System.out.printf(t+"  No pending point-change events.\n");
  // DEBUG:         else {
  // DEBUG:           System.out.printf(t+"  %d pending point-change events:\n", dirtyPoints.size());
  // DEBUG:           for (Propagator.SimulatorEvent e : dirtyPoints) {
  // DEBUG:             System.out.printf(t+"    %s\n", e);
  // DEBUG:             if (e.state != this)
  // DEBUG:               System.out.printf(t+"    ERROR: above event is on wrong state\n");
  // DEBUG:           }
  // DEBUG:         }
  // DEBUG:         Set<Component> comps = circuit.getNonWires();
  // DEBUG:         if (comps.isEmpty())
  // DEBUG:           System.out.printf(t+"  Contains no components.\n");
  // DEBUG:         else
  // DEBUG:           System.out.printf(t+"  Contains %d components:\n", comps.size());
  // DEBUG:         Set<Object> printed = new HashSet<>();
  // DEBUG:         // sanity check the dirtyComponents
  // DEBUG:         for (Component c: dirtyComponents)
  // DEBUG:           if (!comps.contains(c))
  // DEBUG:             System.out.printf(t+"    ERROR: component marked dirty, but unlisted: %s\n", c);
  // DEBUG:         for (Component c : comps) {
  // DEBUG:           Object data = getData(c);
  // DEBUG:           System.out.printf(t+"    (%s) %s data = %s\n", 
  // DEBUG:               dirtyComponents.contains(c) ? "dirty" : "clean",
  // DEBUG:               c, data);
  // DEBUG:           if (data instanceof CircuitState) {
  // DEBUG:             if (printed.contains(data)) {
  // DEBUG:               System.out.printf(t+"  ERROR - this substate was already seen.\n");
  // DEBUG:             } else {
  // DEBUG:               printed.add(data);
  // DEBUG:               if (!substates.contains(data))
  // DEBUG:                 System.out.printf(t+"  ERROR - this substate is not found in substates map.\n");
  // DEBUG:               ((CircuitState)data).dump("for component %s in %s", c, circuit.getName());
  // DEBUG:             }
  // DEBUG:           }
  // DEBUG:         }
  // DEBUG:         for (CircuitState sub : substates) {
  // DEBUG:           if (!printed.contains(sub))
  // DEBUG:             System.out.printf(t+"  ERROR - substate not associated with component: %s\n", sub);
  // DEBUG:         }
  // DEBUG:         System.out.printf(t+"}\n");
  // DEBUG:         dumpIndent -= 2;
  // DEBUG:       }
  // DEBUG:     }
  // DEBUG:   }
  // DEBUG: }

}
