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

package com.cburch.logisim.circuit.appear;
import static com.cburch.logisim.circuit.Strings.S;

import java.awt.Window;

import com.cburch.draw.gui.CanvasBoundAttribute;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.appear.AppearanceCanvas;
import com.cburch.logisim.gui.appear.DynamicConditionDialog;
import com.cburch.logisim.util.StringGetter;

public class DynamicConditionAttribute extends CanvasBoundAttribute<DynamicCondition> {

  // path > val    ... only true when path data is fully defined without errors
  // path < val    ... only true when path data is fully defined without errors
  // path == val    ... only true when path data is fully defined without errors
  // path != val    ... only true when path data is fully defined without errors
  // path is error  ... when path data has one or more error bits
  // path is undefined   ... when path data has one or more undefined bits, but no errors
  // path is defined .. when path has no error or undefined bits

  public DynamicConditionAttribute(String name, StringGetter disp) {
    super(name, disp);
  }

  @Override
  public DynamicCondition parse(String value) {
    // This only gets called in cases where a cell editor returns a String, but
    // our cell editor only returns DyanamicCondition objects.
    throw new UnsupportedOperationException("DynamicCondition.parse() not implemented");
  }

  @Override
  public java.awt.Component getCellEditor(Window parent, AppearanceCanvas canvas, DynamicCondition value) {
    Circuit circuit = canvas.getCircuit();
    return DynamicConditionDialog.makeDialog(parent, circuit, value);
  }

  @Override
  public String toDisplayString(DynamicCondition value) {
    return value == null ? DynamicCondition.NONE.toDisplayString() : value.toDisplayString();
  }

  @Override
  public Domain getDomain() {
    return Domain.ofDescription("expression of the form: path > val, path < val, path == val, path != val, path is error, path is undefined, path is defined; where path is a slash-delineated component path within the circuit");
  }

}
