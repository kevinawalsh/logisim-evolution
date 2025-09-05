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

import java.util.Set;
import java.util.ArrayList;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.Icon;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;

public final class PokeTool extends Tool {
  private class Listener implements CircuitListener {
    public void circuitChanged(CircuitEvent event) {
      Circuit circ = pokedCircuit;
      if (event.getCircuit() == circ
          && circ != null
          && (event.getAction() == CircuitEvent.ACTION_REMOVE
            || event.getAction() == CircuitEvent.ACTION_CLEAR)
          && !circ.contains(pokedComponent)) {
        removeCaret(false);
      }
    }
  }

  private static class WireCaret extends AbstractCaret {
    Canvas canvas;
    Wire wire;
    int x;
    int y;
    int radixChoice;
    ArrayList<RadixOption> ordering;

    static final Font FONT = new Font("Monospaced", Font.PLAIN, 12);
    static final Font SMALL = new Font("SansSerif", Font.PLAIN, 8);
    // We render only a limited set of characters, and there
    // is a lot of extra space above and below, so we adjust
    // the heights a bit to make it tighter.
    static final int FONT_HEIGHT_ADJUST = 2;
    static final int SMALL_HEIGHT_ADJUST = 2;
    
    static final RadixOption BIN = RadixOption.RADIX_2;
    static final RadixOption OCT = RadixOption.RADIX_8;
    static final RadixOption HEX = RadixOption.RADIX_16;
    static final RadixOption U10 = RadixOption.RADIX_10_UNSIGNED;
    static final RadixOption S10 = RadixOption.RADIX_10_SIGNED;
    static final RadixOption[] DEFAULT_ORDER = { BIN, U10, S10, OCT, HEX };

    WireCaret(Canvas c, Wire w, int x, int y, AttributeSet opts) {
      canvas = c;
      wire = w;
      setPoint(x, y);
      setBounds(w.getNominalBounds());

      // There are 5 radix options: 2, 8, 16, u10, s10
      //
      // Some may look identical, depending on the value.
      //
      // In some case, the display is huge, e.g. 32-bit binary.
      //
      // For single-bit wires, we display only binary, unlabeled.
      //
      // We will display binary alone, then u10 and s10 together, then u16 and
      // u8 together. App preferences determine which to see first, which to see
      // second, and the remainder will be last.
      ordering = new ArrayList<>();
      RadixOption r1 = RadixOption.decode(AppPreferences.POKE_WIRE_RADIX1.get());
      if (r1 == BIN) {
        ordering.add(BIN);
      } else if (r1 == U10 || r1 == S10) {
        ordering.add(U10);
        ordering.add(S10);
      } else if (r1 == OCT || r1 == HEX) {
        ordering.add(HEX);
        ordering.add(OCT);
      }
      RadixOption r2 = RadixOption.decode(AppPreferences.POKE_WIRE_RADIX2.get());
      if (r1 == BIN) {
        ordering.add(BIN);
      } else if (r1 == U10 || r1 == S10) {
        ordering.add(U10);
        ordering.add(S10);
      } else if (r1 == OCT || r1 == HEX) {
        ordering.add(HEX);
        ordering.add(OCT);
      }
      for (RadixOption r : DEFAULT_ORDER) {
        if (!ordering.contains(r))
          ordering.add(r);
      }
    }

    private void setPoint(int x, int y) {
      // snap to exact wire path
      if (wire.isVertical()) {
        this.x = wire.getEnd0().x;
        this.y = Math.min(Math.max(y,  wire.getEnd0().y), wire.getEnd1().y);
      } else {
        this.x = Math.min(Math.max(x,  wire.getEnd0().x), wire.getEnd1().x);
        this.y = wire.getEnd0().y;
      }
    }

    private void nextChoice() {
      if (ordering.get(radixChoice) == BIN)
        radixChoice = (radixChoice + 1) % 5;
      else
        radixChoice = (radixChoice + 2) % 5;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      int xx = e.getPoint().x;
      int yy = e.getPoint().y;
      // nearby click: only change display
      // distant click: only move cursor
      if (Math.abs(x - xx) < 10 && Math.abs(y - yy) < 10)
        nextChoice();
      else
        setPoint(xx, yy);
    }

