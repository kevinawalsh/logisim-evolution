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

package com.cburch.logisim.hdl;

import java.util.ArrayList;

import com.bfh.logisim.gui.FPGAReport;

// Represents a snippet of VHDL or Verilog Hdl code, and contains
// some helpers for programmatically generating such code.
public class Hdl extends ArrayList<String> {
  
  public enum Lang { VHDL, Verilog }
  public final Lang lang;

  public final boolean isVhdl, isVerilog;
  public final FPGAReport err;

  // HDL-specific formats                VHDL                      Verilog
  public final String assn;           // %s <= %s;                 assign %s = %s;
  public final String bitAssn;        // %s(%d) <= %s;             assign %s[%d] = %s;
  public final String rangeAssn;      // %s(%d downto %d) <= %s;   assign %s[%d:%d] = %s;
  public final String assnBit;        // %s <= %s(%d);             assign %s = %s[%d];
  public final String assnRange;      // %s <= %s(%d downto %d);   assign %s = %s[%d:%d];
  public final String bitAssnBit;     // %s(%d) <= %s(%d);         assign %s[%d] = %s[%d];
  public final String idx;            // (%d)                      [%d]
  public final String range;          // (%d downto %d)            [%d:%d]
  public final String zero;           // '0'                       1'b0
  public final String one;            // '1'                       1'b1
  public final String unconnected;    // open                      (emptystring)
  public final String not;            // not                       ~
  public final String allZeros;       // (others => '0')           0

  private int indent = 0;
  private String tab = "";
  private StringBuffer buf = new StringBuffer();
  private ArrayList<Integer> align = new ArrayList<>();

  public Hdl(String lang, FPGAReport err) {
    if (lang.equals("VHDL"))
      this.lang = Lang.VHDL;
    else if (lang.equals("Verilog"))
      this.lang = Lang.Verilog;
    else
      throw new IllegalArgumentException("Unrecognized HDL: " + lang);
    this.isVhdl = this.lang == Lang.VHDL;
    this.isVerilog = this.lang == Lang.Verilog;
    this.err = err;
    if (isVhdl) {
      assn = "%s \t<= %s;";
      bitAssn = "%s(%d) \t<= %s;";
      rangeAssn = "%s(%d downto %d) \t<= %s;";
      assnBit = "%s \t<= %s(%d);";
      assnRange = "%s \t<= %s(%d downto %d);";
      bitAssnBit = "%s(%d) \t<= %s(%d);";
      idx = "(%d)";
      range = "(%d downto %d)";
      zero = "'0'";
      one = "'1'";
      unconnected = "open";
      not = "not";
      allZeros = "(others => '0')";
    } else {
      assn = "assign %s \t= %s;";
      bitAssn = "assign %s[%d] \t= %s;";
      rangeAssn = "assign %s[%d:%d] \t= %s;";
      assnBit = "assign %s \t= %s[%d];";
      assnRange = "assign %s \t= %s[%d:%d];";
      bitAssnBit = "assign %s[%d] \t= %s[%d];";
      idx = "[%d]";
      range = "[%d:%d]";
      zero = "1'b0";
      one = "1'b1";
      unconnected = "";
      not = "~";
      allZeros = "0";
    }
  }

  // public Hdl(Lang lang) {
  //   this.lang = lang;
  //   this.isVhdl = lang == Lang.VHDL;
  //   this.isVerilog = lang == Lang.Verilog;
  //   this.err = null;
  // }

  public void indent() {
    indent += 2;
    tab = " ".repeat(indent);
  }

  public void dedent() {
    if (indent >= 2) {
      indent -= 2;
      tab = " ".repeat(indent);
    }
  }

  public void stmt() {
    align.clear();
    add("");
  }

  // Note: Embedded '\t' chars define alignment points but otherwise are removed.
  public void stmt(String fmt, Object ...args) {
    stmt(String.format(fmt, args).split("\\R", -1));
  }

  public void stmt(String[] lines) {
    align.clear();
    if (lines.length == 0)
      return;
    String line = lines[0];
    buf.setLength(0);
    buf.append(tab);
    int s = 0;
    int t = line.indexOf('\t', s);
    while (t >= 0) {
      align.add(t - align.size());
      buf.append(line.substring(s, t));
      s = t+1;
      t = line.indexOf('\t', s);
    }
    buf.append(line.substring(s));
    add(buf.toString());
    buf.setLength(0);
    cont(1, lines);
  }

