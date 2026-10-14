import com.smsgatewayhub.SmsGatewayHub;
import com.smsgatewayhub.SmsGatewayHubException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WhatsApp template first; if it is not delivered within 300 seconds, send an SMS.
 * Needs SGH_CPAAS_KEY (sghcp_...).
 */
public class WhatsappWithSmsFallback {
    public static void main(String[] args) {
        SmsGatewayHub client = SmsGatewayHub.builder()
            .cpaasKey(System.getenv("SGH_CPAAS_KEY"))
            .build();

        Map<String, Object> whatsapp = new LinkedHashMap<String, Object>();
        whatsapp.put("template", "order_update");
        whatsapp.put("language", "en");
        whatsapp.put("variables", Arrays.asList("Asha", "1234"));

        Map<String, Object> sms = new LinkedHashMap<String, Object>();
        sms.put("text", "Hi Asha, your order 1234 is shipped.");
        sms.put("sender", "SGHTST");
        sms.put("templateId", "1207XXXXXXXXXXXXXXX");
        sms.put("entityId", "1101XXXXXXXXXXXXXXX");

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("channels", Arrays.asList("whatsapp", "sms"));
        body.put("to", "919812345678");
        body.put("fallbackAfter", Integer.valueOf(300));
        body.put("ref", "order-1234");
        body.put("callbackUrl", "https://example.com/hooks/sgh");
        body.put("whatsapp", whatsapp);
        body.put("sms", sms);

        try {
            Map<String, Object> msg = client.messages().send(body);
            System.out.println("Queued " + msg.get("id") + " status " + msg.get("status"));

            Map<String, Object> status = client.messages().get(String.valueOf(msg.get("id")));
            System.out.println("Now: " + status);
        } catch (SmsGatewayHubException e) {
            System.err.println("Failed: " + e.getStatus() + " " + e.getCode() + " " + e.getMessage()
                + (e.getChannel() != null ? " (channel " + e.getChannel() + ")" : ""));
        }
    }
}
