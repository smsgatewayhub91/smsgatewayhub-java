import com.smsgatewayhub.Balance;
import com.smsgatewayhub.SmsGatewayHub;

/**
 * Print the SMS balance. Needs SGH_API_KEY.
 */
public class GetBalance {
    public static void main(String[] args) {
        SmsGatewayHub client = SmsGatewayHub.fromEnv();
        Balance b = client.sms().balance();
        System.out.println("Promotional: " + b.get("Promo"));
        System.out.println("Transactional: " + b.get("Trans"));
        System.out.println("All: " + b.getBalances());
    }
}
