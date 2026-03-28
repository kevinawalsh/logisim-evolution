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

package com.cburch.logisim.file;

import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.tools.MenuTool;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.WeakList;

public class MouseMappings {
  public static interface MouseMappingsListener {
    public void mouseMappingsChanged();
  }

  private HashMap<Integer, Tool> map;
  private int cache_mods; // FIXME: this really isn't necessary, is it?
  private Tool cache_tool;

  public MouseMappings() {
    map = new HashMap<Integer, Tool>();
  }

  private WeakList<MouseMappingsListener> listeners = new WeakList<>();
  public void addMouseMappingsWeakListener(Object owner, MouseMappingsListener l) { listeners.add(owner, l); }
  public void removeMouseMappingsWeakListener(Object owner, MouseMappingsListener l) { listeners.remove(owner, l); }
  private void fireMouseMappingsChanged() { for (MouseMappingsListener l : listeners) l.mouseMappingsChanged(); }
  
  public void clear() {
    cache_mods = -1;
    map.clear();
  }

  private static final int DEFAULT_MENU_MODS[] = {
    InputEvent.CTRL_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK,
    InputEvent.BUTTON2_DOWN_MASK, 
    InputEvent.BUTTON3_DOWN_MASK,
  };

  public void reset() {
    clear();
    for (int mods : DEFAULT_MENU_MODS)
      setToolFor(mods, MenuTool.SINGLETON);
  }

  public boolean shouldOmitAllAttributesFromXml() {
    // We omit the entire mappings block in xml if everything is set to the defaults.
    if (map.size() != 3)
      return false;
    for (int mods : DEFAULT_MENU_MODS)
      if (map.get(Integer.valueOf(mods)) != MenuTool.SINGLETON)
        return false;
    return true;
  }

  public void copyFrom(MouseMappings other, LogisimFile file) {
    if (this == other)
      return;
    this.clear();
    for (Integer mods : other.map.keySet()) {
      Tool srcTool = other.map.get(mods);
      Tool dstTool = file.findEquivalentTool(srcTool);
      if (dstTool != null) {
        dstTool = dstTool.cloneTool();
        AttributeSets.copy(srcTool.getAttributeSet(),
            dstTool.getAttributeSet());
        this.map.put(mods, dstTool);
      }
    }
    fireMouseMappingsChanged();
  }

  public Set<Integer> getMappedModifiers() {
    return map.keySet();
  }

  public Map<Integer, Tool> getMappings() {
    return map;
  }

  public Tool getToolFor(int mods) {
    if (mods == cache_mods) {
      return cache_tool;
    } else {
      Tool ret = map.get(Integer.valueOf(mods));
      cache_mods = mods;
      cache_tool = ret;
      return ret;
    }
  }

  void replaceAll(Map<Tool, Tool> toolMap) {
    boolean changed = false;
    for (Map.Entry<Integer, Tool> entry : map.entrySet()) {
      Integer key = entry.getKey();
      Tool tool = entry.getValue();
      if (tool instanceof AddTool) {
        ComponentFactory factory = ((AddTool) tool).getFactory();
        if (toolMap.containsKey(factory)) {
          changed = true;
          Tool newTool = toolMap.get(factory);
          if (newTool == null) {
            map.remove(key);
          } else {
            Tool clone = newTool.cloneTool();
            LoadedLibrary.copyAttributes(clone.getAttributeSet(),
                tool.getAttributeSet());
            map.put(key, clone);
          }
        }
      } else {
        if (toolMap.containsKey(tool)) {
          changed = true;
          Tool newTool = toolMap.get(tool);
          if (newTool == null) {
            map.remove(key);
          } else {
            Tool clone = newTool.cloneTool();
            LoadedLibrary.copyAttributes(clone.getAttributeSet(),
                tool.getAttributeSet());
            map.put(key, clone);
          }
        }
      }
    }
    if (changed)
      fireMouseMappingsChanged();
  }

  public void setToolFor(int mods, Tool tool) {
    if (mods == cache_mods)
      cache_mods = -1;

    if (tool == null) {
      Object old = map.remove(Integer.valueOf(mods));
      if (old != null)
        fireMouseMappingsChanged();
    } else {
      Object old = map.put(Integer.valueOf(mods), tool);
      if (old != tool)
        fireMouseMappingsChanged();
    }
  }

  public boolean usesToolFromSource(Tool query) {
    for (Tool tool : map.values()) {
      if (tool.sharesSource(query)) {
        return true;
      }
    }
    return false;
  }
}
