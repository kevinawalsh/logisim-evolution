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

package com.bfh.logisim.fpga;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bfh.logisim.download.Toolchain;
import com.bfh.logisim.gui.FPGAReport;
import com.cburch.logisim.data.Bounds;

// Board describes an fpga-based platform (i.e. "demo board") that can be the
// target of the FPGA synthesis. Each Board has a name, chipset, image, and list
// of I/O resources.
public class Board {
  
  public static final int STD_IMG_WIDTH = 740;
  public static final int STD_IMG_HEIGHT = 400;

	public final String name; // user-friendly name, e.g. "Alchitry Cu V2 with Io hat"
	public final String codename; // fallback board name used for various tools, e.g. "alchitry-cu" 
  // public final String id; // short id, no spaces or odd symbols, e.g. "AlchitryCu_v2_Io"
	
	public final Chipset fpga; // vendor, family, part, ...
	public final Image image;
	// public final Image scaledImage; // scaled to fit STD_IMG_WIDTH x STD_IMG_HEIGHT
  // public final double imgScale;
  // public final int imgXOffset, imgYOffset;
  public final String imgFormat; // "png" or "jpg"
  public final byte imgBytes[];

  // NOTE: All of the tool names in this file come from the xml, and they may be:
  // - the canonical toolchainName for some known toolchain,
  // - the shortName for some known toolchain,
  // - an alternate name recognized by some known toolchain,
  // - none of the above, i.e. an unrecognized toolchain

  private String defaultSynthesisTool, defaultProgrammingTool; // e.g. "Apio" + "openFPGALoader"
  private final LinkedHashMap<String, LinkedHashMap<String, String>> toolchainParams
    = new LinkedHashMap<>(); // name -> key -> val
  
  // with this board, some tools only work for synthesis (1), some only for programming (2), some for both (3)
  private final HashMap<String, Integer> toolchainCapabilities = new HashMap<>();

	private final ArrayList<BoardIO> ios = new ArrayList<>();

	public Board(String name, String codename, Chipset fpga, Image image, String imgFormat, byte imgBytes[]) {
    this.name = name;
    this.codename = generateCodename(name, codename);
    this.fpga = fpga;
    this.image = image;
    this.imgFormat = imgFormat;
    this.imgBytes = imgBytes;

    // NOTE: this scaling code is identical to code found in BoardPanel.scaleImage()
    // int iw = image.getWidth(null);
    // int ih = image.getHeight(null);
    // if ((iw == STD_IMG_WIDTH && ih <= STD_IMG_HEIGHT) || (iw <= STD_IMG_WIDTH && ih == STD_IMG_HEIGHT)) {
    //   this.scaledImage = image;
    //   this.imgScale = 1.0;
    // } else {
    //   double sx = STD_IMG_WIDTH * 1.0 / iw;
    //   double sy = STD_IMG_HEIGHT * 1.0 / ih;
    //   this.imgScale = Math.min(sx, sy);
    //   this.scaledImage = image.getScaledInstance(
    //       Math.max(STD_IMG_WIDTH, (int)Math.round(sx * iw)),
    //       Math.max(STD_IMG_HEIGHT, (int)Math.round(sx * ih)),
    //       Image.SCALE_SMOOTH);
    // }
    // this.imgXOffset = (scaledImage.getWidth(null) - STD_IMG_WIDTH)/2;
    // this.imgYOffset = (scaledImage.getHeight(null) - STD_IMG_HEIGHT)/2;
	}

  public static String generateCodename(String name, String codename) {
    if (codename != null && !codename.trim().isEmpty())
      return codename;
    return name
      .replaceAll("^[,\\s]+|[,\\s]+$", "")       // strip leading/trailing spaces/commas
      .replaceAll("[,\\s]+(?=[^\\w,\\s])", "")   // remove spaces/commas before punctuation
      .replaceAll("(?<=[^\\w,\\s])[,\\s]+", "")  // remove spaces/commas after punctuation
      .replaceAll("[,\\s]+", "-");               // replace remaining spaces/commas with dash
  }

  public List<BoardIO> getIoComponents() {
    return Collections.unmodifiableList(ios);
  }

	public void addComponent(BoardIO io) { ios.add(io); }

	public void addComponents(List<BoardIO> io) {
		ios.addAll(io);
	}

  public void setDefaultSynthesisTool(String toolchain) { defaultSynthesisTool = toolchain != null && !toolchain.isEmpty() ? toolchain : null; }
  public String getDefaultSynthesisTool() { return defaultSynthesisTool; }
  public void setDefaultProgrammingTool(String toolchain) { defaultProgrammingTool = toolchain != null && !toolchain.isEmpty() ? toolchain : null; }
  public String getDefaultProgrammingTool() { return defaultProgrammingTool; }

  public void addToolchain(String toolchain, String capabilities) {
    toolchainParams.putIfAbsent(toolchain, new LinkedHashMap<>());
    if (capabilities == null) // if not specified, assume both
      capabilities = "synthesis,programming";
    for (String cap : capabilities.split(",")) {
      cap = cap.trim();
      if (cap.equalsIgnoreCase("synthesis"))
        toolchainCapabilities.merge(toolchain, 1, (a, b) -> a + b);
      else if (cap.equalsIgnoreCase("programming"))
        toolchainCapabilities.merge(toolchain, 2, (a, b) -> a + b);
    }
  }

  // Note: This method requires an exact match for the toolchain name, which must
  // match whatever was listed in the board xml.
  public boolean synthesisEnabled(String toolchain) {
    Integer caps = toolchainCapabilities.get(toolchain);
    return caps != null && (caps & 1) == 1;
  }

