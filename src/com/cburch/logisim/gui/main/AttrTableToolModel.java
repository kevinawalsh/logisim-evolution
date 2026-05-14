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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.generic.AttrTableSetException;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;

public class AttrTableToolModel extends AttributeSetTableModel {
  Project proj;
  Tool tool;

  public AttrTableToolModel(Project proj, Tool tool) {
    super(tool.getAttributeSet());
    this.proj = proj;
    this.tool = tool;
  }

  @Override
  public String getTitle() {
    return S.fmt("toolAttrTitle", tool.getDisplayName());
  }

  @Override
  public String getEmptyHint() {
    return S.get("selectionNoAttrs");
  }

  public Tool getTool() {
    return tool;
  }

  private Circuit getCircuit() {
    if (!(tool instanceof AddTool))
      return null;
    ComponentFactory fact = ((AddTool)tool).getFactory();
    if (!(fact instanceof SubcircuitFactory))
      return null;
    return ((SubcircuitFactory)fact).getSubcircuit();
  }

  private VhdlContent getVhdlContent() {
    if (!(tool instanceof AddTool))
      return null;
    ComponentFactory fact = ((AddTool)tool).getFactory();
    if (!(fact instanceof VhdlEntity))
      return null;
    return ((VhdlEntity)fact).getContent();
  }

  @Override
  public boolean isRowValueEditable(int rowIndex) {
    if (!super.isRowValueEditable(rowIndex))
      return false;
    Attribute<?> attr = getRow(rowIndex).getAttribute();
    Circuit circ = getCircuit();
    if (circ != null) {
      return proj.getLogisimFile().contains(circ)
          || !circ.getStaticAttributes().containsAttribute(attr);
    }
    VhdlContent vhdl = getVhdlContent();
    if (vhdl != null) {
      return proj.getLogisimFile().contains(circ)
          || !vhdl.getStaticAttributes().containsAttribute(attr);
    }
    return true; // built-in tool, jar library tool, etc.
  }

  @Override
  public <V> void setValueRequested(Attribute<V> attr, V value)
      throws AttrTableSetException {
    // validate circuit name, label, and other attributes
    Circuit circ = getCircuit();
    VhdlContent vhdl = getVhdlContent();
    String err = null;
    if (attr == CircuitAttributes.CIRCUIT_NAME) {
      if (circ == null)
        return; // huh ?
      String name = ((String)value).trim();
      if (name.equals(circ.getName()))
        return; // no change
      err = ProjectCircuitActions.getNewNameErrors(
              proj.getLogisimFile(), name, false);
    } else if (attr == VhdlEntity.NAME_ATTR) {
      if (vhdl == null)
        return; // huh ?
      String name = ((String)value).trim();
      if (name.equals(vhdl.getName()))
        return; // no change
      err = ProjectCircuitActions.getNewNameErrors(
              proj.getLogisimFile(), name, true);
    }
    if (err != null)
      throw new AttrTableSetException(err);

    proj.doAction(ToolAttributeAction.create(tool, attr, value));
  }
}
