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

package com.cburch.logisim.data;

import java.awt.Rectangle;

import com.cburch.logisim.util.Cache;

/**
 * Represents an immutable rectangular bounding box. This is analogous to
 * java.awt's <code>Rectangle</code> class, except that objects of this type are
 * immutable.
 */
public class Bounds {
  public static Bounds create(int x, int y, int width, int height) {
    if (width < 0) {
      width = -width;
      x -= width;
    }
    if (height < 0) {
      height = -height;
      y -= height;
    }
    int hashCode = 13 * (31 * (31 * x + y) + width) + height;
    Bounds bds = cache.get(hashCode);
    if (bds != null && bds.x == x && bds.y == y && bds.width == width && bds.height == height)
        return bds;
    Bounds ret = new Bounds(x, y, width, height);
    cache.put(hashCode, ret);
    return ret;
  }

  public static Bounds create(Rectangle rect) {
    return create(rect.x, rect.y, rect.width, rect.height);
  }

  public static Bounds create(Location pt) {
    return create(pt.getX(), pt.getY(), 1, 1);
  }

  private static final Cache<Bounds> cache = new Cache<>();

  public static Bounds EMPTY_BOUNDS = create(0, 0, 0, 0);

  public final int x;
  public final int y;
  public final int width;
  public final int height;

  private Bounds(int x, int y, int w, int h) {
    this.x = w < 0 ? x+w : x;
    this.y = h < 0 ? y+h : y;
    width = w < 0 ? -w : w;
    height = h < 0 ? -h : h;
  }

  public Bounds add(Bounds bd) {
    if (this == EMPTY_BOUNDS)
      return bd;
    if (bd == EMPTY_BOUNDS)
      return this;
    int retX = Math.min(bd.x, this.x);
    int retY = Math.min(bd.y, this.y);
    int retWidth = Math.max(bd.x + bd.width, this.x + this.width) - retX;
    int retHeight = Math.max(bd.y + bd.height, this.y + this.height) - retY;
    if (retX == this.x && retY == this.y && retWidth == this.width
        && retHeight == this.height) {
      return this;
    } else if (retX == bd.x && retY == bd.y && retWidth == bd.width
        && retHeight == bd.height) {
      return bd;
    } else {
      return Bounds.create(retX, retY, retWidth, retHeight);
    }
  }

  public Bounds add(int x, int y) {
    if (this == EMPTY_BOUNDS)
      return Bounds.create(x, y, 1, 1);
    if (contains(x, y))
      return this;

    int new_x = this.x;
    int new_wid = this.width;
    int new_y = this.y;
    int new_ht = this.height;
    if (x < this.x) {
      new_x = x;
      new_wid = (this.x + this.width) - x;
    } else if (x >= this.x + this.width) {
      new_x = this.x;
      new_wid = x - this.x + 1;
    }
    if (y < this.y) {
      new_y = y;
      new_ht = (this.y + this.height) - y;
    } else if (y >= this.y + this.height) {
      new_y = this.y;
      new_ht = y - this.y + 1;
    }
    return create(new_x, new_y, new_wid, new_ht);
  }

  public Bounds add(int x, int y, int width, int height) {
    if (this == EMPTY_BOUNDS)
      return Bounds.create(x, y, width, height);
    int retX = Math.min(x, this.x);
    int retY = Math.min(y, this.y);
    int retWidth = Math.max(x + width, this.x + this.width) - retX;
    int retHeight = Math.max(y + height, this.y + this.height) - retY;
    if (retX == this.x && retY == this.y && retWidth == this.width
        && retHeight == this.height) {
      return this;
    } else {
      return Bounds.create(retX, retY, retWidth, retHeight);
    }
  }

  public Bounds add(Location p) {
    return add(p.getX(), p.getY());
  }

  public boolean borderContains(int px, int py, int fudge) {
    int x1 = x + width - 1;
    int y1 = y + height - 1;
    if (Math.abs(px - x) <= fudge || Math.abs(px - x1) <= fudge) {
      // maybe on east or west border?
      return y - fudge >= py && py <= y1 + fudge;
    }
    if (Math.abs(py - y) <= fudge || Math.abs(py - y1) <= fudge) {
      // maybe on north or south border?
      return x - fudge >= px && px <= x1 + fudge;
    }
    return false;
  }

