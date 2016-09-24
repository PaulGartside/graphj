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

import java.awt.Color;
import java.awt.Paint;
import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.InetAddress;

public class Graph implements ComponentListener, ActionListener
{
  // ComponentListener interface:
  public void componentHidden ( ComponentEvent e ) {}
  public void componentShown  ( ComponentEvent e ) {}
  public void componentMoved  ( ComponentEvent e ) {}
  public void componentResized( ComponentEvent e )
  {
    Draw_All();
  }

  // ActionListener interface:
  public void actionPerformed( ActionEvent e )
  {
    Object source = e.getSource();

    if     ( source == m_zoom_in   ) ZoomIn();
    else if( source == m_zoop_out  ) ZoomOut();
    else if( source == m_zoop_hor  ) ZoomHorizontal();
    else if( source == m_go_left_s ) GoLeftSide();
    else if( source == m_go_left   ) GoLeft();
    else if( source == m_go_right  ) GoRight();
    else if( source == m_go_right_s) GoRightSide();
    else if( source == m_go_up     ) GoUp();
    else if( source == m_go_down   ) GoDown();
    else if( source == m_exit      ) OnExit();
  }

  public static void main( String[] args )
  {
    try {
      Graph graph = new Graph( args );

      graph.Run();
    }
    catch( Exception e )
    {
      Utils.Handle_Exception( e );
    }
  }
  public Graph( String[] args )
  {
    m_win.addComponentListener( this );

    Parse_Args( args );

    if( null != m_fname )
    {
      m_path  = FileSystems.getDefault().getPath( m_fname );

      m_states.add( m_init_gui );   // First  state
      m_states.add( m_graph_data ); // Second state
    }
    else
    {
      m_path = null;

      m_states.add( m_init_gui );       // First  state
      m_states.add( m_server.m_init );  // Second state
      m_states.add( m_server.m_idle  ); // Third  state
    }
  }
  void Parse_Args( String[] args  )
  {
    final int args_len = args.length;

    if( args_len == 1 ) // Must be file
    {
      m_fname = args[0];
    }
    else if( args_len == 2 ) // Must be -p port
    {
      if( !args[0].equals("-p") ) Usage();

      m_server.m_port = Integer.valueOf( args[1] );
    }
    else if( args_len == 3 ) // Must be -p port file
    {
      if( !args[0].equals("-p") ) Usage();

      m_server.m_port  = Integer.valueOf( args[1] );
               m_fname = args[2];
    }
    else {
      Usage();
    }
  }
  void Run() throws Exception
  {
    while( null != m_states.peekFirst() )
    {
      // Run everything, including startup in the swing thread:
      SwingUtilities.invokeAndWait( m_states.peekFirst() );
    }
  }
  void Usage()
  {
    System.out.println("usage: Graph [-p port] [file]");
    System.exit( 0 );
  }
  void Die( String msg )
  {
    System.out.println("Graph :" + msg );
    System.exit( 0 );
  }
  void Init_GUI()
  {
    Init_Frame();

    m_states.removeFirst();
  }
  void Graph_Data()
  {
    try {
      Init_Data_From_File();
      Det_MinMax();
      Det_Scale();

      m_done_initializing = true;

      Draw_All();

      m_states.removeFirst();
    }
    catch( Exception e )
    {
      Utils.Handle_Exception( e );
    }
  }
  void Get_Titles( String line )
  {
    Matcher tt = pat_title .matcher( line );
    Matcher tx = pat_xtitle.matcher( line );
    Matcher ty = pat_ytitle.matcher( line );
    Matcher l1 = pat_line1 .matcher( line );
    Matcher l2 = pat_line2 .matcher( line );
    Matcher l3 = pat_line3 .matcher( line );
    Matcher l4 = pat_line4 .matcher( line );

    if     ( tt.matches() ) m_cfg.Title    = tt.group(1);
    else if( tx.matches() ) m_cfg.X_Title  = tx.group(1);
    else if( ty.matches() ) m_cfg.Y_Title  = ty.group(1);
    else if( l1.matches() ) m_cfg.L1_Title = l1.group(1);
    else if( l2.matches() ) m_cfg.L2_Title = l2.group(1);
    else if( l3.matches() ) m_cfg.L3_Title = l3.group(1);
    else if( l4.matches() ) m_cfg.L4_Title = l4.group(1);
  }
  void Get_X_Y_Values( String line, int line_num )
  {
    String[] tokens = line.split("(\\s|,)+");
    int non_zero_toks = 0;
    for( int k=0; k<tokens.length; k++ )
    {
      if( 0 < tokens[k].length() ) non_zero_toks++;
    }
    if     ( non_zero_toks == 0 ) ; // Empty line
    else if( 5 < non_zero_toks )
    {
      Die( "'"+ m_fname +"' has too many parameters on line: "+ line_num );
    }
    else if( non_zero_toks == 1 ) // Single y-value, x-value implicit
    {
      double x  = m_cfg.pX.size();             m_cfg.pX .add( x  );
      double y1 = Double.valueOf( tokens[0] ); m_cfg.pY1.add( y1 );
    }
    else {
      double x  = Double.valueOf( tokens[0] ); m_cfg.pX .add( x  );
      double y1 = Double.valueOf( tokens[1] ); m_cfg.pY1.add( y1 );
      if( 2 < non_zero_toks )
      {
        double y2 = Double.valueOf( tokens[2] ); m_cfg.pY2.add( y2 );
        if( 3 < non_zero_toks )
        {
          double y3 = Double.valueOf( tokens[3] ); m_cfg.pY3.add( y3 );
          if( 4 < non_zero_toks )
          {
            double y4 = Double.valueOf( tokens[4] ); m_cfg.pY4.add( y4 );
          }
        }
      }
    }
  }
  void Get_Data( BufferedReader bre ) throws IOException
  {
    int    line_num = 0;
    String line = null;
    while( null != (line = bre.readLine()) )
    {
      line_num++;

      Matcher m = pat_comment.matcher( line );
      if( m.matches() ) // Whole comment line
      {
        Get_Titles( line );
      }
      else {
        Get_X_Y_Values( line, line_num );
      }
    }
  }
  void Init_Data_From_File() throws FileNotFoundException, IOException
  {
    if( !Files.isRegularFile( m_path ) )
    {
      Die("Not a regular file: "+ m_fname );
    }
    m_cfg.Title = m_fname;
    File infile = m_path.toFile();

    FileInputStream     fis = new FileInputStream( infile );
    BufferedInputStream bis = new BufferedInputStream( fis, 512 );
    InputStreamReader   isr = new InputStreamReader( bis );
    BufferedReader      bre = new BufferedReader( isr );

    Get_Data( bre );

    m_mod_time = Utils.ModificationTime( m_path );
  }
  void Init_Frame()
  {
    Font F = new Font( m_font_name, Font.BOLD, 24 );
    Color B = Color.BLUE;
    Color R = Color.RED;

    m_zoom_in   .setFont( F );  m_zoom_in   .setForeground( B );
    m_zoop_out  .setFont( F );  m_zoop_out  .setForeground( B );
    m_zoop_hor  .setFont( F );  m_zoop_hor  .setForeground( B );
    m_go_left_s .setFont( F );  m_go_left_s .setForeground( B );
    m_go_left   .setFont( F );  m_go_left   .setForeground( B );
    m_go_right  .setFont( F );  m_go_right  .setForeground( B );
    m_go_right_s.setFont( F );  m_go_right_s.setForeground( B );
    m_go_up     .setFont( F );  m_go_up     .setForeground( B );
    m_go_down   .setFont( F );  m_go_down   .setForeground( B );
    m_exit      .setFont( F );  m_exit      .setForeground( R );

    m_zoom_in   .addActionListener( this );
    m_zoop_out  .addActionListener( this );
    m_zoop_hor  .addActionListener( this );
    m_go_left_s .addActionListener( this );
    m_go_left   .addActionListener( this );
    m_go_right  .addActionListener( this );
    m_go_right_s.addActionListener( this );
    m_go_up     .addActionListener( this );
    m_go_down   .addActionListener( this );
    m_exit      .addActionListener( this );

    m_panel.add( m_zoom_in   );
    m_panel.add( m_zoop_out  );
    m_panel.add( m_zoop_hor  );
    m_panel.add( m_go_left_s );
    m_panel.add( m_go_left   );
    m_panel.add( m_go_right  );
    m_panel.add( m_go_right_s );
    m_panel.add( m_go_up     );
    m_panel.add( m_go_down   );
    m_panel.add( m_exit      );

    Container content = m_frame.getContentPane();
              content.setLayout( new BorderLayout() );
              content.add( m_panel, BorderLayout.NORTH );
              content.add( m_win  , BorderLayout.CENTER );

    Toolkit   def_tk    = Toolkit.getDefaultToolkit();
    Dimension screen_sz = def_tk.getScreenSize();
    screen_sz.width  *= 0.9;
    screen_sz.height *= 0.9;

    m_frame.setSize( screen_sz.width, screen_sz.height );
    m_frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    m_frame.setVisible( true );
  }
  void Det_MinMax()
  {
    m_cfg.x_min_all = m_cfg.pX .get( 0 );
    m_cfg.x_max_all = m_cfg.pX .get( 0 );
    m_cfg.y_min_all = m_cfg.pY1.get( 0 );
    m_cfg.y_max_all = m_cfg.pY1.get( 0 );

    for( int k=0; k<m_cfg.pX.size(); k+=1 )
    {
      m_cfg.x_min_all = Math.min( m_cfg.x_min_all, m_cfg.pX .get( k ) );
      m_cfg.x_max_all = Math.max( m_cfg.x_max_all, m_cfg.pX .get( k ) );
      m_cfg.y_min_all = Math.min( m_cfg.y_min_all, m_cfg.pY1.get( k ) );
      m_cfg.y_max_all = Math.max( m_cfg.y_max_all, m_cfg.pY1.get( k ) );

      if( 0<m_cfg.pY2.size() )
      {
        m_cfg.y_min_all = Math.min( m_cfg.y_min_all, m_cfg.pY2.get( k ) );
        m_cfg.y_max_all = Math.max( m_cfg.y_max_all, m_cfg.pY2.get( k ) );

        if( 0<m_cfg.pY3.size() )
        {
          m_cfg.y_min_all = Math.min( m_cfg.y_min_all, m_cfg.pY3.get( k ) );
          m_cfg.y_max_all = Math.max( m_cfg.y_max_all, m_cfg.pY3.get( k ) );

          if( 0<m_cfg.pY4.size() )
          {
            m_cfg.y_min_all = Math.min( m_cfg.y_min_all, m_cfg.pY4.get( k ) );
            m_cfg.y_max_all = Math.max( m_cfg.y_max_all, m_cfg.pY4.get( k ) );
          }
        }
      }
    }
    m_cfg.x_min = m_cfg.x_min_all;
    m_cfg.x_max = m_cfg.x_max_all;
    m_cfg.y_min = Math.min( m_cfg.y_min_all, m_cfg.y_min );
    m_cfg.y_max = Math.max( m_cfg.y_max_all, m_cfg.y_max );
  }

