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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumSet;
import java.util.Map;

import org.w3c.dom.Element;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.XmlUtil;
import com.cburch.logisim.std.io.DipSwitch;
import com.cburch.logisim.std.io.LedBar;
import com.cburch.logisim.std.io.PortIO;
import com.cburch.logisim.std.io.RGBLed;
import com.cburch.logisim.util.Errors;
import static com.bfh.logisim.netlist.Netlist.Int3;

// Each BoardIO represents one physical I/O resource, like an LED, button, or
// switch. Some I/O resources can only be used as inputs (e.g. a button), some
// can only be used as outputs (e.g. an LED), some are inherently bidirectional
// (e.g. the PS2 keyboard connector), and some have no inherent direction at all
// (e.g. a debug or expansion header directly connected to a configurable pin on
// the FPGA). As a simplification, we assume all the bits in a multi-bit I/O
// resource has the same "direction", which can be one of:
//  in  - for things that can only be used as inputs, e.g. buttons and switches
//  out - for things that can only be used as outputs, e.g. LEDs
//  any - for things that can be used as inputs, outputs, or bidirectional inout
// Note: Things with direction "any", if misused, can cause physical short
// circuits. A keyboard connector would be classified as a 4-bit I/O resource
// with direction "any", but if you map an output driver to those bits, it will
// conflict when the keyboard tries to send scancodes. However, this is always a
// risk with any bidirectional port anyway. The only place we could do a little
// better here is allowing a multi-bit connection where the individual bits can
// have differrent directions (whereas now they'd all have to be promoted to
// "any", even if we knew some bits are input-only, or the connector would have
// to be split into separate BoardIO resources).
public class BoardIO {

  public static enum Type {

    // NOTE: Careful of ordering, the last argument can't use a forward reference.

    // name           description,                          assn,         reality,   orient, dir,      min/max/def width,  degneratesTo
    AllZeros         ("Always-zero Input",                  ASSIGNABLE,   SYNTHETIC, UNORIENTABLE,  DIR.IN,   -1, -1, -1,         null),
    AllOnes          ("Always-one Input",                   ASSIGNABLE,   SYNTHETIC, UNORIENTABLE,  DIR.IN,   -1, -1, -1,         null),
    Constant         ("User-defined Constant Input",        ASSIGNABLE,   SYNTHETIC, UNORIENTABLE,  DIR.IN,   -1, -1, -1,         null),
    Unconnected      ("Unconnected",                        ASSIGNABLE,   SYNTHETIC, UNORIENTABLE,  DIR.OUT,  -1, -1, -1,         null),

    InReserved       ("Reserved Input Pin",                 UNASSIGNABLE, PHYSICAL,  UNORIENTABLE,  DIR.IN,   1, 32, 1,           null),
    OutReserved      ("Reserved Output Pin",                UNASSIGNABLE, PHYSICAL,  UNORIENTABLE,  DIR.OUT,  1, 32, 1,           null),

    InPin            ("Generic Input Pin",                  ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.IN,   1, 1, 1,            null),
    BiPin            ("Generic Bidirectional Pin",          ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.BI,   1, 1, 1,            null),
    OutPin           ("Generic Output Pin",                 ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.OUT,  1, 1, 1,            null),

    Button           ("Button or Switch",                   ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.IN,   1, 1, 1,            InPin),
    DIPSwitch        ("DIP Switch",                         ASSIGNABLE,   PHYSICAL,  ORIENTABLE,    DIR.IN,   DipSwitch.MIN_SWITCH, DipSwitch.MAX_SWITCH, DipSwitch.DEF_SWITCH, Button),

