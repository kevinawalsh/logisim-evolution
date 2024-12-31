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

package com.bfh.logisim.settings;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.bfh.logisim.download.FPGADownload;
import com.bfh.logisim.download.GowinDownload;
import com.bfh.logisim.download.LatticeDownload;
import com.bfh.logisim.gui.FPGASettingsDialog;
import com.cburch.logisim.util.Errors;

public class Settings {

  public static final String VHDL = "VHDL";
  public static final String VERILOG = "Verilog";

  private static final String WorkSpace = "WorkSpace";
  private static final String WorkPath = "WorkPath";
  private static final String WorkPathName = "logisim_fpga_workspace" + File.separator;

  private static final String XilinxToolsPath = "XilinxToolsPath";
  // AlteraToolsPath can be a local directory, in which case we look for
  // programs like quartus_pgm and quartus_map in that directory.
  // Or, AlteraToolsPath can be a local filename, which is used as a single
  // executable script or program to do synthesis.
  // or, AlteraToolsPath can be a URL (starting with http:// or https://) in
  // which case a web API is used.
  private static final String AlteraToolsPath = "AlteraToolsPath";
  private static final String GowinShPath = "GowinShPath";
  private static final String GowinProgPath = "GowinProgPath";
  private static final String Altera64Bit = "Altera64Bit";
  private static final String LatticeToolsPath = "LatticeToolsPath";
  private static final String ApioToolsPath = "ApioToolsPath";
  private static final String OpenFPGAloaderPath = "openFPGAloaderPath";
  private static final String RawBinaryFormat = "RawBinaryFormat";
  private static final String HDLTypeToGenerate = "HDLTypeToGenerate";
  private static final String FPGABoards = "FPGABoards";
  private static final String SelectedBoard = "SelectedBoard";
  private static final String ExternalBoard = "ExternalBoardFile_";

  private String HomePath;
  private String SharedPath;
  private String SettingsElement = "LogisimFPGASettings";
  private String UserSettingsFileName = ".LogisimFPGASettings.xml";
  private String SharedSettingsFileName = "LogisimFPGASettings.xml";
  private String LoadedSettingsFileName = "";
  private Document SettingsDocument;
  private boolean modified = false;
  private BoardList KnownBoards = new BoardList();
  private ArrayList<Listener> listeners = new ArrayList<>();

  private static Settings singleton;

  public static Settings getSettings() {
    if (singleton == null)
      singleton = new Settings();
    return singleton;
  }

  private Settings() {
    HomePath = System.getProperty("user.home");
    SharedPath = "";
    try {
      String path = Settings.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      String decodedPath = URLDecoder.decode(path, "UTF-8");
      SharedPath = new File(decodedPath).getParent();
    } catch (UnsupportedEncodingException e) {
    }

    if (!readFrom(HomePath, UserSettingsFileName)) {
      readFrom(SharedPath, SharedSettingsFileName);
    }

    if (!settingsComplete())
      writeXml();
  }

  public void addSettingsListener(Listener l) {
    listeners.add(l);
  }

  public void removeSettingsListener(Listener l) {
    listeners.remove(l);
  }

  public static interface Listener {
    public void fpgaSettingsChanged();
  }

  private static FPGASettingsDialog dialog;
  public static void doSettingsDialog(JFrame parentFrame) {
    if (dialog != null) {
      dialog.toFront();
    } else {
      Settings s = getSettings();
      dialog = new FPGASettingsDialog(parentFrame, s);
      dialog.doDialog();
      dialog = null;
    }
  }

  public void notifyListeners() {
    for (Listener l : listeners)
      l.fpgaSettingsChanged();
  }

