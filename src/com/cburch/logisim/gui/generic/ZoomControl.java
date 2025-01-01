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
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSlider;

import java.awt.event.MouseAdapter;
import java.awt.Insets;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Icons;

public class ZoomControl extends JPanel {
  private class GridIcon extends JComponent
    implements MouseListener, PropertyChangeListener {
    private static final long serialVersionUID = 1L;
    boolean state = true;

    public GridIcon() {
      addMouseListener(this);
      setPreferredSize(new Dimension(20, 40));
      setToolTipText("");
      setFocusable(true);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
      return S.get("zoomShowGrid");
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
      model.setShowGrid(!state);
    }

    public void mouseReleased(MouseEvent e) {
    }

    @Override
    protected void paintComponent(Graphics g) {
      int width = getWidth();
      int height = getHeight();
      g.setColor(state ? Color.BLACK : Color.GRAY);
      int xdim = (Math.min(width, 18) - 4) / 3 * 3 + 1;
      int ydim = (Math.min(height, 32) - 4) / 3 * 3 + 1;
      int xoff = (width - xdim) / 2;
      int yoff = (height - ydim) / 2;
      for (int x = 0; x < xdim; x += 3) {
        for (int y = 0; y < ydim; y += 3) {
          g.drawLine(x + xoff, y + yoff, x + xoff, y + yoff);
        }
      }
      g.setColor(Color.BLACK);
      g.drawLine(xoff, yoff, xoff+3, yoff);
      g.drawLine(xoff, yoff, xoff, yoff+3);
      g.drawLine(xoff, yoff+ydim, xoff, yoff+ydim-3);
      g.drawLine(xoff, yoff+ydim, xoff+3, yoff+ydim);
      g.drawLine(xoff+xdim, yoff, xoff+xdim-3, yoff);
      g.drawLine(xoff+xdim, yoff, xoff+xdim, yoff+3);
      g.drawLine(xoff+xdim, yoff+ydim, xoff+xdim-3, yoff+ydim);
      g.drawLine(xoff+xdim, yoff+ydim, xoff+xdim, yoff+ydim-3);
    }

    public void propertyChange(PropertyChangeEvent evt) {
      update();
    }

    private void update() {
      boolean grid = model.getShowGrid();
      if (grid != state) {
        state = grid;
        repaint();
      }
    }
  }

  private class SliderModel extends DefaultBoundedRangeModel implements PropertyChangeListener {
    private static final long serialVersionUID = 1L;

    public SliderModel(ZoomModel model) {
      super(nearestZoomOption(), 0, 0, model.getZoomOptions().length-1);
    }

    public void propertyChange(PropertyChangeEvent evt) {
      fireStateChanged();
    }

    public void setValue(int i) {
      zoomTo(i);
    }

    public int getValue() {
      return nearestZoomOption();
    }
  }

  private static final long serialVersionUID = 1L;

  private ZoomModel model;
  // private ZoomLabel label;
  private SliderModel sliderModel;
  private JSlider slider;
  private GridIcon grid;
  private JComponent canvas;

  private int nearestZoomOption() {
    double[] choices = model.getZoomOptions();
    double factor = model.getZoomFactor() * 100.0;
    int closest = 0;
    for (int i = 1; i < choices.length; i++) {
      if (Math.abs(choices[i] - factor) < Math.abs(choices[closest] - factor))
        closest = i;
    }
    return closest;
  }

  private class ZoomButton extends JButton {
    boolean out;
    public ZoomButton(String icon, boolean left) {
      super(Icons.getIcon(icon));
      out = left;
      setOpaque(false);
      setBackground(new java.awt.Color(0, 0, 0, 0));
      setBorderPainted(false);
      if (left)
        setMargin(new Insets(2, 1, 2, 0));
      else
        setMargin(new Insets(2, 0, 2, 1));
      addMouseListener(new ZoomMouseListener());
      addActionListener(new ZoomActionListener());
      setFocusable(false);
    }
    protected class ZoomMouseListener extends MouseAdapter {
      public void mouseEntered(MouseEvent ev) {
        setBorderPainted(true);
      }
      public void mouseExited(MouseEvent ev) { setBorderPainted(false); }
    }
    protected class ZoomActionListener implements ActionListener {
      public void actionPerformed(ActionEvent e) {
        if (out) zoomOut();
        else zoomIn();
      }
    }
  }

