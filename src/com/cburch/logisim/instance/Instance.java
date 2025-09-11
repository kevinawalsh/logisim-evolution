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

import java.awt.Font;
import java.util.List;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.GraphicsUtil;

// Tentative Design Notes (1 of 3): The relationship between Instances,
// InstanceState, InstanceFactory, etc. can be very confusing. Ignoring logisim
// for a moment, consider a blueprint that shows various wires, multiplexors,
// decoders, registers, flip-flops, etc. A naive way of implementing a circuit
// simulator that can model and edit such a blueprint would be:
//   - [level 1] A java class for each type of component, e.g. a java Mux class,
//     a java Decoder class, a java Register class. Maybe these could all extend
//     some generic java Component class, so they could inherit some behaviors.
//     Java static methods and variables of the Mux class would represent
//     operations and data that are common to all instantiated multiplexors,
//     i.e. to the entire concept of a multiplexor.
//   - [level 2] Each multiplexor appearing in the blueprint would be
//     represented by a java instance of java class Mux. Similarly each decoder
//     appearing in the blueprint would be an instance of the java Decoder
//     class. Java dynamic/member methods and variables of the Mux class would
//     represent operations and data on individual multiplexors in the circuit.
//
// This is NOT AT ALL how Logisim does things. Instead, Logisim implemnts a meta
// type system, one level up, essentially implementing an entire class-object
// system within itself. In this scheme, there are three levels:
//   - [level 2] a java object of type Instance represents an instantiated
//     component (e.g. like a mux) that has been placed at a specifc spot in a
//     circuit. Each Instance object is created by an InstanceFactory. For
//     example, some Instance might represent a specific 4-to-1 mux in a
//     particular circuit.
//   - [level 1] a java object of type InstanceFactory represents one type
//     of component. For example, there will be one object (of type
//     InstanceFactory) representing the multiplexor kind, another java object
//     (of type InstanceFactory) will represent the decoder kind, and so on.
//   - [level 0] various java classes that extend InstanceFactory. Each java
//     instances of one of these class represents one kind of component.
//
// The reason for this is that it allows for meta-programming. There are lots of
// "kinds" of components that are nearly identical, or entire categories of
// "kinds" of components that vary in some programmatic way. So within Java, we
// can dynamically generate an entire pool of InstanceFactory objects, each
// representing a "kind" of components. Simple example:
//     [level 0] java class Multiplexor
//     [level 1] java object m, of type Multiplexor
//     [level 2] java object i, of type Instance, which was created by m
// Another example:
//     [level 0] java class Pin
//     [level 1] objects inPin and outPin, each of type Pin
//     [level 2] java object i1, i2, i3, o1, o2, o3, each of type Instance,
//     created by either inPin or outPin
//
// NOTE: the above may not be entirely accurate, especially for Pin. And we also
// have things like class Component, InstanceComponent, and so on. Confusing!
//
// ALSO NOTE: While a [level 2] Instance has attributes, it does NOT have any
// dynamic state. So an Instance representing an LED has a Color attribute, but
// no Value representing the current on/off state. Instead, all circuit state is
// the tied to circuit simulations, encapsulated in CircuitState, InstanceState,
// InstancePainter, and related objects, along with additional instance-specific
// "data" tucked away inside these state objects. This is a one-to-many
// relationship: An LED, appearing in a specific place in some circuit, might be
// involoved in multiple simultaneous simulations, and needs on/off state
// information about each of those simulations. This happens whenever circuits
// are embedded within each other as subcircuits, and the user can start and
// switch between multiple simulations from the UI explicitly.
//
public final class Instance implements Location.At {

  // Not every Component that exists is an InstanceComponent. But for those
  // Components that are, this function will get the matching Instance for it.
  // For Components that are not InstanceComponents, this just returns null.
  // If you already have InstanceComponent, just call geetInstance(). If you
  // don't, but you are sure of the types, this saves a typecast, but that's it.
  public static Instance getInstanceFor(Component comp) {
    if (comp instanceof InstanceComponent) {
      return ((InstanceComponent) comp).getInstance();
    } else {
      return null;
    }
  }

  // returns the matching InstanceComponent twin of an Instancee
  public static InstanceComponent getComponentFor(Instance instance) {
    return instance.comp;
  }


  // Makes the twin for an InstanceComponent
  static Instance makeFor(InstanceComponent comp) { return new Instance(comp); }
  private Instance(InstanceComponent comp) { this.comp = comp; }

  private InstanceComponent comp;

  public void addAttributeListener() {
    comp.addAttributeListener(/*this*/);
  }

