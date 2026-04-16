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

import com.cburch.hex.HexModel;
import com.cburch.logisim.gui.hex.HexFrame;

public abstract class MemContents implements HexModel {
  
  // MemContents holds the dimensions and bytes for Ram and Rom. Subclasses hold
  // additional state needed to support viewing/editing in a separate HexFrame
  // window, triggering propagation, and (for Rom) adding actions to the project
  // undo/redo stack. Subclasses provide two notificaton methods, to perform
  // Ram-specific or Rom-specific triggers, plus the four
  // setContentBytes/clearContents methods for HexModel and copyContents
  // method used by HexFile.
  //
  // Ram and Rom differ in how they handle changes to contents.
  // For Ram, RamContents is part of the simulation state.
  //  - Each of an instance's simulations has its own RamContents and RamState.
  //  - The underlying RamContents reference doesn't usually change.
  //  - Clearing, and other changes to RamContents, are done in-place.
  //  - Content changes are not captured in the project undo/redo log.
  // For Rom, RomContents is a component property.
  //  - Each of an instance's simulations has its own RomState, but these all
  //    refer to a common shared RomContents for the instance.
  //  - Clearing, and other changes to RomContents, are done in-place.
  //  - Content changes are all captured in the project undo/redo log.
  abstract protected void fireBytesChanged(boolean fromSimulation, long start, long count);
  abstract protected void fireDimensionsChanged();
	abstract public void copyContents(long start, MemContents src, long offset, long count);
	public void copyContents(MemContents src) {
    copyContents(0, src, 0, Math.min(getValueCount(), src.getValueCount()));
  }
  abstract public HexFrame getHexFrame();

  private static final int PAGE_SIZE_BITS = 12;
  private static final int PAGE_SIZE = 1 << PAGE_SIZE_BITS;

  private static final int PAGE_MASK = PAGE_SIZE - 1;

  private int width;
  private int addrBits;
  private int mask;
  private Page[] pages;

  protected MemContents(int addrBits, int width) {
    this.addrBits = addrBits;
    this.width = width;
    this.mask = width == 32 ? 0xffffffff : ((1 << width) - 1);
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
    pages[0] = MemContentsSub.createPage(pageLength, width);
  }

  protected MemContents(MemContents other) {
    width = other.width;
    addrBits = other.addrBits;
    mask = other.mask;
    pages = new Page[other.pages.length];
    for (int i = 0; i < pages.length; i++)
      if (other.pages[i] != null)
        pages[i] = other.pages[i].duplicate();
  }
  
  protected void clear(boolean fromSimulation) {
    for (int i = 0; i < pages.length; i++) {
      if (pages[i] != null) {
        if (pages[i] != null)
          clearPage(fromSimulation, i);
      }
    }
  }

  private void clearPage(boolean fromSimulation, int index) {
    Page page = pages[index];
    if (page == null || page.isClear())
      return;
    pages[index] = null;
    fireBytesChanged(fromSimulation, index << PAGE_SIZE_BITS, PAGE_SIZE);
  }

  private void ensurePage(int index) {
    if (pages[index] == null) {
      pages[index] = MemContentsSub.createPage(PAGE_SIZE, width);
    }
  }

  protected void clear(long start, long len) {
    if (len == 0)
      return;

    int pageStart = (int) (start >>> PAGE_SIZE_BITS);
    int startOffs = (int) (start & PAGE_MASK);
    int pageEnd = (int) ((start + len - 1) >>> PAGE_SIZE_BITS);
    int endOffs = (int) ((start + len - 1) & PAGE_MASK);

    if (pageStart == pageEnd) {
      Page page = pages[pageStart];
      if (page == null)
        return;
      int[] vals = new int[(int) len];
      if (!page.matches(vals, startOffs, mask)) {
        page.load(startOffs, vals, mask);
        if (page.isClear())
          pages[pageStart] = null;
        fireBytesChanged(false, start, len);
      }
    } else {
      if (startOffs == 0) {
        pageStart--;
      } else {
        Page page = pages[pageStart];
        if (page == null) {
          // nothing to do
        } else {
          int[] vals = new int[PAGE_SIZE - startOffs];
          if (!page.matches(vals, startOffs, mask)) {
            page.load(startOffs, vals, mask);
            if (page.isClear())
              pages[pageStart] = null;
            fireBytesChanged(false, start, PAGE_SIZE - pageStart);
          }
        }
      }
      for (int i = pageStart + 1; i < pageEnd; i++) {
        if (pages[i] != null)
          clearPage(false, i);
      }
      if (endOffs >= 0) {
        Page page = pages[pageEnd];
        if (page == null) {
          // nothing to do
        } else {
          int[] vals = new int[endOffs + 1];
          if (!page.matches(vals, 0, mask)) {
            page.load(0, vals, mask);
            if (page.isClear())
              pages[pageEnd] = null;
            fireBytesChanged(false, (long) pageEnd << PAGE_SIZE_BITS, endOffs + 1);
          }
        }
      }
    }
  }
  
