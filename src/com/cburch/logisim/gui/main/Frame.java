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
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.draw.toolbar.ToolbarModel;
import com.cburch.hdl.HdlModel;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.appear.AppearanceView;
import com.cburch.logisim.gui.generic.AttrTable;
import com.cburch.logisim.gui.generic.AttrTableModel;
import com.cburch.logisim.gui.generic.BasicZoomModel;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.generic.CardPanel;
import com.cburch.logisim.gui.generic.HelpTabContent;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.RegTabContent;
import com.cburch.logisim.gui.generic.ZoomControl;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.StateStore;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.hdl.HdlContentView;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Pokable;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.HorizontalSplitPane;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.VerticalSplitPane;

public class Frame extends LFrame.MainWindow implements LocaleListener {
  class MyProjectListener implements ProjectListener, LibraryListener,
        CircuitListener, ChangeListener {

    public void attributeListChanged(AttributeEvent e) {
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      if (event.getAction() == CircuitEvent.ACTION_SET_NAME) {
        computeTitle();
      }
    }

    private void enableSave() {
      boolean ok = getProject().isFileDirty();
      getRootPane().putClientProperty("windowModified", ok);
      getRootPane().putClientProperty("Window.documentModified", ok); // MacOS window decoration hint
    }

    @Override
    public void libraryChanged(LibraryEvent e) {
      if (e.getAction() == LibraryEvent.SET_NAME) {
        computeTitle();
      } else if (e.getAction() == LibraryEvent.DIRTY_STATE) {
        enableSave();
      }
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();

      if (action == ProjectEvent.ACTION_SET_FILE) {
        computeTitle();
        boolean avoidPoke = !hasPokableComponents();
        project.setTool(project.getOptions().getToolbarData().getFirstTool(avoidPoke));
        placeToolbar();
      } else if (action == ProjectEvent.ACTION_SET_STATE) {
        if (event.getData() instanceof CircuitState) {
          CircuitState state = (CircuitState)event.getData();
          if (state.getParentState() != null)
            topTab.setSelectedIndex(1); // sim explorer view
        }
      } else if (action == ProjectEvent.ACTION_SET_CURRENT) {
        if (event.getData() instanceof Circuit) {
          setEditorView(EDIT_LAYOUT);
          if (appearance != null)
            appearance.setCircuit(project, project.getCircuitState());
        } else if (event.getData() instanceof HdlModel) {
          setHdlEditorView((HdlModel)event.getData());
        }
        viewAttributes(null, null, true);
        computeTitle();
      } else if (action == ProjectEvent.ACTION_SET_TOOL) {
        if (attrTable == null)
          return; // for startup
        Tool oldTool = (Tool) event.getOldData();
        Tool newTool = (Tool) event.getData();
        if (!getEditorView().equals(EDIT_APPEARANCE))
          viewAttributes(oldTool, newTool, false);
      }
    }

    @Override
    public void stateChanged(ChangeEvent event) {
      Object source = event.getSource();
      if (source == mainPanel) {
        firePropertyChange(EDITOR_VIEW, "???", getEditorView());
      }
    }
  }

