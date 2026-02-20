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

package com.cburch.logisim.instance;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.comp.TextField;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.tools.Caret;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.TextEditable;

public class InstanceTextField
  implements AttributeListener, TextEditable {
  private Canvas canvas;
  private InstanceComponent comp;
  private TextField field;
  private Attribute<String> labelAttr;
  private Attribute<Font> fontAttr;
  private int fieldX;
  private int fieldY;
  private int halign;
  private int valign;

  InstanceTextField(InstanceComponent comp) {
    this.comp = comp;
    this.field = null;
    this.labelAttr = null;
    this.fontAttr = null;
  }

  public void attributeListChanged(AttributeEvent e) {
  }

  public void attributeValueChanged(AttributeEvent e) {
    Attribute<?> attr = e.getAttribute();
    if (attr == labelAttr) {
      updateField(comp.getAttributeSet());
    } else if (attr == fontAttr) {
      if (field != null)
        field.setFont((Font) e.getValue());
    }
  }

  private void createField(AttributeSet attrs, String text) {
    Font font = attrs.getValue(fontAttr);
    field = new TextField(fieldX, fieldY, halign, valign, font);
    field.setText(text);
  }

  void draw(Component comp, ComponentDrawContext context) {
    if (field != null) {
      Graphics2D g = (Graphics2D)context.getGraphics().create();
      try {
        field.draw(g);
      } finally {
        g.dispose();
      }
    }
  }

  Bounds getBounds(Graphics g) {
    return field == null ? Bounds.EMPTY_BOUNDS : field.getBounds(g);
  }

  public Action getCommitAction(Circuit circuit, String oldText, String newText) {
    SetAttributeAction act = new SetAttributeAction(circuit, S.getter("changeLabelAction"));
    if ((oldText == null) != (newText == null) || (newText != null && !newText.equals(oldText)))
      act.set(comp, labelAttr, newText);
    return act;
  }

  public Caret getTextCaret(ComponentUserEvent event) {
    canvas = event.getCanvas();

    // if field is absent, create it empty
    // and if it is empty, just return a caret at its beginning
    if (field == null)
      createField(comp.getAttributeSet(), "");
    String text = field.getText();
    if (text == null || text.equals(""))
      return field.getCaret(canvas, 0);
    else
      return field.getCaret(canvas, event.getX(), event.getY());
  }

  private boolean shouldRegister() {
    return labelAttr != null || fontAttr != null;
  }

  void update(Attribute<String> labelAttr, Attribute<Font> fontAttr, int x,
      int y, int halign, int valign) {
    boolean wasReg = shouldRegister();
    this.labelAttr = labelAttr;
    this.fontAttr = fontAttr;
    this.fieldX = x;
    this.fieldY = y;
    this.halign = halign;
    this.valign = valign;
    boolean shouldReg = shouldRegister();
    AttributeSet attrs = comp.getAttributeSet();
    if (!wasReg && shouldReg)
      attrs.addAttributeWeakListener(null, this);
    if (wasReg && !shouldReg)
      attrs.removeAttributeWeakListener(null, this);

    updateField(attrs);
  }

  public String getText() {
    return comp.getAttributeSet().getValue(labelAttr);
  }

  private void updateField(AttributeSet attrs) {
    String text = attrs.getValue(labelAttr);
    if (text == null || text.equals("")) {
        field = null;
    } else {
      if (field == null) {
        createField(attrs, text);
      } else {
        Font font = attrs.getValue(fontAttr);
        if (font != null)
          field.setFont(font);
        field.setLocation(fieldX, fieldY, halign, valign);
        field.setText(text);
      }
    }
  }
}
