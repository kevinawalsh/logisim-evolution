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

package com.cburch.logisim.instance;

import com.cburch.logisim.comp.ComponentState;

/* Design Notes on CircuitState.setData()/getData() (3 of 4)
 *
 * The InstanceData interface seems to serve no real purpose. It is part of the
 * tangled relationship between Component, Instance, and InstanceComponent.
 *
 * For instance-flavored components (those implemnted using InstanceFactory,
 * InstanceComponent, and all that), Instance.setData()/getData() have a
 * restriction that requires data to be InstanceData, not just any object. It
 * isn't clear what purpose this restriction serves, however, since these are
 * all thin wrappers around CircuitState.setData()/getData(), which accepts any
 * ComponentState, or any Object at all. And even instance-flavored components
 * can (and sometimes do) call CircuitState.setData()/getData() directly,
 * bypassing the restriction.
 *
 * See also:
 *   InstanceDataSingleton
 */
 public interface InstanceData extends ComponentState {
  // public Object clone(); // already in ComponentState
}
