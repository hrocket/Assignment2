package dslab.transfer;


import at.ac.tuwien.dsg.orvell.StopShellException;

import dslab.nameserver.INameserverRemote;
import dslab.util.Config;
import dslab.util.DMTP;


import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferServerConnection extends Thread {

    public TransferServerConnection(Socket socket, Config config, String componentId, INameserverRemote root) {
        this.socket = socket;
        this.config = config;
        this.closed = false;
        this.threatPool = Executors.newFixedThreadPool(10);
        this.componentId = componentId;
        this.root = root;
    }

    public void run() {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("ok DMTP2.0");

            String inputLine, outputLine;
            DMTP transferDMTP = new TransferDMTP();
            while ((inputLine = in.readLine()) != null && !closed) {
                outputLine = transferDMTP.processInput(inputLine.stripLeading());
                if (outputLine != null) {
                    out.println(outputLine);
                    if (outputLine.equals("ok bye"))
                        quit();
                }
                if (inputLine.stripLeading().equals("send")) {
                    out.println(send(transferDMTP));
                }
            }
            stopConnection();
        } catch (IOException e) {
            // I don't know what to do against this exception...
            System.out.println("SocketException: Software caused connection abort: recv failed");
        }
    }

    public String send(DMTP transferDMTP) {
        String message = transferDMTP.getMail().validateMail();
        if(message.equals("")) {
            Runnable runnable = new TransferClientThread(transferDMTP.getMail(), this.config, root);
            threatPool.execute(runnable);
            return "ok";
        } else return message;
    }

    public void quit() {
        closed = true;
    }

    private void stopConnection() {
        if (closed) {
            System.out.println("Shutting down the connection to" + componentId);
            if (!threatPool.isShutdown())
                threatPool.shutdown();
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
                throw new StopShellException();
            } catch (SocketException e) {
                //A socket exception will be thrown if the connection is shut down
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
    }


    private Socket socket;
    private Config config;
    private boolean closed;
    private ExecutorService threatPool;
    private String componentId;
    private INameserverRemote root;
}
