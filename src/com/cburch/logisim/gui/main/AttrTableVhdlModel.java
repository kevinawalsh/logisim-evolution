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

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.generic.AttrTableSetException;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;

public class AttrTableVhdlModel extends AttributeSetTableModel {
  private Project proj;
  private VhdlContent vhdl;

  public AttrTableVhdlModel(Project proj, VhdlContent vhdl) {
    super(vhdl.getStaticAttributes());
    this.proj = proj;
    this.vhdl = vhdl;
  }

  @Override
  public String getTitle() {
    return S.fmt("vhdlAttrTitle", vhdl.getName());
  }

  @Override
  public boolean isRowValueEditable(int rowIndex) {
    // hdl outside current project can't be modified at all
    return super.isRowValueEditable(rowIndex) &&
      proj.getLogisimFile().contains(vhdl);
  }

  @Override
  public <V> void setValueRequested(Attribute<V> attr, V value)
      throws AttrTableSetException {
    if (!proj.getLogisimFile().contains(vhdl)) {
      // This should never happen, prevented by isRowValueEditable().
      String msg = S.get("cannotModifyVhdlError");
      throw new AttrTableSetException(msg);
    }
    String err = null;
    // validate vhdl name, label, and other attributes
    if (attr == VhdlEntity.NAME_ATTR) {
      String name = ((String)value).trim();
      if (name.equals(vhdl.getName()))
        return;
      err = ProjectCircuitActions.getNewNameErrors(
              proj.getLogisimFile(), name, false);
    }
    if (err != null)
      throw new AttrTableSetException(err);

    CircuitMutation xn = CircuitMutation.forVhdl(vhdl);
    xn.setForVhdl(attr, value);
    proj.doAction(xn.toAction(S.getter("changeVhdlAttrAction")));
  }
}
