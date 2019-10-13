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

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.gui.log.ClockSource;
import com.cburch.logisim.gui.log.ComponentSelector;
import com.cburch.logisim.gui.log.SignalInfo;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.UniquelyNamedThread;

public class Simulator {

  public static class Event {
    private Simulator source;
    private boolean didTick, didSingleStep, didPropagate;

    public Event(Simulator src, boolean t, boolean s, boolean p) {
      source = src;
      didTick = t;
      didSingleStep = s;
      didPropagate = p;
    }

    public Simulator getSource() { return source; }
    public boolean didTick() { return didTick; }
    public boolean didSingleStep() { return didSingleStep; }
    public boolean didPropagate() { return didPropagate; }
  }

  public static interface StatusListener {
    public void simulatorReset(Event e);
    public void simulatorStateChanged(Event e);
  }

  public static interface Listener extends StatusListener {
    public void propagationCompleted(Event e);
  }

  public static interface ProgressListener extends Listener {
    public boolean wantProgressEvents();
    public void propagationInProgress(Event e);
  }

  // This thread keeps track of the current stepPoints (when running in step
  // mode), and it invokes various Propagator methods:
  //
  //     propagator.reset() -- clears all signal values
  //     propagator.toggleClocks() -- toggles clock components
  //     propagator.propagate() -- auto-propagates until signals are stable
  //     propagator.step(stepPoints) -- propagates a single step
  //     propagator.isPending() -- checks if more signal changes are pending
  //
  // The thread will invoked these in response to various events:
  //
  // [auto-tick]   If autoTicking is on and autoPropagation is on, the thread
  //               periodically wakes up and invokes toggleClocks() then
  //               propagate().
  //
  // [manual-tick] If the User/GUI requests a tick happen and autoPropagation is
  //               on, the thread wakes up and invokes toggleClocks() then
  //               propagate(). If autoPropagation is off, thread will wake up
  //               and call toggleClocks() then step().
  //
  // [nudge]       If the user makes a circuit change, the thread wakes up and
  //               invokes propagate() or step().
  //
  // [reset]       If the User/GUI requests a reset, the thread wakes up and
  //               invokes reset() and maybe also propagate().
  //
  // [single-step] If the User/GUI requests a single-step propagation (this
  //               only happens when autoTicking is off), the thread wakes up
  //               and invokes step(). If if autoTicking is on and signals are
  //               stable, then toggleClocks() is also called before step().
  private static class SimThread extends UniquelyNamedThread {

    private Simulator sim;
    private long lastTick = System.nanoTime();

    // NOTE: These variables must only be accessed with lock held.
    private Propagator _propagator = null;
    private boolean _autoPropagating = true;
    private boolean _autoTicking = false;
    private double _autoTickFreq = 1.0; // Hz
    private long _autoTickNanos = (long)Math.round(1e9 / (2*_autoTickFreq));
    private int _manualTicksRequested = 0;
    private int _manualStepsRequested = 0;
    private boolean _nudgeRequested = false;
    private boolean _resetRequested = false;
    private boolean _complete = false;
    private boolean _oops = false;

    // This last one should be made thread-safe, but it isn't for now.
    private PropagationPoints stepPoints = new PropagationPoints();

    SimThread(Simulator s) {
      super("SimThread");
      sim = s;
    }

    synchronized Propagator getPropagator() { return _propagator; }
    synchronized boolean isExceptionEncountered() { return _oops; }
    synchronized boolean isAutoTicking() { return _autoTicking; }
    synchronized boolean isAutoPropagating() { return _autoPropagating; }
    synchronized double getTickFrequency() { return _autoTickFreq; }
  
    synchronized void drawStepPoints(ComponentDrawContext context) {
      if (!_autoPropagating)
        stepPoints.draw(context);
    }

    synchronized void drawPendingInputs(ComponentDrawContext context) {
      if (!_autoPropagating)
        stepPoints.drawPendingInputs(context);
    }
  
