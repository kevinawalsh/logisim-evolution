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

package com.cburch.logisim.std.base;
import static com.cburch.logisim.std.Strings.S;

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.CutterTool;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.MenuTool;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.TextTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.WiringTool;

public class Base extends Library {
  private final List<Tool> tools;
  private final AddTool textAdder = new AddTool(Base.class, Text.FACTORY);

  public Base() {
    WiringTool wiring = new WiringTool();
    SelectTool select = new SelectTool();
    CutterTool cutter = new CutterTool();

    tools = Arrays.asList(new Tool[] {
      PokeTool.SINGLETON,
      new EditTool(select, wiring),
      // Select by itself is kind of useless. It can select and move things, or
      // click to edit attributes. But it can't modify wires like EditTool can.
      // Leave it in, maybe useful for custom keyboard/mouse mappings.
      select,
      wiring,
      cutter,
      new TextTool(), 
      // MenuTool is kind of useless, but necessary for custom keyboard/mouse mappings,
      // e.g. for the right-click binding.
      MenuTool.SINGLETON,
      // TextTool internally uses Text.FACTORY, but also supports click-to-edit,
      // custom cursor, etc. A dedicated "add text tool" is useless. But it
      // needs to exist here for XmlCircuitReader to re-construct circuits, and
      // for XmlWriter to serialize circuits.
      textAdder,
    });
  }

  public boolean isDeprecatedTool(String name) {
    return name.equals("Text");
  }

  @Override
  public String getDisplayName() {
    return S.get("baseLibrary");
  }

  @Override
  public String getName() {
    return "Base";
  }

  @Override
  public List<Tool> getTools() {
    return tools;
  }

  @Override
  public boolean displayInToolbox() {
    return false;
  }
}
