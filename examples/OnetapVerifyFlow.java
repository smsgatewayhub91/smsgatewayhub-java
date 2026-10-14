import com.smsgatewayhub.SmsGatewayHub;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OneTap Verify: passwordless login with a one-time link.
 *
 * 1. Your server creates the link (WhatsApp first, SMS fallback).
 * 2. The user taps it and lands on your redirectUrl with ?vl_token=...
 * 3. Your server verifies the token (valid once) and signs the user in.
 *
 * Needs SGH_CPAAS_KEY.
 */
public class OnetapVerifyFlow {
    public static void main(String[] args) {
        SmsGatewayHub client = SmsGatewayHub.fromEnv();

        // Step 1 - create and send
        Map<String, Object> sms = new LinkedHashMap<String, Object>();
        sms.put("text", "Tap to sign in to MyShop: {{link}}");
        sms.put("sender", "SGHTST");

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("to", "919812345678");
        body.put("channels", Arrays.asList("whatsapp", "sms"));
        body.put("fallbackAfter", Integer.valueOf(60));
        body.put("expiry", Integer.valueOf(600));
        body.put("redirectUrl", "https://myshop.example.com/auth/onetap");
        body.put("state", "session-abc");
        body.put("appName", "MyShop");
        body.put("sms", sms);

        Map<String, Object> link = client.verifyLink().create(body);
        System.out.println("Created " + link.get("id") + " status " + link.get("status"));

        // Step 2/3 - in your redirect handler you receive the full URL, e.g.:
        String callbackUrl = args.length > 0 ? args[0]
            : "https://myshop.example.com/auth/onetap?vl_token=PASTE_TOKEN&state=session-abc";
        String token = SmsGatewayHub.getVerifyToken(callbackUrl);
        if (token == null) {
            System.out.println("No vl_token in URL");
            return;
        }
        Map<String, Object> result = client.verifyLink().verify(token);
        System.out.println("Verify result: " + result);

        // Optional: check status or cancel an unused link
        System.out.println(client.verifyLink().get(String.valueOf(link.get("id"))).get("status"));
    }
}
