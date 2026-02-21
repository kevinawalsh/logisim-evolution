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

package com.cburch.logisim.std.io;

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;

class FileViewerAttributes extends AbstractAttributeSet {
  private final /*public*/ static FileViewerAttributes instance = new FileViewerAttributes(); // why?
      
  // WARNING: The prefix of these lists before STORAGE must be identical. The list of possible
  // attributes depends on STORAGE, so during xml file loading STORAGE must be set before the
  // remaining attributes.
  private static final List<Attribute<?>> FILE_EMBEDDED_ATTRIBUTES =
      Arrays.asList(new Attribute<?>[] {
        FileViewer.ATTR_WIDTH, FileViewer.ATTR_LINES, FileViewer.ATTR_COLS, FileViewer.ATTR_SELECT, FileViewer.ATTR_STORAGE, FileViewer.ATTR_CONTENTS });

  private static final List<Attribute<?>> FILE_LINKED_ATTRIBUTES =
      Arrays.asList(new Attribute<?>[] {
        FileViewer.ATTR_WIDTH, FileViewer.ATTR_LINES, FileViewer.ATTR_COLS, FileViewer.ATTR_SELECT, FileViewer.ATTR_STORAGE, FileViewer.ATTR_FILENAME });

  BitWidth width = BitWidth.create(16);
  int lines = 5;
  int cols = 40;
  AttributeOption select = FileViewer.BY_LINE;
  AttributeOption storage = FileViewer.FILE_EMBED;
  List<String> contents = null;
  Attributes.LinkedFile filename = null;

  public FileViewerAttributes() { }

  @Override
  protected void copyInto(AbstractAttributeSet destObj) {
    ; // nothing to do
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    if (storage == FileViewer.FILE_EMBED)
      return FILE_EMBEDDED_ATTRIBUTES;
    else
      return FILE_LINKED_ATTRIBUTES;
  }

  @Override
  public <E> E getValue(Attribute<E> attr) {
    if (attr == FileViewer.ATTR_WIDTH)
      return (E) width;
    if (attr == FileViewer.ATTR_LINES)
      return (E) Integer.valueOf(lines);
    if (attr == FileViewer.ATTR_COLS)
      return (E) Integer.valueOf(cols);
    if (attr == FileViewer.ATTR_SELECT)
      return (E) select;
    if (attr == FileViewer.ATTR_STORAGE)
      return (E) storage;
    if (attr == FileViewer.ATTR_CONTENTS )
      return (E) contents;
    if (attr == FileViewer.ATTR_FILENAME )
      return (E) filename;
    return null;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == FileViewer.ATTR_WIDTH)
      width = (BitWidth) value;
    else if (attr == FileViewer.ATTR_LINES)
      lines = (Integer) value;
    else if (attr == FileViewer.ATTR_COLS)
      cols = (Integer) value;
    else if (attr == FileViewer.ATTR_SELECT)
      select = (AttributeOption) value;
    else if (attr == FileViewer.ATTR_STORAGE) {
      if (value != storage) {
        fireAttributeListChanged(); // why before changing value?
        storage = (AttributeOption) value;
      }
    }
    else if (attr == FileViewer.ATTR_CONTENTS )
      contents = (List<String>) value;
    else if (attr == FileViewer.ATTR_FILENAME )
      filename = (Attributes.LinkedFile) value;
  }

}
