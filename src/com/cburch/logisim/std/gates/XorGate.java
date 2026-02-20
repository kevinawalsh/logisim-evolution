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

package com.cburch.logisim.std.gates;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Graphics2D;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Expressions;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.tools.WireRepairData;
import com.cburch.logisim.util.GraphicsUtil;

class XorGate extends AbstractGate {

  protected static Expression xorExpression(Expression[] inputs, int numInputs) {
    if (numInputs > 2) {
      throw new UnsupportedOperationException("XorGate");
    }
    Expression ret = inputs[0];
    for (int i = 1; i < numInputs; i++) {
      ret = Expressions.xor(ret, inputs[i]);
    }
    return ret;
  }

  public static XorGate FACTORY = new XorGate();

  private XorGate() {
    super("XOR Gate", S.getter("xorGateComponent"), true);
    setAdditionalWidth(10);
    setIconNames("xorGate.gif", "xorGateRect.gif", "dinXorGate.gif");
    setPaintInputLines(true);
  }

  @Override
  protected Expression computeExpression(Expression[] inputs, int numInputs) {
    return xorExpression(inputs, numInputs);
  }

  @Override
  protected Value computeOutput(Value[] inputs, int numInputs,
      InstanceState state) {
    Object behavior = state.getAttributeValue(GateAttributes.ATTR_XOR);
    if (behavior == GateAttributes.XOR_ODD) {
      return GateFunctions.computeOddParity(inputs, numInputs);
    } else {
      return GateFunctions.computeExactlyOne(inputs, numInputs);
    }
  }

  @Override
  protected Value getIdentity() {
    return Value.FALSE;
  }

  @Override
  public String getRectangularLabel(AttributeSet attrs) {
    if (attrs == null)
      return "";
    boolean isOdd = false;
    Object behavior = attrs.getValue(GateAttributes.ATTR_XOR);
    if (behavior == GateAttributes.XOR_ODD) {
      Object inputs = attrs.getValue(GateAttributes.ATTR_INPUTS);
      if (inputs == null || ((Integer) inputs).intValue() != 2) {
        isOdd = true;
      }
    }
    return isOdd ? "2k+1" : "=1";
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    if (ctx.lang.equals("VHDL"))
      return GateVhdlGenerator.forXor(ctx);
    else
      return GateVerilogGenerator.forXor(ctx);
  }

  @Override
  protected void paintDinShape(InstancePainter painter, int width,
      int height, int inputs) {
    PainterDin.paintXor(painter, width, height, false);
  }

  @Override
  public void paintIconShaped(InstancePainter painter) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.drawCenteredArc(g, 2, -5, 22, -90, 53);
    GraphicsUtil.drawCenteredArc(g, 2, 23, 22, 90, -53);
    GraphicsUtil.drawCenteredArc(g, -10, 9, 16, -30, 60);
    GraphicsUtil.drawCenteredArc(g, -12, 9, 16, -30, 60);
  }

  @Override
  protected void paintShape(InstancePainter painter, int width, int height) {
    PainterShaped.paintXor(painter, width, height);
  }

  @Override
  protected boolean shouldRepairWire(Instance instance, WireRepairData data) {
    return !data.getPoint().equals(instance.getLocation());
  }

}
