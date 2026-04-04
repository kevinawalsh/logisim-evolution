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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

  // For the affected circuit, there are three mutually disjoint sets:
  // - some components unaffected by this ReplacementMap
  // - zero or more oldComponents that were previously in this circuit
  //   but will no longer be due to the actions of this ReplacementMap
  // - zero or more newComponents that were not previously in this
  //   circuit but are going to be due to the actions of of this ReplacementMap
  // In other words:
  //  circuit before ReplacementMap = oldComps + unaffectedComps
  //  circuit after ReplacementMap =             unaffectedComps + newComps
  // And there are 3 scenarios:
  // 1. a1 --> b1, b2, ...      // old a replaced by new b1, b2, ...
  //    a1... <-- b1            // new b1 replaces old a1... [inverse relation]
  //    a1... <-- b2            // new b2 replaces old a1... [inverse relation]
  //             ...
  // 2. a1 --> { }              // old a1 replaced by nothing, i.e. a1 was simply deleted
  //                            // inverse relation doesn't have any entry here
  // 3. { } <-- b1              // new b1 replaces nothing, i.e. b1 was added from thin air
  //                            // map doesn't have any entry for this
  // And an invariant:
  //   Whenever map[a] = { ... b ... }
  //   Whenever inverse[b] = { ... a ... }

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
    sanityCheck("1-to-1 constructor");
  }

  void sanityCheck(String title) {
    Set<Component> oldComps = map.keySet();
    Set<Component> newComps = inverse.keySet();
    Set<Component> intersection = new HashSet<>(oldComps);
    intersection.retainAll(newComps);
    if (!intersection.isEmpty()) {
      System.err.println("In " + title);
      System.err.printf("ERR: %d components are both old and new\n", intersection.size());
      for (Component comp : intersection) 
        System.err.printf("     %s is both old and new\n", comp);
    }
    Set<Component> allBs = new HashSet<>();
    for (Component oldComp : map.keySet()) {
      HashSet<Component> bs = map.get(oldComp);
      allBs.addAll(bs);
    }
    // allBs should be newComps
    allBs.removeAll(newComps);
    if (!allBs.isEmpty()) {
      System.err.println("In " + title);
      System.err.printf("ERR: %d components replace old things, but aren't new?\n", allBs.size());
      for (Component comp : allBs) 
        System.err.printf("     %s replaces an old but isn't new\n", comp);
    }
    Set<Component> allAs = new HashSet<>();
    for (Component newComp : inverse.keySet()) {
      HashSet<Component> as = inverse.get(newComp);
      allAs.addAll(as);
    }
    // allAs should be oldComps
    allAs.removeAll(oldComps);
    if (!allAs.isEmpty()) {
      System.err.println("In " + title);
      System.err.printf("ERR: %d components replaced by new things, but aren't old?\n", allAs.size());
      for (Component comp : allAs) 
        System.err.printf("     %s replaced by a new but isn't old\n", comp);
    }
    // Whenever map[a]={...b...} then inverse[b]={...a...}
    for (Component a : map.keySet()) {
      HashSet<Component> bs = map.get(a);
      for (Component b : bs) {
        HashSet<Component> as = inverse.get(b);
        if (as == null) {
          System.err.println("In " + title);
          System.err.printf("ERR: a replaced by b, but b missing from inverse map: %s %s\n", a, b);
        } else if (!as.contains(a)) {
          System.err.println("In " + title);
          System.err.printf("ERR: a replaced by b, but b doesn't replace a: %s %s\n", a, b);
        }
      }
    }
    // vice versa: Whenever inverse[b]={...a...} then map[a]={...b...}
    for (Component b : inverse.keySet()) {
      HashSet<Component> as = inverse.get(b);
      for (Component a : as) {
        HashSet<Component> bs = map.get(a);
        if (as == null) {
          System.err.println("In " + title);
          System.err.printf("ERR: b replaces a, but a missing from map: %s %s\n", a, b);
        } else if (!as.contains(a)) {
          System.err.println("In " + title);
          System.err.printf("ERR: b replaces a, but a isn't replaced by b: %s %s\n", a, b);
        }
      }
    }
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
    HashSet<Component> oldSet = inverse.put(comp, new HashSet<Component>(3));
    if (oldSet != null) {
      System.err.println("Internal error: duplicate component in add()");
      Thread.dumpStack();
    }
  }

  // compose (math-style) two relations:
  //    a-->b  b-->c  becomes a-->c
  //    c<--b  b<--a  becomes c<--a
  void append(ReplacementMap next) {
    // Sanity: anything removed by this should not be present in next
    for (Component a: this.map.keySet()) {
      if (next.map.get(a) != null) {
        System.err.println("a removed twice: " + a);
        Thread.dumpStack();
      }
      if (next.inverse.get(a) != null) {
        System.err.println("a removed, then re-added: " + a);
      }
    }
    // Sanity: anything added by this, should not be also added by next
    for (Component b: next.inverse.keySet()) {
      if (this.inverse.get(b) != null) {
        System.err.println("b added twice: " + b);
        Thread.dumpStack();
      }
    }

    // Step 1: Handle cases where next deletes b outright, or replaces b by c1...
    for (Map.Entry<Component, HashSet<Component>> e : next.map.entrySet()) {
      Component b = e.getKey();
      HashSet<Component> cs = e.getValue(); // what b is replaced by
      // Four possible scenarios:
      // this          next
      // a1... <-- b   b --> c1...  //  1: a1... replaced by b, then b replaced by c1...
      // no preimage   b --> c1...  //  2: b unaffected, then b replaced by c1...
      // a1... <-- b   b --> { }    //  3: a1... replaced by b, then b deleted
      // no preimage   b --> { }    //  4: b unaffected, then b deleted
      HashSet<Component> as = this.inverse.remove(b); // what was replaced to get b
      // For cases 1 and 3, we just removed the preimage, so b is no longer in this.inverse
      // What about cases where we are doing multiple loops?
      // Example:
      // this          next
      // a1 <--> b1    b1,b2 <--> c
      // a2 <--> b2
      // First iteration is for b1 --> c
      // - case 1, bWasPreviouslyUnaffected=false (because inverse[b1] exists)
      // - inverse[b1]          removed entry
      // - map[a1]={c}          removed b1 from set, added c instead
      // - inverse[c]={a1}      created a new inverse entry for c
      // Second iteration is for b2 --> c
      // - case 1, bWasPreviouslyUnaffected=false (because inverse[b2] exists)
      // - inverse[b2]          removed entry
      // - map[a2]={c}          removed b2 from set, added c instead
      // - inverse[c]={a1,a2}   found inverse entry for c, added a2
      // [this all seems correct]
      // Is it possible to encounter the same b multiple times and get confused?
      // No: because we iterate over b in next.map keyset, so the b are unique.
      boolean bWasPreviouslyUnaffected = false;
      if (as == null) { // no preimage: b was unaffected by this ReplacementMap (case 2, 4)
        as = new HashSet<Component>(3); // fake entry: we say b replaces itself.
        as.add(b);
        // FIXME: just handle these cases here, fully, don't add a fake entry and fall to below
        bWasPreviouslyUnaffected = true;
      }
      // With the fake entry for as, we now have:
      // this          next
      // a1... <-- b   b --> c1...  //  1: a1... replaced by b, then b replaced by c1...
      //     b <-- b   b --> c1...  //  2: b unaffected, then b replaced by c1...
      // a1... <-- b   b --> { }    //  3: a1... replaced by b, then b deleted
      //     b <-- b   b --> { }    //  4: b unaffected, then b deleted
      for (Component a : as) {
        HashSet<Component> aDst = this.map.get(a);
        if (aDst == null) { // should happen in the "no preimage" cases (case 2, 4)
          // in those, b was unaffected, so it shouldn't be in either map or inverse
          if (!bWasPreviouslyUnaffected)
            System.err.println("huh? b replaced a, but a isn't in map?");
          if (a != b)
            System.err.println("huh? b != a?");
          aDst = new HashSet<Component>(cs.size());
          this.map.put(a, aDst);
          // Here a==b so we just added b --> {} to the map, and next will
          // try to remove b from the set (will be nop, since it's empty), then
          // add all of cs, so we end up with map containing: b --> cs
          // [this seems correct]
        } else {
          // should happen in cases where b was new (case 1, 3)
          if (bWasPreviouslyUnaffected)
            System.err.println("huh? b was unaffected, but b was in the map?");
          // inverse[b]={...a...} means b... replaced a...
          // map[a]=aDst means a... were replaced by something (should be b...)
          if (!aDst.contains(b))
            System.err.println("huh? b replaced a, but a not replaced by b?");
          // Next we will change map[a] to remove b and add cs,
          // so we end up with map containing: a --> ...cs...
          // [this seems correct]
          // and our inverse isn't yet fixed:
          //   inverse still has: a... <-- b
          //   but needs instead: a... <-- c
        }
        aDst.remove(b);
        aDst.addAll(cs);
      }

      // This adds inverse: a... <-- c
      for (Component c : cs) {
        // Intially, this.inverse won't have any entry for c, since c was newly added
        // by next ReplacementMap. But if next has b1 --> {c} and b2 --> {c}, for example,
        // and this.inverse has {a1, a2} <-- b1 and {a3, a4} <-- b2, then
        // on first iteration, we add {a1,a2} <-- c to this.inverse,
        // and on second iteration, we find c is already prresent in this.inverse,
        // and just expand it to be {a1,a2,a3,a4} <-- c.
        HashSet<Component> cSrc = this.inverse.get(c);
        if (cSrc == null) {
          cSrc = new HashSet<Component>(as.size());
          this.inverse.put(c, cSrc);
        }
        cSrc.addAll(as);
      }
    }

    // Step 2: Handle cases where next adds c from thin air
    for (Map.Entry<Component, HashSet<Component>> e : next.inverse.entrySet()) {
      Component c = e.getKey();
      // Four possible scenarios:
      // this          next
      //               b... <-- c  //  1: something happens, then c replaces b...
      //                               (already handled above, c will be in this.inverse)
      //               {  } <-- c  //  2: something happens, then c added from thin air
      //                               (not yet handled, c will not be in this.inverse)
      if (!inverse.containsKey(c)) {
        // case 2: next added c from thin air
        HashSet<Component> bs = e.getValue();
        if (!bs.isEmpty()) {
          // FIXME: We are seeing this error in practice
          System.err.println("Internal error: component replaced but not represented");
          Thread.dumpStack();
        }
        // Add { } <-- c to this.inverse
        // [this seems correct]
        inverse.put(c, new HashSet<Component>(3));
      }
    }
    sanityCheck("append");
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
    HashSet<Component> oldSet = map.put(a, new HashSet<Component>(3));
    if (oldSet != null) {
      System.err.println("Internal error: duplicate component in remove()");
      Thread.dumpStack();
    }
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
