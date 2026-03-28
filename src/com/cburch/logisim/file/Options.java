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
import static com.cburch.logisim.file.Strings.S;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;

public class Options implements AttributeDefaultProvider {
  public static final AttributeOption GATE_UNDEFINED_IGNORE = new AttributeOption(
      "ignore", S.getter("gateUndefinedIgnore"));
  public static final AttributeOption GATE_UNDEFINED_ERROR = new AttributeOption(
      "error", S.getter("gateUndefinedError"));

  public static final Attribute<Integer> ATTR_SIM_LIMIT = Attributes
      .forInteger("simlimit", S.getter("simLimitOption"));
  public static final Attribute<Integer> ATTR_SIM_RAND = Attributes
      .forInteger("simrand", S.getter("simRandomOption"));
  public static final Attribute<Integer> ATTR_SIM_SMOOTHING = Attributes
      .forInteger("simsmooth", S.getter("simSmoothingOption"));
  public static final Attribute<AttributeOption> ATTR_GATE_UNDEFINED = Attributes
      .forOption("gateUndefined", S.getter("gateUndefinedOption"),
          new AttributeOption[] { GATE_UNDEFINED_IGNORE,
            GATE_UNDEFINED_ERROR });

  public static final Integer sim_rand_dflt = Integer.valueOf(32);

  public static final Integer sim_smoothing_dflt = Integer.valueOf(50);

  private static final Attribute<?>[] ATTRIBUTES = { ATTR_GATE_UNDEFINED,
    ATTR_SIM_LIMIT, ATTR_SIM_RAND, ATTR_SIM_SMOOTHING };
  private static final Object[] DEFAULTS = { GATE_UNDEFINED_IGNORE,
    Integer.valueOf(1000), Integer.valueOf(0), sim_smoothing_dflt };

  private AttributeSet attrs;
  private MouseMappings mmappings;
  private ToolbarData toolbar;

  public Options() {
    attrs = AttributeSets.fixedSet(ATTRIBUTES, DEFAULTS);
    mmappings = new MouseMappings();
    toolbar = new ToolbarData();
  }

  public void copyFrom(Options other, LogisimFile dest) {
    AttributeSets.copy(other.attrs, this.attrs);
    this.toolbar.copyFrom(other.toolbar, dest);
    this.mmappings.copyFrom(other.mmappings, dest);
  }

  public AttributeSet getAttributeSet() {
    return attrs;
  }

  @Override
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    for (int i = 0; i < ATTRIBUTES.length; i++)
      if (ATTRIBUTES[i] == attr)
        return DEFAULTS[i];
    return null;
  }

  @Override
  public boolean isAllDefaultValues(AttributeSet attrs, LogisimVersion ver) {
    for (int i = 0; i < ATTRIBUTES.length; i++)
      if (!DEFAULTS[i].equals(attrs.getValue(ATTRIBUTES[i])))
        return false;
    return true;
  }

  public MouseMappings getMouseMappings() {
    return mmappings;
  }

  public ToolbarData getToolbarData() {
    return toolbar;
  }
}