  int Det_Power( Ptr_Double axis_sz )
  {
    int power = 0;
    if( 0<axis_sz.val ) {
      while( axis_sz.val > 10 ) { power += 1; axis_sz.val *= 0.1; }
      while( axis_sz.val <  1 ) { power -= 1; axis_sz.val *= 10 ; }
    }
    return power;
  }

  //               power
  // return base*10
//double exp10( double base, int power )
//{
//  while( power < 0 ) { base *= 0.1; power += 1; }
//  while( 0 < power ) { base *= 10 ; power -= 1; }
//
//  return base;
//}

//void Det_Scale()
//{
//  Ptr_Double p_sz_x = new Ptr_Double( m_cfg.sz_x() );
//  Ptr_Double p_sz_y = new Ptr_Double( m_cfg.sz_y() );
//
//  int pow_x = Det_Power( p_sz_x );
//  int pow_y = Det_Power( p_sz_y );
//
//  if     (                    p_sz_x.val < 2 ) m_cfg.tick_x = exp10( 2, pow_x-1 );
//  else if( 2 <= p_sz_x.val && p_sz_x.val < 5 ) m_cfg.tick_x = exp10( 5, pow_x-1 );
//  else if( 5 <= p_sz_x.val && p_sz_x.val <=10) m_cfg.tick_x = exp10(10, pow_x-1 );
//  else Utils.Assert( false, "Det_Scale X Error, p_sz_x = "+ p_sz_x.val );
//
//  if     (                    p_sz_y.val < 2 ) m_cfg.tick_y = exp10( 2, pow_y-1 );
//  else if( 2 <= p_sz_y.val && p_sz_y.val < 5 ) m_cfg.tick_y = exp10( 5, pow_y-1 );
//  else if( 5 <= p_sz_y.val && p_sz_y.val <=10) m_cfg.tick_y = exp10(10, pow_y-1 );
//  else Utils.Assert( false, "Det_Scale Y Error, p_sz_y = "+ p_sz_y.val );
//}
  void Det_Scale()
  {
    Ptr_Double p_sz_x = new Ptr_Double( m_cfg.sz_x() );
    Ptr_Double p_sz_y = new Ptr_Double( m_cfg.sz_y() );

    int pow_x = Det_Power( p_sz_x );
    int pow_y = Det_Power( p_sz_y );

    if     (                    p_sz_x.val < 2 ) m_cfg.tick_x =  2*Math.pow( 10, pow_x-1 );
    else if( 2 <= p_sz_x.val && p_sz_x.val < 5 ) m_cfg.tick_x =  5*Math.pow( 10, pow_x-1 );
    else if( 5 <= p_sz_x.val && p_sz_x.val <=10) m_cfg.tick_x = 10*Math.pow( 10, pow_x-1 );
    else Utils.Assert( false, "Det_Scale X Error, p_sz_x = "+ p_sz_x.val );

    if     (                    p_sz_y.val < 2 ) m_cfg.tick_y =  2*Math.pow( 10, pow_y-1 );
    else if( 2 <= p_sz_y.val && p_sz_y.val < 5 ) m_cfg.tick_y =  5*Math.pow( 10, pow_y-1 );
    else if( 5 <= p_sz_y.val && p_sz_y.val <=10) m_cfg.tick_y = 10*Math.pow( 10, pow_y-1 );
    else Utils.Assert( false, "Det_Scale Y Error, p_sz_y = "+ p_sz_y.val );
  }

