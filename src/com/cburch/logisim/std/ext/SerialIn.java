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

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;

import com.fazecast.jSerialComm.SerialPort;

import com.cburch.logisim.circuit.CircuitState;
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
// import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.UniquelyNamedThread;

public class SerialIn extends InstanceFactory {

  // TODO: expose all outputs to logging?
  // public static class Logger extends InstanceLogger { ... }

  public static class Poker extends InstancePoker {
    Rectangle r; // button position
    boolean isOpening;
    boolean isClosing;

    @Override
    public void mousePressed(InstanceState circState, MouseEvent e) {
      if (r == null || !r.contains(e.getX(), e.getY()))
        return;
      State s = getState(circState);
      if (isOpening)
        s.open();
      else if (isClosing)
        s.close();
    }

    @Override
    public void paint(InstancePainter painter) {
      State state = getState(painter);
      Graphics2D g = (Graphics2D)painter.getGraphics();
      Bounds bds = painter.getNominalBounds();
      isOpening = !state.isOpen;
      isClosing = !isOpening && !state.isClosing;
      String s = (isOpening ? S.get("serialInputOpen") : S.get("serialInputClose"));
      int x = bds.x + bds.width - 7;
      int y = bds.y + bds.height - 5;
      if (!isOpening && !isClosing) {
        g.setColor(Color.GRAY);
        s = S.get("serialInputWait");
      }
      r = GraphicsUtil.getTextBounds(g, s, x, y, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BOTTOM);
      g.setColor(Color.WHITE);
      g.fillRect(r.x-3, r.y+2, r.width + 6, r.height-2);
      g.setColor(Color.DARK_GRAY);
      g.drawRect(r.x-3, r.y+2, r.width + 6, r.height-2);
      GraphicsUtil.drawText(g, s, x, y, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BOTTOM);
    }

  }

  public static final Attribute<String> ATTR_PORT =
      new SerialPortPathAttribute("port", S.getter("serialInputPort"));

  static final AttributeOption MODE_8N1 = new AttributeOption("8n1", S.unlocalized("8n1"));
  static final AttributeOption MODE_8N2 = new AttributeOption("8n2", S.unlocalized("8n2"));
  static final AttributeOption MODE_8E1 = new AttributeOption("8e1", S.unlocalized("8e1"));
  static final AttributeOption MODE_8E2 = new AttributeOption("8e2", S.unlocalized("8e2"));
  static final AttributeOption MODE_8O1 = new AttributeOption("8o1", S.unlocalized("8o1"));
  static final AttributeOption MODE_8O2 = new AttributeOption("8o2", S.unlocalized("8o2"));
  static final AttributeOption MODE_7N1 = new AttributeOption("7n1", S.unlocalized("7n1"));
  static final AttributeOption MODE_7N2 = new AttributeOption("7n2", S.unlocalized("7n2"));
  static final AttributeOption MODE_7E1 = new AttributeOption("7e1", S.unlocalized("7e1"));
  static final AttributeOption MODE_7E2 = new AttributeOption("7e2", S.unlocalized("7e2"));
  static final AttributeOption MODE_7O1 = new AttributeOption("7o1", S.unlocalized("7o1"));
  static final AttributeOption MODE_7O2 = new AttributeOption("7o2", S.unlocalized("7o2"));
  static final Attribute<AttributeOption> ATTR_MODE =
      Attributes.forOption("mode", S.getter("ioSerialMode"),
          new AttributeOption[] {
            MODE_8N1, MODE_8N2, MODE_8E1, MODE_8E2, MODE_8O1, MODE_8O2,
            MODE_7N1, MODE_7N2, MODE_7E1, MODE_7E2, MODE_7O1, MODE_7O2 });

