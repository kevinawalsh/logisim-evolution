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

package com.cburch.logisim.std.memory;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;

public class RegisterPoker extends InstancePoker {
  private int initValue;
  private int curValue;

  @Override
  public boolean capturesTextInput() { return true; }

  @Override
  public boolean init(InstanceState state, MouseEvent e) {
    RegisterData data = (RegisterData) state.getDataFor();
    if (data == null) {
      data = new RegisterData(state.getAttributeValue(Register.ATTR_INIT));
      state.setData(data);
    }
    initValue = data.value;
    curValue = initValue;
    return true;
  }

  @Override
  public void keyTyped(InstanceState state, KeyEvent e) {
    int val = Character.digit(e.getKeyChar(), 16);
    if (val < 0)
      return;
    e.consume();
    BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
    if (dataWidth == null)
      dataWidth = BitWidth.create(8);
    curValue = (curValue * 16 + val) & dataWidth.getMask();
    RegisterData data = (RegisterData) state.getDataFor();
    data.value = curValue;

    state.fireInvalidated();
  }

  @Override
  public void keyPressed(InstanceState state, KeyEvent e) {
    BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
    if (dataWidth == null)
      dataWidth = BitWidth.create(8);
    if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_RIGHT) {
      int maxVal = dataWidth.getMask();
      if (curValue != maxVal) {
        curValue = curValue + 1;
        RegisterData data = (RegisterData) state.getDataFor();
        data.value = curValue;
        state.fireInvalidated();
      }
      e.consume();
    } else if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_LEFT) {
      if (curValue != 0) {
        curValue = curValue - 1;
        RegisterData data = (RegisterData) state.getDataFor();
        data.value = curValue;
        state.fireInvalidated();
      }
      e.consume();
    }
  }

  @Override
  public void paint(InstancePainter painter) {
    Bounds bds = painter.getNominalBounds();
    BitWidth dataWidth = painter.getAttributeValue(StdAttr.WIDTH);
    int width = dataWidth == null ? 8 : dataWidth.getWidth();
    int len = (width + 3) / 4;

    Graphics g = painter.getGraphics();
    g.setColor(Color.RED); // red to indicate text capture
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      if (len > 4) {
        g.drawRect(bds.getX(), bds.getY() + 3, bds.getWidth(), 25);
      } else {
        int wid = 7 * len + 2;
        g.drawRect(bds.getX() + (bds.getWidth() - wid) / 2, bds.getY() + 4, wid, 15);
      }
    } else {
      int wid = 7 * len + 2;
      g.drawRect(bds.getX() + (bds.getWidth() - wid) / 2, bds.getY(), wid, 16);
    }
    g.setColor(Color.BLACK);
  }
}
