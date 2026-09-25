package de.scangolf.core.game;

/** Zustände einer Runde. */
public enum GameState {
    /** Kugel liegt, Spieler kann zielen und schlagen. */
    AIMING,
    /** Kugel rollt. */
    ROLLING,
    /** Kugel ist eingelocht – Runde vorbei. */
    HOLED,
    /** Maximale Schlagzahl erreicht (oder aufgegeben) – Runde vorbei. */
    GAVE_UP
}
