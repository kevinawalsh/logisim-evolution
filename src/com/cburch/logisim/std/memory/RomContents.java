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
import static com.cburch.logisim.std.Strings.S;

import java.lang.ref.WeakReference;

import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

public class RomContents extends MemContents {

  // RomContents holds the dimensions and bytes for Rom, plus additional state
  // needed to support viewing/editing in a separate HexFrame window, adding
  // actions to the project undo/redo stack, and triggering propagation.
  //
  // Changes come from:
  //  - HexFrame window
  //    [ these may trigger propagation, and always require an Action ]
  //    editor --> setContents(...) --> Action --> set() --> fire
  //    editor --> clearContents(...) -->  Action --> clear() --> fire
  //  - poke tool
  //    [ same, but these always trigger propagation ]
  //  - popup menu
  //    [ same, but these always trigger propagation ]
  //
  // When the bytes or dimensions change, we fire an event to listeners.
  // Currently, this triggers at most two events:
  // 1. Notifying HexFrame window, so it can redraw the window
  //    when changes are made from poke tool, popup, or itself.
  //    This only happens if a HexFrame window is open.
  // 2. Calling instance.fireInvalidated(), to trigger propagation.
  //    This only happens if this RomContents is used by an in-circuit Instance.
  //    For RomConents associated with an AddTool, this is skipped.
 
  private Project project; // used for Action, which is always required
  private WeakReference<HexFrame> hexFrameRef; // only if currently open
  private WeakReference<Instance> instanceRef;  // only if in circuit

  public RomContents(int addrBits, int width) {
    super(addrBits, width);
    hexFrameRef = new WeakReference<>(null);
    instanceRef = new WeakReference<>(null);
  }

  private RomContents(RomContents other) {
    super(other);
    hexFrameRef = new WeakReference<>(null);
    instanceRef = new WeakReference<>(null);
  }
 
  public RomContents duplicate() {
    // the new RomContents won't yet have an instance or hexFrame
    return new RomContents(this);
  }

  public void setProject(Project project) {
    // Update project binding, always needed to create Action during changes
    if (this.project == project) {
      // System.err.println("WARN: rom no need to change project");
      return;
    }
    if (this.project != null)
      System.err.println("WARN: rom changing project?");
    if (project == null)
      System.err.println("WARN: rom losing project?");
    this.project = project;
  }

