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

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Random;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.draw.shapes.DrawAttr;
import com.cburch.logisim.circuit.appear.DynamicCondition;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.GraphicsUtil;

public abstract class AbstractCanvasObject extends AbstractDrawingAttributeSet
  implements CanvasObject {
	private static final int OVERLAP_TRIES = 50;
	private static final int GENERATE_RANDOM_TRIES = 20;

	public AbstractCanvasObject() { }

	public Handle canDeleteHandle(Location loc) {
		return null;
	}

	public Handle canInsertHandle(Location desired) {
		return null;
	}

	public boolean canMoveHandle(Handle handle) {
		return false;
	}

	public boolean canRemove() {
		return true;
	}

	public abstract boolean contains(Location loc, boolean assumeFilled);

	public Handle deleteHandle(Handle handle) {
		throw new UnsupportedOperationException("deleteHandle");
	}

	public abstract Bounds getBounds();

	public abstract String getDisplayName();

	public String getDisplayNameAndLabel() {
		return getDisplayName();
	}

	public abstract List<Handle> getHandles(HandleGesture gesture);

	protected Location getRandomPoint(Bounds bds, Random rand) {
		int x = bds.getX();
		int y = bds.getY();
		int w = bds.getWidth();
		int h = bds.getHeight();
		for (int i = 0; i < GENERATE_RANDOM_TRIES; i++) {
			Location loc = Location.create(x + rand.nextInt(w),
					y + rand.nextInt(h));
			if (contains(loc, false))
				return loc;
		}
		return null;
	}

	public void insertHandle(Handle desired, Handle previous) {
		throw new UnsupportedOperationException("insertHandle");
	}

	public abstract boolean matches(CanvasObject other);

	public abstract int matchesHashCode();

	public Handle moveHandle(HandleGesture gesture) {
		throw new UnsupportedOperationException("moveHandle");
	}

	public boolean overlaps(CanvasObject other) {
		Bounds a = this.getBounds();
		Bounds b = other.getBounds();
		Bounds c = a.intersect(b);
		Random rand = new Random();
		if (c.getWidth() == 0 || c.getHeight() == 0) {
			return false;
		} else if (other instanceof AbstractCanvasObject) {
			AbstractCanvasObject that = (AbstractCanvasObject) other;
			for (int i = 0; i < OVERLAP_TRIES; i++) {
				if (i % 2 == 0) {
					Location loc = this.getRandomPoint(c, rand);
					if (loc != null && that.contains(loc, false))
						return true;
				} else {
					Location loc = that.getRandomPoint(c, rand);
					if (loc != null && this.contains(loc, false))
						return true;
				}
			}
			return false;
		} else {
			for (int i = 0; i < OVERLAP_TRIES; i++) {
				Location loc = this.getRandomPoint(c, rand);
				if (loc != null && other.contains(loc, false))
					return true;
			}
			return false;
		}
	}

	public abstract void paint(Graphics2D g, HandleGesture gesture);

	protected boolean setForFill(Graphics2D g) {
		if (containsAttribute(DrawAttr.PAINT_TYPE)) {
			Object value = getValue(DrawAttr.PAINT_TYPE);
			if (value == DrawAttr.PAINT_STROKE)
				return false;
		}

		Color color = getValue(DrawAttr.FILL_COLOR);
		if (color != null && color.getAlpha() == 0) {
			return false;
		} else {
			if (color != null)
				g.setColor(color);
			return true;
		}
	}

	protected boolean setForStroke(Graphics2D g) {
		if (containsAttribute(DrawAttr.PAINT_TYPE)) {
			Object value = getValue(DrawAttr.PAINT_TYPE);
			if (value == DrawAttr.PAINT_FILL)
				return false;
		}

		Integer width = getValue(DrawAttr.STROKE_WIDTH);
		if (width != null && width.intValue() > 0) {
			Color color = getValue(DrawAttr.STROKE_COLOR);
			if (color != null && color.getAlpha() == 0) {
				return false;
			} else {
				GraphicsUtil.switchToWidth(g, width.intValue());
				if (color != null)
					g.setColor(color);
				return true;
			}
		} else {
			return false;
		}
	}

	public abstract Element toSvgElement(Document doc);

	public Element toDynamicSvgElement(Document doc) {
    Element elt = toSvgElement(doc);
    if (elt == null)
      return null;
    DynamicCondition dyn = getValue(DrawAttr.DYNAMIC_CONDITION);
    if (dyn != null && dyn != DynamicCondition.NONE)
      elt.setAttribute("visibility", dyn.toSvgString());
    return elt;
  }

	public abstract void translate(int dx, int dy);

  @Override
  public DynamicCondition getDynamicCondition() {
    DynamicCondition dyn = getValue(DrawAttr.DYNAMIC_CONDITION);
    return (dyn == DynamicCondition.NONE) ? null : dyn;
  }

  @Override
  public void clearDynamicCondition() {
    updateAttr(DrawAttr.DYNAMIC_CONDITION, null);
  }
}
