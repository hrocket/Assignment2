package dslab.util;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class DMTP {

    public DMTP() {}

    public String processInput(String input) {
        System.out.println(input);
        String trimInput = input.substring(input.indexOf(" ") + 1);
        if(input.equals("begin"))
            return begin();
        else if (input.startsWith("to"))
            return to(trimInput);
        else if (input.startsWith("from"))
            return from(trimInput);
        else if(input.startsWith("subject"))
            return subject(trimInput);
        else if(input.startsWith("data"))
            return data(trimInput);
        else if(input.startsWith("quit"))
            return "ok bye";
        else return null;
    }

    public String begin() {
        mail = new Mail();
        return "ok";
    }

    public abstract String to(String input);

    public String from(String input) {
        if (mail == null)
            return "error, a Message has to start with 'begin'!";

        String formattedInput = input.trim();
        if (formattedInput.isEmpty())
            return "error, a sender must be specified!";

        String[] fromDomain = formattedInput.split("@");
        if (fromDomain.length > 2)
            return "error,only one sender can be specified";
        else {
            this.mail.setSender(formattedInput);
            return "ok";
        }
    }

    public String subject(String input) {
        if (mail == null)
            return "error, a Message has to start with 'begin'!";

        String formattedInput = input.trim();
        if (formattedInput.isEmpty())
            return "error, a subject must be specified!";

        this.mail.setSubject(formattedInput);
        return "ok";
    }

    public String data(String input) {
        if (mail == null)
            return "error, a Message has to start with 'begin'!";

        this.mail.setData(input);
        return "ok";
    }

    public abstract String send();

    public Mail getMail() {
        return mail;
    }

    Mail mail;
}
