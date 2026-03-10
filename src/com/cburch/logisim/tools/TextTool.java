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
import static com.cburch.logisim.tools.Strings.S;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.Icon;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.comp.TextFieldCaret;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.decor.Text;
import com.cburch.logisim.std.decor.TextCaret;
import com.cburch.logisim.util.Icons;

public final class TextTool extends Tool {
  private class MyListener implements CaretListener, CircuitListener {
    public void circuitChanged(CircuitEvent event) {
      if (event.getCircuit() != caretCircuit) {
        event.getCircuit().removeCircuitWeakListener(null, this);
        return;
      }
      int action = event.getAction();
      if (action == CircuitEvent.ACTION_REMOVE) {
        if (event.getData() == caretComponent) {
          caret.cancelEditing();
        }
      }
    }

    public void editingCanceled(CaretEvent e) {
      if (e.getCaret() != caret) {
        e.getCaret().removeCaretListener(this);
        return;
      }
      caret.removeCaretListener(this);
      caretCircuit.removeCircuitWeakListener(null, this);

      caretCircuit = null;
      caretComponent = null;
      caretCreatingText = false;
      caret = null;
      caretCanvas.getProject().getFrame().setEditHandler(null);
    }

    public void editingStopped(CaretEvent e) {
      if (e.getCaret() != caret) {
        e.getCaret().removeCaretListener(this);
        return;
      }
      caret.removeCaretListener(this);
      caretCircuit.removeCircuitWeakListener(null, this);

      String val = caret.getText();
      boolean isEmpty = (val == null || val.equals(""));
      Action a;
      Project proj = caretCanvas.getProject();
      if (caretCreatingText) {
        if (!isEmpty) {
          // Adding new Text component
          caretComponent.getAttributeSet().setAttr(Text.ATTR_TEXT, val);
          CircuitMutation xn = new CircuitMutation(caretCircuit);
          xn.add(caretComponent);
          a = xn.toAction(S.getter("addComponentAction",
                Text.FACTORY.getDisplayGetter()));
        } else {
          // Ignore adding new blank Text component
          a = null;
        }
      } else {
        if (isEmpty && caretComponent.getFactory() instanceof Text) {
          // Removing existing Text component (user removed all text)
          CircuitMutation xn = new CircuitMutation(caretCircuit);
          xn.remove(caretComponent);
          a = xn.toAction(S.getter("removeComponentAction",
                Text.FACTORY.getDisplayGetter()));
        } else {
          Object obj = caretComponent.getFeature(TextEditable.class);
          if (obj == null) { // should never happen
            a = null;
          } else {
            // Change Text or Label or other EditableText  
            TextEditable editable = (TextEditable) obj;
            a = editable.getCommitAction(caretCircuit,
                e.getOldText(), e.getText());
          }
        }
      }

      caretCircuit = null;
      caretComponent = null;
      caretCreatingText = false;
      caret = null;
      proj.getFrame().setEditHandler(null);

      if (a != null && !a.isEmpty())
        proj.doAction(a);
    }
  }

