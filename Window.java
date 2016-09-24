////////////////////////////////////////////////////////////////////////////////
// Java Graphing Program (graphj)                                             //
// Copyright (c) 07 Sep 2015 Paul J. Gartside                                 //
////////////////////////////////////////////////////////////////////////////////
// Permission is hereby granted, free of charge, to any person obtaining a    //
// copy of this software and associated documentation files (the "Software"), //
// to deal in the Software without restriction, including without  limitation //
// the rights to use, copy, modify, merge, publish, distribute, sublicense,   //
// and/or sell copies of the Software, and to permit persons to whom the      //
// Software is furnished to do so, subject to the following conditions:       //
//                                                                            //
// The above copyright notice and this permission notice shall be included in //
// all copies or substantial portions of the Software.                        //
//                                                                            //
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR //
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,   //
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL    //
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER //
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING    //
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER        //
// DEALINGS IN THE SOFTWARE.                                                  //
////////////////////////////////////////////////////////////////////////////////

import java.awt.Graphics;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Font;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;

class Window extends JComponent
             implements MouseListener, MouseMotionListener
{
  // MouseListener interface:
  public void mouseClicked ( MouseEvent e ) {}
  public void mouseEntered ( MouseEvent e ) {}
  public void mouseExited  ( MouseEvent e ) {}
  public void mouseReleased( MouseEvent e ) {}
  public void mousePressed ( MouseEvent e )
  {
    m_old_x = e.getX();
    m_old_y = e.getY();
  }

  // MouseMotionListener interface:
  public void mouseMoved  ( MouseEvent e ) {}
  public void mouseDragged( MouseEvent e )
  {
    m_curr_x = e.getX();
    m_curr_y = e.getY();

    if( m_g != null )
    {
    //SetPaint( Color.white );
      m_g.setPaint( Color.magenta );

      m_g.drawLine( m_old_x, m_old_y, m_curr_x, m_curr_y );
    }
    m_old_x = m_curr_x;
    m_old_y = m_curr_y;

    repaint();
  }

  Window()
  {
    setDoubleBuffered( false );
    addMouseListener( this );
    addMouseMotionListener( this );
  }

  public void paintComponent( Graphics g )
  {
    if( m_image == null )
    {
      clear();
    }
    g.drawImage( m_image, 0, 0, null );
  }

  void clear()
  {
    final int curr_size_image_w = getSize().width;
    final int curr_size_image_h = getSize().height;

    if( null == m_image
     || getSize().width  != m_size_image_w
     || getSize().height != m_size_image_h )
    {
      m_size_image_w = getSize().width ;
      m_size_image_h = getSize().height;

      m_image = createImage( m_size_image_w, m_size_image_h );

      m_g = (Graphics2D)m_image.getGraphics();
    }
    m_g.setPaint( Color.black );
    m_g.fillRect( 0, 0, getSize().width, getSize().height );
    m_g.setPaint( Color.white );
  }

  void SetPaint( Paint paint )
  {
    m_g.setPaint( paint );
  }
  void SetFont( Font font )
  {
    m_g.setFont( font );
  }
  Font GetFont()
  {
    return m_g.getFont();
  }
  double GetTextWidth( String text )
  {
    FontRenderContext frc = m_g.getFontRenderContext();
    Rectangle2D bounds = m_g.getFont().getStringBounds( text, frc );
    return bounds.getWidth();
  }
  double GetTextHeight( String text )
  {
    FontRenderContext frc = m_g.getFontRenderContext();
    Rectangle2D bounds = m_g.getFont().getStringBounds( text, frc );
    return bounds.getHeight();
  }
  double GetTextAscent( String text )
  {
    FontRenderContext frc = m_g.getFontRenderContext();
    LineMetrics metrics = m_g.getFont().getLineMetrics( text, frc );
    return metrics.getAscent();
  }
  double GetTextDescent( String text )
  {
    FontRenderContext frc = m_g.getFontRenderContext();
    LineMetrics metrics = m_g.getFont().getLineMetrics( text, frc );
    return metrics.getDescent();
  }
  double GetTextLeading( String text )
  {
    FontRenderContext frc = m_g.getFontRenderContext();
    LineMetrics metrics = m_g.getFont().getLineMetrics( text, frc );
    return metrics.getLeading();
  }

  void DrawLine( int x1, int y1, int x2, int y2 )
  {
    m_g.drawLine( x1, y1, x2, y2 );
  }
  void DrawRect( int x, int y, int w, int h )
  {
    m_g.drawRect( x, y, w, h );
  }
  void DrawString( String s, double x, double y )
  {
    m_g.drawString( s, (int)(x+0.5), (int)(y+0.5) );
  }

  Image      m_image;
  Graphics2D m_g;
  int        m_size_image_w;
  int        m_size_image_h;
  int        m_curr_x;
  int        m_curr_y;
  int        m_old_x;
  int        m_old_y;
}

