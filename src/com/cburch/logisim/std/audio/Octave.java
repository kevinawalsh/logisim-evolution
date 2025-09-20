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

package com.cburch.logisim.std.audio;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.arith.Comparator;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Octave extends InstanceFactory {

  static final Attribute<Integer> ATTR_OCTAVE
    = Attributes.forIntegerRange("octave", S.unlocalized("Octave"), 1, 7);
  static final Attribute<Boolean> ATTR_CENTER_ON_C
    = Attributes.forBoolean("centerOnC", S.unlocalized("Center on C"));

  // port numbers
  static final int CK = 0;
  static final int WE = 1;
  static final int IN0 = 2; // A or C

  static final String flat = "\u266D";
  static final String[] names = {
    "A", "A#/B"+flat, "B", "C", "C#/D"+flat, "D", "D#/E"+flat, "E", "F", "F#/G"+flat, "G", "G#/A"+flat
  };

  public Octave() {
    super("Octave", S.getter("audioOctaveComponent"));
    setIconName("octave.gif");
    setOffsetBounds(Bounds.create(-50, -65, 80, 120));
    setPorts(makePorts(true));
  }

  private static String nameOf(boolean centerOnC, int i) {
    int offset = centerOnC ? 3 : 0;
    return names[(i+offset)%12];
  }

  private static Port[] makePorts(boolean centerOnC) {
    Port[] ps = new Port[14];
    ps[CK] = new Port(20, 30, Port.INPUT, BitWidth.ONE);
    ps[CK].setToolTip(S.getter("octaveClock"));
    ps[WE] = new Port(10, 30, Port.INPUT, BitWidth.ONE);
    ps[WE].setToolTip(S.getter("octaveWriteEnable"));
    for (int i = 0; i < 12; i++) {
      ps[IN0+i] = new Port(-50, (i-6)*10, Port.INPUT, BitWidth.ONE);
      ps[IN0+i].setToolTip(S.unlocalized(nameOf(centerOnC, i)));
    }
    return ps;
  }

  @Override
  public AttributeSet createAttributeSet() {
    // We defer init to here, so that output stream is only initialized when being used
    setAttributes(new Attribute[] {
      StdAttr.EDGE_TRIGGER, ATTR_OCTAVE, ATTR_CENTER_ON_C },
        new Object[] {
          StdAttr.TRIG_RISING, Integer.valueOf(4), Boolean.TRUE });
    return super.createAttributeSet();
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.setPorts(makePorts(instance.getAttributeValue(ATTR_CENTER_ON_C)));
    instance.addAttributeListener();
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_CENTER_ON_C) {
      instance.setPorts(makePorts(instance.getAttributeValue(ATTR_CENTER_ON_C)));
    }
  }

  @Override
  public void propagate(InstanceState circState) {
    State data = getState(circState);
    if (data.out == null)
      return;

    Value enable = circState.getPortValue(WE);
    if (enable == Value.FALSE)
      return;

    Object trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
    Value clock = circState.getPortValue(CK);
    Value lastClock = data.setLastClock(clock);

    boolean go;
    if (trigger == StdAttr.TRIG_FALLING) {
      go = lastClock == Value.TRUE && clock == Value.FALSE;
    } else {
      go = lastClock == Value.FALSE && clock == Value.TRUE;
    }
    if (!go)
      return;

    data.out.silenceChannel(0);

    // if (enable == Value.FALSE) {
    //   for (int i = 0; i < 12; i++)
    //     data.on[i] = false;
    //   return;
    // }

    boolean onC = circState.getAttributeValue(ATTR_CENTER_ON_C);
    int octave = circState.getAttributeValue(ATTR_OCTAVE);
    int offset = onC ? 24 : 21;

    for (int i = 0; i < 12; i++) {
      boolean play = circState.getPortValue(IN0+i) == Value.TRUE;
      data.on[i] = play;
      if (play == true) {
        int note = offset + octave*12 + i;
        int velocity = 127;
        data.out.play(note, velocity, 0, 0, 0);
      }
    }

  }

  private static final Font FONT = new Font("monospaced", Font.PLAIN, 8);

  @Override
  public void paintInstance(InstancePainter painter) {
    State data = (State) painter.getDataFor();
    Bounds bds = painter.getNominalBounds();
    Graphics g = painter.getGraphics();
    
    int x = bds.x;
    int y = bds.y;
    int w = bds.width;
    int h = bds.height;

    // painter.drawBounds();
    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.BLACK);
    g.drawRect(x+30, y+65-30, w-30, 60);
    GraphicsUtil.switchToWidth(g, 1);
    MidiDevice.paintSpeakerIcon(g, x+65, y+65, data != null && data.out != null);

    painter.drawClock(CK, Direction.NORTH); // port number 0
    painter.drawPort(WE);
    for (int i = 0; i < 12; i++)
      painter.drawPort(IN0+i);

    boolean onC = painter.getAttributeValue(ATTR_CENTER_ON_C);

    int k = 0;
    for (int i = 0; i < 12; i++) {
      String name = nameOf(onC, i);
      if (name.length() != 1) continue;
      int y0 = y + 17*k;
      k++;
      g.setColor((data != null && data.on[i]) ? Color.YELLOW : Color.WHITE);
      g.fillRect(x, y0, 50, 17);
      g.setColor(Color.BLACK);
      g.drawRect(x, y0, 50, 17);
      GraphicsUtil.drawText(g, FONT, name, 
          x+5, y0+8, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER) ;
    }
    k = 0;
    for (int i = 0; i < 12; i++) {
      String name = nameOf(onC, i);
      if (name.length() == 1) {
        k++;
        continue;
      }
      int y0 = y + 17*k;
      g.setColor(Color.BLACK);
      g.drawRect(x+20, y0-12/2, 30, 12);
      g.setColor((data != null && data.on[i]) ? Color.YELLOW : Color.BLACK);
      g.fillRect(x+20, y0-12/2, 30, 12);
      g.setColor(Color.WHITE);
      GraphicsUtil.drawText(g, FONT, name, 
          x+25, y0-1, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER) ;
    }

  }

  private State getState(InstanceState state) {
    State ret = (State) state.getDataFor();
    if (ret == null) {
      ret = new State(state);
      state.setData(ret);
    }
    return ret;
  }
  
  static class State implements ComponentData {
    private Value lastClock = Value.UNKNOWN;
    private MidiDevice out;
    boolean[] on = new boolean[12];

    public State(InstanceState circState) {
      out = MidiDevice.open();
    }

    public State(State orig) {
      out = MidiDevice.open();
      lastClock = orig.lastClock;
      on = orig.on.clone();
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }

    public Value setLastClock(Value newClock) {
      Value ret = lastClock;
      lastClock = newClock;
      return ret;
    }

  }

}
