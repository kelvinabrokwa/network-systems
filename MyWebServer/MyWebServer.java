/*
 * MyWebServer.java
 */
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.PrintWriter;

public class MyWebServer {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java MyWebServer <port number> <directory>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        String dir = args[1];

        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(port);
        }
        catch (IOException e) {
            System.err.println("Could not open socket on port " + port);
            System.err.println(e.getMessage());
            System.exit(1);
        }

        try {
            Socket socket = serverSocket.accept();
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            }
        }
        catch (IOException e) {
            System.err.println("Could not accept connection");
            System.err.println(e.getMessage());
            System.exit(1);
        }
        finally {
            try {
                serverSocket.close();
            }
            catch (IOException e) {
                System.err.println("Could not close socket");
                System.err.println(e.getMessage());
                System.exit(1);
            }

        }
    }

}
