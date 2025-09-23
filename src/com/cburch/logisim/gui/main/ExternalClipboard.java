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

import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.Collections;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.XmlWriter;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.DragDrop;

public class ExternalClipboard<T> implements ClipboardOwner {

  public static final DataFlavor stringFlavor = DataFlavor.stringFlavor;
  public static final DataFlavor imageFlavor = DataFlavor.imageFlavor;

  public static final ExternalClipboard<String> forString = new ExternalClipboard<>(stringFlavor);

  public static final ExternalClipboard<Image> forImage = new ExternalClipboard<>(imageFlavor);

  private DataFlavor plainFlavor;
  private T current; // the owned system clip, if any, for this manager (fixme: not necessary?)
  private DragDrop dnd;

  private ExternalClipboard(DataFlavor plainFlavor) {
    this.plainFlavor = plainFlavor;
    this.dnd = new DragDrop(LayoutClipboard.mimeTypeComponentsClip, plainFlavor);
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    current = null;
  }

  public boolean isAvailable() {
    return SystemClipboard.isDataFlavorAvailable(plainFlavor);
    // Note, we don't check SystemClipboard.isDataFlavorAvailable(dnd.dataFlavor)
    // as LayoutClipboard.forComponent.isAvailable() takes priority for that flavor.
  }
  
  public void set(Project proj, Component comp, T plain) {
    String xml = XmlWriter.encodeSelection(proj.getLogisimFile(), proj, Collections.singletonList(comp));
    Transferable xfer = null;
    if (xml == null)
      xfer = plainSelection(plain);
    else
      xfer = new XmlAndPlainData(plain, xml);
    if (xfer != null) {
      current = plain;
      SystemClipboard.setContents(xfer, this); 
    }
  }

  private static Transferable plainSelection(Object obj) {
    if (obj instanceof String) {
      String plain = (String)obj;
      if (plain == null || plain.length() == 0)
        return null;
      return new StringSelection(plain);
    } else if (obj instanceof Image) {
      Image plain = (Image)obj;
      if (plain == null)
        return null;
      return new ImageSelection(plain);
    }
    return null;
  }

  private class XmlAndPlainData<T> implements DragDrop.Support {
    T plain;
    String xml;
    XmlAndPlainData(T plain, String xml) { this.plain = plain; this.xml = xml; }

    @Override
    public DragDrop getDragDrop() { return dnd; }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (flavor.equals(plainFlavor))
        return plain;
      else if (flavor.equals(dnd.dataFlavor))
        return xml;
      else
        throw new UnsupportedFlavorException(flavor);
    }
  }

  private static class ImageSelection implements Transferable {
    private Image img;
    private DataFlavor[] flavors = { DataFlavor.imageFlavor };

    public ImageSelection(Image i) { img = i; }

    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException {
        if (flavor.equals(DataFlavor.imageFlavor))
          return img;
        else
          throw new UnsupportedFlavorException(flavor);
    }

    public DataFlavor[] getTransferDataFlavors() { return flavors; }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return flavor != null && flavor.equals(DataFlavor.imageFlavor);
    }
  }

  public T get(Project proj) {
    // if (current != null)
    //   return current;
    try {
      Transferable xfer = SystemClipboard.getContents(null);
      if (xfer != null && xfer.isDataFlavorSupported(plainFlavor))
        return (T)xfer.getTransferData(plainFlavor);
    } catch (Exception e) {
      proj.showError("Error parsing clipboard data", e);
    }
    return null;
  }

}
