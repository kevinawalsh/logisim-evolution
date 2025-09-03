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

package com.cburch.logisim.tools.key;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Direction;

public class DirectionConfigurator implements KeyConfigurator, Cloneable {
  private Attribute<? extends Object> attr;
  private int modsEx;

  // Note: As of at least git commit 3663685d7d7ad2e9cbccf988c6ca6efb879fb437,
  // this class is used as:
  //   new DirectionConfigurator(StdAttr.LABEL_LOC)
  // and the two-argument constructor is never used.
  
  public DirectionConfigurator(Attribute<? extends Object> attr) {
    this.attr = attr;
    this.modsEx = InputEvent.ALT_DOWN_MASK;
  }

  public DirectionConfigurator(Attribute<? extends Object> attr, int modifiersEx) {
    this.attr = attr;
    this.modsEx = modifiersEx;
  }

  @Override
  public DirectionConfigurator clone() {
    try {
      return (DirectionConfigurator) super.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }

  public KeyConfigurationResult keyEventReceived(KeyConfigurationEvent event) {
    if (event.getType() == KeyConfigurationEvent.KEY_PRESSED) {
      KeyEvent e = event.getKeyEvent();
      if (e.getModifiersEx() == modsEx) {
        Direction value = null;
        switch (e.getKeyCode()) {
        case KeyEvent.VK_UP:
          value = Direction.NORTH;
          break;
        case KeyEvent.VK_DOWN:
          value = Direction.SOUTH;
          break;
        case KeyEvent.VK_LEFT:
          value = Direction.WEST;
          break;
        case KeyEvent.VK_RIGHT:
          value = Direction.EAST;
          break;
        }
        if (value != null) {
          event.consume();
          return new KeyConfigurationResult(event, attr, value);
        }
      }
    }
    return null;
  }
}
