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

package com.cburch.logisim.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.JComponent;

/*
 * Adapted from:
 * https://gist.github.com/alanwhite/42502f20390baf879d093691ebb72066
 *
 * To make this work ensure on jdk17.02+2 onwards
 * Compile with -XDignore.symbol.file --add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED 
 * Run with --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED 
 * 
 * This will also compile and run on platforms other than osx as it uses reflection to reach the
 * osx specific classes.
 */

public class GestureUtilities {

  // Mimics com.apple.eawt.event.MagnificationListener
  @FunctionalInterface
  public interface MagnificationListener {
    public void magnify(MagnificationEvent event);
  }

  // Mimics com.apple.eawt.event.RotationListener
  @FunctionalInterface
  public interface RotationListener {
    public void rotate(RotationEvent event);
  }

  // Mimics com.apple.eawt.event.MagnificationEvent
  public static class MagnificationEvent {
    private double amount;
    private Object appleEvent;
    private MagnificationEvent(Object o) throws Exception { 
      appleEvent = o;
      amount = (Double)appleEvent.getClass().getMethod("getMagnification").invoke(appleEvent);
    }
    public double getMagnification() { return amount; }
    public void consume() {
      try {
        appleEvent.getClass().getMethod("consume").invoke(appleEvent);
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
    // protected boolean isConsumed() { }
  }

  // Mimics com.apple.eawt.event.RotationEvent
  public static class RotationEvent {
    private double amount;
    private Object appleEvent;
    private RotationEvent(Object o) throws Exception { 
      appleEvent = o;
      amount = (Double)appleEvent.getClass().getMethod("getRotation").invoke(appleEvent);
    }
    public double getRotation() { return amount; }
    public void consume() {
      try {
        appleEvent.getClass().getMethod("consume").invoke(appleEvent);
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
    // protected boolean isConsumed() { }
  }

  // Reflection Proxy for com.apple.eawt.event.MagnificationListener
	private static class MagnificationHandler implements InvocationHandler {
    private MagnificationListener listener;

    private MagnificationHandler(MagnificationListener l) { listener = l; }

		public Object invoke(Object proxy, Method method, Object[] args) {
      if (method == null || !"magnify".equals(method.getName())) {
        System.out.println("Unexpected handler invocation: proxy=" + proxy + " method="+method);
        return null;
      }
      if (args == null || args.length != 1) {
        System.out.println("Unexpected handler argumnets: method="+method + " args="+ (args != null ? ""+args.length : "null"));
        return null;
      }
      Object event = args[0];
      // event should be com.apple.eawt.event.MagnificationEvent (or subclass?)
      // if (!(event instanceof com.apple.eawt.event.MagnificationEvent)) { ... }
			try {
        listener.magnify(new MagnificationEvent(event));
			} catch (Throwable t) {
				t.printStackTrace();
			}
			return null;
		}
	}

  // Reflection Proxy for com.apple.eawt.event.RotationListener
	private static class RotationHandler implements InvocationHandler {
    private RotationListener listener;

    private RotationHandler(RotationListener l) { listener = l; }

		public Object invoke(Object proxy, Method method, Object[] args) {
      if (method == null || !"rotate".equals(method.getName())) {
        System.out.println("Unexpected handler invocation: proxy=" + proxy + " method="+method);
        return null;
      }
      if (args == null || args.length != 1) {
        System.out.println("Unexpected handler argumnets: method="+method + " args="+ (args != null ? ""+args.length : "null"));
        return null;
      }
      Object event = args[0];
      // event should be com.apple.eawt.event.RotationEvent (or subclass?)
      // if (!(event instanceof com.apple.eawt.event.RotationEvent)) { ... }
			try {
        listener.rotate(new RotationEvent(event));
			} catch (Throwable t) {
				t.printStackTrace();
			}
			return null;
		}
	}

  // Accessing com.apple.eawt.event.GestureUtilities by reflection requires running with java option:
  //   --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED 
  private static Class javax_swing_JComponent;
  private static Class com_apple_eawt_event_GestureUtilities;
  private static Class com_apple_eawt_event_GestureListener;
  private static Class com_apple_eawt_event_MagnificationListener;
  private static Class com_apple_eawt_event_RotationListener;
  private static boolean appleGestureSupported;
  static {
    try {
      initialize();
    } catch (Throwable t) {
      appleGestureSupported = false;
      System.out.println("Apple gesture support: failed to initialize: " + t.getMessage());
      t.printStackTrace();
    }
  }

  private static void initialize() throws Exception {
    String osname = System.getProperty("os.name").toLowerCase();
    if (!osname.startsWith("mac") && !osname.startsWith("darwin"))
      return;

    javax_swing_JComponent = Class.forName("javax.swing.JComponent");
    com_apple_eawt_event_GestureUtilities = Class.forName("com.apple.eawt.event.GestureUtilities");
    com_apple_eawt_event_MagnificationListener = Class.forName("com.apple.eawt.event.MagnificationListener");
    com_apple_eawt_event_RotationListener = Class.forName("com.apple.eawt.event.RotationListener");
    com_apple_eawt_event_GestureListener = Class.forName("com.apple.eawt.event.GestureListener");

    appleGestureSupported = true;
    Debug.println(1, "Apple gesture support initialized");
  }

  private GestureUtilities() { }

  private static Object lock = new Object();

  private static void reportError(Throwable t) {
    synchronized(lock) {
      if (!appleGestureSupported)
        return;
      appleGestureSupported = false;
      if (t instanceof IllegalAccessException) {
        String opt = "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED";
        String jvmCmd = ProcessHandle.current().info().commandLine().orElse("error");
        if (!Arrays.asList(jvmCmd.split(" ")).contains(opt)) { // not perfect, but good enough
          System.out.printf("Note: To enable Apple gesture support, use JVM option '%s'.\n", opt);
          return;
        }
      }
      Debug.println(0, "Apple gesture support failed, will be disabled: " + t.getMessage());
      if (Debug.verbose > 0)
        t.printStackTrace();
    }
  }

  @SuppressWarnings("unchecked")
  public static void addMagnificationListenerTo(JComponent comp, MagnificationListener listener) {
    if (!appleGestureSupported)
      return;
    try {
      synchronized (lock) {
        HashMap<MagnificationListener, Object> proxies;
        proxies = (HashMap<MagnificationListener, Object>)
          comp.getClientProperty("apple-gesture-magnification-listeners");
        if (proxies == null) {
          proxies = new HashMap<>();
          comp.putClientProperty("apple-gesture-magnification-listeners", proxies);
        }
        Object proxy = proxies.get(listener);
        if (proxy == null) {
          proxy = Proxy.newProxyInstance(
              com_apple_eawt_event_MagnificationListener.getClassLoader(),
              new Class[]{com_apple_eawt_event_MagnificationListener},
              new MagnificationHandler(listener));
          proxies.put(listener, proxy);
        }

        com_apple_eawt_event_GestureUtilities.getMethod(
            "addGestureListenerTo",
            javax_swing_JComponent,
            com_apple_eawt_event_GestureListener).invoke(null, comp, proxy);
      }
    } catch (Throwable t) {
      reportError(t);
    }
  }

  @SuppressWarnings("unchecked")
  public static void removeMagnificationListenerFrom(JComponent comp, MagnificationListener listener) {
    if (!appleGestureSupported)
      return;
    try {
      synchronized (lock) {
        HashMap<MagnificationListener, Object> proxies;
        proxies = (HashMap<MagnificationListener, Object>)
          comp.getClientProperty("apple-gesture-magnification-listeners");
        if (proxies == null)
          return;
        Object proxy = proxies.remove(listener);
        if (proxy == null)
          return;

        com_apple_eawt_event_GestureUtilities.getMethod(
            "removeGestureListenerFrom",
            javax_swing_JComponent,
            com_apple_eawt_event_GestureListener).invoke(null, comp, proxy);
      }
    } catch (Throwable t) {
      reportError(t);
    }
  }

  @SuppressWarnings("unchecked")
  public static void addRotationListenerTo(JComponent comp, RotationListener listener) {
    if (!appleGestureSupported)
      return;
    try {
      synchronized (lock) {
        HashMap<RotationListener, Object> proxies;
        proxies = (HashMap<RotationListener, Object>)
          comp.getClientProperty("apple-gesture-rotation-listeners");
        if (proxies == null) {
          proxies = new HashMap<>();
          comp.putClientProperty("apple-gesture-rotation-listeners", proxies);
        }
        Object proxy = proxies.get(listener);
        if (proxy == null) {
          proxy = Proxy.newProxyInstance(
              com_apple_eawt_event_RotationListener.getClassLoader(),
              new Class[]{com_apple_eawt_event_RotationListener},
              new RotationHandler(listener));
          proxies.put(listener, proxy);
        }

        com_apple_eawt_event_GestureUtilities.getMethod(
            "addGestureListenerTo",
            javax_swing_JComponent,
            com_apple_eawt_event_GestureListener).invoke(null, comp, proxy);
      }

    } catch (Throwable t) {
      reportError(t);
    }
  }

  @SuppressWarnings("unchecked")
  public static void removeRotationListenerFrom(JComponent comp, RotationListener listener) {
    if (!appleGestureSupported)
      return;
    try {
      synchronized (lock) {
        HashMap<RotationListener, Object> proxies;
        proxies = (HashMap<RotationListener, Object>)
          comp.getClientProperty("apple-gesture-rotation-listeners");
        if (proxies == null)
          return;
        Object proxy = proxies.remove(listener);
        if (proxy == null)
          return;

        com_apple_eawt_event_GestureUtilities.getMethod(
            "removeGestureListenerFrom",
            javax_swing_JComponent,
            com_apple_eawt_event_GestureListener).invoke(null, comp, proxy);
      }
    } catch (Throwable t) {
      reportError(t);
    }
  }

}