  // Note: Embedded '\t' chars align to previously-defined alignment points but
  // otherwise are removed.
  public void cont(String fmt, Object ...args) {
    cont(String.format(fmt, args).split("\\R", -1));
  }

  public void cont(String[] lines) {
    cont(0, lines);
  }

  private void cont(int idx, String[] lines) {
    for (int j = idx; j < lines.length; j++) {
      String line = lines[j];
      buf.setLength(0);
      buf.append(tab);
      int s = 0;
      int t = line.indexOf('\t', s);
      int i = 0;
      int p = 0;
      while (t >= 0) {
        buf.append(line.substring(s, t));
        p += t-s;
        if (i < align.size()) {
          int a = align.get(i++);
          while (p < a) {
            buf.append(' ');
            p++;
          }
        }
        s = t+1;
        t = line.indexOf('\t', s);
      }
      buf.append(line.substring(s));
      add(buf.toString());
      buf.setLength(0);
    }
  }

  public void assign(String name, String val) {
    stmt(assn, name, val);
  }

  public void assign(String name, int nameIdx, String val) {
    stmt(bitAssn, name, nameIdx, val);
  }

  public void assign(String name, int nameIdxHi, int nameIdxLo, String val) {
    if (nameIdxHi == nameIdxLo)
      stmt(bitAssn, name, nameIdxLo, val);
    else
      stmt(rangeAssn, name, nameIdxHi, nameIdxLo, val);
  }

  public void assign(String name, String val, int valIdx) {
    stmt(assnBit, name, val, valIdx);
  }

  public void assign(String name, String val, int valIdxHi, int valIdxLo) {
    if (valIdxHi == valIdxLo)
      stmt(assnBit, name, val, valIdxLo);
    else
      stmt(assnRange, name, val, valIdxHi, valIdxLo);
  }

  public void assign(String name, int nameIdx, String val, int valIdx) {
    stmt(bitAssnBit, name, nameIdx, val, valIdx);
  }

  // todo: consider enforcing max line length (e.g. 80 chars)?
  public void comment(String fmt, Object ...args) {
    comment(String.format(fmt, args).split("\\R", -1));
  }

  public void comment(String[] lines) {
    if (isVhdl)
      for (String line : lines)
        add(tab + "-- " + line);
    else
      for (String line : lines)
        add(tab + "// " + line);
  }

  // For VHDL, returns '0', '1', "00101101", "1011011", etc.
  // For Verilog, returns 1'b0, 1'b1, 5'b01011, 3'b110, etc.
	public String literal(long value, int width) {
    if (width == 0)
      throw new IllegalArgumentException("Can't create HDL literal with zero width");
    if (width == 1)
      return (value & 1) == 0 ? zero : one;
		StringBuffer out = new StringBuffer();
    if (isVhdl) {
      out.append(width == 1 ? '\'' : '\"');
      for (int i = width - 1; i >= 0; i--)
        out.append(((value >>> i) & 1) == 0 ? '0' : '1');
      out.append(width == 1 ? '\'' : '\"');
    } else {
      out.append(width);
      out.append("'b");
      for (int i = width - 1; i >= 0; i--)
        out.append(((value >>> i) & 1) == 0 ? '0' : '1');
    }
    return out.toString();
	}

  // Returns "(others => '1')", "(others => '0'), "~0", or "0".
  public String all(boolean value) {
    if (isVhdl)
      return String.format("(others => %s)", bit(value));
    else if (value)
      return "~0";
    else
      return "0";
  }
 
  // Same as literal(0, 1) or literal(1, 1).
  public String bit(boolean value) {
    return value ? one : zero;
  }

  // Same as literal(value, 1).
  public String bit(int value) {
    return (value & 1) == 0 ? zero : one;
  }

  // Similar to literal(0, width), but uses hex or decimal to be pretty.
  public String zeros(int width) { return fill(false, width); }

  // Similar to literal(1, width), but uses hex or decimal to be pretty.
  public String ones(int width) { return fill(true, width); }

  // Similar to literal(value?-1:0, width), but uses hex or decimal to be pretty.
  // For VHDL, returns X"FFF", or "111"&X"FFFF", etc.
  // For Verilog, returns 7'd-1, 7'd0, etc.
  public String fill(boolean value, int width) {
    if (width == 0)
      throw new IllegalArgumentException("Can't create HDL literal with zero width");
    // else if (width == 1)
    //   return value ? one : zero;
    else if (isVerilog && value)
      return width + "'d-1";
    else if (isVerilog)
      return width + "'d0";
    else if (width % 4 == 0)
      return "X\"" + (value?"F":"0").repeat(width/4) + "\"";
    else if (width > 12)
      return "\"" + (value?"1":"0").repeat(width%4) + "\" & X\"" + ("F".repeat(width/4)) + "\"";
    else 
      return "\"" + (value?"1":"0").repeat(width) + "\"";
  }