  void Draw_The_Title()
  {
    double font_size = 10 + 0.01*m_cfg.win_width
                          + 0.01*m_cfg.win_height;

    Font font_title = new Font( m_font_name, Font.PLAIN, (int)font_size );

    m_win.SetFont( font_title );

    double t_w = m_win.GetTextWidth ( m_cfg.Title );
    double t_h = m_win.GetTextHeight( m_cfg.Title );

    double xtp = 0.5*( m_cfg.win_width - t_w );
    double ytp = m_cfg.win_height*0.01 + t_h;

    m_win.SetPaint( Color.white );
    m_win.DrawString( m_cfg.Title, xtp, ytp );

    m_cfg.y_box_st = (int)(ytp + t_h*0.2 + 0.5);
  }

  void Draw_A_Legend( String text, Paint paint )
  {
    double t_h = m_win.GetTextHeight( text );

    double xtp = 0.4*m_cfg.win_width;
    double ytp =     m_cfg.win_height - m_cfg.line_legend_h + 0.5*t_h;

    m_win.SetPaint( paint );
    m_win.DrawString( text, xtp, ytp );

    final int ix      = (int)(xtp+0.5) - 10;
    final int iy      = (int)(ytp - t_h/2 + 0.5);
    final int x_start = (int)(0.1*m_cfg.win_width + 0.5);

    for( int x=ix; x>x_start; x-=20 )
    {
      m_win.DrawRect( x-2, iy-2, 5, 5 );

      if( x != ix ) m_win.DrawLine( x, iy, x+21, iy );
    }
    m_cfg.line_legend_h += t_h;
  }

