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

package com.bfh.logisim.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.BoardIO;
import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.util.GraphicsUtil;
import static com.bfh.logisim.fpga.PinBindings.Dest;
import static com.bfh.logisim.fpga.PinBindings.Source;

public class BindingsDialog extends JDialog {

  private final Board board;
  private PinBindings pinBindings;
  private double imgScale;
  private Point imgOffset;

  private Synthetic zeros, ones, constants, bitbucket;
  private Synthetic[] synthetics;

  private JButton cancel = new JButton("Cancel");
  private JButton unmap = new JButton("Unset");
  private JButton reset = new JButton("Reset All");
  private JButton done = new JButton("Done");
  private JLabel status = new JLabel();

  private HashMap<BoardIO, Rect> rects = new HashMap<>();
  private SelectionPanel picture = new SelectionPanel();
  private SourceList sources;
  JLayeredPane overlay;

  // Caution: these *must* be Integer, not int. See javadoc for JLayeredPane.
  private static final Integer LAYER_BOTTOM = 0;
  private static final Integer LAYER_TOP = 1;

  public BindingsDialog(Board board, PinBindings pinBindings, JFrame parentFrame) {
    super(parentFrame, ModalityType.APPLICATION_MODAL);
    this.board = board;
    this.pinBindings = pinBindings;
    this.imgScale = Board.scaleForFitted(board.image, Board.STD_IMG_WIDTH, Board.STD_IMG_HEIGHT);
    this.imgOffset = Board.offsetForFitted(board.image, Board.STD_IMG_WIDTH, Board.STD_IMG_HEIGHT);
    sources = new SourceList();

    overlay = new JLayeredPane();

    for (BoardIO io : board.getIoComponents()) {
      Rect r = new Rect(io);
      rects.put(io, r);
      overlay.add(r, LAYER_TOP);
      r.setBounds(scaleToFullsize(io.rect));
      r.setVisible(false);
    }

    sources.recalculateAllBitCounts();

    setTitle("Configure FPGA Pin Bindings");
    setResizable(false);
    setAlwaysOnTop(false);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

    // Synthetic BoardIO buttons
    zeros = new Synthetic(BoardIO.Type.AllZeros, 0, "Zeros", "zeros.gif",
        "Use constant 0 (or all zeros) for selected input signal.");
    ones = new Synthetic(BoardIO.Type.AllOnes, -1, "Ones", "ones.gif",
        "Use constant 1 (or all ones) for selected input signal.");
    constants = new Synthetic(BoardIO.Type.Constant, 0, "Constant", "constants.gif",
        "Define a constant to use for selected input signal.");
    bitbucket = new Synthetic(BoardIO.Type.Unconnected, 0, "Disconnected", "disconnected.gif",
        "Leave the selected output or inout signal disconnected.");
    synthetics = new Synthetic[] { ones, zeros, constants, bitbucket };
    JPanel buttonpanel = new JPanel(); // default FlowLayout
    buttonpanel.setBackground(Color.BLACK);
    for (Synthetic b : synthetics)
      buttonpanel.add(b);

    // Component selection list
    JScrollPane scroll = new JScrollPane(sources);
    scroll.setToolTipText("<html>Select a component and connect it to a board I/O resource.<br>"
        + "Use drop-down menu to expand or change component type.</html>");
   
    // Action buttons along right side
    cancel.addActionListener(e -> finished(false));
    unmap.addActionListener(e -> sources.unmapCurrent());
    reset.addActionListener(e -> sources.resetAll());
    done.addActionListener(e -> { finished(true); });

    getRootPane().setDefaultButton(done);
    getRootPane().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) { finished(false); }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

