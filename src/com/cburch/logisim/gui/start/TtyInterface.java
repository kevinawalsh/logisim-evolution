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

package com.cburch.logisim.gui.start;
import static com.cburch.logisim.gui.start.Strings.S;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.circuit.Analyze;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.FileStatistics;
import com.cburch.logisim.file.LoadCanceledByUser;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.ExportImage;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.io.Keyboard;
import com.cburch.logisim.std.io.Tty;
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.UniquelyNamedThread;

public class TtyInterface {

  // It's possible to avoid using the separate thread using
  // System.in.available(),
  // but this doesn't quite work because on some systems, the keyboard input
  // is not interactively echoed until System.in.read() is invoked.
  private static class StdinThread extends UniquelyNamedThread {
    private LinkedList<char[]> queue; // of char[]

    public StdinThread() {
      super("TtyInterface-StdInThread");
      queue = new LinkedList<char[]>();
    }

    public char[] getBuffer() {
      synchronized (queue) {
        if (queue.isEmpty()) {
          return null;
        } else {
          return queue.removeFirst();
        }
      }
    }

    @Override
    public void run() {
      InputStreamReader stdin = new InputStreamReader(System.in);
      char[] buffer = new char[32];
      while (true) {
        try {
          int nbytes = stdin.read(buffer);
          if (nbytes > 0) {
            char[] add = new char[nbytes];
            System.arraycopy(buffer, 0, add, 0, nbytes);
            synchronized (queue) {
              queue.addLast(add);
            }
          }
        } catch (IOException e) {
        }
      }
    }
  }

  private static int countDigits(int num) {
    int digits = 1;
    int lessThan = 10;
    while (num >= lessThan) {
      digits++;
      lessThan *= 10;
    }
    return digits;
  }

  private static void displaySpeed(long tickCount, long elapse) {
    double hertz = (double) tickCount / elapse * 1000.0;
    double precision;
    if (hertz >= 100)
      precision = 1.0;
    else if (hertz >= 10)
      precision = 0.1;
    else if (hertz >= 1)
      precision = 0.01;
    else if (hertz >= 0.01)
      precision = 0.0001;
    else
      precision = 0.0000001;
    hertz = (int) (hertz / precision) * precision;
    String hertzStr = hertz == (int) hertz ? "" + (int) hertz : "" + hertz;
    System.out.println(S.fmt("ttySpeedMsg", hertzStr, tickCount, elapse));
  }

  private static void displayStatistics(LogisimFile file) {
    FileStatistics stats = FileStatistics.compute(file,
        file.getMainCircuit());
    FileStatistics.Count total = stats.getTotalWithSubcircuits();
    int maxName = 0;
    for (FileStatistics.Count count : stats.getCounts()) {
      int nameLength = count.getFactory().getDisplayName().length();
      if (nameLength > maxName)
        maxName = nameLength;
    }
    String fmt = "%" + countDigits(total.getUniqueCount()) + "d\t" + "%"
        + countDigits(total.getRecursiveCount()) + "d\t";
    String fmtNormal = fmt + "%-" + maxName + "s\t%s\n";
    for (FileStatistics.Count count : stats.getCounts()) {
      Library lib = count.getLibrary();
      String libName = lib == null ? "-" : lib.getDisplayName();
      System.out.printf(fmtNormal, // OK
          Integer.valueOf(count.getUniqueCount()), Integer
          .valueOf(count.getRecursiveCount()), count
          .getFactory().getDisplayName(), libName);
    }
    FileStatistics.Count totalWithout = stats.getTotalWithoutSubcircuits();
    System.out.printf(
        fmt + "%s\n", // OK
        Integer.valueOf(totalWithout.getUniqueCount()),
        Integer.valueOf(totalWithout.getRecursiveCount()),
        S.get("statsTotalWithout"));
    System.out.printf(
        fmt + "%s\n", // OK
        Integer.valueOf(total.getUniqueCount()),
        Integer.valueOf(total.getRecursiveCount()),
        S.get("statsTotalWith"));
  }

