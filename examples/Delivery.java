import com.smsgatewayhub.DeliveryReport;
import com.smsgatewayhub.SmsGatewayHub;

/**
 * Delivery reports for a job id. Needs SGH_API_KEY.
 * Usage: java Delivery 20047
 */
public class Delivery {
    public static void main(String[] args) {
        String jobId = args.length > 0 ? args[0] : "20047";
        SmsGatewayHub client = SmsGatewayHub.fromEnv();
        DeliveryReport d = client.sms().delivery(jobId);
        for (DeliveryReport.Report r : d.getReports()) {
            System.out.println(r.getMessageId() + " " + r.getStatus() + " " + r.getDeliveredAt());
        }
    }
}
