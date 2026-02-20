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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.UIManager;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Implicant;
import com.cburch.logisim.analyze.model.OutputExpressionsEvent;
import com.cburch.logisim.analyze.model.OutputExpressionsListener;
import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.analyze.model.TruthTableEvent;
import com.cburch.logisim.analyze.model.TruthTableListener;
import com.cburch.logisim.util.GraphicsUtil;

class KarnaughMapPanel extends JPanel implements ExpressionRenderer.Colorizer {
  public static final Color ERROR_COLOR = new Color(0xa0, 0x20, 0x20);

  private class MyListener
    implements OutputExpressionsListener, TruthTableListener {

    public void rowsChanged(TruthTableEvent event) { }

    public void cellsChanged(TruthTableEvent event) {
      repaint();
    }

    public void expressionChanged(OutputExpressionsEvent event) {
      if (event.getType() == OutputExpressionsEvent.OUTPUT_MINIMAL
          && event.getVariable().equals(output)) {
        repaint();
      }
    }

    public void structureChanged(TruthTableEvent event) {
      computePreferredSize();
    }

  }

  private static final long serialVersionUID = 1L;
  private static final Font HEAD_FONT = new Font("Serif", Font.BOLD | Font.ITALIC, 14);
  private static final Font BODY_FONT = new Font("Serif", Font.PLAIN, 14);

  private static final int SEMI_TRANSPARENT = 96;
  private static final Color[] IMP_COLORS = new Color[] {
    new Color(255, 0, 0, SEMI_TRANSPARENT),
    new Color(128, 0, 255, SEMI_TRANSPARENT),
    new Color(0, 255, 255, SEMI_TRANSPARENT),
    new Color(128, 255, 0, SEMI_TRANSPARENT),
    new Color(255, 0, 192, SEMI_TRANSPARENT),
    new Color(0, 64, 255, SEMI_TRANSPARENT),
    new Color(0, 255, 64, SEMI_TRANSPARENT),
    new Color(255, 192, 0, SEMI_TRANSPARENT),
  };
  private static final Color[] EXPR_COLORS = new Color[] {
    new Color(255, 0, 0),
    new Color(128, 0, 255),
    new Color(0, 255, 255),
    new Color(128, 255, 0),
    new Color(255, 0, 192),
    new Color(0, 64, 255),
    new Color(0, 255, 64),
    new Color(208, 160, 0),
  };
  // todo: try to ignore irrelvant variables when inputs>4

  private static final int MAX_VARS = 4;
  private static final int[] ROW_VARS = { 0, 0, 1, 1, 2 };
  private static final int[] COL_VARS = { 0, 1, 1, 2, 2 };
  private static final int CELL_HORZ_SEP = 10;
  private static final int CELL_VERT_SEP = 10;
  private static final int IMP_INSET = 3;
  private static final int IMP_BORDER = 2;

  private static final int IMP_RADIUS = 6;

  private MyListener myListener = new MyListener();
  private AnalyzerModel model;
  private String output;
  private int headHeight, headWidth;
  private int cellWidth = 1, cellHeight = 1;
  private int tableWidth, tableHeight;
  private Color selColor;
  private Point highlight;
  private boolean selected;

  boolean isSelected() {
    return selected;
  }