  private static boolean displayTableRow(boolean showHeader, ArrayList<Value> prevOutputs,
      ArrayList<Value> curOutputs, ArrayList<String> headers, ArrayList<String> formats, int format) {
    boolean shouldPrint = false;
    if (prevOutputs == null) {
      shouldPrint = true;
    } else {
      for (int i = 0; i < curOutputs.size(); i++) {
        Value a = prevOutputs.get(i);
        Value b = curOutputs.get(i);
        if (!a.equals(b)) {
          shouldPrint = true;
          break;
        }
      }
    }
    if (shouldPrint) {
      String sep;
      if ((format & FORMAT_TABLE_TABBED) != 0)
        sep = "\t";
      else if ((format & FORMAT_TABLE_CSV) != 0)
        sep = ",";
      else // if ((format & FORMAT_TABLE_PRETTY) != 0)
        sep = " ";
      if (showHeader) {
        for (int i = 0; i < headers.size(); i++) {
          if ((format & FORMAT_TABLE_TABBED) != 0)
            formats.add("%s");
          else if ((format & FORMAT_TABLE_CSV) != 0)
            formats.add("%s");
          else { // if ((format & FORMAT_TABLE_PRETTY) != 0)
            int w = headers.get(i).length();
            w = Math.max(w, valueFormat(curOutputs.get(i), format).length());
            formats.add("%" + w + "s");
          }
        }
        for (int i = 0; i < headers.size(); i++) {
          if (i != 0)
            System.out.print(sep); // OK
          System.out.printf(formats.get(i), headers.get(i)); // OK
        }
        System.out.println(); // OK
      }
      for (int i = 0; i < curOutputs.size(); i++) {
        if (i != 0)
          System.out.print(sep); // OK
        System.out.printf(formats.get(i), valueFormat(curOutputs.get(i), format)); // OK
      }
      System.out.println(); // OK
    }
    return shouldPrint;
  }

  private static String valueFormat(Value v, int format) {
    if ((format & FORMAT_TABLE_BIN) != 0) {
      // everything in binary
      return v.toString();
    } else if ((format & FORMAT_TABLE_HEX) != 0) {
      // everything thing in hex, no prefixes
      return v.toHexString();
    } else {
      // under 6 bits or less in binary, no spaces
      // otherwise in hex, with prefix
      if (v.getWidth() <= 6)
        return v.toBinaryString();
      else
        return "0x" + v.toHexString();
    }
  }

  private static void ensureLineTerminated() {
    if (!lastIsNewline) {
      lastIsNewline = true;
      System.out.print('\n'); // OK
    }
  }

  private static boolean loadRam(CircuitState circState, File loadFile)
      throws IOException {
    if (loadFile == null)
      return false;

    boolean found = false;
    for (Component comp : circState.getCircuit().getNonWires()) {
      if (comp.getFactory() instanceof Ram) {
        Ram ramFactory = (Ram) comp.getFactory();
        InstanceState ramState = circState.getInstanceState(comp);
        MemContents m = ramFactory.getContents(ramState);
        HexFile.open(m, loadFile);
        found = true;
      }
    }

    for (CircuitState sub : circState.getSubstates()) {
      found |= loadRam(sub, loadFile);
    }
    return found;
  }

  private static boolean prepareForTty(CircuitState circState,
      ArrayList<InstanceState> keybStates) {
    boolean found = false;
    for (Component comp : circState.getCircuit().getNonWires()) {
      Object factory = comp.getFactory();
      if (factory instanceof Tty) {
        Tty ttyFactory = (Tty) factory;
        InstanceState ttyState = circState.getInstanceState(comp);
        ttyFactory.sendToStdout(ttyState);
        found = true;
      } else if (factory instanceof Keyboard) {
        keybStates.add(circState.getInstanceState(comp));
        found = true;
      }
    }

    for (CircuitState sub : circState.getSubstates()) {
      found |= prepareForTty(sub, keybStates);
    }
    return found;
  }

  public static void run(Startup args) {
    File fileToOpen = args.getFilesToOpen().get(0);
    Loader loader = new Loader(null);
    LogisimFile.FileWithSimulations file;
    try {
      file = loader.openLogisimFile(fileToOpen, args.getSubstitutions());
    } catch (LoadCanceledByUser e) {
      System.out.println(S.fmt("ttyLoadCanceled", fileToOpen.getName()));
      System.exit(-1);
      return;
    } catch (LoadFailedException e) {
      System.out.println(S.fmt("ttyLoadError", fileToOpen.getName()));
      System.exit(-1);
      return;
    }

    int ret = 0;
    if (args.headlessList) {
      ret = doList(file);
    }
    if (ret == 0 && args.headlessPng) {
      ret = doPng(args.headlessPngCircuits, file);
    }
    if (ret == 0 && args.headlessTty) {
      ret = doTty(args.getTtyFormat(), args.getLoadFile(), file, args.getCircuitToTest(),
          args.getTtyRandomHead(), args.getTtyRandomBody(), args.getTtyRandomTail());
    }
    System.exit(ret);
  }

