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

import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

public class SaturatingSubtractor extends SaturatingAdder {

  public SaturatingSubtractor() {
    super("SaturatingSubtractor", "audioSaturatingSubtractorComponent");
    setIconName("saturatingsubtractor.png");
  }
  
  protected void paintDecoration(Graphics2D g, double cx, double cy) {
    g.setColor(Color.GRAY);
    g.fill(new Ellipse2D.Double(cx-12, cy-12, 24, 24));
    g.setColor(Color.WHITE);
    GraphicsUtil.switchToWidth(g, 2);
    g.draw(new Line2D.Double(cx-8, cy, cx+8, cy));
  }

  @Override
  public void propagate(InstanceState state) {
    int w = state.getAttributeValue(StdAttr.WIDTH).getWidth();
    int n = state.getAttributeValue(ATTR_INPUTS);
    boolean signed = state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
    AttributeOption norm = state.getAttributeValue(ATTR_NORM);

    long sum = 0;
    int m = 0;
    for (int i = 0; i < n; i++) {
      Value v = state.getPortValue(1+i);
      if (v.isFullyDefined()) {
        long x = v.extendAsLong(signed);
        if (i == 0)
          sum += x;
        else
          sum -= x;
        m++;
      }
    }
    long max = signed ? ((1L << (w-1)) - 1) : ((1L << w) - 1);
    long min = signed ? -((1L << (w-1))) : 0L;
    if (norm == NORM_FIT) {
      // Signed:
      //   Inputs in [-A, +B], sum in [-A*m, +B*m], so scale by 1/m.
      // Unsigned:
      //   Inputs in [0, +B], sum in [0, +B*m], so scale by 1/m.
      if (m > 1)
        sum /= m;
    } else if (norm == NORM_CENTER) {
      // Signed:
      //   Inputs centered on 0, sum centered on 0, so no offset.
      // Unsigned:
      //   Inputs centered on B/2, sum centered m*B/2, so offset by -(m-1)*B/2.
      //   Note: this offset works fine even in the m=1 and m=0 cases. Having
      //   the output be centered at B/2 when there are no inputs is reasonable.
      if (!signed)
        sum = sum - (m-1)*max/2;
    }
    sum = Math.max(min, Math.min(max, sum));
    state.setPort(0, Value.createKnown(BitWidth.create(w), (int)sum), 1);
  }

}
