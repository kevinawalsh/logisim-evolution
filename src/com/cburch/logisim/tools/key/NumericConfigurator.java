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
import com.cburch.logisim.data.AttributeSet;

public abstract class NumericConfigurator<V>
  implements KeyConfigurator, Cloneable {
  private static final int MAX_TIME_KEY_LASTS = 800;

  private Attribute<V> attr;
  private int minValue;
  private int maxValue;
  private int curValue;
  private int radix;
  private int modsEx;
  private long whenTyped;

  public NumericConfigurator(Attribute<V> attr, int min, int max,
      int modifiersEx) {
    this(attr, min, max, modifiersEx, 10);
  }

  public NumericConfigurator(Attribute<V> attr, int min, int max,
      int modifiersEx, int radix) {
    this.attr = attr;
    this.minValue = min;
    this.maxValue = max;
    this.radix = radix;
    this.modsEx = modifiersEx;
    this.curValue = 0;
    this.whenTyped = 0;
  }

  @Override
  public NumericConfigurator<V> clone() {
    try {
      @SuppressWarnings("unchecked")
      NumericConfigurator<V> ret = (NumericConfigurator<V>) super.clone();
      ret.whenTyped = 0;
      ret.curValue = 0;
      return ret;
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }

  protected abstract V createValue(int value);

  protected int getMaximumValue(AttributeSet attrs) {
    return maxValue;
  }

  protected int getMinimumValue(AttributeSet attrs) {
    return minValue;
  }

  public KeyConfigurationResult keyEventReceived(KeyConfigurationEvent event) {
    // Using KEY_TYPED with ALT_DOWN_MASK is problematic on MacOS. For example,
    // MacOS treats Option+4 as the cents symbol, so e.getKeyChar() would return
    // the unicode for cents, rather than the character '4' in that case. This
    // problem occurs for all digits and probably many characters too. So we use
    // KEY_PRESSED here instead, and manually handle the SHIFT modifier.
    if (event.getType() == KeyConfigurationEvent.KEY_PRESSED) {
      KeyEvent e = event.getKeyEvent();
      int eventMods = e.getModifiersEx();
      char key = (char)e.getKeyCode();
      if ((eventMods & InputEvent.SHIFT_DOWN_MASK) != 0 && 'A' <= key && key <= 'Z') {
        // Handle uppercase, e.g. converting ALT+SHIFT+'F' into ALT+'F'
        eventMods &= ~InputEvent.SHIFT_DOWN_MASK;
      }
      int digit = Character.digit(key, radix);
      if (digit >= 0 && eventMods == modsEx) {
        long now = System.currentTimeMillis();
        long sinceLast = now - whenTyped;
        AttributeSet attrs = event.getAttributeSet();
        int min = getMinimumValue(attrs);
        int max = getMaximumValue(attrs);
        int val = 0;
        if (sinceLast < MAX_TIME_KEY_LASTS) {
          val = radix * curValue;
          if (val > max) {
            val = 0;
          }
        }
        val += digit;
        if (val > max) {
          val = digit;
          if (val > max) {
            return null;
          }
        }
        event.consume();
        whenTyped = now;
        curValue = val;

        if (val >= min) {
          Object valObj = createValue(val);
          return new KeyConfigurationResult(event, attr, valObj);
        }
      }
    }
    return null;
  }
}
