package com.smsgatewayhub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Result of {@code sms().delivery(jobId)}. */
public final class DeliveryReport {

    /** Delivery status of one message. */
    public static final class Report {
        private final String messageId;
        private final String status;
        private final String deliveredAt;

        Report(String messageId, String status, String deliveredAt) {
            this.messageId = messageId;
            this.status = status;
            this.deliveredAt = deliveredAt;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getStatus() {
            return status;
        }

        /** Delivery date/time as returned by the API (DeliveryDate), may be null. */
        public String getDeliveredAt() {
            return deliveredAt;
        }

        @Override
        public String toString() {
            return messageId + ":" + status + "@" + deliveredAt;
        }
    }

    private final List<Report> reports;
    private final Map<String, Object> raw;

    DeliveryReport(List<Report> reports, Map<String, Object> raw) {
        this.reports = Collections.unmodifiableList(reports);
        this.raw = raw;
    }

    static DeliveryReport from(Map<String, Object> m) {
        List<Report> list = new ArrayList<Report>();
        Object data = m.get("DeliveryReports");
        if (data instanceof List) {
            for (Object o : (List<?>) data) {
                if (o instanceof Map) {
                    Map<?, ?> item = (Map<?, ?>) o;
                    list.add(new Report(
                        Transport.str(item.get("MessageId")),
                        Transport.str(item.get("DeliveryStatus")),
                        Transport.str(item.get("DeliveryDate"))));
                }
            }
        }
        return new DeliveryReport(list, m);
    }

    public List<Report> getReports() {
        return reports;
    }

    /** The full parsed API response. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "DeliveryReport" + reports;
    }
}
