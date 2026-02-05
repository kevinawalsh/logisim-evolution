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

package com.cburch.logisim.comp;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import com.cburch.logisim.Main;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.tools.Caret;
import com.cburch.logisim.tools.CaretEvent;
import com.cburch.logisim.tools.CaretListener;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.UndoRedo;

public class TextFieldCaret implements Caret, TextFieldListener {

  public static final Color EDIT_BACKGROUND = new Color(0xff, 0xff, 0x99);
  public static final Color EDIT_BORDER = Color.DARK_GRAY;
  public static final Color SELECTION_BACKGROUND = new Color(0x99, 0xcc, 0xff);

  private LinkedList<CaretListener> listeners = new LinkedList<CaretListener>();
  protected TextField field;
  protected Graphics g;
  protected String oldText;
  protected String curText;
  protected int cursor, anchor; // text between cursor and anchor is selected
  protected TextFieldCaretEditHandler editMenuHandler;
  protected UndoRedo log = new UndoRedo();
  protected Canvas canvas;

  // used during mouse selection
  boolean selectByWord = false;
  boolean selectByLine = false;
  int selectOrigin = 0;

  public TextFieldCaret(Canvas canvas, TextField field, Graphics g, int pos) {
    this.canvas = canvas;
    this.field = field;
    this.g = g;
    this.oldText = this.curText = field.getText();
    cursor = anchor = pos;

    editMenuHandler = new TextFieldCaretEditHandler();

    field.addTextFieldListener(this);
  }

  public EditHandler getEditHandler() { return editMenuHandler; }

  public TextFieldCaret(Canvas canvas, TextField field, Graphics g, int x, int y) {
    this(canvas, field, g, 0);
    cursor = anchor = findCaret(x, y);
  }

  public void addCaretListener(CaretListener l) {
    listeners.add(l);
  }

  public void cancelEditing() {
    CaretEvent e = new CaretEvent(this, oldText, oldText);
    curText = oldText;
    cursor = anchor = curText.length();
    for (CaretListener l : new ArrayList<CaretListener>(listeners)) {
      l.editingCanceled(e);
    }
    field.removeTextFieldListener(this);
    log.clear();
    editMenuHandler.computeEnabled();
  }

  public void commitText(String text) {
    curText = text;
    cursor = anchor = curText.length();
    log.clear();
    field.setText(text);
    editMenuHandler.computeEnabled();
  }

