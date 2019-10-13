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

//import java.util.PriorityQueue;
import java.util.Random;
import java.lang.ref.WeakReference;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Options;

public class Propagator {
  // static class ComponentPoint {
  //   Component cause;
  //   Location loc;

  //   public ComponentPoint(Component cause, Location loc) {
  //     this.cause = cause;
  //     this.loc = loc;
  //   }

  //   @Override
  //   public boolean equals(Object other) {
  //     if (!(other instanceof ComponentPoint))
  //       return false;
  //     ComponentPoint o = (ComponentPoint) other;
  //     return this.cause.equals(o.cause) && this.loc.equals(o.loc);
  //   }

  //   @Override
  //   public int hashCode() {
  //     return 31 * cause.hashCode() + loc.hashCode();
  //   }
  // }

  private static class Listener implements AttributeListener {
    // weak reference here, to allow prop to be garbage collected
    WeakReference<Propagator> prop;

    public Listener(Propagator propagator) {
      prop = new WeakReference<>(propagator);
    }

    public void attributeListChanged(AttributeEvent e) {
    }

    public void attributeValueChanged(AttributeEvent e) {
      Propagator p = prop.get();
      if (p == null)
        return;
      if (e.getAttribute().equals(Options.ATTR_SIM_RAND))
        p.updateRandomness();
      else if (e.getAttribute().equals(Options.ATTR_SIM_LIMIT))
        p.updateSimLimit();
    }
  }

  // static class DrivenValue {
  //   DrivenValue next; // linked list
  //   final Component driver;
  //   Value val;
  //   DrivenValue(Component c, Value v) { driver = c; val = v; }
  // }

  public static class SimulatorEvent extends SplayQueue.Node
    implements Comparable<SimulatorEvent> {

    int time;
    int serialNumber; // used to make the times unique

    CircuitState state; // state of circuit containing component
    Location loc; // the location at which value is emitted
    Component cause; // component emitting the value
    Value val; // value being emitted

    private SimulatorEvent(int time, int serialNumber,
        CircuitState state, Location loc, Component cause, Value val) {
      super(((long)time << 32) | (serialNumber & 0xFFFFFFFFL));
      this.time = time;
      this.serialNumber = serialNumber;
      this.state = state;
      this.cause = cause;
      this.loc = loc;
      this.val = val;
      // System.out.printf("sim event: %s at %s by %s\n", val, loc, cause);
      // try { throw new Exception(); }
      // catch (Exception e) { e.printStackTrace(); }
    }

    public SimulatorEvent cloneFor(CircuitState newState) {
      Propagator newProp = newState.getPropagator();
      int dtime = newProp.clock - state.getPropagator().clock;
      SimulatorEvent ret = new SimulatorEvent(time + dtime,
          newProp.eventSerialNumber++, newState, loc, cause, val);
      return ret;
    }

    public int compareTo(SimulatorEvent o) {
      // Yes, these subtractions may overflow. This is intentional, as it
      // avoids potential wraparound problems as the counters increment.
      int ret = this.time - o.time;
      if (ret != 0)
        return ret;
      return this.serialNumber - o.serialNumber;
    }

    @Override
    public String toString() {
      return loc + ":" + val + "(" + cause + ")";
    }
  }

  // // This one is only used to initialize  TODO: can we eliminate this... is it used only when initializing BundleMap?
  // static Value getDrivenValueAt(CircuitState circState, Location p) {
  //   // for CircuitWires - to get values, ignoring wires' contributions
  //   DrivenValue vals;
  //   synchronized (circState.valuesLock) {
  //     vals = circState.slowpath_drivers.get(p);
  //   }
  //   return computeValue(vals);
  // }

  // static Value computeValue(DrivenValue vals) {
  //   if (vals == null)
  //     return Value.NIL;
  //   Value ret = vals.val;
  //   for (DrivenValue v = vals.next; v != null; v = v.next)
  //     ret = ret.combine(v.val);
  //   return ret;
  // }

  // static void copyDrivenValues(CircuitState dest, CircuitState src) {
  //   // note: we don't bother with our this.valuesLock here: it isn't needed
  //   // (b/c no other threads have a reference to this yet), and to avoid the
  //   // possibility of deadlock (though that shouldn't happen either since no
  //   // other threads have references to this yet).
  //   dest.slowpath_drivers.clear();
  //   synchronized (src.valuesLock)  {
  //     for (Location loc : src.slowpath_drivers.keySet()) {
  //       DrivenValue v = src.slowpath_drivers.get(loc);
  //       DrivenValue n = new DrivenValue(v.driver, v.val);
  //       dest.slowpath_drivers.put(loc, n);
  //       while (v.next != null) {
  //         n.next = new DrivenValue(v.next.driver, v.next.val);
  //         v = v.next;
  //         n = n.next;
  //       }
  //     }
  //   }
  // }

  private CircuitState root; // root of state tree

  /**
   * The number of clock cycles to let pass before deciding that the circuit
   * is oscillating.
   */
  private int simLimit = 1000;

