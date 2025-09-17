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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class SerialInputFormat {

  // Delimiter string is used to parse the incoming bytes from serial port into
  // records to be parsed.  For example:
  //   "[\\n]" --> break data at newline, parse each line as a record
  //   "[ \\t\\r\\n]" --> split on simple whitespace
  //   "[, ]" --> split on commas and spaces
  // In all cases, multiple adjacent delimiters are treated as one, i.e. empty
  // records are ignored.
  //
  // If the delimiter set is left empty, ... FIXME

  // Format string is a simple DSL for parsing records into Value vectors. The
  // string is something like a regex or scanf-style string, but needs to
  // specify the widths more explicitly so we know how wide the output ports
  // should be.
  // Examples:
  //   "%8d %8d %8d" --> three ports, 8 bits each, data looks like "17 -35 28"
  //   "%4d,%4d:%32d" --> three ports, 4 4 and 32 bits, data looks like "-9,5:39135"
  //   "(%8u, %8d)" -->  two ports, 8 bits each, data looks like "(255, -123)"
  //
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
  //
  // In cases where the format matches but data is out of range for one of the
  // tokens, that token produces ERROR. For example, to match "%8d" we just
  // parse as a signed decimal java int (digits, with optional +/- prefix), then
  // enforce the range and produce ERROR if the value is out of range.
  //
  // In cases where only a prefix of the format matches, the rest of the tokens
  // produce UNKNOWN. 
  //
  // Extra/trailing data in a record after a match is discarded.
  //
  // If the format doesn't match the record at all, the entire record is discarded and
  // all values are set to UNKNOWN values.

  // Note on unicode, ascii, and character endodings: we treat serial input data
  // mostly as bytes, not text. The format and delimiter specifiers aren't
  // really text strings, in the full unicode sense, or even in the 7-bit clean
  // ascii sense. Instead, they specify byte sequences, because we want to be
  // able to match on arbitrary byte data from the serial port, like zero bytes,
  // or 0xff bytes, etc. The serial data is *not* assumed to be utf8 or any
  // other particular encoding.
  //
  // Below, it is unavoidable that some Java String values are sometimes used,
  // e.g. because the user will enter format and delimiter specifiers using
  // swing components (String-oriented), or when they are stored in the XML file
  // (String-oriented), or get passed to other places in logisim. We allow the
  // escapes (like "\xff") so the user can directly specify any desired byte
  // value. We also allow unicode characters and escapes outside ascii, like
  // emoji, or "\uc3b5", or "\U10FFFD". However, whenever such unicode is
  // encountered, it is immediately replaced with the corresponding utf-8 byte
  // sequences.
  //
  // Also note: bytes from escape sequences aren't always treated the same as
  // the non-escaped versions, for example:
  //  - A delimiter "[,]" is allowed, but "\x5B,\x5D" is not.
  //  - "...%8d..." is taken as an 8-bit hex value placeholder,
  //    but "...%8\x64..." is considered an error.

  // For raw mode:
  //   del = ""
  //   fmt = ""
  //   widths = { 8 }
  //   tokens = { ByteToken() }
  //
  // For text mode, either del or fmt must be non-empty
  //   del = possibly empty, possibly invalid
  //   fmt = possibly empty, possibly invalid
  //   widths = { ... one or more values 1-32 ... } 
  //   tokens = { ... stuff matching widths ... }
  //
  // NOTE: we don't currently check to ensure delims don't appear in the fmt
  // pattern. We might also want to warn if delims include characters
  // that might appear in valid values, like 0-9 a-f A-F, or + and - if
  // signed decimal values appear in the fmt.

  private String del;    // e.g. "[\n\r\t]", or "" for raw mode
  private String fmt;    // e.g. "%32d, %8d, %8x", or "" for raw mode
  private int[] widths;  // e.g. { 32, 8, 8}, or { 8 } for raw mode
  private byte[] delims; // e.g. { 0x0A, 0x0D, 0x20 } or null for raw mode

  private ArrayList<Token> tokens;
  // private boolean hasNewline, hasWhitespace;

  private boolean valid = true;
  private String errmsg;

  public SerialInputFormat() { // raw mode
    this("", ""); 
  }

  public SerialInputFormat(SerialInputFormat other) {
    this(other.del, other.fmt); 
  }

  // Note: both del and fmt may contain escapes, and
  // they are unescaped before processing.
  public SerialInputFormat(String del, String fmt) {
    this.fmt = fmt == null ? "" : fmt;
    this.del = del == null ? "" : del;
    parseTokens();
    convertDelims();
    convertWidths();
  }

  public String getDelimiterString() { return del; }

  public String getFormatString() { return fmt; }

  public boolean isValid() { return valid; }

  public String errorMessage() { return errmsg; }

  public ArrayList<Token> getTokens() { return tokens; }

  public byte[] getDelims() { return delims; }

  public boolean hasDelimiters() { return delims != null; }

  public boolean isDelim(byte x) {
    return delims != null && contained(x, delims, delims.length);
  }

  public boolean isRaw() {
    return delims == null && 
        tokens.size() == 1 &&
        tokens.get(0) instanceof ByteToken;
  }

  public int numValues() {
    return widths.length;
  }

  public int[] getWidths() {
    return widths;
  }

  public boolean sameAs(SerialInputFormat other) {
    // We could check on parsed structures, but whatever.
    return del.equals(other.del) && fmt.equals(other.fmt);
  }

  @FunctionalInterface
  private interface PlainByteConsumer {
    // Consumes byte b = s[i], and possibly other
    // bytes after s[i], where e is the length of s.
    // These bytes come directly from the original
    // byte-string, they were not escaped.
    // Returns position of last consumed byte:
    //  i if only s[i] was consumed,
    //  i+1 if s[i] and s[i+1] was consumed,
    //  i+n if s[i]...s[i+n] was consumed, etc.
    int consume(byte b, int i, int e);
  }

  @FunctionalInterface
  private interface UnescapedByteConsumer {
    // Consumes byte b, the result of unescaping.
    // This byte was not in the original byte-string.
    void consume(byte b);
  }

  private void unescapeAndApply(byte[] s,
      UnescapedByteConsumer unescaped,
      PlainByteConsumer plain) {
    boolean escaped = false;
outer:
    for (int i = 0, e = s.length; i < e; i++) {
      byte b = s[i];
      if (escaped) {
        // process escapes
        if (b == 'n') unescaped.consume((byte)'\n');
        else if (b == 'r') unescaped.consume((byte)'\r');
        else if (b == 't') unescaped.consume((byte)'\t');
        else if (b == 'a') unescaped.consume((byte)0x07);
        else if (b == 'b') unescaped.consume((byte)'\b');
        else if (b == 'e') unescaped.consume((byte)0x1B);
        else if (b == 'f') unescaped.consume((byte)0x0C);
        else if (b == 'v') unescaped.consume((byte)0x0B);
        else if (b == '\\') unescaped.consume((byte)'\\');
        else if (b == '\'') unescaped.consume((byte)'\'');
        else if (b == '"') unescaped.consume((byte)'\"');
        else if (b == '?') unescaped.consume((byte)'?');
        else if (b == 'x' || b == 'u' || b == 'U') { // hex or unicode
          boolean unicode4 = (b == 'u');
          boolean unicode8 = (b == 'U');
          if (i + 1 < e && isHex(s[i+1])) {
            int digits = 0;
            long x = 0;
            while (i + 1 < e && isHex(s[i+1])) {
              x = 16 * x + fromHex(s[++i]);
              digits++;
              if (unicode4 && digits == 4) break;
              else if (unicode8 && digits == 8) break;
              else if (!unicode4 && !unicode8 && digits == 2) break;
            }
            if (!unicode4 && !unicode8) {
              unescaped.consume((byte)(x & 0xff));
            } else if (unicode4 && digits != 4) {
              err("\\u must be followed by four hex digits");
            } else if (unicode8 && digits != 8) {
              err("\\U must be followed by four eight digits");
            } else {
              try {
                String bs = new String(Character.toChars((int)x));
                for (byte utf8: bs.getBytes(StandardCharsets.UTF_8))
                  unescaped.consume(utf8);
              } catch (Exception ex) {
                err(String.format("\\%c%0"+digits+"x is not a valid unicode codepoint (%s)", b, x, ex.getMessage()));
              }
            }
          } else {
            // malformed... missing hex digits
            err(unicode4 ? "\\u must be followed by four hex digits" :
                unicode8 ? "\\U must be followed by eight hex digits" :
                "\\x must be followed by one or two hex digits");
          }
        } else if (within(b, '0', '7')) { // octal
          int x = (b - '0');
          if (i + 1 < e && within(s[i+1], '0', '7'))
            x = 8 * x + (s[++i] - '0');
          if (i + 1 < e && within(s[i+1], '0', '7'))
            x = 8 * x + (s[++i] - '0');
          if (x > 0xff)
            err(String.format("\\%o is not a valid octal escape", x));
          unescaped.consume((byte)x);
        } else {
          unescaped.consume(b); // malformed, but allow whatever else.
          if (b >= 0x20 || b <= 0x7E)
            err(String.format("\\%c is not a valid escape sequence", (char)b));
          else
            err(String.format("\\<<%x>> is not a valid escape sequence", b));
        }
        escaped = false;
      } else if (b == '\\') {
        escaped = true;
      } else {
        i = plain.consume(b, i, e);
      }
    }
    if (escaped)
      err("invalid escape prefix '\\' at end of string");
  }

  private void push(ByteArrayOutputStream partialToken, Token t) {
    if (partialToken.size() > 0) {
      tokens.add(new StaticToken(partialToken.toByteArray()));
      partialToken.reset();
    }
    tokens.add(t);
  }
  
  private void parseTokens() {
    tokens = new ArrayList<>();
    // - If both del and fmt are empty, that is raw mode.
    // - If fmt is empty, but del isn't, we use a semi-raw
    // mode with a default format equivalent to "%c". This
    // means most bytes in the stream are taken as-is, like
    // raw mode, but delimiter chars are skipped.
    if (fmt.isEmpty()) {
      tokens.add(new ByteToken());
      return;
    }
    // if del is empty, but fmt isn't, this *could* be
    // a valid situation, e.g. fmt="%4x" or fmt="%1b" or 
    // fmt="%3u", any format that can match a single byte,
    // or certain other formats that can match without any
    // ambiguity on the stream as it arrives. So we assume
    // the user intended this.
    // Alternative, we could use some smart default delimiter? E.g.:
    // if (del.isEmpty()) {
    //   if (tokens.size() == 1 && tokens.get(0) instanceof ByteToken) {
    //     del = ""; // no delimiter, just raw bytes
    //   } else if (!hasWhitespace) {
    //     del = "[\n\r\t ]";
    //   } else if (!hasNewline) {
    //     del = "[\r\n]";
    //   } else {
    //     del = ""; // fallback
    //   }
    // }

    ByteArrayOutputStream partialToken = new ByteArrayOutputStream();
    byte[] s = fmt.getBytes(StandardCharsets.UTF_8);
    unescapeAndApply(s, 
        b -> partialToken.write(b),
        (b, i, e) -> {
          if (b != '%') {
            partialToken.write(b);
          } else {
            if (++i >= e) {
              err("% must be followed by a value specifier, or use %% for a literal percent sign");
              return i-1; // malformed... trailing %
            }
            b = s[i];
            // %% --> literal %
            if (b == '%') {
              partialToken.write((byte)'%');
              return i;
            }
            // %c --> raw byte
            if (b == 'c' || b == 'C') {
              push(partialToken, new ByteToken());
              return i;
            }
            // get width
            int w = 0;
            while (within(b, '0', '9')) {
              w = w * 10 + (b - '0');
              if (++i >= e) {
                err("%-style value placeholder should end in one of: c, d, u, b, o, x");
                return i-1; // malformed... missing fmt char
              }
              b = s[i];
            }
            if (w <= 0) { // malformed... missing or zero width
              err("invalid value width (" + w + "), must be 1-32");
              w = 8; // whatever
            } else if (w > 32) { // malfomed... width too large
              err("invalid value width (" + w + "), must be 1-32");
              w = 32; 
            }
            if (b == 'd') push(partialToken, new SignedDecimalToken(w));
            else if (b == 'b') push(partialToken, new RadixToken(w, 2));
            else if (b == 'o') push(partialToken, new RadixToken(w, 8));
            else if (b == 'u') push(partialToken, new RadixToken(w, 10));
            else if (b == 'x') push(partialToken, new RadixToken(w, 16));
            else if (b == 'c') {
              if (w != 8)
                err("%" + w + "c is not valid, only %c (or, equivalently, %8c) is allowed");
              push(partialToken, new ByteToken());
            } else {
              if (0x20 <= b && b <= 0x7E)
                err("%" + w + (char)b + " is not valid, value placeholder should end in one of: c, d, u, b, o, x");
              else
                err("%" + w + String.format("<<%x>>", b) + " is not valid, value placeholder should end in one of: c, d, u, b, o, x");
              push(partialToken, new SignedDecimalToken(w)); // malformed... bad fmt char
            }
          }
          return i;
        });
    if (partialToken.size() > 0) {
      tokens.add(new StaticToken(partialToken.toByteArray()));
      partialToken.reset();
    }
  }


  private void convertDelims() {
    if (del.isEmpty()) {
      delims = null;
      return;
    }
    String d = del;
    // Remove braces first, then convert to bytes, then unescape.
    // Note: the braces could be removed after converting to bytes
    // but this shouldn't matter since the braces are 7-bit clean.
    // We do NOT allow the user to use escapes for the braces, that
    // just seems convoluted.
    int n = d.length();
    if (n >= 2 && d.charAt(0) == '[' && d.charAt(n-1) == ']')
      d = d.substring(1, n-1);
    else
      err("delimiter string should begin with '[' and end with ']'");
    
    delims = unescape(d.getBytes(StandardCharsets.UTF_8));
    if (delims.length == 0) {
      // Not possible? Or maybe delim is "[<utf-weirdness>]" ?
      delims = null;
    } else if (delims.length > 1) {
      // eliminate duplicates (but don't sort, keep user order)
      int uniq = 1;
      for (int i = 1; i < delims.length; i++) {
        if (!contained(delims[i], delims, uniq))
          delims[uniq++] = delims[i];
      }
      if (uniq != delims.length) {
        // could perhaps warn here?
        byte[] b = new byte[uniq];
        for (int i = 0; i < uniq; i++)
          b[i] = delims[i];
        delims = b;
      }
    }
  }

  private void convertWidths() {
    ArrayList<Integer> widths = new ArrayList<>();
    for (Token t : tokens) {
      if (t.width > 0)
        widths.add(t.width);
    }
    if (widths.isEmpty()) {
      // fmt must not have contained any value placeholders...
      // That's an error, but let's still default to something,
      // to keep the worker thread happy.
      tokens.add(new ByteToken());
      widths.add(8);
      err("format string should describe values to expect, like %32d, %16x, etc.");
    }
    this.widths = widths.stream().mapToInt(v->v).toArray();
  }

  private void err(String msg) {
    valid = false;
    if (errmsg == null)
      errmsg = msg;
  }

  public static class DataBuffer {
    public int cap;
    public byte[] buf; // circular
    public int pos;
    public int len;
    public boolean valueOutOfRange, unmatched;

    public DataBuffer(byte[] data) {
      buf = data;
      cap = data.length;
    }

    public DataBuffer(int cap) {
      this.cap = cap;
      this.buf = new byte[cap];
    }

    public Value[] parseRecord(SerialInputFormat format,
        int pos, int len) {
      this.pos = pos;
      this.len = len;
      valueOutOfRange = false;
      unmatched = false;
      int n = format.numValues();
      Value[] out = new Value[n];
      ArrayList<Token> tokens = format.getTokens();
      int i = 0;
      for (Token t : tokens) {
        Value v = t.consume(this);
        if (unmatched)
          return null;
        else if (v != null)
          out[i++] = v;
      }
      return out;
    }
  }

  public static abstract class Token {
    public final int width;
    Token(int w) { width = w; }
    public abstract Value consume(DataBuffer buf);
  }

  public static class StaticToken extends Token {
    public final byte[] bytes;
    public StaticToken(byte[] b) {
      super(0);
      bytes = b;
    }
    public Value consume(DataBuffer b) {
      int n = bytes.length;
      for (int i = 0; i < n; i++) {
        if (b.len <= 0 || b.buf[b.pos] != bytes[i]) {
          b.unmatched = true;
          return null;
        }
        b.pos = (b.pos + 1) % b.cap; b.len--;
      }
      return null;
    }
  }

  public static class ByteToken extends Token {
    public ByteToken() { super(8); }
    public Value consume(DataBuffer b) {
      if (b.len <= 0) {
        b.unmatched = true;
        return null;
      } else {
        byte x = b.buf[b.pos];
        b.pos = (b.pos + 1) % b.cap; b.len--;
        return Value.createKnown(BitWidth.EIGHT, (int)x);
      }
    }
  }

  public static class SignedDecimalToken extends Token {
    final int maxDigits;
    public SignedDecimalToken(int w) {
      super(w);
      maxDigits = (int)Math.floor((w-1) * Math.log10(2.0)) + 1;
    }
    public Value consume(DataBuffer b) {
      boolean negative = false;
      long maxAbsVal;
      if (b.len > 0 && b.buf[b.pos] == '-') {
        b.pos = (b.pos + 1) % b.cap; b.len--;
        negative = true;
        maxAbsVal = (1L << (width - 1));
      } else if (b.len > 0 && b.buf[b.pos] == '+') {
        b.pos = (b.pos + 1) % b.cap; b.len--;
        maxAbsVal = (1L << (width - 1)) - 1;
      } else {
        maxAbsVal = (1L << (width - 1)) - 1;
      }
      int digits = 0;
      long x = 0;
      while (b.len > 0 && within(b.buf[b.pos], '0', '9')) {
        x = 10 * x + (b.buf[b.pos] - '0');
        b.pos = (b.pos + 1) % b.cap; b.len--;
        digits++;
        if (x > maxAbsVal)
          b.valueOutOfRange = true;
      }
      if (negative)
        x = -x;
      if (digits == 0) {
        b.unmatched = true;
        return null;
      } else {
        return Value.createKnown(BitWidth.create(width), (int)x);
      }
    }
  }

  public static class RadixToken extends Token {
    public final int radix, maxDigits;
    public RadixToken(int w, int r) {
      super(w);
      radix = r;
      if (radix == 16)
        maxDigits = (w + 3) / 4;
      else if (radix == 8)
        maxDigits = (w + 2) / 3;
      else if (radix == 2)
        maxDigits = w;
      else // unsigned decimal
        maxDigits = (int)Math.floor(w * Math.log10(2.0)) + 1;
    }
    public Value consume(DataBuffer b) {
      int digits = 0;
      long x = 0, maxAbsVal = (1L << width) - 1;
      while (b.len > 0 && isHex(b.buf[b.pos]) && fromHex(b.buf[b.pos]) < radix) {
        x = radix * x + fromHex(b.buf[b.pos]);
        b.pos = (b.pos + 1) % b.cap; b.len--;
        digits++;
        if (x > maxAbsVal)
          b.valueOutOfRange = true;
      }
      if (digits == 0) {
        b.unmatched = true;
        return null;
      } else {
        // note: this coerces out-of-range values back into range
        return Value.createKnown(BitWidth.create(width), (int)x);
      }
    }
  }

  private static boolean within(byte c, char s, char e) {
    return s <= c && c <= e;
  }

  private static boolean isHex(byte c) {
    return within(c, '0', '9') ||
        within(c, 'A', 'F') ||
        within(c, 'a', 'f');
  }

  private static int fromHex(byte c) {
    return within(c, '0', '9') ? (c - '0')
        : within(c, 'A', 'F') ? (c - 'A')
        : within(c, 'a', 'f') ? (c - 'a')
        : 0;
  }

  private static boolean contained(byte x, byte[] arr, int arrlen) {
    for (int i = 0; i < arrlen; i++)
      if (x == arr[i])
        return true;
    return false;
  }

  private byte[] unescape(byte[] s) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    unescapeAndApply(s, 
        b -> out.write(b),
        (b, i, e) -> { out.write(b); return i; });
    return out.toByteArray();
  }

}