    synchronized void addPendingInput(CircuitState state, Component comp) {
      if (!_autoPropagating)
        stepPoints.addPendingInput(state, comp);
    }

    synchronized String getSingleStepMessage() {
      return _autoPropagating ? "" : stepPoints.getSingleStepMessage();
    }

    synchronized boolean setPropagator(Propagator value) {
      if (_propagator == value)
        return false;
      _propagator = value;
      _manualTicksRequested = 0;
      _manualStepsRequested = 0;
      if (Thread.currentThread() != this)
        notifyAll();
      return true;
    }
    
    synchronized boolean setAutoPropagation(boolean value) {
      if (_autoPropagating == value)
        return false;
      _autoPropagating = value;
      if (_autoPropagating)
        _manualStepsRequested = 0; // manual steps not allowed in autoPropagating mode
      else
        _nudgeRequested = false; // nudges not allowed in single-step mode
      if (Thread.currentThread() != this)
        notifyAll();
      return true;
    }

    synchronized boolean setAutoTicking(boolean value) {
      if (_autoTicking == value)
        return false;
      _autoTicking = value;
      if (Thread.currentThread() != this)
        notifyAll();
      return true;
    }

    synchronized boolean setTickFrequency(double freq) {
      if (_autoTickFreq == freq)
        return false;
      _autoTickFreq = freq;
      _autoTickNanos = freq <= 0 ? 0 : (long)Math.round(1e9 / (2*_autoTickFreq));
      if (Thread.currentThread() != this)
        notifyAll();
      return true;
    }

    synchronized void requestStep() {
      _manualStepsRequested++;
      _autoPropagating = false;
      if (Thread.currentThread() != this)
        notifyAll();
    }

    synchronized void requestTick(int count) {
      _manualTicksRequested += count;
      if (Thread.currentThread() != this)
        notifyAll();
    }

    synchronized void requestReset() {
      _resetRequested = true;
      _manualTicksRequested = 0;
      _manualStepsRequested = 0;
      if (Thread.currentThread() != this)
        notifyAll();
    }

    synchronized boolean requestNudge() {
      if (!_autoPropagating)
        return false;
      _nudgeRequested = true;
      if (Thread.currentThread() != this)
        notifyAll();
      return true;
    }

    synchronized void requestShutDown() {
      _complete = true;
      if (Thread.currentThread() != this)
        notifyAll();
    }

