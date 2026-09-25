package de.scangolf.core.game;

import de.scangolf.core.util.FloatList;

/** Spur eines Schlags (Polylinie in Feld-mm) für Darstellung und Urkunde. */
public final class ShotTrace {

    private final FloatList pts = new FloatList(64);
    private boolean water;
    private boolean holed;

    ShotTrace(double x, double y) {
        add(x, y);
    }

    void add(double x, double y) {
        pts.add((float) x);
        pts.add((float) y);
    }

    float lastX() {
        return pts.get(pts.size() - 2);
    }

    float lastY() {
        return pts.get(pts.size() - 1);
    }

    void endInWater() {
        water = true;
    }

    void endHoled() {
        holed = true;
    }

    public int pointCount() {
        return pts.size() / 2;
    }

    public float x(int i) {
        return pts.get(2 * i);
    }

    public float y(int i) {
        return pts.get(2 * i + 1);
    }

    /** Endete der Schlag im Wasser? */
    public boolean water() {
        return water;
    }

    public boolean holed() {
        return holed;
    }
}
