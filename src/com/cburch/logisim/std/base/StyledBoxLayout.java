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

import java.util.ArrayList;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.LineMetrics;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.Locale;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.GraphicsUtil;

import static com.cburch.logisim.util.GraphicsUtil.ALIGN;

// StyledBoxLayout handles text rendering for Markdownish.
public class StyledBoxLayout implements Text.LayoutEngine {

  final String text;
  final TextStyling styling;
  final int textWidth; // excludes margins
  
  Bounds bounds;  // accurate once layout is complete, includes margin
  ArrayList<VisualLine> lines;

  public StyledBoxLayout(String t, int tw, TextStyling sty) {
    text = t;
    textWidth = tw; // must be positive, includes margins
    styling = sty;

    lines = new ArrayList<>();
    Markdownish md = new Markdownish(text, styling);
    bounds = layoutMarkdown(md);
  }

  public Bounds getBounds(Location loc) {
    return bounds.translate(loc);
  }

  public void drawText(Graphics2D g, Location loc) {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
    g.setFont(styling.font);
    g.setColor(styling.color);
    for (VisualLine line : lines) {
      for (VisualCell cell : line.cells) {
        if (cell.border) {
          g.setColor(styling.color);
          g.draw(new Rectangle2D.Float(loc.x + cell.x, loc.y + cell.y, cell.w, cell.h));
        }
        for (VisualBox box : cell.boxes) {
          if (box.marker != null) {
            // no layout, only a marker
            box.marker.draw(g, loc.x + box.x, loc.y + box.baselineY);
          } else if (box.accent == null) {
            box.layout.draw(g, loc.x + box.x, loc.y + box.baselineY);
          } else if (box.accent.type == TextStyling.AccentType.UNDERLINE) {
            float px = box.accent.size.px(box.font);
            g.setColor(box.accent.color);
            g.fill(new Rectangle2D.Float(loc.x + box.x,
                  loc.y + box.baselineY + box.layout.getDescent(),
                  box.layout.getVisibleAdvance(), px));
            g.setColor(styling.color);
            box.layout.draw(g, loc.x + box.x, loc.y + box.baselineY);
          } else if (box.accent.type == TextStyling.AccentType.LEADERBLOCK) {
            float px = box.accent.size.px(box.font);
            float px2 = (float)box.font.getStringBounds(" ", frc).getWidth();
            g.setColor(box.accent.color);
            g.fill(new Rectangle2D.Float(loc.x + box.x, loc.y + box.y,
                  px, box.textSize()));
            g.setColor(styling.color);
            box.layout.draw(g, loc.x + box.x + px + px2, loc.y + box.baselineY);
          }
        }
      }
    }
  }
  
  private Bounds layoutMarkdown(Markdownish md) {
    Box box = layoutBody(md.body, 0, 0, 0, textWidth, null);
    float w = box.width; // includes possible overflow
    float h = box.height;
    return Bounds.create(0, 0, (int)Math.ceil(w), (int)Math.ceil(h));
  }

  // BODY block, these have 1+ blocks of various types
  // Place blocks (including their margins) starting at y, but topmost block's
  // margin can collapse with recentClear space above us.
  private Box layoutBody(Markdownish.Block body, float x, float y, float recentClear, float bodyWidth, ItemMarker outerMarker) {
    
    float mt = body.margin[0].px(body.font); // body margin top
    float mr = body.margin[1].px(body.font); // body margin right
    float mb = body.margin[2].px(body.font); // body margin bottom
    float ml = body.margin[3].px(body.font); // body margin left
    
    mt = Math.max(0, mt - recentClear); // collapse body margin top
    recentClear += mt;

    if (body.blocks.isEmpty()) {
      // Likely never happens: no blocks, so just body margins
      // TODO: if this does ever happen, should probably place the outer marker,
      // if present, on a line by itself with no content.
      return new Box(x, y, bodyWidth, mt + 0 + mb, mb);
    }

    float h = mt; // content height so far
    float w = bodyWidth;

    for (Markdownish.Block block : body.blocks) {
      float bx = x + ml;
      float by = y + h;
      float bw = bodyWidth - ml - mr;
      Box box;
      if (block.type == Markdownish.BlockType.LIST) {
        box = layoutList(block, bx, by, recentClear, bw, outerMarker);
      } else if (block.type == Markdownish.BlockType.TABLE) {
        box = layoutTable(block, bx, by, recentClear, bw, outerMarker);
      } else if (block.type == Markdownish.BlockType.BODY) {
        box = layoutBody(block, bx, by, recentClear, bw, outerMarker);
      } else { // PARAGRAPH, HEADER, FENCED_CODE
        box = layoutLeafBlock(block, bx, by, recentClear, bw, outerMarker);
      }
      outerMarker = null;
      w = Math.max(w, ml + box.width + mr);
      recentClear = box.bottomClear;
      h += box.height;
    }

    mb = Math.max(0, mb - recentClear); // collapse body margin bottom
    recentClear += mb;

    h += mb;
    return new Box(x, y, w, h, recentClear);
  }

