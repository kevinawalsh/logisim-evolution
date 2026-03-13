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

package com.cburch.logisim.gui.find;
import static com.cburch.logisim.gui.find.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.hdl.HdlModel;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.gui.generic.DetailPanel;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.WrapLayout;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.decor.Text;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;

public class FindFrame extends LFrame.Dialog implements LocaleListener {
  // maybe use LFrame.SubWindow instead?

  private class TopPanel extends JPanel {
    TitledBorder findTextBorder = BorderFactory.createTitledBorder("");
    TitledBorder findRegexBorder = BorderFactory.createTitledBorder("");
    JTextField field = new JTextField();
    TitledBorder whereBorder = BorderFactory.createTitledBorder("");
    TitledBorder howBorder = BorderFactory.createTitledBorder("");
    JRadioButton inSheet = new JRadioButton();
    JRadioButton inCircuit = new JRadioButton();
    JRadioButton inProject = new JRadioButton();
    JRadioButton inAll = new JRadioButton();
    JCheckBox caseSensitive = new JCheckBox();
    JRadioButton matchSubstring = new JRadioButton();
    JRadioButton matchWord = new JRadioButton();
    JRadioButton matchExact = new JRadioButton();
    JRadioButton matchRegex = new JRadioButton();
    JButton go = new JButton();
    DetailPanel options;

    TopPanel() {
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

      JPanel input = new JPanel(new BorderLayout());
      input.setBorder(findTextBorder);
      input.add(field, BorderLayout.CENTER);
      add(input);

      JPanel where = new JPanel(new WrapLayout());
      where.setBorder(whereBorder);
      where.add(inSheet);
      where.add(inCircuit);
      where.add(inProject);
      where.add(inAll);
      add(where);

      JPanel how = new JPanel(new WrapLayout());
      how.setBorder(howBorder);
      how.add(caseSensitive);
      how.add(matchSubstring);
      how.add(matchWord);
      how.add(matchExact);
      how.add(matchRegex);
      options = new DetailPanel("", how);
      add(options);

      matchRegex.addItemListener(e ->
          input.setBorder(matchRegex.isSelected()
            ? findRegexBorder : findTextBorder));

      JPanel cmd = new JPanel(new WrapLayout());
      cmd.add(go);
      add(cmd);

      ButtonGroup g = new ButtonGroup();
      g.add(inSheet);
      g.add(inCircuit);
      g.add(inProject);
      g.add(inAll);
      inSheet.setSelected(true);

      caseSensitive.setSelected(true);
      g = new ButtonGroup();
      g.add(matchSubstring);
      g.add(matchWord);
      g.add(matchExact);
      g.add(matchRegex);
      matchSubstring.setSelected(true);
    }
  }

  private class ResultPanel extends JList<Result> {
    @SuppressWarnings("unchecked")
    ResultPanel() {
      setModel(model);
      setCellRenderer(new ResultRenderer());
      setFont(getFont().deriveFont(Font.PLAIN));
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }
  }

  private TopPanel top;
  private ResultPanel results;
  private JScrollPane scrollPane;
  private Model model = new Model();