  // Note: This method requires an exact match for the toolchain name, which must
  // match whatever was listed in the board xml.
  public boolean programmingEnabled(String toolchain) {
    Integer caps = toolchainCapabilities.get(toolchain);
    return caps != null && (caps & 2) == 2;
  }

  // Note: This method requires an exact match for the toolchain name, which must
  // match whatever was listed in the board xml.
  public void removeToolchain(String toolchain) {
    toolchainParams.remove(toolchain);
    toolchainCapabilities.remove(toolchain);
  }

  // Note: This method requires an exact match for the toolchain name, which must
  // match whatever was listed in the board xml.
  public void setToolchainParam(String toolchain, String key, String val) {
    toolchainParams.computeIfAbsent(toolchain, (k) -> new LinkedHashMap<>()).put(key, val);
  }

  // Note: This method requires an exact match for the toolchain name, which must
  // match whatever was listed in the board xml.
  public void removeToolchainParam(String toolchain, String key) {
    LinkedHashMap<String, String> m = toolchainParams.get(toolchain);
    if (m != null)
      m.remove(key);
  }

  // Note: This method returns whatever names were in the board xml, which may not
  // match the canonical toolchain names.
  public List<String> getListedToolchains() {
    ArrayList<String> ret = new ArrayList<>(toolchainParams.keySet());
    if (defaultSynthesisTool != null && !ret.contains(defaultSynthesisTool))
      ret.add(defaultSynthesisTool);
    if (defaultProgrammingTool != null && !ret.contains(defaultProgrammingTool))
      ret.add(defaultProgrammingTool);
    return ret;
  }

  // Note: This method requires an exact match for the toolchain name, which must
  // match whatever was listed in the board xml.
  public Map<String, String> getToolchainParams(String toolchain) {
    LinkedHashMap<String, String> m = toolchainParams.get(toolchain);
    return (m != null) ? Collections.unmodifiableMap(m) : Collections.emptyMap();
  }
  
  public boolean mentions(Toolchain t) {
    for (String tcName : getListedToolchains())
      if (t.approximateNameMatch(tcName))
        return true;
    return false;
  }

  public boolean recommendsForSynthesis(Toolchain t) {
    if (defaultSynthesisTool != null && t.approximateNameMatch(defaultSynthesisTool))
      return true;
    for (Map.Entry<String, LinkedHashMap<String, String>> e : toolchainParams.entrySet()) {
      String tcName = e.getKey();
      if (t.approximateNameMatch(tcName)) {
        if (synthesisEnabled(tcName))
          return true;
      }
    }
    return false;
  }

  public boolean recommendsForProgramming(Toolchain t) {
    if (defaultProgrammingTool != null && t.approximateNameMatch(defaultProgrammingTool))
      return true;
    for (Map.Entry<String, LinkedHashMap<String, String>> e : toolchainParams.entrySet()) {
      String tcName = e.getKey();
      if (t.approximateNameMatch(tcName)) {
        if (programmingEnabled(tcName))
          return true;
      }
    }
    return false;
  }

  public String paramFor(Toolchain t, String key) {
    for (Map.Entry<String, LinkedHashMap<String, String>> e : toolchainParams.entrySet()) {
      String tcName = e.getKey();
      if (t.approximateNameMatch(tcName)) {
        LinkedHashMap<String, String> m = e.getValue();
        if (m.containsKey(key))
          return m.get(key);
      }
    }
    return null;
  }

  public void printStats(FPGAReport out) {
		out.AddInfo("Board '%s' contains the following I/O resources:", name);
		for (BoardIO.Type type : BoardIO.PhysicalTypes) {
      int count = 0, bits = 0;
			for (BoardIO io : ios) {
				if (io.type == type) {
					count++;
					bits += io.width;
        }
      }
      out.AddInfo("   %s: %d components (%d bits)", type, count, bits);
		}
	}

	public ArrayList<Bounds> compatableRects(BoardIO.Type type, int bits) {
		ArrayList<Bounds> result = new ArrayList<>();
		for (BoardIO io : ios)
      if (io.type.equals(type) && bits <= io.width)
        result.add(io.rect);
		return result;
	}

  public BoardIO getComponent(Bounds r) {
		for (BoardIO io : ios)
      if (io.rect.equals(r))
        return io;
    return null;
  }
  
  public static void drawFitted(Graphics g, Image img, int w, int h) {
    g.setColor(Color.BLACK);
    g.fillRect(0, 0, w, h);

    if (img == null)
      return;

    int imgW = img.getWidth(null);
    int imgH = img.getHeight(null);

    double scale = Math.min((double) w / imgW, (double) h / imgH);
    int drawW = (int) Math.round(imgW * scale);
    int drawH = (int) Math.round(imgH * scale);
    int drawX = (w - drawW) / 2;
    int drawY = (h - drawH) / 2;

    g.drawImage(img, drawX, drawY, drawW, drawH, null);
  }

  public static double scaleForFitted(Image img, int w, int h) {
    if (img == null)
      return 1.0;
    int imgW = img.getWidth(null);
    int imgH = img.getHeight(null);
    double scale = Math.min((double) w / imgW, (double) h / imgH);
    return scale;
  }

  public static Point offsetForFitted(Image img, int w, int h) {
    if (img == null)
      return new Point(0, 0);
    int imgW = img.getWidth(null);
    int imgH = img.getHeight(null);
    double scale = Math.min((double) w / imgW, (double) h / imgH);
    int drawW = (int) Math.round(imgW * scale);
    int drawH = (int) Math.round(imgH * scale);
    int drawX = (w - drawW) / 2;
    int drawY = (h - drawH) / 2;
    return new Point(drawX, drawY);
  }

}
