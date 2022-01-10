package dslab.mailbox;

import dslab.util.Config;
import dslab.util.DMTP;
import dslab.util.Mail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class MailBoxDMTP extends DMTP {
    public MailBoxDMTP(ConcurrentHashMap<Integer, Mail> mailStorage, Config config) {
        this.mailStorage = mailStorage;
        this.config = config;
    }

    @Override
    public String to(String input) {
        Mail mail = super.getMail();
        String userConfig = config.getString("users.config");
        Config user = new Config(userConfig);

        if (mail == null)
            return "error, a Message has to start with 'begin'!";

        String formattedInput = input.trim();
        if (formattedInput.isEmpty()) {
            return "error, one or more recipients must be specified.";
        }

        ArrayList<String> unknownRecipients = new ArrayList<>();
        ArrayList<String> recipients = new ArrayList<>(Arrays.asList(formattedInput.split(",")));
        //find unknown recipients
        for (String recipient : recipients) {
            String name = recipient.substring(0, recipient.indexOf("@"));
            if (!user.containsKey(name)) {
                unknownRecipients.add(recipient);
            }
        }
        //handle unknown recipients
        if (unknownRecipients.size() > 0) {
            StringBuilder str = new StringBuilder();
            recipients.removeAll(unknownRecipients);
            for (String unknown : unknownRecipients) {
                str.append(unknown).append(",");
            }
            return "error " + str;
        }

        mail.setRecipients(recipients);
        return "ok " + recipients.size();
    }

    public String send() {
        saveMail();
        return "ok";
    }

    private synchronized void saveMail() {
        Integer mailId = 1;
        while (mailStorage.putIfAbsent(mailId, super.getMail()) != null)
            mailId++;
    }

    private ConcurrentHashMap<Integer, Mail> mailStorage;
    private Config config;
}
