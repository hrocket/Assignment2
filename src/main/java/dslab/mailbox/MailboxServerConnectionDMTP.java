package dslab.mailbox;

import dslab.util.Config;
import dslab.util.DMTP;
import dslab.util.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class MailboxServerConnectionDMTP extends Thread {

    public MailboxServerConnectionDMTP(Socket socket, ConcurrentHashMap<Integer, Mail> mailStorage, Config config) {
        this.socket = socket;
        this.mailStorage = mailStorage;
        this.closed = false;
        this.config = config;
    }

    public void run() {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Mailbox Thread started.");
            out.println("ok DMTP2.0");

            String inputLine, outputLine;
            DMTP mailBoxDMTP = new MailBoxDMTP(this.mailStorage, config);
            while((inputLine = in.readLine()) != null && !closed) {
                outputLine = mailBoxDMTP.processInput(inputLine.stripLeading());
                if(outputLine != null) {
                    out.println(outputLine);
                    System.out.println(outputLine);
                    if (outputLine.equals("ok bye")) {
                        quit();
                    }
                }
                if (inputLine.stripLeading().equals("send")) {
                    out.println(mailBoxDMTP.send());
                }
            }
            close();
        }  catch (IOException e) {
            // I don't know what to do against this exception...
            System.out.println("SocketException: Software caused connection abort: recv failed");
        }
    }

    public void quit() {
        this.closed = true;
    }

    public void close() throws IOException {
        this.closed = true;
        if(!this.socket.isClosed())
            this.socket.close();

    }

    private Socket socket;
    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private boolean closed;
    private Config config;
}
