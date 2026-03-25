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

package com.cburch.logisim.tools;
import static com.cburch.logisim.tools.Strings.S;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.ExternalClipboard;
import com.cburch.logisim.gui.main.LayoutClipboard;
import com.cburch.logisim.gui.main.LayoutEditHandler;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringUtil;

public final class MenuTool extends Tool {
  private static class MenuComponent extends JPopupMenu implements ActionListener {
    private static final long serialVersionUID = 1L;
    Project proj;
    Circuit circ;
    Component comp;
    JMenuItem cut = new JMenuItem(S.get("compCutItem"));
    JMenuItem copy = new JMenuItem(S.get("compCopyItem"));
    JMenuItem dup = new JMenuItem(S.get("compDuplicateItem"));
    JMenuItem del = new JMenuItem(S.get("compDeleteItem"));
    // JMenuItem attrs = new JMenuItem(S.get("compShowAttrItem"));
    JMenuItem rotate = new JMenuItem(S.get("compRotate"));

    MenuComponent(Project proj, Circuit circ, Component comp) {
      this.proj = proj;
      this.circ = circ;
      this.comp = comp;
      boolean canChange = proj.getLogisimFile().contains(circ);

      add(cut);
      cut.addActionListener(this);
      cut.setEnabled(canChange);
      add(copy);
      copy.addActionListener(this);
      copy.setEnabled(canChange);
      add(dup);
      dup.addActionListener(this);
      dup.setEnabled(canChange);
      add(del);
      del.addActionListener(this);
      del.setEnabled(canChange);

      if (comp.getAttributeSet().containsAttribute(StdAttr.FACING)) {
        addSeparator();
        add(rotate);
        rotate.setEnabled(canChange);
        rotate.addActionListener(this);
      }

      // add(attrs);
      // attrs.addActionListener(this);
    }

    public void actionPerformed(ActionEvent e) {
      Collection<Component> comps = Collections.singletonList(comp);
      Object src = e.getSource();
      if (src == copy) {
        SelectionActions.doCopy(proj, comps);
        return;
      }
      CircuitMutation xn = new CircuitMutation(circ);
      if (src == cut) {
        SelectionActions.doCopy(proj, comps);
        xn.remove(comp);
        proj.doAction(xn.toAction(S.getter(
                "removeComponentAction", comp.getFactory().getDisplayGetter())));
      } else if (src == dup) {
        Map<Component, Component> m = Selection.copyComponents(proj, circ, comps);
        Component newComp = m.get(comp);
        xn.add(newComp);
        proj.doAction(xn.toAction(S.getter(
                "duplicateComponentAction", comp.getFactory().getDisplayGetter())));
      } else if (src == del) {
        xn.remove(comp);
        proj.doAction(xn.toAction(S.getter(
                "removeComponentAction", comp.getFactory().getDisplayGetter())));
      // } else if (src == attrs) {
      //  proj.getFrame().viewComponentAttributes(circ, comp);
      } else if (src == rotate) {
        Direction d = comp.getAttributeSet().getValue(StdAttr.FACING);
        xn.set(comp, StdAttr.FACING, d.getRight());
        proj.doAction(xn.toAction(S.getter(
                "rotateComponentAction", comp.getFactory().getDisplayGetter())));
      }
    }
  }

  private static class MenuSelection extends JPopupMenu implements ActionListener {
    private static final long serialVersionUID = 1L;
    Project proj;
    JMenuItem cut = new JMenuItem(S.get("selCutItem"));
    JMenuItem copy = new JMenuItem(S.get("selCopyItem"));
    JMenuItem dup = new JMenuItem(S.get("selDuplicateItem"));
    JMenuItem del = new JMenuItem(S.get("selDeleteItem"));
    JMenuItem rotate = new JMenuItem(S.get("selRotateItem"));

