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

package com.cburch.logisim.instance;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.log.Loggable;

class InstanceLoggerAdapter implements Loggable {

  private InstanceComponent comp;
  private InstanceLogger logger;
  private InstanceStateImpl state;

  public InstanceLoggerAdapter(InstanceComponent comp,
      Class<? extends InstanceLogger> loggerClass) {
    try {
      this.comp = comp;
      this.logger = loggerClass.getDeclaredConstructor().newInstance();
      this.state = new InstanceStateImpl(null, comp);
    } catch (Exception t) {
      String className = loggerClass.getName();
      System.err.printf("Error while instantiating logger %s: %s\n", className,
          t.getClass().getName());
      String msg = t.getMessage();
      if (msg != null)
        System.err.printf("  (%s)\n", msg); // OK
      logger = null;
    }
  }

  private InstanceStateImpl instanceStateFor(CircuitState circuitState) {
      state.repurpose(circuitState, comp);
      return state;
  }

  public String getLogName(Object option) {
    return logger == null ? null : logger.getLogName(instanceStateFor(null), option);
  }

  public BitWidth getBitWidth(Object option) {
    return logger == null ? null : logger.getBitWidth(instanceStateFor(null), option);
  }

  public boolean isInput(Object option) {
    return logger == null ? false : logger.isInput(instanceStateFor(null), option);
  }

  public Object[] getLogOptions() {
    return logger == null ? null : logger.getLogOptions(instanceStateFor(null));
  }

  public Value getLogValue(CircuitState circuitState, Object option) {
    return logger == null ? Value.UNKNOWN : logger.getLogValue(instanceStateFor(circuitState), option);
  }
}
