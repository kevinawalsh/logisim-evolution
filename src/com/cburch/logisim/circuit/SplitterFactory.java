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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.comp.AbstractComponentFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentListingFeature;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.IntegerConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.tools.key.KeyConfigurator;
import com.cburch.logisim.tools.key.ParallelConfigurator;
import com.cburch.logisim.util.Icons;
import com.cburch.logisim.util.StringGetter;

public class SplitterFactory extends AbstractComponentFactory {
  public static final SplitterFactory instance = new SplitterFactory();

  private static final Icon toolIcon = Icons.getIcon("splitter.gif");

  private SplitterFactory() {
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new SplitterAttributes();
  }

  @Override
  public Component createComponent(Location loc, AttributeSet attrs) {
    return new Splitter(loc, attrs);
  }

  //
  // user interface methods
  //
  @Override
  public void drawGhost(ComponentDrawContext context, Color color, int x,
      int y, AttributeSet attrsBase) {
    SplitterAttributes attrs = (SplitterAttributes) attrsBase;
    context.getGraphics().setColor(color);
    Location loc = Location.create(x, y);
    if (attrs.appear == SplitterAttributes.APPEAR_LEGACY) {
      SplitterPainter.drawLegacy(context, attrs, loc);
    } else {
      SplitterPainter.drawLines(context, attrs, loc);
    }
  }

