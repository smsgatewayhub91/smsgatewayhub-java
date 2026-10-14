package com.smsgatewayhub;

import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classic SMS API ({@code client.sms()}): send, balance, delivery. Uses the {@code apiKey}.
 */
public final class SmsApi {

    /** Format used for {@code scheduleAt} date/time objects, e.g. 2026/10/06 08:30:00 PM. */
    public static final String SCHEDULE_PATTERN = "yyyy/MM/dd hh:mm:ss a";

    static final int MAX_NUMBERS = 100;

    private final Transport t;

    SmsApi(Transport t) {
        this.t = t;
    }

    /** Sends a transactional SMS (channel 2, route 1) with automatic unicode detection. */
    public SendResult send(String senderId, Object to, String text) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("senderId", senderId);
        p.put("to", to);
        p.put("text", text);
        return send(p);
    }

    /**
     * Sends an SMS.
     *
     * <p>Keys: {@code senderId}, {@code to} (String, Collection or array; max 100 numbers),
     * {@code text}, {@code channel} (1, 2, "OTP", "promotional", "transactional", "otp"; default 2),
     * {@code route} (default 1), {@code unicode} (Boolean; auto-detected when absent),
     * {@code flash} (Boolean), {@code scheduleAt} (String as-is, or Date / Calendar / java.time value),
     * {@code groupId}, {@code entityId}, {@code dltTemplateId}.</p>
     */
    public SendResult send(Map<String, Object> params) {
        t.requireApiKey();
        if (params == null) {
            throw Transport.configError("send parameters are required");
        }
        String senderId = Transport.str(params.get("senderId"));
        if (Transport.isEmpty(senderId)) {
            throw Transport.configError("senderId is required");
        }
        String text = Transport.str(params.get("text"));
        if (text == null || text.length() == 0) {
            throw Transport.configError("text is required");
        }
        String numbers = joinNumbers(params.get("to"));
        String channel = normalizeChannel(params.get("channel"));
        Object routeValue = params.get("route");
        String route = routeValue == null ? "1" : Transport.str(routeValue);
        if (Transport.isEmpty(route)) {
            route = "1";
        }
        Object uni = params.get("unicode");
        boolean unicode = uni == null ? hasNonAscii(text) : Transport.truthy(uni);
        boolean flash = Transport.truthy(params.get("flash"));

        Transport.Query q = new Transport.Query()
            .add("APIKey", t.apiKey)
            .add("senderid", senderId)
            .add("channel", channel)
            .add("DCS", unicode ? "8" : "0")
            .add("flashsms", flash ? "1" : "0")
            .add("number", numbers)
            .add("text", text)
            .add("route", route);
        Object sched = params.get("scheduleAt");
        if (sched != null) {
            q.addIfPresent("schedtime", formatSchedule(sched));
        }
        q.addIfPresent("groupid", params.get("groupId"));
        q.addIfPresent("EntityId", params.get("entityId"));
        q.addIfPresent("dlttemplateid", params.get("dltTemplateId"));

        // SendSMS is not idempotent: retry only connect failures and HTTP 429 (never timeouts / 5xx).
        Transport.Response r = t.execute("GET", "/api/mt/SendSMS?" + q, null, false,
            Transport.RetryMode.CONNECT_OR_429, null);
        return SendResult.from(t.classicResult(r));
    }

    /** Account balance, e.g. {Promo=9988, Trans=0}. */
    public Balance balance() {
        t.requireApiKey();
        Transport.Query q = new Transport.Query().add("APIKey", t.apiKey);
        Transport.Response r = t.execute("GET", "/api/mt/GetBalance?" + q, null, false, true, null);
        return Balance.from(t.classicResult(r));
    }

    /** Delivery reports for a job id returned by {@link #send(Map)}. */
    public DeliveryReport delivery(String jobId) {
        t.requireApiKey();
        if (Transport.isEmpty(jobId)) {
            throw Transport.configError("jobId is required");
        }
        Transport.Query q = new Transport.Query().add("APIKey", t.apiKey).add("jobid", jobId.trim());
        Transport.Response r = t.execute("GET", "/api/mt/GetDelivery?" + q, null, false, true, null);
        return DeliveryReport.from(t.classicResult(r));
    }

    // ------------------------------------------------------------------ helpers

    static String joinNumbers(Object to) {
        List<String> items = new ArrayList<String>();
        if (to == null) {
            throw Transport.configError("to is required");
        }
        if (to instanceof Iterable) {
            for (Object o : (Iterable<?>) to) {
                addNumbers(items, o);
            }
        } else if (to.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(to);
            for (int i = 0; i < n; i++) {
                addNumbers(items, java.lang.reflect.Array.get(to, i));
            }
        } else {
            addNumbers(items, to);
        }
        if (items.isEmpty()) {
            throw Transport.configError("to is required");
        }
        if (items.size() > MAX_NUMBERS) {
            throw Transport.configError("Max 100 numbers per request");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static void addNumbers(List<String> items, Object o) {
        String s = Transport.str(o);
        if (s == null) {
            return;
        }
        for (String part : s.split(",")) {
            String p = part.trim();
            if (p.length() > 0) {
                items.add(p);
            }
        }
    }

    static String normalizeChannel(Object channel) {
        if (channel == null) {
            return "2";
        }
        String c = Transport.str(channel).trim().toLowerCase(Locale.ROOT);
        if (c.length() == 0 || c.equals("2") || c.equals("transactional")) {
            return "2";
        }
        if (c.equals("1") || c.equals("promotional")) {
            return "1";
        }
        if (c.equals("otp")) {
            return "OTP";
        }
        throw Transport.configError("channel must be 1, 2 or \"OTP\" (or \"promotional\", \"transactional\", \"otp\")");
    }

    static boolean hasNonAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    static String formatSchedule(Object value) {
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Date) {
            return new SimpleDateFormat(SCHEDULE_PATTERN, Locale.US).format((Date) value);
        }
        if (value instanceof Calendar) {
            Calendar cal = (Calendar) value;
            SimpleDateFormat f = new SimpleDateFormat(SCHEDULE_PATTERN, Locale.US);
            f.setTimeZone(cal.getTimeZone());
            return f.format(cal.getTime());
        }
        if (value instanceof TemporalAccessor) {
            try {
                return DateTimeFormatter.ofPattern(SCHEDULE_PATTERN, Locale.US).format((TemporalAccessor) value);
            } catch (DateTimeException e) {
                throw Transport.configError("scheduleAt must contain a date and a time (use LocalDateTime or ZonedDateTime)");
            }
        }
        throw Transport.configError("scheduleAt must be a String, Date, Calendar or java.time date-time");
    }
}