  public KarnaughMapPanel(AnalyzerModel model) {
    this.model = model;
    model.getOutputExpressions().addOutputExpressionsListener(myListener);
    model.getTruthTable().addTruthTableListener(myListener);
    setToolTipText(" ");
    selColor = UIManager.getDefaults().getColor("List.selectionBackground");
    addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        highlight(e);
      }
    });
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
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
  }

  private Point computeOrigin() {
    Dimension sz = getSize();
    int x = (sz.width - tableWidth) / 2;
    if (x < 0)
      x = Math.max(-headWidth, sz.width - tableWidth);
    int y = (sz.height - tableHeight) / 2;
    if (y < 0)
      y = Math.max(-headHeight, sz.height - tableHeight);
    return new Point(x, y);
  }

  void computePreferredSize() {
    TruthTable table = model.getTruthTable();

    String message = null;
    if (output == null)
      message = S.get("karnaughNoOutputError");
    else if (table.getInputColumnCount() > MAX_VARS)
      message = S.get("karnaughTooManyInputsError");

    if (message != null)
      computePreferredSize(message);
    else
      computePreferredSize(table);

    setPreferredSize(new Dimension(tableWidth, tableHeight));
    revalidate();
    repaint();
  }

  private void computePreferredSize(String message) {
    FontMetrics fm = getFontMetrics(BODY_FONT);
    tableHeight = fm.getHeight();
    tableWidth = fm.stringWidth(message);
  }

  private void computePreferredSize(TruthTable table) {
    List<String> inputs = model.getInputs().bits;
    int inputCount = table.getInputColumnCount();
    int rowVars = ROW_VARS[inputCount];
    int colVars = COL_VARS[inputCount];
    String rowHeader = header(inputs, 0, rowVars);
    String colHeader = header(inputs, rowVars, rowVars+colVars);
    FontMetrics fm = getFontMetrics(BODY_FONT);
    cellHeight = fm.getAscent() + CELL_VERT_SEP;
    cellWidth = fm.stringWidth("00") + CELL_HORZ_SEP;
    int rows = 1 << rowVars;
    int cols = 1 << colVars;
    int bodyWidth = cellWidth * (cols + 1);
    int bodyHeight = cellHeight * (rows + 1);

    FontMetrics headFm = getFontMetrics(HEAD_FONT);

    int rowLabelWidth = headFm.stringWidth(rowHeader);
    if (rowVars > 1 && rowLabelWidth > Math.max(bodyWidth, 100)) {
      // use two lines for row header
      String s1 = inputs.get(0) + ",";
      String s2 = inputs.get(1);
      int w1 = headFm.stringWidth(s1);
      int w2 = headFm.stringWidth(s2) + headFm.getHeight()*cellWidth/cellHeight;
      headWidth = Math.max(w1, w2);
    } else {
      // use one line for row header
      headWidth = headFm.stringWidth(rowHeader);
    }

    int colLabelWidth = headFm.stringWidth(colHeader) + cellWidth/2;
    if (colVars > 1 && colLabelWidth > Math.max(bodyWidth, 100)) {
      // use two lines for column header
      headHeight = 2*headFm.getHeight();
      String s1 = inputs.get(rowVars+0) + ",";
      String s2 = inputs.get(rowVars+1);
      int w1 = headFm.stringWidth(s1) + cellWidth/2 - headFm.getHeight()*cellWidth/cellHeight;
      int w2 = headFm.stringWidth(s2) + cellWidth/2;
      colLabelWidth = Math.max(w1, w2);
    } else {
      // use one line for column header
      headHeight = headFm.getHeight();
    }

    tableWidth = headWidth + Math.max(bodyWidth, colLabelWidth);
    tableHeight = headHeight + bodyHeight;
  }

  private int getCol(int tableRow, int rows, int cols) {
    int ret = tableRow % cols;
    switch (ret) {
    case 2:
      return 3;
    case 3:
      return 2;
    default:
      return ret;
    }
  }

  public int getOutputColumn(MouseEvent event) {
    return model.getOutputs().bits.indexOf(output);
  }

  private int getRow(int tableRow, int rows, int cols) {
    int ret = tableRow / cols;
    switch (ret) {
    case 2:
      return 3;
    case 3:
      return 2;
    default:
      return ret;
    }
  }

  public int getRow(MouseEvent event) {
    Point p = getRowCol(event);
    if (p == null)
      return -1;
    TruthTable table = model.getTruthTable();
    int inputs = table.getInputColumnCount();
    int rows = 1 << ROW_VARS[inputs];
    int cols = 1 << COL_VARS[inputs];
    return getTableRow(p.y, p.x, rows, cols);
  }

  Point getRowCol(MouseEvent event) {
    TruthTable table = model.getTruthTable();
    int inputs = table.getInputColumnCount();
    if (inputs >= ROW_VARS.length)
      return null;
    Point o = computeOrigin();
    int x = event.getX() - o.x - headWidth - cellWidth;
    int y = event.getY() - o.y - headHeight - cellHeight;
    if (x < 0 || y < 0)
      return null;
    int row = y / cellHeight;
    int col = x / cellWidth;
    int rows = 1 << ROW_VARS[inputs];
    int cols = 1 << COL_VARS[inputs];
    if (row >= rows || col >= cols)
      return null;
    return new Point(col, row);
  }

  private int getTableRow(int row, int col, int rows, int cols) {
    return toRow(row, rows) * cols + toRow(col, cols);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    TruthTable table = model.getTruthTable();
    int row = getRow(event);
    if (row < 0)
      return null;
    int col = getOutputColumn(event);
    Entry entry = table.getOutputEntry(row, col);
    String s = entry.getErrorMessage();
    if (s == null)
      s = "";
    else
      s += "<br>";
    s += output + " = " + entry.getDescription();
    List<String> inputs = model.getInputs().bits;
    if (inputs.size() == 0)
      return "<html>"+s+"</html>";
    s += "<br>When:";
    int n = inputs.size();
    for (int i = 0; i < MAX_VARS && i < inputs.size(); i++) {
      s += "<br>&nbsp;&nbsp;&nbsp;&nbsp;" + inputs.get(i) + " = " + ((row>>(n-i-1)) & 1);
    }
    return "<html>"+s+"</html>";
  }

  void highlight(MouseEvent event) {
    Point p = getRowCol(event);
    if (p == null && highlight != null) {
      highlight = null;
      repaint();
    } else if (p == null || p.equals(highlight)) {
      return;
    } else {
      highlight = p;
      repaint();
    }
  }

  private String header(List<String> inputs, int start, int end) {
    if (start >= end)
      return "";
    StringBuilder ret = new StringBuilder(inputs.get(start));
    for (int i = start + 1; i < end; i++) {
      ret.append(", ");
      ret.append(inputs.get(i));
    }
    return ret.toString();
  }

  private String label(int row, int rows) {
    switch (rows) {
    case 2:
      return "" + row;
    case 4:
      switch (row) {
      case 0:
        return "00";
      case 1:
        return "01";
      case 2:
        return "11";
      case 3:
        return "10";
      }
    default:
      return "";
    }
  }

  void localeChanged() {
    computePreferredSize();
    repaint();
  }

  @Override
  public void paintComponent(Graphics g) {
    // super.paintComponent(g);
    paintBorder(g);
    g.setColor(selected ? selColor : getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());
    g.setColor(Color.BLACK);
    paintKmap(g);
  }

  public void paintKmap(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    Object oldHints[] = GraphicsUtil.setRenderingHintsForNiceText(g2);
    paintKmapNice(g2);
    GraphicsUtil.restoreRenderingHints(g2, oldHints);
  }

  private void paintKmapNice(Graphics2D g) {
    TruthTable table = model.getTruthTable();
    int inputCount = table.getInputColumnCount();
    Dimension sz = getSize();
    String message = null;
    if (output == null) {
      message = S.get("karnaughNoOutputError");
    } else if (inputCount > MAX_VARS) {
      message = S.get("karnaughTooManyInputsError");
    }
    if (message != null) {
      g.setFont(BODY_FONT);
      GraphicsUtil.drawCenteredText(g, message, sz.width / 2,
          sz.height / 2);
      return;
    }

    Point o = computeOrigin();
    int left = o.x;
    int top = o.y;
    int x = left;
    int y = top;
    int rowVars = ROW_VARS[inputCount];
    int colVars = COL_VARS[inputCount];
    int rows = 1 << rowVars;
    int cols = 1 << colVars;
    int bodyWidth = cellWidth * (cols + 1);
    int bodyHeight = cellHeight * (rows + 1);
    
    g.setFont(HEAD_FONT);
    FontMetrics headFm = g.getFontMetrics();

    // Color cc = g.getColor();
    // g.setColor(Color.YELLOW);
    // g.fillRect(left, top-(cellHeight-headHeight), cellWidth, cellHeight);
    // g.fillRect(left+cellWidth, top+cellHeight-(cellHeight-headHeight), cellWidth, cellHeight);
    // g.fillRect(left+2*cellWidth, top-(cellHeight-headHeight), cellWidth, cellHeight);
    // g.fillRect(left, top+2*cellHeight-(cellHeight-headHeight), cellWidth, cellHeight);
    // g.setColor(Color.RED);
    // g.drawRect(left, top, tableWidth, tableHeight);
    // g.setColor(Color.GREEN);
    // g.drawRect(left+1, top+1, tableWidth-2, headHeight-2);
    // g.drawRect(left+1, top+1, headWidth-2, tableHeight-2);
    // g.setColor(Color.MAGENTA);
    // g.fillRect(x + headWidth + cellWidth/2, y + headFm.getAscent(), 3, 3);
    // g.fillRect(x, y + headHeight + headFm.getAscent() + cellHeight/2,3,3);
    // g.setColor(cc);

    List<String> inputs = model.getInputs().bits;
    String rowHeader = header(inputs, 0, rowVars);
    String colHeader = header(inputs, rowVars, rowVars + colVars);

    int rowLabelWidth = headFm.stringWidth(rowHeader);
    if (rowVars > 1 && rowLabelWidth > Math.max(bodyWidth, 100)) {
      // use two lines for row header
      String s1 = inputs.get(0) + ",";
      String s2 = inputs.get(1);
      g.drawString(s1,
          x,
          y + headHeight + headFm.getAscent() + cellHeight/2);
      g.drawString(s2,
          x + headFm.getHeight()*cellWidth/cellHeight,
          y + headHeight + headFm.getAscent() + cellHeight/2 + headFm.getHeight());
    } else {
      // use one lines for row header
      g.drawString(rowHeader, x, y + headHeight + headFm.getAscent() + cellHeight/2);
    }

    int colLabelWidth = headFm.stringWidth(colHeader) + cellWidth/2;
    if (colVars > 1 && colLabelWidth > Math.max(bodyWidth, 100)) {
      // use two lines for column header
      String s1 = inputs.get(rowVars+0) + ",";
      String s2 = inputs.get(rowVars+1);
      g.drawString(s1,
          x + headWidth + cellWidth/2 - headFm.getHeight()*cellWidth/cellHeight,
          y + headFm.getAscent());
      g.drawString(s2,
          x + headWidth + cellWidth/2,
          y + headFm.getAscent() + headFm.getHeight());
    } else {
      // use one line for column header
      g.drawString(colHeader, x + headWidth + cellWidth/2, y + headFm.getAscent());
    }

    x += headWidth;
    y += headHeight;
    g.setFont(BODY_FONT);
    FontMetrics fm = g.getFontMetrics();
    int dy = (cellHeight + fm.getAscent()) / 2;
    for (int i = 0; i < cols; i++) {
      String label = label(i, cols);
      g.drawString(
          label,
          x + (i + 1) * cellWidth
          + (cellWidth - fm.stringWidth(label)) / 2, y + dy);
    }
    for (int i = 0; i < rows; i++) {
      String label = label(i, rows);
      g.drawString(label, x + (cellWidth - fm.stringWidth(label)) / 2, y
          + (i + 1) * cellHeight + dy);
    }

    int outputColumn = table.getOutputIndex(output);
    x += cellWidth;
    y += cellHeight;
    g.setColor(ERROR_COLOR);
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        int row = getTableRow(i, j, rows, cols);
        Entry entry = table.getOutputEntry(row, outputColumn);
        if (entry.isError()) {
          g.fillRect(x + j * cellWidth, y + i * cellHeight,
              cellWidth, cellHeight);
        }
      }
    }

    List<Implicant> implicants = model.getOutputExpressions().getMinimalImplicants(output);
    if (implicants != null) {
      Graphics2D g2 = (Graphics2D)g.create(x, y, cellWidth * cols, cellHeight * rows);
      try {
        GraphicsUtil.usePureStrokeRendering(g2);
        g2.setStroke(new BasicStroke(IMP_BORDER));
        int index = 0;
        for (Implicant imp : implicants) {
          g2.setColor(IMP_COLORS[index % IMP_COLORS.length]);
          paintImplicant(g2, imp, rows, cols);
          index++;
        }
      } finally {
        g2.dispose();
      }
    }

    g.setColor(Color.GRAY);
    if (cols > 1 || inputCount == 0)
      g.drawLine(x, y, x + bodyWidth, y);
    if (rows > 1 || inputCount == 0)
      g.drawLine(x, y, x, y + bodyHeight);
    if ((rows > 1 && cols > 1) || inputCount == 0)
      g.drawLine(x, y, x - (cellHeight+headHeight)*cellWidth/cellHeight, y - (cellHeight+headHeight));
    if (outputColumn < 0)
      return;

    g.setColor(Color.BLACK);
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        int row = getTableRow(i, j, rows, cols);
        Entry entry = table.getOutputEntry(row, outputColumn);
        String text = entry.getDescription();
        g.drawString(
            text,
            x + j * cellWidth
            + (cellWidth - fm.stringWidth(text)) / 2, y
            + i * cellHeight + dy);
      }
    }
  }

  private static void rounded(Graphics g, int x, int y, int w, int h, int dx, int dy) {
    g.fillRoundRect(x, y, w, h, dx, dy);
    Color c = g.getColor();
    g.setColor(c.darker().darker());
    g.drawRoundRect(x, y, w, h, dx, dy);
    g.setColor(c);
  }

  private void paintImplicant(Graphics g, Implicant imp, int rows, int cols) {
    int rowMax = -1;
    int rowMin = rows;
    int colMax = -1;
    int colMin = cols;
    boolean oneRowFound = false;
    int count = 0;
    boolean bold = false;
    for (Implicant sq : imp.getTerms()) {
      int tableRow = sq.getRow();
      int row = getRow(tableRow, rows, cols);
      int col = getCol(tableRow, rows, cols);
      if (row == 1)
        oneRowFound = true;
      if (row > rowMax)
        rowMax = row;
      if (row < rowMin)
        rowMin = row;
      if (col > colMax)
        colMax = col;
      if (col < colMin)
        colMin = col;
      if (highlight != null && row == highlight.y && col == highlight.x)
        bold = true;
      ++count;
    }

    if (bold) {
      Color c = g.getColor();
      g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 160));
    }

    int numCols = colMax - colMin + 1;
    int numRows = rowMax - rowMin + 1;
    int covered = numCols * numRows;
    int d = 2 * IMP_RADIUS;
    if (covered == count) {
      rounded(g, colMin * cellWidth + IMP_INSET,
          rowMin * cellHeight + IMP_INSET, numCols * cellWidth - 2
          * IMP_INSET, numRows * cellHeight - 2 * IMP_INSET, d, d);
    } else if (covered == 16) {
      if (count == 4) { // four corners
        int w = cellWidth - IMP_INSET + d;
        int h = cellHeight - IMP_INSET + d;
        int x1 = 3 * cellWidth + IMP_INSET;
        int y1 = 3 * cellHeight + IMP_INSET;
        rounded(g, -d, -d, w, h, d, d);
        rounded(g, x1, -d, w, h, d, d);
        rounded(g, -d, y1, w, h, d, d);
        rounded(g, x1, y1, w, h, d, d);
      } else if (oneRowFound) { // first and last columns
        int w = cellWidth - IMP_INSET + d;
        int h = 4 * cellHeight - 2 * IMP_INSET;
        int x1 = 3 * cellWidth + IMP_INSET;
        rounded(g, -d, IMP_INSET, w, h, d, d);
        rounded(g, x1, IMP_INSET, w, h, d, d);
      } else { // first and last rows
        int w = 4 * cellWidth - 2 * IMP_INSET;
        int h = cellHeight - IMP_INSET + d;
        int y1 = 3 * cellHeight + IMP_INSET;
        rounded(g, IMP_INSET, -d, w, h, d, d);
        rounded(g, IMP_INSET, y1, w, h, d, d);
      }
    } else if (numCols == 4) { // wrap around side
      int top = rowMin * cellHeight + IMP_INSET;
      int w = cellWidth - IMP_INSET + d;
      int h = numRows * cellHeight - 2 * IMP_INSET;
      rounded(g, -d, top, w, h, d, d);
      rounded(g, 3 * cellWidth + IMP_INSET, top, w, h, d, d);
    } else { // numRows == 4 // wrap around top and bottom
      int left = colMin * cellWidth + IMP_INSET;
      int w = numCols * cellWidth - 2 * IMP_INSET;
      int h = cellHeight - IMP_INSET + d;
      rounded(g, left, -d, w, h, d, d);
      rounded(g, left, 3 * cellHeight + IMP_INSET, w, h, d, d);
    }
  }

  public void setOutput(String value) {
    boolean recompute = (output == null || value == null) && output != value;
    output = value;
    if (recompute)
      computePreferredSize();
    else
      repaint();
  }

  private int toRow(int row, int rows) {
    if (rows == 4) {
      switch (row) {
      case 2:
        return 3;
      case 3:
        return 2;
      default:
        return row;
      }
    } else {
      return row;
    }
  }

  public Color colorFor(Expression e) {
    if (output == null)
      return null;
    int format = model.getOutputExpressions().getMinimizedFormat(output);
    TruthTable table = model.getTruthTable();
    int index = 0;
    List<Implicant> implicants = model.getOutputExpressions().getMinimalImplicants(output);
    for (Implicant imp : implicants) {
      Expression i;
      if (format == AnalyzerModel.FORMAT_SUM_OF_PRODUCTS)
        i = imp.toProduct(table);
      else
        i = imp.toSum(table);
      if (i.equals(e))
        return EXPR_COLORS[index % EXPR_COLORS.length];
      index++;
    }
    return null;
  }

}
