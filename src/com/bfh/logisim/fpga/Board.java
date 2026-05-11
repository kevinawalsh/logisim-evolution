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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

  private String defaultToolchain; // e.g. "Apio"
  private final LinkedHashMap<String, LinkedHashMap<String, String>> toolchainParams
    = new LinkedHashMap<>(); // toolchain -> key -> val
  
  // some toolchains only work for programming, not synthesis
  private final HashSet<String> programmers = new HashSet<>();

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

  public void setDefaultToolchain(String toolchain) { defaultToolchain = toolchain != null && !toolchain.isEmpty() ? toolchain : null; }
  public String getDefaultToolchain() { return defaultToolchain; }

  public void addToolchain(String toolchain) {
    toolchainParams.putIfAbsent(toolchain, new LinkedHashMap<>());
  }

  public void addToolchainProgrammer(String toolchain) {
    toolchainParams.putIfAbsent(toolchain, new LinkedHashMap<>());
    programmers.add(toolchain);
  }

  public boolean isOnlyProgrammer(String toolchain) {
    return programmers.contains(toolchain);
  }

  public void removeToolchain(String toolchain) {
    toolchainParams.remove(toolchain);
  }

  public void setToolchainParam(String toolchain, String key, String val) {
    toolchainParams.computeIfAbsent(toolchain, (k) -> new LinkedHashMap<>()).put(key, val);
  }

  public void removeToolchainParam(String toolchain, String key) {
    LinkedHashMap<String, String> m = toolchainParams.get(toolchain);
    if (m != null)
      m.remove(key);
  }

  public List<String> getToolchains() {
    ArrayList<String> ret = new ArrayList<>(toolchainParams.keySet());
    if (defaultToolchain != null && !ret.contains(defaultToolchain))
      ret.add(defaultToolchain);
    return ret;
  }
  
  public Map<String, String> getToolchainParams(String toolchain) {
    LinkedHashMap<String, String> m = toolchainParams.get(toolchain);
    return (m != null) ? Collections.unmodifiableMap(m) : Collections.emptyMap();
  }

  public String getToolchainParam(String toolchain, String key) {
    LinkedHashMap<String, String> m = toolchainParams.get(toolchain);
    return (m != null) ? m.get(key) : null;
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
