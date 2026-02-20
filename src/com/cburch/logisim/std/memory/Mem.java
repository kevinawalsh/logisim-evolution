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
import static com.cburch.logisim.std.Strings.S;

import java.awt.Graphics2D;
import java.io.File;
import java.util.WeakHashMap;

import com.cburch.hex.HexModel;
import com.cburch.hex.HexModelListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;

public abstract class Mem extends InstanceFactory {
  // Note: The code is meant to be able to handle up to 32-bit addresses, but it
  // hasn't been debugged thoroughly. There are two definite changes I would
  // make if I were to extend the address bits: First, there would need to be some
  // modification to the memory's graphical representation, because there isn't
  // room in the box to include such long memory addresses with the current font
  // size. And second, I'd alter the MemContents class's PAGE_SIZE_BITS
  // constant to 14 so that its "page table" isn't quite so big.

  static class MemListener implements HexModelListener {

    Instance instance;

    MemListener(Instance instance) {
      this.instance = instance;
    }

    public void bytesChanged(HexModel source, long start, long numBytes,
        int[] values) {
      instance.fireInvalidated(); // ROM edit affects all simulations
    }

    public void metainfoChanged(HexModel source) {
    }
  }

  static final AttributeOption SINGLE = new AttributeOption("single",
      S.getter("memSingle"));
  static final AttributeOption DUAL = new AttributeOption("dual",
      S.getter("memDual"));
  static final AttributeOption QUAD = new AttributeOption("quad",
      S.getter("memQuad"));
  static final Attribute<AttributeOption> LINE_ATTR = Attributes.forOption(
      "line", S.getter("memLineSize"), new AttributeOption[] {
        SINGLE, DUAL, QUAD });

  public static final int SymbolWidth = 200;
  public static final Attribute<BitWidth> ADDR_ATTR = Attributes.forBitWidth(
      "addrWidth", S.getter("ramAddrWidthAttr"), 2, 24);

  public static final Attribute<BitWidth> DATA_ATTR = Attributes.forBitWidth(
      "dataWidth", S.getter("ramDataWidthAttr"));
  // port-related constants
  static final int DATA = 0;
  static final int ADDR = 1;
  static final int MEM_INPUTS = 2;
  // other constants
  static final int DELAY = 10;

  private WeakHashMap<Instance, File> currentInstanceFiles;

