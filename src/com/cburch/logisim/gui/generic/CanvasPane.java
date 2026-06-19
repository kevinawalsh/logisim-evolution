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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.cburch.logisim.Main;
import com.cburch.logisim.util.DesktopIntegration;
import com.cburch.logisim.util.GestureUtilities;

public class CanvasPane extends JScrollPane {
  private class Listener implements ComponentListener, PropertyChangeListener {

    public void componentHidden(ComponentEvent e) { }

    public void componentMoved(ComponentEvent e) { }

    public void componentResized(ComponentEvent e) {
      contents.recomputeSize();
    }

    public void componentShown(ComponentEvent e) { }

    public void propertyChange(PropertyChangeEvent e) {
      String prop = e.getPropertyName();
      if (prop.equals(ZoomModel.ZOOM)) {
        double oldZoom = ((Double) e.getOldValue()).doubleValue();
        Rectangle r = getViewport().getViewRect();
        double cx = (r.x + r.width / 2) / oldZoom;
        double cy = (r.y + r.height / 2) / oldZoom;

        double newZoom = ((Double) e.getNewValue()).doubleValue();
        contents.recomputeSize();
        r = getViewport().getViewRect();
        int hv = (int) (cx * newZoom) - r.width / 2;
        int vv = (int) (cy * newZoom) - r.height / 2;
        getHorizontalScrollBar().setValue(hv);
        getVerticalScrollBar().setValue(vv);
      }
    }
  }

  private boolean pendingScrollUpdates; // hack
  private void magnify(double eventx, double eventy, boolean relative, double amt) {
    // Attempt to maintain mouse position during zoom, using
    // [m]ax, [v]alue, [e]xtent, and [r]elative position within it,
    // to calculate target [n]ew[m]ax, [p]ercent and [n]ew[v]alue.
    if (pendingScrollUpdates)
      return;
    pendingScrollUpdates = true;
    double zoom = zoomModel.getZoomFactor();

    double mx = (double)getViewport().getView().getWidth();
    double my = (double)getViewport().getView().getHeight();
    Rectangle r = getViewport().getViewRect();
    double ex = (double)r.width;
    double ey = (double)r.height;
    double rx = (double)(eventx - (relative?r.x:0));
    double ry = (double)(eventy - (relative?r.y:0));

    // System.out.printf("max:                %f x %f\n", mx, my);
    // System.out.printf("extent size:        %f x %f\n", ex, ey);
    // System.out.printf("extent offset:      %d , %d\n", r.x, r.y);
    // System.out.printf("relative mouse pos: %f , %f %s\n", rx, ry,
    //     (relative ? ("adjusted by "+r.x+", "+r.y) : "unadjusted"));

    // Calling zoomModel.setZoomFactor() will kick off (eventual) changes to
    // the canvas size and scrollbar model, and the appearance/disappearance of
    // scrollbars affects the viewport size, and changes to the viewport
    // size can also cause layout updates which also change the scrollbar
    // model. This makes reliance on the scrollbar model tricky -- any
    // changes we make hear can get overwritten soon after depending on the
    // unpredictable ordering of AWT events, or we could be using stale
    // values from the scrollbar. We will use viewport instead, and we will
    // make the update twice: once now (works in most cases, and avoids artifacts),
    // then a second time in invokeLater (handles cases where the size we
    // set now gets overwritten by swing layout updates).

    double opts[] = zoomModel.getZoomOptions();
    double newZoom = zoom;
    if (amt > 0) { // ZOOM IN
      newZoom *= Math.pow(1.08, amt); // newZoom += 0.1;
      double max = opts[opts.length-1] / 100.0;
      zoomModel.setZoomFactor(newZoom >= max ? max : newZoom);
    } else if (amt < 0) { // ZOOM OUT
      newZoom /= Math.pow(1.08, -amt); // newZoom -= 0.1;
      double min = opts[0] / 100.0;
      zoomModel.setZoomFactor(newZoom <= min ? min : newZoom);
    }
    newZoom = zoomModel.getZoomFactor();

    double nmx = mx * newZoom / zoom;
    double nmy = my * newZoom / zoom;
    double px = (r.x/mx) + (ex/mx - ex/nmx) * (rx/ex);
    double py = (r.y/my) + (ey/my - ey/nmy) * (ry/ey);
    int nvx = (int)Math.max(0, nmx * px);
    int nvy = (int)Math.max(0, nmy * py);

    // System.out.printf("newmax:             %f x %f\n", nmx, nmy);
    // System.out.printf("relative mouse pos: %f%% , %f%%\n", px, py);
    // System.out.printf("new extent offset:  %d , %d\n", nvx, nvy);

    // Set once now
    getViewport().setViewPosition(new Point(nvx, nvy));

    // Set again later, to be sure it takes
    SwingUtilities.invokeLater( () -> {
      getViewport().setViewPosition(new Point(nvx, nvy));
      pendingScrollUpdates = false;
    });
  }

  private void magnify(double amt) {
    Point m = getMousePosition(true);
    if (m == null)
      return;
    magnify(m.getX(), m.getY(), false, amt);
  }

  public void zoomIn() {
    if (zoomModel == null) return;
    double[] choices = zoomModel.getZoomOptions();
    double factor = zoomModel.getZoomFactor() * 100.0 * 1.001;
    for (double choice : choices) {
      if (choice > factor) { zoomModel.setZoomFactor(choice / 100.0); return; }
    }
  }

