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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;

abstract class MemState implements ComponentData {

  // MemState holds per-simulation state for both Ram and Rom, providing:
  //  - a reference to the underlying MemContents subclass with the data
  //  - current cursor and scroll position
  //  - drawing methods to show data, address, hilighted cell, etc.

  // For Ram, each MemState has its own contents.
  // For Rom, all MemState for an instance share one contents.
  protected final MemContents contents; // all changes are in-place

  private long curScroll = 0;
  private long cursorLoc = -1;
  private long curAddr = -1;

  private boolean RecalculateParameters = true;
  private int NrOfLines = 1;
  private int NrDataSymbolsEachLine = 1;
  private int AddrBlockSize = 0;
  private int DataBlockSize = 0;
  private int DataSize = 0;
  private int SpaceSize = 0;
  private int xOffset = 0;
  private int yOffset = 0;
  private int CharHeight = 0;
  private int CachedAddrBits, CachedDataBits;

  public static final Font FONT = new Font("monospaced", Font.PLAIN, 12);

  MemState(MemContents contents) {
    this.contents = contents;
    // contents.addHexModelWeakListener(null, this);
  }

  public MemState(MemContents contents, MemState other) {
    this.contents = contents; // other.contents.duplicate();
    // contents.addHexModelWeakListener(null, this);
    curScroll = other.curScroll;
    cursorLoc = other.cursorLoc;
    curAddr = other.curAddr;
    RecalculateParameters = other.RecalculateParameters;
    NrOfLines = other.NrOfLines;
    NrDataSymbolsEachLine = other.NrDataSymbolsEachLine;
    AddrBlockSize = other.AddrBlockSize;
    DataBlockSize = other.DataBlockSize;
    DataSize = other.DataSize;
    SpaceSize = other.SpaceSize;
    xOffset = other.xOffset;
    yOffset = other.yOffset;
    CharHeight = other.CharHeight;
    CachedAddrBits = other.CachedAddrBits;
    CachedDataBits = other.CachedDataBits;
  }

  private void CalculateDisplayParameters(Graphics2D g,
      int offsetX, int offsetY,
      int DisplayWidth, int DisplayHeight) {
    RecalculateParameters = false;
    int addrBits = contents.getLogLength();
    int dataBits = contents.getValueWidth();

    CachedAddrBits = addrBits;
    CachedDataBits = dataBits;

    CharHeight = StringUtil.estimateBounds("0".length(), FONT).getHeight();
    SpaceSize = StringUtil.estimateBounds(" ".length(), FONT).getWidth() * 3/4;

    int estAddrWidth = StringUtil.estimateBounds(StringUtil.toHexString(addrBits, 0).length(), FONT).getWidth();
    AddrBlockSize = ((estAddrWidth + 9) / 10) * 10;

    DataSize = StringUtil.estimateBounds(StringUtil.toHexString(dataBits, 0).length(), FONT).getWidth();
    DataSize += SpaceSize;

    NrDataSymbolsEachLine = (DisplayWidth - AddrBlockSize) / DataSize;
    if (NrDataSymbolsEachLine > 3 && NrDataSymbolsEachLine % 2 != 0)
      NrDataSymbolsEachLine--;
    NrOfLines = Math.max(1, DisplayHeight / (CharHeight + 2));
    int TotalShowableEntries = NrDataSymbolsEachLine * NrOfLines;
    int TotalNrOfEntries = (1 << addrBits);
    while (TotalShowableEntries > (TotalNrOfEntries + NrDataSymbolsEachLine - 1)) {
      NrOfLines--;
      TotalShowableEntries -= NrDataSymbolsEachLine;
    }
    if (NrOfLines == 0) {
      NrOfLines = 1;
      NrDataSymbolsEachLine = TotalNrOfEntries;
    }
    /* here we calculate to total x-sizes */
    DataBlockSize = NrDataSymbolsEachLine * (DataSize);
    int TotalWidth = AddrBlockSize + DataBlockSize;
    xOffset = offsetX + (DisplayWidth / 2) - (TotalWidth / 2);
    /* Same calculations for the height */
    yOffset = offsetY;
  }

