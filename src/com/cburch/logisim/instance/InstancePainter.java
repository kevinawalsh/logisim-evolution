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

import java.awt.Color;
import java.awt.Graphics2D;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.proj.Project;

// This is more like ComponentPainter maybe?
public class InstancePainter implements InstanceState {

  private final ComponentDrawContext context; // non-null
  private final CircuitState circState; // may be null

  // InstancePainter is used to help draw components. It can be repurposed
  // for drawing multiple components. There will either be a Component, or a
  // Factory+AttributeSet pair, but never both.
  //
  //     comp     factory  attrs
  //     -------- -------- --------
  // (a) present  null     null     -- e.g. drawing a Component in a circuit
  // (b) null     present  present  -- e.g. drawing a ghost before placing
  // (c) null     null     null     -- when not being used
  //
  // Many methods only work in case (a). Others work only in case (b). Most fail
  // in (c). FIXME: This should probably be two different classes, possibly with
  // some shared ancestor for the 
  
  private Component comp;
  private ComponentFactory factory;
  private AttributeSet attrs;

  public InstancePainter(ComponentDrawContext context, Component comp) {
    this.context = context;
    this.circState = context.getCircuitState();
    this.comp = comp;
  }

  public void drawBounds() { // (a)
    if (comp != null)
      context.drawBounds(comp);
  }

  public void drawClock(int i, Direction dir) { // (a)
    if (comp != null)
      context.drawClock(comp, i, dir);
  }

  public void drawClockSymbol(int xpos, int ypos) { // (a)
    if (comp != null)
      context.drawClockSymbol(comp, xpos, ypos);
  }

  public void drawDongle(int x, int y) { // any ... just draws a dot
    context.drawDongle(x, y);
  }

  public void drawHandle(int x, int y) { // any ... just draws a square
    context.drawHandle(x, y);
  }

  public void drawHandle(Location loc) { // any ... just draws a square
    context.drawHandle(loc);
  }

  public void drawHandles() { // (a)
    if (comp != null)
      context.drawHandles(comp);
  }

  // Note: drawLabel() only works when a Component is present,
  // since it relies on a Component's textField. In particular, it doesn't work
  // within drawGhost() or any other cases where we only have a Factory and
  // AttributeSet, rather than a Component.
  public void drawLabel() { // (a)
    if (comp != null) {
      Color c = getAttributeValue(StdAttr.LABEL_COLOR);
      Graphics2D g = getGraphics();
      Color old = g.getColor();
      g.setColor(c == null ? Color.BLACK : c);
      comp.drawLabel(context);
      g.setColor(old);
    }
  }

  public void drawPort(int i) { // (a)
    if (comp != null)
      context.drawPin(comp, i);
  }

  public void drawPort(int i, String label, Direction dir) { // (a)
    if (comp != null)
      context.drawPin(comp, i, label, dir);
  }

  public void drawPorts() { // (a)
    if (comp != null)
      context.drawPins(comp);
  }

  public void drawRectangle(Bounds bds, String label) { // any ... just draws a box with text
    context.drawRectangle(bds.getX(), bds.getY(), bds.getWidth(),
        bds.getHeight(), label);
  }

  public void drawRectangle(int x, int y, int width, int height, String label) { // any ... just draws a box with text
    context.drawRectangle(x, y, width, height, label);
  }

