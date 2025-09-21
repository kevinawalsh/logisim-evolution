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
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;

public class HttpIn extends InstanceFactory {

  // TODO: expose all outputs to logging?
  // public static class Logger extends InstanceLogger { ... }

  public static final Attribute<String> ATTR_URL =
      Attributes.forString("url", S.getter("httpInputURL"));

  // polling mode:
  //   Outputs reflect most recent response data.
  //   There is no clock, the URL is polled automatically on some schedule
  //   whenever the component is enabled.
  // clocked mode:
  //   Outputs reflect most recent response data, but only update on clock
  //   edges. The URL is polled no faster than the clock determines.
  
  static final AttributeOption POLLING =
      new AttributeOption("polling", S.getter("httpInputPolling"));
  static final AttributeOption CLOCKED =
      new AttributeOption("clocked", S.getter("httpInputClocked"));
  static final Attribute<AttributeOption> ATTR_WHEN =
      Attributes.forOption("trigger", S.getter("httpInputTrigger"),
          new AttributeOption[] { POLLING, CLOCKED });

  public static final Attribute<HttpInputFormat> ATTR_FORMAT =
      new FormatAttribute("format", S.getter("httpInputFormat"));

  public static final Attribute<Integer> ATTR_DELAY =
    Attributes.forIntegerRange("delay", S.getter("httpInputDelay"), 100, 60000);

  public HttpIn() {
    super("HttpIn", S.getter("httpInputComponent"));
    setAttributes(new Attribute[] {
      ATTR_URL, ATTR_FORMAT, ATTR_DELAY,
      ATTR_WHEN, StdAttr.EDGE_TRIGGER,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] {
        "http://example.com/data", new HttpInputFormat("%8d %8d %8d"), Integer.valueOf(500),
        POLLING, StdAttr.TRIG_RISING,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setIconName("http-in.png");
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    recomputeLabelTextFieldPosition(instance);
  }

  private void recomputeLabelTextFieldPosition(Instance instance) {
    AttributeOption when = instance.getAttributeValue(ATTR_WHEN);
    if (when == CLOCKED)
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_RIGHT | Instance.AVOID_LEFT | Instance.AVOID_BOTTOM);
    else
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_RIGHT | Instance.AVOID_LEFT);
  }

