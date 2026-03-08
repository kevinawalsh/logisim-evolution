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
package com.cburch.logisim.gui.generic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import javax.swing.JComponent;

public class PillLabel extends JComponent {

  private String text = "";
  private Color fg = Color.WHITE;
  private Color bg = Color.MAGENTA;
  private int padding = 24;

  public PillLabel() {
    setAlignmentY(CENTER_ALIGNMENT);
  }

  public PillLabel(String text) {
    this.text = text;
    setAlignmentY(CENTER_ALIGNMENT);
  }

  public PillLabel(String text, Color bg) {
    this.text = text;
    this.bg = bg;
    setAlignmentY(CENTER_ALIGNMENT);
  }

  public PillLabel(String text, Color fg, Color bg) {
    this.text = text;
    this.fg = fg;
    this.bg = bg;
    setAlignmentY(CENTER_ALIGNMENT);
  }

  public void setText(String text) { this.text = text; revalidate(); repaint(); }
  public void setColor(Color fg) { this.fg = fg; revalidate(); repaint(); }
  public void setBackground(Color bg) { this.bg = bg; revalidate(); repaint(); }
  public void setPadding(int x) { this.padding = x; revalidate(); repaint(); }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics fm = getFontMetrics(getFont());
    int tw = (fm != null ? fm.stringWidth(text) : 80);
    return new Dimension(tw + padding, 22);
  }

  @Override public Dimension getMinimumSize() { return getPreferredSize(); }

  @Override public Dimension getMaximumSize() { return getPreferredSize(); }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int h = getHeight(), arc = 6, px = padding/2;
    g.setFont(getFont());
    FontMetrics fm = g.getFontMetrics();
    g.setColor(bg);
    g.fillRoundRect(0, 0, getWidth(), h, arc, arc);
    g.setColor(fg);
    g.drawString(text, px, fm.getAscent() + (h - fm.getHeight()) / 2);
  }

}
