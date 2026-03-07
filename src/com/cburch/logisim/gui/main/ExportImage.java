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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.UniquelyNamedThread;

public class ExportImage {

  private static class ExportThread extends UniquelyNamedThread {
    Frame frame;
    Canvas canvas;
    File dest;
    FileFilter filter;
    String ext;
    List<Circuit> circuits;
    double scale;
    boolean printerView;
    ProgressMonitor monitor;

    ExportThread(Frame frame, Canvas canvas, File dest, FileFilter f,
        String ext, List<Circuit> circuits, double scale, boolean printerView,
        ProgressMonitor monitor) {
      super("ExportThread");
      this.frame = frame;
      this.canvas = canvas;
      this.dest = dest;
      this.filter = f;
      this.ext = ext;
      this.circuits = circuits;
      this.scale = scale;
      this.printerView = printerView;
      this.monitor = monitor;
    }

    private void export(Circuit circuit) {
      File filename;
      if (dest.isDirectory()) {
        filename = new File(dest, circuit.getName() + ext);
      } else if (filter.accept(dest)) {
        filename = dest;
      } else {
        String newName = dest.getName() + ext;
        filename = new File(dest.getParentFile(), newName);
      }
      String msg = exportImage(canvas, circuit,
          scale, printerView, filename, ext, monitor);
      if (msg != null) {
        JOptionPane.showMessageDialog(frame, msg);
      }
    }

    @Override
    public void run() {
      for (Circuit circ : circuits) {
        export(circ);
      }
    }
  }

  public static String exportImage(Canvas canvas, Circuit circuit, double scale, boolean printerView, File dest, String format, ProgressMonitor monitor) {
    Bounds bds = circuit.getCircuitVisibleBounds().expand(BORDER_SIZE);
    int width = (int) Math.round(bds.getWidth() * scale);
    int height = (int) Math.round(bds.getHeight() * scale);
    if (width == 0)
      width = 100;
    if (height == 0)
      height = 100;
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D base = img.createGraphics();
    Graphics2D g = (Graphics2D)base.create();
    try {
      GraphicsUtil.setRenderingHintsForCanvas(g);
      g.setColor(Color.white);
      g.fillRect(0, 0, width, height);
      g.setColor(Color.black);
      g.scale(scale, scale);
      g.translate(-bds.getX(), -bds.getY());

      CircuitState circuitState = canvas.getProject().getCircuitStateForPrinting(circuit);
      ComponentDrawContext context = new ComponentDrawContext(canvas,
          circuit, circuitState, base, g, printerView);
      circuit.draw(context, null);

      switch (format) {
        case FORMAT_PNG:
          ImageIO.write(img, "PNG", dest);
          break;
        case FORMAT_JPG:
          ImageIO.write(img, "JPEG", dest);
          break;
      }
    } catch (Exception e) {
      return S.get("couldNotCreateFile");
    } finally {
      g.dispose();
      base.dispose();
      if (monitor != null)
        monitor.close();
      return null;
    }
  }

  private static class SettingsPanel extends JPanel implements ChangeListener {
    private static final long serialVersionUID = 1L;
    JSlider slider;
    JLabel curScale;
    JCheckBox printerView;
    JRadioButton formatPng;
    JRadioButton formatJpg;
    GridBagLayout gridbag;
    GridBagConstraints gbc;
    Dimension curScaleDim;

    @SuppressWarnings("rawtypes")
    SettingsPanel(JList list) {
      // set up components
      formatPng = new JRadioButton("PNG");
      formatJpg = new JRadioButton("JPEG");
      ButtonGroup bgroup = new ButtonGroup();
      bgroup.add(formatPng);
      bgroup.add(formatJpg);
      formatPng.setSelected(true);

      slider = new JSlider(JSlider.HORIZONTAL, -3 * SLIDER_DIVISIONS,
          3 * SLIDER_DIVISIONS, 0);
      slider.setMajorTickSpacing(10);
      slider.addChangeListener(this);
      curScale = new JLabel("222%");
      curScale.setHorizontalAlignment(SwingConstants.RIGHT);
      curScale.setVerticalAlignment(SwingConstants.CENTER);
      curScaleDim = new Dimension(curScale.getPreferredSize());
      curScaleDim.height = Math.max(curScaleDim.height,
          slider.getPreferredSize().height);
      stateChanged(null);

      printerView = new JCheckBox();
      printerView.setSelected(true);

      // set up panel
      gridbag = new GridBagLayout();
      gbc = new GridBagConstraints();
      setLayout(gridbag);

      // now add components into panel
      gbc.gridy = 0;
      gbc.gridx = GridBagConstraints.RELATIVE;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      gbc.insets = new Insets(5, 0, 5, 0);
      gbc.fill = GridBagConstraints.NONE;
      addGb(new JLabel(S.get("labelCircuits") + " "));
      gbc.fill = GridBagConstraints.HORIZONTAL;
      addGb(new JScrollPane(list));
      gbc.fill = GridBagConstraints.NONE;

      gbc.gridy++;
      addGb(new JLabel(S.get("labelImageFormat") + " "));
      Box formatsPanel = new Box(BoxLayout.Y_AXIS);
      formatsPanel.add(formatPng);
      formatsPanel.add(formatJpg);
      addGb(formatsPanel);

      gbc.gridy++;
      addGb(new JLabel(S.get("labelScale") + " "));
      addGb(slider);
      addGb(curScale);

      gbc.gridy++;
      addGb(new JLabel(S.get("labelPrinterView") + " "));
      addGb(printerView);
    }

