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

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.appear.CircuitAppearance;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentEvent;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentListener;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.FailException;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.TestException;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.std.memory.Rom;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.EventSourceWeakSupport;

public class Circuit implements AttributeDefaultProvider {
  private class EndChangedTransaction extends CircuitTransaction {
    private Component comp;
    private Map<Location, EndData> toRemove;
    private Map<Location, EndData> toAdd;

    EndChangedTransaction(Component comp, Map<Location, EndData> toRemove,
        Map<Location, EndData> toAdd) {
      this.comp = comp;
      this.toRemove = toRemove;
      this.toAdd = toAdd;
    }

    @Override
    protected Map<Circuit, Integer> getAccessedCircuits() {
      return Collections.singletonMap(Circuit.this, READ_WRITE);
    }

    @Override
    protected void run(CircuitMutator mutator) {
      for (Location loc : toRemove.keySet()) {
        EndData removed = toRemove.get(loc);
        EndData replaced = toAdd.remove(loc);
        if (replaced == null) {
          wires.remove(comp, removed);
        } else if (!replaced.equals(removed)) {
          wires.replace(comp, removed, replaced);
        }
      }
      for (EndData end : toAdd.values()) {
        wires.add(comp, end);
      }
      ((CircuitMutatorImpl) mutator).markModified(Circuit.this);
    }
  }

  private class MyComponentListener implements ComponentListener {
    public void componentInvalidated(ComponentEvent e) {
      fireEvent(CircuitEvent.ACTION_INVALIDATE, e.getSource());
    }

    public void endChanged(ComponentEvent e) {
      locker.checkForWritePermission("ends changed", Circuit.this);
      Component comp = e.getSource();
      HashMap<Location, EndData> toRemove = toMap(e.getOldData());
      HashMap<Location, EndData> toAdd = toMap(e.getData());
      EndChangedTransaction xn = new EndChangedTransaction(comp,
          toRemove, toAdd);
      locker.execute(xn);
      fireEvent(CircuitEvent.ACTION_INVALIDATE, comp);
    }

    private HashMap<Location, EndData> toMap(Object val) {
      HashMap<Location, EndData> map = new HashMap<Location, EndData>();
      if (val instanceof List) {
        @SuppressWarnings("unchecked")
        List<EndData> valList = (List<EndData>) val;
        for (EndData end : valList) {
          if (end != null) {
            map.put(end.getLocation(), end);
          }
        }
      } else if (val instanceof EndData) {
        EndData end = (EndData) val;
        map.put(end.getLocation(), end);
      }
      return map;
    }
  }

  public static boolean isInput(Component comp) {
    return comp.getEnd(0).getType() != EndData.INPUT_ONLY;
  }

  private MyComponentListener myComponentListener = new MyComponentListener();
  private CircuitAppearance appearance;
  private AttributeSet staticAttrs;
  private SubcircuitFactory subcircuitFactory;
  private EventSourceWeakSupport<CircuitListener> listeners = new EventSourceWeakSupport<CircuitListener>();
  private HashSet<Component> comps = new HashSet<Component>(); // doesn't include wires
  CircuitWires wires = new CircuitWires();
  // wires is package-protected for CircuitState and Analyze only.
  private ArrayList<Component> clocks = new ArrayList<Component>();
  private CircuitLocker locker;

  private WeakHashMap<Component, Circuit> circuitsUsingThis;

  private LogisimFile logiFile;

  public Circuit(String name, LogisimFile file) {
    appearance = new CircuitAppearance(this);
    staticAttrs = CircuitAttributes.createBaseAttrs(this, file, name);
    subcircuitFactory = new SubcircuitFactory(this);
    locker = new CircuitLocker();
    circuitsUsingThis = new WeakHashMap<Component, Circuit>();
    logiFile = file;
  }

  public LogisimFile getLogisimFile() {
    return logiFile;
  }

  public void addCircuitWeakListener(Object owner, CircuitListener l) { listeners.add(owner, l); }
  public void removeCircuitWeakListener(Object owner, CircuitListener l) { listeners.remove(owner, l); }

