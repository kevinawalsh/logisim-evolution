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

package com.cburch.hex;

import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import com.cburch.logisim.util.WeakList;

public class Test {
	private static class Model implements HexModel {
		private WeakList<HexModelListener> listeners = new WeakList<>();
		private int[] data = new int[924];

		public void addHexModelWeakListener(Object owner, HexModelListener l) {
			listeners.add(owner, l);
		}

		public void fill(long start, long len, int value) {
			int[] oldValues = new int[(int) len];
			System.arraycopy(data, (int) (start - 11111), oldValues, 0,
					(int) len);
			Arrays.fill(data, (int) (start - 11111), (int) len, value);
			for (HexModelListener l : listeners) {
				l.bytesChanged(this, start, len, oldValues);
			}
		}

		public int get(long address) {
			return data[(int) (address - 11111)];
		}

		public long getFirstOffset() {
			return 11111;
		}

		public long getLastOffset() {
			return data.length + 11110;
		}

		public int getValueWidth() {
			return 9;
		}

		public void removeHexModelWeakListener(Object owner, HexModelListener l) {
			listeners.remove(owner, l);
		}

		public void set(long address, int value) {
			int[] oldValues = new int[] { data[(int) (address - 11111)] };
			data[(int) (address - 11111)] = value & 0x1FF;
			for (HexModelListener l : listeners) {
				l.bytesChanged(this, address, 1, oldValues);
			}
		}

		public void set(long start, int[] values) {
			int[] oldValues = new int[values.length];
			System.arraycopy(data, (int) (start - 11111), oldValues, 0,
					values.length);
			System.arraycopy(values, 0, data, (int) (start - 11111),
					values.length);
			for (HexModelListener l : listeners) {
				l.bytesChanged(this, start, values.length, oldValues);
			}
		}
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		HexModel model = new Model();
		HexEditor editor = new HexEditor(model);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(new JScrollPane(editor));
		frame.pack();
		frame.setVisible(true);
	}
}
