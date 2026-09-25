package de.scangolf.core.game;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;

/**
 * Kugelphysik mit festem Zeitschritt. Einheiten: mm und Sekunden, Feldkoordinaten.
 *
 * Wände sind die Wandzellen des Levels (Quadrate) plus der Feldrand. Die Kontaktnormale
 * wird aus allen berührten Wandzellen gemittelt (gewichtet mit der Eindringtiefe) – so
 * prallt die Kugel auch an schräg gezeichneten, treppenförmig gerasterten Wänden sinnvoll ab.
 * Unterschritte von höchstens {@link #MAX_SUBSTEP_MM} verhindern Tunneln durch dünne Wände.
 */
public final class Physics {

    public static final int HZ = 120;
    public static final double DT = 1.0 / HZ;

    /** Rollwiderstand (konstante Verzögerung) in mm/s². */
    public static final double FRICTION = 120.0;
    /** Geschwindigkeitsabhängige Dämpfung in 1/s. */
    public static final double DRAG = 0.35;
    /** Darunter bleibt die Kugel liegen. */
    public static final double STOP_SPEED = 4.0;
    /** Höchstgeschwindigkeit eines Schlags (volle Stärke). */
    public static final double MAX_SHOT_SPEED = 420.0;
    /** Abprall: Anteil der Normalgeschwindigkeit, der erhalten bleibt. */
    public static final double RESTITUTION = 0.7;
    /** Abprall: Anteil der Tangentialgeschwindigkeit, der erhalten bleibt. */
    public static final double TANGENT_KEEP = 0.95;
    /** Maximale Strecke je Unterschritt. */
    public static final double MAX_SUBSTEP_MM = 0.25;
    /** Beschleunigung zur Lochmitte, solange die Kugel über dem Loch ist (Mulde). */
    public static final double HOLE_PULL = 700.0;
    /** Schneller als das rollt die Kugel über das Loch hinweg. */
    public static final double CAPTURE_SPEED = 190.0;
    /** Sicherheitsnetz: länger rollt keine Kugel (wird in den Tests als Fehler gewertet). */
    public static final int MAX_ROLL_STEPS = 30 * HZ;

    // Ereignis-Bits
    public static final int EV_WALL = 1;
    public static final int EV_WATER = 2;
    public static final int EV_HOLED = 4;
    public static final int EV_STOPPED = 8;
    public static final int EV_FORCED_STOP = 16;

    /** Für Tests: meldet jede Position nach einem Unterschritt. */
    public interface SubstepListener {
        void substep(double x, double y);
    }

    private final Level level;
    private final double r;
    private final double width;
    private final double height;
    private final double cell;
    private final double[] contact = new double[2];
    private SubstepListener listener;

    // Kugelzustand
    double x;
    double y;
    double vx;
    double vy;

    public Physics(Level level) {
        this.level = level;
        this.r = Rules.BALL_RADIUS_MM;
        this.width = level.widthMm();
        this.height = level.heightMm();
        this.cell = level.cellMm();
    }

    public void setSubstepListener(SubstepListener l) {
        this.listener = l;
    }

    public double speed() {
        return Math.sqrt(vx * vx + vy * vy);
    }

    /** Ein fester Zeitschritt. Rückgabe: Ereignis-Bits. */
    int step() {
        int ev = 0;
        double hx = level.holeX() - x;
        double hy = level.holeY() - y;
        double dh = Math.sqrt(hx * hx + hy * hy);
        boolean overHole = dh < Rules.HOLE_RADIUS_MM;
        if (overHole && dh > 1e-9) {
            vx += hx / dh * HOLE_PULL * DT;
            vy += hy / dh * HOLE_PULL * DT;
        }
        double speed = speed();
        double dec = (FRICTION + DRAG * speed) * DT;
        if (speed <= dec) {
            vx = 0;
            vy = 0;
        } else {
            double f = (speed - dec) / speed;
            vx *= f;
            vy *= f;
        }
        speed = speed();
        int n = Math.max(1, (int) Math.ceil(speed * DT / MAX_SUBSTEP_MM));
        double sub = DT / n;
        for (int i = 0; i < n; i++) {
            double px = x;
            double py = y;
            x += vx * sub;
            y += vy * sub;
            if (resolve(px, py)) {
                ev |= EV_WALL;
            }
            if (listener != null) {
                listener.substep(x, y);
            }
            if (level.cellAtMm(x, y) == CellType.WATER) {
                return ev | EV_WATER;
            }
            if (captured()) {
                return ev | EV_HOLED;
            }
        }
        if (speed() < STOP_SPEED && !overHole) {
            vx = 0;
            vy = 0;
            ev |= EV_STOPPED;
        }
        return ev;
    }

    private boolean captured() {
        double hx = level.holeX() - x;
        double hy = level.holeY() - y;
        double d = Math.sqrt(hx * hx + hy * hy);
        double s = speed();
        return (d < Rules.HOLE_CAPTURE_RADIUS_MM && s < CAPTURE_SPEED)
                || (d < Rules.HOLE_RADIUS_MM && s < 2 * STOP_SPEED);
    }