    MenuSelection(Project proj) {
      this.proj = proj;
      boolean canChange = proj.getLogisimFile().contains(proj.getCurrentCircuit());
      add(cut);
      cut.addActionListener(this);
      cut.setEnabled(canChange);
      add(copy);
      copy.addActionListener(this);
      add(dup);
      dup.addActionListener(this);
      dup.setEnabled(canChange);
      add(del);
      del.addActionListener(this);
      del.setEnabled(canChange);

      for (Component comp : proj.getSelection().getComponents()) {
        // hmmmm... allow rotate if any allow it, or only if all allow it?
        // if (comp instanceof Wire)
        //  continue;
        if (comp.getAttributeSet().containsAttribute(StdAttr.FACING)) {
          addSeparator();
          add(rotate);
          rotate.addActionListener(this);
          rotate.setEnabled(canChange);
          break;
        }
      }
    }

    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      Selection sel = proj.getSelection();
      if (src == cut)
        SelectionActions.doCut(proj, sel);
      else if (src == copy)
        SelectionActions.doCopy(proj, sel);
      else if (src == dup)
        proj.doAction(SelectionActions.duplicate(sel));
      else if (src == del)
        proj.doAction(SelectionActions.delete(sel));
      else if (src == rotate) {
        Circuit circ = proj.getCurrentCircuit();
        CircuitMutation xn = new CircuitMutation(circ);
        int n = 0;
        Component singleton = null;
        for (Component comp : sel.getComponents()) {
          // hmmmm... allow rotate if any allow it, or only if all allow it?
          // if (comp instanceof Wire)
          //  continue;
          if (comp.getAttributeSet().containsAttribute(StdAttr.FACING)) {
            singleton = comp;
            Direction d = comp.getAttributeSet().getValue(StdAttr.FACING);
            xn.set(comp, StdAttr.FACING, d.getRight());
            n++;
          }
        }
        proj.doAction(xn.toAction(
              n == 1
              ? S.getter("rotateComponentAction", singleton.getFactory().getDisplayGetter())
              : S.getter("rotateSelectionAction", StringUtil.constantGetter(n))));
      }
    }
  }

  private static class MenuCircuit extends JPopupMenu implements ActionListener {
    private static final long serialVersionUID = 1L;
    Project proj;
    Circuit circ;

    JMenuItem paste = new JMenuItem(S.get("circuitPasteItem"));
    JMenuItem all = new JMenuItem(S.get("circuitSelectAllItem"));

    MenuCircuit(Project proj, Circuit circ) {
      this.proj = proj;
      this.circ = circ;
      boolean canChange = proj.getLogisimFile().contains(circ);

      add(paste);
      paste.addActionListener(this);
      paste.setEnabled(canChange &&
          (LayoutClipboard.forComponents.isAvailable()
           || ExternalClipboard.forString.isAvailable()
           || ExternalClipboard.forImage.isAvailable()));
      addSeparator();
      add(all);
      all.addActionListener(this);
      all.setEnabled(canChange);
    }

    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      if (src == paste)
        LayoutEditHandler.paste(proj.getFrame());
      else if (src == all)
        LayoutEditHandler.selectAll(proj.getFrame());
    }
  }

  private MenuTool() { }

  public static final MenuTool SINGLETON = new MenuTool();

  @Override
  public boolean isBuiltin() { return true; }

  @Override
  public String getDescription() {
    return S.get("menuToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("menuTool");
  }

  @Override
  public String getName() {
    return "Menu Tool";
  }

  private JPopupMenu menuFor(Canvas canvas, Component comp) {
    JPopupMenu menu = new MenuComponent(canvas.getProject(), canvas.getCircuit(), comp);
    MenuExtender extender = (MenuExtender) comp.getFeature(MenuExtender.class);
    if (extender != null)
      extender.configureMenu(menu, canvas.getProject());
    return menu;
  }

  @Override
  public void mousePressed(Canvas canvas, MouseEvent e) {
    int x = e.getX();
    int y = e.getY();
    Location pt = Location.create(x, y);

    JPopupMenu menu;
    Project proj = canvas.getProject();
    Selection sel = proj.getSelection();
    Collection<Component> selClicked = sel.getComponentsContaining(pt);
    if (!selClicked.isEmpty() && sel.getComponents().size() > 1) {
      menu = new MenuSelection(proj);
    } else if (!selClicked.isEmpty()) {
      menu = menuFor(canvas, selClicked.iterator().next());
    } else {
      Collection<Component> clicked = canvas.getCircuit().getAllVisiblyContaining(pt);
      if (!clicked.isEmpty())
        menu = menuFor(canvas, clicked.iterator().next());
      else
        menu = new MenuCircuit(proj, canvas.getCircuit());
    }

    if (menu != null)
      canvas.showPopupMenu(menu, x, y);
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    Graphics2D g = c.getGraphics();
    g.setColor(Color.BLACK);
    g.fillRect(x + 2, y + 1, 9, 2);
    g.drawRect(x + 2, y + 3, 15, 12);
    g.setColor(Color.lightGray);
    g.drawLine(x + 4, y + 2, x + 8, y + 2);
    for (int y_offs = y + 6; y_offs < y + 15; y_offs += 3) {
      g.drawLine(x + 4, y_offs, x + 14, y_offs);
    }
  }
}
