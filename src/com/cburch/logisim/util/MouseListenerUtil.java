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

package com.cburch.logisim.util;

import java.util.function.Consumer;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class MouseListenerUtil {

  // MouseListenerUtil provides helpers to addresses an issue with Swing's
  // `component.addMouseListener(MouseListener listener)` mechanism, which is
  // not ideal if used directly. When a user clicks, the listener will get three
  // callbacks, in order:
  //   listener.mousePressed(...);
  //   listener.mouseReleased(...);
  //   listener.mouseClicked(...);
  // But this happens only if there was absolutely no mouse movement during the
  // gesture. Even the slightest mouse movement will suppress the
  // `mouseClicked(...)` callback, making the UI seem glitchy and unreliable.
  // This wrapper provides some tolerance, so mouseClicked() is called as long
  // as the press and release are within a few pixels.
  //
  // Usage:
  //   Replace: component.addMouseListener(...);
  //   With   : component.addMouseListener(MouseListenerUtil.clickFix(...));
  //
  // Or, if `mousePressed(...) and `mouseReleased(...)` are not of interest:
  //   Replace: component.addMouseListener(...);
  //   With   : component.addMouseListener(MouseListenerUtil.clickHandler(e -> ...));

  public static final int CLICK_TOLERANCE = 5; // pixels of mouse movement still counted as a click

  public static MouseListener clickFix(MouseListener inner) {
    return new MouseListener() {
      private Point pressPoint;

      @Override
      public void mousePressed(MouseEvent e) {
        pressPoint = e.getPoint();
        inner.mousePressed(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        Point pt = pressPoint;
        pressPoint = null;
        inner.mouseReleased(e);
        if (pt != null && e.getPoint().distance(pt) <= CLICK_TOLERANCE)
          inner.mouseClicked(e);
      }

      @Override
      public void mouseClicked(MouseEvent e)  { } // superseded by above
      @Override
      public void mouseEntered(MouseEvent e)  { inner.mouseEntered(e); }
      @Override
      public void mouseExited(MouseEvent e)   { inner.mouseExited(e); }
    };
  }

  public static MouseListener clickHandler(Consumer<MouseEvent> handler) {
    return new MouseListener() {
      private Point pressPoint;

      @Override
      public void mousePressed(MouseEvent e) {
        pressPoint = e.getPoint();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        Point pt = pressPoint;
        pressPoint = null;
        if (pt != null && e.getPoint().distance(pt) <= CLICK_TOLERANCE)
          handler.accept(e);
      }

      @Override
      public void mouseClicked(MouseEvent e)  { } // superseded by above
      @Override
      public void mouseEntered(MouseEvent e)  { }
      @Override
      public void mouseExited(MouseEvent e)   { }
    };
  }

}