  void Draw_Line_Legends()
  {
    double font_size = 8 + 0.002*m_cfg.win_width
                         + 0.002*m_cfg.win_height;

    Font font_legend = new Font( m_font_name, Font.PLAIN, (int)font_size );
    m_win.SetFont( font_legend );

    m_cfg.line_legend_h = (int)(m_win.GetTextHeight("ABC") + 0.5);

    if( 0<m_cfg.pY1.size() )
    {
      if( 0<m_cfg.pY2.size() )
      {
        if( 0<m_cfg.pY3.size() )
        {
          if( 0<m_cfg.pY4.size() )
          {
            if( 0<m_cfg.L4_Title.length() ) Draw_A_Legend( m_cfg.L4_Title, Color.gray );
          }
          if( 0<m_cfg.L3_Title.length() ) Draw_A_Legend( m_cfg.L3_Title, Color.red );
        }
        if( 0<m_cfg.L2_Title.length() ) Draw_A_Legend( m_cfg.L2_Title, Color.cyan );
      }
      if( 0<m_cfg.L1_Title.length() ) Draw_A_Legend( m_cfg.L1_Title, Color.green );
    }
  }

  void Draw_Box_Graticules()
  {
    double font_size = 5 + 0.01*m_cfg.win_width
                         + 0.01*m_cfg.win_height;
    Font font_grats = new Font( m_font_name, Font.PLAIN, (int)font_size );
    m_win.SetFont( font_grats );

    double t_h = m_win.GetTextHeight( "0123456789" );

    m_cfg.x_legend_h = (int)( t_h + 0.5 );
    if( 0<m_cfg.X_Title.length() ) m_cfg.x_legend_h *= 2;

    m_win.SetPaint( Color.white );

    Draw_the_horizontal_graticles();
    Draw_the_vertical_graticles();
    Draw_the_enclosing_box();
  }

//String Double_2_String( final double X )
//{
//  String S = String.valueOf( X );
//
//  if( 0 < X && X < 1 )
//  {
//    // Change values like 0.010000000000002 to 0.01
//    if( 5 < S.length() )
//    {
//      if( S.endsWith("001")
//       || S.endsWith("002")
//       || S.endsWith("003")
//       || S.endsWith("004")
//       || S.endsWith("005")
//       || S.endsWith("006")
//       || S.endsWith("007")
//       || S.endsWith("008")
//       || S.endsWith("009") )
//      {
//        S = S.substring( 0, S.length()-3 );
//
//        while( 3 < S.length() && S.endsWith("0") )
//        {
//          S = S.substring( 0, S.length()-1 );
//        }
//      }
//    }
//  }
//  else if( -1 < X && X < 0 )
//  {
//    // Change values like -0.010000000000002 to -0.01
//    if( 6 < S.length() )
//    {
//      if( S.endsWith("001")
//       || S.endsWith("002")
//       || S.endsWith("003")
//       || S.endsWith("004")
//       || S.endsWith("005")
//       || S.endsWith("006")
//       || S.endsWith("007")
//       || S.endsWith("008")
//       || S.endsWith("009") )
//      {
//        S = S.substring( 0, S.length()-3 );
//
//        while( 4 < S.length() && S.endsWith("0") )
//        {
//          S = S.substring( 0, S.length()-1 );
//        }
//      }
//    }
//  }
//  return S;
//}
  String Double_2_String( final double X )
  {
    String S = String.valueOf( X );

    if( -1 < X && X < 1 )
    {
      final int LEN_1 = X < 0 ? 6 : 5;
      final int LEN_2 = X < 0 ? 4 : 3;

      // Change values like  0.010000000000002 to  0.01
      // Change values like -0.010000000000002 to -0.01
      if( LEN_1 < S.length() )
      {
        if( S.endsWith("001")
         || S.endsWith("002")
         || S.endsWith("003")
         || S.endsWith("004")
         || S.endsWith("005")
         || S.endsWith("006")
         || S.endsWith("007")
         || S.endsWith("008")
         || S.endsWith("009") )
        {
          S = S.substring( 0, S.length()-3 );

          while( LEN_2 < S.length() && S.endsWith("0") )
          {
            S = S.substring( 0, S.length()-1 );
          }
        }
      }
    }
    return S;
  }
  String Float_2_String( final float X )
  {
    String S = String.valueOf( X );

    if( -1 < X && X < 1 )
    {
      final int LEN_1 = X < 0 ? 6 : 5;
      final int LEN_2 = X < 0 ? 4 : 3;

      // Change values like  0.010000000000002 to  0.01
      // Change values like -0.010000000000002 to -0.01
      if( LEN_1 < S.length() )
      {
        if( S.endsWith("001")
         || S.endsWith("002")
         || S.endsWith("003")
         || S.endsWith("004")
         || S.endsWith("005")
         || S.endsWith("006")
         || S.endsWith("007")
         || S.endsWith("008")
         || S.endsWith("009") )
        {
          S = S.substring( 0, S.length()-3 );

          while( LEN_2 < S.length() && S.endsWith("0") )
          {
            S = S.substring( 0, S.length()-1 );
          }
        }
      }
    }
    return S;
  }

