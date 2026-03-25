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

package com.cburch.logisim.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// import java.awt.dnd.DragSourceEvent;
// import java.awt.dnd.DragSourceListener;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import com.cburch.logisim.Main;

public class DragDrop {

  public final DataFlavor dataFlavor;
  public final DataFlavor[] dataFlavors;
  public final Class dataClass;
  public final Class[] dataClasses;

  // DragDrop and Support are intended to make it easier to add drag and drop
  // support to a class:
  //
  // public class Foo ... implements DragDrop.Support {
  //    ...
  //    public static final DragDrop dnd = new DragDrop(Foo.class);
  //    public DragDrop getDragDrop() { return dnd; }
  // }
  //
  // This is enough to make Foo implement all of the methods of interface
  // Transferable (the sending side), and provide a jvm-local data flavor for
  // the class (used by the receiving side).
 
  public DragDrop(Object ...classOrMimeTypeStringOrDataFlavor) {
    int n = classOrMimeTypeStringOrDataFlavor.length;
    DataFlavor[] flavors = new DataFlavor[n];
    Class[] classes = new Class[n];
    try {
      for (int i = 0; i < n; i++) {
        Object o  = classOrMimeTypeStringOrDataFlavor[i];
        if (o instanceof Class) {
          flavors[i] = new DataFlavor(
              String.format("%s;class=\"%s\"",
                DataFlavor.javaJVMLocalObjectMimeType,
                ((Class)o).getName()));
          classes[i] = (Class)o;
        } else if (o instanceof String) {
          flavors[i] = new DataFlavor((String)o);
        } else if (o instanceof DataFlavor) {
          flavors[i] = (DataFlavor)o;
        } else {
          throw new IllegalArgumentException("DragDrop flavor must be stirng, class, or DataFlavor");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      flavors = new DataFlavor[] { };
      classes = new Class[] { };
    }
    dataFlavors = flavors;
    dataClasses = classes;
    if (flavors.length > 0) {
      dataFlavor = flavors[0];
      dataClass = classes[0]; // null if flavor[0] wasn't a Class
    } else {
      dataFlavor = null;
      dataClass = null;
    }
  }

  public static DataFlavor uuidTokenFlavor(String tag) {
    if (Main.headless)
      return null;
    try {
      String mimetype = String.format("application/x-logisim-%s-token;class=java.lang.String", tag);
      return new DataFlavor(mimetype);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }
  
  public static String uuidNonce() {
    return UUID.randomUUID().toString();
  }

  public static String uuidToken(String tag) {
    return tag + ":" + UUID.randomUUID().toString();
  }
  
  public static final DataFlavor JVMLOCAL_UUID_FLAVOR = uuidTokenFlavor("jvm");
  public final static String JVMLOCAL_UUID_TOKEN = uuidToken("jvm");

  public interface Support extends Transferable {
    public DragDrop getDragDrop();

    public default Object convertTo(DataFlavor flavor) {
      DragDrop dnd = getDragDrop();
      if (dnd == null || dnd.dataFlavors == null)
        return null;
      if (flavor.equals(JVMLOCAL_UUID_FLAVOR))
        return JVMLOCAL_UUID_TOKEN;
      for (int i = 0; i < dnd.dataFlavors.length; i++) {
        if (!dnd.dataFlavors[i].equals(flavor))
          continue;
        else if (dnd.dataClasses[i] != null)
          return convertTo(dnd.dataClasses[i]);
        else
          return convertToFlavor(i, dnd.dataFlavors[i]);
      }
      return null;
    }

    // This one is meant to be overridden
    public default Object convertToFlavor(int idx, Object dataFlavor) {
      return null;
    }

    public default Object convertTo(Class cls) {
      return cls.isInstance(this) ? this : null;
    }

    @Override
    public default Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if(!isDataFlavorSupported(flavor))
        throw new UnsupportedFlavorException(flavor);
      Object obj = convertTo(flavor);
      if (obj == null)
        throw new UnsupportedFlavorException(flavor);
      return obj;
    }

    @Override
    public default DataFlavor[] getTransferDataFlavors() {
      return getDragDrop().dataFlavors;
    }

    @Override
    public default boolean isDataFlavorSupported(DataFlavor flavor) {
      if (flavor == null)
        return false;
      DragDrop dnd = getDragDrop();
      for (DataFlavor supported: dnd.dataFlavors)
        if (flavor.equals(supported))
            return true;
      return false;
    }
  }

  // This code makes it easier to add support for a component acting as a drag
  // source, for components that don't natively provide that support.
  public static final int MOVE = DnDConstants.ACTION_MOVE;
  public static final int COPY = DnDConstants.ACTION_COPY;
  public static final int LINK = DnDConstants.ACTION_LINK;

  public static <T extends JComponent & Transferable> void enable(T t, int actions) {
    t.addHierarchyListener(new HierarchyListener() {
      Handler<T> h;
      DragGestureRecognizer r;
      public void hierarchyChanged(HierarchyEvent e) {
        if (h == null && t.isShowing()) {
          h = new Handler<T>(t);
          r = source.createDefaultDragGestureRecognizer(t, actions, h);
        } else if (h != null && !t.isShowing()) {
          r.removeDragGestureListener(h);
          h = null;
          r = null;
        }
      }
    });
  }

  public static final DragSource source;
 
  public static class Handler<T extends Transferable> extends DragSourceAdapter implements DragGestureListener {
    T t;

    public Handler(T t) { this.t = t; System.out.println("*** HERE ***"); }

    @Override
    public void dragGestureRecognized(DragGestureEvent e) {
      Cursor cursor = null;
      System.err.println("[DnD-DEBUG] Handler.dragGestureRecognized: t="+t);
      if (t instanceof Ghost) {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        System.out.println("USE_NATIVE_DRAG_IMAGE="+USE_NATIVE_DRAG_IMAGE +", DISABLE_DRAG_OVERLAY="+DISABLE_DRAG_OVERLAY);
        if (USE_NATIVE_DRAG_IMAGE && !DISABLE_DRAG_OVERLAY) {
          // On macOS (and modern Windows), the JVM supports native drag images.
          // AppKit moves the image with the cursor automatically, so no Java work
          // is needed in dragMouseMoved — which avoids the macOS JVM crash where
          // doAWTRunLoopImpl re-entrantly delivers mouseDragged during draggingUpdated:.
          Ghost g = (Ghost) t;
          Dimension d = g.getSize();
          BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
          Graphics2D gfx = img.createGraphics();
          try {
            g.paintDragImage(null, gfx, d);
          } finally {
            gfx.dispose();
          }
          e.getDragSource().startDrag(e, cursor, img, new Point(10, 10), t, this);
          return;
        }
      }
      e.getDragSource().startDrag(e, cursor, t, this);
    }

    // @Override
    // public void dragDropEnd(DragSourceDropEvent e) { }
  }

  private static final DragImageAnimator animator;

  static {
    if (Main.headless) {
      animator = null;
      source = null;
    } else {
      animator = new DragImageAnimator();
      source = DragSource.getDefaultDragSource();
      source.addDragSourceListener(animator);
      source.addDragSourceMotionListener(animator);
    }
  }

  // If a Transferable component implements Ghost, it will be painted near the
  // mouse during a drag. On platforms where DragSource.isDragImageSupported()
  // is true (macOS, modern Windows), we pass the image to startDrag() and the
  // OS handles it natively. On other platforms (Linux), we fall back to a
  // glass-pane overlay painted on whichever Java window is under the cursor.
  public interface Ghost {
    // For a JComponent, these defaults work fine, or override them to customize
    // the drag image.
    default public void paintComponent(Graphics g) { }
    default public void paintDragImage(JComponent dest, Graphics g, Dimension dim) {
      paintComponent(g);
    }
    default public Dimension getSize() { return new Dimension(1, 1); }
  }


  // On macOS, using any top-level window (JFrame, JWindow) for the drag overlay
  // causes a JVM crash: CDropTarget.draggingUpdated: calls doAWTRunLoopImpl,
  // which re-entrantly delivers a mouseDragged to the overlay's AWTView,
  // crashing at deliverJavaMouseEvent+1840. Fix: paint the ghost image on the
  // glass pane of whichever Java window is under the cursor instead of using
  // a separate native window.
  public static final boolean DISABLE_DRAG_OVERLAY =
      Boolean.getBoolean("logisim.disableDragOverlay");

  // Experiment 2: At first drag move, disable ALL active DropTargets in ALL
  // windows (to test if any CDropTarget.draggingUpdated: call triggers the crash).
  // Re-enables them on dragDropEnd. Note: drops will not work while this is active.
  public static final boolean EXPERIMENT2_DISABLE_ALL_DROPTARGETS =
      Boolean.getBoolean("logisim.experiment2");

  // On macOS and modern Windows JDKs, DragSource supports native drag images:
  // the JVM passes the image to AppKit/Win32 which moves it with the cursor
  // automatically. When true, we use startDrag(Cursor,Image,...) and skip the
  // glass-pane fallback entirely, so dragMouseMoved does nothing — which avoids
  // the macOS JVM crash (doAWTRunLoopImpl re-entering deliverJavaMouseEvent).
  public static final boolean USE_NATIVE_DRAG_IMAGE =
      !Main.headless && DragSource.isDragImageSupported();

  // Transparent glass pane overlay for showing the drag ghost image.
  private static class GhostGlassPane extends JPanel {
    private Ghost ghost;
    private int ghostX, ghostY;

    GhostGlassPane(Ghost ghost) {
      this.ghost = ghost;
      setOpaque(false);
      setLayout(null);
    }

    void setGhostPosition(int x, int y) {
      ghostX = x;
      ghostY = y;
      repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
      if (ghost == null) return;
      Dimension d = ghost.getSize();
      Graphics2D g2 = (Graphics2D) g.create(ghostX, ghostY, d.width, d.height);
      try {
        ghost.paintDragImage(this, g2, d);
      } finally {
        g2.dispose();
      }
    }
  }

  private static class DragImageAnimator extends DragSourceAdapter
    implements DragSourceMotionListener {
    private Ghost ghost = null;
    private JRootPane currentRootPane = null;
    private Component savedGlassPane = null;
    private boolean savedGlassPaneVisible;
    private GhostGlassPane activeGlassPane = null;
    int moveCount = 0;
    private final List<DropTarget> disabledTargets = new ArrayList<>();

    private void ensureGhost(DragSourceDragEvent e) {
      if (ghost == null && !DISABLE_DRAG_OVERLAY && !USE_NATIVE_DRAG_IMAGE) {
        System.out.println("ghost="+ghost+" USE_NATIVE_DRAG_IMAGE="+USE_NATIVE_DRAG_IMAGE +", DISABLE_DRAG_OVERLAY="+DISABLE_DRAG_OVERLAY);
        Object t = e.getDragSourceContext().getTransferable();
        if (t instanceof Ghost) ghost = (Ghost) t;
      }
    }

    // Experiment 2: disable every active DropTarget in every window so that
    // CDropTarget.draggingUpdated: is never called during this drag.
    private void disableAllDropTargets() {
      disabledTargets.clear();
      for (Window w : Window.getWindows()) {
        collectAndDisable(w);
      }
      System.err.printf("[DnD-DEBUG] Experiment2: disabled %d DropTargets across all windows%n",
          disabledTargets.size());
    }

    private void collectAndDisable(Container c) {
      DropTarget dt = c.getDropTarget();
      if (dt != null && dt.isActive()) {
        dt.setActive(false);
        disabledTargets.add(dt);
      }
      for (Component child : c.getComponents()) {
        if (child instanceof Container)
          collectAndDisable((Container) child);
      }
    }

    private void reEnableAllDropTargets() {
      for (DropTarget dt : disabledTargets)
        dt.setActive(true);
      System.err.printf("[DnD-DEBUG] Experiment2: re-enabled %d DropTargets%n",
          disabledTargets.size());
      disabledTargets.clear();
    }

    @Override
    public void dragEnter(DragSourceDragEvent e) {
      ensureGhost(e);
      System.err.printf("[DnD-DEBUG] DragImageAnimator.dragEnter: hasGhost=%b thread=%s%n",
          ghost != null, Thread.currentThread().getName());
      moveCount = 0;
    }

    @Override
    public void dragMouseMoved(DragSourceDragEvent e) {
      ensureGhost(e);
      if (EXPERIMENT2_DISABLE_ALL_DROPTARGETS && moveCount == 0)
        disableAllDropTargets();
      int sx = e.getX(), sy = e.getY();
      if (++moveCount % 20 == 1)
        System.err.printf("[DnD-DEBUG] DragImageAnimator.dragMouseMoved #%d: (%d,%d) thread=%s%n",
            moveCount, sx, sy, Thread.currentThread().getName());
      if (ghost == null) return;
      updateGhostPosition(sx, sy);
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent e) {
      System.err.printf("[DnD-DEBUG] DragImageAnimator.dragDropEnd: hasGhost=%b thread=%s%n",
          ghost != null, Thread.currentThread().getName());
      if (EXPERIMENT2_DISABLE_ALL_DROPTARGETS)
        reEnableAllDropTargets();
      removeFromCurrentRootPane();
      ghost = null;
      moveCount = 0;
    }

    private void updateGhostPosition(int sx, int sy) {
      JRootPane rp = findRootPaneAt(sx, sy);
      if (rp != currentRootPane) {
        removeFromCurrentRootPane();
        if (rp != null) installOnRootPane(rp);
        currentRootPane = rp;
      }
      if (activeGlassPane != null) {
        Point p = new Point(sx, sy);
        try {
          SwingUtilities.convertPointFromScreen(p, currentRootPane);
        } catch (Exception ex) { return; }
        activeGlassPane.setGhostPosition(p.x + 10, p.y + 10);
      }
    }

    private void installOnRootPane(JRootPane rp) {
      savedGlassPane = rp.getGlassPane();
      savedGlassPaneVisible = savedGlassPane.isVisible();
      activeGlassPane = new GhostGlassPane(ghost);
      rp.setGlassPane(activeGlassPane);
      activeGlassPane.setVisible(true);
    }

    private void removeFromCurrentRootPane() {
      if (currentRootPane != null && savedGlassPane != null) {
        try {
          currentRootPane.setGlassPane(savedGlassPane);
          savedGlassPane.setVisible(savedGlassPaneVisible);
        } catch (Exception ex) { /* ignore if window is gone */ }
      }
      activeGlassPane = null;
      savedGlassPane = null;
      currentRootPane = null;
    }

    private static JRootPane findRootPaneAt(int sx, int sy) {
      // Iterate in reverse: more recently created (typically front) windows first.
      Window[] windows = Window.getWindows();
      for (int i = windows.length - 1; i >= 0; i--) {
        Window w = windows[i];
        if (!w.isShowing() || !(w instanceof RootPaneContainer)) continue;
        try {
          Rectangle bounds = new Rectangle(w.getLocationOnScreen(), w.getSize());
          if (bounds.contains(sx, sy))
            return ((RootPaneContainer) w).getRootPane();
        } catch (Exception ex) { /* ignore */ }
      }
      return null;
    }
  }

}
