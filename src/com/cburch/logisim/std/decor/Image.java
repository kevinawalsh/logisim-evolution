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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.ExportImage;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.JoinedAction;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Reshapable;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Errors;
import com.cburch.logisim.util.JInputDialog;
import com.cburch.logisim.util.StringGetter;

public class Image extends InstanceFactory implements Reshapable {

  // Image just displays an image, which can be embedded in the project or
  // linked to an external file. Like Text, this component has no behavior,
  // inputs, or outputs. 

  public static class ImageContent {
    String format; // "PNG" or "JPG"
    Attributes.LinkedFile source;
    byte[] imgData;
    BufferedImage img;
    long timestamp;

    public ImageContent(Attributes.LinkedFile src) {
      this.source = src;
      if (src.relative.toString().toLowerCase().endsWith(".png"))
        format = "PNG";
      else
        format = "JPG";
    }

    public ImageContent(ImageContent other) {
      this.format = other.format;
      this.source = other.source;
      this.imgData = other.imgData;
      this.img = other.img;
      this.timestamp = other.timestamp;
    }

    public ImageContent(File f) throws IOException {
      imgData = Files.readAllBytes(f.toPath());
      ByteArrayInputStream stream = new ByteArrayInputStream(imgData);
      img = ImageIO.read(stream);
      stream.close();
      if (f.toString().toLowerCase().endsWith(".png"))
        format = "PNG";
      else
        format = "JPG";
    }

    public ImageContent(String format, BufferedImage img, byte[] imgData) {
      this.format = format;
      this.img = img;
      this.imgData = imgData;
    }

    public BufferedImage getImage() {
      if (source == null)
        return img;
      long ts = source.absolute.lastModified();
      if (ts == timestamp)
        return img;
      timestamp = ts;
      try {
        img = ImageIO.read(source.absolute);
      } catch (IOException e) {
        img = null;
        // maybe don't warn?
        System.out.println("image file access error: " + source.relative);
      }
      return img;
    }

  }

  public static final Attribute<Attributes.LinkedFile> ATTR_FILENAME_SINGLETON =
      Attributes.forFilename("filename", 
          S.getter("stdImageFilename"), S.getter("stdImageLoadDialogTitle"),
          ExportImage.PNG_FILTER, ExportImage.JPG_FILTER);

  public static final ImageContentAttribute ATTR_IMAGE_CONTENT =
    new ImageContentAttribute("image", S.getter("stdImageContents"));

  public static final double MIN_SCALE = 0.1;
  public static final double MAX_SCALE = 10.0;
  public static final Attribute<Double> ATTR_IMAGE_XSCALE =
    Attributes.forDoubleRange("xscale", S.getter("stdImageXScaleAttr"), MIN_SCALE, 1.0, MAX_SCALE);
  public static final Attribute<Double> ATTR_IMAGE_YSCALE =
    Attributes.forDoubleRange("yscale", S.getter("stdImageYScaleAttr"), MIN_SCALE, 1.0, MAX_SCALE);

  public static class ImageContentAttribute extends Attribute<ImageContent> {

    public ImageContentAttribute(String name, StringGetter disp) {
      super(name, disp);
    }

    @Override
    public java.awt.Component getCellEditor(Window source, ImageContent s) {
      return new FileChooser((Frame)source, s);
    }

    @Override
    public String toDisplayString(ImageContent value) {
      if (value == null)
        return S.get("stdImageClickToLoad");
      else if (value.source != null)
        return value.source.relative + " [" + value.source.absolute + "]";
      else
        return String.format(S.get("stdImageImageInfo"), value.format, value.imgData.length);
    }

    @Override
    public String toStandardString(ImageContent value) {
      return toStandardStringRelative(value, null);
    }
    
    @Override
    public String toStandardStringRelative(ImageContent value, String outFilename) {
      if (value == null) {
        return "";
      } else if (value.source != null) {
        return "file:" + ATTR_FILENAME_SINGLETON.toStandardStringRelative(value.source, outFilename);
      } else {
        try {
          ByteArrayOutputStream result = new ByteArrayOutputStream();
          result.write((value.format+":\n").getBytes("UTF-8"));
          byte[] LF = System.lineSeparator().getBytes("UTF-8"); // "\n" or "\r\n"
          OutputStream encoded = Base64.getMimeEncoder(76, LF).wrap(result);
          encoded.write(value.imgData, 0, value.imgData.length);
          try { encoded.flush(); } catch (IOException e) { e.printStackTrace(); }
          try { result.flush(); } catch (IOException e) { e.printStackTrace(); }
          try { encoded.close(); } catch (IOException e) { e.printStackTrace(); }
          try { result.close(); } catch (IOException e) { e.printStackTrace(); }
          return new String(result.toByteArray(), "UTF-8");
        } catch (Exception e) {
          e.printStackTrace();
          return "";
        }
      }
    }

