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

package com.cburch.logisim.analyze.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.JPanel;

import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.analyze.model.ParserException;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Expressions;
import com.cburch.logisim.util.GraphicsUtil;

class ExpressionRenderer extends JPanel {

  @Override
  public void validate() { }
  @Override
  public void invalidate() { }
  @Override
  public void revalidate() { }
  @Override
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) { }
  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) { }
  @Override
  public void repaint(long tm, int x, int y, int width, int height) { }
  @Override
  public void repaint(Rectangle r) { }
  @Override
  public void repaint() { }
  @Override
  public boolean isOpaque() {
    Color back = getBackground();
    Component p = getParent();
    if (p != null)
      p = p.getParent();
    boolean colorMatch = (back != null) && (p != null) &&
        back.equals(p.getBackground()) && p.isOpaque();
    return !colorMatch && super.isOpaque();
  }

  private static final Font OP_FONT = new Font("Serif", Font.PLAIN, 14);
  private static final Font TXT_FONT = new Font("Serif", Font.BOLD, 14);
  private static final Font VAR_FONT = new Font("Serif", Font.BOLD | Font.ITALIC, 14);
  private static final Font SUB_FONT = new Font("Serif", Font.ITALIC, 10);
  private static final FontMetrics OP_FONT_METRICS;
  private static final FontMetrics TXT_FONT_METRICS;
  private static final FontMetrics VAR_FONT_METRICS;
  private static final FontMetrics SUB_FONT_METRICS;
  private static final float OP_ASCENT, OP_DESCENT;
  private static final float TXT_ASCENT, TXT_DESCENT;
  private static final float VAR_ASCENT, VAR_DESCENT;
  private static final float SUB_ASCENT, SUB_DESCENT;
  private static final float TYP_ASCENT, TYP_DESCENT, TYP_HEIGHT;
  static {
    BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    try {
      GraphicsUtil.setRenderingHintsForNiceText(g);
      OP_FONT_METRICS = g.getFontMetrics(OP_FONT);
      TXT_FONT_METRICS = g.getFontMetrics(TXT_FONT);
      VAR_FONT_METRICS = g.getFontMetrics(VAR_FONT);
      SUB_FONT_METRICS = g.getFontMetrics(SUB_FONT);

      OP_ASCENT = OP_FONT_METRICS.getAscent();
      OP_DESCENT = OP_FONT_METRICS.getDescent();
      TXT_ASCENT = TXT_FONT_METRICS.getAscent();
      TXT_DESCENT = TXT_FONT_METRICS.getDescent();
      VAR_ASCENT = VAR_FONT_METRICS.getAscent();
      VAR_DESCENT = VAR_FONT_METRICS.getDescent();
      SUB_ASCENT = SUB_FONT_METRICS.getAscent();
      SUB_DESCENT = SUB_FONT_METRICS.getDescent();
      TYP_ASCENT = VAR_ASCENT;
      TYP_DESCENT = VAR_DESCENT + SUB_DESCENT;
      TYP_HEIGHT = TYP_ASCENT + TYP_DESCENT;
    } finally {
      g.dispose();
    }
  }

  abstract class Box {
    //           ___________________
    //     ^    |                   |   )
    //     |    |                   |   ) ascent 
    //  height  |                   |   )
    //     |   -o-  -  -  -  -  -  -|-
    //     v    |___________________|   ) descent
    //           <----- width -----> 
    
    float w, h, a, d; // width, height, ascent, descent
    float xx, yy;
    int depth;
    Color startColor, endColor;
    Box endColorBox;

    Box(Expression e, int depth) {
      this.depth = depth;
      this.startColor = (colorizer == null || e == null) ? null : colorizer.colorFor(e);
      this.endColorBox = this;
    }
    void paintAndColor(Graphics2D g) {
      if (startColor != null) {
        endColorBox.endColor = g.getColor();
        g.setColor(startColor);
      }
      // DEBUG paintDepth(g);
      paint(g);
      if (endColor != null)
        g.setColor(endColor);
    }
    abstract void paint(Graphics2D g);
    abstract void layout(float x, float y, LineBreaker lines);
    void move(float dx, float dy) { xx += dx; yy += dy; }
    // DEBUG void paintDepth(Graphics2D g) {
    // DEBUG   Font f = g.getFont();
    // DEBUG   g.setFont(SUB_FONT);
    // DEBUG   g.drawString(""+depth, xx, yy-TYP_HEIGHT);
    // DEBUG   g.setFont(f);
    // DEBUG }
  }

  /*
  class ColorBox extends Box {
    Color color;
    ColorBox partner;
    ColorBox(Color c) {
      super(null, -1);
      this.color = c;
      if (c != null)
        partner = new ColorBox(null);
    }
    Box getRevertBox() {
      return partner;
    }
    @Override
    void paint(Graphics2D g) {
      if (partner != null)
        partner.color = (color == null ? null : g.getColor());
      if (color != null) 
        g.setColor(color);
    }
    @Override
    void layout(float x, float y, LineBreaker lines) {
      lines.boxes.add(this);
      xx = x;
      yy = y;
    }
  }
  */

  class NotBox extends Box {
    Box inner;
    LineBreaker innerLines = new LineBreaker();

    NotBox(Expression e, Box inner, int depth) {
      super(e, depth);
      this.inner = inner;
      w = inner.w + 2f;
      a = inner.a + 3f;
      d = inner.d;
      h = a + d;
    }
    @Override
    void layout(float x, float y, LineBreaker lines) {
      lines.boxes.add(this);
      xx = x;
      yy = y;
      innerLines.layout(inner, x+1, y);
    }
    @Override
    void move(float dx, float dy) {
      xx += dx; yy += dy; 
      for (Box b: innerLines.boxes)
        b.move(dx, dy);
    }
    @Override
    void paint(Graphics2D g) {
      g.draw(new Line2D.Float(xx+1, yy-a+1.5f, xx+w-1, yy-a+1.5f));
      innerLines.paint(g);
    }
  }

  class StringBox extends Box {
    String s;
    StringBox(String s, int depth) {
      super(null, depth);
      this.s = s;
      w = OP_FONT_METRICS.stringWidth(s);
      a = OP_ASCENT;
      d = OP_DESCENT;
      h = a+d;
    }
    @Override
    void layout(float x, float y, LineBreaker lines) {
      lines.boxes.add(this);
      xx = x;
      yy = y;
    }
    @Override
    void paint(Graphics2D g) {
      g.setFont(OP_FONT);
      g.drawString(s, xx, yy);
    }
  }
  class OpBox extends StringBox {
    int precedence;
    OpBox(String s, int precedence, int depth) {
      super(s, depth);
      this.precedence = precedence;
    }
  }

  static final float PAREN_GROWTH = 1.18f; // each paren nesting grows 18% larger
  static final float PAREN_SHIFT = 0.10f; // shift down 20% to make it look more balanced
  class ParenBox extends StringBox {
    float scale = PAREN_GROWTH;
    ParenBox(String s, int depth) {
      super(s, depth);
      rescale(0);
    }
    void rescale(float innerHeight) {
      scale = PAREN_GROWTH * Math.max(1.0f, innerHeight / TYP_HEIGHT);
      w = scale*OP_FONT_METRICS.stringWidth(s);
      a = scale*OP_ASCENT * PAREN_GROWTH;
      d = scale*OP_DESCENT * PAREN_GROWTH;
      h = a+d;
      float shift = h * PAREN_SHIFT;
      a -= shift;
      d += shift;
    }
    @Override
    void paint(Graphics2D g) {
      g.setFont(OP_FONT);
      AffineTransform xform = g.getTransform();
      g.scale(scale, scale);
      g.drawString(s, xx/scale, (yy+h*PAREN_SHIFT)/scale);
      g.setTransform(xform);
    }
  }
  class LeftParenBox extends ParenBox {
    LeftParenBox(int depth) {
      super("(", depth);
    }
  }
  class RightParenBox extends ParenBox {
    RightParenBox(int depth) {
      super(")", depth);
    }
  }

  class ConstBox extends Box {
    String s;
    ConstBox(Expression e, String s, int depth) {
      super(e, depth);
      this.s = s;
      w = TXT_FONT_METRICS.stringWidth(s);
      a = TXT_ASCENT;
      d = TXT_DESCENT;
      h = a+d;
    }
    @Override
    void layout(float x, float y, LineBreaker lines) {
      lines.boxes.add(this);
      xx = x;
      yy = y;
    }
    @Override
    void paint(Graphics2D g) {
      g.setFont(TXT_FONT);
      g.drawString(s, xx, yy);
    }
  }

  class VarBox extends Box {
    String name, sub;
    float sx, sy;
    VarBox(Expression e, String desc, int depth) {
      super(e, depth);
      try {
        Var.Bit v = Var.Bit.parse(desc);
        name = v.name;
        if (v.b >= 0)
          sub = "" + v.b;
      } catch (ParserException ex) {
        name = desc;
      }
      w = VAR_FONT_METRICS.stringWidth(name);
      a = VAR_ASCENT;
      d = VAR_DESCENT;
      if (sub != null) {
        float sw = SUB_FONT_METRICS.stringWidth(sub);
        float sd = SUB_DESCENT;
        sx = w;
        sy = d;
        w += sw;
        d += sd;
      }
      h = a+d;
    }
    @Override
    void layout(float x, float y, LineBreaker lines) {
      lines.boxes.add(this);
      xx = x;
      yy = y;
    }
    @Override
    void paint(Graphics2D g) {
      g.setFont(VAR_FONT);
      g.drawString(name, xx, yy);
      if (sub != null) {
        g.setFont(SUB_FONT);
        g.drawString(sub, xx+sx, yy+sy);
      }
    }
  }

  static final Color[] BBOX_SHADE = {
      new Color(200, 100, 100),
      new Color(100, 200, 100),
      new Color(100, 100, 200),
      new Color(200, 100, 200),
  };

  class BoundingBox extends Box {
    int depth; // debug
    Box[] insides;
    BoundingBox(Expression e, int depth, Box ...insides) {
      super(e, depth);
      this.insides = insides;
      for (Box b : insides) {
        a = Math.max(a, b.a);
        d = Math.max(d, b.d);
        w += b.w;
        if (b instanceof BoundingBox) { // debug
          int id = ((BoundingBox)b).depth;
          if (id >= depth)
            depth = id+1;
        }
      }
      h = a + d;
    }
    @Override
    void layout(float x, float y, LineBreaker lines) {
      lines.bboxes.add(this);
      xx = x;
      yy = y;
      // ColorBox c = (color == null ? null : new ColorBox(color));
      // if (c != null)
      //   c.layout(x, y, lines);
      int n = lines.boxes.size();
      for (Box b : insides) {
        b.layout(x, y, lines);
        x += b.w;
      }
      int m = lines.boxes.size();
      if (startColor != null && n < m) {
        lines.boxes.get(n).startColor = startColor;
        lines.boxes.get(n).endColorBox = lines.boxes.get(m-1);
      }
      // if (c != null)
      //   c.getRevertBox().layout(x, y, lines);
    }
    void paint(Graphics2D g) {
      debugShade(g, this, BBOX_SHADE, depth); // debug
    }
  }

  class BoxMaker implements Expression.Visitor<Box> {
    int depth = 1;

    @Override
    public Box visitBinary(Expression e, Expression a, Expression b, Expression.Op op) {
      Box bb_a;
      int opLvl = notation.opLvl[op.Id];
      int aLvl = a.getPrecedence(notation);
      if (aLvl < opLvl || (aLvl == opLvl && a.getOp() != op)) {
        ParenBox lparen = new LeftParenBox(depth);
        ParenBox rparen = new RightParenBox(depth);
        depth++;
        Box bb = a.visit(this);
        depth--;
        lparen.rescale(bb.h);
        rparen.rescale(bb.h);
        bb_a = new BoundingBox(a, depth, lparen, bb, rparen);
      } else {
        bb_a = a.visit(this);
      }

      Box mid = new OpBox(notation.opSym[op.Id], opLvl, depth);

      if (b == null)
        return new BoundingBox(e, depth, mid, bb_a);

      int bLvl = b.getPrecedence(notation);
      Box bb_b;
      if (bLvl < opLvl || (bLvl == opLvl && b.getOp() != op)) {
        ParenBox lparen = new LeftParenBox(depth);
        ParenBox rparen = new RightParenBox(depth);
        depth++;
        Box bb = b.visit(this);
        depth--;
        lparen.rescale(bb.h);
        rparen.rescale(bb.h);
        bb_b = new BoundingBox(b, depth, lparen, bb, rparen);
      } else {
        bb_b = b.visit(this);
      }

      return new BoundingBox(e, depth, bb_a, mid, bb_b);
    }

    @Override
    public Box visitConstant(Expression e, int value) {
      return new ConstBox(e, Integer.toString(value, 16), depth);
    }
    @Override
    public Box visitVariable(Expression e, String desc) {
      return new VarBox(e, desc, depth);
    }
    @Override
    public Box visitNot(Expression e, Expression a) {
      if (notation.opSym[Expression.Op.NOT.Id].equals("/")) { // overbar
        depth++;
        Box inner = a.visit(this);
        depth--;
        return new NotBox(e, inner, depth);
      } else {
        return visitBinary(e, a, null, Expression.Op.NOT);
      }
    }
  }

  Expression.Notation notation = Expression.Notation.ENGINEERING;
  void setNotation(Expression.Notation n) {
    notation = n;
  }

  Expression.Notation getNotation() {
    return notation;
  }

  static final Color[] BOX_SHADE = {
      new Color(200, 150, 100),
      new Color(100, 200, 120),
      new Color(150, 150, 200),
  };

  static class LineBreaker {
    final ArrayList<Box> boxes = new ArrayList<>();
    final ArrayList<BoundingBox> bboxes = new ArrayList<>();

    void paint(Graphics2D g) {
      for (Box b : boxes)
        b.paintAndColor(g);
    }

    // DEBUG void debugPaint(Graphics2D g) {
    // DEBUG   for (Box b : bboxes)
    // DEBUG     b.paint(g);
    // DEBUG   int i = 0;
    // DEBUG   for (Box b : boxes) {
    // DEBUG     debugShade(g, b, BOX_SHADE, i++);
    // DEBUG     b.paint(g);
    // DEBUG     g.setFont(SUB_FONT);
    // DEBUG     g.drawString(""+b.depth, b.xx, b.yy+b.d+5);
    // DEBUG   }
    // DEBUG }

    void layout(Box top, float x, float y) {
      boxes.clear();
      bboxes.clear();
      top.layout(x, y, this);
    }

    void fitToWidth(float width) {
      // DEBUG why = null;
      int n = boxes.size();
      int end = n-1;
      if (n < 1) //  || totalWidth(0, end) <= width)
        return;
      // p[s][end] is total penalty for s..end as one line
      // p[s][e] for e<end is penalty for a line s..e followed by other lines e+1.. ..end
      double[][] p = new double[n][n];
      // best[i] is either end (best is one line) or e<end (best is a linebreak after e)
      int[] best = new int[n];

      // DEBUG System.out.printf("There are %d boxes\n", n);
      for (int start = end; start >= 0; start--) {
        // DEBUG System.out.printf("Penalties for laying out %d..%d\n", start, end);
        float w = start == 0 ? width : (width - LEFT_INDENT);
        p[start][end] = penalty(start, end, w);
        // DEBUG System.out.printf("  as one line %d..%d --> %f\n", start, end, p[start][end]);
        best[start] = end;
        for (int endl = start; endl < end; endl++) {
          p[start][endl] = penalty(start, endl, w) + p[endl+1][best[endl+1]];
          // DEBUG System.out.printf("  as multiline %d..%d, %d.. ..%s --> %f\n",
          // DEBUG    start, endl, endl+1, end, p[start][endl]);
          if (p[start][endl] < p[start][best[start]])
            best[start] = endl;
        }
        // DEBUG System.out.printf("  best is %d..%d --> %f\n", start, best[start], p[start][best[start]]);
      }

      int startl = 0;
      int endl = best[startl];

      // DEBUG why = "";
      // DEBUG System.out.printf("  first line is %d..%d --> %f = %f + ...", startl, endl,
      // DEBUG    p[startl][endl], penalty(startl, endl, width));
      // DEBUG System.out.println(why);

      // first line stays at left margin, maybe moves up a little
      float xoff = 0;
      float yoff = -(maxAscent(0, end) - maxAscent(0, endl));
      for (int i = startl; i <= endl; i++)
        boxes.get(i).move(xoff, yoff);

      // each next line moves down, move way left, and indents a bit
      xoff = LEFT_INDENT;
      while (endl < end) {
        int next = best[endl+1];
        // DEBUG why = "";
        // DEBUG System.out.printf("  next line is %d..%d --> %f = %f + ... ",
        // DEBUG     endl+1, next, p[endl+1][next], penalty(endl+1, next, width-LEFT_INDENT));
        // DEBUG System.out.println(why);
        xoff -= totalWidth(startl, endl);
        yoff += maxDescent(startl, endl) + LEADING + maxAscent(endl+1, next);
        for (int i = endl+1; i <= next; i++) {
          boxes.get(i).move(xoff, yoff);
        }
        startl = endl+1;
        endl = next;
      }
      // DEBUG System.out.println("Layout fit into width " + width + " + and height " + getHeight());
    }

    float maxAscent(int s, int e) {
      float a = 0;
      for (int i = s; i <= e; i++)
        a = Math.max(a, boxes.get(i).a);
      return a;
    }

    float maxDescent(int s, int e) {
      float d = 0;
      for (int i = s; i <= e; i++)
        d = Math.max(d, boxes.get(i).d);
      return d;
    }

    float totalWidth(int s, int e) {
      float w = 0;
      for (int i = s; i <= e; i++)
        w += boxes.get(i).w;
      return w;
    }

    float getHeight() {
      if (boxes.size() == 0)
        return DEFAULT_HEIGHT;
      Box last = boxes.get(boxes.size()-1);
      return last.yy + last.d;
    }

    float getWidth() {
      if (boxes.size() == 0)
        return DEFAULT_WIDTH;
      float w = 0;
      for (Box b : boxes)
        w = Math.max(w, b.xx + b.w);
      return w;
    }

    // DEBUG String why;
    double penalty(int s, int e, float width) {
      double p = 0;
      float w = totalWidth(s, e);
      // Overly-long lines get penalized harshly.
      // Short lines get penalized using square, so that two moderate lines is
      // preferred to one shorter and one longer line.
      if (w > width) {
        p += 10000 + Math.pow((w-width)/width, 2);
        // DEBUG if (why != null) why += String.format(" %f b/c overlong", p);
      } else {
        p += 10 * Math.pow((width-w)/width, 2);
        // DEBUG if (why != null) why += String.format(" %f b/c too short", p);
      }
      if (e == boxes.size()-1)
        return p; // no other penalties if this is the last line
      // Breaking after an operator is worse than just before it, and
      // penalties get somewhat worse at higher depths or with higher-precedence
      // operators.
      Box left = boxes.get(e);
      Box right = boxes.get(e+1);
      if (left instanceof LeftParenBox || right instanceof RightParenBox) { // break near wrong paren
        p += (150 * left.depth);
        // DEBUG if (why != null) why += String.format(" %f b/c near paren", p);
      }
      if (left instanceof OpBox) { // break just after an operator
        p += (100 + ((OpBox)left).precedence) * left.depth;
        // DEBUG if (why != null) why += String.format(" %f b/c after op", p);
      } else if (right instanceof OpBox) { // break just before an operator
        p += (50 + ((OpBox)right).precedence) * left.depth;
        // DEBUG if (why != null) why += String.format(" %f b/c before op", p);
      }
      return p;
    }

  }

  static void debugShade(Graphics2D g, Box b, Color[] colors, int idx) {
    Color c = g.getColor();
    g.setColor(colors[idx % colors.length]);
    g.fill(new Rectangle2D.Float(b.xx, b.yy-b.a, b.w, b.h));
    g.setColor(g.getColor().darker());
    g.draw(new Rectangle2D.Float(b.xx, b.yy-b.a, b.w, b.h));
    g.setColor(c);
  }

  static final int DEFAULT_HEIGHT = 35;
  static final int DEFAULT_WIDTH = 100;
  static final int LEADING = 6; // gap between lines
  static final int LEFT_MARGIN = 15; // margin at left of every line
  static final int LEFT_INDENT = 25; // extra margin for continuation lines

  boolean centered = false;
  int width = 100;
  LineBreaker lines = new LineBreaker();

  public ExpressionRenderer() { }

  public void setCentered(boolean b) {
    centered = true;
  }

  public void setExpressionWidth(int w) {
    if (centered)
      width = w - 2*LEFT_MARGIN;
    else
      width = w - LEFT_MARGIN;
  }

  public void setExpression(String name, Expression expr) {
    expr = Expressions.eq(Expressions.variable(name), expr);
    Box top = expr.visit(new BoxMaker());
    // for now, just lay things out on one line, don't break any boxes
    lines.layout(top, 0, top.a);
    lines.fitToWidth(width);
  }

  public void setError(String name, String msg) {
    ArrayList<Box> boxes = new ArrayList<>();
    boxes.add(new VarBox(null, name, 0));
    boxes.add(new StringBox(" = ", 0));
    boxes.add(new StringBox("{", 0));
    for (String word : msg.split("\\s+"))
      boxes.add(new StringBox(" " + word, 1));
    boxes.add(new StringBox(" }", 0));
    Box[] a = boxes.toArray(new Box[boxes.size()]);
    Box top = new BoundingBox(null, 0, a);
    lines.layout(top, 0, top.a);
    lines.fitToWidth(width);
  }

  public void clear() {
    Box top = new BoundingBox(null, 0, new Box[] { new StringBox(" ", 0) });
    lines.layout(top, 0, top.a);
    lines.fitToWidth(width);
  }

  public static class NamedExpression {
    public String name;
    public Expression expr; // can be null
    public String exprString;
    public String err;
    NamedExpression(String n) {
      name = n;
    }
    NamedExpression(String n, Expression e, String s) {
      name = n;
      expr = e;
      exprString = s;
    }
  }

  public void setExpression(NamedExpression e) {
    if (e.expr != null)
      setExpression(e.name, e.expr);
    else
      setError(e.name, e.err != null ? e.err : "unspecified");
  }


  public int getExpressionHeight() {
    return (int)Math.ceil(lines.getHeight());
  }

  public int getExpressionWidth() {
    return (int)Math.ceil(lines.getWidth() + (centered?1:2)*LEFT_MARGIN);
  }

  public Rectangle getExpressionBounds() {
    int w = getExpressionWidth();
    int h = getExpressionHeight();
    int x = centered ? Math.max(0, (getWidth() - w)/2) : LEFT_MARGIN;
    int y = (getHeight() - h)/2;
    return new Rectangle(x, y, w, h);
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintBorder(g);
    
    Graphics2D g2 = (Graphics2D)g;
    Object oldHints[] = GraphicsUtil.setRenderingHintsForNiceText(g2);
    AffineTransform xform = g2.getTransform();

    if (centered)
      g2.translate(
          Math.max(0, (getWidth() - lines.getWidth()) / 2),
          (getHeight() - lines.getHeight())/2);
    else
      g2.translate(LEFT_MARGIN, (getHeight() - lines.getHeight())/2);
    g2.setColor(getForeground());
    lines.paint(g2);

    g2.setTransform(xform);
    GraphicsUtil.restoreRenderingHints(g2, oldHints);
  }

  Colorizer colorizer;

  public void setColorizer(Colorizer c) {
    colorizer = c;
  }

  public interface Colorizer {
    public Color colorFor(Expression e);
    // public Color colorForTopLevel(int termNumber); 
  }

}
