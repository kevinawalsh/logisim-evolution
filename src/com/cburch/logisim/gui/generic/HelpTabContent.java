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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.menu.HelpBroker;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Tool;

public class HelpTabContent extends JPanel {

  private static final int BTN_SIZE = 15;
  private static final int BTN_MARGIN = 4;

  private JEditorPane editor = new JEditorPane("text/html", "");
  private Project proj;
  private String currentTitle = "";
  private String currentHtml = "";

  public HelpTabContent(Frame frame) {
    super(new BorderLayout());
    proj = frame.getProject();
    editor.setEditable(false);
    editor.setOpaque(false);
    editor.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
        HelpBroker.followLink(e.getDescription(), proj);
    });

    JScrollPane scrollPane = new JScrollPane(editor);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);

    JButton popoutBtn = makePopoutButton();

    JLayeredPane layered = new JLayeredPane();
    layered.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
    layered.add(popoutBtn, JLayeredPane.PALETTE_LAYER);
    layered.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        int w = layered.getWidth(), h = layered.getHeight();
        scrollPane.setBounds(0, 0, w, h);
        int sbw = scrollPane.getVerticalScrollBar().getPreferredSize().width;
        popoutBtn.setBounds(w - BTN_SIZE - BTN_MARGIN - sbw, BTN_MARGIN, BTN_SIZE, BTN_SIZE);
      }
    });

    add(layered, BorderLayout.CENTER);
    viewNone();
  }

  private JButton makePopoutButton() {
    JButton btn = new JButton() {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        // semi-transparent fill
        // g2.setColor(new Color(160, 160, 160, 60));
        // g2.fillRect(0, 0, w, h);
        // border
        g2.setStroke(new java.awt.BasicStroke(2f,
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        // g2.setColor(new Color(100, 100, 100, 120));
        g2.setColor(new Color(50, 50, 50, 120));
        g2.drawLine(w-10, 1, 1, 1);
        g2.drawLine(1, 1, 1, h-2);
        g2.drawLine(1, h-2, w-2, h-2);
        g2.drawLine(w-2, h-2, w-2, 9);
        // arrow
        // g2.setColor(new Color(50, 50, 50, 120));
        g2.drawLine(7, h-8, w-2, 1);
        g2.drawLine(w-2, 1, w-6, 1);
        g2.drawLine(w-2, 1, w-2, 5);
        g2.dispose();
      }
    };
    btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 9f));
    btn.setOpaque(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusable(false);
    btn.setToolTipText("Open in separate window");
    btn.addActionListener(e -> openPopout());
    return btn;
  }

  private void openPopout() {
    String title = currentTitle.isEmpty() ? "Quick Help" : "Quick Help for " + currentTitle;
    JFrame win = new JFrame(title);
    JEditorPane popEditor = new JEditorPane("text/html", currentHtml);
    popEditor.setEditable(false);
    popEditor.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
        HelpBroker.followLink(e.getDescription(), proj);
    });
    JScrollPane sp = new JScrollPane(popEditor);
    sp.getVerticalScrollBar().setUnitIncrement(16);
    win.setContentPane(sp);
    win.setMinimumSize(new Dimension(300, 200));
    win.setSize(450, 500);
    win.setLocationRelativeTo(this);
    win.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    win.setVisible(true);
  }

  public void viewNone() {
    currentTitle = "";
    currentHtml = wrapHtml("<i>" + S.get("quickhelpNoneMessage") + "</i>");
    editor.setText(currentHtml);
    editor.setCaretPosition(0);
  }

  public void view(Tool tool) {
    if (tool == null) {
      viewNone();
      return;
    }
    String msg = tool.getQuickHelp();
    if (msg == null)
      msg = "<i>" + S.fmt("quickhelpMissingMessage", tool.getDisplayName()) + "</i>";
    currentTitle = tool.getDisplayName();
    currentHtml = wrapHtml(msg);
    editor.setText(currentHtml);
    editor.setCaretPosition(0);
  }

  public void view(Component comp) {
    if (comp == null) {
      viewNone();
      return;
    }
    String msg = getQuickHelp(comp);
    if (msg == null)
      msg = "<i>" + S.fmt("quickhelpMissingMessage", comp.getFactory().getDisplayName()) + "</i>";
    currentTitle = comp.getFactory().getDisplayName();
    currentHtml = wrapHtml(msg);
    editor.setText(currentHtml);
    editor.setCaretPosition(0);
  }

  private static String getQuickHelp(Component comp) {
    ComponentFactory source = comp.getFactory();
    if (source == null)
      return null;
    AttributeSet attrs = comp.getAttributeSet();
    String msg = (String)source.getFeature(ComponentFactory.QUICK_HELP, attrs);
    return msg;
  }

  private static String wrapHtml(String body) {
    // We'd like <p> class "trg" to be small-caps, but Swing's html renderer can't do text-transform
    // or font-variant. So we resort to this hack, replacing <p class="trg">...</p> with upper-case.
    body = Pattern.compile("(?i)(<p\\s+class=\"trg\">)(.*?)(</p>)")
      .matcher(body)
      .replaceAll(m -> m.group(1) + m.group(2).toUpperCase() + m.group(3));

    Font font = UIManager.getFont("Label.font");
    Color fg = UIManager.getColor("Label.foreground");
    Color fg2 = UIManager.getColor("Label.disabledForeground");
    Color bg = UIManager.getColor("Label.background");
    Color rule = bg != null && fg2 != null
      ? new Color(
          (fg2.getRed()   + bg.getRed())   / 2,
          (fg2.getGreen() + bg.getGreen()) / 2,
          (fg2.getBlue()  + bg.getBlue())  / 2)
      : new Color(180, 180, 180);

    String fontFamily = font != null ? font.getFamily() : "sans-serif";
    int fontSize = font != null ? font.getSize() : 10;
    int midPt = Math.max(fontSize - 1, 8);
    int smallPt = Math.max(fontSize - 2, 7);

    String color = fg != null
      ? String.format("#%02x%02x%02x", fg.getRed(), fg.getGreen(), fg.getBlue())
      : "#131314";
    String muted = fg2 != null
      ? String.format("#%02x%02x%02x", fg2.getRed(), fg2.getGreen(), fg2.getBlue())
      : "#888888";
    String rulec = String.format("#%02x%02x%02x", rule.getRed(), rule.getGreen(), rule.getBlue());

    return "<html><head><style>"
      + "body{font-family:" + fontFamily + ";font-size:" + fontSize + "pt;color:" + color + ";margin:6px}"
      + "h1{font-size:" + fontSize + "pt;font-weight:bold;margin:0 0 2px 0}"
      + "h2{font-size:" + midPt + "pt;font-weight:normal;color:" + muted + ";margin:0 0 2px 0}"
      + "h3{font-size:" + smallPt + "pt;font-weight:bold;border-top:1px solid " + rulec + ";margin:5px 0 0 0;padding-top:4px}"
      + "a{color:#4A8EDB;text-decoration:none}"
      + "p{margin:0 0 4px 0}"
      + "p.ref{font-size:" + smallPt + "pt;margin: 0 0 4px 0}"
      + "p.trg{font-size:" + smallPt + "pt;color:" + muted + ";margin:4px 0 0 0}"
      + "p.act{font-size:" + midPt + "pt;color:" + color + ";margin:0}"
      + "</style></head><body>"
      + body
      + "</body></html>";
  }

}
