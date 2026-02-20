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

package com.cburch.draw.shapes;
import static com.cburch.draw.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.model.Handle;
import com.cburch.draw.model.HandleGesture;
import com.cburch.draw.util.EditableLabel;
import com.cburch.logisim.circuit.appear.DynamicCondition;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.UnmodifiableList;

public class Text extends AbstractCanvasObject {
	private EditableLabel label;
  private DynamicCondition visibility;

	private Text(int x, int y, int halign, int valign, String text, Font font,
			Color color) {
		label = new EditableLabel(x, y, text, font);
		label.setColor(color);
		label.setHorizontalAlignment(halign);
		label.setVerticalAlignment(valign);
	}

	public Text(int x, int y, String text) {
		this(x, y, EditableLabel.LEFT, EditableLabel.BASELINE, text,
				DrawAttr.DEFAULT_FONT, Color.BLACK);
	}

	@Override
	public Text clone() {
		Text ret = (Text) super.clone();
		ret.label = this.label.clone();
		return ret;
	}

	@Override
	public boolean contains(Location loc, boolean assumeFilled) {
		return label.contains(loc.getX(), loc.getY());
	}

	@Override
	public List<Attribute<?>> getAttributes() {
		return DrawAttr.ATTRS_TEXT;
	}

	@Override
	public Bounds getBounds() {
		Bounds b = label.getBounds();
		// return Bounds.create(b.getX()-3, b.getY(), b.getWidth() + 6, b.getHeight());
		return b;
	}

	@Override
	public String getDisplayName() {
		return S.get("shapeText");
	}

	public List<Handle> getHandles() {
		Bounds bds = getBounds();
		int x = bds.getX();
		int y = bds.getY();
		int w = bds.getWidth();
		int h = bds.getHeight();
		return UnmodifiableList.create(new Handle[] { new Handle(this, x, y),
				new Handle(this, x + w, y), new Handle(this, x + w, y + h),
				new Handle(this, x, y + h) });
	}

	@Override
	public List<Handle> getHandles(HandleGesture gesture) {
		return getHandles();
	}

	public EditableLabel getLabel() {
		return label;
	}

	public Location getLocation() {
		return Location.create(label.getX(), label.getY());
	}

	public String getText() {
		return label.getText();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> V getValue(Attribute<V> attr) {
		if (attr == DrawAttr.FONT) {
			return (V) label.getFont();
		} else if (attr == DrawAttr.FILL_COLOR) {
			return (V) label.getColor();
		} else if (attr == DrawAttr.HALIGNMENT) {
			int halign = label.getHorizontalAlignment();
			if (halign == EditableLabel.LEFT)
				return (V) DrawAttr.HALIGN_LEFT;
			else if (halign == EditableLabel.RIGHT)
				return (V) DrawAttr.HALIGN_RIGHT;
			else
				return (V) DrawAttr.HALIGN_CENTER;
		} else if (attr == DrawAttr.VALIGNMENT) {
			int valign = label.getVerticalAlignment();
			if (valign == EditableLabel.TOP)
				return (V) DrawAttr.VALIGN_TOP;
			else if (valign == EditableLabel.BOTTOM)
				return (V) DrawAttr.VALIGN_BOTTOM;
			else if (valign == EditableLabel.BASELINE)
				return (V) DrawAttr.VALIGN_BASELINE;
			else
				return (V) DrawAttr.VALIGN_MIDDLE;
    } else if (attr == DrawAttr.DYNAMIC_CONDITION) {
			return (V) visibility;
		} else {
			return null;
		}
	}

	@Override
	public boolean matches(CanvasObject other) {
		if (other instanceof Text) {
			Text that = (Text) other;
			return this.label.equals(that.label) &&
          (this.visibility == null ? "" : this.visibility.toSvgString()).equals(
          (that.visibility == null ? "" : that.visibility.toSvgString()));
		} else {
			return false;
		}
	}

	@Override
	public int matchesHashCode() {
		int ret = label.hashCode();
    if (visibility != null)
      ret = ret * 31 + visibility.toSvgString().hashCode();
    return ret;
	}

	@Override
	public void paint(Graphics2D g, HandleGesture gesture) {
		label.paintLabel(g);
	}

	public void setText(String value) {
		label.setText(value);
	}

	@Override
	public Element toSvgElement(Document doc) {
		return SvgCreator.createText(doc, this);
	}

	@Override
	public void translate(int dx, int dy) {
		label.setLocation(label.getX() + dx, label.getY() + dy);
	}

	@Override
	public <V> void updateAttr(Attribute<V> attr, V value) {
		if (attr == DrawAttr.FONT) {
			label.setFont((Font) value);
		} else if (attr == DrawAttr.FILL_COLOR) {
			label.setColor((Color) value);
		} else if (attr == DrawAttr.HALIGNMENT) {
			Integer intVal = (Integer) ((AttributeOption) value).getValue();
			label.setHorizontalAlignment(intVal.intValue());
		} else if (attr == DrawAttr.VALIGNMENT) {
			Integer intVal = (Integer) ((AttributeOption) value).getValue();
			label.setVerticalAlignment(intVal.intValue());
    } else if (attr == DrawAttr.DYNAMIC_CONDITION) {
			visibility = (DynamicCondition) value;
		}
	}
}
