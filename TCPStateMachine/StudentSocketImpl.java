import java.net.*;
import java.io.*;
import java.util.*;

class StudentSocketImpl extends BaseSocketImpl {

    // SocketImpl data members:
    protected InetAddress address;
    protected int port;
    protected int localport;

    private Demultiplexer D;
    private Timer tcpTimer;

    private int ackNum;
    private int seqNum;
    private int peerSeqNum;
    private int lastAck;
    private int windowSize = 1;

    private State state = State.CLOSED;

    private Hashtable<String, TCPTimerTask> packetTimers = new Hashtable<String, TCPTimerTask>();

    private enum State {
        LISTENING,
        SYN_SENT,
        SYN_RCVD,
        ESTABLISHED,
        CLOSE_WAIT,
        CLOSING,
        CLOSED,
        FIN_WAIT_1,
        FIN_WAIT_2,
        LAST_ACK,
        TIME_WAIT
    };


    StudentSocketImpl(Demultiplexer D) {  // default constructor
        this.D = D;
    }

    /**
     * Updates the state and console logs
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
        int seqNum = 0;
        int ackNum = lastAck = 0;
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
                null);

        sendPacket(synPacket, true);

        updateState(State.SYN_SENT);

        // wait until state becomes established
        synchronized(this) {
            while (state != State.ESTABLISHED) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Handles timing stuff and junk
     */
    private synchronized void sendPacket(TCPPacket packet, boolean retransmit) {
        if (retransmit) {
            TCPTimerTask timerTask = createTimerTask(1000, packet);
            packetTimers.put(Integer.toString(packet.seqNum), timerTask);
        }
        TCPWrapper.send(packet, address);
    }

