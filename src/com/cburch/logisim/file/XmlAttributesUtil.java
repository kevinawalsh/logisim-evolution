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

package com.cburch.logisim.file;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.Main;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;

public class XmlAttributesUtil {
  static void addAttributeSetContent(Document doc, String outFilepath,
      Element elt, AttributeSet attrs, AttributeDefaultProvider source) {
    if (attrs == null)
      return;
    LogisimVersion ver = Main.VERSION;
    if (source != null && source.shouldOmitAllAttributesFromXml(attrs, ver))
      return;
    for (Attribute<?> attrBase : attrs.getAttributes()) {
      @SuppressWarnings("unchecked")
      Attribute<Object> attr = (Attribute<Object>) attrBase;
      if (!attrs.isToSave(attr))
        continue;
      if (source != null && source.hasDefaultAttributeValue(attrs, attr, ver))
        continue;
      Object val = attrs.getValue(attr);
      if (val == null)
        continue;
      Element a = doc.createElement("a");
      a.setAttribute("name", attr.getName());
      String value = attr.toStandardStringRelative(val, outFilepath);
      if (value.indexOf("\n") >= 0)
        a.appendChild(doc.createTextNode(value));
      else
        a.setAttribute("val", value);
      elt.appendChild(a);
    }
  }

}
