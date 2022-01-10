package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MailboxServerThreadDMTP extends Thread {

    public MailboxServerThreadDMTP(ServerSocket socket, ConcurrentHashMap<Integer, Mail> mailStorage, Config config) {
        this.serverSocket = socket;
        this.mailStorage = mailStorage;
        this.shutdown = false;
        this.connections = new ArrayList<>();
        this.config = config;
    }

    public void run() {
        while (!shutdown) {
            try {
                MailboxServerConnectionDMTP mailboxServerConnectionDMTP = new MailboxServerConnectionDMTP(serverSocket.accept(), mailStorage, config);
                mailboxServerConnectionDMTP.start();
                connections.add(mailboxServerConnectionDMTP);
            } catch (SocketException e) {
                //shutdown server socket connection
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() throws IOException {
        this.shutdown = true;
        for (MailboxServerConnectionDMTP thread : connections) {
            if (thread.isAlive())
                thread.close();
        }
    }

    private ServerSocket serverSocket;
    private Config config;
    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private boolean shutdown;
    private List<MailboxServerConnectionDMTP> connections;
}