  public void setRomInstance(Instance instance) {
    // Update instance binding, only needed if we are in a circuit
    Instance oldInstance = instanceRef.get();
    if (oldInstance == instance) {
      // System.err.println("WARN: rom no need to change instance");
      return;
    }
    if (oldInstance != null)
      System.err.println("WARN: rom changing instance?");
    if (instance == null)
      System.err.println("WARN: rom losing instance?");
    instanceRef = new WeakReference<>(instance);
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

  @Override public void clearHexFrameRef(Object hexFrame) {
    HexFrame prev = hexFrameRef.get();
    if (prev == null)
      return; // already cleared
    hexFrameRef = new WeakReference<>(null);
    if (prev != hexFrame)
      System.err.println("rom - wrong hex frame closed?");
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
    Instance instance = instanceRef.get();
    if (instance != null)
      instance.fireInvalidated();
  }

  @Override
  protected void fireDimensionsChanged() {
    HexFrame hexFrame = hexFrameRef.get();
    if (hexFrame != null)
      hexFrame.getListener().dimensionsChanged();
    Instance instance = instanceRef.get();
    if (instance != null)
      instance.fireInvalidated();
  }

  // accessor methods called by poke, menu, hexframe to make changes...
  // creates Action, which then makes change and
  // notifies hexframe (if open) and instance propagation (if in circuit)
  
  @Override
  public void clearContents() {
    System.out.println("rom clearContents as action");
    if (isAllZeros())
      return;
    if (project != null)
      project.doAction(new ClearAll(instanceRef.get(), this));
    else
      System.out.println("set direct here, probably setting on a tool?");
  }

  @Override
  public void clearContents(long start, long length) {
    System.out.println("rom clearContents as action");
    if (project != null)
      project.doAction(new ClearRange(instanceRef.get(), this, start, length));
    else
      System.out.println("set direct here, probably setting on a tool?");
  }
  
  @Override
  public void setContents(long start, int data) {
    System.out.println("rom setContent as action");
    if (project != null)
      project.doAction(new ChangeBytes(instanceRef.get(), this, start, null, new int[] { data }));
    else {
      System.out.println("set direct here, probably setting on a tool?");
      Thread.dumpStack();
    }
  }

  @Override
  public void setContents(long start, int[] data) {
    System.out.println("rom setContent as action");
    if (project != null)
      project.doAction(new ChangeBytes(instanceRef.get(), this, start, null, data));
    else {
      System.out.println("set direct here, probably setting on a tool?");
      Thread.dumpStack();
    }
  }

  @Override
	public void copyContents(long start, MemContents src, long offset, long count) {
    // If new data is under 32KB, use ChangeBytes with array to hold old data,
    // otherwise use CopyContents which uses duplication for old data.
    System.out.println("rom copyContents as action");
    if (project != null) {
      if (count <= 32*1024) {
        int[] data = src.get(offset, count);
        project.doAction(new ChangeBytes(instanceRef.get(), this, start, null, data));
      } else {
        project.doAction(new CopyContents(instanceRef.get(), this, start, src, offset, count));
      }
    } else {
      System.out.println("set direct here, probably setting on a tool or loading xml?");
      Thread.dumpStack();
      copyFrom(start, src, offset, count);
    }
  }

  // Actions push changes to underlying bytes 
  // private static void _clear(RomContents contents) {
  //   contents.clear();
  // }
  
  private static class ClearAll extends Action {
    private Instance instance;
    private RomContents contents;
    private RomContents oldContents;

    ClearAll(Instance instance, RomContents contents) {
      this.instance = instance;
      this.contents = contents;
      this.oldContents = contents.duplicate();
    }

    @Override
    public void doIt(Project proj) {
      contents.clear(false);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public void undo(Project proj) {
      contents.copyFrom(0, oldContents, 0, oldContents.getLogLength());
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public String getName() {
      return S.get("romClearContentsAction");
    }
  }

  private static class ClearRange extends Action {
    private Instance instance;
    private RomContents contents;
    private int[] oldValues;
    long start, length;

    ClearRange(Instance instance, RomContents contents, long start, long length) {
      this.instance = instance;
      this.contents = contents;
      this.start = start;
      this.length = length;
      this.oldValues = contents.get(start, length);
    }

    @Override
    public void doIt(Project proj) {
      contents.clear(start, length);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public void undo(Project proj) {
      contents.set(false, start, oldValues);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public String getName() {
      return S.get("romChangeAction");
    }
  }

  private static class ChangeBytes extends Action {
    private Instance instance;
    private RomContents contents;
    private long start;
    private int[] oldValues;
    private int[] newValues;

    ChangeBytes(Instance instance, RomContents contents, long start, int[] oldValues, int[] newValues) {
      this.instance = instance;
      this.contents = contents;
      this.start = start;
      this.newValues = newValues;
      if (oldValues == null)
        oldValues = contents.get(start, newValues.length);
      this.oldValues = oldValues;
    }

    @Override
    public boolean shouldAppendTo(Action other) {
      if (other instanceof ChangeBytes) {
        ChangeBytes o = (ChangeBytes) other;
        long oEnd = o.start + o.newValues.length;
        long end = start + newValues.length;
        // Only merge edits if consecutive, same underlying contents, and same
        // instance (or both have no instance). So if user edits rom in one
        // simulation, then edits iame rom in a different simulation, those
        // don't get merged.
        if (o.instance == instance && o.contents == contents && oEnd >= start && end >= o.start)
          return true;
      }
      return super.shouldAppendTo(other);
    }

    @Override
    public Action append(Action other) {
      if (other instanceof ChangeBytes) {
        ChangeBytes o = (ChangeBytes) other;
        long oEnd = o.start + o.newValues.length;
        long end = start + newValues.length;
        if (oEnd >= start && end >= o.start) {
          long nStart = Math.min(start, o.start);
          long nEnd = Math.max(end, oEnd);
          int[] nOld = new int[(int) (nEnd - nStart)];
          int[] nNew = new int[(int) (nEnd - nStart)];
          System.arraycopy(o.oldValues, 0, nOld,
              (int) (o.start - nStart), o.oldValues.length);
          System.arraycopy(oldValues, 0, nOld,
              (int) (start - nStart), oldValues.length);
          System.arraycopy(newValues, 0, nNew,
              (int) (start - nStart), newValues.length);
          System.arraycopy(o.newValues, 0, nNew,
              (int) (o.start - nStart), o.newValues.length);
          return new ChangeBytes(instance, contents, nStart, nOld, nNew);
        }
      }
      return super.append(other);
    }

    @Override
    public void doIt(Project proj) {
      contents.set(false, start, newValues);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public void undo(Project proj) {
      contents.set(false, start, oldValues);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public String getName() {
      return S.get("romChangeAction");
    }
  }

  private static class CopyContents extends Action {
    private Instance instance;
    private RomContents contents;
    private MemContents src;
    private RomContents oldContents;
    long start, offset, count;

    CopyContents(Instance instance, RomContents contents, long start, MemContents src, long offset, long count) {
      this.instance = instance;
      this.contents = contents;
      this.src = src;
      this.oldContents = contents.duplicate();
    }

    @Override
    public void doIt(Project proj) {
      contents.copyFrom(start, src, offset, count);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public void undo(Project proj) {
      contents.copyFrom(oldContents);
      if (instance != null)
        instance.fireInvalidated();
    }

    @Override
    public String getName() {
      return S.get("romLoadAction");
    }
  }

}
