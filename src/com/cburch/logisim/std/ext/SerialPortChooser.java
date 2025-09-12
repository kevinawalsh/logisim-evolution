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

import com.fazecast.jSerialComm.SerialPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

import com.cburch.logisim.Main;
import com.cburch.logisim.util.JInputDialog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
// import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public class SerialPortChooser extends JDialog implements JInputDialog<String> {

  private String chosen; // null if cancelled
  private boolean selectFirstKnownPort;
  private String cancelValue;

  private final JTable table;
  private final DefaultTableModel model;
  private final JTextField pathField;
  private final JButton okBtn, cancelBtn;
  private final Timer refreshTimer;

  public SerialPortChooser(Window parent, String initialValue) {
    super((Frame)parent, S.get("serialPortDialogTitle"), true);
    
    cancelValue = initialValue;

    ArrayList<String[]> rows = enumeratePorts();
   
    if (initialValue == null || initialValue.trim().isEmpty()) {
      if (rows.size() > 0) {
        initialValue = rows.get(0)[0];
      } else {
        selectFirstKnownPort = true;
        if (Main.MacOS) initialValue = "/dev/tty.usbserial-10";
        else if (Main.Linux) initialValue = "/dev/ttyUSB0";
        else if (Main.MSWindows) initialValue = "COM3";
        else initialValue = "<unknown>";
      }
    }

    setLayout(new BorderLayout(8,8));

    String[] cols = new String[]{
      S.get("serialPortDialogPath"), S.get("serialPortDialogDescription") };
    model = new DefaultTableModel(cols , 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    table = new JTable(model);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // allow horizontal scrolling
    table.getColumnModel().getColumn(0).setPreferredWidth(170);
    table.getColumnModel().getColumn(1).setPreferredWidth(270);
    table.setPreferredScrollableViewportSize(new Dimension(440, 240));
    table.setFillsViewportHeight(true);
    JScrollPane sp = new JScrollPane(table,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    sp.setPreferredSize(new Dimension(440, 240));
    // sp.setMinimumSize(new Dimension(440, 240));

    JPanel centerPane = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    centerPane.setBorder(new EmptyBorder(12, 12, 12, 12));
    centerPane.add(sp);
    add(centerPane, BorderLayout.CENTER);

    JPanel south = new JPanel(new BorderLayout(0, 8));
    south.setBorder(new EmptyBorder(12, 12, 12, 12));
    
    pathField = new JTextField(initialValue, 32);

    JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    inputRow.setBorder(new EmptyBorder(8, 12, 8, 12));
    pathField.setColumns(32);
    inputRow.add(new JLabel(S.get("serialPortDialogPath") + ":  "));
    inputRow.add(pathField);
    south.add(inputRow, BorderLayout.CENTER);

    // Buttons
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    okBtn = new JButton("OK");
    cancelBtn = new JButton("Cancel");
    buttons.add(okBtn);
    buttons.add(cancelBtn);
    south.add(buttons, BorderLayout.SOUTH);
    add(south, BorderLayout.SOUTH);

    // sync: table -> field
    table.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        int r = table.getSelectedRow();
        if (r >= 0) {
          selectFirstKnownPort = false;
          String path = (String) model.getValueAt(r, 0);
          if (!Objects.equals(path, pathField.getText()))
            pathField.setText(path);
        }
      }
    });

    // sync: field -> table
    pathField.getDocument().addDocumentListener(new DocumentListener() {
      void sync() {
        selectFirstKnownPort = false;
        String txt = pathField.getText().trim();
        int row = indexOfPath(txt);
        if (row >= 0) table.setRowSelectionInterval(row, row);
        else table.clearSelection();
      }
      @Override public void insertUpdate(DocumentEvent e) { sync(); }
      @Override public void removeUpdate(DocumentEvent e) { sync(); }
      @Override public void changedUpdate(DocumentEvent e) { sync(); }
    });

    okBtn.addActionListener(e -> { chosen = pathField.getText().trim(); dispose(); });
    cancelBtn.addActionListener(e -> { chosen = cancelValue; dispose(); });
    getRootPane().setDefaultButton(okBtn);

    // Periodic refresh
    refreshTimer = new Timer(2000, e -> refreshPorts());
    refreshTimer.setInitialDelay(0);

    addWindowListener(new WindowAdapter() {
      @Override public void windowOpened(WindowEvent e) { refreshTimer.start(); }
      @Override public void windowClosing(WindowEvent e) { refreshTimer.stop(); }
      @Override public void windowClosed(WindowEvent e) { refreshTimer.stop(); }
    });

    setPreferredSize(new Dimension(640, 360));
    pack();
    setLocationRelativeTo(parent);
  }
  
  @Override
  public void setValue(String path) {
    if (path != null || !path.trim().isEmpty()) {
      selectFirstKnownPort = false;
    } else {
      path = "";
    }
    if (!Objects.equals(path, pathField.getText())) {
      pathField.setText(path);
      int row = indexOfPath(path);
      if (row >= 0) table.setRowSelectionInterval(row, row);
      else table.clearSelection();
    }
  }

  @Override
  public String getValue() {
    return chosen;
  }

  private ArrayList<String[]> enumeratePorts() {
    SerialPort[] ports = SerialPort.getCommPorts();
    ArrayList<String[]> rows = new ArrayList<>(ports.length);
    for (SerialPort p : ports) {
      String path = p.getSystemPortPath();      // e.g., "COM4", "/dev/ttyUSB0"
      String desc = p.getDescriptivePortName(); // friendly name
      rows.add(new String[]{ path, desc });
    }
    rows.sort(Comparator.comparing(a -> a[0], String.CASE_INSENSITIVE_ORDER));
    return rows;
  }

  private void refreshPorts() {
    ArrayList<String[]> rows = enumeratePorts();
    if (!modelMatches(rows)) {
      String keep;
      if (selectFirstKnownPort && rows.size() > 0) {
        selectFirstKnownPort = false;
        keep = rows.get(0)[0];
        pathField.setText(keep);
      } else {
        keep = pathField.getText().trim();
      }
      model.setRowCount(0);
      for (String[] r : rows)
        model.addRow(r);
      int idx = indexOfPath(keep);
      if (idx >= 0)
        table.setRowSelectionInterval(idx, idx);
      else
        table.clearSelection();
    }
  }

  private boolean modelMatches(ArrayList<String[]> rows) {
    if (model.getRowCount() != rows.size()) return false;
    for (int i = 0; i < rows.size(); i++) {
      if (!Objects.equals(model.getValueAt(i, 0), rows.get(i)[0])) return false;
      if (!Objects.equals(model.getValueAt(i, 1), rows.get(i)[1])) return false;
    }
    return true;
  }

  private int indexOfPath(String path) {
    if (path == null || path.isEmpty())
      return -1;
    for (int i = 0; i < model.getRowCount(); i++) {
      if (path.equalsIgnoreCase((String) model.getValueAt(i, 0)))
        return i;
    }
    return -1;
  }

  // public static void main(String[] args) {
  //   SwingUtilities.invokeLater(() -> {
  //     SerialPortChooser dlg = new SerialPortChooser(SwingUtilities.getWindowAncestor(null), "");
  //     dlg.setVisible(true);
  //     String sel = dlg.chosen;
  //     System.out.println("Selected: " + sel);
  //     System.exit(0);
  //   });
  // }

}

