import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  protected InetAddress address;
  protected int port;
  protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;


  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException {
    this.port = port;
    this.address = address;

    // register connection socket with demultiplexer
    D.registerConnection(address, localport, port, this);

    // send SYN packet
    int sourcePort = localport;
    int destPort = port;
    int seqNum = 0;
    int ackNum = 0;
    boolean ackFlag = false;
    boolean synFlag = true;
    boolean finFlag = false;
    int windowSize = 1024;
    byte[] data = new byte[0];
    TCPPacket synPacket = new TCPPacket(
            sourcePort,
            destPort,
            seqNum,
            ackNum,
            ackFlag,
            synFlag,
            finFlag,
            windowSize,
            data
    );
    TCPWrapper.send(synPacket, address);
  }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p) {
    System.out.println("Receive Packet server? :");
    System.out.println(p.toString());
    /*
    if (p.synFlag && !p.ackFlag) {
      // UNREGISTER THE LISTENING SOCKET?!??!?!?!

      // if this is a listening socket
      StudentSocketImpl connection = new StudentSocketImpl(D);
      connection.localport = D.getNextAvailablePort();
      connection.port = p.sourcePort;
      connection.address = p.sourceAddr;

      // TODO: double check this
      try {
        connection.acceptConnection();
      } catch (IOException e) {
        System.out.println(e);
      }

      // reply with SYN-ACK
      int sourcePort = connection.localport;
      int destPort = p.sourcePort;
      int seqNum = 1; // TODO: make random?
      int ackNum = seqNum + 1;
      boolean ackFlag = true;
      boolean synFlag = true;
      boolean finFlag = false;
      int windowSize = 1024; // TODO: figure out what this is
      byte[] data = new byte[0];
      TCPPacket synAckPacket = new TCPPacket(
              sourcePort,
              destPort,
              seqNum,
              ackNum,
              ackFlag,
              synFlag,
              finFlag,
              windowSize,
              data
      );
      TCPWrapper.send(synAckPacket, p.sourceAddr);
      return;
    }
    else if (p.synFlag && p.ackFlag) {
      // client received SYN-ACK
      // send ACK
    }
    */

    // this is a connection socket
    // deal with packet
  }
  
  /** 
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling 
   * ServerSocket.accept(), but this method belongs to the Socket object 
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
    // we are in a client Socket
    localport = getLocalPort();
    //System.out.println("------>");
    //System.out.println(localport);
    // register as listener socket
    D.registerListeningSocket(localport, this);
  }

  
  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;
    
  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket. 
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
  }

  /** 
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if (tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return 
   * information.
   */
  public synchronized void handleTimer(Object ref){

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;
  }
}
