package dslab.nameserver;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NameserverRemote implements INameserverRemote, INameserverGetter {

    private ConcurrentHashMap<String, String> domains;
    private ConcurrentHashMap<String, INameserverRemote> children;

    public NameserverRemote() {
        domains = new ConcurrentHashMap<>();
        children = new ConcurrentHashMap<>();
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        if (domain == null || domain.isEmpty()) {
            throw new InvalidDomainException("The domain can not be null or empty!");
        }
        String[] domainParts = domain.split("\\.");
        //If the length is equal to 1, then it is a child of this server.
        if (domainParts.length == 1) {
            if (children.containsKey(domainParts[0])) {
                throw new AlreadyRegisteredException("The nameserver is already registered.");
            } else {
                children.put(domainParts[0], nameserver);
            }
        } else {
            //Length is greater than 1 -> must be forwarded to corresponding child
            String child = domainParts[domainParts.length - 1];
            if (children.containsKey(child)) {
                INameserverRemote childServer = children.get(child);
                String remainingDomain = domain.substring(0, domain.length() - child.length() - 1);
                childServer.registerNameserver(remainingDomain, nameserver);
            } else {
                throw new InvalidDomainException("There is no nameserver with the domain: " + child);
            }
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        if (domain == null || domain.isEmpty()) {
            throw new InvalidDomainException("The domain can not be null or empty!");
        }
        String[] domainParts = domain.split("\\.");
        if (domainParts.length == 1) {
            if (domains.containsKey(domainParts[0])) {
                throw new AlreadyRegisteredException("The mailbox server is already registered.");
            } else {
                domains.put(domainParts[0], address);
            }
        } else {
            //Length is greater than 1 -> must be forwarded to corresponding child
            String child = domainParts[domainParts.length - 1];
            if (children.containsKey(child)) {
                INameserverRemote childServer = children.get(child);
                String remainingDomain = domain.substring(0, domain.length() - child.length() - 1);
                childServer.registerMailboxServer(remainingDomain, address);
            } else {
                throw new InvalidDomainException("There is no nameserver with the domain: " + child);
            }
        }
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        return children.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        return domains.get(username);
    }

    public String getNameservers() {
        Enumeration<String> zones = children.keys();
        StringBuilder stringBuilder = new StringBuilder();
        Collections.list(zones).forEach(zone -> stringBuilder.append(zone).append("\n"));
        return stringBuilder.toString();
    }

    @Override
    public String getAddresses() {
        Set<Map.Entry<String, String>> addresses = domains.entrySet();
        StringBuilder stringBuilder = new StringBuilder();
        addresses.forEach(address -> stringBuilder.append(address.getKey()).append(" ").append(address.getValue()).append("\n"));
        return stringBuilder.toString();
    }
}
