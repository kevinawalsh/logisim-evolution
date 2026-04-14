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

public abstract class Action {

  // Note: if doIt(proj) depends on current selection, or current selected
  // circuit, or similar non-action state, then redo() must be overriden to
  // perform the action in way that does not depend on those transient things.
  public abstract void doIt(Project proj);

  public abstract String getName();

  public boolean shouldAppendTo(Action other) { return false; }

  // Append can return null if the combined actions are effectively a no-op and
  // both should be removed from the undo/log.
  public Action append(Action other) { return new JoinedAction(this, other); }

  // FIXME: merging actions indiscriminently is probably not ideal. Currently,
  // only a few actions support merging at all, and some of those that do seem
  // overly aggressive, losing meaningful intermediate state. Moving components,
  // then moving them again... why should that be merged into one action? And
  // why would we consider that "no action" and remove the undo entirely if the
  // two moves happen to cancel each other?

  public boolean isEmpty() { return false; }

  public abstract void undo(Project proj);

  public void redo(Project proj) {
    doIt(proj);
  }
}
