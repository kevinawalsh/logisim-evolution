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

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;

public class MemPoker extends InstancePoker {
  private static class AddrPoker extends MemPoker {
    @Override
    public Bounds getBounds(InstancePainter painter) {
      MemState data = (MemState) painter.getData();
      return data.getBounds(-1, painter.getBounds());
    }

    @Override
    public void keyTyped(InstanceState state, KeyEvent e) {
      char c = e.getKeyChar();
      int val = Character.digit(e.getKeyChar(), 16);
      MemState data = (MemState) state.getData();
      if (val >= 0) {
        long newScroll = (data.getScroll() * 16 + val)
            & (data.getLastAddress());
        data.setScroll(newScroll);
      } else if (c == ' ') {
        data.setScroll(data.getScroll() + (data.GetNrOfLines() - 1)
            * data.GetNrOfLineItems());
      } else if (c == '\r' || c == '\n') {
        data.setScroll(data.getScroll() + data.GetNrOfLineItems());
      } else if (c == '\u0008' || c == '\u007f') {
        data.setScroll(data.getScroll() - data.GetNrOfLineItems());
      } else if (c == 'R' || c == 'r') {
        data.getContents().clear();
      } else {
        return;
      }
      e.consume();
    }

    @Override
    public void keyPressed(InstanceState state, KeyEvent e) {
      MemState data = (MemState) state.getData();
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        data.setScroll(data.getScroll() - data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        data.setScroll(data.getScroll() + data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
        data.setScroll(data.getScroll() - data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
        data.setScroll(data.getScroll() + data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
        data.setScroll(data.getScroll() - (data.GetNrOfLines() - 1) * data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
        data.setScroll(data.getScroll() + (data.GetNrOfLines() - 1) * data.GetNrOfLineItems());
      } else {
        return;
      }
      e.consume();
    }

    @Override
    public void paint(InstancePainter painter) {
      Bounds bds = getBounds(painter);
      Graphics g = painter.getGraphics();
      g.setColor(Color.RED);
      g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
      g.setColor(Color.BLACK);
    }
  }

  private static class DataPoker extends MemPoker {
    int initValue;
    int curValue;

    private DataPoker(InstanceState state, MemState data, long addr) {
      data.setCursor(addr);
      initValue = data.getContents().get(data.getCursor());
      curValue = initValue;

      Object attrs = state.getInstance().getAttributeSet();
      if (attrs instanceof RomAttributes) {
        Project proj = state.getProject();
        if (proj != null) {
          ((RomAttributes) attrs).setProject(proj);
        }
      }
    }

    @Override
    public Bounds getBounds(InstancePainter painter) {
      MemState data = (MemState) painter.getData();
      Bounds inBounds = painter.getInstance().getBounds();
      return data.getBounds(data.getCursor(), inBounds);
    }

    @Override
    public void keyTyped(InstanceState state, KeyEvent e) {
      char c = e.getKeyChar();
      int val = Character.digit(e.getKeyChar(), 16);
      MemState data = (MemState) state.getData();
      if (val >= 0) {
        curValue = curValue * 16 + val;
        data.getContents().set(data.getCursor(), curValue);
        state.fireInvalidated();
      } else if (c == ' ' || c == '\t') {
        moveTo(data, data.getCursor() + 1);
      } else if (c == '\r' || c == '\n') {
        moveTo(data, data.getCursor() + data.GetNrOfLineItems());
      } else if (c == '\u0008' || c == '\u007f') {
        moveTo(data, data.getCursor() - 1);
      } else if (c == 'R' || c == 'r') {
        data.getContents().clear();
      } else {
        return;
      }
      e.consume();
    }

    @Override
    public void keyPressed(InstanceState state, KeyEvent e) {
      MemState data = (MemState) state.getData();
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        moveTo(data, data.getCursor() - data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        moveTo(data, data.getCursor() + data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
        moveTo(data, data.getCursor() - 1);
      } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
        moveTo(data, data.getCursor() + 1);
      } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
        moveTo(data, data.getCursor() - (data.GetNrOfLines() - 1) * data.GetNrOfLineItems());
      } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
        moveTo(data, data.getCursor() + (data.GetNrOfLines() - 1) * data.GetNrOfLineItems());
      } else {
        return;
      }
      e.consume();
    }

    private void moveTo(MemState data, long addr) {
      if (addr < 0)
        addr = 0;
      else if (addr > data.getLastAddress())
        addr = data.getLastAddress();
      if (data.isValidAddr(addr)) {
        data.setCursor(addr);
        data.scrollToShow(addr);
        initValue = data.getContents().get(addr);
        curValue = initValue;
      }
    }

    @Override
    public void paint(InstancePainter painter) {
      Bounds bds = getBounds(painter);
      if (bds.isEmpty())
        return;
      Graphics g = painter.getGraphics();
      g.setColor(Color.RED);
      g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
      g.setColor(Color.BLACK);
    }

    @Override
    public void stopEditing(InstanceState state) {
      MemState data = (MemState) state.getData();
      data.setCursor(-1);
    }
  }

  private MemPoker sub;

  @Override
  public Bounds getBounds(InstancePainter state) {
    return sub.getBounds(state);
  }

  @Override
  public boolean init(InstanceState state, MouseEvent event) {
    Bounds bds = state.getInstance().getBounds();
    MemState data = (MemState) state.getData();
    long addr = data.getAddressAt(event.getX() - bds.getX(), event.getY()
        - bds.getY());

    // See if outside box
    if (addr < 0) {
      sub = new AddrPoker();
    } else {
      sub = new DataPoker(state, data, addr);
    }
    return true;
  }

  @Override
  public void keyPressed(InstanceState state, KeyEvent e) {
    sub.keyPressed(state, e);
  }

  @Override
  public void keyTyped(InstanceState state, KeyEvent e) {
    sub.keyTyped(state, e);
  }

  @Override
  public void paint(InstancePainter painter) {
    sub.paint(painter);
  }
}
