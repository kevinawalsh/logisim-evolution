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

package com.cburch.logisim.data;

import com.cburch.logisim.LogisimVersion;

public interface AttributeDefaultProvider {
  
  // Determines what attribute value to use when parsing a file created with the given version, if
  // the file didn't include a value for the attribute. Normally, this would be whatever the default
  // value would have been for that version. But in a few versions of logisim, accidentally the default value was
  // always written to xml and some non-default value was skipped instead. This should return
  // whichever value was omitted when writing to the xml.
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver);

  // This is used to decide if an entire set of attributes should be skipped when writing to xml.
  // There are two relevant cases:
  // - If all the values are the defaults and would have been omitted anyway; returning true in this
  //   case is merely a slight optimization, letting the xml code skip the whole set instead of
  //   checking then skipping' each attribute individually.
  // - If the values are transient and should never be written to xml regardless of their values,
  //   e.g. as for SelectTool.
  public boolean shouldOmitAllAttributesFromXml(AttributeSet attrs, LogisimVersion ver);

  // Decide whether an individual attribute should be omitted when writing to xml.
  default public boolean hasDefaultAttributeValue(AttributeSet attrs, Attribute<?> attr, LogisimVersion ver) {
    Object val = attrs.getValue(attr);
    Object dflt = getDefaultAttributeValue(attr, ver);
    if (val == null && dflt == null)
      return true;
    if (val == null || dflt == null)
      return false;
    return dflt.equals(val);
  }
}