    // Scroll panel and status along left side
    status.setBorder(
        BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
          BorderFactory.createEmptyBorder(2, 2, 2, 2)));

    overlay.add(picture, LAYER_BOTTOM);
    picture.setBounds(0, 0, picture.getWidth(), picture.getHeight());
    overlay.setPreferredSize(picture.getPreferredSize());
    overlay.setMinimumSize(picture.getPreferredSize());
    JPanel top = new JPanel();
    top.add(overlay);
    top.setBackground(Color.BLACK);
    top.setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Color.BLACK));

    setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = GridBagConstraints.RELATIVE;
    c.gridwidth = 2;
    add(top, c);
    add(buttonpanel, c);

    c.gridwidth = 1;
    c.gridx = 1;
    c.insets.left = c.insets.right = c.insets.bottom = 5;
    c.weightx = 0.0;
    c.fill = GridBagConstraints.BOTH;
    c.weighty = 1.0;
    add(new JPanel(), c); // filler
    c.weighty = 0.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    add(cancel, c);
    add(unmap, c);
    add(reset, c);
    add(done, c);

    c.gridx = 0;
    c.gridwidth = 2;
    c.insets.left = c.insets.right = 0;
    c.insets.top = c.insets.bottom = 0;
    add(status, c);

    c.fill = GridBagConstraints.BOTH;
    c.gridwidth = 1;
    c.gridheight = 4;
    c.gridx = 0;
    c.gridy = 2;
    c.weightx = 1.0;
    c.insets.left = 5;
    c.insets.right = 0;
    c.insets.top = c.insets.bottom = 5;
    add(scroll, c);

    if (sources.model.getSize() > 0)
      sources.setSelectedIndex(0);

    updateStatus();

    pack();
    setLocationRelativeTo(parentFrame);
  }

  public boolean cancelled = false;
  private void finished(boolean done) {
    cancelled = !done;
    setVisible(false);
    dispose();
  }

  private class SelectionPanel extends JPanel {
    SelectionPanel() {
      setPreferredSize(new Dimension(Board.STD_IMG_WIDTH, Board.STD_IMG_HEIGHT));
      setMinimumSize(new Dimension(Board.STD_IMG_WIDTH, Board.STD_IMG_HEIGHT));
      setBackground(Color.BLACK);
      setOpaque(true);
    }
    @Override
    public int getWidth() { return Board.STD_IMG_WIDTH; }
    @Override
    public int getHeight() { return Board.STD_IMG_HEIGHT; }
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Board.drawFitted(g, board.image, Board.STD_IMG_WIDTH, Board.STD_IMG_HEIGHT);
    }
  }

  private static final Color MISTY = new Color(1f, 0f, 0f, 0.4f); // fill
  private static final Color MISTYB = new Color(1f, 0f, 0f); // border
  private static final Color HILIGHT = new Color(0f, 1f, 0f, 0.4f); // fill
  private static final Color HILIGHTB = new Color(0f, 1f, 0f); // border
  private static final Color HOVER = new Color(1f, 1f, 0f, 0.4f); // fill
  private static final Color HOVERB = new Color(1f, 1f, 0f); // border
  private static final Color MAPPED = new Color(0.4f, 0.7f, 0.1f, 0.4f); // fill
  private static final Color MAPPEDB = new Color(0.4f, 0.7f, 0.1f); // border
  private static final Color MAPPED_TEXT = new Color(0, 0x60, 0); // text

  private class Rect extends JPanel implements MouseListener {
    BoardIO io;
    boolean emphasized, hover;
    int emphasizeBit; // -1 for all
    int nmapped;
    boolean isMapped[];

    Rect(BoardIO io) {
      this.io = io;
      isMapped = new boolean[io.width];
      nmapped = 0;
      setOpaque(false);
      setVisible(false);
      addMouseListener(this);
      setToolTipText("Map component to " + io);
    }

    @Override
    protected void paintComponent(Graphics g) {
      boolean fullymapped = nmapped == io.width;
      g.setColor(hover ? HOVER :
          emphasized && (emphasizeBit == -1 || io.width == 1) ? HILIGHT :
          !fullymapped ? MISTY :
          MAPPED);
      Rectangle rr = scaleToFullsize(io.rect);
      g.fillRect(0, 0, rr.width, rr.height);
      g.setColor(hover ? HOVERB : emphasized ? HILIGHTB : fullymapped ? MAPPEDB : MISTYB);
      g.drawRect(0, 0, rr.width-1, rr.height-1);
      if (io.width > 1 && io.orientation != null) {
        Color fill[] = new Color[io.width];
        Color edge[] = new Color[io.width];
        for (int i = 0; i < io.width; i++) {
          fill[i] = hover || !isMapped[i] ? null :
            (emphasized && (emphasizeBit == -1 || emphasizeBit == i)) ? HILIGHT :
            MAPPED;
          edge[i] = hover ? HOVERB :
            (emphasized && (emphasizeBit == -1 || emphasizeBit == i)) ? HILIGHTB :
            isMapped[i] ? MAPPEDB : MISTYB;
        }
        g.translate(-rr.x, -rr.y);
        io.drawOrientedPins(g, imgOffset.x, imgOffset.y, imgScale, fill, edge, null);
        g.translate(rr.y, rr.y);
      } else if (io.width > 1 && rr.width > rr.height) {
        //    _________
        //   |/_/_/_/_/|  width = 6 bit
        //
        double dx = (rr.width - 1.0) / (io.width - 1.0);
        int xt = (int)(nmapped*dx);
        int xb = (int)((nmapped-1)*dx);
        int xr = rr.width - 1;
        int yb = rr.height - 1;
        int[] x = new int[] {xt, xr, xr, xb};
        int[] y = new int[] {0,  0,  yb, yb};
        g.setColor(MISTY);
        g.fillPolygon(x, y, 4);
        g.setColor(MISTYB);
        g.drawPolygon(x, y, 4);
        x = new int[] {0, xt, xb, 0};
        g.setColor(emphasized ? HILIGHT : MAPPED);
        g.fillPolygon(x, y, 4);
        g.setColor(emphasized ? HILIGHTB : MAPPEDB);
        g.drawPolygon(x, y, 4);
      } else if (io.width > 1) {
        //  ___
        // |_- |
        // |  _|
        // |_- |  width = 4 bits
        // |  _|
        // |_-_|
        // 
        double dy = (rr.height - 1.0) / (io.width - 1.0);
        int yb = rr.height - 1;
        int yl = yb - (int)((nmapped-1)*dy);
        int yr = yb - (int)(nmapped*dy);
        int xr = rr.width - 1;
        int[] x = new int[] {0,  0, xr, xr};
        int[] y = new int[] {yl, 0, 0,  yr};
        g.setColor(MISTY);
        g.fillPolygon(x, y, 4);
        g.setColor(MISTYB);
        g.drawPolygon(x, y, 4);
        y = new int[] {yb, yl, yr, yb};
        g.setColor(emphasized ? HILIGHT : MAPPED);
        g.fillPolygon(x, y, 4);
        g.setColor(emphasized ? HILIGHTB : MAPPEDB);
        g.drawPolygon(x, y, 4);
      }

      }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e))
        return;
      if (sources.current == null)
        return;
      if (sources.current.width.size() == 1 && io.width > 1)
        doBitSelectPopup(e);
      else {
        Dest dest = pinBindings.mappings.get(sources.current);
        if (dest != null && dest.io == io)
          sources.unmapCurrent();
        else
          sources.mapCurrent(io, -1);
      }
    }

    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
    public void mouseExited(MouseEvent e) { hover = false; repaint(); }
    public void mousePressed(MouseEvent e) { }
    public void mouseReleased(MouseEvent e) { }

    void doBitSelectPopup(MouseEvent e) {
      JPopupMenu popup = new JPopupMenu("Select Bit");
      String[] pinLabels = io.type.pinLabels(io.width);
      Dest dest = pinBindings.mappings.get(sources.current);
      for (int i = 0; i < io.width; i++) {
        final int bit = i;
        JCheckBoxMenuItem menu = new JCheckBoxMenuItem(pinLabels[i]);
        menu.setSelected(dest != null && dest.io == io && dest.bit == bit);
        if (pinBindings.containsMappingFor(io, bit))
          menu.setForeground(MAPPED_TEXT);
        menu.addActionListener(ev -> sources.mapCurrent(io, bit));
        popup.add(menu);
      }
      if (dest != null && dest.io == io) {
        JMenuItem menu = new JMenuItem("Remove Mapping");
        menu.addActionListener((ev) -> sources.unmapCurrent());
        popup.add(menu);
      }
      popup.show(this, e.getX(), e.getY());
    }

    void emphasize(boolean b, int bit) {
      if (emphasized == b && (b == false || emphasizeBit == bit))
        return;
      emphasized = b;
      emphasizeBit = bit;
      repaint();
    }

    void setBitIsMapped(int bit, boolean b) {
      if (bit >= io.width)
        return;
      if (bit < 0) {
        if (b && nmapped == io.width)
          return;
        if (!b && nmapped == 0)
          return;
        for (int i = 0; i < io.width; i++)
          isMapped[i] = b;
        nmapped = b ? io.width : 0;
      } else {
        if (isMapped[bit] == b)
          return;
        isMapped[bit] = b;
        nmapped += b ? +1 : -1;
      }
      repaint();
    }
  }

  private class SourcesModel extends AbstractListModel {
    ArrayList<Source> data = new ArrayList<>();
    SourcesModel() {
      sync();
    }
    void sync() {
      int n = data.size();
      if (n > 0) {
        data.clear();
        fireIntervalRemoved(this, 0, n-1);
      }
      for (Path path : pinBindings.components.keySet())
        data.add(pinBindings.sourceFor(path));
      Collections.sort(data, new NaturalOrderComparator<>(src -> src.path.relstr()));
      n = data.size();
      fireIntervalAdded(this, 0, n-1);
      pinBindings.mappings.forEach((s, d) -> {
        if (s.bit >= 0)
          expand(s.path);
        // Note: Source.equals() matches only on path and bit, so idx will find
        // matching row even if types are wrong
        int idx = data.indexOf(s);
        // replace default-type source with actual-type source from existing mapping
        data.set(idx, s);
      });
    }
    void changed(Source src) {
      int i = data.indexOf(src);
      if (i < 0)
        return;
      fireContentsChanged(this, i, i);
    }
    void replace(int idx, Source newSrc) {
      data.set(idx, newSrc);
      fireContentsChanged(this, idx, idx);
    }
    void expand(Path path) {
      int i = startIndex(path);
      if (i+1 < data.size() && data.get(i+1).path.equals(path))
        return; // already expanded
      Source src = data.get(i);
      if (src.type != BoardIO.Type.Expanded) {
        // this only happens during sync(), not from user-action expand
        replace(i, 
          new Source(src.path, src.comp, -1, BoardIO.Type.Expanded, src.width.copy()));
      }
      ArrayList<Source> bitSources = pinBindings.bitSourcesFor(path);
      data.addAll(i+1, bitSources);
      fireIntervalAdded(this, i+1, i+1+bitSources.size()-1);
    }
    void collapse(int i, int nbits) {
      data.subList(i+1, i+nbits+1 /*exclusive*/).clear();
      fireIntervalRemoved(this, i+1, i+nbits /*inclusive*/);
    }
    int startIndex(Path path) {
      for (int i = 0; i < data.size(); i++)
        if (data.get(i).path.equals(path))
          return i;
      return -1; // should never happen
    }
    @Override
    public Object getElementAt(int index) { return data.get(index); }
    @Override
    public int getSize() { return data.size(); }
  }

  private class SourceRenderer extends DefaultListCellRenderer {
    public final TypeButton typeButton = new TypeButton();

    String typeButtonText(Source src) {
      int w = src.width.size();
      String dir = src.width.in > 0 ? "in" : src.width.out > 0 ? "out" : "inout";
      if (w == 1) {
        return String.format("%s, %s, 1 bit", src.type, dir);
      } else if (src.bit < 0) {
        return String.format("%s, %s, %d bits", src.type, dir, w);
      } else {
        return String.format("type %s, %s, bit %d", src.type, dir, src.bit);
      }
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object val,
        int i, boolean sel, boolean focus) {
      boolean done = false;
      typeButton.text = "???";
      boolean indented = false;
      if (val instanceof Source) {
        Source src = (Source)val;
        Dest dst = pinBindings.mappings.get(src);
        done = dst != null;
        typeButton.src = src;
        typeButton.text = typeButtonText(src);
        if (src.bit >= 0) {
          indented = true;
          val = pinBindings.pinLabels(src.path)[src.bit];
        } else {
          val = src.path.relstr();
        }
        if (dst != null)
          val = val + " mapped to " + dst;
      }
      Component c = super.getListCellRendererComponent(list, val, i, sel, focus);
      if (c instanceof JLabel) {
        typeButton.height = c.getPreferredSize().height + 4;
        typeButton.width = indented ? 280 : 220;
        typeButton.rowHasFocus = sel;
        ((JLabel)c).setIcon(typeButton);
        ((JLabel)c).setForeground(sel ? Color.WHITE : done ? MAPPED_TEXT : Color.BLACK);
      }
      return c;
    }
  }

  private static Color TYPE_BUTTON_COLOR = new Color(0x22, 0x55, 0xcc);
  private static class TypeButton implements Icon {
    public final IdentityHashMap<Source, Rectangle> buttonBounds = new IdentityHashMap<>();
    static final int GAP = 7;
    String text;
    Source src;
    int width, height;
    boolean rowHasFocus;

    public int getIconWidth()	{ return width; }
    public int getIconHeight() { return height; }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D)g;
      Object[] oldHints = GraphicsUtil.setRenderingHintsForNiceText(g2);
      Object oldRQ = g2.getRenderingHint(RenderingHints.KEY_RENDERING);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      int tx = width - GAP - 10 - 4;
      int ty = height / 2;
      Rectangle b = buttonBounds.get(src);
      if (b == null) {
        Rectangle t = GraphicsUtil.getTextBounds(g2.getFontRenderContext(), g2.getFont(),
            text, tx, ty,
            GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_FIRST);
        b = new Rectangle(t.x-GAP, 2, t.width + GAP + 10 + 4 + GAP, height-4);
        buttonBounds.put(src, b); // cache for later, and for hitbox detection
      }
      g2.setColor(rowHasFocus ? TYPE_BUTTON_COLOR : Color.GRAY);
      g2.fillRoundRect(b.x, b.y, b.width, b.height, 10, 10);
      g2.setColor(Color.WHITE);
      g2.drawRoundRect(b.x, b.y, b.width, b.height, 10, 10);
      GraphicsUtil.drawText(g2, text, tx, ty,
          GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_FIRST);
      int xx = tx + 4;
      int yy = ty - 2;
      g2.fillPolygon(new int[] { xx, xx+5, xx+10 }, new int[] { yy, yy+6, yy }, 3);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, oldRQ);
      GraphicsUtil.restoreRenderingHints(g2, oldHints);
    }
  }

  private class SourceList extends JList<Source> implements MouseListener {

    SourcesModel model = new SourcesModel();
    Source current;
    SourceRenderer renderer;
    TypeButton typeButton = new TypeButton();

    @SuppressWarnings("unchecked")
    SourceList() {
      setModel(model);
      setCellRenderer(renderer = new SourceRenderer());
      setFont(getFont().deriveFont(Font.PLAIN));
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      current = null;
      addListSelectionListener(e -> selected(getSelectedValue()));
      addMouseListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      Point pt = e.getPoint();
      int idx = locationToIndex(pt);
      if (idx < 0)
        return;
      setSelectedIndex(idx);
      Rectangle cell = getCellBounds(idx, idx);
      if (cell == null || !cell.contains(pt))
        return;
      pt.x -= cell.x;
      pt.y -= cell.y;
      Source src = (Source)model.getElementAt(idx);
      Rectangle b = renderer.typeButton.buttonBounds.get(src);
      if (b == null)
        return;
      if (!b.contains(pt))
        return;
      pt.x += cell.x;
      pt.y += cell.y;
      doTypeSelectPopup(idx, src, cell.x + b.x, cell.y + b.y + b.height);
    }

    @Override
    public void mouseEntered(MouseEvent e) { }
    @Override
    public void mouseExited(MouseEvent e) { }
    @Override
    public void mousePressed(MouseEvent e) { }
    @Override
    public void mouseReleased(MouseEvent e) { }

    void doTypeSelectPopup(int idx, Source src, int x, int y) {
      JPopupMenu popup = new JPopupMenu("Select I/O Type");
      List<BoardIO.Type> types = pinBindings.typesFor(src.path);
      if (src.bit < 0) {
        // Show menu for single-bit or un-expanded row of multi-bit I/O component
        int w = pinBindings.widthFor(src.path).size();
        for (BoardIO.Type t : types) {
          if (w > 1 && BoardIO.OneBitTypes.contains(t))
            continue;
          JRadioButtonMenuItem menu = new JRadioButtonMenuItem(t.toString());
          menu.setSelected(src.type == t);
          menu.addActionListener(ev -> setSourceType(idx, src, t));
          popup.add(menu);
        }
        if (w > 1) {
          JRadioButtonMenuItem menu = new JRadioButtonMenuItem("Expanded");
          menu.setSelected(src.type == BoardIO.Type.Expanded);
          menu.addActionListener(ev -> setSourceType(idx, src, BoardIO.Type.Expanded));
          popup.add(menu);
        }
      } else {
        // show menu for one bit of an expanded multi-bit I/O component
        for (BoardIO.Type t : types) {
          if (!BoardIO.OneBitTypes.contains(t))
            continue;
          JRadioButtonMenuItem menu = new JRadioButtonMenuItem(t.toString());
          menu.setSelected(src.type == t);
          menu.addActionListener(ev -> setBitSourceType(idx, src, t));
          popup.add(menu);
        }
      }
      popup.show(this, x, y);
    }

    // Prereq: src.bit < 0
    void setSourceType(int idx, Source src, BoardIO.Type t) {
      if (src.type == t)
        return;
      Dest old = unmap(src);
      boolean phys = old != null && BoardIO.PhysicalTypes.contains(old.io.type);
      int syntheticValue = old == null ? 0 : old.io.syntheticValue;
      if (src.type == BoardIO.Type.Expanded) { // note: src.width > 1
        // collapse: remap if all bits were mapped to bits of a compatable dest
        int n = src.width.size();
        old = pinBindings.mappings.get(model.data.get(idx+1));
        phys = old != null && BoardIO.PhysicalTypes.contains(old.io.type);
        syntheticValue = 0;
        for (int i = 0; i < n; i++) {
          Source bitSrc = model.data.get(idx+1+i);
          Dest bitDest = unmap(bitSrc);
          if (old == null)
            continue;
          else if (bitDest == null || phys != BoardIO.PhysicalTypes.contains(bitDest.io.type))
            old = null;
          else if (phys && (bitDest.io != old.io || bitDest.bit != i))
            old = null;
          else if (!phys && 
              ((old.io.type == BoardIO.Type.Unconnected) ^ (bitDest.io.type == BoardIO.Type.Unconnected)))
            old = null; // note: zeros, ones, constant can be mixed, but not with Unconnected
          else
            syntheticValue |= (bitDest.io.syntheticValue & 1) << i;
        }
        model.collapse(idx, n);
      }
      Source newSrc = new Source(src.path, src.comp, -1, t, src.width.copy());
      if (src == current) {
        current = newSrc;
        model.replace(idx, newSrc);
        setEnablesFor(current);
      }
      if (src.type == BoardIO.Type.Expanded && old != null && !phys) {
        int n = src.width.size();
        BoardIO.Type s;
        if (old.io.type == BoardIO.Type.Unconnected)
          s = BoardIO.Type.Unconnected;
        else if (syntheticValue == 0)
          s = BoardIO.Type.AllZeros;
        else if (syntheticValue == (1 << n)-1)
          s = BoardIO.Type.AllOnes;
        else
          s = BoardIO.Type.Constant;
        pinBindings.addMapping(newSrc, BoardIO.makeSynthetic(s, n, syntheticValue), -1);
        updateStatus();
        if (current == newSrc)
          setEnablesFor(current);
      } else if (src.type == BoardIO.Type.Expanded && old != null
          && old.io.isCompatible(newSrc.width, newSrc.type)) {
        pinBindings.addMapping(newSrc, old.io, -1);
        rects.get(old.io).setBitIsMapped(-1, true);
        updateStatus();
        if (current == newSrc)
          setEnablesFor(current);
      } else if (t == BoardIO.Type.Expanded) { // note: src.width > 1
        // expand: remap all bits to bits of same dest
        model.expand(src.path);
        if (old != null && !BoardIO.PhysicalTypes.contains(old.io.type)) {
          // special case: old was a synthetic I/O device (zeros, ones, etc.)
          int val = old.io.syntheticValue;
          int n = src.width.size();
          for (int i = 0; i < n; i++) {
            BoardIO io = BoardIO.makeSynthetic(old.io.type, 1, (val >> i) & 1);
            Source bitSource = model.data.get(idx+1+i);
            pinBindings.addMapping(bitSource, io, -1);
          }
          updateStatus();
        } else if (old != null) { // regular case: physical I/O device
          // find a one-bit type compatible with the original
          Netlist.Int3 bitWidth = src.width.forSingleBit();
          BoardIO.Type bitType = null;
          for (BoardIO.Type tt : pinBindings.typesFor(src.path)) {
            if (BoardIO.OneBitTypes.contains(tt) && old.io.isCompatible(bitWidth, tt)) {
              bitType = tt;
              break;
            }
          }
          if (bitType != null) {
            int n = src.width.size();
            for (int i = 0; i < n; i++) {
              Source bitSource = model.data.get(idx+1+i);
              setBitSourceType(idx+1+i, bitSource, bitType);
              pinBindings.addMapping(bitSource, old.io, i);
              rects.get(old.io).setBitIsMapped(i, true); // not needed: already marked?
            }
            updateStatus();
          }
        }
        setSelectedValue(model.data.get(idx+1), true);
      }
    }

    // Prereq: src.bit >= 0
    void setBitSourceType(int idx, Source src, BoardIO.Type t) {
      if (src.type == t)
        return;
      // change type of one bit of expanded multi-bit I/O component
      unmap(src); 
      Source newSrc = new Source(src.path, src.comp, src.bit, t, src.width.copy());
      if (current == src) {
        current = newSrc;
        model.replace(idx, newSrc);
        setEnablesFor(current);
      } else {
        model.replace(idx, newSrc);
      }
    }

    void selected(Source src) {
      current = src;
      setEnablesFor(current);
    }
    void mapCurrent(BoardIO io, int bit) {
      // System.out.printf("Mapping %s of %s to %s\n", 
      //     (bit < 0 ? "all bits" : "bit "+bit), current, io);
      if (current == null)
          return;
      unmap(current);
      HashSet<Source> changed = pinBindings.addMapping(current, io, bit);
      for (Source src : changed)
        model.changed(src);
      updateStatus();
      setEnablesFor(current);
      int i = getSelectedIndex() + 1;
      while (i < model.data.size() && model.data.get(i).type == BoardIO.Type.Expanded)
        i++;
      if (i < model.data.size())
        setSelectedValue(model.data.get(i), true);
      // recalculate all nmapped values, easier than updating only changed ones
      recalculateAllBitCounts();
    }

    void recalculateAllBitCounts() {
      boolean[] all = new boolean[1]; // sentinel
      HashMap<BoardIO, boolean[]> maps = new HashMap<>();
      pinBindings.mappings.forEach((s, d) -> {
        boolean map[] = maps.get(d.io);
        if (map == null)
          map = new boolean[d.io.width];
        if (d.bit == -1) {
          map = all;
        } else {
          for (int i = 0; i < s.width.size(); i++)
            map[d.bit + i] = true;
        }
        maps.put(d.io, map);
      });
      rects.forEach((io, r) -> {
        boolean[] map = maps.get(io);
        if (map == null)
          r.setBitIsMapped(-1, false);
        else if (map == all)
          r.setBitIsMapped(-1, true);
        else for (int i = 0; i < map.length; i++)
          r.setBitIsMapped(i, map[i]);
      });
    }

    void mapCurrent(BoardIO.Type synthType, int val) {
      if (current == null)
          return;
      if (synthType == BoardIO.Type.Constant) {
        while (true) {
          Object sel = JOptionPane.showInputDialog(this,
              "Enter a constant integer value (signed decimal, hex, or octal):", "Define Constant",
              JOptionPane.QUESTION_MESSAGE, null, null, "0x00000000");
          if (sel == null || sel.equals(""))
            return;
          try {
            val = Integer.decode(""+sel);
            break;
          } catch (NumberFormatException ex) { }
        }
      }
      BoardIO io = BoardIO.makeSynthetic(synthType, current.width.size(), val);
      mapCurrent(io, -1);
    }

    Dest unmap(Source src) {
      Dest old = pinBindings.mappings.get(src);
      if (old == null)
        return null;
      pinBindings.mappings.remove(src);
      Rect r = rects.get(old.io);
      if (r != null && old.bit < 0)
        r.setBitIsMapped(-1, false);
      else if (r != null) {
        for (int i = 0; i < src.width.size(); i++)
          r.setBitIsMapped(old.bit+i, false);
      }
      model.changed(src);
      updateStatus();
      if (src == current)
        setEnablesFor(current);
      return old;
    }

    void unmapCurrent() {
      if (current == null)
        return;
      unmap(current);
      int i = getSelectedIndex();
      if (i+1 < model.data.size())
        setSelectedValue(model.data.get(i+1), true);
    }

    void resetAll() {
      pinBindings.mappings.clear();
      model.sync();
      if (!model.data.contains(current))
        current = model.data.size() > 0 ? model.data.get(0) : null;
      if (current != null)
        setSelectedValue(current, true);
      else
        clearSelection();
      updateStatus();
      setEnablesFor(current);
    }

  }

  static final Color hilight = new Color(200, 0, 0);
  private class Synthetic extends JToggleButton {
    BoardIO.Type type;
    int val;
    Color oldBg = null;
    Synthetic(BoardIO.Type type, int val, String label, String icon, String tip) {
      super(label, Commander.getIcon(icon));
      this.type = type;
      this.val = val;
      setToolTipText(tip);
      setEnabled(false);
      addItemListener(e -> {
        if (oldBg == null)
          oldBg = getBackground();
        if(e.getStateChange() == ItemEvent.SELECTED)
          setBackground(hilight);
        else if (e.getStateChange() == ItemEvent.DESELECTED)
          setBackground(oldBg);
      });
      addActionListener(e -> {
        if (isSelected())
          sources.mapCurrent(type, val);
        else
          sources.unmapCurrent();
      });
    }
      
    // At least on Linux with openjdk 11.0.2, JToggleButton width is
    // underestimated consistently by about 6 pixels, resulting in a truncated
    // label. Correct the estimate here.
    @Override
    public Dimension getPreferredSize() {
      Dimension d = super.getPreferredSize();
      if (d == null)
        return null;
      d.width += 10;
      return d;
    }
    @Override
    public Dimension getMaximumSize() {
      Dimension d = super.getMaximumSize();
      if (d == null)
        return null;
      d.width += 10;
      return d;
    }
    @Override
    public Dimension getMinimumSize() {
      Dimension d = super.getMinimumSize();
      if (d == null)
        return null;
      d.width += 10;
      return d;
    }

  }

  // private Source prevCurrent = null;
  void setEnablesFor(Source current) {
    // Todo: for expanded, show current mappings for all bits?
    if (current == null || current.type == BoardIO.Type.Expanded) {
      // No current selection, so hide all rects.
      for (Rect r : rects.values())
        r.setVisible(false);
      // No current selection, so disable and deselect all synthetics.
      for (Synthetic b : synthetics) {
        b.setSelected(false);
        b.setEnabled(false);
      }
    } else {
      // Mappbale row, so show previous and potential mappings.
      if (true) { // if (prevCurrent != current)
        for (Rect r : rects.values())
          r.setVisible(false);
        // Recalculate visibility of all rects and synthetics
        for (BoardIO io : pinBindings.compatibleResources(current)) {
          Rect r = rects.get(io);
          r.setVisible(true);
        }
        // ones, zeros, and constants are for source with direction "in"
        ones.setEnabled(current.width.in > 0);
        zeros.setEnabled(current.width.in > 0);
        constants.setEnabled(current.width.in > 0);
        // bitbucket is for source with direction "inout" or "out"
        bitbucket.setEnabled(current.width.inout > 0 || current.width.out > 0);
      }
      // Recalculate emphasis
      Dest dest = pinBindings.mappings.get(current);
      for (Synthetic b : synthetics)
        b.setSelected(dest != null && dest.io.type == b.type);
      for (Rect r : rects.values()) {
        r.emphasize(dest != null && r.io == dest.io, dest == null ? -1 : dest.bit);
      }
    }
    // prevCurrent = current;
    overlay.repaint();
  }

  void updateStatus() {
    String txt = pinBindings.getStatus();
    status.setText(txt);
    boolean finished = txt.startsWith("All");
    done.setEnabled(finished);
  }

	/*
	   The code below for NaturalOrderComparator comes from:
       https://github.com/paour/natorder/blob/master/NaturalOrderComparator.java
	   It has been altered for use in Logisim. The original file header is as follows:

	   NaturalOrderComparator.java -- Perform 'natural order' comparisons of strings in Java.
	   Copyright (C) 2003 by Pierre-Luc Paour <natorder@paour.com>

	   Based on the C version by Martin Pool, of which this is more or less a straight conversion.
	   Copyright (C) 2000 by Martin Pool <mbp@humbug.org.au>

	   This software is provided 'as-is', without any express or implied
	   warranty.  In no event will the authors be held liable for any damages
	   arising from the use of this software.

	   Permission is granted to anyone to use this software for any purpose,
	   including commercial applications, and to alter it and redistribute it
	   freely, subject to the following restrictions:

	   1. The origin of this software must not be misrepresented; you must not
	   claim that you wrote the original software. If you use this software
	   in a product, an acknowledgment in the product documentation would be
	   appreciated but is not required.
	   2. Altered source versions must be plainly marked as such, and must not be
	   misrepresented as being the original software.
	   3. This notice may not be removed or altered from any source distribution.
	*/

	private static class NaturalOrderComparator<T> implements Comparator<T> {
    Function<T, String> stringify;
    public NaturalOrderComparator(Function<T, String> f) {
      stringify = f;
    }
    @Override
		public int compare(T objA, T objB) {
      String a = stringify.apply(objA);
      String b = stringify.apply(objB);
			int na = a.length(), nb = b.length();
			int ia = 0, ib = 0;
			for (;; ia++, ib++) {
				char ca = charAt(a, ia, na);
				char cb = charAt(b, ib, nb);

				// skip spaces
				while (Character.isSpaceChar(ca))
					ca = charAt(a, ++ia, na);
				while (Character.isSpaceChar(cb))
					cb = charAt(b, ++ib, nb);

				// copmare numerical sequences
				if (Character.isDigit(ca) && Character.isDigit(cb))
				{
					int bias = 0;
					for (;; ia++, ib++)
					{
						ca = charAt(a, ia, na);
						cb = charAt(b, ib, nb);
						if (!Character.isDigit(ca) && !Character.isDigit(cb))
							break;
						else if (!Character.isDigit(ca))
							return -1; // a is less
						else if (!Character.isDigit(cb))
							return +1; // a is greater
						else if (bias == 0 && ca < cb)
							bias = -1; // a is less, if equal length
						else if (bias == 0 && ca > cb)
							bias = +1; // a is greater, if equal length
					}
					if (bias != 0)
						return bias;
				}

				// compare ascii
				if (ca < cb)
					return -1; // a is less
				else if (ca > cb)
					return +1; // a is greater
				else if (ca == 0 && cb == 0)
					return a.compareTo(b);
			}
		}

		static char charAt(String s, int i, int n) {
			return (i >= n ? 0 : s.charAt(i));
		}

		/*
		public static void main(String[] args) { // test program for NaturalOrderComparator
			String[] strings = new String[] {
				"1-2", "1-02", "1-20", "10-20",
					"fred", "jane",
					"pic2", "pic3", "pic4", "pic 4 else", "pic 5", "pic 5", "pic 5 something", "pic 6", "pic   7",
					"pic01", "pic02", "pic02a", "pic05",
					"pic100", "pic100a", "pic120", "pic121", "pic02000",
					"tom",
					"x2-g8", "x2-y7", "x2-y08", "x8-y8" };
			java.util.List list = java.util.Arrays.asList(strings);

			String orig = list.toString();
			System.out.println("Original: " + orig);

			Collections.shuffle(list);
			// System.out.println("Scrambled: " + list);
			Collections.sort(list, new NaturalOrderComparator());
			System.out.println("Sorted  : " + list);

			System.out.println("Correct? " + (orig.equals(list.toString())));
		}
		*/
	}

  private Rectangle scaleToFullsize(Bounds bds) {
    return new Rectangle(
        imgOffset.x + (int)Math.round(bds.x*imgScale),
        imgOffset.y + (int)Math.round(bds.y*imgScale),
        (int)Math.round(bds.width*imgScale),
        (int)Math.round(bds.height*imgScale));
  }

  private int scaleToFullsize(int dim) {
    return (int)Math.round(dim*imgScale);
  }

}
