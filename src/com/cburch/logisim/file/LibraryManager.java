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

package com.cburch.logisim.file;
import static com.cburch.logisim.file.Strings.S;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.WeakHashMap;

import com.cburch.logisim.tools.Library;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.proj.Project;

public class LibraryManager {

  private static abstract class LibraryDescriptor {
    protected final String prefix, suffix;
    protected final File absoluteFile;

    LibraryDescriptor(String prefix, File absoluteFile, String optionalSuffix) {
      if (!absoluteFile.isAbsolute())
        throw new IllegalArgumentException("library path must be absolute");
      this.prefix = prefix;
      this.absoluteFile = absoluteFile;
      this.suffix = (optionalSuffix != null ? "#" + optionalSuffix : "");
    }

    boolean isSameFile(File query) {
      try {
        return Files.isSameFile(absoluteFile.toPath(), query.toPath());
      } catch (IOException e) {
        return absoluteFile.equals(query);
      }
      // if (!query.isAbsolute())
      //   throw new IllegalArgumentException("library query path must be absolute");
      // return absoluteFile.equals(query);
    }

    abstract void setBase(Loader loader, LoadedLibrary lib)
        throws LoadFailedException, LoadCanceledByUser;

    String toShortDescriptor() {
      return prefix + "#"
          + absoluteFile.getName()
          + suffix;
    }

    String toRelativeDescriptor(File mainFile) {
      return prefix + "#"
          + toRelative(mainFile.toPath(), absoluteFile.toPath())
          + suffix;
    }

    String toAbsoluteDescriptor() {
      return prefix + "#" + absoluteFile.toString() + suffix;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof LibraryDescriptor))
        return false;
      return this.toAbsoluteDescriptor().equals(((LibraryDescriptor)other).toAbsoluteDescriptor());
    }

    @Override
    public int hashCode() {
      return this.toAbsoluteDescriptor().hashCode();
    }

  }

  private static class JarDescriptor extends LibraryDescriptor {
    private String className;

    JarDescriptor(File absFile, String className) {
      super("jar", absFile, className);
      this.className = className;
    }

    @Override
    void setBase(Loader loader, LoadedLibrary lib) throws LoadFailedException, LoadCanceledByUser {
      lib.setBase(loader.loadJarFile(absoluteFile, className));
    }
  }

  private static class LogisimProjectDescriptor extends LibraryDescriptor {

    LogisimProjectDescriptor(File absFile) {
      super("file", absFile, null);
    }

    @Override
    void setBase(Loader loader, LoadedLibrary lib) throws LoadFailedException, LoadCanceledByUser {
      lib.setBase(loader.loadLogisimLibraryStage3(absoluteFile).file);
    }

  }

