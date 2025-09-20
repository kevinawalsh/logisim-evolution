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
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
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
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.GraphicsUtil;

public class MidiIn extends InstanceFactory {

  static final AttributeOption IFACE_SERIAL = MidiSink.IFACE_SERIAL;
  static final AttributeOption IFACE_PARALLEL3 = MidiSink.IFACE_PARALLEL3;
  static final AttributeOption IFACE_PARALLEL4 = MidiSink.IFACE_PARALLEL4;
  static final AttributeOption IFACE_LOGISIM1 = MidiSink.IFACE_LOGISIM1;
  static final AttributeOption IFACE_LOGISIM5 = MidiSink.IFACE_LOGISIM5;
  static final Attribute<AttributeOption> ATTR_IFACE = MidiSink.ATTR_IFACE;
  
  static final int CK = 0; // all interfaces
  static final int RE = 1; // all interfaces
  static final int RDY = 2; // all interfaces
  static final int OUT = 3; // serial, parallel3, parallel4, logisim1
  static final int NUM_PORTS_OTHER = 4;
  static final int NOTE = 3; // logisim5
  static final int VELO = 4; // logisim5
  static final int DAMP = 5; // logisim5
  static final int CHAN = 6; // logisim5
  static final int INST = 7; // logisim5
  static final int NUM_PORTS_LOGISIM5 = 8;

