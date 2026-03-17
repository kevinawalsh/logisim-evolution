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
import java.util.List;
import java.util.Map;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentListingFeature;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.std.Builtin;

/**
 * Exports a JSON file describing every component in Logisim's standard library:
 * port positions for all relevant attribute combinations, attribute value ranges,
 * and optional notes. Invoked via the -dump-components CLI flag.
 *
 * Coordinate system: port positions are (dx, dy) offsets from the component's
 * anchor point (the "loc" attribute in .circ XML). +dx is East (right), +dy is
 * South (down). Units are Logisim grid units (10 = 1 grid square).
 *
 * Rotation: for components with rotation="standard", variants list
 * facing=east port positions only. Derive other facings by rotating (dx,dy)
 * around the anchor (0,0):
 *   west:  (-dx, -dy)   [180 degrees]
 *   north: (dy,  -dx)   [CCW 90 degrees in screen coordinates]
 *   south: (-dy,  dx)   [CW  90 degrees in screen coordinates]
 * Rotation: for components with rotation="mirrored", variants list
 * facing=east port positions only. Derive north facing by rotating 90 degrees
 * CCW about anchor (0,0), and derive other facings by facings by mirroring (dx,dy)
 * over the x or y axis, or 
 *   west:  (-dx,  dy)   [Mirror east facing layout over y axis]
 *   north: (dy,  -dx)   [CCW 90 degrees in screen coordinates]
 *   south: (-dy, -dx)   [Mirror north facing layout over x axis]
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
      // if (outputFile == null || outputFile.equals("-")) {
      //   out = new PrintWriter(System.out);
      // } else {
      //   out = new PrintWriter(new FileWriter(outputFile));
      // }
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
    w.key("mirrored");
    w.beginObject();
    w.keyValue("description",
        "For components with rotation='mirrored', port_layouts list facing=east layout of " +
        "port positions only. Derive layouts for north facing by rotating (dx,dy) " +
        "around (0,0) by 90 CCW in screen coordinates. Derive layouts for west and " +
        "south facings by mirroring over the x or y axis:");
    w.keyValue("west",  "(-dx, dy)   [Mirror east facing layout over x axis]");
    w.keyValue("north", "(dy, -dx)   [CCW 90 degrees in screen coordinates]");
    w.keyValue("south", "(-dy, -dx)  [Mirror north facing layout over x axis]");
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
    w.key("usage");
    w.value("To find component port layout: look up the component by library and name, " +
        "find the variant whose attribute values match those in the .circ file " +
        "(defaulting to default_attrs for any omitted attributes), then read " +
        "port dx/dy offsets. Adjust offsets if standard_rotation=true and not facing East. " +
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
        if (!done.add(factory)) continue;
        // if (!factory.getName().equals("AND Gate")) continue;
        try {
          int a = w.linecount;
          processComponent(factory, w);
          int b = w.linecount;
          System.out.printf("Wrote %d lines of json for '%s'\n", b-a, factory.getName());
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
    List<Attribute<?>> attrList = defaultAttrs.getAttributes();

    // Get ComponentListingFeature notes (if any), and layout variant exclusion list
    Map<String, String> attrNotes = Collections.emptyMap();
    List<String> variantAttrExclusions = Collections.emptyList();
    Object o = factory.getFeature(ComponentListingFeature.class, defaultAttrs);
    if (o instanceof ComponentListingFeature) {
      ComponentListingFeature clf = (ComponentListingFeature) o;
      Map<String, String> notes = clf.getAttributeNotes(defaultAttrs);
      if (notes != null) attrNotes = notes;
      List<String> excluded = clf.getLayoutAnalysisExcludedAttributes(defaultAttrs);
      if (excluded != null) variantAttrExclusions = excluded;
    }

    // Detect FACING attribute
    Object facingAttrObj = factory.getFeature(ComponentFactory.FACING_ATTRIBUTE_KEY, defaultAttrs);
    Attribute<Direction> facingAttr = (facingAttrObj instanceof Attribute)
        ? (Attribute<Direction>) facingAttrObj : null;

    // Get default attribute values description
    List<AttrInfo> attrs = new ArrayList<>();
    for (Attribute<?> attr : attrList) {
      AttrInfo ai = new AttrInfo();
      ai.xmlName = attr.getName();
      ai.description = attr.getDisplayName();
      ai.values = describeValues(attr);
      ai.note = attrNotes.get(attr.getName());
      ai.defaultVal = attrToXml(attr, defaultAttrs);
      ai.isFacing = (attr == facingAttr);
      attrs.add(ai);
    }

    // Find port-affecting non-FACING attributes
    List<Attribute<?>> portAffecting = findPortAffectingAttrs(factory, defaultAttrs, facingAttr);

    // Get default port positions (facing=east if applicable)
    AttributeSet eastAttrs = defaultAttrs;
    if (facingAttr != null) {
      eastAttrs = cloneWithValue(defaultAttrs, facingAttr, "east");
    }

    // Check rotation
    String rotation = classifyRotation(factory, eastAttrs, facingAttr);

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
    w.beginArray();
    for (Attribute<?> pa : variantAttrList) w.value(pa.getName());
    w.endArray();

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
        w.key("values");
        w.beginArray();
        for (String v : (List<String>) ai.values) w.value(v);
        w.endArray();
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
        if (comboFailed) continue;

        List<PortInfo> ports = getPortInfos(factory, varAttrs);
        if (ports == null) continue;

        w.beginObject();
        if (facing != null) w.keyValue("facing", facing.toString());
        for (int i = 0; i < variantAttrList.size(); i++) {
          w.keyValue(variantAttrList.get(i).getName(), combo.get(i));
        }
        w.key("ports");
        w.beginArray();
        for (PortInfo pi : ports) {
          w.beginObject();
          w.keyValue("name", pi.name);
          w.keyValue("type", pi.type);
          w.keyValue("dx", pi.dx);
          w.keyValue("dy", pi.dy);
          w.endObject();
        }
        w.endArray(); // ports
        w.endObject(); // variant
      }
    }
    w.endArray(); // variants

    w.endObject(); // component
  }

  // ---------------------------------------------------------------------------
  // Port position helpers
  // ---------------------------------------------------------------------------

  private static List<PortInfo> getPortInfos(ComponentFactory factory, AttributeSet attrs) {
    try {
      Component comp = factory.createComponent(Location.create(0, 0), attrs);
      List<EndData> ends = comp.getEnds();
      List<Port> ports = null;
      if (comp instanceof InstanceComponent) {
        Instance inst = ((InstanceComponent) comp).getInstance();
        ports = inst.getPorts();
      }
      List<PortInfo> result = new ArrayList<>();
      for (int i = 0; i < ends.size(); i++) {
        EndData end = ends.get(i);
        PortInfo pi = new PortInfo();
        pi.dx = end.getLocation().getX();
        pi.dy = end.getLocation().getY();
        pi.type = typeString(end.getType());
        pi.name = (ports != null && i < ports.size())
            ? tooltipOrDefault(ports.get(i), pi.type, i)
            : defaultPortName(pi.type, i);
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

  private static String tooltipOrDefault(Port port, String type, int index) {
    String tip = port.getToolTip();
    if (tip != null && !tip.isEmpty()
        && !tip.equals("Input") && !tip.equals("Output") && !tip.equals("Bidir")
        && !tip.equals("input") && !tip.equals("output") && !tip.equals("bidir")) {
      return tip;
    }
    return defaultPortName(type, index);
  }

  private static String defaultPortName(String type, int index) {
    if ("output".equals(type)) return index == 0 ? "out" : "out" + index;
    if ("input".equals(type))  return "in" + index;
    return "port" + index;
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
      List<Port> ports = null;
      if (comp instanceof InstanceComponent) {
        ports = ((InstanceComponent) comp).getInstance().getPorts();
      }
      for (int i = 0; i < ends.size(); i++) {
        EndData end = ends.get(i);
        if (end.getLocation().getX() == 0 && end.getLocation().getY() == 0) {
          String t = typeString(end.getType());
          String name = (ports != null && i < ports.size())
              ? tooltipOrDefault(ports.get(i), t, i)
              : defaultPortName(t, i);
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
    List<PortInfo> eastPorts = getPortInfos(factory, eastAttrs);
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
      List<PortInfo> dirPorts = getPortInfos(factory, dirAttrs);
      if (dirPorts == null) return ROTATION_CUSTOM;
      if (dirPorts.size() != eastPorts.size()) return ROTATION_CUSTOM;

      for (int i = 0; i < eastPorts.size() && maybeStandard; i++) {
        PortInfo ep = eastPorts.get(i);
        PortInfo dp = dirPorts.get(i);
        int expectedDx, expectedDy;
        if (dir == Direction.WEST) {
          expectedDx = -ep.dx;
          expectedDy = -ep.dy;
        } else if (dir == Direction.NORTH) {
          expectedDx =  ep.dy;
          expectedDy = -ep.dx;
        } else { // SOUTH
          expectedDx = -ep.dy;
          expectedDy =  ep.dx;
        }
        // System.out.printf("%d vs %d, %d vs %d\n", dp.dx, expectedDx, dp.dy, expectedDy);
        maybeStandard &= (dp.dx == expectedDx && dp.dy == expectedDy);
      }
      for (int i = 0; i < eastPorts.size() && maybeMirrorLeft; i++) {
        PortInfo ep = eastPorts.get(i);
        PortInfo dp = dirPorts.get(i);
        int expectedDx, expectedDy;
        if (dir == Direction.WEST) {
          expectedDx = -ep.dx;
          expectedDy = -ep.dy * -1;
        } else if (dir == Direction.NORTH) {
          expectedDx =  ep.dy;
          expectedDy = -ep.dx;
        } else { // SOUTH
          expectedDx = -ep.dy * -1;
          expectedDy =  ep.dx;
        }
        // System.out.printf("%d vs %d, %d vs %d\n", dp.dx, expectedDx, dp.dy, expectedDy);
        maybeMirrorLeft &= (dp.dx == expectedDx && dp.dy == expectedDy);
      }
      for (int i = 0; i < eastPorts.size() && maybeMirrorRight; i++) {
        PortInfo ep = eastPorts.get(i);
        PortInfo dp = dirPorts.get(i);
        int expectedDx, expectedDy;
        if (dir == Direction.WEST) {
          expectedDx = -ep.dx;
          expectedDy = -ep.dy * -1;
        } else if (dir == Direction.NORTH) {
          expectedDx =  ep.dy * -1;
          expectedDy = -ep.dx;
        } else { // SOUTH
          expectedDx = -ep.dy;
          expectedDy =  ep.dx;
        }
        // System.out.printf("%d vs %d, %d vs %d\n", dp.dx, expectedDx, dp.dy, expectedDy);
        maybeMirrorRight &= (dp.dx == expectedDx && dp.dy == expectedDy);
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

  private static List<Attribute<?>> findPortAffectingAttrs(
      ComponentFactory factory, AttributeSet defaultAttrs,
      Attribute<Direction> facingAttr) {

    // Use east-facing as baseline (to avoid facing from confusing the check)
    AttributeSet baseAttrs = defaultAttrs;
    if (facingAttr != null) {
      baseAttrs = cloneWithValue(defaultAttrs, facingAttr, "east");
    }
    List<PortInfo> basePorts = getPortInfos(factory, baseAttrs);
    if (basePorts == null) return Collections.emptyList();

    List<Attribute<?>> result = new ArrayList<>();
    for (Attribute<?> attr : defaultAttrs.getAttributes()) {
      if (attr == facingAttr) continue;
      List<String> vals = getFiniteValues(attr);
      if (vals == null || vals.size() <= 1) continue;
      for (String val : vals) {
        @SuppressWarnings("rawtypes")
        AttributeSet testAttrs = cloneWithValue(baseAttrs, (Attribute)attr, val);
        if (testAttrs == null) continue;
        List<PortInfo> testPorts = getPortInfos(factory, testAttrs);
        if (testPorts == null) continue;
        if (!portInfosEqual(basePorts, testPorts)) {
          result.add(attr);
          break;
        }
      }
    }
    return result;
  }

  private static boolean portInfosEqual(List<PortInfo> a, List<PortInfo> b) {
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (a.get(i).dx != b.get(i).dx) return false;
      if (a.get(i).dy != b.get(i).dy) return false;
      if (!a.get(i).type.equals(b.get(i).type)) return false;
    }
    return true;
  }

  // ---------------------------------------------------------------------------
  // Attribute value enumeration
  // ---------------------------------------------------------------------------

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

  private static class PortInfo {
    int dx, dy;
    String type, name;
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

    void value(String v) {
      if (inArray[depth - 1]) { comma(); indent(); }
      out.print("\"" + escape(v) + "\"");
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

    void keyValue(String k, String v) {
      key(k); out.print("\"" + escape(v) + "\""); needsComma = true;
    }

    void keyValue(String k, boolean v) {
      key(k); out.print(v); needsComma = true;
    }

    void keyValue(String k, int v) {
      key(k); out.print(v); needsComma = true;
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
