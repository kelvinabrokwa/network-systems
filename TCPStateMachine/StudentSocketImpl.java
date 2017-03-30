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

    private enum State { LISTENING, SYN_SENT, SYN_RCVD, ESTABLISHED };
    private State state;

    StudentSocketImpl(Demultiplexer D) {  // default constructor
        this.D = D;
    }

    /**
     *
     *
     */
    private synchronized void updateState(State newState) {
        System.out.println("!!!" + state + "->" + newState);
        state = newState;
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
        localport = D.getNextAvailablePort();
        this.port = port;
        this.address = address;

        // register connection socket with demultiplexer
        D.registerConnection(address, localport, port, this);

        // send SYN packet
        int sourcePort = localport;
        int destPort = port;
        int seqNum = 100;
        int ackNum = -1;
        boolean ackFlag = false;
        boolean synFlag = true;
        boolean finFlag = false;
        int windowSize = 1;
        TCPPacket synPacket = new TCPPacket(
                sourcePort,
                destPort,
                seqNum,
                ackNum,
                ackFlag,
                synFlag,
                finFlag,
                windowSize,
                null
                );

        TCPWrapper.send(synPacket, address);

        state = State.SYN_SENT;
    }

    /**
     * Called by Demultiplexer when a packet comes in for this connection
     * @param p The packet that arrived
     */
    public synchronized void receivePacket(TCPPacket p) {
        this.notifyAll();

        switch (state) {
            case LISTENING:
                // expect SYN
                if (!(p.synFlag && !p.ackFlag)) {
                    System.out.println("Incorrect message flags for state.");
                }

                // update state
                address = p.sourceAddr;
                port = p.sourcePort;
                updateState(State.SYN_RCVD);

                try {
                    D.unregisterListeningSocket(localport, this);
                    D.registerConnection(address, localport, port, this);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // reply with SYN-ACK
                int sourcePort = localport;
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

            case SYN_SENT:
                // expect SYN-ACK
                if (!p.synFlag || !p.ackFlag) {
                    System.out.println("Incorrect message flags for state. In state " + state + " : expected SYN-ACK");
                }

                // send an ACK
        }
    }

    /**
     * Waits for an incoming connection to arrive to connect this socket to
     * Ultimately this is called by the application calling
     * ServerSocket.accept(), but this method belongs to the Socket object
     * that will be returned, not the listening ServerSocket.
     * Note that localport is already set prior to this being called.
     */
    public synchronized void acceptConnection() throws IOException {
        // register as listener socket
        localport = getLocalPort();
        D.registerListeningSocket(localport, this);
        state = State.LISTENING;
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
