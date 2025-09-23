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

package com.cburch.logisim.std.ext;
import static com.cburch.logisim.std.Strings.S;

import java.util.List;

import com.cburch.logisim.tools.FactoryDescription;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class Ext extends Library {

  private static FactoryDescription[] DESCRIPTIONS = {
    new FactoryDescription("SerialIn", S.getter("serialInputComponent"), "serial-in.png", "SerialIn"),
    // new FactoryDescription("SerialOut", S.getter("serialOutputComponent"), "serial-in.png", "SerialOut"),
    // new FactoryDescription("WebGet", S.getter("httpComponent"), "http-get.png", "WebGet"),
    new FactoryDescription("HttpIn", S.getter("httpInputComponent"), "http-in.png", "HttpIn"),
    new FactoryDescription("RealTimeClock", S.getter("rtcComponent"), "rtc.png", "RealTimeClock"),
  };

  private List<Tool> tools = null;

  public Ext() { }

  @Override
  public String getDisplayName() {
    return S.get("externalsLibrary");
  }

  @Override
  public String getName() {
    return "External I/O";
  }

  @Override
  public List<Tool> getTools() {
    if (tools == null) {
      tools = FactoryDescription.getTools(Ext.class, DESCRIPTIONS);
    }
    return tools;
  }

}
