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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Dial extends Slider {
      
  public static class DialPoker extends SliderPoker {

    @Override
    public void mouseDragged(InstanceState state, MouseEvent e) {
      AttributeSet attrs = state.getAttributeSet();
      Bounds bds = state.getInstance().getNominalBounds();
      boolean spin = attrs.getValue(FREE_SPINNING);
      int cx = bds.x + bds.width/2;
      int cy = bds.y + bds.height/2;
      int dx = e.getX() - cx;
      int dy = e.getY() - cy;
      double radians = Math.atan2(-dy, dx);
      double degrees = Math.toDegrees(radians);
      if (degrees <= -90)
        degrees += 360;
      double t = spin ?
          (degrees - 270) / -360
          : (degrees - ANGLE_MIN) / (ANGLE_MAX - ANGLE_MIN);
      State data = (State) state.getDataAsCustom();
      if (data == null) {
        data = new State(attrs);
        data.setPosition(t, attrs);
        state.setData(data);
      } else {
        data.setPosition(t, attrs);
      }
      state.queueForPropagation();
    }

    @Override
    public void paint(InstancePainter painter) {
      paintDial(painter, true, true);
    }

  }
  
  public static final Attribute<Boolean> FREE_SPINNING
      = Attributes.forBoolean("freeSpinning", S.getter("freeSpinning"));

  private static final double ANGLE_MIN = 225; // only if non-free-spinning
  private static final double ANGLE_MAX = -45; // only if non-free-spinning
  private static final int MARGIN = 3;
  private static final int RADIUS = 20;

  public Dial() {
    super("Dial", S.getter("dialComponent"));
    setAttributes(new Attribute[] {
      StdAttr.FACING, Io.ATTR_COLOR, StdAttr.WIDTH, StdAttr.MODE,
      FREE_SPINNING, RETURN_TO_ZERO,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] {
        Direction.SOUTH, DEFAULT_BACKGROUND_COLOR,
        BitWidth.create(8), StdAttr.UNSIGNED_OPTION,
        false, false,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIconName("dial.png");
    setInstanceLogger(Logger.class);
    setInstancePoker(DialPoker.class);
    setPorts(new Port[] { new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH) });
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    return Bounds.create(-20, 0, 40, 40).rotate(Direction.SOUTH, facing, 0, 0);
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds().expand(-1);
    GraphicsUtil.switchToWidth(g, 1);
    g.drawOval(bds.x+4, bds.y+4, bds.width-8, bds.height-8);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(bds.x, bds.y, bds.width, bds.height);
  }

  private static void drawTick(Graphics g, int x, int y, double t, int a, int b, Color color, boolean freespin) {
    // x, y is center of bounds
 
    double deg = freespin ?
        270 + t * (-360)
        : ANGLE_MIN + t * (ANGLE_MAX - ANGLE_MIN);
    double rad = Math.toRadians(deg);

    double x1 = x + a * Math.cos(rad);
    double y1 = y - a * Math.sin(rad);
    double x2 = x + b * Math.cos(rad);
    double y2 = y - b * Math.sin(rad);

    g.setColor(color);
    ((Graphics2D)g).draw(new Line2D.Double(x1, y1, x2, y2));
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    boolean colorized = painter.shouldDrawColor();
    boolean showState = painter.getShowState();
    paintDial(painter, colorized, showState);
  }

  private static final Color LIMIT_COLORS[] = {
    new Color(0xC6,0x00,0x00), // dark red
    new Color(0xFF,0xAA,0x00), // orange
    new Color(0x00,0xA9,0x9B), // vivid teal
  };

  static void paintDial(InstancePainter painter, boolean colorized, boolean showState) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();

    AttributeSet attrs = painter.getAttributeSet();
    BitWidth bw = attrs.getValue(StdAttr.WIDTH);
    AttributeOption mode = attrs.getValue(StdAttr.MODE);
    Direction facing = attrs.getValue(StdAttr.FACING);
    Color slideColor = attrs.getValue(Io.ATTR_COLOR);
    boolean freeSpin = attrs.getValue(FREE_SPINNING);

    int x = bds.getX();
    int y = bds.getY();
    int w = bds.getWidth();
    int h = bds.getHeight();

    Color tickColor, pinColor, edgeColor, outsideColor;
    if (!colorized) {
      slideColor = Color.WHITE;
      tickColor = Color.DARK_GRAY;
      pinColor = Color.BLACK;
      edgeColor = Color.DARK_GRAY;
      outsideColor = Color.WHITE;
    } else {
      pinColor = Meter.pickContrasting(slideColor, HANDLE_COLORS);
      tickColor = Meter.pickTicks(slideColor, Color.DARK_GRAY);
      edgeColor = Meter.pickContrasting(slideColor, LIMIT_COLORS);
      outsideColor = Meter.mix(slideColor, Color.WHITE, 0.5, 255);
    }

    g.setColor(outsideColor);
    g.fillRect(x+1, y+1, w-2, h-2);
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x+1, y+1, w-2, h-2);
    GraphicsUtil.switchToWidth(g, 1);

    g.setColor(slideColor);
    g.fillOval(x+5, y+5, w-10, h-10);
    g.setColor(Color.BLACK);
    g.drawOval(x+5, y+5, w-10, h-10);

    int cx = x + w/2;
    int cy = y + h/2;
    int edge = RADIUS-2;
    
    Meter.RangedValue r = new Meter.RangedValue(null, bw, mode);
    if (bw.getWidth() <= 3) {
      for (long v = r.min; v <= r.max; v++) {
        double tt = (v - r.min) * 1.0 / (r.max - r.min);
        if (v == r.min || v == r.max)
          drawTick(g, cx, cy, tt, edge-2, edge+2, edgeColor, freeSpin);
        else if (v == 0 && mode == StdAttr.SIGNED_OPTION)
          drawTick(g, cx, cy, tt, edge-2, edge, edgeColor, freeSpin);
        else
          drawTick(g, cx, cy, tt, edge-2, edge, tickColor, freeSpin);
      }
      if (bw.getWidth() == 1) {
        // draw a fake middle tick
        drawTick(g, cx, cy, 0.5, edge-2, edge, tickColor, freeSpin);
      }
    } else {
      for (int i = 0; i < 8; i++) {
        double tt = i / 7.0;
        if (i == 0 || i == 7)
          drawTick(g, cx, cy, tt, edge-2, edge+2, edgeColor, freeSpin);
        else
          drawTick(g, cx, cy, tt, edge-2, edge, tickColor, freeSpin);
      }
      if (mode == StdAttr.SIGNED_OPTION) {
        // draw a zero tick, in different style
        double tt = (0 - r.min) * 1.0 / (r.max - r.min);
        drawTick(g, cx, cy, tt, edge-2, edge, edgeColor, freeSpin);
      }
    }

    if (showState) {
      State data = (State) painter.getDataAsCustom();
      if (data == null) {
        data = new State(attrs);
        painter.setData(data);
      }
      double t = data.getPosition();
      GraphicsUtil.switchToWidth(g, 1);
      drawTick(g, cx, cy, t, edge/3, edge-6, pinColor, freeSpin);
    }

    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();
    painter.drawPorts();
  }


}
