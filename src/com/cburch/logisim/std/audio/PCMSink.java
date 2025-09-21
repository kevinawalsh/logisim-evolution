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

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
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
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class PCMSink extends InstanceFactory {

  static final AttributeOption SIGNED = StdAttr.SIGNED_OPTION;
  static final AttributeOption UNSIGNED = StdAttr.UNSIGNED_OPTION;
  static final AttributeOption FLOAT = new AttributeOption("float", S.unlocalized("float")); // not exposed in UI

  // static final AttributeOption RATE_5KHZ = new AttributeOption("5.5125 kHz", S.unlocalized("5.5125 kHz"));
  static final AttributeOption RATE_8KHZ = new AttributeOption("8 kHz", S.unlocalized("8 kHz"));
  static final AttributeOption RATE_11KHZ = new AttributeOption("11.025 kHz", S.unlocalized("11.025 kHz"));
  static final AttributeOption RATE_12KHZ = new AttributeOption("12 kHz", S.unlocalized("12 kHz"));
  static final AttributeOption RATE_16KHZ = new AttributeOption("16 kHz", S.unlocalized("16 kHz"));
  static final AttributeOption RATE_22KHZ = new AttributeOption("22.050 kHz", S.unlocalized("22.050 kHz"));
  static final AttributeOption RATE_24KHZ = new AttributeOption("24 kHz", S.unlocalized("24 kHz"));
  static final AttributeOption RATE_32KHZ = new AttributeOption("32 kHz", S.unlocalized("32 kHz"));
  static final AttributeOption RATE_44_1KHZ = new AttributeOption("44.1 kHz", S.unlocalized("44.1 kHz"));
  static final AttributeOption RATE_44KHZ = new AttributeOption("44 kHz", S.unlocalized("44 kHz"));
  static final AttributeOption RATE_48KHZ = new AttributeOption("48 kHz", S.unlocalized("48 kHz"));
  // static final AttributeOption RATE_64KHZ = new AttributeOption("64 kHz", S.unlocalized("64 kHz"));
  static final Attribute<AttributeOption> ATTR_RATE = Attributes.forOption(
      "rate", S.getter("audioSampleRate"), new AttributeOption[] {
        /*RATE_5KHZ,*/ RATE_8KHZ, RATE_11KHZ, RATE_12KHZ, RATE_16KHZ,
            RATE_22KHZ, RATE_24KHZ, RATE_32KHZ, RATE_44_1KHZ,
            RATE_44KHZ, RATE_48KHZ /*, RATE_64KHZ */});

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
          StdAttr.TRIG_RISING, RATE_32KHZ, BitWidth.EIGHT, UNSIGNED, Integer.valueOf(512) });
    return super.createAttributeSet();
  }

  @Override
  public void propagate(InstanceState circState) {
    State data = getState(circState);
    if (data.err != null)
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

    Value v = circState.getPortValue(IN);
    if (!v.isFullyDefined())
      return;
    long sample = v.toIntValue();

    if (data.out == null) {
      // First try the user-chosen PCM format
      boolean ok = data.open();
      if (!ok)
        return;
    }

    int n = data.resampler.accept(sample);
    if (n < 0) {
      data.failed(null, "Error (write failed)");
    } else {
      try { data.out.start(); }
      catch (Throwable t) { data.failed(t, "Error (start failed)"); }
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    State data = (State) painter.getDataAsCustom();
    Bounds bds = painter.getNominalBounds();
    Graphics g = painter.getGraphics();
    
    String err = (data != null ? data.err : null);

    if (data != null && data.out != null && data.out.isOpen() && data.resampler != null) {
      int n = data.resampler.outLen + (data.out.getBufferSize() - data.out.available());
      int m = data.resampler.outBuf.length + data.out.getBufferSize();
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
    MidiDevice.paintSpeakerIcon(g, x, y,
        err != null,
        data != null && data.out != null
        && data.out.isOpen() && data.out.isActive() && data.out.isRunning()
        && data.out.available() < data.out.getBufferSize());

    if (err != null) {
      g.setColor(Color.RED);
      GraphicsUtil.drawText(g, err, bds.x+bds.width+3, bds.y, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
      g.setColor(Color.BLACK);
    }
  }

  private State getState(InstanceState state) {
    State ret = (State) state.getDataAsCustom();
    if (ret == null) {
      ret = new State(state);
      state.setData(ret);
    } else {
      ret.update(state);
    }
    return ret;
  }
  
  private static int rateOf(AttributeOption opt) {
    // if (opt == RATE_5KHZ)    return 5512.5;
    if (opt == RATE_8KHZ)    return 8000;
    if (opt == RATE_11KHZ)   return 11025;
    if (opt == RATE_12KHZ)   return 12000;
    if (opt == RATE_16KHZ)   return 16000;
    if (opt == RATE_22KHZ)   return 22050;
    if (opt == RATE_24KHZ)   return 24000;
    if (opt == RATE_32KHZ)   return 32000;
    if (opt == RATE_44_1KHZ) return 44100;
    if (opt == RATE_44KHZ)   return 44000;
    if (opt == RATE_48KHZ)   return 48000;
    // if (opt == RATE_64KHZ)   return 64000;
    return 32000;
  }

  private static int[] fallbackRates(AttributeOption opt) {
    // For all rates, we eventually try 8k, 16k, 44-48k, before giving up
    // if (opt == RATE_5KHZ)    return 5512.5;
    if (opt == RATE_8KHZ)    return new int[] { 16000, 24000, 32000, 48000 };              //  8000 x 2, 3, 4, 6
    if (opt == RATE_11KHZ)   return new int[] { 22050, 33075, 44100, 16000, 8000 };        // 11025 x 2, 3, 4
    if (opt == RATE_12KHZ)   return new int[] { 24000, 36000, 48000, 16000, 8000 };        // 12000 x 2, 3, 4
    if (opt == RATE_16KHZ)   return new int[] { 32000, 48000, 8000 };                      // 16000 x 2, 3, 1/2
    if (opt == RATE_22KHZ)   return new int[] { 44100, 11025, 16000, 8000 };               // 22050 x 2, 1/2
    if (opt == RATE_24KHZ)   return new int[] { 48000, 12000, 16000, 8000 };               // 24000 x 2, 1/2, 1/3
    if (opt == RATE_32KHZ)   return new int[] { 16000, 44000, 8000 };                      // 32000 x 1/2, 1/3
    if (opt == RATE_44_1KHZ) return new int[] { 44000, 22050, 11025, 16000, 8000 };        // 44100 x ~1, 1/2
    if (opt == RATE_44KHZ)   return new int[] { 44100, 22000, 22050, 11025, 16000, 8000 }; // 44000 x ~1, 1/2, ~1/2
    if (opt == RATE_48KHZ)   return new int[] { 24000, 16000, 12000, 8000 };               // 48000 x 1/2, 1/3, 1/4
    // if (opt == RATE_64KHZ)   return new int[] { ... }
    return 32000;
  }

  static class State implements ComponentData.WithLifetimeTracking {
    private Value lastClock = Value.UNKNOWN;
    
    private volatile String err;
    
    private int channels; // always 1 for now
  
    // Audio parameters as configured by user within simulator
    private int buflen; // total buffer size (number of samples)
    private AttributeOption sim_rateOption; // samples per second
    private int sim_rate; // samples per second
    private boolean sim_signed; // SIGNED or UNSIGNED
    private int sim_bitsPerSample; // sample depth, e.g. 24-bit samples
    
    // Audio parameters supported by underlying audio system
    private AudioFormat fmt;
    private int sys_rate; // samples per second
    private int sys_bitsPerSample; // sample depth, e.g. 24-bit samples
    private int sys_bytesPerSample; // sample depth, e.g. 3 bytes (rounded up)
    private AttributeOption sys_signed; // SIGNED, UNSIGNED, or FLOAT
    private SourceDataLine out; // opened using fmt
   
    // 
    private double stepInPerOut; // input samples per one output

    public State(InstanceState circState) {
      int b = circState.getAttributeValue(ATTR_BUFSIZE);
      int s = circState.getAttributeValue(StdAttr.WIDTH).getWidth();
      AttributeOption r = circState.getAttributeValue(ATTR_RATE);
      boolean g = circState.getAttributeValue(StdAttr.MODE) == SIGNED;
      init(b, s, r, g);
    }

    public State(State orig) {
      lastClock = orig.lastClock;
      init(orig.buflen, orig.sim_bitsPerSample, orig.sim_rateOption, orig.sim_signed);
    }

    void failed(Throwable t, String defaultErrmsg) {
      closeAudio();
      if (t != null)
        System.err.println(t.getMessage());
      if (t instanceof LineUnavailableException e)
        err = "Unavailable (line is busy)";
      else if (t intanceof IllegalStateException e)
        err = "Error (line already open)";
      else if (t intanceof SecurityException e)
        err = "Access Denied (security restriction)";
      else if (t intanceof IllegalArgumentException e)
        err = "Failed (PCM parameters not supported)";
      else if (defaultErrmsg != null)
        err = defaultErrmsg;
      else if (t != null)
        err = "Error (" + t.getMessage() + ")";
      else
        err = "Error (mystery)";
    }

    void init(int b, int s, AttributeOption r, boolean g) {
      out = null; 
      resampler = null;
      err = null;
      buflen = b;
      sim_bitsPerSample = s;
      sim_rateOption = r;
      sim_rate = rateOf(sim_rateOption);
      sim_signed = g;

      channels = 1;
      fmt = null;
    }

    void update(InstanceState circState) {
      int b = circState.getAttributeValue(ATTR_BUFSIZE);
      int s = circState.getAttributeValue(StdAttr.WIDTH).getWidth();
      AttributeOption r = circState.getAttributeValue(ATTR_RATE);
      boolean g = circState.getAttributeValue(StdAttr.MODE) == SIGNED;
      if (r == sim_rateOption && b == buflen && s == sim_bitsPerSample && g == sim_signed)
        return;
      closeAudio();
      init(b, s, r, g);
    }

    void makeFmt(AttributeOption signOpt, int rate, int bitsPerSample) throws Exception {
      sys_rate = rate;
      sys_bitsPerSample = bitsPerSample;
      sys_bytesPerSample = (sys_bitsPerSample + 7)/8;
      sys_signed = signOpt;
      AudioFormat.Encoding encoding = 
          signOpt == FLOAT ? AudioFormat.Encoding.PCM_FLOAT :
          signOpt == SIGNED ? AudioFormat.Encoding.PCM_SIGNED
          AudioFormat.Encoding.PCM_UNSIGNED;
      int frameSize = sys_bytesPerSample*channels;
      float sampleRate = sys_rate; // samples per second
      float frameRate = sys_rate; // uncompressed, so frameRate = sampleRate
      fmt = new AudioFormat(encoding, sampleRate,
          sys_bitsPerSample, channels,
          frameSize, frameRate, false /* bigEndian */);
    }

    boolean tryOpen(AttributeOption signOpt, int rate, int bitsPerSample) {
      try {

        makeFmt(signOpt, rate, bitsPerSample);

        out = AudioSystem.getSourceDataLine(fmt);

        int bytesPerFrame = fmt.getFrameSize();
        // int prebufLen = (buflen/4) * bytesPerFrame // out provides 75% of buffering
        // int prebufLen = bytesPerFrame; // minimal buffering outside the line's built-in buffer
        int prebufLen = 0; // This gets calculated after, by resampler. Oh well.
        out.open(fmt, buflen*bytesPerFrame - prebufLen);

        // yay, system seems to support this format
        resampler = new MonoResampler(out, sim_rate, sim_bitsPerSample, sim_signed);
        return true;

      } catch (Throwable t) {
        failed(t, "Error (unsupported PCM parameters)");
        return false;
      }
    }

    boolean open() {
      
      // First, try user-specified format
      if (tryOpen(sim_signed ? SIGNED : UNSIGNED, sim_rate, sim_bitsPerSample))
        return true;
      String originalErrMessage = err;

      // Try SIGNED in place of UNSIGNED
      if (!sim_signed && tryOpen(SIGNED, sim_rate, sim_bitsPerSample))
        return true;

      // Try widening to 8, 16, 24, or 32 bit, SIGNED
      for (int w : new int[] { 8, 16, 24, 32 }) {
        if (sim_bitsPerSample < w && tryOpen(SIGNED, sim_rate, w))
          return true;
      }

      // Try 32-bit, FLOAT
      if (tryOpen(FLOAT, sim_rate, 32))
        return true;

      // Getting desperate, try other sampling rates, 16-bit, SIGNED
      for (int alt_rate : fallbackRates(sim_rateOption)) {
        if (tryOpen(SIGNED, sim_rate, 16))
          return true;
      }

      // Last chance, try other sampling rates, 32-bit, FLOAT
      for (int alt_rate : fallbackRates(sim_rateOption)) {
        if (tryOpen(FLOAT, sim_rate, 32))
          return true;
      }

      err = originalErrMessage;
      return false;
    }

    void closeAudio() {
      if (out != null) {
        try { if (out.isOpen()) out.close(); }
        catch (Throwable t) { t.printStackTrace(); }
        out = null;
        resampler = null;
      }
      simFmt = null;
      sysFmt = null;
    }
    
    @Override
    public void simulationDeactivating(CircuitState cs, Component comp) {
      closeAudio();
    }

    @Override
    public void simulationCleanup(CircuitState cs, Component comp) {
      closeAudio();
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