  // async mode:
  //   Outputs reflect most recent serial data, but are UNKNOWN initially.
  //   There is no clock. If serial data arrives very quickly, simulator could
  //   miss some data. Data is not queued as it is read from serial port, only
  //   the most recent data is kept.
  // sync mode:
  //   Outputs reflect most recent serial data, but only update on a clock
  //   trigger. If clock is not triggered, outputs stay constant, and data
  //   is queued. Data is queued as it is read from serial port, one set of
  //   values is passed to the simulator for each clock edge.
  static final AttributeOption CLOCKING_ASYNCHRONOUS =
      new AttributeOption("asynchronous", S.getter("serialInputAsynchronous"));
  static final AttributeOption CLOCKING_SYNCHRONOUS =
      new AttributeOption("synchronous", S.getter("serialInputSynchronous"));
  static final Attribute<AttributeOption> ATTR_CLOCKING =
      Attributes.forOption("clocking", S.getter("serialInputClocking"),
          new AttributeOption[] { CLOCKING_ASYNCHRONOUS, CLOCKING_SYNCHRONOUS });

  // max size of queued values, used only for SYNC case
  // (in async case, the queue size is effectively just 1)
  private static Attribute<Integer> ATTR_QUEUE =
      Attributes.forIntegerRange("queue", S.getter("serialInputQueueSize"), 1, 32*1024);
  
  // (in async case, the queue size is effectively just 1)
  private static Attribute<Integer> ATTR_BAUD =
      Attributes.forIntegerRange("baud", S.getter("serialInputBaud"), 110, 256000);
 
  public static final Attribute<SerialInputFormat> ATTR_FORMAT =
      new FormatAttribute("format", S.getter("serialInputFormat"));


  public SerialIn() {
    super("SerialIn", S.getter("serialInputComponent"));
    setAttributes(new Attribute[] { /* StdAttr.FACING, */
      ATTR_MODE, ATTR_BAUD, ATTR_FORMAT, ATTR_PORT,
      ATTR_CLOCKING, StdAttr.EDGE_TRIGGER, ATTR_QUEUE,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] { /* Direction.EAST, */
        MODE_8N1, 115200, new SerialInputFormat("[\\n\\r]", "%8d %8d %8d"), "/dev/ttyUSB0",
        CLOCKING_ASYNCHRONOUS, StdAttr.TRIG_RISING, 1024,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setIconName("serial-in.png");
    // setFacingAttribute(StdAttr.FACING);
    // setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    setInstancePoker(Poker.class);
    // setInstanceLogger(Logger.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    recomputeLabelTextFieldPosition(instance);
  }

  private void recomputeLabelTextFieldPosition(Instance instance) {
    AttributeOption clocking = instance.getAttributeValue(ATTR_CLOCKING);
    if (clocking == CLOCKING_SYNCHRONOUS)
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_RIGHT | Instance.AVOID_BOTTOM);
    else
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_RIGHT);
  }

