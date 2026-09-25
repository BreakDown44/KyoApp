package de.scangolf.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kleiner JSON-Leser ohne Bibliothek.
 *
 * Ergebnis-Typen: {@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Double}, {@code Boolean} und {@code null}.
 */
public final class Json {

    private final String s;
    private int pos;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != p.s.length()) {
            throw p.error("Unerwartete Zeichen nach dem Ende");
        }
        return v;
    }

    // ------------------------------------------------------------ Zugriffshilfen

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o, String key) {
        Object v = map(o).get(key);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Objekt erwartet: " + key);
        }
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("JSON-Objekt erwartet");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o, String key) {
        Object v = map(o).get(key);
        if (!(v instanceof List)) {
            throw new IllegalArgumentException("Liste erwartet: " + key);
        }
        return (List<Object>) v;
    }

    public static double num(Object o, String key) {
        Object v = map(o).get(key);
        if (!(v instanceof Double)) {
            throw new IllegalArgumentException("Zahl erwartet: " + key);
        }
        return ((Double) v).doubleValue();
    }

    public static double num(Object o, String key, double def) {
        Object v = map(o).get(key);
        return v instanceof Double ? ((Double) v).doubleValue() : def;
    }

    public static String str(Object o, String key) {
        Object v = map(o).get(key);
        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Text erwartet: " + key);
        }
        return (String) v;
    }

    public static boolean has(Object o, String key) {
        return map(o).get(key) != null;
    }

    // ------------------------------------------------------------ Parser

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON-Fehler an Position " + pos + ": " + msg);
    }

    private void skipWs() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object value() {
        if (pos >= s.length()) {
            throw error("Wert erwartet");
        }
        char c = s.charAt(pos);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (s.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new HashMap<String, Object>();
        pos++;
        skipWs();
        if (pos < s.length() && s.charAt(pos) == '}') {
            pos++;
            return m;
        }
        while (true) {
            skipWs();
            if (pos >= s.length() || s.charAt(pos) != '"') {
                throw error("Schlüssel erwartet");
            }
            String k = string();
            skipWs();
            expect(':');
            skipWs();
            m.put(k, value());
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<Object>();
        pos++;
        skipWs();
        if (pos < s.length() && s.charAt(pos) == ']') {
            pos++;
            return l;
        }
        while (true) {
            skipWs();
            l.add(value());
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect(']');
            return l;
        }
    }

    private void expect(char c) {
        if (pos >= s.length() || s.charAt(pos) != c) {
            throw error("'" + c + "' erwartet");
        }
        pos++;
    }

    private String string() {
        pos++;
        StringBuffer sb = new StringBuffer();
        while (true) {
            if (pos >= s.length()) {
                throw error("Text nicht abgeschlossen");
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
                throw error("Escape unvollständig");
            }
            char e = s.charAt(pos++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > s.length()) {
                        throw error("\\u unvollständig");
                    }
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    throw error("Unbekanntes Escape \\" + e);
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw error("Wert erwartet");
        }
        try {
            return Double.valueOf(s.substring(start, pos));
        } catch (NumberFormatException ex) {
            throw error("Zahl ungültig");
        }
    }

    // ------------------------------------------------------------ Schreiben

    /** Text als JSON-String (mit Anführungszeichen) anhängen. */
    public static void quote(StringBuffer sb, String v) {
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String h = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int k = h.length(); k < 4; k++) {
                            sb.append('0');
                        }
                        sb.append(h);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** Zahl so schreiben, dass sie beim Einlesen exakt denselben double-Wert ergibt. */
    public static String number(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("Zahl nicht darstellbar: " + v);
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
