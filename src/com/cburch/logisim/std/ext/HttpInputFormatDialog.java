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

package com.cburch.logisim.std.ext;
import static com.cburch.logisim.std.Strings.S;

import java.util.Random;
import java.util.function.Consumer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.util.JInputDialog;

public class HttpInputFormatDialog extends JDialog implements JInputDialog<HttpInputFormat> {

  // For now, this duplicates a lot of code from SerialInputFormatDialog. They
  // can perhaps be combined later.
    
  private static final Font monoPlain = new Font(Font.MONOSPACED, Font.PLAIN, UIManager.getFont("TextField.font").getSize());
  private static final Font monoBold = new Font(Font.MONOSPACED, Font.BOLD, UIManager.getFont("TextField.font").getSize());

  // Top section shows illustration, example, and errors
  private JPanel illustration = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
  private JPanel demonstration = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
  private final Timer demoTimer = new Timer(2500, e -> demonstrate());

  // Main section has primary choice: raw bytes, or parsed/text-oriented
  private final JRadioButton rawFormatButton = new JRadioButton("one byte");
  private final JRadioButton parsedFormatButton = new JRadioButton("formatted text");

  // Record format subsection
  private final String[] fmtPrebaked = {
    "four 8-bit hex values, with spaces", "%8x %8x %8x %8x",
    "a pair of signed decimals, with parens and comma", "(%32d, %32d)",
    "three 8-bit hex values, separated by colons", "%8x:%8x:%8x",
    "6DOF 8-bit IMU readings, decimal with labels", "xa=%8d ya=%8d za=%8d xr=%8d yr=%8d zr=%8d",
    "6DOF 8-bit IMU readings, decimal vector notation", "acceleration: (%8d, %8d, %8d) rotation: (%8d, %8d, %8d)",
  };
  private final JRadioButton[] fmtPrebakedButtons;
  private final JRadioButton fmtCustomButton = new JRadioButton("custom:");
  private final JTextField fmtCustomTextfield = new JTextField(64);

  // Bottom buttons
  private final JButton ok = new JButton("OK");
  private final JButton cancel = new JButton("Cancel");
 
  private final Random random = new Random();
  private int rawExampleIdx = 0;
  private static final String rawExamples[] = {
    "A",
    "3",
    "\u0000",
    "\u0003",
    "7",
    "\u00a2",
  };
  
  private boolean programmaticChange;
  private boolean confirmed;
  private HttpInputFormat format, oldFormat;

  public boolean isConfirmed() { return confirmed; }

  public HttpInputFormat getValue() { return confirmed ? format : oldFormat; }