  private boolean readFrom(String dir, String name) {
    File SettingsFile = new File(join(dir, name));
    if (!SettingsFile.exists())
      return false;
    LoadedSettingsFileName = SettingsFile.getPath();
    try {
      // Create instance of DocumentBuilderFactory
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // Get the DocumentBuilder
      DocumentBuilder parser = factory.newDocumentBuilder();
      // Create blank DOM Document
      SettingsDocument = parser.parse(SettingsFile);
    } catch (Exception e) {
      Errors.title("FPGA Settings").show("Can't open FPGA settings file: "
          + SettingsFile.getPath(), e);
      return false;
    }
    return true;
  }

  private String join(String path, String name) {
    if (path.endsWith(File.separator))
      return path + name;
    else
      return path + File.separator + name;
  }

  private String getAttribute(String nodeName, String attrName, String defValue) {
    Element e = (Element)SettingsDocument.getElementsByTagName(nodeName).item(0);
    Attr a = e.getAttributeNode(attrName);
    if (a != null)
      return a.getNodeValue();
    a = SettingsDocument.createAttribute(attrName);
    a.setNodeValue(defValue);
    e.setAttributeNode(a);
    modified = true;
    return defValue;
  }

  private void setAttribute(String nodeName, String attrName, String value) {
    if (value == null)
      value = "";
    Element e = (Element)SettingsDocument.getElementsByTagName(nodeName).item(0);
    Attr a = e.getAttributeNode(attrName);
    if (a != null && a.getNodeValue().equals(value)) {
      return;
    }
    if (a == null) {
      a = SettingsDocument.createAttribute(attrName);
      e.setAttributeNode(a);
    }
    a.setNodeValue(value);
    modified = true;
  }

  private String normalizePath(String path) {
    if (path == null || path.isEmpty())
      return null;
    if (path.length() > 1 && path.endsWith(File.separator))
      path = path.substring(0, path.length() - 1);
    return path;
  }

  public String GetAlteraToolPath() {
    String s = getAttribute(WorkSpace, AlteraToolsPath, "");
    return normalizePath(s);
  }

  public String GetXilinxToolPath() {
    String s = getAttribute(WorkSpace, XilinxToolsPath, "");
    return normalizePath(s);
  }

  public String GetGowinShPath() {
    String s = getAttribute(WorkSpace, GowinShPath, "");
    return normalizePath(s);
  }

  public String GetGowinProgPath() {
    String s = getAttribute(WorkSpace, GowinProgPath, "");
    return normalizePath(s);
  }

  public String GetLatticeToolPath() {
    String s = getAttribute(WorkSpace, LatticeToolsPath, "");
    return normalizePath(s);
  } 

  public String GetApioToolPath() {
    String s = getAttribute(WorkSpace, ApioToolsPath, "");
    return normalizePath(s);
  }

  public String GetOpenFPGALoaderPath() {
    String s = getAttribute(WorkSpace, OpenFPGAloaderPath, "");
    return normalizePath(s);
  }

  public boolean SetAlteraToolPath(String path) {
    path = normalizePath(path);
    if (!validAlteraToolPath(path))
      return false;
    setAttribute(WorkSpace, AlteraToolsPath, path);
    return true;
  }

  public boolean validAlteraToolPath(String path) {
    path = normalizePath(path);
    return path == null
      || path.toLowerCase().startsWith("http://")
      || path.toLowerCase().startsWith("https://")
      || allToolsPresent(path, FPGADownload.ALTERA_PROGRAMS)
      || isExecutableScript(path);
  }

  public boolean SetXilinxToolPath(String path) {
    path = normalizePath(path);
    if (!validXilinxToolPath(path))
      return false;
    setAttribute(WorkSpace, XilinxToolsPath, path);
    return true;
  }

  public boolean SetGowinShPath(String path) {
    path = normalizePath(path);
    setAttribute(WorkSpace, GowinShPath, path);
    return true;
  }

  public boolean SetGowinProgPath(String path) {
    path = normalizePath(path);
    setAttribute(WorkSpace, GowinProgPath, path);
    return true;
  }

  public boolean validXilinxToolPath(String path) {
    path = normalizePath(path);
    return path == null
      || allToolsPresent(path, FPGADownload.XILINX_PROGRAMS)
      || isExecutableScript(path);
  }

