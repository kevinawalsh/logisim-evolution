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

package com.cburch.logisim.prefs;

import java.util.Locale;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.util.LocaleManager;

public class AppPreferences {

  public static final class ChangeEvent<E> {
    public final Object pref; // PrefMonitor<E> or similar object like TemplatePref
    public final E oldValue, newValue;
    public ChangeEvent(Object pref, E oldValue, E newValue) {
      this.pref = pref;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }
  }

  @FunctionalInterface
  public interface Listener<E> {
    public void prefChanged(ChangeEvent<E> event);
  }

  private static class LocalePreference extends PrefMonitor<String> {
    public LocalePreference() {
      super("international", "locale", "");
      if (value != null && !value.equals(""))
        LocaleManager.setLocale(new Locale(value));
      LocaleManager.addLocaleListener(() -> set(LocaleManager.getLocale().getLanguage()));
      set(LocaleManager.getLocale().getLanguage());
    }

    @Override
    public boolean setAndFire(String v) {
      if (findLocale(v) != null && super.setAndFire(v)) {
        if (value != null && !value.equals(""))
          LocaleManager.setLocale(new Locale(value));
        return true;
      }
      return false;
    }

    private static Locale findLocale(String lang) {
      Locale[] check;
      for (int set = 0; set < 2; set++) {
        if (set == 0)
          check = new Locale[] { Locale.getDefault(), Locale.ENGLISH };
        else
          check = Locale.getAvailableLocales();
        for (int i = 0; i < check.length; i++) {
          Locale loc = check[i];
          if (loc != null && loc.getLanguage().equals(lang))
            return loc;
        }
      }
      return null;
    }
  }

  private static class AccentsPreference extends PrefMonitor<Boolean> {
    public AccentsPreference() {
      super("international", "replaceAccents", false);
      LocaleManager.setReplaceAccents(value);
    }

    @Override
    public boolean setAndFire(Boolean v) {
      if (super.setAndFire(v)) {
        LocaleManager.setReplaceAccents(value);
        return true;
      }
      return false;
    }
  }

  /** Clears all user-set preferences; state (window size, zoom, etc.) is unaffected. */
  public static void clear() {
    SettingsStore.clear();
  }

  public static void handleGraphicsAcceleration() {
    String accel = GRAPHICS_ACCELERATION.get();
    try {
      if (accel == ACCEL_NONE) {
        System.setProperty("sun.java2d.metal", "false");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.d3d", "false");
      } else if (accel == ACCEL_METAL) {
        System.setProperty("sun.java2d.metal", "true");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.d3d", "false");
      } else if (accel == ACCEL_OPENGL) {
        System.setProperty("sun.java2d.metal", "false");
        System.setProperty("sun.java2d.opengl", "true");
        System.setProperty("sun.java2d.d3d", "false");
      } else if (accel == ACCEL_D3D) {
        System.setProperty("sun.java2d.metal", "false");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.d3d", "true");
      } else {
        // defaults, which can be overridden on command line, etc.
      }
    } catch (Exception t) {
      System.err.println("Note: Could not enable " + accel + " graphics acceleration.");
    }
  }

  // =========================================================================
  // Template preferences
  // =========================================================================

  public static final TemplatePref TEMPLATE = new TemplatePref();

  // =========================================================================
  // Display preferences (section: "display")
  // =========================================================================

  public static final String SHAPE_SHAPED = "shaped";
  public static final String SHAPE_RECTANGULAR = "rectangular";
  public static final String SHAPE_DIN40700 = "din40700";
  public static final PrefMonitor<String>
      GATE_SHAPE = new PrefMonitor<>("display", "gateShape",
          new String[] { SHAPE_SHAPED, SHAPE_RECTANGULAR, SHAPE_DIN40700 },
          SHAPE_SHAPED);

  public static final PrefMonitor<String>
      LOCALE = new LocalePreference();

  public static final PrefMonitor<Boolean>
      ACCENTS_REPLACE = new AccentsPreference();

  public static final PrefMonitor<Boolean>
      SHOW_TICK_RATE = new PrefMonitor<>("display", "showTickRate", false);

  public static final PrefMonitor<Boolean>
      SHOW_COORDS = new PrefMonitor<>("display", "showCoordinates", false);

  public static final String TOOLBAR_HIDDEN = "hidden";
  public static final String TOOLBAR_DOWN_MIDDLE = "downMiddle";
  public static final PrefMonitor<String>
      TOOLBAR_PLACEMENT = new PrefMonitor<>("display", "toolbarPlacement",
          new String[] {
            Direction.NORTH.toString(), Direction.SOUTH.toString(),
            Direction.EAST.toString(), Direction.WEST.toString(),
            TOOLBAR_DOWN_MIDDLE, TOOLBAR_HIDDEN },
          Direction.NORTH.toString());

  public static final PrefMonitor<Boolean>
      PRINTER_VIEW = new PrefMonitor<>("display", "printerView", false);

  public static final PrefMonitor<Boolean>
      ATTRIBUTE_HALO = new PrefMonitor<>("editing", "attributeHalo", true);

  public static final PrefMonitor<Boolean>
      COMPONENT_TIPS = new PrefMonitor<>("editing", "componentTips", true);

  public static final PrefMonitor<Boolean>
      MOVE_KEEP_CONNECT = new PrefMonitor<>("editing", "keepConnected", true);

