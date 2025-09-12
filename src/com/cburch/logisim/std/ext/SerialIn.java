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

import java.util.ArrayDeque;
import java.util.ArrayList;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;

import com.fazecast.jSerialComm.SerialPort;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.UniquelyNamedThread;

public class SerialIn extends InstanceFactory {

  // TODO later, after prototype is working: expose all outputs
  // public static class Logger extends InstanceLogger { ... }

  public static class Poker extends InstancePoker {
    Rectangle r; // button position

    @Override
    public void mousePressed(InstanceState circState, MouseEvent e) {
      if (r == null || !r.contains(e.getX(), e.getY()))
        return;
      State s = getState(circState);
      synchronized (s) {
        if (!s.isOpen)
          s.open();
        else
          s.close();
      }
    }

    @Override
    public void paint(InstancePainter painter) {
      State state = getState(painter);
      Graphics2D g = (Graphics2D)painter.getGraphics();
      Bounds bds = painter.getNominalBounds();
      String s = (!state.isOpen ? S.get("serialInputOpen") : S.get("serialInputClose"));
      int x = bds.x + 30;
      int y = bds.y + bds.height - 5;
      GraphicsUtil.drawText(g, s, x, y, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM);
      r = GraphicsUtil.getTextBounds(g, s, x, y, GraphicsUtil.H_LEFT, GraphicsUtil.V_BOTTOM);
      g.setColor(Color.DARK_GRAY);
      g.drawRect(r.x-5, r.y, r.width + 10, r.height);
    }

  }

  // TODO: add a custom renderer/editor here to show enumerated serial ports
  public static final Attribute<String> ATTR_PORT =
      new SerialPortPathAttribute("port", S.getter("serialInputPort"));

  // TODO: maybe add one or two other common options? Are there any?
  static final AttributeOption MODE_8N1 = new AttributeOption("8n1", S.unlocalized("8n1"));
  static final AttributeOption MODE_8N2 = new AttributeOption("8n2", S.unlocalized("8n2"));
  static final AttributeOption MODE_8E1 = new AttributeOption("8e1", S.unlocalized("8e1"));
  static final AttributeOption MODE_8E2 = new AttributeOption("8e2", S.unlocalized("8e2"));
  static final AttributeOption MODE_8O1 = new AttributeOption("8o1", S.unlocalized("8o1"));
  static final AttributeOption MODE_8O2 = new AttributeOption("8o2", S.unlocalized("8o2"));
  static final AttributeOption MODE_7N1 = new AttributeOption("7n1", S.unlocalized("7n1"));
  static final AttributeOption MODE_7N2 = new AttributeOption("7n2", S.unlocalized("7n2"));
  static final AttributeOption MODE_7E1 = new AttributeOption("7e1", S.unlocalized("7e1"));
  static final AttributeOption MODE_7E2 = new AttributeOption("7e2", S.unlocalized("7e2"));
  static final AttributeOption MODE_7O1 = new AttributeOption("7o1", S.unlocalized("7o1"));
  static final AttributeOption MODE_7O2 = new AttributeOption("7o2", S.unlocalized("7o2"));
  static final Attribute<AttributeOption> ATTR_MODE =
      Attributes.forOption("mode", S.getter("ioSerialMode"),
          new AttributeOption[] {
            MODE_8N1, MODE_8N2, MODE_8E1, MODE_8E2, MODE_8O1, MODE_8O2,
            MODE_7N1, MODE_7N2, MODE_7E1, MODE_7E2, MODE_7O1, MODE_7O2 });

  // async mode:
  //   Outputs reflect most recent serial data, but are UNKNOWN initially.
  //   There is no clock. If serial data arrives very quickly, simulator could
  //   miss some data. Data is not queued as it is read from serial port, only
  //   the most recent data is kept.
  // sync mode:
  //   Outputs reflect most recent serial data, but only update on a clock
  //   trigger. If clock is not triggered, outputs stay constant, and data
  //   is queued. Data is queued as it is read from serial port, one set of
  //   values is passed to the simulator for each clock edge.
  static final AttributeOption CLOCKING_ASYNCHRONOUS =
      new AttributeOption("asynchronous", S.getter("serialInputAsynchronous"));
  static final AttributeOption CLOCKING_SYNCHRONOUS =
      new AttributeOption("synchronous", S.getter("serialInputSynchronous"));
  static final Attribute<AttributeOption> ATTR_CLOCKING =
      Attributes.forOption("clocking", S.getter("serialInputClocking"),
          new AttributeOption[] { CLOCKING_ASYNCHRONOUS, CLOCKING_SYNCHRONOUS });

