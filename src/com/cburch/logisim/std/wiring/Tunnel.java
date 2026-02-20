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

package com.cburch.logisim.std.wiring;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;

public class Tunnel extends InstanceFactory {
  public static final Tunnel FACTORY = new Tunnel();

  static final int TEXT_MARGIN = 3; // average space around text (unevenly distributed)
  static final int ARROW_DEPTH = 4; // point to text-box distance
  static final int ARROW_MARGIN = 5; // point to label distancee
  static final int ARROW_MIN_WIDTH = 16; // e.g. 16 wide upward facing even for short label
  static final int ARROW_MAX_WIDTH = 20; // e.g. 20 wide upward facing even for long label
  
  // We enforce that the label bounding box is at least NxN,
  // so that with the added margin, there's enough room for
  // a minimum-width arrow to attach to it cleanly.
  static final int MIN_TEXTBOX_DIMENSION = ARROW_MIN_WIDTH - 2 * TEXT_MARGIN;

  //  |<--->|  arrow depth
  //  |<------>| arrow margin
  //       .------------------------------.
  //      / |                             | margin 3
  //     /  |  +--------------------+     |
  //    /   |  |     WEST-FACING    |     |
  //   /    |  |                    |     |
  //  o     |  x=anchor             |     | label height
  //   \    |  |                    |     |
  //    \   |  |       LABEL        |     |
  //     \  |  +--------------------+     |
  //      \ |                             | margin 3
  //       '------------------------------'
  //        margin     label width    margin
  //          1                         5

  public Tunnel() {
    super("Tunnel", S.getter("tunnelComponent"));
    setIconName("tunnel.gif");
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
  }