  double Find_Max_Y_Number_Width( double y )
  {
    double max_Y_num_w = 0; // Max y number width
    for( ; y<=m_cfg.y_max; y+=m_cfg.tick_y )
    {
      // If y is some tiny value like 0.00123E-10, set it to zero
      //   so that y.str does not distort max_Y_min_w
      if( Math.abs(y) < 0.01*m_cfg.sz_y() ) y = 0;

    //double t_w = m_win.GetTextWidth( String.valueOf( (float)y ) );
      double t_w = m_win.GetTextWidth( Float_2_String( (float)y ) );
      max_Y_num_w = Math.max( t_w, max_Y_num_w );
    }
    return max_Y_num_w;
  }
  double Find_Y_Title_Width()
  {
    double y_title_w = 0;
    if( 0<m_cfg.Y_Title.length() )
    {
      y_title_w = m_win.GetTextHeight( String.valueOf( m_cfg.Y_Title ) );
    }
    // Give the Y numbers some padding in case there is no Y_Title
    y_title_w = Math.max( y_title_w, 0.02*m_cfg.win_width );
    return y_title_w;
  }
  void Draw_Y_Title()
  {
    if( 0<m_cfg.Y_Title.length() )
    {
      double t_w = m_win.GetTextWidth ( m_cfg.Y_Title );
      double t_h = m_win.GetTextHeight( m_cfg.Y_Title );
      double t_a = m_win.GetTextAscent( m_cfg.Y_Title );

      double xtp = t_a/2;
      double ytp = m_cfg.y_box_st + 0.5*( m_cfg.y_box_fn() - m_cfg.y_box_st - t_w );

      m_win.m_g.rotate( Math.PI/2 );
      m_win.DrawString( m_cfg.Y_Title, ytp, -xtp );
      m_win.m_g.rotate( -Math.PI/2 );
    }
  }
  void Draw_the_horizontal_graticles()
  {
    // Start the graticle, specified by y, on a multiple of tick_y
    int   iy = (int)(m_cfg.y_min/m_cfg.tick_y);
    double y = iy*m_cfg.tick_y;
    if( y < m_cfg.y_min ) y += m_cfg.tick_y;

    double max_Y_num_w = Find_Max_Y_Number_Width( y );
    double y_title_w   = Find_Y_Title_Width();

    // m_cfg.y_legend_w must be set before calling Data_2_Line
    //   to draw the horizontal graticles
    final double space_factor = 1.2;
    m_cfg.y_legend_w = (int)(space_factor*( max_Y_num_w + y_title_w )+0.5);

    y = iy*m_cfg.tick_y;
    if( y < m_cfg.y_min ) y += m_cfg.tick_y;

    // Draw the numbers:
    for( ; y<=m_cfg.y_max; y+=m_cfg.tick_y )
    {
      Data_2_Line( m_cfg.x_min, y, m_cfg.x_max, y );

      // If y is some tiny value like 0.00123E-10, set it to zero
      //   so that y.str does not distort max_Y_min_w
      if( Math.abs(y) < 0.01*m_cfg.sz_y() ) y = 0;

    //String y_str = String.valueOf( (float)y );
      String y_str = Float_2_String( (float)y );
      double t_w = m_win.GetTextWidth ( y_str );
      double t_a = m_win.GetTextAscent( y_str );

      double xtp = space_factor*y_title_w + 0.5*( max_Y_num_w - t_w );
      double ytp = Data_2_Pnt_Y( y ) + 0.5*t_a;

      m_win.DrawString( y_str, xtp, ytp );
    }
    Draw_Y_Title();
  }

