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

package com.cburch.logisim.gui.generic;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import com.cburch.logisim.prefs.PrefMonitor;

public class BasicZoomModel implements ZoomModel {
  private double[] zoomOptions;

  private PropertyChangeSupport support;
  private double zoomFactor;
  private boolean showGrid;

  public BasicZoomModel(PrefMonitor<Boolean> gridPref,
      PrefMonitor<Double> zoomPref, double[] zoomOpts) {
    zoomOptions = zoomOpts;
    support = new PropertyChangeSupport(this);
    zoomFactor = apply(1.0);
    showGrid = true;

    setZoomFactor(zoomPref.get());
    setShowGrid(gridPref.get());
  }

  public void addPropertyChangeListener(String prop, PropertyChangeListener l) {
    support.addPropertyChangeListener(prop, l);
  }

  public boolean getShowGrid() {
    return showGrid;
  }

  public double getZoomFactor() {
    return zoomFactor;
  }

  public double[] getZoomOptions() {
    return zoomOptions;
  }

  public void removePropertyChangeListener(String prop,
      PropertyChangeListener l) {
    support.removePropertyChangeListener(prop, l);
  }

  public void setShowGrid(boolean value) {
    if (value != showGrid) {
      showGrid = value;
      support.firePropertyChange(ZoomModel.SHOW_GRID, !value, value);
    }
  }

  public void setZoomFactor(double value) {
    double oldValue = zoomFactor;
    value = apply(value);
    if (value != oldValue) {
      zoomFactor = value;
      support.firePropertyChange(ZoomModel.ZOOM,
          Double.valueOf(oldValue), Double.valueOf(value));
    }
  }

  // my 39 favorite zoom amounts... fractions 0.2 <= a/b <= 8.0,
  // with integers a and b, and b in {1, 2, 3, 4, 5, 6, 8}.
  private static final double targets[] = {
    1/5.0, 1/4.0, 1/3.0, 3/8.0, 2/5.0, 1/2.0, 3/5.0,
    5/8.0, 2/3.0, 3/4.0, 4/5.0, 5/6.0, 7/8.0, 1/1.0,
    9/8.0, 7/6.0, 6/5.0, 5/4.0, 4/3.0, 7/5.0, 3/2.0,
    8/5.0, 5/3.0, 7/4.0, 9/5.0, 2/1.0, 9/4.0, 7/3.0,
    5/2.0, 8/3.0, 3/1.0, 10/3.0, 7/2.0, 4/1.0, 9/2.0,
    5/1.0, 6/1.0, 7/1.0, 8/1.0,
  };

  private final double snap = 0.005; // 0.5% window
  private final double release = snap*1.5;
  private int latched = -1;

  double apply(double z) {
    double lnz = Math.log(z);
    int best = -1;
    double bestD = Double.MAX_VALUE;
    for (int i = 0; i < targets.length; i++) {
      double d = Math.abs(lnz - Math.log(targets[i])); // relative diff
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    double tol = (latched == best ? release : snap);
    if (bestD <= tol) {
      latched = best;
      return targets[best];
    }
    if (latched >= 0) {
      // stay snapped until we exit release window
      double dLatched = Math.abs(lnz - Math.log(targets[latched]));
      if (dLatched <= release)
        return targets[latched];
      latched = -1;
    }
    return z; // no snap
  }

}
