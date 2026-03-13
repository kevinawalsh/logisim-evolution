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

public class CircuitAttributes extends AbstractAttributeSet {

  // For each subcircuit instance, one of these listens for changes both to the
  // underlying circuit's static attributes and to the underlying circuit's
  // appearance.
  private class MyListener
    implements AttributeListener, CircuitAppearanceListener {

    private Circuit source;
    private MyListener(Circuit s) { source = s; }

    public void attributeListChanged(AttributeEvent e) { }

    public void attributeValueChanged(AttributeEvent e) {
      @SuppressWarnings("unchecked")
      Attribute<Object> a = (Attribute<Object>) e.getAttribute();
      for (Attribute<?> s : STATIC_ATTRS)
        if (s == a)
          Debug.printf(0, "CircuitAttributes.MyListener mis-relaying static attr %s\n", a);
      fireAttributeValueChanged(a, e.getValue());
    }

    // When the underlying source circuit apparance changes, we ask the
    // subcircuit factory to recompute the ports and bounds for this instance,
    // and we invalidate the instance so it is redrawn.
    public void circuitAppearanceChanged(CircuitAppearanceEvent e) {
      Debug.printf(0, "CircuitAttributes.MyListener.circuitAppearanceChanged()\n");
      SubcircuitFactory factory;
      factory = (SubcircuitFactory) subcircInstance.getFactory();
      if (e.isConcerning(CircuitAppearanceEvent.PORTS)) {
        Debug.printf(0, "CircuitAttributes.MyListener ... --> computePorts(instance)\n");
        factory.computePorts(subcircInstance);
      }
      if (e.isConcerning(CircuitAppearanceEvent.BOUNDS)) {
        Debug.printf(0, "CircuitAttributes.MyListener ... --> recomputeBounds()\n");
        subcircInstance.recomputeBounds();
      }
      subcircInstance.fireInvalidated();
      // FIXME: Also reset the custom flag... why here?
      if (source != null & !source.getAppearance().isDefaultAppearance()) {
        Debug.printf(0, "why here?\n");
        source.getStaticAttributes().setAttr(CIRCUIT_APPEARANCE, APPEAR_CUSTOM);
      }
    }
  }

  // For each circuit, one of these listens for certain changes to static
  // attributes (name changes, and changes to a default appearance type).
  private static class StaticListener implements AttributeListener {
    private Circuit source;

    private StaticListener(Circuit s) { source = s; }

    public void attributeListChanged(AttributeEvent e) { }

    public void attributeValueChanged(AttributeEvent e) {
      if (e.getAttribute() == CIRCUIT_NAME) {
        // When the name changes, we fire CircuitListener.circuitChanged().
        source.fireEvent(CircuitEvent.ACTION_SET_NAME, e.getValue());
      } else if (e.getAttribute() == CIRCUIT_APPEARANCE) {
        // When the appearance changes to a default (computed, non-custom)
        // style, we recalculate the shape.
        if (e.getValue() == APPEAR_CLASSIC || e.getValue() == APPEAR_FPGA) {
          source.getAppearance().setDefaultAppearance(true);
          source.RecalcDefaultShape();
        }
      }
    }
  }

  static AttributeSet createBaseAttrs(Circuit source, Library lib, String name) {
    AttributeSet ret = AttributeSets.fixedSet(STATIC_ATTRS, STATIC_DEFAULTS);
    ret.setToSave(CIRCUIT_NAME, false); // name already appears as an attribute of circuit's outer xml node
    ret.setAttr(CIRCUIT_NAME, name);
    ret.addAttributeWeakListener(source, new StaticListener(source));
    return ret;
  }

  public static final Attribute<String> CIRCUIT_NAME = Attributes.forString(
      "circuit", S.getter("circuitName"));

  public static final Attribute<String> CIRCUIT_REVISION = Attributes
      .forString("clabel", S.getter("circuitLabelAttr"));

  public static final Attribute<Direction> CIRCUIT_REVISION_FACING_ATTR = Attributes
      .forDirection("clabelup", S.getter("circuitLabelDirAttr"));

  public static final Attribute<Font> CIRCUIT_REVISION_FONT_ATTR = Attributes
      .forFont("clabelfont", S.getter("circuitLabelFontAttr"));
  public static final Attribute<Boolean> CIRCUIT_IS_VHDL_BOX = Attributes
      .forBoolean("circuitvhdl", S.getter("circuitIsVhdl"));
  public static final Attribute<String> CIRCUIT_VHDL_PATH = Attributes
      .forString("circuitvhdlpath", S.getter("circuitVhdlPath"));

