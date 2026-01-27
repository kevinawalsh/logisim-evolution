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

package com.cburch.logisim.gui.main;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;

import com.cburch.draw.toolbar.AbstractToolbarModel;
import com.cburch.draw.toolbar.Toolbar;
import com.cburch.draw.toolbar.ToolbarItem;
import com.cburch.draw.toolbar.ToolbarSeparator;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.file.ToolbarData;
import com.cburch.logisim.gui.menu.Popups;
import com.cburch.logisim.gui.opts.ToolbarActions;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.DragDrop;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.InputEventUtil;

class LayoutToolbarModel extends AbstractToolbarModel {
  private class MyListener implements ProjectListener, AttributeListener,
          ToolbarData.ToolbarListener, PropertyChangeListener {
    
    @Override
    public void attributeListChanged(AttributeEvent e) { }

    @Override
    public void attributeValueChanged(AttributeEvent e) {
      fireToolbarAppearanceChanged();
    }

    @Override
    public void projectChanged(ProjectEvent e) {
      int act = e.getAction();
      if (act == ProjectEvent.ACTION_SET_TOOL) {
        fireToolbarAppearanceChanged();
      } else if (act == ProjectEvent.ACTION_SET_FILE) {
        LogisimFile old = (LogisimFile) e.getOldData();
        if (old != null) {
          ToolbarData data = old.getOptions().getToolbarData();
          data.removeToolbarWeakListener(null, this);
          data.removeToolAttributeWeakListener(/*null,*/ this);
        }
        LogisimFile file = (LogisimFile) e.getData();
        if (file != null) {
          ToolbarData data = file.getOptions().getToolbarData();
          data.addToolbarWeakListener(null, this);
          data.addToolAttributeWeakListener(/*null,*/ this);
        }
        buildContents();
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (AppPreferences.GATE_SHAPE.isSource(event)) {
        fireToolbarAppearanceChanged();
      }
    }

    @Override
    public void toolbarChanged() {
      buildContents();
    }
  }

  private static Font FONT = new Font("SansSerif", Font.PLAIN, 12);

  private class ToolItem implements ToolbarItem, AttributeListener {
    private Tool tool;
    String label;

    ToolItem(Tool tool) {
      this.tool = tool;
      if (tool instanceof AddTool)
        makeLabel();
    }

    private void makeLabel() {
      AddTool addTool = (AddTool)tool;
      if (addTool.getFactory() instanceof SubcircuitFactory) {
        label = tool.getName();
        ((SubcircuitFactory)addTool.getFactory()).getSubcircuit()
            .getStaticAttributes().addAttributeWeakListener(null, this);
      } else if (addTool.getFactory() instanceof VhdlEntity) {
        label = tool.getName();
        ((VhdlEntity)addTool.getFactory()).getContent()
            .getStaticAttributes().addAttributeWeakListener(null, this);
      }
    }

    @Override
    public void attributeValueChanged(AttributeEvent e) {
      if (e.getAttribute() == VhdlEntity.NAME_ATTR
          || e.getAttribute() == CircuitAttributes.NAME_ATTR) {
        makeLabel();
        fireToolbarContentsChanged();
      } else {
        fireToolbarAppearanceChanged();
      }
    }

    @Override
    public void attributeListChanged(AttributeEvent e) { }

    @Override
    public Dimension getDimension(JComponent c, Object orientation) {
      if (label == null)
        return new Dimension(24, 24);
      FontMetrics fm = c.getFontMetrics(FONT);
      int w = fm.stringWidth(label);
      // int h = fm.getHeight();
      if (orientation == Toolbar.HORIZONTAL)
        return new Dimension(24 + 3 + w, 24);
      else
        return new Dimension(24, 24 + 2 + w);
    }

    @Override
    public String getToolTip() {
      String ret = tool.getDescription();
      int index = 1;
      for (ToolbarItem item : items) {
        if (item == this)
          break;
        if (item instanceof ToolItem)
          ++index;
      }
      if (index <= 10) {
        if (index == 10)
          index = 0;
        int mask = InputEvent.CTRL_DOWN_MASK;
        ret += " (" + InputEventUtil.toKeyDisplayString(Character.valueOf((char)('0'+index)), mask) + ")";
      }
      return ret;
    }

    @Override
    public boolean isSelectable() {
      return true;
    }

    @Override
    public void paintIcon(Component dest, Graphics g) {
      // draw halo
      if (tool == haloedTool
          && AppPreferences.ATTRIBUTE_HALO.get()) {
        g.setColor(Canvas.HALO_COLOR);
        Dimension dim = dest.getPreferredSize();
        g.fillRect(1, 1, dim.width-2, dim.height-2);
      }

      // draw tool icon
      g.setColor(Color.BLACK);
      Graphics g_copy = g.create();
      ComponentDrawContext c = new ComponentDrawContext(dest, null, null, g, g_copy);
      tool.paintIcon(c, 2, 2);

      if (label != null) {
        Dimension dim = dest.getPreferredSize();
        if (dim.width >= dim.height) {
          GraphicsUtil.drawText(g_copy, FONT, label, 24 - 2, 24/2 - 2,
              GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
        } else {
          ((Graphics2D)g_copy).rotate(Math.PI/2, 24/2, 24/2);
          GraphicsUtil.drawText(g_copy, FONT, label, 24 - 2, 24/2 - 2,
              GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
        }
      }

      g_copy.dispose();

    }
  }

  private static ToolbarItem findItem(List<ToolbarItem> items, Tool tool) {
    for (ToolbarItem item : items) {
      if (item instanceof ToolItem) {
        if (tool == ((ToolItem) item).tool) {
          return item;
        }
      }
    }
    return null;
  }

  private Frame frame;
  private Project proj;
  private MyListener myListener;
  private List<ToolbarItem> items;

  private Tool haloedTool;

  public LayoutToolbarModel(Frame frame, Project proj) {
    this.frame = frame;
    this.proj = proj;
    myListener = new MyListener();
    items = Collections.emptyList();
    haloedTool = null;
    buildContents();

    // set up listeners
    ToolbarData data = proj.getOptions().getToolbarData();
    data.addToolbarWeakListener(null, myListener);
    data.addToolAttributeWeakListener(/*null,*/ myListener);
    AppPreferences.GATE_SHAPE.addPropertyChangeListener(myListener);
    proj.addProjectWeakListener(null, myListener);
  }

  private void buildContents() {
    List<ToolbarItem> oldItems = items;
    List<ToolbarItem> newItems = new ArrayList<ToolbarItem>();
    ToolbarData data = proj.getLogisimFile().getOptions().getToolbarData();
    for (Tool tool : data.getContents()) {
      if (tool == null) {
        newItems.add(new ToolbarSeparator(5));
      } else {
        ToolbarItem i = findItem(oldItems, tool);
        if (i == null) {
          newItems.add(new ToolItem(tool));
        } else {
          newItems.add(i);
        }
      }
    }
    items = Collections.unmodifiableList(newItems);
    fireToolbarContentsChanged();
  }

  @Override
  public List<ToolbarItem> getItems() {
    return items;
  }

  @Override
  public boolean isSelected(ToolbarItem item) {
    if (item instanceof ToolItem) {
      Tool tool = ((ToolItem) item).tool;
      return tool == proj.getTool();
    } else {
      return false;
    }
  }

  @Override
  public void itemSelected(ToolbarItem item) {
    if (item instanceof ToolItem) {
      Tool tool = ((ToolItem) item).tool;
      proj.setTool(tool);
    }
  }

  public void setHaloedTool(Tool t) {
    if (haloedTool != t) {
      haloedTool = t;
      fireToolbarAppearanceChanged();
    }
  }

  @Override
  public DataFlavor getAcceptedDataFlavor() {
    // return com.cburch.logisim.tools.MenuTool.dnd.dataFlavor;
    return Tool.dnd.dataFlavor;
  }

  @Override
  public DataFlavor[] getPrecheckDataFlavors() {
    return new DataFlavor[] {
      DragDrop.JVMLOCAL_UUID_FLAVOR,
      (DataFlavor)Tool.UUID_FLAVOR,
    };
    // note: a separate Project.UUID_FLAVOR is not needed,
    // b/c the tool UUIDs we accept are project-specific anyway
  }

  @Override
  public boolean dragPrecheck(String[] tokens) {
    // if (incoming instanceof Tool && ((Tool)incoming).isBuiltin())
    //   return true;
    if (!tokens[0].equals(DragDrop.JVMLOCAL_UUID_TOKEN))
      return false;
    if (tokens[1].startsWith("tool:builtin:"))
      return true;
    // if (!(incoming instanceof AddTool))
    //   return false;
    // AddTool tool = (AddTool)incoming;
    // ComponentFactory factory = tool.getFactory();
    // if (factory == null)
    //   return false;
    // if (factory instanceof SubcircuitFactory || factory instanceof VhdlEntity)
    if (!tokens[1].startsWith("tool:add-subcircuit:")
        && !tokens[1].startsWith("tool:add-vhdl:"))
      return false;
    //   // LogisimFile f = ((SubcircuitFactory)factory).getCircuit().getLogisimFile(); 
    //   // if (proj.getLogisimFile() == f)
    //   //   return true;
    //   // Search our file to see if we have the same AddTool, either as an
    //   // AddTool for one our circuits, or an AddTool for some jar library we
    //   // loaded, or an AddTool for some circuit inside (perhaps even nested
    //   // multiple levels deep) inside a Logisim library.
    //   return proj.getLogisimFile().findEquivalentTool(tool) != null;
    String uuid = tokens[1].substring(tokens[1].lastIndexOf(':')+1);
    return proj.getLogisimFile().findToolByUUID(uuid) != null;
  }
  
  private boolean dropCheck(Tool incoming) {
    if (!(incoming instanceof AddTool))
      return false;
    AddTool tool = (AddTool)incoming;
    ComponentFactory factory = tool.getFactory();
    if (factory == null)
      return false;
    if (factory instanceof SubcircuitFactory || factory instanceof VhdlEntity)
      // Search our file to see if we have the same AddTool, either as an
      // AddTool for one our circuits, or an AddTool for some jar library we
      // loaded, or an AddTool for some circuit inside (perhaps even nested
      // multiple levels deep) inside a Logisim library.
      return proj.getLogisimFile().findEquivalentTool(tool) != null;
    return false;
  }

  @Override
  public boolean handleDrop(Object incoming, int pos) {
    Tool tool = (Tool)incoming;
    if (tool == null || !tool.isBuiltin() && !dropCheck(tool))
      return false;
    Options opts = proj.getLogisimFile().getOptions();
    proj.doAction(ToolbarActions.addTool(opts.getToolbarData(), tool.cloneTool(), pos));
    return true;
  }

  @Override
  public boolean handleDragDrop(int oldPos, int newPos) {
    Options opts = proj.getLogisimFile().getOptions();
    proj.doAction(ToolbarActions.moveTool(opts.getToolbarData(), oldPos, newPos));
    return true;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return Popups.forLayoutToolbar(proj);
  }

}
