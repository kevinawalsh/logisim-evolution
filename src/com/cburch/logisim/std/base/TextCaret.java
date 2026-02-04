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
import java.util.LinkedList;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.List;

import com.cburch.logisim.Main;
import com.cburch.logisim.comp.TextFieldCaret;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.tools.Caret;
import com.cburch.logisim.tools.CaretEvent;
import com.cburch.logisim.tools.CaretListener;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.UndoRedo;

// This class is like a combination of TextField and TextFieldCaret, but handles
// multi-line text, and eliminates a bunch of indirection.
//
// For Text (and Callout), the relevant objects are...
//   Text (the factory) contains:
//      method to create and configure TextInstanceComponent objects
//   TextInstanceComponent (the instances) contains:
//      method to create TextCaret
//      TextAttributes, with the text, halign, valign, font, etc.
//   TextCaret contains:
//      pointer to the same TextAttributes, with the text, halign, valign, font, etc.
//      location x, y
//      graphics
//      canvas
//      cursor positioning info
//
// By comnparison, for other components with (single-line) text for labels...
//   Varioud factory classes contain:
//      method to create and configure InstanceComponent objects
//   InstanceComponent contains:
//      InstanceTextField
//   InstanceTextField is a shim/helper layer that contains:
//      location x, y
//      canvas
//      pointer back to the InstanceComponent
//      copies of halign, valign from the attributes or wherever
//      pointers to label/text and font attributes
//      and a TextField
//   TextField is a thin wrapper that contains:
//     location x, y (again)
//     copies of halign, valign (again)
//     font
//     text
//     method to return a TextFieldCaret
//   TextFieldCaret contains:  
//     pointer back to the field
//     graphics
//     old text, new text (transient, used during editing?)
//     canvas (again)
//     cursor positioning info

class TextCaret implements Caret, AttributeListener {

  public static final Color EDIT_BACKGROUND = TextFieldCaret.EDIT_BACKGROUND;
  public static final Color EDIT_BORDER = TextFieldCaret.EDIT_BORDER;
  public static final Color SELECTION_BACKGROUND = TextFieldCaret.SELECTION_BACKGROUND;

  // From TextFieldCaret
  private LinkedList<CaretListener> listeners = new LinkedList<CaretListener>();
  private TextAttributes attrs;
  private Graphics g;
  private String oldText;
  private String curText;
  private int cursor, anchor; // text between cursor and anchor is selected
  private TextCaretEditHandler editMenuHandler;
  private UndoRedo log = new UndoRedo();
  private Canvas canvas;
  private float preferredCaretX = Float.NaN; // remembered x for vertical movement

  // used during mouse selection
  private boolean selectByWord = false;
  private boolean selectByLine = false;
  private int selectOrigin = 0;

  // from TextField
  private Location loc;
  
  public TextCaret(TextAttributes attrs, Canvas canvas, Location loc, int px, int py) {
    this.attrs = attrs;
    this.canvas = canvas;
    this.g = canvas.getGraphics();
    this.oldText = this.curText = attrs.getText();
    this.loc = loc;
    this.cursor = this.anchor = findCaret(px, py);
    
    editMenuHandler = new TextCaretEditHandler();

    // attrs.addAttributeListener(this); // FIXME: not supported yet
  }

  @Override
  public EditHandler getEditHandler() { return editMenuHandler; }

  @Override
  public void addCaretListener(CaretListener l) { listeners.add(l); }

  @Override
  public void removeCaretListener(CaretListener l) { listeners.remove(l); }

  @Override
  public void cancelEditing() {
    CaretEvent e = new CaretEvent(this, oldText, oldText);
    curText = oldText;
    cursor = anchor = curText.length();
    for (CaretListener l : new ArrayList<CaretListener>(listeners))
      l.editingCanceled(e);
    // attrs.removeAttributeListener(this); // FIXME: not supported yet
    log.clear();
    editMenuHandler.computeEnabled();
  }

  @Override
  public void commitText(String text) {
    curText = text;
    cursor = anchor = curText.length();
    log.clear();
    // attrs.setText(text); // action will handle it?
    editMenuHandler.computeEnabled();
  }