  public void fireInvalidated() {
    comp.fireInvalidated();
  }

  public AttributeSet getAttributeSet() {
    return comp.getAttributeSet();
  }

  public <E> E getAttributeValue(Attribute<E> attr) {
    return comp.getAttributeSet().getValue(attr);
  }

  public Bounds getNominalBounds() {
    return comp.getNominalBounds();
  }

  public InstanceComponent getComponent() {
    return comp;
  }

  public InstanceData getData(CircuitState state) {
    return (InstanceData) state.getData(comp);
  }

  public InstanceFactory getFactory() {
    return (InstanceFactory) comp.getFactory();
  }

  public Location getLocation() {
    return comp.getLocation();
  }

  public Location getPortLocation(int index) {
    return comp.getEnd(index).getLocation();
  }

  public List<Port> getPorts() {
    return comp.getPorts();
  }

  public void recomputeBounds() {
    comp.recomputeBounds();
  }

  public void setAttributeReadOnly(Attribute<?> attr, boolean value) {
    comp.getAttributeSet().setReadOnly(attr, value);
  }

  public void setData(CircuitState state, InstanceData data) {
    state.setData(comp, data);
  }

  public void setPorts(Port[] ports) {
    comp.setPorts(ports);
  }

  public void setTextField(Attribute<String> labelAttr,
      Attribute<Font> fontAttr, int x, int y, int halign, int valign) {
    setTextField(labelAttr, fontAttr, x, y, halign, valign, false);
  }
  public void setTextField(Attribute<String> labelAttr,
      Attribute<Font> fontAttr, int x, int y, int halign, int valign, boolean multiline) {
    comp.setTextField(labelAttr, fontAttr, x, y, halign, valign, multiline);
  }

  public static final int AVOID_TOP = 1;
  public static final int AVOID_RIGHT = 2;
  public static final int AVOID_BOTTOM = 4;
  public static final int AVOID_LEFT = 8;
  public static final int AVOID_SIDES = AVOID_LEFT | AVOID_RIGHT;
  public static final int AVOID_CENTER = 16;

  public void computeLabelTextField(int avoid) {
    if (avoid != 0) {
      Direction facing = getAttributeValue(StdAttr.FACING);
      if (facing == Direction.NORTH)
        avoid = (avoid&0x10)|((avoid << 1)&0xf)|((avoid&0xf) >> 3);
      else if (facing == Direction.EAST)
        avoid = (avoid&0x10)|((avoid << 2)&0xf)|((avoid&0xf) >> 2);
      else if (facing == Direction.SOUTH)
        avoid = (avoid&0x10)|((avoid << 3)&0xf)|((avoid&0xf) >> 1);
    }
    Object labelLoc = getAttributeValue(StdAttr.LABEL_LOC);

    Bounds bds = getNominalBounds();
    int x = bds.getX() + bds.getWidth() / 2;
    int y = bds.getY() + bds.getHeight() / 2;
    int halign = GraphicsUtil.H_CENTER;
    int valign = GraphicsUtil.V_CENTER;
    if (labelLoc == StdAttr.LABEL_CENTER) {
      int offset = 0;
      if ((avoid & AVOID_CENTER) != 0)
        offset = 3;
      x = bds.getX() + (bds.getWidth() - offset) / 2;
      y = bds.getY() + (bds.getHeight() - offset) / 2;
    } else if (labelLoc == Direction.NORTH) {
      y = bds.getY() - 2;
      valign = GraphicsUtil.V_BOTTOM;
      if ((avoid & AVOID_TOP) != 0) {
        x += 2;
        halign = GraphicsUtil.H_LEFT;
      }
    } else if (labelLoc == Direction.SOUTH) {
      y = bds.getY() + bds.getHeight() + 2;
      valign = GraphicsUtil.V_TOP;
      if ((avoid & AVOID_BOTTOM) != 0) {
        x += 2;
        halign = GraphicsUtil.H_LEFT;
      }
    } else if (labelLoc == Direction.EAST) {
      x = bds.getX() + bds.getWidth() + 2;
      halign = GraphicsUtil.H_LEFT;
      if ((avoid & AVOID_RIGHT) != 0) {
        y -= 2;
        valign = GraphicsUtil.V_BOTTOM;
      }
    } else if (labelLoc == Direction.WEST) {
      x = bds.getX() - 2;
      halign = GraphicsUtil.H_RIGHT;
      if ((avoid & AVOID_LEFT) != 0) {
        y -= 2;
        valign = GraphicsUtil.V_BOTTOM;
      }
    }
    setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, x, y, halign, valign);
  }
}
