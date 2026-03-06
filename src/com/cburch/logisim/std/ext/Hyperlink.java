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

package com.cburch.logisim.std.ext;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.menu.HelpBroker;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.tools.Reshapable;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.util.GraphicsUtil;

public class Hyperlink extends InstanceFactory implements Reshapable {

  static final Attribute<String> ATTR_HREF =
      Attributes.forString("href", S.getter("extHyperlinkHref"));

  private static final int MIN_SIZE = 10;
  private static final int ARC = 10;  // rounded corner radius
  private static final int DEPTH = 3; // 3D shadow depth

  static final Attribute<Integer> ATTR_WIDTH = Attributes.forIntegerRange(
      "width", S.getter("extHyperlinkWidth"), MIN_SIZE, 10000);
  static final Attribute<Integer> ATTR_HEIGHT = Attributes.forIntegerRange(
      "height", S.getter("extHyperlinkHeight"), MIN_SIZE, 10000);

  public static class Poker extends InstancePoker {
    @Override
    public boolean init(InstanceState state, MouseEvent e) {
      String href = state.getAttributeValue(ATTR_HREF);
      return href != null && !href.isBlank();
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      String href = state.getAttributeValue(ATTR_HREF);
      if (href != null && !href.isBlank())
        HelpBroker.followLink(href, state.getProject());
    }
  }

  public Hyperlink() {
    super("Hyperlink", S.getter("hyperlinkComponent"));
    setAttributes(
        new Attribute[] {
          StdAttr.LABEL,
          StdAttr.LABEL_FONT,
          StdAttr.LABEL_COLOR,
          Text.BG_COLOR,
          ATTR_HREF,
          ATTR_WIDTH,
          ATTR_HEIGHT,
        },
        new Object[] {
          "Click Me!",
          StdAttr.DEFAULT_LABEL_FONT,
          Color.BLUE,
          new Color(0xDDDDDD),
          "https://wikipedia.org/wiki/Mark_Dean_(computer_scientist)",
          80,
          30,
        });
    setIconName("hyperlink.gif");
    setInstancePoker(Poker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.computeLabelTextField(Instance.AVOID_CENTER);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_WIDTH || attr == ATTR_HEIGHT) {
      instance.recomputeBounds();
    }
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int w = attrs.getValue(ATTR_WIDTH);
    int h = attrs.getValue(ATTR_HEIGHT);
    return Bounds.create(-w / 2, -h / 2, w, h);
  }

  // The resize handle is at the bottom-right corner of the component.
  private Location handleLocation(Component comp) {
    Bounds bds = comp.getNominalBounds();
    return Location.create(bds.getX() + bds.getWidth(), bds.getY() + bds.getHeight());
  }

  @Override
  public Collection<Location> getReshapeHandles(Component comp) {
    return List.of(handleLocation(comp));
  }

  private int[] calculateNewSize(Component comp, Location handle, int rdx, int rdy) {
    Location loc = comp.getLocation();
    // handle is at (loc.x + w/2, loc.y + h/2), dragging changes w and h
    int newRight  = handle.getX() + rdx;
    int newBottom = handle.getY() + rdy;
    int w = Math.max(MIN_SIZE, round10(newRight  - (loc.getX() - comp.getAttributeSet().getValue(ATTR_WIDTH)  / 2)));
    int h = Math.max(MIN_SIZE, round10(newBottom - (loc.getY() - comp.getAttributeSet().getValue(ATTR_HEIGHT) / 2)));
    return new int[] { w, h };
  }

  private static int round10(int v) {
    return Math.round(v / 10.0f) * 10;
  }

