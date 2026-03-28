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

package com.cburch.logisim.comp;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.swing.Icon;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.generic.QuickHelp;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;

public abstract class AbstractComponentFactory implements ComponentFactory {
  private static final Icon toolIcon = Icons.getIcon("subcirc.gif");

  private AttributeSet defaultSet;

  protected AbstractComponentFactory() {
  } 
  public boolean ActiveOnHigh(AttributeSet attrs) {
    return true;
  }

  public AttributeSet createAttributeSet() {
    return AttributeSets.EMPTY;
  }

  public void drawGhost(ComponentDrawContext context, Color color, int x,
      int y, AttributeSet attrs) {
    Graphics2D g = context.getGraphics();
    Bounds bds = getOffsetBounds(attrs); // Note: for Text this is wrong, but InstanceFactory
                                         // overrides and does not call this due to
                                         // Text.paintGhost implementation
    g.setColor(color);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x + bds.getX(), y + bds.getY(), bds.getWidth(),
        bds.getHeight());
  }

  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    AttributeSet dfltSet = defaultSet;
    if (dfltSet == null) {
      dfltSet = (AttributeSet) createAttributeSet().clone();
      defaultSet = dfltSet;
    }
    return dfltSet.getValue(attr);
  }

  public StringGetter getDisplayGetter() {
    return StringUtil.constantGetter(getName());
  }

  public String getDisplayName() {
    return getDisplayGetter().toString();
  }

  @Override
  public Object getFeature(Object key, AttributeSet attrs) {
    if (key == QUICK_HELP) {
      return QuickHelp.load(getClass());
    }
    return null;
  }

  public boolean HasThreeStateDrivers(AttributeSet attrs) {
    return false;
  }

  public boolean shouldOmitAllAttributesFromXml(AttributeSet attrs, LogisimVersion ver) {
    // For components generally, don't skip the attributes en masse when encoding to xml.
    return false;
  }

  public void paintIcon(ComponentDrawContext context, int x, int y,
      AttributeSet attrs) {
    Graphics2D g = context.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(context.getDestination(), g, x + 2, y + 2);
    } else {
      g.setColor(Color.black);
      g.drawRect(x + 5, y + 2, 11, 17);
      Value[] v = { Value.TRUE, Value.FALSE };
      for (int i = 0; i < 3; i++) {
        g.setColor(v[i % 2].getColor());
        g.fillOval(x + 5 - 1, y + 5 + 5 * i - 1, 3, 3);
        g.setColor(v[(i + 1) % 2].getColor());
        g.fillOval(x + 16 - 1, y + 5 + 5 * i - 1, 3, 3);
      }
    }
  }

  @Override
  public String toString() {
    return getName();
  }

}
