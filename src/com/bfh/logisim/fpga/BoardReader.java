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
import java.util.Base64;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.bfh.logisim.settings.BoardList;
import com.cburch.logisim.file.XmlIterator;
import com.cburch.logisim.file.XmlUtil;
import com.cburch.logisim.util.Errors;

// Reader for the legacy xml board format
public class BoardReader {

  private BoardReader() { }
	public static Board read(String path) {
		try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
      Document doc;

			if (path.startsWith("jar|")) {
        String parts[] = path.split("\\|", 3);
        String jarpath = parts[1];
        String rscpath = parts[2];
        try (ZipFile zf = new ZipFile(jarpath)) {
          ZipEntry entry = zf.getEntry(rscpath);
          if (entry == null)
            throw new Exception(jarpath + " doesn't contain " + rscpath);
          doc = parser.parse(zf.getInputStream(entry));
        }
      } else if (path.startsWith("file|")) {
        String parts[] = path.split("\\|", 2);
        String filepath = parts[1];
				doc = parser.parse(new File(filepath));
      } else {
				doc = parser.parse(new File(path));
      }

      // New format is: <Board name="..."> ...
      // Old format is: <Name_of_Board>...<BoardInformation> ... <BoardPicture>...
      // - Conceivably, some board in old format could be named "Board".
      // - So if it start with something other than "Board" OR it contains
      //   both <BoardInformation> and <BoardPicture>, we fall back to old format.
      Element boardElt = doc.getDocumentElement();
      String outerTag = boardElt.getTagName();
      boolean oldFormat = !outerTag.equals("Board")
        || (doc.getElementsByTagName("BoardInformation").getLength() == 1
            && doc.getElementsByTagName("BoardPicture").getLength() == 1);
      if (oldFormat)
        return BoardReaderOld.parse(path, doc);

      // board.name is attribute of the top element
      String name = boardElt.getAttribute("name");
      if (name == null)
        name = BoardList.filenameForPath(path); // fallback: use file name instead

      // board.codename is attribute of the top element (optional)
      String codename = boardElt.getAttribute("codename");

      // board.fpga is in <FPGA>
      Chipset fpga = parseChipset(XmlUtil.getChildElement(boardElt, "FPGA"));

      // board.image is in <Picture>
      byte[] imageBytes = null;
      Element picElt = XmlUtil.getChildElement(boardElt, "Picture");
      if (picElt != null) {
        // imageFormat = picElt.getAttribute("format");
        // imageWidth = picElt.getAttribute("width");
        // imageHeight = picElt.getAttribute("height");
        String encoding = picElt.getAttribute("encoding");
        if (encoding == null || encoding.isEmpty())
          encoding = "base64";
        if (encoding.equalsIgnoreCase("base64")) {
          imageBytes = base64Decode(picElt);
        } else {
          Errors.title("Error").show("The selected xml contains a <Picture> with unrecognized encoding: " + encoding);
        }
      }
      BoardImage img = BoardImage.parse(imageBytes);
			
      Board b = new Board(name, codename, fpga, img.image, img.format, img.bytes);

      parseToolchains(b, XmlUtil.getChildElement(boardElt, "Toolchains"));
     
      parseIoComponents(b, XmlUtil.getChildElement(boardElt, "IOComponents"));
      return b;

    } catch (Exception e) {
      Errors.title("Error").show("The selected xml file was invalid: " + e.getMessage(), e);
      return null;
		}
	}

  private static Chipset parseChipset(Element elt) throws Exception {
    if (elt == null)
      return null;

    // FIXME: Revise. For now, we create a map compatible with the old format.
    // FIXME: many of these should be optional, or have sane defaults.
    HashMap<String, String> map = new HashMap<>();
    
    Element chipElt = XmlUtil.getChildElement(elt, "Chip");
    if (chipElt == null)
      throw new Exception("Required element <Chip> is missing");
    map.put("FPGAInformation/Vendor", chipElt.getAttribute("vendor"));
    map.put("FPGAInformation/Family", chipElt.getAttribute("family"));
    map.put("FPGAInformation/Part", chipElt.getAttribute("part"));
    map.put("FPGAInformation/Speedgrade", chipElt.getAttribute("speedGrade"));
    map.put("FPGAInformation/Package", chipElt.getAttribute("package"));
    
    Element clockElt = XmlUtil.getChildElement(elt, "Clock");
    if (clockElt == null)
      throw new Exception("Required element <Clock> is missing");
    map.put("ClockInformation/FPGApin", clockElt.getAttribute("pin"));
    map.put("ClockInformation/Frequency", clockElt.getAttribute("frequency"));
    map.put("ClockInformation/IOStandard", clockElt.getAttribute("ioStandard"));
    map.put("ClockInformation/PullBehavior", clockElt.getAttribute("pull"));

    Element jtagElt = XmlUtil.getChildElement(elt, "JTAG");
    String val = jtagElt == null ? null : jtagElt.getAttribute("pos"); // optional
    if (val != null && !val.isEmpty())
      map.put("FPGAInformation/JTAGPos", val);

    Element usbtmcElt = XmlUtil.getChildElement(elt, "USBTMC");
    val = usbtmcElt == null ? null : usbtmcElt.getAttribute("available"); // optional
    if (val != null && !val.isEmpty())
      map.put("FPGAInformation/USBTMC", val);
    
    Element flashElt = XmlUtil.getChildElement(elt, "Flash");
    val = flashElt == null ? null : flashElt.getAttribute("pos"); // optional
    if (val != null && !val.isEmpty())
      map.put("FPGAInformation/FlashPos", val);
    val = flashElt == null ? null : flashElt.getAttribute("name"); // optional
    if (val != null && !val.isEmpty())
      map.put("FPGAInformation/FlashName", val);

    Element unusedpinsElt = XmlUtil.getChildElement(elt, "UnusedPins");
    if (unusedpinsElt == null)
      throw new Exception("Required element <UnusedPins> is missing");
    map.put("UnusedPins/PullBehavior", unusedpinsElt.getAttribute("pull"));

    return new Chipset(map);
  }
  
  private static void parseToolchains(Board board, Element tcElt) throws Exception {
    if (tcElt == null)
      return;
    String def = tcElt.getAttribute("default");
    if (def != null && !def.isEmpty()) {
      board.setDefaultSynthesisTool(def);
      board.setDefaultProgrammingTool(def);
    }
    def = tcElt.getAttribute("defaultSynthesis");
    if (def != null && !def.isEmpty())
      board.setDefaultSynthesisTool(def);
    def = tcElt.getAttribute("defaultProgramming");
    if (def != null && !def.isEmpty())
      board.setDefaultProgrammingTool(def);
    for (Element child : XmlIterator.forChildElements(tcElt, "Toolchain")) {
      String name = child.getAttribute("name");
      if (name == null || name.isEmpty())
        throw new Exception("Required name attribute of <Toolchain> is missing");
      board.addToolchain(name, child.getAttribute("capabilities"));
      for (Element p : XmlIterator.forChildElements(child, "Param")) {
        String key = p.getAttribute("key");
        String val = p.getAttribute("value");
        if (key == null || key.isEmpty())
          throw new Exception("Required key attribute of <Param> is missing");
        if (val == null)
          continue;
        board.setToolchainParam(name, key, val);
      }
    }
  }

  private static byte[] base64Decode(Element elt) throws Exception {
    // The writer wraps at 80 chars; strip all whitespace before decoding.
    String encoded = elt.getTextContent().replaceAll("\\s+", "");
    return Base64.getDecoder().decode(encoded);
  }

  private static void parseIoComponents(Board board, Element ioElt) throws Exception {
    if (ioElt == null)
      return;
    for (Element child : XmlIterator.forChildElements(ioElt))
      board.addComponent(BoardIO.parseXml(child));
  }

}
