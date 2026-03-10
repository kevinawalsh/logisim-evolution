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

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

import com.cburch.logisim.Main;
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
      super("locale", "");
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
      super("accentsReplace", false);
      LocaleManager.setReplaceAccents(value);
      // No need for listener here, no other code changes the value in LocaleManager
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

  public static void clear() {
    Preferences p = getPrefs(true);
    try { p.clear(); }
    catch (BackingStoreException e) { }
  }

  public static Preferences getPrefs() {
    return getPrefs(false);
  }

  private static Preferences getPrefs(boolean shouldClear) {
    if (prefs == null) {
      synchronized (AppPreferences.class) {
        if (prefs == null) {
          Preferences p = Preferences.userNodeForPackage(Main.class);
          if (shouldClear) {
            try { p.clear(); }
            catch (BackingStoreException e) { }
          }
          prefs = p;
        }
      }
    }
    return prefs;
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

  // class variables for maintaining consistency between properties,
  // internal variables, and other classes
  private static Preferences prefs = null;

  // Template preferences
  public static final TemplatePref TEMPLATE = new TemplatePref();
  // International preferences
  public static final String SHAPE_SHAPED = "shaped";
  public static final String SHAPE_RECTANGULAR = "rectangular";
  public static final String SHAPE_DIN40700 = "din40700";
  public static final PrefMonitor<String>
      GATE_SHAPE = new PrefMonitor("gateShape",
          new String[] { SHAPE_SHAPED, SHAPE_RECTANGULAR, SHAPE_DIN40700 },
          SHAPE_SHAPED);
  public static final PrefMonitor<String>
      LOCALE = new LocalePreference();
  public static final PrefMonitor<Boolean>
      ACCENTS_REPLACE = new AccentsPreference();
  // Window preferences
  public static final PrefMonitor<Boolean>
      SHOW_TICK_RATE = new PrefMonitor("showTickRate", false);
  public static final PrefMonitor<Boolean>
      SHOW_COORDS = new PrefMonitor("showCoordinates", false);
  public static final String TOOLBAR_HIDDEN = "hidden";
  public static final String TOOLBAR_DOWN_MIDDLE = "downMiddle";
  public static final PrefMonitor<String>
      TOOLBAR_PLACEMENT = new PrefMonitor("toolbarPlacement",
          new String[] {
            Direction.NORTH.toString(), Direction.SOUTH.toString(),
            Direction.EAST.toString(), Direction.WEST.toString(),
            TOOLBAR_DOWN_MIDDLE, TOOLBAR_HIDDEN },
          Direction.NORTH.toString());
  // Layout preferences
  public static final PrefMonitor<Boolean>
      PRINTER_VIEW = new PrefMonitor("printerView", false);
  public static final PrefMonitor<Boolean>
      ATTRIBUTE_HALO = new PrefMonitor("attributeHalo", true);
  public static final PrefMonitor<Boolean>
      COMPONENT_TIPS = new PrefMonitor("componentTips", true);
  public static final PrefMonitor<Boolean>
      MOVE_KEEP_CONNECT = new PrefMonitor("keepConnected", true);
  public static final PrefMonitor<Boolean>
      ADD_SHOW_GHOSTS = new PrefMonitor("showGhosts", true);
  public static final String ADD_AFTER_UNCHANGED = "unchanged";
  public static final String ADD_AFTER_EDIT = "edit";
  public static final PrefMonitor<String>
      ADD_AFTER = new PrefMonitor("afterAdd",
          new String[] { ADD_AFTER_EDIT, ADD_AFTER_UNCHANGED },
          ADD_AFTER_EDIT);

  public static final PrefMonitor<String> POKE_WIRE_RADIX1;
  public static final PrefMonitor<String> POKE_WIRE_RADIX2;
  static {
    RadixOption[] radixOptions = RadixOption.OPTIONS;
    String[] radixStrings = new String[radixOptions.length];
    for (int i = 0; i < radixOptions.length; i++) {
      radixStrings[i] = radixOptions[i].getSaveString();
    }
    POKE_WIRE_RADIX1 = new PrefMonitor("pokeRadix1",
          radixStrings, RadixOption.RADIX_2.getSaveString());
    POKE_WIRE_RADIX2 = new PrefMonitor("pokeRadix2",
          radixStrings, RadixOption.RADIX_10_SIGNED.getSaveString());
  }

  // Experimental preferences
  public static final PrefMonitor<Boolean> AUTO_BACKUP = new PrefMonitor("autobackup", true);
  public static final PrefMonitor<Integer> AUTO_BACKUP_FREQ = new PrefMonitor("autobackupFreq", 7);
  public static final String ACCEL_DEFAULT = "default";
  public static final String ACCEL_NONE = "none";
  public static final String ACCEL_METAL = "metal";
  public static final String ACCEL_OPENGL = "opengl";
  public static final String ACCEL_D3D = "d3d";
  public static final PrefMonitor<String> GRAPHICS_ACCELERATION =
    new PrefMonitor("graphicsAcceleration",
          new String[] { ACCEL_DEFAULT, ACCEL_NONE, ACCEL_METAL, ACCEL_OPENGL, ACCEL_D3D },
          ACCEL_DEFAULT);
  public static final String DUALSCREEN_NONE = "none";
  public static final String DUALSCREEN_FIX = "fixBlackWindows";
  public static final String DUALSCREEN_MORE = "fixBlackWindowsMore";
  public static final String DUALSCREEN_MOST = "fixBlackWindowsBest";
  public static final PrefMonitor<String> DUALSCREEN =
    new PrefMonitor("dualScreenFixes",
          new String[] { DUALSCREEN_NONE, DUALSCREEN_FIX, DUALSCREEN_MORE, DUALSCREEN_MOST },
          DUALSCREEN_NONE);

  // Third party softwares preferences
  public static final PrefMonitor<String>
      QUESTA_PATH = new PrefMonitor("questaPath", "");
  public static final PrefMonitor<Boolean>
      QUESTA_VALIDATION = new PrefMonitor("questaValidation", false);

  // hidden window preferences - not part of the preferences dialog, changes
  // to preference does not affect current windows, and the values are not
  // saved until the application is closed
  public static final RecentProjects
      RECENT_PROJECTS = new RecentProjects();

  public static final PrefMonitor<Double>
      TICK_FREQUENCY = new PrefMonitor("tickFrequency", 1.0);

  public static final PrefMonitor<Boolean>
      LAYOUT_SHOW_GRID = new PrefMonitor("layoutGrid", true);

  public static final PrefMonitor<Double>
      LAYOUT_ZOOM = new PrefMonitor("layoutZoom", 1.0);

  public static final PrefMonitor<Boolean>
      APPEARANCE_SHOW_GRID = new PrefMonitor("appearanceGrid", true);

  public static final PrefMonitor<Double>
      APPEARANCE_ZOOM = new PrefMonitor("appearanceZoom", 1.0);

  public static final PrefMonitor<Integer>
      WINDOW_STATE = new PrefMonitor("windowState", JFrame.NORMAL);

  public static final PrefMonitor<Integer>
      WINDOW_WIDTH = new PrefMonitor("windowWidth", 640);

  public static final PrefMonitor<Integer>
      WINDOW_HEIGHT = new PrefMonitor("windowHeight", 480);

  public static final PrefMonitor<String>
      WINDOW_LOCATION = new PrefMonitor("windowLocation", "0,0");

  public static final PrefMonitor<Double>
      WINDOW_MAIN_SPLIT = new PrefMonitor("windowMainSplit", 0.25);

  public static final PrefMonitor<Double>
      WINDOW_LEFT_SPLIT = new PrefMonitor("windowLeftSplit", 0.5);

  public static final PrefMonitor<Double>
      WINDOW_RIGHT_SPLIT = new PrefMonitor("windowRightSplit", 0.75);

  public static final PrefMonitor<String>
      DIALOG_DIRECTORY = new PrefMonitor("dialogDirectory", "");

  public static final PrefMonitor<Integer>
      WIRING_TOOL_TIP = new PrefMonitor("wiringToolTip", 3);

  public static final PrefMonitor<Integer>
      CUTTER_TOOL_TIP = new PrefMonitor("cutterToolTip", 3);
}
