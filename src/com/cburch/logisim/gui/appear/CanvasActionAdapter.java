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

package com.cburch.logisim.gui.appear;

import java.util.HashSet;
import java.util.Set;

import com.cburch.draw.actions.ModelAction;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.undo.Action;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutator;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.appear.AppearanceElement;
import com.cburch.logisim.proj.Project;

public class CanvasActionAdapter extends com.cburch.logisim.proj.Action {

  private class ActionTransaction extends CircuitTransaction {
    private boolean forward;

    ActionTransaction(boolean forward) {
      this.forward = forward;
    }

    @Override
    protected Set<Circuit> getAccessedCircuits() {
      return new HashSet<>(circuit.getCircuitsUsingThis());
    }

    @Override
    protected void run(CircuitMutator mutator) {
      if (forward) {
        canvasAction.doIt();
      } else {
        canvasAction.undo();
        if (wasDefault)
          circuit.getAppearance().setDefaultAppearance(true);
      }
    }
  }

  private Circuit circuit;
  private Action canvasAction;

  private boolean wasDefault;

  public CanvasActionAdapter(Circuit circuit, Action action) {
    this.circuit = circuit;
    this.canvasAction = action;
  }

  private boolean affectsPorts() {
    if (canvasAction instanceof ModelAction) {
      for (CanvasObject o : ((ModelAction) canvasAction).getObjects()) {
        if (o instanceof AppearanceElement) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void doIt(Project proj) {
    wasDefault = circuit.getAppearance().isDefaultAppearance();
    if (affectsPorts() || wasDefault) {
      ActionTransaction xn = new ActionTransaction(true);
      xn.execute();
    } else {
      canvasAction.doIt();
    }
  }

  @Override
  public String getName() {
    return canvasAction.getName();
  }

  @Override
  public void undo(Project proj) {
    if (affectsPorts() || wasDefault) {
      ActionTransaction xn = new ActionTransaction(false);
      xn.execute();
    } else {
      canvasAction.undo();
    }
  }
}