  public ZoomControl(ZoomModel model, JComponent canvas) {
    super(new BorderLayout());
    this.model = model;
    this.canvas = canvas;

    // label = new ZoomLabel();
    sliderModel = new SliderModel(model);

    JButton plus = new ZoomButton("zoomin.png", false);
    JButton minus = new ZoomButton("zoomout.png", true);
    slider = new JSlider(sliderModel);

    JPanel zoom = new JPanel(new BorderLayout());
    zoom.add(minus, BorderLayout.WEST);
    // zoom.add(label, BorderLayout.CENTER);
    zoom.add(plus, BorderLayout.EAST);
    // zoom.add(slider, BorderLayout.NORTH);
    zoom.add(slider, BorderLayout.CENTER);
    // zoom.add(new JLabel("Zoom", SwingConstants.CENTER), BorderLayout.SOUTH);

    this.add(zoom, BorderLayout.CENTER);

    grid = new GridIcon();
    this.add(grid, BorderLayout.EAST);
    grid.update();

    model.addPropertyChangeListener(ZoomModel.SHOW_GRID, grid);
    model.addPropertyChangeListener(ZoomModel.ZOOM, sliderModel);

    showCoordinates(AppPreferences.SHOW_COORDS.get());
    AppPreferences.SHOW_COORDS.addPropertyChangeListener(
        new PropertyChangeListener() {
          public void propertyChange(PropertyChangeEvent event) {
            showCoordinates(AppPreferences.SHOW_COORDS.get());
          }
        });

  }

  public String zoomString() {
    double factor = model.getZoomFactor();
    return String.format("%.0f%%", factor * 100.0);
  }

  public void zoomIn() {
    double zoom = model.getZoomFactor();
    double[] choices = model.getZoomOptions();
    double factor = zoom * 100.0 * 1.001;
    for (int i = 0; i < choices.length; i++) {
      if (choices[i] > factor) {
        model.setZoomFactor(choices[i] / 100.0);
        return;
      }
    }
  }

  public void zoomOut() {
    double zoom = model.getZoomFactor();
    double[] choices = model.getZoomOptions();
    double factor = zoom * 100.0 * 0.999;
    for (int i = choices.length - 1; i >= 0; i--) {
      if (choices[i] < factor) {
        model.setZoomFactor(choices[i] / 100.0);
        return;
      }
    }
  }

  public void zoomTo(int i) {
    double[] choices = model.getZoomOptions();
    i = Math.max(Math.min(i, choices.length - 1), 0);
    model.setZoomFactor(choices[i] / 100.0);
  }

  // private class ZoomLabel extends JLabel implements PropertyChangeListener {
  //   public ZoomLabel() { super(zoomString(), SwingConstants.CENTER); }
  //   public void propertyChange(PropertyChangeEvent evt) { update(); }
  //   public void update() { setText(zoomString()); }
  // }

  public void setZoomModel(ZoomModel newModel, JComponent newCanvas) {
    ZoomModel oldModel = model;
    JComponent oldCanvas = canvas;
    if (oldModel == newModel && oldCanvas == newCanvas)
      return;
    if (oldCanvas != newCanvas) {
      if (oldCanvas != null)
        oldCanvas.removeMouseMotionListener(coordListener);
      canvas = newCanvas;
      if (newCanvas != null)
        newCanvas.addMouseMotionListener(coordListener);
    }
    if (oldModel != newModel) {
      if (oldModel != null) {
        oldModel.removePropertyChangeListener(ZoomModel.SHOW_GRID, grid);
        oldModel.removePropertyChangeListener(ZoomModel.ZOOM, sliderModel);
        newModel.removePropertyChangeListener(ZoomModel.ZOOM, zoomListener);
      }
      model = newModel;
      sliderModel = new SliderModel(model);
      slider.setModel(sliderModel);
      grid.update();
      if (newModel != null) {
        newModel.addPropertyChangeListener(ZoomModel.SHOW_GRID, grid);
        newModel.addPropertyChangeListener(ZoomModel.ZOOM, sliderModel);
        newModel.addPropertyChangeListener(ZoomModel.ZOOM, zoomListener);
      }
    }
    updateCoordinates();
  }

  private boolean showCoords = false;
  private JLabel coords = new JLabel("") {
    @Override
    public Dimension getMinimumSize() {
      return new Dimension(20, 24);
    }
    @Override
    public boolean isValidateRoot() {
      // This prevents setText from causing a revalidation and re-layout of
      // entire application frame, canvas, etc.
      return true;
    }
  };
  private int coords_x = 0, coords_y = 0;
  private void updateCoordinates() {
    coords.setText(String.format("  zoom: %s  pos: %d, %d",
          zoomString(), coords_x, coords_y));
  }
  private MouseAdapter coordListener = new MouseAdapter() {
    @Override
    public void mouseMoved(MouseEvent e) {
      coords_x = e.getX();
      coords_y = e.getY();
      updateCoordinates();
    }
    @Override
    public void mouseExited(MouseEvent e) { coords.setText(" "); } // this does not work
  };
  private PropertyChangeListener zoomListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      updateCoordinates();
    }
  };


  public void showCoordinates(boolean value) {
    if (showCoords == value)
      return;
    showCoords = value;
    if (showCoords) {
      updateCoordinates();
      add(coords, BorderLayout.SOUTH);
      canvas.addMouseMotionListener(coordListener);
      model.addPropertyChangeListener(ZoomModel.ZOOM, zoomListener);
    } else {
      remove(coords);
      canvas.removeMouseMotionListener(coordListener);
      model.removePropertyChangeListener(ZoomModel.ZOOM, zoomListener);
    }
    revalidate();
  }
}
