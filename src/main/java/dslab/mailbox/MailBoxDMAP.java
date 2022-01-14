package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MailBoxDMAP {

    public MailBoxDMAP(Config config, ConcurrentHashMap<Integer, Mail> mailStorage, SecureConnection secureConnection) {
        this.config = config;
        this.isLoggedIn = false;
        this.user = "";
        this.domain = config.getString("domain");
        this.mailStorage = mailStorage;
        this.secureConnection = secureConnection;

    }

    public List<String> processInput(String input) {
        input = secureConnection.decrypt(input);
        String trimInput = input.substring(input.indexOf(" ") + 1);
        List<String> response;
        List<String> responseEncrypted = new ArrayList<>();
        if (input.startsWith("login"))
            response = login(trimInput);
        else if (input.startsWith("list"))
            response = list();
        else if (input.startsWith("show"))
            response = show(trimInput);
        else if(input.startsWith("delete"))
            response = delete(trimInput);
        else if (input.startsWith("logout"))
            response = logout();
        else if (input.startsWith("quit"))
            response = quit();
        else response = null;
        if(response != null)
            response.forEach(responseLine -> responseEncrypted.add(secureConnection.encrypt(responseLine)));
        return responseEncrypted;
    }

    private List<String> login(String input) {
        List<String> response = new ArrayList<>();
        if (isLoggedIn) {
            response.add("you are already logged in");
            return response;
        }
        String userConfig = config.getString("users.config");
        Config users = new Config(userConfig);
        String[] credentials = input.split(" ");
        if(credentials.length == 2) {
            String username = credentials[0];
            String password = credentials[1];

            if (users.containsKey(username)) {
                if (users.getString(username).equals(password)) {
                    isLoggedIn = true;
                    user = username;
                    response.add("ok");
                } else response.add("error: Wrong password!");
            } else response.add("error: This username does not exist!");
        } else response.add("error: invalid input");
        return response;
    }

    private List<String> startSecure() {
        List<String> response = new ArrayList<>();
        response.add("ok");
        return response;
    }

    private List<String> list() {
        List<String> response = new ArrayList<>();
        if (!isLoggedIn) {
            response.add("You have to login first.");
            return response;
        }

        StringBuilder mails = new StringBuilder();
        //clear stringbuilder
        mails.setLength(0);
        for (Map.Entry<Integer, Mail> entry : mailStorage.entrySet()) {
            Mail mail = entry.getValue();
            if (mail.getRecipients().contains(user + "@" + domain)) {
                mails.append(entry.getKey()).append(" ").append(mail.getSender()).append(" ").append(mail.getSubject()).append(mail.getData()).append("\n");
                response.add(mails.toString());
            }
        }
        if (mails.toString().isEmpty())
            response.add("error... There are no messages available!");
        return response;
    }

    private List<String> show(String input) {
        List<String> response = new ArrayList<>();
        if (!isLoggedIn) {
            response.add("You have to login first.");
            return response;
        }

        int id = 0;
        if (input.matches("[0-9]+"))
            id = Integer.parseInt(input);
        else response.add("error: Please only use integer as id!");
        if(!mailStorage.containsKey(id)) {
            response.add("There are no mails with the given id.");
            return response;
        }
        Mail mail = mailStorage.get(id);
        if (mail.getRecipients().contains(user + "@" + domain)){
            response.add("from " + mail.getSender());
            response.add("to " + mail.getRecipientsAsString());
            response.add("subject " + mail.getSubject());
            response.add("data " + mail.getData());
            response.add("hash " + mail.getHash());
            response.add("ok");
        } else response.add("There are no mails concerning you with the given id.");
        return response;
    }

    private List<String> delete(String input) {
        List<String> response = new ArrayList<>();
        if (!isLoggedIn) {
            response.add("You have to login first.");
            return response;
        }

        int id = 0;
        if (input.matches("[0-9]+"))
            id = Integer.parseInt(input);
        else response.add("error: Please only use integer as id!");

        if(mailStorage.containsKey(id)) {
            Mail mail = mailStorage.get(id);
            if (mail.getRecipients().contains(user + "@" + domain)) {
                mail.getRecipients().remove(user + "@" + domain);
                if(mail.getRecipients().size() == 0 )
                    mailStorage.remove(id);
                response.add("ok");
            }
            else response.add("There are no mails concerning you with the given id.");
        } else response.add("There are no mails with the given id.");
        return response;
    }

    private List<String> logout() {
        List<String> response = new ArrayList<>();
        if (!isLoggedIn) {
            response.add("You have to login first.");
            return response;
        }

        this.user = "";
        this.isLoggedIn = false;
        response.add("ok");
        return response;
    }

    private List<String> quit() {
        List<String> response = new ArrayList<>();
        if (isLoggedIn)
            logout();

        response.add("ok bye");
        return response;
    }

    private Config config;
    private String user;
    private boolean isLoggedIn;
    private String domain;
    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private SecureConnection secureConnection;
}