  private static final Cursor cursor
      = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);

  private MyListener listener = new MyListener();
  private AttributeSet attrs;
  private Caret caret = null;
  private boolean caretCreatingText = false;
  private boolean caretNeedsSelectAll = false;
  private Canvas caretCanvas = null;
  private Circuit caretCircuit = null;
  private Component caretComponent = null;

  public TextTool() {
    attrs = Text.FACTORY.createAttributeSet();
  } // usually one per project

  private TextTool(TextTool base) {
    attrs = (AttributeSet)base.attrs.clone();
  }

  @Override
  public boolean isBuiltin() { return true; }

  @Override
  public Tool cloneTool() {
    return new TextTool(this);
  }

  // All instances considered equal, so it is unique per toolbar, etc.
  @Override
  public boolean equals(Object other) {
    return other instanceof TextTool;
  }

  @Override
  public int hashCode() {
    return TextTool.class.hashCode();
  }

  @Override
  public void deselect(Canvas canvas) {
    if (caret != null) {
      caret.stopEditing();
      caret = null;
      canvas.getProject().getFrame().setEditHandler(null);
    }
  }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    if (caret != null)
      caret.draw(context.getGraphics());
  }

  @Override
  public AttributeSet getAttributeSet() {
    return attrs;
  }

  @Override
  public Cursor getCursor() {
    return cursor;
  }

  @Override
  public String getDescription() {
    return S.get("textToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("textTool");
  }

  @Override
  public String getName() {
    return "Text Tool";
  }

  @Override
  public void keyPressed(Canvas canvas, KeyEvent e) {
    if (caret != null) {
      caret.keyPressed(e);
      canvas.getProject().repaintCanvas();
    }
  }

  @Override
  public void keyReleased(Canvas canvas, KeyEvent e) {
    if (caret != null) {
      caret.keyReleased(e);
      canvas.getProject().repaintCanvas();
    }
  }

  @Override
  public void keyTyped(Canvas canvas, KeyEvent e) {
    if (caret != null) {
      caret.keyTyped(e);
      canvas.getProject().repaintCanvas();
    }
  }

  @Override
  public void mouseDragged(Canvas canvas, MouseEvent e) {
    Project proj = canvas.getProject();
    Circuit circ = canvas.getCircuit();

    if (!proj.getLogisimFile().contains(circ)) {
      if (caret != null)
        caret.cancelEditing();
      canvas.setErrorMessage(S.getter("cannotModifyError"));
      return;
    }

    // Maybe user is clicking within the current caret.
    if (caret != null && !caretNeedsSelectAll) {
      caret.mouseDragged(e);
      proj.repaintCanvas();
    }
  }

  @Override
  public void mousePressed(Canvas canvas, MouseEvent e) {
    caretNeedsSelectAll = false;
    Project proj = canvas.getProject();
    Circuit circ = canvas.getCircuit();

    if (!proj.getLogisimFile().contains(circ)) {
      if (caret != null)
        caret.cancelEditing();
      canvas.setErrorMessage(S.getter("cannotModifyError"));
      return;
    }

    boolean hadCaret = caret != null;;
    // Maybe user is clicking within the current caret.
    if (hadCaret) {
      if (caret.getVisibleBounds().contains(e.getX(), e.getY())) { // Yes
        caret.mousePressed(e);
        proj.repaintCanvas();
        return;
      } else { // No. End the current caret.
        caret.stopEditing();
      }
    }
    // caret will be null at this point

    // Otherwise search for a new caret.
    int x = e.getX();
    int y = e.getY();
    Location loc = Location.create(x, y);
    ComponentUserEvent event = new ComponentUserEvent(canvas, x, y);

    // First search in selection.
    for (Component comp : proj.getSelection().getComponentsContaining(loc)) {
      TextEditable editable = (TextEditable) comp.getFeature(TextEditable.class);
      if (editable != null) {
        caret = editable.getTextCaret(event);
        if (caret != null) {
          proj.getFrame().viewComponentAttributes(circ, comp);
          caretComponent = comp;
          caretCreatingText = false;
          break;
        }
      }
    }

    // Then search in circuit
    if (caret == null) {
      for (Component comp : circ.getAllVisiblyContaining(loc)) {
        TextEditable editable = (TextEditable) comp.getFeature(TextEditable.class);
        if (editable != null) {
          caret = editable.getTextCaret(event);
          if (caret != null) {
            proj.getFrame().viewComponentAttributes(circ, comp);
            caretComponent = comp;
            caretCreatingText = false;
            break;
          }
        }
      }
    }

    // if nothing found, create a new Text component, if we didn't already have one
    if (caret == null) {
      if (hadCaret || loc.getX() < 0 || loc.getY() < 0)
        return;

      if ((e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0) {
        Canvas.snapToGrid(e);
        x = e.getX();
        y = e.getY();
        loc = Location.create(x, y);
      }

      AttributeSet copy = (AttributeSet) attrs.clone();
      caretComponent = Text.FACTORY.createComponent(loc, copy);
      caretCreatingText = true;
      TextEditable editable = (TextEditable) caretComponent.getFeature(TextEditable.class);
      if (editable != null) {
        caret = editable.getTextCaret(event);
        proj.getFrame().viewComponentAttributes(circ, caretComponent);
        caretNeedsSelectAll = true;
      }
    }

    if (caret != null) {
      caretCanvas = canvas;
      caretCircuit = canvas.getCircuit();
      caret.addCaretListener(listener);
      caretCircuit.addCircuitWeakListener(null, listener);
      proj.getFrame().setEditHandler(caret.getEditHandler());
    }
    proj.repaintCanvas();
  }

  @Override
  public void mouseReleased(Canvas canvas, MouseEvent e) {
    Project proj = canvas.getProject();
    Circuit circ = canvas.getCircuit();

    if (!proj.getLogisimFile().contains(circ)) {
      if (caret != null)
        caret.cancelEditing();
      canvas.setErrorMessage(S.getter("cannotModifyError"));
      return;
    }

    if (caret != null) {
      if (caretNeedsSelectAll && caret instanceof TextFieldCaret) {
        ((TextFieldCaret)caret).selectAll();
      } else if (caretNeedsSelectAll && caret instanceof TextCaret) {
        ((TextCaret)caret).selectAll();
      } else {
        caret.mouseReleased(e);
      }
      caretNeedsSelectAll = false;
      proj.repaintCanvas();
    }
  }
  
  private static final Icon toolIcon = Icons.getIcon("text.gif");

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    Graphics g = c.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(c.getDestination(), g, x + 2, y + 2);
    } else {
      int[] xp = { x + 5, x + 5, x + 9, x + 12, x + 14, x + 11, x + 16 };
      int[] yp = { y, y + 17, y + 12, y + 18, y + 18, y + 12, y + 12 };
      g.setColor(java.awt.Color.black);
      g.fillPolygon(xp, yp, xp.length);
    }
  }

  public static String getText(Component comp) {
    return comp.getAttributeSet().getValue(Text.ATTR_TEXT);
  }

  public Component create(Location loc, String text) {
    AttributeSet copy = (AttributeSet) attrs.clone();
    Component comp = Text.FACTORY.createComponent(loc, copy);
    comp.getAttributeSet().setAttr(Text.ATTR_TEXT, text);
    return comp;
  }

  public static TextTool getToolFromProject(Project proj) {
    Library base = proj.getLogisimFile().getLibrary("Base");
    if (base == null)
      return null;
    Tool tool = base.getTool("Text Tool");
    if (tool instanceof TextTool)
      return (TextTool)tool;
    return null;
  }
}