  public long getAddressAt(int x, int y) {
    /*
     * This function returns the address of a data symbol inside the data
     * block
     */
    int ystart = yOffset;
    int ystop = ystart + NrOfLines * (CharHeight + 2);
    int xstart = xOffset + AddrBlockSize;
    int xstop = xstart + DataBlockSize;
    if ((x < xstart) | (x > xstop) | (y < ystart) | (y > ystop))
      return -1;
    x = x - xstart;
    y = y - ystart;
    int line = y / (CharHeight + 2);
    int symbol = x / (DataSize);
    long pointedAddr = curScroll + (line * NrDataSymbolsEachLine) + symbol;
    return isValidAddr(pointedAddr) ? pointedAddr : getLastAddress();
  }

  public Bounds getBounds(long addr, Bounds bds) {
    /* This function returns the rectangle shape around an item */
    if (addr < 0) {
      return Bounds.create(bds.getX() + xOffset, bds.getY() + yOffset,
          AddrBlockSize, CharHeight + 2);
    } else if (addr < curScroll || addr >= (curScroll + NrOfLines * NrDataSymbolsEachLine)) {
      return Bounds.EMPTY_BOUNDS;
    } else {
      addr -= curScroll;
      int line = ((int) addr) / NrDataSymbolsEachLine;
      int item = ((int) addr) % NrDataSymbolsEachLine;
      return Bounds.create(
          bds.getX() + xOffset + AddrBlockSize + item * (DataSize),
          bds.getY() + yOffset + line * (CharHeight + 2),
          DataSize, CharHeight + 2);
    }
  }

  public MemContents getContents() {
    return contents;
  }

  long getCurrent() {
    return curAddr;
  }

  //
  // methods for manipulating cursor and scroll location
  //
  long getCursor() {
    return cursorLoc;
  }

  int getDataBits() {
    return contents.getValueWidth();
  }

  long getLastAddress() {
    return (1L << contents.getLogLength()) - 1;
  }

  int GetNrOfLineItems() {
    return NrDataSymbolsEachLine;
  }

  int GetNrOfLines() {
    return NrOfLines;
  }

  long getScroll() {
    return curScroll;
  }

  public boolean isSplitted() {
    return false;
  }

  boolean isValidAddr(long addr) {
    int addrBits = contents.getLogLength();
    return addr >>> addrBits == 0;
  }

  private boolean classicAppearance = true;
  private int displayWidth = 0, displayHeight = 0;

