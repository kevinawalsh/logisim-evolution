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
package com.cburch.logisim.std.memory;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Project;

class RomState extends MemState implements ComponentData.WithLifetimeTracking {

  // RomState holds simulation state for Rom, which is nothing beyond the basics
  // that MemState provides.

  RomState(Project proj, Instance inst, RomContents contents) {
    super(contents);
    contents.setProject(proj);
    contents.setRomInstance(inst);
  }

  private RomState(RomState other) {
    // no contents duplication: new state shares the same RomContents
    super(other.contents, other);
  }

  @Override
  public RomState duplicateForNewSimulation() {
    return new RomState(this);
  }

  @Override
  public void simulationRelocating(CircuitState cs, Component originalComp, Component replacementComp) {
    System.out.println("relocate");
    ((RomContents)contents).setProject(cs.getProject());
    ((RomContents)contents).setRomInstance(Instance.getInstanceFor(replacementComp));
  };

  @Override
  public void simulationCleanup(CircuitState cs, Component comp) {
    if (!cs.getCircuit().contains(comp))
      ((RomContents)contents).closeHexFrame(Instance.getInstanceFor(comp));
  }

}
