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

package com.cburch.logisim.gui.generic;

/**
 * Code taken from Cornell's version of Logisim:
 * http://www.cs.cornell.edu/courses/cs3410/2015sp/
 */
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.hdl.HdlModelListener;
import com.cburch.hdl.HdlModel;

public class ProjectExplorerToolNode extends ProjectExplorerModel.Node<Tool>
  implements CircuitListener, HdlModelListener {

  private static final long serialVersionUID = 1L;
  private Circuit circuit;
  private VhdlContent vhdl;

  public ProjectExplorerToolNode(ProjectExplorerModel model, Tool tool, ProjectExplorerModel.Node<?> parent) {
    super(model, tool, parent);
    if (tool instanceof AddTool) {
      // force load = false, to avoid triggering source load for all tools on startup
      Object factory = ((AddTool) tool).getFactory(false);
      if (factory instanceof SubcircuitFactory) {
        circuit = ((SubcircuitFactory) factory).getSubcircuit();
        circuit.addCircuitWeakListener(null, this);
      } else if (factory instanceof VhdlEntity) {
        vhdl = ((VhdlEntity) factory).getContent();
        vhdl.addHdlModelWeakListener(null, this);
      }
    }
  }

  public void circuitChanged(CircuitEvent event) {
    int act = event.getAction();
    if (act == CircuitEvent.ACTION_SET_NAME || act == CircuitEvent.ACTION_DISPLAY_CHANGE)
      fireAppearanceChanged();
  }

  @Override
  public void contentSet(HdlModel model) {
    fireAppearanceChanged();
  }

  @Override
  public void aboutToSave(HdlModel source) { }

  @Override
  public void displayChanged(HdlModel source) {
    fireAppearanceChanged();
  }

  @Override
  public void appearanceChanged(HdlModel source) { }

  @Override
  protected void decommission() {
    if (circuit != null) {
      circuit.removeCircuitWeakListener(null, this);
    }
    if (vhdl != null) {
      vhdl.removeHdlModelWeakListener(null, this);
    }
  }

  @Override
  public boolean getAllowsChildren() { return false; }

}
