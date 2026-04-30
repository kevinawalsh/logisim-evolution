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

package com.cburch.logisim.gui.prefs;
import static com.cburch.logisim.gui.prefs.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.JButton;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.PillLabel;
import com.cburch.logisim.gui.opts.MouseOptions;
import com.cburch.logisim.gui.opts.SimulateOptions;
import com.cburch.logisim.gui.opts.ToolbarOptions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.TableLayout;
import com.cburch.logisim.util.WindowMenuItemManager;

public class SettingsFrame extends LFrame.Dialog {

  enum NavItemType { HEADER, PANEL }
  enum Section { APP, PROJ, FPGA }

  static class NavItem {
    final NavItemType type;
    final SettingsPanel panel; // null for headers
    Section section;
    String label;

    NavItem(String label, Section section) { // header
      this.type = NavItemType.HEADER;
      this.label = label;
      this.section = section;
      this.panel = null;
    }
    NavItem(SettingsPanel panel, Section section) { // panel item
      this.type = NavItemType.PANEL;
      this.panel = panel;
      this.section = section;
      this.label = panel.getTitle();
    }
  }

  private static final Color APP_COLOR  = new Color(0x4A8EDB);
  private static final Color PROJ_COLOR = new Color(0x4A9A55);
  private static final Color FPGA_COLOR = new Color(0x7B5EA7);

  private class HeaderBar extends JPanel {
    private final PillLabel pill = new PillLabel();
    private final JComboBox<Project> projectCombo = new JComboBox<>();
    private boolean updatingCombo = false;

