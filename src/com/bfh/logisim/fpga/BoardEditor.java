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

package com.bfh.logisim.fpga;
import static com.bfh.logisim.fpga.Strings.S;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import com.bfh.logisim.settings.BoardList;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.generic.ComboBox;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.io.DipSwitch;
import com.cburch.logisim.std.io.PortIO;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.JFileChoosers;

public class BoardEditor extends JFrame {

  private JButton save;
  private JTextField name;
  private BoardPanel image;
  private Chipset fpga;
  public LinkedList<BoardIO> ioComponents = new LinkedList<>();
  public BoardIO selectedIO = null;
  private IOSidebarPanel sidebar;

  public BoardEditor() {
    super(S.get("FPGABoardEditor"));
    LFrame.attachIcon(this, "resources/logisim/img/fpga-icon-%d.png");

    setResizable(false);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setLayout(new BorderLayout());

    image = new BoardPanel(this);
    sidebar = new IOSidebarPanel();

    JPanel center = new JPanel(new BorderLayout());
    center.add(image, BorderLayout.CENTER);
    center.add(sidebar, BorderLayout.EAST);
    add(center, BorderLayout.CENTER);

    JPanel buttons = new JPanel();
    buttons.setLayout(new BoxLayout(buttons, BoxLayout.PAGE_AXIS));
    JPanel buttonsA = new JPanel();
    JPanel buttonsB = new JPanel();

    buttonsA.add(new JLabel("Board Name:"));

    name = new JTextField(20);
    name.setEnabled(true);
    buttonsA.add(name);

    JButton chipset = new JButton("Configure FPGA Chipset");
    chipset.addActionListener(e -> doChipsetDialog());
    buttonsA.add(chipset);

    JButton pic = new JButton("Change Picture");
    pic.addActionListener(e -> doChangeImage());
    buttonsB.add(pic);

    JButton builtin = new JButton("Built-in FPGA Boards");
    builtin.addActionListener(e -> doBuiltin());
    buttonsB.add(builtin);

    JButton load = new JButton("Load Board");
    load.addActionListener(e -> doLoad());
    buttonsB.add(load);

    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(e -> { setVisible(false); clear(); });
    buttonsB.add(cancel);

    save = new JButton("Save Board");
    save.addActionListener(e -> doSave());
    save.setEnabled(false);
    buttonsB.add(save);

    buttons.add(buttonsA);
    buttons.add(buttonsB);
    add(buttons, BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(null);

    setVisible(true);
  }

  public void doModal(JDialog dlg, int x, int y) {
    dlg.pack();
    Point p = getLocationOnScreen();
    dlg.setLocation(p.x+x-dlg.getWidth()/2, p.y+y-10);
    dlg.setModal(true);
    dlg.setResizable(false);
    dlg.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    dlg.setAlwaysOnTop(true);
    dlg.setVisible(true);
  }

  private void doSave() {
    if (ioComponents.isEmpty()) {
      Errors.title("Warning").warn("No I/O resources have been specified.\n"
          + "Before saving, you may want to draw rectangles on the image\n"
          + "to specify I/O resources for this FPGA board.");
    }
    File file = getSaveFile();
    if (file == null)
      return;
    String filename = file.getName();
    if (filename.toLowerCase().endsWith(".xml"))
      filename = filename.substring(0, filename.length() - 4);
    name.setText(filename);
    Board board = new Board(filename, null, null, fpga, image.getImage());
    board.addComponents(ioComponents);
    if (!BoardWriter.write(file, board))
      return;
  }

  private void doBuiltin() {
    ComboBox<String> boardsList = new ComboBox<>();
    BoardList.refresh();
    for (String boardname : BoardList.getAllNames())
      boardsList.addItem(boardname);
    boardsList.setSelectedItem(BoardList.getSelectedName());
    JDialogOk dlg = new JDialogOk("Select Existing FPGA Board") {
      public void okClicked() {
        String name = boardsList.getSelectedValue();
        AppPreferences.FPGA_SELECTED_BOARD.set(name);
        setBoard(BoardReader.read(BoardList.getSelectedPath()));
      }
    };
    JPanel p = new JPanel();
    p.add(new JLabel("Select existing FPGA board:"));
    p.add(boardsList);
    dlg.getContentPane().add(p, BorderLayout.CENTER);
    dlg.pack();
    dlg.setVisible(true);
  }

  private void doLoad() {
    JFileChooser fc = JFileChoosers.create();
    fc.setDialogTitle("Choose XML board description");
    fc.setFileFilter(Loader.XML_FILTER);
    fc.setAcceptAllFileFilterUsed(false);
    int retval = fc.showOpenDialog(null);
    if (retval != JFileChooser.APPROVE_OPTION)
      return;
    String path = fc.getSelectedFile().getPath();
    setBoard(BoardReader.read(path));
  }

  private void setBoard(Board board) {
    if (board == null)
      return;
    name.setText(board.name);
    fpga = board.fpga;
    ioComponents.clear();
    ioComponents.addAll(board);
    image.setImage(board.image);
    sidebar.showIdle();
    setEnables();
  }

  private void setEnables() {
    save.setEnabled(image.getImage() != null && fpga != null);
  }

  public void clear() {
    if (isVisible())
      setVisible(false);
    image.clear();
    ioComponents.clear();
    fpga = null;
    name.setText("");
    selectedIO = null;
    sidebar.showIdle();
    setEnables();
  }

  private File getSaveFile() {
    JFileChooser fc = JFileChoosers.create();
    fc.setDialogTitle("Choose file to save XML board description:");
    fc.setFileFilter(Loader.XML_FILTER);
    fc.setAcceptAllFileFilterUsed(false);
    if (name.getText().isEmpty())
      fc.setSelectedFile(new File("Unnamed_FPGA_board.xml"));
    else
      fc.setSelectedFile(new File(name.getText() + ".xml"));
    int retval = fc.showSaveDialog(null);
    if (retval != JFileChooser.APPROVE_OPTION)
      return null;
    return fc.getSelectedFile();
  }

  public void reactivate() {
    if (!isVisible()) {
      clear();
      setVisible(true);
    }
    toFront();
  }

  private static void add(JComponent dlg, GridBagConstraints c,
      String caption, JComponent input) {
    dlg.add(new JLabel(caption + " "), c);
    c.gridx++;
    dlg.add(input, c);
    c.gridx--;
    c.gridy++;
  }

  private void doChipsetDialog() {
    final JDialog dlg = new JDialog(this, "FPGA Chipset Properties");
    GridBagConstraints c = new GridBagConstraints();
    dlg.setLayout(new GridBagLayout());
    c.gridx = 0;
    c.gridy = 0;
    c.fill = GridBagConstraints.HORIZONTAL;

    final boolean ok[] = new boolean[] { false };
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener((e) -> {
      ok[0] = false;
      dlg.setVisible(false);
    });
    JButton done = new JButton("OK");
    done.addActionListener((e) -> {
      ok[0] = true;
      dlg.setVisible(false);
    });

    JTextField rate = new JTextField(10);
    JComboBox<String> hz = new JComboBox<>(new String[] { "Hz", "kHz", "MHz" });
    JTextField clkLoc = new JTextField();
    JComboBox<PullBehavior> clkPull = new JComboBox<>(PullBehavior.OPTIONS);
    JComboBox<IoStandard> clkStandard = new JComboBox<>(IoStandard.OPTIONS);
    JComboBox<PullBehavior> unusedPull = new JComboBox<>(PullBehavior.OPTIONS);
    JTextField jtagPos = new JTextField("1");
    JComboBox<String> vendor = new JComboBox<>(Chipset.VENDORS);
    JTextField family = new JTextField();
    JTextField part = new JTextField();
    JTextField pkg = new JTextField();
    JTextField speed = new JTextField();
    JTextField flashName = new JTextField();
    JTextField flashPos = new JTextField("2");
    JCheckBox usbTmc = new JCheckBox("USBTMC Download");

    if (fpga == null) {
      rate.setText("50");
      hz.setSelectedIndex(2);
      clkPull.setSelectedIndex(0);
      clkStandard.setSelectedIndex(0);
      unusedPull.setSelectedIndex(0);
      vendor.setSelectedIndex(0);
      usbTmc.setSelected(false);
    } else {
      rate.setText(fpga.Speed.split(" ")[0]);
      hz.setSelectedItem(fpga.Speed.split(" ")[1]);
      clkLoc.setText(fpga.ClockPinLocation);
      clkPull.setSelectedItem(fpga.ClockPullBehavior);
      clkStandard.setSelectedItem(fpga.ClockIOStandard);
      unusedPull.setSelectedItem(fpga.UnusedPinsBehavior);
      jtagPos.setText(""+fpga.JTAGPos);
      vendor.setSelectedItem(fpga.VendorName);
      family.setText(fpga.Technology);
      part.setText(fpga.Part);
      pkg.setText(fpga.Package);
      speed.setText(fpga.SpeedGrade);
      flashName.setText(fpga.FlashName);
      flashPos.setText(""+fpga.FlashPos);
      usbTmc.setSelected(fpga.USBTMCDownload);
    }

    JPanel freqPanel = new JPanel();
    freqPanel.setLayout(new GridBagLayout());
    freqPanel.add(rate, c);
    c.gridx++;
    freqPanel.add(hz, c);

    JPanel clockPanel = new JPanel();
    clockPanel.setLayout(new GridBagLayout());
    c.gridx = 0;
    c.gridy = 0;
    add(clockPanel, c, "Clock frequency:", freqPanel);
    add(clockPanel, c, "Clock pin FPGA location:", clkLoc);
    add(clockPanel, c, "Clock pin pull behavior:", clkPull);
    add(clockPanel, c, "Clock pin I/O standard:", clkStandard);
    add(clockPanel, c, "Unused FPGA pin behavior:", unusedPull);
    add(clockPanel, c, "FPGA position in JTAG chain:", jtagPos);

    JPanel devPanel = new JPanel();
    devPanel.setLayout(new GridBagLayout());
    c.gridx = 0;
    c.gridy = 0;
    add(devPanel, c, "FPGA vendor:", vendor);
    add(devPanel, c, "FPGA family:", family);
    add(devPanel, c, "FPGA part:", part);
    add(devPanel, c, "FPGA package:", pkg);
    add(devPanel, c, "FPGA speed grade:", speed);
    add(devPanel, c, "Flash name:", flashName);
    add(devPanel, c, "Flash position in JTAG chain:", flashPos);

    c.gridx = 0;
    c.gridy = 0;
    c.fill = GridBagConstraints.NORTH;
    dlg.add(clockPanel, c);

    c.gridx = 1;
    c.gridy = 0;
    c.fill = GridBagConstraints.NORTH;
    dlg.add(devPanel, c);

    c.gridx = 0;
    c.gridy = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    dlg.add(usbTmc, c);

    c.gridx = 0;
    c.gridy = 2;
    c.fill = GridBagConstraints.HORIZONTAL;
    dlg.add(cancel, c);

    c.gridx = 1;
    c.gridy = 2;
    c.fill = GridBagConstraints.HORIZONTAL;
    dlg.add(done, c);

    dlg.pack();
    dlg.setLocation(Projects.getCenteredLoc(dlg.getWidth(), dlg.getHeight()));
    dlg.setModal(true);
    dlg.setResizable(false);
    dlg.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    dlg.setAlwaysOnTop(false);

    for (;;) {
      dlg.setVisible(true);
      if (!ok[0])
        break;
      long freq = getFrequency(rate.getText(), hz.getSelectedItem().toString());
      if (freq == 0) {
        Errors.title("Error").show("Please specify a clock frequency.");
      } else if (freq == -1) {
        Errors.title("Error").show("Clock frequency must be a multiple of 1 Hz.");
      } else if (freq < 0) {
        Errors.title("Error").show("Invalid clock frequency.");
      } else if (clkLoc.getText().isEmpty()) {
        Errors.title("Error").show("Please specify clock pin FPGA location.");
      } else if (family.getText().isEmpty()) {
        Errors.title("Error").show("Please specify FPGA family.");
      } else if (part.getText().isEmpty()) {
        Errors.title("Error").show("Please specify FPGA part.");
      } else if (pkg.getText().isEmpty()) {
        Errors.title("Error").show("Please specify FPGA package.");
      } else if (speed.getText().isEmpty()) {
        Errors.title("Error").show("Please specify FPGA speed grade.");
      } else {
        HashMap<String, String> params = new HashMap<>();
        params.put("ClockInformation/Frequency", ""+freq);
        params.put("ClockInformation/FPGApin", clkLoc.getText());
        params.put("ClockInformation/PullBehavior", ""+clkPull.getSelectedItem());
        params.put("ClockInformation/IOStandard", ""+clkStandard.getSelectedItem());
        params.put("FPGAInformation/Family", family.getText() );
        params.put("FPGAInformation/Part", part.getText());
        params.put("FPGAInformation/Package", pkg.getText());
        params.put("FPGAInformation/Speedgrade", speed.getText());
        params.put("FPGAInformation/Vendor", ""+vendor.getSelectedItem());
        params.put("FPGAInformation/USBTMC", ""+usbTmc.isSelected());
        params.put("FPGAInformation/JTAGPos", jtagPos.getText());
        params.put("FPGAInformation/FlashPos", flashPos.getText());
        params.put("FPGAInformation/FlashName", flashName.getText());
        params.put("UnusedPins/PullBehavior", ""+unusedPull.getSelectedItem());
        try {
          fpga = new Chipset(params);
          break;
        } catch (Exception e) {
          Errors.title("Error").show("Invalid chipset parameters: " + e.getMessage(), e);
        }
      }
    }
    dlg.dispose();
    setEnables();
  }

  private long getFrequency(String str, String speed) {
    long num = 0;
    long multiplier = 1;
    boolean dec_mult = false;

    if (speed.equals("kHz"))
      multiplier = 1000;
    if (speed.equals("MHz"))
      multiplier = 1000000;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c >= '0' && c <= '9') {
        num *= 10;
        num += (c - '0');
        if (dec_mult) {
          multiplier /= 10;
          if (multiplier == 0)
            return -1;
        }
      } else if (!dec_mult && c == '.') {
        dec_mult = true;
      } else {
        return -2;
      }
    }
    return num * multiplier;
  }

  private static final FileFilter PNG_JPG_FILTER =
      Loader.makeFileFilter(S.unlocalized("Images (*.png, *.jpg, ...)"),
          ".png", ".jpg", ".jpeg", ".jpe", ".jfi", ".jfif", ".jfi");

  public void doChangeImage() {
    JFileChooser fc = JFileChoosers.create();
    fc.setDialogTitle("Select FPGA Board Picture");
    fc.setFileFilter(PNG_JPG_FILTER);
    fc.setAcceptAllFileFilterUsed(false);
    int retval = fc.showOpenDialog(null);
    if (retval == JFileChooser.APPROVE_OPTION) {
      File file = fc.getSelectedFile();
      try {
        image.setImage(file);
      } catch (IOException ex) {
        Errors.title("Error").show("Error loading image", ex);
      }
    }
    setEnables();
  }

  public BoardIO findBoardIO(int x, int y) {
    for (BoardIO io : ioComponents)
      if (io.rect.contains(x, y))
        return io;
    return null;
  }

  public void doRectSelectDialog(Bounds rect, int x, int y) {
    for (BoardIO io : ioComponents) {
      if (io.rect.overlaps(rect)) {
        Errors.title("Error").show("Please ensure rectangles do not overlap.");
        return;
      }
    }
    sidebar.showNewRect(rect);
  }

  public void doBoardIODialog(BoardIO io) {
    sidebar.showEditIO(io);
  }

  public void clearSelection() {
    sidebar.showIdle();
  }

  public void moveSelectedIO(int newX, int newY) {
    if (selectedIO == null) return;
    sidebar.moveEditingIO(newX, newY);
  }

  // Sidebar panel shown to the right of the board image.
  // Shows instructions when idle; shows I/O component properties when a rect
  // is drawn or an existing component is clicked. Changes apply immediately.
  private class IOSidebarPanel extends JPanel {

    private static final String IDLE = "idle", EDIT = "edit";
    private final CardLayout cards = new CardLayout();

    private BoardIO editingIO = null;
    private boolean updating = false;

    // Type selector and variable-width controls
    private JComboBox<BoardIO.Type> typeCombo;
    private JPanel sizeOrientPanel;
    private JComboBox<Integer> widthCombo;
    private JComboBox<String> orientCombo;

    // Pin location fields (rebuilt when type/width changes)
    private JPanel pinInner;
    private JTextField[] pinFields = new JTextField[0];

    // Property fields
    private final JTextField labelField = new JTextField();
    private final JTextField xField = new JTextField(4);
    private final JTextField yField = new JTextField(4);
    private final JTextField wField = new JTextField(4);
    private final JTextField hField = new JTextField(4);
    private final JComboBox<IoStandard> standardCombo = new JComboBox<>(IoStandard.OPTIONS);
    private final JComboBox<DriveStrength> strengthCombo = new JComboBox<>(DriveStrength.OPTIONS);
    private final JComboBox<PullBehavior> pullCombo = new JComboBox<>(PullBehavior.OPTIONS);
    private final JComboBox<PinActivity> activityCombo = new JComboBox<>(PinActivity.OPTIONS);

    // Rows shown/hidden based on type
    private JPanel strengthRow;
    private JPanel pullRow;
    private JPanel activityRow;

    IOSidebarPanel() {
      setLayout(cards);
      setPreferredSize(new Dimension(300, Board.IMG_HEIGHT));
      setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY));

      // Idle card
      JPanel idleCard = new JPanel(new BorderLayout());
      JLabel hint = new JLabel(
          "<html><center><i>Draw rectangles on the<br>"
          + "picture to define<br>I/O components.</i></center></html>");
      hint.setHorizontalAlignment(JLabel.CENTER);
      idleCard.add(hint, BorderLayout.CENTER);
      add(idleCard, IDLE);

      add(buildEditCard(), EDIT);
      cards.show(this, IDLE);
    }

    private JPanel buildEditCard() {
      JPanel editCard = new JPanel(new BorderLayout(0, 4));
      editCard.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

      // --- Top: type selector and optional size/orientation ---
      JPanel topPanel = new JPanel();
      topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.PAGE_AXIS));
      // topPanel.setBorder(BorderFactory.createTitledBorder("I/O Component Type"));

      typeCombo = new JComboBox<>(BoardIO.PhysicalTypes.toArray(new BoardIO.Type[0]));
      typeCombo.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object val,
            int idx, boolean sel, boolean focus) {
          super.getListCellRendererComponent(list, val, idx, sel, focus);
          if (val instanceof BoardIO.Type)
            setText(((BoardIO.Type) val).getDescription());
          return this;
        }
      });
      typeCombo.addActionListener(e -> { if (!updating) onTypeChanged(); });
      topPanel.add(makeRow("Type:", typeCombo));

      sizeOrientPanel = new JPanel();
      sizeOrientPanel.setLayout(new BoxLayout(sizeOrientPanel, BoxLayout.PAGE_AXIS));
      widthCombo = new JComboBox<>();
      widthCombo.addActionListener(e -> { if (!updating) onWidthChanged(); });
      orientCombo = new JComboBox<>();
      for (PinOrdering po : PinOrdering.OPTIONS)
        orientCombo.addItem(po.desc);
      orientCombo.addActionListener(e -> { if (!updating) applyCurrentValues(); });
      sizeOrientPanel.add(makeRow("Size:", widthCombo));
      sizeOrientPanel.add(makeRow("Orientation:", orientCombo));
      sizeOrientPanel.setVisible(false);
      topPanel.add(sizeOrientPanel);

      editCard.add(topPanel, BorderLayout.NORTH);

      // --- Center: scrollable pin locations and properties ---
      JPanel propsPanel = new JPanel();
      propsPanel.setLayout(new BoxLayout(propsPanel, BoxLayout.PAGE_AXIS));

      // Pin locations section (rebuilt dynamically)
      JPanel pinSection = new JPanel(new BorderLayout());
      //pinSection.setBorder(BorderFactory.createTitledBorder("FPGA Pin Locations"));
      pinInner = new JPanel();
      pinInner.setLayout(new BoxLayout(pinInner, BoxLayout.PAGE_AXIS));
      pinSection.add(pinInner, BorderLayout.CENTER);
      propsPanel.add(pinSection);

      // Properties section
      JPanel propSection = new JPanel(new BorderLayout());
      // propSection.setBorder(BorderFactory.createTitledBorder("Properties"));
      JPanel propInner = new JPanel();
      propInner.setLayout(new BoxLayout(propInner, BoxLayout.PAGE_AXIS));

      FocusAdapter applyOnFocus = new FocusAdapter() {
        @Override public void focusLost(FocusEvent e) {
          if (!updating) applyCurrentValues();
        }
      };
      labelField.addFocusListener(applyOnFocus);
      xField.addFocusListener(applyOnFocus);
      yField.addFocusListener(applyOnFocus);
      wField.addFocusListener(applyOnFocus);
      hField.addFocusListener(applyOnFocus);
      standardCombo.addActionListener(e -> { if (!updating) applyCurrentValues(); });
      strengthCombo.addActionListener(e -> { if (!updating) applyCurrentValues(); });
      pullCombo.addActionListener(e -> { if (!updating) applyCurrentValues(); });
      activityCombo.addActionListener(e -> { if (!updating) applyCurrentValues(); });

      propInner.add(makeRow("Label:", labelField));

      JPanel geoGrid = new JPanel(new GridLayout(2, 4, 2, 2));
      geoGrid.add(new JLabel(" X:"));
      geoGrid.add(xField);
      geoGrid.add(new JLabel(" Y:"));
      geoGrid.add(yField);
      geoGrid.add(new JLabel(" W:"));
      geoGrid.add(wField);
      geoGrid.add(new JLabel(" H:"));
      geoGrid.add(hField);
      propInner.add(geoGrid);

      propInner.add(makeRow("I/O Std:", standardCombo));

      strengthRow = makeRow("Drive Str:", strengthCombo);
      propInner.add(strengthRow);

      pullRow = makeRow("Pull:", pullCombo);
      propInner.add(pullRow);

      activityRow = makeRow("Activity:", activityCombo);
      propInner.add(activityRow);

      propSection.add(propInner, BorderLayout.CENTER);
      propsPanel.add(propSection);

      JScrollPane scroll = new JScrollPane(propsPanel,
          JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
          JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      editCard.add(scroll, BorderLayout.CENTER);

      // --- Bottom: delete button ---
      JButton deleteBtn = new JButton("Delete Component");
      deleteBtn.addActionListener(e -> deleteCurrentIO());
      editCard.add(deleteBtn, BorderLayout.SOUTH);

      return editCard;
    }

    private JPanel makeRow(String label, JComponent field) {
      JPanel row = new JPanel(new BorderLayout(4, 0));
      row.add(new JLabel(label), BorderLayout.WEST);
      row.add(field, BorderLayout.CENTER);
      return row;
    }

    void showIdle() {
      editingIO = null;
      selectedIO = null;
      cards.show(this, IDLE);
      image.repaint();
    }

    void showNewRect(Bounds rect) {
      editingIO = null;
      selectedIO = null;

      updating = true;
      try {
        typeCombo.setSelectedIndex(0);
        BoardIO.Type type = (BoardIO.Type) typeCombo.getSelectedItem();
        boolean needsSize = needsSize(type);
        sizeOrientPanel.setVisible(needsSize);
        if (needsSize) {
          populateWidthCombo(type, type.defaultWidth());
          populateOrientCombo(type, null, rect);
        }
        labelField.setText("");
        xField.setText("" + rect.x);
        yField.setText("" + rect.y);
        wField.setText("" + rect.width);
        hField.setText("" + rect.height);
        standardCombo.setSelectedItem(IoStandard.DEFAULT);
        strengthCombo.setSelectedItem(DriveStrength.DEFAULT);
        pullCombo.setSelectedItem(PullBehavior.FLOAT);
        activityCombo.setSelectedItem(PinActivity.ACTIVE_HIGH);
        rebuildPinPanel(type, type.defaultWidth(), null);
        updateConditionalRows(type);
      } finally {
        updating = false;
      }

      cards.show(this, EDIT);
      applyCurrentValues(); // creates the initial IO immediately
    }

    void moveEditingIO(int newX, int newY) {
      if (editingIO == null) return;
      int x = Math.max(0, Math.min(newX, Board.IMG_WIDTH  - editingIO.rect.width  - 1));
      int y = Math.max(0, Math.min(newY, Board.IMG_HEIGHT - editingIO.rect.height - 1));
      updating = true;
      xField.setText("" + x);
      yField.setText("" + y);
      updating = false;
      applyCurrentValues();
    }

    void showEditIO(BoardIO io) {
      editingIO = io;
      selectedIO = io;

      updating = true;
      try {
        typeCombo.setSelectedItem(io.type);
        boolean needsSize = needsSize(io.type);
        sizeOrientPanel.setVisible(needsSize);
        if (needsSize) {
          populateWidthCombo(io.type, io.width);
          populateOrientCombo(io.type, io.orientation, io.rect);
        }
        labelField.setText(io.label != null ? io.label : "");
        xField.setText("" + io.rect.x);
        yField.setText("" + io.rect.y);
        wField.setText("" + io.rect.width);
        hField.setText("" + io.rect.height);
        standardCombo.setSelectedItem(io.standard);
        strengthCombo.setSelectedItem(io.strength);
        pullCombo.setSelectedItem(io.pull);
        activityCombo.setSelectedItem(io.activity);
        rebuildPinPanel(io.type, io.width, io);
        updateConditionalRows(io.type);
      } finally {
        updating = false;
      }

      cards.show(this, EDIT);
      image.repaint();
    }

    private boolean needsSize(BoardIO.Type type) {
      return type == BoardIO.Type.DIPSwitch || type == BoardIO.Type.Ribbon;
    }

    private void populateWidthCombo(BoardIO.Type type, int selected) {
      widthCombo.removeAllItems();
      int min = type == BoardIO.Type.DIPSwitch ? DipSwitch.MIN_SWITCH : PortIO.MIN_IO;
      int max = type == BoardIO.Type.DIPSwitch ? DipSwitch.MAX_SWITCH : PortIO.MAX_IO;
      for (int i = min; i <= max; i++)
        widthCombo.addItem(i);
      widthCombo.setSelectedItem(selected);
    }

    private void populateOrientCombo(BoardIO.Type type, PinOrdering orient, Bounds rect) {
      orientCombo.removeAllItems();
      for (PinOrdering po : PinOrdering.OPTIONS)
        orientCombo.addItem(po.desc);
      if (orient != null) {
        orientCombo.setSelectedItem(orient.desc);
      } else {
        boolean wide = rect.width >= rect.height;
        String def = type == BoardIO.Type.DIPSwitch
            ? (wide ? PinOrdering.ORDER_1_LR.desc : PinOrdering.ORDER_1_TB.desc)
            : (wide ? PinOrdering.ORDER_2_BTLR.desc : PinOrdering.ORDER_2_LRTB.desc);
        orientCombo.setSelectedItem(def);
      }
    }

    private void updateConditionalRows(BoardIO.Type type) {
      strengthRow.setVisible(BoardIO.OutputTypes.contains(type));
      pullRow.setVisible(BoardIO.InputTypes.contains(type) && type != BoardIO.Type.Pin);
      activityRow.setVisible(!BoardIO.InOutTypes.contains(type));
    }

    private void rebuildPinPanel(BoardIO.Type type, int width, BoardIO io) {
      pinInner.removeAll();
      String[] labels = type.pinLabels(width);
      pinFields = new JTextField[width];
      FocusAdapter applyOnFocus = new FocusAdapter() {
        @Override public void focusLost(FocusEvent e) {
          if (!updating) applyCurrentValues();
        }
      };
      for (int i = 0; i < width; i++) {
        pinFields[i] = new JTextField();
        if (io != null && io.pins != null && i < io.pins.length && io.pins[i] != null)
          pinFields[i].setText(io.pins[i]);
        pinFields[i].addFocusListener(applyOnFocus);
        pinInner.add(makeRow(labels[i] + ":", pinFields[i]));
      }
      pinInner.revalidate();
      pinInner.repaint();
    }

    private void onTypeChanged() {
      BoardIO.Type type = (BoardIO.Type) typeCombo.getSelectedItem();
      if (type == null || editingIO == null) return;
      Bounds rect = editingIO.rect;

      updating = true;
      try {
        boolean needsSize = needsSize(type);
        sizeOrientPanel.setVisible(needsSize);
        if (needsSize) {
          populateWidthCombo(type, type.defaultWidth());
          populateOrientCombo(type, null, rect);
        }
        String pinVals[] = new String[pinFields.length];
        for (int i = 0; i < pinFields.length; i++)
          pinVals[i] = pinFields[i].getText();
        rebuildPinPanel(type, type.defaultWidth(), null);
        for (int i = 0; i < pinFields.length && i < pinVals.length; i++)
          pinFields[i].setText(pinVals[i]);
        updateConditionalRows(type);
      } finally {
        updating = false;
      }

      applyCurrentValues();
    }

    private void onWidthChanged() {
      BoardIO.Type type = (BoardIO.Type) typeCombo.getSelectedItem();
      if (type == null || widthCombo.getSelectedItem() == null) return;
      int width = (Integer) widthCombo.getSelectedItem();

      updating = true;
      try {
        rebuildPinPanel(type, width, editingIO);
      } finally {
        updating = false;
      }

      applyCurrentValues();
    }

    private void applyCurrentValues() {
      BoardIO.Type type = (BoardIO.Type) typeCombo.getSelectedItem();
      if (type == null) return;

      // Parse geometry; fall back to current IO's rect if invalid
      int x, y, w, h;
      try {
        x = Integer.parseInt(xField.getText().trim());
        y = Integer.parseInt(yField.getText().trim());
        w = Integer.parseInt(wField.getText().trim());
        h = Integer.parseInt(hField.getText().trim());
        if (x < 0 || y < 0 || w <= 0 || h <= 0
            || x + w >= Board.IMG_WIDTH || y + h >= Board.IMG_HEIGHT)
          throw new NumberFormatException("out of range");
      } catch (NumberFormatException e) {
        if (editingIO == null) return;
        x = editingIO.rect.x; y = editingIO.rect.y;
        w = editingIO.rect.width; h = editingIO.rect.height;
      }
      Bounds rect = Bounds.create(x, y, w, h);

      // Width and orientation (only for DIPSwitch / Ribbon)
      boolean needsSize = needsSize(type);
      int width = needsSize && widthCombo.getSelectedItem() != null
          ? (Integer) widthCombo.getSelectedItem() : type.defaultWidth();
      PinOrdering orient = needsSize
          ? PinOrdering.get((String) orientCombo.getSelectedItem()) : null;

      // Collect pin locations (may be empty strings while user is still typing)
      String[] pins = new String[width];
      for (int i = 0; i < width; i++)
        pins[i] = (i < pinFields.length && pinFields[i] != null)
            ? pinFields[i].getText().trim() : "";

      // Label (null if empty)
      String lbl = labelField.getText().trim();
      if (lbl.isEmpty()) lbl = null;

      // Other properties, conditioned on type
      IoStandard std = (IoStandard) standardCombo.getSelectedItem();
      DriveStrength strength = BoardIO.OutputTypes.contains(type)
          ? (DriveStrength) strengthCombo.getSelectedItem() : DriveStrength.UNKNOWN;
      PullBehavior pull = (BoardIO.InputTypes.contains(type) && type != BoardIO.Type.Pin)
          ? (PullBehavior) pullCombo.getSelectedItem() : PullBehavior.UNKNOWN;
      PinActivity activity;
      if (type == BoardIO.Type.Pin)
        activity = PinActivity.ACTIVE_HIGH;
      else if (!BoardIO.InOutTypes.contains(type))
        activity = (PinActivity) activityCombo.getSelectedItem();
      else
        activity = PinActivity.UNKNOWN;

      // Build the new IO object and update the list
      BoardIO newIO = new BoardIO(type, width, lbl, rect,
          std, pull, activity, strength, orient, pins);

      if (editingIO != null) {
        int idx = ioComponents.indexOf(editingIO);
        if (idx >= 0)
          ioComponents.set(idx, newIO);
        else
          ioComponents.add(newIO);
      } else {
        ioComponents.add(newIO);
      }
      editingIO = newIO;
      selectedIO = newIO;

      image.repaint();
    }

    private void deleteCurrentIO() {
      if (editingIO != null)
        ioComponents.remove(editingIO);
      editingIO = null;
      selectedIO = null;
      cards.show(this, IDLE);
      image.repaint();
    }
  }
}