  public HttpInputFormatDialog(Window owner, HttpInputFormat format) {
    super(owner, "Http Response Format", ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    this.format = format;

    // fonts
    fmtCustomTextfield.setFont(monoPlain);

    // root panel and main axis
    JPanel root = new JPanel();
    root.setBorder(new EmptyBorder(12, 12, 12, 12));
    root.setLayout(new BorderLayout(0, 12));
    getContentPane().add(root, BorderLayout.CENTER);
    JPanel col = new JPanel();
    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
    root.add(col, BorderLayout.CENTER);

    // Top section
    JPanel top = new JPanel(); 
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    top.setBorder(new CompoundBorder(
        new EmptyBorder(0, 18, 0, 0),
        BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK)));
    col.add(top);
    for (JPanel p : new JPanel[]{ illustration, demonstration }) {
      p.setBorder(new EmptyBorder(0, 6, 0, 0));
      p.setAlignmentX(0f);
      p.setMinimumSize(new Dimension(0, 48));
      p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
      top.add(p);
    }

    // Main section
    ButtonGroup fmtGrp = new ButtonGroup();
    fmtGrp.add(rawFormatButton);
    fmtGrp.add(parsedFormatButton);
    col.add(buttonWithDescription(rawFormatButton,
          "First byte if the http response is returned directly."));
    col.add(Box.createVerticalStrut(10));
    col.add(buttonWithDescription(parsedFormatButton,
          "Http response data is formatted text with one or more values."));
 
    // Record format subsection
    JPanel fsub = subsection("Expected format of each http response is ...");
    fmtCustomTextfield.setToolTipText("Use a mix of text and value placeholders.\n"
        + "Valid placeholders each specify a width N (from 1 to 32) and a format:\n"
        + "%Nd -- an N-bit value written in signed decimal format\n"
        + "%Nu -- an N-bit value written in unsigned decimal format\n"
        + "%Nx -- an N-bit value written in hex format\n"
        + "%No -- an N-bit value written in octal format\n"
        + "%Nb -- an N-bit value written in binary format\n"
        + "%c -- an 8-bit value taken directly http response bytes\n"
        + "%% -- use two percent signs to match a literal percent in the http response");
    fmtPrebakedButtons = makeButtonGroup(fmtPrebaked, fmtCustomButton, fmtCustomTextfield);
    // each format option gets its own row 
    for (JRadioButton btn: fmtPrebakedButtons)
      fsub.add(row(2, btn));
    fsub.add(row(2, fmtCustomButton, fmtCustomTextfield));
    fsub.setAlignmentX(0f);
    fsub.setMaximumSize(new Dimension(Integer.MAX_VALUE, fsub.getPreferredSize().height));
    col.add(Box.createVerticalStrut(4));
    col.add(fsub);

    // Bottom row buttons
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    btns.setBorder(new EmptyBorder(0, 12, 12, 12));
    btns.add(ok);
    btns.add(cancel);
    getContentPane().add(btns, BorderLayout.SOUTH);

    // behavior: bottom row buttons
    ok.addActionListener(e -> { confirmed = true; dispose(); });
    cancel.addActionListener(e -> { confirmed = false; dispose(); });
    getRootPane().setDefaultButton(ok);

    // behavior: main buttons enable/disable subpanels
    Runnable toggle = () -> {
      boolean on = parsedFormatButton.isSelected();
      setEnabledDeep(fsub, on);
      illustrate();
    };
    rawFormatButton.addActionListener(e -> toggle.run());
    parsedFormatButton.addActionListener(e -> toggle.run());

    // initialize selections
    setValue(format);

    // finish layout
    pack();
    setMinimumSize(new Dimension(Math.max(560, getWidth()), getHeight()));
    setLocationRelativeTo(owner);
  
    demoTimer.setCoalesce(true);
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowOpened(java.awt.event.WindowEvent e) { demoTimer.start(); }
      @Override public void windowClosing(java.awt.event.WindowEvent e) { demoTimer.stop(); }
      @Override public void windowClosed (java.awt.event.WindowEvent e) { demoTimer.stop(); }
    });
  }

  public void setValue(HttpInputFormat newFormat) {
    format = oldFormat = newFormat;
    if (format.isRaw()) {
      fmtCustomTextfield.setText(fmtPrebaked[1]);
      rawFormatButton.doClick();
    } else {
      String fmt = format.getFormatString();
      fmtCustomTextfield.setText(fmt);
      parsedFormatButton.doClick();
    }
  }

  private static JPanel buttonWithDescription(JRadioButton rb, String desc) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    JPanel line1 = row(4, rb);
    JPanel line2 = row(0, new JLabel("<html><i>"+desc+"</i></html>"));
    line2.setBorder(new EmptyBorder(0, 22, 0, 0));
    panel.add(line1);
    panel.add(line2);
    panel.setAlignmentX(0f);
    // fsub.setMaximumSize(new Dimension(Integer.MAX_VALUE, fsub.getPreferredSize().height));
    return panel;
  }

  private static JPanel subsection(String title) {
    JPanel sub = new JPanel();
    sub.setLayout(new BoxLayout(sub, BoxLayout.Y_AXIS));
    sub.setBorder(new CompoundBorder(new EmptyBorder(6, 24, 6, 6),
          new TitledBorder(new EtchedBorder(), title)));
    return sub;
  }

  private JRadioButton[] makeButtonGroup(String[] defn, JRadioButton customBtn, JTextField customTxt) {
    final JRadioButton[] btns = new JRadioButton[defn.length/2];
    ButtonGroup grp = new ButtonGroup();
    grp.add(customBtn);
    for (int i = 0; i < defn.length; i += 2) {
      final String desc = defn[i];
      final String patt = defn[i+1];
      JRadioButton b = new JRadioButton(desc);
      btns[i/2] = b;
      grp.add(b);
      // behavior: prebaked option --> auto fill custom field
      b.addActionListener(e -> {
        programmaticChange = true;
        customTxt.setText(patt);
        programmaticChange = false;
      });
    }
   
    // behavior: custom text --> update example, and select prebaked
    uponChange(customTxt, (text) -> {
      if (!programmaticChange || !text.isEmpty())
        illustrate();
      if (programmaticChange)
        return;
      for (int i = 0; i < defn.length; i += 2) {
        final String desc = defn[i];
        final String patt = defn[i+1];
        if (text.equals(patt)) {
          btns[i/2].setSelected(true);
          return;
        }
      }
      customBtn.setSelected(true);
    });

    return btns;
  }

  private static JPanel row(int vgap, Component... comps) {
    JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, vgap));
    for (Component c : comps)
      r.add(c);
    return r;
  }

  private static void setEnabledDeep(Component c, boolean en) {
    c.setEnabled(en);
    if (c instanceof Container) {
      for (Component ch : ((Container)c).getComponents())
        setEnabledDeep(ch, en);
    }
  }
 
  private static JLabel box(String text) {
    JLabel l = new JLabel(text);
    l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
    l.setMinimumSize(new Dimension(0, 20));
    l.setPreferredSize(new Dimension(l.getPreferredSize().width, 20));
    return l;
  }

  private static JLabel box(String text, int fg, int bg, boolean monospaced) {
    JLabel l = new JLabel(text);
    if (monospaced)
      l.setFont(monoBold);
    l.setBorder(new CompoundBorder(
          new LineBorder(new Color(0xDDDDDD)),
          new EmptyBorder(2, 1, 2, 1)));
    l.setOpaque(true);
    l.setForeground(new Color(fg));
    l.setBackground(new Color(bg));
    l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
    l.setMinimumSize(new Dimension(0, 20));
    l.setPreferredSize(new Dimension(l.getPreferredSize().width, 20));
    return l;
  }
  
  private static String escapeForNonPrintable(byte c) {
    switch (c) {
    case '\n': return "\\n";
    case '\r': return "\\r";
    case '\t': return "\\t";
    // case '\u0007': return "\\a";
    // case '\b': return "\\b";
    // case '\u001B': return "\\e";
    // case '\u000C': return "\\f";
    // case '\u000B': return "\\v";
    default: return String.format("\\x02x", (int)(c & 0xff));
    }
  }

  private static void addTextAsBoxes(JPanel panel, byte[] s) {
    String prev = "";
    for (byte c : s) {
      if (0x20 <= c && c <= 0x7E) {
        prev += (char)c;
        continue;
      }
      if (!prev.isEmpty())
        panel.add(box(prev, 0x000000, 0xffffff, true));
      panel.add(box(escapeForNonPrintable(c), 0x003300, 0xccffcc, true));
    }
    if (!prev.isEmpty())
      panel.add(box(prev, 0x000000, 0xffffff, true));
  }
  
  private void illustrate() {
    illustration.removeAll();
    if (rawFormatButton.isSelected())
      illustrateRaw();
    else
      illustrateParsed();
    illustration.invalidate();
    illustration.revalidate();
    illustration.repaint();
    demonstrate();
  }

  private void illustrateRaw() {
    format = new HttpInputFormat();
    illustration.add(box("Http response looks like "));
    illustration.add(box("byte", 0x003300, 0xccffcc, false));
    illustration.add(box(" ... rest is ignored ..."));
  }

  private void illustrateParsed() {
    String fmtText = fmtCustomTextfield.getText();
    format = new HttpInputFormat(fmtText);
    if (!format.isValid()) {
      String msg = "Error: " + format.errorMessage();
      illustration.add(box(msg, 0x800000, 0xFFE6E6, false));
    } else {
      illustration.add(box("Each http response is "));
      for (HttpInputFormat.Token t : format.getTokens()) {
        if (t instanceof HttpInputFormat.StaticToken) {
          HttpInputFormat.StaticToken tt = (HttpInputFormat.StaticToken)t;
          addTextAsBoxes(illustration, tt.bytes);
        } else if (t instanceof HttpInputFormat.ByteToken) {
          illustration.add(box("byte", 0x003300, 0xccffcc, false));
        } else if (t instanceof HttpInputFormat.SignedDecimalToken) {
          illustration.add(box(t.width+"-bit int", 0x00264d, 0xe6f2ff, false));
        } else if (t instanceof HttpInputFormat.RadixToken) {
          HttpInputFormat.RadixToken tt = (HttpInputFormat.RadixToken)t;
          if (tt.radix == 16)
            illustration.add(box(t.width+"-bit hex", 0x804000, 0xfff2e6, false));
          else if (tt.radix == 10)
            illustration.add(box(t.width+"-bit uint", 0x26004d, 0xf2e6ff, false));
          else if (tt.radix == 8)
            illustration.add(box(t.width+"-bit octal", 0x33334d, 0xf0f0f5, false));
          else if (tt.radix == 2)
            illustration.add(box(t.width+"-bit binary", 0x333300, 0xffffe6, false));
          else
            illustration.add(box("???base"+tt.radix+"_"+t.width, 0x330000, 0xffe6e6, false));
        } else {
          illustration.add(box("???_"+t.width, 0x330000, 0xffe6e6, false));
        }
      }
    }
  }

  int biasedUnsigned(int b) {
    long max = (1L << b) - 1;
    int a = (int)Math.min(max, b <= 8 ? 15L : 999L);       // small
    int bnd = (int)Math.min(max, b <= 8 ? 99L : 100_000L); // small-ish

    double u = random.nextDouble();
    if (u < 0.50) return (int)random.nextInt(a + 1);   // 50% in [0..999]
    if (u < 0.80) return (int)random.nextInt(bnd + 1); // 30% in [0..100k]
    return (int)(random.nextLong() & max);             // 25% anywhere
  }

  String rnd(String fmt, int bits) {
    long r = biasedUnsigned(bits);
    return String.format(fmt, r);
  }

  private void demonstrate() {
    demonstration.removeAll();
    if (format == null)
      return;
    if (!format.isValid()) {
      demonstration.add(box("Example will be generated when error is fixed..."));
    } else if (format.isRaw()) {
      demonstration.add(box("Example: "));
      String s = rawExamples[rawExampleIdx];
      rawExampleIdx = (rawExampleIdx + 1) % rawExamples.length;
      if (s.charAt(0) < 0x20) {
        // show each byte as individual hex code
        for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          demonstration.add(box(String.format("%02x", 0xff & (int)c), 0x003300, 0xccffcc, true));
        }
      } else {
        // show most bytes as ascii, a few as escapes
        for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          if (0x20 <= c && c <= 0x7E)
            demonstration.add(box(" "+c+" ", 0x000000, 0xffffff, true));
          else
            demonstration.add(box(escapeForNonPrintable((byte)c), 0x003300, 0xccffcc, true));
        }
      }
    } else {
      boolean alt = random.nextBoolean();
      demonstration.add(box("Example: "));
      for (HttpInputFormat.Token t : format.getTokens()) {
        if (t instanceof HttpInputFormat.StaticToken) {
          HttpInputFormat.StaticToken tt = (HttpInputFormat.StaticToken)t;
          addTextAsBoxes(demonstration, tt.bytes);
        } else if (t instanceof HttpInputFormat.ByteToken) {
          int r = random.nextInt() & 0xff;
          String b = (!alt && 0x20 <= r && r <= 0x7E & r != '\\') ?
              ""+(char)r : String.format("\\x%02x", r);
          demonstration.add(box(b, 0x003300, 0xccffcc, false));
        } else if (t instanceof HttpInputFormat.SignedDecimalToken) {
          String s;
          int bits = t.width;
          long r;
          if (bits <= 4) {
            // pick randomly at uniform
            r = random.nextInt(1 << bits) - (1 << (bits-1));
          } else {
            r = biasedUnsigned(bits-1);
            if (random.nextDouble() < 0.25)
              r = -(r+1);
          }
          s = String.format(alt ? "%+d" : "%d", r);
          demonstration.add(box(s, 0x00264d, 0xe6f2ff, false));
        } else if (t instanceof HttpInputFormat.RadixToken) {
          HttpInputFormat.RadixToken tt = (HttpInputFormat.RadixToken)t;
          if (tt.radix == 16) {
            demonstration.add(box(rnd(alt ? "%0"+tt.maxDigits+"X" : "%x", t.width), 0x804000, 0xfff2e6, false));
          } else if (tt.radix == 10) {
            demonstration.add(box(rnd("%d", t.width), 0x26004d, 0xf2e6ff, false));
          } else if (tt.radix == 8) {
            demonstration.add(box(rnd(alt ? "%0"+tt.maxDigits+"o" : "%o", t.width), 0x33334d, 0xf0f0f5, false));
          } else if (tt.radix == 2) {
            String s = "";
            for (int i = 0; i < t.width; i++) {
              s += random.nextBoolean() ? '1' : '0';
              if (alt && i != t.width - 1 && s.equals("0"))
                s = "";
            }
            demonstration.add(box(s, 0x333300, 0xffffe6, false));
          } else {
            demonstration.add(box("???_"+t.width, 0x330000, 0xffe6e6, false));
          }
        } else {
          demonstration.add(box("???_"+t.width, 0x330000, 0xffe6e6, false));
        }
      }
    }
    demonstration.invalidate();
    demonstration.revalidate();
    demonstration.repaint();
  }

  private static void uponChange(JTextField field, Consumer<String> func) {
    field.getDocument().addDocumentListener(new DocumentListener () {
      public void changedUpdate(DocumentEvent e) { func.accept(field.getText()); }
      public void removeUpdate(DocumentEvent e) { func.accept(field.getText()); }
      public void insertUpdate(DocumentEvent e) { func.accept(field.getText()); }
    });
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      HttpInputFormatDialog d = new HttpInputFormatDialog(null, new HttpInputFormat("%32d %32d %c"));
      d.setVisible(true);
      System.exit(0);
    });
  }

}
