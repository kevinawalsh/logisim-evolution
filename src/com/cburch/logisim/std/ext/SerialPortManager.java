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

package com.cburch.logisim.std.ext;
import static com.cburch.logisim.std.Strings.S;

import java.util.ArrayList;
import java.util.WeakHashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import com.fazecast.jSerialComm.SerialPort;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.JDialogOk;

public class SerialPortManager {

  private static class OpenPortInfo {
    String path;
    SerialPort port;
    // InstanceComponent ic; // SerialIn instance controlling this port
    CircuitState cs; // The circuit state (simulation) ic is part of,
                     // which may be a substate of some other simulation,
                     // etc., up to the ancestor state.

    OpenPortInfo(String path, SerialPort port, /* InstanceComponent ic,*/ CircuitState cs) {
      this.path = path;
      this.port = port;
      // this.ic = ic;
      this.cs = cs;
    }
  }

  private static final ArrayList<OpenPortInfo> openPorts = new ArrayList<>();
  private static final WeakHashMap<Project, Integer> projects = new WeakHashMap<>();

  private static PortProjectListener myListener = new PortProjectListener();

  private static final Object lock = new Object();


  private static OpenPortInfo findOpenPort(String path) {
    for (OpenPortInfo i : openPorts)
      if (i.path.equals(path))
        return i;
    return null;
  }

  private static OpenPortInfo findOpenPort(SerialPort port) {
    synchronized(lock) {
      for (OpenPortInfo i : openPorts)
        if (i.port == port)
          return i;
      return null;
    }
  }

  static SerialPort openPort(/* InstanceComponent ic, */ CircuitState cs, String path, int baud, String mode) throws Exception {
    if (/*ic == null ||*/ cs == null)
      throw new Exception("sorry :(");
    int bits, parity, stop;
    try {
      // mode is "8n1" or similar
      bits = mode.charAt(0) - '0';
      char par = mode.toLowerCase().charAt(1);
      parity =
          par == 'e' ? SerialPort.EVEN_PARITY :
          par == 'o' ? SerialPort.ODD_PARITY :
          SerialPort.NO_PARITY; // 'n'
      stop = mode.charAt(2) - '0';
    } catch (Exception e) {
      throw new Exception("bad mode (" + mode +")");
    }
    OpenPortInfo prev = findOpenPort(path);
    if (prev != null && prev.port != null) {
      boolean wasClosed = maybeForceClose(prev);
      if (!wasClosed)
        throw new Exception("cancelled");
    }
    synchronized (lock) {
      prev = findOpenPort(path);
      if (prev != null)
        close(prev);
      SerialPort port = SerialPort.getCommPort(path);
      port.setComPortParameters(baud, bits, stop, parity);
      port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
      port.setDTR();
      port.setRTS();
      if (!port.openPort())
        throw new Exception(String.format("error (%d)", port.getLastErrorCode()));
      
      OpenPortInfo info = new OpenPortInfo(path, port, /* ic,*/ cs);
      openPorts.add(info);

      Project proj = cs.getProject();
      // System.out.println("opened serial port: " + path);
      // System.out.println("  for comp " + ic + " within circuit state: " + cs);
      // while (cs.isSubstate()) {
      //   // Component sub = cs.getSubcircuit();
      //   cs = cs.getParentState();
      //   // System.out.println("  of comp " + sub + " within circuit state: " + cs);
      //   System.out.println("  within circuit state: " + cs);
      // }
      // System.out.println("  in project: " + proj.getLogisimFile().getName());

      Integer i = projects.get(proj);
      if (i == null) {
        projects.put(proj, (Integer)1);
        proj.addProjectWeakListener(null, myListener);
      } else {
        projects.put(proj, (Integer)(i+1));
      }

      return port;
    }
  }

