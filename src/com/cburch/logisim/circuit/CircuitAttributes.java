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

package com.cburch.logisim.circuit;
import static com.cburch.logisim.circuit.Strings.S;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.circuit.appear.CircuitAppearanceEvent;
import com.cburch.logisim.circuit.appear.CircuitAppearanceListener;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.Debug;

// A circuit has one CircuitAttributes to hold parameters related to the circuit itself, like
// CIRCUIT_NAME and CIRCUIT_APPEARANCE.
// By contrast, the subcircuit factory for a circuit uses SubcircuitAttributes, which holds
// parameters related to a specific instance, like FACING and LABEL.
public class CircuitAttributes extends AttributeSets.ArrayBacked {

  private final Circuit source;

  public CircuitAttributes(Circuit source, String name) {
    super(STATIC_ATTRS, STATIC_DEFAULTS);
    this.source = source;
    // no need to save name, it already appears as an attribute of circuit's outer xml node
    setToSave(CIRCUIT_NAME, false);
    setAttr(CIRCUIT_NAME, name);
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    super.updateAttr(attr, value);
    if (attr == CIRCUIT_NAME) {
      // When the name changes, we fire CircuitListener.circuitChanged().
      source.fireEvent(CircuitEvent.ACTION_SET_NAME, value);
    } else if (attr == CIRCUIT_APPEARANCE) {
      // If the appearance just changeed to a default (computed, non-custom)
      // style, recalculate the shape now.
      if (value == APPEAR_CLASSIC || value == APPEAR_FPGA) {
        // FIXME - confirm... CircuitChange should have already assured that we
        // are within a suitable transaction, locking any parent circuits, etc.
        source.getAppearance().setDefaultAppearance(true);
        // source.getLocker().execute(new GoToDefaultAppearanceTransaction());
      } else { // CUSTOM
        // Do nothing: 
        // - If user switched to CUSTOM in the properties panel, nothing changes yet,
        //   until user goes into appearance editor and makes actual changes.
        // - If Circuit made the switch to CUSTOM in response to appearance editor activity,
        //   as signaled by a callback from appearance, then CircuitAppearance was already changed.
        //   (FIXME: MAYBE? does CircuitAppearance ever call fire to relay changes without
        //   updating its own state first?)
      }    
    }
  }

  // private class GoToDefaultAppearanceTransaction extends CircuitTransaction {
  //   @Override
  //   protected Map<Circuit, Integer> getAccessedCircuits() {
  //     Map<Circuit, Integer> accessMap = new HashMap<Circuit, Integer>();
  //     for (Circuit supercirc : source.getCircuitsUsingThis())
  //       accessMap.put(supercirc, READ_WRITE);
  //     return accessMap;
  //   }
  //   @Override
  //   protected void run(CircuitMutator mutator) {
  //     source.getAppearance().setDefaultAppearance(true);
  //   }
  // }

  public static final Attribute<String> CIRCUIT_NAME = Attributes.forString(
      "circuit", S.getter("circuitName"));

  public static final Attribute<String> CIRCUIT_REVISION = Attributes
      .forString("clabel", S.getter("circuitRevisionAttr"));

  // TODO: allow "don't show" as an option for the revision label placement?
  public static final Attribute<Direction> CIRCUIT_REVISION_FACING_ATTR = Attributes
      .forDirection("clabelup", S.getter("circuitRevisionDirAttr"));

  public static final Attribute<Font> CIRCUIT_REVISION_FONT_ATTR = Attributes
      .forFont("clabelfont", S.getter("circuitRevisionFontAttr"));

  public static final Attribute<Boolean> CIRCUIT_IS_VHDL_BOX = Attributes
      .forBoolean("circuitvhdl", S.getter("circuitIsVhdl"));
  public static final Attribute<String> CIRCUIT_VHDL_PATH = Attributes
      .forString("circuitvhdlpath", S.getter("circuitVhdlPath"));

  public static final AttributeOption APPEAR_CLASSIC = StdAttr.APPEAR_CLASSIC;
  public static final AttributeOption APPEAR_FPGA = StdAttr.APPEAR_FPGA;
  public static final AttributeOption APPEAR_CUSTOM = new AttributeOption(
      "custom", S.getter("circuitCustomAppearance"));
  public static final Attribute<AttributeOption> CIRCUIT_APPEARANCE = Attributes
      .forOption("appearance", S.getter("circuitAppearanceAttr"),
          new AttributeOption[] { APPEAR_CLASSIC, APPEAR_FPGA, APPEAR_CUSTOM });

  static final Attribute<?>[] STATIC_ATTRS = {
    CIRCUIT_NAME,
    CIRCUIT_REVISION, CIRCUIT_REVISION_FACING_ATTR, CIRCUIT_REVISION_FONT_ATTR,
    CIRCUIT_IS_VHDL_BOX, CIRCUIT_VHDL_PATH,
    CIRCUIT_APPEARANCE };

  static final Object[] STATIC_DEFAULTS = {
    "",
    "", Direction.EAST, StdAttr.DEFAULT_LABEL_FONT,
    false, "",
    APPEAR_FPGA
  };

}