    InRibbon         ("Input Bus or Ribbon Cable",          ASSIGNABLE,   PHYSICAL,  ORIENTABLE,    DIR.IN,   1, 32, 8,           InPin),
    BiRibbon         ("Bidirectional Bus or Ribbon Cable",  ASSIGNABLE,   PHYSICAL,  ORIENTABLE,    DIR.BI,   PortIO.MIN_IO, PortIO.MAX_IO, PortIO.DEF_IO, BiPin),
    OutRibbon        ("Output Bus or Ribbon Cable",         ASSIGNABLE,   PHYSICAL,  ORIENTABLE,    DIR.OUT,  1, 32, 8,           OutPin),

    LED              ("LED",                                ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.OUT,  1, 1, 1,            OutPin),
    RGBLED           ("3-wire RGB LED",                     ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.OUT,  3, 3, 3,            LED),
    SevenSegment     ("Seven Segment Display",              ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.OUT,  8, 8, 8,            LED),
    SevenSegmentGang ("Seven Segment Multi-Digit Display",  ASSIGNABLE,   PHYSICAL,  UNORIENTABLE,  DIR.OUT,  8+2, 8+SevenSegment.MAX_DIGITS, 8+4, null /* degneratesTo: special case */),
    LEDBar           ("LED Bar",                            ASSIGNABLE,   PHYSICAL,  ORIENTABLE,    DIR.OUT,  LedBar.MIN_SEGMENTS, LedBar.MAX_SEGMENTS, LedBar.DEFAULT_SEGMENTS, LED),

    Expanded         ("Expanded", UNASSIGNABLE, null, null, null, -1, -1, -1, null),  // only used by PinBindingsDialog as a placeholder 
    Unknown          ("Unknown", null, null, null, -1, -1, -1, null);  // only used during parsing as temporary placeholder

    private enum ASSN    { ASSIGNABLE, UNASSIGNABLE }
    private enum REALITY { PHYSICAL, SYNTHETIC }
    private enum ORIENT  { ORIENTABLE, UNORIENTABLE }
    private enum DIR     { IN, OUT, BI }

    public final String description;
    public final boolean assignable;
    public final boolean physical, synthetic;
    public final boolean orientable;
    public final boolean inputOnly, outputOnly, anyDirection;
    public final int minWidth, maxWidth, defWidth;
    public final boolean oneBit; // min = max = def = 1
    public final boolean variableWidth; // min != max
    private final Type degeneratesTo;

    Type(String descr, ASSN assignable, REALITY reality, ORIENT orientable, DIR dir, int minWidth, int maxWidth, int defWidth, Type degeneratesTo) {
      this.description = descr;
      this.assignable = (assignable == ASSIGNABLE);
      this.physical == (reality == REALITY.PHYSICAL);
      this.synthetic == (reality == REALITY.SYNTHETIC);
      this.orientable = (orientable == ORIENTABLE);
      this.inputOnly = (dir == DIR.IN);
      this.outputOnly = (dir == DIR.OUT);
      this.anyDirection = (dir == DIR.BIDIR);
      this.minWidth = minWidth;
      this.maxWidth = maxWidth;
      this.defWidth = defWidth;
      this.oneBit = (minWidth == 1 && maxWidth == 1 && defWidth == 1);
      this.variableWidth = (minWidth != maxWidth);
      this.degeneratesTo = degeneratesTo;
    }

    // Note: The types above are used to describe physical I/O resources
    // (with the characteristics as noted above). But Logisim components within
    // the circuit design under test also use the above types to describe
    // constraints on the I/O resources they are meant to be connected to. For
    // Pin and Ribbon types, the component will specific whether the bits are
    // meant to be treated as in, out, or bidirectional. For example, a PortIO
    // component (which declares type Ribbon) must connect to a bidirectional
    // pin, and would not make sense to connect to Buttons or LEDs. That is, all
    // logisim components have a specific direction: in, out, or inout.

		public static Type getPhysicalType(String str) {
			for (Type t : PhysicalTypes)
				if (t.name().equalsIgnoreCase(str))
					return t;
			return Type.Unknown;
		}

    public int minWidth() { return minWidth; }

    public int maxWidth() { return maxWidth; }

    public String getDescription() { return description; }

