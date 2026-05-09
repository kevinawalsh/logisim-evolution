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

import java.io.ByteArrayInputStream;
import java.util.Iterator;

import java.awt.Image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

// Helper class for parsing and serializing board images
public class BoardImage {

  public final byte bytes[]; // original image encoding
  public final Image image; // unscaled, parsed from bytes
  public final String format; // "jpg" or "png"

  public BoardImage(Image image, String format, byte[] bytes) {
    this.bytes = bytes;
    this.format = format;
    this.image = image;
  }

  public static BoardImage parse(byte bytes[]) throws Exception {
    if (bytes == null)
      return new BoardImage(null, null, null);
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    try (ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext())
        throw new Exception("Unknown image format");
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis);
        Image image = reader.read(0);
        String format = reader.getFormatName();
        return new BoardImage(image, format, bytes);
      } finally {
        reader.dispose();
      }
    } catch (Exception ex) {
      throw new Exception("Failed to decode board image", ex);
    }
  }

}
