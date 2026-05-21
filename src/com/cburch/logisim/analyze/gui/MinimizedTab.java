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

package com.cburch.logisim.analyze.gui;
import static com.cburch.logisim.analyze.model.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.IOException;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.OutputExpressions;
import com.cburch.logisim.analyze.model.OutputExpressionsEvent;
import com.cburch.logisim.analyze.model.OutputExpressionsListener;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.LogisimMenuItem;
import com.cburch.logisim.gui.menu.PrintHandler;
import com.cburch.logisim.util.MouseListenerUtil;

class MinimizedTab extends AnalyzerTab {

  @SuppressWarnings("rawtypes")
  private static class FormatModel extends AbstractListModel
    implements ComboBoxModel {
    static int getFormatIndex(int choice) {
      switch (choice) {
      case AnalyzerModel.FORMAT_PRODUCT_OF_SUMS:
        return 1;
      default:
        return 0;
      }
    }

    private static final long serialVersionUID = 1L;

    private String[] choices;
    private int selected;

    private FormatModel() {
      selected = 0;
      choices = new String[2];
      localeChanged();
    }

    public Object getElementAt(int index) {
      return choices[index];
    }

    int getSelectedFormat() {
      switch (selected) {
      case 1:
        return AnalyzerModel.FORMAT_PRODUCT_OF_SUMS;
      default:
        return AnalyzerModel.FORMAT_SUM_OF_PRODUCTS;
      }
    }

    public Object getSelectedItem() {
      return choices[selected];
    }

    public int getSize() {
      return choices.length;
    }

    void localeChanged() {
      choices[0] = S.get("minimizedSumOfProducts");
      choices[1] = S.get("minimizedProductOfSums");
      fireContentsChanged(this, 0, choices.length);
    }

    public void setSelectedItem(Object value) {
      for (int i = 0; i < choices.length; i++) {
        if (choices[i].equals(value)) {
          selected = i;
        }
      }
    }
  }

  private class MyListener
    implements OutputExpressionsListener, ActionListener, ItemListener {
    public void actionPerformed(ActionEvent event) {
      String output = getCurrentVariable();
      int format = outputExprs.getMinimizedFormat(output);
      formatChoice.setSelectedIndex(FormatModel.getFormatIndex(format));
      outputExprs.setExpression(output,
          outputExprs.getMinimalExpression(output));
    }

    public void expressionChanged(OutputExpressionsEvent event) {
      String output = getCurrentVariable();
      if (event.getType() == OutputExpressionsEvent.OUTPUT_MINIMAL
          && event.getVariable().equals(output)) {
        minimizedExpr.setExpression(
            output, outputExprs.getMinimalExpression(output));
        MinimizedTab.this.validate();
      }
      setAsExpr.setEnabled(output != null
          && !outputExprs.isExpressionMinimal(output));
      int format = outputExprs.getMinimizedFormat(output);
      formatChoice.setSelectedIndex(FormatModel.getFormatIndex(format));
    }

    public void itemStateChanged(ItemEvent event) {
      if (event.getSource() == formatChoice) {
        String output = getCurrentVariable();
        FormatModel model = (FormatModel) formatChoice.getModel();
        outputExprs.setMinimizedFormat(output,
            model.getSelectedFormat());
      } else {
        updateTab();
      }
    }
  }

  private class ExpressionPanel extends JPanel {
    String name;
    Expression expr;
    ExpressionRenderer prettyView = new ExpressionRenderer();
    Color selColor;
    boolean selected;
    int lastWidth = -1;
    Rectangle exprBounds;
    static final int BW = 1, MARGIN = 9;
    static final int VERTICAL_PAD = 20;

    boolean isSelected() {
      return selected;
    }

