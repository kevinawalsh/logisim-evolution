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

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.util.EventScheduler;
import com.cburch.logisim.util.QNode;

public class MidiDevice {

  private AttributeOption instlist[];
  public final Attribute<AttributeOption> ATTR_INSTR;
  public final Attribute<Integer> ATTR_CHAN;

  private Object midiLock = new Object();
  private Synthesizer midiSynth = null;
  private Releaser midiReleaser;
  private Instrument[] midiInstrument;
  private MidiChannel[] midiChannel;
  public final int numChannels;
  private int[] midiChannelInstrument;


  public static MidiDevice open() {
    try { return new MidiDevice(); }
    catch (Exception e) { return null; }
  }

  private MidiDevice() throws MidiUnavailableException {
    try {
      midiSynth = MidiSystem.getSynthesizer(); 
      midiSynth.open();

      midiChannel = midiSynth.getChannels();
      if (midiChannel == null || midiChannel.length == 0)
        throw new MidiUnavailableException("No midi channels available");
      numChannels = midiChannel.length;
      ATTR_CHAN = Attributes.forIntegerRange("channel", S.getter("midiChannel"), 0, numChannels - 1);

      midiInstrument = midiSynth.getDefaultSoundbank().getInstruments();
      if (midiInstrument == null || midiInstrument.length == 0)
        throw new MidiUnavailableException("No midi instruments available");

      instlist = new AttributeOption[midiInstrument.length];
      for (int i = 0 ; i < midiInstrument.length ; i++) {
        String name = i + ": " + midiInstrument[i].getName();
        instlist[i] = new AttributeOption(name, name, S.unlocalized(name));
      }
      ATTR_INSTR = Attributes.forOption("instrument", S.getter("midiInstrument"), instlist);

      midiChannelInstrument = new int[numChannels];
      for (int i = 0; i < numChannels; i++)
        midiChannelInstrument[i] = -1;

      midiChannel[0].allNotesOff();

      midiReleaser = new Releaser();
      midiReleaser.start();
    } catch (MidiUnavailableException e) {
      if (midiSynth != null)
        midiSynth.close();
      throw e;
    }
  }

  public AttributeOption defaultInstrument() { return instlist[0]; }
  public int numChannels() { return numChannels; }

  public void setProgram(int chan, int inst, int volume) {
    synchronized (midiLock) {
      if (midiChannelInstrument[chan] != inst) {
        // switch instruments
        if (inst < 0) inst = 0;
        else if (inst >= midiInstrument.length) inst = midiInstrument.length - 1;
        midiSynth.loadInstrument(midiInstrument[inst]);
        midiChannel[chan].programChange(midiInstrument[inst].getPatch().getProgram());
        midiChannel[chan].controlChange(7, volume);
        midiChannelInstrument[chan] = inst;
      }
    }
  }

  // notes 0 and 128 affect instrument damper, but do not play notes
  // note 1 to 127 cause NoteOn(note)
  // note -1 to -127 cause NoteOff(-note) [ negatives using 8 bit twos complement ]
  public void play(int note, int velocity, int damper, int chan, int hold) { 

    synchronized (midiLock) {
      if (damper == 1)
        midiChannel[chan].allNotesOff();

      if (0 < note && note < 128)
        midiChannel[chan].noteOn(note, velocity);
      else if (note >= 128)
        midiChannel[chan].noteOff(256 - note, velocity);
    }

    if (0 < note && note < 128 && hold > 0)
      midiReleaser.schedule(new Release(hold, chan, note, velocity));
  }

  public void resetAllChannels() {
    for (int i = 0; i < numChannels; i++) {
      midiChannel[i].allNotesOff();
      setProgram(i, 0, 127);
    }
  }

  public void resetChannel(int chan) {
    midiChannel[chan].allNotesOff();
    setProgram(chan, 0, 127);
  }

  public void silenceChannel(int chan) {
    midiChannel[chan].allNotesOff();
  }

  static class Release extends QNode {
    int chan, note, velo;
    Release(long hold, int chan, int note, int velo) {
      super(System.currentTimeMillis() + hold);
      this.chan = chan;
      this.note = note;
      this.velo = velo;
    }
  }

  class Releaser extends EventScheduler<Release> {

    Releaser() { super("MidiNoteReleaser"); }

    public void fire(Release r) {
      play(256 - r.note, r.velo, 0, r.chan, 0);
    }

  }

  public static void paintSpeakerIcon(Graphics g, int x, int y, boolean error, boolean active) {
    x -= 12;
    y -= 12;
    g.setColor(Color.BLACK);
    g.drawRect(x+1, y+7, 6, 10);
    int[] bx = new int[] { x+7, x+13, x+14, x+14, x+13, x+7 };
    int[] by = new int[] { y+7, y+1, y+1, y+23, y+23, y+17 };
    g.setColor(error ? Color.RED : active ? Color.BLUE : Color.GRAY);
    g.drawPolyline(bx, by, 6);
    g.drawArc(x+14, y+1, 9, 22, -60, 120);
    g.drawArc(x+10, y+5, 9, 12, -60, 120);
  }

}

