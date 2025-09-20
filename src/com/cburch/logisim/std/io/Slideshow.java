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

package com.cburch.logisim.std.io;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import com.cburch.logisim.comp.ComponentData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.std.base.Image;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;

public class Slideshow extends InstanceFactory {

  private static class State implements ComponentData {
    int w, h, n;
    Image.ImageContent[] slides; // always large enough for MAX_SLIDES
    int cur;

    State(int w, int h, int n) {
      this.w = w;
      this.h = h;
      this.n = n;
      this.slides = new Image.ImageContent[MAX_SLIDES];
      this.cur = -1;
    }

    State(State other) {
      w = other.w;
      h = other.h;
      n = other.n;
      cur = other.cur;
      slides = new Image.ImageContent[MAX_SLIDES];
      for (int i = 0; i < n; i++)
        slides[i] = new Image.ImageContent(other.slides[i]);
    }

    void updateSize(int w, int h, int n) {
      this.w = w;
      this.h = h;
      this.n = n;
      if (this.cur >= n)
        this.cur = -1;
    }

    void updateSlide(int i, Image.ImageContent s) {
      if (i < 0 | i >= n)
        return;
      slides[i] = s;
    }

    void deselect() { 
      cur = -1;
    }

    void selectSlide(int i) {
      cur = i % n;
    }

    BufferedImage getImage() {
      if (cur < 0 || slides[cur] == null) {
        return null;
      }
      return slides[cur].getImage();
    }

    @Override
    public State duplicateForNewSimulation() {
      return new State(this);
    }
  }

  static final AttributeOption SCALE = new AttributeOption("scale",
      S.getter("ioSlideshowScaleOption"));
  static final AttributeOption SCALE_CROP = new AttributeOption("scale-crop",
      S.getter("ioSlideshowScaleCropOption"));
  static final AttributeOption STRETCH = new AttributeOption("stretch",
      S.getter("ioSlideshowStretchOption"));
  static final AttributeOption CROP = new AttributeOption("crop",
      S.getter("ioSlideshowCropOption"));

  static final Attribute<AttributeOption> ATTR_FIT = 
      Attributes.forOption("fit", S.getter("ioSlideshowFit"),
          new AttributeOption[] { SCALE, SCALE_CROP, STRETCH, CROP });

  public static final int MAX_SLIDES = 32;

  public static final Attribute<BitWidth> ATTR_WIDTH = Attributes.forBitWidth(
      "addrWidth", S.getter("ioSlideshowAddrWidth"), 1, 32);

  public static final Attribute<Integer> ATTR_COUNT =
      Attributes.forIntegerRange("count", S.getter("ioSlideshowCount"), 1, MAX_SLIDES);

  public static final Attribute<Integer> ATTR_IMG_WIDTH =
      Attributes.forIntegerRange("width", S.getter("ioSlideshowWidth"), 10, 640);

  public static final Attribute<Integer> ATTR_IMG_HEIGHT =
      Attributes.forIntegerRange("height", S.getter("ioSlideshowHeight"), 10, 640);
 
  public static final ArrayList<Attribute<Image.ImageContent>> ATTR_SLIDE = new ArrayList<>();
  static {
    for (int i = 0; i < MAX_SLIDES; i++)
      ATTR_SLIDE.add(new Image.ImageContentAttribute("image"+i, S.getter("ioSlideshowImage", ""+i)));
  }

  public Slideshow() {
    super("Slideshow", S.getter("ioSlideshowComponent"));
    setKeyConfigurator(new BitWidthConfigurator(ATTR_WIDTH, 1, 30));
    setIconName("slideshow.gif");
    setPorts(new Port[] { new Port(0, 0, Port.INPUT, ATTR_WIDTH) });
    // setInstancePoker(Poker.class);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new SlideshowAttributes();
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int w = attrs.getValue(ATTR_IMG_WIDTH).intValue();
    int h = attrs.getValue(ATTR_IMG_HEIGHT).intValue();
    return Bounds.create(0, -h+10, w, h);
  }

  private State getState(InstanceState state) {
    int w = state.getAttributeValue(ATTR_IMG_WIDTH).intValue();
    int h = state.getAttributeValue(ATTR_IMG_HEIGHT).intValue();
    int n = state.getAttributeValue(ATTR_COUNT).intValue();
      
    State data = (State) state.getDataFor();
    if (data == null) {
      data = new State(w, h, n);
      state.setData(data);
    } else {
      data.updateSize(w, h, n);
    }
    for (int i = 0; i < n; i++)
      data.updateSlide(i, state.getAttributeValue(ATTR_SLIDE.get(i)));

    return data;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_IMG_WIDTH || attr == ATTR_IMG_HEIGHT)
      instance.recomputeBounds();
    instance.fireInvalidated();
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    State data = getState(painter);
    Bounds bds = painter.getNominalBounds();
    boolean showState = painter.getShowState();
    Graphics g = painter.getGraphics();
    AttributeOption fit = painter.getAttributeValue(ATTR_FIT);

    int w = data.w;
    int h = data.h;
    
    if (showState) {
      int x = bds.getX();
      int y = bds.getY();
      BufferedImage img = data.getImage();
      if (img != null) {
        if (fit == STRETCH) {
          g.drawImage(img,
              x, y, x+w, y+h,
              0, 0, img.getWidth(), img.getHeight(),
              null);
        } else if (fit == SCALE) {
          double f = Math.max(1.0*img.getWidth()/w, 1.0*img.getHeight()/h);
          g.drawImage(img,
              x, y, x+w, y+h,
              0, 0, (int)(f*w), (int)(f*h),
              null);
        } else if (fit == SCALE_CROP) {
          double f = Math.min(1.0*img.getWidth()/w, 1.0*img.getHeight()/h);
          g.drawImage(img,
              x, y, x+w, y+h,
              0, 0, (int)(f*w), (int)(f*h),
              null);
        } else { // crop
          g.drawImage(img,
              x, y, x+w, y+h,
              0, 0, w, h,
              null);
        }
      } else {
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(x, y, w, h);
        g.setColor(Color.RED);
        g.drawLine(x, y, x+w-1, y+h-1);
        g.drawLine(x, y+h-1, x+w-1, y);
      }
      // TODO: could add inputs to control rotation, scaling, translucency, etc.
    }

    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    int n = state.getAttributeValue(ATTR_COUNT).intValue();

    State data = getState(state);
    int v = state.getPortValue(0).toIntValue();
    if (v < 0)
      data.deselect();
    else
      data.selectSlide(v);
  }

}
