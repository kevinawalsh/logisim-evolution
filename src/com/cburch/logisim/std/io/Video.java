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

/*
 * This file was originally written by Kevin Walsh <kwalsh@cs.cornell.edu> for
 * Cornell's CS 314 computer organization course. It was subsequently modified
 * Martin Dybdal <dybber@dybber.dk> and Anders Boesen Lindbo Larsen
 * <abll@diku.dk> for use in the computer architecture class at the Department
 * of Computer Science, University of Copenhagen.
 */
package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.Graphics;

import com.bric.swing.ColorPicker;
import com.bric.swing.IndexedColorPicker;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.AbstractComponentFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentEvent;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentState;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.comp.ManagedComponent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.util.JInputComponent;
import com.cburch.logisim.util.StringGetter;

// N x N pixel LCD display with various color models
class Video extends ManagedComponent implements ToolTipMaker, AttributeListener {
  public static final ComponentFactory factory = new Factory();

  static final String BLINK_YES = "Blinking Dot";
  static final String BLINK_NO = "No Cursor";
  static final String[] BLINK_OPTIONS = { BLINK_YES, BLINK_NO };
  static final String RESET_ASYNC = "Asynchronous";
  static final String RESET_SYNC = "Synchronous";
  static final String[] RESET_OPTIONS = { RESET_ASYNC, RESET_SYNC };
  static final String BLANK_FIXED = "Fixed Color";
  static final String BLANK_INPUT  = "Input Color";
  static final String[] BLANK_OPTIONS = { BLANK_FIXED, BLANK_INPUT };

  static final String MODEL_RGB = "888 RGB (24 bit)";
  static final String MODEL_555_RGB = "555 RGB (15 bit)";
  static final String MODEL_565_RGB = "565 RGB (16 bit)";
  static final String MODEL_111_RGB = "8-Color RGB (3 bit)";
  static final String MODEL_ATARI = "Atari 2600 (7 bit)";
  static final String MODEL_XTERM16 = "XTerm16 (4 bit)";
  static final String MODEL_XTERM256 = "XTerm256 (8 bit)";
  static final String MODEL_GRAY4 = "Grayscale (4 bit)";
  static final String[] MODEL_OPTIONS = { MODEL_RGB, MODEL_555_RGB, MODEL_565_RGB, MODEL_111_RGB, MODEL_ATARI, MODEL_XTERM16, MODEL_XTERM256, MODEL_GRAY4 };

  static final Integer[] SIZE_OPTIONS = { 2, 4, 8, 16, 32, 64, 128, 256 };

  public static final Attribute BLINK_OPTION = Attributes.forOption("cursor",
      S.getter("rgbVideoCursor"), BLINK_OPTIONS);
  public static final Attribute RESET_OPTION = Attributes.forOption("reset",
      S.getter("rgbVideoReset"), RESET_OPTIONS);
  public static final Attribute BLANK_OPTION = Attributes.forOption("blank",
      S.getter("rgbVideoBlank"), BLANK_OPTIONS);
  public static final Attribute<ColorModelColor> FIXED_OPTION = forColor("fixed",
      S.getter("rgbVideoFixed"));
  public static final Attribute MODEL_OPTION = Attributes.forOption("color",
      S.getter("rgbVideoColor"), MODEL_OPTIONS);
  public static final Attribute<Integer> WIDTH_OPTION = Attributes.forOption("width",
      S.getter("rgbVideoWidth"), SIZE_OPTIONS);
  public static final Attribute<Integer> HEIGHT_OPTION = Attributes.forOption("height",
      S.getter("rgbVideoHeight"), SIZE_OPTIONS);
  public static final Attribute<Integer> SCALE_OPTION = Attributes.forIntegerRange("scale",
      S.getter("rgbVideoScale"), 1, 8);

