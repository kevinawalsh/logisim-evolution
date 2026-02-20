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

package com.cburch.logisim.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;

import com.cburch.draw.util.TextMetrics;

public class GraphicsUtil {

  static public void drawArrow(Graphics2D g, int x0, int y0, int x1, int y1,
      int stemWidth, int headLength, int headAngle) {
    double offs = headAngle * Math.PI / 180.0;
    double angle = Math.atan2(y0 - y1, x0 - x1);
    double xh0 = x1 + headLength * Math.cos(angle + offs);
    double xh1 = x1 + headLength * Math.cos(angle - offs);
    double yh0 = y1 + headLength * Math.sin(angle + offs);
    double yh1 = y1 + headLength * Math.sin(angle - offs);
    double xh = (xh0 + xh1) / 2;
    double yh = (yh0 + yh1) / 2;
    double dx = stemWidth * Math.cos(angle+Math.PI/2) / 2;
    double dy = stemWidth * Math.sin(angle+Math.PI/2) / 2;
    int[] xs = { (int)(x0-dx), (int)(xh-dx), (int)xh0,
      x1, (int)xh1,  (int)(xh+dx), (int)(x0+dx) };
    int[] ys = { (int)(y0-dy), (int)(yh-dy), (int)yh0,
      y1, (int)yh1,  (int)(yh+dy), (int)(y0+dy) };
    g.fillPolygon(xs, ys, 7);
  }

  static public void drawCenteredArc(Graphics2D g, int x, int y, int r,
      int start, int dist) {
    g.drawArc(x - r, y - r, 2 * r, 2 * r, start, dist);
  }

  static public void drawCenteredText(Graphics2D g, String text, int x, int y) {
    drawText(g, text, x, y, H_CENTER, V_CENTER);
  }

  static public void drawCenteredText(Graphics2D g, Font font, String text, int x, int y) {
    drawText(g, font, text, x, y, H_CENTER, V_CENTER);
  }

  // Returns a cursor box at specified character position.
  static public Rectangle getTextCursor(FontRenderContext frc, Font font, String text,
      int x, int y, int pos, int halign, int valign) {
    // FIXME: this doesn't handle unicode (e.g. emoji, multi-byte accents) properly
    Rectangle r = getTextBounds(frc, font, text, x, y, halign, valign);
    if (pos > 0)
      r.x += new TextMetrics(frc, font, text.substring(0, pos)).i_width;
    r.width = 1;
    return r;
  }

  // Performs hit testing, and returns character position given offset within rendered text.
  static public int getTextPosition(FontRenderContext frc, Font font, String text,
      int x, int y, int halign, int valign) {
    // FIXME: this doesn't handle unicode (e.g. emoji, multi-byte accents) properly
    Rectangle r = getTextBounds(frc, font, text, 0, 0, halign, valign);
    x -= (int)r.x;
    int last = 0;
    for (int i = 0; i < text.length(); i++) {
      int cur = (int)font.getStringBounds(text.substring(0, i + 1), frc).getWidth();
      if (x <= (last + cur) / 2) {
        return i;
      }
      last = cur;
    }
    return text.length();
  }

  static public void drawText(Graphics2D g, Font font, String text, int x,
      int y, int halign, int valign) {
    Font oldfont = g.getFont();
    if (font != null)
      g.setFont(font);
    drawText(g, text, x, y, halign, valign);
    if (font != null)
      g.setFont(oldfont);
  }

  static public void drawText(Graphics2D g, String text, int x, int y,
      int halign, int valign) {
    if (text.length() == 0)
      return;
    FontRenderContext frc = g.getFontRenderContext();
    Font font = g.getFont();
    TextMetrics tm = new TextMetrics(frc, font, text);
    Rectangle bd = transform(x, y, tm.i_width, tm.i_height, tm.i_ascent, tm.i_descent, halign, valign);
    g.drawString(text, bd.x, bd.y + tm.i_ascent);
  }

  static public Rectangle getTextBounds(FontRenderContext frc, Font font, String text,
      int x, int y, int halign, int valign) {
    TextMetrics tm = new TextMetrics(frc, font, text);
    return transform(x, y, tm.i_width, tm.i_height, tm.i_ascent, tm.i_descent, halign, valign);
  }

  static public void outlineText(Graphics2D g, String text, int x, int y, Color fg, Color bg) {
    // g.setColor(bg);
    // for (int dx = -1; dx <= 1; dx++)
    //   for (int dy = -1; dy <= 1; dy++)
    //     g.drawString(text, x+dx, y+dy);
    // g.setColor(fg);
    // g.drawString(text, x, y);
    Graphics2D g2 = (Graphics2D)g;
    GlyphVector glyphVector = g2.getFont().createGlyphVector(g2.getFontRenderContext(), text);
    Shape textShape = glyphVector.getOutline();
    AffineTransform transform = g2.getTransform();
    g2.translate(x, y);
    g2.setColor(bg);
    g2.draw(textShape);
    g2.setColor(fg);
    g2.fill(textShape);
    g2.setTransform(transform);
  }