  @Override
  public void draw(Graphics g) {
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    int x = loc.x;
    int y = loc.y;
    Font font = attrs.getFont();
    if (font != null)
      g.setFont(font);

    // draw boundary
    Bounds area = getBounds(g).expand(Text.PAD);
    area.fill(g, EDIT_BACKGROUND);
    area.draw(g, EDIT_BORDER);

    // draw selection
    BoxLayout box = computeLayout(g);
    List<VisualLine> vlines = box.lines;
    if (cursor != anchor) {
      int selA = Math.min(cursor, anchor);
      int selB = Math.max(cursor, anchor);

      Graphics2D g2 = (Graphics2D) g;
      g2.setColor(SELECTION_BACKGROUND);

      int lineno = 0;
      for (VisualLine vl : vlines) {
        lineno++;
        // Selection against a visual line range [vl.start, vl.end]
        int lineA = vl.start;
        int lineB = vl.end;

        // compute overlap
        int a = Math.max(selA, lineA);
        int b = Math.min(selB, lineB);

        if (a < b) {
          int la = a - lineA;
          int lb = b - lineA;
          Shape highlight = vl.layout.getLogicalHighlightShape(la, lb);
          AffineTransform tx = AffineTransform.getTranslateInstance(vl.x, vl.baselineY);
          Shape s = tx.createTransformedShape(highlight);
          g2.fill(s);
        }
        if (selA <= lineB && selB > lineB && vl.hardBreakAfter) {
          // If selection spans a trailing newline, highlight that newline.
          float end = vl.isEmpty() ? 0 : vl.layout.getAdvance();
          float newlineWidth = vl.height()*0.4f; // 40% aspect ratio for newline char seems reasonable
          System.out.println("select newline");
          Rectangle2D r = new Rectangle2D.Float(vl.x + end, vl.topY(), newlineWidth, vl.height());
          g2.fill(r);
        }
      }
    }

    // draw text
    g.setColor(Color.BLACK);
    // GraphicsUtil.drawText(g, lines, x, y, halign, valign);
    int textWidth = (attrs.isWrapping() ? attrs.getTextWidth() : -1);
    drawMultilineText(g, curText, loc, textWidth, font, halign, valign);


    // draw caret
    if (cursor == anchor) {
      Graphics2D g2 = (Graphics2D) g;
      VisualLine vl = findLineContaining(vlines, cursor);
      if (vl != null) {
        float cx = vl.caretXForGlobalIndex(cursor);
        int top = (int) Math.floor(vl.topY());
        int bot = (int) Math.ceil(vl.bottomY());
        g2.setColor(Color.BLACK);
        g2.drawLine((int) cx, top, (int) cx, bot);
      }
    }

  }

  private static VisualLine findLineContaining(List<VisualLine> lines, int pos) {
    // Find line where pos is in [start, end], but allow pos==end to stick to that line
    for (int i = 0; i < lines.size(); i++) {
      VisualLine vl = lines.get(i);
      if (pos < vl.start) return (i > 0) ? lines.get(i - 1) : vl;
      if (pos <= vl.end) return vl;
    }
    return lines.isEmpty() ? null : lines.get(lines.size() - 1);
  }

  static final float INTER_PARAGRAPH_SPACE = 0.7f;

  static class VisualLine {
    final int start;     // global UTF-16 index into curText (inclusive)
    final int end;       // global UTF-16 index into curText (exclusive) -- excludes '\n'
    final boolean hardBreakAfter; // true if this line is followed by a '\n' in curText
    final TextLayout layout;
    /*final*/ float x;     // draw origin x, where layout.draw() is called
    final float baselineY; // baseline y, where layout.draw() is called

    VisualLine(int start, int end, boolean hardBreakAfter, TextLayout layout, float x, float baselineY) {
      this.start = start;
      this.end = end;
      this.hardBreakAfter = hardBreakAfter;
      this.layout = layout;
      this.x = x;
      this.baselineY = baselineY;
    }

    float topY() { return baselineY - layout.getAscent(); }
    float bottomY() { return baselineY + layout.getDescent() + layout.getLeading(); }
    float height() { return layout.getAscent() + layout.getDescent() + layout.getLeading(); }

    float caretXForGlobalIndex(int globalIndex) {
      int local = clamp(globalIndex - start, 0, end - start);
      // TextLayout wants local insertion index
      TextHitInfo hit = TextHitInfo.leading(local);
      float[] caretInfo = layout.getCaretInfo(hit); // [x1, y1, x2, y2] but typically x positions
      return x + caretInfo[0];
    }

    int hitTestGlobal(float px) {
      float relX = px - x;
      // y arg is offset from baseline; 0 is fine for "near baseline"
      TextHitInfo hit = layout.hitTestChar(relX, 0);
      return start + hit.getInsertionIndex();
    }

    private static int clamp(int v, int lo, int hi) {
      return (v < lo) ? lo : (v > hi ? hi : v);
    }

    boolean isEmpty() {
      return start == end;
    }
  }

  static class BoxLayout {
    final Bounds bounds;          // overall bounds (same as getBounds)
    final List<VisualLine> lines; // visual lines in draw order (wrapped)
    BoxLayout(Bounds b, List<VisualLine> ls) { bounds = b; lines = ls; }
  }

  static BoxLayout layoutWrappedText(Graphics g, String[] paragraphs, Location loc,
      int textWidth, Font font, int halign, int valign) {

    Graphics2D g2 = (Graphics2D) g;
    g2.setFont(font);
    FontRenderContext frc = g2.getFontRenderContext();

    final boolean autoWrap = textWidth > 0;
    int y = loc.y;
    float dy = 0f;
    float interParagraphSpace = autoWrap ? font.getSize2D() * INTER_PARAGRAPH_SPACE : 0;

    ArrayList<VisualLine> lines = new ArrayList<>();
    int start = 0;

    float maxAdvance = 0f; // used to compute bounds width in manual-wrap mode

    for (int p = 0; p < paragraphs.length; p++) {
      String para = paragraphs[p];

      if (p != 0)
        dy += interParagraphSpace;

      boolean hardBreakAfter = (p < paragraphs.length - 1);
      int end = start + para.length();
      boolean empty = para.isEmpty();

      if (empty || !autoWrap) { // empty or manual-wrap: one visual line for entire paragraph
        // empty paragraph uses a space, to get sensible line height
        TextLayout layout = new TextLayout(empty ? " " : para, font, frc);
        if (lines.isEmpty())
          y -= valignAdjust(layout, valign);
        dy += layout.getAscent();
        lines.add(new VisualLine(start, end, hardBreakAfter, layout, 0, y + dy));
        maxAdvance = Math.max(maxAdvance, layout.getAdvance()); // with manual-wrap, trailing spaces make the bounds wider
        dy += layout.getDescent() + layout.getLeading();

      } else { // Auto-wrap: LineBreakMeasurer produces multiple visual lines per paragraph
        AttributedString astr = new AttributedString(para);
        AttributedCharacterIterator it = astr.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
        measurer.setPosition(it.getBeginIndex());

        while (measurer.getPosition() < it.getEndIndex()) {
          int a = measurer.getPosition();
          TextLayout layout = measurer.nextLayout(textWidth);
          int b = measurer.getPosition();
          if (lines.isEmpty())
            y -= valignAdjust(layout, valign);
          dy += layout.getAscent();
          lines.add(new VisualLine(start + a, start + b, hardBreakAfter && (b == para.length()), layout, 0, y + dy));
          dy += layout.getDescent() + layout.getLeading();
        }

      }

      start = end + 1; // add one, for newline between paragraphs
    }

    if (!autoWrap)
      textWidth = (int) Math.ceil(maxAdvance);

    int x = loc.x;
    x -= halignAdjust(textWidth, halign);

    for (VisualLine line : lines) {
      float lineWidth = autoWrap ? line.layout.getVisibleAdvance() : line.layout.getAdvance();
      float dx = halignAdjustLine(textWidth, lineWidth, halign);
      line.x = x + dx;
    }

    Bounds b = Bounds.create(x, y, textWidth, (int) Math.ceil(dy));
    return new BoxLayout(b, lines);
  }