    public String[] pinLabels(int width) {
      switch (this) {
        case SevenSegment:
        case SevenSegmentGang:
          return com.cburch.logisim.std.io.SevenSegment.pinLabels(width);
        case RGBLED:
          return RGBLed.pinLabels();
        default:
          return genericPinLabels(width);
      }
    }

    public PinOrdering defaultPinOrdering(Bounds r) {
      if (r.width >= r.height) {
        switch (this) {
          case DIPSwitch: return PinOrdering.ORDER_1_LR;
          case LEDBar: return PinOrdering.ORDER_1_RL;
          case Ribbon: return PinOrdering.ORDER_2_BTLR;
          default: return null;
        }
      } else {
        switch (type) {
          case DIPSwitch: return PinOrdering.ORDER_1_TB;
          case LEDBar: return PinOrdering.ORDER_1_BT;
          case Ribbon: return PinOrdering.ORDER_2_LRTB;
          default: return null;
        }
      }
    }

  }

  public static final EnumSet<Type> PhysicalTypes = deriveSet(t -> t.physical);
  // public static final EnumSet<Type> SyntheticTypes = deriveSet(t -> t.synthetic);
  // public static final EnumSet<Type> InputTypes = deriveSet(t -> t.inputOnly);
  // public static final EnumSet<Type> OutputTypes = deriveSet(t -> t.outputOnly);
  // public static final EnumSet<Type> InOutTypes = deriveSet(t -> t.anyDirection);
  // public static final EnumSet<Type> OneBitTypes = deriveSet(t -> t.oneBit);
  // public static final EnumSet<Type> VariableWidthTypes = deriveSet(t -> t.variableWidth);
  // public static final EnumSet<Type> OrientableTypes = deriveSet(t -> t.orientable);

  private static EnumSet<Type> deriveSet(Predicate<Type> pred) {
    EnumSet<Type> set = EnumSet.noneOf(Type.class);
    for (Type t : Type.values())
      if (pred.test(t))
        set.add(t);
    return set;
  }

  private static String[] genericPinLabels(int width) {
    if (width == 1)
      return new String[] { "Pin" };
    String[] labels = new String[width];
    for (int i = 0; i < width; i++)
      labels[i] = "Pin_" + i;
    return labels;
  }

	public final Type type;
	public final int width;
  public final String label;
	public final Bounds rect;                    // only physical types
	public final InputBias bias;                 // only physical types, in/bi/---
	public final IoStandard standard;            // only physical types, in/bi/out
	public final PinActivity activity;           // only physical types, in/bi/out - always ACTIVE_HIGH for Pin and for synthetic
	public final DriveStrength strength;         // only physical types, --/bi/out
	public final IdleBehavior idle;              // only physical types, --/bi/out
  public final PinOrdering orientation;        // only orientable types
  public final int syntheticValue;             // only synthetic types

  // NOTE: For multi-bit types, all pins use the same idle, bias, strength, etc.
  // The exception is syntheticValue, which supports individually specifying the
  // bit for each pin in a multi-bit synthetic input. Conceivably, an FPGA board
  // might want to specify per-bit specs, e.g. different idle or bias specs for
  // some of the pins of a multi-bit component. For now, that isn't supported.

  // FIXME: activity for Pin is always ACTIVE_HIGH... why? Ribbon allows both
  // ACTIVE_HIGH and ACTIVE_LOW.
	
  public final String[] pins;

  // constructor for synthetic I/O resources
  private BoardIO(Type t, int w, int val) {
    type = t;
    width = w;
    syntheticValue = val;
    if (t == Type.Constant)
      label = String.format("constant 0x%x", val);
    else if (t == Type.AllOnes)
      label = "all ones";
    else if (t == Type.AllZeros)
      label = "all zeros";
    else if (t == Type.Unconnected)
      label = "unconnected signal";
    else
      label = "unknown signal";
    activity = PinActivity.ACTIVE_HIGH;
    // rest are unused/empty
    rect = null;
    standard = null;
    bias = null;
    idle = null;
    strength = null;
    orientation = null;
    pins = null;
  }

