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

import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.bfh.logisim.settings.Settings;
import com.bfh.logisim.download.FPGADownload;

public class FPGASettingsDialog implements ActionListener {

	private JDialog panel;
	private Settings settings;
	private JTextField alteraPath, /*xilinxPath,*/ /*latticePath,*/ /*apioPath,*/ /*openFPGAloaderPath,*/ gowinShPath, gowinProgPath /*, workPath*/;
	private JRadioButton altera32Choice, altera64Choice, svfChoice, rbfChoice;

	public FPGASettingsDialog(JFrame parentFrame, Settings settings) {
		this.settings = settings;

		panel = new JDialog(parentFrame, ModalityType.APPLICATION_MODAL);
		panel.setTitle("FPGA Compiler and Toolchain Settings");
		panel.setResizable(false);
		panel.setAlwaysOnTop(false);
		panel.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		String apath = settings.GetAlteraToolPath();
		if (apath == null) apath = "";
		// String xpath = settings.GetXilinxToolPath();
		// if (xpath == null) xpath = "";
		// String lpath = settings.GetLatticeToolPath();
		// if (lpath == null) lpath = "";
		// String ppath = settings.GetApioToolPath();
		// if (ppath == null) ppath = "";
		// String fpath = settings.GetOpenFPGALoaderPath();
		// if (fpath == null) fpath = "";
		// String opath = settings.GetOpenFPGALoaderPath();
		// if (opath == null) opath = "";
		String gpath = settings.GetGowinShPath();
		if (gpath == null) gpath = "";
		String gprpath = settings.GetGowinProgPath();
		if (gprpath == null) gprpath = "";
		// String wpath = settings.GetStaticWorkspacePath();
		// if (wpath == null) wpath = "";

		// JLabel globalSection = new JLabel("Global Settings");
		JLabel alteraSection = new JLabel("Altera Settings");
		// JLabel xilinxSection = new JLabel("Xilinx Settings");
		// JLabel latticeSection = new JLabel("Lattice Settings");
		// JLabel apioSection = new JLabel("Apio Settings");
		JLabel gowinSection = new JLabel("Gowin Settings");
		// JLabel openFPGAloaderSection = new JLabel("openFPGAloader Settings");
		Font font = alteraSection.getFont();
		Font boldFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
		// globalSection.setFont(boldFont);
		alteraSection.setFont(boldFont);
		// xilinxSection.setFont(boldFont);
		// latticeSection.setFont(boldFont);
		gowinSection.setFont(boldFont);
		// apioSection.setFont(boldFont);
		// openFPGAloaderSection.setFont(boldFont);

		// JLabel workLabel = new JLabel("Temporary directory for compilation:");
		// workPath = new JTextField(wpath);
		// workPath.setPreferredSize(new Dimension(450, 10));
		// workPath.setToolTipText("leave blank to use default");
		// JButton workPicker = new JButton("Choose");
		// workPicker.setActionCommand("workPicker");
		// workPicker.addActionListener(this);

		JLabel alteraLabel = new JLabel("Altera tools path (trusted URL, script, or install directory):");
		alteraPath = new JTextField(apath);
		alteraPath.setPreferredSize(new Dimension(450, 10));
		alteraPath.setToolTipText("A *trusted* URL, a custom script, or install directory of "+FPGADownload.ALTERA_PROGRAMS[0]+".");
		JButton alteraPicker = new JButton("Choose");
		alteraPicker.setActionCommand("alteraPicker");
		alteraPicker.addActionListener(this);

		altera32Choice = new JRadioButton("32-bit (faster, less memory, small projects)");
		altera64Choice = new JRadioButton("64-bit (slower, more memory, large projects)");
		ButtonGroup group = new ButtonGroup();
		group.add(altera32Choice);
		group.add(altera64Choice);
		if (settings.GetAltera64Bit())
			altera64Choice.setSelected(true);
		else
			altera32Choice.setSelected(true);

		// JLabel xilinxLabel = new JLabel("Xilinx tools path (script or install directory):");
		// xilinxPath = new JTextField(xpath);
		// xilinxPath.setPreferredSize(new Dimension(450, 10));
		// xilinxPath.setToolTipText("A custom script, or install directory of "+FPGADownload.XILINX_PROGRAMS[0]+".");
		// JButton xilinxPicker = new JButton("Choose");
		// xilinxPicker.setActionCommand("xilinxPicker");
		// xilinxPicker.addActionListener(this);

		JLabel gowinLabel = new JLabel("Gowin IDE path (path to gw_sh)");
		gowinShPath = new JTextField(gpath);
		gowinShPath.setPreferredSize(new Dimension(400, 10));
		JButton gowinPicker = new JButton("Choose");
		gowinPicker.setActionCommand("gowinPicker");
		gowinPicker.addActionListener(this);
		JLabel gowinProgLabel = new JLabel("Gowin Programmer path (path to programmer_cli)");
		gowinProgPath = new JTextField(gprpath);
		gowinProgPath.setPreferredSize(new Dimension(400, 10));
		JButton gowinProgPicker = new JButton("Choose");
		gowinProgPicker.setActionCommand("gowinProgPicker");
		gowinProgPicker.addActionListener(this);

		// JLabel latticeLabel = new JLabel("Lattice tools path (install directory):");
		// latticePath = new JTextField(lpath);
		// latticePath.setPreferredSize(new Dimension(450, 10));
		// latticePath.setToolTipText("Install directory of "+FPGADownload.LATTICE_PROGRAMS[0]+" or similar tools.");
		// JButton latticePicker = new JButton("Choose");
		// latticePicker.setActionCommand("latticePicker");
		// latticePicker.addActionListener(this);

		// JLabel apioLabel = new JLabel("Apio tools path (python virtualenv directory):");
		// apioPath = new JTextField(ppath);
		// apioPath.setPreferredSize(new Dimension(450, 10));
		// apioPath.setToolTipText("Python virtualenv directory, path to apio executable, or leave blank to use system python");
		// JButton apioPicker = new JButton("Choose");
		// apioPicker.setActionCommand("apioPicker");
		// apioPicker.addActionListener(this);

		// JLabel openFPGAloaderLabel = new JLabel("openFPGAloader tools path (path to executable, or blank to use system):");
		// openFPGAloaderPath = new JTextField(fpath); // BUG? was ppath
		// openFPGAloaderPath.setPreferredSize(new Dimension(450, 10));
		// openFPGAloaderPath.setToolTipText("Path to openFPGAloader executable, or leave blank to use system");
		// JButton openFPGAloaderPicker = new JButton("Choose");
		// openFPGAloaderPicker.setActionCommand("openFPGAloaderPicker");
		// openFPGAloaderPicker.addActionListener(this);

		svfChoice = new JRadioButton("svf (Serial Vector Format)");
		rbfChoice = new JRadioButton("rbf (Raw Binary File)");
		ButtonGroup group2 = new ButtonGroup();
		group2.add(svfChoice);
		group2.add(rbfChoice);
		if (settings.GetUseRBF())
			rbfChoice.setSelected(true);
		else
			svfChoice.setSelected(true);

		JButton ok = new JButton("OK");
		ok.setActionCommand("OK");
		ok.addActionListener(this);
		JButton cancel = new JButton("Cancel");
		cancel.setActionCommand("Cancel");
		cancel.addActionListener(this);

		panel.getRootPane().setDefaultButton(ok);
    panel.getRootPane().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) { panel.setVisible(false); }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		int y = -1;

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(15, 10, 5, 0);
		// panel.add(globalSection, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		// panel.add(workLabel, c);
		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		// panel.add(workPath, c);
		// c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 5, 0, 0);
		// panel.add(workPicker, c);

		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(10, 10, 5, 0);
		panel.add(alteraSection, c);

		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		panel.add(alteraLabel, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		panel.add(alteraPath, c);
		c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 5, 0, 0);
		panel.add(alteraPicker, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		panel.add(altera32Choice, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		panel.add(altera64Choice, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		panel.add(svfChoice, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		panel.add(rbfChoice, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(10, 10, 5, 0);
		// panel.add(xilinxSection, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		// panel.add(xilinxLabel, c);
		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		// panel.add(xilinxPath, c);
		// c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 5, 0, 0);
		// panel.add(xilinxPicker, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(10, 10, 5, 0);
		// panel.add(latticeSection, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		// panel.add(latticeLabel, c);
		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		// panel.add(latticePath, c);
		// c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 5, 0, 0);
		// panel.add(latticePicker, c);

		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(10, 0, 10, 0);
		panel.add(gowinSection, c);

		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 10, 2, 0);
		panel.add(gowinLabel, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 10, 5, 0);
		panel.add(gowinShPath, c);
		c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 10, 5, 0);
		panel.add(gowinPicker, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 10, 2, 0);
		panel.add(gowinProgLabel, c);
		c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 10, 5, 0);
		panel.add(gowinProgPath, c);
		c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 10, 5, 0);
		panel.add(gowinProgPicker, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(10, 10, 5, 0);
		// panel.add(apioSection, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		// panel.add(apioLabel, c);
		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		// panel.add(apioPath, c);
		// c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 5, 0, 0);
		// panel.add(apioPicker, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(10, 10, 5, 0);
		// panel.add(openFPGAloaderSection, c);

		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 2; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(5, 20, 0, 0);
		// panel.add(openFPGAloaderLabel, c);
		// c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.fill = GridBagConstraints.BOTH; c.insets = new Insets(2, 20, 0, 0);
		// panel.add(openFPGAloaderPath, c);
		// c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 5, 0, 0);
		// panel.add(openFPGAloaderPicker, c);

		c.gridx = 0; c.gridy = ++y; c.gridwidth = 1; c.anchor = GridBagConstraints.EAST; c.insets = new Insets(20, 50, 20, 20);
		panel.add(cancel, c);
		c.gridx = 1; c.gridy = y; c.gridwidth = 1; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(20, 0, 20, 20);
		panel.add(ok, c);

		panel.pack();
		panel.setMinimumSize(new Dimension(600, 600));
		panel.setLocationRelativeTo(parentFrame);
	}

	public void doDialog() {
		panel.setVisible(true);
	}

	public void toFront() {
		panel.toFront();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		/* if (e.getActionCommand().equals("workPicker")) {
			pick(null, workPath.getText(), false);
		} else */ if (e.getActionCommand().equals("alteraPicker")) {
			pick("Altera", alteraPath.getText(), true);
		// } else if (e.getActionCommand().equals("xilinxPicker")) {
		//	pick("Xilinx", xilinxPath.getText(), false);
		// } else if (e.getActionCommand().equals("latticePicker")) {
		// 	pick("Lattice", latticePath.getText(), false);
		// } else if (e.getActionCommand().equals("apioPicker")) {
		// 	pick("Apio", apioPath.getText(), false);
		// } else if (e.getActionCommand().equals("openFPGAloaderPicker")) {
		// 	pick("openFPGAloader", openFPGAloaderPath.getText(), false);
		} else if (e.getActionCommand().equals("gowinPicker")) {
			pick("Gowin", gowinShPath.getText(), false);
		} else if (e.getActionCommand().equals("gowinProgPicker")) {
			pick("Gowin Programmer", gowinProgPath.getText(), false);
		} else if (e.getActionCommand().equals("Cancel")) {
			panel.setVisible(false);
		} else if (e.getActionCommand().equals("OK")) {
			panel.setVisible(false);
			save();
		}
	}

	public static String pretty(String[] names, String conjunction) {
		String s = names[0];
		for (int i = 1; i < names.length; i++) {
			s += (i == names.length - 1 ? " "+conjunction+" " : ", "); 
			s += names[i];
		}
		return s;
	}

	private void save() {
		String apath = alteraPath.getText();
		if (!settings.SetAlteraToolPath(apath)) {
			String names = pretty(FPGADownload.ALTERA_PROGRAMS, "and");
			JOptionPane.showMessageDialog(null,
					"Error setting Altera tool path.\n" +
					"Please pick a *trusted* URL, or a directory containing " + names + ", or select a stand-alone synthesis script.");
		}
		settings.SetAltera64Bit(altera64Choice.isSelected());
		// String xpath = xilinxPath.getText();
		// if (!settings.SetXilinxToolPath(xpath)) {
		// 	String names = pretty(FPGADownload.XILINX_PROGRAMS, "and");
		// 	JOptionPane.showMessageDialog(null,
		// 			"Error setting Xilinx tool path.\n" +
		// 			"Please select a directory containing " + names + ", or select a stand-alone synthesis script.");
		// }
		// String lpath = latticePath.getText();
		// if (!settings.SetLatticeToolPath(lpath)) {
		// 	String names = pretty(FPGADownload.LATTICE_PROGRAMS, "or");
		// 	JOptionPane.showMessageDialog(null,
		// 			"Error setting Lattice tool path.\n" +
		// 			"Please select a directory containing " + names + ".");
		// }
		// String ppath = apioPath.getText();
		// if (!settings.SetApioToolPath(ppath)) {
		// 	String names = pretty(FPGADownload.APIO_PROGRAMS, "and");
		// 	JOptionPane.showMessageDialog(null,
		// 			"Invalid python virtualenv directory.\n" +
		// 			"Please select a directory containing " + names + ".");
		// }
		String gpath = gowinShPath.getText();
		if (!settings.SetGowinShPath(gpath)) {
			JOptionPane.showMessageDialog(null,
					"Invalid Gowin Toolchain directory.\n" +
					"Please select a directory containing " + FPGADownload.GOWIN_SH + ".");
		}
		String gprpath = gowinProgPath.getText();
		if (!settings.SetGowinProgPath(gprpath)) {
			JOptionPane.showMessageDialog(null,
					"Invalid Gowin Toolchain directory.\n" +
					"Please select a directory containing " + FPGADownload.GOWIN_PROG + ".");
		}
		// String fpath = openFPGAloaderPath.getText();
		// if (!settings.SetOpenFPGAloaderPath(fpath)) {
		// 	JOptionPane.showMessageDialog(null,
		// 			"Invalid setting for openFPGAloader path.\n" +
		// 			"Please select eecutable or a directory containing openFPGAloader.");
		// }
		settings.SetUseRBF(rbfChoice.isSelected());
		// settings.SetStaticWorkspacePath(workPath.getText());
		settings.UpdateSettingsFile();
		settings.notifyListeners();
	}

	private void pick(String vendor, String path, boolean allowFiles) {
		JFileChooser fc = new JFileChooser(path);
		fc.setFileSelectionMode(allowFiles ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY);
		if (!"".equals(path)) {
			File file = new File(path);
			if (file.exists()) {
				fc.setSelectedFile(file);
			}
		}
		if (vendor != null)
			fc.setDialogTitle(vendor + " Design Suite Path Selection");
		else
			fc.setDialogTitle("Temporary directory for compilation");
		int retval = fc.showOpenDialog(null);
		if (retval != JFileChooser.APPROVE_OPTION)
			return;
		File file = fc.getSelectedFile();
		path = file.getPath();
		if ("Altera".equals(vendor)) {
			alteraPath.setText(path);
			if (!settings.validAlteraToolPath(path)) {
				String names = pretty(FPGADownload.ALTERA_PROGRAMS, "and");
				JOptionPane.showMessageDialog(null,
						"Invalid Altera tool path.\n" +
						"Please pick a *trusted* URL, or a directory containing " + names + ", or select a stand-alone synthesis script.");
			}
		// } else if ("Xilinx".equals(vendor)) {
		// 	xilinxPath.setText(path);
		// 	if (!settings.validXilinxToolPath(path)) {
		// 		String names = pretty(FPGADownload.XILINX_PROGRAMS, "and");
		// 		JOptionPane.showMessageDialog(null,
		// 				"Invalid Xilinx tool path.\n" +
		// 				"Please select a directory containing " + names + ", or select a stand-alone synthesis script.");
		// 	}
		// } else if ("Lattice".equals(vendor)) {
		// 	latticePath.setText(path);
		// 	if (!settings.validLatticeToolPath(path)) {
		// 		String names = pretty(FPGADownload.LATTICE_PROGRAMS, "or");
		// 		JOptionPane.showMessageDialog(null,
		// 				"Invalid Lattice tool path.\n" +
		// 				"Please select a directory containing " + names + ".");
		// 	}
		// } else if ("Apio".equals(vendor)) {
		// 	apioPath.setText(path);
		// 	if (!settings.validApioToolPath(path)) {
		// 		String names = pretty(FPGADownload.APIO_PROGRAMS, "and");
		// 		JOptionPane.showMessageDialog(null,
		// 				"Invalid python virtualenv directory.\n" +
		// 				"Please select a directory containing " + names + ".");
		// 	}
		// } else if ("openFPGAloader".equals(vendor)) {
		// 	openFPGAloaderPath.setText(path);
		// 	if (!settings.validOpenFPGAloaderPath(path)) {
		// 		JOptionPane.showMessageDialog(null,
		// 				"Invalid setting for openFPGAloader path.\n" +
		// 				"Please select executable or a directory containing openFPGAloader.");
		// 	}
		} else if ("Gowin".equals(vendor)) {
			gowinShPath.setText(path);
			if (!settings.SetGowinShPath(path)) {
				JOptionPane.showMessageDialog(null,
						"Invalid Gowin Toolchain directory.\n" +
						"Please select a directory containing " + FPGADownload.GOWIN_SH + ".");
			}
		} else if ("Gowin Progammer".equals(vendor)) {
			gowinProgPath.setText(path);
			if (!settings.SetGowinProgPath(path)) {
				JOptionPane.showMessageDialog(null,
						"Invalid Gowin Toolchain directory.\n" +
						"Please select a directory containing " + FPGADownload.GOWIN_PROG + ".");
			}
		// } else {
		// 	workPath.setText(path);
		}
	}
}
