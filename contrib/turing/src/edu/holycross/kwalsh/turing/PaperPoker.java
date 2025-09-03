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

package edu.holycross.kwalsh.turing;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;

public class PaperPoker extends InstancePoker {

  boolean left, right;

	public PaperPoker() { }

	@Override
	public boolean init(InstanceState s, MouseEvent e) {
		Bounds bds = s.getInstance().getNominalBounds();
    int x = e.getX(), y = e.getY();
    int roller = PaperTape.inRoller(bds, x, y);
    if (roller < 0) {
      left = true;
      right = false;
      return true;
    } else if (roller > 0) {
      left = false;
      right = true;
      return true;
    } else if (PaperTape.cellBounds(bds).contains(x, y)) {
      left = false;
      right = false;
      PaperData state = PaperData.get(s);
      state.setAnimated(false);
      return true;
    } else {
      return false;
    }
	}

  @Override
  public void mousePressed(InstanceState s, MouseEvent e) {
    if (!init(s, e))
      return;
		PaperData state = PaperData.get(s);
    if (left)
      state.moveLeft();
    else if (right)
      state.moveRight();
    else 
      return;
		s.fireInvalidated();
  }

  @Override
  public void mouseReleased(InstanceState s, MouseEvent e) { }
	
  @Override
  public void keyPressed(InstanceState s, KeyEvent e) {
    if (left || right)
      return;

		PaperData state = PaperData.get(s);
    if (e.getKeyCode() == KeyEvent.VK_DELETE) {
      state.erase();
    } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
      state.moveLeft();
      state.erase();
    } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
      state.moveLeft();
    } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
      state.moveRight();
    } else {
      return;
    }
		s.fireInvalidated();
  }

  @Override
	public void keyTyped(InstanceState s, KeyEvent e) {
    if (left || right)
      return;

		PaperData state = PaperData.get(s);
    int c = (int)e.getKeyChar();
    if (c != '\b' && c != 0x1F) { // backspace and delete
      if (state.printUtf32Symbol(c))
        state.moveRight();
    } else {
      return;
    }
		s.fireInvalidated();
  }

	@Override
	public void paint(InstancePainter painter) {
    if (left || right)
      return;

		Bounds bds = painter.getNominalBounds();
    bds = PaperTape.cellBounds(bds);
		Graphics g = painter.getGraphics();
		g.setColor(Color.RED);
		g.drawRect(bds.x+1, bds.y+1, bds.width-2, bds.height-2);
		g.setColor(Color.BLACK);
	}

	@Override
  public void stopEditing(InstanceState s) {
    PaperData state = PaperData.get(s);
    state.setAnimated(true);
  }

}
