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
import java.util.WeakHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.UniquelyNamedThread;

public class HttpFetchManager {

  public static class Worker {
    Instance instance;
    CircuitState cs;
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

    Worker(String url, boolean autoFetch, int delay) {
      this.url = url == null ? "" : url;;
      this.delay = Math.max(100, delay);
      this.autoFetch = autoFetch;
      status = "ready";
    }

    private void fire() {
      sync.lock();
      try {
        // NOTE: It seems like we should be able to
        // cause a propagation more directly, since
        // we have the circuitState, which ultimately
        // just needs to mark instance.getComponent()
        // as dirty, basically one line of code.
        if (instance == null)
          System.err.println("missing instance in HttpIn???");
        else
          instance.fireInvalidated();
      } finally {
        sync.unlock();
      }
    }

    public void enableFetching(boolean enable) {
      if (enable && (instance == null || cs == null)) {
        System.err.println("HttpIn: Can't enable fetching without circuitstate and instance");
        return;
      }
      sync.lock();
      try {
        if (dead) {
          System.err.println("HttpIn: Can't enable/disable fetching in dead simulation");
          return;
        }
        if (fetchEnabled == enable)
          return; // nothing to do
        fetchEnabled = enable;
        if (enable) {
          CircuitState top = cs.getProject().getCircuitState();
          // FIXME: just query cs.isActive()
          simActive = cs == top || cs.hasAncestorState(top);
          status = "";
          timestamp = 0;
          penalty = 0;
          fresh = false;
          response = null;
        }
        if (enable && thread == null) {
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
      if (enable && (instance == null || cs == null)) {
        System.err.println("HttpIn: Can't de/activate fetching without circuitstate and instance");
        return;
      }
      sync.lock();
      try {
        if (dead) {
          System.err.println("HttpIn: Can't de/activate fetching in dead simulation");
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
        if (enable && fetchEnabled && thread == null) {
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
          System.err.println("HttpIn: Can't change params for dead simulation");
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
            System.out.println("fetching: " + target);
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
              e.printStackTrace();
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
  private static final WeakHashMap<Object, Integer> watches = new WeakHashMap<>();
  private static Watchdog myListener = new Watchdog();
  private static final Object lock = new Object();

  public static Worker makeWorker(String url, boolean autoFetch, int delay, Instance instance, CircuitState cs) {
    Worker worker = new Worker(url, autoFetch, delay);
    if (instance != null || cs != null)
      bind(worker, instance, cs);
    return worker;
  }

  public static Worker makeWorker(Worker other) {
    other.sync.lock();
    try {
      return new Worker(other.url, other.autoFetch, other.delay);
    } finally {
      other.sync.unlock();
    }
  }

  public static void bind(Worker worker, Instance instance, CircuitState cs) {
    // System.out.println("binding...");
    if (cs == null) {
      System.out.println("nevermind, killing...");
      kill(worker);
      return;
    }
    CircuitState csOld = null;
    worker.sync.lock();
    try {
      if (worker.instance != instance) {
        System.out.println("setting instance...");
        System.out.println("  old=" + worker.instance);
        System.out.println("  new=" + instance);
        worker.instance = instance;
      }

      if (worker.cs == cs) {
        // System.out.println("cs didn't change ...");
        return;
      }
      if (worker.cs != null) {
        // cs really shouln't change, right?
        System.err.println("huh... cs mismatch in HttpIn!!!");
        System.err.println("old: " + worker.cs);
        System.err.println("new: " + cs);
        if (cs.getProject() != worker.cs.getProject())
          csOld = worker.cs;
        worker.cs = cs;
      } else {
        worker.cs = cs;
      }
    } finally {
      worker.sync.unlock();
    }
    synchronized (lock) {
      if (!workers.contains(worker))
        workers.add(worker);
      if (csOld != null) {
        System.out.println("unbind worker from cs");
        System.out.println("  worker=" + worker);
        System.out.println("  cs=" + csOld);
        System.out.println("  proj=" + csOld.getProject().getLogisimFile().getName());
        unlistenRecursive(csOld);
      }
      Project proj = cs.getProject();
      System.out.println("bind worker to cs");
      System.out.println("  worker=" + worker);
      System.out.println("  cs=" + cs);
      System.out.println("  proj=" + proj.getLogisimFile().getName());
      listenRecursive(cs);
    }
  }

  private static void listenRecursive(CircuitState cs) {
    listen(cs.getCircuit());
    while (cs.isSubstate()) {
      // Component sub = cs.getSubcircuit();
      cs = cs.getParentState();
      listen(cs.getCircuit());
    }
    listen(cs.getProject());
  }

  private static void unlistenRecursive(CircuitState cs) {
    unlisten(cs.getCircuit());
    while (cs.isSubstate()) {
      // Component sub = cs.getSubcircuit();
      cs = cs.getParentState();
      unlisten(cs.getCircuit());
    }
    unlisten(cs.getProject());
  }

  private static void listen(Project proj) {
    listen(proj, () -> proj.addProjectWeakListener(null, myListener));
  }

  private static void listen(Circuit circ) {
    listen(circ, () -> circ.addCircuitWeakListener(null, myListener));
  }

  private static void listen(Object obj, Runnable act) {
    synchronized(lock) {
      Integer i = watches.get(obj);
      if (i == null) {
        watches.put(obj, (Integer)1);
        act.run();
      } else {
        watches.put(obj, (Integer)(i+1));
      }
    }
  }
  
  private static void unlisten(Project proj) {
    unlisten(proj, () -> proj.removeProjectWeakListener(null, myListener));
  }

  private static void unlisten(Circuit circ) {
    unlisten(circ, () -> circ.removeCircuitWeakListener(null, myListener));
  }

  private static void unlisten(Object obj, Runnable act) {
    synchronized(lock) {
      Integer i = watches.get(obj);
      if (i == null || i <= 1) {
        watches.remove(obj);
        act.run();
      } else {
        watches.put(obj, (Integer)(i-1));
      }
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
      System.out.println("kill worker from cs");
      System.out.println("  worker=" + worker);
      System.out.println("  cs=" + worker.cs);
      System.out.println("  proj=" + worker.cs.getProject().getLogisimFile().getName());
      unlistenRecursive(worker.cs);
    }
  }

  // FIXME: should also listen to circuit, so we can stop workers
  // for components that have been removed from a circuit
  private static class Watchdog implements ProjectListener, CircuitListener {

    static void killIf(Predicate<Worker> condition) {
      synchronized(lock) {
        for (int i = workers.size()-1; i >= 0; i--) {
          Worker worker = workers.get(i);
          worker.sync.lock();
          try {
            if (condition.test(worker))
              kill(worker);
          } finally {
            worker.sync.unlock();
          }
        }
      }
    }
    
    @Override
    public void circuitChanged(CircuitEvent event) {
      int action = event.getAction();
      if (action == CircuitEvent.ACTION_REMOVE) {
        Circuit circ = event.getCircuit();
        Component comp = (Component)event.getData();
        // if this comp is a Worker.instance, kill that worker
        // if any Worker.cs subcirc/ancestor was was this comp, kill that worker
        //   [oops, can't implement without access to cs.getSubcircuit() ]
        // alternatively...
        //   We hacked CircuitState to call kill/reset directly.
        killIf( (worker) ->  {
            if (worker.instance != null && worker.instance.getComponent() == comp)
              return true;
            // CircuitState cs = worker.cs;
            // while (cs.isSubstate()) {
            //   Component parent = cs.getSubcircuit();
            //   if (parent == comp)
            //     return true;
            //   cs = cs.getParentState();
            // }
            return false;
        });
      } else if (action == CircuitEvent.ACTION_CLEAR) {
        Circuit circ = event.getCircuit();
        // if any Worker.instance was in this circuit, kill that worker
        // if any Worker.cs subcirc/ancestor was in this circuit, kill that worker
        killIf( (worker) ->  {
            CircuitState cs = worker.cs;
            if (cs.getCircuit() == circ)
              return true;
            while (cs.isSubstate()) {
              cs = cs.getParentState();
              if (cs.getCircuit() == circ)
                return true;
            }
            return false;
        });
      } else if (action == CircuitEvent.TRANSACTION_DONE) {
        // FIXME stopgap
        Circuit circ = event.getCircuit();
        // ugh, we can't even know what project the affected circuit
        // belongs too, as it could be in multiple projects if 
        // being used as a library.
        //   activateWorkers(circ.... getProject());
        // instead, scan all workers, ugh.
        synchronized(lock) {
          for (int i = workers.size()-1; i >= 0; i--) {
            Worker worker = workers.get(i);
            worker.sync.lock();
            try {
              // FIXME: use cs.isActive(), etc.
              // if (worker.cs.getCircuit() != circ && !worker.cs.hasAncestorState(... circ))
              //   continue;
              CircuitState top = worker.cs.getProject().getCircuitState();
              boolean active = worker.cs == top || worker.cs.hasAncestorState(top);
              if (active != worker.simActive) {
                System.out.println("changing worker activity, the hard way");
                worker.simActive = active;
                worker.change.signalAll();
              }
            } finally {
              worker.sync.unlock();
            }
          }
        }
      }
    }

    // FIXME stopgap: we can't reliably determine when simulation
    // is removed. So instead, de-activate workers that aren't part
    // of the current top-level simulation.
    private static void activateWorkers(Project proj) {
      CircuitState top = proj.getCircuitState();
      synchronized(lock) {
        for (int i = workers.size()-1; i >= 0; i--) {
          Worker worker = workers.get(i);
          worker.sync.lock();
          try {
            if (worker.cs.getProject() != proj)
              continue;
            boolean active = worker.cs == top || worker.cs.hasAncestorState(top);
            if (active != worker.simActive) {
              System.out.println("changing worker activity");
              worker.simActive = active;
              worker.change.signalAll();
            }
          } finally {
            worker.sync.unlock();
          }
        }
      }
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_STATE) {
        // System.out.println("set sim state");
        Project proj = event.getProject();
        activateWorkers(proj);
      } else if (action == ProjectEvent.ACTION_CLEAR_STATES) {
        // System.out.println("clear sim states... close all ports?");
        Project proj = event.getProject();
        killIf( (worker) -> worker.cs.getProject() == proj );
      } else if (action == ProjectEvent.ACTION_ADD_STATE) {
        // CircuitState cs = (CircuitState)event.getData();
        // System.out.println("new cs: " + cs);
      } else if (action == ProjectEvent.ACTION_DELETE_STATE) {
        CircuitState cs = (CircuitState)event.getData();
        Project proj = cs.getProject();
        killIf( (worker) ->
            worker.cs.getProject() == proj
                  && (worker.cs == cs || worker.cs.hasAncestorState(cs)) );
      }
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
    STATUS_CODES.put(202, "Accepted");
    STATUS_CODES.put(208, "Already Reported");
    STATUS_CODES.put(502, "Bad Gateway");
    STATUS_CODES.put(400, "Bad Request");
    STATUS_CODES.put(509, "Bandwidth Limit Exceeded");
    STATUS_CODES.put(409, "Conflict");
    STATUS_CODES.put(100, "Continue");
    STATUS_CODES.put(201, "Created");
    STATUS_CODES.put(103, "Early Hints");
    STATUS_CODES.put(417, "Expectation Failed");
    STATUS_CODES.put(424, "Failed Dependency");
    STATUS_CODES.put(403, "Forbidden");
    STATUS_CODES.put(302, "Found");
    STATUS_CODES.put(504, "Gateway Timeout");
    STATUS_CODES.put(410, "Gone");
    STATUS_CODES.put(505, "HTTP Version Not Supported");
    STATUS_CODES.put(418, "I'm a teapot");
    STATUS_CODES.put(226, "IM Used");
    STATUS_CODES.put(507, "Insufficient Storage");
    STATUS_CODES.put(500, "Internal Server Error");
    STATUS_CODES.put(411, "Length Required");
    STATUS_CODES.put(423, "Locked");
    STATUS_CODES.put(508, "Loop Detected");
    STATUS_CODES.put(405, "Method Not Allowed");
    STATUS_CODES.put(301, "Moved Permanently");
    STATUS_CODES.put(207, "Multi-Status");
    STATUS_CODES.put(300, "Multiple Choices");
    STATUS_CODES.put(511, "Network Authentication Required");
    STATUS_CODES.put(204, "No Content");
    STATUS_CODES.put(203, "Non-Authoritative Information");
    STATUS_CODES.put(406, "Not Acceptable");
    STATUS_CODES.put(510, "Not Extended");
    STATUS_CODES.put(404, "Not Found");
    STATUS_CODES.put(501, "Not Implemented");
    STATUS_CODES.put(304, "Not Modified");
    STATUS_CODES.put(200, "OK");
    STATUS_CODES.put(206, "Partial Content");
    STATUS_CODES.put(413, "Payload Too Large");
    STATUS_CODES.put(402, "Payment Required");
    STATUS_CODES.put(308, "Permanent Redirect");
    STATUS_CODES.put(412, "Precondition failed");
    STATUS_CODES.put(428, "Precondition Required");
    STATUS_CODES.put(102, "Processing");
    STATUS_CODES.put(407, "Proxy Authentication Required");
    STATUS_CODES.put(431, "Request Header Fields Too Large");
    STATUS_CODES.put(408, "Request Timeout");
    STATUS_CODES.put(416, "Requested Range Not Satisfiable");
    STATUS_CODES.put(205, "Reset Content");
    STATUS_CODES.put(303, "See Other");
    STATUS_CODES.put(503, "Service Unavailable");
    STATUS_CODES.put(101, "Switching Protocols");
    STATUS_CODES.put(307, "Temporary Redirect");
    STATUS_CODES.put(425, "Too Early");
    STATUS_CODES.put(429, "Too Many Requests");
    STATUS_CODES.put(401, "Unauthorized");
    STATUS_CODES.put(451, "Unavailable For Legal Reasons");
    STATUS_CODES.put(422, "Unprocessable Entity");
    STATUS_CODES.put(415, "Unsupported Media Type");
    STATUS_CODES.put(426, "Upgrade Required");
    STATUS_CODES.put(414, "URI Too Long");
    STATUS_CODES.put(506, "Variant Also Negotiates");
    STATUS_CODES.put(100, "Continue");
    STATUS_CODES.put(101, "Switching Protocols");
    STATUS_CODES.put(102, "Processing");
    STATUS_CODES.put(103, "Early Hints");
    STATUS_CODES.put(103, "Checkpoint");
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
    STATUS_CODES.put(414, "URI Too Long");
    STATUS_CODES.put(414, "Request-URI Too Long");
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