  static int doList(LogisimFile.FileWithSimulations file) {
    for (Circuit c : file.file.getCircuits()) {
      System.out.println(c);
    }
    return 0;
  }

  static int doPng(String names[], LogisimFile.FileWithSimulations file) {
    double scale = 1.0;
    String format = ExportImage.FORMAT_PNG;
    Project proj = new Project(file);
    Canvas canvas = new Canvas(proj);
    int err = 0;
    for (Circuit c : file.file.getCircuits()) {
      for (String n : names) {
        if (!n.trim().equals("*") && !n.trim().equals(c.toString()))
          continue;
        File dest = new File(c.toString() + ".png");
        System.out.println("Exporting " + c + " as " + dest);
        String msg = ExportImage.exportImage(canvas, c, scale, true, dest, format, null);
        if (msg != null) {
          System.err.println(msg);
          err = 1;
        }
      }
    }
    return err;
  }

  static int doTty(int format, File loadfile, LogisimFile.FileWithSimulations file, String circuitToTest,
      int head, int body, int tail) {
    if ((format & FORMAT_STATISTICS) != 0) {
      format &= ~FORMAT_STATISTICS;
      displayStatistics(file.file);
    }
    if (format == 0) { // no simulation remaining to perform, so just exit
      System.exit(0);
    }

    Project proj = new Project(file);
    Circuit circuit;
    if (circuitToTest == null || circuitToTest.length() == 0) {
      circuit = file.file.getMainCircuit();
    } else {
      circuit = file.file.getCircuit(circuitToTest);
    }
    Map<Instance, String> pinNames = Analyze.getPinLabels(circuit);
    ArrayList<Instance> outputPins = new ArrayList<Instance>();
    ArrayList<Instance> inputPins = new ArrayList<Instance>();
    Instance haltPin = null;
    for (Map.Entry<Instance, String> entry : pinNames.entrySet()) {
      Instance pin = entry.getKey();
      String pinName = entry.getValue();
      if (Pin.FACTORY.isInputPin(pin)) {
        inputPins.add(pin);
      } else {
        outputPins.add(pin);
        if (pinName.equals("halt")) {
          haltPin = pin;
        }
      }
    }
    if (haltPin == null && (format & FORMAT_TABLE) != 0) {
      doTableAnalysis(proj, circuit, pinNames, format, head, body, tail);
      return 0;
    }

    CircuitState circState = CircuitState.createRootState(proj, circuit);
    // we have to do our initial propagation before the simulation starts -
    // it's necessary to populate the circuit with substates.
    circState.getPropagator().propagate();
    if (loadfile != null) {
      try {
        boolean loaded = loadRam(circState, loadfile);
        if (!loaded) {
          System.out.println(S.get("loadNoRamError"));
          System.exit(-1);
        }
      } catch (IOException e) {
        System.out.println(S.get("loadIoError") + ": " + e.toString());
        System.exit(-1);
      }
    }
    int simCode = runSimulation(circState, outputPins, pinNames, haltPin, format);
    return simCode;
  }

