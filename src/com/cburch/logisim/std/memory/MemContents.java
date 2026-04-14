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

import com.cburch.hex.HexModel;
import com.cburch.hex.HexModelListener;
import com.cburch.logisim.util.WeakList;

public abstract class MemContents {

  private static final int PAGE_SIZE_BITS = 12;
  private static final int PAGE_SIZE = 1 << PAGE_SIZE_BITS;

  private static final int PAGE_MASK = PAGE_SIZE - 1;

  // MemContents holds the bytes and dimensions for RAM and ROM.
  // Changes may come from, e.g.
  //  - HexFrame/HexEditor windows
  //    [ triggers propagation, and may require Action]
  //    editor --> MemContents.set() // FIXME - should go through state
  //    editor --> MemContents.fill() // FIXME - should go through state
  //  - poke tool
  //    [ triggers propagation, and may require Action]
  //    poke rom --> RomState.setContentBytes --> Action --> MemContents.set()
  //    poke rom --> RomState.clearContents --> Action --> MemContents.clear()
  //    poke ram --> RamState.setContentBytes --> MemContents.set()
  //    poke ram --> RamState.clearContents --> MemContents.clear()
  //  - popup menu
  //    [ triggers propagation, and may require Action]
  //    rom menu --> RomState.clearContents --> action --> MemContents.clear()
  //    ram menu --> RamState.clearContents --> MemContents.clear()
  //  - propagation (ram only)
  //    [ no propagation, no Action ]
  //    write --> MemContents.set()
  //
  // When the bytes or dimensions change, we fire an event to listeners. Only
  // HexEditor windows listening for changes, e.g. it can redraw the window when
  // changes are made from poke tool, popup, or propagation. Previously there
  // was a complicated tangle of listeners and callbacks to create an Action and
  // trigger propagation when various events happen, but this was cumbersome and
  // fragile.
  private WeakList<HexModelListener> listeners;

  private int width;
  private int addrBits;
  private int mask;
  private Page[] pages;

  private MemContents(int addrBits, int width) {
    listeners = new WeakList<>();
    setDimensions(addrBits, width);
  }

  private MemContents(MemContents other) {
    listeners = new WeakList<>();
    width = other.width;
    addrBits = other.addrBits;
    mask = other.mask;
    pages = new Page[other.pages.length];
    for (int i = 0; i < pages.length; i++) {
      if (other.pages[i] != null) {
        pages[i] = other.pages[i].duplicate();
      }
    }
  }
  
  public MemContents duplicate() {
    return new MemContents(this);
  }

  public void clearDirect() {
    for (int i = 0; i < pages.length; i++) {
      if (pages[i] != null) {
        if (pages[i] != null)
          clearPage(i);
      }
    }
  }

  private void clearPage(int index) {
    Page page = pages[index];
    int[] oldValues = new int[page.getLength()];
    boolean changed = false;
    for (int j = 0; j < oldValues.length; j++) {
      int val = page.get(j) & mask;
      oldValues[j] = val;
      if (val != 0)
        changed = true;
    }
    if (changed) {
      pages[index] = null;
      fireBytesChanged(index << PAGE_SIZE_BITS, oldValues.length, oldValues);
    }
  }

  private void ensurePage(int index) {
    if (pages[index] == null) {
      pages[index] = MemContentsSub.createPage(PAGE_SIZE, width);
    }
  }

