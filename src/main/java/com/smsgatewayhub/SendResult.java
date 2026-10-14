package com.smsgatewayhub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Result of {@code sms().send(...)}. */
public final class SendResult {

    /** One accepted number and its message id. */
    public static final class Message {
        private final String number;
        private final String messageId;

        Message(String number, String messageId) {
            this.number = number;
            this.messageId = messageId;
        }

        public String getNumber() {
            return number;
        }

        public String getMessageId() {
            return messageId;
        }

        @Override
        public String toString() {
            return number + "=" + messageId;
        }
    }

    private final String jobId;
    private final List<Message> messages;
    private final Map<String, Object> raw;

    SendResult(String jobId, List<Message> messages, Map<String, Object> raw) {
        this.jobId = jobId;
        this.messages = Collections.unmodifiableList(messages);
        this.raw = raw;
    }

    static SendResult from(Map<String, Object> m) {
        List<Message> list = new ArrayList<Message>();
        Object data = m.get("MessageData");
        if (data instanceof List) {
            for (Object o : (List<?>) data) {
                if (o instanceof Map) {
                    Map<?, ?> item = (Map<?, ?>) o;
                    list.add(new Message(Transport.str(item.get("Number")), Transport.str(item.get("MessageId"))));
                }
            }
        }
        return new SendResult(Transport.str(m.get("JobId")), list, m);
    }

    /** Job id, used with {@code sms().delivery(jobId)}. */
    public String getJobId() {
        return jobId;
    }

    /** Accepted numbers with their message ids. */
    public List<Message> getMessages() {
        return messages;
    }

    /** The full parsed API response. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "SendResult[jobId=" + jobId + ", messages=" + messages + "]";
    }
}