  public int get(long addr) {
    int page = (int) (addr >>> PAGE_SIZE_BITS);
    int offs = (int) (addr & PAGE_MASK);
    if (page < 0 || page >= pages.length || pages[page] == null)
      return 0;
    return pages[page].get(offs) & mask;
  }

  public int[] get(long addr, long count) {
    int[] ret = new int[(int) count];
    int page = (int) (addr >>> PAGE_SIZE_BITS);
    int offs = (int) (addr & PAGE_MASK);
    int retOffs = 0;
    int remaining = (int) count;
    while (remaining > 0) {
      int n = Math.min(remaining, PAGE_SIZE - offs);
      if (page >= 0 && page < pages.length && pages[page] != null) {
        int[] vals = pages[page].get(offs, n);
        for (int i = 0; i < n; i++)
          ret[retOffs + i] = vals[i] & mask;
      }
      // else ret entries remain 0 (default for unallocated pages)
      retOffs += n;
      remaining -= n;
      page++;
      offs = 0;
    }
    return ret;
  }
  
  @Override
  public long getValueCount() { return (1L << addrBits); }

  @Override
  public long getLastOffset() { return (1L << addrBits) - 1; }

  @Override
  public int getValueWidth() { return width; }

  public int getLogLength() { return addrBits; }

  protected void set(boolean fromSimulation, long addr, int value) {
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
      fireBytesChanged(fromSimulation, addr, 1);
    }
  }

  protected void set(boolean fromSimulation, long start, int[] values) {
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
        page.load(startOffs, values, mask);
        if (page.isClear())
          pages[pageStart] = null;
        fireBytesChanged(fromSimulation, start, values.length);
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
          page.load(startOffs, vals, mask);
          if (page.isClear())
            pages[pageStart] = null;
          fireBytesChanged(fromSimulation, start, PAGE_SIZE - pageStart);
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
            page.load(0, vals, mask);
            if (page.isClear())
              pages[i] = null;
            fireBytesChanged(false, (long) i << PAGE_SIZE_BITS, PAGE_SIZE);
          }
        }
      }
      if (endOffs >= 0) {
        ensurePage(pageEnd);
        vals = new int[endOffs + 1];
        System.arraycopy(values, offs, vals, 0, endOffs + 1);
        Page page = pages[pageEnd];
        if (!page.matches(vals, startOffs, mask)) {
          page.load(0, vals, mask);
          if (page.isClear())
            pages[pageEnd] = null;
          fireBytesChanged(false, (long) pageEnd << PAGE_SIZE_BITS, endOffs + 1);
        }
      }
    }
  }
  
  protected void copyFrom(MemContents src) {
    copyFrom(0, src, 0, Math.min(getValueCount(), src.getValueCount()));
  }

  protected void copyFrom(long start, MemContents src, long offs, long count) {
    count = Math.min(count, getLastOffset() - start + 1);
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
      int n = (int)Math.min(count, Math.min(PAGE_SIZE - si, PAGE_SIZE - di));
      if (dstPage == null && srcPage == null) {
        // both already all zeros, so do nothing
      } else if (srcPage == null) {
        // clearing locations di..di+n on this page
        clear(dp*PAGE_SIZE+di, n);
      } else {
        if (dstPage == null)
          dstPage = pages[dp] = MemContentsSub.createPage(PAGE_SIZE, width);
        // copy locations di..di+n on this page
        int[] vals = srcPage.get(si, n);
        dstPage.set(di, vals);
        // fire here
        fireBytesChanged(false, dp*PAGE_SIZE+di, n);
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
  public boolean isAllZeros() {
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

  protected void setDimensions(int addrBits, int width) {
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
    fireDimensionsChanged();
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
