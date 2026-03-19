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

package com.cburch.logisim.access;

import java.util.Map;
import java.util.List;

import com.cburch.logisim.data.AttributeSet;

/**
 * Feature interface for providing extra annotation notes for component
 * attributes in the component-listings.json file. Component factories
 * may implement this feature to supply per-attribute notes (e.g., to
 * document constraints, dependencies, or unusual behaviors that are not
 * captured by the attribute's display name or value range alone).
 *
 * Usage: override getFeature(Object key, AttributeSet attrs) in the factory
 * and return an implementation of this interface when key ==
 * ComponentListingFeature.class.
 */
public interface ComponentListingFeature {

  /**
   * Returns extra annotation notes for specific attributes, keyed by the
   * attribute's XML name (i.e., Attribute.getName()). These notes are merged
   * into the "note" field of the corresponding attribute entry in the JSON
   * output. Return an empty map if no notes are needed.
   */
  default Map<String, String> getAttributeNotes(AttributeSet attrs) {
    return null;
  }

  /**
   * Returns a list of attributes to be included from the enumeration during
   * port layout analysis, alongside those found by automated checks.
   */
  default List<String> getLayoutAnalysisIncludedAttributes(AttributeSet attrs) {
    return null;
  }

  /**
   * Returns a list of attributes to be excluded from the enumeration during
   * port layout analysis.
   */
  default List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs) {
    return null;
  }

  default String getLayoutRotation(AttributeSet attrs) {
    if (getCustomPortLayout(attrs) != null) return "custom";
    else return null;
  }

  /**
   * Returns a list of port positions for the "ports" section of the
   * port_layout for these attributes, or null if it should be generated
   * automatically.
   */
  default List<PortPosition> getCustomPortLayout(AttributeSet attrs) {
    return null;
  }

  public static class PortPosition {
    public boolean isArray;
    public final String name; // e.g. "Data"
    public final String type; // "input", "output", "inout"
    public final Object dx, dy; // String, Integer, or null if this is not a single port
    public final Object firstIndex, count, stepDx, stepDy; // String, Integer, or null if this not an array of ports
 
    public PortPosition(String n, String t, Object dx, Object dy) {
      this.isArray = false;
      this.name = n;
      this.type = t;
      this.dx = dx;
      this.dy = dy;
      this.firstIndex = this.count = this.stepDx = this.stepDy = null;
    }

    // NOTE: this duplicates ComponentListingExplorer.PortInfo, essentially
    public PortPosition(String n, String t, Object i, Object c, Object fx, Object fy, Object sx, Object sy) {
      this.isArray = true;
      this.name = n;
      this.type = t;
      this.firstIndex = i;
      this.count = c;
      this.dx = fx;
      this.dy = fy;
      this.stepDx = sx;
      this.stepDy = sy;
    }

    public boolean equalsExceptName(PortPosition other) {
      if (!this.type.equals(other.type)) return false;
      if (!this.dx.equals(other.dx)) return false;
      if (!this.dy.equals(other.dy)) return false;
      if (this.isArray != other.isArray) return false;
      if (isArray && !this.firstIndex.equals(other.firstIndex)) return false;
      if (isArray && !this.count.equals(other.count)) return false;
      if (isArray && !this.stepDx.equals(other.stepDx)) return false;
      if (isArray && !this.stepDy.equals(other.stepDy)) return false;
      return true;
    }
  }

  default PortPosition portAt(String n, String t, Object dx, Object dy) {
    return new PortPosition(n, t, dx, dy);
  }
  default PortPosition portsAt(String n, String t, Object i, Object c, Object fx, Object fy, Object sx, Object sy) {
    return new PortPosition(n, t, i, c, fx, fy, sx, sy);
  }

}
