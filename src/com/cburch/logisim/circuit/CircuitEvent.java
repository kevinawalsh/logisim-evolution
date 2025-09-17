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

public class CircuitEvent {
  public final static int ACTION_SET_NAME = 0; // data=String name changed
  public final static int ACTION_ADD = 1; // data=Component added
  public final static int ACTION_REMOVE = 2; // data=Component removed
  // public final static int ACTION_CHANGE = 3; // data=Component changed (unused)
  public final static int ACTION_INVALIDATE = 4; // data=Component invalidated (pin types changed)
  public final static int ACTION_CLEAR = 5; // data=Set<Component> entire circuit cleared
  public final static int TRANSACTION_DONE = 6; // data=ReplacementSet done series of add/remove 
  public final static int ACTION_DISPLAY_CHANGE = 7; // data=null viewed/haloed status change

  private int action;
  private Circuit circuit;
  private Object data;

  CircuitEvent(int action, Circuit circuit, Object data) {
    this.action = action;
    this.circuit = circuit;
    this.data = data;
  }

  // access methods
  public int getAction() {
    return action;
  }

  public Circuit getCircuit() {
    return circuit;
  }

  public Object getData() {
    return data;
  }

  public CircuitTransactionResult getResult() {
    return (CircuitTransactionResult) data;
  }

  public String toString() {
    String s;
    switch (action) {
    case ACTION_SET_NAME : s = "ACTION_SET_NAME"; break;
    case ACTION_ADD : s = "ACTION_ADD"; break;
    case ACTION_REMOVE : s = "ACTION_REMOVE"; break;
    // case ACTION_CHANGE : s = "ACTION_CHANGE"; break;
    case ACTION_INVALIDATE : s = "ACTION_INVALIDATE"; break;
    case ACTION_CLEAR : s = "ACTION_CLEAR"; break;
    case TRANSACTION_DONE : s = "TRANSACTION_DONE"; break;
    case ACTION_DISPLAY_CHANGE : s = "ACTION_DISPLAY_CHANGE"; break;
    default: s = "UNKNOWN_ACTION(" + action + ")"; break;
    }
    return s + "{\n  circuit=" +  circuit + "\n  data=" + data +"\n}";
  }
}