  // private static String sz(HDLGenerator.Generics params, String name) {
  //   Integer val = params == null ? null : params.getValue(name);
  //   if (val == null) {
  //     err.AddFatalError("Internal error: Signal reference to non-existant generic parameter ID=%d.", w);
  //     return "???";
  //   }
  //   return val.intValue();
  // }

  // Returns an appropriate type definition for a signal of fixed width (when
  // widthStr is an integer) or width defined by a generic parameter
  // (otherwise). All generics are assumed to be buses. Examples:
  //   w = "1"    VHDL: "std_logic"                         Verilog: ""
  //   w = "8"    VHDL: "std_logic_vector(7 downto 0)"      Verilog: "[7:0] "
  //   w = "N"    VHDL: "std_logic_vector(N-1 downto 0)"    Verilog: "[N-1:0] "
  public String typeForWidth(String w) {
    // Special case: when w has parens, like "(1)", we deliberately treat it as
    // a vector. See ToplevelHDLGenerator.addWireVector().
    try {
      return typeForWidth(Integer.parseInt(w));
    } catch (NumberFormatException ex) {
      return isVhdl ? "std_logic_vector("+w+"-1 downto 0)" : "["+w+"-1:0] ";
    }
  }
  public String typeForWidth(int n) {
    if (n == 1)
      return isVhdl ? "std_logic" : "";
    // else if (w == 0) // Q: can this happen?
    //   return isVhdl ? "std_logic_vector(0 downto 0)" : "[0:0]";
    else
      return isVhdl ? "std_logic_vector("+(n-1)+" downto 0)" : "["+(n-1)+":0] ";
  }

  // Map is used for port mappings and generic parameter mapping.
  public static class Map extends Hdl {
    // HDL-specific formats              VHDL                        Verilog
    public final String map;          // %s => %s                    .%s(%s)
    public final String map0;         // %s(0) => %s                 .%s(%s)
    // public final String mapBit;       // %s => %s(%d)                .%s(%s(%d))
    // public final String map0Bit;      // %s(0) => %s(%d)             .%s(%s(%d))

    public Map(String lang, FPGAReport err) {
      super(lang, err);
      if (isVhdl) {
        map = "%s => %s";
        map0 = "%s(0) => %s";
//        mapBit = "%s => %s(%d)";
//        map0Bit = "%s(0) => %s(%d)";
      } else {
        map = ".%s(%s)";
        map0 = ".%s(%s)";
//        mapBit = ".%s(%s(%d))";
//        map0Bit = ".%s(%s(%d))";
      }
    }

    public void assign(String name, String val) { throw new IllegalArgumentException(); }
    public void assign(String name, int nameIdx, String val) { throw new IllegalArgumentException(); }
    public void assign(String name, String val, int valIdx) { throw new IllegalArgumentException(); }
    public void assign(String name, int nameIdx, String val, int valIdx) { throw new IllegalArgumentException(); }

    // same vhdl types: vector => vector
    // same vhdl types: signal => signal
    public void add(String name, String val) {
      stmt(map, name, val);
    }

    // same vhdl types: vector => vector(7 downto 0)
    public void add(String name, String val, int end, int start) {
      stmt(map, name, String.format(val+range, end, start));
    }

    // mismatched vhdl types: vector(0) => signal
    public void add0(String name, String val) {
      stmt(map0, name, val);
    }

    // signal => vector(i)
    public void add(String name, String val, int valIdx) {
      stmt(map, name, String.format(val+idx, valIdx));
    }

    // VHDL:
    //   vector(n-1) => vals[n-1]
    //   ...
    //   vector(1) => vals[1]
    //   vector(1) => vals[0]
    // Verilog:
    //   .vector({vals[n-1], ..., vals[1], vals[0]})
    public void addVector(String name, String[] vals) {
      if (isVhdl) {
        for (int i = vals.length-1; i >= 0; i--)
            stmt(map, String.format(name+idx, i), vals[i]);
      } else {
        ArrayList<String> v = new ArrayList<>();
        for (int i = vals.length-1; i >= 0; i--)
          v.add(vals[i]);
        stmt(map, name, "{"+String.join(", ", v)+"}");
      }
    }

  }

}
