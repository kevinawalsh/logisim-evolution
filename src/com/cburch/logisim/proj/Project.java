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

package com.cburch.logisim.proj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.cburch.hdl.HdlModel;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitLocker;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.file.ProjectsDirty;
import com.cburch.logisim.file.XmlProjectReader;
import com.cburch.logisim.gui.log.LogFrame;
// import com.cburch.logisim.std.hdl.VhdlSimulator;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.gui.test.TestFrame;
import com.cburch.logisim.gui.test.TestThread;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.EventSourceWeakSupport;

public class Project {
  private static class ActionData {
    CircuitState circuitState;
    HdlModel hdlModel;
    Action action;

    public ActionData(CircuitState circuitState, HdlModel hdlModel, Action action) {
      this.circuitState = circuitState;
      this.hdlModel = hdlModel;
      this.action = action;
    }
  }

  private class MyListener implements Selection.Listener, LibraryListener {
    public void libraryChanged(LibraryEvent event) {
      int action = event.getAction();
      if (action == LibraryEvent.REMOVE_LIBRARY) {
        Library unloaded = (Library) event.getData();
        if (tool != null && unloaded.containsFromSource(tool)) {
          setTool(null);
        }
      } else if (action == LibraryEvent.REMOVE_TOOL) {
        Object data = event.getData();
        if (data instanceof AddTool) {
          Object factory = ((AddTool) data).getFactory();
          if (factory instanceof SubcircuitFactory) {
            SubcircuitFactory fact = (SubcircuitFactory) factory;
            if (fact.getSubcircuit() == getCurrentCircuit()) {
              setCurrentCircuit(file.getMainCircuit());
            }
          }
        }
      }
    }

    public void selectionChanged(Selection.Event e) {
      fireEvent(ProjectEvent.ACTION_SELECTION, e.getSource());
    }
  }

  private static final int MAX_UNDO_SIZE = 64;

  private Simulator simulator = new Simulator();
  // private VhdlSimulator vhdlSimulator = null;

  private LogisimFile file;
  private HdlModel hdlModel;
  private CircuitState circuitState; // active sim state
  private HashMap<Circuit, CircuitState> recentRootState
      = new HashMap<>(); // most recent root sim state for each circuit
  private LinkedList<CircuitState> allRootStates
      = new LinkedList<>(); // all root sim states, in display order
  private Frame frame = null;
  private LogFrame logFrame = null;
  private TestFrame testFrame = null;
  private Tool tool = null;
  private LinkedList<ActionData> undoLog = new LinkedList<ActionData>();
  private int undoMods = 0;
  private LinkedList<ActionData> redoLog = new LinkedList<ActionData>();
  private EventSourceWeakSupport<ProjectListener> projectListeners = new EventSourceWeakSupport<>();
  private EventSourceWeakSupport<LibraryListener> fileListeners = new EventSourceWeakSupport<>();
  private EventSourceWeakSupport<CircuitListener> circuitListeners = new EventSourceWeakSupport<>();
  private Dependencies dependencies;
  private MyListener myListener = new MyListener();
  private boolean startupScreen = false;

  // Whether the user has approved external (http/https/mailto) links from this
  // project. Set to true when user clicks "Always trust" in the link confirmation
  // dialog. Reset to false on each file load (so it's in-memory only, not saved).
  private boolean externalLinksApproved = false;
  public boolean isExternalLinksApproved() { return externalLinksApproved; }
  public void setExternalLinksApproved(boolean approved) { externalLinksApproved = approved; }

  public Project(LogisimFile.FileWithSimulations file) {
    fileListeners.add(null, myListener);
    setLogisimFile(file);

    // this.vhdlSimulator = new VhdlSimulator(this);
  }

  public void addCircuitWeakListener(/*Object owner,*/ CircuitListener value) {
    circuitListeners.add(null, value);
    Circuit current = getCurrentCircuit();
    if (current != null)
      current.addCircuitWeakListener(null, value);
  }

  public void addLibraryWeakListener(/*Object owner,*/ LibraryListener value) {
    fileListeners.add(null, value);
    file.addLibraryWeakListener(null, value);
  }