  private static class Factory extends AbstractComponentFactory {
    private Factory() { }
    public String getName() { return "RGB Video"; }
    public String getDisplayName() { return S.get("rgbVideoComponent"); }
    public AttributeSet createAttributeSet() { return new VideoAttributes(); }
    public Component createComponent(Location loc, AttributeSet attrs) { return new Video(loc, attrs); }
    public Bounds getOffsetBounds(AttributeSet attrs) { // nominal and visible
      int s = attrs.getValue(SCALE_OPTION);
      int w = attrs.getValue(WIDTH_OPTION);
      int h = attrs.getValue(HEIGHT_OPTION);
      int bw = (s*w+14 < 100 ? 100 : s*w+14);
      int bh = (s*h+14 < 20 ? 20 : s*h+14);
      return Bounds.create(-30, -bh, bw, bh);
    }
    public void paintIcon(ComponentDrawContext context, int x, int y, AttributeSet attrs) {
      drawVideoIcon(context, x, y);
    }
  }

  static final int P_RST = 0;
  static final int P_CLK = 1;
  static final int P_WE = 2;
  static final int P_X = 3;
  static final int P_Y = 4;
  static final int P_DATA = 5;

  private Video(Location loc, AttributeSet attrs) {
    super(loc, attrs, 6);
    setEnd(P_RST, getLocation().translate(0, 0), BitWidth.ONE, EndData.INPUT_ONLY);
    setEnd(P_CLK, getLocation().translate(10, 0), BitWidth.ONE, EndData.INPUT_ONLY);
    setEnd(P_WE, getLocation().translate(20, 0), BitWidth.ONE, EndData.INPUT_ONLY);
    configureComponent();
    attrs.addAttributeWeakListener(null, this);
  }

  public ComponentFactory getFactory() { return factory; }

  Location loc(int pin) { return getEndLocation(pin); }
  Value val(CircuitState s, int pin) { return s.getValue(loc(pin)); }
  int addr(CircuitState s, int pin) { return val(s, pin).toIntValue(); }

  @SuppressWarnings("unchecked")
  public void propagate(CircuitState circuitState) {
    AttributeSet attrs = getAttributeSet();
    State state = getState(circuitState, attrs);
    int x = addr(circuitState, P_X);
    int y = addr(circuitState, P_Y);
    int color = addr(circuitState, P_DATA);
    state.last_x = x;
    state.last_y = y;
    state.color = color;

    Object reset_option = attrs.getValue(RESET_OPTION);
    if (reset_option == null) reset_option = RESET_OPTIONS[0];
    Object blank_option = attrs.getValue(BLANK_OPTION);
    if (blank_option == null) blank_option = BLANK_OPTIONS[0];
    ColorModel cm = getColorModel(attrs.getValue(MODEL_OPTION));
    int w = attrs.getValue(WIDTH_OPTION);
    int h = attrs.getValue(HEIGHT_OPTION);

    if (state.tick(val(circuitState, P_CLK)) && val(circuitState, P_WE) == Value.TRUE) {
      Graphics g = state.img.getGraphics();
      g.setColor(new Color(cm.getRGB(color)));
      g.fillRect(x, y, 1, 1);
      if (RESET_SYNC.equals(reset_option) && val(circuitState, P_RST) == Value.TRUE) {
        if (BLANK_FIXED.equals(blank_option)) {
          ColorModelColor cmc = attrs.getValue(FIXED_OPTION);
          color = cmc == null ? 0 : ((ColorModelColor)cmc).color;
          g.setColor(new Color(cm.getRGB(color)));
        } else {
          // input color already calculated
        }
        g.fillRect(0, 0, w, h);
      }
    }

    if (!RESET_SYNC.equals(reset_option) && val(circuitState, P_RST) == Value.TRUE) {
      Graphics g = state.img.getGraphics();
      if (BLANK_FIXED.equals(blank_option)) {
        ColorModelColor cmc = attrs.getValue(FIXED_OPTION);
        color = cmc == null ? 0 : ((ColorModelColor)cmc).color;
      } else {
        // input color already calculated
      }
      g.setColor(new Color(cm.getRGB(color)));
      g.fillRect(0, 0, w, h);
    }
  }

  public void draw(ComponentDrawContext context) {
    Location loc = getLocation();
    AttributeSet attrs = getAttributeSet();
    State state = getState(context.getCircuitState(), attrs);
    drawVideo(context, loc.getX(), loc.getY(), state);
  }

