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

package com.cburch.logisim.comp;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.LinkedList;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.util.GraphicsUtil;

public class TextField {

  public static final GraphicsUtil ALIGN = GraphicsUtil.ALIGN;

  protected int x;
  protected int y;
  protected int halign;
  protected int valign;
  protected Font font;
  protected String text = "";
  private LinkedList<TextFieldListener> listeners = new LinkedList<TextFieldListener>();

  public TextField(int x, int y, int halign, int valign) {
    this(x, y, halign, valign, null);
  }

  public TextField(int x, int y, int halign, int valign, Font font) {
    this.x = x;
    this.y = y;
    this.halign = halign;
    this.valign = valign;
    this.font = font;
  }

  public void addTextFieldListener(TextFieldListener l) {
    listeners.add(l);
  }

  public void draw(Graphics2D g) {
    GraphicsUtil.drawText(g, font, text, x, y, halign, valign);
  }

  public void fireTextChanged(TextFieldEvent e) {
    for (TextFieldListener l : new ArrayList<TextFieldListener>(listeners)) {
      l.textChanged(e);
    }
  }

  public Bounds getVisibleBounds() {
    // return Bounds.create(GraphicsUtil.getTextBounds(g, font, text, x, y, halign, valign));
    return Bounds.create(GraphicsUtil.getTextBounds(GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT,
          font, text, x, y, halign, valign));
  }

  public TextFieldCaret getCaret(Canvas canvas, int pos) {
    return new TextFieldCaret(canvas, this, pos);
  }

  public TextFieldCaret getCaret(Canvas canvas, int x, int y) {
    return new TextFieldCaret(canvas, this, x, y);
  }

  public Font getFont() {
    return font;
  }

  public int getHAlign() {
    return halign;
  }

  public String getText() {
    return text;
  }

  public int getVAlign() {
    return valign;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  public void removeTextFieldListener(TextFieldListener l) {
    listeners.remove(l);
  }

  public void setAlign(int halign, int valign) {
    this.halign = halign;
    this.valign = valign;
  }

  public void setFont(Font font) {
    this.font = font;
  }

  public void setHorzAlign(int halign) {
    this.halign = halign;
  }

  public void setLocation(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public void setLocation(int x, int y, int halign, int valign) {
    this.x = x;
    this.y = y;
    this.halign = halign;
    this.valign = valign;
  }

  public void setText(String text) {
    if (!text.equals(this.text)) {
      TextFieldEvent e = new TextFieldEvent(this, this.text, text);
      this.text = text;
      fireTextChanged(e);
    }
  }

  public void setVertAlign(int valign) {
    this.valign = valign;
  }

}