  // LIST, these have 1+ BODY blocks
  private Box layoutList(Markdownish.Block list, float x, float y, float recentClear, float listWidth, ItemMarker outerMarker) {
    
    float mt = list.margin[0].px(list.font);
    float mr = list.margin[1].px(list.font);
    float mb = list.margin[2].px(list.font);
    float ml = list.margin[3].px(list.font);
    
    mt = Math.max(0, mt - recentClear); // collapse margin top
    recentClear += mt;
    
    int n = list.blocks.size();

    if (n == 0) {
      // Likely never happens: no blocks, so just list margins
      // TODO: if this does ever happen, should probably place the outer
      // outerMarker, if present, on a line by itself with no content.
      return new Box(x, y, listWidth, mt + 0 + mb, mb);
    }

    // Determine item markers
    ItemMarker marker[] = new ItemMarker[n];
    float indent = 0;
    if (list.bullet.equals(")") || list.bullet.equals(".")) {
      for (int i = 0; i < n; i++) {
        marker[i] = new Num(list.startnum + i, list.bullet, list.font);
        indent = Math.max(indent, marker[i].width());
      }
    } else {
      Bullet b = new Bullet(list.bullet, list.font);
      for (int i = 0; i < n; i++)
        marker[i] = b;
      indent = b.width();
    }

    float h = mt; // content height so far
    float w = listWidth;

    if (outerMarker != null) {
      lines.add(new VisualLine(new VisualCell(new VisualBox(outerMarker, x - outerMarker.width(), y + h))));
      h += outerMarker.height(); // occupies vertical space
      outerMarker = null;
    }

    for (int i = 0; i < n; i++) {
      Markdownish.Block block = list.blocks.get(i);

      float bx = x + ml + indent;
      float by = y + h;
      float bw = listWidth - ml - mr - indent;
      Box box = layoutBody(block, bx, by, recentClear, bw, marker[i]);
      w = Math.max(w, ml + box.width + mr);
      recentClear = box.bottomClear;
      h += box.height;
    }

    mb = Math.max(0, mb - recentClear); // collapse body margin bottom
    recentClear += mb;

    h += mb;
    return new Box(x, y, w, h, recentClear);
  }