  private static void close(OpenPortInfo info) {
    synchronized (lock) {
      if (info.port == null)
        return;
      // System.out.println("closing serial port: " + info.path);
      try {
        info.port.closePort();
      } catch (Exception e) {
        System.err.println("Error closing port: " + info.path);
        e.printStackTrace();
      }
      info.port = null;
      openPorts.remove(info);
     
      Project proj = info.cs.getProject();
      Integer i = projects.get(proj);
      if (i == null || i <= 1) {
        projects.remove(proj);
        proj.removeProjectWeakListener(null, myListener);
      } else {
        projects.put(proj, (Integer)(i-1));
      }
    }
  }

  public static void closePort(SerialPort port) {
    synchronized (lock) {
      OpenPortInfo prev = findOpenPort(port);
      if (prev == null)
        return;
      close(prev);
    }
  }

  private static class PortProjectListener implements ProjectListener {
    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_STATE) {
        // System.out.println("set sim state");
      } else if (action == ProjectEvent.ACTION_CLEAR_STATES) {
        // System.out.println("clear sim states... close all ports?");
        Project proj = event.getProject();
        synchronized(lock) {
          for (int i = openPorts.size()-1; i >= 0; i--) {
            OpenPortInfo info = openPorts.get(i);
            if (info.cs.getProject() == proj)
              close(info);
          }
        }
      } else if (action == ProjectEvent.ACTION_ADD_STATE) {
        CircuitState cs = (CircuitState)event.getData();
        // System.out.println("new cs: " + cs);
      } else if (action == ProjectEvent.ACTION_DELETE_STATE) {
        CircuitState cs = (CircuitState)event.getData();
        Project proj = cs.getProject();
        synchronized(lock) {
          for (int i = openPorts.size()-1; i >= 0; i--) {
            OpenPortInfo info = openPorts.get(i);
            if (info.cs.getProject() == proj
                && (info.cs == cs || info.cs.hasAncestorState(cs)))
              close(info);
          }
        }
        // System.out.println("killed cs: " + cs);
        // while (cs.isSubstate()) {
        //   // Component sub = cs.getSubcircuit();
        //   cs = cs.getParentState();
        //   // System.out.println("  of comp " + sub + " within circuit state: " + cs);
        //   System.out.println("  within circuit state: " + cs);
        // }
        // System.out.println("  in project: " + proj.getLogisimFile().getName());
      }
    }
  }

  private static boolean maybeForceClose(OpenPortInfo info) {
    ForceCloseDialog dialog = new ForceCloseDialog(info);
    dialog.setVisible(true);
    return dialog.wasClosed;
  }

  private static class ForceCloseDialog extends JDialogOk {
    OpenPortInfo info;
    boolean wasClosed;

    ForceCloseDialog(OpenPortInfo info) {
      super("Force Open Serial Port?", true);
      this.info = info;

      String msg = "Click OK to force open '" + info.path + "'.\n"
          + "\nThis port is already opened within another simulation."
          + "\nIf you force the port open here, the other simulation's"
          + " port will be closed.\n"
          + "\nThe other simulation is:";
      CircuitState cs = info.cs;
      Project proj = cs.getProject();
      msg += String.format("\n     %s [simulation %d]", cs.getCircuit().getName(), cs.getId());
      while (cs.isSubstate()) {
        // Component sub = cs.getSubcircuit();
        cs = cs.getParentState();
        msg += String.format("\n     within %s [simulation %d]", cs.getCircuit().getName(), cs.getId());
      }
      msg += String.format("\n     of project %s", proj.getLogisimFile().getName());
      msg += "\n\nAre you sure you want to force-open the port?";

      JTextArea textArea = new JTextArea(10, 50);
      textArea.setEditable(false);
      textArea.setText(msg);
      textArea.setCaretPosition(0);

      JScrollPane scrollPane = new JScrollPane(textArea);
      Box box = Box.createHorizontalBox();
      box.add(new JLabel(UIManager.getIcon("OptionPane.informationIcon")));
      box.add(scrollPane);
      box.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

      getContentPane().add(box);
      pack();
    }

    public void okClicked() {
      close(info);
      wasClosed = true;
    }

    public void cancelClicked() {
      wasClosed = false;
    }
  }

}