    /**
     * Summe der Kontaktnormalen (gewichtet mit Eindringtiefe) in contact[], Rückgabe: größte Tiefe.
     */
    double contact(double cx, double cy) {
        double nx = 0;
        double ny = 0;
        double maxDepth = 0;
        if (cx < r) {
            double d = r - cx;
            nx += d;
            maxDepth = Math.max(maxDepth, d);
        }
        if (cx > width - r) {
            double d = r - (width - cx);
            nx -= d;
            maxDepth = Math.max(maxDepth, d);
        }
        if (cy < r) {
            double d = r - cy;
            ny += d;
            maxDepth = Math.max(maxDepth, d);
        }
        if (cy > height - r) {
            double d = r - (height - cy);
            ny -= d;
            maxDepth = Math.max(maxDepth, d);
        }
        int x0 = (int) Math.floor((cx - r) / cell);
        int x1 = (int) Math.floor((cx + r) / cell);
        int y0 = (int) Math.floor((cy - r) / cell);
        int y1 = (int) Math.floor((cy + r) / cell);
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 >= level.cols()) {
            x1 = level.cols() - 1;
        }
        if (y1 >= level.rows()) {
            y1 = level.rows() - 1;
        }
        double r2 = r * r;
        for (int gy = y0; gy <= y1; gy++) {
            double sy0 = gy * cell;
            double qy = cy < sy0 ? sy0 : (cy > sy0 + cell ? sy0 + cell : cy);
            double dy = cy - qy;
            for (int gx = x0; gx <= x1; gx++) {
                if (!level.isWall(gx, gy)) {
                    continue;
                }
                double sx0 = gx * cell;
                double qx = cx < sx0 ? sx0 : (cx > sx0 + cell ? sx0 + cell : cx);
                double dx = cx - qx;
                double d2 = dx * dx + dy * dy;
                if (d2 >= r2) {
                    continue;
                }
                double d = Math.sqrt(d2);
                double depth;
                double ux;
                double uy;
                if (d > 1e-9) {
                    depth = r - d;
                    ux = dx / d;
                    uy = dy / d;
                } else {
                    // Mittelpunkt in der Zelle: weg von der Zellmitte schieben
                    double mx = cx - (sx0 + cell / 2);
                    double my = cy - (sy0 + cell / 2);
                    double m = Math.sqrt(mx * mx + my * my);
                    if (m < 1e-9) {
                        mx = 0;
                        my = -1;
                        m = 1;
                    }
                    ux = mx / m;
                    uy = my / m;
                    depth = r + cell;
                }
                nx += ux * depth;
                ny += uy * depth;
                if (depth > maxDepth) {
                    maxDepth = depth;
                }
            }
        }
        contact[0] = nx;
        contact[1] = ny;
        return maxDepth;
    }

    /** Eindringen auflösen und abprallen. Rückgabe: ob eine Wand berührt wurde. */
    private boolean resolve(double px, double py) {
        boolean hit = false;
        double lastNx = 0;
        double lastNy = 0;
        for (int iter = 0; iter < 4; iter++) {
            double depth = contact(x, y);
            if (depth <= 1e-9) {
                return hit;
            }
            double nx = contact[0];
            double ny = contact[1];
            double len = Math.sqrt(nx * nx + ny * ny);
            if (len < 1e-12) {
                // Normalen heben sich auf (z. B. enger Spalt): zurück Richtung vorheriger Position
                nx = px - x;
                ny = py - y;
                len = Math.sqrt(nx * nx + ny * ny);
                if (len < 1e-12) {
                    nx = -vx;
                    ny = -vy;
                    len = Math.sqrt(nx * nx + ny * ny);
                    if (len < 1e-12) {
                        nx = 0;
                        ny = -1;
                        len = 1;
                    }
                }
            }
            nx /= len;
            ny /= len;
            x += nx * (depth + 1e-7);
            y += ny * (depth + 1e-7);
            reflect(nx, ny);
            lastNx = nx;
            lastNy = ny;
            hit = true;
        }
        if (contact(x, y) > 1e-6) {
            // Nicht sauber auflösbar (sehr enge Ecke): zurück auf die letzte gültige Position
            x = px;
            y = py;
            if (lastNx != 0 || lastNy != 0) {
                reflect(lastNx, lastNy);
            }
            if (contact(x, y) > 1e-6) {
                vx = 0;
                vy = 0;
            }
        }
        return hit;
    }

    private void reflect(double nx, double ny) {
        double vn = vx * nx + vy * ny;
        if (vn >= 0) {
            return;
        }
        double tx = vx - vn * nx;
        double ty = vy - vn * ny;
        vx = tx * TANGENT_KEEP - vn * RESTITUTION * nx;
        vy = ty * TANGENT_KEEP - vn * RESTITUTION * ny;
    }
}
