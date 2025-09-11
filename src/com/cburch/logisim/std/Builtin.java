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

package com.cburch.logisim.std;
import static com.cburch.logisim.std.Strings.S;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.bfh.logisim.library.BFHPraktika;
import com.cburch.logisim.std.arith.Arithmetic;
import com.cburch.logisim.std.base.Base;
import com.cburch.logisim.std.ext.Ext;
import com.cburch.logisim.std.gates.Gates;
import com.cburch.logisim.std.io.Io;
import com.cburch.logisim.std.audio.Audio;
import com.cburch.logisim.std.memory.Memory;
import com.cburch.logisim.std.plexers.Plexers;
import com.cburch.logisim.std.wiring.Wiring;
import com.cburch.logisim.std.wiring.Analog;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class Builtin extends Library {
  private List<Library> libraries = null;

  public Builtin() {
    libraries = Arrays.asList(new Library[] { new Base(), new Gates(),
      new Wiring(), new Analog(), new Plexers(), new Arithmetic(), new Memory(),
      new Io(), new Audio(), new BFHPraktika(), new Ext() });
  }

  public static boolean isBuiltinLibrary(Class<? extends Library> libClass) {
    return libClass == Builtin.class
        || libClass == Base.class
        || libClass == Gates.class
        || libClass == Wiring.class
        || libClass == Analog.class
        || libClass == Plexers.class
        || libClass == Arithmetic.class
        || libClass == Memory.class
        || libClass == Io.class
        || libClass == Audio.class
        || libClass == BFHPraktika.class
        || libClass == Ext.class;
  }

  @Override
  public String getDisplayName() {
    return S.get("builtinLibrary");
  }

  @Override
  public List<Library> getLibraries() {
    return libraries;
  }

  @Override
  public String getName() {
    return "Builtin";
  }

  @Override
  public List<Tool> getTools() {
    return Collections.emptyList();
  }
}
