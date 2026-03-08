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

package com.cburch.logisim.gui.opts;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.file.ToolbarData;
import com.cburch.logisim.file.ToolbarData.ToolbarListener;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Tool;

@SuppressWarnings({ "serial", "rawtypes" })
class ToolbarList extends JList {

  // DataFlavor for dragging items within (or out of) the toolbar list.
  // The transferred data is the Integer index of the dragged item.
  // ProjectExplorer uses an identical flavor to accept "drop to remove" gestures.
  static final DataFlavor TOOLBAR_INDEX_FLAVOR;
  static {
    DataFlavor f = null;
    try {
      f = new DataFlavor("application/x-logisim-toolbar-index;class=java.lang.Integer");
    } catch (ClassNotFoundException e) { }
    TOOLBAR_INDEX_FLAVOR = f;
  }

  private class ToolbarTransferHandler extends TransferHandler {
    private int dragSourceIndex = -1;
    private boolean droppedInternally = false;

    @Override
    public int getSourceActions(JComponent c) {
      return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      dragSourceIndex = getSelectedIndex();
      droppedInternally = false;
      if (dragSourceIndex < 0) return null;
      final int idx = dragSourceIndex;
      return new Transferable() {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
          return new DataFlavor[] { TOOLBAR_INDEX_FLAVOR };
        }
        @Override
        public boolean isDataFlavorSupported(DataFlavor f) {
          return TOOLBAR_INDEX_FLAVOR != null && TOOLBAR_INDEX_FLAVOR.equals(f);
        }
        @Override
        public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
          if (TOOLBAR_INDEX_FLAVOR != null && TOOLBAR_INDEX_FLAVOR.equals(f))
            return Integer.valueOf(idx);
          throw new UnsupportedFlavorException(f);
        }
      };
    }

    @Override
    protected void exportDone(JComponent c, Transferable data, int action) {
      // When dropped on the ProjectExplorer (MOVE action, not internal reorder),
      // remove the dragged item from the toolbar.
      if (action == MOVE && !droppedInternally && dragSourceIndex >= 0 && project != null) {
        Object item = base.get(dragSourceIndex);
        Action a = (item == null)
            ? ToolbarActions.removeSeparator(base, dragSourceIndex)
            : ToolbarActions.removeTool(base, dragSourceIndex);
        project.doAction(a);
      }
      dragSourceIndex = -1;
      droppedInternally = false;
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDrop()) return false;
      // Internal reorder: toolbar item dropped back onto the toolbar list
      if (TOOLBAR_INDEX_FLAVOR != null
          && support.isDataFlavorSupported(TOOLBAR_INDEX_FLAVOR)) {
        support.setDropAction(MOVE);
        return true;
      }
      // External add: tool dragged in from the ProjectExplorer
      if (support.isDataFlavorSupported(Tool.dnd.dataFlavor)) {
        try {
          Object t = support.getTransferable().getTransferData(Tool.dnd.dataFlavor);
          if (t instanceof Tool) {
            support.setDropAction(COPY); // don't remove from explorer
            return true;
          }
        } catch (Exception e) { /* ignore — not a directly transferable Tool */ }
      }
      return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!support.isDrop() || project == null) return false;
      JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
      int dropIndex = dl.getIndex();
      if (dropIndex < 0) dropIndex = base.size();

      try {
        if (TOOLBAR_INDEX_FLAVOR != null
            && support.isDataFlavorSupported(TOOLBAR_INDEX_FLAVOR)) {
          // Internal reorder
          int fromIndex = (Integer) support.getTransferable().getTransferData(TOOLBAR_INDEX_FLAVOR);
          if (fromIndex < 0 || fromIndex >= base.size()) return false;
          // Adjust insertion point for removal of the dragged item
          int toIndex = (dropIndex > fromIndex) ? dropIndex - 1 : dropIndex;
          if (fromIndex == toIndex) return false; // no-op: dropped in same position
          droppedInternally = true;
          project.doAction(ToolbarActions.moveTool(base, fromIndex, toIndex));
          setSelectedIndex(toIndex);
          return true;
        }
        if (support.isDataFlavorSupported(Tool.dnd.dataFlavor)) {
          // Add tool from ProjectExplorer
          Object obj = support.getTransferable().getTransferData(Tool.dnd.dataFlavor);
          if (!(obj instanceof Tool)) return false;
          project.doAction(ToolbarActions.addTool(base, ((Tool) obj).cloneTool(), dropIndex));
          setSelectedIndex(dropIndex);
          return true;
        }
      } catch (Exception e) { /* ignore */ }
      return false;
    }
  }

  private static class ListRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      Component ret;
      Icon icon;
      if (value instanceof Tool) {
        Tool t = (Tool) value;
        ret = super.getListCellRendererComponent(list,
            t.getDisplayName(), index, isSelected, cellHasFocus);
        icon = new ToolIcon(t);
      } else if (value == null) {
        ret = super.getListCellRendererComponent(list, "------------", index,
            isSelected, cellHasFocus);
        icon = null;
      } else {
        ret = super.getListCellRendererComponent(list,
            value.toString(), index, isSelected, cellHasFocus);
        icon = null;
      }
      if (ret instanceof JLabel) {
        ((JLabel) ret).setIcon(icon);
      }
      return ret;
    }
  }

  private class Model extends AbstractListModel
    implements ToolbarListener, AttributeListener, PropertyChangeListener {
    public void attributeListChanged(AttributeEvent e) {
    }

    public void attributeValueChanged(AttributeEvent e) {
      repaint();
    }

    public Object getElementAt(int index) {
      return base.get(index);
    }

    public int getSize() {
      return base.size();
    }

    public void propertyChange(PropertyChangeEvent event) {
      if (AppPreferences.GATE_SHAPE.isSource(event)) {
        repaint();
      }
    }

    public void toolbarChanged() {
      fireContentsChanged(this, 0, getSize());
    }
  }

  private static class ToolIcon implements Icon {
    private Tool tool;

    ToolIcon(Tool tool) {
      this.tool = tool;
    }

    public int getIconHeight() {
      return 20;
    }

    public int getIconWidth() {
      return 20;
    }

    public void paintIcon(Component comp, Graphics g, int x, int y) {
      Graphics gNew = g.create();
      tool.paintIcon(new ComponentDrawContext(comp, null, null, g, gNew),
          x + 2, y + 2);
      gNew.dispose();
    }
  }

  private ToolbarData base;
  private Model model;
  private Project project;

  @SuppressWarnings("unchecked")
  public ToolbarList(ToolbarData base, Project project) {
    this.base = base;
    this.project = project;
    this.model = new Model();

    setModel(model);
    setCellRenderer(new ListRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setDragEnabled(true);
    setDropMode(DropMode.INSERT);
    setTransferHandler(new ToolbarTransferHandler());

    AppPreferences.GATE_SHAPE.addPropertyChangeWeakListener(model);
    base.addToolbarWeakListener(null, model);
    base.addToolAttributeWeakListener(/*null,*/ model);
  }

  public void localeChanged() {
    model.toolbarChanged();
  }
}
