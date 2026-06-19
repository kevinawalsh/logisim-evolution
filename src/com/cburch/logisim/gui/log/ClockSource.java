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
package com.cburch.logisim.gui.log;
import static com.cburch.logisim.gui.log.Strings.S;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.StringGetter;

public class ClockSource extends JDialogOk {

  private ComponentSelector selector;
  private JList<ClockGroup> groupList;
  private JLabel msgLabel = new JLabel();
  private StringGetter msg;
  SignalInfo item;

  public ClockSource(StringGetter msg, Circuit circ, boolean requireDriveable) {
    super("Clock Source Selection", true);
    this.msg = msg;

    selector = new ComponentSelector(circ,
        requireDriveable
        ? ComponentSelector.DRIVEABLE_CLOCKS
        : ComponentSelector.OBSERVEABLE_CLOCKS);

    JScrollPane explorerPane = new JScrollPane(selector,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    explorerPane.setPreferredSize(new Dimension(120, 200));

    msgLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    explorerPane.setBorder(
        BorderFactory.createCompoundBorder(
          BorderFactory.createEmptyBorder(0, 10, 10, 10),
          explorerPane.getBorder()));
    getContentPane().add(msgLabel, BorderLayout.NORTH);
    getContentPane().add(explorerPane, BorderLayout.CENTER);

    localeChanged();

    setMinimumSize(new Dimension(200, 300));
    setPreferredSize(new Dimension(300, 400));
    pack();
  }
  
  private ClockSource(StringGetter msg, Circuit circ, ArrayList<SignalInfo> clocks) {
    super("Clock Source Selection", true);
    this.msg = msg;

    // Top section: clock equivalence classes
    ArrayList<ClockGroup> groups = makeGroups(clocks);
    groupList = new JList<>(groups.toArray(new ClockGroup[0]));
    groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    groupList.setCellRenderer(new ClockGroupRenderer());
    groupList.setFixedCellHeight(24);
    if (!groups.isEmpty())
      groupList.setSelectedIndex(0);

    // Bottom section: non-Clock observable signals in an expanding tree
    selector = new ComponentSelector(circ, ComponentSelector.OBSERVEABLE_NON_CLOCKS);

    // Cross-clear: selecting in one section clears the other
    groupList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !groupList.isSelectionEmpty())
        selector.clearSelection();
    });
    selector.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !selector.getSelectionModel().isSelectionEmpty())
        groupList.clearSelection();
    });

    int clockHeight = Math.min(groups.size() * 24 + 4, 120);
    JScrollPane clockPane = new JScrollPane(groupList,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    clockPane.setPreferredSize(new Dimension(250, clockHeight));

    JLabel otherLabel = new JLabel("Other clock sources:");
    otherLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 2, 0));

    JPanel clockSection = new JPanel(new BorderLayout());
    clockSection.add(clockPane, BorderLayout.CENTER);
    clockSection.add(otherLabel, BorderLayout.SOUTH);

    JScrollPane treePane = new JScrollPane(selector,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    treePane.setPreferredSize(new Dimension(250, 150));

    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(clockSection, BorderLayout.NORTH);
    mainPanel.add(treePane, BorderLayout.CENTER);
    mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

    msgLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    getContentPane().add(msgLabel, BorderLayout.NORTH);
    getContentPane().add(mainPanel, BorderLayout.CENTER);

    localeChanged();

    setMinimumSize(new Dimension(250, 400));
    setPreferredSize(new Dimension(350, 500));
    pack();
  }

  public void localeChanged() {
    if (selector != null) selector.localeChanged();
    msgLabel.setText("<html>" + msg.toString() + "</html>"); // for line breaking
  }

  public void okClicked() {
    if (groupList != null) {
      ClockGroup sel = groupList.getSelectedValue();
      if (sel != null) {
        item = sel.members.get(0);
        return;
      }
      // Nothing selected in clock groups — check the tree
      SignalInfo.List list = selector.getSelectedItems();
      if (list != null && list.size() == 1)
        item = list.get(0);
    } else {
      SignalInfo.List list = selector.getSelectedItems();
      if (list == null || list.size() != 1)
        return;
      item = list.get(0);
    }
  }

  public static Component doClockDriverDialog(Circuit circ) {
    ClockSource dialog = new ClockSource(
        S.getter("selectClockDriverMessage"),
        circ, true);
    dialog.setVisible(true);
    return dialog.item == null ? null : dialog.item.getComponent(); // always top-level
  }

  public static SignalInfo doClockMissingObserverDialog(Circuit circ) {
    ClockSource dialog = new ClockSource(
        S.getter("selectClockMissingMessage"),
        circ, false);
    dialog.setVisible(true);
    return dialog.item;
  }

  public static SignalInfo doClockMultipleObserverDialog(Circuit circ, ArrayList<SignalInfo> clocks) {
    ClockSource dialog = new ClockSource(S.getter("selectClockMultipleMessage"), circ, clocks);
    dialog.setVisible(true);
    return dialog.item;
  }

  public static SignalInfo doClockObserverDialog(Circuit circ) {
    ArrayList<SignalInfo> clocks = ComponentSelector.findClocks(circ);
    if (clocks != null && !clocks.isEmpty()) {
      ClockSource dialog = new ClockSource(S.getter("selectClockObserverMessage"), circ, clocks);
      dialog.setVisible(true);
      return dialog.item;
    }
    // No actual Clock components — show the plain tree (all observable signals)
    ClockSource dialog = new ClockSource(S.getter("selectClockObserverMessage"), circ, false);
    dialog.setVisible(true);
    return dialog.item;
  }

  public static class CycleInfo {
    public int hi, lo, phase, ticks;
    public CycleInfo(int h, int l, int p) {
      hi = h;
      lo = l;
      phase = p;
      ticks = hi + lo;
    }
  }
  public static final CycleInfo DEFAULT_CYCLE_INFO = new CycleInfo(1, 1, 0);

  public static CycleInfo getCycleInfo(SignalInfo clockSource) {
    Component clk = clockSource.getComponent();
    if (clk.getFactory() instanceof Clock) {
      int hi = clk.getAttributeSet().getValue(Clock.ATTR_HIGH);
      int lo = clk.getAttributeSet().getValue(Clock.ATTR_LOW);
      int phase = clk.getAttributeSet().getValue(Clock.ATTR_PHASE);
      return new CycleInfo(hi, lo, phase);
    }
    return DEFAULT_CYCLE_INFO;
  }

  public static boolean allEquivalent(ArrayList<SignalInfo> clocks) {
    if (clocks.size() <= 1)
      return true;
    CycleInfo first = getCycleInfo(clocks.get(0));
    for (int i = 1; i < clocks.size(); i++) {
      CycleInfo ci = getCycleInfo(clocks.get(i));
      if (ci.hi != first.hi || ci.lo != first.lo || ci.phase != first.phase)
        return false;
    }
    return true;
  }

  private static ArrayList<ClockGroup> makeGroups(ArrayList<SignalInfo> clocks) {
    LinkedHashMap<String, ClockGroup> map = new LinkedHashMap<>();
    for (SignalInfo clock : clocks) {
      CycleInfo ci = getCycleInfo(clock);
      String key = ci.hi + ":" + ci.lo + ":" + ci.phase;
      map.computeIfAbsent(key, k -> new ClockGroup(ci)).members.add(clock);
    }
    return new ArrayList<>(map.values());
  }

  private static String clockLocationLabel(SignalInfo clock) {
    int n = clock.getPathLength();
    if (n == 1)
      return "at " + clock.getPathComponent(0).getLocation();
    StringBuilder sb = new StringBuilder("in ");
    for (int i = 0; i < n - 1; i++) {
      if (i > 0) sb.append(", ");
      Component subcomp = clock.getPathComponent(i);
      String label = subcomp.getAttributeSet().getValue(StdAttr.LABEL);
      if (label != null && !label.isEmpty())
        sb.append(label);
      else
        sb.append(clock.getPathCircuit(i + 1).getName());
      sb.append(" ").append(subcomp.getLocation());
    }
    sb.append(" at ").append(clock.getPathComponent(n - 1).getLocation());
    return sb.toString();
  }

  private static String clockGroupLabel(ClockGroup group) {
    CycleInfo ci = group.params;
    String params = "[" + ci.hi + ":" + ci.lo + ":" + ci.phase + "]";
    String loc = clockLocationLabel(group.members.get(0));
    int others = group.members.size() - 1;
    String suffix = others > 0
        ? " and " + others + " other location" + (others == 1 ? "" : "s")
        : "";
    return "Clock " + params + " " + loc + suffix;
  }

  private static class ClockGroup {
    final CycleInfo params;
    final ArrayList<SignalInfo> members = new ArrayList<>();
    ClockGroup(CycleInfo params) { this.params = params; }
  }

  private class ClockGroupRenderer extends DefaultListCellRenderer {
    @Override
    public java.awt.Component getListCellRendererComponent(JList<?> list,
        Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof ClockGroup) {
        ClockGroup group = (ClockGroup) value;
        setText(clockGroupLabel(group));
        setIcon(group.members.get(0).icon);
      }
      return this;
    }
  }

}
