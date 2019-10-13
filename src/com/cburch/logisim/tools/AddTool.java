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

package com.cburch.logisim.tools;
import static com.cburch.logisim.tools.Strings.S;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitException;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.XmlWriter;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.LayoutClipboard;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.gui.main.ToolAttributeAction;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Dependencies;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.key.KeyConfigurationEvent;
import com.cburch.logisim.tools.key.KeyConfigurationResult;
import com.cburch.logisim.tools.key.KeyConfigurator;
import com.cburch.logisim.util.DragDrop;
import com.cburch.logisim.file.LogisimFileActions;

public class AddTool extends Tool {

  private class MyAttributeListener implements AttributeListener {
    public void attributeListChanged(AttributeEvent e) { bounds = null; }
    public void attributeValueChanged(AttributeEvent e) { bounds = null; }
  }

  private static int INVALID_COORD = Integer.MIN_VALUE;
  private static int SHOW_NONE = 0;
  private static int SHOW_GHOST = 1;
  private static int SHOW_ADD = 2;
  private static int SHOW_ADD_NO = 3;

  private static Cursor cursor
      = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

  private Class<? extends Library> libraryClass;
  private FactoryDescription description;
  private boolean sourceLoadAttempted;
  private ComponentFactory factory;
  private AttributeSet attrs;
  private Bounds bounds;
  private boolean shouldSnap;
  private int lastX = INVALID_COORD;
  private int lastY = INVALID_COORD;
  private int state = SHOW_GHOST;
  private Action lastAddition;
  private boolean keyHandlerTried;
  private KeyConfigurator keyHandler;

  private AddTool(AddTool base) {
    this.libraryClass = base.libraryClass;
    this.description = base.description;
    this.sourceLoadAttempted = base.sourceLoadAttempted;
    this.factory = base.factory;
    this.bounds = base.bounds;
    this.shouldSnap = base.shouldSnap;
    if (base.isAllDefaultValues(base.attrs, null))
      this.attrs = new FactoryAttributes((FactoryAttributes)base.attrs);
    else
      this.attrs = (AttributeSet) base.attrs.clone();
    attrs.addAttributeWeakListener(this, new MyAttributeListener());
  }

  public AddTool(Class<? extends Library> libClass, FactoryDescription description) {
    this.libraryClass = libClass;
    this.description = description;
    this.sourceLoadAttempted = false;
    this.shouldSnap = true;
    this.attrs = new FactoryAttributes(libClass, description);
    attrs.addAttributeWeakListener(this, new MyAttributeListener());
    this.keyHandlerTried = false;
  }

  public AddTool(Class<? extends Library> libClass, ComponentFactory source) {
    this.libraryClass = libClass;
    this.description = null;
    this.sourceLoadAttempted = true;
    this.factory = source;
    this.bounds = null;
    this.attrs = new FactoryAttributes(source);
    attrs.addAttributeWeakListener(this, new MyAttributeListener());
    Boolean value = (Boolean) source.getFeature(
        ComponentFactory.SHOULD_SNAP, attrs);
    this.shouldSnap = value == null ? true : value.booleanValue();
  }

  // This constructor is deprecated. It is here for JAR-library backwards
  // compatibility with other verisions of Logisim-Evolution and/or original
  // Logisim.
	public AddTool(ComponentFactory source) {
    this(null, source);
    // The null libraryClass here is fixed in Loader.loadJarFile().
	}

  // This is used only to fix the null libraryClass from deprecated constructor
  // above.
  public void fixupLibraryClass(Class<? extends Library> libClass) {
    if (libraryClass == null)
      libraryClass = libClass;
  }

  public void cancelOp() {
  }

  @Override
  public Tool cloneTool() {
    return new AddTool(this);
  }