    @Override
    public ImageContent parse(String str) {
      throw new UnsupportedOperationException("parse filename without source");
    }

    @Override
    public ImageContent parseFromUser(Window source, String value) {
      throw new UnsupportedOperationException("parse filename without source");
    }

    @Override
    public ImageContent parseFromFilesystem(File directory, String value) {
      if (value == null || value.equals(""))
        return null;
      String prefix = value.length() >= 5 ? value.substring(0, 5) : value;
      if (prefix.equalsIgnoreCase("file:")) {
        return new ImageContent(ATTR_FILENAME_SINGLETON.parseFromFilesystem(directory, value.substring(5)));
      } else if (prefix.equalsIgnoreCase("PNG:\n") || prefix.equalsIgnoreCase("JPG:\n")) {
        String format = value.substring(0, 3).toUpperCase();
        try {
          byte[] bytes = value.getBytes("UTF-8");
          ByteArrayInputStream input = new ByteArrayInputStream(bytes, 5, bytes.length-5);
          InputStream decoded = Base64.getMimeDecoder().wrap(input);
          bytes = decoded.readAllBytes();
          decoded.close();
          input.close();
          input = new ByteArrayInputStream(bytes);
          BufferedImage img = ImageIO.read(input);
          input.close();
          return new ImageContent(format, img, bytes);
        } catch (IOException e) {
          e.printStackTrace();
          return null;
        }
      } else {
        throw new IllegalArgumentException("Bad image data for " + getName());
      }
    }
  }

  private static class FileChooser extends java.awt.Component implements JInputDialog<ImageContent> {
    Frame parent;
    ImageContent result;

    FileChooser(Frame parent, ImageContent r) {
      this.parent = parent;
      this.result = r;
    }

    public void setValue(ImageContent r) { result = r; }
    public ImageContent getValue() { return result; }

    @SuppressWarnings("unchecked")
    public void setVisible(boolean b) {
      if (!b)
        return;
      JInputDialog<Attributes.LinkedFile> chooser =
        (JInputDialog<Attributes.LinkedFile>)
        ATTR_FILENAME_SINGLETON.getCellEditor(parent, result == null ? null : result.source);

      chooser.setVisible(true);
      Attributes.LinkedFile lf = chooser.getValue();

      if (lf == null)
        return;

      long size;
      try {
        size = Files.size(lf.absolute.toPath());
      } catch (IOException ex) {
        Errors.title(S.get("stdImageErrorTitle")).show(S.get("stdImageErrorMessage"), ex);
        return;
      }

      String[] options = {
        S.get("stdImageEmbedOption"),
        S.get("stdImageLinkOption"),
        S.get("stdImageCancelOption") };
      int choice = JOptionPane.showOptionDialog(parent,
          String.format(S.get("stdImageStorageDialogQuestion"), size),
          S.get("stdImageStorageDialogTitle"), 0,
          JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
      if (choice == 0) {
        try {
          result = new ImageContent(lf.absolute);
        } catch (IOException ex) {
          Errors.title(S.get("stdImageErrorTitle")).show(S.get("stdImageErrorMessage"), ex);
          return;
        }
      } else if (choice == 1) {
          result = new ImageContent(lf);
      } else {
        result = null;
      }
    }
  }

  public Image() {
    super("Image", S.getter("stdImageComponent"));
    setIconName("image.gif");
    setShouldSnap(false);
    setAttributes(
        new Attribute[] { ATTR_IMAGE_CONTENT,
          StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT, StdAttr.LABEL_COLOR, ATTR_IMAGE_XSCALE, ATTR_IMAGE_YSCALE },
        new Object[] { null,
            "", Direction.SOUTH, StdAttr.DEFAULT_LABEL_FONT, Color.BLACK, 1.0, 1.0});
  }
  
