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

package com.cburch.logisim.std.ext;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class RealTimeClock extends InstanceFactory {

  static final AttributeOption REAL = new AttributeOption("realtime-s", S.getter("rtcRealtimeS"));
  static final AttributeOption SS = new AttributeOption("uptime-s", S.getter("rtcUptimeS"));
  static final AttributeOption MS = new AttributeOption("uptime-ms", S.getter("rtcUptimeMS"));
  static final AttributeOption US = new AttributeOption("uptime-us", S.getter("rtcUptimeUS"));
  static final AttributeOption NS = new AttributeOption("uptime-ns", S.getter("rtcUptimeNS"));
  static final Attribute<AttributeOption> ATTR_SRC = Attributes.forOption(
      "clock", S.getter("rtcClock"), new AttributeOption[] { REAL, SS, MS, US, NS });

  public RealTimeClock() {
    super("RealTimeClock", S.getter("rtcComponent"));
    setAttributes(
        new Attribute[] { StdAttr.EDGE_TRIGGER, StdAttr.WIDTH, ATTR_SRC },
        new Object[] { StdAttr.TRIG_RISING, BitWidth.create(32), REAL });
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIconName("rtc.png");
    setOffsetBounds(Bounds.create(-20, -10, 20, 20));
    setPorts(new Port[] {
      new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH),
      new Port(-10, 10, Port.INPUT, 1)
    });
  }
  
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    instance.fireInvalidated();
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();

    Bounds bds = painter.getNominalBounds();
    double cx = bds.x + bds.width/2.0;
    double cy = bds.y + bds.height/2.0;
    double r = Math.min(bds.width, bds.height)/2.0;

    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.WHITE);
    g.fill(new Ellipse2D.Double(cx-r, cy-r, 2*r, 2*r));
    g.setColor(Color.BLACK);
    g.draw(new Ellipse2D.Double(cx-r, cy-r, 2*r, 2*r));
    g.draw(new Line2D.Double(cx, cy, cx, cy-0.8*r));
    g.draw(new Line2D.Double(cx, cy, cx+0.5*r*3/5, cy-0.5*r*4/5));
    GraphicsUtil.switchToWidth(g, 1);
    g.setColor(Color.RED);
    g.draw(new Line2D.Double(cx, cy, cx-0.8*r*12/13, cy+0.8*r*2/13));
    g.setColor(Color.BLACK);

    painter.drawPorts();
    painter.drawClock(1, Direction.NORTH);
  }

  @Override
  public void propagate(InstanceState iState) {

    State data = (State) iState.getDataAsCustom();
    if (data == null) {
      data = new State();
      iState.setData(data);
    }

    AttributeOption trigger = iState.getAttributeValue(StdAttr.EDGE_TRIGGER);
    Value clock = iState.getPortValue(1);
    Value lastClock = data.setLastClock(clock);
    boolean go;
    if (trigger == StdAttr.TRIG_FALLING)
      go = lastClock == Value.TRUE && clock == Value.FALSE;
    else
      go = lastClock == Value.FALSE && clock == Value.TRUE;
    if (!go)
      return;

    BitWidth bw = iState.getAttributeValue(StdAttr.WIDTH);

    AttributeOption src = iState.getAttributeValue(ATTR_SRC);

    long now = System.nanoTime();
    int t = 0;
    if (src == REAL)
      t = (int)(System.currentTimeMillis()/1000);
    else if (src == SS)
      t = (int)((now - data.startNS) / 1_000_000_000L);
    else if (src == MS)
      t = (int)((now - data.startNS) / 1_000_000L);
    else if (src == US)
      t = (int)((now - data.startNS) / 1_000L);
    else if (src == NS)
      t = (int)((now - data.startNS) / 1L);
    
    iState.setPort(0, Value.createKnown(bw, t), 1);
  }

  private static class State implements ComponentData {

    Value lastClock = Value.UNKNOWN;
    long startNS; // ns since sometime

    public State() {
      startNS = System.nanoTime();
    }

    public State(State other) {
      lastClock = other.lastClock;
      startNS = other.startNS;
    }

    public Value setLastClock(Value newClock) { // no need for sync here
      Value ret = lastClock;
      lastClock = newClock;
      return ret;
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }

  }

}