  public static BoardIO makeSynthetic(Type t, int w, int val) {
    if (!t.synthetic)
      throw new IllegalArgumentException("BoardIO type "+t+" is not meant for synthetic I/O resources");
    return new BoardIO(t, w, val);
  }

  public static BoardIO decodeSynthetic(String str, int w) {
    if (str.startsWith("constant ")) {
      int val = Integer.decode(str.substring(9));
      return makeSynthetic(Type.Constant, w, val);
    } else if (str.equals("all zeros")) {
      return makeSynthetic(Type.AllZeros, w, 0);
    } else if (str.equals("all ones")) {
      return makeSynthetic(Type.AllOnes, w, -1);
    } else if (str.equals("unconnected signal")) {
      return makeSynthetic(Type.Unconnected, w, 0);
    } else {
      return null;
    }
  }

  // constructor for physical I/O resources
  BoardIO(Type t, int w, String l, Bounds r,
      IoStandard s, InputBias b, PinActivity a, DriveStrength g, IdleBehavior v, PinOrdering o, String[] x) {
    if (!t.physical)
      throw new IllegalArgumentException("BoardIO type "+t+" is not meant for physical I/O resources");
    type = t;
    width = w;
    label = l;
    rect = r;
    standard = s; // input, output, bidir
    bias = b; // input, bidir
    if (type.inputOnly && bias == null)
      throw new IllegalArgumentException("BoardIO type "+t+" is an input, but bias resistor spec is missing");
    if (type.anyDirection && bias == null)
      throw new IllegalArgumentException("BoardIO type "+t+" is bidirectional, but bias resistor spec is missing");
    if (type.outputOnly && bias != null)
      throw new IllegalArgumentException("BoardIO type "+t+" is an output, but has a bias resistor spec");
    activity = a; // input, output, bidir
    strength = g; // output, bidir
    if (type.outputOnly && strength == null)
      throw new IllegalArgumentException("BoardIO type "+t+" is an output, but drive strength spec is missing");
    if (type.anyDirection && strength == null)
      throw new IllegalArgumentException("BoardIO type "+t+" is bidirectional, but drive strength spec is missing");
    if (type.inputOnly && strength != null)
      throw new IllegalArgumentException("BoardIO type "+t+" is an input, but has a drive strength spec");
    idle = v; // output, bidir
    if (type.outputOnly && idle == null)
      throw new IllegalArgumentException("BoardIO type "+t+" is an output, but idle spec is missing");
    if (type.anyDirection && idle == null)
      throw new IllegalArgumentException("BoardIO type "+t+" is bidirectional, but idle spec is missing");
    if (type.inputOnly && idle != null)
      throw new IllegalArgumentException("BoardIO type "+t+" is an input, but has idle spec");
    orientation = o;
    pins = x;
    // rest are defaults/empty
    syntheticValue = 0;
  }

  // constructor for physical I/O resources, copies existing physical but but with new position
  BoardIO(BoardIO io, Bounds newPosition) {
    this(io.type, io.width, io.label, newPosition,
        io.standard, io.bias, io.activity,
        io.strength, io.idle, io.orientation, io.pins);
  }