  // max size of queued values, used only for SYNC case
  // (in async case, the queue size is effectively just 1)
  private static Attribute<Integer> ATTR_QUEUE =
      Attributes.forIntegerRange("queue", S.getter("serialInputQueueSize"), 1, 32*1024);
  
  // (in async case, the queue size is effectively just 1)
  private static Attribute<Integer> ATTR_BAUD =
      Attributes.forIntegerRange("baud", S.getter("serialInputBaud"), 110, 256000);

  // delim is used to parse the incoming bytes from serial port into records
  // to be parsed.  For example:
  //   "[\n]" --> break data at newline, parse each line as a record
  //   "[ \t\r\n]" --> split on simple whitespace
  //   "[, ]" --> split on commas and spaces
  // In all cases, multiple delimiters in a row are treated as one, i.e. empty
  // records are ignored.
  // If delim is left empty, a default is used.
  public static final Attribute<String> ATTR_DELIMITER =
      Attributes.forString("delimiter", S.getter("serialInputDelimiter"));
 
  // Format string is a simple DSL for parsing records into Value vectors. The
  // string is something like a regex or scanf-style string, but needs to
  // specify the widths more explicitly so we know how wide the output ports
  // should be.
  // Examples:
  //   "%8d %8d %8d" --> three ports, 8 bits each, data looks like "17 -35 28"
  //   "%4d,%4d:%32d" --> three ports, 4 4 and 32 bits, data looks like "-9,5:39135"
  //   "(%8u, %8d)" -->  two ports, 8 bits each, data looks like "(255, -123)"
  // Possible tokens might include:
  //   "%8d" --> 8 bits, formated as signed decimal in range -128 to +127
  //   "%8u" --> 8 bits, formated as unsigned decimal in range 0 to 255
  //   "%32d" --> 32 bits, formated as signed or unsigned decimal in range -2^31 to 2^32-1
  //   "%4b" --> 4 bits, formatted in binary with 0s and 1s
  //   "%1b" --> 1 bit, just a simple 0 or 1
  //   "%10x" --> 10 bits, formatted in hex, e.g. "3FC" or "3fc"
  //   "%c" --> 8 bits, a raw byte taken directly from the data stream
  //   "," --> matches and discards a comma
  //   " " --> matches and discards a space
  //   "%%" --> matches and discards a literal "%"
  //   [char] --> matches and discards some other character
  // In cases where the format matches but data is out of range for one of the
  // tokens, that token produces ERROR. So to match "%8d" we just parse as a
  // signed decimal java int (or maybe long? or even just any sequence of
  // digits with optional +/- prefix?), then enforce the range.
  // In cases where only a prefix of the format matches, the rest of the tokens
  // produce UNKNOWN. Or if that's tricky/unclean in code, the entire set of
  // values could be set to ERROR.
  // Extra/trailing data in a record after a match is discarded.
  // If the format doesn't match the record at all, the entire record is discarded and
  // all values are set to ERROR values.
  public static final Attribute<Matcher> ATTR_FORMAT =
      new MatcherAttribute("format", S.getter("serialInputFormat"));


  public SerialIn() {
    super("SerialIn", S.getter("serialInputComponent"));
    setAttributes(new Attribute[] { /* StdAttr.FACING, */
      ATTR_MODE, ATTR_BAUD, ATTR_FORMAT, ATTR_DELIMITER, ATTR_PORT,
      ATTR_CLOCKING, StdAttr.EDGE_TRIGGER, ATTR_QUEUE,
      StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR },
      new Object[] { /* Direction.EAST, */
        MODE_8N1, 115200, parseFormat("%8d %8d %8d"), "[\\n]", "/dev/ttyUSB0",
        CLOCKING_ASYNCHRONOUS, StdAttr.TRIG_RISING, 1024,
        "", StdAttr.LABEL_CENTER, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK });
    setIconName("serial-in.png");
    // setFacingAttribute(StdAttr.FACING);
    // setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC));
    setInstancePoker(Poker.class);
    // setInstanceLogger(Logger.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    recomputeLabelTextFieldPosition(instance);
  }

