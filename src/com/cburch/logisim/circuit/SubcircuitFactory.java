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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.bfh.logisim.hdlgenerator.CircuitHDLGenerator;
import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.circuit.appear.CircuitAppearanceListener;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;

public class SubcircuitFactory extends InstanceFactory {
  private class CircuitFeature
    implements StringGetter, MenuExtender {
    private Instance instance;
    private Project proj;

    public CircuitFeature(Instance instance) {
      this.instance = instance;
    }

    private void viewSubcircuit() {
      proj.setCurrentCircuit(source);
    }

    private void viewSubsimulation() {
      CircuitState superState = proj.getCircuitState();
      if (superState == null)
        return;
      CircuitState subState = superState.getCircuitSubstateFor(instance.getComponent());
      proj.setCircuitState(subState);
    }

    public void configureMenu(JPopupMenu menu, Project proj) {
      this.proj = proj;
      String name = instance.getFactory().getDisplayName();
      String simtext = S.fmt("subsimulationViewItem", name);
      JMenuItem simitem = new JMenuItem(simtext);
      simitem.addActionListener(e -> viewSubsimulation());
      menu.add(simitem);
      String text = S.fmt("subcircuitViewItem", name);
      JMenuItem item = new JMenuItem(text);
      item.addActionListener(e -> viewSubcircuit());
      menu.add(item);
    }

    public String toString() {
      return S.fmt("subcircuitCircuitTip", source.getName());
    }
  }

  private Circuit source;

  public SubcircuitFactory(Circuit source) {
    super("", null);
    this.source = source;
    setFacingAttribute(StdAttr.FACING);
    setDefaultToolTip(new CircuitFeature(null));
    setInstancePoker(SubcircuitPoker.class);
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
  }

  void computePortsBoundsAndLabel(Instance instance) {
    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    Map<Location, Instance> portLocs =
        source.getAppearance().getPortOffsets(facing);
    Port[] ports = new Port[portLocs.size()];
    Instance[] pins = new Instance[portLocs.size()];
    int i = -1;
    for (Map.Entry<Location, Instance> portLoc : portLocs.entrySet()) {
      i++;
      Location loc = portLoc.getKey();
      Instance pin = portLoc.getValue();
      String type = Pin.FACTORY.isInputPin(pin) ? Port.INPUT : Port.OUTPUT;
      BitWidth width = pin.getAttributeValue(StdAttr.WIDTH);
      ports[i] = new Port(loc.getX(), loc.getY(), type, width);
      pins[i] = pin;

      String label = pin.getAttributeValue(StdAttr.LABEL);
      if (label != null && label.length() > 0) {
        ports[i].setToolTip(StringUtil.constantGetter(label));
      }
    }

    SubcircuitAttributes attrs = (SubcircuitAttributes)instance.getAttributeSet();
    attrs.setPinInstances(pins);
    instance.setPorts(ports);
    instance.recomputeBounds();
    configureLabel(instance); // label position is affected the circuit's bounds
  }

