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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentEvent;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentListener;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.TextEditable;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.util.EventSourceWeakSupport;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.UnmodifiableList;

// Tentative Design Notes (2 of 3): InstanceComponent and Instance are two sides
// of the same coin. Every java InstanceComponent object has exactly one java
// Instance object as a member variable, and every java Instance object in turn
// has that same java InstanceComponent object as a member variable. There are
// no other uses for Instance or InstanceComponent anywhere in Logisim. Neither
// is ever extended in any way. I'm not sure why the two java files aren't
// simply merged into one. Perhaps historical? Perhaps plugins do something
// different? Maybe some kind of java bytecode lazy loading optimization? For
// now, I've marked these both as "final". If something breaks, maybe we'll find
// out. 
// Edit: InstanceComponent is no longer final, and now has subclasses within
// std/base/Text and std/wiring/Tunnel. Those classes have some trouble with
// computing Bounds (unlike all other components, they really needs a graphics
// context to get the bounds), so they now have their own sublass of
// InstanceComponent.
// 
// So, to sum up:
//
//    For every InstanceComponent c, we have: c.instance.comp == c
//    For every Instance i, we have: i.comp.instance == i
//    Thus i and c are essentially interchangeable.

public /*final*/ class InstanceComponent
  implements Component, AttributeListener, ToolTipMaker {

  private EventSourceWeakSupport<ComponentListener> listeners;
  private InstanceFactory factory;
  private Instance instance;
  private Location loc;
  private Bounds nominalBounds;
  private List<Port> portList;
  private EndData[] endArray;
  private List<EndData> endList;
  private boolean hasToolTips;
  private HashSet<Attribute<BitWidth>> widthAttrs;
  private AttributeSet attrs;
  private boolean attrListenRequested;
  private InstanceTextField textField; // note: not used by Text or Callout
  private InstanceStateImpl instanceState;

  public InstanceComponent(InstanceFactory factory, Location loc,
      AttributeSet attrs) {
    this.listeners = null;
    this.factory = factory;
    this.instance = Instance.makeFor(this); // new Instance(this);
    this.loc = loc;
    this.nominalBounds = factory.getOffsetBounds(attrs).translate(loc);
    this.portList = factory.getPorts();
    this.endArray = null;
    this.hasToolTips = false;
    this.attrs = attrs;
    this.attrListenRequested = false;
    this.textField = null;

    computeEnds();
  }

  void addAttributeListener(/*Instance instance*/) {
    if (!attrListenRequested) {
      attrListenRequested = true;
      if (widthAttrs == null)
        getAttributeSet().addAttributeWeakListener(null, this);
    }
  }

  public void addComponentWeakListener(Object owner, ComponentListener l) {
    EventSourceWeakSupport<ComponentListener> ls = listeners;
    if (ls == null) {
      ls = new EventSourceWeakSupport<ComponentListener>();
      ls.add(owner, l);
      listeners = ls;
    } else {
      ls.add(owner, l);
    }
  }

  public void attributeListChanged(AttributeEvent e) {
  }

  public void attributeValueChanged(AttributeEvent e) {
    Attribute<?> attr = e.getAttribute();
    if (widthAttrs != null && widthAttrs.contains(attr))
      computeEnds();
    if (attrListenRequested) {
      factory.instanceAttributeChanged(instance, e.getAttribute());
    }
  }

  private void computeEnds() {
    List<Port> ports = portList;
    EndData[] esOld = endArray;
    int esOldLength = esOld == null ? 0 : esOld.length;
    EndData[] es = esOld;
    if (es == null || es.length != ports.size()) {
      es = new EndData[ports.size()];
      if (esOldLength > 0) {
        int toCopy = Math.min(esOldLength, es.length);
        System.arraycopy(esOld, 0, es, 0, toCopy);
      }
    }
    HashSet<Attribute<BitWidth>> wattrs = null;
    boolean toolTipFound = false;
    ArrayList<EndData> endsChangedOld = null;
    ArrayList<EndData> endsChangedNew = null;
    Iterator<Port> pit = ports.iterator();
    for (int i = 0; pit.hasNext() || i < esOldLength; i++) {
      Port p = pit.hasNext() ? pit.next() : null;
      EndData oldEnd = i < esOldLength ? esOld[i] : null;
      EndData newEnd = p == null ? null : p.toEnd(loc, attrs);
      if (oldEnd == null || !oldEnd.equals(newEnd)) {
        if (newEnd != null)
          es[i] = newEnd;
        if (endsChangedOld == null) {
          endsChangedOld = new ArrayList<EndData>();
          endsChangedNew = new ArrayList<EndData>();
        }
        endsChangedOld.add(oldEnd);
        endsChangedNew.add(newEnd);
      }

      if (p != null) {
        Attribute<BitWidth> attr = p.getWidthAttribute();
        if (attr != null) {
          if (wattrs == null) {
            wattrs = new HashSet<Attribute<BitWidth>>();
          }
          wattrs.add(attr);
        }

        if (p.getToolTip() != null)
          toolTipFound = true;
      }
    }
    if (!attrListenRequested) {
      HashSet<Attribute<BitWidth>> oldWattrs = widthAttrs;
      if (wattrs == null && oldWattrs != null) {
        getAttributeSet().removeAttributeWeakListener(null, this);
      } else if (wattrs != null && oldWattrs == null) {
        getAttributeSet().addAttributeWeakListener(null, this);
      }
    }
    if (es != esOld) {
      endArray = es;
      endList = new UnmodifiableList<EndData>(es);
    }
    widthAttrs = wattrs;
    hasToolTips = toolTipFound;
    if (endsChangedOld != null) {
      fireEndsChanged(endsChangedOld, endsChangedNew);
    }
  }

  public boolean nominallyContains(Location pt) {
    Location translated = pt.translate(-loc.getX(), -loc.getY());
    return factory.nominallyContains(translated, instance.getAttributeSet());
  }

  public boolean visiblyContains(Location pt) { // note: Text and Tunnel override this
    InstanceTextField field = textField;
    if (field != null && field.getVisibleBounds().contains(pt))
      return true;
    else
      return nominallyContains(pt);
  }

  //
  // drawing methods
  //
  public void draw(ComponentDrawContext context) {
    InstancePainter painter = context.getInstancePainter();
    painter.setComponent(this);
    factory.paintInstance(painter);
  }

  public void drawLabel(ComponentDrawContext context) { // note: not used by Text or Callout
    InstanceTextField field = textField;
    if (field != null)
      field.draw(this, context);
  }

  public boolean endsAt(Location pt) {
    EndData[] ends = endArray;
    for (int i = 0; i < ends.length; i++) {
      if (ends[i].getLocation().equals(pt))
        return true;
    }
    return false;
  }

  public void expose(ComponentDrawContext context) {
    Bounds b = getVisibleBounds();
    context.getDestination().repaint(b.getX(), b.getY(), b.getWidth(), b.getHeight());
  }

  private void fireEndsChanged(ArrayList<EndData> oldEnds,
      ArrayList<EndData> newEnds) {
    EventSourceWeakSupport<ComponentListener> ls = listeners;
    if (ls != null) {
      ComponentEvent e = null;
      for (ComponentListener l : ls) {
        if (e == null)
          e = new ComponentEvent(this, oldEnds, newEnds);
        l.endChanged(e);
      }
    }
  }

  @Override
  public void fireInvalidated() {
    EventSourceWeakSupport<ComponentListener> ls = listeners;
    if (ls != null) {
      ComponentEvent e = null;
      for (ComponentListener l : ls) {
        if (e == null)
          e = new ComponentEvent(this);
        l.componentInvalidated(e);
      }
    }
  }

  public AttributeSet getAttributeSet() {
    return attrs;
  }

  public Bounds getNominalBounds() {
    return nominalBounds;
  }

  public Bounds getVisibleBounds() { // note: Text overrides this
    Bounds ret = nominalBounds;
    InstanceTextField field = textField;
    if (field != null)
      ret = ret.add(field.getVisibleBounds());
    return ret;
  }

  public EndData getEnd(int index) {
    return endArray[index];
  }

  public List<EndData> getEnds() {
    return endList;
  }

  public ComponentFactory getFactory() {
    return factory;
  }

  public Object getFeature(Object key) { // note: Text overrides this for key==TextEditable
    Object ret = factory.getInstanceFeature(instance, key);
    if (ret != null) {
      return ret;
    } else if (key == ToolTipMaker.class) {
      Object defaultTip = factory.getDefaultToolTip();
      if (hasToolTips || defaultTip != null)
        return this;
    } else if (key == TextEditable.class) {
      InstanceTextField field = textField;
      if (field != null)
        return field;
    }
    return null;
  }

  //
  // methods for Instance
  //
  public Instance getInstance() {
    return instance;
  }

  public InstanceStateImpl getInstanceStateImpl() {
    return instanceState;
  }

  //
  // location/extent methods
  //
  public Location getLocation() {
    return loc;
  }

  List<Port> getPorts() {
    return portList;
  }

  public String getToolTip(ComponentUserEvent e) {
    int x = e.getX();
    int y = e.getY();
    int i = -1;
    for (EndData end : endArray) {
      i++;
      if (end.getLocation().manhattanDistanceTo(x, y) < 10) {
        Port p = portList.get(i);
        return p.getToolTip();
      }
    }
    StringGetter defaultTip = factory.getDefaultToolTip();
    return defaultTip == null ? null : defaultTip.toString();
  }

  public void propagate(CircuitState state) {
    factory.propagate(state.dangerouslyGetTransientInstanceState(this));
  }

  void recomputeBounds() {
    nominalBounds = factory.getOffsetBounds(attrs).translate(loc);
  }

  public void removeComponentWeakListener(Object owner, ComponentListener l) {
    if (listeners != null) {
      listeners.remove(owner, l);
      if (listeners.isEmpty())
        listeners = null;
    }
  }

  public void setInstanceStateImpl(InstanceStateImpl instanceState) {
    this.instanceState = instanceState;
  }

  void setPorts(Port[] ports) {
    Port[] portsCopy = ports.clone();
    portList = new UnmodifiableList<Port>(portsCopy);
    computeEnds();
  }

  void setTextField(Attribute<String> labelAttr, Attribute<Font> fontAttr,
      int x, int y, int halign, int valign) { // note: not used by Text or Callout
    InstanceTextField field = textField;
    if (field == null) {
      field = new InstanceTextField(this);
      field.update(labelAttr, fontAttr, x, y, halign, valign);
      textField = field;
    } else {
      field.update(labelAttr, fontAttr, x, y, halign, valign);
    }
  }

  public String toString() { // note: Text overrides this
    InstanceTextField field = textField;
    if (field != null) {
      String label = field.getText();
      return "InstanceComponent{factory="+factory.getName()
          +",loc="+loc+",textfield="+label+"}@"+System.identityHashCode(this);
    } else {
      String label = attrs.getValue(StdAttr.LABEL);
      return "InstanceComponent{factory="+factory.getName()
          +",loc="+loc+",stdlabel="+label+"}@"+System.identityHashCode(this);
    }
  }
}