  private static int valignAdjust(TextLayout layout, int valign) {
    float h = layout.getAscent() + layout.getDescent() + layout.getLeading();
    if (valign == GraphicsUtil.V_BASELINE)
      return (int) layout.getAscent();
    else if (valign == GraphicsUtil.V_BOTTOM)
      return (int) h;
    else if (valign == GraphicsUtil.V_CENTER)
      return (int) (h / 2f);
    else // V_TOP
      return 0;
  }
  
  private static float halignAdjust(int textWidth, int halign) {
    if (halign == GraphicsUtil.H_RIGHT)
      return textWidth;
    else if (halign == GraphicsUtil.H_CENTER)
      return textWidth / 2;
    else // H_LEFT
      return 0;
  }

  private static float halignAdjustLine(int textWidth, float lineWidth, int halign) {
    if (halign == GraphicsUtil.H_RIGHT)
      return (textWidth - lineWidth);
    else if (halign == GraphicsUtil.H_CENTER)
      return (textWidth - lineWidth)/2;
    else // H_LEFT
      return 0;
  }

  private BoxLayout computeLayout(Graphics g) {
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    int textWidth = attrs.isWrapping() ? attrs.getTextWidth() : -1;
    Font font = attrs.getFont();
    String[] paragraphs = curText.split("\n", -1);
    return layoutWrappedText(g, paragraphs, loc, textWidth, font, halign, valign);
  }

  static void drawMultilineText(Graphics g, String text, Location loc, int textWidth, Font font, int halign, int valign) {
    String[] paragraphs = text.split("\n", -1); // keep empty paragraph at end
    if (textWidth <= 0) {
      GraphicsUtil.drawText(g, font, paragraphs, loc.x, loc.y, halign, valign);
    } else {
      drawWrappedText(g, paragraphs, loc, textWidth, font, halign, valign);
    }
  }

  static void drawWrappedText(Graphics g, String[] paragraphs, Location loc,
      int textWidth, Font font, int halign, int valign) {
    BoxLayout wl = layoutWrappedText(g, paragraphs, loc, textWidth, font, halign, valign);
    Graphics2D g2 = (Graphics2D) g;
    g2.setFont(font);
    for (VisualLine line : wl.lines) {
      line.layout.draw(g2, line.x, line.baselineY);
    }
  }

  @Override
  public Bounds getBounds(Graphics g) {
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    int textWidth = attrs.isWrapping() ? attrs.getTextWidth() : -1;
    Font font = attrs.getFont();
    return getBounds(g, curText, loc, textWidth, font, halign, valign);
  }

  static Bounds getBounds(Graphics g, String text, Location loc, int textWidth, Font font, int halign, int valign) {
    String[] paragraphs = text.split("\n", -1); // keep blank lines at end
    if (textWidth <= 0) {
      return Bounds.create(GraphicsUtil.getTextBounds(g, font, paragraphs, loc.x, loc.y, halign, valign));
    } else {
      BoxLayout wl = layoutWrappedText(g, paragraphs, loc, textWidth, font, halign, valign);
      return wl.bounds;
    }
  }


  @Override
  public String getText() {
    return curText;
  }

  @Override
  public void keyPressed(KeyEvent e) {
    int ign;
    // Control unused on MacOS, but used as menuMask on Linux/Windows
    // Alt unused on Linux/Windows, but used for wordMask on MacOS
    // Meta unused on Linux/Windows, but used for menuMask on MacOS
    if (Main.MacOS)
      ign = InputEvent.CTRL_DOWN_MASK;
    else
      ign = InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
    if ((e.getModifiersEx() & ign) != 0)
      return;
    int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    int wordMask =  Main.MacOS
        ? InputEvent.ALT_DOWN_MASK /* MacOS Option keys, don't bother with ALT_GRAPH_DOWN_MASK */
        : menuMask; /* Windows/Linux wordMask == menuMask == CONTROL_DOWN_MASK */
    boolean shift = ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
    boolean menukey = ((e.getModifiersEx() & menuMask) != 0);
    boolean wordkey = ((e.getModifiersEx() & wordMask) != 0);
    processMovementKeys(e, shift, wordkey, menukey);
    if (e.isConsumed())
      return;
    if (menukey)
      menuShortcutKeyPressed(e, shift);
    else if (!wordkey)
      normalKeyPressed(e, shift);
  }

