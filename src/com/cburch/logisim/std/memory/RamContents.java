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

package com.cburch.logisim.std.memory;

import java.lang.ref.WeakReference;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Debug;

public class RamContents extends MemContents {

  // RamContents holds the dimensions and bytes for Ram, plus additional state
  // needed to support viewing/editing in a separate HexFrame window and
  // triggering propagation.
  //
  // Changes come from:
  //  - HexFrame window
  //    [ these may trigger propagation ]
  //    editor --> setContents(...) --> set() --> fire
  //    editor --> clearContents(...) -->  clear() --> fire
  //  - poke tool
  //    [ same, but these always trigger propagation ]
  //  - popup menu
  //    [ same, but these always trigger propagation ]
  //  - simulation
  //    propagate -> simulatorSet() -> fire
  //    reset -> simulatorClear() -> fire
  //    [ this never triggers propagation ]
  //
  // When the bytes or dimensions change, we fire an event to listeners.
  // Currently, this triggers at most two events:
  // 1. Notifying HexFrame window, so it can redraw the window
  //    when changes are made from poke tool, popup, itself, or simulation
  //    This only happens if a HexFrame window is open.
  // 2. Calling state.queueForPropagation(), to trigger propagation.
  //    This only happens if this RamContents is used by an in-circuit Instance.
  //    For RamConents associated with an AddTool, this is skipped.
 
  private Project project; // used only for positioning HexFrame
  private WeakReference<HexFrame> hexFrameRef; // only if currently open
  private WeakReference<Instance> instanceRef;  // only if in circuit
  private WeakReference<CircuitState> circStateRef;  // only if in simulation

  public RamContents(int addrBits, int width) {
    super(addrBits, width);
    hexFrameRef = new WeakReference<>(null);
    instanceRef = new WeakReference<>(null);
    circStateRef = new WeakReference<>(null);
  }

  private RamContents(RamContents other) {
    super(other);
    hexFrameRef = new WeakReference<>(null);
    instanceRef = new WeakReference<>(null);
    circStateRef = new WeakReference<>(null);
  }
 
  public RamContents duplicate() {
    // the new RamContents won't yet have a circState, instance, or hexFrame
    return new RamContents(this);
  }

  public void setProject(Project project) {
    // Update project binding, used for positioning HexFrame
    if (this.project == project) {
      // System.err.println("WARN: ram no need to change project");
      return;
    }
    // if (this.project != null)
    //   System.err.println("WARN: ram changing project?");
    // if (project == null)
    //   System.err.println("WARN: ram losing project?");
    this.project = project;
  }

  public void setRamInstance(Instance instance) {
    // Update instance binding, only needed if we are in a circuit
    Instance oldInstance = instanceRef.get();
    if (oldInstance == instance) {
      // System.err.println("WARN: ram no need to change instance");
      return;
    }
    // if (oldInstance != null)
    //   System.err.println("WARN: ram changing instance?");
    // if (instance == null)
    //   System.err.println("WARN: ram losing instance?");
    instanceRef = new WeakReference<>(instance);
  }

  public void setRamCircState(CircuitState circState) {
    // Update circState binding, only needed if we are in a simulation
    CircuitState oldCircState = circStateRef.get();
    if (oldCircState == circState) {
      // System.err.println("WARN: ram no need to change circState");
      return;
    }
    // if (oldCircState != null)
    //   System.err.println("WARN: ram changing circState?");
    // if (circState == null)
    //   System.err.println("WARN: ram losing circState?");
    circStateRef = new WeakReference<>(circState);
  }

  @Override
  public HexFrame getHexFrame() {
    // Check if we have an existing hexframe window
    HexFrame hexFrame = hexFrameRef.get();
    if (hexFrame == null) {
      Instance instance = instanceRef.get();
      if (project == null)
        throw new IllegalStateException("missing project");
      // Create new hexframe window, retain reference
      // project is used here to create the window, required
      // instance is used for recent-file history, optional
      // this is the HexModel
      hexFrame = new HexFrame(project, instance, this);
      hexFrameRef = new WeakReference<>(hexFrame);
    }
    return hexFrame;
  }

  @Override
  public void clearHexFrameRef(Object hexFrame) {
    HexFrame prev = hexFrameRef.get();
    hexFrameRef = new WeakReference<>(null);
    if (prev != hexFrame)
      Debug.println(1, "ram - wrong hex frame closed?");
  }

  public void closeHexFrame() {
    HexFrame hexFrame = hexFrameRef.get();
    hexFrameRef = new WeakReference<>(null);
    if (hexFrame != null)
      hexFrame.closeAndDispose();
  }

  @Override
  protected void fireBytesChanged(boolean fromSimulation, long start, long count) {
    HexFrame hexFrame = hexFrameRef.get();
    if (hexFrame != null)
      hexFrame.getListener().bytesChanged(start, count);
    if (!fromSimulation) {
      Instance instance = instanceRef.get();
      CircuitState circState = circStateRef.get();
      if (instance != null && circState != null)
        circState.queueForPropagation(instance.getComponent());
    }
  }

  @Override
  protected void fireDimensionsChanged() {
    HexFrame hexFrame = hexFrameRef.get();
    if (hexFrame != null)
      hexFrame.getListener().dimensionsChanged();
    Instance instance = instanceRef.get();
    CircuitState circState = circStateRef.get();
    if (instance != null)
      instance.fireInvalidated();
  }

  // accessor methods called by simulation
  // makes change directly, and notifies hexframe (if open)
  
  void simulatorSet(long addr, int value) {
    set(true, addr, value);
  }

  void simulatorClear() {
    clear(true);
  }

  // accessor methods called by poke, menu, hexframe to make changes...
  // makes change directly,
  // and notifies hexframe (if open) and state propagation (if in simulation)
  
  @Override
  public void clearContents() {
    clear(false);
  }

  @Override
  public void clearContents(long start, long length) {
    clear(start, length);
  }
  
  @Override
  public void setContents(long start, int data) {
    set(false, start, data);
  }

  @Override
  public void setContents(long start, int[] data) {
    set(false, start, data);
  }

  @Override
  public void copyContents(long start, MemContents src, long offset, long count) {
    copyFrom(start, src, offset, count);
  }

}
