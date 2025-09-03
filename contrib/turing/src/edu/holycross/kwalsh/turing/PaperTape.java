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

package edu.holycross.kwalsh.turing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.net.URL;

import javax.swing.ImageIcon;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.gui.start.TtyInterface;

/**
 * A paper tape, as might be used to build a turing machine.
 */
class PaperTape extends InstanceFactory {

  /* public static class Logger extends InstanceLogger implements TtyInterface.PaperTape {

    public Object[] getLogOptions(InstanceState state) {
      return new Object[] { "L", "R", "P", "V" };
    }


    static final BitWidth one = BitWidth.create(1);
    static final BitWidth one = BitWidth.create(1);
    @Override
    public String getLogName(InstanceState state, Object option) {
      return state.getAttributeValue(StdAttr.LABEL);
    }

    @Override
    public BitWidth getBitWidth(InstanceState state, Object option) {
      return bitwidth;
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
      int rgb = 0;
      if (data != null)
        return Value.createUnknown(bitwidth);
      else
        return Value.createKnown(bitwidth, ((Integer)data.getValue()).intValue());
    }
  }
  */

  private static final int R = 0;
  private static final int CLK = 1;
  private static final int S = 2;
  private static final int P = 3;
  private static final int V = 4;
  private static final int L = 5;

  // 7 bit
  public static final String PRINTABLE_ASCII = "Printable ASCII (blank is any unprintable)";

  // 7 bit
  public static final String FULL_ASCII = "ASCII (blank is NUL)";

  // 32 bit
  public static final String UNICODE = "Unicode UTF32 (blank is NUL)";

  // 4 bit
  public static final String BCD = "Digits 0-9 (blank is anything else)";

  // 2 bit
  public static final String ABC = "Alpha: blank, A, B, C";

  // 2 bit
  public static final String BINARY = "Tiny: blank, \u0259, 0, 1";

  // 1 bit
  public static final String DOTS = "Minimal: blank, dot";

  // 5 bit
  public static final String ALAN = "Turing's Favorite: blank, 0, 1, \u0259, \uD835\uDF36, \uD835\uDF36, ...";

  public static final String[] ALPHABET = { PRINTABLE_ASCII, FULL_ASCII, UNICODE, ABC, BCD, BINARY, DOTS, ALAN };
  public static final Attribute<String> ATTR_ALPHABET = Attributes.forOption(
      "alphabet", StringUtil.constantGetter("Alphabet"), ALPHABET);

  public static final Attribute<String> ATTR_INIT = Attributes.forString(
      "init", StringUtil.constantGetter("Initial Symbols"));

  public static final Attribute<Integer> ATTR_SIZE = Attributes.forIntegerRange(
      "cells", StringUtil.constantGetter("Display Width"), 5, 50);

	public PaperTape() {
		super("Paper Tape");
    int cells = 9;
    int w = 90 + cells * 30;
		setOffsetBounds(Bounds.create(-w/2, -110, w, 110));
    setAttributes(
        new Attribute[] { ATTR_ALPHABET, ATTR_INIT, ATTR_SIZE, StdAttr.LABEL, StdAttr.LABEL_FONT },
        new Object[] { ALAN, "", 9, "", StdAttr.DEFAULT_LABEL_FONT });

		setInstancePoker(PaperPoker.class);

		URL url = getClass().getClassLoader().getResource("resources/turing/papertape.png");
		if (url != null)
			setIcon(new ImageIcon(url));
	}

