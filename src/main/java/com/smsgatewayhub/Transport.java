package com.smsgatewayhub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;

/** Internal HTTP layer: headers, retries, error mapping. Not part of the public API. */
final class Transport {

    static final String USER_AGENT = "smsgatewayhub-java/" + SmsGatewayHub.VERSION;
    static final int RAW_LIMIT = 2000;
    static final Charset UTF8 = Charset.forName("UTF-8");

    static final String API_KEY_MISSING =
        "apiKey is required for the sms group. Find it in the panel under Panel > API "
        + "(or set the SGH_API_KEY environment variable).";
    static final String CPAAS_KEY_MISSING =
        "cpaasKey (sghcp_...) is required for messages, verifyLink and emailVerify. Find it in the panel under "
        + "Panel > CPaaS > API (or set the SGH_CPAAS_KEY environment variable).";

    final String apiKey;
    final String cpaasKey;
    final String baseUrl;
    final int timeoutSeconds;
    final int maxRetries;

    Transport(String apiKey, String cpaasKey, String baseUrl, int timeoutSeconds, int maxRetries) {
        this.apiKey = apiKey;
        this.cpaasKey = cpaasKey;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    // ------------------------------------------------------------------ key checks

    void requireApiKey() {
        if (isEmpty(apiKey)) {
            throw configError(API_KEY_MISSING);
        }
    }

    void requireCpaasKey() {
        if (isEmpty(cpaasKey)) {
            throw configError(CPAAS_KEY_MISSING);
        }
    }

    static SmsGatewayHubException configError(String message) {
        return new SmsGatewayHubException(0, "config_error", message, null, null);
    }

    // ------------------------------------------------------------------ HTTP

    /** Which failures may be retried. */
    enum RetryMode {
        /** Network errors, 429 and 5xx (GET requests, CPaaS message sends with an Idempotency-Key). */
        ALWAYS,
        /** Only connect failures - the request never left this machine (other POSTs). */
        CONNECT_ONLY,
        /** Connect failures and HTTP 429 only - never timeouts, read errors or 5xx (classic SendSMS). */
        CONNECT_OR_429
    }

    /**
     * Executes one logical request with retries.
     *
     * @param retryAfterSend true = {@link RetryMode#ALWAYS}, false = {@link RetryMode#CONNECT_ONLY}
     */
    Response execute(String method, String pathAndQuery, String jsonBody, boolean cpaasAuth,
                     boolean retryAfterSend, String idempotencyKey) {
        return execute(method, pathAndQuery, jsonBody, cpaasAuth,
            retryAfterSend ? RetryMode.ALWAYS : RetryMode.CONNECT_ONLY, idempotencyKey);
    }

    /** Executes one logical request with retries according to {@code mode}. */
    Response execute(String method, String pathAndQuery, String jsonBody, boolean cpaasAuth,
                     RetryMode mode, String idempotencyKey) {
        byte[] payload = jsonBody == null ? null : jsonBody.getBytes(UTF8);
        int timeoutMs = timeoutSeconds * 1000;
        int attempt = 0;
        while (true) {
            boolean connected = false;
            try {
                URL url = new URL(baseUrl + pathAndQuery);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setUseCaches(false);
                conn.setRequestMethod(method);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Accept", "application/json");
                if (cpaasAuth) {
                    conn.setRequestProperty("Authorization", "Bearer " + cpaasKey);
                }
                if (idempotencyKey != null) {
                    conn.setRequestProperty("Idempotency-Key", idempotencyKey);
                }
                if (payload != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    // Streaming mode also stops HttpURLConnection from silently re-sending a POST.
                    conn.setFixedLengthStreamingMode(payload.length);
                }
                conn.connect();
                connected = true;
                if (payload != null) {
                    OutputStream os = conn.getOutputStream();
                    try {
                        os.write(payload);
                        os.flush();
                    } finally {
                        os.close();
                    }
                }
                int status = conn.getResponseCode();
                String body = readBody(conn, status);
                boolean retryStatus;
                if (mode == RetryMode.ALWAYS) {
                    retryStatus = status == 429 || status >= 500;
                } else if (mode == RetryMode.CONNECT_OR_429) {
                    retryStatus = status == 429;
                } else {
                    retryStatus = false;
                }
                if (retryStatus && attempt < maxRetries) {
                    String retryAfter = status == 429 ? conn.getHeaderField("Retry-After") : null;
                    sleep(retryDelayMs(attempt, retryAfter));
                    attempt++;
                    continue;
                }
                return new Response(status, body);
            } catch (MalformedURLException e) {
                throw configError("Invalid baseUrl: " + redact(baseUrl));
            } catch (HttpRetryException e) {
                // Thrown in streaming mode for authentication challenges / redirects: surface the status.
                return new Response(e.responseCode(), "");
            } catch (IOException e) {
                // After the connection was opened only ALWAYS mode may repeat the request.
                boolean retryable = (!connected || mode == RetryMode.ALWAYS) && attempt < maxRetries;
                if (retryable) {
                    sleep(retryDelayMs(attempt, null));
                    attempt++;
                    continue;
                }
                throw networkError(e);
            }
        }
    }

    private static String readBody(HttpURLConnection conn, int status) throws IOException {
        InputStream in;
        if (status >= 400) {
            in = conn.getErrorStream();
        } else {
            in = conn.getInputStream();
        }
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), UTF8);
        } finally {
            in.close();
        }
    }

    static long retryDelayMs(int attempt, String retryAfter) {
        if (retryAfter != null) {
            try {
                long sec = Long.parseLong(retryAfter.trim());
                if (sec >= 0) {
                    return Math.min(sec, 10L) * 1000L;
                }
            } catch (NumberFormatException ignored) {
                // HTTP-date or garbage: fall back to backoff
            }
        }
        long ms = 500L << Math.min(attempt, 4);
        return Math.min(ms, 4000L);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsGatewayHubException(0, "network_error", "Interrupted while waiting to retry", null, null);
        }
    }

    private SmsGatewayHubException networkError(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return new SmsGatewayHubException(0, "timeout",
                "Request timed out after " + timeoutSeconds + " s", null, null);
        }
        String detail = e.getMessage();
        String msg = "Network error (" + e.getClass().getSimpleName() + ")";
        if (detail != null && detail.length() > 0) {
            msg = msg + ": " + detail;
        }
        return new SmsGatewayHubException(0, "network_error", redact(msg), null, null);
    }

    // ------------------------------------------------------------------ response mapping

    /** Maps a CPaaS response: non-2xx throws, 2xx must be a JSON object. */
    @SuppressWarnings("unchecked")
    Map<String, Object> cpaasResult(Response r) {
        if (r.status < 200 || r.status >= 300) {
            throw httpError(r);
        }
        Object parsed = tryParse(r.body);
        if (!(parsed instanceof Map)) {
            throw new SmsGatewayHubException(r.status, "invalid_response",
                "Response body is not a JSON object", null, clip(r.body));
        }
        return (Map<String, Object>) parsed;
    }

    /** Maps a classic SMS API response: non-2xx throws, ErrorCode other than 000 / 0 throws. */
    @SuppressWarnings("unchecked")
    Map<String, Object> classicResult(Response r) {
        if (r.status < 200 || r.status >= 300) {
            throw httpError(r);
        }
        Object parsed = tryParse(r.body);
        if (!(parsed instanceof Map)) {
            throw new SmsGatewayHubException(r.status, "invalid_response",
                "Response body is not a JSON object", null, clip(r.body));
        }
        Map<String, Object> m = (Map<String, Object>) parsed;
        String ec = str(m.get("ErrorCode"));
        if (ec != null && ec.length() > 0 && !"000".equals(ec) && !"0".equals(ec)) {
            String em = str(m.get("ErrorMessage"));
            throw new SmsGatewayHubException(r.status, ec, redact(em == null ? "SMS API error " + ec : em),
                null, clip(r.body));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    SmsGatewayHubException httpError(Response r) {
        String code = "http_" + r.status;
        String message = "HTTP " + r.status;
        String channel = null;
        Object parsed = tryParse(r.body);
        if (parsed instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) parsed;
            Object err = m.get("error");
            if (err instanceof Map) {
                Map<String, Object> em = (Map<String, Object>) err;
                String c = str(em.get("code"));
                if (c != null && c.length() > 0) {
                    code = c;
                }
                String msg = str(em.get("message"));
                if (msg != null && msg.length() > 0) {
                    message = msg;
                }
                channel = str(em.get("channel"));
            } else if (err instanceof String && ((String) err).length() > 0) {
                message = (String) err;
            } else if (m.get("ErrorMessage") != null) {
                message = str(m.get("ErrorMessage"));
            }
        }
        return new SmsGatewayHubException(r.status, code, redact(message), channel, clip(r.body));
    }

    private static Object tryParse(String body) {
        if (body == null || body.trim().length() == 0) {
            return null;
        }
        try {
            return Json.parse(body);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    String clip(String body) {
        if (body == null) {
            return null;
        }
        String s = redact(body);
        if (s.length() > RAW_LIMIT) {
            s = s.substring(0, RAW_LIMIT);
        }
        return s;
    }

    /** Removes the keys (plain and URL-encoded) from any text that may end up in an exception. */
    String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        out = redactOne(out, apiKey);
        out = redactOne(out, cpaasKey);
        return out;
    }

    private static String redactOne(String text, String key) {
        if (isEmpty(key)) {
            return text;
        }
        String out = text.replace(key, "***");
        String enc = enc(key);
        if (!enc.equals(key)) {
            out = out.replace(enc, "***");
        }
        return out;
    }

    static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }
    }

    /** Converts a JSON scalar to a string; integral doubles lose their ".0". */
    static String str(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            return (String) v;
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d);
            }
            return Double.toString(d);
        }
        return String.valueOf(v);
    }

    static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue() != 0;
        }
        String s = v.toString().trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
    }

    /** Ordered query-string builder; every key and value is URL-encoded as UTF-8. */
    static final class Query {
        private final StringBuilder sb = new StringBuilder();

        Query add(String key, String value) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(enc(key)).append('=').append(enc(value == null ? "" : value));
            return this;
        }

        Query addIfPresent(String key, Object value) {
            String s = str(value);
            if (s != null && s.length() > 0) {
                add(key, s);
            }
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
