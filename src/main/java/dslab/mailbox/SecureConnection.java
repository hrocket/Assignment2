package dslab.mailbox;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SecureConnection {

    public SecureConnection(String componentId) {
        this.componentId = componentId;
        this.setUpSecureConnection = false;
        this.isEncryptedConnection = false;
        this.currentStep = 0;
    }

    public void setUpSecureConnection() {
        this.setUpSecureConnection = true;
    }

    public boolean setUpStatus() {
        return setUpSecureConnection;
    }

    public List<String> processInput(String input) throws SecurityException {
        String trimInput = input.substring(input.indexOf(" ") + 1);
        if (input.startsWith("startsecure"))
            return startSecure();
        else if (currentStep == 1)
            return challenge(input);
        else if (currentStep == 2)
            return finalizeSetUp(decrypt(input));
        else return null;
    }

    private List<String> startSecure() {
        List<String> response = new ArrayList<>();
        response.add("ok " + componentId);
        currentStep = 1;
        return response;
    }

    private List<String> challenge(String input) throws SecurityException {
        List<String> response = new ArrayList<>();

        String decryptedMessage = "";
        String responseString = "";
        try {
            byte[] serverPrivateKey = Files.readAllBytes(Paths.get("keys/server/" + componentId + ".der"));
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(serverPrivateKey));
            decryptedMessage = decryptRSA(input, key);
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new SecurityException("Error during RSA decryption.");
        }

        String[] splitInput = decryptedMessage.split(" ");
        if (splitInput[0].startsWith("ok") && splitInput.length == 4) {
            responseString = splitInput[0] + " " + splitInput[1];
            this.vector = Base64.getDecoder().decode(splitInput[3]);
            byte[] clientSecret = Base64.getDecoder().decode(splitInput[2]);
            secretKey = new SecretKeySpec(clientSecret, 0, clientSecret.length, "AES");
        } else {
            throw new SecurityException("Error during reading shared secret key.");
        }
        this.isEncryptedConnection = true;
        response.add(encrypt(responseString));
        currentStep = 2;
        return response;
    }

    private List<String> finalizeSetUp(String input) {
        List<String> response = new ArrayList<>();
        if (input.equals("ok")) {
            this.setUpSecureConnection = false;
        }
        return response;
    }

    private String decryptRSA(String input, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(input)));
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String encrypt(String input) {
        if(isEncryptedConnection) {
            try {
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(vector));
                return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes("UTF-8")));
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException |
                    UnsupportedEncodingException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
                e.printStackTrace();
            }
        } else {
            return input;
        }
        return null;
    }

    public String decrypt(String input) {
        if(isEncryptedConnection) {
            try {
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(vector));
                return new String(cipher.doFinal(Base64.getDecoder().decode(input)));
            } catch (Exception e) {
                System.out.println("Error while decrypting: " + e.toString());
            }
        } else {
            return input;
        }
        return null;
    }

    private String componentId;
    private boolean setUpSecureConnection;
    private boolean isEncryptedConnection;
    private int currentStep;
    private byte[] vector;
    private Key secretKey;
}
