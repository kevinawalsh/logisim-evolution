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
import java.awt.Font;
import java.awt.Graphics2D;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;

public class LinearMap extends FitRange {

  static final BoundedRangeAttribute ATTR_DOMAIN =
      new BoundedRangeAttribute("domain", S.getter("audioLinearMapDomain"));
  static final BoundedRangeAttribute ATTR_RANGE =
      new BoundedRangeAttribute("range", S.getter("audioLinearMapRange"));

  public LinearMap() {
    super("LinearMap", "audioLinearMapComponent");
    setAttributes(
        new Attribute[] {
          INPUT_WIDTH, ATTR_DOMAIN, OUTPUT_WIDTH, ATTR_RANGE },
          new Object[] {
            BitWidth.create(8), new BoundedRange(-50, 50),
            BitWidth.create(8), new BoundedRange(0, 200) });
  }
 
  private static final Font FONT = new Font("monospaced", Font.PLAIN, 6);

  protected void paintShape(InstancePainter painter, boolean inner) {
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    GraphicsUtil.switchToWidth(g, 2);
    g.translate(bds.x, bds.y);
    g.draw(outline);
    if (inner) {
      BoundedRange domain = painter.getAttributeValue(ATTR_DOMAIN);
      BoundedRange range = painter.getAttributeValue(ATTR_RANGE);
      Color c = g.getColor();
      g.setColor(Color.GRAY);
      g.draw(curve);
      g.setColor(c);
      GraphicsUtil.switchToWidth(g, 1);
      GraphicsUtil.drawText(g, FONT, ""+domain.hi, 1, 4, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
      GraphicsUtil.drawText(g, FONT, ""+domain.lo, 1, 33, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM_FIRST);
      GraphicsUtil.drawText(g, FONT, ""+range.hi, 29, 7, GraphicsUtil.H_RIGHT, GraphicsUtil.V_TOP);
      GraphicsUtil.drawText(g, FONT, ""+range.lo, 29, 35, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BOTTOM_FIRST);
    }
    g.translate(-bds.x, -bds.y);
    GraphicsUtil.switchToWidth(g, 1);
  }

  @Override
  public void propagate(InstanceState state) {
    BitWidth wi = state.getAttributeValue(INPUT_WIDTH);
    BitWidth wo = state.getAttributeValue(OUTPUT_WIDTH);
    BoundedRange domain = state.getAttributeValue(ATTR_DOMAIN);
    BoundedRange range = state.getAttributeValue(ATTR_RANGE);
    boolean si = domain.lo < 0;
    boolean so = range.lo < 0;

    Value v = state.getPortValue(1);
    Value out;
    if (!v.isFullyDefined()) {
      out = Value.createUnknown(wo);
    } else {
      long x = v.extendAsLong(si);
      double f = (x - domain.lo)*1.0/(domain.hi - domain.lo); // f in [0.0, 1.0]
      f = Math.max(0.0, Math.min(1.0, f));
      long y = range.lo + (long)Math.round(f*(range.hi - range.lo));
      y = Math.max(range.lo, Math.min(range.hi, y));
      out = Value.createKnown(wo, (int)y);
    }
    state.setPort(0, out, 1);
  }

  static class BoundedRange {
    final String str;
    final long lo, hi;
    BoundedRange(long lo, long hi) {
      this.lo = lo; // Math.max(-(1L<<31), Math.min((1L<<32)-2, Math.min(lo, hi)));
      this.hi = hi; // Math.max(this.lo+1, Math.min((1L<<32)-1, Math.max(lo, hi)));
      this.str = String.format("[%d, %d]", this.lo, this.hi);
    }
    BoundedRange(long lo, long hi, String str) {
      this.lo = lo; // Math.max(-(1L<<31), Math.min((1L<<32)-2, Math.min(lo, hi)));
      this.hi = hi; // Math.max(this.lo+1, Math.min((1L<<32)-1, Math.max(lo, hi)));
      this.str = str;
    }
    public String toString() {
      return str;
    }
  }

  // Holds an inclusive non-empty range, like [-100, +100] or [0, 255], or even [10, -10].
  // Either value can go as low as INT_MAX (-2billion) or as high as +UINT_MAX (+4billion),
  // with the constraint that lo != hi. and they differ by less than 2**32.
  // The string format is "[$lo, $hi]",
  // where $lo and $hi must be written in decimal, with optional "+",
  // and where the space is optional and the braces are optional.
  private static class BoundedRangeAttribute extends Attribute<BoundedRange> {

    private BoundedRangeAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    // @SuppressWarnings("rawtypes")
    // @Override
    // public java.awt.Component getCellEditor(BoundedRange value) {
    //   return super.getCellEditor(value == null ? "[0, 1]" : value.str);
    // }

    @Override
    public BoundedRange parse(String value) {
      value = value.trim();
      String orig = value;
      if (value.startsWith("[") && value.endsWith("]"))
        value = value.substring(1, value.length()-1).trim();
      if (value.startsWith("[") && !value.endsWith("]"))
        throw new NumberFormatException("must have a closing ']'");
      if (!value.startsWith("[") && value.endsWith("]"))
        throw new NumberFormatException("must have an opening '['");
      int n = value.length();
      String[] pieces = value.split(",", -1);
      if (pieces.length == -1)
        throw new NumberFormatException("missing comma, should have format \"[low, high]\"");
      if (pieces.length != 2)
        throw new NumberFormatException("too many commas, should have format \"[low, high]\"");
      String slo = pieces[0].trim();
      String shi = pieces[1].trim();
      if (slo.isEmpty())
        throw new NumberFormatException("missing low value, should have format \"[low, high]\"");
      if (shi.isEmpty())
        throw new NumberFormatException("missing high value, should have format \"[low, high]\"");
      long lo, hi;
      try { lo = Long.parseLong(slo); }
      catch (Exception e) { throw new NumberFormatException("invalid low integer: \""+slo+"\""); }
      try { hi = Long.parseLong(shi); }
      catch (Exception e) { throw new NumberFormatException("invalid high integer: \""+shi+"\""); }
      if (lo < -(1L<<31))
        throw new NumberFormatException("low can't be below " + (-(1L<<31)));
      if (lo > ((1L<<32)-1))
        throw new NumberFormatException("low can't be above " + ((1L<<32)-1));
      if (hi < -(1L<<31))
        throw new NumberFormatException("high can't be below " + (-(1L<<31)));
      if (hi > ((1L<<32)-1))
        throw new NumberFormatException("high can't be above " + ((1L<<32)-1));
      if (hi == lo)
        throw new NumberFormatException("high and low must be different");
      return new BoundedRange(lo, hi, orig);
    }
    
    @Override
    public Domain getDomain() { return Domain.ofDescription("[lo, hi], designating an inclusive range of signed or unsigned integers"); }

  }


}