  private void updatePorts(Instance instance) {
    AttributeOption clocking = instance.getAttributeValue(ATTR_CLOCKING);
    SerialInputFormat m = instance.getAttributeValue(ATTR_FORMAT);
    int[] widths = m.getWidths();
    int n = widths.length + (clocking == CLOCKING_SYNCHRONOUS ? 3 : 0);
    Port[] ps = new Port[n];
    int idx = 0;
    if (clocking == CLOCKING_SYNCHRONOUS) {
      ps[0] = new Port(-30, 10, Port.INPUT, 1); // clock
      ps[0].setToolTip(S.getter("serialInputClockTip"));
      ps[1] = new Port(-40, 10, Port.INPUT, 1); // read enable
      ps[1].setToolTip(S.getter("serialInputReadEnableTip"));
      ps[2] = new Port(-10, 10, Port.OUTPUT, 1); // data ready
      ps[2].setToolTip(S.getter("serialInputAvailableTip"));
      idx = 3;
    }
    for (int i = 0; i < widths.length; i++) {
      ps[idx+i] = new Port(0, -10 * i, Port.OUTPUT, widths[i]);
      ps[idx+i].setToolTip(S.getter("serialInputDataTip", ""+(i + 1)));
    }
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    SerialInputFormat m = attrs.getValue(ATTR_FORMAT);
    int p = Math.max(5, m.numValues());
    return Bounds.create(-80, -(10 * p), 80, 10 + 10 * p);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      recomputeLabelTextFieldPosition(instance);
    } else if (attr == ATTR_CLOCKING) {
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
    int cx = bds.x + bds.width/2;
    int cy = bds.y + bds.height/2;

    drawUsbLogo((Graphics2D)painter.getGraphics(), cx-25, cy-12, 50, 24, Color.BLACK);

    if (painter.getShowState()) {
      State state = (State)painter.getDataFor();
      // Connection status LED
      Color c = (state != null && state.isOpen ? ON_COLOR : OFF_COLOR);
      Graphics2D g = (Graphics2D)painter.getGraphics();
      g.setColor(c);
      g.fillRect(bds.x+5, bds.y+bds.height-15, 12, 8);
      g.setColor(Color.GRAY);
      g.drawRect(bds.x+5, bds.y+bds.height-15, 12, 8);
      // TODO: Maybe also show a blinking activity light?

      g.setColor(Color.BLACK);
      if (state != null) {
        String status = state.status;
        if (status != null)
            GraphicsUtil.drawText(g, status, bds.x+5, bds.y+2, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
        long t = state.timestamp;
        if (t != 0) {
          String hhmmss = DateTimeFormatter.ofPattern("HH:mm:ss")
              .format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()));
          GraphicsUtil.drawText(g, hhmmss, bds.x+bds.width-5, bds.y+bds.height-2, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BOTTOM);
        }

        }
    }
    