  public void RecalcDefaultShape() {
    if (appearance.isDefaultAppearance()) {
      appearance.recomputeDefaultAppearance();
    }
  }

  public void autoHdlAnnotate(FPGAReport err) {
    ArrayList<Component> comps = new ArrayList<>();
    for (Component comp : this.getNonWires()) {
      if (comp.getFactory().HDLIgnore())
        continue; // ignore components that do not end up in HDL
      if (comp.getFactory().getHDLNamePrefix(comp) == null)
        continue; // ignore ocmponents that do not need a Path name
      comps.add(comp);
    }

    // Scan existing labels to make sure they are okay.
    HashMap<String, Component> names = new HashMap<>();
    for (Component comp : comps) {
      if (comp.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory sub = (SubcircuitFactory) comp.getFactory();
        sub.getSubcircuit().autoHdlAnnotate(err);
      }
      String name = HDLSupport.deriveHdlPathName(comp);
      if (!names.containsKey(name)) {
        names.put(name, comp); // no name clash, leave this one alone
        continue;
      }
      // Name is alreayd taken, change label, or pick a new label
      String label = comp.getAttributeSet().getValueOrElse(StdAttr.LABEL, "");
      int seqno = 0;
      String suffix;
      if (!label.isEmpty())
        suffix = label + "_";
      else
        suffix = ""+name.toLowerCase().charAt(0); // e.g. Button b0, Switch s0
      String newLabel;
      do {
        newLabel = suffix + (++seqno);
        comp.getAttributeSet().setAttr(StdAttr.LABEL, newLabel);
        name = HDLSupport.deriveHdlPathName(comp);
      } while (names.containsKey(name));
      names.put(name, comp);
      if (label.isEmpty())
        err.AddInfo("Circuit %s: Added label '%s' for %s at %s.",
            this.getName(), newLabel, comp.getFactory().getDisplayName(), comp.getLocation());
      else
        err.AddInfo("Circuit %s: Changed label '%s' from '%s' for %s at %s.",
            this.getName(), newLabel, label, comp.getFactory().getDisplayName(), comp.getLocation());
    }
    err.AddInfo("Circuit %s: Finished annotating all components.", this.getName());
    // FIXME: The above use of setAttr() is a dirty hack. I do not know how to
    // change the label in such a way that the whole project is correctly
    // notified so I just change the label here and do some dirty updates in
    // fpga gui Commander.java
  }

