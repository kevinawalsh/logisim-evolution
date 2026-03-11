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

package com.cburch.logisim.gui.generic;
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.menu.HelpBroker;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Tool;

public class HelpTabContent extends JScrollPane {

  private JEditorPane editor = new JEditorPane("text/html", "");
  private Project proj;

  public HelpTabContent(Frame frame) {
    super();
    proj = frame.getProject();
    editor.setEditable(false);
    editor.setOpaque(false);
    editor.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
        HelpBroker.followLink(e.getDescription(), proj);
    });
    setViewportView(editor);
    getVerticalScrollBar().setUnitIncrement(16);
    viewNone();
  }

  public void viewNone() {
    editor.setText(wrapHtml("<i>" + S.get("quickhelpNoneMessage") + "</i>"));
    editor.setCaretPosition(0);
  }

  public void view(Tool tool) {
    String msg = tool.getQuickHelp();
    if (msg == null)
      msg = "<i>" + S.fmt("quickhelpMissingMessage", tool.getDescription()) + "</i>";
    editor.setText(wrapHtml(msg));
    editor.setCaretPosition(0);
  }

  private static String wrapHtml(String body) {
    Font font = UIManager.getFont("Label.font");
    Color fg = UIManager.getColor("Label.foreground");
    String fontFamily = font != null ? font.getFamily() : "sans-serif";
    int fontSize = font != null ? font.getSize() : 12;
    String color = fg != null
        ? String.format("#%02x%02x%02x", fg.getRed(), fg.getGreen(), fg.getBlue())
        : "#000000";
    return "<html><head><style>"
        + "body{font-family:" + fontFamily + ";font-size:" + fontSize + "pt;"
        + "color:" + color + ";margin:6px}"
        + "a{color:#4A8EDB}"
        + "</style></head><body>"
        + body
        + "</body></html>";
  }

}
