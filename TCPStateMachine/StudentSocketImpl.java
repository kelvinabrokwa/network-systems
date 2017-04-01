import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
    private int windowSize = 1;

    private State state = State.CLOSED;

    private TCPPacket synAckAck;
    private TCPPacket finAck;
    private TCPTimerTask timeWaitTask;

    private Hashtable<State, TCPTimerTask> packetTimers = new Hashtable<State, TCPTimerTask>();

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

        updateState(State.SYN_SENT);

        // send SYN packet
        int sourcePort = localport;
        int destPort = port;
        int seqNum = this.seqNum = ThreadLocalRandom.current().nextInt(0, 1000);
        int ackNum = 0;
        boolean ackFlag = false;
        boolean synFlag = true;
        boolean finFlag = false;
        int windowSize = 1024;
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
        this.seqNum++;

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
            //packetTimers.put(Integer.toString(packet.seqNum), timerTask);
            packetTimers.put(this.state, timerTask);
        }
        TCPWrapper.send(packet, address);
    }

    /**
     * Called by Demultiplexer when a packet comes in for this connection
     * @param p The packet that arrived
     */
    public synchronized void receivePacket(TCPPacket p) {
        TCPPacket packet;

        switch (state) {
            case LISTENING:
                // expect SYN
                if (p.synFlag && !p.ackFlag && !p.finFlag) {
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
                    this.peerSeqNum = p.seqNum;
                    this.seqNum = ThreadLocalRandom.current().nextInt(0, 1000);
                    this.ackNum = this.peerSeqNum + 1;
                    
                    packet = new TCPPacket(
                            this.localport, // sourcePort
                            p.sourcePort, // destPort
                            this.seqNum, // seqNum
                            this.ackNum, // ackNum
                            true, //ackFlag
                            true, // synFlag
                            false, // finFlag
                            1024, // windowSize
                            null // data
                            );
                    sendPacket(packet, true);
                }
                else printWrongPacket(state, p, "SYN");

                break;

            case SYN_SENT:
                // expect SYN-ACK
                if (p.synFlag && p.ackFlag && !p.finFlag) {
                    if (p.ackNum != this.seqNum) {
                        System.out.println("Wrong SYN-ACK. Pack ack: " + 
                                p.ackNum + ", sock seq num: "+ this.seqNum);
                        break;
                    }

                    // stop the timer
                    packetTimers.get(State.SYN_SENT).cancel();

                    // update state
                    updateState(State.ESTABLISHED)  ;

                    // send an ACK
                    this.peerSeqNum = p.seqNum;
                    this.ackNum = p.seqNum + 1;

                    packet = new TCPPacket(
                            localport, // sourcePort
                            p.sourcePort, // destPort
                            this.seqNum, // seqNum
                            this.ackNum, // ackNum
                            true, //ackFlag
                            false, // synFlag
                            false, // finFlag
                            1024, // windowSize
                            null // data
                            );

                    this.synAckAck = packet;
                    sendPacket(packet, false);
                }
                else printWrongPacket(state, p, "SYN-ACK");

                break;

            case SYN_RCVD:
                // in server
                // expecting ACK
                if (p.ackFlag && !p.synFlag && !p.finFlag) {
                    if (p.seqNum != this.ackNum) {
                        System.out.println("WRONG SYN-ACK-ACK PACKET: p seq num: " 
                                + p.seqNum + ", ackNum: " + this.ackNum);
                        break;
                    }
                    // stop the packet timer
                    packetTimers.get(State.SYN_RCVD).cancel();

                    updateState(State.ESTABLISHED);
                }
                else printWrongPacket(state, p, "ACK");

                break;

            case ESTABLISHED:
                if (p.ackFlag && p.synFlag && !p.finFlag) {
                    sendPacket(synAckAck, false);
                    break;
                }
                if (p.finFlag) {
                    updateState(State.CLOSE_WAIT);

                    // send ACK
                    this.peerSeqNum = p.seqNum;
                    this.ackNum = p.seqNum + 1;
                
                    packet = new TCPPacket(
                            localport, // sourcePort
                            p.sourcePort, // destPort
                            this.seqNum, // seqNum
                            this.ackNum, // ackNum
                            true, //ackFlag
                            false, // synFlag
                            false, // finFlag
                            1024, // windowSize
                            null // data
                            );
                    sendPacket(packet, false);
                }

                break;

            case FIN_WAIT_1:
                // Packet is an ACK
                if (p.ackFlag && !p.synFlag && !p.finFlag) {
                    // Check to make sure this is the right ACK
                    if (p.ackNum != this.seqNum) {
                        System.out.println("WRONG ACK");
                        break;
                    }
                    // cancel last FIN retransmission
                    packetTimers.get(State.FIN_WAIT_1).cancel();
                    
                    

                    updateState(State.FIN_WAIT_2);
                }
                // Packet is a FIN
                else if (p.finFlag && !p.ackFlag && !p.synFlag) {
                    updateState(State.CLOSING);

                    //packetTimers.get(State.FIN_WAIT_1).cancel();

                    // send ACK
                    this.peerSeqNum = p.seqNum;
                    this.ackNum = this.peerSeqNum + 1;
            
                    packet = new TCPPacket(
                            this.localport, // sourcePort
                            p.sourcePort, // destPort
                            this.seqNum, // seqNum
                            this.ackNum, // ackNum
                            true, //ackFlag
                            false, // synFlag
                            false, // finFlag
                            1024, // windowSize
                            null // data
                            );
                    this.finAck = packet;
                    sendPacket(packet, false);
                }
                // Means server is in SYN_RCVD and needs SYN-ACK-ACK resent
                else if (p.ackFlag && p.synFlag && !p.finFlag) {
                    sendPacket(this.synAckAck, false);
                }
                else printWrongPacket(state, p, "ACK");

                break;

            case FIN_WAIT_2:
                // expecting a FIN
                if (p.finFlag && !p.ackFlag && !p.synFlag) {
                    // send ACK
                    this.peerSeqNum = p.seqNum;
                    //this.seqNum++;
                    this.ackNum = p.seqNum + 1;
                    
                    packet = new TCPPacket(
                            this.localport, // sourcePort
                            p.sourcePort, // destPort
                            this.seqNum, // seqNum
                            this.ackNum, // ackNum
                            true, //ackFlag
                            false, // synFlag
                            false, // finFlag
                            1024, // windowSize
                            null // data
                            );
                    this.finAck = packet;
                    sendPacket(packet, false);

                    updateState(State.TIME_WAIT);
                    this.timeWaitTask = createTimerTask(10 * 1000, State.TIME_WAIT);
                }
                else printWrongPacket(state, p, "FIN");

                break;

            case LAST_ACK:
                // expecting an ACK
                if (p.ackFlag && !p.finFlag && !p.synFlag) {
                    // verify ACK
                    if (p.ackNum != this.seqNum) {
                        System.out.println("WRONG ACK");
                        System.out.println(p);
                        break;
                    }

                    packetTimers.get(State.LAST_ACK).cancel();

                    updateState(State.TIME_WAIT);
                    this.timeWaitTask = createTimerTask(10 * 1000, State.TIME_WAIT);
                }
                else printWrongPacket(state, p, "ACK");
                
                break;

            case CLOSING:
                // expecting an ACK
                if (p.ackFlag && !p.finFlag && !p.synFlag) {
                    // verify ACK
                    if (p.ackNum != this.seqNum) {
                        System.out.println("This is not the ACK you are looking for");
                        System.out.println(p);
                        break;
                    }

                    packetTimers.get(State.FIN_WAIT_1).cancel();

                    updateState(State.TIME_WAIT);
                    this.timeWaitTask = createTimerTask(10 * 1000, State.TIME_WAIT);
                }
                else if (p.finFlag && !p.ackFlag && !p.synFlag) {
                    sendPacket(this.finAck, false);
                }
                else printWrongPacket(state, p, "ACK");

                break;

            case TIME_WAIT:
                if (p.finFlag && !p.ackFlag && !p.synFlag) {
                    timeWaitTask.cancel();

                    this.peerSeqNum = p.seqNum;
                    this.ackNum= p.seqNum + 1;
                    
                    packet = new TCPPacket(
                            this.localport, // sourcePort
                            p.sourcePort, // destPort
                            this.seqNum, // seqNum
                            this.ackNum, // ackNum
                            true, //ackFlag
                            false, // synFlag
                            false, // finFlag
                            1024, // windowSize
                            null // data
                            );
                    sendPacket(packet, false);

                    System.out.println("RESTARTING TIME_WAIT");
                    this.timeWaitTask = createTimerTask(10 * 1000, State.TIME_WAIT);
                }
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

            System.out.println("\n CLOSE CALLED");

            if (state == State.CLOSE_WAIT) {
                updateState(State.LAST_ACK);
            } else if (state == State.ESTABLISHED) {
                updateState(State.FIN_WAIT_1);
            } else {
                System.out.println("WRONG STATE TO BE CALLING CLOSE SON. CURRENT STATE: " + state);
            }

            // send a FIN packet
            int sourcePort = localport;
            int destPort = port;
            boolean ackFlag = false;
            boolean synFlag = false;
            boolean finFlag = true;
            int windowSize = this.windowSize;
            byte[] data = null;
            TCPPacket finPacket = new TCPPacket(
                    sourcePort,
                    destPort,
                    this.seqNum,
                    this.ackNum,
                    ackFlag,
                    synFlag,
                    finFlag,
                    windowSize,
                    data);


            sendPacket(finPacket, true);
            this.seqNum++;
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
        if (this.state == State.TIME_WAIT) {
            tcpTimer.cancel();
            tcpTimer = null;
            
            try {
                this.D.unregisterConnection(address, localport, port, this);
            } catch (IOException e) {
                e.printStackTrace();
            }

            updateState(State.CLOSED);
            return;
        }
        
        
        // this must run only once the last timer (30 second timer) has expired
        tcpTimer.cancel();
        tcpTimer = null;

        System.out.println("THE FOLLOWING PACKET TIMED OUT:");
        System.out.println((TCPPacket)packet);
        System.out.println("RESENDING PAKCET");
        sendPacket((TCPPacket)packet, true);
        //this.notifyAll();
    }

    /**
     * 
     */
    private synchronized void printWrongPacket(State currState, TCPPacket p, String expected) {
        System.out.println("Incorrect message flags for state. In state " + currState + " : expected " + expected);
        System.out.println("Got the following packet instead");
        System.out.println(p);
    }
}
