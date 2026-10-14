package com.smsgatewayhub;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of {@code sms().balance()}.
 *
 * <p>{@code "Balance":"Promo:9988|Trans:0"} becomes {@code {Promo=9988, Trans=0}}. Values are
 * {@code Long} or {@code Double} when numeric, otherwise the original {@code String}. Unknown parts
 * are kept under their own name.</p>
 */
public final class Balance {

    private final Map<String, Object> balances;
    private final Map<String, Object> raw;

    Balance(Map<String, Object> balances, Map<String, Object> raw) {
        this.balances = Collections.unmodifiableMap(balances);
        this.raw = raw;
    }

    static Balance from(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Object b = m.get("Balance");
        if (b instanceof String) {
            String[] parts = ((String) b).split("\\|");
            for (String part : parts) {
                String p = part.trim();
                if (p.length() == 0) {
                    continue;
                }
                int idx = p.indexOf(':');
                String name = idx < 0 ? "Balance" : p.substring(0, idx).trim();
                String value = idx < 0 ? p : p.substring(idx + 1).trim();
                out.put(name, toNumber(value));
            }
        } else if (b instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) b).entrySet()) {
                Object v = e.getValue();
                out.put(String.valueOf(e.getKey()), v instanceof String ? toNumber((String) v) : v);
            }
        } else if (b != null) {
            out.put("Balance", b);
        }
        return new Balance(out, m);
    }

    private static Object toNumber(String s) {
        try {
            return Long.valueOf(Long.parseLong(s));
        } catch (NumberFormatException ignored) {
            // not an integer
        }
        try {
            return Double.valueOf(Double.parseDouble(s));
        } catch (NumberFormatException ignored) {
            return s;
        }
    }

    /** All balances by name (for example "Promo", "Trans"). */
    public Map<String, Object> getBalances() {
        return balances;
    }

    /** Numeric balance by name, or null when missing / not numeric. */
    public Number get(String name) {
        Object v = balances.get(name);
        return v instanceof Number ? (Number) v : null;
    }

    /** The full parsed API response. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "Balance" + balances;
    }
}