    int cnt;
    private boolean loop() {

      Propagator prop = null;
      boolean doReset = false;
      boolean doNudge = false;
      boolean doTick = false;
      boolean doTickIfStable = false;
      boolean doStep = false;
      boolean doProp = false;
      long now = 0;

      synchronized (this) {

        boolean ready = false;
        do {
          if (_complete)
            return false;

          prop = _propagator;
          now = System.nanoTime();

          if (_resetRequested) {
            _resetRequested = false;
            doReset = true;
            doProp = _autoPropagating;
            ready = true;
          }

          if (_nudgeRequested) {
            _nudgeRequested = false;
            doNudge = true;
            ready = true;
          }

          if (_manualStepsRequested > 0) {
            _manualStepsRequested--;
            doTickIfStable = _autoTicking;
            doStep = true;
            ready = true;
          }
          
          if (_manualTicksRequested > 0) {
            // variable is decremented below
            doTick = true;
            doProp = _autoPropagating;
            doStep = !_autoPropagating;
            ready = true;
          }
         
          long delta = 0;
          if (_autoTicking && _autoPropagating && _autoTickNanos > 0) {
            // see if it is time to do an auto-tick
            long deadline = lastTick + _autoTickNanos;
            delta = deadline - now;
            if (delta <= 0) {
              doTick = true;
              doProp = true;
              ready = true;
            }
          }
         
          if (!ready) {
            // LockSupport.parkNanos(delta);
            try {
              if (delta > 0)
                wait(delta/1000000, (int)(delta%1000000));
              else
                wait();
            }
            catch (InterruptedException e) { } // yes, we swallow the interrupt
          }
        } while (!ready);

        _oops = false;
      }
      // DEBUGGING
      // System.out.printf("%d nudge %s tick %s prop %s step %s\n", cnt++, doNudge, doTick, doProp, doStep);

      boolean oops = false;
      boolean osc = false;
      boolean ticked = false;
      boolean stepped = false;
      boolean propagated = false;
      boolean hasClocks = true;

      if (doReset) try {
        stepPoints.clear();
        if (prop != null)
          prop.reset();
        sim._fireSimulatorReset(); // todo: fixme: ack, wrong thread!
      } catch (Exception err) {
        oops = true;
        err.printStackTrace();
      }

      if (doTick || (doTickIfStable && prop != null && !prop.isPending())) {
        lastTick = now;
        ticked = true;
        if (prop != null)
          hasClocks = prop.toggleClocks();
      }

      if (doProp || doNudge) try {
        propagated = doProp;
        // todo: need to fire events in here for chrono fine grained
        ProgressListener p = sim._progressListener; // events fired on simulator thread, but probably should not be
        Event evt = p == null ? null : new Event(sim, false, false, false);
        stepPoints.clear();
        if (prop != null)
          propagated |= prop.propagate(p, evt);
      } catch (Exception err) {
        oops = true;
        err.printStackTrace();
      }

      if (doStep) try {
        stepped = true;
        stepPoints.clear();
        if (prop != null)
          prop.step(stepPoints);
        if (prop == null || !prop.isPending())
          propagated = true;
      } catch (Exception err) {
        oops = true;
        err.printStackTrace();
      }
     
      osc = prop != null && prop.isOscillating();

      boolean clockDied = false;
      synchronized (this) {
        _oops = oops;
        if (osc) {
          _autoPropagating = false;
          _nudgeRequested = false;
        }
        if (ticked && _manualTicksRequested > 0)
          _manualTicksRequested--;
        if (_autoTicking && !hasClocks) {
          _autoTicking = false;
          clockDied = true;
        }
      }
   
      // We report nudges, but we report them as no-ops, unless they were
      // accompanied by a tick, step, or propagate. That allows for a repaint in
      // some components.
      if (ticked || stepped || propagated || doNudge)
        sim._firePropagationCompleted(ticked, stepped && !propagated, propagated); // todo: fixme: ack, wrong thread!
      if (clockDied)
        sim.fireSimulatorStateChanged(); ; // todo: fixme: ack, wrong thread!

      return true;
    }