  private static int runSimulation(CircuitState circState,
      ArrayList<Instance> outputPins, Map<Instance, String> pinNames,
      Instance haltPin, int format) {
    boolean showTable = (format & FORMAT_TABLE) != 0;
    boolean showSpeed = (format & FORMAT_SPEED) != 0;
    boolean showTty = (format & FORMAT_TTY) != 0;
    boolean showHalt = (format & FORMAT_HALT) != 0;

    ArrayList<InstanceState> keyboardStates = null;
    StdinThread stdinThread = null;
    if (showTty) {
      keyboardStates = new ArrayList<InstanceState>();
      boolean ttyFound = prepareForTty(circState, keyboardStates);
      if (!ttyFound) {
        System.out.println(S.get("ttyNoTtyError"));
        System.exit(-1);
      }
      if (keyboardStates.isEmpty()) {
        keyboardStates = null;
      } else {
        stdinThread = new StdinThread();
        stdinThread.start();
      }
    }

    int retCode;
    long tickCount = 0;
    long start = System.currentTimeMillis();
    boolean halted = false;
    ArrayList<Value> prevOutputs = null;
    ArrayList<String> headers = new ArrayList<String>();
    ArrayList<String> formats = new ArrayList<String>();
    Propagator prop = circState.getPropagator();
    boolean needTableHeader = true;
    for (Instance pin : outputPins) {
      if (pin == haltPin)
        continue;
      headers.add(pinNames.get(pin));
    }
    while (true) {
      ArrayList<Value> curOutputs = new ArrayList<Value>();
      for (Instance pin : outputPins) {
        InstanceState pinState = circState.getInstanceState(pin);
        Value val = Pin.FACTORY.getValue(pinState);
        if (pin == haltPin) {
          halted |= val.equals(Value.TRUE);
        } else if (showTable) {
          curOutputs.add(val);
        }
      }
      if (showTable) {
        if (displayTableRow(needTableHeader, prevOutputs, curOutputs, headers, formats, format))
          needTableHeader = false;
      }

      if (halted) {
        retCode = 0; // normal exit
        break;
      }
      if (prop.isOscillating()) {
        retCode = 1; // abnormal exit
        break;
      }
      if (keyboardStates != null) {
        char[] buffer = stdinThread.getBuffer();
        if (buffer != null) {
          for (InstanceState keyState : keyboardStates) {
            Keyboard.addToBuffer(keyState, buffer);
          }
        }
      }
      prevOutputs = curOutputs;
      tickCount++;
      prop.toggleClocks();
      prop.propagate();
    }
    long elapse = System.currentTimeMillis() - start;
    if (showTty)
      ensureLineTerminated();
    if (showHalt || retCode != 0) {
      if (retCode == 0) {
        System.out.println(S.get("ttyHaltReasonPin"));
      } else if (retCode == 1) {
        System.out.println(S.get("ttyHaltReasonOscillation"));
      }
    }
    if (showSpeed) {
      displaySpeed(tickCount, elapse);
    }
    return retCode;
  }

