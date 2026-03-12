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

// Based on code written by Josh Israel, as part of Algorithms, 4th edition,
// available at: https://algs4.cs.princeton.edu/33balanced/SplayBST.java

package com.cburch.logisim.util;

// A simple splay tree implementation, using keys of type long, and values that
// extend type QNode. This supports (approximately) a subset of the
// java.util.PriorityQueue API, but only enough to support Propagator.
public class SplayQueue<T extends QNode> {

  // Objects in the queue must be subclasses of QNode.

  // Root of the tree.
  private T root;
  private int size;

  // add(t) inserts a new node into the queue.
  @SuppressWarnings("unchecked")
  public void add(T t) {
    if (root == null) {
      root = t;
      size++;
      return;
    }

    root = (T)splay(root, t.key);
    long cmp = t.key - root.key;

    if (cmp < 0) {
      // New node t displaces root, which moves down right.
      t.left = root.left;
      t.right = root;
      root.left = null;
      root = t;
      size++;
    } else if (cmp > 0) {
      // New node t displaces root, which moves down left.
      t.right = root.right;
      t.left = root;
      root.right = null;
      root = t;
      size++;
    } else {
      throw new IllegalArgumentException("SplayQueue keys must be unique");
    }
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public void clear() {
    root = null;
    size = 0;
  }

  // splay(t, k) rebalances the tree rooted at node t around key k, by moving a
  // node close to k (or an exact match, if it exists) up to the root.
  private static QNode splay(QNode t, long k) {
    if (t == null)
      return null;

    long cmp1 = k - t.key;
    if (cmp1 < 0) {
      if (t.left == null)
        return t; // can't go any further left
      long cmp2 = k - t.left.key;
      if (cmp2 < 0) {
        t.left.left = splay(t.left.left, k);
        t = rotateRight(t);
      } else if (cmp2 > 0) {
        t.left.right = splay(t.left.right, k);
        if (t.left.right != null)
          t.left = rotateLeft(t.left);
      }
      return t.left == null ? t : rotateRight(t);

    } else if (cmp1 > 0) { 
      if (t.right == null)
        return t; // can't go any further right
      long cmp2 = k - t.right.key;
      if (cmp2 < 0) {
        t.right.left  = splay(t.right.left, k);
        if (t.right.left != null)
          t.right = rotateRight(t.right);
      }
      else if (cmp2 > 0) {
        t.right.right = splay(t.right.right, k);
        t = rotateLeft(t);
      }
      return t.right == null ? t : rotateLeft(t);

    } else {
      return t;
    }
  }

  // splay(t) rebalances the tree rooted at node t, by moving the smallest node
  // to the root.
  private static QNode splay(QNode t) {
    if (t == null)
      return null;
    if (t.left == null)
      return t; // can't go any further left
    t.left.left = splay(t.left.left);
    t = rotateRight(t);
    return t.left == null ? t : rotateRight(t);
  }

  private static QNode rotateRight(QNode t) {
    QNode x = t.left;
    t.left = x.right;
    x.right = t;
    return x;
  }

  private static QNode rotateLeft(QNode t) {
    QNode x = t.right;
    t.right = x.left;
    x.left = t;
    return x;
  }

  // peek() returns the smallest node, or null if the queue is empty.
  @SuppressWarnings("unchecked")
  public T peek() {
    if (root == null)
      return null;
    root = (T)splay(root);
    return root;
  }

  // remove() removes the smallest node, or null if the queue is empty.
  @SuppressWarnings("unchecked")
  public T remove() {
    if (root == null)
      return null;
    size--;
    T t = (T)splay(root);
    root = (T)t.right;
    return t;
  }

}