  private static Rectangle transform(int x, int y, int width, int height,
      int ascent, int descent, int halign, int valign) {
    Rectangle ret = new Rectangle(x, y, width, height);
    switch (halign) {
    case H_CENTER:
      ret.translate(-(width / 2), 0);
      break;
    case H_RIGHT:
      ret.translate(-width, 0);
      break;
    default:
      ;
    }
    switch (valign) {
    case V_TOP:
      break;
    case V_CENTER:
      ret.translate(0, -(ascent / 2));
      break;
    case V_CENTER_OVERALL:
      ret.translate(0, -(height / 2));
      break;
    case V_BASELINE:
      ret.translate(0, -ascent);
      break;
    case V_BOTTOM:
      ret.translate(0, -height);
      break;
    default:
      ;
    }
    return ret;
  }

  static final String TAB = "    "; // TAB = four spaces

  static private int tabStringWidth(Graphics2D g, Font font, String text) {
    String segments[] = text.split("\t", -1);
    if (segments.length == 0)
      return 0;
    FontRenderContext fr = ((Graphics2D)g).getFontRenderContext();
    if (segments.length == 1)
      return (int)font.getStringBounds(segments[0], fr).getWidth();
    int w = (segments.length - 1) * (int)font.getStringBounds(TAB, fr).getWidth();
    for (String s : segments)
      w += (int)font.getStringBounds(s, fr).getWidth();
    return w;
  }

  static final BasicStroke COMMON_STROKES[] = {
    new BasicStroke(0.5f),
    new BasicStroke(1f), new BasicStroke(2f), new BasicStroke(3f), new BasicStroke(4f), new BasicStroke(5f) };

  static public void switchToWidth(Graphics g, int width) {
    Graphics2D g2 = (Graphics2D) g;
    if (1 <= width && width <= 5)
      g2.setStroke(COMMON_STROKES[width]);
    else
      g2.setStroke(new BasicStroke((float) width));
  }

  static public void switchToWidth(Graphics g, float width) {
    Graphics2D g2 = (Graphics2D) g;
    if (width == 0.5f) g2.setStroke(COMMON_STROKES[0]);
    else if (width == 1f) g2.setStroke(COMMON_STROKES[1]);
    else if (width == 2f) g2.setStroke(COMMON_STROKES[2]);
    else if (width == 3f) g2.setStroke(COMMON_STROKES[3]);
    else if (width == 4f) g2.setStroke(COMMON_STROKES[4]);
    else if (width == 5f) g2.setStroke(COMMON_STROKES[5]);
    else g2.setStroke(new BasicStroke(width));
  }

  public static final int H_LEFT = -1;
  public static final int H_CENTER = 0;
  public static final int H_RIGHT = 1;

  public static final int V_TOP = -1;
  public static final int V_CENTER = 0;
  public static final int V_BASELINE = 1;
  public static final int V_BOTTOM = 2;

  public static final int V_CENTER_OVERALL = 3;

  // Painting code using Graphics/Graphics2D seems to fall into three cases:
  //
  // 1. UI-like: Part of the regular java/swing/awt user interface, a button or
  //    icon in the toolbar or in the project explorer tree, as part of the
  //    attribute table, etc.
  //
  //    For UI painting, we don't usually apply any rendering hints, and
  //    instead rely on the default anti-aliasing and fractional-metrics
  //    settings. This provides some consistency with any rendering done by
  //    Swing for buttons, labels, etc.
  //
  // 2. Canvas-like: The main circuit/simulation editor pane, circuit
  //    appearance, exported or printed images of circuits, etc.
  //
  //    Although inconsistently applied, most code has been converging to:
  //
  //    * KEY_ANTIALIASING => VALUE_ANTIALIAS_ON
  //
  //    * KEY_TEXT_ANTIALIASING => VALUE_TEXT_ANTIALIAS_ON
  //
  //    Additionally, experiments (on a MacOS M2 2022 model) with Text and
  //    Callout using longer wrapped layouts, and suggestions online, indicate
  //    that we would benefit from:
  //
  //    * KEY_FRACTIONALMETRICS => VALUE_FRACTIONALMETRICS_ON
  //
  // 3. Diagrams: Text-heavy but non-paragraph visualizations, such as K-Map
  //    table or analysis expression rendering.
  //
  //    These use text and regular anti-aliasing ON hints. Fractional-metrics
  //    probably won't matter for these much.
  // 
  // 4. Miscellaneous: In a very few places, stroke control hints are applied.
  //    This isn't done (yet) in any consistent way.
  //
  // Conventions for Graphics and Graphics2D:
  //
  // - Graphics2D variables already have appropriate hinting applied. For
  //   example, a Graphics2D variable within circuit-related painting code
  //   should be assumed to have ANTIALIAS=ON, TEXT_ANTIALIAS=ON, and
  //   FRACTIONALMETRICS=ON already. Similarly, a Graphics2D variable within
  //   icon-painting code can be assumed to have swing/awt defaults.
  //
  // - Most Logisim methods should take Graphics2D, not a Graphics. This is a
  //   signal that the appropriate hinting is already applied.
  //
  // - For swing/awt callbacks and entry points that by necessity take a
  //   Graphics parameter, these are often cast to a Graphics2D. At the point of
  //   casting, appropriate hinting should be applied using one of the methods
  //   below, or leave a comment if no hinting is needed.
  //
  // - Any remaining `Graphics` variables should be considered suspect. They are
  //   hopefully purely UI-related, so don't need any hinting.
  //
  // - Any method that changes hinting should also restore them, or dispose the
  //   graphics entirely.
  //
  // - Creation of new Graphics and Graphics2D can be through:
  //    - g2.create() // common, used for isolating drawing side-effects
  //    - java.awt.Component.getGraphics() // to be avoided where possible
  //    - BufferedImage.getGraphics() // deprecated
  //    - BufferedImage.createGraphics() // better
  //    - possibly other cases?
  //   In all such cases where a new Graphics/Graphics2D is created:
  //    - Hinting should be applied at the time of creation, except for
  //      g2.create() which would inherit the hints from g2.
  //    - dispose() must be called.
  //
  // Note: these conventions are not yet applied consistently everywhere.
 
