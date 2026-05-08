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

package com.bfh.logisim.settings;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Debug;

// Tagged path: file|/Users/kwalsh//AlchitryCu_v2.xml (external board, MacOS, linux)
//              file|C:\\Users\\kwalsh\\AlchitryCu_v2.xml (external board, Windows)
//              jar|/home/logisim.jar|resources/logisim/boards/TERASIC_DE0.xml (builtin board)
// Filename: TERASIC_DE0, AlchitryCu_v2
//          (this is the last part of the path, may not be unique)
// Name: "Terasic DE0", "Alchitry Cu V2",
//          "Alchitry Cu V2 (external)", ...
//          (from xml, modified for uniqueness)
//
// FIXME: for now, we are still using name = filename, but need to fix this

public class BoardList {

  private static final String RESOURCE_PATH = "resources/logisim/boards";
  private static final TreeMap<String, String> boards = new TreeMap<>(); // name -> path
	static {
    refresh();
	}

  public static void refresh() {
    boards.clear();
    loadExternalBoards(); // these get priority
    loadBuiltinBoards();
  }

	private static void loadBoardsFromDirectory(File dir, boolean recursive) {
		File[] files = dir.listFiles();
    if (files == null)
      return;
		for (File file : files) {
			if (file.isDirectory()) {
        if (recursive)
          loadBoardsFromDirectory(file, true);
			} else {
				try {
					String path = file.getCanonicalPath();
          if (!path.toLowerCase().endsWith(".xml"))
            continue;
          // FIXME: examine xml to ensure it is a board, and get a proper name
          path = "file|" + path;
          String name = nameForPath(path);
          boards.putIfAbsent(name, path);
				} catch (IOException e) {
					Debug.error("scanning fpga board definitions: " + file, e);
				}
			}
		}
	}

	private static void loadBoardsfromJar(File jar) {
		ZipFile zf;
		try {
			zf = new ZipFile(jar);
    } catch (Exception e) {
      Debug.error("scanning fpga board definitions: " + jar, e);
      return;
		}
		Enumeration<? extends ZipEntry> entries = zf.entries();
		while (entries.hasMoreElements()) {
			ZipEntry ze = entries.nextElement();
      if (ze.isDirectory()) continue;
			String path = ze.getName();
      if (!path.startsWith(RESOURCE_PATH+"/") || !path.toLowerCase().endsWith(".xml"))
        continue;
      // FIXME: examine xml to ensure it is a board, and get a proper name
      path = "jar|" + jar + "|" + path;
      String name = nameForPath(path);
      boards.putIfAbsent(name, path);
		}
		try { zf.close(); }
    catch (IOException e) { }
	}

  // FIXME: use better names
	public static String nameForPath(String path) {
		return filenameForPath(path);
	}

	public static String filenameForPath(String path) {
		String[] parts;
		if (path.startsWith("jar|")) // "jar|somejar.jar|/resources/logisim/boards/some/board.xml"
			parts = path.split("/");
		else // "file|/some/path/board.xml"  or "/some/path/board.xml"
			parts = path.split(Pattern.quote(File.separator));
    String name = parts[parts.length - 1];
    if (name.toLowerCase().endsWith(".xml"))
      name = name.substring(0, name.length() - 4);
    return name;
	}

  private static void loadBuiltinBoards() {
    String classPath = System.getProperty("java.class.path", "");
    for (String elt : classPath.split(File.pathSeparator)) {
      if (elt.isEmpty()) continue; // empty classpath, trailing separator, etc.
      if (elt.endsWith("*") || elt.endsWith(File.separator + "*"))  {
        // TODO: enumerate directory for jar files. or maybe also zip and xml?
        continue;
      }
      File file = new File(elt);
      if (file.isDirectory()) {
        // loadBoardsFromDirectory(file, false /* non recursive */);
        loadBoardsFromDirectory(new File(file, RESOURCE_PATH), true /* recursive */);
      } else {
        loadBoardsfromJar(file);
      }
    }
  }

  private static void loadExternalBoards() {
		for (String path : AppPreferences.FPGA_BOARDLIST.get()) {
      path = "file|" + path;
      // FIXME: examine xml to ensure it is a board, and get a proper name
      String name = nameForPath(path);
      boards.putIfAbsent(name, path);
    }
  }

	public static boolean hasBoardNamed(String name) {
    return boards.containsKey(name);
	}

  // Resolve a command-line argument to a tagged board path, using the same
  // flexible matching as getSelectedName(): board name, tagged path, filesystem
  // path (with or without .xml), or bare filename stem. Returns null if arg is
  // empty or cannot be resolved.
  public static String getPathForArg(String arg) {
    if (arg == null || arg.isEmpty())
      return null;
    // Exact board name match
    if (boards.containsKey(arg))
      return boards.get(arg);
    // Already a tagged path ("jar|..." or "file|...")
    if (arg.startsWith("jar|") || arg.startsWith("file|")) {
      for (String path : boards.values())
        if (path.equals(arg))
          return path;
      return arg; // use directly even if not in the map
    }
    // Filesystem path ending in .xml
    if (arg.toLowerCase().endsWith(".xml")) {
      String tagged = "file|" + arg;
      for (String path : boards.values())
        if (path.equals(tagged))
          return path;
      if (new File(arg).exists())
        return tagged;
    }
    // Plain filesystem path without .xml
    if (new File(arg).exists())
      return "file|" + arg;
    // Bare filename stem (without .xml extension)
    for (Map.Entry<String, String> e : boards.entrySet())
      if (arg.equals(filenameForPath(e.getValue())))
        return e.getValue();
    return null; // not found
  }

	public static String getPathForBoardNamed(String name) {
    System.out.println("path for: '" + name + "' = '"+boards.get(name)+"'");
    return boards.get(name);
	}

	public static List<String> getAllNames() {
    return List.copyOf(boards.navigableKeySet());
  }

  private static String getFirstName()  {
    if (boards.isEmpty()) System.out.println("fallback, but no boards");
    else System.out.println("fallback to '" + boards.firstKey() +"'");
    return boards.isEmpty() ? null : boards.firstKey();
  }

  public static String getSelectedName() {
    String b = AppPreferences.FPGA_SELECTED_BOARD.get();
    // FPGA_SELECTED_BOARD can be empty...
    if (b == null || b.isEmpty())
      return getFirstName(); // fallback
    // ... or a proper board name
    if (boards.containsKey(b))
      return b;
    // ... or a tagged file path like "jar|..." or "file|..."
    if (b.startsWith("jar|") || b.startsWith("file|")) {
      for (Map.Entry<String, String> e : boards.entrySet()) {
        if (e.getValue().equals(b))
          return e.getKey();
      }
      return getFirstName(); // fallback
    }
    // ... or a plain file path
    if (b.toLowerCase().endsWith(".xml")) {
      for (Map.Entry<String, String> e : boards.entrySet()) {
        if (e.getValue().equals("file|"+b))
          return e.getKey();
      }
      return getFirstName(); // fallback
    }
    // ... or a bare file name (without the ".xml" extension)
    for (Map.Entry<String, String> e : boards.entrySet()) {
      if (b.equals(filenameForPath(e.getValue())))
        return e.getKey();
    }
    // no valid user board preference; just use the first board
    return getFirstName();
  }

  public static String getSelectedPath() {
    String name = getSelectedName();
    return name == null ? null : boards.get(name);
  }

}
