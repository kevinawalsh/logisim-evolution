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

package com.cburch.logisim.tools;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.Main;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.generic.Callout;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.util.DragDrop;

public abstract class Tool implements AttributeDefaultProvider, DragDrop.Support, DragDrop.Ghost {
  private static Cursor dflt_cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

  public Tool cloneTool() {
    return this;
  }

  public void draw(Canvas canvas, ComponentDrawContext context) {
    draw(context);
  }

  // This was the draw method until 2.0.4 - As of 2.0.5, you should
  // use the other draw method.
  // todo: remove and delete this obsolete code
  public void draw(ComponentDrawContext context) { }

  public AttributeSet getAttributeSet() {
    return null;
  }

  public AttributeSet getAttributeSet(Canvas canvas) {
    return getAttributeSet();
  }

  public Cursor getCursor() {
    return dflt_cursor;
  }

  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    return null;
  }

  public abstract String getDescription();
  public abstract String getDisplayName();

  public Set<Component> getHiddenComponents(Canvas canvas) {
    return null;
  }

  public abstract String getName();

  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver) {
    return false;
  }

  public void keyPressed(Canvas canvas, KeyEvent e) { }
  public void keyReleased(Canvas canvas, KeyEvent e) { }
  public void keyTyped(Canvas canvas, KeyEvent e) { }

  public void mouseClicked(Canvas canvas, Graphics g, MouseEvent e) { }
  public void mouseDragged(Canvas canvas, Graphics g, MouseEvent e) { }
  public void mouseEntered(Canvas canvas, Graphics g, MouseEvent e) { }
  public void mouseExited(Canvas canvas, Graphics g, MouseEvent e) { }
  public void mouseMoved(Canvas canvas, Graphics g, MouseEvent e) { }
  public void mousePressed(Canvas canvas, Graphics g, MouseEvent e) { }
  public void mouseReleased(Canvas canvas, Graphics g, MouseEvent e) { }

  public void paintIcon(ComponentDrawContext c, int x, int y) { }

  public void select(Canvas canvas) { }
  public void deselect(Canvas canvas) { }

  public void setAttributeSet(AttributeSet attrs) { }

  public boolean sharesSource(Tool other) {
    return this.equals(other);
  }

  @Override
  public String toString() {
    return getName();
  }

  public boolean isBuiltin() { return false; } // most builtins should return true

  public static final DragDrop dnd = Main.headless ? null : new DragDrop(Tool.class);
  public DragDrop getDragDrop() { return dnd; }

  JDragLabel dragLabel;
  private class JDragLabel extends JLabel {
    public void publicPaintComponent(Graphics g) { paintComponent(g); }
  }

  private JDragLabel getDragLabel() {
    if (dragLabel != null)
      return dragLabel;
    dragLabel = new JDragLabel();
    dragLabel.setOpaque(true);
    // dragLabel.setFont(plainFont);
    dragLabel.setText(getDisplayName());
    dragLabel.setIcon(new Icon() {
      public int getIconHeight() { return 20; }
      public int getIconWidth() { return 20; }
      public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
        Graphics g2 = g.create();
        ComponentDrawContext context = new ComponentDrawContext(dragLabel, null, null, g, g2);
        Tool.this.paintIcon(context, x, y);
        g2.dispose();
      }
    });
    return dragLabel;
  }

  public void paintDragImage(JComponent dest, Graphics g, Dimension dim) {
    JDragLabel l = getDragLabel();
    l.setSize(dim);
    l.setLocation(0, 0);
    getDragLabel().publicPaintComponent(g);
  }

  public Dimension getSize() {
    return getDragLabel().getPreferredSize();
  }

  protected Callout tip = null;
  protected void hideHint() {
    if (tip == null)
      return;
    tip.hide();
    tip = null;
  }

  protected static int hintCount = 0;

  protected boolean hintReady(PrefMonitor<Integer> budget) {
    // hint on events 15 20 25 30 35, 
    if (hintCount > 35)
      return false;
    hintCount++;
    int round = budget.get();
    if (round > 0 && hintCount > 35) {
      budget.set(round - 1);
      return false;
    }
    return (round > 0 && hintCount >= 15 && hintCount % 5 == 0);
  }

  protected void hintWorked() {
    if (hintCount < 20)
      hintCount = 31; // do at most one more this round
    else
      hintCount = 35;
  }

  protected void showHint(Canvas canvas, Location p, String msg) {
    tip = new Callout(msg);
    tip.show(canvas, p.x, p.y, Callout.NW, Callout.DURATION);
  }

}
