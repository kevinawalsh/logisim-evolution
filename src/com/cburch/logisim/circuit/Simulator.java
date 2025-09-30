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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.log.ClockSource;
import com.cburch.logisim.gui.log.ComponentSelector;
import com.cburch.logisim.gui.log.SignalInfo;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Debug;
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

  /* private static class OptionsListener implements AttributeListener {
    // weak reference here, to allow prop to be garbage collected
    WeakReference<Simulator> sim;

    public OptionsListener(Simulator s) {
      sim = new WeakReference<>(s);
    }

    public void attributeListChanged(AttributeEvent e) { }

    public void attributeValueChanged(AttributeEvent e) {
      Simulator s = sim.get();
      if (s == null)
        return;
      if (e.getAttribute().equals(Options.ATTR_SIM_SMOOTHING))
        s.updateSmoothingFactor();
    }
  }
  */

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

    private ReentrantLock simStateLock = new ReentrantLock();
    private Condition simStateUpdated = simStateLock.newCondition();
    // NOTE: These variables must only be accessed with lock held.
    private Propagator _propagator = null;
    private boolean _autoPropagating = true;
    private boolean _autoTicking = false;
    private double _autoTickFreq = 1.0; // Hz
    private int _smoothingFactor = 1; // for WEMA
    private long _autoTickNanos = (long)Math.round(1e9 / (2*_autoTickFreq));
    private int _manualTicksRequested = 0;
    private int _manualStepsRequested = 0;
    private boolean _nudgeRequested = false;
    private boolean _resetRequested = false;
    private boolean _complete = false;
    private double _avgTickNanos = -1.0; // nanoseconds, EWMA

    // These are copies of some of the above variables that can be read without
    // the lock if synchronization with other variables is not needed.
    private volatile Propagator propagatorUnsynchronized = null;
    private volatile boolean autoPropagatingUnsynchronized = true;
    private volatile boolean autoTickingUnsynchronized = false;
    private volatile double autoTickFreqUnsynchronized = 1.0; // Hz

    // These next ones are written only by the simulation thread, and read by
    // the repaining thread. They can be read without locks as they do not need
    // to be kept consistent with other variables.
    private volatile boolean exceptionEncountered = false;
    private volatile boolean oscillating = false;

    // stepPoints should be made thread-safe, but it isn't for now.
    private PropagationPoints stepPoints = new PropagationPoints();

    // lastTick is used only within loop() by a single thread.
    // No synchronization needed.
    private long lastTick = System.nanoTime(); // time of last propagation start

    // DEBUGGING
    // private final long era = lastTick;
    // private String displayTime(long t) {
    //   long delta = t - era;
    //   return String.format("[era+%d.%06dms]", delta/1000000, delta%1000000);
    // }
    // private String displayDuration(long delta) {
    //   return String.format("%.06fms", delta/1000000.0);
    // }
    // private String displayDuration(double delta) {
    //   return String.format("%.06fms", delta/1000000.0);
    // }

    SimThread(Simulator s) {
      super("SimThread");
      sim = s;
    }

    Propagator getPropagatorUnsynchronized() { return propagatorUnsynchronized; }
    boolean isAutoTickingUnsynchronized() { return autoTickingUnsynchronized; }
    boolean isAutoPropagatingUnsynchronized() { return autoPropagatingUnsynchronized; }
    double getTickFrequencyUnsynchronized() { return autoTickFreqUnsynchronized; }

    // This should be made thread-safe, but stepPoints is not yet so.
    void drawStepPoints(ComponentDrawContext context) {
      // simStateLock.lock(); try {
      if (!autoPropagatingUnsynchronized)
        stepPoints.draw(context);
      // } finally { simStateLock.unlock(); }
    }

    // This should be made thread-safe, but stepPoints is not yet so.
    void drawPendingInputs(ComponentDrawContext context) {
      // simStateLock.lock(); try {
      if (!autoPropagatingUnsynchronized)
        stepPoints.drawPendingInputs(context);
      // } finally { simStateLock.unlock(); }
    }
  
    // This should be made thread-safe, but stepPoints is not yet so.
    void addPendingInput(CircuitState state, Component comp) {
      // simStateLock.lock(); try {
      if (!autoPropagatingUnsynchronized)
        stepPoints.addPendingInput(state, comp);
      // } finally { simStateLock.unlock(); }
    }

    // This should be made thread-safe, but stepPoints is not yet so.
    String getSingleStepMessage() {
      // simStateLock.lock(); try {
      return autoPropagatingUnsynchronized ? "" : stepPoints.getSingleStepMessage();
      // } finally { simStateLock.unlock(); }
    }

    boolean setPropagator(Propagator prop) {
      int f = 1;
      if (prop != null) {
        Options opts = prop.getRootState().getProject().getOptions();
        f = opts.getAttributeSet().getValue(Options.ATTR_SIM_SMOOTHING);
        if (f < 1)
          f = 1;
      }
      simStateLock.lock(); try {
        if (_propagator == prop)
          return false;
        _propagator = prop;
        propagatorUnsynchronized = prop;
        _smoothingFactor = f;
        _manualTicksRequested = 0;
        _manualStepsRequested = 0;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
        return true;
      } finally { simStateLock.unlock(); }
    }
    
    boolean setAutoPropagation(boolean value) {
      simStateLock.lock(); try {
        if (_autoPropagating == value)
          return false;
        _autoPropagating = value;
        autoPropagatingUnsynchronized = value;
        if (_autoPropagating)
          _manualStepsRequested = 0; // manual steps not allowed in autoPropagating mode
        else
          _nudgeRequested = false; // nudges not allowed in single-step mode
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
        return true;
      } finally { simStateLock.unlock(); }
    }

    boolean setAutoTicking(boolean value) {
      simStateLock.lock(); try {
        if (_autoTicking == value)
          return false;
        _autoTicking = value;
        autoTickingUnsynchronized = value;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
        return true;
      } finally { simStateLock.unlock(); }
    }

    boolean setTickFrequency(double freq) {
      simStateLock.lock(); try {
        if (_autoTickFreq == freq)
          return false;
        Debug.println(1, "Auto-tick frequency set to " + freq);
        _autoTickFreq = freq;
        autoTickFreqUnsynchronized = freq;
        _autoTickNanos = freq <= 0 ? 0 : (long)Math.round(1e9 / (2*_autoTickFreq));
        _avgTickNanos = -1.0; // reset
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
        return true;
      } finally { simStateLock.unlock(); }
    }

    void requestStep() {
      simStateLock.lock(); try {
        _manualStepsRequested++;
        _autoPropagating = false;
        autoPropagatingUnsynchronized = false;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
      } finally { simStateLock.unlock(); }
    }

    void requestTick(int count) {
      simStateLock.lock(); try {
        _manualTicksRequested += count;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
      } finally { simStateLock.unlock(); }
    }

    void requestReset() {
      simStateLock.lock(); try {
        _resetRequested = true;
        _manualTicksRequested = 0;
        _manualStepsRequested = 0;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
      } finally { simStateLock.unlock(); }
    }

    boolean requestNudge() {
      simStateLock.lock(); try {
        if (!_autoPropagating)
          return false;
        _nudgeRequested = true;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
        return true;
      } finally { simStateLock.unlock(); }
    }

    void requestShutDown() {
      simStateLock.lock(); try {
        _complete = true;
        if (Thread.currentThread() != this)
          simStateUpdated.signalAll();
      } finally { simStateLock.unlock(); }
    }

    // private int cnt; // Debugging
    private boolean loop() {

      Propagator prop = null;
      boolean doReset = false;
      boolean doNudge = false;
      boolean doTick = false;
      boolean doTickIfStable = false;
      boolean doStep = false;
      boolean doProp = false;
      long now = 0;

      simStateLock.lock(); try {

        boolean ready = false;
        do { // while not ready
          
          if (_complete)
            return false;

          prop = _propagator;
          now = System.nanoTime();

          if (_resetRequested) {
            // System.out.println("reset requested");
            _resetRequested = false;
            doReset = true;
            doProp = _autoPropagating;
            _avgTickNanos = -1.0; // reset
            ready = true;
          } else if (_nudgeRequested) {
            // System.out.println("nudge requested");
            _nudgeRequested = false;
            doNudge = true;
            // _avgTickNanos = -1.0; // reset
            ready = true;
          } else if (_manualStepsRequested > 0) {
            // System.out.println("manual step requested");
            _manualStepsRequested--;
            doTickIfStable = _autoTicking;
            doStep = true;
            // _avgTickNanos = -1.0; // reset
            ready = true;
          } else if (_manualTicksRequested > 0) {
            // System.out.println("manual tick requested");
            // variable is decremented below
            doTick = true;
            doProp = _autoPropagating;
            doStep = !_autoPropagating;
            // _avgTickNanos = -1.0; // reset
            ready = true;
          } else {
            // wait, but perhaps not long (depending on auto-tick), so calculate deadline
            if (_autoTicking && _autoPropagating && _autoTickNanos > 0) {
              // see if it is time to do an auto-tick
              int k = _smoothingFactor;
              long lastNanos = now - lastTick;
              double avg;
              if (_avgTickNanos <= 0) {
                // don't wait, we just started auto-tick and have no baseline
                // simulation tick frequency history yet
                _avgTickNanos = _autoTickNanos;
                doTick = true;
                doProp = true;
                ready = true;
                // System.out.printf("k=%d, now = %s lastNanos = %s - %s = %s = avg, deadline = %s + %s = now+%s\n",
                //     k, displayTime(now), displayTime(now), displayTime(lastTick), displayDuration(lastNanos),
                //     displayTime(lastTick), displayDuration(_autoTickNanos), displayDuration(deadline-now));
              } else {
                // calculate deadline using
                // EWMA with factors 1/k and (k-1)/k
                avg = ((k - 1.0)/k) * _avgTickNanos + (1.0/k) * lastNanos;
                // Assume last k-1 ticks took about _avgTickNanos each,
                // so set deadline to make this tick bring the average over k
                // ticks be in line with target.
                long deadline = lastTick + _autoTickNanos -
                    (long)((k-1)*(_avgTickNanos - _autoTickNanos));
                // System.out.printf("k=%d, now = %s lastNanos = %s - %s = %s, avg = (k-1)/k*%s + 1/k*%s = %s, deadline = %s + %s - %s = now+%s\n",
                //     k, displayTime(now), displayTime(now), displayTime(lastTick), displayDuration(lastNanos), displayDuration(_avgTickNanos),
                //     displayDuration(lastNanos), displayDuration(avg),
                //     displayTime(lastTick), displayDuration(_autoTickNanos), displayDuration((long)((k-1)*(_avgTickNanos - _autoTickNanos))), displayDuration(deadline-now));
                long delta = deadline - now;
                if (delta <= 1000) {
                  // within 1 usec, close enough, dont wait
                  _avgTickNanos = avg;
                  doTick = true;
                  doProp = true;
                  ready = true;
                  // System.out.printf("missed by %10d, last = %10d, avg = %10.1f, goal = %10d, k = %d\n",
                  //     delta, lastNanos, _avgTickNanos, _autoTickNanos, k);
                } else if (delta < 1000000) {
                  // less than 1 ms, busy wait
                  simStateLock.unlock(); try {
                    long t;
                    do {
                      t = System.nanoTime();
                    } while (t < deadline);
                  } finally { simStateLock.lock(); }
                  // System.out.printf("busy   by %10d, last = %10d, avg = %10.1f, goal = %10d, k = %d\n",
                  //     delta, lastNanos, _avgTickNanos, _autoTickNanos, k);
                } else {
                  // longer delay, put thread to sleep
                  try { simStateUpdated.awaitNanos(delta); }
                  catch (InterruptedException e) { } // yes, we swallow the interrupt
                  // System.out.printf("ahead  by %10d, last = %10d, avg = %10.1f, goal = %10d, k = %d\n",
                  //     delta, lastNanos, _avgTickNanos, _autoTickNanos, k);
                }
              }
            } else {
              // System.out.printf("no work to do, awaiting update");
              // // not auto-ticking, so reset sim tick frequency history
              // and wait for update before trying again
              _avgTickNanos = -1.0; // reset
              try { simStateUpdated.await(); }
              catch (InterruptedException e) { } // yes, we swallow the interrupt
            }
          }
        } while (!ready);

      } finally { simStateLock.unlock(); }
      // DEBUGGING
      // //iSystem.out.printf("%d nudge %s tick %s prop %s step %s\n", cnt++, doNudge, doTick, doProp, doStep);
      
      exceptionEncountered = false; // volatile, but not synchronized

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
        // err.printStackTrace();
        Debug.error("simulator propagation", err);
      }

      if (doTick || (doTickIfStable && prop != null && !prop.isPending())) {
        lastTick = now;
        // System.out.printf("TICK: lastTick = now = %s\n", displayTime(lastTick));
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
        // err.printStackTrace();
        Debug.error("simulator propagation", err);
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
        // err.printStackTrace();
        Debug.error("simulator propagation", err);
      }
     
      osc = prop != null && prop.isOscillating();

      boolean clockDied = false;
      exceptionEncountered = oops; // volatile, but not synchronized
      oscillating = osc; // volatile, but not synchronized
      simStateLock.lock(); try {
        if (osc) {
          _autoPropagating = false;
          autoPropagatingUnsynchronized = false;
          _nudgeRequested = false;
        }
        if (ticked && _manualTicksRequested > 0)
          _manualTicksRequested--;
        if (_autoTicking && !hasClocks) {
          _autoTicking = false;
          autoTickingUnsynchronized = false;
          clockDied = true;
        }
      } finally { simStateLock.unlock(); }
   
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
          // e.printStackTrace();
          Debug.error("simulator thread", e);
          exceptionEncountered = true; // volatile, but not synchronized
          simStateLock.lock(); try {
            _autoPropagating = false;
            autoPropagatingUnsynchronized = false;
            _autoTicking = false;
            autoTickingUnsynchronized = false;
            _manualTicksRequested = 0;
            _manualStepsRequested = 0;
            _nudgeRequested = false;
          } finally { simStateLock.unlock(); }
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
    return simThread.getTickFrequencyUnsynchronized();
  }

  public boolean isExceptionEncountered() {
    return simThread.exceptionEncountered; // volatile, but not synchronized
  }

  public boolean isOscillating() {
    // Propagator prop = simThread.getPropagatorUnsynchronized();
    // return prop != null && prop.isOscillating();
    return simThread.oscillating;  // volatile, but not synchronized
  }

  public CircuitState getCircuitState() {
    Propagator prop = simThread.getPropagatorUnsynchronized();
    return prop == null ? null : prop.getRootState();
  }

  public boolean isAutoPropagating() {
    return simThread.isAutoPropagatingUnsynchronized();
  }

  public boolean isAutoTicking() {
    return simThread.isAutoTickingUnsynchronized();
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