  public static final PrefMonitor<Boolean>
      ADD_SHOW_GHOSTS = new PrefMonitor<>("editing", "showGhosts", true);

  public static final String ADD_AFTER_UNCHANGED = "unchanged";
  public static final String ADD_AFTER_EDIT = "edit";
  public static final PrefMonitor<String>
      ADD_AFTER = new PrefMonitor<>("editing", "afterAdd",
          new String[] { ADD_AFTER_EDIT, ADD_AFTER_UNCHANGED },
          ADD_AFTER_EDIT);

  public static final PrefMonitor<String> POKE_WIRE_RADIX1;
  public static final PrefMonitor<String> POKE_WIRE_RADIX2;
  static {
    RadixOption[] radixOptions = RadixOption.OPTIONS;
    String[] radixStrings = new String[radixOptions.length];
    for (int i = 0; i < radixOptions.length; i++)
      radixStrings[i] = radixOptions[i].getSaveString();
    POKE_WIRE_RADIX1 = new PrefMonitor<>("display", "pokeRadix1",
        radixStrings, RadixOption.RADIX_2.getSaveString());
    POKE_WIRE_RADIX2 = new PrefMonitor<>("display", "pokeRadix2",
        radixStrings, RadixOption.RADIX_10_SIGNED.getSaveString());
  }

  // =========================================================================
  // Backup preferences (section: "backups")
  // =========================================================================

  public static final PrefMonitor<Boolean>
      AUTO_BACKUP = new PrefMonitor<>("backups", "enabled", true);

  public static final PrefMonitor<Integer>
      AUTO_BACKUP_FREQ = new PrefMonitor<>("backups", "frequency", 7);

  // =========================================================================
  // Questa preferences (section: "questa")
  // =========================================================================

  public static final PrefMonitor<String>
      QUESTA_PATH = new PrefMonitor<>("hdl-validation", "questaPath", "");

  public static final PrefMonitor<Boolean>
      QUESTA_VALIDATION = new PrefMonitor<>("hdl-validation", "questaValidation", false);

  // =========================================================================
  // Graphics preferences (section: "graphics")
  // =========================================================================

  public static final String ACCEL_DEFAULT = "default";
  public static final String ACCEL_NONE    = "none";
  public static final String ACCEL_METAL   = "metal";
  public static final String ACCEL_OPENGL  = "opengl";
  public static final String ACCEL_D3D     = "d3d";
  public static final PrefMonitor<String> GRAPHICS_ACCELERATION =
      new PrefMonitor<>("graphics", "acceleration",
          new String[] { ACCEL_DEFAULT, ACCEL_NONE, ACCEL_METAL, ACCEL_OPENGL, ACCEL_D3D },
          ACCEL_DEFAULT);

  public static final String DUALSCREEN_NONE = "none";
  public static final String DUALSCREEN_FIX  = "fixBlackWindows";
  public static final String DUALSCREEN_MORE = "fixBlackWindowsMore";
  public static final String DUALSCREEN_MOST = "fixBlackWindowsBest";
  public static final PrefMonitor<String> DUALSCREEN =
      new PrefMonitor<>("graphics", "dualScreenFixes",
          new String[] { DUALSCREEN_NONE, DUALSCREEN_FIX, DUALSCREEN_MORE, DUALSCREEN_MOST },
          DUALSCREEN_NONE);

  // =========================================================================
  // Tips and Hints preferences (section: "hints")
  // =========================================================================

  public static final PrefMonitor<Integer>
      WIRING_TOOL_HINTS = new PrefMonitor<>("hints", "wiring", 3);

  public static final PrefMonitor<Integer>
      CUTTER_TOOL_HINTS = new PrefMonitor<>("hints", "cutter", 3);

  // =========================================================================
  // FPGA stuff
  // =========================================================================
  
  public static final PrefMonitor<String>
    FPGA_WORKSPACE_PATH = new PrefMonitor<>("fpga", "workspace", "");

  public static final FPGABoardlistPref
    FPGA_BOARDLIST = new FPGABoardlistPref("fpga", "externalBoards");

  public static final PrefMonitor<String>
    APIO_PATH = new PrefMonitor<>("apio", "path", "");

  public static final PrefMonitor<String>
    OPENFPGALOADER_PATH = new PrefMonitor<>("openFPGALoader", "path", "");

  public static final PrefMonitor<String>
    XILINX_PATH = new PrefMonitor<>("xilinx", "path", "");

  public static final PrefMonitor<String>
    LATTICE_PATH = new PrefMonitor<>("lattice", "path", "");

  public static final PrefMonitor<String>
    GOWIN_SHELL_PATH = new PrefMonitor<>("gowin", "shell", "");
  public static final PrefMonitor<String>
    GOWIN_PROGRAMMER_PATH = new PrefMonitor<>("gowin", "programmer", "");

  public static final PrefMonitor<String>
    ALTERA_PATH = new PrefMonitor<>("altera", "path", "");
  public static final PrefMonitor<Boolean>
    ALTERA_64BIT = new PrefMonitor<>("altera", "64bit", Boolean.TRUE);
  public static final PrefMonitor<String>
    ALTERA_FORMAT = new PrefMonitor<>("altera", "format",
          new String[] { "svf", "rbf"},
          "svf");
}