    private void addGb(JComponent comp) {
      gridbag.setConstraints(comp, gbc);
      add(comp);
    }

    String getImageFormat() {
      if (formatJpg.isSelected())
        return FORMAT_JPG;
      return FORMAT_PNG;
    }

    boolean getPrinterView() {
      return printerView.isSelected();
    }

    double getScale() {
      return Math.pow(2.0, (double) slider.getValue() / SLIDER_DIVISIONS);
    }

    public void stateChanged(ChangeEvent e) {
      double scale = getScale();
      curScale.setText((int) Math.round(100.0 * scale) + "%");
      if (curScaleDim != null)
        curScale.setPreferredSize(curScaleDim);
    }
  }

  public static final FileFilter PNG_FILTER =
      Loader.makeFileFilter(S.getter("exportPngFilter"), ".png");
  public static final FileFilter JPG_FILTER =
      Loader.makeFileFilter(S.getter("exportJpgFilter"),
          ".jpg", ".jpeg", ".jpe", ".jfi", ".jfif", ".jfi");

  public static FileFilter getFilter(String fmt) {
    switch (fmt) {
    case FORMAT_PNG: return PNG_FILTER;
    case FORMAT_JPG: return JPG_FILTER;
    default:
      System.err.println("Unexpected image format; aborted!");
      return null;
    }
  }

  static void doExport(Project proj) {
    // First display circuit/parameter selection dialog
    Frame frame = proj.getFrame();
    CircuitJList list = new CircuitJList(proj, true);
    if (list.getModel().getSize() == 0) {
      JOptionPane.showMessageDialog(proj.getFrame(),
          S.get("exportEmptyCircuitsMessage"),
          S.get("exportEmptyCircuitsTitle"),
          JOptionPane.YES_NO_OPTION);
      return;
    }
    SettingsPanel options = new SettingsPanel(list);
    int action = JOptionPane.showConfirmDialog(frame, options,
        S.get("exportImageSelect"), JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (action != JOptionPane.OK_OPTION)
      return;
    List<Circuit> circuits = list.getSelectedCircuits();
    double scale = options.getScale();
    boolean printerView = options.getPrinterView();
    if (circuits.isEmpty())
      return;

    String fmt = options.getImageFormat();
    FileFilter filter = getFilter(fmt);
    if (filter == null)
      return;

    // Then display file chooser
    Loader loader = proj.getLogisimFile().getLoader();
    JFileChooser chooser = loader.createChooser();
    chooser.setAcceptAllFileFilterUsed(false);
    if (circuits.size() > 1) {
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setDialogTitle(S.get("exportImageDirectorySelect"));
    } else {
      chooser.setFileFilter(filter);
      chooser.setDialogTitle(S.get("exportImageFileSelect"));
    }
    int returnVal = chooser.showDialog(frame,
        S.get("exportImageButton"));
    if (returnVal != JFileChooser.APPROVE_OPTION)
      return;

    // Determine whether destination is valid
    File dest = chooser.getSelectedFile();
    chooser.setCurrentDirectory(dest.isDirectory() ? dest : dest.getParentFile());
    if (dest.exists()) {
      if (!dest.isDirectory()) {
        int confirm = JOptionPane.showConfirmDialog(proj.getFrame(),
            S.get("confirmOverwriteMessage"),
            S.get("confirmOverwriteTitle"),
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION)
          return;
      }
    } else {
      if (circuits.size() > 1) {
        boolean created = dest.mkdir();
        if (!created) {
          JOptionPane.showMessageDialog(proj.getFrame(),
              S.get("exportNewDirectoryErrorMessage"),
              S.get("exportNewDirectoryErrorTitle"),
              JOptionPane.YES_NO_OPTION);
          return;
        }
      }
    }

    // Create the progress monitor
    ProgressMonitor monitor = new ProgressMonitor(frame,
        S.get("exportImageProgress"), null, 0, 10000);
    monitor.setMillisToDecideToPopup(100);
    monitor.setMillisToPopup(200);
    monitor.setProgress(0);

    // And start a thread to actually perform the operation
    // (This is run in a thread so that Swing will update the
    // monitor.)
    new ExportThread(frame, frame.getCanvas(), dest, filter, fmt, circuits,
        scale, printerView, monitor).start();

  }

  private static final int SLIDER_DIVISIONS = 6;

  public static final String FORMAT_PNG = ".png";

  public static final String FORMAT_JPG = ".jpg";

  private static final int BORDER_SIZE = 5;

  private ExportImage() {
  }
}
