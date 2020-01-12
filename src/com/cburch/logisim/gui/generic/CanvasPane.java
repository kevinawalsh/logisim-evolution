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
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseWheelEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.cburch.logisim.Main;

public class CanvasPane extends JScrollPane {
  private class Listener implements ComponentListener, PropertyChangeListener {

    public void componentHidden(ComponentEvent e) {
    }

    public void componentMoved(ComponentEvent e) {
    }

    //
    // ComponentListener methods
    //
    public void componentResized(ComponentEvent e) {
      contents.recomputeSize();
    }

    public void componentShown(ComponentEvent e) {
    }

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
  public void mouseWheelMoved(MouseWheelEvent mwe, boolean relative) {
    if (mwe.isControlDown()) {
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
      double rx = (double)(mwe.getX() - (relative?r.x:0));
      double ry = (double)(mwe.getY() - (relative?r.y:0));

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
      if (mwe.getWheelRotation() < 0) { // ZOOM IN
        newZoom *= 1.08; // newZoom += 0.1;
        double max = opts[opts.length-1] / 100.0;
        zoomModel.setZoomFactor(newZoom >= max ? max : newZoom);
      } else { // ZOOM OUT
        newZoom /= 1.08; // newZoom -= 0.1;
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

      // Set once now
      getViewport().setViewPosition(new java.awt.Point(nvx, nvy));

      // Set again later, to be sure it takes
      SwingUtilities.invokeLater( () -> {
        getViewport().setViewPosition(new java.awt.Point(nvx, nvy));
        pendingScrollUpdates = false;
      });

    } else if (mwe.isShiftDown()) {
      getHorizontalScrollBar().setValue(
          scrollValue(getHorizontalScrollBar(), mwe.getWheelRotation()));
    } else {
      getVerticalScrollBar().setValue(
          scrollValue(getVerticalScrollBar(), mwe.getWheelRotation()));
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

  private static final long serialVersionUID = 1L;

  private CanvasPaneContents contents;
  private Listener listener;
  private ZoomModel zoomModel;

  public static class SmartScrollModel extends DefaultBoundedRangeModel {
    public SmartScrollModel(BoundedRangeModel m) {
      super(m.getValue(), m.getExtent(), m.getMinimum(), m.getMaximum());
    }
    public void setExtent(int n) {
      System.out.println(this + " setExtent " + n);
      super.setExtent(n);
    }
    public void setMaximum(int n) {
      System.out.println(this + " setMaximum " + n);
      super.setMaximum(n);
    }
    public void setMinimum(int n) {
      System.out.println(this + " setMinimum " + n);
      super.setMinimum(n);
    }
    public void setValue(int n) {
      System.out.println(this + " setValue " + n);
      super.setValue(n);
    }
    public void setRangeProperties(int v, int e, int m, int x, boolean a) {
      System.out.println(this + " setRange " + v + " " + e + " " + m + " " + x + " " + a);
      // Thread.dumpStack();
      super.setRangeProperties(v, e, m, x, a);
    }
  }

  public CanvasPane(CanvasPaneContents contents) {
    super((Component) contents);
    this.contents = contents;
    this.listener = new Listener();
    this.zoomModel = null;
    if (Main.AlwaysUseScrollbars) {
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
    contents.setCanvasPane(this);
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
