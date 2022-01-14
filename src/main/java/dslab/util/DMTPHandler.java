package dslab.util;


import java.util.Arrays;
import java.util.List;

public class DMTPHandler {

    private final Mail MAIL = new Mail();
    private final String DELIMITER = ",";
    private state current = state.IDLE;
    private int counter = 0;


    public DMTPHandler() {
    }

    enum state {
        IDLE,
        INPUT,
        SENT,
    }

    public Mail getMail() {
        return this.MAIL;
    }

    public String processRequest(String request) {

        if (request.equals("begin") && this.current == state.IDLE) {

            this.current = state.INPUT;
            return "ok";

        } else if (request.startsWith("quit")) {

            return "ok bye";

        } else if (this.current == state.INPUT) {

            if (request.startsWith("from ")) {

                int offset = 5;
                MAIL.setSender(request.substring(offset));
                return "ok";

            }

            else if (request.startsWith("to ")) {

                int offset = 3;
                request = request.substring(offset);
                List<String> mails = Arrays.asList(request.split(DELIMITER));
                MAIL.setRecipients(mails);
                return "ok " + mails.size();

            } else if (request.startsWith("subject ")) {

                int offset = 8;
                MAIL.setSubject(request.substring(offset));
                return "ok";

            } else if (request.startsWith("data ")) {

                int offset = 5;
                MAIL.setData(request.substring(offset));
                return "ok";

            } else if (request.startsWith("hash ")){

                int offset = 5;
                MAIL.setHash(request.substring(offset));
                return "ok";

            } else if (request.startsWith("send")) {

                String check = MAIL.validateMail();
                if(check.isEmpty()){
                    this.current = state.SENT;
                    return "ok";
                } else {
                    return check;
                }

            }
        }

        return "error protocol error";
    }

    public String processTransfer(String msg, Mail mail) {

        if (msg.startsWith("error unknown recipient")) {

            throw new RuntimeException("wrong Recipient in Transfer Handler");

        } else {

            if ((msg.equals("ok DMTP2.0") || msg.equals("ok"))  && counter == 0) {

                counter++;
                return "begin";

            } else if (msg.equals("ok") && counter == 1) {

                counter++;
                return "to " + mail.getRecipients();

            } else if (msg.equals("ok 1") && counter == 2) {

                counter++;
                return "from " + mail.getSender();

            } else if (msg.equals("ok") && counter == 3) {

                counter++;
                return "data " + mail.getData();

            } else if (msg.equals("ok") && counter == 4) {

                counter++;
                return "subject " + mail.getSubject();

            }else if (msg.equals("ok") && counter == 5) {

                counter = 0;
                return "send";

            }
            return "error protocol error";
        }
    }

}












