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

package com.cburch.logisim.tools;

import java.awt.event.KeyEvent;

import com.cburch.logisim.gui.generic.ComponentSearchPopup;
import com.cburch.logisim.gui.main.Canvas;

final class ComponentSearch {

  private String searchText = "";
  private ComponentSearchPopup searchPopup = null;

  public ComponentSearch() { }

  public boolean isActive() {
    return searchPopup != null;
  }

  public void beginSearch(Canvas canvas, char initialChar, int x, int y) {
    if (isActive())
      return;
    searchText = String.valueOf(initialChar);
    searchPopup = new ComponentSearchPopup(canvas, initialChar,
        tool -> dismissSearch(canvas, tool));
    searchPopup.showAt(x, y);
  }

  public void cancelSearch() {
    dismissSearch(null, null);
  }

  public void keyPressed(Canvas canvas, KeyEvent e) {
    if (!isActive())
      return;
    switch (e.getKeyCode()) {
      case KeyEvent.VK_ESCAPE:
        cancelSearch();
        e.consume();
        return;
      case KeyEvent.VK_BACK_SPACE:
        if (searchText.length() > 0) {
          searchText = searchText.substring(0, searchText.length() - 1);
          if (searchText.isEmpty())
            cancelSearch();
          else
            searchPopup.updateSearch(searchText);
        }
        e.consume();
        return;
      case KeyEvent.VK_UP:
        searchPopup.moveSelection(-1);
        e.consume();
        return;
      case KeyEvent.VK_DOWN:
        searchPopup.moveSelection(1);
        e.consume();
        return;
      case KeyEvent.VK_TAB:
        searchText = searchPopup.tabComplete();
        e.consume();
        return;
      case KeyEvent.VK_ENTER:
        selectCurrentResult(canvas);
        e.consume();
        return;
      default:
        // let keyTyped handle printable characters
        return;
    }
  }

  public void keyReleased(Canvas canvas, KeyEvent e) { }

  public void keyTyped(Canvas canvas, KeyEvent e) {
    if (!isActive())
      return;
    char c = e.getKeyChar();
    if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c)) {
      searchText += c;
      searchPopup.updateSearch(searchText);
      e.consume();
    }
  }

  private void selectCurrentResult(Canvas canvas) {
    dismissSearch(canvas, searchPopup.getSelectedTool());
  }

  private void dismissSearch(Canvas canvas, AddTool tool) {
    if (!isActive())
      return;
    searchText = "";
    searchPopup.dismiss();
    searchPopup = null;
    if (tool != null) {
      canvas.getProject().setTool(tool);
      tool.snapToMouse(canvas);
    }
  }

}
