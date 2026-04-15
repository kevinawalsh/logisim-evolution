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

public class Test {
	private static class Model implements HexModel {
    HexModelListener listener = null;
		private int[] data = new int[924];
		
    public void clearContents() {
      clearContents(0, getValueCount());
		}

    public void clearHexFrameRef(Object o) { }

		public void clearContents(long start, long len) {
			Arrays.fill(data, (int) (start), (int) len, 0);
			listener.bytesChanged(start, len);
		}

		public int get(long address) {
			return data[(int) (address)];
		}
		
    public long getValueCount() {
			return data.length;
		}

		public long getLastOffset() {
			return data.length - 1;
		}

		public int getValueWidth() {
			return 9;
		}

		public void setContents(long address, int value) {
			data[(int) (address)] = value & 0x1FF;
      listener.bytesChanged(address, 1);
		}

		public void setContents(long start, int[] values) {
			System.arraycopy(values, 0, data, (int) (start), values.length);
      listener.bytesChanged(start, values.length);
		}
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		Model model = new Model();
		HexEditor editor = new HexEditor(model);
    model.listener = editor.getListener();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(new JScrollPane(editor));
		frame.pack();
		frame.setVisible(true);
	}
}