  public MidiIn() {
    super("MidiIn", S.getter("audioMidiInComponent"));
    setIconName("midiin.gif");
    setOffsetBounds(Bounds.create(-30, -30, 30, 60));
    setAttributes(new Attribute[] { StdAttr.EDGE_TRIGGER, ATTR_IFACE},
        new Object[] { StdAttr.TRIG_RISING, IFACE_SERIAL });
    setPorts(makePorts(IFACE_SERIAL));
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
      ps = new Port[8];
      ps[CK] = new Port(-10, 30, Port.INPUT, BitWidth.ONE);
      ps[CK].setToolTip(S.getter("midiClock"));
      ps[RE] = new Port(-20, 30, Port.INPUT, BitWidth.ONE);
      ps[RE].setToolTip(S.getter("midiReadEnable"));
      ps[RDY] = new Port(-10, -30, Port.OUTPUT, BitWidth.ONE);
      ps[RDY].setToolTip(S.getter("midiOutputReady"));
      ps[NOTE] = new Port(0, 0, Port.OUTPUT, BitWidth.EIGHT);
      ps[NOTE].setToolTip(S.getter("midiNote"));
      ps[VELO] = new Port(0, 10, Port.OUTPUT, BitWidth.SEVEN);
      ps[VELO].setToolTip(S.getter("midiVelocity"));
      ps[DAMP] = new Port(0, -10, Port.OUTPUT, BitWidth.ONE);
      ps[DAMP].setToolTip(S.getter("midiDamping"));
      ps[CHAN] = new Port(0, -20, Port.OUTPUT, BitWidth.FOUR);
      ps[CHAN].setToolTip(S.getter("midiChannel"));
      ps[INST] = new Port(0, 20, Port.OUTPUT, BitWidth.EIGHT);
      ps[INST].setToolTip(S.getter("midiInstrument"));
    } else { // IFACE_SERIAL, IFACE_PARALLEL3, IFACE_PARALLEL4, IFACE_LOGISIM1
      ps = new Port[4];
      ps[CK] = new Port(-10, 30, Port.INPUT, BitWidth.ONE);
      ps[CK].setToolTip(S.getter("midiClock"));
      ps[RE] = new Port(-20, 30, Port.INPUT, BitWidth.ONE);
      ps[RE].setToolTip(S.getter("midiEnable"));
      ps[RDY] = new Port(-10, -30, Port.OUTPUT, BitWidth.ONE);
      ps[RDY].setToolTip(S.getter("midiOutputReady"));
      if (iface == IFACE_SERIAL) {
        ps[OUT] = new Port(0, 0, Port.OUTPUT, BitWidth.of(8));
        ps[OUT].setToolTip(S.getter("midiOutputSerial"));
      } else if (iface == IFACE_PARALLEL3) {
        ps[OUT] = new Port(0, 0, Port.OUTPUT, BitWidth.of(24));
        ps[OUT].setToolTip(S.getter("midiOutputParallel3"));
      } else if (iface == IFACE_PARALLEL4) {
        ps[OUT] = new Port(0, 0, Port.OUTPUT, BitWidth.of(32));
        ps[OUT].setToolTip(S.getter("midiOutputParallel4"));
      } else { /* IFACE_LOGISIM1 */
        ps[OUT] = new Port(0, 0, Port.OUTPUT, BitWidth.of(32));
        ps[OUT].setToolTip(S.getter("midiOutputLogisim1"));
      }
    }
    return ps;
  }

  @Override
  public void propagate(InstanceState state) {
    enumerate();

    Object trigger = state.getAttributeValue(StdAttr.EDGE_TRIGGER);
    State data = getState(state);

    Value enable = state.getPortValue(RE);
    Value clock = state.getPortValue(CK);
    Value lastClock = data.setLastClock(clock);

    boolean advance;
    if (trigger == StdAttr.TRIG_FALLING) {
      advance = enable == Value.TRUE && lastClock == Value.TRUE && clock == Value.FALSE;
    } else {
      advance = enable == Value.TRUE && lastClock == Value.FALSE && clock == Value.TRUE;
    }
    
    AttributeOption iface = state.getAttributeValue(ATTR_IFACE);

    // check if new data is available, either 1 byte (serial) or 1-3 bytes (others)
    if (iface == IFACE_SERIAL) {

      boolean avail = false;
      int b0 = -1;
      synchronized (midiLock) {
        if (queue.n > 0) {
          b0 = advance ? queue.remove() : queue.peek(0);
          avail = true;
        }
      }
      state.setPort(RDY, avail ? Value.TRUE : Value.FALSE, 1);
      if (avail)
        state.setPort(OUT, Value.createKnown(BitWidth.of(8), b0), 1);
      return;

    } else {

      int max;
      if (iface == IFACE_PARALLEL3) max = 3;
      else if (iface == IFACE_PARALLEL4) max = 4;
      else max = 3; // IFACE_LOGISIM1 || IFACE_LOGISIM5

      int b0 = 0, b1 = 0, b2 = 0, b3 = 0;
      boolean avail = false, discard = true;
      while (discard) { /* examine queue */
        discard = false;
        avail = false;
        b0 = b1 = b2 = b3 = 0;
        synchronized (midiLock) {
          // remove stray partial messages
          while (queue.n > 0 && (queue.peek(0) & 0x80) == 0)
            queue.remove();
          // check for empty queue
          if (queue.n == 0)
            break; /* stop examine queue */
          // check MIDI status byte at head of queue 
          b0 = queue.peek(0);
          int expecting;
          if (((b0 >> 4) & 0xF) == 0x9) // MIDI NoteOn
            expecting = 2;
          else if (((b0 >> 4) & 0xF) == 0x8) // MIDI NoteOff
            expecting = 2;
          else if (((b0 >> 4) & 0xF) == 0xC) // MIDI Program Change
            expecting = 1;
          else // MIDI Reset or unrecognized
            expecting = 0;
          // check if enough parameter bytes
          if (expecting > 0) {
            avail = (queue.n > expecting);
          } else {
            avail = true;
            // take as many parameter bytes as can be found, up to max
            while (1+expecting < max &&
                1+expecting < queue.n &&
                (queue.peek(1+expecting) & 0x80) == 0)
              expecting++;
          }
          // check if success so far
          if (!avail)
            break; /* stop examine queue */
          // grab parameter bytes, discard message if incomplete
          if (expecting >= 1) {
            b1 = queue.peek(1);
            if ((b1 & 0x80) != 0) {
              System.out.println("MIDI: first param byte missing");
              queue.remove();
              continue; /* repeat examine queue */
            }
          }
          if (expecting >= 2) {
            b2 = queue.peek(2);
            if ((b2 & 0x80) != 0) {
              System.out.println("MIDI: second param byte missing");
              queue.remove();
              queue.remove();
              continue; /* repeat examine queue */
            }
          }
          if (expecting >= 3) {
            b3 = queue.peek(3);
            if ((b3 & 0x80) != 0) {
              System.out.println("MIDI: third param byte missing");
              queue.remove();
              queue.remove();
              queue.remove();
              continue; /* repeat examine queue */
            }
          }
          /* filter unusable messages if needed */
          if (iface == IFACE_LOGISIM1 || iface == IFACE_LOGISIM5) {
            if (((b0 >> 4) & 0xF) == 0x9) { // MIDI NoteOn
              // hurray
            } else if (((b0 >> 4) & 0xF) == 0x8) { // MIDI NoteOff
              // will handle below
            } else if (((b0 >> 4) & 0xF) == 0xC) { // MIDI Program Change
              int chan = b0 & 0xF;
              midiChannelInstrument[chan] = b1;
              discard = true;
            } else if (b0 == 0xff) { // MIDI Reset
              for (int chan = 0; chan < 16; chan++)
                midiChannelInstrument[chan] = 0;
              discard = true;
            } else { // Unrecognized
              discard = true;
            }
          }
          // advance queue if needed
          if (advance || discard) {
            for (int i = 0; i < 1 + expecting; i++)
              queue.remove();
          }
        } // synchronized
      } /* done examine queue */

      if (!avail) {
        state.setPort(RDY, Value.FALSE, 1);
        return;
      }

      state.setPort(RDY, Value.TRUE, 1);
      if (iface == IFACE_PARALLEL3) {
        state.setPort(OUT, Value.createKnown(BitWidth.of(24),
              ((b2&0xff) << 16) | ((b1&0xff) << 8) | (b0&0xff)), 1);
        return;
      } else if (iface == IFACE_PARALLEL4) {
        state.setPort(OUT, Value.createKnown(BitWidth.of(32),
              ((b3&0xff) << 24) | ((b2&0xff) << 16) | ((b1&0xff) << 8) | (b0&0xff)), 1);
        return;
      } else { // IFACE_LOGISIM1 || IFACE_LOGISIM5
        int stat = (b0 >> 4) & 0xF;
        int chan = b0 & 0xF;
        int note = (stat == 0x8) ? (256 - (b1&0x7f)) : (b1&0x7f);
        int velo = (b2&0x7f);
        int inst = midiChannelInstrument[chan] & 0xff;

        if (iface == IFACE_LOGISIM1) {
          Value v = Value.createKnown(BitWidth.of(32),
              (chan << 28) | (inst << 16) | (velo << 8) | note);
          state.setPort(OUT, v, 1);
        } else { /* IFACE_LOGISIM5 */
          state.setPort(NOTE, Value.createKnown(BitWidth.EIGHT, note), 1);
          state.setPort(VELO, Value.createKnown(BitWidth.SEVEN, velo), 1);
          state.setPort(DAMP, Value.FALSE, 1);
          state.setPort(CHAN, Value.createKnown(BitWidth.FOUR, chan), 1);
          state.setPort(INST, Value.createKnown(BitWidth.EIGHT, inst), 1);
        }
      }

    } /* which iface */

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

    int x = bds.x + (bds.width-24)/2;
    int y = bds.y + (bds.height-24)/2 - 10;
    g.setColor(Color.BLACK);
    g.drawOval(x, y, 24, 24);
    g.drawOval(x+2, y+10, 4, 4);
    g.drawOval(x+18, y+10, 4, 4);
    g.drawOval(x+4, y+4, 4, 4);
    g.drawOval(x+16, y+4, 4, 4);
    g.drawOval(x+10, y+2, 4, 4);
    g.drawArc(x+9, y+20, 6, 8, 10, 160);

    int n = numDevices;
    if (n > 0)
      g.setColor(Color.BLUE);
    else
      g.setColor(Color.RED);
    GraphicsUtil.drawText(g, ""+n, x+3, y+40, GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER);
    g.drawLine(x+12, y+27, x+12, y+36);
    g.drawLine(x+12, y+36, x+24, y+36);
    g.drawLine(x+24, y+36, x+19, y+31);
    g.drawLine(x+24, y+36, x+19, y+41);
  }

  // Global state, since the underlying midi system is global anyway
  static Object midiLock = new Object();
  static int numDevices = 0;
  static long lastEnumeration = 0;
  
  // static Synthesizer midiSynth = null;
  // static Releaser midiReleaser;
  // static Instrument[] midiInstrument;
  // static MidiChannel[] midiChannel;
  // static int numChannels;
  static int[] midiChannelInstrument = new int[] {
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };


  private static void enumerate() {
    long now = System.currentTimeMillis();
    synchronized(midiLock) {
      if (now - lastEnumeration < 5000)
        return;
      lastEnumeration = now;
        
      Debug.printf(1, "Enumerating MIDI devices...\n");
      HashSet<MidiDevice> found = new HashSet<>();
      try {
        MidiDevice.Info[] info = MidiSystem.getMidiDeviceInfo();
        Debug.printf(1, "%d MIDI devices found\n", info.length);
        for (int i = 0; i < info.length; i++) {
          Debug.printf(1, "Querying MIDI device %d: %s\n", i, info[i]);
          MidiDevice dev = MidiSystem.getMidiDevice(info[i]);
          if (dev.getMaxTransmitters() == 0)
            continue;
          if ((dev instanceof Sequencer) || (dev instanceof Synthesizer))
            continue;

          Rx r = openedDevices.get(dev);
          if (r != null) {
            Debug.printf(1, " * already opened, appears to be working normally\n");
            found.add(dev);
            continue;
          }
          try {
            dev.open();
            Debug.printf(1, " * opened device\n");
          } catch (Exception e) {
            Debug.printf(1, " * device not available: %s\n", e.getMessage());
            continue;
          }
          try {
            Transmitter t = dev.getTransmitter();
            r = new Rx(dev, t);
            t.setReceiver(r);
          } catch (Exception e) {
            try { dev.close(); }
            catch (Exception e2) { }
            Debug.printf(1, " * device failed: %s\n", e.getMessage());
            continue;
          }

          openedDevices.put(dev, r);
          found.add(dev);
        }
      } catch (MidiUnavailableException e) {
        e.printStackTrace();
      }

      ArrayList<MidiDevice> toClose = new ArrayList<>();
      for (MidiDevice dev : openedDevices.keySet()) {
        if (!found.contains(dev))
          toClose.add(dev);
      }
      if (!toClose.isEmpty()) {
        Debug.printf(1, "Closing %d stale MIDI devices...\n", toClose.size());
        for (MidiDevice dev: toClose) {
          Rx r = openedDevices.get(dev);
          r.close();
        }
      }

      numDevices = openedDevices.size();
      Debug.printf(1, "MIDI input from %d devices\n", numDevices);
    }
  }

  private State getState(InstanceState state) {
    State ret = (State) state.getDataFor();
    if (ret == null) {
      ret = new State();
      state.setData(ret);
    } else {
      // ret.update(...)
    }
    return ret;
  }

  // A single, global queue for all MidiIn components, all devices, etc.
  static Queue queue = new Queue();

  static class Queue {
    byte[] queue = new byte[1000];
    int s = 0, e = 0, n = 0;

    Queue() { }

    void add(byte[] b) {
      if (n+b.length > queue.length)
        return;
      for (int i = 0; i < b.length; i++) {
        queue[e] = b[i];
        e = (e + 1) % queue.length;
        n++;
      }
    }

    int remove() {
      if (n == 0) return -1; // should never happen
      int b = queue[s];
      s = (s + 1) % queue.length;
      n--;
      return b;
    }

    int peek(int i) {
      if (i >= n) return -1; // should never happen
      return queue[(s + i) % queue.length];
    }
  }

  static HashMap<MidiDevice, Rx> openedDevices = new HashMap<>();

  static class Rx implements Receiver {
    MidiDevice dev;
    Transmitter tx;

    Rx(MidiDevice dev, Transmitter tx) {
      this.dev = dev;
      this.tx = tx;
    }

    public void close() {
      Debug.println(1, " * Closed MIDI device: " + dev);
      synchronized(midiLock) {
        try { dev.close(); }
        catch (Exception e) { }
        openedDevices.remove(dev);
        numDevices--;
      }
    }

    public void send(MidiMessage message, long timeStamp) {
      byte[] msg = message.getMessage();
      if (Debug.verbose >= 1) {
        String s = String.format("%d: (%d bytes)", timeStamp, msg.length);
        for (int i = 0; i < msg.length; i++)
          s += String.format(" %02x", msg[i]);
        System.out.println(s);
      }
      synchronized(midiLock) {
        queue.add(msg);
      }
    }
  }

  static class State implements ComponentData {
    private Value lastClock = Value.UNKNOWN;
    // private int curData = -1;

    public State() { }

    public State(State other) {
      lastClock = other.lastClock;
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
