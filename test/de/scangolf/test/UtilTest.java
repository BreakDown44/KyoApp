package de.scangolf.test;

import de.scangolf.core.util.Base64;
import de.scangolf.core.util.Json;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class UtilTest {

    public void testBase64RoundTrip() {
        Random rnd = new Random(42);
        for (int len = 0; len < 70; len++) {
            byte[] b = new byte[len];
            rnd.nextBytes(b);
            String enc = Base64.encode(b, 0, b.length);
            Check.equal(java.util.Base64.getEncoder().encodeToString(b), enc, "Base64 wie JDK, Länge " + len);
            byte[] dec = Base64.decode(enc);
            Check.isTrue(java.util.Arrays.equals(b, dec), "Base64 decode, Länge " + len);
        }
    }

    public void testJsonParse() {
        Object o = Json.parse("{\"a\": [1, 2.5, -3e2, true, false, null], \"s\": \"x\\\"y\\u00e4\\n\", \"o\": {}}");
        Map<String, Object> m = Json.map(o);
        List<Object> a = Json.list(o, "a");
        Check.equal(6, a.size(), "Listenlänge");
        Check.equal(1.0, a.get(0), "Zahl 1");
        Check.equal(2.5, a.get(1), "Zahl 2.5");
        Check.equal(-300.0, a.get(2), "Zahl -3e2");
        Check.equal(Boolean.TRUE, a.get(3), "true");
        Check.equal(null, a.get(5), "null");
        Check.equal("x\"yä\n", m.get("s"), "String mit Escapes");
        Check.isTrue(Json.obj(o, "o").isEmpty(), "leeres Objekt");
    }

    public void testJsonRejectsGarbage() {
        String[] bad = {"{", "[1,", "{\"a\" 1}", "tru", "\"abc", "{} x"};
        for (String s : bad) {
            try {
                Json.parse(s);
                throw Check.fail("Kein Fehler bei: " + s);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    public void testJsonNumberRoundTrip() {
        Random rnd = new Random(1);
        for (int i = 0; i < 1000; i++) {
            double v = (rnd.nextDouble() - 0.5) * Math.pow(10, rnd.nextInt(8));
            String s = Json.number(v);
            Check.equal(v, Json.parse(s), "Zahl " + s);
        }
        Check.equal("12", Json.number(12.0), "ganze Zahl ohne .0");
    }
}
