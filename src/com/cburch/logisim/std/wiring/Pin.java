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

package com.cburch.logisim.std.wiring;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.geom.Line2D;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.circuit.appear.DynamicValueProvider;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.Icons;

public class Pin extends InstanceFactory implements DynamicValueProvider {

  @SuppressWarnings("serial")
  private static class EditDecimal extends JDialog implements KeyListener {

    private JFormattedTextField text;
    private int bitWidth;
    PinState pinState;
    InstanceState state;
    RadixOption radix;
    boolean tristate;
    private static final Color VALID_COLOR = new Color(0xff, 0xf0, 0x99);
    private static final Color INVALID_COLOR = new Color(0xff, 0x66, 0x66);

    public EditDecimal(InstanceState state) {
      super();
      this.state = state;
      radix = state.getAttributeValue(RadixOption.ATTRIBUTE);
      pinState = getState(state);
      Value value = pinState.intendedValue;
      bitWidth = value.getWidth();
      PinAttributes attrs = (PinAttributes) state.getAttributeSet();
      tristate = (attrs.behavior == TRISTATE);

      setTitle(S.get(radix == RadixOption.RADIX_10_SIGNED
            ? "pinEditSignedDecimalTitle"
            : "pinEditSignedDecimalTitle"));
      GridBagConstraints gbc = new GridBagConstraints();
      final JButton ok = new JButton(S.get("okOption"));
      final JButton cancel = new JButton(S.get("cancelOption"));
      ok.addActionListener((ev) -> accept());
      cancel.addActionListener((ev) -> EditDecimal.this.setVisible(false));
      addWindowFocusListener(new WindowFocusListener() {
        public void windowLostFocus(WindowEvent e) {
          EditDecimal.this.setVisible(false);
        }
        public void windowGainedFocus(WindowEvent e) { }
      });
      setLayout(new GridBagLayout());

      text = new JFormattedTextField();
      text.setFont(DEFAULT_FONT);
      text.setColumns(11);
      text.setText(value.toDecimalString(radix == RadixOption.RADIX_10_SIGNED));
      text.selectAll();

      text.getDocument().addDocumentListener(new DocumentListener() {
        public void insertUpdate(DocumentEvent e) {
          String s = text.getText();
          if (isEditValid(s)) {
            text.setBackground(VALID_COLOR);
            ok.setEnabled(true);
          } else {
            text.setBackground(INVALID_COLOR);
            ok.setEnabled(false);
          }

        }
        public void removeUpdate(DocumentEvent e) {
          insertUpdate(e);
        }
        public void changedUpdate(DocumentEvent e) { }
      });
      text.addKeyListener(this);
      text.setBorder(BorderFactory.createCompoundBorder(
            text.getBorder(),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)));
      text.setBackground(VALID_COLOR);

      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;
      gbc.insets = new Insets(8, 4, 8, 4);
      gbc.gridx = 0;
      gbc.gridy = 2;
      add(cancel, gbc);
      gbc.gridx++;
      add(ok, gbc);
      gbc.fill = GridBagConstraints.NONE;

      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.anchor = GridBagConstraints.BASELINE;

      long lo, hi;
      if (radix == RadixOption.RADIX_10_SIGNED) {
        lo = -(1L << (bitWidth-1));
        hi = (1L << bitWidth-1) - 1;
      } else {
        lo = 0;
        hi = (1L << bitWidth) - 1;
      }
      add(new JLabel(S.fmt(tristate ? "pinEditRangeTristate" : "pinEditRange", lo, hi)), gbc);
      gbc.gridy++;
      add(text, gbc);

      pack();
    }

    public void accept() {
      String s = text.getText();
      if (isEditValid(s)) {
        s = s.trim();
        Value newVal;
        if (s.matches("x+") || s.matches("z+") || s.matches("\\?+")) {
          newVal = Value.createUnknown(BitWidth.create(bitWidth));
        } else {
          try {
            int n = (int) Long.parseLong(s);
            newVal = Value.createKnown(BitWidth.create(bitWidth), n);
          } catch (NumberFormatException exception) {
            return;
          }
        }
        setVisible(false);
        pinState.intendedValue = newVal;
        state.queueForPropagation();
      }
    }

