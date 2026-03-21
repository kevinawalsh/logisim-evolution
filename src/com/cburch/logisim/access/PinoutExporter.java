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
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.access;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.start.Startup;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * Implements the --pinout headless CLI option. Prints the pinout (footprint) of
 * a single component to stdout: library/component name, bounding box, and each
 * port's name, direction, bit-width, and coordinate.
 */
public class PinoutExporter {

  /** A matched (library, factory) pair found during search. */
  private static class FoundComponent {
    final Library lib;
    final ComponentFactory factory;
    FoundComponent(Library lib, ComponentFactory factory) {
      this.lib = lib;
      this.factory = factory;
    }
  }

  public static void exportTo(String spec, Startup startup) {
    // JSON format detection
    if (spec.trim().startsWith("{")) {
      System.err.println("Error: JSON format not yet implemented for --pinout.");
      System.exit(1);
    }

    // --- Parse plain format: [libName][compName]@(x,y) key1=val1 ... ---
    String libName = null;
    String compName = null;
    Location loc = Location.ORIGIN;
    List<String[]> attrPairs = new ArrayList<>();

    int pos = 0;
    String s = spec.trim();

    if (s.isEmpty() || s.charAt(0) != '[') {
      System.err.println("Error: component spec must start with '['. "
          + "Expected format: [LibName][CompName]@(x,y) key=val ...");
      System.exit(1);
    }

    // First bracket token
    int close1 = s.indexOf(']', 1);
    if (close1 < 0) {
      System.err.println("Error: missing ']' in component spec.");
      System.exit(1);
    }
    String token1 = s.substring(1, close1);
    pos = close1 + 1;

    // Optional second bracket token
    if (pos < s.length() && s.charAt(pos) == '[') {
      int close2 = s.indexOf(']', pos + 1);
      if (close2 < 0) {
        System.err.println("Error: missing ']' in component spec.");
        System.exit(1);
      }
      libName = token1;
      compName = s.substring(pos + 1, close2);
      pos = close2 + 1;
    } else {
      compName = token1;
    }

    // Skip whitespace
    while (pos < s.length() && s.charAt(pos) == ' ') pos++;

    // Optional @(x, y)
    if (pos < s.length() && s.charAt(pos) == '@') {
      pos++; // skip '@'
      if (pos >= s.length() || s.charAt(pos) != '(') {
        System.err.println("Error: expected '(' after '@' in location spec.");
        System.exit(1);
      }
      int closeP = s.indexOf(')', pos);
      if (closeP < 0) {
        System.err.println("Error: missing ')' in location spec.");
        System.exit(1);
      }
      String coords = s.substring(pos + 1, closeP);
      String[] parts = coords.split(",", 2);
      if (parts.length != 2) {
        System.err.println("Error: expected two coordinates in '@(x, y)'.");
        System.exit(1);
      }
      try {
        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());
        loc = Location.create(x, y);
      } catch (NumberFormatException e) {
        System.err.println("Error: non-integer coordinates in '@(x, y)': " + e.getMessage());
        System.exit(1);
      }
      pos = closeP + 1;
    }

    // Remaining: space-separated key=val pairs
    String rest = s.substring(pos).trim();
    if (!rest.isEmpty()) {
      for (String token : rest.split("\\s+")) {
        int eq = token.indexOf('=');
        if (eq <= 0) {
          System.err.println("Error: expected 'key=value' but got: " + token);
          System.exit(1);
        }
        attrPairs.add(new String[]{ token.substring(0, eq), token.substring(eq + 1) });
      }
    }

    // --- Build library list to search ---
    List<Library> roots = new ArrayList<>();
    if (!startup.getFilesToOpen().isEmpty()) {
      LogisimFile.FileWithSimulations fw = startup.headlessOpen(startup.getFilesToOpen().get(0));
      roots.add(fw.file);
    } else {
      roots.addAll(new Builtin().getLibraries());
    }

