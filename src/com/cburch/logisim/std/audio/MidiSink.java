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

import java.awt.Graphics;

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

public class MidiSink extends InstanceFactory {

  private static Attribute<Integer> ATTR_HOLD = Attributes.forIntegerRange("hold", S.getter("midiHold"), 0, 1000);
  
  static final AttributeOption IFACE_SERIAL = new AttributeOption("serial",
      S.getter("midiInterfaceSerial"));
  static final AttributeOption IFACE_PARALLEL3 = new AttributeOption("parallel3",
      S.getter("midiInterfaceParallel3"));
  static final AttributeOption IFACE_PARALLEL4 = new AttributeOption("parallel4",
      S.getter("midiInterfaceParallel4"));
  static final AttributeOption IFACE_LOGISIM1 = new AttributeOption("logisim1",
      S.getter("midiInterfaceLogisim1"));
  static final AttributeOption IFACE_LOGISIM5 = new AttributeOption("logisim5",
      S.getter("midiInterfaceLogisim5"));
  static final Attribute<AttributeOption> ATTR_IFACE = 
      Attributes.forOption("interface", S.getter("midiInterfaceOption"),
          new AttributeOption[] { IFACE_SERIAL, IFACE_PARALLEL3, IFACE_PARALLEL4, IFACE_LOGISIM1, IFACE_LOGISIM5 });

  // port numbers
  static final int CK = 0; // all interfaces
  static final int WE = 1; // all interfaces
  static final int IN = 2; // serial, parallel3, parallel4, logisim1
  static final int NUM_PORTS_OTHER = 3;
  static final int NOTE = 2; // logisim5
  static final int VELO = 3; // logisim5
  static final int DAMP = 4; // logisim5
  static final int CHAN = 5; // logisim5
  static final int INST = 6; // logisim5
  static final int NUM_PORTS_LOGISIM5 = 7;


  public MidiSink() {
    super("MidiSink", S.getter("audioMidiSinkComponent"));
    setIconName("midisink.gif");
    setOffsetBounds(Bounds.create(-30, -30, 30, 60));
    setPorts(makePorts(IFACE_LOGISIM5));
  }

