package com.smsgatewayhub;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Official SMSGatewayHub client for Java (server-side use only - never ship your keys in apps or browsers).
 *
 * <pre>
 * SmsGatewayHub client = SmsGatewayHub.builder()
 *     .apiKey(System.getenv("SGH_API_KEY"))
 *     .cpaasKey(System.getenv("SGH_CPAAS_KEY"))
 *     .build();
 * SendResult r = client.sms().send("SGHTST", "919812345678", "Hello");
 * </pre>
 *
 * <p>Options that are not set fall back to the environment variables SGH_API_KEY, SGH_CPAAS_KEY and
 * SGH_BASE_URL. Instances are immutable and thread-safe.</p>
 */
public final class SmsGatewayHub {

    /** SDK version. */
    public static final String VERSION = "1.0.0";

    /** Default API host. Resellers / white-label partners set their own with {@code baseUrl(...)}. */
    public static final String DEFAULT_BASE_URL = "https://www.smsgatewayhub.com";

    private final Transport transport;
    private final SmsApi sms;
    private final MessagesApi messages;
    private final VerifyLinkApi verifyLink;
    private final EmailVerifyApi emailVerify;

    private SmsGatewayHub(Transport transport) {
        this.transport = transport;
        this.sms = new SmsApi(transport);
        this.messages = new MessagesApi(transport);
        this.verifyLink = new VerifyLinkApi(transport);
        this.emailVerify = new EmailVerifyApi(transport);
    }

    /** Starts building a client. */
    public static Builder builder() {
        return new Builder();
    }

    /** Client configured only from the environment variables SGH_API_KEY, SGH_CPAAS_KEY, SGH_BASE_URL. */
    public static SmsGatewayHub fromEnv() {
        return new Builder().build();
    }

    /** Classic SMS API (needs apiKey). */
    public SmsApi sms() {
        return sms;
    }

    /** CPaaS unified messages API (needs cpaasKey). */
    public MessagesApi messages() {
        return messages;
    }

    /** OneTap Verify (needs cpaasKey). */
    public VerifyLinkApi verifyLink() {
        return verifyLink;
    }

    /** Email Verification (needs cpaasKey). */
    public EmailVerifyApi emailVerify() {
        return emailVerify;
    }

    /** Effective base URL (no trailing slash). */
    public String getBaseUrl() {
        return transport.baseUrl;
    }

    /** Effective timeout in seconds. */
    public int getTimeoutSeconds() {
        return transport.timeoutSeconds;
    }

    /** Effective maximum number of retries. */
    public int getMaxRetries() {
        return transport.maxRetries;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Returns the {@code vl_token} query value from a full OneTap Verify callback URL, or null.
     */
    public static String getVerifyToken(String url) {
        if (url == null) {
            return null;
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        String query = url.substring(q + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash);
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            if ("vl_token".equals(decode(name))) {
                String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
                return value.length() == 0 ? null : value;
            }
        }
        return null;
    }

    /** Alias of {@link #getVerifyToken(String)}. */
    public static String parseRedirect(String url) {
        return getVerifyToken(url);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    // ------------------------------------------------------------------ builder

    /** Builder for {@link SmsGatewayHub}. */
    public static final class Builder {
        private String apiKey;
        private String cpaasKey;
        private String baseUrl;
        private int timeoutSeconds = 30;
        private int maxRetries = 2;

        Builder() {
        }

        /** Classic SMS API key (Panel > API). Default: env SGH_API_KEY. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** CPaaS key "sghcp_..." (Panel > CPaaS > API). Default: env SGH_CPAAS_KEY. */
        public Builder cpaasKey(String cpaasKey) {
            this.cpaasKey = cpaasKey;
            return this;
        }

        /** API host, e.g. https://sms.your-brand.com. Default: env SGH_BASE_URL, else https://www.smsgatewayhub.com. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Connect and read timeout in seconds (default 30). */
        public Builder timeout(int seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        /** Maximum retries for retryable failures (default 2, 0 disables retries). */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public SmsGatewayHub build() {
            String ak = pick(apiKey, "SGH_API_KEY");
            String ck = pick(cpaasKey, "SGH_CPAAS_KEY");
            String url = pick(baseUrl, "SGH_BASE_URL");
            if (url == null) {
                url = DEFAULT_BASE_URL;
            }
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : 30;
            int retries = maxRetries >= 0 ? maxRetries : 0;
            return new SmsGatewayHub(new Transport(ak, ck, url, timeout, retries));
        }

        private static String pick(String value, String envName) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
            String env = null;
            try {
                env = System.getenv(envName);
            } catch (SecurityException ignored) {
                // environment not readable
            }
            if (env != null && env.trim().length() > 0) {
                return env.trim();
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return "SmsGatewayHub[baseUrl=" + transport.baseUrl + "]";
    }
}