    @Override
    public void keyTyped(KeyEvent e) {
      char ch = e.getKeyChar();
      if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
        nextChoice();
        e.consume();
      }
    }

    @Override
    public void draw(Graphics g) {
      Value v = canvas.getCircuitState().getValue(wire.getEnd0());
    
      String vStr0, vStr1 = null, lStr0 = null, lStr1 = null;
      if (v.getWidth() == 1) {
        // binary, unlabeled
        vStr0 = BIN.toString(v);
      } else {
        vStr0 = ordering.get(radixChoice).toString(v);
        lStr0 = ordering.get(radixChoice).toDisplayString().toLowerCase();
        if (ordering.get(radixChoice) != BIN) {
          vStr1 = ordering.get(radixChoice + 1).toString(v);
          lStr1 = ordering.get(radixChoice + 1).toDisplayString().toLowerCase();
        }
      }

      // TODO: improve font hinting

      Rectangle rv0 = GraphicsUtil.getTextBounds(g, FONT, vStr0, 0, 0, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM);
      if (rv0.height > FONT_HEIGHT_ADJUST)
        rv0.height -= FONT_HEIGHT_ADJUST;
      Rectangle rl0 = (lStr0 != null) ?
          GraphicsUtil.getTextBounds(g, SMALL, lStr0, 0, 0, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM)
          : new Rectangle(0, 0, 0, 0);
      if (rl0.height > SMALL_HEIGHT_ADJUST)
        rl0.height -= SMALL_HEIGHT_ADJUST;

      Rectangle rv1 = (vStr1 != null) ?
          GraphicsUtil.getTextBounds(g, FONT, vStr1, 0, 0, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM)
          : new Rectangle(0, 0, 0, 0);
      if (rv1.height > FONT_HEIGHT_ADJUST)
        rv1.height -= FONT_HEIGHT_ADJUST;
      Rectangle rl1 = (lStr1 != null) ?
          GraphicsUtil.getTextBounds(g, SMALL, lStr1, 0, 0, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM)
          : new Rectangle(0, 0, 0, 0);
      if (rl1.height > SMALL_HEIGHT_ADJUST)
        rl1.height -= SMALL_HEIGHT_ADJUST;

      int w0 = Math.max(rv0.width, rl0.width);
      int w1 = Math.max(rv1.width, rl1.width);
      if (w1 > 0)
        w0 = w1 = Math.max(w0, w1); // visually, equal size looks nicer?

      int lmargin = 4; // margin around entire box
      int rmargin = 4; // margin around entire box
      int tmargin = 2; // margin around entire box
      int bmargin = 0; // margin around entire box
      int mid = w1 > 0 ? 9 : 0; // gap between left and right sides
      int w = lmargin + w0 + mid + w1 + rmargin; // total width

      int pad = 0; // extra left and right space
      if (w < 45) {
        pad = (45 - w) / 2;
        w = 45;
      }

      int h = tmargin + Math.max(rv0.height, rv1.height) + bmargin; // total height
      if (lStr0 != null) {
        h += Math.max(rl0.height, rl1.height);
      }

      Rectangle r = canvas.getViewableRect();
      int dx = Math.max(0, w - (r.x + r.width - x));
      int dxx1 = (dx > w/2) ? -30 : 15; // offset of callout stem
      int dxx2 = (dx > w/2) ? -15 : 30; // offset of callout stem
      // The point of the stem seems to go about 1 or 2 pixels
      // past the specified point, because of bevels. So
      // adjust the point towards the box.
      int cxx = (dxx1 < 0) ? -2 : 2;
      int xx, yy;
      int xp[], yp[];
      if (y - 15 - h <= r.y) {
        // callout below cursor
        int cyy = 1;
        xx = x - dx; yy = y + 15 + h; // bottom left corner of box
        xp = new int[] { xx, xx,   x+dxx1, x+cxx, x+dxx2, xx+w, x+w };
        yp = new int[] { yy, yy-h, yy-h,   y+cyy, yy-h,   yy-h, yy  };
      } else {
        // callout above cursor
        int cyy = -1;
        xx = x - dx; yy = y - 15; // bottom left corner of box
        xp = new int[] { xx, xx,   xx+w, xx+w, x+dxx2, x+cxx, x+dxx1 };
        yp = new int[] { yy, yy-h, yy-h, yy,   yy,     y+cyy, yy    };
      }

      g.setColor(caretColor);
      g.fillPolygon(xp, yp, xp.length);
      g.setColor(Color.BLACK);
      g.drawPolygon(xp, yp, xp.length);

      if (vStr1 != null) {
        g.setColor(Color.GRAY);
        g.drawLine(
            xx + lmargin + pad + w0 + mid/2, yy - h + Math.max(tmargin, bmargin) + 1,
            xx + lmargin + pad + w0 + mid/2, yy - Math.max(tmargin, bmargin) - 1);
        
        g.setColor(Color.BLACK);
        GraphicsUtil.drawText(g, FONT, vStr1, 
            xx + w - rmargin - pad - w1/2,
            yy - h + tmargin + rv0.height,
            GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
        GraphicsUtil.drawText(g, SMALL, lStr1, 
            xx + w - rmargin - pad - w1/2,
            yy - bmargin,
            GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
      }

      GraphicsUtil.drawText(g, FONT, vStr0, 
          xx + pad + lmargin + w0/2,
          yy - h + tmargin + rv0.height,
          GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
      if (lStr0 != null) {
        GraphicsUtil.drawText(g, SMALL, lStr0, 
            xx + pad + lmargin + w0/2,
            yy - bmargin,
            GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
      }
    }
  }

  private static final Icon toolIcon = Icons.getIcon("poke.gif");

  private static final Color caretColor = new Color(255, 255, 150);

  private static Cursor cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

  private Listener listener = new Listener();
  private Circuit pokedCircuit;
  private Component pokedComponent;
  private Caret pokeCaret;

  private PokeTool() { }

  public static final PokeTool SINGLETON = new PokeTool();

  @Override
  public boolean isBuiltin() { return true; }

  @Override
  public void deselect(Canvas canvas) {
    removeCaret(true);
    canvas.setHighlightedWires(WireSet.EMPTY);
  }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    if (pokeCaret != null)
      pokeCaret.draw(context.getGraphics());
  }

  @Override
  public Cursor getCursor() {
    return cursor;
  }

  @Override
  public String getDescription() {
    return S.get("pokeToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("pokeTool");
  }

  @Override
  public String getName() {
    return "Poke Tool";
  }

  private void syntheticMouseEventByLabel(Canvas canvas, KeyEvent e, boolean pressed) {
    char ch = e.getKeyChar();
    if (ch == KeyEvent.CHAR_UNDEFINED)
      return;
    Circuit circ = canvas.getCircuit();
    Set<Component> hits = circ.getByLabelCaseInsensitive("" + ch);
    if (hits == null || hits.isEmpty())
      return;
    ComponentUserEvent ce = null;
    MouseEvent me = null;
    for (Component hit : hits) {
      Pokable p = (Pokable)hit.getFeature(Pokable.class);
      if (p == null)
        continue;
      if (ce == null)
        ce = new ComponentUserEvent(canvas, -1, -1);
      Caret caret = p.getPokeCaret(ce);
      // send a NOBUTTON event, caret could use this to detect
      // that this is a synthetic event and ignore, if it wants.
      if (pressed) {
        if (me == null)
          me = new MouseEvent(canvas,
              MouseEvent.MOUSE_PRESSED, e.getWhen(), 0 /* modifiers */,
              -1 /* x */, -1 /* y */, 1 /* clickCount */, false /* popup */);
        caret.mousePressed(me);
      } else {
        if (me == null)
          me = new MouseEvent(canvas,
              MouseEvent.MOUSE_RELEASED, e.getWhen(), 0 /* modifiers */,
              -1 /* x */, -1 /* y */, 1 /* clickCount */, false /* popup */);
        caret.mouseReleased(me);
      }
    }
    // TODO: is it important to send a matching release for every press?
  }

  @Override
  public void keyPressed(Canvas canvas, KeyEvent e) {
    if (pokeCaret != null) {
      pokeCaret.keyPressed(e);
      canvas.getProject().repaintCanvas();
      if (e.isConsumed() || pokeCaret.capturesTextInput())
        return;
    }
    syntheticMouseEventByLabel(canvas, e, true);
  }

  @Override
  public void keyReleased(Canvas canvas, KeyEvent e) {
    if (pokeCaret != null) {
      pokeCaret.keyReleased(e);
      canvas.getProject().repaintCanvas();
      if (e.isConsumed() || pokeCaret.capturesTextInput())
        return;
    }
    syntheticMouseEventByLabel(canvas, e, false);
  }

  @Override
  public void keyTyped(Canvas canvas, KeyEvent e) {
    if (pokeCaret != null) {
      pokeCaret.keyTyped(e);
      canvas.getProject().repaintCanvas();
    }
  }

  @Override
  public void mouseDragged(Canvas canvas, Graphics g, MouseEvent e) {
    if (pokeCaret != null) {
      pokeCaret.mouseDragged(e);
      canvas.getProject().repaintCanvas();
    }
  }

  @Override
  public void mousePressed(Canvas canvas, Graphics g, MouseEvent e) {
    int x = e.getX();
    int y = e.getY();
    Location loc = Location.create(x, y);
    boolean dirty = false;
    if (pokeCaret != null && !pokeCaret.getBounds(g).contains(loc)) {
      canvas.setHighlightedWires(WireSet.EMPTY);
      dirty = true;
      removeCaret(true);
    } else if (!(pokeCaret instanceof WireCaret)) {
      canvas.setHighlightedWires(WireSet.EMPTY);
    }
    if (pokeCaret == null) {
      ComponentUserEvent event = new ComponentUserEvent(canvas, x, y);
      Circuit circ = canvas.getCircuit();
      for (Component c : circ.getAllVisiblyContaining(loc, g)) {
        if (pokeCaret != null)
          break;

        if (c instanceof Wire) {
          Caret caret = new WireCaret(canvas, (Wire) c, x, y, canvas
              .getProject().getOptions().getAttributeSet());
          setPokedComponent(circ, c, caret);
          canvas.setHighlightedWires(circ.getWireSet((Wire) c));
        } else {
          Pokable p = (Pokable) c.getFeature(Pokable.class);
          if (p != null) {
            Caret caret = p.getPokeCaret(event);
            setPokedComponent(circ, c, caret);
            AttributeSet attrs = c.getAttributeSet();
            if (attrs != null && attrs.getAttributes().size() > 0) {
              Project proj = canvas.getProject();
              proj.getFrame().viewComponentAttributes(circ, c);
            }
          }
        }
      }
    }
    if (pokeCaret != null) {
      dirty = true;
      pokeCaret.mousePressed(e);
    }
    if (dirty)
      canvas.getProject().repaintCanvas();
  }

  @Override
  public void mouseReleased(Canvas canvas, Graphics g, MouseEvent e) {
    if (pokeCaret != null) {
      pokeCaret.mouseReleased(e);
      canvas.getProject().repaintCanvas();
    }
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    Graphics g = c.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(c.getDestination(), g, x + 2, y + 2);
    } else {
      g.setColor(Color.BLACK);
      g.drawLine(x + 4, y + 2, x + 4, y + 17);
      g.drawLine(x + 4, y + 17, x + 1, y + 11);
      g.drawLine(x + 4, y + 17, x + 7, y + 11);

      g.drawLine(x + 15, y + 2, x + 15, y + 17);
      g.drawLine(x + 15, y + 2, x + 12, y + 8);
      g.drawLine(x + 15, y + 2, x + 18, y + 8);
    }
  }

  private void removeCaret(boolean normal) {
    Circuit circ = pokedCircuit;
    Caret caret = pokeCaret;
    if (caret != null) {
      if (normal)
        caret.stopEditing();
      else
        caret.cancelEditing();
      circ.removeCircuitWeakListener(null, listener);
      pokedCircuit = null;
      pokedComponent = null;
      pokeCaret = null;
    }
  }

  private void setPokedComponent(Circuit circ, Component comp, Caret caret) {
    removeCaret(true);
    pokedCircuit = circ;
    pokedComponent = comp;
    pokeCaret = caret;
    if (caret != null) {
      circ.addCircuitWeakListener(null, listener);
    }
  }

  public boolean isScrollable() {
    return pokeCaret != null && !(pokeCaret instanceof WireCaret);
  }
}
