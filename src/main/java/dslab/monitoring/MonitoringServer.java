package dslab.monitoring;

import java.io.*;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("");
    }

    @Override
    public void run() {
        shell.out().println("Starting monitoring server.. ");
        int port = config.getInt("udp.port");

        try {
            datagramSocket = new DatagramSocket(port);
            shell.out().println("Monitoring server is up and listening to port " + port);
            monitoringServerThread = new MonitoringServerThread(datagramSocket);
            monitoringServerThread.start();
            shell.run();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Command
    @Override
    public void addresses() {
        Map<String, Integer> addresses = monitoringServerThread.getAddresses();
        if (addresses.isEmpty())
            shell.out().println("There are no addresses.");
        for (Map.Entry<String, Integer> entry : addresses.entrySet()) {
            shell.out().println(entry.getKey() + " " + entry.getValue());
        }
    }

    @Command
    @Override
    public void servers() {
        Map<String, Integer> servers = monitoringServerThread.getServers();
        if (servers.isEmpty())
            shell.out().println("No data for servers.");
        for (Map.Entry<String, Integer> entry : servers.entrySet()) {
            shell.out().println(entry.getKey() + " " + entry.getValue());
        }
    }

    @Command
    @Override
    public void shutdown() {
        shell.out().println("Shutdown monitoring server...");
        if (datagramSocket != null) {
            datagramSocket.close();
        }
        if (monitoringServerThread != null && monitoringServerThread.isAlive())
            monitoringServerThread.shutdown();
        shell.out().println("Monitoring server stopped.");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

    private Config config;
    private DatagramSocket datagramSocket;
    private MonitoringServerThread monitoringServerThread;
    private Shell shell;

}