  private boolean wordBoundary(int i) {
    return (i <= 0)
        || (i >= curText.length())
        || (whitespace(i-1) && !whitespace(i));
  }

  private boolean whitespace(int i) {
    return Character.isWhitespace(curText.charAt(i));
  }

  private boolean allowedCharacter(char c) {
    return (c != KeyEvent.CHAR_UNDEFINED)
        && (c == '\n' || c == '\t' || !Character.isISOControl(c));
  }

  // @Override
  // public void selectAll() {
  //   cursor = 0;
  //   anchor = curText.length();
  //   editMenuHandler.computeEnabled();
  // }

  void doCopy() {
    if (anchor != cursor) {
      int pp = (cursor < anchor ? cursor : anchor);
      int ee = (cursor < anchor ? anchor : cursor);
      String s = curText.substring(pp, ee);
      StringSelection sel = new StringSelection(s);
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
    }
  }

  void doCut() {
    if (anchor != cursor) {
      doCopy();
      log.doAction(new TextAction(""));
    }
  }

  void doPaste() {
    try {
      String s = (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
      String cleaned = "";
      boolean lastWasSpace = false;
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (!allowedCharacter(c)) {
          if (lastWasSpace)
            continue;
          c = ' ';
        }
        lastWasSpace = (c == ' ');
        cleaned += c;
      }
      log.doAction(new TextAction(cleaned));
    } catch (Exception ex) {
    }
  }

  private void menuShortcutKeyPressed(KeyEvent e, boolean shift) {
    switch (e.getKeyCode()) {
    case KeyEvent.VK_A: // select all
      cursor = 0;
      anchor = curText.length();
      editMenuHandler.computeEnabled();
      e.consume();
      break;
    case KeyEvent.VK_CUT:
    case KeyEvent.VK_X: // cut
      doCut();
      e.consume();
      break;
    case KeyEvent.VK_COPY:
    case KeyEvent.VK_C: // copy
      doCopy();
      e.consume();
      break;
    case KeyEvent.VK_Z:
      log.undoAction();
      e.consume();
      break;
    case KeyEvent.VK_Y:
      log.redoAction();
      e.consume();
      break;
    case KeyEvent.VK_INSERT:
    case KeyEvent.VK_PASTE:
    case KeyEvent.VK_V: // paste
      doPaste();
      e.consume();
      break;
    default:
      ; // ignore
    }
  }

  // Text field movement shortcuts...
  // For a multi-line text field are ten possible cursor movements:
  //    ______________________________________
  //   |(-5)                                  |   (+1) next char      (-1) prev char
  //   |                 (-4)                 |   (+2) next word      (-2) prev word
  //   |(-3)    (-2)   (-1)I(+1)   (+2)   (+3)|   (+3) anchor of line    (-3) start of line
  //   |                 (+4)             ____|   (+4) down a line    (-4) up a line
  //   |_____________________________(+5)|        (+5) anchor of text    (-5) start of text
  // 
  // When cursor is on first or last line, 4 degenerates to 5.
  // For a single-line text field the same holds except that 3, 4 and 5 are all equivalent.
  //
  //                                                   single-line          multi-line
  //          key          modifiers                   textfield action     textfield action
  // MacOS:
  //          left/right   -                           +/- 1                +/- 1            
  //          left/right   option/wordkey              +/- 2                +/- 2             
  //          left/right   command/menukey             +/- 5                +/- 3
  //          up/down      -                           +/- 5                +/- 4
  //          up/down      command/menukey             +/- 5                +/- 5
  //          home/anchor     -                           +/- 5                +/- 5
  //          pgup/pgdn    -                           +/- 5                +/- 5
  // Linux/Windows:
  //          left/right   -                           +/- 1                +/- 1            
  //          left/right   control/wordkey/menukey     +/- 2                +/- 2             
  //          up/down      -                           +/- 5                +/- 4
  //          up/down      control/wordkey/menukey     +/- 5                +/- 5
  //          home/anchor     -                           +/- 5                +/- 3
  //          home/anchor     control/wordkey/menukey     +/- 5                +/- 5
  //          pgup/pgdn    -                           +/- 5                +/- 5
  //
  // TODO: support for old style linux/apple movemet keys, like control-A / control-E ?

  private void cancelSelection(int direction) {
    // selection is being canceled by left/right movement
    if (direction < 0) anchor = cursor;
    else cursor = anchor;
    editMenuHandler.computeEnabled();
  }
 
  // swap, if needed, so cursor <= anchor
  private void normalizeSelection() {
    if (cursor > anchor) {
      int t = anchor;
      anchor = cursor;
      cursor = t;
    }
  }

