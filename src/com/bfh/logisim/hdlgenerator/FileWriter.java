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

package com.bfh.logisim.hdlgenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import com.bfh.logisim.gui.FPGAReport;

public class FileWriter {
  
  // FIXME: these need a home, duplicated in several places
  static final String VHDL = "VHDL";
  static final String VERILOG = "Verilog";

	public static boolean CopyArchitecture(String source, String dest,
			String componentName, FPGAReport reporter, String HDLType) {
		try {
			if (HDLType.equals(VERILOG)) {
				reporter.AddFatalError("Empty VHDL box not supported in verilog.");
				return false;
			}
			File inFile = new File(source);
			if (!inFile.exists()) {
				reporter.AddFatalError("Source file \"" + source + "\" does not exist!");
				return false;
			}
			// copy file
			String destPath = dest + componentName + ArchitectureExtension + ".vhd";
			File outFile = new File(destPath);
			InputStream in = new FileInputStream(inFile);
			OutputStream out = new FileOutputStream(outFile);
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
			reporter.AddInfo("\"" + source + "\" successfully copied to \""
					+ destPath + "\"");
			return true;
		} catch (Exception e) {
			reporter.AddFatalError("Unable to copy file!");
			return false;
		}
	}
	public static File GetFilePointer(String TargetDirectory,
			String ComponentName, boolean IsMif, boolean IsEntity, int idx,
      FPGAReport MyReporter, String HDLType) {
		try {
			File OutDir = new File(TargetDirectory);
			if (!OutDir.exists()) {
				if (!OutDir.mkdirs()) {
					return null;
				}
			}
			String FileName = TargetDirectory;
			if (!FileName.endsWith(File.separator)) {
				FileName += File.separator;
			}
			FileName += ComponentName;
			if (IsEntity && !IsMif) {
				if (HDLType.equals(VHDL)) {
					FileName += EntityExtension;
				}
			} else if (!IsMif) {
				if (HDLType.equals(VHDL)) {
					FileName += ArchitectureExtension;
				}
			}
			if (IsMif) {
				FileName += "_"+idx+".mif";
			} else if (HDLType.equals(VHDL)) {
				FileName += ".vhd";
			} else {
				FileName += ".v";
			}
			File OutFile = new File(FileName);
			MyReporter.AddInfo("Creating HDL file : " + FileName);
			if (OutFile.exists()) {
				MyReporter.AddWarning("HDL file \"" + FileName
						+ "\" already exists");
				return null;
			}
			return OutFile;
		} catch (Exception e) {
			MyReporter.AddFatalError("Unable to create file!");
			return null;
		}
	}

	public static File GetFilePointer(String TargetDirectory, String Name,
			FPGAReport MyReporter) {
		try {
			File OutDir = new File(TargetDirectory);
			if (!OutDir.exists()) {
				if (!OutDir.mkdirs()) {
					return null;
				}
			}
			String FileName = TargetDirectory;
			if (!FileName.endsWith(File.separator)) {
				FileName += File.separator;
			}
			FileName += Name;
			File OutFile = new File(FileName);
			MyReporter.AddInfo("Creating file : " + FileName);
			if (OutFile.exists()) {
				MyReporter.AddWarning("File \"" + FileName
						+ "\" already exists");
				return null;
			}
			return OutFile;
		} catch (Exception e) {
			MyReporter.AddFatalError("Unable to create file!");
			return null;
		}
	}

	public static boolean WriteContents(File outfile,
			ArrayList<String> Contents, FPGAReport MyReporter) {
		try {
			FileOutputStream output = new FileOutputStream(outfile);
			for (String ThisLine : Contents) {
				if (!ThisLine.isEmpty()) {
					output.write(ThisLine.getBytes());
				}
				output.write("\n".getBytes());
			}
			output.flush();
			output.close();
			return true;
		} catch (Exception e) {
			MyReporter.AddFatalError("Could not write to file \""
					+ outfile.getAbsolutePath() + "\"");
			return false;
		}
	}

	public static final String EntityExtension = "_entity";
	public static final String ArchitectureExtension = "_behavior";
}
