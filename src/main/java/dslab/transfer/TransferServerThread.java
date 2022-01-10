package dslab.transfer;

import dslab.util.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferServerThread extends Thread {

    public TransferServerThread(ServerSocket serverSocket, Config config, String componentId) {
        this.serverSocket = serverSocket;
        this.config = config;
        this.threatPool = Executors.newFixedThreadPool(3);
        this.componentId = componentId;
        this.shutdown = false;
        this.connections = new ArrayList<>();
    }

    @Override
    public void run() {
        while (!shutdown) {
            try {
                //Creates new Thread everytime a socket gets accepted
                TransferServerConnection transferServerConnection = new TransferServerConnection(serverSocket.accept(), config, componentId);
                threatPool.execute(transferServerConnection);
                connections.add(transferServerConnection);
            } catch (SocketException e) {
                break;
                // server socket closed
            } catch (IOException e) {
                System.out.println(e.getMessage());
                break;
            }
        }
    }

    public void shutdown() {
        this.shutdown = true;
        for (TransferServerConnection thread : connections) {
            if (thread.isAlive())
                thread.quit();
        }
        if (!threatPool.isShutdown())
            threatPool.shutdown();
    }

    private ServerSocket serverSocket;
    private Config config;
    private ExecutorService threatPool;
    private String componentId;
    private volatile boolean shutdown;
    private List<TransferServerConnection> connections;

}
