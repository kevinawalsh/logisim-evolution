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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.file.XmlIterator;
import com.cburch.logisim.std.wiring.Pin;
import static com.bfh.logisim.netlist.Netlist.Int3;

// PinBindings tracks the bindings ("mappings") between, on the one side,
// I/O-related components present in the logisim design and, on the other side,
// the physical (or synthetic) I/O resources defined by the FPGA baord.
//
// For the I/O-related components in the design, each one defines:
//   - a "hidden net" (one or more bits)
//   - a list of acceptable BoardIO types (a primary and some alternates)
//   - a path from the top level down to that specific instance
//
// For a multi-bit I/O-related component, an acceptable BoardIO types might only
// support a 1-bit version. In that case, the component can be treated like
// separate 1-bit signals. For example, a 3-bit output with primary type RGBLed
// would be treated as 3 individual signals when used with alternate type LED.
//
// For physical I/O resources, each BoardIO in the Board defines:
//   - its actual physicial type (LED, DipSwtich, Button, raw pin, etc.)
//   - a direction, defined by its type
//   - its bit width
//   - the pin location/number on the FPGA chip for each bit
//   - its location on the FPGA demo board (a rectangle on a picture)
//
// For a multi-bit physical BoardIO resource, like a 4-bit physical DipSwitch,
// the single BoardIO could idealy be treated as three separate one-bit output
// signals with the same direction. [This wasn't previously supported... the xml files
// don't describe locations for the individual pins within a multi-bit I/O
// resource, for example, so there isn't a great way to let the user know which pin was
// which. But I supposed we can have a popup instead showing bit index and/or
// actual pin names.]
//
// There are also "synthetic" I/O resources, like "all ones input", "all zeros
// input", "disconnected output", "constant value input", etc., that can be used
// in place of physical I/O resources.
//
// In the simplest case, each whole I/O component is paired with one whole I/O
// resource. In that case, the type, width and direction of the component and
// resource must match. In the more tricky cases, the separate signals for an
// I/O-related component might be individually mapped to different physical I/O
// resources [which ideally we will also allow to have been broken up and
// treated as separate signals, but again that wasn't previously supported].
//
// Commander holds the instance of this, but mostly doesn't use it.
//
// BindingsDialog is the UI for configuring the bindings.
//
// The Xilinx and Altera FPGA download scripts use this to get the list of pin
// locations that need to be given to the HDL synthesis tools.
//   see: GetFPGAPinLocs(int vendor)
//
// TopLevelHDLGenerator uses this:
//  - to connect hidden nets (with inversions, if needed) to the
//    user-selected BoardIO FPGA pins and/or synthetic values.
public class PinBindings {

  private final FPGAReport err;

  // List of I/O-related components in the design, organized by path.
	public final HashMap<Path, NetlistComponent> components;

  // The FPGA board describing available physical I/O resources.
	public final Board board;

  // Mappings defined so far.
  public final HashMap<Source, Dest> mappings = new HashMap<>();

  // Indicates whether the circuit contains any clock generators.
  // Set by ToplevelHDLGenerator, used by FPGA downloaders.
  public boolean requiresOscillator;