  static void drawVideoIcon(ComponentDrawContext context, int x, int y) {
    Graphics g = context.getGraphics();
    g.setColor(Color.WHITE);
    g.fillRoundRect(x+2, y+2, 16-1, 16-1, 3, 3);
    g.setColor(Color.BLACK);
    g.drawRoundRect(x+2, y+2, 16-1, 16-1, 3, 3);
    g.setColor(Color.RED);
    g.fillRect(x+5, y+5, 5, 5);
    g.setColor(Color.BLUE);
    g.fillRect(x+10, y+5, 5, 5);
    g.setColor(Color.GREEN);
    g.fillRect(x+5, y+10, 5, 5);
    g.setColor(Color.CYAN);
    g.fillRect(x+10, y+10, 5, 5);
    g.setColor(Color.BLACK);
  }

  boolean blink() {
    long now = System.currentTimeMillis();
    return (now/1000 % 2 == 0);
  }

  static DirectColorModel rgb111 = new DirectColorModel(3, 0x4, 0x2, 0x1);
  static DirectColorModel rgb555 = new DirectColorModel(15, 0x7C00, 0x03E0, 0x001F);
  static DirectColorModel rgb565 = new DirectColorModel(16, 0xF800, 0x07E0, 0x001F);
  static DirectColorModel rgb = new DirectColorModel(24, 0xFF0000, 0x00FF00, 0x0000FF);
  static IndexColorModel gray4 = new IndexColorModel(4, 16,
      new int[] {
        0x000000, 0x111111, 0x222222, 0x333333, 0x444444, 0x555555, 0x666666, 0x777777,
        0x888888, 0x999999, 0xaaaaaa, 0xbbbbbb, 0xcccccc, 0xdddddd, 0xeeeeee, 0xffffff,
      }, 0, false, -1, 0);
  static IndexColorModel atari = new IndexColorModel(7, 128,
      new int[] {
        0x000000, 0x0a0a0a, 0x373737, 0x5f5f5f, 0x7a7a7a, 0xa1a1a1, 0xc5c5c5, 0xededed,
        0x000000, 0x352100, 0x5a4500, 0x816c00, 0x9c8700, 0xc3af01, 0xe8d326, 0xfffa4d,
        0x310000, 0x590700, 0x7d2b00, 0xa45200, 0xbf6d04, 0xe7952b, 0xffb950, 0xffe077,
        0x470000, 0x6e0000, 0x931302, 0xba3b2a, 0xd55545, 0xfc7d6c, 0xffa190, 0xffc9b8,
        0x4b0002, 0x720029, 0x96034e, 0xbe2a75, 0xd94590, 0xff6cb7, 0xff91dc, 0xffb8ff,
        0x3c0049, 0x640070, 0x880094, 0xaf24bc, 0xca3fd7, 0xf266fe, 0xff8aff, 0xffb2ff,
        0x1e007d, 0x4500a5, 0x6902c9, 0x9129f1, 0xac44ff, 0xd36bff, 0xf790ff, 0xffb7ff,
        0x000096, 0x1d00bd, 0x4111e1, 0x6939ff, 0x8453ff, 0xab7bff, 0xcf9fff, 0xf7c7ff,
        0x00008d, 0x0004b4, 0x1728d9, 0x3f50ff, 0x5a6bff, 0x8192ff, 0xa5b6ff, 0xcddeff,
        0x000065, 0x001e8c, 0x0042b0, 0x1b6ad8, 0x3685f3, 0x5dacff, 0x82d0ff, 0xa9f8ff,
        0x000f25, 0x00364c, 0x005a70, 0x048298, 0x1f9db3, 0x47c4da, 0x6be8fe, 0x92ffff,
        0x002000, 0x004701, 0x006b25, 0x00934d, 0x1aae68, 0x42d58f, 0x66f9b4, 0x8dffdb,
        0x002700, 0x004e00, 0x007200, 0x0d9a06, 0x28b520, 0x4fdc48, 0x74ff6c, 0x9bff94,
        0x002200, 0x004a00, 0x036e00, 0x2b9500, 0x45b000, 0x6dd812, 0x91fc36, 0xb9ff5d,
        0x000a00, 0x073a00, 0x2b5f00, 0x528600, 0x6da100, 0x95c800, 0xb9ed1c, 0xe0ff43,
        0x000000, 0x352100, 0x5a4500, 0x816c00, 0x9c8700, 0xc3af01, 0xe8d326, 0xfffa4d,
      }, 0, false, -1, 0);
  static IndexColorModel xterm16 = new IndexColorModel(4, 16,
      new int[] {
        0x000000, 0x800000, 0x008000, 0x808000, 0x000080, 0x800080, 0x008080, 0xc0c0c0,
        0x808080, 0xff0000, 0x00ff00, 0xffff00, 0x0000ff, 0xff00ff, 0x00ffff, 0xffffff,
      }, 0, false, -1, 0);
  static IndexColorModel xterm256 = new IndexColorModel(8, 256,
      new int[] {
        0x000000, 0x800000, 0x008000, 0x808000, 0x000080, 0x800080, 0x008080, 0xc0c0c0,
        0x808080, 0xff0000, 0x00ff00, 0xffff00, 0x0000ff, 0xff00ff, 0x00ffff, 0xffffff,
        0x000000, 0x00005f, 0x000087, 0x0000af, 0x0000d7, 0x0000ff, 0x005f00, 0x005f5f,
        0x005f87, 0x005faf, 0x005fd7, 0x005fff, 0x008700, 0x00875f, 0x008787, 0x0087af,
        0x0087d7, 0x0087ff, 0x00af00, 0x00af5f, 0x00af87, 0x00afaf, 0x00afd7, 0x00afff,
        0x00d700, 0x00d75f, 0x00d787, 0x00d7af, 0x00d7d7, 0x00d7ff, 0x00ff00, 0x00ff5f,
        0x00ff87, 0x00ffaf, 0x00ffd7, 0x00ffff, 0x5f0000, 0x5f005f, 0x5f0087, 0x5f00af,
        0x5f00d7, 0x5f00ff, 0x5f5f00, 0x5f5f5f, 0x5f5f87, 0x5f5faf, 0x5f5fd7, 0x5f5fff,
        0x5f8700, 0x5f875f, 0x5f8787, 0x5f87af, 0x5f87d7, 0x5f87ff, 0x5faf00, 0x5faf5f,
        0x5faf87, 0x5fafaf, 0x5fafd7, 0x5fafff, 0x5fd700, 0x5fd75f, 0x5fd787, 0x5fd7af,
        0x5fd7d7, 0x5fd7ff, 0x5fff00, 0x5fff5f, 0x5fff87, 0x5fffaf, 0x5fffd7, 0x5fffff,
        0x870000, 0x87005f, 0x870087, 0x8700af, 0x8700d7, 0x8700ff, 0x875f00, 0x875f5f,
        0x875f87, 0x875faf, 0x875fd7, 0x875fff, 0x878700, 0x87875f, 0x878787, 0x8787af,
        0x8787d7, 0x8787ff, 0x87af00, 0x87af5f, 0x87af87, 0x87afaf, 0x87afd7, 0x87afff,
        0x87d700, 0x87d75f, 0x87d787, 0x87d7af, 0x87d7d7, 0x87d7ff, 0x87ff00, 0x87ff5f,
        0x87ff87, 0x87ffaf, 0x87ffd7, 0x87ffff, 0xaf0000, 0xaf005f, 0xaf0087, 0xaf00af,
        0xaf00d7, 0xaf00ff, 0xaf5f00, 0xaf5f5f, 0xaf5f87, 0xaf5faf, 0xaf5fd7, 0xaf5fff,
        0xaf8700, 0xaf875f, 0xaf8787, 0xaf87af, 0xaf87d7, 0xaf87ff, 0xafaf00, 0xafaf5f,
        0xafaf87, 0xafafaf, 0xafafd7, 0xafafff, 0xafd700, 0xafd75f, 0xafd787, 0xafd7af,
        0xafd7d7, 0xafd7ff, 0xafff00, 0xafff5f, 0xafff87, 0xafffaf, 0xafffd7, 0xafffff,
        0xd70000, 0xd7005f, 0xd70087, 0xd700af, 0xd700d7, 0xd700ff, 0xd75f00, 0xd75f5f,
        0xd75f87, 0xd75faf, 0xd75fd7, 0xd75fff, 0xd78700, 0xd7875f, 0xd78787, 0xd787af,
        0xd787d7, 0xd787ff, 0xdfaf00, 0xdfaf5f, 0xdfaf87, 0xdfafaf, 0xdfafdf, 0xdfafff,
        0xdfdf00, 0xdfdf5f, 0xdfdf87, 0xdfdfaf, 0xdfdfdf, 0xdfdfff, 0xdfff00, 0xdfff5f,
        0xdfff87, 0xdfffaf, 0xdfffdf, 0xdfffff, 0xff0000, 0xff005f, 0xff0087, 0xff00af,
        0xff00df, 0xff00ff, 0xff5f00, 0xff5f5f, 0xff5f87, 0xff5faf, 0xff5fdf, 0xff5fff,
        0xff8700, 0xff875f, 0xff8787, 0xff87af, 0xff87df, 0xff87ff, 0xffaf00, 0xffaf5f,
        0xffaf87, 0xffafaf, 0xffafdf, 0xffafff, 0xffdf00, 0xffdf5f, 0xffdf87, 0xffdfaf,
        0xffdfdf, 0xffdfff, 0xffff00, 0xffff5f, 0xffff87, 0xffffaf, 0xffffdf, 0xffffff,
        0x080808, 0x121212, 0x1c1c1c, 0x262626, 0x303030, 0x3a3a3a, 0x444444, 0x4e4e4e,
        0x585858, 0x626262, 0x6c6c6c, 0x767676, 0x808080, 0x8a8a8a, 0x949494, 0x9e9e9e,
        0xa8a8a8, 0xb2b2b2, 0xbcbcbc, 0xc6c6c6, 0xd0d0d0, 0xdadada, 0xe4e4e4, 0xeeeeee,
      }, 0, false, -1, 0);

