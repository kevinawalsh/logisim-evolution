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

package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Joystick extends InstanceFactory {
  public static class Poker extends InstancePoker {
    @Override
    public void mouseDragged(InstanceState state, MouseEvent e) {
      Location loc = state.getInstance().getLocation();
      int cx = loc.getX() - 15;
      int cy = loc.getY() + 5;
      updateState(state, e.getX() - cx, e.getY() - cy);
    }

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      mouseDragged(state, e);
    }

    @Override
    public void mouseReleased(InstanceState state, MouseEvent e) {
      if (state.getAttributeValue(RETURN_TO_CENTER))
        updateState(state, 0, 0);
    }

    @Override
    public void paint(InstancePainter painter) {
      State state = (State) painter.getData();
      if (state == null) {
        state = new State(0, 0);
        painter.setData(state);
      }
      Graphics g = painter.getGraphics();
      Location loc = painter.getLocation();
      int x = loc.getX();
      int y = loc.getY();
      int dx = state.xPos;
      int dy = state.yPos;
      Color ballColor = painter.getAttributeValue(Io.ATTR_COLOR);
      drawBall(g, x, y, dx, dy, ballColor, true);
    }

    private void updateState(InstanceState state, int dx, int dy) {
      State s = (State) state.getData();
      if (dx < -15)
        dx = -15;
      if (dy < -15)
        dy = -15;
      if (dx > 15)
        dx = 15;
      if (dy > 15)
        dy = 15;
      if (s == null) {
        s = new State(dx, dy);
        state.setData(s);
      } else {
        s.xPos = dx;
        s.yPos = dy;
      }
      state.getInstance().fireInvalidated();
    }
  }

  private static class State implements ComponentData {
    private int xPos;
    private int yPos;

    public State(int x, int y) {
      xPos = x;
      yPos = y;
    }

    State(State other) {
      xPos = other.xPos;
      yPos = other.yPos;
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }
  }

  private static void drawBall(Graphics g, int x, int y, int dx, int dy,
      Color c, boolean inColor) {
    if (inColor && c == null) {
      c = Color.RED;
    } else if (!inColor) {
      int hue = c == null ? 128 : (c.getRed() + c.getGreen() + c.getBlue()) / 3;
      c = new Color(hue, hue, hue);
    }
    int x0 = x - 15 + (dx > 5 ? 1 : dx < -5 ? -1 : 0);
    int y0 = y + 5 + (dy > 5 ? 1 : dy < 0 ? -1 : 0);
    int x1 = x - 15 + dx;
    int y1 = y + 5 + dy;
    g.setColor(Color.WHITE);
    g.fillRect(x - 20, y, 10, 10);
    GraphicsUtil.switchToWidth(g, 3);
    g.setColor(Color.BLACK);
    g.drawLine(x0, y0, x1, y1);
    g.setColor(c);
    GraphicsUtil.switchToWidth(g, 1);
    g.fillOval(x1 - 4, y1 - 4, 8, 8);
    g.setColor(Color.BLACK);
    g.drawOval(x1 - 4, y1 - 4, 8, 8);
  }

  public static final Attribute<Boolean> RETURN_TO_CENTER
      = Attributes.forBoolean("returnToCenter", S.getter("returnToCenter"));
  static final Attribute<BitWidth> ATTR_WIDTH
      = Attributes.forBitWidth("bits", S.getter("ioBitWidthAttr"), 2, 5);

  public Joystick() {
    super("Joystick", S.getter("joystickComponent"));
    setAttributes(new Attribute[] { ATTR_WIDTH, StdAttr.MODE, RETURN_TO_CENTER, Io.ATTR_COLOR },
        new Object[] { BitWidth.create(4), StdAttr.UNSIGNED_OPTION, true, Color.RED });
    setKeyConfigurator(new BitWidthConfigurator(ATTR_WIDTH, 2, 5));
    setOffsetBounds(Bounds.create(-30, -10, 30, 30));
    setIconName("joystick.gif");
    setPorts(new Port[] { new Port(0, 0, Port.OUTPUT, ATTR_WIDTH),
      new Port(0, 10, Port.OUTPUT, ATTR_WIDTH), });
    setInstancePoker(Poker.class);
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRoundRect(-30, -10, 30, 30, 8, 8);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Location loc = painter.getLocation();
    int x = loc.getX();
    int y = loc.getY();

    Graphics g = painter.getGraphics();
    g.drawRoundRect(x - 30, y - 10, 30, 30, 8, 8);
    g.drawRoundRect(x - 28, y - 8, 26, 26, 4, 4);
    State state = (State) painter.getData();
    int dx = state == null ? 0 : state.xPos;
    int dy = state == null ? 0 : state.yPos;
    drawBall(g, x, y, dx, dy, painter.getAttributeValue(Io.ATTR_COLOR),
        painter.shouldDrawColor());
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    BitWidth bits = state.getAttributeValue(ATTR_WIDTH);
    State s = (State) state.getData();
    int xpos = s == null ? 0 : s.xPos;
    int ypos = s == null ? 0 : s.yPos;

    // If bitWidth = 2, output =                   [1 (2) 3 ]
    // If bitWidth = 3, output =               [1 2 3 (4) 5 6 7 ]
    // If bitWidth = 4, output =       [1 2 3 4 5 6 7 (8) 9 A B C D E F ]
    // If bitWidth = 5, output = [1 2 3 4 ... 13 14 15 (16) 17 18 19 ... 28 29 30 31 ]

    // If bitWidth = 3, max = 7
    //   -15 <= pos <= +15
    //   0 <= (pos + 15) * max <= 30 * max
    //   0 <= (pos + 15) * max / 31 < max
    //   1 <= (pos + 15) * max / 31 <= max
    int max = (1 << bits.getWidth()) - 1;
    int xout = (xpos + 15) * max / 31 + 1;
    int yout = (ypos + 15) * max / 31 + 1;
    if (state.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION) {
      xout -= (max+1)/2;
      yout -= (max+1)/2;
    }
    state.setPort(0, Value.createKnown(bits, xout), 1);
    state.setPort(1, Value.createKnown(bits, yout), 1);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    instance.fireInvalidated();
  }
}
