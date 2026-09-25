package de.scangolf.core.render;

/** Typen von Touch-Ereignissen (logische Koordinaten des Bildschirms). */
public final class Touch {

    public static final int DOWN = 0;
    public static final int MOVE = 1;
    public static final int UP = 2;
    /** Berührung abgebrochen (z. B. Finger verlässt die Fläche). */
    public static final int CANCEL = 3;

    private Touch() {
    }
}
