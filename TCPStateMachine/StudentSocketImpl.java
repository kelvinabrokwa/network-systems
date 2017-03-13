import java.net.InetAddress;

public class StudentSocketImpl {
    /**
     *  Called for an active open (client) to connect to address:port
     */
    public synchronized void connect(InetAddress address, int port) throws IOException {

    }

    /**
     * Called by Demultiplexer when a packet arrives for this connection. You must have registered with the
     * Demultiplexer first.
     */
    public synchronized void receivePacket(TCPPacket p) {

    }

    /**
     * Waits for an incoming connection to arrive to connect this socket to. Ultimately this is called by the
     * application calling ServerSocket.accept(), but this method belongs to the Socket object that will be
     * returned, not the listening ServerSocket.
     */
    public synchronized void acceptConnection() throws IOException {

    }

    /**
     * Close the connection. called by the application.
     */
    public synchronized void close() throws IOException {

    }

    /**
     * Handle timer event, called by TCPTimerTask. ref is a generic pointer that you can use to pass data back
     * to this routine when the timer expires. (hint, could be a TCPPacket).
     */
    public synchronized void handleTimer(Object ref) {

    }
}