  private static int doTableAnalysis(Project proj, Circuit circuit,
      Map<Instance, String> pinLabels,
      int format, int head, int body, int tail) {

    // args(ArrayList<Instance> inputPins, ArrayList<Instance> outputPins,
    //     Map<Instance, String> pinNames, int format)

    //     Model model = new AnalyzerModel();
    // Analyze.computeTable(model, proj, circuit, pinNames);
    // TruthTable table = model.getTruthTable();

    // ArrayList<String> formats = new ArrayList<>();
    // ArrayList<String> headers = new ArrayList<>();
    // for (Var v : model.getInputs().vars) {
    //   String name = v.name;
    //   headers.add(name);
    // }
    // for (Var v : model.getOutputs().vars) {
    //   String name = v.name;
    //   headers.add(name);
    // }

    // boolean needTableHeader = true;
    // ArrayList<Value> prevValues = null;
    // for (int i = 0; i < table.getRowCount()) {
    //   int col = 0;
    //   for (Var v : model.getInputs().vars) {
    //     for (int vi = 0; vi < v.width; vi++) {
    //       if (TruthTable.isInputSet(i, col, inputs))
    //         ...
    //     }
    //   }

    //   ArrayList<Value> currValue = new ArrayList<>();
    //   if (displayTableRow(needTableHeader, prevValues, curValues, headers, formats, format))
    //   needTableHeader = false;
    // }

    // model.getTruthTable().getOutputColumn(i);
    // model.setVariables(inputVars, outputVars);
    // for (int i = 0; i < columns.length; i++) {
    //   model.getTruthTable().setOutputColumn(i, columns[i]);
    // }
    ArrayList<Instance> inputPins = new ArrayList<Instance>();
    ArrayList<Var> inputVars = new ArrayList<Var>();
    ArrayList<String> inputNames = new ArrayList<String>();
    ArrayList<Instance> outputPins = new ArrayList<Instance>();
    ArrayList<Var> outputVars = new ArrayList<Var>();
    ArrayList<String> outputNames = new ArrayList<String>();
    ArrayList<String> formats = new ArrayList<String>();
    for (Map.Entry<Instance, String> entry : pinLabels.entrySet()) {
      Instance pin = entry.getKey();
      int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      Var var = new Var(entry.getValue(), width);
      if (Pin.FACTORY.isInputPin(pin)) {
        inputPins.add(pin);
        for (String name : var)
          inputNames.add(name);
        inputVars.add(var);
      } else {
        outputPins.add(pin);
        for (String name : var)
          outputNames.add(name);
        outputVars.add(var);
      }
    }

    ArrayList<String> headers = new ArrayList<>();
    ArrayList<Instance> pinList = new ArrayList<>();
    /* input pins first */
    for (Map.Entry<Instance, String> entry : pinLabels.entrySet()) {
      Instance pin = entry.getKey();
      String pinName = entry.getValue();
      if (Pin.FACTORY.isInputPin(pin)) {
        headers.add(pinName);
        pinList.add(pin);
      }
    }
    /* output pins last */
    for (Map.Entry<Instance, String> entry : pinLabels.entrySet()) {
      Instance pin = entry.getKey();
      String pinName = entry.getValue();
      if (!Pin.FACTORY.isInputPin(pin)) {
        headers.add(pinName);
        pinList.add(pin);
      }
    }

    int inputCount = inputNames.size();
    int rowCount = 1 << inputCount;

    if (head == 0 && tail == 0 && body == 0) {
      head = rowCount;
    } else {
      if (head + tail + body >= rowCount) {
        head = rowCount;
        tail = body = 0;
      }
    }

    int[] bodyidx = new int[body];
    if (body > 0) {
      // (deterministic) reservoir sampling!
      Random r = new Random((head + ":" + body + ":" + tail).hashCode());
      for (int k = 0; k < body; ++k)
        bodyidx[k] = k + head;
      for (int k = body ; k < rowCount - head - tail; ++k) {
        int v = r.nextInt(k+1);
        if (v < body)
          bodyidx[v] = k + head;
      }
      Arrays.sort(bodyidx);
    }

    int[] allidx = new int[head + body + tail];
    int ii = 0;
    for (int i = 0; i < head; i++)
      allidx[ii++] = i;
    for (int i = 0; i < body; i++)
      allidx[ii++] = bodyidx[i];
    for (int i = rowCount-tail; i < rowCount; i++)
      allidx[ii++] = i;

    boolean needTableHeader = true;
    HashMap<Instance, Value> valueMap = new HashMap<>();
    int bodyii = 0;
    for (ii = 0; ii < allidx.length; ii++) {
      int i = allidx[ii];
      if (ii == head || i == rowCount-tail)
        System.out.println("...");
      valueMap.clear();
      CircuitState circuitState = CircuitState.createRootState(proj, circuit);
      int incol = 0;
      for (int j = 0; j < inputPins.size(); j++) {
        Instance pin = inputPins.get(j);
        int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
        Value v[] = new Value[width];
        for (int b = width-1; b >= 0; b--) {
          boolean value = TruthTable.isInputSet(i, incol++, inputCount);
          v[b] = value ?  Value.TRUE : Value.FALSE;
        }
        InstanceState pinState = circuitState.getInstanceState(pin);
        Pin.FACTORY.driveInputPin(pinState, Value.create(v));
        valueMap.put(pin, Value.create(v));
      }

      Propagator prop = circuitState.getPropagator();
      prop.propagate();
      /*
       * TODO for the SimulatorPrototype class do { prop.step(); } while
       * (prop.isPending());
       */
      // TODO: Search for circuit state

      for (int j = 0; j < outputPins.size(); j++) {
        Instance pin = outputPins.get(j);
        if (prop.isOscillating()) {
          BitWidth width = pin.getAttributeValue(StdAttr.WIDTH);
          valueMap.put(pin, Value.createError(width));
        } else {
          int width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
          InstanceState pinState = circuitState.getInstanceState(pin);
          Value outValue = Pin.FACTORY.getValue(pinState);
          valueMap.put(pin, outValue);
        }
      }
      ArrayList<Value> currValues = new ArrayList<>();
      for (Instance pin : pinList) {
        currValues.add(valueMap.get(pin));
      }
      displayTableRow(needTableHeader, null, currValues, headers, formats, format);
      needTableHeader = false;
    }

    return 0;
  }

  public static void sendFromTty(char c) {
    lastIsNewline = c == '\n';
    System.out.print(c); // OK
  }

  public static final int FORMAT_TABLE = 1;
  public static final int FORMAT_SPEED = 2;
  public static final int FORMAT_TTY = 4;
  public static final int FORMAT_HALT = 8;
  public static final int FORMAT_STATISTICS = 16;
  public static final int FORMAT_TABLE_TABBED = 32;
  public static final int FORMAT_TABLE_CSV = 64;
  public static final int FORMAT_TABLE_BIN = 128;
  public static final int FORMAT_TABLE_HEX = 256;
  public static final int FORMAT_RANDOMIZE = 256;

  private static boolean lastIsNewline = true;
}
