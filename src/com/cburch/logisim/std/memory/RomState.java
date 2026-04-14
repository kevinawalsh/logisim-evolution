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

import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Action;

class RomState extends MemState {

  RomState(MemContents contents) {
    super(contents);
  }

  RomState(RomState other) {
    super(other);
  }

  public void setContents(MemContents newContents) {
    if (contents == newContents) return;
    System.out.println("rom contents changed?");
    contents.removeHexModelWeakListener(null, this);
    contents = newContents;
    contents.addHexModelWeakListener(null, this);
    setBits(contents.getLogLength(), contents.getWidth());
  }

  @Override
  public RomState duplicateForNewSimulation() {
    return new RomState(this);
  }

  @Override
  void clearContents(InstanceState state) {
    System.out.println("rom clearContents as action");
    if (contents.isAllZeros())
      return;
    // Circuit circ = state.getCircuitState().getCircuit();
    // MemContents newContents = MemContents.create(oldContents.getLogLength(), oldContents.getWidth());
    // CircuitMutation xn = CircuitMutation.forCircuit(circ);
    // xn.set(state.getInstance().getComponent(), Rom.CONTENTS_ATTR, newContents);
    // proj.doAction(xn.toAction(S.getter("romClearContentsAction")));
    state.getProject().doAction(new ClearBytes(state.getInstance(), contents));
  }

  @Override
  void setContentBytes(InstanceState state, long start, int[] data) {
    System.out.println("rom setContentBytes as action");
    state.getProject().doAction(new ChangeBytes(state.getInstance(), contents, start, null, data));
  }

  private static class ClearBytes extends Action {
    private Instance instance;
    private MemContents contents;
    private MemContents oldContents;

    ClearBytes(Instance instance, MemContents contents) {
      this.instance = instance;
      this.contents = contents;
      this.oldContents = contents.duplicate();
    }

    @Override
    public void doIt(Project proj) {
      contents.clear();
      instance.fireInvalidated();
    }

    @Override
    public void undo(Project proj) {
      contents.copyFrom(0, oldContents, 0, oldContents.getLogLength());
      instance.fireInvalidated();
    }

    @Override
    public String getName() {
      return S.get("romClearContentsAction");
    }
  }

  private static class ChangeBytes extends Action {
    private Instance instance;
    private MemContents contents;
    private long start;
    private int[] oldValues;
    private int[] newValues;

    ChangeBytes(Instance instance, MemContents contents, long start, int[] oldValues, int[] newValues) {
      this.instance = instance;
      this.contents = contents;
      this.start = start;
      this.newValues = newValues;
      if (oldValues == null) {
        oldValues = new int[newValues.length];
        for (int i = 0; i < oldValues.length; i++)
          oldValues[i] = contents.get(start + i);
      }
      this.oldValues = oldValues;
    }

    @Override
    public boolean shouldAppendTo(Action other) {
      if (other instanceof ChangeBytes) {
        ChangeBytes o = (ChangeBytes) other;
        long oEnd = o.start + o.newValues.length;
        long end = start + newValues.length;
        if (o.instance == instance && oEnd >= start && end >= o.start)
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
      contents.set(start, newValues);
      instance.fireInvalidated();
    }

    @Override
    public void undo(Project proj) {
      contents.set(start, oldValues);
      instance.fireInvalidated();
    }

    @Override
    public String getName() {
      return S.get("romChangeAction");
    }
  }

}