  class MyWindowListener extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent e) {
      if (confirmClose(S.get("confirmCloseTitle"))) {
        layoutCanvas.closeCanvas();
        // frameClosing() is needed to record window position, which may be used
        // in savePreferences() later. Normally Projects has a listener that
        // invokes frameClosing() directly, but that may not be called before
        // this listener, depending on AWT ordering of listeners, and the
        // location must be recorded before the dispose call.
        Projects.frameClosing(Frame.this);
        Frame.this.dispose();
      }
    }

    @Override
    public void windowOpened(WindowEvent e) {
      layoutCanvas.computeSize(true);
    }
  }

  // This code is disabled as it is not maintained and does not currently work.
  // private class VhdlSimState extends JPanel implements VhdlSimulatorListener {
  // import java.awt.Graphics2D;
  // import java.awt.Graphics;
  // import java.awt.geom.Ellipse2D;
  // import javax.swing.border.EmptyBorder;

  //   private static final long serialVersionUID = 1L;
  //   Ellipse2D.Double circle;
  //   Color color;
  //   private int margin = 5;

  //   public VhdlSimState() {
  //     int radius = 15;
  //     circle = new Ellipse2D.Double(margin, margin, radius, radius);
  //     setOpaque(false);
  //     color = Color.GRAY;
  //     this.setBorder(new EmptyBorder(margin, margin, margin, margin));
  //   }

  //   public Dimension getPreferredSize() {
  //     Rectangle bounds = circle.getBounds();
  //     return new Dimension(bounds.width + 2 * margin, bounds.height + 2
  //         * margin);
  //   }

  //   public void paintComponent(Graphics g) {
  //     super.paintComponent(g);
  //     Graphics2D g2 = (Graphics2D) g;
  //     g2.setColor(color);
  //     g2.fill(circle);
  //   }

  //   @Override
  //   public void stateChanged() {

  //     switch (project.getVhdlSimulator().getState()) {
  //     case DISABLED:
  //       color = Color.GRAY;
  //       break;
  //     case ENABLED:
  //       color = Color.RED;
  //       break;
  //     case STARTING:
  //       color = Color.ORANGE;
  //       break;
  //     case RUNNING:
  //       color = new Color(40, 180, 40);
  //       break;
  //     }

  //     this.repaint();

  //     // this.setText("VHDL Sim : " +
  //     // project.getSimulator().getCircuitState().getVhdlSimulator().getState());
  //   }

  // }

  private static Point getInitialLocation() {
    int x = StateStore.WINDOW_X.get();
    int y = StateStore.WINDOW_Y.get();
    try {
      while (isProjectFrameAt(x, y)) {
        x += 20;
        y += 20;
      }
      Rectangle desired = new Rectangle(x, y, 50, 50);

      int gcBestSize = 0;
      Point gcBestPoint = null;
      GraphicsEnvironment ge;
      ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      for (GraphicsDevice gd : ge.getScreenDevices()) {
        for (GraphicsConfiguration gc : gd.getConfigurations()) {
          Rectangle gcBounds = gc.getBounds();
          if (gcBounds.intersects(desired)) {
            Rectangle inter = gcBounds.intersection(desired);
            int size = inter.width * inter.height;
            if (size > gcBestSize) {
              gcBestSize = size;
              int x2 = Math.max(
                  gcBounds.x,
                  Math.min(inter.x, inter.x + inter.width - 50));
              int y2 = Math.max(
                  gcBounds.y,
                  Math.min(inter.y, inter.y + inter.height - 50));
              gcBestPoint = new Point(x2, y2);
            }
          }
        }
      }
      if (gcBestPoint != null) {
        if (isProjectFrameAt(gcBestPoint.x, gcBestPoint.y)) {
          gcBestPoint = null;
        }
      }
      return gcBestPoint;
    } catch (Throwable t) {
      return null;
    }
  }

  private static boolean isProjectFrameAt(int x, int y) {
    for (Project current : Projects.getOpenProjects()) {
      Frame frame = current.getFrame();
      if (frame != null) {
        Point loc = frame.getLocationOnScreen();
        int d = Math.abs(loc.x - x) + Math.abs(loc.y - y);
        if (d <= 3) {
          return true;
        }
      }
    }
    return false;
  }

  private static final long serialVersionUID = 1L;
  public static final String EDITOR_VIEW = "editorView";
  public static final String EXPLORER_VIEW = "explorerView";
  public static final String EDIT_LAYOUT = "layout";
  public static final String EDIT_APPEARANCE = "appearance";
  public static final String EDIT_HDL = "hdl";
  private static final double[] ZOOM_OPTIONS = {
    20, 33.3, 50, 70, 80, 90, 100, 125, 150, 200, 250, 300, 400, 800 };
  private MyProjectListener myProjectListener = new MyProjectListener();
  // GUI elements shared between views
  private MainMenuListener menuListener;
  private Toolbar toolbar;
  private HorizontalSplitPane leftRegion, rightRegion, editRegion;
  private VerticalSplitPane mainRegion;
  private JPanel rightPanel;
  private CardPanel mainPanel;
  // left-side elements
  private JTabbedPane topTab, bottomTab;
  private Toolbox toolbox;
  private SimulationExplorer simExplorer;
  private AttrTable attrTable;
  private RegTabContent regPanel;
  private HelpTabContent helpPanel;
  // private VhdlSimState vhdlSimState;
  private ZoomControl zoom;
  // for the Layout view
  private LayoutToolbarModel layoutToolbarModel;
  private Canvas layoutCanvas;
  // private VhdlSimulatorConsole vhdlSimulatorConsole;
  private HdlContentView hdlEditor;

  private ZoomModel layoutZoomModel;

  private LayoutEditHandler layoutEditHandler;

  private AttrTableSelectionModel attrTableSelectionModel;

  // for the Appearance view
  private AppearanceView appearance;

  // for VHDL Editor
  private ToolbarModel hdlToolbarModel;

  // private Double lastFraction = 0.75; // right-split default; see commented-out code below

  public Frame(Project project) {
    super(project);

    setBackground(Color.white);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(new MyWindowListener());

    project.addProjectWeakListener(null, myProjectListener);
    project.addLibraryWeakListener(null, myProjectListener);
    project.addCircuitWeakListener(null, myProjectListener);
    computeTitle();

    // set up elements for the Layout view
    layoutToolbarModel = new LayoutToolbarModel(this, project);
    layoutCanvas = new Canvas(project);
    layoutZoomModel = new BasicZoomModel(StateStore.LAYOUT_GRID.get(),
        StateStore.LAYOUT_ZOOM.get(), ZOOM_OPTIONS);

    layoutCanvas.getGridPainter().setZoomModel(layoutZoomModel);
    layoutEditHandler = new LayoutEditHandler(this);
    attrTableSelectionModel = new AttrTableSelectionModel(project, this);

    // set up menu bar and toolbar
    menuListener = new MainMenuListener(this, menubar);
    menuListener.setEditHandler(layoutEditHandler);
    toolbar = new Toolbar(layoutToolbarModel);

    // set up the left-side components
    toolbox = new Toolbox(project, this, menuListener);
    simExplorer = new SimulationExplorer(project, menuListener);

    bottomTab = new JTabbedPane();
    bottomTab.setFont(new Font("Dialog", Font.BOLD, 9));
    bottomTab.addTab("Properties", attrTable = new AttrTable(this));
    bottomTab.addTab("State", regPanel = new RegTabContent(this));
    bottomTab.addTab("Help", helpPanel = new HelpTabContent(this));
    if (Main.MacOS) // adjust space above/below
      bottomTab.setBorder(BorderFactory.createEmptyBorder(5, 0, -5, 0));

    // vhdlSimState = new VhdlSimState();
    // vhdlSimState.stateChanged();
    // project.getVhdlSimulator().addVhdlSimStateListener(vhdlSimState);

    zoom = new ZoomControl(layoutZoomModel, layoutCanvas);

    // set up the central area
    CanvasPane canvasPane = new CanvasPane(layoutCanvas);
    JPanel circEditor = new JPanel(new BorderLayout());
    canvasPane.setZoomModel(layoutZoomModel);
    mainPanel = new CardPanel();
    mainPanel.addView(EDIT_LAYOUT, canvasPane);
    mainPanel.setView(EDIT_LAYOUT);
    circEditor.add(mainPanel, BorderLayout.CENTER);

    // set up the contents, split down the middle, with the canvas
    // on the right and a split pane on the left containing the
    // explorer and attribute values.
    JPanel explPanel = new JPanel(new BorderLayout());
    explPanel.add(toolbox, BorderLayout.CENTER);

    JPanel simPanel = new JPanel(new BorderLayout());
    simPanel.add(simExplorer, BorderLayout.CENTER);

    topTab = new JTabbedPane();
    topTab.setFont(new Font("Dialog", Font.BOLD, 9));
    topTab.add("Design", explPanel); // index=0 
    topTab.add("Simulate", simPanel); // index=1 (see ACTION_SET_STATE below)
    // FIXME: on mac, JTabbedPane leaves too much whitespace here
    if (Main.MacOS) // adjust space above/below
      topTab.setBorder(BorderFactory.createEmptyBorder(5, 0, -5, 0));

    JPanel attrFooter = new JPanel(new BorderLayout());
    attrFooter.add(zoom);

    JPanel bottomTabAndZoom = new JPanel(new BorderLayout(0, 4));
    bottomTabAndZoom.add(bottomTab, BorderLayout.CENTER);
    bottomTabAndZoom.add(attrFooter, BorderLayout.SOUTH);

    leftRegion = new HorizontalSplitPane(topTab, bottomTabAndZoom,
        StateStore.WINDOW_LEFT_SPLIT.get());

    hdlEditor = new HdlContentView(project);
    // vhdlSimulatorConsole = new VhdlSimulatorConsole(project);
    editRegion = new HorizontalSplitPane(circEditor, hdlEditor, 1.0);
    rightRegion = editRegion; // new HorizontalSplitPane(editRegion, vhdlSimulatorConsole, 1.0); // ?

    rightPanel = new JPanel(new BorderLayout()); // panel where toolbar is attached
    rightPanel.add(rightRegion, BorderLayout.CENTER);

    mainRegion = new VerticalSplitPane(leftRegion, rightPanel,
        clamp(StateStore.WINDOW_MAIN_SPLIT.get(), 0.05, 0.80));

    getContentPane().add(mainRegion, BorderLayout.CENTER);

    computeTitle();

    this.setSize(StateStore.WINDOW_WIDTH.get(), StateStore.WINDOW_HEIGHT.get());
    Point prefPoint = getInitialLocation();
    if (prefPoint != null) {
      this.setLocation(prefPoint);
    }
    this.setExtendedState(StateStore.WINDOW_STATE.get());

    menuListener.register(mainPanel);
    KeyboardToolSelection.register(toolbar);

    project.setFrame(this);
    if (project.getTool() == null) {
      boolean avoidPoke = !hasPokableComponents();
      project.setTool(project.getOptions().getToolbarData().getFirstTool(avoidPoke));
    }
    mainPanel.addChangeListener(myProjectListener);
    AppPreferences.TOOLBAR_PLACEMENT.addPrefChangeWeakListener(this, e -> placeToolbar());
    placeToolbar();

    LocaleManager.addLocaleListener(this);
    toolbox.updateStructure();
  }
  
  private boolean hasPokableComponents() {
    Circuit circuit = project.getCurrentCircuit();
    if (circuit == null)
      return false;
    for (Component comp : circuit.getComponents()) {
      if (comp.getFeature(Pokable.class) != null)
        return true;
    }
    return false;
  }

  private void computeTitle() {
    String s;
    Circuit circuit = project.getCurrentCircuit();
    HdlModel hdl = project.getCurrentHdl();
    String name = project.getLogisimFile().getName();
    if (circuit != null)
      s = S.fmt("titleCircFileKnown", circuit.getName(), name);
    else if (hdl != null)
      s = S.fmt("titleCircFileKnown", hdl.getName(), name);
    else
      s = S.fmt("titleFileKnown", name);
    this.setTitle(s + " (v " + Main.VERSION_NAME + ")");
    myProjectListener.enableSave();
  }

  public boolean confirmClose() {
    return confirmClose(S.get("confirmCloseTitle"));
  }

  // returns true if user is OK with proceeding
  public boolean confirmClose(String title) {
    String message = S.fmt("confirmDiscardMessage", project.getLogisimFile().getName());

    if (!project.isFileDirty()) {
      return true;
    }
    toFront();
    String[] options = {
      S.get("saveOption"),
      S.get("discardOption"),
      S.get("cancelOption") };
    int result = JOptionPane.showOptionDialog(this, message, title, 0,
        JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    boolean ret;
    if (result == 0) {
      ret = ProjectActions.doSave(project);
    } else if (result == 1) {
      dispose(); // close the current project
      ProjectActions.removeAutoBackup(project);
      ret = true;
    } else {
      ret = false;
    }

    return ret;
  }

  public Canvas getCanvas() {
    return layoutCanvas;
  }

  public String getEditorView() {
    return (getHdlEditorView() != null ? EDIT_HDL : mainPanel.getView());
  }

  // public VhdlSimulatorConsole getVhdlSimulatorConsole() {
  //   return vhdlSimulatorConsole;
  // }

  public ZoomModel getZoomModel() {
    return layoutZoomModel;
  }

  @Override
  public void localeChanged() {
    computeTitle();
  }

  private void placeToolbar() {
    String loc = AppPreferences.TOOLBAR_PLACEMENT.get();
    Container contents = getContentPane();
    contents.remove(toolbar);
    rightPanel.remove(toolbar);

    if (AppPreferences.TOOLBAR_DOWN_MIDDLE.equals(loc)) {
      toolbar.setPosition(AppPreferences.TOOLBAR_DOWN_MIDDLE);
      rightPanel.add(toolbar, BorderLayout.WEST);
    } else if (Direction.NORTH.toString().equals(loc)) {
      toolbar.setPosition(Direction.NORTH);
      rightPanel.add(toolbar, BorderLayout.NORTH);
    } else if (Direction.SOUTH.toString().equals(loc)) {
      toolbar.setPosition(Direction.SOUTH);
      rightPanel.add(toolbar, BorderLayout.SOUTH);
    } else if (Direction.EAST.toString().equals(loc)) {
      toolbar.setPosition(Direction.EAST);
      rightPanel.add(toolbar, BorderLayout.EAST);
    } else if (Direction.WEST.toString().equals(loc)) {
      toolbar.setPosition(Direction.WEST);
      contents.add(toolbar, BorderLayout.WEST);
    }
    contents.validate();
  }

  public void savePreferences() {
    StateStore.TICK_FREQ.set(project.getSimulator().getTickFrequency());
    StateStore.LAYOUT_GRID.set(layoutZoomModel.getShowGrid());
    StateStore.LAYOUT_ZOOM.set(layoutZoomModel.getZoomFactor());
    if (appearance != null) {
      ZoomModel aZoom = appearance.getZoomModel();
      StateStore.APPEARANCE_GRID.set(aZoom.getShowGrid());
      StateStore.APPEARANCE_ZOOM.set(aZoom.getZoomFactor());
    }
    int state = getExtendedState() & ~JFrame.ICONIFIED;
    StateStore.WINDOW_STATE.set(state);
    Dimension dim = getSize();
    StateStore.WINDOW_WIDTH.set(dim.width);
    StateStore.WINDOW_HEIGHT.set(dim.height);
    Point loc;
    try {
      loc = getLocationOnScreen();
    } catch (IllegalComponentStateException e) {
      loc = Projects.getLocation(this);
    }
    if (loc != null) {
      StateStore.WINDOW_X.set(loc.x);
      StateStore.WINDOW_Y.set(loc.y);
    }
    StateStore.WINDOW_LEFT_SPLIT.set(leftRegion.getFraction());
    StateStore.WINDOW_MAIN_SPLIT.set(clamp(mainRegion.getFraction(), 0.05, 0.80));
    StateStore.DIALOG_DIRECTORY.set(JFileChoosers.getCurrentDirectory());
    StateStore.save();
  }

  void setAttrTableModel(AttrTableModel value) {
    attrTable.setAttrTableModel(value);
    boolean helped = false;
    if (value instanceof AttrTableToolModel) {
      Tool tool = ((AttrTableToolModel) value).getTool();
      toolbox.setHaloedTool(tool);
      layoutToolbarModel.setHaloedTool(tool);
      helpPanel.view(tool);
      helped = true;
    } else {
      toolbox.setHaloedTool(null);
      layoutToolbarModel.setHaloedTool(null);
    }
    if (value instanceof AttrTableComponentModel) {
      Circuit circ = ((AttrTableComponentModel) value).getCircuit();
      Component comp = ((AttrTableComponentModel) value).getComponent();
      layoutCanvas.setHaloedComponent(circ, comp);
      helpPanel.view(comp);
      helped = true;
    } else {
      layoutCanvas.setHaloedComponent(null, null);
    }
    if (!helped && value instanceof AttrTableSelectionModel) {
      Component choice = null;
      for (Component sel: getCanvas().getSelection().getComponents()) {
        if (choice == null) {
          choice = sel;
        } else if (choice.getFactory() != sel.getFactory()) {
          choice = null;
          break;
        }
      }
      if (choice != null) {
        helpPanel.view(choice);
        helped = true;
      }
    }
    if (!helped)
      helpPanel.view(project.getTool());
  }

  public void setEditorView(String view) {
    String curView = mainPanel.getView();
    if (hdlEditor.getHdlModel() == null && curView.equals(view))
      return;
    editRegion.forceFraction(1.0);
    hdlEditor.setHdlModel(null);

    if (view.equals(EDIT_APPEARANCE)) { // appearance view
      AppearanceView app = appearance;
      if (app == null) {
        app = new AppearanceView();
        app.setCircuit(project, project.getCircuitState());
        mainPanel.addView(EDIT_APPEARANCE, app.getCanvasPane());
        appearance = app;
      }
      toolbar.setToolbarModel(app.getToolbarModel());
      app.getAttrTableDrawManager(attrTable).attributesSelected();
      zoom.setZoomModel(app.getZoomModel(), app.getCanvas());
      menuListener.setEditHandler(app.getEditHandler());
      mainPanel.setView(view);
      app.getCanvas().requestFocus();
    } else { // layout view
      toolbar.setToolbarModel(layoutToolbarModel);
      zoom.setZoomModel(layoutZoomModel, layoutCanvas);
      menuListener.setEditHandler(layoutEditHandler);
      viewAttributes(null, project.getTool(), true);
      mainPanel.setView(view);
      layoutCanvas.requestFocus();
    }
  }

  public void setEditHandler(EditHandler handler) {
    if (handler != null) {
      menuListener.setEditHandler(handler);
    } else if (getEditorView().equals(EDIT_APPEARANCE)) {
      menuListener.setEditHandler(appearance.getEditHandler());
    } else {
      menuListener.setEditHandler(layoutEditHandler);
    }
  }

  // public void setVhdlSimulatorConsoleStatus(boolean visible) {
  //   if (visible) {
  //     rightRegion.setFraction(lastFraction);
  //   } else {
  //     lastFraction = rightRegion.getFraction();
  //     rightRegion.forceFraction(1);
  //   }
  // }

  private void setHdlEditorView(HdlModel hdl) {
    hdlEditor.setHdlModel(hdl);
    editRegion.forceFraction(0.0);

    toolbar.setToolbarModel(hdlEditor.getToolbarModel());
    // toolbar.setToolbarModel(app.getToolbarModel());
    // app.getAttrTableDrawManager(attrTable).attributesSelected();
    // zoom.setZoomModel(app.getZoomModel());
    // menuListener.setEditHandler(app.getEditHandler());
    // mainPanel.setView(view);
    // app.getCanvas().requestFocus();
  }

  public HdlModel getHdlEditorView() {
    return hdlEditor.getHdlModel();
  }

  public void viewAttributes(Library lib) {
    setAttrTableModel(new AttrTableLibraryModel(project, lib));
  }

  void viewAttributes(Tool newTool) {
    viewAttributes(null, newTool, false);
  }

  // private void viewAttributes(Tool newTool, boolean force) {
  //   viewAttributes(null, newTool, force);
  // }

  private void viewAttributes(Tool oldTool, Tool newTool, boolean force) {
    if (newTool == null && !force)
      return;
    AttributeSet newAttrs = newTool == null ? null : newTool.getAttributeSet(layoutCanvas);
    if (newAttrs == null) {
      // AttrTableModel oldModel = attrTable.getAttrTableModel();
      // boolean same = oldModel instanceof AttrTableToolModel
      //     && ((AttrTableToolModel) oldModel).getTool() == oldTool;
      // if (!force && !same && !(oldModel instanceof AttrTableCircuitModel)) {
      //   System.out.println("skip view attr");
      //   System.out.printf("oldTool = %s\n", oldTool);
      //   System.out.printf("newTool = %s\n", newTool);
      //   System.out.printf("oldModel = %s\n", oldModel);
      //   if (oldModel instanceof AttrTableToolModel)
      //     System.out.printf("oldModel.tool = %s\n", ((AttrTableToolModel) oldModel).getTool());
      //   Thread.dumpStack();
      //   // return;
      // }
      Circuit circ = project.getCurrentCircuit();
      HdlModel hdl = project.getCurrentHdl();
      if (circ != null)
        setAttrTableModel(new AttrTableCircuitModel(project, circ));
      else if (hdl != null && hdl instanceof VhdlContent)
        setAttrTableModel(new AttrTableVhdlModel(project, (VhdlContent)hdl));
      else if (force)
        setAttrTableModel(null);
    } else if (newAttrs instanceof SelectionAttributes) {
      setAttrTableModel(attrTableSelectionModel);
    } else {
      setAttrTableModel(new AttrTableToolModel(project, newTool));
    }
  }

  public void viewComponentAttributes(Circuit circ, Component comp) {
    if (comp == null) {
      setAttrTableModel(null);
    } else {
      setAttrTableModel(new AttrTableComponentModel(project, circ, comp));
    }
  }

  public void clearExplorerSelection() {
    if (toolbox != null)
      toolbox.clearExplorerSelection();
  }

  public Library getSelectedToolboxLibrary() {
    return toolbox == null ? null : toolbox.getSelectedLibrary();
  }

  public Tool getSelectedToolboxTool() {
    return toolbox == null ? null : toolbox.getSelectedTool();
  }

  public void fireLibrarySelected() {
    layoutEditHandler.computeEnabled();
  }

  public void revealInExplorer(List<Library> libPath, Object obj) {
    if (toolbox != null)
      toolbox.reveal(libPath, obj);
  }

  public HdlContentView getHdlContentView() {
    return hdlEditor;
  }

  public double clamp(double val, double min, double max) {
    return Math.min(Math.max(val, min), max);
  }
}
