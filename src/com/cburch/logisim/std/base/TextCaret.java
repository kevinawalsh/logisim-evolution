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
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

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
    
  Font markdownEditFont = new Font("Monospaced", Font.PLAIN, 12);

  public static final Color EDIT_MASK = new Color(200, 200, 200, 200);
  public static final Color EDIT_BACKGROUND = TextFieldCaret.EDIT_BACKGROUND;
  public static final Color EDIT_BORDER = TextFieldCaret.EDIT_BORDER;
  public static final Color SELECTION_BACKGROUND = TextFieldCaret.SELECTION_BACKGROUND;

  private LinkedList<CaretListener> listeners = new LinkedList<CaretListener>();
  private TextAttributes attrs;
  private Graphics g;
  private String oldText;
  private String curText;
  private TextCaretEditHandler editMenuHandler;
  private UndoRedo log = new UndoRedo();
  private Canvas canvas;
  private float preferredCaretX = Float.NaN; // remembered x for vertical movement
 
  // If cursor==anchor, then no text is selected, and a caret is usually shown 
  // between the characters at positions cursor-1 and cursor.
  //
  // Otherwise, text within [cursor, anchor] is selected, and highlight is shown.
  //
  // There are some special cases:
  // - cursor==0: the cursor is shown at the start of the text.
  // - cursor==curText.length: the cursor is usually shown at the end of the text.
  // - cursor==anchor and caret is "between" line i-1 and line i, i.e. if
  //   curText[cursor-1] is a newline, or if curText[cursor-1] is the last character shown
  //   on some soft-wrapped line. Here, the caretBias controls whether the caret is shown
  //   at the end of line i-1 or the start of line i.
  private int cursor, anchor;
  private boolean cursorReverseBias; // true == reverse bias, false == forward bias

  // used during mouse selection
  private boolean selectByWord = false;
  private boolean selectByPara = false;
  private int selectOrigin = 0;

  private Location loc;
  private Bounds initialBounds;
  
  public TextCaret(TextAttributes attrs, Canvas canvas, Location loc, int cursor, boolean revBias, Bounds initialBounds) {
    this.attrs = attrs;
    this.canvas = canvas;
    this.g = canvas.getGraphics();
    this.oldText = this.curText = attrs.getText();
    this.loc = loc;
    this.cursor = this.anchor = cursor;
    this.cursorReverseBias = revBias;
    this.initialBounds = initialBounds;
    BoxLayout box = computeLayout(g);
    editMenuHandler = new TextCaretEditHandler();

    attrs.addAttributeWeakListener(null, this);
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
    cursorReverseBias = false;
    for (CaretListener l : new ArrayList<CaretListener>(listeners))
      l.editingCanceled(e);
    attrs.removeAttributeWeakListener(null, this);
    log.clear();
    editMenuHandler.computeEnabled();
  }

  @Override
  public void commitText(String text) {
    curText = text;
    cursor = anchor = curText.length();
    cursorReverseBias = false;
    log.clear();
    // attrs.setText(text); // action will handle it?
    editMenuHandler.computeEnabled();
  }

  @Override
  public void draw(Graphics g) {
    
    BoxLayout box = computeLayout(g);

    // fill initial bounds in translucent gray, to obscure the original text
    initialBounds.fill(g, EDIT_MASK);

    // draw boundary
    Bounds area = box.bounds.expand(Text.PAD);
    area.fill(g, EDIT_BACKGROUND);
    area.draw(g, EDIT_BORDER);

    // draw selection
    if (cursor != anchor) {
      int selA = Math.min(cursor, anchor);
      int selB = Math.max(cursor, anchor);

      Graphics2D g2 = (Graphics2D) g;
      g2.setColor(SELECTION_BACKGROUND);

      for (BoxLayout.VisualLine vl : box.lines) {
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
        if (selA <= lineB && selB > lineB && !vl.softBreakAfter) {
          // If selection spans a trailing newline, highlight that newline.
          float end = vl.isEmpty() ? 0 : vl.layout.getAdvance();
          float newlineWidth = vl.height()*0.4f; // 40% aspect ratio for newline char seems reasonable
          Rectangle2D r = new Rectangle2D.Float(vl.x + end, vl.topY(), newlineWidth, vl.height());
          g2.fill(r);
        }
      }
    }

    // draw text
    g.setColor(attrs.getFGColor());
    box.drawText(g);

    // draw caret
    if (cursor == anchor) {
      Graphics2D g2 = (Graphics2D) g;
      BoxLayout.VisualLine vl = box.lineForPosition(cursor, cursorReverseBias);
      float cx = vl.caretXForPosition(cursor);
      int top = (int) Math.floor(vl.topY());
      int bot = (int) Math.ceil(vl.bottomY());
      g2.setColor(Color.BLACK);
      g2.drawLine((int) cx, top, (int) cx, bot);
    }

  }

  private BoxLayout computeLayout(Graphics g) {
    int halign = attrs.getHorizontalAlign();
    int valign = attrs.getVerticalAlign();
    int textWidth = attrs.isWrapping() ? attrs.getTextWidth() : -1;
    Font font = attrs.getFont();
    if (attrs.isMarkdownish())
      font = markdownEditFont.deriveFont(font.getSize2D());
    boolean spacing = attrs.isWrapping() && !attrs.isMarkdownish();
    return new BoxLayout(g, curText, loc, textWidth, font, halign, valign, spacing);
  }

  @Override
  public Bounds getBounds(Graphics g) {
    return computeLayout(g).bounds.expand(Text.PAD);
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

  // FIXME: select all when placing new text using TextTool
  // @Override
  // public void selectAll() {
  //   cursor = 0;
  //   anchor = curText.length();
  //   cursorReverseBias = false;
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
      // String cleaned = "";
      // boolean lastWasSpace = false;
      // for (int i = 0; i < s.length(); i++) {
      //   char c = s.charAt(i);
      //   if (!allowedCharacter(c)) {
      //     if (lastWasSpace)
      //       continue;
      //     c = ' ';
      //   }
      //   lastWasSpace = (c == ' ');
      //   cleaned += c;
      // }
      String cleaned = TextAttributes.normalize(s);
      log.doAction(new TextAction(cleaned));
    } catch (Exception ex) {
    }
  }

  private void menuShortcutKeyPressed(KeyEvent e, boolean shift) {
    switch (e.getKeyCode()) {
    case KeyEvent.VK_A: // select all
      cursor = 0;
      anchor = curText.length();
      cursorReverseBias = false;
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

  // Text caret movement shortcuts...
  //
  // For multi-line capable text there are twelve possible cursor movements:
  //    ______________________________________
  //   |(-6)                                  |   (+1) next char      (-1) prev char
  //   |(-5)             (-4)                 |   (+2) next word      (-2) prev word
  //   |(-3)    (-2)   (-1)I(+1)   (+2)   (+3)|   (+3) end of line    (-3) start of line
  //   |                 (+4)         (+5)____|   (+4) down a line    (-4) up a line
  //   |_____________________________(+6)|        (+5) end of para    (-5) start of para
  //                                              (+6) end of text    (-6) start of text
  // 
  // For single-line only text, there are six possible cursor movements:
  //                                            
  //   .--------------------------------------.   (+1) next char      (-1) prev char
  //   |(<=-3)   (-2)  (-1)I(+1)  (+2)  (>=+3)|   (+2) next word      (-2) prev word
  //   '--------------------------------------'   (>=+3) end of line  (<=-3) start of line
  // 
  //                                                   single-line          multi-line
  //          key          modifiers                   textfield action     textfield action
  // MacOS:
  //          left/right   -                           +/- 1                +/- 1            
  //          left/right   option/wordkey              +/- 2                +/- 2             
  //          left/right   command/menukey             +/- 3                +/- 3
  //          up/down      -                           +/- 3                +/- 4
  //          up/down      command/menukey             +/- 3                +/- 6
  //          home/end     -                           +/- 3                +/- 6
  //          pgup/pgdn    -                           +/- 3                +/- 6
  // Linux/Windows:
  //          left/right   -                           +/- 1                +/- 1            
  //          left/right   control/wordkey/menukey     +/- 2                +/- 2             
  //          up/down      -                           +/- 3                +/- 4
  //          up/down      control/wordkey/menukey     +/- 3                +/- 6
  //          home/end     -                           +/- 3                +/- 3
  //          home/end     control/wordkey/menukey     +/- 3                +/- 6
  //          pgup/pgdn    -                           +/- 3                +/- 6
  //
  // Note: there are no keyboard shortcuts for start/end paragraph.
  // TODO: support for old style linux/apple movemet keys, like control-A / control-E ?

  private void cancelSelection(int direction) {
    // selection is being canceled by left/right movement
    if (direction < 0)
      anchor = cursor;
    else
      cursor = anchor;
    cursorReverseBias = false;
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

    if (move < -6 || move == 0 || move > +6) { // invalid
      return;
    } else if (move == -6 || move == +6) { // start/end of text
      cursor = (move < 0) ? 0 : curText.length();
      cursorReverseBias = false;
    } else if (move == -5 || move == +5) { // start/end of para
      if (!shift && cursor != anchor)
        cancelSelection(move);
      BoxLayout box = computeLayout(g);
      BoxLayout.VisualLine vl = box.lineForPosition(cursor, cursorReverseBias);
      if (move < 0)
          vl = box.firstLineOfParagraphContaining(vl);
      else
          vl = box.lastLineOfParagraphContaining(vl);
      cursor = (move < 0) ? vl.start : vl.end;
      cursorReverseBias = (move > 0);
      preferredCaretX = Float.NaN;
    } else if (move == -3 || move == +3) { // start/end of line
      if (!shift && cursor != anchor)
        cancelSelection(move);
    
      BoxLayout box = computeLayout(g);
      BoxLayout.VisualLine vl = box.lineForPosition(cursor, cursorReverseBias);
      cursor = (move < 0) ? vl.start : vl.end;
      cursorReverseBias = (move > 0);
      preferredCaretX = Float.NaN;
    } else if (move == -4 || move == +4) { // up/down a line
      if (!shift && cursor != anchor)
        cancelSelection(move);
        
      BoxLayout box = computeLayout(g);
      int dir = (move < 0) ? -1 : +1;

      // Determine current line index
      BoxLayout.VisualLine cur = box.lineForPosition(cursor, cursorReverseBias);

      // Save preferred X on first vertical move.
      if (Float.isNaN(preferredCaretX))
        preferredCaretX = cur.caretXForPosition(cursor);

      // MSWord ignores traversal up from first line, or down from last line.
      // Google Docs moves to start/end of line, but retains preferredCaretX and bias.
      // Let's do the latter.
      int tgt = cur.lineno + dir;
      if (tgt < 0) {
        cursor = 0;
      } else if (tgt >= box.lines.size()) {
        cursor = curText.length();
      } else {
        BoxLayout.VisualLine dest = box.lines.get(tgt);
        cursor = dest.positionForX(preferredCaretX);
      }
    } else { // next/prev char, next/prev word
      boolean byword = (move == -2 || move == +2);
      if (!shift && cursor != anchor) {
        // selection is being canceled by left/right movement,
        // so we count the cancellation as the first step
        cancelSelection(move);
      } else {
        // move one char left/right as the first step, if possible
        cursor = move < 0 ? BoxLayout.prevGraphemeBoundary(cursor, curText) : BoxLayout.nextGraphemeBoundary(cursor, curText);
      }
      if (byword) {
        while (!wordBoundary(cursor))
          cursor = move < 0 ? BoxLayout.prevGraphemeBoundary(cursor, curText) : BoxLayout.nextGraphemeBoundary(cursor, curText);
      }
      preferredCaretX = Float.NaN; // horizontal movement resets vertical goal x
      cursorReverseBias = false; // horizontal movement resets bias
    }

    if (!shift)
      anchor = cursor;
    editMenuHandler.computeEnabled();
  }
 
  // NOTE: This doesn't handle RTL text intuitively. Oh well.
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
        moveCaret(dir*3, shift); // MacOS start/end of line
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
        moveCaret(dir*6, shift); // start/end of text
      else
        moveCaret(dir*4, shift); // up/down a line
      e.consume();
      break;
    case KeyEvent.VK_PAGE_UP:
      dir = -1;
      // fall through
    case KeyEvent.VK_PAGE_DOWN:
      moveCaret(dir*6, shift); // start/end of text
      e.consume();
      break;
    case KeyEvent.VK_HOME:
      dir = -1;
      // fall through
    case KeyEvent.VK_END:
      if (Main.MacOS)
        moveCaret(dir*6, shift); //  MacOS start/end of text
      else if (menukey)
        moveCaret(dir*6, shift); // start/end of text
      else 
        moveCaret(dir*3, shift); // start/end of line
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
      if (cursor != anchor) {
        log.doAction(new TextAction(""));
      } else {
        int left = BoxLayout.prevGraphemeBoundary(cursor, curText);
        if (left < cursor)
          log.doAction(new TextAction(left, cursor, ""));
      }
      e.consume();
      break;
    case KeyEvent.VK_DELETE: // BACK_SPACE on MacOS?
      if (cursor != anchor) {
        log.doAction(new TextAction(""));
      } else {
        int right = BoxLayout.nextGraphemeBoundary(cursor, curText);
        if (cursor < right)
          log.doAction(new TextAction(cursor, right, ""));
      }
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
    // shift-enter and ctrl-enter both generate U+2028 LINE SEPARATOR
    char c = e.getKeyChar();
    if (((c == '\r' || c == '\n'))
      && (e.getModifiersEx() & (InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0
        && (((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0)
            != ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0))) {
      c = '\u2028';
    } else {
      int ign = InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK;
      if ((e.getModifiersEx() & ign) != 0)
        return;
    }
    e.consume();
    if (c == '\u000B' || c == '\u000C' || c == '\u0085' || c == '\u2029')
      c = '\n';
    // Note: we don't (yet) strip unpaired surrogates here... but maybe we should?
    if (allowedCharacter(c))
      log.doAction(new TextAction("" + c));
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (!mouseIsPressed)
      return;
    BoxLayout box = computeLayout(g);
    BoxLayout.VisualLine vl = box.lineForY(e.getY());
    int p = vl.positionForX(e.getX());
    boolean revBias = (p == vl.end);
    if (selectByPara) {
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

  boolean mouseIsPressed = false; // necessary to avoid phantom dragging when first creating caret.

  @Override
  public void mousePressed(MouseEvent e) {
    mouseIsPressed = true;
    BoxLayout box = computeLayout(g);
    BoxLayout.VisualLine vl = box.lineForY(e.getY());
    int p = vl.positionForX(e.getX());
    boolean revBias = (p == vl.end);
    boolean shift = ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
    if (shift)
      selectOrigin = (p <= (cursor+anchor)/2) ? Math.max(cursor, anchor) : Math.min(cursor, anchor);
    else
      selectOrigin = p;
    int clicks = e.getClickCount();
    if (clicks >= 3) {
      // expand to entire paragraph
      selectByWord = false;
      selectByPara = true;
      cursor = Math.min(selectOrigin, p);
      moveCaret(-5, false); // will set anchor
      cursor = Math.max(selectOrigin, p);
      moveCaret(+5, true); // only sets cursor
      if (cursor < curText.length() && curText.charAt(cursor) == '\n')
        cursor++;
    } else if (clicks == 2) {
      // expand to entire word, or to whitespace between words
      selectByWord = true;
      selectByPara = false;
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
      selectByPara = false;
      anchor = selectOrigin;
      cursor = p;
      cursorReverseBias = revBias; // only matters if anchor == cursor
    }
    editMenuHandler.computeEnabled();
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    mouseIsPressed = false;
  }

  @Override
  public void stopEditing() {
    CaretEvent e = new CaretEvent(this, oldText, curText);
    attrs.removeAttributeWeakListener(null, this);
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
      // oldText = curText = (String)e.getValue();
      // cursor = anchor = curText.length();
      // cursorReverseBias = false;
      // log.clear();
      // editMenuHandler.computeEnabled();
      cancelEditing();
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
        if (this.cursorPos != this.anchorPos)
          return false; // selection-delete
        else if (prev.repl.length() != 0)
          return false; // previous was not strictly deleting text
        if (prev.cursorPos != prev.anchorPos)
          return false; // previous was selection-delete
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
      cursorReverseBias = false;
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
      cursorReverseBias = false;
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
      cursorReverseBias = false;
      editMenuHandler.computeEnabled();
      canvas.getProject().repaintCanvas();
    }
  }

}

// FIXME / TODO
// x In auto-wrap mode, allow trailing whitespace to overhang textWidth
// x goto start/end line movements should stop at soft wraps
// x triple-click to select entire line should stop at soft wrap? actually no.
// x verify hit-test, esp with newline at end, or blank line (replaced by space)
// x review all code
// x don't render original component when caret is shown
// x when placing new "text", select all initially
// x Backspace/delete full glyph at a time
// - Markdown-like styling (header, bullets)
// - tab stops
// - caching (layout is computed repeatedly even within same action)
// - when placing text initially, sizing, or moving, snap to grid unless alt is held?