  public void draw(Graphics g) {
    int x = field.getX();
    int y = field.getY();
    int halign = field.getHAlign();
    int valign = field.getVAlign();
    Font font = field.getFont();
    if (font != null)
      g.setFont(font);

    // draw boundary
    Bounds box = getBounds(g);
    g.setColor(EDIT_BACKGROUND);
    g.fillRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());
    g.setColor(EDIT_BORDER);
    g.drawRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());

    // draw selection
    if (cursor != anchor) {
      g.setColor(SELECTION_BACKGROUND);
      Rectangle p = GraphicsUtil.getTextCursor(g, font, curText, x, y, cursor < anchor ? cursor : anchor, halign, valign);
      Rectangle e = GraphicsUtil.getTextCursor(g, font, curText, x, y, cursor < anchor ? anchor : cursor, halign, valign);
      g.fillRect(p.x, p.y - 1, e.x - p.x + 1, e.height + 2);
    }

    // draw text
    g.setColor(Color.BLACK);
    GraphicsUtil.drawText(g, curText, x, y, halign, valign);

    // draw cursor
    if (cursor == anchor) {
      Rectangle p = GraphicsUtil.getTextCursor(g, font, curText, x, y, cursor, halign, valign);
      g.drawLine(p.x, p.y, p.x, p.y + p.height);
    }
  }

  public String getText() {
    return curText;
  }

  public Bounds getBounds(Graphics g) {
    int x = field.getX();
    int y = field.getY();
    int halign = field.getHAlign();
    int valign = field.getVAlign();
    Font font = field.getFont();
    Bounds bds = Bounds.create(GraphicsUtil.getTextBounds(g, font, curText, x, y, halign, valign));
    Bounds box = bds.add(field.getBounds(g)).expand(3);
    return box;
  }

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

  protected boolean wordBoundary(int i) {
    return (i <= 0)
        || (i >= curText.length())
        || (whitespace(i-1) && !whitespace(i));
  }

  protected boolean whitespace(int i) {
    return Character.isWhitespace(curText.charAt(i));
  }

  protected boolean allowedCharacter(char c) {
    return (c != KeyEvent.CHAR_UNDEFINED) && !Character.isISOControl(c);
  }

  public void selectAll() {
    cursor = 0;
    anchor = curText.length();
    editMenuHandler.computeEnabled();
  }

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

  protected void menuShortcutKeyPressed(KeyEvent e, boolean shift) {
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
  // For a single-line text field are six possible cursor movements:
  //                                            
  //   .--------------------------------------.   (+1) next char      (-1) prev char
  //   |(-3)    (-2)   (-1)I(+1)   (+2)   (+3)|   (+2) next word      (-2) prev word
  //   '--------------------------------------'   (+3) end of line    (-3) start of line
  // 
  // For similar multi-line cursor mevements, see std/base/TextCaret.
  //
  //                                                   single-line          multi-line
  //          key          modifiers                   textfield action     textfield action
  // MacOS:
  //          left/right   -                           +/- 1                +/- 1            
  //          left/right   option/wordkey              +/- 2                +/- 2             
  //          left/right   command/menukey             +/- 3                +/- 3
  //          up/down      -                           +/- 3                +/- 4
  //          up/down      command/menukey             +/- 3                +/- 5
  //          home/end     -                           +/- 3                +/- 5
  //          pgup/pgdn    -                           +/- 3                +/- 5
  // Linux/Windows:
  //          left/right   -                           +/- 1                +/- 1            
  //          left/right   control/wordkey/menukey     +/- 2                +/- 2             
  //          up/down      -                           +/- 3                +/- 4
  //          up/down      control/wordkey/menukey     +/- 3                +/- 5
  //          home/end     -                           +/- 3                +/- 3
  //          home/end     control/wordkey/menukey     +/- 3                +/- 5
  //          pgup/pgdn    -                           +/- 3                +/- 5
  //
  // TODO: support for old style linux/apple movemet keys, like control-A / control-E ?

  protected void cancelSelection(int direction) {
    // selection is being canceled by left/right movement
    if (direction < 0) anchor = cursor;
    else cursor = anchor;
    editMenuHandler.computeEnabled();
  }
 
  // swap, if needed, so cursor <= anchor
  protected void normalizeSelection() {
    if (cursor > anchor) {
      int t = anchor;
      anchor = cursor;
      cursor = t;
    }
  }

  protected void moveCaret(int move, boolean shift) {
    if (!shift)
      normalizeSelection();

    if (move < -5 || move == 0 || move > 5) { // invalid
      return;
    } else if (move <= -3) { // start of line, up a line, start of text
      cursor = 0;
    } else if (move >= +3) { // end of line, down a line, end of text
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
    }

    if (!shift)
      anchor = cursor;
    editMenuHandler.computeEnabled();
  }

  protected void processMovementKeys(KeyEvent e, boolean shift, boolean wordkey, boolean menukey) {
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
        moveCaret(dir*5, shift); // start/end of text
      else
        moveCaret(dir*4, shift); // up/down a line
      e.consume();
      break;
    case KeyEvent.VK_PAGE_UP:
      dir = -1;
      // fall through
    case KeyEvent.VK_PAGE_DOWN:
      moveCaret(dir*5, shift); // start/end of text
      e.consume();
      break;
    case KeyEvent.VK_HOME:
      dir = -1;
      // fall through
    case KeyEvent.VK_END:
      if (Main.MacOS)
        moveCaret(dir*5, shift); //  MacOS start/end of text
      else if (menukey)
        moveCaret(dir*5, shift); // start/end of text
      else 
        moveCaret(dir*3, shift); // start/end of line
      e.consume();
      break;
    default:
      break;
    }
  }

  protected void normalKeyPressed(KeyEvent e, boolean shift) {
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
    case KeyEvent.VK_ENTER:
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

  public void keyReleased(KeyEvent e) { }

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

  public void mouseReleased(MouseEvent e) { }

  protected int findCaret(int x, int y) {
    x -= field.getX();
    y -= field.getY();
    int halign = field.getHAlign();
    int valign = field.getVAlign();
    return GraphicsUtil.getTextPosition(g, field.getFont(), curText, x, y, halign, valign);
  }

  public void removeCaretListener(CaretListener l) {
    listeners.remove(l);
  }

  public void stopEditing() {
    CaretEvent e = new CaretEvent(this, oldText, curText);
    field.setText(curText);
    for (CaretListener l : new ArrayList<CaretListener>(listeners)) {
      l.editingStopped(e);
    }
    field.removeTextFieldListener(this);
  }

  public void textChanged(TextFieldEvent e) {
    curText = field.getText();
    oldText = curText;
    cursor = anchor = curText.length();
    log.clear();
    editMenuHandler.computeEnabled();
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

  private class TextFieldCaretEditHandler extends EditHandler {

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
