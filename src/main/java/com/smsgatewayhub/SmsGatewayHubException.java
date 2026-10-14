package com.smsgatewayhub;

/**
 * The single error type thrown by the SDK (unchecked).
 *
 * <ul>
 *   <li>{@link #getStatus()} - HTTP status, 0 for network / timeout / configuration errors.</li>
 *   <li>{@link #getCode()} - machine readable code, for example {@code bad_request}, {@code http_502},
 *       a classic SMS API ErrorCode such as {@code 13}, {@code network_error}, {@code timeout}
 *       or {@code config_error}.</li>
 *   <li>{@link #getMessage()} - human readable message (never contains your keys).</li>
 *   <li>{@link #getChannel()} - channel that failed (CPaaS errors only), may be null.</li>
 *   <li>{@link #getRaw()} - response body text, clipped to 2000 characters, may be null.</li>
 * </ul>
 */
public class SmsGatewayHubException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String channel;
    private final String raw;

    public SmsGatewayHubException(int status, String code, String message, String channel, String raw) {
        super(message);
        this.status = status;
        this.code = code;
        this.channel = channel;
        this.raw = raw;
    }

    /** HTTP status code, or 0 for network, timeout and configuration errors. */
    public int getStatus() {
        return status;
    }

    /** Error code string. */
    public String getCode() {
        return code;
    }

    /** Channel reported by the CPaaS API (for example "sms" or "whatsapp"), or null. */
    public String getChannel() {
        return channel;
    }

    /** Raw response body (clipped to 2000 characters), or null when there was no response. */
    public String getRaw() {
        return raw;
    }

    /** True when this is a configuration error raised before any network call. */
    public boolean isConfigError() {
        return "config_error".equals(code);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SmsGatewayHubException[");
        sb.append("status=").append(status).append(", code=").append(code);
        if (channel != null) {
            sb.append(", channel=").append(channel);
        }
        sb.append("]: ").append(getMessage());
        return sb.toString();
    }
}
