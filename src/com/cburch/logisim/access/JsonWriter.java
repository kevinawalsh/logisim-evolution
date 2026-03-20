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

package com.cburch.logisim.access;

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

class JsonWriter {

  private final String filename; // null for stdout
  private final PrintWriter out;

  private boolean needsComma = false;
  private int depth = 0; // caller must track nesting of objects and arrays

  int linecount = 0;
  int bytecount = 0;

  JsonWriter(String filename) throws IOException {
    OutputStream dest = (filename == null) ? System.out : new FileOutputStream(filename);
    CountingOutputStream cos = new CountingOutputStream(dest);
    this.out = new PrintWriter(new OutputStreamWriter(cos, StandardCharsets.UTF_8));
    this.filename = filename;
  }

  void close() throws IOException {
    out.flush();
    if (filename != null)
      out.close();
  }

  void beginObject() {
    if (depth > 0) commaNewlineIndent();
    out.print("{");
    depth++;
    needsComma = false;
  }

  void endObject() {
    depth--;
    out.println();
    linecount++;
    indent();
    out.print("}");
    if (depth == 0) {
      out.println();
      linecount++;
    }
    needsComma = true;
  }

  void keyArray(String k, List<String> vals) {
    keyArray(k, vals, v -> v);
  }

  <V> void keyArray(String k, List<V> vals, Function<V, String> xform) {
    key(k);
    out.print("[");
    needsComma = false;
    for (V v : vals) {
      if (needsComma) out.print(", ");
      else out.print(" ");
      out.print(toJsonString(xform.apply(v)));
      needsComma = true;
    }
    if (needsComma) out.print(" ");
    out.print("]");
    needsComma = true;
  }

  void beginArray() {
    out.print("[");
    depth++;
    needsComma = false;
  }

  void endArray() {
    depth--;
    if (needsComma) { out.println(); indent(); linecount++; }
    out.print("]");
    needsComma = true;
  }

  void key(String k) {
    commaNewlineIndent();
    out.print(toJsonString(k) + ": ");
    needsComma = false;
  }

  void keyValue(String k, Object v) {
    key(k);
    out.print(toJsonString(v.toString()));
    needsComma = true;
  }

  void keyValueAsPrimitive(String k, Object v) {
    key(k);
    out.print(toJsonPrimitive(v));
    needsComma = true;
  }

  private static String toJsonPrimitive(Object v) {
    String s = v.toString();
    if ((v instanceof Integer
          || v instanceof Boolean
          || s.equals("true")
          || s.equals("false")))
      return s;
    try {
      int i = Integer.parseInt(s);
      if (s.equals(""+i))
        return s;
    } catch (NumberFormatException ex) { }
    return toJsonString(s);
  }

  private void indent() {
    for (int i = 0; i < depth; i++)
      out.print("  ");
  }

  private void commaNewlineIndent() {
    if (needsComma) { out.println(","); needsComma = false; }
    else out.println();
    linecount++;
    indent();
  }

  private static String toJsonString(String s) {
    if (s == null) return "\"\"";
    return "\"" + s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t") + "\"";
  }

  private class CountingOutputStream extends FilterOutputStream {
    public CountingOutputStream(OutputStream out) {
      super(out);
    }

    @Override
    public void write(int b) throws IOException {
      out.write(b);
      bytecount++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      bytecount += len;
    }
  }

}