  @Override
  protected void configureNewInstance(Instance instance) {
    instance.computeLabelTextField(0);
    instance.addAttributeListener();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Point2D.Double scaling = getScaling(attrs);
    Image.ImageContent content = attrs.getValue(ATTR_IMAGE_CONTENT);
    BufferedImage img = content == null ? null : content.getImage();
    return getOffsetBounds(img, scaling);
  }

  private Bounds getOffsetBounds(BufferedImage img, Point2D.Double scaling) {
    int w = 20, h = 20;
    if (img != null) {
      w = img.getWidth();
      h = img.getHeight();
    }
    return Bounds.create(0, 0, (int)Math.ceil(w*scaling.x), (int)Math.ceil(h*scaling.y));
  }

  @Override
  public boolean HDLIgnore() { return true; }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.LABEL_LOC)
      instance.computeLabelTextField(0);
    else if (attr == ATTR_IMAGE_CONTENT) {
      instance.recomputeBounds();
      instance.fireInvalidated();
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    paint(painter, true, null);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    paint(painter, false, null);
  }

  private void paint(InstancePainter painter, boolean border, Point2D.Double altScaling) {
    Image.ImageContent content = painter.getAttributeValue(ATTR_IMAGE_CONTENT);
    BufferedImage img = content == null ? null : content.getImage();
    Graphics2D g = painter.getGraphics();
    Point2D.Double scaling = altScaling != null ? altScaling : getScaling(painter.getAttributeSet());
    Location loc = painter.getLocation();
    Bounds bds = getOffsetBounds(img, scaling).translate(loc);

    int x = bds.getX();
    int y = bds.getY();
    if (img != null) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
          g2.translate(x, y);
          g2.scale(scaling.x, scaling.y);
          g2.drawImage(img, 0, 0, null);
      } finally {
          g2.dispose();
      }
    } else {
      int w = bds.getWidth();
      int h = bds.getHeight();
      g.setColor(Color.LIGHT_GRAY);
      g.fillRect(x, y, w, h);
      g.setColor(Color.RED);
      g.drawLine(x, y, x+w-1, y+w-1);
      g.drawLine(x, y+w-1, x+w-1, y);
    }

    if (border) {
      g.setColor(Color.GRAY);
      g.drawRect(x, y, bds.getWidth(), bds.getHeight());
    } else {
      painter.drawLabel();
    }
  }

  @Override
  public void propagate(InstanceState state) { }


  // Adapted from https://stackoverflow.com/questions/41015956/
  // Note: the observer code isn't tested, and may not be necessary, as
  // this code is only used for pasting images.
  public static BufferedImage toBufferedImage(java.awt.Image image) {
    if (image instanceof BufferedImage)
      return (BufferedImage)image;
    ReentrantLock lock = new ReentrantLock();
    Condition sizeReady = lock.newCondition();
    Condition dataReady = lock.newCondition();
    ImageObserver obs = (img, flags, x, y, width, height) -> {
      lock.lock();
      try {
        if ((flags & ImageObserver.ALLBITS) != 0) {
          sizeReady.signal();
          dataReady.signal();
          return false;
        }
        if ((flags & (ImageObserver.WIDTH|ImageObserver.HEIGHT)) != 0)
          sizeReady.signal();
        return true;
      }
      finally {
        lock.unlock();
      }
    };
    BufferedImage img;
    lock.lock();
    try {
      int width = -1, height = -1;
      while ((width = image.getWidth(obs)) < 0 || (height = image.getHeight(obs)) < 0 ) {
        sizeReady.awaitUninterruptibly();
      }
      img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      try {
        // g.setBackground(new Color(0, true));
        // g.clearRect(0, 0, width, height);
        while (!g.drawImage(image, 0, 0, obs)) {
          dataReady.awaitUninterruptibly();
        }
      } finally {
        g.dispose();
      }
    } finally {
      lock.unlock();
    }
    return img;
  }

  // private static BufferedImage toBufferedImage(java.awt.Image image) {
  //   if (image instanceof BufferedImage) {
  //     return (BufferedImage)image;
  //   }
  //   int w = image.getWidth(null);
  //   int h  = image.getHeight(null);
  //   BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
  //   Graphics g = img.createGraphics();
  //   g.drawImage(image, 0, 0, null);
  //   g.dispose();
  //   return img;
  // }

  private static byte[] toByteArray(BufferedImage img, String format) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(img, format, out);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    try { out.flush(); } catch (IOException e) { e.printStackTrace(); }
    try { out.close(); } catch (IOException e) { e.printStackTrace(); }
    return out.toByteArray();
  }

  public static Component create(AddTool imageTool, Location loc, java.awt.Image image) {
    BufferedImage img = toBufferedImage(image);
    byte[] imgData = toByteArray(img, "PNG");
    AttributeSet copy = (AttributeSet) imageTool.getAttributeSet().clone();
    Component comp = imageTool.getFactory().createComponent(loc, copy);
    comp.getAttributeSet().setAttr(ATTR_IMAGE_CONTENT, new ImageContent("PNG", img, imgData));
    return comp;
  }

  public static AddTool getToolFromProject(Project proj) {
    Library decor = proj.getLogisimFile().getLibrary("Decor");
    if (decor == null)
      return null;
    Tool tool = decor.getTool("Image");
    if (tool instanceof AddTool && ((AddTool)tool).getFactory() instanceof Image)
      return (AddTool)tool;
    return null;
  }

  public static java.awt.Image getImage(Component comp) {
    ImageContent content = comp.getAttributeSet().getValue(ATTR_IMAGE_CONTENT);
    return content.getImage();
  }
  
  @Override
  public Object getInstanceFeature(Instance instance, Object key) {
    if (key == Reshapable.class)
      return this;
    else if (key == DECORATIVE)
      return Boolean.TRUE;
    else
      return super.getInstanceFeature(instance, key);
  }

  @Override
  public Collection<Location> getReshapeHandles(Component comp) {
    AttributeSet attrs = comp.getAttributeSet();
    Bounds bds = getOffsetBounds(attrs).translate(comp.getLocation());
    Location corner = Location.create(bds.x + bds.width, bds.y + bds.height);
    return List.of(corner);
  }

  private Point2D.Double getScaling(AttributeSet attrs) {
    Double xscale = attrs.getValue(ATTR_IMAGE_XSCALE);
    Double yscale = attrs.getValue(ATTR_IMAGE_YSCALE);
    if (xscale == null) xscale = 1.0;
    if (yscale == null) yscale = 1.0;
    return new Point2D.Double(xscale, yscale);
  }

  private Point2D.Double calculateScaling(AttributeSet attrs, Location loc, int rdx, int rdy) {
    Bounds bds = getOffsetBounds(attrs).translate(loc);
    Location corner = Location.create(bds.x + bds.width, bds.y + bds.height);
    corner = corner.translate(rdx, rdy);

    Image.ImageContent content = attrs.getValue(ATTR_IMAGE_CONTENT);
    BufferedImage img = content == null ? null : content.getImage();
    int w = 20, h = 20;
    if (img != null) {
      w = img.getWidth();
      h = img.getHeight();
    }

    double xs = clamp((corner.x - loc.x) * 1.0 / w, MIN_SCALE, MAX_SCALE);
    double ys = clamp((corner.y - loc.y) * 1.0 / h, MIN_SCALE, MAX_SCALE);
    return new Point2D.Double(xs, ys);
  }

  public double clamp(double val, double min, double max) {
    return Math.min(Math.max(val, min), max);
  }

  @Override
  public void doReshapeAction(Project proj, Circuit circ, Component comp,
      Location handle, int rdx, int rdy) {
    AttributeSet attrs = comp.getAttributeSet();
    Location loc = comp.getLocation();

    Point2D.Double oldScale = getScaling(attrs);
    Point2D.Double newScale = calculateScaling(attrs, loc, rdx, rdy);

    if (oldScale.x == newScale.x && oldScale.y == newScale.y)
      return;

    SetAttributeAction xAct = new SetAttributeAction(circ, S.getter("imageRescale"));
    xAct.set(comp, ATTR_IMAGE_XSCALE, newScale.x);
    SetAttributeAction yAct = new SetAttributeAction(circ, S.getter("imageRescale"));
    xAct.set(comp, ATTR_IMAGE_YSCALE, newScale.y);
    JoinedAction act = new JoinedAction(xAct, yAct);
    proj.doAction(act);
  }

  @Override
  public void drawReshaping(InstancePainter painter, Location handle, int rdx, int rdy) {
    AttributeSet attrs = painter.getAttributeSet();
    Location loc = painter.getLocation();
    Point2D.Double newScale = calculateScaling(attrs, loc, rdx, rdy);
    paint(painter, true, newScale);
  }

}
