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

package com.cburch.logisim.tools.move;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Location;

public class MoveResult {
  private ConnectionPlan plan;
  private Collection<ConnectionData> unsatisfiedConnections;
  private Collection<Location> unconnectedLocations;
  private int totalDistance;

  public MoveResult(MoveRequest request, ConnectionPlan plan,
      Collection<ConnectionData> unsatisfiedConnections, int totalDistance) {
    this.plan = plan;
    this.unsatisfiedConnections = unsatisfiedConnections;
    this.totalDistance = totalDistance;

    ArrayList<Location> unconnected = new ArrayList<>();
    for (ConnectionData conn : unsatisfiedConnections) {
      unconnected.add(conn.getLocation());
    }
    unconnectedLocations = unconnected;
  }

  void addUnsatisfiedConnections(Collection<ConnectionData> toAdd) {
    unsatisfiedConnections.addAll(toAdd);
    for (ConnectionData conn : toAdd) {
      unconnectedLocations.add(conn.getLocation());
    }
  }

  public ConnectionPlan getConnectionPlan() {
    return plan;
  }

  int getTotalDistance() {
    return totalDistance;
  }

  public Collection<Location> getUnconnectedLocations() {
    return unconnectedLocations;
  }

  Collection<ConnectionData> getUnsatisifiedConnections() {
    return unsatisfiedConnections;
  }

  public Collection<Wire> getWiresToAdd() {
    Collection<Wire> ret = replacements.getWireAdditions();
    if (replacements.getAllAdditions().size() != ret.size())
      System.err.println("MISMATCH - old code would have returned garbage here?");
    return ret;
  }

  public void print(PrintStream out) {
    out.print("MoveResult: ");
    replacements.print(out);
  }
  public String toString() {
    return "MoveResult: " + replacements.toString();
  }
}
