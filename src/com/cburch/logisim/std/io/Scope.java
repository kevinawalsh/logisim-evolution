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

package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

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
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Scope extends InstanceFactory {

  private static final int MAX_ZOOM = 20;

  private static final Integer[] HEIGHT_OPTIONS = { 40, 60, 80, 100, 120, 140 };
  private static final Integer[] WIDTH_OPTIONS = { 40, 80, 120, 160, 200, 240 };
  
  private static final Attribute<Integer> WIDTH_OPTION =
      Attributes.forOption("scopewidth", S.getter("ioScopeWidth"), WIDTH_OPTIONS);
  private static final Attribute<Integer> HEIGHT_OPTION =
      Attributes.forOption("scopeheight", S.getter("ioScopeHeight"), HEIGHT_OPTIONS);
  private static final Attribute<Integer> SAMPLES_OPTION =
      Attributes.forIntegerRange("samples", S.getter("ioScopeSamples"), 40, 64*1024);

  public static class Poker extends InstancePoker {

    @Override
    public void mousePressed(InstanceState circState, MouseEvent e) {
      Bounds bds = circState.getInstance().getNominalBounds();
      int ax = bds.x + bds.width - 5, ay = bds.y + bds.height - 15;
      int bx = bds.x + bds.width - 5, by = bds.y + bds.height - 5;
      State state = getState(circState);
      if (dist(e, ax, ay) <= 4 && state.zoom < MAX_ZOOM) {
        state.zoom++;
      } else if (dist(e, bx, by) <= 4 && state.zoom > 1) {
        state.zoom--;
      }
    }

    private static double dist(MouseEvent e, int x, int y) {
      double mx = e.getX();
      double my = e.getY();
      return Math.sqrt((mx-x)*(mx-x)+(my-y)*(my-y));
    }

    @Override
    public void paint(InstancePainter painter) {
      State state = getState(painter);
      Graphics2D g = painter.getGraphics();
      Bounds bds = painter.getNominalBounds();
      int ax = bds.x + bds.width - 5, ay = bds.y + bds.height - 15;
      int bx = bds.x + bds.width - 5, by = bds.y + bds.height - 5;
      GraphicsUtil.switchToWidth(g, 1);
      g.setColor(Color.WHITE);
      g.fillOval(ax-4, ay-4, 8, 8);
      g.fillOval(bx-4, by-4, 8, 8);
      g.setColor(Color.GRAY);
      g.drawOval(ax-4, ay-4, 8, 8);
      g.drawOval(bx-4, by-4, 8, 8);
      GraphicsUtil.switchToWidth(g, 1);
      g.setColor(state.zoom < MAX_ZOOM ? Color.BLACK : Color.GRAY);
      g.drawLine(ax-2, ay, ax+2, ay);
      g.drawLine(ax, ay-2, ax, ay+2);
      g.setColor(state.zoom > 1 ? Color.BLACK : Color.GRAY);
      g.drawLine(bx-2, by, bx+2, by);
    }

  }

  private static class State implements ComponentData {
    private double[] samples;
    private int idx, cnt;
    Value lastClock = Value.UNKNOWN;
    int zoom = 1; // 1 to MAX_ZOOM

    private BitWidth bw; // saved, for revalidation
    private AttributeOption mode; // saved, for revalidation

    public State(AttributeSet attrs) {
      int n  = attrs.getValue(SAMPLES_OPTION);
      samples = new double[n];
      bw = attrs.getValue(StdAttr.WIDTH);
      mode = attrs.getValue(StdAttr.MODE);
    }

    private State(State other) {
      samples = other.samples.clone();
      idx = other.idx;
      cnt = other.cnt;
      lastClock = other.lastClock;
      zoom = other.zoom;
      bw = other.bw;
      mode = other.mode;
    }

    public Value setLastClock(Value newClock) {
      Value ret = lastClock;
      lastClock = newClock;
      return ret;
    }

    public void addSample(Value val, AttributeSet attrs) {
      int n = attrs.getValue(SAMPLES_OPTION);
      BitWidth bw0 = attrs.getValue(StdAttr.WIDTH);
      AttributeOption mode0 = attrs.getValue(StdAttr.MODE);
      if (n != samples.length) {
        samples = new double[n];
        idx = 0;
        cnt = 0;
      }
      if (bw0 != bw || mode0 != mode) {
        bw = bw0;
        mode = mode0;
        idx = 0;
        cnt = 0;
      }
      Meter.RangedValue pt = new Meter.RangedValue(val, bw, mode);
      double t = (pt.val - pt.min)*1.0/(pt.max - pt.min);
      t = Math.max(0.0, Math.min(t, 1.0));
      samples[idx++] = t;
      if (idx >= n)
        idx = 0;
      else if (cnt < n)
        cnt++;
    }
    
    @Override
    public ComponentData duplicateForNewSimulation() {
      return new State(this);
    }

    public void drawWaveform(Graphics2D g,
        double x, double y, double w, double h) {
      // we aren't thread-safe, so make some local copies
      int cnt = this.cnt;
      int idx = this.idx;
      int zoom = this.zoom;
      double[] samples = this.samples;
      int n = samples.length;

      if (cnt <= 0 || idx >= n || cnt > n)
        return;

      int domain = n/zoom;
      if (cnt > domain)
        cnt = domain;

      // The waveform is nicest with anti-aliasing disabled and pure, beveled strokes
      Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      Object sc = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
      Stroke old = g.getStroke();

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setStroke(new BasicStroke(0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

      // Span: full, or partial with right alignment
      final double spanW = (cnt <= 1) ? 0.0 : w * (cnt - 1) / Math.max(1.0, (domain - 1));
      final double startX = x + (w - spanW);
      final double dx = (cnt <= 1) ? 0.0 : (spanW / (cnt - 1));

      int cursor = idx + n - cnt;

      Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO, Math.max(cnt, 2));
      for (int i = 0; i < cnt; i++) {
        int j = (cursor + i) % n;
        double s = samples[j];
        float px = (float)(startX + i * dx);
        float py = (float)(y + (1.0 - s) * h);
        if (i == 0)
          path.moveTo(px, py);
        else
          path.lineTo(px, py);
      }
      g.draw(path);

      if (cnt == 1) {
        // draw a dot so a single sample is visible
        float px = (float)startX;
        float py = (float)(y + (1.0 - samples[cursor % n]) * h);
        g.draw(new Line2D.Float(px, py, px, py));
      }

      g.setStroke(old);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, sc);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

  }

  public Scope() {
    super("Scope", S.getter("scopeComponent"));
    setAttributes(new Attribute<?>[] { 
      Io.ATTR_ON_COLOR, Io.ATTR_OFF_COLOR,
          WIDTH_OPTION, HEIGHT_OPTION, SAMPLES_OPTION,
          StdAttr.EDGE_TRIGGER, StdAttr.WIDTH, StdAttr.MODE,
      }, new Object[] {
        Color.GREEN, Color.GRAY,
            100, 60, 8*1024,
            StdAttr.TRIG_RISING, BitWidth.create(8), StdAttr.UNSIGNED_OPTION
      });
    setIconName("scope.png");
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setInstancePoker(Poker.class);
    setPorts(new Port[] {
      new Port(0, 0, Port.INPUT, StdAttr.WIDTH),
      new Port(20, 20, Port.INPUT, 1), // CK
      new Port(10, 20, Port.INPUT, 1), // EN
    });
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.computeLabelTextField(Instance.AVOID_RIGHT);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int w = attrs.getValue(WIDTH_OPTION);
    int h = attrs.getValue(HEIGHT_OPTION);
    return Bounds.create(0, -(h-20), w, h);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    instance.recomputeBounds();
    instance.fireInvalidated();
  }

  @Override
  public void paintInstance(InstancePainter painter) {

    AttributeSet attrs = painter.getAttributeSet();
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    
    boolean colorized = painter.shouldDrawColor();
    boolean showState = painter.getShowState();

    Color onColor, offColor, tickColor;
    if (!colorized) {
      onColor = Color.BLACK;
      offColor = Color.LIGHT_GRAY;
      tickColor = Color.DARK_GRAY;
    } else {
      onColor = attrs.getValue(Io.ATTR_ON_COLOR);
      offColor = attrs.getValue(Io.ATTR_OFF_COLOR);
      tickColor = Meter.pickTicks(offColor, onColor);
    }
    
    int x = bds.x;
    int y = bds.y;
    int w = bds.width;
    int h = bds.height;
    
    GraphicsUtil.switchToWidth(g, 1);
    
    g.setColor(offColor);
    g.fillRoundRect(x, y, w, h, 3, 3);

    g.setColor(Color.BLACK);
    g.drawRoundRect(x, y, w, h, 3, 3);

    x += 1; w -= 2;
    y += 3; h -= 6;

    g.setColor(tickColor);
    g.draw(new Line2D.Double(x+0.5, y-0.5, x+w-1, y-0.5));
    g.draw(new Line2D.Double(x+0.5, y+h-0.5, x+w-1, y+h-0.5));
    if (attrs.getValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION)
      g.draw(new Line2D.Double(x+0.5, y+h/2-0.5, x+w-1, y+h/2-0.5));

    if (showState) {
      State state = (State)painter.getDataAsCustom();
      if (state == null) {
        state = new State(attrs);
        painter.setData(state);
      }

      // maybe use synchronized?
      g.setColor(onColor);
      state.drawWaveform(g, x, y, w, h);
    }

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPort(0);
    painter.drawClock(1, Direction.NORTH);
    painter.drawPort(2);
  }


  @Override
  public void propagate(InstanceState circState) {
    
    Value sample = circState.getPortValue(0);
    Value clock = circState.getPortValue(1);
    Value enable = circState.getPortValue(2);

    if (!clock.isFullyDefined() || !sample.isFullyDefined() || enable == Value.FALSE)
      return;

    AttributeSet attrs = circState.getAttributeSet();
    State state = getState(circState);
    AttributeOption trigger = attrs.getValue(StdAttr.EDGE_TRIGGER);

    Value lastClock = state.setLastClock(clock);
    boolean go;
    if (trigger == StdAttr.TRIG_FALLING) {
      go = lastClock == Value.TRUE && clock == Value.FALSE;
    } else {
      go = lastClock == Value.FALSE && clock == Value.TRUE;
    }
    if (!go)
      return;

    // maybe use synchronized?
    state.addSample(sample, attrs);

  }

  private static State getState(InstanceState circState) {
    State state = (State) circState.getDataAsCustom();
    if (state == null) {
      AttributeSet attrs = circState.getAttributeSet();
      state = new State(attrs);
      circState.setData(state);
    }
    return state;
  }

}
