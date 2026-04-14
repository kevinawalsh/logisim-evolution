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

package com.cburch.logisim.file;
import static com.cburch.logisim.file.Strings.S;

import java.util.ArrayList;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class LogisimFileActions {
  private static class AddCircuit extends Action {
    private Circuit circuit;

    AddCircuit(Circuit circuit) {
      this.circuit = circuit;
    }

    @Override
    public void doIt(Project proj) {
      proj.getLogisimFile().addCircuit(circuit);
    }

    @Override
    public String getName() {
      return S.get("addCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().removeCircuit(circuit);
    }
  }

  private static class AddVhdl extends Action {
    private VhdlContent vhdl;

    AddVhdl(VhdlContent vhdl) {
      this.vhdl = vhdl;
    }

    @Override
    public void doIt(Project proj) {
      proj.getLogisimFile().addVhdlContent(vhdl);
    }

    @Override
    public String getName() {
      return S.get("addVhdlAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().removeVhdl(vhdl);
    }
  }

  private static class LoadLibraries extends Action {
    private Library[] libs;
    private int idx;

    LoadLibraries(Library[] libs, int idx) {
      this.libs = libs;
      this.idx = idx;
    }

    @Override
    public void doIt(Project proj) {
      int pos = idx >= 0 ? idx : proj.getLogisimFile().getLibraries().size();
      for (int i = 0; i < libs.length; i++) {
        proj.getLogisimFile().addLibrary(libs[i], pos++);
      }
    }

    @Override
    public String getName() {
      if (libs.length == 1) {
        return S.get("loadLibraryAction");
      } else {
        return S.get("loadLibrariesAction");
      }
    }

    @Override
    public void undo(Project proj) {
      for (int i = libs.length - 1; i >= 0; i--) {
        proj.getLogisimFile().removeLibrary(libs[i]);
      }
    }
  }

  private static class MoveTool extends Action {
    private AddTool tool;
    private int fromIndex;
    private int toIndex;

    MoveTool(AddTool tool, int toIndex) {
      this.tool = tool;
      this.toIndex = toIndex;
    }

    @Override
    public Action append(Action other) {
      MoveTool ret = new MoveTool(tool, ((MoveTool) other).toIndex);
      ret.fromIndex = this.fromIndex;
      return ret.fromIndex == ret.toIndex ? null : ret;
    }

    @Override
    public void doIt(Project proj) {
      fromIndex = proj.getLogisimFile().getTools().indexOf(tool);
      proj.getLogisimFile().moveTool(tool, toIndex);
    }

    @Override
    public String getName() {
      return S.get("moveToolAction");
    }

    @Override
    public boolean shouldAppendTo(Action other) {
      return other instanceof MoveTool
          && ((MoveTool) other).tool == this.tool;
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().moveTool(tool, fromIndex);
    }
  }

  private static class MoveLibrary extends Action {
    private Library lib;
    private int fromIndex;
    private int toIndex;

    MoveLibrary(Library lib, int toIndex) {
      this.lib = lib;
      this.toIndex = toIndex;
    }

    @Override
    public Action append(Action other) {
      MoveLibrary ret = new MoveLibrary(lib,
          ((MoveLibrary) other).toIndex);
      ret.fromIndex = this.fromIndex;
      return ret.fromIndex == ret.toIndex ? null : ret;
    }

    @Override
    public void doIt(Project proj) {
      fromIndex = proj.getLogisimFile().getLibraries().indexOf(lib);
      proj.getLogisimFile().moveLibrary(lib, toIndex);
    }

    @Override
    public String getName() {
      return S.get("moveLibraryAction");
    }

    @Override
    public boolean shouldAppendTo(Action other) {
      return other instanceof MoveLibrary
          && ((MoveLibrary) other).lib == this.lib;
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().moveLibrary(lib, fromIndex);
    }
  }

  private static class RemoveCircuit extends Action {
    private Circuit circuit;
    private int index;

    RemoveCircuit(Circuit circuit) {
      this.circuit = circuit;
    }

    @Override
    public void doIt(Project proj) {
      index = proj.getLogisimFile().removeCircuit(circuit);
    }

    @Override
    public String getName() {
      return S.get("removeCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().addCircuit(circuit, index);
    }
  }

  private static class RemoveVhdl extends Action {
    private VhdlContent vhdl;
    private int index;

    RemoveVhdl(VhdlContent vhdl) {
      this.vhdl = vhdl;
    }

    @Override
    public void doIt(Project proj) {
      index = proj.getLogisimFile().indexOfVhdl(vhdl);
      proj.getLogisimFile().removeVhdl(vhdl);
    }

    @Override
    public String getName() {
      return S.get("removeVhdlAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().addVhdlContent(vhdl, index);
    }
  }

  private static class RevertAttributeValue {
    private AttributeSet attrs;
    private Attribute<Object> attr;
    private Object value;

    RevertAttributeValue(AttributeSet attrs, Attribute<Object> attr,
        Object value) {
      this.attrs = attrs;
      this.attr = attr;
      this.value = value;
    }
  }

  private static class RevertDefaults extends Action {
    private Options oldOpts;
    private ArrayList<Library> libraries = null;
    private ArrayList<RevertAttributeValue> attrValues = new ArrayList<>();

    RevertDefaults() { }

    private void copyToolAttributes(Library srcLib, Library dstLib) {
      for (Tool srcTool : srcLib.getTools()) {
        AttributeSet srcAttrs = srcTool.getAttributeSet();
        Tool dstTool = dstLib.getTool(srcTool.getName());
        if (srcAttrs != null && dstTool != null) {
          AttributeSet dstAttrs = dstTool.getAttributeSet();
          for (Attribute<?> attrBase : srcAttrs.getAttributes()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> attr = (Attribute<Object>) attrBase;
            Object srcValue = srcAttrs.getValue(attr);
            Object dstValue = dstAttrs.getValue(attr);
            if (!dstValue.equals(srcValue)) {
              dstAttrs.setAttr(attr, srcValue);
              attrValues.add(new RevertAttributeValue(dstAttrs,
                    attr, dstValue));
            }
          }
        }
      }
    }

    @Override
    public void doIt(Project proj) {
      LogisimFile src = ProjectActions.createNewFile(proj == null ? null : proj.getFrame());
      LogisimFile dst = proj.getLogisimFile();

      copyToolAttributes(src, dst);
      for (Library srcLib : src.getLibraries()) {
        Library dstLib = dst.getLibrary(srcLib.getName());
        if (dstLib == null) {
          try {
            String desc = LibraryManager.instance.getAbsoluteDescriptor(srcLib);
            dstLib = dst.getLoader().loadLibrary(desc);
            if (dstLib == null)
              continue;
            proj.getLogisimFile().addLibrary(dstLib);
            if (libraries == null)
              libraries = new ArrayList<Library>();
            libraries.add(dstLib);
          } catch (LoadCanceledByUser ex) {
            // todo: log
            continue;
          }
        }
        copyToolAttributes(srcLib, dstLib);
      }

      Options newOpts = proj.getOptions();
      oldOpts = new Options();
      oldOpts.copyFrom(newOpts, dst);
      newOpts.copyFrom(src.getOptions(), dst);
    }

    @Override
    public String getName() {
      return S.get("revertDefaultsAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getOptions().copyFrom(oldOpts, proj.getLogisimFile());

      for (RevertAttributeValue attrValue : attrValues) {
        attrValue.attrs.setAttr(attrValue.attr, attrValue.value);
      }

      if (libraries != null) {
        for (Library lib : libraries) {
          proj.getLogisimFile().removeLibrary(lib);
        }
      }
    }
  }

  private static class SetMainCircuit extends Action {
    private Circuit oldval;
    private Circuit newval;

    SetMainCircuit(Circuit circuit) {
      newval = circuit;
    }

    @Override
    public void doIt(Project proj) {
      oldval = proj.getLogisimFile().getMainCircuit();
      proj.getLogisimFile().setMainCircuit(newval);
    }

    @Override
    public String getName() {
      return S.get("setMainCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().setMainCircuit(oldval);
    }
  }

  private static class UnloadLibraries extends Action {
    private Library[] libs;
    private int[] index;

    UnloadLibraries(Library[] libs) {
      this.libs = libs;
      this.index = new int[libs.length];
    }

    @Override
    public void doIt(Project proj) {
      for (int i = libs.length - 1; i >= 0; i--) {
        index[i] = proj.getLogisimFile().removeLibrary(libs[i]);
      }
    }

    @Override
    public String getName() {
      if (libs.length == 1) {
        return S.get("unloadLibraryAction");
      } else {
        return S.get("unloadLibrariesAction");
      }
    }

    @Override
    public void undo(Project proj) {
      for (int i = 0; i < libs.length; i++) {
        proj.getLogisimFile().addLibrary(libs[i], index[i]);
      }
    }
  }

  public static Action addCircuit(Circuit circuit) {
    return new AddCircuit(circuit);
  }

  public static Action addVhdl(VhdlContent vhdl) {
    return new AddVhdl(vhdl);
  }

  public static Action loadLibraries(Library[] libs) {
    return new LoadLibraries(libs, -1);
  }

  public static Action loadLibrary(Library lib) {
    return new LoadLibraries(new Library[] { lib }, -1);
  }

  public static Action loadLibrary(Library lib, int idx) {
    return new LoadLibraries(new Library[] { lib }, idx);
  }

  public static Action moveTool(AddTool tool, int toIndex) {
    return new MoveTool(tool, toIndex);
  }

  public static Action moveLibrary(Library lib, int toIndex) {
    return new MoveLibrary(lib, toIndex);
  }

  public static Action removeCircuit(Circuit circuit) {
    return new RemoveCircuit(circuit);
  }

  public static Action removeVhdl(VhdlContent vhdl) {
    return new RemoveVhdl(vhdl);
  }

  public static Action revertDefaults() {
    return new RevertDefaults();
  }

  public static Action setMainCircuit(Circuit circuit) {
    return new SetMainCircuit(circuit);
  }

  public static Action unloadLibraries(Library[] libs) {
    return new UnloadLibraries(libs);
  }

  public static Action unloadLibrary(Library lib) {
    return new UnloadLibraries(new Library[] { lib });
  }

  private LogisimFileActions() {
  }
}
