package de.scangolf.core.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Sucht QR-artige Suchmuster (7x7 Module, Lauflängen 1:1:3:1:1) im Arbeitsbild.
 * Zeilenweise Suche, Bestätigung senkrecht und nochmals waagerecht (wie bei QR-Decodern).
 * Das Verhältnis gilt für jede Drehung, deshalb ist die Suche drehungsunabhängig.
 */
final class FinderDetector {

    private final WorkImage img;
    private final int threshold;
    private final List<FinderPattern> found = new ArrayList<FinderPattern>();

    FinderDetector(WorkImage img, int threshold) {
        this.img = img;
        this.threshold = threshold;
    }

    private boolean dark(int x, int y) {
        return img.get(x, y) < threshold;
    }

    List<FinderPattern> detect() {
        int w = img.w;
        int h = img.h;
        int[] sc = new int[5];
        for (int y = 0; y < h; y++) {
            for (int k = 0; k < 5; k++) {
                sc[k] = 0;
            }
            int state = 0;
            for (int x = 0; x < w; x++) {
                if (dark(x, y)) {
                    if ((state & 1) == 1) {
                        state++;
                    }
                    sc[state]++;
                } else if ((state & 1) == 0) {
                    if (state == 4) {
                        if (ratioOk(sc)) {
                            handleCandidate(sc, y, x);
                        }
                        sc[0] = sc[2];
                        sc[1] = sc[3];
                        sc[2] = sc[4];
                        sc[3] = 1;
                        sc[4] = 0;
                        state = 3;
                    } else {
                        state++;
                        sc[state]++;
                    }
                } else {
                    sc[state]++;
                }
            }
            if (state == 4 && ratioOk(sc)) {
                handleCandidate(sc, y, w);
            }
        }
        return found;
    }

    static boolean ratioOk(int[] sc) {
        int total = 0;
        for (int i = 0; i < 5; i++) {
            if (sc[i] == 0) {
                return false;
            }
            total += sc[i];
        }
        if (total < 7) {
            return false;
        }
        double m = total / 7.0;
        double tol = Math.max(m * 0.5, 1.2);
        return Math.abs(m - sc[0]) < tol && Math.abs(m - sc[1]) < tol
                && Math.abs(3 * m - sc[2]) < 3 * tol
                && Math.abs(m - sc[3]) < tol && Math.abs(m - sc[4]) < tol;
    }

    private static int sum(int[] sc) {
        return sc[0] + sc[1] + sc[2] + sc[3] + sc[4];
    }

    private void handleCandidate(int[] sc, int y, int xEnd) {
        int total = sum(sc);
        double cx = xEnd - sc[4] - sc[3] - sc[2] / 2.0;
        double cy = crossCheckVertical((int) cx, y, sc[2], total);
        if (Double.isNaN(cy)) {
            return;
        }
        double[] hx = crossCheckHorizontal((int) cx, (int) cy, sc[2], total);
        if (hx == null) {
            return;
        }
        cx = hx[0];
        double module = (total + hx[1]) / 14.0;
        for (int i = 0; i < found.size(); i++) {
            FinderPattern f = found.get(i);
            if (f.near(cx, cy, module)) {
                f.merge(cx, cy, module);
                return;
            }
        }
        found.add(new FinderPattern(cx, cy, module));
    }

    private double crossCheckVertical(int cx, int startY, int maxCount, int originalTotal) {
        int h = img.h;
        if (cx < 0 || cx >= img.w) {
            return Double.NaN;
        }
        int[] sc = new int[5];
        int i = startY;
        while (i >= 0 && dark(cx, i)) {
            sc[2]++;
            i--;
        }
        if (i < 0) {
            return Double.NaN;
        }
        while (i >= 0 && !dark(cx, i) && sc[1] <= maxCount) {
            sc[1]++;
            i--;
        }
        if (i < 0 || sc[1] > maxCount) {
            return Double.NaN;
        }
        while (i >= 0 && dark(cx, i) && sc[0] <= maxCount) {
            sc[0]++;
            i--;
        }
        if (sc[0] > maxCount) {
            return Double.NaN;
        }
        i = startY + 1;
        while (i < h && dark(cx, i)) {
            sc[2]++;
            i++;
        }
        if (i == h) {
            return Double.NaN;
        }
        while (i < h && !dark(cx, i) && sc[3] <= maxCount) {
            sc[3]++;
            i++;
        }
        if (i == h || sc[3] > maxCount) {
            return Double.NaN;
        }
        while (i < h && dark(cx, i) && sc[4] <= maxCount) {
            sc[4]++;
            i++;
        }
        if (sc[4] > maxCount) {
            return Double.NaN;
        }
        int total = sum(sc);
        if (5 * Math.abs(total - originalTotal) >= 2 * originalTotal || !ratioOk(sc)) {
            return Double.NaN;
        }
        return i - sc[4] - sc[3] - sc[2] / 2.0;
    }

    /** Liefert {Mitte x, Gesamtlänge} oder null. */
    private double[] crossCheckHorizontal(int startX, int cy, int maxCount, int originalTotal) {
        int w = img.w;
        if (cy < 0 || cy >= img.h) {
            return null;
        }
        int[] sc = new int[5];
        int i = startX;
        while (i >= 0 && dark(i, cy)) {
            sc[2]++;
            i--;
        }
        if (i < 0) {
            return null;
        }
        while (i >= 0 && !dark(i, cy) && sc[1] <= maxCount) {
            sc[1]++;
            i--;
        }
        if (i < 0 || sc[1] > maxCount) {
            return null;
        }
        while (i >= 0 && dark(i, cy) && sc[0] <= maxCount) {
            sc[0]++;
            i--;
        }
        if (sc[0] > maxCount) {
            return null;
        }
        i = startX + 1;
        while (i < w && dark(i, cy)) {
            sc[2]++;
            i++;
        }
        if (i == w) {
            return null;
        }
        while (i < w && !dark(i, cy) && sc[3] <= maxCount) {
            sc[3]++;
            i++;
        }
        if (i == w || sc[3] > maxCount) {
            return null;
        }
        while (i < w && dark(i, cy) && sc[4] <= maxCount) {
            sc[4]++;
            i++;
        }
        if (sc[4] > maxCount) {
            return null;
        }
        int total = sum(sc);
        if (5 * Math.abs(total - originalTotal) >= originalTotal * 2 || !ratioOk(sc)) {
            return null;
        }
        return new double[] {i - sc[4] - sc[3] - sc[2] / 2.0, total};
    }
}
