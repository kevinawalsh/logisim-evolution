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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.generic.ComponentSearchPopup;
import com.cburch.logisim.gui.main.Canvas;

final class ComponentSearch {

  private String searchText = "";
  private ComponentSearchPopup searchPopup = null;

  public ComponentSearch() { }

  public boolean isActive() {
    return searchPopup != null;
  }

  public void beginSearch(Canvas canvas, String s, int x, int y) {
    if (isActive())
      return;
    searchText = s;
    searchPopup = new ComponentSearchPopup(canvas, tool -> dismissSearch(canvas, tool));
    searchPopup.showAt(x, y);
    updateSearch(canvas);
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
            updateSearch(canvas);
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
        tabComplete(canvas);
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
      updateSearch(canvas);
      e.consume();
    }
  }

  private void updateSearch(Canvas canvas) {
    List<AddTool> results = getMatchingTools(canvas);
    searchPopup.updateSearch(searchText, results);
  }

  private List<AddTool> getMatchingTools(Canvas canvas) {
    String prefix = searchText.toLowerCase();
    LogisimFile file = canvas.getProject().getLogisimFile();
    List<AddTool> results = new ArrayList<>();
    for (Tool tool : file.getToolsAndSublibraryTools()) {
      if (tool instanceof AddTool) {
        AddTool addTool = (AddTool) tool;
        if (addTool.getDisplayName().toLowerCase().startsWith(prefix))
          results.add(addTool);
      }
    }
    Collections.sort(results,
        (a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
    return results;
  }

  private void tabComplete(Canvas canvas) {
    // FIXME: use first (or currently selected) row from popup instead of longest prefix?
    List<AddTool> results = getMatchingTools(canvas);
    if (results.isEmpty())
      return;
    // Longest common prefix of all match display names (case-insensitive,
    // but stored in the case of the first match)
    String first = results.get(0).getDisplayName();
    int len = first.length();
    for (int i = 1; i < results.size(); i++) {
      String name = results.get(i).getDisplayName();
      int j = 0;
      while (j < len && j < name.length()
          && Character.toLowerCase(first.charAt(j)) == Character.toLowerCase(name.charAt(j)))
        j++;
      len = j;
    }
    String extended = first.substring(0, len);
    if (extended.length() > searchText.length()) {
      searchText = extended;
      updateSearch(canvas);
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
      // TODO: draw ghost immediately at mouse position
    }
  }

}
