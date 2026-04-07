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

package com.cburch.logisim.std.hdl;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.hdl.HdlModel;
import com.cburch.hdl.HdlModelListener;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutator;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;

public class VhdlEntityAttributes extends AbstractAttributeSet {

  public static class VhdlGenericAttribute extends Attribute<Integer> {
    int start, end;
    VhdlContent.Generic g;

    private VhdlGenericAttribute(String name, StringGetter disp, int start, int end, VhdlContent.Generic g) {
      super(name, disp);
      this.start = start;
      this.end = end;
      this.g = g;
    }

    public VhdlContent.Generic getGeneric() {
      return g;
    }

    @Override
    public java.awt.Component getCellEditor(Integer value) {
      return super.getCellEditor(value != null ? value : g.getDefaultIntValue());
    }

    @Override
    public Integer parse(String value) {
      if (value == null)
        return null;
      value = value.trim();
      if (value.length() == 0 || value.equals("default") || value.equals("(default)") || value.equals(toDisplayString(null)))
        return null;
      long v = (long) Long.parseLong(value);
      if (v < start)
        throw new NumberFormatException("integer too small");
      if (v > end)
        throw new NumberFormatException("integer too large");
      return Integer.valueOf((int)v);
    }

    @Override
    public String toDisplayString(Integer value) {
      return value == null ? "(default) " + g.getDefaultValue() : value.toString();
    }

    @Override
    public Domain getDomain() {
      return Domain.ofIntRange(start, end);
    }
  }

  public static Attribute<Integer> forGeneric(VhdlContent.Generic g) {
    String name = g.getName();
    StringGetter disp = StringUtil.constantGetter(name);
    if (g.getType().equals("positive"))
      return new VhdlGenericAttribute("vhdl_" + name, disp, 1, Integer.MAX_VALUE, g);
    else if (g.getType().equals("natural"))
      return new VhdlGenericAttribute("vhdl_" + name, disp, 0, Integer.MAX_VALUE, g);
    else
      return new VhdlGenericAttribute("vhdl_" + name, disp, Integer.MIN_VALUE, Integer.MAX_VALUE, g);
  }

  // private static List<Attribute<?>> static_attributes =
  //     Arrays.asList((Attribute<?>)VhdlEntity.NAME_ATTR, StdAttr.APPEARANCE);
  
  // For each vhdl, one of these listens for certain changes to static
  // attributes (name changes, and changes of appearance type).
  private static class StaticListener implements AttributeListener {
    private VhdlContent vhdl;

    private StaticListener(VhdlContent v) { vhdl = v; }

    public void attributeListChanged(AttributeEvent e) { }

    public void attributeValueChanged(AttributeEvent e) {
      if (e.getAttribute() == VhdlEntity.NAME_ATTR) {
        // When the name attribute changes, fire HdlContent.nameChanged() ?
        // Or when the name changes, fire HdlContent.contentSet() ?
        // vhdl.fireContentSet();
        vhdl.setName((String)e.getValue());
      } else if (e.getAttribute() == StdAttr.APPEARANCE) {
        // When the appearance attribute changes, update the appearance in the
        // factory and recalculate the shape.
        // vhdl.getEntityFactory()
        // source.getAppearance().setDefaultAppearance(true);
        // source.RecalcDefaultShape();
        vhdl.setAppearance((AttributeOption)e.getValue());
      }
    }
  }

  static AttributeSet createBaseAttrs(VhdlContent content) {
    // VhdlContent.Generic[] g = content.getGenerics();
    // List<Attribute<Integer>> a = content.getGenericAttributes();
    Attribute<?>[] attrs = new Attribute<?>[2/* + g.length*/];
    Object[] value = new Object[2/* + g.length*/];
    attrs[0] = VhdlEntity.NAME_ATTR;
    value[0] = content.getName();
    attrs[1] = StdAttr.APPEARANCE;
    value[1] = StdAttr.APPEAR_FPGA;
    // for (int i = 0; i < g.length; i++) {
    //   attrs[2+i] = a.get(i);
    //   value[2+i] = new Integer(g[i].getDefaultValue());
    // }
    // FIXME: mark name as not-to-save?
    AttributeSet ret = AttributeSets.fixedSet(attrs, value);
    ret.addAttributeWeakListener(content, new StaticListener(content));
    return ret;
  }

  /* private class MyListener implements AttributeListener {
    public void attributeListChanged(AttributeEvent e) { }
    public void attributeValueChanged(AttributeEvent e) {
      if (e.getAttribute() == VhdlEntity.NAME_ATTR)
        setAttr(VhdlEntity.NAME_ATTR, (String)e.getValue());
      else if (e.getAttribute() == StdAttr.APPEARANCE)
        setAttr(StdAttr.APPEARANCE, (AttributeOption)e.getValue());
    }
  } */

  private VhdlContent content;
  private Instance vhdlInstance;
  private String label = "";
  private Font labelFont = StdAttr.DEFAULT_LABEL_FONT;
  private Direction facing = Direction.EAST;
  private HashMap<Attribute<Integer>, Integer> genericValues;
  private List<Attribute<?>> instanceAttrs;
  private VhdlEntityListener listener; // strong ref

  VhdlEntityAttributes(VhdlContent content) {
    this.content = content;
    genericValues = null;
    vhdlInstance = null;
    listener = null;
    updateGenerics();
  }

  public VhdlContent getContent() {
    return content;
  }

