package dslab.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Connection {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new PrintWriter(socket.getOutputStream()));
    }

    public String readLine() throws IOException{
        return this.in.readLine();
    }

    public void send(String msg){
        this.out.println(msg);
        this.out.flush();
    }

    public void close() throws IOException{
        this.in.close();
        this.out.close();
        this.socket.close();
    }

    public void closeStreams() throws IOException{
        this.in.close();
        this.out.close();
    }




    /**
     * Sends the mail to all recipients over the network in the way that is specified by the DMTP-protocol
     *
     * @param mail that is to be sent over the network via DMTP
     * @IOException if something went wrong the IO
     */
    public void send(Mail mail) {
        for (int i = 0; i < mail.getRecipients().size(); i++) {
            //sendDMTP(mail);
        }
    }

    /* Helper-method for the send-method
    private void sendDMTP(Mail mail) {
        send("begin");
        send(mail.getFormattedSender());
        send(mail.getFormattedRecipients());
        send(mail.getFormattedSubject());
        send(mail.getFormattedBody());
        send("send");
        send("quit");
    } */
}
