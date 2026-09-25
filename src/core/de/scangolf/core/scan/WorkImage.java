package de.scangolf.core.scan;

/**
 * Herunterskaliertes Graustufenbild (1 Byte je Pixel) für die Markensuche.
 * Box-Filter über factor x factor Pixel; das Original wird nicht kopiert.
 */
final class WorkImage {

    final byte[] gray;
    final int w;
    final int h;
    final int factor;

    private WorkImage(byte[] gray, int w, int h, int factor) {
        this.gray = gray;
        this.w = w;
        this.h = h;
        this.factor = factor;
    }

    static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (77 * r + 150 * g + 29 * b) >> 8;
    }

    static WorkImage downsample(int[] argb, int width, int height, int f) {
        int w = width / f;
        int h = height / f;
        byte[] out = new byte[w * h];
        int[] acc = new int[w];
        int div = f * f;
        for (int oy = 0; oy < h; oy++) {
            for (int i = 0; i < w; i++) {
                acc[i] = 0;
            }
            for (int dy = 0; dy < f; dy++) {
                int row = (oy * f + dy) * width;
                for (int ox = 0; ox < w; ox++) {
                    int base = row + ox * f;
                    int s = 0;
                    for (int dx = 0; dx < f; dx++) {
                        s += luminance(argb[base + dx]);
                    }
                    acc[ox] += s;
                }
            }
            int o = oy * w;
            for (int ox = 0; ox < w; ox++) {
                out[o + ox] = (byte) (acc[ox] / div);
            }
        }
        return new WorkImage(out, w, h, f);
    }

    /**
     * Gleicht ungleichmäßige Ausleuchtung (Schatten, Verlauf) aus: jedes Pixel wird relativ zum
     * lokalen Papierweiß skaliert. Papierweiß je Block = 90-%-Perzentil, danach 3x3-Maximum
     * (ein Block, der ganz in einer Marke liegt, soll nicht als "dunkles Papier" gelten).
     * Arbeitet in place, kein zusätzlicher Bildspeicher.
     */
    void flattenIllumination() {
        int b = Math.max(16, Math.min(w, h) / 20);
        int bw = (w + b - 1) / b;
        int bh = (h + b - 1) / b;
        int[] hist = new int[256];
        for (int i = 0; i < gray.length; i++) {
            hist[gray[i] & 0xFF]++;
        }
        int global = percentile(hist, gray.length, 0.90);
        if (global < 32) {
            return;
        }
        int[] paper = new int[bw * bh];
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                for (int i = 0; i < 256; i++) {
                    hist[i] = 0;
                }
                int n = 0;
                for (int y = by * b; y < Math.min(h, by * b + b); y++) {
                    for (int x = bx * b; x < Math.min(w, bx * b + b); x++) {
                        hist[gray[y * w + x] & 0xFF]++;
                        n++;
                    }
                }
                paper[by * bw + bx] = percentile(hist, n, 0.90);
            }
        }
        float[] pm = new float[bw * bh];
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                int m = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = bx + dx;
                        int y = by + dy;
                        if (x >= 0 && y >= 0 && x < bw && y < bh && paper[y * bw + x] > m) {
                            m = paper[y * bw + x];
                        }
                    }
                }
                // große dunkle Flächen (kein Papier) nicht aufhellen
                pm[by * bw + bx] = m < global / 5 ? global : m;
            }
        }
        // Spaltenweise Interpolationsgewichte einmal vorberechnen, je Zeile nur bw Werte interpolieren.
        int[] xa = new int[w];
        int[] xb = new int[w];
        int[] fx = new int[w];
        for (int x = 0; x < w; x++) {
            double gx = (x + 0.5) / b - 0.5;
            int x0 = (int) Math.floor(gx);
            fx[x] = (int) ((gx - x0) * 256);
            xa[x] = Math.max(0, Math.min(bw - 1, x0));
            xb[x] = Math.max(0, Math.min(bw - 1, x0 + 1));
        }
        int[] rowInv = new int[bw];
        for (int y = 0; y < h; y++) {
            double gy = (y + 0.5) / b - 0.5;
            int y0 = (int) Math.floor(gy);
            double fy = gy - y0;
            int ya = Math.max(0, Math.min(bh - 1, y0));
            int yb = Math.max(0, Math.min(bh - 1, y0 + 1));
            for (int i = 0; i < bw; i++) {
                double p = pm[ya * bw + i] * (1 - fy) + pm[yb * bw + i] * fy;
                rowInv[i] = (int) (255 * 65536 / p);
            }
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int inv = (rowInv[xa[x]] * (256 - fx[x]) + rowInv[xb[x]] * fx[x]) >> 8;
                int v = ((gray[row + x] & 0xFF) * inv) >> 16;
                gray[row + x] = (byte) (v > 255 ? 255 : v);
            }
        }
    }

    int get(int x, int y) {
        return gray[y * w + x] & 0xFF;
    }

    /** Schwelle zwischen Papier und Druckschwarz: Mitte zwischen 1%- und 90%-Perzentil. */
    int blackThreshold() {
        int[] hist = new int[256];
        for (int i = 0; i < gray.length; i++) {
            hist[gray[i] & 0xFF]++;
        }
        int dark = percentile(hist, gray.length, 0.01);
        int paper = percentile(hist, gray.length, 0.90);
        return (dark + paper + 1) / 2;
    }

    static int percentile(int[] hist, int total, double p) {
        long target = (long) Math.ceil(total * p);
        long acc = 0;
        for (int i = 0; i < hist.length; i++) {
            acc += hist[i];
            if (acc >= target) {
                return i;
            }
        }
        return hist.length - 1;
    }
}
