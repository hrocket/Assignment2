package dslab.util;

import java.util.Arrays;
import java.util.List;

public class Mail {

    public Mail (List<String> recipients, String sender, String subject, String data, List<String> domains) {
        this.recipients = recipients;
        this.sender = sender;
        this.subject = subject;
        this.data = data;
        this.domains = domains;
    }

    public Mail (List<String> recipients, String sender, String subject, String data) {
        this.recipients = recipients;
        this.sender = sender;
        this.subject = subject;
        this.data = data;
    }

    public Mail () {}


    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public void setRecipients(String[] recipients) { this.recipients = Arrays.asList(recipients); }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public List<String> getDomains() { return domains; }

    public void setDomains(List<String> domains) { this.domains = domains; }

    public String getHash() { return hash; }

    public void setHash(String hash) { this.hash = hash; }

    public String getRecipientsAsString() {
        StringBuilder recipients = new StringBuilder();
        for (String recipient: this.recipients) {
            recipients.append(recipient).append(",");
        }
        if(recipients.length() > 0)
            return recipients.substring(0, recipients.length() - 1);
        return null;
    }

    @Override
    public String toString() {
        return "Mail{" +
                "recipients=" + getRecipientsAsString() +
                ", sender='" + sender + '\'' +
                ", subject='" + subject + '\'' +
                ", data='" + data + '\'' +
                ", domains=" + domains +
                ", hash=" + hash +
                '}';
    }

    public String toHashFormat() {
        return sender + "\n"
                + recipients.toString() + "\n"
                + subject + "\n"
                + data;
    }

    public String validateMail() {
        String errorMessage = "";
        if(sender == null || sender.trim().isEmpty())
            errorMessage += "error: Please enter a sender!";
        else if(recipients == null || recipients.size() < 1)
            errorMessage +=  "error: Please enter valid recipients!";
        else if(subject == null || subject.trim().isEmpty())
            errorMessage +=  "error: Please enter a subject!";
        else if(data == null)
            errorMessage +=  "warning: Your Mail contains no data!";

        return errorMessage;
    }

    private List<String> recipients;
    private String sender;
    private String subject;
    private String data;
    private List<String> domains;
    private String hash;
}
