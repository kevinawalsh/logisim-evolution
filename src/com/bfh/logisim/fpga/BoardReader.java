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
import java.util.HashMap;

import java.awt.image.BufferedImage;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cburch.logisim.util.Errors;

public class BoardReader {

  private BoardReader() { }
	public static Board read(String path) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
      Document doc;
      String name;
			if (path.startsWith("url:")) {
        name = path.substring(4);
				doc = parser.parse(new BoardReader().getClass().getResourceAsStream("/" + name));
      } else if (path.startsWith("file:")) {
        name = path.substring(5);
				doc = parser.parse(new File(name));
      } else {
        name = path;
				doc = parser.parse(new File(name));
      }

      int i = Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\'));
      name = i < 0 ? name : name.substring(i+1);
      if (name.toLowerCase().endsWith(".xml"))
        name = name.substring(0, name.length()-4);

			Board b = new Board(name, parseApioName(doc), parseOpenFPGALoaderName(doc), parseChipset(doc), parsePicture(doc));
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

  private static BufferedImage parsePicture(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardPicture");
    if (xml == null)
      return null;
    HashMap<String, String> params = xmlToMap(xml);

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

    ImageXmlFactory reader = new ImageXmlFactory();
    reader.SetCodeTable(codes.split(" "));
    reader.SetCompressedString(pixels);
    BufferedImage result = reader.GetPicture(w, h);
    return result;
  }

  private static HashMap<String, String> xmlToMap(NodeList xml) {
    HashMap<String, String> params = new HashMap<>();
    // System.out.println("xml :" + xml);
    for (int i = 0; i < xml.getLength(); i++) {
      Node node = xml.item(i);
      String name = node.getNodeName();
      // System.out.printf("node(%d, %s): %s\n", i, name, node);
      if (name == null || name.equals("#text") || name.equals("#comment"))
        continue;
      NamedNodeMap attrs = node.getAttributes();
      for (int j = 0; attrs != null && j < attrs.getLength(); j++) {
        Node attr = attrs.item(j);
        String tag = attr.getNodeName();
        String val = attr.getNodeValue();
        params.put(name+"/"+tag, val);
        // System.out.printf("  attr(%d, %s): %s\n", j, tag, val);
      }
    }
    return params;
  }

  private static Chipset parseChipset(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardInformation");
    if (xml == null)
      return null;
    return new Chipset(xmlToMap(xml));
  }

  private static String parseApioName(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardInformation");
    if (xml == null)
      return null;
    String apio_name = xmlToMap(xml).get("Toolchain/ApioName");
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
    String openFPGALoader_name = xmlToMap(xml).get("Toolchain/openFPGAloaderName");
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
      if (name == null || name.equals("#text") || name.equals("#comment"))
        continue;
      board.addComponent(BoardIO.parseXml(node));
    }
  }

}
