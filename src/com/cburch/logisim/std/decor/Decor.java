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

package com.cburch.logisim.std.decor;
import static com.cburch.logisim.std.Strings.S;

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.FactoryDescription;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class Decor extends Library {

  private static FactoryDescription plainDesc = 
    new FactoryDescription("Text", S.getter("textComponentPlain"), "comment.png", "Text") {
      @Override
      public ComponentFactory getFactoryFromLibrary(Class<? extends Library> libClass) {
        return Text.FACTORY;
      }
      @Override
      public boolean isFactoryLoaded() { return true; }
    };
  private static FactoryDescription wrappedDesc = 
    new FactoryDescription("TextWrapped", S.getter("textComponentWrapped"), "wrappedText.png", "Text") {
      @Override
      public ComponentFactory getFactoryFromLibrary(Class<? extends Library> libClass) {
        return Text.FACTORY;
      }
      @Override
      public boolean isFactoryLoaded() { return true; }
    };
  private static FactoryDescription markdownishDesc = 
    new FactoryDescription("TextMarkdownish", S.getter("textComponentMarkdownish"), "markdownish.png", "Text") {
      @Override
      public ComponentFactory getFactoryFromLibrary(Class<? extends Library> libClass) {
        return Text.FACTORY;
      }
      @Override
      public boolean isFactoryLoaded() { return true; }
    };

  private static Tool[] TOOLS = {
    new AddTool(Decor.class, plainDesc),
    new AddTool(Decor.class, wrappedDesc),
    new AddTool(Decor.class, markdownishDesc),
    new AddTool(Decor.class, new FactoryDescription("Callout", S.getter("calloutComponent"), "callout.png", "Callout")),
    new AddTool(Decor.class, new FactoryDescription("Image", S.getter("stdImageComponent"), "image.gif", "Image")),
    new AddTool(Decor.class, new FactoryDescription("Hyperlink", S.getter("hyperlinkComponent"), "hyperlink.png", "Hyperlink")),
  };

  static {
    // TOOLS[0] is plain text (default)
    plainDesc.setToolTip(S.getter("textComponentPlainTip"));
    TOOLS[0].getAttributeSet().setAttr(Text.ATTR_FORMAT, Text.TEXT_FORMAT_PLAIN);
    TOOLS[0].getAttributeSet().setReadOnly(Text.ATTR_FORMAT, true);

    // TOOLS[1] is wrapped text
    wrappedDesc.setToolTip(S.getter("textComponentWrappedTip"));
    TOOLS[1].getAttributeSet().setAttr(Text.ATTR_FORMAT, Text.TEXT_FORMAT_WRAPPED);
    TOOLS[1].getAttributeSet().setReadOnly(Text.ATTR_FORMAT, true);

    // TOOLS[2] is markdownish text
    markdownishDesc.setToolTip(S.getter("textComponentMarkdownishTip"));
    TOOLS[2].getAttributeSet().setAttr(Text.ATTR_FORMAT, Text.TEXT_FORMAT_MARKDOWNISH);
    TOOLS[2].getAttributeSet().setReadOnly(Text.ATTR_FORMAT, true);
  }

  private List<Tool> tools = null;

  public Decor() { }

  @Override
  public String getDisplayName() {
    return S.get("decorLibrary");
  }

  @Override
  public String getName() {
    return "Decor";
  }

  @Override
  public List<Tool> getTools() {
    if (tools == null)
      tools = Arrays.asList(TOOLS);
    return tools;
  }

}
