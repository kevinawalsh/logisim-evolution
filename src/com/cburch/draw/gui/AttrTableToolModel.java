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

package com.cburch.draw.gui;

import java.awt.Window;

import com.cburch.draw.gui.CanvasBoundAttribute;
import com.cburch.draw.tools.AbstractTool;
import com.cburch.draw.tools.DrawingAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.appear.AppearanceCanvas;
import com.cburch.logisim.gui.generic.AttrTableSetException;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;

class AttrTableToolModel extends AttributeSetTableModel {
  private AppearanceCanvas canvas;
	private DrawingAttributeSet defaults;
	private AbstractTool currentTool;

	public AttrTableToolModel(AppearanceCanvas canvas, DrawingAttributeSet defaults, AbstractTool tool) {
		super(defaults.createSubset(tool));
    this.canvas = canvas;
		this.defaults = defaults;
		this.currentTool = tool;
	}

  @Override
  @SuppressWarnings("unchecked")
  public <V> java.awt.Component getCellEditor(Attribute<V> attr, Window parent, V value) {
    if (attr instanceof CanvasBoundAttribute)
      return ((CanvasBoundAttribute)attr).getCellEditor(parent, canvas, value);
    else
      return attr.getCellEditor(parent, value);
  }

	@Override
	public String getTitle() {
		return currentTool.getDescription();
	}

	public void setTool(AbstractTool value) {
		currentTool = value;
		setAttributeSet(defaults.createSubset(value));
		fireTitleChanged();
	}

	@Override
	public <V> void setValueRequested(Attribute<V> attr, V value)
			throws AttrTableSetException {
    // fixme: validate attr value first
		defaults.setAttr(attr, value);
	}
}
