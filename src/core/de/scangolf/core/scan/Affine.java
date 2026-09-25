package de.scangolf.core.scan;

/**
 * Affine Abbildung x' = a*x + b*y + c, y' = d*x + e*y + f (z. B. Seiten-mm auf Bildpixel).
 */
public final class Affine {

    public final double a;
    public final double b;
    public final double c;
    public final double d;
    public final double e;
    public final double f;

    public Affine(double a, double b, double c, double d, double e, double f) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    /** Exakte Abbildung, die die drei Quellpunkte auf die drei Zielpunkte legt. */
    public static Affine fromTriangles(double sx0, double sy0, double sx1, double sy1, double sx2, double sy2,
                                       double dx0, double dy0, double dx1, double dy1, double dx2, double dy2) {
        // Quellbasis: u = p1 - p0, v = p2 - p0; Zielbasis ebenso.
        double ux = sx1 - sx0;
        double uy = sy1 - sy0;
        double vx = sx2 - sx0;
        double vy = sy2 - sy0;
        double det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-12) {
            throw new IllegalArgumentException("Quellpunkte liegen auf einer Linie");
        }
        double pux = dx1 - dx0;
        double puy = dy1 - dy0;
        double pvx = dx2 - dx0;
        double pvy = dy2 - dy0;
        // Lineare Matrix M mit M*u = pu, M*v = pv  =>  M = [pu pv] * inv([u v])
        double i00 = vy / det;
        double i01 = -vx / det;
        double i10 = -uy / det;
        double i11 = ux / det;
        double a = pux * i00 + pvx * i10;
        double b = pux * i01 + pvx * i11;
        double d = puy * i00 + pvy * i10;
        double e = puy * i01 + pvy * i11;
        double c = dx0 - a * sx0 - b * sy0;
        double f = dy0 - d * sx0 - e * sy0;
        return new Affine(a, b, c, d, e, f);
    }

    public double mapX(double x, double y) {
        return a * x + b * y + c;
    }

    public double mapY(double x, double y) {
        return d * x + e * y + f;
    }

    public double determinant() {
        return a * e - b * d;
    }

    /** Mittlerer Maßstab (Zieleinheiten je Quelleinheit). */
    public double scale() {
        return Math.sqrt(Math.abs(determinant()));
    }

    public Affine inverse() {
        double det = determinant();
        if (Math.abs(det) < 1e-12) {
            throw new IllegalStateException("Abbildung nicht umkehrbar");
        }
        double ia = e / det;
        double ib = -b / det;
        double id = -d / det;
        double ie = a / det;
        return new Affine(ia, ib, -(ia * c + ib * f), id, ie, -(id * c + ie * f));
    }

    /** Diese Abbildung, danach eine Skalierung um s (z. B. Vollbild-Pixel auf Arbeitsbild). */
    public Affine thenScale(double s) {
        return new Affine(a * s, b * s, c * s, d * s, e * s, f * s);
    }

    /** Drehwinkel der x-Achse in Grad (0 = Blatt liegt gerade). */
    public double rotationDegrees() {
        return Math.toDegrees(Math.atan2(d, a));
    }

    public String toString() {
        return "Affine[" + a + ", " + b + ", " + c + "; " + d + ", " + e + ", " + f + "]";
    }
}
