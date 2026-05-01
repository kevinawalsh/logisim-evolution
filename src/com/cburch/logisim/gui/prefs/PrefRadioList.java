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

package com.cburch.logisim.gui.prefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.util.StringGetter;

class PrefRadioList<E> {
  private List<E> options;
  private Map<E, StringGetter> labels;
  private Map<E, JRadioButton> buttons;

  @SuppressWarnings("unchecked")
  PrefRadioList(Class<E> type, PrefMonitor<E> pref, Map<E, StringGetter> labels) {
    this.labels = labels;
    this.buttons = new HashMap<>();
    this.options = pref.getEnumeratedOptions();
    if (options == null) {
      if (type == Boolean.class) {
        options = List.of((E)Boolean.FALSE, (E)Boolean.TRUE);
      } else {
        options = new ArrayList<E>();
        options.addAll(labels.keySet());
      }
    }

    ButtonGroup group = new ButtonGroup();
    for (E value : options) {
      StringGetter label = labels.get(value);
      JRadioButton btn = new JRadioButton(label == null ? value.toString() : label.toString());
      group.add(btn);
      buttons.put(value, btn);
      btn.addActionListener(e -> {
        if (btn.isSelected()) pref.set(value);
      });
    }

    pref.addPrefChangeWeakListener(this, e -> this.selectOption(pref.get()));
    selectOption(pref.get());
  }

  JPanel createJPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    for (E value : options) {
      panel.add(buttons.get(value));
    }
    return panel;
  }

  void localeChanged() {
    for (Map.Entry<E, StringGetter> entry : labels.entrySet()) {
      JRadioButton btn = buttons.get(entry.getKey());
      if (btn != null) btn.setText(entry.getValue().toString());
    }
  }

  private void selectOption(E value) {
    JRadioButton btn = buttons.get(value);
    if (btn != null) {
      btn.setSelected(true);
    } else if (!options.isEmpty()) {
      buttons.get(options.get(0)).setSelected(true);
    }
  }
}