  Mem(String name, StringGetter desc, int extraPorts) {
    super(name, desc);
    currentInstanceFiles = new WeakHashMap<Instance, File>();
    setInstancePoker(MemPoker.class);
    setKeyConfigurator(JoinedConfigurator.create(new BitWidthConfigurator(
            ADDR_ATTR, 2, 24, 0), new BitWidthConfigurator(DATA_ATTR)));

    setOffsetBounds(Bounds.create(-140, -40, 140, 80));
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    Bounds bds = instance.getNominalBounds();
    int x = bds.getX() + bds.getWidth() / 2;
    int y = bds.getY() - 2;
    int halign = GraphicsUtil.H_CENTER;
    int valign = GraphicsUtil.V_BOTTOM;
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, x, y, halign, valign);
  }

  abstract void configurePorts(Instance instance);

  @Override
  public abstract AttributeSet createAttributeSet();

  protected void DrawAddress(InstancePainter painter, int xpos, int ypos,
      int NrAddressBits) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawLine(xpos + 10, ypos + 10, xpos + 19, ypos + 10);
    g.drawLine(xpos + 5, ypos + 5, xpos + 10, ypos + 10);
    g.drawLine(xpos + 10, ypos + 30, xpos + 19, ypos + 30);
    g.drawLine(xpos + 5, ypos + 25, xpos + 10, ypos + 30);
    if (NrAddressBits > 2) {
      for (int i = 0; i < 3; i++) {
        g.drawLine(xpos + 15, ypos + 13 + i * 6, xpos + 15, ypos + 15
            + i * 6);
      }
    }
    GraphicsUtil.switchToWidth(g, 5);
    g.drawLine(xpos, ypos, xpos + 5, ypos + 5);
    g.drawLine(xpos + 5, ypos + 5, xpos + 5, ypos + 25);
    GraphicsUtil.switchToWidth(g, 1);
    GraphicsUtil.drawText(g, "0", xpos + 22, ypos + 10,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
    GraphicsUtil.drawText(g, Integer.toString(NrAddressBits - 1),
        xpos + 22, ypos + 30, GraphicsUtil.H_LEFT,
        GraphicsUtil.V_CENTER);
    GraphicsUtil.drawText(g, "A", xpos + 50, ypos + 20,
        GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
    g.drawLine(xpos + 40, ypos + 5, xpos + 45, ypos + 10);
    g.drawLine(xpos + 45, ypos + 10, xpos + 45, ypos + 17);
    g.drawLine(xpos + 45, ypos + 17, xpos + 48, ypos + 20);
    g.drawLine(xpos + 48, ypos + 20, xpos + 45, ypos + 23);
    g.drawLine(xpos + 45, ypos + 23, xpos + 45, ypos + 30);
    g.drawLine(xpos + 40, ypos + 35, xpos + 45, ypos + 30);
    String size = Integer.toString((1 << NrAddressBits) - 1);
    int StrSize = g.getFontMetrics(g.getFont()).stringWidth(size);
    g.drawLine(xpos + 60, ypos + 20, xpos + 60 + StrSize, ypos + 20);
    GraphicsUtil.drawText(g, "0", xpos + 60 + (StrSize / 2), ypos + 19,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
    GraphicsUtil.drawText(g, size, xpos + 60 + (StrSize / 2), ypos + 21,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
    painter.drawPort(ADDR);
  }

  public abstract int getControlHeight(AttributeSet attrs);

  public File getCurrentImage(Instance instance) {
    return currentInstanceFiles.get(instance);
  }

  abstract HexFrame getHexFrame(Project proj, Instance instance,
      CircuitState state);

  @Override
  protected Object getInstanceFeature(Instance instance, Object key) {
    if (key == MenuExtender.class) {
      return new MemMenu(this, instance);
    }
    return super.getInstanceFeature(instance, key);
  }

  protected String GetSizeLabel(int NrAddressBits) {
    String[] Labels = { "", "k", "M", "G" };
    int pass = 0;
    int AddrBits = NrAddressBits;
    while (AddrBits > 9) {
      pass++;
      AddrBits -= 10;
    }
    int size = 1 << AddrBits;
    return Integer.toString(size) + Labels[pass];
  }

  abstract MemState getState(Instance instance, CircuitState state);

  abstract MemState getState(InstanceState state);

  @Override
  public abstract void propagate(InstanceState state);

  public void setCurrentImage(Instance instance, File value) {
    currentInstanceFiles.put(instance, value);
  }

  public void DrawMemClassic(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();

    // draw boundary and label
    painter.drawBounds();
    painter.drawLabel();

    String mem = "ram", MEM = "RAM";
    if (this instanceof Rom) {
      mem = "rom";
      MEM = "ROM";
    }

    // draw contents
    int dataLines = lineSize(painter.getAttributeSet());
    if (painter.getShowState()) {
      MemState state = getState(painter);
      state.paint(g, bds.getX(), bds.getY(),
          15, 15, bds.getWidth() - 30, bds.getHeight() - 20, true, dataLines);
    } else {
      int addrBits = painter.getAttributeValue(ADDR_ATTR).getWidth();
      int dataBits = painter.getAttributeValue(DATA_ATTR).getWidth();
      int bytes = ((1 << addrBits)*dataBits + 7)/8;
      String label;
      if (bytes >= 1<<30) {
        label = S.fmt(mem+"GigabyteLabel", "" + (bytes >>> 30));
      } else if (bytes >= 1<<20) {
        label = S.fmt(mem+"MegabyteLabel", "" + (bytes >> 20));
      } else if (bytes >= 1<<10) {
        label = S.fmt(mem+"KilobyteLabel", "" + (bytes >> 10));
      } else {
        label = S.fmt(mem+"ByteLabel", "" + bytes);
      }
      GraphicsUtil.drawCenteredText(g, label,
          bds.getX() + bds.getWidth() / 2,
          bds.getY() + bds.getHeight() / 2);
      if (dataLines > 1)
        GraphicsUtil.drawCenteredText(g, "(" + dataLines + " per line)",
            bds.getX() + bds.getWidth() / 2,
            bds.getY() + bds.getHeight() / 2 + 20);
    }


    AttributeOption proportions = painter.getAttributeValue(Rom.ATTR_PROPORTIONS);
    if (proportions == null)
      proportions = Rom.RECT;
    int symwidth = (proportions == Rom.TALL) ? SymbolWidth/2 : SymbolWidth;

    GraphicsUtil.drawCenteredText(g,
        MEM + " " + GetSizeLabel(painter.getAttributeValue(Mem.ADDR_ATTR).getWidth())
        + " x "
        + Integer.toString(painter.getAttributeValue(Mem.DATA_ATTR).getWidth()),
        bds.getX() + (symwidth / 2) + 20, bds.getY() + 6);

    // draw input and output ports
    painter.drawPort(DATA, S.get("ramDataLabel"), Direction.WEST);
    painter.drawPort(ADDR, S.get("ramAddrLabel"), Direction.EAST);
    for (int i = 1; i < dataLines; i++)
      painter.drawPort(MEM_INPUTS+i-1, ""+i, Direction.WEST);
    // g.setColor(Color.GRAY);
    // painter.drawPort(CS, S.get("ramCSLabel"), Direction.SOUTH);
  }

  public static int lineSize(AttributeSet attrs) {
    AttributeOption v = attrs.getValue(LINE_ATTR);
    if (QUAD.equals(v)) return 4;
    else if (DUAL.equals(v)) return 2;
    else return 1;
  }

}
