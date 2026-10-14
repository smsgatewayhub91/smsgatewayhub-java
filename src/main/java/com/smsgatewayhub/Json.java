package com.smsgatewayhub;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny, dependency-free JSON encoder and parser used by the SDK.
 *
 * <p>Parsing maps JSON values to Java as follows: object to {@code LinkedHashMap<String,Object>},
 * array to {@code ArrayList<Object>}, string to {@code String}, integer numbers to {@code Long}
 * (or {@code Double} when out of range), other numbers to {@code Double}, true/false to
 * {@code Boolean} and null to {@code null}.</p>
 *
 * <p>Encoding accepts Map, Iterable, arrays (object and primitive), CharSequence, Character,
 * Number, Boolean, Enum and null; anything else is encoded as its {@code toString()} value.</p>
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ encode

    /** Encodes a value as a JSON string. */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > 200) {
            throw new IllegalArgumentException("JSON nesting too deep (cyclic structure?)");
        }
        if (v == null) {
            sb.append("null");
        } else if (v instanceof CharSequence) {
            writeString(sb, v.toString());
        } else if (v instanceof Character) {
            writeString(sb, String.valueOf(v));
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v).booleanValue() ? "true" : "false");
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(Double.toString(d));
            }
        } else if (v instanceof BigDecimal) {
            sb.append(((BigDecimal) v).toString());
        } else if (v instanceof Number) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Object o : ((Map<?, ?>) v).entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            Iterator<?> it = ((Iterable<?>) v).iterator();
            while (it.hasNext()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, it.next(), depth + 1);
            }
            sb.append(']');
        } else if (v.getClass().isArray()) {
            sb.append('[');
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, Array.get(v, i), depth + 1);
            }
            sb.append(']');
        } else if (v instanceof Enum) {
            writeString(sb, ((Enum<?>) v).name());
        } else {
            writeString(sb, v.toString());
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == 0x7f || c == 0x2028 || c == 0x2029) {
                        sb.append('\\').append('u')
                          .append(HEX[(c >> 12) & 0xF])
                          .append(HEX[(c >> 8) & 0xF])
                          .append(HEX[(c >> 4) & 0xF])
                          .append(HEX[c & 0xF]);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parse

    /**
     * Parses JSON text.
     *
     * @throws IllegalArgumentException when the text is not valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null");
        }
        Parser p = new Parser(text);
        if (p.pos < p.s.length() && p.s.charAt(p.pos) == (char) 0xFEFF) {
            p.pos++;
        }
        p.skipWs();
        Object v = p.readValue(0);
        p.skipWs();
        if (p.pos != p.s.length()) {
            throw p.error("unexpected trailing characters");
        }
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
            this.pos = 0;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + msg);
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue(int depth) {
            if (depth > 500) {
                throw error("nesting too deep");
            }
            if (pos >= s.length()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return readObject(depth);
                case '[':
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    return readLiteral("true", Boolean.TRUE);
                case 'f':
                    return readLiteral("false", Boolean.FALSE);
                case 'n':
                    return readLiteral("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        Object readLiteral(String word, Object value) {
            if (s.startsWith(word, pos)) {
                pos += word.length();
                return value;
            }
            throw error("invalid literal");
        }

        Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            pos++; // {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw error("expected string key");
                }
                String key = readString();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw error("expected ':'");
                }
                pos++;
                skipWs();
                Object value = readValue(depth + 1);
                map.put(key, value);
                skipWs();
                if (pos >= s.length()) {
                    throw error("unterminated object");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw error("expected ',' or '}'");
                }
            }
        }

        List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<Object>();
            pos++; // [
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(readValue(depth + 1));
                skipWs();
                if (pos >= s.length()) {
                    throw error("unterminated array");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw error("expected ',' or ']'");
                }
            }
        }

        String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(e);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("truncated unicode escape");
                        }
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            int d = Character.digit(s.charAt(pos + i), 16);
                            if (d < 0) {
                                throw error("invalid unicode escape");
                            }
                            code = (code << 4) | d;
                        }
                        pos += 4;
                        sb.append((char) code);
                        break;
                    default:
                        throw error("invalid escape character '" + e + "'");
                }
            }
        }

        Object readNumber() {
            int start = pos;
            boolean isInteger = true;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            int intStart = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos)) && s.charAt(pos) < 128) {
                pos++;
            }
            if (pos == intStart) {
                throw error("invalid number");
            }
            if (pos < s.length() && s.charAt(pos) == '.') {
                isInteger = false;
                pos++;
                int fracStart = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                    pos++;
                }
                if (pos == fracStart) {
                    throw error("invalid number fraction");
                }
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isInteger = false;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                int expStart = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                    pos++;
                }
                if (pos == expStart) {
                    throw error("invalid number exponent");
                }
            }
            String num = s.substring(start, pos);
            if (isInteger) {
                try {
                    return Long.valueOf(Long.parseLong(num));
                } catch (NumberFormatException ignored) {
                    // too large for long: fall through to double
                }
            }
            try {
                return Double.valueOf(Double.parseDouble(num));
            } catch (NumberFormatException ex) {
                throw error("invalid number");
            }
        }
    }
}