  public void addProjectWeakListener(Object owner, ProjectListener what) {
    projectListeners.add(owner, what);
  }

  public boolean confirmClose(String title) {
    return frame.confirmClose(title);
  }

  private boolean referencedInUndoLog(CircuitState root) {
    for (ActionData data : undoLog) {
      CircuitState cs = data.circuitState;
      if (cs == null)
        continue;
      cs = cs.getAncestorState();
      if (cs == root)
        return true;
    }
    return false;
  }

  private boolean referencedInRedoLog(CircuitState root) {
    for (ActionData data : redoLog) {
      CircuitState cs = data.circuitState;
      if (cs == null)
        continue;
      cs = cs.getAncestorState();
      if (cs == root)
        return true;
    }
    return false;
  }

  private boolean referencedInRootStates(CircuitState root) {
    if (circuitState != null) {
      CircuitState cs = circuitState.getAncestorState();
      if (cs == root)
        return true;
    }
    for (CircuitState cs : allRootStates) {
      if (cs == null)
        continue;
      CircuitState other = cs.getAncestorState();
      if (other == root)
        return true;
    }
    for (CircuitState cs : recentRootState.values()) {
      if (cs == null)
        continue;
      CircuitState other = cs.getAncestorState();
      if (other == root)
        return true;
    }
    return false;
  }

  public void doAction(Action act) {
    if (act == null)
      return;
    Action toAdd = act;
    startupScreen = false;
    for (ActionData data : redoLog) {
      CircuitState cs = data.circuitState;
      if (cs == null)
        continue;
      cs = cs.getAncestorState();
      if (referencedInUndoLog(cs))
        continue;
      if (referencedInRootStates(cs))
        continue;
      CircuitState.markAsDefunct(cs);
    }
    redoLog.clear();

    if (!undoLog.isEmpty() && act.shouldAppendTo(getLastAction())) {
      ActionData firstData = undoLog.removeLast();
      Action first = firstData.action;
      --undoMods;
      toAdd = first.append(act);
      if (toAdd != null) {
        undoLog.add(new ActionData(circuitState, hdlModel, toAdd));
        ++undoMods;
      }
      // firstData was removed from undoLog, and we are about drop the
      // firstData.circuitState reference.
      CircuitState cs = firstData.circuitState;
      if (cs != null) {
        cs = cs.getAncestorState();
        if (!referencedInUndoLog(cs)
            && !referencedInRedoLog(cs)
            && !referencedInRootStates(cs))
          CircuitState.markAsDefunct(cs);
      }
      fireEvent(new ProjectEvent(ProjectEvent.ACTION_START, this, act));
      try {
        act.doIt(this);
      } catch (CircuitLocker.LockException e) {
        System.out.println("*** Circuit Lock Bug Diagnostics ***");
        System.out.println("This thread: " + Thread.currentThread());
        System.out.println("attempted to access without any locks:");
        System.out.printf("  circuit \"%s\" [lock serial: %d/%d]\n",
            e.getCircuit().getName(), e.getSerialNumber(),
            e.getCircuit().getLocker().getSerialNumber());
        System.out.println("  owned by thread: " + e.getMutatingThread());
        System.out.println("  with mutator: " + e.getCircuitMutator());
        throw e;
      }
      file.setDirty(isFileDirty());
      fireEvent(new ProjectEvent(ProjectEvent.ACTION_COMPLETE, this, act));
      fireEvent(new ProjectEvent(ProjectEvent.ACTION_MERGE, this, first, toAdd));
      return;
    }
    undoLog.add(new ActionData(circuitState, hdlModel, toAdd));
    fireEvent(new ProjectEvent(ProjectEvent.ACTION_START, this, act));
    try {
      act.doIt(this);
    } catch (CircuitLocker.LockException e) {
      System.out.println("*** Circuit Lock Bug Diagnostics ***");
      System.out.println("This thread: " + Thread.currentThread());
      System.out.println("attempted to access without any locks:");
      System.out.printf("  circuit \"%s\" [lock serial: %d/%d]\n",
          e.getCircuit().getName(), e.getSerialNumber(),
          e.getCircuit().getLocker().getSerialNumber());
      System.out.println("  owned by thread: " + e.getMutatingThread());
      System.out.println("  with mutator: " + e.getCircuitMutator());
      throw e;
    }
    while (undoLog.size() > MAX_UNDO_SIZE) {
      ActionData firstData = undoLog.removeFirst();
      // firstData was removed from undoLog, so we are about drop the
      // firstData.circuitState reference. May need to mark it as defunct now.
      CircuitState cs = firstData.circuitState;
      if (cs != null) {
        cs = cs.getAncestorState();
        if (!referencedInUndoLog(cs)
            && !referencedInRedoLog(cs)
            && !referencedInRootStates(cs))
          CircuitState.markAsDefunct(cs);
      }
    }
    ++undoMods;
    file.setDirty(isFileDirty());
    fireEvent(new ProjectEvent(ProjectEvent.ACTION_COMPLETE, this, act));
    ProjectsDirty.needsBackup(this);
  }

