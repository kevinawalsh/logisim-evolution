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

package com.cburch.logisim.circuit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.cburch.logisim.comp.Component;

// When a circuit change is finished (CircuitEvent.TRANSACTION_DONE), the result
// will contain a replacement map showing the new components added and/or
// removed, and detailing which new components are replacing which old
// components, and vice versa. This seems to support a many-to-many relation,
// though in practice it might be that for every edge a-->b, either a has
// multiple images, or b has multiple pre-images, but not both. Also, an item a
// can have no images (meaning it was just deleted outright), and an item b can
// have no pre-images (meaning it was added out of thin air).
public class ReplacementMap {

  private boolean frozen; // prevents further changes to mappings
  private HashMap<Component, HashSet<Component>> map;
  private HashMap<Component, HashSet<Component>> inverse;

  // map: oldComponent --> {new components that replace oldComponent}
  // inverse: newComponent --> {old components that newComponent replaced}

  // empty relation
  public ReplacementMap() {
    this(new HashMap<Component, HashSet<Component>>(),
        new HashMap<Component, HashSet<Component>>());
  }

  // one-to-one relation of a single old component for a single new one
  public ReplacementMap(Component oldComp, Component newComp) {
    this(new HashMap<Component, HashSet<Component>>(),
        new HashMap<Component, HashSet<Component>>());
    HashSet<Component> oldSet = new HashSet<Component>(3);
    oldSet.add(oldComp);
    HashSet<Component> newSet = new HashSet<Component>(3);
    newSet.add(newComp);
    map.put(oldComp, newSet);
    inverse.put(newComp, oldSet);
  }

  // private constructor with pre-built maps
  private ReplacementMap(HashMap<Component, HashSet<Component>> map,
      HashMap<Component, HashSet<Component>> inverse) {
    this.map = map;
    this.inverse = inverse;
  }

  // makes relation for a new component, out of thin air, replacing nothing
  public void add(Component comp) {
    if (frozen)
      throw new IllegalStateException("cannot change map after frozen");
    inverse.put(comp, new HashSet<Component>(3));
  }

  // compose (math-style) two relations: a-->b  b-->c  becomes a-->c
  void append(ReplacementMap next) {
    for (Map.Entry<Component, HashSet<Component>> e : next.map.entrySet()) {
      Component b = e.getKey();
      HashSet<Component> cs = e.getValue(); // what b is replaced by
      HashSet<Component> as = this.inverse.remove(b); // what was replaced to get b
      if (as == null) { // b pre-existed replacements so
        as = new HashSet<Component>(3); // we say it replaces itself.
        as.add(b);
      }

      for (Component a : as) {
        HashSet<Component> aDst = this.map.get(a);
        if (aDst == null) { // should happen when b pre-existed only
          aDst = new HashSet<Component>(cs.size());
          this.map.put(a, aDst);
        }
        aDst.remove(b);
        aDst.addAll(cs);
      }

      for (Component c : cs) {
        HashSet<Component> cSrc = this.inverse.get(c); // should always be null
        if (cSrc == null) {
          cSrc = new HashSet<Component>(as.size());
          this.inverse.put(c, cSrc);
        }
        cSrc.addAll(as);
      }
    }

    for (Map.Entry<Component, HashSet<Component>> e : next.inverse.entrySet()) {
      Component c = e.getKey();
      if (!inverse.containsKey(c)) {
        HashSet<Component> bs = e.getValue();
        if (!bs.isEmpty()) {
          System.err.println("Internal error: component replaced but not represented");
          Thread.dumpStack();
        }
        inverse.put(c, new HashSet<Component>(3));
      }
    }
  }

  void freeze() {
    frozen = true;
  }

  // range of relation: _ --> {additions}
  public Collection<? extends Component> getAdditions() {
    return inverse.keySet();
  }

  // apply relation: a --> {components replacing a}
  public Collection<Component> getReplacementsFor(Component a) {
    return map.get(a);
  }

  // preimage of relation: {components replaced by b} <-- b
  public Collection<Component> getReplacedBy(Component b) {
    return inverse.get(b);
  }

  // inverted relation
  ReplacementMap getInverseMap() {
    return new ReplacementMap(inverse, map);
  }

  // domain of relation: {removals} --> _
  public Collection<? extends Component> getRemovals() {
    return map.keySet();
  }

  // public Collection<Component> getReplacedComponents() {
  //   return map.keySet();
  // }

  public boolean isEmpty() {
    return map.isEmpty() && inverse.isEmpty();
  }

  public void print(PrintStream out) {
    boolean found = false;
    for (Component a : getRemovals()) {
      if (!found)
        out.println("  removals:");
      found = true;
      out.println("    " + a.toString());
      for (Component b : map.get(a))
        out.println("     `--> " + b.toString());
    }
    if (!found)
      out.println("  removals: none");

    found = false;
    for (Component b : getAdditions()) {
      if (!found)
        out.println("  additions:");
      found = true;
      out.println("    " + b.toString());
      for (Component a : inverse.get(b))
        out.println("     ^-- " + a.toString());
    }
    if (!found)
      out.println("  additions: none");
  }

  // merge new edges a --> {bs} into this relation
  public void put(Component a, Collection<? extends Component> bs) {
    if (frozen)
      throw new IllegalStateException("cannot change map after frozen");

    HashSet<Component> oldBs = map.get(a);
    if (oldBs == null) {
      oldBs = new HashSet<Component>(bs.size());
      map.put(a, oldBs);
    }
    oldBs.addAll(bs);

    for (Component b : bs) {
      HashSet<Component> oldAs = inverse.get(b);
      if (oldAs == null) {
        oldAs = new HashSet<Component>(3);
        inverse.put(b, oldAs);
      }
      oldAs.add(a);
    }
  }

  // makes relation for a deleted component, replaced by nothing
  public void remove(Component a) {
    if (frozen)
      throw new IllegalStateException("cannot change map after frozen");
    map.put(a, new HashSet<Component>(3));
  }

  // makes a relation for a one-to-one relation a-->b replacing a with b
  public void replace(Component a, Component b) {
    put(a, Collections.singleton(b));
  }

  // clears relation
  public void reset() {
    map.clear();
    inverse.clear();
  }

  public String toString() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PrintStream p = new PrintStream(out, true, "UTF-8")) {
        print(p);
    } catch (Exception e) {
    }
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }
}
