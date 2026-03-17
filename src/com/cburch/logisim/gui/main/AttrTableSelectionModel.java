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

import com.cburch.hdl.HdlModel;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.generic.AttrTableSetException;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.gui.main.Selection.Event;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.tools.SetAttributeAction;

class AttrTableSelectionModel extends AttributeSetTableModel
  implements Selection.Listener {
  private Project project;
  private Frame frame;

  public AttrTableSelectionModel(Project project, Frame frame) {
    super(frame.getCanvas().getSelection().getAttributeSet());
    this.project = project;
    this.frame = frame;
    frame.getCanvas().getSelection().addListener(this);
  }

  @Override
  public String getTitle() {
    ComponentFactory wireFactory = null;
    ComponentFactory factory = null;
    String label = null;
    Location loc = null;
    int factoryCount = 0;
    int totalCount = 0;
    boolean variousFound = false;

    Selection selection = frame.getCanvas().getSelection();
    for (Component comp : selection.getComponents()) {
      ComponentFactory fact = comp.getFactory();
      if (fact.equals(factory)) {
        factoryCount++;
      } else if (comp instanceof Wire) {
        wireFactory = fact;
        if (factory == null) {
          factoryCount++;
        }
      } else if (factory == null) {
        factory = fact;
        factoryCount = 1;
        label = comp.getAttributeSet().getValue(StdAttr.LABEL);
        loc = comp.getLocation();
      } else {
        variousFound = true;
      }
      if (!(comp instanceof Wire)) {
        totalCount++;
      }
    }

    if (factory == null) {
      factory = wireFactory;
    }

    if (variousFound) {
      return S.fmt("selectionVarious", "" + totalCount);
    } else if (factoryCount == 0) {
      String circName = frame.getCanvas().getCircuit().getName();
      return S.fmt("circuitAttrTitle", circName);
    } else if (factoryCount == 1 && label != null && label.length() > 0) {
      return S.fmt("selectionOne", factory.getDisplayName()) + " \"" + label + "\"";
    } else if (factoryCount == 1 && loc != null) {
      return S.fmt("selectionOne", factory.getDisplayName() + " " + loc);
    } else if (factoryCount == 1) {
      return S.fmt("selectionOne", factory.getDisplayName());
    } else {
      return S.fmt("selectionMultiple", factory.getDisplayName(), "" + factoryCount);
    }
  }

  public void selectionChanged(Event event) {
    fireTitleChanged();
    if (!frame.getEditorView().equals(Frame.EDIT_APPEARANCE)) {
      frame.setAttrTableModel(this);
      fireStructureChanged();
    }
  }

  @Override
  public boolean isRowValueEditable(int rowIndex) {
    Selection selection = frame.getCanvas().getSelection();
    Circuit circuit = frame.getCanvas().getCircuit();
    HdlModel hdl = frame.getHdlEditorView();
    if (selection.isEmpty() && circuit != null) {
      // Empty selection is really modifying the circuit, so just delegate to
      // that model. This is a little redundant with the check in
      // SelectionAtributes.isReadOnly().
      AttrTableCircuitModel circuitModel = new AttrTableCircuitModel(project, circuit);
      return circuitModel.isRowValueEditable(rowIndex);
    } else if (selection.isEmpty() && hdl != null && hdl instanceof VhdlContent) {
      AttrTableVhdlModel vhdlModel = new AttrTableVhdlModel(project, (VhdlContent)hdl);
      return vhdlModel.isRowValueEditable(rowIndex);
    } else {
      // Non-empty selection calls superclass, which ultimately relies on
      // SelectionAttributes.isReadOnly(attr), which should handle all cases:
      //  - can't edit if selecting within a non-project circuit
      //  - can't edit circuit name if selecting multiple circuits (b/c they
      //    must be unique)
      //  - can't edit vhdl name if selecting multiple vhdl (b/c they
      //    must be unique)
      //  - can't edit static attributes for non-project SubCircuit or
      //    VhdlEntity
      //  - can't edit if any component attribute was read-only
      return super.isRowValueEditable(rowIndex);
    }
  }

  @Override
  public <V> void setValueRequested(Attribute<V> attr, V value)
      throws AttrTableSetException {
    // We rely on isRowValueEditable() to filter out all non-editable cases.
    Selection selection = frame.getCanvas().getSelection();
    Circuit circuit = frame.getCanvas().getCircuit();
    HdlModel hdl = frame.getHdlEditorView();
    if (selection.isEmpty() && circuit != null) {
      AttrTableCircuitModel circuitModel = new AttrTableCircuitModel(project, circuit);
      circuitModel.setValueRequested(attr, value);
    } else if (selection.isEmpty() && hdl != null && hdl instanceof VhdlContent) {
      AttrTableVhdlModel vhdlModel = new AttrTableVhdlModel(project, (VhdlContent)hdl);
      vhdlModel.setValueRequested(attr, value);
    } else {
      // todo (idea): if attr = label, make each label unique by appending number
      SetAttributeAction act = new SetAttributeAction(circuit,
          S.getter("selectionAttributeAction"));
      for (Component comp : selection.getComponents()) {
        if (!(comp instanceof Wire)) {
          act.set(comp, attr, value);
        }
      }
      project.doAction(act);
    }
  }

}