  public Direction getFacing() {
    return facing;
  }

  void setInstance(Instance value) {
    vhdlInstance = value;
    if (vhdlInstance != null && listener == null) {
      listener = new VhdlEntityListener(this);
      content.addHdlModelWeakListener(null, listener);
    }
  }

  void updateGenerics() {
    List<Attribute<Integer>> genericAttrs = content.getGenericAttributes();
    instanceAttrs = new ArrayList<Attribute<?>>(3 + genericAttrs.size());
    instanceAttrs.add(StdAttr.FACING);
    instanceAttrs.add(StdAttr.LABEL);
    // todo: StdAttr.LABEL_LOC
    instanceAttrs.add(StdAttr.LABEL_FONT);
    for (Attribute<Integer> a : genericAttrs)
      instanceAttrs.add(a);
    if (genericValues == null)
      genericValues = new HashMap<Attribute<Integer>, Integer>();
    ArrayList<Attribute<Integer>> toRemove = new ArrayList<>();
    for (Attribute<Integer> a : genericValues.keySet()) {
      if (!genericAttrs.contains(a))
        toRemove.add(a);
    }
    for (Attribute<Integer> a : toRemove)
      genericValues.remove(a);
    fireAttributeListChanged();
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    VhdlEntityAttributes attr = (VhdlEntityAttributes) dest;
    attr.content = content; // .clone();
    // note: attr.label is left unchanged
    attr.labelFont = labelFont;
    attr.facing = facing;
    attr.instanceAttrs = instanceAttrs;
    attr.genericValues = new HashMap<Attribute<Integer>, Integer>();
    for (Attribute<Integer> a : genericValues.keySet())
      attr.genericValues.put(a, genericValues.get(a));
    attr.listener = null;
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return instanceAttrs;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getValue(Attribute<V> attr) {
    // if (attr == VhdlEntity.NAME_ATTR)
    //   return (V) content.getName(); // never happens
    // if (attr == StdAttr.APPEARANCE)
    //   return (V) content.getAppearance(); // never happens
    if (attr == StdAttr.LABEL)
      return (V) label;
    if (attr == StdAttr.LABEL_FONT)
      return (V) labelFont;
    if (attr == StdAttr.FACING)
      return (V) facing;
    if (genericValues != null)
      return (V) genericValues.get((Attribute<Integer>)attr);
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> void updateAttr(Attribute<V> attr, V value) {
    // if (attr == VhdlEntity.NAME_ATTR)
    //   content.setName((String) value); // never happens
    // else if (attr == StdAttr.APPEARANCE)
    //  content.setAppearance((AttributeOption)value); // never happens
    if (attr == StdAttr.LABEL)
      label = (String) value;
    else if (attr == StdAttr.LABEL_FONT)
      labelFont = (Font) value;
    else if (attr == StdAttr.FACING)
      facing = (Direction) value;
    else if (genericValues != null)
      genericValues.put((Attribute<Integer>)attr, (Integer)value);
  }

  // For each vhdl instance, one of these listens to the vhdl content to be
  // notified of changes to appearance, name, etc.
  static class VhdlEntityListener implements HdlModelListener {
    VhdlEntityAttributes attrs;
    VhdlEntityListener(VhdlEntityAttributes a) { attrs = a; }
    
    @Override
    public void contentSet(HdlModel source) {
      CircuitTransaction xn = new VhdlUpdatedTransaction(attrs);
      xn.execute();
      // attrs.updateGenerics();
      // // System.out.println("updating ports...");
      // ((VhdlEntity)attrs.vhdlInstance.getFactory()).updatePorts(attrs.vhdlInstance);
      // attrs.vhdlInstance.fireInvalidated();
      // // attrs.vhdlInstance.recomputeBounds(); // also recompute ports
      // // attrs.fireAttributeValueChanged(VhdlEntity.NAME_ATTR, ((VhdlContent)source).getName());
    }

    private static class VhdlUpdatedTransaction extends CircuitTransaction {
      VhdlEntityAttributes attrs;
      Instance vhdlInstance;
      VhdlEntity vhdlEntity;
      VhdlUpdatedTransaction(VhdlEntityAttributes a) {
        attrs = a;
        vhdlInstance = attrs.vhdlInstance;
        vhdlEntity = (VhdlEntity)vhdlInstance.getFactory();
      }
      @Override
      protected Set<Circuit> getAccessedCircuits() {
        // note: this is overkill by far... this transaction only touches one
        // circuit, but sadly we don't know which one.
        return new HashSet<>(vhdlEntity.getCircuitsUsingThis());
      }
      @Override
      protected void run(CircuitMutator mutator) {
        attrs.updateGenerics();
        vhdlEntity.updatePorts(attrs.vhdlInstance);
        vhdlInstance.fireInvalidated();
      }
    }
    
    @Override
    public void aboutToSave(HdlModel source) { }
    
    @Override
    public void displayChanged(HdlModel source) { }
    
    @Override
    public void appearanceChanged(HdlModel source) {
      // attrs.vhdlInstance.recomputeBounds(); // also recompute ports...
      // System.out.println("updating ports 2...");
      ((VhdlEntity)attrs.vhdlInstance.getFactory()).updatePorts(attrs.vhdlInstance);
      attrs.vhdlInstance.fireInvalidated();
      // attrs.fireAttributeValueChanged(StdAttr.APPEARANCE, ((VhdlContent)source).getAppearance());
    }
  }

}