  public void paint(Graphics2D g, int leftX, int topY,
      int offsetX, int offsetY,
      int DisplayWidth, int DisplayHeight, boolean classic, int dataLines) {
    checkDimensions();
    if (RecalculateParameters || classicAppearance != classic || displayWidth != DisplayWidth || DisplayHeight != displayHeight) {
      classicAppearance = classic;
      displayWidth = DisplayWidth;
      displayHeight = DisplayHeight;
      CalculateDisplayParameters(g, offsetX, offsetY, DisplayWidth, DisplayHeight);
    }
    int BlockHeight = NrOfLines * (CharHeight + 2);
    int TotalNrOfEntries = (1 << contents.getLogLength());
    g.setColor(Color.LIGHT_GRAY);
    g.fillRect(leftX + xOffset, topY + yOffset, DataBlockSize
        + AddrBlockSize, BlockHeight);
    g.setColor(Color.DARK_GRAY);
    g.drawRect(leftX + xOffset + AddrBlockSize, topY + yOffset,
        DataBlockSize, BlockHeight);
    g.setColor(Color.BLACK);
    /* draw the addresses */
    int addr = (int) curScroll;
    if ((addr + (NrOfLines * NrDataSymbolsEachLine)) > TotalNrOfEntries) {
      addr = TotalNrOfEntries - (NrOfLines * NrDataSymbolsEachLine);
      if (addr < 0)
        addr = 0;
      curScroll = addr;
    }
    /* draw the contents */
    int firsty = topY + yOffset + (CharHeight / 2) + 1;
    int yinc = CharHeight + 2;
    int firstx = leftX + xOffset + AddrBlockSize + (SpaceSize / 2)
        + ((DataSize - SpaceSize) / 2);
    Font oldFont = g.getFont();
    g.setFont(FONT);
    for (int i = 0; i < NrOfLines; i++) {
      /* Draw address */
      GraphicsUtil.drawText(g,
          StringUtil.toHexString(contents.getLogLength(), addr), leftX
          + xOffset + (AddrBlockSize / 2), firsty + i
          * (yinc), GraphicsUtil.H_CENTER,
          GraphicsUtil.V_CENTER_FIRST);
      /* Draw data */
      for (int j = 0; j < NrDataSymbolsEachLine; j++) {
        int value = contents.get(addr + j);
        if (isValidAddr(addr + j)) {
          int blockEnd = (int)((curAddr/dataLines)*dataLines + dataLines - 1);
          if ((addr + j) >= curAddr && (addr + j) <= blockEnd) {
            g.setColor(Color.DARK_GRAY);
            g.fillRect(firstx + j * DataSize - (DataSize / 2) - 1,
                firsty + i * yinc - (CharHeight / 2) - 1,
                DataSize + 2, CharHeight + 2);
            g.setColor(Color.WHITE);
            GraphicsUtil.drawText(g, StringUtil.toHexString(
                  contents.getValueWidth(), value), firstx + j
                * DataSize, firsty + i * yinc,
                GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_FIRST);
            g.setColor(Color.BLACK);
          } else {
            GraphicsUtil.drawText(g, StringUtil.toHexString(
                  contents.getValueWidth(), value), firstx + j
                * DataSize, firsty + i * yinc,
                GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_FIRST);
          }
        }
      }
      addr += NrDataSymbolsEachLine;
    }
    g.setFont(oldFont);
  }

  void scrollToShow(long addr) {
    if (RecalculateParameters)
      return;
    int addrBits = contents.getLogLength();
    if ((addr >>> addrBits) != 0)
      return;
    if (addr < curScroll) {
      long linesToScroll = (curScroll - addr + NrDataSymbolsEachLine-1)/NrDataSymbolsEachLine;
      curScroll -= linesToScroll * NrDataSymbolsEachLine;
    } else if (addr >= (curScroll + NrOfLines * NrDataSymbolsEachLine)) {
      long curScrollEnd = curScroll + NrOfLines * NrDataSymbolsEachLine - 1;
      long linesToScroll = (addr - curScrollEnd + NrDataSymbolsEachLine-1)/NrDataSymbolsEachLine;
      curScroll += linesToScroll * NrDataSymbolsEachLine;
      long TotalNrOfEntries = (1 << addrBits);
      if ((curScroll + (NrOfLines * NrDataSymbolsEachLine)) > TotalNrOfEntries)
        curScroll = TotalNrOfEntries - (NrOfLines * NrDataSymbolsEachLine);
    }
    if (curScroll < 0)
      curScroll = 0;
  }

  private void checkDimensions() {
    if (RecalculateParameters)
      return;
    int addrBits = contents.getLogLength();
    int dataBits = contents.getValueWidth();
    if (addrBits != CachedAddrBits || dataBits != CachedDataBits) {
      RecalculateParameters = true;
      cursorLoc = -1;
      curAddr = -1;
      curScroll = 0;
    }
  }

  void setCurrent(long value) {
    curAddr = isValidAddr(value) ? value : -1L;
  }

  void setCursor(long value) {
    cursorLoc = isValidAddr(value) ? value : -1L;
  }

  void setScroll(long addr) {
    checkDimensions();
    if (RecalculateParameters)
      return;
    long maxAddr = (1 << contents.getLogLength())
        - (NrOfLines * NrDataSymbolsEachLine);
    if (addr > maxAddr) {
      addr = maxAddr; // note: maxAddr could be negative
    }
    if (addr < 0) {
      addr = 0;
    }
    curScroll = addr;
  }
}
