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

package com.cburch.logisim.analyze.gui;
import static com.cburch.logisim.analyze.model.Strings.S;

import java.awt.event.ItemListener;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BoxLayout;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.VariableList;
import com.cburch.logisim.analyze.model.VariableListEvent;
import com.cburch.logisim.analyze.model.VariableListListener;

class OutputSelector {
  private class Model extends AbstractListModel<String>
    implements ComboBoxModel<String>, VariableListListener {
    private static final long serialVersionUID = 1L;
    private String selected;

    @Override
    public String getElementAt(int index) {
      return source.bits.get(index);
    }

    @Override
    public String getSelectedItem() {
      return selected;
    }

    @Override
    public int getSize() {
      return source.bits.size();
    }

    public void listChanged(VariableListEvent event) {
      int oldSize = select.getItemCount();
      int newSize = source.bits.size();
      fireContentsChanged(this, 0, oldSize > newSize ? oldSize : newSize);
      if (!source.bits.contains(selected)) {
        selected = (newSize == 0 ? null : source.bits.get(0));
        select.setSelectedItem(selected);
      }
    }

    @Override
    public void setSelectedItem(Object value) {
      selected = (String)value;
    }
  }

  private VariableList source;
  private JLabel label = new JLabel();
  private JComboBox<String> select = new JComboBox<String>();
  private String prototypeValue = null;

  @SuppressWarnings("unchecked")
  public OutputSelector(AnalyzerModel model) {
    this.source = model.getOutputs();

    Model listModel = new Model();
    select.setModel(listModel);
    source.addVariableListListener(listModel);
  }

  public void addItemListener(ItemListener l) {
    select.addItemListener(l);
  }

  public JPanel createPanel() {
    JPanel ret = new JPanel();
    ret.setLayout(new BoxLayout(ret, BoxLayout.Y_AXIS));
    label.setAlignmentX(0.0f);
    select.setAlignmentX(0.0f);
    ret.add(label);
    ret.add(select);
    return ret;
  }

  public JComboBox<String> getComboBox() {
    return select;
  }

  public JLabel getLabel() {
    return label;
  }

  public String getSelectedOutput() {
    String value = (String)select.getSelectedItem();
    if (value != null && !source.bits.contains(value)) {
      if (source.bits.isEmpty()) {
        value = null;
      } else {
        value = source.bits.get(0);
      }
      select.setSelectedItem(value);
    }
    return value;
  }

  void localeChanged() {
    label.setText(S.get("outputSelectLabel"));
  }

  public void removeItemListener(ItemListener l) {
    select.removeItemListener(l);
  }
}
