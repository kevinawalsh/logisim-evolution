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

package com.cburch.logisim.gui.menu;
import static com.cburch.logisim.gui.menu.Strings.S;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.prefs.SettingsFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.DesktopIntegration;

class MenuFile extends Menu implements ActionListener {
  private static final long serialVersionUID = 1L;

  // Hardcoded list of example circuits. Format for each entry is a
  // colon-separated triplet:
  //   "Category > Subcategory > Item Name : path/to/file.circ : Circuit Name"
  // The path before the first ":" uses " > " to define nesting. The last
  // segment is the menu item label. If there are no ">" the item goes directly
  // in the top-level Examples menu. Circuit Name (third part) is optional.
  // Example:
  //   "Audio > PCM Demos > PCM Wave : audio/pcm-demo.circ : wave gen"
  private static final String[] EXAMPLES = {

    "audio > demos > wave generators             : audio-demo.circ : demo-wave-generators",
    "audio > demos > wacky techno-synth          : audio-demo.circ : demo-wacky-technosynth",
    "audio > demos > chaos synth                 : audio-demo.circ : demo-chaos-synth",
    "audio > demos > signal mixing               : audio-demo.circ : demo-mixing",
    "audio > demos > digital resonator           : audio-demo.circ : demo-digital-resonator",
    "audio > demos > low-pass filtering          : audio-demo.circ : demo-lowpass-filter",
    "audio > demos > high-pass filtering         : audio-demo.circ : demo-highpass-filter",
    "audio > demos > bandpass filtered noise     : audio-demo.circ : demo-seascape-bandpass-filtered-noise",
    "audio > demos > adjustable filtering        : audio-demo.circ : demo-adjustable-filter-ord1",
    "audio > demos > FIR filtering               : audio-demo.circ : demo-FIR-filter-ord1",
    "audio > demos > pre-recorded audio playback : audio-demo.circ : demo-recorded-audio",
    "audio > demos > pre-recorded audio + SVF    : audio-demo.circ : demo-recorded-audio-svf",
    "audio > demos > tone + reverb               : audio-demo.circ : demo-reverb-tone",
    "audio > demos > voice + reverb              : audio-demo.circ : demo-reverb-voice",
    "audio > arduino usb > accelerometer +tones  : audio-demo.circ : demo-usb-arduino",
    "audio > arduino usb > boops!                : audio-demo.circ : demo-arduino-trigger-boops",
    "audio > arduino usb > multi-trigger         : audio-demo.circ : demo-arduino-multitrigger",
    "audio > arduino usb > noisy drumkit         : audio-demo.circ : demo-arduino-noisy-drumkit",
    "audio > birdbrain finch > multi-trigger     : audio-demo.circ : demo-finch-multitrigger",

    "audio > waves > sawtooth (80 hz)            : audio-demo.circ : sawtooth-80hz",
    "audio > waves > sawtooth (80 hz, alt)       : audio-demo.circ : sawtooth-80hz-alternate",
    "audio > waves > sawtooth (variable)         : audio-demo.circ : sawtooth",
    "audio > waves > triangle (120 hz)           : audio-demo.circ : triangle-120hz",
    "audio > waves > triangle (62.5 hz)          : audio-demo.circ : triangle-62.5hz",
    "audio > waves > triangle (variable)         : audio-demo.circ : triangle",
    "audio > waves > square                      : audio-demo.circ : squarewave",
    "audio > waves > sin                         : audio-demo.circ : sinwave",
    "audio > waves > white noise                 : audio-demo.circ : white-noise",
    "audio > ramps & filters > sweep             : audio-demo.circ : sweep-0-to-65535",
    "audio > ramps & filters > sweep signed      : audio-demo.circ : sweep-(-32768)-to-(+32767)",
    "audio > ramps & filters > sweep slow        : audio-demo.circ : sweep-(-32768)-to-(+32767)-slowly",
    "audio > ramps & filters > low-pass          : audio-demo.circ : lowpass-filter-onepole",
    "audio > ramps & filters > high-pass         : audio-demo.circ : highpass-filter-onepole",
    "audio > ramps & filters > high-pass (alt)   : audio-demo.circ : highpass-filter-onepole-alternate",
    "audio > ramps & filters > SVF               : audio-demo.circ : SVF-filter",
    "audio > ramps & filters > click track       : audio-demo.circ : click-track",
    "audio > ramps & filters > pulse generator   : audio-demo.circ : pulse-generator",

    "audio > diy & practice > wave generators    : audio-demo.circ : diy-wave-generator",
    "audio > diy & practice > more waves         : audio-demo.circ : diy-waves",
    "audio > diy & practice > stored waveforms   : audio-demo.circ : diy-create-a-wave",
    "audio > diy & practice > envelopes          : audio-demo.circ : diy-envelope",
    "audio > diy & practice > delayline & reverb : audio-demo.circ : diy-delayline-reverb",

    "audio > midi > midi demo                    : audio-midi.circ : main",
    "audio > midi > midi output                  : audio-midi.circ : midi-out",
    "audio > midi > midi input                   : audio-midi.circ : midi-in",

    "MIPS > full 32-bit MIPS computer            : mips-test.circ : main",
    "MIPS > 32-bit ALU                           : mips-test.circ : ALU",
    "MIPS > 32x32 register file                  : mips-test.circ : regfile",

    "logisim features > multi-bit gates          : gates.circ : Multi-bit Gates",
    "logisim features > splitters                : wiring.circ : splitter-examples",
    "logisim features > tunnels                  : wiring.circ : tunnel-examples",
    "logisim features > clocks                   : wiring.circ : clock-examples",
    "logisim features > constants                : wiring.circ : constant-examples",
    "logisim features > dynamic conditions       : DynamicConditions.circ : main",
    "logisim features > in-circuit slideshow     : FilesAndImages.circ : main",
    "logisim features > in-circuit file viewer   : FilesAndImages.circ : main",
    "logisim features > joystick + vga video     : vga-joystick.circ : main",
    "logisim features > slider + dot matrix      : invaders.circ : main",
    "logisim features > inputs and poke tool     : interaction.circ : main",
    "logisim features > real time clock          : digital-clock.circ : main",
    "logisim features > ledbars + counters + rng : io-examples.circ : ledbar-example",

    "miscellaneous > fizz-buzz                   : wiring.circ : fizz-buzz",
    "miscellaneous > ideal relay                 : IdealRelay.circ : main",
    "miscellaneous > invaders game               : invaders.circ : main",
    "miscellaneous > bouncing ball               : bounce.circ : main",
    "miscellaneous > digital clock demo          : digital-clock.circ : main",

  };

