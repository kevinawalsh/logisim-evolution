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
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.Icon;

// Displays warnings and errors as badges, for console tabbed pane titles.
public class BadgeIcon implements Icon {
  private int width = 45, height = 18;
  private Console console;
  public Commander commander; // used for repainting

  public BadgeIcon(Console console) {
    this.console = console;
  }

  public int getIconHeight() { return height; }
  public int getIconWidth() { return width+6; }
 
  public void paintIcon(Component c, Graphics g, int x, int y) {
    int w = console.warnings;
    int e = console.errors;
    int s = console.severes;
    int issues = w + e + s;
    if ((c != null && !c.isEnabled()) || issues == 0)
      g.setColor(Color.LIGHT_GRAY);
    else if (s > 0)
      g.setColor(Color.RED);
    else if (e > 0)
      g.setColor(Color.MAGENTA);
    else
      g.setColor(Color.ORANGE);
    g.translate(x, y);
    g.fillRoundRect(0, 0, width, height, height, height);
    g.setColor(s+e>0 || issues==0 ? Color.WHITE : Color.BLACK);
    g.setFont(g.getFont().deriveFont(Font.BOLD, 13.0f));
    if (issues == 0)
      g.drawString("ok", 16, height/2+5);
    else if (issues > 99)
      g.drawString("99+", 9, height/2+5);
    else
      g.drawString(""+issues, issues<10?22:16, height/2+5);

    g.translate(-x, -y);   //Restore graphics object
  }

  public void update() {
    if (commander != null)
      commander.repaintConsoles();
  }
}
