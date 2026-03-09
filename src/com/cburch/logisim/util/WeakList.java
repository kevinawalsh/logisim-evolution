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

package com.cburch.logisim.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

// WeakList holds a list of event listeners (or any, object, really). Each
// listener may have an owner object. So long as the owner object has not been
// garbage-collected, a strong reference is held to the listener preventing the
// listener from being garbage collected. Once the owner object has been garbage
// collected, however, or if the listener has a null owner, then only a weak
// reference will be held to the listener, meaning it can be garbage-collected
// and lazily removed from the list at any point (i.e. once there are no other
// references to the listener elsewhere).
//
// Any wrapper should be sure to either:
//  - expose the owner parameter to clients
//  - use "Weak" in wrapper method names
// Otherwise, it is too easy for clients to overlook the fact that listeners
// objects are only weakly held and might be garbage collected earlier than
// intended.
//
// Note: Using weak references for listener lists is needed if we want automatic
// cleanup, instead of relying on manual clenaup, i.e. calling
// removeListener(listener) aat some point. But using weak references alone,
// without an "owner" mechanism, isn't sufficient for many cases. Even with an
// "owner" mechanism, client code can be brittle and contain lots of spooky
// action at a distance. When listeners are weakly held, all these
// seemingly-equivalent ways of adding a listener to some class are subtly
// different:
// (1) addListener(this); // where this implements some listener interface
// (2) addListener(this.mylistener); // where mylistener is a member variable
// (3) addListener(mylistener); // where mylistener is a member variable
// (4) addListener(mylistener); // where mylistener is a method-local variable
// (5) addListener(new MyListener());
// (6) addListener(e -> this.foo());
// (7) addListener(this::foo);
// From a cursory pass through the entire code base...
// Case (1) is pretty common, and is usually fine, but it means the handler
//   code is usually separated pretty far from the call site, and it results in
//   lots of public methods which can make it harder to reason about a class.
//   And if the "this" reference is actually an inner class that accidentally
//   doesn't have any strong references, then it instead is likely completely
//   broken.
// Case (2) doesn't appear at all, though it is equivalent to (3).
// Case (3) happens all the time, and is usually fine unless there are are some
//   nested inner classes happening without strong references. Unfortunately,
//   the way it is written is inditinguishable from (4), one of the buggy cases.
//   And having a member variable that is created, used once, then never used
//   again, all but invites someone to refactor it into a method-local variable,
//   like case (4), or just an immediate object, like case (5), both of which
//   are buggy cases.
// Case (4) happens rarely, but is usually a bug when it does.
// Case (5) happens sometimes, and seems to usually be a bug, except in several
//   cases where the listener list actually happens to be an ArrayList instead
//   of a weak-reference list in some particular circumstances or for certain
//   specific implementation of some service interface.
// Case (6) happens lots in newer code, and is always a bug, except when that
//   particular service uses an ArrayList instead of a weak-reference list. This
//   is also the most readable code in my opinion, however.
// Case (7) doesn't appear anywhere yet. I find this style ugly. But it would be
//   equivalent to (6), assuming Java objects do not contain internal strong
//   references to all of their member method objects. But if they do, then this
//   code is fine.
// So to summarize, with simple weak references, the most readable and "modern"
// code is a bug, and the correct cases are fragile and hard to verify. All of
// this to save from having to remove the listeners manually, which would
// require thinking about the lifetime of client objects (and if we are wrong or
// miss a case, we probably get a memory leak). The owner mechanism can work
// around these problems. Of course, having an owner mechanism means we still
// need to think about the lifetime of client objects (and if we are wrong or
// miss a case, we end up with listeners not getting called and subtle bugs).
public class WeakList<L> implements Iterable<L> {
  private final ConcurrentLinkedQueue<WeakReference<L>> listeners = new ConcurrentLinkedQueue<WeakReference<L>>();
  private final Map<Object, LinkedList<L>> strongrefs = new WeakIdentityHashMap<>();
  private final Object lock = new Object();

  // We deliberately use an Identity-based HashMap, because we want reference
  // equality not ".equals()" equality when dealing with owner objects.

  public WeakList() { }

  public void add(Object owner, L listener) {
    if (owner != null) {
      synchronized(lock) {
        LinkedList<L> r = strongrefs.get(owner);
        if (r == null) {
          r = new LinkedList<L>();
          strongrefs.put(owner, r);
        }
        r.add(listener); // strong ref
      }
    }
    listeners.add(new WeakReference<L>(listener));
  }

  public void remove(Object owner, L listener) {
    if (owner != null) {
      synchronized(lock) {
        LinkedList<L> r = strongrefs.get(owner);
        if (r != null) {
          r.remove(listener); // remove strong ref
          if (r.isEmpty())
            strongrefs.remove(owner);
        }
      }
    }
    for (Iterator<WeakReference<L>> it = listeners.iterator(); it.hasNext();) {
      L l = it.next().get();
      if (l == null || l == listener)
        it.remove();
    }
  }

  public boolean isEmpty() {
    for (Iterator<WeakReference<L>> it = listeners.iterator(); it.hasNext();) {
      L l = it.next().get();
      if (l == null)
        it.remove();
      else
        return false;
    }
    return true;
  }

  public Iterator<L> iterator() {
    // We must copy elements into another list to protect against concurrent
    // modification issues, i.e. in case an event handler triggers code that
    // adds or removes a listener.
    ArrayList<L> ret = new ArrayList<L>(listeners.size());
    for (Iterator<WeakReference<L>> it = listeners.iterator(); it.hasNext();) {
      L l = it.next().get();
      if (l == null)
        it.remove();
      else
        ret.add(l);
    }
    return ret.iterator();
  }
}