  public FindFrame() {
    super(null);
    top = new TopPanel();
    results = new ResultPanel();

    Container contents = getContentPane();

    GridBagLayout gb = new GridBagLayout();
    GridBagConstraints gc = new GridBagConstraints();

    JPanel boxes = new JPanel();
    boxes.setLayout(gb);
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.weightx = 1;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.gridx = gc.gridy = 0;

    boxes.add(top, gc);

    gc.gridx = 0;
    gc.gridy++;
    gc.fill = GridBagConstraints.BOTH;
    gc.weighty = 1;
    scrollPane = new JScrollPane();
    scrollPane.setViewportView(results);
    // scrollPane.setPreferredSize(new Dimension(250, 300));
    boxes.add(scrollPane, gc);

    contents.add(boxes, BorderLayout.CENTER);
    // contents.setMinimumSize(new Dimension(250, 300));

    top.go.addActionListener(e -> model.update());
    top.field.addActionListener(e -> model.update());
    top.field.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) { model.clearIfNoResults(); }
      public void removeUpdate(DocumentEvent e) { model.clearIfNoResults(); }
      public void changedUpdate(DocumentEvent e) { model.clearIfNoResults(); }
    });

    results.addListSelectionListener(e -> reveal(results.getSelectedValue()));
    results.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          int i = results.locationToIndex(e.getPoint());
          if (i < 0 || i >= model.data.size())
            return;
          reveal(model.data.get(i));
        }
      }
    });

    LocaleManager.addLocaleListener(this);
    localeChanged();
  }

  @Override
  public void localeChanged() {
    setTitle(S.get("findFrameTitle"));
    top.findTextBorder.setTitle(S.get("findTextLabel"));
    top.findRegexBorder.setTitle(S.get("findRegexLabel"));
    top.whereBorder.setTitle(S.get("whereLabel"));
    top.inSheet.setText(S.get("inSheet"));
    top.inCircuit.setText(S.get("inCircuit"));
    top.inProject.setText(S.get("inProject"));
    top.inAll.setText(S.get("inAll"));
    top.inSheet.setToolTipText(S.get("inSheetTip"));
    top.inCircuit.setToolTipText(S.get("inCircuitTip"));
    top.inProject.setToolTipText(S.get("inProjectTip"));
    top.inAll.setToolTipText(S.get("inAllTip"));
    top.howBorder.setTitle(S.get("matchType"));
    top.caseSensitive.setText(S.get("caseSensitive"));
    top.matchSubstring.setText(S.get("matchSubstring"));
    top.matchWord.setText(S.get("matchWord"));
    top.matchExact.setText(S.get("matchExact"));
    top.matchRegex.setText(S.get("matchRegex"));
    top.matchSubstring.setToolTipText(S.get("matchSubstringTip"));
    top.matchWord.setToolTipText(S.get("matchWordTip"));
    top.matchExact.setToolTipText(S.get("matchExactTip"));
    top.matchRegex.setToolTipText(S.get("matchRegexTip"));
    top.options.setTitle(S.get("matchOptions"));

    top.go.setText(S.get("findButtonLabel"));
  }

  // Subcircuit and VHDL entities have an attribute for the underlying factory
  // name (i.e. the circuit or vhdl content). Other components may have a label,
  // like 'Foo', and the display name might be "Register 'Foo'", but nowhere
  // does the word "Register" appear in an attribute. So we use a bogus
  // string for that part.
  private static final String COMPONENT_TYPE = S.get("matchComponentName");

  private static final Pattern newline = Pattern.compile("\\R");
  private class Model extends AbstractListModel {
    String text;
    Pattern regex;

    ArrayList<Result> data = new ArrayList<>();

    void clearIfNoResults() {
      if (data.size() == 1) {
        data.clear();
        fireIntervalRemoved(this, 0, 1);
      }
    }

    @Override
    public Object getElementAt(int index) {
      return data.get(index);
    }

    @Override
    public int getSize() {
      return data.size();
    }

    void update() {
      int n = data.size();
      if (n > 0) {
        data.clear();
        fireIntervalRemoved(this, 0, n);
      }
      add(null); // null displays as result count
      text = top.field.getText();
      if (text.equals(""))
        return;
      int flags = 0;
      flags |= top.caseSensitive.isSelected() ? 0 : Pattern.CASE_INSENSITIVE;
      flags |= Pattern.MULTILINE;
      if (top.matchSubstring.isSelected())
        flags |= Pattern.LITERAL;
      else if (top.matchWord.isSelected())
        text = "\\b" + Pattern.quote(text) + "\\b";
      else if (top.matchExact.isSelected())
        text = "^" + Pattern.quote(text) + "$";
      regex = Pattern.compile(text, flags);
      List<Project> projects = Projects.getOpenProjects();
      if (projects.isEmpty())
        return;
      Project proj = projects.get(0);
      LibrarySource src = new LibrarySource(proj, new ArrayList<Library>(), proj.getLogisimFile());
      if (top.inSheet.isSelected() || top.inCircuit.isSelected()) {
        Circuit circ = proj.getCurrentCircuit();
        HdlModel hdl = proj.getCurrentHdl();
        if (circ != null) {
          String source = proj.getLogisimFile().getName() + ", " + circ.getName();
          CircuitSource circSource = src.forCircuit(circ);
          if (top.inCircuit.isSelected()) {
            searchAttributes(circ.getStaticAttributes(), source, circSource);
            searchCircuit(circ, source, new HashSet<Object>(), circSource);
          } else {
            searchCircuit(circ, source, null, circSource); // non recursive
          }
        } else if (hdl != null) {
          String source = proj.getLogisimFile().getName() + ", " + hdl.getName();
          searchHdl(hdl, source, src.forHdl(hdl));
        }
      } else if (top.inProject.isSelected()) {
        HashSet<Library> searched = new HashSet<>();
        searchLibrary(proj.getLogisimFile(), proj.getLogisimFile().getName(), searched, src);
      } else {
        HashSet<Library> searched = new HashSet<>();
        for (Project p : projects)
          searchLibrary(p.getLogisimFile(), p.getLogisimFile().getName(), searched,
              new LibrarySource(p, new ArrayList<Library>(), p.getLogisimFile()));
      }
    }

    void searchLibrary(Library lib, String source, HashSet<Library> searched,
        LibrarySource src) {
      if (searched.contains(lib))
        return;
      searched.add(lib);
      searchText(lib.getDisplayName(), source, S.get("matchLibraryName"), src, null);
      for (Tool tool : lib.getTools()) {
        String subsource = source + ", " + tool.getDisplayName();
        Source toolSrc = src.forTool(tool);
        searchAttributes(tool.getAttributeSet(), subsource, toolSrc);
        if (!(tool instanceof AddTool))
          continue;
        AddTool t = (AddTool)tool;
        if (t.getFactory() instanceof SubcircuitFactory) {
          Circuit circ = ((SubcircuitFactory)t.getFactory()).getSubcircuit();
          searchCircuit(circ, subsource, null, src.forCircuit(circ)); // non-recursive
        } else if (t.getFactory() instanceof VhdlEntity) {
          VhdlContent vhdl = ((VhdlEntity)t.getFactory()).getContent();
          searchHdl(vhdl, subsource, src.forHdl(vhdl));
        } else {
          searchText(t.getFactory().getDisplayName(),
              subsource, COMPONENT_TYPE, toolSrc, null);
        }
      }
      for (Library sublib : lib.getLibraries()) {
        String subsource = source + ", " + lib.getDisplayName();
        searchLibrary(sublib, subsource, searched, src.forLibrary(sublib));
      }
    }

    void searchCircuit(Circuit circ, String source, HashSet<Object> searched,
        CircuitSource src) {
      if (searched != null && searched.contains(circ))
        return;
      for (Component comp : circ.getNonWires()) {
        String compName = comp.getDisplayName();
        String subsource = source + "/" + compName;
        Source compSrc = src.forComponent(comp);
        searchAttributes(comp.getAttributeSet(), subsource, compSrc);
        if (!(comp.getFactory() instanceof SubcircuitFactory
              || comp.getFactory() instanceof VhdlEntity))
          searchText(comp.getFactory().getDisplayName(),
              subsource, COMPONENT_TYPE, compSrc, null);
      }
      if (searched == null)
        return; // non-recursive
      searched.add(circ);
      for (Component comp : circ.getNonWires()) {
        ComponentFactory factory = comp.getFactory();
        if (factory instanceof VhdlEntity) {
          VhdlContent vhdl = ((VhdlEntity)factory).getContent();
          if (searched.contains(vhdl))
            continue;
          searched.add(vhdl);
          String subsource = source + "/" + comp.getDisplayName();
          searchHdl(vhdl, subsource, src.findHdl(vhdl));
        } else if (factory instanceof SubcircuitFactory) {
          String subsource = source + "/" + comp.getDisplayName();
          Circuit subcirc = ((SubcircuitFactory)factory).getSubcircuit();
          searchCircuit(subcirc, subsource, searched, src.findCircuit(subcirc));
        }
      }
    }

    void searchHdl(HdlModel hdl, String source, HdlSource src) {
      String code = hdl.getContent();
      searchText(code, source, null /*use line number*/, src, null);
    }

    void searchAttributes(AttributeSet as, String source, Source src) {
      if (as == null)
        return;
      for (Attribute<?> a : as.getAttributes()) {
        Object o = as.getValue(a);
        if (o instanceof String && a == Text.ATTR_TEXT)
          searchText((String)o, source, null /* use line number */, src, a);
        else if (o instanceof String)
          searchText((String)o, source, a.getDisplayName(), src, a);
      }
    }

    void searchText(String content, String source, String context, Source src, Attribute a) {
      Matcher newlines = newline.matcher(content);
      Matcher matches = regex.matcher(content);

      int linestart = 0, lineend = content.length(), lineno = 1;
      boolean multiline = newlines.find();
      if (multiline)
        lineend = newlines.end();

      if (context == null && !multiline)
        context = S.get("matchTextContent");

      while (matches.find()) {
        int s = matches.start();
        int e = matches.end();
        if (multiline && s >= lineend) {
          context = null;
          while (s >= lineend) {
            lineno++;
            linestart = lineend;
            lineend = newlines.find() ? newlines.end() : content.length();
          }
        }
        int ls = linestart;
        int lns = lineno;
        if (multiline && e > lineend) {
          context = null;
          while (e > lineend) {
            lineno++;
            linestart = lineend;
            lineend = newlines.find() ? newlines.end() : content.length();
          }
        }
        int lne = lineno;
        int le = lineend;
        if (context == null && lns == lne)
          context = S.fmt("matchTextLine", lns);
        else if (context == null)
          context = S.fmt("matchTextLines", lns, lne);
        add(new Result(content, ls, le, s, e, source, context, lns, src, a));
      }

      // String lines = content.split("\\R", -1);
      // int n = lines.length;
      // if (context == null && lines.length == 0)
      //   context = S.get("matchTextContent");
      // for (int i = i; i < n; i++) {
      //   String line = lines[i];
      //   int lineno = i+1;
      //   Matcher m = regex.matcher(line);
      //   while (m.find()) {
      //     int s = m.start();
      //     int e = m.end();
      //     String c = context != null ? context : S.fmt("matchTextLine", lineno);
      //     add(new Result(line, s, e, source, c, lineno, src, a));
      //   }
      // }
    }

    void add(Result result) {
      data.add(result);
      fireIntervalAdded(this, data.size()-1, data.size());
    }
  }

  // static int lineNumber(String t, int s) {
  //   int i = t.indexOf('\n');
  //   if (i < 0)
  //     return -1;
  //   int n = 1;
  //   while (i >= 0 && i < s) {
  //     n++;
  //     i = t.indexOf('\n', i+1);
  //   }
  //   return n;
  // }

  static String escapeHtml(String s) {
    int n = s.length();
    StringBuilder out = new StringBuilder(Math.max(16, n));
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
        out.append("&#");
        out.append((int) c);
        out.append(';');
      } else if (c == '\n' && !(i == n-1)) {
        out.append("\\n"); // not really html-escape, but probably better for UI
      } else if (c == '\r' && !(i == n-1 || (i == n-2 && s.charAt(i+1) == '\n'))) {
        out.append("\\r"); // not really html-escape, but probably better for UI
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static abstract class Source<E> { 
    Project proj;
    ArrayList<Library> path;
    E leaf;

    Source(Project r, ArrayList<Library> p, E l) {
      proj = r;
      path = p;
      leaf = l;
    }

    abstract void reveal(Result r);
  }

  private static class ToolSource extends Source<Tool> {
    ToolSource(Project proj, ArrayList<Library> libPath, Tool tool) {
      super(proj, libPath, tool);
    }
    public void reveal(Result r) {
      proj.getFrame().revealInExplorer(path, leaf);
    }
  }

  private static class HdlSource extends Source<HdlModel> {
    HdlSource(Project proj, ArrayList<Library> libPath, HdlModel hdl) {
      super(proj, libPath, hdl);
    }
    public void reveal(Result r) {
      if (proj.getCurrentHdl() != leaf)
        proj.setCurrentHdlModel(leaf);
      if (r.lineno < 0)
        return;
      proj.getFrame().getHdlContentView().setCaretPosition(r.s);
    }
  }

  private static class LibrarySource extends Source<Library> {
    ArrayList<Library> subPath;
    LibrarySource(Project proj, ArrayList<Library> libPath, Library lib) {
      super(proj, libPath, lib);
      subPath = new ArrayList<Library>();
      subPath.addAll(libPath);
      subPath.add(lib);
    }
    CircuitSource forCircuit(Circuit circ) {
      return new CircuitSource(proj, subPath, circ);
    }
    HdlSource forHdl(HdlModel hdl) {
      return new HdlSource(proj, subPath, hdl);
    }
    ToolSource forTool(Tool tool) {
      return new ToolSource(proj, subPath, tool);
    }
    LibrarySource forLibrary(Library sublib) {
      return new LibrarySource(proj, subPath, sublib);
    }
    public void reveal(Result r) {
      proj.getFrame().revealInExplorer(path, leaf);
    }
  }

  private static class CircuitSource extends Source<Circuit> {
    CircuitSource(Project proj, ArrayList<Library> libPath, Circuit circ) {
      super(proj, libPath, circ);
    }

    CircuitSource findCircuit(Circuit subcirc) {
      ComponentFactory f = subcirc.getSubcircuitFactory();
      Library lib = path.get(path.size()-1);
      if (lib.contains(f))
        return new CircuitSource(proj, path, subcirc);
      ArrayList<Library> copy = new ArrayList<>();
      copy.addAll(path);
      findFactory(copy, f);
      return new CircuitSource(proj, copy, subcirc);
    }

    HdlSource findHdl(HdlModel hdl) {
      if (!(hdl instanceof VhdlContent))
        throw new IllegalArgumentException("hdl isn't vhdl? " + hdl);
      ComponentFactory f = ((VhdlContent)hdl).getEntityFactory();
      Library lib = path.get(path.size()-1);
      if (lib.contains(f))
        return new HdlSource(proj, path, hdl);
      ArrayList<Library> copy = new ArrayList<>();
      copy.addAll(path);
      findFactory(copy, f);
      return new HdlSource(proj, copy, hdl);
    }

    static boolean findFactory(ArrayList<Library> p, ComponentFactory f) {
      Library lib = p.get(p.size()-1);
      if (lib.contains(f))
        return true;
      for (Library sublib : lib.getLibraries()) {
        p.add(sublib);
        if (findFactory(p, f))
          return true;
        p.remove(sublib);
      }
      return false;
    }

    ComponentSource forComponent(Component comp) {
      return new ComponentSource(proj, path, leaf, comp);
    }

    public void reveal(Result r) {
      proj.getFrame().revealInExplorer(path, leaf);
    }
  }

  private static class ComponentSource extends Source<Component> {
    Circuit circ;
    ComponentSource(Project proj, ArrayList<Library> libPath, Circuit circ, Component comp) {
      super(proj, libPath, comp);
      this.circ = circ;
    }

    public void reveal(Result r) {
      if (proj.getCurrentCircuit() != circ)
        proj.setCurrentCircuit(circ);
      Canvas canvas = proj.getFrame().getCanvas();
      canvas.setHaloedComponent(circ, leaf);
      canvas.zoomScrollTo(leaf.getNominalBounds().toRectangle());
    }
  }

  private static class Result {
    String content, html;
    int ls, le, s, e;
    String source, context;
    Attribute a; // null for Hdl content and library display name
    int lineno; // only for multi-line Text.ATTR_TEXT content
    Source src;

    Result() { }
    Result(String content, int ls, int le, int s, int e, String source, String context, int lineno, Source src, Attribute a) {
      this.content = content;
      this.ls = ls;
      this.le = le;
      this.s = s;
      this.e = e;
      this.source = source;
      this.context = context;
      this.lineno = lineno;
      this.src = src;
      this.a = a;
      int n = content.length();
      String excerpt = (s == 0 && e == n) ? content : content.substring(s, e);
      if (e-s >= 60)
        excerpt = excerpt.substring(0, 20) + " ... " + excerpt.substring(e-s-20, e-s);
      html = "<font color=\"#820a0a\"><b>"
          + escapeHtml(excerpt)
          + "</b></font>";
      if ((s == 0 && e == n) || (s == ls && e == le))
        return;

      n = le - ls;
      int c = Math.min(n+10, 40);
      if (n < 40 || n < (e-s)+10)
        html = escapeHtml(content.substring(ls, s))
            + html
            + escapeHtml(content.substring(e, le));
      else if (s < c/2)
        html = escapeHtml(content.substring(ls, s))
            + html
            + escapeHtml(content.substring(e, e+c/2)) + "...";
      else if (e >= le-c/2)
        html = "..." + escapeHtml(content.substring(s-c/2, s))
            + html
            + escapeHtml(content.substring(e, le));
      else
        html = "..." + escapeHtml(content.substring(s-c/2, s))
            + html
            + escapeHtml(content.substring(e, e+c/2)) + "...";
    }

    String toHtml() {
      return html;
    }

  }

  static final Color color = UIManager.getDefaults().getColor("Table.gridColor");
  static final Border matteBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, color);

  private class ResultRenderer extends DefaultListCellRenderer {
    static final String b = "<font color=\"#1a128e\">";
    static final String d = "</font>";

    @Override
    public java.awt.Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      if (value == null) {
        int n = model.data.size() - 1;
        if (n <= 0)
          value = S.get("zeroResultLabel");
        else
          value = S.fmt("numResultLabel", n);
      } else if (value instanceof Result) {
        Result r = (Result)value;
        String src = S.fmt("matchSource", b + escapeHtml(r.source) + d);
        value = String.format("<html><small>%s:</small><br><div style=\"padding-left: 15px;\">%s: %s</div></html>",
            src, escapeHtml(r.context), r.toHtml());
      }
      java.awt.Component c
          = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (c instanceof JComponent)
        ((JComponent)c).setBorder(matteBorder);
      return c;
    }
  }

  Result revealed = null;
  private void reveal(Result r) {
    revealed = r;
    if (r != null) {
      r.src.proj.getFrame().toFront();
      r.src.proj.getFrame().getCanvas().setHaloedComponent(null, null);
      r.src.reveal(r);
    }
  }

  private void setLocationRelativeTo(Project proj) {
      // Try to place to right of circuit window, or at least near right of screen,
      // using same height as circuit window.
      Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
      Rectangle r = proj.getFrame().getBounds();
      pack();
      int w = getWidth();
      int h = Math.max(getHeight(), r.height);
      int x = r.x + r.width;
      int y = r.y;
      if (x + w > d.width) { // too small to right of circuit
        if (r.x >= w) // plenty of room to left of circuit
          x = r.x - w;
        else if (r.x > d.width - w) // circuit is near right of screen
          x = 0;
        else // circuit is near left of screen
          x = d.width - w;
      }
      setLocation(x, y);
      setMinimumSize(new Dimension(220, 280));
      setSize(new Dimension(w, h));
  }

  public static void showFindFrame(Project proj) {
    FindFrame finder = FindManager.getFindFrame(proj.getFrame());
    if (!finder.isVisible()) {
      finder.setLocationRelativeTo(proj);
      finder.setVisible(true);
    }
    finder.toFront();
  }

  // Search for matching text
  // x Find on current circuit / current vhdl, vs global search
  // - Replace?
  // Things to search:
  // x vhdl entity contents (entity.getContents...)
  // x circuit contents (circ.nonWires...)
  // x circuit name CircuitAttributes.CIRCUIT_NAME
  // x vhdl name VhdlEntity.NAME_ATTR
  // - toolbar contents (layout toolbar getItems)
  // x standard label attributes on various components (except Pin, Tunnel) StdAttr.LABEL
  // x circuit shared label attributes CircuitAttributes.CIRCUIT_REVISION
  // x text components Text.ATTR_TEXT
  // x tunnel names StdAttr.LABEL
  // x pin names StdAttr.LABEL
  // - later: memory data ? as bytes? as strings? as hex?

  // - todo: search appearance elements, toolbars, mouse mappings
  
  // Search for matching components and attributes (e.g. find all 8-wide multiplexors)
  // - Find on current circuit vs global search
  // Things to search:
  // - circuits
  // - toolbar
  // - mouse mappings

  public static void main(String[] args) throws Exception {
    FindFrame frame = new FindFrame();
    frame.setPreferredSize(new Dimension(350, 600));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
  }
}
