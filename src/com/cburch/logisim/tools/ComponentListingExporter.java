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

package com.cburch.logisim.tools;

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentListingFeature;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.tools.ToolTipMaker;

import static com.cburch.logisim.comp.ComponentListingFeature.PortPosition;

/**
 * Exports a JSON file describing every component in Logisim's standard library:
 * port positions for all relevant attribute combinations, attribute value ranges,
 * and optional notes. Invoked via the -dump-components CLI flag.
 */
public class ComponentListingExporter {

  // Maximum INT_RANGE size to enumerate during port-affecting attribute detection.
  // Ranges larger than this are not varied. 32 covers the largest port-affecting
  // range known (gate inputs 2-32).
  private static final int MAX_ENUM_RANGE = 32;

  public static void run(String outputFile) {
    try {
      PrintWriter out;
      CountingOutputStream cos;
      if (outputFile == null || outputFile.equals("-")) {
        cos = new CountingOutputStream(System.out);
      } else {
        cos = new CountingOutputStream(new FileOutputStream(outputFile));
      }
      out = new PrintWriter(new OutputStreamWriter(cos, StandardCharsets.UTF_8));
      try {
        buildListing(out, cos);
      } finally {
        out.flush();
        if (outputFile != null && !outputFile.equals("-"))
          out.close();
      }
      if (outputFile != null && !outputFile.equals("-"))
        System.err.printf("Component listing written to: %s\n", outputFile);
    } catch (IOException e) {
      System.err.println("Error writing component listing: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void buildListing(PrintWriter out, CountingOutputStream cos) {
    Builtin builtin = new Builtin();
    JsonWriter w = new JsonWriter(out);
    w.beginObject();
    w.key("_meta");
    w.beginObject();
    w.keyValue("version", "1.0");
    w.key("coordinate_system");
    w.beginObject();
    w.keyValue("origin", "component anchor — the 'loc' attribute in .circ XML");
    w.keyValue("x_positive", "East (right)");
    w.keyValue("y_positive", "South (down)");
    w.keyValue("units", "Logisim grid units (10 = 1 grid square), all coordinates must be multiples of 10");
    w.endObject();
    w.key("rotation_transforms");
    w.beginObject();
    w.key("standard");
    w.beginObject();
    w.keyValue("description",
        "For components with rotation='standard', port_layouts list facing=east layout of " +
        "port positions only. Derive layouts for other facings by rotating (dx,dy) around (0,0):");
    w.keyValue("west",  "(-dx, -dy)  [180 degrees]");
    w.keyValue("north", "(dy, -dx)   [CCW 90 degrees in screen coordinates]");
    w.keyValue("south", "(-dy, dx)   [CW 90 degrees in screen coordinates]");
    w.endObject();
    w.key("left-mirrored");
    w.beginObject();
    w.keyValue("description",
        "For components with rotation='left-mirrored', port_layouts list facing=east layout of " +
        "port positions only. Derive layouts for north facing by rotating (dx,dy) " +
        "around (0,0) by 90 CCW in screen coordinates. Derive layouts for west and " +
        "south facings by mirroring over the x or y axis:");
    w.keyValue("west",  "(-dx, dy)   [Mirror east facing layout over x axis]");
    w.keyValue("north", "(dy, -dx)   [CCW 90 degrees in screen coordinates]");
    w.keyValue("south", "(-dy, -dx)  [Mirror north facing layout over x axis]");
    w.endObject();
    w.key("right-mirrored");
    w.beginObject();
    w.keyValue("description",
        "For components with rotation='right-mirrored', port_layouts list facing=east layout of " +
        "port positions only. Derive layouts for south facing by rotating (dx,dy) " +
        "around (0,0) by 90 CW in screen coordinates. Derive layouts for west and " +
        "north facings by mirroring over the x or y axis:");
    w.keyValue("west",  "(-dx, dy)   [Mirror east facing layout over x axis]");
    w.keyValue("north", "(dy, dx)    [CCW 90 degrees in screen coordinates]");
    w.keyValue("south", "(-dy, dx)   [Mirror north facing layout over x axis]");
    w.endObject();
    w.key("custom");
    w.beginObject();
    w.keyValue("description",
        "For components with rotation='custom', port_layouts list all port position layouts.");
    w.endObject();
    w.key("none");
    w.beginObject();
    w.keyValue("description",
        "For components with rotation='none', port position layouts do not vary.");
    w.endObject();
    w.endObject();
    w.key("port_types");
    w.beginObject();
    w.keyValue("input",  "signal flows into the component");
    w.keyValue("output", "signal flows out of the component");
    w.keyValue("inout",  "bidirectional signal");
    w.endObject();
    w.key("output_driver_types");
    w.beginObject();
    w.keyValue("01", "standard push-pull active output driver, actively drives output to 0 or to 1");
    w.keyValue("0Z", "pull-down only output driver, actively drives output to 0 or leaves output floating");
    w.keyValue("Z1", "pull-up only output driver, actively drives output to 1 or leaves output floating");
    w.endObject();
    w.key("port_arrays");
    w.value("Some port entries have a 'count' field, indicating a linear array of " +
        "sequentially-numbered ports. Port names are formed by appending an integer " +
        "index to the 'name' prefix, starting at 'first_index' (default 0). " +
        "Port i (0-based within the array) is at: " +
        "dx = first_dx + i*step_dx, dy = first_dy + i*step_dy. " +
        "Example: name='in', count=4, first_dx=-40, first_dy=-30, step_dx=0, step_dy=20 " +
        "→ in0 at (-40,-30), in1 at (-40,-10), in2 at (-40,10), in3 at (-40,30).");
    w.key("usage");
    w.value("To find component port layout: look up the component by library and name, " +
        "find the variant whose attribute values match those in the .circ file " +
        "(defaulting to default_attr_values for any omitted attributes), then read " +
        "port dx/dy offsets (expanding port arrays as described above). " +
        "Apply rotation transform if rotation != 'custom' and facing != east. " +
        "Absolute port position = anchor_x + dx, anchor_y + dy.");
    w.endObject(); // _meta

    HashSet<ComponentFactory> done = new HashSet<>();
    for (Library lib : builtin.getLibraries()) {
      String libName = lib.getDisplayName();
      if (libName.equals("Mouse Tools"))
        continue;
      w.key(libName);
      w.beginObject();
      for (Tool tool : lib.getTools()) {
        if (!(tool instanceof AddTool)) continue;
        ComponentFactory factory = ((AddTool) tool).getFactory();
        // if (!tool.getName().equals("ROM")) continue; // keep this comment for debugging
        if (!done.add(factory)) continue;
        try {
          int a = w.linecount;
          processComponent(factory, w);
          int b = w.linecount;
          System.out.printf("Wrote %d lines of json for '%s:%s'\n", b-a, lib.getName(), factory.getName());
        } catch (Exception e) {
          System.err.println("WARNING: error processing " + factory.getName()
              + " in " + libName + ": " + e.getMessage());
          e.printStackTrace(System.err);
        }
      }
      w.endObject(); // library
    }

    w.endObject(); // root
    out.flush();
    System.out.printf("Complete: wrote %d lines (%d bytes) of json\n", w.linecount, cos.getCount());
  }

  @SuppressWarnings("unchecked")
  private static void processComponent(ComponentFactory factory, JsonWriter w) {
    AttributeSet defaultAttrs = factory.createAttributeSet();

    // Get ComponentListingFeature notes (if any), and layout variant exclusion list
    Map<String, String> attrNotes = Collections.emptyMap();
    List<String> variantAttrExclusions = Collections.emptyList();
    Object o = factory.getFeature(ComponentListingFeature.class, defaultAttrs);
    ComponentListingFeature clf = null;
    if (o instanceof ComponentListingFeature) {
      clf = (ComponentListingFeature) o;
      Map<String, String> notes = clf.getAttributeNotes(defaultAttrs);
      if (notes != null) attrNotes = notes;
      List<String> excluded = clf.getLayoutAnalysisExcludedAttributes(defaultAttrs);
      if (excluded != null) variantAttrExclusions = excluded;
    }

    // Detect FACING attribute
    Object facingAttrObj = factory.getFeature(ComponentFactory.FACING_ATTRIBUTE_KEY, defaultAttrs);
    Attribute<Direction> facingAttr = (facingAttrObj instanceof Attribute)
        ? (Attribute<Direction>) facingAttrObj : null;

    // Collect all possible attributes via DFS (handles dynamic attr lists, e.g. negate attrs)
    List<AttrInfo> attrs = collectAllAttrInfos(factory, defaultAttrs, facingAttr, attrNotes);

    // Find port-affecting non-FACING attributes
    List<Attribute<?>> portAffecting = findPortAffectingAttrs(clf, factory, defaultAttrs, attrs, facingAttr);

    // Get default port positions (facing=east if applicable)
    AttributeSet eastAttrs = defaultAttrs;
    if (facingAttr != null) {
      eastAttrs = cloneWithValue(defaultAttrs, facingAttr, "east");
    }

    // Check rotation
    String rotation = null;
    if (facingAttr == null)
      rotation = ROTATION_NONE;
    if (rotation == null && clf != null)
      rotation = clf.getLayoutRotation(eastAttrs);
    if (rotation == null)
      rotation = classifyRotation(factory, eastAttrs, facingAttr);

    // Determine anchor description from port at (0,0) facing east
    String anchor = findAnchor(factory, eastAttrs);

    // Build default_attrs map (non-facing attributes at their defaults)
    List<Object[]> defaultAttrPairs = new ArrayList<>();
    for (AttrInfo ai : attrs) {
      if (!ai.isFacing && ai.defaultVal != null) {
        defaultAttrPairs.add(new Object[]{ ai.xmlName, ai.defaultVal });
      }
    }

    // Generate variants: combinations of port-affecting non-FACING attributes
    List<Attribute<?>> variantAttrList = new ArrayList<>(portAffecting);
    List<List<String>> variantValueSets = new ArrayList<>();
    for (Attribute<?> pa : variantAttrList) {
      if (variantAttrExclusions.contains(pa.getName())) {
        String v = attrToXml(pa, eastAttrs);
        variantValueSets.add(List.of(v));
      } else {
        List<String> vals = getFiniteValues(pa);
        if (vals == null || vals.isEmpty()) {
          System.err.println("enumeration impossible?");
          variantAttrList = Collections.emptyList(); // safety
          break;
        }
        variantValueSets.add(vals);
      }
    }

    List<List<String>> combos = buildCombos(variantValueSets);
    if (combos.isEmpty()) combos.add(Collections.emptyList());

    // Determine facings to enumerate per combo
    List<Direction> facingsToEnumerate;
    if (rotation == ROTATION_NONE) {
      facingsToEnumerate = Collections.singletonList(null);
    } else if (rotation != ROTATION_CUSTOM) {
      facingsToEnumerate = Collections.singletonList(Direction.EAST);
    } else {
      facingsToEnumerate = new ArrayList<>();
      facingsToEnumerate.add(Direction.EAST);
      facingsToEnumerate.add(Direction.WEST);
      facingsToEnumerate.add(Direction.NORTH);
      facingsToEnumerate.add(Direction.SOUTH);
    }

    // --- Emit JSON ---
    w.key(factory.getName());
    w.beginObject();
    w.keyValue("description", factory.getDisplayName());
    if (anchor != null) w.keyValue("anchor", anchor);
    w.keyValue("rotation", rotation);

    // layout_affecting_attrs (those that affect port positions, excluding facing)
    w.key("layout_affecting_attrs");
    w.array(variantAttrList, pa -> pa.getName());

    // default_attr_values
    w.key("default_attr_values");
    w.beginObject();
    for (Object[] pair : defaultAttrPairs) w.keyValue((String)pair[0], (String)pair[1]);
    w.endObject();

    // attributes
    w.key("attributes");
    w.beginArray();
    for (AttrInfo ai : attrs) {
      w.beginObject();
      w.keyValue("name", ai.xmlName);
      if (ai.values instanceof List) {
        @SuppressWarnings("rawtypes")
        List<Object> vals = (List<Object>)ai.values;
        w.key("values");
        w.array(vals);
      } else {
        w.keyValue("values", (String)ai.values);
      }
      w.keyValue("description", ai.description);
      if (ai.note != null) w.keyValue("note", ai.note);
      w.endObject();
    }
    w.endArray(); // attributes

    // port_layouts
    w.key("port_layouts");
    w.beginArray();
    for (List<String> combo : combos) {
      for (Direction facing : facingsToEnumerate) {
        // Build attrs for this variant
        AttributeSet varAttrs = defaultAttrs;
        if (facing != null) {
          varAttrs = cloneWithValue(varAttrs, facingAttr, facing.toString());
          if (varAttrs == null) continue;
        }
        boolean comboFailed = false;
        for (int i = 0; i < variantAttrList.size(); i++) {
          @SuppressWarnings("rawtypes")
          Attribute attr = variantAttrList.get(i);
          varAttrs = cloneWithValue(varAttrs, attr, combo.get(i));
          if (varAttrs == null) { comboFailed = true; break; }
        }
        if (comboFailed) {
          System.err.println("ERR: bad combo?");
          continue;
        }

        List<PortPosition> customPorts = 
          clf == null ? null : clf.getCustomPortLayout(varAttrs);
        List<PortPosition> ports = customPorts != null ? null : getPortInfos(factory, varAttrs);
        if (customPorts == null && ports == null) {
          System.err.println("ERR: no ports?");
          continue;
        }

        w.beginObject();
        if (facing != null) w.keyValue("facing", facing.toString());
        for (int i = 0; i < variantAttrList.size(); i++) {
          if (variantAttrExclusions.contains(variantAttrList.get(i).getName()))
            continue;
          w.keyValue(variantAttrList.get(i).getName(), combo.get(i));
        }
        w.key("ports");
        w.beginArray();
        if (customPorts != null) {
          for (PortPosition pi : customPorts)
            toJson(pi, w);
        } else {
          for (PortPosition pi : compressPorts(ports))
            toJson(pi, w);
        }
        w.endArray(); // ports
        w.endObject(); // variant
      }
    }
    w.endArray(); // variants

    w.endObject(); // component
  }

  static void toJson(PortPosition pi, JsonWriter w) {
    w.beginObject();
    if (pi.isArray) {
      w.keyValue("name", pi.name);
      w.keyValue("type", pi.type);
      w.keyValue("count", pi.count);
      if (pi.firstIndex != null && !pi.firstIndex.toString().equals("0"))
        w.keyValue("first_index", pi.firstIndex);
      w.keyValue("first_dx", pi.dx);
      w.keyValue("first_dy", pi.dy);
      w.keyValue("step_dx", pi.stepDx);
      w.keyValue("step_dy", pi.stepDy);
    } else {
      w.keyValue("name", pi.name);
      w.keyValue("type", pi.type);
      w.keyValue("dx", pi.dx);
      w.keyValue("dy", pi.dy);
    }
    w.endObject();
  }

  // ---------------------------------------------------------------------------
  // Port position helpers
  // ---------------------------------------------------------------------------

  private static List<PortPosition> getPortInfos(ComponentFactory factory, AttributeSet attrs) {
    try {
      Component comp = factory.createComponent(Location.create(0, 0), attrs);
      List<EndData> ends = comp.getEnds();
      ToolTipMaker tt = (ToolTipMaker)comp.getFeature(ToolTipMaker.class);
      List<PortPosition> result = new ArrayList<>();
      for (int i = 0; i < ends.size(); i++) {
        EndData end = ends.get(i);
        int dx = end.getLocation().getX();
        int dy = end.getLocation().getY();
        String type = typeString(end.getType());
        String name = getNameFromToolTipOrDefault(tt, dx, dy, type, i);
        PortPosition pi = new PortPosition(name, type, dx, dy);
        result.add(pi);
      }
      return result;
    } catch (Exception e) {
      System.err.println("  WARNING: could not get ports for "
          + factory.getName() + " with attrs " + attrsToString(attrs)
          + ": " + e.getMessage());
      return null;
    }
  }

  private static String getNameFromToolTipOrDefault(ToolTipMaker tt, Object dx, Object dy, String type, int index) {
    String tip = null;
    if (tt != null) {
      try {
        int x = Integer.parseInt(dx.toString());
        int y = Integer.parseInt(dy.toString());
        tip = tt.getToolTip(new ComponentUserEvent(null, x, y));
      } catch (NumberFormatException ex) {
        ex.printStackTrace();
      }
    }
    if (tip != null && !tip.isEmpty()) {
      // EndData tooltips should have the form: "name: description"
      // TODO: should include the descriptions in the json?
      int idx = tip.indexOf(':');
      if (idx > 0) {
        String name = tip.substring(0, idx).trim().replaceAll("\\s+", "_");
        if (!name.isEmpty())
          return name;
      } else if (!tip.equalsIgnoreCase("Input") && !tip.equalsIgnoreCase("Output") && !tip.equalsIgnoreCase("Bidir")
        && !tip.equalsIgnoreCase("input") && !tip.equalsIgnoreCase("output") && !tip.equalsIgnoreCase("bidir")) {
        String name = tip.trim().replaceAll("\\s+", "_");
        if (!name.isEmpty())
          return name;
      }
    }
    return defaultPortName(type, index);
  }

  private static String defaultPortName(String type, int index) {
    if ("output".equals(type)) return index == 0 ? "OUT" : "OUT" + index;
    if ("input".equals(type))  return "IN" + index;
    return "BIDIR" + index;
  }

  private static String typeString(int type) {
    if (type == EndData.OUTPUT_ONLY) return "output";
    if (type == EndData.INPUT_ONLY)  return "input";
    return "inout";
  }

  private static String findAnchor(ComponentFactory factory, AttributeSet attrs) {
    try {
      Component comp = factory.createComponent(Location.create(0, 0), attrs);
      List<EndData> ends = comp.getEnds();
      ToolTipMaker tt = (ToolTipMaker)comp.getFeature(ToolTipMaker.class);
      for (int i = 0; i < ends.size(); i++) {
        EndData end = ends.get(i);
        int dx = end.getLocation().getX();
        int dy = end.getLocation().getY();
        if (dx == 0 && dy == 0) {
          String t = typeString(end.getType());
          String name = getNameFromToolTipOrDefault(tt, dx, dy, t, i);
          return t + " port (" + name + ")";
        }
      }
    } catch (Exception e) {
      // ignore
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Standard rotation check
  // ---------------------------------------------------------------------------

  private static final String ROTATION_STANDARD = "standard";
  private static final String ROTATION_MIRROR_LEFT = "left-mirrored";
  private static final String ROTATION_MIRROR_RIGHT = "right-mirrored";
  private static final String ROTATION_CUSTOM = "custom";
  private static final String ROTATION_NONE = "none";
  private static String classifyRotation(ComponentFactory factory,
      AttributeSet eastAttrs, Attribute<Direction> facingAttr) {
    if (facingAttr == null)
      return ROTATION_NONE;
    // System.out.println("checking rotation for " + factory.getName());
    List<PortPosition> eastPorts = getPortInfos(factory, eastAttrs);
    if (eastPorts == null) {
      System.out.println("ERR can't get ports?");
      return ROTATION_STANDARD; // NONE?
    }
    if (eastPorts.isEmpty()) {
      System.out.println("ERR no ports");
      return ROTATION_STANDARD; // NONE?
    }
    boolean maybeStandard = true;
    boolean maybeMirrorLeft = true;
    boolean maybeMirrorRight = true;

    Direction[] others = { Direction.WEST, Direction.NORTH, Direction.SOUTH };
    for (Direction dir : others) {
      // System.out.println("checking: " + dir);
      AttributeSet dirAttrs = cloneWithValue(eastAttrs, facingAttr, dir.toString());
      List<PortPosition> dirPorts = getPortInfos(factory, dirAttrs);
      if (dirPorts == null) return ROTATION_CUSTOM;
      if (dirPorts.size() != eastPorts.size()) return ROTATION_CUSTOM;

      for (int i = 0; i < eastPorts.size() && maybeStandard; i++) {
        PortPosition ep = eastPorts.get(i);
        PortPosition dp = dirPorts.get(i);
        int expectedDx, expectedDy;
        if (dir == Direction.WEST) {
          expectedDx = -(Integer)ep.dx;
          expectedDy = -(Integer)ep.dy;
        } else if (dir == Direction.NORTH) {
          expectedDx =  (Integer)ep.dy;
          expectedDy = -(Integer)ep.dx;
        } else { // SOUTH
          expectedDx = -(Integer)ep.dy;
          expectedDy =  (Integer)ep.dx;
        }
        // System.out.printf("%d vs %d, %d vs %d\n", dp.dx, expectedDx, dp.dy, expectedDy);
        maybeStandard &= ((Integer)dp.dx == expectedDx && (Integer)dp.dy == expectedDy);
      }
      for (int i = 0; i < eastPorts.size() && maybeMirrorLeft; i++) {
        PortPosition ep = eastPorts.get(i);
        PortPosition dp = dirPorts.get(i);
        int expectedDx, expectedDy;
        if (dir == Direction.WEST) {
          expectedDx = -(Integer)ep.dx;
          expectedDy = -(Integer)ep.dy * -1;
        } else if (dir == Direction.NORTH) {
          expectedDx =  (Integer)ep.dy;
          expectedDy = -(Integer)ep.dx;
        } else { // SOUTH
          expectedDx = -(Integer)ep.dy * -1;
          expectedDy =  (Integer)ep.dx;
        }
        // System.out.printf("%d vs %d, %d vs %d\n", dp.dx, expectedDx, dp.dy, expectedDy);
        maybeMirrorLeft &= ((Integer)dp.dx == expectedDx && (Integer)dp.dy == expectedDy);
      }
      for (int i = 0; i < eastPorts.size() && maybeMirrorRight; i++) {
        PortPosition ep = eastPorts.get(i);
        PortPosition dp = dirPorts.get(i);
        int expectedDx, expectedDy;
        if (dir == Direction.WEST) {
          expectedDx = -(Integer)ep.dx;
          expectedDy = -(Integer)ep.dy * -1;
        } else if (dir == Direction.NORTH) {
          expectedDx =  (Integer)ep.dy * -1;
          expectedDy = -(Integer)ep.dx;
        } else { // SOUTH
          expectedDx = -(Integer)ep.dy;
          expectedDy =  (Integer)ep.dx;
        }
        // System.out.printf("%d vs %d, %d vs %d\n", dp.dx, expectedDx, dp.dy, expectedDy);
        maybeMirrorRight &= ((Integer)dp.dx == expectedDx && (Integer)dp.dy == expectedDy);
      }
    }
    // System.out.printf("std=%s mirror=%s\n", maybeStandard?"maybe":"no", maybeMirror?"maybe":"no");
    if (maybeStandard)
      return ROTATION_STANDARD;
    else if (maybeMirrorLeft)
      return ROTATION_MIRROR_LEFT;
    else if (maybeMirrorRight)
      return ROTATION_MIRROR_RIGHT;
    else
      return ROTATION_CUSTOM;
  }

  // ---------------------------------------------------------------------------
  // Port-affecting attribute detection
  // ---------------------------------------------------------------------------

  private static List<Attribute<?>> findPortAffectingAttrs(ComponentListingFeature clf, 
      ComponentFactory factory, AttributeSet defaultAttrs, List<AttrInfo> allAttrs,
      Attribute<Direction> facingAttr) {
    // System.err.println("findPortAffectinAttrs for " + factory.getName());

    // Use east-facing as baseline (to avoid facing from confusing the check)
    AttributeSet baseAttrs = defaultAttrs;
    if (facingAttr != null) {
      baseAttrs = cloneWithValue(defaultAttrs, facingAttr, "east");
    }
    List<PortPosition> basePorts = getPortInfos(factory, baseAttrs);
    if (basePorts == null) return Collections.emptyList();
    
    // System.out.println("== base attrs ==");
    // System.out.println(baseAttrs.dump());
    // System.out.println("== variations ==");

    List<String> extras = clf == null ? null : clf.getLayoutAnalysisIncludedAttributes(defaultAttrs);
    List<Attribute<?>> result = new ArrayList<>();
    for (AttrInfo ai : allAttrs) { // defaultAttrs.getAttributes()
      Attribute<?> attr = baseAttrs.getAttribute(ai.xmlName);
      // System.out.println("try " + attr.getName());
      if (attr == null || attr == facingAttr) continue;
      if (extras != null && extras.contains(attr.getName())) {
        result.add(attr);
        continue;
      }
      List<String> vals = getFiniteValues(attr);
      if (vals == null || vals.size() <= 1) continue;
      for (String val : vals) {
        // System.out.println("try " + attr.getName() + "="+val);
        @SuppressWarnings("rawtypes")
        AttributeSet testAttrs = cloneWithValue(baseAttrs, (Attribute)attr, val);
        if (testAttrs == null) continue;
        List<PortPosition> testPorts = getPortInfos(factory, testAttrs);
        if (testPorts == null) continue;
        if (!portInfosEqual(basePorts, testPorts)) {
          // System.out.println("differ");
          result.add(attr);
          break;
        }
        // System.out.println("same");
      }
    }
    return result;
  }

  private static boolean portInfosEqual(List<PortPosition> a, List<PortPosition> b) {
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      // Q: why do we not also compare names here? Maybe they don't matter?
      if (!a.get(i).equalsExceptName(b.get(i)))
          return false;
    }
    return true;
  }

  // ---------------------------------------------------------------------------
  // Attribute value enumeration
  // ---------------------------------------------------------------------------

  /**
   * Collects all possible attributes for a component via DFS exploration.
   * Handles dynamic attribute lists (e.g. AND gate negate attrs that appear only
   * when input count is large enough). Exploits prefix-stability: changing attr[i]
   * can only affect attrs[i+1..end], never attrs[0..i-1].
   */
  private static List<AttrInfo> collectAllAttrInfos(
      ComponentFactory factory, AttributeSet defaultAttrs,
      Attribute<Direction> facingAttr, Map<String, String> attrNotes) {
    Map<String, AttrInfo> seen = new LinkedHashMap<>();
    Set<String> seenSigs = new HashSet<>();
    exploreAndCollectAttrs(factory, defaultAttrs, 0, facingAttr, attrNotes, seen, seenSigs);
    return new ArrayList<>(seen.values());
  }

  private static void exploreAndCollectAttrs(
      ComponentFactory factory, AttributeSet attrs, int depth,
      Attribute<Direction> facingAttr, Map<String, String> attrNotes,
      Map<String, AttrInfo> seen, Set<String> seenSigs) {
    List<Attribute<?>> attrList = attrs.getAttributes();
    if (depth >= attrList.size()) return;

    // Signature: names of attrs from depth onward. Deduplicates identical suffixes.
    StringBuilder sb = new StringBuilder();
    for (int i = depth; i < attrList.size(); i++) sb.append(attrList.get(i).getName()).append('\0');
    if (!seenSigs.add(sb.toString())) return;

    // Record all attrs from depth onward; first-seen wins for defaultVal.
    for (int i = depth; i < attrList.size(); i++) {
      Attribute<?> attr = attrList.get(i);
      String xmlName = attr.getName();
      if (!seen.containsKey(xmlName)) {
        AttrInfo ai = new AttrInfo();
        ai.xmlName = xmlName;
        ai.description = attr.getDisplayName();
        ai.values = describeValues(attr);
        ai.note = attrNotes.get(xmlName);
        ai.defaultVal = attrToXml(attr, attrs);
        ai.isFacing = (attr == facingAttr);
        seen.put(xmlName, ai);
      }
    }

    // Vary each attr at positions depth..end to discover new attr-list configurations.
    for (int d = depth; d < attrList.size(); d++) {
      Attribute<?> attr = attrList.get(d);
      List<String> vals = getFiniteValues(attr);
      if (vals == null) continue;
      for (String val : vals) {
        AttributeSet newAttrs = cloneWithValue(attrs, attr, val);
        if (newAttrs == null) continue;
        exploreAndCollectAttrs(factory, newAttrs, d + 1, facingAttr, attrNotes, seen, seenSigs);
      }
    }
  }

  /** Returns a List<String> for LIST-domain attrs, or a hint String for others. */
  private static Object describeValues(Attribute<?> attr) {
    Attribute.Domain domain = attr.getDomain();
    if (domain.kind == Attribute.Domain.Kind.LIST) {
      List<String> result = new ArrayList<>();
      for (String s : domain.options) result.add(s);
      return result;
    }
    return domain.hint;
  }

  /**
   * Returns XML-string values for port-affecting attribute variation, or null
   * if this attribute's domain is not enumerable within the variation limit.
   * Only LIST and small INT_RANGE domains are enumerated.
   */
  private static List<String> getFiniteValues(Attribute<?> attr) {
    Attribute.Domain domain = attr.getDomain();
    if (domain.kind == Attribute.Domain.Kind.LIST) {
      List<String> result = new ArrayList<>();
      for (String s : domain.options) result.add(s);
      return result;
    }
    if (domain.kind == Attribute.Domain.Kind.INT_RANGE) {
      if (domain.imax - domain.imin > MAX_ENUM_RANGE) return null;
      List<String> result = new ArrayList<>();
      for (int i = domain.imin; i <= domain.imax; i++) result.add(String.valueOf(i));
      return result;
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Port array compression
  // ---------------------------------------------------------------------------

  /**
   * Compresses a flat port list by replacing consecutive runs of linearly-spaced,
   * sequentially-named ports with one or two array descriptors. A run qualifies when:
   *   - all names match the pattern prefix+digits (same prefix and type)
   *   - indices are consecutive integers
   *   - positions form a strict linear progression (same step between each pair)
   * If the full group fails but has an even count, the two halves are checked
   * independently (handles components where a gap in the middle splits the ports).
   * Non-qualifying ports are returned as-is (isArray=false).
   */
  private static List<PortPosition> compressPorts(List<PortPosition> ports) {
    // Group ports by (prefix, type), preserving insertion order of first occurrence.
    LinkedHashMap<String, List<PortPosition>> groups = new LinkedHashMap<>();
    Set<PortPosition> consumed = Collections.newSetFromMap(new IdentityHashMap<>());

    for (PortPosition pi : ports) {
      int split = pi.name.length();
      while (split > 0 && Character.isDigit(pi.name.charAt(split - 1))) split--;
      if (split == pi.name.length()) continue; // no trailing digits — individual
      String prefix = pi.name.substring(0, split);
      String key = prefix + "\0" + pi.type;
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(pi);
    }

    // For each group, build 1 or 2 array descriptors (or none).
    // triggerMap: maps the first port of each sub-array to the descriptor to emit there.
    Map<PortPosition, PortPosition> triggerMap = new IdentityHashMap<>();
    for (Map.Entry<String, List<PortPosition>> e : groups.entrySet()) {
      List<PortPosition> g = e.getValue();
      if (g.size() < 2) continue;
      String prefix = e.getKey().substring(0, e.getKey().indexOf('\0'));

      // Sort by numeric index
      g.sort((a, b) -> Integer.compare(indexSuffix(a.name), indexSuffix(b.name)));

      PortPosition single = tryBuildArray(g, prefix);
      if (single != null) {
        triggerMap.put(g.get(0), single);
        consumed.addAll(g);
      } else if (g.size() % 2 == 0) {
        // Try splitting at N/2 to handle a gap in the middle
        int half = g.size() / 2;
        PortPosition first = tryBuildArray(g.subList(0, half), prefix);
        PortPosition second = tryBuildArray(g.subList(half, g.size()), prefix);
        if (first != null && second != null) {
          triggerMap.put(g.get(0), first);
          triggerMap.put(g.get(half), second);
          consumed.addAll(g);
        }
      }
    }

    // Rebuild output in original port order.
    // At each trigger port, emit the corresponding array descriptor.
    // Other consumed ports are suppressed.
    List<PortPosition> result = new ArrayList<>();
    for (PortPosition pi : ports) {
      if (!consumed.contains(pi)) {
        result.add(pi);
      } else {
        PortPosition desc = triggerMap.get(pi);
        if (desc != null) result.add(desc);
        // else: non-trigger consumed port — suppressed
      }
    }
    return result;
  }

  /**
   * Checks whether a sorted (by index) list of ports forms a valid linear array.
   * Requires consecutive indices and linearly-spaced positions.
   * Returns a PortPosition descriptor (isArray=true) if valid, null otherwise.
   */
  private static PortPosition tryBuildArray(List<PortPosition> g, String prefix) {
    if (g.size() < 2) return null;
    int firstIdx = indexSuffix(g.get(0).name);
    for (int i = 1; i < g.size(); i++) {
      if (indexSuffix(g.get(i).name) != firstIdx + i) return null;
    }
    int sdx = (Integer)g.get(1).dx - (Integer)g.get(0).dx;
    int sdy = (Integer)g.get(1).dy - (Integer)g.get(0).dy;
    for (int i = 1; i < g.size(); i++) {
      if ((Integer)g.get(i).dx - (Integer)g.get(i-1).dx != sdx || (Integer)g.get(i).dy - (Integer)g.get(i-1).dy != sdy) return null;
    }
    PortPosition arr = new PortPosition(
        prefix,
        g.get(0).type,
        firstIdx,
        g.size(),
        g.get(0).dx,
        g.get(0).dy,
        sdx,
        sdy);
    return arr;
  }

  private static int indexSuffix(String name) {
    int i = name.length();
    while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
    return Integer.parseInt(name.substring(i));
  }

  // ---------------------------------------------------------------------------
  // AttributeSet cloning helpers
  // ---------------------------------------------------------------------------

  /**
   * Clones attrs and sets attr to the value parsed from xmlValue. Returns null
   * if parsing or setting fails (e.g., the value is not valid for this component
   * state), allowing callers to skip that value gracefully.
   */
  @SuppressWarnings({"rawtypes","unchecked"})
  private static AttributeSet cloneWithValue(AttributeSet attrs,
      Attribute attr, String xmlValue) {
    try {
      Object parsed = attr.parse(xmlValue);
      AttributeSet copy = (AttributeSet) attrs.clone();
      ((AbstractAttributeSet) copy).changeAttr((Attribute<Object>) attr, parsed);
      return copy;
    } catch (Exception e) {
      System.err.println("ERR: Can't clone attribute set: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Cartesian product
  // ---------------------------------------------------------------------------

  private static List<List<String>> buildCombos(List<List<String>> valueSets) {
    List<List<String>> result = new ArrayList<>();
    result.add(new ArrayList<>());
    for (List<String> valueSet : valueSets) {
      List<List<String>> next = new ArrayList<>();
      for (List<String> existing : result) {
        for (String val : valueSet) {
          List<String> combo = new ArrayList<>(existing);
          combo.add(val);
          next.add(combo);
        }
      }
      result = next;
    }
    return result;
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  private static <V> String attrToXml(Attribute<V> attr, AttributeSet attrs) {
    return attr.toStandardString(attrs.getValue(attr));
  }

  private static String attrsToString(AttributeSet attrs) {
    StringBuilder sb = new StringBuilder("{");
    for (Attribute<?> a : attrs.getAttributes()) {
      String v = attrToXml(a, attrs);
      if (v != null) sb.append(a.getName()).append("=").append(v).append(",");
    }
    sb.append("}");
    return sb.toString();
  }

  // ---------------------------------------------------------------------------
  // Inner data classes
  // ---------------------------------------------------------------------------

  private static class AttrInfo {
    String xmlName, description;
    Object values;  // List<String> or String
    String note;
    String defaultVal;
    boolean isFacing;
  }

  // ---------------------------------------------------------------------------
  // Simple JSON writer
  // ---------------------------------------------------------------------------

  private static class JsonWriter {
    private final PrintWriter out;
    private int indent = 0;
    private boolean needsComma = false;
    private boolean[] inArray;  // stack: true=array, false=object
    private int depth = 0;
    public int linecount = 0;

    JsonWriter(PrintWriter out) {
      this.out = out;
      this.inArray = new boolean[64];
    }

    private void indent() {
      for (int i = 0; i < indent; i++) out.print("  ");
    }

    private void comma() {
      if (needsComma) { out.println(","); needsComma = false; }
      else out.println();
      linecount++;
    }

    void beginObject() {
      if (depth > 0) { comma(); indent(); }
      out.print("{");
      inArray[depth++] = false;
      indent++;
      needsComma = false;
    }

    void endObject() {
      indent--;
      out.println();
      linecount++;
      indent();
      out.print("}");
      depth--;
      needsComma = true;
    }

    void array(List<Object> vals) {
      out.print("[");
      needsComma = false;
      for (Object v : vals) {
        if (needsComma) out.print(", ");
        else out.print(" ");
        out.print(toJson(v));
        needsComma = true;
      }
      out.print(" ]");
      needsComma = true;
    }

    <V> void array(List<V> vals, Function<V, Object> xform) {
      out.print("[");
      needsComma = false;
      for (V v : vals) {
        if (needsComma) out.print(", ");
        out.print(toJson(xform.apply(v)));
        needsComma = true;
      }
      out.print("]");
      needsComma = true;
    }

    void beginArray() {
      out.print("[");
      inArray[depth++] = true;
      indent++;
      needsComma = false;
    }

    void endArray() {
      indent--;
      if (needsComma) { out.println(); indent(); linecount++; }
      out.print("]");
      depth--;
      needsComma = true;
    }

    void key(String k) {
      comma();
      indent();
      out.print("\"" + escape(k) + "\": ");
      needsComma = false;
    }

    void value(Object v) {
      if (inArray[depth - 1]) { comma(); indent(); }
      out.print(toJson(v));
      needsComma = true;
    }

    void value(boolean v) {
      if (inArray[depth - 1]) { comma(); indent(); }
      out.print(v);
      needsComma = true;
    }

    void value(int v) {
      if (inArray[depth - 1]) { comma(); indent(); }
      out.print(v);
      needsComma = true;
    }

    void keyValue(String k, Object v) {
      key(k); out.print(toJson(v)); needsComma = true;
    }

    void keyValue(String k, boolean v) {
      key(k); out.print(v); needsComma = true;
    }

    void keyValue(String k, int v) {
      key(k); out.print(v); needsComma = true;
    }

    static String toJson(Object v) {
      String s = v.toString();
      if ((v instanceof Integer
            || v instanceof Boolean
            || s.equals("true")
            || s.equals("false")))
        return s;
      try {
        int i = Integer.parseInt(s);
        if (s.equals(""+i))
          return s;
      } catch (NumberFormatException ex) { }
      return "\"" + escape(s) + "\"";
    }

    private static String escape(String s) {
      if (s == null) return "";
      return s.replace("\\", "\\\\")
               .replace("\"", "\\\"")
               .replace("\n", "\\n")
               .replace("\r", "\\r")
               .replace("\t", "\\t");
    }
  }

  private static class CountingOutputStream extends FilterOutputStream {
    private long count = 0;

    public CountingOutputStream(OutputStream out) {
      super(out);
    }

    @Override
    public void write(int b) throws IOException {
      out.write(b);
      count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      count += len;
    }

    public long getCount() { return count; }
  }

}
