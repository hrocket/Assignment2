package dslab.client;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

public class MessageClient implements IMessageClient, Runnable {

    private String componentId;
    private Config config;
    private Shell shell;
    private Key sharedKey;
    private Connection mailboxConnection;
    private Connection transferConnection;

    private final String RSA = "RSA/ECB/PKCS1Padding";
    private final String AES = "AES/CTR/NoPadding";
    private final String CHARSET = "UTF8";
    private final String SKPATH = "keys/hmac.key";
    private final String MAC = "HmacSHA256";

    private Key secretKey;
    private byte[] vector;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");

    }

    @Override
    public void run() {
        // get shared secret key for message integrity
        File sharedKeyFile = new File(SKPATH);
        try {
            sharedKey = Keys.readSecretKey(sharedKeyFile);
        } catch (IOException e) {
            shell.out().println("error occured while opening shared key file");
        }

        // setup mailbox socket
        String mailboxHost = config.getString("mailbox.host");
        String mailboxUser = config.getString("mailbox.user");
        String mailboxPassword = config.getString("mailbox.password");
        int mailboxPort = config.getInt("mailbox.port");
        try {
            this.mailboxConnection = new Connection(new Socket(mailboxHost, mailboxPort));

            // ok DMAP2.0
            String response = this.mailboxConnection.readLine();
            if (!response.equals("ok DMAP2.0")) {
                shell.out().println("expected mailbox server to know DMAP2.0!");
                shell.out().flush();
                shutdown();
            }


            try {
                // Create challenge & vector from random binaries
                byte[] challenge = SecureRandom.getSeed(32);
                this.vector = SecureRandom.getSeed(16);

                // Encode challenge & vector
                byte[] challenge_encoded = Base64.getEncoder().encode(challenge);
                byte[] vector_encoded = Base64.getEncoder().encode(vector);

                // Convert to String
                String challengeString = new String(challenge_encoded, StandardCharsets.UTF_8);
                String vectorString = new String(vector_encoded, StandardCharsets.UTF_8);

                // Create secret-key
                KeyGenerator generator = KeyGenerator.getInstance(AES);
                generator.init(256);
                SecureRandom secRandom = new SecureRandom(vector);
                secretKey = generator.generateKey();

                // Encode secret-key
                byte[] secretKey_encoded = Base64.getEncoder().encode(secretKey.getEncoded());
                // Convert secret-key to string
                String keyString = new String(secretKey_encoded, StandardCharsets.UTF_8);

                // Start sending
                this.mailboxConnection.send("startsecure");

                String componentId = this.mailboxConnection.readLine();
                componentId = componentId.split(" ")[1];

                System.out.println("startsecure");
                System.out.println(componentId);

                // Locate the public-key-specification
                Path path = Paths.get("keys/client/" + componentId + "_pub.der");
                // Load the public-key-specification
                byte[] publicKeyBinaries = Files.readAllBytes(path);
                // Create public-key from specification
                PublicKey publicKey = KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(publicKeyBinaries));

                // Create the message
                String msg = "ok " + challengeString + " " + keyString + " " + vectorString;
                // Encrypt the message
                String rsaMsg_encrypted = encrypt(msg, publicKey);

                System.out.println(msg);
                System.out.println(rsaMsg_encrypted);

                this.mailboxConnection.send(rsaMsg_encrypted);

                //Check if challenge is received
                String serverChallenge_encrypted = this.mailboxConnection.readLine();
                // Decrypt
                String serverChallenge_decrypted = decrypt(serverChallenge_encrypted);
                serverChallenge_decrypted = serverChallenge_decrypted.split(" ")[1];

                if (!serverChallenge_decrypted.equals(challengeString)) {
                    this.mailboxConnection.send("ERROR");
                    shell.out().println("couldn't recognize Server based on challenge");
                    shell.out().flush();
                    shutdown();
                }

                send("OK");


            } catch (Exception e) {
                shell.out().println("error while setting up secure connection: " + e.getMessage());
                shell.out().flush();
                shutdown();
            }

            send("login " + mailboxUser + " " + mailboxPassword);
            response = receive();
            if (!response.equals("ok")) {
                shell.out().println("error while logging in");
                shell.out().flush();
                shutdown();
            }


        } catch (Exception e) {
            // bla bla
        }

        shell.run();

    }

    public String encrypt(String msg) {
        try {
            // Set up the cipher
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(vector));
            // Convert to Binary
            byte[] msg_binary_decrypted = msg.getBytes(CHARSET);
            // Encrypt
            byte[] msg_binary_encrypted = cipher.doFinal(msg_binary_decrypted);
            // Convert back to String-format
            return Base64.getEncoder().encodeToString(msg_binary_encrypted);
        } catch (Exception e) {
            System.out.println("ERROR");
        }
        return null;
    }

    public String encrypt(String msg, Key key) {
        try {
            // Set up the cipher
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            // Convert to Binary
            byte[] msg_binary_decrypted = msg.getBytes(CHARSET);
            // Encrypt
            byte[] msg_binary_encrypted = cipher.doFinal(msg_binary_decrypted);
            // Convert back to String-format
            return Base64.getEncoder().encodeToString(msg_binary_encrypted);
        } catch (Exception e) {
            System.out.println("ERROR");
        }
        return null;
    }

    public String decrypt(String msg) {
        try {
            // Set up the cipher
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(vector));
            // Convert to Binary
            byte[] msg_binary_encrypted = Base64.getDecoder().decode(msg);
            // Decrypt
            byte[] msg_binary_decrypted = cipher.doFinal(msg_binary_encrypted);
            // Convert back to String (Since its text-data we do not need Base64)
            return new String(msg_binary_decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("ERROR");
        }
        return null;
    }

    public void send(String msg) {
        // encrypt before sending
        String msg_encrypted = encrypt(msg);
        this.mailboxConnection.send(msg_encrypted);
    }

    public String receive() {
        try {
            String msg_encrypted = this.mailboxConnection.readLine();
            // decrypt after receiving
            return decrypt(msg_encrypted);
        } catch (Exception e) {
            shell.out().println("Exception while receiving message:" + e.getMessage());
            shell.out().flush();
            shutdown();
            return null;
        }
    }

    @Override
    public void inbox() {
        try {

            // List
            send("list");
            String response = "";
            ArrayList<Integer> ids = new ArrayList<>();
            while (true) {
                response = receive();
                if (response.length() <= 2)
                    break;
                ids.add(Integer.parseInt(response.split(" ")[0]));
            }

            // Show
            for (int id : ids) {
                send("show " + id);
                while (true) {
                    response = receive();
                    if (response.equals("ok"))
                        break;
                    shell.out().println(response);
                    shell.out().flush();
                }
            }

            if (ids.size() == 0) {
                shell.out().println("Your inbox is currently empty.");
                shell.out().flush();
            }


        } catch (Exception e) {
            System.out.println("SocketException while handling socket: " + e.getMessage());
            shutdown();
        }
    }

    @Override
    public void delete(String id) {

        send("delete " + id);
        String response = receive();

        shell.out().println(response);
        shell.out().flush();

    }

    @Override
    public void verify(String id) {
        try {
            // Set up
            DMTPHandler dmtpHandler = new DMTPHandler();
            send("show " + id);
            while (true) {
                String request = receive();
                String response = dmtpHandler.processRequest(request);
                if (response.equals("ok")) break;
                if (response.equals("error protocol error")) return;
            }
            Mail mail = dmtpHandler.getMail();
            Mac hashMac = Mac.getInstance(MAC);
            hashMac.init(this.secretKey);
            hashMac.update(mail.toHashFormat().getBytes());
            byte[] computedHash = hashMac.doFinal();
            byte[] receivedHash = Base64.getDecoder().decode(mail.getHash().getBytes());
            boolean valid = MessageDigest.isEqual(computedHash, receivedHash);
            shell.out().println(valid ? "ok" : "error");
            shell.out().flush();
        } catch (Exception e) {
            // do
        }
    }

    @Override
    public void msg(String to, String subject, String data) {
        String from = config.getString("transfer.email");
        String transferHost = config.getString("transfer.host");
        int transferPort = config.getInt("transfer.port");
        try {

            this.transferConnection = new Connection(new Socket(transferHost, transferPort));
            Mail mail = calculateHash(from, to, subject, data);

            DMTPHandler dmtpHandler = new DMTPHandler();
            String[] fields = {"begin", "from " + from, "to " + to, "subject " + subject,
                    "data " + data, "hash " + mail.getHash(), "send"};

            int counter = 0;
            String response;

            do {
                this.transferConnection.send(fields[counter++]);
                response = this.transferConnection.readLine();
            } while (!dmtpHandler.processRequest(response).equals("error protocol error"));

            // Counter increases for each field, if he is not equal to the number of fields, it means that an error has occured
            if (counter < (fields.length - 1)) {
                shell.out().println("DMTP2.0 Error while sending Mail!");
                shell.out().flush();
                return;
            }

            // Closing the whole connection, not just the streams
            this.transferConnection.close();

        } catch (Exception e) {
            // Do
        }
    }

    private Mail calculateHash(String from, String to, String subject, String data) throws NoSuchAlgorithmException, InvalidKeyException {
        // calculate hash:
        String hash = "";
        Mac hashMac = Mac.getInstance("HmacSHA256");
        hashMac.init(this.secretKey);
        String[] recipients = to.split(",");
        Mail mail = new Mail(Arrays.asList(recipients), from, subject, data);
        hashMac.update(mail.toHashFormat().getBytes());
        byte[] computedHash = hashMac.doFinal();
        hash = Base64.getEncoder().encodeToString(computedHash);
        mail.setHash(hash);
        return mail;
    }

    @Override
    public void shutdown() {
        try {
            if (this.mailboxConnection != null) {
                this.mailboxConnection.send("logout");
                this.mailboxConnection.close();
            }
            if (this.transferConnection != null) {
                this.transferConnection.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        shell.out().println("Exiting the shell, bye!");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