  /**
   * On average, one out of every 2**simRandomShift propagations through a
   * component is delayed one step more than the component requests. This
   * noise is intended to address some circuits that would otherwise oscillate
   * within Logisim (though they wouldn't oscillate in practice).
   */
  private volatile int simRandomShift;

  // The simulator event queue can be implemented by a PriorityQueue,
  // SplayQueue, or LinkedQueue. LinkedQueue seems fastest in practice, though
  // it has poor worst-case performance. SplayQueue should have good
  // expected-case performance, but it seems a bit slower than LinkedQueue.
  // Priority queue seems slightly worse than the others. It is trivial to
  // switch between the implementations, just re-comment these next lines.

  // private PriorityQueue<SimulatorEvent> toProcess = new PriorityQueue<>();
  // private SplayQueue<SimulatorEvent> toProcess = new SplayQueue<>();
  private LinkedQueue<SimulatorEvent> toProcess = new LinkedQueue<>();

  private int clock = 0;
  private boolean isOscillating = false;
  private boolean oscAdding = false;
  private PropagationPoints oscPoints = new PropagationPoints();
  private int halfClockCycles = 0;
  private Random noiseSource = new Random();
  private int noiseCount = 0;

  private int eventSerialNumber = 0;

  static int lastId = 0;
  int id = lastId++;

  public Propagator(CircuitState root) {
    this.root = root;
    // Listener here uses a weak reference, otherwise a cycle is created: the
    // weak hashmap just below will end up keeping a strong reference to this
    // Propagator, and the listener will not get removed from the hashmap, which
    // in turn keeps this Propagator alive, etc. That cycle keeps every
    // propagator alive forever. A weak reference breaks the cycle and allows
    // dead propagtors to get collected..
    Listener l = new Listener(this);
    root.getProject().getOptions().getAttributeSet().addAttributeWeakListener(this, l);
    updateRandomness();
    updateSimLimit();
  }

  // // precondition: state.valuesLock held
  // private static DrivenValue addCause(CircuitState state, DrivenValue head, 
  //     Location loc, Component cause, Value val) {
  //   if (val == null) // actually, it should be removed
  //     return removeCause(state, head, loc, cause);

  //   // first check whether this is change of previous info
  //   for (DrivenValue n = head; n != null; n = n.next) {
  //     if (n.driver == cause) {
  //       n.val = val;
  //       return head;
  //     }
  //   }

  //   // otherwise, insert into list of causes
  //   DrivenValue n = new DrivenValue(cause, val);
  //   if (head == null) {
  //     head = n;
  //     state.slowpath_drivers.put(loc, head);
  //   } else {
  //     n.next = head.next;
  //     head.next = n;
  //     System.out.printf("--> loc %s has multiple drivers!\n", loc);
  //     for (DrivenValue v = head; v != null; v = v.next)
  //         System.out.printf("  comp %s val %s\n", v.driver, v.val);
  //     Circuit circ = state.getCircuit();
  //     System.out.printf("  circuit = %s\n", circ);
  //     for (Component c : circ.getNonWires())
  //       System.out.printf("   comp: %s\n", c);
  //   }

  //   return head;
  // }

  public void drawOscillatingPoints(ComponentDrawContext context) {
    if (isOscillating)
      oscPoints.draw(context);
  }

  CircuitState getRootState() {
    return root;
  }

  public int getTickCount() {
    return halfClockCycles;
  }

  public boolean isOscillating() {
    return isOscillating;
  }

  boolean isPending() {
    return !toProcess.isEmpty();
  }

  void locationTouched(CircuitState state, Location loc) {
    if (oscAdding)
      oscPoints.add(state, loc);
  }

  public boolean propagate() {
    return propagate(null, null);
  }

  public boolean propagate(Simulator.ProgressListener propListener, Simulator.Event propEvent) { // Safe to call from sim thread
    oscPoints.clear();
    root.processDirtyPoints();
    root.processDirtyComponents();

    int oscThreshold = simLimit;
    int logThreshold = 3 * oscThreshold / 4;
    int iters = 0;
    while (!toProcess.isEmpty()) {
      if (iters > 0 && propListener != null)
        propListener.propagationInProgress(propEvent);
      iters++;

      if (iters < logThreshold) {
        stepInternal(null);
      } else if (iters < oscThreshold) {
        oscAdding = true;
        stepInternal(oscPoints);
      } else {
        isOscillating = true;
        oscAdding = false;
        return true;
      }
    }
    isOscillating = false;
    oscAdding = false;
    oscPoints.clear();
    return iters > 0;
  }

  // // precondition: state.valuesLock held
  // private static DrivenValue removeCause(CircuitState state, DrivenValue head, 
  //     Location loc, Component cause) {
  //   if (head == null)
  //     return null;

  //   if (head.driver == cause) {
  //     head = head.next;
  //     if (head == null)
  //       state.slowpath_drivers.remove(loc);
  //     else
  //       state.slowpath_drivers.put(loc, head);
  //   } else {
  //     DrivenValue prev = head;
  //     DrivenValue cur = head.next;
  //     while (cur != null) {
  //       if (cur.driver == cause) {
  //         prev.next = cur.next;
  //         break;
  //       }
  //       prev = cur;
  //       cur = cur.next;
  //     }
  //   }
  //   return head;
  // }

