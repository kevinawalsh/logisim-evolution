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

package com.cburch.logisim.std.gates;

import java.util.ArrayList;

import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.comp.ComponentFactory;

/**
 * This represents the actual gate selection used corresponding to an
 * expression, without any correspondence to how they would be laid down in a
 * circuit. This intermediate representation permits easy manipulation of an
 * expression's translation.
 */
abstract class CircuitDetermination {
  private static class Determine
    implements Expression.Visitor<CircuitDetermination> {
    private Gate binary(CircuitDetermination aret,
        CircuitDetermination bret, ComponentFactory factory) {
      if (aret instanceof Gate) {
        Gate a = (Gate) aret;
        if (a.factory == factory) {
          if (bret instanceof Gate) {
            Gate b = (Gate) bret;
            if (b.factory == factory) {
              a.inputs.addAll(b.inputs);
              return a;
            }
          }
          a.inputs.add(bret);
          return a;
        }
      }

      if (bret instanceof Gate) {
        Gate b = (Gate) bret;
        if (b.factory == factory) {
          b.inputs.add(aret);
          return b;
        }
      }

      Gate ret = new Gate(factory);
      ret.inputs.add(aret);
      ret.inputs.add(bret);
      return ret;
    }

    @Override
    public CircuitDetermination visitAnd(Expression e, Expression a, Expression b) {
      return binary(a.visit(this), b.visit(this), AndGate.FACTORY);
    }

    @Override
    public CircuitDetermination visitConstant(Expression e, int value) {
      return new Value(value);
    }

    @Override
    public CircuitDetermination visitNot(Expression e, Expression aBase) {
      CircuitDetermination aret = aBase.visit(this);
      if (aret instanceof Gate) {
        Gate a = (Gate) aret;
        if (a.factory == AndGate.FACTORY) {
          a.factory = NandGate.FACTORY;
          return a;
        } else if (a.factory == OrGate.FACTORY) {
          a.factory = NorGate.FACTORY;
          return a;
        } else if (a.factory == XorGate.FACTORY) {
          a.factory = XnorGate.FACTORY;
          return a;
        }
      }

      Gate ret = new Gate(NotGate.FACTORY);
      ret.inputs.add(aret);
      return ret;
    }

    @Override
    public CircuitDetermination visitOr(Expression e, Expression a, Expression b) {
      return binary(a.visit(this), b.visit(this), OrGate.FACTORY);
    }

    @Override
    public CircuitDetermination visitVariable(Expression e, String name) {
      return new Input(name);
    }

    @Override
    public CircuitDetermination visitXor(Expression e, Expression a, Expression b) {
      return binary(a.visit(this), b.visit(this), XorGate.FACTORY);
    }

    @Override
    public CircuitDetermination visitXnor(Expression e, Expression a, Expression b) {
      return binary(a.visit(this), b.visit(this), XnorGate.FACTORY);
    }

    @Override
    public CircuitDetermination visitEq(Expression e, Expression a, Expression b) {
      return binary(a.visit(this), b.visit(this), XnorGate.FACTORY);
    }
  }

  static class Gate extends CircuitDetermination {
    private ComponentFactory factory;
    private ArrayList<CircuitDetermination> inputs = new ArrayList<CircuitDetermination>();

    private Gate(ComponentFactory factory) {
      this.factory = factory;
    }

    @Override
    void convertToNands() {
      // first recurse to clean up any children
      for (CircuitDetermination sub : inputs) {
        sub.convertToNands();
      }

      if (factory == NotGate.FACTORY) {
        inputs.add(inputs.get(0));
      } else if (factory == AndGate.FACTORY) {
        notOutput();
      } else if (factory == OrGate.FACTORY) {
        notAllInputs();
      } else if (factory == NorGate.FACTORY) {
        notAllInputs(); // the order of these two lines is significant
        notOutput();
      } else if (factory == NandGate.FACTORY) {
        ;
      } else if (factory == XorGate.FACTORY ||
          factory == XnorGate.FACTORY) {
        if (inputs.size() == 1) {
          // XOR with 1 input is a no-op. Convert to double negation.
          // XNOR with 1 input is same as NOT.
          inputs.add(inputs.get(0));
          if (factory == XorGate.FACTORY)
            notOutput();
        } else {
          // Note: We use "odd parity" (or "even parity" for XNOR) behavior here,
          // rather than "one and only one" behavior.
          // For n>3, we make 2 subcircuits, one handling the bottom half of the
          // inputs, the other handling the top half.
          // For n==3, we create only one subcircuit, to handle the bottom two
          // inputs, and use the first input as-is.
          if (inputs.size() > 2) {
            int n = inputs.size();
            int half = n / 2;
            Gate sub = new Gate(XorGate.FACTORY);
            sub.inputs = new ArrayList<>(this.inputs.subList(half, n));
            sub.convertToNands();
            inputs.subList(half, n).clear();
            inputs.add(sub);
          }
          if (inputs.size() > 2) {
            int n = inputs.size();
            Gate sub = new Gate(XorGate.FACTORY);
            sub.inputs = this.inputs;
            this.inputs = new ArrayList<>();
            this.inputs.add(sub);
            this.inputs.add(sub.inputs.remove(n-1));
            sub.convertToNands();
          }
          // convert: xor(a, b)
          // into: nand(x, y), x = nand(a, z), y = nand(z, b), z = nand(a, b)
          CircuitDetermination a = inputs.get(0);
          CircuitDetermination b = inputs.get(1);
          Gate z = new Gate(NandGate.FACTORY);
          z.inputs = this.inputs;
          Gate y = new Gate(NandGate.FACTORY);
          y.inputs = new ArrayList<>();
          y.inputs.add(z);
          y.inputs.add(b);
          Gate x = new Gate(NandGate.FACTORY);
          x.inputs = new ArrayList<>();
          x.inputs.add(a);
          x.inputs.add(z);
          this.inputs = new ArrayList<>();
          this.inputs.add(x);
          this.inputs.add(y);
          if (factory == XnorGate.FACTORY)
            notOutput();
        }
      } else {
        throw new IllegalArgumentException("Cannot handle "
            + factory.getDisplayName());
      }
      factory = NandGate.FACTORY;
    }

