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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.PropertyChangeWeakSupport;

public class SystemClipboard {

  // SystemClipboard provieds a static/singleton to notify interested listeners of
  // changes to the system clipboard contents. Only weak references are held to
  // the listeners. This is effectively a proxy, needed because the system
  // clipboard holds strong references to listeners. Here, only one
  // strongly-held singleton object listens to the system clipboard, and all
  // weakly-referenced logisim listeners can be notified.
  //
  // SystemClipboard also provides a wrapper around the system clipboard
  // setContents(), getContents(), and other methods to retry in case the
  // clipboard is busy.

  public static final String CONTENTS_PROPERTY = "system-clipboard-contents";
  public static final Clipboard sysclip = Toolkit.getDefaultToolkit().getSystemClipboard();

  private static final PropertyChangeWeakSupport.Producer listeners =
    new PropertyChangeWeakSupport.Producer() {
      PropertyChangeWeakSupport propListeners = new PropertyChangeWeakSupport(SystemClipboard.class);
      public PropertyChangeWeakSupport getPropertyChangeListeners() { return propListeners; }
    };

  private SystemClipboard() {
    sysclip.addFlavorListener(new FlavorListener() {
      public void flavorsChanged(FlavorEvent e) {
        System.out.println("clipboard has changed");
        // wait a small amount of time until the clipboard is ready (avoid exception "cannot open system clipboard)
        // see https://stackoverflow.com/questions/51797673/in-java-why-do-i-get-java-lang-illegalstateexception-cannot-open-system-clipboa
        try { Thread.sleep(10); } catch (InterruptedException ex) { };
        listeners.firePropertyChange(CONTENTS_PROPERTY, null, null);
      }
    });
  }

  public static void addPropertyChangeWeakListener(PropertyChangeListener listener) {
    listeners.addPropertyChangeWeakListener(listener);
  }

  public static Transferable getContents(Object requestor) {
    for (int timeout = 1; timeout <= 128; timeout *= 4) { 
      try {
        return sysclip.getContents(requestor);
      } catch (IllegalStateException e) {
        try { Thread.sleep(timeout); } catch (InterruptedException ex) { };
      }
    }
    Errors.title("System Clipboard Busy").show("The system clipboard is busy and can't be accessed.");
    return null;
  }

  public static void setContents(Transferable contents, ClipboardOwner owner) {
    for (int timeout = 1; timeout <= 128; timeout *= 4) { 
      try {
        sysclip.setContents(contents, owner);
        return;
      } catch (IllegalStateException e) {
        try { Thread.sleep(timeout); } catch (InterruptedException ex) { };
      }
    }
    Errors.title("System Clipboard Busy").show("The system clipboard is busy and can't be modified.");
  }

  public static boolean isDataFlavorAvailable(DataFlavor flavor) {
    for (int timeout = 1; timeout <= 8; timeout *= 2) { 
      try {
        return sysclip.isDataFlavorAvailable(flavor);
      } catch (IllegalStateException e) {
        try { Thread.sleep(timeout); } catch (InterruptedException ex) { };
        timeout *= 2; // 1 ms, 2 ms, 4ms, 8ms
      }
    }
    return false; // silent error, user liekly made no action here
  }
}