  public static BoardIO parseXml(Element elt) throws Exception {
    Map<String, String> params = XmlUtil.getAttributeMap(elt);

    String typeName = elt.getNodeName();
    if (typeName.equalsIgnoreCase("Pin") || typeName.equalsIgnoreCase("Ribbon")) {
      // These types come in three variants: In_, Out_, and Bi_.
      String use = params.getOrDefault("use", params.getOrDefault("dir", params.getOrDefault("direction", "any")));
      if (use.equalsIgnoreCase("input") || use.equalsIgnoreCase("in"))
        typeName = "In"+typeName; // InPin, InRibbon
      else if (use.equalsIgnoreCase("output") || use.equalsIgnoreCase("out"))
        typeName = "Out"+typeName; // OutPin, OutRibbon
      else if (use.equalsIgnoreCase("any") || use.equalsIgnoreCase("bi") || use.equalsIgnoreCase("bidir") || use.equalsIgnoreCase("bidirectional"))
        typeName = "Bi"+typeName; // BiPin, BiRibbon
      else
        throw new Exception("unrecognized I/O use constraint for '"+typeName+"': '" + use + "'");
    }

    Type t = Type.getPhysicalType(typeName);
    if (t == Type.Unknown)
      throw new Exception("unrecognized I/O resource type: " + elt.getNodeName());
    String name = t.toString();
    boolean in = t.inputOnly || t.anyDirection;
    boolean out = t.outputOnly || t.anyDirection;

    String label = params.get("label");
    if (label != null && !label.isEmpty())
      name += " " + label;
    
    // Coordinates should fit within image bounds
    int x = Integer.parseInt(params.getOrDefault("x", "-1"));
    int y = Integer.parseInt(params.getOrDefault("y", "-1"));
    int w = Integer.parseInt(params.getOrDefault("width", "-1"));
    int h = Integer.parseInt(params.getOrDefault("height", "-1"));
		if (x < 0 || y < 0 || w < 1 || h < 1)
      throw new Exception("invalid coordinates or size for I/O resource " + name);
		Bounds r = Bounds.create(x, y, w, h);
    name += "@ ("+x+","+y+")";

    if (params.containsKey("bias") && !in)
      throw new Exception("bias resistors specified for non-input I/O resource " + name);
    InputBias p = in ? InputBias.get(params.get("bias")) : null; // returns non-null for in

    if (t == Type.Pin && params.containsKey("polarity"))
      throw new Exception("polarity specified for Pin I/O resource " + name);
    PinActivity a = (t == Type.Pin) ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.get("polarity"));

    IoStandard s = IoStandard.get(params.get("ioStandard")); // returns non-null
   
    if (params.containsKey("drive") && !out)
      throw new Exception("drive strength specified for non-output I/O resource " + name);
    DriveStrength g = out ? DriveStrength.get(params.get("drive")) : null; // returns non-null for out
    
    if (params.containsKey("default") && !out)
      throw new Exception("defaul drive specified for non-output I/O resource " + name);
    IdleBehavior v = out ? IdleBehavior.get(params.get("idle")) : null; // returns non-null for out

    PinOrdering o = null;
    String[] pins;
		int width;
    if (params.containsKey("pin")) {
      width = 1;
      pins = new String[] { params.get("pin") };
      if (pins[0] == null)
        throw new Exception("missing pin FPGA location for " + name);
    } else {
      String cnt = params.get("n");
      if (cnt == null) {
        int max = -1;
        for (String str : params.keySet()) {
          if (str.length() > 3 && str.startsWith("pin") && Character.isDigit(str.charAt(3))) {
            try {
              max = Math.max(max, Integer.parseInt(str.substring(3)));
            } catch (NumberFormatException ex) {
            }
          }
        } 
        cnt = "" + (max+1);
      }
      if (t.variableWidth) {
        if (cnt == null)
          throw new Exception("missing pin count for " + name);
        width = Integer.parseInt(cnt);
        if (width <= 0)
          throw new Exception("invalid pin count for " + name);
      } else {
        width = t.defaultWidth();
        if (cnt != null && Integer.parseInt(cnt) != width)
          Errors.title("Error").warn("Ignoring invalid pin count in XML for " + name);
      }
      pins = new String[width];
      for (int i = 0; i < width; i++) {
        pins[i] = params.get("pin" + i);
        if (pins[i] == null)
          throw new Exception("missing pin FPGA location " + i + " for " + name);
      }
      if (t.orientable) {
        String desc = params.get("orientation");
        if (desc != null) {
          o = PinOrdering.get(desc);
          if (o == null)
            throw new Exception("Invalid orientation " + desc + " for " + name);
        } else {
          o = defaultPinOrdering(t, r);
        }
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, v, o, pins);
	}

