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

package com.cburch.draw.toolbar;

import java.util.List;
import java.awt.datatransfer.DataFlavor;

import javax.swing.JPopupMenu;

public interface ToolbarModel {
	public void addToolbarModelListener(ToolbarModelListener listener);

	public List<ToolbarItem> getItems();

	public boolean isSelected(ToolbarItem item);

	public void itemSelected(ToolbarItem item);

	public void removeToolbarModelListener(ToolbarModelListener listener);

  default public boolean supportsDragDrop() { return getAcceptedDataFlavor() != null; }
  default public DataFlavor getAcceptedDataFlavor() { return null; }
  default public boolean handleDrop(Object incoming, int pos) { return false; }
  default public boolean handleDragDrop(int oldPos, int newP) { return false; }
  // default public boolean isSameProject(Object incoming) { return false; }
  default public DataFlavor[] getPrecheckDataFlavors() { return null; }
  default public boolean dragPrecheck(String[] tokens) { return false; }

  default public JPopupMenu getPopupMenu() { return null; }
}
