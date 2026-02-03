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

package com.cburch.logisim.util;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;

public class TextWrapping {
      
  // final static boolean HARD_BREAK_LONG_TOKENS = true;
  
  public static String[] split(String text) {
    if (text == null || text.isEmpty())
      return new String[] { "" };
    text = text.replace("\r\n", "\n").replace('\r', '\n'); // replace CRLF and CR with LF
    return text.split("\\n", -1);
  }

  public static String[] split(String text, int textWidth, Graphics g, Font font) {
      return split(text);
  }

      /*
    Font old = g.getFont();
    try {
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();

      ArrayList<String> out = new ArrayList<>();
      String[] paragraphs = text.split("\n", -1);

      for (String para : paragraphs) {
        if (para.isEmpty()) {
          out.add("");
          continue;
        }

        int len = para.length();
        int start = 0;

        // Keep leading spaces, can be used for monospace tables, indentation, etc.
        while (start < len) {

          // If the remaining part already fits, emit it.
          if (fm.stringWidth(para.substring(start)) <= textWidth) {
            out.add(para.substring(start));
            break;
          }

          // Find the furthest end index that fits within textWidth.
          int end = findMaxFittingEnd(para, start, textWidth, fm);

          if (end <= start) {
            // Emit at least one character anyway, to guarantee progress.
            out.add(para.substring(start, Math.min(start + 1, len)));
            start = Math.min(start + 1, len);
            continue;
          }

          // Prefer breaking at whitespace within (start, end].
          int breakAt = lastWhitespaceBetween(para, start, end);
          if (breakAt > start) {
            // Emit up to breakAt (excluding the whitespace).
            out.add(rstrip(para.substring(start, breakAt)));
            // Skip whitespace after break.
            start = skipWhitespaceForward(para, breakAt);
          } else {
            // No whitespace to break on in this range.
            if (HARD_BREAK_LONG_TOKENS) {
              // Hard break at end.
              out.add(para.substring(start, end));
              start = end;
            } else {
              // Emit the whole remaining token even if it exceeds width.
              out.add(para.substring(start));
              break;
            }
          }
        }
      }

      return out.toArray(new String[0]);
    } finally {
      g.setFont(old);
    }
  }
    */

  /**
   * Returns the maximum end index (exclusive) such that substring(start, end) fits within textWidth.
   * Guaranteed: end > start unless the string is empty.
   */
  /*
  private static int findMaxFittingEnd(String s, int start, int textWidth, FontMetrics fm) {
    int lo = start + 1;
    int hi = s.length();

    // Binary search for the largest fitting end.
    int best = start + 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int w = fm.stringWidth(s.substring(start, mid));
      if (w <= textWidth) {
        best = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return best;
  }
  */

  /*
  private static int lastWhitespaceBetween(String s, int start, int endExclusive) {
    // Look for last whitespace char in s[start, endExclusive)
    for (int i = endExclusive - 1; i >= start; i--) {
      if (Character.isWhitespace(s.charAt(i))) {
        return i;
      }
    }
    return -1;
  }
  */

  /*
  private static int skipWhitespaceForward(String s, int idx) {
    int i = idx;
    int n = s.length();
    while (i < n && Character.isWhitespace(s.charAt(i))) i++;
    return i;
  }
  */

  /*
  private static String rstrip(String s) {
    int i = s.length();
    while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) i--;
    return (i == s.length()) ? s : s.substring(0, i);
  }
  */

}
