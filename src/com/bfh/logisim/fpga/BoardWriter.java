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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.util.Errors;

class BoardWriter {

  public static boolean write(File file, Board board) {
    try {
      StringBuilder sb = new StringBuilder();
      sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");

      // Root element.
      sb.append("<Board")
        // .append(a("id",   board.name))
        .append(a("name", board.name))
        .append(">\n");

      // FPGA chip, clock, and unused-pins settings
      Chipset chip = board.fpga;
      sb.append("   <FPGA>\n");
      sb.append("      <Chip")
        .append(a("vendor",     chip.VendorName))
        .append(a("family",     chip.Technology))
        .append(a("part",       chip.Part))
        .append(a("speedGrade", chip.SpeedGrade))
        .append(a("package",    chip.Package))
        .append("/>\n");
      sb.append("<JTAG")
        .append(a("pos", "" + chip.JTAGPos))
        .append("/>\n");
      if (chip.FlashDefined) {
        sb.append("<Flash")
          .append(a("pos", "" + chip.FlashPos))
          .append(a("name", chip.FlashName))
          .append("/>\n");
      }
      if (chip.USBTMCAvailable) {
        sb.append("<USBTMC available=\"true\"/>");
      }
      sb.append("      <Clock")
        .append(a("pin",        chip.ClockPinLocation))
        .append(a("frequency",  "" + chip.ClockFrequency))
        .append(a("ioStandard", "" + chip.ClockIOStandard))
        .append(a("pull",       "" + chip.ClockPullBehavior))
        .append("/>\n");
      sb.append("      <UnusedPins")
        .append(a("pull", "" + chip.UnusedPinsBehavior))
        .append("/>\n");
      sb.append("   </FPGA>\n");

      // Toolchains section
      String def = board.getDefaultToolchain();
      List<String> toolchains = board.getToolchains();
      if (def == null && toolchains.isEmpty()) {
        sb.append("   <Toolchains/>\n");
      } else {
        sb.append("   <Toolchains");
        if (def != null)
          sb.append(a("default", def));
        if (toolchains.isEmpty()) {
          sb.append("/>\n");
        } else {
          sb.append(">\n");
          for (String tc : toolchains) {
            Map<String, String> params = board.getToolchainParams(tc);
            sb.append("     <Toolchain")
              .append(a("name", tc));
            if (params.isEmpty()) {
              sb.append("/>\n");
            } else {
              sb.append(">\n");
              for (Map.Entry<String, String> kv : params.entrySet()) {
                sb.append("        <Param")
                  .append(a("key", kv.getKey()))
                  .append(a("value", kv.getValue()))
                  .append("/>\n");
              }
              sb.append("      </Toolchain>\n");
            }
          }
          sb.append("    </Toolchains>\n");
        }
      }

      // I/O components
      if (board.getIoComponents().isEmpty()) {
        sb.append("   <IOComponents/>\n");
      } else {
        sb.append("   <IOComponents>\n");
        for (BoardIO comp : board.getIoComponents())
          appendIO(sb, comp);
        sb.append("   </IOComponents>\n");
      }

      // Picture: base64-encoded, wrapped at 76 chars per line
      int picW = board.image.getWidth(null);
      int picH = board.image.getHeight(null);
      String fmt = board.imgFormat;
      String base64 = ImageXmlFactory.encodeToBase64(board.imgBytes);
      sb.append("   <Picture format=\""+fmt+"\" encoding=\"base64\"")
        .append(a("width",  "" + picW))
        .append(a("height", "" + picH))
        .append(">\n");
      for (int i = 0; i < base64.length(); i += 76)
        sb.append("      ").append(base64, i, Math.min(i + 76, base64.length())).append("\n");
      sb.append("   </Picture>\n");

      sb.append("</Board>\n");

      try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
        out.write(sb.toString());
      }
      return true;
    } catch (Exception e) {
      Errors.title("Error").show("Error writing board to " + file + ": " + e.getMessage(), e);
      return false;
    }
  }

  // Returns a single XML attribute formatted as: key="value"
  private static String a(String key, String value) {
    return " " + key + "=\"" + attr(value) + "\"";
  }

  // XML-escapes an attribute value
  private static String attr(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
  }

  // Appends one <IOType .../> element to sb.
  // Attribute order:
  //   1. label
  //   2. per-component params (NrOfPins, Orientation)
  //   3. pins (pin for single-bit, FPGAPin_N for multi-bit)
  //   4. pin parameters (pull, polarity, drive, ioStandard)
  //   5. geometry (x, y, width, height)
  private static void appendIO(StringBuilder sb, BoardIO io) {
    sb.append("      <").append(io.type);

    // 1. Label
    if (io.label != null)
      sb.append(a("label", io.label));

    // 2. Per-component params
    if (io.width > 1)
      sb.append(a("NrOfPins", "" + io.width));
    if (io.orientation != null)
      sb.append(a("Orientation", "" + io.orientation));

    // 3. Pins
    if (io.width == 1) {
      sb.append(a("pin", io.pins[0]));
    } else {
      for (int i = 0; i < io.width; i++)
        sb.append(a("FPGAPin_" + i, io.pins[i]));
    }

    // 4. Pin parameters
    if (io.pull != PullBehavior.UNKNOWN)
      sb.append(a("pull", "" + io.pull));
    if (io.activity != PinActivity.UNKNOWN && io.type != BoardIO.Type.Pin)
      sb.append(a("polarity", "" + io.activity));
    if (io.strength != DriveStrength.UNKNOWN)
      sb.append(a("drive", "" + io.strength));
    if (io.standard != IoStandard.UNKNOWN && io.standard != IoStandard.DEFAULT)
      sb.append(a("ioStandard", "" + io.standard));

    // 5. Geometry
    sb.append(a("x",      "" + io.rect.x))
      .append(a("y",      "" + io.rect.y))
      .append(a("width",  "" + io.rect.width))
      .append(a("height", "" + io.rect.height));

    sb.append("/>\n");
  }

}