  static ColorModel getColorModel(Object model) {
    if (model.equals(MODEL_RGB)) return rgb;
    else if (model.equals(MODEL_555_RGB)) return rgb555;
    else if (model.equals(MODEL_565_RGB)) return rgb565;
    else if (model.equals(MODEL_111_RGB)) return rgb111;
    else if (model.equals(MODEL_ATARI)) return atari;
    else if (model.equals(MODEL_XTERM16)) return xterm16;
    else if (model.equals(MODEL_XTERM256)) return xterm256;
    else if (model.equals(MODEL_GRAY4)) return gray4;
    else return gray4; // ???
  }

  @SuppressWarnings("unchecked")
  void drawVideo(ComponentDrawContext context, int x, int y, State state) {
    Graphics g = context.getGraphics();

    AttributeSet attrs = getAttributeSet();
    Object blink_option = attrs.getValue(BLINK_OPTION);
    Object reset_option = attrs.getValue(RESET_OPTION);
    ColorModel cm = getColorModel(attrs.getValue(MODEL_OPTION));

    int s = attrs.getValue(SCALE_OPTION);
    int w = attrs.getValue(WIDTH_OPTION);
    int h = attrs.getValue(HEIGHT_OPTION);
    int bw = (s*w+14 < 100 ? 100 : s*w+14);
    int bh = (s*h+14 < 20 ? 20 : s*h+14);

    x += -30;
    y += -bh;

    g.drawRoundRect(x, y, bw, bh, 6, 6);
    for (int i = 0; i < 6; i++) {
      if (i != P_CLK)
        context.drawPin(this, i);
    }
    context.drawClock(this, P_CLK, Direction.NORTH);
    g.drawRect(x+6, y+6, s*w+2, s*h+2);
    g.drawImage(state.img, x+7, y+7, x+7+s*w, y+7+s*h, 0, 0, w, h, null);
    // draw a little cursor for sanity
    if (blink_option == null) blink_option = BLINK_OPTIONS[0];
    if (BLINK_YES.equals(blink_option) && blink()
        && state.last_x >= 0 && state.last_x < w
        && state.last_y >= 0 && state.last_y < h) {
      g.setColor(new Color(cm.getRGB(state.color)));
      g.fillRect(x+7+state.last_x*s, y+7+state.last_y*s, s, s);
    }
  }