    // --- Search for component ---
    List<FoundComponent> found = new ArrayList<>();
    for (Library root : roots)
      searchLibrary(root, libName, compName, found);

    if (found.isEmpty()) {
      if (libName != null)
        System.err.printf("Error: component '[%s][%s]' not found.%n", libName, compName);
      else
        System.err.printf("Error: component '[%s]' not found.%n", compName);
      System.exit(1);
    }
    if (found.size() > 1) {
      System.err.printf("Error: ambiguous component name '[%s]' — found in multiple libraries:%n",
          compName);
      for (FoundComponent fc : found)
        System.err.printf("  [%s][%s]%n", fc.lib.getDisplayName(), fc.factory.getName());
      System.err.println("Specify the library name to disambiguate.");
      System.exit(1);
    }

    FoundComponent fc = found.get(0);
    ComponentFactory factory = fc.factory;

    // --- Apply attribute key=val pairs ---
    AttributeSet attrs = factory.createAttributeSet();
    for (String[] pair : attrPairs) {
      String key = pair[0];
      String val = pair[1];
      Attribute<?> found_attr = null;
      for (Attribute<?> a : attrs.getAttributes()) {
        if (a.getName().equals(key)) {
          found_attr = a;
          break;
        }
      }
      if (found_attr == null) {
        System.err.printf("Warning: attribute '%s' not found on '%s', ignoring.%n",
            key, factory.getName());
        continue;
      }
      try {
        @SuppressWarnings("unchecked")
        Attribute<Object> attr = (Attribute<Object>) found_attr;
        Object parsed = attr.parse(val);
        ((AbstractAttributeSet) attrs).changeAttr(attr, parsed);
      } catch (Exception e) {
        System.err.printf("Warning: could not set attribute '%s'='%s': %s%n",
            key, val, e.getMessage());
      }
    }

    // --- Create component and collect data ---
    Component comp;
    try {
      comp = factory.createComponent(loc, attrs);
    } catch (Exception e) {
      System.err.println("Error: could not create component: " + e.getMessage());
      System.exit(1);
      return;
    }

    Bounds bds = comp.getNominalBounds();
    List<EndData> ends = comp.getEnds();

    // --- Print output ---
    System.out.printf("Library: %s%n", fc.lib.getDisplayName());
    System.out.printf("Component: %s%n", factory.getName());
    System.out.printf("Bounding box: x=%d, y=%d, width=%d, height=%d%n",
        bds.x, bds.y, bds.width, bds.height);
    System.out.println("Ports:");
    for (int i = 0; i < ends.size(); i++) {
      EndData end = ends.get(i);
      String portName = ComponentListingExporter.getNameFromToolTipOrDefault(comp, i);
      String dir = typeString(end.getType());
      int bits = end.getWidth().getWidth();
      int px = end.getLocation().getX();
      int py = end.getLocation().getY();
      System.out.printf("  %-12s  %-7s  %d-bit  (%d, %d)%n", portName, dir, bits, px, py);
    }
  }

  /** Recursively search lib (and its sub-libraries) for matching component factories. */
  private static void searchLibrary(Library lib, String libName, String compName,
      List<FoundComponent> results) {
    for (Tool tool : lib.getTools()) {
      if (!(tool instanceof AddTool)) continue;
      ComponentFactory factory = ((AddTool) tool).getFactory();
      if (!compName.equals(factory.getName())) continue;
      if (libName == null
          || libName.equals(lib.getName())
          || libName.equals(lib.getDisplayName())) {
        results.add(new FoundComponent(lib, factory));
      }
    }
    for (Library sub : lib.getLibraries())
      searchLibrary(sub, libName, compName, results);
  }

  private static String typeString(int type) {
    if (type == EndData.OUTPUT_ONLY) return "output";
    if (type == EndData.INPUT_ONLY)  return "input";
    return "inout";
  }
}
