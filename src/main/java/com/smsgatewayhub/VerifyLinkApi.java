package com.smsgatewayhub;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OneTap Verify ({@code client.verifyLink()}): passwordless one-time verification links sent over
 * WhatsApp, SMS or email. Uses the {@code cpaasKey}.
 *
 * <p>POST calls here are never repeated once the request reached the server (only connect failures
 * are retried).</p>
 */
public final class VerifyLinkApi {

    static final String PATH = "/cpaas/v1/verify-link.ashx";

    private final Transport t;

    VerifyLinkApi(Transport t) {
        this.t = t;
    }

    /**
     * Creates and sends a OneTap Verify link. Body keys: to, toEmail, channels, fallbackAfter, expiry,
     * redirectUrl, state, ref, appName, sms, whatsapp, email. {@code {{link}}} inside channel objects is
     * replaced server-side by the one-time link.
     */
    public Map<String, Object> create(Map<String, Object> body) {
        t.requireCpaasKey();
        if (body == null) {
            throw Transport.configError("body is required");
        }
        return t.cpaasResult(t.execute("POST", PATH, Json.stringify(body), true, false, null));
    }

    /**
     * Verifies the token from the {@code vl_token} query parameter on your redirectUrl (valid once).
     * See {@link SmsGatewayHub#getVerifyToken(String)}.
     */
    public Map<String, Object> verify(String token) {
        t.requireCpaasKey();
        if (Transport.isEmpty(token)) {
            throw Transport.configError("token is required");
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("token", token.trim());
        return t.cpaasResult(t.execute("POST", PATH + "?action=verify", Json.stringify(body), true, false, null));
    }

    /** Status of a link ("vl_..."): created, sent, opened, confirmed, verified, expired, failed, cancelled. */
    public Map<String, Object> get(String id) {
        t.requireCpaasKey();
        if (Transport.isEmpty(id)) {
            throw Transport.configError("id is required");
        }
        Transport.Query q = new Transport.Query().add("id", id.trim());
        return t.cpaasResult(t.execute("GET", PATH + "?" + q, null, true, true, null));
    }

    /** Cancels a link that has not been used yet. */
    public Map<String, Object> cancel(String id) {
        t.requireCpaasKey();
        if (Transport.isEmpty(id)) {
            throw Transport.configError("id is required");
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", id.trim());
        return t.cpaasResult(t.execute("POST", PATH + "?action=cancel", Json.stringify(body), true, false, null));
    }
}
