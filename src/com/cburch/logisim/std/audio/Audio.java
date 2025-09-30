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

import java.util.List;
import java.util.ArrayList;

import com.cburch.logisim.tools.FactoryDescription;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class Audio extends Library {

  private static FactoryDescription[] DESCRIPTIONS = {
    new FactoryDescription("Octave", S.getter("audioOctaveComponent"), "octave.gif", "Octave"),
    new FactoryDescription("PCMSink", S.getter("audioPCMSinkComponent"), "midisink.gif", "PCMSink"),
    new FactoryDescription("MidiSink", S.getter("audioMidiSinkComponent"), "midisink.gif", "MidiSink"),
    new FactoryDescription("MidiIn", S.getter("audioMidiInComponent"), "midiin.gif", "MidiIn"),
    new FactoryDescription("SaturatingAdder", S.getter("audioSaturatingAdderComponent"), "saturatingadder.png", "SaturatingAdder"),
    new FactoryDescription("SaturatingSubtractor", S.getter("audioSaturatingSubtractorComponent"), "saturatingsubtractor.png", "SaturatingSubtractor"),
    new FactoryDescription("SaturatingNegator", S.getter("audioSaturatingNegatorComponent"), "saturatingnegator.png", "SaturatingNegator"),
    new FactoryDescription("SaturatingMultiplier", S.getter("audioSaturatingMultiplierComponent"), "saturatingmultiplier.png", "SaturatingMultiplier"),
    new FactoryDescription("SaturatingDivider", S.getter("audioSaturatingDividerComponent"), "saturatingdivider.png", "SaturatingDivider"),
    new FactoryDescription("FitRange", S.getter("audioFitRangeComponent"), "fitrange.png", "FitRange"),
    new FactoryDescription("LinearMap", S.getter("audioLinearMapComponent"), "fitrange.png", "LinearMap"),
    new FactoryDescription("ScaledMath", S.getter("audioScaledMathComponent"), "scaledmath.png", "ScaledMath"),
    new FactoryDescription("LineSelect", S.getter("audioLineSelectComponent"), "lineselect.png", "LineSelect"),
  };

  private List<Tool> tools = null;

  public Audio() { }

  @Override
  public String getDisplayName() {
    return S.get("audioLibrary");
  }

  @Override
  public String getName() {
    return "Audio";
  }

  @Override
  public List<Tool> getTools() {
    if (tools == null) {
      tools = new ArrayList<>();
      tools.addAll(FactoryDescription.getTools(Audio.class, DESCRIPTIONS));
    }
    return tools;
  }

}
