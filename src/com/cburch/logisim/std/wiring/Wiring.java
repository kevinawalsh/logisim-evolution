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

package com.cburch.logisim.std.wiring;
import static com.cburch.logisim.std.Strings.S;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.SplitterFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.FactoryDescription;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class Wiring extends Library {

  private static FactoryDescription EXTENDER_DESCRIPTION =
    new FactoryDescription("Bit Extender",
        S.getter("extenderComponent"), "extender.gif",
        "BitExtender");

  // TODO: There are now two situations like this where we create multiple
  // AddTools for the same factory. Here, for Pin. And in Decor, for Text. If
  // this trend continues, we might want to add support for this directly in
  // AddTool (and/or in FactoryDescription).
  private static FactoryDescription INPUT_PIN_DESCRIPTION = 
    new FactoryDescription("Pin",
        S.getter("pinComponentInput"), (Icon)null, "Pin") {
      @Override
      public ComponentFactory getFactoryFromLibrary(Class<? extends Library> libClass) {
        return Pin.FACTORY;
      }
      @Override
      public boolean isFactoryLoaded() { return true; }
    };
  private static FactoryDescription OUTPUT_PIN_DESCRIPTION = 
    new FactoryDescription("OutputPin",
        S.getter("pinComponentOutput"), (Icon)null, "Pin") {
      @Override
      public ComponentFactory getFactoryFromLibrary(Class<? extends Library> libClass) {
        return Pin.FACTORY;
      }
      @Override
      public boolean isFactoryLoaded() { return true; }
    };

  static {
      INPUT_PIN_DESCRIPTION.setToolTip(S.getter("pinComponentInput"));
      OUTPUT_PIN_DESCRIPTION.setToolTip(S.getter("pinComponentOutput"));
  }


  private List<Tool> tools = null;

  public Wiring() {
  }

  @Override
  public String getDisplayName() {
    return S.get("wiringLibrary");
  }

  @Override
  public String getName() {
    return "Wiring";
  }
  
  @Override
  public List<Tool> getTools() {
    if (tools == null) {
      Tool inPin, outPin;
      List<Tool> ret = new ArrayList<>();
      ret.add(new AddTool(Wiring.class, SplitterFactory.instance));
      ret.add(new AddTool(Wiring.class, INPUT_PIN_DESCRIPTION));
      ret.add(new AddTool(Wiring.class, OUTPUT_PIN_DESCRIPTION) {
        @Override
        public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
          if (attr == StdAttr.FACING) return Direction.WEST;
          if (attr == StdAttr.LABEL_EDGE_LOC) return Direction.EAST;
          return super.getDefaultAttributeValue(attr, ver);
        }
        @Override
        public boolean hasDefaultAttributeValue(AttributeSet attrs, Attribute<?> attr, LogisimVersion ver) {
          if (attr == StdAttr.FACING || attr == StdAttr.LABEL_EDGE_LOC) {
            Object val = attrs.getValue(attr);
            Object dflt = getDefaultAttributeValue(attr, ver);
            if (val == null && dflt == null)
              return true;
            if (val == null || dflt == null)
              return false;
            return dflt.equals(val);
          }
          return super.hasDefaultAttributeValue(attrs, attr, ver);
        }
      });
      ret.add(new AddTool(Wiring.class, Probe.FACTORY));
      ret.add(new AddTool(Wiring.class, Tunnel.FACTORY));
      ret.add(new AddTool(Wiring.class, Clock.FACTORY));
      ret.add(new AddTool(Wiring.class, Constant.FACTORY));
      ret.add(new AddTool(Wiring.class, EXTENDER_DESCRIPTION));
      ((AddTool)ret.get(1)).getAttributeSet().setAttr(Pin.ATTR_TYPE, Pin.INPUT);
      // ((AddTool)ret.get(1)).getAttributeSet().setAttr(StdAttr.FACING, Direction.EAST);
      // ((AddTool)ret.get(1)).getAttributeSet().setAttr(StdAttr.LABEL_EDGE_LOC, Direction.WEST);
      ((AddTool)ret.get(1)).getAttributeSet().setReadOnly(Pin.ATTR_TYPE, true);
      ((AddTool)ret.get(2)).getAttributeSet().setAttr(Pin.ATTR_TYPE, Pin.OUTPUT);
      ((AddTool)ret.get(2)).getAttributeSet().setAttr(StdAttr.FACING, Direction.WEST);
      ((AddTool)ret.get(2)).getAttributeSet().setAttr(StdAttr.LABEL_EDGE_LOC, Direction.EAST);
      ((AddTool)ret.get(2)).getAttributeSet().setReadOnly(Pin.ATTR_TYPE, true);
      tools = ret;
    }
    return tools;
  }

}