  public void zoomOut() {
    if (zoomModel == null) return;
    double[] choices = zoomModel.getZoomOptions();
    double factor = zoomModel.getZoomFactor() * 100.0 * 0.999;
    for (int i = choices.length - 1; i >= 0; i--) {
      if (choices[i] < factor) { zoomModel.setZoomFactor(choices[i] / 100.0); return; }
    }
  }

  public void mouseWheelMoved(MouseWheelEvent mwe, boolean relative) {
    if (mwe.isControlDown() || (Main.MacOS && mwe.isMetaDown())) {
      magnify(mwe.getX(), mwe.getY(), relative, -1.0*mwe.getPreciseWheelRotation());
    } else if (mwe.isShiftDown()) {
      scrollValue(getHorizontalScrollBar(), mwe.getPreciseWheelRotation());
    } else {
      scrollValue(getVerticalScrollBar(), mwe.getPreciseWheelRotation());
    }
  }

  private void scrollValue(JScrollBar bar, double delta) {
    int adjust = (int)Math.round(delta * 2 * bar.getBlockIncrement());
    if (adjust > 0)
      bar.setValue(Math.min(bar.getValue() + adjust, bar.getMaximum()));
    else if (adjust < 0)
      bar.setValue(Math.max(bar.getValue() + adjust, bar.getMinimum()));
  }

  private static final long serialVersionUID = 1L;

  private CanvasPaneContents contents;
  private Listener listener;
  private ZoomModel zoomModel;

  // public static class SmartScrollModel extends DefaultBoundedRangeModel {
  //   public SmartScrollModel(BoundedRangeModel m) {
  //     super(m.getValue(), m.getExtent(), m.getMinimum(), m.getMaximum());
  //   }
  //   public void setExtent(int n) {
  //     System.out.println(this + " setExtent " + n);
  //     super.setExtent(n);
  //   }
  //   public void setMaximum(int n) {
  //     System.out.println(this + " setMaximum " + n);
  //     super.setMaximum(n);
  //   }
  //   public void setMinimum(int n) {
  //     System.out.println(this + " setMinimum " + n);
  //     super.setMinimum(n);
  //   }
  //   public void setValue(int n) {
  //     System.out.println(this + " setValue " + n);
  //     super.setValue(n);
  //   }
  //   public void setRangeProperties(int v, int e, int m, int x, boolean a) {
  //     System.out.println(this + " setRange " + v + " " + e + " " + m + " " + x + " " + a);
  //     Thread.dumpStack();
  //     super.setRangeProperties(v, e, m, x, a);
  //   }
  // }

  public CanvasPane(CanvasPaneContents contents) {
    super((Component) contents);
    this.contents = contents;
    this.listener = new Listener();
    this.zoomModel = null;
    if (DesktopIntegration.AlwaysUseScrollbars) {
      setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
      setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    }
    // getVerticalScrollBar().setModel(new SmartScrollModel(
    //       getVerticalScrollBar().getModel()));
    // getHorizontalScrollBar().setModel(new SmartScrollModel(
    //       getHorizontalScrollBar().getModel()));

    addComponentListener(listener);
    setWheelScrollingEnabled(false);
    addMouseWheelListener((mwe) -> mouseWheelMoved(mwe, false));
    GestureUtilities.addMagnificationListenerTo(this, (e) -> magnify(25*e.getMagnification()));
    contents.setCanvasPane(this);

    int cmd = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, cmd), "zoomIn");
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, cmd | KeyEvent.SHIFT_DOWN_MASK), "zoomIn");
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, cmd), "zoomOut");
    getActionMap().put("zoomIn", new AbstractAction() {
      public void actionPerformed(ActionEvent e) { zoomIn(); }
    });
    getActionMap().put("zoomOut", new AbstractAction() {
      public void actionPerformed(ActionEvent e) { zoomOut(); }
    });
  }

  public Dimension getViewportSize() {
    Dimension size = new Dimension();
    getViewport().getSize(size);
    return size;
  }

  public double getZoomFactor() {
    ZoomModel model = zoomModel;
    return model == null ? 1.0 : model.getZoomFactor();
  }

  public void setZoomModel(ZoomModel model) {
    ZoomModel oldModel = zoomModel;
    if (oldModel != null) {
      oldModel.removePropertyChangeListener(ZoomModel.ZOOM, listener);
    }
    zoomModel = model;
    if (model != null) {
      model.addPropertyChangeListener(ZoomModel.ZOOM, listener);
    }
  }

  public Dimension supportPreferredSize(int width, int height) {
    double zoom = getZoomFactor();
    if (zoom != 1.0) {
      width = (int) Math.ceil(width * zoom);
      height = (int) Math.ceil(height * zoom);
    }
    Dimension minSize = getViewportSize();
    if (minSize.width > width)
      width = minSize.width;
    if (minSize.height > height)
      height = minSize.height;
    return new Dimension(width, height);
  }

  public int supportScrollableBlockIncrement(Rectangle visibleRect,
      int orientation, int direction) {
    int unit = supportScrollableUnitIncrement(visibleRect, orientation,
        direction);
    if (direction == SwingConstants.VERTICAL) {
      return visibleRect.height / unit * unit;
    } else {
      return visibleRect.width / unit * unit;
    }
  }

  public int supportScrollableUnitIncrement(Rectangle visibleRect,
      int orientation, int direction) {
    double zoom = getZoomFactor();
    return (int) Math.round(10 * zoom);
  }
}
