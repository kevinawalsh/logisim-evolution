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

public class HttpInputFormat extends SerialInputFormat {

  // Everything here is basically the same as SerialInputFormat, for now,
  // except there is no delimiter, entire Http responses are parsed at once.
  // And "raw mode" doesn't really make sense, but for now it will just
  // return the first byte of each response.
  //
  // Also, we could probably work with actual strings, decoded from e.g. Http
  // utf8 response data. But for now, we continue to use bytes, as our test
  // cases all use 7-bit clean ascii anyway.

  public HttpInputFormat() { // raw mode
    this(""); 
  }

  public HttpInputFormat(HttpInputFormat other) {
    this(other.getFormatString()); 
  }

  // Note: both del and fmt may contain escapes, and
  // they are unescaped before processing.
  public HttpInputFormat(String fmt) {
    super("", fmt);
  }

  public Value[] parseResponse(String resp) {
    byte[] utf = resp.getBytes(StandardCharsets.UTF_8);
    DataBuffer buf = new DataBuffer(utf);
    Value[] vals = buf.parseRecord(this, 0, utf.length);
    return vals;
  }

}
