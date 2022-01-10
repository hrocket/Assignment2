package dslab.monitoring;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MonitoringServerThread extends Thread {

    public MonitoringServerThread(DatagramSocket datagramSocket) {
        this.datagramSocket = datagramSocket;
        this.closed = false;
    }

    @Override
    public void run() {
        try {
            while(!closed) {
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                datagramSocket.receive(packet); // wait for new package

                String data = new String(packet.getData()); // format of data <server address> <name@domain>

                //extract the data form the packet
                String[] extraction = data.split(" ");
                String serverAddress = extraction[0];
                String mailAddress = extraction[1];

                //format data
                if(serverAddress.startsWith("/"))
                    serverAddress = serverAddress.substring(1);
                mailAddress = mailAddress.substring(0, mailAddress.indexOf("\0"));

                int count = servers.getOrDefault(serverAddress, 0);
                servers.put(serverAddress, count + 1);
                count = addresses.getOrDefault(mailAddress, 0);
                addresses.put(mailAddress, count + 1);
            }
        } catch (SocketException e) {
           //Datagram Socket closed.
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    public Map<String, Integer> getAddresses() {
        return addresses;
    }

    public Map<String, Integer> getServers() {
        return servers;
    }

    public void shutdown() {
        this.closed = true;
        if(datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
        }
    }

    private DatagramSocket datagramSocket;
    private Map<String, Integer> addresses = new HashMap<>();
    private Map<String, Integer> servers = new HashMap<>();
    private boolean closed;
}
