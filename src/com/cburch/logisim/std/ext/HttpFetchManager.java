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

package com.cburch.logisim.std.ext;
import static com.cburch.logisim.std.Strings.S;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.util.Debug;
import com.cburch.logisim.util.UniquelyNamedThread;

public class HttpFetchManager {

  private static int nextId = 1;

  public static class Worker {
    int id = nextId++;
    Component comp;
    final CircuitState cs;
    String url;
    int delay, penalty;
    boolean simActive;
    boolean fetchEnabled;
    boolean autoFetch;
    boolean dead;
    boolean fetching;
    String status; // e.g. "200 OK" or "Bad Format"
    long timestamp; // of last status change
    String response; // most recent response, or null if none
    boolean fresh;  // whether a response is waiting to be consumed

    ReentrantLock sync = new ReentrantLock();
    Condition change = sync.newCondition();
    Thread thread;

    Worker(String url, boolean autoFetch, int delay, Component comp, CircuitState cs) {
      this.url = url == null ? "" : url;;
      this.delay = Math.max(100, delay);
      this.autoFetch = autoFetch;
      this.comp = comp;
      this.cs = cs;
      status = "ready";
    }

    public String toString() {
      return "HttpFetchWorker"+id+" for " + url;
    }

    private void fire() {
      sync.lock();
      try {
        cs.queueForPropagation(comp);
      } finally {
        sync.unlock();
      }
    }

    public void enableFetching(boolean enable) {
      sync.lock();
      try {
        if (dead) {
          Debug.error("HttpIn: Can't enable/disable fetching in defunct simulation");
          return;
        }
        if (fetchEnabled == enable)
          return; // nothing to do
        fetchEnabled = enable;
        if (enable) {
          simActive = cs.isActive();
          status = "";
          timestamp = 0;
          penalty = 0;
          fresh = false;
          response = null;
        }
        if (simActive && fetchEnabled && thread == null) {
          thread = new UniquelyNamedThread(() -> runFetch(), "HttpInputWorker");
          thread.setDaemon(true);
          thread.start();
        }
        change.signalAll();
      } finally {
        sync.unlock();
      }
      fire();
    }

    public void setActive(boolean enable) {
      sync.lock();
      try {
        if (dead) {
          Debug.error("HttpIn: Can't de/activate fetching in defunct simulation");
          return;
        }
        if (simActive == enable)
          return; // nothing to do
        simActive = enable;
        if (enable) {
          status = "";
          timestamp = 0;
          penalty = 0;
          fresh = false;
          response = null;
        }
        if (simActive && fetchEnabled && thread == null) {
          thread = new UniquelyNamedThread(() -> runFetch(), "HttpInputWorker");
          thread.setDaemon(true);
          thread.start();
        }
        change.signalAll();
      } finally {
        sync.unlock();
      }
      fire();
    }

    public void setParams(String newUrl, boolean enable, int newDelay) {
      newDelay = Math.max(100, newDelay);
      newUrl = newUrl == null ? "" : newUrl;
      sync.lock();
      try {
        if (dead) {
          Debug.error("HttpIn: Can't change params for defunct simulation");
          return;
        }
        if (url.equals(newUrl) && autoFetch == enable && delay == newDelay)
          return; // nothing to do
        url = newUrl;
        autoFetch = enable;
        delay = newDelay;
        penalty = 0;
        change.signalAll();
      } finally {
        sync.unlock();
      }
    }

    public String getResponse() {
      sync.lock();
      try {
        String body = response;
        fresh = false;
        change.signalAll();
        return body;
      } finally {
        sync.unlock();
      }
    }