  @Override
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    if (attr == SplitterAttributes.ATTR_APPEARANCE) {
      if (ver.compareTo(LogisimVersion.get(2, 6, 4)) < 0) {
        return SplitterAttributes.APPEAR_LEGACY;
      } else {
        return SplitterAttributes.APPEAR_LEFT;
      }
    } else if (attr instanceof SplitterAttributes.BitOutAttribute) {
      SplitterAttributes.BitOutAttribute a;
      a = (SplitterAttributes.BitOutAttribute) attr;
      return a.getDefault();
    } else {
      return super.getDefaultAttributeValue(attr, ver);
    }
  }

  @Override
  public StringGetter getDisplayGetter() {
    return S.getter("splitterComponent");
  }

  @Override
  public Object getFeature(Object key, AttributeSet attrs) {
    if (key == FACING_ATTRIBUTE_KEY) {
      return StdAttr.FACING;
    } else if (key == KeyConfigurator.class) {
      KeyConfigurator altConfig = ParallelConfigurator.create(
          new BitWidthConfigurator(SplitterAttributes.ATTR_WIDTH),
          new IntegerConfigurator(SplitterAttributes.ATTR_FANOUT, 1,
            32, InputEvent.ALT_DOWN_MASK));
      return JoinedConfigurator.create(new IntegerConfigurator(
            SplitterAttributes.ATTR_FANOUT, 1, 32, 0), altConfig);
    } else if (key == ComponentListingFeature.class) {
      return new MyComponentListingFeature();
    }
    return super.getFeature(key, attrs);
  }

  @Override
  public String getName() {
    return "Splitter";
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) { // nominal and visible
    SplitterAttributes attrs = (SplitterAttributes) attrsBase;
    int fanout = attrs.fanout;
    SplitterParameters parms = attrs.getParameters();
    int xEnd0 = parms.getEnd0X();
    int yEnd0 = parms.getEnd0Y();
    Bounds bds = Bounds.create(0, 0, 1, 1);
    bds = bds.add(xEnd0, yEnd0);
    bds = bds.add(xEnd0 + (fanout - 1) * parms.getEndToEndDeltaX(), yEnd0
        + (fanout - 1) * parms.getEndToEndDeltaY());
    return bds;
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y,
      AttributeSet attrs) {
    Graphics g = c.getGraphics();
    if (toolIcon != null) {
      toolIcon.paintIcon(c.getDestination(), g, x + 2, y + 2);
    }
  }

  private static class MyComponentListingFeature implements ComponentListingFeature {
    // @Override
    // public Map<String, String> getAttributeNotes(AttributeSet attrs) {
    //   HashMap<String, String> notes = new HashMap<>();
    //   String msg = "for all port layouts, multiply step_dx and step_dy by spacing";
    //   notes.put("spacing", msg);
    //   return notes;
    // }

    @Override
    public List<String> getLayoutAnalysisExcludedAttributes(AttributeSet attrs) {
      return List.of("spacing", "fanout");
    }

    @Override
    public List<List<Map.Entry<String, Object>>> getCustomPortLayout(AttributeSet attrs) {
      Object appear = attrs.getValue(SplitterAttributes.ATTR_APPEARANCE);
      Object facing = attrs.getValue(StdAttr.FACING);

      // [
      //   {
      //     "name": "bus",
      //     "type": "inout",
      //     "dx": 0,
      //     "dy": 0
      //   },
      //   {
      //     "name": "tap_",
      //     "type": "inout",
      //     "count": "fanout",
      //     "first_index": 1,
      //     "first_dx": ...,
      //     "first_dy": ...,
      //     "step_dx": ...,
      //     "step_dy": ...
      //   }
      // ]
      ArrayList<Map.Entry<String, Object>> bus = new ArrayList<>();
      bus.add(new AbstractMap.SimpleEntry<>("name", "bus"));
      bus.add(new AbstractMap.SimpleEntry<>("type", "inout"));
      bus.add(new AbstractMap.SimpleEntry<>("dx", 0));
      bus.add(new AbstractMap.SimpleEntry<>("dy", 0));

      ArrayList<Map.Entry<String, Object>> taps = new ArrayList<>();
      taps.add(new AbstractMap.SimpleEntry<>("name", "tap_"));
      taps.add(new AbstractMap.SimpleEntry<>("type", "inout"));
      taps.add(new AbstractMap.SimpleEntry<>("count", "fanout"));
      taps.add(new AbstractMap.SimpleEntry<>("first_index", 1));
      if (facing == Direction.EAST) {
        if (appear == SplitterAttributes.APPEAR_LEFT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", 20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", "-10 + spacing*(fanout-1)*10"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
        } else if (appear == SplitterAttributes.APPEAR_RIGHT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", 20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", 10));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
        } else if (appear == SplitterAttributes.APPEAR_CENTER || appear == SplitterAttributes.APPEAR_LEGACY) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", 20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", "-10*spacing*floor(fanout/2)"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
        }
      } else if (facing == Direction.WEST) {
        if (appear == SplitterAttributes.APPEAR_LEFT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", -20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", 10));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
        } else if (appear == SplitterAttributes.APPEAR_RIGHT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", -20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", "-10 + spacing*(fanout-1)*10"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
        } else if (appear == SplitterAttributes.APPEAR_CENTER || appear == SplitterAttributes.APPEAR_LEGACY) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", -20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", 0));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", "-10*spacing*floor(fanout/2)"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", "10*spacing"));
        }
      } else if (facing == Direction.NORTH) {
        if (appear == SplitterAttributes.APPEAR_LEFT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", -10));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", -20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
        } else if (appear == SplitterAttributes.APPEAR_RIGHT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", "10 + spacing*(fanout-1)*10"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", -20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
        } else if (appear == SplitterAttributes.APPEAR_CENTER || appear == SplitterAttributes.APPEAR_LEGACY) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", "10*spacing*floor((fanout-1)/2)"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", -20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
        }
      } else if (facing == Direction.SOUTH) {
        if (appear == SplitterAttributes.APPEAR_LEFT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", "10 + spacing*(fanout-1)*10"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", 20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
        } else if (appear == SplitterAttributes.APPEAR_RIGHT) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", -10));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", 20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
        } else if (appear == SplitterAttributes.APPEAR_CENTER || appear == SplitterAttributes.APPEAR_LEGACY) {
          taps.add(new AbstractMap.SimpleEntry<>("first_dx", "10*spacing*floor((fanout-1)/2)"));
          taps.add(new AbstractMap.SimpleEntry<>("step_dx", "-10*spacing"));
          taps.add(new AbstractMap.SimpleEntry<>("first_dy", 20));
          taps.add(new AbstractMap.SimpleEntry<>("step_dy", 0));
        }
      }
      return List.of(bus, taps);
    }
  }

}
