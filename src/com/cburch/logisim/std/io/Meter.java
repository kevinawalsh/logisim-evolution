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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;

import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
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
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Meter extends InstanceFactory implements DynamicElementProvider {

  public static class Logger extends InstanceLogger {
    
    @Override
    public String getLogName(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.LABEL);
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.WIDTH);
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      Value data = state.getDataAsValue();
      if (data == null)
        return Value.createUnknown(getBitWidth(state, option));
      else
        return data;
    }
  }

  private static final Color DEFAULT_DIAL_COLOR = new Color(226, 214, 182);
  private static final Color NEAR_MAX_COLORS[] = {
    new Color(0xC6,0x00,0x00), // dark red
    new Color(0xFF,0xAA,0x00), // orange
    new Color(0x00,0xA9,0x9B), // vivid teal
  };

  static final AttributeOption SHAPE_BAR = new AttributeOption("bar",
      S.getter("ioMeterBar"));
  static final AttributeOption SHAPE_DIAL = new AttributeOption("dial",
      S.getter("ioMeterDial"));
  static final AttributeOption SHAPE_BOX = new AttributeOption("box",
      S.getter("ioMeterBox"));
  static final Attribute<AttributeOption> ATTR_SHAPE = Attributes
      .forOption("shape", S.getter("ioMeterShape"),
          new AttributeOption[] { SHAPE_BAR, SHAPE_DIAL, SHAPE_BOX });

  public Meter() {
    super("Meter", S.getter("meterComponent"));
    setAttributes(new Attribute[] {
      StdAttr.FACING, ATTR_SHAPE, Io.ATTR_COLOR, StdAttr.WIDTH, StdAttr.MODE,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] {
        Direction.SOUTH, SHAPE_BAR, DEFAULT_DIAL_COLOR,
        BitWidth.create(8), StdAttr.UNSIGNED_OPTION,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIconName("meter.png");
    setInstanceLogger(Logger.class);
    setPorts(new Port[] { new Port(0, 0, Port.INPUT, StdAttr.WIDTH) });
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.computeLabelTextField(Instance.AVOID_RIGHT);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    AttributeOption shape = attrs.getValue(ATTR_SHAPE);
    if (shape == SHAPE_DIAL)
      return Bounds.create(-20, 0, 40, 40).rotate(Direction.SOUTH, facing, 0, 0);
    else if (shape == SHAPE_BOX)
      return Bounds.create(-30, 0, 60, 40).rotate(Direction.SOUTH, facing, 0, 0);
    else // SHAPE_BAR
      return Bounds.create(-10, 0, 20, 60).rotate(Direction.SOUTH, facing, 0, 0);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      instance.computeLabelTextField(Instance.AVOID_RIGHT);
    } else if (attr == ATTR_SHAPE) {
      instance.recomputeBounds();
      instance.computeLabelTextField(Instance.AVOID_RIGHT);
    } else if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_RIGHT);
    } else if (attr == StdAttr.MODE) {
      instance.fireInvalidated();
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    GraphicsUtil.switchToWidth(g, 2);
    AttributeOption shape = painter.getAttributeValue(ATTR_SHAPE);
    if (shape == SHAPE_DIAL)
      g.drawOval(bds.getX() + 1, bds.getY() + 1, bds.getWidth() - 2,
          bds.getHeight() - 2);
    else
      g.drawRect(bds.getX() + 1, bds.getY() + 1, bds.getWidth() - 2,
          bds.getHeight() - 2);
  }

  private static void drawDialTick(Graphics g, int x, int y,
      /*long*/double val, long min, long max,
      int a, int b, Color color, double a0, double aR) {

    double t = (val - min) * 1.0 / (max - min);
    t = Math.max(0.0, Math.min(1.0, t));
    double deg = a0 + t * aR;
    double rad = Math.toRadians(deg);

    double x1 = x + a * Math.cos(rad);
    double y1 = y - a * Math.sin(rad);
    double x2 = x + b * Math.cos(rad);
    double y2 = y - b * Math.sin(rad);

    g.setColor(color);
    ((Graphics2D)g).draw(new Line2D.Double(x1, y1, x2, y2));
  }

  /* also used by Slider */
  static void drawBarTick(Graphics g, int x, int y,
      /*long*/double val, long min, long max,
      int a, int b, Color color, double a0, double aR, boolean upright) {

    double t = (val - min) * 1.0 / (max - min);
    t = Math.max(0.0, Math.min(1.0, t));
    double v = a0 + t * aR;

    g.setColor(color);
    if (upright)
      ((Graphics2D)g).draw(new Line2D.Double(x+a, y-v, x+b, y-v));
    else
      ((Graphics2D)g).draw(new Line2D.Double(x+v, y-a, x+v, y-b));
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Value data = painter.getDataAsValue();
    Bounds bds = painter.getNominalBounds();
    boolean colorized = painter.shouldDrawColor();
    boolean showState = painter.getShowState();

    paintMeter(g, data, bds, painter.getAttributeSet(), colorized, showState, 2, Color.BLACK);

    g.setColor(painter.getAttributeValue(StdAttr.LABEL_COLOR));
    painter.drawLabel();
    painter.drawPorts();

  }

  static void paintMeter(Graphics g, Value value, Bounds bds,
      AttributeSet attrs, boolean colorized, boolean showState, int borderWidth, Color borderColor) {

    BitWidth bits = attrs.getValue(StdAttr.WIDTH);
    AttributeOption mode = attrs.getValue(StdAttr.MODE);
    AttributeOption shape = attrs.getValue(ATTR_SHAPE);
    Direction facing = attrs.getValue(StdAttr.FACING);
    Color dialColor = attrs.getValue(Io.ATTR_COLOR);

    RangedValue pt = new RangedValue(value, bits, mode);

    int x = bds.getX();
    int y = bds.getY();
    int w = bds.getWidth();
    int h = bds.getHeight();

    Color tickColor, maxColor, pinColor;
    if (!colorized) {
      // int hue = lum(dialColor);
      // dialColor = new Color(t, t, t);
      dialColor = Color.WHITE;
      tickColor = maxColor = Color.DARK_GRAY;
      pinColor = Color.BLACK;
    } else {
      pinColor = pickNeedle(dialColor);
      maxColor = pickNearMax(dialColor);
      tickColor = pickTicks(dialColor, pinColor);
    }

    boolean upright = (facing == Direction.NORTH || facing == Direction.SOUTH);

    g.setColor(dialColor);
    if (shape == SHAPE_DIAL) g.fillOval(x+1, y+1, w-2, h-2);
    else g.fillRect(x+1, y+1, w-2, h-2);

    g.setColor(borderColor);
    GraphicsUtil.switchToWidth(g, borderWidth);
    if (shape == SHAPE_DIAL) g.drawOval(x+1, y+1, w-2, h-2);
    else g.drawRect(x+1, y+1, w-2, h-2);
    GraphicsUtil.switchToWidth(g, 1);

    if (shape == SHAPE_DIAL || shape == SHAPE_BOX) {

      int cx = (shape == SHAPE_DIAL || upright ? x + w/2 : x + 4);
      int cy = (shape == SHAPE_DIAL || !upright ? y + h/2 : y + h - 4);
      int r = (shape == SHAPE_DIAL ? 15 : 32);
      double a0 = (shape == SHAPE_DIAL ? 225 : upright ? 135 : -45);
      double aR = (shape == SHAPE_DIAL ? -270 : upright ? -90 : 90);

      if (pt.bits <= 3) {
        for (long v = pt.min; v <= pt.max; v++) {
          boolean big = (mode != StdAttr.SIGNED_OPTION ?
              v > pt.min && v >= pt.max-1
              : ((v > 0 && v >= pt.max-1) || (v < 0 && v <= pt.min+1)));
          drawDialTick(g, cx, cy, (double)v, pt.min, pt.max, r-3, r,
              big ? maxColor : tickColor, a0, aR);
        }
      } else {
        for (int i = 0; i < 8; i++) {
          boolean big = (mode != StdAttr.SIGNED_OPTION ?
              i >= 6 : (i <= 1 || i >= 6));
          drawDialTick(g, cx, cy, pt.min + i*(pt.max-pt.min)/7.0, pt.min, pt.max, r-3, r,
              big ? maxColor : tickColor, a0, aR);
        }
      }
      GraphicsUtil.switchToWidth(g, 2);
      double kerf = (aR > 0 ? 0.6 : -0.6);
      if (!pt.signed) {
        double aMax = a0 + aR - kerf;
        double aSpan = 1.25/7.0 * aR;
        g.setColor(maxColor);
        ((Graphics2D)g).draw(new Arc2D.Double(cx-r, cy-r, 2*r, 2*r, aMax, -aSpan, Arc2D.OPEN));
      } else { // SIGNED_OPTION
        double aMaxN = a0 + kerf;
        double aMaxP = a0 + aR - kerf;
        double aSpan = 1.25/7.0 * aR;
        g.setColor(maxColor);
        ((Graphics2D)g).draw(new Arc2D.Double(cx-r, cy-r, 2*r, 2*r, aMaxN, aSpan, Arc2D.OPEN));
        ((Graphics2D)g).draw(new Arc2D.Double(cx-r, cy-r, 2*r, 2*r, aMaxP, -aSpan, Arc2D.OPEN));
      }
      GraphicsUtil.switchToWidth(g, 1);
      if (showState)
        drawDialTick(g, cx, cy, (double)pt.val, pt.min, pt.max, 0, r,
            pinColor, a0, aR);

    } else { // SHAPE_BAR
        
      int r = (upright ? w-4 : h-4);
      int a0 = 3;
      int aR = (upright ? h-6 : w-6);

      GraphicsUtil.switchToWidth(g, 1);
      if (pt.bits <= 3) {
        for (long v = pt.min; v <= pt.max; v++) {
          boolean big = (mode != StdAttr.SIGNED_OPTION ?
              v > pt.min && v >= pt.max-1
              : ((v > 0 && v >= pt.max-1) || (v < 0 && v <= pt.min+1)));
          drawBarTick(g, x, y+h, (double)v, pt.min, pt.max, r-4, r-1,
              big ? maxColor : tickColor, a0, aR, upright);
        }
      } else {
        for (int i = 0; i < 8; i++) {
          boolean big = (mode != StdAttr.SIGNED_OPTION ?
              i >= 6 : (i <= 1 || i >= 6));
          drawBarTick(g, x, y+h, pt.min + i*(pt.max-pt.min)/7.0, pt.min, pt.max, r-4, r-1,
              big ? maxColor : tickColor, a0, aR, upright);
        }
      }
      GraphicsUtil.switchToWidth(g, 2);
      if (!pt.signed) {
        double aLo = a0 + 5.5/7.0 * aR;
        double aHi = a0 + 1.0 * aR - 0.5;
        g.setColor(maxColor);
        if (upright)
          ((Graphics2D)g).draw(new Line2D.Double(x+r-1, y+h-aLo, x+r-1, y+h-aHi));
        else
          ((Graphics2D)g).draw(new Line2D.Double(x+aLo, y+h-(r-1), x+aHi, y+h-(r-1)));
      } else { // SIGNED_OPTION
        double aLo0 = a0 + 5.5/7.0 * aR + 0.5;
        double aHi0 = a0 + 1.0 * aR - 0.5;
        double aLo1 = a0 + 0.0 * aR + 0.5;
        double aHi1 = a0 + 1.5/7.0 * aR - 0.5;
        g.setColor(maxColor);
        if (upright) {
          ((Graphics2D)g).draw(new Line2D.Double(x+r-1, y+h-aLo0, x+r-1, y+h-aHi0));
          ((Graphics2D)g).draw(new Line2D.Double(x+r-1, y+h-aLo1, x+r-1, y+h-aHi1));
        } else {
          ((Graphics2D)g).draw(new Line2D.Double(x+aLo0, y+h-(r-1), x+aHi0, y+h-(r-1)));
          ((Graphics2D)g).draw(new Line2D.Double(x+aLo1, y+h-(r-1), x+aHi1, y+h-(r-1)));
        }
      }

      GraphicsUtil.switchToWidth(g, 1);
      if (showState)
        drawBarTick(g, x, y+h, (double)pt.val, pt.min, pt.max, 4, r, pinColor, a0, aR, upright);
    
    }
  }

  @Override
  public void propagate(InstanceState state) {
    Value val = state.getPortValue(0);
    state.setData(val);
  }

  public DynamicElement createDynamicElement(int x, int y, DynamicElement.Path path) {
    return new MeterShape(x, y, path);
  }

  static double _f(int v) {
    double s = v / 255.0;
    return s <= 0.03928 ? s/12.92 : Math.pow((s+0.055)/1.055,2.4);
  }
  static double lum(Color c) {
    return 0.2126*_f(c.getRed()) + 0.7152*_f(c.getGreen()) + 0.0722*_f(c.getBlue());
  }

  static double contrast(Color a, Color b) {
    double L1 = Math.max(lum(a), lum(b));
    double L2 = Math.min(lum(a), lum(b));
    return (L1+0.05)/(L2+0.05);
  }

  /* also used by Dial */
  static Color mix(Color a, Color b, double t, int alpha) {
    int rr = (int)Math.round(a.getRed()  *(1-t)+b.getRed()  *t);
    int gg = (int)Math.round(a.getGreen()*(1-t)+b.getGreen()*t);
    int bb = (int)Math.round(a.getBlue() *(1-t)+b.getBlue() *t);
    return new Color(rr, gg, bb, alpha);
  }

  /* also used by Slider */
  static Color pickNeedle(Color dial) {
    return contrast(dial, Color.BLACK) >= contrast(dial, Color.WHITE) ?
        Color.BLACK : Color.WHITE;
  }

  /* also used by Slider */
  static Color pickTicks(Color dial, Color needle) {
    return mix(needle, dial, 0.70, 200);
  }

  static Color pickNearMax(Color dial) {
    return pickContrasting(dial, NEAR_MAX_COLORS);
  }

  /* also used by Slider */
  static Color pickContrasting(Color bg, Color[] palette) {
    Color best = palette[0];
    double bestC = contrast(bg, best);
    for (int i = 1; i < palette.length; i++){
      double c = contrast(bg, palette[i]);
      if (c > bestC) {
        best = palette[i];
        bestC = c;
      }
    }
    return best;
  }

  /* also used by Slider */
  static class RangedValue {
    final boolean signed;
    final int bits;
    final long val, min, max;

    RangedValue(Value value, BitWidth bw, AttributeOption mode) {
      signed = (mode != StdAttr.UNSIGNED_OPTION);
      bits = bw.getWidth();
      int intval = value == null || !value.isFullyDefined() ?
          0 : value.toIntValue();
      if (!signed) {
        val = (long)intval & 0x00000000ffffffffL;
        min = 0L;
        max = (1L << bits) - 1L;
      } else {
        if (bits == 32 || (intval & (1 << (bits-1))) == 0)
          val = intval;
        else
          val = -(long)((~intval & bw.getMask()) + 1L);
        min = -(1L << bits - 1);
        max = (1L << bits - 1) - 1L;
      }
    }
  }

}
