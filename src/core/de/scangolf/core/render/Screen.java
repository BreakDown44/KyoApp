package de.scangolf.core.render;

/**
 * Ein Bildschirm der App. Die Plattform ruft regelmäßig {@link #update} und {@link #render}
 * auf und leitet Touch-Ereignisse in logischen Koordinaten weiter.
 */
public interface Screen {

    /** Logische Breite, in die gezeichnet wird. */
    int width();

    /** Logische Höhe. */
    int height();

    /** Zeit fortschreiben (Millisekunden, monoton). */
    void update(long nowMs);

    void render(Canvas c);

    /** @param type {@link Touch#DOWN}, MOVE, UP oder CANCEL */
    void touch(int type, double x, double y);
}
