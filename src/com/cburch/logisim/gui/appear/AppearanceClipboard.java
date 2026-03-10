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

package com.cburch.logisim.gui.appear;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.XmlClipReader;
import com.cburch.logisim.file.XmlWriter;
import com.cburch.logisim.file.LoadCanceledByUser;
import com.cburch.logisim.gui.main.SystemClipboard;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.DragDrop;

class AppearanceClipboard implements ClipboardOwner {

  static final AppearanceClipboard DATA = new AppearanceClipboard(); // singleton

  // AppearanceClipboard holds a DrawingsClip object.
  
  public static final String mimeTypeBase = "application/vnd.kwalsh.logisim-evolution-hc";
  public static final String mimeTypeAppearanceClip = mimeTypeBase + ".appearance;class=java.lang.String";
 

  public final DragDrop dnd = new DragDrop(mimeTypeAppearanceClip);
 
  private AppearanceClipboard() { }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) { }

  public void set(Project proj, DrawingsClip value) {
    XmlData data = encode(proj, value);
    if (data != null)
      SystemClipboard.setContents(data, this); 
  }

  public DrawingsClip get(Project proj) {
    return decode(proj, SystemClipboard.getContents(null));
  }

  private DrawingsClip decode(Project proj, Transferable incoming) {
    try {
      if (incoming == null || !incoming.isDataFlavorSupported(dnd.dataFlavor))
        return null;
      String xml = (String)incoming.getTransferData(dnd.dataFlavor);
      if (xml == null || xml.length() == 0)
        return null;
      LogisimFile srcFile = proj.getLogisimFile();
      XmlClipReader.ReadClipContext ctx = XmlClipReader.parseSelection(srcFile, xml);
      if (ctx == null)
        return null;
      return ctx.getDrawings();
    } catch (LoadCanceledByUser e) {
      return null;
    } catch (Exception e) {
      proj.showError("Error parsing clipboard data", e);
      return null;
    }
  }

  private XmlData encode(Project proj, DrawingsClip value) {
    String xml = XmlWriter.encodeSelection(proj.getLogisimFile(), proj, value);
    return xml == null ? null : new XmlData(xml);
  }


  private class XmlData implements DragDrop.Support {
    String xml;
    XmlData(String xml) { this.xml = xml; }
    @Override
    public DragDrop getDragDrop() { return dnd; }
    @Override
    public Object convertToFlavor(int idx, Object dataFlavor) { return xml; }
  }

  public boolean isAvailable() {
    return SystemClipboard.isDataFlavorAvailable(dnd.dataFlavor);
  }

}
