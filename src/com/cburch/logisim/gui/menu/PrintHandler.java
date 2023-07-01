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

package com.cburch.logisim.gui.menu;
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import com.cburch.logisim.gui.main.ExportImage;
import com.cburch.logisim.util.GifEncoder;
import com.cburch.logisim.util.FileChooser;

public abstract class PrintHandler implements Printable {

  static File lastExportedFile;

  public static File getLastExported() {
      return lastExportedFile;
  }

  public static void setLastExported(File f) {
      lastExportedFile = f;
  }

  public void actionPerformed(Frame parent, ActionEvent e) {
    Object src = e.getSource();
    if (src == LogisimMenuBar.PRINT)
      print(parent);
    else if (src == LogisimMenuBar.EXPORT_IMAGE)
      exportImage(parent);
  }

  public void print(Frame parent) {
    PageFormat format = new PageFormat();
    PrinterJob job = PrinterJob.getPrinterJob();
    job.setPrintable(this, format);
    if (!job.printDialog())
      return;
    try {
      job.print();
    } catch (PrinterException e) {
      JOptionPane.showMessageDialog(parent,
          S.fmt("printError", e.toString()),
          S.get("printErrorTitle"), JOptionPane.ERROR_MESSAGE);
    }
  }

  public void exportImage(Frame parent) {
    FileChooser.Filter[] filters = {
      ExportImage.getFilter(ExportImage.FORMAT_PNG),
      ExportImage.getFilter(ExportImage.FORMAT_JPG),
      ExportImage.getFilter(ExportImage.FORMAT_GIF),
    };
    FileChooser chooser = FileChooser.createSelected(parent,
        getLastExported());
    chooser.setTitle(S.get("exportImageFileSelect"));
    chooser.addFilenameFilter(filters[0]);
    chooser.addFilenameFilter(filters[1]);
    chooser.addFilenameFilter(filters[2]);

    if (!chooser.showSaveDialog())
      return;
    File dest = chooser.getSelectedFile();
    String fmt;
    if (filters[2].accept(dest)) {
      fmt = ExportImage.FORMAT_GIF;
    } else if (filters[1].accept(dest)) {
      fmt = ExportImage.FORMAT_JPG;
    } else if (filters[0].accept(dest)) {
      fmt = ExportImage.FORMAT_PNG;
    } else {
      fmt = ExportImage.FORMAT_PNG;
      dest = new File(dest.getParentFile(), dest.getName() + ".png");
      if (dest.exists()) {
        int confirm = JOptionPane.showConfirmDialog(parent,
            S.get("confirmOverwriteMessage"),
            S.get("confirmOverwriteTitle"),
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION)
          return;
      }
    }
    setLastExported(dest);
    exportImage(parent, dest, fmt);
  }

  @Override
  public int print(Graphics pg, PageFormat pf, int pageNum) {
    double imWidth = pf.getImageableWidth();
    double imHeight = pf.getImageableHeight();
    Graphics2D g = (Graphics2D) pg;
    g.translate(pf.getImageableX(), pf.getImageableY());
    return print(g, pf, pageNum, imWidth, imHeight);
  }

  public abstract int print(Graphics2D g, PageFormat pf, int pageNum, double w, double h);

  public abstract Dimension getExportImageSize();

  public abstract void paintExportImage(BufferedImage img, Graphics2D g);

  public void exportImage(Frame parent, File dest, String fmt) {

    Dimension d = getExportImageSize();
    if (d == null) {
      JOptionPane.showMessageDialog(parent, S.get("couldNotCreateImage"));
      return;
    }

    BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
    Graphics base = img.getGraphics();
    Graphics2D g = (Graphics2D)base.create();
    try {
      g.setColor(Color.white);
      g.fillRect(0, 0, d.width, d.height);
      g.setColor(Color.black);

      try {
        paintExportImage(img, g);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(parent, S.get("couldNotCreateImage"));
        return;
      }

      try {
        switch (fmt) {
        case ExportImage.FORMAT_GIF:
          GifEncoder.toFile(img, dest, null);
          break;
        case ExportImage.FORMAT_PNG:
          ImageIO.write(img, "PNG", dest);
          break;
        case ExportImage.FORMAT_JPG:
          ImageIO.write(img, "JPEG", dest);
          break;
        }
      } catch (Exception e) {
        JOptionPane.showMessageDialog(parent, S.get("couldNotCreateFile"));
        return;
      }
    } finally {
        g.dispose();
    }
  }

}