  // Build the Examples JMenu from EXAMPLES. Submenus are created on demand,
  // ordered by first appearance.
  private static JMenu buildExamples() {
    JMenu top = new JMenu("Examples");
    LinkedHashMap<String, JMenu> submenus = new LinkedHashMap<>();
    for (String entry : EXAMPLES) {
      String[] parts = entry.split(":", 3);
      if (parts.length < 2) continue;
      String path = parts[0].trim();
      String filename = parts[1].trim();
      String circuitName = parts.length >= 3 ? parts[2].trim() : null;
      if (circuitName != null && circuitName.isEmpty()) circuitName = null;
      if (filename.isEmpty()) continue;

      String[] segments = path.split(">");
      for (int i = 0; i < segments.length; i++)
        segments[i] = segments[i].trim();

      // All but the last segment are submenu names; last is the item label.
      JMenu parent = top;
      StringBuilder keyBuf = new StringBuilder();
      for (int i = 0; i < segments.length - 1; i++) {
        if (keyBuf.length() > 0) keyBuf.append('>');
        keyBuf.append(segments[i]);
        String key = keyBuf.toString();
        JMenu sub = submenus.get(key);
        if (sub == null) {
          sub = new JMenu(segments[i]);
          parent.add(sub);
          submenus.put(key, sub);
        }
        parent = sub;
      }

      final String fname = filename;
      final String cname = circuitName;
      JMenuItem item = new JMenuItem(segments[segments.length - 1]);
      item.addActionListener(e -> HelpBroker.openLive(fname, cname));
      parent.add(item);
    }
    return top;
  }

  private LogisimMenuBar menubar;
  private JMenu examples = buildExamples();
  private JMenuItem newi = new JMenuItem();
  private JMenuItem open = new JMenuItem();
  private OpenRecent openRecent;
  private JMenuItem close = new JMenuItem();
  private JMenuItem save = new JMenuItem();
  private JMenuItem saveAs = new JMenuItem();
  private MenuItemImpl print = new MenuItemImpl(this, LogisimMenuBar.PRINT);
  private MenuItemImpl exportImage = new MenuItemImpl(this,
      LogisimMenuBar.EXPORT_IMAGE);
  private JMenuItem prefs = new JMenuItem();
  private JMenuItem quit = new JMenuItem();