  private void updatePorts(Instance instance) {
    AttributeOption when = instance.getAttributeValue(ATTR_WHEN);
    HttpInputFormat m = instance.getAttributeValue(ATTR_FORMAT);
    int[] widths = m.getWidths();
    int n = widths.length + (when == CLOCKED ? 4 : 1);
    Port[] ps = new Port[n];
    int idx = 0;
    if (when == CLOCKED) {
      ps[0] = new Port(-30, 10, Port.INPUT, 1); // clock
      ps[0].setToolTip(S.getter("httpInputClockTip"));
      ps[1] = new Port(-40, 10, Port.INPUT, 1); // read enable
      ps[1].setToolTip(S.getter("httpInputReadEnableTip"));
      ps[2] = new Port(-10, 10, Port.OUTPUT, 1); // data ready
      ps[2].setToolTip(S.getter("httpInputAvailableTip"));
      idx = 3;
    }
    ps[idx] = new Port(-80, 0, Port.INPUT, 1); // fetch enable
    ps[idx].setToolTip(S.getter("httpInputFetchEnableTip")); // fetch enable
    idx++;
    for (int i = 0; i < widths.length; i++) {
      ps[idx+i] = new Port(0, -10 * i, Port.OUTPUT, widths[i]);
      ps[idx+i].setToolTip(S.getter("httpInputDataTip", ""+(i + 1)));
    }
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    HttpInputFormat m = attrs.getValue(ATTR_FORMAT);
    int p = Math.max(5, m.numValues());
    return Bounds.create(-80, -(10 * p), 80, 10 + 10 * p);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      recomputeLabelTextFieldPosition(instance);
    } else if (attr == ATTR_WHEN) {
      updatePorts(instance);
      recomputeLabelTextFieldPosition(instance);
      instance.fireInvalidated(); // recompute using clock signal
    } else if (attr == ATTR_FORMAT) {
      updatePorts(instance);
      instance.recomputeBounds();
      recomputeLabelTextFieldPosition(instance);
      instance.fireInvalidated();
    } else {
      // propagate updates State queue length, delimiter, format, etc.
      instance.fireInvalidated();
    }
  }

  static final Color ON_COLOR = Color.GREEN;
  static final Color OFF_COLOR = Color.LIGHT_GRAY;

  @Override
  public void paintInstance(InstancePainter painter) {
    painter.drawBounds();

    Bounds bds = painter.getNominalBounds();
    double cx = bds.x + bds.width/2.0;
    double cy = bds.y + bds.height/2.0;
      
    Graphics2D g = (Graphics2D)painter.getGraphics();

    g.setColor(Color.DARK_GRAY);
    g.draw(new Ellipse2D.Double(cx-10, cy-10, 20, 20));
    g.draw(new Ellipse2D.Double(cx-5, cy-10, 10, 20));
    g.draw(new Line2D.Double(cx, cy-10, cx, cy+10));
    g.draw(new Line2D.Double(cx-7.8, cy-4, cx+7.8, cy-4));
    g.draw(new Line2D.Double(cx-10, cy, cx+10, cy));
    g.draw(new Line2D.Double(cx-7.8, cy+4, cx+7.8, cy+4));

    if (painter.getShowState()) {
      State state = (State)painter.getDataAsCustom();
      // Connection status LED
      Color c = (state != null && state.fetching ? ON_COLOR : OFF_COLOR);
      g.setColor(c);
      g.fillRect(bds.x+5, bds.y+bds.height-15, 12, 8);
      g.setColor(Color.GRAY);
      g.drawRect(bds.x+5, bds.y+bds.height-15, 12, 8);
      // TODO: Maybe also show a blinking activity light?

      g.setColor(Color.BLACK);
      if (state != null) {
        String status = state.status;
        if (status != null)
          GraphicsUtil.drawText(g, state.status, bds.x+5, bds.y+2, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
        long t = state.timestamp;
        if (t != 0) {
          String hhmmss = DateTimeFormatter.ofPattern("HH:mm:ss")
              .format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()));
          GraphicsUtil.drawText(g, hhmmss, bds.x+bds.width-5, bds.y+bds.height-2, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BOTTOM);
        }
      }
    }
    
    painter.drawLabel();
    AttributeOption when = painter.getAttributeValue(ATTR_WHEN);
    if (when == CLOCKED)
      painter.drawClock(1, Direction.NORTH);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState circState) {
    State state = getState(circState);

    int idx = 0;
    Value[] vals = null;

    boolean read;
    AttributeOption when = circState.getAttributeValue(ATTR_WHEN);
    if (when == CLOCKED) {
      idx = 3;
      Value clkenable = circState.getPortValue(1);
      if (clkenable == Value.FALSE) {
        read = false;
      } else {
        AttributeOption trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
        Value clock = circState.getPortValue(0);
        Value lastClock = state.setLastClock(clock);
        if (trigger == StdAttr.TRIG_FALLING) {
          read = lastClock == Value.TRUE && clock == Value.FALSE;
        } else {
          read = lastClock == Value.FALSE && clock == Value.TRUE;
        }
      }
      if (read)
        vals = state.getValues(false); // null if stale, else marks as stale
      boolean ready = state.hasFreshValue();
      circState.setPort(2, ready ? Value.TRUE : Value.FALSE, 1);
    } else { // POLLING
      read = true;
      vals = state.getValues(true); // ok to use stale value
    }

    Value fetchEnable = circState.getPortValue(idx);
    idx++;
    state.enableFetching(fetchEnable == Value.TRUE);

    if (!read)
      return; // don't update data ports

    if (vals != null) {
      for (int i = 0; i < vals.length; i++) {
        circState.setPort(idx+i, vals[i], 1);
      }
    } else {
      // set all output ports to unknown
      int i = -1;
      for (Port p : circState.getInstance().getPorts()) {
        i++;
        if (i < idx)
          continue;
        int w = p.getFixedBitWidth();
        Value v = Value.createUnknown(BitWidth.create(w));
        circState.setPort(i, v, 1);
      }
    }
  }

  private static State getState(InstanceState circState) {
    State state = (State) circState.getDataAsCustom();
    if (state == null) {
      state = new State(circState);
      circState.setData(state);
    } else {
      state.updateBinding(circState);
    }
    return state;
  }

  public static class State implements ComponentData.WithLifetimeTracking {

    private Value lastClock = Value.UNKNOWN;

    private volatile boolean fetching;
    private volatile String status;
    private volatile long timestamp;

    private HttpFetchManager.Worker worker;
    private HttpInputFormat format;
    private Value[] lastValues = null;

    public State(InstanceState iState) {
      AttributeSet attrs = iState.getAttributeSet();
      String url = iState.getAttributeValue(ATTR_URL);
      boolean async = iState.getAttributeValue(ATTR_WHEN) == POLLING;
      int delay = iState.getAttributeValue(ATTR_DELAY);
      format = iState.getAttributeValue(ATTR_FORMAT);

      Instance i = iState.getInstance();
      CircuitState cs = iState.getCircuitState();
      worker = HttpFetchManager.makeWorker(url, async, delay, i, cs);
    }
    
    private State(State other) {
      other.worker.sync.lock();
      try {
        format = new HttpInputFormat(other.format);
        worker = HttpFetchManager.makeWorker(other.worker);
        lastClock = other.lastClock;
        lastValues = (other.lastValues == null ? null : other.lastValues.clone());
      } finally {
        other.worker.sync.unlock();
      }
    }
    
    void enableFetching(boolean enable) {
      worker.enableFetching(enable);
    }

    Value[] getValues(boolean async) {
      worker.sync.lock();
      try {
        fetching = worker.fetching;
        timestamp = worker.timestamp;
        status = worker.status;
        if (async) {
          if (lastValues == null || worker.fresh) {
            String resp = worker.getResponse();
            if (resp != null) {
              lastValues = format.parseResponse(resp);
            }
          }
          return lastValues;
        } else { // sync
          if (worker.fresh) {
            String resp = worker.getResponse();
            if (resp != null) {
              lastValues = format.parseResponse(resp);
              return lastValues;
            }
          }
          return lastValues;
        }
      } finally {
        worker.sync.unlock();
      }
    }

    boolean hasFreshValue() {
      worker.sync.lock();
      try {
        return worker.fresh;
      } finally {
        worker.sync.unlock();
      }
    }

    void updateBinding(InstanceState iState) {
      AttributeSet attrs = iState.getAttributeSet();
      String url = iState.getAttributeValue(ATTR_URL);
      boolean async = iState.getAttributeValue(ATTR_WHEN) == POLLING;
      int delay = iState.getAttributeValue(ATTR_DELAY);
      HttpInputFormat format = iState.getAttributeValue(ATTR_FORMAT);

      worker.sync.lock();
      try {
        
        Instance i = iState.getInstance();
        CircuitState cs = iState.getCircuitState();
        HttpFetchManager.bind(worker, i, cs);

        worker.setParams(url, async, delay);

        if (this.format == null || !format.sameAs(this.format)) {
          this.format = format;
          lastValues = null;
        }

      } finally {
        worker.sync.unlock();
      }

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
  
    @Override
    public void simulationCleanup(CircuitState cs, Component comp) {
      HttpFetchManager.kill(worker);
    }
    
    @Override
    public void simulationActivating(CircuitState cs, Component comp) {
      worker.setActive(true);
    }

    @Override
    public void simulationDeactivating(CircuitState cs, Component comp) {
      worker.setActive(false);
    }

    // Default behavior is fine, just do simulationCleanup()
    // @Override
    // public void simulationReset(CircuitState cs, Component comp) {
    //   // worker.enableFetching(false); // propagate will re-enable, probably...
    //   HttpFetchManager.kill(worker);
    //   return true; // okay to delete this State
    // }

  }

  private static class FormatAttribute extends Attribute<HttpInputFormat> {
    public FormatAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public String toStandardString(HttpInputFormat m) {
      return m.getFormatString();
    }

    @Override
    public String toDisplayString(HttpInputFormat format) {
      if (format.isRaw())
        return "one byte";
      else
        return format.getFormatString().replace("\n", "\\n");
    }

    @Override
    public HttpInputFormat parse(String value) {
      HttpInputFormat m = new HttpInputFormat(value);
      if (!m.isValid())
        Errors.title(S.get("httpInputFormatErrorTitle")).show(m.errorMessage());
      return m;
    }
    
    @Override
    public java.awt.Component getCellEditor(Window source, HttpInputFormat fmt) {
      return new HttpInputFormatDialog(source, fmt);
    }

  }

}