  // TABLE, these have 1+ ROW blocks
  private Box layoutTable(Markdownish.Block table, float x, float y, float recentClear, float tableWidth, ItemMarker outerMarker) {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
    
    float mt = table.margin[0].px(table.font); // table margin top
    float mr = table.margin[1].px(table.font); // table margin right
    float mb = table.margin[2].px(table.font); // table margin bottom
    float ml = table.margin[3].px(table.font); // table margin left
    
    mt = Math.max(0, mt - recentClear); // collapse table margin top
    recentClear += mt;

    if (table.blocks.isEmpty() || table.blocks.get(0).type != Markdownish.BlockType.ROW) {
      // Likely never happens: no blocks, so just table margins
      // TODO: if this does ever happen, should probably place the outer marker,
      // if present, on a line by itself with no content.
      return new Box(x, y, tableWidth, mt + 0 + mb, mb);
    }

    if (outerMarker != null) {
      lines.add(new VisualLine(new VisualCell(new VisualBox(outerMarker, x - outerMarker.width(), y + mt))));
      // occupies no space
      outerMarker = null;
    }
    
    // Determine column widths, for now let's just use width/N
    Markdownish.Block hdr = table.blocks.get(0); // ROW of cell phrases
    int numCols = hdr.phrases.size();
    float colWidths[] = new float[numCols];
    int colWeights[] = new int[numCols];
    float totalWidth = 0;
    for (int i = 0; i < numCols; i++) {
      colWeights[i] = Math.max(1, hdr.phrases.get(i).cellWidth);
      for (Markdownish.Block row : table.blocks) {
        if (row.type != Markdownish.BlockType.ROW)
          continue;
        if (row.phrases.size() <= i)
          continue;
        Markdownish.Phrase phrase = row.phrases.get(i);
        float rPad = row.margin[1].px(row.font);
        float lPad = row.margin[3].px(row.font);
        AttributedCharacterIterator it = phrase.buildAttributedString().getIterator();
        TextLayout layout = new TextLayout(it, frc);
        float cellWidth = layout.getVisibleAdvance();
        if (cellWidth < 1f) cellWidth = 0; // empty column, collapse to narrow bar
        else cellWidth += lPad + rPad;
        colWidths[i] = Math.max(colWidths[i], cellWidth);
      }
      totalWidth += colWidths[i];
    }
    float availWidth = tableWidth - ml - mr;
    if (totalWidth > availWidth) {
      colWidths = calcColumnWidths(availWidth, colWidths, colWeights);
    }

    float h = mt; // content height so far
    float w = tableWidth;

    for (Markdownish.Block row : table.blocks) {
      if (row.type != Markdownish.BlockType.ROW)
        continue;
      float bx = x + ml;
      float by = y + h;
      float bw = tableWidth - ml - mr;
      Box box = layoutTableRow(row, bx, by, bw, colWidths);
      w = Math.max(w, ml + box.width + mr);
      h += box.height;
    }

    recentClear = mb;
    h += mb;
    return new Box(x, y, w, h, recentClear);
  }

  // ROW ... these have a 1+ phrases
  private Box layoutTableRow(Markdownish.Block row, float x, float y, float rowWidth, float colWidths[]) {
    
    if (row.phrases.isEmpty()) {
      // Likely never happens: no cells, so just an empty row
      return new Box(x, y, rowWidth, 0, 0);
    }

    float tPad = row.margin[0].px(row.font);
    float rPad = row.margin[1].px(row.font);
    float bPad = row.margin[2].px(row.font);
    float lPad = row.margin[3].px(row.font);

    VisualLine vl = new VisualLine(x, y);
    int i = 0;
    for (Markdownish.Phrase phrase : row.phrases) {
      float colWidth = colWidths[i++];
      float lPadCell = colWidth > 0 ? lPad : 1.5f;
      float rPadCell = colWidth > 0 ? rPad : 1.5f;
      float cellWidth = colWidth - lPadCell - rPadCell;
      VisualCell vc = layoutPhrase(phrase, x + vl.w + lPadCell, y + tPad, cellWidth, true, null, null);
      vc.border = true;
      vc.x -= lPadCell;
      vc.w += lPadCell + rPadCell;
      vc.y -= tPad;
      vc.h += tPad + bPad;
      if (phrase.cellAlign != ALIGN.H_LEFT) {
        for (VisualBox vb : vc.boxes) {
          float textWidth = vb.layout.getVisibleAdvance();
          if (phrase.cellAlign == ALIGN.H_RIGHT)
            vb.x += (cellWidth - textWidth);
          else // ALIGN.H_CENTER
            vb.x += (cellWidth - textWidth)/2;
        }
      }
      vl.add(vc);
    }

    lines.add(vl);

    int recentClear = 0;
    return new Box(x, y, vl.w, vl.h, recentClear);
  }

  // HEADER, PARAGRAPH, FENCED_CODE ... these have a 1+ phrases
  private Box layoutLeafBlock(Markdownish.Block block, float x, float y, float recentClear, float blockWidth, ItemMarker outerMarker) {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;

    float mt = block.margin[0].px(block.font);
    float mr = block.margin[1].px(block.font);
    float mb = block.margin[2].px(block.font);
    float ml = block.margin[3].px(block.font);
    
    mt = Math.max(0, mt - recentClear); // collapse block margin top

    if (block.phrases.isEmpty()) {
      // Likely never happens: no phrases, so just block margins
      // TODO: if this does ever happen, should probably place the outer marker,
      // if present, on a line by itself with no content.
      return new Box(x, y, blockWidth, mt + 0 + mb, mb);
    }
    
    TextStyling.Accent accent = block.getAccent();

    float h = mt;
    float w = blockWidth;
    float pw = blockWidth - ml - mr;

    for (Markdownish.Phrase phrase : block.phrases) {
      VisualCell vc = layoutPhrase(phrase, x + ml, y + h, pw,
          block.wrapped(), block.getAccent(), outerMarker);
      lines.add(new VisualLine(vc));
      outerMarker = null;
      accent = null;
      h += vc.h;
    }

    h += mb;
    recentClear = mb;
    return new Box(x, y, w, h, recentClear);
  }

