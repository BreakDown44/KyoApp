package de.scangolf.core.util;

/** Minimaler Base64-Codec (RFC 4648, mit Padding), weil java.util.Base64 nicht überall existiert. */
public final class Base64 {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DECODE = new int[128];

    static {
        for (int i = 0; i < DECODE.length; i++) {
            DECODE[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length; i++) {
            DECODE[ALPHABET[i]] = i;
        }
    }

    private Base64() {
    }

    public static String encode(byte[] data, int off, int len) {
        StringBuffer sb = new StringBuffer(((len + 2) / 3) * 4);
        int end = off + len;
        int i = off;
        while (i + 2 < end) {
            int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
            sb.append(ALPHABET[(v >> 18) & 63]).append(ALPHABET[(v >> 12) & 63])
                    .append(ALPHABET[(v >> 6) & 63]).append(ALPHABET[v & 63]);
            i += 3;
        }
        int rest = end - i;
        if (rest == 1) {
            int v = (data[i] & 0xFF) << 16;
            sb.append(ALPHABET[(v >> 18) & 63]).append(ALPHABET[(v >> 12) & 63]).append("==");
        } else if (rest == 2) {
            int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8);
            sb.append(ALPHABET[(v >> 18) & 63]).append(ALPHABET[(v >> 12) & 63])
                    .append(ALPHABET[(v >> 6) & 63]).append('=');
        }
        return sb.toString();
    }

    public static byte[] decode(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 128 && DECODE[c] >= 0) {
                n++;
            } else if (c != '=' && c != '\n' && c != '\r' && c != ' ') {
                throw new IllegalArgumentException("Ungültiges Base64-Zeichen: " + c);
            }
        }
        byte[] out = new byte[(n * 6) / 8];
        int acc = 0;
        int bits = 0;
        int o = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || DECODE[c] < 0) {
                continue;
            }
            acc = (acc << 6) | DECODE[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) ((acc >> bits) & 0xFF);
            }
        }
        return out;
    }
}
