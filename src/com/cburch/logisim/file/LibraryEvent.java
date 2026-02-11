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

package com.cburch.logisim.file;

import com.cburch.logisim.tools.Library;

public class LibraryEvent {
  public final static int ADD_TOOL = 0;
  public final static int REMOVE_TOOL = 1;
  public final static int MOVE_TOOL = 2;
  public final static int ADD_LIBRARY = 3;
  public final static int REMOVE_LIBRARY = 4;
  public final static int MOVE_LIBRARY = 5;
  public final static int SET_MAIN = 6;
  public final static int SET_NAME = 7;
  public static final int DIRTY_STATE = 8;
  public static final int NEEDS_BACKUP = 9;

  private Library source;
  private int action;
  private Object data, oldData;
  
  LibraryEvent(Library source, int action, Object data, Object oldData) {
    this.source = source;
    this.action = action;
    this.data = data;
    this.oldData = oldData;
  }

  LibraryEvent(Library source, int action, Object data) {
    this.source = source;
    this.action = action;
    this.data = data;
  }

  public int getAction() {
    return action;
  }

  public Object getData() {
    return data;
  }

  public Object getOldData() {
    return oldData;
  }

  public Library getSource() {
    return source;
  }

  @Override
  public String toString() {
    switch(action) {
    case ADD_TOOL: return "ADD_TOOL";
    case REMOVE_TOOL: return "REMOVE_TOOL";
    case MOVE_TOOL: return "MOVE_TOOL";
    case ADD_LIBRARY: return "ADD_LIBRARY";
    case REMOVE_LIBRARY: return "REMOVE_LIBRARY";
    case MOVE_LIBRARY: return "MOVE_LIBRARY";
    case SET_MAIN: return "SET_MAIN";
    case SET_NAME: return "SET_NAME";
    case DIRTY_STATE: return "DIRTY_STATE";
    case NEEDS_BACKUP: return "NEEDS_BACKUP";
    default: return "LibraryEvent<?>";
    }
  }

}