  @SuppressWarnings("unchecked")
  private State getState(CircuitState circuitState, AttributeSet attrs) {
    State state = (State) circuitState.getDataAsCustom(this);
    if (state == null) {
      ColorModel cm = getColorModel(attrs.getValue(MODEL_OPTION));
      Object blank_option = attrs.getValue(BLANK_OPTION);
      ColorModelColor cmc = attrs.getValue(FIXED_OPTION);
      int color = cmc == null ? 0 : ((ColorModelColor)cmc).color;
      Color bg = new Color(cm.getRGB(color));
      state = new State(bg);
      circuitState.setData(this, state);
    }
    return state;
  }

  private class State implements ComponentData {
    public Value lastClock = null;
    public BufferedImage img;
    public int last_x, last_y, color;

    State(Color bg) {
      img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
      reset(bg);
    }

    State(State other) {
      lastClock = other.lastClock;
      last_x = other.last_x;
      last_y = other.last_y;
      color = other.color;
      img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
      Graphics g = img.getGraphics();
      g.drawImage(other.img, 0, 0, 256, 256, 0, 0, 256, 256, null);
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }

    public void reset(Color bg) {
      Graphics g = img.getGraphics();
      g.setColor(bg);
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
    }

    public boolean tick(Value clk) {
      boolean rising = (lastClock == null || (lastClock == Value.FALSE && clk == Value.TRUE));
      lastClock = clk;
      return rising;
    }
  }

