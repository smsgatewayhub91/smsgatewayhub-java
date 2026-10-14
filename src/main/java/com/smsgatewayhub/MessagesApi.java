package com.smsgatewayhub;

import java.util.Map;
import java.util.UUID;

/**
 * CPaaS unified messages API ({@code client.messages()}): one call, many channels with fallback.
 * Uses the {@code cpaasKey}.
 */
public final class MessagesApi {

    static final String PATH = "/cpaas/v1/messages.ashx";

    private final Transport t;

    MessagesApi(Transport t) {
        this.t = t;
    }

    /**
     * Sends a message. A random Idempotency-Key is generated and reused on every retry.
     *
     * @param body request body, passed through as JSON (channels, to, toEmail, fallbackAfter, ref,
     *             callbackUrl, whatsapp, sms, rcs, email ...)
     * @return the parsed response (contains {@code id} "cp_..." and {@code status})
     */
    public Map<String, Object> send(Map<String, Object> body) {
        return send(body, null);
    }

    /**
     * Sends a message with your own Idempotency-Key (null or empty = generate one).
     */
    public Map<String, Object> send(Map<String, Object> body, String idempotencyKey) {
        t.requireCpaasKey();
        if (body == null) {
            throw Transport.configError("message body is required");
        }
        String key = Transport.isEmpty(idempotencyKey) ? UUID.randomUUID().toString() : idempotencyKey.trim();
        Transport.Response r = t.execute("POST", PATH, Json.stringify(body), true, true, key);
        return t.cpaasResult(r);
    }

    /** Status and event timeline of one message ("cp_..."). */
    public Map<String, Object> get(String id) {
        t.requireCpaasKey();
        if (Transport.isEmpty(id)) {
            throw Transport.configError("id is required");
        }
        Transport.Query q = new Transport.Query().add("id", id.trim());
        return t.cpaasResult(t.execute("GET", PATH + "?" + q, null, true, true, null));
    }

    /** Lists recent messages. */
    public Map<String, Object> list() {
        return list(null);
    }

    /**
     * Lists messages. Optional keys: {@code status}, {@code ref}, {@code limit}; empty values are omitted.
     */
    public Map<String, Object> list(Map<String, Object> params) {
        t.requireCpaasKey();
        Transport.Query q = new Transport.Query().add("action", "list");
        if (params != null) {
            q.addIfPresent("status", params.get("status"));
            q.addIfPresent("ref", params.get("ref"));
            q.addIfPresent("limit", params.get("limit"));
        }
        return t.cpaasResult(t.execute("GET", PATH + "?" + q, null, true, true, null));
    }
}