  public static BoardIO parseXmlOld(Element elt) throws Exception {
    
    // fixup old names
    String typeName = elt.getNodeName();
    if (typeName.equalsIgnoreCase("PortIO")) typeName = "BiRibbon";
    else if (type.equalsIgnoreCase("Pin")) typeName = "BiPin";

    Type t = Type.getPhysicalType(typeName);
    if (t == Type.Unknown)
      throw new Exception("unrecognized I/O resource type: " + elt.getNodeName());
    boolean in = t.inputOnly || t.anyDirection; 
    boolean out = t.outputOnly || t.anyDirection;

    Map<String, String> params = XmlUtil.getAttributeMap(elt);

    String label = params.get("Label");
    String name = t.toString();
    if (label != null)
      name += " " + label;
    // Images in old built-in xml files were 740x400. Presumably, so were all
    // other old xml files. So coordinates here are probably in that same
    // coordinate space.
    int x = Integer.parseInt(params.getOrDefault("LocationX", "-1"));
    int y = Integer.parseInt(params.getOrDefault("LocationY", "-1"));
    int w = Integer.parseInt(params.getOrDefault("Width", "-1"));
    int h = Integer.parseInt(params.getOrDefault("Height", "-1"));
		if (x < 0 || y < 0 || w < 1 || h < 1)
      throw new Exception("invalid coordinates or size for I/O resource " + name);
		Bounds r = Bounds.create(x, y, w, h);
    name += "@ ("+x+","+y+")";

    if (params.containsKey("FPGAPinPullBehavior") && !in)
      throw new Exception("bias resistors specified for non-input I/O resource " + name);
    InputBias p = in ? InputBias.get(params.get("FPGAPinPullBehavior")) : null; // returns non-null for in
    
    if (t == Type.Pin && params.containsKey("ActivityLevel"))
      throw new Exception("polarity specified for Pin I/O resource " + name);
    PinActivity a = (t == Type.Pin) ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.get("ActivityLevel"));

    IoStandard s = IoStandard.get(params.get("FPGAPinIOStandard")); // returns non-null

    if (params.containsKey("FPGAPinDriveStrength") && !out)
      throw new Exception("drive strength specified for non-output I/O resource " + name);
    DriveStrength g = out ? DriveStrength.get(params.get("FPGAPinDriveStrength")) : null; // returns non-null for out
    
    IdleBehavior v = out ? IdleBehavior.DEFAULT : null;

    PinOrdering o = null;
    String[] pins;
		int width;
    if (params.containsKey("FPGAPinName")) {
      width = 1;
      pins = new String[] { params.get("FPGAPinName") };
      if (pins[0] == null)
        throw new Exception("missing pin FPGA location for " + name);
    } else {
      String cnt = params.get("NrOfPins");
      if (t.variableWidth) {
        if (cnt == null)
          throw new Exception("missing pin count for " + name);
        width = Integer.parseInt(cnt);
        if (width <= 0)
          throw new Exception("invalid pin count for " + name);
      } else {
        width = t.defaultWidth();
        if (cnt != null && Integer.parseInt(cnt) != width)
          Errors.title("Error").warn("Ignoring invalid pin count in XML for " + name);
      }
      pins = new String[width];
      for (int i = 0; i < width; i++) {
        pins[i] = params.get("FPGAPin_" + i);
        if (pins[i] == null)
          throw new Exception("missing pin FPGA location " + i + " for " + name);
      }
      if (t.orientable) {
        String desc = params.get("Orientation");
        if (desc != null) {
          o = PinOrdering.get(desc);
          if (o == null)
            throw new Exception("Invalid orientation " + desc + " for " + name);
        } else {
          o = defaultPinOrdering(t, r);
        }
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, v, o, pins);
	}

