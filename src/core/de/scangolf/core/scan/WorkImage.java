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