  @Override
  public void queueForPropagation() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.queueForPropagation without state");
    circState.queueForPropagation(comp);
  }

  @Override
  public void fireInvalidated() { // (a)
    if (comp != null)
      comp.fireInvalidated();
  }

  public AttributeSet getAttributeSet() { // (a) or (b)
    return comp == null ? attrs : comp.getAttributeSet();
  }

  public <E> E getAttributeValue(Attribute<E> attr) { // (a) or (b)
    return getAttributeSet().getValue(attr);
  }
  
  public Bounds getNominalBounds() { // (a) or (b)
    return comp == null
        ? factory.getOffsetBounds(attrs)
        : comp.getNominalBounds();
  }

  public Circuit getCircuit() { // any
    return context.getCircuit();
  }

  public CircuitState getCircuitState() { // any
    return circState;
  }

  public java.awt.Component getDestination() { // any
    return context.getDestination();
  }

  public InstanceFactory getFactory() { // (a) or (b)
    if (comp instanceof InstanceComponent)
      return (InstanceFactory)comp.getFactory();
    else if (factory instanceof InstanceFactory)
      return (InstanceFactory)factory;
    else
      return null;
  }

  public Object getGateShape() { // any ... just gets app preferences
    return context.getGateShape();
  }

  // Unlike swing/awt Component.getGraphics(), this does *not* create a new
  // Graphics object, but only borrows the existing Graphics object. The caller
  // should not dispose it.
  public Graphics2D getGraphics() { // any
    return context.getGraphics();
  }

  public WireSet getHighlightedWires() { // any
    return context.getHighlightedWires();
  }

  public Instance getInstance() { // (a)
    return comp instanceof InstanceComponent
        ? ((InstanceComponent)comp).getInstance() :  null;
  }

  public Component getComponent() { // (a)
    return comp;
  }

  public Location getLocation() { // (a)
    return comp == null ? Location.create(0, 0) : comp.getLocation();
  }

  public Bounds getNominalOffsetBounds() { // (a) or (b)
    if (comp == null) {
      return factory.getOffsetBounds(attrs);
    } else {
      Location loc = comp.getLocation();
      return comp.getNominalBounds().translate(-loc.getX(), -loc.getY());
    }
  }

  public Value getPortValue(int portIndex) { // (a)
    if (comp != null && circState != null) {
      return circState.getValue(comp.getEnd(portIndex).getLocation());
    } else {
      return Value.UNKNOWN;
    }
  }

  public Project getProject() { // any
    return circState.getProject();
  }

  public boolean getShowState() { // any
    return context.getShowState();
  }

  public int getTickCount() { // any
    return circState.getPropagator().getTickCount();
  }

  public boolean isCircuitRoot() { // any
    return !circState.isSubstate();
  }

  public boolean isPortConnected(int index) { // (a)
    Circuit circ = context.getCircuit();
    Location loc = comp.getEnd(index).getLocation();
    return circ.isConnected(loc, comp);
  }

  public boolean isPrintView() { // any
    return context.isPrintView();
  }
  
  public Integer getDataAsInteger() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataAsInteger(comp);
  }

  public Value getDataAsValue() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataAsValue(comp);
  }
  
  public Double getDataAsDouble() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataAsDouble(comp);
  }

  public int getDataOrDefault(int defaultData) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataOrDefault(comp, defaultData);
  }

  public Value getDataOrDefault(Value defaultData) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataOrDefault(comp, defaultData);
  }

  public double getDataOrDefault(double defaultData) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataOrDefault(comp, defaultData);
  }

  public ComponentData getDataAsCustom() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataAsCustom(comp);
  }

  public CircuitState getDataForSubcircuit() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataForSubcircuit(comp);
  }

  @Deprecated(since = "5.0.5HC", forRemoval = false)
  public Object getData() { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return circState.getDataAsAny(comp);
  }
  
  public void setData(int data) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("setData on InstancePainter");
    circState.setData(comp, data);
  }

  public void setData(Value data) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("setData on InstancePainter");
    circState.setData(comp, data);
  }

  public void setData(double data) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("setData on InstancePainter");
    circState.setData(comp, data);
  }

  public void setData(ComponentData data) { // (a)
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("setData on InstancePainter");
    circState.setData(comp, data);
  }

  void setFactory(ComponentFactory factory, AttributeSet attrs) { // changes to (b)
    this.comp = null;
    this.factory = factory;
    this.attrs = attrs;
  }

  public void setComponent(Component comp) { // changes to (a)
    this.comp = comp;
    this.factory = null;
    this.attrs = null;
  }

  public void setPort(int portIndex, Value value, int delay) { // none
    throw new UnsupportedOperationException("setValue on InstancePainter");
  }

  public boolean shouldDrawColor() { // any
    return context.shouldDrawColor();
  }
}