  @Override
  public Object getFeature(Object key) {
    if (key == ToolTipMaker.class)
      return this;
    else
      return super.getFeature(key);
  }

  @SuppressWarnings("unchecked")
  public String getToolTip(ComponentUserEvent e) {
    int end = -1;
    for (int i = getEnds().size() - 1; i >= 0; i--) {
      if (getEndLocation(i).manhattanDistanceTo(e.getX(), e.getY()) < 10) {
        end = i;
        break;
      }
    }
    switch (end) {
    case P_CLK: return S.get("rgbVideoCLK");
    case P_WE: return S.get("rgbVideoWE");
    case P_X: return S.get("rgbVideoX");
    case P_Y: return S.get("rgbVideoY");
    case P_DATA:
      AttributeSet attrs = getAttributeSet();
      return S.fmt("rgbVideoData", attrs.getValue(MODEL_OPTION).toString());
    case P_RST: return S.get("rgbVideoRST");
    default: return null;
    }
  }

  public void attributeListChanged(AttributeEvent e) {
  }

  public void attributeValueChanged(AttributeEvent e) {
    configureComponent();
  }

  @SuppressWarnings("unchecked")
  void configureComponent() {
    AttributeSet attrs = getAttributeSet();
    String model = (String)attrs.getValue(MODEL_OPTION);
    int bpp = getColorModel(model).getPixelSize();
    int xs = 31 - Integer.numberOfLeadingZeros(attrs.getValue(WIDTH_OPTION));
    int ys = 31 - Integer.numberOfLeadingZeros(attrs.getValue(HEIGHT_OPTION));
    setEnd(P_X, getLocation().translate(40, 0), BitWidth.create(xs), EndData.INPUT_ONLY);
    setEnd(P_Y, getLocation().translate(50, 0), BitWidth.create(ys), EndData.INPUT_ONLY);
    setEnd(P_DATA, getLocation().translate(60, 0), BitWidth.create(bpp), EndData.INPUT_ONLY);
    ColorModelColor cmc = attrs.getValue(FIXED_OPTION);
    if (!cmc.model.equals(model)) {
      cmc = cmc.transferTo(model);
      attrs.setAttr(FIXED_OPTION, cmc);
    }
    recomputeBounds();
    fireComponentInvalidated(new ComponentEvent(this));
  }
  
  private static Attribute<ColorModelColor> forColor(String name, StringGetter disp) {
    return new ColorAttribute(name, disp);
  }

  private static class ColorAttribute extends Attribute<ColorModelColor> {
    public ColorAttribute(String name, StringGetter desc) {
      super(name, desc);
    }

    @Override
    public java.awt.Component getCellEditor(ColorModelColor cmc) {
      if (cmc != null && cmc.indexed)
        return new IndexColorChooser(cmc);
      else
        return new ColorChooser(cmc);
    }

    @Override
    public ColorModelColor parse(String value) {
      int idx = value.indexOf('#');
      int color = Integer.parseInt(value.substring(0, idx), 16) & 0xffffff;
      String model = value.substring(idx+1);
      return new ColorModelColor(model, color);
    }

