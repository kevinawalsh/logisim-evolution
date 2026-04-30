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

package com.bfh.logisim.fpga;

import java.awt.Image;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.data.Bounds;

// Board describes an fpga-based platform (i.e. "demo board") that can be the
// target of the FPGA synthesis. Each Board has a name, chipset, image, and list
// of I/O resources.
public class Board extends AbstractList<BoardIO> {
  
  public static final int IMG_WIDTH = 740;
  public static final int IMG_HEIGHT = 400;

	public final String name;
	public final String apio_name; // board name used for apio toolchain
	public final String openFPGALoader_name; // board name used for openFPGALoader
	public final Chipset fpga;
	public final Image image;

	private final ArrayList<BoardIO> ios = new ArrayList<>();

	public Board(String name, String apio_name, String openFPGALoader_name, Chipset fpga, Image image) {
    this.name = name;
    this.apio_name = apio_name;
    this.openFPGALoader_name = openFPGALoader_name;
    this.fpga = fpga;
    this.image = image.getScaledInstance(IMG_WIDTH, IMG_HEIGHT, Image.SCALE_SMOOTH);
	}

  @Override
  public BoardIO get(int i) {
    return ios.get(i);
  }

  @Override
  public int size() {
    return ios.size();
  }

	public void addComponent(BoardIO io) {
		ios.add(io);
	}

	public void addComponents(List<BoardIO> io) {
		ios.addAll(io);
	}

  public void printStats(FPGAReport out) {
		out.AddInfo("Board '%s' contains the following I/O resources:", name);
		for (BoardIO.Type type : BoardIO.PhysicalTypes) {
      int count = 0, bits = 0;
			for (BoardIO io : ios) {
				if (io.type == type) {
					count++;
					bits += io.width;
        }
      }
      out.AddInfo("   %s: %d components (%d bits)", type, count, bits);
		}
	}

	public ArrayList<Bounds> compatableRects(BoardIO.Type type, int bits) {
		ArrayList<Bounds> result = new ArrayList<>();
		for (BoardIO io : ios)
      if (io.type.equals(type) && bits <= io.width)
        result.add(io.rect);
		return result;
	}

  public BoardIO getComponent(Bounds r) {
		for (BoardIO io : ios)
      if (io.rect.equals(r))
        return io;
    return null;
  }

}
