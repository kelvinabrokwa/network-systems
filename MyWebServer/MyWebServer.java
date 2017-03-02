/*
 * MyWebServer.java
 */
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.util.StringTokenizer;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;

public class MyWebServer {

    private static String fileNotFoundHTML= "File not found";
    private static String notImplementedHTML= "Not Implemented";
    private static String serverName = "Young Money Cash Money: The Server";

    public static void main(String[] args) throws IOException{
        if (args.length != 2) {
            System.err.println("Usage: java MyWebServer <port number> <directory>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        String dir = args[1];

        System.out.println("Server is listening on port :" + port);
        System.out.println("---------------------------------");

        ServerSocket serverSocket = null;

        // creating the listening socket
        try {
            serverSocket = new ServerSocket(port);
        }
        catch (IOException e) {
            System.err.println("Could not open socket on port " + port);
            System.err.println(e.getMessage());
            System.exit(1);
        }

        SimpleDateFormat HTTPDateFormat = new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss zzz");

        // wait for a connectiona and then accept it
        while (true) {
            try (Socket socket = serverSocket.accept()) {
                Header header = new Header();

                BufferedReader bin = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream ostream = socket.getOutputStream();
                String[] req = bin.readLine().split(" ");
                String reqType;
                String fileName;
                try {
                    reqType = req[0];
                    fileName = req[1].substring(1);
                }
                catch (Exception e) {
                    ostream.write(header.setStatus("HTTP/1.1 400 Bad Request").toString().getBytes());
                    continue;
                }

                if (!reqType.equals("GET") && !reqType.equals("HEAD")) {
                    header.setStatus("HTTP/1.1 501 Not Implemented");
                    ostream.write(header.toString().getBytes());
                    continue;
                }

                // try to open file
                File file = new File(fileName);
                Date lastModified = new Date(file.lastModified());

                // file not found
                if (!file.exists()) {
                    header.setStatus("HTTP/1.1 404 Not Found");
                    ostream.write(header.toString().getBytes());
                    continue;
                }

                String headerLine = bin.readLine();

                String ifModifiedSinceVal = null;

                try {
                    while (!headerLine.equals("")) {
                        int colonIdx = headerLine.indexOf(":");
                        String field = headerLine.substring(0, colonIdx);
                        String val = headerLine.substring(colonIdx + 1).trim();
                        if (field.equalsIgnoreCase("If-Modified-Since")) {
                            ifModifiedSinceVal = val;
                        }
                        headerLine = bin.readLine();
                    }
                    if (ifModifiedSinceVal != null) {
                        Date ifModifiedSinceDate = HTTPDateFormat.parse(ifModifiedSinceVal);
                        if (ifModifiedSinceDate.before(lastModified)) {
                            header.setStatus("HTTP/1.1 304 Not Modified")
                                .setLastModified(lastModified)
                                .setContentLength(file.length());
                            ostream.write(header.toString().getBytes());
                            continue;
                        }
                    }
                }
                catch (Exception e) {
                    ostream.write(header.setStatus("HTTP/1.1 400 Bad Request").toString().getBytes());
                    continue;
                }


                // write response header
                header.setStatus("HTTP/1.1 200 OK")
                      .setLastModified(lastModified)
                      .setContentLength(file.length());
                ostream.write(header.toString().getBytes());

                if (reqType.equals("GET")) {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[4096];
                    for (int read; (read = fis.read(data)) > -1;) {
                        ostream.write(data, 0, read);
                    }
                }
            }
            catch (IndexOutOfBoundsException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
            catch (IOException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static class Header {
        private StringBuilder header;
        private SimpleDateFormat HTTPDateFormat;
        private Date currentDate;
        private final String serverName = "Young Money Cash Money: The Server";

        public Header() {
            header = new StringBuilder();
            HTTPDateFormat = new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss zzz");
            currentDate = new Date();

            // set HTTP-date
            header.append("HTTP-date: " + HTTPDateFormat.format(currentDate) + "\r\n");

            // set Server
            header.append("Server: " + serverName + " \r\n");
        }

        public String toString() {
            header.append("\r\n");
            return header.toString();
        }

        public Header setStatus(String status) {
            header.insert(0, status + "\r\n");
            return this;
        }

        public Header setLastModified(Date lastModified) {
            header.append("Last-Modified: " + HTTPDateFormat.format(lastModified) + "\r\n");
            return this;
        }

        public Header setContentLength(long contentLength) {
            header.append("Content-Length: " + contentLength + "\r\n");
            return this;
        }
    }
}