    @Override
    public void run() {
      for (;;) {
        try {
          if (!loop())
            return;
        } catch (Throwable e) {
          e.printStackTrace();
          synchronized(this) {
            _oops = true;
            _autoPropagating = false;
            _autoTicking = false;
            _manualTicksRequested = 0;
            _manualStepsRequested = 0;
            _nudgeRequested = false;
          }
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(null,
                  "The simulator crashed. Save your work and restart Logisim.");
            }
          });
        }
      }
    }

  }

  //
  // Everything below here is invoked and accessed only by the User/GUI thread.
  //

  private SimThread simThread;

  // listeners is protected by a lock because simThread calls the _fire*()
  // methods, but the gui thread can call add/removeSimulateorListener() at any
  // time. Really, the _fire*() methods should be done on the gui thread, I
  // suspect.
  private ArrayList<StatusListener> statusListeners = new ArrayList<>();
  private ArrayList<Listener> activityListeners = new ArrayList<>();
  private volatile ProgressListener _progressListener = null;
  private Object lock = new Object();

  // private class Dummy extends UniquelyNamedThread {
  //   Dummy() { super("dummy"); }
  //   public void run() {
  //     try {
  //       while(true) {
  //         Thread.sleep(6000);
  //         long t = System.currentTimeMillis();
  //         long e = t + 6000;
  //         long sum = 0;
  //         while (t < e) {
  //           t = System.currentTimeMillis();
  //           sum += (t % 100);
  //         }
  //         System.out.printf("t=%d sum=%d\n", t, sum);
  //       }
  //     } catch (Exception e) { }
  //   }
  // }

  public Simulator() {
    simThread = new SimThread(this);
    // UniquelyNamedThread dummy1= new Dummy();
    // UniquelyNamedThread dummy2 = new Dummy();
    // UniquelyNamedThread dummy3 = new Dummy();

    try {
      simThread.setPriority(simThread.getPriority() - 1);
    //   dummy1.setPriority(dummy1.getPriority() - 1);
    //   dummy2.setPriority(dummy2.getPriority() - 1);
    //   dummy3.setPriority(dummy3.getPriority() - 1);
    } catch (IllegalArgumentException | SecurityException e) { }

    simThread.start();
    // dummy1.start();
    // dummy2.start();
    // dummy3.start();

    setTickFrequency(AppPreferences.TICK_FREQUENCY.get().doubleValue());
  }

  public void addSimulatorListener(StatusListener l) {
    if (l instanceof Listener) {
      synchronized(lock) {
        statusListeners.add(l);
        activityListeners.add((Listener)l);
        if (_numListeners >= 0) {
          if (_numListeners+1 >= _listeners.length) {
            Listener[] t = new Listener[2 * _listeners.length];
            for (int i = 0; i < _numListeners; i++)
              t[i] = _listeners[i];
            _listeners = t;
          }
          _listeners[_numListeners] = (Listener)l;
          _numListeners++;
        }
        if (l instanceof ProgressListener) {
          if (_progressListener != null)
            throw new IllegalStateException("only one chronogram listener supported");
          _progressListener = (ProgressListener)l;
        }
      }
    } else {
      synchronized(lock) {
        statusListeners.add(l);
      }
    }
  }

  public void removeSimulatorListener(StatusListener l) {
    if (l instanceof Listener) {
      synchronized(lock) {
        if (l == _progressListener)
          _progressListener = null;
        statusListeners.remove(l);
        activityListeners.remove((Listener)l);
        _numListeners = -1;
      }
    } else {
      synchronized(lock) {
        statusListeners.remove(l);
      }
    }
  }

  public void drawStepPoints(ComponentDrawContext context) {
    simThread.drawStepPoints(context);
  }

  public void drawPendingInputs(ComponentDrawContext context) {
    simThread.drawPendingInputs(context);
  }

  public String getSingleStepMessage() {
    return simThread.getSingleStepMessage();
  }

  public void addPendingInput(CircuitState state, Component comp) {
    simThread.addPendingInput(state, comp);
  }

  private ArrayList<StatusListener> copyStatusListeners() {
    ArrayList<StatusListener> copy;
    synchronized (lock) {
      copy = new ArrayList<StatusListener>(statusListeners);
    }
    return copy;
  }

  // fast copy, only as needed, only used by simThread
  // invariant: whenever the simulator thread observes _numListeners = n, where
  //   n >= 0, then there was recently exactly n listeners registered, and those
  //   n listeners are in _listeners[0..n-1]. No other thread will invalidate
  //   the _listeners variable or change any of those slots.
  private volatile Listener[] _listeners = new Listener[10];
  private volatile int _numListeners = 0;
  
  // called from simThread, but probably should not be
  private void _fireSimulatorReset() {
    Event e = new Event(this, false, false, false);
    for (StatusListener l : copyStatusListeners())
      l.simulatorReset(e);
  }

  // called from simThread, but probably should not be
  private void _firePropagationCompleted(boolean t, boolean s, boolean p) {
    int n = _numListeners;
    Listener[] list = _listeners;
    if (n < 0) {
      // fast copy was out of date, maybe need to refresh
      synchronized (lock) {
        n = activityListeners.size();
        if (n > _listeners.length)
          _listeners = new Listener[2*n];
        for (int i = 0; i < n; i++)
          _listeners[i] = activityListeners.get(i);
        for (int i = n; i < _listeners.length; i++)
          _listeners[i] = null;
        _numListeners = n;
      }
    }
    if (n == 0)
      return; // nothing to do, no listeners as of just a moment ago
    Event e = new Event(this, t, s, p);
    for (int i = 0; i < n; i++)
      _listeners[i].propagationCompleted(e);
  }

  // called from simThread (via Propagator.propagate()), but probably should not be
  // void _firePropagationInProgress() {
  //   Event e = new Event(this, false, false, false);
  //   for (Listener l : copyListeners())
  //     l.propagationInProgress(e);
  // }
  // // called from simThread, but probably should not be
  // private ProgressListener getPropagationListener() {
  //   Listener p = null;
  //   for (StatusListener l : copyStatusListeners()) {
  //     if (l instanceof Listener && ((Listener)l).wantProgressEvents()) {
  //       if (p != null)
  //         throw new IllegalStateException("only one chronogram listener supported");
  //       p = (Listener)l;
  //     }
  //   }
  //   return p;
  // }

  // called only from gui thread, but need copy here anyway because listeners
  // can add/remove from listeners list?
  private void fireSimulatorStateChanged() {
    Event e = new Event(this, false, false, false);
    for (StatusListener l : copyStatusListeners())
      l.simulatorStateChanged(e);
  }

  public double getTickFrequency() {
    return simThread.getTickFrequency();
  }

  public boolean isExceptionEncountered() {
    return simThread.isExceptionEncountered();
  }

  public boolean isOscillating() {
    Propagator prop = simThread.getPropagator();
    return prop != null && prop.isOscillating();
  }

  public CircuitState getCircuitState() {
    Propagator prop = simThread.getPropagator();
    return prop == null ? null : prop.getRootState();
  }

  public boolean isAutoPropagating() {
    return simThread.isAutoPropagating();
  }

  public boolean isAutoTicking() {
    return simThread.isAutoTicking();
  }

  public void setCircuitState(CircuitState state) {
    if (simThread.setPropagator(state == null ? null : state.getPropagator()))
      fireSimulatorStateChanged();
  }

  public void setAutoPropagation(boolean value) {
    if (simThread.setAutoPropagation(value))
      fireSimulatorStateChanged();
  }

  public void setAutoTicking(boolean value) {
    if (value && !ensureClocks())
      return;
    if (simThread.setAutoTicking(value))
      fireSimulatorStateChanged();
  }

  public void setTickFrequency(double freq) {
    if (simThread.setTickFrequency(freq))
      fireSimulatorStateChanged();
  }

  // User/GUI manually requests one single-step propagation.
  public void step() {
    simThread.requestStep();
  }

  // User/GUI manually requests some ticks happen as soon as possible.
  public void tick(int count) {
    if (!ensureClocks())
      return;
    simThread.requestTick(count);
  }

  // User/GUI manually requests a reset
  public void reset() {
    simThread.requestReset();
  }

  // Circuit changed, nudge the signals if needed to fix any pending changes
  public boolean nudge() {
    return simThread.requestNudge();
  }

  public void shutDown() {
    simThread.requestShutDown();
  }

  private boolean ensureClocks() {
    CircuitState cs = getCircuitState();
    if (cs == null)
      return false;
    if (cs.hasKnownClocks())
      return true;
    Circuit circ = cs.getCircuit();
    ArrayList<SignalInfo> clocks = ComponentSelector.findClocks(circ);
    if (clocks != null && clocks.size() >= 1) {
      cs.markKnownClocks();
      return true;
    }

    Component clk = ClockSource.doClockDriverDialog(circ);
    if (clk == null)
      return false;
    if (!cs.setTemporaryClock(clk))
      return false;
    fireSimulatorStateChanged();
    return true;
  }

}