  public boolean SetLatticeToolPath(String path) {
    path = normalizePath(path);
    if (!validLatticeToolPath(path))
      return false;
    setAttribute(WorkSpace, LatticeToolsPath, path);
    return true;
  }

  public boolean validLatticeToolPath(String path) {
    path = normalizePath(path);
    return path == null
      || LatticeDownload.getToolChainType(path) != LatticeDownload.TOOLCHAIN.UNKNOWN;
  }

  public boolean validGowinToolPath(String path) {
    path = normalizePath(path);
    return path == null
      || isExecutableScript(path + File.separator + FPGADownload.GOWIN_SH);
  }

  public boolean SetApioToolPath(String path) {
    path = normalizePath(path);
    if (!validApioToolPath(path))
      return false;
    setAttribute(WorkSpace, ApioToolsPath, path);
    return true;
  }

  public boolean validApioToolPath(String path) {
    path = normalizePath(path);
    return path == null
      || allToolsPresent(path, FPGADownload.APIO_PROGRAMS);
  }

  public boolean SetOpenFPGAloaderPath(String path) {
    path = normalizePath(path);
    if (!validOpenFPGAloaderPath(path))
      return false;
    setAttribute(WorkSpace, OpenFPGAloaderPath, path);
    return true;
  }

  public boolean validOpenFPGAloaderPath(String path) {
    path = normalizePath(path);
    return path == null
      || isExecutableScript(path);
  }

  public boolean GetUseRBF() {
    String s = getAttribute(WorkSpace, RawBinaryFormat, "false");
    return "true".equalsIgnoreCase(s);
  }

  public void SetUseRBF(boolean enable) {
    setAttribute(WorkSpace, RawBinaryFormat, ""+enable);
  }

  public Collection<String> GetBoardNames() {
    return KnownBoards.GetBoardNames();
  }

  public boolean GetAltera64Bit() {
    String s = getAttribute(WorkSpace, Altera64Bit, "true");
    return "true".equalsIgnoreCase(s);
  }

  public void SetAltera64Bit(boolean enable) {
    setAttribute(WorkSpace, Altera64Bit, ""+enable);
  }

  public String GetHDLType() {
    String s = getAttribute(WorkSpace, HDLTypeToGenerate, VHDL);
    if (VHDL.equalsIgnoreCase(s))
      return VHDL;
    if (VERILOG.equalsIgnoreCase(s))
      return VERILOG;
    setAttribute(WorkSpace, HDLTypeToGenerate, VHDL); // correct broken XML value
    return VHDL;
  }

  public void SetHDLType(String lang) {
    if (VHDL.equalsIgnoreCase(lang))
      setAttribute(WorkSpace, HDLTypeToGenerate, VHDL);
    else if (VERILOG.equalsIgnoreCase(lang))
      setAttribute(WorkSpace, HDLTypeToGenerate, VERILOG);
  }

  public String GetSelectedBoard() {
    String defBoard = KnownBoards.GetBoardNames().get(0);
    String s = getAttribute(FPGABoards, SelectedBoard, defBoard);
    if (KnownBoards.BoardInCollection(s))
      return s;
    setAttribute(FPGABoards, SelectedBoard, defBoard); // correct broken XML value
    return defBoard;
  }

