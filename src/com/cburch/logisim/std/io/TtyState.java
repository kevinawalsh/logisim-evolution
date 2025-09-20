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

package com.cburch.logisim.std.io;

import java.util.Arrays;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.start.TtyInterface;

class TtyState implements ComponentData {
  private Value lastClock;
  private String[] rowData;
  private int colCount;
  private char[] lastRow;
  private int row;
  private int col; // only for lastRow
  private boolean sendStdout;

  public TtyState(int rows, int cols) {
    lastClock = Value.UNKNOWN;
    rowData = new String[rows - 1];
    colCount = cols;
    lastRow = new char[colCount];
    sendStdout = false;
    clear();
  }

  TtyState(TtyState other) {
    lastClock = other.lastClock;
    rowData = other.rowData.clone();
    colCount = other.colCount;
    lastRow = other.lastRow.clone();
    row = other.row;
    col = other.col;
    sendStdout = other.sendStdout;
  }

  public void add(char c) {
    if (sendStdout) {
      TtyInterface.sendFromTty(c);
    }

    switch (c) {
    case 12: // control-L
      clear();
      break;
    case '\b': // backspace
      if (col > 0)
        lastRow[--col] = ' ';
      break;
    case '\n': // newline
      commit();
      break;
    case '\r': // carriage return
      col = 0;
      break;
    default:
      if (!Character.isISOControl(c)) {
        if (col == colCount)
          commit(); // wrap
        lastRow[col++] = c;
      }
    }
  }

  public void clear() {
    row = 0;
    Arrays.fill(rowData, "");
    col = 0;
    Arrays.fill(lastRow, ' ');
  }

  @Override
  public TtyState duplicateForNewSimulation() {
    return new TtyState(this);
  }

  private void commit() {
    if (row >= rowData.length) {
      System.arraycopy(rowData, 1, rowData, 0, rowData.length - 1);
      rowData[row - 1] = new String(lastRow);
    } else {
      rowData[row] = new String(lastRow);
      row++;
    }
    col = 0;
    Arrays.fill(lastRow, ' ');
  }

  public int getCursorColumn() {
    return col;
  }

  public int getCursorRow() {
    return row;
  }

  public String getRowString(int index) {
    if (index < row)
      return rowData[index];
    else if (index == row)
      return new String(lastRow);
    else
      return "";
  }

  public Value setLastClock(Value newClock) {
    Value ret = lastClock;
    lastClock = newClock;
    return ret;
  }

  public void setSendStdout(boolean value) {
    sendStdout = value;
  }

  public void updateSize(int rows, int cols) {
    int oldRows = rowData.length + 1;
    if (rows != oldRows) {
      String[] newData = new String[rows - 1];
      if (rows > oldRows // rows have been added,
          || row < rows - 1) { // or rows removed but filled rows fit
        System.arraycopy(rowData, 0, newData, 0, row);
        Arrays.fill(newData, row, rows - 1, "");
      } else { // rows removed, and some filled rows must go
        System.arraycopy(rowData, row - rows + 1, newData, 0, rows - 1);
        row = rows - 1;
      }
      rowData = newData;
    }

    int oldCols = colCount;
    if (cols != oldCols) {
      colCount = cols;
      if (col > colCount)
        col = colCount;
      char[] oldLastRow = lastRow;
      lastRow = new char[colCount];
      Arrays.fill(lastRow, ' ');
      System.arraycopy(oldLastRow, 0, lastRow, 0, Math.min(colCount, oldCols));
      if (cols < oldCols) { // will need to trim any overly-long rows
        for (int i = 0; i < rows - 1; i++) {
          String s = rowData[i];
          if (s.length() > cols)
            rowData[i] = s.substring(0, cols);
        }
      }
    }
  }
}
