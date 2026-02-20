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

import java.awt.Color;
import java.awt.Graphics;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.StringGetter;

/**
 * Represents a category of components that appear in a circuit. This class and
 * <code>Component</code> share the same sort of relationship as the relation
 * between <em>classes</em> and <em>instances</em> in Java. Normally, there is
 * only one ComponentFactory instance created for any particular category. There
 * are many subclasses of ComponentFactory.
 *
 * See Component.java for more details.
 *
 *                  interface ComponentFactory
 *                              |
 *                              |
 *                   AbstractComponentFactory
 *                              |
 *        ______________________|________________________
 *       |              |               |                |
 *  WireFactory  SplitterFactory  Video$Factory   InstanceFactory
 *                                                       |
 *              _________________________________________|___________
 *             |              |             |         |       |      |
 *    SubcircuitFactory  Multiplexer  AbstractGate  Adder  Shifter  etc.
 *                                          |
 *                      ____________________|_____________
 *                      |        |        |        |      |
 *                   AndGate  OrGate  NandGate  XorGate  etc.
 */
public interface ComponentFactory extends AttributeDefaultProvider {
  public static final Object SHOULD_SNAP = new Object();
  public static final Object TOOL_TIP = new Object();
  public static final Object FACING_ATTRIBUTE_KEY = new Object();

  public boolean ActiveOnHigh(AttributeSet attrs);

  public AttributeSet createAttributeSet();

  public Component createComponent(Location loc, AttributeSet attrs);

  public void drawGhost(ComponentDrawContext context, Color color, int x,
      int y, AttributeSet attrs);

  default public void drawLabel(ComponentDrawContext context) { }

  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver);

  public StringGetter getDisplayGetter();

  public String getDisplayName();

  /**
   * Retrieves special-purpose features for this factory. This technique
   * allows for future Logisim versions to add new features for components
   * without requiring changes to existing components. It also removes the
   * necessity for the Component API to directly declare methods for each
   * individual feature. In most cases, the <code>key</code> is a
   * <code>Class</code> object corresponding to an interface, and the method
   * should return an implementation of that interface if it supports the
   * feature.
   * <p/>
   * As of this writing, possible values for <code>key</code> include:
   * <code>TOOL_TIP</code> (return a <code>String</code>) and
   * <code>SHOULD_SNAP</code> (return a <code>Boolean</code>).
   *
   * @param key
   *            an object representing a feature.
   * @return an object representing information about how the component
   *         supports the feature, or <code>null</code> if it does not support
   *         the feature.
   */
  public Object getFeature(Object key, AttributeSet attrs);

  public default boolean HDLIgnore() { return false; }

  public default HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) { return null; }
  public default String getHDLNamePrefix(Component comp) { return null; }

  public String getName();

  // See Component.java for details on the difference between nominal and
  // visible bounding boxes. The same applies here for offset bounds.
  // - getOffsetBounds() returns the nominal offset bounds, not including any
  //   label, centered at location (0, 0). This is not graphics-sensitive, and
  //   for Text and Callout is is only approximate.
  public Bounds getOffsetBounds(AttributeSet attrs); // nominal

  public default Bounds getVisibleOffsetBounds(AttributeSet attrs) { // visible
    return getOffsetBounds(attrs); // Note: this is incorrect for Text
  }

  public boolean HasThreeStateDrivers(AttributeSet attrs);

  // default public List<Attribute<?>> getNonVolatileSimulationAttributes(Component comp) { return null; }
  default public AttributeSet getNonVolatileSimulationState(Component comp, CircuitState state) { return null; }
  default public void setNonVolatileSimulationState(Component comp, CircuitState state, AttributeSet attrs) { }

  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver);

  public void paintIcon(ComponentDrawContext context, int x, int y,
      AttributeSet attrs);

}