    @Override
    public String toDisplayString(ColorModelColor value) {
      if (value == null)
        return "";
      int digits = (value.bits+3)/4;
      int mask = (1 << value.bits) - 1;
      return String.format("0x%0"+digits+"x", value.color & mask);
    }

    @Override
    public String toStandardString(ColorModelColor value) {
      return value == null ? "000000#" + MODEL_RGB : String.format("%x#%s", value.color & 0xffffff, value.model);
    }

    @Override
    public Domain getDomain() {
      return Domain.ofDescription("any color within the specified color model");
    }
  }

  private static class ColorChooser extends ColorPicker
    implements JInputComponent<ColorModelColor> {
    private static final long serialVersionUID = 1L;
    String model;

    ColorChooser(ColorModelColor initial) {
      super(initial == null ? "888" : initial.depth, true, false);
      if (initial != null) {
        model = initial.model;
        ColorModel cm = Video.getColorModel(model);
        setColor(new Color(cm.getRGB(initial.color)));
      } else {
        model = MODEL_RGB;
        setColor(Color.GRAY);
      }
      setOpacityVisible(false);
    }

    public ColorModelColor getValue() {
      return new ColorModelColor(model, getUnscaledColor());
    }

    public void setValue(ColorModelColor value) {
      model = value.model;
      setColor(new Color(Video.getColorModel(model).getRGB(value.color)));
    }
  }

  private static class IndexColorChooser extends IndexedColorPicker
    implements JInputComponent<ColorModelColor> {
    private static final long serialVersionUID = 1L;
    String model;

    IndexColorChooser(ColorModelColor initial) {
      super((IndexColorModel)Video.getColorModel(initial.model), true);
      model = initial.model;
      setColorIndex(initial.color);
    }

    public ColorModelColor getValue() {
      return new ColorModelColor(model, getColorIndex());
    }

    public void setValue(ColorModelColor value) {
      if (!model.equals(value.model))
        throw new IllegalArgumentException("can't change color models here");
      // model = value.model;
      setColorIndex(value.color);
    }
  }

  static class ColorModelColor {
    final String model;
    final int color;
    final String depth;
    final int bits;
    final boolean indexed;

    ColorModelColor(String model, int color) {
      this.model = model;
      this.color = color;
      if (model.equals(MODEL_RGB)) {
        depth="888"; bits = 24; indexed = false;
      } else if (model.equals(MODEL_555_RGB)) {
        depth="555"; bits = 15; indexed = false;
      } else if (model.equals(MODEL_565_RGB)) {
        depth="565"; bits = 16; indexed = false;
      } else if (model.equals(MODEL_111_RGB)) {
        depth="111"; bits = 3; indexed = false;
      } else if (model.equals(MODEL_ATARI)) {
        depth = null; bits = 7; indexed = true;
      } else if (model.equals(MODEL_XTERM16)) {
        depth = null; bits = 4; indexed = true;
      } else if (model.equals(MODEL_XTERM256)) {
        depth = null; bits = 8; indexed = true;
      } else if (model.equals(MODEL_GRAY4)) {
        depth = null; bits = 4; indexed = true;
      } else {
        throw new IllegalArgumentException("invalid color model: " + model);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ColorModelColor)) return false;
      ColorModelColor other = (ColorModelColor) o;
      return model.equals(other.model) && color == other.color;
    }

    @Override
    public int hashCode() {
      return model.hashCode() * 31 + color;
    }

    ColorModelColor transferTo(String destModel) {
      ColorModel src = Video.getColorModel(model);
      ColorModel dst = Video.getColorModel(destModel);

      Color c = new Color(src.getRGB(color));
      float[] rgb0 = new float[] { c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f };
      float[] xyz1 = src.getColorSpace().toCIEXYZ(rgb0);
      float[] rgb2 = dst.getColorSpace().fromCIEXYZ(xyz1);
      int destColor = dst.getDataElement(rgb2, 0);
      return new ColorModelColor(destModel, destColor);
    }

    static String tostr(float[] a) {
      String s = "[";
      for (float f : a) {
        s += " " + f;
      }
      return s + " ]";
    }

  }

}
