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

package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Graphics2D;
import java.awt.Color;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.util.UnmodifiableList;
import com.cburch.logisim.util.GraphicsUtil;

public class SevenSegmentShape extends DynamicElement {

  public SevenSegmentShape(int x, int y, DynamicElement.Path p) {
    super(p, Bounds.create(x, y, 14, 20));
    calculateBounds();
  }

  void calculateBounds() {
    int digits = path.leaf().getAttributeSet().getValue(SevenSegment.ATTR_DIGITS).intValue();
    int x = bounds.getX();
    int y = bounds.getY();
    bounds = Bounds.create(x, y, 14*digits, 20);
  }

  private static final List<Attribute<?>> ATTRIBUTES
      = UnmodifiableList.create(new Attribute<?>[] {
        ATTR_LABEL, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR });

  @Override
  public List<Attribute<?>> getAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public void paintDynamic(Graphics2D g, CircuitState state) {
    calculateBounds();
    SevenSegmentAttributes attrs = (SevenSegmentAttributes)path.leaf().getAttributeSet();
    Color bgColor = attrs.getValue(Io.ATTR_BACKGROUND);
    int x = bounds.getX();
    int y = bounds.getY();
    int w = bounds.getWidth();
    int h = bounds.getHeight();
    GraphicsUtil.switchToWidth(g, 1);
    if (bgColor.getAlpha() != 0) {
      g.setColor(bgColor);
      g.fillRect(x, y, w, h);
    }
    g.setColor(Color.BLACK);
    g.drawRect(x, y, w, h);
    g.setColor(Color.DARK_GRAY);

    int digits = attrs.getValue(SevenSegment.ATTR_DIGITS);

    SevenSegment.State data = null;
    long ticks = 0;
    int persistDuration = 0;
    if (state != null) {
      data = (SevenSegment.State)getData(state);
      ticks = state.getPropagator().getTickCount();
      persistDuration = digits == 1 ? 0 : attrs.getValue(SevenSegment.ATTR_PERSIST).intValue();
    }
    for (int digit = 0; digit < digits; digit++) {
      for (int i = 0; i <= 7; i++) {
        if (data != null) {
          g.setColor(attrs.getColor(data.get(digit, i, ticks, persistDuration)));
        }
        if (i < 7) {
          int[] seg = SEGMENTS[i];
          g.fillRect(x + seg[0], y + seg[1], seg[2], seg[3]);
        } else {
          g.fillOval(x + 11, y + 17, 2, 2); // draw decimal point
        }
      }
      x += 14;
    }
    drawLabel(g);
  }

  static final int SEGMENTS[][] = new int[][]{
      new int[] {3, 1, 6, 2},
      new int[] {9, 3, 2, 6},
      new int[] {9, 11, 2, 6},
      new int[] {3, 17, 6, 2},
      new int[] {1, 11, 2, 6},
      new int[] {1, 3, 2, 6},
      new int[] {3, 9, 6, 2},
  };

  @Override
  public Element toSvgElement(Document doc) {
    return toSvgElement(doc.createElement("visible-sevensegment"));
  }

  @Override
  public String getDisplayName() {
    return S.get("sevenSegmentComponent");
  }

  @Override
  public String toString() {
    return "Seven Segment:" + getBounds();
  }
}
