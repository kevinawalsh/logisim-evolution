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

package com.cburch.logisim.gui.hex;

import com.cburch.logisim.std.memory.MemContents;

public class HexContents extends MemContents {

  // HexContents holds the dimensions and bytes for HexReader, with no support
  // for viewing/editing in a hexframe or trigggering propagation.
 
  public HexContents(int addrBits, int width) {
    super(addrBits, width);
  }

  @Override
  public HexFrame getHexFrame() { return null; }
 
  @Override
  public void clearHexFrameRef(Object hexFrame) { }

  @Override
  public void closeHexFrame() { }

  @Override
  protected void fireBytesChanged(boolean fromSimulation, long start, long count) { }

  @Override
  protected void fireDimensionsChanged() { }

  // accessor methods called by HexFile...
  // makes change directly, no notifications
  
  @Override
  public void clearContents() { clear(true); }

  @Override
  public void clearContents(long start, long length) { clear(start, length); }
  
  @Override
  public void setContents(long start, int data) { set(true, start, data); }

  @Override
  public void setContents(long start, int[] data) { set(true, start, data); }

  @Override
  public void copyContents(long start, MemContents src, long offset, long count) { copyFrom(start, src, offset, count); }

}
