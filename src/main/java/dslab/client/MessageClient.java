package dslab.client;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.Socket;
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

    // Predefined constants
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
    }

    @Override
    public void run() {
        // Get shared secret key for message integrity
        File sharedKeyFile = new File(SKPATH);
        try {
            sharedKey = Keys.readSecretKey(sharedKeyFile);
        } catch (IOException e) {
            shell.out().println("Error while accessing KeyFile");
        }

        // Setup mailbox-connection
        String mailboxHost = config.getString("mailbox.host");
        String mailboxUser = config.getString("mailbox.user");
        String mailboxPassword = config.getString("mailbox.password");
        int mailboxPort = config.getInt("mailbox.port");

        try {
            this.mailboxConnection = new Connection(new Socket(mailboxHost, mailboxPort));

            String response = this.mailboxConnection.readLine();
            if (!response.equals("ok DMAP2.0")) {
                notifyShell("Mailboxserver does not recognize DMAP2.0", true);
            }

            try {
                // Create challenge & vector from random binaries
                byte[] challenge = SecureRandom.getSeed(32);
                this.vector = SecureRandom.getSeed(16);

                // Encode challenge & vector
                byte[] challenge_encoded = Base64.getEncoder().encode(challenge);
                byte[] vector_encoded = Base64.getEncoder().encode(this.vector);

                // Convert to String
                String challengeString = new String(challenge_encoded, StandardCharsets.UTF_8);
                String vectorString = new String(vector_encoded, StandardCharsets.UTF_8);

                // Create secret-key
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                SecureRandom secRandom = new SecureRandom(this.vector); // TODO: IS THIS EVEN USED ??
                this.secretKey = generator.generateKey();

                // Convert encoded secret-key to string
                byte[] secretKey_encoded = Base64.getEncoder().encode(secretKey.getEncoded());
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
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBinaries));

                // Create the client-challenge
                String msg = "ok " + challengeString + " " + keyString + " " + vectorString;
                // Encrypt the message
                String rsaMsg_encrypted = encrypt(msg, publicKey);

                System.out.println(msg);
                System.out.println(rsaMsg_encrypted);

                this.mailboxConnection.send(rsaMsg_encrypted);

                // Receive & decrypt the challenge
                String serverChallenge_encrypted = this.mailboxConnection.readLine();
                String serverChallenge_decrypted = decrypt(serverChallenge_encrypted);
                serverChallenge_decrypted = serverChallenge_decrypted.split(" ")[1];

                // If received server challenge does not equal the original challenge string we abort
                if (!serverChallenge_decrypted.equals(challengeString)) {
                    this.mailboxConnection.send("ERRORTest");
                    notifyShell("Identity of the Server could not be proven - Be careful !", true);
                } else {
                    send("ok");
                }

            } catch (Exception e) {
                e.printStackTrace();
                notifyShell("ERROR123", true);
            }

            // Login with the credentials from the config-file
            send("login " + mailboxUser + " " + mailboxPassword);
            response = receive();


            if (!response.equals("ok")) {
                notifyShell("ERROR: Login failed", true);
            }


        } catch (Exception e) {
            e.printStackTrace();
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
            System.out.println("ERRORHere");
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
            System.out.println("ERRORThere");
        }
        return null;
    }

    /**
     *
     *
     * @param msg
     * @return
     */
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
            e.printStackTrace();
            System.out.println("ERROR234");
        }
        return null;
    }

    /**
     * Encrypts the message and sends it to the mailbox-server
     *
     * @param msg to be send to the mailbox-server
     */
    public void send(String msg) {
        // encrypt before sending
        String msg_encrypted = encrypt(msg);
        this.mailboxConnection.send(msg_encrypted);
    }

    /**
     * Reads an encrypted message from the connection to the mailbox-server, decrypts and returns it
     *
     * @return the decrypted message
     */
    public String receive() {
        try {
            String msg_encrypted = this.mailboxConnection.readLine();
            // decrypt after receiving
            return decrypt(msg_encrypted);
        } catch (Exception e) {
            notifyShell("Receiving the message failed", true);
            return null;
        }
    }

    @Command
    @Override
    public void inbox() {
        try {

            // Send list-command
            send("list");
            String response = "";
            ArrayList<Integer> ids = new ArrayList<>();
            while (true) {
                response = receive();
                // Covers the case that the response is either empty or "ok", in this case we are finished
                if (response.startsWith("error"))
                    break;
                // Store all msg-id's to call show command on them later on
                ids.add(Integer.parseInt(response.split(" ")[0]));
                break;
            }

            // No id's means no mails in the mailbox of the user, so we can abort
            if (ids.size() == 0) {
                notifyShell("There are no mails in your mailbox", false);
                return;
            }

            // Send show-command
            for (int id : ids) {
                send("show " + id);
                while (true) {
                    response = receive();
                    // No more mails available
                    if (response.equals("ok"))
                        break;
                    notifyShell(response, false);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            shutdown();
        }
    }

    @Command
    @Override
    public void delete(String id) {
        send("delete " + id);
        String response = receive();
        notifyShell(response, false);
    }

    @Command
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
            // Hash-Magic
            Mac hashMac = Mac.getInstance(MAC);
            hashMac.init(this.secretKey);
            hashMac.update(mail.toHashFormat().getBytes());
            byte[] computedHash = hashMac.doFinal();
            byte[] receivedHash = Base64.getDecoder().decode(mail.getHash().getBytes());
            // Compare the Hashes
            boolean valid = MessageDigest.isEqual(computedHash, receivedHash);
            notifyShell(valid ? "ok" : "error", false);
        } catch (NoSuchAlgorithmException e) {
            notifyShell("Selected algorithm could not be found", false);
        } catch (InvalidKeyException e) {
            notifyShell("No valid key was provided for HMAC", false);
        }
    }

    @Command
    @Override
    public void msg(String to, String subject, String data) {
        // Set up
        String from = config.getString("transfer.email");
        String transferHost = config.getString("transfer.host");
        int transferPort = config.getInt("transfer.port");
        try {

            // Set up
            this.transferConnection = new Connection(new Socket(transferHost, transferPort));
            Mail mail = calculateHash(from, to, subject, data);
            DMTPHandler dmtpHandler = new DMTPHandler();
            // Store commands in an array, so we can iterate properly
            String[] fields = {"begin", "from " + from, "to " + to, "subject " + subject,
                    "data " + data, "hash " + mail.getHash(), "send"};
            int counter = 0;
            String response;

            // Send commands one by one, we stop as soon as an error occurs
            do {
                this.transferConnection.send(fields[counter++]);
                response = this.transferConnection.readLine();
            } while (!dmtpHandler.processRequest(response).equals("error protocol error"));

            // Counter increases for each field, if he is not equal to the number of fields, it means that an error has occured
            if (counter < (fields.length - 1)) {
                notifyShell("Protocol-Error occured while sending the mail", false);
                return;
            }

            // Closing the whole connection, not just the streams
            this.transferConnection.close();

        } catch (Exception e) {
            // Do
        }
    }

    /**
     *
     * @param from sender of the mail
     * @param to recipient of the mail
     * @param subject subject of the mail
     * @param data main-body of the mail
     *
     * @return A Mail object with all the parameter from this function-call + its hash value
     *
     * @throws NoSuchAlgorithmException if the Algorithm we are looking for could not be found (in this case: HmacSHA256)
     * @throws InvalidKeyException if the secret key is not valid
     */
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

    @Command
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

    /**
     * Sends the msg over the outputstream of the shell and calls shutdown() depending on the shutdown parameter.
     *
     * @param msg to be send over the outputstream of the shell
     * @param shutdown flag to determine weather the system should perform a shutdown
     */
    private void notifyShell(String msg, boolean shutdown){
        shell.out().println(msg);
        shell.out().flush();
        if(shutdown){
            shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
