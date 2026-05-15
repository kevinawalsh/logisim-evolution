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
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
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

  public static final EnumSet<Type> PhysicalTypes = EnumSet.range(Type.Button, Type.LEDBar);
  public static final EnumSet<Type> InputTypes = EnumSet.range(Type.Button, Type.Ribbon);
  public static final EnumSet<Type> OutputTypes = EnumSet.range(Type.Pin, Type.LED);
  public static final EnumSet<Type> InOutTypes = EnumSet.of(Type.Pin, Type.Ribbon);
  public static final EnumSet<Type> OneBitTypes = EnumSet.of(Type.Button, Type.Pin, Type.LED);

	public static enum Type {
    // Note: The order here matters, because of the EnumSet ranges above.
                   // Physical and synthetic I/O resource characteristics:
    AllZeros,      // synth in  onebit/multibit
    AllOnes,       // synth in  onebit/multibit
    Constant,      // synth in  onebit/multibit
    Button,        // phys  in  onebit
    DIPSwitch,     // phys  in  multibit (degenerates to Button)
    Pin,           // phys  any onebit
    Ribbon,        // phys  any multibit (degenerates to Pin)
		LED,           // phys  out onebit
    RGBLED,        // phys  out multibit (degenerates to LED)
    SevenSegment,  // phys  out multibit (degenerates to LED)
    LEDBar,        // phys  out multibit (degenerates to LED)
    Unconnected,   // synth out onebit/multibit

    Expanded, // only used by PinBindingsDialog as a placeholder 
    Unknown; // only used during parsing as temporary placeholder

    // Note: The types above are used both to describe physical I/O resources
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
      if (str.equalsIgnoreCase("PortIO")) // old name for backwards compatibility
        return Ribbon;
			return Type.Unknown;
		}

    public int defaultWidth() {
      switch (this) {
      case LED:
      case Button:
      case Pin:
        return 1;
      case DIPSwitch:
        return DipSwitch.DEF_SWITCH;
      case Ribbon:
        return PortIO.DEF_IO;
      case RGBLED:
        return 3;
      case SevenSegment:
        return 8;
      case LEDBar:
        return LedBar.DEFAULT_SEGMENTS;
      default:
        return 0;
      }
    }

    public String getDescription() {
      switch (this) {
      case AllZeros: return "Always-zero Input";
      case AllOnes: return "Always-one Input";
      case Constant: return "User-defined Constant Input";
      case Button: return "Button or Switch";
      case DIPSwitch: return "DIP Switch";
      case Pin: return "Bi-directional Pin";
      case Ribbon: return "Bi-directional Bus";
      case LED: return "LED";
      case RGBLED: return "3-wire RGB LED";
      case SevenSegment: return "Seven Segment Display";
      case LEDBar: return "LED Bar";
      case Unconnected: return "Unconnected";
      default: return "Unrecognized Board I/O Resource";
      }
    }

    public String[] pinLabels(int width) {
      switch (this) {
      case SevenSegment:
        return com.cburch.logisim.std.io.SevenSegment.pinLabels();
      case RGBLED:
        return RGBLed.pinLabels();
      default:
        return genericPinLabels(width);
      }
    }

    private String[] genericPinLabels(int width) {
      String[] labels = new String[width];
      if (width == 1)
        labels[0] = "Pin";
      else
        for (int i = 0; i < width; i++)
          labels[i] = "Pin_" + i;
      return labels;
    }

	}

	public final Type type;
	public final int width;
  public final String label;
	public final Bounds rect; // only for physical types
	public final IoStandard standard; // only for physical types
	public final PullBehavior pull; // only for physical types
	public final PinActivity activity; // only for physical types; set to ACTIVE_HIGH for synthetic
	public final DriveStrength strength; // only for physical types
	public final PinOrdering orientation; // only for Ribbon and DIPSwitch so far
  public final int syntheticValue; // only for synthetic types

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
    standard = IoStandard.UNKNOWN;
    pull = PullBehavior.UNKNOWN;
    strength = DriveStrength.UNKNOWN;
    orientation = null;
    pins = null;
  }

  public static BoardIO makeSynthetic(Type t, int w, int val) {
    if (PhysicalTypes.contains(t))
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
      IoStandard s, PullBehavior p, PinActivity a, DriveStrength g, PinOrdering o, String[] x) {
    if (!PhysicalTypes.contains(t))
      throw new IllegalArgumentException("BoardIO type "+t+" is not meant for physical I/O resources");
    type = t;
    width = w;
    label = l;
    rect = r;
    standard = s;
    pull = p;
    activity = a;
    strength = g;
    orientation = o;
    pins = x;
    // rest are defaults/empty
    syntheticValue = 0;
  }

  // constructor for physical I/O resources, copies existing physical but but with new position
  BoardIO(BoardIO io, Bounds newPosition) {
    this(io.type, io.width, io.label, newPosition,
        io.standard, io.pull, io.activity,
        io.strength, io.orientation, io.pins);
  }

  public static BoardIO parseXml(Element elt) throws Exception {
    Type t = Type.getPhysicalType(elt.getNodeName());
    if (t == Type.Unknown)
      throw new Exception("unrecognized I/O resource type: " + elt.getNodeName());
    String name = t.toString();

    Map<String, String> params = XmlUtil.getAttributeMap(elt);

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

    PullBehavior p = PullBehavior.get(params.get("pull"));
    PinActivity a = (t == Type.Pin) ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.get("polarity"));
    IoStandard s = IoStandard.get(params.get("ioStandard"));
    DriveStrength g = DriveStrength.get(params.get("drive"));

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
      if (t == Type.Ribbon || t == Type.DIPSwitch) {
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
      if (t == Type.Ribbon || t == Type.DIPSwitch) {
        String desc = params.get("orientation");
        if (desc != null) {
          o = PinOrdering.get(desc);
          if (o == null)
            throw new Exception("Invalid orientation " + desc + " for " + name);
        } else if (r.width >= r.height) {
          o = t == Type.DIPSwitch ? PinOrdering.ORDER_1_LR : PinOrdering.ORDER_2_BTLR;
        } else {
          o = t == Type.DIPSwitch ? PinOrdering.ORDER_1_TB : PinOrdering.ORDER_2_LRTB;
        }
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, o, pins);
	}

  public static BoardIO parseXmlOld(Element elt) throws Exception {
    Type t = Type.getPhysicalType(elt.getNodeName());
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

    PullBehavior p = PullBehavior.get(params.getOrDefault("FPGAPinPullBehavior", "Unknown"));
    PinActivity a = (t == Type.Pin) ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.getOrDefault("ActivityLevel", "Unknown"));
    IoStandard s = IoStandard.get(params.getOrDefault("FPGAPinIOStandard", "Unknown"));
    DriveStrength g = DriveStrength.get(params.getOrDefault("FPGAPinDriveStrength", "Unknown"));

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
      if (t == Type.Ribbon || t == Type.DIPSwitch) {
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
      if (t == Type.Ribbon || t == Type.DIPSwitch) {
        String desc = params.get("Orientation");
        if (desc != null) {
          o = PinOrdering.get(desc);
          if (o == null)
            throw new Exception("Invalid orientation " + desc + " for " + name);
        } else if (r.width >= r.height) {
          o = t == Type.DIPSwitch ? PinOrdering.ORDER_1_LR : PinOrdering.ORDER_2_BTLR;
        } else {
          o = t == Type.DIPSwitch ? PinOrdering.ORDER_1_TB : PinOrdering.ORDER_2_LRTB;
        }
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, o, pins);
	}

	// public Element encodeXml(Document doc) throws Exception {
  //   Element elt = doc.createElement(type.toString());
  //   elt.setAttribute("LocationX", ""+rect.x);
  //   elt.setAttribute("LocationY", ""+rect.y);
  //   elt.setAttribute("Width", ""+rect.width);
  //   elt.setAttribute("Height", ""+rect.height);
  //   if (label != null)
  //     elt.setAttribute("Label", label);
  //   if (width == 1) {
  //     elt.setAttribute("FPGAPinName", pins[0]);
  //   } else {
  //     elt.setAttribute("NrOfPins", ""+width);
  //     for (int i = 0; i < width; i++)
  //       elt.setAttribute("FPGAPin_"+i, pins[i]);
  //   }
  //   if (strength != DriveStrength.UNKNOWN)
  //     elt.setAttribute("FPGAPinDriveStrength", ""+strength);
  //   if (activity != PinActivity.UNKNOWN && type != Type.Pin) // skip Pin
  //     elt.setAttribute("ActivityLevel", ""+activity);
  //   if (pull != PullBehavior.UNKNOWN)
  //     elt.setAttribute("FPGAPinPullBehavior", ""+pull);
  //   if (standard != IoStandard.UNKNOWN && standard != IoStandard.DEFAULT)
  //     elt.setAttribute("FPGAPinIOStandard", ""+standard);
  //   if (orientation != null)
  //     elt.setAttribute("Orientation", ""+orientation);
  //   return elt;
  // }

	// public static BoardIO makeUserDefined(Type t, Bounds r, BoardEditor parent) {
  //   int w = t.defaultWidth();
  //   BoardIO template = new BoardIO(t, w, null/*no label*/, r,
  //       defaultStandard, defaultPull, defaultActivity, defaultStrength, null /* no orientation */, null /*no pins*/);
  //   if (t == Type.DIPSwitch || t == Type.Ribbon)
  //     template = doSizeDialog(template, parent);
  //   if (template == null)
  //     return null;
  //   return doInfoDialog(template, parent, false);
  // }

  // public static BoardIO redoUserDefined(BoardIO io, BoardEditor parent) {
  //   BoardIO template = io;
  //   if (template.type == Type.DIPSwitch || template.type == Type.Ribbon)
  //     template = doSizeDialog(template, parent);
  //   if (template == null)
  //     return io; // user cancelled before getting to config dialog
  //   return doInfoDialog(template, parent, true);
  // }

  // private static BoardIO doSizeDialog(BoardIO t, BoardEditor parent) {
  //   int min = t.type == Type.DIPSwitch ? DipSwitch.MIN_SWITCH : PortIO.MIN_IO;
  //   int max = t.type == Type.DIPSwitch ? DipSwitch.MAX_SWITCH : PortIO.MAX_IO;

  //   final JDialog dlg = new JDialog(parent, t.type + " Size");
  //   dlg.getContentPane().setLayout(new BoxLayout(dlg.getContentPane(), BoxLayout.PAGE_AXIS));

  //   String things = t.type == Type.DIPSwitch ? "Switches" : "Pins";
  //   JLabel question = new JLabel("Number of " + things + " for " + t.type + ":");

  //   JComboBox<Integer> size = new JComboBox<>();
  //   for (int i = min; i <= max; i++)
  //     size.addItem(i);
  //   size.setSelectedItem(t.width);

  //   JLabel question2 = new JLabel("Orientation:");
  //   JComboBox<String> layout = new JComboBox<>();
  //   for (PinOrdering po : PinOrdering.OPTIONS)
  //     layout.addItem(po.desc);
  //   if (t.orientation != null)
  //     layout.setSelectedItem(t.orientation.desc);
  //   else if (t.rect.width >= t.rect.height)
  //     layout.setSelectedItem(t.type == Type.DIPSwitch ? PinOrdering.ORDER_1_LR : PinOrdering.ORDER_2_BTLR);
  //   else
  //     layout.setSelectedItem(t.type == Type.DIPSwitch ? PinOrdering.ORDER_1_TB : PinOrdering.ORDER_2_LRTB);

  //   final int[] width = new int[] { -1 };
  //   final PinOrdering[] orientation = new PinOrdering[] { null };
  //   JButton next = new JButton("Next");
  //   next.addActionListener(e -> {
  //     width[0] = (Integer)size.getSelectedItem();
  //     orientation[0] = PinOrdering.get((String)layout.getSelectedItem());
  //     dlg.setVisible(false);
  //   });

  //   JPanel options = new JPanel();
  //   options.setLayout(new BoxLayout(options, BoxLayout.LINE_AXIS));
  //   options.add(question);
  //   options.add(size);
  //   JPanel options2 = new JPanel();
  //   options2.setLayout(new BoxLayout(options2, BoxLayout.LINE_AXIS));
  //   options2.add(question2);
  //   options2.add(layout);

  //   dlg.add(options);
  //   dlg.add(options2);
  //   dlg.add(next);

  //   parent.doModal(dlg, t.rect.x + t.rect.width/2, t.rect.y);

  //   if (width[0] < 0) // cancelled
  //     return null;
  //   if (t.width == width[0] && t.orientation == orientation[0])
  //     return t; // no change
  //   return new BoardIO(t.type, width[0], t.label,
  //       t.rect, t.standard, t.pull, t.activity, t.strength, orientation[0], t.pins);
  // }

  private static DriveStrength defaultStrength = DriveStrength.DEFAULT;
  private static PinActivity defaultActivity = PinActivity.ACTIVE_HIGH;
  private static IoStandard defaultStandard = IoStandard.DEFAULT;
  private static PullBehavior defaultPull = PullBehavior.FLOAT;

  // private static void add(JDialog dlg, GridBagConstraints c,
  //     String caption, JComponent input) {
  //   JLabel label = new JLabel(caption + " ");
  //   label.setAlignmentX(1f);
  //   dlg.add(label, c);
  //   c.gridx++;
  //   dlg.add(input, c);
  //   c.gridx--;
  //   c.gridy++;
  // }

  // private static BoardIO doInfoDialog(BoardIO t, BoardEditor parent, boolean removable) {
  //   final JDialog dlg = new JDialog(parent, t.type + " Properties");
  //   dlg.setLayout(new GridBagLayout());
  //   GridBagConstraints c = new GridBagConstraints();
  //   c.fill = GridBagConstraints.HORIZONTAL;

  //   JComboBox<IoStandard> standard = new JComboBox<>(IoStandard.OPTIONS);
  //   JComboBox<DriveStrength> strength = new JComboBox<>(DriveStrength.OPTIONS);
  //   JComboBox<PinActivity> activity = new JComboBox<>(PinActivity.OPTIONS);
  //   JComboBox<PullBehavior> pull = new JComboBox<>(PullBehavior.OPTIONS);

  //   standard.setSelectedItem(t.standard);
  //   strength.setSelectedItem(t.strength);
  //   activity.setSelectedItem(t.activity);
  //   pull.setSelectedItem(t.pull);

  //   JTextField x = new JTextField(6);
  //   JTextField y = new JTextField(6);
  //   JTextField w = new JTextField(6);
  //   JTextField h = new JTextField(6);
  //   x.setText(""+t.rect.x);
  //   y.setText(""+t.rect.y);
  //   w.setText(""+t.rect.width);
  //   h.setText(""+t.rect.height);

  //   if (!OutputTypes.contains(t.type))
  //     strength = null;
  //   if (!InputTypes.contains(t.type) || t.type == Type.Pin)
  //     pull = null;
  //   if (InOutTypes.contains(t.type))
  //     activity = null;

  //   JTextField label = new JTextField(6);
  //   if (t.label != null && t.label.length() > 0)
  //     label.setText(t.label);

  //   String[] pinLabels = t.type.pinLabels(t.width);
  //   JTextField[] pinLocs = new JTextField[t.width];
  //   for (int i = 0; i < t.width; i++) {
  //     pinLocs[i] = new JTextField(6);
  //     if (t.pins != null && t.pins[i] != null && t.pins[i].length() > 0)
  //       pinLocs[i].setText(t.pins[i]);
  //   }

  //   c.gridx = 0;
  //   c.gridy = 0;

  //   for (int i = 0; i < t.width; i++) {
  //     add(dlg, c, "FPGA location for " + pinLabels[i] + " :", pinLocs[i]);
  //     if (c.gridy == 8) {
  //       c.gridx += 2;
  //       c.gridy = 0;
  //     }
  //   }

  //   c.gridx = 0;
  //   c.gridy = Math.max(t.width, 32);

  //   add(dlg, c, "Label (optional):", label);
  //   add(dlg, c, "Geometry x coordinate:", x);
  //   add(dlg, c, "Geometry y coordinate:", y);
  //   add(dlg, c, "Geometry width:", w);
  //   add(dlg, c, "Geometry height:", h);
  //   add(dlg, c, "I/O standard:", standard);
  //   if (strength != null)
  //     add(dlg, c, "Drive strength:", strength);
  //   if (pull != null)
  //     add(dlg, c, "Pull behavior:", pull);
  //   if (activity != null)
  //     add(dlg, c, "Signal activity:", activity);

  //   final char[] result = new char[] { 'S' };

  //   if (removable) {
  //     JButton remove = new JButton("Delete Resource");
  //     remove.addActionListener(e -> {
  //       result[0] = 'R';
  //       dlg.setVisible(false);
  //     });
  //     dlg.add(remove, c);
  //     c.gridx++;
  //   }

  //   JButton cancel = new JButton("Cancel");
  //   cancel.addActionListener(e -> {
  //     result[0] = 'C';
  //     dlg.setVisible(false);
  //   });
  //   dlg.add(cancel, c);
  //   c.gridx++;

  //   JButton ok = new JButton("Save");
  //   ok.addActionListener(e -> dlg.setVisible(false));
  //   dlg.add(ok, c);
  //   c.gridx++;

  //   String[] pins = new String[t.width];
  //   int xx, yy, ww, hh;
  //   parent.doModal(dlg, t.rect.x+t.rect.width/2, t.rect.y);
  //   for (;;) {
  //     if (result[0] == 'R')
  //       return null; // user removed resource
  //     if (result[0] == 'C' && removable)
  //       return t; // cancelled, but explicitly not removed, keep same one
  //     if (result[0] == 'C')
  //       return null; // user cancelled, we were adding a new one, so don't add it
  //     // ensure all locations are specified
  //     boolean missing = false;
  //     for (int i = 0; i < t.width && !missing; i++) {
  //       pins[i] = pinLocs[i].getText();
  //       missing = pins[i] == null || pins[i].isEmpty();
  //     }
  //     if (missing) {
  //       Errors.title("Error").show("Please specify an FPGA location for all pins.");
  //       dlg.setVisible(true);
  //       continue;
  //     }
  //     try {
  //       xx = Integer.parseInt(x.getText());
  //       yy = Integer.parseInt(y.getText());
  //       ww = Integer.parseInt(w.getText());
  //       hh = Integer.parseInt(h.getText());
  //     } catch (NumberFormatException ex) {
  //       Errors.title("Error").show("Error parsing geometry.", ex);
  //       dlg.setVisible(true);
  //       continue;
  //     }
  //     if (xx < 0 || yy < 0 || ww <= 0 || hh <= 0
  //         || xx+ww >= Board.STD_IMG_WIDTH || yy+hh >= Board.STD_IMG_HEIGHT) {
  //       Errors.title("Error").show("Invalid geometry.");
  //       dlg.setVisible(true);
  //       continue;
  //     }
  //     break;
  //   }

  //   String txt = label.getText();
  //   if (txt != null && txt.length() == 0)
  //     txt = null;

  //   defaultStandard = (IoStandard)standard.getSelectedItem();
  //   if (pull != null)
  //     defaultPull = (PullBehavior)pull.getSelectedItem();
  //   if (activity != null)
  //     defaultActivity = (PinActivity)activity.getSelectedItem();
  //   if (strength != null)
  //     defaultStrength = (DriveStrength)strength.getSelectedItem();

  //   PinActivity a = activity != null ? defaultActivity : PinActivity.UNKNOWN;
  //   if (t.type == Type.Pin)
  //     a = PinActivity.ACTIVE_HIGH; // special case: Pin is always active high

  //   Bounds rect = Bounds.create(xx, yy, ww, hh);

  //   return new BoardIO(t.type, t.width, txt, rect, defaultStandard,
  //       pull != null ? defaultPull : PullBehavior.UNKNOWN,
  //       a,
  //       strength != null ? defaultStrength : DriveStrength.UNKNOWN,
  //       t.orientation,
  //       pins);
  // }

  public boolean isInput() {
    return InputTypes.contains(type);
  }

  public boolean isInputOutput() {
    return InOutTypes.contains(type);
  }

  public boolean isOutput() {
    return OutputTypes.contains(type);
  }
  
  @Override
  public String toString() {
    if (!PhysicalTypes.contains(type))
      return label;
    String suffix = label != null ? label : String.format("@(%d, %d)", rect.x, rect.y);
    if (type == Type.DIPSwitch || type == Type.Ribbon) // types with variable size
      return String.format("%d-bit %s %s", width, type, suffix);
    else
      return type + " " + suffix; // single-bit and other fixed-width types
  }

  // Postcondition: of the counts returned, at least two will be zero.
  public Int3 getPinCounts() {
    Int3 num = new Int3();
    switch (type) {
    case Button:
    case DIPSwitch:
    case AllZeros:
    case AllOnes:
    case Constant:
      num.in = width;
      break;
    case Pin:
    case Ribbon:
      num.inout = width;
      break;
    case LED:
    case RGBLED:
    case SevenSegment:
    case LEDBar:
    case Unconnected:
      num.out = width;
      break;
    }
    return num;
  }

  // Precondition: of the counts in compWidth, at least two are zero.
  public boolean isCompatible(Int3 compWidth, Type compType) {
    if (compWidth.size() > 1) {
      // Component is multi-bit, such as PortIO, DipSwitch, Keyboard, Tty,
      // RGBLed, SevenSegment, or a multi-bit top-level input or output Ribbon.
      // Ribbon can connect to anything (so long as the directions are
      // compatible), but others must connect to the exactly matching type.
      if (compType != Type.Ribbon && compType != type)
        return false;
      // Widths must match exactly, directions must be compatible.
      Int3 rsrc = getPinCounts();
      return (compWidth.in > 0 && compWidth.in == (rsrc.in + rsrc.inout))
          || (compWidth.out > 0 && compWidth.out == (rsrc.out + rsrc.inout))
          || (compWidth.inout > 0 && compWidth.inout == rsrc.inout);
    } else {
      // Component is single-bit, such as Button, LED, or single-bit top-level
      // input or output Pin. Pin can connect to anything (so long as the
      // directions are compatible), but others must connect to the exactly
      // matching type.
      if (compType != Type.Pin && compType != type)
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

  public void drawOrientedPins(Graphics g, int xOffset, int yOffset, double imgScale,
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
    int margin = 4;
    int sz = Math.min((rect.width-margin) / nx, (rect.height-margin) / ny);
    if (sz < 10) {
      margin = 0;
      sz = Math.min((rect.width) / nx, (rect.height) / ny);
    }
    int xx = (dx >= 0 ? rect.x + margin/2 : rect.x + rect.width - margin - sz - 1);
    int yy = (dy >= 0 ? rect.y + margin/2 : rect.y + rect.height - margin - sz - 1);
    int ix = 0, iy = 0;
    for (int i = 0; i < width; i++) {
      Rectangle2D boxy = new Rectangle2D.Double(
          xOffset + (xx+1)*imgScale, yOffset + (yy+1)*imgScale,
          imgScale*(sz-margin/2.0), imgScale*(sz-margin/2.0));
      Ellipse2D oval = new Ellipse2D.Double(
          xOffset + (xx+dx*((rect.width-margin)*ix*1.0/nx)+1)*imgScale,
          yOffset + (yy+dy*((rect.height-margin)*iy*1.0/ny)+1)*imgScale,
          imgScale*(sz-margin/2.0-1), imgScale*(sz-margin/2.0-1));
      if (fill != null && fill[i] != null) {
        g.setColor(fill[i]);
        if (i == 0) ((Graphics2D)g).fill(boxy);
        else ((Graphics2D)g).fill(oval);
      }
      if (edge != null ? edge[i] != null : edgeDefault != null) {
        g.setColor(edge != null ? edge[i] : edgeDefault);
        if (i == 0) ((Graphics2D)g).draw(boxy);
        else ((Graphics2D)g).draw(oval);
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