	@Override
	protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
		Bounds bds = instance.getNominalBounds();
		instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT,
        bds.getX() + bds.getWidth() / 2,
        bds.getY() - 3,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
    updatePorts(instance);
	}

  public static int widthFor(String alphabet) {
    if (alphabet == UNICODE)
      return 32;
    else if (alphabet == ABC)
      return 2;
    else if (alphabet == DOTS)
      return 1;
    else if (alphabet == BINARY)
      return 2;
    else if (alphabet == BCD)
      return 4;
    else if (alphabet == ALAN)
      return 5;
    else
      return 7;
  }

  private void updatePorts(Instance instance) {
    int cells = instance.getAttributeValue(ATTR_SIZE);
    if (cells % 2 == 0)
      cells++;
    int x = 90 + cells * 30;
    String alphabet = instance.getAttributeValue(ATTR_ALPHABET);
    int w = widthFor(alphabet);
    Port[] ps = new Port[] {
      new Port(-x/2+30, 0, Port.INPUT, 1), // R
      new Port(-20, 0, Port.INPUT, 1),     // CLK
      new Port(-10, 0, Port.OUTPUT, w),    // S
      new Port(+10, 0, Port.INPUT, 1),     // P
      new Port(+20, 0, Port.INPUT, w),     // V
      new Port(x/2-30, 0, Port.INPUT, 1),  // L
    };
    ps[R].setToolTip(StringUtil.constantGetter("R: moves head one cell to the right"));
    ps[L].setToolTip(StringUtil.constantGetter("L: moves head one cell to the right"));
    ps[CLK].setToolTip(StringUtil.constantGetter("CLK: when clock rises, action is performed"));
    ps[S].setToolTip(StringUtil.constantGetter("S: value of the symbol currently under tape head"));
    ps[P].setToolTip(StringUtil.constantGetter("P: whether to print a new symbol when clock rises"));
    ps[V].setToolTip(StringUtil.constantGetter("V: the new symbol to be printed when clock rises"));
		instance.setPorts(ps);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_ALPHABET) {
      // PaperData state = PaperData.get(instance);
      // state.setAlphabet(instance.getAttributeValue(ATTR_ALPHABET));
      updatePorts(instance);
    } else if (attr == ATTR_SIZE) {
      instance.recomputeBounds();
      updatePorts(instance);
    }
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrsBase) {
    int cells = attrsBase.getValue(ATTR_SIZE);
    if (cells % 2 == 0)
      cells++;
    int w = 90 + cells * 30;
    return Bounds.create(-w/2, -110, w, 110);
  }


  private static final Color LIGHT_GREEN = new Color(0xc4, 0xe0, 0xca);
  private static final Color DARK_GREEN = LIGHT_GREEN.darker();
  private static final Color VERY_GREEN = DARK_GREEN.darker().darker();
  private static final Color TAN = new Color(0xf7, 0xe9, 0xbc);
  private static final Color DARK_TAN = TAN.darker();
  private static final Color SILVER = new Color(0xdb, 0xdb, 0xdb);
  private static final Color DARK_SILVER = new Color(0x80, 0x80, 0x80);
  private static final Color RED = new Color(0xff, 0x47, 0x47, 0xcc);

  private void drawTape(Graphics2D g, int x, int y, int cells, PaperData state) {
    int w = 90 + cells * 30;
    g.setColor(TAN);
    g.fillRect(x, y, w, 50);
    g.setColor(LIGHT_GREEN);
    g.fillRect(x, y, w, 9);
    g.fillRect(x, y+41, w, 9);
    g.setColor(DARK_GREEN);
    g.drawLine(x, y, x+w, y);
    g.drawLine(x, y+50, x+w, y+50);
    g.setColor(Color.WHITE);
    for (int xx = 5; xx < x+w-5; xx += 15) {
      g.fillRoundRect(xx, y+2, 4, 6, 3, 3);
      g.fillRoundRect(xx, y+43, 4, 6, 3, 3);
    }
    boolean moving = state != null && state.isMoving();
    for (int xx = x+15 + (moving?15:0); xx < x+w-45; xx += 30) {
      g.setColor(DARK_TAN);
      g.drawRect(xx, y+10, 30, 30);
    }
  }

  private static final Font FONT = new Font("Monospaced", Font.PLAIN, 20);
  private static final Font ERRFONT = new Font("Monospaced", Font.PLAIN, 9);
  private static final Font TINYFONT = new Font("Monospaced", Font.PLAIN, 7);

  private void drawState(Graphics2D g, int x, int y, int cells, PaperData state) {
    if (state == null)
      return;
    int w = 90 + cells * 30;
    Font oldFont = g.getFont();
    g.setFont(FONT);
    g.setColor(Color.BLACK);
    if (state.isMovingLeft())
      x -= 15;
    else if (state.isMovingRight())
      x += 15;
    int pos = state.getPosition();
    int i = pos - (cells-1)/2;
    for (int xx = x+45; xx < x+w-45; xx += 30) {
      int utf32 = state.getUtf32Symbol(i);
      if (utf32 != 0) {
        String s = null;
        try {
          if (FONT.canDisplay(utf32)) {
            s = new String(new int[]{ utf32 }, 0, 1);
            g.drawString(s, xx+8, y+20);
          }
        } catch (IllegalArgumentException e) { }
        if (s == null) {
          g.setFont(ERRFONT);
          g.fillRect(xx+2, y+2, 26, 20);
          g.setColor(TAN);
          g.drawString(String.format("%04x", (utf32>>16)&0xffff), xx+4, y+11);
          g.drawString(String.format("%04x", (utf32>> 0)&0xffff), xx+4, y+20);
          g.setColor(Color.BLACK);
          g.setFont(FONT);
        }
      }
      i++;
    }
    g.setColor(VERY_GREEN);
    g.setFont(TINYFONT);
    i = pos - (cells-1)/2;
    for (int xx = x+45; xx < x+w-45; xx += 30) {
      g.drawString(""+i, xx+2, y+29);
      i++;
    }
    g.setFont(oldFont);
  }

  // private void drawAddress(Graphics2D g, int x, int y) {
  //   Font oldFont = g.getFont();
  //   g.setFont(ADDRFONT);
  //   g.setColor(Color.RED);
  //   GraphicsUtil.drawCenteredText(g, ""+384, x+180, y+50);
  //   g.setFont(oldFont);
  // }

  private void drawHead(Graphics2D g, int x, int y) {
    x -= 35;
    g.setColor(SILVER);
    g.fillRect(x, y+83, 70, 27);
    g.setColor(Color.BLACK);
    g.drawRect(x, y+83, 70, 27);
    g.setColor(RED);
    GraphicsUtil.switchToWidth(g, 8);
    g.drawRoundRect(x+10, y+10, 50, 70, 10, 10);
    GraphicsUtil.switchToWidth(g, 1);
    g.setColor(SILVER);
    g.fillRect(x+18, y+73, 34, 17);
    g.setColor(Color.BLACK);
    g.drawRect(x+18, y+73, 34, 17);
  }

  private static boolean inRoller(int x, int y, int xx, int yy) {
    return Bounds.create(x+13, y, 14, 100).contains(xx, yy)
        || Bounds.create(x, y+5, 40, 60).contains(xx, yy);
  }

  private static final int[] dirty = new int[] {0,0,0,17,0,0,0,0,0,0,35,0,22,0,0,0,0,19,0,0,0,0,0,0,0,0};
  private static final int[] dirtx = new int[] {1, 3, 6, 10, 15, 20, 25, 30, 34, 37, 39};

  private void drawRoller(Graphics2D g, int x, int y, PaperData state) {
    g.setColor(SILVER);
    g.fillRect(x+13, y, 14, 100);
    g.setColor(Color.BLACK);
    g.drawRect(x+13, y, 14, 100);
    g.setColor(DARK_SILVER);
    g.drawLine(x+15, y+3, x+15, y+98);

    g.setColor(SILVER);
    g.fillRect(x, y+5, 40, 60);

    g.setColor(DARK_SILVER);
    g.drawLine(x+2, y+5, x+2, y+65);
    g.drawLine(x+5, y+5, x+5, y+10); g.drawLine(x+5, y+21, x+5, y+30); g.drawLine(x+5, y+37, x+5, y+47); g.drawLine(x+5, y+58, x+5, y+65);
    g.drawLine(x+9, y+5, x+9, y+8); g.drawLine(x+9, y+22, x+9, y+28); g.drawLine(x+9, y+45, x+9, y+47); g.drawLine(x+9, y+61, x+9, y+65);

    boolean moving = state != null && state.isMoving();
    if (moving) {
      g.drawRect(x+0, y+12, 2, 5);
      g.drawRect(x+0, y+53, 2, 5);
      g.drawRect(x+6, y+12, 2, 5);
      g.drawRect(x+6, y+53, 2, 5);
      g.drawRect(x+13, y+12, 4, 5);
      g.drawRect(x+13, y+53, 4, 5);
      g.drawRect(x+23, y+12, 4, 5);
      g.drawRect(x+23, y+53, 4, 5);
      g.drawRect(x+31, y+12, 2, 5);
      g.drawRect(x+31, y+53, 2, 5);
      g.drawRect(x+38, y+12, 2, 5);
      g.drawRect(x+38, y+53, 2, 5);
    } else {
      g.drawRect(x+2, y+12, 2, 5);
      g.drawRect(x+2, y+53, 2, 5);
      g.drawRect(x+9, y+12, 3, 5);
      g.drawRect(x+9, y+53, 3, 5);
      g.drawRect(x+18, y+12, 4, 5);
      g.drawRect(x+18, y+53, 4, 5);
      g.drawRect(x+28, y+12, 3, 5);
      g.drawRect(x+28, y+53, 3, 5);
      g.drawRect(x+36, y+12, 2, 5);
      g.drawRect(x+36, y+53, 2, 5);
    }
    if (state != null) {
      int i = state.getPosition() * 6;
      if (state.isMovingLeft())
        i += 3;
      else if (state.isMovingRight())
        i -= 3;
      i = (-i) % dirty.length;
      if (i < 0)
        i += dirty.length;
      for (int j = 0; j < dirtx.length; j++) {
        int xx = dirtx[j];
        int yy = dirty[(i+j)%dirty.length];
        if (yy == 0)
          continue;
        g.drawLine(x+xx, y+yy, x+xx, y+yy+3);
      }
    }

    g.setColor(Color.BLACK);
    g.drawRect(x, y+5, 40, 60);
  }
  
  public static int inRoller(Bounds bds, int xx, int yy) {
    int x = bds.getX(), y = bds.getY(), w = bds.getWidth();
    if (inRoller(x+10, y+10, xx, yy))
      return +1;
    else if (inRoller(x+w-50, y+10, xx, yy))
      return -1;
    else
      return 0;
  }

  public static Bounds cellBounds(Bounds bds) {
    int cx = bds.getCenterX(), y = bds.getY();
    y += 20;
    return Bounds.create(cx-15, y+10, 30, 30);
  }

	@Override
	public void paintInstance(InstancePainter painter) {
    Graphics2D g = (Graphics2D)painter.getGraphics();
    Bounds bds = painter.getNominalBounds();
    int x = bds.getX(), y = bds.getY();
    int cells = painter.getAttributeValue(ATTR_SIZE);
    if (cells % 2 == 0)
      cells++;
    int w = 90 + cells * 30;

    PaperData state = painter.getShowState()? PaperData.get(painter) : null;
    drawTape(g, x, y+20, cells, state);
    drawState(g, x, y+30, cells, state);
    drawHead(g, x+w/2, y);
    drawRoller(g, x+10, y+10, state);
    drawRoller(g, x+w-50, y+10, state);

    g.setColor(Color.BLACK);
		painter.drawClock(CLK, Direction.NORTH);
		painter.drawPort(L, "L", Direction.SOUTH);
		painter.drawPort(R, "R", Direction.SOUTH);
		painter.drawPort(S, "S", Direction.SOUTH);
		painter.drawPort(P, "P", Direction.SOUTH);
		painter.drawPort(V, "V", Direction.SOUTH);
		painter.drawLabel();
	}

	@Override
	public void propagate(InstanceState s) {
		PaperData state = PaperData.get(s);
		boolean trigger = state.updateClock(s.getPortValue(CLK));
    state.setLRPV(new Value[] { s.getPortValue(L), s.getPortValue(R), s.getPortValue(P), s.getPortValue(V)});
		if (trigger) {
      if (s.getPortValue(P) == Value.TRUE) {
        Value v = s.getPortValue(V);
        if (v.isFullyDefined())
          state.print(v);
      }
      if (s.getPortValue(L) == Value.TRUE)
        state.moveLeft();
      if (s.getPortValue(R) == Value.TRUE)
        state.moveRight();
    }
		s.setPort(S, state.getValue(), 1);
	}
}