  private Element GetBoardSettings(String board) {
    Element e = (Element)SettingsDocument.getElementsByTagName(FPGABoards).item(0);
    NodeList nodes = e.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node n = nodes.item(i);
      if (!(n instanceof Element))
        continue;
      Element e2 = (Element)n;
      if ("Settings".equals(e2.getTagName()) && board.equals(e2.getAttribute("Board")))
        return e2;
    }
    return null;
  }

  static boolean warnedBadToolchain = false;
  static boolean warnedBadHDLType = false;

  public String GetPreferredToolchain(String board) {
    String pref = GetBoardPreferences(board, "Toolchain");
    String toolchain = FPGADownload.normalizeToolchain(pref);
    if (pref != null && toolchain == null) {
      if (!warnedBadToolchain) {
        warnedBadToolchain = true;
        JOptionPane.showMessageDialog(null,
            "Error: Unrecognized toolchain '"+pref+"' in LogisimFPGASettings.xml");
      }
    }
    return toolchain;
  }

  public String GetPreferredHDLType(String board) {
    String pref = GetBoardPreferences(board, "HDLTypeToGenerate");
    if (VHDL.equalsIgnoreCase(pref))
      return VHDL;
    if (VERILOG.equalsIgnoreCase(pref))
      return VERILOG;
    if (pref != null && !warnedBadHDLType) {
      warnedBadHDLType = true;
      JOptionPane.showMessageDialog(null,
          "Error: Unrecognized HDL type '"+pref+"' in LogisimFPGASettings.xml");
    }
    return null;
  }

  private String GetBoardPreferences(String board, String attr) {
    Element e = GetBoardSettings(board);
    if (e == null)
      return null;
    String val = e.getAttribute(attr);
    if (val == null || val.trim().equals(""))
      return null;
    return val;
  }

  public void SetPreferredToolchain(String board, String toolchain) {
    SetBoardPreferences(board, "Toolchain", toolchain);
  }

  public void SetPreferredHDLType(String board, String hdlType) {
    SetBoardPreferences(board, "HDLTypeToGenerate", hdlType);
  }

  private void SetBoardPreferences(String board, String attr, String val) {
    Element e = GetBoardSettings(board);
    if (e == null) {
      Element p = (Element)SettingsDocument.getElementsByTagName(FPGABoards).item(0);
      e = SettingsDocument.createElement("Settings");
      p.appendChild(e);
      Attr b = SettingsDocument.createAttribute("Board");
      b.setNodeValue(board);
      e.setAttributeNode(b);
    }
    Attr a = e.getAttributeNode(attr);
    if (a == null) {
      a = SettingsDocument.createAttribute(attr);
    }
    a.setNodeValue(val);
    e.setAttributeNode(a);
    modified = true;
  }

  public boolean SetSelectedBoard(String boardName) {
    if (!KnownBoards.BoardInCollection(boardName))
      return false;
    setAttribute(FPGABoards, SelectedBoard, boardName);
    return true;
  }

  public String GetSelectedBoardFileName() {
    String SelectedBoardName = GetSelectedBoard();
    return KnownBoards.GetBoardFilePath(SelectedBoardName);
  }

  public String GetStaticWorkspacePath() {
    String s = getAttribute(WorkSpace, WorkPath, "");
    return normalizePath(s);
  }

  public void SetStaticWorkspacePath(String path) {
    path = normalizePath(path);
    setAttribute(WorkSpace, WorkPath, path);
  }

  public String GetWorkspacePath(File projectFile) {
    String p = GetStaticWorkspacePath();
    if (p != null)
      return p;
    if (projectFile != null) {
      String dir = projectFile.getAbsoluteFile().getParentFile().getAbsolutePath();
      String name = projectFile.getName();
      name = name.replaceAll(".circ.xml$", "").replaceAll(".circ$", "") + "_fpga_workspace";
      return join(dir, name);
    }
    return join(HomePath, WorkPathName);
  }

  private void ensureExactlyOneNode(String nodeName) {
    NodeList nodes = SettingsDocument.getElementsByTagName(nodeName);
    int n = nodes.getLength();
    if (n == 0) {
      Element e = SettingsDocument.createElement(nodeName);
      SettingsDocument.getDocumentElement().appendChild(e);
      modified = true;
    } else if (n > 1) {
      JOptionPane.showMessageDialog(null,
          "FPGA settings file is corrupted, some settings may be lost: " 
          + LoadedSettingsFileName);
      while (n > 1) {
        nodes.item(1).getParentNode().removeChild(nodes.item(1));
        nodes = SettingsDocument.getElementsByTagName(nodeName);
        n = nodes.getLength();
      }
      modified = true;
    }
  }

  private boolean settingsComplete() {
    boolean missingXML = (SettingsDocument == null);
    if (missingXML) { 
      try {
        // Create instance of DocumentBuilderFactory
        DocumentBuilderFactory factory = DocumentBuilderFactory
            .newInstance();
        // Get the DocumentBuilder
        DocumentBuilder parser;
        parser = factory.newDocumentBuilder();
        // Create blank DOM Document
        SettingsDocument = parser.newDocument();
      } catch (ParserConfigurationException e) {
        JOptionPane.showMessageDialog(null,
            "Fatal Error: Cannot create settings XML Document");
        System.exit(-4);
      }
      Element root = SettingsDocument.createElement(SettingsElement);
      SettingsDocument.appendChild(root);
    }

    ensureExactlyOneNode(WorkSpace);
    ensureExactlyOneNode(FPGABoards);

    Element e = (Element)SettingsDocument.getElementsByTagName(FPGABoards).item(0);
    NamedNodeMap attrs = e.getAttributes();
    for (int i = 0; i < attrs.getLength(); i++) {
      String k = attrs.item(i).getNodeName();
      String v = attrs.item(i).getNodeValue();
      if (k.startsWith(ExternalBoard) && new File(v).exists())
        KnownBoards.AddExternalBoard(v);
    }

    GetStaticWorkspacePath();
    GetXilinxToolPath();
    GetAlteraToolPath();
    GetGowinShPath();
    GetOpenFPGALoaderPath();
    GetHDLType();
    GetAltera64Bit();

    GetSelectedBoard();

    return !missingXML && !modified;
  }

  public void AddExternalBoard(String filename) {
    Element e = (Element)SettingsDocument.getElementsByTagName(FPGABoards).item(0);
    int id = 1;
    while (e.getAttributeNode(ExternalBoard+id) != null)
      id++;
    setAttribute(FPGABoards, ExternalBoard+id, filename);
    KnownBoards.AddExternalBoard(filename);
  }

  public boolean UpdateSettingsFile() {
    if (!modified)
      return true;
    return writeXml();
  }

  private boolean writeXml() {
    File UserSettingsFile = new File(join(HomePath, UserSettingsFileName));
    if (!UserSettingsFile.exists()) {
      try {
        writeTo(SharedPath, SharedSettingsFileName);
        return true;
      } catch (Exception e) { }
    }
    try {
      writeTo(HomePath, UserSettingsFileName);
      return true;
    } catch (Exception e) {
      Errors.title("FPGA Settings").show("Can't write FPGA settings file: "
          + UserSettingsFileName, e);
      return false;
    }
  }
  
  private void removeWhitespace(Element e) {
    NodeList nodes = e.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node n = nodes.item(i);
      if (n.getNodeName().equalsIgnoreCase("#text"))
          e.removeChild(n);
      else if (n instanceof Element)
        removeWhitespace((Element)n);
    }
  }

  private void writeTo(String dir, String name) throws Exception {
    removeWhitespace(SettingsDocument.getDocumentElement());
    File SettingsFile = new File(join(dir, name));
    TransformerFactory tranFactory = TransformerFactory.newInstance();
    tranFactory.setAttribute("indent-number", 3);
    Transformer aTransformer = tranFactory.newTransformer();
    aTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
    Source src = new DOMSource(SettingsDocument);
    Result dest = new StreamResult(SettingsFile);
    aTransformer.transform(src, dest);
    modified = false;
  }

  private boolean allToolsPresent(String path, String[] progNames) {
    for (String prog: progNames) {
      File test = new File(join(path, prog));
      if (!test.exists())
        return false;
    }
    return true;
  }

  private boolean isExecutableScript(String path) {
    File test = new File(path);
    return test.exists() && !test.isDirectory() && test.canExecute();
  }
}
