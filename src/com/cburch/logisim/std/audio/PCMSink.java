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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.arith.Comparator;
import com.cburch.logisim.tools.key.BitWidthConfigurator;

public class PCMSink extends InstanceFactory {

  static final AttributeOption RATE_16KHZ = new AttributeOption("16 kHz", S.unlocalized("16 kHz"));
  static final AttributeOption RATE_32KHZ = new AttributeOption("32 kHz", S.unlocalized("32 kHz"));
  static final AttributeOption RATE_64KHZ = new AttributeOption("64 kHz", S.unlocalized("64 kHz"));
  static final Attribute<AttributeOption> ATTR_RATE = Attributes.forOption(
      "rate", S.getter("audioSampleRate"), new AttributeOption[] { RATE_16KHZ, RATE_32KHZ, RATE_64KHZ });

  static Attribute<Integer> ATTR_BUFSIZE = Attributes.forIntegerRange("bufsize", S.getter("audioBufferCapacity"), 16, 16*1024);

  // port numbers
  static final int CK = 0;
  static final int WE = 1;
  static final int IN = 2;


  public PCMSink() {
    super("PCMSink", S.getter("audioPCMSinkComponent"));
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIconName("midisink.gif"); // same icon
    setOffsetBounds(Bounds.create(-30, -20, 30, 40));
    Port[] ps = new Port[3];
    ps[CK] = new Port(-10, 20, Port.INPUT, BitWidth.ONE);
    ps[CK].setToolTip(S.getter("pcmClock"));
    ps[WE] = new Port(-20, 20, Port.INPUT, BitWidth.ONE);
    ps[WE].setToolTip(S.getter("pcmWriteEnable"));
    ps[IN] = new Port(-30, 0, Port.INPUT, StdAttr.WIDTH);
    ps[IN].setToolTip(S.getter("pcmInput"));
    setPorts(ps);
  }

  @Override
  public AttributeSet createAttributeSet() {
    // We defer init to here, so that output stream is only initialized when being used
    setAttributes(new Attribute[] {
      StdAttr.EDGE_TRIGGER, ATTR_RATE, StdAttr.WIDTH, StdAttr.MODE, ATTR_BUFSIZE },
        new Object[] {
          StdAttr.TRIG_RISING, RATE_32KHZ, BitWidth.EIGHT, StdAttr.UNSIGNED_OPTION, Integer.valueOf(512) });
    return super.createAttributeSet();
  }

