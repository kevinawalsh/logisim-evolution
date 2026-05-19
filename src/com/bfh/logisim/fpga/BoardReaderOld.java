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

import java.util.Map;
import java.util.HashMap;

import java.awt.image.BufferedImage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.bfh.logisim.settings.BoardList;
import com.cburch.logisim.file.XmlUtil;
import com.cburch.logisim.util.Errors;

// Reader for the legacy xml board format
public class BoardReaderOld {

  private BoardReaderOld() { }

	public static Board parse(String path, Document doc) {
		try {
      // Legacy format has no name within xml, instead it uses file name as board name
      String name = BoardList.filenameForPath(path);

      ImageXmlFactoryOld imgFactory = parsePicture(doc);
      BufferedImage image = imgFactory.getPicture();
      String imageFormat = imgFactory.getFormat();
      byte imageBytes[] = imgFactory.getBytes();
			Board b = new Board(name, null, parseChipset(doc), image, imageFormat, imageBytes);
 
      // Figure out toolchains and toolchain params
      String apio_name = parseApioName(doc);
      String ofl_name = parseOpenFPGALoaderName(doc);
      String vtc;
      if ("altera".equalsIgnoreCase(b.fpga.Vendor))
        vtc = "Altera"; // Altera Quartus II
      else if ("xilinx".equalsIgnoreCase(b.fpga.Vendor))
        vtc = "Xilinx"; // Xilinx ISE
      else if ("lattice".equalsIgnoreCase(b.fpga.Vendor))
        vtc = "Lattice"; // same backend handles Diamond and ispLEVER
      else if ("gowin".equalsIgnoreCase(b.fpga.Vendor))
        vtc = "Gowin";
      else
        vtc = null;
      // For default synthesis tool, use apio if there was a name, or if no vendor toolchain known
      b.setDefaultSynthesisTool((apio_name != null || vtc == null) ? "Apio" : vtc);
      // For default programmer tool, use openFPGALoader if there was a name, or if no apio name or vendor toolchain known,
      // otherwise use apio if there was a name and no vendor toolchain known
      b.setDefaultProgrammingTool((ofl_name != null || (apio_name == null && vtc == null)) ? "openFPGALoader"
            : (apio_name != null || vtc == null) ? "Apio" : vtc);
      if (apio_name != null || vtc == null) {
        b.addToolchain("Apio", "synthesis,programming");
        if (apio_name != null)
          b.setToolchainParam("Apio", "board", apio_name);
      }
      if (vtc != null) {
        b.addToolchain(vtc, "synthesis,programming");
      }
      if (ofl_name != null || (apio_name == null && vtc == null)) {
        b.addToolchain("openFPGALoader", "programming");
        if (ofl_name != null)
          b.setToolchainParam("openFPGALoader", "board", ofl_name);
      }
      if (b.fpga.USBTMCAvailable) {
        b.addToolchain("USBTMC", "programming");
      }

      parseComponents(doc, "PinsInformation", b); // backwards compatability	
			parseComponents(doc, "ButtonsInformation", b); // backwards compatability	
			parseComponents(doc, "LEDsInformation", b); // backwards compatability	
			parseComponents(doc, "IOComponents", b); // new format
			return b;
		} catch (Exception e) {
      Errors.title("Error").show("The selected xml file was invalid: " + e.getMessage(), e);
      return null;
		}
	}

  private static NodeList getSection(Document doc, String name) {
		NodeList sections = doc.getElementsByTagName(name);
		if (sections.getLength() != 1)
			return null;
		return sections.item(0).getChildNodes();
  }

  private static ImageXmlFactoryOld parsePicture(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardPicture");
    if (xml == null)
      return null;
    Map<String, String> params = XmlUtil.xmlToMap(xml);

    int w = Integer.parseInt(params.getOrDefault("PictureDimension/Width", "0"));
    int h = Integer.parseInt(params.getOrDefault("PictureDimension/Height", "0"));
    String pixels = params.get("PixelData/PixelRGB");
    String codes = params.get("CompressionCodeTable/TableData");

    if (w == 0 || h == 0)
      throw new Exception("invalid or missing image dimensions");
    if (codes == null)
      throw new Exception("missing image compression code table");
    if (pixels == null)
      throw new Exception("missing image data");

    return new ImageXmlFactoryOld(w, h, codes.split(" "), pixels);
  }

  private static HashMap<String, String> xmlConversion = new HashMap<>();
  static {
    xmlConversion.put("FPGAInformation/Vendor", "Chip/vendor");
    xmlConversion.put("FPGAInformation/Family", "Chip/family");
    xmlConversion.put("FPGAInformation/Part", "Chip/part");
    xmlConversion.put("FPGAInformation/Speedgrade", "Chip/speedGrade");
    xmlConversion.put("FPGAInformation/Package", "Chip/package");

    xmlConversion.put("ClockInformation/Frequency", "Clock/frequency");
    xmlConversion.put("ClockInformation/FPGApin", "Clock/pin");
    xmlConversion.put("ClockInformation/IOStandard", "Clock/ioStandard");

    xmlConversion.put("FPGAInformation/JTAGPos", "JTAG/pos");
    xmlConversion.put("FPGAInformation/USBTMC", "USBTMC/available");

    xmlConversion.put("FPGAInformation/FlashPos", "Flash/pos");
    xmlConversion.put("FPGAInformation/FlashName", "Flash/name");

    xmlConversion.put("UnusedPins/PullBehavior", "UnmentionedPins/behavior");
  }

  private static Chipset parseChipset(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardInformation");
    if (xml == null)
      return null;
    Map<String, String> oldParams = XmlUtil.xmlToMap(xml);
    Map<String, String> newParams = new HashMap<>();
    xmlConversion.forEach((oldkey, newkey) -> {
      if (oldParams.containsKey(oldkey))
        newParams.put(newkey, oldParams.get(oldkey));
    });
    return new Chipset(newParams);
  }

  private static String parseApioName(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardInformation");
    if (xml == null)
      return null;
    String apio_name = XmlUtil.xmlToMap(xml).get("Toolchain/ApioName");
    if (apio_name != null)
      apio_name = apio_name.trim();
    if (apio_name != null && apio_name.equals(""))
      apio_name = null;
    return apio_name;
  }

  private static String parseOpenFPGALoaderName(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardInformation");
    if (xml == null)
      return null;
    String openFPGALoader_name = XmlUtil.xmlToMap(xml).get("Toolchain/openFPGAloaderName");
    if (openFPGALoader_name != null)
      openFPGALoader_name = openFPGALoader_name.trim();
    if (openFPGALoader_name != null && openFPGALoader_name.equals(""))
      openFPGALoader_name = null;
    return openFPGALoader_name;
  }

  private static void parseComponents(Document doc, String section, Board board)
      throws Exception {
    NodeList xml = getSection(doc, section);
    if (xml == null)
      return;
    for (int i = 0; i < xml.getLength(); i++) {
      Node node = xml.item(i);
      String name = node.getNodeName();
      if (name == null || name.equals("#text") || name.equals("#comment") || node.getNodeType() != Node.ELEMENT_NODE)
        continue;
      board.addComponent(BoardIO.parseXmlOld((Element)node));
    }
  }

}
