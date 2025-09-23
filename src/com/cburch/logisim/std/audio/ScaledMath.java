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
import java.awt.geom.Rectangle2D;
import java.awt.geom.Path2D;
import java.util.HashMap;

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
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class ScaledMath extends InstanceFactory {

  static final AttributeOption SIN = new AttributeOption("sin", S.getter("audioScaledMathSin"));
  static final AttributeOption COS = new AttributeOption("cos", S.getter("audioScaledMathCos"));
  static final AttributeOption SQUARE = new AttributeOption("square", S.getter("audioScaledMathSquare"));
  static final AttributeOption SQRT = new AttributeOption("root", S.getter("audioScaledMathSquareRoot"));
  static final AttributeOption EXP = new AttributeOption("exp", S.getter("audioScaledMathExp"));
  static final AttributeOption LOG = new AttributeOption("log", S.getter("audioScaledMathLog"));
  static final AttributeOption ABS = new AttributeOption("abs", S.getter("audioScaledMathAbs"));
  static final AttributeOption SINC = new AttributeOption("sinc", S.getter("audioScaledMathSinc"));
  static final Attribute<AttributeOption> ATTR_FUNC = Attributes.forOption(
      "function", S.getter("audioScaledMathFunction"), new AttributeOption[] {
        SIN, COS, SQUARE, SQRT, EXP, LOG, ABS, SINC
      });
  
  public static final Attribute<String> ATTR_EQN =
      Attributes.forString("eqn", S.getter("audioScaledMathEquation"));
  public static final Attribute<String> ATTR_IMAP =
      Attributes.forString("inputmap", S.getter("audioScaledMathInputMap"));
  public static final Attribute<String> ATTR_OMAP =
      Attributes.forString("outputmap", S.getter("audioScaledMathOutputMap"));

  protected ScaledMath(String name, String localized) {
    super(name, S.getter(localized));
    setAttributes(
        new Attribute[] {
          StdAttr.WIDTH, StdAttr.MODE, ATTR_FUNC, ATTR_EQN, ATTR_IMAP, ATTR_OMAP },
          new Object[] {
            BitWidth.create(8), StdAttr.UNSIGNED_OPTION, SIN, getEqn(SIN, false), getIMap(8, false), getOMap(8, false) });
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
  }

  public ScaledMath() {
    this("ScaledMath", "audioScaledMathComponent");
    setIconName("scaledmath.png");
    setOffsetBounds(Bounds.create(-30, -15, 30, 30));
    setPorts(new Port[] {
      new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH),
      new Port(-30, 0, Port.INPUT, StdAttr.WIDTH),
    });
  }
  
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.setAttributeReadOnly(ATTR_EQN, true);
    instance.setAttributeReadOnly(ATTR_IMAP, true);
    instance.setAttributeReadOnly(ATTR_OMAP, true);
    instance.getAttributeSet().setToSave(ATTR_EQN, false);
    instance.getAttributeSet().setToSave(ATTR_IMAP, false);
    instance.getAttributeSet().setToSave(ATTR_OMAP, false);
    recomputeEqn(instance);
    recomputeMaps(instance);
  }

  private void recomputeEqn(Instance instance) {
    AttributeOption func = instance.getAttributeValue(ATTR_FUNC);
    boolean signed = instance.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    String newEqn = getEqn(func, signed);
    String oldEqn = instance.getAttributeValue(ATTR_EQN);
    if (!newEqn.equals(oldEqn))
      instance.getAttributeSet().changeAttr(ATTR_EQN, newEqn);
  }

  private void recomputeMaps(Instance instance) {
    int w = instance.getAttributeValue(StdAttr.WIDTH).getWidth();
    boolean signed = instance.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    String newIMap = getIMap(w, signed);
    String oldIMap = instance.getAttributeValue(ATTR_IMAP);
    if (!newIMap.equals(oldIMap))
      instance.getAttributeSet().changeAttr(ATTR_IMAP, newIMap);
    String newOMap = getOMap(w, signed);
    String oldOMap = instance.getAttributeValue(ATTR_IMAP);
    if (!newOMap.equals(oldOMap))
      instance.getAttributeSet().changeAttr(ATTR_OMAP, newOMap);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.MODE || attr == ATTR_FUNC) {
      recomputeEqn(instance);
    }
    if (attr == StdAttr.WIDTH || attr == StdAttr.MODE) {
      recomputeMaps(instance);
    }
    instance.fireInvalidated(); // recompute using new mode, etc.
  }

  private static final HashMap<AttributeOption, Path2D.Double>
      unsignedCurves = new HashMap<>();
  private static final HashMap<AttributeOption, Path2D.Double>
      signedCurves = new HashMap<>();
  
  protected void paintDecoration(Graphics2D g,
      double cx, double cy, AttributeOption func, boolean signed) {
    int N = 20;
    int W = 20;
    g.setColor(Color.GRAY);
    g.fill(new Rectangle2D.Double(cx-W/2-2, cy-W/2-2, W+4, W+4));

    Path2D.Double curve;
    HashMap<AttributeOption, Path2D.Double> cache =
        signed ? signedCurves : unsignedCurves;
    synchronized(cache) {
      curve = cache.get(func);
    }
    if (curve == null) {
      curve = new Path2D.Double();
      if (signed) {
        for (int i = 0; i <= N; i++) {
          double x = i*1.0/N - 0.5;
          double y = compute(func, signed, x);
          double px = W*x;
          double py = -W*y;
          if (i == 0) curve.moveTo(px, py);
          else curve.lineTo(px, py);
        }
      } else {
        for (int i = 0; i <= N; i++) {
          double x = i*1.0/N;
          double y = compute(func, signed, x);
          double px = W*(x-0.5);
          double py = -W*(y-0.5);
          if (i == 0) curve.moveTo(px, py);
          else curve.lineTo(px, py);
        }
      }
      synchronized(cache) {
        cache.put(func, curve);
      }
    }
    // GraphicsUtil.switchToWidth(g, 0.5f);
    g.setColor(Color.WHITE);
    g.translate(cx, cy);
    g.draw(curve);
    g.translate(-cx, -cy);
    g.setColor(Color.BLACK);
    // GraphicsUtil.switchToWidth(g, 1);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics2D g = (Graphics2D)painter.getGraphics();
    painter.drawBounds();
    Bounds bds = painter.getNominalBounds();
    double cx = bds.x + bds.width/2.0;
    double cy = bds.y + bds.height/2.0;
   
    AttributeOption func = painter.getAttributeValue(ATTR_FUNC);
    boolean signed = painter.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    paintDecoration(g, cx, cy, func, signed);

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    BitWidth bw = state.getAttributeValue(StdAttr.WIDTH);
    int w = bw.getWidth();
    boolean signed = state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    AttributeOption func = state.getAttributeValue(ATTR_FUNC);

    long zero = signed ? 0 : (1L << (w-1));
      
    Value input = state.getPortValue(1);
    Value output;
    if (input.isErrorValue()) {
      output = Value.createError(bw);
    } else {
      // exmaple: convert input from [0, 256) to [0.0, 1.0)
      // exmaple: convert input from [-128, +128) to [-0.5, +0.5)
      long span = (1L << w);
      long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
      long min = signed ? -((1L << (w-1))) : 0L;
      long i = input.isFullyDefined() ? input.toIntValue() : signed ? 0 : (span/2);
      if (signed) {
        long signbit = 1L << (w - 1);
        long moresigns = -1L << w;
        if ((i & signbit) != 0)
          i |= moresigns;
      }
      double x = ((double)i)/(double)span;
      double y = compute(func, signed, x);
      // System.out.println(func + " of " + x + " --> " + y);
      // example: convert output from [0.0, 1.0) to [0, 256)
      // example: convert output from [-0.5, +0.5) to [-128, +128)
      long j = (long)Math.round(y * span);
      j = Math.min(max, Math.max(min, j));
      output = Value.createKnown(bw, (int)j);
    }

    state.setPort(0, output, 1);
  }

  private static String getIMap(int w, boolean signed) {
    long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
    long min = signed ? -((1L << (w-1))) : 0L;
    if (signed)
      return String.format("[%d, %d] \u27F6 [-0.5, 0.5)", min, max);
    else
      return String.format("[%d, %d] \u27F6 [0.0, 1.0)", min, max);
  }

  private static String getOMap(int w, boolean signed) {
    long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
    long min = signed ? -((1L << (w-1))) : 0L;
    if (signed)
      return String.format("[-0.5, 0.5) \u27F6 [%d, %d] ", min, max);
    else
      return String.format("[0.0, 1.0) \u27F6 [%d, %d]", min, max);
  }

  private static double compute(AttributeOption func, boolean signed, double x) {
    if (signed) {
      x = Math.min(+0.5, Math.max(-0.5, x));
      return computeSigned(func, x);
    } else {
      x = Math.min(1.0, Math.max(0.0, x));
      return computeUnsigned(func, x);
    }
  }

  private static String getEqn(AttributeOption func, boolean signed) {
      return signed ? getSignedEqn(func) : getUnsignedEqn(func);
  }

  private static String getUnsignedEqn(AttributeOption func) {
    if (func == SIN) {
      return "sin(2\u03c0x)/2 + 0.5";
    } else if (func == COS) {
      return "cos(2*\u03c0x)/2 + 0.5";
    } else if (func == SQUARE) {
      return "x\u00b2";
    } else if (func == SQRT) {
      return "\u221ax";
    } else if (func == EXP) {
      return "(e^(3x) - 1)/(e^3 - 1)";
    } else if (func == LOG) {
      return "ln((e^3-1)x + 1)/3";
    } else if (func == ABS) {
      return "2|x-0.5|";
    } else if (func == SINC) {
      return "sinc(50(x-0.5))+0.5";
    } else {
      return "undefined"; // not reached
    }
  }

  // [0.0, 1.0] --> [0.0, 1.0]
  private static double computeUnsigned(AttributeOption func, double x) {
    if (func == SIN) {
      return Math.sin(2*Math.PI*x) / 2 + 0.5;
    } else if (func == COS) {
      return Math.cos(2*Math.PI*x) / 2 + 0.5;
    } else if (func == SQUARE) {
      return x*x;
    } else if (func == SQRT) {
      return Math.sqrt(x);
    } else if (func == EXP) {
      return (Math.exp(3*x) - 1.0)/(Math.E*Math.E*Math.E - 1.0);
    } else if (func == LOG) {
      return Math.log(x*(Math.E*Math.E*Math.E - 1.0) + 1.0)/3;
    } else if (func == ABS) {
      return 2*Math.abs(x-0.5);
    } else if (func == SINC) {
      return (Math.abs(x-0.5)<0.00001) ? 1.0 : Math.sin(50*Math.PI*(x-0.5))/(100*Math.PI*(x-0.5)) + 0.5;
    } else {
      return x; // not reached
    }
  }

  private static String getSignedEqn(AttributeOption func) {
    if (func == SIN) {
      return "sin(2\u03c0x)/2";
    } else if (func == COS) {
      return "cos(2\u03c0x)/2";
    } else if (func == SQUARE) {
      return "(2*x)\u00b2 - 0.5";
    } else if (func == SQRT) {
      return "\u221a(x + 0.5) - 0.5";
    } else if (func == EXP) {
      return "(e^(3(x+0.5)) - 1)/(e^3 - 1) - 0.5";
    } else if (func == LOG) {
      return "(ln((x+0.5)(e^3 - 1)) + 1)/3 - 0.5";
    } else if (func == ABS) {
      return "|2x| - 0.5";
    } else if (func == SINC) {
      return "sinc(50x)/2";
    } else {
      return "undefined"; // not reached
    }
  }
  
  // [-0.5, +0.5] --> [-0.5, +0.5]
  private static double computeSigned(AttributeOption func, double x) {
    if (func == SIN) {
      return Math.sin(2*Math.PI*x) / 2;
    } else if (func == COS) {
      return Math.cos(2*Math.PI*x) / 2;
    } else if (func == SQUARE) {
      return (2*x)*(2*x) - 0.5;
    } else if (func == SQRT) {
      return Math.sqrt(x + 0.5) - 0.5;
    } else if (func == EXP) {
      return (Math.exp(3*(x+0.5)) - 1.0)/(Math.E*Math.E*Math.E - 1.0) - 0.5;
    } else if (func == LOG) {
      return Math.log((x+0.5)*(Math.E*Math.E*Math.E - 1.0) + 1.0)/3 - 0.5;
    } else if (func == ABS) {
      return Math.abs(2*x) - 0.5;
    } else if (func == SINC) {
      return (Math.abs(x)<0.00001) ? 0.5 : Math.sin(50*Math.PI*x)/(100*Math.PI*x);
    } else {
      return x; // not reached
    }
  }

}