  private void configureLabel(Instance instance) {
    TunnelAttributes attrs = (TunnelAttributes) instance.getAttributeSet();
    Location loc = instance.getLocation();
    instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT,
        loc.getX() + attrs.getLabelAnchorXOffset(),
        loc.getY() + attrs.getLabelAnchorYOffset(),
        attrs.getLabelHAlign(), attrs.getLabelVAlign());
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.setPorts(new Port[] { new Port(0, 0, Port.INOUT, StdAttr.WIDTH) });
    configureLabel(instance);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new TunnelAttributes();
  }

  @Override
  public Component createComponent(Location loc, AttributeSet attrs) {
    // See same fix in std.base.Text
    InstanceComponent ret = new InstanceComponent(this, loc, attrs) {
      @Override
      public boolean visiblyContains(Location pt, Graphics g) {
        return getVisibleBounds(g).contains(pt);
      }
      @Override
      public Bounds getVisibleBounds(Graphics g) {
        return ((Tunnel)getFactory()).getTunnelVisibleBounds(getLocation(), getAttributeSet(), g);
      }
    };
    configureNewInstance(ret.getInstance());
    return ret;
  }

  private Bounds getTunnelVisibleBounds(Location loc, AttributeSet attrsBase, Graphics g) { // visible
    return getVisibleOffsetBounds(attrsBase, g).translate(loc);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) { // nominal
    // This is only an estimate.
    // See same fix in std.base.Text  
    TunnelAttributes attrs = (TunnelAttributes) attrsBase;

    // find text nominal width and height
    Font font = attrs.getFont();
    String text = "ABC";
    Bounds t = StringUtil.estimateAlignedBounds(text, font, 0, 0);

    return getBoundsForTextbox(t.width, t.height, attrs.getFacing());
  }

  @Override
  public Bounds getVisibleOffsetBounds(AttributeSet attrsBase, Graphics g) { // visible
    // See same fix in std.base.Text  
    TunnelAttributes attrs = (TunnelAttributes) attrsBase;

    Font font = attrs.getFont();
    String text = attrs.getLabel();
    if (text == null)
      text = "";
    // Note: extra spaces provide font-size-aware padding on left and right
    // Rectangle r = GraphicsUtil.getTextBounds(g, font, " "+text+" ", 0, 0, 0, 0);
    Rectangle r = GraphicsUtil.getTextBounds(GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT, font, " "+text+" ", 0, 0, 0, 0);

    return getBoundsForTextbox(r.width, r.height, attrs.getFacing());
  }

  private static Bounds getBoundsForTextbox(int tw, int th, Direction facing) {
    int w = Math.max(MIN_TEXTBOX_DIMENSION, tw) + 2*TEXT_MARGIN;
    int h = Math.max(MIN_TEXTBOX_DIMENSION, th) + 2*TEXT_MARGIN;

    // add space for arrow, and for margins around text
    int A = ARROW_MARGIN - ARROW_DEPTH;
    if (facing == Direction.WEST)
      return Bounds.create(0, -h/2, (A + w), h);
    else if (facing == Direction.EAST)
      return Bounds.create(-(w + A), -h/2, (w + A), h);
    else if (facing == Direction.NORTH)
      return Bounds.create(-w/2, 0, w, (A + h));
    else // SOUTH
      return Bounds.create(-w/2, -(h + A), w, (h + A));
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      configureLabel(instance);
      instance.recomputeBounds();
    } else if (attr == StdAttr.LABEL || attr == StdAttr.LABEL_FONT) {
      instance.recomputeBounds();
    }
  }
    
  @Override
  public void paintGhost(InstancePainter painter) {
    
    Graphics2D g = painter.getGraphics();
    TunnelAttributes attrs = (TunnelAttributes) painter.getAttributeSet();
    Direction facing = attrs.getFacing();
    String label = attrs.getLabel();
    Font font = attrs.getFont();
     
    int tx = attrs.getLabelAnchorXOffset();
    int ty = attrs.getLabelAnchorYOffset();
    int halign = attrs.getLabelHAlign();
    int valign = attrs.getLabelVAlign();
    // Note: extra spaces provide font-size-aware padding on left and right
    GraphicsUtil.drawText(g, font, " "+label+" ", tx, ty, halign, valign);
    
    Bounds bds = getVisibleOffsetBounds(attrs, g);

    int x0 = bds.getX();
    int y0 = bds.getY();
    int x1 = x0 + bds.getWidth();
    int y1 = y0 + bds.getHeight();
    int mw = ARROW_MAX_WIDTH / 2;
    int[] xp;
    int[] yp;
    if (facing == Direction.NORTH) {
      int yb = y0 + ARROW_DEPTH;
      if (x1 - x0 <= ARROW_MAX_WIDTH) {
        xp = new int[] { x0, 0, x1, x1, x0 };
        yp = new int[] { yb, y0, yb, y1, y1 };
      } else {
        xp = new int[] { x0, -mw, 0, mw, x1, x1, x0 };
        yp = new int[] { yb, yb, y0, yb, yb, y1, y1 };
      }
    } else if (facing == Direction.SOUTH) {
      int yb = y1 - ARROW_DEPTH;
      if (x1 - x0 <= ARROW_MAX_WIDTH) {
        xp = new int[] { x0, x1, x1, 0, x0 };
        yp = new int[] { y0, y0, yb, y1, yb };
      } else {
        xp = new int[] { x0, x1, x1, mw, 0, -mw, x0 };
        yp = new int[] { y0, y0, yb, yb, y1, yb, yb };
      }
    } else if (facing == Direction.EAST) {
      int xb = x1 - ARROW_DEPTH;
      if (y1 - y0 <= ARROW_MAX_WIDTH) {
        xp = new int[] { x0, xb, x1, xb, x0 };
        yp = new int[] { y0, y0, 0, y1, y1 };
      } else {
        xp = new int[] { x0, xb, xb, x1, xb, xb, x0 };
        yp = new int[] { y0, y0, -mw, 0, mw, y1, y1 };
      }
    } else {
      int xb = x0 + ARROW_DEPTH;
      if (y1 - y0 <= ARROW_MAX_WIDTH) {
        xp = new int[] { xb, x1, x1, xb, x0 };
        yp = new int[] { y0, y0, y1, y1, 0 };
      } else {
        xp = new int[] { xb, x1, x1, xb, xb, x0, xb };
        yp = new int[] { y0, y0, y1, y1, mw, 0, -mw };
      }
    }
    GraphicsUtil.switchToWidth(g, 2);
    g.drawPolygon(xp, yp, xp.length);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Location loc = painter.getLocation();
    int x = loc.getX();
    int y = loc.getY();
    Graphics2D g = painter.getGraphics();
    g.translate(x, y);
    g.setColor(Color.BLACK);
    paintGhost(painter);
    g.translate(-x, -y);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    ; // nothing to do - handled by circuit
  }

}