  @Override
  public int hashCode() {
    FactoryDescription desc = description;
    return desc != null ? desc.hashCode() : factory.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof AddTool))
      return false;
    AddTool o = (AddTool) other;
    if (this.description != null) {
      return this.libraryClass == o.libraryClass
          && this.description.equals(o.description);
    } else {
      return this.factory.equals(o.factory);
    }
  }

  @Override
  public void deselect(Canvas canvas) {
    setState(canvas, SHOW_GHOST);
    moveTo(canvas, canvas.getGraphics(), INVALID_COORD, INVALID_COORD);
    bounds = null;
    lastAddition = null;
  }

  private Tool determineNext(Project proj) {
    String afterAdd = AppPreferences.ADD_AFTER.get();
    if (afterAdd.equals(AppPreferences.ADD_AFTER_UNCHANGED)) {
      return null;
    } else { // switch to Edit Tool
      Library base = proj.getLogisimFile().getLibrary("Base");
      if (base == null) {
        return null;
      } else {
        return base.getTool("Edit Tool");
      }
    }
  }

  @Override
  public void draw(Canvas canvas, ComponentDrawContext context) {
    // next "if" suggested roughly by Kevin Walsh of Cornell to take care of
    // repaint problems on OpenJDK under Ubuntu
    int x = lastX;
    int y = lastY;
    if (x == INVALID_COORD || y == INVALID_COORD)
      return;
    ComponentFactory source = getFactory();
    if (source == null)
      return;
    if (state == SHOW_GHOST) {
      source.drawGhost(context, Color.GRAY, x, y, getBaseAttributes());
    } else if (state == SHOW_ADD) {
      source.drawGhost(context, Color.BLACK, x, y, getBaseAttributes());
    }
  }

  private void expose(java.awt.Component c, int x, int y) {
    Bounds bds = getBounds();
    c.repaint(x + bds.getX(), y + bds.getY(), bds.getWidth(),
        bds.getHeight());
  }

  @Override
  public AttributeSet getAttributeSet() {
    return attrs;
  }

  private AttributeSet getBaseAttributes() {
    AttributeSet ret = attrs;
    if (ret instanceof FactoryAttributes) {
      ret = ((FactoryAttributes) ret).getBase();
    }
    return ret;
  }

  private Bounds getBounds() {
    Bounds ret = bounds;
    if (ret == null) {
      ComponentFactory source = getFactory();
      if (source == null) {
        ret = Bounds.EMPTY_BOUNDS;
      } else {
        AttributeSet base = getBaseAttributes();
        ret = source.getOffsetBounds(base).expand(5);
      }
      bounds = ret;
    }
    return ret;
  }

  @Override
  public Cursor getCursor() {
    return cursor;
  }

  @Override
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    return getFactory().getDefaultAttributeValue(attr, ver);
  }

  @Override
  public String getDescription() {
    String ret;
    FactoryDescription desc = description;
    if (desc != null) {
      ret = desc.getToolTip();
    } else {
      ComponentFactory source = getFactory();
      if (source != null) {
        ret = (String) source.getFeature(ComponentFactory.TOOL_TIP,
            getAttributeSet());
      } else {
        ret = null;
      }
    }
    if (ret == null) {
      ret = S.fmt("addToolText", getDisplayName());
    }
    return ret;
  }

  @Override
  public String getDisplayName() {
    FactoryDescription desc = description;
    return desc == null ? factory.getDisplayName() : desc.getDisplayName();
  }

  public ComponentFactory getFactory() {
    ComponentFactory ret = factory;
    if (ret != null || sourceLoadAttempted) {
      return ret;
    } else {
      ret = description.getFactoryFromLibrary(libraryClass);
      if (ret != null) {
        AttributeSet base = getBaseAttributes();
        Boolean value = (Boolean) ret.getFeature(
            ComponentFactory.SHOULD_SNAP, base);
        shouldSnap = value == null ? true : value.booleanValue();
      }
      factory = ret;
      sourceLoadAttempted = true;
      return ret;
    }
  }

  public ComponentFactory getFactory(boolean forceLoad) {
    return forceLoad ? getFactory() : factory;
  }

  @Override
  public String getName() {
    FactoryDescription desc = description;
    return desc == null ? factory.getName() : desc.getName();
  }

  @Override
  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver) {
    return this.attrs == attrs && attrs instanceof FactoryAttributes
        && !((FactoryAttributes) attrs).isFactoryInstantiated();
  }

  @Override
  public void keyPressed(Canvas canvas, KeyEvent event) {
    processKeyEvent(canvas, event, KeyConfigurationEvent.KEY_PRESSED);

    if (!event.isConsumed() && event.getModifiersEx() == 0) {
      switch (event.getKeyCode()) {
      case KeyEvent.VK_ESCAPE:
        Project proj = canvas.getProject();
        Library base = proj.getLogisimFile().getLibrary("Base");
        if (base != null)
          proj.setTool(base.getTool("Edit Tool"));
        break;
      case KeyEvent.VK_UP:
        setFacing(canvas, Direction.NORTH);
        break;
      case KeyEvent.VK_DOWN:
        setFacing(canvas, Direction.SOUTH);
        break;
      case KeyEvent.VK_LEFT:
        setFacing(canvas, Direction.WEST);
        break;
      case KeyEvent.VK_RIGHT:
        setFacing(canvas, Direction.EAST);
        break;
      case KeyEvent.VK_DELETE:
      case KeyEvent.VK_BACK_SPACE:
        if (lastAddition != null
            && canvas.getProject().getLastAction() == lastAddition) {
          canvas.getProject().undoAction();
          lastAddition = null;
        }
      }
    }
  }

  @Override
  public void keyReleased(Canvas canvas, KeyEvent event) {
    processKeyEvent(canvas, event, KeyConfigurationEvent.KEY_RELEASED);
  }

  @Override
  public void keyTyped(Canvas canvas, KeyEvent event) {
    processKeyEvent(canvas, event, KeyConfigurationEvent.KEY_TYPED);
  }

  @Override
  public void mouseDragged(Canvas canvas, Graphics g, MouseEvent e) {
    if (state != SHOW_NONE) {
      if (shouldSnap)
        Canvas.snapToGrid(e);
      moveTo(canvas, g, e.getX(), e.getY());
    }
  }

  @Override
  public void mouseEntered(Canvas canvas, Graphics g, MouseEvent e) {
    if (state == SHOW_GHOST || state == SHOW_NONE) {
      setState(canvas, SHOW_GHOST);
      canvas.requestFocusInWindow();
    } else if (state == SHOW_ADD_NO) {
      setState(canvas, SHOW_ADD);
      canvas.requestFocusInWindow();
    }
  }

  @Override
  public void mouseExited(Canvas canvas, Graphics g, MouseEvent e) {
    if (state == SHOW_GHOST) {
      moveTo(canvas, canvas.getGraphics(), INVALID_COORD, INVALID_COORD);
      setState(canvas, SHOW_NONE);
    } else if (state == SHOW_ADD) {
      moveTo(canvas, canvas.getGraphics(), INVALID_COORD, INVALID_COORD);
      setState(canvas, SHOW_ADD_NO);
    }
  }

  @Override
  public void mouseMoved(Canvas canvas, Graphics g, MouseEvent e) {
    if (state != SHOW_NONE) {
      if (shouldSnap)
        Canvas.snapToGrid(e);
      moveTo(canvas, g, e.getX(), e.getY());
    }
  }

  @Override
  public void mousePressed(Canvas canvas, Graphics g, MouseEvent e) {
    // verify the addition would be valid
    Circuit circ = canvas.getCircuit();
    if (!canvas.getProject().getLogisimFile().contains(circ)) {
      canvas.setErrorMessage(S.getter("cannotModifyError"));
      return;
    }
    if (factory instanceof SubcircuitFactory) {
      SubcircuitFactory circFact = (SubcircuitFactory) factory;
      Dependencies depends = canvas.getProject().getDependencies();
      if (!depends.canAdd(circ, circFact.getSubcircuit())) {
        canvas.setErrorMessage(S.getter("circularError"));
        return;
      }
    }

    if (shouldSnap)
      Canvas.snapToGrid(e);
    moveTo(canvas, g, e.getX(), e.getY());
    setState(canvas, SHOW_ADD);
  }

  @Override
  public void mouseReleased(Canvas canvas, Graphics g, MouseEvent e) {
    Component added = null;
    Project proj = canvas.getProject();
    LogisimFile file = proj.getLogisimFile();
    if (state == SHOW_ADD) {
      Circuit circ = canvas.getCircuit();
      if (!file.contains(circ))
        return;
      if (shouldSnap)
        Canvas.snapToGrid(e);
      moveTo(canvas, g, e.getX(), e.getY());

      Location loc = Location.create(e.getX(), e.getY());
      AttributeSet attrsCopy = (AttributeSet) attrs.clone();
      String label = attrsCopy.getValue(StdAttr.LABEL);
      if (label != null && !label.equals("")) {
        String newLabel = canvas.getCircuit().chooseUniqueLabel(label);
        if (!newLabel.equals(label))
            attrsCopy.setAttr(StdAttr.LABEL, newLabel);
      }
      ComponentFactory source = getFactory();
      if (source == null)
        return;

      Library libToPromote = file.findLibraryFor(source);
      if (libToPromote == null) {
        canvas.setErrorMessage(S.getter("nestedError"));
        return;
      }
      if (libToPromote == file || file.getLibraries().contains(libToPromote))
        libToPromote = null;
      else {
        int action = JOptionPane.showConfirmDialog(canvas,
            S.fmt("promoteLibraryMessage", libToPromote.getDisplayName()),
            S.get("promoteLibraryTitle"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (action != JOptionPane.OK_OPTION)
          return;
      }

      Component c = source.createComponent(loc, attrsCopy);

      if (circ.hasConflict(c)) {
        canvas.setErrorMessage(S.getter("exclusiveError"));
        return;
      }

      Bounds bds = c.getBounds(g);
      if (bds.getX() < 0 || bds.getY() < 0) {
        canvas.setErrorMessage(S.getter("negativeCoordError"));
        return;
      }

      for (Component comp2 : circ.getAllContaining(loc)) {
        Bounds b = comp2.getBounds();
        if (b.equals(bds)) {
          canvas.setErrorMessage(S.getter("overlapError"));
          return;
        }
      }

      try {
        if (libToPromote != null)
          proj.doAction(LogisimFileActions.loadLibraries(new Library[] { libToPromote }));
        CircuitMutation mutation = new CircuitMutation(circ);
        mutation.add(c);
        Action action = mutation.toAction(S.getter(
              "addComponentAction", factory.getDisplayGetter()));
        proj.doAction(action);
        lastAddition = action;
        added = c;
      } catch (CircuitException ex) {
        JOptionPane.showMessageDialog(canvas.getProject().getFrame(),
            ex.getMessage());
      }
      setState(canvas, SHOW_GHOST);
    } else if (state == SHOW_ADD_NO) {
      setState(canvas, SHOW_NONE);
    }

    Tool next = determineNext(proj);
    if (next != null) {
      proj.setTool(next);
      Action act = SelectionActions.dropAll(canvas.getSelection());
      if (act != null) {
        proj.doAction(act);
      }
      if (added != null)
        canvas.getSelection().add(added);
    }
  }

  private synchronized void moveTo(Canvas canvas, Graphics g, int x, int y) {
    if (state != SHOW_NONE)
      expose(canvas, lastX, lastY);
    lastX = x;
    lastY = y;
    if (state != SHOW_NONE)
      expose(canvas, lastX, lastY);
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    FactoryDescription desc = description;
    if (desc != null && !desc.isFactoryLoaded()) {
      Icon icon = desc.getIcon();
      if (icon != null) {
        icon.paintIcon(c.getDestination(), c.getGraphics(), x + 2,
            y + 2);
        return;
      }
    }

    ComponentFactory source = getFactory();
    if (source != null) {
      AttributeSet base = getBaseAttributes();
      source.paintIcon(c, x, y, base);
    }
  }

  private void processKeyEvent(Canvas canvas, KeyEvent event, int type) {
    KeyConfigurator handler = keyHandler;
    if (!keyHandlerTried) {
      ComponentFactory source = getFactory();
      AttributeSet baseAttrs = getBaseAttributes();
      handler = (KeyConfigurator) source.getFeature(
          KeyConfigurator.class, baseAttrs);
      keyHandler = handler;
      keyHandlerTried = true;
    }

    if (handler != null) {
      AttributeSet baseAttrs = getBaseAttributes();
      KeyConfigurationEvent e = new KeyConfigurationEvent(type,
          baseAttrs, event, this);
      KeyConfigurationResult r = handler.keyEventReceived(e);
      if (r != null) {
        Action act = ToolAttributeAction.create(r);
        canvas.getProject().doAction(act);
      }
    }
  }

  @Override
  public void select(Canvas canvas) {
    setState(canvas, SHOW_GHOST);
    bounds = null;
  }

  private void setFacing(Canvas canvas, Direction facing) {
    ComponentFactory source = getFactory();
    if (source == null)
      return;
    AttributeSet base = getBaseAttributes();
    Object feature = source.getFeature(
        ComponentFactory.FACING_ATTRIBUTE_KEY, base);
    @SuppressWarnings("unchecked")
    Attribute<Direction> attr = (Attribute<Direction>) feature;
    if (attr != null) {
      Action act = ToolAttributeAction.create(this, attr, facing);
      canvas.getProject().doAction(act);
    }
  }

  private void setState(Canvas canvas, int value) {
    if (value == SHOW_GHOST) {
      if (canvas.getProject().getLogisimFile()
          .contains(canvas.getCircuit())
          && AppPreferences.ADD_SHOW_GHOSTS.get()) {
        state = SHOW_GHOST;
      } else {
        state = SHOW_NONE;
      }
    } else {
      state = value;
    }
  }

  @Override
  public boolean sharesSource(Tool other) {
    if (!(other instanceof AddTool))
      return false;
    AddTool o = (AddTool) other;
    if (this.sourceLoadAttempted && o.sourceLoadAttempted) {
      return this.factory.equals(o.factory);
    } else if (this.description == null) {
      return o.description == null;
    } else {
      return this.description.equals(o.description);
    }
  }

  public boolean isBuiltin() {
    if (factory instanceof SubcircuitFactory ||
        factory instanceof VhdlEntity)
      return false; // these have a null libraryClass, b/c they belong to project itself
    // All AddTool() items are now required to have a libraryClass.
    return Builtin.isBuiltinLibrary(libraryClass);
  }

  public boolean isSubcircuitOrVhdl() {
    // note: circ libraries contain only subcircuit and vhdl tools
    return (factory instanceof SubcircuitFactory)
        || (factory instanceof VhdlEntity)
        || (description != null && description.getName().startsWith("file#"));
  }

  public static final DragDrop circuitDnd = Main.headless ? null : new DragDrop(
      Tool.class, LayoutClipboard.mimeTypeCircuitClip);
  public static final DragDrop vhdlDnd = Main.headless ? null : new DragDrop(
      Tool.class, LayoutClipboard.mimeTypeVhdlClip);

  public class TransferableAddTool<E> implements DragDrop.Support, DragDrop.Ghost {
    private DragDrop dnd;
    private LogisimFile file;
    private Project proj;
    private E elt;

    public TransferableAddTool(DragDrop dnd, LogisimFile file, Project proj, E elt) {
      this.dnd = dnd;
      this.file = file;
      this.proj = proj;
      this.elt = elt;
    }

    public LogisimFile getLogisimFile() { return file; }
    public E getElement() { return elt; }
    public DragDrop getDragDrop() { return dnd; }

    @Override
    public Object convertTo(String mimetype) {
      return elt ==  null ? null : XmlWriter.encodeSelection(file, proj, elt);
    }

    @Override
    public Object convertTo(Class cls) {
      return AddTool.this;
    }

    public void paintDragImage(JComponent dest, Graphics g, Dimension dim) {
      AddTool.this.paintDragImage(dest, g, dim);
    }

    public Dimension getSize() {
      return AddTool.this.getSize();
    }
  }

  private static Circuit getCircuit(ComponentFactory fact) {
    return (fact instanceof SubcircuitFactory)
        ? ((SubcircuitFactory)fact).getSubcircuit() : null;
  }

  private static VhdlContent getVhdl(ComponentFactory fact) {
    return (fact instanceof VhdlEntity)
        ? ((VhdlEntity)fact).getContent() : null;
  }

  public class TransferableCircuit extends TransferableAddTool<Circuit> {
    public TransferableCircuit(LogisimFile file, Project proj) {
      super(circuitDnd, file, proj, getCircuit(getFactory()));
    }
  }

  public class TransferableVhdl extends TransferableAddTool<VhdlContent> {
    public TransferableVhdl(LogisimFile file, Project proj) {
      super(circuitDnd, file, proj, getVhdl(getFactory()));
    }
  }

}
