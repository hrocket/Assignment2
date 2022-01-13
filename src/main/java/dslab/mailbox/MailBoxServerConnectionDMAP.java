package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MailBoxServerConnectionDMAP extends Thread {


    public MailBoxServerConnectionDMAP(Socket socket, Config config, ConcurrentHashMap<Integer, Mail> mailStorage) {
        this.socket = socket;
        this.config = config;
        this.mailStorage = mailStorage;
        this.closed = false;
    }


    @Override
    public void run() {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("ok DMAP2.0");

            String inputLine, outputLine;
            MailBoxDMAP mailBoxDMAP = new MailBoxDMAP(config, mailStorage);
            while((inputLine = in.readLine()) != null && !closed) {
                List<String> response =  mailBoxDMAP.processInput(inputLine.stripLeading());
                for(String responsePiece: response) {
                    if((outputLine = responsePiece) != null) {
                        out.println(outputLine);
                        if (outputLine.equals("ok bye")) {
                            close();
                        }
                    }
                }
            }
            close();
        }
        catch (SocketException e) {
            //shutdown server socket connection
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        this.closed = true;
        if(!this.socket.isClosed())
            this.socket.close();
    }

    private Socket socket;
    private Config config;
    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private boolean closed;
}
