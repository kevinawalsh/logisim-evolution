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

package com.cburch.logisim.data;

import java.awt.Window;
import java.io.File;

import javax.swing.JTextField;

import com.cburch.logisim.util.StringGetter;

public abstract class Attribute<V> {
  private String name;
  private StringGetter disp;

  public Attribute(String name, StringGetter disp) {
    this.name = name;
    this.disp = disp;
  }

  protected java.awt.Component getCellEditor(V value) {
    return new JTextField(toDisplayString(value));
  }

  public java.awt.Component getCellEditor(Window source, V value) {
    return getCellEditor(value);
  }

  public String getDisplayName() {
    return disp.toString();
  }

  public String getName() {
    return name;
  }

  public V parseFromUser(Window source, String value) {
    return parse(value);
  }

  public V parseFromFilesystem(File directory, String value) {
    return parse(value);
  }

  public abstract V parse(String value);

  public String toDisplayString(V value) {
    return value == null ? "" : value.toString();
  }

  public Object toDisplayObject(V value) {
    return toDisplayString(value);
  }

  public String toStandardString(V value) {
    return value.toString();
  }

  public String toStandardStringRelative(V value, String outFilepath) {
    return toStandardString(value);
  }

  @Override
  public String toString() {
    return name;
  }
}