  private void moveCaret(int move, boolean shift) {
    if (!shift)
      normalizeSelection();

    if (move == -3 || move == +3) { // start/end of line
      if (!shift && cursor != anchor)
        cancelSelection(move);

      BoxLayout box = computeLayout(g);
      List<VisualLine> lines = box.lines;
      VisualLine vl = findLineContaining(lines, cursor);

      if (vl != null) {
        cursor = (move < 0) ? vl.start : vl.end;
        preferredCaretX = Float.NaN;
      } else {
        cursor = (move < 0) ? 0 : curText.length();
      }
    } else if (move == -4 || move == +4) { // up/down a line
      if (!shift && cursor != anchor)
        cancelSelection(move);

      BoxLayout box = computeLayout(g);
      List<VisualLine> lines = box.lines;
      if (lines.isEmpty()) {
        cursor = (move < 0) ? 0 : curText.length();
      } else {
        int dir = (move < 0) ? -1 : +1;

        // Determine current line index
        int idx = 0;
        VisualLine cur = null;
        for (int i = 0; i < lines.size(); i++) {
          VisualLine vl = lines.get(i);
          if (cursor <= vl.end) { idx = i; cur = vl; break; }
        }
        if (cur == null) { idx = lines.size() - 1; cur = lines.get(idx); }

        // Remember preferred X (update it on first vertical move)
        if (Float.isNaN(preferredCaretX)) {
          preferredCaretX = cur.caretXForGlobalIndex(cursor);
        }

        int tgt = idx + dir;
        if (tgt < 0) {
          cursor = 0;
        } else if (tgt >= lines.size()) {
          cursor = curText.length();
        } else {
          VisualLine dest = lines.get(tgt);
          // hit-test in destination line at preferredCaretX
          int newPos = dest.hitTestGlobal(preferredCaretX);
          if (newPos < dest.start) newPos = dest.start;
          if (newPos > dest.end) newPos = dest.end;
          cursor = newPos;
        }
      }
    } else if (move < -5 || move == 0 || move > 5) { // invalid
      return;
    } else if (move <= -3) { // start of line, up a line, start of text
      cursor = 0;
    } else if (move >= +3) { // anchor of line, down a line, anchor of text
      cursor = curText.length();
    } else { // next/prev char, next/prev word
      int dx = (move < 0 ? -1 : +1);
      boolean byword = (move == -2 || move == +2);
      if (!shift && cursor != anchor) {
        // selection is being canceled by left/right movement,
        // so we count the cancellation as the first step
        cancelSelection(move);
      } else {
        // move one char left/right as the first step, if possible
        if (dx < 0 && cursor > 0) cursor--;
        else if (dx > 0 && cursor < curText.length()) cursor++;
      }
      if (byword) {
        while (!wordBoundary(cursor))
          cursor += dx;
      }
      preferredCaretX = Float.NaN; // horizontal movement resets vertical goal x
    }

    if (!shift)
      anchor = cursor;
    editMenuHandler.computeEnabled();
  }
  
  private void processMovementKeys(KeyEvent e, boolean shift, boolean wordkey, boolean menukey) {
    int dir = +1;
    switch (e.getKeyCode()) {
    case KeyEvent.VK_LEFT:
    case KeyEvent.VK_KP_LEFT:
      dir = -1;
      // fall through
    case KeyEvent.VK_RIGHT:
    case KeyEvent.VK_KP_RIGHT:
      if (menukey && !wordkey)
        moveCaret(dir*3, shift); // MacOS start/anchor of line
      else if (wordkey)
        moveCaret(dir*2, shift); // prev/next word
      else 
        moveCaret(dir*1, shift); // prev/next char
      e.consume();
      break;
    case KeyEvent.VK_UP:
    case KeyEvent.VK_KP_UP:
      dir = -1;
      // fall through
    case KeyEvent.VK_DOWN:
    case KeyEvent.VK_KP_DOWN:
      if (menukey)
        moveCaret(dir*5, shift); // start/anchor of text
      else
        moveCaret(dir*4, shift); // up/down a line
      e.consume();
      break;
    case KeyEvent.VK_PAGE_UP:
      dir = -1;
      // fall through
    case KeyEvent.VK_PAGE_DOWN:
      moveCaret(dir*5, shift); // start/anchor of text
      e.consume();
      break;
    case KeyEvent.VK_HOME:
      dir = -1;
      // fall through
    case KeyEvent.VK_END:
      if (Main.MacOS)
        moveCaret(dir*5, shift); //  MacOS start/anchor of text
      else if (menukey)
        moveCaret(dir*5, shift); // start/anchor of text
      else 
        moveCaret(dir*3, shift); // start/anchor of line
      e.consume();
      break;
    default:
      break;
    }
  }

  private void normalKeyPressed(KeyEvent e, boolean shift) {
    switch (e.getKeyCode()) {
    case KeyEvent.VK_CANCEL:
      cancelEditing();
      e.consume();
      break;
    case KeyEvent.VK_CLEAR:
      log.doAction(new TextAction(0, curText.length(), ""));
      e.consume();
      break;
    case KeyEvent.VK_ESCAPE:
      stopEditing();
      e.consume();
      break;
    case KeyEvent.VK_BACK_SPACE: // DELETE on MacOS?
      if (cursor != anchor)
        log.doAction(new TextAction(""));
      else if (cursor > 0)
        log.doAction(new TextAction(cursor-1, cursor, ""));
      e.consume();
      break;
    case KeyEvent.VK_DELETE: // BACK_SPACE on MacOS?
      if (cursor != anchor)
        log.doAction(new TextAction(""));
      else if (cursor < curText.length())
        log.doAction(new TextAction(cursor, cursor+1, ""));
      e.consume();
      break;
    default:
      ; // ignore
    }
  }

  @Override
  public void keyReleased(KeyEvent e) { }

