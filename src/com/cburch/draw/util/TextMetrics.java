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

package com.cburch.draw.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;

public class TextMetrics {

	public final int i_ascent;
	public final int i_descent;
	public final int i_leading;
	public final int i_height; // = i_ascent + i_height + i_leading
	public final int i_width; // valid only if constructor was given a string
	
  public final float f_ascent;
	public final float f_descent;
	public final float f_leading;
	public final float f_height; // = f_ascent + f_height + f_leading
	public final float f_width; // valid only if constructor was given a string

	public TextMetrics(FontRenderContext frc, Font font, String text) {
    if (frc == null || font == null)
			throw new IllegalStateException("need FontRenderContext and Font to measure text");
		if (text == null) {
			text = "ÄAy";
			i_width = 0;
      f_width = 0f;
		} else {
      f_width = (float)font.getStringBounds(text, frc).getWidth();
			i_width = (int)f_width; // ceil? round?
		}

		LineMetrics lm = font.getLineMetrics(text, frc);
    f_ascent = lm.getAscent();
		f_descent = lm.getDescent();
		f_leading = lm.getLeading();
    f_height = f_ascent + f_descent + f_leading; // lm.getHeight();
		i_ascent = (int)Math.ceil(f_ascent);
		i_descent = (int)Math.ceil(f_descent);
		i_leading = (int)Math.ceil(f_leading);
		i_height = i_ascent + i_descent + i_leading; // (int)Math.ceil(f_height);
	
		// sanity checks...
		/*
		int fa = g.getFontMetrics(font).getAscent();
		int fd = g.getFontMetrics(font).getDescent();
		int fh = g.getFontMetrics(font).getHeight();
		if (fa != ascent || fd != descent || fh != height)
			System.out.println(
					"  a: " + ascent + " vs " + fa +
					"  d: " + descent + " vs " + fd +
					"  h: " + height + " vs " + fh);
		*/
	}

}
