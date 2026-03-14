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

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.circuit.appear.CircuitAppearanceEvent;
import com.cburch.logisim.circuit.appear.CircuitAppearanceListener;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.Debug;

// The subcircuit factory for a circuit uses SubcircuitAttributes, which holds
// parameters related to a specific instance, like FACING and LABEL.
// By contrast, a circuit has one CircuitAttributes to hold parameters related
// to the circuit itself, like CIRCUIT_NAME and CIRCUIT_APPEARANCE.
public class SubcircuitAttributes extends AbstractAttributeSet {

  // For each Instance created by SubcircuitFactory, the corresponding
  // SubcircuitAttributes listens for changes both to the underlying source
  // circuit's static attributes, and to the underlying source circuit's
  // appearance. This is needed because when the source's appearance changes,
  // REVISION_LABEL changes, etc., each Instance needs to adjust it's port
  // locations, bounds, etc., to match the (potentially) new apearance. We
  // call into SubcircuitFactory to do the adjustment.
  //
  // private class SourceListener
  //   implements AttributeListener, CircuitAppearanceListener {

  //   private Circuit source;
  //   private SourceListener(Circuit s) { source = s; }

  //   public void attributeListChanged(AttributeEvent e) { }

  //   public void attributeValueChanged(AttributeEvent e) {
  //     @SuppressWarnings("unchecked")
  //     Attribute<Object> a = (Attribute<Object>) e.getAttribute();
  //     for (Attribute<?> s : STATIC_ATTRS)
  //       if (s == a)
  //         Debug.printf(0, "old:CircuitAttributes.SourceListener mis-relaying static attr %s\n", a);
  //     fireAttributeValueChanged(a, e.getValue());
  //   }

  //   // When the underlying source circuit apparance changes, we ask the
  //   // subcircuit factory to recompute the ports and bounds for this instance,
  //   // and we invalidate the instance so it is redrawn.
  //   public void circuitAppearanceChanged(CircuitAppearanceEvent e) {
  //     Debug.printf(0, "old:CircuitAttributes.SourceListener.circuitAppearanceChanged()\n");
  //     SubcircuitFactory factory;
  //     factory = (SubcircuitFactory) subcircInstance.getFactory();
  //     if (e.isConcerning(CircuitAppearanceEvent.PORTS)) {
  //       Debug.printf(0, "old:CircuitAttributes.SourceListener ... --> computePorts(instance)\n");
  //       factory.computePorts(subcircInstance);
  //     }
  //     if (e.isConcerning(CircuitAppearanceEvent.BOUNDS)) {
  //       Debug.printf(0, "old:CircuitAttributes.SourceListener ... --> recomputeBounds()\n");
  //       subcircInstance.recomputeBounds();
  //     }
  //     subcircInstance.fireInvalidated();
  //     // old:FIXME: Also reset the custom flag... why here?
  //     if (source != null & !source.getAppearance().isDefaultAppearance()) {
  //       Debug.printf(0, "why here?\n");
  //       source.getStaticAttributes().setAttr(CIRCUIT_APPEARANCE, APPEAR_CUSTOM);
  //     }
  //   }
  // }

  private static final List<Attribute<?>> INSTANCE_ATTRS = Arrays.asList(
      new Attribute<?>[] {
        StdAttr.FACING,
        StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT,
      });

  // private Circuit source;
  // private Instance subcircInstance;
  private Direction facing;
  private String label;
  private Object labelLocation;
  private Font labelFont;
  // private SourceListener listener;
  private Instance[] pinInstances;

  public SubcircuitAttributes(Circuit source) {
    // this.source = source;
    // subcircInstance = null;
    facing = source.getAppearance().getFacing();
    label = "";
    labelLocation = Direction.NORTH;
    labelFont = StdAttr.DEFAULT_LABEL_FONT;
    pinInstances = new Instance[0];
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    SubcircuitAttributes other = (SubcircuitAttributes) dest;
    // other.subcircInstance = null;
    // other.listener = null;
    Debug.trace("huh? didn't copy attributes?");
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return INSTANCE_ATTRS;
  }

  public Direction getFacing() {
    return facing;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    if (attr == StdAttr.FACING)
      return (V) facing;
    else if (attr == StdAttr.LABEL)
      return (V) label;
    else if (attr == StdAttr.LABEL_FONT)
      return (V) labelFont;
    else if (attr == StdAttr.LABEL_LOC)
      return (V) labelLocation;
    else
      return null;
    // else {
    //   Debug.trace("old:CircuitAttributes.getValue() with static attr " + attr);
    //   return source.getStaticAttributes().getValue(attr);
    // }
  }

  // @Override
  // public boolean isToSave(Attribute<?> attr) {
  //   Attribute<?>[] statics = STATIC_ATTRS;
  //   for (int i = 0; i < statics.length; i++) {
  //     if (statics[i] == attr) {
  //       Debug.trace("old:CircuitAttributes.isToSave() with static attr " + attr);
  //       return false;
  //     }
  //   }
  //   return true;
  // }

  void setPinInstances(Instance[] value) {
    pinInstances = value;
  }

  public Instance[] getPinInstances() {
    return pinInstances;
  }

  // void setSubcircuit(Instance value) {
  //   subcircInstance = value;
  //   if (subcircInstance != null && listener == null) {
  //     listener = new SourceListener(source);
  //     source.getStaticAttributes().addAttributeWeakListener(null, listener);
  //     source.getAppearance().addCircuitAppearanceWeakListener(null, listener);
  //   }
  // }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.FACING) {
      facing = (Direction) value;
      // if (subcircInstance != null) {
      //   Debug.printf(0, "old:CircuitAttributes.updateAttr(FACING) --> recomputeBounds()\n");
      //   subcircInstance.recomputeBounds();
      // }
    } else if (attr == StdAttr.LABEL) {
      label = (String) value;
    } else if (attr == StdAttr.LABEL_FONT) {
      labelFont = (Font) value;
    } else if (attr == StdAttr.LABEL_LOC) {
      labelLocation = value;
    }
  }
}
