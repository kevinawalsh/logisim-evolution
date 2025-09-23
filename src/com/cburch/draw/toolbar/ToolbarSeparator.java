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

package com.cburch.draw.toolbar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;

public class ToolbarSeparator implements ToolbarItem {
	private int size;

	public ToolbarSeparator(int size) {
		this.size = size;
	}

	public Dimension getDimension(JComponent c, Object orientation) {
		return new Dimension(size, size);
	}

	public String getToolTip() {
		return null;
	}

	public boolean isSelectable() {
		return false;
	}

	public void paintIcon(Component destination, Graphics g) {
    int margin = ToolbarButton.BORDER * 2; // toolbar adds border on all sides
		Dimension dim = destination.getSize();
    int w = dim.width, h = dim.height, s = size;
		g.setColor(Color.GRAY);
		if (h >= w) { // vertical line in horizontal toolbar
      g.drawLine(s/2-1, 2, s/2-1, h-4-margin);
      g.drawLine(s/2+1, 2, s/2+1, h-4-margin);
		} else { // horizontal lines in vertical toolbar
      g.drawLine(2, s/2-1, w-4-margin, s/2-1);
      g.drawLine(2, s/2+1, w-4-margin, s/2+1);
		}
	}
}