    HeaderBar() {
      pill.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 12f));
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 40)),
          BorderFactory.createEmptyBorder(7, 12, 7, 8)));
      setPreferredSize(new Dimension(0, 36));
      projectCombo.setAlignmentY(CENTER_ALIGNMENT);
      projectCombo.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int idx, boolean sel, boolean focus) {
          super.getListCellRendererComponent(list, value, idx, sel, focus);
          if (value instanceof Project)
            setText(((Project) value).getLogisimFile().getDisplayName());
          return this;
        }
      });
      projectCombo.addActionListener(e -> {
        if (updatingCombo) return;
        Project sel = (Project) projectCombo.getSelectedItem();
        if (sel != null && sel != project) {
          switchProject(sel);
          selectSome(Section.PROJ);
        }
      });
      add(pill);
      add(Box.createHorizontalStrut(8));
      add(projectCombo);
      add(Box.createHorizontalGlue());
      projectCombo.setVisible(false);
    }

    void update(String pillText, Color c, boolean isProj) {
      pill.setText(pillText);
      pill.setBackground(c);
      projectCombo.setVisible(isProj);
      if (isProj) refreshProjectCombo();
      revalidate();
      repaint();
    }

    void refreshProjectCombo() {
      updatingCombo = true;
      projectCombo.removeAllItems();
      int i = 0;
      for (Project p : Projects.getOpenProjects())
        projectCombo.addItem(p);
      if (project != null)
        projectCombo.setSelectedItem(project);
      updatingCombo = false;
      revalidate();
      repaint();
    }
  }

  private static class NavCellRenderer extends DefaultListCellRenderer {
    private static final Color HEADER_BG = new Color(0xEEEEEE);
    private static final Font  HEADER_FONT;
    static {
      HEADER_FONT = new JLabel().getFont().deriveFont(Font.BOLD, 11f);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
        int index, boolean selected, boolean cellHasFocus) {
      NavItem item = (NavItem) value;
      JLabel label = (JLabel) super.getListCellRendererComponent(
          list, item.label, index, selected && item.type == NavItemType.PANEL, false);
      if (item.type == NavItemType.HEADER) {
        label.setBackground(HEADER_BG);
        label.setFont(HEADER_FONT);
        label.setForeground(new Color(0x666666));
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        label.setEnabled(false);
      } else {
        label.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 8));
      }
      return label;
    }
  }

  private class MyListener implements LocaleListener {
    @Override
    public void localeChanged() {
      setTitle(S.get("settingsFrameTitle"));
      refreshNavLabels();
      updateBadge();
      for (SettingsPanel p : appPanels) p.localeChanged();
      if (projPanels != null)
        for (SettingsPanel p : projPanels) p.localeChanged();
      for (SettingsPanel p : fpgaPanels) p.localeChanged();
    }
  }

  private static class WinMenuManager extends WindowMenuItemManager implements LocaleListener {
    WinMenuManager() {
      super(S.get("settingsFrameTitle"), true);
      LocaleManager.addLocaleListener(this);
    }
    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return INSTANCE;
    }
    @Override
    public void localeChanged() {
      setText(S.get("settingsFrameTitle"));
    }
  }

  private static SettingsFrame INSTANCE = null;
  private static WinMenuManager MENU_MANAGER = null;

  public static void initializeManager() {
    MENU_MANAGER = new WinMenuManager();
  }

  public static void showAppSettings() {
    SettingsFrame f = getInstance();
    f.onProjectListChanged(true);
    f.selectSome(Section.APP);
    f.setVisible(true);
    f.toFront();
  }

  public static void showProjectSettings(Project proj, Class<? extends SettingsPanel> clazz) {
    SettingsFrame f = getInstance();
    f.switchProject(proj);
    if (clazz == null)
      f.selectSome(Section.PROJ);
    else
      f.selectPanel(clazz);
    f.setVisible(true);
    f.toFront();
  }

  public static void showFPGASettings() {
    SettingsFrame f = getInstance();
    f.onProjectListChanged(true);
    f.selectSome(Section.FPGA);
    f.setVisible(true);
    f.toFront();
  }

  // public static void showProjectPanel(Project proj, int panelIndex) {
  //   SettingsFrame f = getInstance();
  //   f.switchProject(proj);
  //   f.selectProjPanel(panelIndex);
  //   f.setVisible(true);
  //   f.toFront();
  // }

  private static SettingsFrame getInstance() {
    if (INSTANCE == null)
      INSTANCE = new SettingsFrame();
    return INSTANCE;
  }

  private Project project;
  private SettingsPanel[] appPanels;
  private SettingsPanel[] projPanels; // null when no project
  private SettingsPanel[] fpgaPanels;

  private final DefaultListModel<NavItem> navModel = new DefaultListModel<>();
  private final JList<NavItem> navList = new JList<>(navModel);
  private final JPanel contentHolder = new JPanel(new BorderLayout());
  private final HeaderBar header = new HeaderBar();
  private final MyListener myListener = new MyListener();
  private JScrollPane navScroll; // set in buildUI, sized after locale applied

  // Indices into navModel for section headers (updated when proj panels rebuilt)
  private int projHeaderIndex = -1;

  private SettingsFrame() {
    super(null); // not associated with a specific project window
    setDefaultCloseOperation(HIDE_ON_CLOSE);

    appPanels = new SettingsPanel[] {
      new TemplateOptions(this),
      new IntlOptions(this),
      new WindowOptions(this),
      new LayoutOptions(this),
      new ExperimentalOptions(this),
    };
    projPanels = null;
    fpgaPanels = new SettingsPanel[] {
      new QuestaOptions(this),
      new ApioOptions(this),
      new OpenFPGALoaderOptions(this),
    };

    onProjectListChanged(true);

    buildNavModel();
    buildUI();

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    Projects.addListChangeWeakListener(this, () -> onProjectListChanged(false));
    // Size nav column to fit its labels, then derive window sizes from that.
    int navW = Math.max(160, navList.getPreferredSize().width) + 4;
    navScroll.setPreferredSize(new Dimension(navW, 0));
    setPreferredSize(new Dimension(navW + 570, 480));
    setMinimumSize(new Dimension(navW + 320, 300));
    pack();
    setLocationRelativeTo(null);
  }


  public Project getProject() {
    return project;
  }

  public Options getOptions() {
    return project == null ? null : project.getLogisimFile().getOptions();
  }

  private void onProjectListChanged(boolean selectSomeProject) {
    header.refreshProjectCombo();
    List<Project> open = Projects.getOpenProjects();
    if (open.isEmpty()) {
      switchProject(null);
      NavItem item = navList.getSelectedValue();
      if (item == null || (item.type == NavItemType.PANEL && item.section == Section.PROJ))
        selectSome(Section.APP);
    } else if (selectSomeProject || project == null || !open.contains(project)) {
      switchProject(open.get(0));
      selectSome(Section.PROJ);
    }
  }

  private void switchProject(Project proj) {
    if (proj == project) return;
    project = proj;
    projPanels = (proj == null) ? null : new SettingsPanel[] {
      new SimulateOptions(this),
      new ToolbarOptions(this),
      new MouseOptions(this),
      new RevertPanel(this),
    };
    buildNavModel();
    navList.repaint();
  }

  private void buildNavModel() {
    navModel.clear();
    navModel.addElement(new NavItem(S.get("settingsNavAppSection"), Section.APP));
    for (SettingsPanel p : appPanels)
      navModel.addElement(new NavItem(p, Section.APP));

    if (projPanels != null) {
      projHeaderIndex = navModel.size();
      navModel.addElement(new NavItem(S.get("settingsNavProjectSection"), Section.PROJ));
      for (SettingsPanel p : projPanels)
        navModel.addElement(new NavItem(p, Section.PROJ));
    } else {
      projHeaderIndex = -1;
    }

    navModel.addElement(new NavItem(S.get("settingsNavFPGASection"), Section.FPGA));
    for (SettingsPanel p : fpgaPanels)
      navModel.addElement(new NavItem(p, Section.FPGA));
  }

  private void refreshNavLabels() {
    for (int i = 0; i < navModel.size(); i++) {
      NavItem item = navModel.get(i);
      if (item.type == NavItemType.HEADER) {
        item.label = 
          item.section == Section.APP ? S.get("settingsNavAppSection") :
          item.section == Section.FPGA ? S.get("settingsNavFPGASection") :
          S.get("settingsNavProjectSection");
      } else {
        item.label = item.panel.getTitle();
      }
    }
    navList.repaint();
  }

  private void buildUI() {
    // Nav list — skip header items for selection
    navList.setCellRenderer(new NavCellRenderer());
    navList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    navList.setFixedCellHeight(-1);
    navList.setSelectionModel(new javax.swing.DefaultListSelectionModel() {
      @Override
      public void setSelectionInterval(int i0, int i1) {
        // Skip headers
        if (i0 >= 0 && i0 < navModel.size() && navModel.get(i0).type == NavItemType.HEADER)
          return;
        super.setSelectionInterval(i0, i1);
      }
    });
    navList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        NavItem item = navList.getSelectedValue();
        if (item != null && item.type == NavItemType.PANEL)
          showPanel(item.panel, item.section);
      }
    });

    // navScroll width is set after locale is applied (in constructor); height fills panel.
    navScroll = new JScrollPane(navList,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    navScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0, 0, 0, 40)));

    // Content area: header on top, panel below in scroll pane
    JScrollPane contentScroll = new JScrollPane(contentHolder,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    contentScroll.setBorder(null);
    contentScroll.setMinimumSize(new Dimension(300, 0));

    JPanel rightSide = new JPanel(new BorderLayout());
    rightSide.add(header, BorderLayout.NORTH);
    rightSide.add(contentScroll, BorderLayout.CENTER);

    // BorderLayout WEST keeps the nav at its preferred width (fixed); CENTER gets the rest.
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(navScroll, BorderLayout.WEST);
    mainPanel.add(rightSide, BorderLayout.CENTER);

    getContentPane().add(mainPanel, BorderLayout.CENTER);
  }

  private void showPanel(SettingsPanel panel, Section section) {
    contentHolder.removeAll();
    contentHolder.add(panel, BorderLayout.CENTER);
    contentHolder.revalidate();
    contentHolder.repaint();
    String pillText =
      section == Section.APP ? S.get("settingsNavAppBadge") :
      section == Section.FPGA ? S.get("settingsNavFPGABadge") :
      S.get("settingsNavProjectBadge");
    header.update(pillText,
        section == Section.APP ? APP_COLOR :
        section == Section.FPGA ? FPGA_COLOR :
        PROJ_COLOR,
        section == Section.PROJ);
  }

  private void updateBadge() {
    NavItem item = navList.getSelectedValue();
    if (item != null && item.type == NavItemType.PANEL)
      showPanel(item.panel, item.section);
  }

  private void selectSome(Section section) {
    NavItem item = navList.getSelectedValue();
    if (item != null && item.type == NavItemType.PANEL && item.section == section)
      return;
    for (int i = 0; i < navModel.size(); i++) {
      item = navModel.get(i);
      if (item.type == NavItemType.PANEL && item.section == section) {
        navList.setSelectedIndex(i);
        navList.ensureIndexIsVisible(i);
        return;
      }
    }
  }

  private void selectPanel(Class<? extends SettingsPanel> clazz) {
    NavItem item = navList.getSelectedValue();
    if (item != null && item.type == NavItemType.PANEL && clazz.isInstance(item.panel))
      return;
    for (int i = 0; i < navModel.size(); i++) {
      item = navModel.get(i);
      if (item.type == NavItemType.PANEL && clazz.isInstance(item.panel)) {
        navList.setSelectedIndex(i);
        navList.ensureIndexIsVisible(i);
        return;
      }
    }
  }

  // private void selectProjPanel(int panelIndex) {
  //   if (item != null && item.type == NavItemType.PANEL && !item.isApp)
  //     return;
  //   int found = 0;
  //   for (int i = 0; i < navModel.size(); i++) {
  //     NavItem item = navModel.get(i);
  //     if (item.type == NavItemType.PANEL && !item.isApp) {
  //       if (found == panelIndex) {
  //         navList.setSelectedIndex(i);
  //         navList.ensureIndexIsVisible(i);
  //         return;
  //       }
  //       found++;
  //     }
  //   }
  // }

  @Override
  public void setVisible(boolean value) {
    if (value && navList.getSelectedValue() == null)
      selectSome(Section.APP);
    if (value && MENU_MANAGER != null)
      MENU_MANAGER.frameOpened(this);
    super.setVisible(value);
  }

  static class RevertPanel extends SettingsPanel {
    private JButton revert = new JButton();

    RevertPanel(SettingsFrame frame) {
      super(frame);
      setLayout(new TableLayout(1));
      JPanel buttonPanel = new JPanel();
      buttonPanel.add(revert);
      revert.addActionListener(e -> getSettingsFrame().getProject().doAction(
          LogisimFileActions.revertDefaults()));
      add(buttonPanel);
    }

    @Override public String getHelpText() { return S.get("revertHelp"); }
    @Override public String getTitle()    { return S.get("revertTitle"); }
    @Override public void localeChanged() { revert.setText(S.get("revertButton")); }
  }

}
