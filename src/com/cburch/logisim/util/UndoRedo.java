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

package com.cburch.logisim.util;

import java.util.LinkedList;

public class UndoRedo {

  public static abstract class Action {
    public abstract String getName();
    public abstract void execute();
    public abstract void unexecute();
    public abstract boolean isEmpty();

    public boolean shouldAppendTo(Action other) {
      return false;
    }

    // Note: append may re-use/invalidate this action.
    // Returns null if combined act is effectively a no-op.
    public Action append(Action other) {
      throw new RuntimeException("not implemented");
      // return new JoinedAction(this, other);
    }

  }

  LinkedList<Action> undoLog = new LinkedList<>();
  LinkedList<Action> redoLog = new LinkedList<>();
  int maxUndoSize = 64;
  int dirty = 0;

  public UndoRedo() { }
  
  public boolean isDirty() { return (dirty > 0); }
  public void markAsClean() { dirty = 0; }

  public void clear() {
    undoLog.clear();
    redoLog.clear();
    dirty = 0;
  }

  public Action getUndoAction() {
    return undoLog.isEmpty() ? null : undoLog.getLast();
  }

  public Action getRedoAction() {
    return redoLog.isEmpty() ? null : redoLog.getLast();
  }

  public void doAction(Action act) {
    if (act == null || act.isEmpty())
      return;
    redoLog.clear();

    if (!undoLog.isEmpty() && act.shouldAppendTo(undoLog.getLast())) {
      // New action can be merged with previous action
      Action prev = undoLog.removeLast();
      --dirty;
      Action merged = prev.append(act); // prev may be invalidated here
      if (merged != null) {
        undoLog.add(merged);
        ++dirty;
      }
    } else {
      undoLog.add(act);
      while (undoLog.size() > maxUndoSize) {
        undoLog.removeFirst();
      }
      ++dirty;
    }
    act.execute();
  }

  public void undoAction() {
    if (!undoLog.isEmpty()) {
      Action act = undoLog.removeLast();
      redoLog.addLast(act);
      --dirty;
      act.unexecute();
    }
  }

  public void redoAction() {
    if (!redoLog.isEmpty()) {
      Action act = redoLog.removeLast();
      undoLog.addLast(act);
      ++dirty;
      act.execute();
    }
  }

}