    @Override
    void convertToTwoInputs() {
      if (inputs.size() <= 2) {
        for (CircuitDetermination a : inputs) {
          a.convertToTwoInputs();
        }
      } else {
        ComponentFactory subFactory;
        if (factory == NorGate.FACTORY)
          subFactory = OrGate.FACTORY;
        else if (factory == NandGate.FACTORY)
          subFactory = AndGate.FACTORY;
        else if (factory == XnorGate.FACTORY)
          subFactory = XorGate.FACTORY;
        else
          subFactory = factory;

        int split = (inputs.size() + 1) / 2;
        CircuitDetermination a = convertToTwoInputsSub(0, split,
            subFactory);
        CircuitDetermination b = convertToTwoInputsSub(split,
            inputs.size(), subFactory);
        inputs.clear();
        inputs.add(a);
        inputs.add(b);
      }
    }

    private CircuitDetermination convertToTwoInputsSub(int start, int stop,
        ComponentFactory subFactory) {
      if (stop - start == 1) {
        CircuitDetermination a = inputs.get(start);
        a.convertToTwoInputs();
        return a;
      } else {
        int split = (start + stop + 1) / 2;
        CircuitDetermination a = convertToTwoInputsSub(start, split,
            subFactory);
        CircuitDetermination b = convertToTwoInputsSub(split, stop,
            subFactory);
        Gate ret = new Gate(subFactory);
        ret.inputs.add(a);
        ret.inputs.add(b);
        return ret;
      }
    }

    ComponentFactory getFactory() {
      return factory;
    }

    ArrayList<CircuitDetermination> getInputs() {
      return inputs;
    }

    @Override
    boolean isNandNot() {
      return factory == NandGate.FACTORY && inputs.size() == 2
          && inputs.get(0) == inputs.get(1);
    }

    private void notAllInputs() {
      for (int i = 0; i < inputs.size(); i++) {
        CircuitDetermination old = inputs.get(i);
        if (old.isNandNot()) {
          inputs.set(i, ((Gate) old).inputs.get(0));
        } else {
          Gate now = new Gate(NandGate.FACTORY);
          now.inputs.add(old);
          now.inputs.add(old);
          inputs.set(i, now);
        }
      }
    }

    private void notOutput() {
      // change: this_nand(A, B)
      // into: this_nand(sub, sub), where sub = new_nand(A, B)
      Gate sub = new Gate(NandGate.FACTORY);
      sub.inputs = this.inputs;
      this.inputs = new ArrayList<CircuitDetermination>();
      inputs.add(sub);
      inputs.add(sub);
    }

    @Override
    void repair() {
      // check whether we need to split ourself up.
      int num = inputs.size();
      if (num > GateAttributes.MAX_INPUTS) {
        int newNum = (num + GateAttributes.MAX_INPUTS - 1)
            / GateAttributes.MAX_INPUTS;
        ArrayList<CircuitDetermination> oldInputs = inputs;
        inputs = new ArrayList<CircuitDetermination>();

        ComponentFactory subFactory = factory;
        if (subFactory == NandGate.FACTORY)
          subFactory = AndGate.FACTORY;
        else if (subFactory == NorGate.FACTORY)
          subFactory = OrGate.FACTORY;
        else if (subFactory == XnorGate.FACTORY)
          subFactory = XorGate.FACTORY;

        int per = num / newNum;
        int numExtra = num - per * newNum;
        int k = 0;
        for (int i = 0; i < newNum; i++) {
          Gate sub = new Gate(subFactory);
          int subCount = per + (i < numExtra ? 1 : 0);
          for (int j = 0; j < subCount; j++) {
            sub.inputs.add(oldInputs.get(k));
            k++;
          }
          inputs.add(sub);
        }
      }

      // repair large XOR/XNORs to odd/even parity gates
      if (inputs.size() > 2) {
        if (factory == XorGate.FACTORY) {
          factory = OddParityGate.FACTORY;
        } else if (factory == XnorGate.FACTORY) {
          factory = EvenParityGate.FACTORY;
        }
      }

      // finally, recurse to clean up any children
      for (CircuitDetermination sub : inputs) {
        sub.repair();
      }
    }
  }

  static class Input extends CircuitDetermination {
    private String name;

    private Input(String name) {
      this.name = name;
    }

    String getName() {
      return name;
    }
  }

  static class Value extends CircuitDetermination {
    private int value;

    private Value(int value) {
      this.value = value;
    }

    int getValue() {
      return value;
    }
  }

  static CircuitDetermination create(Expression expr) {
    if (expr == null)
      return null;
    return expr.visit(new Determine());
  }

  /**
   * Converts all gates to NANDs. Note that this will fail with an exception
   * if any XOR/XNOR gates are used.
   */
  void convertToNands() {
  }

  /** Ensures that all gates have only two inputs. */
  void convertToTwoInputs() {
  }

  /**
   * A utility method for determining whether this fits the pattern of a NAND
   * representing a NOT.
   */
  boolean isNandNot() {
    return false;
  }

  /**
   * Repairs two errors that may have cropped up in creating the circuit.
   * First, if there are gates with more inputs than their capacity, we repair
   * them. Second, any XOR/XNOR gates with more than 2 inputs should really be
   * Odd/Even Parity gates.
   */
  void repair() {
  }
}