  public void fill(long start, long len, int value) {
    if (len == 0)
      return;

    int pageStart = (int) (start >>> PAGE_SIZE_BITS);
    int startOffs = (int) (start & PAGE_MASK);
    int pageEnd = (int) ((start + len - 1) >>> PAGE_SIZE_BITS);
    int endOffs = (int) ((start + len - 1) & PAGE_MASK);
    value &= mask;

    if (pageStart == pageEnd) {
      ensurePage(pageStart);
      int[] vals = new int[(int) len];
      Arrays.fill(vals, value);
      Page page = pages[pageStart];
      if (!page.matches(vals, startOffs, mask)) {
        int[] oldValues = page.get(startOffs, (int) len);
        page.load(startOffs, vals, mask);
        if (value == 0 && page.isClear())
          pages[pageStart] = null;
        fireBytesChanged(start, len, oldValues);
      }
    } else {
      if (startOffs == 0) {
        pageStart--;
      } else {
        if (value == 0 && pages[pageStart] == null) {
          // nothing to do
        } else {
          ensurePage(pageStart);
          int[] vals = new int[PAGE_SIZE - startOffs];
          Arrays.fill(vals, value);
          Page page = pages[pageStart];
          if (!page.matches(vals, startOffs, mask)) {
            int[] oldValues = page.get(startOffs, vals.length);
            page.load(startOffs, vals, mask);
            if (value == 0 && page.isClear())
              pages[pageStart] = null;
            fireBytesChanged(start, PAGE_SIZE - pageStart,
                oldValues);
          }
        }
      }
      if (value == 0) {
        for (int i = pageStart + 1; i < pageEnd; i++) {
          if (pages[i] != null)
            clearPage(i);
        }
      } else {
        int[] vals = new int[PAGE_SIZE];
        Arrays.fill(vals, value);
        for (int i = pageStart + 1; i < pageEnd; i++) {
          ensurePage(i);
          Page page = pages[i];
          if (!page.matches(vals, 0, mask)) {
            int[] oldValues = page.get(0, PAGE_SIZE);
            page.load(0, vals, mask);
            fireBytesChanged((long) i << PAGE_SIZE_BITS, PAGE_SIZE,
                oldValues);
          }
        }
      }
      if (endOffs >= 0) {
        Page page = pages[pageEnd];
        if (value == 0 && page == null) {
          // nothing to do
        } else {
          ensurePage(pageEnd);
          int[] vals = new int[endOffs + 1];
          Arrays.fill(vals, value);
          if (!page.matches(vals, 0, mask)) {
            int[] oldValues = page.get(0, endOffs + 1);
            page.load(0, vals, mask);
            if (value == 0 && page.isClear())
              pages[pageEnd] = null;
            fireBytesChanged((long) pageEnd << PAGE_SIZE_BITS, endOffs + 1, oldValues);
          }
        }
      }
    }
  }
  
  public void addHexModelWeakListener(Object owner, HexModelListener l) {
    listeners.add(owner, l);
  }

  public void removeHexModelWeakListener(Object owner, HexModelListener l) {
    listeners.remove(owner, l);
  }

  private void fireBytesChanged(long start, long numBytes, int[] oldValues) {
    for (HexModelListener l : listeners)
      l.bytesChanged(this, start, numBytes, oldValues);
  }

  private void fireMetainfoChanged() {
    for (HexModelListener l : listeners)
      l.metainfoChanged(this);
  }

  public int get(long addr) {
    int page = (int) (addr >>> PAGE_SIZE_BITS);
    int offs = (int) (addr & PAGE_MASK);
    if (page < 0 || page >= pages.length || pages[page] == null)
      return 0;
    return pages[page].get(offs) & mask;
  }

  public long getFirstOffset() {
    return 0;
  }

  public long getLastOffset() {
    return (1L << addrBits) - 1;
  }

  public int getLogLength() {
    return addrBits;
  }

  public int getValueWidth() {
    return width;
  }

  public int getWidth() {
    return width;
  }

  public boolean isClear() {
    for (int i = 0; i < pages.length; i++) {
      Page page = pages[i];
      if (page != null) {
        for (int j = page.getLength() - 1; j >= 0; j--) {
          if (page.get(j) != 0)
            return false;
        }
      }
    }
    return true;
  }

