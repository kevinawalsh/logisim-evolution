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

package com.cburch.logisim.gui.main;
import static com.cburch.logisim.gui.main.Strings.S;

import java.util.HashMap;
import java.util.Map;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.key.KeyConfigurationEvent;
import com.cburch.logisim.tools.key.KeyConfigurationResult;

public class ToolAttributeAction extends Action {
  public static Action create(KeyConfigurationResult results) {
    return new ToolAttributeAction(results);
  }

  public static Action create(Tool tool, Attribute<?> attr, Object value) {
    AttributeSet attrs = tool.getAttributeSet();
    KeyConfigurationEvent e = new KeyConfigurationEvent(0, attrs, null, null);
    KeyConfigurationResult r = new KeyConfigurationResult(e, attr, value);
    return new ToolAttributeAction(r);
  }

  private KeyConfigurationResult config;
  private Map<Attribute<?>, Object> oldValues;

  private ToolAttributeAction(KeyConfigurationResult config) {
    this.config = config;
    this.oldValues = new HashMap<Attribute<?>, Object>(2);
  }

  @Override
  public String getName() {
    return S.get("changeToolAttrAction");
  }

  @Override
  public void doIt(Project proj) {
    execute(true);
  }

  @Override
  public void undo(Project proj) {
    execute(false);
  }

  private void execute(boolean forward) {
    if (forward) {
      AttributeSet attrs = config.getEvent().getAttributeSet();
      Map<Attribute<?>, Object> newValues = config.getAttributeValues();
      Map<Attribute<?>, Object> oldValues = new HashMap<>(newValues.size());
      for (Map.Entry<Attribute<?>, Object> entry : newValues.entrySet()) {
        @SuppressWarnings("unchecked")
        Attribute<Object> attr = (Attribute<Object>) entry.getKey();
        oldValues.put(attr, attrs.getValue(attr));
        attrs.setAttr(attr, entry.getValue());
      }
      this.oldValues = oldValues;
    } else {
      AttributeSet attrs = config.getEvent().getAttributeSet();
      Map<Attribute<?>, Object> oldValues = this.oldValues;
      for (Map.Entry<Attribute<?>, Object> entry : oldValues.entrySet()) {
        @SuppressWarnings("unchecked")
        Attribute<Object> attr = (Attribute<Object>) entry.getKey();
        attrs.setAttr(attr, entry.getValue());
      }
    }
  }

}
