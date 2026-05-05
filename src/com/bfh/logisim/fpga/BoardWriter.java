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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.logisim.util.Errors;

class BoardWriter {

	public static boolean write(File file, Board board) {
    Document doc;
		try {
      Chipset chip = board.fpga;

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder parser = factory.newDocumentBuilder();

			doc = parser.newDocument();

			Element root = doc.createElement(board.name);
			doc.appendChild(root);

      put(doc, root, "BoardInformation", "Description of FPGA and its clock",
          make(doc, "FPGAInformation",
            "Vendor", chip.VendorName,
            "Part", chip.Part,
            "Family", chip.Technology,
            "Package", chip.Package,
            "Speedgrade", chip.SpeedGrade,
            "USBTMC", chip.USBTMCDownload,
            "JTAGPos", chip.JTAGPos,
            "FlashName", chip.FlashName,
            "FlashPos", chip.FlashPos),
          make(doc, "ClockInformation",
            "Frequency", chip.ClockFrequency,
            "FPGApin", chip.ClockPinLocation.toUpperCase(),
            "PullBehavior", chip.ClockPullBehavior,
            "IOStandard", chip.ClockIOStandard),
          make(doc, "UnusedPins",
            "PullBehavior", chip.UnusedPinsBehavior));

      Element io = put(doc, root, "IOComponents", "Description of all board I/O components");
			for (BoardIO comp : board)
				io.appendChild(comp.encodeXml(doc));

			ImageXmlFactory writer = new ImageXmlFactory();
			writer.CreateStream(board.image);

      Element pic = put(doc, root, "BoardPicture", "Compressed image of board",
          make(doc, "PictureDimension",
            "Width", board.image.getWidth(null),
            "Height", board.image.getHeight(null)),
          make(doc, "CompressionCodeTable",
            "TableData", writer.GetCodeTable()),
          make(doc, "PixelData",
            "PixelRGB", writer.GetCompressedString()));
		} catch (Exception e) {
      Errors.title("Error").show("Error encoding XML data: " + e.getMessage(), e);
      return false;
		}
		try {
			TransformerFactory tranFactory = TransformerFactory.newInstance();
			tranFactory.setAttribute("indent-number", 3);
			Transformer aTransformer = tranFactory.newTransformer();
			aTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
			Source src = new DOMSource(doc);
			Result dest = new StreamResult(file);
			aTransformer.transform(src, dest);
		} catch (Exception e) {
      Errors.title("Error").show("Error writing XML data to "+file+": " + e.getMessage(), e);
      return false;
		}
    return true;
	}

  private static Element put(Document doc, Element root,
      String section, String comment, Element ...elts) {
    Element sec = doc.createElement(section);
    root.appendChild(sec);
    sec.appendChild(doc.createComment(comment));
    for (Element e : elts)
      sec.appendChild(e);
    return sec;
  }

  private static Element make(Document doc, String name, Object ...keyval) {
    Element e = doc.createElement(name);
    // Note: no idea why setAttribute would be used only for the first one...
    // e.setAttribute(keyval[0], keyval[1]);
    // for (int i = 2; i < keyval.length; i += 2) {
    //   Attr a = doc.createAttribute(keyval[i]);
    //   a.setValue(keyval[i+1]);
    //   e.setAttributeNode(a);
    // }
    for (int i = 0; i < keyval.length; i += 2)
      if (keyval[i+1] != null)
        e.setAttribute(keyval[i].toString(), keyval[i+1].toString());
    return e;
  }

}