  public boolean contains(Component c) {
    return comps.contains(c) || wires.getWires().contains(c);
  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  public void doTestVector(Project project, Instance pin[], Value[] val)
      throws TestException {
    CircuitState state = project.getCircuitState();
    state.reset();

    for (int i = 0; i < pin.length; ++i) {
      if (Pin.FACTORY.isInputPin(pin[i])) {
        InstanceState pinState = state.getInstanceState(pin[i]);
        Pin.FACTORY.driveInputPin(pinState, val[i]);
      }
    }

    Propagator prop = state.getPropagator();

    try {
      prop.propagate();
    } catch (Throwable thr) {
      thr.printStackTrace();
    }

    if (prop.isOscillating())
      throw new TestException("oscillation detected");

    FailException err = null;

    for (int i = 0; i < pin.length; i++) {
      InstanceState pinState = state.getInstanceState(pin[i]);
      if (Pin.FACTORY.isInputPin(pin[i]))
        continue;

      Value v = Pin.FACTORY.getValue(pinState);
      if (!val[i].compatible(v)) {
        if (err == null)
          err = new FailException(i,
              pinState.getAttributeValue(StdAttr.LABEL), val[i], v);
        else
          err.add(new FailException(i,
                pinState.getAttributeValue(StdAttr.LABEL), val[i], v));
      }
    }

    if (err != null) {
      throw err;
    }
  }

  //
  // Graphics methods
  //
  public void draw(ComponentDrawContext context, Collection<Component> hidden) {
    Graphics g = context.getGraphics();
    Graphics g_copy = g.create();
    context.setGraphics(g_copy);
    wires.draw(context, hidden);

    if (hidden == null || hidden.size() == 0) {
      for (Component c : comps) {
        Graphics g_new = g.create();
        context.setGraphics(g_new);
        g_copy.dispose();
        g_copy = g_new;

        c.draw(context);
      }
    } else {
      for (Component c : comps) {
        if (!hidden.contains(c)) {
          Graphics g_new = g.create();
          context.setGraphics(g_new);
          g_copy.dispose();
          g_copy = g_new;

          try {
            c.draw(context);
          } catch (RuntimeException e) {
            // this is a JAR developer error - display it and move on
            e.printStackTrace();
          }
        }
      }
    }
    context.setGraphics(g);
    g_copy.dispose();
  }

  private void fireEvent(CircuitEvent event) {
    for (CircuitListener l : listeners)
      l.circuitChanged(event);
  }

  void fireEvent(int action, Object data) {
    fireEvent(new CircuitEvent(action, this, data));
  }

  public void displayChanged() {
    fireEvent(CircuitEvent.ACTION_DISPLAY_CHANGE, null);
  }

  public Collection<Component> getAllContaining(Location pt) {
    HashSet<Component> ret = new HashSet<>();
    for (Component comp : getComponents()) {
      if (comp.contains(pt))
        ret.add(comp);
    }
    return ret;
  }

  public Collection<Component> getAllContaining(Location pt, Graphics g) {
    HashSet<Component> ret = new HashSet<>();
    for (Component comp : getComponents()) {
      if (comp.contains(pt, g))
        ret.add(comp);
    }
    return ret;
  }

  public Collection<Component> getAllWithin(Bounds bds) {
    HashSet<Component> ret = new HashSet<>();
    for (Component comp : getComponents()) {
      if (bds.contains(comp.getBounds()))
        ret.add(comp);
    }
    return ret;
  }

  public Collection<Component> getAllWithin(Bounds bds, Graphics g) {
    HashSet<Component> ret = new HashSet<>();
    for (Component comp : getComponents()) {
      if (bds.contains(comp.getBounds(g)))
        ret.add(comp);
    }
    return ret;
  }

  public Collection<Wire> getWiresIntersecting(Bounds bds) {
    HashSet<Wire> ret = new HashSet<>();
    for (Wire w: wires.getWires()) {
      Bounds b = w.getBounds();
      if (bds.overlaps(b)) // && !bds.contains(b)
        ret.add(w);
    }
    return ret;
  }

  public CircuitAppearance getAppearance() {
    return appearance;
  }

  public Bounds getCircuitBounds(Graphics g) {
    Bounds wireBounds = wires.getWireBounds();
    Iterator<Component> it = comps.iterator();
    if (!it.hasNext())
      return wireBounds;
    Component first = it.next();
    Bounds firstBounds = g == null ? first.getBounds() : first.getBounds(g);
    int xMin = firstBounds.getX();
    int yMin = firstBounds.getY();
    int xMax = xMin + firstBounds.getWidth();
    int yMax = yMin + firstBounds.getHeight();
    while (it.hasNext()) {
      Component c = it.next();
      Bounds bds = g == null ? c.getBounds() : c.getBounds(g);
      int x0 = bds.getX();
      int x1 = x0 + bds.getWidth();
      int y0 = bds.getY();
      int y1 = y0 + bds.getHeight();
      if (x0 < xMin)
        xMin = x0;
      if (x1 > xMax)
        xMax = x1;
      if (y0 < yMin)
        yMin = y0;
      if (y1 > yMax)
        yMax = y1;
    }
    Bounds compBounds = Bounds.create(xMin, yMin, xMax - xMin, yMax - yMin);
    if (wireBounds.getWidth() == 0 || wireBounds.getHeight() == 0) {
      return compBounds;
    } else {
      return compBounds.add(wireBounds);
    }
  }

  public Collection<Circuit> getCircuitsUsingThis() {
    return circuitsUsingThis.values();
  }

  public ArrayList<Component> getClocks() {
    return clocks;
  }

  private Set<Component> getComponents() {
    return CollectionUtil.createUnmodifiableSetUnion(comps,
        wires.getWires());
  }

  public Collection<? extends Component> getComponents(Location loc) {
    return wires.points.getComponents(loc);
  }

  public Component getExclusive(Location loc) {
    return wires.points.getExclusive(loc);
  }

  public CircuitLocker getLocker() {
    return locker;
  }

  //
  // access methods
  //
  public String getName() {
    return staticAttrs.getValue(CircuitAttributes.NAME_ATTR);
  }

  public Set<Component> getNonWires() {
    return comps;
  }

  public boolean isEmpty() {
    return comps.isEmpty() && wires.getWires().isEmpty();
  }

  public String chooseUniqueLabel(String suggestion) {
    String[] p = suggestion.split("(?=\\d+$)", 2);
    String prefix = p[0];
    int suffix = 0;
    String fmt = "%s%d";
    if (p.length == 2) try {
      suffix = Integer.parseInt(p[1]);
      int len = p[0].length();
      fmt = "%s%0" + len + "d";
    }
    catch (NumberFormatException e) { }
    while (labelConflicts(suggestion)) {
      suggestion = String.format(fmt, prefix, ++suffix);
    }
    return suggestion;
  }

  private boolean labelConflicts(String label) {
    for (Component c : comps) {
      if (label.equals(c.getAttributeSet().getValue(StdAttr.LABEL)))
        return true;
    }
    return false;
  }

  public Collection<? extends Component> getNonWires(Location loc) {
    return wires.points.getNonWires(loc);
  }

  public String getProjName() {
    return logiFile.getName();
  }

  public Collection<? extends Component> getSplitCauses(Location loc) {
    return wires.points.getSplitCauses(loc);
  }

  public Set<Location> getAllLocations() {
    return wires.points.getAllLocations();
  }

  public AttributeSet getStaticAttributes() {
    return staticAttrs;
  }

  public SubcircuitFactory getSubcircuitFactory() {
    return subcircuitFactory;
  }

  public BitWidth getWidth(Location p) {
    return wires.getWidth(p);
  }

  public Set<WidthIncompatibilityData> getWidthIncompatibilityData() {
    return wires.getWidthIncompatibilityData();
  }

  public Set<Wire> getWires() {
    return wires.getWires();
  }

  public Collection<Wire> getWires(Location loc) {
    return wires.points.getWires(loc);
  }

  public List<Wire> getWiresTouching(Location loc) {
    return wires.getWiresTouching(loc);
  }

  public List<Wire> getWireSequenceEndingAt(Wire w, Location loc) {
    return wires.points.getWireSequenceEndingAt(w, loc);
  }

  public WireSet getWireSet(Wire start) {
    return wires.getWireSet(start);
  }

  public boolean hasConflict(Component comp) {
    // return wires.points.hasConflict(comp) || logiFile.hasConflict(comp);
    return wires.points.hasConflict(comp);
  }

  public boolean isConnected(Location loc, Component ignore) {
    for (Component o : wires.points.getComponents(loc)) {
      if (o != ignore)
        return true;
    }
    return false;
  }

  void mutatorAdd(Component c) {

    locker.checkForWritePermission("add", this);

    if (c instanceof Wire) {
      Wire w = (Wire) c;
      if (w.getEnd0().equals(w.getEnd1()))
        return;
      boolean added = wires.add(w);
      if (!added)
        return;
    } else {
      // add it into the circuit
      boolean added = comps.add(c);
      if (!added)
        return;

      wires.add(c);
      ComponentFactory factory = c.getFactory();
      if (factory instanceof Clock) {
        clocks.add(c);
      } else if (factory instanceof SubcircuitFactory) {
        SubcircuitFactory subcirc = (SubcircuitFactory) factory;
        subcirc.getSubcircuit().circuitsUsingThis.put(c, this);
      } else if (factory instanceof VhdlEntity) {
        VhdlEntity vhdl = (VhdlEntity)factory;
        // logiFile.addVhdlContent(vhdl.getContent());
        vhdl.addCircuitUsing(c, this);
      }
      c.addComponentWeakListener(null, myComponentListener);
    }
    fireEvent(CircuitEvent.ACTION_ADD, c);
  }

  public void mutatorClear() {
    locker.checkForWritePermission("clear", this);

    Set<Component> oldComps = comps;
    comps = new HashSet<Component>();
    wires = new CircuitWires();
    clocks.clear();
    for (Component comp : oldComps) {
      if (comp.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory sub = (SubcircuitFactory) comp.getFactory();
        sub.getSubcircuit().circuitsUsingThis.remove(comp);
      } else if (comp.getFactory() instanceof VhdlEntity) {
        VhdlEntity vhdl = (VhdlEntity)comp.getFactory();
        vhdl.removeCircuitUsing(comp);
      }
    }
    fireEvent(CircuitEvent.ACTION_CLEAR, oldComps);
  }

  void mutatorRemove(Component c) {

    locker.checkForWritePermission("remove", this);

    if (c instanceof Wire) {
      wires.remove(c);
    } else {
      wires.remove(c);
      comps.remove(c);
      ComponentFactory factory = c.getFactory();
      if (factory instanceof Clock) {
        clocks.remove(c);
      } else if (factory instanceof Rom) {
        Rom.closeHexFrame(c);
      } else if (factory instanceof SubcircuitFactory) {
        SubcircuitFactory subcirc = (SubcircuitFactory) factory;
        subcirc.getSubcircuit().circuitsUsingThis.remove(c);
      } else if (factory instanceof VhdlEntity) {
        VhdlEntity vhdl = (VhdlEntity)factory;
        vhdl.removeCircuitUsing(c);
      } else if (factory instanceof DynamicElementProvider
          && c instanceof InstanceComponent) {
        // TODO: remove stale appearance dynamic elements in
        // CircuitTransaction.execute() instead?
        HashSet<Circuit> allAffected = new HashSet<>();
        LinkedList<Circuit> todo = new LinkedList<>();
        todo.add(this);
        while (!todo.isEmpty()) {
          Circuit circ = todo.remove();
          if (allAffected.contains(circ))
            continue;
          allAffected.add(circ);
          for (Circuit other : circ.circuitsUsingThis.values())
            if (!allAffected.contains(other))
              todo.add(other);
        }
        for (Circuit circ : allAffected)
          circ.appearance.removeDynamicElement((InstanceComponent)c);
      }
      c.removeComponentWeakListener(null, myComponentListener);
    }
    fireEvent(CircuitEvent.ACTION_REMOVE, c);
  }

  // Note: caller must have validated name already
  public void setCircuitName(String name) {
    staticAttrs.setAttr(CircuitAttributes.NAME_ATTR, name);
  }

  @Override
  public String toString() {
    return staticAttrs.getValue(CircuitAttributes.NAME_ATTR);
  }
  
  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver) {
    return false;
  }

  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    if (attr == CircuitAttributes.NAME_ATTR)
      return null;
    for (int i = 0; i < CircuitAttributes.STATIC_ATTRS.length; i++) {
      if (CircuitAttributes.STATIC_ATTRS[i] == attr)
        return CircuitAttributes.STATIC_DEFAULTS[i];
    }
    return null;
  }

  private ArrayList<PinBindings.Config> fpgaConfigs = new ArrayList<>();

  public ArrayList<PinBindings.Config> getFPGAConfigs() {
    return fpgaConfigs;
  }

  public PinBindings.Config getFPGAConfig(String boardname) {
    for (int i = 0; i < fpgaConfigs.size(); i++)
      if (fpgaConfigs.get(i).boardname.equals(boardname))
        return fpgaConfigs.get(i);
    return null;
  }

  public void saveFPGAConfig(PinBindings.Config config) {
    for (int i = 0; i < fpgaConfigs.size(); i++) {
      if (fpgaConfigs.get(i).boardname.equals(config.boardname)) {
        fpgaConfigs.set(i, config);
        return;
      }
    }
    fpgaConfigs.add(config);
  }

}