  void Draw_the_vertical_graticles()
  {
    // Start the graticle, specified by x, on a multiple of tick_x
    int   ix = (int)(m_cfg.x_min/m_cfg.tick_x);
    double x = ix*m_cfg.tick_x;
    if( x < m_cfg.x_min ) x += m_cfg.tick_x;

    for( ; x<=m_cfg.x_max; x+=m_cfg.tick_x )
    {
      Data_2_Line( x, m_cfg.y_min, x, m_cfg.y_max );

      // If x is some tiny value like 0.00123E-10, set it to zero
      if( Math.abs(x) < 0.01*m_cfg.sz_x() ) x = 0;

      String x_str = String.valueOf( x );
      if( 2<x_str.length() && x_str.endsWith(".0") )
      {
        x_str = x_str.substring( 0, x_str.length()-2 );
      }
      double t_w = m_win.GetTextWidth( x_str );
      double t_h = m_win.GetTextHeight( x_str );

      double xtp = Data_2_Pnt_X( x ) - 0.5*t_w;
      double ytp = m_cfg.y_box_fn() + t_h;

      m_win.DrawString( x_str, xtp, ytp );
    }
    if( 0<m_cfg.X_Title.length() )
    {
      double t_w = m_win.GetTextWidth( m_cfg.X_Title );
      double t_h = m_win.GetTextHeight( m_cfg.X_Title );

      double xtp = m_cfg.x_box_st() + 0.5*( m_cfg.x_box_fn() - m_cfg.x_box_st() - t_w );
      double ytp = m_cfg.y_box_fn() + 2*t_h;

      m_win.DrawString( m_cfg.X_Title, xtp, ytp );
    }
  }

  void Draw_the_enclosing_box()
  {
    int w = m_cfg.win_width;
    int h = m_cfg.win_height;

    int x1 = m_cfg.x_box_st();
    int y1 = m_cfg.y_box_st;
    int x2 = m_cfg.x_box_fn();
    int y2 = m_cfg.y_box_fn();

    m_win.DrawLine( x1, y1, x1, y2 );
    m_win.DrawLine( x1, y1, x2, y1 );
    m_win.DrawLine( x1, y2, x2, y2 );
    m_win.DrawLine( x2, y1, x2, y2 );
  }

  int Data_2_Pnt_X( double dx )
  {
    double x_box_width = m_cfg.x_box_fn() - m_cfg.x_box_st();

    double p_x = m_cfg.x_box_st()
               + x_box_width*( (dx-m_cfg.x_min)/m_cfg.sz_x() );

    return (int)(p_x+0.5);
  }
  int Data_2_Pnt_Y( double dy )
  {
    double y_box_height = m_cfg.y_box_fn() - m_cfg.y_box_st;

    double p_y = m_cfg.y_box_st
               + y_box_height*( (m_cfg.y_max-dy)/m_cfg.sz_y() );

    return (int)(p_y+0.5);
  }

  void Data_2_Line( double x1
                  , double y1
                  , double x2
                  , double y2 )
  {
    int ix1 = Data_2_Pnt_X( x1 );
    int ix2 = Data_2_Pnt_X( x2 );
    int iy1 = Data_2_Pnt_Y( y1 );
    int iy2 = Data_2_Pnt_Y( y2 );

    m_win.m_g.drawLine( ix1, iy1, ix2, iy2 );
  }

