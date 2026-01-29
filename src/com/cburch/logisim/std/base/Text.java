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

package com.cburch.logisim.std.base;
import static com.cburch.logisim.std.Strings.S;

import java.util.Collection;
import java.util.List;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.TextField;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.CustomHandles;
import com.cburch.logisim.tools.Reshapable;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;

public class Text extends InstanceFactory implements CustomHandles, Reshapable {

  static final int PAD = 4;

  static class MultilineAttribute extends Attribute<String> {
    MultilineAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public String parse(String unescapedFromXML) {
      return unescapedFromXML;
    }

    // Note: in the UI's left side panel attribute table, it's
    // difficult (impossible?) to type newlines or tabs. So
    // we escape newlines and tabs here, along with backslashes.
    // This is done only for user/display strings, not for XML.

    @Override
    public String parseFromUser(java.awt.Window window, String escaped) {
      StringBuilder s = new StringBuilder();
      boolean escape = false;
      for (int i = 0; i < escaped.length(); i++) {
        char c = (char)escaped.charAt(i);
        if (escape) {
          escape = false;
          switch (c) {
          case 't': s.append('\t'); break;
          case 'n': s.append('\n'); break;
          case '\\': s.append('\\'); break;
          default:
            // bad escape: leave alone, don't eat the backslash
            s.append("\\" + c);
            break;
          }
        } else if (c == '\\') {
          escape = true;
        } else {
          s.append(c);
        }
      }
      if (escape) // bad trailing escape, leave alone, don't eat it.
        s.append('\\');
      return s.toString();
    }

