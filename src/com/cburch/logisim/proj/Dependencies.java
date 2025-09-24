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

package com.cburch.logisim.proj;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.util.Dag;

public class Dependencies {
  private class MyListener implements LibraryListener, CircuitListener {
    public void circuitChanged(CircuitEvent e) {
      Component comp;
      switch (e.getAction()) {
      case CircuitEvent.ACTION_ADD:
        comp = (Component) e.getData();
        if (comp.getFactory() instanceof SubcircuitFactory) {
          SubcircuitFactory factory = (SubcircuitFactory) comp.getFactory();
          dag.addEdge(e.getCircuit(), factory.getSubcircuit());
        } else if (comp.getFactory() instanceof VhdlEntity) {
          VhdlEntity factory = (VhdlEntity) comp .getFactory();
          dag.addEdge(e.getCircuit(), factory.getContent());
        }
        break;
      case CircuitEvent.ACTION_REMOVE:
        comp = (Component) e.getData();
        if (comp.getFactory() instanceof SubcircuitFactory) {
          SubcircuitFactory factory = (SubcircuitFactory) comp.getFactory();
          boolean found = false;
          for (Component o : e.getCircuit().getNonWires()) {
            if (o.getFactory() == factory) {
              found = true;
              break;
            }
          }
          if (!found)
            dag.removeEdge(e.getCircuit(), factory.getSubcircuit());
        } else if (comp.getFactory() instanceof VhdlEntity) {
          VhdlEntity factory = (VhdlEntity)comp.getFactory();
          boolean found = false;
          for (Component o : e.getCircuit().getNonWires()) {
            if (o.getFactory() == factory) {
              found = true;
              break;
            }
          }
          if (!found)
            dag.removeEdge(e.getCircuit(), factory.getContent());
        }
        break;
      }
    }

    public void libraryChanged(LibraryEvent e) {
      switch (e.getAction()) {
      case LibraryEvent.ADD_TOOL:
        if (e.getData() instanceof AddTool) {
          ComponentFactory factory = ((AddTool) e.getData())
              .getFactory();
          if (factory instanceof SubcircuitFactory) {
            SubcircuitFactory circFact = (SubcircuitFactory) factory;
            processCircuit(circFact.getSubcircuit());
          }
          /* nothing to do for vhdl */
        }
        break;
      case LibraryEvent.REMOVE_TOOL:
        if (e.getData() instanceof AddTool) {
          ComponentFactory factory = ((AddTool) e.getData()).getFactory();
          if (factory instanceof SubcircuitFactory) {
            SubcircuitFactory circFact = (SubcircuitFactory) factory;
            Circuit circ = circFact.getSubcircuit();
            dag.removeNode(circ);
            circ.removeCircuitWeakListener(null, this);
          } else if (factory instanceof VhdlEntity) {
            VhdlEntity circFact = (VhdlEntity)factory;
            dag.removeNode(circFact.getContent());
          }
        }
        break;
      }
    }
  }

  private MyListener myListener = new MyListener();
  private Dag dag = new Dag();

  Dependencies(LogisimFile file) {
    addDependencies(file);
  }

  private void addDependencies(LogisimFile file) {
    file.addLibraryWeakListener(null, myListener);
    for (Circuit circuit : file.getCircuits()) {
      processCircuit(circuit);
    }
  }

  public boolean canAdd(Circuit circ, Circuit sub) {
    return dag.canFollow(sub, circ);
  }

  public boolean canRemove(Circuit circ) {
    return !dag.hasPredecessors(circ);
  }

  public List<String> reasonsNotToRemove(Circuit circ) {
    List<Object> preds = dag.getPredecessors(circ);
    ArrayList<String> ret = new ArrayList<>();
    for (Object o : preds)
      ret.add(o.toString());
    return ret;
  }

  public boolean canRemove(VhdlContent vhdl) {
    return !dag.hasPredecessors(vhdl);
  }

  private void processCircuit(Circuit circ) {
    circ.addCircuitWeakListener(null, myListener);
    for (Component comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof SubcircuitFactory) {
        SubcircuitFactory factory = (SubcircuitFactory) comp
            .getFactory();
        dag.addEdge(circ, factory.getSubcircuit());
      } else if (comp.getFactory() instanceof VhdlEntity) {
        VhdlEntity factory = (VhdlEntity) comp .getFactory();
        dag.addEdge(circ, factory.getContent());
      }
    }
  }

}
