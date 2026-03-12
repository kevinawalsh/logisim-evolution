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

package com.cburch.logisim.circuit;
import static com.cburch.logisim.circuit.Strings.S;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Stroke;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentListener;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.CustomHandles;
import com.cburch.logisim.util.Cache;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.GraphicsUtil;

public final class Wire
  implements Component, AttributeSet, CustomHandles, Iterable<Location> {

  private class EndList extends AbstractList<EndData> {
    @Override
    public EndData get(int i) {
      return getEnd(i);
    }

    @Override
    public int size() {
      return 2;
    }
  }

  public static Wire create(Location e0, Location e1) {
    if (e0.equals(e1)) {
      try { throw new IllegalArgumentException("wire with zero length"); }
      catch (Exception e) { Debug.error("creating wire", e); }
    }
    boolean is_x_equal = e0.x == e1.x;
    if ((is_x_equal && e0.y > e1.y) || (!is_x_equal && e0.x > e1.x)) {
      Location p = e0;
      e0 = e1;
      e1 = p;
    }
    int hashCode = e0.hashCode() * 31 + e1.hashCode();
    Wire w = cache.get(hashCode);
    if (w != null && w.e0.equals(e0) && w.e1.equals(e1))
      return w;
    Wire ret = new Wire(is_x_equal, e0, e1);
    cache.put(hashCode, ret);
    return ret;
  }

  /** Stroke width when drawing wires. */
  public static final int WIDTH = 3;
  public static final int WIDTH_BUS = 4;
  public static final int HIGHLIGHTED_WIDTH = 4;
  public static final int HIGHLIGHTED_WIDTH_BUS = 5;
  public static final Stroke HIGHLIGHTED_STROKE = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);
  public static final double DOT_MULTIPLY_FACTOR = 1.35; /* multiply factor for the intersection points */
  public static final AttributeOption VALUE_HORZ = new AttributeOption(
      "horz", S.getter("wireDirectionHorzOption"));
  public static final AttributeOption VALUE_VERT = new AttributeOption(
      "vert", S.getter("wireDirectionVertOption"));

  public static final Attribute<AttributeOption> dir_attr = Attributes
      .forOption("direction", S.getter("wireDirectionAttr"),
          new AttributeOption[] { VALUE_HORZ, VALUE_VERT });
  public static final Attribute<Integer> len_attr = Attributes.forInteger(
      "length", S.getter("wireLengthAttr"));

  private static final List<Attribute<?>> ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { dir_attr, len_attr });

  private static final Cache<Wire> cache = new Cache<>();

  final Location e0, e1;
  final boolean is_x_equal;

  private Wire(boolean is_x_equal, Location e0, Location e1) {
    this.is_x_equal = is_x_equal;
    this.e0 = e0;
    this.e1 = e1;
  }

  // Wire is immutable and never issues AttributeEvents, so we don't need to track listeners.
  public void addAttributeWeakListener(Object owner, AttributeListener l) {
  }

  // Wire never issues ComponentEvents, so we don't need to track listeners.
  public void addComponentWeakListener(Object owner, ComponentListener e) {
  }

  public boolean nominallyContains(Location q) {
    if (is_x_equal)
      return e0.x - 2 <= q.x && q.x <= e1.x + 2
          && e0.y - 0 <= q.y && q.y <= e1.y + 0;
    else
      return e0.x - 0 <= q.x && q.x <= e1.x + 0
          && e0.y - 2 <= q.y && q.y <= e1.y + 2;
  }

  public boolean nominallyNearby(Location q) {
    if (is_x_equal)
      return e0.x - 4 <= q.x && q.x <= e1.x + 4
          && e0.y - 4 <= q.y && q.y <= e1.y + 4;
    else
      return e0.x - 4 <= q.x && q.x <= e1.x + 4
          && e0.y - 4 <= q.y && q.y <= e1.y + 4;
  }

  public boolean visiblyContains(Location pt) {
    return nominallyContains(pt);
  }

  public void draw(ComponentDrawContext context) {
    CircuitState state = context.getCircuitState();
    Graphics g = context.getGraphics();
    GraphicsUtil.switchToWidth(g, WIDTH);
    g.setColor(state.getValue(e0).getColor());
    g.drawLine(e0.x, e0.y, e1.x, e1.y);
  }

  public void drawHandles(ComponentDrawContext context) {
    context.drawHandle(e0);
    context.drawHandle(e1);
  }

  public boolean endsAt(Location pt) {
    return e0.equals(pt) || e1.equals(pt);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Wire))
      return false;
    Wire w = (Wire) other;
    return w.e0.equals(this.e0) && w.e1.equals(this.e1);
  }

  public void expose(ComponentDrawContext context) {
    context.getDestination().repaint(e0.x - 5, e0.y - 5,
        e1.x - e0.x + 10, e1.y - e0.y + 10);
  }

  public AttributeSet getAttributeSet() {
    return this;
  }

  public Bounds getNominalBounds() {
    return Bounds.create(e0.x - 2, e0.y - 2,
        e1.x - e0.x + 5, e1.y - e0.y + 5);
  }

  public Bounds getVisibleBounds() {
    return getNominalBounds();
  }

  public EndData getEnd(int index) {
    Location loc = getEndLocation(index);
    return new EndData(loc, BitWidth.UNKNOWN, EndData.INPUT_OUTPUT);
  }

  public Location getEnd0() { // top-most or left-most point
    return e0;
  }

  public Location getEnd1() { // bottom-most or right-most point
    return e1;
  }

  public Location getEndLocation(int index) {
    return index == 0 ? e0 : e1;
  }

  public List<EndData> getEnds() {
    return new EndList();
  }

  public ComponentFactory getFactory() {
    return WireFactory.instance;
  }

  public Object getFeature(Object key) {
    if (key == CustomHandles.class)
      return this;
    return null;
  }

  public int getLength() {
    return (e1.y - e0.y) + (e1.x - e0.x);
  }

  // location/extent methods
  public Location getLocation() {
    return e0;
  }

  public Location getOtherEnd(Location loc) {
    return loc.equals(e0) ? e1 : e0;
  }

  public Location getOtherEnd(Wire other) {
    return other.endsAt(e0) ? e1 : e0;
  }

  @Override
  public Object clone() { // Wire is immutable, so no need to clone
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <V> V getValue(Attribute<V> attr) {
    if (attr == dir_attr)
      return (V) (is_x_equal ? VALUE_VERT : VALUE_HORZ);
    else if (attr == len_attr)
      return (V) Integer.valueOf(getLength());
    else
      return null;
  }

  @Override
  public List<Attribute<?>> getAttributes() { return ATTRIBUTES; }
  @Override
  public boolean isReadOnly(Attribute<?> attr) { return true; }
  @Override
  public boolean isToSave(Attribute<?> attr) { return false; }
  @Override
  public void removeAttributeWeakListener(Object owner, AttributeListener l) { }
  @Override
  public void removeComponentWeakListener(Object owner, ComponentListener e) { }
  @Override
  public <V> void changeAttr(Attribute<V> attr, V value) { }

  @Override
  public int hashCode() {
    return e0.hashCode() * 31 + e1.hashCode();
  }

  public boolean isParallel(Wire other) {
    return this.is_x_equal == other.is_x_equal;
  }

  public boolean isVertical() {
    return is_x_equal;
  }

  public Iterator<Location> iterator() {
    return new WireIterator(e0, e1);
  }

  public boolean overlaps(Wire q, boolean includeEnds) {
    if (is_x_equal != q.is_x_equal)
      return false;
    Location q0 = q.e0;
    Location q1 = q.e1;
    if (is_x_equal) {
      if (includeEnds)
        return e1.y >= q0.y && e0.y <= q1.y;
      else
        return e1.y > q0.y && e0.y < q1.y;
    } else {
      if (includeEnds)
        return e1.x >= q0.x && e0.x <= q1.x;
      else
        return e1.x > q0.x && e0.x < q1.x;
    }
  }

  public void propagate(CircuitState state) {
    // Normally this is handled by CircuitWires, and so it won't get
    // called. The exception is when a wire is added or removed
    try { throw new Exception("** never happens? **"); }
    catch (Exception e) { Debug.error("Wire.propagate() is vestigial", e); }
    // state.markPointAsDirty(e0);
    // state.markPointAsDirty(e1);
  }

  public boolean sharesEnd(Wire other) {
    return this.e0.equals(other.e0) || this.e1.equals(other.e0)
        || this.e0.equals(other.e1) || this.e1.equals(other.e1);
  }

  @Override
  public String toString() {
    return "Wire[" + e0 + "-" + e1 + "]";
  }
}
