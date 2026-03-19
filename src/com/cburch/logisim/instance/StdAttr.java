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

package com.cburch.logisim.instance;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Font;
import java.awt.Color;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;

public class StdAttr {
  public static final Attribute<Direction> FACING = Attributes.forDirection(
      "facing", S.getter("stdFacingAttr"));

  public static final Attribute<BitWidth> WIDTH = Attributes.forBitWidth(
      "width", S.getter("stdDataWidthAttr"));

  public static final AttributeOption TRIG_RISING = new AttributeOption(
      "rising", S.getter("stdTriggerRising"));
  public static final AttributeOption TRIG_FALLING = new AttributeOption(
      "falling", S.getter("stdTriggerFalling"));
  public static final AttributeOption TRIG_HIGH = new AttributeOption("high",
      S.getter("stdTriggerHigh"));
  public static final AttributeOption TRIG_LOW = new AttributeOption("low",
      S.getter("stdTriggerLow"));
  public static final Attribute<AttributeOption> TRIGGER = Attributes
      .forOption("trigger", S.getter("stdTriggerAttr"),
          new AttributeOption[] { TRIG_RISING, TRIG_FALLING,
            TRIG_HIGH, TRIG_LOW });
  public static final Attribute<AttributeOption> EDGE_TRIGGER = Attributes
      .forOption("trigger", S.getter("stdTriggerAttr"),
          new AttributeOption[] { TRIG_RISING, TRIG_FALLING });
  
  public static Attribute<Font> TEXT_FONT = Attributes.forFont("font",
      S.getter("textFontAttr"));

  public static final Attribute<String> LABEL = Attributes.forString("label",
      S.getter("stdLabelAttr"));

  public static final Attribute<Font> LABEL_FONT = Attributes.forFont(
      "labelfont", S.getter("stdLabelFontAttr"));
  public static final Font DEFAULT_LABEL_FONT = new Font("SansSerif",
      Font.PLAIN, 12);
  public static final Attribute<Color> LABEL_COLOR = Attributes.forColor(
      "labelcolor", S.getter("stdLabelColorAttr"));
  public static final AttributeOption LABEL_CENTER = new AttributeOption("center",
      "center", S.getter("stdLabelCenter"));
  public static final Attribute<Object> LABEL_LOC = Attributes.forOption(
      "labelloc", S.getter("stdLabelLocAttr"), new Object[] {
          LABEL_CENTER, Direction.NORTH, Direction.SOUTH,
          Direction.EAST, Direction.WEST });
  public static final Attribute<Object> LABEL_EDGE_LOC = Attributes.forOption(
      "labelloc", S.getter("stdLabelLocAttr"), new Object[] {
          Direction.NORTH, Direction.SOUTH,
          Direction.EAST, Direction.WEST });

  public static final AttributeOption APPEAR_CLASSIC = new AttributeOption(
      "classic", S.getter("stdClassicAppearance"));
  public static final AttributeOption APPEAR_FPGA = new AttributeOption(
      "evolution", S.getter("stdEvolutionAppearance"));
  public static final Attribute<AttributeOption> APPEARANCE = Attributes
      .forOption("appearance", S.getter("stdAppearanceAttr"),
          new AttributeOption[] { APPEAR_CLASSIC, APPEAR_FPGA });

  public static final AttributeOption SIGNED_OPTION = new AttributeOption(
      "twosComplement", "twosComplement", S.getter("twosComplementOption"));
  public static final AttributeOption UNSIGNED_OPTION = new AttributeOption(
      "unsigned", "unsigned", S.getter("unsignedOption"));
  public static final Attribute<AttributeOption> MODE = Attributes
      .forOption("mode", S.getter("comparatorType"),
          new AttributeOption[] { SIGNED_OPTION, UNSIGNED_OPTION });

}