  @Override
  public void keyTyped(KeyEvent e) {
    int ign = InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK;
    if ((e.getModifiersEx() & ign) != 0)
      return;

    e.consume();
    char c = e.getKeyChar();
    if (allowedCharacter(c)) {
      log.doAction(new TextAction("" + c));
    } else if (c == '\n') {
      stopEditing();
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    int p = findCaret(e.getX(), e.getY());
    if (selectByLine) {
      if (p < selectOrigin) {
        cursor = selectOrigin;
        moveCaret(+3, false); // will set anchor
        if (anchor < curText.length() && curText.charAt(anchor) == '\n')
          anchor++;
        cursor = p;
        moveCaret(-3, true); // only sets cursor
      } else {
        cursor = selectOrigin;
        moveCaret(-3, false); // will set anchor
        cursor = p;
        moveCaret(+3, true); // only sets cursor
        if (cursor < curText.length() && curText.charAt(cursor) == '\n')
          cursor++;
      }
    } else if (selectByWord) {
      if (p < selectOrigin) {
        anchor = nextWordBoundary(selectOrigin);
        cursor = prevWordBoundary(p);
      } else {
        anchor = prevWordBoundary(selectOrigin);
        cursor = nextWordBoundary(p);
      }
    } else {
      cursor = p;
    }
    editMenuHandler.computeEnabled();
  }

  int nextWordBoundary(int p) {
    if (p < curText.length() && whitespace(p)) {
      p++;
      while (p < curText.length() && curText.charAt(p) != '\n' && whitespace(p))
        p++;
    } else if (p < curText.length()) {
      p++;
      while (p < curText.length() && !whitespace(p))
        p++;
    }
    return p;
  }

  int prevWordBoundary(int p) {
    if (p == curText.length())
        p--;
    if (curText.charAt(p) == '\n') {
      ; // do nothing
    } else if (whitespace(p)) {
      while (p > 0 && curText.charAt(p-1) != '\n' && whitespace(p-1))
        p--;
    } else {
      while (p > 0 && !whitespace(p-1))
        p--;
    }
    return p;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    int p = findCaret(e.getX(), e.getY());
    boolean shift = ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
    if (shift)
      selectOrigin = (p <= (cursor+anchor)/2) ? Math.max(cursor, anchor) : Math.min(cursor, anchor);
    else
      selectOrigin = p;
    int n = e.getClickCount();
    if (n >= 3) {
      // expand to entire line
      selectByWord = false;
      selectByLine = true;
      cursor = Math.min(selectOrigin, p);
      moveCaret(-3, false); // will set anchor
      cursor = Math.max(selectOrigin, p);
      moveCaret(+3, true); // only sets cursor
      if (cursor < curText.length() && curText.charAt(cursor) == '\n')
        cursor++;
    } else if (n == 2) {
      // expand to entire word, or to whitespace between words
      selectByWord = true;
      selectByLine = false;
      if (p == curText.length()) {
        // select nothing, but drag may be coming
        anchor = selectOrigin;
        cursor = p;
      } else if (curText.charAt(p) == '\n') {
        // include just the newline (not visible) in selection
        anchor = selectOrigin;
        cursor = (selectOrigin <= p) ? p + 1 : p;
      } else if (p > selectOrigin) {
        // select word or whitespace, staying within this line
        anchor = prevWordBoundary(selectOrigin);
        cursor = nextWordBoundary(p);
      } else {
        // select word or whitespace, staying within this line
        anchor = nextWordBoundary(selectOrigin);
        cursor = prevWordBoundary(p);
      }
    } else {
      selectByWord = false;
      selectByLine = false;
      anchor = selectOrigin;
      cursor = p;
    }
    editMenuHandler.computeEnabled();
  }

  @Override
  public void mouseReleased(MouseEvent e) { }

  private int findCaret(int px, int py) {
    BoxLayout box = computeLayout(g);
    List<VisualLine> lines = box.lines;
    if (lines.isEmpty()) return 0;

    // If above first line, clamp to start
    if (py < lines.get(0).topY()) return 0;

    // Find the visual line by y
    for (VisualLine vl : lines) {
      if (py >= vl.topY() && py < vl.bottomY()) {
        int hit = vl.hitTestGlobal(px);
        // clamp into [vl.start, vl.end]
        if (hit < vl.start) hit = vl.start;
        if (hit > vl.end) hit = vl.end;
        return hit;
      }
    }

    // Below last line => end of text
    return curText.length();
  }

  @Override
  public void stopEditing() {
    CaretEvent e = new CaretEvent(this, oldText, curText);
    // attrs.removeAttributeListener(this); // FIXME: not supported yet
    // attrs.setText(curText); // action will take care of it?
    for (CaretListener l : new ArrayList<CaretListener>(listeners))
      l.editingStopped(e);
  }

  @Override
  public void attributeListChanged(AttributeEvent e) { }

  @Override
  public void attributeValueChanged(AttributeEvent e) {
    Attribute<?> attr = e.getAttribute();
    if (attr == Text.ATTR_TEXT) {
      oldText = curText = (String)e.getValue();
      cursor = anchor = curText.length();
      log.clear();
      editMenuHandler.computeEnabled();
    }
  }

  static final long MAX_SENTENCE_DELAY_NS = 10L*1000L*1000L*1000L; // 10 seconds
  static final long MAX_KEYSTROKE_DELAY_NS = 500L*1000L*1000L; // 0.5 seconds

  private class TextAction extends UndoRedo.Action {
    long ts, te; // timestamps, in nanoseconds, of start and anchor of edit
    int cursorPos; // cursor position before edit
    int anchorPos; // selection anchor before edit
    int left, right; // range to be removed
    String old; // text between left and right, to be removed
    String repl; // text to be written into removed range
    // note: old.length == right-left always
    // note: repl.length == 0 means replacement is strictly deletion
    // note: after edit, cursor will be at left + repl.length
    //
    // Example: Selecting "ORD" then typing "K" produces the following action.
    //
    //                      ,------------ left: 3
    //                      |     ,------ right: 6
    //                0 1 2 3 4 5 6 7 8 9 
    // original text:  d i s O R D e r s      old: "ord"
    //   edited text:  d i s K e r s         repl: "k"
    //                0 1 2 3 4 5 6 7 8 9 

    public TextAction(String repl) {
      this(Math.min(cursor, anchor), Math.max(cursor, anchor), repl);
    }

    public TextAction(int left, int right, String repl) {
      ts = te = System.nanoTime();
      this.cursorPos = cursor;
      this.anchorPos = anchor;
      this.left = left;
      this.right = right;
      this.old = left < curText.length() ? curText.substring(left, right) : "";
      this.repl = repl;
    }
    
    @Override
    public String getName() { return "Text Edit"; }

    @Override
    public boolean isEmpty() {
      return old.isEmpty() && repl.isEmpty();
    }

    @Override
    public boolean shouldAppendTo(UndoRedo.Action other) {
      if (! (other instanceof TextAction))
        return false;
      TextAction prev = (TextAction)other;
      if (this.repl.length() == 0) {
        // now strictly deleting text, e.g. backspace, or selection-delete
        if (right - left != 1)
          return false; // erased multiple, e.g. selection-delete
        else if (prev.repl.length() != 0)
          return false; // previous was not strictly deleting text
        else if (this.right != prev.left)
          return false; // previous deletion was not right-adjacent to this deletion
        else if (this.ts - prev.te > MAX_KEYSTROKE_DELAY_NS)
          return false; // too large of a gap between actions
        else if (this.te - prev.ts > MAX_SENTENCE_DELAY_NS)
          return false; // too large of a total duration for actions
        else if ((this.old.indexOf('\n') >= 0 || this.old.indexOf('\r') >= 0) &&
          this.te - prev.ts > MAX_SENTENCE_DELAY_NS/2)
          return false; // erased a newline and approaching duration limit
        else if (this.old.isBlank() && this.te - prev.ts > MAX_SENTENCE_DELAY_NS*3/4)
          return false; // erased whitespace and nearly at duration limit
        else
          return true;
      } else {
        // now adding text, e.g. typing or pasting, at cursor or over a selection
        if (left != right)
          return false; // overwriting a selection
        else if (this.repl.length() != 1)
          return false; // pasting
        else if (prev.repl.length() == 0)
          return false; // previous was strictly deleting text
        else if (this.left != prev.left + prev.repl.length())
          return false; // previous addition was not left-adjacent to this addition
        else if (this.ts - prev.te > MAX_KEYSTROKE_DELAY_NS)
          return false; // too large of a gap between actions
        else if (this.te - prev.ts > MAX_SENTENCE_DELAY_NS)
          return false; // too large of a total duration for actions
        else if (!this.repl.startsWith("\n") && prev.repl.endsWith("\n")
            && this.te - prev.ts > MAX_SENTENCE_DELAY_NS/2)
          return false; // starting a new non-empty line and approaching duration limit
        else if (!Character.isWhitespace(this.repl.charAt(0))
            && Character.isWhitespace(prev.repl.charAt(prev.repl.length() - 1))
            && this.te - prev.ts > MAX_SENTENCE_DELAY_NS*3/4)
          return false; //  starting a new word and nearly at duration limit
        else
          return true;
      }
    }

    @Override
    public UndoRedo.Action append(UndoRedo.Action other) {
      if (! (other instanceof TextAction))
        return this; // should never happen
      TextAction later = (TextAction)other;
      this.te = later.te;
      if (this.repl.length() != 0) {
        this.right += (later.right - later.left);
        this.old = this.old + later.old;
        this.repl = this.repl + later.repl;
      } else {
        this.left = later.left;
        this.old = later.old + this.old;
        this.repl = later.repl + this.repl;
      }
      return this;
    }

    @Override
    public void execute() {
      if (right < curText.length())
        curText = curText.substring(0, left) + repl + curText.substring(right);
      else
        curText = curText.substring(0, left) + repl;
      cursor = anchor = left + repl.length();
      editMenuHandler.computeEnabled();
    }

    @Override
    public void unexecute() {
      if (left + repl.length() < curText.length())
        curText = curText.substring(0, left) + old + curText.substring(left + repl.length());
      else
        curText = curText.substring(0, left) + old;
      cursor = cursorPos;
      anchor = anchorPos;
      editMenuHandler.computeEnabled();
    }

  }

  private class TextCaretEditHandler extends EditHandler {

    @Override
    public void computeEnabled() {
      setText(LogisimMenuBar.UNDO, "Undo");
      setEnabled(LogisimMenuBar.UNDO, log.getUndoAction() != null);
      setText(LogisimMenuBar.REDO, "Redo");
      setEnabled(LogisimMenuBar.REDO, log.getRedoAction() != null);
      setEnabled(LogisimMenuBar.CUT, cursor != anchor);
      setEnabled(LogisimMenuBar.COPY, cursor != anchor);
      setEnabled(LogisimMenuBar.PASTE, true); // todo: check clipboard for suitability?
      setEnabled(LogisimMenuBar.DELETE, cursor != anchor);
      setEnabled(LogisimMenuBar.SELECT_ALL, true);
      setEnabled(LogisimMenuBar.DUPLICATE, false);
      setEnabled(LogisimMenuBar.SEARCH, true);
      setEnabled(LogisimMenuBar.RAISE, false);
      setEnabled(LogisimMenuBar.LOWER, false);
      setEnabled(LogisimMenuBar.RAISE_TOP, false);
      setEnabled(LogisimMenuBar.LOWER_BOTTOM, false);
      setEnabled(LogisimMenuBar.ADD_CONTROL, false);
      setEnabled(LogisimMenuBar.REMOVE_CONTROL, false);
    }

    @Override
    public void undo() {
      log.undoAction();
      canvas.getProject().repaintCanvas();
    }

    @Override
    public void redo() {
      log.redoAction();
      canvas.getProject().repaintCanvas();
    }

    @Override
    public void copy() {
      doCopy();
    }

    @Override
    public void cut() {
      doCut();
      canvas.getProject().repaintCanvas();
    }

    @Override
    public void delete() {
      log.doAction(new TextAction(""));
      canvas.getProject().repaintCanvas();
    }

    @Override
    public void paste() {
      doPaste();
      canvas.getProject().repaintCanvas();
    }

    @Override
    public void selectAll() {
      cursor = 0;
      anchor = curText.length();
      editMenuHandler.computeEnabled();
      canvas.getProject().repaintCanvas();
    }
  }

}

/*
// tab stop rendering
public void paint(Graphics graphics) {

     float leftMargin = 10, rightMargin = 310;
     float[] tabStops = { 100, 250 };

     // assume styledText is an AttributedCharacterIterator, and the number
     // of tabs in styledText is tabCount

     int[] tabLocations = new int[tabCount+1];

     int i = 0;
     for (char c = styledText.first(); c != styledText.DONE; c = styledText.next()) {
         if (c == '\t') {
             tabLocations[i++] = styledText.getIndex();
         }
     }
     tabLocations[tabCount] = styledText.getEndIndex() - 1;

     // Now tabLocations has an entry for every tab's offset in
     // the text.  For convenience, the last entry is tabLocations
     // is the offset of the last character in the text.

     LineBreakMeasurer measurer = new LineBreakMeasurer(styledText);
     int currentTab = 0;
     float verticalPos = 20;

     while (measurer.getPosition() < styledText.getEndIndex()) {

         // Lay out and draw each line.  All segments on a line
         // must be computed before any drawing can occur, since
         // we must know the largest ascent on the line.
         // TextLayouts are computed and stored in a Vector;
         // their horizontal positions are stored in a parallel
         // Vector.

         // lineContainsText is true after first segment is drawn
         boolean lineContainsText = false;
         boolean lineComplete = false;
         float maxAscent = 0, maxDescent = 0;
         float horizontalPos = leftMargin;
         Vector layouts = new Vector(1);
         Vector penPositions = new Vector(1);

         while (!lineComplete) {
             float wrappingWidth = rightMargin - horizontalPos;
             TextLayout layout =
                     measurer.nextLayout(wrappingWidth,
                                         tabLocations[currentTab]+1,
                                         lineContainsText);

             // layout can be null if lineContainsText is true
             if (layout != null) {
                 layouts.addElement(layout);
                 penPositions.addElement(Float.valueOf(horizontalPos));
                 horizontalPos += layout.getAdvance();
                 maxAscent = Math.max(maxAscent, layout.getAscent());
                 maxDescent = Math.max(maxDescent,
                     layout.getDescent() + layout.getLeading());
             } else {
                 lineComplete = true;
             }

             lineContainsText = true;

             if (measurer.getPosition() == tabLocations[currentTab]+1) {
                 currentTab++;
             }

             if (measurer.getPosition() == styledText.getEndIndex())
                 lineComplete = true;
             else if (horizontalPos >= tabStops[tabStops.length-1])
                 lineComplete = true;

             if (!lineComplete) {
                 // move to next tab stop
                 int j;
                 for (j=0; horizontalPos >= tabStops[j]; j++) {}
                 horizontalPos = tabStops[j];
             }
         }

         verticalPos += maxAscent;

         Enumeration layoutEnum = layouts.elements();
         Enumeration positionEnum = penPositions.elements();

         // now iterate through layouts and draw them
         while (layoutEnum.hasMoreElements()) {
             TextLayout nextLayout = (TextLayout) layoutEnum.nextElement();
             Float nextPosition = (Float) positionEnum.nextElement();
             nextLayout.draw(graphics, nextPosition.floatValue(), verticalPos);
         }

         verticalPos += maxDescent;
     }
 }
*/
// FIXME / TODO
// x In auto-wrap mode, allow trailing whitespace to overhang textWidth
// - Backspace/delete full glyph at a time
// - Markdown-like styling (header, bullets)
// - tab stops
// - verify hit-test
// - review all code
// - caching
// - don't render original component when caret is shown
// - when placing new "text", select all initially
// - when placing text initially, sizing, or moving, snap to grid unless alt is held?
// - cursor movement with RTL text
//   (for the caret index i, use TextHitInfo.afterOffset(i) or leading/trailing depending on what
//   side you intend.)