  public MenuFile(LogisimMenuBar menubar) {
    this.menubar = menubar;
    openRecent = new OpenRecent(menubar);

    int menuMask = getToolkit().getMenuShortcutKeyMaskEx();

    newi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask));
    open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
    close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask
          | InputEvent.SHIFT_DOWN_MASK));
    save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
    saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask
          | InputEvent.SHIFT_DOWN_MASK));
    print.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask));
    prefs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask));
    quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuMask));

    add(newi);
    add(open);
    add(openRecent);
    add(examples);
    addSeparator();
    add(close);
    add(save);
    add(saveAs);
    addSeparator();
    add(exportImage);
    add(print);
    if (!DesktopIntegration.PreferencesMenuAutomaticallyPresent) {
      addSeparator();
      add(prefs);
    }
    if (!DesktopIntegration.QuitMenuAutomaticallyPresent) {
      addSeparator();
      add(quit);
    }

    Project proj = menubar.getSaveProject();
    newi.addActionListener(this);
    open.addActionListener(this);
    if (proj == null) {
      close.setEnabled(false);
      save.setEnabled(false);
      saveAs.setEnabled(false);
    } else {
      close.addActionListener(this);
      save.addActionListener(this);
      saveAs.addActionListener(this);
    }
    menubar.registerItem(LogisimMenuBar.EXPORT_IMAGE, exportImage);
    menubar.registerItem(LogisimMenuBar.PRINT, print);
    prefs.addActionListener(this);
    quit.addActionListener(this);
  }

  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    Project proj = menubar.getSaveProject();
    Project baseProj = menubar.getBaseProject();
    Frame frame  = baseProj == null ? null : baseProj.getFrame();
    if (src == newi) {
      ProjectActions.doNew(frame);
    } else if (src == open) {
      Project newProj = ProjectActions.doOpen(frame, baseProj);
      // If the current project hasn't been touched and has no file associated
      // with it (i.e. is entirely blank), and the new file was opened
      // successfully, then go ahead and close the old blank window.
      // todo: and has no subwindows or dialogs open?
      if (newProj != null && proj != null
          && !proj.isFileDirty()
          && proj.getLogisimFile().getLoader().getMainFile() == null) {
        proj.getFrame().dispose();
      }
    } else if (src == close && proj != null) {
      int result = 0;
      if (proj.isFileDirty()) {
        /* Must use hardcoded strings here, because the string management is rotten */
        String message = "What should happen to your unsaved changes to " + proj.getLogisimFile().getName();
        String[] options = { "Save", "Discard", "Cancel" };
        result = JOptionPane.showOptionDialog(JOptionPane.getFrameForComponent(this), message, "Confirm Close", 0,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (result == 0) {
          ProjectActions.doSave(proj);
        }
      }

      /* If "cancel" pressed do nothing, otherwise dispose the window, opening one if this was the last opened window */
      if (result != 2) {
        // Get the list of open projects
        List<Project> pl = Projects.getOpenProjects();
        if (pl.size() <= 1 && !DesktopIntegration.HasWindowlessMenubar) {
          // Since we have a single window open, before closing the current
          // project open a new empty one, to avoid having no remaining windows.
          // This isn't needed if (like on MacOS) there is a menubar even when
          // there are no windows.
          ProjectActions.doNew(frame);
        }

        // Close the current project
        frame.dispose();
      }
    } else if (src == save && proj != null) {
      ProjectActions.doSave(proj);
    } else if (src == saveAs && proj != null) {
      ProjectActions.doSaveAs(proj);
    } else if (src == prefs) {
      SettingsFrame.showAppSettings();
    } else if (src == quit) {
      ProjectActions.doQuit();
    }
  }

  void setSaveHandler(Runnable saveHandler, Runnable saveAsHandler) {
    if (saveHandler != null) {
      save.setEnabled(true);
      save.addActionListener(e -> saveHandler.run());
    }
    if (saveAsHandler != null) {
      saveAs.setEnabled(true);
      saveAs.addActionListener(e -> saveAsHandler.run());
    }
  }

  @Override
  void computeEnabled() {
    setEnabled(true);
    menubar.fireEnableChanged();
  }

  public void localeChanged() {
    this.setText(S.get("fileMenu"));
    newi.setText(S.get("fileNewItem"));
    open.setText(S.get("fileOpenItem"));
    openRecent.localeChanged();
    examples.setText(S.get("fileExamples"));
    close.setText(S.get("fileCloseItem"));
    save.setText(S.get("fileSaveItem"));
    saveAs.setText(S.get("fileSaveAsItem"));
    exportImage.setText(S.get("fileExportImageItem"));
    print.setText(S.get("filePrintItem"));
    prefs.setText(S.get("filePreferencesItem"));
    quit.setText(S.get("fileQuitItem"));
  }
}