  private VisualCell layoutPhrase(Markdownish.Phrase phrase, float x, float y, float phraseWidth, boolean wrapped, TextStyling.Accent accent, ItemMarker outerMarker) {
    FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;

    float h = 0;
    float pw = phraseWidth;

    AttributedString astr = phrase.buildAttributedString();
    AttributedCharacterIterator it = astr.getIterator();
    Font font = phrase.font;

    // underline accent is added to the last visual line of a phrase
    // blockleading accent is added to all visual lines of a phrase (maybe only first is better?)
    float accentExtraY = 0f;
    TextStyling.Accent uAccent = null, bAccent = null;
    if (accent != null && accent.type == TextStyling.AccentType.UNDERLINE) {
      uAccent = accent;
      accentExtraY = accent.size.px(font);
    } else if (accent != null && accent.type == TextStyling.AccentType.LEADERBLOCK) {
      bAccent = accent;
      float aw = accent.size.px(font) + (float)font.getStringBounds(" ", frc).getWidth();
      // The accent leaderblock takes up some of the phraseWidth.
      pw -=  aw;
    }

    VisualCell vc = new VisualCell(x, y);

    if (!wrapped) {
      // e.g. phrase within a FENCED_CODE block does not wrap
      int left = it.getBeginIndex();
      int right = it.getEndIndex();
      TextLayout layout = new TextLayout(it, frc);

      if (outerMarker != null) {
        float dx = -outerMarker.width();
        float dy = layout.getAscent() - outerMarker.ascent();
        vc.add(new VisualBox(outerMarker, x + dx, y + dy));
        // occupies no space
        outerMarker = null;
      }

      VisualBox vb = new VisualBox(layout, astr, left, right, x, y, phraseWidth, accent, font);
      vc.add(vb);
    } else {
      // e.g. phrase within a PARAGRAPH should auto-wraps
      LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
      measurer.setPosition(it.getBeginIndex());

      int left = measurer.getPosition();
      while (left < it.getEndIndex()) {
        TextLayout layout = measurer.nextLayout(Math.max(pw, Text.TEXT_MIN_WIDTH));
        int right = measurer.getPosition();
        boolean last = (right >= it.getEndIndex());

        if (outerMarker != null) {
          float dx = -outerMarker.width();
          float dy = layout.getAscent() - outerMarker.ascent();
          lines.add(new VisualLine(new VisualCell(new VisualBox(outerMarker, x + dx, y + h + dy))));
          // occupies no space
          outerMarker = null;
        }

        VisualBox vb = new VisualBox(layout, astr, left, right, x, y + vc.h,
          phraseWidth, bAccent != null ? bAccent : last ? uAccent : null, font);
        vc.add(vb);

        left = right;
      }
    }

    vc.h += accentExtraY;
    return vc;
  }




  abstract class ItemMarker {
    abstract void draw(Graphics2D g, float x, float y);
    abstract float width();
    abstract float height();
    abstract float ascent();
  }
  class Num extends ItemMarker {
    final Font font;
    final String txt; // e.g. "  42) "
    final float w, h, ascent;

    Num(int number, String bullet, Font font) {
      this.font = font;
      this.txt = "  " + number + bullet + " ";
      FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
      Rectangle2D rect = font.getStringBounds(txt, frc);
      w = (float)rect.getWidth(); // FIXME: allow styling?
      h = (float)rect.getHeight();
      LineMetrics lm = font.getLineMetrics(txt, frc);
      ascent = lm.getAscent();
    }

    float width() { return w; }
    float height() { return h; }
    float ascent() { return ascent; }

    void draw(Graphics2D g, float x, float y) {
      // g.setColor(java.awt.Color.CYAN);
      // g.draw(new Rectangle2D.Float(x, y, w, h));
      g.setColor(styling.color); // FIXME: allow styling?
      g.setFont(font);
      g.drawString(txt, x, y + ascent);
    }
  }
  class Bullet extends ItemMarker {
    final String bullet;
    final float w, h, ascent;

