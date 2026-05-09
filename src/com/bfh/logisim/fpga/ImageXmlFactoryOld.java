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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

public class ImageXmlFactoryOld {

	private static final char V2_IDENTIFIER = '@';

  private final int width, height;
  private final BufferedImage image;
  private final String format;
  private final byte bytes[];
	private final String[] CodeTable;
	private final StringBuffer AsciiStream;

	public BufferedImage getPicture() { return image; }
  public String getFormat() { return format; }
  public byte[] getBytes() { return bytes; }

  private static byte[] toPngBytes(BufferedImage img) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    return baos.toByteArray();
  }

  ImageXmlFactoryOld(int w, int h, String[] Table, String stream) throws IOException {
    this.width = w;
    this.height = h;
		this.CodeTable = Table.clone();
		this.AsciiStream = new StringBuffer();
		this.AsciiStream.append(stream);

		if (AsciiStream == null)
			throw new IOException("missing stream");
		if (CodeTable == null)
			throw new IOException("missing code table");
		if (CodeTable.length != 256)
			throw new IOException("bad code table size");
		BufferedImage result = null;
		Map<String, Integer> CodeLookupTable = new HashMap<String, Integer>();
		for (int i = 0; i < CodeTable.length; i++)
			CodeLookupTable.put(CodeTable[i], i);
		int index = 0;
		Set<String> TwoCodes = new HashSet<String>();
		TwoCodes.add("-");
		TwoCodes.add("+");
		TwoCodes.add("=");
		boolean jpegCompressed = AsciiStream.charAt(0) == V2_IDENTIFIER;
		if (jpegCompressed) {
			index++;
			ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
			while (index < AsciiStream.length()) {
				if (TwoCodes.contains(AsciiStream.substring(index, index + 1))) {
					bytestream.write((byte) (CodeLookupTable.get(AsciiStream
							.substring(index, index + 2)) - 128));
					index += 2;
				} else {
					bytestream.write((byte) (CodeLookupTable.get(AsciiStream
							.substring(index, index + 1)) - 128));
					index++;
				}
			}
      bytestream.flush();
      bytes = bytestream.toByteArray();
      format = "jpg";
			ByteArrayInputStream instream = new ByteArrayInputStream(bytes);
      image = ImageIO.read(instream);
		} else {
			image = new BufferedImage(width, height,
					BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D g2 = image.createGraphics(); // UI, no custom rendering hints
      try {
        g2.setBackground(Color.BLACK);
        String CurRedComp, CurGreenComp, CurBlueComp;
        for (int y = 0; y < height; y++) {
          for (int x = 0; x < width; x++) {
            if (TwoCodes.contains(AsciiStream.substring(index,
                    index + 1))) {
              CurRedComp = AsciiStream.substring(index, index + 2);
              index += 2;
            } else {
              CurRedComp = AsciiStream.substring(index, index + 1);
              index++;
            }
            if (TwoCodes.contains(AsciiStream.substring(index,
                    index + 1))) {
              CurGreenComp = AsciiStream.substring(index, index + 2);
              index += 2;
            } else {
              CurGreenComp = AsciiStream.substring(index, index + 1);
              index++;
            }
            if (TwoCodes.contains(AsciiStream.substring(index,
                    index + 1))) {
              CurBlueComp = AsciiStream.substring(index, index + 2);
              index += 2;
            } else {
              CurBlueComp = AsciiStream.substring(index, index + 1);
              index++;
            }
            if (!CodeLookupTable.containsKey(CurRedComp)
                || !CodeLookupTable.containsKey(CurGreenComp)
                || !CodeLookupTable.containsKey(CurBlueComp)) {
              throw new IOException("bad pixel data");
                }
            Color PixCol = new Color(CodeLookupTable.get(CurRedComp),
                CodeLookupTable.get(CurGreenComp),
                CodeLookupTable.get(CurBlueComp));
            g2.setColor(PixCol);
            g2.fillRect(x, y, 1, 1);
          }
        }
        bytes = toPngBytes(image);
        format = "png";
      } finally {
        g2.dispose();
      }
		}
	}

}