  abstract void set(long addr, int value) {

  public void setDirect(long addr, int value) {
    int page = (int) (addr >>> PAGE_SIZE_BITS);
    int offs = (int) (addr & PAGE_MASK);
    if (page < 0 || page >= pages.length)
      return;
    int old = pages[page] == null ? 0 : pages[page].get(offs) & mask;
    int val = value & mask;
    if (old != val) {
      if (pages[page] == null) {
        pages[page] = MemContentsSub.createPage(PAGE_SIZE, width);
      }
      pages[page].set(offs, val);
      fireBytesChanged(addr, 1, new int[] { old });
    }
  }

  public void setDirect(long start, int[] values) {
    if (values.length == 0)
      return;

    int pageStart = (int) (start >>> PAGE_SIZE_BITS);
    int startOffs = (int) (start & PAGE_MASK);
    int pageEnd = (int) ((start + values.length - 1) >>> PAGE_SIZE_BITS);
    int endOffs = (int) ((start + values.length - 1) & PAGE_MASK);

    if (pageStart == pageEnd) {
      ensurePage(pageStart);
      Page page = pages[pageStart];
      if (!page.matches(values, startOffs, mask)) {
        int[] oldValues = page.get(startOffs, values.length);
        page.load(startOffs, values, mask);
        if (page.isClear())
          pages[pageStart] = null;
        fireBytesChanged(start, values.length, oldValues);
      }
    } else {
      int nextOffs;
      if (startOffs == 0) {
        pageStart--;
        nextOffs = 0;
      } else {
        ensurePage(pageStart);
        int[] vals = new int[PAGE_SIZE - startOffs];
        System.arraycopy(values, 0, vals, 0, vals.length);
        Page page = pages[pageStart];
        if (!page.matches(vals, startOffs, mask)) {
          int[] oldValues = page.get(startOffs, vals.length);
          page.load(startOffs, vals, mask);
          if (page.isClear())
            pages[pageStart] = null;
          fireBytesChanged(start, PAGE_SIZE - pageStart, oldValues);
        }
        nextOffs = vals.length;
      }
      int[] vals = new int[PAGE_SIZE];
      int offs = nextOffs;
      for (int i = pageStart + 1; i < pageEnd; i++, offs += PAGE_SIZE) {
        Page page = pages[i];
        if (page == null) {
          boolean allZeroes = true;
          for (int j = 0; j < PAGE_SIZE; j++) {
            if ((values[offs + j] & mask) != 0) {
              allZeroes = false;
              break;
            }
          }
          if (!allZeroes) {
            page = MemContentsSub.createPage(PAGE_SIZE, width);
            pages[i] = page;
          }
        }
        if (page != null) {
          System.arraycopy(values, offs, vals, 0, PAGE_SIZE);
          if (!page.matches(vals, startOffs, mask)) {
            int[] oldValues = page.get(0, PAGE_SIZE);
            page.load(0, vals, mask);
            if (page.isClear())
              pages[i] = null;
            fireBytesChanged((long) i << PAGE_SIZE_BITS, PAGE_SIZE, oldValues);
          }
        }
      }
      if (endOffs >= 0) {
        ensurePage(pageEnd);
        vals = new int[endOffs + 1];
        System.arraycopy(values, offs, vals, 0, endOffs + 1);
        Page page = pages[pageEnd];
        if (!page.matches(vals, startOffs, mask)) {
          int[] oldValues = page.get(0, endOffs + 1);
          page.load(0, vals, mask);
          if (page.isClear())
            pages[pageEnd] = null;
          fireBytesChanged((long) pageEnd << PAGE_SIZE_BITS, endOffs + 1, oldValues);
        }
      }
    }
  }

  public void copyFrom(long start, MemContents src, long offs, int count) {
    count = (int)Math.min(count, getLastOffset() - start + 1);
    if (count <= 0)
      return;
    if (src.width != width)
      throw new IllegalArgumentException(String.format(
            "memory width mismatch: src is %d bits wide, dest is %d bits wide",
            src.addrBits, addrBits));
    if (offs + count - 1 > src.getLastOffset())
      throw new IllegalArgumentException(String.format(
            "memory offset out of range: offset 0x%x count 0x%x exceeds last valid offset 0x%x",
            offs, count, src.getLastOffset()));

    int dp = (int) (start >>> PAGE_SIZE_BITS);
    int di = (int) (start & PAGE_MASK);
    int dstPageEnd = (int) ((start + count - 1) >>> PAGE_SIZE_BITS);
    int dstEndOffs = (int) ((start + count - 1) & PAGE_MASK);

    int sp = (int) (offs >>> PAGE_SIZE_BITS);
    int si = (int) (offs & PAGE_MASK);
    int srcPageEnd = (int) ((offs + count - 1) >>> PAGE_SIZE_BITS);
    int srcEndOffs = (int) ((offs + count - 1) & PAGE_MASK);

    boolean changed = false;
    do {
      Page dstPage = pages[dp];
      Page srcPage = src.pages[sp];
      int n = Math.min(count, Math.min(PAGE_SIZE - si, PAGE_SIZE - di));
      if (dstPage == null && srcPage == null) {
        // both already all zeros, so do nothing
      } else if (srcPage == null) {
        // clearing locations di..di+n on this page
        fill(dp*PAGE_SIZE+di, n, 0);
      } else {
        if (dstPage == null)
          dstPage = pages[dp] = MemContentsSub.createPage(PAGE_SIZE, width);
        // copy locations di..di+n on this page
        int[] oldVals = dstPage.get(di, n);
        int[] vals = srcPage.get(si, n);
        dstPage.set(di, vals);
        // fire here
        fireBytesChanged(dp*PAGE_SIZE+di, n, oldVals);
      }
      count -= n;
      di += n;
      si += n;
      if (di >= PAGE_SIZE) {
        di = 0;
        dp++;
      }
      if (si >= PAGE_SIZE) {
        si = 0;
        sp++;
      }
    } while (count > 0); // (dp <= dstPageEnd || di <= dstEndOffs)
  }

  // Note: The xml writing code previously used getDefaultAttributeValue() then
  // equals() to check whether it is necessary to write an attribute value into
  // the xml. So I wanted to implement equals() [and hashCode() to go with it,
  // as recommended] so that the default memory contents (all zeros) could be
  // omitted. But equals() is also called other places, at least:
  //  - when moving components (part of the checks to see if state should be
  //    transfered over)
  //  - when checking if the selection attribute set need to be recalculated
  //  - during xml reading
  // It's unclear whether a potentially expensive operation is
  // justified in all of those cases.
  // So for now, we don't override equals() [or hashcode()] and instead
  // make a targeted change to the xml writing code. It now calls a new
  // function, hasDefaultAttributeValue(), which Rom now overrides to check
  // for the all zeros case.
  boolean isAllZeros() {
    int n = pages.length;
    for (int i = 0; i < n; i++) {
      Page a = pages[i];
      if (a == null)
        continue;
      int len = a.getLength();
      for (int j = 0; j < len; j++) {
        if ((a.get(j) & mask) != 0)
          return false;
      }
    }
    return true;
  }
  /*
  @Override
  public boolean equals(Object o) {
    System.out.println("comparing mem contents");
    if (this == o)
      return true;
    Thread.dumpStack();
    if (!(o instanceof MemContents))
      return false;
    MemContents other = (MemContents) o;
    if (width != other.width || addrBits != other.addrBits)
      return false;
    int n = pages.length;
    for (int i = 0; i < n; i++) {
      Page a = pages[i];
      Page b = other.pages[i];
      if (a == null && b == null)
        continue;
      int len = (a != null ? a : b).getLength();
      for (int j = 0; j < len; j++) {
        int va = (a == null ? 0 : a.get(j)) & mask;
        int vb = (b == null ? 0 : b.get(j)) & mask;
        if (va != vb)
          return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    System.out.println("hashing mem contents");
    Thread.dumpStack();
    int h = addrBits * 31 + width;
    for (Page page : pages) {
      if (page == null)
        continue;
      for (int j = 0, n = page.getLength(); j < n; j++)
        h = h * 31 + (page.get(j) & mask);
    }
    return h;
  }
  */

  public void setDimensions(int addrBits, int width) {
    if (addrBits == this.addrBits && width == this.width)
      return;
    this.addrBits = addrBits;
    this.width = width;
    this.mask = width == 32 ? 0xffffffff : ((1 << width) - 1);

    Page[] oldPages = pages;
    int pageCount;
    int pageLength;
    if (addrBits < PAGE_SIZE_BITS) {
      pageCount = 1;
      pageLength = 1 << addrBits;
    } else {
      pageCount = 1 << (addrBits - PAGE_SIZE_BITS);
      pageLength = PAGE_SIZE;
    }
    pages = new Page[pageCount];
    int copiedPages = 0;
    if (oldPages != null) {
      int n = Math.min(oldPages.length, pages.length);
      for (int i = 0; i < n; i++) {
        if (oldPages[i] != null) {
          copiedPages++;
          pages[i] = MemContentsSub.createPage(pageLength, width);
          int m = Math.max(oldPages[i].getLength(), pageLength);
          for (int j = 0; j < m; j++) {
            pages[i].set(j, oldPages[i].get(j));
          }
        }
      }
    }
    if (copiedPages == 0 && pages[0] == null) {
      pages[0] = MemContentsSub.createPage(pageLength, width);
    }
    fireMetainfoChanged();
  }

  static abstract class Page {
    abstract void clear();

    abstract Page duplicate();

    abstract int get(int addr);

    int[] get(int start, int len) {
      int[] ret = new int[len];
      for (int i = 0; i < ret.length; i++)
        ret[i] = get(start + i);
      return ret;
    }

    void set(int start, int[] val) {
      for (int i = 0; i < val.length; i++)
        set(start + i, val[i]);
    }

    abstract int getLength();

    boolean isClear() {
      for (int i = 0, n = getLength(); i < n; i++) {
        if (get(i) != 0)
          return false;
      }
      return true;
    }

    abstract void load(int start, int[] values, int mask);

    boolean matches(int[] values, int start, int mask) {
      for (int i = 0; i < values.length; i++) {
        if (get(start + i) != (values[i] & mask))
          return false;
      }
      return true;
    }

    abstract void set(int addr, int value);
  }

}
