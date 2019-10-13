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

import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;

import com.cburch.logisim.Main;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.XmlWriter;
import com.cburch.logisim.gui.main.LayoutClipboard;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.DragDrop;

public abstract class Library {

  public boolean directlyContains(ComponentFactory query) {
    if (contains(query))
      return true;
    for (Library lib : getLibraries())
      if (lib.contains(query))
        return true;
    return false;
  }

  public Library findLibraryFor(ComponentFactory query) {
    // depth 0 first
    if (contains(query))
      return this;
    // depth 1 next
    for (Library lib : getLibraries())
      if (lib.contains(query))
        return lib;
    // depth 2 or more next, with no particular preference
    for (Library lib : getLibraries()) {
      Library sublib = lib.findNestedLibraryFor(query);
      if (sublib != null)
        return sublib;
    }
    return null;
  }

  private Library findNestedLibraryFor(ComponentFactory query) {
    for (Library lib : getLibraries()) {
      if (lib.contains(query))
        return lib;
      Library sublib = lib.findNestedLibraryFor(query);
      if (sublib != null)
        return sublib;
    }
    return null;
  }

  public Tool findEquivalentTool(Tool query) {
    for (Tool tool : getTools()) {
      if (tool.equals(query))
        return tool;
    }
    for (Library lib : getLibraries()) {
      Tool tool = lib.findEquivalentTool(query);
      if (tool != null)
        return tool;
    }
    return null;
  }

  public boolean contains(ComponentFactory query) {
    return indexOf(query) >= 0;
  }

  public boolean containsFromSource(Tool query) {
    for (Tool tool : getTools()) {
      if (tool.sharesSource(query)) {
        return true;
      }
    }
    return false;
  }

  public String getDisplayName() {
    return getName();
  }

  public List<Library> getLibraries() {
    return Collections.emptyList();
  }

  public Library getLibrary(String name) {
    for (Library lib : getLibraries()) {
      if (lib.getName().equals(name)) {
        return lib;
      }
    }
    return null;
  }

  public String getName() {
    return getClass().getName();
  }

  public Tool getTool(String name) {
    for (Tool tool : getTools()) {
      if (tool.getName().equals(name)) {
        return tool;
      }
    }
    return null;
  }

  public abstract List<? extends Tool> getTools();

  public List<? extends Tool> getToolsAndSublibraryTools() {
    ArrayList<Tool> allTools = new ArrayList<>();
    allTools.addAll(getTools());
    for (Library sublib : getLibraries())
      allTools.addAll(sublib.getToolsAndSublibraryTools());
    return allTools;
  }

  public List<? extends Tool> getToolsAndDirectLibraryTools() {
    ArrayList<Tool> allTools = new ArrayList<>();
    allTools.addAll(getTools());
    for (Library sublib : getLibraries())
      allTools.addAll(sublib.getTools());
    return allTools;
  }

  public int indexOf(ComponentFactory query) {
    int index = -1;
    for (Tool obj : getTools()) {
      index++;
      if (obj instanceof AddTool) {
        AddTool tool = (AddTool) obj;
        if (tool.getFactory() == query)
          return index;
      }
    }
    return -1;
  }

  public AddTool findToolFor(ComponentFactory query) {
    for (Tool obj : getTools()) {
      if (obj instanceof AddTool) {
        AddTool tool = (AddTool) obj;
        if (tool.getFactory() == query)
          return tool;
      }
    }
    for (Library lib : getLibraries()) {
      AddTool tool = lib.findToolFor(query);
      if (tool != null)
        return tool;
    }
    return null;
  }

  public boolean isDirty() {
    return false;
  }

  @Override
  public String toString() {
    return getName();
  }

  public boolean displayInToolbox() {
    return true;
  }
  
  public static final DragDrop dnd = Main.headless ? null : new DragDrop(
      Library.class, LayoutClipboard.mimeTypeLibraryClip);

  public class TransferableLibrary implements DragDrop.Support, DragDrop.Ghost {
    private LogisimFile file;
    private Project proj;

    public TransferableLibrary(LogisimFile file, Project proj) {
      this.file = file;
      this.proj = proj;
    }

    public LogisimFile getLogisimFile() { return file; }
    public Library getLibrary() { return Library.this; }

    public DragDrop getDragDrop() { return dnd; }

    @Override
    public Object convertTo(String mimetype) {
      return XmlWriter.encodeSelection(file, proj, Library.this);
    }

    @Override
    public Object convertTo(Class cls) {
      return Library.this;
    }

    JDragLabel dragLabel;
    private class JDragLabel extends JLabel {
      public void publicPaintComponent(Graphics g) { paintComponent(g); }
    }

    private JDragLabel getDragLabel() {
      if (dragLabel != null)
        return dragLabel;
      dragLabel = new JDragLabel();
      dragLabel.setOpaque(true);
      // dragLabel.setFont(plainFont);
      dragLabel.setText(getDisplayName());
      dragLabel.setIcon(UIManager.getIcon("Tree.closedIcon"));
      return dragLabel;
    }

    public void paintDragImage(JComponent dest, Graphics g, Dimension dim) {
      JDragLabel l = getDragLabel();
      l.setSize(dim);
      l.setLocation(0, 0);
      getDragLabel().publicPaintComponent(g);
    }

    public Dimension getSize() {
      return getDragLabel().getPreferredSize();
    }
  }

  // removeLibrary() is not used. It is here for JAR-library backwards compatibility
  // with other verisions of Logisim-Evolution and/or original Logisim.
  public boolean removeLibrary(String Name) { return false; }

}
