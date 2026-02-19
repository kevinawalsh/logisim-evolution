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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

public class SaturatingDivider extends SaturatingAdder {

  public SaturatingDivider() {
    super("SaturatingDivider", "audioSaturatingDividerComponent");
    setIconName("saturatingdivider.png");
  }
  
  @Override
  protected void paintDecoration(Graphics2D g, double cx, double cy) {
    g.setColor(Color.GRAY);
    g.fill(new Ellipse2D.Double(cx-12, cy-12, 24, 24));
    g.setColor(Color.WHITE);
    GraphicsUtil.switchToWidth(g, 2);
    g.fill(new Ellipse2D.Double(cx-1.5, cy-6-1.5, 3, 3));
    g.fill(new Ellipse2D.Double(cx-1.5, cy+6-1.5, 3, 3));
    g.draw(new Line2D.Double(cx-8, cy, cx+8, cy));
  }

  @Override
  public void propagate(InstanceState state) {
    int w = state.getAttributeValue(StdAttr.WIDTH).getWidth();
    int n = state.getAttributeValue(ATTR_INPUTS);
    boolean signed = state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    AttributeOption norm = state.getAttributeValue(ATTR_NORM);

    long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
    long min = signed ? -((1L << (w-1))) : 0L;
    double prod = 1.0;
    int m = 0;
    for (int i = 0; i < n; i++) {
      Value v = state.getPortValue(1+i);
      if (v.isFullyDefined()) {
        long x = v.extendAsLong(signed);
        if (m > 1 && norm == NORM_FIT)
          prod *= max;
        if (!signed && norm == NORM_CENTER) {
          // prod *= (x - max/2.0);
          if (i == 0)
            prod *= (x - (max+1)/2);
          else
            prod /= (x - (max+1)/2);
        } else {
          if (i == 0)
            prod *= x;
          else
            prod /= x;
        }
        m++;
      }
    }
    if (norm == NORM_FIT) {
      // This was already done in loop, above.
      // Signed:
      //   Inputs are in [-A, +B] and prod is in [-A^m, +B^m] so scale by 1/B^(m-1).
      // Unsigned:
      //   Inputs are in [0, +B] and prod is in [0, +B^m] so scale by 1/B^(m-1).
    } else if (norm == NORM_CENTER) {
      // Signed:
      //   Inputs centered on 0, prod centered on 0, so no offset.
      // Unsigned:
      //   Inputs originally centered on B/2, above we
      //   offset them by -B/2 to be centered on 0.
      //   So prod is now centered on 0, and we offst by +B/2.
      //   Note: this offset works fine even in the m=1 and m=0 cases. Having
      //   the output be centered at B/2+1 when there are no inputs is reasonable.
      //   Also note: We could use a fractional midpoint, e.g. B/2=3.5 in the
      //   4-bit case where B=15, so the range is perfectly centered. But there
      //   would be no inputs that represent the exact center. We instead round
      //   up to B/2=4, to match the asymmetric range for the signed case, since
      //   that's what we'd normally subtract when converting from unsigned to
      //   signed.
      if (!signed) {
        // prod = prod + max/2.0;
        prod = prod + (max+1)/2;
      }
    }
    long ret = Math.max(min, Math.min(max, (long)Math.round(prod)));
    state.setPort(0, Value.createKnown(BitWidth.create(w), (int)ret), 1);
  }

}
