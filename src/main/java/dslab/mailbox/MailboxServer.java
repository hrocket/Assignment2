package dslab.mailbox;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.util.Config;
import dslab.util.Mail;

public class MailboxServer implements IMailboxServer, Runnable {

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.componentId = componentId;
        this.mailStorage = new ConcurrentHashMap<>();
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("");
    }

    @Override
    public void run() {
        try {
            Registry registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
            INameserverRemote root = (INameserverRemote) registry.lookup(config.getString("root_id"));
            String domain = componentId.substring(8).replace('-', '.');
            InetAddress ip = InetAddress.getLocalHost();
            root.registerMailboxServer(domain, ip.getHostAddress() + ':' + config.getString("dmtp.tcp.port"));
        } catch (RemoteException | NotBoundException | InvalidDomainException | UnknownHostException e) {
            System.out.println(e.getMessage());
        } catch (AlreadyRegisteredException e) {
            System.out.println(e.getMessage());
        }

        int portDMTP = config.getInt("dmtp.tcp.port");
        int portDMAP = config.getInt("dmap.tcp.port");
        shell.out().println("Mailbox Server is running...");
        try {
            serverSocketDMTP = new ServerSocket(portDMTP);
            serverSocketDMAP = new ServerSocket(portDMAP);

            shell.out().println("For incoming messages use port " + portDMTP);
            shell.out().println("For user login use port " + portDMAP);

            mailboxServerThreadDMTP = new MailboxServerThreadDMTP(serverSocketDMTP, mailStorage, config);
            mailboxServerThreadDMTP.start();
            mailboxServerThreadDMAP = new MailboxServerThreadDMAP(serverSocketDMAP, config, mailStorage, componentId);
            mailboxServerThreadDMAP.start();
            shell.run();
        } catch (IOException e) {
            shell.out().println("An error occurred during the creation of the sockets");
            throw new UncheckedIOException(e);
        }
    }

    @Command
    @Override
    public void shutdown() {
        try {
            shell.out().println("Shutdown mailbox server..");
            if (serverSocketDMTP != null && !serverSocketDMTP.isClosed()) {
                serverSocketDMTP.close();
            }
            if (serverSocketDMAP != null && !serverSocketDMAP.isClosed())
                serverSocketDMAP.close();
            if (mailboxServerThreadDMTP != null && mailboxServerThreadDMTP.isAlive())
                mailboxServerThreadDMTP.shutdown();
            if (mailboxServerThreadDMAP != null && mailboxServerThreadDMAP.isAlive())
                mailboxServerThreadDMAP.shutdown();
            shell.out().println("Mailbox server stopped.");
        } catch (IOException e) {
            shell.out().println("All sockets closed.");
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }

    private Config config;
    private ServerSocket serverSocketDMTP;
    private ServerSocket serverSocketDMAP;
    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private MailboxServerThreadDMTP mailboxServerThreadDMTP;
    private MailboxServerThreadDMAP mailboxServerThreadDMAP;
    private Shell shell;
    private String componentId;
}
