import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import org.payable.ecr.ECRTerminal;

/**
 * PAYable SDK Demo
 *
 */
class Demo {

    static ECRTerminal ecrTerminal;

    public static void main(String[] args) {

        try {

            ecrTerminal = new ECRTerminal("192.168.2.204", new ECRTerminal.Listener() {

                @Override
                public void onOpen(String data) {
                    String request = "{\"endpoint\":\"PAYMENT\",\"amount\":30.00,\"id\":1,\"method\":\"CARD\",\"order_tracking\":\"some_id\",\"receipt_email\":\"aslam@payable.lk\",\"receipt_sms\":\"0762724081\",\"txid\":14526}";
                    ecrTerminal.send(request);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {

                }

                @Override
                public void onMessage(String message) {

                }

                @Override
                public void onMessage(ByteBuffer message) {

                }

                @Override
                public void onError(Exception ex) {

                }
            });

            ecrTerminal.debug = true;
            ecrTerminal.connect();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