  public int doTestVector(String vectorname, String name) {
    Circuit circuit = (name == null ? file.getMainCircuit() : file.getCircuit(name));
    if (circuit == null) {
      System.err.println("Circuit '" + name + "' not found.");
      return -1;
    }
    setCurrentCircuit(circuit);
    return TestThread.doTestVector(this, circuit, vectorname);
  }

  private void fireEvent(int action, Object data) {
    fireEvent(new ProjectEvent(action, this, data));
  }

  private void fireEvent(int action, Object old, Object data) {
    fireEvent(new ProjectEvent(action, this, old, data));
  }

  private void fireEvent(ProjectEvent event) {
    for (ProjectListener l : projectListeners) {
      l.projectChanged(event);
    }
  }

  /**
   * Decide whether or not you can redo
   *
   * @return if we can redo
   */
  public boolean getCanRedo() {
    // If there's a redo option found, we can redo.
    return (redoLog.size() > 0);
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  public List<CircuitState> getRootCircuitStates() {
    return allRootStates;
  }

  public CircuitState getCircuitStateForPrinting(Circuit circuit) {
    // This is only used for printing and exporting things, so let's use the
    // current state, if it is the right circuit, even if it is not a root
    // state. Otherwise use the most recent root state, if there is one.
    // Otherwise, just make a fresh blank state, but don't record it as a new
    // simulation.
    if (circuitState != null && circuitState.getCircuit() == circuit)
      return circuitState;
    CircuitState ret = recentRootState.get(circuit);
    if (ret != null)
      return ret;
    return CircuitState.createRootState(this, circuit);
  }

  public void removeCircuitStateAncestor(CircuitState cs) {
    CircuitState root = cs.getAncestorState();
    Circuit circ = cs.getCircuit();
    allRootStates.remove(root);
    fireEvent(ProjectEvent.ACTION_DELETE_STATE, root);
    recentRootState.remove(root.getCircuit(), root);
    if (circuitState.getAncestorState() == root) {
      circuitState = null;
      // Current simulation was just removed
      if (!allRootStates.isEmpty())
        setCircuitState(allRootStates.get(0)); // Switch to existing simulation
      else
        setCurrentCircuit(circ); // Will create a new simulation
    }
    if (!referencedInUndoLog(root)
        && !referencedInRedoLog(root)
        && !referencedInRootStates(root)) {
      CircuitState.markAsDefunct(root);
    }
  }

  public Circuit getCurrentCircuit() {
    return circuitState == null ? null : circuitState.getCircuit();
  }

  public HdlModel getCurrentHdl() {
    return hdlModel;
  }

  public void setCurrentHdlModel(HdlModel hdl) {
    if (hdlModel == hdl)
      return;
    setTool(null);

    CircuitState old = circuitState;
    HdlModel oldHdl = hdlModel;

    // Canvas canvas = frame == null ? null : frame.getCanvas();
    // if (canvas != null) {
    //   if (tool != null)
    //     tool.deselect(canvas);
    //   Selection selection = canvas.getSelection();
    //   if (selection != null) {
    //     Action act = SelectionActions.dropAll(selection);
    //     if (act != null) {
    //       doAction(act);
    //     }
    //   }
    //   if (tool != null)
    //     tool.select(canvas);
    // }
    Circuit oldCircuit = old == null ? null : old.getCircuit();
    if (oldCircuit != null) {
      for (CircuitListener l : circuitListeners)
        oldCircuit.removeCircuitWeakListener(null, l);
    }

    circuitState = null;
    hdlModel = hdl;
    if (old != null) {
      CircuitState.transferActiveStatus(old, null);
      simulator.setCircuitState(null);
    }

    Object oldActive = old;
    if (oldHdl != null)
      oldActive = oldHdl;
    fireEvent(ProjectEvent.ACTION_SET_CURRENT, oldActive, hdl);
    if (old != null)
      fireEvent(ProjectEvent.ACTION_SET_STATE, old, null);
    if (oldCircuit != null)
      oldCircuit.displayChanged();
    if (oldHdl != null)
      oldHdl.displayChanged();
    hdl.displayChanged();
  }

  public Dependencies getDependencies() {
    return dependencies;
  }

  public Frame getFrame() {
    return frame;
  }

  public Action getLastAction() {
    return undoLog.isEmpty() ? null : undoLog.getLast().action;
  }

  public Action getLastRedoAction() {
    return redoLog.isEmpty() ? null : redoLog.getLast().action;
  }

  public LogFrame getLogFrame() {
    if (logFrame == null)
        logFrame = new LogFrame(this);
    return logFrame;
  }

  public LogisimFile getLogisimFile() {
    return file;
  }

  public void showError(String description, Throwable ...errs) {
    Errors.project(file.getName()).show(description, errs);
  }

  public Options getOptions() {
    return file.getOptions();
  }

  public void showSettingsFrame() {
    SettingsFrame.showProjectSettings(this);
  }

  public Selection getSelection() {
    if (frame == null)
      return null;
    Canvas canvas = frame.getCanvas();
    if (canvas == null)
      return null;
    return canvas.getSelection();
  }

  public Simulator getSimulator() {
    return simulator;
  }

  public TestFrame getTestFrame() {
    if (testFrame == null)
      testFrame = new TestFrame(this);
    return testFrame;
  }

  public Tool getTool() {
    return tool;
  }

  // public VhdlSimulator getVhdlSimulator() {
  //   return vhdlSimulator;
  // }

  public boolean isFileDirty() {
    return (undoMods > 0);
  }

  // We track whether this project is the empty project opened
  // at startup by default, because we want to close it
  // immediately as another project is opened, if there
  // haven't been any changes to it.
  public boolean isStartupScreen() {
    return startupScreen;
  }

  /**
   * Redo actions that were previously undone
   */
  public void redoAction() {
    // showUndoRedoLogs("before redo");
    // If there ARE things to undo...
    if (redoLog != null && redoLog.size() > 0) {
      // Add the last element of the undo log to the redo log
      undoLog.addLast(redoLog.getLast());
      ++undoMods;

      // Remove the last item in the redo log, but keep the data
      ActionData data = redoLog.removeLast();

      // Restore the circuit state to the redo's state
      if (data.circuitState != null)
        setCircuitState(data.circuitState);
      else if (data.hdlModel != null)
        setCurrentHdlModel(data.hdlModel);

      // Get the actions required to make that state change happen
      Action action = data.action;

      // Call the event
      fireEvent(new ProjectEvent(ProjectEvent.REDO_START, this, action));

      // Redo the action
      action.doIt(this);
      file.setDirty(isFileDirty());

      // Complete the redo
      fireEvent(new ProjectEvent(ProjectEvent.REDO_COMPLETE, this, action));
    }
    // showUndoRedoLogs("after redo");
  }

  public void removeCircuitWeakListener(/*Object owner,*/ CircuitListener value) {
    circuitListeners.remove(null, value);
    Circuit current = getCurrentCircuit();
    if (current != null)
      current.removeCircuitWeakListener(null, value);
  }

  public void removeLibraryWeakListener(/*Object owner,*/ LibraryListener value) {
    fileListeners.remove(null, value);
    file.removeLibraryWeakListener(null, value);
  }

  public void removeProjectWeakListener(Object owner, ProjectListener value) {
    projectListeners.remove(owner, value);
  }

  public void repaintCanvas() {
    // for actions that ought not be logged (i.e., those that
    // change nothing, except perhaps the current values within
    // the circuit)
    fireEvent(new ProjectEvent(ProjectEvent.REPAINT_REQUEST, this, null));
  }

  public void setCircuitState(CircuitState value) {
    if (value == null || circuitState == value)
      return;

    CircuitState old = circuitState;
    HdlModel oldHdl = hdlModel;
    Object oldActive = old;
    if (oldHdl != null)
      oldActive = oldHdl;

    Circuit oldCircuit = old == null ? null : old.getCircuit();
    Circuit newCircuit = value.getCircuit();
    boolean circuitChanged = old == null || oldCircuit != newCircuit;
    if (circuitChanged) {
      Canvas canvas = frame == null ? null : frame.getCanvas();
      if (canvas != null) {
        if (tool != null)
          tool.deselect(canvas);
        Selection selection = canvas.getSelection();
        if (selection != null) {
          Action act = SelectionActions.dropAll(selection);
          if (act != null) {
            doAction(act);
          }
        }
        if (tool != null)
          tool.select(canvas);
      }
      if (oldCircuit != null) {
        for (CircuitListener l : circuitListeners) {
          oldCircuit.removeCircuitWeakListener(null, l);
        }
      }
    }
    hdlModel = null;
    circuitState = value;
    CircuitState.transferActiveStatus(old, circuitState);
    if (!circuitState.isSubstate()) {
      if (!allRootStates.contains(circuitState)) {
        allRootStates.add(circuitState);
        fireEvent(ProjectEvent.ACTION_ADD_STATE, circuitState);
      }
      recentRootState.put(newCircuit, circuitState);
    }
    simulator.setCircuitState(circuitState);
    if (circuitChanged) {
      fireEvent(ProjectEvent.ACTION_SET_CURRENT, oldActive, newCircuit);
      if (newCircuit != null) {
        for (CircuitListener l : circuitListeners) {
          newCircuit.addCircuitWeakListener(null, l);
        }
      }
      if (oldCircuit != null)
        oldCircuit.displayChanged();
      if (oldHdl != null)
        oldHdl.displayChanged();
      newCircuit.displayChanged();
    }
    fireEvent(ProjectEvent.ACTION_SET_STATE, old, circuitState);
  }

  public void setCurrentCircuit(Circuit circuit) {
    CircuitState circState = recentRootState.get(circuit);
    if (circState == null)
      circState = CircuitState.createRootState(this, circuit);
    setCircuitState(circState);
  }

  public void setFileAsClean() {
    undoMods = 0;
    file.setDirty(isFileDirty());
  }

  public final long windowCreationTime = System.currentTimeMillis();

  public void setFrame(Frame value) {
    if (value == null || frame != null) {
      try { throw new IllegalStateException(String.format("set frame old=%s\nand frame new=%s\n", frame, value)); }
      catch (Exception e) { Debug.error("project setFrame", e); }
    }
    // Todo: simplify: oldValue should always be null here, new value always non-null.
    if (frame == value)
      return;
    Frame oldValue = frame;
    frame = value;
    Projects.windowCreated(this, oldValue, value);
    value.getCanvas().getSelection().addListener(myListener);
  }

  public void setLogisimFile(LogisimFile.FileWithSimulations value) {
    LogisimFile old = this.file; // old is only null during constructor
    if (old != null) {
      for (LibraryListener l : fileListeners) {
        old.removeLibraryWeakListener(null, l);
      }
    }
    file = value.file;
    HashSet<CircuitState> toBeDefunct = new HashSet<>();
    toBeDefunct.add(circuitState);
    circuitState = null;
    toBeDefunct.addAll(recentRootState.values());
    recentRootState.clear();
    toBeDefunct.addAll(allRootStates);
    allRootStates.clear();
    for (ActionData data : undoLog)
      toBeDefunct.add(data.circuitState);
    undoLog.clear();
    for (ActionData data : redoLog)
      toBeDefunct.add(data.circuitState);
    redoLog.clear();
    undoMods = 0;
    for (CircuitState cs : toBeDefunct) {
      if (cs != null)
        CircuitState.markAsDefunct(cs);
    }
    fireEvent(ProjectEvent.ACTION_CLEAR_STATES, null);
    // todo: close and dispose of orphaned ram hex window instances.
    dependencies = new Dependencies(file);
    fireEvent(ProjectEvent.ACTION_SET_FILE, old, file);

    ArrayList<String> simErrs = new ArrayList<>();
    value.simulations.forEach((circ, sims) ->  {
      sims.forEach(sim -> {
        CircuitState rootState = CircuitState.createRootState(this, circ);
        sim.forEach((path, attrs) -> {
          ArrayList<Component> cpath = XmlProjectReader.findComponent(circ, path);
          if (cpath == null) {
            simErrs.add(String.format("Component not found: %s", path));
            return; // continue sim.forEach
          }
          CircuitState circState = rootState;
          for (int i = 0; i < cpath.size()-1; i++) {
            Component comp = cpath.get(i);
            if (!(comp.getFactory() instanceof SubcircuitFactory)) {
              simErrs.add(String.format("Bad element %s of component path: %s", i, path));
              return; // continue sim.forEach
            }
            circState = circState.getCircuitSubstateFor(comp);
          }
          Component comp = cpath.get(cpath.size()-1);
          try {
            comp.getFactory().setNonVolatileSimulationState(comp, circState, attrs);
          } catch (Throwable e) {
            simErrs.add(String.format("Error restoring data for %s: %s", path, e.getMessage()));
          }
        });
        recentRootState.put(circ, rootState);
        allRootStates.add(rootState);
      });
    });
    if (!simErrs.isEmpty())
      showError(String.join("\n", simErrs));
    setCurrentCircuit(file.getMainCircuit());
    for (LibraryListener l : fileListeners) {
      file.addLibraryWeakListener(null, l);
    }

    file.setDirty(true); // toggle it so everybody hears the file is fresh
    file.setDirty(false);
  }

  public void setStartupScreen(boolean value) {
    startupScreen = value;
  }

  public void setTool(Tool value) {
    if (tool == value || hdlModel != null)
      return;
    Tool old = tool;
    Canvas canvas = frame.getCanvas();
    if (old != null)
      old.deselect(canvas);
    Selection selection = canvas.getSelection();
    if (selection != null && !selection.isEmpty()) {
      if (!(value instanceof SelectTool || value instanceof EditTool))
        doAction(SelectionActions.clear(selection));
    }
    startupScreen = false;
    tool = value;
    if (tool != null)
      tool.select(frame.getCanvas());
    fireEvent(ProjectEvent.ACTION_SET_TOOL, old, tool);
  }

  // void showUndoRedoLogs(String title) {
  //   System.out.println(title);
  //   int n;
  //   n = undoLog.size();
  //   if (n == 0) System.out.println("undo log: empty");
  //   else System.out.println("undo log: " + n  + " actions");
  //   for (int i = 0; i < n; i++)
  //     System.out.println(" " + (i+1)+". " + undoLog.get(i).action.getName());
  //   n = redoLog.size();
  //   if (n == 0) System.out.println("redo log: empty");
  //   else System.out.println("redo log: " + n  + " actions");
  //   for (int i = 0; i < n; i++)
  //     System.out.println(" " + (i+1)+". " + redoLog.get(i).action.getName());
  // }

  public void undoAction() {
    // showUndoRedoLogs("before undo");
    if (undoLog.size() > 0) {
      redoLog.addLast(undoLog.getLast());
      ActionData data = undoLog.removeLast();
      if (data.circuitState != null) {
        setCircuitState(data.circuitState);
      }
      else if (data.hdlModel != null)
        setCurrentHdlModel(data.hdlModel);
      Action action = data.action;
      --undoMods;
      fireEvent(new ProjectEvent(ProjectEvent.UNDO_START, this, action));
      action.undo(this);
      file.setDirty(isFileDirty());
      fireEvent(new ProjectEvent(ProjectEvent.UNDO_COMPLETE, this, action));
    }
    // showUndoRedoLogs("after undo");
  }

}
