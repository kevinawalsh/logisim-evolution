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

package com.cburch.logisim.std.audio;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

public class FitRange extends InstanceFactory {

  static final Attribute<Integer> ATTR_INPUTS =
      Attributes.forIntegerRange("inputs", S.getter("gateInputsAttr"), 2, 5);

  static final AttributeOption NORM_CAP = new AttributeOption("cap", S.getter("audioNormalizationCap"));
  static final AttributeOption NORM_FIT = new AttributeOption("fit", S.getter("audioNormalizationFit"));
  static final AttributeOption NORM_CENTER = new AttributeOption("center", S.getter("audioNormalizationCenter"));
  static final Attribute<AttributeOption> ATTR_NORM = Attributes.forOption(
      "normalization", S.getter("audioNormalizationMode"), new AttributeOption[] { NORM_CAP, NORM_FIT, NORM_CENTER });
  
  static final Attribute<AttributeOption> INPUT_MODE = Attributes.forOption(
      "typeIn", S.getter("audioFitInputMode"), new AttributeOption[] { StdAttr.SIGNED_OPTION, StdAttr.UNSIGNED_OPTION });
  static final Attribute<AttributeOption> OUTPUT_MODE = Attributes.forOption(
      "typeOut", S.getter("audioFitOutputMode"), new AttributeOption[] { StdAttr.SIGNED_OPTION, StdAttr.UNSIGNED_OPTION });
  
  public static final Attribute<BitWidth> INPUT_WIDTH = Attributes.forBitWidth(
      "widthIn", S.getter("audioFitInputWidthAttr"));
  public static final Attribute<BitWidth> OUTPUT_WIDTH = Attributes.forBitWidth(
      "widthOut", S.getter("audioFitOutputWidthAttr"));

  static final int WIDTH = 30, HEIGHT = 40;
  static final Path2D.Double outline = new Path2D.Double();
  static final Path2D curve = new Path2D.Double();
  static final Ellipse2D shadow;
  static {
    outline.moveTo(0, 5);
    outline.curveTo(10, 0, WIDTH-10, 10, WIDTH, 5);
    outline.lineTo(WIDTH, HEIGHT-5);
    outline.curveTo(WIDTH-10, HEIGHT, 10, HEIGHT-10, 0, HEIGHT-5);
    outline.closePath();

    double cx = WIDTH/2.0, cy = HEIGHT/2.0;
    curve.moveTo(cx-8, cy);
    curve.curveTo(cx-4, cy-4, cx+4, cy+4, cx+8, cy);

    double r = Math.min(WIDTH, HEIGHT)/2.0-4;
    shadow = new Ellipse2D.Double(cx-r, cy-r, 2*r, 2*r);
  }

