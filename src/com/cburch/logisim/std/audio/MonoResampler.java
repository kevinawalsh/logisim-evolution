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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;

// MonoResamplePipe handles conversion from the simulator's requested PCM output
// format to the underlying system's supported PCM format. It can handle:
// - mono audio (1 channel)
// - signedness conversions,
// - widening or narrowing of sample depths,
// - rate changes using a linear SRC with single phase tNext,
// - and it can output either little endian or big endian.
class MonoResampler {

  private final SourceDataLine line;
  private final double inRate;
  private final int inBits;
  private final boolean inSign;
  
  private final double outRate;
  private final double stepInPerOut; // input samples per one output
  private final int outBits, outBytes, outFrameSize;
  private final boolean outLittleEndian;
  private final AudioFormat.Encoding outEncoding; // only SIGNED, UNSIGNED, FLOAT

  // state for streaming linear resampler (positions measured in input-sample units)
  private long inIndex = -1; // index of most recent input sample
  private double tNext = 0.0; // position of next output sample (in input units)
  private double prev = 0.0; // previous input sample (-1..+1)
  private boolean havePrev = false;

  // staging buffer, before data hits the line
  /*private*/ final byte[] outBuf; // length shown in paint
  /*private*/ int outLen = 0; // shown in paint

  public MonoResampler(SourceDataLine output_line, double sim_rate, int sim_bitsPerSample, boolean sim_signed) {
    line = output_line;
    inRate = sim_rate;
    inBits = sim_bitsPerSample;
    inSign = sim_signed;

    AudioFormat fmt = line.getFormat();
    outRate = fmt.getSampleRate();
    stepInPerOut = sim_rate / outRate; // delta_t (in input samples) between successive outputs
    outBits = fmt.getSampleSizeInBits();
    outBytes = Math.max(1, (outBits + 7) / 8);
    outFrameSize = fmt.getFrameSize();
    outEncoding = fmt.getEncoding();
    outLittleEndian = !fmt.isBigEndian();

    // allocate staging buffer with reasonable size
    int frameSize = outBytes * fmt.getChannels(); // mono, so channels=1
    double targetMs = 15.0; // 10–20 ms was a suggested sweet spot
                            // Alternatively, we could could go as low as 1 frameSize
    int frames = Math.max(64, (int)Math.ceil(outRate * targetMs/1000.0));
    int outBufBytes = frames * frameSize;
    outBuf = new byte[outBufBytes];
  }

  // Process one sample from simulated circuit.
  // Returns negative on error, positive if bytes were written to line.
  public int accept(long sample) {
    double x = toUnit(sample, inBits, inSign); // normalize to [-1,1)
    inIndex++;
    if (!havePrev) {
      prev = x;
      havePrev = true;
      return 0; // no data written yet
    }

    // Emit all output samples whose timestamp tNext doesn't exceed the current
    // input index. We are now between data s[inIndex-1]..s[inIndex].
    int written = 0;
    while (tNext <= inIndex) {
      // linear interpolation between prev = s[inIndex-1] and x = s[inIndex]
      double frac = tNext - (inIndex - 1); // in [0,1]
      // snap near 0/1 to preserve identity when rates match
      if (frac <= 1e-12) frac = 0.0;
      else if (frac >= 1.0 - 1e-12) frac = 1.0;
      double y = prev + (x - prev) * frac;
      int n = emit(y);
      if (n < 0)
        return -1;
      written += n;
      tNext += stepInPerOut; // next output time
    }
    prev = x;
    return written;
  }

  // Returns negative on error, positive if bytes were written to line.
  private int flush() {
    int n = outLen - (outLen % outFrameSize);
    if (n <= 0)
      return 0; // not enough data to write yet
    try { 
      int written = line.write(outBuf, 0, n);
      if (written <= 0) {
        outLen = 0;
        return -1; // write failed, maybe line is closed
      }
      int rem = outLen - written;
      if (rem > 0)
        System.arraycopy(outBuf, n, outBuf, 0, rem);
      outLen = rem;
      return written;
    } catch (Throwable t) {
      System.err.println(t.getMessage());
      outLen = 0;
      return -1; // write failed, unknown reason
    }
  }

  // normalize to [-1,1) as double (exact for power-of-two scaling)
  private static double toUnit(long v, int bits, boolean signed) {
    long mask = (1L << bits) - 1;
    v &= mask;
    if (!signed) {
      double mid = 1L << (bits - 1);
      return (v - mid) / mid; // [-1,1)
    } else {
      long sbit = 1L << (bits - 1);
      v = ((v ^ sbit) - sbit);      // sign-extend
      double max = 1L << (bits - 1);
      return v / max;
    }
  }

  private int emit(double y) {
    if (outEncoding == AudioFormat.Encoding.PCM_FLOAT)
      writeFloat32((float)y);
    else if (outEncoding == AudioFormat.Encoding.PCM_SIGNED)
      writeInt(y, true);
    else if (outEncoding == AudioFormat.Encoding.PCM_UNSIGNED)
      writeInt(y, false);

    if (outLen >= outBuf.length - outBytes)
      return flush();
    else
      return 0; // not enough data yet to justify writing to line
  }

  private void writeFloat32(float y) {
    if (y >  1.0f) y =  1.0f;
    if (y < -1.0f) y = -1.0f;
    int bits = Float.floatToIntBits(y);
    if (outLittleEndian) {
      outBuf[outLen++] = (byte) bits;
      outBuf[outLen++] = (byte) (bits >>> 8);
      outBuf[outLen++] = (byte) (bits >>> 16);
      outBuf[outLen++] = (byte) (bits >>> 24);
    } else {
      outBuf[outLen++] = (byte) (bits >>> 24);
      outBuf[outLen++] = (byte) (bits >>> 16);
      outBuf[outLen++] = (byte) (bits >>> 8);
      outBuf[outLen++] = (byte) bits;
    }
  }

  private void writeInt(double y, boolean signed) {
    int v;
    if (signed) {
      long maxPos = (outBits == 32) ? 0x7FFFFFFFL : ((1L << (outBits - 1)) - 1);
      long minNeg = - (1L << (outBits - 1));
      long r = Math.round(y * (double)maxPos); // unbiased rounding
      if (r < minNeg) r = minNeg;
      else if (r > maxPos) r = maxPos;
      v = (int) r;
      if (outBits < 32)
        v &= (1 << outBits) - 1;
    } else {
      long maxU = (outBits == 32) ? 0xFFFFFFFFL : ((1L << outBits) - 1);
      long r = Math.round((y * 0.5 + 0.5) * (double)maxU);
      if (r < 0) r = 0;
      else if (r > maxU) r = maxU;
      v = (int) r;
    }
    if (outLittleEndian)
      for (int i = 0; i < outBytes; i++)
        outBuf[outLen++] = (byte)(v >>> (8*i));
    else
      for (int i = outBytes - 1; i >= 0; i--)
        outBuf[outLen++] = (byte)(v >>> (8*i));
  }

}