  private void recomputeLabelTextFieldPosition(Instance instance) {
    AttributeOption clocking = instance.getAttributeValue(ATTR_CLOCKING);
    if (clocking == CLOCKING_SYNCHRONOUS)
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_RIGHT | Instance.AVOID_BOTTOM);
    else
      instance.computeLabelTextField(Instance.AVOID_CENTER | Instance.AVOID_RIGHT);
  }

  private void updatePorts(Instance instance) {
    AttributeOption clocking = instance.getAttributeValue(ATTR_CLOCKING);
    Matcher m = instance.getAttributeValue(ATTR_FORMAT);
    int n = m.widths.length + (clocking == CLOCKING_SYNCHRONOUS ? 2 : 0);
    Port[] ps = new Port[n];
    for (int i = 0; i < m.widths.length; i++) {
      ps[i] = new Port(0, -10 * i, Port.OUTPUT, m.widths[i]);
      ps[i].setToolTip(S.getter("serialInputDataTip", ""+(i + 1)));
    }
    if (clocking == CLOCKING_SYNCHRONOUS) {
      ps[n - 2] = new Port(-40, 10, Port.INPUT, 1); // read enable
      ps[n - 2].setToolTip(S.getter("serialInputReadEnableTip"));
      ps[n - 1] = new Port(-30, 10, Port.INPUT, 1); // clock
      ps[n - 1].setToolTip(S.getter("serialInputClockTip"));
    }
    instance.setPorts(ps);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Matcher m = attrs.getValue(ATTR_FORMAT);
    int p = m.widths.length;
    return Bounds.create(-80, -(10 + 10 * p), 80, 20 + 10 * p);
  }

  @Override protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC) {
      recomputeLabelTextFieldPosition(instance);
    } else if (attr == ATTR_CLOCKING) {
      updatePorts(instance);
      recomputeLabelTextFieldPosition(instance);
      instance.fireInvalidated(); // recompute using clock signal
    } else if (attr == ATTR_FORMAT) {
      updatePorts(instance);
      instance.recomputeBounds();
      recomputeLabelTextFieldPosition(instance);
      instance.fireInvalidated();
    } else {
      // propagate updates State queue length, delimiter, format, etc.
      instance.fireInvalidated();
    }
  }

  static final Color ON_COLOR = Color.GREEN;
  static final Color OFF_COLOR = Color.LIGHT_GRAY;

  @Override
  public void paintInstance(InstancePainter painter) {
    painter.drawBounds();

    Bounds bds = painter.getNominalBounds();

    // TODO: draw a USB-like symbol in top left corner

    if (painter.getShowState()) {
      State state = (State)painter.getData();
      // Connection status LED
      Color c = (state != null && state.isOpen ? ON_COLOR : OFF_COLOR);
      Graphics2D g = (Graphics2D)painter.getGraphics();
      g.setColor(c);
      g.fillRect(bds.x+5, bds.y+bds.height-15, 12, 8);
      g.setColor(Color.GRAY);
      g.drawRect(bds.x+5, bds.y+bds.height-15, 12, 8);
      // TODO: Maybe also a blinking activity light?

      g.setColor(Color.BLACK);
      GraphicsUtil.drawText(g, state == null ? "" : state.status, bds.x+5, bds.y+5, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
    }

    painter.drawLabel();
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState circState) {
    State state = getState(circState);
    int[] widths = null;
    synchronized (state) {
      widths = state.matcher.widths;
    }
    int n = widths.length;

    Value[] vals = null;

    AttributeOption clocking = circState.getAttributeValue(ATTR_CLOCKING);
    if (clocking == CLOCKING_SYNCHRONOUS) {
      Value enable = circState.getPortValue(n-2);
      if (enable == Value.FALSE)
        return;
      AttributeOption trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
      Value clock = circState.getPortValue(n-1);
      Value lastClock = state.setLastClock(clock);
      boolean go;
      if (trigger == StdAttr.TRIG_FALLING) {
        go = lastClock == Value.TRUE && clock == Value.FALSE;
      } else {
        go = lastClock == Value.FALSE && clock == Value.TRUE;
      }
      synchronized (state) {
        if (go)
          vals = state.q.pollFirst();
      }
    } else { // CLOCKING_ASYNCHRONOUS
      synchronized (state) { 
        vals = state.lastAsync;
      } // end synchronized
    }
    
    for (int i = 0; i < n; i++) {
      int b = widths[i];
      Value v = (vals == null ? null : vals[i]);
      if (v != null && v.getWidth() != widths[i])
        v = null;
      if (v == null)
        v = Value.createUnknown(BitWidth.create(b));
      circState.setPort(i,v, 1);
    }
  }

  static abstract class Token {
    int width = 0;
    Value lastVal;
    abstract int consume(byte[] buf, int pos, int len);
  }
  static class StaticToken extends Token {
    String tok = "";
    byte[] bytes = new byte[0];
    StaticToken() { }
    void extend(String s) {
      tok += s;
      bytes = tok.getBytes(StandardCharsets.UTF_8);
    }
    int consume(byte[] buf, int pos, int len) {
      int n = bytes.length;
      for (int i = 0; i < n; i++) {
        if (len < 0 || buf[pos] != bytes[i])
          return i;
      }
      return n;
    }
  }
  static class ByteToken extends Token {
    ByteToken() { width = 8; }
    int consume(byte[] buf, int pos, int len) {
      if (len == 0) {
        lastVal = Value.createUnknown(BitWidth.EIGHT);
        return 0;
      } else {
        lastVal = Value.createKnown(BitWidth.EIGHT, buf[pos]);
        return 1;
      }
    }
  }
  static class SignedDecimalToken extends Token {
    SignedDecimalToken(int w) {
      width = w;;
    }
    int consume(byte[] buf, int pos, int len) {
      int cnt = 0;
      boolean negative = false;
      if (len > 0 && buf[pos] == '-') {
        pos++; len--; cnt++;
        negative = true;
      } else if (len > 0 && buf[pos] == '+') {
        pos++; len--; cnt++;
      }
      int digits = 0;
      long x = 0;
      while (len > 0 && within((char)(buf[pos] & 0xff), '0', '9')) {
        x = 10 * x + (buf[pos] - '0');
        pos++; len--;
        digits++;
      }
      if (negative)
        x = -x;
      if (digits == 0)
        lastVal = Value.createUnknown(BitWidth.create(width));
      else
        lastVal = Value.createKnown(BitWidth.create(width), (int)x);
      return cnt + digits;
    }
  }
  static class RadixToken extends Token {
    int radix;
    RadixToken(int w, int r) {
      width = w;;
      radix = r;
    } 
    int consume(byte[] buf, int pos, int len) {
      int cnt = 0;
      long x = 0;
      while (len > 0 && isHex((char)(buf[pos] & 0xff)) && fromHex((char)(buf[pos] & 0xff)) < radix) {
        x = radix * x + fromHex((char)(buf[pos] & 0xff));
        pos++; len--;
        cnt++;
      }
      if (cnt == 0)
        lastVal = Value.createUnknown(BitWidth.create(width));
      else
        lastVal = Value.createKnown(BitWidth.create(width), (int)x);
      return cnt;
    }
  }

  // fixme: this should be the attrib directly?
  static class Matcher {
    String fmt;
    int[] widths;
    ArrayList<Token> tokens = new ArrayList<>();
    boolean hasNewline, hasWhitespace;
    boolean valid = true;
    String errmsg;

    Matcher(String fmt) {
      this.fmt = fmt;
    }

    void push(char c) {
      if (tokens.isEmpty() || !(tokens.get(0) instanceof StaticToken))
        tokens.add(new StaticToken());
      StaticToken t = (StaticToken)tokens.get(tokens.size()-1);
      t.extend("" + c);
      if (c == '\n' || c == '\r')
        hasNewline = hasWhitespace = true;
      else if (c == ' ' || c == '\t')
        hasWhitespace = true;
    }
    void push(int codepoint) {
      if (0 <= codepoint && codepoint < 127) {
        push((char)(codepoint & 0x7f));
      } else {
        try {
          String s = new String(Character.toChars((int)codepoint));
          if (tokens.isEmpty() || !(tokens.get(0) instanceof StaticToken))
            tokens.add(new StaticToken());
          StaticToken t = (StaticToken)tokens.get(tokens.size()-1);
          t.extend(s);
        } catch (Exception e) {
          err(String.format("\\u%x is not a valid unicode codepoint (%s)", codepoint, e.getMessage()));
        }
      }
    }

    void push(Token t) {
      tokens.add(t);
    }

    void err(String msg) {
      valid = false;
      if (errmsg == null)
        errmsg = msg;
    }
  }

  private static boolean within(char c, char s, char e) {
    return s <= c && c <= e;
  }
  private static boolean isHex(char c) {
    return within(c, '0', '9') ||
        within(c, 'A', 'F') ||
        within(c, 'a', 'f');
  }
  private static int fromHex(char c) {
    return within(c, '0', '9') ? (c - '0')
        : within(c, 'A', 'F') ? (c - 'A')
        : within(c, 'a', 'f') ? (c - 'a')
        : 0;
  }

  private static Matcher parseFormat(String fmt) {
    Matcher m = new Matcher(fmt);
    boolean escaped = false;
outer:
    for (int i = 0, e = fmt.length(); i < e; i++) {
      char c = fmt.charAt(i);
      if (escaped) {
        // process escapes
        if (c == 'n') m.push('\n');
        else if (c == 'r') m.push('\r');
        else if (c == 't') m.push('\t');
        else if (c == 'a') m.push('\u0007');
        else if (c == 'b') m.push('\b');
        else if (c == 'e') m.push('\u001B');
        else if (c == 'f') m.push('\u000C');
        else if (c == 'v') m.push('\u000B');
        else if (c == '\\') m.push('\\');
        else if (c == '\'') m.push('\'');
        else if (c == '"') m.push('\"');
        else if (c == '?') m.push('?');
        else if (c == 'x' || c == 'u') { // hex or unicode
          boolean unicode = (c == 'u');
          if (i + 1 < e && isHex(fmt.charAt(i+1))) {
            int cnt = 0;
            long x = 0;
            while (i + 1 < e && isHex(fmt.charAt(i+1))) {
              x = 16 * x + fromHex(fmt.charAt(++i));
              cnt++;
              if (unicode && cnt == 4) break;
              else if (!unicode && cnt == 2) break;
            }
            if (unicode) {
              if (cnt != 4)
                m.err("\\u must be followed by four hex digits");
              m.push((int)(x & 0xffffffffL)); // codepoint
            } else {
              m.push((char)(x & 0xff));
            }
          } else {
            // malformed... missing hex digits
            m.err(unicode ? "\\u must be followed by four hex digits" :
                "\\x must be followed by one or two hex digits");
          }
        } else if (within(c, '0', '7')) { // octal
          int x = (c - '0');
          if (i + 1 < e && within(fmt.charAt(i+1), '0', '7'))
            x = 8 * x + (fmt.charAt(++i) - '0');
          if (i + 1 < e && within(fmt.charAt(i+1), '0', '7'))
            x = 8 * x + (fmt.charAt(++i) - '0');
          m.push((char)x);
        } else {
          m.push(c); // malformed, but allow whatever else.
          m.err(String.format("\\%c is not a valid escape sequence", c));
        }
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else if (c != '%') {
        m.push(c);
      } else {
        if (++i >= e) {
          m.err("% must be followed by a format string, or use %% for a literal percent sign");
          break outer; // malformed... trailing %
        }
        else c = fmt.charAt(i);  
        // %c --> raw byte
        if (c == '%') {
          m.push('%');
          continue;
        }
        if (c == 'c' || c == 'C') {
          m.push(new ByteToken());
          continue;
        }
        // get width
        int w = 0;
        while (Character.isDigit(c)) {
          w = w * 10 + (c - '0');
          if (++i >= e) {
            m.err("%-style format string should end in: c, d, u, b, o, u, or x");
            break outer; // malformed... missing fmt char
          }
          else c = fmt.charAt(i);  
        }
        if (w <= 0) { // malformed... missing or zero width
          m.err("invalid width (" + w + "), must be 1-32");
          w = 8; // whatever
        } else if (w > 32) { // malfomed... width too large
          m.err("invalid width (" + w + "), must be 1-32");
          w = 32; 
        }
        if (c == 'd') m.push(new SignedDecimalToken(w));
        else if (c == 'b') m.push(new RadixToken(w, 2));
        else if (c == 'o') m.push(new RadixToken(w, 8));
        else if (c == 'u') m.push(new RadixToken(w, 10));
        else if (c == 'x') m.push(new RadixToken(w, 16));
        else if (c == 'c') {
          if (w != 8)
            m.err("%" + w + "c is not valid, only %c (or, equivalently, %8c) is allowed");
          m.push(new ByteToken());
        } else {
          m.push(new SignedDecimalToken(w)); // malformed... bad fmt char
          m.err("%" + c + " is not valid, should be: c, d, u, b, o, u, or x");
        }
      }
    }
    if (escaped)
      m.err("'\\' at end of string should be a complete escape sequence");

    ArrayList<Integer> widths = new ArrayList<>();
    for (Token t : m.tokens) {
      if (t.width > 0)
        widths.add(t.width);
    }
    if (widths.isEmpty()) {
      m.push(new ByteToken()); // default for empty or fully invalid fmt
      widths.add(8);
      m.err("format string must describe format of values to expect, like %32d, %16x, etc.");
    }
    m.widths = widths.stream().mapToInt(v->v).toArray();
    return m;
  }

  private static Value[] parseRecord(Matcher m, byte[] buf, int pos, int len) {
    Value[] out = new Value[m.widths.length];
    int i = 0;
    for (Token t : m.tokens) {
      int n = t.consume(buf, pos, len);
      pos += n;
      len -= n;
      if (t.lastVal != null)
        out[i++] = t.lastVal;
    }
    return out;
  }

  private static State getState(InstanceState circState) {
    State state = (State) circState.getData();
    if (state == null) {
      state = new State(circState);
      circState.setData(state);
    } else {
      state.updateBinding(circState);
    }
    return state;
  }

  private static class State implements InstanceData, Cloneable {

    Value lastClock = Value.UNKNOWN;

    volatile boolean isOpen = false;
    volatile String status = "ready";

    AttributeOption mode;
    int baud, qlen;
    String fmt, delim, path;
    int[] bits;

    Matcher matcher;
    
    Instance instance; // TODO: assign, and listen for circuit removal
    
    SerialPort port;
    Thread worker;

    // shared between worker and others
    ArrayDeque<Value[]> q = new ArrayDeque<>();
    Value[] lastAsync = null;

    State(InstanceState circState) {
      updateBinding(circState);
    }
    
    private State(State other) {
      synchronized(other) {
        mode = other.mode;
        baud = other.baud;
        qlen = other.qlen;
        fmt = other.fmt;
        delim = other.delim;
        path = other.path; // should not open both at same time...
        matcher = other.matcher;
        instance = null; // don't know which instance this will be for? 
      }
    }

    synchronized void updateBinding(InstanceState circState) {
      if (instance != null) {
        // are we still attached to the same instance?
        Instance i = circState.getInstance();
        if (i == null) {
          System.err.println("Trouble... instance missing?!?!?");
        } else if (i != instance) {
          System.err.println("Trouble ahead: instance mismatch?!?!?");
          instance = i;
        }
      } else {
        instance = circState.getInstance();
      }
      AttributeSet attrs = circState.getAttributeSet();
      AttributeOption mode = circState.getAttributeValue(ATTR_MODE);
      String path = circState.getAttributeValue(ATTR_PORT);
      int baud = circState.getAttributeValue(ATTR_BAUD);
      Matcher m = circState.getAttributeValue(ATTR_FORMAT);
      String delim = circState.getAttributeValue(ATTR_DELIMITER);
      int qlen = circState.getAttributeValue(ATTR_QUEUE);
   
      // TODO: fmt, delim sanity checks and defaults
      // if (fmt.equals("")) {
      //   fmt = "%c"; // take raw bytes, skip nothing
      // } else if (delim.equals("")) {
      //   delim = "\\n"; // fixme, be more clever here about defaults
      // }
      
      // TODO: normalize delim

      if (!path.equals(this.path)) {
        System.out.println("path changed");
        close();
        this.path = path;
      }
      if (qlen != this.qlen) {
        System.out.println("qlen changed");
        this.qlen = qlen;
        while (q.size() > qlen)
          q.removeFirst();
      }
      if (!m.fmt.equals(this.fmt) || !delim.equals(this.delim)) {
        System.out.println("fmt/delim changed");
        q.clear();
        lastAsync = null;
        this.delim = delim;
        this.fmt = m.fmt;
        this.matcher = m;
        // TODO if invalid matcher, or if delim is empty...?
      }
      if (baud != this.baud || mode != this.mode) {
        System.out.println("baud/mode changed");
        this.baud = baud;
        this.mode = mode;
        if (port != null) {
          // close and re-open?
          port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        }
      }
    }

    public Value setLastClock(Value newClock) {
      Value ret = lastClock;
      lastClock = newClock;
      return ret;
    }

    @Override
    public Object clone() {
      return new State(this);
    }

    private void run() {
      try {
        int cap = 1024;
        byte[] buf = new byte[cap]; // circular buf, 1024 bytes
        int w = 0; // next position to write into
        int r = 0; // next position to read
        int n = 0; // number of bytes 
        boolean first = true;
        while (isOpen) {
          if (n == cap) {
            r = (r + 1) % cap;
            n--;
          }
          int ok = port.readBytes(buf, 1, w);
          if (ok < 0)
            break;
          w = (w + 1) % cap;
          n++;
          synchronized (this) {
            // FIXME,split on delim
            if (buf[(r + n - 1) % cap] == '\n') {
              if (!first) {
                Value[] vals = parseRecord(matcher, buf, r, n-1);
                boolean changed = lastAsync == null || vals.length != lastAsync.length ;
                for (int i = 0; !changed && i < vals.length; i++)
                  changed = !vals[i].equals(lastAsync[i]);
                while (q.size() >= qlen)
                  q.removeFirst();
                q.addLast(vals);
                if (changed) {
                  lastAsync = vals;
                  if (instance == null)
                    System.out.println("missing instance???");
                  else if (instance.getAttributeValue(ATTR_CLOCKING) == CLOCKING_ASYNCHRONOUS)
                    instance.fireInvalidated();
                }
              } else {
                first = false;
              }
              w = r = n = 0;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        close();
      }
    }

    synchronized void open() {
      if (isOpen)
        return;
      try {
        status = "opening";
        port = SerialPort.getCommPort(path);
        port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
        port.setDTR();
        port.setRTS();
        if (!port.openPort()) {
          status = String.format("error (%d)", port.getLastErrorCode());
          port = null;
          return;
        }
        isOpen = true;
        status = "opened";
        worker = new UniquelyNamedThread(this::run, "SerialInputReader");
        worker.setDaemon(true);
        worker.start();
      } catch (Exception e) {
        isOpen = false;
        status = "error";
        port = null;
        worker = null;
        e.printStackTrace();
      }
      if (instance != null)
        instance.fireInvalidated();
      else
        System.err.println("missing instance in open()???");
    }

    synchronized void close() {
      isOpen = false;
      if (port != null) {
          try {
          port.closePort();
        } catch (Exception e) {
          e.printStackTrace();
        }
        port = null;
      }
      if (worker != null) {
        if (worker != Thread.currentThread()) {
          try {
            worker.interrupt();
            worker.join(1000);
          } catch (Exception e) {
            e.printStackTrace();
          }
          if (worker.isAlive()) {
            System.err.println("Can't stop " + worker);
          }
        }
        worker = null;
        q.clear();
        lastAsync = null;
      }
      if (instance != null)
        instance.fireInvalidated();
      else
        System.err.println("missing instance in close()???");
    }

  }

  private static class SerialPortPathAttribute extends Attribute<String> {
    public SerialPortPathAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public java.awt.Component getCellEditor(Window source, String value) {
      return new SerialPortChooser(source, value);
    }

    @Override
    public String parse(String value) {
      return value;
    }
  }

  private static class MatcherAttribute extends Attribute<Matcher> {
    public MatcherAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public Matcher parse(String value) {
      Matcher m = parseFormat(value);
      if (!m.valid)
        Errors.title(S.get("serialInputFormatErrorTitle")).show(m.errmsg);
      return m;
    }

    @Override
    public String toDisplayString(Matcher value) {
      return toStandardString(value);
    }

    @Override
    public String toStandardString(Matcher m) {
      return m.fmt;
    }

  }

}
