import com.smsgatewayhub.SmsGatewayHub;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Email Verification: single address and a batch. Needs SGH_CPAAS_KEY.
 */
public class EmailVerify {
    public static void main(String[] args) {
        SmsGatewayHub client = SmsGatewayHub.fromEnv();

        Map<String, Object> one = client.emailVerify().check("someone@example.com");
        System.out.println(one.get("result") + " / " + one.get("reason") + " / suggestion: " + one.get("suggestion"));

        Map<String, Object> batch = client.emailVerify().batch(
            Arrays.asList("a@example.com", "b@example.org", "c@example.net"), "October leads");
        String batchId = String.valueOf(batch.get("batchId"));
        System.out.println("Batch " + batchId);

        Map<String, Object> opts = new LinkedHashMap<String, Object>();
        opts.put("results", Boolean.TRUE);
        opts.put("page", Integer.valueOf(1));
        opts.put("pageSize", Integer.valueOf(100));
        System.out.println(client.emailVerify().batchStatus(batchId, opts));
    }
}