  void reset() {
    halfClockCycles = 0;
    toProcess.clear();
    root.reset();
    isOscillating = false;
  }

  void setValue(CircuitState state, Location pt, Value val, Component cause, int delay) {
    if (cause instanceof Wire || cause instanceof Splitter)
      return;
    if (delay <= 0) {
      delay = 1;
    }
    int randomShift = simRandomShift;
    if (randomShift > 0) { // random noise is turned on
      // multiply the delay by 32 so that the random noise
      // only changes the delay by 3%.
      delay <<= randomShift;
      if (!(cause.getFactory() instanceof SubcircuitFactory)) {
        if (noiseCount > 0) {
          noiseCount--;
        } else {
          delay++;
          noiseCount = noiseSource.nextInt(1 << randomShift);
        }
      }
    }
    toProcess.add(new SimulatorEvent(clock + delay, eventSerialNumber, state, pt, cause, val));

    // DEBUG: System.out.printf("%s: set %s in %s to %s by %s after %s\n",
    // DEBUG:     clock, pt, state, val, cause, delay);
    // DEBUG: Thread.dumpStack();

    eventSerialNumber++;
  }

  boolean step(PropagationPoints changedPoints) { // Safe to call from sim thread
    oscPoints.clear();
    root.processDirtyPoints();
    root.processDirtyComponents();

    if (toProcess.isEmpty())
      return false;

    PropagationPoints oldOsc = oscPoints;
    oscAdding = changedPoints != null;
    oscPoints = changedPoints;
    stepInternal(changedPoints);
    oscAdding = false;
    oscPoints = oldOsc;
    return true;
  }

  // private int visitedNonce = 1;
  private void stepInternal(PropagationPoints changedPoints) { // Safe to call from sim thread
		// DEBUG: System.out.println("== Step Internal ==");
    
		if (toProcess.isEmpty()) {
			// DEBUG: System.out.println("-- Done --");
      return;
		}

    // update clock
    clock = toProcess.peek().time;
    // visitedNonce++; // used to ensure a fresh circuitState.visited set.

    // propagate all values for this clock tick
    while (true) {
      SimulatorEvent ev = toProcess.peek();
      if (ev == null || ev.time != clock)
        break;
      toProcess.remove();
      CircuitState state = ev.state;

      // // if it's already handled for this clock tick, continue
      // if (state.visitedNonce != visitedNonce) {
      //   // first time visiting this circuitState during this call to stepInternal
      //   state.visitedNonce = visitedNonce;
      //   state.visited.clear();
      // }
      // if (!state.visited.add(new ComponentPoint(ev.cause, ev.loc)))
      //   continue; // this component+loc change has already been handled

			// DEBUG: System.out.printf("%s: proc %s in %s to %s by %s\n",
			// DEBUG: 		ev.time, ev.loc, ev.state, ev.val, ev.cause);

      if (changedPoints != null)
        changedPoints.add(state, ev.loc);

      // // change the information about value
      // Value oldVal, newVal;
      // synchronized (state.valuesLock) {
      //   DrivenValue oldHead = state.slowpath_drivers.get(ev.loc);
      //   oldVal = computeValue(oldHead);
      //   DrivenValue newHead = addCause(state, oldHead, ev.loc, ev.cause, ev.val);
      //   newVal = computeValue(newHead);
      // }

      // if the value at point has changed, propagate it
      // if (!newVal.equals(oldVal)) {
        state.markPointAsDirty(ev); // ev.loc, ev.cause, ev.val);
      // }
    }

		// DEBUG: System.out.println("-- process dirty points --");
		// DEBUG: root.dump("for %s before processDirtyPoints", this);
    root.processDirtyPoints();
		// DEBUG: root.dump("for %s after processDirtyPoints, before processDirtyComponents", this);
		// DEBUG: System.out.println("-- process dirty components --");
    root.processDirtyComponents();
		// DEBUG: root.dump("for %s after processDirtyComponents", this);
		// DEBUG: System.out.println("-- Done --");
  }

  public boolean toggleClocks() {
    halfClockCycles++;
    return root.toggleClocks(halfClockCycles);
  }

  @Override
  public String toString() {
    return "Prop" + id;
  }

  private void updateRandomness() {
    Options opts = root.getProject().getOptions();
    Object rand = opts.getAttributeSet().getValue(Options.ATTR_SIM_RAND);
    int val = ((Integer) rand).intValue();
    int logVal = 0;
    while ((1 << logVal) < val)
      logVal++;
    simRandomShift = logVal;
  }

  private void updateSimLimit() {
    Options opts = root.getProject().getOptions();
    Object limit = opts.getAttributeSet().getValue(Options.ATTR_SIM_LIMIT);
    int val = ((Integer) limit).intValue();
    simLimit = val;
  }

}