  void Draw_The_Points( ArrayList<Double> pY )
  {
    for( int k=0; k<m_cfg.pX.size(); k++ )
    {
      double dx = m_cfg.pX.get( k );
      double dy =       pY.get( k );

      int ix = Data_2_Pnt_X( dx );
      int iy = Data_2_Pnt_Y( dy );

      m_win.m_g.drawRect( ix-2, iy-2, 5, 5 );
    }
  }
  void Draw_The_Lines( ArrayList<Double> pY )
  {
    for( int k=1; k<m_cfg.pX.size(); k++ )
    {
      double x1 = m_cfg.pX.get( k-1 );
      double y1 =       pY.get( k-1 );
      double x2 = m_cfg.pX.get( k   );
      double y2 =       pY.get( k   );

      Data_2_Line( x1, y1, x2, y2 );
    }
  }

  void Draw_All()
  {
    m_win.clear();

    if( m_done_initializing )
    {
      m_cfg.win_width = m_win.getWidth();
      m_cfg.win_height= m_win.getHeight();

      // Draw the box
      Draw_The_Title     ();
      Draw_Line_Legends  ();
      Draw_Box_Graticules();
  
      m_win.m_g.setPaint( Color.green );
      Draw_The_Points( m_cfg.pY1 );
      Draw_The_Lines ( m_cfg.pY1 );
      if( 0<m_cfg.pY2.size() )
      {
        m_win.m_g.setPaint( Color.cyan );
        Draw_The_Points( m_cfg.pY2 );
        Draw_The_Lines ( m_cfg.pY2 );
        if( 0<m_cfg.pY3.size() )
        {
          m_win.m_g.setPaint( Color.red );
          Draw_The_Points( m_cfg.pY3 );
          Draw_The_Lines ( m_cfg.pY3 );
          if( 0<m_cfg.pY4.size() )
          {
            m_win.m_g.setPaint( Color.gray );
            Draw_The_Points( m_cfg.pY4 );
            Draw_The_Lines ( m_cfg.pY4 );
          }
        }
      }
    }
    m_win.repaint();
    m_win.setVisible( true );
  }

  void ZoomIn()
  {
    double size_x = m_cfg.sz_x();
    double size_y = m_cfg.sz_y();

    double x_min_old = m_cfg.x_min;
    double x_max_old = m_cfg.x_max;
    double y_min_old = m_cfg.y_min;
    double y_max_old = m_cfg.y_max;
 
    m_cfg.x_max = x_min_old + size_x*0.9;
    m_cfg.x_min = x_max_old - size_x*0.9;
    m_cfg.y_max = y_min_old + size_y*0.9;
    m_cfg.y_min = y_max_old - size_y*0.9;
 
    Det_Scale();
 
    Draw_All();
  }
  void ZoomOut()
  {
    double size_x = m_cfg.sz_x();
    double size_y = m_cfg.sz_y();

    double x_min_old = m_cfg.x_min;
    double x_max_old = m_cfg.x_max;
    double y_min_old = m_cfg.y_min;
    double y_max_old = m_cfg.y_max;
 
    m_cfg.x_max = x_min_old + size_x*99/88;
    m_cfg.x_min = x_max_old - size_x*99/88;
    m_cfg.y_max = y_min_old + size_y*99/88;
    m_cfg.y_min = y_max_old - size_y*99/88;
 
    if( m_cfg.x_min < m_cfg.x_min_all ) m_cfg.x_min = m_cfg.x_min_all;
    if( m_cfg.x_max > m_cfg.x_max_all ) m_cfg.x_max = m_cfg.x_max_all;
    if( m_cfg.y_min < m_cfg.y_min_all ) m_cfg.y_min = m_cfg.y_min_all;
    if( m_cfg.y_max > m_cfg.y_max_all ) m_cfg.y_max = m_cfg.y_max_all;
 
    Det_Scale();  // This seems to work
 
    Draw_All();
  }
  void ZoomHorizontal()
  {
    double size_x = m_cfg.sz_x();
  //double size_y = m_cfg.sz_y();

    double x_min_old = m_cfg.x_min;
    double x_max_old = m_cfg.x_max;
  //double y_min_old = m_cfg.y_min;
  //double y_max_old = m_cfg.y_max;
 
    m_cfg.x_max = x_min_old + size_x*0.9;
    m_cfg.x_min = x_max_old - size_x*0.9;
  //m_cfg.y_max = y_min_old + size_y*0.9;
  //m_cfg.y_min = y_max_old - size_y*0.9;
 
    Det_Scale();
 
    Draw_All();
  }
  void GoLeftSide()
  {
    double size_x = m_cfg.sz_x();

    m_cfg.x_min = m_cfg.x_min_all;
    m_cfg.x_max = m_cfg.x_min + size_x;

    Det_Scale();

    Draw_All();
  }
  void GoLeft()
  {
    double size_x = m_cfg.sz_x();

    double x_min_old = m_cfg.x_min;
    double x_max_old = m_cfg.x_max;

    m_cfg.x_min = x_min_old - size_x*0.5;
    m_cfg.x_max = x_max_old - size_x*0.5;

    if( m_cfg.x_min < m_cfg.x_min_all )
    {
      m_cfg.x_min = m_cfg.x_min_all;
      m_cfg.x_max = m_cfg.x_min_all + size_x;
    }
    Det_Scale();

    Draw_All();
  }
  void GoRight()
  {
    double size_x = m_cfg.sz_x();

    double x_min_old = m_cfg.x_min;
    double x_max_old = m_cfg.x_max;

    m_cfg.x_min = x_min_old + size_x*0.5;
    m_cfg.x_max = x_max_old + size_x*0.5;

    if( m_cfg.x_max > m_cfg.x_max_all )
    {
      m_cfg.x_max = m_cfg.x_max_all;
      m_cfg.x_min = m_cfg.x_max_all - size_x;
    }
    Det_Scale();

    Draw_All();
  }
  void GoRightSide()
  {
    double size_x = m_cfg.sz_x();

    m_cfg.x_max = m_cfg.x_max_all;
    m_cfg.x_min = m_cfg.x_max_all - size_x;

    Det_Scale();

    Draw_All();
  }
  void GoUp()
  {
    double size_y = m_cfg.sz_y();

    double y_min_old = m_cfg.y_min;
    double y_max_old = m_cfg.y_max;

    m_cfg.y_min = y_min_old + size_y*0.1;
    m_cfg.y_max = y_max_old + size_y*0.1;

    if( m_cfg.y_max > m_cfg.y_max_all )
    {
      m_cfg.y_max = m_cfg.y_max_all;
      m_cfg.y_min = m_cfg.y_max_all - size_y;
    }
    Det_Scale();

    Draw_All();
  }
  void GoDown()
  {
    double size_y = m_cfg.sz_y();

    double y_min_old = m_cfg.y_min;
    double y_max_old = m_cfg.y_max;

    m_cfg.y_min = y_min_old - size_y*0.1;
    m_cfg.y_max = y_max_old - size_y*0.1;

    if( m_cfg.y_min < m_cfg.y_min_all )
    {
      m_cfg.y_min = m_cfg.y_min_all;
      m_cfg.y_max = m_cfg.y_min_all + size_y;
    }
    Det_Scale();

    Draw_All();
  }
  void OnExit()
  {
    System.exit( 0 );
  }