    Bullet(String b, Font font) {
      FontRenderContext frc = GraphicsUtil.CANVAS_FONT_RENDER_CONTEXT;
      LineMetrics lm = font.getLineMetrics("Ag", frc);
      ascent = lm.getAscent();
      h = lm.getHeight();
      w = h*2f; // FIXME: allow styling?
      bullet = b;
    }

    float width() { return w; }
    float height() { return h; }
    float ascent() { return ascent; }

    void draw(Graphics2D g, float x, float y) {
      // g.setColor(java.awt.Color.CYAN);
      // g.draw(new Rectangle2D.Float(x, y, w, h));
      g.setColor(styling.color); // FIXME: allow styling?
      x += w*0.5f;
      y += h*0.6f;
      if (bullet.equals("*")) {
        float d = h*0.45f;
        float r = d/2f;
        g.fill(new Ellipse2D.Float(x-r, y-r, d, d));
      } else if (bullet.equals("-")) {
        float ww = h*0.40f;
        float hh = h*0.18f;
        g.fill(new Rectangle2D.Float(x-ww/2, y-hh/2, ww, hh));
      } else { // "+"
        float d = h*0.6f;
        float r = d/2f;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x,     y - r); // top
        p.lineTo(x + r, y    ); // right
        p.lineTo(x,     y + r); // bottom
        p.lineTo(x - r, y    ); // left
        p.closePath();
        g.fill(p);
      }
    }
  }

  // A horizontal row of cells, all taking up same height
  static class VisualLine {
    ArrayList<VisualCell> cells;
    float x, y; // top left corner for entire row of cells
    float w; // total width = sum of cell widths
    float h; // total height = max of cell heights
    VisualLine(float x, float y) {
      this.cells = new ArrayList<>();
      this.x = x;
      this.y = y;
      this.w = this.h = 0;
    }
    VisualLine(VisualCell vc) {
      cells = new ArrayList<>();
      cells.add(vc);
      x = vc.x;
      y = vc.y;
      w = vc.w;
      h = vc.h;
    }
    void add(VisualCell cell) {
      cells.add(cell);
      w += cell.w;
      h = Math.max(h, cell.h);
    }
  }

  // a vertical stack of boxes, all taking up same width
  static class VisualCell {
    ArrayList<VisualBox> boxes;
    float x, y; // top left corner for entire stack of boxes
    float w; // total width = max of box widths
    float h; // total height = sum of box heights
    boolean border;
    VisualCell(VisualBox vb) {
      boxes = new ArrayList<>();
      boxes.add(vb);
      x = vb.x;
      y = vb.y;
      w = vb.width;
      h = vb.height();
      border = false;
    }
    VisualCell(float xx, float yy) {
      boxes = new ArrayList<>();
      x = xx;
      y = yy;
      w = 0;
      h = 0;
      border = false;
    }
    void add(VisualBox vb) {
      boxes.add(vb);
      w = Math.max(w, vb.width);
      h += vb.height();
    }
  }

  // horizontal layout for one line of text
  static class VisualBox {
    final TextLayout layout;
    final AttributedString astr;
    final int start, end;     // index range within astr for this line
    /*final*/ float x, y;         // top left corner
    final float width; 
    final float baselineY;    // y+ascent, where layout.draw is called
    final ItemMarker marker;  // for list items
    final TextStyling.Accent accent;
    final Font font;          // only needed for computing accent sizing

    VisualBox(ItemMarker marker, float x, float y) {
      layout = null;
      astr = null;
      start = end = 0;
      this.x = x;
      this.y = y;
      width = 0;
      baselineY = y;
      this.marker = marker;
      accent = null;
      font = null;
    }

    VisualBox(TextLayout layout, AttributedString astr, int s, int e, float x, float y, float w, TextStyling.Accent a, Font f) {
      this.layout = layout; // does not include newline
      this.astr = astr;
      this.start = s;
      this.end = e;
      this.x = x;
      this.y = y;
      this.width = w;
      this.accent = a;
      this.font = f;
      marker = null;
      this.baselineY = y + layout.getAscent();
    }

    float bottomY() { return layout == null ? baselineY : baselineY + layout.getDescent() + layout.getLeading(); }
    float textSize() { return layout == null ? 0 : layout.getAscent() + layout.getDescent(); }
    float height() { return layout == null ? 0 : textSize() + layout.getLeading(); }

    public int sourcePositionForX(float px) { // snaps to some nearby grapheme boundary
      float relX = px - x, relY = 0; // 0 means baseline
      TextHitInfo hit = layout.hitTestChar(relX, 0);
      int pos = start + hit.getInsertionIndex();
      pos = clamp(pos, start, end);

      AttributedCharacterIterator it = astr.getIterator();
      int begin = it.getBeginIndex();
      int end = it.getEndIndex();
      if (end == begin)
        return 0; // empty text, should not be possible
      pos = clamp(pos, begin, end); // should be in range already, but just in case
      it.setIndex(pos == end ? end - 1 : pos);
      Markdownish.Span span = (Markdownish.Span)it.getAttribute(Markdownish.SPAN_ID);
      if (span == null)
        return 0; // unmarked character, huh?
      int runStart = it.getRunStart(Markdownish.SPAN_ID);
      int runLimit = it.getRunLimit(Markdownish.SPAN_ID);
      int offset = clamp(pos - runStart, 0, runLimit - runStart + 1); // clamp just in case
      int srcIdx = clamp(span.start + offset, span.start, span.end+1); // clamp just in case
      return srcIdx;
    }

    public boolean getBiasForX(float px) {
      return true; // reverse bias, good enough
    }

    private static int clamp(int v, int lo, int hi) {
      return (v < lo) ? lo : (v > hi ? hi : v);
    }

  }

  public Text.CaretPosition caretPositionForPoint(int px, int py) {
    VisualLine vl = lineForY(py);
    VisualCell vc = cellForX(vl, px);
    VisualBox vb = boxForY(vc, py);
    int cursor = snapToGraphemeBoundary(vb.sourcePositionForX(px));
    boolean revBias = vb.getBiasForX(px);
    return new Text.CaretPosition(bounds, cursor, revBias);
  }

  public VisualLine lineForY(int py) {
    for (VisualLine vl : lines) {
      if (vl.h != 0 && py < vl.y + vl.h) {
        return vl;
      }
    }
    return lines.get(lines.size() - 1);
  }

  public VisualCell cellForX(VisualLine vl, int px) {
    for (VisualCell vc : vl.cells) {
      if (px < vc.x + vc.w) {
        return vc;
      }
    }
    return vl.cells.get(vl.cells.size() - 1);
  }

  public VisualBox boxForY(VisualCell vc, int py) {
    for (VisualBox vb : vc.boxes) {
      if (vb.layout != null && py < vb.bottomY()) {
        return vb;
      }
    }
    return vc.boxes.get(vc.boxes.size() - 1);
  }
  
  public int snapToGraphemeBoundary(int pos) {
    if (text.isEmpty())
      return 0;
    BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
    bi.setText(text);
    if (bi.isBoundary(pos)) {
      return pos;
    } else { // reverse bias
      int prev = bi.preceding(pos);
      return (prev == BreakIterator.DONE || prev < 0) ? 0 : prev;
    }
  }

  static final class Box {
    float x, y, width, height; // includes all internal margins and content
    float bottomClear; // internal space at bottom of box eligible for margin collapse
    Box(float xx, float yy, float ww, float hh, float bc) {
      x = xx; y = yy; width = ww; height = hh; bottomClear = bc;
    }
  }

  static float[] calcColumnWidths(float availWidth, float[] colWidth, int[] colWeight) {
    int numCols = colWidth.length;
    float[] result = new float[numCols];
    boolean[] fixed = new boolean[numCols];
    float remainingWidth = availWidth;
    float remainingWeight = 0;
    for (int w : colWeight)
      remainingWeight += w;

    // Iteratively fix columns that don't need their full weighted share.
    // A column is ok if its natural width doesn't exceed its proportional share.
    // Repeat, since fixing one column increases the share for others.
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int i = 0; i < numCols; i++) {
        if (fixed[i]) continue;
        float share = remainingWidth * colWeight[i] / remainingWeight;
        if (colWidth[i] <= share) {
          result[i] = colWidth[i];
          fixed[i] = true;
          remainingWidth -= colWidth[i];
          remainingWeight -= colWeight[i];
          changed = true;
        }
      }
    }

    // Remaining unfixed columns need wrapping, give each its share.
    for (int i = 0; i < numCols; i++) {
      if (!fixed[i])
        result[i] = remainingWidth * colWeight[i] / remainingWeight;
    }

    return result;
  }
}
