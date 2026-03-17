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

package com.cburch.logisim.std.decor;
import static com.cburch.logisim.std.Strings.S;

import java.util.Collection;
import java.util.List;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import javax.swing.Icon;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.generic.QuickHelp;
import com.cburch.logisim.gui.menu.HelpBroker;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Caret;
import com.cburch.logisim.tools.CustomHandles;
import com.cburch.logisim.tools.Reshapable;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.TextEditable;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.util.Icons;
import com.cburch.logisim.util.StringGetter;

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

public class Text extends InstanceFactory implements CustomHandles, Reshapable {

  static final int HANDLE_PAD = 0; // Used when reshaping

  static class MultilineAttribute extends Attribute<String> {
    MultilineAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public String parse(String unescapedFromXML) {
      return unescapedFromXML.replace("\r\n", "\n").replace("\r", "\n"); // eliminate CRLF and CR
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
      return s.toString().replace("\r\n", "\n").replace("\r", "\n"); // eliminate CRLF and CR
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
    
    @Override
    public Domain getDomain() { return Domain.ofDescription("any text, with newlines escaped"); }

  }

  public static Attribute<String> ATTR_TEXT = new MultilineAttribute("text",
      S.getter("textTextAttr"));
  public static Attribute<Font> ATTR_FONT = StdAttr.TEXT_FONT;
  public static Attribute<AttributeOption> ATTR_HALIGN = Attributes
      .forOption(
          "halign",
          S.getter("textHorzAlignAttr"),
          new AttributeOption[] {
            new AttributeOption(Integer.valueOf(ALIGN.H_LEFT),
                "left", S.getter("textHorzAlignLeftOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.H_RIGHT),
                "right", S.getter("textHorzAlignRightOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.H_CENTER),
                "center", S.getter("textHorzAlignCenterOpt")),
          });

  // Note: Prior to 5.0.6, for legacy reasons vertical alignment options were
  // all relative to the first line of text. So V_BASELINE is the baseline of
  // the first line of text, V_BOTTOM is the bottom of the first line (ignoring
  // margins), and V_CENTER is the center of the first line (ignoring margins).
  // V_TOP was also the top of the first line (ignoring margins).
  //
  // Starting in 5.0.6, V_TOP is the top of the multi-line box, including
  // margins. Two new options are added, V_CENTER_OVERALL and V_BOTTOM_OVERALL,
  // which, which align to the overall center and bottom including margins. The
  // old options V_BASELINE, V_CENTER, and V_BOTTOM will be renamed within the
  // UI, and perhaps V_CENTER and V_BOTTOM will someday be deprecated or
  // removed, or converted automatically to V_CENTER_OVERALL and
  // V_BOTTOM_OVERALL.
  public static Attribute<AttributeOption> ATTR_VALIGN = Attributes
      .forOption(
          "valign",
          S.getter("textVertAlignAttr"),
          new AttributeOption[] {
            new AttributeOption(Integer.valueOf(ALIGN.V_TOP),
                "top", S.getter("textVertAlignTopOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.V_CENTER_OVERALL),
                "center-overall", S.getter("textVertAlignOverallCenterOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.V_BOTTOM_OVERALL),
                "bottom-overall", S.getter("textVertAlignOverallBottomOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.V_CENTER_FIRST), // todo: decprecate
                "center", S.getter("textVertAlignFirstCenterOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.V_BASELINE), // todo: decprecate?
                "base", S.getter("textVertAlignFirstBaseOpt")),
            new AttributeOption(Integer.valueOf(ALIGN.V_BOTTOM_FIRST), // todo: decprecate
                "bottom", S.getter("textVertAlignFirstBottomOpt")),
          });

  public static Attribute<String> ATTR_STYLE = Attributes.forString("style",
      S.getter("textStyleAttr"));

  static final Attribute<Color> FG_COLOR = Attributes.forColor(
      "foreground", S.getter("textForegroundColorAttr"));
  public static final Attribute<Color> BG_COLOR = Attributes.forColor(
      "background", S.getter("textBackgroundColorAttr"));

  public final static AttributeOption TEXT_FORMAT_PLAIN =
    new AttributeOption("plain", S.getter("textFormatPlain"));
  public final static AttributeOption TEXT_FORMAT_WRAPPED =
    new AttributeOption("wrapped", S.getter("textFormatWrapped"));
  public final static AttributeOption TEXT_FORMAT_MARKDOWNISH =
    new AttributeOption("markdownish", S.getter("textFormatMarkdownish"));
  public static Attribute<AttributeOption> ATTR_FORMAT = Attributes.forOption(
          "format",
          S.getter("textFormatAttr"),
          new AttributeOption[] {
            TEXT_FORMAT_PLAIN, TEXT_FORMAT_WRAPPED, TEXT_FORMAT_MARKDOWNISH });

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
    setInstancePoker(Poker.class);
  }

  @Override
  public void propagate(InstanceState state) { }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new TextAttributes();
  }

  protected static class TextInstanceComponent extends InstanceComponent implements TextEditable {

    public TextInstanceComponent(Text factory, Location loc, TextAttributes attrs) {
      super(factory, loc, attrs);
    }

    // @Override
    // public Bounds getNominalBounds() {
    //   return getFactory().getOffsetBounds(getAttributeSet()).translate(getLocation()); // nominal
    // }
      
    @Override
    public boolean visiblyContains(Location pt) {
      return getVisibleBounds().contains(pt);
    }

    @Override
    public Bounds getVisibleBounds() {
      return ((Text)getFactory()).getTextVisibleBounds(getLocation(), getAttributeSet());
    }

    @Override
    public String toString() {
      String text = ((TextAttributes)getAttributeSet()).getText();
      return "TextInstanceComponent{factory="+getFactory().getName()
        +",loc="+getLocation()+",text="+text+"}@"+System.identityHashCode(this);
    }

    @Override
    public Object getFeature(Object key) {
      if (key == TextEditable.class)
        return this;
      if (key == ToolTipMaker.class) {
        // Only offer tooltips for markdownish text (which may contain links).
        // For other formats fall through to the default (no tooltip).
        TextAttributes attrs = (TextAttributes) getAttributeSet();
        if (attrs.isMarkdownish()) return this;
        return null;
      }
      return super.getFeature(key);
    }

    @Override
    public String getToolTip(ComponentUserEvent event) {
      TextAttributes attrs = (TextAttributes) getAttributeSet();
      if (attrs.isMarkdownish()) {
        LayoutEngine engine = attrs.getLayout();
        if (engine instanceof StyledBoxLayout) {
          Location loc = getLocation();
          String url = ((StyledBoxLayout) engine).urlForPoint(
              event.getX() - loc.getX(), event.getY() - loc.getY());
          if (url != null) {
            if (url.startsWith("#"))
              return S.fmt("textClickToSwitch", htmlEscape(url.substring(1)));
            if (url.startsWith("logisim:"))
              return S.fmt("textClickToExample", htmlEscape(url.substring(8)));
            return S.fmt("textClickToBrowse", htmlEscape(url));
          }
        }
      }
      return null;
    }

    private static String htmlEscape(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Action getCommitAction(Circuit circuit, String oldText, String newText) {
      SetAttributeAction act = new SetAttributeAction(circuit, S.getter("changeTextAction"));
      if (newText != null)
        newText = newText.replace("\r\n", "\n").replace("\r", "\n"); // eliminate CRLF and CR
      if ((oldText == null) != (newText == null) || (newText != null && !newText.equals(oldText)))
        act.set(this, ATTR_TEXT, newText);
      return act;
    }

    @Override
    public Caret getTextCaret(ComponentUserEvent event) {
      Text text = (Text)getFactory();
      TextAttributes attrs = (TextAttributes)getAttributeSet();
      Location loc = getLocation();
      CaretPosition p = text.getTextCaretPosition(loc, attrs, event.getX(), event.getY());
      return new TextCaret(attrs, event.getCanvas(),
          loc, p.cursor, p.revBias, p.bounds.translate(loc));
    }

  }

  @Override
  public Component createComponent(Location loc, AttributeSet attrs) {
    TextInstanceComponent ret = new TextInstanceComponent(this, loc, (TextAttributes)attrs);
    configureNewInstance(ret.getInstance());
    return ret;
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) { // nominal
    // // This is an estimate. We can't properly compute without a graphics
    // // context. But this still gets called in some cases, e.g. while opening a
    // // file to check for overlap, and during some copy-paste operations.
    TextAttributes attrs = (TextAttributes)attrsBase;
    // // String text = attrs.getText();
    // // if (text == null || text.equals(""))
    // //   return Bounds.EMPTY_BOUNDS; // should never happen
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    // Font font = attrs.getFont();

    // // Note: don't expand by any margin. Better to underestimate than overestimate.
    // String text = "ABC"; // note: we use a fixed, short string, because the
    //                      // estimate string width is often very wrong, leading
    //                      // to UI annoyances, e.g. inability to move a string
    //                      // near the canvas left edge.
    // return StringUtil.estimateAlignedBounds(text, font, halign, valign);

    // return Bounds.create(0, 0, 0, 0); // xml reader removes these entirely?
    // return Bounds.create(0, 0, 1, 1); // can't place left-aligned text side-by-side with right-aligned

    // As of 5.0.6, we use a fixed 10x10 box for Text nominal bounds. The only
    // practical use of these bounds are for overlap detection: logisim enforces
    // a rule that two components can't be exactly overlapping (using nominal
    // bounds for the check). This rule is meant to avoid common bugs where two
    // gates are placed exactly on top of each other: this can cause a confusing
    // conflict in the output values, since the overlap is essentially invisible
    // in the UI, and is sometimes hard to correct even if noticed. 
    //
    // Downsides of using fixed nominal bounds for Text:
    //  - two textboxes with same alignment can't share the same anchor point,
    //    even if their visual sizes differ.
    //  - two textboxes with close anchor points can conflict, depending on
    //    their alignments, even if their visual sizes differ.
    // Neither of these seems significant. And arguably, it would be even more
    // confusing if modifying the text contents could suddenly cause a location
    // conflict.
    int w = 10, h = 10;
    int x, y;
    if (halign == ALIGN.H_LEFT) x = 0;
    else if (halign == ALIGN.H_CENTER) x = -5;
    else x = -10; // H_RIGHT
    if (valign == ALIGN.V_TOP) y = 0;
    else if (valign == ALIGN.V_CENTER_FIRST || valign == ALIGN.V_CENTER_OVERALL) y = -5;
    else if (valign == ALIGN.V_BASELINE) y = -7;
    else y = -10; // V_BOTTOM_FIRST, V_BOTTOM_OVERALL
    return Bounds.create(x, y, w, h);
  }
  
  @Override
  public Bounds getVisibleOffsetBounds(AttributeSet attrsBase) { // visible
    return getTextOnlyVisibleOffsetBounds(attrsBase, null);
  }

  protected final Bounds getTextOnlyVisibleOffsetBounds(AttributeSet attrsBase, Integer altTextWidth) { // visible
    TextAttributes attrs = (TextAttributes) attrsBase;
    return attrs.getLayout(altTextWidth).getBounds(Location.ORIGIN);
  }

  private Bounds getTextVisibleBounds(Location loc, AttributeSet attrsBase) { // visible
    return getVisibleOffsetBounds(attrsBase).translate(loc);
  }

  protected Bounds getTextOnlyVisibleBounds(Location loc, AttributeSet attrsBase, Integer altTextWidth) { // visible
    return getTextOnlyVisibleOffsetBounds(attrsBase, altTextWidth).translate(loc);
  }

  protected CaretPosition getTextCaretPosition(Location loc, TextAttributes attrs, int px, int py) {
    return attrs.getLayout().caretPositionForPoint(px - loc.x, py - loc.y);
  }

  @Override
  public boolean HDLIgnore() { return true; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
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
    Graphics2D g = painter.getGraphics();
    if (altTextWidth != null) {
      // width-reshaping: use alternative width and draw a text-only border
      Bounds bds = getTextOnlyVisibleBounds(loc, attrs, altTextWidth);
      bds.draw(g, Color.GRAY);
    } else if (drawBoundingBox) {
      // ghost: draw full bounding box
      Bounds bds = getTextVisibleBounds(loc, attrs);
      bds.draw(g, Color.GRAY);
      // Bounds bds2 = getOffsetBounds(attrs).translate(loc);
      // bds2.draw(g, Color.MAGENTA); // for debugging
    } else {
      // normal: text-only background fill
      Bounds bds = getTextOnlyVisibleBounds(loc, attrs, null);
      bds.fill(g, attrs.getBGColor());
    }
    g.setColor(attrs.getFGColor());
    attrs.getLayout(altTextWidth).drawText(g, loc);
  }

  @Override
  public void paintIcon(InstancePainter painter) {
    if (!"Text".equals(getName())) {
      super.paintIcon(painter);
      return;
    }
    AttributeOption fmt = painter.getAttributeValue(ATTR_FORMAT);
    String name;
    if (fmt == TEXT_FORMAT_MARKDOWNISH)
      name = "markdownish.png";
    else if (fmt == TEXT_FORMAT_WRAPPED)
      name = "wrappedText.png";
    else
      name = "comment.png";
    Icon icon = Icons.getIcon(name);
    Graphics2D g = painter.getGraphics();
    if (icon != null)
      icon.paintIcon(painter.getDestination(), painter.getGraphics(), 2, 2);
    else
      super.paintIcon(painter);
  }

  @Override
  public void drawHandles(ComponentDrawContext context) {
    Graphics2D g = context.getGraphics();
    g.setColor(Color.GRAY);
    InstancePainter painter = context.getInstancePainter();
    Bounds bds = getTextVisibleBounds(painter.getLocation(), painter.getAttributeSet());
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    painter.drawHandles();
  }

  @Override
  public Collection<Location> getReshapeHandles(Component comp) {
    TextAttributes attrs = (TextAttributes)comp.getAttributeSet();
    if (attrs.isWrapping() == false)
      return List.of(); // empty
    Location loc = comp.getLocation();
    int width = attrs.getTextWidth();
    if (attrs.getHorizontalAlign() == ALIGN.H_LEFT)
      return List.of(loc.translate(width + HANDLE_PAD, 0));
    else if (attrs.getHorizontalAlign() == ALIGN.H_RIGHT)
      return List.of(loc.translate(-(width + HANDLE_PAD), 0));
    else // H_CENTER
      return List.of(
          loc.translate(-(width/2 + HANDLE_PAD), 0),
          loc.translate(width-width/2 + HANDLE_PAD, 0));
  }

  protected int calculateNewTextWidth(Location loc, TextAttributes attrs, Location handle, int rdx, int rdy) {
    int width;
    if (attrs.getHorizontalAlign() == ALIGN.H_LEFT)
      width = (handle.getX() - HANDLE_PAD + rdx) - loc.getX();
    else if (attrs.getHorizontalAlign() == ALIGN.H_RIGHT)
      width = loc.getX() - (handle.getX() + HANDLE_PAD + rdx);
    else if (handle.getX() >= loc.getX()) // H_CENTER, adjusting right handle
      width = 2*((handle.getX() - HANDLE_PAD + rdx) - loc.getX());
    else // H_CENTER, adjusting left handle
      width = 2*(loc.getX() - (handle.getX() + HANDLE_PAD + rdx));
    return clamp(width, TEXT_MIN_WIDTH, TEXT_MAX_WIDTH);
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
  public final Object getFeature(Object key, AttributeSet attrs) {
    if (key == TOOL_TIP) {
      AttributeOption fmt = attrs.getValue(ATTR_FORMAT);
      if (fmt == TEXT_FORMAT_MARKDOWNISH)
        return S.get("textComponentMarkdownishTip");
      else if (fmt == TEXT_FORMAT_WRAPPED)
        return S.get("textComponentWrappedTip");
      else
        return S.get("textComponentPlainTip");
    }
    if (key == QUICK_HELP) {
      AttributeOption fmt = attrs.getValue(ATTR_FORMAT);
      String suffix = fmt == TEXT_FORMAT_WRAPPED ? "-wrapped"
          : fmt == TEXT_FORMAT_MARKDOWNISH ? "-markdownish"
          : "-plain";
      return QuickHelp.load(getClass(), suffix);
    }
    return super.getFeature(key, attrs);
  }

  @Override
  public Object getInstanceFeature(Instance instance, Object key) {
    if (key == Reshapable.class)
      return this;
    else if (key == TextEditable.class)
      return this;
    else if (key == DECORATIVE)
      return Boolean.TRUE;
    else
      return super.getInstanceFeature(instance, key);
  }

  int clamp(int val, int min, int max) {
    return Math.min(Math.max(val, min), max);
  }

  // Poker for markdownish text links. init() activates only when the click
  // lands on a CLICKABLE span; otherwise returns false so PokeTool falls through
  // to its normal behavior (showing the attribute panel).
  public static class Poker extends InstancePoker {
    private String pendingUrl;

    @Override
    public boolean init(InstanceState state, MouseEvent e) {
      TextAttributes attrs = (TextAttributes) state.getAttributeSet();
      if (!attrs.isMarkdownish()) return false;
      LayoutEngine engine = attrs.getLayout();
      if (!(engine instanceof StyledBoxLayout)) return false;
      Location loc = state.getInstance().getLocation();
      pendingUrl = ((StyledBoxLayout) engine).urlForPoint(e.getX() - loc.getX(), e.getY() - loc.getY());
      return pendingUrl != null;
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      if (pendingUrl != null) {
        String url = pendingUrl;
        pendingUrl = null;
        HelpBroker.followLink(url, state.getProject());
      }
    }
  }

  interface LayoutEngine {
    Bounds getBounds(Location loc);
    void drawText(Graphics2D g, Location loc);
    CaretPosition caretPositionForPoint(int px, int py); // relative to ORIGIN
  }

  static final class CaretPosition {
    public final Bounds bounds; // relative to ORIGIN
    public final int cursor;
    public final boolean revBias;
    CaretPosition(Bounds b, int c, boolean rb) {
      bounds = b;
      cursor = c;
      revBias = rb;
    }
  }

}