  @Override
  public void propagate(InstanceState circState) {
    State data = getState(circState);
    if (data.out == null)
      return;

    Object trigger = circState.getAttributeValue(StdAttr.EDGE_TRIGGER);
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

    int sample = circState.getPortValue(IN).toIntValue();
    if (sample < 0)
      return;

    if (data.count > data.buf.length)
      return;

    // System.out.printf("Buf has %d of %d bytes filled, writing %d byte sample\n", data.count, data.buf.length, data.bytesPerSample);
    for (int i = 0; i < data.bytesPerSample; i++) {
      data.buf[data.count++] = (byte)(sample & 0xff);
      sample = sample >> 8;
    }

    if (data.count < data.buf.length)
      return;

    if (!data.out.isOpen()) {
      try {
        data.out.open(data.fmt, data.buflen*data.bytesPerSample - data.buf.length);
      } catch (LineUnavailableException e) {
        System.out.println(e.getMessage());
        data.out = null;
        data.count = 0;
        return;
      }
    }

    int n = data.out.write(data.buf, 0, data.count);
    if (n <= 0) {
      data.out.close();
      data.out = null;
      data.count = 0;
    } else if (n < data.count) {
      data.out.start();
      System.arraycopy(data.buf, data.count-n,
          data.buf, 0, data.count - n);
      data.count -= n;
    } else {
      data.out.start();
      data.count = 0;
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    State data = (State) painter.getDataFor();
    Bounds bds = painter.getNominalBounds();
    Graphics g = painter.getGraphics();

    if (data != null && data.out != null && data.out.isOpen()) {
      int n = data.count + (data.out.getBufferSize() - data.out.available());
      int m = data.buf.length + data.out.getBufferSize();
      int h = bds.height * n/m;
      g.setColor(Color.LIGHT_GRAY);
      g.fillRect(bds.x, bds.y + bds.height - h, bds.width, h);
    }
    painter.drawBounds();
    painter.drawClock(CK, Direction.NORTH); // port number 0
    painter.drawPort(WE);
    painter.drawPort(IN);

    int x = bds.x + bds.width/2;
    int y = bds.y + bds.height/2;
    MidiDevice.paintSpeakerIcon(g, x, y, data != null && data.out != null
        && data.out.isOpen() && data.out.isActive() && data.out.isRunning()
        && data.out.available() < data.out.getBufferSize());
  }

  private State getState(InstanceState state) {
    State ret = (State) state.getDataFor();
    if (ret == null) {
      ret = new State(state);
      state.setData(ret);
    } else {
      ret.update(state);
    }
    return ret;
  }
  
  private static int rateOf(AttributeOption opt) {
    if (opt == RATE_16KHZ) return 16000;
    if (opt == RATE_32KHZ) return 32000;
    if (opt == RATE_32KHZ) return 64000;
    return 32000;
  }

  static class State implements ComponentData {
    private Value lastClock = Value.UNKNOWN;
    private byte[] buf; // approx 25% of buflen*bytesPerSample
    private int count = 0;
    private int buflen, bitsPerSample, bytesPerSample;
    private SourceDataLine out;
    private AudioFormat fmt;
    private AttributeOption rateOption;
    private boolean signed;

    public State(InstanceState circState) {
      int b = circState.getAttributeValue(ATTR_BUFSIZE);
      int s = circState.getAttributeValue(StdAttr.WIDTH).getWidth();
      AttributeOption r = circState.getAttributeValue(ATTR_RATE);
      boolean g = circState.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
      init(b, s, r, g);
    }

    public State(State orig) {
      lastClock = orig.lastClock;
      init(orig.buflen, orig.bitsPerSample, orig.rateOption, orig.signed);
    }

    void init(int b, int s, AttributeOption r, boolean g) {
      buflen = b;
      bitsPerSample = s;
      rateOption = r;
      signed = g;
      int rate = rateOf(rateOption);
      bytesPerSample = (bitsPerSample + 7)/8;
      // buf = new byte[(buflen/4) * bytesPerSample]; // out provides 75% of buffering
      buf = new byte[bytesPerSample]; // minimual buffering
      int channels = 1;
      fmt = new AudioFormat(
          signed ? AudioFormat.Encoding.PCM_SIGNED : AudioFormat.Encoding.PCM_UNSIGNED,
          rate, bitsPerSample, channels,
          bytesPerSample*channels, rate*bytesPerSample*channels, false);
      try {
        out = AudioSystem.getSourceDataLine(fmt);
      } catch (LineUnavailableException e) {
        System.out.println(e.getMessage());
        out = null;
      }
    }

    void update(InstanceState circState) {
      int b = circState.getAttributeValue(ATTR_BUFSIZE);
      int s = circState.getAttributeValue(StdAttr.WIDTH).getWidth();
      AttributeOption r = circState.getAttributeValue(ATTR_RATE);
      boolean g = circState.getAttributeValue(StdAttr.MODE) == StdAttr.SIGNED_OPTION;
      if (r == rateOption && b == buflen && s == bitsPerSample && g == signed)
        return;
      if (out != null) {
        if (out.isOpen())
          out.close();
        out = null;
      }
      init(b, s, r, g);
    }
          
    // @Override
    // public void finalize() {
    //   if (out != null && out.isOpen())
    //     out.close();
    // }
    
    // FIXME: hook into new CircuitState active/inactive tracking

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
