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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.proj.Project;

public class InstanceStateImpl implements InstanceState {
  private CircuitState circuitState;
  private Component component;

  public InstanceStateImpl(CircuitState circuitState, Component component) {
    this.circuitState = circuitState;
    this.component = component;

    if (component instanceof InstanceComponent) {
      ((InstanceComponent) component).setInstanceStateImpl(this);
    }
  }

  public void fireInvalidated() {
    if (component instanceof InstanceComponent) {
      ((InstanceComponent) component).fireInvalidated();
    }
  }

  public AttributeSet getAttributeSet() {
    return component.getAttributeSet();
  }

  public <E> E getAttributeValue(Attribute<E> attr) {
    return component.getAttributeSet().getValue(attr);
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  public InstanceFactory getFactory() {
    if (component instanceof InstanceComponent)
      return (InstanceFactory)((InstanceComponent)component).getFactory();
    return null;
  }

  public Instance getInstance() {
    if (component instanceof InstanceComponent)
      return ((InstanceComponent) component).getInstance();
    return null;
  }

  public Component getComponent() {
    return component;
  }

  public Value getPortValue(int portIndex) {
    EndData data = component.getEnd(portIndex);
    return circuitState.getValue(data.getLocation());
  }

  public Project getProject() {
    return circuitState.getProject();
  }

  public int getTickCount() {
    return circuitState.getPropagator().getTickCount();
  }

  public boolean isCircuitRoot() {
    return !circuitState.isSubstate();
  }

  public boolean isPortConnected(int index) {
    Circuit circ = circuitState.getCircuit();
    Location loc = component.getEnd(index).getLocation();
    return circ.isConnected(loc, component);
  }

  public void repurpose(CircuitState circuitState, Component component) {
    this.circuitState = circuitState;
    this.component = component;
    // todo: seems sketchy, need to undo the setInstanceStateImpl() from before?
  }
  
  public Integer getDataAsInteger() {
    if (circuitState == null)
      return null;
    return circuitState.getDataAsInteger(component);
  }

  public Value getDataAsValue() {
    if (circuitState == null)
      return null;
    return circuitState.getDataAsValue(component);
  }

  public Double getDataAsDouble() {
    if (circuitState == null)
      return null;
    return circuitState.getDataAsDouble(component);
  }

  public int getDataOrDefault(int defaultData) {
    if (circuitState == null)
      return defaultData;
    return circuitState.getDataOrDefault(component, defaultData);
  }

  public Value getDataOrDefault(Value defaultData) {
    if (circuitState == null)
      return defaultData;
    return circuitState.getDataOrDefault(component, defaultData);
  }

  public double getDataOrDefault(double defaultData) {
    if (circuitState == null)
      return defaultData;
    return circuitState.getDataOrDefault(component, defaultData);
  }
  
  public ComponentData getDataFor() {
    if (circuitState == null)
      return null;
    return circuitState.getDataFor(component);
  }

  public CircuitState getDataForSubcircuit() {
    if (circuitState == null)
      return null;
    return circuitState.getDataForSubcircuit(component);
  }

  @Deprecated(since = "5.0.5HC", forRemoval = false)
  public Object getData() {
    if (circuitState == null)
      return null;
    return circuitState.getDataAsAny(component);
  }

  public void setData(int data) { circuitState.setData(component, data); }
  public void setData(Value data) { circuitState.setData(component, data); }
  public void setData(double data) { circuitState.setData(component, data); }
  public void setData(ComponentData data) { circuitState.setData(component, data); }

  public void setPort(int portIndex, Value value, int delay) {
    EndData end = component.getEnd(portIndex);
    circuitState.setValue(end.getLocation(), value, component, delay);
  }
}
