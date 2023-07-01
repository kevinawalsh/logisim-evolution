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

package com.cburch.logisim.analyze.gui;
import static com.cburch.logisim.analyze.model.Strings.S;

import java.util.Date;
import java.io.File;
import java.io.PrintStream;
import java.io.IOException;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.util.FileChooser;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.VariableList;

class ExportTableButton extends JButton {
  private static final long serialVersionUID = 1L;

  private JFrame parent;
  private AnalyzerModel model;

  ExportTableButton(JFrame parent, AnalyzerModel model) {
    this.parent = parent;
    this.model = model;
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        doSave();
      }
    });
  }

  void localeChanged() {
    setText(S.get("exportTableButton"));
  }

  private static void center(PrintStream out, String s, int n) {
    int pad = n - s.length();
    if (pad <= 0) {
      out.printf("%s", s);
    } else {
      String left = (pad/2 > 0) ? "%" + (pad/2) + "s" : "%s";
      String right = (pad-pad/2) > 0 ? "%" + (pad-pad/2) + "s" : "%s";
      out.printf(left + "%s" + right, "", s, "");
    }
  }

  void doSave(File file) throws IOException {
    PrintStream out = new PrintStream(file);
    try {
      out.println("# Truth table");
      Circuit c = model.getCurrentCircuit();
      if (c != null)
        out.println("# Generated from circuit " + c.getName());
      out.println("# Exported on " + new Date());
      out.println();
      out.println("# Hints and Notes on Formatting:");
      out.println("# * You can edit this file then import it back into Logisim!");
      out.println("# * Anything after a '#' is a comment and will be ignored.");
      out.println("# * Blank lines and separator lines (e.g., ~~~~~~) are ignored.");
      out.println("# * Keep column names simple (no spaces, punctuation, etc.)");
      out.println("# * 'Name[N..0]' indicates an N+1 bit variable, whereas");
      out.println("#   'Name' by itself indicates a 1-bit variable.");
      out.println("# * You can use 'x' to indicate \"don't care\" for both");
      out.println("#   input and output bits.");
      out.println("# * You can use binary (e.g., '10100011xxxx') notation or");
      out.println("#   or hex (e.g., 'C3x'). Logisim will figure out which is which.");
      out.println();
      VariableList inputs = model.getInputs();
      VariableList outputs = model.getOutputs();
      int colwidth[] = new int[inputs.vars.size() + outputs.vars.size()];
      int i;
      i = 0;
      for (Var var : inputs.vars)
        colwidth[i++] = Math.max(var.toString().length(), var.width);
      for (Var var : outputs.vars)
        colwidth[i++] = Math.max(var.toString().length(), var.width);
      i = 0;
      for (Var var : inputs.vars) {
        center(out, var.toString(), colwidth[i++]);
        out.print(" ");
      }
      out.print("|");
      for (Var var : outputs.vars) {
        out.print(" ");
        center(out, var.toString(), colwidth[i++]);
      }
      out.println();
      for (i = 0; i < colwidth.length; i++) {
        for (int j = 0; j < colwidth[i]+1; j++)
          out.print("~");
      }
      out.println("~");
      TruthTable table = model.getTruthTable();
      int rows = table.getVisibleRowCount();
      for (int row = 0; row < rows; row++) {
        i = 0;
        int col;
        col = 0;
        for (Var var : inputs.vars) {
          String s = "";
          for (int b = var.width - 1; b >= 0; b--) {
            Entry val = table.getVisibleInputEntry(row, col++);
            s += val.toBitString();
          }
          center(out, s, colwidth[i++]);
          out.print(" ");
        }
        out.print("|");
        col = 0;
        for (Var var : outputs.vars) {
          String s = "";
          for (int b = var.width - 1; b >= 0; b--) {
            Entry val = table.getVisibleOutputEntry(row, col++);
            s += val.toBitString();
          }
          out.print(" ");
          center(out, s, colwidth[i++]);
        }
        out.println();
      }
    } finally {
      out.close();
    }
  }

  private File lastFile = null;
  void doSave() {
    if (lastFile == null) {
      Circuit c = model.getCurrentCircuit();
      if (c != null)
        lastFile = new File(c.getName() + ".txt");
      else
        lastFile = new File("truthtable.txt");
    }
    FileChooser chooser = FileChooser.createSelected(parent, lastFile);
    chooser.setTitle(S.get("saveButton"));
    chooser.addFilenameFilter(FILE_FILTER);
    chooser.addFilenameFilter(FileChooser.ACCEPT_ALL);
    if (chooser.showSaveDialog()) {
      File file = chooser.getSelectedFile();
      if (file.exists() && !file.canWrite()) {
        JOptionPane.showMessageDialog(parent,
            S.fmt("cantWriteMessage", file.getName()),
            S.get("saveErrorTitle"), JOptionPane.OK_OPTION);
        return;
      }
      try {
        doSave(file);
        lastFile = file;
      } catch (IOException e) {
        JOptionPane.showMessageDialog(parent,
            e.getMessage(),
            S.get("saveErrorTitle"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static final FileChooser.Filter FILE_FILTER =
      new FileChooser.Filter("Truth Table", "txt"); // FIXME: localize
}