    painter.drawLabel();
    AttributeOption clocking = painter.getAttributeValue(ATTR_CLOCKING);
    if (clocking == CLOCKING_SYNCHRONOUS)
      painter.drawClock(1, Direction.NORTH);
    painter.drawPorts();
  }

  static void drawUsbLogo(Graphics2D g, int x, int y, int w, int h, Color color) {

    double s = Math.max(3.0, Math.min(w/10, h/5));
    double r = s/2;

    g.setColor(color);
    GraphicsUtil.switchToWidth(g, 1.5f);

    // root
    double xDot = x + w - r*1.35, yDot = y + h/2;
    g.fill(new Ellipse2D.Double(xDot-r*1.35, yDot-r*1.35, s*1.35, s*1.35));

    // center triangle
    double xTri = x + r, yTri = yDot;
    Path2D tri = new Path2D.Double();
    tri.moveTo(xTri-r, yTri);
    tri.lineTo(xTri+r, yTri-r*1.414);
    tri.lineTo(xTri+r, yTri+r*1.414);
    tri.closePath();
    g.fill(tri);

    // upper square
    double xSqr = x + w/4, ySqr = y + r;
    g.fill(new Rectangle2D.Double(xSqr-r, ySqr-r, s, s));

    // lower circle
    double xCir = x + 2*w/5, yCir = y + h - r;
    g.fill(new Ellipse2D.Double(xCir-r, yCir-r, s, s));

    // stems
    g.draw(new Line2D.Double(xDot, yDot, xTri, yTri));
    drawCurve(g, xDot, yDot, xSqr+3.5*s, yDot, xSqr+2*s, ySqr, xSqr, ySqr, r);
    drawCurve(g, xDot, yDot, xCir+3.5*s, yDot, xCir+2*s, yCir, xCir, yCir, r);

    GraphicsUtil.switchToWidth(g, 1);
  }

  static void drawCurve(Graphics2D g,
      double xA, double yA,
      double xB, double yB,
      double xC, double yC,
      double xD, double yD, double r) {
    double dx, dy, s;

    // r pixels from B, towards A
    dx = xA - xB; dy = yA - yB;
    s = r/Math.hypot(dx, dy);
    double xBA = xB + dx * s, yBA = yB + dy * s;
    
    // r pixels from B, towards C
    dx = xC - xB; dy = yC - yB;
    s = r/Math.hypot(dx, dy);
    double xBC = xB + dx * s, yBC = yB + dy * s;

    // r pixels from C, towards B
    dx = xB - xC; dy = yB - yC;
    s = r/Math.hypot(dx, dy);
    double xCB = xC + dx * s, yCB = yC + dy * s;
    
    // r pixels from C, towards D
    dx = xD - xC; dy = yD - yC;
    s = r/Math.hypot(dx, dy);
    double xCD = xC + dx * s, yCD = yC + dy * s;

    Path2D curve = new Path2D.Double();
    curve.moveTo(xA, yA);
    curve.lineTo(xBA, yBA);
    curve.quadTo(xB, yB, xBC, yBC);
    curve.lineTo(xCB, yCB);
    curve.quadTo(xC, yC, xCD, yCD);
    curve.lineTo(xD, yD);
    g.draw(curve);
  }

  @Override
  public void propagate(InstanceState circState) {
    State state = getState(circState);

    int idx = 0;
    Value[] vals = null;

    AttributeOption clocking = circState.getAttributeValue(ATTR_CLOCKING);
    if (clocking == CLOCKING_SYNCHRONOUS) {
      idx = 3;
      Value enable = circState.getPortValue(1);
      boolean go;
      if (enable == Value.FALSE) {
        go = false;
      } else {
        AttributeOption trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
        Value clock = circState.getPortValue(0);
        Value lastClock = state.setLastClock(clock);
        if (trigger == StdAttr.TRIG_FALLING) {
          go = lastClock == Value.TRUE && clock == Value.FALSE;
        } else {
          go = lastClock == Value.FALSE && clock == Value.TRUE;
        }
      }
      // if (!go)
      //   return; // do not update data ports or ready port
      if (go)
        vals = state.getValues(false);
      boolean ready = state.hasQueuedValue();
      circState.setPort(2, ready ? Value.TRUE : Value.FALSE, 1);
      if (!go)
        return; // update ready port, but not data ports
    } else { // CLOCKING_ASYNCHRONOUS
      vals = state.getValues(true);
    }

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
    State state = (State) circState.getDataFor();
    if (state == null) {
      state = new State(circState);
      circState.setData(state);
    } else {
      state.updateBinding(circState);
    }
    return state;
  }

  // This is called (sometimes) by CircuitState when simulation is no longer valid
  public static void kill(State state) {
    System.out.println("serial... CircuitState notify of kill");
    if (state == null)
      return;
    state.close();
  }

  // This is called (sometimes) by CircuitState when simulation is reset
  // This could be static, to match kill, or vice-versa. But whatever.
  public boolean reset(CircuitState cs, Instance instance) {
    System.out.println("serial... CircuitState notify of reset");
    State state = (State)instance.getDataFor(cs);
    if (state == null)
      return true; // remove State from cs (but it was already null?)
    state.close();
    return false; // keep state, it can be re-opened
  }

  public static class State implements ComponentData {

    private Value lastClock = Value.UNKNOWN;

    private volatile String status = "ready";
    private volatile long timestamp;

    private AttributeOption mode;
    private boolean async;
    private int baud, qlen;
    private String path;
    private SerialInputFormat format;
    
    private Instance instance;
    private CircuitState circState;
    // private InstanceComponent ic;
    
    private volatile boolean isOpen = false; // accesed by mouse handler, but non-critical
    private volatile boolean isClosing = false; // accessed by mouse handler, but non-critical
    private volatile SerialPort port;
    private Thread worker;

    private ArrayDeque<Value[]> q = new ArrayDeque<>();
    private Value[] lastAsync = null;

    // The data lock protects against concurrent access to
    // format, lastAsync, q, async, and instance.
    // None of these variables involve access to the port.
    // This lock is to be held only for short durations and
    // holders should not block or sleep.
    private ReentrantLock data = new ReentrantLock();
   
    // The critical lock protects against concurrent access
    // to port, worker, baud, mode, path, circState, and against concurrent
    // changes to isOpen and isClosed. All of these
    // variables involve the underlying port, and threads
    // holding this lock sometimes need to block.
    private ReentrantLock crit = new ReentrantLock();
    private Condition workerDead = crit.newCondition();

    public State(InstanceState iState) {
      updateBinding(iState);
    }
    
    private State(State other) {
      other.crit.lock();
      try {
        // System.out.println("copy state from other...");
        lastClock = other.lastClock;
        mode = other.mode;
        async = other.async;
        baud = other.baud;
        path = other.path; // should not open both at same time...
        circState = null; // FIXME: duplicateForNewSimulation should take the new circuitstate as param...
      } finally {
        other.crit.unlock();
      }
      other.data.lock();
      try {
        qlen = other.qlen;
        format = new SerialInputFormat(other.format);
        instance = null; // don't know which instance this will be for? 
      } finally {
        other.data.unlock();
      }
    }

    Value[] getValues(boolean mostRecent) {
      data.lock();
      try {
        if (mostRecent)
          return lastAsync;
        else
          return q.pollFirst();
      } finally {
        data.unlock();
      }
    }

    boolean hasQueuedValue() {
      data.lock();
      try {
        return !q.isEmpty();
      } finally {
        data.unlock();
      }
    }

    void updateBinding(InstanceState iState) {
      AttributeSet attrs = iState.getAttributeSet();
      AttributeOption mode = iState.getAttributeValue(ATTR_MODE);
      String path = iState.getAttributeValue(ATTR_PORT);
      boolean async = iState.getAttributeValue(ATTR_CLOCKING) == CLOCKING_ASYNCHRONOUS;
      int baud = iState.getAttributeValue(ATTR_BAUD);
      SerialInputFormat format = iState.getAttributeValue(ATTR_FORMAT);
      int qlen = iState.getAttributeValue(ATTR_QUEUE);

      // check non-critical things first
      data.lock();
      try {
        // update instance
        if (instance != null) {
          // are we still attached to the same instance?
          Instance i = iState.getInstance();
          if (i == null) {
            System.err.println("Trouble... instance missing?!?!?");
            instance = null;
          } else if (i != instance) {
            // System.err.println("Trouble ahead: instance mismatch?!?!?");
            // System.out.println("old instance: " + instance);
            // System.out.println("old ic: " + instance.getComponent());
            // System.out.println("new instance: " + i);
            // System.out.println("new ic: " + i.getComponent());
            instance = i;
          }
        } else {
          instance = iState.getInstance();
        }

        if (qlen != this.qlen) {
          // System.out.println("qlen changed");
          this.qlen = qlen;
          while (q.size() > qlen)
            q.removeFirst();
        }
        if (this.format == null || !format.sameAs(this.format)) {
          // System.out.println("format changed");
          this.format = format;
          q.clear();
          lastAsync = null;
        }
        if (async != this.async) {
          // System.out.println("async changed");
          this.async = async;
        }
      } finally {
        data.unlock();
      }

      // check critical things second
      crit.lock();
      try {
        CircuitState cs = iState.getCircuitState();
        if (this.circState != null) {
          // cs really shouln't change, right?
          if (cs != this.circState) {
            System.err.println("huh... cs mismatch in SerialIn!!!");
            System.err.println("old: " + this.circState);
            System.err.println("new: " + cs);
          }
          this.circState = cs;
          // FIXME: if this situation ever happens, we also need to update SerialPortmanager 
        } else if (cs == null) {
          System.out.println("huh.. old and new cs both null!!!");
        } else {
          this.circState = cs;
          // port can't be open yet, so this is fine
        }

        // InstanceComponent ic = (InstanceComponent)iState.getInstance().getComponent();
        // if (this.ic != null) {
        //   // cs really shouln't change, right?
        //   if (ic != this.ic) {
        //     System.out.println("huh.. ic mismatch!!!");
        //     System.out.println("old: " + this.ic);
        //     System.out.println("new: " + ic);
        //   }
        //   this.ic = ic;
        // } else if (ic == null) {
        //   System.out.println("huh.. new ic is null!!!");
        // } else {
        //   this.ic = ic;
        // }

        // check port parameters
        if (baud != this.baud || mode != this.mode) {
          // System.out.println("baud/mode changed");
          this.baud = baud;
          this.mode = mode;
          if (port != null) {
            // close and re-open?
            port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
          }
        }
        // update path
        if (!path.equals(this.path)) {
          // System.out.println("path changed");
          close();
          this.path = path;
        }
      } finally {
        crit.unlock();
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

    private void run() {
      try {
        SerialInputFormat.DataBuffer buf = new SerialInputFormat.DataBuffer(1024);
        int w = 0; // next position to write into
        int r = 0; // next position to read
        int n = 0; // number of bytes 
        boolean first = true;
        while (isOpen && !isClosing && !Thread.currentThread().isInterrupted()) { // volatile
          // ensure space in buffer
          if (n == buf.cap) {
            r = (r + 1) % buf.cap;
            n--;
          }
          // read from serial
          int ok = port.readBytes(buf.buf, 1, w); // volatile; blocking
          if (ok < 0)
            break;
          byte b = buf.buf[w];
          w = (w + 1) % buf.cap;
          n++;
          // process buffer, if appropriate
          data.lockInterruptibly(); // blocking; access format, qlen, instance
          try {
            Value[] vals = null;
            if (!format.hasDelimiters()) {
              // no delimiter, see if we can match any of current buffer
              vals = buf.parseRecord(format, r, n);
              if (vals == null)
                  continue; // no match, but no err since this was best effot
              // drop the matching part
              r = buf.pos;
              n = buf.len;
            } else if (!format.isDelim(b)) {
              continue;
            } else if (first) {
              // skip first record, it's often truncated garbage
              first = false;
              w = r = n = 0;
            } else {
              vals = buf.parseRecord(format, r, n-1);
              // drop record (entire buffer)
              w = r = n = 0;
              // note: we drop record even if it wasn't matched,
              // that's a case of bad input anyway
            }
            // TODO: provide circuit access to valueOutOfRange errors?
            if (vals != null) {
              // match, send to circuit
              boolean changed = lastAsync == null || vals.length != lastAsync.length;
              for (int i = 0; !changed && i < vals.length; i++)
                changed = !vals[i].equals(lastAsync[i]);
              while (q.size() >= qlen)
                q.removeFirst();
              q.addLast(vals);
              if (changed)
                lastAsync = vals;
              timestamp = System.currentTimeMillis();
              status = "ok";
              if ((async && changed) || (!async && q.size() == 1))
                fire();
            } else {
              status = "bad data";
              timestamp = System.currentTimeMillis();
              fire();
            }
          } finally {
            data.unlock();
          }
        }
      } catch (InterruptedException e) {
        // System.out.println("worker interrupted");
        // do nothing
      } catch (Exception e) {
        System.err.println("serial port worker crashed");
        e.printStackTrace();
      } finally {
        data.lock(); 
        try {
          q.clear();
          lastAsync = null;
          fire();
        } finally {
          data.unlock();
        }
        crit.lock();
        try {
          worker = null;
          workerDead.signalAll();
          close();
        } finally {
          crit.unlock();
        }
      }
      // System.out.println("worker is done");
    }

    void close() {
      crit.lock();
      try {
        if (!isOpen || isClosing)
          return;
        isClosing = true;
        isOpen = false;
        status = "closing";
        // note: worker may be running concurrently
        if (port != null) {
          SerialPortManager.closePort(port);
        }
        if (worker != null) {
          // worker may be blocked:
          //  (a) in port.readBytes()
          //  (b) in data.lockInterruptibly() [within the run loop]
          //  (c) in data.lock() [ in the cleanup code, to clear the queue ]
          //  (d) in crit.lock() [ in the cleanup code, to clear worker and close ]
          // worker may be making progress:
          //  - inside one of two data-lock critical sections
          //  - elsewhere
          try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            for (int i = 0; i < 3 && worker != null; i++) {
              // System.out.println("interrupting worker");
              worker.interrupt(); // unblocks (a) and (b)
                                  // (c) will resolve itself, as data locks are short lived
              long remaining = deadline - System.nanoTime();
              if (remaining <= 0L)
                break;
              workerDead.awaitNanos(remaining); // unblocks (d)
            }
          } catch (InterruptedException e) {
          }
          if (worker != null) {
            System.err.println("Can't stop serial port worker: " + worker);
            worker = null;
          }
          // System.out.println("ok, done!");
        }
        // at this point, worker is dead, will not touch port again
        port = null;
        status = "ready";
      } finally {
        isClosing = false;
        crit.unlock();
      }
      fire();
    }

    void open() {
      crit.lock();
      try {
        if (isOpen || isClosing)
          return;
        try {
          status = "opening";
          port = SerialPortManager.openPort(/* ic,*/ circState,
              path, baud, mode.toString());
          isOpen = true;
          status = "opened";
          worker = new UniquelyNamedThread(this::run, "SerialInputReader");
          worker.setDaemon(true);
          worker.start();
        } catch (Exception e) {
          isOpen = false;
          status = e.getMessage();
          port = null;
          worker = null;
        }
      } finally {
        crit.unlock();
      }
      fire();
    }

    private void fire() {
      data.lock();
      try {
        // NOTE: It seems like we should be able to
        // cause a propagation more directly, since
        // we have the circuitState, which ultimately
        // just needs to mark instance.getComponent()
        // as dirty, basically one line of code.
        if (instance == null)
          System.err.println("missing instance in SerialIn???");
        else
          instance.fireInvalidated();
      } finally {
        data.unlock();
      }
    }

  }

  private static class SerialPortPathAttribute extends Attribute<String> {
    public SerialPortPathAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public java.awt.Component getCellEditor(Window source, String value) {
      return new SerialPortChooser(source, value);
    }

    @Override
    public String parse(String value) {
      return value;
    }
  }

  private static class FormatAttribute extends Attribute<SerialInputFormat> {
    public FormatAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    // Both del and fmt are allowed to have escapes, like "\\n" and "\\t",
    // so we first ensure that neither has an unescaped newline, then
    // we serialize as: delims\nformat.
    // We could also use a fully escaped version of both or either
    // of del and fmt, e.g. taken from the parsed versions, but we will let the
    // XML code handle our mostly unescaped strings for now, to preserve the
    // original user input as much as possible.

    @Override
    public String toStandardString(SerialInputFormat m) {
      String del = m.getDelimiterString().replace("\n", "\\n");
      String fmt = m.getFormatString().replace("\n", "\\n");
      return del + "\n" + fmt;
    }

    @Override
    public String toDisplayString(SerialInputFormat format) {
      if (format.isRaw())
        return "raw bytes";
      else if (format.hasDelimiters())
        return format.getFormatString().replace("\n", "\\n").replace("\t", "\\t")
            + " (delimited by " + format.getDelimiterString().replace("\n", "\\n").replace("\t", "\\t") + ")";
      else
        return format.getFormatString().replace("\n", "\\n").replace("\t", "\\t");
    }

    @Override
    public SerialInputFormat parse(String value) {
      int i = value.indexOf('\n');
      String del = (i < 0) ? "" : value.substring(0, i);
      String fmt = (i < 0) ? value : value.substring(i+1);
      SerialInputFormat m = new SerialInputFormat(del, fmt);
      if (!m.isValid())
        Errors.title(S.get("serialInputFormatErrorTitle")).show(m.errorMessage());
      return m;
    }
    
    @Override
    public java.awt.Component getCellEditor(Window source, SerialInputFormat fmt) {
      return new SerialInputFormatDialog(source, fmt);
    }

  }

}