  @Override
  public AttributeSet createAttributeSet() {
    // We defer init to here, so that Midi is only initialized when being used
    if (init()) {
      setAttributes(new Attribute[] { StdAttr.EDGE_TRIGGER, dev.ATTR_INSTR, dev.ATTR_CHAN, ATTR_HOLD, ATTR_IFACE },
          new Object[] { StdAttr.TRIG_RISING, dev.defaultInstrument(), 0, 250, IFACE_LOGISIM5 });
    }
    return super.createAttributeSet();
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.setPorts(makePorts(instance.getAttributeValue(ATTR_IFACE)));
    instance.addAttributeListener();
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_IFACE) {
      instance.setPorts(makePorts(instance.getAttributeValue(ATTR_IFACE)));
    }
  }


  private static Port[] makePorts(AttributeOption iface) {
    Port[] ps;
    if (iface == IFACE_LOGISIM5) {
      ps = new Port[7];
      ps[CK] = new Port(-10, 30, Port.INPUT, BitWidth.ONE);
      ps[CK].setToolTip(S.getter("midiClock"));
      ps[WE] = new Port(-20, 30, Port.INPUT, BitWidth.ONE);
      ps[WE].setToolTip(S.getter("midiWriteEnable"));
      ps[NOTE] = new Port(-30, 0, Port.INPUT, BitWidth.EIGHT);
      ps[NOTE].setToolTip(S.getter("midiNote"));
      ps[VELO] = new Port(-30, 10, Port.INPUT, BitWidth.SEVEN);
      ps[VELO].setToolTip(S.getter("midiVelocity"));
      ps[DAMP] = new Port(-30, -10, Port.INPUT, BitWidth.ONE);
      ps[DAMP].setToolTip(S.getter("midiDamping"));
      ps[CHAN] = new Port(-30, -20, Port.INPUT, BitWidth.FOUR);
      ps[CHAN].setToolTip(S.getter("midiChannel"));
      ps[INST] = new Port(-30, 20, Port.INPUT, BitWidth.EIGHT);
      ps[INST].setToolTip(S.getter("midiInstrument"));
    } else { // IFACE_SERIAL, IFACE_PARALLEL3, IFACE_PARALLEL4, IFACE_LOGISIM1
      ps = new Port[3];
      ps[CK] = new Port(-10, 30, Port.INPUT, BitWidth.ONE);
      ps[CK].setToolTip(S.getter("midiClock"));
      ps[WE] = new Port(-20, 30, Port.INPUT, BitWidth.ONE);
      ps[WE].setToolTip(S.getter("midiEnable"));
      if (iface == IFACE_SERIAL) {
        ps[IN] = new Port(-30, 0, Port.INPUT, BitWidth.of(8));
        ps[IN].setToolTip(S.getter("midiInputSerial"));
      } else if (iface == IFACE_PARALLEL3) {
        ps[IN] = new Port(-30, 0, Port.INPUT, BitWidth.of(24));
        ps[IN].setToolTip(S.getter("midiInputParallel3"));
      } else if (iface == IFACE_PARALLEL4) {
        ps[IN] = new Port(-30, 0, Port.INPUT, BitWidth.of(32));
        ps[IN].setToolTip(S.getter("midiInputParallel4"));
      } else { /* IFACE_LOGISIM1 */
        ps[IN] = new Port(-30, 0, Port.INPUT, BitWidth.of(32));
        ps[IN].setToolTip(S.getter("midiInputLogisim1"));
      }
    }
    return ps;
  }

  @Override
  public void propagate(InstanceState circState) {
    if (dev == null)
      return;

    Object trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
    State data = getState(circState);

    Value enable = circState.getPortValue(WE);
    Value clock = circState.getPortValue(CK);
    Value lastClock = data.setLastClock(clock);
    if (enable == Value.FALSE)
      return;
    boolean go;
    if (trigger == StdAttr.TRIG_FALLING) {
      go = lastClock == Value.TRUE && clock == Value.FALSE;
    } else {
      go = lastClock == Value.FALSE && clock == Value.TRUE;
    }
    if (!go)
      return;
    
    AttributeOption iface = circState.getAttributeValue(ATTR_IFACE);

    if (iface == IFACE_LOGISIM5 || iface == IFACE_LOGISIM1) {
      int note, velo, damp, chan, inst;
      int hold = circState.getAttributeValue(ATTR_HOLD);
      if (iface == IFACE_LOGISIM5) {
        // five inputs, logisim style
        note = circState.getPortValue(NOTE).toIntValue();
        velo = circState.getPortValue(VELO).toIntValue();
        damp = circState.getPortValue(DAMP).toIntValue();
        chan = circState.getPortValue(CHAN).toIntValue();
        inst = circState.getPortValue(INST).toIntValue();
      } else {
        // one combined 32-bit input, logisim style, format as follows:
        //     xxxD CCCC IIII IIII xVVV VVVV NNNN NNNN
        // Where:
        //  D is a 1-bit "damper" control
        //  CCCC is a 4-bit channel selector
        //  IIIIIIIII is an 8-bit instrument selector
        //  VVVVVVVV is a 7-bit velocity selector
        //  NNNNNNNNN is an 8-bit note selector
        // and the x bits are ignored.
        Value v = circState.getPortValue(IN);
        note = v.extract(0, 8).toIntValue();
        velo = v.extract(8, 15).toIntValue();
        chan = v.extract(16, 24).toIntValue();
        inst = v.extract(24, 28).toIntValue();
        damp = v.extract(28, 29).toIntValue();
      }

      if (velo < 0)
        velo = 127;

      if (damp < 0)
        damp = 1;

      if (chan < 0)
        chan = circState.getAttributeValue(dev.ATTR_CHAN);
      if (chan < 0) chan = 0;
      if (chan >= dev.numChannels) chan = dev.numChannels - 1;

      if (inst < 0) {
        // s = "###: name"
        String s = circState.getAttributeValue(dev.ATTR_INSTR).toString();
        inst = Integer.parseInt(s.substring(0, s.indexOf(':')));
      }
      
      dev.setProgram(chan, inst, volume);
      dev.play(note, velo, damp, chan, hold);
    } else if (iface == IFACE_PARALLEL3 || iface == IFACE_PARALLEL4) {
      // 3 or 4 bytes: MIDI control byte and 0, 1 or 2 MIDI parameter bytes, rest unused
      // 4-byte Big Endian:
      //    1SSS CCCC  .... ....  .... ....  0... ....
      //    1SSS CCCC  0PPP PPPP  .... ....  0... ....
      //    1SSS CCCC  0NNN NNNN  0VVV VVVV  0... ....
      // 3-byte Little Endian:
      //    0... ....  .... ....  .... ....  1SSS CCCC
      //    0... ....  .... ....  0PPP PPPP  1SSS CCCC
      //    0... ....  0VVV VVVV  0NNN NNNN  1SSS CCCC
      // 3-byte Big Endian:
      //               1SSS CCCC  .... ....  .... ....
      //               1SSS CCCC  0PPP PPPP  .... ....
      //               1SSS CCCC  0NNN NNNN  0VVV VVVV
      // 3-byte Little Endian:
      //               0... ....  .... ....  1SSS CCCC
      //               0... ....  0PPP PPPP  1SSS CCCC
      //               0VVV VVVV  0NNN NNNN  1SSS CCCC
      Value v = circState.getPortValue(IN);
      int w = v.getWidth();

      int b0, b1, b2;
      if (v.get(7) == Value.TRUE) {
        // b0 at little end
        b0 = v.extract(0, 8).toIntValue();
        b1 = v.extract(8, 16).toIntValue();
        b2 = v.extract(16, 24).toIntValue();
      } else if (v.get(w-1) == Value.TRUE) {
        b2 = v.extract(w-24, w-16).toIntValue();
        b1 = v.extract(w-16, w-8).toIntValue();
        b0 = v.extract(w-8, w).toIntValue();
        // b0 at big end
      } else {
        return;
      }
      process(b0, b1, b2);

    } else { // IFACE_SERIAL
      int b = circState.getPortValue(IN).toIntValue();
      if (b < 0)
        return;
      if ((b & 0x80) == 0) {
        // parameter byte
        if (data.expecting > 0) {
          data.param[2-data.expecting] = b;
          data.expecting--;
          if (data.expecting == 0)
            process(data);
        }
      } else {
        // control byte
        data.cmd = b;
        if (((b >> 4) & 0xF) == 0x9) // MIDI NoteOn
          data.expecting = 2;
        else if (((b >> 4) & 0xF) == 0x8) // MIDI NoteOff
          data.expecting = 2;
        else if (((b >> 4) & 0xF) == 0xC) // MIDI Program Change
          data.expecting = 1;
        else // MIDI Reset or unrecognized
          data.expecting = 0;
        if (data.expecting == 0)
          process(data);
      }
    }
  }

  static void process(State data) {
    int b0 = data.cmd;
    int b1 = data.param[0];
    int b2 = data.param[1];
      
    data.cmd = -1;
    data.param[0] = -1;
    data.param[1] = -1;

    process(b0, b1, b2);
  }

  static void process(int b0, int b1, int b2) {
    if (b0 < 0)
      return;

    int stat = (b0 >> 4) & 0xF;
    int chan = (b0 >> 0) & 0xF;

    if (stat == 0x9) {
      // MIDI NoteOn control message
      int note = b1;
      int velo = b2;
      if (note < 0 || note >= 128 || velo < 0 || velo >= 128 || chan >= dev.numChannels)
        return;
      dev.play(note, velo, 0, chan, 0);
    } else if (stat == 0x8) {
      // MIDI NoteOff control message
      int note = b1;
      int velo = b2;
      if (note < 0 || note >= 128 || velo < 0 || velo >= 128 || chan >= dev.numChannels)
        return;
      dev.play(256 - note, velo, 0, chan, 0);
    } else if (stat == 0xC) {
      // MIDI Program Change control message (set instrument)
      int prog = b1;
      if (prog < 0 || prog >= 128)
        return;
      dev.setProgram(chan, prog, volume); 
    } else if (stat == 0xF && chan == 0xF) {
      // MIDI Reset control message
      dev.resetAllChannels();
    } else {
      // not yet supported
    }

  }

  @Override
  public void paintInstance(InstancePainter painter) {
    painter.drawBounds();
    int numPorts;
    if (painter.getAttributeValue(ATTR_IFACE) == IFACE_LOGISIM5)
      numPorts = NUM_PORTS_LOGISIM5;
    else
      numPorts = NUM_PORTS_OTHER;

    painter.drawClock(CK, Direction.NORTH); // port number 0
    for (int i = 1; i < numPorts; i++)
      painter.drawPort(i);

    Bounds bds = painter.getNominalBounds();
    Graphics g = painter.getGraphics();

    int x = bds.x + bds.width/2;
    int y = bds.y + bds.height/2;
    String err = null; // TODO
    MidiDevice.paintSpeakerIcon(g, x, y, err != null, dev != null);
  }

  // Global state, since the underlying midi system is global anyway
  private static Object initLock = new Object();
  private static boolean initFailed = false;
  private static MidiDevice dev;
  static final int volume = 127;

  private static boolean init() {
    synchronized(initLock) {
      if (dev == null && !initFailed) {
        dev = MidiDevice.open();
        if (dev == null)
          initFailed = true;
      }
      return dev != null;
    }
  }

  private State getState(InstanceState state) {
    State ret = (State) state.getDataAsCustom();
    if (ret == null) {
      ret = new State();
      state.setData(ret);
    }
    return ret;
  }

  static class State implements ComponentData {
    private Value lastClock;
    private int cmd = -1;
    private int[] param = new int[] { -1, -1 };
    private int expecting = 0;

    public State() {
      lastClock = Value.UNKNOWN;
    }
    
    private State(State other) {
      lastClock = other.lastClock;
      cmd = other.cmd;
      param = other.param.clone();
      expecting = other.expecting;
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