  // Use this for hinting on the canvas used for circuits, circuit appearance,
  // exported or printed images of the circuit, etc. This is for case 2 above,
  // "Canvas-like" scenarios.
  public static Object[] setRenderingHintsForCanvas(Graphics g) {
    return setRenderingHintsForNiceText(g);
  }

  // Use this for hinting in other places where nicer, smoothed text is desired,
  // for example in K-map visualizations, or analysis expression rendering. This
  // is for case 3 above, "Diagrams".
  public static Object[] setRenderingHintsForNiceText(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    Object ta = g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
    Object fm = g2.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
    if (aa == RenderingHints.VALUE_ANTIALIAS_ON
        && ta == RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        && fm == RenderingHints.VALUE_FRACTIONALMETRICS_ON)
      return null; // no need to set or restore anything
    Object[] old = new Object[3];
    if (aa != RenderingHints.VALUE_ANTIALIAS_ON) {
      old[0] = aa;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);
    }
    if (ta != RenderingHints.VALUE_TEXT_ANTIALIAS_ON) {
      old[1] = ta;
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    if (fm != RenderingHints.VALUE_FRACTIONALMETRICS_ON) {
      old[2] = fm;
      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
          RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    return old;
  }

  // Use this after calling either setRenderingHintsForCanvas(g) or
  // setRenderingHintsForNiceText(g), unless g will be destroyed anyway.
  public static void restoreRenderingHints(Graphics g, Object[] old) {
    if (old == null)
      return;
    Graphics2D g2 = (Graphics2D)g;
    if (old[0] != null)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old[0]);
    if (old[1] != null)
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, old[1]);
    if (old[2] != null)
      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, old[2]);
  }

  // This is a convenience helper for case 4 above, "Miscellaneous" rendering.
  public static Object usePureStrokeRendering(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    Object old = g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
    if (old == RenderingHints.VALUE_STROKE_PURE)
      return null; // no need to set or restore anything
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
        RenderingHints.VALUE_STROKE_PURE);
    return old;
  }

  // This is a convenience helper for case 4 above, "Miscellaneous" rendering.
  public static Object useDefaultStrokeRendering(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    Object old = g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
    if (old == RenderingHints.VALUE_STROKE_DEFAULT)
      return null; // no need to set or restore anything
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
        RenderingHints.VALUE_STROKE_DEFAULT);
    return old;
  }

  public static final FontRenderContext CANVAS_FONT_RENDER_CONTEXT = new FontRenderContext(null /*xform*/, true /*text antialiasing*/, true /*fractional metrics*/);
  public static final FontRenderContext NICE_TEXT_FONT_RENDER_CONTEXT = CANVAS_FONT_RENDER_CONTEXT;

  // Use this after calling either usePureStrokeRendering(g) or
  // useDefaultStrokeRendering(g), unless g will be destroyed anyway.
  public static void restoreStrokeRendering(Graphics g, Object old) {
    if (old == null)
      return;
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, old);
  }

  // Many classes have code like:
  //    import com.cburch.logisim.util.GraphicsUtil;
  //    ...
  //    ... GraphicsUtil.switchToWidth(g, 2);
  //    ... GraphicsUtil.drawText(text, font, x, y, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
  //    ...
  // The long name "GraphicsUtil" can be tedious, so there is a temptation to declare local helpers,
  // or to locally re-declare alignment constants. Instead, use static imports for the constants:
  //    import static com.cburch.logisim.util.GraphicsUtil.ALIGN;
  //    ... GraphicsUtil.switchToWidth(g, 2);
  //    ... GraphicsUtil.drawText(text, font, x, y, ALIGN.H_LEFT, ALIGN.V_TOP);
  // Or import static everything:
  //    import static com.cburch.logisim.util.GraphicsUtil.*;
  //    ... switchToWidth(g, 2);
  //    ... drawText(text, font, x, y, H_LEFT, V_TOP); // or ALIGN.H_LEFT, ALIGN.V_TOP
  public static final GraphicsUtil ALIGN = new GraphicsUtil();
  private GraphicsUtil() { }
}