  public static class Source {
    public final Path path;
    public final NetlistComponent comp;
    public final int bit; // -1 means entire NetlistComponent is mapped
    public final BoardIO.Type type;
    public final Int3 width;
    public Source(Path p, NetlistComponent c, int b, BoardIO.Type t, Int3 w) {
      path = p;
      comp = c;
      bit = b;
      type = t;
      width = w;
    }
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Source))
        return false;
      Source other = (Source)o;
      return path.equals(other.path) && bit == other.bit;
    }
    public int hashCode() {
      return path.toString().hashCode() + 31*bit;
    }
    @Override
    public String toString() {
      if (bit < 0)
        return path + " " + type;
      else
        return path + " " + type + " [bit " + bit + "]";
    }
  }

  public static class Dest {
    public final BoardIO io; // may be synthetic
    public final int bit; // -1 means something maps to this entire BoardIO
    private Int3 seqno; // set by finalizeMappings(), used during HDL generation
    public Dest(BoardIO i, int b) { io = i; bit = b; }
    @Override
    public String toString() {
      if (bit < 0)
        return io.toString();
      else
        return io.pinLabel(bit) + " of " + io;
    }
    public Int3 seqno() {
      return seqno.copy();
    }
  }

  public PinBindings(FPGAReport err, Board board,
      HashMap<Path, NetlistComponent> components, Config config) {
    this.err = err;
		this.components = components;
		this.board = board;
    if (config != null) {
      Path root = null;
      for (Path p : components.keySet()) {
        root = p.root();
        break;
      }
      if (root == null)
        return; // don't bother, nothing to do
      for (Config.Mapping m : config.mappings) {
        try {
          addMapping(m, root);
        } catch (Exception e) {
          err.AddInfo("Ignoring previously saved mapping: %s", e.getMessage());
        }
      }
    }
  }

  private void addMapping(Config.Mapping m, Path root) throws Exception {
    String relpath = m.src;
    String srcPin = null;
    int i = relpath.indexOf(" of ");
    if (i > 0) {
      srcPin = relpath.substring(0, i);
      relpath = relpath.substring(i+4);
    }
    Path path = root.extend(relpath);
    String destName = m.dest;
    String destPin = null;
    i = destName.indexOf(" of ");
    if (i > 0) {
      destPin = destName.substring(0, i);
      destName = destName.substring(i+4);
    }
    BoardIO.Type type = BoardIO.Type.getPhysicalType(m.type);
    if (type == BoardIO.Type.Unknown)
      throw new Exception(m.type + " is not a valid type");
    NetlistComponent comp = components.get(path);
    if (comp == null)
      throw new Exception("Path " + path + " no longer exists");
    Int3 compWidth = widthFor(comp);
    BoardIO io = findBoardIO(destName, compWidth.size());
    if (io == null)
      throw new Exception("I/O resource " + destName + " no longer exists");
    if (!typesFor(comp).contains(type))
      throw new Exception(type + " is no longer a supported type");
    if (BoardIO.OneBitTypes.contains(type)) {
      if (compWidth.size() > 1 && srcPin == null)
        throw new Exception(type + " is only one bit, but " + compWidth.size() +
            " bits needed for " + path);
    } else {
      if (compWidth.size() == 1 || srcPin != null)
        throw new Exception(type + " is multi-bit, but only one bit needed for " + m.src);
    }
    int srcBit = -1;
    if (srcPin != null) {
      srcBit = indexOf(pinLabels(comp), srcPin);
      if (srcBit < 0)
        throw new Exception(srcPin + " is no longer part of " + path);
    }
    int destBit = -1;
    if (BoardIO.PhysicalTypes.contains(io.type)) {
      if (destPin != null) {
        destBit = indexOf(io.pinLabels(), destPin);
        if (destBit < 0)
          throw new Exception(destPin + " is not part of I/O resource " + io);
      }
    } else {
      if (destPin != null)
        throw new Exception(m.dest + " is not a valid I/O resource");
      if (io.type == BoardIO.Type.Unconnected) {
        if (compWidth.in > 0)
          throw new Exception("component expecting input can't be unconnected");
      } else {
        if (compWidth.out + compWidth.inout > 0)
          throw new Exception("constant input can't be used for component that outputs");
      }
    }
    Source src;
    if (srcBit >= 0)
      src = new Source(path, comp, srcBit, type, compWidth.forSingleBit());
    else
      src = new Source(path, comp, -1, type, compWidth);
    addMapping(src, io, destBit, null);
	}

  private static int indexOf(String[] a, String s) {
    for (int i = 0; i < a.length; i++)
      if (a[i].equals(s))
        return i;
    return -1;
  }

  private BoardIO findBoardIO(String name, int width) {
    for (BoardIO io : board.getIoComponents())
      if (io.toString().equals(name))
        return io;
    return BoardIO.decodeSynthetic(name, width);
  }

  // public void setComponents(HashMap<Path, NetlistComponent> newComponents) {
  //   // todo: preserve existing mappings wherever possible.
  //   // For now, we just clear all mappings and reset components.
  //   mappings.clear();
  //   components.clear();
  //   components.putAll(newComponents);
  // }
  
  // ToplevelHDLGenerator has a port for each bit of each physical I/O pin that
  // it uses (for hidden nets and/or top-level circuit pins). Commander calls
  // this to lock in the mappings, giving each physical BoardIO input, inout,
  // and output bit a sequence number, and counting how many bits there are in
  // total.
  //
  // For inout ports, it is essential that the seqno assigned here match the
  // global indices assigned during DRC, since these signals can't be renumbered
  // in ToplevelHDLGenerator. And note that (so far) the only inout ports are
  // hidden nets, so the seqno for all inout signals can just come directly from
  // the global indices. For consistency, we also make the in-pins and out-pins
  // also follow the global indices for hidden ports, and give non-hidden in-pin
  // and out-pin signals higher seqno, at the end of the list.
  private Int3 finalizedCounts;
  private Int3 finalizedOpenCounts;
  public void finalizeMappings() {
    Int3 counts = new Int3();
    Int3 opens = new Int3();
    mappings.forEach((s, d) -> {
      if (BoardIO.PhysicalTypes.contains(d.io.type)) {
        d.seqno = counts.copy();
        counts.increment(s.width);
      } else if (d.io.type == BoardIO.Type.Unconnected) {
        d.seqno = opens.copy();
        opens.increment(s.width);
      }
    });
    finalizedCounts = counts;
    finalizedOpenCounts = opens;
  }

  // Counts of all I/O-related physical FPGA pins used in the design.
  public Int3 countFPGAPhysicalIOPins() {
    return finalizedCounts.copy();
  }

  // Counts of all I/O-related unconnected mappings.
  public Int3 countFPGAUnconnectedIOMappings() {
    return finalizedOpenCounts.copy();
  }

  public static interface PhysicalPinConsumer {
    public void process(String pin, String net, BoardIO io, String label);
  }

  public void forEachPhysicalPin(PhysicalPinConsumer f) {
    err.AddInfo("Assigning input pins");
    forEachPhysicalPin(f, w -> w.in, "FPGA_INPUT_PIN_");
    err.AddInfo("Assigning inout pins");
    forEachPhysicalPin(f, w -> w.inout, "FPGA_BIDIR_PIN_");
    err.AddInfo("Assigning output pins");
    forEachPhysicalPin(f, w -> w.out, "FPGA_OUTPUT_PIN_");
  }

  private void forEachPhysicalPin(PhysicalPinConsumer f,
      Function<Int3, Integer> selector, String signalPrefix) {
    mappings.forEach((s, d) -> {
      int w = selector.apply(s.width);
      err.AddInfo("  %s ? w=%d", s, w);
      if (w > 0 && BoardIO.PhysicalTypes.contains(d.io.type)) {
        err.AddInfo("    mapping to %s", d);
        String[] labels = d.io.pinLabels();
        String[] pins = d.io.pins;
        int seqno = selector.apply(d.seqno);
        int b = d.bit < 0 ? 0 : d.bit;
        for (int i = 0; i < w; i++)
          f.process(pins[b+i], signalPrefix + (seqno + i), d.io,
              d.bit < 0 ? d.io.toString() :
              String.format("%s of %s", labels[b+i], d.io.toString()));
      }
    });
  }

  public String[] pinLabels(Path path) {
    return pinLabels(components.get(path));
  }

  public String[] pinLabels(NetlistComponent comp) {
    if (comp.hiddenPort == null) {
      // Top-level input or output port.
      int w = comp.original.getEnd(0).getWidth().getWidth();
      return BoardIO.Type.Ribbon.pinLabels(w);
    } else {
      // Button, LED, PortIO, and other I/O-related components.
      return comp.hiddenPort.labels.toArray(new String[comp.hiddenPort.labels.size()]);
    }
  }

  public static final List<BoardIO.Type> SINGLE_BIT_PIN_TYPES =
      Arrays.asList(new BoardIO.Type[] { BoardIO.Type.Pin });
  public static final List<BoardIO.Type> MULTI_BIT_PIN_TYPES =
      Arrays.asList(new BoardIO.Type[] { BoardIO.Type.Ribbon, BoardIO.Type.Pin });

  public List<BoardIO.Type> typesFor(Path path) {
    return typesFor(components.get(path));
  }

  public List<BoardIO.Type> typesFor(NetlistComponent comp) {
    if (comp.hiddenPort == null) {
      // Top-level input or output port.
      int w = comp.original.getEnd(0).getWidth().getWidth();
      return w > 1 ? MULTI_BIT_PIN_TYPES : SINGLE_BIT_PIN_TYPES;
    } else {
      // Button, LED, PortIO, and other I/O-related types.
      return comp.hiddenPort.types;
    }
  }

  public Int3 widthFor(Path path) {
    return widthFor(components.get(path));
  }

  public Int3 widthFor(NetlistComponent comp) {
    if (comp.hiddenPort == null) {
      // Top-level input or output port. Bidirectional is supported here.
      int w = comp.original.getEnd(0).getWidth().getWidth();
      boolean i = comp.original.getEnd(0).canOutput(); // output to circuit, input from board
      boolean o = comp.original.getEnd(0).canInput(); // input to circuit, output from board
      Int3 compWidth = new Int3();
      if (i && o)
        compWidth.inout = w;
      else if (i)
        compWidth.in = w;
      else
        compWidth.out = w;
      return compWidth;
    } else {
      return comp.hiddenPort.width();
    }
  }

  private HashMap<String, PullBehavior> inputPullRequests = new HashMap<>();

  public void addPull(String pin, PullBehavior pull) {
    inputPullRequests.put(pin, pull);
  }
  public PullBehavior getInputPinPull(String pin) {
    return inputPullRequests.getOrDefault(pin, PullBehavior.NONE);
  }
  public int countInputPinPulls() {
    return inputPullRequests.size();
  }

  public Source sourceFor(Path path) {
    NetlistComponent comp = components.get(path);
    Int3 compWidth = widthFor(comp);
    List<BoardIO.Type> types = typesFor(comp);
    BoardIO.Type type = selectDefaultType(types, compWidth.size());
    return new Source(path, comp, -1, type, compWidth);
  }

  public ArrayList<Source> bitSourcesFor(Path path) {
    NetlistComponent comp = components.get(path);
    Int3 bitWidth = widthFor(comp).forSingleBit();
    List<BoardIO.Type> types = typesFor(comp);
    BoardIO.Type bitType = selectDefaultType(types, 1);
    ArrayList<Source> ret = new ArrayList<>();
    String[] pinLabels = pinLabels(path);
    for (int i = 0; i < pinLabels.length; i++)
      ret.add(new Source(path, comp, i, bitType, bitWidth.copy()));
    return ret;
  }

  public boolean isMapped(Path path) {
    if (mappings.containsKey(sourceFor(path)))
      return true;
    for (Source bitSrc : bitSourcesFor(path))
      if (!mappings.containsKey(bitSrc))
        return false;
    return true;
  }

  public boolean containsMappingFor(BoardIO io, int bit) {
    for (Dest d : mappings.values()) {
      if (d.io == io && (d.bit < 0 || d.bit == bit))
        return true;
    }
    return false;
  }

  private BoardIO.Type selectDefaultType(List<BoardIO.Type> types, int width) {
    if (width == 1) {
      // Pick first type meant for single-bit inputs.
      for (BoardIO.Type t : types)
        if (BoardIO.OneBitTypes.contains(t))
          return t;
      return BoardIO.Type.Pin; // default if nothing appropriate
    } else {
      // Pick first type meant for multi-bit inputs.
      for (BoardIO.Type t : types)
        if (!BoardIO.OneBitTypes.contains(t))
          return t;
      return BoardIO.Type.Ribbon; // default if nothing appropriate
    }
  }

  public ArrayList<BoardIO> compatibleResources(Source src) {
    // NetlistComponent comp = components.get(src.path);
    if (src.bit < 0)
      return compatibleResources(src.width, src.type);
    else
      return compatibleResources(src.width.forSingleBit(), src.type);
  }

  private ArrayList<BoardIO> compatibleResources(Int3 compWidth, BoardIO.Type compType) {
    ArrayList<BoardIO> res = new ArrayList<>();
    if (compWidth.isMixedDirection()) {
      err.AddError("INTERNAL ERROR: Detected I/O component with mixed direction bits.");
      return res;
    }
    for (BoardIO io : board.getIoComponents()) {
      if (io.isCompatible(compWidth, compType))
        res.add(io);
    }
    return res;
  }

  public HashSet<Source> addMapping(Source src, BoardIO io, int bit) {
    HashSet<Source> modified = new HashSet<>();
    // sanity check: sizes must match
    int srcWidth = src.bit >= 0 ? 1 : src.width.size();
    int ioWidth = bit >= 0 ? 1 : io.width;
    if (srcWidth != ioWidth) {
      err.AddError("INTERNAL ERROR: Component is %d bits, but I/O resource is %d bits.",
          srcWidth, ioWidth);
      return modified;
    }
    addMapping(src, io, bit, modified);
    return modified;
  }

  private void addMapping(Source src, BoardIO io, int bit, HashSet<Source> modified) {
    // remove existing mappings from same source and mappings to same dest
    mappings.entrySet().removeIf(e -> {
      Source s = e.getKey();
      Dest d = e.getValue();
      boolean samesource =
          s.path.equals(src.path) && (src.bit < 0 || s.bit < 0 || src.bit == s.bit);
      boolean samedest = 
          d.io == io && (bit < 0 || d.bit < 0 || bit == d.bit);
      if (modified != null && (samesource || samedest))
        modified.add(s);
      return samesource || samedest;
    });
    if (modified != null)
      modified.add(src);
    mappings.put(src, new Dest(io, bit));
  }

  public String getStatus() { // result begins with "All" if and only if everything mapped
    int remaining = 0, count = 0;
    for (Path path : components.keySet()) {
      count++;
      if (!isMapped(path))
        remaining++;
    }
    if (remaining == 0)
      return String.format("All %d components are mapped to I/O resources", count);
    else
      return String.format("%d of %d components remaining to be mapped to I/O resources",
          remaining, count);
  }

  public boolean allPinsAssigned() {
    for (Path path : components.keySet())
      if (!isMapped(path))
        return false;
    return true;
  }

  public Config makeConfig(String boardname, String clkmode, int clkdiv) {
    Config c = new Config(boardname, clkmode, clkdiv);
    mappings.forEach((s, d) -> {
      String src = s.path.relstr();
      if (s.bit >= 0)
        src = pinLabels(s.path)[s.bit] + " of " + src;
      c.mappings.add(new Config.Mapping(src, s.type.toString(), d.toString()));
    });
    return c;
  }

  public static Config parseConfig(Element xml) throws Exception {
    String boardname = xml.getAttribute("board");
    if (boardname == null || boardname.isEmpty())
      throw new Exception("missing boardname");
    String clkmode = "reduced";
    int clkdiv = 0;
    for (Element clk : XmlIterator.forChildElements(xml, "clock")) {
      clkmode = clk.getAttribute("mode");
      if (clkmode == null || clkmode.isEmpty())
        throw new Exception("missing clock mode");
      else if (clkmode.equals("maximum"))
        clkdiv = 0;
      else if (clkmode.equals("dynamic"))
        clkdiv = -1;
      else if (clkmode.equals("reduced")) {
        String s = clk.getAttribute("period");
        if (s == null || s.isEmpty())
          throw new Exception("missing period for reduced clock mode");
        try {
          clkdiv = Integer.parseInt(s);
        } catch (Exception e) {
          throw new Exception("invalid period for reduced clock mode: " + s);
        }
      }
    }
    Config c = new Config(boardname, clkmode, clkdiv);
    for (Element m : XmlIterator.forChildElements(xml, "map")) {
      String src = m.getAttribute("src");
      if (src == null || src.isEmpty())
        throw new Exception("missing pin mapping source");
      String type = m.getAttribute("type");
      if (type == null || type.isEmpty())
        throw new Exception("missing pin mapping type");
      String dest = m.getAttribute("dest");
      if (dest == null || dest.isEmpty())
        throw new Exception("missing pin mapping destination");
      c.mappings.add(new Config.Mapping(src, type, dest));
    }
    return c;
  }

  public static class Config { // holds clock and pin config, to be saved in logisim file
    public final String boardname; // e.g. "TERASIC_DE0"
    public final ArrayList<Mapping> mappings = new ArrayList<>();
    public String clkmode; // "maximum", "reduced", or "dynamic"
    public int clkdiv; // only for "reduced" mode
    
    public static class Mapping {
      public final String src, type, dest;
      public Mapping(String s, String t, String d) {
        src = s;
        type = t;
        dest = d;
      }
    }

    public Config(String boardname, String clkmode, int clkdiv) {
      this.boardname = boardname;
      this.clkmode = clkmode;
      this.clkdiv = clkdiv;
    }

    public Element toXml(Document doc) {
      Element config = doc.createElement("fpgaconfig");
      config.setAttribute("board", boardname);
      Element clk = doc.createElement("clock");
      clk.setAttribute("mode", clkmode);
      if (clkmode.equals("reduced"))
        clk.setAttribute("period", ""+clkdiv);
      config.appendChild(clk);
      for (Mapping m : mappings) {
        Element map = doc.createElement("map");
        map.setAttribute("src", m.src);
        map.setAttribute("type", m.type);
        map.setAttribute("dest", m.dest);
        config.appendChild(map);
      }
      return config;
    }
  }

}
