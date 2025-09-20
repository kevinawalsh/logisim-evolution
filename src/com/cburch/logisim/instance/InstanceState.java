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

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;

// Only known implementing classes are:
//   InstanceStateImpl
//   InstancePainter
public interface InstanceState {
  public void fireInvalidated();

  public AttributeSet getAttributeSet();

  public <E> E getAttributeValue(Attribute<E> attr);

  public Integer getDataAsInteger();
  public Value getDataAsValue();
  public Double getDataAsDouble();
  public ComponentData getDataFor();
  public CircuitState getDataForSubcircuit();
  @Deprecated(since = "5.0.5HC", forRemoval = false)
  public Object getData();

  public int  getDataOrDefault(int defaultValue);
  public Value  getDataOrDefault(Value defaultValue);
  public double  getDataOrDefault(double defaultValue);

  public void setData(int data);
  public void setData(Value data);
  public void setData(double data);
  public void setData(ComponentData value);

  public InstanceFactory getFactory();

  public Instance getInstance();

  public Value getPortValue(int portIndex);
  
  public CircuitState getCircuitState();

  public Project getProject();

  public int getTickCount();

  public boolean isCircuitRoot();

  public boolean isPortConnected(int portIndex);

  public CircuitState createCircuitSubstateFor(Circuit circ);

  public void setPort(int portIndex, Value value, int delay);
}