  public boolean canBeInput() { return type.inputOnly || type.anyDirection; }

  public boolean isInputOutput() { return type.anyDirection; }

  // public boolean canBeOutput() { return type.outputOnly || type.anyDirection; ]
  
  @Override
  public String toString() {
    if (!type.physical)
      return label;
    String suffix = label != null ? label : String.format("@(%d, %d)", rect.x, rect.y);
    if (type.orientable) {
      suffix = orientation + " " + suffix;
    if (type == SevenSegmentGang)
      return String.format("%d-digit Seven Segment Display %s", width - 8, suffix);
    else if (type.variableWidth)
      return String.format("%d-bit %s %s", width, type, suffix);
    else
      return type + " " + suffix; // single-bit and other fixed-width types
  }

  // Postcondition: of the counts returned, at least two will be zero.
  public Int3 getPinCounts() {
    Int3 num = new Int3();
    if (type.inputOnly)
      num.in = width;
    else if (type.outputOnly)
      num.out = width;
    else if (type.anyDirection)
      num.inout = width;
    return num;
  }

  // Precondition: of the counts in compWidth, at least two are zero.
  public boolean isCompatible(Int3 compWidth, Type compType) {
    if (compWidth.size() > 1) {
      // Component is multi-bit, such as PortIO, DipSwitch, Keyboard, Tty,
      // RGBLed, SevenSegment, or a multi-bit top-level input or output pin.
      // The generic pin types (represented by InRibbon, OutRibbon) and PortIO
      // (represented by BiRibon) can connect to anything (so long as the
      // directions are compatible), but others must connect to the exactly
      // matching type.
      if (compType != type &&
          compType != Type.InRibbon /* Generic input Pin */ &&
          compType != Type.OutRibbon /* Generic output Pin */  &&
          compType != Type.BiRibbon /* PortIO */)
        return false;
      // Widths must match exactly, directions must be compatible.
      Int3 rsrc = getPinCounts();
      return (compWidth.in > 0 && compWidth.in == (rsrc.in + rsrc.inout))
          || (compWidth.out > 0 && compWidth.out == (rsrc.out + rsrc.inout))
          || (compWidth.inout > 0 && compWidth.inout == rsrc.inout);
    } else {
      // Component is single-bit, such as Button, LED, or single-bit top-level
      // input or output Pin. The generic pin types (represented by InPin and
      // OutPin) can connect to anything (so long as the directions are
      // compatible), but others must connect to the exactly matching type.
      // FIXME: what about 1-bit PortIO?
      if (compType != type &&
          compType != Type.InPin /* Generic input Pin */&& 
          compType != Type.OutPin /* Generic output Pin */)
        return false;
      // Widths must be sufficient, directions must be compatible.
      Int3 rsrc = getPinCounts();
      return (compWidth.in == 1 && 1 <= (rsrc.in + rsrc.inout))
          || (compWidth.out == 1 && 1 <= (rsrc.out + rsrc.inout))
          || (compWidth.inout == 1 && 1 <= rsrc.inout);
    }
  }

  public String[] pinLabels() {
    return type.pinLabels(width);
  }

  public String pinLabel(int bit) {
    return type.pinLabels(width)[bit];
  }

  // TODO: Add orientation attribute for 7segment, and draw pins for it when appropriate.

  public void drawOrientedPins(Graphics2D g, int xOffset, int yOffset, double imgScale,
      Color fill[], Color edge[], Color edgeDefault) {
    if (width <= 1 || orientation == null)
      return;
    //   LRTB   RLBT    BTLR       TBRL       TB   BT   LR        RL
    //   0 1       6    1 3 5      6 4 2 0    0    3    0 1 2 3   3 2 1 0
    //   2 3     5 4    0 2 4 6      5 3 1    1    2
    //   4 5     3 2                          2    1
    //   6       1 0                          3    0
    int gang = orientation.gang;
    int dx = orientation.order.contains("Left-Right") ? +1
      : orientation.order.contains("Right-Left") ? -1 : 0;
    int dy = orientation.order.contains("Top-Bottom") ? +1
      : orientation.order.contains("Bottom-Top") ? -1 : 0;
    int nx, ny;
    boolean horizontal =
      orientation.order.endsWith("Left-Right") || orientation.order.endsWith("Right-Left");
    if (horizontal) {
      nx = (width + gang - 1) / gang;
      ny = gang;
    } else {
      nx = gang;
      ny = (width + gang - 1) / gang;
    }
    int xmargin = 4;
    int xsz = (rect.width-xmargin) / nx;
    if (xsz < 10) { xmargin = 0; xsz = (rect.width) / nx; }
    int ymargin = 4;
    int ysz = (rect.height-ymargin) / ny;
    if (ysz < 10) { ymargin = 0; ysz = (rect.height) / ny; }

    double pinW = (xsz-xmargin/2.0-1)*imgScale;
    double pinH = (ysz-ymargin/2.0-1)*imgScale;
    Font font = fitFont(g, width, pinW, pinH);

    int xx = (dx >= 0 ? rect.x + xmargin/2 : rect.x + rect.width - xmargin - xsz - 1);
    int yy = (dy >= 0 ? rect.y + ymargin/2 : rect.y + rect.height - ymargin - ysz - 1);
    int ix = 0, iy = 0;
    for (int i = 0; i < width; i++) {
      double pinX = xOffset + (xx+dx*((rect.width-xmargin)*ix*1.0/nx)+1)*imgScale;
      double pinY = yOffset + (yy+dy*((rect.height-ymargin)*iy*1.0/ny)+1)*imgScale;
      Shape pinShape = (i == 0)
        ? new Rectangle2D.Double(pinX, pinY, pinW, pinH)
        : new RoundRectangle2D.Double(pinX, pinY, pinW, pinH, Math.min(pinW, pinH), Math.min(pinW, pinH));
      Color pinFill = fill != null ? fill[i] : null;
      if (pinFill != null) {
        g.setColor(pinFill);
        g.fill(pinShape);
      }
      Color pinBorder = edge != null ? edge[i] : edgeDefault;
      if (pinBorder != null) {
        g.setColor(pinBorder);
        g.draw(pinShape);
        drawPinNumber(g, i, font, pinX, pinY, pinW, pinH);
      }
      if (horizontal) {
        iy++;
        if (iy == gang) {
          iy = 0;
          ix++;
        }
      } else {
        ix++;
        if (ix == gang) {
          ix = 0;
          iy++;
        }
      }
    }
  }

  private static Font fitFont(Graphics2D g, int numPins, double pinW, double pinH) {
    String text = ""+(numPins-1);
    float size = 2 + (float)Math.min(pinW, pinH); // start generous
    Font baseFont = new Font("SansSerif", Font.BOLD, 12);
    Font font;
    FontMetrics fm;
    do {
      font = baseFont.deriveFont(size);
      fm = g.getFontMetrics(font);
      size -= 0.5f;
    } while (size > 4 &&
        (fm.stringWidth(text) > pinW || fm.getAscent() + fm.getDescent() > pinH));
    return font;
  }

  private  static void drawPinNumber(Graphics2D g, int i, Font font, double pinX, double pinY, double pinW, double pinH) {
    String text = ""+i;
    Font oldFont = g.getFont();
    g.setFont(font);
    FontMetrics fm = g.getFontMetrics(font);
    int textW = fm.stringWidth(text);
    int textH = fm.getAscent() + fm.getDescent();
    float x = (float)(pinX + (pinW - textW) / 2.0);
    float y = (float)(pinY + (pinH - textH) / 2.0 + fm.getAscent());
    g.drawString(text, x, y);
    g.setFont(oldFont);
  }

}

