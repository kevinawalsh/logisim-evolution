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

package com.bfh.logisim.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import javax.swing.Icon;
import javax.swing.JLabel;

import com.bfh.logisim.fpga.Board;

public class BoardIcon implements Icon {
  private Image image;
  public static final int ICON_WIDTH = 240;
  public static final int ICON_HEIGHT = 130;
  public final JLabel label = new JLabel();

  public BoardIcon() {
  }

  public int getIconHeight() { return ICON_HEIGHT; }
  public int getIconWidth() { return ICON_WIDTH; }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    g.translate(x, y);
    Board.drawFitted(g, image, ICON_WIDTH, ICON_HEIGHT);
    g.translate(-x, -y);
  }

  public void setImage(Image img) {
    image = img; 
		label.setIcon(this);
    label.repaint();
  }

}
