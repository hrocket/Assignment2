package dslab.transfer;

import dslab.util.Config;
import dslab.util.Mail;
import dslab.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;

public class TransferClientThread extends Thread {

    public TransferClientThread(Mail mail, Config config) {
        this.mail = mail;
        this.config = config;
        this.domainConfig = new Config("domains");
        this.domains = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.monitoringConfig = new Config("monitoring");
        this.dmtp = new ArrayList<>();
    }

    public void run() {
        statistics();
        lookupDomain((ArrayList<String>) this.mail.getRecipients());
        initializeProtocol();
        sendMail();
        //if error handling failed, discard error messages
        if (!errors.isEmpty())
            handleError();
    }

    public void sendMail() {
        for (String domain : domains) {
            String[] destination = domain.split(":");
            String host = destination[0];
            int port = Integer.parseInt(destination[1]);
            try {
                Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                for (Pair entry : dmtp) {
                    String request = entry.getKey();
                    if (!entry.getValue().isEmpty())
                        request += " " + entry.getValue();
                    out.println(request);
                    String response;
                    boolean error = false;
                    while ((response = in.readLine()) != null) {
                        if (handleResponse(response) || response.equals("ok bye")) {
                            break;
                        } else
                            error = true;
                    }
                    if (error)
                        break;
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void lookupDomain(ArrayList<String> recipients) {
        ArrayList<String> toRemove = new ArrayList<>();
        this.domains.clear();
        for (String recipient : recipients) {
            if (recipient.contains("@")) {
                String domain = recipient.split("@")[1];
                try {
                    this.domains.add(domainConfig.getString(domain));
                } catch (MissingResourceException e) {
                    toRemove.add(recipient);
                }
            } else {
                toRemove.add(recipient);
            }
        }
        recipients.removeAll(toRemove);
        this.mail.setRecipients(recipients);
        this.mail.setDomains(this.domains);

        for (String recipient : toRemove) {
            errors.add("Could not find recipient with domain: " + recipient);
        }
    }

    private boolean handleResponse(String response) {
        if (response.startsWith("ok"))
            return true;
        if (response.startsWith("error")) {
            errors.add(response);
            return false;
        }
        return true;
    }

    private void handleError() {
        first = false;
        //Add sender to domains and set sender as recipient
        this.domains.clear();
        String domain = mail.getSender().split("@")[1];
        this.domains.add(domainConfig.getString(domain));
        ArrayList<String> recipient = new ArrayList<>();
        recipient.add(mail.getSender());
        this.mail.setRecipients(recipient);
        this.mail.setSender("mailer@" + config.getString("registry.host") + ":" + config.getInt("tcp.port"));
        this.mail.setSubject("An error occurred while sending the email");
        this.mail.setData(errors.toString());

        //initialize the protocol again
        initializeProtocol();

        //send error message
        sendMail();
    }

    private void initializeProtocol() {
        dmtp.add(new Pair("begin", ""));
        dmtp.add(new Pair("to", mail.getRecipientsAsString()));
        dmtp.add(new Pair("from", mail.getSender()));
        dmtp.add(new Pair("subject", mail.getSubject()));
        dmtp.add(new Pair("data", mail.getData()));
        dmtp.add(new Pair("send", ""));
        dmtp.add(new Pair("quit", ""));
    }

    private void statistics() {
        int udpPort = monitoringConfig.getInt("udp.port");
        String data = "";
        InetAddress hostAddress = null;
        try {
            hostAddress = InetAddress.getByName("127.0.0.1");
            int tcpPort = config.getInt("tcp.port");
            data = hostAddress.toString() + ":" + tcpPort + " " + mail.getSender();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        //convert data to byte to send via datagramSocket
        byte[] buffer = data.getBytes();
        //create a package with buffer
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, hostAddress, udpPort);
        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.send(datagramPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Mail mail;
    private Config config;
    private List<String> domains;
    private Config monitoringConfig;
    private List<Pair> dmtp;
    private List<String> errors;
    private boolean first;
    private Config domainConfig;
}
