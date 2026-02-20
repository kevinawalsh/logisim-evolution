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

package com.cburch.draw.model;

import java.awt.Graphics2D;
import java.util.List;

import com.cburch.logisim.circuit.appear.DynamicCondition;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;

public interface CanvasObject extends AttributeSet, Cloneable {
	public abstract Handle canDeleteHandle(Location desired);

	public abstract Handle canInsertHandle(Location desired);

	public abstract boolean canMoveHandle(Handle handle);

	public abstract boolean canRemove();

	public abstract boolean contains(Location loc, boolean assumeFilled);

	public Handle deleteHandle(Handle handle);

	// public abstract AttributeSet getAttributeSet();

	public abstract Bounds getBounds();

	public abstract String getDisplayName();

	public abstract String getDisplayNameAndLabel();

	public abstract List<Handle> getHandles(HandleGesture gesture);

	public void insertHandle(Handle desired, Handle previous);

	public abstract boolean matches(CanvasObject other);

	public abstract int matchesHashCode();

	public Handle moveHandle(HandleGesture gesture);

	public abstract boolean overlaps(CanvasObject other);

	public abstract void paint(Graphics2D g, HandleGesture gesture);

	public void translate(int dx, int dy);

  public DynamicCondition getDynamicCondition();

  public void clearDynamicCondition();
}
