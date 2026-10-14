package com.smsgatewayhub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Email Verification ({@code client.emailVerify()}). Uses the {@code cpaasKey}.
 */
public final class EmailVerifyApi {

    static final String PATH = "/cpaas/v1/email-verify.ashx";
    static final int MAX_BATCH = 1000;

    private final Transport t;

    EmailVerifyApi(Transport t) {
        this.t = t;
    }

    /**
     * Verifies one address. The result contains result (valid, invalid, risky, unknown), reason,
     * suggestion, disposable, roleBased, freeProvider, hasMx and domain.
     */
    public Map<String, Object> check(String email) {
        t.requireCpaasKey();
        if (Transport.isEmpty(email)) {
            throw Transport.configError("email is required");
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("email", email.trim());
        return t.cpaasResult(t.execute("POST", PATH, Json.stringify(body), true, false, null));
    }

    /** Starts a batch (max 1000 addresses). The result contains {@code batchId}. */
    public Map<String, Object> batch(Collection<String> emails) {
        return batch(emails, null);
    }

    /** Starts a named batch (max 1000 addresses). The result contains {@code batchId}. */
    public Map<String, Object> batch(Collection<String> emails, String name) {
        t.requireCpaasKey();
        if (emails == null || emails.isEmpty()) {
            throw Transport.configError("emails are required");
        }
        if (emails.size() > MAX_BATCH) {
            throw Transport.configError("Max 1000 emails per batch");
        }
        List<String> list = new ArrayList<String>(emails);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("emails", list);
        if (!Transport.isEmpty(name)) {
            body.put("name", name);
        }
        return t.cpaasResult(t.execute("POST", PATH, Json.stringify(body), true, false, null));
    }

    /** Batch progress / summary. */
    public Map<String, Object> batchStatus(String batchId) {
        return batchStatus(batchId, null);
    }

    /**
     * Batch status. Optional keys: {@code results} (Boolean, include per-address results), {@code page},
     * {@code pageSize}, {@code filter}.
     */
    public Map<String, Object> batchStatus(String batchId, Map<String, Object> options) {
        t.requireCpaasKey();
        if (Transport.isEmpty(batchId)) {
            throw Transport.configError("batchId is required");
        }
        Transport.Query q = new Transport.Query().add("batch", batchId.trim());
        if (options != null) {
            if (Transport.truthy(options.get("results"))) {
                q.add("results", "1");
            }
            q.addIfPresent("page", options.get("page"));
            q.addIfPresent("pageSize", options.get("pageSize"));
            q.addIfPresent("filter", options.get("filter"));
        }
        return t.cpaasResult(t.execute("GET", PATH + "?" + q, null, true, true, null));
    }
}
