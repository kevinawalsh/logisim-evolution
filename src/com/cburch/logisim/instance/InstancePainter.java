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

import java.awt.Graphics;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
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
  private ComponentDrawContext context;
  private Component comp;
  private ComponentFactory factory;
  private AttributeSet attrs;

  public InstancePainter(ComponentDrawContext context, Component comp) {
    this.context = context;
    this.comp = comp;
  }

  //
  // helper methods for drawing common elements in components
  //
  public void drawBounds() {
    if (comp != null)
      context.drawBounds(comp);
  }

  public void drawClock(int i, Direction dir) {
    if (comp != null)
      context.drawClock(comp, i, dir);
  }

  public void drawClockSymbol(int xpos, int ypos) {
    if (comp != null)
      context.drawClockSymbol(comp, xpos, ypos);
  }

  public void drawDongle(int x, int y) {
    context.drawDongle(x, y);
  }

  public void drawHandle(int x, int y) {
    context.drawHandle(x, y);
  }

  public void drawHandle(Location loc) {
    context.drawHandle(loc);
  }

  public void drawHandles() {
    if (comp != null)
      context.drawHandles(comp);
  }

  // Note: drawLabel() only works when a Component is present,
  // since it relies on a Component's textField. In particular, it doesn't work
  // within drawGhost() or any other cases where we only have a Factory and
  // AttributeSet, rather than a Component.
  public void drawLabel() {
    if (comp != null)
      comp.drawLabel(context);
  }

  public void drawPort(int i) {
    if (comp != null)
      context.drawPin(comp, i);
  }

  public void drawPort(int i, String label, Direction dir) {
    if (comp != null)
      context.drawPin(comp, i, label, dir);
  }

  public void drawPorts() {
    if (comp != null)
      context.drawPins(comp);
  }

  public void drawRectangle(Bounds bds, String label) {
    context.drawRectangle(bds.getX(), bds.getY(), bds.getWidth(),
        bds.getHeight(), label);
  }

  public void drawRectangle(int x, int y, int width, int height, String label) {
    context.drawRectangle(x, y, width, height, label);
  }

  @Override
  public void fireInvalidated() {
    if (comp != null)
      comp.fireInvalidated();
  }

  public AttributeSet getAttributeSet() {
    return comp == null ? attrs : comp.getAttributeSet();
  }

  public <E> E getAttributeValue(Attribute<E> attr) {
    return getAttributeSet().getValue(attr);
  }
  
  public Bounds getNominalBounds() {
    return comp == null
        ? factory.getOffsetBounds(attrs)
        : comp.getNominalBounds();
  }

  public Circuit getCircuit() {
    return context.getCircuit();
  }

  public CircuitState getCircuitState() {
    return context.getCircuitState();
  }

  public InstanceData getData() {
    CircuitState circState = context.getCircuitState();
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("InstancePainter.getData without state");
    return (InstanceData) circState.getData(comp);
  }

  public java.awt.Component getDestination() {
    return context.getDestination();
  }

  public InstanceFactory getFactory() {
    if (comp instanceof InstanceComponent)
      return (InstanceFactory)comp.getFactory();
    else if (factory instanceof InstanceFactory)
      return (InstanceFactory)factory;
    else
      return null;
  }

  public Object getGateShape() {
    return context.getGateShape();
  }

  public Graphics getGraphics() {
    return context.getGraphics();
  }

  //
  // methods related to the context of the canvas
  //
  public WireSet getHighlightedWires() {
    return context.getHighlightedWires();
  }

  //
  // methods related to the instance
  //
  public Instance getInstance() {
    return comp instanceof InstanceComponent
        ? ((InstanceComponent)comp).getInstance() :  null;
  }

  public Component getComponent() {
    return comp;
  }

  public Location getLocation() {
    return comp == null ? Location.create(0, 0) : comp.getLocation();
  }

  public Bounds getNominalOffsetBounds() {
    if (comp == null) {
      return factory.getOffsetBounds(attrs);
    } else {
      Location loc = comp.getLocation();
      return comp.getNominalBounds().translate(-loc.getX(), -loc.getY());
    }
  }

  // public Bounds getVisibleOffsetBounds() {
  //   if (comp == null) {
  //     return factory.getOffsetBounds(attrs, getGraphics());
  //   } else {
  //     Location loc = comp.getLocation();
  //     return comp.getVisibleBounds(getGraphics()).translate(-loc.getX(), -loc.getY());
  //   }
  // }

  public Value getPortValue(int portIndex) {
    CircuitState s = context.getCircuitState();
    if (comp != null && s != null) {
      return s.getValue(comp.getEnd(portIndex).getLocation());
    } else {
      return Value.UNKNOWN;
    }
  }

  //
  // methods related to the circuit state
  //
  public Project getProject() {
    return context.getCircuitState().getProject();
  }

  public boolean getShowState() {
    return context.getShowState();
  }

  public int getTickCount() {
    return context.getCircuitState().getPropagator().getTickCount();
  }

  public boolean isCircuitRoot() {
    return !context.getCircuitState().isSubstate();
  }

  public boolean isPortConnected(int index) {
    Circuit circ = context.getCircuit();
    Location loc = comp.getEnd(index).getLocation();
    return circ.isConnected(loc, comp);
  }

  public boolean isPrintView() {
    return context.isPrintView();
  }

  public void setData(InstanceData value) {
    CircuitState circState = context.getCircuitState();
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("setData on InstancePainter");
    circState.setData(comp, value);
  }

  @Override
  public CircuitState createCircuitSubstateFor(Circuit circ) {
    CircuitState circState = context.getCircuitState();
    if (circState == null || comp == null)
      throw new UnsupportedOperationException("createCircuitSubstateFor on InstancePainter");
    return circState.createCircuitSubstateFor(comp, circ);
  }

  void setFactory(ComponentFactory factory, AttributeSet attrs) {
    this.comp = null;
    this.factory = factory;
    this.attrs = attrs;
  }

  public void setComponent(Component comp) {
    this.comp = comp;
    this.factory = null;
    this.attrs = null;
  }

  public void setPort(int portIndex, Value value, int delay) {
    throw new UnsupportedOperationException("setValue on InstancePainter");
  }

  public boolean shouldDrawColor() {
    return context.shouldDrawColor();
  }
}