    /**
     * Called by Demultiplexer when a packet comes in for this connection
     * @param p The packet that arrived
     */
    public synchronized void receivePacket(TCPPacket p) {
        int sourcePort;
        int destPort;
        //int seqNum;
        int ackNum;
        boolean ackFlag;
        boolean synFlag;
        boolean finFlag;
        int windowSize;
        byte[] data;
        TCPPacket packet;

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
                sourcePort = localport;
                destPort = p.sourcePort;
                this.peerSeqNum = p.seqNum;
                this.seqNum = 0;
                ackNum = lastAck = seqNum + 1;
                ackFlag = true;
                synFlag = true;
                finFlag = false;
                windowSize = 1024; // TODO: figure out what this is
                data = null;
                packet = new TCPPacket(
                        sourcePort,
                        destPort,
                        seqNum,
                        ackNum,
                        ackFlag,
                        synFlag,
                        finFlag,
                        windowSize,
                        data);
                sendPacket(packet, true);

                break;

            case SYN_SENT:
                // expect SYN-ACK
                if (!p.synFlag || !p.ackFlag) {
                    System.out.println("Incorrect message flags for state. In state " + state +
                            " : expected SYN-ACK");
                }

                // stop the timer
                packetTimers.get(Integer.toString(seqNum)).cancel();

                // send an ACK
                sourcePort = localport;
                destPort = p.sourcePort;
                this.peerSeqNum = p.seqNum;
                this.seqNum++;
                ackNum = lastAck = p.seqNum + 1;
                ackFlag = true;
                synFlag = false;
                finFlag = false;
                windowSize = 1024;
                data = null;
                packet = new TCPPacket(
                        sourcePort,
                        destPort,
                        seqNum,
                        ackNum,
                        ackFlag,
                        synFlag,
                        finFlag,
                        windowSize,
                        data);
                sendPacket(packet, false);

                // update state
                updateState(State.ESTABLISHED);

                break;

            case SYN_RCVD:
                // in server
                // expecting ACK
                if (!p.ackFlag || (p.finFlag || p.synFlag)) {
                    System.out.println("Incorrect message flags for state. In state " + state + " : expected ACK");
                    System.out.println("Got the following packet instead");
                    System.out.println(p);
                }

                // stop the packet timer
                packetTimers.get(Integer.toString(p.ackNum - 1)).cancel();

                updateState(State.ESTABLISHED);

                break;

            case ESTABLISHED:
                if (p.finFlag) {
                    // send ACK
                    sourcePort = localport;
                    destPort = p.sourcePort;
                    this.peerSeqNum = p.seqNum;
                    this.seqNum++;
                    ackNum = lastAck = p.seqNum + 1;
                    ackFlag = true;
                    synFlag = false;
                    finFlag = false;
                    windowSize = 1024;
                    data = null;
                    packet = new TCPPacket(
                            sourcePort,
                            destPort,
                            seqNum,
                            ackNum,
                            ackFlag,
                            synFlag,
                            finFlag,
                            windowSize,
                            data);
                    sendPacket(packet, false);

                    updateState(State.CLOSE_WAIT);
                }

                break;

            case FIN_WAIT_1:
                // expecting an ACK or a FIN
                if ((!p.ackFlag && !p.finFlag) || (p.ackFlag && p.finFlag) || p.synFlag) {
                    System.out.println("Incorrect message flags for state. In state " + state + " : expected ACK");
                    System.out.println("Got the following packet instead");
                    System.out.println(p);
                }

                // this ack is not meant for me
                if (p.ackFlag) {
                    if (p.seqNum != lastAck) {
                        return;
                    }
                }

                // cancel last FIN retransmission
                packetTimers.get(Integer.toString(seqNum)).cancel();

                if (p.ackFlag) {
                    updateState(State.FIN_WAIT_2);
                }
                else if (p.finFlag) {
                    updateState(State.CLOSING);

                    // send ACK
                    sourcePort = localport;
                    destPort = p.sourcePort;
                    this.peerSeqNum = p.seqNum;
                    this.seqNum++;
                    ackNum = lastAck = p.seqNum + 1;
                    ackFlag = true;
                    synFlag = false;
                    finFlag = false;
                    windowSize = 1024;
                    data = null;
                    packet = new TCPPacket(
                            sourcePort,
                            destPort,
                            seqNum,
                            ackNum,
                            ackFlag,
                            synFlag,
                            finFlag,
                            windowSize,
                            data);
                    sendPacket(packet, false);
                } else {
                    System.out.println("Wrong flags for state. Expected ACK or FIN");
                }

                break;

            case FIN_WAIT_2:
                // expecting an ACK
                if (!p.finFlag || (p.ackFlag || p.synFlag)) {
                    System.out.println("Incorrect message flags for state. In state " + state + " : expected FIN");
                }

                // send ACK
                sourcePort = localport;
                destPort = p.sourcePort;
                this.peerSeqNum = p.seqNum;
                this.seqNum++;
                ackNum = lastAck = p.seqNum + 1;
                ackFlag = true;
                synFlag = false;
                finFlag = false;
                windowSize = 1024;
                data = null;
                packet = new TCPPacket(
                        sourcePort,
                        destPort,
                        seqNum,
                        ackNum,
                        ackFlag,
                        synFlag,
                        finFlag,
                        windowSize,
                        data);
                sendPacket(packet, false);

                break;

            case LAST_ACK:
            case CLOSING:
                // expecting an ACK
                if (!p.ackFlag || (p.finFlag || p.synFlag)) {
                    System.out.println("Incorrect message flags for state. In state " + state + " : expected ACK");
                }

                // verify ACK
                if (p.seqNum != lastAck) {
                    break;
                }

                updateState(State.TIME_WAIT);

                try {
                    Thread.sleep(1*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    D.unregisterConnection(address, localport, port, this);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                updateState(State.CLOSED);


                break;
        }

        this.notifyAll();
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
        updateState(State.LISTENING);

        // wait until state becomes established
        synchronized(this) {
            while (state != State.ESTABLISHED) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
        if (address != null) {
            if (state != State.ESTABLISHED && state != State.CLOSE_WAIT) {
                System.out.println("Probably already closing. Ignoring this closing call.");
                return;
            }

            // send a FIN packet
            int sourcePort = localport;
            int destPort = port;
            this.seqNum++;
            int ackNum = lastAck; //this.peerSeqNum + 1;
            boolean ackFlag = false;
            boolean synFlag = false;
            boolean finFlag = true;
            int windowSize = this.windowSize;
            byte[] data = null;
            TCPPacket finPacket = new TCPPacket(
                    sourcePort,
                    destPort,
                    seqNum,
                    ackNum,
                    ackFlag,
                    synFlag,
                    finFlag,
                    windowSize,
                    data);


            System.out.println(seqNum);
            sendPacket(finPacket, true);

            if (state == State.CLOSE_WAIT) {
                updateState(State.LAST_ACK);
            } else if (state == State.ESTABLISHED) {
                updateState(State.FIN_WAIT_1);
            } else {
                System.out.println("WRONG STATE TO BE CALLING CLOSE SON. CURRENT STATE: " + state);
            }
        } else {
            // this is a ServerSocket
            updateState(State.CLOSED);
        }
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
    public synchronized void handleTimer(Object packet){
        // this must run only once the last timer (30 second timer) has expired
        tcpTimer.cancel();
        tcpTimer = null;

        System.out.println("THE FOLLOWING PACKET TIMED OUT:");
        System.out.println((TCPPacket)packet);
        sendPacket((TCPPacket)packet, true);
    }
}
