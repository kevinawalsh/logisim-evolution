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

import java.util.Arrays;

class MemContentsSub {
  private final static class BytePage extends MemContents.Page {
    private byte[] data;

    BytePage(int size) {
      data = new byte[size];
    }

    BytePage(BytePage other) {
      data = new byte[other.data.length];
      System.arraycopy(other.data, 0, data, 0, other.data.length);
    }

    @Override
    public BytePage duplicate() {
      return new BytePage(this);
    }

    @Override
    void clear() {
      Arrays.fill(data, (byte) 0);
    }

    @Override
    int get(int addr) {
      return addr >= 0 && addr < data.length ? data[addr] : 0;
    }

    //
    // methods for accessing data within memory
    //
    @Override
    int getLength() {
      return data.length;
    }

    @Override
    void load(int start, int[] values, int mask) {
      int n = Math.min(values.length, data.length - start);
      for (int i = 0; i < n; i++) {
        data[start + i] = (byte) (values[i] & mask);
      }
    }

    @Override
    void set(int addr, int value) {
      if (addr >= 0 && addr < data.length) {
        byte oldValue = data[addr];
        if (value != oldValue) {
          data[addr] = (byte) value;
        }
      }
    }
  }

  private final static class IntPage extends MemContents.Page {
    private int[] data;

    IntPage(int size) {
      data = new int[size];
    }

    IntPage(IntPage other) {
      data = new int[other.data.length];
      System.arraycopy(other.data, 0, data, 0, other.data.length);
    }

    @Override
    public IntPage duplicate() {
      return new IntPage(this);
    }

    @Override
    void clear() {
      Arrays.fill(data, 0);
    }

    @Override
    int get(int addr) {
      return addr >= 0 && addr < data.length ? data[addr] : 0;
    }

    //
    // methods for accessing data within memory
    //
    @Override
    int getLength() {
      return data.length;
    }

    @Override
    void load(int start, int[] values, int mask) {
      int n = Math.min(values.length, data.length - start);
      for (int i = 0; i < n; i++) {
        data[start + i] = values[i] & mask;
      }
    }

    @Override
    void set(int addr, int value) {
      if (addr >= 0 && addr < data.length) {
        int oldValue = data[addr];
        if (value != oldValue) {
          data[addr] = value;
        }
      }
    }
  }

  private final static class ShortPage extends MemContents.Page {
    private short[] data;

    ShortPage(int size) {
      data = new short[size];
    }

    ShortPage(ShortPage other) {
      data = new short[other.data.length];
      System.arraycopy(other.data, 0, data, 0, other.data.length);
    }

    @Override
    public ShortPage duplicate() {
      return new ShortPage(this);
    }

    @Override
    void clear() {
      Arrays.fill(data, (short) 0);
    }

    @Override
    int get(int addr) {
      return addr >= 0 && addr < data.length ? data[addr] : 0;
    }

    //
    // methods for accessing data within memory
    //
    @Override
    int getLength() {
      return data.length;
    }

    @Override
    void load(int start, int[] values, int mask) {
      int n = Math.min(values.length, data.length - start);
      for (int i = 0; i < n; i++) {
        data[start + i] = (short) (values[i] & mask);
      }
    }

    @Override
    void set(int addr, int value) {
      if (addr >= 0 && addr < data.length) {
        short oldValue = data[addr];
        if (value != oldValue) {
          data[addr] = (short) value;
        }
      }
    }
  }

  static MemContents.Page createPage(int size, int bits) {
    if (bits <= 8)
      return new BytePage(size);
    else if (bits <= 16)
      return new ShortPage(size);
    else
      return new IntPage(size);
  }

  private MemContentsSub() {
  }
}
