package de.scangolf.core.scan;

/** Gefundenes Suchmuster (Mittelpunkt und Modulgröße in Pixeln des Arbeitsbildes). */
public final class FinderPattern {

    double x;
    double y;
    double module;
    int count;

    FinderPattern(double x, double y, double module) {
        this.x = x;
        this.y = y;
        this.module = module;
        this.count = 1;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double module() {
        return module;
    }

    /** Wie oft das Muster bestätigt wurde (Anzahl Zeilentreffer). */
    public int count() {
        return count;
    }

    boolean near(double px, double py, double m) {
        double tol = Math.max(module, m);
        return Math.abs(px - x) <= tol && Math.abs(py - y) <= tol
                && m < module * 1.5 && module < m * 1.5;
    }

    void merge(double px, double py, double m) {
        double n = count;
        x = (x * n + px) / (n + 1);
        y = (y * n + py) / (n + 1);
        module = (module * n + m) / (n + 1);
        count++;
    }

    public String toString() {
        return "Finder(" + Math.round(x * 10) / 10.0 + ", " + Math.round(y * 10) / 10.0
                + ", m=" + Math.round(module * 100) / 100.0 + ", n=" + count + ")";
    }
}