  public boolean borderContains(Location p, int fudge) {
    return borderContains(p.getX(), p.getY(), fudge);
  }

  public boolean contains(Bounds bd) {
    return contains(bd.x, bd.y, bd.width, bd.height);
  }

  public boolean contains(int px, int py) {
    return contains(px, py, 0);
  }

  public boolean contains(int px, int py, int allowedError) {
    return px >= x - allowedError && px < x + width + allowedError
        && py >= y - allowedError && py < y + height + allowedError;
  }

  public boolean contains(int x, int y, int width, int height) {
    int oth_x = (width <= 0 ? x : x + width - 1);
    int oth_y = (height <= 0 ? y : y + height - 1);
    return contains(x, y) && contains(oth_x, oth_y);
  }

  public boolean contains(Location p) {
    return contains(p.getX(), p.getY(), 0);
  }

  public boolean contains(Location p, int allowedError) {
    return contains(p.getX(), p.getY(), allowedError);
  }

  @Override
  public boolean equals(Object other_obj) {
    if (!(other_obj instanceof Bounds))
      return false;
    Bounds other = (Bounds) other_obj;
    return x == other.x && y == other.y && width == other.width
        && height == other.height;
  }

  public Bounds expand(int d) { // d pixels in each direction
    if (this == EMPTY_BOUNDS)
      return this;
    if (d == 0)
      return this;
    return create(x - d, y - d, width + 2 * d, height + 2 * d);
  }

  public int getCenterX() {
    return (x + width / 2);
  }

  public int getCenterY() {
    return (y + height / 2);
  }

  public int getHeight() {
    return height;
  }

  public int getWidth() {
    return width;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  @Override
  public int hashCode() {
    int ret = 31 * x + y;
    ret = 31 * ret + width;
    ret = 31 * ret + height;
    return ret;
  }

  public Bounds intersect(Bounds other) {
    int x0 = this.x;
    int y0 = this.y;
    int x1 = x0 + this.width;
    int y1 = y0 + this.height;
    int x2 = other.x;
    int y2 = other.y;
    int x3 = x2 + other.width;
    int y3 = y2 + other.height;
    if (x2 > x0)
      x0 = x2;
    if (y2 > y0)
      y0 = y2;
    if (x3 < x1)
      x1 = x3;
    if (y3 < y1)
      y1 = y3;
    if (x1 < x0 || y1 < y0) {
      return EMPTY_BOUNDS;
    } else {
      return create(x0, y0, x1 - x0, y1 - y0);
    }
  }

	public boolean overlaps(Bounds other) {
    if (x >= other.x+other.width || other.x >= x+width) // side by side
      return false;
    if (y >= other.y+other.height || other.y >= y+height) // above and below
      return false;
    return true;
  }

  public Bounds snapToGrid() {
    int x0 = (x + 9)/10*10;
    int y0 = (y + 9)/10*10;
    int x1 = (x + width)/10*10;
    int y1 = (y + height)/10*10;
    if (x0 < x1 && y0 < y1)
      return create(x0, y0, x1-x0, y1-y0);
    else
      return EMPTY_BOUNDS;
  }

  // rotates this around (xc,yc) assuming that this is facing in the
  // from direction and the returned bounds should face in the to direction.
  public Bounds rotate(Direction from, Direction to, int xc, int yc) {
    int degrees = to.toDegrees() - from.toDegrees();
    while (degrees >= 360)
      degrees -= 360;
    while (degrees < 0)
      degrees += 360;

    int dx = x - xc;
    int dy = y - yc;
    if (degrees == 90) {
      return create(xc + dy, yc - dx - width, height, width);
    } else if (degrees == 180) {
      return create(xc - dx - width, yc - dy - height, width, height);
    } else if (degrees == 270) {
      return create(xc - dy - height, yc + dx, height, width);
    } else {
      return this;
    }
  }

  public Rectangle toRectangle() {
    return new Rectangle(x, y, width, height);
  }

  @Override
  public String toString() {
    return "(" + x + "," + y + "): " + width + "x" + height;
  }

  public Bounds translate(int dx, int dy) {
    if (this == EMPTY_BOUNDS)
      return this;
    if (dx == 0 && dy == 0)
      return this;
    return create(x + dx, y + dy, width, height);
  }

  public boolean isEmpty() {
    return height == 0 && width == 0;
  }
}