    public String toDisplayString(String s) {
      StringBuilder escaped = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
        char c = (char)s.charAt(i);
        switch (c) {
        case '\t': escaped.append("\\t"); break;
        case '\n': escaped.append("\\n"); break;
        case '\\': escaped.append("\\\\"); break;
        default: escaped.append(c); break;
        }
      }
      return escaped.toString();
    }

  }

  public static Attribute<String> ATTR_TEXT = new MultilineAttribute("text",
      S.getter("textTextAttr"));
  public static Attribute<Font> ATTR_FONT = Attributes.forFont("font",
      S.getter("textFontAttr"));
  public static Attribute<AttributeOption> ATTR_HALIGN = Attributes
      .forOption(
          "halign",
          S.getter("textHorzAlignAttr"),
          new AttributeOption[] {
            new AttributeOption(Integer.valueOf(TextField.H_LEFT),
                "left", S.getter("textHorzAlignLeftOpt")),
            new AttributeOption(Integer.valueOf(TextField.H_RIGHT),
                "right", S.getter("textHorzAlignRightOpt")),
            new AttributeOption(Integer.valueOf(TextField.H_CENTER),
                "center", S.getter("textHorzAlignCenterOpt")),
          });

  // Note: For legacy reasons, all the vertical alignment options are relative
  // to the first line of text. So V_BASELINE is the baseline of the first line
  // of text, V_BOTTOM is the bottom of the first line, and V_CENTER is the center
  // of the first line.
  public static Attribute<AttributeOption> ATTR_VALIGN = Attributes
      .forOption(
          "valign",
          S.getter("textVertAlignAttr"),
          new AttributeOption[] {
            new AttributeOption(Integer.valueOf(TextField.V_TOP),
                "top", S.getter("textVertAlignTopOpt")),
            new AttributeOption(Integer.valueOf(TextField.V_BASELINE),
                "base", S.getter("textVertAlignBaseOpt")),
            new AttributeOption(Integer.valueOf(TextField.V_BOTTOM),
                "bottom", S.getter("textVertAlignBottomOpt")),
            new AttributeOption(Integer.valueOf(TextField.V_CENTER),
                "center", S.getter("textVertAlignCenterOpt")),
          });

  static final Attribute<Color> FG_COLOR = Attributes.forColor(
      "foreground", S.getter("textForegroundColorAttr"));
  static final Attribute<Color> BG_COLOR = Attributes.forColor(
      "background", S.getter("textBackgroundColorAttr"));

  static final Attribute<Boolean> TEXT_WRAP = Attributes.forBoolean(
      "wrap", S.getter("textWrapping"));
  static final int TEXT_MIN_WIDTH = 10;
  static final int TEXT_MAX_WIDTH = 10000;
  static final Attribute<Integer> TEXT_WIDTH = Attributes.forIntegerRange(
      "textwidth", S.getter("textWidth"), TEXT_MIN_WIDTH, TEXT_MAX_WIDTH);

  public static final Text FACTORY = new Text();

  private Text() {
    this("Text", S.getter("textComponent"));
  }

  protected Text(String name, StringGetter desc) {
    super(name, desc);
    setIconName("comment.png");
    setShouldSnap(false);
  }

  @Override
  public void propagate(InstanceState state) { }

  protected void configureLabel(Instance instance) {
    TextAttributes attrs = (TextAttributes) instance.getAttributeSet();
    Location loc = instance.getLocation();
    instance.setTextField(ATTR_TEXT, ATTR_FONT, loc.getX(), loc.getY(),
        attrs.getHorizontalAlign(), attrs.getVerticalAlign(), true);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    configureLabel(instance);
    instance.addAttributeListener();
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new TextAttributes();
  }

  @Override
  public Component createComponent(Location loc, AttributeSet attrs) {
    InstanceComponent ret = new InstanceComponent(this, loc, attrs) {
      // @Override
      // public Bounds getNominalBounds() {
      //   return getFactory().getOffsetBounds(getAttributeSet()).translate(getLocation()); // nomminal
      // }
      @Override
      public boolean visiblyContains(Location pt, Graphics g) {
        return getVisibleBounds(g).contains(pt);
      }
      @Override
      public Bounds getVisibleBounds(Graphics g) {
        // Note: textField.getBounds() would work here, if superclass provided
        // access. But for consistency, call the factory instead since the
        // factory must implement getOffsetBounds(attr, g) anyway.
        return ((Text)getFactory()).getTextVisibleBounds(getLocation(), getAttributeSet(), g);
      }
    };
    configureNewInstance(ret.getInstance());
    return ret;
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) { // nominal
    // This is an estimate. We can't properly compute without a graphics
    // context. But this still gets called in some cases, e.g. while opening a
    // file to check for overlap, and during some copy-paste operations.
    TextAttributes attrs = (TextAttributes)attrsBase;
    // String text = attrs.getText();
    // if (text == null || text.equals(""))
    //   return Bounds.EMPTY_BOUNDS; // should never happen
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    Font font = attrs.getFont();

    // Note: don't expand by 4 as done below. Better to underestimate than overestimate.
    String text = "ABC"; // note: we use a fixed, short string, because the
                         // estimate string width is often very wrong, leading
                         // to UI annoyances, e.g. inability to move a string
                         // near the canvas left edge.
    return StringUtil.estimateAlignedBounds(text, font, halign, valign);
  }
  
  @Override
  public Bounds getVisibleOffsetBounds(AttributeSet attrsBase, Graphics g) { // visible
    return getTextOnlyVisibleOffsetBounds(attrsBase, g, null);
  }

  protected final Bounds getTextOnlyVisibleOffsetBounds(AttributeSet attrsBase, Graphics g, Integer altTextWidth) { // visible
    TextAttributes attrs = (TextAttributes) attrsBase;
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    Font font = attrs.getFont();
    String[] lines = attrs.getLines(g, altTextWidth);
    Rectangle r = GraphicsUtil.getTextBounds(g, font, lines, 0, 0, halign, valign);
    if (attrs.isWrapping()) {
      // force to textWidth
      int w = altTextWidth != null ? altTextWidth : attrs.getTextWidth();
      if (r.width != w) {
        if (halign == TextField.H_CENTER)
          r.x -= (w - r.width) / 2;
        else if (halign == TextField.H_RIGHT)
          r.x -= (w - r.width);
        r.width = w;
      }
    }
    return Bounds.create(r).expand(PAD);
  }

  private Bounds getTextVisibleBounds(Location loc, AttributeSet attrsBase, Graphics g) { // visible
    return getVisibleOffsetBounds(attrsBase, g).translate(loc);
  }

  protected Bounds getTextOnlyVisibleBounds(Location loc, AttributeSet attrsBase, Graphics g, Integer altTextWidth) { // visible
    return getTextOnlyVisibleOffsetBounds(attrsBase, g, altTextWidth).translate(loc);
  }

  @Override
  public boolean HDLIgnore() { return true; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_HALIGN || attr == ATTR_VALIGN)
      configureLabel(instance);
    instance.recomputeBounds(); // nominal
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    paint(painter, true, null);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    paint(painter, false, null);
  }

  @Override
  public void drawReshaping(InstancePainter painter, Location handle, int rdx, int rdy) {
    TextAttributes attrs = (TextAttributes)painter.getAttributeSet();
    Location loc = painter.getLocation();
    int altTextWidth = calculateNewTextWidth(loc, attrs, handle, rdx, rdy);
    paint(painter, true, altTextWidth);
  }

  public void paint(InstancePainter painter, boolean drawBoundingBox, Integer altTextWidth) {
    TextAttributes attrs = (TextAttributes)painter.getAttributeSet();
    Location loc = painter.getLocation();
    Graphics g = painter.getGraphics();
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    if (altTextWidth != null) {
      // width-reshaping: use alternative width and draw a text-only border
      Bounds bds = getTextOnlyVisibleBounds(loc, attrs, g, altTextWidth);
      bds.draw(g, Color.GRAY);
    } else if (drawBoundingBox) {
      // ghost: draw full bounding box
      Bounds bds = getTextVisibleBounds(loc, attrs, g);
      bds.draw(g, Color.GRAY);
    } else {
      // normal: text-only background fill
      Bounds bds = getTextOnlyVisibleBounds(loc, attrs, g, null);
      bds.fill(g, attrs.getBGColor());
    }
    g.setColor(attrs.getFGColor());
    // Note: This next code is essentially identical to painter.drawLabel(),
    // which draws by using TextFieldMultiline, which in turn uses GraphicsUtil.
    // But painter.drawLabel() only works when there is a Component, not when
    // there is only a Factory and AttributeSet, because the textField needed is
    // within the Component. So we duplicate the code here.
    Font font = attrs.getFont();
    String[] lines = attrs.getLines(g, altTextWidth);
    GraphicsUtil.drawText(g, font, lines, loc.getX(), loc.getY(), halign, valign);
  }

  // TODO: pink handles for resize
  @Override
  public void drawHandles(ComponentDrawContext context) {
    Graphics g = context.getGraphics();
    g.setColor(Color.GRAY);
    InstancePainter painter = context.getInstancePainter();
    Bounds bds = getTextVisibleBounds(painter.getLocation(), painter.getAttributeSet(), g);
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    painter.drawHandles();
  }

  @Override
  public Collection<Location> getReshapeHandles(Component comp) {
    TextAttributes attrs = (TextAttributes)comp.getAttributeSet();
    if (attrs.isWrapping() == false)
      return List.of(); // empty
    Location loc = comp.getLocation();
    int tw = attrs.getTextWidth();
    if (attrs.getHorizontalAlign() == TextField.H_LEFT)
      return List.of(loc.translate(tw + PAD, 0));
    else if (attrs.getHorizontalAlign() == TextField.H_RIGHT)
      return List.of(loc.translate(-(tw + PAD), 0));
    else // H_CENTER
      return List.of(
          loc.translate(-(tw/2 + PAD), 0),
          loc.translate(tw-tw/2 + PAD, 0));
  }

  protected int calculateNewTextWidth(Location loc, TextAttributes attrs, Location handle, int rdx, int rdy) {
    int textWidth;
    if (attrs.getHorizontalAlign() == TextField.H_LEFT)
      textWidth = (handle.getX() - PAD + rdx) - loc.getX();
    else if (attrs.getHorizontalAlign() == TextField.H_RIGHT)
      textWidth = loc.getX() - (handle.getX() + PAD + rdx);
    else if (handle.getX() >= loc.getX()) // H_CENTER, adjusting right handle
      textWidth = 2*((handle.getX() - PAD + rdx) - loc.getX());
    else // H_CENTER, adjusting left handle
      textWidth = 2*(loc.getX() - (handle.getX() + PAD + rdx));
    return clamp(textWidth, TEXT_MIN_WIDTH, TEXT_MAX_WIDTH);
  }

  @Override
  public void doReshapeAction(Project proj, Circuit circ, Component comp,
      Location handle, int rdx, int rdy) {
    TextAttributes attrs = (TextAttributes)comp.getAttributeSet();
    Location loc = comp.getLocation();
    int textWidth = calculateNewTextWidth(loc, attrs, handle, rdx, rdy);
    SetAttributeAction act = new SetAttributeAction(circ, S.getter("textReshape"));
    act.set(comp, TEXT_WIDTH, textWidth);
    proj.doAction(act);
  }

  @Override
  public Object getInstanceFeature(Instance instance, Object key) {
    if (key == Reshapable.class)
      return this;
    else
      return super.getInstanceFeature(instance, key);
  }

  int clamp(int val, int min, int max) {
    return Math.min(Math.max(val, min), max);
  }
}
