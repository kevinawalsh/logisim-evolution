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

package com.cburch.logisim.gui.prefs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.bfh.logisim.fpga.BoardEditor;
import com.bfh.logisim.fpga.BoardReader;
import com.bfh.logisim.settings.BoardList;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.JFileChoosers;

public class BoardsOptions extends FPGAToolchainPanel {
  private static final long serialVersionUID = 1L;

  private final JPanel gridPanel = new JPanel();
  { gridPanel.setLayout(new BoxLayout(gridPanel, BoxLayout.Y_AXIS)); }

  public BoardsOptions(SettingsFrame window) {
    super(window, "Boards");

    addExplanation("In addition to built-in FPGA boards, the following will be available.");

    JPanel addRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
    JButton addButton = new JButton("Add Board...");
    addButton.addActionListener(e -> doAddBoard());
    addRow.add(addButton);
    add(addRow);

    JScrollPane scroll = new JScrollPane(gridPanel,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scroll.setPreferredSize(new Dimension(540, 180));
    add(scroll);

    AppPreferences.addFPGAChangeWeakListener(this, this::rebuildGrid);
    rebuildGrid();
  }

  private void doAddBoard() {
    JFileChooser fc = JFileChoosers.create();
    fc.setFileFilter(new FileNameExtensionFilter("FPGA Board files", "xml"));
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fc.setAcceptAllFileFilterUsed(false);
    fc.setDialogTitle("Select Board Description File");
    if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
      return;
    File file = fc.getSelectedFile();
    AppPreferences.FPGA_BOARDLIST.add(file.getPath());
    BoardList.refresh();
  }

  private void rebuildGrid() {
    gridPanel.removeAll();
    for (String path : AppPreferences.FPGA_BOARDLIST.get())
      gridPanel.add(makeRow(path, BoardReader.validateFileAndGetName(path)));
    gridPanel.add(Box.createVerticalGlue());
    gridPanel.revalidate();
    gridPanel.repaint();
  }

  private JPanel makeRow(String path, String name) {
    JPanel row = new JPanel() {
      @Override public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
      }
    };
    row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
    row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));

    JButton remove = new JButton("remove");
    remove.setMargin(new Insets(2, 6, 2, 6));
    remove.setAlignmentY(Component.CENTER_ALIGNMENT);
    remove.addActionListener(e -> {
      AppPreferences.FPGA_BOARDLIST.remove(path);
      BoardList.refresh();
    });

    boolean invalid = (name == null);

    JButton edit = new JButton("edit");
    edit.setMargin(new Insets(2, 4, 2, 4));
    edit.setAlignmentY(Component.CENTER_ALIGNMENT);
    if (invalid)
      edit.setEnabled(false);
    else
      edit.addActionListener(e -> BoardEditor.openWithPath(path));

    JLabel nameLabel = new JLabel(invalid ? "invalid board xml" : name);
    if (invalid)
      nameLabel.setForeground(Color.RED);
    nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel pathLabel = new JLabel(path);
    Font f = pathLabel.getFont();
    pathLabel.setFont(f.deriveFont(f.getSize2D() - 1f));
    pathLabel.setForeground(Color.GRAY);
    pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel text = new JPanel();
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    text.setOpaque(false);
    text.setAlignmentY(Component.CENTER_ALIGNMENT);
    text.add(nameLabel);
    text.add(pathLabel);

    row.add(remove);
    row.add(Box.createHorizontalStrut(8));
    row.add(edit);
    row.add(Box.createHorizontalStrut(8));
    row.add(text);
    row.add(Box.createHorizontalGlue());

    return row;
  }
}