  public static final AttributeOption APPEAR_CLASSIC = StdAttr.APPEAR_CLASSIC;
  public static final AttributeOption APPEAR_FPGA = StdAttr.APPEAR_FPGA;
  public static final AttributeOption APPEAR_CUSTOM = new AttributeOption(
      "custom", S.getter("circuitCustomAppearance"));
  public static final Attribute<AttributeOption> CIRCUIT_APPEARANCE = Attributes
      .forOption("appearance", S.getter("circuitAppearanceAttr"),
          new AttributeOption[] { APPEAR_CLASSIC, APPEAR_FPGA, APPEAR_CUSTOM });

  static final Attribute<?>[] STATIC_ATTRS = {
    CIRCUIT_NAME,
    CIRCUIT_REVISION, CIRCUIT_REVISION_FACING_ATTR, CIRCUIT_REVISION_FONT_ATTR,
    CIRCUIT_IS_VHDL_BOX, CIRCUIT_VHDL_PATH,
    CIRCUIT_APPEARANCE };

  static final Object[] STATIC_DEFAULTS = {
    "",
    "", Direction.EAST, StdAttr.DEFAULT_LABEL_FONT,
    false, "",
    APPEAR_FPGA };

  private static final List<Attribute<?>> INSTANCE_ATTRS = Arrays.asList(
      new Attribute<?>[] {
        StdAttr.FACING,
        StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT,
        /* CIRCUIT_NAME,
        CIRCUIT_REVISION, CIRCUIT_REVISION_FACING_ATTR, CIRCUIT_REVISION_FONT_ATTR,
        CIRCUIT_IS_VHDL_BOX, CIRCUIT_VHDL_PATH,
        CIRCUIT_APPEARANCE */ });

  private Circuit source;
  private Instance subcircInstance;
  private Direction facing;
  private String label;
  private Object labelLocation;
  private Font labelFont;
  private MyListener listener;
  private Instance[] pinInstances;

  public CircuitAttributes(Circuit source) {
    this.source = source;
    subcircInstance = null;
    facing = source.getAppearance().getFacing();
    label = "";
    labelLocation = Direction.NORTH;
    labelFont = StdAttr.DEFAULT_LABEL_FONT;
    pinInstances = new Instance[0];
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    CircuitAttributes other = (CircuitAttributes) dest;
    other.subcircInstance = null;
    other.listener = null;
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return INSTANCE_ATTRS;
  }

  public Direction getFacing() {
    return facing;
  }

  public Instance[] getPinInstances() {
    return pinInstances;
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
    else {
      Debug.trace("CircuitAttributes.getValue() with static attr " + attr);
      return source.getStaticAttributes().getValue(attr);
    }
  }

  @Override
  public boolean isToSave(Attribute<?> attr) {
    Attribute<?>[] statics = STATIC_ATTRS;
    for (int i = 0; i < statics.length; i++) {
      if (statics[i] == attr) {
        Debug.trace("CircuitAttributes.isToSave() with static attr " + attr);
        return false;
      }
    }
    return true;
  }

  void setPinInstances(Instance[] value) {
    pinInstances = value;
  }

  void setSubcircuit(Instance value) {
    subcircInstance = value;
    if (subcircInstance != null && listener == null) {
      listener = new MyListener(source);
      source.getStaticAttributes().addAttributeWeakListener(null, listener);
      source.getAppearance().addCircuitAppearanceWeakListener(null, listener);
    }
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.FACING) {
      facing = (Direction) value;
      if (subcircInstance != null) {
        Debug.printf(0, "CircuitAttributes.updateAttr(FACING) --> recomputeBounds()\n");
        subcircInstance.recomputeBounds();
      }
    } else if (attr == StdAttr.LABEL) {
      label = (String) value;
    } else if (attr == StdAttr.LABEL_FONT) {
      labelFont = (Font) value;
    } else if (attr == StdAttr.LABEL_LOC) {
      labelLocation = value;
    } else {
      Debug.trace("CircuitAttributes.updateAttr() with static attr " + attr);
      source.getStaticAttributes().setAttr(attr, value);
      if (attr == CIRCUIT_NAME)
        source.fireEvent(CircuitEvent.ACTION_SET_NAME, value);
    }
  }
}
