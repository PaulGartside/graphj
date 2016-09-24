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

import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

class TFTP_Server
{
  TFTP_Server( Graph graph, Config config )
  {
    m_graph = graph;
    m_cfg   = config;
  }
  void Init()
  {
    try {
      m_socket = new DatagramSocket( m_port );
    }
    catch( Exception e )
    {
      System.out.println( e );

      // Cant run server, so remove an extra state so that
      // Idle() does not get run
      m_graph.m_states.removeFirst();
    }
    m_graph.m_states.removeFirst();
  }

  void Idle()
  {
    try {
      m_packet.setLength( DG_PKT_SIZE );

      m_socket.setSoTimeout( 100 );
      m_socket.receive( m_packet );

      if( 0 < m_packet.getLength() )
      {
        if( PacketIsPut() )
        {
          Clear_TFTP();

          m_client_addr   = m_packet.getAddress();
          m_graph.m_fname = Get_Packet_Filename();

          m_graph.m_states.addFirst( m_send_ack );
        }
      }
    }
    catch( SocketTimeoutException e )
    {
      // No problem, Idle() will be called again.
    }
    catch( Exception e )
    {
      System.out.println( e );
    }
  }
  boolean PacketIsPut()
  {
    final byte[] pkt_data = m_packet.getData();
    final int    pkt_len  = m_packet.getLength();

    // If filename is 1 char, and mode is octet, length will be 10
    if( 10 <= pkt_len )
    {
      final int OPCODE = (pkt_data[0] << 8 & 0xFF00) | (pkt_data[1] & 0xFF);

      if( OPCODE == OPCODE_PUT
       && 0   == pkt_data[pkt_len-7]
       && 'o' == pkt_data[pkt_len-6]
       && 'c' == pkt_data[pkt_len-5]
       && 't' == pkt_data[pkt_len-4]
       && 'e' == pkt_data[pkt_len-3]
       && 't' == pkt_data[pkt_len-2]
       && 0   == pkt_data[pkt_len-1] )
      {
        return true;
      }
    }
    return false;
  }
  void Clear_TFTP()
  {
    m_block_num        = 0;
    m_total_bytes_rcvd = 0;
    m_T_start          = System.currentTimeMillis();
    m_data_timeouts    = 0;
    m_rcvd_file.clear();
  }
  String Get_Packet_Filename()
  {
    final byte[] pkt_data = m_packet.getData();
    final int    pkt_len  = m_packet.getLength();

    m_sb.setLength( 0 );
 
    int fname_len = 0;
 
    for( int k=2; k<pkt_len-6 && 0!=pkt_data[k]; k++ )
    {
      fname_len++;
    }
    if( 0<fname_len )
    {
      for( int k=2; k<pkt_len-6 && 0!=pkt_data[k]; k+=1 )
      {
        char c = (char)pkt_data[k];
        m_sb.append( c );
      }
    }
    return m_sb.toString();
  }
  void Send_Ack()
  {
    // Make sure BLOCK_NUM is put into Ack_pkt in big endian format:
    m_packet.setLength( 4 );
    final byte[] pkt_data = m_packet.getData();
    pkt_data[ 0 ] = 0;
    pkt_data[ 1 ] = OPCODE_ACK;
    pkt_data[ 2 ] = (byte)(m_block_num >> 8 & 0xFF);
    pkt_data[ 3 ] = (byte)(m_block_num      & 0xFF);

    try {
      m_packet.setAddress( m_client_addr ); // May not be necessary
      m_packet.setData( pkt_data, 0, 4 );   // May not be necessary
      m_socket.send( m_packet );

      if( 0<m_data_timeouts ) Log("Re-sent ack "+ m_block_num );

      m_graph.m_states.removeFirst();             // Leave m_send_ack
      m_graph.m_states.addFirst( m_wait_4_data ); // Run   m_wait_4_data
    }
    catch( IOException e )
    {
      System.out.println( e );
      // Failed, leave m_send_ack and to back to m_idle
      m_graph.m_states.removeFirst();
    }
  }
  void Wait_4_Data()
  {
    final long ACK_TIMEOUT = 500; // Milli-Seconds
    final long START_TIME  = System.currentTimeMillis();

    for( long time_left = ACK_TIMEOUT
       ; 0 < time_left && m_graph.m_states.peekFirst() == m_wait_4_data
       ; time_left = ACK_TIMEOUT - (System.currentTimeMillis() - START_TIME) )
    {
      int result = Wait_4_Client_Packet( time_left );

      if( result < 0 )
      {
        // Failed, leave m_wait_4_data and to back to m_idle
        m_graph.m_states.removeFirst();
      }
      else if( result == 0 )
      {
        // Timed out
        m_data_timeouts += 1;
        if( m_data_timeouts < 4 )
        {
          // Re-send data
          m_graph.m_states.removeFirst();          // Leave m_wait_4_data
          m_graph.m_states.addFirst( m_send_ack ); // Run   m_send_ack
        }
        else {
          // Too many timeouts, assume client is not responding and drop out of PUT
          Log("Timed out waiting for DATA: "+ (m_block_num+1) );
          m_graph.m_states.removeFirst();
        }
      }
      else // 0 < result, received a packet from CLI_ADDR:CLI_PORT
      {
        // Packet was received from our client, so look at it:
        if( PacketDataBlockNum() == (m_block_num+1) )
        {
          final byte[] pkt_data = m_packet.getData();
          final int    pkt_len  = m_packet.getLength();

          m_block_num += 1;
          m_data_timeouts = 0;
          // First 4 bytes of data packet are header, and rest is data:
          m_total_bytes_rcvd += pkt_len - 4;

          m_graph.m_states.removeFirst();           // Leave m_wait_4_data
          m_graph.m_states.addFirst( m_save_data ); // Run   m_save_data
        }
        else {
        //Log("Packet not data, wait for another packet");
        }
      }
    }
  }
  // Helper function to receive a packet with a TIMEOUT from TGT_ADDR:TGT_PORT
  //
  // Returns 1 if a packet was received
  //         0 if timed out
  //        -1 if an error occured
  //
  int Wait_4_Client_Packet( final long TIMEOUT )
  {
    int status = 1;

    final long START_TIME = System.currentTimeMillis();

    for( long time_left = TIMEOUT
       ; 0 < time_left && 0 < status
       ; time_left = TIMEOUT - (System.currentTimeMillis() - START_TIME) )
    {
      try {
        m_packet.setLength( DG_PKT_SIZE );
        m_socket.setSoTimeout( (int)time_left );
        m_socket.receive( m_packet );

        if( m_packet.getAddress().equals( m_client_addr ) )
        {
          return 1; // Received a packet from TGT_ADDR:TGT_PORT
        }
        else {
          ; // Packet not from target process, so wait for another packet
        }
      }
      catch( SocketTimeoutException e )
      {
        return 0;
      }
      catch( Exception e )
      {
        System.out.println( e );
        return -1;
      }
    }
    return status;
  }
  // If m_packet is a data packet, return block num, else return -1
  int PacketDataBlockNum()
  {
    final byte[] pkt_data = m_packet.getData();
    final int    pkt_len  = m_packet.getLength();

    if( pkt_len < 4 ) return -1;

    final int OPCODE = (pkt_data[0] << 8 & 0xFF00) | (pkt_data[1] & 0xFF);

    if( OPCODE != OPCODE_DATA ) return -1;

    int block_num = (pkt_data[2] << 8 & 0xFF00) | (pkt_data[3] & 0xFF);

    return block_num;
  }
//int PacketDataBlockNum()
//{
//  final byte[] pkt_data = m_packet.getData();
//  final int    pkt_len  = m_packet.getLength();
//
//  if( pkt_len < 4 ) return -1;
//
//  final short OPCODE = (short)(pkt_data[0] << 8 | pkt_data[1]);
//
//  if( OPCODE != OPCODE_DATA ) return -1;
//
//  // Converting signed bytes to int's, and then dropping
//  //   higher order bytes causes the following conversions:
//  //    0 ->   0
//  //    1 ->   1
//  //    ...
//  //  126 -> 126
//  //  127 -> 127
//  // -128 -> 128
//  // -127 -> 129
//  // -126 -> 130
//  //    ...
//  //   -2 -> 254
//  //   -1 -> 255
//  // And so forth.
//  int b_hi = (int)pkt_data[2]; b_hi = b_hi&0xFF;
//  int b_lo = (int)pkt_data[3]; b_lo = b_lo&0xFF;
//
//  int block_num = ( (b_hi<<8) & 0xFF00 ) | (b_lo & 0xFF);
//
//  return block_num;
//}
  void Save_Data()
  {
    final byte[] pkt_data = m_packet.getData();
    final int    pkt_len  = m_packet.getLength();
    final int    DATA_HDR_SZ = 4;

    if( pkt_len < 512+DATA_HDR_SZ )
    {
      // Received last data packet
      m_graph.m_states.removeFirst();            // Leave m_save_data
      m_graph.m_states.addFirst( m_graph_data ); // Run   m_graph_data
    }
    else {
      // More data packets to receive
      m_graph.m_states.removeFirst();          // Leave m_save_data
      m_graph.m_states.addFirst( m_send_ack ); // Run   m_send_ack
    }

    if( DATA_HDR_SZ < pkt_len )
    {
      final int curr_size = m_rcvd_file.size();
      m_rcvd_file.ensureCapacity( curr_size+512 );

      for( int k=DATA_HDR_SZ; k<pkt_len; k++ )
      {
        m_rcvd_file.add( pkt_data[k] );
      }
    }

    if( m_graph.m_states.peekFirst() == m_graph_data )
    {
      // Send last ack:

      // Make sure BLOCK_NUM is put into Ack_pkt in big endian format:
      m_packet.setLength( 4 );
    //final byte[] pkt_data = m_packet.getData();
      pkt_data[ 0 ] = 0;
      pkt_data[ 1 ] = OPCODE_ACK;
      pkt_data[ 2 ] = (byte)(m_block_num >> 8 & 0xFF);
      pkt_data[ 3 ] = (byte)(m_block_num      & 0xFF);

      m_packet.setAddress( m_client_addr ); // May not be necessary

      // Send 3 acks to make sure client gets an ack
    //for( int k=0; k<3; k+=1 )
    //{
        try {
          m_packet.setAddress( m_client_addr ); // May not be necessary
          m_packet.setData( pkt_data, 0, 4 );
          m_socket.send( m_packet );
          Utils.Sleep( 10 );
        }
        catch( IOException e )
        {
          System.out.println( e );
        }
    //}
    }
  }
  void Graph_Data()
  {
    try {
      m_cfg.Clear();

      Init_Data_From_Mem();

      m_graph.Det_MinMax();
      m_graph.Det_Scale();

      m_graph.m_done_initializing = true;

      m_graph.Draw_All();

      m_graph.m_states.removeFirst(); // Leave m_graph_data and back to m_idle
    }
    catch( Exception e )
    {
      Utils.Handle_Exception( e );
    }
  }
  void Init_Data_From_Mem() throws IOException
  {
    m_cfg.Title = m_graph.m_fname;

    Byte[] Data = m_rcvd_file.toArray( new Byte[0] );

    if( null == m_is_data || m_is_data.length != Data.length )
    {
      m_is_data = new byte[ Data.length ];
    }
    for( int k=0; k<m_is_data.length; k++ ) m_is_data[k] = Data[k];

    ByteArrayInputStream bais = new ByteArrayInputStream( m_is_data );
    BufferedInputStream  bis  = new BufferedInputStream( bais, 512 );
    InputStreamReader    isr  = new InputStreamReader( bis );
    BufferedReader       bre  = new BufferedReader( isr );

    m_graph.Get_Data( bre );
  }
  void Log( String s )
  {
    System.out.println( "Graph: "+ s );
  }

