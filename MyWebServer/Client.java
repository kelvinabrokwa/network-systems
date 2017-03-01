import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStream;

public class Client{
    public static void main(String[] args) {
        Socket server;
        //InputStream in;
        OutputStream out;
        PrintWriter pout;
        try {
            server = new Socket("~/myweb/", 8817);
            
            //in = server.getInputStream();
            out = server.getOutputStream();

            pout = new PrintWriter(out, true);
            pout.println("Hello!");

            server.close();
        }
        catch (UnknownHostException e) {
            System.err.println("Can't find host.");
            System.exit(1);
        }
        catch (IOException e) {
            System.err.println("Error connecting to host.");
            System.exit(1);
        }
        catch (Exception e) {
            System.err.println("Failed.");
            System.exit(1);
        }
    }
}