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

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.bfh.logisim.settings.BoardList;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.generic.ComboBox;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.main.ExportImage;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.JDialogOk;

public class BoardEditor extends JFrame {

	private JButton save, load, builtin;
	private JTextField name;
	private BoardPanel image;
  private Chipset fpga;
	public LinkedList<BoardIO> ioComponents = new LinkedList<>();

	public BoardEditor() {
    super(Strings.get("FPGABoardEditor"));
    LFrame.attachIcon(this, "resources/logisim/img/fpga-icon-%d.png");

		setResizable(false);
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setLayout(new BorderLayout());

		// Set an empty board picture
		image = new BoardPanel(this);
    add(image, BorderLayout.CENTER);

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

		builtin = new JButton("Built-in FPGA Boards");
		builtin.addActionListener(e -> doBuiltin());
		buttonsB.add(builtin);

		load = new JButton("Load Board");
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
    if (name.getText().isEmpty()) {
      Errors.title("Error").show("Please specify a name for the board before saving.");
      return;
    }
    if (ioComponents.isEmpty()) {
      Errors.title("Warning").warn("No I/O resources have been specified.\n"
          + "Before saving, you may want to draw rectangles on the image\n"
          + "to specify I/O resources for this FPGA board.");
    }
    Board board = new Board(name.getText(), null, null, fpga, image.getImage());
    board.addComponents(ioComponents);
    String dir = getSaveDirectory();
    if (dir == null)
      return;
    String filename = dir + board.name + ".xml";
    if (!BoardWriter.write(filename, board))
      return;
    // setVisible(false);
    // clear();
  }



  private void doBuiltin() {
    ComboBox<String> boardsList = new ComboBox<>();
    for (String boardname : BoardList.names())
      boardsList.addItem(boardname);
    boardsList.setSelectedItem(BoardList.getSelectedBoard());
    JDialogOk dlg = new JDialogOk("Select Built-in FPGA Board") {
      public void okClicked() {
        String name = boardsList.getSelectedValue();
        AppPreferences.FPGA_SELECTED_BOARD.set(name);
        setBoard(BoardReader.read(BoardList.getSelectedPath()));
      }
    };
    JPanel p = new JPanel();
    p.add(new JLabel("Select a built-in FPGA board:"));
    p.add(boardsList);
    dlg.getContentPane().add(p, BorderLayout.CENTER);
    dlg.pack();
    dlg.setVisible(true);
  }

  private void doLoad() {
    JFileChooser fc = new JFileChooser();
    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
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

    setEnables();
	}

  private void setEnables() {
    save.setEnabled(image.getImage() != null && fpga != null);
    // save.setEnabled(!name.getText().isEmpty()
    //     && fpga != null
    //     && image.getImage() != null
    //     && !ioComponents.isEmpty());
  }

	public void clear() {
		if (isVisible())
			setVisible(false);
		image.clear();
		ioComponents.clear();
		fpga = null;
		name.setText("");
    setEnables();
	}

	private String getSaveDirectory() {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setDialogTitle("Choose directory to save XML board description:");
		int retval = fc.showSaveDialog(null);
		if (retval != JFileChooser.APPROVE_OPTION)
      return null;
    String dir = fc.getSelectedFile().getPath();
    dir = dir.replaceAll("\\\\","/");
    if (!dir.endsWith("/"))
      dir += "/";
    return dir;
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
      // sensible default values
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

  public void doChangeImage() {
    JFileChooser fc = new JFileChooser();
    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fc.setDialogTitle("Choose FPGA board picture to use");
    fc.setFileFilter(ExportImage.PNG_FILTER);
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
		final JDialog dlg = new JDialog(this, "Add I/O Resource");
    dlg.getContentPane().setLayout(new BoxLayout(dlg.getContentPane(), BoxLayout.PAGE_AXIS));
    JLabel label = new JLabel("Select a type of FPGA I/O resource to add:");
    label.setAlignmentX(0.5f);
    dlg.getContentPane().add(label);
		for (BoardIO.Type type : BoardIO.PhysicalTypes) {
      JButton button = new JButton(type.getDescription());
      button.setAlignmentX(0.5f);
			button.addActionListener(e -> {
        dlg.setVisible(false);
        BoardIO io = BoardIO.makeUserDefined(type, rect, this);
        if (io != null) {
          ioComponents.add(io);
          image.repaint();
        }
      });
			dlg.getContentPane().add(button);
		}
    doModal(dlg, rect.x + rect.width/2, rect.y);
	}

  public void doBoardIODialog(BoardIO io) {
    BoardIO redo = BoardIO.redoUserDefined(io, this);
    if (redo == null) {
      ioComponents.remove(io);
      image.repaint();
    } else if (redo != io) {
      ioComponents.remove(io);
      ioComponents.add(redo);
      image.repaint();
    }
  }

}
