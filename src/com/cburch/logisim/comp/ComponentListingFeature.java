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
  Map<String, String> getAttributeNotes(AttributeSet attrs);

  /**
   * Returns a list of attributes to be excluded from the enumeration during
   * port layout analysis.
   */
  List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs);

}