  final Deque<Thread> m_states = new ArrayDeque<Thread>();

  final Thread m_init_gui   = new Thread() { public void run() { Init_GUI(); } };
  final Thread m_graph_data = new Thread() { public void run() { Graph_Data(); } };

  final JButton m_zoom_in    = new JButton("[+]");
  final JButton m_zoop_out   = new JButton("[-]");
  final JButton m_zoop_hor   = new JButton("<+>");
  final JButton m_go_left_s  = new JButton("|<-");
  final JButton m_go_left    = new JButton("<-");
  final JButton m_go_right   = new JButton("->");
  final JButton m_go_right_s = new JButton("->|");
  final JButton m_go_up      = new JButton("/|\\");
  final JButton m_go_down    = new JButton("\\|/");
  final JButton m_exit       = new JButton("[X]");

  final Window m_win   = new Window();
  final JPanel m_panel = new JPanel();
  final JFrame m_frame = new JFrame("Graph");

        String m_fname; // Full path and filename
  final Path   m_path;
        long   m_mod_time;

  final Config m_cfg = new Config();
  final String m_font_name = Font.SANS_SERIF;

  final TFTP_Server m_server = new TFTP_Server( this, m_cfg );

  boolean m_done_initializing = false;

  // Optional white space, #, anything else
  final Pattern pat_comment= Pattern.compile("^\\s*#.*$");

  // beg = Optional white space, #, optional white space
  // end = Optional white space, name allowing spaces, optional white space
  final String beg = "^\\s*#\\s*";
  final String end = "\\s*(\\S+(\\s*\\S+)*)\\s*$";
  final Pattern pat_title  = Pattern.compile( beg+"Title:"  +end );
  final Pattern pat_xtitle = Pattern.compile( beg+"X-Title:"+end );
  final Pattern pat_ytitle = Pattern.compile( beg+"Y-Title:"+end );
  final Pattern pat_line1  = Pattern.compile( beg+"Line-1:" +end );
  final Pattern pat_line2  = Pattern.compile( beg+"Line-2:" +end );
  final Pattern pat_line3  = Pattern.compile( beg+"Line-3:" +end );
  final Pattern pat_line4  = Pattern.compile( beg+"Line-4:" +end );
}