  private void configureLabel(Instance instance) {
    Bounds bds = instance.getNominalBounds();
    Object loc = instance.getAttributeValue(StdAttr.LABEL_LOC);

    int x = bds.getX() + bds.getWidth() / 2;
    int y = bds.getY() + bds.getHeight() / 2;
    int ha = GraphicsUtil.H_CENTER;
    int va = GraphicsUtil.V_CENTER_FIRST; // CENTER_OVERALL may look nicer?
    if (loc == Direction.EAST) {
      x = bds.getX() + bds.getWidth() + 2;
      ha = GraphicsUtil.H_LEFT;
    } else if (loc == Direction.WEST) {
      x = bds.getX() - 2;
      ha = GraphicsUtil.H_RIGHT;
    } else if (loc == Direction.SOUTH) {
      y = bds.getY() + bds.getHeight() + 2;
      va = GraphicsUtil.V_TOP;
    } else if (loc == StdAttr.LABEL_CENTER) {
      ha = GraphicsUtil.H_CENTER;
      va = GraphicsUtil.V_CENTER_FIRST; // CENTER_OVERALL may look nicer?
    } else {
      y = bds.getY() - 2;
      va = GraphicsUtil.V_BASELINE;
    }
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, x, y, ha, va);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new SubcircuitAttributes(source);
  }

  @Override
  public void configureNewInstance(Instance instance) {

    // For each Instance we create, we add listeners to respond to:
    // 1. Changes to instance attributes, i.e. values in SubcircuitAttributes.
    //   - When FACING changes --> adjust instance ports, bounds, and label.
    //   - When LABEL_LOC changes --> adjust label.
    instance.addAttributeListener();
    // 2. Changes to source circuit static attributes, i.e. values in CircuitAttributes.
    //   - When CIRCUIT_NAME changes, or other things that can similarly impact
    //     the subcircuit appearance --> adjust instance ports, bounds, and label.
    source.getStaticAttributes().addAttributeWeakListener(instance, new SourceListener());
    // 3. Changes to the source's CircuitAppearance
    //   - When appearance changes --> adjust instance ports, bounds, and label.
    source.getAppearance().addCircuitAppearanceWeakListener(instance, new AppearanceListener(instance));
    // Note: For #2 and #3, we are conservative... we don't carefully track
    // whether a specific change to the appearance has any real impact on the
    // layout of the ports or the bounds, instead we recalculate just in case.

    computePortsBoundsAndLabel(instance);
  }

  // Listener case #1
  @Override
  public void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      Debug.printf(0, "SubcircuitFactory.instanceAttributeChanged(FACING) --> computePortsBoundsAndLabel(instance)\n");
      computePortsBoundsAndLabel(instance);
    } else if (attr == StdAttr.LABEL_LOC) {
      configureLabel(instance);
    }
  }

  // Listener case #2 -- this may not be needed at all?
  private class SourceListener implements AttributeListener {
    public void attributeListChanged(AttributeEvent e) { }
    public void attributeValueChanged(AttributeEvent e) {
      @SuppressWarnings("unchecked")
      Object attr = e.getAttribute();
      if (attr == CircuitAttributes.CIRCUIT_NAME
          || attr == CircuitAttributes.CIRCUIT_REVISION
          || attr == CircuitAttributes.CIRCUIT_REVISION_FACING_ATTR // ?
          || attr == CircuitAttributes.CIRCUIT_REVISION_FONT_ATTR // ?
          || attr == CircuitAttributes.CIRCUIT_APPEARANCE) {
        // FIXME: Is CIRCUIT_APPEARANCE sufficient?
        // FIXME: This can all happen once, in the circuit... does not need
        // to be per-instance, right?!?
        // Debug.trace("SubcircuitFactory.instanceAttributeChanged() with static attr " + attr);
        // CircuitTransaction xn = new ChangeAppearanceTransaction();
        // source.getLocker().execute(xn); --> source.getAppearance().recomputeDefaultAppearance();
      }
    }
  }

  // Listener case #3
  private class AppearanceListener implements CircuitAppearanceListener {
    Instance subcircInstance;
    AppearanceListener(Instance subcircInstance) {
      this.subcircInstance = subcircInstance;
    }
    public void circuitAppearanceChanged(Circuit circuit) {
      computePortsBoundsAndLabel(subcircInstance);
      subcircInstance.fireInvalidated();
    }
  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  @Override
  public boolean nominallyContains(Location loc, AttributeSet attrs) {
    if (super.nominallyContains(loc, attrs)) {
      Direction facing = attrs.getValue(StdAttr.FACING);
      Direction defaultFacing = source.getAppearance().getFacing();
      Location query;

      if (facing.equals(defaultFacing)) {
        query = loc;
      } else {
        query = loc.rotate(facing, defaultFacing, 0, 0);
      }

      return source.getAppearance().contains(query);
    } else {
      return false;
    }
  }

  private void drawCircuitRevisionLabel(InstancePainter painter, Bounds bds,
      Direction facing, Direction defaultFacing) {
    AttributeSet staticAttrs = source.getStaticAttributes();
    String revlabel = staticAttrs.getValue(CircuitAttributes.CIRCUIT_REVISION);
    // FIXME: most of this code is just drawing a multi-line string, which
    // probably can be done using GraphicsUtil much more simply.
    if (revlabel != null && !revlabel.equals("")) {
      Direction up =
          staticAttrs.getValue(CircuitAttributes.CIRCUIT_REVISION_FACING_ATTR);
      Font font =
          staticAttrs.getValue(CircuitAttributes.CIRCUIT_REVISION_FONT_ATTR);

      int back = revlabel.indexOf('\\');
      int lines = 1;
      boolean backs = false;
      while (back >= 0 && back <= revlabel.length() - 2) {
        char c = revlabel.charAt(back + 1);
        if (c == 'n')
          lines++;
        else if (c == '\\')
          backs = true;
        back = revlabel.indexOf('\\', back + 2);
      }

      int x = bds.getX() + bds.getWidth() / 2;
      int y = bds.getY() + bds.getHeight() / 2;
      Graphics2D g = (Graphics2D)painter.getGraphics().create();
      try {
        double angle = Math.PI / 2
          - (up.toRadians() - defaultFacing.toRadians())
          - facing.toRadians();
        if (Math.abs(angle) > 0.01) {
          g.rotate(angle, x, y);
        }
        g.setFont(font);
        if (lines == 1 && !backs) {
          GraphicsUtil.drawCenteredText(g, revlabel, x, y);
        } else {
          FontMetrics fm = g.getFontMetrics();
          int height = fm.getHeight();
          y = y - (height * lines - fm.getLeading()) / 2 + fm.getAscent();
          back = revlabel.indexOf('\\');
          while (back >= 0 && back <= revlabel.length() - 2) {
            char c = revlabel.charAt(back + 1);
            if (c == 'n') {
              String line = revlabel.substring(0, back);
              GraphicsUtil.drawText(g, line, x, y,
                  GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
              y += height;
              revlabel = revlabel.substring(back + 2);
              back = revlabel.indexOf('\\');
            } else if (c == '\\') {
              revlabel = revlabel.substring(0, back)
                + revlabel.substring(back + 1);
              back = revlabel.indexOf('\\', back + 1);
            } else {
              back = revlabel.indexOf('\\', back + 2);
            }
          }
          GraphicsUtil.drawText(g, revlabel, x, y, GraphicsUtil.H_CENTER,
              GraphicsUtil.V_BASELINE);
        }
      } finally {
        g.dispose();
      }
    }
  }

  @Override
  public StringGetter getDisplayGetter() {
    return StringUtil.constantGetter(source.getName());
  }

  @Override
  public Object getInstanceFeature(Instance instance, Object key) {
    if (key == MenuExtender.class)
      return new CircuitFeature(instance);
    return super.getInstanceFeature(instance, key);
  }

  @Override
  public String getName() {
    return source.getName();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    Direction defaultFacing = source.getAppearance().getFacing();
    Bounds bds = source.getAppearance().getOffsetBounds();
    return bds.rotate(defaultFacing, facing, 0, 0);
  }

  public Circuit getSubcircuit() {
    return source;
  }

  private CircuitState getSubstate(InstanceState stateInContext) {
    if (stateInContext instanceof InstanceStateImpl) {
      CircuitState cs = ((InstanceStateImpl)stateInContext).getCircuitState();
      Component comp = ((InstanceStateImpl)stateInContext).getComponent();
      return cs.getCircuitSubstateFor(comp);
    } else
      throw new IllegalArgumentException("getSubstate on wrong type " + stateInContext);
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    // don't need attrs, since circuit doesn't use them anyway
    return new CircuitHDLGenerator(ctx, this.source);
  }

  @Override
  public String getHDLNamePrefix(Component comp) {
    String s = "Subcircuit_" + source.getName();
    s = s.replaceAll("[^a-zA-Z0-9]{1,}", "_");
    s = s.replaceAll("^_", "");
    s = s.replaceAll("_$", "");
    return s;
  }

  private void paintBase(InstancePainter painter, Graphics2D g) {
    SubcircuitAttributes attrs = (SubcircuitAttributes) painter.getAttributeSet();
    Direction facing = attrs.getFacing();
    Direction defaultFacing = source.getAppearance().getFacing();
    Location loc = painter.getLocation();
    g.translate(loc.getX(), loc.getY());
    source.getAppearance().paintSubcircuit(painter, g, facing);
    drawCircuitRevisionLabel(painter, getOffsetBounds(attrs), facing, defaultFacing);
    g.translate(-loc.getX(), -loc.getY());
    painter.drawLabel();
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    Color fg = g.getColor();
    int v = fg.getRed() + fg.getGreen() + fg.getBlue();
    Composite oldComposite = null;
    if (v > 50) {
      oldComposite = ((Graphics2D) g).getComposite();
      Composite c = AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
          0.5f);
      ((Graphics2D) g).setComposite(c);
    }
    paintBase(painter, g);
    if (oldComposite != null) {
      ((Graphics2D) g).setComposite(oldComposite);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    paintBase(painter, painter.getGraphics());
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState stateInContext) {
    CircuitState subState = getSubstate(stateInContext);


    SubcircuitAttributes attrs = (SubcircuitAttributes)stateInContext.getAttributeSet();
    Instance[] pins = attrs.getPinInstances();

    // FIXME: why can't we just use the pins from CircuitAppearance, instead of
    // keeping a separate copy stashed away in SubcircuitAttributes?
    // BEGIN SANITY CHECK
    Direction facing = stateInContext.getInstance().getAttributeValue(StdAttr.FACING);
    Map<Location, Instance> portLocs = source.getAppearance().getPortOffsets(facing);
    Instance[] pins2 = new Instance[portLocs.size()];
    int j = -1;
    for (Map.Entry<Location, Instance> portLoc : portLocs.entrySet()) {
      j++;
      pins2[j] = portLoc.getValue();
    }
    if (pins2.length != pins.length) {
      System.out.printf("pin size mismatch: %d vs %d\n", pins2.length, pins.length);
    } else for (int i = 0; i < pins.length; i++) {
      if (pins2[i] != pins[i])
        System.out.printf("pin[%d] mismatch: %s vs %s\n", i, pins2[i], pins[i]);
    }
    // END OF SANITY CHECK

    for (int i = 0; i < pins.length; i++) {
      Instance pin = pins[i];
      InstanceState pinState = subState.dangerouslyGetTransientInstanceState(pin);
      if (Pin.FACTORY.isInputPin(pin)) {
        Value newVal = stateInContext.getPortValue(i);
        Value oldVal = Pin.FACTORY.getValue(pinState);
        if (!newVal.equals(oldVal)) {
          Pin.FACTORY.driveInputPin(pinState, newVal);
          Pin.FACTORY.propagate(pinState);
        }
      } else { // it is output-only
        Value val = pinState.getPortValue(0);
        stateInContext.setPort(i, val, 1);
      }
    }
  }

}