  // TFTP constants:
  static final int OPCODE_GET   = 1;
  static final int OPCODE_PUT   = 2;
  static final int OPCODE_DATA  = 3;
  static final int OPCODE_ACK   = 4;
  static final int OPCODE_ERROR = 5;
  static final int DG_PKT_SIZE  = 1024;
  static final int DEFAULT_PORT = 69;

  final Thread m_init        = new Thread() { public void run() { Init(); } };
  final Thread m_idle        = new Thread() { public void run() { Idle(); } };
  final Thread m_send_ack    = new Thread() { public void run() { Send_Ack(); } };
  final Thread m_wait_4_data = new Thread() { public void run() { Wait_4_Data(); } };
  final Thread m_save_data   = new Thread() { public void run() { Save_Data(); } };
  final Thread m_graph_data  = new Thread() { public void run() { Graph_Data(); } };

  Graph           m_graph;
  Config          m_cfg;

  // TFTP variables:
  int             m_port = DEFAULT_PORT;
  DatagramSocket  m_socket;
  DatagramPacket  m_packet = new DatagramPacket( new byte[DG_PKT_SIZE], DG_PKT_SIZE );
  InetAddress     m_client_addr;
  int             m_block_num;
  int             m_total_bytes_rcvd;
  long            m_T_start;
  int             m_data_timeouts;
  StringBuilder   m_sb = new StringBuilder();
  ArrayList<Byte> m_rcvd_file = new ArrayList<>();
  byte[]          m_is_data; // Input stream data
}

