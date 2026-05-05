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
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.cburch.logisim.data.Bounds;

public class BoardPanel extends JPanel implements MouseListener, MouseMotionListener {

  private Image scaledImage;
	private int xs, ys, w, h;
	private BoardEditor editor;
  private boolean moving = false;  // true when dragging a selected IO to move it
  private int moveOffsetX, moveOffsetY; // mouse pos relative to IO top-left at drag start

	public BoardPanel(BoardEditor parent) {
    scaledImage = null;
		editor = parent;
	 	xs = ys = w = h = 0;
	 	addMouseListener(this);
	 	addMouseMotionListener(this);
    setBackground(Color.BLACK);
    setPreferredSize(new Dimension(Board.IMG_WIDTH, Board.IMG_HEIGHT));
	}

  public void setImage(File file) throws IOException {
    setImage(ImageIO.read(file));
  }
	
  public void setImage(Image image) {
    if (image != null)
      scaledImage = image.getScaledInstance(getWidth(), getHeight(), Image.SCALE_SMOOTH);
    else
      scaledImage = null;
		repaint();
	}

  public Image getImage() {
    return scaledImage;
  }

	public void clear() {
    scaledImage = null;
	}

	public Boolean isEmpty() {
		return scaledImage == null;
	}


  @Override
	public int getWidth() { return Board.IMG_WIDTH; }
  @Override
	public int getHeight() { return Board.IMG_HEIGHT; }

  @Override
	public void mouseClicked(MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e))
      return;
    if (scaledImage == null) {
      editor.doChangeImage();
    } else {
      BoardIO io = editor.findBoardIO(e.getX(), e.getY());
      if (io != null)
        editor.doBoardIODialog(io);
      else
        editor.clearSelection();
    }
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
      BoardIO io = editor.findBoardIO(e.getX(), e.getY());
      if (io == null)
        setCursor(CROSSHAIR);
      else if (io == editor.selectedIO)
        setCursor(MOVE_CURSOR);
      else
        setCursor(DEFAULT_CURSOR);
    } else {
      setCursor(DEFAULT_CURSOR);
    }
  }

  @Override
	public void mousePressed(MouseEvent e) {
    if (scaledImage == null) return;
    BoardIO sel = editor.selectedIO;
    if (SwingUtilities.isLeftMouseButton(e)
        && sel != null && sel.rect.contains(e.getX(), e.getY())) {
      // Begin moving the selected IO
      moving = true;
      moveOffsetX = e.getX() - sel.rect.x;
      moveOffsetY = e.getY() - sel.rect.y;
      setCursor(MOVE_CURSOR);
      xs = ys = w = h = 0;
    } else {
      // Begin drawing a new rect
      moving = false;
      xs = e.getX();
      ys = e.getY();
      w = h = 0;
    }
	}

  @Override
	public void mouseDragged(MouseEvent e) {
    if (moving) {
      editor.moveSelectedIO(e.getX() - moveOffsetX, e.getY() - moveOffsetY);
    } else if (scaledImage != null) {
      w = e.getX() - xs;
      h = e.getY() - ys;
      repaint();
    }
	}

  @Override
	public void mouseReleased(MouseEvent e) {
    if (moving) {
      moving = false;
      setCursor(DEFAULT_CURSOR); // mouseMoved will refine on next motion
    } else if (scaledImage != null) {
      if (h != 0 && w != 0) {
        Bounds rect = Bounds.create(xs, ys, w, h);
        editor.doRectSelectDialog(rect, e.getX(), e.getY());
      }
      xs = ys = w = h = 0;
      repaint();
    }
	}

  private static final Color MISTY    = new Color(1f, 0f,   0f,   0.4f);
  private static final Color SELECTED = new Color(0f, 0.3f, 1f,   0.5f);

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (scaledImage != null) {
      g.drawImage(scaledImage, 0, 0, null);
      for (BoardIO io: editor.ioComponents) {
        boolean sel = (io == editor.selectedIO);
        g.setColor(sel ? SELECTED : MISTY);
        g.fillRect(io.rect.x, io.rect.y, io.rect.width, io.rect.height);
        g.setColor(sel ? Color.BLUE : Color.RED);
        g.drawRect(io.rect.x, io.rect.y, io.rect.width, io.rect.height);
        io.drawOrientedPins(g, null, null, sel ? Color.BLUE : Color.RED);
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
      g.setColor(Color.gray);
      g.fillRect(0, 0, getWidth(), getHeight());
      String[] lines = {
        "Click to add picture of FPGA board,",
        "or select a Built-in FPGA board below.",
        "",
        "The board picture must be PNG or JPEG format, and should be",
        " at least " + getWidth() + "x" + getHeight() + " pixels for best display." };

      g.setColor(Color.black);
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