    boolean isEditValid(String s) {
      if (s == null)
        return false;
      s = s.trim();
      if (s.equals(""))
        return false;
      if (tristate && (s.matches("x+") || s.matches("z+") || s.matches("\\?+")))
        return true;
      try {
        long n = Long.parseLong(s);
        if (radix == RadixOption.RADIX_10_SIGNED)
          return (n >= -(1L << (bitWidth-1)) && n < (1L << bitWidth-1));
        else
          return (n >= 0 && n < (1L << bitWidth));
      } catch (NumberFormatException e) {
        return false;
      }
    }

    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        e.consume();
        accept();
      } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        e.consume();
        setVisible(false);
      }
    }

    @Override
    public void keyReleased(KeyEvent e) { }

    @Override
    public void keyTyped(KeyEvent e) { }
  }

  public static class PinLogger extends InstanceLogger {

    @Override
    public String getLogName(InstanceState state, Object option) {
      PinAttributes attrs = (PinAttributes) state.getAttributeSet();
      String ret = attrs.label;
      if (ret == null || ret.equals("")) {
        String type = attrs.type == INPUT
            ? S.get("pinInputName") : S.get("pinOutputName");
        return type + state.getInstance().getLocation();
      } else {
        return ret;
      }
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.WIDTH);
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      PinState s = getState(state);
      return s.intendedValue;
    }

    @Override
    public boolean isInput(InstanceState state, Object option) {
      PinAttributes attrs = (PinAttributes) state.getAttributeSet();
      return attrs.type == INPUT;
    }
  }

  public static class PinPoker extends InstancePoker {

    int bitPressed = -1;
    int bitCaret = -1;

    @Override
    public boolean capturesTextInput() {
      return bitCaret >= 0;
    }

    private int getBit(InstanceState state, MouseEvent e) {
      RadixOption radix = state.getAttributeValue(RadixOption.ATTRIBUTE);
      BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
      int r;
      if (radix == RadixOption.RADIX_16) {
        r = 4;
      } else if (radix == RadixOption.RADIX_8) {
        r = 3;
      } else if (radix == RadixOption.RADIX_2) {
        r = 1;
      } else {
        return -1;
      }
      if (width.getWidth() <= r) {
        return 0;
      } else {
        Bounds bds = state.getInstance().getNominalBounds(); // intentionally with no graphics object - we don't want label included
        int i = (bds.getX() + bds.getWidth() - e.getX() - 4) / (r == 1 ? 10 : 7);
        int j = (bds.getY() + bds.getHeight() - e.getY() - 2) / 14;
        int bit;
        if (r == 1) {
          bit = 8 * j + i;
        } else {
          bit = i * r;
        }
        if (bit < 0 || bit >= width.getWidth()) {
          return -1;
        } else {
          return bit;
        }
      }
    }

    private boolean isOkayToModify(InstanceState state, java.awt.Component src) {
      if (state.isCircuitRoot())
        return true;
      if (!(src instanceof Canvas)) // ?
        return true;
      if (!(state instanceof InstanceStateImpl)) {
        Debug.error("Pin attempting to modify non-root InstancePainter state?");
        return false;
      }
      Canvas canvas = (Canvas)src;
      // Pin is frozen, prompt if we should make new root circuit state
      // Why two params here? Isn't circState the same as state.getCircuitState()?
      CircuitState circState = canvas.getCircuitState();

      java.awt.Component frame = SwingUtilities.getRoot(canvas);

      int choice = JOptionPane.showConfirmDialog(frame,
          S.get("pinFrozenQuestion"),
          S.get("pinFrozenTitle"),
          JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.WARNING_MESSAGE);
      if (choice != JOptionPane.OK_OPTION)
        return false;
    

      circState = circState.cloneAsNewRootState();
      canvas.getProject().setCircuitState(circState);
      
      // WARNING: Calling circState.getInstanceState() here is a mistake.
      //    BAD: state = circState.getInstanceState(state.getInstance());
      // CircuitState returns a often-reused singleton InstanceStateImpl. This
      // can be obseved when the clock is on, any state object it returns will
      // be volatile and rapidly changing.
      // Instead, we repurpose the InstanceStateImpl we already have, which
      // came from InstancePokerAdapter and is NOT reused for other components.
      // This makes more sense anyway: the state within the instance poker gets
      // rebound to our new circState, rather than leaving that instance poker
      // inconsistent.
      ((InstanceStateImpl)state).repurpose(circState, ((InstanceStateImpl)state).getComponent());

      return true;
    }

    private boolean handleBitPress(InstanceState state, int bit, RadixOption radix, java.awt.Component src, char ch) {
      if (!isOkayToModify(state, src))
        return false;
      BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
      PinState pinState = getState(state);
      int r = (radix == RadixOption.RADIX_16
          ? 4 : (radix == RadixOption.RADIX_8 ? 3 : 1));
      if (bit+r > width.getWidth())
        r = width.getWidth() - bit;
      Value val[] = pinState.intendedValue.getAll();
      PinAttributes attrs = (PinAttributes) state.getAttributeSet();
      boolean tristate = (attrs.behavior == TRISTATE);
      // if this was just converted from substate to root state, normalize val
      if (tristate) { // convert E bits to z
        for (int b = 0; b < val.length; b++)
          if (val[b] != Value.TRUE && val[b] != Value.FALSE && val[b] != Value.UNKNOWN)
            val[b] = Value.UNKNOWN;
      } else { // convert E and X bits to 1
        for (int b = 0; b < val.length; b++)
          if (val[b] != Value.TRUE && val[b] != Value.FALSE)
            val[b] = Value.TRUE;
      }
      if (ch == 0) { // toggle
        boolean zeros = true, ones = true, defined = true;
        for (int b = bit; b < bit + r; b++) {
          if (val[b] == Value.FALSE)
            ones = false;
          else if (val[b] == Value.TRUE)
            zeros = false;
          else
            defined = false;
        }
        if (!defined || (ones && !tristate)) {
          for (int b = bit; b < bit + r; b++)
            val[b] = Value.FALSE;
        } else if (ones && tristate) {
          for (int b = bit; b < bit + r; b++)
            val[b] = Value.UNKNOWN;
        } else {
          int carry = 1;
          Value v[] = new Value[]{ Value.FALSE, Value.TRUE };
          for (int b = bit; b < bit + r; b++) {
            int s = (val[b] == Value.TRUE ? 1 : 0) + carry;
            val[b] = v[(s % 2)];
            carry = s / 2;
          }
        }
      } else if (tristate && (ch == 'x' || ch == 'X' || ch == 'z' || ch == 'Z')) {
        for (int b = bit; b < bit + r; b++)
          val[b] = Value.UNKNOWN;
      } else {
        int d;
        if ('0' <= ch && ch <= '9')
          d = ch - '0';
        else if ('a' <= ch && ch <= 'f')
          d = 0xa + (ch - 'a');
        else if ('A' <= ch && ch <= 'F')
          d = 0xA + (ch - 'A');
        else
          return false;
        if (d >= 1 << r)
          return false;
        Value v[] = new Value[]{ Value.FALSE, Value.TRUE };
        for (int i = 0; i < r; i++)
          val[bit+i] = (((d&(1<<i)) != 0) ? Value.TRUE : Value.FALSE);
      }
      pinState.intendedValue = Value.create(val);
      state.queueForPropagation();
      return true;
    }

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      if (!((PinAttributes)state.getAttributeSet()).isInput())
        return;
      bitPressed = getBit(state, e);
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      if (!((PinAttributes)state.getAttributeSet()).isInput())
        return;
      RadixOption radix = state.getAttributeValue(RadixOption.ATTRIBUTE);
      if (radix == RadixOption.RADIX_10_SIGNED || radix == RadixOption.RADIX_10_UNSIGNED) {
        if (!isOkayToModify(state, e.getComponent()))
          return;
        EditDecimal dialog = new EditDecimal(state);
        dialog.setLocation(e.getXOnScreen()-60, e.getYOnScreen()-40);
        dialog.setVisible(true);
      } else {
        // special case: for synthetic "by label" mouse release, mousePressed is
        // on different caret, so bitPressed will be uninitialized
        if (bitPressed < 0 && e.getX() == -1 && e.getY() == -1 && radix == RadixOption.RADIX_2) {
          BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
          if (width == BitWidth.ONE) {
            handleBitPress(state, 0, radix, e.getComponent(), (char)0);
            return;
          }
        }
        int bit = getBit(state, e);
        if (bit == bitPressed && bit >= 0) {
          bitCaret = bit;
          handleBitPress(state, bit, radix, e.getComponent(), (char)0);
        }
        if (bitCaret < 0) {
          BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
          int r = (radix == RadixOption.RADIX_16
              ? 4 : (radix == RadixOption.RADIX_8 ? 3 : 1));
          bitCaret = ((width.getWidth()-1)/r) * r;
        }
        bitPressed = -1;
      }
    }

    @Override
    public void keyTyped(InstanceState state, KeyEvent e) {
      if (!((PinAttributes)state.getAttributeSet()).isInput())
        return;
      char ch = e.getKeyChar();
      RadixOption radix = state.getAttributeValue(RadixOption.ATTRIBUTE);
      if (radix == RadixOption.RADIX_10_SIGNED || radix == RadixOption.RADIX_10_UNSIGNED)
        return;
      int r = (radix == RadixOption.RADIX_16
          ? 4 : (radix == RadixOption.RADIX_8 ? 3 : 1));
      BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
      if (bitCaret < 0)
        bitCaret = ((width.getWidth()-1)/r) * r;
      if (handleBitPress(state, bitCaret, radix, e.getComponent(), ch)) {
        bitCaret -= r;
        if (bitCaret < 0)
          bitCaret = ((width.getWidth()-1)/r) * r;
      }
    }

    @Override
    public void paint(InstancePainter painter) {
      if (bitCaret < 0)
        return;
      BitWidth width = painter.getAttributeValue(StdAttr.WIDTH);
      RadixOption radix = painter.getAttributeValue(RadixOption.ATTRIBUTE);
      if (radix == RadixOption.RADIX_10_SIGNED || radix == RadixOption.RADIX_10_UNSIGNED)
        return;
      int r = (radix == RadixOption.RADIX_16
          ? 4 : (radix == RadixOption.RADIX_8 ? 3 : 1));
      // note: always show red, to indicate text capture
      // if (width.getWidth() <= r)
      //   return;
      Bounds bds = painter.getNominalBounds();
      Graphics2D g = painter.getGraphics();
      g.setColor(Color.RED); // red to indicate text capture
      int x = bds.getX();
      int y = bds.getY();
      int w = bds.getWidth();
      int h = bds.getHeight();
      if (width.getWidth() == 1) {
        g.drawOval(x + 3, y + 3, 15, 15);
      } else {
        GraphicsUtil.switchToWidth(g, 2);
        y += h;
        x += w;
        if (width.getWidth() <= r) {
          x -= w/2 - 3;
          y -= 4;
        } else if (radix == RadixOption.RADIX_2) {
          x -= 7 + 10 * (bitCaret % 8);
          y -= 3 + 14 * (bitCaret / 8);
        } else {
          x -= 5 + 7 * (bitCaret / r);
          y -= 4;
        }
        g.draw(new Line2D.Float(x - 5.5f, y, x, y));
      }
      g.setColor(Color.BLACK);
    }
  }

  private static class PinState implements ComponentData {

    Value foundValue; // for color - received value from wire connected to this pin
    Value intendedValue; // for display - output: received value; input: UI or parent value

    PinState(Value v) {
      foundValue = intendedValue = v;
    }

    PinState(PinState other) {
      foundValue = other.foundValue;
      intendedValue = other.intendedValue;
    };

    @Override
    public PinState duplicateForNewSimulation() {
      return new PinState(this);
    }

  }

  private static PinState getState(InstanceState state) {
    PinAttributes attrs = (PinAttributes) state.getAttributeSet();
    BitWidth width = attrs.width;
    PinState ret = (PinState) state.getDataAsCustom();
    if (ret == null) {
      ret = new PinState(Value.repeat(attrs.defaultBitValue(), width));
      state.setData(ret);
    }
    if (ret.intendedValue.getWidth() != width.getWidth()) {
      ret.intendedValue = ret.intendedValue.extendWidth(width.getWidth(),
          attrs.defaultBitValue());
    }
    if (ret.foundValue.getWidth() != width.getWidth()) {
      ret.foundValue = ret.foundValue.extendWidth(width.getWidth(),
          Value.UNKNOWN);
    }
    return ret;
  }

  @Override
  public Value getDynamicValue(Instance instance, Object instanceStateData) {
    PinState data = (PinState) instanceStateData;
    return data == null ? Value.NIL : data.intendedValue;
  }

  // private static Value pull2(Value mod, BitWidth expectedWidth, Value pullTo) {
  //   if (mod.getWidth() == expectedWidth.getWidth())
  //     return mod.pullEachBitTowards(pullTo);
  //   else
  //     return Value.createKnown(expectedWidth, 0);
  // }

  public static final AttributeOption INPUT = new AttributeOption("input",
      S.getter("pinInputOption"));
  public static final AttributeOption OUTPUT = new AttributeOption("output",
      S.getter("pinOutputOption"));
  public static final Attribute<AttributeOption> ATTR_TYPE = 
      Attributes.forOption("type", S.getter("pinTypeAttr"),
          new AttributeOption[] { INPUT, OUTPUT });

  public static final AttributeOption SIMPLE = new AttributeOption("simple",
      S.getter("pinSimpleOption"));
  public static final AttributeOption TRISTATE = new AttributeOption("tristate",
      S.getter("pinTristateOption"));
  public static final AttributeOption PULL_DOWN = new AttributeOption("pulldown",
      S.getter("pinPullDownOption"));
  public static final AttributeOption PULL_UP = new AttributeOption("pullup",
      S.getter("pinPullUpOption"));
  public static final Attribute<AttributeOption> ATTR_BEHAVIOR =
      Attributes.forOption("behavior", S.getter("pinBehaviorAttr"),
          new AttributeOption[] { SIMPLE, TRISTATE, PULL_DOWN, PULL_UP });

  public static final Pin FACTORY = new Pin();

  private static final Icon ICON_IN = Icons.getIcon("pinInput.gif");

  private static final Icon ICON_OUT = Icons.getIcon("pinOutput.gif");

  private static final Font ICON_WIDTH_FONT = new Font("SansSerif", Font.BOLD, 9);

  private static final Font DEFAULT_FONT = new Font("monospaced", Font.PLAIN, 12);

  private static final Color ICON_WIDTH_COLOR = Value.WIDTH_ERROR_COLOR.darker();

  private Pin() {
    super("Pin", S.getter("pinComponent"));
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(JoinedConfigurator.create(
          new BitWidthConfigurator(StdAttr.WIDTH),
          new DirectionConfigurator(StdAttr.LABEL_EDGE_LOC)));
    setInstanceLogger(PinLogger.class);
    setInstancePoker(PinPoker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    configurePorts(instance);
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  private void configurePorts(Instance instance) {
    PinAttributes attrs = (PinAttributes) instance.getAttributeSet();
    String endType = attrs.isOutput() ? Port.INPUT : Port.OUTPUT;
    Port port = new Port(0, 0, endType, StdAttr.WIDTH);
    if (attrs.isOutput()) {
      port.setToolTip(S.getter("pinOutputToolTip"));
    } else {
      port.setToolTip(S.getter("pinInputToolTip"));
    }
    instance.setPorts(new Port[] { port });
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new PinAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    BitWidth width = attrs.getValue(StdAttr.WIDTH);
    return Probe.getOffsetBounds(facing, width,
        attrs.getValue(RadixOption.ATTRIBUTE) /* RadixOption.RADIX_2 */);
  }

  // public int getType(Instance instance) {
  //   PinAttributes attrs = (PinAttributes) instance.getAttributeSet();
  //   return attrs.type;
  // }

  public Value getValue(InstanceState state) {
    return getState(state).intendedValue;
  }

  public BitWidth getWidth(Instance instance) {
    PinAttributes attrs = (PinAttributes) instance.getAttributeSet();
    return attrs.width;
  }

  @Override
  public boolean HasThreeStateDrivers(AttributeSet base) {
    PinAttributes attrs = (PinAttributes) base;
    return (attrs.type == INPUT) && (attrs.behavior == TRISTATE);
  }

  @Override
  public String getHDLNamePrefix(Component comp) {
    // return "Pin";
    int w = comp.getEnd(0).getWidth().getWidth();
    if (comp.getEnd(0).canOutput())
      return w > 1 ? "InputBus" : "Input";
    else
      return w > 1 ? "OutputBus" : "Output";
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_TYPE) {
      configurePorts(instance);
    } else if (attr == StdAttr.WIDTH || attr == StdAttr.FACING
        || attr == StdAttr.LABEL_EDGE_LOC || attr == RadixOption.ATTRIBUTE) {
      instance.recomputeBounds();
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == Pin.ATTR_BEHAVIOR) {
      instance.fireInvalidated();
    }
  }

  public boolean isInputPin(Instance instance) {
    PinAttributes attrs = (PinAttributes) instance.getAttributeSet();
    return attrs.type == INPUT;
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    PinAttributes attrs = (PinAttributes) painter.getAttributeSet();
    Location loc = painter.getLocation();
    Bounds bds = painter.getNominalOffsetBounds();
    int x = loc.getX();
    int y = loc.getY();
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    boolean output = attrs.isOutput();
    if (output) {
      BitWidth width = attrs.getValue(StdAttr.WIDTH);
      if (width == BitWidth.ONE) {
        g.drawOval(x + bds.getX() + 1, y + bds.getY() + 1,
            bds.getWidth() - 2, bds.getHeight() - 2);
      } else {
        g.drawRoundRect(x + bds.getX() + 1, y + bds.getY() + 1,
            bds.getWidth() - 2, bds.getHeight() - 2, 6, 6);
      }
    } else {
      g.drawRect(x + bds.getX() + 1, y + bds.getY() + 1,
          bds.getWidth() - 2, bds.getHeight() - 2);
    }
  }

  @Override
  public void paintIcon(InstancePainter painter) {
    paintIconBase(painter);
    BitWidth w = painter.getAttributeValue(StdAttr.WIDTH);
    if (!w.equals(BitWidth.ONE)) {
      Graphics2D g = painter.getGraphics();
      g.setColor(ICON_WIDTH_COLOR);
      g.setFont(ICON_WIDTH_FONT);
      GraphicsUtil.drawCenteredText(g, "" + w.getWidth(), 10, 9);
      g.setColor(Color.BLACK);
    }
  }

  private void paintIconBase(InstancePainter painter) {
    PinAttributes attrs = (PinAttributes) painter.getAttributeSet();
    Direction dir = attrs.facing;
    boolean output = attrs.isOutput();
    Graphics2D g = painter.getGraphics();
    if (output) {
      if (ICON_OUT != null) {
        Icons.paintRotated(g, 2, 2, dir, ICON_OUT,
            painter.getDestination());
        return;
      }
    } else {
      if (ICON_IN != null) {
        Icons.paintRotated(g, 2, 2, dir, ICON_IN,
            painter.getDestination());
        return;
      }
    }
    int pinx, piny;
    if (dir == Direction.WEST) {
      pinx = 4;
      piny = 9;
    } else if (dir == Direction.NORTH) {
      pinx = 9;
      piny = 4;
    } else if (dir == Direction.SOUTH) {
      pinx = 9;
      piny = 16;
    } else { // EAST
      pinx = 16;
      piny = 9;
    }

    g.setColor(Color.black);
    if (output) {
      g.drawOval(4, 4, 13, 13);
    } else {
      g.drawRect(4, 4, 13, 13);
    }
    g.setColor(Value.TRUE.getColor());
    g.fillOval(7, 7, 8, 8);
    g.fillOval(pinx, piny, 3, 3);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    PinAttributes attrs = (PinAttributes) painter.getAttributeSet();
    Graphics2D g = painter.getGraphics();
    Bounds bds = painter.getInstance().getNominalBounds();
    int x = bds.getX();
    int y = bds.getY();
    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.black);
    if (attrs.type == OUTPUT) {
      if (attrs.width.getWidth() == 1) {
        g.drawOval(x + 1, y + 1, bds.getWidth() - 2,
            bds.getHeight() - 2);
      } else {
        g.drawRoundRect(x + 1, y + 1, bds.getWidth() - 2,
            bds.getHeight() - 2, 12, 12);
      }
    } else {
      g.drawRect(x + 1, y + 1, bds.getWidth() - 2, bds.getHeight() - 2);
    }

    painter.drawLabel();

    if (!painter.getShowState()) {
      g.setColor(Color.BLACK);
      GraphicsUtil.drawCenteredText(g, "x" + attrs.width.getWidth(),
          bds.getX() + bds.getWidth() / 2,
          bds.getY() + bds.getHeight() / 2 - 1);
    } else {
      PinState state = getState(painter);
      Value value = pull(attrs, state.intendedValue);
      if (attrs.width.getWidth() <= 1) {
        Value found = state.foundValue;
        g.setColor(found.getColor());
        g.fillOval(x + 4, y + 4, 13, 13);

        if (attrs.width.getWidth() == 1) {
          g.setColor(Color.WHITE);
          g.setFont(DEFAULT_FONT);
          GraphicsUtil.drawCenteredText(g,
              value.toDisplayString(), x + 10, y + 10);
        }
      } else {
        Probe.paintValue(painter, value);
      }
    }

    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    PinAttributes attrs = (PinAttributes) state.getAttributeSet();

    PinState q = getState(state);
    Value found = state.getPortValue(0);
    if (attrs.type == OUTPUT) {
      q.foundValue = found; // for color
      q.intendedValue = found; // for display
    } else {
      q.foundValue = found; // for color
      Value drive = pull(attrs, q.intendedValue);
      if (!drive.equals(found))
        state.setPort(0, drive, 1);
    }
  }

  // Before version 4.0.0HC: Attributes are a confusing mess. "Output?", "Three
  // State?", and "Pull Behavior" all interact in complicated ways. There seems
  // to currently only be a few possible combinations that are implemented:
  // Note: gray wires (with NIL value) are never connected to ports. Any wire
  // connected to a port is simulated as Unknown and shown as blue.
  //   Output pin:
  //     UI can display 0, 1, E, or X, and subcircuit passes whatever is
  //     received. When connected to a blue wire, displays X regardless of
  //     tri-state option. When disconnected entirely, it displays either 0 (if
  //     tri-state option is not selected), or X (if tri-state option is
  //     selected), but regardless, passes the X up to parent. The pull option
  //     is ignored.
  //   Input pin with tristate but no pull:
  //     UI can display and choose 0, 1, or X, and also shows red if there is an
  //     error on the output bus. Parent circuit can send 0, 1, X, or E, and all
  //     will pass through into subcircuit.
  //   Input pin with pull-up (or pull-down):
  //     UI can display and choose only 0 or 1, but also shows red if there is
  //     an error on the output bus. Parent circuit can send 0 or 1, but if it
  //     tries to send X or E these get converted to 1 (or 0 for pull-down). I
  //     think the behavior for E here is unreasonable: E should get sent
  //     through no matter what. The tristate option is ignored here. The UI
  //     when viewing a subcircuit doesn't distinguish between the case where
  //     parent circuit sends 1, and when parent sends an X that gets pulled-up
  //     to 1. I think it could show blue in one case, just like it shows red in
  //     cases of errors. Or show X, but color it to match the 1 value (or 0
  //     value).
  //   Input pin without tristate:
  //     This behaves the same as as tri-state with pull-down.
  //
  // Notice that tri-state=false seems pointless, but is the default because it
  // has an important UI difference: UI can select only 0 or 1, but Pull
  // attribute separately specifies what happens to Unknown values from parent
  // circuit, either leaving them alone (tri-state-like) or pulling up or down.
  //
  // It seems plausible to add support for another combination:
  //   Output pin with pull-up (or pull-down):
  //      UI could display 0, 1, E, or X, but X gets converted to 0 or 1 before
  //      being sent up to parent. Color could match what is being sent up to
  //      parent.
  // On the other hand, this just adds complexity, with no real benefit over
  // simply wiring things up properly.
  //
  // Version 4.0.0HC: Simplified new behavior:
  //   Output pin [behavior option hidden, has no effect]
  //      Depending on value of connected bus, UI displays 0, 1, E, or X, and
  //      subcircuit passes whatever is displayed up to parent. Color matches
  //      both the connected bus and the value passed up to parent. This is the
  //      simplest option, and it is the only option for output pins.
  //   Input pin in simple mode: [Behavior=simple]
  //      User can choose 0 or 1. Parent circuit can send 0, 1, E, or X. UI
  //      displays whatever user chose (or parent sent). Sends whatever is
  //      displayed to connected bus. Color matches whatever appears on
  //      connected bus (which may be different from what is displayed). Note:
  //      This mode never generates floating values, it only passes them along
  //      from a parent circuit.
  //   Input pin in tristate mode: [Behavior=tristate]
  //      Same circuit behavior as simple, but different UI behavior.
  //      User can choose 0, 1, or X. Parent circuit can send 0, 1, E, or X. UI
  //      displays whatever user chose (or parent sent). Sends whatever is
  //      displayed to connected bus. Color matches whatever appears on
  //      connected bus (which may be different from what is displayed). Note:
  //      This mode can generate X values from the UI, but for purposes of
  //      HDL-generation, this mode never generates floating values, it only
  //      passes them along from a parent circuit.
  //   Input pin with pull-up (or pull-down): [Behavior=pullup/pulldown]
  //      Same UI behavior as simple, but different circuit behavior.
  //      User can choose 0 or 1. Parent circuit can send 0, 1, or E, but if it
  //      tries to send X it gets converted to and displayed as 1 (or 0). Sends
  //      whatever is displayed to connected bus. Color matches whatever appears
  //      on connected bus. [or we could show blue in case of pull, or half
  //      blue]

  public void driveInputPin(InstanceState state, Value value) {
    PinAttributes attrs = (PinAttributes) state.getAttributeSet();
    PinState myState = getState(state);
    // don't pull here -- instead, pull when displaying and propagating
    myState.intendedValue = value; // pull(attrs, value);
  }

  private Value pull(PinAttributes attrs, Value value) {
    if (value == Value.NIL)
      return Value.createUnknown(attrs.width);
    else if (attrs.behavior == PULL_UP)
      return value.pullEachBitTowards(Value.TRUE);
    else if (attrs.behavior == PULL_DOWN)
      return value.pullEachBitTowards(Value.FALSE);
    else
      return value;
  }

  @Override
  public final Object getFeature(Object key, AttributeSet attrs) {
    if (key == TOOL_TIP) {
      AttributeOption type = attrs.getValue(ATTR_TYPE);
      if (type == INPUT)
        return S.get("pinComponentInput");
      else
        return S.get("pinComponentOutput");
    }
    return super.getFeature(key, attrs);
  }

}
