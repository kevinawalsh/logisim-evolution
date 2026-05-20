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

package com.bfh.logisim.fpga;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.util.MouseListenerUtil;

public class BoardPanel extends JPanel implements MouseListener, MouseMotionListener {

  static final int STD_IMG_WIDTH = Board.STD_IMG_WIDTH;
  static final int STD_IMG_HEIGHT = Board.STD_IMG_HEIGHT;

  private byte bytes[]; // original image encoding, saved to xml
  private Image image; // unscaled, parsed from bytes
  private Image scaledImage; // scaled to fit within STD_IMG_WIDTH x STD_IMG_HEIGHT
  private String imgFormat; // "jpg" or "png"
  private double imgScale; // scaledImage = image * imgScale
  private int imgXOffset, imgYOffset; // to center scaledImage within STD_IMG_WIDTH x STD_IMG_HEIGHT
  private int imgScaledWidth, imgScaledHeight;
  private int xs, ys, w, h; // within scaled image
	private BoardEditor editor;
  private boolean making = false;  // iff drawing a new existing IO
  private boolean moving = false;  // iff moving an existing IO
  private int moveOffsetX, moveOffsetY; // mouse pos relative to IO top-left at drag start

	public BoardPanel(BoardEditor parent) {
    bytes = null;
    image = null;
    scaledImage = null;
    imgFormat = null;
		editor = parent;
	 	xs = ys = w = h = 0;
	 	addMouseListener(MouseListenerUtil.clickFix(this));
	 	addMouseMotionListener(this);
    setBackground(Color.BLACK);
    setPreferredSize(new Dimension(STD_IMG_WIDTH, STD_IMG_HEIGHT));
	}

