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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
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

    // name           description,                          assn,           reality,   orient, dir,      min/max/def width,  degneratesTo
    AllZeros         ("Always-zero Input",                  ASSIGNABLE.YES, REALITY.SYNTHETIC, ORIENTABLE.NO,  DIR.IN,   -1, -1, -1,         null),
    AllOnes          ("Always-one Input",                   ASSIGNABLE.YES, REALITY.SYNTHETIC, ORIENTABLE.NO,  DIR.IN,   -1, -1, -1,         null),
    Constant         ("User-defined Constant Input",        ASSIGNABLE.YES, REALITY.SYNTHETIC, ORIENTABLE.NO,  DIR.IN,   -1, -1, -1,         null),
    Unconnected      ("Unconnected",                        ASSIGNABLE.YES, REALITY.SYNTHETIC, ORIENTABLE.NO,  DIR.OUT,  -1, -1, -1,         null),

    InReserved       ("Reserved Input Pin",                 ASSIGNABLE.NO,  REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.IN,   1, 32, 1,           null),
    OutReserved      ("Reserved Output Pin",                ASSIGNABLE.NO,  REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.OUT,  1, 32, 1,           null),

    InPin            ("Generic Input Pin",                  ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.IN,   1, 1, 1,            null),
    BiPin            ("Generic Bidirectional Pin",          ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.BI,   1, 1, 1,            null),
    OutPin           ("Generic Output Pin",                 ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.OUT,  1, 1, 1,            null),
    InRibbon         ("Input Bus or Ribbon Cable",          ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.IN,   1, 32, 8,           InPin),
    BiRibbon         ("Bidirectional Bus or Ribbon Cable",  ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.BI,   PortIO.MIN_IO, PortIO.MAX_IO, PortIO.DEF_IO, BiPin),
    OutRibbon        ("Output Bus or Ribbon Cable",         ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.OUT,  1, 32, 8,           OutPin),

    Button           ("Button or Switch",                   ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.IN,   1, 1, 1,            InPin),
    DIPSwitch        ("DIP Switch",                         ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.IN,   DipSwitch.MIN_SWITCH, DipSwitch.MAX_SWITCH, DipSwitch.DEF_SWITCH, Button),

    LED              ("LED",                                ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.OUT,  1, 1, 1,            OutPin),
    RGBLED           ("3-wire RGB LED",                     ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.NO,  DIR.OUT,  3, 3, 3,            LED),
    SevenSegment     ("Seven Segment Display",              ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.OUT,  8, 8, 8,            LED),
    SevenSegmentGang ("Seven Segment Multi-Digit Display",  ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.OUT,  8+2, 8+com.cburch.logisim.std.io.SevenSegment.MAX_DIGITS, 8+4, null /* degneratesTo: special case */),
    LEDBar           ("LED Bar",                            ASSIGNABLE.YES, REALITY.PHYSICAL,  ORIENTABLE.YES, DIR.OUT,  LedBar.MIN_SEGMENTS, LedBar.MAX_SEGMENTS, LedBar.DEFAULT_SEGMENTS, LED),

    Expanded         ("Expanded", null, null, null, null, -1, -1, -1, null),  // only used by PinBindingsDialog as a placeholder 
    Unknown          ("Unknown", null, null, null, null, -1, -1, -1, null);  // only used during parsing as temporary placeholder

    private enum ASSIGNABLE { YES, NO }
    private enum REALITY { PHYSICAL, SYNTHETIC }
    private enum ORIENTABLE  { YES, NO }
    private enum DIR     { IN, OUT, BI }

    public final String description;
    public final boolean assignable;
    public final boolean physical, synthetic;
    public final boolean orientable;
    public final boolean inputOnly, outputOnly, anyDirection, canInput, canOutput;
    public final int minWidth, maxWidth, defWidth;
    public final boolean oneBit; // min = max = def = 1
    public final boolean variableWidth; // min != max
    private final Type degeneratesTo;

    Type(String descr, ASSIGNABLE assignable, REALITY reality, ORIENTABLE orientable, DIR dir, int minWidth, int maxWidth, int defWidth, Type degeneratesTo) {
      this.description = descr;
      this.assignable = (assignable == ASSIGNABLE.YES);
      this.physical = (reality == REALITY.PHYSICAL);
      this.synthetic = (reality == REALITY.SYNTHETIC);
      this.orientable = (orientable == ORIENTABLE.YES);
      this.inputOnly = (dir == DIR.IN);
      this.outputOnly = (dir == DIR.OUT);
      this.anyDirection = (dir == DIR.BI);
      this.canInput = inputOnly || anyDirection;
      this.canOutput = outputOnly || anyDirection;
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

    // In/Out/BiPin, In/Out/BiRibbon can't have polarity... why?? FIXME
    public boolean alwaysActiveHigh() {
      return this == Type.InPin || this == Type.OutPin || this == Type.BiPin ||
        this == Type.InRibbon || this == Type.OutRibbon || this == Type.BiRibbon;
    }

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
          case SevenSegment: return PinOrdering.ORDER_1_BT;
          case SevenSegmentGang: return PinOrdering.ORDER_1_LR;
          case DIPSwitch: return PinOrdering.ORDER_1_LR;
          case LEDBar: return PinOrdering.ORDER_1_RL;
          case InRibbon:
          case OutRibbon:
          case BiRibbon: return PinOrdering.ORDER_2_BTLR;
          default: return null;
        }
      } else {
        switch (this) {
          case SevenSegment: return PinOrdering.ORDER_1_LR;
          case DIPSwitch: return PinOrdering.ORDER_1_BT;
          case LEDBar: return PinOrdering.ORDER_1_BT;
          case InRibbon:
          case OutRibbon:
          case BiRibbon: return PinOrdering.ORDER_2_LRTB;
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

  private static EnumSet<Type> deriveSet(java.util.function.Predicate<Type> pred) {
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

    if (params.containsKey("bias") && !t.canInput)
      throw new Exception("bias resistors specified for non-input I/O resource " + name);
    InputBias p = t.canInput ? InputBias.get(params.get("bias")) : null; // returns non-null for in

    if (t.alwaysActiveHigh() && params.containsKey("polarity"))
      throw new Exception("polarity specified for Pin I/O resource " + name);
    PinActivity a = t.alwaysActiveHigh() ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.get("polarity"));

    IoStandard s = IoStandard.get(params.get("ioStandard")); // returns non-null

    if (params.containsKey("drive") && !t.canOutput)
      throw new Exception("drive strength specified for non-output I/O resource " + name);
    DriveStrength g = t.canOutput ? DriveStrength.get(params.get("drive")) : null; // returns non-null for out

    if (params.containsKey("default") && !t.canOutput)
      throw new Exception("defaul drive specified for non-output I/O resource " + name);
    IdleBehavior v = t.canOutput ? IdleBehavior.get(params.get("idle")) : null; // returns non-null for out

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
        width = t.defWidth;
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
          o = t.defaultPinOrdering(r);
        }
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, v, o, pins);
	}

  public static BoardIO parseXmlOld(Element elt) throws Exception {
    
    // fixup old names
    String typeName = elt.getNodeName();
    if (typeName.equalsIgnoreCase("PortIO")) typeName = "BiRibbon";
    else if (typeName.equalsIgnoreCase("Pin")) typeName = "BiPin";

    Type t = Type.getPhysicalType(typeName);
    if (t == Type.Unknown)
      throw new Exception("unrecognized I/O resource type: " + elt.getNodeName());

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

    if (params.containsKey("FPGAPinPullBehavior") && !t.canInput)
      throw new Exception("bias resistors specified for non-input I/O resource " + name);
    InputBias p = t.canInput ? InputBias.get(params.get("FPGAPinPullBehavior")) : null; // returns non-null for in
   
    // FIXME: why can't the Pin types have a polarity? Or what about ribbon?
    if (t.alwaysActiveHigh() && params.containsKey("ActivityLevel"))
      throw new Exception("polarity specified for Pin I/O resource " + name);
    PinActivity a = t.alwaysActiveHigh() ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.get("ActivityLevel"));

    IoStandard s = IoStandard.get(params.get("FPGAPinIOStandard")); // returns non-null

    if (params.containsKey("FPGAPinDriveStrength") && !t.canOutput)
      throw new Exception("drive strength specified for non-output I/O resource " + name);
    DriveStrength g = t.canOutput ? DriveStrength.get(params.get("FPGAPinDriveStrength")) : null; // returns non-null for out
    
    IdleBehavior v = t.canOutput ? IdleBehavior.DEFAULT : null;

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
        width = t.defWidth;
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
          o = t.defaultPinOrdering(r);
        }
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, v, o, pins);
	}

  public boolean canBeInput() { return type.inputOnly || type.anyDirection; }

  public boolean isInputOutput() { return type.anyDirection; }
  
  @Override
  public String toString() {
    if (!type.physical)
      return label;
    String suffix = label != null ? label : String.format("@(%d, %d)", rect.x, rect.y);
    if (type.orientable)
      suffix = orientation + " " + suffix;
    if (type == Type.SevenSegmentGang)
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

  public void drawOrientedPins(Graphics2D g, int xOffset, int yOffset, double imgScale,
      Color fill[], Color edge[], Color edgeDefault) {
    if (width <= 1 || orientation == null)
      return;
    boolean seg7 = (type == Type.SevenSegment || type == Type.SevenSegmentGang);
    int gang = seg7 ? 1 : orientation.gang;
    // SevenSegment and SevenSegmentGang:
    //  LR           RL              TB               BT
    //  .--A--.      (P) .--D--.     ,--E--,--F--,    
    //  F     B          C     E     D     G     A              (P)
    //  |--G--|          |--G--|     '--C--'--B--'    ,--B--,--C--,
    //  E     C          B     F     (P)              A     G     D     
    //  '--D--' (P)      '--A--'                      '--F--'--E--'
    //
    // Ribbon, DipSwitch, LEDBar:
    //   LRTB   RLBT    BTLR       TBRL       TB   BT   LR        RL
    //   0 1       6    1 3 5      6 4 2 0    0    3    0 1 2 3   3 2 1 0
    //   2 3     5 4    0 2 4 6      5 3 1    1    2
    //   4 5     3 2                          2    1
    //   6       1 0                          3    0
    int dx = orientation.order.contains("Left-Right") ? +1
      : orientation.order.contains("Right-Left") ? -1 : 0;
    int dy = orientation.order.contains("Top-Bottom") ? +1
      : orientation.order.contains("Bottom-Top") ? -1 : 0;
    boolean horizontal =
      orientation.order.endsWith("Left-Right") || orientation.order.endsWith("Right-Left");
    if (seg7) {
      int digits = (width == 8) ? 1 : width - 8;
      int nx, ny;
      if (horizontal) {
        nx = digits;
        ny = 1;
      } else {
        nx = 1;
        ny = digits;
      }
      // digit box size is (dxsz, dysz)
      double dxsz = rect.width * 1.0 / nx;
      double dysz = rect.height * 1.0 / ny;
      // horizontal segment (A, D, G) and vertical segment sizes (B, C, E, F)
      double hslen, vslen, segwidth;
      if (horizontal) {
        segwidth = Math.min(0.125 * dysz, 0.125 * dxsz);
        hslen = Math.max(segwidth*1.5, 0.45 * dxsz);
        vslen = Math.max(segwidth*1.5, 0.30 * dysz);
      } else {
        segwidth = Math.min(0.125 * dxsz, 0.125 * dysz);
        hslen = Math.max(segwidth*1.5, 0.45 * dysz);
        vslen = Math.max(segwidth*1.5, 0.30 * dxsz);
      }
      double[][] layout = new double[][] { // {cx, cy, angle}, ...
        {0.56, 0.1025, 0},    // A
        {0.85, 0.30125, 100}, // B
        {0.80, 0.69875, 100}, // C
        {0.44, 0.8975, 0},    // D
        {0.15, 0.69875, 100}, // E
        {0.20, 0.30125, 100}, // F
        {0.52, 0.500, 0},     // G
        {0.8975, 0.8975, 0},  // DP
      };
      String[] labels = {"A", "B", "C", "D", "E", "F", "G", "DP" };
      Font font = fitFont(g, 1, segwidth*imgScale, segwidth*imgScale);
      double xx = (dx >= 0 ? rect.x + dxsz / 2 : rect.x + rect.width - dxsz / 2 - 1);
      double yy = (dy >= 0 ? rect.y + dysz / 2 : rect.y + rect.height - dysz / 2- 1);
      int ix = 0, iy = 0;
      double rot;
      if (dx == +1) rot = 0;
      else if (dy == +1) rot = 90;
      else if (dx == -1) rot = 180;
      else rot = 270;
      for (int digit = 0; digit < digits; digit++) {
        double cX = xOffset + (xx+rect.width*ix*1.0/nx)*imgScale;
        double cY = yOffset + (yy+rect.height*iy*1.0/ny)*imgScale;
        for (int i = 0; i < 8; i++) {
          double[] digitLayout = layout[i];
          double seglen = i == 7 ? segwidth : digitLayout[2] == 0 ? hslen : vslen;
          Color pinFill = fill != null ? fill[i] : null;
          if (pinFill != null) {
            g.setColor(pinFill);
            segmentShape(g, true, digitLayout, seglen*imgScale, segwidth*imgScale, rot,
                cX, cY, dxsz*imgScale, dysz*imgScale, null, null);
          }
          Color pinBorder = edge != null ? edge[i] : edgeDefault;
          if (pinBorder != null) {
            g.setColor(pinBorder);
            segmentShape(g, false, digitLayout, seglen*imgScale, segwidth*imgScale, rot,
                cX, cY, dxsz*imgScale, dysz*imgScale, labels[i], font);
          }
        }
        if (digits > 1) {
          int i = 8 + digit;
          Color pinFill = fill != null ? fill[i] : null;
          if (pinFill != null) {
            g.setColor(pinFill);
            digitEnableShape(g, true, 2.5*segwidth*imgScale, rot, cX, cY, dxsz*imgScale, dysz*imgScale, null, null);
          }
          Color pinBorder = edge != null ? edge[i] : edgeDefault;
          if (pinBorder != null) {
            g.setColor(pinBorder);
            digitEnableShape(g, false, 2.5*segwidth*imgScale, rot, cX, cY, dxsz*imgScale, dysz*imgScale, ""+(1+digit), font);
          }
        }
        ix += dx;
        iy += dy;
      }
    } else {
      int nx, ny;
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
    drawPinLabel(g, ""+i, font, pinX + pinW/2, pinY + pinH/2);
  }

  private  static void drawPinLabel(Graphics2D g, String text, Font font, double pinCX, double pinCY) {
    Font oldFont = g.getFont();
    g.setFont(font);
    FontMetrics fm = g.getFontMetrics(font);
    int textW = fm.stringWidth(text);
    int textH = fm.getAscent() + fm.getDescent();
    float x = (float)(pinCX - textW / 2.0);
    float y = (float)(pinCY - textH / 2.0 + fm.getAscent());
    g.drawString(text, x, y);
    g.setFont(oldFont);
  }

  // Draws one segment of a seven-segment display as a rounded rectangle.
  // layout[] = {rx, ry, angle}: relative position within digit box [0,1] and
  // orientation angle in degrees (0=horizontal, 90=vertical). rot applies an
  // overall CW rotation (0/90/180/270) to the entire digit layout.
  private static void segmentShape(Graphics2D g, boolean fill, double[] layout,
      double seglen, double segwidth, double rot,
      double cX, double cY, double dxsz, double dysz, String label, Font font) {
    double rx = layout[0], ry = layout[1], angle = layout[2];
    double relX, relY;
    switch ((int) rot) {
      case 90:  relX = 1.0 - ry; relY = rx;        angle += 90;  break;
      case 180: relX = 1.0 - rx; relY = 1.0 - ry;  angle += 180; break;
      case 270: relX = ry;       relY = 1.0 - rx;  angle += 270; break;
      default:  relX = rx;       relY = ry;                      break;
    }
    double segCX = cX + (relX - 0.5) * dxsz;
    double segCY = cY + (relY - 0.5) * dysz;
    AffineTransform saved = g.getTransform();
    g.translate(segCX, segCY);
    g.rotate(Math.toRadians(angle));
    Shape seg = new RoundRectangle2D.Double(
        -seglen / 2, -segwidth / 2, seglen, segwidth, segwidth, segwidth);
    if (fill)
      g.fill(seg);
    else
      g.draw(seg);
    g.setTransform(saved);
    if (!fill && label != null && font != null)
      drawPinLabel(g, label, font, segCX, segCY);
  }

  // Draws a triangle and label in the corner of a seven-segment digit bounding
  // box.
  private static void digitEnableShape(Graphics2D g, boolean fill, double legLen, double rot,
      double cX, double cY, double dxsz, double dysz, String label, Font font) {
    double cornerX, cornerY, legDX, legDY;
    switch ((int) rot) {
      case 90:  cornerX = cX + dxsz / 2; cornerY = cY - dysz / 2; legDX = -1; legDY = +1; break;
      case 180: cornerX = cX + dxsz / 2; cornerY = cY + dysz / 2; legDX = -1; legDY = -1; break;
      case 270: cornerX = cX - dxsz / 2; cornerY = cY + dysz / 2; legDX = +1; legDY = -1; break;
      default:  cornerX = cX - dxsz / 2; cornerY = cY - dysz / 2; legDX = +1; legDY = +1; break;
    }
    Path2D.Double tri = new Path2D.Double();
    tri.moveTo(cornerX, cornerY);
    tri.lineTo(cornerX + legDX * legLen, cornerY);
    tri.lineTo(cornerX, cornerY + legDY * legLen);
    tri.closePath();
    if (fill)
      g.fill(tri);
    else
      g.draw(tri);
    if (!fill && label != null && font != null)
      drawPinLabel(g, label, font,
          cornerX + legDX * legLen / 3.0,
          cornerY + legDY * legLen / 3.0);
  }

}

