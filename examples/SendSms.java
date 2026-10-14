import com.smsgatewayhub.SendResult;
import com.smsgatewayhub.SmsGatewayHub;
import com.smsgatewayhub.SmsGatewayHubException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Send a classic SMS.
 *
 * Run: set SGH_API_KEY, then
 *   javac -cp smsgatewayhub-1.0.0.jar SendSms.java
 *   java -cp smsgatewayhub-1.0.0.jar:. SendSms      (use ; instead of : on Windows)
 */
public class SendSms {
    public static void main(String[] args) {
        SmsGatewayHub client = SmsGatewayHub.builder()
            .apiKey(System.getenv("SGH_API_KEY"))
            .build();

        try {
            // Simple form: transactional (channel 2), route 1, unicode auto-detected.
            SendResult r = client.sms().send("SGHTST", "919812345678", "Your OTP is 123456");
            System.out.println("Job " + r.getJobId() + " -> " + r.getMessages());

            // Full form with DLT details and two numbers.
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put("senderId", "SGHTST");
            p.put("to", Arrays.asList("919812345678", "919812345679"));
            p.put("text", "Dear customer, your order 1234 is shipped.");
            p.put("channel", "transactional");
            p.put("entityId", "1101XXXXXXXXXXXXXXX");
            p.put("dltTemplateId", "1207XXXXXXXXXXXXXXX");
            SendResult r2 = client.sms().send(p);
            for (SendResult.Message m : r2.getMessages()) {
                System.out.println(m.getNumber() + " -> " + m.getMessageId());
            }
        } catch (SmsGatewayHubException e) {
            System.err.println("Failed: " + e.getCode() + " " + e.getMessage());
        }
    }
}
