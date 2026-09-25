package de.scangolf.core.level;

/**
 * Spielregeln und Maße, die Scan-Prüfung und Spiel gemeinsam nutzen.
 * Alle Längen in Millimetern auf dem Papier (Feldkoordinaten).
 */
public final class Rules {

    /** Radius der Kugel. Ein Durchgang muss mindestens 2 * BALL_RADIUS_MM breit sein. */
    public static final double BALL_RADIUS_MM = 3.5;

    /** Sichtbarer Radius des Lochs. */
    public static final double HOLE_RADIUS_MM = 5.5;

    /** Die Kugel fällt, wenn ihr Mittelpunkt näher als dieser Wert am Loch ist (und sie langsam genug ist). */
    public static final double HOLE_CAPTURE_RADIUS_MM = 4.0;

    /** Toleranz der Erreichbarkeitsprüfung gegenüber der Rasterung (1-mm-Zellen). */
    public static final double REACH_SLACK_MM = 0.3;

    public static final int MAX_STROKES = 10;

    public static final int DEFAULT_PAR = 3;

    private Rules() {
    }
}