    ExpressionPanel() {
      selColor = UIManager.getDefaults().getColor("List.selectionBackground");

      setFocusable(true);
      prettyView.setBorder(BorderFactory.createLineBorder(Color.BLACK, BW));
      // prettyView.setFocusable(true);
      // setBackground(Color.WHITE);
      // prettyView.setBackground(Color.WHITE);
      prettyView.setCentered(true);
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (lastWidth != getWidth())
            update();
        }
      });
      FocusListener f = new FocusListener() {
        public void focusGained(FocusEvent e) {
          if (e.isTemporary()) return;
          selected = true;
          repaint();
        }
        public void focusLost(FocusEvent e) {
          if (e.isTemporary()) return;
          selected = false;
          repaint();
        }
      };
      addFocusListener(f);
      prettyView.addFocusListener(f);
      addMouseListener(MouseListenerUtil.clickHandler(e -> {
        if (exprBounds != null && exprBounds.contains(e.getPoint()))
          requestFocusInWindow();
        else
          MinimizedTab.this.requestFocusInWindow();
      }));
    }
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (name != null && expr != null) {
        Graphics2D g2 = (Graphics2D)g;
        AffineTransform xform = g2.getTransform();
        g2.translate(prettyView.getX(), prettyView.getY());
        prettyView.setBackground(selected ? selColor : Color.WHITE);
        prettyView.paintComponent(g2);
        g2.setTransform(xform);
      }
    }
    void update() {
      Dimension d = getSize();
      lastWidth = d.width;

      prettyView.setExpressionWidth(d.width - 2*BW);
      if (name != null && expr != null)
        prettyView.setExpression(name, expr);
      else
        prettyView.clear();

      // make expr take up only what it needs, plus space for border,
      // limited only by our own size
      prettyView.setSize(d);
      Rectangle r = prettyView.getExpressionBounds();
      r.grow(BW, BW+MARGIN);
      Rectangle area = new Rectangle(0, 0, d.width, d.height);
      exprBounds = r.intersection(area);
      exprBounds.grow(-2, -2);
      prettyView.setBounds(exprBounds);

      // make our preferred size tall enough, but narrower than necessary to
      // allow window to shrink
      d.width = 100;
      d.height = r.height;
      setPreferredSize(d);

      invalidate();
      repaint();
    }
    void setExpression(String name, Expression expr) {
      this.name = name;
      this.expr = expr;
      update();
    }

  }

  private static final long serialVersionUID = 1L;

  private OutputSelector selector;
  private KarnaughMapPanel karnaughMap;
  private JLabel formatLabel = new JLabel();
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private JComboBox formatChoice = new JComboBox<>(new FormatModel());
  private ExpressionPanel minimizedExpr = new ExpressionPanel();
  private ExpressionTab.NotationSelector notation;
  private JButton setAsExpr = new JButton();

  private MyListener myListener = new MyListener();
  private AnalyzerModel model;
  private OutputExpressions outputExprs;

  public MinimizedTab(AnalyzerModel model, LogisimMenuBar menubar) {
    this.model = model;
    this.outputExprs = model.getOutputExpressions();
    outputExprs.addOutputExpressionsListener(myListener);

    notation = new ExpressionTab.NotationSelector(
        minimizedExpr.prettyView, BoxLayout.Y_AXIS) {
      @Override
      void updated() {
        minimizedExpr.update();
      }
    };

    selector = new OutputSelector(model);
    selector.addItemListener(myListener);
    karnaughMap = new KarnaughMapPanel(model);
    setAsExpr.addActionListener(myListener);
    formatChoice.addItemListener(myListener);
    minimizedExpr.prettyView.setColorizer(karnaughMap);

    JPanel main = new JPanel();

    GridBagLayout gb = new GridBagLayout();
    GridBagConstraints gc = new GridBagConstraints();
    main.setLayout(gb);

    gc.gridx = 0;
    gc.gridy = GridBagConstraints.RELATIVE;
    gc.weightx = 1.0;
    gc.weighty = 0.0;

    JPanel outPanel = selector.createPanel();
    JPanel fmtPanel = new JPanel();
    fmtPanel.setLayout(new BoxLayout(fmtPanel, BoxLayout.Y_AXIS));
    formatLabel.setAlignmentX(0.0f);
    formatChoice.setAlignmentX(0.0f);
    fmtPanel.add(formatLabel);
    fmtPanel.add(formatChoice);
    outPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    fmtPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    notation.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JPanel selectors = new JPanel();
    selectors.setLayout(new BoxLayout(selectors, BoxLayout.X_AXIS));
    selectors.add(outPanel);
    selectors.add(fmtPanel);
    selectors.add(notation);

    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.CENTER;
    gb.setConstraints(selectors, gc);
    main.add(selectors);

    gc.fill = GridBagConstraints.NONE;
    gc.ipadx = gc.ipady = 10;
    gc.anchor = GridBagConstraints.CENTER;
    gb.setConstraints(karnaughMap, gc);
    main.add(karnaughMap);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.ipadx = gc.ipady = 10;
    gb.setConstraints(minimizedExpr, gc);
    main.add(minimizedExpr);
    
    JPanel button = new JPanel(new GridLayout(1, 1));
    button.add(setAsExpr);

    gc.weightx = 0.0;
    gc.ipadx = gc.ipady = 0;
    gc.fill = GridBagConstraints.NONE;
    gb.setConstraints(button, gc);
    main.add(button);

    JScrollPane pane = new JScrollPane(main,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    setLayout(new BorderLayout());
    add(pane, BorderLayout.CENTER);

    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent event) {
        int width = pane.getViewport().getWidth();
        main.setSize(new Dimension(width, main.getHeight()));
      }
      public void componentShown(ComponentEvent e) {
        karnaughMap.computePreferredSize();
      }
    });

    String selected = selector.getSelectedOutput();
    setAsExpr.setEnabled(selected != null
        && !outputExprs.isExpressionMinimal(selected));

    TransferHandler ccpTab, ccpKmap, ccpExpr;
    setTransferHandler(ccpTab = new MinimizedTransferHandler());
    karnaughMap.setTransferHandler(ccpKmap = new KmapTransferHandler());
    minimizedExpr.setTransferHandler(ccpExpr = new ExpressionTransferHandler());

    InputMap inputMap1 = getInputMap();
    InputMap inputMap2 = karnaughMap.getInputMap();
    InputMap inputMap3 = minimizedExpr.getInputMap();
    for (LogisimMenuItem item: LogisimMenuBar.EDIT_ITEMS) {
      KeyStroke accel = menubar.getAccelerator(item);
      inputMap1.put(accel, item);
      inputMap2.put(accel, item);
      inputMap3.put(accel, item);
    }

    getActionMap().put(LogisimMenuBar.COPY, ccpTab.getCopyAction());
    karnaughMap.getActionMap().put(LogisimMenuBar.COPY, ccpKmap.getCopyAction());
    minimizedExpr.getActionMap().put(LogisimMenuBar.COPY, ccpExpr.getCopyAction());

    MouseMotionAdapter m = new MouseMotionAdapter() {
      public void mouseDragged(MouseEvent e) {
        JComponent c = (JComponent)e.getSource();
        TransferHandler handler = c.getTransferHandler();
        handler.exportAsDrag(c, e, TransferHandler.COPY);
      }
    };
    karnaughMap.addMouseMotionListener(m);
    minimizedExpr.addMouseMotionListener(m);

    pane.addMouseListener(MouseListenerUtil.clickHandler(e -> requestFocusInWindow()));

    FocusListener f = new FocusListener() {
      public void focusGained(FocusEvent e) {
        if (e.isTemporary()) return;
        editHandler.computeEnabled();
      }
      public void focusLost(FocusEvent e) {
        if (e.isTemporary()) return;
        editHandler.computeEnabled();
      }
    };
    addFocusListener(f);
    minimizedExpr.addFocusListener(f);
    karnaughMap.addFocusListener(f);
  }

  private String getCurrentVariable() {
    return selector.getSelectedOutput();
  }

  @Override
  void localeChanged() {
    selector.localeChanged();
    karnaughMap.localeChanged();
    notation.localeChanged();
    setAsExpr.setText(S.get("minimizedSetButton"));
    formatLabel.setText(S.get("minimizedFormat"));
    ((FormatModel) formatChoice.getModel()).localeChanged();
  }

  @Override
  void updateTab() {
    final String output = getCurrentVariable();
    if (output != null && model.getTruthTable().getRowCount() > 4096) {
      (new Analyzer.PleaseWait<Void>("Calculating Expression", this) {
        @Override
        public Void doInBackground() throws Exception {
          model.getOutputExpressions().getExpression(output);
          return null;
        }
      }).get();
    }
    karnaughMap.setOutput(output);
    int format = outputExprs.getMinimizedFormat(output);
    formatChoice.setSelectedIndex(FormatModel.getFormatIndex(format));
    minimizedExpr.setExpression(
        output, outputExprs.getMinimalExpression(output));
    setAsExpr.setEnabled(output != null
        && !outputExprs.isExpressionMinimal(output));
  }

  @Override
  EditHandler getEditHandler() {
    return editHandler;
  }

  EditHandler editHandler = new EditHandler() {
    @Override
    public void computeEnabled() {
      boolean viewing = minimizedExpr.isFocusOwner()
          || karnaughMap.isFocusOwner();
      setEnabled(LogisimMenuBar.CUT, false);
      setEnabled(LogisimMenuBar.COPY, viewing);
      setEnabled(LogisimMenuBar.PASTE, false);
      setEnabled(LogisimMenuBar.DELETE, false);
      setEnabled(LogisimMenuBar.DUPLICATE, false);
      setEnabled(LogisimMenuBar.SELECT_ALL, false);
      setEnabled(LogisimMenuBar.SEARCH, false);
      setEnabled(LogisimMenuBar.RAISE, false);
      setEnabled(LogisimMenuBar.LOWER, false);
      setEnabled(LogisimMenuBar.RAISE_TOP, false);
      setEnabled(LogisimMenuBar.LOWER_BOTTOM, false);
      setEnabled(LogisimMenuBar.ADD_CONTROL, false);
      setEnabled(LogisimMenuBar.REMOVE_CONTROL, false);
    }
    @Override
    public void actionPerformed(ActionEvent e) {
      Object action = e.getSource();
      if (minimizedExpr.isSelected())
        minimizedExpr.getActionMap().get(action).actionPerformed(e);
      else if (karnaughMap.isSelected())
        karnaughMap.getActionMap().get(action).actionPerformed(e);
    }
  };

  private class MinimizedTransferHandler extends TransferHandler {
    @Override
    protected Transferable createTransferable(JComponent c) {
      if (minimizedExpr.isFocusOwner()) {
        return new KmapSelection(karnaughMap);
      } else if (karnaughMap.isFocusOwner()) {
        return new ExpressionSelection(minimizedExpr.prettyView);
      } else {
        return null;
      }
    }
    @Override
    public int getSourceActions(JComponent c) { return COPY; }
    @Override
    public boolean importData(TransferHandler.TransferSupport info) { return false; }
    @Override
    protected void exportDone(JComponent c, Transferable tdata, int action) { }
    @Override
    public boolean canImport(TransferHandler.TransferSupport support) { return false; }
  }


  private class KmapTransferHandler extends MinimizedTransferHandler {
    @Override
    protected Transferable createTransferable(JComponent c) {
      return new KmapSelection(karnaughMap);
    }
  }

  private class ExpressionTransferHandler extends MinimizedTransferHandler {
    @Override
    protected Transferable createTransferable(JComponent c) {
      return new ExpressionSelection(minimizedExpr.prettyView);
    }
  }

  static class ImageSelection implements Transferable {
    private Image image;

    public ImageSelection() { }

    public void setImage(Image image) {
      this.image = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] { DataFlavor.imageFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (!DataFlavor.imageFlavor.equals(flavor)) {
        throw new UnsupportedFlavorException(flavor);
      }
      return image;
    }
  }

  static class KmapSelection extends ImageSelection {
    public KmapSelection(KarnaughMapPanel kmap) {
      int w = kmap.getWidth();
      int h = kmap.getHeight();
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      try {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        kmap.paintKmap(g);
      } finally {
        g.dispose();
      }
      setImage(img);
    }
  }

  static class ExpressionSelection extends ImageSelection {
    public ExpressionSelection(ExpressionRenderer prettyView) {
      int w = prettyView.getWidth();
      int h = prettyView.getHeight();
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      try {
        prettyView.setBackground(Color.WHITE);
        prettyView.paintComponent(g);
      } finally {
        g.dispose();
      }
      setImage(img);
    }
  }

  @Override
  PrintHandler getPrintHandler() {
    return printHandler;
  }

  PrintHandler printHandler = new PrintHandler() {
    @Override
    public Dimension getExportImageSize() {
      int kWidth = karnaughMap.getWidth();
      int kHeight = karnaughMap.getHeight();
      int eWidth = minimizedExpr.prettyView.getWidth();
      int eHeight = minimizedExpr.prettyView.getHeight();
      int width = Math.max(kWidth, eWidth);
      int height = kHeight + 30 + eHeight;
      return new Dimension(width, height);
    }

    @Override
    public void paintExportImage(BufferedImage img, Graphics2D g) {
      int width = img.getWidth();
      int height = img.getHeight();
      g.setClip(0, 0, width, height);

      AffineTransform xform = g.getTransform();
      g.translate((width - karnaughMap.getWidth())/2, 0);
      g.setColor(Color.BLACK);
      karnaughMap.paintKmap(g);
      g.setTransform(xform);

      ExpressionRenderer prettyView = new ExpressionRenderer();
      prettyView.setCentered(true);
      prettyView.setBackground(Color.WHITE);
      prettyView.setExpressionWidth(minimizedExpr.prettyView.getWidth());
      prettyView.setExpression(minimizedExpr.name, minimizedExpr.expr);
      prettyView.setSize(new Dimension(
            prettyView.getExpressionWidth(),
            prettyView.getExpressionHeight()));

      g.translate((width - prettyView.getWidth())/2, karnaughMap.getHeight() + 30);
      g.setColor(Color.BLACK);
      prettyView.paintComponent(g);
    }

    @Override
    public int print(Graphics2D g, PageFormat pf, int pageNum, double w, double h) {
      if (pageNum != 0)
        return Printable.NO_SUCH_PAGE;

      AffineTransform xform = g.getTransform();
      g.translate((w - karnaughMap.getWidth())/2, 0);
      g.setColor(Color.BLACK);
      karnaughMap.paintKmap(g);
      g.setTransform(xform);

      ExpressionRenderer prettyView = new ExpressionRenderer();
      prettyView.setCentered(true);
      prettyView.setBackground(Color.WHITE);
      prettyView.setExpressionWidth((int)w);
      prettyView.setExpression(minimizedExpr.name, minimizedExpr.expr);
      prettyView.setSize(new Dimension(
            prettyView.getExpressionWidth(),
            prettyView.getExpressionHeight()));

      g.translate((w - prettyView.getWidth())/2, karnaughMap.getHeight() + 30);
      prettyView.paintComponent(g);
      return Printable.PAGE_EXISTS;
    }
  };
  
}
