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

package com.cburch.draw.tools;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cburch.draw.model.AbstractDrawingAttributeSet;
import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.util.UnmodifiableList;

public class DrawingAttributeSet extends AbstractDrawingAttributeSet {

	private class Restriction extends AbstractAttributeSet implements AttributeListener {
		private AbstractTool tool;
		private List<Attribute<?>> selectedAttrs;
		private List<Attribute<?>> selectedView;

		Restriction(AbstractTool tool) {
			this.tool = tool;
			updateAttributes();
		}

		public void attributeListChanged(AttributeEvent e) {
			fireAttributeListChanged();
		}

		public void attributeValueChanged(AttributeEvent e) {
			if (selectedAttrs.contains(e.getAttribute())) {
				@SuppressWarnings("unchecked")
				Attribute<Object> attr = (Attribute<Object>) e.getAttribute();
				fireAttributeValueChanged(attr, e.getValue());
			}
			updateAttributes();
		}

		@Override
		protected void copyInto(AbstractAttributeSet dest) {
      System.out.println("warning: DrawingAttributeSet.Restrction.copyInto() seems wrong");
			DrawingAttributeSet.this.addAttributeWeakListener(null, this);
		}

		@Override
		public List<Attribute<?>> getAttributes() {
			return selectedView;
		}

		@Override
		public <V> V getValue(Attribute<V> attr) {
			return DrawingAttributeSet.this.getValue(attr);
		}

		@Override
		public <V> void updateAttr(Attribute<V> attr, V value) {
			DrawingAttributeSet.this.setAttr(attr, value);
			updateAttributes();
		}

		private void updateAttributes() {
			List<Attribute<?>> toolAttrs;
			if (tool == null) {
				toolAttrs = Collections.emptyList();
			} else {
				toolAttrs = tool.getAttributes();
			}
			if (!toolAttrs.equals(selectedAttrs)) {
				selectedAttrs = new ArrayList<Attribute<?>>(toolAttrs);
				selectedView = Collections.unmodifiableList(selectedAttrs);
				DrawingAttributeSet.this.addAttributeWeakListener(null, this);
				fireAttributeListChanged();
			}
		}
	}

  private static final Attribute<?>[] attrs_all = new Attribute<?>[] {
        DrawAttr.FONT,
        DrawAttr.HALIGNMENT,
        DrawAttr.VALIGNMENT,
        DrawAttr.PAINT_TYPE,
        DrawAttr.STROKE_WIDTH,
        DrawAttr.STROKE_COLOR,
        DrawAttr.FILL_COLOR,
        DrawAttr.TEXT_DEFAULT_FILL,
        DrawAttr.CORNER_RADIUS
  };
	private static final List<Attribute<?>> ATTRS_ALL = UnmodifiableList.create(attrs_all);
	private static final Object[] defaults_all = new Object[] {
        DrawAttr.DEFAULT_FONT,
        DrawAttr.HALIGN_CENTER,
        DrawAttr.VALIGN_MIDDLE,
        DrawAttr.PAINT_STROKE,
        Integer.valueOf(1),
        Color.BLACK,
        Color.WHITE,
        Color.BLACK,
        Integer.valueOf(10)
  };

	private Object[] values;

	public DrawingAttributeSet() {
		values = defaults_all; // defaults_all.clone();
	}

	public <E extends CanvasObject> E applyTo(E drawable) {
		AbstractCanvasObject d = (AbstractCanvasObject) drawable;
		// use a for(i...) loop since the attribute list may change as we go on
		for (int i = 0; i < d.getAttributes().size(); i++) {
			Attribute<?> attr = d.getAttributes().get(i);
			@SuppressWarnings("unchecked")
			Attribute<Object> a = (Attribute<Object>) attr;
			if (attr == DrawAttr.FILL_COLOR
					&& this.containsAttribute(DrawAttr.TEXT_DEFAULT_FILL)) {
				d.setAttr(a, this.getValue(DrawAttr.TEXT_DEFAULT_FILL));
			} else if (this.containsAttribute(a)) {
				Object value = this.getValue(a);
				d.setAttr(a, value);
			}
		}
		return drawable;
	}

	@Override
	public Object clone() {
    DrawingAttributeSet ret = (DrawingAttributeSet) super.clone();
    ret.values = (Object[])this.values.clone();
    return ret;
	}

	public AttributeSet createSubset(AbstractTool tool) {
		return new Restriction(tool);
	}

	public List<Attribute<?>> getAttributes() {
		return ATTRS_ALL;
	}

  @Override
  @SuppressWarnings("unchecked")
	public <V> V getValue(Attribute<V> attr) {
    for (int i = 0; i < attrs_all.length; i++) {
      if (attrs_all[i].equals(attr))
        return (V)values[i];
    }
		return null;
	}

  @Override
	public <V> void updateAttr(Attribute<V> attr, V value) {
    for (int i = 0; i < attrs_all.length; i++) {
      if (attrs_all[i].equals(attr)) {
        values[i] = value;
        break;
      }
    }
  }

  @Override
  public <V> void changeAttr(Attribute<V> attr, V value) {
    updateAttr(attr, value);
    fireAttributeValueChanged(attr, value);
    fireAttributeListChanged();
  }
}
