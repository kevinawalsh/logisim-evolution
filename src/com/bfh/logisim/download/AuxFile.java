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

package com.bfh.logisim.download;

import java.io.File;
import java.util.ArrayList;

import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;

// Represents a generated non-Hdl file needed as part of a toolchain project,
// e.g. a script or constraint file.
public class AuxFile extends ArrayList<String> {
  
  public final FPGAReport err;
  public final File dest;

  private int indent = 0;
  private String tab = "";
  private StringBuffer buf = new StringBuffer();
  private ArrayList<Integer> align = new ArrayList<>();

  public AuxFile(String dir, String filename, FPGAReport err) {
    this.err = err;
    this.dest  = FileWriter.GetFilePointer(dir, filename, err);
  }

  public boolean save() {
    return dest != null && FileWriter.WriteContents(dest, this, err);
  }

  public boolean saveAs(String dir, String filename) {
    File copy = FileWriter.GetFilePointer(dir, filename, err);
    return copy != null && FileWriter.WriteContents(copy, this, err);
  }

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

}