  public void setImage(File file) throws IOException {
    try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) throw new IOException("Unknown image format");
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis);
        setImage(reader.read(0), reader.getFormatName(), Files.readAllBytes(file.toPath()));
      } finally {
        reader.dispose();
      }
    }
  }
	
  public void setImage(Image image, String format, byte bytes[]) {
    if (format.equalsIgnoreCase("png"))
      this.imgFormat = "png";
    else if (format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg"))
      this.imgFormat = "jpg";
    else 
      this.imgFormat = format.toLowerCase(); // ??
    this.bytes = bytes;
    this.image = image;
    scaleImage();
		repaint();
	}

  private void scaleImage() {
    int iw = image.getWidth(null);
    int ih = image.getHeight(null);
    if ((iw == STD_IMG_WIDTH && ih <= STD_IMG_HEIGHT) || (iw <= STD_IMG_WIDTH && ih == STD_IMG_HEIGHT)) {
      this.scaledImage = image;
      this.imgScale = 1.0;
      this.imgScaledWidth = iw;
      this.imgScaledHeight = ih;
    } else {
      double sx = STD_IMG_WIDTH * 1.0 / iw;
      double sy = STD_IMG_HEIGHT * 1.0 / ih;
      this.imgScale = Math.min(sx, sy);
      this.imgScaledWidth = (int)Math.round(imgScale * iw);
      this.imgScaledHeight = (int)Math.round(imgScale * ih);
      this.scaledImage = image.getScaledInstance(imgScaledWidth, imgScaledHeight, Image.SCALE_SMOOTH);
    }
    this.imgXOffset = (STD_IMG_WIDTH - imgScaledWidth) / 2;
    this.imgYOffset = (STD_IMG_HEIGHT - imgScaledHeight) / 2;
  }

  public Image getScaledImage() { return scaledImage; }
  public Image getOriginalImage() { return image; }
  public String getFormat() { return imgFormat; }
  public byte[] getOriginalBytes() { return bytes; }

	public void clear() {
    bytes = null;
    image = null;
    imgFormat = null;
    scaledImage = null;
    imgScale = 0.0;
    imgXOffset = 0;
    imgYOffset = 0;
	}

	public Boolean isEmpty() {
		return image == null;
	}

  @Override
	public int getWidth() { return STD_IMG_WIDTH; } // overrides JPanel.getWidth()
  @Override
	public int getHeight() { return STD_IMG_HEIGHT; } // overrides JPanel.getHeight()

  @Override
	public void mouseClicked(MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e))
      return;
    if (image == null)
      editor.doChangeImage();
    else
      editor.doBoardIODialog(findBoardIO(e));
  }
        
  static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
  static final Cursor CROSSHAIR     = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
  static final Cursor MOVE_CURSOR   = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

  @Override
	public void mouseEntered(MouseEvent e) { mouseMoved(e); }

  @Override
	public void mouseExited(MouseEvent e) { setCursor(DEFAULT_CURSOR); }

  @Override
	public void mouseMoved(MouseEvent e) {
    if (w != 0 || h != 0) {
      setCursor(CROSSHAIR);
    } else if (scaledImage != null) {
      BoardIO io = findBoardIO(e);
      setCursor(io != null
          ? MOVE_CURSOR :
          withinScaledImage(e)
          ? CROSSHAIR :
          DEFAULT_CURSOR);
    } else {
      setCursor(DEFAULT_CURSOR);
    }
  }

  private boolean withinScaledImage(MouseEvent e) {
    return (e.getX() >= imgXOffset && e.getX() < imgXOffset + imgScaledWidth
        && e.getY() >= imgYOffset && e.getY() < imgYOffset + imgScaledHeight);
  }

  @Override
	public void mousePressed(MouseEvent e) {
    making = moving = false;
    if (scaledImage == null) return;
    if (!SwingUtilities.isLeftMouseButton(e))
      return;
    if (!withinScaledImage(e)) {
      editor.clearSelection();
      return;
    }
    BoardIO io = findBoardIO(e);
    if (io != null) {
      // Begin moving this IO; select it first if it isn't already
      if (io != editor.selectedIO)
        editor.doBoardIODialog(io);
      moving = true;
      moveOffsetX = e.getX() - (int)Math.round(imgXOffset + io.rect.x * imgScale);
      moveOffsetY = e.getY() - (int)Math.round(imgYOffset + io.rect.y * imgScale);
      setCursor(MOVE_CURSOR);
      xs = ys = w = h = 0;
    } else {
      // Begin drawing a new rect
      editor.clearSelection();
      making = true;
      xs = e.getX();
      ys = e.getY();
      w = h = 0;
    }
	}

  private BoardIO findBoardIO(MouseEvent e) {
    int ox = (int)Math.round((e.getX() - imgXOffset) / imgScale);
    int oy = (int)Math.round((e.getY() - imgYOffset) / imgScale);
    return editor.findBoardIO(ox, oy);
  }

  @Override
	public void mouseDragged(MouseEvent e) {
    int ex = Math.max(imgXOffset, Math.min(imgXOffset + imgScaledWidth - 1, e.getX()));
    int ey = Math.max(imgYOffset, Math.min(imgYOffset + imgScaledHeight - 1, e.getY()));
    if (moving) {
      int ox = (int)Math.round((ex - moveOffsetX - imgXOffset) / imgScale);
      int oy = (int)Math.round((ey - moveOffsetY - imgYOffset) / imgScale);
      editor.moveSelectedIO(ox, oy);
    } else if (making) {
      w = ex - xs;
      h = ey - ys;
      repaint();
    }
	}

  @Override
	public void mouseReleased(MouseEvent e) {
    if (moving) {
      moving = false;
      setCursor(DEFAULT_CURSOR); // mouseMoved will refine on next motion
    } else if (making) {
      if (h != 0 && w != 0) {
        int ox = (int)Math.round((xs - imgXOffset) / imgScale);
        int oy = (int)Math.round((ys - imgYOffset) / imgScale);
        int ow = Math.max(3, (int)Math.round(w / imgScale));
        int oh = Math.max(3, (int)Math.round(h / imgScale));
        Bounds rect = Bounds.create(ox, oy, ow, oh);
        editor.doRectSelectDialog(rect);
      }
      xs = ys = w = h = 0;
      repaint();
    }
    moving = making = false;
	}

  private static final Color MISTY    = new Color(1f, 0f,   0f,   0.4f);
  private static final Color SELECTED = new Color(0f, 0.3f, 1f,   0.5f);

  @Override
  public void paint(Graphics g1) {
    Graphics2D g = (Graphics2D)g1;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    super.paint(g);
    if (scaledImage != null) {
      g.setColor(Color.BLACK);
      g.fillRect(0, 0, STD_IMG_WIDTH, STD_IMG_HEIGHT);
      g.drawImage(scaledImage, imgXOffset, imgYOffset, null);
      for (BoardIO io: editor.ioComponents) {
        boolean sel = (io == editor.selectedIO);
        Rectangle2D r = new Rectangle2D.Double(
            imgXOffset + io.rect.x * imgScale, imgYOffset + io.rect.y * imgScale,
            io.rect.width * imgScale, io.rect.height * imgScale);
        g.setColor(sel ? SELECTED : MISTY);
        g.fill(r);
        g.setColor(sel ? Color.BLUE : Color.RED);
        g.draw(r);
        io.drawOrientedPins(g, imgXOffset, imgYOffset, imgScale, null, null, sel ? Color.CYAN : Color.ORANGE);
      }
      g.setColor(Color.RED);
      if (w != 0 || h != 0) {
        int xr, yr, wr, hr;
        xr = (w < 0) ? xs + w : xs;
        yr = (h < 0) ? ys + h : ys;
        wr = (w < 0) ? -w : w;
        hr = (h < 0) ? -h : h;
        g.drawRect(xr, yr, wr, hr);
      }
    } else {
      g.setColor(Color.GRAY);
      g.fillRect(0, 0, getWidth(), getHeight());
      String[] lines = {
        "Click to add picture of FPGA board,",
        "or select a Built-in FPGA board below.",
        "",
        "The board picture must be PNG or JPEG format, and ideally",
        "fit within " + STD_IMG_WIDTH + "x" + STD_IMG_HEIGHT + " pixels for best display." };

      g.setColor(Color.BLACK);
      g.setFont(new Font(g.getFont().getFontName(), Font.BOLD, 18));

      int ypos = 100;
      for (String msg : lines) {
        FontMetrics fm = g.getFontMetrics();
        float ascent = fm.getAscent();
        int xpos = (getWidth() - fm.stringWidth(msg)) / 2;
        ypos += ascent*3/2;
        if (msg.equals(""))
          g.setFont(new Font(g.getFont().getFontName(), Font.BOLD, 16));
        else
          g.drawString(msg, xpos, ypos);
      }
    }
  }

}
