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

package com.bfh.logisim.download;

import java.io.File;
import java.util.ArrayList;

import com.bfh.logisim.fpga.PinBindings;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.bfh.logisim.hdlgenerator.TickHDLGenerator;
import com.bfh.logisim.hdlgenerator.ToplevelHDLGenerator;
import com.bfh.logisim.netlist.Netlist;

public abstract class FPGASynthesizer extends FPGATool {

  static final String TOP_HDL = ToplevelHDLGenerator.HDL_NAME;
  static final String CLK_PORT = TickHDLGenerator.FPGA_CLK_NET;
  
  // Parameters set by Commander, available for use by tool
  public FPGAProgrammer programmer; // if null, synthesizer should auto-select

  // Capability flag set by downloader, used by Commander
  public boolean supportsRemoteJTAG = false;

  protected FPGASynthesizer(Toolchain toolchain, String nickname, FPGAReport err) {
    super(toolchain, nickname, err);
  }

  // Subclasses can override this to generate a custom top-level HDL file
  public ToplevelHDLGenerator toplevelHDLGenerator(Netlist.Context ctx, PinBindings pinBindings) {
    return new ToplevelHDLGenerator(ctx, pinBindings);
  }

  private ArrayList<String> enumerateHDLFiles(String path) {
    ArrayList<String> files = new ArrayList<>();
    if (lang.equals(VHDL))
      enumerateHDLFilesRecursive(path, files,
          FileWriter.EntityExtension + ".vhd",
          FileWriter.ArchitectureExtension + ".vhd");
    else
      enumerateHDLFilesRecursive(path, files, ".v", null);
    return files;
  }

  private void enumerateHDLFilesRecursive(String path, ArrayList<String> files,
    String entityEnding, String behaviorEnding) {
    File dir = new File(path);
    if (!path.endsWith(File.separator))
      path += File.separator;
    for (File f : dir.listFiles()) {
      String subpath = path + f.getName();
      if (f.isDirectory())
        enumerateHDLFilesRecursive(subpath, files, entityEnding, behaviorEnding);
      else if (f.getName().endsWith(entityEnding))
        files.add(subpath.replace("\\", "/"));
      else if (f.getName().endsWith(behaviorEnding))
        files.add(subpath.replace("\\", "/"));
    }
  }

  // Commander will do either the full or quick sequence:
  //
  //  Full (Gen+Synth+Program)                  Quick (Program)
  //  1. Delete old work directory              1. keep old work directory
  //  2. Do pin assignments                     2. skip pin assignment
  //  3. Generate HDL files                     3. skip HDL generation
  //  4. tool.generateScripts(bindings)         4. skip generating scripts
  //  5. tool.createSynthesisPlan(stages)       5. no plan for synthesis
  //                                               instead check tool.readyForDownload()
  //  6. tool.createProgrammingPlan(stages)     6. tool.createProgrammingPlan(stages)
  //  7. execute each stage                     7. execute each stage
  
  public boolean generateScripts(PinBindings ioResources) {
    return generateScripts(ioResources, enumerateHDLFiles(circuitPath));
  }
 
  // Generate project files and scripts needed before synthesis can occur.
  // Returns true on success.
  protected abstract boolean generateScripts(PinBindings ioResources, ArrayList<String> hdlFiles);
 
  // Create plan for full synthesis + programming sequence
  // Returns true on success.
  public abstract boolean createSynthesisPlan(ArrayList<Stage> stages);

  // Check if things are in a state suitable for a quick programming-only sequence
  // Returns true on success.
  public abstract boolean readyForDownload();

  // Create plan for quick programming-only sequence
  // Returns true on success.
  public abstract boolean createProgrammingPlan(ArrayList<Stage> stages);

  // Determine allowable fpga clock frequencies. If a PLL or similar block is
  // available and supported, the parameters of that block, together with the
  // base oscillator frequency, determine which clock frequencies are available.
  // The returned list may be incomplete, perhaps just including a few of the
  // likely useful frequencies or parameter combinations. If no PLL or simlar
  // block is available, or the toolchain doesn't support it (yet), then we
  // return a list with just the oscillator frequency instead.
  public double[] availableFpgaFrequencies() {
    // default: no PLL or similar block available or supported
    return new double[] { board.fpga.ClockFrequency };
  }

}