  public FitRange() {
    super("FitRange", S.getter("audioFitRangeComponent"));
    setAttributes(
        new Attribute[] {
          INPUT_WIDTH, INPUT_MODE, OUTPUT_WIDTH, OUTPUT_MODE, ATTR_NORM },
          new Object[] {
            BitWidth.create(8), StdAttr.UNSIGNED_OPTION,
            BitWidth.create(8), StdAttr.SIGNED_OPTION, NORM_FIT });
    setOffsetBounds(Bounds.create(-WIDTH, -HEIGHT/2, WIDTH, HEIGHT));
    setIconName("fitrange.png");
    Port[] ps = new Port[2];
    ps[0] = new Port(0, 0, Port.OUTPUT, OUTPUT_WIDTH);
    ps[1] = new Port(-WIDTH, 0, Port.INPUT, INPUT_WIDTH);
    setPorts(ps);
  }
  
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    instance.fireInvalidated(); // recompute using new mode, etc.
  }

  private void paintSymbol(Graphics2D g, char c, double x, double y) {
    if (c == '+') {
      g.draw(new Line2D.Double(x-2.5, y, x+2.5, y));
      g.draw(new Line2D.Double(x, y-2.5, x, y+2.5));
    } else if (c == '-') {
      g.draw(new Line2D.Double(x-2.5, y, x+2.5, y));
    } else if (c == '0') {
      g.draw(new Ellipse2D.Double(x-1.5, y-2.5, 3, 5));
    }
  }

  private void paintShape(InstancePainter painter, boolean inner) {
    Graphics2D g = (Graphics2D)painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    GraphicsUtil.switchToWidth(g, 2);
    g.translate(bds.x, bds.y);
    g.draw(outline);
    if (inner) {
      Color c = g.getColor();
      g.setColor(Color.GRAY);
      g.fill(shadow);
      g.setColor(Color.WHITE);
      g.draw(curve);
      g.setColor(c);
      GraphicsUtil.switchToWidth(g, 1);
      boolean si = painter.getAttributeValue(INPUT_MODE) == StdAttr.SIGNED_OPTION;
      boolean so = painter.getAttributeValue(OUTPUT_MODE) == StdAttr.SIGNED_OPTION;
      paintSymbol(g, '+', 5, 9);
      paintSymbol(g, si ? '-' : '0', 5, 29);
      paintSymbol(g, '+', 25, 11);
      paintSymbol(g, so ? '-' : '0', 25, 31);
    }
    g.translate(-bds.x, -bds.y);
    GraphicsUtil.switchToWidth(g, 1);
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    paintShape(painter, false);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = (Graphics2D)painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    paintShape(painter, true);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    BitWidth wi = state.getAttributeValue(INPUT_WIDTH);
    BitWidth wo = state.getAttributeValue(OUTPUT_WIDTH);
    boolean si = state.getAttributeValue(INPUT_MODE) == StdAttr.SIGNED_OPTION;
    boolean so = state.getAttributeValue(OUTPUT_MODE) == StdAttr.SIGNED_OPTION;
    AttributeOption norm = state.getAttributeValue(ATTR_NORM);

    Value v = state.getPortValue(1);
    if (wo.equals(wi) && (si == so)) {
      state.setPort(0, v, 1); // nop
    } else if (v.isErrorValue()) {
      state.setPort(0, Value.createError(wo), 1);
    } else if (!v.isFullyDefined()) {
      state.setPort(0, Value.createUnknown(wo), 1);
    } else if (wo.equals(wi) && si) {
      // input in [-lo, +hi], make offset be in [0, max]
      int w = wo.getWidth();
      long signbit = 1L << (w-1);
      long x = v.extendAsLong(false);
      if (norm == NORM_CAP) {
        // negative inputs --> zero
        // non-negative inputs --> nop
        if ((x & signbit) != 0)
          x = 0;
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      } else { // NORM_FIT || NORM_CENTER
        // add offset
        x = x ^ signbit;
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      }
    } else if (wo.equals(wi)) { // signed output
      // input in [0, max], make offset be in [-lo, hi]
      int w = wo.getWidth();
      long signbit = 1L << (w-1);
      long x = v.extendAsLong(false);
      if (norm == NORM_CAP) {
        // small inputs --> nop
        // large inputs --> hi
        if ((x & signbit) != 0)
          x = signbit-1;
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      } else { // NORM_FIT || NORM_CENTER
        // subtract offset
        x = x ^ signbit;
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      }
    } else {
      int iw = wi.getWidth();
      long ir = (1L << iw); // span of input range
      long imin = si ? -ir/2 : 0L;
      long imax = si ? (ir/2 - 1) : (ir - 1);
      int ow = wo.getWidth();
      long or = (1L << ow); // span of output range
      long omin = so ? -or/2 : 0L;
      long omax = so ? (or/2 - 1) : (or - 1);
      long x = v.extendAsLong(si);
      if (norm == NORM_CAP) {
        x = Math.max(omin, Math.min(omax, x));
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      } else if (norm == NORM_FIT) {
        // use floating point and hope for the best...
        // f ranges from 0.0 (inclusive) to 1.0 (exclusive)
        long x0 = x;
        double f = ((double)x - (double)imin)/(double)ir;
        x = (long)Math.round(omin + f * or);
        x = Math.max(omin, Math.min(omax, x)); // in case of floating point issues
        // System.out.printf("%d in [%d, %d] --> %f --> %d in [%d, %d]\n", x0, imin, imax, f, x, omin, omax);
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      } else { // NORM_CENTER
        long ic = (imax+1+imin)/2;
        long oc = (omax+1+omin)/2;
        x = x + oc - ic;
        x = Math.max(omin, Math.min(omax, x));
        state.setPort(0, Value.createKnown(wo, (int)x), 1);
      }
    }
  }

}
