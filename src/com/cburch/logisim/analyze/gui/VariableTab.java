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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import org.jdesktop.xswingx.BuddySupport;

import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.analyze.model.VariableList;
import com.cburch.logisim.analyze.model.VariableListEvent;
import com.cburch.logisim.analyze.model.VariableListListener;
import com.cburch.logisim.analyze.model.ParserException;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.LogisimMenuItem;
import com.cburch.logisim.util.MouseListenerUtil;

class VariableTab extends AnalyzerTab {

  private VariableList inputs, outputs;
  private JTable inputsTable, outputsTable;
  private JLabel error = new JLabel(" ");
  private JLabel inputsLabel, outputsLabel;
  private JTable focus;

  private JTable ioTable(VariableList data, LogisimMenuBar menubar) {
    final TableCellEditor ed1 = new SingleClickVarEditor(data);
    final TableCellEditor ed2 = new DoubleClickVarEditor(data);
    JTable table = new JTable(1, 1) {
      public TableCellEditor getCellEditor(int row, int column) {
        return (row == getRowCount() - 1 ? ed1 : ed2);
      }
    };
    table.getTableHeader().setUI(null);
    table.setModel(new VariableTableModel(data, table));
    table.setDefaultRenderer(Var.class, new VarRenderer());
    table.setRowHeight(30);
    table.setShowGrid(false);
    table.setDragEnabled(true);
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setColumnSelectionAllowed(false);
    table.setRowSelectionAllowed(true);
    TransferHandler ccp = new VarTransferHandler(table, data);
    table.setTransferHandler(ccp);
    table.setDropMode(DropMode.INSERT_ROWS);

    InputMap inputMap = table.getInputMap();
    for (LogisimMenuItem item: LogisimMenuBar.EDIT_ITEMS) {
      KeyStroke accel = menubar.getAccelerator(item);
      inputMap.put(accel, item);
    }

    ActionMap actionMap = table.getActionMap();

    actionMap.put(LogisimMenuBar.CUT, ccp.getCutAction());
    actionMap.put(LogisimMenuBar.COPY, ccp.getCopyAction());
    actionMap.put(LogisimMenuBar.PASTE, ccp.getPasteAction());
    actionMap.put(LogisimMenuBar.DELETE, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        int[] idxs = table.getSelectedRows();
        if (idxs.length == 0)
          return;
        int newIdx = table.getSelectionModel().getMaxSelectionIndex()+1;
        Arrays.sort(idxs);
        for (int i = idxs.length-1; i >= 0; i--) {
          int idx = idxs[i];
          if (idx < 0 || idx >= data.vars.size())
            continue;
          data.remove(data.vars.get(idx));
          newIdx--;
        }
        if (newIdx >= data.vars.size())
          newIdx = data.vars.size() - 1;
        if (newIdx >= 0)
          table.setRowSelectionInterval(newIdx, newIdx);
      }
    });
    actionMap.put(LogisimMenuBar.SELECT_ALL, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (data.vars.size() > 0)
          table.setRowSelectionInterval(0, data.vars.size()-1);
      }
    });
    actionMap.put(LogisimMenuBar.RAISE, new Mover(-1, table, data));
    actionMap.put(LogisimMenuBar.LOWER, new Mover(+1, table, data));
    actionMap.put(LogisimMenuBar.RAISE_TOP, new Mover(Integer.MIN_VALUE, table, data));
    actionMap.put(LogisimMenuBar.LOWER_BOTTOM, new Mover(Integer.MAX_VALUE, table, data));

    return table;
  }

  private class Mover extends AbstractAction {
    int delta;
    JTable table;
    VariableList data;
    Mover(int d, JTable t, VariableList v) {
      delta = d; // +N for lower, -N for raise
      table = t;
      data = v;
    }
    public void actionPerformed(ActionEvent e) {
      int[] idxs = table.getSelectedRows();
      int n = idxs.length;
      if (n == 0)
        return;
      Arrays.sort(idxs);
      int a = (delta > 0 ? n-1 : 0);
      int b = (delta > 0 ? -1 : n);
      int d = (delta > 0 ? +1 : -1);
      for (int i = a; i != b; i -= d) {
        int idx = idxs[i];
        int count = 0;
        while (count != delta
            && idx >= 0 && idx < data.vars.size()
            && idx+d >= 0 && idx+d < data.vars.size()
            && (i == a || idxs[i+d] != idx+d)) {
          count += d;
          data.move(data.vars.get(idx), d);
          idx += d;
        }
        idxs[i] = idx;
      }
      table.clearSelection();
      for (int i = 0; i < idxs.length; i++)
        table.addRowSelectionInterval(idxs[i], idxs[i]);
    }
  }

  private JScrollPane wrap(JTable table) {

    JScrollPane scroll = new JScrollPane(table,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(60, 100));

    scroll.addMouseListener(MouseListenerUtil.clickHandler(e -> {
      table.changeSelection(table.getRowCount()-1, 0, false, false);
      table.grabFocus();
    }));
    scroll.setTransferHandler(table.getTransferHandler());

    table.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        if (e.isTemporary()) return;
        focus = table;
        editHandler.computeEnabled();
      }
      public void focusLost(FocusEvent e) {
        if (e.isTemporary()) return;
        table.clearSelection();
        if (focus == table) {
          focus = null;
        }
      }
    });

    ListSelectionModel sel = table.getSelectionModel();
    sel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        editHandler.computeEnabled();
      }
    });

    return scroll;
  }

  VariableTab(VariableList inputs, VariableList outputs, LogisimMenuBar menubar) {
    this.inputs = inputs;
    this.outputs = outputs;

    inputsTable = ioTable(inputs, menubar);
    outputsTable = ioTable(outputs, menubar);
    JScrollPane inputsTablePane = wrap(inputsTable);
    JScrollPane outputsTablePane = wrap(outputsTable);

    GridBagLayout gb = new GridBagLayout();
    GridBagConstraints gc = new GridBagConstraints();
    setLayout(gb);
    
    gc.insets = new Insets(10, 10, 2, 10);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = gc.weighty = 0.0;

    inputsLabel = new JLabel("Input Variables");
    gc.gridx = 0;
    gc.gridy = 0;
    gb.setConstraints(inputsLabel, gc);
    add(inputsLabel);

    outputsLabel = new JLabel("Output Variables");
    gc.gridx = 1;
    gc.gridy = 0;
    gb.setConstraints(outputsLabel, gc);
    add(outputsLabel);

    gc.insets = new Insets(2, 10, 3, 10);
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = gc.weighty = 1.0;

    gc.gridx = 0;
    gc.gridy = 1;
    gb.setConstraints(inputsTablePane, gc);
    add(inputsTablePane);

    gc.gridx = 1;
    gc.gridy = 1;
    gb.setConstraints(outputsTablePane, gc);
    add(outputsTablePane);
    
    gc.insets = new Insets(3, 10, 10, 10);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = gc.weighty = 0.0;

    gc.gridwidth = 2;
    gc.gridx = 0;
    gc.gridy = 2;
    gb.setConstraints(error, gc);
    add(error);
    error.setForeground(Color.RED);

 
    this.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        if (!outputs.vars.isEmpty()) {
          outputsTable.changeSelection(0, 0, false, false);
          outputsTable.requestFocusInWindow();
        } else if (!inputs.vars.isEmpty()) {
          inputsTable.changeSelection(0, 0, false, false);
          inputsTable.requestFocusInWindow();
        }
      }
    });

    editHandler.computeEnabled();
  }

  @Override
  void localeChanged() {
    inputsLabel.setText(S.get("inputVariables"));
    outputsLabel.setText(S.get("outputVariables"));
  }

  @Override
  void updateTab() {
    VariableTableModel inputModel = (VariableTableModel)inputsTable.getModel();
    inputModel.update();
    VariableTableModel outputModel = (VariableTableModel)outputsTable.getModel();
    outputModel.update();
  }

  private static final int OK = 0;
  private static final int EMPTY = 1;
  private static final int UNCHANGED = 2;
  private static final int RESIZED = 3;
  private static final int BAD_NAME = 4;
  private static final int DUP_NAME = 5;
  private static final int TOO_WIDE = 6;

  private int validateInput(VariableList data, Var oldVar, String text, int w) {
    int err = OK;
    if (text.length() == 0) {
      err = EMPTY;
    } else if (!Character.isJavaIdentifierStart(text.charAt(0))) {
      error.setText(S.get("variableStartError"));
      err = BAD_NAME;
    } else {
      for (int i = 1; i < text.length() && err == OK; i++) {
        char c = text.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          error.setText(S.fmt("variablePartError", "" + c));
          err = BAD_NAME;
        }
      }
    }
    if (err == OK && oldVar != null) {
      if (oldVar.name.equals(text) && oldVar.width == w)
        err = UNCHANGED;
      else if (oldVar.name.equals(text))
        err = RESIZED;
    }
    if (err == OK) {
      for (int i = 0, n = data.vars.size(); i < n && err == OK; i++) {
        Var other = data.vars.get(i);
        if (other != oldVar && text.equals(other.name)) {
          error.setText(S.get("variableDuplicateError"));
          err = DUP_NAME;
        }
      }
    }
    if (err == OK || err == EMPTY) {
      if (data.bits.size() + w > data.getMaximumSize()) {
        error.setText(S.fmt("variableMaximumError", "" + data.getMaximumSize()));
        err = TOO_WIDE;
      } else {
        error.setText(" ");
      }
    }
    return err;
  }

  @Override
  EditHandler getEditHandler() {
    return editHandler;
  }

  EditHandler editHandler = new EditHandler() {
    @Override
    public void computeEnabled() {
      boolean canRaise = false, canLower = false;
      if (focus != null && !focus.isEditing()
          && focus.getRowCount() > 1 && focus.getSelectedRowCount() > 0) {
        int a = focus.getSelectionModel().getMinSelectionIndex();
        int b = focus.getSelectionModel().getMaxSelectionIndex();
        int n = focus.getSelectedRowCount();
        if (b - a + 1 > n) { // non-contiguous selection
          canRaise = canLower = true;
        } else {
          canRaise = !focus.isRowSelected(0);
          canLower = !focus.isRowSelected(focus.getRowCount()-2);
        }
      }
      setEnabled(LogisimMenuBar.CUT, true);
      setEnabled(LogisimMenuBar.COPY, true);
      setEnabled(LogisimMenuBar.PASTE, true);
      setEnabled(LogisimMenuBar.DELETE, true);
      setEnabled(LogisimMenuBar.DUPLICATE, false);
      setEnabled(LogisimMenuBar.SELECT_ALL, focus != null);
      setEnabled(LogisimMenuBar.RAISE, canRaise);
      setEnabled(LogisimMenuBar.LOWER, canLower);
      setEnabled(LogisimMenuBar.RAISE_TOP, canRaise);
      setEnabled(LogisimMenuBar.LOWER_BOTTOM, canLower);
      setEnabled(LogisimMenuBar.ADD_CONTROL, false);
      setEnabled(LogisimMenuBar.REMOVE_CONTROL, false);
    }
    @Override
    public void actionPerformed(ActionEvent e) {
      Object action = e.getSource();
      if (focus != null) focus.getActionMap().get(action).actionPerformed(null);
    }
  };

  private static class VariableTableModel
    extends AbstractTableModel implements VariableListListener {

    private JTable table;
    private VariableList list;
    private Var[] listCopy;
    private Var empty = new Var("", 1);

    public VariableTableModel(VariableList list, JTable table) {
      this.list = list;
      this.table = table;
      updateCopy();
      list.addVariableListListener(this);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    @Override
    public void setValueAt(Object o, int row, int column) {
      Var newVar = (Var)o;
      Var oldVar = (Var)getValueAt(row, column);
      if (newVar == null || newVar.name.equals("") || newVar.equals(oldVar))
        return;
      if (row == listCopy.length) {
        list.add(newVar);
        table.changeSelection(row+1, column, false, false);
        table.grabFocus();
      } else {
        list.replace(oldVar, newVar);
        table.changeSelection(row, column, false, false);
        table.grabFocus();
      }
    }

    @Override
    public Object getValueAt(int row, int col) {
      if (row == listCopy.length)
        return empty;
      else if (row >= 0 && row < listCopy.length)
        return listCopy[row];
      else
        return null;
    }
      
    @Override
    public int getColumnCount() { return 1; };
    @Override
    public String getColumnName(int column) { return ""; }
    @Override
    public Class<?> getColumnClass(int columnIndex) { return Var.class; }
    @Override
    public int getRowCount() { return listCopy.length + 1; }

    @Override
    public void listChanged(VariableListEvent event) {
      updateCopy();
      Integer idx = event.getIndex();
      switch (event.getType()) {
      case VariableListEvent.ALL_REPLACED:
        fireTableDataChanged();
        return;
      case VariableListEvent.ADD:
        fireTableRowsInserted(idx, idx);
        return;
      case VariableListEvent.REMOVE:
        fireTableRowsDeleted(idx, idx);
        return;
      case VariableListEvent.MOVE:
        fireTableRowsUpdated(0, listCopy.length-1);
        return;
      case VariableListEvent.REPLACE:
        fireTableRowsUpdated(idx, idx);
        return;
      }
    }

    private void update() {
      updateCopy();
      fireTableDataChanged();
    }

    private void updateCopy() {
      listCopy = list.vars.toArray(new Var[list.vars.size()]);
    }
  }
  
  public static class VarRenderer extends DefaultTableCellRenderer {
    Border border = BorderFactory.createEmptyBorder(10, 10, 10, 10);
    Font plain, italic;
    public VarRenderer() {
      setBorder(border);
      plain = getFont();
      italic = plain.deriveFont(Font.ITALIC);
    }
    @Override
    public Component getTableCellRendererComponent(JTable table,
        Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      boolean empty = value.toString().equals("");
      if (empty)
        value = "Click to add a new variable";
      JComponent c = (JComponent)super.getTableCellRendererComponent(table,
          value, isSelected, hasFocus, row, column);
      c.setFont(empty ? italic : plain);
      return c;
    }
  }

  class BitWidthRenderer extends DefaultListCellRenderer {
    public BitWidthRenderer() {
    }
    @Override
    public Component getListCellRendererComponent(JList<?> list,
        Object w, int index, boolean isSelected, boolean cellHasFocus) {
      String s = ((Integer)w) == 1 ? ("1 bit") : (w + " bits");
      return super.getListCellRendererComponent(list, s, index, isSelected, cellHasFocus);
    }
  }

  public class SingleClickVarEditor extends AbstractCellEditor implements TableCellEditor {
    JTextField field = new JTextField();
    JComboBox<Integer> width;
    Var editing;
    VariableList data;
    public SingleClickVarEditor(VariableList data) {
      field.setBorder(BorderFactory.createCompoundBorder(
            field.getBorder(),
            BorderFactory.createEmptyBorder(1, 3, 1, 3)));
      this.data = data;
      int maxwidth = data.getMaximumSize();
      Integer widths[] = new Integer[maxwidth > 32 ? 32 : maxwidth];
      for (int i = 0; i < widths.length; i++)
        widths[i] = i+1;
      width = new JComboBox<>(widths);
      width.setFocusable(false);
      width.setRenderer(new BitWidthRenderer());
      width.setMaximumRowCount(widths.length);
      BuddySupport.addRight(width, field);
    }

    @Override
    public Object getCellEditorValue() {
      String name = field.getText().trim();
      int w = (Integer)width.getSelectedItem();
      Var v = new Var(name, w);
      return v;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table,
        Object value, boolean isSelected, int row, int column) {
      editing = (Var)value;
      field.setText(editing.name);
      width.setSelectedItem(editing.width);
      return field;
    }

    @Override
    public boolean stopCellEditing() {
      if (ok()) {
        super.stopCellEditing();
        return true;
      } else {
        return false;
      }
    }
    boolean ok() {
      Var oldVar = editing;
      editing = null;
      String name = field.getText().trim();
      int w = (Integer)width.getSelectedItem();
      if (oldVar == null || oldVar.name.equals("")) {
        // validate new name and width
        int err = validateInput(data, null, name, w);
        if (err == EMPTY)
          return true; // do nothing, empty Var will be ignored in setValueAt()
        if (err == BAD_NAME || err == DUP_NAME || err == TOO_WIDE)
          return false; // prevent loss of focus
        if (err == OK)
          return true; // new Var will be added in setValueAt()
      } else {
        // validate replacement name and width
        int err = validateInput(data, oldVar, name, w);
        if (err == EMPTY || err == BAD_NAME || err == DUP_NAME || err == TOO_WIDE)
          return false; // prevent loss of focus
        if (err == UNCHANGED)
          return true; // do nothing, unchanged Var will be ignored in setValueAt()
        if (err == OK || err == RESIZED)
          return true; // modified Var will be created in setValueAt()
      }
      return false; // should never happen
    }
  }

  public class DoubleClickVarEditor extends SingleClickVarEditor {
    public DoubleClickVarEditor(VariableList data) {
      super(data);
    }
    @Override
    public boolean isCellEditable(EventObject e) {
      if (super.isCellEditable(e)) {
        if (e instanceof MouseEvent) {
          MouseEvent me = (MouseEvent) e;
          return me.getClickCount() >= 2;
        }
        if (e instanceof KeyEvent) {
          KeyEvent ke = (KeyEvent) e;
          return (ke.getKeyCode() == KeyEvent.VK_F2
              || ke.getKeyCode() == KeyEvent.VK_ENTER);
        }
      }
      return false;
    }
  }

  Var parse(String s) {
    try {
      return Var.parse(s);
    } catch (ParserException e) {
      error.setText(e.getMessage());
      return null;
    }
  }

  private class VarTransferHandler extends TransferHandler {
    JTable table;
    VariableList data;
    ArrayList<Var> pendingDelete = new ArrayList<>();

    VarTransferHandler(JTable table, VariableList data) {
      this.table = table;
      this.data = data;
    }

    public boolean importData(TransferHandler.TransferSupport info) {
      String s;
      try {
        s = (String)info.getTransferable().getTransferData(DataFlavor.stringFlavor);
      } catch (Exception e) {
        return false;
      }

      int newIdx = data.vars.size();
      if (info.isDrop()) {
        try {
          JTable.DropLocation dl = (JTable.DropLocation)info.getDropLocation();
          newIdx = Math.max(0, Math.min(dl.getRow(), data.vars.size()));
        } catch (ClassCastException e) {
        }
      }
     
      String[] list = s.split(",");
      ArrayList<Var> newVars = new ArrayList<>();
      for (String vs : list) {
        vs = vs.trim();
        if (vs.length() == 0)
          continue;
        Var newVar = parse(vs);
        if (newVar == null)
          continue;

        Var oldVar = null;
        int oldIdx;
        for (oldIdx = 0; oldIdx < data.vars.size(); oldIdx++) {
          Var v = data.vars.get(oldIdx);
          if (v.name.equals(newVar.name)) {
            oldVar = v;
            break;
          }
        }

        int err = validateInput(data, oldVar, newVar.name, newVar.width);
        if (err == UNCHANGED || err == RESIZED) {
          if (newIdx > oldIdx)
            newIdx--; // don't count old place when calculating delta > 0
          if (oldIdx != newIdx)
            data.move(oldVar, newIdx - oldIdx);
          if (err == RESIZED)
            data.replace(oldVar, newVar);
          else
            newVar = oldVar;
          newVars.add(newVar);
          newIdx++;
        } else if (err == OK) {
          data.add(newVar);
          oldIdx = data.vars.size() - 1;
          if (newIdx < data.vars.size() - 1)
            data.move(newVar, newIdx - oldIdx);
          newVars.add(newVar);
          newIdx++;
        }
      }
      if (newVars.size() > 0) {
        pendingDelete.clear();
        table.clearSelection();
        for (Var v : newVars) {
          int idx = data.indexOf(v);
          table.addRowSelectionInterval(idx, idx);
        }
        table.grabFocus();
        return true;
      }
      return false;
    }

    protected Transferable createTransferable(JComponent c) {
      int[] rows = table.getSelectedRows();
      String s = null;
      pendingDelete.clear();
      for (int idx : rows) {
        if (idx < 0 || idx >= data.vars.size())
          continue;
        Var v = data.vars.get(idx);
        pendingDelete.add(v);
        s = (s == null ? "" : (s + ", ")) + v.toString();
      }
      return s == null ? null : new StringSelection(s);
    }

    public int getSourceActions(JComponent c) {
      return COPY_OR_MOVE;
    }

    protected void exportDone(JComponent c, Transferable tdata, int action) {
      if (action == MOVE && pendingDelete.size() > 0) {
        for (Var v : pendingDelete)
          data.remove(v);
      }
      pendingDelete.clear();
    }

    public boolean canImport(TransferHandler.TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }
  }

}
