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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.event.MouseInputListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.MouseMappings;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.generic.CanvasPaneContents;
import com.cburch.logisim.gui.generic.GridPainter;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

public class Canvas extends JPanel
  implements LocaleListener, CanvasPaneContents {

  private class MyListener implements MouseInputListener, KeyListener,
          PopupMenuListener, PropertyChangeListener, MouseWheelListener {

    boolean menu_on = false;

    private Tool getToolFor(MouseEvent e) {
      if (menu_on) {
        return null;
      }

      Tool ret = mappings.getToolFor(e);
      if (ret == null) {
        return proj.getTool();
      } else {
        return ret;
      }
    }

    //
    // KeyListener methods
    //
    @Override
    public void keyPressed(KeyEvent e) {
      Tool tool = proj.getTool();
      if (tool != null) {
        tool.keyPressed(Canvas.this, e);
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {
      Tool tool = proj.getTool();
      if (tool != null) {
        tool.keyReleased(Canvas.this, e);
      }
    }

    @Override
    public void keyTyped(KeyEvent e) {
      Tool tool = proj.getTool();
      if (tool != null) {
        tool.keyTyped(Canvas.this, e);
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      Tool tool = getToolFor(e);
      if (tool != null)
        tool.mouseClicked(Canvas.this, getGraphics(), e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (drag_tool != null)
        drag_tool.mouseDragged(Canvas.this, getGraphics(), e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      if (drag_tool != null) {
        drag_tool.mouseEntered(Canvas.this, getGraphics(), e);
      } else {
        Tool tool = getToolFor(e);
        if (tool != null) {
          tool.mouseEntered(Canvas.this, getGraphics(), e);
        }
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (drag_tool != null) {
        drag_tool.mouseExited(Canvas.this, getGraphics(), e);
      } else {
        Tool tool = getToolFor(e);
        if (tool != null) {
          tool.mouseExited(Canvas.this, getGraphics(), e);
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if ((e.getModifiersEx() & BUTTONS_MASK) != 0) {
        // If the control key is down while the mouse is being
        // dragged, mouseMoved is called instead. This may well be
        // an issue specific to the MacOS Java implementation,
        // but it exists there in the 1.4 and 5.0 versions.
        mouseDragged(e);
        return;
      }

      Tool tool = getToolFor(e);
      if (tool != null) {
        tool.mouseMoved(Canvas.this, getGraphics(), e);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      viewport.setErrorMessage(null, null);
      proj.setStartupScreen(false);
      proj.getFrame().clearExplorerSelection();
      Canvas.this.requestFocus();
      drag_tool = getToolFor(e);
      if (drag_tool != null)
        drag_tool.mousePressed(Canvas.this, getGraphics(), e);

      completeAction();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (drag_tool != null) {
        drag_tool.mouseReleased(Canvas.this, getGraphics(), e);
        drag_tool = null;
      }

      Tool tool = proj.getTool();
      if (tool != null) {
        tool.mouseMoved(Canvas.this, getGraphics(), e);
      }

      completeAction();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {
      Tool tool = proj.getTool();
      if (!mwe.isControlDown() &&
          tool != null && tool instanceof PokeTool && ((PokeTool)tool).isScrollable()) {
        int id = (mwe.getWheelRotation() < 0) ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
        KeyEvent e = new KeyEvent(mwe.getComponent(), KeyEvent.KEY_PRESSED,
            mwe.getWhen(), 0, id, '\0');
        tool.keyPressed(Canvas.this, e);
      } else {
        canvasPane.mouseWheelMoved(mwe, true);
      }
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
      menu_on = false;
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      menu_on = false;
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (AppPreferences.GATE_SHAPE.isSource(event)
          || AppPreferences.SHOW_TICK_RATE.isSource(event)) {
        paintThread.requestRepaint();
      } else if (AppPreferences.COMPONENT_TIPS.isSource(event)) {
        boolean showTips = AppPreferences.COMPONENT_TIPS.get();
        setToolTipText(showTips ? "" : null);
      }
    }

    private int scrollValue(JScrollBar bar, int val) {
      if (val > 0) {
        if (bar.getValue() < bar.getMaximum() + val * 2
            * bar.getBlockIncrement()) {
          return bar.getValue() + val * 2 * bar.getBlockIncrement();
        }
      } else {
        if (bar.getValue() > bar.getMinimum() + val * 2
            * bar.getBlockIncrement()) {
          return bar.getValue() + val * 2 * bar.getBlockIncrement();
        }
      }
      return 0;
    }
  }

  private class MyProjectListener implements ProjectListener,
          LibraryListener, CircuitListener, AttributeListener,
          Simulator.Listener, Selection.Listener {

    @Override
    public void attributeListChanged(AttributeEvent e) {
    }

    @Override
    public void attributeValueChanged(AttributeEvent e) {
      Attribute<?> attr = e.getAttribute();
      if (attr == Options.ATTR_GATE_UNDEFINED) {
        CircuitState circState = getCircuitState();
        circState.markComponentsDirty(getCircuit().getNonWires());
        // TODO actually, we'd want to mark all components in
        // subcircuits as dirty as well
      }
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      int act = event.getAction();
      if (act == CircuitEvent.ACTION_REMOVE) {
        Component c = (Component) event.getData();
        if (c == painter.getHaloedComponent()) {
          proj.getFrame().viewComponentAttributes(null, null);
        }
      } else if (act == CircuitEvent.ACTION_CLEAR) {
        if (painter.getHaloedComponent() != null) {
          proj.getFrame().viewComponentAttributes(null, null);
        }
      } else if (act == CircuitEvent.ACTION_INVALIDATE) {
        completeAction();
      }
    }

    private Tool pickSomeTool(List<? extends Tool> opts) {
      Tool ret = null;
      for (Tool o : opts) {
        if (ret == null && o != null) {
          ret = o;
        } else if (o instanceof EditTool) {
          ret = o;
        }
      }
      return ret;
    }

    @Override
    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.REMOVE_TOOL) {
        Object tool = event.getData();
        Circuit circ = null;
        if (tool instanceof AddTool) {
          ComponentFactory f = ((AddTool) tool).getFactory();
          if (f instanceof SubcircuitFactory)
            circ = ((SubcircuitFactory) f).getSubcircuit();
        }

        if (circ == proj.getCurrentCircuit() && circ != null) {
          proj.setCurrentCircuit(proj.getLogisimFile().getMainCircuit());
        }

        if (proj.getTool() == tool) {
          Tool next = pickSomeTool(
              proj.getLogisimFile().getOptions().getToolbarData().getContents());
          if (next == null) {
            for (Library lib : proj.getLogisimFile().getLibraries()) {
              next = pickSomeTool(lib.getTools());
              if (next != null)
                break;
            }
          }
          proj.setTool(next);
        }

        if (circ != null) {
          CircuitState state = getCircuitState();
          CircuitState last = state;
          while (state != null && state.getCircuit() != circ) {
            last = state;
            state = state.getParentState();
          }
          if (state != null)
            getProject().setCircuitState(last.cloneAsNewRootState());
        }
      }
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      int act = event.getAction();
      if (act == ProjectEvent.ACTION_SET_CURRENT) {
        viewport.setErrorMessage(null, null);
        if (painter.getHaloedComponent() != null) {
          proj.getFrame().viewComponentAttributes(null, null);
        }
      } else if (act == ProjectEvent.ACTION_SET_FILE) {
        LogisimFile old = (LogisimFile) event.getOldData();
        if (old != null) {
          old.getOptions().getAttributeSet().removeAttributeWeakListener(null, this);
        }
        LogisimFile file = (LogisimFile) event.getData();
        if (file != null) {
          AttributeSet attrs = file.getOptions().getAttributeSet();
          attrs.addAttributeWeakListener(null, this);
          loadOptions(attrs);
          mappings = file.getOptions().getMouseMappings();
        }
      } else if (act == ProjectEvent.ACTION_SET_TOOL) {
        viewport.setErrorMessage(null, null);

        Tool t = event.getTool();
        if (t == null) {
          setCursor(Cursor.getDefaultCursor());
        } else {
          setCursor(t.getCursor());
        }
      } else if (act == ProjectEvent.ACTION_SET_STATE) {
        CircuitState oldState = (CircuitState) event.getOldData();
        CircuitState newState = (CircuitState) event.getData();
        if (oldState != null && newState != null) {
          Propagator oldProp = oldState.getPropagator();
          Propagator newProp = newState.getPropagator();
          if (oldProp != newProp) {
            tickCounter.clear();
          }
        }
      }

      if (act != ProjectEvent.ACTION_SELECTION
          && act != ProjectEvent.ACTION_START
          && act != ProjectEvent.UNDO_START) {
        completeAction();
      }
    }

    @Override
    public void selectionChanged(Selection.Event event) {
      repaint();
    }

    @Override
    public void propagationCompleted(Simulator.Event e) {
      paintThread.requestRepaint();
      // if (e.didTick())
      //   waitForRepaintDone();
    }

    @Override
    public void simulatorStateChanged(Simulator.Event e) {
    }

    @Override
    public void simulatorReset(Simulator.Event e) {
      waitForRepaintDone();
    }
  }

  private class MyViewport extends JViewport {

    private static final long serialVersionUID = 1L;
    StringGetter errorMessage = null;
    String widthMessage = null;
    Color errorColor = DEFAULT_ERROR_COLOR;
    boolean isNorth = false;
    boolean isSouth = false;
    boolean isWest = false;
    boolean isEast = false;
    boolean isNortheast = false;
    boolean isNorthwest = false;
    boolean isSoutheast = false;
    boolean isSouthwest = false;

    MyViewport() {
    }

    @Override
    public Color getBackground() {
      return getView() == null ? super.getBackground() : getView()
          .getBackground();
    }

    @Override
    public void paintChildren(Graphics g) {
      super.paintChildren(g);
      paintContents(g);
    }

    void paintContents(Graphics g) {
      int msgY = getHeight() - 23;
      StringGetter message = errorMessage;
      if (message != null) {
        g.setColor(errorColor);
        msgY = paintString(g, msgY, message.toString());
      }

      if (proj.getSimulator().isOscillating()) {
        g.setColor(OSC_ERR_COLOR);
        msgY = paintString(g, msgY, S.get("canvasOscillationError"));
      }

      if (proj.getSimulator().isExceptionEncountered()) {
        g.setColor(SIM_EXCEPTION_COLOR);
        msgY = paintString(g, msgY, S.get("canvasExceptionError"));
      }

      computeViewportContents();
      Dimension sz = getSize();
      g.setColor(Value.WIDTH_ERROR_COLOR);

      if (widthMessage != null) {
        msgY = paintString(g, msgY, widthMessage);
      }

      int w = sz.width;
      int h = sz.height;
      int s = 25; // stem width
      int l = 35; // head length
      int a = 60; // head angle
      if (isNorth)
        GraphicsUtil.drawArrow(g, w/2, 24, w/2, 2, s, l, a);
      if (isSouth)
        GraphicsUtil.drawArrow(g, w/2, h - 24, w/2, h - 2, s, l, a);
      if (isEast)
        GraphicsUtil.drawArrow(g, w - 24, h/2, w - 2, h/2, s, l, a);
      if (isWest)
        GraphicsUtil.drawArrow(g, 24, h/2, 2, h/2, s, l, a);
      if (isNortheast)
        GraphicsUtil.drawArrow(g, w - 28, 28, w - 12, 12, s, l, a);
      if (isNorthwest)
        GraphicsUtil.drawArrow(g, 28, 28, 12, 12, s, l, a);
      if (isSoutheast)
        GraphicsUtil.drawArrow(g, w - 28, h - 28, w - 12, h - 12, s, l, a);
      if (isSouthwest)
        GraphicsUtil.drawArrow(g, 28, h - 28, 12, h - 12, s, l, a);

      if (AppPreferences.SHOW_TICK_RATE.get()) {
        String hz = tickCounter.getTickRate();
        if (hz != null && !hz.equals("")) {
          g.setColor(TICK_RATE_COLOR);
          g.setFont(TICK_RATE_FONT);
          FontMetrics fm = g.getFontMetrics();
          int x = getWidth() - fm.stringWidth(hz) - 5;
          int y = fm.getAscent() + 5;
          g.drawString(hz, x, y);
        }
      }

      GraphicsUtil.switchToWidth(g, 1);

      if (!proj.getSimulator().isAutoPropagating()) {
        g.setColor(SINGLE_STEP_MSG_COLOR);
        Font old = g.getFont();
        g.setFont(SINGLE_STEP_MSG_FONT);
        g.drawString(proj.getSimulator().getSingleStepMessage(), 10, 15);
        g.setFont(old);
      }

      g.setColor(Color.BLACK);

    }

    private int paintString(Graphics g, int y, String msg) {
      Font old = g.getFont();
      g.setFont(ERR_MSG_FONT);
      FontMetrics fm = g.getFontMetrics();
      int x = (getWidth() - fm.stringWidth(msg)) / 2;
      if (x < 0) {
        x = 0;
      }
      g.drawString(msg, x, y);
      g.setFont(old);
      return y - 23;
    }

    void setEast(boolean value) {
      isEast = value;
    }

    void setErrorMessage(StringGetter msg, Color color) {
      if (errorMessage != msg) {
        errorMessage = msg;
        errorColor = color == null ? DEFAULT_ERROR_COLOR : color;
        paintThread.requestRepaint();
      }
    }

    void setNorth(boolean value) {
      isNorth = value;
    }

    void setNortheast(boolean value) {
      isNortheast = value;
    }

    void setNorthwest(boolean value) {
      isNorthwest = value;
    }

    void setSouth(boolean value) {
      isSouth = value;
    }

    void setSoutheast(boolean value) {
      isSoutheast = value;
    }

    void setSouthwest(boolean value) {
      isSouthwest = value;
    }

    void setWest(boolean value) {
      isWest = value;
    }

    void setWidthMessage(String msg) {
      widthMessage = msg;
      isNorth = false;
      isSouth = false;
      isWest = false;
      isEast = false;
      isNortheast = false;
      isNorthwest = false;
      isSoutheast = false;
      isSouthwest = false;
    }

  }

  public static void snapToGrid(MouseEvent e) {
    int old_x = e.getX();
    int old_y = e.getY();
    int new_x = snapXToGrid(old_x);
    int new_y = snapYToGrid(old_y);
    e.translatePoint(new_x - old_x, new_y - old_y);
  }

  //
  // static methods
  //
  public static int snapXToGrid(int x) {
    if (x < 0) {
      return -((-x + 5) / 10) * 10;
    } else {
      return ((x + 5) / 10) * 10;
    }
  }

  public static int snapYToGrid(int y) {
    if (y < 0) {
      return -((-y + 5) / 10) * 10;
    } else {
      return ((y + 5) / 10) * 10;
    }
  }

  private static final long serialVersionUID = 1L;
  public static final Color HALO_COLOR = new Color(255, 0, 255);
  // don't bother to update the size if it hasn't changed more than this
  static final double SQRT_2 = Math.sqrt(2.0);
  private static final int BOUNDS_BUFFER = 70;
  // pixels shown in canvas beyond outermost boundaries
  private static final int THRESH_SIZE_UPDATE = 10;
  private static final int BUTTONS_MASK = InputEvent.BUTTON1_DOWN_MASK
      | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;
  private static final Color DEFAULT_ERROR_COLOR = new Color(192, 0, 0);
  private static final Color OSC_ERR_COLOR = DEFAULT_ERROR_COLOR;
  private static final Color SIM_EXCEPTION_COLOR = DEFAULT_ERROR_COLOR;
  private static final Font ERR_MSG_FONT = new Font("Sans Serif", Font.BOLD, 18);
  private static final Color TICK_RATE_COLOR = new Color(0, 0, 92, 92);
  private static final Font TICK_RATE_FONT = new Font("Serif", Font.BOLD, 12);
  private static final Color SINGLE_STEP_MSG_COLOR = Color.BLUE;
  private static final Font SINGLE_STEP_MSG_FONT = new Font("Sans Serif", Font.BOLD, 12);

  private Project proj;
  private Tool drag_tool;
  private Selection selection;
  private MouseMappings mappings;
  private CanvasPane canvasPane;
  private Bounds oldPreferredSize;
  private MyListener myListener = new MyListener();
  private MyViewport viewport = new MyViewport();
  private MyProjectListener myProjectListener = new MyProjectListener();

  private TickCounter tickCounter;

  private CanvasPaintThread paintThread;

  private CanvasPainter painter;

  private volatile boolean paintDirty = false; // only for within paintComponent

  private volatile boolean inPaint = false; // only for within paintComponent

  private Object repaintLock = new Object(); // for waitForRepaintDone

  public Canvas(Project proj) {
    setLayout(null);
    this.proj = proj;
    this.selection = new Selection(proj, this);
    this.painter = new CanvasPainter(this);
    this.oldPreferredSize = null;
    this.paintThread = new CanvasPaintThread(this);
    this.mappings = proj.getOptions().getMouseMappings();
    this.canvasPane = null;
    this.tickCounter = new TickCounter();

    setBackground(Color.white);
    addMouseListener(myListener);
    addMouseMotionListener(myListener);
    setFocusTraversalKeysEnabled(false);
    addKeyListener(myListener);
    addMouseWheelListener(myListener);

    proj.addProjectWeakListener(null, myProjectListener);
    proj.addLibraryWeakListener(/*null,*/ myProjectListener);
    proj.addCircuitWeakListener(/*null,*/ myProjectListener);
    proj.getSimulator().addSimulatorListener(tickCounter);
    selection.addListener(myProjectListener);
    LocaleManager.addLocaleListener(this);

    AttributeSet options = proj.getOptions().getAttributeSet();
    options.addAttributeWeakListener(null, myProjectListener);
    AppPreferences.COMPONENT_TIPS.addPropertyChangeListener(myListener);
    AppPreferences.GATE_SHAPE.addPropertyChangeListener(myListener);
    AppPreferences.SHOW_TICK_RATE.addPropertyChangeListener(myListener);
    loadOptions(options);
    paintThread.start();
  }

  public void closeCanvas() {
    paintThread.requestStop();
  }

  private void completeAction() {
    if (proj.getCurrentCircuit() == null)
      return;
    computeSize(false);
    // After any interaction, nudge the simulator, which in autoPropagate mode
    // will (if needed) eventually, fire a propagateCompleted event, which will
    // cause a repaint. If not in autoPropagate mode, do the repaint here
    // instead.
    if (!proj.getSimulator().nudge())
      paintThread.requestRepaint();
  }

  public void computeSize(boolean immediate) {
    if (proj.getCurrentCircuit() == null)
      return;
    Bounds bounds = proj.getCurrentCircuit().getCircuitBounds(getGraphics());
    int width = bounds.getX() + bounds.getWidth() + BOUNDS_BUFFER;
    int height = bounds.getY() + bounds.getHeight() + BOUNDS_BUFFER;
    Dimension dim;
    if (canvasPane == null) {
      dim = new Dimension(width, height);
    } else {
      dim = canvasPane.supportPreferredSize(width, height);
    }
    if (!immediate) {
      Bounds old = oldPreferredSize;
      if (old != null
          && Math.abs(old.getWidth() - dim.width) < THRESH_SIZE_UPDATE
          && Math.abs(old.getHeight() - dim.height) < THRESH_SIZE_UPDATE) {
        return;
      }
    }
    oldPreferredSize = Bounds.create(0, 0, dim.width, dim.height);
    setPreferredSize(dim);
    revalidate();
  }

  public Rectangle getViewableRect() {
    Rectangle viewableBase;
    Rectangle viewable;
    if (canvasPane != null) {
      viewableBase = viewport.getViewRect();
    } else {
      Bounds bds = proj.getCurrentCircuit().getCircuitBounds(getGraphics());
      viewableBase = new Rectangle(0, 0, bds.getWidth(), bds.getHeight());
    }
    double zoom = getZoomFactor();
    if (zoom == 1.0) {
      viewable = viewableBase;
    } else {
      viewable = new Rectangle((int) (viewableBase.x / zoom),
          (int) (viewableBase.y / zoom),
          (int) (viewableBase.width / zoom),
          (int) (viewableBase.height / zoom));
    }
    return viewable;
  }

  private void computeViewportContents() {
    Set<WidthIncompatibilityData> exceptions = proj.getCurrentCircuit()
        .getWidthIncompatibilityData();
    if (exceptions == null || exceptions.isEmpty()) {
      viewport.setWidthMessage(null);
      return;
    }

    Rectangle viewable = getViewableRect();

    viewport.setWidthMessage(S.get("canvasWidthError")
        + (exceptions.size() == 1 ? "" : " (" + exceptions.size() + ")"));
    for (WidthIncompatibilityData ex : exceptions) {
      boolean isWithin = false;
      for (int i = 0; i < ex.size(); i++) {
        Location p = ex.getPoint(i);
        int x = p.getX();
        int y = p.getY();
        if (x >= viewable.x && x < viewable.x + viewable.width
            && y >= viewable.y && y < viewable.y + viewable.height) {
          isWithin = true;
          break;
        }
      }

      // If none are, insert an arrow.
      if (!isWithin) {
        Location p = ex.getPoint(0);
        int x = p.getX();
        int y = p.getY();
        boolean isWest = x < viewable.x;
        boolean isEast = x >= viewable.x + viewable.width;
        boolean isNorth = y < viewable.y;
        boolean isSouth = y >= viewable.y + viewable.height;

        if (isNorth) {
          if (isEast) {
            viewport.setNortheast(true);
          } else if (isWest) {
            viewport.setNorthwest(true);
          } else {
            viewport.setNorth(true);
          }
        } else if (isSouth) {
          if (isEast) {
            viewport.setSoutheast(true);
          } else if (isWest) {
            viewport.setSouthwest(true);
          } else {
            viewport.setSouth(true);
          }
        } else {
          if (isEast) {
            viewport.setEast(true);
          } else if (isWest) {
            viewport.setWest(true);
          }
        }
      }
    }
  }

  public Circuit getCircuit() {
    return proj.getCurrentCircuit();
  }

  public CircuitState getCircuitState() {
    return proj.getCircuitState();
  }

  Tool getDragTool() {
    return drag_tool;
  }

  public StringGetter getErrorMessage() {
    return viewport.errorMessage;
  }

  GridPainter getGridPainter() {
    return painter.getGridPainter();
  }

  Component getHaloedComponent() {
    return painter.getHaloedComponent();
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  public Project getProject() {
    return proj;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect,
      int orientation, int direction) {
    return canvasPane.supportScrollableBlockIncrement(visibleRect,
        orientation, direction);
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return false;
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect,
      int orientation, int direction) {
    return canvasPane.supportScrollableUnitIncrement(visibleRect,
        orientation, direction);
  }

  public Selection getSelection() {
    return selection;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    boolean showTips = AppPreferences.COMPONENT_TIPS.get();
    if (showTips) {
      Canvas.snapToGrid(event);
      Location loc = Location.create(event.getX(), event.getY());
      ComponentUserEvent e = null;
      for (Component comp : getCircuit().getAllContaining(loc)) {
        Object makerObj = comp.getFeature(ToolTipMaker.class);
        if (makerObj != null && makerObj instanceof ToolTipMaker) {
          ToolTipMaker maker = (ToolTipMaker) makerObj;
          if (e == null) {
            e = new ComponentUserEvent(this, loc.getX(), loc.getY());
          }
          String ret = maker.getToolTip(e);
          if (ret != null) {
            unrepairMouseEvent(event);
            return ret;
          }
        }
      }
    }
    return null;
  }

  //
  // graphics methods
  //
  double getZoomFactor() {
    CanvasPane pane = canvasPane;
    return pane == null ? 1.0 : pane.getZoomFactor();
  }

  boolean ifPaintDirtyReset() {
    if (paintDirty) {
      paintDirty = false;
      return false;
    } else {
      return true;
    }
  }

  boolean isPopupMenuUp() {
    return myListener.menu_on;
  }

  private void loadOptions(AttributeSet options) {
    boolean showTips = AppPreferences.COMPONENT_TIPS.get();
    setToolTipText(showTips ? "" : null);

    proj.getSimulator().removeSimulatorListener(myProjectListener);
    proj.getSimulator().addSimulatorListener(myProjectListener);
  }

  @Override
  public void localeChanged() {
    paintThread.requestRepaint();
  }

  @Override
  public void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    // g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
    //    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    inPaint = true;
    try {
      super.paintComponent(g);
      boolean clear = false;
      do {
        if (clear) {
          // Clear the screen so we don't get
          // artifacts due to aliasing (e.g. where
          // semi-transparent (gray) pixels on the
          // edges of a line turn woudl darker if
          // painted a second time.
          g.setColor(Color.WHITE);
          g.fillRect(0, 0, getWidth(), getHeight());
        }
        painter.paintContents(g, proj);
        clear = true;
      } while (paintDirty);
      if (canvasPane == null)
        viewport.paintContents(g);
    } finally {
      inPaint = false;
      synchronized (repaintLock) {
        repaintLock.notifyAll();
      }
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    repairMouseEvent(e);
    super.processMouseEvent(e);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    repairMouseEvent(e);
    super.processMouseMotionEvent(e);
  }

  @Override
  public void recomputeSize() {
    computeSize(true);
  }

  @Override
  public void repaint() {
    if (inPaint) {
      paintDirty = true;
    } else {
      super.repaint();
    }
  }

  @Override
  public void repaint(int x, int y, int width, int height) {
    double zoom = getZoomFactor();
    if (zoom < 1.0) {
      int newX = (int) Math.floor(x * zoom);
      int newY = (int) Math.floor(y * zoom);
      width += x - newX;
      height += y - newY;
      x = newX;
      y = newY;
    } else if (zoom > 1.0) {
      int x1 = (int) Math.ceil((x + width) * zoom);
      int y1 = (int) Math.ceil((y + height) * zoom);
      width = x1 - x;
      height = y1 - y;
    }
    super.repaint(x, y, width, height);
  }

  @Override
  public void repaint(Rectangle r) {
    double zoom = getZoomFactor();
    if (zoom == 1.0) {
      super.repaint(r);
    } else {
      this.repaint(r.x, r.y, r.width, r.height);
    }
  }

  private void repairMouseEvent(MouseEvent e) {
    double zoom = getZoomFactor();
    if (zoom != 1.0) {
      zoomEvent(e, zoom);
    }
  }

  //
  // CanvasPaneContents methods
  //
  @Override
  public void setCanvasPane(CanvasPane value) {
    canvasPane = value;
    canvasPane.setViewport(viewport);
    viewport.setView(this);
    setOpaque(false);
    computeSize(true);
  }

  public void setErrorMessage(StringGetter message) {
    viewport.setErrorMessage(message, null);
  }

  public void setErrorMessage(StringGetter message, Color color) {
    viewport.setErrorMessage(message, color);
  }

  public void setHaloedComponent(Circuit circ, Component comp) {
    painter.setHaloedComponent(circ, comp);
  }

  public void setHighlightedWires(WireSet value) {
    painter.setHighlightedWires(value);
  }

  public void showPopupMenu(JPopupMenu menu, int x, int y) {
    double zoom = getZoomFactor();
    if (zoom != 1.0) {
      x = (int) Math.round(x * zoom);
      y = (int) Math.round(y * zoom);
    }
    myListener.menu_on = true;
    menu.addPopupMenuListener(myListener);
    menu.show(this, x, y);
  }

  private void unrepairMouseEvent(MouseEvent e) {
    double zoom = getZoomFactor();
    if (zoom != 1.0) {
      zoomEvent(e, 1.0 / zoom);
    }
  }

  private void waitForRepaintDone() {
    synchronized (repaintLock) {
      try {
        while (inPaint) {
          repaintLock.wait();
        }
      } catch (InterruptedException e) {
      }
    }
  }

  private void zoomEvent(MouseEvent e, double zoom) {
    int oldx = e.getX();
    int oldy = e.getY();
    int newx = (int) Math.round(e.getX() / zoom);
    int newy = (int) Math.round(e.getY() / zoom);
    e.translatePoint(newx - oldx, newy - oldy);
  }

  public void zoomScrollTo(Rectangle r) {
    r.grow(40, 40); // give some wiggle room on all sides
    Rectangle v = getViewableRect();
    ZoomModel zoomModel = proj.getFrame().getZoomModel();
    double zoom = zoomModel.getZoomFactor();
    while ((v.width < r.width+40 || v.height < r.height) && zoom > 0.2) {
      zoom = Math.max(0.2, zoom - 0.1);
      zoomModel.setZoomFactor(zoom);
      v = getViewableRect();
    }
    if (v.contains(r))
      return;
    if (r.x < v.x)
      canvasPane.getHorizontalScrollBar().setValue((int)(zoom * r.x));
    else if (r.x + r.width > v.x + v.width)
      canvasPane.getHorizontalScrollBar().setValue((int)(zoom * (r.x + r.width - v.width)));
    if (r.y < v.y)
      canvasPane.getVerticalScrollBar().setValue((int)(zoom * r.y));
    else if (r.y + r.height > v.y + v.height)
      canvasPane.getVerticalScrollBar().setValue((int)(zoom * (r.y + r.height - v.height)));
  }

}
