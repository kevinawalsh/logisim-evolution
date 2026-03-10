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

package com.cburch.logisim.gui.main;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import com.cburch.draw.model.CanvasModelEvent;
import com.cburch.draw.model.CanvasModelListener;
import com.cburch.hdl.HdlModel;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.appear.RevertAppearanceAction;
import com.cburch.logisim.gui.generic.CardPanel;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.MenuListener;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.gui.menu.SimulateListener;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

public class MainMenuListener extends MenuListener {

  protected class FileListener implements ActionListener {
    public void actionPerformed(ActionEvent event) {
      Object src = event.getSource();
      Project proj = frame.getProject();
      if (src == LogisimMenuBar.EXPORT_IMAGE) {
        ExportImage.doExport(proj);
      } else if (src == LogisimMenuBar.PRINT) {
        Print.doPrint(proj);
      }
    }

    public void register() {
      menubar.addActionListener(LogisimMenuBar.EXPORT_IMAGE, this);
      menubar.addActionListener(LogisimMenuBar.PRINT, this);
    }
  }

  protected class ProjectMenuListener implements ProjectListener, LibraryListener,
        ActionListener, PropertyChangeListener, CanvasModelListener {
    public void actionPerformed(ActionEvent event) {
      Object src = event.getSource();
      Project proj = frame.getProject();
      if (proj == null)
        return;
      LogisimFile file = proj.getLogisimFile();
      if (file == null)
        return;
      Circuit circ = proj.getCurrentCircuit();
      HdlModel hdl = proj.getCurrentHdl();
      if (src == LogisimMenuBar.ADD_CIRCUIT) {
        ProjectCircuitActions.doAddCircuit(proj);
      } else if (src == LogisimMenuBar.ADD_VHDL) {
        ProjectCircuitActions.doAddVhdl(proj);
      } else if (src == LogisimMenuBar.IMPORT_VHDL) {
        ProjectCircuitActions.doImportVhdl(proj);
      } else if (src == LogisimMenuBar.ADD_TOOL) {
        ProjectCircuitActions.doAddTool(proj);
      } else if (src == LogisimMenuBar.MOVE_TOOL_UP) {
        if (circ != null && file.getCircuits().contains(circ))
          ProjectCircuitActions.doMoveCircuit(proj, circ, -1);
        else if (hdl != null && file.getVhdlContents().contains(hdl))
          ProjectCircuitActions.doMoveHdl(proj, hdl, -1);
      } else if (src == LogisimMenuBar.MOVE_TOOL_DOWN) {
        if (circ != null && file.getCircuits().contains(circ))
          ProjectCircuitActions.doMoveCircuit(proj, circ, 1);
        else if (hdl != null && file.getVhdlContents().contains(hdl))
          ProjectCircuitActions.doMoveHdl(proj, hdl, 1);
      } else if (src == LogisimMenuBar.SET_MAIN_CIRCUIT) {
        ProjectCircuitActions.doSetAsMainCircuit(proj, circ);
      } else if (src == LogisimMenuBar.REMOVE_TOOL) {
        if (circ != null && file.getCircuits().contains(circ))
          ProjectCircuitActions.doRemoveCircuit(proj, circ);
        else if (hdl != null && file.getVhdlContents().contains(hdl))
          ProjectCircuitActions.doRemoveHdl(proj, hdl);
      } else if (src == LogisimMenuBar.EDIT_LAYOUT) {
        if (proj.getCurrentHdl() != null)
          frame.setEditorView(Frame.EDIT_HDL); // not yet necessary
        else
          frame.setEditorView(Frame.EDIT_LAYOUT);
      } else if (src == LogisimMenuBar.EDIT_APPEARANCE) {
        frame.setEditorView(Frame.EDIT_APPEARANCE);
      } else if (src == LogisimMenuBar.TOGGLE_APPEARANCE) {
        boolean viewingAppearance = frame.getEditorView().equals(Frame.EDIT_APPEARANCE);
        if (circ != null)
          frame.setEditorView(viewingAppearance ? Frame.EDIT_LAYOUT : Frame.EDIT_APPEARANCE);
        // todo: custom appearance for vhdl components
        // else
        //   frame.setEditorView(viewAppearance ? Frame.EDIT_HDL : Frame.EDIT_APPEARANCE);
      } else if (src == LogisimMenuBar.REVERT_APPEARANCE) {
        if (circ != null && file.getCircuits().contains(circ))
          proj.doAction(new RevertAppearanceAction(circ));
        // todo: custom appearance for vhdl components
        // else if (hdl != null && file.getVhdlContents().contains(hdl))
        //   proj.doAction(new RevertAppearanceAction(hdl));
      } else if (src == LogisimMenuBar.ANALYZE_CIRCUIT) {
        if (circ != null)
          ProjectCircuitActions.doAnalyze(proj, circ);
      } else if (src == LogisimMenuBar.CIRCUIT_STATS) {
        if (circ != null)
          StatisticsDialog.show(frame, file, circ);
      }
    }

    public void computeEnabled() {
      Project proj = frame == null ? null : frame.getProject();
      LogisimFile file = proj == null ? null : proj.getLogisimFile();
      Circuit circ = proj == null ? null : proj.getCurrentCircuit();
      HdlModel hdl = proj == null ? null : proj.getCurrentHdl();
      int circIndex = file == null || circ == null ? -1 : file.indexOfCircuit(circ);
      int hdlIndex = file == null || hdl == null ? -1 : file.indexOfHdl(hdl);
      int curIndex = circIndex >= 0 ? circIndex : hdlIndex;
      String editorView = frame == null ? "" : frame.getEditorView();
      boolean canSetMain = false;
      boolean canMoveUp = false;
      boolean canMoveDown = false;
      boolean canRemove = false;
      boolean canRevert = false;
      boolean viewingAppearance = editorView.equals(Frame.EDIT_APPEARANCE);
      boolean viewingLayout = editorView.equals(Frame.EDIT_LAYOUT);
      if (curIndex >= 0) {
        int n = file.getTools().size();
        canMoveUp = curIndex > 0;
        canMoveDown = curIndex < n - 1;
        canRemove = curIndex == hdlIndex || file.getCircuits().size() > 1;
      }
      if (circIndex >= 0) {
        canSetMain = file.getMainCircuit() != circ;
        canRevert = viewingAppearance
            && !circ.getAppearance().isDefaultAppearance();
      }

      if (circ != null)
        menubar.setCircuitName(circ.getName());
      else if (hdl != null)
        menubar.setVhdlName(hdl.getName());
      else
        menubar.setCircuitName(null);

      menubar.setEnabled(LogisimMenuBar.ADD_CIRCUIT, true);
      menubar.setEnabled(LogisimMenuBar.ADD_VHDL, true);
      menubar.setEnabled(LogisimMenuBar.IMPORT_VHDL, true);
      menubar.setEnabled(LogisimMenuBar.ADD_TOOL, true);
      menubar.setEnabled(LogisimMenuBar.MOVE_TOOL_UP, canMoveUp);
      menubar.setEnabled(LogisimMenuBar.MOVE_TOOL_DOWN, canMoveDown);
      menubar.setEnabled(LogisimMenuBar.SET_MAIN_CIRCUIT, canSetMain);
      menubar.setEnabled(LogisimMenuBar.REMOVE_TOOL, canRemove);
      menubar.setEnabled(LogisimMenuBar.EDIT_LAYOUT, viewingAppearance);
      menubar.setEnabled(LogisimMenuBar.EDIT_APPEARANCE, viewingLayout);
      menubar.setEnabled(LogisimMenuBar.TOGGLE_APPEARANCE, viewingLayout || viewingAppearance);
      menubar.setEnabled(LogisimMenuBar.REVERT_APPEARANCE, canRevert);
      menubar.setEnabled(LogisimMenuBar.ANALYZE_CIRCUIT, viewingLayout);
      menubar.setEnabled(LogisimMenuBar.CIRCUIT_STATS, viewingLayout);
      fireEnableChanged();
    }

    protected void computeRevertEnabled() {
      // do this separately since it can happen rather often
      Project proj = frame.getProject();
      LogisimFile file = proj.getLogisimFile();
      // todo: custom appearance for vhdl components
      Circuit circ = proj.getCurrentCircuit();
      boolean viewingAppearance = frame.getEditorView().equals(Frame.EDIT_APPEARANCE);
      boolean canRevert = file.contains(circ) && viewingAppearance
          && !circ.getAppearance().isDefaultAppearance();
      boolean oldValue = menubar.isEnabled(LogisimMenuBar.REVERT_APPEARANCE);
      if (canRevert != oldValue) {
        menubar.setEnabled(LogisimMenuBar.REVERT_APPEARANCE, canRevert);
        fireEnableChanged();
      }
    }

    public void libraryChanged(LibraryEvent event) {
      computeEnabled();
    }

    public void modelChanged(CanvasModelEvent event) {
      computeRevertEnabled();
    }

    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_CURRENT) {
        if (event.getOldData() instanceof Circuit) {
          Circuit old = (Circuit) event.getOldData();
          old.getAppearance().removeCanvasModelWeakListener(null, this);
        }
        if (event.getData() instanceof Circuit) {
          Circuit circ = (Circuit) event.getData();
          circ.getAppearance().addCanvasModelWeakListener(null, this);
        }
        computeEnabled();
      } else if (action == ProjectEvent.ACTION_SET_FILE) {
        computeEnabled();
      }
    }

    public void propertyChange(PropertyChangeEvent e) {
      computeEnabled();
    }

    void register() {
      Project proj = frame.getProject();
      if (proj == null) {
        return;
      }

      proj.addProjectWeakListener(null, this);
      proj.addLibraryWeakListener(null, this);
      frame.addPropertyChangeListener(Frame.EDITOR_VIEW, this);
      frame.addPropertyChangeListener(Frame.EXPLORER_VIEW, this);
      Circuit circ = proj.getCurrentCircuit();
      if (circ != null) {
        circ.getAppearance().addCanvasModelWeakListener(null, this);
      }

      menubar.addActionListener(LogisimMenuBar.ADD_CIRCUIT, this);
      menubar.addActionListener(LogisimMenuBar.ADD_VHDL, this);
      menubar.addActionListener(LogisimMenuBar.IMPORT_VHDL, this);
      menubar.addActionListener(LogisimMenuBar.ADD_TOOL, this);
      menubar.addActionListener(LogisimMenuBar.MOVE_TOOL_UP, this);
      menubar.addActionListener(LogisimMenuBar.MOVE_TOOL_DOWN, this);
      menubar.addActionListener(LogisimMenuBar.SET_MAIN_CIRCUIT, this);
      menubar.addActionListener(LogisimMenuBar.REMOVE_TOOL, this);
      menubar.addActionListener(LogisimMenuBar.EDIT_LAYOUT, this);
      menubar.addActionListener(LogisimMenuBar.EDIT_APPEARANCE, this);
      menubar.addActionListener(LogisimMenuBar.TOGGLE_APPEARANCE, this);
      menubar.addActionListener(LogisimMenuBar.REVERT_APPEARANCE, this);
      menubar.addActionListener(LogisimMenuBar.ANALYZE_CIRCUIT, this);
      menubar.addActionListener(LogisimMenuBar.CIRCUIT_STATS, this);

      computeEnabled();
    }
  }

  protected class SimulateMenuListener implements ProjectListener, SimulateListener {
    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_STATE) {
        menubar.setCircuitState(frame.getProject().getSimulator(),
            frame.getProject().getCircuitState());
      }
    }

    void register() {
      Project proj = frame.getProject();
      proj.addProjectWeakListener(null, this);
      menubar.setSimulateListener(this);
      menubar.setCircuitState(proj.getSimulator(), proj.getCircuitState());
    }

    public void stateChangeRequested(Simulator sim, CircuitState state) {
      if (state != null)
        frame.getProject().setCircuitState(state);
    }
  }

  protected Frame frame;
  protected FileListener fileListener = new FileListener();
  protected ProjectMenuListener projectListener = new ProjectMenuListener();
  protected SimulateMenuListener simulateListener = new SimulateMenuListener();

  public MainMenuListener(Frame frame, LogisimMenuBar menubar) {
    super(menubar);
    this.frame = frame;
  }

  public void register(CardPanel mainPanel) {
    fileListener.register();
    editListener.register();
    projectListener.register();
    simulateListener.register();
  }

}
