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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.cburch.logisim.prefs.AppPreferences;


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
	private static final ArrayList<String> builtinBoardNames = new ArrayList<>();
	private static final ArrayList<String> builtinBoardPaths = new ArrayList<>();
	private static final ArrayList<String> externalBoardNames = new ArrayList<>();
	private static final ArrayList<String> externalBoardPaths = new ArrayList<>();
  private static final TreeMap<String name, String path> boards = new TreeMap<>();
	static {
    refresh();
	}

  private static void refresh() {
    builtinBoardNames.clear();
    builtinBoardPaths.clear();
    externalBoardNames.clear();
    externalBoardPaths.clear();
    boards.clear();

    loadBuiltinBoards();
    loadExternalBoards();

    for (int i = 0; i < externalBoardNames.size(); i++)
      boards.putIfAbsent(externalBoardNames.get(i), externalBoardPaths.get(i));
    for (int i = 0; i < builtinBoardNames.size(); i++)
      boards.putIfAbsent(builtinBoardNames.get(i), builtinBoardPaths.get(i));
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
          builtinBoardNames.add(name);
          builtinBoardPaths.add(path);
				} catch (IOException e) {
					Debug.error("scanning fpga board definitions: " + path, e);
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
		}
		Enumeration<? extends ZipEntry> entries = zf.entries();
		while (entries.hasMoreElements()) {
			ZipEntry ze = entry.nextElement();
      if (ze.isDirectory()) continue;
			String path = ze.getName();
      if (!path.startsWith(RESOURCE_PATH+"/") || !name.toLowerCase().endsWith(".xml"))
        continue;
      // FIXME: examine xml to ensure it is a board, and get a proper name
      path = "jar|" + jar + "|" + path;
      String name = nameForPath(path);
      builtinBoardNames.add(name);
      builtinBoardPaths.add(path);
		}
		try { zf.close(); }
    catch (IOException e) { }
	}

  // FIXME: use better names
	private static String nameForPath(String path) {
		String[] parts;
		if (path.startsWith("jar:"))
			parts = path.split("/");
		else
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
        loadBoardsFromDirectory(file, false /* non recursive */);
        loadBoardsFromDirectory(new File(file, RESOURCE_PATH, true /* recursive */));
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
      externalBoardNames.add(name);
      externalBoardPaths.add(path);
    }
  }

	public static boolean hasBoardNamed(String name) {
    return GetBoardFilePath(BoardName) != null;
	}

	public static String getPathForBoardNamed(String name) {
		for (String board : AppPreferences.FPGA_BOARDLIST.get())
			if (nameForPath(board).equals(BoardName))
				return board;
		for (String board : builtinBoards)
			if (nameForPath(board).equals(BoardName))
				return board;
		return null;
	}

  // names are unique
	public static List<String> getAllNames() {
		ArrayList<String> ret = new ArrayList<>();
    for (String path : AppPreferences.FPGA_BOARDLIST.get()) {
      path = "file|" + path;
      String name = nameForPath(path);
      if (!ret.contains(name))
        ret.add(name);
    }
    for (String name : builtinBoardNames)
      if (!ret.contains(name))
        ret.add(name);
    return ret;
	}

  public static String getSelectedName() {
    String b = AppPreferences.FPGA_SELECTED_BOARD.get();
    // FPGA_SELECTED_BOARD can be a name
    // , a filename (without .xml), a file path,
    // or a tagged path.
    if (getAllNames().contains(b))
      return b;
    // FPGA_SELECTED_BOARD can be a filename (without the xml path)
    //
    // , a filename (without .xml), a file path,
    // or a tagged path.

    if (kb.GetBoardNames().contains(b))
      return b;
    else if (!kb.GetBoardNames().isEmpty())
      return kb.GetBoardNames().get(0);
    else
      return "";
  }

  public static String getSelectedPath() {
    System.out.println("board = " + known().getSelectedBoard());
    System.out.println(" path = " + known().GetBoardFilePath(known().getSelectedBoard()));
    return known().GetBoardFilePath(known().getSelectedBoard());
  }


}