    private void runFetch() {
      try {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5)).build();
        while (true) {
          try {
            String target;
            sync.lock();
            try {
              // should we stop entirely?
              if (dead) {
                return;
              }
              // should we just be asleep?
              if (!fetchEnabled || !simActive) {
                change.await();
                continue;
              }
              // should we throttle?
              if (autoFetch || !fresh) {
                long now = System.currentTimeMillis();
                long elapsed = now - timestamp;
                if (elapsed < (delay+penalty)) {
                  long deadline = (delay+penalty) - elapsed;
                  change.awaitNanos(deadline * 1_000_000);
                  continue;
                }
              }
              fetching = true;
              target = url;
            } finally {
              sync.unlock();
            }
            Debug.println(2, this +" GET " + target);
            URI uri;
            try {
              uri = URI.create(target);
            } catch (Exception e) {
              throw new ConnectException("bad url");
            }
            HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Accept", "*/*").GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            String body = resp.body();
            sync.lock();
            try {
              timestamp = System.currentTimeMillis();
              if (!target.equals(url)) {
                // oops, fetched wrong url...
                status = "retry";
                penalty = 0;
              } else {
                status = statusCode(code);
                if (code >= 200 && code < 300) {
                  response = body;
                  fresh = true;
                  penalty = 0;
                }
              }
            } finally {
              fetching = false;
              sync.unlock();
            }
          } catch (InterruptedException ie) {
            return;
          } catch (Exception e) {
            if (!(e instanceof ConnectException))
              Debug.error("HttpFetchManager run", e);
            sync.lock();
            try {
              fetching = false;
              status = e.getMessage();
              if (status ==  null || status.isEmpty())
                status = "failed";
              timestamp = System.currentTimeMillis();
              penalty = Math.min(60_000, Math.max(4*delay, 2*penalty));
            } finally {
              sync.unlock();
            }
          }
          fire();
        } // end of while(true)
      } finally {
        sync.lock();
        try {
          if (!dead) {
            dead = true;
            change.signalAll();
          }
        } finally {
          sync.unlock();
        }
      }
    }

  }

  private static final ArrayList<Worker> workers = new ArrayList<>();
  private static final Object lock = new Object();

  public static Worker makeWorker(String url, boolean autoFetch, int delay, Component comp, CircuitState cs) {
    if (cs == null) throw new IllegalArgumentException("cs");
    if (comp == null) throw new IllegalArgumentException("comp");
    Worker worker = new Worker(url, autoFetch, delay, comp, cs);
    synchronized (lock) {
      if (!workers.contains(worker))
        workers.add(worker);
    }
    return worker;
  }

  public static void relocate(Worker worker, Component comp, CircuitState cs) {
    // - component changes when a component moves on the canvas
    // - cs should never change
    if (cs == null) throw new IllegalArgumentException("cs");
    if (cs != worker.cs) throw new IllegalArgumentException("cs");
    if (comp == null) throw new IllegalArgumentException("comp");
    
    worker.sync.lock();
    try {
      worker.comp = comp;
    } finally {
      worker.sync.unlock();
    }
  }

  static void kill(Worker worker) {
    worker.sync.lock();
    try {
      if (!worker.dead) {
        worker.dead = true;
        worker.change.signalAll();
        if (worker.thread != null)
          worker.thread.interrupt();
      }
      // wait until thread is dead? or just ignore it...
    } finally {
      worker.sync.unlock();
    }
    synchronized (lock) {
      workers.remove(worker);
    }
  }

  static String statusCode(int code) {
    String desc = STATUS_CODES.get(code);
    if (desc != null)
      return String.format("%d %s", code, desc);
    else if (100 <= code && code <= 199)
      return String.format("%d INFO", code);
    else if (200 <= code && code <= 299)
      return String.format("%d SUCCESS", code);
    else if (300 <= code && code <= 399)
      return String.format("%d REDIRECT", code);
    else if (400 <= code && code <= 499)
      return String.format("%d CLIENT ERROR", code);
    else if (500 <= code && code <= 599)
      return String.format("%d SERVER ERROR", code);
    else
      return String.format("%d MYSTERY", code);
  }

  static final HashMap<Integer, String> STATUS_CODES = new HashMap<>();
  static{ 
    STATUS_CODES.put(100, "Continue");
    STATUS_CODES.put(101, "Switching Protocols");
    STATUS_CODES.put(102, "Processing");
    STATUS_CODES.put(103, "Checkpoint");
    STATUS_CODES.put(103, "Early Hints");
    STATUS_CODES.put(200, "OK");
    STATUS_CODES.put(201, "Created");
    STATUS_CODES.put(202, "Accepted");
    STATUS_CODES.put(203, "Non-Authoritative Information");
    STATUS_CODES.put(204, "No Content");
    STATUS_CODES.put(205, "Reset Content");
    STATUS_CODES.put(206, "Partial Content");
    STATUS_CODES.put(207, "Multi-Status");
    STATUS_CODES.put(208, "Already Reported");
    STATUS_CODES.put(226, "IM Used");
    STATUS_CODES.put(300, "Multiple Choices");
    STATUS_CODES.put(301, "Moved Permanently");
    STATUS_CODES.put(302, "Found");
    STATUS_CODES.put(302, "Moved Temporarily");
    STATUS_CODES.put(303, "See Other");
    STATUS_CODES.put(304, "Not Modified");
    STATUS_CODES.put(305, "Use Proxy");
    STATUS_CODES.put(307, "Temporary Redirect");
    STATUS_CODES.put(308, "Permanent Redirect");
    STATUS_CODES.put(400, "Bad Request");
    STATUS_CODES.put(401, "Unauthorized");
    STATUS_CODES.put(402, "Payment Required");
    STATUS_CODES.put(403, "Forbidden");
    STATUS_CODES.put(404, "Not Found");
    STATUS_CODES.put(405, "Method Not Allowed");
    STATUS_CODES.put(406, "Not Acceptable");
    STATUS_CODES.put(407, "Proxy Authentication Required");
    STATUS_CODES.put(408, "Request Timeout");
    STATUS_CODES.put(409, "Conflict");
    STATUS_CODES.put(410, "Gone");
    STATUS_CODES.put(411, "Length Required");
    STATUS_CODES.put(412, "Precondition failed");
    STATUS_CODES.put(413, "Payload Too Large");
    STATUS_CODES.put(413, "Request Entity Too Large");
    STATUS_CODES.put(414, "Request-URI Too Long");
    STATUS_CODES.put(414, "URI Too Long");
    STATUS_CODES.put(415, "Unsupported Media Type");
    STATUS_CODES.put(416, "Requested Range Not Satisfiable");
    STATUS_CODES.put(417, "Expectation Failed");
    STATUS_CODES.put(418, "I'm a teapot");
    STATUS_CODES.put(422, "Unprocessable Entity");
    STATUS_CODES.put(423, "Locked");
    STATUS_CODES.put(424, "Failed Dependency");
    STATUS_CODES.put(425, "Too Early");
    STATUS_CODES.put(426, "Upgrade Required");
    STATUS_CODES.put(428, "Precondition Required");
    STATUS_CODES.put(429, "Too Many Requests");
    STATUS_CODES.put(431, "Request Header Fields Too Large");
    STATUS_CODES.put(451, "Unavailable For Legal Reasons");
    STATUS_CODES.put(500, "Internal Server Error");
    STATUS_CODES.put(501, "Not Implemented");
    STATUS_CODES.put(502, "Bad Gateway");
    STATUS_CODES.put(503, "Service Unavailable");
    STATUS_CODES.put(504, "Gateway Timeout");
    STATUS_CODES.put(505, "HTTP Version Not Supported");
    STATUS_CODES.put(506, "Variant Also Negotiates");
    STATUS_CODES.put(507, "Insufficient Storage");
    STATUS_CODES.put(508, "Loop Detected");
    STATUS_CODES.put(509, "Bandwidth Limit Exceeded");
    STATUS_CODES.put(510, "Not Extended");
    STATUS_CODES.put(511, "Network Authentication Required");
  }

}