  @Override
  public void drawReshaping(InstancePainter painter, Location handle, int rdx, int rdy) {
    int[] size = calculateNewSize(painter.getInstance().getComponent(), handle, rdx, rdy);
    int w = size[0], h = size[1];
    Location loc = painter.getLocation();
    int x = loc.getX() - w / 2;
    int y = loc.getY() - h / 2;
    Graphics2D g = painter.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.GRAY);
    g.drawRoundRect(x, y, w - DEPTH, h - DEPTH, ARC, ARC);
    String label = painter.getAttributeValue(StdAttr.LABEL);
    if (label != null && !label.isEmpty()) {
      Font font = painter.getAttributeValue(StdAttr.LABEL_FONT);
      g.setFont(font);
      g.setColor(Color.DARK_GRAY);
      FontMetrics fm = g.getFontMetrics();
      int tx = x + (w - DEPTH - fm.stringWidth(label)) / 2;
      int ty = y + (h - DEPTH - fm.getHeight()) / 2 + fm.getAscent();
      g.drawString(label, tx, ty);
    }
  }

  @Override
  public void doReshapeAction(Project proj, Circuit circ, Component comp,
      Location handle, int rdx, int rdy) {
    int[] size = calculateNewSize(comp, handle, rdx, rdy);
    SetAttributeAction act = new SetAttributeAction(circ, S.getter("extHyperlinkReshape"));
    act.set(comp, ATTR_WIDTH,  size[0]);
    act.set(comp, ATTR_HEIGHT, size[1]);
    proj.doAction(act);
  }

  // --- ToolTipMaker and feature dispatch ---

  @Override
  protected Object getInstanceFeature(Instance instance, Object key) {
    if (key == ToolTipMaker.class)
      return (ToolTipMaker) (event) -> toolTipFor(instance.getAttributeSet());
    if (key == Reshapable.class)
      return this;
    return super.getInstanceFeature(instance, key);
  }

  private static String toolTipFor(AttributeSet attrs) {
    String href = attrs.getValue(ATTR_HREF);
    if (href == null || href.isBlank()) return null;
    if (href.startsWith("#"))
      return S.fmt("textClickToSwitch", htmlEscape(href.substring(1)));
    if (href.startsWith("logisim:"))
      return S.fmt("textClickToExample", htmlEscape(href.substring(8)));
    return S.fmt("textClickToBrowse", htmlEscape(href));
  }

  private static String htmlEscape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    paintButton(painter, painter.getAttributeSet());
    painter.drawPorts();
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    paintButton(painter, painter.getAttributeSet());
  }

  private void paintButton(InstancePainter painter, AttributeSet attrs) {
    Bounds bds = painter.getNominalBounds();
    int x = bds.getX();
    int y = bds.getY();
    int w = bds.getWidth();
    int h = bds.getHeight();

    Color bg = attrs.getValue(Text.BG_COLOR);
    if (!painter.shouldDrawColor()) {
      int hue = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
      bg = new Color(hue, hue, hue);
    }

    Graphics2D g = painter.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Shadow layer (bottom-right offset)
    g.setColor(bg.darker());
    g.fillRoundRect(x + DEPTH, y + DEPTH, w - DEPTH, h - DEPTH, ARC, ARC);

    // Main button face
    g.setColor(bg);
    g.fillRoundRect(x, y, w - DEPTH, h - DEPTH, ARC, ARC);

    // Highlight on top-left — classic 3D raised look
    g.setColor(bg.brighter());
    GraphicsUtil.switchToWidth(g, 1.5f);
    g.drawRoundRect(x + 1, y + 1, w - DEPTH - 2, h - DEPTH - 2, ARC - 2, ARC - 2);
    GraphicsUtil.switchToWidth(g, 1);

    // Outline
    g.setColor(Color.BLACK);
    g.drawRoundRect(x, y, w - DEPTH, h - DEPTH, ARC, ARC);

    // Label text centered in the button face
    String label = attrs.getValue(StdAttr.LABEL);
    if (label != null && !label.isEmpty()) {
      Font font = attrs.getValue(StdAttr.LABEL_FONT);
      Color labelColor = attrs.getValue(StdAttr.LABEL_COLOR);
      g.setFont(font);
      g.setColor(labelColor);
      FontMetrics fm = g.getFontMetrics();
      int tx = x + (w - DEPTH - fm.stringWidth(label)) / 2;
      int ty = y + (h - DEPTH - fm.getHeight()) / 2 + fm.getAscent();
      g.drawString(label, tx, ty);
    }
  }

  @Override
  public void propagate(InstanceState state) {
    // No ports — nothing to propagate.
  }

}
