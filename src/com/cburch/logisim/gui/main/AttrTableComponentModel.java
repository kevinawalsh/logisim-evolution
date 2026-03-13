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
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.generic.AttrTableSetException;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.SetAttributeAction;

class AttrTableComponentModel extends AttributeSetTableModel {
  Project proj;
  Circuit circ;
  Component comp;

  AttrTableComponentModel(Project proj, Circuit circ, Component comp) {
    super(comp.getAttributeSet());
    this.proj = proj;
    this.circ = circ;
    this.comp = comp;
  }

  public Circuit getCircuit() {
    return circ;
  }

  public Component getComponent() {
    return comp;
  }

  @Override
  public String getTitle() {
    return comp.getDisplayName();
  }

  @Override
  public <V> void setValueRequested(Attribute<V> attr, V value)
      throws AttrTableSetException {
    if (!proj.getLogisimFile().contains(circ)) {
      String msg = S.get("cannotModifyCircuitError");
      throw new AttrTableSetException(msg);
    }
    String err = null;
    // validate circuit name, label, and other attributes
    if (attr == CircuitAttributes.CIRCUIT_NAME) {
      String name = ((String)value).trim();
      if (name.equals(circ.getName()))
        return;
      err = ProjectCircuitActions.getNewNameErrors(
              proj.getLogisimFile(), name, false);
    }
    if (err != null) {
      System.err.println("new name err: " + err);
      // try { throw new Exception(); } catch (Exception e) { e.printStackTrace(); }
      throw new AttrTableSetException(err);
    }
    SetAttributeAction act = new SetAttributeAction(circ, S.getter("changeAttributeAction"));
    act.set(comp, attr, value);
    proj.doAction(act);
  }
}
