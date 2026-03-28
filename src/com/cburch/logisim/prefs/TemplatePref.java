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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

import com.cburch.logisim.gui.start.Startup;
import com.cburch.logisim.util.WeakList;

public class TemplatePref {

  // Template preferences,
  // holds type (integer) and custom template path (optional, string).
  
  private static final String TYPE_KEY = "templateType";
  private static final String FILE_KEY = "templateFile";
  
  public static final int TEMPLATE_UNKNOWN = -1;
  public static final int TEMPLATE_EMPTY = 0;
  public static final int TEMPLATE_PLAIN = 1;
  public static final int TEMPLATE_CUSTOM = 2;

  private int typeValue;
  private File fileValue; // non-null only if typeValue==TEMPLATE_CUSTOM
  private Preferences backingStore;

  public TemplatePref() {
    this.typeValue = TEMPLATE_PLAIN;
    this.fileValue = null;
    this.backingStore = AppPreferences.getPrefs();
    backingStore.addPreferenceChangeListener(e -> backingStoreChanged(e));

    setFromBackingStore(false);
  }

  public int getType() {
    return typeValue;
  }

  public boolean isEmptyType() { return typeValue == TEMPLATE_EMPTY; }
  public boolean isPlainType() { return typeValue == TEMPLATE_PLAIN; }
  public boolean isCustomType() { return typeValue == TEMPLATE_CUSTOM; }

  public File getFile() {
    return fileValue;
  }

  public void setEmpty() {
    set(TEMPLATE_EMPTY, null, null);
  }

  public void setPlain() {
    set(TEMPLATE_PLAIN, null, null);
  }

  public void setCustom(File file, Template template) {
    set(TEMPLATE_CUSTOM, file, template);
  }

  private void set(int newType, File newFile, Template newTemplate) {
    if (setAndFire(newType, newFile, newTemplate))
      setIntoBackingStore();
  }

  private boolean setAndFire(int newType, File newFile, Template newTemplate) {
    if (newType == typeValue && (typeValue != TEMPLATE_CUSTOM || identical(newFile, fileValue)))
      return false;
    Object oldValue = typeValue == TEMPLATE_CUSTOM ? fileValue : (Integer)typeValue;
    Object newValue = newType == TEMPLATE_CUSTOM ? newFile : (Integer)newType;
    typeValue = newType;
    fileValue = newFile;
    customTemplate = newTemplate;
    customTemplateSourceFile = newFile;
    SwingUtilities.invokeLater(() ->
        firePrefChangeEvent(new AppPreferences.ChangeEvent<Object>(this, oldValue, newValue)));
    return true;
  }

  private void setIntoBackingStore() {
    try {
      String path = fileValue == null ? "" : fileValue.getCanonicalPath();
      // in case PreferenceChangeEvent arrives quickly, let's do path first,
      // as that callback will turn into a nop.
      backingStore.put(FILE_KEY, path);
      backingStore.putInt(TYPE_KEY, (Integer)typeValue);
    } catch (IOException ex) { }
  }

  private void backingStoreChanged(PreferenceChangeEvent event) {
    if (event.getKey().equals(TYPE_KEY)) {
      int newType = convertTypeFromString(event.getNewValue());
      if (newType == typeValue)
        return;
      setFromBackingStore(true); // type changed out from under us
    } else if (event.getKey().equals(FILE_KEY)) {
      String path = event.getNewValue();
      File newFile = (path == null || path.equals("")) ? null : new File(path);
      if (identical(newFile, fileValue))
        return;
      setFromBackingStore(true); // file changed out from under us
    }
  }

  private void setFromBackingStore(boolean shouldFire) {
    int newType = backingStore.getInt(TYPE_KEY, (Integer)TEMPLATE_PLAIN);
    String path = newType == TEMPLATE_CUSTOM ? backingStore.get(FILE_KEY, "") : null;
    File newFile = (path == null || path.equals("")) ? null : new File(path);
    if (newType == typeValue && (typeValue != TEMPLATE_CUSTOM || newFile.equals(fileValue)))
      return;
    Object oldValue = typeValue == TEMPLATE_CUSTOM ? fileValue : (Integer)typeValue;
    Object newValue = newType == TEMPLATE_CUSTOM ? newFile : (Integer)newType;
    typeValue = newType;
    fileValue = newFile;
    customTemplate = null;
    customTemplateSourceFile = null;
    if (shouldFire)
      SwingUtilities.invokeLater(() ->
          firePrefChangeEvent(new AppPreferences.ChangeEvent<Object>(this, oldValue, newValue)));
  }

  public Template getTemplate() {
    switch (typeValue) {
    case TEMPLATE_EMPTY:
      return getEmptyTemplate();
    case TEMPLATE_CUSTOM:
      return getCustomTemplate();
    case TEMPLATE_PLAIN:
    default:
      return getPlainTemplate();
    }
  }

  private Template emptyTemplate = null; // cached, set once
  public Template getEmptyTemplate() {
    if (emptyTemplate == null)
      emptyTemplate = Template.createEmpty();
    return emptyTemplate;
  }

  private Template plainTemplate = null; // cached, set once
  private Template getPlainTemplate() {
    if (plainTemplate == null) {
      ClassLoader ld = Startup.class.getClassLoader();
      InputStream in = ld.getResourceAsStream("resources/logisim/default-template.circ");
      if (in == null) {
        plainTemplate = getEmptyTemplate();
      } else {
        try {
          try {
            plainTemplate = Template.create(in);
          } finally {
            in.close();
          }
        } catch (Exception e) {
          plainTemplate = getEmptyTemplate();
        }
      }
    }
    return plainTemplate;
  }

  private Template customTemplate = null; // cached, may be reset
  private File customTemplateSourceFile = null; // file from where customTemplate came
  private Template getCustomTemplate() {
    File toRead = fileValue;
    if (customTemplateSourceFile == null || !(customTemplateSourceFile.equals(toRead))) {
      customTemplate = null;
      customTemplateSourceFile = null;
      if (toRead != null) {
        FileInputStream reader = null;
        try {
          reader = new FileInputStream(toRead);
          customTemplate = Template.create(reader);
          customTemplateSourceFile = toRead;
        } catch (Exception t) {
        } finally {
          if (reader != null) {
            try { reader.close(); }
            catch (IOException e) { }
          }
        }
      }
    }
    return customTemplate == null ? getPlainTemplate() : customTemplate;
  }

  private int convertTypeFromString(String s) {
    if (s == null)
      return TEMPLATE_PLAIN;
    try {
      int newValue = Integer.parseInt(s);
      if (newValue == TEMPLATE_EMPTY ||
          newValue == TEMPLATE_PLAIN ||
          newValue == TEMPLATE_CUSTOM)
        return newValue;
    } catch (NumberFormatException e) { }
    return TEMPLATE_PLAIN;
  }

  private final WeakList<AppPreferences.Listener<Object>> listeners = new WeakList<>();
  public void addPrefChangeWeakListener(Object owner, AppPreferences.Listener<Object> l) { listeners.add(owner, l); }
  public void removePrefChangeWeakListener(Object owner, AppPreferences.Listener<Object> l) { listeners.remove(owner, l); }
  private void firePrefChangeEvent(AppPreferences.ChangeEvent<Object> evt) { for (AppPreferences.Listener<Object> l : listeners) l.prefChanged(evt); }

  protected static boolean identical(File a, File b) {
    return (a == null && b == null)
        || (a != null && b != null && a.equals(b));
  }

}