//  private static String toRelative(File circFile, File otherFile) {
//     File curdir = circFile.getParentFile();
//     if (curdir == null)
//       throw new IllegalArgumentException("circ file must have parent");
//    //   try {
//    //     return file.getCanonicalPath();
//    //   } catch (IOException e) {
//    //     return file.toString();
//    //   }
// 
//     String name = otherFile.getName();
//     File fileDir = otherFile.getParentFile();
//     if (fileDir == null)
//       throw new IllegalArgumentException("other file must be absolute");
// 
//     // if (fileDir != null) {
//       if (curdir.equals(fileDir))
//         return name;
//       else if (curdir.equals(fileDir.getParentFile()))
//         return fileDir.getName() + File.separator + name;
//       else if (fileDir.equals(curdir.getParentFile()))
//         return ".." + File.separator + name;
//       else
//         return otherFile.toString();
//     // }
//     //   try {
//     //     return file.getCanonicalPath();
//     //   } catch (IOException e) {
//     //     return file.toString();
//     //   }
//  }

  // Logisim .circ files contain some embedded file paths, e.g. for external
  // libraries ("file#foo.circ" or "jar#foo.jar"), and for some
  // hdl-related components. When user adds an external library (todo: or
  // chooses a path for hdl-related components), we don't know if
  // the user would prefer to embed a relative path (i.e. relative to the
  // directory containing the .circ file, or to the current working directory),
  // or an absolute path. The pros and cons depend on the situation:
  //  - Moving .circ file around within same filesystem with some shared
  //    libraries: abs is better.
  //  - Moving .circ file together with dependencies (e.g. in "project" folder):
  //    rels is better.
  // And there are complications:
  //  - If the circ file hasn't yet been saved, .circ file directory is unknown.
  //  - Upon save-as, the .circ file directory may change.
  //  - For windows and mac, the cwd is often not obvious or intuitive.
  //
  // Currently, when saving a .circ file, we make relative any path that is for
  // a file in the same directory, some sub-directory, or the direct parent
  // directory of the .circ file. So for a circ file:
  //      /home/kwalsh/proj/foo.circ
  // we would use these relative paths:
  //      jar#lib.jar          -- resolves to /home/kwalsh/proj/lib.jar
  //      jar#libs/lib.jar     -- resolves to /home/kwalsh/proj/libs/lib.jar
  //      jar#libs/foo/lib.jar -- resolves to /home/kwalsh/proj/libs/foo/lib.jar
  //      jar#../lib.jar       -- resolves to /home/kwalsh/lib.jar
  // and all other paths would be written as absolute, canonical paths. When
  // loading a .circ file, we resolve relative paths against the directory
  // containing the .circ file. When copy-pasting, we always use absolute,
  // canonical paths.
  //
  // There is currently no way to change the path except by editing the saved
  // xml. Ideally, paths would stay the same through load-edit-save cycles, but
  // currently all paths are re-normalized when saving the .circ file. It might
  // make sense to add a property in the attributes tab to allow a choice of
  // "relative" or "absolute" (or maybe "shell-style with homedir, etc.), and
  // maybe normalize only once upon the first save then preserve any later
  // changes the user makes to the paths. Alternatively, we could have a global
  // or project option to control the default, or a dialog on import.

  public static String toRelative(Path circFile, Path embeddedFile) {
    if (!embeddedFile.isAbsolute())
      throw new IllegalArgumentException("embedded path must be absolute");
    if (!circFile.isAbsolute())
      throw new IllegalArgumentException("circ path must be absolute");
    try {
      embeddedFile = embeddedFile.toRealPath();
      Path baseDir = circFile.toRealPath().getParent();
      Path baseParentDir = baseDir.getParent();
      Path eDir = embeddedFile.getParent();
      String eName = embeddedFile.getFileName().toString();
      if (baseDir.equals(eDir))
        return eName;
      if (baseParentDir != null && baseParentDir.equals(eDir))
        return ".." + File.separator + eName;
      if (eDir.startsWith(baseDir))
        return eDir.subpath(baseDir.getNameCount(), eDir.getNameCount())
            + File.separator + eName;
    } catch (IOException e) {
    }
    return embeddedFile.toString();
  }

  public static final LibraryManager instance = new LibraryManager();

  private HashMap<LibraryDescriptor, WeakReference<LoadedLibrary>> fileMap;

  private WeakHashMap<LoadedLibrary, LibraryDescriptor> invMap;

  private LibraryManager() {
    fileMap = new HashMap<LibraryDescriptor, WeakReference<LoadedLibrary>>();
    invMap = new WeakHashMap<LoadedLibrary, LibraryDescriptor>();
    ProjectsDirty.initialize();
  }

  public void fileSaved(Loader loader, File dest, File oldFile, LogisimFile file, Project proj) {
    LoadedLibrary old = findKnown(oldFile);
    if (old != null) {
      old.setDirty(false);
    }

    LoadedLibrary lib = findKnown(dest);
    if (lib != null) {
      LogisimFile clone = file.cloneLogisimFile(dest, loader, proj);
      if (clone == null)
        throw new IllegalStateException("clone failed");
      clone.setName(file.getName());
      clone.setDirty(false);
      lib.setBase(clone);
    }
  }

  private LoadedLibrary findKnown(Object key) {
    WeakReference<LoadedLibrary> retLibRef;
    retLibRef = fileMap.get(key);
    if (retLibRef == null) {
      return null;
    } else {
      LoadedLibrary retLib = retLibRef.get();
      if (retLib == null) {
        fileMap.remove(key);
        return null;
      } else {
        return retLib;
      }
    }
  }

  public Library findReference(LogisimFile file, File query) {
    for (Library lib : file.getLibraries()) {
      LibraryDescriptor desc = invMap.get(lib);
      if (desc != null && desc.isSameFile(query))
        return lib;
      if (lib instanceof LoadedLibrary) {
        LoadedLibrary loadedLib = (LoadedLibrary) lib;
        if (loadedLib.getBase() instanceof LogisimFile) {
          LogisimFile loadedProj = (LogisimFile) loadedLib.getBase();
          Library ret = findReference(loadedProj, query);
          if (ret != null)
            return lib;
        }
      }
    }
    return null;
  }

  private String getDescriptor(File mainFile, Library lib, int verbose) {
    if (Builtin.isBuiltinLibrary(lib.getClass())) {
      return "#" + lib.getName();
    } else {
      LibraryDescriptor desc = invMap.get(lib);
      if (desc == null)
        return null;
      if (verbose == 0)
        return desc.toShortDescriptor();
      else if (verbose == 1 && mainFile != null)
        return desc.toRelativeDescriptor(mainFile);
      else
        return desc.toAbsoluteDescriptor();
    }
  }

  public String getShortDescriptor(Library lib) {
    return getDescriptor(null, lib, 0);
  }

  public String getRelativeDescriptor(File mainFile, Library lib) {
    return getDescriptor(mainFile, lib, 1);
  }

  public String getAbsoluteDescriptor(Library lib) {
    return getDescriptor(null, lib, 2);
  }

  public String getShortDescriptor(String desc) {
    if (desc.startsWith("file#")) {
      String path = desc.substring(5);
      String file = new File(path).getName();
      return "file#" + file;
    } else if (desc.startsWith("jar#")) {
      String path = desc.substring(4);
      String suffix = "";
      int i = path.lastIndexOf('#');
      if (i >= 0) {
        suffix = path.substring(i);
        path = path.substring(0, i);
      }
      String file = new File(path).getName();
      return "jar#" + file + suffix;
    } else {
      return desc;
    }
  }


  Collection<LogisimFile> getLogisimLibraries() {
    ArrayList<LogisimFile> ret = new ArrayList<LogisimFile>();
    for (LoadedLibrary lib : invMap.keySet()) {
      if (lib.getBase() instanceof LogisimFile) {
        ret.add((LogisimFile) lib.getBase());
      }
    }
    return ret;
  }

  public LoadedLibrary loadJarLibraryStage2(Loader loader, File toReadAbsolute,
      String className) {
    if (!toReadAbsolute.isAbsolute())
      throw new IllegalArgumentException("jar path must be absolute");
    JarDescriptor jarDescriptor = new JarDescriptor(toReadAbsolute, className);
    LoadedLibrary ret = findKnown(jarDescriptor);
    if (ret != null)
      return ret;

    try {
      ret = new LoadedLibrary(loader.loadJarFile(toReadAbsolute, className));
    } catch (LoadFailedException e) {
      if (e.getCause() != null)
        Errors.project(loader.getMainFile()).show(e.getMessage(), e.getCause());
      else
        Errors.project(loader.getMainFile()).show(e.getMessage());
      return null;
    }

    fileMap.put(jarDescriptor, new WeakReference<LoadedLibrary>(ret));
    invMap.put(ret, jarDescriptor);
    return ret;
  }

  // Called only by Loader.loadLibrary()
  Library loadLibraryStage2(Loader loader, String desc) throws LoadCanceledByUser {
    // It may already be loaded.
    // Otherwise we'll have to decode it.
    int sep = desc.indexOf('#');
    if (sep < 0) {
      Errors.project(loader.getMainFile()).show(S.fmt("fileDescriptorError", desc));
      return null;
    }
    String type = desc.substring(0, sep);
    String name = desc.substring(sep + 1);

    if (type.equals("")) {
      Library ret = loader.getBuiltin().getLibrary(name);
      if (ret == null) {
        Errors.project(loader.getMainFile()).show(S.fmt("fileBuiltinMissingError", name));
        return null;
      }
      return ret;
    } else if (type.equals("file")) {
      return loader.loadLogisimLibraryWithSubstitutions(name);
    } else if (type.equals("jar")) {
      int sepLoc = name.lastIndexOf('#');
      String fileName = name.substring(0, sepLoc);
      String className = name.substring(sepLoc + 1);
      return loader.loadJarLibraryWithSubstitutions(fileName, className);
    } else {
      Errors.project(loader.getMainFile()).show(S.fmt("fileTypeError", type, desc));
      return null;
    }
  }

  // This is used only by Loader.loadLogisimLibrary()
  LoadedLibrary loadLogisimLibraryStage2(Loader loader, File toRead) throws LoadCanceledByUser {
    LoadedLibrary ret = findKnown(toRead);
    if (ret != null)
      return ret;

    try {
      ret = new LoadedLibrary(loader.loadLogisimLibraryStage3(toRead).file);
    } catch (LoadFailedException e) {
      if (e.getCause() != null)
        Errors.project(loader.getMainFile()).show(e.getMessage(), e.getCause());
      else
        Errors.project(loader.getMainFile()).show(e.getMessage());
      return null;
    }

    LogisimProjectDescriptor desc = new LogisimProjectDescriptor(toRead);
    fileMap.put(desc, new WeakReference<LoadedLibrary>(ret));
    invMap.put(ret, desc);
    return ret;
  }

  public void reload(Loader loader, LoadedLibrary lib) {
    LibraryDescriptor descriptor = invMap.get(lib);
    if (descriptor == null) {
      Errors.project(loader.getMainFile()).show(S.fmt("unknownLibraryFileError", lib.getDisplayName()));
    } else {
      try {
        descriptor.setBase(loader, lib);
      } catch (LoadCanceledByUser e) {
        // eat exception
      } catch (LoadFailedException e) {
        if (e.getCause() != null)
          Errors.project(loader.getMainFile()).show(e.getMessage(), e.getCause());
        else
          Errors.project(loader.getMainFile()).show(e.getMessage());
      }
    }
  }

  void setDirty(File file, boolean dirty) {
    LoadedLibrary lib = findKnown(file);
    if (lib != null) {
      lib.setDirty(dirty);
    }
  }
}
