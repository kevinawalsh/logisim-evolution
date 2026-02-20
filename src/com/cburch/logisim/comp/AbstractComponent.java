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

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;

// Used only by ManagedComponent, hence used only by Video and Splitter.
public abstract class AbstractComponent implements Component {
  protected AbstractComponent() { }

  public boolean nominallyContains(Location pt) {
    Bounds bds = getNominalBounds();
    if (bds == null)
      return false;
    return bds.contains(pt, 1);
  }

  public boolean visiblyContains(Location pt) {
    return nominallyContains(pt);
  }

  public boolean endsAt(Location pt) {
    for (EndData data : getEnds()) {
      if (data.getLocation().equals(pt))
        return true;
    }
    return false;
  }

  public abstract Bounds getNominalBounds();

  public Bounds getVisibleBounds() {
    return getNominalBounds();
  }

}
