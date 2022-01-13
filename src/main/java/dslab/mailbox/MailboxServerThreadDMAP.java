package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MailboxServerThreadDMAP extends Thread {

    public MailboxServerThreadDMAP(ServerSocket serverSocket, Config config, ConcurrentHashMap<Integer, Mail> mailStorage, String componentId) {
        this.serverSocket = serverSocket;
        this.config = config;
        this.mailStorage = mailStorage;
        this.connections = new ArrayList<>();
        this.componentId = componentId;
    }

    @Override
    public void run() {
        while(!shutdown) {
            try {
                MailBoxServerConnectionDMAP mailBoxServerConnectionDMAP = new MailBoxServerConnectionDMAP(serverSocket.accept(), config, mailStorage, componentId);
                mailBoxServerConnectionDMAP.start();
                connections.add(mailBoxServerConnectionDMAP);
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
        for (MailBoxServerConnectionDMAP thread : connections) {
            if (thread.isAlive())
                thread.close();
        }
    }

    private ServerSocket serverSocket;
    private Config config;
    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private boolean shutdown;
    private List<MailBoxServerConnectionDMAP> connections;
    private String componentId;
}
