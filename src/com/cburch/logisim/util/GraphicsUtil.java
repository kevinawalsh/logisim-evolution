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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.Shape;

import com.cburch.draw.util.TextMetrics;

public class GraphicsUtil {
  static public void drawArrow(Graphics g, int x0, int y0, int x1, int y1,
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

  static public void drawCenteredArc(Graphics g, int x, int y, int r,
      int start, int dist) {
    g.drawArc(x - r, y - r, 2 * r, 2 * r, start, dist);
  }

  static public void drawCenteredText(Graphics g, String text, int x, int y) {
    drawText(g, text, x, y, H_CENTER, V_CENTER);
  }

  static public void drawCenteredText(Graphics g, Font font, String text, int x, int y) {
    drawText(g, font, text, x, y, H_CENTER, V_CENTER);
  }

  // Returns a cursor box at specified character position.
  static public Rectangle getTextCursor(Graphics g, Font font, String text,
      int x, int y, int pos, int halign, int valign) {
    Rectangle r = getTextBounds(g, font, text, x, y, halign, valign);
    if (pos > 0)
      r.x += new TextMetrics(g, font, text.substring(0, pos)).width;
    r.width = 1;
    return r;
  }

  static public int getTextPosition(Graphics g, Font font, String text,
      int x, int y, int halign, int valign) {
    Rectangle r = getTextBounds(g, font, text, 0, 0, halign, valign);
    x -= (int)r.x;
    int last = 0;
    if (font == null)
      font = g.getFont();
    FontRenderContext fr = ((Graphics2D)g).getFontRenderContext();
    for (int i = 0; i < text.length(); i++) {
      int cur = (int)font.getStringBounds(text.substring(0, i + 1), fr).getWidth();
      if (x <= (last + cur) / 2) {
        return i;
      }
      last = cur;
    }
    return text.length();
  }

  static public void drawText(Graphics g, Font font, String text, int x,
      int y, int halign, int valign) {
    Font oldfont = g.getFont();
    if (font != null)
      g.setFont(font);
    drawText(g, text, x, y, halign, valign);
    if (font != null)
      g.setFont(oldfont);
  }

  static public void drawText(Graphics g, String text, int x, int y,
      int halign, int valign) {
    if (text.length() == 0)
      return;
    Rectangle bd = getTextBounds(g, text, x, y, halign, valign);
    TextMetrics tm = new TextMetrics(g, text);
    g.drawString(text, bd.x, bd.y + tm.ascent);
  }

  static public Rectangle getTextBounds(Graphics g, String text, int x,
      int y, int halign, int valign) {
    return getTextBounds(g, null, text, x, y, halign, valign);
  }

  static public Rectangle getTextBounds(Graphics g, Font font, String text,
      int x, int y, int halign, int valign) {
    TextMetrics tm = new TextMetrics(g, font, text);
    return transform(x, y, tm.width, tm.height, tm.ascent, tm.descent, halign, valign);
  }

  static public void outlineText(Graphics g, String text, int x, int y, Color fg, Color bg) {
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

  static private int tabStringWidth(Graphics g, Font font, String text) {
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

  // Returns a cursor box at specified character position.
  static public Rectangle getTextCursor(Graphics g, Font font, String text[],
      int x, int y, int pos, int halign, int valign) {
    TextMetrics tm = new TextMetrics(g, font, null);
    if (font == null)
      font = g.getFont();
    Rectangle r = getTextBounds(g, font, text, x, y, halign, valign);
    int width = (int)r.getWidth();
    // "ab\nc\n" --> 0 a 1 b 2 \n 3 \n 4 c 5 \n 6
    x = (int)r.getX();
    y = (int)r.getY();
    for (String line: text) {
      if (pos > line.length()) {
        pos -= line.length() + 1;
        y += tm.height;
        continue;
      }
      int linewidth = tabStringWidth(g, font, line);
      switch (halign) {
      case H_CENTER:
        x += (width - linewidth)/2;
        break;
      case H_RIGHT:
        x += (width - linewidth);
        break;
      }
      if (pos > 0)
        x += tabStringWidth(g, font, line.substring(0, pos));
      return new Rectangle(x, y, 1, tm.ascent + tm.descent);
    }
    return null;
  }

  static public int getTextPosition(Graphics g, Font font, String text[],
      int x, int y, int halign, int valign) {
    Rectangle r = getTextBounds(g, font, text, 0, 0, halign, valign);
    TextMetrics tm = new TextMetrics(g, font, null);
    if (font == null)
      font = g.getFont();
    x -= (int)r.getX();
    y -= (int)r.getY();
    int pos = 0;
    for (String line : text) {
      if (y >= tm.height) {
        y -= tm.height;
        pos += line.length() + 1;
        continue;
      }
      int linewidth = tabStringWidth(g, font, line);
      switch (halign) {
      case H_CENTER:
        x -= (r.getWidth() - linewidth)/2;
        break;
      case H_RIGHT:
        x -= (r.getWidth() - linewidth);
        break;
      }
      int last = 0;
      for (int i = 0; i < line.length(); i++) {
        int cur = tabStringWidth(g, font, line.substring(0, i + 1));
        if (x <= (last + cur) / 2) {
          return pos + i;
        }
        last = cur;
      }
      return pos + line.length();
    }
    return pos - 1;
  }

  static public void drawText(Graphics g, Font font, String text[], int x,
      int y, int halign, int valign) {
    Font oldfont = g.getFont();
    if (font != null)
      g.setFont(font);
    drawText(g, text, x, y, halign, valign);
    if (font != null)
      g.setFont(oldfont);
  }

  static public void drawText(Graphics g, String text[], int x, int y,
      int halign, int valign) {
    if (text.length == 0)
      return;
    Rectangle bds = getTextBounds(g, text, x, y, halign, valign);
    FontRenderContext fr = ((Graphics2D)g).getFontRenderContext();
    TextMetrics tm = new TextMetrics(g);
    Font font = g.getFont();
    y = bds.y + tm.ascent;
    for (String line : text) {
      int linewidth;
      switch (halign) {
      case H_CENTER:
        linewidth = tabStringWidth(g, font, line);
        x = bds.x + (bds.width - linewidth)/2;
        break;
      case H_RIGHT:
        linewidth = tabStringWidth(g, font, line);
        x = bds.x + (bds.width - linewidth);
        break;
      default:
        x = bds.x;
      }
      int w = (int)font.getStringBounds(TAB, fr).getWidth();
      for (String s : line.split("\t", -1)) {
        g.drawString(s, x, y);
        x +=(int)font.getStringBounds(s, fr).getWidth() + w;
      }
      y += tm.height;
    }
  }

  static public Rectangle getTextBounds(Graphics g, String text[], int x,
      int y, int halign, int valign) {
    return getTextBounds(g, null, text, x, y, halign, valign);
  }

  static public Rectangle getTextBounds(Graphics g, Font font, String text[],
      int x, int y, int halign, int valign) {
    int n = text.length;
    if (n == 0)
      return getTextBounds(g, font, "", x, y, halign, valign);
    TextMetrics tm = new TextMetrics(g, font, null);
    if (font == null)
      font = g.getFont();
    int width = tabStringWidth(g, font, text[0]);
    for (int i = 1; i < n; i++) {
      int w = tabStringWidth(g, font, text[i]);
      if (w > width)
        width = w;
    }
    Rectangle ret = new Rectangle(x, y, width, n * tm.height);
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
      ret.translate(0, -(tm.height / 2));
      break;
    case V_CENTER_OVERALL:
      ret.translate(0, -(n * tm.height / 2));
      break;
    case V_BASELINE:
      ret.translate(0, -tm.ascent);
      break;
    case V_BOTTOM:
      ret.translate(0, -tm.height);
      break;
    default:
      ;
    }
    return ret;
  }

  static public void switchToWidth(Graphics g, int width) {
    Graphics2D g2 = (Graphics2D) g;
    g2.setStroke(new BasicStroke((float) width));
  }

  static public void switchToWidth(Graphics g, float width) {
    Graphics2D g2 = (Graphics2D) g;
    g2.setStroke(new BasicStroke(width));
  }

  public static final int H_LEFT = -1;
  public static final int H_CENTER = 0;
  public static final int H_RIGHT = 1;

  public static final int V_TOP = -1;
  public static final int V_CENTER = 0;
  public static final int V_BASELINE = 1;
  public static final int V_BOTTOM = 2;

  public static final int V_CENTER_OVERALL = 3;

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
