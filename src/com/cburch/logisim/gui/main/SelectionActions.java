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
import static com.cburch.logisim.gui.main.Strings.S;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.cburch.hdl.HdlModel;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.CircuitTransactionResult;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.gui.menu.ProjectLibraryActions;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Dependencies;
import com.cburch.logisim.proj.JoinedAction;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.decor.Image;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.TextTool;

public class SelectionActions {

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */

  private abstract static class SelectionAnchoringAction extends Action {

    protected Selection sel;
    protected Circuit circuit;
    protected int count;
    protected SelectionSave before;
    protected CircuitMutation xn;
    protected CircuitTransaction xnReverse;

    SelectionAnchoringAction(Selection sel, int count) {
      this.sel = sel;
      this.circuit = sel.getProject().getCurrentCircuit();
      this.before = SelectionSave.create(sel);
      this.count = count;
    }

    @Override
    public void doIt(Project proj) {
      xn = new CircuitMutation(circuit);
      doIt(proj, circuit, xn);
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public void redo(Project proj) {
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    protected abstract void doIt(Project proj, Circuit circ, CircuitMutation xn);

    @Override
    public boolean shouldAppendTo(Action other) {
      Action last;
      if (other instanceof JoinedAction)
        last = ((JoinedAction) other).getLastAction();
      else
        last = other;

      SelectionSave otherAfter = null;
      // paste and duplicate create new floating selections, so
      // things that 
      if (last instanceof PasteComponents) {
        otherAfter = ((PasteComponents) last).after;
      } else if (last instanceof Duplicate) {
        otherAfter = ((Duplicate) last).after;
      }
      return otherAfter != null && otherAfter.equals(this.before);
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
    }

  }

  private static class Deselect extends SelectionAnchoringAction {
    // Deselect makes all floating elements become anchored, and clears the selection.

    Deselect(Selection sel, int count) {
      super(sel, count);
    }

    @Override
    public String getName() {
      return count == 1 ? S.get("anchorComponentAction") : S.get("anchorComponentsAction");
    }

    @Override
    protected void doIt(Project proj, Circuit circ, CircuitMutation xn) {
      sel.clear(xn);
    }

  }

  private static class Delete extends Action {
    private Selection sel;
    private CircuitTransaction xnReverse;
    private Circuit circuit;
    private CircuitMutation xn;

    Delete(Selection sel) {
      this.circuit = sel.getProject().getCurrentCircuit();
      this.sel = sel;
    }

    @Override
    public void doIt(Project proj) {
      xn = new CircuitMutation(circuit);
      sel.deleteAllHelper(xn);
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public String getName() {
      return S.get("deleteSelectionAction");
    }

    @Override
    public void redo(Project proj) {
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
    }
  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */
  private static class Drop extends SelectionAnchoringAction {
    // Drop removes things from the selection.

    private Component[] drops;

    Drop(Selection sel, Collection<Component> toDrop, int numDrops) {
      super(sel, toDrop.size());
      this.drops = toDrop.toArray(new Component[toDrop.size()]);
    }

    @Override
    protected void doIt(Project proj, Circuit circ, CircuitMutation xn) {
      for (Component comp : drops)
        sel.remove(xn, comp);
    }

    @Override
    public String getName() {
      return count == 1 ? S.get("dropComponentAction") : S.get("dropComponentsAction");
    }

  }

  private static class Duplicate extends Action {
    private Selection sel;
    private CircuitTransaction xnReverse;
    private SelectionSave after;
    private Circuit circuit;
    private CircuitMutation xn;

    Duplicate(Selection sel) {
      this.circuit = sel.getProject().getCurrentCircuit();
      this.sel = sel;
    }

    @Override
    public void doIt(Project proj) {
      xn = new CircuitMutation(circuit);
      sel.duplicateHelper(xn);
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
      after = SelectionSave.create(sel);
    }

    @Override
    public String getName() {
      return S.get("duplicateSelectionAction");
    }

    @Override
    public void redo(Project proj) {
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
    }
  }

  private static HashSet<Circuit> getDependencies(Circuit circ, Collection<Circuit> newCircs) {
    LinkedList<Circuit> q = new LinkedList<>();
    HashSet<Circuit> downstream = new HashSet<>();
    q.add(circ);
    while (!q.isEmpty()) {
      Circuit c = q.remove();
      downstream.add(c);
      if (!newCircs.contains(c))
        continue; // reached the boundary between new and old circuits
      for (Component comp : circ.getNonWires()) {
        if (comp.getFactory() instanceof SubcircuitFactory) {
          SubcircuitFactory factory = (SubcircuitFactory) comp.getFactory();
          Circuit subCirc = factory.getSubcircuit();
          if (subCirc == circ)
            return null; // circular
          if (q.contains(subCirc) || downstream.contains(subCirc))
            continue; // already visited
          q.add(subCirc);
        }
      }
    }
    return downstream;
  }

  static Location pasteLocationFromMouse(Project proj) {
    if (proj == null || proj.getFrame() == null)
      return null;
    Canvas canvas = proj.getFrame().getCanvas();
    if (canvas == null)
      return null;
    Point pt = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(pt, canvas);
    double zoom = canvas.getZoomFactor();
    Rectangle r = canvas.getViewableRect();
    int x = Math.max(r.x+20, Math.min(r.x+r.width-20, (int)(pt.getX() / zoom)));
    int y = Math.max(r.y+20, Math.min(r.y+r.height-20, (int)(pt.getY() / zoom)));
    return Location.create(Canvas.snapXToGrid(x), Canvas.snapYToGrid(y));
  }

  private static class PasteText extends Action {
    private String clip;
    private Selection sel;
    private Circuit circuit;
    private TextTool tool;
    private Location loc;
    private CircuitTransaction xnReverse;
    private CircuitMutation xn;

    PasteText(Project proj, String clip, Selection sel) {
      this.clip = clip;
      this.sel = sel;
      this.circuit = proj.getCurrentCircuit();
      this.tool = TextTool.getToolFromProject(proj);
      this.loc = pasteLocationFromMouse(proj);
    }

    boolean valid(Project proj) {
      return clip != null && clip.length() != 0 && tool != null && loc != null;
    }

    @Override
    public void doIt(Project proj) {
      xn = new CircuitMutation(circuit);
      sel.pasteHelper(xn, Collections.singletonList(tool.create(loc, clip)));
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public void redo(Project proj) {
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public String getName() {
      return S.get("pasteTextAction");
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
    }
  }

  private static class PasteImage extends Action {
    private java.awt.Image clip;
    private Selection sel;
    private Circuit circuit;
    private AddTool tool;
    private Location loc;
    private CircuitTransaction xnReverse;
    private CircuitMutation xn;

    PasteImage(Project proj, java.awt.Image clip, Selection sel) {
      this.clip = clip;
      this.sel = sel;
      this.circuit = proj.getCurrentCircuit();
      this.tool = Image.getToolFromProject(proj);
      this.loc = pasteLocationFromMouse(proj);
    }

    boolean valid(Project proj) {
      return clip != null && tool != null && loc != null;
    }

    @Override
    public void doIt(Project proj) {
      xn = new CircuitMutation(circuit);
      sel.pasteHelper(xn, Collections.singletonList(Image.create(tool, loc, clip)));
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public void redo(Project proj) {
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public String getName() {
      return S.get("pasteImageAction");
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
    }
  }

  private static class PasteComponents extends Action {
    private Selection sel;
    private LayoutClipboard.Clip<Collection<Component>> clip;
    private CircuitTransaction xnReverse, cxnReverse;
    private SelectionSave after;
    private boolean validated;
    private CircuitMutation xn;
    private Circuit circuit;

    PasteComponents(Selection sel, LayoutClipboard.Clip<Collection<Component>> clip) {
      circuit = sel.getProject().getCurrentCircuit();
      this.sel = sel;
      this.clip = clip;
    }

    boolean circular(Project proj)  {
      Circuit circuit = proj.getCurrentCircuit();

      // check if adding these components would cause a circular dependency
      Dependencies dependencies = proj.getDependencies();
      for (Component c : clip.selection) {
        ComponentFactory factory = c.getFactory();
        if (!(factory instanceof SubcircuitFactory))
          continue;
        Circuit subCirc = ((SubcircuitFactory)factory).getSubcircuit();
        if (clip.circuits.contains(subCirc))
          continue; // no dependency info for this subcircuit
        if (!dependencies.canAdd(circuit, subCirc))
          return true;
      }

      // new circuits are not represented in the existing dependencies, so we
      // need to (a) make sure there are no circularities within the new
      // circuits and (b) check whether we can add an edge from the current
      // circuit to every dependency of the new circuits.
      for (Circuit c : clip.circuits) {
        HashSet<Circuit> downstream = getDependencies(c, clip.circuits);
        if (downstream == null) // failure indicates circularity
          return true;
        for (Circuit subCirc : downstream) {
          if (clip.circuits.contains(subCirc))
            continue; // not on the boundary between new and old circuits
          if (!dependencies.canAdd(circuit, subCirc))
            return true;
        }
      }
      return false;
    }

    boolean valid(Project proj) {
      if (clip == null)
        return false;
      if (validated)
        return true;
      if (circular(proj)) {
        proj.getFrame().getCanvas().setErrorMessage(
            com.cburch.logisim.tools.Strings.S.getter("circularError"));
        clip = null;
        return false;
      }
      validated = true;
      return true;
    }

    @Override
    public void doIt(Project proj) {
      LogisimFile file = proj.getLogisimFile();
      for (Library lib : clip.libraries)
        file.addLibrary(lib);
      for (VhdlContent vhdl : clip.vhdl)
        file.addVhdlContent(vhdl);
      if (clip.circuitTransaction != null) {
        for (Circuit c : clip.circuits)
          file.addCircuit(c);
        CircuitTransactionResult result = clip.circuitTransaction.execute();
        cxnReverse = result.getReverseTransaction();
      } else {
        cxnReverse = null;
      }

      xn = new CircuitMutation(circuit);
      sel.pasteHelper(xn, clip.selection);
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
      after = SelectionSave.create(sel);
    }

    @Override
    public void redo(Project proj) {
      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public String getName() {
      return S.get("pasteComponentsAction");
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
      if (cxnReverse != null)
        cxnReverse.execute();
      LogisimFile file = proj.getLogisimFile();
      for (Circuit circ : clip.circuits)
        file.removeCircuit(circ);
      for (VhdlContent vhdl : clip.vhdl)
        file.removeVhdl(vhdl);
      for (Library lib : clip.libraries)
        file.removeLibrary(lib);
    }
  }

  private static String confirmName(Project proj, String suggested, boolean is_vhdl, LayoutClipboard.Clip<?> clip) {
    String newName = suggested;
    LogisimFile file = proj.getLogisimFile();
    if (newName == null || newName.equals("") || file.getTool(newName) != null) {
      // suggested name is bad, generate better suggestion, confirm with user
      int i = 1;
      do {
        if (newName == null || newName.equals(""))
          newName = "circuit";
        else
          newName = suggested + (i++);
      } while (file.getTool(newName) != null);
      if (is_vhdl)
        newName = ProjectCircuitActions.promptForVhdlName(proj.getFrame(), file, newName);
      else
        newName = ProjectCircuitActions.promptForCircuitName(proj.getFrame(), file, newName);
      if (newName == null)
        return null; // user cancelled or picked bad name
    }
    for (Circuit c : clip.circuits) {
      if (newName.equals(c.getName())) {
        JOptionPane.showMessageDialog(proj.getFrame(),
            com.cburch.logisim.gui.menu.Strings.S.get("circuitNameDuplicateError"),
            com.cburch.logisim.gui.menu.Strings.S.get("circuitNameDialogTitle"),
            JOptionPane.ERROR_MESSAGE);
        return null;
      }
    }
    for (VhdlContent vhdl : clip.vhdl) {
      if (newName.equals(vhdl.getName())) {
        JOptionPane.showMessageDialog(proj.getFrame(),
            com.cburch.logisim.gui.menu.Strings.S.get("circuitNameDuplicateError"),
            com.cburch.logisim.gui.menu.Strings.S.get("circuitNameDialogTitle"),
            JOptionPane.ERROR_MESSAGE);
        return null;
      }
    }
    return newName;
  }

  private static class PasteComponentsAsCircuit extends Action {
    private LayoutClipboard.Clip<Collection<Component>> clip;
    private CircuitTransaction xnReverse, cxnReverse;
    private String newName = null;
    private Circuit circuit;

    PasteComponentsAsCircuit(LayoutClipboard.Clip<Collection<Component>> clip) {
      this.clip = clip;
    }

    boolean valid(Project proj) {
      if (clip == null)
        return false;
      else if (newName != null)
        return true;
      newName = confirmName(proj, null, false, clip);
      if (newName == null) {
        clip = null;
        return false;
      }

      // New components go into a freshly-named circuit, which no other circuit
      // can depend on. So we only need to make sure there are no circularities
      // within the other new circuits.
      for (Circuit c : clip.circuits) {
        HashSet<Circuit> downstream = getDependencies(c, clip.circuits);
        if (downstream == null) { // failure indicates circularity
          proj.getFrame().getCanvas().setErrorMessage(
              com.cburch.logisim.tools.Strings.S.getter("circularError"));
          clip = null;
          return false;
        }
      }
      return true;
    }

    @Override
    public void doIt(Project proj) {
      LogisimFile file = proj.getLogisimFile();

      for (Library lib : clip.libraries)
        file.addLibrary(lib);
      for (VhdlContent vhdl : clip.vhdl)
        file.addVhdlContent(vhdl);
      if (clip.circuitTransaction != null) {
        for (Circuit c : clip.circuits)
          file.addCircuit(c);
        CircuitTransactionResult result = clip.circuitTransaction.execute();
        cxnReverse = result.getReverseTransaction();
      } else {
        cxnReverse = null;
      }

      circuit = new Circuit(newName, file);
      file.addCircuit(circuit);
      proj.setCurrentCircuit(circuit);
      CircuitMutation xn = new CircuitMutation(circuit);
      for (Component c : clip.selection)
        xn.add(c);

      CircuitTransactionResult result = xn.execute();
      xnReverse = result.getReverseTransaction();
    }

    @Override
    public String getName() {
      return S.get("pasteAsCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      xnReverse.execute();
      LogisimFile file = proj.getLogisimFile();
      file.removeCircuit(circuit);
      if (cxnReverse != null)
        cxnReverse.execute();
      for (Circuit circ : clip.circuits)
        file.removeCircuit(circ);
      for (VhdlContent vhdl : clip.vhdl)
        file.removeVhdl(vhdl);
      for (Library lib : clip.libraries)
        file.removeLibrary(lib);
    }
  }

  private static class AddCircuit extends Action {
    private LayoutClipboard.Clip<Circuit> clip;
    private CircuitTransaction cxnReverse;
    private String newName;
    private Circuit prevCircuit;
    private HdlModel prevHdl;
    private int idx;
    private String title;

    AddCircuit(Project proj, LayoutClipboard.Clip<Circuit> clip, int idx, int reason) {
      this.clip = clip;
      this.idx = idx;
      this.title = reason == DUPLICATE ? S.get("duplicateCircuitAction")
          : reason == PASTE ? S.get("pasteCircuitAction")
          : S.get("dragDropCircuitAction");
      prevCircuit = proj.getCurrentCircuit();
      prevHdl = proj.getCurrentHdl();
    }

    boolean valid(Project proj) {
      if (clip == null || idx > proj.getLogisimFile().getTools().size())
        return false;
      else if (newName != null)
        return true;
      String oldName = clip.selection.getName();
      newName = confirmName(proj, oldName, false, clip);
      if (newName == null) {
        clip = null;
        return false;
      }
      if (!newName.equals(oldName))
        clip.selection.setCircuitName(newName);

      // New circuit is freshly-named, and no other circuit can depend on it. So
      // we only need to make sure there are no circularities within the other
      // new circuits.
      for (Circuit c : clip.circuits) {
        HashSet<Circuit> downstream = getDependencies(c, clip.circuits);
        if (downstream == null) { // failure indicates circularity
          proj.getFrame().getCanvas().setErrorMessage(
              com.cburch.logisim.tools.Strings.S.getter("circularError"));
          clip = null;
          return false;
        }
      }
      return true;
    }

    @Override
    public void doIt(Project proj) {
      LogisimFile file = proj.getLogisimFile();
      int pos = idx;
      if (pos < 0)
        pos = file.getTools().size();
      file.addCircuit(clip.selection, pos++);
      proj.setCurrentCircuit(clip.selection);

      for (Library lib : clip.libraries)
        file.addLibrary(lib);
      for (VhdlContent vhdl : clip.vhdl)
        file.addVhdlContent(vhdl, pos++);
      for (Circuit c : clip.circuits)
        file.addCircuit(c, pos++);

      CircuitTransactionResult result = clip.circuitTransaction.execute();
      cxnReverse = result.getReverseTransaction();
    }

    @Override
    public String getName() {
      return title;
    }

    @Override
    public void undo(Project proj) {
      cxnReverse.execute();
      LogisimFile file = proj.getLogisimFile();
      for (Circuit circ : clip.circuits)
        file.removeCircuit(circ);
      for (VhdlContent vhdl : clip.vhdl)
        file.removeVhdl(vhdl);
      for (Library lib : clip.libraries)
        file.removeLibrary(lib);
      file.removeCircuit(clip.selection);
      if (prevHdl != null)
        proj.setCurrentHdlModel(prevHdl);
      else if (prevCircuit != null)
        proj.setCurrentCircuit(prevCircuit);
    }
  }

  private static class AddVhdl extends Action {
    private LayoutClipboard.Clip<VhdlContent> clip;
    private String newName;
    private Circuit prevCircuit;
    private HdlModel prevHdl;
    private int idx;
    private String title;

    AddVhdl(Project proj, LayoutClipboard.Clip<VhdlContent> clip, int idx, int reason) {
      this.clip = clip;
      this.idx = idx;
      this.title = reason == DUPLICATE ? S.get("duplicateVhdlAction")
          : reason == PASTE ? S.get("pasteVhdlAction")
          : S.get("dragDropVhdlAction");
      prevCircuit = proj.getCurrentCircuit();
      prevHdl = proj.getCurrentHdl();
    }

    boolean valid(Project proj) {
      if (clip == null || idx > proj.getLogisimFile().getTools().size())
        return false;
      else if (newName != null)
        return true;
      String oldName = clip.selection.getName();
      newName = confirmName(proj, oldName, false, clip);
      if (newName == null) {
        clip = null;
        return false;
      }
      if (!newName.equals(oldName))
        clip.selection.setName(newName);

      // todo: no dependency tracking for vhdl yet, so no other circuits.
      return true;
    }

    @Override
    public void doIt(Project proj) {
      LogisimFile file = proj.getLogisimFile();
      int pos = idx;
      if (pos < 0)
        pos = file.getTools().size();
      file.addVhdlContent(clip.selection, pos);
      proj.setCurrentHdlModel(clip.selection);
    }

    @Override
    public String getName() {
      return title;
    }

    @Override
    public void undo(Project proj) {
      LogisimFile file = proj.getLogisimFile();
      file.removeVhdl(clip.selection);
      if (prevHdl != null)
        proj.setCurrentHdlModel(prevHdl);
      else if (prevCircuit != null)
        proj.setCurrentCircuit(prevCircuit);
    }
  }

  private static class Translate extends SelectionAnchoringAction {
    private int dx, dy;
    private ReplacementMap replacements;

    Translate(Selection sel, int dx, int dy, ReplacementMap replacements) {
      super(sel, 0);
      this.dx = dx;
      this.dy = dy;
      this.replacements = replacements;
    }

    @Override
    protected void doIt(Project proj, Circuit circ, CircuitMutation xn) {
      sel.translateHelper(xn, dx, dy);
      if (replacements != null)
        xn.replace(replacements);
    }

    @Override
    public String getName() {
      return S.get("moveSelectionAction");
    }

  }

  /**
   * Code taken from Cornell's version of Logisim:
   * http://www.cs.cornell.edu/courses/cs3410/2015sp/
   */

  // anchor all floating elements, clearing selection
  public static Action clear(Selection sel) {
    int numAnchor = sel.getFloatingComponents().size();
    if (numAnchor == 0) {
      sel.clear(null);
      return null;
    } else {
      return new Deselect(sel, numAnchor);
    }
  }

  public static Action delete(Selection sel) {
    return new Delete(sel);
  }

  public static void doCopy(Project proj, Selection sel) { // Note: copy is not an Action
    doCopy(proj, sel.getComponents());
  }

  public static void doCopy(Project proj, Collection<Component> comps) { // Note: copy is not an Action
    // special case: selection is a single Text, or single Image
    if (comps.size() == 0) {
      System.out.println("nothing to copy");
    } else if (comps.size() > 1) {
      LayoutClipboard.forComponents.set(proj, comps);
    } else {
      Component comp = comps.iterator().next();
      if (comp.getFactory() instanceof Text) {
        String text = TextTool.getText(comp);
        ExternalClipboard.forString.set(proj, comp, text);
      } else if (comp.getFactory() instanceof Image) {
        java.awt.Image image = Image.getImage(comp);
        ExternalClipboard.forImage.set(proj, comp, image);
      } else {
        LayoutClipboard.forComponents.set(proj, comps);
      }
    }
  }

  public static void doCopy(Project proj, Circuit sel) { // Note: copy is not an Action
    LayoutClipboard.forCircuit.set(proj, sel);
  }

  public static void doCopy(Project proj, VhdlContent sel) { // Note: copy is not an Action
    LayoutClipboard.forVhdl.set(proj, sel);
  }

  public static void doCopy(Project proj, Library sel) { // Note: copy is not an Action
    LayoutClipboard.forLibrary.set(proj, sel);
  }

  public static void doCut(Project proj, Selection sel) {
    if (!sel.isEmpty()) {
      doCopy(proj, sel);
      proj.doAction(new Delete(sel));
    }
  }

  public static void doCut(Project proj, Circuit sel) {
    LayoutClipboard.forCircuit.set(proj, sel);
    ProjectCircuitActions.doRemoveCircuit(proj, sel);
  }

  public static void doCut(Project proj, VhdlContent sel) {
    LayoutClipboard.forVhdl.set(proj, sel);
    ProjectCircuitActions.doRemoveVhdl(proj, sel);
  }

  public static void doCut(Project proj, Library sel) {
    LayoutClipboard.forLibrary.set(proj, sel);
    ProjectLibraryActions.doUnloadLibrary(proj, sel);
  }

  // clears the selection, anchoring all floating elements in selection
  public static Action drop(Selection sel, Collection<Component> comps) {
    HashSet<Component> floating = new HashSet<Component>(
        sel.getFloatingComponents());
    HashSet<Component> anchored = new HashSet<Component>(
        sel.getAnchoredComponents());
    ArrayList<Component> toDrop = new ArrayList<Component>();
    ArrayList<Component> toIgnore = new ArrayList<Component>();
    for (Component comp : comps) {
      if (floating.contains(comp)) {
        toDrop.add(comp);
      } else if (anchored.contains(comp)) {
        toDrop.add(comp);
        toIgnore.add(comp);
      }
    }
    if (toDrop.size() == toIgnore.size()) {
      for (Component comp : toIgnore) {
        sel.remove(null, comp);
      }
      return null;
    } else {
      int numDrop = toDrop.size() - toIgnore.size();
      return new Drop(sel, toDrop, numDrop);
    }
  }

  public static Action dropAll(Selection sel) {
    return drop(sel, sel.getComponents());
  }

  public static Action duplicate(Selection sel) {
    return new Duplicate(sel);
  }

  public static boolean doPaste(Project proj, Selection sel) {
    return doPasteComponents(proj, sel)
        || doPasteCircuit(proj)
        || doPasteVhdl(proj)
        || doPasteLibrary(proj);
  }

  public static boolean doPasteText(Project proj, Selection sel) {
    String clip = ExternalClipboard.forString.get(proj);
    if (clip == null)
      return false;
    PasteText act = new PasteText(proj, clip, sel);
    if (act.valid(proj)) {
      proj.doAction(act);
      return true;
    }
    return false;
  }

  public static boolean doPasteImage(Project proj, Selection sel) {
    java.awt.Image clip = ExternalClipboard.forImage.get(proj);
    if (clip == null)
      return false;
    PasteImage act = new PasteImage(proj, clip, sel);
    if (act.valid(proj)) {
      proj.doAction(act);
      return true;
    }
    return false;
  }

  public static boolean doPasteComponents(Project proj, Selection sel) {
    LayoutClipboard.Clip<Collection<Component>> clip = LayoutClipboard.forComponents.get(proj);
    if (clip != null) {
      PasteComponents act = new PasteComponents(sel, clip);
      if (act.valid(proj)) {
        proj.doAction(act);
        return true;
      }
    }
    return doPasteText(proj, sel) || doPasteImage(proj, sel);
  }

  public static boolean doPasteComponentsAsCircuit(Project proj) {
    LayoutClipboard.Clip<Collection<Component>> clip = LayoutClipboard.forComponents.get(proj);
    if (clip == null)
      return false;
    PasteComponentsAsCircuit act = new PasteComponentsAsCircuit(clip);
    if (act.valid(proj)) {
      proj.doAction(act);
      return true;
    }
    return false;
  }

  public static boolean doDuplicate(Project proj, Circuit c) {
    Transferable t = LayoutClipboard.forCircuit.encode(proj, c);
    return doDuplicate(proj, t);
  }

  public static boolean doDuplicate(Project proj, VhdlContent v) {
    Transferable t = LayoutClipboard.forVhdl.encode(proj, v);
    return doDuplicate(proj, t);
  }

  public static boolean doDuplicate(Project proj, Library lib) {
      Transferable t = LayoutClipboard.forLibrary.encode(proj, lib);
      return doDuplicate(proj, t);
  }

  public static boolean doDuplicate(Project proj, Transferable t) {
    return doAdd(proj, t, -1, DUPLICATE);
  }

  public static boolean doPaste(Project proj, Transferable t) {
    return doAdd(proj, t, -1, PASTE);
  }

  public static boolean doDrop(Project proj, Transferable t, int newIdx) {
    return doAdd(proj, t, newIdx, DROP);
  }

  private static final int DUPLICATE = 1;
  private static final int PASTE = 2;
  private static final int DROP = 3;

  private static boolean doAdd(Project proj, Transferable t, int newIdx, int reason) {
    if (t.isDataFlavorSupported(LayoutClipboard.forCircuit.dnd.dataFlavor)) {
      // paste, drag JVM-foreign, or drag-copy JVM-local for Circuit
      LayoutClipboard.Clip<Circuit> clip = LayoutClipboard.forCircuit.get(proj, t);
      if (clip != null) 
        return doAddCircuit(proj, clip, newIdx, reason);
    } else if (t.isDataFlavorSupported(LayoutClipboard.forVhdl.dnd.dataFlavor)) {
      // paste, drag JVM-foreign, or drag-copy JVM-local for Vhdl
      LayoutClipboard.Clip<VhdlContent> clip = LayoutClipboard.forVhdl.get(proj, t);
      if (clip != null) 
        return doAddVhdl(proj, clip, newIdx, reason);
    } else if (t.isDataFlavorSupported(LayoutClipboard.forLibrary.dnd.dataFlavor)) {
      // paste, drag JVM-foreign, or drag-copy JVM-local for Library
      LayoutClipboard.Clip<Library> clip = LayoutClipboard.forLibrary.get(proj, t);
      if (clip != null) 
        return doAddLibrary(proj, clip, newIdx, reason);
    }
    return false;
  }

  public static boolean doPasteCircuit(Project proj) {
    LayoutClipboard.Clip<Circuit> clip = LayoutClipboard.forCircuit.get(proj);
    return clip != null && doAddCircuit(proj, clip, -1, PASTE);
  }
  
  private static boolean doAddCircuit(Project proj, LayoutClipboard.Clip<Circuit> clip, int idx, int reason) {
    AddCircuit act = new AddCircuit(proj, clip, idx, reason);
    if (act.valid(proj)) {
      proj.doAction(act);
      return true;
    }
    return false;
  }

  public static boolean doPasteVhdl(Project proj) {
    LayoutClipboard.Clip<VhdlContent> clip = LayoutClipboard.forVhdl.get(proj);
    return clip != null && doAddVhdl(proj, clip, -1, PASTE);
  }

  private static boolean doAddVhdl(Project proj, LayoutClipboard.Clip<VhdlContent> clip, int idx, int reason) {
    AddVhdl act = new AddVhdl(proj, clip, idx, reason);
    if (act.valid(proj)) {
      proj.doAction(act);
      return true;
    }
    return false;
  }

  public static boolean doPasteLibrary(Project proj) {
    LayoutClipboard.Clip<Library> clip = LayoutClipboard.forLibrary.get(proj);
    return clip != null && doAddLibrary(proj, clip, -1, PASTE);
  }

  public static boolean doAddLibrary(Project proj, LayoutClipboard.Clip<Library> clip, int idx, int reason) {
    // todo: more error checking for naming clashes / duplicate libraries?
    if (proj.getLogisimFile().getLibraries().contains(clip.selection))
      return false;
    Action act = LogisimFileActions.loadLibrary(clip.selection, idx);
    proj.doAction(act);
    return true;
  }

  public static Action translate(Selection sel, int dx, int dy, ReplacementMap repl) {
    return new Translate(sel, dx, dy, repl);
  }

  private SelectionActions() {
  }
}
