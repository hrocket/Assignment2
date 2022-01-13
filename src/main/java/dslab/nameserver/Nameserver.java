package dslab.nameserver;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Nameserver implements INameserver {

    private String componentId;
    private Config config;
    private PrintStream out;
    private Shell shell;
    private Registry registry;
    private INameserverRemote nameserverRemote;
    private INameserverRemote root;


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.out = out;
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt(componentId + ": ");
        this.nameserverRemote = new NameserverRemote();
    }

    @Override
    public void run() {
        try {
            //Only the config of the root ns does not include the key "domain"
            if (!config.containsKey("domain")) {
                this.registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
                INameserverRemote remote = (INameserverRemote) UnicastRemoteObject.exportObject(this.nameserverRemote, 0);
                //bind the stub of the remote Object to the binding name of the config
                registry.bind(config.getString("root_id"), remote);
            } else {
                registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
                root = (INameserverRemote) registry.lookup(config.getString("root_id"));
                root.registerNameserver(config.getString("domain"), nameserverRemote);
            }
        } catch (RemoteException | AlreadyBoundException | NotBoundException e) {
            e.printStackTrace();
        } catch (AlreadyRegisteredException e) {
            e.printStackTrace();
        } catch (InvalidDomainException e) {
            e.printStackTrace();
        }
        System.out.println("Server " + componentId + " is up!");
        shell.run();
    }

    @Command
    @Override
    public void nameservers() {
        if(!config.containsKey("domain")) {
            out.print(((INameserverGetter) nameserverRemote).getNameservers());
        } else {
            try {
                out.print(((INameserverGetter) root.getNameserver(config.getString("domain"))).getNameservers());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Command
    @Override
    public void addresses() {
        if (!config.containsKey("domain")) {
            out.print(((INameserverGetter) nameserverRemote).getAddresses());
        } else {
            try {
                out.print(((INameserverGetter) root.getNameserver(config.getString("domain"))).getAddresses());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Command
    @Override
    public void shutdown() {
        if(!config.containsKey("domain")) {
            try {
                UnicastRemoteObject.unexportObject(this.nameserverRemote, true);
            } catch (NoSuchObjectException e) {
                e.printStackTrace();
            }
            try {
                registry.unbind(config.getString("root_id"));
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (NotBoundException e) {
                e.printStackTrace();
            } catch (NoSuchObjectException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        //close shell
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
