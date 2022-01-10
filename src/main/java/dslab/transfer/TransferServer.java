package dslab.transfer;

import java.io.*;
import java.net.ServerSocket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.componentId = componentId;
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("");
    }

    @Override
    public void run() {
        int port = config.getInt("tcp.port");
        shell.out().println("Transfer Server is running...");
        try {
            serverSocket = new ServerSocket(port);
            shell.out().println("Listening to port: " + port);
            //Create new transferServer thread to listen to server socket
            transferServerThread = new TransferServerThread(serverSocket, config, componentId);
            transferServerThread.start();
            shell.run();
        } catch (IOException e) {
            e.printStackTrace();
            shell.out().println("Could not listen to port: " + port);
        }
    }


    @Command
    @Override
    public void shutdown() {
        try {
            shell.out().println("Shutdown transfer server..");
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
            if(transferServerThread != null && transferServerThread.isAlive())
                transferServerThread.shutdown();
            shell.out().println("Transfer server stopped");
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

    private Config config;
    private ServerSocket serverSocket;
    private String componentId;
    private Shell shell;
    private TransferServerThread transferServerThread;
}
