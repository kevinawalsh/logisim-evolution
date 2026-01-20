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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.gui.menu.MenuListener;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

class SimulationExplorer extends JPanel
  implements ProjectListener {

  private static final long serialVersionUID = 1L;
  private Project project;

  private HashMap<CircuitState, SimulationPanel> sims;
  private SimulationPanel currentCard;
  private WidthTrackingPanel simListPanel;

  SimulationExplorer(Project proj, MenuListener menu) {
    super(new BorderLayout());
    this.project = proj;

    SimulationToolbarModel toolbarModel = new SimulationToolbarModel(proj, menu);
    Toolbar toolbar = new Toolbar(toolbarModel);
    add(toolbar, BorderLayout.NORTH);

    sims = new HashMap<>();
    simListPanel = new WidthTrackingPanel();
    List<CircuitState> states = proj.getRootCircuitStates();
    for (CircuitState root : states) {
      SimulationPanel card = new SimulationPanel(root);
      sims.put(root, card);
      simListPanel.addChildAndStrut(card);
    }

    CircuitState cur = project.getCircuitState();
    if (cur != null) {
      currentCard = sims.get(cur.getAncestorState());
      currentCard.setCurrentView(cur);
    }

    simListPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    simListPanel.setBackground(UIManager.getColor("Panel.background"));

    JScrollPane scrollPane = new JScrollPane(simListPanel);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getViewport().setBackground(simListPanel.getBackground());
    add(scrollPane, BorderLayout.CENTER);

    proj.addProjectWeakListener(null, this);
  }

  public void projectChanged(ProjectEvent event) {
    int action = event.getAction();
    if (action == ProjectEvent.ACTION_SET_STATE) {
      CircuitState chosenState = project.getCircuitState();
      SimulationPanel chosenPanel = sims.get(chosenState.getAncestorState());
      if (chosenPanel == null)
        return; // huh?
      if (chosenPanel != currentCard) {
        if (currentCard != null)
          currentCard.setCurrentView(null);
        currentCard = chosenPanel;
      }
      currentCard.setCurrentView(chosenState);
    } else if (action == ProjectEvent.ACTION_CLEAR_STATES) {
      sims.clear();
      currentCard = null;
      SwingUtilities.invokeLater(() -> {
        simListPanel.removeAll();
        simListPanel.revalidate();
        simListPanel.repaint();
      });
    } else if (action == ProjectEvent.ACTION_ADD_STATE) {
      CircuitState root = (CircuitState)event.getData();
      SimulationPanel card = new SimulationPanel(root);
      sims.put(root, card);
      SwingUtilities.invokeLater(() -> {
        simListPanel.addChildAndStrut(card);
        simListPanel.revalidate();
        simListPanel.repaint();
      });
    } else if (action == ProjectEvent.ACTION_DELETE_STATE) {
      CircuitState root = (CircuitState)event.getData();
      SimulationPanel card = sims.remove(root);
      if (card != null) {
        if (card == currentCard)
          currentCard = null;
        SwingUtilities.invokeLater(() -> {
          simListPanel.removeChildAndStrut(card);
          simListPanel.revalidate();
          simListPanel.repaint();
        });
      }
    }
  }


  private static class WidthTrackingPanel extends JPanel implements Scrollable {
    public WidthTrackingPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return Math.max(visibleRect.height - 16, 16);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }

    void addChildAndStrut(Component child) {
      if (getComponentCount() > 0)
        add(Box.createVerticalStrut(8));
      add(child);
    }

    void removeChildAndStrut(Component child) {
      int i = getComponentZOrder(child);
      if (i < 0) return;

      remove(child);

      if (i < getComponentCount()) {
        Component after = getComponent(i);
        if (after instanceof Box.Filler) {
          remove(after);
        }
      }
      else if (i > 0) {
        Component before = getComponent(i - 1);
        if (before instanceof Box.Filler) {
          remove(before);
        }
      }
    }

  }

}
