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
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.prefs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Supplier;

import com.cburch.logisim.util.Debug;

class BackingStore {
  
  static final long WRITE_DELAY_MS = 500;
  
  final Object lock = new Object();
  final String name;
  final File file;
  final Supplier<String> buildXml;
  Timer writeTimer;
  TimerTask writeTask; // non-null iff dirty
  
  BackingStore(String name, File file, Supplier<String> buildXml) {
    this.name = name;
    this.file = file;
    this.buildXml = buildXml;
    Runtime.getRuntime().addShutdownHook(
        new Thread(() -> flushIfDirty(), "logisim-" + name + "-flush"));
  }
  
  void flushIfDirty() {
    synchronized (lock) {
      if (writeTask != null)
        writeNow();
    }
  }

  void markDirty() {
    synchronized (lock) {
      if (writeTask != null) {
        writeTask.cancel();
        writeTask = null;
      }
      if (writeTimer == null) {
        writeTimer = new Timer("logisim-state-write", true); // daemon
      }
      writeTask = new TimerTask() {
        @Override public void run() { writeNow(); }
      };
      writeTimer.schedule(writeTask, WRITE_DELAY_MS);
    }
  }

  void writeNow() {
    synchronized (lock) {
      if (writeTask != null) { writeTask.cancel(); writeTask = null; }
      try {
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
        File tmp = new File(dir != null ? dir : new File("."), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), buildXml.get(), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), file.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        Debug.error("Error writing " + name + ": " + file, e);
      }
    }
  }

  static String xmlEscapeAttr(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
  }
  
  static String xmlEscapeComment(String s) {
    if (s == null) return "";
    // XML comments must not contain "--"
    return s.replace("--", "-~");
  }

}
