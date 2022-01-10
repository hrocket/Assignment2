package dslab.transfer;

import dslab.util.DMTP;

import java.util.ArrayList;
import java.util.Arrays;

public class TransferDMTP extends DMTP {

    public TransferDMTP() { }

    @Override
    public String send() {
        return null;
    }

    @Override
    public String to(String input) {
        if (super.getMail() == null)
            return "A Message has to start with 'begin'!";

        if (input.isEmpty()) {
            return "One or more recipients must be specified.";
        }

        ArrayList<String> recipients = new ArrayList<>(Arrays.asList(input.split(",")));
        super.getMail().setRecipients(recipients);
        return "ok " + recipients.size();
    }
}
