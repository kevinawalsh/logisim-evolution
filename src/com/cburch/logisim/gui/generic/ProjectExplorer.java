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

package com.cburch.logisim.gui.generic;

/**
 * Code taken from Cornell's version of Logisim:
 * http://www.cs.cornell.edu/courses/cs3410/2015sp/
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.LayoutClipboard;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.MouseListenerUtil;

public class ProjectExplorer extends JTree implements LocaleListener {

  private class DeleteAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    public void actionPerformed(ActionEvent event) {
      TreePath path = getSelectionPath();
      if (listener != null && path != null && path.getPathCount() == 2) {
        listener.deleteRequested(new Event(path));
      }

      ProjectExplorer.this.requestFocus();
    }
  }

  private class SelectAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    public void actionPerformed(ActionEvent event) {
      proj.doAction(SelectionActions.clear(proj.getSelection())); // only needed here for lib selection
      TreePath path = getSelectionPath();
      if (path != null && listener != null) {
        listener.doubleClicked(new Event(path));
      }
      // ProjectExplorer.this.requestFocus();
    }
  }

  private class MyCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    Font plainFont, boldFont;

    private boolean isCurrentView(Tool tool) {
      if (tool instanceof AddTool && proj != null && proj.getFrame() != null) {
        Circuit circ = null;
        VhdlContent vhdl = null;
        ComponentFactory fact = ((AddTool) tool).getFactory(false);
        if (fact instanceof SubcircuitFactory)
          circ = ((SubcircuitFactory) fact).getSubcircuit();
        else if (fact instanceof VhdlEntity)
          vhdl = ((VhdlEntity)fact).getContent();
        if (proj.getFrame().getHdlEditorView() == null)
          return (circ != null && circ == proj.getCurrentCircuit());
        else
          return (vhdl != null && vhdl == proj.getFrame().getHdlEditorView());
      }
      return false;
    }

    private boolean isMainCircuit(Tool tool) {
      if (tool instanceof AddTool && proj != null && proj.getLogisimFile() != null) {
        ComponentFactory fact = ((AddTool) tool).getFactory(false);
        if (fact instanceof SubcircuitFactory) {
          Circuit circ = ((SubcircuitFactory) fact).getSubcircuit();
          return proj.getLogisimFile().getMainCircuit() == circ;
        }
      }
      return false;
    }

    @Override
    public java.awt.Component getTreeCellRendererComponent(JTree tree,
        Object value, boolean selected, boolean expanded, boolean leaf,
        int row, boolean hasFocus) {
      java.awt.Component ret;
      ret = super.getTreeCellRendererComponent(tree, value, selected,
          expanded, leaf, row, hasFocus);
      if (plainFont == null) {
        plainFont = ret.getFont();
        boldFont = new Font(plainFont.getFontName(), Font.BOLD, plainFont.getSize());
      }
      ret.setFont(plainFont);
      if (ret instanceof JComponent) {
        JComponent comp = (JComponent) ret;
        comp.setToolTipText(null);
        comp.setOpaque(false);
      }
      if (!(ret instanceof JLabel))
        return ret;
      JLabel label = (JLabel)ret;
      if (value instanceof ProjectExplorerToolNode) {
        ProjectExplorerToolNode toolNode = (ProjectExplorerToolNode) value;
        Tool tool = toolNode.getValue();
        if (isCurrentView(tool)) {
          label.setFont(boldFont);
          // label.setBackground(VIEWED_TOOL_COLOR);
          // label.setOpaque(true);
        }
        String text = tool.getDisplayName();
        if (isMainCircuit(tool)) {
          text += label.getFont().canDisplay(MAIN_MARKER) ? " "+MAIN_MARKER : " "+MAIN_MARKER2;
        }
        label.setText(text);
        label.setIcon(new ToolIcon(tool, isMainCircuit(tool)));
        label.setToolTipText(tool.getDescription());
      } else if (value instanceof ProjectExplorerLibraryNode) {
        ProjectExplorerLibraryNode libNode = (ProjectExplorerLibraryNode) value;
        Library lib = libNode.getValue();
        String text = lib.getDisplayName();
        if (lib.isDirty()) {
          text += label.getFont().canDisplay(DIRTY_MARKER) ? " "+DIRTY_MARKER : DIRTY_MARKER2;
        }
        label.setText(text);
      }
      return ret;
    }
  }
  private class MyListener implements MouseListener, TreeSelectionListener {
    private void checkForPopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null && listener != null) {
          JPopupMenu menu = listener.menuRequested(new Event(path));
          if (menu != null) {
            menu.show(ProjectExplorer.this, e.getX(), e.getY());
          }
        }
      }
    }

    public void mouseClicked(MouseEvent e) {
      proj.doAction(SelectionActions.clear(proj.getSelection())); // only needed here for lib selection
      if (e.getClickCount() == 2) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null && listener != null) {
          listener.doubleClicked(new Event(path));
        }
      }
    }

    public void mouseEntered(MouseEvent e) { } 
    public void mouseExited(MouseEvent e) { }

    public void mousePressed(MouseEvent e) {
      ProjectExplorer.this.requestFocus();
      checkForPopup(e);
    }

    public void mouseReleased(MouseEvent e) {
      checkForPopup(e);
    }

    public void valueChanged(TreeSelectionEvent e) {
      TreePath path = e.getNewLeadSelectionPath();
      if (listener != null) {
        listener.selectionChanged(new Event(path));
      }
    }
  }

  private class ToolIcon implements Icon {

    Tool tool;
    Circuit circ = null;
    VhdlContent vhdl = null;
    boolean isMain;

    ToolIcon(Tool tool, boolean isMain) {
      this.tool = tool;
      if (tool instanceof AddTool) {
        ComponentFactory fact = ((AddTool) tool).getFactory(false);
        if (fact instanceof SubcircuitFactory) {
          circ = ((SubcircuitFactory) fact).getSubcircuit();
        } else if (fact instanceof VhdlEntity) {
          vhdl = ((VhdlEntity) fact).getContent();
        }
      }
    }

    public int getIconHeight() { return 20; }
    public int getIconWidth() { return 20; }

    public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
      boolean viewed;
      if (proj.getFrame().getHdlEditorView() == null)
        viewed = (circ != null && circ == proj.getCurrentCircuit());
      else
        viewed = (vhdl != null && vhdl == proj.getFrame().getHdlEditorView());
      boolean haloed = !viewed
          && (tool == haloedTool && AppPreferences.ATTRIBUTE_HALO.get());

      // draw active-tool-halo or current-viewed-halo if appropriate
      if (viewed || haloed) {
        Shape s = g.getClip();
        g.clipRect(x, y, 20, 20);
        g.setColor(viewed ? VIEWED_TOOL_HALO_COLOR : Canvas.HALO_COLOR);
        g.fillOval(x-2, y-2, 23, 23);
        g.setColor(Color.BLACK);
        g.setClip(s);
      }

      // draw tool icon
      Graphics gIcon = g.create();
      ComponentDrawContext context = new ComponentDrawContext(
          ProjectExplorer.this, null, null, g, gIcon);
      tool.paintIcon(context, x, y);
      gIcon.dispose();

      // draw magnifying glass if appropriate
      if (viewed) {
        int tx = x + 13;
        int ty = y + 13;
        int[] xp = { tx - 1, x + 18, x + 20, tx + 1 };
        int[] yp = { ty + 1, y + 20, y + 18, ty - 1 };
        g.setColor(MAGNIFYING_INTERIOR);
        g.fillOval(x + 5, y + 5, 10, 10);
        g.setColor(Color.BLACK);
        g.drawOval(x + 5, y + 5, 10, 10);
        g.fillPolygon(xp, yp, xp.length);
      }
    }
  }

  private static final long serialVersionUID = 1L;

  private static final char DIRTY_MARKER = '\u25CF'; // black circle
  private static final String DIRTY_MARKER2 = " *";
  private static final char MAIN_MARKER = '\u2747'; // sparkle
  private static final String MAIN_MARKER2 = " <>";

  public static final Color MAGNIFYING_INTERIOR = new Color(200, 200, 255, 64);
  // public static final Color VIEWED_TOOL_COLOR = new Color(255, 255, 153);
  public static final Color VIEWED_TOOL_HALO_COLOR =
      UIManager.getDefaults().getColor("List.selectionBackground");

  private Project proj;
  private MyListener myListener = new MyListener();
  private MyCellRenderer renderer = new MyCellRenderer();
  private DeleteAction deleteAction = new DeleteAction();
  private SelectAction selectAction = new SelectAction();
  private Listener listener = null;
  private Tool haloedTool = null;

  public ProjectExplorer(Project proj) {
    this(proj, false);
  }

  public ProjectExplorer(Project proj, boolean showAll) {
    super();
    this.proj = proj;
    ProjectExplorerModel model = new ProjectExplorerModel(proj, showAll);
    setModel(model);
    setRootVisible(!showAll); // hide the fake root when showing all
    setShowsRootHandles(false); // do not show root handles
    if (showAll) {
      // expand first level children
      ProjectExplorerModel.Node<?> root = model.getRootNode();
      for (ProjectExplorerModel.Node<?> node : root.getChildren())
          expandPath(model.getPath(node));
    }

    addMouseListener(MouseListenerUtil.clickFix(myListener));
    ToolTipManager.sharedInstance().registerComponent(this);

    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    setCellRenderer(renderer);
    addTreeSelectionListener(myListener);
    setDragEnabled(true);
    setDropMode(DropMode.INSERT);
    TransferHandler ccp = new ProjectTransferHandler();
    setTransferHandler(ccp);

    ActionMap amap = getActionMap();
    amap.put(deleteAction, deleteAction);
    amap.put(selectAction, selectAction);
    InputMap imap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), deleteAction);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), deleteAction);
    imap.put(KeyStroke.getKeyStroke("released SPACE"), selectAction);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), selectAction);
   
    AppPreferences.GATE_SHAPE.addPrefChangeWeakListener(this, e -> this.repaint());
    LocaleManager.addLocaleListener(this);
  }

  public Tool getSelectedTool() {
    TreePath path = getSelectionPath();
    if (path == null)
      return null;
    Object last = path.getLastPathComponent();

    if (last instanceof ProjectExplorerToolNode) {
      return ((ProjectExplorerToolNode) last).getValue();
    } else {
      return null;
    }
  }

  public void setSelectedTool(Tool tool) {
    ProjectExplorerModel model = (ProjectExplorerModel) getModel();
    ProjectExplorerModel.Node<?> node = model.findTool(tool);
    if (node != null) {
      TreePath path = model.getPath(node);
      scrollPathToVisible(path);
      setSelectionPath(path);
    }
    else
      Debug.println(1, "Oops: object not found: " + tool);

  }

  public Library getSelectedLibrary() {
    TreePath path = getSelectionPath();
    if (path == null)
      return null;
    Object last = path.getLastPathComponent();

    if (last instanceof ProjectExplorerLibraryNode) {
      return ((ProjectExplorerLibraryNode) last).getValue();
    } else {
      return null;
    }
  }

  public void updateStructure() {
    ProjectExplorerModel model = (ProjectExplorerModel) getModel();
    model.updateStructure();
  }

  public void localeChanged() {
    // repaint() would work, except that names that get longer will be
    // abbreviated with an ellipsis, even when they fit into the window.
    ((ProjectExplorerModel) getModel()).updateStructure();
  }

  public void setHaloedTool(Tool t) {
    haloedTool = t;
  }

  public void setListener(Listener value) {
    listener = value;
  }

  // Same MIME type as ToolbarList.TOOLBAR_INDEX_FLAVOR; duplicated here to avoid
  // a package dependency from gui/generic onto gui/opts.
  private static final DataFlavor TOOLBAR_INDEX_FLAVOR;
  static {
    DataFlavor f = null;
    try {
      f = new DataFlavor("application/x-logisim-toolbar-index;class=java.lang.Integer");
    } catch (ClassNotFoundException e) { }
    TOOLBAR_INDEX_FLAVOR = f;
  }

  private class ProjectTransferHandler extends TransferHandler {

    @Override
    public int getSourceActions(JComponent comp) {
      return COPY | MOVE | LINK;
    }

    @Override
    public Transferable createTransferable(JComponent comp) {
      // Circuit and Vhdl AddTools can be dragged out to be dropped into to this
      // project (for reordering and/or copying), into other JVM-local or
      // JVM-foriegn projects (xml-based copying), into the toolbar, or into the
      // mouse options panes. To support all these, dual-purpose Transferables
      // are used.
      Tool tool = getSelectedTool();
      if (tool instanceof AddTool) {
        if (((AddTool)tool).getFactory() instanceof SubcircuitFactory) {
          // System.out.println("xfer explorer AddTool circuit item: " + tool);
          return ((AddTool)tool).new TransferableCircuit(proj.getLogisimFile(), proj);
        } else if (((AddTool)tool).getFactory() instanceof VhdlEntity) {
          // System.out.println("xfer explorer AddTool vhdl item: " + tool);
          return ((AddTool)tool).new TransferableVhdl(proj.getLogisimFile(), proj);
        }
      }
      // Other tools (e.g. Muxes, Wiring) can be dragged out to be dropped into 
      // toolbars, mouse option panes, etc.
      if (tool instanceof Transferable) {
        // System.out.println("not add circuit or vhdl tool, but still ok to send locally: " + tool);
        return (Transferable)tool;
      }
      // Libraries can be dragged out to be dropped into this project (for
      // reordering and/or copying), or into other JVM-local or JVM-foriegn
      // projects (xml-based copying). To support all these, dual-purpose
      // Transferables are used.
      Library lib = getSelectedLibrary();
      if (lib != null) {
        // System.out.println("xfer explorer Library item: " + lib);
        return lib.new TransferableLibrary(proj.getLogisimFile(), proj);
      }
      return null;
    }

    // We use the default TransferHandler behavior for drag/drop, but not for
    // cut/copy/paste. Drag/drop will transfer using JVM-local Circuit and
    // Library objects when possible, and will only xml-encode objects when
    // dragging across JVMs (in which case it gets encoded as soon as the mouse
    // is released). For cut/copy/paste, we always want to xml-encode
    // immediately, and never use JVM-local objects.
    @Override
    public void exportToClipboard(JComponent comp, Clipboard clip, int action)
        throws IllegalStateException { 
      // System.out.println("exporting to clip");
      if (clip != Toolkit.getDefaultToolkit().getSystemClipboard())
        throw new IllegalArgumentException("mystery clipboard");
      Tool tool = getSelectedTool();
      Library lib = getSelectedLibrary();
      if (tool instanceof AddTool) {
        ComponentFactory fact = ((AddTool)tool).getFactory();
        if (fact instanceof SubcircuitFactory) {
          Circuit circ = ((SubcircuitFactory)fact).getSubcircuit();
          if (action == MOVE)
            SelectionActions.doCut(proj, circ);
          else
            SelectionActions.doCopy(proj, circ);
        } else if (fact instanceof VhdlEntity) {
          VhdlContent vhdl = ((VhdlEntity) fact).getContent();
          if (action == MOVE)
            SelectionActions.doCut(proj, vhdl);
          else
            SelectionActions.doCopy(proj, vhdl);
        }
      } else if (lib != null) {
        if (action == MOVE)
          SelectionActions.doCut(proj, lib);
        else
          SelectionActions.doCopy(proj, lib);
      }
    }

    private final DataFlavor[] supportedFlavors = new DataFlavor[] {
        Tool.dnd.dataFlavor,
        Library.dnd.dataFlavor,
        LayoutClipboard.forCircuit.dnd.dataFlavor,
        LayoutClipboard.forVhdl.dnd.dataFlavor,
        LayoutClipboard.forLibrary.dnd.dataFlavor,
    };

    private DataFlavor supportedFlavor(TransferSupport support) {
      for (DataFlavor flavor : supportedFlavors) {
        if (support.isDataFlavorSupported(flavor))
          return flavor;
      }
      return null;
    }
    
    @Override
    public boolean canImport(TransferSupport support) {
      // Accept toolbar item drops even in showAll mode: dropping a toolbar item here
      // signals "remove from toolbar" (the actual removal happens in the source's exportDone).
      if (TOOLBAR_INDEX_FLAVOR != null && support.isDrop()
          && support.isDataFlavorSupported(TOOLBAR_INDEX_FLAVOR)) {
        support.setDropAction(MOVE);
        return true;
      }
      if (listener == null || !(getModel().getRoot() instanceof ProjectExplorerLibraryNode))
        return false; // no drag if we are in showAll mode (i.e. in prefs window)
      try {
        DataFlavor flavor = supportedFlavor(support);
        if (flavor == null)
          return false;
        boolean isTool, isLocal;
        isTool = (flavor == Tool.dnd.dataFlavor
            || flavor == LayoutClipboard.forCircuit.dnd.dataFlavor
            || flavor == LayoutClipboard.forVhdl.dnd.dataFlavor);
        if (flavor == Tool.dnd.dataFlavor) {
          // drag import of JVM-local Tool
          Tool incoming = (Tool)support.getTransferable().getTransferData(flavor);
          if (!(incoming instanceof AddTool))
            return false; // only AddTool can be imported into a project (i.e. no mouse tools)
          ComponentFactory fact = ((AddTool)incoming).getFactory();
          if (!(fact instanceof SubcircuitFactory || fact instanceof VhdlEntity))
            return false; // only Circuit and Vhdl can be imported into a project
          isLocal = proj.getLogisimFile().getTools().contains(incoming);
        } else if (flavor == Library.dnd.dataFlavor) {
          // drag import of JVM-local Library
          Library incoming = (Library)support.getTransferable().getTransferData(flavor);
          isLocal = proj.getLogisimFile().getLibraries().contains(incoming);
        } else {
          // drag import of JVM-foreign Circuit, Vhdl, or Library
          isLocal = false;
        }
        int newIdx = insertionIndex(support, isTool);
        if (newIdx < 0)
          return false; // bad drop target
        if (!isLocal)
          support.setDropAction(COPY);
        return true;
      } catch (Throwable e) {
        // e.printStackTrace();
        Debug.error("project explorer", e);
      }
      return false;
    }

    private int insertionIndex(TransferSupport support, boolean isTool) {
      Object root = getModel().getRoot();
      if (!(root instanceof ProjectExplorerLibraryNode)) // no inserting in options window
        return -1;
      ProjectExplorerLibraryNode libNode = (ProjectExplorerLibraryNode)root;
      Library lib = libNode.getValue();
      int n = lib.getTools().size();
      int m = lib.getLibraries().size();
      if (!support.isDrop())
        return isTool ? n : m;
      try {
        JTree.DropLocation dl = (JTree.DropLocation)support.getDropLocation();
        TreePath newPath = dl.getPath();
        if (newPath.getPathCount() != 1)
          return -1;
        Object o = newPath.getPathComponent(0);
        if (o != getModel().getRoot())
          return -1;
        int newIdx = dl.getChildIndex();
        if (newIdx < n && !isTool)
          return -1;
        else if (newIdx > n && isTool)
          return -1;
        // System.out.println("drop at top position " + newIdx);
        return isTool ? newIdx : (newIdx - n);
      } catch (ClassCastException e) {
        return -1;
      }
    }

    @Override
    public boolean importData(TransferSupport support) {
      // System.out.println("importing...");
      // Toolbar item drop is a no-op here; the removal is handled by the source's exportDone.
      if (TOOLBAR_INDEX_FLAVOR != null && support.isDrop()
          && support.isDataFlavorSupported(TOOLBAR_INDEX_FLAVOR))
        return true;
      if (listener == null || !(getModel().getRoot() instanceof ProjectExplorerLibraryNode))
        return false; // no import if we are in showAll mode (i.e. in prefs window)
      try {
        DataFlavor flavor = supportedFlavor(support);
        if (flavor == null)
          return false;
        boolean isTool = (flavor == Tool.dnd.dataFlavor
            || flavor == LayoutClipboard.forCircuit.dnd.dataFlavor
            || flavor == LayoutClipboard.forVhdl.dnd.dataFlavor);
        int newIdx = insertionIndex(support, isTool);
        if (newIdx < 0)
          return false; // bad drop target
        boolean isMove = support.isDrop() && support.getDropAction() == MOVE;

        if (isMove && flavor == Tool.dnd.dataFlavor) {
          // drag import of JVM-local AddTool
          Tool incoming = (Tool)support.getTransferable().getTransferData(flavor);
          if (!(incoming instanceof AddTool))
            return false; // only AddTool can be imported into a project (i.e. no mouse tools)
          ComponentFactory fact = ((AddTool)incoming).getFactory();
          if (!(fact instanceof SubcircuitFactory || fact instanceof VhdlEntity))
            return false; // only Circuit and Vhdl can be imported into a project

          int oldIdx = proj.getLogisimFile().getTools().indexOf(incoming);
          // System.out.println("import tool " + incoming + " at " + newIdx + " vs " + oldIdx);
          if (oldIdx >= 0) {
            // System.out.printf("move circuit or vhdl from %d to %d\n", oldIdx, newIdx);
            return listener.moveRequested((AddTool)incoming, newIdx);
          } else {
            // System.out.printf("bad move circuit or vhdl to %d\n", newIdx);
            return false;
          }
        } else if (isMove && flavor == Library.dnd.dataFlavor) {
          // drag import of JVM-local Library
          Library incoming = (Library)support.getTransferable().getTransferData(flavor);
          int oldIdx = proj.getLogisimFile().getLibraries().indexOf(incoming);
          // System.out.println("import lib " + incoming + " at " + newIdx + " vs " + oldIdx);
          if (oldIdx >= 0) {
            // System.out.printf("move lib from %d to %d\n", oldIdx, newIdx);
            return listener.moveRequested(incoming, newIdx);
          } else {
            // System.out.printf("bad move lib to %d\n", newIdx);
            return false;
          }
        } else if (isMove) {
          // huh? isMove should happen only for move-type drag of JVM-local object
          // System.out.printf("bad move\n");
          return false;
        } else if (support.isDrop()) {
          // JVM-foreign drag or copy-type drag of Circuit, Vhdl, or Library
          return SelectionActions.doDrop(proj, support.getTransferable(), newIdx);
        } else {
          // paste of Circuit, Vhdl, or Library
          return SelectionActions.doPaste(proj, support.getTransferable());
        }
      } catch (UnsupportedFlavorException | IOException e) {
        // e.printStackTrace();
        Debug.error("xml transfer", e);
      }
      return false;
    }
  }

  public static interface Listener {
    public void doubleClicked(Event event);
    public JPopupMenu menuRequested(Event event);
    default public void deleteRequested(Event event) { }
    default public boolean moveRequested(AddTool dragged, int newIdx) { return false; }
    default public boolean moveRequested(Library dragged, int newIdx) { return false; }
    public void selectionChanged(Event event);
  }

  public static class Event {
    private TreePath path;
    public Event(TreePath p) { path = p; }
    public TreePath getTreePath() { return path; }
    public Object getTarget() {
      return path == null ? null : path.getLastPathComponent();
    }
  }

  public void clearExplorerSelection() {
    clearSelection();
  }

  public void reveal(List<Library> libPath, Object obj) {
    ProjectExplorerModel model = (ProjectExplorerModel) getModel();
    ProjectExplorerModel.Node<?> node = model.findObject(libPath, obj);
    if (node != null) {
      TreePath path = model.getPath(node);
      scrollPathToVisible(path);
      setSelectionPath(path);
    }
    else
      System.out.println("Oops: object not found: " + obj);
  }
}
